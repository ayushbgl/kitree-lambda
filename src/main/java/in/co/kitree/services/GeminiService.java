package in.co.kitree.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for generating consultation summaries using Google Gemini AI.
 * Uses Gemini 3 Flash model for multimodal audio analysis.
 */
public class GeminiService {

    private static final String MODEL_NAME = "gemini-3-flash";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Minimum call duration in seconds to generate summary (2 minutes)
    public static final int MIN_DURATION_SECONDS = 120;

    private final Client client;
    private final boolean isTest;

    /**
     * Result object for summary generation.
     */
    public static class GeminiSummaryResult {
        public final boolean success;
        public final Map<String, Object> summary;
        public final String errorMessage;
        public final boolean shouldRetry;

        private GeminiSummaryResult(boolean success, Map<String, Object> summary,
                                    String errorMessage, boolean shouldRetry) {
            this.success = success;
            this.summary = summary;
            this.errorMessage = errorMessage;
            this.shouldRetry = shouldRetry;
        }

        public static GeminiSummaryResult success(Map<String, Object> summary) {
            return new GeminiSummaryResult(true, summary, null, false);
        }

        public static GeminiSummaryResult error(String message, boolean shouldRetry) {
            return new GeminiSummaryResult(false, null, message, shouldRetry);
        }
    }

