package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.RequestBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import io.sentry.Sentry;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.BaggageHeader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AstrologyService {

    private final HttpClient httpClient;
    private final Gson gson;

    public AstrologyService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(AstrologyServiceConfig.HTTP_TIMEOUT_SECONDS))
                .build();
        this.gson = new GsonBuilder().create();
    }

    private HttpResponse<String> makeTracedPost(HttpRequest.Builder builder, String description) throws Exception {
        ISpan currentSpan = Sentry.getSpan();
        if (currentSpan != null) {
            builder.header("sentry-trace", currentSpan.toSentryTrace().getValue());
            BaggageHeader baggage = currentSpan.toBaggageHeader(List.of());
            if (baggage != null) {
                builder.header("baggage", baggage.getValue());
            }
        }
        ISpan span = currentSpan != null ? currentSpan.startChild("http.client", description) : null;
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (span != null) {
                span.setStatus(SpanStatus.fromHttpStatusCode(response.statusCode()));
                span.finish();
            }
            return response;
        } catch (Exception e) {
            if (span != null) {
                span.setThrowable(e);
                span.setStatus(SpanStatus.INTERNAL_ERROR);
                span.finish();
            }
            throw e;
        }
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
        body.put("api_token", AstrologyServiceConfig.API_TOKEN);

        return callLambda("/get_horoscope", body);
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
        body.put("api_token", AstrologyServiceConfig.API_TOKEN);

        return callLambda("/dasha", body);
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
        body.put("api_token", AstrologyServiceConfig.API_TOKEN);

        return callLambda("/divisional_charts", body);
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
        body.put("api_token", AstrologyServiceConfig.API_TOKEN);

        return callLambda("/gochar", body);
    }

    private String callLambda(String path, Map<String, Object> body) throws Exception {
        String url = AstrologyServiceConfig.LAMBDA_BASE_URL + path;
        HttpResponse<String> httpResponse = makeTracedPost(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))),
                "POST " + url
        );
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            return parseLambdaResponse(httpResponse.body());
        } else {
            throw new RuntimeException("Astrology Lambda " + path + " failed with status: " + httpResponse.statusCode());
        }
    }

    private String parseLambdaResponse(String response) {
        try {
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
