//package in.co.kitree.services;
//
//import com.razorpay.RazorpayException;
//import com.stripe.Stripe;
//import com.stripe.exception.StripeException;
//import com.stripe.model.PaymentIntent;
//import com.stripe.param.PaymentIntentCreateParams;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class StripeService {
//    private final Boolean isTest;
//
//
//    public StripeService(boolean isTest) throws RazorpayException {
//        this.isTest = isTest;
//        if (isTest) {
//            Stripe.apiKey = STRIPE_TEST_SECRET_KEY;
//        } else {
//            Stripe.apiKey = STRIPE_SECRET_KEY;
//        }
//    }
//
//    public Map<String, String> createPaymentIntent(double amount, String currency) throws StripeException {
////        CustomerCreateParams customerParams = CustomerCreateParams.builder().build();
////        Customer customer = Customer.create(customerParams);
////        EphemeralKeyCreateParams ephemeralKeyParams =
////                EphemeralKeyCreateParams.builder()
////                        .setStripeVersion("2024-09-30.acacia")
////                        .setCustomer(customer.getId())
////                        .build();
//
////        EphemeralKey ephemeralKey = EphemeralKey.create(ephemeralKeyParams);
//        PaymentIntentCreateParams paymentIntentParams =
//                PaymentIntentCreateParams.builder()
//                        .setAmount(getAmountInCents(amount))
//                        .setCurrency(currency)
////                        .setCustomer(customer.getId())
//                        .build();
//
//        PaymentIntent paymentIntent = PaymentIntent.create(paymentIntentParams);
//        Map<String, String> responseData = new HashMap<>();
//        responseData.put("paymentIntent", paymentIntent.getClientSecret());
////        responseData.put("ephemeralKey", ephemeralKey.getSecret());
////        responseData.put("customer", customer.getId());
//        if (isTest) {
//            responseData.put("publishableKey", STRIPE_TEST_PUBLISHABLE_KEY);
//        } else {
//            responseData.put("publishableKey", STRIPE_PUBLISHABLE_KEY);
//        }
//
//        return responseData;
//    }
//
//    private long getAmountInCents(double amount) {
//        return (long) (amount * 100);
//    }
//}
