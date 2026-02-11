package in.co.kitree.pojos;

import com.google.cloud.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Represents a platform-controlled product in the catalog.
 * Products are managed by Kitree, not by individual experts.
 *
 * Firestore collection: platform_products/{productId}
 */
public class PlatformProduct {
    // Core identification
    private String productId;
    private String sku;

    // Product details (platform controlled)
    private String name;
    private String description;
    private String shortDescription;

    // Media
    private List<String> images;
    private String thumbnailUrl;

    // Categorization
    private String productType;  // bracelet, pendant, ring, mala, decor, book, bowl, pyramid, lamp
    private String category;     // CRYSTALS, GEMSTONES, TRADITIONAL, MEDITATION, HOME_DECOR
    private List<String> tags;

    // Pricing
    private Double suggestedPriceInr;  // Platform suggested MRP
    private Double minPriceInr;        // Minimum allowed seller price
    private Double costPriceInr;       // Platform's cost (for margin calculations)

    // Shipping info (for platform shipping)
    private Integer weightGrams;
    private Map<String, Integer> dimensionsCm;  // {l, w, h}
    private Double shippingCostInr;

    // Stock & availability
    private boolean platformStockAvailable;
    private Integer platformStockQuantity;

    // Status
    private boolean isActive;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Search/matching attributes (from existing MatchedProduct concept)
    private ProductMatchingAttributes matchingAttributes;

    public PlatformProduct() {}

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Double getSuggestedPriceInr() { return suggestedPriceInr; }
    public void setSuggestedPriceInr(Double suggestedPriceInr) { this.suggestedPriceInr = suggestedPriceInr; }

    public Double getMinPriceInr() { return minPriceInr; }
    public void setMinPriceInr(Double minPriceInr) { this.minPriceInr = minPriceInr; }

    public Double getCostPriceInr() { return costPriceInr; }
    public void setCostPriceInr(Double costPriceInr) { this.costPriceInr = costPriceInr; }

    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }

    public Map<String, Integer> getDimensionsCm() { return dimensionsCm; }
    public void setDimensionsCm(Map<String, Integer> dimensionsCm) { this.dimensionsCm = dimensionsCm; }

    public Double getShippingCostInr() { return shippingCostInr; }
    public void setShippingCostInr(Double shippingCostInr) { this.shippingCostInr = shippingCostInr; }

    public boolean isPlatformStockAvailable() { return platformStockAvailable; }
    public void setPlatformStockAvailable(boolean platformStockAvailable) { this.platformStockAvailable = platformStockAvailable; }

    public Integer getPlatformStockQuantity() { return platformStockQuantity; }
    public void setPlatformStockQuantity(Integer platformStockQuantity) { this.platformStockQuantity = platformStockQuantity; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public ProductMatchingAttributes getMatchingAttributes() { return matchingAttributes; }
    public void setMatchingAttributes(ProductMatchingAttributes matchingAttributes) { this.matchingAttributes = matchingAttributes; }

    /**
     * Check if product has sufficient stock for platform shipping.
     */
    public boolean hasStock(int quantity) {
        return platformStockAvailable && platformStockQuantity != null && platformStockQuantity >= quantity;
    }

    /**
     * Convert to Map for Firestore storage.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("productId", productId);
        map.put("sku", sku);
        map.put("name", name);
        map.put("description", description);
        map.put("shortDescription", shortDescription);
        map.put("images", images);
        map.put("thumbnailUrl", thumbnailUrl);
        map.put("productType", productType);
        map.put("category", category);
        map.put("tags", tags);
        map.put("suggestedPriceInr", suggestedPriceInr);
        map.put("minPriceInr", minPriceInr);
        map.put("costPriceInr", costPriceInr);
        map.put("weightGrams", weightGrams);
        map.put("dimensionsCm", dimensionsCm);
        map.put("shippingCostInr", shippingCostInr);
        map.put("platformStockAvailable", platformStockAvailable);
        map.put("platformStockQuantity", platformStockQuantity);
        map.put("isActive", isActive);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        if (matchingAttributes != null) {
            map.put("matchingAttributes", matchingAttributes.toMap());
        }
        return map;
    }

    /**
     * Nested class for product matching attributes.
     */
    public static class ProductMatchingAttributes {
        private List<String> material;  // rose_quartz, citrine, pyrite
        private List<String> planets;   // venus, jupiter, saturn
        private List<String> purpose;   // love, wealth, protection
        private List<String> chakras;   // heart, solar_plexus, root
        private List<String> colors;    // pink, yellow, gold

        public ProductMatchingAttributes() {}

        public List<String> getMaterial() { return material; }
        public void setMaterial(List<String> material) { this.material = material; }

        public List<String> getPlanets() { return planets; }
        public void setPlanets(List<String> planets) { this.planets = planets; }

        public List<String> getPurpose() { return purpose; }
        public void setPurpose(List<String> purpose) { this.purpose = purpose; }

        public List<String> getChakras() { return chakras; }
        public void setChakras(List<String> chakras) { this.chakras = chakras; }

        public List<String> getColors() { return colors; }
        public void setColors(List<String> colors) { this.colors = colors; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("material", material);
            map.put("planets", planets);
            map.put("purpose", purpose);
            map.put("chakras", chakras);
            map.put("colors", colors);
            return map;
        }
    }

    @Override
    public String toString() {
        return "PlatformProduct{" +
                "productId='" + productId + '\'' +
                ", sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", productType='" + productType + '\'' +
                ", category='" + category + '\'' +
                ", suggestedPriceInr=" + suggestedPriceInr +
                ", isActive=" + isActive +
                '}';
    }
}
