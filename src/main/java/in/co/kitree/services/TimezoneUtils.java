package in.co.kitree.services;

import net.iakovlev.timeshape.TimeZoneEngine;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class TimezoneUtils {
    
    // Singleton instance of TimeZoneEngine for performance
    private static volatile TimeZoneEngine timeZoneEngine;
    private static final Object lock = new Object();
    
    /**
     * Initialize the TimeZoneEngine singleton
     * This method is thread-safe and will only initialize once
     */
    private static TimeZoneEngine getTimeZoneEngine() {
        if (timeZoneEngine == null) {
            synchronized (lock) {
                if (timeZoneEngine == null) {
                    try {
                        // Initialize with accelerated geometry for better performance
                        timeZoneEngine = TimeZoneEngine.initialize(true);
                    } catch (Exception e) {
                        LoggingService.warn("timezone_engine_init_failed", java.util.Map.of("error", e.getMessage()));
                        // Fallback to non-accelerated version
                        try {
                            timeZoneEngine = TimeZoneEngine.initialize(false);
                        } catch (Exception e2) {
                            LoggingService.error("timezone_engine_init_fallback_failed", e2);
                            throw new RuntimeException("Could not initialize TimeZoneEngine", e2);
                        }
                    }
                }
            }
        }
        return timeZoneEngine;
    }
    
    /**
     * Get timezone offset in hours for given latitude and longitude using Timeshape
     * This provides accurate timezone detection based on actual timezone boundaries
     */
    public static double getTimezoneOffset(double latitude, double longitude) {
        try {
            TimeZoneEngine engine = getTimeZoneEngine();
            Optional<ZoneId> zoneIdOpt = engine.query(latitude, longitude);
            
            if (zoneIdOpt.isPresent()) {
                ZoneId zoneId = zoneIdOpt.get();
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                ZoneOffset offset = now.getOffset();
                
                // Convert offset to hours (including fractional hours)
                return offset.getTotalSeconds() / 3600.0;
            } else {
                // Fallback to simple longitude-based calculation if no timezone found
                return getFallbackTimezoneOffset(longitude);
            }
        } catch (Exception e) {
            LoggingService.error("timezone_get_offset_error", e);
            // Fallback to simple longitude-based calculation
            return getFallbackTimezoneOffset(longitude);
        }
    }

    /**
     * Get timezone ID for given latitude and longitude using Timeshape
     * Returns the actual timezone ID (e.g., "Asia/Kolkata", "America/New_York")
     */
    public static String getTimezoneId(double latitude, double longitude) {
        try {
            TimeZoneEngine engine = getTimeZoneEngine();
            Optional<ZoneId> zoneIdOpt = engine.query(latitude, longitude);
            
            if (zoneIdOpt.isPresent()) {
                return zoneIdOpt.get().getId();
            } else {
                // Fallback to offset-based timezone ID
                return getFallbackTimezoneId(longitude);
            }
        } catch (Exception e) {
            LoggingService.error("timezone_get_id_error", e);
            // Fallback to offset-based timezone ID
            return getFallbackTimezoneId(longitude);
        }
    }

    /**
     * Get timezone offset as ZoneOffset object using Timeshape
     */
    public static ZoneOffset getZoneOffset(double latitude, double longitude) {
        try {
            TimeZoneEngine engine = getTimeZoneEngine();
            Optional<ZoneId> zoneIdOpt = engine.query(latitude, longitude);
            
            if (zoneIdOpt.isPresent()) {
                ZoneId zoneId = zoneIdOpt.get();
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                return now.getOffset();
            } else {
                // Fallback to simple longitude-based calculation
                return getFallbackZoneOffset(longitude);
            }
        } catch (Exception e) {
            LoggingService.error("timezone_get_zone_offset_error", e);
            // Fallback to simple longitude-based calculation
            return getFallbackZoneOffset(longitude);
        }
    }

    /**
     * Get ZoneId object for given latitude and longitude using Timeshape
     * This is the most accurate method as it returns the actual ZoneId
     */
    public static Optional<ZoneId> getZoneId(double latitude, double longitude) {
        try {
            TimeZoneEngine engine = getTimeZoneEngine();
            return engine.query(latitude, longitude);
        } catch (Exception e) {
            LoggingService.error("timezone_get_zoneid_error", e);
            return Optional.empty();
        }
    }

    /**
     * Fallback method for timezone offset calculation based on longitude
     * Used when Timeshape fails or doesn't find a timezone
     */
    private static double getFallbackTimezoneOffset(double longitude) {
        // Simple approximation based on longitude
        // Each 15 degrees of longitude represents 1 hour difference
        double timezoneOffset = longitude / 15.0;
        
        // Round to nearest 0.5 hour (30 minutes)
        return Math.round(timezoneOffset * 2.0) / 2.0;
    }
    
    /**
     * Fallback method for timezone ID based on longitude
     * Used when Timeshape fails or doesn't find a timezone
     */
    private static String getFallbackTimezoneId(double longitude) {
        double offset = getFallbackTimezoneOffset(longitude);
        
        // Convert to ZoneOffset format (e.g., +05:30 for India)
        int hours = (int) offset;
        int minutes = (int) ((offset - hours) * 60);
        
        ZoneOffset zoneOffset = ZoneOffset.ofHoursMinutes(hours, minutes);
        return zoneOffset.getId();
    }
    
    /**
     * Fallback method for ZoneOffset based on longitude
     * Used when Timeshape fails or doesn't find a timezone
     */
    private static ZoneOffset getFallbackZoneOffset(double longitude) {
        double offset = getFallbackTimezoneOffset(longitude);
        int hours = (int) offset;
        int minutes = (int) ((offset - hours) * 60);
        return ZoneOffset.ofHoursMinutes(hours, minutes);
    }
}
