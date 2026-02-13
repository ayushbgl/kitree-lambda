package in.co.kitree.pojos;

/**
 * Enum for wallet transaction types.
 * Used to ensure type safety and prevent invalid transaction type strings.
 */
public enum TransactionType {
    RECHARGE,
    BONUS,          // Promotional credit added alongside a recharge (not real cash)
    CONSULTATION_DEDUCTION,
    PRODUCT_DEDUCTION,
    DIGITAL_PRODUCT_DEDUCTION,
    WEBINAR_DEDUCTION,
    ORDER_EARNING,  // For expert earnings (all order types)
    REFUND,
    CASHBACK,
    REFERRAL_BONUS;
    
    /**
     * Convert enum to string (for Firestore storage).
     */
    public String toString() {
        return this.name();
    }
    
    /**
     * Convert string to enum (for reading from Firestore).
     * Returns null if string doesn't match any enum value.
     */
    public static TransactionType fromString(String value) {
        if (value == null) return null;
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
