package in.co.kitree.services;

import in.co.kitree.pojos.MatchedProduct;
import in.co.kitree.pojos.RemedyAttributes;

import java.util.*;

/**
 * Mock implementation of ProductMatchingService.
 * Returns hardcoded products based on simple tag matching.
 *
 * This will be replaced with VectorProductMatchingService in the future
 * for semantic search against a real product catalog.
 */
public class MockProductMatchingService implements ProductMatchingService {

    // Base URL for product images (placeholder)
    private static final String IMAGE_BASE = "https://kitree.co.in/products/images/";

    // Maximum products to return per query
    private static final int MAX_RESULTS = 4;

    // Hardcoded product catalog organized by tags
    private static final Map<String, List<MatchedProduct>> PRODUCTS_BY_TAG;

    static {
        PRODUCTS_BY_TAG = new HashMap<>();

        // =====================================================================
        // CRYSTALS & GEMSTONES
        // =====================================================================

        // Rose Quartz - Love, Venus, Heart Chakra
        PRODUCTS_BY_TAG.put("rose_quartz", List.of(
            new MatchedProduct("RQ-BRAC-001", "Rose Quartz Bracelet 8mm", 599,
                IMAGE_BASE + "rose-quartz-bracelet.jpg", "bracelet", "material: rose_quartz"),
            new MatchedProduct("RQ-PEND-001", "Rose Quartz Heart Pendant", 799,
                IMAGE_BASE + "rose-quartz-pendant.jpg", "pendant", "material: rose_quartz"),
            new MatchedProduct("RQ-MALA-001", "Rose Quartz Mala 108 Beads", 1299,
                IMAGE_BASE + "rose-quartz-mala.jpg", "mala", "material: rose_quartz"),
            new MatchedProduct("RQ-PALM-001", "Rose Quartz Palm Stone", 449,
                IMAGE_BASE + "rose-quartz-palm.jpg", "stone", "material: rose_quartz")
        ));

        // Amethyst - Spirituality, Saturn alternative, Crown Chakra
        PRODUCTS_BY_TAG.put("amethyst", List.of(
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet 8mm", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "material: amethyst"),
            new MatchedProduct("AM-PEND-001", "Amethyst Point Pendant", 899,
                IMAGE_BASE + "amethyst-pendant.jpg", "pendant", "material: amethyst"),
            new MatchedProduct("AM-CLUS-001", "Amethyst Cluster", 1499,
                IMAGE_BASE + "amethyst-cluster.jpg", "decor", "material: amethyst"),
            new MatchedProduct("AM-GEOD-001", "Amethyst Geode Small", 2499,
                IMAGE_BASE + "amethyst-geode.jpg", "decor", "material: amethyst")
        ));

        // Citrine - Wealth, Jupiter, Solar Plexus
        PRODUCTS_BY_TAG.put("citrine", List.of(
            new MatchedProduct("CT-BRAC-001", "Citrine Bracelet 8mm", 799,
                IMAGE_BASE + "citrine-bracelet.jpg", "bracelet", "material: citrine"),
            new MatchedProduct("CT-PEND-001", "Citrine Point Pendant", 999,
                IMAGE_BASE + "citrine-pendant.jpg", "pendant", "material: citrine"),
            new MatchedProduct("CT-TREE-001", "Citrine Money Tree", 1299,
                IMAGE_BASE + "citrine-tree.jpg", "decor", "material: citrine")
        ));

        // Blue Sapphire - Saturn, Career
        PRODUCTS_BY_TAG.put("blue_sapphire", List.of(
            new MatchedProduct("BS-RING-001", "Blue Sapphire Ring (Natural)", 15999,
                IMAGE_BASE + "blue-sapphire-ring.jpg", "ring", "material: blue_sapphire"),
            new MatchedProduct("BS-PEND-001", "Blue Sapphire Pendant", 12999,
                IMAGE_BASE + "blue-sapphire-pendant.jpg", "pendant", "material: blue_sapphire")
        ));

        // Yellow Sapphire - Jupiter
        PRODUCTS_BY_TAG.put("yellow_sapphire", List.of(
            new MatchedProduct("YS-RING-001", "Yellow Sapphire Ring (Natural)", 18999,
                IMAGE_BASE + "yellow-sapphire-ring.jpg", "ring", "material: yellow_sapphire"),
            new MatchedProduct("YS-PEND-001", "Yellow Sapphire Pendant", 14999,
                IMAGE_BASE + "yellow-sapphire-pendant.jpg", "pendant", "material: yellow_sapphire")
        ));

        // Ruby - Sun
        PRODUCTS_BY_TAG.put("ruby", List.of(
            new MatchedProduct("RB-RING-001", "Ruby Ring (Natural)", 22999,
                IMAGE_BASE + "ruby-ring.jpg", "ring", "material: ruby"),
            new MatchedProduct("RB-PEND-001", "Ruby Pendant", 18999,
                IMAGE_BASE + "ruby-pendant.jpg", "pendant", "material: ruby")
        ));

        // Pearl - Moon
        PRODUCTS_BY_TAG.put("pearl", List.of(
            new MatchedProduct("PL-RING-001", "Pearl Ring (Natural)", 4999,
                IMAGE_BASE + "pearl-ring.jpg", "ring", "material: pearl"),
            new MatchedProduct("PL-PEND-001", "Pearl Pendant", 3999,
                IMAGE_BASE + "pearl-pendant.jpg", "pendant", "material: pearl"),
            new MatchedProduct("PL-MALA-001", "Pearl Mala 108 Beads", 8999,
                IMAGE_BASE + "pearl-mala.jpg", "mala", "material: pearl")
        ));

        // Emerald - Mercury
        PRODUCTS_BY_TAG.put("emerald", List.of(
            new MatchedProduct("EM-RING-001", "Emerald Ring (Natural)", 25999,
                IMAGE_BASE + "emerald-ring.jpg", "ring", "material: emerald"),
            new MatchedProduct("EM-PEND-001", "Emerald Pendant", 19999,
                IMAGE_BASE + "emerald-pendant.jpg", "pendant", "material: emerald")
        ));

        // Red Coral - Mars
        PRODUCTS_BY_TAG.put("red_coral", List.of(
            new MatchedProduct("RC-RING-001", "Red Coral Ring", 5999,
                IMAGE_BASE + "red-coral-ring.jpg", "ring", "material: red_coral"),
            new MatchedProduct("RC-PEND-001", "Red Coral Pendant", 4999,
                IMAGE_BASE + "red-coral-pendant.jpg", "pendant", "material: red_coral"),
            new MatchedProduct("RC-BRAC-001", "Red Coral Bracelet", 3499,
                IMAGE_BASE + "red-coral-bracelet.jpg", "bracelet", "material: red_coral")
        ));

        // Hessonite (Gomed) - Rahu
        PRODUCTS_BY_TAG.put("hessonite", List.of(
            new MatchedProduct("HE-RING-001", "Hessonite Ring (Gomed)", 6999,
                IMAGE_BASE + "hessonite-ring.jpg", "ring", "material: hessonite"),
            new MatchedProduct("HE-PEND-001", "Hessonite Pendant", 5499,
                IMAGE_BASE + "hessonite-pendant.jpg", "pendant", "material: hessonite")
        ));

        // Cat's Eye - Ketu
        PRODUCTS_BY_TAG.put("cats_eye", List.of(
            new MatchedProduct("CE-RING-001", "Cat's Eye Ring (Lehsunia)", 7999,
                IMAGE_BASE + "cats-eye-ring.jpg", "ring", "material: cats_eye"),
            new MatchedProduct("CE-PEND-001", "Cat's Eye Pendant", 6499,
                IMAGE_BASE + "cats-eye-pendant.jpg", "pendant", "material: cats_eye")
        ));

        // Black Tourmaline - Protection
        PRODUCTS_BY_TAG.put("black_tourmaline", List.of(
            new MatchedProduct("BT-BRAC-001", "Black Tourmaline Bracelet", 699,
                IMAGE_BASE + "black-tourmaline-bracelet.jpg", "bracelet", "material: black_tourmaline"),
            new MatchedProduct("BT-PEND-001", "Black Tourmaline Pendant", 599,
                IMAGE_BASE + "black-tourmaline-pendant.jpg", "pendant", "material: black_tourmaline"),
            new MatchedProduct("BT-RAW-001", "Raw Black Tourmaline", 399,
                IMAGE_BASE + "black-tourmaline-raw.jpg", "stone", "material: black_tourmaline")
        ));

        // Tiger Eye - Confidence, Career
        PRODUCTS_BY_TAG.put("tiger_eye", List.of(
            new MatchedProduct("TE-BRAC-001", "Tiger Eye Bracelet 8mm", 549,
                IMAGE_BASE + "tiger-eye-bracelet.jpg", "bracelet", "material: tiger_eye"),
            new MatchedProduct("TE-PEND-001", "Tiger Eye Pendant", 649,
                IMAGE_BASE + "tiger-eye-pendant.jpg", "pendant", "material: tiger_eye")
        ));

        // Clear Quartz - Amplification, All purpose
        PRODUCTS_BY_TAG.put("clear_quartz", List.of(
            new MatchedProduct("CQ-BRAC-001", "Clear Quartz Bracelet", 499,
                IMAGE_BASE + "clear-quartz-bracelet.jpg", "bracelet", "material: clear_quartz"),
            new MatchedProduct("CQ-PEND-001", "Clear Quartz Point Pendant", 599,
                IMAGE_BASE + "clear-quartz-pendant.jpg", "pendant", "material: clear_quartz"),
            new MatchedProduct("CQ-WAND-001", "Clear Quartz Wand", 899,
                IMAGE_BASE + "clear-quartz-wand.jpg", "tool", "material: clear_quartz")
        ));

        // Obsidian - Protection, Grounding
        PRODUCTS_BY_TAG.put("obsidian", List.of(
            new MatchedProduct("OB-BRAC-001", "Black Obsidian Bracelet", 549,
                IMAGE_BASE + "obsidian-bracelet.jpg", "bracelet", "material: obsidian"),
            new MatchedProduct("OB-MIRR-001", "Obsidian Scrying Mirror", 1299,
                IMAGE_BASE + "obsidian-mirror.jpg", "tool", "material: obsidian")
        ));

        // Lapis Lazuli - Wisdom, Throat Chakra
        PRODUCTS_BY_TAG.put("lapis_lazuli", List.of(
            new MatchedProduct("LL-BRAC-001", "Lapis Lazuli Bracelet", 899,
                IMAGE_BASE + "lapis-bracelet.jpg", "bracelet", "material: lapis_lazuli"),
            new MatchedProduct("LL-PEND-001", "Lapis Lazuli Pendant", 999,
                IMAGE_BASE + "lapis-pendant.jpg", "pendant", "material: lapis_lazuli")
        ));

        // =====================================================================
        // PLANETARY ASSOCIATIONS
        // =====================================================================

        PRODUCTS_BY_TAG.put("sun", List.of(
            new MatchedProduct("RB-RING-001", "Ruby Ring (Natural)", 22999,
                IMAGE_BASE + "ruby-ring.jpg", "ring", "planet: sun"),
            new MatchedProduct("RB-PEND-001", "Ruby Pendant", 18999,
                IMAGE_BASE + "ruby-pendant.jpg", "pendant", "planet: sun"),
            new MatchedProduct("CT-BRAC-001", "Citrine Bracelet", 799,
                IMAGE_BASE + "citrine-bracelet.jpg", "bracelet", "planet: sun (alternative)")
        ));

        PRODUCTS_BY_TAG.put("moon", List.of(
            new MatchedProduct("PL-RING-001", "Pearl Ring", 4999,
                IMAGE_BASE + "pearl-ring.jpg", "ring", "planet: moon"),
            new MatchedProduct("PL-PEND-001", "Pearl Pendant", 3999,
                IMAGE_BASE + "pearl-pendant.jpg", "pendant", "planet: moon"),
            new MatchedProduct("MS-BRAC-001", "Moonstone Bracelet", 799,
                IMAGE_BASE + "moonstone-bracelet.jpg", "bracelet", "planet: moon (alternative)")
        ));

        PRODUCTS_BY_TAG.put("mars", List.of(
            new MatchedProduct("RC-RING-001", "Red Coral Ring", 5999,
                IMAGE_BASE + "red-coral-ring.jpg", "ring", "planet: mars"),
            new MatchedProduct("RC-PEND-001", "Red Coral Pendant", 4999,
                IMAGE_BASE + "red-coral-pendant.jpg", "pendant", "planet: mars"),
            new MatchedProduct("RJ-BRAC-001", "Red Jasper Bracelet", 549,
                IMAGE_BASE + "red-jasper-bracelet.jpg", "bracelet", "planet: mars (alternative)")
        ));

        PRODUCTS_BY_TAG.put("mercury", List.of(
            new MatchedProduct("EM-RING-001", "Emerald Ring", 25999,
                IMAGE_BASE + "emerald-ring.jpg", "ring", "planet: mercury"),
            new MatchedProduct("EM-PEND-001", "Emerald Pendant", 19999,
                IMAGE_BASE + "emerald-pendant.jpg", "pendant", "planet: mercury"),
            new MatchedProduct("GR-BRAC-001", "Green Aventurine Bracelet", 549,
                IMAGE_BASE + "green-aventurine-bracelet.jpg", "bracelet", "planet: mercury (alternative)")
        ));

        PRODUCTS_BY_TAG.put("jupiter", List.of(
            new MatchedProduct("YS-RING-001", "Yellow Sapphire Ring", 18999,
                IMAGE_BASE + "yellow-sapphire-ring.jpg", "ring", "planet: jupiter"),
            new MatchedProduct("YS-PEND-001", "Yellow Sapphire Pendant", 14999,
                IMAGE_BASE + "yellow-sapphire-pendant.jpg", "pendant", "planet: jupiter"),
            new MatchedProduct("CT-BRAC-001", "Citrine Bracelet", 799,
                IMAGE_BASE + "citrine-bracelet.jpg", "bracelet", "planet: jupiter (alternative)")
        ));

        PRODUCTS_BY_TAG.put("venus", List.of(
            new MatchedProduct("DM-PEND-001", "Diamond Pendant", 35999,
                IMAGE_BASE + "diamond-pendant.jpg", "pendant", "planet: venus"),
            new MatchedProduct("RQ-BRAC-001", "Rose Quartz Bracelet", 599,
                IMAGE_BASE + "rose-quartz-bracelet.jpg", "bracelet", "planet: venus (alternative)"),
            new MatchedProduct("OP-RING-001", "Opal Ring", 8999,
                IMAGE_BASE + "opal-ring.jpg", "ring", "planet: venus (alternative)")
        ));

        PRODUCTS_BY_TAG.put("saturn", List.of(
            new MatchedProduct("BS-RING-001", "Blue Sapphire Ring", 15999,
                IMAGE_BASE + "blue-sapphire-ring.jpg", "ring", "planet: saturn"),
            new MatchedProduct("BS-PEND-001", "Blue Sapphire Pendant", 12999,
                IMAGE_BASE + "blue-sapphire-pendant.jpg", "pendant", "planet: saturn"),
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "planet: saturn (alternative)"),
            new MatchedProduct("IR-RING-001", "Iron Ring (Shani)", 299,
                IMAGE_BASE + "iron-ring.jpg", "ring", "planet: saturn (traditional)")
        ));

