package in.co.kitree.handlers;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.RazorpayWebhookBody;
import in.co.kitree.pojos.RequestBody;
import in.co.kitree.services.CustomerCipher;
import in.co.kitree.services.LoggingService;
import in.co.kitree.services.Razorpay;
import in.co.kitree.services.SemanticVersion;

import org.json.JSONObject;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.WriteResult;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Handler for admin and system-level operations.
 * These functions bypass normal userId auth and use their own secrets.
 * Extracted from Handler.java as part of refactoring.
 */
public class AdminHandler {

    private static final String ADMIN_SECRET = "C6DC17344FA8287F92C93B11CDF99";
    private static final String RAZORPAY_WEBHOOK_SECRET = "wq)(#1^|cqhhH$x";

    private final Firestore db;
    private final Razorpay razorpay;
    private final Gson gson;

    public AdminHandler(Firestore db, Razorpay razorpay) {
        this.db = db;
        this.razorpay = razorpay;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public String handleRequest(String action, RequestBody requestBody) throws Exception {
        return switch (action) {
            case "app_startup" -> handleAppStartup(requestBody);
            case "make_admin" -> handleMakeAdmin(requestBody);
            case "remove_admin" -> handleRemoveAdmin(requestBody);
            case "razorpay_webhook" -> handleRazorpayWebhook(requestBody);
            default -> gson.toJson(Map.of("success", false, "errorMessage", "Unknown action: " + action));
        };
    }

    private String handleMakeAdmin(RequestBody requestBody) throws FirebaseAuthException {
        if (!ADMIN_SECRET.equals(requestBody.getAdminSecret())) {
            return "Not Authorized";
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin", true);
        FirebaseAuth.getInstance().setCustomUserClaims(requestBody.getAdminUid(), claims);
        return "Done Successfully!";
    }

    private String handleRemoveAdmin(RequestBody requestBody) throws FirebaseAuthException {
        if (!ADMIN_SECRET.equals(requestBody.getAdminSecret())) {
            return "Not Authorized";
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin", false);
        FirebaseAuth.getInstance().setCustomUserClaims(requestBody.getAdminUid(), claims);
        return "Done Successfully!";
    }

    private String handleRazorpayWebhook(RequestBody requestBody) throws ExecutionException, InterruptedException {
        if (!RAZORPAY_WEBHOOK_SECRET.equals(requestBody.getAdminSecret())) {
            return "Not Authorized";
        }

        RazorpayWebhookBody webhookBody = requestBody.getRazorpayWebhookBody();
        JSONObject razorpayWebhookBody = new JSONObject(webhookBody.getBody());
        String eventType = razorpayWebhookBody.getString("event");

        String customerId = CustomerCipher.decryptCaesarCipher(
            razorpayWebhookBody.getJSONObject("payload")
                .getJSONObject("subscription")
                .getJSONObject("entity")
                .getJSONObject("notes")
                .getString("receipt")
        );

        boolean sig = razorpay.verifyWebhookSignature(
            webhookBody.getBody(),
            webhookBody.getHeaders().get("x-razorpay-signature")
        );

        if (!sig) {
            return "Webhook Signature not verified";
        }

        updateSubscriptionDetails(customerId, razorpayWebhookBody, eventType);
        return "Done Successfully";
    }

    private String handleAppStartup(RequestBody requestBody) {
        String versionCode = requestBody.getVersionCode();
        String warningVersion = "1.1.0";
        String forceVersion = "1.0.0";
        Map<String, Integer> response = new HashMap<>();
        response.put("appUpdate", 0);
        if (SemanticVersion.compareSemanticVersions(versionCode, forceVersion) < 0) {
            response.put("appUpdate", 2);
        } else if (versionCode.compareTo(warningVersion) < 0) {
            response.put("appUpdate", 1);
        }
        return gson.toJson(response);
    }

    // ============= Private Helpers =============

    private void updateSubscriptionDetails(String userId, JSONObject razorpayWebhookBody, String eventType) throws ExecutionException, InterruptedException {
        String subscriptionId = razorpayWebhookBody.getJSONObject("payload")
            .getJSONObject("subscription")
            .getJSONObject("entity")
            .getString("id");

        if ("subscription.charged".equals(eventType)) {
            DocumentReference subscriptionRef = db.collection("users").document(userId)
                .collection("orders").document(subscriptionId);
            var subscriptionSnapshot = subscriptionRef.get().get();
            Long amount = subscriptionSnapshot.getLong("amount");
            String currency = subscriptionSnapshot.getString("currency");
            String expertId = subscriptionSnapshot.getString("expert_id");
            String category = subscriptionSnapshot.getString("category");

            long currentEnd = razorpayWebhookBody.getJSONObject("payload")
                .getJSONObject("subscription")
                .getJSONObject("entity")
                .getLong("current_end");
            Timestamp currentEndTime = new Timestamp(currentEnd * 1000);
            Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());

            Map<String, Object> subscriptionFields = new HashMap<>();
            subscriptionFields.put("current_end_time", currentEndTime);
            subscriptionFields.put("payment_received_at", currentTimestamp);

            Map<String, Object> subscriptionPaymentsFields = new HashMap<>();
            subscriptionPaymentsFields.put("payment_received_at", currentTimestamp);
            subscriptionPaymentsFields.put("amount", amount);
            subscriptionPaymentsFields.put("currency", currency);
            subscriptionPaymentsFields.put("expert_id", expertId);
            subscriptionPaymentsFields.put("category", category);

            ApiFuture<WriteResult> future = db.collection("users").document(userId)
                .collection("orders").document(subscriptionId).update(subscriptionFields);
            ApiFuture<WriteResult> futurePayments = db.collection("users").document(userId)
                .collection("orders").document(subscriptionId)
                .collection("subscription_payments").document().create(subscriptionPaymentsFields);

            future.get();
            futurePayments.get();
        } else if ("subscription.cancelled".equals(eventType)) {
            cancelSubscriptionInDb(userId, subscriptionId);
        }
    }

    private void cancelSubscriptionInDb(String userId, String subscriptionId) throws ExecutionException, InterruptedException {
        db.collection("users").document(userId).collection("orders").document(subscriptionId)
            .update("cancelled_at", new Timestamp(System.currentTimeMillis())).get();
    }

}
