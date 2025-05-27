package in.co.kitree;

import com.google.cloud.firestore.DocumentReference;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class EmulatorSetupTest extends TestBase {
    
    @Test
    void testFirestoreConnection() throws Exception {
        // Test Firestore write
        DocumentReference docRef = db.collection("test").document("test-doc");
        docRef.set(Map.of("test", "value")).get();
        
        // Test Firestore read
        var doc = docRef.get().get();
        assertTrue(doc.exists());
        assertEquals("value", doc.getString("test"));
    }
    
    @Test
    void testAuthConnection() throws Exception {
        // Test Auth user creation
        String uid = "test-user-" + System.currentTimeMillis();
        UserRecord user = createAuthUser(uid);
        
        // Verify user was created
        assertNotNull(user);
        assertEquals(uid, user.getUid());
        assertTrue(user.isEmailVerified());
        
        // Clean up
        deleteAuthUser(uid);
    }
    
    @Test
    void testEnvironmentVariables() {
        // Verify environment variables are set
        assertNotNull(System.getenv("FIRESTORE_EMULATOR_HOST"));
        assertNotNull(System.getenv("FIREBASE_AUTH_EMULATOR_HOST"));
        assertEquals("test", System.getenv("ENVIRONMENT"));
    }
} 