package in.co.kitree.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.genai.Client;
import com.google.genai.types.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Orchestrates daily rashifal generation:
 * birth profile → Python /rashifal → Hindi TTS → Cloudinary → Firestore
 */
public class RashifalService {

    private static final String TTS_MODEL = "gemini-2.5-flash-preview-tts";
    private static final int SAMPLE_RATE = 24000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int NUM_CHANNELS = 1;

    private static final String[] RASHI_NAMES = {
        "", "मेष", "वृषभ", "मिथुन", "कर्क", "सिंह", "कन्या",
        "तुला", "वृश्चिक", "धनु", "मकर", "कुम्भ", "मीन"
    };
    private static final String[] WEEKDAY_NAMES = {
        "रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"
    };

    private final Firestore db;
    private final Client geminiClient;
    private final AstrologyService astrologyService;
    private final ObjectMapper objectMapper;
    private final boolean isTest;

    public RashifalService(Firestore db, AstrologyService astrologyService, boolean isTest) {
        this.db = db;
        this.astrologyService = astrologyService;
        this.isTest = isTest;
        this.objectMapper = new ObjectMapper();

        String apiKey = loadGeminiApiKey(isTest);
        if (apiKey != null && !apiKey.isEmpty()) {
            this.geminiClient = Client.builder().apiKey(apiKey).build();
            LoggingService.info("rashifal_service_initialized", Map.of("environment", isTest ? "TEST" : "PROD"));
        } else {
            LoggingService.error("rashifal_gemini_no_api_key", Map.of("environment", isTest ? "TEST" : "PROD"));
            this.geminiClient = null;
        }
    }

    private static String loadGeminiApiKey(boolean isTest) {
        String key = SecretsProvider.getString(isTest ? "GEMINI_API_KEY_TEST" : "GEMINI_API_KEY");
        if (key.isEmpty()) {
            key = SecretsProvider.getString("GEMINI_API_KEY");
        }
        return key;
    }

    private static final int STALE_GENERATING_MINUTES = 15;

