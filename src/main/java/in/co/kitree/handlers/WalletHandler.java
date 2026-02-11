package in.co.kitree.handlers;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.RequestBody;
import in.co.kitree.services.LoggingService;
import in.co.kitree.services.Razorpay;
import in.co.kitree.services.WalletService;
import in.co.kitree.pojos.WalletTransaction;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Handler for wallet-related operations.
 * Manages wallet balance queries, recharge orders, and payment deductions.
 */
public class WalletHandler {

    // Constants
    private static final double MIN_WALLET_RECHARGE_AMOUNT = 100.0;

    private final Firestore db;
    private final Razorpay razorpay;
    private final Gson gson;

    public WalletHandler(Firestore db, Razorpay razorpay) {
        this.db = db;
        this.razorpay = razorpay;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Get wallet balance for a user with a specific expert.
     * Requires expertId - wallets are per-expert.
     */
    private String handleWalletBalance(String userId, RequestBody requestBody) throws ExecutionException, InterruptedException {
        WalletService walletService = new WalletService(this.db);
        String currency = requestBody.getCurrency();
        String expertId = requestBody.getExpertId();

        if (expertId == null || expertId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
        }

        if (currency != null && !currency.isEmpty()) {
            // Return specific currency balance
            Double balance = walletService.getExpertWalletBalance(userId, expertId, currency);
            return gson.toJson(Map.of(
                "success", true,
                "balance", balance,
                "currency", currency,
                "expertId", expertId
            ));
        } else {
            // Return all currency balances
            Map<String, Double> balances = walletService.getExpertWalletBalances(userId, expertId);
            String defaultCurrency = walletService.getUserDefaultCurrency(userId);
            return gson.toJson(Map.of(
                "success", true,
                "balances", balances,
                "defaultCurrency", defaultCurrency,
                "expertId", expertId
            ));
        }
    }

    /**
     * Create a Razorpay order for wallet recharge.
     * Step 1 of the two-step recharge process.
     *
     * Requires expertId to credit to expert-specific wallet.
     * Validates amount against expert's recharge_options from their on-demand plan.
     * Calculates bonus server-side to prevent fraud.
     */
    private String handleCreateWalletRechargeOrder(String userId, RequestBody requestBody) throws Exception {
        Double amount = requestBody.getAmount();
        String expertId = requestBody.getExpertId();

        // Expert ID is required for per-expert wallets
        if (expertId == null || expertId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Expert ID is required"));
        }

        if (amount == null || amount <= 0) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Invalid amount"));
        }

        // Get expert's on-demand plan to validate recharge options
        Double bonus = 0.0;
        String currency = requestBody.getCurrency();

        // Find the expert's on-demand consultation plan
        QuerySnapshot plansSnapshot = this.db.collection("users").document(expertId)
                .collection("plans")
                .whereEqualTo("type", "CONSULTATION")
                .whereEqualTo("consultationMode", "ON_DEMAND")
                .limit(1)
                .get().get();

        if (!plansSnapshot.isEmpty()) {
            DocumentSnapshot planDoc = plansSnapshot.getDocuments().get(0);

            // Get recharge_options from plan
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rechargeOptions = (List<Map<String, Object>>) planDoc.get("recharge_options");

            if (rechargeOptions != null && !rechargeOptions.isEmpty()) {
                // Validate that amount matches one of the configured options
                boolean validAmount = false;
                for (Map<String, Object> option : rechargeOptions) {
                    Number optionAmount = (Number) option.get("amount");
                    if (optionAmount != null && Math.abs(optionAmount.doubleValue() - amount) < 0.01) {
                        validAmount = true;
                        // Get bonus from server-side config (prevents fraud)
                        Number optionBonus = (Number) option.get("bonus");
                        if (optionBonus != null) {
                            bonus = optionBonus.doubleValue();
                        }
                        break;
                    }
                }

                if (!validAmount) {
                    return gson.toJson(Map.of(
                        "success", false,
                        "errorMessage", "Invalid recharge amount. Please select from available options."
                    ));
                }
            }

            // Use currency from plan if not specified
            if (currency == null || currency.isEmpty()) {
                String planCurrency = planDoc.getString("onDemandCurrency");
                currency = planCurrency != null ? planCurrency : WalletService.getDefaultCurrency();
            }
        } else {
            // No on-demand plan configured - use minimum validation
            if (amount < MIN_WALLET_RECHARGE_AMOUNT) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Minimum recharge amount is ₹" + MIN_WALLET_RECHARGE_AMOUNT));
            }

            WalletService walletService = new WalletService(this.db);
            if (currency == null || currency.isEmpty()) {
                currency = walletService.getUserDefaultCurrency(userId);
            }
        }

        // Create payment gateway order
        String gatewayOrderId = razorpay.createOrder(amount, in.co.kitree.services.CustomerCipher.encryptCaesarCipher(userId));

