package in.co.kitree.services;

import in.co.kitree.pojos.Coupon;
import in.co.kitree.pojos.ServicePlan;
import in.co.kitree.pojos.FirebaseUser;
import in.co.kitree.pojos.CouponResult;

public class CouponService {
    public static CouponResult applyCoupon(Coupon coupon, ServicePlan servicePlan, FirebaseUser user, String language) {
        // Validate inputs
        if (coupon == null || servicePlan == null || user == null) {
            throw new IllegalArgumentException("Coupon, service plan, and user must not be null");
        }
        if (coupon.getCode() == null) {
            throw new IllegalArgumentException("Coupon code must not be null");
        }
        if (coupon.getType() == null) {
            throw new IllegalArgumentException("Coupon type must not be null");
        }
        if (coupon.getValue() == null) {
            throw new IllegalArgumentException("Coupon value must not be null");
        }
        if (coupon.getMinAmount() == null) {
            throw new IllegalArgumentException("Coupon minimum amount must not be null");
        }
        if (coupon.getMaxDiscount() == null) {
            throw new IllegalArgumentException("Coupon max discount must not be null");
        }
        if (coupon.getExpertId() == null) {
            throw new IllegalArgumentException("Coupon expertId must not be null");
        }
        if (servicePlan.getAmount() == null) {
            throw new IllegalArgumentException("Service plan amount must not be null");
        }
        if (servicePlan.getExpertId() == null) {
            throw new IllegalArgumentException("Service plan expertId must not be null");
        }
        if (user.getCouponUsageFrequency() == null) {
            throw new IllegalArgumentException("User coupon usage frequency must not be null");
        }

        // Create result object
        CouponResult result = new CouponResult();
        result.setValid(false);

        // Check if coupon is expired
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt() < System.currentTimeMillis()) {
            result.setError("Coupon is expired");
            return result;
        }

        // Check minimum amount
        if (servicePlan.getAmount() < coupon.getMinAmount()) {
            result.setError("Order amount is below the minimum amount required for this coupon");
            return result;
        }

        // Check expert ID match
        if (!coupon.getExpertId().equals(servicePlan.getExpertId())) {
            result.setError("Coupon is not valid for this expert");
            return result;
        }

        // Check max usage
        if (coupon.getMaxUsage() != null) {
            Long usageCount = user.getCouponUsageFrequency().getOrDefault(coupon.getCode(), 0L);
            if (usageCount >= coupon.getMaxUsage()) {
                result.setError("Coupon has reached its maximum usage limit");
                return result;
            }
        }

        // Calculate discount
        double discountAmount;
        if (coupon.getType() == Coupon.CouponType.FLAT) {
            discountAmount = coupon.getValue();
        } else { // PERCENTAGE
            discountAmount = (servicePlan.getAmount() * coupon.getValue()) / 100;
        }

        // Apply max discount cap
        if (coupon.getMaxDiscount() != null && discountAmount > coupon.getMaxDiscount()) {
            discountAmount = coupon.getMaxDiscount();
        }

        // Set final amounts
        double finalAmount = servicePlan.getAmount() - discountAmount;
        result.setValid(true);
        result.setDiscount(discountAmount);
        result.setNewAmount(finalAmount);

        return result;
    }
}
