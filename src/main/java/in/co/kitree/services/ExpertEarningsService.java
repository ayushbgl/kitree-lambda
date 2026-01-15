package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for handling expert earnings operations.
 * This is separate from the wallet - earnings are credited here when consultations complete.
 * Path: users/{expertId}/expert_earnings/{transactionId}
 * 
 * The expert earnings balance is stored on the expert's user document:
 * users/{expertId}.expert_earnings_balances: { "INR": 450, ... }
 */
public class ExpertEarningsService {
    private static final String DEFAULT_CURRENCY = "INR";
    
    private final Firestore db;

    public ExpertEarningsService(Firestore db) {
        this.db = db;
    }

    /**
     * Get expert earnings balances (all currencies).
     * Path: users/{expertId}.expert_earnings_balances
     *
     * @param expertId The expert's ID
     * @return Map of currency codes to earnings balances
     */
    public Map<String, Double> getExpertEarningsBalances(String expertId) throws ExecutionException, InterruptedException {
        DocumentSnapshot userDoc = db.collection("users").document(expertId).get().get();
        if (userDoc.exists() && userDoc.contains("expert_earnings_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> balances = (Map<String, Object>) userDoc.get("expert_earnings_balances");
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
     * Get expert earnings balance in a specific currency.
     *
     * @param expertId The expert's ID
     * @param currency The currency code (e.g., "INR", "USD")
     * @return The earnings balance in the specified currency (0.0 if not found)
     */
    public Double getExpertEarningsBalance(String expertId, String currency) throws ExecutionException, InterruptedException {
        Map<String, Double> balances = getExpertEarningsBalances(expertId);
        return balances.getOrDefault(currency, 0.0);
    }

    /**
     * Credit expert earnings using a Firestore transaction.
     * This is called when a consultation ends and the expert should receive their earnings.
     *
     * @param transaction   The Firestore transaction
     * @param expertId      The expert's ID
     * @param currency      The currency code
     * @param amount        The earnings amount (gross, before platform fee)
     * @param platformFee   The platform fee deducted
     * @param orderId       The order ID for this earning
     * @param description   Description of the earning
     * @return The new earnings balance after the update
     */
    public Double creditExpertEarningsInTransaction(
            Transaction transaction, 
            String expertId, 
            String currency, 
            Double amount,
            Double platformFee,
            String orderId,
            String description
    ) throws ExecutionException, InterruptedException {
        // Calculate net earnings
        Double netEarnings = amount - platformFee;
        
        // Update earnings balance
        DocumentReference userRef = db.collection("users").document(expertId);
        DocumentSnapshot userDoc = transaction.get(userRef).get();
        
        Map<String, Double> balances = new HashMap<>();
        if (userDoc.exists() && userDoc.contains("expert_earnings_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingBalances = (Map<String, Object>) userDoc.get("expert_earnings_balances");
            if (existingBalances != null) {
                for (Map.Entry<String, Object> entry : existingBalances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        balances.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        Double currentBalance = balances.getOrDefault(currency, 0.0);
        Double newBalance = currentBalance + netEarnings;
        balances.put(currency, newBalance);
        
        transaction.update(userRef, "expert_earnings_balances", balances);
        
        // Create earnings transaction record
        CollectionReference earningsRef = db.collection("users").document(expertId).collection("expert_earnings");
        DocumentReference earningDocRef = earningsRef.document();
        
        Map<String, Object> earningData = new HashMap<>();
        earningData.put("type", "ORDER_EARNING");
        earningData.put("gross_amount", amount);
        earningData.put("platform_fee", platformFee);
        earningData.put("amount", netEarnings);  // Net amount credited
        earningData.put("currency", currency);
        earningData.put("order_id", orderId);
        earningData.put("status", "COMPLETED");
        earningData.put("created_at", Timestamp.now());
        earningData.put("description", description);
        
        transaction.set(earningDocRef, earningData);
        
        return newBalance;
    }

    /**
     * Credit expert earnings using a Firestore transaction with a pre-read snapshot.
     * This version avoids reading the document again to prevent "reads before writes" errors.
     *
     * @param transaction   The Firestore transaction
     * @param expertId      The expert's ID
     * @param userRef       The pre-created user document reference
     * @param userDoc       The pre-read user document snapshot
     * @param currency      The currency code
     * @param amount        The earnings amount (gross, before platform fee)
     * @param platformFee   The platform fee deducted
     * @param orderId       The order ID for this earning
     * @param description   Description of the earning
     * @return The new earnings balance after the update
     */
    public Double creditExpertEarningsInTransactionWithSnapshot(
            Transaction transaction,
            String expertId,
            DocumentReference userRef,
            DocumentSnapshot userDoc,
            String currency, 
            Double amount,
            Double platformFee,
            String orderId,
            String description
    ) {
        // Calculate net earnings
        Double netEarnings = amount - platformFee;
        
        // Get existing balances from pre-read snapshot
        Map<String, Double> balances = new HashMap<>();
        if (userDoc.exists() && userDoc.contains("expert_earnings_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingBalances = (Map<String, Object>) userDoc.get("expert_earnings_balances");
            if (existingBalances != null) {
                for (Map.Entry<String, Object> entry : existingBalances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        balances.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        Double currentBalance = balances.getOrDefault(currency, 0.0);
        Double newBalance = currentBalance + netEarnings;
        balances.put(currency, newBalance);
        
        transaction.update(userRef, "expert_earnings_balances", balances);
        
        // Create earnings transaction record
        CollectionReference earningsRef = db.collection("users").document(expertId).collection("expert_earnings");
        DocumentReference earningDocRef = earningsRef.document();
        
        Map<String, Object> earningData = new HashMap<>();
        earningData.put("type", "ORDER_EARNING");
        earningData.put("gross_amount", amount);
        earningData.put("platform_fee", platformFee);
        earningData.put("amount", netEarnings);  // Net amount credited
        earningData.put("currency", currency);
        earningData.put("order_id", orderId);
        earningData.put("status", "COMPLETED");
        earningData.put("created_at", Timestamp.now());
        earningData.put("description", description);
        
        transaction.set(earningDocRef, earningData);
        
        return newBalance;
    }

    /**
     * Credit expert earnings (non-transactional, use with caution).
     *
     * @param expertId    The expert's ID
     * @param currency    The currency code
     * @param amount      The earnings amount (gross, before platform fee)
     * @param platformFee The platform fee deducted
     * @param orderId     The order ID for this earning
     * @param description Description of the earning
     * @return The new earnings balance
     */
    public Double creditExpertEarnings(
            String expertId, 
            String currency, 
            Double amount,
            Double platformFee,
            String orderId,
            String description
    ) throws ExecutionException, InterruptedException {
        Double netEarnings = amount - platformFee;
        
        DocumentReference userRef = db.collection("users").document(expertId);
        
        // Use FieldValue.increment for atomic updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("expert_earnings_balances." + currency, FieldValue.increment(netEarnings));
        userRef.update(updates).get();
        
        // Create earnings transaction record
        CollectionReference earningsRef = db.collection("users").document(expertId).collection("expert_earnings");
        DocumentReference earningDocRef = earningsRef.document();
        
        Map<String, Object> earningData = new HashMap<>();
        earningData.put("type", "ORDER_EARNING");
        earningData.put("gross_amount", amount);
        earningData.put("platform_fee", platformFee);
        earningData.put("amount", netEarnings);
        earningData.put("currency", currency);
        earningData.put("order_id", orderId);
        earningData.put("status", "COMPLETED");
        earningData.put("created_at", Timestamp.now());
        earningData.put("description", description);
        
        earningDocRef.set(earningData).get();
        
        return getExpertEarningsBalance(expertId, currency);
    }

    /**
     * Record a payout to the expert (reduces earnings balance).
     *
     * @param transaction   The Firestore transaction
     * @param expertId      The expert's ID
     * @param currency      The currency code
     * @param payoutAmount  The payout amount
     * @param payoutMethod  The payout method (e.g., "BANK_TRANSFER", "UPI")
     * @param payoutRef     The payout reference ID
     * @return The new earnings balance after payout
     */
    public Double recordPayoutInTransaction(
            Transaction transaction,
            String expertId,
            String currency,
            Double payoutAmount,
            String payoutMethod,
            String payoutRef
    ) throws ExecutionException, InterruptedException {
        // Update earnings balance (deduct payout amount)
        DocumentReference userRef = db.collection("users").document(expertId);
        DocumentSnapshot userDoc = transaction.get(userRef).get();
        
        Map<String, Double> balances = new HashMap<>();
        if (userDoc.exists() && userDoc.contains("expert_earnings_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingBalances = (Map<String, Object>) userDoc.get("expert_earnings_balances");
            if (existingBalances != null) {
                for (Map.Entry<String, Object> entry : existingBalances.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        balances.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        Double currentBalance = balances.getOrDefault(currency, 0.0);
        if (currentBalance < payoutAmount) {
            throw new IllegalArgumentException("Insufficient earnings balance for payout");
        }
        
        Double newBalance = currentBalance - payoutAmount;
        balances.put(currency, newBalance);
        
        transaction.update(userRef, "expert_earnings_balances", balances);
        
        // Create payout transaction record
        CollectionReference earningsRef = db.collection("users").document(expertId).collection("expert_earnings");
        DocumentReference earningDocRef = earningsRef.document();
        
        Map<String, Object> earningData = new HashMap<>();
        earningData.put("type", "PAYOUT");
        earningData.put("amount", -payoutAmount);  // Negative for payout
        earningData.put("currency", currency);
        earningData.put("payout_method", payoutMethod);
        earningData.put("payout_ref", payoutRef);
        earningData.put("status", "COMPLETED");
        earningData.put("created_at", Timestamp.now());
        earningData.put("description", "Payout via " + payoutMethod);
        
        transaction.set(earningDocRef, earningData);
        
        return newBalance;
    }

    /**
     * Get the default currency.
     */
    public static String getDefaultCurrency() {
        return DEFAULT_CURRENCY;
    }
}