        // Create PENDING transaction directly in expert_wallets transactions
        // This shows immediately in passbook for better UX
        WalletService walletService = new WalletService(this.db);
        WalletTransaction pendingTransaction = new WalletTransaction();
        pendingTransaction.setType("RECHARGE");
        pendingTransaction.setSource("PAYMENT");
        pendingTransaction.setAmount(amount);
        pendingTransaction.setCurrency(currency);
        pendingTransaction.setStatus("PENDING");
        pendingTransaction.setGatewayOrderId(gatewayOrderId);
        pendingTransaction.setCreatedAt(com.google.cloud.Timestamp.now());
        if (bonus > 0) {
            pendingTransaction.setBonusAmount(bonus);
        }

        walletService.createExpertWalletTransaction(userId, expertId, pendingTransaction);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("gatewayOrderId", gatewayOrderId);
        response.put("gatewayKey", razorpay.getRazorpayKey());
        response.put("gatewayType", "RAZORPAY");
        response.put("amount", amount);
        response.put("bonus", bonus);
        response.put("currency", currency);
        response.put("expertId", expertId);

        return gson.toJson(response);
    }

    /**
     * Process pending wallet deduction when Razorpay payment is verified (for partial wallet payments).
     * This method is idempotent - it checks if already processed before deducting.
     *
     * This is called from Handler.java during payment verification, not as an API endpoint.
     */
    public void processWalletDeductionOnPaymentVerification(String userId, String orderId) {
        try {
            // Read the order document to check for pending wallet deduction
            DocumentSnapshot orderDoc = this.db.collection("users").document(userId)
                    .collection("orders").document(orderId).get().get();

            if (!orderDoc.exists()) {
                LoggingService.warn("wallet_deduction_order_not_found", Map.of("orderId", orderId));
                return;
            }

            // Check if there's a pending wallet deduction
            Double pendingWalletDeduction = orderDoc.getDouble("pending_wallet_deduction");
            if (pendingWalletDeduction == null || pendingWalletDeduction <= 0) {
                // No wallet deduction pending
                return;
            }

            // Check idempotency - if wallet_transaction_id exists, already processed
            if (orderDoc.getString("wallet_transaction_id") != null) {
                LoggingService.info("wallet_deduction_already_processed", Map.of("orderId", orderId));
                return;
            }

            String expertId = orderDoc.getString("expert_id");
            String currency = orderDoc.getString("currency");
            String category = orderDoc.getString("category");

            if (expertId == null || currency == null) {
                LoggingService.warn("wallet_deduction_missing_fields", Map.of("orderId", orderId, "hasExpertId", expertId != null, "hasCurrency", currency != null));
                return;
            }

            final Double finalWalletDeduction = pendingWalletDeduction;
            final String finalExpertId = expertId;
            final String finalCurrency = currency;
            final String finalCategory = category;

            WalletService walletService = new WalletService(this.db);

            // Execute atomically in a transaction
            this.db.runTransaction(transaction -> {
                // 1. Read wallet document
                DocumentReference walletRef = db.collection("users").document(userId)
                        .collection("expert_wallets").document(finalExpertId);
                DocumentSnapshot walletDoc = transaction.get(walletRef).get();

                // 2. Re-read order document to check idempotency within transaction
                DocumentReference orderRef = db.collection("users").document(userId)
                        .collection("orders").document(orderId);
                DocumentSnapshot orderDocTx = transaction.get(orderRef).get();

                if (orderDocTx.getString("wallet_transaction_id") != null) {
                    // Already processed, skip
                    return null;
                }

                // 3. Get current wallet balance
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
                Double currentBalance = balances.getOrDefault(finalCurrency, 0.0);

                // If insufficient balance, deduct what's available (graceful degradation)
                Double actualDeduction = Math.min(currentBalance, finalWalletDeduction);

                if (actualDeduction > 0) {
                    // 4. Deduct from wallet
                    walletService.updateExpertWalletBalanceInTransactionWithSnapshot(
                            transaction, walletRef, walletDoc, finalCurrency, -actualDeduction
                    );

                    // 5. Create wallet transaction record
                    WalletTransaction walletTx = new WalletTransaction();
                    walletTx.setType("ORDER_PAYMENT");
                    walletTx.setSource("WALLET");
                    walletTx.setAmount(-actualDeduction);
                    walletTx.setCurrency(finalCurrency);
                    walletTx.setOrderId(orderId);
                    walletTx.setStatus("COMPLETED");
                    walletTx.setCreatedAt(com.google.cloud.Timestamp.now());
                    if (finalCategory != null) {
                        walletTx.setCategory(finalCategory);
                    }

                    String txId = walletService.createExpertWalletTransactionInTransaction(
                            transaction, userId, finalExpertId, walletTx
                    );

                    // 6. Update order with wallet transaction reference
                    Map<String, Object> orderUpdates = new HashMap<>();
                    orderUpdates.put("wallet_transaction_id", txId);
                    orderUpdates.put("wallet_deduction", actualDeduction);
                    transaction.update(orderRef, orderUpdates);
                }

                // 7. Remove pending flag
                transaction.update(orderRef, "pending_wallet_deduction", FieldValue.delete());

                return null;
            }).get();

            LoggingService.info("wallet_deduction_processed", Map.of("orderId", orderId, "amount", finalWalletDeduction));

        } catch (Exception e) {
            // Log error but don't fail the payment verification
            LoggingService.error("wallet_deduction_failed", e, Map.of("orderId", orderId));
        }
    }
}
