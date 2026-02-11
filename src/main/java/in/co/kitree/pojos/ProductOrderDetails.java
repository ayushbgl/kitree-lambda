package in.co.kitree.pojos;

import com.google.cloud.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents product-specific order details.
 * Used alongside the existing order structure when type = "PRODUCT".
 *
 * Supports both single-item orders (backward compatible) and multi-item orders.
 * - Single-item: uses productId, sku, productName, quantity, unitPrice fields directly
 * - Multi-item: uses items[] array, with aggregated totals in subtotal, shippingCost, amount
 *
 * These fields extend the base order in: users/{userId}/orders/{orderId}
 */
public class ProductOrderDetails {
    // Order identification
    private String orderId;
    private String expertId;

    // Multi-item support: list of line items
    // When items is non-empty, this is a multi-item order
    private List<OrderLineItem> items;

    // Single-item fields (for backward compatibility with existing orders)
    // Product identification (snapshot at time of order)
    private String productId;
    private String sku;
    private String productName;
    private String productImageUrl;
    private Integer quantity;

    // Pricing breakdown
    private Double unitPrice;       // Price per unit (single-item only)
    private Double subtotal;        // Sum of (unitPrice * quantity) for all items
    private Double shippingCost;    // Total shipping cost
    private Double amount;          // Grand total (subtotal + shippingCost)

    // Seller configuration at time of order (snapshot)
    // For multi-item: these reflect the dominant or first item's config
    private boolean isWhiteLabel;
    private String shippingMode;    // "PLATFORM" or "SELF"

    // Commission/earnings (snapshot at time of order)
    // For multi-item: these are aggregated totals across all items
    private Double platformFeePercent;  // Average or dominant rate
    private Double platformFeeAmount;   // Total platform fee
    private Double expertEarnings;      // Total expert earnings

    // Shipping details
    private Map<String, Object> address;
    private String trackingNumber;
    private Timestamp shippedAt;
    private Timestamp deliveredAt;

    // Order status for product orders
    // INITIATED -> PAID -> PROCESSING -> SHIPPED -> DELIVERED (or CANCELLED/REFUNDED)
    private String status;

    public ProductOrderDetails() {
        this.items = new ArrayList<>();
    }

    // Order status constants
    public static final String STATUS_INITIATED = "INITIATED";
    public static final String STATUS_PAYMENT_PENDING = "PAYMENT_PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getExpertId() { return expertId; }
    public void setExpertId(String expertId) { this.expertId = expertId; }

    public List<OrderLineItem> getItems() { return items; }
    public void setItems(List<OrderLineItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    /**
     * Check if this is a multi-item order.
     */
    public boolean isMultiItemOrder() {
        return items != null && !items.isEmpty();
    }

    /**
     * Get total item count across all line items.
     */
    public int getTotalItemCount() {
        if (!isMultiItemOrder()) {
            return quantity != null ? quantity : 1;
        }
        return items.stream().mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 1).sum();
    }

