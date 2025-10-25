package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class FreeAstrologyApiTransformationTest {
    
    private AstrologyService astrologyService;
    private Gson gson;
    private Method transformMethod;
    
    @BeforeEach
    public void setUp() throws Exception {
        astrologyService = new AstrologyService();
        gson = new Gson();
        
        // Use reflection to access the private transform method
        transformMethod = AstrologyService.class.getDeclaredMethod(
            "transformThirdPartyResponseToExpectedFormat", String.class);
        transformMethod.setAccessible(true);
    }
    
    @Test
    public void testTransformFreeAstrologyApiResponse() throws Exception {
        // Sample response from Free Astrology API
        String freeAstrologyResponse = """
            {
                "statusCode": 200,
                "input": {
                    "year": 1993,
                    "month": 1,
                    "date": 12,
                    "hours": 10,
                    "minutes": 15,
                    "seconds": 0,
                    "latitude": 28.65195,
                    "longitude": 77.23149,
                    "timezone": 5.5,
                    "settings": {
                        "observation_point": "topocentric",
                        "ayanamsha": "lahiri"
                    }
                },
                "output": [
                    {
                        "0": {
                            "name": "Ascendant",
                            "fullDegree": 322.29098802770164,
                            "normDegree": 22.290988027701644,
                            "isRetro": "false",
                            "current_sign": 11
                        },
                        "1": {
                            "name": "Sun",
                            "fullDegree": 268.2374964826742,
                            "normDegree": 28.237496482674203,
                            "isRetro": "false",
                            "current_sign": 9,
                            "house_number": 11
                        },
                        "2": {
                            "name": "Moon",
                            "fullDegree": 138.16891625282082,
                            "normDegree": 18.16891625282082,
                            "isRetro": "false",
                            "current_sign": 5,
                            "house_number": 7
                        },
                        "3": {
                            "name": "Mars",
                            "fullDegree": 82.21320019121544,
                            "normDegree": 22.213200191215435,
                            "isRetro": "true",
                            "current_sign": 3,
                            "house_number": 5
                        },
                        "4": {
                            "name": "Mercury",
                            "fullDegree": 261.1493864504042,
                            "normDegree": 21.149386450404222,
                            "isRetro": "false",
                            "current_sign": 9,
                            "house_number": 11
                        },
                        "5": {
                            "name": "Jupiter",
                            "fullDegree": 170.49284248875065,
                            "normDegree": 20.492842488750654,
                            "isRetro": "false",
                            "current_sign": 6,
                            "house_number": 8
                        },
                        "6": {
                            "name": "Venus",
                            "fullDegree": 315.1265177288521,
                            "normDegree": 15.126517728852093,
                            "isRetro": "false",
                            "current_sign": 11,
                            "house_number": 1
                        },
                        "7": {
                            "name": "Saturn",
                            "fullDegree": 293.8373593029421,
                            "normDegree": 23.837359302942104,
                            "isRetro": "false",
                            "current_sign": 10,
                            "house_number": 12
                        },
                        "8": {
                            "name": "Rahu",
                            "fullDegree": 236.0722337450286,
                            "normDegree": 26.072233745028598,
                            "isRetro": "true",
                            "current_sign": 8,
                            "house_number": 10
                        },
                        "9": {
                            "name": "Ketu",
                            "fullDegree": 56.0722337450286,
                            "normDegree": 26.072233745028598,
                            "isRetro": "true",
                            "current_sign": 2,
                            "house_number": 4
                        },
                        "10": {
                            "name": "Uranus",
                            "fullDegree": 264.57719166006655,
                            "normDegree": 24.577191660066546,
                            "isRetro": "false",
                            "current_sign": 9,
                            "house_number": 11
                        },
                        "11": {
                            "name": "Neptune",
                            "fullDegree": 265.03146323545394,
                            "normDegree": 25.03146323545394,
                            "isRetro": "false",
                            "current_sign": 9,
                            "house_number": 11
                        },
                        "12": {
                            "name": "Pluto",
                            "fullDegree": 211.15689057805014,
                            "normDegree": 1.1568905780501382,
                            "isRetro": "false",
                            "current_sign": 8,
                            "house_number": 10
                        },
                        "13": {
                            "name": "ayanamsa",
                            "value": 23.755654173892367
                        },
                        "debug": {
                            "observation_point": "topocentric",
                            "ayanamsa": "lahiri"
                        }
                    },
                    {
                        "Ascendant": {
                            "current_sign": 11,
                            "fullDegree": 322.29098802770164,
                            "normDegree": 22.290988027701644,
                            "isRetro": "false"
                        },
                        "Sun": {
                            "current_sign": 9,
                            "house_number": 11,
                            "fullDegree": 268.2374964826742,
                            "normDegree": 28.237496482674203,
                            "isRetro": "false"
                        },
                        "Moon": {
                            "current_sign": 5,
                            "house_number": 7,
                            "fullDegree": 138.16891625282082,
                            "normDegree": 18.16891625282082,
                            "isRetro": "false"
                        },
                        "Mars": {
                            "current_sign": 3,
                            "house_number": 5,
                            "fullDegree": 82.21320019121544,
                            "normDegree": 22.213200191215435,
                            "isRetro": "true"
                        },
                        "Mercury": {
                            "current_sign": 9,
                            "house_number": 11,
                            "fullDegree": 261.1493864504042,
                            "normDegree": 21.149386450404222,
                            "isRetro": "false"
                        },
                        "Jupiter": {
                            "current_sign": 6,
                            "house_number": 8,
                            "fullDegree": 170.49284248875065,
                            "normDegree": 20.492842488750654,
                            "isRetro": "false"
                        },
                        "Venus": {
                            "current_sign": 11,
                            "house_number": 1,
                            "fullDegree": 315.1265177288521,
                            "normDegree": 15.126517728852093,
                            "isRetro": "false"
                        },
                        "Saturn": {
                            "current_sign": 10,
                            "house_number": 12,
                            "fullDegree": 293.8373593029421,
                            "normDegree": 23.837359302942104,
                            "isRetro": "false"
                        },
                        "Rahu": {
                            "current_sign": 8,
                            "house_number": 10,
                            "fullDegree": 236.0722337450286,
                            "normDegree": 26.072233745028598,
                            "isRetro": "true"
                        },
                        "Ketu": {
                            "current_sign": 2,
                            "house_number": 4,
                            "fullDegree": 56.0722337450286,
                            "normDegree": 26.072233745028598,
                            "isRetro": "true"
                        },
                        "Uranus": {
                            "current_sign": 9,
                            "house_number": 11,
                            "fullDegree": 264.57719166006655,
                            "normDegree": 24.577191660066546,
                            "isRetro": "false"
                        },
                        "Neptune": {
                            "current_sign": 9,
                            "house_number": 11,
                            "fullDegree": 265.03146323545394,
                            "normDegree": 25.03146323545394,
                            "isRetro": "false"
                        },
                        "Pluto": {
                            "current_sign": 8,
                            "house_number": 10,
                            "fullDegree": 211.15689057805014,
                            "normDegree": 1.1568905780501382,
                            "isRetro": "false"
                        }
                    }
                ]
            }
            """;
        
        // Call the transform method
        String transformedResponse = (String) transformMethod.invoke(astrologyService, freeAstrologyResponse);
        
        assertNotNull(transformedResponse);
        assertFalse(transformedResponse.isEmpty());
        
        // Parse the transformed response
        JsonObject jsonResponse = JsonParser.parseString(transformedResponse).getAsJsonObject();
        
        // Verify that all expected planets are present
        assertTrue(jsonResponse.has("ascendant"), "Should contain ascendant");
        assertTrue(jsonResponse.has("sun"), "Should contain sun");
        assertTrue(jsonResponse.has("moon"), "Should contain moon");
        assertTrue(jsonResponse.has("mars"), "Should contain mars");
        assertTrue(jsonResponse.has("mercury"), "Should contain mercury");
        assertTrue(jsonResponse.has("jupiter"), "Should contain jupiter");
        assertTrue(jsonResponse.has("venus"), "Should contain venus");
        assertTrue(jsonResponse.has("saturn"), "Should contain saturn");
        assertTrue(jsonResponse.has("rahu"), "Should contain rahu");
        assertTrue(jsonResponse.has("ketu"), "Should contain ketu");
        assertTrue(jsonResponse.has("uranus"), "Should contain uranus");
        assertTrue(jsonResponse.has("neptune"), "Should contain neptune");
        assertTrue(jsonResponse.has("pluto"), "Should contain pluto");
        
        // Verify structure of planet data
        JsonObject ascendant = jsonResponse.getAsJsonObject("ascendant");
        assertTrue(ascendant.has("sign"), "Ascendant should have sign");
        assertTrue(ascendant.has("is_retrograde"), "Ascendant should have is_retrograde");
        assertTrue(ascendant.has("longitude"), "Ascendant should have longitude");
        
        assertEquals(11, ascendant.get("sign").getAsInt(), "Ascendant sign should be 11");
        assertEquals(false, ascendant.get("is_retrograde").getAsBoolean(), "Ascendant should not be retrograde");
        assertEquals(22.290988027701644, ascendant.get("longitude").getAsDouble(), 0.0001, "Ascendant longitude should match");
        
        // Verify Mars retrograde status
        JsonObject mars = jsonResponse.getAsJsonObject("mars");
        assertEquals(3, mars.get("sign").getAsInt(), "Mars sign should be 3");
        assertEquals(true, mars.get("is_retrograde").getAsBoolean(), "Mars should be retrograde");
        assertEquals(22.213200191215435, mars.get("longitude").getAsDouble(), 0.0001, "Mars longitude should match");
        
        // Verify Sun data
        JsonObject sun = jsonResponse.getAsJsonObject("sun");
        assertEquals(9, sun.get("sign").getAsInt(), "Sun sign should be 9");
        assertEquals(false, sun.get("is_retrograde").getAsBoolean(), "Sun should not be retrograde");
        assertEquals(28.237496482674203, sun.get("longitude").getAsDouble(), 0.0001, "Sun longitude should match");
    }
    
    @Test
    public void testTransformWithInvalidResponse() throws Exception {
        // Test with invalid JSON
        String invalidResponse = "invalid json";
        
        Exception exception = assertThrows(Exception.class, () -> {
            transformMethod.invoke(astrologyService, invalidResponse);
        });
        
        // The exception will be wrapped in InvocationTargetException
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Failed to transform third-party API response"));
    }
    
    @Test
    public void testTransformWithMissingOutput() throws Exception {
        // Test with response missing output array
        String responseWithoutOutput = """
            {
                "statusCode": 200,
                "input": {}
            }
            """;
        
        Exception exception = assertThrows(Exception.class, () -> {
            transformMethod.invoke(astrologyService, responseWithoutOutput);
        });
        
        // Just verify that an exception was thrown
        assertNotNull(exception);
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }
    
    @Test
    public void testTransformWithInsufficientOutputElements() throws Exception {
        // Test with output array having only one element
        String responseWithOneElement = """
            {
                "statusCode": 200,
                "output": [
                    {
                        "0": {"name": "Ascendant"}
                    }
                ]
            }
            """;
        
        Exception exception = assertThrows(Exception.class, () -> {
            transformMethod.invoke(astrologyService, responseWithOneElement);
        });
        
        // Just verify that an exception was thrown
        assertNotNull(exception);
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }
}
