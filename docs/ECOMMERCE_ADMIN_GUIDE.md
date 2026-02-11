# E-Commerce Admin Guide

## Overview

The Kitree e-commerce system uses a **Managed Inventory Model** where:
- **Platform (Admin)** controls the master product catalog
- **Experts (Sellers)** "subscribe" to products and configure their storefronts
- **Users (Customers)** purchase from expert storefronts

---

## Adding Products to Platform Catalog

Products are stored in Firestore at: `platform_products/{productId}`

### Method 1: Firebase Console (Manual)

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Navigate to **Firestore Database**
3. Select the `platform_products` collection (create if doesn't exist)
4. Click **Add document**
5. Use auto-generated ID or specify custom productId
6. Add the following fields:

```javascript
{
  // Required fields
  "name": "Rose Quartz Bracelet",           // Product name
  "isActive": true,                          // Must be true to appear in catalog

  // Identification
  "sku": "RQ-BRACELET-001",                 // Unique SKU (optional but recommended)

  // Categorization
  "category": "CRYSTALS",                    // CRYSTALS | GEMSTONES | TRADITIONAL | MEDITATION | HOME_DECOR
  "productType": "bracelet",                 // bracelet | pendant | ring | mala | bowl | pyramid | lamp | decor | book
  "tags": ["rose-quartz", "love", "healing"], // Search tags (lowercase)

  // Description
  "description": "Beautiful rose quartz bracelet for love and healing...",
  "shortDescription": "Rose quartz healing bracelet",

  // Media
  "thumbnailUrl": "https://storage.googleapis.com/.../thumbnail.jpg",
  "images": [
    "https://storage.googleapis.com/.../image1.jpg",
    "https://storage.googleapis.com/.../image2.jpg"
  ],

  // Pricing (in INR)
  "suggestedPriceInr": 999,                 // Suggested retail price
  "minPriceInr": 799,                       // Minimum allowed seller price
  "costPriceInr": 400,                      // Platform's cost (internal use)

  // Shipping (for platform-fulfilled orders)
  "shippingCostInr": 50,                    // Shipping cost charged to customer
  "weightGrams": 50,                        // Product weight
  "dimensionsCm": { "l": 10, "w": 5, "h": 2 }, // Dimensions for shipping

  // Stock (for platform-fulfilled orders)
  "platformStockAvailable": true,           // Is platform shipping available?
  "platformStockQuantity": 100,             // Available stock quantity

  // Matching attributes (for AI recommendations)
  "matchingAttributes": {
    "material": ["rose_quartz"],
    "planets": ["venus"],
    "purpose": ["love", "healing", "relationships"],
    "chakras": ["heart"],
    "colors": ["pink"]
  }
}
```

### Method 2: Seed Script (Bulk Import)

Create a JSON file with products and use the `seedProducts` API:

```java
// ProductCatalogService.seedProducts() can be called via a Lambda function
// or directly through a test/seed script

List<PlatformProduct> products = Arrays.asList(
    createProduct("Rose Quartz Bracelet", "CRYSTALS", "bracelet", 999, 799),
    createProduct("Citrine Pendant", "CRYSTALS", "pendant", 1499, 1199),
    // ... more products
);

catalogService.seedProducts(products);
```

### Method 3: Admin API (If Implemented)

Call the `upsert_platform_product` endpoint (requires admin authentication):

```bash
curl -X POST https://api.kitree.in/upsert_platform_product \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Rose Quartz Bracelet",
    "sku": "RQ-BRACELET-001",
    "category": "CRYSTALS",
    "productType": "bracelet",
    "suggestedPriceInr": 999,
    "minPriceInr": 799,
    "isActive": true,
    ...
  }'
```

---

## Product Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Product display name |
| `isActive` | boolean | Yes | Must be `true` to appear in catalog |
| `sku` | string | No | Unique product identifier |
| `category` | string | No | Product category for filtering |
| `productType` | string | No | Product type (bracelet, pendant, etc.) |
| `suggestedPriceInr` | number | Yes | Suggested retail price in INR |
| `minPriceInr` | number | No | Minimum price experts can set |
| `shippingCostInr` | number | No | Platform shipping cost |
| `platformStockAvailable` | boolean | No | Enable platform fulfillment |
| `platformStockQuantity` | number | No | Available stock for platform shipping |
| `thumbnailUrl` | string | No | Product thumbnail image URL |
| `images` | array | No | Additional product image URLs |
| `tags` | array | No | Search tags (lowercase) |
| `matchingAttributes` | map | No | AI matching attributes |

---

## Managing Products

### Deactivate a Product
Set `isActive: false` to hide from catalog (soft delete):
```javascript
// In Firestore
db.collection('platform_products').doc(productId).update({
  isActive: false,
  updatedAt: Timestamp.now()
});
```

### Update Stock
```javascript
// Increment stock
db.collection('platform_products').doc(productId).update({
  platformStockQuantity: FieldValue.increment(50)
});

// Set exact stock
db.collection('platform_products').doc(productId).update({
  platformStockQuantity: 100
});
```

---

## Commission Structure

Platform commissions are configured per fulfillment type:

| Fulfillment Type | Default Commission |
|-----------------|-------------------|
| Platform Ships (Generic) | 10% |
| Platform Ships (White-Label) | 20% |
| Seller Ships (Generic) | 10% |
| Seller Ships (White-Label) | 20% |

Custom rates can be set per expert in:
`users/{expertId}/private/platform_fee_config`

```javascript
{
  "defaultFeePercent": 10,
  "feeByType": {
    "PRODUCT": 10,
    "PRODUCT_WHITE_LABEL": 20
  }
}
```

---

## Order Fulfillment (Platform Shipping)

For orders with `shippingMode: "PLATFORM"`:

1. View pending orders in Admin Dashboard or query:
   ```javascript
   db.collectionGroup('orders')
     .where('type', '==', 'PRODUCT')
     .where('shippingMode', '==', 'PLATFORM')
     .where('status', 'in', ['PAID', 'PROCESSING'])
   ```

2. Update status to `SHIPPED` with tracking:
   ```javascript
   db.collection('users').doc(userId)
     .collection('orders').doc(orderId)
     .update({
       status: 'SHIPPED',
       trackingNumber: 'TRACK123456',
       shippedAt: Timestamp.now()
     });
   ```

3. Mark as `DELIVERED` when complete.

---

## Firestore Indexes Required

Create these composite indexes in Firebase Console:

```
Collection: platform_products
Fields: isActive (Asc), name (Asc)

Collection: platform_products
Fields: isActive (Asc), category (Asc), name (Asc)

Collection Group: orders
Fields: type (Asc), shippingMode (Asc), status (Asc), created_at (Desc)

Collection Group: orders
Fields: type (Asc), expert_id (Asc), created_at (Desc)
```
