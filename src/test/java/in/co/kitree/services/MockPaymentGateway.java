package in.co.kitree.services;

import java.util.UUID;

/**
 * Mock PaymentGateway for testing.
 * Supports configurable success/failure scenarios without hitting real payment APIs.
 *
 * Usage in tests:
 *   MockPaymentGateway gateway = MockPaymentGateway.success("RAZORPAY");
 *   MockPaymentGateway failGateway = MockPaymentGateway.fail("STRIPE", "Card declined");
 */
public class MockPaymentGateway implements PaymentGateway {

    private final String gatewayType;
    private final boolean shouldSucceed;
    private final String errorMessage;

    private String lastOrderId;
    private double lastAmount;
    private String lastCurrency;

    private MockPaymentGateway(String gatewayType, boolean shouldSucceed, String errorMessage) {
        this.gatewayType = gatewayType;
        this.shouldSucceed = shouldSucceed;
        this.errorMessage = errorMessage;
    }

    /** Create a mock that always succeeds. */
    public static MockPaymentGateway success(String gatewayType) {
        return new MockPaymentGateway(gatewayType, true, null);
    }

    /** Create a mock that always fails with the given error. */
    public static MockPaymentGateway fail(String gatewayType, String errorMessage) {
        return new MockPaymentGateway(gatewayType, false, errorMessage);
    }

    @Override
    public PaymentOrderResult createOrder(double amount, String currency, String receipt) throws Exception {
        this.lastAmount = amount;
        this.lastCurrency = currency;

        if (!shouldSucceed) {
            throw new RuntimeException(errorMessage != null ? errorMessage : "Payment creation failed");
        }

        String orderId = "mock_" + gatewayType.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        this.lastOrderId = orderId;

        String clientSecret = PaymentGatewayRouter.STRIPE.equals(gatewayType)
                ? "mock_secret_" + orderId
                : null;

        return new PaymentOrderResult(orderId, clientSecret);
    }

    @Override
    public boolean verifyPayment(String gatewayOrderId, String gatewayPaymentId, String gatewaySignature) {
        return shouldSucceed;
    }

    @Override
    public String getPublishableKey() {
        return "mock_pk_" + gatewayType.toLowerCase();
    }

    @Override
    public String getGatewayType() {
        return gatewayType;
    }

    // --- Test inspection methods ---

    public String getLastOrderId() {
        return lastOrderId;
    }

    public double getLastAmount() {
        return lastAmount;
    }

    public String getLastCurrency() {
        return lastCurrency;
    }
}
