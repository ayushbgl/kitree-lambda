package in.co.kitree.services;

import com.google.cloud.firestore.DocumentSnapshot;
import in.co.kitree.pojos.PayoutBreakdown;
import in.co.kitree.pojos.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Stateless service for computing expert payouts that are fair to the platform.
 *
 * <h2>Why this exists</h2>
 * Experts can offer wallet promotions (e.g. "pay ₹500 get ₹1000").
 * Payouts must be based on <em>real money the platform actually received</em>, not the
 * face-value wallet balance deducted.  Likewise, coupon discounts reduce the real amount
 * received, so commission must be on the post-coupon total.
 *
 * <h2>Core formula</h2>
 * <pre>
 *   effectiveRealAmount = gatewayAmount + walletDeduction × realRatio
 *   platformFee         = round(effectiveRealAmount × platformFeePercent / 100)
 *   expertEarnings      = effectiveRealAmount − platformFee
 * </pre>
 *
 * <p>All methods are static — instantiation is not needed.</p>
 */
public final class PayoutCalculationService {

    private PayoutCalculationService() {}

    // -------------------------------------------------------------------------
    // Primary calculation
    // -------------------------------------------------------------------------

    /**
     * Calculate payout breakdown for any charge (wallet-only, gateway-only, or mixed).
     *
     * @param gatewayAmount      Amount charged via payment gateway (always real money). Use 0 for
     *                           pure wallet charges.
     * @param walletDeduction    Face-value wallet balance deducted. Use 0 for gateway-only charges.
     * @param realRatio          Fraction of wallet balance that is real cash, in [0, 1].
     *                           Obtain from {@link #extractRealRatio}.
     * @param platformFeePercent Platform commission percentage (e.g. 10.0 for 10%).
     * @return Immutable {@link PayoutBreakdown} with all computed fields.
     */
    public static PayoutBreakdown calculate(double gatewayAmount, double walletDeduction,
                                             double realRatio, double platformFeePercent) {
        double clampedRatio = clamp(realRatio, 0.0, 1.0);
        double effectiveReal = gatewayAmount + walletDeduction * clampedRatio;
        double fee = roundMoney(effectiveReal * platformFeePercent / 100.0);
        double earnings = roundMoney(effectiveReal - fee);
        return PayoutBreakdown.of(
                roundMoney(gatewayAmount),
                roundMoney(walletDeduction),
                clampedRatio,
                roundMoney(effectiveReal),
                platformFeePercent,
                fee,
                earnings);
    }

    // -------------------------------------------------------------------------
    // realRatio extraction
    // -------------------------------------------------------------------------

    /**
     * Extract the real-money ratio from a Firestore wallet document.
     *
     * <p>Handles absent {@code real_balances} field (returns 1.0 — no retroactive penalty on
     * existing wallets) and division by zero (returns 1.0).</p>
     *
     * @param walletDoc  Snapshot of {@code users/{userId}/expert_wallets/{expertId}}.
     * @param currency   Currency code (e.g. "INR").
     * @return ratio in [0, 1].
     */
    public static double extractRealRatio(DocumentSnapshot walletDoc, String currency) {
        if (walletDoc == null || !walletDoc.exists()) {
            return 1.0;
        }

        Double totalBalance = null;
        Double realBalance = null;

        // Read balances map
        if (walletDoc.contains("balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> balances = (Map<String, Object>) walletDoc.get("balances");
            if (balances != null && balances.get(currency) instanceof Number) {
                totalBalance = ((Number) balances.get(currency)).doubleValue();
            }
        }

        // Read real_balances map
        if (walletDoc.contains("real_balances")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> realBalances = (Map<String, Object>) walletDoc.get("real_balances");
            if (realBalances != null && realBalances.get(currency) instanceof Number) {
                realBalance = ((Number) realBalances.get(currency)).doubleValue();
            }
        }

        // real_balances field absent → legacy wallet, treat as fully real (no penalty)
        if (realBalance == null) {
            return 1.0;
        }

        return extractRealRatio(totalBalance != null ? totalBalance : 0.0, realBalance);
    }

    /**
     * Overload for testing and non-Firestore contexts.
     *
     * @param totalBalance Total wallet balance (face value).
     * @param realBalance  Real cash portion (may be null if field was absent → returns 1.0).
     * @return ratio in [0, 1].
     */
    public static double extractRealRatio(double totalBalance, Double realBalance) {
        if (realBalance == null) {
            return 1.0; // legacy wallet — no penalty
        }
        if (totalBalance <= 0) {
            return 1.0; // division by zero guard; wallet is empty so ratio is irrelevant
        }
        return clamp(realBalance / totalBalance, 0.0, 1.0);
    }

    // -------------------------------------------------------------------------
    // real_balances credit / debit helpers
    // -------------------------------------------------------------------------

    /**
     * Compute how much to credit {@code real_balances} for a given transaction type.
     *
     * <ul>
     *   <li>RECHARGE — full credit amount is real cash.</li>
     *   <li>REFUND — refunded amount is treated as real cash (restores real ratio).</li>
     *   <li>All others (BONUS, CASHBACK, REFERRAL_BONUS, …) — 0, not real cash.</li>
     * </ul>
     *
     * @param type         The wallet transaction type.
     * @param creditAmount The credit amount (must be ≥ 0).
     * @return Amount to add to {@code real_balances}.
     */
    public static double computeRealBalanceCredit(TransactionType type, double creditAmount) {
        if (type == TransactionType.RECHARGE || type == TransactionType.REFUND) {
            return Math.max(0.0, creditAmount);
        }
        return 0.0;
    }

    /**
     * Compute the new {@code real_balances} value after a wallet debit.
     *
     * <p>The real balance is reduced proportionally to the debit: the same fraction that the
     * debit represents in the total balance is removed from real_balances. The result is
     * clamped to [0, newTotalBalance] to guard against floating-point drift.</p>
     *
     * @param currentBalance     Current total wallet balance (before debit). Must be &gt; 0.
     * @param currentRealBalance Current real_balances value (≥ 0).
     * @param debitAmount        Amount being debited (≥ 0).
     * @return New real_balances value after the debit.
     */
    public static double computeRealBalanceAfterDebit(double currentBalance,
                                                        double currentRealBalance,
                                                        double debitAmount) {
        if (currentBalance <= 0 || debitAmount <= 0) {
            return Math.max(0.0, currentRealBalance);
        }
        double ratio = clamp(currentRealBalance / currentBalance, 0.0, 1.0);
        double realDeducted = debitAmount * ratio;
        double newReal = currentRealBalance - realDeducted;
        double newTotal = currentBalance - debitAmount;
        // Clamp: real ≥ 0 and real ≤ newTotal (floating-point may overshoot slightly)
        return clamp(newReal, 0.0, Math.max(0.0, newTotal));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Round a monetary amount to 2 decimal places (HALF_UP).
     */
    public static double roundMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
