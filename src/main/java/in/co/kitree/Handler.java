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
    Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private Firestore db;
    private Razorpay razorpay;
    //    private StripeService stripeService;
    private static LambdaLogger logger;
    protected PythonLambdaService pythonLambdaService;

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

            String userId = event.getRequestContext().getAuthorizer().getJwt().getClaims().getUser_id();

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
                    } else {
                        String orderId = razorpay.createOrder(servicePlan.getAmount(), CustomerCipher.encryptCaesarCipher(userId));

                        orderDetails.put("subscription", false);
                        orderDetails.put("payment_gateway", "RAZORPAY");
                        createOrderInDB(userId, orderId, orderDetails);

                        response.put("order_id", orderId);
                        response.put("payment_gateway", "RAZORPAY");
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
                FirebaseOrder order = fetchOrder(userId, requestBody.getOrderId());
                System.out.println("order: " + order);
                // TODO: Check for date range, dont allow long date ranges for efficiency reasons.
                if (order == null || order.getExpertId() == null || order.getExpertId().isEmpty() || rangeStart == null || rangeEnd == null || rangeStart.isEmpty() || rangeEnd.isEmpty()) { // TODO: Check if rangeEnd > rangeStart and the difference is maximum 1 month + 1 day (or 32 days).
                    return gson.toJson(Collections.emptyList());
                }

                String expertId = order.getExpertId();
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
                Map<String, Object> horoscopeApiRequestBody = new HashMap<>();

//                TODO: Uncomment later once we integrate with call screen. AI agent: do not change directly, ask me first.
                //                String orderId = requestBody.getOrderId();
//                String clientId = requestBody.getUserId();

//                if (orderId == null || orderId.isEmpty() || clientId == null || clientId.isEmpty()) {
//                    return gson.toJson(Map.of("success", false));
//                }
//
//                FirebaseOrder order = fetchOrder(clientId, orderId);
//
//                if (order == null || order.getProfileIds() == null || order.getProfileIds().isEmpty()) {
//                    return gson.toJson(Map.of("success", false));
//                }

                // Validate required fields
                if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                        requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                        requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                        requestBody.getHoroscopeLongitude() == null) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Missing required horoscope details"));
                }
                horoscopeApiRequestBody.put("date", requestBody.getHoroscopeDate());
                horoscopeApiRequestBody.put("month", requestBody.getHoroscopeMonth());
                horoscopeApiRequestBody.put("year", requestBody.getHoroscopeYear());
                horoscopeApiRequestBody.put("hour", requestBody.getHoroscopeHour());
                horoscopeApiRequestBody.put("minute", requestBody.getHoroscopeMinute());
                horoscopeApiRequestBody.put("latitude", requestBody.getHoroscopeLatitude());
                horoscopeApiRequestBody.put("longitude", requestBody.getHoroscopeLongitude());
                horoscopeApiRequestBody.put("api_token", "D80FE645F582F9E0");
                return getAstrologyDetails(gson.toJson(horoscopeApiRequestBody));
            }

            if ("get_dasha_details".equals(requestBody.getFunction())) {
                Map<String, Object> dashaApiRequestBody = new HashMap<>();
                // Validate required fields
                if (requestBody.getDashaDate() == null || requestBody.getDashaMonth() == null ||
                    requestBody.getDashaYear() == null || requestBody.getDashaHour() == null ||
                    requestBody.getDashaMinute() == null || requestBody.getDashaLatitude() == null ||
                    requestBody.getDashaLongitude() == null || requestBody.getDashaPrefix() == null) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Missing required dasha details"));
                }
                dashaApiRequestBody.put("date", requestBody.getDashaDate());
                dashaApiRequestBody.put("month", requestBody.getDashaMonth());
                dashaApiRequestBody.put("year", requestBody.getDashaYear());
                dashaApiRequestBody.put("hour", requestBody.getDashaHour());
                dashaApiRequestBody.put("minute", requestBody.getDashaMinute());
                dashaApiRequestBody.put("latitude", requestBody.getDashaLatitude());
                dashaApiRequestBody.put("longitude", requestBody.getDashaLongitude());
                dashaApiRequestBody.put("prefix", requestBody.getDashaPrefix());
                dashaApiRequestBody.put("api_token", "D80FE645F582F9E0");
                return getDashaDetails(gson.toJson(dashaApiRequestBody));
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return gson.toJson(Map.of("success", false));
        }
        return null;
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

    private String getAstrologyDetails(String requestBody) throws Exception {

        String API_URL = "https://kitree-python-server.salmonmoss-7e006d81.centralindia.azurecontainerapps.io/get_horoscope";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Horoscope API response: " + response);
            return response;
        } else {
            throw new RuntimeException("API request failed with status code: " + httpResponse.statusCode());
        }
    }

    private String getDashaDetails(String requestBody) throws Exception {
        String API_URL = "https://kitree-python-server.salmonmoss-7e006d81.centralindia.azurecontainerapps.io/dasha";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Dasha API response: " + response);
            return response;
        } else {
            throw new RuntimeException("Dasha API request failed with status code: " + httpResponse.statusCode());
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
