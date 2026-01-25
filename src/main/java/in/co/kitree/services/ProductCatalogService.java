package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import in.co.kitree.pojos.PlatformProduct;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing the platform product catalog.
 * Products are controlled by Kitree, not individual experts.
 *
 * Collection: platform_products/{productId}
 */
public class ProductCatalogService {
    private static final String COLLECTION_NAME = "platform_products";

    private final Firestore db;

    public ProductCatalogService(Firestore db) {
        this.db = db;
    }

    /**
     * Get all active platform products.
     */
    public List<PlatformProduct> getActiveProducts() throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("isActive", true)
                .orderBy("name");

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<PlatformProduct> products = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            PlatformProduct product = documentToProduct(doc);
            if (product != null) {
                products.add(product);
            }
        }

        LoggingService.info("get_active_products", Map.of("count", products.size()));
        return products;
    }

    /**
     * Get a single product by ID.
     */
    public PlatformProduct getProduct(String productId) throws ExecutionException, InterruptedException {
        if (productId == null || productId.isEmpty()) {
            return null;
        }

        DocumentReference docRef = db.collection(COLLECTION_NAME).document(productId);
        DocumentSnapshot doc = docRef.get().get();

        if (!doc.exists()) {
            return null;
        }

        return documentToProduct(doc);
    }

    /**
     * Get products by category.
     */
    public List<PlatformProduct> getProductsByCategory(String category) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("isActive", true)
                .whereEqualTo("category", category)
                .orderBy("name");

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<PlatformProduct> products = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            PlatformProduct product = documentToProduct(doc);
            if (product != null) {
                products.add(product);
            }
        }

        return products;
    }

    /**
     * Get products by type (bracelet, pendant, etc.).
     */
    public List<PlatformProduct> getProductsByType(String productType) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("isActive", true)
                .whereEqualTo("productType", productType)
                .orderBy("name");

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<PlatformProduct> products = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            PlatformProduct product = documentToProduct(doc);
            if (product != null) {
                products.add(product);
            }
        }

        return products;
    }

    /**
     * Search products by tags.
     */
    public List<PlatformProduct> searchProductsByTag(String tag) throws ExecutionException, InterruptedException {
        Query query = db.collection(COLLECTION_NAME)
                .whereEqualTo("isActive", true)
                .whereArrayContains("tags", tag.toLowerCase());

        ApiFuture<QuerySnapshot> future = query.get();
        QuerySnapshot snapshot = future.get();

        List<PlatformProduct> products = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            PlatformProduct product = documentToProduct(doc);
            if (product != null) {
                products.add(product);
            }
        }

        return products;
    }

    /**
     * Check if platform has stock for a product.
     */
    public boolean checkPlatformStock(String productId, int quantity) throws ExecutionException, InterruptedException {
        PlatformProduct product = getProduct(productId);
        if (product == null) {
            return false;
        }
        return product.hasStock(quantity);
    }

    /**
     * Decrement platform stock (within a transaction).
     */
    public void decrementPlatformStock(Transaction tx, String productId, int quantity) {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(productId);
        tx.update(docRef, "platformStockQuantity", FieldValue.increment(-quantity));
    }

    /**
     * Increment platform stock (for cancellations/returns).
     */
    public void incrementPlatformStock(String productId, int quantity) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(productId);
        docRef.update("platformStockQuantity", FieldValue.increment(quantity)).get();
    }

    /**
     * Create or update a platform product (admin only).
     */
    public void upsertProduct(PlatformProduct product) throws ExecutionException, InterruptedException {
        if (product.getProductId() == null || product.getProductId().isEmpty()) {
            // Generate new ID
            DocumentReference newDocRef = db.collection(COLLECTION_NAME).document();
            product.setProductId(newDocRef.getId());
            product.setCreatedAt(Timestamp.now());
        }
        product.setUpdatedAt(Timestamp.now());

        DocumentReference docRef = db.collection(COLLECTION_NAME).document(product.getProductId());
        docRef.set(product.toMap()).get();

        LoggingService.info("product_upserted", Map.of(
            "productId", product.getProductId(),
            "name", product.getName()
        ));
    }

    /**
     * Deactivate a product (soft delete).
     */
    public void deactivateProduct(String productId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(productId);
        docRef.update(
            "isActive", false,
            "updatedAt", Timestamp.now()
        ).get();

        LoggingService.info("product_deactivated", Map.of("productId", productId));
    }

    /**
     * Seed products from a list (for initial data population).
     */
    public int seedProducts(List<PlatformProduct> products) throws ExecutionException, InterruptedException {
        WriteBatch batch = db.batch();
        int count = 0;

        for (PlatformProduct product : products) {
            if (product.getProductId() == null || product.getProductId().isEmpty()) {
                product.setProductId(db.collection(COLLECTION_NAME).document().getId());
            }
            product.setCreatedAt(Timestamp.now());
            product.setUpdatedAt(Timestamp.now());
            product.setActive(true);

            DocumentReference docRef = db.collection(COLLECTION_NAME).document(product.getProductId());
            batch.set(docRef, product.toMap());
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

        LoggingService.info("products_seeded", Map.of("count", count));
        return count;
    }

    /**
     * Convert Firestore document to PlatformProduct.
     */
    @SuppressWarnings("unchecked")
    private PlatformProduct documentToProduct(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Map<String, Object> data = doc.getData();
        if (data == null) {
            return null;
        }

        PlatformProduct product = new PlatformProduct();
        product.setProductId(doc.getId());
        product.setSku((String) data.get("sku"));
        product.setName((String) data.get("name"));
        product.setDescription((String) data.get("description"));
        product.setShortDescription((String) data.get("shortDescription"));
        product.setImages((List<String>) data.get("images"));
        product.setThumbnailUrl((String) data.get("thumbnailUrl"));
        product.setProductType((String) data.get("productType"));
        product.setCategory((String) data.get("category"));
        product.setTags((List<String>) data.get("tags"));

        if (data.get("suggestedPriceInr") != null) {
            product.setSuggestedPriceInr(((Number) data.get("suggestedPriceInr")).doubleValue());
        }
        if (data.get("minPriceInr") != null) {
            product.setMinPriceInr(((Number) data.get("minPriceInr")).doubleValue());
        }
        if (data.get("costPriceInr") != null) {
            product.setCostPriceInr(((Number) data.get("costPriceInr")).doubleValue());
        }
        if (data.get("weightGrams") != null) {
            product.setWeightGrams(((Number) data.get("weightGrams")).intValue());
        }
        product.setDimensionsCm((Map<String, Integer>) data.get("dimensionsCm"));
        if (data.get("shippingCostInr") != null) {
            product.setShippingCostInr(((Number) data.get("shippingCostInr")).doubleValue());
        }

        product.setPlatformStockAvailable(Boolean.TRUE.equals(data.get("platformStockAvailable")));
        if (data.get("platformStockQuantity") != null) {
            product.setPlatformStockQuantity(((Number) data.get("platformStockQuantity")).intValue());
        }

        product.setActive(Boolean.TRUE.equals(data.get("isActive")));
        product.setCreatedAt((Timestamp) data.get("createdAt"));
        product.setUpdatedAt((Timestamp) data.get("updatedAt"));

        // Parse matching attributes
        Map<String, Object> matchingAttrsMap = (Map<String, Object>) data.get("matchingAttributes");
        if (matchingAttrsMap != null) {
            PlatformProduct.ProductMatchingAttributes attrs = new PlatformProduct.ProductMatchingAttributes();
            attrs.setMaterial((List<String>) matchingAttrsMap.get("material"));
            attrs.setPlanets((List<String>) matchingAttrsMap.get("planets"));
            attrs.setPurpose((List<String>) matchingAttrsMap.get("purpose"));
            attrs.setChakras((List<String>) matchingAttrsMap.get("chakras"));
            attrs.setColors((List<String>) matchingAttrsMap.get("colors"));
            product.setMatchingAttributes(attrs);
        }

        return product;
    }
}
