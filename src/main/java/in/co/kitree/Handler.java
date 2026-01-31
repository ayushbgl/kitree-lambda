package in.co.kitree;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.razorpay.RazorpayException;
import in.co.kitree.handlers.ConsultationHandler;
import in.co.kitree.handlers.ExpertHandler;
import in.co.kitree.handlers.ProductOrderHandler;
import in.co.kitree.handlers.WalletHandler;
import in.co.kitree.handlers.WebhookHandler;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;
import in.co.kitree.services.Razorpay.ErrorCode;
import in.co.kitree.services.AuthenticationService;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.core.SdkBytes;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.google.cloud.firestore.AggregateField.sum;

//public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
//public class Handler implements RequestHandler<Object, Object> {
public class Handler implements RequestHandler<RequestEvent, Object> {
    // Constants
    private static final int AUTO_TERMINATE_GRACE_PERIOD_SECONDS = 60;
    private static final double MIN_WALLET_RECHARGE_AMOUNT = 100.0;
    private static final int MIN_CONSULTATION_MINUTES = 3;

    // Robustness constants for cron job
    private static final int INITIATED_ORDER_TIMEOUT_SECONDS = 300; // 5 minutes - auto-fail INITIATED orders older than this
    private static final int BUSY_STATUS_STALENESS_SECONDS = 1800; // 30 minutes - free expert if BUSY for this long with no valid orders
    private static final int SINGLE_PARTICIPANT_TIMEOUT_SECONDS = 120; // 2 minutes - end call if only one participant
    
    Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private Firestore db;
    private Razorpay razorpay;
    //    private StripeService stripeService;
    protected PythonLambdaService pythonLambdaService;
    private AstrologyService astrologyService;
    private ProductOrderHandler productOrderHandler;
    private ExpertHandler expertHandler;
    private ConsultationHandler consultationHandler;
    private WalletHandler walletHandler;
    private WebhookHandler webhookHandler;

