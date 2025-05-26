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

    public static FirebaseUser getUserDetails(Firestore db, String userId) throws ExecutionException, InterruptedException {
        DocumentReference doc = db.collection("users").document(userId);
        FirebaseUser user = new FirebaseUser();
        user.setUid(userId);
        ApiFuture<DocumentSnapshot> ref = doc.get();
        DocumentSnapshot documentSnapshot = ref.get();
        if (documentSnapshot.exists()) {
            user.setName(
                    (String) Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("displayName", ""));
            user.setPhoneNumber(
                    (String) Objects.requireNonNull(documentSnapshot.getData())
                            .getOrDefault("phoneNumber", ""));

            Map<String, String> referredByValue = (Map<String, String>) documentSnapshot.getData().getOrDefault("referredBy", new HashMap<String, String>());
            if (referredByValue == null) {
                referredByValue = new HashMap<String, String>();
            }
            user.setReferredBy(referredByValue);
        }
        return user;
    }
}
