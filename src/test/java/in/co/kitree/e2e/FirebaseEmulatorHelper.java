package in.co.kitree.e2e;

import com.google.firebase.FirebaseApp;

public class FirebaseEmulatorHelper {
    public static void cleanup() {
        try {
            FirebaseApp.getInstance().delete();
        } catch (Exception ignored) {}
    }
} 