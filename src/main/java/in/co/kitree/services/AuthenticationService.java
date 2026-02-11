package in.co.kitree.services;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;

/**
 * Service for handling Firebase authentication in Lambda
 * Verifies JWT tokens and extracts user information
 */
public class AuthenticationService {
    
    /**
     * Verifies a Firebase ID token and returns the user ID
     * 
     * @param idToken The Firebase ID token from the Authorization header
     * @return The user ID (uid) if token is valid, null otherwise
     * @throws FirebaseAuthException If token verification fails
     */
    public static String verifyTokenAndGetUserId(String idToken) throws FirebaseAuthException {
        if (idToken == null || idToken.isEmpty()) {
            return null;
        }
        
        // Remove "Bearer " prefix if present
        if (idToken.startsWith("Bearer ")) {
            idToken = idToken.substring(7);
        }
        
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            LoggingService.warn("firebase_token_verification_failed", java.util.Map.of("error", e.getMessage()));
            throw e;
        }
    }

    /**
     * Verifies a Firebase ID token and returns the full token object
     * 
     * @param idToken The Firebase ID token from the Authorization header
     * @return The decoded FirebaseToken if valid, null otherwise
     * @throws FirebaseAuthException If token verification fails
     */
    public static FirebaseToken verifyToken(String idToken) throws FirebaseAuthException {
        if (idToken == null || idToken.isEmpty()) {
            return null;
        }
        
        // Remove "Bearer " prefix if present
        if (idToken.startsWith("Bearer ")) {
            idToken = idToken.substring(7);
        }
        
        try {
            return FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            LoggingService.warn("firebase_token_verification_failed", java.util.Map.of("error", e.getMessage()));
            throw e;
        }
    }

    /**
     * Checks if the given user has the "admin" custom claim set to true.
     */
    public static boolean isAdmin(String userId) throws FirebaseAuthException {
        UserRecord user = FirebaseAuth.getInstance().getUser(userId);
        return Boolean.TRUE.equals(user.getCustomClaims().get("admin"));
    }

    /**
     * Extracts the Authorization token from headers map
     * Handles case-insensitive header keys
     * 
     * @param headers Map of headers (case-insensitive keys)
     * @return The token value or null if not found
     */
    public static String extractTokenFromHeaders(java.util.Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        
        // Try different case variations of the Authorization header
        // HTTP API v2 may use lowercase header names
        String token = headers.get("Authorization");
        if (token == null) {
            token = headers.get("authorization");
        }
        if (token == null) {
            token = headers.get("AUTHORIZATION");
        }
        // Also check for "authorization" with different casing
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("authorization")) {
                token = entry.getValue();
                break;
            }
        }
        
        return token;
    }
}

