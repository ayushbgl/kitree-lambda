package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import in.co.kitree.pojos.MatchedProduct;
import in.co.kitree.pojos.OnDemandConsultationOrder;
import in.co.kitree.pojos.RemedyAttributes;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

/**
 * Service for generating AI-powered consultation summaries using Gemini.
 *
 * Supports multiple consultation types: astrology, tarot, numerology, palmistry, vastu, reiki.
 * Uses flexible content_blocks schema to handle varying content types.
 *
 * Uses Firestore-based state management for retry handling:
 * - summaryStatus: PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
 * - summaryRetryCount: Number of retry attempts
 * - summaryLastAttempt: Timestamp of last attempt
 * - summaryError: Last error message
 *
 * Summary schema:
 * - headline, brief_summary, primary_concern, sentiment
 * - consultation_types: array of detected types (astrology, tarot, numerology, etc.)
 * - content_blocks: array of flexible blocks with display_type determining rendering
 *   - Common: id, display_type, category, title, content
 *   - Type-specific fields based on display_type (prediction, remedy_product, tarot_card, etc.)
 *   - remedy_product blocks include matched_products array (from ProductMatchingService)
 * - follow_up: {recommended, timeframe, reason, same_expert_recommended}
 *
 * Product Matching:
 * - remedy_product blocks have product_attributes extracted by Gemini
 * - MockProductMatchingService (swappable for VectorProductMatchingService) finds catalog matches
 * - Matched products are added as matched_products array to each remedy block
 */
public class ConsultationSummaryService {

    private final Firestore db;
    private final OnDemandConsultationService consultationService;
    private final StreamService streamService;
    private final GeminiService geminiService;
    private final ProductMatchingService productMatchingService;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // Minimum call duration in seconds to generate summary (configurable constant)
    public static final int MIN_DURATION_SECONDS = 120; // 2 minutes

    // Maximum retries within a single invocation (Firestore handles cross-invocation retries)
    private static final int MAX_IMMEDIATE_RETRIES = 2;

    // Call type used for Stream calls
    private static final String STREAM_CALL_TYPE = "consultation_audio";

    public ConsultationSummaryService(Firestore db, boolean isTest) {
        this.db = db;
        this.consultationService = new OnDemandConsultationService(db);
        this.streamService = new StreamService(isTest);
        this.geminiService = new GeminiService(isTest);
        // Using MockProductMatchingService for now - can be swapped for VectorProductMatchingService later
        this.productMatchingService = new MockProductMatchingService();
    }

    /**
     * Result object for summary generation.
     */
    public static class SummaryResult {
        public final boolean success;
        public final String status;  // "generated", "skipped", "error", "pending_retry", "already_processing"
        public final Map<String, Object> summary;
        public final String errorMessage;

        private SummaryResult(boolean success, String status, Map<String, Object> summary,
                              String errorMessage) {
            this.success = success;
            this.status = status;
            this.summary = summary;
            this.errorMessage = errorMessage;
        }

        public static SummaryResult success(Map<String, Object> summary) {
            return new SummaryResult(true, "generated", summary, null);
        }

        public static SummaryResult skipped(String reason) {
            return new SummaryResult(true, "skipped", null, reason);
        }

        public static SummaryResult error(String message) {
            return new SummaryResult(false, "error", null, message);
        }

        public static SummaryResult pendingRetry(String message) {
            return new SummaryResult(false, "pending_retry", null, message);
        }

        public static SummaryResult alreadyProcessing() {
            return new SummaryResult(false, "already_processing", null,
                "Summary generation already in progress or completed");
        }
    }

