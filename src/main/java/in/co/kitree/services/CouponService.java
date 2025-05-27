package in.co.kitree.services;

import in.co.kitree.pojos.Coupon;
import in.co.kitree.pojos.CouponResult;
import in.co.kitree.pojos.FirebaseUser;
import in.co.kitree.pojos.ServicePlan;

import java.sql.Timestamp;

public class CouponService {

    public CouponService() {
    }

    public static CouponResult applyCoupon(Coupon coupon, ServicePlan plan, FirebaseUser user, String language) {
        CouponResult result = new CouponResult();

        // Check if user is in allowed list
        if(coupon.getUserIdsAllowed() != null && !coupon.getUserIdsAllowed().isEmpty() && !coupon.getUserIdsAllowed().contains(user.getUid())) {
            result.setValid(false);
            result.setMessage(TranslationService.translate("coupon.not_enabled", language));
            return result;
        }

        // Check if coupon is enabled
        if (!coupon.isEnabled()) {
            result.setValid(false);
            result.setMessage(TranslationService.translate("coupon.not_enabled", language));
            return result;
        }

        // Check start date
        if (coupon.getStartDate().after(new Timestamp(System.currentTimeMillis()))) {
            result.setValid(false);
            result.setMessage(TranslationService.translate("coupon.not_yet_started", language));
            return result;
        }

        // Check end date
        if (coupon.getEndDate().before(new Timestamp(System.currentTimeMillis()))) {
            result.setValid(false);
            result.setMessage("Coupon is expired");
            return result;
        }

        // Check minimum cart amount
        if (coupon.getMinCartAmount() != null && coupon.getMinCartAmount() > plan.getAmount()) {
            result.setValid(false);
            result.setMessage("Minimum cart amount not met");
            return result;
        }

        // Check total usage limit
        if (coupon.getTotalUsageLimit() != null && coupon.getClaimsMadeSoFar() != null && coupon.getTotalUsageLimit() <= coupon.getClaimsMadeSoFar()) {
            result.setValid(false);
            result.setMessage("Total usage limit reached");
            return result;
        }

        // Check max claims per user
        if (coupon.getMaxClaimsPerUser() != null
                && coupon.getMaxClaimsPerUser() <= user.getCouponUsageFrequency().get(coupon.getCode())
        ) {
            result.setValid(false);
            result.setMessage("Max claims per user reached");
            return result;
        }

        // Calculate discount based on type
        if (coupon.getType().equals(Coupon.CouponType.FLAT)) {
            result.setDiscount(Math.min(coupon.getDiscount(), plan.getAmount()));
            result.setNewAmount(Math.max(0, plan.getAmount() - coupon.getDiscount()));
        }

        if (coupon.getType().equals(Coupon.CouponType.PERCENTAGE)) {
            result.setDiscount(Math.ceil(plan.getAmount() * coupon.getDiscount()) / 100);
            result.setNewAmount(plan.getAmount() - result.getDiscount());
        }

        result.setValid(true);
        result.setMessage("Coupon applied successfully");
        return result;
    }
}
