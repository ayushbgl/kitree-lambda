package in.co.kitree.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Stream Video API and verifying webhooks.
 * Handles webhook signature verification and Stream API calls.
 */
public class StreamService {

    private static final String STREAM_API_BASE_URL = "https://video.stream-io-api.com/api/v2/video";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Response object containing call details from Stream API.
     */
    public static class StreamCallResponse {
        private String callId;
        private String callType;
        private String status; // "ended", "active", etc.
        private Instant createdAt;
        private Instant endedAt;
        private StreamSession session;
        private String errorMessage;

        public String getCallId() { return callId; }
        public void setCallId(String callId) { this.callId = callId; }
        public String getCallType() { return callType; }
        public void setCallType(String callType) { this.callType = callType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getEndedAt() { return endedAt; }
        public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
        public StreamSession getSession() { return session; }
        public void setSession(StreamSession session) { this.session = session; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public boolean hasError() { return errorMessage != null && !errorMessage.isEmpty(); }

        public static StreamCallResponse error(String message) {
            StreamCallResponse response = new StreamCallResponse();
            response.setErrorMessage(message);
            return response;
        }
    }

    /**
     * Represents a call session with participant data.
     */
    public static class StreamSession {
        private String sessionId;
        private Instant startedAt;
        private Instant endedAt;
        private List<StreamParticipant> participants = new ArrayList<>();

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
        public Instant getEndedAt() { return endedAt; }
        public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
        public List<StreamParticipant> getParticipants() { return participants; }
        public void setParticipants(List<StreamParticipant> participants) { this.participants = participants; }
    }

    /**
     * Represents a participant in a call session with join/leave timestamps.
     */
    public static class StreamParticipant {
        private String oderId;
        private Instant joinedAt;
        private Instant leftAt; // null if still in call

        public String getUserId() { return oderId; }
        public void setUserId(String oderId) { this.oderId = oderId; }
        public Instant getJoinedAt() { return joinedAt; }
        public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
        public Instant getLeftAt() { return leftAt; }
        public void setLeftAt(Instant leftAt) { this.leftAt = leftAt; }

        public boolean isStillInCall() { return leftAt == null; }
    }

    /**
     * Represents a call recording from Stream.
     */
    public static class RecordingInfo {
        private String filename;
        private String url;              // Pre-signed S3 URL (expires after 2 weeks)
        private long durationSeconds;
        private String mimeType;
        private Instant startTime;
        private Instant endTime;

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }

        @Override
        public String toString() {
            return "RecordingInfo{" +
                    "filename='" + filename + '\'' +
                    ", durationSeconds=" + durationSeconds +
                    ", mimeType='" + mimeType + '\'' +
                    '}';
        }
    }
    
    private final boolean isTest;
    private final String apiKey;
    private final String apiSecret;
    
    /**
     * Creates a new StreamService instance.
     * Loads API credentials from secrets.json based on environment.
     * 
     * Expected secrets.json format:
     * {
     *   "STREAM_API_KEY": "...",
     *   "STREAM_API_SECRET": "...",
     *   "STREAM_API_KEY_TEST": "...",
     *   "STREAM_API_SECRET_TEST": "..."
     * }
     * 
     * @param isTest true for test environment, false for production
     */
    public StreamService(boolean isTest) {
        this.isTest = isTest;
        
        String key;
        String secret;

        if (isTest) {
            key = SecretsProvider.getString("STREAM_API_KEY_TEST");
            secret = SecretsProvider.getString("STREAM_API_SECRET_TEST");
        } else {
            key = SecretsProvider.getString("STREAM_API_KEY");
            secret = SecretsProvider.getString("STREAM_API_SECRET");
        }

        LoggingService.info("stream_service_initialized", Map.of("environment", isTest ? "TEST" : "PROD", "apiKeyLoaded", !key.isEmpty()));

        this.apiKey = key;
        this.apiSecret = secret;
    }
    