    /**
     * Generate consultation summary for a completed order.
     * Uses Gemini AI to analyze the call recording.
     *
     * This method uses Firestore transactions for:
     * - Optimistic locking to prevent parallel processing
     * - State tracking for retry handling across Lambda invocations
     *
     * @param userId  The user's ID
     * @param orderId The order ID
     * @return SummaryResult with generated summary or error
     */
    public SummaryResult generateSummary(String userId, String orderId) {
        Path tempAudioFile = null;

        try {
            LoggingService.setContext(userId, orderId, null);
            LoggingService.info("generate_summary_started");

            // 1. Get the order
            OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
            if (order == null) {
                return SummaryResult.error("Order not found");
            }

            // 2. Verify order is COMPLETED
            if (!"COMPLETED".equals(order.getStatus())) {
                return SummaryResult.error("Order is not completed. Current status: " + order.getStatus());
            }

            // 3. Check if summary already exists
            if (order.getSummary() != null) {
                LoggingService.info("summary_already_exists");
                return SummaryResult.success(order.getSummary());
            }

            // 4. Check current summary status
            String currentStatus = order.getSummaryStatus();
            if (OnDemandConsultationService.SUMMARY_STATUS_COMPLETED.equals(currentStatus)) {
                LoggingService.info("summary_already_completed");
                return SummaryResult.success(order.getSummary());
            }
            if (OnDemandConsultationService.SUMMARY_STATUS_SKIPPED.equals(currentStatus)) {
                LoggingService.info("summary_previously_skipped");
                return SummaryResult.skipped(order.getSummaryError());
            }
            if (OnDemandConsultationService.SUMMARY_STATUS_FAILED.equals(currentStatus)) {
                LoggingService.info("summary_previously_failed");
                return SummaryResult.error("Summary generation previously failed: " + order.getSummaryError());
            }

            // 5. Try to claim the order for processing (optimistic lock)
            boolean claimed = consultationService.tryClaimSummaryGeneration(userId, orderId);
            if (!claimed) {
                LoggingService.info("summary_claim_failed", Map.of(
                    "currentStatus", currentStatus != null ? currentStatus : "null"
                ));
                return SummaryResult.alreadyProcessing();
            }

            LoggingService.info("summary_claimed_for_processing");

            // 6. Check call duration - skip if too short
            Long billableSeconds = order.getBillableSeconds();
            if (billableSeconds == null) {
                billableSeconds = order.getDurationSeconds();
            }
            if (billableSeconds == null || billableSeconds < MIN_DURATION_SECONDS) {
                String reason = "Call duration (" + (billableSeconds != null ? billableSeconds : 0) +
                    "s) is less than minimum required (" + MIN_DURATION_SECONDS + "s)";
                LoggingService.info("summary_skipped_short_call", Map.of(
                    "billableSeconds", billableSeconds != null ? billableSeconds : 0,
                    "minRequired", MIN_DURATION_SECONDS
                ));
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_SKIPPED, null, reason, false);
                return SummaryResult.skipped(reason);
            }

            // 7. Verify Gemini service is configured
            if (!geminiService.isConfigured()) {
                String error = "Gemini service not configured";
                LoggingService.warn("gemini_not_configured");
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_FAILED, null, error, false);
                return SummaryResult.error(error);
            }

