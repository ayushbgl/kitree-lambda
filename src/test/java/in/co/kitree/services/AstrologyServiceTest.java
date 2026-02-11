package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.co.kitree.pojos.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AstrologyServiceTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockResponse;

    private AstrologyService astrologyService;
    private Gson gson;

    @BeforeEach
    void setUp() {
        astrologyService = new AstrologyService(mockHttpClient);
        gson = new Gson();
    }

    // ---- Missing-field validation tests (no HTTP calls) ----

    @Test
    void testGetAstrologicalDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getAstrologicalDetails(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required horoscope details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void testGetDashaDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getDashaDetails(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required dasha details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void testGetDivisionalChartsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getDivisionalCharts(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required divisional chart details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void testGetGocharDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getGocharDetails(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required gochar details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockHttpClient);
    }

    // ---- Successful API response tests (mocked HTTP) ----

    @Test
    void testGetAstrologicalDetails() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            stubHttpResponse(200, lambdaResponse(Map.of("ascendant", "Aries", "sun", "Leo", "moon", "Cancer")));

            String response = astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody());

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            assertTrue(json.has("ascendant"));
            assertTrue(json.has("sun"));
            verifyHttpCallTo("/get_horoscope");
        }
    }

    @Test
    void testGetDashaDetails() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            stubHttpResponse(200, lambdaResponse(Map.of("dashas", "Sun-Moon", "current_level_calculated", true)));

            String response = astrologyService.getDashaDetails(createValidDashaRequestBody());

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            assertTrue(json.has("dashas"));
            verifyHttpCallTo("/dasha");
        }
    }

    @Test
    void testGetDivisionalCharts() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            stubHttpResponse(200, lambdaResponse(Map.of("charts", Map.of("D2", "data", "D9", "data"))));

            String response = astrologyService.getDivisionalCharts(createValidDivisionalChartsRequestBody());

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            assertTrue(json.has("charts"));
            verifyHttpCallTo("/divisional_charts");
        }
    }

    @Test
    void testGetGocharDetails() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            stubHttpResponse(200, lambdaResponse(Map.of("gochar", Map.of("saturn", "Aquarius"))));

            String response = astrologyService.getGocharDetails(createValidHoroscopeRequestBody());

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            assertTrue(json.has("gochar"));
            verifyHttpCallTo("/gochar");
        }
    }

    // ---- Error response tests ----

    @Test
    void testGetAstrologicalDetailsLambdaError() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            stubHttpErrorResponse(500);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody()));

            assertTrue(ex.getMessage().contains("/get_horoscope"));
            assertTrue(ex.getMessage().contains("500"));
        }
    }

    @Test
    void testGetDashaDetailsLambdaError() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            stubHttpErrorResponse(502);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    astrologyService.getDashaDetails(createValidDashaRequestBody()));

            assertTrue(ex.getMessage().contains("/dasha"));
            assertTrue(ex.getMessage().contains("502"));
        }
    }

    // ---- Direct response parsing (no "body" wrapper) ----

    @Test
    void testDirectResponseWithoutBodyWrapper() throws Exception {
        try (MockedStatic<AstrologyServiceConfig> configMock = mockStatic(AstrologyServiceConfig.class)) {
            stubConfig(configMock);
            // Response is direct JSON, not wrapped in {"body": "..."}
            String directJson = gson.toJson(Map.of("ascendant", "Taurus"));
            stubHttpResponse(200, directJson);

            String response = astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody());

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            assertTrue(json.has("ascendant"));
            assertEquals("Taurus", json.get("ascendant").getAsString());
        }
    }

    // ---- Helpers ----

    private void stubConfig(MockedStatic<AstrologyServiceConfig> configMock) {
        configMock.when(AstrologyServiceConfig::getLambdaBaseUrl).thenReturn("https://mock-astrology.example.com");
        configMock.when(AstrologyServiceConfig::getApiToken).thenReturn("mock-token");
    }

    @SuppressWarnings("unchecked")
    private void stubHttpResponse(int statusCode, String body) throws Exception {
        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(body);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
    }

    @SuppressWarnings("unchecked")
    private void stubHttpErrorResponse(int statusCode) throws Exception {
        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
    }

    /** Wraps payload in a Lambda-style {"statusCode":200,"body":"..."} envelope. */
    private String lambdaResponse(Map<String, Object> payload) {
        String innerJson = gson.toJson(payload);
        return gson.toJson(Map.of("statusCode", 200, "body", innerJson));
    }

    @SuppressWarnings("unchecked")
    private void verifyHttpCallTo(String expectedPath) throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.endsWith(expectedPath), "Expected URI to end with " + expectedPath + " but was " + uri);
        assertTrue(uri.startsWith("https://mock-astrology.example.com"), "Expected mock base URL but was " + uri);
    }

    private RequestBody createValidHoroscopeRequestBody() {
        RequestBody requestBody = new RequestBody();
        requestBody.setHoroscopeDate(15);
        requestBody.setHoroscopeMonth(8);
        requestBody.setHoroscopeYear(1990);
        requestBody.setHoroscopeHour(10);
        requestBody.setHoroscopeMinute(30);
        requestBody.setHoroscopeLatitude(28.6139);
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
        requestBody.setDashaLatitude(28.6139);
        requestBody.setDashaLongitude(77.2090);
        requestBody.setDashaPrefix(Arrays.asList("Sun", "Moon"));
        return requestBody;
    }

    private RequestBody createValidDivisionalChartsRequestBody() {
        RequestBody requestBody = new RequestBody();
        requestBody.setHoroscopeDate(15);
        requestBody.setHoroscopeMonth(8);
        requestBody.setHoroscopeYear(1990);
        requestBody.setHoroscopeHour(10);
        requestBody.setHoroscopeMinute(30);
        requestBody.setHoroscopeLatitude(28.6139);
        requestBody.setHoroscopeLongitude(77.2090);
        requestBody.setDivisionalChartNumbers(Arrays.asList(2, 9));
        return requestBody;
    }
}
