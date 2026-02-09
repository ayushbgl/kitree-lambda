package in.co.kitree;

import com.google.gson.Gson;
import in.co.kitree.pojos.RequestBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the RequestBody JSON deserialization respects security invariants:
 * - {@code callerUserId} is declared {@code transient} → client can never inject it via JSON
 * - {@code actAs} is a normal field → properly round-trips through JSON
 */
public class ActAsRequestBodySerializationTest {

    private final Gson gson = new Gson();

    @Test
    @DisplayName("callerUserId in JSON body is silently ignored (transient field)")
    void callerUserIdIsNotDeserializedFromJson() {
        String json = "{\"function\":\"expert_metrics\",\"actAs\":\"expert-123\",\"callerUserId\":\"hacker-456\"}";

        RequestBody body = gson.fromJson(json, RequestBody.class);

        assertEquals("expert-123", body.getActAs(), "actAs should be deserialized normally");
        assertNull(body.getCallerUserId(),
            "callerUserId must be null after JSON deserialization — transient prevents client spoofing");
    }

    @Test
    @DisplayName("callerUserId set programmatically (server-side) is preserved")
    void callerUserIdSetProgrammaticallyIsPreserved() {
        RequestBody body = new RequestBody();
        body.setCallerUserId("real-server-side-uid");

        assertEquals("real-server-side-uid", body.getCallerUserId());
    }

    @Test
    @DisplayName("actAs is correctly deserialized from JSON")
    void actAsIsDeserializedFromJson() {
        String json = "{\"function\":\"expert_earnings_balance\",\"actAs\":\"target-expert-id\"}";

        RequestBody body = gson.fromJson(json, RequestBody.class);

        assertEquals("target-expert-id", body.getActAs());
    }

    @Test
    @DisplayName("Missing actAs in JSON results in null")
    void missingActAsIsNull() {
        String json = "{\"function\":\"expert_earnings_balance\"}";

        RequestBody body = gson.fromJson(json, RequestBody.class);

        assertNull(body.getActAs());
    }

    @Test
    @DisplayName("Empty string actAs is preserved (not coerced to null)")
    void emptyActAsIsPreservedAsEmptyString() {
        String json = "{\"function\":\"expert_metrics\",\"actAs\":\"\"}";

        RequestBody body = gson.fromJson(json, RequestBody.class);

        assertEquals("", body.getActAs());
    }

    @Test
    @DisplayName("callerUserId is excluded when serializing back to JSON")
    void callerUserIdIsExcludedFromSerialization() {
        RequestBody body = new RequestBody();
        body.setCallerUserId("server-uid");
        body.setActAs("expert-uid");

        String json = gson.toJson(body);

        assertFalse(json.contains("callerUserId"),
            "callerUserId must not appear in serialized JSON — transient keeps it server-side only");
        assertTrue(json.contains("expert-uid"), "actAs must still be serialized");
    }

    @Test
    @DisplayName("JSON with only callerUserId injection attempt produces null callerUserId")
    void pureInjectionAttackProducesNullCallerUserId() {
        // Simulate a malicious client sending only callerUserId
        String json = "{\"function\":\"record_expert_payout\",\"callerUserId\":\"admin-uid\"}";

        RequestBody body = gson.fromJson(json, RequestBody.class);

        assertNull(body.getCallerUserId(),
            "Injection of callerUserId via JSON must be completely ignored");
        assertNull(body.getActAs(), "actAs should also be null");
    }
}
