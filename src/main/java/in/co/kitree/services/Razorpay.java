package in.co.kitree.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class Razorpay implements PaymentGateway {

    private final Boolean isTest;

    private final RazorpayClient razorpayClient;
    private String RAZORPAY_KEY = "";
    private String RAZORPAY_SECRET = "";

    private String RAZORPAY_TEST_KEY = "";
    private String RAZORPAY_TEST_SECRET = "";

    private String RAZORPAY_WEBHOOK_SECRET = "";

    public enum ErrorCode {
        CANCELLATION_FAILED_SERVER_ERROR
    }

    public Razorpay(boolean isTest) throws RazorpayException {

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Read the JSON file into a JsonNode
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));

            this.RAZORPAY_KEY = rootNode.path("RAZORPAY_KEY").asText();
            this.RAZORPAY_SECRET = rootNode.path("RAZORPAY_SECRET").asText();
            this.RAZORPAY_TEST_KEY = rootNode.path("RAZORPAY_TEST_KEY").asText();
            this.RAZORPAY_TEST_SECRET = rootNode.path("RAZORPAY_TEST_SECRET").asText();
            if (isTest) {
                this.RAZORPAY_WEBHOOK_SECRET = rootNode.path("RAZORPAY_WEBHOOK_SECRET_TEST").asText();
            } else {
                this.RAZORPAY_WEBHOOK_SECRET = rootNode.path("RAZORPAY_WEBHOOK_SECRET").asText();
            }
        } catch (IOException e) {
            LoggingService.error("razorpay_secrets_read_failed", e);
        }

        this.isTest = isTest;
        if (isTest) {
            this.razorpayClient = new RazorpayClient(RAZORPAY_TEST_KEY, RAZORPAY_TEST_SECRET);
        } else {
            this.razorpayClient = new RazorpayClient(RAZORPAY_KEY, RAZORPAY_SECRET);
        }
    }

    @Override
    public PaymentOrderResult createOrder(double amount, String currency, String receipt) throws RazorpayException {
        long amountInPaise = getAmountInPaise(amount);
        JSONObject options = new JSONObject();
        JSONObject notes = new JSONObject();
        notes.put("receipt", receipt);
        options.put("amount", amountInPaise);
        options.put("currency", "INR");
        options.put("notes", notes);
        Order order = razorpayClient.orders.create(options);
        return new PaymentOrderResult(order.get("id"), null);
    }

    @Override
    public boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String razorpaySign) throws RazorpayException {
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", razorpayOrderId);
        options.put("razorpay_payment_id", razorpayPaymentId);
        options.put("razorpay_signature", razorpaySign);
        return com.razorpay.Utils.verifyPaymentSignature(options, this.isTest ? this.RAZORPAY_TEST_SECRET : this.RAZORPAY_SECRET);
    }

    public String createSubscription(String planId, String encryptedCustomerId) throws RazorpayException {
        LoggingService.debug("razorpay_create_subscription", Map.of("planId", planId));
        JSONObject options = new JSONObject();
        JSONObject notes = new JSONObject();
        notes.put("receipt", encryptedCustomerId);
        options.put("plan_id", planId);
        options.put("customer_notify", 0);
        options.put("total_count", 120);
        options.put("quantity", 1);
        options.put("notes", notes);
        Subscription subscription = razorpayClient.subscriptions.create(options);
        return subscription.get("id");
    }

    public boolean verifySubscription(String subscriptionId) {
        try {
            Subscription subscription = razorpayClient.subscriptions.fetch(subscriptionId);
            LoggingService.debug("razorpay_verify_subscription", Map.of("subscriptionId", subscriptionId, "status", subscription.get("status")));
            return "active".equals(subscription.get("status"));
        } catch (RazorpayException e) {
            return false;
        }
    }

    public boolean verifyWebhookSignature(String body, String signature) {
        try {
            return com.razorpay.Utils.verifyWebhookSignature(body, signature, RAZORPAY_WEBHOOK_SECRET);
        } catch (RazorpayException e) {
            LoggingService.error("razorpay_webhook_signature_verification_failed", e);
            return false;
        }
    }

    public Subscription cancel(String subscriptionId) throws RazorpayException {
        return razorpayClient.subscriptions.cancel(subscriptionId);
    }

    /**
     * Get the Razorpay key for the current environment (test or prod)
     * This method returns only the public key, not the secret
     * @return The Razorpay key for the current environment
     */
    @Override
    public String getPublishableKey() {
        return this.isTest ? this.RAZORPAY_TEST_KEY : this.RAZORPAY_KEY;
    }

    @Override
    public String getGatewayType() {
        return PaymentGatewayRouter.RAZORPAY;
    }


    private long getAmountInPaise(double amountInINR) {
        return (long) (amountInINR * 100);
    }
}
