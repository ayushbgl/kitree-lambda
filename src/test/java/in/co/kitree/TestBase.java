package in.co.kitree;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public abstract class TestBase {
    protected static Firestore db;
    protected static FirebaseAuth auth;
    protected static final int FIRESTORE_PORT = 8080;
    protected static final int AUTH_PORT = 9099;
    protected static final String FIRESTORE_EMULATOR_HOST = System.getenv("CI") != null ? "localhost:" + FIRESTORE_PORT : "127.0.0.1:" + FIRESTORE_PORT;
    protected static final String AUTH_EMULATOR_HOST = System.getenv("CI") != null ? "localhost:" + AUTH_PORT : "127.0.0.1:" + AUTH_PORT;

    @BeforeAll
    public static void setupFirebase() throws IOException {
        // Set environment variables for emulators
        System.setProperty("FIRESTORE_EMULATOR_HOST", FIRESTORE_EMULATOR_HOST);
        System.setProperty("FIREBASE_AUTH_EMULATOR_HOST", AUTH_EMULATOR_HOST);

        // Load the service account file
        GoogleCredentials credentials = GoogleCredentials.fromStream(
            TestBase.class.getClassLoader().getResourceAsStream("serviceAccountKeyTest.json")
        );

        // Initialize Firebase with the service account
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId("kitree-emulator")
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        // Get Firestore and Auth instances
        db = FirestoreClient.getFirestore();
        auth = FirebaseAuth.getInstance();

        // Create service account in emulator
        try {
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setUid("dummy")
                .setEmail("dummy@kitree-emulator.iam.gserviceaccount.com")
                .setDisplayName("Test Service Account")
                .setEmailVerified(true);
            
            auth.createUser(request);
        } catch (FirebaseAuthException e) {
            // Ignore if user already exists
            if (!e.getMessage().contains("already exists")) {
                throw e;
            }
        }
    }

    @BeforeEach
    public void clearFirestore() throws ExecutionException, InterruptedException {
        // Clear all collections before each test
        // This is a placeholder - we'll implement the actual clearing logic
        // when we have the collection structure from your Firebase rules
    }

    protected Map<String, Object> createTestUser(String userId) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", "test@example.com");
        userData.put("displayName", "Test User");
        userData.put("createdAt", System.currentTimeMillis());
        return userData;
    }

    protected UserRecord createAuthUser(String uid) throws FirebaseAuthException {
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
            .setUid(uid)
            .setEmail("test@example.com")
            .setDisplayName("Test User")
            .setEmailVerified(true);
        
        return auth.createUser(request);
    }

    protected void deleteAuthUser(String uid) throws FirebaseAuthException {
        auth.deleteUser(uid);
    }

    protected void setUserClaims(String uid, Map<String, Object> claims) throws FirebaseAuthException {
        auth.setCustomUserClaims(uid, claims);
    }

    protected void clearCollection(String collectionPath) throws ExecutionException, InterruptedException {
        // Delete all documents in a collection
        db.collection(collectionPath).listDocuments().forEach(doc -> {
            try {
                doc.delete().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to delete document: " + doc.getId(), e);
            }
        });
    }
} 