    public Handler() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (!isTest()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(
                                    getClass().getResourceAsStream("/serviceAccountKey.json")))
                            .build();
                    FirebaseApp.initializeApp(options);
                } else {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(
                                    getClass().getResourceAsStream("/serviceAccountKeyTest.json")))
                            .build();
                    FirebaseApp.initializeApp(options);
                }
            }
            this.db = FirestoreClient.getFirestore();
            this.razorpay = new Razorpay(isTest());
            this.pythonLambdaService = createPythonLambdaService();
            this.astrologyService = new AstrologyService();
            this.productOrderHandler = new ProductOrderHandler(db, razorpay);
            this.expertHandler = new ExpertHandler(db);
            this.consultationHandler = new ConsultationHandler(db, pythonLambdaService, isTest());
            this.walletHandler = new WalletHandler(db, razorpay);
            this.webhookHandler = new WebhookHandler(db, isTest());
        } catch (Exception e) {
            LoggingService.error("handler_init_failed", e);
            if (isTest()) {
                // In test environment, try to initialize Firebase again with test configuration
                try {
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(
                                        getClass().getResourceAsStream("/serviceAccountKeyTest.json")))
                                .build();
                        FirebaseApp.initializeApp(options);
                    }
                    this.db = FirestoreClient.getFirestore();
                } catch (Exception firebaseEx) {
                    LoggingService.error("firebase_init_failed_test_env", firebaseEx);
                    throw new RuntimeException("Failed to initialize Firebase in test environment", firebaseEx);
                }

                try {
                    this.razorpay = new Razorpay(true);
                } catch (RazorpayException ex) {
                    LoggingService.warn("razorpay_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    // Set default region for testing
                    System.setProperty("aws.region", "ap-south-1");
                    this.pythonLambdaService = createPythonLambdaService();
                } catch (Exception ex) {
                    LoggingService.warn("python_lambda_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    this.astrologyService = new AstrologyService();
                } catch (Exception ex) {
                    LoggingService.warn("astrology_service_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    this.productOrderHandler = new ProductOrderHandler(db, razorpay);
                } catch (Exception ex) {
                    LoggingService.warn("product_order_handler_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    this.expertHandler = new ExpertHandler(db);
                } catch (Exception ex) {
                    LoggingService.warn("expert_handler_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    this.consultationHandler = new ConsultationHandler(db, pythonLambdaService, isTest());
                } catch (Exception ex) {
                    LoggingService.warn("consultation_handler_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    this.walletHandler = new WalletHandler(db, razorpay);
                } catch (Exception ex) {
                    LoggingService.warn("wallet_handler_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    this.webhookHandler = new WebhookHandler(db, isTest());
                } catch (Exception ex) {
                    LoggingService.warn("webhook_handler_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
            } else {
                throw new RuntimeException("Failed to initialize Handler", e);
            }
        }
    }

    protected PythonLambdaService createPythonLambdaService() {
        LambdaClient lambdaClient = LambdaClient.builder()
                .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
                .build();

        return new PythonLambdaService() {
            @Override
            public PythonLambdaResponseBody invokePythonLambda(PythonLambdaEventRequest request) {
                try {
                    String payload = gson.toJson(request);
                    InvokeRequest invokeRequest = InvokeRequest.builder()
                            .functionName("certgen")
                            .payload(SdkBytes.fromUtf8String(payload))
                            .build();

                    InvokeResponse response = lambdaClient.invoke(invokeRequest);
                    String responsePayload = response.payload().asUtf8String();
                    return gson.fromJson(responsePayload, PythonLambdaResponseBody.class);
                } catch (Exception e) {
                    LoggingService.error("python_lambda_invoke_failed", e, Map.of(
                        "exceptionType", e.getClass().getName()
                    ));
                    throw new RuntimeException("Failed to invoke Python Lambda", e);
                }
            }
        };
    }

    public String handleRequest(RequestEvent event, Context context) {
        // Initialize structured logging with request context
        LoggingService.initRequest(context);
        
        try {
            if ("aws.events".equals(event.getSource())) {
                // Check if this is a scheduled auto-terminate event
                String detailType = event.getDetailType();
                if ("auto_terminate_consultations".equals(detailType)) {
                    LoggingService.setFunction("auto_terminate_consultations");
                    LoggingService.info("cron_job_started", Map.of("job", "auto_terminate_consultations"));
                    return handleAutoTerminateConsultations();
                }
                LoggingService.info("lambda_warmed_up");
                return "Warmed up!";
            }
            
            // Path-based routing for webhooks (before body-based routing)
            // This allows 3rd party webhooks (Stream, Razorpay) to POST to /webhooks/{provider}
            // Delegated to WebhookHandler
            String rawPath = event.getRawPath();
            if (WebhookHandler.handlesPath(rawPath)) {
                LoggingService.setFunction("webhook_handler");
                LoggingService.info("webhook_request_received", Map.of("path", rawPath));
                return webhookHandler.handleWebhookRequest(event, rawPath);
            }
            
            LoggingService.debug("request_received", Map.of("body", event.getBody() != null ? event.getBody() : "null"));
            RequestBody requestBody = this.gson.fromJson(event.getBody(), RequestBody.class);
            LoggingService.debug("environment_check", Map.of("isTest", isTest()));

            if ("make_admin".equals(requestBody.getFunction())) {
                if (!"C6DC17344FA8287F92C93B11CDF99".equals(requestBody.getAdminSecret())) {
                    return "Not Authorized";
                }
                Map<String, Object> claims = new HashMap<>();
                claims.put("admin", true);
                FirebaseAuth.getInstance().setCustomUserClaims(requestBody.getAdminUid(), claims);
                return "Done Successfully!";
            }

            if ("remove_admin".equals(requestBody.getFunction())) {
                if (!"C6DC17344FA8287F92C93B11CDF99".equals(requestBody.getAdminSecret())) {
                    return "Not Authorized";
                }
                Map<String, Object> claims = new HashMap<>();
                claims.put("admin", false);
                FirebaseAuth.getInstance().setCustomUserClaims(requestBody.getAdminUid(), claims);
                return "Done Successfully!";
            }

            if ("razorpay_webhook".equals(requestBody.getFunction())) {

                if (!"wq)(#1^|cqhhH$x".equals(requestBody.getAdminSecret())) {
                    return "Not Authorized";
                }

                JSONObject razorpayWebhookBody = new JSONObject(requestBody.getRazorpayWebhookBody().getBody());

                String eventType = razorpayWebhookBody.getString("event");

                String customerId = CustomerCipher.decryptCaesarCipher(razorpayWebhookBody.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity").getJSONObject("notes").getString("receipt"));

                boolean sig = razorpay.verifyWebhookSignature(requestBody.getRazorpayWebhookBody().getBody(), requestBody.getRazorpayWebhookBody().getHeaders().get("x-razorpay-signature"));

                if (!sig) {
                    return "Webhook Signature not verified";
                }

                updateSubscriptionDetails(customerId, razorpayWebhookBody, eventType);
                return "Done Successfully";
            }

            // Extract and verify Firebase token from Authorization header
            // Authentication is optional - some endpoints don't require it
            String userId = extractUserIdFromToken(event, requestBody.getFunction());
            
            // For functions that require authentication, ensure userId is not null
            if (userId == null && requiresAuthentication(requestBody.getFunction())) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: Authentication required"));
            }

            if ("generate_report".equals(requestBody.getFunction())) {
                String language = requestBody.getLanguage() == null ? "en" : requestBody.getLanguage();
                return fulfillDigitalOrders(userId, requestBody.getOrderId(), language);
            }

            // ============= Expert Endpoints =============
            // Delegated to ExpertHandler
            if (ExpertHandler.handles(requestBody.getFunction())) {
                return expertHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }

            if ("buy_service".equals(requestBody.getFunction())) {

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

                ServicePlan servicePlan = getPlanDetails(requestBody.getPlanId(), requestBody.getExpertId());

                if (servicePlan.getCategory().equals("CUSTOMIZED_BRACELET")) {
                    if (requestBody.getAmount() == null || requestBody.getAmount() < 250 || requestBody.getBeads() == null || requestBody.getBeads().isEmpty() || requestBody.getAddress() == null) {
                        // TODO: here we check for 250 amount to avoid API abuse, although we should validate with beads, currency INR is assumed and hardcoded in the code.
                        orderDetails.put("success", false);
                        return gson.toJson(orderDetails);
                    }
                    servicePlan.setAmount(requestBody.getAmount());
                }

                FirebaseUser user = UserService.getUserDetails(this.db, userId);
                FirebaseUser expert = UserService.getUserDetails(this.db, requestBody.getExpertId()); // TODO: This is only for admin dashboard
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
                    orderDetails.put("category", servicePlan.getCategory()); // Rename service to category in frontend orders screen.
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
                    // No referrals and coupons awarded, TODO: Test referrals with webinar etc.
                    verifyOrderInDB(userId, orderId); // TODO: Make one firestore call.
                    // TODO: Fulfill digital orders??
                    Map<String, String> response = new HashMap<>();
                    response.put("orderId", orderId);
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
                        try { // TODO
                            referrals = (Map<String, String>) Objects.requireNonNull(referralDocument.getData()).getOrDefault(requestBody.getExpertId(), new HashMap<>());
                        } catch (Exception e) {
                            referrals = new HashMap<>();
                        }
                        LoggingService.debug("referrals_lookup", Map.of("referrals", referrals != null ? referrals.toString() : "null"));
                        if (referrals == null || referrals.isEmpty()) {
                            DocumentSnapshot referralDetails = this.db.collection("users").document(requestBody.getExpertId()).collection("public").document("store").get().get();
                            int referredDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referredDiscount", 0)));
                            int referrerDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referrerDiscount", 0)));
                            LoggingService.debug("referral_discount", Map.of("referredDiscount", referredDiscount, "referrerDiscount", referrerDiscount));
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

                        // Round to 2 decimal places
                        walletDeduction = Math.round(walletDeduction * 100.0) / 100.0;
                        gatewayAmount = Math.round(gatewayAmount * 100.0) / 100.0;

                        if (gatewayAmount <= 0) {
                            paymentMethod = "WALLET_ONLY";
                        } else {
                            paymentMethod = "WALLET_AND_GATEWAY";
                        }
                    }
                }

                // Store payment breakdown in order details
                orderDetails.put("wallet_deduction", walletDeduction);
                orderDetails.put("gateway_amount", gatewayAmount);
                orderDetails.put("payment_method", paymentMethod);

                // Handle full wallet payment (no Razorpay needed)
                if ("WALLET_ONLY".equals(paymentMethod)) {
                    String orderId = UUID.randomUUID().toString();
                    final Double finalWalletDeduction = walletDeduction;
                    final String finalOrderId = orderId;
                    final String finalCurrency = currency;
                    final String finalExpertId = requestBody.getExpertId();

                    WalletService walletService = new WalletService(this.db);
                    final String[] walletTransactionIdHolder = new String[1];

                    // Execute atomically in a transaction
                    this.db.runTransaction(transaction -> {
                        // 1. Read wallet document
                        DocumentReference walletRef = db.collection("users").document(userId)
                                .collection("expert_wallets").document(finalExpertId);
                        DocumentSnapshot walletDoc = transaction.get(walletRef).get();

                        // 2. Validate balance (security check - never trust client)
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

                        // 3. Deduct from wallet
                        walletService.updateExpertWalletBalanceInTransactionWithSnapshot(
                                transaction, walletRef, walletDoc, finalCurrency, -finalWalletDeduction
                        );

                        // 4. Create wallet transaction record
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

                        // 5. Create order document with payment already received
                        orderDetails.put("order_id", finalOrderId);
                        orderDetails.put("wallet_transaction_id", txId);
                        // No gateway_type for wallet-only payments (wallet is not a gateway)
                        orderDetails.put("payment_received_at", com.google.cloud.Timestamp.now());

                        DocumentReference orderRef = db.collection("users").document(userId)
                                .collection("orders").document(finalOrderId);
                        transaction.set(orderRef, orderDetails);

                        return null;
                    }).get();

                    // Award referrals/coupons after successful transaction
                    incrementCouponUsageCount(userId, orderId);
                    rewardReferrer(userId, orderId);

                    // Return success response
                    Map<String, Object> response = new HashMap<>();
                    response.put("orderId", orderId);
                    response.put("walletDeduction", walletDeduction);
                    response.put("walletTransactionId", walletTransactionIdHolder[0]);
                    response.put("gatewayAmount", 0.0);
                    response.put("totalAmount", finalAmount);
                    response.put("paymentMethod", "WALLET_ONLY");
                    response.put("success", true);
                    return this.gson.toJson(response);
                }

                // Store pending wallet deduction for partial payments
                if (walletDeduction > 0) {
                    orderDetails.put("pending_wallet_deduction", walletDeduction);
                }

                Map<String, Object> response = new HashMap<>();
                if (servicePlan.getCurrency().equals("INR")) {
                    if (servicePlan.isSubscription()) {
                        // Subscriptions don't support wallet partial payment
                        String gatewaySubscriptionId = razorpay.createSubscription(servicePlan.getRazorpayId(), CustomerCipher.encryptCaesarCipher(userId));

                        // Generate our own order ID (UUID) instead of using gateway's ID
                        String orderId = UUID.randomUUID().toString();

                        orderDetails.put("order_id", orderId);
                        orderDetails.put("gateway_subscription_id", gatewaySubscriptionId);
                        orderDetails.put("subscription", true);
                        orderDetails.put("gateway_type", "RAZORPAY");
                        createOrderInDB(userId, orderId, orderDetails);

                        response.put("orderId", orderId);
                        response.put("gatewaySubscriptionId", gatewaySubscriptionId);
                        response.put("gatewayType", "RAZORPAY");
                        response.put("gatewayKey", razorpay.getRazorpayKey());
                    } else {
                        // Use gatewayAmount (which accounts for wallet deduction) instead of full amount
                        String gatewayOrderId = razorpay.createOrder(gatewayAmount, CustomerCipher.encryptCaesarCipher(userId));

                        // Generate our own order ID (UUID) instead of using gateway's ID
                        String orderId = UUID.randomUUID().toString();

                        orderDetails.put("order_id", orderId);
                        orderDetails.put("gateway_order_id", gatewayOrderId);
                        orderDetails.put("subscription", false);
                        orderDetails.put("gateway_type", "RAZORPAY");
                        createOrderInDB(userId, orderId, orderDetails);

                        response.put("orderId", orderId);
                        response.put("gatewayOrderId", gatewayOrderId);
                        response.put("gatewayType", "RAZORPAY");
                        response.put("gatewayKey", razorpay.getRazorpayKey());

                        // Include wallet payment breakdown in response
                        response.put("walletDeduction", walletDeduction);
                        response.put("gatewayAmount", gatewayAmount);
                        response.put("totalAmount", finalAmount);
                        response.put("paymentMethod", paymentMethod);
                    }
                } else {
//                    Map<String, String> paymentIntent = stripeService.createPaymentIntent(servicePlan.getAmount(), servicePlan.getCurrency());
//
//                    String orderId = UUID.randomUUID().toString();
//                    orderDetails.put("orderId", orderId);
//                    orderDetails.put("subscription", false);
//                    orderDetails.put("gateway_type", "STRIPE");
//                    createOrderInDB(userId, orderId, orderDetails);
//
//                    response.put("payment_intent_client_secret", paymentIntent.get("paymentIntent"));
//                    response.put("gatewayType", "STRIPE");
                }

                return this.gson.toJson(response);
            }

            if ("buy_gift".equals(requestBody.getFunction())) {
                String expertId = requestBody.getExpertId();
                String webinarId = requestBody.getPlanId();
                String orderId = requestBody.getOrderId(); // Order ID of the webinar
                Long giftAmount = requestBody.getGiftAmount(); // TODO: Currency
                String giftOrderId = razorpay.createOrder(Double.valueOf(giftAmount), CustomerCipher.encryptCaesarCipher(userId)); // Order ID of the gift within the webinar
                Map<String, String> response = new HashMap<>();
                response.put("gift_order_id", giftOrderId);

                Map<String, Object> giftDetails = new HashMap<>();
                giftDetails.put("gift_order_id", giftOrderId);
                giftDetails.put("amount", giftAmount);
                DocumentReference doc = this.db.collection("users").document(userId).collection("orders").document(orderId);
                doc.update("gifts", FieldValue.arrayUnion(giftDetails)).get();
                return this.gson.toJson(response);
            }

            if ("apply_coupon".equals(requestBody.getFunction())) {
                String language = requestBody.getLanguage() == null ? "en" : requestBody.getLanguage();
                return this.gson.toJson(applyCoupon(requestBody.getCouponCode(), requestBody.getExpertId(), requestBody.getPlanName(), userId, language));
            }

            if ("checkReferralBonus".equals(requestBody.getFunction())) {
                Map<String, Object> response = new HashMap<>();
                FirebaseUser user = UserService.getUserDetails(this.db, userId);
                if (user.getReferredBy() != null) { // TODO: Remove deduplication of code
                    String referredBy = user.getReferredBy().get(requestBody.getExpertId());
                    if (referredBy != null && !referredBy.isBlank() && !referredBy.equals(userId) && !referredBy.equals(requestBody.getExpertId())) {

                        DocumentSnapshot referralDocument = this.db.collection("users").document(userId).collection("private").document("referrals").get().get();
                        Map<String, String> referrals;
                        try { // TODO
                            referrals = (Map<String, String>) Objects.requireNonNull(referralDocument.getData()).getOrDefault(requestBody.getExpertId(), new HashMap<>());
                        } catch (Exception e) {
                            referrals = new HashMap<>();
                        }
                        LoggingService.debug("referrals_check", Map.of("referrals", referrals != null ? referrals.toString() : "null"));
                        if (referrals == null || referrals.isEmpty()) {
                            ServicePlan servicePlan = getPlanDetails(requestBody.getPlanId(), requestBody.getExpertId());
                            DocumentSnapshot referralDetails = this.db.collection("users").document(requestBody.getExpertId()).collection("public").document("store").get().get();
                            int referredDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referredDiscount", 0)));
                            Double newAmount = servicePlan.getAmount() * (1.00 - referredDiscount / 100.0);
                            newAmount = Math.round(newAmount * 100.0) / 100.0;
                            Double discount = Math.round((servicePlan.getAmount() - newAmount) * 100.0) / 100.0;
                            response.put("newAmount", newAmount); // TODO: Have exactly two decimal digits
                            response.put("discount", discount);
                            response.put("valid", true);
                            return this.gson.toJson(response);
                        }
                    }
                }
                response.put("valid", false);
                return this.gson.toJson(response);
            }

            if ("updateExpertImage".equals(requestBody.getFunction())) {
                String base64Image = requestBody.getBase64Image();
                if (base64Image == null || base64Image.isBlank()) {
                    return "Image not found";
                }
                String cloudinaryUrl = "cloudinary://334183382528294:C6nSfrjAMU0acJQ7WXPvmxCnSOY@kitree";
                Cloudinary cloudinary = new Cloudinary(cloudinaryUrl);
                cloudinary.config.secure = true;
                String path = this.isTest() ? "test/" : "";
                Map uploadResult = cloudinary.uploader().upload(base64Image, ObjectUtils.asMap("public_id", path + "expertDisplayPictures/" + userId, "unique_filename", false, "overwrite", true));
                String url = String.valueOf(uploadResult.get("secure_url"));
                LoggingService.info("expert_image_updated", Map.of("userId", userId, "url", url));
                this.db.collection("users").document(userId).collection("public").document("store").set(Map.of("displayPicture", url), SetOptions.merge()).get();
            }

            if ("verify_payment".equals(requestBody.getFunction())) {
                // Use generic getters for gateway fields
                String gatewaySubscriptionId = requestBody.getGatewaySubscriptionId();
                String gatewayOrderId = requestBody.getGatewayOrderId();
                String gatewayPaymentId = requestBody.getGatewayPaymentId();
                String gatewaySignature = requestBody.getGatewaySignature();
                String verificationType = requestBody.getType(); // "WALLET_RECHARGE", "gift", or null (default order)

                // Handle subscription verification
                if (gatewaySubscriptionId != null) {
                    if (razorpay.verifySubscription(gatewaySubscriptionId)) {
                        String firestoreSubscriptionOrderId = requestBody.getOrderId() != null ? requestBody.getOrderId() : gatewaySubscriptionId;
                        incrementCouponUsageCount(userId, firestoreSubscriptionOrderId);
                        rewardReferrer(userId, firestoreSubscriptionOrderId);
                        return "Verified";
                    }
                    return gson.toJson(Map.of("success", false, "errorMessage", "Subscription verification failed"));
                }

                // Validate required fields for non-subscription payments
                if (gatewayOrderId == null || gatewayPaymentId == null || gatewaySignature == null) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Missing payment details"));
                }

                // Verify Razorpay payment signature (common for all payment types)
                if (!razorpay.verifyPayment(gatewayOrderId, gatewayPaymentId, gatewaySignature)) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Payment verification failed"));
                }

                // === WALLET RECHARGE ===
                if ("WALLET_RECHARGE".equals(verificationType)) {
                    String expertId = requestBody.getExpertId();
                    if (expertId == null || expertId.isEmpty()) {
                        return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required for wallet recharge"));
                    }

                    WalletService walletService = new WalletService(this.db);

                    // Check if already completed (idempotency)
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

                    // Find the PENDING transaction
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

                    // Complete the transaction - update status and credit balance
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

                // === ORDER PAYMENT (default) ===
                String firestoreOrderId = requestBody.getOrderId();
                if (firestoreOrderId == null || firestoreOrderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
                }

                // Process pending wallet deduction for partial wallet payments
                walletHandler.processWalletDeductionOnPaymentVerification(userId, firestoreOrderId);

                verifyOrderInDB(userId, firestoreOrderId);
                NotificationService.sendNotification("f0JuQoCUQQ68I-tHqlkMxm:APA91bHZqzyL-xZG_g4qXhZyT9SP8jSh5hRJ8_21Ux9YPvcqzC7wi_tC9eKD6uZi52BndchctrXsINOmoo8A4OTn79oZkiwMeXPmcauVbgIXNEk_Qh7xFQc");
                NotificationService.sendNotification("fdljkc67TkI6iH-obDwVHR:APA91bGrUdmUGsI-SudlhQnrGasRTgiosL46ISzeudbcoXrzpNgz1Uu0y0c0WMZHtCt0ct5UwWN9kVFx3TJRuhTuxXjuay-6otAFO1uBaJNo8nz1VOAobbc");
                NotificationService.sendNotification("eX4C_MX0Q8e2yzwheVUx7a:APA91bFWnho4d3Mbx8EpAgJMGHzOdNzCb3O2fl3DdC1Rx92cMYZDSKIRFx2A-pR20BFUiXGL3qZMu64uGNJYzxAjOx9KG7cr-D8EIvGsOGiKI3EPWFJrU2c");

                incrementCouponUsageCount(userId, firestoreOrderId);
                rewardReferrer(userId, firestoreOrderId);
                return "Verified";
            }

            if ("app_startup".equals(requestBody.getFunction())) {
                String versionCode = requestBody.getVersionCode();
                String warningVersion = "1.1.0"; // Should be bigger or equal than forceVersion.
                String forceVersion = "1.0.0";
                Map<String, Integer> response = new HashMap<>();
                response.put("appUpdate", 0);
                if (SemanticVersion.compareSemanticVersions(versionCode, forceVersion) < 0) {
                    response.put("appUpdate", 2);
                } else if (versionCode.compareTo(warningVersion) < 0) {
                    response.put("appUpdate", 1);
                }

                // 0 - No update
                // 1 - Recommend update
                // 2 - Force update
                return this.gson.toJson(response);
            }

            if ("expert_metrics".equals(requestBody.getFunction())) {

                Map<String, Object> response = new HashMap<>();
                String expertId = requestBody.getExpertId();
                if (isAdmin(userId) || userId.equals(expertId)) {

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
                        orderEarningsDouble = (((Long) orderEarnings).doubleValue());
                    }

                    Object subscriptionEarnings = Objects.requireNonNull(subscriptionsSnapshot.get(sum("amount")));
                    double subscriptionEarningsDouble = 0.0;
                    if (subscriptionEarnings instanceof Double) {
                        subscriptionEarningsDouble = (Double) subscriptionEarnings;
                    } else if (subscriptionEarnings instanceof Long) {
                        subscriptionEarningsDouble = (((Long) subscriptionEarnings).doubleValue());
                    }
                    response.put("totalEarnings", orderEarningsDouble + subscriptionEarningsDouble);
                } else {
                    return "Not authorized";
                }

                return this.gson.toJson(response);
            }

            if ("cancel_subscription".equals(requestBody.getFunction())) {
                try {
                    // TODO: Test authorization and add caching
                    String gatewaySubscriptionId = requestBody.getGatewaySubscriptionId();
                    if (!(checkIfOrderOwnedByUser(gatewaySubscriptionId, userId) || isAdmin(userId))) {
                        return "Not authorized";
                    }
                    razorpay.cancel(gatewaySubscriptionId);
                    cancelSubscriptionInDb(userId, gatewaySubscriptionId);
                } catch (RazorpayException e) {
                    throw new HandlerException(ErrorCode.CANCELLATION_FAILED_SERVER_ERROR);
                }
            }

            if ("make_call".equals(requestBody.getFunction())) {
                Booking booking = new Booking(this.db);
                Map<String, String> response = booking.makeCall(userId, requestBody.getOrderId());
                return this.gson.toJson(response);
            }

            if ("get_certificate_courses".equals(requestBody.getFunction())) {
                PythonLambdaEventRequest getCoursesEvent = new PythonLambdaEventRequest();
                getCoursesEvent.setFunction("get_certificate_courses");
                getCoursesEvent.setLanguage(requestBody.getLanguage() == null ? "en" : requestBody.getLanguage());
                
                PythonLambdaResponseBody pythonResponse = pythonLambdaService.invokePythonLambda(getCoursesEvent);
                
                if (pythonResponse != null && pythonResponse.getCourses() != null) {
                    return gson.toJson(Map.of("courses", pythonResponse.getCourses()));
                } else {
                    LoggingService.error("python_lambda_courses_fetch_failed");
                    return gson.toJson(Map.of("success", false, "errorMessage", "Failed to fetch courses."));
                }
            }

            if ("generate_certificate".equals(requestBody.getFunction())) {
                PythonLambdaEventRequest genCertEvent = new PythonLambdaEventRequest();
                genCertEvent.setFunction("generate_certificate");
                genCertEvent.setName(requestBody.getCertificateHolderName());
                genCertEvent.setDate(requestBody.getCertificateDate());
                genCertEvent.setCourse(requestBody.getCertificateCourse());
                return pythonLambdaService.invokePythonLambda(genCertEvent).getCertificate();
            }
            if ("generate_aura_report".equals(requestBody.getFunction())) {
                // Basic validation (can be enhanced)
                if (requestBody.getUserName() == null || requestBody.getDob() == null || requestBody.getScannerDetails() == null) {
                    LoggingService.error("aura_report_missing_fields");
                    return gson.toJson(Map.of("success", false, "errorMessage", "Missing required user details or scanner data."));
                }
                // Check if user is admin
                if (!isAdmin(userId)) {
                    LoggingService.warn("aura_report_unauthorized_attempt", Map.of("userId", userId));
                    return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized action."));
                }

                PythonLambdaEventRequest auraEvent = new PythonLambdaEventRequest();
                auraEvent.setFunction("generate_aura_report"); // Function name for Python
                auraEvent.setUserName(requestBody.getUserName());
                auraEvent.setDob(requestBody.getDob());
                auraEvent.setScannerDetails(requestBody.getScannerDetails());
                auraEvent.setLanguage(requestBody.getLanguage() == null ? "en" : requestBody.getLanguage());
                // Add other necessary fields if Python needs them (e.g., expert details for author page?)
                // auraEvent.setExpertId(...) // TODO

                // Invoke Python Lambda
                PythonLambdaResponseBody pythonResponse = pythonLambdaService.invokePythonLambda(auraEvent);

                // Check the response and return
                if (pythonResponse != null && pythonResponse.getAuraReportLink() != null) {
                    LoggingService.info("aura_report_generated", Map.of("link", pythonResponse.getAuraReportLink()));
                    // Return only the link in a simple map for the frontend
                    return gson.toJson(Map.of("auraReportLink", pythonResponse.getAuraReportLink()));
                } else {
                    LoggingService.error("aura_report_generation_failed");
                    return gson.toJson(Map.of("success", false, "errorMessage", "Report generation failed on the backend."));
                }
            }

            if ("get_stream_user_token".equals(requestBody.getFunction())) {
                PythonLambdaEventRequest getStreamUserTokenEvent = new PythonLambdaEventRequest();
                getStreamUserTokenEvent.setFunction("get_stream_user_token");
                getStreamUserTokenEvent.setUserId(userId);
                getStreamUserTokenEvent.setTest(isTest());

                // TODO: Shall we get it from mobile via API? Then we have to see where it is used downstream.
                getStreamUserTokenEvent.setUserName(UserService.getUserDetails(this.db, userId).getName());

                return pythonLambdaService.invokePythonLambda(getStreamUserTokenEvent).getStreamUserToken();
            }

            if ("create_call".equals(requestBody.getFunction())) {

                // This implicitly also checks if the order is owned by the user.
                FirebaseOrder order = fetchOrder(requestBody.getUserId(), requestBody.getOrderId());
                if (order == null) {
                    return "Not authorized";
                }
                String expertId = order.getExpertId();
                if (Objects.equals(requestBody.getUserId(), expertId)) {
                    return "Cannot call yourself";
                }
                if (!userId.equals(expertId)) {
                    return "Not authorized";
                }

                PythonLambdaEventRequest createCallEvent = new PythonLambdaEventRequest();
                createCallEvent.setFunction("create_call");

                createCallEvent.setType("CONSULTATION");
                createCallEvent.setUserId(requestBody.getUserId());
                createCallEvent.setUserName(order.getUserName());
                createCallEvent.setExpertId(expertId);
                createCallEvent.setOrderId(requestBody.getOrderId());
                createCallEvent.setExpertName(requestBody.getExpertName());
                createCallEvent.setVideo(order.isVideo());
                createCallEvent.setTest(isTest());

                pythonLambdaService.invokePythonLambda(createCallEvent);
                return "Success";
            }

            // ================== SESSION / WEBINAR / COURSE APIs (O(1) Implementation) ==================

            if ("create_session_plan".equals(requestBody.getFunction())) {
                // Validate required fields
                String title = requestBody.getTitle();
                Long scheduledStartTime = requestBody.getScheduledStartTime();
                Integer durationMinutes = requestBody.getDurationMinutes();

                if (title == null || title.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Title is required"));
                }
                if (scheduledStartTime == null) {
                    return gson.toJson(Map.of("success", false, "error", "Scheduled start time is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.createSessionPlan(
                        userId,
                        title,
                        requestBody.getDescription(),
                        requestBody.getCategory(),
                        scheduledStartTime,
                        durationMinutes,
                        requestBody.getPrice(),
                        requestBody.getCurrency(),
                        requestBody.getMaxParticipants(),
                        requestBody.getSessionCount(),
                        requestBody.getInteractionMode(),
                        requestBody.getGiftsEnabled(),
                        requestBody.getGiftOptions()
                );
                return gson.toJson(result);
            }

            if ("add_course_session".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();
                Integer sessionNumber = requestBody.getSessionNumber();
                String title = requestBody.getTitle();
                Long scheduledStartTime = requestBody.getScheduledStartTime();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }
                if (sessionNumber == null) {
                    return gson.toJson(Map.of("success", false, "error", "Session number is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.addCourseSession(
                        userId,
                        planId,
                        sessionNumber,
                        title,
                        requestBody.getDescription(),
                        scheduledStartTime,
                        requestBody.getDurationMinutes()
                );
                return gson.toJson(result);
            }

            if ("start_session".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.startSession(
                        userId,
                        planId,
                        requestBody.getSessionNumber()
                );
                return gson.toJson(result);
            }

            if ("stop_session".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.stopSession(
                        userId,
                        planId,
                        requestBody.getSessionNumber()
                );
                return gson.toJson(result);
            }

            if ("join_session".equals(requestBody.getFunction())) {
                String orderId = requestBody.getOrderId();
                String expertId = requestBody.getExpertId();
                String planId = requestBody.getPlanId();

                if (orderId == null || orderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
                }
                if (expertId == null || expertId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Expert ID is required"));
                }
                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.joinSession(
                        userId,
                        orderId,
                        expertId,
                        planId,
                        requestBody.getSessionNumber(),
                        requestBody.getUserName(),
                        requestBody.getUserPhotoUrl()
                );
                return gson.toJson(result);
            }

            if ("leave_session".equals(requestBody.getFunction())) {
                String orderId = requestBody.getOrderId();
                String expertId = requestBody.getExpertId();
                String planId = requestBody.getPlanId();

                if (orderId == null || orderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
                }
                if (expertId == null || expertId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Expert ID is required"));
                }
                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.leaveSession(
                        userId,
                        orderId,
                        expertId,
                        planId,
                        requestBody.getSessionNumber()
                );
                return gson.toJson(result);
            }

            if ("raise_hand".equals(requestBody.getFunction())) {
                String orderId = requestBody.getOrderId();

                if (orderId == null || orderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.raiseHand(userId, orderId);
                return gson.toJson(result);
            }

            if ("lower_hand".equals(requestBody.getFunction())) {
                String orderId = requestBody.getOrderId();

                if (orderId == null || orderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.lowerHand(userId, orderId);
                return gson.toJson(result);
            }

            if ("promote_participant".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();
                String targetUserId = requestBody.getTargetUserId();
                String targetOrderId = requestBody.getTargetOrderId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }
                if (targetUserId == null || targetUserId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
                }
                if (targetOrderId == null || targetOrderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.promoteParticipant(
                        userId, planId, targetUserId, targetOrderId
                );
                return gson.toJson(result);
            }

            if ("demote_participant".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();
                String targetUserId = requestBody.getTargetUserId();
                String targetOrderId = requestBody.getTargetOrderId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }
                if (targetUserId == null || targetUserId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
                }
                if (targetOrderId == null || targetOrderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.demoteParticipant(
                        userId, planId, targetUserId, targetOrderId
                );
                return gson.toJson(result);
            }

            if ("mute_participant".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();
                String targetUserId = requestBody.getTargetUserId();
                String targetOrderId = requestBody.getTargetOrderId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }
                if (targetUserId == null || targetUserId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
                }
                if (targetOrderId == null || targetOrderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.muteParticipant(
                        userId, planId, targetUserId, targetOrderId
                );
                return gson.toJson(result);
            }

            if ("kick_participant".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();
                String targetUserId = requestBody.getTargetUserId();
                String targetOrderId = requestBody.getTargetOrderId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }
                if (targetUserId == null || targetUserId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
                }
                if (targetOrderId == null || targetOrderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.kickParticipant(
                        userId, planId, targetUserId, targetOrderId
                );
                return gson.toJson(result);
            }

            if ("toggle_session_gifts".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();
                Boolean giftsEnabled = requestBody.getGiftsEnabled();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.toggleGifts(userId, planId, giftsEnabled);
                return gson.toJson(result);
            }

            if ("get_session_participants".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                List<Map<String, Object>> participants = sessionService.getParticipants(userId, planId);
                return gson.toJson(Map.of("success", true, "participants", participants));
            }

            if ("get_raised_hands".equals(requestBody.getFunction())) {
                String planId = requestBody.getPlanId();

                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                List<Map<String, Object>> hands = sessionService.getRaisedHands(userId, planId);
                return gson.toJson(Map.of("success", true, "raisedHands", hands));
            }

            if ("get_live_sessions".equals(requestBody.getFunction())) {
                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                List<Map<String, Object>> sessions = sessionService.getLiveSessions(requestBody.getLimit());
                return gson.toJson(Map.of("success", true, "sessions", sessions));
            }

            if ("get_upcoming_sessions".equals(requestBody.getFunction())) {
                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                List<Map<String, Object>> sessions = sessionService.getUpcomingSessions(
                        requestBody.getCategory(),
                        requestBody.getLimit()
                );
                return gson.toJson(Map.of("success", true, "sessions", sessions));
            }

            if ("send_gift".equals(requestBody.getFunction())) {
                String orderId = requestBody.getOrderId();
                String expertId = requestBody.getExpertId();
                String planId = requestBody.getPlanId();
                String giftId = requestBody.getGiftId();

                if (orderId == null || orderId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
                }
                if (expertId == null || expertId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Expert ID is required"));
                }
                if (planId == null || planId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
                }
                if (giftId == null || giftId.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Gift ID is required"));
                }

                StreamService streamService = new StreamService(isTest());
                SessionService sessionService = new SessionService(db, streamService, isTest());

                Map<String, Object> result = sessionService.sendGift(
                        userId,
                        orderId,
                        expertId,
                        planId,
                        giftId,
                        requestBody.getPrice() // Can optionally override gift amount
                );
                return gson.toJson(result);
            }

            // ================== END SESSION APIs ==================

            if ("confirm_appointment".equals(requestBody.getFunction())) {

                String userIdFromRequest = requestBody.getUserId();
                String orderId = requestBody.getOrderId();
                String appointmentDate = requestBody.getAppointmentDate();
                Map<String, String> appointmentSlot = requestBody.getAppointmentSlot();
                String userTimeZone = requestBody.getUserTimeZone();

                if (userIdFromRequest == null || userIdFromRequest.isEmpty() || orderId == null || orderId.isEmpty() || appointmentDate == null || appointmentDate.isEmpty() || appointmentSlot == null || appointmentSlot.get("startTime") == null || appointmentSlot.get("startTime").isEmpty() || appointmentSlot.get("endTime") == null || appointmentSlot.get("endTime").isEmpty() || userTimeZone == null || userTimeZone.isEmpty()) {
                    return gson.toJson(Map.of("success", false, "error", "Bad Data: Missing required fields."));
                }

                FirebaseOrder order = fetchOrder(userIdFromRequest, orderId);
                if (order == null) {
                    return gson.toJson(Map.of("success", false, "error", "Not authorized or order not found."));
                }
                String expertId = order.getExpertId();

                if (!userIdFromRequest.equals(userId) && !userId.equals(expertId) && !isAdmin(userId)) {
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

            if ("get_expert_availability".equals(requestBody.getFunction())) {

                // TODO: Check for time zone.
                String rangeStart = requestBody.getRangeStart();
                String rangeEnd = requestBody.getRangeEnd();
                String userTimeZone = requestBody.getUserTimeZone();
                String userIdFromRequest = requestBody.getUserId();
                FirebaseOrder order = fetchOrder(userIdFromRequest, requestBody.getOrderId());
                LoggingService.debug("order_fetched_for_availability", Map.of("orderId", requestBody.getOrderId() != null ? requestBody.getOrderId() : "null"));
                // TODO: Check for date range, dont allow long date ranges for efficiency reasons.
                if (order == null || order.getExpertId() == null || order.getExpertId().isEmpty() || rangeStart == null || rangeEnd == null || rangeStart.isEmpty() || rangeEnd.isEmpty()) { // TODO: Check if rangeEnd > rangeStart and the difference is maximum 1 month + 1 day (or 32 days).
                    return gson.toJson(Collections.emptyList());
                }

                String expertId = order.getExpertId();
                
                // Check authorization: user must be the customer, expert, or admin
                if (!userIdFromRequest.equals(userId) && !userId.equals(expertId) && !isAdmin(userId)) {
                    return gson.toJson(Map.of("success", false, "error", "Not authorized."));
                }
                
                DocumentReference docRef = this.db.collection("users").document(expertId).collection("public").document("store");
                FieldMask mask = FieldMask.of("availability", "availabilityTimeZone");
                ApiFuture<DocumentSnapshot> future = docRef.get(mask);
                DocumentSnapshot document = future.get();
                Map<String, Map<String, List<Map<String, String>>>> availability = (Map<String, Map<String, List<Map<String, String>>>>) document.get("availability");
                String availabilityTimeZone = document.getString("availabilityTimeZone");
                LoggingService.debug("availability_timezone", Map.of("timezone", availabilityTimeZone != null ? availabilityTimeZone : "null"));
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
                ServicePlan servicePlan = getPlanDetails(planId, expertId); // TODO: Shall we consider storing these details along with the booking to avoid another firestore call?
                long durationOfSlot = servicePlan.getDuration();
                String durationUnitString = servicePlan.getDurationUnit();
                SchedulingService.DurationUnit durationUnit = SchedulingService.DurationUnit.MINUTES;
                if ("HOURS".equals(durationUnitString)) {
                    durationUnit = SchedulingService.DurationUnit.HOURS; // TODO: Check other units
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

            if ("get_astrological_details".equals(requestBody.getFunction())) {
                return astrologyService.getAstrologicalDetails(requestBody);
            }

            if ("get_dasha_details".equals(requestBody.getFunction())) {
                return astrologyService.getDashaDetails(requestBody);
            }

            if ("get_divisional_charts".equals(requestBody.getFunction())) {
                return astrologyService.getDivisionalCharts(requestBody);
            }

            if ("get_gochar_details".equals(requestBody.getFunction())) {
                Map<String, Object> gocharApiRequestBody = new HashMap<>();
                // Validate required fields
                if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                    requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                    requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                    requestBody.getHoroscopeLongitude() == null) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Missing required gochar details"));
                }
                gocharApiRequestBody.put("date", requestBody.getHoroscopeDate());
                gocharApiRequestBody.put("month", requestBody.getHoroscopeMonth());
                gocharApiRequestBody.put("year", requestBody.getHoroscopeYear());
                gocharApiRequestBody.put("hour", requestBody.getHoroscopeHour());
                gocharApiRequestBody.put("minute", requestBody.getHoroscopeMinute());
                gocharApiRequestBody.put("latitude", requestBody.getHoroscopeLatitude());
                gocharApiRequestBody.put("longitude", requestBody.getHoroscopeLongitude());
                gocharApiRequestBody.put("api_token", "D80FE645F582F9E0");
                return getGocharDetails(gson.toJson(gocharApiRequestBody));
            }

            // =====================================================================
            // WALLET MANAGEMENT ENDPOINTS
            // =====================================================================
            // Delegated to WalletHandler
            if (WalletHandler.handles(requestBody.getFunction())) {
                return walletHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }

            // =====================================================================
            // ON-DEMAND CONSULTATION ENDPOINTS
            // =====================================================================
            // Delegated to ConsultationHandler
            if (ConsultationHandler.handles(requestBody.getFunction())) {
                return consultationHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }

            // ============= Product Ecommerce Endpoints =============
            // Delegated to ProductOrderHandler
            if (ProductOrderHandler.handles(requestBody.getFunction())) {
                return productOrderHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }

        } catch (Exception e) {
            LoggingService.error("request_handler_exception", e);
            return gson.toJson(Map.of("success", false));
        }
        return null;
    }

    /**
     * Checks if a function requires authentication
     * Currently, all functions require authentication
     * 
     * @param functionName The function name
     * @return true (all functions require authentication)
     */
    private boolean requiresAuthentication(String functionName) {
        // All functions require authentication
        return true;
    }

    /**
     * Extracts and verifies user ID from Firebase token in Authorization header
     * All functions require authentication
     * 
     * @param event The Lambda request event
     * @param functionName The function being called
     * @return User ID if token is valid, null if token is missing or invalid
     */
    private String extractUserIdFromToken(RequestEvent event, String functionName) {
        // Extract token from headers
        String token = null;
        if (event.getHeaders() != null) {
            token = AuthenticationService.extractTokenFromHeaders(event.getHeaders());
        }
        
        // If no token, return null (will be handled by caller)
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        // Verify token
        try {
            String userId = AuthenticationService.verifyTokenAndGetUserId(token);
            return userId;
        } catch (FirebaseAuthException e) {
            LoggingService.warn("token_verification_failed", Map.of("error", e.getMessage()));
            // Return null - caller will handle the error
            return null;
        }
    }

    private void incrementCouponUsageCount(String userId, String orderId) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
        if (document.exists()) {
            Map<String, Object> orderDetails = document.getData();
            if (orderDetails == null) {
                return;
            }
            String couponCode = orderDetails.getOrDefault("couponCode", "").toString();
            if (couponCode == null || couponCode.isEmpty()) {
                return;
            }
            String expertId = orderDetails.get("expertId").toString();
            this.db.collection("users").document(expertId).collection("public").document("store").collection("coupons").document(couponCode).update("claimsMadeSoFar", FieldValue.increment(1));
        }
    }

    private List<Map<String, ZonedDateTime>> getExistingBookings(String expertId, String startDate, String endDate, String expertTimeZone) {
        // TODO: Take care of time zones, if we want to have each time zone covered, we want to fetch overrides for 1 more or less day based upon expert's timezone. Safer is to fetch for both dates.
        List<Map<String, ZonedDateTime>> existingBookings = new ArrayList<>();
        try {
            long oneDayInSeconds = 24 * 60 * 60; // 24 hours * 60 minutes * 60 seconds
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Timestamp startTimestamp = Timestamp.from(dateFormat.parse(startDate).toInstant().minusSeconds(oneDayInSeconds));
            Timestamp endTimestamp = Timestamp.from(dateFormat.parse(endDate).toInstant().plusSeconds(oneDayInSeconds));
            List<QueryDocumentSnapshot> queryDocumentSnapshots = this.db.collectionGroup("orders").whereEqualTo("expertId", expertId).whereGreaterThanOrEqualTo("appointmentSlotStart", startTimestamp).whereLessThanOrEqualTo("appointmentSlotStart", endTimestamp).get().get().getDocuments();
            for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                Timestamp appointmentSlotStart = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("appointmentSlotStart"))).toSqlTimestamp();
                Timestamp appointmentSlotEnd = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("appointmentSlotEnd"))).toSqlTimestamp();
                Map<String, ZonedDateTime> booking = new HashMap<>();
                booking.put("startTime", appointmentSlotStart.toInstant().atZone(ZoneId.of(expertTimeZone)));
                booking.put("endTime", appointmentSlotEnd.toInstant().atZone(ZoneId.of(expertTimeZone)));
                existingBookings.add(booking);
            }
        } catch (InterruptedException | ExecutionException | ParseException e) {
            throw new RuntimeException(e);
        }
        return existingBookings;
    }

    private List<Map<String, Object>> getOverrides(String expertId, String startDate, String endDate) {
        // TODO: Take care of time zones, if we want to have each time zone covered, we want to fetch overrides for 1 more or less day based upon expert's timezone. Safer is to fetch for both dates.
        CollectionReference overridesRef = this.db.collection("users").document(expertId).collection("public").document("store").collection("overrides");

        // Convert dates to LocalDate for easier comparison
        LocalDate startLocalDate = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalDate endLocalDate = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // Build a query to filter documents based on date range
        Query query = overridesRef.whereGreaterThanOrEqualTo("date", startLocalDate.toString()).whereLessThanOrEqualTo("date", endLocalDate.toString());

        // Get a list of documents
        List<Map<String, Object>> overrides = new ArrayList<>();
        try {
            List<QueryDocumentSnapshot> queryDocumentSnapshots = query.get().get().getDocuments(); // TODO: Handle potential exceptions <>
            for (DocumentSnapshot document : queryDocumentSnapshots) {
                overrides.add(document.getData());
            }
            return overrides;
        } catch (Exception e) {
            LoggingService.error("get_overrides_failed", e);
            return Collections.emptyList();
        }
    }

    private boolean checkIfOrderOwnedByUser(String orderId, String userId) {
        try {
            this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

//  private String getJSONInputStream(InputStream input) throws IOException {
//    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
//    String data = "";
//    String line;
//    while ((line = reader.readLine()) != null) {
//      data += line;
//    }
//    return data;
//  }

    private String generateCode() {
        // Use StringBuilder for efficient string building
        StringBuilder codeBuilder = new StringBuilder();
        Random random = new Random();

        // Loop 6 times to generate each character
        for (int i = 0; i < 6; i++) {
            // Generate a random int between 65 (A) and 90 (Z) for uppercase characters
            int randomChar = 65 + random.nextInt(26);
            codeBuilder.append((char) randomChar);
        }

        return codeBuilder.toString();
    }

    public static String toYYYYMMDD(com.google.cloud.Timestamp timestamp) {
        java.util.Date date = timestamp.toDate();
        Instant instant = date.toInstant();
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.of("UTC"));
        LocalDateTime localDateTime = zonedDateTime.toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return formatter.format(localDateTime);
    }

    private String fulfillDigitalOrders(String userId, String orderId, String language) throws ExecutionException, InterruptedException {
        DocumentSnapshot orderDocument = this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
        String type = String.valueOf(orderDocument.get("type"));
        if (!"DIGITAL_PRODUCT".equals(type)) {
            return "Bad Request";
        }
        if (orderDocument.contains("reportLink")) {
            return "Already fulfilled";
        }
        if (!orderDocument.contains("profileId")) {
            return "Bad Request";
        }
        String profileId = String.valueOf(orderDocument.get("profileId"));
        DocumentSnapshot profileDocument = this.db.collection("users").document(userId).collection("profiles").document(profileId).get().get();
        if (!profileDocument.contains("preferredName") || !profileDocument.contains("dob")) { //TODO: this is for pythagorean
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("errorCode", "N1").get();
            return "Bad Request";
        }
        if (!profileDocument.contains("dob")) {
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("errorCode", "N2").get();
            return "Bad Request";
        }
        if (orderDocument.contains("errorCode")) {
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("errorCode", FieldValue.delete()).get();
        }
        com.google.cloud.Timestamp dob = ((com.google.cloud.Timestamp) Objects.requireNonNull(profileDocument.get("dob")));
        String dobString = toYYYYMMDD(dob); // TODO: Take care of timezone in db and server.
        String category = String.valueOf(orderDocument.get("category"));
        if ("NUMEROLOGY_REPORT".equals(category)) {
            FirebaseUser user = UserService.getUserDetails(this.db, userId);
            String expertId = String.valueOf(orderDocument.get("expertId"));
            FirebaseUser expert = UserService.getUserDetails(this.db, expertId);
            PythonLambdaEventRequest createNumerologyReportEvent = new PythonLambdaEventRequest();
            ExpertStoreDetails expertStoreDetails = fetchExpertStoreDetails(expertId); // TODO: Too many Firebase calls.
            createNumerologyReportEvent.setFunction("numerology_report");
            createNumerologyReportEvent.setExpertName(expert.getName());
            createNumerologyReportEvent.setExpertPhoneNumber(expert.getPhoneNumber());
            createNumerologyReportEvent.setExpertImageUrl(expertStoreDetails.getImageUrl());
            createNumerologyReportEvent.setAboutExpert(expertStoreDetails.getAbout());
            createNumerologyReportEvent.setUserName(String.valueOf(profileDocument.get("name")));
            createNumerologyReportEvent.setUserPreferredName(String.valueOf(profileDocument.get("preferredName")));
            createNumerologyReportEvent.setDob(dobString);
            createNumerologyReportEvent.setUserPhoneNumber(user.getPhoneNumber());
            createNumerologyReportEvent.setOrderId(orderId);
            createNumerologyReportEvent.setLanguage(language);
            String reportLink = pythonLambdaService.invokePythonLambda(createNumerologyReportEvent).getReportLink();
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("reportLink", reportLink).get();
        }
        return "Done Successfully";
    }

    private void rewardReferrer(String userId, String orderId) throws InterruptedException, ExecutionException {
        DocumentSnapshot orderDocument = this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
        String referrer = String.valueOf(orderDocument.get("referredBy"));
        String expertId = String.valueOf(orderDocument.get("expertId"));
        long referrerDiscount = Long.parseLong(String.valueOf(Objects.requireNonNull(orderDocument.getData()).getOrDefault("referrerDiscount", 0L)));
        if (referrer == null || expertId == null) {
            return;
        }

        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("orderId", orderId);
            fields.put("referrer", referrer);
            Map<String, Map<String, String>> referrerFields = new HashMap<>();
            referrerFields.put(expertId, fields);
            this.db.collection("users").document(userId).collection("private").document("referrals").set(referrerFields).get();
        } catch (Exception e) { // TODO: This is old logic
            // Referrer has been already rewarded; do nothing
            return;
        }

        if (referrerDiscount <= 0L) {
            return;
        }

        // TODO: Create coupon for referrer
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

        this.db.collection("users").document(expertId).collection("public").document("store").collection("coupons").document(couponCode).create(couponFields).get();
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
        LoggingService.debug("coupon_data_loaded", Map.of("couponCode", couponCode));

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
        LoggingService.debug("coupon_type", Map.of("type", data.get("type") != null ? data.get("type").toString() : "null"));
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
                    // TODO: Handle unexpected string values (optional)
                    enumValue = null;
            }
            coupon.setType(enumValue);
        } else {
            throw new RuntimeException("Invalid coupon type: null");
        }
        LoggingService.debug("coupon_parsed", Map.of("couponCode", couponCode, "discount", coupon.getDiscount()));
        ServicePlan servicePlan = getPlanDetails(planName, expertId);

        FirebaseUser user = new FirebaseUser();
        Map<String, Long> userCouponFrequency = new HashMap<>();
        long count = this.db // TODO: We do not want this query everytime. We can optimize it.
                .collectionGroup("orders").whereEqualTo("couponCode", couponCode).whereEqualTo("userId", userId).whereEqualTo("expertId", expertId).orderBy("paymentReceivedAt", Query.Direction.DESCENDING).count().get().get().getCount();
        userCouponFrequency.put(couponCode, count);
        user.setCouponUsageFrequency(userCouponFrequency);
        user.setUid(userId);
        LoggingService.debug("coupon_validation", Map.of("userId", userId, "couponCode", couponCode, "usageCount", count));
        couponResult = CouponService.applyCoupon(coupon, servicePlan, user, language);
        return couponResult;
    }

    private void verifyOrderInDB(String userId, String orderId) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> future = this.db.collection("users").document(userId).collection("orders").document(orderId).update("paymentReceivedAt", new Timestamp(System.currentTimeMillis()));
        future.get();
    }

    private void updateSubscriptionDetails(String userId, JSONObject razorpayWebhookBody, String eventType) throws ExecutionException, InterruptedException {
        String subscriptionId = razorpayWebhookBody.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity").getString("id");
        if (eventType.equals("subscription.charged")) {

            DocumentReference subscriptionRef = this.db.collection("users").document(userId).collection("orders").document(subscriptionId);
            DocumentSnapshot subscriptionSnapshot = subscriptionRef.get().get();
            Long amount = subscriptionSnapshot.getLong("amount");
            String currency = subscriptionSnapshot.getString("currency");
            String expertId = subscriptionSnapshot.getString("expert_id");
            String category = subscriptionSnapshot.getString("category");

            long currentEnd = razorpayWebhookBody.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity").getLong("current_end");
            Timestamp currentEndTime = new Timestamp(currentEnd * 1000);
            Map<String, Object> subscriptionFields = new HashMap<>();
            Map<String, Object> subscriptionPaymentsFields = new HashMap<>();
            Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());

            subscriptionFields.put("current_end_time", currentEndTime); // TODO: Maybe want to check that the subscription state is active
            subscriptionFields.put("payment_received_at", currentTimestamp);
            subscriptionPaymentsFields.put("payment_received_at", currentTimestamp);
            subscriptionPaymentsFields.put("amount", amount);
            subscriptionPaymentsFields.put("currency", currency);
            subscriptionPaymentsFields.put("expert_id", expertId);
            subscriptionPaymentsFields.put("category", category);

            ApiFuture<WriteResult> future = this.db.collection("users").document(userId).collection("orders").document(subscriptionId).update(subscriptionFields);
            ApiFuture<WriteResult> futurePayments = this.db.collection("users").document(userId).collection("orders").document(subscriptionId).collection("subscription_payments").document().create(subscriptionPaymentsFields);

            future.get();
            futurePayments.get();
        } else if (eventType.equals("subscription.cancelled")) {
            cancelSubscriptionInDb(userId, subscriptionId);
        }
    }

    private void cancelSubscriptionInDb(String userId, String subscriptionId) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> future = this.db.collection("users").document(userId).collection("orders").document(subscriptionId).update("cancelled_at", new Timestamp(System.currentTimeMillis()));
        future.get();

    }

    private void createOrderInDB(String userId, String orderId, Map<String, Object> orderDetails) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> future = this.db.collection("users").document(userId).collection("orders").document(orderId).create(orderDetails);
        future.get();
    }

    private ServicePlan getPlanDetails(String planId, String expertId) throws ExecutionException, InterruptedException {

        ServicePlan servicePlan = null;
        DocumentReference doc = this.db.collection("users").document(expertId).collection("plans").document(planId);
        ApiFuture<DocumentSnapshot> ref = doc.get();
        DocumentSnapshot documentSnapshot = ref.get();
        if (documentSnapshot.exists()) {
            servicePlan = new ServicePlan();
            servicePlan.setPlanId(planId);
            LoggingService.debug("plan_details_loaded", Map.of("planId", planId, "expertId", expertId));
            servicePlan.setAmount(((Long) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("amount", 0L)).doubleValue());
            servicePlan.setCurrency((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("currency", ""));
            servicePlan.setSubscription((Boolean) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("isSubscription", false));
            servicePlan.setVideo((Boolean) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("isVideo", false));
            servicePlan.setRazorpayId((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("razorpayId", ""));
            servicePlan.setType((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("type", ""));
            servicePlan.setSubtype((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("subtype", ""));
            servicePlan.setCategory((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("category", ""));
            // Handle both Integer and Long for duration (Firestore can return either)
            Object durationObj = Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("duration", 30L);
            Long duration;
            if (durationObj instanceof Integer) {
                duration = ((Integer) durationObj).longValue();
            } else if (durationObj instanceof Long) {
                duration = (Long) durationObj;
            } else {
                duration = 30L; // Default fallback
            }
            servicePlan.setDuration(duration);
            servicePlan.setDurationUnit((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("durationUnit", "MINUTES")); // TODO: Default value
            if (documentSnapshot.contains("date")) {
                com.google.cloud.Timestamp date = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("date")));
                servicePlan.setDate(date);
            }
            if (documentSnapshot.contains("sessionStartedAt")) {
                com.google.cloud.Timestamp sessionStartedAt = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("sessionStartedAt")));
                servicePlan.setSessionStartedAt(sessionStartedAt);
            }
            if (documentSnapshot.contains("sessionCompletedAt")) {
                com.google.cloud.Timestamp sessionCompletedAt = ((com.google.cloud.Timestamp) Objects.requireNonNull(documentSnapshot.get("sessionCompletedAt")));
                servicePlan.setSessionCompletedAt(sessionCompletedAt);
            }
            servicePlan.setTitle((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("title", ""));
            
            // Read on-demand consultation rate fields (handle both Integer and Long from Firestore)
            Map<String, Object> data = Objects.requireNonNull(documentSnapshot.getData());
            
            // Convert onDemandRatePerMinuteAudio
            if (data.containsKey("onDemandRatePerMinuteAudio")) {
                Object audioRateObj = data.get("onDemandRatePerMinuteAudio");
                if (audioRateObj != null) {
                    if (audioRateObj instanceof Double) {
                        servicePlan.setOnDemandRatePerMinuteAudio((Double) audioRateObj);
                    } else if (audioRateObj instanceof Integer) {
                        servicePlan.setOnDemandRatePerMinuteAudio(((Integer) audioRateObj).doubleValue());
                    } else if (audioRateObj instanceof Long) {
                        servicePlan.setOnDemandRatePerMinuteAudio(((Long) audioRateObj).doubleValue());
                    } else if (audioRateObj instanceof Number) {
                        servicePlan.setOnDemandRatePerMinuteAudio(((Number) audioRateObj).doubleValue());
                    }
                }
            }
            
            // Convert onDemandRatePerMinuteVideo
            if (data.containsKey("onDemandRatePerMinuteVideo")) {
                Object videoRateObj = data.get("onDemandRatePerMinuteVideo");
                if (videoRateObj != null) {
                    if (videoRateObj instanceof Double) {
                        servicePlan.setOnDemandRatePerMinuteVideo((Double) videoRateObj);
                    } else if (videoRateObj instanceof Integer) {
                        servicePlan.setOnDemandRatePerMinuteVideo(((Integer) videoRateObj).doubleValue());
                    } else if (videoRateObj instanceof Long) {
                        servicePlan.setOnDemandRatePerMinuteVideo(((Long) videoRateObj).doubleValue());
                    } else if (videoRateObj instanceof Number) {
                        servicePlan.setOnDemandRatePerMinuteVideo(((Number) videoRateObj).doubleValue());
                    }
                }
            }
            
            // Convert onDemandRatePerMinuteChat
            if (data.containsKey("onDemandRatePerMinuteChat")) {
                Object chatRateObj = data.get("onDemandRatePerMinuteChat");
                if (chatRateObj != null) {
                    if (chatRateObj instanceof Double) {
                        servicePlan.setOnDemandRatePerMinuteChat((Double) chatRateObj);
                    } else if (chatRateObj instanceof Integer) {
                        servicePlan.setOnDemandRatePerMinuteChat(((Integer) chatRateObj).doubleValue());
                    } else if (chatRateObj instanceof Long) {
                        servicePlan.setOnDemandRatePerMinuteChat(((Long) chatRateObj).doubleValue());
                    } else if (chatRateObj instanceof Number) {
                        servicePlan.setOnDemandRatePerMinuteChat(((Number) chatRateObj).doubleValue());
                    }
                }
            }
            
            // Set onDemandCurrency
            if (data.containsKey("onDemandCurrency")) {
                servicePlan.setOnDemandCurrency((String) data.get("onDemandCurrency"));
            }
        }

        LoggingService.debug("service_plan_loaded", Map.of("planId", planId != null ? planId : "null"));
        return servicePlan;
    }

    private boolean isTest() {
        return "test".equals(System.getenv("ENVIRONMENT"));
    }

    private boolean isAdmin(String userId) throws FirebaseAuthException {
        return Boolean.TRUE.equals(FirebaseAuth.getInstance().getUser(userId).getCustomClaims().get("admin"));
    }

    private FirebaseOrder fetchOrder(String clientId, String orderId) {
        LoggingService.debug("fetch_order", Map.of("clientId", clientId != null ? clientId : "null", "orderId", orderId != null ? orderId : "null"));
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
            LoggingService.error("fetch_order_failed", e, Map.of("clientId", clientId != null ? clientId : "null", "orderId", orderId != null ? orderId : "null"));
            return null;
        }
        return firebaseOrder;
    }

    private ExpertStoreDetails fetchExpertStoreDetails(String expertId) {
        ExpertStoreDetails expertStoreDetails = new ExpertStoreDetails();
        try {
            DocumentReference doc = this.db.collection("users").document(expertId).collection("public").document("store");
            ApiFuture<DocumentSnapshot> ref = doc.get();
            DocumentSnapshot documentSnapshot = ref.get();
            if (documentSnapshot.exists()) {
                expertStoreDetails.setAbout((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("about", ""));
                expertStoreDetails.setStoreName((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("storeName", ""));
                expertStoreDetails.setImageUrl((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("displayPicture", ""));
            }
        } catch (Exception e) { // TODO
            LoggingService.error("fetch_expert_store_details_failed", e, Map.of("expertId", expertId != null ? expertId : "null"));
            expertStoreDetails.setStoreName("");
            expertStoreDetails.setAbout("");
            expertStoreDetails.setImageUrl("");
        }
        return expertStoreDetails;
    }


    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private String getGocharDetails(String requestBody) throws Exception {
        // Use the same provider configuration as other astrology endpoints
        String baseUrl;
        if (AstrologyServiceConfig.isAwsLambdaProviderSelected()) {
            baseUrl = AstrologyServiceConfig.AWS_LAMBDA_BASE_URL;
        } else if (AstrologyServiceConfig.isPythonServerProviderSelected()) {
            baseUrl = AstrologyServiceConfig.PYTHON_SERVER_BASE_URL;
        } else {
            // Default to AWS Lambda if provider is not recognized
            baseUrl = AstrologyServiceConfig.AWS_LAMBDA_BASE_URL;
        }
        
        String API_URL = baseUrl + "/gochar";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            LoggingService.debug("gochar_api_response", Map.of("statusCode", httpResponse.statusCode()));
            // Parse Lambda response format to extract body field if wrapped
            return parseLambdaResponse(response);
        } else {
            throw new RuntimeException("Gochar API request failed with status code: " + httpResponse.statusCode());
        }
    }

    /**
     * Parse Lambda Function URL response
     * Lambda Function URLs automatically extract the body from the Lambda response format,
     * so the response should already be the JSON we want. This method handles both cases.
     */
    private String parseLambdaResponse(String response) {
        try {
            // Try to parse as JSON to check if it's wrapped in Lambda response format
            Map<String, Object> responseMap = gson.fromJson(response, Map.class);
            
            // If it has a "body" field, it's wrapped in Lambda response format
            if (responseMap.containsKey("body") && responseMap.get("body") instanceof String) {
                return (String) responseMap.get("body");
            }
            
            // Otherwise, return as-is (Lambda Function URL already extracted the body)
            return response;
        } catch (Exception e) {
            // If parsing fails, assume it's already the JSON we want
            return response;
        }
    }

    // =====================================================================
    // AUTO-TERMINATE CONSULTATIONS CRON JOB
    // =====================================================================

    /**
     * Auto-terminate consultations that have exceeded their max_allowed_duration.
     * This method is called by AWS EventBridge on a schedule (e.g., every 5 minutes).
     */
    private String handleAutoTerminateConsultations() {
        long nowMillis = System.currentTimeMillis();
        int terminatedCount = 0;
        int errorCount = 0;
        
        try {
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);
            
            // Query all CONNECTED on-demand consultations
            // Using collectionGroup query to search across all users' orders
            com.google.cloud.Timestamp now = com.google.cloud.Timestamp.now();
            nowMillis = now.toDate().getTime(); // Update nowMillis with precise timestamp
            
            Query query = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("status", "CONNECTED");
            
            for (QueryDocumentSnapshot doc : query.get().get().getDocuments()) {
                try {
                    // Get consultation details
                    String orderId = doc.getId();
                    String userId = doc.getReference().getParent().getParent().getId();
                    com.google.cloud.Timestamp startTime = doc.getTimestamp("start_time");
                    Long maxAllowedDuration = doc.getLong("max_allowed_duration");
                    
                    if (startTime == null || maxAllowedDuration == null) {
                        LoggingService.warn("auto_terminate_skipping_order", Map.of(
                            "orderId", orderId,
                            "reason", "missing_start_time_or_max_duration"
                        ));
                        continue;
                    }
                    
                    // Calculate elapsed time
                    long startTimeMillis = startTime.toDate().getTime();
                    long elapsedSeconds = (nowMillis - startTimeMillis) / 1000;
                    
                    // Add grace period
                    if (elapsedSeconds >= (maxAllowedDuration + AUTO_TERMINATE_GRACE_PERIOD_SECONDS)) {
                        LoggingService.setContext(userId, orderId, null);
                        LoggingService.info("auto_terminating_consultation", Map.of(
                            "elapsedSeconds", elapsedSeconds,
                            "maxAllowedDuration", maxAllowedDuration,
                            "gracePeriodSeconds", AUTO_TERMINATE_GRACE_PERIOD_SECONDS
                        ));
                        
                        // End the consultation
                        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
                        if (order != null && "CONNECTED".equals(order.getStatus())) {
                            // Calculate final values using interval-based billing if available
                            Long totalDurationSeconds = consultationService.calculateElapsedSeconds(order);
                            Long billableSeconds;

                            // Prefer interval-based calculation if intervals exist
                            java.util.List<Map<String, Object>> userIntervalMaps = order.getUserIntervals();
                            java.util.List<Map<String, Object>> expertIntervalMaps = order.getExpertIntervals();

                            if (userIntervalMaps != null && !userIntervalMaps.isEmpty() &&
                                expertIntervalMaps != null && !expertIntervalMaps.isEmpty()) {
                                java.util.List<OnDemandConsultationService.ParticipantInterval> userIntervals =
                                    consultationService.parseIntervalsFromList(userIntervalMaps);
                                java.util.List<OnDemandConsultationService.ParticipantInterval> expertIntervals =
                                    consultationService.parseIntervalsFromList(expertIntervalMaps);

                                billableSeconds = consultationService.calculateOverlapFromIntervals(
                                    userIntervals, expertIntervals, order.getMaxAllowedDuration());
                                LoggingService.info("cron_using_interval_billing");
                            } else {
                                billableSeconds = consultationService.calculateBillableSeconds(order);
                                LoggingService.info("cron_using_simple_billing");
                            }

                            Double cost = consultationService.calculateCost(billableSeconds, order.getExpertRatePerMinute());
                            Double platformFeeAmount = consultationService.calculatePlatformFee(cost, order.getPlatformFeePercent());
                            Double expertEarnings = cost - platformFeeAmount;

                            String expertId = order.getExpertId();
                            String currency = order.getCurrency();

                            LoggingService.info("auto_terminate_billing", Map.of(
                                "orderId", orderId,
                                "totalDurationSeconds", totalDurationSeconds,
                                "billableSeconds", billableSeconds,
                                "cost", cost
                            ));

                            final Long finalDurationSeconds = billableSeconds; // Use billable seconds
                            final Double finalCost = cost;
                            final Double finalPlatformFeeAmount = platformFeeAmount;
                            final Double finalExpertEarnings = expertEarnings;
                            final String finalUserId = userId;
                            final String finalOrderId = orderId;
                            final String finalExpertId = expertId;
                            final String finalCurrency = currency;
                            
                            ExpertEarningsService earningsService = new ExpertEarningsService(this.db);
                            
                            this.db.runTransaction(transaction -> {
                                // ============================================================
                                // PHASE 1: READ ALL DOCUMENTS FIRST (Firestore requirement)
                                // ============================================================
                                
                                // Read user's expert-specific wallet
                                DocumentReference userExpertWalletRef = db.collection("users").document(finalUserId)
                                        .collection("expert_wallets").document(finalExpertId);
                                DocumentSnapshot userExpertWalletDoc = transaction.get(userExpertWalletRef).get();
                                
                                // Read expert user doc (for earnings balance)
                                DocumentReference expertUserRef = db.collection("users").document(finalExpertId);
                                DocumentSnapshot expertUserDoc = transaction.get(expertUserRef).get();
                                
                                // Read order (for validation in completeOrderInTransaction)
                                DocumentReference orderRef = db.collection("users").document(finalUserId)
                                        .collection("orders").document(finalOrderId);
                                DocumentSnapshot orderDoc = transaction.get(orderRef).get();
                                
                                // Verify order is still CONNECTED (prevent double-charging)
                                if (!orderDoc.exists() || !"CONNECTED".equals(orderDoc.getString("status"))) {
                                    throw new IllegalStateException("Order is not in CONNECTED status: " + finalOrderId);
                                }
                                
                                // Check for other active consultations (read all potential orders)
                                boolean hasOtherActive = consultationService.hasOtherConnectedConsultationsInTransaction(
                                    transaction, finalExpertId, finalOrderId
                                );
                                
                                // Read expert status document
                                DocumentReference expertStoreRef = db.collection("users").document(finalExpertId)
                                        .collection("public").document("store");
                                DocumentSnapshot expertStoreDoc = transaction.get(expertStoreRef).get();
                                
                                // ============================================================
                                // PHASE 2: PERFORM ALL WRITES
                                // ============================================================
                                
                                // Deduct from user's expert-specific wallet
                                walletService.updateExpertWalletBalanceInTransaction(
                                    transaction, finalUserId, finalExpertId, finalCurrency, -finalCost
                                );
                                
                                // Credit expert's earnings passbook (not wallet)
                                earningsService.creditExpertEarningsInTransaction(
                                    transaction, finalExpertId, finalCurrency, finalCost, finalPlatformFeeAmount,
                                    finalOrderId, "On-demand consultation earning (auto-terminated)"
                                );
                                
                                // Create deduction transaction for user's expert-specific wallet
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
                                if (!hasOtherActive) {
                                    Map<String, Object> statusUpdates = new HashMap<>();
                                    statusUpdates.put("consultation_status", "FREE");
                                    statusUpdates.put("consultation_status_updated_at", com.google.cloud.Timestamp.now());
                                    transaction.update(expertStoreRef, statusUpdates);
                                }
                                
                                return null;
                            }).get();
                            
                            terminatedCount++;
                            LoggingService.info("consultation_auto_terminated_successfully", Map.of(
                                "orderId", orderId
                            ));
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    LoggingService.error("auto_terminate_consultation_error", e, Map.of(
                        "orderId", doc.getId()
                    ));
                }
            }
        } catch (Exception e) {
            LoggingService.error("auto_terminate_consultations_fatal_error", e);
            return gson.toJson(Map.of(
                "success", false,
                "errorMessage", e.getMessage()
            ));
        }
        
        // ============================================================
        // PHASE 2: Auto-fail stale INITIATED orders (user started but never connected)
        // ============================================================
        int failedInitiatedCount = 0;
        try {
            LoggingService.info("checking_stale_initiated_orders");
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

            // Query all INITIATED on-demand consultations
            Query initiatedQuery = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("status", "INITIATED");

            for (QueryDocumentSnapshot doc : initiatedQuery.get().get().getDocuments()) {
                try {
                    String orderId = doc.getId();
                    String userId = doc.getReference().getParent().getParent().getId();
                    String expertId = doc.getString("expert_id");
                    com.google.cloud.Timestamp createdAt = doc.getTimestamp("created_at");

                    if (createdAt == null) {
                        LoggingService.warn("initiated_order_missing_created_at", Map.of("orderId", orderId));
                        continue;
                    }

                    // Check if order is stale (older than INITIATED_ORDER_TIMEOUT_SECONDS)
                    long createdAtMillis = createdAt.toDate().getTime();
                    long elapsedSeconds = (nowMillis - createdAtMillis) / 1000;

                    if (elapsedSeconds >= INITIATED_ORDER_TIMEOUT_SECONDS) {
                        LoggingService.setContext(userId, orderId, expertId);
                        LoggingService.info("auto_failing_stale_initiated_order", Map.of(
                            "elapsedSeconds", elapsedSeconds,
                            "timeoutSeconds", INITIATED_ORDER_TIMEOUT_SECONDS
                        ));

                        // Mark order as FAILED
                        DocumentReference orderRef = doc.getReference();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", "FAILED");
                        updates.put("end_time", com.google.cloud.Timestamp.now());
                        updates.put("failure_reason", "INITIATED_TIMEOUT");
                        orderRef.update(updates).get();

                        // Free expert if no other active consultations
                        if (expertId != null) {
                            boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
                            // Also check for other non-stale INITIATED orders
                            boolean hasOtherInitiated = hasNonStaleInitiatedOrders(expertId, orderId, nowMillis);

                            if (!hasOtherActive && !hasOtherInitiated) {
                                LoggingService.info("freeing_expert_after_initiated_timeout");
                                walletService.setConsultationStatus(expertId, "FREE");
                            }
                        }

                        // End the Stream call if it exists
                        String streamCallCid = doc.getString("stream_call_cid");
                        if (streamCallCid != null) {
                            try {
                                StreamService streamService = new StreamService(isTest());
                                String[] cidParts = StreamService.parseCallCid(streamCallCid);
                                if (cidParts != null) {
                                    streamService.endCall(cidParts[0], cidParts[1]);
                                }
                            } catch (Exception streamError) {
                                LoggingService.warn("failed_to_end_stream_call_for_stale_initiated", Map.of(
                                    "streamCallCid", streamCallCid,
                                    "error", streamError.getMessage()
                                ));
                            }
                        }

                        failedInitiatedCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    LoggingService.error("auto_fail_initiated_order_error", e, Map.of(
                        "orderId", doc.getId()
                    ));
                }
            }
        } catch (Exception e) {
            LoggingService.error("stale_initiated_orders_cleanup_error", e);
        }

        // ============================================================
        // PHASE 3: End calls with single participant for too long
        // If only one participant has joined for > SINGLE_PARTICIPANT_TIMEOUT_SECONDS, end the call
        // ============================================================
        int singleParticipantEndedCount = 0;
        try {
            LoggingService.info("checking_single_participant_calls");
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);

            // Query all CONNECTED on-demand consultations
            Query connectedQuery = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("status", "CONNECTED");

            for (QueryDocumentSnapshot doc : connectedQuery.get().get().getDocuments()) {
                try {
                    String orderId = doc.getId();
                    String userId = doc.getReference().getParent().getParent().getId();
                    String expertId = doc.getString("expert_id");

                    // Check participant join times
                    com.google.cloud.Timestamp userJoinedAt = doc.getTimestamp("user_joined_at");
                    com.google.cloud.Timestamp expertJoinedAt = doc.getTimestamp("expert_joined_at");
                    com.google.cloud.Timestamp bothJoinedAt = doc.getTimestamp("both_participants_joined_at");
                    com.google.cloud.Timestamp startTime = doc.getTimestamp("start_time");

                    // Skip if both participants have joined (billing is happening normally)
                    if (bothJoinedAt != null) {
                        continue;
                    }

                    // Check if only one participant has been in the call for too long
                    boolean shouldEnd = false;
                    String endReason = null;

                    // If user joined but expert hasn't joined for > timeout
                    if (userJoinedAt != null && expertJoinedAt == null) {
                        long waitingSeconds = (nowMillis - userJoinedAt.toDate().getTime()) / 1000;
                        if (waitingSeconds >= SINGLE_PARTICIPANT_TIMEOUT_SECONDS) {
                            shouldEnd = true;
                            endReason = "USER_WAITING_TIMEOUT";
                            LoggingService.info("single_participant_timeout_user_waiting", Map.of(
                                "orderId", orderId,
                                "waitingSeconds", waitingSeconds
                            ));
                        }
                    }

                    // If expert joined but user hasn't joined for > timeout (less common)
                    if (expertJoinedAt != null && userJoinedAt == null) {
                        long waitingSeconds = (nowMillis - expertJoinedAt.toDate().getTime()) / 1000;
                        if (waitingSeconds >= SINGLE_PARTICIPANT_TIMEOUT_SECONDS) {
                            shouldEnd = true;
                            endReason = "EXPERT_WAITING_TIMEOUT";
                            LoggingService.info("single_participant_timeout_expert_waiting", Map.of(
                                "orderId", orderId,
                                "waitingSeconds", waitingSeconds
                            ));
                        }
                    }

                    // If neither has joined but the order has been CONNECTED for > timeout
                    // (This shouldn't normally happen, but handle it as a safety net)
                    if (userJoinedAt == null && expertJoinedAt == null && startTime != null) {
                        long waitingSeconds = (nowMillis - startTime.toDate().getTime()) / 1000;
                        if (waitingSeconds >= SINGLE_PARTICIPANT_TIMEOUT_SECONDS * 2) {
                            shouldEnd = true;
                            endReason = "NO_PARTICIPANTS_TIMEOUT";
                            LoggingService.info("single_participant_timeout_no_participants", Map.of(
                                "orderId", orderId,
                                "waitingSeconds", waitingSeconds
                            ));
                        }
                    }

                    if (shouldEnd) {
                        LoggingService.setContext(userId, orderId, expertId);
                        LoggingService.info("ending_single_participant_call", Map.of("reason", endReason));

                        // Mark order as FAILED with reason
                        DocumentReference orderRef = doc.getReference();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", "FAILED");
                        updates.put("end_time", com.google.cloud.Timestamp.now());
                        updates.put("failure_reason", endReason);
                        // No billing since both participants never joined together
                        updates.put("cost", 0.0);
                        updates.put("duration_seconds", 0L);
                        updates.put("billable_seconds", 0L);
                        orderRef.update(updates).get();

                        // Free expert
                        if (expertId != null) {
                            boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
                            if (!hasOtherActive) {
                                walletService.setConsultationStatus(expertId, "FREE");
                            }
                        }

                        // End the Stream call if it exists
                        String streamCallCid = doc.getString("stream_call_cid");
                        if (streamCallCid != null) {
                            try {
                                StreamService streamService = new StreamService(isTest());
                                String[] cidParts = StreamService.parseCallCid(streamCallCid);
                                if (cidParts != null) {
                                    streamService.endCall(cidParts[0], cidParts[1]);
                                }
                            } catch (Exception streamError) {
                                LoggingService.warn("failed_to_end_stream_call_single_participant", Map.of(
                                    "streamCallCid", streamCallCid,
                                    "error", streamError.getMessage()
                                ));
                            }
                        }

                        singleParticipantEndedCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    LoggingService.error("single_participant_check_error", e, Map.of(
                        "orderId", doc.getId()
                    ));
                }
            }
        } catch (Exception e) {
            LoggingService.error("single_participant_cleanup_error", e);
        }

        // ============================================================
        // PHASE 4: Free orphaned BUSY experts (with improved staleness checks)
        // ============================================================
        int freedExpertsCount = 0;
        try {
            LoggingService.info("checking_orphaned_busy_experts");
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db);

            // Query for experts who are currently BUSY
            Query busyExpertsQuery = this.db.collectionGroup("store")
                    .whereEqualTo("consultation_status", "BUSY");

            for (QueryDocumentSnapshot doc : busyExpertsQuery.get().get().getDocuments()) {
                try {
                    // Extract expert ID from document path: users/{expertId}/public/store
                    String expertId = doc.getReference().getParent().getParent().getParent().getId();
                    LoggingService.setExpertId(expertId);

                    // Get BUSY status timestamp for staleness check
                    com.google.cloud.Timestamp busyStatusUpdatedAt = doc.getTimestamp("consultation_status_updated_at");
                    boolean isBusyStatusStale = false;
                    if (busyStatusUpdatedAt != null) {
                        long busyDurationSeconds = (nowMillis - busyStatusUpdatedAt.toDate().getTime()) / 1000;
                        isBusyStatusStale = busyDurationSeconds >= BUSY_STATUS_STALENESS_SECONDS;
                        if (isBusyStatusStale) {
                            LoggingService.info("busy_status_is_stale", Map.of(
                                "busyDurationSeconds", busyDurationSeconds,
                                "threshold", BUSY_STATUS_STALENESS_SECONDS
                            ));
                        }
                    }

                    // Check for CONNECTED consultations
                    boolean hasActiveConnected = false;
                    Query activeConnectedQuery = this.db.collectionGroup("orders")
                            .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                            .whereEqualTo("expertId", expertId)
                            .whereEqualTo("status", "CONNECTED");

                    if (!activeConnectedQuery.get().get().isEmpty()) {
                        hasActiveConnected = true;
                    }

                    // Check for non-stale INITIATED consultations
                    boolean hasActiveInitiated = false;
                    if (!hasActiveConnected) {
                        hasActiveInitiated = hasNonStaleInitiatedOrders(expertId, null, nowMillis);
                    }

                    // Decision: Free expert if:
                    // 1. No active CONNECTED orders AND no non-stale INITIATED orders, OR
                    // 2. BUSY status is stale (safety fallback)
                    boolean shouldFreeExpert = false;
                    String freeReason = null;

                    if (!hasActiveConnected && !hasActiveInitiated) {
                        shouldFreeExpert = true;
                        freeReason = "no_active_consultations";
                    } else if (isBusyStatusStale && !hasActiveConnected) {
                        // If BUSY for too long but no CONNECTED calls, free even if there are stale INITIATED
                        shouldFreeExpert = true;
                        freeReason = "busy_status_stale_no_connected";
                    }

                    if (shouldFreeExpert) {
                        LoggingService.info("freeing_orphaned_busy_expert", Map.of(
                            "reason", freeReason
                        ));
                        walletService.setConsultationStatus(expertId, "FREE");
                        freedExpertsCount++;
                        LoggingService.info("expert_freed_successfully");
                    } else {
                        LoggingService.debug("expert_has_active_consultations_keeping_busy", Map.of(
                            "expertId", expertId,
                            "hasActiveConnected", hasActiveConnected,
                            "hasActiveInitiated", hasActiveInitiated
                        ));
                    }
                } catch (Exception e) {
                    LoggingService.error("error_checking_freeing_expert", e, Map.of(
                        "expertId", doc.getReference().getParent().getParent().getParent().getId()
                    ));
                }
            }
        } catch (Exception e) {
            LoggingService.error("orphaned_busy_expert_cleanup_error", e);
        }

        // =====================================================================
        // PROCESS PENDING SUMMARY GENERATIONS
        // =====================================================================
        int summariesProcessed = 0;
        int summariesSucceeded = 0;
        int summariesFailed = 0;
        int summariesSkipped = 0;

        try {
            LoggingService.setFunction("process_pending_summaries");
            ConsultationSummaryService summaryService = new ConsultationSummaryService(db, isTest());

            // Process up to 5 summaries per cron run to avoid timeout
            // Each summary can take 30-60 seconds to process
            Map<String, Integer> summaryResults = summaryService.processPendingSummaries(5);

            summariesProcessed = summaryResults.getOrDefault("processed", 0);
            summariesSucceeded = summaryResults.getOrDefault("succeeded", 0);
            summariesFailed = summaryResults.getOrDefault("failed", 0);
            summariesSkipped = summaryResults.getOrDefault("skipped", 0);

        } catch (Exception e) {
            LoggingService.error("process_pending_summaries_error", e);
        }

        LoggingService.setFunction("auto_terminate_consultations");
        LoggingService.info("auto_terminate_job_completed", Map.of(
            "terminatedCount", terminatedCount,
            "failedInitiatedCount", failedInitiatedCount,
            "singleParticipantEndedCount", singleParticipantEndedCount,
            "freedExpertsCount", freedExpertsCount,
            "errorCount", errorCount,
            "summariesProcessed", summariesProcessed,
            "summariesSucceeded", summariesSucceeded,
            "summariesFailed", summariesFailed,
            "summariesSkipped", summariesSkipped
        ));
        return gson.toJson(Map.of(
            "success", true,
            "terminatedCount", terminatedCount,
            "failedInitiatedCount", failedInitiatedCount,
            "singleParticipantEndedCount", singleParticipantEndedCount,
            "freedExpertsCount", freedExpertsCount,
            "errorCount", errorCount,
            "summariesProcessed", summariesProcessed,
            "summariesSucceeded", summariesSucceeded,
            "summariesFailed", summariesFailed,
            "summariesSkipped", summariesSkipped
        ));
    }

    /**
     * Check if an expert has any non-stale INITIATED orders.
     * An order is considered stale if it's older than INITIATED_ORDER_TIMEOUT_SECONDS.
     *
     * @param expertId The expert ID to check
     * @param excludeOrderId Optional order ID to exclude from the check
     * @param nowMillis Current time in milliseconds
     * @return true if there are non-stale INITIATED orders
     */
    private boolean hasNonStaleInitiatedOrders(String expertId, String excludeOrderId, long nowMillis) {
        try {
            Query initiatedQuery = this.db.collectionGroup("orders")
                    .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                    .whereEqualTo("expertId", expertId)
                    .whereEqualTo("status", "INITIATED");

            for (QueryDocumentSnapshot doc : initiatedQuery.get().get().getDocuments()) {
                String orderId = doc.getId();
                if (excludeOrderId != null && orderId.equals(excludeOrderId)) {
                    continue;
                }

                com.google.cloud.Timestamp createdAt = doc.getTimestamp("created_at");
                if (createdAt == null) {
                    // If no created_at, assume it's recent and count it
                    return true;
                }

                long elapsedSeconds = (nowMillis - createdAt.toDate().getTime()) / 1000;
                if (elapsedSeconds < INITIATED_ORDER_TIMEOUT_SECONDS) {
                    // Found a non-stale INITIATED order
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LoggingService.error("error_checking_non_stale_initiated_orders", e, Map.of(
                "expertId", expertId
            ));
            // On error, assume there might be active orders to avoid incorrectly freeing expert
            return true;
        }
    }

    public class HandlerException extends Exception {
        ErrorCode errorCode;

        public HandlerException(ErrorCode errorCode) {
            super();
            this.errorCode = errorCode;
        }
    }
}