        PRODUCTS_BY_TAG.put("rahu", List.of(
            new MatchedProduct("HE-RING-001", "Hessonite Ring (Gomed)", 6999,
                IMAGE_BASE + "hessonite-ring.jpg", "ring", "planet: rahu"),
            new MatchedProduct("HE-PEND-001", "Hessonite Pendant", 5499,
                IMAGE_BASE + "hessonite-pendant.jpg", "pendant", "planet: rahu")
        ));

        PRODUCTS_BY_TAG.put("ketu", List.of(
            new MatchedProduct("CE-RING-001", "Cat's Eye Ring", 7999,
                IMAGE_BASE + "cats-eye-ring.jpg", "ring", "planet: ketu"),
            new MatchedProduct("CE-PEND-001", "Cat's Eye Pendant", 6499,
                IMAGE_BASE + "cats-eye-pendant.jpg", "pendant", "planet: ketu")
        ));

        // =====================================================================
        // PURPOSE/INTENTION
        // =====================================================================

        PRODUCTS_BY_TAG.put("love", List.of(
            new MatchedProduct("RQ-BRAC-001", "Rose Quartz Bracelet", 599,
                IMAGE_BASE + "rose-quartz-bracelet.jpg", "bracelet", "purpose: love"),
            new MatchedProduct("RQ-PEND-001", "Rose Quartz Heart Pendant", 799,
                IMAGE_BASE + "rose-quartz-pendant.jpg", "pendant", "purpose: love"),
            new MatchedProduct("RD-BRAC-001", "Rhodonite Bracelet", 649,
                IMAGE_BASE + "rhodonite-bracelet.jpg", "bracelet", "purpose: love")
        ));

