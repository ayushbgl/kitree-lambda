package in.co.kitree.handlers;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.razorpay.RazorpayException;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;
import in.co.kitree.services.Razorpay.ErrorCode;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Handler for service purchase, payment verification, scheduling, and subscription operations.
 * Extracted from Handler.java as part of refactoring.
 */
public class ServiceHandler {

    private final Firestore db;
    private final Razorpay razorpay;
    private final StripeService stripeService;
    private final ServicePlanService servicePlanService;
    private final Gson gson;

    public ServiceHandler(Firestore db, Razorpay razorpay, StripeService stripeService) {
        this.db = db;
        this.razorpay = razorpay;
        this.stripeService = stripeService;
        this.servicePlanService = new ServicePlanService(db);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public String handleRequest(String action, String userId, RequestBody requestBody) throws Exception {
        return switch (action) {
            case "buy_service" -> handleBuyService(userId, requestBody);
            case "verify_payment" -> handleVerifyPayment(userId, requestBody);
            case "confirm_appointment" -> handleConfirmAppointment(userId, requestBody);
            case "apply_coupon" -> handleApplyCoupon(userId, requestBody);
            case "get_expert_availability" -> handleGetExpertAvailability(userId, requestBody);
            default -> gson.toJson(Map.of("success", false, "errorMessage", "Unknown action: " + action));
        };
    }

    // ============= Service Handler Methods =============

    private String handleBuyService(String userId, RequestBody requestBody) throws Exception {
        Map<String, Object> orderDetails = new HashMap<>();
        if (requestBody.getExpertId() == null) {
            orderDetails.put("success", false);
            orderDetails.put("errorMessage", "Expert Id is required");
            return gson.toJson(orderDetails);
        }

        if (requestBody.getPlanId() == null) {
            orderDetails.put("success", false);
            orderDetails.put("errorMessage", "Plan Id is required");
            return gson.toJson(orderDetails);
        }

        ServicePlan servicePlan = servicePlanService.getPlanDetails(requestBody.getPlanId(), requestBody.getExpertId());

        if (servicePlan.getCategory().equals("CUSTOMIZED_BRACELET")) {
            if (requestBody.getAmount() == null || requestBody.getAmount() < 250 || requestBody.getBeads() == null || requestBody.getBeads().isEmpty() || requestBody.getAddress() == null) {
                orderDetails.put("success", false);
                return gson.toJson(orderDetails);
            }
            servicePlan.setAmount(requestBody.getAmount());
        }

        FirebaseUser user = UserService.getUserDetails(this.db, userId);
        FirebaseUser expert = UserService.getUserDetails(this.db, requestBody.getExpertId());
        orderDetails.put("created_at", new Timestamp(System.currentTimeMillis()));
        orderDetails.put("user_name", user.getName());
        orderDetails.put("user_id", userId);
        orderDetails.put("user_phone_number", user.getPhoneNumber());
        orderDetails.put("amount", servicePlan.getAmount());
        orderDetails.put("currency", servicePlan.getCurrency());
        orderDetails.put("plan_id", requestBody.getPlanId());
        orderDetails.put("type", servicePlan.getType());
        orderDetails.put("expert_id", requestBody.getExpertId());
        orderDetails.put("expert_name", expert.getName());
        if (servicePlan.getType().equals("DIGITAL_PRODUCT")) {
            orderDetails.put("subtype", servicePlan.getSubtype());
        }
        if (servicePlan.getCategory().equals("CUSTOMIZED_BRACELET") && requestBody.getBeads() != null && !requestBody.getBeads().isEmpty() && requestBody.getAddress() != null) {
            orderDetails.put("beads", requestBody.getBeads());
            orderDetails.put("address", requestBody.getAddress());
            orderDetails.put("currency", "INR");
        }

        if (!servicePlan.getType().equals("WEBINAR")) {
            orderDetails.put("is_video", servicePlan.isVideo());
            orderDetails.put("category", servicePlan.getCategory());
        } else {
            orderDetails.put("date", servicePlan.getDate());
            orderDetails.put("title", servicePlan.getTitle());
            if (servicePlan.getSessionStartedAt() != null) {
                orderDetails.put("session_started_at", servicePlan.getSessionStartedAt());
            }
            if (servicePlan.getSessionCompletedAt() != null) {
                orderDetails.put("session_completed_at", servicePlan.getSessionCompletedAt());
            }
        }

        if (servicePlan.getAmount() == null || servicePlan.getAmount() <= 0) {
            String orderId = UUID.randomUUID().toString();
            orderDetails.put("order_id", orderId);
            createOrderInDB(userId, orderId, orderDetails);
            verifyOrderInDB(userId, orderId);
            return gson.toJson(orderDetails);
        }

        if (requestBody.getCouponCode() != null && !requestBody.getCouponCode().isBlank()) {
            String language = requestBody.getLanguage() == null ? "en" : requestBody.getLanguage();
            CouponResult couponResult = applyCoupon(requestBody.getCouponCode(), requestBody.getExpertId(), requestBody.getPlanId(), userId, language);
            if (couponResult.isValid()) {
                orderDetails.put("coupon_code", requestBody.getCouponCode());
                orderDetails.put("original_amount", servicePlan.getAmount());
                orderDetails.put("amount", couponResult.getNewAmount());
                orderDetails.put("discount", couponResult.getDiscount());
                servicePlan.setAmount(couponResult.getNewAmount());
            }
        }

        if (user.getReferredBy() != null) {
            String referredBy = user.getReferredBy().get(requestBody.getExpertId());
            if (referredBy != null && !referredBy.isBlank() && !referredBy.equals(userId) && !referredBy.equals(requestBody.getExpertId())) {
                DocumentSnapshot referralDocument = this.db.collection("users").document(userId).collection("private").document("referrals").get().get();
                Map<String, String> referrals;
                try {
                    referrals = (Map<String, String>) Objects.requireNonNull(referralDocument.getData()).getOrDefault(requestBody.getExpertId(), new HashMap<>());
                } catch (Exception e) {
                    referrals = new HashMap<>();
                }
                if (referrals == null || referrals.isEmpty()) {
                    DocumentSnapshot referralDetails = this.db.collection("users").document(requestBody.getExpertId()).collection("public").document("store").get().get();
                    int referredDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referredDiscount", 0)));
                    int referrerDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referrerDiscount", 0)));
                    orderDetails.put("referred_by", referredBy);
                    orderDetails.put("referrer_discount", referrerDiscount);
                    orderDetails.put("referred_discount", referredDiscount);
                    if (orderDetails.get("coupon_code") == null && referredDiscount > 0 && referredDiscount <= 100) {
                        orderDetails.put("original_amount", servicePlan.getAmount());
                        Double newAmount = servicePlan.getAmount() * (1.00 - referredDiscount / 100.0);
                        orderDetails.put("amount", newAmount);
                        orderDetails.put("discount", servicePlan.getAmount() - (Double) orderDetails.get("amount"));
                        servicePlan.setAmount(newAmount);
                    }
                }
            }
        }

        // Wallet Payment Logic
        Double finalAmount = servicePlan.getAmount();
        String currency = servicePlan.getCurrency();
        Double walletDeduction = 0.0;
        Double gatewayAmount = finalAmount;
        String paymentMethod = "GATEWAY";

        if (Boolean.TRUE.equals(requestBody.getUseWalletBalance()) && finalAmount > 0) {
            WalletService walletService = new WalletService(this.db);
            Double walletBalance = walletService.getExpertWalletBalance(userId, requestBody.getExpertId(), currency);

            if (walletBalance > 0) {
                walletDeduction = Math.min(walletBalance, finalAmount);
                gatewayAmount = finalAmount - walletDeduction;
                walletDeduction = Math.round(walletDeduction * 100.0) / 100.0;
                gatewayAmount = Math.round(gatewayAmount * 100.0) / 100.0;

                if (gatewayAmount <= 0) {
                    paymentMethod = "WALLET_ONLY";
                } else {
                    paymentMethod = "WALLET_AND_GATEWAY";
                }
            }
        }

        orderDetails.put("wallet_deduction", walletDeduction);
        orderDetails.put("gateway_amount", gatewayAmount);
        orderDetails.put("payment_method", paymentMethod);

        if ("WALLET_ONLY".equals(paymentMethod)) {
            String orderId = UUID.randomUUID().toString();
            final Double finalWalletDeduction = walletDeduction;
            final String finalOrderId = orderId;
            final String finalCurrency = currency;
            final String finalExpertId = requestBody.getExpertId();

            WalletService walletService = new WalletService(this.db);
            final String[] walletTransactionIdHolder = new String[1];

            this.db.runTransaction(transaction -> {
                DocumentReference walletRef = db.collection("users").document(userId)
                        .collection("expert_wallets").document(finalExpertId);
                DocumentSnapshot walletDoc = transaction.get(walletRef).get();

                Map<String, Double> balances = new HashMap<>();
                if (walletDoc.exists() && walletDoc.contains("balances")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingBalances = (Map<String, Object>) walletDoc.get("balances");
                    if (existingBalances != null) {
                        for (Map.Entry<String, Object> entry : existingBalances.entrySet()) {
                            if (entry.getValue() instanceof Number) {
                                balances.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                            }
                        }
                    }
                }
                Double currentBalance = balances.getOrDefault(finalCurrency, 0.0);

                if (currentBalance < finalWalletDeduction) {
                    throw new IllegalStateException("Insufficient wallet balance");
                }

                walletService.updateExpertWalletBalanceInTransactionWithSnapshot(
                        transaction, walletRef, walletDoc, finalCurrency, -finalWalletDeduction
                );

                WalletTransaction walletTx = new WalletTransaction();
                walletTx.setType("ORDER_PAYMENT");
                walletTx.setSource("WALLET");
                walletTx.setAmount(-finalWalletDeduction);
                walletTx.setCurrency(finalCurrency);
                walletTx.setOrderId(finalOrderId);
                walletTx.setStatus("COMPLETED");
                walletTx.setCreatedAt(com.google.cloud.Timestamp.now());
                walletTx.setCategory(servicePlan.getCategory());

                String txId = walletService.createExpertWalletTransactionInTransaction(
                        transaction, userId, finalExpertId, walletTx
                );
                walletTransactionIdHolder[0] = txId;

                orderDetails.put("order_id", finalOrderId);
                orderDetails.put("wallet_transaction_id", txId);
                orderDetails.put("payment_received_at", com.google.cloud.Timestamp.now());

                DocumentReference orderRef = db.collection("users").document(userId)
                        .collection("orders").document(finalOrderId);
                transaction.set(orderRef, orderDetails);

                return null;
            }).get();

            incrementCouponUsageCount(userId, orderId);
            rewardReferrer(userId, orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("walletDeduction", walletDeduction);
            response.put("walletTransactionId", walletTransactionIdHolder[0]);
            response.put("gatewayAmount", 0.0);
            response.put("totalAmount", finalAmount);
            response.put("paymentMethod", "WALLET_ONLY");
            response.put("success", true);
            return gson.toJson(response);
        }

        if (walletDeduction > 0) {
            orderDetails.put("pending_wallet_deduction", walletDeduction);
        }

        Map<String, Object> response = new HashMap<>();
        String encryptedCustomerId = CustomerCipher.encryptCaesarCipher(userId);
        PaymentGateway gateway = PaymentGatewayRouter.getGateway(currency, razorpay, stripeService);

        // Razorpay subscriptions (Razorpay-only feature)
        if (servicePlan.isSubscription() && gateway instanceof Razorpay rpGateway) {
            String gatewaySubscriptionId = rpGateway.createSubscription(servicePlan.getRazorpayId(), encryptedCustomerId);
            String orderId = UUID.randomUUID().toString();
            orderDetails.put("order_id", orderId);
            orderDetails.put("gateway_subscription_id", gatewaySubscriptionId);
            orderDetails.put("subscription", true);
            orderDetails.put("gateway_type", gateway.getGatewayType());
            createOrderInDB(userId, orderId, orderDetails);
            response.put("orderId", orderId);
            response.put("gatewaySubscriptionId", gatewaySubscriptionId);
            response.put("gatewayType", gateway.getGatewayType());
            response.put("gatewayKey", gateway.getPublishableKey());
        } else {
            // One-time payment via common interface
            PaymentOrderResult orderResult = gateway.createOrder(gatewayAmount, currency, encryptedCustomerId);
            String orderId = UUID.randomUUID().toString();
            orderDetails.put("order_id", orderId);
            orderDetails.put("gateway_order_id", orderResult.getGatewayOrderId());
            orderDetails.put("subscription", false);
            orderDetails.put("gateway_type", gateway.getGatewayType());
            createOrderInDB(userId, orderId, orderDetails);
            response.put("orderId", orderId);
            response.put("gatewayOrderId", orderResult.getGatewayOrderId());
            response.put("gatewayType", gateway.getGatewayType());
            response.put("gatewayKey", gateway.getPublishableKey());
            if (orderResult.getClientSecret() != null) {
                response.put("clientSecret", orderResult.getClientSecret());
            }
            response.put("walletDeduction", walletDeduction);
            response.put("gatewayAmount", gatewayAmount);
            response.put("totalAmount", finalAmount);
            response.put("paymentMethod", paymentMethod);
        }

        return gson.toJson(response);
    }

    private String handleBuyGift(String userId, RequestBody requestBody) throws Exception {
        PaymentOrderResult giftResult = razorpay.createOrder(Double.valueOf(requestBody.getGiftAmount()), "INR", CustomerCipher.encryptCaesarCipher(userId));
        Map<String, String> response = new HashMap<>();
        response.put("gift_order_id", giftResult.getGatewayOrderId());

        Map<String, Object> giftDetails = new HashMap<>();
        giftDetails.put("gift_order_id", giftResult.getGatewayOrderId());
        giftDetails.put("amount", requestBody.getGiftAmount());
        DocumentReference doc = this.db.collection("users").document(userId).collection("orders").document(requestBody.getOrderId());
        doc.update("gifts", FieldValue.arrayUnion(giftDetails)).get();
        return gson.toJson(response);
    }

    private String handleApplyCoupon(String userId, RequestBody requestBody) throws ExecutionException, InterruptedException {
        String language = requestBody.getLanguage() == null ? "en" : requestBody.getLanguage();
        return gson.toJson(applyCoupon(requestBody.getCouponCode(), requestBody.getExpertId(), requestBody.getPlanName(), userId, language));
    }

    private String handleCheckReferralBonus(String userId, RequestBody requestBody) throws Exception {
        Map<String, Object> response = new HashMap<>();
        FirebaseUser user = UserService.getUserDetails(this.db, userId);
        if (user.getReferredBy() != null) {
            String referredBy = user.getReferredBy().get(requestBody.getExpertId());
            if (referredBy != null && !referredBy.isBlank() && !referredBy.equals(userId) && !referredBy.equals(requestBody.getExpertId())) {
                DocumentSnapshot referralDocument = this.db.collection("users").document(userId).collection("private").document("referrals").get().get();
                Map<String, String> referrals;
                try {
                    referrals = (Map<String, String>) Objects.requireNonNull(referralDocument.getData()).getOrDefault(requestBody.getExpertId(), new HashMap<>());
                } catch (Exception e) {
                    referrals = new HashMap<>();
                }
                if (referrals == null || referrals.isEmpty()) {
                    ServicePlan servicePlan = servicePlanService.getPlanDetails(requestBody.getPlanId(), requestBody.getExpertId());
                    DocumentSnapshot referralDetails = this.db.collection("users").document(requestBody.getExpertId()).collection("public").document("store").get().get();
                    int referredDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referredDiscount", 0)));
                    Double newAmount = servicePlan.getAmount() * (1.00 - referredDiscount / 100.0);
                    newAmount = Math.round(newAmount * 100.0) / 100.0;
                    Double discount = Math.round((servicePlan.getAmount() - newAmount) * 100.0) / 100.0;
                    response.put("newAmount", newAmount);
                    response.put("discount", discount);
                    response.put("valid", true);
                    return gson.toJson(response);
                }
            }
        }
        response.put("valid", false);
        return gson.toJson(response);
    }

    private String handleVerifyPayment(String userId, RequestBody requestBody) throws Exception {
        String gatewaySubscriptionId = requestBody.getGatewaySubscriptionId();
        String gatewayOrderId = requestBody.getGatewayOrderId();
        String gatewayPaymentId = requestBody.getGatewayPaymentId();
        String gatewaySignature = requestBody.getGatewaySignature();
        String verificationType = requestBody.getType();

        // Razorpay subscription verification (subscriptions are Razorpay-only)
        if (gatewaySubscriptionId != null) {
            if (razorpay.verifySubscription(gatewaySubscriptionId)) {
                String firestoreSubscriptionOrderId = requestBody.getOrderId() != null ? requestBody.getOrderId() : gatewaySubscriptionId;
                incrementCouponUsageCount(userId, firestoreSubscriptionOrderId);
                rewardReferrer(userId, firestoreSubscriptionOrderId);
                return "Verified";
            }
            return gson.toJson(Map.of("success", false, "errorMessage", "Subscription verification failed"));
        }

        // Determine gateway from order document
        PaymentGateway gateway = razorpay; // default
        if (requestBody.getOrderId() != null && !requestBody.getOrderId().isEmpty()) {
            DocumentSnapshot orderDoc = this.db.collection("users").document(userId)
                    .collection("orders").document(requestBody.getOrderId()).get().get();
            if (orderDoc.exists()) {
                String orderGatewayType = orderDoc.getString("gateway_type");
                gateway = PaymentGatewayRouter.STRIPE.equals(orderGatewayType) ? stripeService : razorpay;
            }
        }

        // Verify via the common interface
        if (gatewayOrderId == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Gateway order ID is required"));
        }
        if (!gateway.verifyPayment(gatewayOrderId, gatewayPaymentId, gatewaySignature)) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Payment verification failed"));
        }

        if ("WALLET_RECHARGE".equals(verificationType)) {
            String expertId = requestBody.getExpertId();
            if (expertId == null || expertId.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required for wallet recharge"));
            }

            WalletService walletService = new WalletService(this.db);

            if (walletService.isRechargeOrderAlreadyCompleted(userId, expertId, gatewayOrderId)) {
                String currency = WalletService.getDefaultCurrency();
                Double currentBalance = walletService.getExpertWalletBalance(userId, expertId, currency);
                return gson.toJson(Map.of(
                    "success", true,
                    "newBalance", currentBalance,
                    "currency", currency,
                    "expertId", expertId,
                    "message", "Payment already processed"
                ));
            }

            Map<String, Object> pendingTx = walletService.findPendingRechargeByOrderId(userId, expertId, gatewayOrderId);
            if (pendingTx == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Recharge order not found or already processed"));
            }

            String transactionId = (String) pendingTx.get("_id");
            Double amount = pendingTx.get("amount") instanceof Number ? ((Number) pendingTx.get("amount")).doubleValue() : null;
            Double bonus = pendingTx.get("bonus_amount") instanceof Number ? ((Number) pendingTx.get("bonus_amount")).doubleValue() : 0.0;
            String currency = (String) pendingTx.get("currency");

            if (amount == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Invalid transaction: amount is missing"));
            }
            if (currency == null) {
                currency = WalletService.getDefaultCurrency();
            }

            Double newBalance = walletService.completeRechargeTransaction(
                userId, expertId, transactionId, gatewayPaymentId, amount, bonus, currency
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("newBalance", newBalance);
            response.put("currency", currency);
            response.put("amountPaid", amount);
            response.put("expertId", expertId);
            if (bonus > 0) {
                response.put("bonusReceived", bonus);
            }
            return gson.toJson(response);
        }

        // Default: ORDER PAYMENT
        String firestoreOrderId = requestBody.getOrderId();
        if (firestoreOrderId == null || firestoreOrderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
        }

        // Process pending wallet deduction for partial wallet payments
        WalletHandler walletHandler = new WalletHandler(db, razorpay, stripeService);
        walletHandler.processWalletDeductionOnPaymentVerification(userId, firestoreOrderId);

        verifyOrderInDB(userId, firestoreOrderId);
        NotificationService.sendNotification("f0JuQoCUQQ68I-tHqlkMxm:APA91bHZqzyL-xZG_g4qXhZyT9SP8jSh5hRJ8_21Ux9YPvcqzC7wi_tC9eKD6uZi52BndchctrXsINOmoo8A4OTn79oZkiwMeXPmcauVbgIXNEk_Qh7xFQc");
        NotificationService.sendNotification("fdljkc67TkI6iH-obDwVHR:APA91bGrUdmUGsI-SudlhQnrGasRTgiosL46ISzeudbcoXrzpNgz1Uu0y0c0WMZHtCt0ct5UwWN9kVFx3TJRuhTuxXjuay-6otAFO1uBaJNo8nz1VOAobbc");
        NotificationService.sendNotification("eX4C_MX0Q8e2yzwheVUx7a:APA91bFWnho4d3Mbx8EpAgJMGHzOdNzCb3O2fl3DdC1Rx92cMYZDSKIRFx2A-pR20BFUiXGL3qZMu64uGNJYzxAjOx9KG7cr-D8EIvGsOGiKI3EPWFJrU2c");

        incrementCouponUsageCount(userId, firestoreOrderId);
        rewardReferrer(userId, firestoreOrderId);
        return "Verified";
    }

    private String handleCancelSubscription(String userId, RequestBody requestBody) throws Exception {
        try {
            String gatewaySubscriptionId = requestBody.getGatewaySubscriptionId();
            if (!(checkIfOrderOwnedByUser(gatewaySubscriptionId, userId) || AuthenticationService.isAdmin(userId))) {
                return "Not authorized";
            }
            razorpay.cancel(gatewaySubscriptionId);
            cancelSubscriptionInDb(userId, gatewaySubscriptionId);
        } catch (RazorpayException e) {
            throw new RuntimeException(ErrorCode.CANCELLATION_FAILED_SERVER_ERROR.toString(), e);
        }
        return null;
    }

    private String handleConfirmAppointment(String userId, RequestBody requestBody) throws Exception {
        String userIdFromRequest = requestBody.getUserId();
        String orderId = requestBody.getOrderId();
        String appointmentDate = requestBody.getAppointmentDate();
        Map<String, String> appointmentSlot = requestBody.getAppointmentSlot();
        String userTimeZone = requestBody.getUserTimeZone();

        if (userIdFromRequest == null || userIdFromRequest.isEmpty() || orderId == null || orderId.isEmpty() ||
            appointmentDate == null || appointmentDate.isEmpty() || appointmentSlot == null ||
            appointmentSlot.get("startTime") == null || appointmentSlot.get("startTime").isEmpty() ||
            appointmentSlot.get("endTime") == null || appointmentSlot.get("endTime").isEmpty() ||
            userTimeZone == null || userTimeZone.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Bad Data: Missing required fields."));
        }

        FirebaseOrder order = fetchOrder(userIdFromRequest, orderId);
        if (order == null) {
            return gson.toJson(Map.of("success", false, "error", "Not authorized or order not found."));
        }
        String expertId = order.getExpertId();

        if (!userIdFromRequest.equals(userId) && !userId.equals(expertId) && !AuthenticationService.isAdmin(userId)) {
            return gson.toJson(Map.of("success", false, "error", "Not authorized."));
        }

        LocalDate date = LocalDate.parse(appointmentDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalTime appointmentStartTime = LocalTime.parse(appointmentSlot.get("startTime"), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime appointmentEndTime = LocalTime.parse(appointmentSlot.get("endTime"), DateTimeFormatter.ofPattern("HH:mm"));
        ZoneId zoneId = ZoneId.of(userTimeZone);
        ZonedDateTime zonedStartTimestamp = ZonedDateTime.of(date, appointmentStartTime, zoneId);
        ZonedDateTime zonedEndTimestamp = ZonedDateTime.of(date, appointmentEndTime, zoneId);

        Map<String, Object> appointmentDetails = new HashMap<>();
        appointmentDetails.put("appointmentUpdatedAt", new Timestamp(System.currentTimeMillis()));
        appointmentDetails.put("appointmentSlotStart", new Timestamp(zonedStartTimestamp.toInstant().toEpochMilli()));
        appointmentDetails.put("appointmentSlotEnd", new Timestamp(zonedEndTimestamp.toInstant().toEpochMilli()));
        this.db.collection("users").document(userIdFromRequest).collection("orders").document(orderId).update(appointmentDetails).get();
        return gson.toJson(Map.of("success", true, "message", "Appointment confirmed"));
    }

    @SuppressWarnings("unchecked")
    private String handleGetExpertAvailability(String userId, RequestBody requestBody) throws Exception {
        String rangeStart = requestBody.getRangeStart();
        String rangeEnd = requestBody.getRangeEnd();
        String userTimeZone = requestBody.getUserTimeZone();
        String userIdFromRequest = requestBody.getUserId();
        FirebaseOrder order = fetchOrder(userIdFromRequest, requestBody.getOrderId());

        if (order == null || order.getExpertId() == null || order.getExpertId().isEmpty() ||
            rangeStart == null || rangeEnd == null || rangeStart.isEmpty() || rangeEnd.isEmpty()) {
            return gson.toJson(Collections.emptyList());
        }

        String expertId = order.getExpertId();

        if (!userIdFromRequest.equals(userId) && !userId.equals(expertId) && !AuthenticationService.isAdmin(userId)) {
            return gson.toJson(Map.of("success", false, "error", "Not authorized."));
        }

        DocumentReference docRef = this.db.collection("users").document(expertId).collection("public").document("store");
        FieldMask mask = FieldMask.of("availability", "availabilityTimeZone");
        ApiFuture<DocumentSnapshot> future = docRef.get(mask);
        DocumentSnapshot document = future.get();
        Map<String, Map<String, List<Map<String, String>>>> availability = (Map<String, Map<String, List<Map<String, String>>>>) document.get("availability");
        String availabilityTimeZone = document.getString("availabilityTimeZone");

        if (availabilityTimeZone == null) {
            return gson.toJson(new HashMap<>());
        }

        List<Map<String, Object>> overrides = getOverrides(expertId, rangeStart, rangeEnd);
        List<Map<String, ZonedDateTime>> existingBookings = getExistingBookings(expertId, rangeStart, rangeEnd, availabilityTimeZone);

        if ((availability == null || availability.isEmpty()) && (overrides == null || overrides.isEmpty())) {
            return gson.toJson(new HashMap<>());
        }

        if (availability == null) {
            availability = new HashMap<>();
        }

        String planId = order.getPlanId();
        ServicePlan servicePlan = servicePlanService.getPlanDetails(planId, expertId);
        long durationOfSlot = servicePlan.getDuration();
        String durationUnitString = servicePlan.getDurationUnit();
        SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
        if ("HOURS".equals(durationUnitString)) {
            durationUnit = SchedulingService.DurationUnit.HOURS;
        }

        long startTimeIncrementInMinutes = durationOfSlot;

        Map<String, SchedulingService.AvailabilitySlot> availabilitySlots = new SchedulingService().getExpertAvailabilitySlots(
                availability,
                overrides,
                existingBookings,
                availabilityTimeZone,
                rangeStart,
                rangeEnd,
                userTimeZone,
                durationOfSlot,
                durationUnit,
                startTimeIncrementInMinutes
        );

        return gson.toJson(availabilitySlots);
    }

    // ============= Private Helpers =============

    private void createOrderInDB(String userId, String orderId, Map<String, Object> orderDetails) throws ExecutionException, InterruptedException {
        this.db.collection("users").document(userId).collection("orders").document(orderId).create(orderDetails).get();
    }

    private void verifyOrderInDB(String userId, String orderId) throws ExecutionException, InterruptedException {
        this.db.collection("users").document(userId).collection("orders").document(orderId)
            .update("payment_received_at", com.google.cloud.Timestamp.now()).get();
    }

    private void incrementCouponUsageCount(String userId, String orderId) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
        if (document.exists()) {
            Map<String, Object> orderDetails = document.getData();
            if (orderDetails == null) return;
            String couponCode = orderDetails.getOrDefault("couponCode", "").toString();
            if (couponCode == null || couponCode.isEmpty()) return;
            String expertId = orderDetails.get("expertId").toString();
            this.db.collection("users").document(expertId).collection("public").document("store")
                .collection("coupons").document(couponCode).update("claimsMadeSoFar", FieldValue.increment(1));
        }
    }

    private void rewardReferrer(String userId, String orderId) throws InterruptedException, ExecutionException {
        DocumentSnapshot orderDocument = this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
        String referrer = String.valueOf(orderDocument.get("referredBy"));
        String expertId = String.valueOf(orderDocument.get("expertId"));
        long referrerDiscount = Long.parseLong(String.valueOf(Objects.requireNonNull(orderDocument.getData()).getOrDefault("referrerDiscount", 0L)));
        if (referrer == null || expertId == null) return;

        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("orderId", orderId);
            fields.put("referrer", referrer);
            Map<String, Map<String, String>> referrerFields = new HashMap<>();
            referrerFields.put(expertId, fields);
            this.db.collection("users").document(userId).collection("private").document("referrals").set(referrerFields).get();
        } catch (Exception e) {
            return;
        }

        if (referrerDiscount <= 0L) return;

        String couponCode = null;
        boolean couponExists = true;
        while (couponExists) {
            couponCode = generateCode();
            couponExists = this.db.collection("public").document("store").collection("coupons").document(couponCode).get().get().exists();
        }

        Map<String, Object> couponFields = new HashMap<>();
        long millisecondsInOneYear = 86400L * 365L * 1000;
        couponFields.put("code", couponCode);
        couponFields.put("type", "PERCENT");
        couponFields.put("discount", referrerDiscount);
        couponFields.put("startDate", new Timestamp(System.currentTimeMillis()));
        couponFields.put("endDate", new Timestamp(System.currentTimeMillis() + millisecondsInOneYear));
        couponFields.put("isEnabled", true);
        couponFields.put("totalUsageLimit", 1L);
        couponFields.put("maxClaimsPerUser", 1L);
        couponFields.put("userIdsAllowed", Collections.singletonList(referrer));

        this.db.collection("users").document(expertId).collection("public").document("store")
            .collection("coupons").document(couponCode).create(couponFields).get();
    }

    private CouponResult applyCoupon(String couponCode, String expertId, String planName, String userId, String language) throws ExecutionException, InterruptedException {
        CouponResult couponResult = new CouponResult();

        if (couponCode == null || couponCode.isEmpty()) {
            couponResult.setValid(false);
            couponResult.setMessage("Coupon code cannot be empty");
            return couponResult;
        }
        if (expertId == null || expertId.isEmpty()) {
            couponResult.setValid(false);
            couponResult.setMessage("Expert Id cannot be empty");
            return couponResult;
        }
        if (planName == null || planName.isEmpty()) {
            couponResult.setValid(false);
            couponResult.setMessage("Plan name cannot be empty");
            return couponResult;
        }

        couponCode = couponCode.toUpperCase();
        Coupon coupon = new Coupon();
        DocumentSnapshot couponSnapshot = this.db.collection("users").document(expertId).collection("public").document("store").collection("coupons").document(couponCode).get().get();
        Map<String, Object> data = couponSnapshot.getData();
        if (data == null) {
            couponResult.setValid(false);
            couponResult.setMessage("Coupon not found");
            return couponResult;
        }

        coupon.setCode(couponCode);

        Object discount = data.getOrDefault("discount", 0.0);
        if (discount instanceof Double) {
            coupon.setDiscount((Double) discount);
        } else if (discount instanceof Long) {
            coupon.setDiscount(((Long) discount).doubleValue());
        }

        Object minCartAmount = data.getOrDefault("minCartAmount", 0.0);
        if (minCartAmount instanceof Double) {
            coupon.setMinCartAmount((Double) minCartAmount);
        } else if (discount instanceof Long) {
            coupon.setMinCartAmount(((Long) minCartAmount).doubleValue());
        }

        Object maxDiscountAmount = data.getOrDefault("maxDiscountAmount", Double.MAX_VALUE);
        if (maxDiscountAmount instanceof Double) {
            coupon.setMaxDiscountAmount((Double) maxDiscountAmount);
        } else if (maxDiscountAmount instanceof Long) {
            coupon.setMaxDiscountAmount(((Long) maxDiscountAmount).doubleValue());
        }

        coupon.setTotalUsageLimit((Long) data.getOrDefault("totalUsageLimit", Long.MAX_VALUE));
        coupon.setMaxClaimsPerUser((Long) data.getOrDefault("maxClaimsPerUser", Long.MAX_VALUE));
        coupon.setStartDate(new Timestamp(Objects.requireNonNull(couponSnapshot.getTimestamp("startDate")).toDate().getTime()));
        coupon.setEndDate(new Timestamp(Objects.requireNonNull(couponSnapshot.getTimestamp("endDate")).toDate().getTime()));
        coupon.setClaimsMadeSoFar((Long) data.getOrDefault("claimsMadeSoFar", 0L));
        coupon.setOnlyForNewUsers((Boolean) data.getOrDefault("onlyForNewUsers", false));
        coupon.setEnabled((Boolean) data.getOrDefault("isEnabled", true));
        coupon.setUserIdsAllowed((List<String>) data.getOrDefault("userIdsAllowed", Collections.emptyList()));
        String type = couponSnapshot.getString("type");
        if (type != null) {
            Coupon.CouponType enumValue;
            switch (type) {
                case "FLAT":
                    enumValue = Coupon.CouponType.FLAT;
                    break;
                case "PERCENT":
                    enumValue = Coupon.CouponType.PERCENTAGE;
                    break;
                default:
                    enumValue = null;
            }
            coupon.setType(enumValue);
        } else {
            throw new RuntimeException("Invalid coupon type: null");
        }
        ServicePlan servicePlan = servicePlanService.getPlanDetails(planName, expertId);

        FirebaseUser user = new FirebaseUser();
        Map<String, Long> userCouponFrequency = new HashMap<>();
        long count = this.db
                .collectionGroup("orders").whereEqualTo("couponCode", couponCode).whereEqualTo("userId", userId).whereEqualTo("expertId", expertId).orderBy("paymentReceivedAt", Query.Direction.DESCENDING).count().get().get().getCount();
        userCouponFrequency.put(couponCode, count);
        user.setCouponUsageFrequency(userCouponFrequency);
        user.setUid(userId);
        couponResult = CouponService.applyCoupon(coupon, servicePlan, user, language);
        return couponResult;
    }

    private String generateCode() {
        StringBuilder codeBuilder = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int randomChar = 65 + random.nextInt(26);
            codeBuilder.append((char) randomChar);
        }
        return codeBuilder.toString();
    }

    private FirebaseOrder fetchOrder(String clientId, String orderId) {
        FirebaseOrder firebaseOrder = new FirebaseOrder();
        firebaseOrder.setOrderId(orderId);
        firebaseOrder.setUserId(clientId);
        try {
            DocumentReference doc = this.db.collection("users").document(clientId).collection("orders").document(orderId);
            ApiFuture<DocumentSnapshot> ref = doc.get();
            DocumentSnapshot documentSnapshot = ref.get();
            if (documentSnapshot.exists()) {
                firebaseOrder.setExpertId((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("expertId", ""));
                firebaseOrder.setUserName((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("userName", ""));
                firebaseOrder.setVideo((boolean) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("isVideo", false));
                firebaseOrder.setPlanId((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("planId", ""));
            }
        } catch (Exception e) {
            return null;
        }
        return firebaseOrder;
    }

    private boolean checkIfOrderOwnedByUser(String orderId, String userId) {
        try {
            this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void cancelSubscriptionInDb(String userId, String subscriptionId) throws ExecutionException, InterruptedException {
        this.db.collection("users").document(userId).collection("orders").document(subscriptionId)
            .update("cancelled_at", new Timestamp(System.currentTimeMillis())).get();
    }

    private List<Map<String, ZonedDateTime>> getExistingBookings(String expertId, String startDate, String endDate, String expertTimeZone) {
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();
        try {
            long oneDayInSeconds = 24 * 60 * 60;
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd");
            Timestamp startTimestamp = Timestamp.from(dateFormat.parse(startDate).toInstant().minusSeconds(oneDayInSeconds));
            Timestamp endTimestamp = Timestamp.from(dateFormat.parse(endDate).toInstant().plusSeconds(oneDayInSeconds));
            List<QueryDocumentSnapshot> queryDocumentSnapshots = this.db.collectionGroup("orders")
                .whereEqualTo("expertId", expertId)
                .whereGreaterThanOrEqualTo("appointmentSlotStart", startTimestamp)
                .whereLessThanOrEqualTo("appointmentSlotStart", endTimestamp)
                .get().get().getDocuments();
            for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                Timestamp appointmentSlotStart = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("appointmentSlotStart"))).toSqlTimestamp();
                Timestamp appointmentSlotEnd = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("appointmentSlotEnd"))).toSqlTimestamp();
                Map<String, ZonedDateTime> booking = new HashMap<>();
                booking.put("startTime", appointmentSlotStart.toInstant().atZone(ZoneId.of(expertTimeZone)));
                booking.put("endTime", appointmentSlotEnd.toInstant().atZone(ZoneId.of(expertTimeZone)));
                existingBookings.add(booking);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return existingBookings;
    }

    private List<Map<String, Object>> getOverrides(String expertId, String startDate, String endDate) {
        CollectionReference overridesRef = this.db.collection("users").document(expertId).collection("public").document("store").collection("overrides");
        LocalDate startLocalDate = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalDate endLocalDate = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Query query = overridesRef.whereGreaterThanOrEqualTo("date", startLocalDate.toString()).whereLessThanOrEqualTo("date", endLocalDate.toString());
        List<Map<String, Object>> overrides = new ArrayList<>();
        try {
            List<QueryDocumentSnapshot> queryDocumentSnapshots = query.get().get().getDocuments();
            for (DocumentSnapshot document : queryDocumentSnapshots) {
                overrides.add(document.getData());
            }
            return overrides;
        } catch (Exception e) {
            LoggingService.error("get_overrides_failed", e);
            return Collections.emptyList();
        }
    }
}
