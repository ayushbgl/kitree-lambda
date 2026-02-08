package in.co.kitree.handlers;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Handler for on-demand consultation operations.
 * Extracted from Handler.java as part of refactoring.
 *
 * Dependencies:
 * - Firestore db: Data access
 * - PythonLambdaService: For Stream token/call creation
 * - isTest flag: Environment detection for Stream service
 */
public class ConsultationHandler {

    // Constants (moved from Handler.java)
    private static final int MIN_CONSULTATION_MINUTES = 3;
    private static final int INITIATED_ORDER_TIMEOUT_SECONDS = 300; // 5 minutes

    private final Firestore db;
    private final PythonLambdaService pythonLambdaService;
    private final boolean isTest;
    private final Gson gson;

    public ConsultationHandler(Firestore db, PythonLambdaService pythonLambdaService, boolean isTest) {
        this.db = db;
        this.pythonLambdaService = pythonLambdaService;
        this.isTest = isTest;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Routes consultation-related function calls to the appropriate handler method.
     *
     * @param functionName The function name from the request
     * @param userId The authenticated user ID
     * @param requestBody The request body
     * @return The response string, or null if the function is not handled by this handler
     */
    public String handleRequest(String functionName, String userId, RequestBody requestBody) {
        try {
            switch (functionName) {
                case "on_demand_consultation_initiate":
                    return handleOnDemandConsultationInitiate(userId, requestBody);
                case "on_demand_consultation_connect":
                    return handleOnDemandConsultationConnect(userId, requestBody);
                case "on_demand_consultation_heartbeat":
                    return handleOnDemandConsultationHeartbeat(userId, requestBody);
                case "update_consultation_max_duration":
                    return handleUpdateConsultationMaxDuration(userId, requestBody);
                case "on_demand_consultation_end":
                    return handleOnDemandConsultationEnd(userId, requestBody);
                case "submit_review":
                    return handleSubmitReview(userId, requestBody);
                case "cleanup_stale_order":
                    return handleCleanupStaleOrder(userId, requestBody);
                case "recalculate_charge":
                    return handleRecalculateCharge(requestBody);
                case "generate_consultation_summary":
                    return handleGenerateConsultationSummary(userId, requestBody);
                case "get_consultation_summary":
                    return handleGetConsultationSummary(userId, requestBody);
                case "get_active_call_for_user":
                    return handleGetActiveCallForUser(userId, requestBody);
                default:
                    return null; // Not handled by this handler
            }
        } catch (Exception e) {
            LoggingService.error("consultation_handler_exception", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Check if a function name is handled by this handler.
     */
    public static boolean handles(String functionName) {
        return functionName != null && (
            functionName.equals("on_demand_consultation_initiate") ||
            functionName.equals("on_demand_consultation_connect") ||
            functionName.equals("on_demand_consultation_heartbeat") ||
            functionName.equals("update_consultation_max_duration") ||
            functionName.equals("on_demand_consultation_end") ||
            functionName.equals("cleanup_stale_order") ||
            functionName.equals("recalculate_charge") ||
            functionName.equals("generate_consultation_summary") ||
            functionName.equals("get_consultation_summary") ||
            functionName.equals("get_active_call_for_user") ||
            functionName.equals("submit_review")
        );
    }

    // ============= Helper Methods =============

    /**
     * Get plan details from Firestore.
     * Moved from Handler.java to support consultation initiation.
     */
    private ServicePlan getPlanDetails(String planId, String expertId) throws ExecutionException, InterruptedException {
        ServicePlan servicePlan = null;
        DocumentReference doc = this.db.collection("users").document(expertId).collection("plans").document(planId);
        ApiFuture<DocumentSnapshot> ref = doc.get();
        DocumentSnapshot documentSnapshot = ref.get();

        if (documentSnapshot.exists()) {
            servicePlan = new ServicePlan();
            servicePlan.setPlanId(planId);
            LoggingService.debug("plan_details_loaded", Map.of("planId", planId, "expertId", expertId));

            Map<String, Object> data = Objects.requireNonNull(documentSnapshot.getData());

            servicePlan.setAmount(((Long) data.getOrDefault("amount", 0L)).doubleValue());
            servicePlan.setCurrency((String) data.getOrDefault("currency", ""));
            servicePlan.setSubscription((Boolean) data.getOrDefault("isSubscription", false));
            servicePlan.setVideo((Boolean) data.getOrDefault("isVideo", false));
            servicePlan.setRazorpayId((String) data.getOrDefault("razorpayId", ""));
            servicePlan.setType((String) data.getOrDefault("type", ""));
            servicePlan.setSubtype((String) data.getOrDefault("subtype", ""));
            servicePlan.setCategory((String) data.getOrDefault("category", ""));

            // Handle both Integer and Long for duration
            Object durationObj = data.getOrDefault("duration", 30L);
            Long duration;
            if (durationObj instanceof Integer) {
                duration = ((Integer) durationObj).longValue();
            } else if (durationObj instanceof Long) {
                duration = (Long) durationObj;
            } else {
                duration = 30L;
            }
            servicePlan.setDuration(duration);
            servicePlan.setDurationUnit((String) data.getOrDefault("durationUnit", "MINUTES"));

            if (documentSnapshot.contains("date")) {
                servicePlan.setDate((com.google.cloud.Timestamp) documentSnapshot.get("date"));
            }
            if (documentSnapshot.contains("sessionStartedAt")) {
                servicePlan.setSessionStartedAt((com.google.cloud.Timestamp) documentSnapshot.get("sessionStartedAt"));
            }
            if (documentSnapshot.contains("sessionCompletedAt")) {
                servicePlan.setSessionCompletedAt((com.google.cloud.Timestamp) documentSnapshot.get("sessionCompletedAt"));
            }
            servicePlan.setTitle((String) data.getOrDefault("title", ""));

            // Read on-demand consultation rate fields
            if (data.containsKey("onDemandRatePerMinuteAudio")) {
                Object audioRateObj = data.get("onDemandRatePerMinuteAudio");
                if (audioRateObj != null) {
                    servicePlan.setOnDemandRatePerMinuteAudio(convertToDouble(audioRateObj));
                }
            }
            if (data.containsKey("onDemandRatePerMinuteVideo")) {
                Object videoRateObj = data.get("onDemandRatePerMinuteVideo");
                if (videoRateObj != null) {
                    servicePlan.setOnDemandRatePerMinuteVideo(convertToDouble(videoRateObj));
                }
            }
            if (data.containsKey("onDemandRatePerMinuteChat")) {
                Object chatRateObj = data.get("onDemandRatePerMinuteChat");
                if (chatRateObj != null) {
                    servicePlan.setOnDemandRatePerMinuteChat(convertToDouble(chatRateObj));
                }
            }
            if (data.containsKey("onDemandCurrency")) {
                servicePlan.setOnDemandCurrency((String) data.get("onDemandCurrency"));
            }
        }

        return servicePlan;
    }

    private Double convertToDouble(Object obj) {
        if (obj instanceof Double) {
            return (Double) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).doubleValue();
        } else if (obj instanceof Long) {
            return ((Long) obj).doubleValue();
        }
        return null;
    }

    // ============= Consultation Handler Methods =============

    /**
     * Initiate an on-demand consultation.
     * Checks expert status, wallet balance, and creates the consultation order.
     */
    private String handleOnDemandConsultationInitiate(String userId, RequestBody requestBody) throws Exception {
        String expertId = requestBody.getExpertId();
        String consultationType = requestBody.getConsultationType();
        String category = requestBody.getCategory();
        String planId = requestBody.getPlanId();

        if (expertId == null || consultationType == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required fields"));
        }

        // Validate consultation type
        if (!Arrays.asList("audio", "video", "chat").contains(consultationType.toLowerCase())) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Invalid consultation type"));
        }

        // Normalize to lowercase
        consultationType = consultationType.toLowerCase();

        WalletService walletService = new WalletService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

        // Check expert status
        String expertStatus = walletService.getExpertStatus(expertId);
        if (!"ONLINE".equals(expertStatus)) {
            String errorMessage = "BUSY".equals(expertStatus) ? "Expert is busy" : "Expert is offline";
            return gson.toJson(Map.of(
                "success", false,
                "errorCode", "EXPERT_NOT_AVAILABLE",
                "errorMessage", errorMessage
            ));
        }

        // Determine rate from plan
        Double rate = null;
        String currency = WalletService.getDefaultCurrency();

        if (planId != null && !planId.isEmpty()) {
            ServicePlan plan = getPlanDetails(planId, expertId);
            if (plan != null) {
                rate = consultationService.getOnDemandRate(plan, consultationType);
                currency = consultationService.getOnDemandCurrency(plan);
            }
        }

        if (rate == null || rate <= 0) {
            return gson.toJson(Map.of("success", false, "errorMessage", "On-demand rate not configured for this expert"));
        }

        // Get platform fee config
        PlatformFeeConfig feeConfig = walletService.getPlatformFeeConfig(expertId);
        Double platformFeePercent = feeConfig.getFeePercent("ON_DEMAND_CONSULTATION", category);

        // Check wallet balance
        Double walletBalance = walletService.getExpertWalletBalance(userId, expertId, currency);
        Double minimumRequired = rate * MIN_CONSULTATION_MINUTES;

        if (walletBalance < minimumRequired) {
            return gson.toJson(Map.of(
                "success", false,
                "errorCode", "LOW_BALANCE",
                "requiredAmount", minimumRequired,
                "currentBalance", walletBalance,
                "currency", currency,
                "expertId", expertId
            ));
        }

        // Calculate max allowed duration
        Long maxAllowedDuration = (long) ((walletBalance / rate) * 60);

        // Get user and expert names
        FirebaseUser user = UserService.getUserDetails(this.db, userId);
        FirebaseUser expert = UserService.getUserDetails(this.db, expertId);

        final Double finalRate = rate;
        final String finalCurrency = currency;
        final Double finalPlatformFeePercent = platformFeePercent;
        final Long finalMaxAllowedDuration = maxAllowedDuration;
        final String finalConsultationType = consultationType;

        // Create order and lock expert status in a transaction
        String[] orderIdHolder = new String[1];
        try {
            this.db.runTransaction(transaction -> {
                // Double-check expert status
                DocumentReference storeRef = db.collection("users").document(expertId)
                        .collection("public").document("store");
                DocumentSnapshot storeDoc = transaction.get(storeRef).get();

                Boolean isOnline = storeDoc.getBoolean("is_online");
                if (isOnline == null || !isOnline) {
                    throw new RuntimeException("Expert is no longer available");
                }

                String consultationStatus = storeDoc.getString("consultation_status");
                if ("BUSY".equals(consultationStatus)) {
                    throw new RuntimeException("Expert is no longer available");
                }

                walletService.setConsultationStatusInTransaction(transaction, expertId, "BUSY");

                OnDemandConsultationOrder order = new OnDemandConsultationOrder();
                order.setUserId(userId);
                order.setUserName(user != null ? user.getName() : null);
                order.setExpertId(expertId);
                order.setExpertName(expert != null ? expert.getName() : null);
                order.setPlanId(planId);
                order.setConsultationType(finalConsultationType);
                order.setCategory(category);
                order.setExpertRatePerMinute(finalRate);
                order.setCurrency(finalCurrency);
                order.setPlatformFeePercent(finalPlatformFeePercent);
                order.setMaxAllowedDuration(finalMaxAllowedDuration);
                order.setStatus("INITIATED");

                orderIdHolder[0] = consultationService.createOrderInTransaction(transaction, order);

                return null;
            }).get();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Expert is no longer available")) {
                return gson.toJson(Map.of(
                    "success", false,
                    "errorCode", "EXPERT_NOT_AVAILABLE",
                    "errorMessage", "Expert is no longer available"
                ));
            }
            throw e;
        }

        String orderId = orderIdHolder[0];

        // Create GetStream call for audio/video consultations
        String streamUserToken = null;
        String streamCallCid = null;

        if ("audio".equals(consultationType) || "video".equals(consultationType)) {
            try {
                PythonLambdaEventRequest getStreamTokenEvent = new PythonLambdaEventRequest();
                getStreamTokenEvent.setFunction("get_stream_user_token");
                getStreamTokenEvent.setUserId(userId);
                getStreamTokenEvent.setUserName(user != null ? user.getName() : userId);
                getStreamTokenEvent.setTest(isTest);

                streamUserToken = pythonLambdaService.invokePythonLambda(getStreamTokenEvent).getStreamUserToken();

                PythonLambdaEventRequest createCallEvent = new PythonLambdaEventRequest();
                createCallEvent.setFunction("create_call");
                createCallEvent.setType("CONSULTATION");
                createCallEvent.setUserId(userId);
                createCallEvent.setUserName(user != null ? user.getName() : null);
                createCallEvent.setExpertId(expertId);
                createCallEvent.setExpertName(expert != null ? expert.getName() : null);
                createCallEvent.setOrderId(orderId);
                createCallEvent.setVideo("video".equals(consultationType));
                createCallEvent.setTest(isTest);

                pythonLambdaService.invokePythonLambda(createCallEvent);

                streamCallCid = "consultation_" + consultationType + ":" + orderId;

                consultationService.updateStreamCallCid(userId, orderId, streamCallCid);
            } catch (Exception e) {
                LoggingService.error("stream_call_creation_failed", e, Map.of("orderId", orderId));

                try {
                    final String finalOrderId = orderId;
                    this.db.runTransaction(transaction -> {
                        boolean hasOtherConsultations = consultationService.hasOtherConnectedConsultationsInTransaction(
                            transaction, expertId, finalOrderId
                        );

                        if (!hasOtherConsultations) {
                            walletService.setConsultationStatusInTransaction(transaction, expertId, "FREE");
                        }

                        consultationService.markOrderAsFailedInTransaction(transaction, userId, finalOrderId);

                        return null;
                    }).get();
                } catch (Exception compensationError) {
                    LoggingService.error("compensation_transaction_failed", compensationError, Map.of(
                        "orderId", orderId,
                        "expertId", expertId
                    ));
                }

                return gson.toJson(Map.of(
                    "success", false,
                    "errorCode", "STREAM_CALL_CREATION_FAILED",
                    "errorMessage", "Failed to create video call. Please try again."
                ));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderId", orderId);
        response.put("maxDuration", maxAllowedDuration);
        response.put("rate", rate);
        response.put("currency", currency);
        response.put("platformFeePercent", platformFeePercent);

        if (streamUserToken != null) {
            response.put("streamUserToken", streamUserToken);
        }
        if (streamCallCid != null) {
            response.put("callCid", streamCallCid);
        }

        return gson.toJson(response);
    }

    /**
     * Connect an on-demand consultation (called when the call actually starts).
     */
    private String handleOnDemandConsultationConnect(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();

        if (orderId == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
        }

        WalletService walletService = new WalletService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);

        if (order == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order not found"));
        }

        if (!"INITIATED".equals(order.getStatus())) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order is not in INITIATED status"));
        }

        consultationService.connectOrder(userId, orderId);

        return gson.toJson(Map.of("success", true));
    }

    /**
     * Heartbeat for an active on-demand consultation.
     */
    private String handleOnDemandConsultationHeartbeat(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();

        if (orderId == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
        }

        WalletService walletService = new WalletService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);

        if (order == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order not found"));
        }

        if (!"CONNECTED".equals(order.getStatus())) {
            return gson.toJson(Map.of(
                "status", "TERMINATE",
                "reason", "NOT_CONNECTED"
            ));
        }

        Long remainingSeconds = consultationService.calculateRemainingSeconds(order);

        if (remainingSeconds <= 0) {
            return gson.toJson(Map.of(
                "status", "TERMINATE",
                "reason", "LOW_BALANCE"
            ));
        }

        return gson.toJson(Map.of(
            "status", "CONTINUE",
            "remainingSeconds", remainingSeconds
        ));
    }

