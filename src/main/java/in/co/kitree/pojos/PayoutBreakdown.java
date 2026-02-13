package in.co.kitree.pojos;

/**
 * Immutable result of a payout calculation.
 *
 * <p>Use {@link #of} to construct. All monetary fields are rounded to 2 decimal places.</p>
 */
public final class PayoutBreakdown {

    /** Gateway (cash) component of the charge. */
    public final double gatewayAmount;

    /** Wallet deduction (face-value) component of the charge. */
    public final double walletDeduction;

    /**
     * Fraction of wallet balance that is real cash, clamped to [0, 1].
     * 1.0 means the wallet is fully funded by real money; 0.0 means entirely bonus.
     */
    public final double realRatio;

    /**
     * Effective real-money amount: {@code gatewayAmount + walletDeduction * realRatio}.
     * This is the base for commission calculations.
     */
    public final double effectiveRealAmount;

    /** Platform commission percentage applied to {@link #effectiveRealAmount}. */
    public final double platformFeePercent;

    /** Platform fee in currency units: {@code round(effectiveRealAmount * platformFeePercent / 100)}. */
    public final double platformFee;

    /** Expert earnings: {@code effectiveRealAmount - platformFee}. */
    public final double expertEarnings;

    private PayoutBreakdown(double gatewayAmount, double walletDeduction, double realRatio,
                             double effectiveRealAmount, double platformFeePercent,
                             double platformFee, double expertEarnings) {
        this.gatewayAmount = gatewayAmount;
        this.walletDeduction = walletDeduction;
        this.realRatio = realRatio;
        this.effectiveRealAmount = effectiveRealAmount;
        this.platformFeePercent = platformFeePercent;
        this.platformFee = platformFee;
        this.expertEarnings = expertEarnings;
    }

    /** Package-private factory — callers should use {@link PayoutCalculationService#calculate}. */
    static PayoutBreakdown of(double gatewayAmount, double walletDeduction, double realRatio,
                               double effectiveRealAmount, double platformFeePercent,
                               double platformFee, double expertEarnings) {
        return new PayoutBreakdown(gatewayAmount, walletDeduction, realRatio,
                effectiveRealAmount, platformFeePercent, platformFee, expertEarnings);
    }

    @Override
    public String toString() {
        return String.format(
                "PayoutBreakdown{gateway=%.2f, wallet=%.2f, realRatio=%.4f, " +
                "effectiveReal=%.2f, feePercent=%.1f%%, platformFee=%.2f, expertEarnings=%.2f}",
                gatewayAmount, walletDeduction, realRatio,
                effectiveRealAmount, platformFeePercent, platformFee, expertEarnings);
    }
}
