package in.co.kitree;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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
    
    Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private Firestore db;
    private Razorpay razorpay;
    //    private StripeService stripeService;
    private static LambdaLogger logger;
    protected PythonLambdaService pythonLambdaService;
    private AstrologyService astrologyService;

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
        } catch (Exception e) {
            System.out.println("Error initializing Handler: " + e.getMessage());
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
                    System.out.println("Failed to initialize Firebase in test environment: " + firebaseEx.getMessage());
                    throw new RuntimeException("Failed to initialize Firebase in test environment", firebaseEx);
                }

                try {
                    this.razorpay = new Razorpay(true);
                } catch (RazorpayException ex) {
                    System.out.println("Warning: Could not initialize Razorpay in test environment: " + ex.getMessage());
                }
                try {
                    // Set default region for testing
                    System.setProperty("aws.region", "ap-south-1");
                    this.pythonLambdaService = createPythonLambdaService();
                } catch (Exception ex) {
                    System.out.println("Warning: Could not initialize pythonLambdaService in test environment: " + ex.getMessage());
                }
                try {
                    this.astrologyService = new AstrologyService();
                } catch (Exception ex) {
                    System.out.println("Warning: Could not initialize astrologyService in test environment: " + ex.getMessage());
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
                    logger.log(String.format("Failed to invoke Python Lambda. Exception type: %s, Message: %s", e.getClass().getName(), e.getMessage()));
                    throw new RuntimeException("Failed to invoke Python Lambda", e);
                }
            }
        };
    }

    public String handleRequest(RequestEvent event, Context context) {
        logger = context.getLogger();
        try {
            if ("aws.events".equals(event.getSource())) {
                // Check if this is a scheduled auto-terminate event
                String detailType = event.getDetailType();
                if ("auto_terminate_consultations".equals(detailType)) {
                    logger.log("Running auto-terminate consultations cron job...\n");
                    return handleAutoTerminateConsultations();
                }
                logger.log("warmed up\n");
                return "Warmed up!";
            }
            System.out.println("event body: " + event.getBody());
            RequestBody requestBody = this.gson.fromJson(event.getBody(), RequestBody.class);
            System.out.println("env is test: " + isTest());

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
                orderDetails.put("createdAt", new Timestamp(System.currentTimeMillis()));
                orderDetails.put("userName", user.getName());
                orderDetails.put("userId", userId);
                orderDetails.put("userPhoneNumber", user.getPhoneNumber());
                orderDetails.put("amount", servicePlan.getAmount());
                orderDetails.put("currency", servicePlan.getCurrency());
                orderDetails.put("planId", requestBody.getPlanId());
                orderDetails.put("type", servicePlan.getType());
                orderDetails.put("expertId", requestBody.getExpertId());
                orderDetails.put("expertName", expert.getName());
                if (servicePlan.getType().equals("DIGITAL_PRODUCT")) {
                    orderDetails.put("subtype", servicePlan.getSubtype());
                }
                if (servicePlan.getCategory().equals("CUSTOMIZED_BRACELET") && requestBody.getBeads() != null && !requestBody.getBeads().isEmpty() && requestBody.getAddress() != null) {
                    orderDetails.put("beads", requestBody.getBeads());
                    orderDetails.put("address", requestBody.getAddress());
                    orderDetails.put("currency", "INR");
                }

                if (!servicePlan.getType().equals("WEBINAR")) {
                    orderDetails.put("isVideo", servicePlan.isVideo());
                    orderDetails.put("category", servicePlan.getCategory()); // Rename service to category in frontend orders screen.
                } else {
                    orderDetails.put("date", servicePlan.getDate());
                    orderDetails.put("title", servicePlan.getTitle());
                    if (servicePlan.getSessionStartedAt() != null) {
                        orderDetails.put("sessionStartedAt", servicePlan.getSessionStartedAt());
                    }
                    if (servicePlan.getSessionCompletedAt() != null) {
                        orderDetails.put("sessionCompletedAt", servicePlan.getSessionCompletedAt());
                    }
                }

                if (servicePlan.getAmount() == null || servicePlan.getAmount() <= 0) {
                    String orderId = UUID.randomUUID().toString();
                    orderDetails.put("orderId", orderId);
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
                        orderDetails.put("couponCode", requestBody.getCouponCode());
                        orderDetails.put("originalAmount", servicePlan.getAmount());
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
                        System.out.println("referrals: " + referrals);
                        if (referrals == null || referrals.isEmpty()) {
                            DocumentSnapshot referralDetails = this.db.collection("users").document(requestBody.getExpertId()).collection("public").document("store").get().get();
                            int referredDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referredDiscount", 0)));
                            int referrerDiscount = Integer.parseInt(String.valueOf(Objects.requireNonNull(referralDetails.getData()).getOrDefault("referrerDiscount", 0)));
                            System.out.println("referredDiscount: " + referredDiscount);
                            orderDetails.put("referredBy", referredBy);
                            orderDetails.put("referrerDiscount", referrerDiscount);
                            orderDetails.put("referredDiscount", referredDiscount);
                            if (orderDetails.get("couponCode") == null && referredDiscount > 0 && referredDiscount <= 100) {
                                orderDetails.put("originalAmount", servicePlan.getAmount());
                                Double newAmount = servicePlan.getAmount() * (1.00 - referredDiscount / 100.0);
                                orderDetails.put("amount", newAmount);
                                orderDetails.put("discount", servicePlan.getAmount() - (Double) orderDetails.get("amount"));
                                servicePlan.setAmount(newAmount);
                            }
                        }
                    }
                }

                Map<String, String> response = new HashMap<>();
                if (servicePlan.getCurrency().equals("INR")) {
                    if (servicePlan.isSubscription()) {
                        String subscriptionId = razorpay.createSubscription(servicePlan.getRazorpayId(), CustomerCipher.encryptCaesarCipher(userId));

                        orderDetails.put("subscription", true);
                        orderDetails.put("payment_gateway", "RAZORPAY");
                        createOrderInDB(userId, subscriptionId, orderDetails);

                        response.put("subscription_id", subscriptionId);
                        response.put("payment_gateway", "RAZORPAY");
                        response.put("razorpay_key", razorpay.getRazorpayKey());
                    } else {
                        String orderId = razorpay.createOrder(servicePlan.getAmount(), CustomerCipher.encryptCaesarCipher(userId));

                        orderDetails.put("subscription", false);
                        orderDetails.put("payment_gateway", "RAZORPAY");
                        createOrderInDB(userId, orderId, orderDetails);

                        response.put("order_id", orderId);
                        response.put("payment_gateway", "RAZORPAY");
                        response.put("razorpay_key", razorpay.getRazorpayKey());
                    }
                } else {
//                    Map<String, String> paymentIntent = stripeService.createPaymentIntent(servicePlan.getAmount(), servicePlan.getCurrency());
//
//                    String orderId = UUID.randomUUID().toString();
//                    orderDetails.put("orderId", orderId);
//                    orderDetails.put("subscription", false);
//                    orderDetails.put("payment_gateway", "STRIPE");
//                    createOrderInDB(userId, orderId, orderDetails);
//
//                    response.put("payment_intent_client_secret", paymentIntent.get("paymentIntent"));
//                    response.put("payment_gateway", "STRIPE");
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
                        System.out.println("referrals: " + referrals);
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
                System.out.println(userId);
                System.out.println(url);
                this.db.collection("users").document(userId).collection("public").document("store").set(Map.of("displayPicture", url), SetOptions.merge()).get();
            }

            if ("verify_payment".equals(requestBody.getFunction())) {

                if (requestBody.getRazorpaySubscriptionId() != null) { // TODO: No action here as webhooks will verify and update the status in DB
                    if (razorpay.verifySubscription(requestBody.getRazorpaySubscriptionId())) {
//                        verifyOrderInDB(userId, requestBody.getRazorpaySubscriptionId());
                        incrementCouponUsageCount(userId, requestBody.getRazorpaySubscriptionId());
                        rewardReferrer(userId, requestBody.getRazorpaySubscriptionId());
                        return "Verified";
                    }
                } else if (requestBody.getRazorpayOrderId() != null) {
                    if (razorpay.verifyPayment(requestBody.getRazorpayOrderId(), requestBody.getRazorpayPaymentId(), requestBody.getRazorpaySignature())) {

                        if ("gift".equals(requestBody.getType())) {
                            // TODO: Shall we get the expert and plan id from frontend or not? Also, add validations

                            String expertId = requestBody.getExpertId();
                            String webinarId = requestBody.getPlanId();
                            String orderId = requestBody.getOrderId(); // Order ID of the webinar
                            String giftOrderId = requestBody.getRazorpayOrderId(); // Order ID of the gift within the webinar
                            addWebinarGiftDetails(userId, expertId, webinarId, orderId, giftOrderId);
                            return "Verified";
                        }
                        verifyOrderInDB(userId, requestBody.getRazorpayOrderId());
                        NotificationService.sendNotification("f0JuQoCUQQ68I-tHqlkMxm:APA91bHZqzyL-xZG_g4qXhZyT9SP8jSh5hRJ8_21Ux9YPvcqzC7wi_tC9eKD6uZi52BndchctrXsINOmoo8A4OTn79oZkiwMeXPmcauVbgIXNEk_Qh7xFQc");
                        NotificationService.sendNotification("fdljkc67TkI6iH-obDwVHR:APA91bGrUdmUGsI-SudlhQnrGasRTgiosL46ISzeudbcoXrzpNgz1Uu0y0c0WMZHtCt0ct5UwWN9kVFx3TJRuhTuxXjuay-6otAFO1uBaJNo8nz1VOAobbc");
                        NotificationService.sendNotification("eX4C_MX0Q8e2yzwheVUx7a:APA91bFWnho4d3Mbx8EpAgJMGHzOdNzCb3O2fl3DdC1Rx92cMYZDSKIRFx2A-pR20BFUiXGL3qZMu64uGNJYzxAjOx9KG7cr-D8EIvGsOGiKI3EPWFJrU2c");

                        // TODO: Make the referral Async, add error handling; make it a transaction with order verification
                        incrementCouponUsageCount(userId, requestBody.getRazorpayOrderId());
                        rewardReferrer(userId, requestBody.getRazorpayOrderId());
//                        fulfillDigitalOrders(userId, requestBody.getRazorpayOrderId());
                        return "Verified";
                    }
                } else {
                    return "Can't verify!";
                }
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
                        ordersQuery = ordersQuery.orderBy("createdAt", Query.Direction.DESCENDING);

                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

                        Date startDate = dateFormat.parse(requestBody.getDateRangeFilter().get(0));
                        Timestamp startTimestamp = new Timestamp(startDate.getTime());

                        Date endDate = dateFormat.parse(requestBody.getDateRangeFilter().get(1));
                        Timestamp endTimestamp = new Timestamp(endDate.getTime());

                        ordersQuery = ordersQuery.whereGreaterThanOrEqualTo("createdAt", startTimestamp);
                        ordersQuery = ordersQuery.whereLessThan("createdAt", endTimestamp);

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
                    if (!(checkIfOrderOwnedByUser(requestBody.getRazorpaySubscriptionId(), userId) || isAdmin(userId))) {
                        return "Not authorized";
                    }
                    razorpay.cancel(requestBody.getRazorpaySubscriptionId());
                    cancelSubscriptionInDb(userId, requestBody.getRazorpaySubscriptionId());
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
                    logger.log("ERROR: Failed to get courses from Python Lambda.");
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
                    logger.log("ERROR: Missing required fields for generate_aura_report");
                    return gson.toJson(Map.of("success", false, "errorMessage", "Missing required user details or scanner data."));
                }
                // Check if user is admin
                if (!isAdmin(userId)) {
                    logger.log("WARN: Non-admin user attempted to generate aura report.");
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
                    logger.log("Aura report generated successfully. Link: " + pythonResponse.getAuraReportLink());
                    // Return only the link in a simple map for the frontend
                    return gson.toJson(Map.of("auraReportLink", pythonResponse.getAuraReportLink()));
                } else {
                    logger.log("ERROR: Failed to get aura report link from Python Lambda.");
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

            if ("create_webinar".equals(requestBody.getFunction())) {
                // Create webinar room and start webinar
                // TODO: Currently anybody can join the webinar, we need to manage permissions at Stream's end.
                String webinarId = requestBody.getPlanId();
                if (webinarId == null) {
                    return "Not valid";
                }
                ServicePlan servicePlan = getPlanDetails(webinarId, userId);
                if (servicePlan == null) {
                    return "Not authorized";
                }

                if (!Objects.equals(servicePlan.getType(), "WEBINAR")) {
                    return "Not valid";
                }

                // TODO: Move sessionStartedAt in webinar plan to backend and check if it is already set, then early return.

                PythonLambdaEventRequest createCallEvent = new PythonLambdaEventRequest();
                createCallEvent.setFunction("create_call");

                createCallEvent.setType("WEBINAR");
                createCallEvent.setExpertId(userId);
                createCallEvent.setWebinarId(webinarId);
                createCallEvent.setExpertName(requestBody.getExpertName());
                createCallEvent.setTest(isTest());

                // TODO: Check response from python lambda
                pythonLambdaService.invokePythonLambda(createCallEvent);

                this.db.collection("users").document(userId).collection("plans").document(webinarId).collection("metadata").document("gifts").set(new HashMap<>(), SetOptions.merge()).get();

                // TODO: Needs to be done in O(1). Another way is to do to have a snapshot listeners on webinar plans
                //  in frontend for webinars that are supposed to happen on that particular day.
                Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
                ApiFuture<QuerySnapshot> queryFuture = this.db.collectionGroup("orders").whereEqualTo("planId", webinarId).whereEqualTo("expertId", userId).whereEqualTo("type", "WEBINAR")
//                        .orderBy("paymentReceivedAt") TODO: Race condition
                        .get();

                QuerySnapshot querySnapshot = queryFuture.get();
                for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                    document.getReference().update("sessionStartedAt", currentTimestamp).get();
                }
                return "Success";
            }

            if ("stop_webinar".equals(requestBody.getFunction())) {
                String webinarId = requestBody.getPlanId();
                if (webinarId == null) {
                    return "Not valid";
                }
                ServicePlan servicePlan = getPlanDetails(webinarId, userId);
                if (servicePlan == null) {
                    return "Not authorized";
                }

                if (!Objects.equals(servicePlan.getType(), "WEBINAR")) {
                    return "Not valid";
                }

                // TODO: Move sessionCompletedAt in webinar plan to backend and check if it is already set, then early return.

                // TODO: Needs to be done in O(1). Another way is to do to have a snapshot listeners on webinar plans
                //  in frontend for webinars that are supposed to happen on that particular day.
                Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
                ApiFuture<QuerySnapshot> queryFuture = this.db.collectionGroup("orders").whereEqualTo("planId", webinarId).whereEqualTo("expertId", userId).whereEqualTo("type", "WEBINAR").orderBy("paymentReceivedAt").get();

                QuerySnapshot querySnapshot = queryFuture.get();
                for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                    document.getReference().update("sessionCompletedAt", currentTimestamp).get();
                }
                return "Success";
            }

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
                System.out.println("order: " + order);
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
                System.out.println("availabilityTimeZone: " + availabilityTimeZone);
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

            if ("wallet_balance".equals(requestBody.getFunction())) {
                return handleWalletBalance(userId, requestBody);
            }

            if ("create_wallet_recharge_order".equals(requestBody.getFunction())) {
                return handleCreateWalletRechargeOrder(userId, requestBody);
            }

            if ("verify_wallet_recharge".equals(requestBody.getFunction())) {
                return handleVerifyWalletRecharge(userId, requestBody);
            }

            // =====================================================================
            // ON-DEMAND CONSULTATION ENDPOINTS
            // =====================================================================

            if ("on_demand_consultation_initiate".equals(requestBody.getFunction())) {
                return handleOnDemandConsultationInitiate(userId, requestBody);
            }

            if ("on_demand_consultation_connect".equals(requestBody.getFunction())) {
                return handleOnDemandConsultationConnect(userId, requestBody);
            }

            if ("on_demand_consultation_heartbeat".equals(requestBody.getFunction())) {
                return handleOnDemandConsultationHeartbeat(userId, requestBody);
            }

            if ("update_consultation_max_duration".equals(requestBody.getFunction())) {
                return handleUpdateConsultationMaxDuration(userId, requestBody);
            }

            if ("on_demand_consultation_end".equals(requestBody.getFunction())) {
                return handleOnDemandConsultationEnd(userId, requestBody);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
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
            System.err.println("Token verification failed: " + e.getMessage());
            // Return null - caller will handle the error
            return null;
        }
    }

    private void addWebinarGiftDetails(String userId, String expertId, String webinarId, String orderId, String giftOrderId) {
        try {
            DocumentReference documentReference = this.db.collection("users").document(userId).collection("orders").document(orderId);
            DocumentSnapshot document = documentReference.get().get();
            if (document == null || !document.exists()) {
                return;
            }
            Map<String, Object> orderDetails = document.getData();
            if (orderDetails == null) {
                return;
            }
            List<Map<String, Object>> giftDetails = (List<Map<String, Object>>) orderDetails.getOrDefault("gifts", new ArrayList<Map<String, Object>>());
            System.out.println("giftDetails: " + giftDetails);
            for (Map<String, Object> gift : giftDetails) {
                System.out.println("gift: " + gift);
                if (!gift.get("gift_order_id").equals(giftOrderId)) {
                    continue;
                }
                System.out.println("gift3: " + gift);
                System.out.println("gift2: " + gift);
                gift.put("paymentReceivedAt", new Timestamp(System.currentTimeMillis()));
                documentReference.update("gifts", giftDetails).get();

                this.db.collection("users").document(expertId).collection("plans").document(webinarId).collection("metadata").document("gifts").update("totalAmount", FieldValue.increment((Long) gift.getOrDefault("amount", 0L))).get();
                break;
            }
        } catch (Exception e) {
            System.out.println("Error in updating gift details: " + userId + " " + orderId + " " + e.getMessage());
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
            System.out.println("Error in getting overrides: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean checkIfOrderOwnedByUser(String razorpaySubscriptionId, String userId) {
        try {
            this.db.collection("users").document(userId).collection("orders").document(razorpaySubscriptionId).get().get();
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
        System.out.println(data);

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
        System.out.println("Type: " + data.get("type").toString());
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
        System.out.println("Coupon: " + coupon);
        ServicePlan servicePlan = getPlanDetails(planName, expertId);

        FirebaseUser user = new FirebaseUser();
        Map<String, Long> userCouponFrequency = new HashMap<>();
        long count = this.db // TODO: We do not want this query everytime. We can optimize it.
                .collectionGroup("orders").whereEqualTo("couponCode", couponCode).whereEqualTo("userId", userId).whereEqualTo("expertId", expertId).orderBy("paymentReceivedAt", Query.Direction.DESCENDING).count().get().get().getCount();
        userCouponFrequency.put(couponCode, count);
        user.setCouponUsageFrequency(userCouponFrequency);
        user.setUid(userId);
        System.out.println("Coupon usage frequency: " + user.getCouponUsageFrequency());
        System.out.println("User: " + user);
        System.out.println("Coupon: " + coupon);
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
            String expertId = subscriptionSnapshot.getString("expertId");
            String category = subscriptionSnapshot.getString("category");

            long currentEnd = razorpayWebhookBody.getJSONObject("payload").getJSONObject("subscription").getJSONObject("entity").getLong("current_end");
            Timestamp currentEndTime = new Timestamp(currentEnd * 1000);
            Map<String, Object> subscriptionFields = new HashMap<>();
            Map<String, Object> subscriptionPaymentsFields = new HashMap<>();
            Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());

            subscriptionFields.put("currentEndTime", currentEndTime); // TODO: Maybe want to check that the subscription state is active
            subscriptionFields.put("paymentReceivedAt", currentTimestamp);
            subscriptionPaymentsFields.put("paymentReceivedAt", currentTimestamp);
            subscriptionPaymentsFields.put("amount", amount);
            subscriptionPaymentsFields.put("currency", currency);
            subscriptionPaymentsFields.put("expertId", expertId);
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
        ApiFuture<WriteResult> future = this.db.collection("users").document(userId).collection("orders").document(subscriptionId).update("cancelledAt", new Timestamp(System.currentTimeMillis()));
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
            System.out.println("Document data: " + documentSnapshot.getData());
            servicePlan.setAmount(((Long) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("amount", 0L)).doubleValue());
            servicePlan.setCurrency((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("currency", ""));
            servicePlan.setSubscription((Boolean) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("isSubscription", false));
            servicePlan.setVideo((Boolean) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("isVideo", false));
            servicePlan.setRazorpayId((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("razorpayId", ""));
            servicePlan.setType((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("type", ""));
            servicePlan.setSubtype((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("subtype", ""));
            servicePlan.setCategory((String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("category", ""));
            servicePlan.setDuration((Long) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("duration", 30)); // TODO: Default value
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
        }

        System.out.println("service plan: " + servicePlan);
        return servicePlan;
    }

    private boolean isTest() {
        return "test".equals(System.getenv("ENVIRONMENT"));
    }

    private boolean isAdmin(String userId) throws FirebaseAuthException {
        return Boolean.TRUE.equals(FirebaseAuth.getInstance().getUser(userId).getCustomClaims().get("admin"));
    }

    private FirebaseOrder fetchOrder(String clientId, String orderId) {
        System.out.println("fetchOrder: " + clientId + " " + orderId);
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
            System.out.println("Error in fetching order: " + e.getMessage());
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
            System.out.println("Error in fetching expert store details: " + e.getMessage()); // TODO: Sentry
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
            System.out.println("Gochar API response: " + response);
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
        int terminatedCount = 0;
        int errorCount = 0;
        
        try {
            WalletService walletService = new WalletService(this.db);
            OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);
            
            // Query all CONNECTED on-demand consultations
            // Using collectionGroup query to search across all users' orders
            com.google.cloud.Timestamp now = com.google.cloud.Timestamp.now();
            long nowMillis = now.toDate().getTime();
            
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
                        logger.log("Skipping order " + orderId + " - missing start_time or max_allowed_duration");
                        continue;
                    }
                    
                    // Calculate elapsed time
                    long startTimeMillis = startTime.toDate().getTime();
                    long elapsedSeconds = (nowMillis - startTimeMillis) / 1000;
                    
                    // Add grace period
                    if (elapsedSeconds >= (maxAllowedDuration + AUTO_TERMINATE_GRACE_PERIOD_SECONDS)) {
                        logger.log("Auto-terminating consultation: " + orderId + 
                                " (elapsed: " + elapsedSeconds + "s, max: " + maxAllowedDuration + "s)");
                        
                        // End the consultation
                        OnDemandConsultationOrder order = consultationService.getOrder(userId, orderId);
                        if (order != null && "CONNECTED".equals(order.getStatus())) {
                            // Calculate final values
                            Long durationSeconds = consultationService.calculateElapsedSeconds(order);
                            Double cost = consultationService.calculateCost(durationSeconds, order.getExpertRatePerMinute());
                            Double platformFeeAmount = consultationService.calculatePlatformFee(cost, order.getPlatformFeePercent());
                            Double expertEarnings = cost - platformFeeAmount;
                            
                            String expertId = order.getExpertId();
                            String currency = order.getCurrency();
                            
                            final Long finalDurationSeconds = durationSeconds;
                            final Double finalCost = cost;
                            final Double finalPlatformFeeAmount = platformFeeAmount;
                            final Double finalExpertEarnings = expertEarnings;
                            final String finalUserId = userId;
                            final String finalOrderId = orderId;
                            final String finalExpertId = expertId;
                            final String finalCurrency = currency;
                            
                            // Check for other active consultations BEFORE transaction (to avoid race condition)
                            boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(finalExpertId, finalOrderId);
                            final boolean finalHasOtherActive = hasOtherActive;
                            
                            this.db.runTransaction(transaction -> {
                                // Deduct from user wallet
                                walletService.updateWalletBalanceInTransaction(
                                    transaction, finalUserId, finalCurrency, -finalCost
                                );
                                
                                // Credit expert wallet
                                walletService.updateWalletBalanceInTransaction(
                                    transaction, finalExpertId, finalCurrency, finalExpertEarnings
                                );
                                
                                // Create deduction transaction for user
                                WalletTransaction deductionTransaction = new WalletTransaction();
                                deductionTransaction.setType("CONSULTATION_DEDUCTION");
                                deductionTransaction.setSource("PAYMENT");
                                deductionTransaction.setAmount(-finalCost);
                                deductionTransaction.setCurrency(finalCurrency);
                                deductionTransaction.setOrderId(finalOrderId);
                                deductionTransaction.setStatus("COMPLETED");
                                deductionTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
                                deductionTransaction.setDescription("On-demand consultation charge (auto-terminated)");
                                
                                walletService.createWalletTransactionInTransaction(transaction, finalUserId, deductionTransaction);
                                
                                // Create credit transaction for expert
                                WalletTransaction creditTransaction = new WalletTransaction();
                                creditTransaction.setType("ORDER_EARNING");
                                creditTransaction.setSource("PAYMENT");
                                creditTransaction.setAmount(finalExpertEarnings);
                                creditTransaction.setCurrency(finalCurrency);
                                creditTransaction.setOrderId(finalOrderId);
                                creditTransaction.setStatus("COMPLETED");
                                creditTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
                                creditTransaction.setDescription("On-demand consultation earning (auto-terminated)");
                                
                                walletService.createWalletTransactionInTransaction(transaction, finalExpertId, creditTransaction);
                                
                                // Update order
                                consultationService.completeOrderInTransaction(
                                    transaction, finalUserId, finalOrderId,
                                    finalDurationSeconds, finalCost, finalPlatformFeeAmount, finalExpertEarnings
                                );
                                
                                // Set expert status back to ONLINE if no other active consultations
                                if (!finalHasOtherActive) {
                                    walletService.setExpertStatusInTransaction(transaction, finalExpertId, "ONLINE");
                                }
                                
                                return null;
                            }).get();
                            
                            terminatedCount++;
                            logger.log("Successfully auto-terminated consultation: " + orderId);
                        }
                    }
                } catch (Exception e) {
                    errorCount++;
                    logger.log("Error auto-terminating consultation " + doc.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            logger.log("Error in handleAutoTerminateConsultations: " + e.getMessage());
            e.printStackTrace();
            return gson.toJson(Map.of(
                "success", false,
                "errorMessage", e.getMessage()
            ));
        }
        
        logger.log("Auto-terminate job completed. Terminated: " + terminatedCount + ", Errors: " + errorCount);
        return gson.toJson(Map.of(
            "success", true,
            "terminatedCount", terminatedCount,
            "errorCount", errorCount
        ));
    }

    public class HandlerException extends Exception {
        ErrorCode errorCode;

        public HandlerException(ErrorCode errorCode) {
            super();
            this.errorCode = errorCode;
        }
    }

    // =====================================================================
    // WALLET MANAGEMENT HANDLER METHODS
    // =====================================================================

    /**
     * Get wallet balance for a user.
     * Returns all currency balances or a specific currency if provided.
     */
    private String handleWalletBalance(String userId, RequestBody requestBody) throws ExecutionException, InterruptedException {
        WalletService walletService = new WalletService(this.db);
        String currency = requestBody.getCurrency();
        
        if (currency != null && !currency.isEmpty()) {
            // Return specific currency balance
            Double balance = walletService.getWalletBalance(userId, currency);
            return gson.toJson(Map.of(
                "success", true,
                "balance", balance,
                "currency", currency
            ));
        } else {
            // Return all currency balances
            Map<String, Double> balances = walletService.getWalletBalances(userId);
            String defaultCurrency = walletService.getUserDefaultCurrency(userId);
            return gson.toJson(Map.of(
                "success", true,
                "balances", balances,
                "defaultCurrency", defaultCurrency
            ));
        }
    }

    /**
     * Create a Razorpay order for wallet recharge.
     * Step 1 of the two-step recharge process.
     */
    private String handleCreateWalletRechargeOrder(String userId, RequestBody requestBody) throws Exception {
        Double amount = requestBody.getAmount();
        if (amount == null || amount <= 0) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Invalid amount"));
        }
        
        // Minimum recharge amount
        if (amount < MIN_WALLET_RECHARGE_AMOUNT) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Minimum recharge amount is ₹" + MIN_WALLET_RECHARGE_AMOUNT));
        }
        
        WalletService walletService = new WalletService(this.db);
        String currency = requestBody.getCurrency();
        if (currency == null || currency.isEmpty()) {
            currency = walletService.getUserDefaultCurrency(userId);
        }
        
        // Create Razorpay order
        String razorpayOrderId = razorpay.createOrder(amount, CustomerCipher.encryptCaesarCipher(userId));
        
        // Store recharge order details for verification
        Map<String, Object> rechargeOrderDetails = new HashMap<>();
        rechargeOrderDetails.put("userId", userId);
        rechargeOrderDetails.put("amount", amount);
        rechargeOrderDetails.put("currency", currency);
        rechargeOrderDetails.put("razorpay_order_id", razorpayOrderId);
        rechargeOrderDetails.put("status", "PENDING");
        rechargeOrderDetails.put("created_at", com.google.cloud.Timestamp.now());
        
        this.db.collection("users").document(userId).collection("wallet_recharge_orders")
                .document(razorpayOrderId).set(rechargeOrderDetails).get();
        
        return gson.toJson(Map.of(
            "success", true,
            "order_id", razorpayOrderId,
            "razorpay_key", razorpay.getRazorpayKey(),
            "amount", amount,
            "currency", currency
        ));
    }

    /**
     * Verify Razorpay payment and update wallet balance.
     * Step 2 of the two-step recharge process.
     */
    private String handleVerifyWalletRecharge(String userId, RequestBody requestBody) throws Exception {
        String razorpayOrderId = requestBody.getRazorpayOrderId();
        String razorpayPaymentId = requestBody.getRazorpayPaymentId();
        String razorpaySignature = requestBody.getRazorpaySignature();
        
        if (razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing payment details"));
        }
        
        // Verify payment signature
        if (!razorpay.verifyPayment(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Payment verification failed"));
        }
        
        WalletService walletService = new WalletService(this.db);
        
        // Check if payment already processed
        if (walletService.isPaymentAlreadyProcessed(userId, razorpayPaymentId)) {
            String defaultCurrency = walletService.getUserDefaultCurrency(userId);
            Double currentBalance = walletService.getWalletBalance(userId, defaultCurrency);
            return gson.toJson(Map.of(
                "success", true,
                "newBalance", currentBalance,
                "currency", defaultCurrency,
                "message", "Payment already processed"
            ));
        }
        
        // Fetch recharge order details
        DocumentSnapshot rechargeOrderDoc = this.db.collection("users").document(userId)
                .collection("wallet_recharge_orders").document(razorpayOrderId).get().get();
        
        if (!rechargeOrderDoc.exists()) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Recharge order not found"));
        }
        
        Double amount = rechargeOrderDoc.getDouble("amount");
        String currency = rechargeOrderDoc.getString("currency");
        if (currency == null) currency = WalletService.getDefaultCurrency();
        
        final String finalCurrency = currency;
        final Double finalAmount = amount;
        
        // Use transaction to update balance and create transaction record
        Double[] newBalanceHolder = new Double[1];
        this.db.runTransaction(transaction -> {
            // Update wallet balance
            newBalanceHolder[0] = walletService.updateWalletBalanceInTransaction(transaction, userId, finalCurrency, finalAmount);
            
            // Create wallet transaction record
            WalletTransaction walletTransaction = new WalletTransaction();
            walletTransaction.setType("RECHARGE");
            walletTransaction.setSource("PAYMENT");
            walletTransaction.setAmount(finalAmount);
            walletTransaction.setCurrency(finalCurrency);
            walletTransaction.setPaymentId(razorpayPaymentId);
            walletTransaction.setStatus("COMPLETED");
            walletTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
            walletTransaction.setDescription("Wallet recharge via Razorpay");
            
            walletService.createWalletTransactionInTransaction(transaction, userId, walletTransaction);
            
            return null;
        }).get();
        
        // Update recharge order status
        this.db.collection("users").document(userId).collection("wallet_recharge_orders")
                .document(razorpayOrderId).update("status", "COMPLETED", "payment_id", razorpayPaymentId).get();
        
        return gson.toJson(Map.of(
            "success", true,
            "newBalance", newBalanceHolder[0],
            "currency", currency
        ));
    }

    // =====================================================================
    // ON-DEMAND CONSULTATION HANDLER METHODS
    // =====================================================================

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
        
        // Normalize to lowercase for consistent use throughout the method
        consultationType = consultationType.toLowerCase();
        
        WalletService walletService = new WalletService(this.db);
        OnDemandConsultationService consultationService = new OnDemandConsultationService(this.db, walletService);
        
        // Check expert status
        String expertStatus = walletService.getExpertStatus(expertId);
        if (!"ONLINE".equals(expertStatus)) {
            return gson.toJson(Map.of(
                "success", false,
                "errorCode", "EXPERT_NOT_AVAILABLE",
                "errorMessage", "Expert is " + expertStatus.toLowerCase()
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
        Double walletBalance = walletService.getWalletBalance(userId, currency);
        Double minimumRequired = rate * MIN_CONSULTATION_MINUTES; // Minimum consultation minutes
        
        if (walletBalance < minimumRequired) {
            return gson.toJson(Map.of(
                "success", false,
                "errorCode", "LOW_BALANCE",
                "requiredAmount", minimumRequired,
                "currentBalance", walletBalance,
                "currency", currency
            ));
        }
        
        // Calculate max allowed duration
        Long maxAllowedDuration = (long) ((walletBalance / rate) * 60); // in seconds
        
        // Get user and expert names
        FirebaseUser user = UserService.getUserDetails(this.db, userId);
        FirebaseUser expert = UserService.getUserDetails(this.db, expertId);
        
        final Double finalRate = rate;
        final String finalCurrency = currency;
        final Double finalPlatformFeePercent = platformFeePercent;
        final Long finalMaxAllowedDuration = maxAllowedDuration;
        
        // Create order and lock expert status in a transaction
        String[] orderIdHolder = new String[1];
        try {
            this.db.runTransaction(transaction -> {
                // Double-check expert status
                DocumentReference storeRef = db.collection("users").document(expertId)
                        .collection("public").document("store");
                DocumentSnapshot storeDoc = transaction.get(storeRef).get();
                String currentStatus = storeDoc.getString("expert_status");
                
                if (!"ONLINE".equals(currentStatus)) {
                    throw new RuntimeException("Expert is no longer available");
                }
                
                // Lock expert status to BUSY
                walletService.setExpertStatusInTransaction(transaction, expertId, "BUSY");
                
                // Create order
                OnDemandConsultationOrder order = new OnDemandConsultationOrder();
                order.setUserId(userId);
                order.setUserName(user != null ? user.getName() : null);
                order.setExpertId(expertId);
                order.setExpertName(expert != null ? expert.getName() : null);
                order.setPlanId(planId);
                order.setConsultationType(consultationType.toLowerCase());
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
                // Get GetStream token
                PythonLambdaEventRequest getStreamTokenEvent = new PythonLambdaEventRequest();
                getStreamTokenEvent.setFunction("get_stream_user_token");
                getStreamTokenEvent.setUserId(userId);
                getStreamTokenEvent.setUserName(user != null ? user.getName() : userId);
                getStreamTokenEvent.setTest(isTest());
                
                streamUserToken = pythonLambdaService.invokePythonLambda(getStreamTokenEvent).getStreamUserToken();
                
                // Create GetStream call
                PythonLambdaEventRequest createCallEvent = new PythonLambdaEventRequest();
                createCallEvent.setFunction("create_call");
                createCallEvent.setType("CONSULTATION");
                createCallEvent.setUserId(userId);
                createCallEvent.setUserName(user != null ? user.getName() : null);
                createCallEvent.setExpertId(expertId);
                createCallEvent.setExpertName(expert != null ? expert.getName() : null);
                createCallEvent.setOrderId(orderId);
                createCallEvent.setVideo("video".equals(consultationType));
                createCallEvent.setTest(isTest());
                
                pythonLambdaService.invokePythonLambda(createCallEvent);
                
                streamCallCid = "consultation_" + consultationType + ":" + orderId;
                
                // Update order with stream call CID
                consultationService.updateStreamCallCid(userId, orderId, streamCallCid);
            } catch (Exception e) {
                // Compensating transaction: revert expert status and mark order as FAILED
                logger.log("GetStream call creation failed for order " + orderId + ": " + e.getMessage());
                
                try {
                    // Check for other active consultations BEFORE transaction (to avoid race condition)
                    boolean hasOtherConsultations = consultationService.hasOtherConnectedConsultations(expertId, orderId);
                    final boolean finalHasOtherConsultations = hasOtherConsultations;
                    
                    this.db.runTransaction(transaction -> {
                        // Revert expert status to ONLINE if no other active consultations
                        if (!finalHasOtherConsultations) {
                            walletService.setExpertStatusInTransaction(transaction, expertId, "ONLINE");
                        }
                        
                        // Mark order as FAILED
                        consultationService.markOrderAsFailedInTransaction(transaction, userId, orderId);
                        
                        return null;
                    }).get();
                } catch (Exception compensationError) {
                    logger.log("Error in compensating transaction: " + compensationError.getMessage());
                    // Log but don't throw - the order is already marked as FAILED
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
     * Returns CONTINUE or TERMINATE based on remaining time.
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
        
        // Verify wallet balance and recalculate max_duration based on actual balance
        Long[] newMaxDurationHolder = new Long[1];
        Long[] additionalDurationHolder = new Long[1];
        
        try {
            this.db.runTransaction(transaction -> {
                // Fetch current wallet balance in transaction
                Double currentBalance = walletService.getWalletBalance(userId, currency);
                
                // Calculate max duration based on actual balance: (balance / rate) * 60
                Long maxDurationFromBalance = (long) ((currentBalance / rate) * 60); // in seconds
                
                // Calculate elapsed time
                Long elapsedSeconds = consultationService.calculateElapsedSeconds(order);
                
                // New max duration is the maximum we can afford based on balance
                // Only extend if new max_duration > current elapsed time
                if (maxDurationFromBalance <= elapsedSeconds) {
                    throw new RuntimeException("Insufficient balance to extend consultation");
                }
                
                Long currentMaxDuration = order.getMaxAllowedDuration();
                Long newMaxDuration = maxDurationFromBalance;
                
                // Additional duration is the difference
                additionalDurationHolder[0] = newMaxDuration - currentMaxDuration;
                
                // Update max duration atomically
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
     * Calculates cost, deducts from wallet, and credits expert.
     */
    private String handleOnDemandConsultationEnd(String userId, RequestBody requestBody) throws Exception {
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
        
        // Allow ending if CONNECTED or already TERMINATED
        if (!Arrays.asList("CONNECTED", "TERMINATED").contains(order.getStatus())) {
            if ("COMPLETED".equals(order.getStatus())) {
                // Already completed, return existing data
                return gson.toJson(Map.of(
                    "success", true,
                    "cost", order.getCost(),
                    "duration", order.getDurationSeconds(),
                    "currency", order.getCurrency(),
                    "message", "Consultation already completed"
                ));
            }
            return gson.toJson(Map.of("success", false, "errorMessage", "Consultation is not active"));
        }
        
        // Calculate duration and cost
        Long durationSeconds = consultationService.calculateElapsedSeconds(order);
        Double cost = consultationService.calculateCost(durationSeconds, order.getExpertRatePerMinute());
        Double platformFeeAmount = consultationService.calculatePlatformFee(cost, order.getPlatformFeePercent());
        Double expertEarnings = cost - platformFeeAmount;
        
        String expertId = order.getExpertId();
        String currency = order.getCurrency();
        
        final Long finalDurationSeconds = durationSeconds;
        final Double finalCost = cost;
        final Double finalPlatformFeeAmount = platformFeeAmount;
        final Double finalExpertEarnings = expertEarnings;
        
        // Check for other active consultations BEFORE transaction (to avoid race condition)
        boolean hasOtherActive = consultationService.hasOtherConnectedConsultations(expertId, orderId);
        final boolean finalHasOtherActive = hasOtherActive;
        
        Double[] remainingBalanceHolder = new Double[1];
        
        this.db.runTransaction(transaction -> {
            // Deduct from user wallet
            remainingBalanceHolder[0] = walletService.updateWalletBalanceInTransaction(
                transaction, userId, currency, -finalCost
            );
            
            // Credit expert wallet
            walletService.updateWalletBalanceInTransaction(
                transaction, expertId, currency, finalExpertEarnings
            );
            
            // Create deduction transaction for user
            WalletTransaction deductionTransaction = new WalletTransaction();
            deductionTransaction.setType("CONSULTATION_DEDUCTION");
            deductionTransaction.setSource("PAYMENT");
            deductionTransaction.setAmount(-finalCost);
            deductionTransaction.setCurrency(currency);
            deductionTransaction.setOrderId(orderId);
            deductionTransaction.setStatus("COMPLETED");
            deductionTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
            deductionTransaction.setDescription("On-demand consultation charge");
            
            walletService.createWalletTransactionInTransaction(transaction, userId, deductionTransaction);
            
            // Create credit transaction for expert
            WalletTransaction creditTransaction = new WalletTransaction();
            creditTransaction.setType("ORDER_EARNING");
            creditTransaction.setSource("PAYMENT");
            creditTransaction.setAmount(finalExpertEarnings);
            creditTransaction.setCurrency(currency);
            creditTransaction.setOrderId(orderId);
            creditTransaction.setStatus("COMPLETED");
            creditTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
            creditTransaction.setDescription("On-demand consultation earning");
            
            walletService.createWalletTransactionInTransaction(transaction, expertId, creditTransaction);
            
            // Update order
            consultationService.completeOrderInTransaction(
                transaction, userId, orderId,
                finalDurationSeconds, finalCost, finalPlatformFeeAmount, finalExpertEarnings
            );
            
            // Set expert status back to ONLINE if no other active consultations
            if (!finalHasOtherActive) {
                walletService.setExpertStatusInTransaction(transaction, expertId, "ONLINE");
            }
            
            return null;
        }).get();
        
        return gson.toJson(Map.of(
            "success", true,
            "cost", cost,
            "duration", durationSeconds,
            "currency", currency,
            "remainingBalance", remainingBalanceHolder[0]
        ));
    }
}
