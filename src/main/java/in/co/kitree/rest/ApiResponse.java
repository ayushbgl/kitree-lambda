package in.co.kitree.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Standardized API response wrapper for REST endpoints.
 * Produces Lambda Function URL structured responses with proper HTTP status codes.
 */
public class ApiResponse {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    ApiResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = new HashMap<>();
        this.headers.put("Content-Type", "application/json");
    }

    /**
     * Convert to Lambda Function URL structured response format.
     * When a Lambda Function URL handler returns a Map with statusCode/headers/body,
     * Lambda uses those values instead of wrapping in 200.
     */
    public Map<String, Object> toLambdaResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", headers);
        response.put("body", body);
        return response;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    // --- Factory Methods ---

    public static ApiResponse ok(String jsonBody) {
        return new ApiResponse(200, jsonBody);
    }

    public static ApiResponse created(String jsonBody) {
        return new ApiResponse(201, jsonBody);
    }

    public static ApiResponse badRequest(String jsonBody) {
        return new ApiResponse(400, jsonBody);
    }

    public static ApiResponse badRequestMessage(String message) {
        return new ApiResponse(400, gson.toJson(Map.of("success", false, "errorMessage", message)));
    }

    public static ApiResponse unauthorized(String jsonBody) {
        return new ApiResponse(401, jsonBody);
    }

    public static ApiResponse unauthorizedMessage(String message) {
        return new ApiResponse(401, gson.toJson(Map.of("success", false, "errorMessage", message)));
    }

    public static ApiResponse forbidden(String jsonBody) {
        return new ApiResponse(403, jsonBody);
    }

    public static ApiResponse forbiddenMessage(String message) {
        return new ApiResponse(403, gson.toJson(Map.of("success", false, "errorMessage", message)));
    }

    public static ApiResponse notFound(String jsonBody) {
        return new ApiResponse(404, jsonBody);
    }

    public static ApiResponse notFoundMessage(String message) {
        return new ApiResponse(404, gson.toJson(Map.of("success", false, "errorMessage", message)));
    }

    public static ApiResponse conflict(String jsonBody) {
        return new ApiResponse(409, jsonBody);
    }

    public static ApiResponse unprocessable(String jsonBody) {
        return new ApiResponse(422, jsonBody);
    }

    public static ApiResponse error(String jsonBody) {
        return new ApiResponse(500, jsonBody);
    }

    public static ApiResponse errorMessage(String message) {
        return new ApiResponse(500, gson.toJson(Map.of("success", false, "errorMessage", message)));
    }

    public static ApiResponse options() {
        return new ApiResponse(204, "");
    }
}
