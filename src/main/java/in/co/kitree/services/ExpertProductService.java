package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import in.co.kitree.pojos.ExpertProductConfig;
import in.co.kitree.pojos.PlatformProduct;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing expert product configurations.
 * Experts can enable/disable products, set pricing, and configure shipping options.
 *
 * Collection: users/{expertId}/seller_products/{productId}
 */
public class ExpertProductService {
    private static final String COLLECTION_NAME = "seller_products";

    private final Firestore db;
    private final ProductCatalogService catalogService;

    public ExpertProductService(Firestore db, ProductCatalogService catalogService) {
        this.db = db;
        this.catalogService = catalogService;
    }

    /**
     * Get all product configurations for an expert.
     */
    public List<ExpertProductConfig> getExpertProducts(String expertId) throws ExecutionException, InterruptedException {
        if (expertId == null || expertId.isEmpty()) {
            return Collections.emptyList();
        }

        CollectionReference colRef = db.collection("users").document(expertId).collection(COLLECTION_NAME);
        ApiFuture<QuerySnapshot> future = colRef.get();
        QuerySnapshot snapshot = future.get();

        List<ExpertProductConfig> configs = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            ExpertProductConfig config = documentToConfig(doc);
            if (config != null) {
                configs.add(config);
            }
        }

