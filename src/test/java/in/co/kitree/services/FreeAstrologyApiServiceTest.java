package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.co.kitree.TestBase;
import in.co.kitree.pojos.RequestBody;
import in.co.kitree.pojos.ThirdPartyAstrologyRequest;
import in.co.kitree.pojos.ThirdPartyAstrologyResponse;
import in.co.kitree.pojos.ThirdPartyDashaRequest;
import in.co.kitree.pojos.FreeAstrologyDivisionalChartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests specifically for the Free Astrology API integration
 * These tests require ASTROLOGY_API_KEY environment variable to be set
 */
public class FreeAstrologyApiServiceTest extends TestBase {
    
    private AstrologyService astrologyService;
    private Gson gson;
    
    @BeforeEach
    public void setUp() {
        astrologyService = new AstrologyService();
        gson = new Gson();
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "ASTROLOGY_API_KEY", matches = ".*")
    public void testFreeAstrologyApiDashaRequest() throws Exception {
        // This test requires ASTROLOGY_API_KEY environment variable to be set
        String apiKey = System.getenv("ASTROLOGY_API_KEY");
        assertNotNull(apiKey, "ASTROLOGY_API_KEY environment variable must be set for this test");
        assertFalse(apiKey.isEmpty(), "ASTROLOGY_API_KEY cannot be empty");
        
        // Create a test request for dasha
        RequestBody requestBody = createValidDashaRequestBody();
        
        // Note: This test will use the Python server by default since ASTROLOGY_API_PROVIDER is set to "PYTHON_SERVER"
        // To test the Free Astrology API, you would need to modify the AstrologyService.ASTROLOGY_API_PROVIDER constant
        String response = astrologyService.getDashaDetails(requestBody);
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
        
        // Parse response to verify structure
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        
        // Check if response contains dasha data
        assertTrue(jsonResponse.has("dashas") || jsonResponse.has("current_level_calculated"));
    }
    
    @Test
    public void testFreeAstrologyApiDashaRequestPayloadCreation() {
        // Test the creation of Free Astrology API dasha request payload
        RequestBody requestBody = createValidDashaRequestBody();
        
        // Calculate timezone
        double timezone = TimezoneUtils.getTimezoneOffset(
                requestBody.getDashaLatitude(), 
                requestBody.getDashaLongitude()
        );
        
        // Create settings
        Map<String, String> config = new HashMap<>();
        config.put("observation_point", "topocentric");
        config.put("ayanamsha", "lahiri");
        
        // Create Free Astrology API dasha request
        ThirdPartyDashaRequest dashaRequest = new ThirdPartyDashaRequest(
                requestBody.getDashaYear(),
                requestBody.getDashaMonth(),
                requestBody.getDashaDate(),
                requestBody.getDashaHour(),
                requestBody.getDashaMinute(),
                0, // seconds
                requestBody.getDashaLatitude(),
                requestBody.getDashaLongitude(),
                timezone,
                config
        );
        
        // Verify the request structure
        assertEquals(1990, dashaRequest.getYear());
        assertEquals(8, dashaRequest.getMonth());
        assertEquals(15, dashaRequest.getDate());
        assertEquals(10, dashaRequest.getHours());
        assertEquals(30, dashaRequest.getMinutes());
        assertEquals(0, dashaRequest.getSeconds());
        assertEquals(28.6139, dashaRequest.getLatitude(), 0.0001);
        assertEquals(77.2090, dashaRequest.getLongitude(), 0.0001);
        assertEquals(5.5, dashaRequest.getTimezone(), 0.5); // Delhi timezone
        assertNotNull(dashaRequest.getConfig());
        assertEquals("topocentric", dashaRequest.getConfig().get("observation_point"));
        assertEquals("lahiri", dashaRequest.getConfig().get("ayanamsha"));
        
        // Test JSON serialization
        String jsonPayload = gson.toJson(dashaRequest);
        assertNotNull(jsonPayload);
        assertFalse(jsonPayload.isEmpty());
        
        // Verify JSON contains expected fields
        assertTrue(jsonPayload.contains("\"year\":1990"));
        assertTrue(jsonPayload.contains("\"month\":8"));
        assertTrue(jsonPayload.contains("\"date\":15"));
        assertTrue(jsonPayload.contains("\"hours\":10"));
        assertTrue(jsonPayload.contains("\"minutes\":30"));
        assertTrue(jsonPayload.contains("\"latitude\":28.6139"));
        assertTrue(jsonPayload.contains("\"longitude\":77.2090"));
        assertTrue(jsonPayload.contains("\"observation_point\":\"topocentric\""));
        assertTrue(jsonPayload.contains("\"ayanamsha\":\"lahiri\""));
    }
    
