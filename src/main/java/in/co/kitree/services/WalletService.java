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
            LoggingService.debug("wallet_expert_store_not_found", Map.of("expertId", expertId));
            return "OFFLINE";
        }

        // Check consultation_status first (system-controlled: BUSY/FREE)
        String consultationStatus = storeDoc.getString("consultation_status");
        if ("BUSY".equals(consultationStatus)) {
            LoggingService.debug("wallet_expert_busy", Map.of("expertId", expertId));
            return "BUSY";
        }

        // Then check is_online (user-controlled: true/false)
        Boolean isOnline = storeDoc.getBoolean("is_online");
        LoggingService.debug("wallet_expert_online_status", Map.of("expertId", expertId, "isOnline", String.valueOf(isOnline)));

        if (isOnline == null || !isOnline) {
            LoggingService.debug("wallet_expert_offline", Map.of("expertId", expertId));
            return "OFFLINE";
        }

        LoggingService.debug("wallet_expert_online", Map.of("expertId", expertId));
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
     * Update expert wallet balance within a Firestore transaction using a pre-read snapshot.
     * This version avoids reading the document again to prevent "reads before writes" errors.
     * Path: users/{userId}/expert_wallets/{expertId}
     *
     * @param transaction The Firestore transaction
     * @param walletRef   The pre-created wallet document reference
     * @param walletDoc   The pre-read wallet document snapshot (can be non-existent)
     * @param currency    The currency code
     * @param amount      The amount to add (positive) or subtract (negative)
     * @return The new balance after the update
     */
    public Double updateExpertWalletBalanceInTransactionWithSnapshot(
            Transaction transaction, 
            DocumentReference walletRef, 
            DocumentSnapshot walletDoc, 
            String currency, 
            Double amount
    ) {
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

        // Add optional fields if present
        if (walletTransaction.getPaymentId() != null) {
            transactionData.put("payment_id", walletTransaction.getPaymentId());
        }
        if (walletTransaction.getGatewayOrderId() != null) {
            transactionData.put("gateway_order_id", walletTransaction.getGatewayOrderId());
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
        
        // Consultation-specific normalized fields (for CONSULTATION_DEDUCTION type)
        if (walletTransaction.getDurationSeconds() != null) {
            transactionData.put("duration_seconds", walletTransaction.getDurationSeconds());
        }
        if (walletTransaction.getRatePerMinute() != null) {
            transactionData.put("rate_per_minute", walletTransaction.getRatePerMinute());
        }
        if (walletTransaction.getConsultationType() != null) {
            transactionData.put("consultation_type", walletTransaction.getConsultationType());
        }
        if (walletTransaction.getCategory() != null) {
            transactionData.put("category", walletTransaction.getCategory());
        }
        
        return transactionData;
    }

    /**
     * Find a pending recharge transaction by Razorpay order ID.
     * Path: users/{userId}/expert_wallets/{expertId}/transactions
     *
     * @param userId          The user's ID
     * @param expertId        The expert's ID
     * @param gatewayOrderId  The payment gateway order ID
     * @return Map containing transaction data and document ID, or null if not found
     */
    public Map<String, Object> findPendingRechargeByOrderId(String userId, String expertId, String gatewayOrderId)
            throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId).collection("transactions");

        Query query = transactionsRef
                .whereEqualTo("gateway_order_id", gatewayOrderId)
                .whereEqualTo("status", "PENDING")
                .limit(1);

        QuerySnapshot snapshot = query.get().get();
        if (snapshot.isEmpty()) {
            return null;
        }

        QueryDocumentSnapshot doc = snapshot.getDocuments().get(0);
        Map<String, Object> result = new HashMap<>(doc.getData());
        result.put("_id", doc.getId());
        result.put("_ref", doc.getReference());
        return result;
    }

    /**
     * Complete a recharge transaction - update status to COMPLETED and credit balance.
     * This is done atomically in a transaction.
     *
     * @param userId          The user's ID
     * @param expertId        The expert's ID
     * @param transactionId   The transaction document ID
     * @param paymentId       The Razorpay payment ID
     * @param amount          The recharge amount
     * @param bonus           The bonus amount
     * @param currency        The currency code
     * @return The new wallet balance
     */
    public Double completeRechargeTransaction(String userId, String expertId, String transactionId,
                                               String paymentId, Double amount, Double bonus, String currency)
            throws ExecutionException, InterruptedException {
        Double totalCredit = amount + (bonus != null ? bonus : 0.0);
        Double[] newBalanceHolder = new Double[1];
        final Double finalBonus = bonus != null ? bonus : 0.0;

        db.runTransaction(transaction -> {
            // Read wallet document
            DocumentReference walletRef = db.collection("users").document(userId)
                    .collection("expert_wallets").document(expertId);
            DocumentSnapshot walletDoc = transaction.get(walletRef).get();

            // Read transaction document
            DocumentReference txRef = db.collection("users").document(userId)
                    .collection("expert_wallets").document(expertId)
                    .collection("transactions").document(transactionId);
            DocumentSnapshot txDoc = transaction.get(txRef).get();

            // Verify transaction is still PENDING
            if (!txDoc.exists() || !"PENDING".equals(txDoc.getString("status"))) {
                throw new IllegalStateException("Transaction is not pending or does not exist");
            }

            // Update wallet balance with total (amount + bonus)
            newBalanceHolder[0] = updateExpertWalletBalanceInTransactionWithSnapshot(
                    transaction, walletRef, walletDoc, currency, totalCredit
            );

            // Update recharge transaction status (amount only, bonus tracked separately)
            Map<String, Object> txUpdates = new HashMap<>();
            txUpdates.put("status", "COMPLETED");
            txUpdates.put("payment_id", paymentId);
            txUpdates.put("completed_at", Timestamp.now());
            transaction.update(txRef, txUpdates);

            // Create separate BONUS transaction if bonus > 0
            if (finalBonus > 0) {
                CollectionReference transactionsRef = db.collection("users").document(userId)
                        .collection("expert_wallets").document(expertId)
                        .collection("transactions");
                DocumentReference bonusTxRef = transactionsRef.document();

                Map<String, Object> bonusTx = new HashMap<>();
                bonusTx.put("type", "BONUS");
                bonusTx.put("source", "PROMOTION");
                bonusTx.put("amount", finalBonus);
                bonusTx.put("currency", currency);
                bonusTx.put("status", "COMPLETED");
                bonusTx.put("created_at", Timestamp.now());
                bonusTx.put("completed_at", Timestamp.now());
                bonusTx.put("related_recharge_tx_id", transactionId);
                bonusTx.put("razorpay_order_id", txDoc.getString("razorpay_order_id"));

                transaction.set(bonusTxRef, bonusTx);
            }

            return null;
        }).get();

        return newBalanceHolder[0];
    }

    /**
     * Check if a recharge transaction already exists and is completed for the given Razorpay order.
     *
     * @param userId          The user's ID
     * @param expertId        The expert's ID
     * @param gatewayOrderId  The payment gateway order ID
     * @return true if a COMPLETED transaction exists for this order
     */
    public boolean isRechargeOrderAlreadyCompleted(String userId, String expertId, String gatewayOrderId)
            throws ExecutionException, InterruptedException {
        CollectionReference transactionsRef = db.collection("users").document(userId)
                .collection("expert_wallets").document(expertId).collection("transactions");

        Query query = transactionsRef
                .whereEqualTo("gateway_order_id", gatewayOrderId)
                .whereEqualTo("status", "COMPLETED")
                .limit(1);

        return !query.get().get().isEmpty();
    }

}