    /**
     * Validates guards and claims the "generating" slot in Firestore.
     * Returns a JSON response string if generation should be aborted (cached, in-progress, or invalid).
     * Returns null if the slot was claimed and async generation should proceed.
     */
    public String prepareRashifalGeneration(String userId) throws Exception {
        if (geminiClient == null) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "Gemini not configured"));
        }

        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        DocumentReference rashifalRef = db.collection("users").document(userId)
                .collection("rashifal").document("current");

        DocumentSnapshot existingDoc = rashifalRef.get().get();
        if (existingDoc.exists()) {
            String status = existingDoc.getString("status");
            String existingDate = existingDoc.getString("date");

            if ("ready".equals(status) && today.equals(existingDate)) {
                // Already done for today — touch doc to re-trigger Firestore subscription on the frontend
                rashifalRef.update("lastRequested", FieldValue.serverTimestamp());
                return objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "audioUrl", existingDoc.getString("audioUrl")
                ));
            }

            if ("generating".equals(status) && today.equals(existingDate)) {
                Timestamp generatedAt = existingDoc.getTimestamp("generatedAt");
                if (generatedAt != null) {
                    long elapsedMinutes = (System.currentTimeMillis() - generatedAt.toDate().getTime()) / 60000;
                    if (elapsedMinutes < STALE_GENERATING_MINUTES) {
                        // Still fresh — generation is already in progress
                        return objectMapper.writeValueAsString(Map.of("success", true));
                    }
                    LoggingService.warn("rashifal_stale_generating_cleared", Map.of("userId", userId, "elapsedMinutes", elapsedMinutes));
                }
            }
        }

        // Validate profile before claiming the slot
        QuerySnapshot profileSnapshot = db.collection("users").document(userId)
                .collection("profiles")
                .orderBy("markedPrimaryAt", Query.Direction.DESCENDING)
                .limit(1).get().get();
        if (profileSnapshot.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "No birth profile found"));
        }
        DocumentSnapshot profile = profileSnapshot.getDocuments().get(0);
        if (profile.getString("location") == null || !profile.getString("location").contains(",")) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "Invalid location in profile"));
        }
        if (profile.getTimestamp("dob") == null) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "Missing dob in profile"));
        }

        // Claim the generation slot
        Map<String, Object> generatingData = new HashMap<>();
        generatingData.put("status", "generating");
        generatingData.put("date", today);
        generatingData.put("generatedAt", FieldValue.serverTimestamp());
        rashifalRef.set(generatingData).get();

        return null; // caller should proceed with async generation
    }

    /**
     * Deletes the "generating" doc so the user is not stuck after a failed async invocation.
     */
    public void cleanupRashifalGenerating(String userId) {
        try {
            db.collection("users").document(userId).collection("rashifal").document("current").delete().get();
        } catch (Exception e) {
            LoggingService.warn("rashifal_cleanup_failed", Map.of("userId", userId));
        }
    }

    /**
     * Performs the actual generation: Python /rashifal → Gemini TTS → Cloudinary → Firestore.
     * Assumes the "generating" slot has already been claimed via prepareRashifalGeneration.
     * Cleans up Firestore on failure so the user can retry.
     */
    public void executeRashifalGeneration(String userId) {
        DocumentReference rashifalRef = db.collection("users").document(userId)
                .collection("rashifal").document("current");
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            doGenerate(userId, rashifalRef, today);
        } catch (Exception e) {
            LoggingService.error("rashifal_async_generation_failed", e);
        }
    }

    /**
     * Generate rashifal audio for a user and persist to Firestore.
     * Combines prepareRashifalGeneration + doGenerate for the synchronous path.
     */
    public String generateRashifal(String userId) throws Exception {
        String earlyResult = prepareRashifalGeneration(userId);
        if (earlyResult != null) return earlyResult;

        DocumentReference rashifalRef = db.collection("users").document(userId)
                .collection("rashifal").document("current");
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String audioUrl = doGenerate(userId, rashifalRef, today);
        return objectMapper.writeValueAsString(Map.of("success", true, "audioUrl", audioUrl));
    }

    /**
     * Core generation: Python /rashifal → Gemini TTS → Cloudinary → Firestore.
     * Cleans up the Firestore doc on failure and rethrows.
     */
    private String doGenerate(String userId, DocumentReference rashifalRef, String today) throws Exception {
        try {
            // Fetch profile
            QuerySnapshot profileSnapshot = db.collection("users").document(userId)
                    .collection("profiles")
                    .orderBy("markedPrimaryAt", Query.Direction.DESCENDING)
                    .limit(1).get().get();
            if (profileSnapshot.isEmpty()) throw new RuntimeException("No birth profile found");
            DocumentSnapshot profile = profileSnapshot.getDocuments().get(0);

            String[] loc = profile.getString("location").split(",");
            double latitude = Double.parseDouble(loc[0].trim());
            double longitude = Double.parseDouble(loc[1].trim());

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTime(profile.getTimestamp("dob").toDate());
            int yr = cal.get(Calendar.YEAR), mo = cal.get(Calendar.MONTH) + 1;
            int dy = cal.get(Calendar.DAY_OF_MONTH), hr = cal.get(Calendar.HOUR_OF_DAY), mn = cal.get(Calendar.MINUTE);

            // Python /rashifal
            JsonNode data = objectMapper.readTree(callPythonRashifal(yr, mo, dy, hr, mn, latitude, longitude));
            int natalMoonSign = data.path("natal_moon_sign").asInt();
            int taraBala = data.path("tara_bala").asInt();
            int weekday = data.path("weekday").asInt();
            int transitMoonSign = data.path("transit_moon_sign").asInt();
            int transitSunSign = data.path("transit_sun_sign").asInt();

            // Gemini TTS
            String hindiText = buildHindiRashifalText(natalMoonSign, transitMoonSign, transitSunSign, taraBala, weekday);
            byte[] wavBytes = pcmToWav(generateTtsAudio(hindiText));

            // TODO: Cloudinary URLs are publicly accessible without authentication.
            // Migrate to signed/authenticated URLs for proper access control.
            String audioUrl = uploadToCloudinary(wavBytes, userId);

            // Update Firestore
            Map<String, Object> readyData = new HashMap<>();
            readyData.put("status", "ready");
            readyData.put("date", today);
            readyData.put("audioUrl", audioUrl);
            readyData.put("natalMoonSign", natalMoonSign);
            readyData.put("taraBala", taraBala);
            readyData.put("generatedAt", FieldValue.serverTimestamp());
            rashifalRef.set(readyData).get();

            LoggingService.info("rashifal_generated", Map.of("userId", userId, "date", today));
            return audioUrl;
        } catch (Exception e) {
            try { rashifalRef.delete().get(); } catch (Exception ignored) {
                LoggingService.warn("rashifal_cleanup_failed", Map.of("userId", userId));
            }
            throw e;
        }
    }

    private String callPythonRashifal(int year, int month, int day, int hour, int minute,
                                       double latitude, double longitude) throws Exception {
        return astrologyService.getRashifalData(year, month, day, hour, minute, latitude, longitude);
    }

    private byte[] generateTtsAudio(String text) throws Exception {
        Content content = Content.fromParts(Part.fromText(text));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("AUDIO"))
                .speechConfig(SpeechConfig.builder()
                        .voiceConfig(VoiceConfig.builder()
                                .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder()
                                        .voiceName("Kore")
                                        .build())
                                .build())
                        .build())
                .build();

        GenerateContentResponse response = geminiClient.models.generateContent(TTS_MODEL, content, config);

        List<Candidate> candidates = response.candidates()
                .orElseThrow(() -> new RuntimeException("No TTS candidates"));
        Content responseContent = candidates.get(0).content()
                .orElseThrow(() -> new RuntimeException("No TTS content"));
        List<Part> parts = responseContent.parts()
                .orElseThrow(() -> new RuntimeException("No TTS parts"));
        com.google.genai.types.Blob audioBlob = parts.get(0).inlineData()
                .orElseThrow(() -> new RuntimeException("No TTS inline data"));
        return audioBlob.data()
                .orElseThrow(() -> new RuntimeException("No TTS audio bytes"));
    }

    private byte[] pcmToWav(byte[] pcmData) {
        int dataSize = pcmData.length;
        int byteRate = SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = NUM_CHANNELS * BITS_PER_SAMPLE / 8;

        ByteBuffer buf = ByteBuffer.allocate(44 + dataSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buf.put(new byte[]{'R', 'I', 'F', 'F'});
        buf.putInt(36 + dataSize);
        buf.put(new byte[]{'W', 'A', 'V', 'E'});

        // fmt chunk
        buf.put(new byte[]{'f', 'm', 't', ' '});
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) NUM_CHANNELS);
        buf.putInt(SAMPLE_RATE);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) BITS_PER_SAMPLE);

        // data chunk
        buf.put(new byte[]{'d', 'a', 't', 'a'});
        buf.putInt(dataSize);
        buf.put(pcmData);

        return buf.array();
    }

    private String uploadToCloudinary(byte[] wavBytes, String userId) throws Exception {
        String cloudinaryUrl = SecretsProvider.getString("CLOUDINARY_URL");
        if (cloudinaryUrl.isEmpty()) {
            throw new RuntimeException("CLOUDINARY_URL not configured in secrets.json");
        }
        Cloudinary cloudinary = new Cloudinary(cloudinaryUrl);
        cloudinary.config.secure = true;
        String path = isTest ? "test/" : "";
        Map uploadResult = cloudinary.uploader().upload(
                wavBytes,
                ObjectUtils.asMap(
                        "resource_type", "raw",
                        "public_id", path + "rashifal/" + userId,
                        "unique_filename", false,
                        "overwrite", true
                )
        );
        return String.valueOf(uploadResult.get("secure_url"));
    }

    private String buildHindiRashifalText(int natalMoonSign, int transitMoonSign, int transitSunSign,
                                           int taraBala, int weekday) {
        String rashi = (natalMoonSign >= 1 && natalMoonSign <= 12) ? RASHI_NAMES[natalMoonSign] : "अज्ञात";
        String transitMoon = (transitMoonSign >= 1 && transitMoonSign <= 12) ? RASHI_NAMES[transitMoonSign] : "अज्ञात";
        String transitSun = (transitSunSign >= 1 && transitSunSign <= 12) ? RASHI_NAMES[transitSunSign] : "अज्ञात";
        String dayName = (weekday >= 0 && weekday <= 6) ? WEEKDAY_NAMES[weekday] : "आज";

        String taraDesc;
        // Odd tara: Janma(1), Vipat(3), Pratyak(5), Naidhana(7) are challenging
        // Even tara: Sampat(2), Kshema(4), Sadhana(6), Mitra(8), Param Mitra(9) are favorable
        if (taraBala == 1 || taraBala == 3 || taraBala == 5 || taraBala == 7) {
            taraDesc = "सावधानी बरतें, कुछ चुनौतियाँ हो सकती हैं";
        } else {
            taraDesc = "शुभ समय है, आगे बढ़ें";
        }

        return String.format(
            "%s राशि वालों के लिए आज %s का राशिफल। " +
            "आज चंद्रमा %s राशि में और सूर्य %s राशि में विराजमान हैं। " +
            "तारा बल के अनुसार आज %s। " +
            "अपने मन को शांत रखें और सकारात्मक सोच अपनाएं। " +
            "परिवार और मित्रों के साथ समय बिताएं। " +
            "आर्थिक मामलों में सतर्कता बरतें और बड़े निर्णय सोच-समझकर लें। " +
            "स्वास्थ्य का विशेष ध्यान रखें।",
            rashi, dayName, transitMoon, transitSun, taraDesc
        );
    }
}
