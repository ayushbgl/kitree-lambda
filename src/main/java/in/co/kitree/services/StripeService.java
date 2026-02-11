package in.co.kitree.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

import java.util.Map;

/**
 * Stripe payment gateway service.
 * Handles PaymentIntent creation and verification for non-INR currencies.
 */
public class StripeService implements PaymentGateway {

    private final String publishableKey;

    public StripeService(boolean isTest) {
        String secretKey;
        String pubKey;

        if (isTest) {
            secretKey = SecretsProvider.getString("STRIPE_TEST_SECRET_KEY");
            pubKey = SecretsProvider.getString("STRIPE_TEST_PUBLISHABLE_KEY");
        } else {
            secretKey = SecretsProvider.getString("STRIPE_SECRET_KEY");
            pubKey = SecretsProvider.getString("STRIPE_PUBLISHABLE_KEY");
        }

        this.publishableKey = pubKey;
        Stripe.apiKey = secretKey;
    }

    @Override
    public PaymentOrderResult createOrder(double amount, String currency, String receipt) throws StripeException {
        long amountInSmallestUnit = getAmountInSmallestUnit(amount, currency);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.toLowerCase())
                .putMetadata("receipt", receipt)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        return new PaymentOrderResult(intent.getId(), intent.getClientSecret());
    }

    /**
     * Verify a Stripe payment by checking PaymentIntent status.
     * Only uses gatewayOrderId (the PaymentIntent ID); other params are ignored.
     */
    @Override
    public boolean verifyPayment(String gatewayOrderId, String gatewayPaymentId, String gatewaySignature) throws StripeException {
        PaymentIntent intent = PaymentIntent.retrieve(gatewayOrderId);
        return "succeeded".equals(intent.getStatus());
    }

    @Override
    public String getPublishableKey() {
        return this.publishableKey;
    }

    @Override
    public String getGatewayType() {
        return PaymentGatewayRouter.STRIPE;
    }

    /**
     * Convert amount to smallest currency unit (cents, pence, etc.).
     * Most currencies use 100 subunits. Zero-decimal currencies (JPY, KRW, etc.) use 1.
     */
    private static long getAmountInSmallestUnit(double amount, String currency) {
        String upper = currency.toUpperCase();
        if ("JPY".equals(upper) || "KRW".equals(upper) || "VND".equals(upper) ||
                "CLP".equals(upper) || "PYG".equals(upper) || "UGX".equals(upper) ||
                "RWF".equals(upper) || "GNF".equals(upper) || "XOF".equals(upper) ||
                "XAF".equals(upper) || "BIF".equals(upper) || "DJF".equals(upper) ||
                "KMF".equals(upper) || "MGA".equals(upper) || "VUV".equals(upper) ||
                "XPF".equals(upper)) {
            return (long) amount;
        }
        return Math.round(amount * 100);
    }
}
