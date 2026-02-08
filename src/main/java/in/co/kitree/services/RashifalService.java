package in.co.kitree.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.genai.Client;
import com.google.genai.types.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Orchestrates daily rashifal generation:
 * birth profile → Python /rashifal → Hindi TTS → Cloudinary → Firestore
 */
public class RashifalService {

    private static final String TTS_MODEL = "gemini-2.5-pro-preview-tts";
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
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean isTest;

    public RashifalService(Firestore db, boolean isTest) {
        this.db = db;
        this.isTest = isTest;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        String apiKey = loadGeminiApiKey(isTest);
        if (apiKey != null && !apiKey.isEmpty()) {
            this.geminiClient = Client.builder().apiKey(apiKey).build();
            LoggingService.info("rashifal_service_initialized", Map.of("environment", isTest ? "TEST" : "PROD"));
        } else {
            LoggingService.error("rashifal_gemini_no_api_key", Map.of("environment", isTest ? "TEST" : "PROD"));
            this.geminiClient = null;
        }
    }

    private String loadCloudinaryUrl() {
        try {
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));
            return rootNode.path("CLOUDINARY_URL").asText("");
        } catch (IOException e) {
            LoggingService.error("rashifal_cloudinary_secrets_read_failed", e);
            return null;
        }
    }

    private String loadGeminiApiKey(boolean isTest) {
        try {
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));
            String keyName = isTest ? "GEMINI_API_KEY_TEST" : "GEMINI_API_KEY";
            String key = rootNode.path(keyName).asText("");
            if (key.isEmpty()) {
                key = rootNode.path("GEMINI_API_KEY").asText("");
            }
            return key;
        } catch (IOException e) {
            LoggingService.error("rashifal_secrets_read_failed", e);
            return null;
        }
    }

    /**
     * Generate rashifal audio for a user and persist to Firestore.
     * Returns JSON with success/errorMessage.
     */
    public String generateRashifal(String userId) throws Exception {
        if (geminiClient == null) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "Gemini not configured"));
        }

        // 1. Fetch primary birth profile
        QuerySnapshot profileSnapshot = db.collection("users").document(userId)
                .collection("profiles")
                .orderBy("markedPrimaryAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .get();

        if (profileSnapshot.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "No birth profile found"));
        }

        DocumentSnapshot profile = profileSnapshot.getDocuments().get(0);

        // Parse location "lat,lon"
        String locationStr = profile.getString("location");
        if (locationStr == null || !locationStr.contains(",")) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "Invalid location in profile"));
        }
        String[] locationParts = locationStr.split(",");
        double latitude = Double.parseDouble(locationParts[0].trim());
        double longitude = Double.parseDouble(locationParts[1].trim());

        // Parse dob timestamp
        Timestamp dobTimestamp = profile.getTimestamp("dob");
        if (dobTimestamp == null) {
            return objectMapper.writeValueAsString(Map.of("success", false, "errorMessage", "Missing dob in profile"));
        }
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(dobTimestamp.toDate());
        int birthYear = cal.get(Calendar.YEAR);
        int birthMonth = cal.get(Calendar.MONTH) + 1;
        int birthDay = cal.get(Calendar.DAY_OF_MONTH);
        int birthHour = cal.get(Calendar.HOUR_OF_DAY);
        int birthMinute = cal.get(Calendar.MINUTE);

        // 2. Mark as generating
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        DocumentReference rashifalRef = db.collection("users").document(userId)
                .collection("rashifal").document("current");
        Map<String, Object> generatingData = new HashMap<>();
        generatingData.put("status", "generating");
        generatingData.put("date", today);
        generatingData.put("generatedAt", FieldValue.serverTimestamp());
        rashifalRef.set(generatingData).get();

        // 3. Call Python /rashifal
        String rashifalJson = callPythonRashifal(birthYear, birthMonth, birthDay, birthHour, birthMinute, latitude, longitude);
        JsonNode rashifalData = objectMapper.readTree(rashifalJson);

        int natalMoonSign = rashifalData.path("natal_moon_sign").asInt();
        int taraBala = rashifalData.path("tara_bala").asInt();
        int weekday = rashifalData.path("weekday").asInt();
        int transitMoonSign = rashifalData.path("transit_moon_sign").asInt();
        int transitSunSign = rashifalData.path("transit_sun_sign").asInt();

        // 4. Build Hindi TTS text
        String hindiText = buildHindiRashifalText(natalMoonSign, transitMoonSign, transitSunSign, taraBala, weekday);

        // 5. Generate audio via Gemini TTS
        byte[] pcmBytes = generateTtsAudio(hindiText);

        // 6. Convert PCM → WAV
        byte[] wavBytes = pcmToWav(pcmBytes);

        // 7. Upload to Cloudinary
        String audioUrl = uploadToCloudinary(wavBytes, userId);

        // 8. Update Firestore with ready status
        Map<String, Object> readyData = new HashMap<>();
        readyData.put("status", "ready");
        readyData.put("date", today);
        readyData.put("audioUrl", audioUrl);
        readyData.put("natalMoonSign", natalMoonSign);
        readyData.put("taraBala", taraBala);
        readyData.put("generatedAt", FieldValue.serverTimestamp());
        rashifalRef.set(readyData).get();

        LoggingService.info("rashifal_generated", Map.of("userId", userId, "date", today));
        return objectMapper.writeValueAsString(Map.of("success", true, "audioUrl", audioUrl));
    }

    private String callPythonRashifal(int year, int month, int day, int hour, int minute,
                                       double latitude, double longitude) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("api_token", AstrologyServiceConfig.PYTHON_SERVER_API_TOKEN);
        requestBody.put("date", day);
        requestBody.put("month", month);
        requestBody.put("year", year);
        requestBody.put("hour", hour);
        requestBody.put("minute", minute);
        requestBody.put("latitude", latitude);
        requestBody.put("longitude", longitude);

        String baseUrl = AstrologyServiceConfig.isAwsLambdaProviderSelected()
                ? AstrologyServiceConfig.AWS_LAMBDA_BASE_URL
                : AstrologyServiceConfig.PYTHON_SERVER_BASE_URL;
        String apiUrl = baseUrl + "/rashifal";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Rashifal Python API failed: " + response.statusCode());
        }

        // Lambda Function URL wraps body in a JSON envelope
        if (AstrologyServiceConfig.isAwsLambdaProviderSelected()) {
            JsonNode responseNode = objectMapper.readTree(response.body());
            if (responseNode.has("body")) {
                return responseNode.get("body").asText();
            }
        }
        return response.body();
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
        String cloudinaryUrl = loadCloudinaryUrl();
        if (cloudinaryUrl == null || cloudinaryUrl.isEmpty()) {
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
