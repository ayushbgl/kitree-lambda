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
            return currency != null ? currency : "INR";
        }
        return "INR";
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
        // Try to get from private collection first
        DocumentSnapshot privateDoc = db.collection("users").document(expertId)
                .collection("private").document("platform_fee_config").get().get();
        
        if (privateDoc.exists()) {
            return docToPlatformFeeConfig(privateDoc);
        }
        
        // Try to get from public store
        DocumentSnapshot storeDoc = db.collection("users").document(expertId)
                .collection("public").document("store").get().get();
        
        if (storeDoc.exists() && storeDoc.contains("platform_fee_config")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) storeDoc.get("platform_fee_config");
            return mapToPlatformFeeConfig(configMap);
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
     * Get expert status (ONLINE, OFFLINE, BUSY).
     *
     * @param expertId The expert's ID
     * @return The expert status (defaults to "OFFLINE")
     */
    public String getExpertStatus(String expertId) throws ExecutionException, InterruptedException {
        DocumentSnapshot storeDoc = db.collection("users").document(expertId)
                .collection("public").document("store").get().get();
        
        if (storeDoc.exists() && storeDoc.contains("expert_status")) {
            String status = storeDoc.getString("expert_status");
            return status != null ? status : "OFFLINE";
        }
        return "OFFLINE";
    }

    /**
     * Set expert status atomically within a transaction.
     */
    public void setExpertStatusInTransaction(Transaction transaction, String expertId, String status)
            throws ExecutionException, InterruptedException {
        DocumentReference storeRef = db.collection("users").document(expertId)
                .collection("public").document("store");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("expert_status", status);
        updates.put("expert_status_updated_at", Timestamp.now());
        
        transaction.update(storeRef, updates);
    }

    /**
     * Set expert status (non-transactional).
     */
    public void setExpertStatus(String expertId, String status) throws ExecutionException, InterruptedException {
        DocumentReference storeRef = db.collection("users").document(expertId)
                .collection("public").document("store");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("expert_status", status);
        updates.put("expert_status_updated_at", Timestamp.now());
        
        storeRef.update(updates).get();
    }
}
