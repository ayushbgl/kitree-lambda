package in.co.kitree.services;

import com.google.cloud.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BillingService.
 * Tests the stateless billing calculation logic — no Firebase emulators required.
 */
public class BillingServiceTest {

    private OnDemandConsultationService consultationService;

    private static final Double TEST_RATE_PER_MINUTE = 10.0;

    @BeforeEach
    public void setUp() {
        // Pass null db — calculation methods don't touch Firestore
        consultationService = new OnDemandConsultationService(null);
    }

    @Test
    public void testExpertNeverJoins_ZeroCharge() {
        Timestamp userJoinedAt = Timestamp.now();

        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(userJoinedAt);
        userInterval.setLeftAt(Timestamp.now());

        // Expert has NO intervals (never joined)
        List<OnDemandConsultationService.ParticipantInterval> userIntervals = new ArrayList<>();
        userIntervals.add(userInterval);
        List<OnDemandConsultationService.ParticipantInterval> expertIntervals = new ArrayList<>();

        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(0L, billableSeconds, "Billable seconds should be 0 when expert never joins");

        Double cost = consultationService.calculateCost(billableSeconds, TEST_RATE_PER_MINUTE);
        assertEquals(0.0, cost, "Cost should be 0 when billable seconds is 0");
    }

    @Test
    public void testNormalCall_CorrectOverlapBilling() {
        Timestamp baseTime = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 - 200, 0);

        // User joined at T=0, left at T=180
        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(baseTime);
        userInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 180, 0));

        // Expert joined at T=30, left at T=150
        // Overlap: T=30 to T=150 = 120 seconds
        OnDemandConsultationService.ParticipantInterval expertInterval =
            new OnDemandConsultationService.ParticipantInterval();
        expertInterval.setJoinedAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 30, 0));
        expertInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 150, 0));

        List<OnDemandConsultationService.ParticipantInterval> userIntervals = new ArrayList<>();
        userIntervals.add(userInterval);
        List<OnDemandConsultationService.ParticipantInterval> expertIntervals = new ArrayList<>();
        expertIntervals.add(expertInterval);

        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(120L, billableSeconds, "Billable seconds should be 120 (overlap from T=30 to T=150)");

        Double cost = consultationService.calculateCost(billableSeconds, TEST_RATE_PER_MINUTE);
        assertEquals(20.0, cost, "Cost should be Rs 20 for 2 minutes at Rs 10/min");
    }

    @Test
    public void testMultipleSegments_CorrectOverlapSum() {
        // User: [0-200], Expert: [50-100], [150-200]
        // Expected overlap: 50s + 50s = 100s

        Timestamp baseTime = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 - 250, 0);

        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(baseTime);
        userInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 200, 0));

        OnDemandConsultationService.ParticipantInterval expertInterval1 =
            new OnDemandConsultationService.ParticipantInterval();
        expertInterval1.setJoinedAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 50, 0));
        expertInterval1.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 100, 0));

        OnDemandConsultationService.ParticipantInterval expertInterval2 =
            new OnDemandConsultationService.ParticipantInterval();
        expertInterval2.setJoinedAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 150, 0));
        expertInterval2.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 200, 0));

        List<OnDemandConsultationService.ParticipantInterval> userIntervals = new ArrayList<>();
        userIntervals.add(userInterval);

        List<OnDemandConsultationService.ParticipantInterval> expertIntervals = new ArrayList<>();
        expertIntervals.add(expertInterval1);
        expertIntervals.add(expertInterval2);

        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(100L, billableSeconds,
            "Billable seconds should be 100 (50s from first segment + 50s from second segment)");
    }

    @Test
    public void testClientJoinsEarly_ChargeOnlyFromExpertJoin() {
        // User: [0-300], Expert: [60-300]
        // Expected overlap: 240 seconds

        Timestamp baseTime = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 - 350, 0);

        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(baseTime);
        userInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 300, 0));

        OnDemandConsultationService.ParticipantInterval expertInterval =
            new OnDemandConsultationService.ParticipantInterval();
        expertInterval.setJoinedAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 60, 0));
        expertInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 300, 0));

        List<OnDemandConsultationService.ParticipantInterval> userIntervals = new ArrayList<>();
        userIntervals.add(userInterval);
        List<OnDemandConsultationService.ParticipantInterval> expertIntervals = new ArrayList<>();
        expertIntervals.add(expertInterval);

        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(240L, billableSeconds,
            "Should only charge from when expert joined (240 seconds of overlap)");
    }

    @Test
    public void testMaxDurationCap_NeverExceedsAllowed() {
        // User: [0-600], Expert: [0-600], Max: 300
        // Expected: 300 (capped)

        Timestamp baseTime = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 - 650, 0);

        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(baseTime);
        userInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 600, 0));

        OnDemandConsultationService.ParticipantInterval expertInterval =
            new OnDemandConsultationService.ParticipantInterval();
        expertInterval.setJoinedAt(baseTime);
        expertInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 600, 0));

        List<OnDemandConsultationService.ParticipantInterval> userIntervals = new ArrayList<>();
        userIntervals.add(userInterval);
        List<OnDemandConsultationService.ParticipantInterval> expertIntervals = new ArrayList<>();
        expertIntervals.add(expertInterval);

        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 300L);

        assertEquals(300L, billableSeconds,
            "Billable seconds should be capped at maxAllowedDuration (300)");
    }

    @Test
    public void testCostCalculation_RoundingTo2Decimals() {
        // 70 seconds = 1.1667 minutes * Rs 10 = Rs 11.667 -> Rs 11.67
        Double cost = consultationService.calculateCost(70L, 10.0);
        assertEquals(11.67, cost, 0.001, "Cost should be rounded to 2 decimal places");

        // 90 seconds = 1.5 minutes * Rs 10 = Rs 15.00
        cost = consultationService.calculateCost(90L, 10.0);
        assertEquals(15.0, cost, 0.001, "Cost for 90 seconds at Rs 10/min should be Rs 15");

        // 0 seconds = Rs 0
        cost = consultationService.calculateCost(0L, 10.0);
        assertEquals(0.0, cost, 0.001, "Cost for 0 seconds should be Rs 0");
    }

    @Test
    public void testPlatformFeeCalculation() {
        Double fee = consultationService.calculatePlatformFee(100.0, 10.0);
        assertEquals(10.0, fee, 0.001, "Platform fee should be Rs 10 for Rs 100 at 10%");

        fee = consultationService.calculatePlatformFee(15.67, 15.0);
        assertEquals(2.35, fee, 0.001, "Platform fee should be rounded to 2 decimal places");
    }

    @Test
    public void testBillingResultStatuses() {
        BillingService.BillingResult completed = BillingService.BillingResult.completed(
            120L, 20.0, 2.0, 18.0);
        assertTrue(completed.success);
        assertEquals("completed", completed.status);
        assertEquals(120L, completed.billableSeconds);
        assertEquals(20.0, completed.cost);

        BillingService.BillingResult zeroCharge = BillingService.BillingResult.zeroCharge();
        assertTrue(zeroCharge.success);
        assertEquals("zero_charge", zeroCharge.status);
        assertEquals(0L, zeroCharge.billableSeconds);

        BillingService.BillingResult skipped = BillingService.BillingResult.skipped("already_completed");
        assertTrue(skipped.success);
        assertEquals("skipped_already_completed", skipped.status);

        BillingService.BillingResult error = BillingService.BillingResult.error("Something went wrong");
        assertFalse(error.success);
        assertEquals("error", error.status);
        assertEquals("Something went wrong", error.errorMessage);
    }
}
