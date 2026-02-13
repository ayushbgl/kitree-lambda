package in.co.kitree.services;

import in.co.kitree.pojos.PayoutBreakdown;
import in.co.kitree.pojos.TransactionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PayoutCalculationService}.
 * No Firebase / Firestore required — pure math only.
 *
 * Naming convention: test_<scenario>_<expectedBehaviour>
 */
public class PayoutCalculationServiceTest {

    private static final double DELTA = 0.001;

    // =========================================================================
    // calculate() — core payout formula
    // =========================================================================

    @Test
    public void test_allRealMoney_expertGets90Percent() {
        // User pays ₹1000 via gateway (all real), platform takes 10%
        PayoutBreakdown p = PayoutCalculationService.calculate(1000.0, 0.0, 1.0, 10.0);
        assertEquals(1000.00, p.effectiveRealAmount, DELTA);
        assertEquals(100.00, p.platformFee, DELTA);
        assertEquals(900.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_50PercentBonusWallet_expertGets45PercentOfFaceValue() {
        // User pays ₹500 real, gets ₹1000 wallet → realRatio = 0.5
        // User spends ₹1000 from wallet (face value)
        // effectiveReal = 0 + 1000 * 0.5 = 500 → expert gets 90% of 500 = 450
        PayoutBreakdown p = PayoutCalculationService.calculate(0.0, 1000.0, 0.5, 10.0);
        assertEquals(500.00, p.effectiveRealAmount, DELTA);
        assertEquals(50.00, p.platformFee, DELTA);
        assertEquals(450.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_allBonusWallet_expertEarnsZero() {
        // realRatio = 0 → no real money → expert earns ₹0
        PayoutBreakdown p = PayoutCalculationService.calculate(0.0, 500.0, 0.0, 10.0);
        assertEquals(0.00, p.effectiveRealAmount, DELTA);
        assertEquals(0.00, p.platformFee, DELTA);
        assertEquals(0.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_couponOnlyNoWalletBonus_payoutOnPostCouponAmount() {
        // Product priced ₹1000, user has 10% coupon, pays ₹900 via gateway.
        // realRatio = 1.0 (no wallet bonus involved).
        // effectiveReal = 900 → expert earns 90% of 900 = 810
        PayoutBreakdown p = PayoutCalculationService.calculate(900.0, 0.0, 1.0, 10.0);
        assertEquals(900.00, p.effectiveRealAmount, DELTA);
        assertEquals(90.00, p.platformFee, DELTA);
        assertEquals(810.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_couponPlusWalletBonus_compoundCorrectly() {
        // User's wallet: realRatio = 0.5 (₹500 real out of ₹1000 total)
        // Service costs ₹800 after coupon, paid fully from wallet.
        // effectiveReal = 800 * 0.5 = 400 → expert earns 360
        PayoutBreakdown p = PayoutCalculationService.calculate(0.0, 800.0, 0.5, 10.0);
        assertEquals(400.00, p.effectiveRealAmount, DELTA);
        assertEquals(40.00, p.platformFee, DELTA);
        assertEquals(360.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_mixedGatewayAndWallet_correctSplit() {
        // User pays ₹300 via gateway + ₹700 from wallet (realRatio = 0.4)
        // effectiveReal = 300 + 700*0.4 = 300 + 280 = 580 → expert earns 522
        PayoutBreakdown p = PayoutCalculationService.calculate(300.0, 700.0, 0.4, 10.0);
        assertEquals(580.00, p.effectiveRealAmount, DELTA);
        assertEquals(58.00, p.platformFee, DELTA);
        assertEquals(522.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_blendedRatioAfterTwoRecharges() {
        // Recharge 1: pays ₹200, gets ₹1000 → real=200, total=1000
        // Recharge 2: pays ₹1000, gets ₹15000 → real=1200, total=16000
        // realRatio = 1200/16000 = 0.075
        // User spends ₹3000 from wallet:
        //   effectiveReal = 3000 * 0.075 = 225
        //   platformFee = 22.50 → expertEarnings = 202.50
        double realRatio = PayoutCalculationService.extractRealRatio(16000.0, 1200.0);
        assertEquals(0.075, realRatio, DELTA);
        PayoutBreakdown p = PayoutCalculationService.calculate(0.0, 3000.0, realRatio, 10.0);
        assertEquals(225.00, p.effectiveRealAmount, DELTA);
        assertEquals(22.50, p.platformFee, DELTA);
        assertEquals(202.50, p.expertEarnings, DELTA);
    }

    @Test
    public void test_zeroCostAfterFullCoupon_earningsZero() {
        // 100% coupon discount → user pays ₹0
        PayoutBreakdown p = PayoutCalculationService.calculate(0.0, 0.0, 1.0, 10.0);
        assertEquals(0.00, p.effectiveRealAmount, DELTA);
        assertEquals(0.00, p.platformFee, DELTA);
        assertEquals(0.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_floatingPointRounding_correctTo2Decimals() {
        // ₹33.33 charge at 10% → fee = 3.333 → rounds to 3.33, earnings = 30.00
        PayoutBreakdown p = PayoutCalculationService.calculate(33.33, 0.0, 1.0, 10.0);
        assertEquals(3.33, p.platformFee, DELTA);
        assertEquals(30.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_realBalancesExceedsTotal_ratioClampedTo1() {
        // Defensive: real_balances somehow > balances (data inconsistency) → ratio = 1.0
        double ratio = PayoutCalculationService.extractRealRatio(500.0, 700.0);
        assertEquals(1.0, ratio, DELTA);
        PayoutBreakdown p = PayoutCalculationService.calculate(0.0, 500.0, ratio, 10.0);
        assertEquals(500.00, p.effectiveRealAmount, DELTA);
        assertEquals(450.00, p.expertEarnings, DELTA);
    }

    @Test
    public void test_nullRealBalances_ratioDefaultsTo1() {
        // Old wallet has no real_balances field → ratio = 1.0 (backward compat)
        double ratio = PayoutCalculationService.extractRealRatio(1000.0, null);
        assertEquals(1.0, ratio, DELTA);
    }

    @Test
    public void test_emptyWallet_ratioDefaultsTo1() {
        // Zero total balance → guard against div-by-zero
        double ratio = PayoutCalculationService.extractRealRatio(0.0, 0.0);
        assertEquals(1.0, ratio, DELTA);
    }

    // =========================================================================
    // computeRealBalanceCredit()
    // =========================================================================

    @Test
    public void test_rechargeCredit_fullAmountIsReal() {
        double credit = PayoutCalculationService.computeRealBalanceCredit(TransactionType.RECHARGE, 500.0);
        assertEquals(500.0, credit, DELTA);
    }

    @Test
    public void test_refundCredit_fullAmountIsReal() {
        double credit = PayoutCalculationService.computeRealBalanceCredit(TransactionType.REFUND, 200.0);
        assertEquals(200.0, credit, DELTA);
    }

    @Test
    public void test_bonusCredit_zeroRealBalance() {
        double credit = PayoutCalculationService.computeRealBalanceCredit(TransactionType.BONUS, 500.0);
        assertEquals(0.0, credit, DELTA);
    }

    @Test
    public void test_cashbackCredit_zeroRealBalance() {
        double credit = PayoutCalculationService.computeRealBalanceCredit(TransactionType.CASHBACK, 100.0);
        assertEquals(0.0, credit, DELTA);
    }

    @Test
    public void test_referralBonusCredit_zeroRealBalance() {
        double credit = PayoutCalculationService.computeRealBalanceCredit(TransactionType.REFERRAL_BONUS, 50.0);
        assertEquals(0.0, credit, DELTA);
    }

    // =========================================================================
    // computeRealBalanceAfterDebit()
    // =========================================================================

    @Test
    public void test_debit_proportionalReduction() {
        // total=1000, real=500 (ratio 0.5), debit=200 → realDeducted=100 → newReal=400
        double newReal = PayoutCalculationService.computeRealBalanceAfterDebit(1000.0, 500.0, 200.0);
        assertEquals(400.0, newReal, DELTA);
    }

    @Test
    public void test_debit_neverGoesNegative() {
        // total=100, real=20, debit=150 (more than total — shouldn't happen but guard)
        double newReal = PayoutCalculationService.computeRealBalanceAfterDebit(100.0, 20.0, 150.0);
        assertTrue(newReal >= 0.0, "real_balances must never go negative");
    }

    @Test
    public void test_debit_newRealNeverExceedsNewTotal() {
        // total=1000, real=999, debit=500 → newTotal=500, should be clamped to 500 at most
        double newReal = PayoutCalculationService.computeRealBalanceAfterDebit(1000.0, 999.0, 500.0);
        double newTotal = 500.0;
        assertTrue(newReal <= newTotal,
                "real_balances must not exceed total balance after debit. Got: " + newReal);
        assertTrue(newReal >= 0.0);
    }

    @Test
    public void test_debit_allBonusWallet_realStaysZero() {
        // total=1000, real=0 (all bonus) → debit=300 → newReal=0
        double newReal = PayoutCalculationService.computeRealBalanceAfterDebit(1000.0, 0.0, 300.0);
        assertEquals(0.0, newReal, DELTA);
    }

    @Test
    public void test_debit_fullyDepleted_rechargedFresh() {
        // After depletion: total=0, real=0. New recharge adds ₹500 real + ₹500 bonus (total=1000, real=500)
        // Ratio should now be 0.5 from fresh recharge only
        double ratioAfterDepletion = PayoutCalculationService.extractRealRatio(0.0, 0.0);
        assertEquals(1.0, ratioAfterDepletion, DELTA); // empty → guard returns 1.0
        // After fresh recharge
        double ratioFresh = PayoutCalculationService.extractRealRatio(1000.0, 500.0);
        assertEquals(0.5, ratioFresh, DELTA);
    }

    @Test
    public void test_zeroDebit_realUnchanged() {
        double newReal = PayoutCalculationService.computeRealBalanceAfterDebit(1000.0, 400.0, 0.0);
        assertEquals(400.0, newReal, DELTA);
    }

    // =========================================================================
    // roundMoney()
    // =========================================================================

    @Test
    public void test_roundMoney_halfUpRounding() {
        assertEquals(3.34, PayoutCalculationService.roundMoney(3.335), DELTA);
        assertEquals(3.33, PayoutCalculationService.roundMoney(3.334), DELTA);
        assertEquals(0.00, PayoutCalculationService.roundMoney(0.0), DELTA);
        assertEquals(100.00, PayoutCalculationService.roundMoney(100.0), DELTA);
    }
}
