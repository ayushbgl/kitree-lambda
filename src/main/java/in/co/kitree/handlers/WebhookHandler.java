package in.co.kitree.handlers;

import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.OnDemandConsultationOrder;
import in.co.kitree.pojos.RequestEvent;
import in.co.kitree.pojos.WalletTransaction;
import in.co.kitree.services.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Handler for webhook requests from 3rd party services (Stream, Razorpay, etc.).
 * Handles video call webhooks including participant joined/left events and call ended events.
 */
public class WebhookHandler {

    private final Firestore db;
    private final boolean isTest;
    private final Gson gson;

    public WebhookHandler(Firestore db, boolean isTest) {
        this.db = db;
        this.isTest = isTest;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Check if this handler handles the given raw path.
     */
    public static boolean handlesPath(String rawPath) {
        return rawPath != null && rawPath.startsWith("/webhooks/");
    }

    /**
     * Handle webhook requests from 3rd party services (Stream, Razorpay, etc.)
     * Routes based on the URL path: /webhooks/stream, /webhooks/razorpay, etc.
     */
    public String handleWebhookRequest(RequestEvent event, String rawPath) {
        LoggingService.info("processing_webhook_request", Map.of("path", rawPath));

        switch (rawPath) {
            case "/webhooks/stream":
                return handleStreamWebhook(event);
            case "/webhooks/razorpay":
                // Future: migrate razorpay_webhook to path-based routing
                return gson.toJson(Map.of("error", "Razorpay webhook not yet migrated to path-based routing"));
            default:
                LoggingService.warn("unknown_webhook_endpoint", Map.of("path", rawPath));
                return gson.toJson(Map.of("error", "Unknown webhook endpoint", "path", rawPath));
        }
    }

    /**
     * Handle Stream video webhooks for call events.
     * Events: call.ended, call.session_participant_left, etc.
     */
    private String handleStreamWebhook(RequestEvent event) {
        LoggingService.setFunction("stream_webhook");
        LoggingService.info("processing_stream_webhook");

        try {
            // Extract webhook metadata from headers
            // Stream sends: X-SIGNATURE, X-API-KEY, X-WEBHOOK-ID, X-WEBHOOK-ATTEMPT
            String body = event.getBody();
            Map<String, String> headers = event.getHeaders();

            String webhookId = getHeader(headers, "X-WEBHOOK-ID", "x-webhook-id");
            String webhookAttempt = getHeader(headers, "X-WEBHOOK-ATTEMPT", "x-webhook-attempt");
            String signature = getHeader(headers, "X-SIGNATURE", "x-signature");
            String apiKeyHeader = getHeader(headers, "X-API-KEY", "x-api-key");

            if (webhookId != null) {
                LoggingService.setCorrelationId(webhookId);
                LoggingService.info("stream_webhook_metadata", Map.of(
                    "webhookId", webhookId,
                    "attempt", webhookAttempt != null ? webhookAttempt : "unknown"
                ));
            }

            // Verify webhook signature for security
            // Stream uses HMAC-SHA256 with API secret to sign webhook payloads
            StreamService streamService = new StreamService(isTest);
            if (streamService.isConfigured()) {
                // First, verify API key matches (if provided)
                if (apiKeyHeader != null && !apiKeyHeader.isEmpty()) {
                    if (!apiKeyHeader.equals(streamService.getApiKey())) {
                        LoggingService.warn("stream_webhook_api_key_mismatch");
                        if (!isTest) {
                            return gson.toJson(Map.of("error", "Invalid API key"));
                        }
                    }
                }

                // Verify signature
                if (signature != null && !signature.isEmpty()) {
                    boolean isValid = streamService.verifyWebhookSignature(body, signature);
                    if (!isValid) {
                        LoggingService.warn("stream_webhook_signature_verification_failed");
                        // Log but don't reject in test mode (signatures may not be configured)
                        if (!isTest) {
                            return gson.toJson(Map.of("error", "Invalid webhook signature"));
                        }
                        LoggingService.info("continuing_despite_invalid_signature_test_mode");
                    } else {
                        LoggingService.debug("stream_webhook_signature_verified");
                    }
                } else {
                    LoggingService.debug("no_webhook_signature_skipping_verification");
                }
            } else {
                LoggingService.debug("stream_service_not_configured_skipping_verification");
            }

            // Parse the webhook payload
            if (body == null || body.isEmpty()) {
                LoggingService.warn("stream_webhook_empty_body");
                return gson.toJson(Map.of("error", "Empty webhook body"));
            }

            LoggingService.debug("stream_webhook_body_received", Map.of("bodyLength", body.length()));

            // Parse webhook event
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = gson.fromJson(body, Map.class);
            String eventType = (String) payload.get("type");

            if (eventType == null) {
                LoggingService.warn("stream_webhook_missing_event_type");
                return gson.toJson(Map.of("error", "Missing event type"));
            }

            LoggingService.info("stream_webhook_event_received", Map.of("eventType", eventType));

            // Extract call CID from the payload
            // Stream webhook format varies by event type
            String callCid = extractCallCidFromPayload(payload);

            if (callCid == null) {
                LoggingService.info("stream_webhook_no_call_cid", Map.of("eventType", eventType));
                return gson.toJson(Map.of("status", "ignored", "reason", "no_call_cid"));
            }

            LoggingService.info("stream_webhook_call_cid_extracted", Map.of(
                "callCid", callCid,
                "eventType", eventType
            ));

            // Handle different event types
            switch (eventType) {
                case "call.ended":
                    return handleStreamCallEnded(callCid, payload);
                case "call.session_ended":
                    return handleStreamCallEnded(callCid, payload);
                case "call.session_participant_joined":
                    return handleStreamParticipantJoined(callCid, payload);
                case "call.session_participant_left":
                    return handleStreamParticipantLeft(callCid, payload);
                default:
                    LoggingService.info("stream_webhook_event_ignored", Map.of("eventType", eventType));
                    return gson.toJson(Map.of("status", "ignored", "event_type", eventType));
            }

        } catch (Exception e) {
            LoggingService.error("stream_webhook_processing_error", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return gson.toJson(Map.of("error", "Internal error processing webhook", "message", errorMessage));
        }
    }

    /**
     * Helper to get a header value, trying multiple case variations.
     * HTTP headers are case-insensitive, but Java Maps are case-sensitive.
     */
    private String getHeader(Map<String, String> headers, String... names) {
        if (headers == null) return null;
        for (String name : names) {
            String value = headers.get(name);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Extract call CID from Stream webhook payload.
     * The structure varies by event type.
     */
    @SuppressWarnings("unchecked")
    private String extractCallCidFromPayload(Map<String, Object> payload) {
        try {
            // Try call.cid at root level
            if (payload.containsKey("call_cid")) {
                return (String) payload.get("call_cid");
            }

            // Try nested in call object
            Map<String, Object> call = (Map<String, Object>) payload.get("call");
            if (call != null && call.containsKey("cid")) {
                return (String) call.get("cid");
            }

            // Try call.session structure
            Map<String, Object> callSession = (Map<String, Object>) payload.get("call_session");
            if (callSession != null) {
                call = (Map<String, Object>) callSession.get("call");
                if (call != null && call.containsKey("cid")) {
                    return (String) call.get("cid");
                }
            }

            return null;
        } catch (Exception e) {
            LoggingService.warn("error_extracting_call_cid", Map.of("error", e.getMessage()));
            return null;
        }
    }

    /**
     * Finalize an on-demand consultation order.
     * Calculates cost, deducts from user's wallet, credits expert earnings, and updates order status.
     * Returns true if successful, false otherwise.
     */
    private boolean finalizeOnDemandConsultation(String userId, String orderId, String expertId) {
        try {
            OnDemandConsultationService consultationService = new OnDemandConsultationService(db);
            WalletService walletService = new WalletService(db);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);

            // Get the order
            OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
            if (order == null) {
                LoggingService.warn("finalize_order_not_found", Map.of("orderId", orderId));
                return false;
            }

            // Only finalize if still CONNECTED
            if (!"CONNECTED".equals(order.getStatus())) {
                LoggingService.info("finalize_skipped_not_connected", Map.of(
                    "orderId", orderId,
                    "currentStatus", order.getStatus()
                ));
                return false;
            }

            // Calculate duration and cost
            // Use billable seconds (time when both participants were present) for billing
            Long totalDurationSeconds = consultationService.calculateElapsedSeconds(order);
            Long billableSeconds;

            // Prefer interval-based calculation if intervals exist (handles reconnections)
            java.util.List<Map<String, Object>> userIntervalMaps = order.getUserIntervals();
            java.util.List<Map<String, Object>> expertIntervalMaps = order.getExpertIntervals();

            if (userIntervalMaps != null && !userIntervalMaps.isEmpty() &&
                expertIntervalMaps != null && !expertIntervalMaps.isEmpty()) {
                // Use interval-based overlap calculation
                java.util.List<OnDemandConsultationService.ParticipantInterval> userIntervals =
                    consultationService.parseIntervalsFromList(userIntervalMaps);
                java.util.List<OnDemandConsultationService.ParticipantInterval> expertIntervals =
                    consultationService.parseIntervalsFromList(expertIntervalMaps);

                billableSeconds = consultationService.calculateOverlapFromIntervals(
                    userIntervals, expertIntervals, order.getMaxAllowedDuration());

                LoggingService.info("using_interval_based_billing", Map.of(
                    "userIntervalCount", userIntervals.size(),
                    "expertIntervalCount", expertIntervals.size()
                ));
            } else {
                // Fall back to simple calculation using both_participants_joined_at
                billableSeconds = consultationService.calculateBillableSeconds(order);
                LoggingService.info("using_simple_billing");
            }

            Double cost = consultationService.calculateCost(billableSeconds, order.getExpertRatePerMinute());
            Double platformFeeAmount = consultationService.calculatePlatformFee(cost, order.getPlatformFeePercent());
            Double expertEarnings = cost - platformFeeAmount;
            String currency = order.getCurrency();

            LoggingService.info("billing_calculation", Map.of(
                "totalDurationSeconds", totalDurationSeconds,
                "billableSeconds", billableSeconds,
                "cost", cost
            ));

            final Long finalDurationSeconds = billableSeconds; // Use billable seconds for the stored duration
            final Double finalCost = cost;
            final Double finalPlatformFeeAmount = platformFeeAmount;
            final Double finalExpertEarnings = expertEarnings;
            final String finalOrderId = orderId;
            final String finalExpertId = expertId;
            final String finalUserId = userId;
            final String finalCurrency = currency;

            // Execute in transaction
            this.db.runTransaction(transaction -> {
                // ===== PHASE 1: ALL READS FIRST (Firestore requirement) =====

                // Read user's expert-specific wallet
                DocumentReference userExpertWalletRef = db.collection("users").document(finalUserId)
                        .collection("expert_wallets").document(finalExpertId);
                DocumentSnapshot walletDoc = transaction.get(userExpertWalletRef).get();

                // Read expert user document (for earnings balance)
                DocumentReference expertUserRef = db.collection("users").document(finalExpertId);
                DocumentSnapshot expertUserDoc = transaction.get(expertUserRef).get();

                // Read order document
                DocumentReference orderRef = db.collection("users").document(finalUserId)
                        .collection("orders").document(finalOrderId);
                DocumentSnapshot orderDoc = transaction.get(orderRef).get();

                // Read expert status document
                DocumentReference expertStoreRef = db.collection("users").document(finalExpertId)
                        .collection("public").document("store");
                DocumentSnapshot expertStoreDoc = transaction.get(expertStoreRef).get();

                // Check for other active consultations (this does reads internally)
                boolean hasOtherActive = consultationService.hasOtherConnectedConsultationsInTransaction(
                    transaction, finalExpertId, finalOrderId
                );

                // ===== PHASE 2: VALIDATIONS =====

                // Verify order is still CONNECTED (prevent double-charging)
                if (!orderDoc.exists() || !"CONNECTED".equals(orderDoc.getString("status"))) {
                    throw new IllegalStateException("Order is not in CONNECTED status: " + finalOrderId);
                }

                // ===== PHASE 3: ALL WRITES =====

                // Deduct from user's expert-specific wallet (pass pre-read snapshot)
                walletService.updateExpertWalletBalanceInTransactionWithSnapshot(
                    transaction, userExpertWalletRef, walletDoc, finalCurrency, -finalCost
                );

                // Credit expert's earnings passbook (pass pre-read snapshot)
                earningsService.creditExpertEarningsInTransactionWithSnapshot(
                    transaction, finalExpertId, expertUserRef, expertUserDoc,
                    finalCurrency, finalCost, finalPlatformFeeAmount,
                    finalOrderId, "On-demand consultation earning"
                );

                // Create deduction transaction
                // Store normalized fields only - no description (frontend constructs from these fields)
                // expertId/expertName NOT stored - redundant since path is expert_wallets/{expertId}
                WalletTransaction deductionTransaction = new WalletTransaction();
                deductionTransaction.setType("CONSULTATION_DEDUCTION");
                deductionTransaction.setSource("PAYMENT");
                deductionTransaction.setAmount(-finalCost);
                deductionTransaction.setCurrency(finalCurrency);
                deductionTransaction.setOrderId(finalOrderId);
                deductionTransaction.setStatus("COMPLETED");
                deductionTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
                // Normalized fields for frontend to construct display text
                deductionTransaction.setDurationSeconds(finalDurationSeconds);
                deductionTransaction.setRatePerMinute(order.getExpertRatePerMinute());
                deductionTransaction.setConsultationType(order.getConsultationType());
                deductionTransaction.setCategory(order.getCategory());
                // description NOT set - frontend handles all display text
                walletService.createExpertWalletTransactionInTransaction(transaction, finalUserId, finalExpertId, deductionTransaction);

                // Update order status
                Map<String, Object> orderUpdates = new HashMap<>();
                orderUpdates.put("status", "COMPLETED");
                orderUpdates.put("end_time", com.google.cloud.Timestamp.now());
                orderUpdates.put("duration_seconds", finalDurationSeconds);
                orderUpdates.put("cost", finalCost);
                orderUpdates.put("platform_fee_amount", finalPlatformFeeAmount);
                orderUpdates.put("expert_earnings", finalExpertEarnings);
                transaction.update(orderRef, orderUpdates);

                // Set consultation status back to FREE if no other active consultations
                if (!hasOtherActive && expertStoreDoc.exists()) {
                    Map<String, Object> statusUpdates = new HashMap<>();
                    statusUpdates.put("consultation_status", "FREE");
                    statusUpdates.put("consultation_status_updated_at", com.google.cloud.Timestamp.now());
                    transaction.update(expertStoreRef, statusUpdates);
                }

                return null;
            }).get();

            LoggingService.info("consultation_finalized_successfully", Map.of(
                "orderId", orderId,
                "durationSeconds", billableSeconds,
                "cost", cost,
                "currency", currency
            ));
            return true;

        } catch (Exception e) {
            LoggingService.error("finalize_consultation_error", e, Map.of("orderId", orderId != null ? orderId : "unknown"));
            return false;
        }
    }

    /**
     * Handle Stream call.ended or call.session_ended event.
     * Acts as an idempotent retry for billing in case participant_left was missed.
     *
     * REFACTORED: Now uses BillingService.recalculateCharge() which fetches authoritative data
     * from Stream API and computes overlap-based billing idempotently.
     */
    private String handleStreamCallEnded(String callCid, Map<String, Object> payload) {
        LoggingService.info("handling_stream_call_ended", Map.of("callCid", callCid));

        try {
            OnDemandConsultationService consultationService = new OnDemandConsultationService(db);

            // Find order by stream call CID
            Map<String, Object> orderData = consultationService.getOrderByStreamCallCid(callCid);

            if (orderData == null) {
                LoggingService.info("call_ended_order_not_found", Map.of("callCid", callCid));
                return gson.toJson(Map.of("status", "order_not_found", "call_cid", callCid));
            }

            String orderId = (String) orderData.get("order_id");
            String expertId = (String) orderData.get("expert_id");
            String userId = (String) orderData.get("user_id");
            String orderType = (String) orderData.get("type");
            String orderStatus = (String) orderData.get("status");

            LoggingService.setContext(userId, orderId, expertId);
            LoggingService.info("call_ended_order_found", Map.of(
                "orderType", orderType != null ? orderType : "unknown",
                "orderStatus", orderStatus != null ? orderStatus : "unknown"
            ));

            // Check if already completed - idempotency
            if ("COMPLETED".equals(orderStatus) || "CANCELLED".equals(orderStatus)) {
                LoggingService.info("call_ended_order_already_completed");
                return gson.toJson(Map.of("status", "already_completed", "order_id", orderId));
            }

            // For on-demand consultations that are still CONNECTED, finalize billing
            // This acts as a safety net if participant_left webhook was missed
            BillingService.BillingResult billingResult = null;
            if ("ON_DEMAND_CONSULTATION".equals(orderType) && "CONNECTED".equals(orderStatus)) {
                LoggingService.info("finalizing_consultation_via_call_ended_webhook");
                BillingService billingService = new BillingService(db, isTest);
                billingResult = billingService.recalculateCharge(callCid);

                LoggingService.info("call_ended_billing_result", Map.of(
                    "success", billingResult.success,
                    "status", billingResult.status
                ));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "processed");
            response.put("order_id", orderId);

            if (billingResult != null) {
                response.put("billing_status", billingResult.status);
                if (billingResult.billableSeconds != null) {
                    response.put("billable_seconds", billingResult.billableSeconds);
                }
                if (billingResult.cost != null) {
                    response.put("cost", billingResult.cost);
                }
            }

            return gson.toJson(response);

        } catch (Exception e) {
            LoggingService.error("stream_call_ended_error", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return gson.toJson(Map.of("error", "Error processing call ended", "message", errorMessage));
        }
    }

    /**
     * Handle Stream call.session_participant_joined event.
     * Tracks when each participant joins - simple tracking only.
     *
     * REFACTORED: No longer stores intervals locally. Simple join timestamps are kept
     * for backward compatibility. Stream API is the source of truth for billing.
     */
    @SuppressWarnings("unchecked")
    private String handleStreamParticipantJoined(String callCid, Map<String, Object> payload) {
        LoggingService.info("handling_stream_participant_joined", Map.of("callCid", callCid));

        try {
            OnDemandConsultationService consultationService = new OnDemandConsultationService(db);

            // Find order by stream call CID
            Map<String, Object> orderData = consultationService.getOrderByStreamCallCid(callCid);

            if (orderData == null) {
                LoggingService.info("participant_joined_order_not_found", Map.of("callCid", callCid));
                return gson.toJson(Map.of("status", "order_not_found", "call_cid", callCid));
            }

            String orderId = (String) orderData.get("order_id");
            String expertId = (String) orderData.get("expert_id");
            String userId = (String) orderData.get("user_id");
            String orderType = (String) orderData.get("type");
            String orderStatus = (String) orderData.get("status");

            LoggingService.setContext(userId, orderId, expertId);

            // Only process for on-demand consultations that are active
            if (!"ON_DEMAND_CONSULTATION".equals(orderType)) {
                LoggingService.info("participant_joined_not_on_demand");
                return gson.toJson(Map.of("status", "ignored", "reason", "not_on_demand"));
            }

            if (!"INITIATED".equals(orderStatus) && !"CONNECTED".equals(orderStatus)) {
                LoggingService.info("participant_joined_order_not_active", Map.of("status", orderStatus != null ? orderStatus : "null"));
                return gson.toJson(Map.of("status", "ignored", "reason", "order_not_active"));
            }

            // Extract participant info from payload
            Map<String, Object> participant = (Map<String, Object>) payload.get("participant");
            if (participant == null) {
                LoggingService.warn("participant_joined_no_participant_data");
                return gson.toJson(Map.of("status", "ignored", "reason", "no_participant_data"));
            }

            Map<String, Object> participantUser = (Map<String, Object>) participant.get("user");
            String participantUserId = participantUser != null ? (String) participantUser.get("id") : null;

            if (participantUserId == null) {
                LoggingService.warn("participant_joined_no_user_id");
                return gson.toJson(Map.of("status", "ignored", "reason", "no_user_id"));
            }

            // Determine if this is the user or expert joining
            boolean isUser = userId != null && participantUserId.equals(userId);
            boolean isExpert = expertId != null && participantUserId.equals(expertId);
            String participantType = isUser ? "user" : (isExpert ? "expert" : "unknown");

            LoggingService.info("participant_joined", Map.of(
                "participantUserId", participantUserId,
                "participantType", participantType
            ));

            if (!isUser && !isExpert) {
                LoggingService.warn("participant_joined_unknown_participant", Map.of(
                    "participantUserId", participantUserId,
                    "expectedUserId", userId != null ? userId : "null",
                    "expectedExpertId", expertId != null ? expertId : "null"
                ));
                return gson.toJson(Map.of("status", "ignored", "reason", "unknown_participant"));
            }

            // Update order with simple join tracking (no interval storage)
            // Stream API is the source of truth for billing - we just track status here
            com.google.cloud.Timestamp nowTs = com.google.cloud.Timestamp.now();
            DocumentReference orderRef = db.collection("users").document(userId)
                    .collection("orders").document(orderId);

            final boolean finalIsUser = isUser;
            final boolean finalIsExpert = isExpert;

            Map<String, Object> result = db.runTransaction(transaction -> {
                DocumentSnapshot orderDoc = transaction.get(orderRef).get();
                if (!orderDoc.exists()) {
                    throw new IllegalStateException("Order not found");
                }

                Map<String, Object> updates = new HashMap<>();

                // Track simple join timestamps (for backward compatibility and logging)
                if (finalIsUser && orderDoc.getTimestamp("user_joined_at") == null) {
                    updates.put("user_joined_at", nowTs);
                    LoggingService.info("user_join_tracked");
                }

                if (finalIsExpert && orderDoc.getTimestamp("expert_joined_at") == null) {
                    updates.put("expert_joined_at", nowTs);
                    LoggingService.info("expert_join_tracked");
                }

                // Check if both have now joined (for status tracking)
                com.google.cloud.Timestamp userJoinedAt = orderDoc.getTimestamp("user_joined_at");
                com.google.cloud.Timestamp expertJoinedAt = orderDoc.getTimestamp("expert_joined_at");

                // After this update, check if both will be present
                boolean userHasJoined = userJoinedAt != null || finalIsUser;
                boolean expertHasJoined = expertJoinedAt != null || finalIsExpert;
                boolean bothJoined = userHasJoined && expertHasJoined;

                if (bothJoined && orderDoc.getTimestamp("both_participants_joined_at") == null) {
                    updates.put("both_participants_joined_at", nowTs);
                    LoggingService.info("both_participants_now_joined");
                }

                // Update order status to CONNECTED if still INITIATED
                String currentStatus = orderDoc.getString("status");
                if ("INITIATED".equals(currentStatus)) {
                    updates.put("status", "CONNECTED");
                    updates.put("start_time", nowTs);
                    LoggingService.info("order_status_changed_to_connected");
                }

                if (!updates.isEmpty()) {
                    transaction.update(orderRef, updates);
                }

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("userJoined", userHasJoined);
                resultMap.put("expertJoined", expertHasJoined);
                resultMap.put("bothJoined", bothJoined);
                return resultMap;
            }).get();

            boolean userJoined = (boolean) result.get("userJoined");
            boolean expertJoined = (boolean) result.get("expertJoined");
            boolean bothJoined = (boolean) result.get("bothJoined");

            return gson.toJson(Map.of(
                "status", "processed",
                "order_id", orderId,
                "participant_type", participantType,
                "user_joined", userJoined,
                "expert_joined", expertJoined,
                "both_joined", bothJoined
            ));

        } catch (Exception e) {
            LoggingService.error("stream_participant_joined_error", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return gson.toJson(Map.of("error", "Error processing participant joined", "message", errorMessage));
        }
    }

    /**
     * Handle Stream call.session_participant_left event.
     * For 1:1 on-demand consultations, when ANY participant leaves, the call ends and billing is finalized.
     * This is similar to WhatsApp calls - when either party leaves, the call is over.
     *
     * REFACTORED: Now uses BillingService.recalculateCharge() which fetches authoritative data
     * from Stream API and computes overlap-based billing idempotently.
     */
    @SuppressWarnings("unchecked")
    private String handleStreamParticipantLeft(String callCid, Map<String, Object> payload) {
        LoggingService.info("handling_stream_participant_left", Map.of("callCid", callCid));

        try {
            OnDemandConsultationService consultationService = new OnDemandConsultationService(db);

            // Find order by stream call CID
            Map<String, Object> orderData = consultationService.getOrderByStreamCallCid(callCid);

            if (orderData == null) {
                LoggingService.info("participant_left_order_not_found", Map.of("callCid", callCid));
                return gson.toJson(Map.of("status", "order_not_found", "call_cid", callCid));
            }

            String orderId = (String) orderData.get("order_id");
            String expertId = (String) orderData.get("expert_id");
            String userId = (String) orderData.get("user_id");
            String orderType = (String) orderData.get("type");
            String orderStatus = (String) orderData.get("status");

            LoggingService.setContext(userId, orderId, expertId);
            LoggingService.info("participant_left_order_found", Map.of(
                "orderType", orderType != null ? orderType : "unknown",
                "orderStatus", orderStatus != null ? orderStatus : "unknown"
            ));

            // Check if already completed - idempotency
            if ("COMPLETED".equals(orderStatus) || "CANCELLED".equals(orderStatus)) {
                LoggingService.info("participant_left_order_already_completed");
                return gson.toJson(Map.of("status", "already_completed", "order_id", orderId != null ? orderId : "unknown"));
            }

            // Only handle on-demand consultations that are still CONNECTED
            if (!"ON_DEMAND_CONSULTATION".equals(orderType) || !"CONNECTED".equals(orderStatus)) {
                LoggingService.info("participant_left_not_active_on_demand", Map.of(
                    "orderType", orderType != null ? orderType : "unknown",
                    "orderStatus", orderStatus != null ? orderStatus : "unknown"
                ));
                return gson.toJson(Map.of("status", "ignored", "reason", "not_active_on_demand"));
            }

            // Extract participant info for logging
            String participantType = "unknown";
            Map<String, Object> participant = (Map<String, Object>) payload.get("participant");
            if (participant != null) {
                Map<String, Object> participantUser = (Map<String, Object>) participant.get("user");
                if (participantUser != null) {
                    String participantUserId = (String) participantUser.get("id");
                    if (participantUserId != null) {
                        if (userId != null && participantUserId.equals(userId)) {
                            participantType = "user";
                        } else if (expertId != null && participantUserId.equals(expertId)) {
                            participantType = "expert";
                        }
                        LoggingService.info("participant_who_left", Map.of(
                            "participantUserId", participantUserId,
                            "participantType", participantType
                        ));
                    }
                }
            }

            // Use BillingService to calculate and apply charge
            // This fetches data from Stream API and computes overlap-based billing
            LoggingService.info("calculating_billing_via_billing_service");
            BillingService billingService = new BillingService(db, isTest);
            BillingService.BillingResult billingResult = billingService.recalculateCharge(callCid);

            LoggingService.info("billing_result", Map.of(
                "success", billingResult.success,
                "status", billingResult.status,
                "billableSeconds", billingResult.billableSeconds != null ? billingResult.billableSeconds : 0L,
                "cost", billingResult.cost != null ? billingResult.cost : 0.0
            ));

            // End the Stream call so the remaining participant is disconnected
            boolean callEnded = false;
            if (billingResult.success) {
                try {
                    StreamService streamService = new StreamService(isTest);
                    String[] cidParts = StreamService.parseCallCid(callCid);
                    if (cidParts != null) {
                        LoggingService.info("ending_stream_call", Map.of("callCid", callCid));
                        callEnded = streamService.endCall(cidParts[0], cidParts[1]);
                        LoggingService.info("stream_call_end_result", Map.of("callEnded", callEnded));
                    }
                } catch (Exception e) {
                    LoggingService.error("error_ending_stream_call", e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "processed");
            response.put("event", "participant_left");
            response.put("call_cid", callCid);
            response.put("order_id", orderId != null ? orderId : "unknown");
            response.put("billing_status", billingResult.status);
            response.put("call_ended", callEnded);
            response.put("participant_type", participantType);

            if (billingResult.billableSeconds != null) {
                response.put("billable_seconds", billingResult.billableSeconds);
            }
            if (billingResult.cost != null) {
                response.put("cost", billingResult.cost);
            }

            return gson.toJson(response);

        } catch (Exception e) {
            LoggingService.error("stream_participant_left_error", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return gson.toJson(Map.of("error", "Error processing participant left", "message", errorMessage));
        }
    }
}
