package in.co.kitree.services;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Structured logging service for CloudWatch Logs Insights.
 * 
 * Features:
 * - JSON structured output
 * - Request ID and correlation ID tracking
 * - Business context (userId, orderId, expertId)
 * - Log levels (DEBUG, INFO, WARN, ERROR)
 * 
 * Example CloudWatch Logs Insights queries:
 * 
 * <pre>
 * -- Find all logs for a specific order
 * fields @timestamp, message, orderId, userId, expertId
 * | filter orderId = "abc-123"
 * | sort @timestamp asc
 * 
 * -- Find all errors in auto-terminate cron
 * fields @timestamp, message, error, orderId
 * | filter level = "ERROR" and function = "auto_terminate_consultations"
 * 
 * -- Track consultation lifecycle
 * fields @timestamp, message, orderId, requestId
 * | filter orderId = "abc-123"
 * | filter message in ["consultation_initiated", "consultation_connected", "consultation_ended"]
 * </pre>
 */
public class LoggingService {
    
    private static final Logger logger = LogManager.getLogger(LoggingService.class);
    private static final Gson gson = new Gson();
    
    // ThreadContext (MDC) keys
    public static final String KEY_REQUEST_ID = "requestId";
    public static final String KEY_CORRELATION_ID = "correlationId";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_ORDER_ID = "orderId";
    public static final String KEY_EXPERT_ID = "expertId";
    public static final String KEY_FUNCTION = "function";
    public static final String KEY_DATA = "data";
    
    /**
     * Initialize logging context with Lambda request information.
     * Call this at the start of every Lambda invocation.
     * 
     * @param context Lambda context
     */
    public static void initRequest(Context context) {
        clearContext();
        if (context != null) {
            ThreadContext.put(KEY_REQUEST_ID, context.getAwsRequestId());
        }
    }
    
    /**
     * Initialize logging context with a custom request ID.
     * Useful for cron jobs or webhook processing.
     * 
     * @param requestId Custom request ID
     */
    public static void initRequest(String requestId) {
        clearContext();
        if (requestId != null) {
            ThreadContext.put(KEY_REQUEST_ID, requestId);
        }
    }
    
    /**
     * Set a correlation ID for tracking requests across services.
     * 
     * @param correlationId Correlation ID (e.g., from webhook)
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            ThreadContext.put(KEY_CORRELATION_ID, correlationId);
        }
    }
    
    /**
     * Set the current function being executed.
     * 
     * @param function Function name (e.g., "on_demand_consultation_initiate")
     */
    public static void setFunction(String function) {
        if (function != null) {
            ThreadContext.put(KEY_FUNCTION, function);
        }
    }
    
    /**
     * Set business context for logging.
     * 
     * @param userId User ID (can be null)
     * @param orderId Order ID (can be null)
     * @param expertId Expert ID (can be null)
     */
    public static void setContext(String userId, String orderId, String expertId) {
        if (userId != null) {
            ThreadContext.put(KEY_USER_ID, userId);
        }
        if (orderId != null) {
            ThreadContext.put(KEY_ORDER_ID, orderId);
        }
        if (expertId != null) {
            ThreadContext.put(KEY_EXPERT_ID, expertId);
        }
    }
    
    /**
     * Set individual context values.
     */
    public static void setUserId(String userId) {
        if (userId != null) {
            ThreadContext.put(KEY_USER_ID, userId);
        }
    }
    
    public static void setOrderId(String orderId) {
        if (orderId != null) {
            ThreadContext.put(KEY_ORDER_ID, orderId);
        }
    }
    
    public static void setExpertId(String expertId) {
        if (expertId != null) {
            ThreadContext.put(KEY_EXPERT_ID, expertId);
        }
    }
    
    /**
     * Clear all logging context. Call at the end of request processing.
     */
    public static void clearContext() {
        ThreadContext.clearAll();
    }
    
    // =========================================================================
    // Logging Methods
    // =========================================================================
    
    /**
     * Log at DEBUG level with structured data.
     */
    public static void debug(String message) {
        logger.debug(message);
    }
    
    public static void debug(String message, Map<String, Object> data) {
        setDataContext(data);
        logger.debug(message);
        clearDataContext();
    }
    
    /**
     * Log at INFO level with structured data.
     */
    public static void info(String message) {
        logger.info(message);
    }
    
    public static void info(String message, Map<String, Object> data) {
        setDataContext(data);
        logger.info(message);
        clearDataContext();
    }
    
    /**
     * Log at WARN level with structured data.
     */
    public static void warn(String message) {
        logger.warn(message);
    }
    
    public static void warn(String message, Map<String, Object> data) {
        setDataContext(data);
        logger.warn(message);
        clearDataContext();
    }
    
    /**
     * Log at ERROR level with structured data.
     */
    public static void error(String message) {
        logger.error(message);
    }
    
    public static void error(String message, Throwable t) {
        logger.error(message, t);
    }
    
    public static void error(String message, Map<String, Object> data) {
        setDataContext(data);
        logger.error(message);
        clearDataContext();
    }
    
    public static void error(String message, Throwable t, Map<String, Object> data) {
        setDataContext(data);
        logger.error(message, t);
        clearDataContext();
    }
    
    // =========================================================================
    // Convenience Methods for Common Patterns
    // =========================================================================
    
    /**
     * Log the start of an operation with timing context.
     * Returns start time for use with logOperationEnd.
     */
    public static long logOperationStart(String operation) {
        info(operation + "_started");
        return System.currentTimeMillis();
    }
    
    public static long logOperationStart(String operation, Map<String, Object> data) {
        info(operation + "_started", data);
        return System.currentTimeMillis();
    }
    
    /**
     * Log the end of an operation with duration.
     */
    public static void logOperationEnd(String operation, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        info(operation + "_completed", Map.of("durationMs", duration));
    }
    
    public static void logOperationEnd(String operation, long startTime, Map<String, Object> additionalData) {
        long duration = System.currentTimeMillis() - startTime;
        Map<String, Object> data = new HashMap<>(additionalData);
        data.put("durationMs", duration);
        info(operation + "_completed", data);
    }
    
    /**
     * Log an operation failure with duration and error details.
     */
    public static void logOperationFailed(String operation, long startTime, Throwable t) {
        long duration = System.currentTimeMillis() - startTime;
        error(operation + "_failed", t, Map.of("durationMs", duration));
    }
    
    public static void logOperationFailed(String operation, long startTime, String errorMessage) {
        long duration = System.currentTimeMillis() - startTime;
        error(operation + "_failed", Map.of("durationMs", duration, "errorMessage", errorMessage));
    }
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    private static void setDataContext(Map<String, Object> data) {
        if (data != null && !data.isEmpty()) {
            ThreadContext.put(KEY_DATA, gson.toJson(data));
        }
    }
    
    private static void clearDataContext() {
        ThreadContext.remove(KEY_DATA);
    }
    
    /**
     * Create a mutable map with the given key-value pairs.
     * Convenience method for creating log data.
     */
    public static Map<String, Object> data(Object... keyValuePairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }
}