    /**
     * Get distinct product count.
     */
    public int getDistinctProductCount() {
        if (!isMultiItemOrder()) {
            return 1;
        }
        return items.size();
    }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }

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

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

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

    public Map<String, Object> getAddress() { return address; }
    public void setAddress(Map<String, Object> address) { this.address = address; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public Timestamp getShippedAt() { return shippedAt; }
    public void setShippedAt(Timestamp shippedAt) { this.shippedAt = shippedAt; }

    public Timestamp getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Timestamp deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * Check if this order requires platform shipping.
     */
    public boolean requiresPlatformShipping() {
        return ExpertProductConfig.SHIPPING_PLATFORM.equals(shippingMode);
    }

    /**
     * Check if order can be shipped (is paid and not yet shipped).
     */
    public boolean canBeShipped() {
        return STATUS_PAID.equals(status) || STATUS_PROCESSING.equals(status);
    }

    /**
     * Calculate total amount from unit price, quantity, and shipping.
     */
    public void calculateAmount() {
        double total = (unitPrice != null ? unitPrice : 0.0) * (quantity != null ? quantity : 1);
        total += (shippingCost != null ? shippingCost : 0.0);
        this.amount = total;
    }

    /**
     * Convert to Map for Firestore storage.
     * These fields are merged with base order fields.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("order_id", orderId);
        map.put("expert_id", expertId);

        // Multi-item order support
        if (isMultiItemOrder()) {
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (OrderLineItem item : items) {
                itemMaps.add(item.toMap());
            }
            map.put("items", itemMaps);
        }

        // Single-item fields (also kept for backward compatibility display)
        map.put("productId", productId);
        map.put("sku", sku);
        map.put("productName", productName);
        map.put("productImageUrl", productImageUrl);
        map.put("quantity", quantity);
        map.put("unitPrice", unitPrice);
        map.put("subtotal", subtotal);
        map.put("shippingCost", shippingCost);
        map.put("amount", amount);
        map.put("isWhiteLabel", isWhiteLabel);
        map.put("shippingMode", shippingMode);
        map.put("platformFeePercent", platformFeePercent);
        map.put("platformFeeAmount", platformFeeAmount);
        map.put("expertEarnings", expertEarnings);
        map.put("address", address);
        if (trackingNumber != null) {
            map.put("trackingNumber", trackingNumber);
        }
        if (shippedAt != null) {
            map.put("shippedAt", shippedAt);
        }
        if (deliveredAt != null) {
            map.put("deliveredAt", deliveredAt);
        }
        map.put("status", status);
        return map;
    }

    /**
     * Create from Firestore document data.
     */
    @SuppressWarnings("unchecked")
    public static ProductOrderDetails fromMap(Map<String, Object> map) {
        if (map == null) return null;

        ProductOrderDetails details = new ProductOrderDetails();
        details.setOrderId((String) map.get("order_id"));
        details.setExpertId((String) map.get("expert_id"));

        // Parse items array if present (multi-item order)
        Object itemsObj = map.get("items");
        if (itemsObj instanceof List) {
            List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) itemsObj;
            List<OrderLineItem> items = new ArrayList<>();
            for (Map<String, Object> itemMap : itemMaps) {
                OrderLineItem item = OrderLineItem.fromMap(itemMap);
                if (item != null) {
                    items.add(item);
                }
            }
            details.setItems(items);
        }

        // Single-item fields (also used for display even in multi-item orders)
        details.setProductId((String) map.get("productId"));
        details.setSku((String) map.get("sku"));
        details.setProductName((String) map.get("productName"));
        details.setProductImageUrl((String) map.get("productImageUrl"));
        details.setQuantity(map.get("quantity") != null ? ((Number) map.get("quantity")).intValue() : 1);
        details.setUnitPrice(map.get("unitPrice") != null ? ((Number) map.get("unitPrice")).doubleValue() : null);
        details.setSubtotal(map.get("subtotal") != null ? ((Number) map.get("subtotal")).doubleValue() : null);
        details.setShippingCost(map.get("shippingCost") != null ? ((Number) map.get("shippingCost")).doubleValue() : null);
        details.setAmount(map.get("amount") != null ? ((Number) map.get("amount")).doubleValue() : null);
        details.setWhiteLabel(Boolean.TRUE.equals(map.get("isWhiteLabel")));
        details.setShippingMode((String) map.get("shippingMode"));
        details.setPlatformFeePercent(map.get("platformFeePercent") != null ? ((Number) map.get("platformFeePercent")).doubleValue() : null);
        details.setPlatformFeeAmount(map.get("platformFeeAmount") != null ? ((Number) map.get("platformFeeAmount")).doubleValue() : null);
        details.setExpertEarnings(map.get("expertEarnings") != null ? ((Number) map.get("expertEarnings")).doubleValue() : null);
        details.setAddress((Map<String, Object>) map.get("address"));
        details.setTrackingNumber((String) map.get("trackingNumber"));
        details.setStatus((String) map.get("status"));

        Object shippedAt = map.get("shippedAt");
        if (shippedAt instanceof Timestamp) {
            details.setShippedAt((Timestamp) shippedAt);
        }

        Object deliveredAt = map.get("deliveredAt");
        if (deliveredAt instanceof Timestamp) {
            details.setDeliveredAt((Timestamp) deliveredAt);
        }

        return details;
    }

    @Override
    public String toString() {
        return "ProductOrderDetails{" +
                "productId='" + productId + '\'' +
                ", sku='" + sku + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", amount=" + amount +
                ", isWhiteLabel=" + isWhiteLabel +
                ", shippingMode='" + shippingMode + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