        return configs;
    }

    /**
     * Get expert's product configurations with joined product data.
     * Used for displaying product list with full details.
     */
    public List<ExpertProductConfig> getExpertProductsWithDetails(String expertId) throws ExecutionException, InterruptedException {
        List<ExpertProductConfig> configs = getExpertProducts(expertId);

        for (ExpertProductConfig config : configs) {
            PlatformProduct product = catalogService.getProduct(config.getProductId());
            config.setProduct(product);
        }

        return configs;
    }

    /**
     * Get a specific product configuration for an expert.
     */
    public ExpertProductConfig getExpertProductConfig(String expertId, String productId) throws ExecutionException, InterruptedException {
        if (expertId == null || productId == null) {
            return null;
        }

        DocumentReference docRef = db.collection("users").document(expertId)
                .collection(COLLECTION_NAME).document(productId);
        DocumentSnapshot doc = docRef.get().get();

        if (!doc.exists()) {
            return null;
        }

        return documentToConfig(doc);
    }

    /**
     * Get expert's product config with joined product data.
     */
    public ExpertProductConfig getExpertProductConfigWithDetails(String expertId, String productId) throws ExecutionException, InterruptedException {
        ExpertProductConfig config = getExpertProductConfig(expertId, productId);
        if (config != null) {
            PlatformProduct product = catalogService.getProduct(productId);
            config.setProduct(product);
        }
        return config;
    }

    /**
     * Get all enabled products for an expert (for storefront display).
     */
    public List<ExpertProductConfig> getExpertEnabledProducts(String expertId) throws ExecutionException, InterruptedException {
        if (expertId == null || expertId.isEmpty()) {
            return Collections.emptyList();
        }

        Query query = db.collection("users").document(expertId)
                .collection(COLLECTION_NAME)
                .whereEqualTo("isEnabled", true);

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<ExpertProductConfig> configs = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            ExpertProductConfig config = documentToConfig(doc);
            if (config != null) {
                // Join with platform product data
                PlatformProduct product = catalogService.getProduct(config.getProductId());
                if (product != null && product.isActive()) {
                    config.setProduct(product);
                    configs.add(config);
                }
            }
        }

        return configs;
    }

    /**
     * Update or create an expert's product configuration.
     */
    public void updateProductConfig(String expertId, ExpertProductConfig config) throws ExecutionException, InterruptedException {
        if (expertId == null || config == null || config.getProductId() == null) {
            throw new IllegalArgumentException("Expert ID and product configuration with productId are required");
        }

        // Validate product exists
        PlatformProduct product = catalogService.getProduct(config.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + config.getProductId());
        }

        // Validate seller price is at or above minimum
        if (config.getSellerPriceInr() != null && product.getMinPriceInr() != null) {
            if (config.getSellerPriceInr() < product.getMinPriceInr()) {
                throw new IllegalArgumentException(
                    "Seller price (" + config.getSellerPriceInr() + ") cannot be below minimum price (" + product.getMinPriceInr() + ")"
                );
            }
        }

        // Set defaults
        config.setExpertId(expertId);
        if (config.getShippingMode() == null) {
            config.setShippingMode(ExpertProductConfig.SHIPPING_PLATFORM);
        }

        // Check if this is new or update
        ExpertProductConfig existing = getExpertProductConfig(expertId, config.getProductId());
        if (existing == null) {
            config.setCreatedAt(Timestamp.now());
        } else {
            config.setCreatedAt(existing.getCreatedAt());
        }
        config.setUpdatedAt(Timestamp.now());

        // Save to Firestore
        DocumentReference docRef = db.collection("users").document(expertId)
                .collection(COLLECTION_NAME).document(config.getProductId());
        docRef.set(config.toMap()).get();

        LoggingService.info("expert_product_config_updated", Map.of(
            "expertId", expertId,
            "productId", config.getProductId(),
            "isEnabled", config.isEnabled(),
            "sellerPrice", config.getSellerPriceInr() != null ? config.getSellerPriceInr() : "null",
            "isWhiteLabel", config.isWhiteLabel(),
            "shippingMode", config.getShippingMode()
        ));
    }

    /**
     * Enable a product for an expert with default settings.
     */
    public void enableProduct(String expertId, String productId) throws ExecutionException, InterruptedException {
        PlatformProduct product = catalogService.getProduct(productId);
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        ExpertProductConfig config = getExpertProductConfig(expertId, productId);
        if (config == null) {
            config = new ExpertProductConfig();
            config.setProductId(productId);
            config.setSellerPriceInr(product.getSuggestedPriceInr());
            config.setShippingMode(ExpertProductConfig.SHIPPING_PLATFORM);
            config.setWhiteLabel(false);
        }
        config.setEnabled(true);

        updateProductConfig(expertId, config);
    }

    /**
     * Disable a product for an expert.
     */
    public void disableProduct(String expertId, String productId) throws ExecutionException, InterruptedException {
        ExpertProductConfig config = getExpertProductConfig(expertId, productId);
        if (config != null) {
            config.setEnabled(false);
            updateProductConfig(expertId, config);
        }
    }

    /**
     * Validate seller price against product minimum.
     */
    public boolean validateSellerPrice(String productId, Double sellerPrice) throws ExecutionException, InterruptedException {
        if (sellerPrice == null || sellerPrice <= 0) {
            return false;
        }

        PlatformProduct product = catalogService.getProduct(productId);
        if (product == null) {
            return false;
        }

        if (product.getMinPriceInr() == null) {
            return true; // No minimum constraint
        }

        return sellerPrice >= product.getMinPriceInr();
    }

    /**
     * Check if expert has stock available for a product.
     */
    public boolean checkExpertStock(String expertId, String productId, int quantity) throws ExecutionException, InterruptedException {
        ExpertProductConfig config = getExpertProductConfig(expertId, productId);
        if (config == null || !config.isEnabled()) {
            return false;
        }

        if (config.isPlatformShipping()) {
            // Check platform stock
            return catalogService.checkPlatformStock(productId, quantity);
        } else {
            // Check expert's own stock
            return config.hasStock(quantity);
        }
    }

    /**
     * Decrement expert's stock (for self-shipping).
     */
    public void decrementExpertStock(String expertId, String productId, int quantity) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection("users").document(expertId)
                .collection(COLLECTION_NAME).document(productId);
        docRef.update("selfStockQuantity", FieldValue.increment(-quantity)).get();
    }

    /**
     * Get all experts selling a specific product (for admin analytics).
     */
    public List<ExpertProductConfig> getExpertsBySelling(String productId) throws ExecutionException, InterruptedException {
        // This requires a collection group query
        Query query = db.collectionGroup(COLLECTION_NAME)
                .whereEqualTo("productId", productId)
                .whereEqualTo("isEnabled", true);

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<ExpertProductConfig> configs = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            ExpertProductConfig config = documentToConfig(doc);
            if (config != null) {
                configs.add(config);
            }
        }

        return configs;
    }

    /**
     * Bulk enable multiple products for an expert.
     */
    public int bulkEnableProducts(String expertId, List<String> productIds) throws ExecutionException, InterruptedException {
        WriteBatch batch = db.batch();
        int count = 0;

        for (String productId : productIds) {
            PlatformProduct product = catalogService.getProduct(productId);
            if (product == null || !product.isActive()) {
                continue;
            }

            ExpertProductConfig config = getExpertProductConfig(expertId, productId);
            if (config == null) {
                config = new ExpertProductConfig();
                config.setProductId(productId);
                config.setExpertId(expertId);
                config.setSellerPriceInr(product.getSuggestedPriceInr());
                config.setShippingMode(ExpertProductConfig.SHIPPING_PLATFORM);
                config.setWhiteLabel(false);
                config.setCreatedAt(Timestamp.now());
            }
            config.setEnabled(true);
            config.setUpdatedAt(Timestamp.now());

            DocumentReference docRef = db.collection("users").document(expertId)
                    .collection(COLLECTION_NAME).document(productId);
            batch.set(docRef, config.toMap());
            count++;

            // Firestore batches are limited to 500 operations
            if (count % 400 == 0) {
                batch.commit().get();
                batch = db.batch();
            }
        }

        if (count % 400 != 0) {
            batch.commit().get();
        }

        LoggingService.info("bulk_enable_products", Map.of(
            "expertId", expertId,
            "count", count
        ));

        return count;
    }

    /**
     * Convert Firestore document to ExpertProductConfig.
     */
    private ExpertProductConfig documentToConfig(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Map<String, Object> data = doc.getData();
        if (data == null) {
            return null;
        }

        // Set productId from document ID if not in data
        if (!data.containsKey("productId")) {
            data.put("productId", doc.getId());
        }

        return ExpertProductConfig.fromMap(data);
    }
}
