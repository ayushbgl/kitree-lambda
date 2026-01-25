package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import in.co.kitree.pojos.OnDemandConsultationOrder;
import in.co.kitree.pojos.WalletTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Stateless billing service that computes charges from Stream API data.
 * Replaces webhook-driven interval accumulation with fetch-based calculation.
 *
 * Key features:
 * - Fetches authoritative call participation data from Stream Video API
 * - Computes overlap duration (time when both user and expert are in call)
 * - Applies charges idempotently in Firestore transactions
 * - Supports execution from webhooks and cron retries
 */
public class BillingService {

    private final Firestore db;
    private final StreamService streamService;
    private final WalletService walletService;
    private final ExpertEarningsService earningsService;
    private final OnDemandConsultationService consultationService;

    /**
     * Result object for billing calculations.
     */
    public static class BillingResult {
        public final boolean success;
        public final String status; // "completed", "skipped_already_completed", "error", "zero_charge"
        public final Long billableSeconds;
        public final Double cost;
        public final Double platformFee;
        public final Double expertEarnings;
        public final String errorMessage;

        private BillingResult(boolean success, String status, Long billableSeconds,
                              Double cost, Double platformFee, Double expertEarnings, String errorMessage) {
            this.success = success;
            this.status = status;
            this.billableSeconds = billableSeconds;
            this.cost = cost;
            this.platformFee = platformFee;
            this.expertEarnings = expertEarnings;
            this.errorMessage = errorMessage;
        }

        public static BillingResult completed(Long billableSeconds, Double cost,
                                              Double platformFee, Double expertEarnings) {
            return new BillingResult(true, "completed", billableSeconds, cost,
                platformFee, expertEarnings, null);
        }

        public static BillingResult zeroCharge() {
            return new BillingResult(true, "zero_charge", 0L, 0.0, 0.0, 0.0, null);
        }

        public static BillingResult skipped(String reason) {
            return new BillingResult(true, "skipped_" + reason, null, null, null, null, null);
        }

        public static BillingResult error(String message) {
            return new BillingResult(false, "error", null, null, null, null, message);
        }

        @Override
        public String toString() {
            return "BillingResult{success=" + success + ", status='" + status + "', billableSeconds=" +
                billableSeconds + ", cost=" + cost + ", error='" + errorMessage + "'}";
        }
    }

    /**
     * Create a new BillingService instance.
     *
     * @param db Firestore instance
     * @param isTest true for test environment
     */
    public BillingService(Firestore db, boolean isTest) {
        this.db = db;
        this.streamService = new StreamService(isTest);
        this.walletService = new WalletService(db);
        this.earningsService = new ExpertEarningsService(db);
        this.consultationService = new OnDemandConsultationService(db);
    }

    /**
     * Create a BillingService with injected dependencies (for testing).
     */
    public BillingService(Firestore db, StreamService streamService, WalletService walletService,
                          ExpertEarningsService earningsService, OnDemandConsultationService consultationService) {
        this.db = db;
        this.streamService = streamService;
        this.walletService = walletService;
        this.earningsService = earningsService;
        this.consultationService = consultationService;
    }

    /**
     * Recalculate and apply charge for a consultation using call CID.
     *
     * @param callCid The Stream call CID (format: {type}:{id})
     * @return BillingResult with calculation details
     */
    public BillingResult recalculateCharge(String callCid) {
        String[] parts = StreamService.parseCallCid(callCid);
        if (parts == null) {
            return BillingResult.error("Invalid call CID format: " + callCid);
        }
        return recalculateCharge(parts[0], parts[1]);
    }