        PRODUCTS_BY_TAG.put("career", List.of(
            new MatchedProduct("CT-BRAC-001", "Citrine Bracelet", 799,
                IMAGE_BASE + "citrine-bracelet.jpg", "bracelet", "purpose: career"),
            new MatchedProduct("TE-BRAC-001", "Tiger Eye Bracelet", 549,
                IMAGE_BASE + "tiger-eye-bracelet.jpg", "bracelet", "purpose: career"),
            new MatchedProduct("PY-CUBE-001", "Pyrite Cube", 699,
                IMAGE_BASE + "pyrite-cube.jpg", "decor", "purpose: career")
        ));

        PRODUCTS_BY_TAG.put("wealth", List.of(
            new MatchedProduct("CT-BRAC-001", "Citrine Bracelet", 799,
                IMAGE_BASE + "citrine-bracelet.jpg", "bracelet", "purpose: wealth"),
            new MatchedProduct("CT-TREE-001", "Citrine Money Tree", 1299,
                IMAGE_BASE + "citrine-tree.jpg", "decor", "purpose: wealth"),
            new MatchedProduct("PY-CLUS-001", "Pyrite Cluster", 899,
                IMAGE_BASE + "pyrite-cluster.jpg", "decor", "purpose: wealth"),
            new MatchedProduct("GR-BRAC-001", "Green Aventurine Bracelet", 549,
                IMAGE_BASE + "green-aventurine-bracelet.jpg", "bracelet", "purpose: wealth")
        ));