    /**
     * Verifies the webhook signature from Stream.
     * Stream uses HMAC-SHA256 with the API secret to sign webhook payloads.
     * The signature is sent in the X-SIGNATURE header.
     * 
     * @param body The raw webhook body
     * @param signature The signature from the X-SIGNATURE header
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyWebhookSignature(String body, String signature) {
        if (signature == null || signature.isEmpty()) {
            LoggingService.warn("stream_webhook_no_signature");
            return false;
        }

        if (apiSecret == null || apiSecret.isEmpty()) {
            LoggingService.warn("stream_webhook_no_api_secret");
            return false;
        }
        
        try {
            // Compute HMAC-SHA256 signature using API secret
            String computedSignatureHex = computeHmacSha256(body, apiSecret);
            
            // Stream may send signature in different formats, try to match:
            // 1. Direct hex comparison
            boolean isValid = computedSignatureHex.equalsIgnoreCase(signature);
            
            // 2. If not matching, try with "sha256=" prefix (some webhook implementations use this)
            if (!isValid && signature.startsWith("sha256=")) {
                isValid = computedSignatureHex.equalsIgnoreCase(signature.substring(7));
            }
            
            // 3. Try base64 encoded comparison
            if (!isValid) {
                String computedSignatureBase64 = computeHmacSha256Base64(body, apiSecret);
                isValid = computedSignatureBase64.equals(signature);
            }
            
            LoggingService.debug("stream_webhook_signature_verification", Map.of("result", isValid ? "VALID" : "INVALID"));
            if (!isValid) {
                LoggingService.debug("stream_webhook_signature_mismatch", Map.of("computed", computedSignatureHex, "received", signature));
            }

            return isValid;
        } catch (Exception e) {
            LoggingService.error("stream_webhook_signature_error", e);
            return false;
        }
    }
    
    /**
     * Computes HMAC-SHA256 signature and returns as Base64 string.
     */
    private String computeHmacSha256Base64(String message, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        sha256Hmac.init(secretKeySpec);
        byte[] signedBytes = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signedBytes);
    }
    
    /**
     * Computes HMAC-SHA256 signature for the given message.
     * 
     * @param message The message to sign
     * @param secret The secret key
     * @return The hex-encoded signature
     */
    private String computeHmacSha256(String message, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        sha256Hmac.init(secretKeySpec);
        byte[] signedBytes = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : signedBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Get the API key for this environment.
     * 
     * @return The Stream API key
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Check if the service is properly configured.
     * 
     * @return true if API key and secret are configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() 
            && apiSecret != null && !apiSecret.isEmpty();
    }
    
    /**
     * Check if running in test mode.
     * 
     * @return true if test environment
     */
    public boolean isTest() {
        return isTest;
    }
    