    /**
     * Recalculate and apply charge for a consultation.
     * This is the main entry point - called by webhooks and cron.
     *
     * Algorithm:
     * 1. Find order by call ID
     * 2. Idempotency check: skip if COMPLETED
     * 3. Backward compatibility: use stored intervals if present
     * 4. Otherwise, fetch sessions from Stream API
     * 5. Build presence intervals for user and expert
     * 6. Calculate overlap using existing algorithm
     * 7. Compute cost using existing pricing rules
     * 8. Apply charge in Firestore transaction (idempotent)
     *
     * @param callType Stream call type (e.g., "consultation_video")
     * @param callId Stream call ID (same as orderId)
     * @return BillingResult with calculation details
     */
    public BillingResult recalculateCharge(String callType, String callId) {
        try {
            LoggingService.info("recalculate_charge_start", Map.of(
                "callType", callType,
                "callId", callId
            ));

            // Step 1: Find the order by call CID
            String callCid = callType + ":" + callId;
            Map<String, Object> orderData = consultationService.getOrderByStreamCallCid(callCid);

            if (orderData == null) {
                LoggingService.warn("recalculate_order_not_found", Map.of("callCid", callCid));
                return BillingResult.error("Order not found for call: " + callCid);
            }

            String orderId = (String) orderData.get("order_id");
            String userId = (String) orderData.get("user_id");
            String expertId = (String) orderData.get("expert_id");
            String currentStatus = (String) orderData.get("status");

            LoggingService.setContext(userId, orderId, expertId);

            // Step 2: Idempotency check - skip if already completed
            if ("COMPLETED".equals(currentStatus)) {
                LoggingService.info("recalculate_skipped_already_completed");
                return BillingResult.skipped("already_completed");
            }

            if (!"CONNECTED".equals(currentStatus)) {
                LoggingService.info("recalculate_skipped_not_connected", Map.of("status", currentStatus));
                return BillingResult.error("Order not in CONNECTED status: " + currentStatus);
            }

            // Step 3: Get full order details
            OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
            if (order == null) {
                return BillingResult.error("Failed to get order details");
            }

            // Step 4: Calculate billable seconds
            Long billableSeconds;

            // Backward compatibility: if old order has stored intervals, use them
            List<Map<String, Object>> userIntervalMaps = order.getUserIntervals();
            List<Map<String, Object>> expertIntervalMaps = order.getExpertIntervals();

            if (userIntervalMaps != null && !userIntervalMaps.isEmpty() &&
                expertIntervalMaps != null && !expertIntervalMaps.isEmpty()) {
                // Use stored intervals (old orders created before this refactor)
                LoggingService.info("using_stored_intervals");
                List<OnDemandConsultationService.ParticipantInterval> userIntervals =
                    consultationService.parseIntervalsFromList(userIntervalMaps);
                List<OnDemandConsultationService.ParticipantInterval> expertIntervals =
                    consultationService.parseIntervalsFromList(expertIntervalMaps);

                billableSeconds = consultationService.calculateOverlapFromIntervals(
                    userIntervals, expertIntervals, order.getMaxAllowedDuration());
            } else {
                // New path: fetch from Stream API
                LoggingService.info("fetching_from_stream_api");
                billableSeconds = calculateFromStreamApi(callType, callId, userId, expertId, order);
            }

            // Step 5: Calculate cost using existing pricing rules
            Double ratePerMinute = order.getExpertRatePerMinute();
            Double cost = consultationService.calculateCost(billableSeconds, ratePerMinute);
            Double platformFeeAmount = consultationService.calculatePlatformFee(
                cost, order.getPlatformFeePercent());
            Double expertEarnings = cost - platformFeeAmount;

            LoggingService.info("billing_calculation", Map.of(
                "billableSeconds", billableSeconds,
                "cost", cost,
                "platformFee", platformFeeAmount,
                "expertEarnings", expertEarnings
            ));

            // Step 6: Handle zero charge case
            if (billableSeconds == 0L || cost == 0.0) {
                return applyZeroCharge(userId, orderId, expertId, order);
            }

            // Step 7: Apply charge in transaction (idempotent)
            return applyCharge(userId, orderId, expertId, order,
                billableSeconds, cost, platformFeeAmount, expertEarnings);

        } catch (Exception e) {
            LoggingService.error("recalculate_charge_error", e, Map.of("callId", callId));
            return BillingResult.error(e.getMessage());
        }
    }