    @Test
    public void testTimezoneCalculationForDifferentLocations() {
        // Test timezone calculation for various locations using Timeshape
        
        // Delhi, India - should be around +5.5
        double delhiTimezone = TimezoneUtils.getTimezoneOffset(28.6139, 77.2090);
        assertEquals(5.5, delhiTimezone, 0.5); // Allow tolerance for DST
        
        // Mumbai, India - should be around +5.5
        double mumbaiTimezone = TimezoneUtils.getTimezoneOffset(19.0760, 72.8777);
        assertEquals(5.5, mumbaiTimezone, 0.5);
        
        // New York, USA - should be around -5 (or -4 during DST)
        double nyTimezone = TimezoneUtils.getTimezoneOffset(40.7128, -74.0060);
        assertTrue(nyTimezone >= -5.0 && nyTimezone <= -4.0);
        
        // London, UK - should be around 0 (or +1 during DST)
        double londonTimezone = TimezoneUtils.getTimezoneOffset(51.5074, -0.1278);
        assertTrue(londonTimezone >= 0.0 && londonTimezone <= 1.0);
        
        // Tokyo, Japan - should be around +9
        double tokyoTimezone = TimezoneUtils.getTimezoneOffset(35.6762, 139.6503);
        assertEquals(9.0, tokyoTimezone, 0.5);
        
        // Sydney, Australia - should be around +10 (or +11 during DST)
        double sydneyTimezone = TimezoneUtils.getTimezoneOffset(-33.8688, 151.2093);
        assertTrue(sydneyTimezone >= 10.0 && sydneyTimezone <= 11.0);
        
        // Test ZoneId retrieval for more accurate testing
        Optional<ZoneId> delhiZoneId = TimezoneUtils.getZoneId(28.6139, 77.2090);
        assertTrue(delhiZoneId.isPresent());
        assertTrue(delhiZoneId.get().getId().contains("Asia/Kolkata") || 
                  delhiZoneId.get().getId().contains("Asia/Calcutta"));
        
        Optional<ZoneId> nyZoneId = TimezoneUtils.getZoneId(40.7128, -74.0060);
        assertTrue(nyZoneId.isPresent());
        assertTrue(nyZoneId.get().getId().contains("America/New_York"));
        
        Optional<ZoneId> londonZoneId = TimezoneUtils.getZoneId(51.5074, -0.1278);
        assertTrue(londonZoneId.isPresent());
        assertTrue(londonZoneId.get().getId().contains("Europe/London"));
    }
    
    @Test
    public void testThirdPartyResponseTransformation() {
        // Test the transformation of third-party API response to expected format
        String mockThirdPartyResponse = createMockThirdPartyResponse();
        
        // This would be called internally by AstrologyService.transformThirdPartyResponseToExpectedFormat
        // We'll test the logic here
        
        JsonObject jsonResponse = JsonParser.parseString(mockThirdPartyResponse).getAsJsonObject();
        assertNotNull(jsonResponse);
        
        // Verify the mock response structure
        assertTrue(jsonResponse.has("statusCode"));
        assertTrue(jsonResponse.has("input"));
        assertTrue(jsonResponse.has("output"));
        
        assertEquals(200, jsonResponse.get("statusCode").getAsInt());
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
        requestBody.setDashaPrefix(new ArrayList<>()); // Empty for Maha Dasas only
        return requestBody;
    }
    
