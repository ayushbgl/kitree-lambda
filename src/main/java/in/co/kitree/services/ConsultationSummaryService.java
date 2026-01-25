package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import in.co.kitree.pojos.OnDemandConsultationOrder;

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
 * Uses Firestore-based state management for retry handling:
 * - summaryStatus: PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
 * - summaryRetryCount: Number of retry attempts
 * - summaryLastAttempt: Timestamp of last attempt
 * - summaryError: Last error message
 *
 * Summary schema includes:
 * - headline, brief_summary, primary_concern, sentiment
 * - important_dates: array of {date, significance, is_auspicious}
 * - topics: array of {id, category, title, summary, expert_advice}
 * - predictions: array of {id, category, prediction_text, timeframe, likelihood, astrological_factors}
 * - remedies: array of {id, type, title, description, purpose, priority, timing, frequency, ...type-specific fields}
 * - insights: array of {id, category, text, planetary_influence}
 * - follow_up: {recommended, timeframe, reason, same_expert_recommended}
 */
public class ConsultationSummaryService {

    private final Firestore db;
    private final OnDemandConsultationService consultationService;
    private final StreamService streamService;
    private final GeminiService geminiService;

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

            // 12. Store summary and mark as completed
            consultationService.updateSummaryStatus(userId, orderId,
                OnDemandConsultationService.SUMMARY_STATUS_COMPLETED, summary, null, false);

            LoggingService.info("summary_generated_successfully", Map.of(
                "hasTopics", summary.containsKey("topics"),
                "hasPredictions", summary.containsKey("predictions"),
                "hasRemedies", summary.containsKey("remedies")
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
}
