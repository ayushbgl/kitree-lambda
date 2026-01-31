package in.co.kitree.handlers;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.razorpay.RazorpayException;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for all product ecommerce operations.
 * Extracted from Handler.java as part of refactoring.
 */
public class ProductOrderHandler {

    private final Firestore db;
    private final Razorpay razorpay;
    private final Gson gson;

    public ProductOrderHandler(Firestore db, Razorpay razorpay) {
        this.db = db;
        this.razorpay = razorpay;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Routes product-related function calls to the appropriate handler method.
     *
     * @param functionName The function name from the request
     * @param userId The authenticated user ID
     * @param requestBody The request body
     * @return The response string, or null if the function is not handled by this handler
     */
    public String handleRequest(String functionName, String userId, RequestBody requestBody) {
        try {
            switch (functionName) {
                case "get_platform_products":
                    return handleGetPlatformProducts(requestBody);
                case "get_expert_products":
                    return handleGetExpertProducts(userId, requestBody);
                case "update_expert_product":
                    return handleUpdateExpertProduct(userId, requestBody);
                case "get_expert_storefront":
                    return handleGetExpertStorefront(requestBody);
                case "buy_product":
                    return handleBuyProduct(userId, requestBody);
                case "buy_products":
                    return handleBuyProducts(userId, requestBody);
                case "verify_product_payment":
                    return handleVerifyProductPayment(userId, requestBody);
                case "get_user_product_orders":
                    return handleGetUserProductOrders(userId);
                case "get_expert_product_orders":
                    return handleGetExpertProductOrders(userId, requestBody);
                case "get_platform_shipping_orders":
                    return handleGetPlatformShippingOrders(userId);
                case "update_product_order_status":
                    return handleUpdateProductOrderStatus(userId, requestBody);
                case "cancel_product_order":
                    return handleCancelProductOrder(userId, requestBody);
                case "seed_platform_products":
                    return handleSeedPlatformProducts(userId, requestBody);
                case "admin_upsert_product":
                    return handleAdminUpsertProduct(userId, requestBody);
                default:
                    return null; // Not handled by this handler
            }
        } catch (Exception e) {
            LoggingService.error("product_order_handler_exception", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Check if a function name is handled by this handler.
     */
    public static boolean handles(String functionName) {
        return functionName != null && (
            functionName.equals("get_platform_products") ||
            functionName.equals("get_expert_products") ||
            functionName.equals("update_expert_product") ||
            functionName.equals("get_expert_storefront") ||
            functionName.equals("buy_product") ||
            functionName.equals("buy_products") ||
            functionName.equals("verify_product_payment") ||
            functionName.equals("get_user_product_orders") ||
            functionName.equals("get_expert_product_orders") ||
            functionName.equals("get_platform_shipping_orders") ||
            functionName.equals("update_product_order_status") ||
            functionName.equals("cancel_product_order") ||
            functionName.equals("seed_platform_products") ||
            functionName.equals("admin_upsert_product")
        );
    }

    // ============= Helper Methods =============

    private boolean isAdmin(String userId) throws FirebaseAuthException {
        return Boolean.TRUE.equals(FirebaseAuth.getInstance().getUser(userId).getCustomClaims().get("admin"));
    }

    private ProductOrderService createOrderService() {
        ProductCatalogService catalogService = new ProductCatalogService(db);
        ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
        ExpertEarningsService earningsService = new ExpertEarningsService(db);
        return new ProductOrderService(db, catalogService, expertProductService, earningsService);
    }

    // ============= Product Ecommerce Handler Methods =============

    /**
     * Get all active platform products.
     */
    private String handleGetPlatformProducts(RequestBody requestBody) {
        try {
            ProductCatalogService catalogService = new ProductCatalogService(db);
            List<PlatformProduct> products;

            String category = requestBody.getCategory();
            String productType = requestBody.getType();

            if (category != null && !category.isEmpty()) {
                products = catalogService.getProductsByCategory(category);
            } else if (productType != null && !productType.isEmpty()) {
                products = catalogService.getProductsByType(productType);
            } else {
                products = catalogService.getActiveProducts();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("products", products);
            return gson.toJson(response);
        } catch (Exception e) {
            LoggingService.error("get_platform_products_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Get expert's product configurations.
     */
    private String handleGetExpertProducts(String userId, RequestBody requestBody) {
        try {
            String expertId = requestBody.getExpertId() != null ? requestBody.getExpertId() : userId;

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);

            List<ExpertProductConfig> configs = expertProductService.getExpertProductsWithDetails(expertId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("products", configs);
            return gson.toJson(response);
        } catch (Exception e) {
            LoggingService.error("get_expert_products_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Update expert's product configuration.
     */
    private String handleUpdateExpertProduct(String userId, RequestBody requestBody) {
        try {
            if (requestBody.getProductId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Product ID is required"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);

            ExpertProductConfig config = new ExpertProductConfig();
            config.setProductId(requestBody.getProductId());
            config.setExpertId(userId);

            if (requestBody.getIsEnabled() != null) {
                config.setEnabled(requestBody.getIsEnabled());
            }
            if (requestBody.getSellerPriceInr() != null) {
                config.setSellerPriceInr(requestBody.getSellerPriceInr());
            }
            if (requestBody.getIsWhiteLabel() != null) {
                config.setWhiteLabel(requestBody.getIsWhiteLabel());
            }
            if (requestBody.getShippingMode() != null) {
                config.setShippingMode(requestBody.getShippingMode());
            }
            if (requestBody.getSelfShippingCostInr() != null) {
                config.setSelfShippingCostInr(requestBody.getSelfShippingCostInr());
            }
            if (requestBody.getSelfStockQuantity() != null) {
                config.setSelfStockQuantity(requestBody.getSelfStockQuantity());
            }
            if (requestBody.getCustomDescription() != null) {
                config.setCustomDescription(requestBody.getCustomDescription());
            }

            expertProductService.updateProductConfig(userId, config);

            return gson.toJson(Map.of("success", true));
        } catch (Exception e) {
            LoggingService.error("update_expert_product_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Get expert's enabled products for storefront (public).
     */
    private String handleGetExpertStorefront(RequestBody requestBody) {
        try {
            if (requestBody.getExpertId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);

            List<ExpertProductConfig> enabledProducts = expertProductService.getExpertEnabledProducts(requestBody.getExpertId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("products", enabledProducts);
            return gson.toJson(response);
        } catch (Exception e) {
            LoggingService.error("get_expert_storefront_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Create product order (buy product).
     */
    private String handleBuyProduct(String userId, RequestBody requestBody) {
        try {
            if (requestBody.getExpertId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }
            if (requestBody.getProductId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Product ID is required"));
            }

            int quantity = requestBody.getQuantity() != null ? requestBody.getQuantity() : 1;
            if (quantity < 1) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Quantity must be at least 1"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            Map<String, Object> orderResult = orderService.createOrder(
                userId,
                requestBody.getExpertId(),
                requestBody.getProductId(),
                quantity,
                requestBody.getAddress(),
                requestBody.getCouponCode()
            );

            // Create Razorpay order for payment
            Double amount = (Double) orderResult.get("amount");
            String orderId = (String) orderResult.get("orderId");

            if (amount != null && amount > 0) {
                try {
                    // Encrypt userId for Razorpay notes
                    String encryptedCustomerId = CustomerCipher.encryptCaesarCipher(userId);
                    String razorpayOrderId = razorpay.createOrder(amount, encryptedCustomerId);
                    orderResult.put("razorpayOrderId", razorpayOrderId);
                } catch (RazorpayException e) {
                    LoggingService.error("razorpay_order_creation_failed", e);
                    return gson.toJson(Map.of("success", false, "errorMessage", "Failed to create payment order"));
                }
            }

            return gson.toJson(orderResult);
        } catch (Exception e) {
            LoggingService.error("buy_product_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Create multi-item product order (buy products).
     * Items should be passed as a list with productId and quantity for each.
     */
    @SuppressWarnings("unchecked")
    private String handleBuyProducts(String userId, RequestBody requestBody) {
        try {
            if (requestBody.getExpertId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
            }

            // Get items from request - expects a list of {productId, quantity}
            List<Map<String, Object>> items = requestBody.getItems();
            if (items == null || items.isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "At least one item is required"));
            }

            // Validate items
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                if (item.get("productId") == null) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Product ID is required for item " + (i + 1)));
                }
                Object qtyObj = item.get("quantity");
                int qty = qtyObj != null ? ((Number) qtyObj).intValue() : 1;
                if (qty < 1) {
                    return gson.toJson(Map.of("success", false, "errorMessage", "Quantity must be at least 1 for item " + (i + 1)));
                }
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            Map<String, Object> orderResult = orderService.createMultiItemOrder(
                userId,
                requestBody.getExpertId(),
                items,
                requestBody.getAddress(),
                requestBody.getCouponCode()
            );

            // Create Razorpay order for payment
            Double amount = (Double) orderResult.get("amount");
            String orderId = (String) orderResult.get("orderId");

            if (amount != null && amount > 0) {
                try {
                    // Encrypt userId for Razorpay notes
                    String encryptedCustomerId = CustomerCipher.encryptCaesarCipher(userId);
                    String razorpayOrderId = razorpay.createOrder(amount, encryptedCustomerId);
                    orderResult.put("razorpayOrderId", razorpayOrderId);
                } catch (RazorpayException e) {
                    LoggingService.error("razorpay_order_creation_failed", e);
                    return gson.toJson(Map.of("success", false, "errorMessage", "Failed to create payment order"));
                }
            }

            return gson.toJson(orderResult);
        } catch (Exception e) {
            LoggingService.error("buy_products_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Verify product order payment.
     * SECURITY: Payment signature verification is MANDATORY - never skip this check.
     */
    private String handleVerifyProductPayment(String userId, RequestBody requestBody) {
        try {
            if (requestBody.getOrderId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
            }

            // SECURITY: All payment gateway fields are REQUIRED - never process without verification
            if (requestBody.getGatewayOrderId() == null ||
                requestBody.getGatewayPaymentId() == null ||
                requestBody.getGatewaySignature() == null) {
                LoggingService.warn("verify_product_payment_missing_gateway_fields", Map.of(
                    "userId", userId,
                    "orderId", requestBody.getOrderId()
                ));
                return gson.toJson(Map.of("success", false, "errorMessage", "Payment gateway details are required for verification"));
            }

            // Verify Razorpay payment signature - MANDATORY check
            boolean isValid = razorpay.verifyPayment(
                requestBody.getGatewayOrderId(),
                requestBody.getGatewayPaymentId(),
                requestBody.getGatewaySignature()
            );
            if (!isValid) {
                LoggingService.warn("verify_product_payment_invalid_signature", Map.of(
                    "userId", userId,
                    "orderId", requestBody.getOrderId(),
                    "gatewayOrderId", requestBody.getGatewayOrderId()
                ));
                return gson.toJson(Map.of("success", false, "errorMessage", "Payment verification failed - invalid signature"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            orderService.verifyOrderPayment(userId, requestBody.getOrderId());

            return gson.toJson(Map.of("success", true, "orderId", requestBody.getOrderId()));
        } catch (Exception e) {
            LoggingService.error("verify_product_payment_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Get user's product orders.
     */
    private String handleGetUserProductOrders(String userId) {
        try {
            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            List<Map<String, Object>> orders = orderService.getUserProductOrders(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);
            return gson.toJson(response);
        } catch (Exception e) {
            LoggingService.error("get_user_product_orders_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Get expert's product orders.
     * SECURITY: User can only access their own orders (as expert).
     * The userId from the authenticated token is used as expertId.
     */
    private String handleGetExpertProductOrders(String userId, RequestBody requestBody) {
        try {
            // SECURITY: Verify the user is actually an expert by checking if they have store_details
            // This prevents non-experts from querying orders with an arbitrary expert_id
            DocumentReference storeRef = db.collection("users").document(userId).collection("public").document("store_details");
            DocumentSnapshot storeDoc = storeRef.get().get();

            if (!storeDoc.exists()) {
                LoggingService.warn("get_expert_product_orders_not_expert", Map.of("userId", userId));
                return gson.toJson(Map.of("success", false, "errorMessage", "User is not registered as an expert"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            // Use authenticated userId as expertId - user can only see their own orders
            List<Map<String, Object>> orders = orderService.getExpertProductOrders(userId, requestBody.getStatusFilter());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);
            return gson.toJson(response);
        } catch (Exception e) {
            LoggingService.error("get_expert_product_orders_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Get platform shipping orders (admin).
     * SECURITY: Admin-only endpoint for viewing all platform shipping orders.
     */
    private String handleGetPlatformShippingOrders(String userId) {
        try {
            // SECURITY: Verify admin access - CRITICAL security check
            if (!isAdmin(userId)) {
                LoggingService.warn("get_platform_shipping_orders_unauthorized", Map.of("userId", userId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: Admin access required"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            List<Map<String, Object>> orders = orderService.getPlatformShippingOrders();
            Map<String, Object> stats = orderService.getOrderStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);
            response.put("statistics", stats);
            return gson.toJson(response);
        } catch (Exception e) {
            LoggingService.error("get_platform_shipping_orders_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Update product order status.
     * SECURITY: Only admin can update platform shipping orders.
     * Expert can only update their own self-shipping orders.
     */
    private String handleUpdateProductOrderStatus(String userId, RequestBody requestBody) {
        try {
            if (requestBody.getOrderId() == null || requestBody.getUserId() == null || requestBody.getNewStatus() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Order ID, User ID, and new status are required"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            // SECURITY: Verify authorization based on shipping mode
            ProductOrderDetails order = orderService.getOrder(requestBody.getUserId(), requestBody.getOrderId());
            if (order == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Order not found"));
            }

            boolean isAdminUser = isAdmin(userId);
            boolean isOrderExpert = userId.equals(order.getExpertId());
            boolean isSelfShipping = "SELF".equals(order.getShippingMode());

            // Admin can update any order
            // Expert can only update their own self-shipping orders
            if (!isAdminUser && !(isOrderExpert && isSelfShipping)) {
                LoggingService.warn("update_product_order_status_unauthorized", Map.of(
                    "userId", userId,
                    "orderId", requestBody.getOrderId(),
                    "expertId", order.getExpertId(),
                    "shippingMode", order.getShippingMode()
                ));
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: Cannot update this order"));
            }

            orderService.updateOrderStatus(
                requestBody.getUserId(),
                requestBody.getOrderId(),
                requestBody.getNewStatus(),
                requestBody.getTrackingNumber(),
                userId
            );

            return gson.toJson(Map.of("success", true));
        } catch (Exception e) {
            LoggingService.error("update_product_order_status_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Cancel product order.
     */
    private String handleCancelProductOrder(String userId, RequestBody requestBody) {
        try {
            if (requestBody.getOrderId() == null) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Order ID is required"));
            }

            String targetUserId = requestBody.getUserId() != null ? requestBody.getUserId() : userId;

            ProductCatalogService catalogService = new ProductCatalogService(db);
            ExpertProductService expertProductService = new ExpertProductService(db, catalogService);
            ExpertEarningsService earningsService = new ExpertEarningsService(db);
            ProductOrderService orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

            orderService.cancelOrder(
                targetUserId,
                requestBody.getOrderId(),
                userId,
                requestBody.getNotes()
            );

            return gson.toJson(Map.of("success", true));
        } catch (Exception e) {
            LoggingService.error("cancel_product_order_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Admin: Seed platform products.
     * SECURITY: Admin-only endpoint for bulk product creation.
     */
    private String handleSeedPlatformProducts(String userId, RequestBody requestBody) {
        try {
            // SECURITY: Verify admin access - CRITICAL security check
            if (!isAdmin(userId)) {
                LoggingService.warn("seed_platform_products_unauthorized", Map.of("userId", userId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: Admin access required"));
            }

            if (requestBody.getProductsToSeed() == null || requestBody.getProductsToSeed().isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Products to seed are required"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            int count = catalogService.seedProducts(requestBody.getProductsToSeed());

            return gson.toJson(Map.of("success", true, "count", count));
        } catch (Exception e) {
            LoggingService.error("seed_platform_products_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Admin: Upsert platform product.
     * SECURITY: Admin-only endpoint for product management.
     */
    private String handleAdminUpsertProduct(String userId, RequestBody requestBody) {
        try {
            // SECURITY: Verify admin access - CRITICAL security check
            if (!isAdmin(userId)) {
                LoggingService.warn("admin_upsert_product_unauthorized", Map.of("userId", userId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: Admin access required"));
            }

            if (requestBody.getProductsToSeed() == null || requestBody.getProductsToSeed().isEmpty()) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Product data is required"));
            }

            ProductCatalogService catalogService = new ProductCatalogService(db);
            PlatformProduct product = requestBody.getProductsToSeed().get(0);
            catalogService.upsertProduct(product);

            return gson.toJson(Map.of("success", true, "productId", product.getProductId()));
        } catch (Exception e) {
            LoggingService.error("admin_upsert_product_error", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }
}