        PRODUCTS_BY_TAG.put("protection", List.of(
            new MatchedProduct("BT-BRAC-001", "Black Tourmaline Bracelet", 699,
                IMAGE_BASE + "black-tourmaline-bracelet.jpg", "bracelet", "purpose: protection"),
            new MatchedProduct("OB-BRAC-001", "Black Obsidian Bracelet", 549,
                IMAGE_BASE + "obsidian-bracelet.jpg", "bracelet", "purpose: protection"),
            new MatchedProduct("TE-PEND-001", "Tiger Eye Pendant", 649,
                IMAGE_BASE + "tiger-eye-pendant.jpg", "pendant", "purpose: protection")
        ));

        PRODUCTS_BY_TAG.put("health", List.of(
            new MatchedProduct("CQ-BRAC-001", "Clear Quartz Bracelet", 499,
                IMAGE_BASE + "clear-quartz-bracelet.jpg", "bracelet", "purpose: health"),
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "purpose: health"),
            new MatchedProduct("BL-BRAC-001", "Bloodstone Bracelet", 599,
                IMAGE_BASE + "bloodstone-bracelet.jpg", "bracelet", "purpose: health")
        ));

        PRODUCTS_BY_TAG.put("spirituality", List.of(
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "purpose: spirituality"),
            new MatchedProduct("CQ-WAND-001", "Clear Quartz Wand", 899,
                IMAGE_BASE + "clear-quartz-wand.jpg", "tool", "purpose: spirituality"),
            new MatchedProduct("LP-BRAC-001", "Labradorite Bracelet", 899,
                IMAGE_BASE + "labradorite-bracelet.jpg", "bracelet", "purpose: spirituality"),
            new MatchedProduct("RU-MALA-001", "Rudraksha Mala 5 Mukhi", 999,
                IMAGE_BASE + "rudraksha-mala.jpg", "mala", "purpose: spirituality")
        ));

        PRODUCTS_BY_TAG.put("meditation", List.of(
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "purpose: meditation"),
            new MatchedProduct("LP-BRAC-001", "Labradorite Bracelet", 899,
                IMAGE_BASE + "labradorite-bracelet.jpg", "bracelet", "purpose: meditation"),
            new MatchedProduct("CQ-PEND-001", "Clear Quartz Point Pendant", 599,
                IMAGE_BASE + "clear-quartz-pendant.jpg", "pendant", "purpose: meditation")
        ));

        // =====================================================================
        // CHAKRAS
        // =====================================================================

        PRODUCTS_BY_TAG.put("root", List.of(
            new MatchedProduct("BT-BRAC-001", "Black Tourmaline Bracelet", 699,
                IMAGE_BASE + "black-tourmaline-bracelet.jpg", "bracelet", "chakra: root"),
            new MatchedProduct("OB-BRAC-001", "Black Obsidian Bracelet", 549,
                IMAGE_BASE + "obsidian-bracelet.jpg", "bracelet", "chakra: root"),
            new MatchedProduct("RJ-BRAC-001", "Red Jasper Bracelet", 549,
                IMAGE_BASE + "red-jasper-bracelet.jpg", "bracelet", "chakra: root")
        ));

        PRODUCTS_BY_TAG.put("sacral", List.of(
            new MatchedProduct("CR-BRAC-001", "Carnelian Bracelet", 599,
                IMAGE_BASE + "carnelian-bracelet.jpg", "bracelet", "chakra: sacral"),
            new MatchedProduct("OR-CALC-001", "Orange Calcite", 449,
                IMAGE_BASE + "orange-calcite.jpg", "stone", "chakra: sacral")
        ));

        PRODUCTS_BY_TAG.put("solar_plexus", List.of(
            new MatchedProduct("CT-BRAC-001", "Citrine Bracelet", 799,
                IMAGE_BASE + "citrine-bracelet.jpg", "bracelet", "chakra: solar_plexus"),
            new MatchedProduct("TE-BRAC-001", "Tiger Eye Bracelet", 549,
                IMAGE_BASE + "tiger-eye-bracelet.jpg", "bracelet", "chakra: solar_plexus")
        ));

        PRODUCTS_BY_TAG.put("heart", List.of(
            new MatchedProduct("RQ-BRAC-001", "Rose Quartz Bracelet", 599,
                IMAGE_BASE + "rose-quartz-bracelet.jpg", "bracelet", "chakra: heart"),
            new MatchedProduct("GR-BRAC-001", "Green Aventurine Bracelet", 549,
                IMAGE_BASE + "green-aventurine-bracelet.jpg", "bracelet", "chakra: heart"),
            new MatchedProduct("ML-BRAC-001", "Malachite Bracelet", 999,
                IMAGE_BASE + "malachite-bracelet.jpg", "bracelet", "chakra: heart")
        ));

        PRODUCTS_BY_TAG.put("throat", List.of(
            new MatchedProduct("LL-BRAC-001", "Lapis Lazuli Bracelet", 899,
                IMAGE_BASE + "lapis-bracelet.jpg", "bracelet", "chakra: throat"),
            new MatchedProduct("AQ-BRAC-001", "Aquamarine Bracelet", 1299,
                IMAGE_BASE + "aquamarine-bracelet.jpg", "bracelet", "chakra: throat"),
            new MatchedProduct("BL-LACE-001", "Blue Lace Agate Bracelet", 749,
                IMAGE_BASE + "blue-lace-agate.jpg", "bracelet", "chakra: throat")
        ));

        PRODUCTS_BY_TAG.put("third_eye", List.of(
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "chakra: third_eye"),
            new MatchedProduct("LP-BRAC-001", "Labradorite Bracelet", 899,
                IMAGE_BASE + "labradorite-bracelet.jpg", "bracelet", "chakra: third_eye"),
            new MatchedProduct("SO-BRAC-001", "Sodalite Bracelet", 649,
                IMAGE_BASE + "sodalite-bracelet.jpg", "bracelet", "chakra: third_eye")
        ));

        PRODUCTS_BY_TAG.put("crown", List.of(
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "chakra: crown"),
            new MatchedProduct("CQ-BRAC-001", "Clear Quartz Bracelet", 499,
                IMAGE_BASE + "clear-quartz-bracelet.jpg", "bracelet", "chakra: crown"),
            new MatchedProduct("SL-PEND-001", "Selenite Pendant", 599,
                IMAGE_BASE + "selenite-pendant.jpg", "pendant", "chakra: crown")
        ));

        // =====================================================================
        // TRADITIONAL REMEDIES
        // =====================================================================

        PRODUCTS_BY_TAG.put("rudraksha", List.of(
            new MatchedProduct("RU-MALA-001", "Rudraksha Mala 5 Mukhi", 999,
                IMAGE_BASE + "rudraksha-mala.jpg", "mala", "traditional: rudraksha"),
            new MatchedProduct("RU-BRAC-001", "Rudraksha Bracelet", 599,
                IMAGE_BASE + "rudraksha-bracelet.jpg", "bracelet", "traditional: rudraksha"),
            new MatchedProduct("RU-1MUK-001", "1 Mukhi Rudraksha", 15999,
                IMAGE_BASE + "rudraksha-1mukhi.jpg", "bead", "traditional: rudraksha")
        ));

        PRODUCTS_BY_TAG.put("tulsi", List.of(
            new MatchedProduct("TL-MALA-001", "Tulsi Mala 108 Beads", 399,
                IMAGE_BASE + "tulsi-mala.jpg", "mala", "traditional: tulsi"),
            new MatchedProduct("TL-BRAC-001", "Tulsi Bracelet", 249,
                IMAGE_BASE + "tulsi-bracelet.jpg", "bracelet", "traditional: tulsi")
        ));

        // Generic crystal (fallback)
        PRODUCTS_BY_TAG.put("crystal", List.of(
            new MatchedProduct("CQ-BRAC-001", "Clear Quartz Bracelet", 499,
                IMAGE_BASE + "clear-quartz-bracelet.jpg", "bracelet", "generic: crystal"),
            new MatchedProduct("AM-BRAC-001", "Amethyst Bracelet", 699,
                IMAGE_BASE + "amethyst-bracelet.jpg", "bracelet", "generic: crystal"),
            new MatchedProduct("RQ-BRAC-001", "Rose Quartz Bracelet", 599,
                IMAGE_BASE + "rose-quartz-bracelet.jpg", "bracelet", "generic: crystal")
        ));
    }

    @Override
    public List<MatchedProduct> findMatchingProducts(RemedyAttributes attributes) {
        if (attributes == null || !attributes.needsProductMatching()) {
            return List.of();
        }

        // Use LinkedHashSet to maintain order and avoid duplicates
        Set<MatchedProduct> results = new LinkedHashSet<>();

        // Priority 1: Match by material (most specific)
        if (attributes.getMaterial() != null) {
            for (String material : attributes.getMaterial()) {
                String key = normalizeTag(material);
                List<MatchedProduct> matches = PRODUCTS_BY_TAG.get(key);
                if (matches != null) {
                    // If product type is specified, filter
                    if (attributes.getProductType() != null) {
                        for (MatchedProduct p : matches) {
                            if (attributes.getProductType().equalsIgnoreCase(p.getProductType())) {
                                results.add(p);
                            }
                        }
                    } else {
                        results.addAll(matches);
                    }
                }
            }
        }

        // Priority 2: Match by planet
        if (results.size() < MAX_RESULTS && attributes.getPlanets() != null) {
            for (String planet : attributes.getPlanets()) {
                String key = normalizeTag(planet);
                List<MatchedProduct> matches = PRODUCTS_BY_TAG.get(key);
                if (matches != null) {
                    results.addAll(matches);
                }
            }
        }

        // Priority 3: Match by purpose
        if (results.size() < MAX_RESULTS && attributes.getPurpose() != null) {
            for (String purpose : attributes.getPurpose()) {
                String key = normalizeTag(purpose);
                List<MatchedProduct> matches = PRODUCTS_BY_TAG.get(key);
                if (matches != null) {
                    results.addAll(matches);
                }
            }
        }

        // Priority 4: Match by chakra
        if (results.size() < MAX_RESULTS && attributes.getChakras() != null) {
            for (String chakra : attributes.getChakras()) {
                String key = normalizeTag(chakra);
                List<MatchedProduct> matches = PRODUCTS_BY_TAG.get(key);
                if (matches != null) {
                    results.addAll(matches);
                }
            }
        }

        // Fallback: if nothing matched, return generic crystals
        if (results.isEmpty()) {
            results.addAll(PRODUCTS_BY_TAG.getOrDefault("crystal", List.of()));
        }

        // Limit results
        List<MatchedProduct> resultList = new ArrayList<>(results);
        return resultList.subList(0, Math.min(resultList.size(), MAX_RESULTS));
    }

    @Override
    public List<MatchedProduct> findByTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return List.of();
        }

        String key = normalizeTag(tag);
        List<MatchedProduct> matches = PRODUCTS_BY_TAG.get(key);

        if (matches == null) {
            return PRODUCTS_BY_TAG.getOrDefault("crystal", List.of());
        }

        return matches.subList(0, Math.min(matches.size(), MAX_RESULTS));
    }

    /**
     * Normalize tag for lookup (lowercase, replace spaces with underscores).
     */
    private String normalizeTag(String tag) {
        return tag.toLowerCase()
                  .trim()
                  .replace(" ", "_")
                  .replace("-", "_");
    }
}
