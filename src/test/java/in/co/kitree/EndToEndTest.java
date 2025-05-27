package in.co.kitree;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class EndToEndTest extends TestBase {
    private static final String TEST_USER_ID = "test-user-id";
    private static final String TEST_COLLECTION = "users";
    private static final String TEST_DOCUMENT = "test-user-id";

    @BeforeEach
    public void setup() throws ExecutionException, InterruptedException {
        // Clear any existing test data
        clearCollection(TEST_COLLECTION);
    }

    @Test
    public void testUserRegistrationAndProfileCreation() throws Exception {
        // 1. Create user in Firebase Auth
        UserRecord user = createAuthUser(TEST_USER_ID);
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());

        // 2. Create user profile in Firestore
        Map<String, Object> userData = createTestUser(TEST_USER_ID);
        DocumentReference userRef = db.collection(TEST_COLLECTION).document(TEST_USER_ID);
        userRef.set(userData).get();

        // 3. Verify user profile
        DocumentSnapshot profile = userRef.get().get();
        assertTrue(profile.exists());
        assertEquals("test@example.com", profile.getString("email"));
        assertEquals("Test User", profile.getString("displayName"));
        assertNotNull(profile.getLong("createdAt"));

        // 4. Set user claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin", true);
        setUserClaims(TEST_USER_ID, claims);

        // 5. Verify user claims
        UserRecord updatedUser = auth.getUser(TEST_USER_ID);
        Map<String, Object> userClaims = updatedUser.getCustomClaims();
        assertTrue((Boolean) userClaims.get("admin"));

        // 6. Cleanup
        deleteAuthUser(TEST_USER_ID);
        userRef.delete().get();
    }

    @Test
    public void testUserDeletion() throws Exception {
        // 1. Create user and profile
        UserRecord user = createAuthUser(TEST_USER_ID);
        Map<String, Object> userData = createTestUser(TEST_USER_ID);
        DocumentReference userRef = db.collection(TEST_COLLECTION).document(TEST_USER_ID);
        userRef.set(userData).get();

        // 2. Verify initial state
        assertTrue(userRef.get().get().exists());
        assertNotNull(auth.getUser(TEST_USER_ID));

        // 3. Delete user
        deleteAuthUser(TEST_USER_ID);
        userRef.delete().get();

        // 4. Verify deletion
        assertFalse(userRef.get().get().exists());
        assertThrows(FirebaseAuthException.class, () -> {
            auth.getUser(TEST_USER_ID);
        });
    }

    @Test
    public void testUserProfileUpdate() throws Exception {
        // 1. Create user and profile
        UserRecord user = createAuthUser(TEST_USER_ID);
        Map<String, Object> userData = createTestUser(TEST_USER_ID);
        DocumentReference userRef = db.collection(TEST_COLLECTION).document(TEST_USER_ID);
        userRef.set(userData).get();

        // 2. Update profile
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", "Updated Name");
        updates.put("email", "updated@example.com");
        userRef.update(updates).get();

        // 3. Verify updates
        DocumentSnapshot updatedProfile = userRef.get().get();
        assertEquals("Updated Name", updatedProfile.getString("displayName"));
        assertEquals("updated@example.com", updatedProfile.getString("email"));

        // 4. Cleanup
        deleteAuthUser(TEST_USER_ID);
        userRef.delete().get();
    }
} 