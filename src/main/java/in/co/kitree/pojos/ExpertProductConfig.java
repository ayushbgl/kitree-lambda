package in.co.kitree.pojos;

import com.google.cloud.Timestamp;
import java.util.Map;

/**
 * Represents an expert's configuration for selling a platform product.
 * Experts can enable/disable products, set their own price, and choose shipping options.
 *
 * Firestore collection: users/{expertId}/seller_products/{productId}
 */
public class ExpertProductConfig {
    // References
    private String productId;      // Reference to platform_products/{productId}
    private String expertId;       // Owner expert

    // Seller configuration
    private boolean isEnabled;     // Is seller selling this product?
    private Double sellerPriceInr; // Seller's selling price (must be >= minPriceInr)

    // White-label option
    private boolean isWhiteLabel;  // true = platform branding removed (higher commission)

    // Shipping option
    private String shippingMode;   // "PLATFORM" or "SELF"
    private Double selfShippingCostInr;  // If SELF, seller's shipping charge
    private Integer selfStockQuantity;   // Seller's own inventory count (for SELF shipping)

    // Status
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Transient: joined product data (not stored, used for API responses)
    private transient PlatformProduct product;

    public ExpertProductConfig() {}

    // Shipping mode constants
    public static final String SHIPPING_PLATFORM = "PLATFORM";
    public static final String SHIPPING_SELF = "SELF";

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getExpertId() { return expertId; }
    public void setExpertId(String expertId) { this.expertId = expertId; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public Double getSellerPriceInr() { return sellerPriceInr; }
    public void setSellerPriceInr(Double sellerPriceInr) { this.sellerPriceInr = sellerPriceInr; }

    public boolean isWhiteLabel() { return isWhiteLabel; }
    public void setWhiteLabel(boolean whiteLabel) { isWhiteLabel = whiteLabel; }

    public String getShippingMode() { return shippingMode; }
    public void setShippingMode(String shippingMode) { this.shippingMode = shippingMode; }

    public Double getSelfShippingCostInr() { return selfShippingCostInr; }
    public void setSelfShippingCostInr(Double selfShippingCostInr) { this.selfShippingCostInr = selfShippingCostInr; }

    public Integer getSelfStockQuantity() { return selfStockQuantity; }
    public void setSelfStockQuantity(Integer selfStockQuantity) { this.selfStockQuantity = selfStockQuantity; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public PlatformProduct getProduct() { return product; }
    public void setProduct(PlatformProduct product) { this.product = product; }

    /**
     * Check if this config uses platform shipping.
     */
    public boolean isPlatformShipping() {
        return SHIPPING_PLATFORM.equals(shippingMode);
    }

    /**
     * Check if this config uses self shipping.
     */
    public boolean isSelfShipping() {
        return SHIPPING_SELF.equals(shippingMode);
    }

    /**
     * Get effective shipping cost based on mode.
     */
    public Double getEffectiveShippingCost(PlatformProduct platformProduct) {
        if (isSelfShipping()) {
            return selfShippingCostInr != null ? selfShippingCostInr : 0.0;
        }
        return platformProduct != null ? platformProduct.getShippingCostInr() : 0.0;
    }

    /**
     * Check if seller has stock (for self-shipping).
     */
    public boolean hasStock(int quantity) {
        if (isPlatformShipping()) {
            return true; // Platform handles stock
        }
        return selfStockQuantity != null && selfStockQuantity >= quantity;
    }

    /**
     * Convert to Map for Firestore storage.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("productId", productId);
        map.put("expertId", expertId);
        map.put("isEnabled", isEnabled);
        map.put("sellerPriceInr", sellerPriceInr);
        map.put("isWhiteLabel", isWhiteLabel);
        map.put("shippingMode", shippingMode);
        map.put("selfShippingCostInr", selfShippingCostInr);
        map.put("selfStockQuantity", selfStockQuantity);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    /**
     * Create from Firestore document data.
     */
    @SuppressWarnings("unchecked")
    public static ExpertProductConfig fromMap(Map<String, Object> map) {
        if (map == null) return null;

        ExpertProductConfig config = new ExpertProductConfig();
        config.setProductId((String) map.get("productId"));
        config.setExpertId((String) map.get("expertId"));
        config.setEnabled(Boolean.TRUE.equals(map.get("isEnabled")));
        config.setSellerPriceInr(map.get("sellerPriceInr") != null ? ((Number) map.get("sellerPriceInr")).doubleValue() : null);
        config.setWhiteLabel(Boolean.TRUE.equals(map.get("isWhiteLabel")));
        config.setShippingMode((String) map.get("shippingMode"));
        config.setSelfShippingCostInr(map.get("selfShippingCostInr") != null ? ((Number) map.get("selfShippingCostInr")).doubleValue() : null);
        config.setSelfStockQuantity(map.get("selfStockQuantity") != null ? ((Number) map.get("selfStockQuantity")).intValue() : null);

        Object createdAt = map.get("createdAt");
        if (createdAt instanceof Timestamp) {
            config.setCreatedAt((Timestamp) createdAt);
        }

        Object updatedAt = map.get("updatedAt");
        if (updatedAt instanceof Timestamp) {
            config.setUpdatedAt((Timestamp) updatedAt);
        }

        return config;
    }

    @Override
    public String toString() {
        return "ExpertProductConfig{" +
                "productId='" + productId + '\'' +
                ", expertId='" + expertId + '\'' +
                ", isEnabled=" + isEnabled +
                ", sellerPriceInr=" + sellerPriceInr +
                ", isWhiteLabel=" + isWhiteLabel +
                ", shippingMode='" + shippingMode + '\'' +
                '}';
    }
}
