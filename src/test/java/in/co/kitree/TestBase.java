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
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class TestBase {
    protected static Firestore db;
    protected static FirebaseAuth auth;
    protected static boolean emulatorAvailable = false;
    protected static final int FIRESTORE_PORT = 8080;
    protected static final int AUTH_PORT = 9099;
    protected static final String FIRESTORE_EMULATOR_HOST = System.getenv("CI") != null ? "localhost:" + FIRESTORE_PORT : "127.0.0.1:" + FIRESTORE_PORT;
    protected static final String AUTH_EMULATOR_HOST = System.getenv("CI") != null ? "localhost:" + AUTH_PORT : "127.0.0.1:" + AUTH_PORT;

    @BeforeAll
    public static void setupFirebase() {
        // Check if emulators are running before attempting setup
        String host = System.getenv("CI") != null ? "localhost" : "127.0.0.1";
        if (!isPortOpen(host, FIRESTORE_PORT) || !isPortOpen(host, AUTH_PORT)) {
            System.err.println("Firebase emulators not running — emulator-dependent tests will be skipped");
            emulatorAvailable = false;
            return;
        }

        try {
            // Set environment variables for emulators
            System.setProperty("FIRESTORE_EMULATOR_HOST", FIRESTORE_EMULATOR_HOST);
            System.setProperty("FIREBASE_AUTH_EMULATOR_HOST", AUTH_EMULATOR_HOST);
            System.setProperty("FIREBASE_AUTH_EMULATOR_SKIP_CREDENTIALS_VALIDATION", "true");

            // Load the service account file
            InputStream serviceAccount = TestBase.class.getClassLoader()
                .getResourceAsStream("serviceAccountKeyTest.json");
            if (serviceAccount == null) {
                System.err.println("serviceAccountKeyTest.json not found — emulator tests will be skipped");
                emulatorAvailable = false;
                return;
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);

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
            emulatorAvailable = true;
        } catch (Exception e) {
            System.err.println("Firebase setup failed — emulator tests will be skipped: " + e.getMessage());
            emulatorAvailable = false;
        }
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    public void clearFirestore() throws ExecutionException, InterruptedException {
        assumeTrue(emulatorAvailable, "Firebase emulators not available — skipping");

        // Clear all collections before each test
        clearCollection("users");
        clearCollection("servicePlans");
        clearCollection("coupons");
        clearCollection("orders");

        // Clear any test users from Auth
        try {
            auth.listUsers(null).iterateAll().forEach(user -> {
                if (user.getUid().startsWith("test-")) {
                    try {
                        auth.deleteUser(user.getUid());
                    } catch (FirebaseAuthException e) {
                        System.err.println("Failed to delete test user: " + user.getUid());
                    }
                }
            });
        } catch (FirebaseAuthException e) {
            System.err.println("Failed to list users for cleanup: " + e.getMessage());
        }
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