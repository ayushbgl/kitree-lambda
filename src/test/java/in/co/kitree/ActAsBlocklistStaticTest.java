package in.co.kitree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static tests for the BLOCKED_ACT_AS_PATHS constant in Handler.
 * Verifies that all call/session/purchase path prefixes are blocked from impersonation,
 * and that safe management paths (earnings, metrics, products) are allowed.
 */
public class ActAsBlocklistStaticTest {

    @SuppressWarnings("unchecked")
    private static Set<String> getBlockedPaths() throws Exception {
        Field field = Handler.class.getDeclaredField("BLOCKED_ACT_AS_PATHS");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

    // --- Path prefixes that MUST be blocked (session/call/purchase/consultation) ---
    private static final List<String> MUST_BE_BLOCKED = Arrays.asList(
        "/api/v1/stream/token",
        "/api/v1/sessions",
        "/api/v1/consultations",
        "/api/v1/reviews",
        "/api/v1/products/buy",
        "/api/v1/orders"
    );

    // --- Path prefixes that MUST NOT match any blocked prefix (safe for admin impersonation) ---
    private static final List<String> MUST_NOT_BE_BLOCKED = Arrays.asList(
        "/api/v1/experts/someExpert/earnings",
        "/api/v1/experts/someExpert/metrics",
        "/api/v1/experts/someExpert/products",
        "/api/v1/experts/someExpert/booking-metrics",
        "/api/v1/experts/someExpert/platform-fee",
        "/api/v1/experts/someExpert/mark-busy",
        "/api/v1/experts/someExpert/mark-free",
        "/api/v1/experts/someExpert/payouts",
        "/api/v1/experts/someExpert/availability",
        "/api/v1/app/startup"
    );

    private boolean isBlockedByPrefix(Set<String> blockedPaths, String path) {
        for (String prefix : blockedPaths) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    @Test
    @DisplayName("All call/session/purchase path prefixes are in the blocklist")
    void dangerousPathsAreBlocked() throws Exception {
        Set<String> blocked = getBlockedPaths();
        for (String path : MUST_BE_BLOCKED) {
            assertTrue(blocked.contains(path),
                "'" + path + "' must be in BLOCKED_ACT_AS_PATHS");
        }
    }

    @Test
    @DisplayName("Management paths are NOT blocked by any prefix")
    void managementPathsAreAllowedForImpersonation() throws Exception {
        Set<String> blocked = getBlockedPaths();
        for (String path : MUST_NOT_BE_BLOCKED) {
            assertFalse(isBlockedByPrefix(blocked, path),
                "'" + path + "' must NOT be blocked — admin should be able to manage expert data via actAs");
        }
    }

    @Test
    @DisplayName("Blocklist is non-empty")
    void blocklistIsNonEmpty() throws Exception {
        Set<String> blocked = getBlockedPaths();
        assertTrue(blocked.size() >= MUST_BE_BLOCKED.size(),
            "BLOCKED_ACT_AS_PATHS must contain at least all expected dangerous prefixes");
    }

    @Test
    @DisplayName("Blocklist contains no null or empty entries")
    void blocklistHasNoBlankEntries() throws Exception {
        Set<String> blocked = getBlockedPaths();
        for (String path : blocked) {
            assertNotNull(path, "Blocklist must not contain null entries");
            assertFalse(path.isBlank(), "Blocklist must not contain blank entries");
        }
    }
}
