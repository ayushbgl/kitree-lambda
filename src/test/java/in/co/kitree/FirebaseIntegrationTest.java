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

public class FirebaseIntegrationTest extends TestBase {
    private static final String TEST_USER_ID = "test-user-id";
    private static final String TEST_COLLECTION = "test-collection";
    private static final String TEST_DOCUMENT = "test-document";

    @BeforeEach
    public void setup() throws ExecutionException, InterruptedException {
        // Clear any existing test data
        clearCollection(TEST_COLLECTION);
    }

    @Test
    public void testCreateAndReadDocument() throws ExecutionException, InterruptedException {
        // Create test data
        Map<String, Object> data = new HashMap<>();
        data.put("field1", "value1");
        data.put("field2", 42);

        // Write to Firestore
        DocumentReference docRef = db.collection(TEST_COLLECTION).document(TEST_DOCUMENT);
        docRef.set(data).get();

        // Read from Firestore
        DocumentSnapshot document = docRef.get().get();

        // Verify
        assertTrue(document.exists());
        assertEquals("value1", document.getString("field1"));
        assertEquals(42, document.getLong("field2"));
    }

    @Test
    public void testCreateAndUpdateDocument() throws ExecutionException, InterruptedException {
        // Create initial document
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("field1", "initial");
        DocumentReference docRef = db.collection(TEST_COLLECTION).document(TEST_DOCUMENT);
        docRef.set(initialData).get();

        // Update document
        Map<String, Object> updates = new HashMap<>();
        updates.put("field1", "updated");
        docRef.update(updates).get();

        // Verify
        DocumentSnapshot document = docRef.get().get();
        assertEquals("updated", document.getString("field1"));
    }

    @Test
    public void testCreateAndDeleteDocument() throws ExecutionException, InterruptedException {
        // Create document
        Map<String, Object> data = new HashMap<>();
        data.put("field1", "value1");
        DocumentReference docRef = db.collection(TEST_COLLECTION).document(TEST_DOCUMENT);
        docRef.set(data).get();

        // Verify document exists
        DocumentSnapshot beforeDelete = docRef.get().get();
        assertTrue(beforeDelete.exists());

        // Delete document
        docRef.delete().get();

        // Verify document is deleted
        DocumentSnapshot afterDelete = docRef.get().get();
        assertFalse(afterDelete.exists());
    }

    @Test
    public void testCreateAndUpdateUserClaims() throws Exception {
        // Create test user
        UserRecord user = createAuthUser(TEST_USER_ID);
        assertNotNull(user);
        assertEquals(TEST_USER_ID, user.getUid());

        // Set custom claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin", true);
        claims.put("role", "superuser");
        setUserClaims(TEST_USER_ID, claims);

        // Verify claims
        UserRecord updatedUser = auth.getUser(TEST_USER_ID);
        Map<String, Object> userClaims = updatedUser.getCustomClaims();
        assertTrue((Boolean) userClaims.get("admin"));
        assertEquals("superuser", userClaims.get("role"));

        // Cleanup
        deleteAuthUser(TEST_USER_ID);
    }

    @Test
    public void testUserNotFound() {
        // Try to get a non-existent user
        assertThrows(FirebaseAuthException.class, () -> {
            auth.getUser("non-existent-user");
        });
    }
} 