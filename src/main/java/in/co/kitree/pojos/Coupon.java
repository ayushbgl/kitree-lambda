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
}