    /**
     * Creates a new GeminiService instance.
     * Loads API key from secrets.json based on environment.
     *
     * @param isTest true for test environment, false for production
     */
    public GeminiService(boolean isTest) {
        this.isTest = isTest;

        String apiKey = loadApiKey(isTest);
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("[GeminiService] No API key configured for " + (isTest ? "TEST" : "PROD"));
            this.client = null;
        } else {
            this.client = Client.builder().apiKey(apiKey).build();
            System.out.println("[GeminiService] Initialized for " + (isTest ? "TEST" : "PROD") + " environment");
        }
    }

    /**
     * Load Gemini API key from secrets.json.
     */
    private String loadApiKey(boolean isTest) {
        try {
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));
            String keyName = isTest ? "GEMINI_API_KEY_TEST" : "GEMINI_API_KEY";
            String key = rootNode.path(keyName).asText("");

            // Fallback to single key if environment-specific not found
            if (key.isEmpty()) {
                key = rootNode.path("GEMINI_API_KEY").asText("");
            }

            return key;
        } catch (IOException e) {
            System.err.println("[GeminiService] Error reading secrets.json: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if the service is properly configured.
     */
    public boolean isConfigured() {
        return client != null;
    }

    /**
     * Generate a consultation summary from an audio file.
     *
     * @param audioFilePath   Path to the local audio file
     * @param category        Consultation category (HOROSCOPE, TAROT, etc.)
     * @param expertName      Name of the expert
     * @param durationSeconds Duration of the call in seconds
     * @return GeminiSummaryResult with the generated summary or error
     */
    public GeminiSummaryResult generateSummary(Path audioFilePath, String category,
                                                String expertName, long durationSeconds) {
        if (!isConfigured()) {
            return GeminiSummaryResult.error("GeminiService not configured", false);
        }

        if (!Files.exists(audioFilePath)) {
            return GeminiSummaryResult.error("Audio file not found: " + audioFilePath, false);
        }

        try {
            // Read audio file bytes
            byte[] audioBytes = Files.readAllBytes(audioFilePath);
            String mimeType = determineMimeType(audioFilePath.toString());

            System.out.println("[GeminiService] Processing audio file: " + audioFilePath +
                " (" + audioBytes.length / 1024 + " KB, " + mimeType + ")");

            // Build the prompt
            String prompt = buildSummaryPrompt(category, expertName, durationSeconds);

            // Create content with audio and text
            Content content = Content.fromParts(
                Part.fromText(prompt),
                Part.fromBytes(audioBytes, mimeType)
            );

            // Configure generation with JSON schema
            GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(buildResponseSchema())
                .build();

            // Generate content
            GenerateContentResponse response = client.models.generateContent(
                MODEL_NAME,
                content,
                config
            );

            // Parse response
            String responseText = response.text();
            if (responseText == null || responseText.isEmpty()) {
                return GeminiSummaryResult.error("Empty response from Gemini", true);
            }

            System.out.println("[GeminiService] Received response (" + responseText.length() + " chars)");

            // Parse JSON response
            Map<String, Object> summary = parseJsonResponse(responseText);
            if (summary == null || summary.isEmpty()) {
                return GeminiSummaryResult.error("Failed to parse Gemini response as JSON", true);
            }

            // Add metadata
            summary.put("generated_at", com.google.cloud.Timestamp.now());
            summary.put("language", "en");
            summary.put("generation_model", MODEL_NAME);

            return GeminiSummaryResult.success(summary);

        } catch (Exception e) {
            System.err.println("[GeminiService] Error generating summary: " + e.getMessage());
            e.printStackTrace();

            // Determine if error is retryable
            boolean shouldRetry = isRetryableError(e);
            return GeminiSummaryResult.error(e.getMessage(), shouldRetry);
        }
    }

    /**
     * Generate summary from a file that's already uploaded to Gemini Files API.
     * Use this for larger files (> 20MB).
     */
    public GeminiSummaryResult generateSummaryFromUri(String fileUri, String mimeType,
                                                       String category, String expertName,
                                                       long durationSeconds) {
        if (!isConfigured()) {
            return GeminiSummaryResult.error("GeminiService not configured", false);
        }

        try {
            String prompt = buildSummaryPrompt(category, expertName, durationSeconds);

            Content content = Content.fromParts(
                Part.fromText(prompt),
                Part.fromUri(fileUri, mimeType)
            );

            GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(buildResponseSchema())
                .build();

            GenerateContentResponse response = client.models.generateContent(
                MODEL_NAME,
                content,
                config
            );

            String responseText = response.text();
            if (responseText == null || responseText.isEmpty()) {
                return GeminiSummaryResult.error("Empty response from Gemini", true);
            }

            Map<String, Object> summary = parseJsonResponse(responseText);
            if (summary == null || summary.isEmpty()) {
                return GeminiSummaryResult.error("Failed to parse Gemini response as JSON", true);
            }

            summary.put("generated_at", com.google.cloud.Timestamp.now());
            summary.put("language", "en");
            summary.put("generation_model", MODEL_NAME);

            return GeminiSummaryResult.success(summary);

        } catch (Exception e) {
            System.err.println("[GeminiService] Error generating summary from URI: " + e.getMessage());
            e.printStackTrace();
            return GeminiSummaryResult.error(e.getMessage(), isRetryableError(e));
        }
    }

    /**
     * Upload a file to Gemini Files API and return the file URI.
     * Use this for files > 20MB.
     */
    public String uploadFile(Path filePath, String mimeType) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("GeminiService not configured");
        }

        UploadFileConfig uploadConfig = UploadFileConfig.builder()
            .mimeType(mimeType)
            .build();

        com.google.genai.types.File uploadedFile = client.files.upload(
            filePath.toString(),
            uploadConfig
        );

        return uploadedFile.name().orElseThrow(() ->
            new RuntimeException("Failed to get uploaded file name"));
    }

    /**
     * Build the prompt for summary generation.
     */
    private String buildSummaryPrompt(String category, String expertName, long durationSeconds) {
        String categoryContext = getCategoryContext(category);
        int durationMinutes = (int) (durationSeconds / 60);

        return String.format("""
            You are analyzing an astrology consultation audio recording between a user and an expert.

            **Consultation Context:**
            - Category: %s
            - Expert Name: %s
            - Duration: approximately %d minutes

            **Category-Specific Context:**
            %s

            **Instructions:**
            1. Listen to the entire audio recording carefully
            2. Identify the user's primary concern and questions they asked
            3. Extract all predictions, important dates, and remedies mentioned by the expert
            4. Categorize topics discussed (career, finance, health, relationships, spirituality, family, education, travel)
            5. Assess the overall sentiment of the consultation (positive, neutral, negative, or mixed)
            6. Note any follow-up recommendations made by the expert

            **Output Requirements:**
            - Output ONLY valid JSON matching the schema
            - All dates should be in "Month YYYY" or "Month DD, YYYY" format
            - Remedy types must be one of: mantra, product, puja, donation, lifestyle
            - Likelihood values: highly_likely, likely, possible, unlikely
            - Priority values: essential, recommended, optional
            - If a section has no relevant content, use an empty array [] or appropriate null
            - Do not include any markdown formatting, only raw JSON
            - Be concise but comprehensive

            **Important Notes:**
            - If the audio is unclear or mostly silent, return minimal data with what you can extract
            - Focus on actionable insights and specific predictions
            - Preserve the expert's specific advice and recommendations accurately
            """,
            category != null ? category : "HOROSCOPE",
            expertName != null ? expertName : "Expert",
            durationMinutes,
            categoryContext
        );
    }

    /**
     * Get category-specific context for the prompt.
     */
    private String getCategoryContext(String category) {
        if (category == null) category = "HOROSCOPE";

        return switch (category.toUpperCase()) {
            case "TAROT" -> """
                This is a Tarot reading consultation. Pay attention to:
                - Card names and their interpretations
                - Spread positions and their meanings
                - Symbolic messages and guidance
                - Future possibilities revealed by the cards
                """;
            case "NUMEROLOGY" -> """
                This is a Numerology consultation. Pay attention to:
                - Life path numbers, destiny numbers, and other numerological calculations
                - Lucky numbers and dates
                - Name analysis and vibrations
                - Yearly/monthly predictions based on numbers
                """;
            case "PALMISTRY" -> """
                This is a Palmistry reading consultation. Pay attention to:
                - Lines discussed (heart line, head line, life line, fate line)
                - Mounts and their significance
                - Hand shape analysis
                - Predictions based on palm features
                """;
            case "VASTU" -> """
                This is a Vastu consultation. Pay attention to:
                - Directional recommendations
                - Room placements and corrections
                - Remedial measures for Vastu defects
                - Auspicious placements for prosperity
                """;
            default -> """
                This is an astrological horoscope consultation. Pay attention to:
                - Planetary positions and their effects (grahas)
                - Dasha periods (mahadasha, antardasha)
                - House placements and their significance
                - Transit effects and timing predictions
                - Yogas and doshas mentioned
                """;
        };
    }

    /**
     * Build the JSON response schema for structured output.
     */
    private Schema buildResponseSchema() {
        return Schema.builder()
            .type("object")
            .properties(Map.ofEntries(
                Map.entry("headline", Schema.builder().type("string").build()),
                Map.entry("brief_summary", Schema.builder().type("string").build()),
                Map.entry("primary_concern", Schema.builder().type("string").build()),
                Map.entry("sentiment", Schema.builder()
                    .type("string")
                    .enum_(List.of("positive", "neutral", "negative", "mixed"))
                    .build()),
                Map.entry("important_dates", Schema.builder()
                    .type("array")
                    .items(Schema.builder()
                        .type("object")
                        .properties(Map.of(
                            "date", Schema.builder().type("string").build(),
                            "significance", Schema.builder().type("string").build(),
                            "is_auspicious", Schema.builder().type("boolean").build()
                        ))
                        .build())
                    .build()),
                Map.entry("topics", Schema.builder()
                    .type("array")
                    .items(Schema.builder()
                        .type("object")
                        .properties(Map.of(
                            "id", Schema.builder().type("string").build(),
                            "category", Schema.builder().type("string").build(),
                            "title", Schema.builder().type("string").build(),
                            "summary", Schema.builder().type("string").build(),
                            "expert_advice", Schema.builder().type("string").build()
                        ))
                        .build())
                    .build()),
                Map.entry("predictions", Schema.builder()
                    .type("array")
                    .items(Schema.builder()
                        .type("object")
                        .properties(Map.of(
                            "id", Schema.builder().type("string").build(),
                            "category", Schema.builder().type("string").build(),
                            "prediction_text", Schema.builder().type("string").build(),
                            "timeframe", Schema.builder().type("string").build(),
                            "likelihood", Schema.builder().type("string").build(),
                            "astrological_factors", Schema.builder()
                                .type("array")
                                .items(Schema.builder().type("string").build())
                                .build()
                        ))
                        .build())
                    .build()),
                Map.entry("remedies", Schema.builder()
                    .type("array")
                    .items(Schema.builder()
                        .type("object")
                        .properties(Map.ofEntries(
                            Map.entry("id", Schema.builder().type("string").build()),
                            Map.entry("type", Schema.builder()
                                .type("string")
                                .enum_(List.of("mantra", "product", "puja", "donation", "lifestyle"))
                                .build()),
                            Map.entry("title", Schema.builder().type("string").build()),
                            Map.entry("description", Schema.builder().type("string").build()),
                            Map.entry("purpose", Schema.builder().type("string").build()),
                            Map.entry("priority", Schema.builder()
                                .type("string")
                                .enum_(List.of("essential", "recommended", "optional"))
                                .build()),
                            Map.entry("timing", Schema.builder().type("string").build()),
                            Map.entry("frequency", Schema.builder().type("string").build()),
                            Map.entry("mantra_text", Schema.builder().type("string").build()),
                            Map.entry("repetition_count", Schema.builder().type("integer").build()),
                            Map.entry("product_type", Schema.builder().type("string").build()),
                            Map.entry("puja_type", Schema.builder().type("string").build())
                        ))
                        .build())
                    .build()),
                Map.entry("insights", Schema.builder()
                    .type("array")
                    .items(Schema.builder()
                        .type("object")
                        .properties(Map.of(
                            "id", Schema.builder().type("string").build(),
                            "category", Schema.builder().type("string").build(),
                            "text", Schema.builder().type("string").build(),
                            "planetary_influence", Schema.builder().type("string").build()
                        ))
                        .build())
                    .build()),
                Map.entry("follow_up", Schema.builder()
                    .type("object")
                    .properties(Map.of(
                        "recommended", Schema.builder().type("boolean").build(),
                        "timeframe", Schema.builder().type("string").build(),
                        "reason", Schema.builder().type("string").build(),
                        "same_expert_recommended", Schema.builder().type("boolean").build()
                    ))
                    .build())
            ))
            .required(List.of("headline", "brief_summary", "sentiment"))
            .build();
    }

    /**
     * Parse JSON response from Gemini.
     */
    private Map<String, Object> parseJsonResponse(String jsonText) {
        try {
            // Clean up response if it contains markdown code blocks
            String cleanJson = jsonText.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            } else if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            return objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            System.err.println("[GeminiService] Failed to parse JSON: " + e.getMessage());
            System.err.println("[GeminiService] Raw response: " + jsonText);
            return null;
        }
    }

    /**
     * Determine MIME type from file path.
     */
    private String determineMimeType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".webm")) return "audio/webm";
        if (lower.endsWith(".mp4")) return "audio/mp4"; // Assuming audio-only MP4
        return "audio/mpeg"; // Default
    }

    /**
     * Determine if an error is retryable (transient).
     */
    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;

        message = message.toLowerCase();

        // Rate limit errors
        if (message.contains("rate") || message.contains("quota") || message.contains("429")) {
            return true;
        }

        // Temporary server errors
        if (message.contains("500") || message.contains("502") || message.contains("503") ||
            message.contains("504") || message.contains("timeout")) {
            return true;
        }

        // Network errors
        if (message.contains("connection") || message.contains("network")) {
            return true;
        }

        return false;
    }
}
