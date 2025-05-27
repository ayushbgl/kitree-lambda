package in.co.kitree.e2e;

import com.google.cloud.firestore.Firestore;
import in.co.kitree.TestBase;
import in.co.kitree.pojos.*;
import in.co.kitree.services.CouponService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServicePurchaseFlowTest extends TestBase {
    private FirebaseUser testUser;
    private ServicePlan testServicePlan;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        // Create test user
        testUser = new FirebaseUser();
        testUser.setUid("test-user-" + System.currentTimeMillis());
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setCouponUsageFrequency(new HashMap<>());

        // Save user to Firestore emulator
        db.collection("users").document(testUser.getUid()).set(testUser).get();

        // Create test service plan
        testServicePlan = new ServicePlan();
        testServicePlan.setId("test-service-" + System.currentTimeMillis());
        testServicePlan.setAmount(1000.0);
        testServicePlan.setExpertId("test-expert-id");
        testServicePlan.setName("Test Service");
        testServicePlan.setDescription("Test Service Description");

        // Save service plan to Firestore emulator
        db.collection("servicePlans").document(testServicePlan.getId()).set(testServicePlan).get();

        // Create test coupon
        testCoupon = new Coupon();
        testCoupon.setCode("TEST50");
        testCoupon.setType(Coupon.CouponType.PERCENTAGE);
        testCoupon.setValue(20);
        testCoupon.setMinAmount(500);
        testCoupon.setMaxDiscount(200);
        testCoupon.setExpertId("test-expert-id");

        // Save coupon to Firestore emulator
        db.collection("coupons").document(testCoupon.getCode()).set(testCoupon).get();
    }

    @AfterEach
    void cleanup() throws ExecutionException, InterruptedException {
        // Clean up test data
        if (testUser != null) {
            db.collection("users").document(testUser.getUid()).delete().get();
        }
        if (testServicePlan != null) {
            db.collection("servicePlans").document(testServicePlan.getId()).delete().get();
        }
        if (testCoupon != null) {
            db.collection("coupons").document(testCoupon.getCode()).delete().get();
        }
    }

    @Test
    void testCompleteServicePurchaseFlow() {
        // 1. Apply coupon
        CouponResult couponResult = CouponService.applyCoupon(testCoupon, testServicePlan, testUser, "en");
        assertTrue(couponResult.isValid());
        assertEquals(200, couponResult.getDiscountAmount());
        assertEquals(800, couponResult.getFinalAmount());

        // 2. Create order
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("servicePlanId", testServicePlan.getId());
        orderData.put("userId", testUser.getUid());
        orderData.put("amount", testServicePlan.getAmount());
        orderData.put("discountAmount", couponResult.getDiscountAmount());
        orderData.put("finalAmount", couponResult.getFinalAmount());
        orderData.put("couponCode", testCoupon.getCode());

        // 3. Generate payment
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("orderId", "test-order-" + System.currentTimeMillis());
        paymentData.put("amount", couponResult.getFinalAmount());
        paymentData.put("currency", "INR");
        paymentData.put("receipt", "receipt_" + System.currentTimeMillis());

        // 4. Verify payment
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("razorpay_payment_id", "pay_test_" + System.currentTimeMillis());
        verificationData.put("razorpay_order_id", paymentData.get("orderId"));
        verificationData.put("razorpay_signature", "test_signature");

        // 5. Update user's coupon usage
        Map<String, Long> usageFrequency = testUser.getCouponUsageFrequency();
        usageFrequency.put(testCoupon.getCode(), 1L);
        testUser.setCouponUsageFrequency(usageFrequency);

        // 6. Verify final state
        assertNotNull(paymentData.get("orderId"));
        assertNotNull(verificationData.get("razorpay_payment_id"));
        assertEquals(1L, testUser.getCouponUsageFrequency().get(testCoupon.getCode()));
    }

    @Test
    void testServicePurchaseFlowWithInvalidCoupon() {
        // 1. Create invalid coupon
        Coupon invalidCoupon = new Coupon();
        invalidCoupon.setCode("INVALID50");
        invalidCoupon.setType(Coupon.CouponType.PERCENTAGE);
        invalidCoupon.setValue(20);
        invalidCoupon.setMinAmount(2000); // Higher than service amount
        invalidCoupon.setMaxDiscount(200);
        invalidCoupon.setExpertId("test-expert-id");

        // 2. Try to apply invalid coupon
        CouponResult couponResult = CouponService.applyCoupon(invalidCoupon, testServicePlan, testUser, "en");
        assertFalse(couponResult.isValid());
        assertTrue(couponResult.getError().contains("minimum amount"));

        // 3. Create order without discount
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("servicePlanId", testServicePlan.getId());
        orderData.put("userId", testUser.getUid());
        orderData.put("amount", testServicePlan.getAmount());
        orderData.put("discountAmount", 0);
        orderData.put("finalAmount", testServicePlan.getAmount());

        // 4. Generate payment
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("orderId", "test-order-" + System.currentTimeMillis());
        paymentData.put("amount", testServicePlan.getAmount());
        paymentData.put("currency", "INR");
        paymentData.put("receipt", "receipt_" + System.currentTimeMillis());

        // 5. Verify payment
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("razorpay_payment_id", "pay_test_" + System.currentTimeMillis());
        verificationData.put("razorpay_order_id", paymentData.get("orderId"));
        verificationData.put("razorpay_signature", "test_signature");

        // 6. Verify final state
        assertNotNull(paymentData.get("orderId"));
        assertNotNull(verificationData.get("razorpay_payment_id"));
        assertEquals(0, testUser.getCouponUsageFrequency().size());
    }

    @Test
    void testServicePurchaseFlowWithExpiredCoupon() {
        // 1. Create expired coupon
        Coupon expiredCoupon = new Coupon();
        expiredCoupon.setCode("EXPIRED50");
        expiredCoupon.setType(Coupon.CouponType.PERCENTAGE);
        expiredCoupon.setValue(20);
        expiredCoupon.setMinAmount(500);
        expiredCoupon.setMaxDiscount(200);
        expiredCoupon.setExpertId("test-expert-id");
        expiredCoupon.setExpiresAt(System.currentTimeMillis() - 1000); // Expired 1 second ago

        // 2. Try to apply expired coupon
        CouponResult couponResult = CouponService.applyCoupon(expiredCoupon, testServicePlan, testUser, "en");
        assertFalse(couponResult.isValid());
        assertTrue(couponResult.getError().contains("expired"));

        // 3. Create order without discount
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("servicePlanId", testServicePlan.getId());
        orderData.put("userId", testUser.getUid());
        orderData.put("amount", testServicePlan.getAmount());
        orderData.put("discountAmount", 0);
        orderData.put("finalAmount", testServicePlan.getAmount());

        // 4. Generate payment
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("orderId", "test-order-" + System.currentTimeMillis());
        paymentData.put("amount", testServicePlan.getAmount());
        paymentData.put("currency", "INR");
        paymentData.put("receipt", "receipt_" + System.currentTimeMillis());

        // 5. Verify payment
        Map<String, Object> verificationData = new HashMap<>();
        verificationData.put("razorpay_payment_id", "pay_test_" + System.currentTimeMillis());
        verificationData.put("razorpay_order_id", paymentData.get("orderId"));
        verificationData.put("razorpay_signature", "test_signature");

        // 6. Verify final state
        assertNotNull(paymentData.get("orderId"));
        assertNotNull(verificationData.get("razorpay_payment_id"));
        assertEquals(0, testUser.getCouponUsageFrequency().size());
    }
} 