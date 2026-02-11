package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.RequestBody;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import io.sentry.Sentry;
import io.sentry.ISpan;
import io.sentry.SpanStatus;

import java.util.HashMap;
import java.util.Map;

public class AstrologyService {

    private final LambdaClient lambdaClient;
    private final String functionName;
    private final Gson gson;

    public AstrologyService(LambdaClient lambdaClient, boolean isTest) {
        this.lambdaClient = lambdaClient;
        this.functionName = isTest ? "kitree-astrology-api-test" : "kitree-astrology-api-prod";
        this.gson = new GsonBuilder().create();
    }

    // Package-private constructor for unit testing with a specific function name
    AstrologyService(LambdaClient lambdaClient, String functionName) {
        this.lambdaClient = lambdaClient;
        this.functionName = functionName;
        this.gson = new GsonBuilder().create();
    }

    public String getAstrologicalDetails(RequestBody requestBody) throws Exception {
        if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                requestBody.getHoroscopeLongitude() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required horoscope details"));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("date", requestBody.getHoroscopeDate());
        body.put("month", requestBody.getHoroscopeMonth());
        body.put("year", requestBody.getHoroscopeYear());
        body.put("hour", requestBody.getHoroscopeHour());
        body.put("minute", requestBody.getHoroscopeMinute());
        body.put("latitude", requestBody.getHoroscopeLatitude());
        body.put("longitude", requestBody.getHoroscopeLongitude());

        return invokeLambda("get_horoscope", body);
    }

    public String getDashaDetails(RequestBody requestBody) throws Exception {
        if (requestBody.getDashaDate() == null || requestBody.getDashaMonth() == null ||
                requestBody.getDashaYear() == null || requestBody.getDashaHour() == null ||
                requestBody.getDashaMinute() == null || requestBody.getDashaLatitude() == null ||
                requestBody.getDashaLongitude() == null || requestBody.getDashaPrefix() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required dasha details"));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("date", requestBody.getDashaDate());
        body.put("month", requestBody.getDashaMonth());
        body.put("year", requestBody.getDashaYear());
        body.put("hour", requestBody.getDashaHour());
        body.put("minute", requestBody.getDashaMinute());
        body.put("latitude", requestBody.getDashaLatitude());
        body.put("longitude", requestBody.getDashaLongitude());
        body.put("prefix", requestBody.getDashaPrefix());

        return invokeLambda("dasha", body);
    }

    public String getDivisionalCharts(RequestBody requestBody) throws Exception {
        if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                requestBody.getHoroscopeLongitude() == null || requestBody.getDivisionalChartNumbers() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required divisional chart details"));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("date", requestBody.getHoroscopeDate());
        body.put("month", requestBody.getHoroscopeMonth());
        body.put("year", requestBody.getHoroscopeYear());
        body.put("hour", requestBody.getHoroscopeHour());
        body.put("minute", requestBody.getHoroscopeMinute());
        body.put("latitude", requestBody.getHoroscopeLatitude());
        body.put("longitude", requestBody.getHoroscopeLongitude());
        body.put("divisional_chart_numbers", requestBody.getDivisionalChartNumbers());

        return invokeLambda("divisional_charts", body);
    }

    public String getGocharDetails(RequestBody requestBody) throws Exception {
        if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                requestBody.getHoroscopeLongitude() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required gochar details"));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("date", requestBody.getHoroscopeDate());
        body.put("month", requestBody.getHoroscopeMonth());
        body.put("year", requestBody.getHoroscopeYear());
        body.put("hour", requestBody.getHoroscopeHour());
        body.put("minute", requestBody.getHoroscopeMinute());
        body.put("latitude", requestBody.getHoroscopeLatitude());
        body.put("longitude", requestBody.getHoroscopeLongitude());

        return invokeLambda("gochar", body);
    }

    /**
     * Invoke the astrology lambda's /rashifal endpoint.
     * Used by RashifalService for daily rashifal generation.
     */
    public String getRashifalData(int year, int month, int day, int hour, int minute,
                                   double latitude, double longitude) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("date", day);
        body.put("month", month);
        body.put("year", year);
        body.put("hour", hour);
        body.put("minute", minute);
        body.put("latitude", latitude);
        body.put("longitude", longitude);

        return invokeLambda("rashifal", body);
    }

    private String invokeLambda(String action, Map<String, Object> body) throws Exception {
        body.put("action", action);

        ISpan currentSpan = Sentry.getSpan();
        ISpan span = currentSpan != null
                ? currentSpan.startChild("lambda.invoke", functionName + " " + action)
                : null;
        try {
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(gson.toJson(body)))
                    .build();

            InvokeResponse response = lambdaClient.invoke(request);

            if (response.functionError() != null) {
                throw new RuntimeException("Astrology Lambda " + action + " failed: " + response.functionError()
                        + " — " + response.payload().asUtf8String());
            }

            String responsePayload = response.payload().asUtf8String();
            if (span != null) {
                span.setStatus(SpanStatus.OK);
                span.finish();
            }
            return parseLambdaResponse(responsePayload);
        } catch (Exception e) {
            if (span != null) {
                span.setThrowable(e);
                span.setStatus(SpanStatus.INTERNAL_ERROR);
                span.finish();
            }
            throw e;
        }
    }

    private String parseLambdaResponse(String response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = gson.fromJson(response, Map.class);
            if (responseMap.containsKey("body") && responseMap.get("body") instanceof String) {
                return (String) responseMap.get("body");
            }
            return response;
        } catch (Exception e) {
            return response;
        }
    }
}
