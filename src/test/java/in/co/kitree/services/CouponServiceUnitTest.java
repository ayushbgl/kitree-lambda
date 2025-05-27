package in.co.kitree.services;

import in.co.kitree.pojos.Coupon;
import in.co.kitree.pojos.FirebaseUser;
import in.co.kitree.pojos.ServicePlan;
import in.co.kitree.pojos.CouponResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CouponServiceUnitTest { // TODO
    // @Mock
    // private ServicePlan mockServicePlan;
    // @Mock
    // private FirebaseUser mockUser;

    // private static final String TEST_COUPON_CODE = "TEST50";
    // private static final String TEST_USER_ID = "test-user-id";
    // private static final String LANGUAGE = "en";

    // @BeforeEach
    // void setUp() {
    //     MockitoAnnotations.openMocks(this);
    //     when(mockUser.getUid()).thenReturn(TEST_USER_ID);
    // }

    // @Test
    // void testApplyCoupon_ValidFlatDiscount() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000)); // Yesterday
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000)); // Tomorrow
    //     coupon.setType(Coupon.CouponType.FLAT);
    //     coupon.setDiscount(50.0);
    //     coupon.setMinCartAmount(100.0);
    //     coupon.setMaxClaimsPerUser(3);
    //     coupon.setTotalUsageLimit(100);
    //     coupon.setClaimsMadeSoFar(50);

    //     when(mockServicePlan.getAmount()).thenReturn(200.0);
    //     Map<String, Integer> usageFrequency = new HashMap<>();
    //     usageFrequency.put(TEST_COUPON_CODE, 1);
    //     when(mockUser.getCouponUsageFrequency()).thenReturn(usageFrequency);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertTrue(result.isValid());
    //     assertEquals(50.0, result.getDiscount());
    //     assertEquals(150.0, result.getNewAmount());
    // }

    // @Test
    // void testApplyCoupon_ValidPercentageDiscount() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000));
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000));
    //     coupon.setType(Coupon.CouponType.PERCENTAGE);
    //     coupon.setDiscount(20.0);
    //     coupon.setMinCartAmount(100.0);
    //     coupon.setMaxClaimsPerUser(3);
    //     coupon.setTotalUsageLimit(100);
    //     coupon.setClaimsMadeSoFar(50);

    //     when(mockServicePlan.getAmount()).thenReturn(200.0);
    //     Map<String, Integer> usageFrequency = new HashMap<>();
    //     usageFrequency.put(TEST_COUPON_CODE, 1);
    //     when(mockUser.getCouponUsageFrequency()).thenReturn(usageFrequency);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertTrue(result.isValid());
    //     assertEquals(40.0, result.getDiscount());
    //     assertEquals(160.0, result.getNewAmount());
    // }

    // @Test
    // void testApplyCoupon_DisabledCoupon() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(false);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("coupon.not_enabled", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_FutureStartDate() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() + 86400000)); // Tomorrow

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("coupon.not_yet_started", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_ExpiredCoupon() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() - 86400000)); // Yesterday

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("Coupon is expired", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_MinimumCartAmountNotMet() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000));
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000));
    //     coupon.setMinCartAmount(100.0);

    //     when(mockServicePlan.getAmount()).thenReturn(50.0);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("Minimum cart amount not met", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_TotalUsageLimitReached() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000));
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000));
    //     coupon.setTotalUsageLimit(100);
    //     coupon.setClaimsMadeSoFar(100);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("Total usage limit reached", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_MaxClaimsPerUserReached() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000));
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000));
    //     coupon.setMaxClaimsPerUser(3);

    //     Map<String, Integer> usageFrequency = new HashMap<>();
    //     usageFrequency.put(TEST_COUPON_CODE, 3);
    //     when(mockUser.getCouponUsageFrequency()).thenReturn(usageFrequency);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("Max claims per user reached", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_UserNotInAllowedList() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000));
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000));
    //     coupon.setUserIdsAllowed(Arrays.asList("other-user-id"));

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertFalse(result.isValid());
    //     assertEquals("coupon.not_enabled", result.getMessage());
    // }

    // @Test
    // void testApplyCoupon_FlatDiscountExceedsAmount() {
    //     // Arrange
    //     Coupon coupon = new Coupon();
    //     coupon.setCode(TEST_COUPON_CODE);
    //     coupon.setEnabled(true);
    //     coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 86400000));
    //     coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 86400000));
    //     coupon.setType(Coupon.CouponType.FLAT);
    //     coupon.setDiscount(300.0);

    //     when(mockServicePlan.getAmount()).thenReturn(200.0);
    //     Map<String, Integer> usageFrequency = new HashMap<>();
    //     usageFrequency.put(TEST_COUPON_CODE, 1);
    //     when(mockUser.getCouponUsageFrequency()).thenReturn(usageFrequency);

    //     // Act
    //     CouponResult result = CouponService.applyCoupon(coupon, mockServicePlan, mockUser, LANGUAGE);

    //     // Assert
    //     assertNotNull(result);
    //     assertTrue(result.isValid());
    //     assertEquals(200.0, result.getDiscount());
    //     assertEquals(0.0, result.getNewAmount());
    // }
}
