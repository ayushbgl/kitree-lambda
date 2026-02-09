package in.co.kitree.handlers;

import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.PlatformFeeConfig;
import in.co.kitree.pojos.RequestBody;
import in.co.kitree.services.*;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.google.cloud.firestore.AggregateField.sum;

/**
 * Handler for expert-related operations.
 * Extracted from Handler.java as part of refactoring.
 */
public class ExpertHandler {

    private final Firestore db;
    private final Gson gson;

    public ExpertHandler(Firestore db) {
        this.db = db;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Routes expert-related function calls to the appropriate handler method.
     *
     * @param functionName The function name from the request
     * @param userId The authenticated user ID
     * @param requestBody The request body
     * @return The response string, or null if the function is not handled by this handler
     */
    public String handleRequest(String functionName, String userId, RequestBody requestBody) {
        try {
            switch (functionName) {
                case "mark_expert_busy":
                    return handleMarkExpertBusy(userId, requestBody.getOrderId());
                case "mark_expert_free":
                    return handleMarkExpertFree(userId, requestBody.getOrderId());
                case "expert_earnings_balance":
                    return handleExpertEarningsBalance(userId, requestBody);
                case "get_expert_booking_metrics":
                    return handleGetExpertBookingMetrics(userId, requestBody);
                case "record_expert_payout":
                    return handleRecordExpertPayout(userId, requestBody);
                case "set_expert_platform_fee":
                    return handleSetExpertPlatformFee(userId, requestBody);
                case "get_expert_platform_fee":
                    return handleGetExpertPlatformFee(userId, requestBody);
                case "expert_metrics":
                    return handleExpertMetrics(userId, requestBody);
                case "updateExpertImage":
                    return handleUpdateExpertImage(userId, requestBody);
                default:
                    return null; // Not handled by this handler
            }
        } catch (Exception e) {
            LoggingService.error("expert_handler_exception", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Check if a function name is handled by this handler.
     */
    public static boolean handles(String functionName) {
        return functionName != null && (
            functionName.equals("mark_expert_busy") ||
            functionName.equals("mark_expert_free") ||
            functionName.equals("expert_earnings_balance") ||
            functionName.equals("get_expert_booking_metrics") ||
            functionName.equals("record_expert_payout") ||
            functionName.equals("set_expert_platform_fee") ||
            functionName.equals("get_expert_platform_fee") ||
            functionName.equals("expert_metrics") ||
            functionName.equals("updateExpertImage")
        );
    }

    // ============= Helper Methods =============

    private boolean isAdmin(String userId) throws FirebaseAuthException {
        return Boolean.TRUE.equals(FirebaseAuth.getInstance().getUser(userId).getCustomClaims().get("admin"));
    }

    // ============= Expert Handler Methods =============

    /**
     * Get expert earnings balance (for experts viewing their earnings in expert mode).
     * This is separate from wallet balance - shows earnings from consultations.
     */
    private String handleExpertEarningsBalance(String expertId, RequestBody requestBody) throws ExecutionException, InterruptedException {
        ExpertEarningsService earningsService = new ExpertEarningsService(this.db);
        String currency = requestBody.getCurrency();

        if (currency != null && !currency.isEmpty()) {
            // Return specific currency balance
            Double balance = earningsService.getExpertEarningsBalance(expertId, currency);
            return gson.toJson(Map.of(
                "success", true,
                "balance", balance,
                "currency", currency
            ));
        } else {
            // Return all currency balances
            Map<String, Double> balances = earningsService.getExpertEarningsBalances(expertId);
            return gson.toJson(Map.of(
                "success", true,
                "balances", balances,
                "defaultCurrency", ExpertEarningsService.getDefaultCurrency()
            ));
        }
    }

    /**
     * Get aggregate booking metrics for an expert.
     * Returns total bookings, revenue, and earnings with breakdown by type.
     * Uses Firestore aggregation queries (count, sum) for efficiency.
     * Supports date range filtering for different time periods.
     */
    private String handleGetExpertBookingMetrics(String expertId, RequestBody requestBody) throws Exception {
        LoggingService.setFunction("get_expert_booking_metrics");
        LoggingService.setExpertId(expertId);
        LoggingService.info("get_expert_booking_metrics_started");

        if (expertId == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID required"));
        }

        // Parse date range from request (REQUIRED - no more "all time")
        Long startDateMillis = requestBody.getStartDate();
        Long endDateMillis = requestBody.getEndDate();
        String bookingType = requestBody.getBookingType(); // "all", "scheduled", "onDemand", "product"

        if (startDateMillis == null || endDateMillis == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Date range is required"));
        }

        com.google.cloud.Timestamp startTs = com.google.cloud.Timestamp.ofTimeMicroseconds(startDateMillis * 1000);
        com.google.cloud.Timestamp endTs = com.google.cloud.Timestamp.ofTimeMicroseconds(endDateMillis * 1000);

        LoggingService.info("metrics_date_range", Map.of(
            "startDate", startTs.toString(),
            "endDate", endTs.toString(),
            "bookingType", bookingType != null ? bookingType : "all"
        ));

        String currency = "INR"; // Default currency

        try {
            // Build base query with expert_id and date filters
            Query baseQuery = this.db.collectionGroup("orders")
                    .whereEqualTo("expert_id", expertId)
                    .whereGreaterThanOrEqualTo("created_at", startTs)
                    .whereLessThanOrEqualTo("created_at", endTs);

            // --- COUNT QUERIES (all orders regardless of status) ---

            // On-demand count
            long onDemandCount = 0;
            if (bookingType == null || bookingType.equals("all") || bookingType.equals("onDemand")) {
                Query onDemandQuery = baseQuery.whereEqualTo("type", "ON_DEMAND_CONSULTATION");
                AggregateQuerySnapshot onDemandSnapshot = onDemandQuery.count().get().get();
                onDemandCount = onDemandSnapshot.getCount();
            }

            // Product count
            long productCount = 0;
            if (bookingType == null || bookingType.equals("all") || bookingType.equals("product")) {
                Query productQuery = baseQuery.whereEqualTo("type", "PRODUCT");
                AggregateQuerySnapshot productSnapshot = productQuery.count().get().get();
                productCount = productSnapshot.getCount();
            }

            // Scheduled count (CONSULTATION type)
            long scheduledCount = 0;
            if (bookingType == null || bookingType.equals("all") || bookingType.equals("scheduled")) {
                Query scheduledQuery = baseQuery.whereEqualTo("type", "CONSULTATION");
                AggregateQuerySnapshot scheduledSnapshot = scheduledQuery.count().get().get();
                scheduledCount = scheduledSnapshot.getCount();
            }

            // Calculate total based on filter
            long totalBookings;
            if (bookingType == null || bookingType.equals("all")) {
                totalBookings = onDemandCount + productCount + scheduledCount;
            } else if (bookingType.equals("onDemand")) {
                totalBookings = onDemandCount;
            } else if (bookingType.equals("product")) {
                totalBookings = productCount;
            } else {
                totalBookings = scheduledCount;
            }

            // --- EARNINGS QUERIES (only COMPLETED orders) ---
            // NOTE: Revenue calculation removed from expert dashboard - experts only see their earnings
            // Revenue is not shown to experts; only totalEarnings is calculated and returned

            double totalEarnings = 0.0;

            // On-demand completed: sum expert_earnings only
            if (bookingType == null || bookingType.equals("all") || bookingType.equals("onDemand")) {
                Query onDemandCompletedQuery = baseQuery
                        .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                        .whereEqualTo("status", "COMPLETED");
                AggregateQuerySnapshot onDemandSums = onDemandCompletedQuery
                        .aggregate(sum("expert_earnings"))
                        .get().get();
                Double onDemandEarnings = onDemandSums.getDouble(sum("expert_earnings"));
                if (onDemandEarnings != null) totalEarnings += onDemandEarnings;
            }

            // Scheduled completed (CONSULTATION type): sum expert_earnings
            if (bookingType == null || bookingType.equals("all") || bookingType.equals("scheduled")) {
                Query scheduledCompletedQuery = baseQuery
                        .whereEqualTo("type", "CONSULTATION")
                        .whereEqualTo("status", "paid");
                AggregateQuerySnapshot scheduledSums = scheduledCompletedQuery
                        .aggregate(sum("amount"), sum("expert_earnings"))
                        .get().get();
                Double scheduledAmount = scheduledSums.getDouble(sum("amount"));
                Double scheduledEarnings = scheduledSums.getDouble(sum("expert_earnings"));
                // For scheduled, if expert_earnings not set, use amount (no platform fee on scheduled)
                if (scheduledEarnings != null) {
                    totalEarnings += scheduledEarnings;
                } else if (scheduledAmount != null) {
                    totalEarnings += scheduledAmount;
                }
            }

            // Product completed: sum expert_earnings (or amount if expert_earnings not set)
            if (bookingType == null || bookingType.equals("all") || bookingType.equals("product")) {
                Query productCompletedQuery = baseQuery
                        .whereEqualTo("type", "PRODUCT")
                        .whereEqualTo("status", "paid");
                AggregateQuerySnapshot productSums = productCompletedQuery
                        .aggregate(sum("amount"), sum("expert_earnings"))
                        .get().get();
                Double productAmount = productSums.getDouble(sum("amount"));
                Double productEarnings = productSums.getDouble(sum("expert_earnings"));
                if (productEarnings != null) {
                    totalEarnings += productEarnings;
                } else if (productAmount != null) {
                    totalEarnings += productAmount; // Fallback to amount if expert_earnings not set
                }
            }

            LoggingService.info("metrics_calculated", Map.of(
                "totalBookings", totalBookings,
                "totalEarnings", totalEarnings,
                "onDemandCount", onDemandCount,
                "scheduledCount", scheduledCount,
                "productCount", productCount
            ));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalBookings", totalBookings);
            // NOTE: Revenue removed from expert dashboard - experts only see earnings
            response.put("totalRevenue", 0); // Kept for backward compatibility but always 0
            response.put("totalEarnings", Math.round(totalEarnings * 100.0) / 100.0);
            response.put("currency", currency);
            response.put("scheduledCount", scheduledCount);
            response.put("onDemandCount", onDemandCount);
            response.put("productCount", productCount);

            return gson.toJson(response);

        } catch (Exception e) {
            LoggingService.error("get_expert_booking_metrics_error", e);
            return gson.toJson(Map.of(
                "success", false,
                "errorMessage", "Failed to calculate metrics: " + e.getMessage()
            ));
        }
    }

    /**
     * Admin endpoint to record a payout to an expert.
     * Deducts from expert_earnings_balances and creates a record in payouts subcollection.
     */
    private String handleRecordExpertPayout(String adminUserId, RequestBody requestBody) {
        LoggingService.setFunction("record_expert_payout");
        LoggingService.info("record_expert_payout_started");

        try {
            // Verify admin access against the original caller
            String callerUserId = requestBody.getCallerUserId() != null ? requestBody.getCallerUserId() : adminUserId;
            if (!isAdmin(callerUserId)) {
                LoggingService.warn("record_expert_payout_unauthorized", Map.of("userId", callerUserId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Admin access required"));
            }

            // Get required parameters
            String expertId = requestBody.getExpertId();
            Double amount = requestBody.getAmount();
            String currency = requestBody.getCurrency();
            String method = requestBody.getPayoutMethod();
            String reference = requestBody.getPayoutReference();
            String notes = requestBody.getNotes();

            // Validate required fields
            if (expertId == null || expertId.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }
            if (amount == null || amount <= 0) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Valid amount is required"));
            }
            if (method == null || method.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Payout method is required"));
            }
            if (reference == null || reference.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Payout reference is required"));
            }

            // Default currency
            if (currency == null || currency.isEmpty()) {
                currency = "INR";
            }

            LoggingService.setExpertId(expertId);
            LoggingService.info("record_expert_payout_processing", Map.of(
                "amount", amount,
                "currency", currency,
                "method", method
            ));

            // Record the payout
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            String payoutId = earningsService.recordPayout(expertId, currency, amount, method, reference, notes);

            // Get updated balance
            Double newBalance = earningsService.getExpertEarningsBalance(expertId, currency);

            LoggingService.info("record_expert_payout_success", Map.of(
                "payoutId", payoutId,
                "newBalance", newBalance
            ));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payoutId", payoutId);
            response.put("newBalance", newBalance);
            response.put("currency", currency);

            return gson.toJson(response);

        } catch (IllegalArgumentException e) {
            LoggingService.warn("record_expert_payout_validation_error", Map.of("error", e.getMessage()));
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        } catch (Exception e) {
            LoggingService.error("record_expert_payout_error", e);
            return gson.toJson(Map.of(
                "success", false,
                "errorMessage", "Failed to record payout: " + e.getMessage()
            ));
        }
    }

    /**
     * Admin endpoint to set platform fee configuration for an expert.
     * Stores in users/{expertId}/private/platform_fee_config document.
     * Only admins can call this endpoint.
     */
    private String handleSetExpertPlatformFee(String adminUserId, RequestBody requestBody) {
        LoggingService.setFunction("set_expert_platform_fee");
        LoggingService.info("set_expert_platform_fee_started");

        try {
            // Verify admin access - CRITICAL security check against original caller
            String callerUserId = requestBody.getCallerUserId() != null ? requestBody.getCallerUserId() : adminUserId;
            if (!isAdmin(callerUserId)) {
                LoggingService.warn("set_expert_platform_fee_unauthorized", Map.of("userId", callerUserId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Admin access required"));
            }

            // Get required parameters
            String expertId = requestBody.getExpertId();
            Double defaultFeePercent = requestBody.getDefaultFeePercent();
            Map<String, Double> feeByType = requestBody.getFeeByType();
            Map<String, Double> feeByCategory = requestBody.getFeeByCategory();

            // Validate expert ID
            if (expertId == null || expertId.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }

            // At least one fee configuration must be provided
            if (defaultFeePercent == null && (feeByType == null || feeByType.isEmpty()) && (feeByCategory == null || feeByCategory.isEmpty())) {
                return gson.toJson(Map.of("success", false, "errorMessage", "At least one fee configuration is required"));
            }

            // Validate fee percentages (should be between 0 and 100)
            if (defaultFeePercent != null && (defaultFeePercent < 0 || defaultFeePercent > 100)) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Default fee percent must be between 0 and 100"));
            }

            LoggingService.setExpertId(expertId);
            LoggingService.info("set_expert_platform_fee_processing", Map.of(
                "defaultFeePercent", defaultFeePercent != null ? defaultFeePercent : "not set",
                "feeByTypeCount", feeByType != null ? feeByType.size() : 0,
                "feeByCategoryCount", feeByCategory != null ? feeByCategory.size() : 0
            ));

            // Prepare the document data
            Map<String, Object> feeConfigData = new HashMap<>();
            if (defaultFeePercent != null) {
                feeConfigData.put("default_fee_percent", defaultFeePercent);
            }
            if (feeByType != null && !feeByType.isEmpty()) {
                feeConfigData.put("fee_by_type", feeByType);
            }
            if (feeByCategory != null && !feeByCategory.isEmpty()) {
                feeConfigData.put("fee_by_category", feeByCategory);
            }
            feeConfigData.put("updated_at", com.google.cloud.Timestamp.now());
            feeConfigData.put("updated_by", adminUserId);

            // Write to the private collection: users/{expertId}/private/platform_fee_config
            DocumentReference feeConfigRef = db.collection("users").document(expertId)
                    .collection("private").document("platform_fee_config");
            feeConfigRef.set(feeConfigData, SetOptions.merge()).get();

            LoggingService.info("set_expert_platform_fee_success", Map.of("expertId", expertId));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("expertId", expertId);
            response.put("message", "Platform fee configuration updated successfully");

            return gson.toJson(response);

        } catch (Exception e) {
            LoggingService.error("set_expert_platform_fee_error", e);
            return gson.toJson(Map.of(
                "success", false,
                "errorMessage", "Failed to set platform fee: " + e.getMessage()
            ));
        }
    }

    /**
     * Admin endpoint to get platform fee configuration for an expert.
     * Reads from users/{expertId}/private/platform_fee_config document.
     * Only admins can call this endpoint.
     */
    private String handleGetExpertPlatformFee(String adminUserId, RequestBody requestBody) {
        LoggingService.setFunction("get_expert_platform_fee");
        LoggingService.info("get_expert_platform_fee_started");

        try {
            // Verify admin access - CRITICAL security check against original caller
            String callerUserId = requestBody.getCallerUserId() != null ? requestBody.getCallerUserId() : adminUserId;
            if (!isAdmin(callerUserId)) {
                LoggingService.warn("get_expert_platform_fee_unauthorized", Map.of("userId", callerUserId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Admin access required"));
            }

            String expertId = requestBody.getExpertId();

            // Validate expert ID
            if (expertId == null || expertId.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }

            LoggingService.setExpertId(expertId);

            // Get the platform fee config using WalletService
            WalletService walletService = new WalletService(db);
            PlatformFeeConfig feeConfig = walletService.getPlatformFeeConfig(expertId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("expertId", expertId);
            response.put("defaultFeePercent", feeConfig.getDefaultFeePercent());
            response.put("feeByType", feeConfig.getFeeByType());
            response.put("feeByCategory", feeConfig.getFeeByCategory());

            LoggingService.info("get_expert_platform_fee_success", Map.of("expertId", expertId));

            return gson.toJson(response);

        } catch (Exception e) {
            LoggingService.error("get_expert_platform_fee_error", e);
            return gson.toJson(Map.of(
                "success", false,
                "errorMessage", "Failed to get platform fee: " + e.getMessage()
            ));
        }
    }

    /**
     * Mark expert as BUSY when they join a scheduled call.
     * This is called from the frontend when an expert joins a scheduled consultation.
     */
    private String handleMarkExpertBusy(String expertId, String orderId) {
        LoggingService.setFunction("mark_expert_busy");
        LoggingService.setContext(null, orderId, expertId);
        LoggingService.info("marking_expert_busy_started");

        try {
            if (expertId == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }

            // Validate the order exists and belongs to this expert (optional but recommended)
            if (orderId != null) {
                OnDemandConsultationService consultationService = new OnDemandConsultationService(db);
                // Try to find the order to verify expert ownership
                // Note: This would require a method to get order by ID across collections
                // For now, we trust the frontend to send correct data
            }

            WalletService walletService = new WalletService(db);

            // Set expert status to BUSY
            walletService.setConsultationStatus(expertId, "BUSY");

            LoggingService.info("expert_marked_busy_successfully");
            return gson.toJson(Map.of("success", true, "status", "BUSY"));

        } catch (Exception e) {
            LoggingService.error("mark_expert_busy_failed", e);
            return gson.toJson(Map.of("success", false, "errorMessage", "Failed to update expert status: " + e.getMessage()));
        }
    }

    /**
     * Mark expert as FREE when they end a scheduled call.
     * Checks for other active consultations before freeing the expert.
     */
    private String handleMarkExpertFree(String expertId, String orderId) {
        LoggingService.setFunction("mark_expert_free");
        LoggingService.setContext(null, orderId, expertId);
        LoggingService.info("marking_expert_free_started");

        try {
            if (expertId == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }

            WalletService walletService = new WalletService(db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(db);

            // Check if expert has other active consultations
            boolean hasOtherActive = false;
            if (orderId != null) {
                hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
            }

            if (hasOtherActive) {
                LoggingService.info("expert_keeping_busy_other_consultations", Map.of(
                    "reason", "other_active_consultations"
                ));
                return gson.toJson(Map.of("success", true, "status", "BUSY", "reason", "other_active_consultations"));
            }

            // Set expert status to FREE
            walletService.setConsultationStatus(expertId, "FREE");

            LoggingService.info("expert_marked_free_successfully");
            return gson.toJson(Map.of("success", true, "status", "FREE"));

        } catch (Exception e) {
            LoggingService.error("mark_expert_free_failed", e);
            return gson.toJson(Map.of("success", false, "errorMessage", "Failed to update expert status: " + e.getMessage()));
        }
    }

    /**
     * Expert metrics: aggregated order and subscription earnings.
     * Requires admin or matching expertId.
     */
    private String handleExpertMetrics(String userId, RequestBody requestBody) throws Exception {
        Map<String, Object> response = new HashMap<>();
        String expertId = requestBody.getExpertId();
        String callerUserId = requestBody.getCallerUserId() != null ? requestBody.getCallerUserId() : userId;
        if (isAdmin(callerUserId) || userId.equals(expertId)) {
            Query ordersQuery = db.collectionGroup("orders");
            Query subscriptionsQuery = db.collectionGroup("subscription_payments");

            if (expertId != null) {
                ordersQuery = ordersQuery.whereEqualTo("expertId", expertId);
                subscriptionsQuery = subscriptionsQuery.whereEqualTo("expertId", expertId);
            }
            if (requestBody.getCategory() != null && !("all").equals(requestBody.getCategory())) {
                ordersQuery = ordersQuery.whereEqualTo("category", requestBody.getCategory());
                subscriptionsQuery = subscriptionsQuery.whereEqualTo("category", requestBody.getCategory());
            }
            if (requestBody.getType() != null && !("all").equals(requestBody.getType())) {
                ordersQuery = ordersQuery.whereEqualTo("type", requestBody.getType());
            }
            if (requestBody.getDateRangeFilter() != null && !requestBody.getDateRangeFilter().isEmpty()) {
                ordersQuery = ordersQuery.orderBy("created_at", Query.Direction.DESCENDING);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

                Date startDate = dateFormat.parse(requestBody.getDateRangeFilter().get(0));
                Timestamp startTimestamp = new Timestamp(startDate.getTime());

                Date endDate = dateFormat.parse(requestBody.getDateRangeFilter().get(1));
                Timestamp endTimestamp = new Timestamp(endDate.getTime());

                ordersQuery = ordersQuery.whereGreaterThanOrEqualTo("created_at", startTimestamp);
                ordersQuery = ordersQuery.whereLessThan("created_at", endTimestamp);

                subscriptionsQuery = subscriptionsQuery.whereGreaterThanOrEqualTo("paymentReceivedAt", startTimestamp);
                subscriptionsQuery = subscriptionsQuery.whereLessThan("paymentReceivedAt", endTimestamp);
            }

            ordersQuery = ordersQuery.whereEqualTo("subscription", false).orderBy("paymentReceivedAt", Query.Direction.ASCENDING);

            AggregateQuerySnapshot ordersSnapshot = ordersQuery.aggregate(sum("amount")).get().get();
            AggregateQuerySnapshot subscriptionsSnapshot = subscriptionsQuery.aggregate(sum("amount")).get().get();

            Object orderEarnings = Objects.requireNonNull(ordersSnapshot.get(sum("amount")));
            double orderEarningsDouble = 0.0;
            if (orderEarnings instanceof Double) {
                orderEarningsDouble = (Double) orderEarnings;
            } else if (orderEarnings instanceof Long) {
                orderEarningsDouble = ((Long) orderEarnings).doubleValue();
            }

            Object subscriptionEarnings = Objects.requireNonNull(subscriptionsSnapshot.get(sum("amount")));
            double subscriptionEarningsDouble = 0.0;
            if (subscriptionEarnings instanceof Double) {
                subscriptionEarningsDouble = (Double) subscriptionEarnings;
            } else if (subscriptionEarnings instanceof Long) {
                subscriptionEarningsDouble = ((Long) subscriptionEarnings).doubleValue();
            }

            response.put("totalEarnings", orderEarningsDouble + subscriptionEarningsDouble);
        } else {
            return "Not authorized";
        }
        return gson.toJson(response);
    }

    /**
     * Update expert display picture via Cloudinary upload.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String handleUpdateExpertImage(String userId, RequestBody requestBody) throws Exception {
        String base64Image = requestBody.getBase64Image();
        if (base64Image == null || base64Image.isBlank()) {
            return "Image not found";
        }
        String cloudinaryUrl = loadCloudinaryUrl();
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            return "Cloudinary not configured";
        }
        boolean isTest = "test".equals(System.getenv("ENVIRONMENT"));
        Cloudinary cloudinary = new Cloudinary(cloudinaryUrl);
        cloudinary.config.secure = true;
        String path = isTest ? "test/" : "";
        Map uploadResult = cloudinary.uploader().upload(base64Image, ObjectUtils.asMap(
            "public_id", path + "expertDisplayPictures/" + userId,
            "unique_filename", false,
            "overwrite", true
        ));
        String url = String.valueOf(uploadResult.get("secure_url"));
        LoggingService.info("expert_image_updated", Map.of("userId", userId, "url", url));
        db.collection("users").document(userId).collection("public").document("store")
            .set(Map.of("displayPicture", url), SetOptions.merge()).get();
        return "Done";
    }

    private String loadCloudinaryUrl() {
        try (FileInputStream fis = new FileInputStream("secrets.json")) {
            String content = new String(fis.readAllBytes());
            return new JSONObject(content).getString("CLOUDINARY_URL");
        } catch (Exception e) {
            LoggingService.error("handler_cloudinary_secrets_read_failed", e);
            return null;
        }
    }

}
