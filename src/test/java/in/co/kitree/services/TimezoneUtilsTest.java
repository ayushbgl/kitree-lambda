package in.co.kitree.services;

import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests specifically for TimezoneUtils with Timeshape integration
 */
public class TimezoneUtilsTest {
    
    @Test
    public void testTimeshapeIntegration() {
        // Test that Timeshape is working correctly
        
        // Delhi, India - should return Asia/Kolkata or similar
        Optional<ZoneId> delhiZoneId = TimezoneUtils.getZoneId(28.6139, 77.2090);
        assertTrue(delhiZoneId.isPresent(), "Delhi timezone should be found");
        String delhiId = delhiZoneId.get().getId();
        assertTrue(delhiId.contains("Asia"), "Delhi should be in Asia timezone");
        
        // New York, USA - should return America/New_York
        Optional<ZoneId> nyZoneId = TimezoneUtils.getZoneId(40.7128, -74.0060);
        assertTrue(nyZoneId.isPresent(), "New York timezone should be found");
        String nyId = nyZoneId.get().getId();
        assertTrue(nyId.contains("America"), "New York should be in America timezone");
        
        // London, UK - should return Europe/London
        Optional<ZoneId> londonZoneId = TimezoneUtils.getZoneId(51.5074, -0.1278);
        assertTrue(londonZoneId.isPresent(), "London timezone should be found");
        String londonId = londonZoneId.get().getId();
        assertTrue(londonId.contains("Europe"), "London should be in Europe timezone");
    }
    
    @Test
    public void testTimezoneOffsetCalculation() {
        // Test timezone offset calculations
        
        // Delhi should be around +5.5 hours
        double delhiOffset = TimezoneUtils.getTimezoneOffset(28.6139, 77.2090);
        assertEquals(5.5, delhiOffset, 0.1, "Delhi should be UTC+5:30");
        
        // New York should be around -5 or -4 hours (depending on DST)
        double nyOffset = TimezoneUtils.getTimezoneOffset(40.7128, -74.0060);
        assertTrue(nyOffset >= -5.0 && nyOffset <= -4.0, 
                  "New York should be UTC-5 or UTC-4 (DST)");
        
        // London should be around 0 or +1 hours (depending on DST)
        double londonOffset = TimezoneUtils.getTimezoneOffset(51.5074, -0.1278);
        assertTrue(londonOffset >= 0.0 && londonOffset <= 1.0, 
                  "London should be UTC+0 or UTC+1 (DST)");
    }
    
    @Test
    public void testTimezoneIdRetrieval() {
        // Test timezone ID retrieval
        
        String delhiTimezoneId = TimezoneUtils.getTimezoneId(28.6139, 77.2090);
        assertNotNull(delhiTimezoneId);
        assertTrue(delhiTimezoneId.contains("Asia") || delhiTimezoneId.contains("+05:30"));
        
        String nyTimezoneId = TimezoneUtils.getTimezoneId(40.7128, -74.0060);
        assertNotNull(nyTimezoneId);
        assertTrue(nyTimezoneId.contains("America") || 
                  nyTimezoneId.contains("-05:00") || 
                  nyTimezoneId.contains("-04:00"));
        
        String londonTimezoneId = TimezoneUtils.getTimezoneId(51.5074, -0.1278);
        assertNotNull(londonTimezoneId);
        assertTrue(londonTimezoneId.contains("Europe") || 
                  londonTimezoneId.contains("+00:00") || 
                  londonTimezoneId.contains("+01:00"));
    }
    
    @Test
    public void testEdgeCases() {
        // Test edge cases and invalid coordinates
        
        // Test coordinates in the middle of the ocean (should fallback)
        Optional<ZoneId> oceanZoneId = TimezoneUtils.getZoneId(0.0, 0.0);
        // This might or might not return a timezone depending on Timeshape data
        
        // Test extreme coordinates
        Optional<ZoneId> northPoleZoneId = TimezoneUtils.getZoneId(90.0, 0.0);
        // North pole might not have a specific timezone
        
        // Test that methods don't throw exceptions
        assertDoesNotThrow(() -> TimezoneUtils.getTimezoneOffset(0.0, 0.0));
        assertDoesNotThrow(() -> TimezoneUtils.getTimezoneId(0.0, 0.0));
        assertDoesNotThrow(() -> TimezoneUtils.getZoneOffset(0.0, 0.0));
        assertDoesNotThrow(() -> TimezoneUtils.getZoneId(0.0, 0.0));
    }
}
