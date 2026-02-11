package in.co.kitree.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;

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
            LoggingService.error("gemini_no_api_key", Map.of("environment", isTest ? "TEST" : "PROD"));
            this.client = null;
        } else {
            this.client = Client.builder().apiKey(apiKey).build();
            LoggingService.info("gemini_initialized", Map.of("environment", isTest ? "TEST" : "PROD"));
        }
    }

    private static String loadApiKey(boolean isTest) {
        String key = SecretsProvider.getString(isTest ? "GEMINI_API_KEY_TEST" : "GEMINI_API_KEY");
        if (key.isEmpty()) {
            key = SecretsProvider.getString("GEMINI_API_KEY");
        }
        return key;
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

            LoggingService.debug("gemini_processing_audio", Map.of("filePath", audioFilePath.toString(), "sizeKB", audioBytes.length / 1024, "mimeType", mimeType));

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

            LoggingService.debug("gemini_response_received", Map.of("responseLength", responseText.length()));

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
            LoggingService.error("gemini_generate_summary_failed", e);

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
            LoggingService.error("gemini_generate_summary_from_uri_failed", e);
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
     * Supports flexible content blocks for different consultation types.
     */
    private String buildSummaryPrompt(String category, String expertName, long durationSeconds) {
        String categoryContext = getCategoryContext(category);
        int durationMinutes = (int) (durationSeconds / 60);

        return String.format("""
            You are analyzing a spiritual consultation audio recording between a user and an expert.
            The consultation may cover multiple spiritual domains (astrology, tarot, numerology, etc.) - treat it as a unified, blended experience.

            **Consultation Context:**
            - Primary Category: %s
            - Expert Name: %s
            - Duration: approximately %d minutes

            **Category-Specific Context:**
            %s

            **Instructions:**
            1. Listen to the entire audio recording carefully
            2. Identify the user's primary concern and questions asked
            3. Extract ALL meaningful content into content_blocks with appropriate display_types
            4. The summary should feel like a natural, unified reading - NOT sectioned by category
            5. Assess the overall sentiment (positive, neutral, negative, mixed)

            **Content Block Types (display_type):**
            Use these display_type values based on what content is being conveyed:
            - "text": General text, summaries, explanations
            - "prediction": Future predictions with timeframe and likelihood
            - "date_highlight": Important dates with significance and auspiciousness
            - "remedy_mantra": Mantras with text, repetitions, timing
            - "remedy_product": Product recommendations (crystals, yantras, etc.)
            - "remedy_ritual": Pujas, rituals, donations
            - "remedy_lifestyle": Lifestyle changes, dos/don'ts
            - "tarot_card": Tarot card drawn (use card_id like "major_fool", "cups_ace", "swords_king")
            - "planetary_info": Planet positions, transits, dashas
            - "numerology_number": Important numbers with meanings
            - "vastu_direction": Directional/placement guidance
            - "chakra_info": Chakra-related insights
            - "energy_reading": Reiki/energy observations
            - "insight": General spiritual insights
            - "quote": Notable expert quotes to highlight

            **Product Recommendations (IMPORTANT):**
            When the expert recommends a physical product (gemstone, crystal, yantra, bracelet, pendant, mala, rudraksha), extract these attributes:
            - product_attributes.material: ["rose_quartz", "blue_sapphire", "amethyst", "citrine", "tiger_eye", "rudraksha", etc.]
            - product_attributes.product_type: "bracelet", "pendant", "ring", "mala", "yantra", "statue", null for any
            - product_attributes.planets: ["saturn", "venus", "jupiter", "mars", "mercury", "sun", "moon", "rahu", "ketu"]
            - product_attributes.purpose: ["love", "career", "protection", "health", "wealth", "peace", "clarity"]
            - product_attributes.chakras: ["root", "sacral", "solar_plexus", "heart", "throat", "third_eye", "crown"]
            - product_attributes.color: ["blue", "pink", "green", "yellow", "red", "white", "black", "purple"]
            - expert_quote: Exact quote of what the expert said about this product

            **Tarot Card IDs (if tarot reading):**
            Use these exact card_id formats:
            - Major Arcana: "major_fool", "major_magician", "major_high_priestess", "major_empress", "major_emperor", "major_hierophant", "major_lovers", "major_chariot", "major_strength", "major_hermit", "major_wheel", "major_justice", "major_hanged_man", "major_death", "major_temperance", "major_devil", "major_tower", "major_star", "major_moon", "major_sun", "major_judgement", "major_world"
            - Minor Arcana: "{suit}_{rank}" like "cups_ace", "wands_two", "swords_queen", "pentacles_king"
            - Include position (e.g., "past", "present", "future", "outcome") and is_reversed if mentioned

            **Output Requirements:**
            - Output ONLY valid JSON matching the schema
            - All dates in "Month YYYY" or "Month DD, YYYY" format
            - Each content_block needs: id (unique), display_type, category, title, content (main text)
            - Add type-specific fields based on display_type (see schema)
            - If a section has no content, use empty array []
            - Be concise but comprehensive - capture all meaningful information
            - Blend insights naturally - don't artificially separate by consultation type

            **Important:**
            - If audio is unclear, return minimal data with what you can extract
            - Focus on actionable insights and specific predictions
            - Preserve expert's exact advice and product recommendations
            - For product remedies, always include the expert_quote of what they said
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
                - Card names and their interpretations (use exact card_id format)
                - Spread positions (past, present, future, outcome, etc.)
                - Whether cards are reversed or upright
                - Symbolic messages and guidance
                - Future possibilities revealed by the cards
                """;
            case "NUMEROLOGY" -> """
                This is a Numerology consultation. Pay attention to:
                - Life path numbers, destiny numbers, and other numerological calculations
                - Lucky numbers and dates
                - Name analysis and vibrations
                - Yearly/monthly predictions based on numbers
                - Number-based remedies and recommendations
                """;
            case "PALMISTRY" -> """
                This is a Palmistry reading consultation. Pay attention to:
                - Lines discussed (heart line, head line, life line, fate line)
                - Mounts and their significance
                - Hand shape analysis
                - Predictions based on palm features
                - Any recommended remedies based on palm reading
                """;
            case "VASTU" -> """
                This is a Vastu consultation. Pay attention to:
                - Directional recommendations (use vastu_direction display_type)
                - Room placements and corrections
                - Remedial measures for Vastu defects
                - Auspicious placements for prosperity
                - Specific objects to place or remove
                """;
            case "REIKI", "ENERGY", "HEALING" -> """
                This is a Reiki/Energy healing consultation. Pay attention to:
                - Chakra observations and blockages
                - Energy flow assessments
                - Healing techniques recommended
                - Spiritual guidance and affirmations
                - Any crystals or tools recommended for energy work
                - Meditation or visualization practices suggested
                """;
            default -> """
                This is an astrological horoscope consultation. Pay attention to:
                - Planetary positions and their effects (grahas)
                - Dasha periods (mahadasha, antardasha)
                - House placements and their significance
                - Transit effects and timing predictions
                - Yogas and doshas mentioned
                - Gemstone and remedy recommendations with specific materials
                """;
        };
    }

    /**
     * Build the JSON response schema for structured output.
     * Uses flexible content_blocks approach to support various consultation types.
     */
    private Schema buildResponseSchema() {
        // Product attributes schema for remedy product matching
        Schema productAttributesSchema = Schema.builder()
            .type("object")
            .properties(Map.of(
                "material", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build(),
                "product_type", Schema.builder().type("string").build(),
                "color", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build(),
                "purpose", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build(),
                "planets", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build(),
                "chakras", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build()
            ))
            .build();

        // Flexible content block schema
        Schema contentBlockSchema = Schema.builder()
            .type("object")
            .properties(Map.ofEntries(
                // Common fields for all blocks
                Map.entry("id", Schema.builder().type("string").build()),
                Map.entry("display_type", Schema.builder()
                    .type("string")
                    .enum_(List.of(
                        "text", "prediction", "date_highlight",
                        "remedy_mantra", "remedy_product", "remedy_ritual", "remedy_lifestyle",
                        "tarot_card", "planetary_info", "numerology_number",
                        "vastu_direction", "chakra_info", "energy_reading",
                        "insight", "quote"
                    ))
                    .build()),
                Map.entry("category", Schema.builder().type("string").build()),
                Map.entry("title", Schema.builder().type("string").build()),
                Map.entry("content", Schema.builder().type("string").build()),

                // Prediction fields
                Map.entry("timeframe", Schema.builder().type("string").build()),
                Map.entry("likelihood", Schema.builder()
                    .type("string")
                    .enum_(List.of("highly_likely", "likely", "possible", "unlikely"))
                    .build()),
                Map.entry("astrological_factors", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build()),

                // Date highlight fields
                Map.entry("date", Schema.builder().type("string").build()),
                Map.entry("is_auspicious", Schema.builder().type("boolean").build()),

                // Remedy common fields
                Map.entry("priority", Schema.builder()
                    .type("string")
                    .enum_(List.of("essential", "recommended", "optional"))
                    .build()),
                Map.entry("timing", Schema.builder().type("string").build()),
                Map.entry("frequency", Schema.builder().type("string").build()),
                Map.entry("expert_quote", Schema.builder().type("string").build()),

                // Mantra-specific
                Map.entry("mantra_text", Schema.builder().type("string").build()),
                Map.entry("repetition_count", Schema.builder().type("integer").build()),

                // Product-specific (for matching)
                Map.entry("product_attributes", productAttributesSchema),

                // Ritual/Puja specific
                Map.entry("ritual_type", Schema.builder().type("string").build()),
                Map.entry("donation_recipient", Schema.builder().type("string").build()),
                Map.entry("donation_items", Schema.builder()
                    .type("array")
                    .items(Schema.builder().type("string").build())
                    .build()),

                // Tarot card fields
                Map.entry("card_id", Schema.builder().type("string").build()),
                Map.entry("position", Schema.builder().type("string").build()),
                Map.entry("is_reversed", Schema.builder().type("boolean").build()),
                Map.entry("card_meaning", Schema.builder().type("string").build()),

                // Planetary info fields
                Map.entry("planet", Schema.builder().type("string").build()),
                Map.entry("house", Schema.builder().type("integer").build()),
                Map.entry("sign", Schema.builder().type("string").build()),
                Map.entry("dasha_period", Schema.builder().type("string").build()),
                Map.entry("transit_effect", Schema.builder().type("string").build()),

                // Numerology fields
                Map.entry("number", Schema.builder().type("integer").build()),
                Map.entry("number_type", Schema.builder().type("string").build()),

                // Vastu fields
                Map.entry("direction", Schema.builder().type("string").build()),
                Map.entry("room", Schema.builder().type("string").build()),
                Map.entry("placement_item", Schema.builder().type("string").build()),

                // Chakra/Energy fields
                Map.entry("chakra", Schema.builder().type("string").build()),
                Map.entry("energy_state", Schema.builder().type("string").build()),

                // Quote fields
                Map.entry("speaker", Schema.builder().type("string").build())
            ))
            .required(List.of("id", "display_type", "content"))
            .build();

        // Main schema
        return Schema.builder()
            .type("object")
            .properties(Map.ofEntries(
                // Core summary fields
                Map.entry("headline", Schema.builder().type("string").build()),
                Map.entry("brief_summary", Schema.builder().type("string").build()),
                Map.entry("primary_concern", Schema.builder().type("string").build()),
                Map.entry("sentiment", Schema.builder()
                    .type("string")
                    .enum_(List.of("positive", "neutral", "negative", "mixed"))
                    .build()),

                // Consultation types detected (for multi-type blending)
                Map.entry("consultation_types", Schema.builder()
                    .type("array")
                    .items(Schema.builder()
                        .type("string")
                        .enum_(List.of("astrology", "tarot", "numerology", "palmistry",
                            "vastu", "reiki", "energy_healing", "general"))
                        .build())
                    .build()),

                // Flexible content blocks
                Map.entry("content_blocks", Schema.builder()
                    .type("array")
                    .items(contentBlockSchema)
                    .build()),

                // Follow-up recommendation
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
            .required(List.of("headline", "brief_summary", "sentiment", "content_blocks"))
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
            LoggingService.warn("gemini_json_parse_failed", Map.of("error", e.getMessage()));
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
