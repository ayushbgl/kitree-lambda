package in.co.kitree.pojos;

import java.sql.Timestamp;
import java.util.List;


public class Coupon {

    public enum CouponType {
        FLAT, PERCENTAGE
    }

    private String code;

    public CouponType getType() {
        return type;
    }

    public void setType(CouponType type) {
        this.type = type;
    }

    private CouponType type;
    private double discount;
    private Timestamp startDate;
    private Timestamp endDate;
    private boolean isEnabled;
    private Boolean onlyForNewUsers;
    private Double minCartAmount;
    private Long totalUsageLimit;
    private Long maxClaimsPerUser;
    private Double maxDiscountAmount;
    private Long claimsMadeSoFar;

    private List<String> userIdsAllowed;

    public Coupon() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }


    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public Boolean getOnlyForNewUsers() {
        return onlyForNewUsers;
    }

    public void setOnlyForNewUsers(Boolean onlyForNewUsers) {
        this.onlyForNewUsers = onlyForNewUsers;
    }

    public Double getMinCartAmount() {
        return minCartAmount;
    }

    public void setMinCartAmount(Double minCartAmount) {
        this.minCartAmount = minCartAmount;
    }

    public Long getTotalUsageLimit() {
        return totalUsageLimit;
    }

    public void setTotalUsageLimit(Long totalUsageLimit) {
        this.totalUsageLimit = totalUsageLimit;
    }

    public Long getMaxClaimsPerUser() {
        return maxClaimsPerUser;
    }

    public void setMaxClaimsPerUser(Long maxClaimsPerUser) {
        this.maxClaimsPerUser = maxClaimsPerUser;
    }

    public Double getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(Double maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public Long getClaimsMadeSoFar() {
        return claimsMadeSoFar;
    }

    public void setClaimsMadeSoFar(Long claimsMadeSoFar) {
        this.claimsMadeSoFar = claimsMadeSoFar;
    }

    public List<String> getUserIdsAllowed() {
        return userIdsAllowed;
    }

    public void setUserIdsAllowed(List<String> userIdsAllowed) {
        this.userIdsAllowed = userIdsAllowed;
    }

    @Override
    public String toString() {
        return "Coupon{" +
                "code='" + code + '\'' +
                ", type=" + type +
                ", discount=" + discount +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", isEnabled=" + isEnabled +
                ", onlyForNewUsers=" + onlyForNewUsers +
                ", minCartAmount=" + minCartAmount +
                ", totalUsageLimit=" + totalUsageLimit +
                ", maxClaimsPerUser=" + maxClaimsPerUser +
                ", maxDiscountAmount=" + maxDiscountAmount +
                ", claimsMadeSoFar=" + claimsMadeSoFar +
                ", userIdsAllowed=" + userIdsAllowed +
                '}';
    }
}
