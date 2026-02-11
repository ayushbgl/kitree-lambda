package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import in.co.kitree.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BillingService.
 * Tests the stateless billing calculation based on Stream API data.
 */
public class BillingServiceTest extends TestBase {

    private BillingService billingService;
    private OnDemandConsultationService consultationService;
    private WalletService walletService;

    // Test data constants
    private static final String TEST_USER_ID = "test-user-001";
    private static final String TEST_EXPERT_ID = "test-expert-001";
    private static final String TEST_ORDER_ID = "test-order-001";
    private static final String TEST_CALL_TYPE = "consultation_video";
    private static final String TEST_CALL_CID = TEST_CALL_TYPE + ":" + TEST_ORDER_ID;
    private static final Double TEST_RATE_PER_MINUTE = 10.0; // Rs 10/min
    private static final Double TEST_PLATFORM_FEE_PERCENT = 10.0; // 10%
    private static final String TEST_CURRENCY = "INR";

    @BeforeEach
    public void setUp() throws ExecutionException, InterruptedException {
        // Services under test - using real Firestore emulator
        consultationService = new OnDemandConsultationService(db);
        walletService = new WalletService(db);

        // Create test user and expert
        createTestUserInFirestore(TEST_USER_ID, "Test User");
        createTestUserInFirestore(TEST_EXPERT_ID, "Test Expert");

        // Create expert store document
        createExpertStore(TEST_EXPERT_ID);

        // Create wallet for user with expert
        createExpertWallet(TEST_USER_ID, TEST_EXPERT_ID, 1000.0); // Rs 1000 balance
    }

    @Test
    public void testExpertNeverJoins_ZeroCharge() throws ExecutionException, InterruptedException {
        // Setup: Create a CONNECTED order where only user joined
        createTestOrder(TEST_USER_ID, TEST_ORDER_ID, TEST_EXPERT_ID, "CONNECTED",
            TEST_RATE_PER_MINUTE, TEST_PLATFORM_FEE_PERCENT, 600L); // 10 min max

        // Set user_joined_at but not expert_joined_at (expert never joined)
        Timestamp userJoinedAt = Timestamp.now();
        updateOrderField(TEST_USER_ID, TEST_ORDER_ID, "user_joined_at", userJoinedAt);
        // Note: both_participants_joined_at is NOT set (expert never joined)

        // Execute billing with a mock/stub approach
        // Since we can't easily mock Stream API, we test the overlap calculation logic directly
        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(userJoinedAt);
        userInterval.setLeftAt(Timestamp.now());

        // Expert has NO intervals (never joined)
        List<OnDemandConsultationService.ParticipantInterval> userIntervals = new ArrayList<>();
        userIntervals.add(userInterval);
        List<OnDemandConsultationService.ParticipantInterval> expertIntervals = new ArrayList<>(); // Empty

        // Calculate overlap - should be 0
        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(0L, billableSeconds, "Billable seconds should be 0 when expert never joins");

        // Verify cost calculation
        Double cost = consultationService.calculateCost(billableSeconds, TEST_RATE_PER_MINUTE);
        assertEquals(0.0, cost, "Cost should be 0 when billable seconds is 0");
    }

    @Test
    public void testNormalCall_CorrectOverlapBilling() throws ExecutionException, InterruptedException {
        // Setup: User and expert both in call for 120 seconds
        Timestamp baseTime = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 - 200, 0); // 200 seconds ago

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

        // Calculate overlap
        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(120L, billableSeconds, "Billable seconds should be 120 (overlap from T=30 to T=150)");

