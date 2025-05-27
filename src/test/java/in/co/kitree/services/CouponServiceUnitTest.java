package in.co.kitree.services;

import in.co.kitree.pojos.Coupon;
import in.co.kitree.pojos.FirebaseUser;
import in.co.kitree.pojos.ServicePlan;
import in.co.kitree.pojos.CouponResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CouponServiceUnitTest {
    @Mock
    private ServicePlan mockServicePlan;
    @Mock
    private FirebaseUser mockUser;

    private static final String TEST_COUPON_CODE = "TEST50";
    private static final String TEST_USER_ID = "test-user-id";
    private static final String TEST_EXPERT_ID = "expert-id";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockUser.getUid()).thenReturn(TEST_USER_ID);
    }

    @Test
    void testApplyCoupon_ValidFlatDiscount() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.FLAT);
        coupon.setValue(50);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId(TEST_EXPERT_ID);

        when(mockServicePlan.getAmount()).thenReturn(200.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(50, result.getDiscountAmount());
        assertEquals(150, result.getFinalAmount());
    }

    @Test
    void testApplyCoupon_ValidPercentageDiscount() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.PERCENTAGE);
        coupon.setValue(20);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId(TEST_EXPERT_ID);

        when(mockServicePlan.getAmount()).thenReturn(200.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(40, result.getDiscountAmount());
        assertEquals(160, result.getFinalAmount());
    }

    @Test
    void testApplyCoupon_AmountBelowMinimum() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.FLAT);
        coupon.setValue(50);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId(TEST_EXPERT_ID);

        when(mockServicePlan.getAmount()).thenReturn(50.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getError().contains("minimum amount"));
    }

    @Test
    void testApplyCoupon_MaxDiscountExceeded() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.PERCENTAGE);
        coupon.setValue(50);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId(TEST_EXPERT_ID);

        when(mockServicePlan.getAmount()).thenReturn(500.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(100, result.getDiscountAmount()); // Max discount capped
        assertEquals(400, result.getFinalAmount());
    }

    @Test
    void testApplyCoupon_InvalidExpertId() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.FLAT);
        coupon.setValue(50);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId("different-expert-id");

        when(mockServicePlan.getAmount()).thenReturn(200.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getError().contains("expert"));
    }

    @Test
    void testApplyCoupon_MaxUsageExceeded() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.FLAT);
        coupon.setValue(50);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId(TEST_EXPERT_ID);
        coupon.setMaxUsage(2);

        Map<String, Long> usageFrequency = new HashMap<>();
        usageFrequency.put(TEST_COUPON_CODE, 2L);

        when(mockServicePlan.getAmount()).thenReturn(200.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(usageFrequency);

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getError().contains("maximum usage"));
    }

    @Test
    void testApplyCoupon_ExpiredCoupon() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.FLAT);
        coupon.setValue(50);
        coupon.setMinAmount(100);
        coupon.setMaxDiscount(100);
        coupon.setExpertId(TEST_EXPERT_ID);
        coupon.setExpiresAt(System.currentTimeMillis() - 1000); // Expired

        when(mockServicePlan.getAmount()).thenReturn(200.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act
        CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");

        // Assert
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getError().contains("expired"));
    }

    @Test
    void testApplyCoupon_NullValues() {
        // Arrange
        Coupon coupon = new Coupon();
        coupon.setCode(TEST_COUPON_CODE);
        coupon.setType(Coupon.CouponType.FLAT);
        // Intentionally not setting other values

        when(mockServicePlan.getAmount()).thenReturn(200.0);
        when(mockServicePlan.getExpertId()).thenReturn(TEST_EXPERT_ID);
        when(mockUser.getCouponUsageFrequency()).thenReturn(new HashMap<>());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            CouponService.applyCoupon(coupon, mockServicePlan, mockUser, "en");
        });
    }
}
