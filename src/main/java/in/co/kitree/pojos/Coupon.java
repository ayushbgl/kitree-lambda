package in.co.kitree.pojos;

import java.sql.Timestamp;
import java.util.List;
import lombok.Data;

@Data
public class Coupon {

    public enum CouponType {
        FLAT, PERCENTAGE
    }

    private String code;
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
    private Integer value;
    private Integer minAmount;
    private Integer maxDiscount;
    private String expertId;
    private Integer maxUsage;
    private Long expiresAt;

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

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public Integer getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(Integer minAmount) {
        this.minAmount = minAmount;
    }

    public Integer getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(Integer maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public String getExpertId() {
        return expertId;
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
