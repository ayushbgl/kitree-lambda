package in.co.kitree.services;

/**
 * Routes payments to the appropriate gateway based on currency.
 * INR → Razorpay, all other currencies → Stripe.
 */
public class PaymentGatewayRouter {

    public static final String RAZORPAY = "RAZORPAY";
    public static final String STRIPE = "STRIPE";

    /**
     * Get the appropriate PaymentGateway implementation based on currency.
     *
     * @param currency      ISO currency code (e.g. "INR", "USD")
     * @param razorpay      Razorpay implementation
     * @param stripeService Stripe implementation
     * @return the gateway to use for this currency
     */
    public static PaymentGateway getGateway(String currency, Razorpay razorpay, StripeService stripeService) {
        if (currency == null || currency.isEmpty() || "INR".equalsIgnoreCase(currency)) {
            return razorpay;
        }
        return stripeService;
    }

    /**
     * Determine which payment gateway type to use based on currency.
     */
    public static String getGatewayType(String currency) {
        if (currency == null || currency.isEmpty() || "INR".equalsIgnoreCase(currency)) {
            return RAZORPAY;
        }
        return STRIPE;
    }
}