    /**
     * End a Stream video call.
     * This is used to programmatically end a call when finalizing a consultation.
     * 
     * @param callType The call type (e.g., "consultation_video", "consultation_audio")
     * @param callId The call ID
     * @return true if the call was ended successfully (or was already ended)
     */
    public boolean endCall(String callType, String callId) {
        if (callType == null || callId == null) {
            LoggingService.warn("stream_end_call_missing_params");
            return false;
        }

        if (!isConfigured()) {
            LoggingService.error("stream_end_call_not_configured");
            return false;
        }
        
        try {
            // Stream Video API endpoint to end a call
            // POST /video/call/{type}/{id}/end
            String url = String.format("%s/call/%s/%s/end?api_key=%s", 
                STREAM_API_BASE_URL, callType, callId, apiKey);
            
            // Create JWT token for API authentication
            String authToken = createServerAuthToken();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authToken)
                    .header("stream-auth-type", "jwt")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            LoggingService.debug("stream_end_call_response", Map.of("statusCode", statusCode));

            // 200 = success, 404 = call not found (already ended), both are OK
            if (statusCode == 200 || statusCode == 404) {
                return true;
            }

            LoggingService.warn("stream_end_call_failed", Map.of("statusCode", statusCode));
            return false;

        } catch (Exception e) {
            LoggingService.error("stream_end_call_error", e);
            return false;
        }
    }
    
    /**
     * Create a server-side JWT token for Stream API authentication.
     * This is a simplified version - for production, use proper JWT library.
     */
    private String createServerAuthToken() {
        try {
            // Create a simple JWT for server authentication
            // Header
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            
            // Payload with server flag
            long now = System.currentTimeMillis() / 1000;
            long exp = now + 3600; // 1 hour expiry
            String payload = String.format(
                "{\"user_id\":\"server\",\"iat\":%d,\"exp\":%d}", now, exp);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.getBytes(StandardCharsets.UTF_8));
            
            // Signature
            String signatureInput = header + "." + encodedPayload;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            byte[] signatureBytes = sha256Hmac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            
            return header + "." + encodedPayload + "." + signature;
            
        } catch (Exception e) {
            LoggingService.error("stream_auth_token_creation_error", e);
            return "";
        }
    }

    /**
     * Create a user token with specific role for session/webinar access.
     * The role determines what permissions the user has in the call.
     *
     * COST OPTIMIZATION: Users with viewer role will have no send-audio/send-video
     * permissions, making them "livestream viewers" which are billed at lower rates.
     *
     * @param userId The user's unique ID
     * @param role The user's role (host, speaker, viewer)
     * @param callId The session/call ID
     * @param callType The call type (default, audio_room, livestream)
     * @return JWT token for Stream SDK
     */
    public String createUserToken(String userId, String role, String callId, String callType) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

            long now = System.currentTimeMillis() / 1000;
            long exp = now + 86400; // 24 hour expiry for session tokens

            // Build payload with role and call restrictions
            StringBuilder payloadBuilder = new StringBuilder();
            payloadBuilder.append("{");
            payloadBuilder.append("\"user_id\":\"").append(userId).append("\",");
            payloadBuilder.append("\"role\":\"").append(role).append("\",");
            payloadBuilder.append("\"call_cids\":[\"").append(callType).append(":").append(callId).append("\"],");
            payloadBuilder.append("\"iat\":").append(now).append(",");
            payloadBuilder.append("\"exp\":").append(exp);
            payloadBuilder.append("}");

            String payload = payloadBuilder.toString();
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.getBytes(StandardCharsets.UTF_8));

            // Sign
            String signatureInput = header + "." + encodedPayload;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            byte[] signatureBytes = sha256Hmac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            LoggingService.debug("stream_user_token_created", Map.of("userId", userId, "role", role));
            return header + "." + encodedPayload + "." + signature;

        } catch (Exception e) {
            LoggingService.error("stream_user_token_error", e, Map.of("userId", userId, "role", role));
            return "";
        }
    }

    /**
     * Create a Stream call for sessions/webinars.
     * Uses appropriate call type based on interaction mode for cost optimization.
     *
     * @param callType The call type (default, audio_room, livestream)
     * @param callId The unique call ID
     * @param hostUserId The host user ID
     * @return true if call created successfully
     */
    public boolean createCall(String callType, String callId, String hostUserId) {
        if (!isConfigured()) {
            LoggingService.error("stream_create_call_not_configured");
            return false;
        }

        try {
            String url = String.format("%s/call/%s/%s?api_key=%s",
                STREAM_API_BASE_URL, callType, callId, apiKey);

            String authToken = createServerAuthToken();

            // Request body with call settings optimized for sessions
            String body = String.format(
                "{" +
                "\"data\": {" +
                "  \"created_by_id\": \"%s\"," +
                "  \"settings_override\": {" +
                "    \"audio\": {\"mic_default_on\": false}," +
                "    \"video\": {\"camera_default_on\": false}," +
                "    \"recording\": {\"mode\": \"available\"}" +
                "  }," +
                "  \"members\": [{\"user_id\": \"%s\", \"role\": \"host\"}]" +
                "}" +
                "}", hostUserId, hostUserId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", authToken)
                .header("stream-auth-type", "jwt")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            LoggingService.info("stream_create_call_response", Map.of("statusCode", statusCode, "callType", callType, "callId", callId));

            // 201 = created, 200 = already exists (get_or_create behavior)
            return statusCode == 201 || statusCode == 200;

        } catch (Exception e) {
            LoggingService.error("stream_create_call_error", e, Map.of("callType", callType, "callId", callId));
            return false;
        }
    }

    /**
     * Update a member's role in a call.
     * Used to promote/demote participants between viewer and speaker.
     *
     * @param callType The call type
     * @param callId The call ID
     * @param userId The user to update
     * @param newRole The new role (viewer, speaker, host)
     * @return true if update successful
     */
    public boolean updateMemberRole(String callType, String callId, String userId, String newRole) {
        if (!isConfigured()) {
            LoggingService.error("stream_update_member_role_not_configured");
            return false;
        }

        try {
            String url = String.format("%s/call/%s/%s/members?api_key=%s",
                STREAM_API_BASE_URL, callType, callId, apiKey);

            String authToken = createServerAuthToken();

            String body = String.format(
                "{\"update_members\": [{\"user_id\": \"%s\", \"role\": \"%s\"}]}",
                userId, newRole);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", authToken)
                .header("stream-auth-type", "jwt")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            LoggingService.info("stream_update_member_role_response", Map.of("statusCode", statusCode, "userId", userId, "newRole", newRole));

            return statusCode == 200 || statusCode == 201;

        } catch (Exception e) {
            LoggingService.error("stream_update_member_role_error", e, Map.of("userId", userId, "newRole", newRole));
            return false;
        }
    }

    /**
     * Grant specific permissions to a user in a call.
     *
     * @param callType The call type
     * @param callId The call ID
     * @param userId The user to grant permissions to
     * @param permissions List of permissions (e.g., "send-audio", "send-video")
     * @return true if permissions granted
     */
    public boolean grantPermissions(String callType, String callId, String userId, List<String> permissions) {
        if (!isConfigured()) {
            return false;
        }

        try {
            String url = String.format("%s/call/%s/%s/user_permissions?api_key=%s",
                STREAM_API_BASE_URL, callType, callId, apiKey);

            String authToken = createServerAuthToken();

            // Build permissions array
            StringBuilder permBuilder = new StringBuilder("[");
            for (int i = 0; i < permissions.size(); i++) {
                if (i > 0) permBuilder.append(",");
                permBuilder.append("\"").append(permissions.get(i)).append("\"");
            }
            permBuilder.append("]");

            String body = String.format(
                "{\"user_id\": \"%s\", \"grant_permissions\": %s}",
                userId, permBuilder.toString());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", authToken)
                .header("stream-auth-type", "jwt")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            LoggingService.error("stream_grant_permissions_error", e);
            return false;
        }
    }

    /**
     * Revoke specific permissions from a user in a call.
     *
     * @param callType The call type
     * @param callId The call ID
     * @param userId The user to revoke permissions from
     * @param permissions List of permissions to revoke
     * @return true if permissions revoked
     */
    public boolean revokePermissions(String callType, String callId, String userId, List<String> permissions) {
        if (!isConfigured()) {
            return false;
        }

        try {
            String url = String.format("%s/call/%s/%s/user_permissions?api_key=%s",
                STREAM_API_BASE_URL, callType, callId, apiKey);

            String authToken = createServerAuthToken();

            StringBuilder permBuilder = new StringBuilder("[");
            for (int i = 0; i < permissions.size(); i++) {
                if (i > 0) permBuilder.append(",");
                permBuilder.append("\"").append(permissions.get(i)).append("\"");
            }
            permBuilder.append("]");

            String body = String.format(
                "{\"user_id\": \"%s\", \"revoke_permissions\": %s}",
                userId, permBuilder.toString());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", authToken)
                .header("stream-auth-type", "jwt")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            LoggingService.error("stream_revoke_permissions_error", e);
            return false;
        }
    }

    /**
     * Fetch call details from Stream Video API.
     * GET /api/v2/video/call/{type}/{id}?api_key={apiKey}
     *
     * The call object includes a 'session' field with participant data:
     * - session.participants[]: Array of participants
     * - Each participant has: user_id, joined_at, left_at
     *
     * @param callType The call type (e.g., "consultation_video", "consultation_audio")
     * @param callId The call ID (orderId)
     * @return StreamCallResponse with call and session details, or error response
     */
    public StreamCallResponse getCallDetails(String callType, String callId) {
        if (callType == null || callId == null) {
            return StreamCallResponse.error("Missing callType or callId");
        }

        if (!isConfigured()) {
            return StreamCallResponse.error("StreamService not configured");
        }

        try {
            // Stream Video API endpoint to get call details
            // GET /video/call/{type}/{id}
            String url = String.format("%s/call/%s/%s?api_key=%s",
                STREAM_API_BASE_URL, callType, callId, apiKey);

            // Create JWT token for API authentication
            String authToken = createServerAuthToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authToken)
                    .header("stream-auth-type", "jwt")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            LoggingService.debug("stream_get_call_details_response", Map.of("statusCode", statusCode, "callType", callType, "callId", callId));

            if (statusCode == 404) {
                return StreamCallResponse.error("Call not found: " + callType + ":" + callId);
            }

            if (statusCode != 200) {
                LoggingService.warn("stream_get_call_details_failed", Map.of("statusCode", statusCode, "callType", callType, "callId", callId));
                return StreamCallResponse.error("HTTP " + statusCode);
            }

            // Parse response JSON
            return parseCallResponse(response.body(), callType, callId);

        } catch (Exception e) {
            LoggingService.error("stream_get_call_details_error", e, Map.of("callType", callType, "callId", callId));
            return StreamCallResponse.error(e.getMessage());
        }
    }

    /**
     * Fetch call recordings from Stream Video API.
     * GET /api/v2/video/call/{type}/{id}/recordings?api_key={apiKey}
     *
     * @param callType The call type (e.g., "consultation_audio")
     * @param callId The call ID (orderId)
     * @return List of RecordingInfo objects, or empty list if none found
     */
    public List<RecordingInfo> getCallRecordings(String callType, String callId) {
        List<RecordingInfo> recordings = new ArrayList<>();

        if (callType == null || callId == null) {
            LoggingService.warn("stream_get_recordings_missing_params");
            return recordings;
        }

        if (!isConfigured()) {
            LoggingService.error("stream_get_recordings_not_configured");
            return recordings;
        }

        try {
            String url = String.format("%s/call/%s/%s/recordings?api_key=%s",
                STREAM_API_BASE_URL, callType, callId, apiKey);

            String authToken = createServerAuthToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authToken)
                    .header("stream-auth-type", "jwt")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            LoggingService.debug("stream_get_recordings_response", Map.of("statusCode", statusCode, "callType", callType, "callId", callId));

            if (statusCode == 404) {
                LoggingService.info("stream_no_recordings_found", Map.of("callType", callType, "callId", callId));
                return recordings;
            }

            if (statusCode != 200) {
                LoggingService.warn("stream_get_recordings_failed", Map.of("statusCode", statusCode, "callType", callType, "callId", callId));
                return recordings;
            }

            return parseRecordingsResponse(response.body());

        } catch (Exception e) {
            LoggingService.error("stream_get_recordings_error", e, Map.of("callType", callType, "callId", callId));
            return recordings;
        }
    }

    /**
     * Parse the recordings response from Stream API.
     *
     * Expected response structure:
     * {
     *   "recordings": [
     *     {
     *       "filename": "recording-123.mp4",
     *       "url": "https://s3.../recording.mp4?signature=...",
     *       "start_time": "2024-01-15T10:30:00Z",
     *       "end_time": "2024-01-15T11:00:00Z"
     *     }
     *   ]
     * }
     */
    private List<RecordingInfo> parseRecordingsResponse(String jsonBody) {
        List<RecordingInfo> recordings = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode recordingsNode = root.path("recordings");

            if (!recordingsNode.isArray()) {
                LoggingService.debug("stream_no_recordings_array_in_response");
                return recordings;
            }

            for (JsonNode recordingNode : recordingsNode) {
                RecordingInfo info = new RecordingInfo();

                info.setFilename(recordingNode.path("filename").asText(null));
                info.setUrl(recordingNode.path("url").asText(null));

                // Parse start/end times and calculate duration
                String startTimeStr = recordingNode.path("start_time").asText(null);
                String endTimeStr = recordingNode.path("end_time").asText(null);

                if (startTimeStr != null && !startTimeStr.isEmpty()) {
                    try {
                        info.setStartTime(Instant.parse(startTimeStr));
                    } catch (Exception e) {
                        LoggingService.warn("stream_recording_parse_start_time_error", Map.of("startTime", startTimeStr));
                    }
                }

                if (endTimeStr != null && !endTimeStr.isEmpty()) {
                    try {
                        info.setEndTime(Instant.parse(endTimeStr));
                    } catch (Exception e) {
                        LoggingService.warn("stream_recording_parse_end_time_error", Map.of("endTime", endTimeStr));
                    }
                }

                // Calculate duration if both times are available
                if (info.getStartTime() != null && info.getEndTime() != null) {
                    info.setDurationSeconds(Duration.between(info.getStartTime(), info.getEndTime()).getSeconds());
                }

                // Determine MIME type from filename or URL
                String filename = info.getFilename();
                if (filename != null) {
                    if (filename.endsWith(".mp4")) {
                        info.setMimeType("audio/mp4");
                    } else if (filename.endsWith(".webm")) {
                        info.setMimeType("audio/webm");
                    } else if (filename.endsWith(".mp3")) {
                        info.setMimeType("audio/mpeg");
                    } else {
                        info.setMimeType("audio/mp4"); // Default
                    }
                }

                // Only add recordings with valid URLs
                if (info.getUrl() != null && !info.getUrl().isEmpty()) {
                    recordings.add(info);
                    LoggingService.debug("stream_recording_found", Map.of("filename", info.getFilename() != null ? info.getFilename() : "unknown"));
                }
            }

        } catch (Exception e) {
            LoggingService.error("stream_parse_recordings_error", e);
        }

        return recordings;
    }

    /**
     * Parse the JSON response from Stream API into StreamCallResponse.
     *
     * Expected response structure:
     * {
     *   "call": {
     *     "id": "order-123",
     *     "type": "consultation_video",
     *     "created_at": "2024-01-15T10:30:45.123Z",
     *     "ended_at": "2024-01-15T11:00:00.000Z",
     *     "session": {
     *       "id": "session-uuid",
     *       "started_at": "...",
     *       "ended_at": "...",
     *       "participants": [
     *         {"user_id": "user1", "joined_at": "...", "left_at": "..."},
     *         {"user_id": "expert1", "joined_at": "...", "left_at": "..."}
     *       ]
     *     }
     *   }
     * }
     */
    private StreamCallResponse parseCallResponse(String jsonBody, String callType, String callId) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode callNode = root.path("call");

            if (callNode.isMissingNode()) {
                return StreamCallResponse.error("No 'call' field in response");
            }

            StreamCallResponse response = new StreamCallResponse();
            response.setCallId(callNode.path("id").asText(callId));
            response.setCallType(callNode.path("type").asText(callType));

            // Parse timestamps
            String createdAt = callNode.path("created_at").asText(null);
            if (createdAt != null && !createdAt.isEmpty()) {
                response.setCreatedAt(Instant.parse(createdAt));
            }

            String endedAt = callNode.path("ended_at").asText(null);
            if (endedAt != null && !endedAt.isEmpty()) {
                response.setEndedAt(Instant.parse(endedAt));
            }

            // Determine status based on ended_at
            response.setStatus(response.getEndedAt() != null ? "ended" : "active");

            // Parse session if present
            JsonNode sessionNode = callNode.path("session");
            if (!sessionNode.isMissingNode() && !sessionNode.isNull()) {
                response.setSession(parseSession(sessionNode));
            }

            return response;

        } catch (Exception e) {
            LoggingService.error("stream_parse_call_response_error", e);
            return StreamCallResponse.error("Parse error: " + e.getMessage());
        }
    }

    /**
     * Parse a session node from the API response.
     */
    private StreamSession parseSession(JsonNode sessionNode) {
        StreamSession session = new StreamSession();

        session.setSessionId(sessionNode.path("id").asText(null));

        String startedAt = sessionNode.path("started_at").asText(null);
        if (startedAt != null && !startedAt.isEmpty()) {
            try {
                session.setStartedAt(Instant.parse(startedAt));
            } catch (Exception e) {
                LoggingService.warn("stream_session_parse_started_at_error", Map.of("startedAt", startedAt));
            }
        }

        String endedAt = sessionNode.path("ended_at").asText(null);
        if (endedAt != null && !endedAt.isEmpty()) {
            try {
                session.setEndedAt(Instant.parse(endedAt));
            } catch (Exception e) {
                LoggingService.warn("stream_session_parse_ended_at_error", Map.of("endedAt", endedAt));
            }
        }

        // Parse participants
        JsonNode participantsNode = sessionNode.path("participants");
        if (participantsNode.isArray()) {
            List<StreamParticipant> participants = new ArrayList<>();
            for (JsonNode participantNode : participantsNode) {
                StreamParticipant participant = parseParticipant(participantNode);
                if (participant != null) {
                    participants.add(participant);
                }
            }
            session.setParticipants(participants);
        }

        return session;
    }

    /**
     * Parse a participant node from the API response.
     */
    private StreamParticipant parseParticipant(JsonNode participantNode) {
        StreamParticipant participant = new StreamParticipant();

        // Try different field names for user ID
        String oderId = participantNode.path("user_id").asText(null);
        if (oderId == null || oderId.isEmpty()) {
            oderId = participantNode.path("user").path("id").asText(null);
        }
        if (oderId == null || oderId.isEmpty()) {
            return null; // Skip participants without user ID
        }
        participant.setUserId(oderId);

        // Parse timestamps
        String joinedAt = participantNode.path("joined_at").asText(null);
        if (joinedAt != null && !joinedAt.isEmpty()) {
            try {
                participant.setJoinedAt(Instant.parse(joinedAt));
            } catch (Exception e) {
                LoggingService.warn("stream_participant_parse_joined_at_error", Map.of("joinedAt", joinedAt));
            }
        }

        String leftAt = participantNode.path("left_at").asText(null);
        if (leftAt != null && !leftAt.isEmpty()) {
            try {
                participant.setLeftAt(Instant.parse(leftAt));
            } catch (Exception e) {
                LoggingService.warn("stream_participant_parse_left_at_error", Map.of("leftAt", leftAt));
            }
        }

        return participant;
    }

    /**
     * Parse a call CID into type and id components.
     * Call CID format: {type}:{id}
     *
     * @param callCid The full call CID (e.g., "consultation_video:abc123")
     * @return Array with [type, id] or null if invalid format
     */
    public static String[] parseCallCid(String callCid) {
        if (callCid == null || !callCid.contains(":")) {
            return null;
        }
        String[] parts = callCid.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return parts;
    }
}
