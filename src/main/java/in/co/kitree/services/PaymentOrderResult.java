package in.co.kitree.services;

/**
 * Result of creating a payment order across gateways.
 * Razorpay populates gatewayOrderId; Stripe populates both gatewayOrderId (paymentIntentId) and clientSecret.
 */
public class PaymentOrderResult {

    private final String gatewayOrderId;
    private final String clientSecret; // Stripe only — null for Razorpay

    public PaymentOrderResult(String gatewayOrderId, String clientSecret) {
        this.gatewayOrderId = gatewayOrderId;
        this.clientSecret = clientSecret;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    /** Stripe client secret for the frontend Payment Sheet. Null for Razorpay. */
    public String getClientSecret() {
        return clientSecret;
    }
}
