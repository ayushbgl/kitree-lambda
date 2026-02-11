package in.co.kitree.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * Converts existing handler JSON string responses into proper ApiResponse objects
 * with appropriate HTTP status codes.
 *
 * This bridges the gap between the old handlers (which always return JSON strings
 * with success:true/false) and the new REST layer (which needs HTTP status codes).
 */
public class ResponseConverter {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Convert a handler's JSON string response to an ApiResponse with appropriate status code.
     * Default success status is 200 OK.
     */
    public static ApiResponse fromHandlerResponse(String handlerResult) {
        return fromHandlerResponse(handlerResult, 200);
    }

    /**
     * Convert a handler's JSON string response to an ApiResponse.
     * Uses 201 Created for successful resource creation.
     */
    public static ApiResponse fromHandlerResponseCreated(String handlerResult) {
        return fromHandlerResponse(handlerResult, 201);
    }

    /**
     * Core conversion logic.
     * Inspects the handler's JSON response to determine appropriate HTTP status code.
     */
    private static ApiResponse fromHandlerResponse(String handlerResult, int successStatus) {
        if (handlerResult == null) {
            return ApiResponse.errorMessage("No response from handler");
        }

        // Handle plain string responses (not JSON)
        if (!handlerResult.trim().startsWith("{") && !handlerResult.trim().startsWith("[")) {
            String lower = handlerResult.toLowerCase();
            if (lower.contains("not authorized") || lower.contains("unauthorized")) {
                return ApiResponse.unauthorizedMessage(handlerResult);
            }
            if (lower.contains("not found")) {
                return ApiResponse.notFoundMessage(handlerResult);
            }
            if (lower.contains("not verified")) {
                return ApiResponse.unauthorizedMessage(handlerResult);
            }
            // Plain success strings like "Done Successfully!"
            return new ApiResponse(successStatus, gson.toJson(Map.of("success", true, "message", handlerResult)));
        }

        try {
            JsonObject json = JsonParser.parseString(handlerResult).getAsJsonObject();

            // If response has success:true, return with success status
            if (json.has("success") && json.get("success").getAsBoolean()) {
                return new ApiResponse(successStatus, handlerResult);
            }

            // If response has success:false, determine error status from context
            if (json.has("success") && !json.get("success").getAsBoolean()) {
                return determineErrorStatus(json, handlerResult);
            }

            // No success field — treat as success (e.g., metrics responses, simple data)
            return new ApiResponse(successStatus, handlerResult);

        } catch (Exception e) {
            // If JSON parsing fails, return as-is with success status
            return new ApiResponse(successStatus, handlerResult);
        }
    }

    /**
     * Determine the appropriate error HTTP status from the response content.
     */
    private static ApiResponse determineErrorStatus(JsonObject json, String rawJson) {
        // Check for explicit errorCode
        if (json.has("errorCode")) {
            String errorCode = json.get("errorCode").getAsString();
            switch (errorCode) {
                case "NOT_FOUND":
                case "EXPERT_NOT_FOUND":
                case "ORDER_NOT_FOUND":
                case "SESSION_NOT_FOUND":
                    return ApiResponse.notFound(rawJson);
                case "UNAUTHORIZED":
                case "AUTH_REQUIRED":
                    return ApiResponse.unauthorized(rawJson);
                case "FORBIDDEN":
                case "ADMIN_REQUIRED":
                    return ApiResponse.forbidden(rawJson);
                case "CONFLICT":
                case "ALREADY_EXISTS":
                case "DUPLICATE":
                    return ApiResponse.conflict(rawJson);
                case "VALIDATION_ERROR":
                case "INVALID_INPUT":
                    return ApiResponse.unprocessable(rawJson);
                case "INSUFFICIENT_BALANCE":
                case "PAYMENT_FAILED":
                    return ApiResponse.unprocessable(rawJson);
                default:
                    break;
            }
        }

        // Check errorMessage patterns
        String errorMessage = "";
        if (json.has("errorMessage")) {
            errorMessage = json.get("errorMessage").getAsString().toLowerCase();
        } else if (json.has("error")) {
            errorMessage = json.get("error").getAsString().toLowerCase();
        }

        if (errorMessage.contains("not found") || errorMessage.contains("does not exist")) {
            return ApiResponse.notFound(rawJson);
        }
        if (errorMessage.contains("unauthorized") || errorMessage.contains("authentication required")) {
            return ApiResponse.unauthorized(rawJson);
        }
        if (errorMessage.contains("not authorized") || errorMessage.contains("admin access required")
                || errorMessage.contains("forbidden") || errorMessage.contains("permission denied")) {
            return ApiResponse.forbidden(rawJson);
        }
        if (errorMessage.contains("already exists") || errorMessage.contains("conflict")
                || errorMessage.contains("duplicate")) {
            return ApiResponse.conflict(rawJson);
        }
        if (errorMessage.contains("insufficient") || errorMessage.contains("invalid")
                || errorMessage.contains("validation")) {
            return ApiResponse.unprocessable(rawJson);
        }

        // Default: 400 Bad Request for generic failures
        return ApiResponse.badRequest(rawJson);
    }

}
