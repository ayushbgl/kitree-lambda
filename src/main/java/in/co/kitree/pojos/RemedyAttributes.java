package in.co.kitree.pojos;

import java.util.List;

/**
 * Attributes extracted from an expert's remedy recommendation.
 * Used for matching against product catalog.
 */
public class RemedyAttributes {
    // Remedy classification
    private String remedyType;      // product, mantra, ritual, donation, lifestyle, puja
    private String specificity;     // exact, category, attribute, planetary, general

    // Product matching attributes
    private List<String> material;      // rose_quartz, blue_sapphire, amethyst
    private String productType;         // bracelet, pendant, ring, mala (null = any)
    private List<String> color;         // pink, blue, green
    private List<String> purpose;       // love, career, protection, health
    private List<String> planets;       // saturn, venus, jupiter, mars
    private List<String> chakras;       // heart, throat, root
    private Boolean wearable;           // Must be wearable item

    // Original expert recommendation
    private String expertQuote;

    public RemedyAttributes() {}

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RemedyAttributes attrs = new RemedyAttributes();

        public Builder remedyType(String type) { attrs.remedyType = type; return this; }
        public Builder specificity(String spec) { attrs.specificity = spec; return this; }
        public Builder material(List<String> mat) { attrs.material = mat; return this; }
        public Builder material(String... mat) { attrs.material = List.of(mat); return this; }
        public Builder productType(String type) { attrs.productType = type; return this; }
        public Builder color(List<String> col) { attrs.color = col; return this; }
        public Builder color(String... col) { attrs.color = List.of(col); return this; }
        public Builder purpose(List<String> purp) { attrs.purpose = purp; return this; }
        public Builder purpose(String... purp) { attrs.purpose = List.of(purp); return this; }
        public Builder planets(List<String> plan) { attrs.planets = plan; return this; }
        public Builder planets(String... plan) { attrs.planets = List.of(plan); return this; }
        public Builder chakras(List<String> chak) { attrs.chakras = chak; return this; }
        public Builder chakras(String... chak) { attrs.chakras = List.of(chak); return this; }
        public Builder wearable(Boolean wear) { attrs.wearable = wear; return this; }
        public Builder expertQuote(String quote) { attrs.expertQuote = quote; return this; }

        public RemedyAttributes build() { return attrs; }
    }

    // Getters and Setters
    public String getRemedyType() { return remedyType; }
    public void setRemedyType(String remedyType) { this.remedyType = remedyType; }

    public String getSpecificity() { return specificity; }
    public void setSpecificity(String specificity) { this.specificity = specificity; }

    public List<String> getMaterial() { return material; }
    public void setMaterial(List<String> material) { this.material = material; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public List<String> getColor() { return color; }
    public void setColor(List<String> color) { this.color = color; }

    public List<String> getPurpose() { return purpose; }
    public void setPurpose(List<String> purpose) { this.purpose = purpose; }

    public List<String> getPlanets() { return planets; }
    public void setPlanets(List<String> planets) { this.planets = planets; }

    public List<String> getChakras() { return chakras; }
    public void setChakras(List<String> chakras) { this.chakras = chakras; }

    public Boolean getWearable() { return wearable; }
    public void setWearable(Boolean wearable) { this.wearable = wearable; }

    public String getExpertQuote() { return expertQuote; }
    public void setExpertQuote(String expertQuote) { this.expertQuote = expertQuote; }

    /**
     * Check if this is a product-type remedy that needs matching.
     */
    public boolean needsProductMatching() {
        return "product".equalsIgnoreCase(remedyType);
    }

    /**
     * Create from a Map (Gemini response parsing).
     */
    @SuppressWarnings("unchecked")
    public static RemedyAttributes fromMap(java.util.Map<String, Object> map) {
        RemedyAttributes attrs = new RemedyAttributes();

        if (map == null) return attrs;

        attrs.remedyType = (String) map.get("remedy_type");
        attrs.specificity = (String) map.get("specificity");
        attrs.expertQuote = (String) map.get("expert_quote");

        java.util.Map<String, Object> productAttrs =
            (java.util.Map<String, Object>) map.get("product_attributes");

        if (productAttrs != null) {
            attrs.material = (List<String>) productAttrs.get("material");
            attrs.productType = (String) productAttrs.get("product_type");
            attrs.color = (List<String>) productAttrs.get("color");
            attrs.purpose = (List<String>) productAttrs.get("purpose");
            attrs.planets = (List<String>) productAttrs.get("planets");
            attrs.chakras = (List<String>) productAttrs.get("chakras");
            attrs.wearable = (Boolean) productAttrs.get("wearable");
        }

        return attrs;
    }
}
