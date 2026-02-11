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
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AstrologyServiceTest {

    private static final String FUNCTION_NAME = "kitree-astrology-api-test";

    @Mock
    private LambdaClient mockLambdaClient;

    private AstrologyService astrologyService;
    private Gson gson;

    @BeforeEach
    void setUp() {
        astrologyService = new AstrologyService(mockLambdaClient, FUNCTION_NAME);
        gson = new Gson();
    }

    // ---- Missing-field validation tests (no Lambda calls) ----

    @Test
    void testGetAstrologicalDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getAstrologicalDetails(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required horoscope details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockLambdaClient);
    }

    @Test
    void testGetDashaDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getDashaDetails(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required dasha details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockLambdaClient);
    }

    @Test
    void testGetDivisionalChartsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getDivisionalCharts(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required divisional chart details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockLambdaClient);
    }

    @Test
    void testGetGocharDetailsWithMissingFields() throws Exception {
        RequestBody requestBody = new RequestBody();

        String response = astrologyService.getGocharDetails(requestBody);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Missing required gochar details", json.get("errorMessage").getAsString());
        verifyNoInteractions(mockLambdaClient);
    }

    // ---- Successful Lambda invocation tests ----

    @Test
    void testGetAstrologicalDetails() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("ascendant", "Aries", "sun", "Leo", "moon", "Cancer")));

        String response = astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody());

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("ascendant"));
        assertTrue(json.has("sun"));
        verifyInvokeWithAction("get_horoscope");
    }

    @Test
    void testGetDashaDetails() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("dashas", "Sun-Moon", "current_level_calculated", true)));

        String response = astrologyService.getDashaDetails(createValidDashaRequestBody());

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("dashas"));
        verifyInvokeWithAction("dasha");
    }

    @Test
    void testGetDivisionalCharts() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("charts", Map.of("D2", "data", "D9", "data"))));

        String response = astrologyService.getDivisionalCharts(createValidDivisionalChartsRequestBody());

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("charts"));
        verifyInvokeWithAction("divisional_charts");
    }

    @Test
    void testGetGocharDetails() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("gochar", Map.of("saturn", "Aquarius"))));

        String response = astrologyService.getGocharDetails(createValidHoroscopeRequestBody());

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("gochar"));
        verifyInvokeWithAction("gochar");
    }

    @Test
    void testGetRashifalData() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("natal_moon_sign", 5, "tara_bala", 2)));

        String response = astrologyService.getRashifalData(1990, 8, 15, 10, 30, 28.6139, 77.2090);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("natal_moon_sign"));
        verifyInvokeWithAction("rashifal");
    }

    // ---- Error response tests ----

    @Test
    void testLambdaFunctionError() {
        InvokeResponse errorResponse = InvokeResponse.builder()
                .functionError("Unhandled")
                .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"Something went wrong\"}"))
                .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class))).thenReturn(errorResponse);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody()));

        assertTrue(ex.getMessage().contains("get_horoscope"));
        assertTrue(ex.getMessage().contains("Unhandled"));
    }

    @Test
    void testLambdaDashaFunctionError() {
        InvokeResponse errorResponse = InvokeResponse.builder()
                .functionError("Unhandled")
                .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"Timeout\"}"))
                .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class))).thenReturn(errorResponse);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                astrologyService.getDashaDetails(createValidDashaRequestBody()));

        assertTrue(ex.getMessage().contains("dasha"));
        assertTrue(ex.getMessage().contains("Unhandled"));
    }

    // ---- Direct response parsing (no "body" wrapper) ----

    @Test
    void testDirectResponseWithoutBodyWrapper() throws Exception {
        String directJson = gson.toJson(Map.of("ascendant", "Taurus"));
        stubLambdaResponse(directJson);

        String response = astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody());

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("ascendant"));
        assertEquals("Taurus", json.get("ascendant").getAsString());
    }

    // ---- Verify function name and payload ----

    @Test
    void testInvokeUsesCorrectFunctionName() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("ascendant", "Aries")));

        astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody());

        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(mockLambdaClient).invoke(captor.capture());
        assertEquals(FUNCTION_NAME, captor.getValue().functionName());
    }

    @Test
    void testPayloadContainsActionField() throws Exception {
        stubLambdaResponse(lambdaEnvelope(Map.of("ascendant", "Aries")));

        astrologyService.getAstrologicalDetails(createValidHoroscopeRequestBody());

        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(mockLambdaClient).invoke(captor.capture());
        String payload = captor.getValue().payload().asUtf8String();
        JsonObject payloadJson = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals("get_horoscope", payloadJson.get("action").getAsString());
        // Verify no api_token in payload
        assertFalse(payloadJson.has("api_token"));
    }

    // ---- Helpers ----

    private void stubLambdaResponse(String responsePayload) {
        InvokeResponse response = InvokeResponse.builder()
                .payload(SdkBytes.fromUtf8String(responsePayload))
                .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class))).thenReturn(response);
    }

    /** Wraps payload in a Lambda-style {"statusCode":200,"body":"..."} envelope. */
    private String lambdaEnvelope(Map<String, Object> payload) {
        String innerJson = gson.toJson(payload);
        return gson.toJson(Map.of("statusCode", 200, "body", innerJson));
    }

    private void verifyInvokeWithAction(String expectedAction) {
        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(mockLambdaClient).invoke(captor.capture());
        String payload = captor.getValue().payload().asUtf8String();
        JsonObject payloadJson = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals(expectedAction, payloadJson.get("action").getAsString());
        assertEquals(FUNCTION_NAME, captor.getValue().functionName());
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
