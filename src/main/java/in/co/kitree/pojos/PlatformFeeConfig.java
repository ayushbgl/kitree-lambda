package in.co.kitree.pojos;

import com.google.cloud.Timestamp;
import java.util.Map;

/**
 * POJO representing platform fee configuration for an expert.
 * Stored in users/{expertId}/private/platform_fee_config or users/{expertId}/public/store
 */
public class PlatformFeeConfig {
    private static final Double DEFAULT_FEE_PERCENT = 10.0;
    
    // Default platform fee percentage (e.g., 10.0 for 10%)
    private Double defaultFeePercent;
    
    // Fee percentage by order type (e.g., "CONSULTATION": 10.0, "PRODUCT": 15.0)
    private Map<String, Double> feeByType;
    
    // Fee percentage by category (e.g., "HOROSCOPE": 10.0, "TAROT": 12.0)
    private Map<String, Double> feeByCategory;
    
    // When this fee config became effective
    private Timestamp effectiveFrom;
    
    // When this fee config expires (for historical tracking)
    private Timestamp effectiveUntil;

    public PlatformFeeConfig() {
        this.defaultFeePercent = DEFAULT_FEE_PERCENT; // Default 10%
    }

    /**
     * Get the platform fee percentage for a given order type and category.
     * Priority: category-specific fee > type-specific fee > default fee
     * 
     * @param orderType The order type (e.g., "ON_DEMAND_CONSULTATION", "PRODUCT")
     * @param category The category (e.g., "HOROSCOPE", "TAROT"), can be null
     * @return The applicable fee percentage
     */
    public Double getFeePercent(String orderType, String category) {
        // Check category-specific fee first
        if (category != null && feeByCategory != null && feeByCategory.containsKey(category)) {
            return feeByCategory.get(category);
        }
        
        // Check type-specific fee
        if (orderType != null && feeByType != null && feeByType.containsKey(orderType)) {
            return feeByType.get(orderType);
        }
        
        // Return default fee
        return defaultFeePercent != null ? defaultFeePercent : DEFAULT_FEE_PERCENT;
    }

    // Getters and Setters
    public Double getDefaultFeePercent() { return defaultFeePercent; }
    public void setDefaultFeePercent(Double defaultFeePercent) { this.defaultFeePercent = defaultFeePercent; }

    public Map<String, Double> getFeeByType() { return feeByType; }
    public void setFeeByType(Map<String, Double> feeByType) { this.feeByType = feeByType; }

    public Map<String, Double> getFeeByCategory() { return feeByCategory; }
    public void setFeeByCategory(Map<String, Double> feeByCategory) { this.feeByCategory = feeByCategory; }

    public Timestamp getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Timestamp effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public Timestamp getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(Timestamp effectiveUntil) { this.effectiveUntil = effectiveUntil; }

    @Override
    public String toString() {
        return "PlatformFeeConfig{" +
                "defaultFeePercent=" + defaultFeePercent +
                ", feeByType=" + feeByType +
                ", feeByCategory=" + feeByCategory +
                ", effectiveFrom=" + effectiveFrom +
                ", effectiveUntil=" + effectiveUntil +
                '}';
    }
}
