package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import in.co.kitree.pojos.FirebaseUser;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class UserService {
    private static final String USERS_COLLECTION = "users";

    public static FirebaseUser getUserDetails(Firestore db, String userId) throws ExecutionException, InterruptedException {
        // Validate inputs
        if (db == null) {
            throw new IllegalArgumentException("Firestore instance cannot be null");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        // Create user object with basic info
        FirebaseUser user = new FirebaseUser();
        user.setUid(userId);

        // Get user document
        DocumentReference docRef = db.collection(USERS_COLLECTION).document(userId);
        if (docRef == null) {
            throw new IllegalStateException("Failed to get document reference");
        }

        // Fetch document
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        // If document doesn't exist, return user with just the ID
        if (!document.exists()) {
            return user;
        }

        // Get document data
        Map<String, Object> data = document.getData();
        if (data == null) {
            return user;
        }

        // Set user properties with null checks
        String name = (String) data.getOrDefault("displayName", "");
        user.setName(name == null ? "" : name);
        String phone = (String) data.getOrDefault("phoneNumber", "");
        user.setPhoneNumber(phone == null ? "" : phone);

        // Handle referredBy field
        Object referredByObj = data.get("referredBy");
        if (referredByObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> referredBy = (Map<String, String>) referredByObj;
            user.setReferredBy(referredBy);
        } else {
            user.setReferredBy(new HashMap<>());
        }

        return user;
    }
}