            // 8. Get recordings from GetStream
            String streamCallCid = order.getStreamCallCid();
            if (streamCallCid == null || streamCallCid.isEmpty()) {
                String reason = "No Stream call ID associated with order";
                LoggingService.warn("no_stream_call_cid");
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_SKIPPED, null, reason, false);
                return SummaryResult.skipped(reason);
            }

            String[] callCidParts = StreamService.parseCallCid(streamCallCid);
            if (callCidParts == null) {
                callCidParts = new String[]{STREAM_CALL_TYPE, orderId};
            }

            List<StreamService.RecordingInfo> recordings =
                streamService.getCallRecordings(callCidParts[0], callCidParts[1]);

            if (recordings.isEmpty()) {
                String reason = "No recordings available for this call";
                LoggingService.info("no_recordings_found", Map.of(
                    "callType", callCidParts[0],
                    "callId", callCidParts[1]
                ));
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_SKIPPED, null, reason, false);
                return SummaryResult.skipped(reason);
            }

            // 9. Download the first recording to temp file
            StreamService.RecordingInfo recording = recordings.get(0);
            LoggingService.info("downloading_recording", Map.of(
                "filename", recording.getFilename() != null ? recording.getFilename() : "unknown",
                "durationSeconds", recording.getDurationSeconds()
            ));

            tempAudioFile = downloadRecordingToTemp(recording.getUrl(), recording.getFilename());
            if (tempAudioFile == null) {
                String error = "Failed to download recording";
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_PENDING, null, error, true);
                return SummaryResult.pendingRetry(error);
            }

            // 10. Generate summary using Gemini with immediate retries
            GeminiService.GeminiSummaryResult geminiResult = null;
            int attempt = 0;
            final long finalBillableSeconds = billableSeconds;

            while (attempt < MAX_IMMEDIATE_RETRIES) {
                attempt++;
                LoggingService.info("gemini_attempt", Map.of("attempt", attempt));

                geminiResult = geminiService.generateSummary(
                    tempAudioFile,
                    order.getCategory(),
                    order.getExpertName(),
                    finalBillableSeconds
                );

                if (geminiResult.success) {
                    break;
                }

                if (!geminiResult.shouldRetry || attempt >= MAX_IMMEDIATE_RETRIES) {
                    break;
                }

                // Wait before retry (exponential backoff)
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 11. Handle Gemini result
            if (geminiResult == null) {
                String error = "Gemini service returned null";
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_PENDING, null, error, true);
                return SummaryResult.pendingRetry(error);
            }

            if (!geminiResult.success) {
                String error = geminiResult.errorMessage != null ? geminiResult.errorMessage : "Unknown error";

                if (geminiResult.shouldRetry) {
                    LoggingService.warn("gemini_failed_will_retry", Map.of("error", error));
                    consultationService.updateSummaryStatus(userId, orderId,
                        OnDemandConsultationService.SUMMARY_STATUS_PENDING, null, error, true);
                    return SummaryResult.pendingRetry(error);
                } else {
                    LoggingService.error("gemini_failed_permanent",
                        new RuntimeException(error), Map.of("orderId", orderId));
                    consultationService.updateSummaryStatus(userId, orderId,
                        OnDemandConsultationService.SUMMARY_STATUS_FAILED, null, error, false);
                    return SummaryResult.error(error);
                }
            }

            Map<String, Object> summary = geminiResult.summary;

            // 12. Process product recommendations and match to catalog
            int matchedProducts = processProductRecommendations(summary);
            if (matchedProducts > 0) {
                LoggingService.info("products_matched", Map.of(
                    "matchedRemedyCount", matchedProducts
                ));
            }

            // 13. Store summary and mark as completed
            consultationService.updateSummaryStatus(userId, orderId,
                OnDemandConsultationService.SUMMARY_STATUS_COMPLETED, summary, null, false);

            LoggingService.info("summary_generated_successfully", Map.of(
                "hasContentBlocks", summary.containsKey("content_blocks"),
                "contentBlockCount", summary.containsKey("content_blocks") ?
                    ((List<?>) summary.get("content_blocks")).size() : 0,
                "hasFollowUp", summary.containsKey("follow_up")
            ));

            return SummaryResult.success(summary);

        } catch (Exception e) {
            LoggingService.error("generate_summary_error", e, Map.of("orderId", orderId));

            // Try to update status to allow retry
            try {
                consultationService.updateSummaryStatus(userId, orderId,
                    OnDemandConsultationService.SUMMARY_STATUS_PENDING, null, e.getMessage(), true);
            } catch (Exception updateEx) {
                LoggingService.warn("failed_to_update_summary_status", Map.of(
                    "error", updateEx.getMessage()
                ));
            }

            return SummaryResult.pendingRetry(e.getMessage());

        } finally {
            // Always cleanup temp file
            if (tempAudioFile != null) {
                try {
                    Files.deleteIfExists(tempAudioFile);
                    LoggingService.info("temp_file_cleaned", Map.of(
                        "path", tempAudioFile.toString()
                    ));
                } catch (Exception e) {
                    LoggingService.warn("temp_file_cleanup_failed", Map.of(
                        "path", tempAudioFile.toString(),
                        "error", e.getMessage()
                    ));
                }
            }
        }
    }

    /**
     * Download recording from URL to temp file in /tmp directory.
     *
     * @param url      Pre-signed S3 URL
     * @param filename Original filename (for extension)
     * @return Path to temp file, or null if download failed
     */
    private Path downloadRecordingToTemp(String url, String filename) {
        try {
            // Determine file extension
            String extension = ".mp4";
            if (filename != null) {
                int lastDot = filename.lastIndexOf('.');
                if (lastDot > 0) {
                    extension = filename.substring(lastDot);
                }
            }

            // Create temp file in /tmp (Lambda's writable directory)
            Path tempFile = Files.createTempFile("recording_", extension);

            LoggingService.info("downloading_to_temp", Map.of(
                "tempPath", tempFile.toString()
            ));

            // Download file
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMinutes(3))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                LoggingService.error("download_failed", null, Map.of(
                    "statusCode", response.statusCode()
                ));
                Files.deleteIfExists(tempFile);
                return null;
            }

            // Copy stream to file
            try (InputStream inputStream = response.body()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            long fileSize = Files.size(tempFile);
            LoggingService.info("download_complete", Map.of(
                "sizeBytes", fileSize,
                "sizeKB", fileSize / 1024
            ));

            return tempFile;

        } catch (Exception e) {
            LoggingService.error("download_exception", e, Map.of("url", url));
            return null;
        }
    }

    /**
     * Get existing summary for an order without regenerating.
     *
     * @param userId  The user's ID
     * @param orderId The order ID
     * @return SummaryResult with existing summary or error
     */
    public SummaryResult getSummary(String userId, String orderId) {
        try {
            OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
            if (order == null) {
                return SummaryResult.error("Order not found");
            }

            Map<String, Object> summary = order.getSummary();
            if (summary == null) {
                return SummaryResult.error("Summary not available");
            }

            return SummaryResult.success(summary);

        } catch (Exception e) {
            LoggingService.error("get_summary_error", e, Map.of("orderId", orderId));
            return SummaryResult.error(e.getMessage());
        }
    }

    /**
     * Queue an order for summary generation.
     * Called after billing completion - sets status to PENDING for the cron job to pick up.
     *
     * @param userId  The user's ID
     * @param orderId The order ID
     */
    public void queueSummaryGeneration(String userId, String orderId) {
        try {
            consultationService.markSummaryPending(userId, orderId);
            LoggingService.info("summary_queued", Map.of(
                "userId", userId,
                "orderId", orderId
            ));
        } catch (Exception e) {
            LoggingService.warn("failed_to_queue_summary", Map.of(
                "orderId", orderId,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Process pending summary retries.
     * Called by the cron job to process orders that need summary generation.
     *
     * @param batchSize Maximum number of orders to process in this invocation
     * @return Map with processing results (processed, succeeded, failed counts)
     */
    public Map<String, Integer> processPendingSummaries(int batchSize) {
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        try {
            LoggingService.info("process_pending_summaries_started", Map.of("batchSize", batchSize));

            List<String[]> pendingOrders = consultationService.getOrdersPendingSummaryRetry(batchSize);

            LoggingService.info("found_pending_summaries", Map.of("count", pendingOrders.size()));

            for (String[] orderInfo : pendingOrders) {
                String userId = orderInfo[0];
                String orderId = orderInfo[1];

                try {
                    processed++;
                    SummaryResult result = generateSummary(userId, orderId);

                    switch (result.status) {
                        case "generated":
                            succeeded++;
                            break;
                        case "skipped":
                            skipped++;
                            break;
                        case "already_processing":
                            // Another instance is handling it
                            break;
                        default:
                            failed++;
                            break;
                    }

                } catch (Exception e) {
                    failed++;
                    LoggingService.error("process_pending_summary_error", e, Map.of(
                        "userId", userId,
                        "orderId", orderId
                    ));
                }
            }

        } catch (Exception e) {
            LoggingService.error("process_pending_summaries_fatal_error", e);
        }

        Map<String, Integer> results = new HashMap<>();
        results.put("processed", processed);
        results.put("succeeded", succeeded);
        results.put("failed", failed);
        results.put("skipped", skipped);

        LoggingService.info("process_pending_summaries_completed", Map.of(
            "processed", processed,
            "succeeded", succeeded,
            "failed", failed,
            "skipped", skipped
        ));

        return results;
    }

    /**
     * Process content_blocks and match product recommendations to catalog.
     * Finds remedy_product blocks and adds matched_products array to each.
     *
     * @param summary The summary map from Gemini
     * @return Number of remedy blocks that got product matches
     */
    @SuppressWarnings("unchecked")
    private int processProductRecommendations(Map<String, Object> summary) {
        if (summary == null) return 0;

        Object contentBlocksObj = summary.get("content_blocks");
        if (!(contentBlocksObj instanceof List)) {
            return 0;
        }

        List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) contentBlocksObj;
        int matchedCount = 0;

        for (Map<String, Object> block : contentBlocks) {
            String displayType = (String) block.get("display_type");

            // Only process remedy_product blocks
            if (!"remedy_product".equals(displayType)) {
                continue;
            }

            try {
                // Extract product_attributes from the block
                Object attrsObj = block.get("product_attributes");
                RemedyAttributes attributes;

                if (attrsObj instanceof Map) {
                    Map<String, Object> attrsMap = (Map<String, Object>) attrsObj;
                    // Wrap in the expected structure for fromMap
                    Map<String, Object> wrapperMap = new HashMap<>();
                    wrapperMap.put("remedy_type", "product");
                    wrapperMap.put("specificity", determineSpecificity(attrsMap));
                    wrapperMap.put("product_attributes", attrsMap);
                    wrapperMap.put("expert_quote", block.get("expert_quote"));
                    attributes = RemedyAttributes.fromMap(wrapperMap);
                } else {
                    // No product_attributes, try to match by title/content
                    String title = (String) block.get("title");
                    String content = (String) block.get("content");
                    attributes = inferAttributesFromText(title, content);
                }

                if (attributes == null) {
                    continue;
                }

                // Find matching products
                List<MatchedProduct> matchedProducts = productMatchingService.findMatchingProducts(attributes);

                if (!matchedProducts.isEmpty()) {
                    // Convert matched products to list of maps for JSON serialization
                    List<Map<String, Object>> productMaps = new ArrayList<>();
                    for (MatchedProduct product : matchedProducts) {
                        productMaps.add(product.toMap());
                    }
                    block.put("matched_products", productMaps);
                    matchedCount++;

                    LoggingService.info("remedy_products_matched", Map.of(
                        "blockId", block.get("id") != null ? block.get("id") : "unknown",
                        "productCount", matchedProducts.size()
                    ));
                }

            } catch (Exception e) {
                LoggingService.warn("product_matching_error", Map.of(
                    "blockId", block.get("id") != null ? block.get("id") : "unknown",
                    "error", e.getMessage()
                ));
            }
        }

        return matchedCount;
    }

    /**
     * Determine specificity level based on product attributes.
     */
    @SuppressWarnings("unchecked")
    private String determineSpecificity(Map<String, Object> attrs) {
        if (attrs == null) return "general";

        // Check for exact material match
        Object material = attrs.get("material");
        if (material instanceof List && !((List<?>) material).isEmpty()) {
            return "exact";
        }

        // Check for planetary association
        Object planets = attrs.get("planets");
        if (planets instanceof List && !((List<?>) planets).isEmpty()) {
            return "planetary";
        }

        // Check for purpose/category
        Object purpose = attrs.get("purpose");
        if (purpose instanceof List && !((List<?>) purpose).isEmpty()) {
            return "attribute";
        }

        // Check for product type
        if (attrs.get("product_type") != null) {
            return "category";
        }

        return "general";
    }

    /**
     * Infer product attributes from text when Gemini didn't extract structured attributes.
     */
    private RemedyAttributes inferAttributesFromText(String title, String content) {
        if (title == null && content == null) return null;

        String text = ((title != null ? title : "") + " " + (content != null ? content : "")).toLowerCase();

        RemedyAttributes.Builder builder = RemedyAttributes.builder()
            .remedyType("product")
            .specificity("general");

        // Try to infer material from common crystal/gemstone names
        List<String> materials = new ArrayList<>();
        if (text.contains("rose quartz")) materials.add("rose_quartz");
        if (text.contains("blue sapphire") || text.contains("neelam")) materials.add("blue_sapphire");
        if (text.contains("amethyst")) materials.add("amethyst");
        if (text.contains("citrine")) materials.add("citrine");
        if (text.contains("tiger eye") || text.contains("tiger's eye")) materials.add("tiger_eye");
        if (text.contains("clear quartz") || text.contains("crystal quartz")) materials.add("clear_quartz");
        if (text.contains("black tourmaline")) materials.add("black_tourmaline");
        if (text.contains("rudraksha")) materials.add("rudraksha");
        if (text.contains("emerald") || text.contains("panna")) materials.add("emerald");
        if (text.contains("pearl") || text.contains("moti")) materials.add("pearl");
        if (text.contains("coral") || text.contains("moonga")) materials.add("coral");
        if (text.contains("yellow sapphire") || text.contains("pukhraj")) materials.add("yellow_sapphire");

        if (!materials.isEmpty()) {
            builder.material(materials);
            builder.specificity("exact");
        }

        // Try to infer product type
        if (text.contains("bracelet")) builder.productType("bracelet");
        else if (text.contains("pendant")) builder.productType("pendant");
        else if (text.contains("ring")) builder.productType("ring");
        else if (text.contains("mala")) builder.productType("mala");

        // Try to infer planets
        List<String> planets = new ArrayList<>();
        if (text.contains("saturn") || text.contains("shani")) planets.add("saturn");
        if (text.contains("venus") || text.contains("shukra")) planets.add("venus");
        if (text.contains("jupiter") || text.contains("guru") || text.contains("brihaspati")) planets.add("jupiter");
        if (text.contains("mars") || text.contains("mangal")) planets.add("mars");
        if (text.contains("mercury") || text.contains("budh")) planets.add("mercury");
        if (text.contains("sun") || text.contains("surya")) planets.add("sun");
        if (text.contains("moon") || text.contains("chandra")) planets.add("moon");
        if (text.contains("rahu")) planets.add("rahu");
        if (text.contains("ketu")) planets.add("ketu");

        if (!planets.isEmpty()) {
            builder.planets(planets);
            if (materials.isEmpty()) builder.specificity("planetary");
        }

        // Try to infer purpose
        List<String> purposes = new ArrayList<>();
        if (text.contains("love") || text.contains("relationship") || text.contains("marriage")) purposes.add("love");
        if (text.contains("career") || text.contains("job") || text.contains("business")) purposes.add("career");
        if (text.contains("protection") || text.contains("evil eye") || text.contains("negative")) purposes.add("protection");
        if (text.contains("health") || text.contains("healing")) purposes.add("health");
        if (text.contains("wealth") || text.contains("money") || text.contains("prosperity")) purposes.add("wealth");
        if (text.contains("peace") || text.contains("calm") || text.contains("stress")) purposes.add("peace");

        if (!purposes.isEmpty()) {
            builder.purpose(purposes);
            if (materials.isEmpty() && planets.isEmpty()) builder.specificity("attribute");
        }

        return builder.build();
    }
}