    /**
     * Update max duration for an active consultation (mid-consultation recharge).
     */
    private String handleUpdateConsultationMaxDuration(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        Double additionalAmount = requestBody.getAdditionalAmount();

        if (orderId == null || additionalAmount == null || additionalAmount <= 0) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Invalid request parameters"));
        }

        WalletService walletService = new WalletService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);

        if (order == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order not found"));
        }

        if (!"CONNECTED".equals(order.getStatus())) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Consultation is not active"));
        }

        Double rate = order.getExpertRatePerMinute();
        String currency = order.getCurrency();
        String expertId = order.getExpertId();

        Long[] newMaxDurationHolder = new Long[1];
        Long[] additionalDurationHolder = new Long[1];

        try {
            this.db.runTransaction(transaction -> {
                Double newBalance = walletService.updateExpertWalletBalanceInTransaction(
                    transaction, userId, expertId, currency, additionalAmount
                );

                WalletTransaction walletTransaction = new WalletTransaction();
                walletTransaction.setType("RECHARGE");
                walletTransaction.setSource("MID_CONSULTATION");
                walletTransaction.setAmount(additionalAmount);
                walletTransaction.setCurrency(currency);
                walletTransaction.setOrderId(orderId);
                walletTransaction.setStatus("COMPLETED");
                walletTransaction.setCreatedAt(com.google.cloud.Timestamp.now());

                walletService.createExpertWalletTransactionInTransaction(transaction, userId, expertId, walletTransaction);

                Long maxDurationFromBalance = (long) ((newBalance / rate) * 60);
                Long elapsedSeconds = consultationService.calculateElapsedSeconds(order);

                if (maxDurationFromBalance <= elapsedSeconds) {
                    throw new RuntimeException("Insufficient balance to extend consultation");
                }

                Long currentMaxDuration = order.getMaxAllowedDuration();
                Long newMaxDuration = maxDurationFromBalance;

                additionalDurationHolder[0] = newMaxDuration - currentMaxDuration;

                consultationService.updateMaxDurationInTransaction(transaction, userId, orderId, newMaxDuration);
                newMaxDurationHolder[0] = newMaxDuration;

                return null;
            }).get();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Insufficient balance")) {
                return gson.toJson(Map.of(
                    "success", false,
                    "errorCode", "INSUFFICIENT_BALANCE",
                    "errorMessage", "Insufficient wallet balance to extend consultation"
                ));
            }
            throw e;
        }

        return gson.toJson(Map.of(
            "success", true,
            "newMaxDuration", newMaxDurationHolder[0],
            "additionalDuration", additionalDurationHolder[0]
        ));
    }

    /**
     * End an on-demand consultation.
     * Calculates cost, deducts from user's wallet, and credits expert's earnings.
     */
    private String handleOnDemandConsultationEnd(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();

        if (orderId == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
        }

        WalletService walletService = new WalletService(this.db);
        ExpertEarningsService earningsService = new ExpertEarningsService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);

        if (order == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order not found"));
        }

        // IDEMPOTENCY: If already COMPLETED, return existing data
        if ("COMPLETED".equals(order.getStatus())) {
            LoggingService.info("order_already_completed_idempotent", Map.of("orderId", orderId, "status", "COMPLETED"));
            Double remainingBalance = walletService.getExpertWalletBalance(userId, order.getExpertId(), order.getCurrency());
            return gson.toJson(Map.of(
                "success", true,
                "cost", order.getCost() != null ? order.getCost() : 0.0,
                "duration", order.getDurationSeconds() != null ? order.getDurationSeconds() : 0L,
                "currency", order.getCurrency(),
                "remainingBalance", remainingBalance != null ? remainingBalance : 0.0,
                "expertId", order.getExpertId(),
                "message", "Consultation already completed"
            ));
        }

        if (!Arrays.asList("CONNECTED", "TERMINATED").contains(order.getStatus())) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Consultation is not active (status: " + order.getStatus() + ")"));
        }

        // Calculate billing
        Long totalDurationSeconds = consultationService.calculateElapsedSeconds(order);
        Long billableSeconds = consultationService.calculateBillableSeconds(order);
        Double cost = consultationService.calculateCost(billableSeconds, order.getExpertRatePerMinute());
        Double platformFeeAmount = consultationService.calculatePlatformFee(cost, order.getPlatformFeePercent());
        Double expertEarnings = cost - platformFeeAmount;

        String expertId = order.getExpertId();
        String currency = order.getCurrency();

        LoggingService.info("consultation_end_billing", Map.of(
            "totalDurationSeconds", totalDurationSeconds,
            "billableSeconds", billableSeconds,
            "cost", cost
        ));

        final Long finalDurationSeconds = billableSeconds;
        final Double finalCost = cost;
        final Double finalPlatformFeeAmount = platformFeeAmount;
        final Double finalExpertEarnings = expertEarnings;
        final String finalOrderId = orderId;

        Double[] remainingBalanceHolder = new Double[1];

        try {
            this.db.runTransaction(transaction -> {
                remainingBalanceHolder[0] = walletService.updateExpertWalletBalanceInTransaction(
                    transaction, userId, expertId, currency, -finalCost
                );

                earningsService.creditExpertEarningsInTransaction(
                    transaction, expertId, currency, finalCost, finalPlatformFeeAmount,
                    finalOrderId, "On-demand consultation earning"
                );

                WalletTransaction deductionTransaction = new WalletTransaction();
                deductionTransaction.setType("CONSULTATION_DEDUCTION");
                deductionTransaction.setSource("PAYMENT");
                deductionTransaction.setAmount(-finalCost);
                deductionTransaction.setCurrency(currency);
                deductionTransaction.setOrderId(finalOrderId);
                deductionTransaction.setStatus("COMPLETED");
                deductionTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
                deductionTransaction.setDurationSeconds(finalDurationSeconds);
                deductionTransaction.setRatePerMinute(order.getExpertRatePerMinute());
                deductionTransaction.setConsultationType(order.getConsultationType());
                deductionTransaction.setCategory(order.getCategory());

                walletService.createExpertWalletTransactionInTransaction(transaction, userId, expertId, deductionTransaction);

                consultationService.completeOrderInTransaction(
                    transaction, userId, finalOrderId,
                    finalDurationSeconds, finalCost, finalPlatformFeeAmount, finalExpertEarnings
                );

                boolean hasOtherActive = consultationService.hasOtherConnectedConsultationsInTransaction(
                    transaction, expertId, finalOrderId
                );

                if (!hasOtherActive) {
                    walletService.setConsultationStatusInTransaction(transaction, expertId, "FREE");
                }

                return null;
            }).get();
        } catch (Exception e) {
            // Check if concurrent completion
            OnDemandConsultationOrder refreshedOrder = consultationService.getOrder(userId, orderId);
            if (refreshedOrder != null && "COMPLETED".equals(refreshedOrder.getStatus())) {
                LoggingService.info("order_completed_by_concurrent_request", Map.of("orderId", orderId, "completedBy", "webhook"));
                Double remainingBalance = walletService.getExpertWalletBalance(userId, expertId, currency);
                return gson.toJson(Map.of(
                    "success", true,
                    "cost", refreshedOrder.getCost() != null ? refreshedOrder.getCost() : 0.0,
                    "duration", refreshedOrder.getDurationSeconds() != null ? refreshedOrder.getDurationSeconds() : 0L,
                    "currency", refreshedOrder.getCurrency(),
                    "remainingBalance", remainingBalance != null ? remainingBalance : 0.0,
                    "expertId", expertId,
                    "message", "Consultation completed by server"
                ));
            }
            throw e;
        }

        return gson.toJson(Map.of(
            "success", true,
            "cost", cost,
            "duration", billableSeconds,
            "currency", currency,
            "remainingBalance", remainingBalanceHolder[0],
            "expertId", expertId
        ));
    }

    /**
     * Cleanup a stale order.
     */
    private String handleCleanupStaleOrder(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        LoggingService.setFunction("cleanup_stale_order");
        LoggingService.setContext(userId, orderId, null);
        LoggingService.info("cleanup_stale_order_started");

        if (orderId == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
        }

        WalletService walletService = new WalletService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

        Map<String, Object> orderData = null;

        OnDemandConsultationOrder userOrder = consultationService.getOrder(userId, orderId);
        if (userOrder != null) {
            orderData = Map.of(
                "orderId", orderId,
                "userId", userId,
                "expertId", userOrder.getExpertId(),
                "status", userOrder.getStatus(),
                "streamCallCid", userOrder.getStreamCallCid() != null ? userOrder.getStreamCallCid() : ""
            );
        } else {
            String streamCallCidGuess = "consultation_video:" + orderId;
            orderData = consultationService.getOrderByStreamCallCid(streamCallCidGuess);
            if (orderData == null) {
                streamCallCidGuess = "consultation_audio:" + orderId;
                orderData = consultationService.getOrderByStreamCallCid(streamCallCidGuess);
            }
        }

        if (orderData == null) {
            LoggingService.warn("cleanup_order_not_found");
            return gson.toJson(Map.of("success", false, "errorMessage", "Order not found"));
        }

        String orderUserId = (String) orderData.get("userId");
        String expertId = (String) orderData.get("expertId");
        String status = (String) orderData.get("status");
        String streamCallCid = (String) orderData.get("streamCallCid");

        LoggingService.setContext(orderUserId, orderId, expertId);
        LoggingService.info("cleanup_order_found", Map.of("status", status));

        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            LoggingService.info("cleanup_order_already_terminal");
            if (expertId != null) {
                boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
                if (!hasOtherActive) {
                    walletService.setConsultationStatus(expertId, "FREE");
                }
            }
            return gson.toJson(Map.of("success", true, "status", status, "message", "Order already in terminal state"));
        }

        // Try to end Stream call
        if (streamCallCid != null && !streamCallCid.isEmpty()) {
            try {
                StreamService streamService = new StreamService(isTest);
                String[] cidParts = StreamService.parseCallCid(streamCallCid);
                if (cidParts != null) {
                    boolean ended = streamService.endCall(cidParts[0], cidParts[1]);
                    LoggingService.info("stream_call_end_attempt", Map.of("ended", ended));
                }
            } catch (Exception e) {
                LoggingService.warn("stream_call_end_error", Map.of("error", e.getMessage()));
            }
        }

        // Mark order as FAILED
        DocumentReference orderRef = this.db.collection("users").document(orderUserId)
                .collection("orders").document(orderId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "FAILED");
        updates.put("end_time", com.google.cloud.Timestamp.now());
        updates.put("failure_reason", "CLEANUP_STALE_ORDER");
        orderRef.update(updates).get();

        // Free expert if needed
        if (expertId != null) {
            boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
            if (!hasOtherActive) {
                LoggingService.info("freeing_expert_after_cleanup");
                walletService.setConsultationStatus(expertId, "FREE");
            }
        }

        LoggingService.info("cleanup_stale_order_completed");
        return gson.toJson(Map.of(
            "success", true,
            "status", "FAILED",
            "message", "Order cleaned up successfully"
        ));
    }

    /**
     * Recalculate billing charge for a consultation.
     */
    private String handleRecalculateCharge(RequestBody requestBody) {
        LoggingService.setFunction("recalculate_charge");
        LoggingService.info("recalculate_charge_started");

        String callCid = requestBody.getCallCid();
        if (callCid == null || callCid.isEmpty()) {
            LoggingService.warn("recalculate_charge_missing_call_cid");
            return gson.toJson(Map.of("success", false, "error", "call_cid is required"));
        }

        LoggingService.info("recalculate_charge_processing", Map.of("callCid", callCid));

        BillingService billingService = new BillingService(db, isTest);
        BillingService.BillingResult result = billingService.recalculateCharge(callCid);

        LoggingService.info("recalculate_charge_result", Map.of("success", result.success, "status", result.status));

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success);
        response.put("status", result.status);

        if (result.billableSeconds != null) response.put("billable_seconds", result.billableSeconds);
        if (result.cost != null) response.put("cost", result.cost);
        if (result.platformFee != null) response.put("platform_fee", result.platformFee);
        if (result.expertEarnings != null) response.put("expert_earnings", result.expertEarnings);
        if (result.errorMessage != null) response.put("error", result.errorMessage);

        return gson.toJson(response);
    }

    /**
     * Generate consultation summary.
     */
    private String handleGenerateConsultationSummary(String userId, RequestBody requestBody) {
        LoggingService.setFunction("generate_consultation_summary");
        LoggingService.setContext(userId, requestBody.getOrderId(), null);
        LoggingService.info("generate_consultation_summary_started");

        String orderId = requestBody.getOrderId();
        if (orderId == null || orderId.isEmpty()) {
            LoggingService.warn("generate_consultation_summary_missing_order_id");
            return gson.toJson(Map.of("success", false, "error", "order_id is required"));
        }

        ConsultationSummaryService summaryService = new ConsultationSummaryService(db, isTest);
        ConsultationSummaryService.SummaryResult result = summaryService.generateSummary(userId, orderId);

        if (result.success) {
            LoggingService.info("generate_consultation_summary_success");
            return gson.toJson(Map.of("success", true, "summary", result.summary));
        } else {
            LoggingService.warn("generate_consultation_summary_failed", Map.of("error", result.errorMessage));
            return gson.toJson(Map.of("success", false, "error", result.errorMessage));
        }
    }

    /**
     * Get existing consultation summary.
     */
    private String handleGetConsultationSummary(String userId, RequestBody requestBody) {
        LoggingService.setFunction("get_consultation_summary");
        LoggingService.setContext(userId, requestBody.getOrderId(), null);
        LoggingService.info("get_consultation_summary_started");

        String orderId = requestBody.getOrderId();
        if (orderId == null || orderId.isEmpty()) {
            LoggingService.warn("get_consultation_summary_missing_order_id");
            return gson.toJson(Map.of("success", false, "error", "order_id is required"));
        }

        ConsultationSummaryService summaryService = new ConsultationSummaryService(db, isTest);
        ConsultationSummaryService.SummaryResult result = summaryService.getSummary(userId, orderId);

        if (result.success) {
            LoggingService.info("get_consultation_summary_success");
            return gson.toJson(Map.of("success", true, "summary", result.summary));
        } else {
            LoggingService.info("get_consultation_summary_not_found", Map.of("error", result.errorMessage));
            return gson.toJson(Map.of("success", false, "error", result.errorMessage));
        }
    }

    /**
     * Get active call for a user (for rejoin functionality).
     */
    private String handleGetActiveCallForUser(String userId, RequestBody requestBody) throws Exception {
        LoggingService.setFunction("get_active_call_for_user");
        LoggingService.setContext(userId, null, null);
        LoggingService.info("get_active_call_for_user_started");

        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db);

        Query activeOrdersQuery = this.db.collection("users").document(userId)
                .collection("orders")
                .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                .whereIn("status", Arrays.asList("INITIATED", "CONNECTED"));

        QuerySnapshot snapshot = activeOrdersQuery.get().get();

        if (snapshot.isEmpty()) {
            LoggingService.info("no_active_call_found");
            return gson.toJson(Map.of("success", true, "hasActiveCall", false));
        }

        QueryDocumentSnapshot latestDoc = null;
        for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
            if (latestDoc == null) {
                latestDoc = doc;
            } else {
                com.google.cloud.Timestamp docCreated = doc.getTimestamp("created_at");
                com.google.cloud.Timestamp latestCreated = latestDoc.getTimestamp("created_at");
                if (docCreated != null && latestCreated != null && docCreated.compareTo(latestCreated) > 0) {
                    latestDoc = doc;
                }
            }
        }

        if (latestDoc == null) {
            return gson.toJson(Map.of("success", true, "hasActiveCall", false));
        }

        String status = latestDoc.getString("status");
        com.google.cloud.Timestamp createdAt = latestDoc.getTimestamp("created_at");
        long nowMillis = System.currentTimeMillis();

        if ("INITIATED".equals(status) && createdAt != null) {
            long elapsedSeconds = (nowMillis - createdAt.toDate().getTime()) / 1000;
            if (elapsedSeconds >= INITIATED_ORDER_TIMEOUT_SECONDS) {
                LoggingService.info("active_call_is_stale_initiated", Map.of("elapsedSeconds", elapsedSeconds));
                return gson.toJson(Map.of(
                    "success", true,
                    "hasActiveCall", false,
                    "message", "Active call was stale and should be cleaned up"
                ));
            }
        }

        String orderId = latestDoc.getId();
        String expertId = latestDoc.getString("expert_id");
        String expertName = latestDoc.getString("expert_name");
        String consultationType = latestDoc.getString("consultation_type");
        String streamCallCid = latestDoc.getString("stream_call_cid");

        LoggingService.info("active_call_found", Map.of("orderId", orderId, "status", status));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("hasActiveCall", true);
        response.put("orderId", orderId);
        response.put("expertId", expertId);
        response.put("expertName", expertName);
        response.put("consultationType", consultationType);
        response.put("status", status);
        if (streamCallCid != null) {
            response.put("streamCallCid", streamCallCid);
        }

        return gson.toJson(response);
    }

    /**
     * Submits or updates a review for a consultation booking.
     * Uses a transaction to atomically update both the booking review and expert rating aggregates.
     */
    @SuppressWarnings("unchecked")
    private String handleSubmitReview(String userId, RequestBody requestBody) {
        try {
            String bookingId = requestBody.getBookingId();
            Integer rating = requestBody.getRating();
            String comment = requestBody.getComment();
            Integer previousRating = requestBody.getPreviousRating();

            if (bookingId == null || bookingId.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Booking ID is required"));
            }
            if (rating == null || rating < 1 || rating > 5) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Rating must be between 1 and 5"));
            }
            if (comment != null && comment.length() > 500) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Comment must be under 500 characters"));
            }

            DocumentReference bookingRef = db.collection("users").document(userId).collection("orders").document(bookingId);
            DocumentSnapshot bookingDoc = bookingRef.get().get();

            if (!bookingDoc.exists()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Booking not found"));
            }

            String bookingUserId = bookingDoc.getString("user_id");
            if (bookingUserId == null || !bookingUserId.equals(userId)) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: You can only review your own bookings"));
            }

            String bookingType = bookingDoc.getString("type");
            if (!"CONSULTATION".equals(bookingType)) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Only consultations can be reviewed"));
            }

            Object sessionCompletedAt = bookingDoc.get("session_completed_at");
            if (sessionCompletedAt == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Session must be completed before reviewing"));
            }

            Long durationSeconds = bookingDoc.getLong("duration_seconds");
            if (durationSeconds == null || durationSeconds < 600) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Sessions under 10 minutes cannot be reviewed"));
            }

            String expertId = bookingDoc.getString("expert_id");
            if (expertId == null || expertId.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID not found on booking"));
            }

            boolean isEdit = previousRating != null;
            Map<String, Object> existingReview = (Map<String, Object>) bookingDoc.get("review");
            if (existingReview != null && previousRating == null) {
                Number existingRatingVal = (Number) existingReview.get("rating");
                if (existingRatingVal != null) {
                    previousRating = existingRatingVal.intValue();
                    isEdit = true;
                }
            }

            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("rating", rating);
            reviewData.put("comment", comment != null ? comment.trim() : null);

            if (isEdit) {
                reviewData.put("updated_at", FieldValue.serverTimestamp());
                if (existingReview != null && existingReview.get("created_at") != null) {
                    reviewData.put("created_at", existingReview.get("created_at"));
                } else {
                    reviewData.put("created_at", FieldValue.serverTimestamp());
                }
            } else {
                reviewData.put("created_at", FieldValue.serverTimestamp());
            }

            DocumentReference expertStoreRef = db.collection("users").document(expertId).collection("public").document("store");

            final Integer finalPreviousRating = previousRating;
            final boolean finalIsEdit = isEdit;
            final Integer finalRating = rating;

            db.runTransaction(transaction -> {
                transaction.update(bookingRef, "review", reviewData);
                transaction.update(bookingRef, "updated_at", FieldValue.serverTimestamp());

                if (finalIsEdit && finalPreviousRating != null) {
                    int ratingDelta = finalRating - finalPreviousRating;
                    transaction.update(expertStoreRef, "ratingSum", FieldValue.increment(ratingDelta));
                } else {
                    transaction.update(expertStoreRef, "ratingSum", FieldValue.increment(finalRating));
                    transaction.update(expertStoreRef, "ratingCount", FieldValue.increment(1));
                }

                return null;
            }).get();

            LoggingService.info("review_submitted", Map.of(
                "userId", userId,
                "bookingId", bookingId,
                "rating", rating,
                "isEdit", finalIsEdit
            ));

            return gson.toJson(Map.of("success", true));

        } catch (Exception e) {
            LoggingService.error("submit_review_failed", e);
            return gson.toJson(Map.of("success", false, "errorMessage", "Failed to submit review: " + e.getMessage()));
        }
    }

    /**
     * Auto-terminate consultations cron entrypoint.
     * Called from Handler.handleRequest for scheduled EventBridge events.
     */
    public String handleAutoTerminateConsultations(boolean isTest) {
        // Delegate to the auto-terminate logic extracted from Handler.java
        long nowMillis = System.currentTimeMillis();
        int terminatedCount = 0;
        int errorCount = 0;

        try {
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

            com.google.cloud.Timestamp now = com.google.cloud.Timestamp.now();
            nowMillis = now.toDate().getTime();

            Query query = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("status", "CONNECTED");

            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : query.get().get().getDocuments()) {
                try {
                    String orderId = doc.getId();
                    String userId = doc.getReference().getParent().getParent().getId();
                    com.google.cloud.Timestamp startTime = doc.getTimestamp("start_time");
                    Long maxAllowedDuration = doc.getLong("max_allowed_duration");

                    if (startTime == null || maxAllowedDuration == null) {
                        continue;
                    }

                    long startTimeMillis = startTime.toDate().getTime();
                    long elapsedSeconds = (nowMillis - startTimeMillis) / 1000;

                    if (elapsedSeconds >= (maxAllowedDuration + AUTO_TERMINATE_GRACE_PERIOD_SECONDS)) {
                        LoggingService.setContext(userId, orderId, null);
                        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
                        if (order != null && "CONNECTED".equals(order.getStatus())) {
                            java.util.List<Map<String, Object>> userIntervalMaps = order.getUserIntervals();
                            java.util.List<Map<String, Object>> expertIntervalMaps = order.getExpertIntervals();
                            Long billableSeconds;

                            if (userIntervalMaps != null && !userIntervalMaps.isEmpty() &&
                                expertIntervalMaps != null && !expertIntervalMaps.isEmpty()) {
                                billableSeconds = consultationService.calculateOverlapFromIntervals(
                                    consultationService.parseIntervalsFromList(userIntervalMaps),
                                    consultationService.parseIntervalsFromList(expertIntervalMaps),
                                    order.getMaxAllowedDuration());
                            } else {
                                billableSeconds = consultationService.calculateBillableSeconds(order);
                            }

                            Double cost = consultationService.calculateCost(billableSeconds, order.getExpertRatePerMinute());
                            Double platformFeeAmount = consultationService.calculatePlatformFee(cost, order.getPlatformFeePercent());
                            Double expertEarnings = cost - platformFeeAmount;
                            String expertId = order.getExpertId();
                            String currency = order.getCurrency();

                            final Long finalDurationSeconds = billableSeconds;
                            final Double finalCost = cost;
                            final Double finalPlatformFeeAmount = platformFeeAmount;
                            final String finalUserId = userId;
                            final String finalOrderId = orderId;
                            final String finalExpertId = expertId;
                            final String finalCurrency = currency;
                            ExpertEarningsService earningsService = new ExpertEarningsService(this.db);

                            this.db.runTransaction(transaction -> {
                                DocumentReference userExpertWalletRef = db.collection("users").document(finalUserId)
                                        .collection("expert_wallets").document(finalExpertId);
                                DocumentSnapshot userExpertWalletDoc = transaction.get(userExpertWalletRef).get();
                                DocumentReference expertUserRef = db.collection("users").document(finalExpertId);
                                DocumentSnapshot expertUserDoc = transaction.get(expertUserRef).get();
                                DocumentReference orderRef = db.collection("users").document(finalUserId)
                                        .collection("orders").document(finalOrderId);
                                DocumentSnapshot orderDoc = transaction.get(orderRef).get();

                                if (!orderDoc.exists() || !"CONNECTED".equals(orderDoc.getString("status"))) {
                                    throw new IllegalStateException("Order is not in CONNECTED status: " + finalOrderId);
                                }

                                boolean hasOtherActive = consultationService.hasOtherConnectedConsultationsInTransaction(
                                    transaction, finalExpertId, finalOrderId);

                                DocumentReference expertStoreRef2 = db.collection("users").document(finalExpertId)
                                        .collection("public").document("store");
                                DocumentSnapshot expertStoreDoc = transaction.get(expertStoreRef2).get();

                                walletService.updateExpertWalletBalanceInTransaction(
                                    transaction, finalUserId, finalExpertId, finalCurrency, -finalCost);
                                earningsService.creditExpertEarningsInTransaction(
                                    transaction, finalExpertId, finalCurrency, finalCost, finalPlatformFeeAmount,
                                    finalOrderId, "On-demand consultation earning (auto-terminated)");

                                WalletTransaction deductionTransaction = new WalletTransaction();
                                deductionTransaction.setType("CONSULTATION_DEDUCTION");
                                deductionTransaction.setSource("PAYMENT");
                                deductionTransaction.setAmount(-finalCost);
                                deductionTransaction.setCurrency(finalCurrency);
                                deductionTransaction.setOrderId(finalOrderId);
                                deductionTransaction.setStatus("COMPLETED");
                                deductionTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
                                deductionTransaction.setDurationSeconds(finalDurationSeconds);
                                deductionTransaction.setRatePerMinute(order.getExpertRatePerMinute());
                                deductionTransaction.setConsultationType(order.getConsultationType());
                                deductionTransaction.setCategory(order.getCategory());
                                walletService.createExpertWalletTransactionInTransaction(transaction, finalUserId, finalExpertId, deductionTransaction);

                                Map<String, Object> orderUpdates = new HashMap<>();
                                orderUpdates.put("status", "COMPLETED");
                                orderUpdates.put("end_time", com.google.cloud.Timestamp.now());
                                orderUpdates.put("duration_seconds", finalDurationSeconds);
                                orderUpdates.put("cost", finalCost);
                                orderUpdates.put("platform_fee_amount", finalPlatformFeeAmount);
                                orderUpdates.put("expert_earnings", expertEarnings);
                                transaction.update(orderRef, orderUpdates);

                                if (!hasOtherActive) {
                                    Map<String, Object> statusUpdates = new HashMap<>();
                                    statusUpdates.put("consultation_status", "FREE");
                                    statusUpdates.put("consultation_status_updated_at", com.google.cloud.Timestamp.now());
                                    transaction.update(expertStoreRef2, statusUpdates);
                                }
                                return null;
                            }).get();

                            terminatedCount++;
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    LoggingService.error("auto_terminate_consultation_error", e, Map.of("orderId", doc.getId()));
                }
            }
        } catch (Exception e) {
            LoggingService.error("auto_terminate_consultations_fatal_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }

        // Auto-fail stale INITIATED orders
        int failedInitiatedCount = 0;
        try {
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

            Query initiatedQuery = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("status", "INITIATED");

            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : initiatedQuery.get().get().getDocuments()) {
                try {
                    String orderId = doc.getId();
                    String userId = doc.getReference().getParent().getParent().getId();
                    String expertId = doc.getString("expert_id");
                    com.google.cloud.Timestamp createdAt = doc.getTimestamp("created_at");

                    if (createdAt == null) continue;

                    long elapsedSeconds = (nowMillis - createdAt.toDate().getTime()) / 1000;
                    if (elapsedSeconds >= INITIATED_ORDER_TIMEOUT_SECONDS) {
                        DocumentReference orderRef = doc.getReference();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", "FAILED");
                        updates.put("end_time", com.google.cloud.Timestamp.now());
                        updates.put("failure_reason", "INITIATED_TIMEOUT");
                        orderRef.update(updates).get();

                        if (expertId != null) {
                            boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
                            boolean hasOtherInitiated = hasNonStaleInitiatedOrders(expertId, orderId, nowMillis);
                            if (!hasOtherActive && !hasOtherInitiated) {
                                walletService.setConsultationStatus(expertId, "FREE");
                            }
                        }

                        String streamCallCid = doc.getString("stream_call_cid");
                        if (streamCallCid != null) {
                            try {
                                StreamService streamSvc = new StreamService(isTest);
                                String[] cidParts = StreamService.parseCallCid(streamCallCid);
                                if (cidParts != null) streamSvc.endCall(cidParts[0], cidParts[1]);
                            } catch (Exception ignore) {}
                        }
                        failedInitiatedCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    LoggingService.error("auto_fail_initiated_order_error", e, Map.of("orderId", doc.getId()));
                }
            }
        } catch (Exception e) {
            LoggingService.error("stale_initiated_orders_cleanup_error", e);
        }

        // Process pending summaries
        int summariesProcessed = 0, summariesSucceeded = 0, summariesFailed = 0, summariesSkipped = 0;
        try {
            ConsultationSummaryService summaryService = new ConsultationSummaryService(db, isTest);
            Map<String, Integer> summaryResults = summaryService.processPendingSummaries(5);
            summariesProcessed = summaryResults.getOrDefault("processed", 0);
            summariesSucceeded = summaryResults.getOrDefault("succeeded", 0);
            summariesFailed = summaryResults.getOrDefault("failed", 0);
            summariesSkipped = summaryResults.getOrDefault("skipped", 0);
        } catch (Exception e) {
            LoggingService.error("process_pending_summaries_error", e);
        }

        return gson.toJson(Map.of(
            "success", true,
            "terminatedCount", terminatedCount,
            "failedInitiatedCount", failedInitiatedCount,
            "errorCount", errorCount,
            "summariesProcessed", summariesProcessed,
            "summariesSucceeded", summariesSucceeded,
            "summariesFailed", summariesFailed,
            "summariesSkipped", summariesSkipped
        ));
    }

    private boolean hasNonStaleInitiatedOrders(String expertId, String excludeOrderId, long nowMillis) {
        try {
            Query initiatedQuery = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("expertId", expertId)
                    .whereEqualTo("status", "INITIATED");

            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : initiatedQuery.get().get().getDocuments()) {
                String orderId = doc.getId();
                if (excludeOrderId != null && orderId.equals(excludeOrderId)) continue;

                com.google.cloud.Timestamp createdAt = doc.getTimestamp("created_at");
                if (createdAt == null) return true;

                long elapsedSeconds = (nowMillis - createdAt.toDate().getTime()) / 1000;
                if (elapsedSeconds < INITIATED_ORDER_TIMEOUT_SECONDS) return true;
            }
            return false;
        } catch (Exception e) {
            LoggingService.error("error_checking_non_stale_initiated_orders", e, Map.of("expertId", expertId));
            return true;
        }
    }

    private static final int AUTO_TERMINATE_GRACE_PERIOD_SECONDS = 60;
}