    /**
     * Calculate billable seconds by fetching session data from Stream API.
     */
    private Long calculateFromStreamApi(String callType, String callId, String userId,
                                        String expertId, OnDemandConsultationOrder order) {
        // Fetch call details from Stream
        StreamService.StreamCallResponse callResponse = streamService.getCallDetails(callType, callId);

        if (callResponse.hasError()) {
            LoggingService.warn("stream_api_error", Map.of("error", callResponse.getErrorMessage()));
            // If Stream API fails, fall back to simple calculation if we have join timestamps
            if (order.getBothParticipantsJoinedAt() != null) {
                LoggingService.info("falling_back_to_simple_billing");
                return consultationService.calculateBillableSeconds(order);
            }
            return 0L;
        }

        StreamService.StreamSession session = callResponse.getSession();
        if (session == null || session.getParticipants() == null || session.getParticipants().isEmpty()) {
            LoggingService.info("no_session_data_zero_charge");
            return 0L;
        }

        // Build intervals from Stream participant data
        List<OnDemandConsultationService.ParticipantInterval> userIntervals =
            buildIntervalsFromStreamParticipants(session.getParticipants(), userId);
        List<OnDemandConsultationService.ParticipantInterval> expertIntervals =
            buildIntervalsFromStreamParticipants(session.getParticipants(), expertId);

        LoggingService.info("stream_intervals_built", Map.of(
            "userIntervalCount", userIntervals.size(),
            "expertIntervalCount", expertIntervals.size()
        ));

        // Calculate overlap
        return consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, order.getMaxAllowedDuration());
    }

    /**
     * Build ParticipantInterval list from Stream participant data.
     * A participant may have multiple join/leave events (reconnections).
     */
    private List<OnDemandConsultationService.ParticipantInterval> buildIntervalsFromStreamParticipants(
            List<StreamService.StreamParticipant> participants, String targetUserId) {

        List<OnDemandConsultationService.ParticipantInterval> intervals = new ArrayList<>();

        for (StreamService.StreamParticipant participant : participants) {
            if (participant.getUserId() != null && participant.getUserId().equals(targetUserId)) {
                OnDemandConsultationService.ParticipantInterval interval =
                    new OnDemandConsultationService.ParticipantInterval();

                // Convert Instant to Timestamp
                if (participant.getJoinedAt() != null) {
                    interval.setJoinedAt(Timestamp.ofTimeSecondsAndNanos(
                        participant.getJoinedAt().getEpochSecond(),
                        participant.getJoinedAt().getNano()));
                }

                if (participant.getLeftAt() != null) {
                    interval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
                        participant.getLeftAt().getEpochSecond(),
                        participant.getLeftAt().getNano()));
                }
                // If leftAt is null, the participant is still in the call - use current time
                // This handles provisional billing for ongoing calls

                intervals.add(interval);
            }
        }

        return intervals;
    }

    /**
     * Apply zero charge - order completed but no billing.
     * Used when expert never joined or there was no overlap.
     */
    private BillingResult applyZeroCharge(String userId, String orderId, String expertId,
                                          OnDemandConsultationOrder order) {
        try {
            LoggingService.info("applying_zero_charge", Map.of("orderId", orderId));

            db.runTransaction(transaction -> {
                // Read order and verify status
                DocumentReference orderRef = db.collection("users").document(userId)
                        .collection("orders").document(orderId);
                DocumentSnapshot orderDoc = transaction.get(orderRef).get();

                if (!orderDoc.exists() || !"CONNECTED".equals(orderDoc.getString("status"))) {
                    throw new IllegalStateException("Order no longer CONNECTED");
                }

                // Read expert store for status update
                DocumentReference expertStoreRef = db.collection("users").document(expertId)
                        .collection("public").document("store");
                DocumentSnapshot expertStoreDoc = transaction.get(expertStoreRef).get();

                // Check for other active consultations
                boolean hasOtherActive = consultationService.hasOtherConnectedConsultationsInTransaction(
                    transaction, expertId, orderId);

                // Update order to COMPLETED with zero cost
                Map<String, Object> orderUpdates = new HashMap<>();
                orderUpdates.put("status", "COMPLETED");
                orderUpdates.put("end_time", Timestamp.now());
                orderUpdates.put("duration_seconds", 0L);
                orderUpdates.put("cost", 0.0);
                orderUpdates.put("platform_fee_amount", 0.0);
                orderUpdates.put("expert_earnings", 0.0);
                transaction.update(orderRef, orderUpdates);

                // Free expert if no other active consultations
                if (!hasOtherActive && expertStoreDoc.exists()) {
                    Map<String, Object> statusUpdates = new HashMap<>();
                    statusUpdates.put("consultation_status", "FREE");
                    statusUpdates.put("consultation_status_updated_at", Timestamp.now());
                    transaction.update(expertStoreRef, statusUpdates);
                }

                return null;
            }).get();

            LoggingService.info("zero_charge_applied_successfully");
            return BillingResult.zeroCharge();

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("no longer CONNECTED")) {
                return BillingResult.skipped("already_completed");
            }
            LoggingService.error("zero_charge_error", e);
            return BillingResult.error(e.getMessage());
        }
    }

    /**
     * Apply calculated charge in a Firestore transaction.
     * Reuses the transaction pattern from finalizeOnDemandConsultation().
     */
    private BillingResult applyCharge(String userId, String orderId, String expertId,
                                      OnDemandConsultationOrder order, Long billableSeconds,
                                      Double cost, Double platformFeeAmount, Double expertEarnings) {
        try {
            String currency = order.getCurrency();

            final Long finalBillableSeconds = billableSeconds;
            final Double finalCost = cost;
            final Double finalPlatformFeeAmount = platformFeeAmount;
            final Double finalExpertEarnings = expertEarnings;
            final String finalCurrency = currency;

            db.runTransaction(transaction -> {
                // ===== PHASE 1: ALL READS FIRST (Firestore requirement) =====

                // Read user's expert-specific wallet
                DocumentReference userExpertWalletRef = db.collection("users").document(userId)
                        .collection("expert_wallets").document(expertId);
                DocumentSnapshot walletDoc = transaction.get(userExpertWalletRef).get();

                // Read expert user document (for earnings balance)
                DocumentReference expertUserRef = db.collection("users").document(expertId);
                DocumentSnapshot expertUserDoc = transaction.get(expertUserRef).get();

                // Read order document
                DocumentReference orderRef = db.collection("users").document(userId)
                        .collection("orders").document(orderId);
                DocumentSnapshot orderDoc = transaction.get(orderRef).get();

                // Read expert status document
                DocumentReference expertStoreRef = db.collection("users").document(expertId)
                        .collection("public").document("store");
                DocumentSnapshot expertStoreDoc = transaction.get(expertStoreRef).get();

                // Check for other active consultations
                boolean hasOtherActive = consultationService.hasOtherConnectedConsultationsInTransaction(
                    transaction, expertId, orderId);

                // ===== PHASE 2: VALIDATIONS =====

                // Verify order is still CONNECTED (prevent double-charging)
                if (!orderDoc.exists() || !"CONNECTED".equals(orderDoc.getString("status"))) {
                    throw new IllegalStateException("Order no longer CONNECTED: " + orderId);
                }

                // ===== PHASE 3: ALL WRITES =====

                // Deduct from user's expert-specific wallet
                walletService.updateExpertWalletBalanceInTransactionWithSnapshot(
                    transaction, userExpertWalletRef, walletDoc, finalCurrency, -finalCost);

                // Credit expert's earnings passbook
                earningsService.creditExpertEarningsInTransactionWithSnapshot(
                    transaction, expertId, expertUserRef, expertUserDoc,
                    finalCurrency, finalCost, finalPlatformFeeAmount,
                    orderId, "On-demand consultation earning");

                // Create deduction transaction
                WalletTransaction deductionTransaction = new WalletTransaction();
                deductionTransaction.setType("CONSULTATION_DEDUCTION");
                deductionTransaction.setSource("PAYMENT");
                deductionTransaction.setAmount(-finalCost);
                deductionTransaction.setCurrency(finalCurrency);
                deductionTransaction.setOrderId(orderId);
                deductionTransaction.setStatus("COMPLETED");
                deductionTransaction.setCreatedAt(Timestamp.now());
                deductionTransaction.setDurationSeconds(finalBillableSeconds);
                deductionTransaction.setRatePerMinute(order.getExpertRatePerMinute());
                deductionTransaction.setConsultationType(order.getConsultationType());
                deductionTransaction.setCategory(order.getCategory());
                walletService.createExpertWalletTransactionInTransaction(
                    transaction, userId, expertId, deductionTransaction);

                // Update order status
                Map<String, Object> orderUpdates = new HashMap<>();
                orderUpdates.put("status", "COMPLETED");
                orderUpdates.put("end_time", Timestamp.now());
                orderUpdates.put("duration_seconds", finalBillableSeconds);
                orderUpdates.put("cost", finalCost);
                orderUpdates.put("platform_fee_amount", finalPlatformFeeAmount);
                orderUpdates.put("expert_earnings", finalExpertEarnings);
                transaction.update(orderRef, orderUpdates);

                // Set consultation status back to FREE if no other active consultations
                if (!hasOtherActive && expertStoreDoc.exists()) {
                    Map<String, Object> statusUpdates = new HashMap<>();
                    statusUpdates.put("consultation_status", "FREE");
                    statusUpdates.put("consultation_status_updated_at", Timestamp.now());
                    transaction.update(expertStoreRef, statusUpdates);
                }

                return null;
            }).get();

            LoggingService.info("charge_applied_successfully", Map.of(
                "orderId", orderId,
                "billableSeconds", billableSeconds,
                "cost", cost
            ));

            return BillingResult.completed(billableSeconds, cost, platformFeeAmount, expertEarnings);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("no longer CONNECTED")) {
                return BillingResult.skipped("already_completed");
            }
            LoggingService.error("apply_charge_error", e, Map.of("orderId", orderId));
            return BillingResult.error(e.getMessage());
        }
    }
}
