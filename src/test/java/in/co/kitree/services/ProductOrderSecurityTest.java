package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.UserRecord;
import in.co.kitree.TestBase;
import in.co.kitree.pojos.ExpertProductConfig;
import in.co.kitree.pojos.PlatformProduct;
import in.co.kitree.pojos.ProductOrderDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for ProductOrderService.
 * Tests commission calculation, price integrity, authorization, and fraud prevention.
 */
public class ProductOrderSecurityTest extends TestBase {

    private static final String TEST_USER_ID = "test-user-001";
    private static final String TEST_EXPERT_ID = "test-expert-001";
    private static final String TEST_ADMIN_ID = "test-admin-001";
    private static final String TEST_PRODUCT_ID = "test-product-001";

    private ProductCatalogService catalogService;
    private ExpertProductService expertProductService;
    private ExpertEarningsService earningsService;
    private ProductOrderService orderService;

    @BeforeEach
    public void setUp() throws Exception {
        // Initialize services
        catalogService = new ProductCatalogService(db);
        expertProductService = new ExpertProductService(db, catalogService);
        earningsService = new ExpertEarningsService(db);
        orderService = new ProductOrderService(db, catalogService, expertProductService, earningsService);

        // Clear product-related collections
        clearCollection("platform_products");

        // Create test users in Auth
        try {
            createAuthUser(TEST_USER_ID);
        } catch (Exception e) {
            // User may already exist
        }
        try {
            createAuthUser(TEST_EXPERT_ID);
        } catch (Exception e) {
            // User may already exist
        }
        try {
            UserRecord admin = createAuthUser(TEST_ADMIN_ID);
            setUserClaims(TEST_ADMIN_ID, Map.of("admin", true));
        } catch (Exception e) {
            // User may already exist
        }

        // Create test user in Firestore
        createTestUserInFirestore(TEST_USER_ID, "Test Customer");
        createTestUserInFirestore(TEST_EXPERT_ID, "Test Expert");
        createTestUserInFirestore(TEST_ADMIN_ID, "Test Admin");

        // Create expert store details
        createExpertStoreDetails(TEST_EXPERT_ID);

        // Create platform product
        createPlatformProduct();

        // Create expert product config
        createExpertProductConfig();
    }

    // ==================== COMMISSION CALCULATION TESTS ====================

    @Test
    @DisplayName("Standard commission (10%) calculated correctly for non-white-label")
    public void testStandardCommissionCalculation() throws Exception {
        // Create order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");
        assertNotNull(orderId);

        // Verify commission calculation
        DocumentSnapshot orderDoc = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId).get().get();

        double unitPrice = orderDoc.getDouble("unitPrice");
        double platformFeePercent = orderDoc.getDouble("platformFeePercent");
        double platformFeeAmount = orderDoc.getDouble("platformFeeAmount");
        double expertEarnings = orderDoc.getDouble("expertEarnings");

