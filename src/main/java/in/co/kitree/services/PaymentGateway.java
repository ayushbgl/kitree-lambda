package in.co.kitree.services;

/**
 * Common interface for payment gateways (Razorpay, Stripe).
 * Handlers use this interface; PaymentGatewayRouter selects the implementation
 * based on currency.
 */
public interface PaymentGateway {

    /**
     * Create a payment order/intent.
     *
     * @param amount   amount in major currency unit (e.g. 500.0 INR, 10.50 USD)
     * @param currency ISO currency code (e.g. "INR", "USD")
     * @param receipt  encrypted customer identifier for gateway metadata
     * @return result containing gateway-specific order details
     */
    PaymentOrderResult createOrder(double amount, String currency, String receipt) throws Exception;

    /**
     * Verify a payment after the client completes checkout.
     * Razorpay: HMAC signature verification using all three params.
     * Stripe: PaymentIntent status check using only gatewayOrderId (others ignored).
     *
     * @param gatewayOrderId   Razorpay order ID or Stripe PaymentIntent ID
     * @param gatewayPaymentId Razorpay payment ID (null for Stripe)
     * @param gatewaySignature Razorpay signature (null for Stripe)
     * @return true if payment is verified
     */
    boolean verifyPayment(String gatewayOrderId, String gatewayPaymentId, String gatewaySignature) throws Exception;

    /**
     * Get the publishable/public key for the frontend SDK.
     */
    String getPublishableKey();

    /**
     * Get the gateway type identifier (e.g. "RAZORPAY", "STRIPE").
     */
    String getGatewayType();
}
