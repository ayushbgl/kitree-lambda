package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import in.co.kitree.pojos.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing product orders.
 * Handles order creation, status updates, and admin queries for platform shipping.
 *
 * Orders stored at: users/{userId}/orders/{orderId} with type="PRODUCT"
 */
public class ProductOrderService {
    private static final String ORDERS_COLLECTION = "orders";
    private static final String ORDER_TYPE = "PRODUCT";

    // Default commission rates (can be overridden per expert)
    private static final double DEFAULT_COMMISSION_PERCENT = 10.0;
    private static final double WHITE_LABEL_COMMISSION_PERCENT = 20.0;

    private final Firestore db;
    private final ProductCatalogService catalogService;
    private final ExpertProductService expertProductService;
    private final ExpertEarningsService earningsService;

    public ProductOrderService(
            Firestore db,
            ProductCatalogService catalogService,
            ExpertProductService expertProductService,
            ExpertEarningsService earningsService) {
        this.db = db;
        this.catalogService = catalogService;
        this.expertProductService = expertProductService;
        this.earningsService = earningsService;
    }

    /**
     * Create a product order (before payment).
     * Returns order ID for Razorpay payment flow.
     */
    public Map<String, Object> createOrder(
            String userId,
            String expertId,
            String productId,
            int quantity,
            Map<String, Object> address,
            String couponCode
    ) throws ExecutionException, InterruptedException {
        // Validate inputs
        if (userId == null || expertId == null || productId == null) {
            throw new IllegalArgumentException("User ID, Expert ID, and Product ID are required");
        }

        // Get expert's product configuration
        ExpertProductConfig config = expertProductService.getExpertProductConfigWithDetails(expertId, productId);
        if (config == null || !config.isEnabled()) {
            throw new IllegalArgumentException("Product not available from this expert");
        }

        PlatformProduct product = config.getProduct();
        if (product == null || !product.isActive()) {
            throw new IllegalArgumentException("Product not found or inactive");
        }

        // Check stock
        if (!expertProductService.checkExpertStock(expertId, productId, quantity)) {
            throw new IllegalArgumentException("Insufficient stock for requested quantity");
        }

        // Calculate pricing
        double unitPrice = config.getSellerPriceInr() != null ? config.getSellerPriceInr() : product.getSuggestedPriceInr();
        double shippingCost = config.getEffectiveShippingCost(product);
        double subtotal = unitPrice * quantity;
        double totalAmount = subtotal + shippingCost;

        // Get commission rates (per-expert or default)
        PlatformFeeConfig feeConfig = getExpertFeeConfig(expertId);
        double commissionPercent = getProductCommissionPercent(feeConfig, config.isWhiteLabel());
        double platformFeeAmount = subtotal * (commissionPercent / 100.0);
        double expertEarnings = subtotal - platformFeeAmount;

        // If platform shipping, platform keeps shipping cost
        if (config.isPlatformShipping()) {
            // Expert doesn't get shipping revenue for platform shipping
        } else {
            // Expert keeps shipping revenue for self shipping
            expertEarnings += shippingCost;
        }

        // Create order
        String orderId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.now();

        // Build order data
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("order_id", orderId);
        orderData.put("user_id", userId);
        orderData.put("expert_id", expertId);
        orderData.put("type", ORDER_TYPE);
        orderData.put("created_at", now);
        orderData.put("status", ProductOrderDetails.STATUS_INITIATED);

        // Product details (snapshot)
        orderData.put("productId", productId);
        orderData.put("sku", product.getSku());
        orderData.put("productName", product.getName());
        orderData.put("productImageUrl", product.getThumbnailUrl());
        orderData.put("quantity", quantity);

        // Pricing
        orderData.put("unitPrice", unitPrice);
        orderData.put("shippingCost", shippingCost);
        orderData.put("amount", totalAmount);
        orderData.put("currency", "INR");

        // Configuration snapshot
        orderData.put("isWhiteLabel", config.isWhiteLabel());
        orderData.put("shippingMode", config.getShippingMode());

        // Commission snapshot
        orderData.put("platformFeePercent", commissionPercent);
        orderData.put("platformFeeAmount", platformFeeAmount);
        orderData.put("expertEarnings", expertEarnings);

        // Shipping address
        if (address != null) {
            orderData.put("address", address);
        }

        // Get user and expert names
        FirebaseUser user = UserService.getUserDetails(db, userId);
        FirebaseUser expert = UserService.getUserDetails(db, expertId);
        if (user != null) {
            orderData.put("user_name", user.getName());
            orderData.put("user_phone_number", user.getPhoneNumber());
        }
        if (expert != null) {
            orderData.put("expert_name", expert.getName());
        }

        // Save order
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION).document(orderId);
        orderRef.set(orderData).get();

