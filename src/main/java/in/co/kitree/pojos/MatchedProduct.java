package in.co.kitree.pojos;

/**
 * Represents a product matched to an expert's remedy recommendation.
 * Used to display suggested products in consultation summaries.
 */
public class MatchedProduct {
    private String sku;
    private String name;
    private int priceInr;
    private String imageUrl;
    private String productType;  // bracelet, pendant, ring, mala, decor, book
    private boolean available;
    private String matchReason;  // Why this product matched (for debugging/display)

    public MatchedProduct() {
        this.available = true;
    }

    public MatchedProduct(String sku, String name, int priceInr, String imageUrl, String productType) {
        this.sku = sku;
        this.name = name;
        this.priceInr = priceInr;
        this.imageUrl = imageUrl;
        this.productType = productType;
        this.available = true;
    }

    public MatchedProduct(String sku, String name, int priceInr, String imageUrl,
                          String productType, String matchReason) {
        this(sku, name, priceInr, imageUrl, productType);
        this.matchReason = matchReason;
    }

    // Getters and Setters
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPriceInr() { return priceInr; }
    public void setPriceInr(int priceInr) { this.priceInr = priceInr; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }

    /**
     * Get formatted price with rupee symbol.
     */
    public String getFormattedPrice() {
        return "₹" + priceInr;
    }

    /**
     * Convert to map for Firestore storage.
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("sku", sku);
        map.put("name", name);
        map.put("price_inr", priceInr);
        map.put("formatted_price", getFormattedPrice());
        map.put("image_url", imageUrl);
        map.put("product_type", productType);
        map.put("available", available);
        if (matchReason != null) {
            map.put("match_reason", matchReason);
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchedProduct that = (MatchedProduct) o;
        return sku != null && sku.equals(that.sku);
    }

    @Override
    public int hashCode() {
        return sku != null ? sku.hashCode() : 0;
    }
}
