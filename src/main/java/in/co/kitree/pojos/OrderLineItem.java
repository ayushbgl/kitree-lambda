package in.co.kitree.pojos;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single line item in a multi-item product order.
 * Each line item corresponds to one product with its quantity and pricing.
 *
 * Used within ProductOrderDetails.items[] for multi-item orders.
 */
public class OrderLineItem {
    // Product identification (snapshot at time of order)
    private String productId;
    private String sku;
    private String productName;
    private String productImageUrl;
    private Integer quantity;

    // Pricing for this line item
    private Double unitPrice;       // Price per unit
    private Double shippingCost;    // Shipping cost for this item
    private Double lineTotal;       // (unitPrice * quantity) + shippingCost

    // Seller configuration at time of order (snapshot)
    private boolean isWhiteLabel;
    private String shippingMode;    // "PLATFORM" or "SELF"

    // Commission/earnings for this line item
    private Double platformFeePercent;
    private Double platformFeeAmount;
    private Double expertEarnings;

    public OrderLineItem() {}

    // Getters and Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

    public Double getShippingCost() { return shippingCost; }
    public void setShippingCost(Double shippingCost) { this.shippingCost = shippingCost; }

    public Double getLineTotal() { return lineTotal; }
    public void setLineTotal(Double lineTotal) { this.lineTotal = lineTotal; }

    public boolean isWhiteLabel() { return isWhiteLabel; }
    public void setWhiteLabel(boolean whiteLabel) { isWhiteLabel = whiteLabel; }

    public String getShippingMode() { return shippingMode; }
    public void setShippingMode(String shippingMode) { this.shippingMode = shippingMode; }

    public Double getPlatformFeePercent() { return platformFeePercent; }
    public void setPlatformFeePercent(Double platformFeePercent) { this.platformFeePercent = platformFeePercent; }

    public Double getPlatformFeeAmount() { return platformFeeAmount; }
    public void setPlatformFeeAmount(Double platformFeeAmount) { this.platformFeeAmount = platformFeeAmount; }

    public Double getExpertEarnings() { return expertEarnings; }
    public void setExpertEarnings(Double expertEarnings) { this.expertEarnings = expertEarnings; }

    /**
     * Calculate line total from unit price, quantity, and shipping.
     */
    public void calculateLineTotal() {
        double subtotal = (unitPrice != null ? unitPrice : 0.0) * (quantity != null ? quantity : 1);
        this.lineTotal = subtotal + (shippingCost != null ? shippingCost : 0.0);
    }

    /**
     * Get subtotal (without shipping).
     */
    public Double getSubtotal() {
        return (unitPrice != null ? unitPrice : 0.0) * (quantity != null ? quantity : 1);
    }

    /**
     * Check if this item requires platform shipping.
     */
    public boolean requiresPlatformShipping() {
        return ExpertProductConfig.SHIPPING_PLATFORM.equals(shippingMode);
    }

    /**
     * Convert to Map for Firestore storage.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", productId);
        map.put("sku", sku);
        map.put("productName", productName);
        map.put("productImageUrl", productImageUrl);
        map.put("quantity", quantity);
        map.put("unitPrice", unitPrice);
        map.put("shippingCost", shippingCost);
        map.put("lineTotal", lineTotal);
        map.put("isWhiteLabel", isWhiteLabel);
        map.put("shippingMode", shippingMode);
        map.put("platformFeePercent", platformFeePercent);
        map.put("platformFeeAmount", platformFeeAmount);
        map.put("expertEarnings", expertEarnings);
        return map;
    }

    /**
     * Create from Firestore document data.
     */
    public static OrderLineItem fromMap(Map<String, Object> map) {
        if (map == null) return null;

        OrderLineItem item = new OrderLineItem();
        item.setProductId((String) map.get("productId"));
        item.setSku((String) map.get("sku"));
        item.setProductName((String) map.get("productName"));
        item.setProductImageUrl((String) map.get("productImageUrl"));
        item.setQuantity(map.get("quantity") != null ? ((Number) map.get("quantity")).intValue() : 1);
        item.setUnitPrice(map.get("unitPrice") != null ? ((Number) map.get("unitPrice")).doubleValue() : null);
        item.setShippingCost(map.get("shippingCost") != null ? ((Number) map.get("shippingCost")).doubleValue() : null);
        item.setLineTotal(map.get("lineTotal") != null ? ((Number) map.get("lineTotal")).doubleValue() : null);
        item.setWhiteLabel(Boolean.TRUE.equals(map.get("isWhiteLabel")));
        item.setShippingMode((String) map.get("shippingMode"));
        item.setPlatformFeePercent(map.get("platformFeePercent") != null ? ((Number) map.get("platformFeePercent")).doubleValue() : null);
        item.setPlatformFeeAmount(map.get("platformFeeAmount") != null ? ((Number) map.get("platformFeeAmount")).doubleValue() : null);
        item.setExpertEarnings(map.get("expertEarnings") != null ? ((Number) map.get("expertEarnings")).doubleValue() : null);
        return item;
    }

    @Override
    public String toString() {
        return "OrderLineItem{" +
                "productId='" + productId + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", lineTotal=" + lineTotal +
                '}';
    }
}
