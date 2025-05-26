package in.co.kitree.services;

import in.co.kitree.pojos.Coupon;
import in.co.kitree.pojos.CouponResult;
import in.co.kitree.pojos.FirebaseUser;
import in.co.kitree.pojos.ServicePlan;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CouponServiceTest {

    @Test
    public void testFlatCoupon() {

        Coupon coupon = new Coupon();
        coupon.setDiscount(10.0);
        coupon.setMinCartAmount(100.0);
        coupon.setEnabled(true);
        coupon.setType(Coupon.CouponType.FLAT);
        coupon.setStartDate(new Timestamp(System.currentTimeMillis() - 100000));
        coupon.setEndDate(new Timestamp(System.currentTimeMillis() + 100000));

        ServicePlan plan = new ServicePlan();
        plan.setAmount(100.00);

        FirebaseUser user = new FirebaseUser();
        user.setCouponUsageFrequency(new HashMap<>());
        user.getCouponUsageFrequency().put(coupon.getCode(), 0L);

        CouponResult result = CouponService.applyCoupon(coupon, plan, user, "en");
        System.out.println(result.getMessage());
        assertEquals(90.0, result.getNewAmount());
    }
}