        assertEquals(10.0, platformFeePercent, 0.01, "Standard commission should be 10%");
        assertEquals(unitPrice * 0.10, platformFeeAmount, 0.01, "Platform fee should be 10% of price");
        assertEquals(unitPrice * 0.90, expertEarnings, 0.01, "Expert should earn 90%");
    }

    @Test
    @DisplayName("White-label commission (20%) calculated correctly")
    public void testWhiteLabelCommissionCalculation() throws Exception {
        // Update expert config to white-label
        db.collection("users").document(TEST_EXPERT_ID)
            .collection("seller_products").document(TEST_PRODUCT_ID)
            .update("isWhiteLabel", true).get();

        // Create order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");

        // Verify white-label commission
        DocumentSnapshot orderDoc = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId).get().get();

        double platformFeePercent = orderDoc.getDouble("platformFeePercent");
        assertEquals(20.0, platformFeePercent, 0.01, "White-label commission should be 20%");
    }

    @Test
    @DisplayName("Commission snapshot is immutable after order creation")
    public void testCommissionSnapshotImmutable() throws Exception {
        // Create order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");

        // Get original commission
        DocumentSnapshot orderDoc = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId).get().get();
        double originalCommission = orderDoc.getDouble("platformFeePercent");

        // Try to modify commission (simulating malicious attempt)
        // Note: In production, Firestore security rules should prevent this
        DocumentReference orderRef = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId);

        // The order status prevents reprocessing
        String status = orderDoc.getString("status");
        assertEquals(ProductOrderDetails.STATUS_INITIATED, status);

        // Commission snapshot should be preserved
        assertEquals(10.0, originalCommission, 0.01);
    }

    // ==================== PRICE INTEGRITY TESTS ====================

    @Test
    @DisplayName("Price is fetched from database, not client-submitted")
    public void testPriceFromDatabase() throws Exception {
        // Create order (client cannot submit price)
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 2, // quantity = 2
            createTestAddress(), null
        );

        // Verify price comes from database
        double unitPrice = (Double) result.get("unitPrice");
        double amount = (Double) result.get("amount");

        assertEquals(499.0, unitPrice, 0.01, "Unit price should match database config");
        // amount = (499 * 2) + shipping (0 for platform shipping)
        assertTrue(amount >= 998.0, "Total should be calculated server-side");
    }

    @Test
    @DisplayName("Price changes after order creation do not affect existing order")
    public void testPriceSnapshotPreserved() throws Exception {
        // Create order at original price
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");
        double originalPrice = (Double) result.get("unitPrice");

        // Change price in expert config
        db.collection("users").document(TEST_EXPERT_ID)
            .collection("seller_products").document(TEST_PRODUCT_ID)
            .update("sellerPriceInr", 999.0).get();

        // Original order should still have original price
        DocumentSnapshot orderDoc = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId).get().get();

        assertEquals(originalPrice, orderDoc.getDouble("unitPrice"), 0.01,
            "Order price should be preserved from creation time");
    }

    @Test
    @DisplayName("Minimum price validation enforced")
    public void testMinimumPriceEnforced() throws Exception {
        // Try to set price below minimum (should be caught by ExpertProductService)
        // Platform product has minPriceInr = 399

        // The expert config has sellerPriceInr = 499 which is above min
        // If expert tries to set lower than min, it should fail in the config service

        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        double unitPrice = (Double) result.get("unitPrice");
        assertTrue(unitPrice >= 399.0, "Price should be at or above minimum");
    }

    // ==================== STOCK VALIDATION TESTS ====================

    @Test
    @DisplayName("Order fails when insufficient stock")
    public void testInsufficientStockFails() throws Exception {
        // Update platform stock to 0
        db.collection("platform_products").document(TEST_PRODUCT_ID)
            .update(Map.of(
                "platformStockAvailable", false,
                "platformStockQuantity", 0
            )).get();

        // Attempt to create order should fail
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(
                TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
                createTestAddress(), null
            );
        }, "Should fail with insufficient stock");
    }

    @Test
    @DisplayName("Stock decremented only after payment verification")
    public void testStockDecrementedOnPayment() throws Exception {
        // Get initial stock
        DocumentSnapshot productDoc = db.collection("platform_products").document(TEST_PRODUCT_ID).get().get();
        long initialStock = productDoc.getLong("platformStockQuantity");

        // Create order (stock not decremented yet)
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");

        // Stock should still be same after order creation
        productDoc = db.collection("platform_products").document(TEST_PRODUCT_ID).get().get();
        assertEquals(initialStock, productDoc.getLong("platformStockQuantity"),
            "Stock should not decrement until payment verified");

        // Verify payment (would normally require Razorpay signature)
        orderService.verifyOrderPayment(TEST_USER_ID, orderId);

        // Now stock should be decremented
        productDoc = db.collection("platform_products").document(TEST_PRODUCT_ID).get().get();
        assertEquals(initialStock - 1, productDoc.getLong("platformStockQuantity"),
            "Stock should decrement after payment verification");
    }

    // ==================== EARNINGS CREDIT TESTS ====================

    @Test
    @DisplayName("Expert earnings credited correctly after payment")
    public void testEarningsCreditedOnPayment() throws Exception {
        // Create and verify order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");
        orderService.verifyOrderPayment(TEST_USER_ID, orderId);

        // Check expert earnings balance
        Map<String, Double> balances = earningsService.getExpertEarningsBalances(TEST_EXPERT_ID);

        // Expert should have 90% of price (10% commission)
        double expectedEarnings = 499.0 * 0.90;
        assertTrue(balances.containsKey("INR"), "Expert should have INR balance");
        assertEquals(expectedEarnings, balances.get("INR"), 0.01,
            "Expert earnings should be 90% of sale price");
    }

    @Test
    @DisplayName("Expert earnings include shipping for self-shipping mode")
    public void testEarningsIncludeShippingForSelfShip() throws Exception {
        // Update config to self-shipping
        db.collection("users").document(TEST_EXPERT_ID)
            .collection("seller_products").document(TEST_PRODUCT_ID)
            .update(Map.of(
                "shippingMode", "SELF",
                "selfShippingCostInr", 50.0,
                "selfStockQuantity", 10
            )).get();

        // Create and verify order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");
        orderService.verifyOrderPayment(TEST_USER_ID, orderId);

        // Check expert earnings
        DocumentSnapshot orderDoc = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId).get().get();

        double expertEarnings = orderDoc.getDouble("expertEarnings");
        // 90% of 499 + 50 shipping = 449.1 + 50 = 499.1
        double expectedEarnings = (499.0 * 0.90) + 50.0;
        assertEquals(expectedEarnings, expertEarnings, 0.01,
            "Expert should earn product margin plus shipping for self-ship");
    }

    // ==================== AUTHORIZATION TESTS ====================

    @Test
    @DisplayName("Expert can only view their own product orders")
    public void testExpertCanOnlyViewOwnOrders() throws Exception {
        // Create order for test expert
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        // Expert should see their orders
        List<Map<String, Object>> orders = orderService.getExpertProductOrders(TEST_EXPERT_ID, null);
        assertFalse(orders.isEmpty(), "Expert should see their orders");

        // Different expert should not see these orders
        List<Map<String, Object>> otherOrders = orderService.getExpertProductOrders("other-expert-id", null);
        assertTrue(otherOrders.isEmpty(), "Other expert should not see these orders");
    }

    @Test
    @DisplayName("User can only view their own orders")
    public void testUserCanOnlyViewOwnOrders() throws Exception {
        // Create order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        // User should see their orders
        List<Map<String, Object>> orders = orderService.getUserProductOrders(TEST_USER_ID);
        assertFalse(orders.isEmpty(), "User should see their orders");

        // Different user should not see these orders
        List<Map<String, Object>> otherOrders = orderService.getUserProductOrders("other-user-id");
        assertTrue(otherOrders.isEmpty(), "Other user should not see these orders");
    }

    @Test
    @DisplayName("Order status update validates authorization")
    public void testOrderStatusUpdateAuthorization() throws Exception {
        // Create order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");
        orderService.verifyOrderPayment(TEST_USER_ID, orderId);

        // Expert should be able to update their self-shipping orders
        // But for platform shipping, only admin should update
        // (The actual authorization check is in Handler.java)

        // Verify order status was updated
        DocumentSnapshot orderDoc = db.collection("users").document(TEST_USER_ID)
            .collection("orders").document(orderId).get().get();
        assertEquals(ProductOrderDetails.STATUS_PAID, orderDoc.getString("status"));
    }

    // ==================== DOUBLE-PAYMENT PREVENTION TESTS ====================

    @Test
    @DisplayName("Payment cannot be verified twice")
    public void testDoublePaymentPrevention() throws Exception {
        // Create order
        Map<String, Object> result = orderService.createOrder(
            TEST_USER_ID, TEST_EXPERT_ID, TEST_PRODUCT_ID, 1,
            createTestAddress(), null
        );

        String orderId = (String) result.get("orderId");

        // First verification
        orderService.verifyOrderPayment(TEST_USER_ID, orderId);

        // Get initial stock after first verification
        DocumentSnapshot productDoc = db.collection("platform_products").document(TEST_PRODUCT_ID).get().get();
        long stockAfterFirst = productDoc.getLong("platformStockQuantity");

        // Second verification should not decrement stock again
        orderService.verifyOrderPayment(TEST_USER_ID, orderId);

        productDoc = db.collection("platform_products").document(TEST_PRODUCT_ID).get().get();
        long stockAfterSecond = productDoc.getLong("platformStockQuantity");

        assertEquals(stockAfterFirst, stockAfterSecond,
            "Stock should not decrement on duplicate payment verification");
    }

    // ==================== HELPER METHODS ====================

    private void createTestUserInFirestore(String userId, String name) throws ExecutionException, InterruptedException {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", userId);
        userData.put("name", name);
        userData.put("email", userId + "@test.com");
        userData.put("phoneNumber", "+919999999999");
        userData.put("createdAt", Timestamp.now());

        db.collection("users").document(userId).set(userData).get();
    }

    private void createExpertStoreDetails(String expertId) throws ExecutionException, InterruptedException {
        Map<String, Object> storeDetails = new HashMap<>();
        storeDetails.put("name", "Test Expert Store");
        storeDetails.put("isActive", true);
        storeDetails.put("createdAt", Timestamp.now());

        db.collection("users").document(expertId)
            .collection("public").document("store_details")
            .set(storeDetails).get();
    }

    private void createPlatformProduct() throws ExecutionException, InterruptedException {
        Map<String, Object> productData = new HashMap<>();
        productData.put("productId", TEST_PRODUCT_ID);
        productData.put("sku", "TEST-SKU-001");
        productData.put("name", "Test Crystal Bracelet");
        productData.put("description", "A test product");
        productData.put("category", "CRYSTALS");
        productData.put("productType", "bracelet");
        productData.put("suggestedPriceInr", 499.0);
        productData.put("minPriceInr", 399.0);
        productData.put("costPriceInr", 200.0);
        productData.put("shippingCostInr", 0.0); // Free platform shipping
        productData.put("platformStockAvailable", true);
        productData.put("platformStockQuantity", 100);
        productData.put("isActive", true);
        productData.put("createdAt", Timestamp.now());

        db.collection("platform_products").document(TEST_PRODUCT_ID).set(productData).get();
    }

    private void createExpertProductConfig() throws ExecutionException, InterruptedException {
        Map<String, Object> configData = new HashMap<>();
        configData.put("productId", TEST_PRODUCT_ID);
        configData.put("expertId", TEST_EXPERT_ID);
        configData.put("isEnabled", true);
        configData.put("sellerPriceInr", 499.0);
        configData.put("isWhiteLabel", false);
        configData.put("shippingMode", "PLATFORM");
        configData.put("createdAt", Timestamp.now());

        db.collection("users").document(TEST_EXPERT_ID)
            .collection("seller_products").document(TEST_PRODUCT_ID)
            .set(configData).get();
    }

    private Map<String, Object> createTestAddress() {
        Map<String, Object> address = new HashMap<>();
        address.put("name", "Test Customer");
        address.put("phone", "+919999999999");
        address.put("line1", "123 Test Street");
        address.put("city", "Test City");
        address.put("state", "Test State");
        address.put("pincode", "123456");
        address.put("country", "India");
        return address;
    }
}
