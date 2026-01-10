package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import in.co.kitree.pojos.PlatformFeeConfig;
import in.co.kitree.pojos.WalletTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for handling wallet operations including balance management and transactions.
 */
public class WalletService {
    private static final String DEFAULT_CURRENCY = "INR";
    
    private final Firestore db;

    public WalletService(Firestore db) {
        this.db = db;
    }

    /**
     * Get wallet balances for a user (all currencies).
     *
     * @param userId The user's ID
     * @return Map of currency codes to balances
     */
    public Map<String, Double> getWalletBalances(String userId) throws ExecutionException, InterruptedException {
        DocumentSnapshot userDoc = db.collection("users").document(userId).get().get();
        if (userDoc.exists() && userDoc.contains("wallet_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> balances = (Map<String, Object>) userDoc.get("wallet_balances");
            if (balances != null) {
                Map<String, Double> result = new HashMap<>();
                for (Map.Entry<String, Object> entry : balances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                return result;
            }
        }
        return new HashMap<>();
    }

    /**
     * Get wallet balance for a user in a specific currency.
     *
     * @param userId   The user's ID
     * @param currency The currency code (e.g., "INR", "USD")
     * @return The balance in the specified currency (0.0 if not found)
     */
    public Double getWalletBalance(String userId, String currency) throws ExecutionException, InterruptedException {
        Map<String, Double> balances = getWalletBalances(userId);
        return balances.getOrDefault(currency, 0.0);
    }

    /**
     * Get the user's default currency.
     *
     * @param userId The user's ID
     * @return The default currency code (defaults to "INR")
     */
    public String getUserDefaultCurrency(String userId) throws ExecutionException, InterruptedException {
        DocumentSnapshot userDoc = db.collection("users").document(userId).get().get();
        if (userDoc.exists() && userDoc.contains("default_currency")) {
            String currency = userDoc.getString("default_currency");
            return currency != null ? currency : DEFAULT_CURRENCY;
        }
        return DEFAULT_CURRENCY;
    }
    
    public static String getDefaultCurrency() {
        return DEFAULT_CURRENCY;
    }

    /**
     * Update wallet balance for a user in a specific currency using a Firestore transaction.
     * This method should be called within an existing transaction context.
     *
     * @param transaction The Firestore transaction
     * @param userId      The user's ID
     * @param currency    The currency code
     * @param amount      The amount to add (positive) or subtract (negative)
     * @return The new balance after the update
     */
    public Double updateWalletBalanceInTransaction(Transaction transaction, String userId, String currency, Double amount)
            throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentSnapshot userDoc = transaction.get(userRef).get();
        
        Map<String, Double> balances = new HashMap<>();
        if (userDoc.exists() && userDoc.contains("wallet_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingBalances = (Map<String, Object>) userDoc.get("wallet_balances");
            if (existingBalances != null) {
                for (Map.Entry<String, Object> entry : existingBalances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        balances.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        Double currentBalance = balances.getOrDefault(currency, 0.0);
        Double newBalance = currentBalance + amount;
        balances.put(currency, newBalance);
        
        transaction.update(userRef, "wallet_balances", balances);
        return newBalance;
    }

    /**
     * Increment wallet balance (non-transactional, use with caution).
     *
     * @param userId   The user's ID
     * @param currency The currency code
     * @param amount   The amount to add
     * @return The new balance
     */
    public Double incrementWalletBalance(String userId, String currency, Double amount)
            throws ExecutionException, InterruptedException {
        DocumentReference userRef = db.collection("users").document(userId);
        
        // Use FieldValue.increment for atomic updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("wallet_balances." + currency, FieldValue.increment(amount));
        userRef.update(updates).get();
        
        return getWalletBalance(userId, currency);
    }

    /**
     * Create a wallet transaction record.
     *
     * @param userId      The user's ID
     * @param transaction The wallet transaction to create
     * @return The transaction ID
     */
    public String createWalletTransaction(String userId, WalletTransaction transaction)
            throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId).collection("wallet_transactions");
        DocumentReference docRef = transactionsRef.document();
        
        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("type", transaction.getType());
        transactionData.put("source", transaction.getSource());
        transactionData.put("amount", transaction.getAmount());
        transactionData.put("currency", transaction.getCurrency());
        transactionData.put("status", transaction.getStatus());
        transactionData.put("created_at", transaction.getCreatedAt() != null ? transaction.getCreatedAt() : Timestamp.now());
        transactionData.put("description", transaction.getDescription());
        
        // Add optional fields if present
        if (transaction.getPaymentId() != null) {
            transactionData.put("payment_id", transaction.getPaymentId());
        }
        if (transaction.getOrderId() != null) {
            transactionData.put("order_id", transaction.getOrderId());
        }
        if (transaction.getCouponCode() != null) {
            transactionData.put("coupon_code", transaction.getCouponCode());
        }
        if (transaction.getReferralCode() != null) {
            transactionData.put("referral_code", transaction.getReferralCode());
        }
        if (transaction.getReferrerId() != null) {
            transactionData.put("referrer_id", transaction.getReferrerId());
        }
        if (transaction.getCashbackSourceOrderId() != null) {
            transactionData.put("cashback_source_order_id", transaction.getCashbackSourceOrderId());
        }
        if (transaction.getCashbackReason() != null) {
            transactionData.put("cashback_reason", transaction.getCashbackReason());
        }
        if (transaction.getRefundReason() != null) {
            transactionData.put("refund_reason", transaction.getRefundReason());
        }
        
        docRef.set(transactionData).get();
        return docRef.getId();
    }

    /**
     * Create a wallet transaction within an existing Firestore transaction.
     */
    public String createWalletTransactionInTransaction(Transaction firestoreTransaction, String userId, WalletTransaction walletTransaction)
            throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId).collection("wallet_transactions");
        DocumentReference docRef = transactionsRef.document();
        
        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("type", walletTransaction.getType());
        transactionData.put("source", walletTransaction.getSource());
        transactionData.put("amount", walletTransaction.getAmount());
        transactionData.put("currency", walletTransaction.getCurrency());
        transactionData.put("status", walletTransaction.getStatus());
        transactionData.put("created_at", walletTransaction.getCreatedAt() != null ? walletTransaction.getCreatedAt() : Timestamp.now());
        transactionData.put("description", walletTransaction.getDescription());
        
        // Add optional fields if present
        if (walletTransaction.getPaymentId() != null) {
            transactionData.put("payment_id", walletTransaction.getPaymentId());
        }
        if (walletTransaction.getOrderId() != null) {
            transactionData.put("order_id", walletTransaction.getOrderId());
        }
        if (walletTransaction.getCouponCode() != null) {
            transactionData.put("coupon_code", walletTransaction.getCouponCode());
        }
        if (walletTransaction.getReferralCode() != null) {
            transactionData.put("referral_code", walletTransaction.getReferralCode());
        }
        if (walletTransaction.getReferrerId() != null) {
            transactionData.put("referrer_id", walletTransaction.getReferrerId());
        }
        if (walletTransaction.getCashbackSourceOrderId() != null) {
            transactionData.put("cashback_source_order_id", walletTransaction.getCashbackSourceOrderId());
        }
        if (walletTransaction.getCashbackReason() != null) {
            transactionData.put("cashback_reason", walletTransaction.getCashbackReason());
        }
        if (walletTransaction.getRefundReason() != null) {
            transactionData.put("refund_reason", walletTransaction.getRefundReason());
        }
        
        firestoreTransaction.set(docRef, transactionData);
        return docRef.getId();
    }

    /**
     * Check if a payment has already been processed (to prevent duplicate recharges).
     *
     * @param userId    The user's ID
     * @param paymentId The Razorpay payment ID
     * @return true if payment already processed
     */
    public boolean isPaymentAlreadyProcessed(String userId, String paymentId) throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId).collection("wallet_transactions");
        Query query = transactionsRef.whereEqualTo("payment_id", paymentId).whereEqualTo("status", "COMPLETED");
        return !query.get().get().isEmpty();
    }

    /**
     * Get the platform fee configuration for an expert.
     *
     * @param expertId The expert's ID
     * @return The platform fee configuration (defaults to 10% if not found)
     */
    public PlatformFeeConfig getPlatformFeeConfig(String expertId) throws ExecutionException, InterruptedException {
        // Try to get from separate document: users/{expertId}/platform_fee_config
        DocumentSnapshot feeConfigDoc = db.collection("users").document(expertId)
                .collection("platform_fee_config").document("config").get().get();
        
        if (feeConfigDoc.exists()) {
            return docToPlatformFeeConfig(feeConfigDoc);
        }
        
        // Fallback: Try to get from top-level field in users/{expertId}
        DocumentSnapshot userDoc = db.collection("users").document(expertId).get().get();
        if (userDoc.exists() && userDoc.contains("platform_fee_config")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) userDoc.get("platform_fee_config");
            return mapToPlatformFeeConfig(configMap);
        }
        
        // Legacy: Try to get from private collection (for backward compatibility)
        DocumentSnapshot privateDoc = db.collection("users").document(expertId)
                .collection("private").document("platform_fee_config").get().get();
        
        if (privateDoc.exists()) {
            return docToPlatformFeeConfig(privateDoc);
        }
        
        // Return default config
        return new PlatformFeeConfig();
    }

    private PlatformFeeConfig docToPlatformFeeConfig(DocumentSnapshot doc) {
        PlatformFeeConfig config = new PlatformFeeConfig();
        if (doc.contains("default_fee_percent")) {
            config.setDefaultFeePercent(doc.getDouble("default_fee_percent"));
        }
        if (doc.contains("fee_by_type")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> feeByType = (Map<String, Object>) doc.get("fee_by_type");
            if (feeByType != null) {
                Map<String, Double> result = new HashMap<>();
                for (Map.Entry<String, Object> entry : feeByType.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                config.setFeeByType(result);
            }
        }
        if (doc.contains("fee_by_category")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> feeByCategory = (Map<String, Object>) doc.get("fee_by_category");
            if (feeByCategory != null) {
                Map<String, Double> result = new HashMap<>();
                for (Map.Entry<String, Object> entry : feeByCategory.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                config.setFeeByCategory(result);
            }
        }
        return config;
    }

    private PlatformFeeConfig mapToPlatformFeeConfig(Map<String, Object> configMap) {
        PlatformFeeConfig config = new PlatformFeeConfig();
        if (configMap != null) {
            if (configMap.containsKey("default_fee_percent") && configMap.get("default_fee_percent") instanceof Number) {
                config.setDefaultFeePercent(((Number) configMap.get("default_fee_percent")).doubleValue());
            }
            if (configMap.containsKey("fee_by_type")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> feeByType = (Map<String, Object>) configMap.get("fee_by_type");
                if (feeByType != null) {
                    Map<String, Double> result = new HashMap<>();
                    for (Map.Entry<String, Object> entry : feeByType.entrySet()) {
                        if (entry.getValue() instanceof Number) {
                            result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                        }
                    }
                    config.setFeeByType(result);
                }
            }
            if (configMap.containsKey("fee_by_category")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> feeByCategory = (Map<String, Object>) configMap.get("fee_by_category");
                if (feeByCategory != null) {
                    Map<String, Double> result = new HashMap<>();
                    for (Map.Entry<String, Object> entry : feeByCategory.entrySet()) {
                        if (entry.getValue() instanceof Number) {
                            result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                        }
                    }
                    config.setFeeByCategory(result);
                }
            }
        }
        return config;
    }

    /**
     * Get expert's computed status for display (BUSY > OFFLINE > ONLINE).
     * This combines is_online (user-controlled) and consultation_status (system-controlled).
     *
     * @param expertId The expert's ID
     * @return The computed status: "BUSY" if consultation_status is BUSY, 
     *         "OFFLINE" if is_online is false, "ONLINE" if is_online is true
     */
    public String getExpertStatus(String expertId) throws ExecutionException, InterruptedException {
        DocumentSnapshot storeDoc = db.collection("users").document(expertId)
                .collection("public").document("store").get().get();
        
        if (!storeDoc.exists()) {
            System.out.println("[WalletService] Expert store document does not exist for expertId: " + expertId);
            return "OFFLINE";
        }
        
        // Check consultation_status first (system-controlled: BUSY/FREE)
        String consultationStatus = storeDoc.getString("consultation_status");
        if ("BUSY".equals(consultationStatus)) {
            System.out.println("[WalletService] Expert consultation_status is BUSY for expertId: " + expertId);
            return "BUSY";
        }
        
        // Then check is_online (user-controlled: true/false)
        Boolean isOnline = storeDoc.getBoolean("is_online");
        System.out.println("[WalletService] Expert is_online value for expertId " + expertId + ": " + isOnline + " (type: " + (isOnline != null ? isOnline.getClass().getSimpleName() : "null") + ")");
        
        if (isOnline == null || !isOnline) {
            System.out.println("[WalletService] Expert is OFFLINE for expertId: " + expertId + " (isOnline: " + isOnline + ")");
            return "OFFLINE";
        }
        
        System.out.println("[WalletService] Expert is ONLINE for expertId: " + expertId);
        return "ONLINE";
    }

    /**
     * Get expert's online availability (user-controlled).
     *
     * @param expertId The expert's ID
     * @return true if expert is online, false otherwise (defaults to false)
     */
    public boolean getExpertOnlineStatus(String expertId) throws ExecutionException, InterruptedException {
        DocumentSnapshot storeDoc = db.collection("users").document(expertId)
                .collection("public").document("store").get().get();
        
        if (storeDoc.exists() && storeDoc.contains("is_online")) {
            Boolean isOnline = storeDoc.getBoolean("is_online");
            return isOnline != null && isOnline;
        }
        return false;
    }

    /**
     * Get expert's consultation status (system-controlled).
     *
     * @param expertId The expert's ID
     * @return "BUSY" if expert has active consultations, "FREE" otherwise (defaults to "FREE")
     */
    public String getConsultationStatus(String expertId) throws ExecutionException, InterruptedException {
        DocumentSnapshot storeDoc = db.collection("users").document(expertId)
                .collection("public").document("store").get().get();
        
        if (storeDoc.exists() && storeDoc.contains("consultation_status")) {
            String status = storeDoc.getString("consultation_status");
            return status != null ? status : "FREE";
        }
        return "FREE";
    }

    /**
     * Set expert's online availability (user-controlled).
     *
     * @param expertId The expert's ID
     * @param isOnline true for ONLINE, false for OFFLINE
     */
    public void setExpertOnlineStatus(String expertId, boolean isOnline) throws ExecutionException, InterruptedException {
        DocumentReference storeRef = db.collection("users").document(expertId)
                .collection("public").document("store");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("is_online", isOnline);
        updates.put("is_online_updated_at", Timestamp.now());
        
        storeRef.update(updates).get();
    }

    /**
     * Set consultation status atomically within a transaction (system-controlled).
     * Used to mark expert as BUSY when consultation starts, FREE when it ends.
     */
    public void setConsultationStatusInTransaction(Transaction transaction, String expertId, String status)
            throws ExecutionException, InterruptedException {
        DocumentReference storeRef = db.collection("users").document(expertId)
                .collection("public").document("store");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("consultation_status", status);
        updates.put("consultation_status_updated_at", Timestamp.now());
        
        transaction.update(storeRef, updates);
    }

    /**
     * Set consultation status (non-transactional) - system-controlled.
     */
    public void setConsultationStatus(String expertId, String status) throws ExecutionException, InterruptedException {
        DocumentReference storeRef = db.collection("users").document(expertId)
                .collection("public").document("store");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("consultation_status", status);
        updates.put("consultation_status_updated_at", Timestamp.now());
        
        storeRef.update(updates).get();
    }

    // =====================================================================
    // Expert-Specific Wallet Methods (Per-Expert Customer Wallets)
    // Path: users/{userId}/expert_wallets/{expertId}
    // =====================================================================

    /**
     * Get wallet balances for a user with a specific expert (all currencies).
     * Path: users/{userId}/expert_wallets/{expertId}
     *
     * @param userId   The user's ID
     * @param expertId The expert's ID
     * @return Map of currency codes to balances
     */
    public Map<String, Double> getExpertWalletBalances(String userId, String expertId) throws ExecutionException, InterruptedException {
        DocumentSnapshot walletDoc = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId).get().get();
        
        if (walletDoc.exists() && walletDoc.contains("balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> balances = (Map<String, Object>) walletDoc.get("balances");
            if (balances != null) {
                Map<String, Double> result = new HashMap<>();
                for (Map.Entry<String, Object> entry : balances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                return result;
            }
        }
        return new HashMap<>();
    }

    /**
     * Get wallet balance for a user with a specific expert in a specific currency.
     *
     * @param userId   The user's ID
     * @param expertId The expert's ID
     * @param currency The currency code (e.g., "INR", "USD")
     * @return The balance in the specified currency (0.0 if not found)
     */
    public Double getExpertWalletBalance(String userId, String expertId, String currency) throws ExecutionException, InterruptedException {
        Map<String, Double> balances = getExpertWalletBalances(userId, expertId);
        return balances.getOrDefault(currency, 0.0);
    }

    /**
     * Update expert-specific wallet balance using a Firestore transaction.
     * Path: users/{userId}/expert_wallets/{expertId}
     *
     * @param transaction The Firestore transaction
     * @param userId      The user's ID
     * @param expertId    The expert's ID
     * @param currency    The currency code
     * @param amount      The amount to add (positive) or subtract (negative)
     * @return The new balance after the update
     */
    public Double updateExpertWalletBalanceInTransaction(Transaction transaction, String userId, String expertId, String currency, Double amount)
            throws ExecutionException, InterruptedException {
        DocumentReference walletRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId);
        DocumentSnapshot walletDoc = transaction.get(walletRef).get();
        
        Map<String, Double> balances = new HashMap<>();
        if (walletDoc.exists() && walletDoc.contains("balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingBalances = (Map<String, Object>) walletDoc.get("balances");
            if (existingBalances != null) {
                for (Map.Entry<String, Object> entry : existingBalances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        balances.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        Double currentBalance = balances.getOrDefault(currency, 0.0);
        Double newBalance = currentBalance + amount;
        balances.put(currency, newBalance);
        
        Map<String, Object> data = new HashMap<>();
        data.put("balances", balances);
        data.put("updated_at", Timestamp.now());
        
        if (walletDoc.exists()) {
            transaction.update(walletRef, data);
        } else {
            data.put("created_at", Timestamp.now());
            transaction.set(walletRef, data);
        }
        
        return newBalance;
    }

    /**
     * Create a wallet transaction for expert-specific wallet within a Firestore transaction.
     * Path: users/{userId}/expert_wallets/{expertId}/transactions/{txId}
     *
     * @param firestoreTransaction The Firestore transaction
     * @param userId               The user's ID
     * @param expertId             The expert's ID
     * @param walletTransaction    The wallet transaction to create
     * @return The transaction ID
     */
    public String createExpertWalletTransactionInTransaction(Transaction firestoreTransaction, String userId, String expertId, WalletTransaction walletTransaction)
            throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId).collection("transactions");
        DocumentReference docRef = transactionsRef.document();
        
        Map<String, Object> transactionData = buildWalletTransactionData(walletTransaction);
        
        firestoreTransaction.set(docRef, transactionData);
        return docRef.getId();
    }

    /**
     * Create a wallet transaction for expert-specific wallet (non-transactional).
     * Path: users/{userId}/expert_wallets/{expertId}/transactions/{txId}
     *
     * @param userId            The user's ID
     * @param expertId          The expert's ID
     * @param walletTransaction The wallet transaction to create
     * @return The transaction ID
     */
    public String createExpertWalletTransaction(String userId, String expertId, WalletTransaction walletTransaction)
            throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId).collection("transactions");
        DocumentReference docRef = transactionsRef.document();
        
        Map<String, Object> transactionData = buildWalletTransactionData(walletTransaction);
        
        docRef.set(transactionData).get();
        return docRef.getId();
    }

    /**
     * Check if a payment has already been processed for expert-specific wallet.
     *
     * @param userId    The user's ID
     * @param expertId  The expert's ID
     * @param paymentId The Razorpay payment ID
     * @return true if payment already processed
     */
    public boolean isExpertWalletPaymentAlreadyProcessed(String userId, String expertId, String paymentId) throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId).collection("transactions");
        Query query = transactionsRef.whereEqualTo("payment_id", paymentId).whereEqualTo("status", "COMPLETED");
        return !query.get().get().isEmpty();
    }

    /**
     * Increment expert-specific wallet balance (non-transactional, use with caution).
     *
     * @param userId   The user's ID
     * @param expertId The expert's ID
     * @param currency The currency code
     * @param amount   The amount to add
     * @return The new balance
     */
    public Double incrementExpertWalletBalance(String userId, String expertId, String currency, Double amount)
            throws ExecutionException, InterruptedException {
        DocumentReference walletRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId);
        
        // Use FieldValue.increment for atomic updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("balances." + currency, FieldValue.increment(amount));
        updates.put("updated_at", Timestamp.now());
        
        // Check if document exists first
        DocumentSnapshot walletDoc = walletRef.get().get();
        if (walletDoc.exists()) {
            walletRef.update(updates).get();
        } else {
            // Create new document with initial balance
            Map<String, Object> data = new HashMap<>();
            Map<String, Double> balances = new HashMap<>();
            balances.put(currency, amount);
            data.put("balances", balances);
            data.put("created_at", Timestamp.now());
            data.put("updated_at", Timestamp.now());
            walletRef.set(data).get();
        }
        
        return getExpertWalletBalance(userId, expertId, currency);
    }

    /**
     * Helper method to build wallet transaction data map.
     */
    private Map<String, Object> buildWalletTransactionData(WalletTransaction walletTransaction) {
        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("type", walletTransaction.getType());
        transactionData.put("source", walletTransaction.getSource());
        transactionData.put("amount", walletTransaction.getAmount());
        transactionData.put("currency", walletTransaction.getCurrency());
        transactionData.put("status", walletTransaction.getStatus());
        transactionData.put("created_at", walletTransaction.getCreatedAt() != null ? walletTransaction.getCreatedAt() : Timestamp.now());
        transactionData.put("description", walletTransaction.getDescription());
        
        // Add optional fields if present
        if (walletTransaction.getPaymentId() != null) {
            transactionData.put("payment_id", walletTransaction.getPaymentId());
        }
        if (walletTransaction.getOrderId() != null) {
            transactionData.put("order_id", walletTransaction.getOrderId());
        }
        if (walletTransaction.getCouponCode() != null) {
            transactionData.put("coupon_code", walletTransaction.getCouponCode());
        }
        if (walletTransaction.getReferralCode() != null) {
            transactionData.put("referral_code", walletTransaction.getReferralCode());
        }
        if (walletTransaction.getReferrerId() != null) {
            transactionData.put("referrer_id", walletTransaction.getReferrerId());
        }
        if (walletTransaction.getCashbackSourceOrderId() != null) {
            transactionData.put("cashback_source_order_id", walletTransaction.getCashbackSourceOrderId());
        }
        if (walletTransaction.getCashbackReason() != null) {
            transactionData.put("cashback_reason", walletTransaction.getCashbackReason());
        }
        if (walletTransaction.getRefundReason() != null) {
            transactionData.put("refund_reason", walletTransaction.getRefundReason());
        }
        if (walletTransaction.getBonusAmount() != null) {
            transactionData.put("bonus_amount", walletTransaction.getBonusAmount());
        }
        
        return transactionData;
    }

}
