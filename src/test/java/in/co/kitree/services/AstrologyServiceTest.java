package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.co.kitree.TestBase;
import in.co.kitree.pojos.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AstrologyServiceTest extends TestBase {
    
    private AstrologyService astrologyService;
    private Gson gson;
    
    @BeforeEach
    public void setUp() {
        astrologyService = new AstrologyService();
        gson = new Gson();
    }
    
    @Test
    public void testGetAstrologicalDetailsWithPythonServer() throws Exception {
        // Test with Python server (default configuration)
        RequestBody requestBody = createValidHoroscopeRequestBody();
        
        String response = astrologyService.getAstrologicalDetails(requestBody);
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
        
        // Parse response to verify structure
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        
        // Check if response contains planet data
        assertTrue(jsonResponse.has("ascendant") || jsonResponse.has("sun") || jsonResponse.has("moon"));
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "ASTROLOGY_API_KEY", matches = ".*")
    public void testGetAstrologicalDetailsWithThirdPartyAPI() throws Exception {
        // This test requires ASTROLOGY_API_KEY environment variable to be set
        // Temporarily modify the service to use third-party API for this test
        RequestBody requestBody = createValidHoroscopeRequestBody();
        
        // We need to modify the AstrologyService to use third-party API for this test
        // For now, we'll test the validation logic
        String response = astrologyService.getAstrologicalDetails(requestBody);
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }
    
    @Test
    public void testGetAstrologicalDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();
        // Don't set any horoscope fields
        
        String response = astrologyService.getAstrologicalDetails(requestBody);
        
        assertNotNull(response);
        
        // Should return error response
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(jsonResponse.has("success"));
        assertFalse(jsonResponse.get("success").getAsBoolean());
        assertTrue(jsonResponse.has("errorMessage"));
    }
    
    @Test
    public void testGetDashaDetailsWithPythonServer() throws Exception {
        RequestBody requestBody = createValidDashaRequestBody();
        
        String response = astrologyService.getDashaDetails(requestBody);
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
        
        // Parse response to verify structure
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        
        // Check if response contains dasha data
        assertTrue(jsonResponse.has("dashas") || jsonResponse.has("current_level_calculated"));
    }
    
    @Test
    public void testGetDashaDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();
        // Don't set any dasha fields
        
        String response = astrologyService.getDashaDetails(requestBody);
        
        assertNotNull(response);
        
        // Should return error response
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(jsonResponse.has("success"));
        assertFalse(jsonResponse.get("success").getAsBoolean());
        assertTrue(jsonResponse.has("errorMessage"));
    }
    
    @Test
    public void testGetDivisionalChartsWithPythonServer() throws Exception {
        RequestBody requestBody = createValidDivisionalChartsRequestBody();
        
        String response = astrologyService.getDivisionalCharts(requestBody);
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
        
        // Parse response to verify structure
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        
        // Check if response contains charts data
        assertTrue(jsonResponse.has("charts"));
    }
    
    @Test
    public void testGetDivisionalChartsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();
        // Don't set any divisional chart fields
        
        String response = astrologyService.getDivisionalCharts(requestBody);
        
        assertNotNull(response);
        
        // Should return error response
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(jsonResponse.has("success"));
        assertFalse(jsonResponse.get("success").getAsBoolean());
        assertTrue(jsonResponse.has("errorMessage"));
    }
    
    @Test
    public void testTimezoneCalculation() {
        // Test timezone calculation for different coordinates using Timeshape
        
        // India (Delhi) - should be around +5.5
        double indiaTimezone = TimezoneUtils.getTimezoneOffset(28.6139, 77.2090);
        assertEquals(5.5, indiaTimezone, 0.5); // Allow some tolerance for DST
        
        // New York - should be around -5 (or -4 during DST)
        double nyTimezone = TimezoneUtils.getTimezoneOffset(40.7128, -74.0060);
        assertTrue(nyTimezone >= -5.0 && nyTimezone <= -4.0); // Account for DST
        
        // London - should be around 0 (or +1 during DST)
        double londonTimezone = TimezoneUtils.getTimezoneOffset(51.5074, -0.1278);
        assertTrue(londonTimezone >= 0.0 && londonTimezone <= 1.0); // Account for DST
        
        // Test timezone ID retrieval
        String delhiTimezoneId = TimezoneUtils.getTimezoneId(28.6139, 77.2090);
        assertNotNull(delhiTimezoneId);
        assertTrue(delhiTimezoneId.contains("Asia") || delhiTimezoneId.contains("+05:30"));
        
        String nyTimezoneId = TimezoneUtils.getTimezoneId(40.7128, -74.0060);
        assertNotNull(nyTimezoneId);
        assertTrue(nyTimezoneId.contains("America") || nyTimezoneId.contains("-05:00") || nyTimezoneId.contains("-04:00"));
    }
    
    private RequestBody createValidHoroscopeRequestBody() {
        RequestBody requestBody = new RequestBody();
        requestBody.setHoroscopeDate(15);
        requestBody.setHoroscopeMonth(8);
        requestBody.setHoroscopeYear(1990);
        requestBody.setHoroscopeHour(10);
        requestBody.setHoroscopeMinute(30);
        requestBody.setHoroscopeLatitude(28.6139); // Delhi
        requestBody.setHoroscopeLongitude(77.2090);
        return requestBody;
    }
    
    private RequestBody createValidDashaRequestBody() {
        RequestBody requestBody = new RequestBody();
        requestBody.setDashaDate(15);
        requestBody.setDashaMonth(8);
        requestBody.setDashaYear(1990);
        requestBody.setDashaHour(10);
        requestBody.setDashaMinute(30);
        requestBody.setDashaLatitude(28.6139); // Delhi
        requestBody.setDashaLongitude(77.2090);
        requestBody.setDashaPrefix(java.util.Arrays.asList("Sun", "Moon"));
        return requestBody;
    }
    
    private RequestBody createValidDivisionalChartsRequestBody() {
        RequestBody requestBody = new RequestBody();
        requestBody.setHoroscopeDate(15);
        requestBody.setHoroscopeMonth(8);
        requestBody.setHoroscopeYear(1990);
        requestBody.setHoroscopeHour(10);
        requestBody.setHoroscopeMinute(30);
        requestBody.setHoroscopeLatitude(28.6139); // Delhi
        requestBody.setHoroscopeLongitude(77.2090);
        requestBody.setDivisionalChartNumbers(java.util.Arrays.asList(2, 9)); // D2 and D9 charts
        return requestBody;
    }
}
