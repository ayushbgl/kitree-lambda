package in.co.kitree.services;

import in.co.kitree.pojos.MatchedProduct;
import in.co.kitree.pojos.RemedyAttributes;

import java.util.List;

/**
 * Interface for matching products to remedy recommendations.
 *
 * Current implementation: MockProductMatchingService (hardcoded products)
 * Future implementation: VectorProductMatchingService (semantic search via Pinecone/Weaviate)
 */
public interface ProductMatchingService {

    /**
     * Find products matching the given remedy attributes.
     *
     * @param attributes Extracted attributes from expert's recommendation
     * @return List of matched products, sorted by relevance (max 4-5 items)
     */
    List<MatchedProduct> findMatchingProducts(RemedyAttributes attributes);

    /**
     * Find products by a simple keyword/tag.
     * Convenience method for simple lookups.
     *
     * @param tag Material, planet, or purpose tag
     * @return List of matched products
     */
    List<MatchedProduct> findByTag(String tag);
}