        LoggingService.info("product_order_created", Map.of(
            "orderId", orderId,
            "userId", userId,
            "expertId", expertId,
            "productId", productId,
            "amount", totalAmount,
            "isWhiteLabel", config.isWhiteLabel(),
            "shippingMode", config.getShippingMode()
        ));

        // Return order details for payment
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", orderId);
        result.put("amount", totalAmount);
        result.put("currency", "INR");
        result.put("productName", product.getName());
        result.put("quantity", quantity);
        result.put("unitPrice", unitPrice);
        result.put("shippingCost", shippingCost);
        return result;
    }

    /**
     * Create a multi-item product order (before payment).
     * Returns order ID for Razorpay payment flow.
     *
     * @param userId    The user placing the order
     * @param expertId  The expert/seller for all items
     * @param items     List of items, each with productId and quantity
     * @param address   Shipping address
     * @param couponCode Optional coupon code
     * @return Order details including orderId, amount for payment
     */
    public Map<String, Object> createMultiItemOrder(
            String userId,
            String expertId,
            List<Map<String, Object>> items,
            Map<String, Object> address,
            String couponCode
    ) throws ExecutionException, InterruptedException {
        // Validate inputs
        if (userId == null || expertId == null) {
            throw new IllegalArgumentException("User ID and Expert ID are required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }

        // Get expert fee config once
        PlatformFeeConfig feeConfig = getExpertFeeConfig(expertId);

        // Process all items
        List<OrderLineItem> lineItems = new ArrayList<>();
        double totalSubtotal = 0.0;
        double totalShipping = 0.0;
        double totalPlatformFee = 0.0;
        double totalExpertEarnings = 0.0;
        int totalQuantity = 0;

        // Track first item for backward-compat fields
        String firstProductId = null;
        String firstProductName = null;
        String firstProductImageUrl = null;
        String firstSku = null;
        boolean hasWhiteLabel = false;
        boolean hasPlatformShipping = false;

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> itemInput = items.get(i);
            String productId = (String) itemInput.get("productId");
            int quantity = itemInput.get("quantity") != null ? ((Number) itemInput.get("quantity")).intValue() : 1;

            if (productId == null) {
                throw new IllegalArgumentException("Product ID is required for item " + (i + 1));
            }

            // Get expert's product configuration
            ExpertProductConfig config = expertProductService.getExpertProductConfigWithDetails(expertId, productId);
            if (config == null || !config.isEnabled()) {
                throw new IllegalArgumentException("Product not available from this expert: " + productId);
            }

            PlatformProduct product = config.getProduct();
            if (product == null || !product.isActive()) {
                throw new IllegalArgumentException("Product not found or inactive: " + productId);
            }

            // Check stock
            if (!expertProductService.checkExpertStock(expertId, productId, quantity)) {
                throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
            }

            // Calculate pricing for this item
            double unitPrice = config.getSellerPriceInr() != null ? config.getSellerPriceInr() : product.getSuggestedPriceInr();
            double itemShipping = config.getEffectiveShippingCost(product);
            double itemSubtotal = unitPrice * quantity;

            // Calculate commission for this item
            double commissionPercent = getProductCommissionPercent(feeConfig, config.isWhiteLabel());
            double itemPlatformFee = itemSubtotal * (commissionPercent / 100.0);
            double itemExpertEarnings = itemSubtotal - itemPlatformFee;

            // Expert gets shipping revenue only for self-shipping
            if (config.isSelfShipping()) {
                itemExpertEarnings += itemShipping;
            }

            // Create line item
            OrderLineItem lineItem = new OrderLineItem();
            lineItem.setProductId(productId);
            lineItem.setSku(product.getSku());
            lineItem.setProductName(product.getName());
            lineItem.setProductImageUrl(product.getThumbnailUrl());
            lineItem.setQuantity(quantity);
            lineItem.setUnitPrice(unitPrice);
            lineItem.setShippingCost(itemShipping);
            lineItem.setLineTotal(itemSubtotal + itemShipping);
            lineItem.setWhiteLabel(config.isWhiteLabel());
            lineItem.setShippingMode(config.getShippingMode());
            lineItem.setPlatformFeePercent(commissionPercent);
            lineItem.setPlatformFeeAmount(itemPlatformFee);
            lineItem.setExpertEarnings(itemExpertEarnings);
            lineItems.add(lineItem);

            // Accumulate totals
            totalSubtotal += itemSubtotal;
            totalShipping += itemShipping;
            totalPlatformFee += itemPlatformFee;
            totalExpertEarnings += itemExpertEarnings;
            totalQuantity += quantity;

            // Track flags
            if (config.isWhiteLabel()) hasWhiteLabel = true;
            if (config.isPlatformShipping()) hasPlatformShipping = true;

            // Keep first item info for backward compatibility
            if (i == 0) {
                firstProductId = productId;
                firstProductName = product.getName();
                firstProductImageUrl = product.getThumbnailUrl();
                firstSku = product.getSku();
            }
        }

        double totalAmount = totalSubtotal + totalShipping;

        // Create order
        String orderId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.now();

        // Build order data
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("order_id", orderId);
        orderData.put("user_id", userId);
        orderData.put("expert_id", expertId);
        orderData.put("type", ORDER_TYPE);
        orderData.put("created_at", now);
        orderData.put("status", ProductOrderDetails.STATUS_INITIATED);

        // Multi-item: store items array
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (OrderLineItem lineItem : lineItems) {
            itemMaps.add(lineItem.toMap());
        }
        orderData.put("items", itemMaps);

        // Backward compatibility fields (first/primary item info for display)
        orderData.put("productId", firstProductId);
        orderData.put("sku", firstSku);
        orderData.put("productName", lineItems.size() > 1
            ? firstProductName + " + " + (lineItems.size() - 1) + " more"
            : firstProductName);
        orderData.put("productImageUrl", firstProductImageUrl);
        orderData.put("quantity", totalQuantity);

        // Pricing totals
        orderData.put("subtotal", totalSubtotal);
        orderData.put("shippingCost", totalShipping);
        orderData.put("amount", totalAmount);
        orderData.put("currency", "INR");

        // Configuration snapshot (aggregate)
        orderData.put("isWhiteLabel", hasWhiteLabel);
        orderData.put("shippingMode", hasPlatformShipping ? ExpertProductConfig.SHIPPING_PLATFORM : ExpertProductConfig.SHIPPING_SELF);

        // Commission snapshot (aggregated)
        double avgCommissionPercent = totalSubtotal > 0 ? (totalPlatformFee / totalSubtotal) * 100.0 : DEFAULT_COMMISSION_PERCENT;
        orderData.put("platformFeePercent", avgCommissionPercent);
        orderData.put("platformFeeAmount", totalPlatformFee);
        orderData.put("expertEarnings", totalExpertEarnings);

        // Shipping address
        if (address != null) {
            orderData.put("address", address);
        }

        // Get user and expert names
        FirebaseUser user = UserService.getUserDetails(db, userId);
        FirebaseUser expert = UserService.getUserDetails(db, expertId);
        if (user != null) {
            orderData.put("user_name", user.getName());
            orderData.put("user_phone_number", user.getPhoneNumber());
        }
        if (expert != null) {
            orderData.put("expert_name", expert.getName());
        }

        // Save order
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION).document(orderId);
        orderRef.set(orderData).get();

        LoggingService.info("multi_item_product_order_created", Map.of(
            "orderId", orderId,
            "userId", userId,
            "expertId", expertId,
            "itemCount", lineItems.size(),
            "totalQuantity", totalQuantity,
            "amount", totalAmount
        ));

        // Return order details for payment
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", orderId);
        result.put("amount", totalAmount);
        result.put("currency", "INR");
        result.put("productName", (String) orderData.get("productName"));
        result.put("itemCount", lineItems.size());
        result.put("totalQuantity", totalQuantity);
        result.put("subtotal", totalSubtotal);
        result.put("shippingCost", totalShipping);
        return result;
    }

    /**
     * Verify order payment (called after Razorpay payment success).
     * Decrements stock and credits expert earnings.
     * Supports both single-item and multi-item orders.
     */
    @SuppressWarnings("unchecked")
    public void verifyOrderPayment(String userId, String orderId) throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION).document(orderId);

        // Track items that need self-shipping stock decrement (done outside transaction)
        List<Map<String, Object>> selfShippingItems = new ArrayList<>();

        db.runTransaction(transaction -> {
            DocumentSnapshot orderDoc = transaction.get(orderRef).get();
            if (!orderDoc.exists()) {
                throw new IllegalArgumentException("Order not found");
            }

            String status = orderDoc.getString("status");
            if (!ProductOrderDetails.STATUS_INITIATED.equals(status) &&
                !ProductOrderDetails.STATUS_PAYMENT_PENDING.equals(status)) {
                // Already processed
                return null;
            }

            // Check if this is a multi-item order
            Object itemsObj = orderDoc.get("items");
            boolean isMultiItem = itemsObj instanceof List && !((List<?>) itemsObj).isEmpty();

            if (isMultiItem) {
                // Multi-item order: decrement stock for each item
                List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
                for (Map<String, Object> item : items) {
                    String itemProductId = (String) item.get("productId");
                    String itemShippingMode = (String) item.get("shippingMode");
                    int itemQuantity = item.get("quantity") != null ? ((Number) item.get("quantity")).intValue() : 1;

                    if (ExpertProductConfig.SHIPPING_PLATFORM.equals(itemShippingMode)) {
                        catalogService.decrementPlatformStock(transaction, itemProductId, itemQuantity);
                    } else {
                        // Track for self-shipping stock decrement after transaction
                        selfShippingItems.add(Map.of(
                            "productId", itemProductId,
                            "quantity", itemQuantity
                        ));
                    }
                }
            } else {
                // Single-item order (backward compatibility)
                String productId = orderDoc.getString("productId");
                String shippingMode = orderDoc.getString("shippingMode");
                Integer quantity = orderDoc.getLong("quantity") != null ? orderDoc.getLong("quantity").intValue() : 1;

                if (ExpertProductConfig.SHIPPING_PLATFORM.equals(shippingMode)) {
                    catalogService.decrementPlatformStock(transaction, productId, quantity);
                } else {
                    selfShippingItems.add(Map.of(
                        "productId", productId,
                        "quantity", quantity
                    ));
                }
            }

            // Update order status
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", ProductOrderDetails.STATUS_PAID);
            updates.put("verified_at", Timestamp.now());
            transaction.update(orderRef, updates);

            return null;
        }).get();

        // Handle expert stock decrement outside transaction for self-shipping items
        DocumentSnapshot orderDoc = orderRef.get().get();
        String expertId = orderDoc.getString("expert_id");

        for (Map<String, Object> item : selfShippingItems) {
            String productId = (String) item.get("productId");
            int quantity = (Integer) item.get("quantity");
            expertProductService.decrementExpertStock(expertId, productId, quantity);
        }

        // Credit expert earnings (aggregated amount minus platform fee)
        Double expertEarnings = orderDoc.getDouble("expertEarnings");
        if (expertId != null && expertEarnings != null && expertEarnings > 0) {
            earningsService.creditExpertEarnings(
                expertId,
                "INR",
                expertEarnings + (orderDoc.getDouble("platformFeeAmount") != null ? orderDoc.getDouble("platformFeeAmount") : 0.0), // gross
                orderDoc.getDouble("platformFeeAmount") != null ? orderDoc.getDouble("platformFeeAmount") : 0.0, // platform fee
                orderId,
                "Product sale: " + orderDoc.getString("productName")
            );
        }

        LoggingService.info("product_order_verified", Map.of(
            "orderId", orderId,
            "userId", userId,
            "itemCount", selfShippingItems.size() > 0 ? selfShippingItems.size() : 1
        ));
    }

    /**
     * Get product order by ID.
     */
    public ProductOrderDetails getOrder(String userId, String orderId) throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION).document(orderId);
        DocumentSnapshot doc = orderRef.get().get();

        if (!doc.exists()) {
            return null;
        }

        Map<String, Object> data = doc.getData();
        if (data == null || !ORDER_TYPE.equals(data.get("type"))) {
            return null;
        }

        return ProductOrderDetails.fromMap(data);
    }

    /**
     * Get all product orders needing platform shipping (for admin dashboard).
     * Returns orders with shippingMode=PLATFORM and status in [PAID, PROCESSING].
     */
    public List<Map<String, Object>> getPlatformShippingOrders() throws ExecutionException, InterruptedException {
        // Collection group query for all orders
        Query query = db.collectionGroup(ORDERS_COLLECTION)
                .whereEqualTo("type", ORDER_TYPE)
                .whereEqualTo("shippingMode", ExpertProductConfig.SHIPPING_PLATFORM)
                .whereIn("status", Arrays.asList(
                    ProductOrderDetails.STATUS_PAID,
                    ProductOrderDetails.STATUS_PROCESSING
                ))
                .orderBy("created_at", Query.Direction.DESCENDING);

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<Map<String, Object>> orders = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> orderData = doc.getData();
            if (orderData != null) {
                // Extract userId from document path
                String userId = doc.getReference().getParent().getParent().getId();
                orderData.put("userId", userId);
                orders.add(orderData);
            }
        }

        return orders;
    }

    /**
     * Get all product orders for an expert (for expert dashboard).
     */
    public List<Map<String, Object>> getExpertProductOrders(String expertId, String statusFilter) throws ExecutionException, InterruptedException {
        Query query = db.collectionGroup(ORDERS_COLLECTION)
                .whereEqualTo("type", ORDER_TYPE)
                .whereEqualTo("expert_id", expertId)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(100);

        if (statusFilter != null && !statusFilter.isEmpty()) {
            query = query.whereEqualTo("status", statusFilter);
        }

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<Map<String, Object>> orders = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> orderData = doc.getData();
            if (orderData != null) {
                String userId = doc.getReference().getParent().getParent().getId();
                orderData.put("userId", userId);
                orders.add(orderData);
            }
        }

        return orders;
    }

    /**
     * Get product orders for a user.
     */
    public List<Map<String, Object>> getUserProductOrders(String userId) throws ExecutionException, InterruptedException {
        Query query = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION)
                .whereEqualTo("type", ORDER_TYPE)
                .orderBy("created_at", Query.Direction.DESCENDING);

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<Map<String, Object>> orders = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> orderData = doc.getData();
            if (orderData != null) {
                orders.add(orderData);
            }
        }

        return orders;
    }

    /**
     * Update order status (admin/expert function).
     */
    public void updateOrderStatus(
            String userId,
            String orderId,
            String newStatus,
            String trackingNumber,
            String updatedBy
    ) throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION).document(orderId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("updated_at", Timestamp.now());
        updates.put("updated_by", updatedBy);

        if (trackingNumber != null && !trackingNumber.isEmpty()) {
            updates.put("trackingNumber", trackingNumber);
        }

        if (ProductOrderDetails.STATUS_SHIPPED.equals(newStatus)) {
            updates.put("shippedAt", Timestamp.now());
        } else if (ProductOrderDetails.STATUS_DELIVERED.equals(newStatus)) {
            updates.put("deliveredAt", Timestamp.now());
        }

        orderRef.update(updates).get();

        LoggingService.info("product_order_status_updated", Map.of(
            "orderId", orderId,
            "userId", userId,
            "newStatus", newStatus,
            "updatedBy", updatedBy
        ));
    }

    /**
     * Mark order as shipped with tracking number (admin for platform shipping, expert for self shipping).
     */
    public void markOrderShipped(
            String userId,
            String orderId,
            String trackingNumber,
            String shippedBy
    ) throws ExecutionException, InterruptedException {
        updateOrderStatus(userId, orderId, ProductOrderDetails.STATUS_SHIPPED, trackingNumber, shippedBy);
    }

    /**
     * Mark order as delivered.
     */
    public void markOrderDelivered(String userId, String orderId, String markedBy) throws ExecutionException, InterruptedException {
        updateOrderStatus(userId, orderId, ProductOrderDetails.STATUS_DELIVERED, null, markedBy);
    }

    /**
     * Cancel an order (before shipping).
     * Restores stock and reverses earnings.
     */
    public void cancelOrder(String userId, String orderId, String cancelledBy, String reason) throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection(ORDERS_COLLECTION).document(orderId);

        DocumentSnapshot doc = orderRef.get().get();
        if (!doc.exists()) {
            throw new IllegalArgumentException("Order not found");
        }

        String currentStatus = doc.getString("status");
        // Can only cancel if not yet shipped
        if (ProductOrderDetails.STATUS_SHIPPED.equals(currentStatus) ||
            ProductOrderDetails.STATUS_DELIVERED.equals(currentStatus)) {
            throw new IllegalArgumentException("Cannot cancel shipped or delivered orders");
        }

        String productId = doc.getString("productId");
        String expertId = doc.getString("expert_id");
        String shippingMode = doc.getString("shippingMode");
        Integer quantity = doc.getLong("quantity") != null ? doc.getLong("quantity").intValue() : 1;

        // Restore stock if order was verified/paid
        if (ProductOrderDetails.STATUS_PAID.equals(currentStatus) ||
            ProductOrderDetails.STATUS_PROCESSING.equals(currentStatus)) {
            if (ExpertProductConfig.SHIPPING_PLATFORM.equals(shippingMode)) {
                catalogService.incrementPlatformStock(productId, quantity);
            }
            // For self-shipping, expert manages their own stock
        }

        // Update order status
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", ProductOrderDetails.STATUS_CANCELLED);
        updates.put("cancelled_at", Timestamp.now());
        updates.put("cancelled_by", cancelledBy);
        updates.put("cancellation_reason", reason);
        orderRef.update(updates).get();

        // TODO: Handle refund through Razorpay if needed
        // TODO: Reverse expert earnings if already credited

        LoggingService.info("product_order_cancelled", Map.of(
            "orderId", orderId,
            "userId", userId,
            "cancelledBy", cancelledBy,
            "reason", reason != null ? reason : ""
        ));
    }

    /**
     * Get expert's fee config for product commission rates.
     */
    private PlatformFeeConfig getExpertFeeConfig(String expertId) throws ExecutionException, InterruptedException {
        // Try to get from users/{expertId}/private/platform_fee_config
        DocumentReference configRef = db.collection("users").document(expertId)
                .collection("private").document("platform_fee_config");
        DocumentSnapshot doc = configRef.get().get();

        if (doc.exists()) {
            Map<String, Object> data = doc.getData();
            if (data != null) {
                PlatformFeeConfig config = new PlatformFeeConfig();
                if (data.get("defaultFeePercent") != null) {
                    config.setDefaultFeePercent(((Number) data.get("defaultFeePercent")).doubleValue());
                }
                @SuppressWarnings("unchecked")
                Map<String, Double> feeByType = (Map<String, Double>) data.get("feeByType");
                config.setFeeByType(feeByType);
                return config;
            }
        }

        // Return default config
        return new PlatformFeeConfig();
    }

    /**
     * Calculate product commission percent based on config and white-label status.
     */
    private double getProductCommissionPercent(PlatformFeeConfig config, boolean isWhiteLabel) {
        // Check for specific PRODUCT type fee
        if (config.getFeeByType() != null) {
            String key = isWhiteLabel ? "PRODUCT_WHITE_LABEL" : "PRODUCT";
            if (config.getFeeByType().containsKey(key)) {
                return config.getFeeByType().get(key);
            }
        }

        // Use default rates based on white-label status
        if (isWhiteLabel) {
            return WHITE_LABEL_COMMISSION_PERCENT;
        }

        // Use config default or system default
        return config.getDefaultFeePercent() != null ? config.getDefaultFeePercent() : DEFAULT_COMMISSION_PERCENT;
    }

    /**
     * Get order statistics for admin dashboard.
     */
    public Map<String, Object> getOrderStatistics() throws ExecutionException, InterruptedException {
        Map<String, Object> stats = new HashMap<>();

        // Count platform shipping orders by status
        Query pendingQuery = db.collectionGroup(ORDERS_COLLECTION)
                .whereEqualTo("type", ORDER_TYPE)
                .whereEqualTo("shippingMode", ExpertProductConfig.SHIPPING_PLATFORM)
                .whereIn("status", Arrays.asList(
                    ProductOrderDetails.STATUS_PAID,
                    ProductOrderDetails.STATUS_PROCESSING
                ));

        int pendingCount = pendingQuery.get().get().size();
        stats.put("pendingPlatformShipments", pendingCount);

        Query shippedQuery = db.collectionGroup(ORDERS_COLLECTION)
                .whereEqualTo("type", ORDER_TYPE)
                .whereEqualTo("shippingMode", ExpertProductConfig.SHIPPING_PLATFORM)
                .whereEqualTo("status", ProductOrderDetails.STATUS_SHIPPED);

        int shippedCount = shippedQuery.get().get().size();
        stats.put("inTransitPlatformShipments", shippedCount);

        return stats;
    }
}