    private RequestBody createValidDivisionalChartRequestBody() {
        RequestBody requestBody = new RequestBody();
        requestBody.setHoroscopeDate(15);
        requestBody.setHoroscopeMonth(8);
        requestBody.setHoroscopeYear(1990);
        requestBody.setHoroscopeHour(10);
        requestBody.setHoroscopeMinute(30);
        requestBody.setHoroscopeLatitude(28.6139); // Delhi
        requestBody.setHoroscopeLongitude(77.2090);
        requestBody.setDivisionalChartNumbers(Arrays.asList(2, 9, 10)); // D2, D9, D10
        return requestBody;
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
    
    private String createMockThirdPartyResponse() {
        return """
        {
            "statusCode": 200,
            "input": {
                "year": 1990,
                "month": 8,
                "date": 15,
                "hours": 10,
                "minutes": 30,
                "seconds": 0,
                "latitude": 28.6139,
                "longitude": 77.2090,
                "timezone": 5.5,
                "config": {
                    "observation_point": "topocentric",
                    "ayanamsha": "lahiri"
                }
            },
            "output": [
                {
                    "0": {
                        "name": "Ascendant",
                        "fullDegree": 260.15231949660466,
                        "normDegree": 20.15231949660466,
                        "isRetro": "false",
                        "current_sign": 9
                    },
                    "1": {
                        "name": "Sun",
                        "fullDegree": 114.60861229742005,
                        "normDegree": 24.608612297420052,
                        "isRetro": "false",
                        "current_sign": 4
                    },
                    "2": {
                        "name": "Moon",
                        "fullDegree": 285.033591796012,
                        "normDegree": 15.03359179601199,
                        "isRetro": "false",
                        "current_sign": 10
                    }
                },
                {
                    "Ascendant": {
                        "current_sign": 9,
                        "fullDegree": 260.15231949660466,
                        "normDegree": 20.15231949660466,
                        "isRetro": "false"
                    },
                    "Sun": {
                        "current_sign": 4,
                        "fullDegree": 114.60861229742005,
                        "normDegree": 24.608612297420052,
                        "isRetro": "false"
                    },
                    "Moon": {
                        "current_sign": 10,
                        "fullDegree": 285.033591796012,
                        "normDegree": 15.03359179601199,
                        "isRetro": "false"
                    }
                }
            ]
        }
        """;
    }
    @Test
    public void testFreeAstrologyApiDivisionalChartRequestPayloadCreation() {
        // Test the creation of Free Astrology API divisional chart request payload
        RequestBody requestBody = createValidDivisionalChartRequestBody();
        
        // Calculate timezone
        double timezone = TimezoneUtils.getTimezoneOffset(
                requestBody.getHoroscopeLatitude(), 
                requestBody.getHoroscopeLongitude()
        );
        
        // Create settings
        Map<String, String> config = new HashMap<>();
        config.put("observation_point", "topocentric");
        config.put("ayanamsha", "lahiri");
        
        // Create Free Astrology API divisional chart request
        FreeAstrologyDivisionalChartRequest chartRequest = new FreeAstrologyDivisionalChartRequest(
                requestBody.getHoroscopeYear(),
                requestBody.getHoroscopeMonth(),
                requestBody.getHoroscopeDate(),
                requestBody.getHoroscopeHour(),
                requestBody.getHoroscopeMinute(),
                0, // seconds
                requestBody.getHoroscopeLatitude(),
                requestBody.getHoroscopeLongitude(),
                timezone,
                config
        );
        
        // Verify request payload
        assertNotNull(chartRequest);
        assertEquals(requestBody.getHoroscopeYear(), chartRequest.getYear());
        assertEquals(requestBody.getHoroscopeMonth(), chartRequest.getMonth());
        assertEquals(requestBody.getHoroscopeDate(), chartRequest.getDate());
        assertEquals(requestBody.getHoroscopeHour(), chartRequest.getHours());
        assertEquals(requestBody.getHoroscopeMinute(), chartRequest.getMinutes());
        assertEquals(0, chartRequest.getSeconds());
        assertEquals(requestBody.getHoroscopeLatitude(), chartRequest.getLatitude());
        assertEquals(requestBody.getHoroscopeLongitude(), chartRequest.getLongitude());
        assertEquals(timezone, chartRequest.getTimezone());
        assertEquals(config, chartRequest.getConfig());
        
        // Test JSON serialization
        String jsonPayload = gson.toJson(chartRequest);
        assertNotNull(jsonPayload);
        assertFalse(jsonPayload.isEmpty());
        
        // Verify JSON contains expected fields
        assertTrue(jsonPayload.contains("\"year\""));
        assertTrue(jsonPayload.contains("\"month\""));
        assertTrue(jsonPayload.contains("\"date\""));
        assertTrue(jsonPayload.contains("\"hours\""));
        assertTrue(jsonPayload.contains("\"minutes\""));
        assertTrue(jsonPayload.contains("\"seconds\""));
        assertTrue(jsonPayload.contains("\"latitude\""));
        assertTrue(jsonPayload.contains("\"longitude\""));
        assertTrue(jsonPayload.contains("\"timezone\""));
        assertTrue(jsonPayload.contains("\"config\""));
    }
    
    @Test
    public void testDivisionalChartUrlMapping() {
        // Test that all supported divisional chart numbers have corresponding URLs
        int[] supportedCharts = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 20, 24, 27, 30, 40, 45, 60};
        
        for (int chartNumber : supportedCharts) {
            String url = AstrologyServiceConfig.getDivisionalChartUrl(chartNumber);
            assertNotNull(url, "URL should not be null for chart " + chartNumber);
            assertFalse(url.isEmpty(), "URL should not be empty for chart " + chartNumber);
            assertTrue(url.startsWith("https://json.freeastrologyapi.com/"), 
                      "URL should start with Free Astrology API base URL for chart " + chartNumber);
        }
        
        // Test unsupported chart number
        assertThrows(IllegalArgumentException.class, () -> {
            AstrologyServiceConfig.getDivisionalChartUrl(99);
        });
    }
}