        // Verify cost: 120 seconds = 2 minutes * Rs 10/min = Rs 20
        Double cost = consultationService.calculateCost(billableSeconds, TEST_RATE_PER_MINUTE);
        assertEquals(20.0, cost, "Cost should be Rs 20 for 2 minutes at Rs 10/min");
    }

    @Test
    public void testMultipleSegments_CorrectOverlapSum() throws ExecutionException, InterruptedException {
        // Setup: Expert joins, drops, rejoins
        // User: [0-200]
        // Expert: [50-100], [150-200]
        // Expected overlap: [50-100] (50s) + [150-200] (50s) = 100s

        Timestamp baseTime = Timestamp.ofTimeSecondsAndNanos(
            System.currentTimeMillis() / 1000 - 250, 0);

        // User interval: 0-200
        OnDemandConsultationService.ParticipantInterval userInterval =
            new OnDemandConsultationService.ParticipantInterval();
        userInterval.setJoinedAt(baseTime);
        userInterval.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 200, 0));

        // Expert interval 1: 50-100
        OnDemandConsultationService.ParticipantInterval expertInterval1 =
            new OnDemandConsultationService.ParticipantInterval();
        expertInterval1.setJoinedAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 50, 0));
        expertInterval1.setLeftAt(Timestamp.ofTimeSecondsAndNanos(
            baseTime.getSeconds() + 100, 0));

        // Expert interval 2: 150-200
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

        // Calculate overlap
        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, 600L);

        assertEquals(100L, billableSeconds,
            "Billable seconds should be 100 (50s from first segment + 50s from second segment)");
    }

    @Test
    public void testClientJoinsEarly_ChargeOnlyFromExpertJoin() throws ExecutionException, InterruptedException {
        // Setup: Client joins early, expert joins later
        // User: [0-300]
        // Expert: [60-300]
        // Expected overlap: [60-300] = 240 seconds

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
            baseTime.getSeconds() + 60, 0)); // Expert joins 60 seconds later
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
    public void testMaxDurationCap_NeverExceedsAllowed() throws ExecutionException, InterruptedException {
        // Setup: Actual overlap exceeds max allowed duration
        // User: [0-600] (10 min)
        // Expert: [0-600] (10 min)
        // Max allowed: 300 seconds (5 min)
        // Expected: 300 seconds (capped)

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

        Long maxAllowedDuration = 300L; // 5 minutes cap
        Long billableSeconds = consultationService.calculateOverlapFromIntervals(
            userIntervals, expertIntervals, maxAllowedDuration);

        assertEquals(300L, billableSeconds,
            "Billable seconds should be capped at maxAllowedDuration (300)");
    }

    @Test
    public void testCostCalculation_RoundingTo2Decimals() {
        // Test cost rounding
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
        // Test platform fee calculation
        // Cost = Rs 100, Platform fee = 10%
        Double fee = consultationService.calculatePlatformFee(100.0, 10.0);
        assertEquals(10.0, fee, 0.001, "Platform fee should be Rs 10 for Rs 100 at 10%");

        // Cost = Rs 15.67, Platform fee = 15%
        fee = consultationService.calculatePlatformFee(15.67, 15.0);
        assertEquals(2.35, fee, 0.001, "Platform fee should be rounded to 2 decimal places");
    }

    @Test
    public void testBillingResultStatuses() {
        // Test BillingResult factory methods
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

    // Helper methods to create test data in Firestore

    private void createTestUserInFirestore(String userId, String displayName)
            throws ExecutionException, InterruptedException {
        Map<String, Object> userData = new HashMap<>();
        userData.put("display_name", displayName);
        userData.put("email", userId + "@test.com");
        userData.put("created_at", Timestamp.now());

        db.collection("users").document(userId).set(userData).get();
    }

    private void createExpertStore(String expertId) throws ExecutionException, InterruptedException {
        Map<String, Object> storeData = new HashMap<>();
        storeData.put("consultation_status", "BUSY");
        storeData.put("consultation_status_updated_at", Timestamp.now());

        db.collection("users").document(expertId)
            .collection("public").document("store")
            .set(storeData).get();
    }

    private void createExpertWallet(String userId, String expertId, Double balance)
            throws ExecutionException, InterruptedException {
        Map<String, Object> walletData = new HashMap<>();
        Map<String, Object> balances = new HashMap<>();
        balances.put(TEST_CURRENCY, balance);
        walletData.put("balances", balances);
        walletData.put("updated_at", Timestamp.now());

        db.collection("users").document(userId)
            .collection("expert_wallets").document(expertId)
            .set(walletData).get();
    }

    private void createTestOrder(String userId, String orderId, String expertId, String status,
                                  Double ratePerMinute, Double platformFeePercent, Long maxAllowedDuration)
            throws ExecutionException, InterruptedException {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("order_id", orderId);
        orderData.put("user_id", userId);
        orderData.put("expert_id", expertId);
        orderData.put("type", "ON_DEMAND_CONSULTATION");
        orderData.put("consultation_type", "video");
        orderData.put("status", status);
        orderData.put("expert_rate_per_minute", ratePerMinute);
        orderData.put("platform_fee_percent", platformFeePercent);
        orderData.put("max_allowed_duration", maxAllowedDuration);
        orderData.put("currency", TEST_CURRENCY);
        orderData.put("stream_call_cid", TEST_CALL_CID);
        orderData.put("created_at", Timestamp.now());
        orderData.put("start_time", Timestamp.now());

        db.collection("users").document(userId)
            .collection("orders").document(orderId)
            .set(orderData).get();
    }

    private void updateOrderField(String userId, String orderId, String field, Object value)
            throws ExecutionException, InterruptedException {
        db.collection("users").document(userId)
            .collection("orders").document(orderId)
            .update(field, value).get();
    }
}
