package in.co.kitree.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.co.kitree.pojos.RequestBody;
import in.co.kitree.pojos.ThirdPartyAstrologyRequest;
import in.co.kitree.pojos.ThirdPartyAstrologyResponse;
import in.co.kitree.pojos.ThirdPartyPlanetData;
import in.co.kitree.pojos.ThirdPartyDashaRequest;
import in.co.kitree.pojos.ThirdPartyDashaEntry;
import in.co.kitree.pojos.FreeAstrologyDivisionalChartRequest;
import in.co.kitree.pojos.FreeAstrologyDivisionalChartResponse;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

public class AstrologyService {
    
    private final HttpClient httpClient;
    private final Gson gson;
    private String astrologyApiKey;
    
    public AstrologyService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(AstrologyServiceConfig.HTTP_TIMEOUT_SECONDS))
                .build();
        this.gson = new GsonBuilder().create();
        
        // Read API key from secrets.json
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));
            this.astrologyApiKey = rootNode.path("ASTROLOGY_API_KEY").asText();
        } catch (IOException e) {
            System.err.println("Error reading ASTROLOGY_API_KEY from secrets.json: " + e.getMessage());
            this.astrologyApiKey = null;
        }
    }
    
    /**
     * Get astrological details (horoscope) based on birth data
     */
    public String getAstrologicalDetails(RequestBody requestBody) throws Exception {
        // Validate required fields
        if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                requestBody.getHoroscopeLongitude() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required horoscope details"));
        }
        
        if (AstrologyServiceConfig.isFreeAstrologyApiProviderSelected()) {
            return getAstrologicalDetailsFromFreeAstrologyApi(requestBody);
        } else {
            return getAstrologicalDetailsFromPythonServer(requestBody);
        }
    }
    
    /**
     * Get dasha details based on birth data
     */
    public String getDashaDetails(RequestBody requestBody) throws Exception {
        // Validate required fields
        if (requestBody.getDashaDate() == null || requestBody.getDashaMonth() == null ||
                requestBody.getDashaYear() == null || requestBody.getDashaHour() == null ||
                requestBody.getDashaMinute() == null || requestBody.getDashaLatitude() == null ||
                requestBody.getDashaLongitude() == null || requestBody.getDashaPrefix() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required dasha details"));
        }
        
        if (AstrologyServiceConfig.isFreeAstrologyApiProviderSelected()) {
            return getDashaDetailsFromFreeAstrologyApi(requestBody);
        } else {
            return getDashaDetailsFromPythonServer(requestBody);
        }
    }
    
    /**
     * Get divisional charts based on birth data
     */
    public String getDivisionalCharts(RequestBody requestBody) throws Exception {
        // Validate required fields
        if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
                requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
                requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
                requestBody.getHoroscopeLongitude() == null || requestBody.getDivisionalChartNumbers() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required divisional chart details"));
        }
        
        if (AstrologyServiceConfig.isFreeAstrologyApiProviderSelected()) {
            return getDivisionalChartsFromFreeAstrologyApi(requestBody);
        } else {
            return getDivisionalChartsFromPythonServer(requestBody);
        }
    }
    
    /**
     * Get astrological details from Python server (existing implementation)
     */
    private String getAstrologicalDetailsFromPythonServer(RequestBody requestBody) throws Exception {
        Map<String, Object> horoscopeApiRequestBody = new HashMap<>();
        horoscopeApiRequestBody.put("date", requestBody.getHoroscopeDate());
        horoscopeApiRequestBody.put("month", requestBody.getHoroscopeMonth());
        horoscopeApiRequestBody.put("year", requestBody.getHoroscopeYear());
        horoscopeApiRequestBody.put("hour", requestBody.getHoroscopeHour());
        horoscopeApiRequestBody.put("minute", requestBody.getHoroscopeMinute());
        horoscopeApiRequestBody.put("latitude", requestBody.getHoroscopeLatitude());
        horoscopeApiRequestBody.put("longitude", requestBody.getHoroscopeLongitude());
        horoscopeApiRequestBody.put("api_token", AstrologyServiceConfig.PYTHON_SERVER_API_TOKEN);
        
        String API_URL = AstrologyServiceConfig.PYTHON_SERVER_BASE_URL + "/get_horoscope";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(horoscopeApiRequestBody)))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Horoscope API response: " + response);
            return response;
        } else {
            throw new RuntimeException("API request failed with status code: " + httpResponse.statusCode());
        }
    }
    
    /**
     * Get astrological details from Free Astrology API
     */
    private String getAstrologicalDetailsFromFreeAstrologyApi(RequestBody requestBody) throws Exception {
        // Calculate timezone from coordinates
        double timezone = TimezoneUtils.getTimezoneOffset(
                requestBody.getHoroscopeLatitude(), 
                requestBody.getHoroscopeLongitude()
        );
        
        // Create request payload
        Map<String, String> settings = new HashMap<>();
        settings.put("observation_point", AstrologyServiceConfig.OBSERVATION_POINT);
        settings.put("ayanamsha", AstrologyServiceConfig.AYANAMSHA);
        
        ThirdPartyAstrologyRequest freeAstrologyApiRequest = new ThirdPartyAstrologyRequest(
                requestBody.getHoroscopeYear(),
                requestBody.getHoroscopeMonth(),
                requestBody.getHoroscopeDate(),
                requestBody.getHoroscopeHour(),
                requestBody.getHoroscopeMinute(),
                0, // seconds
                requestBody.getHoroscopeLatitude(),
                requestBody.getHoroscopeLongitude(),
                timezone,
                settings
        );
        
        String payload = gson.toJson(freeAstrologyApiRequest);
        
        // Get API key from secrets.json
        if (this.astrologyApiKey == null || this.astrologyApiKey.isEmpty()) {
            throw new RuntimeException("ASTROLOGY_API_KEY not found in secrets.json");
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AstrologyServiceConfig.FREE_ASTROLOGY_API_PLANETS_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", this.astrologyApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Free Astrology API response: " + response);
            
            // Parse and transform response to match expected format
            return transformThirdPartyResponseToExpectedFormat(response);
        } else {
            throw new RuntimeException("Free Astrology API request failed with status code: " + httpResponse.statusCode());
        }
    }
    
    /**
     * Transform Free Astrology API response to match the expected format from Python server
     */
    private String transformThirdPartyResponseToExpectedFormat(String thirdPartyResponse) {
        try {
            // Parse the third-party response
            ThirdPartyAstrologyResponse response = gson.fromJson(thirdPartyResponse, ThirdPartyAstrologyResponse.class);
            
            // Extract planet data from the output array (second element contains named planets)
            ThirdPartyPlanetData[] output = response.getOutput();
            if (output == null || output.length < 2) {
                throw new RuntimeException("Invalid response format from third-party API");
            }
            
            // The second element contains the named planet data
            Map<String, Object> transformedResponse = new HashMap<>();
            
            // Transform each planet data
            for (ThirdPartyPlanetData planet : output) {
                if (planet != null && planet.getName() != null) {
                    Map<String, Object> planetData = new HashMap<>();
                    planetData.put("sign", planet.getCurrentSign());
                    planetData.put("is_retrograde", "true".equals(planet.getIsRetro()));
                    planetData.put("longitude", planet.getNormDegree());
                    
                    transformedResponse.put(planet.getName().toLowerCase(), planetData);
                }
            }
            
            return gson.toJson(transformedResponse);
        } catch (Exception e) {
            System.err.println("Error transforming third-party response: " + e.getMessage());
            throw new RuntimeException("Failed to transform third-party API response", e);
        }
    }
    
    /**
     * Get dasha details from Free Astrology API
     */
    private String getDashaDetailsFromFreeAstrologyApi(RequestBody requestBody) throws Exception {
        // Calculate timezone from coordinates
        double timezone = TimezoneUtils.getTimezoneOffset(
                requestBody.getDashaLatitude(), 
                requestBody.getDashaLongitude()
        );
        
        // Create request payload
        Map<String, String> config = new HashMap<>();
        config.put("observation_point", AstrologyServiceConfig.OBSERVATION_POINT);
        config.put("ayanamsha", AstrologyServiceConfig.AYANAMSHA);
        
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
        
        String payload = gson.toJson(dashaRequest);
        
        // Get API key from secrets.json
        if (this.astrologyApiKey == null || this.astrologyApiKey.isEmpty()) {
            throw new RuntimeException("ASTROLOGY_API_KEY not found in secrets.json");
        }
        
        // Determine which endpoint to use based on dashaPrefix
        String apiUrl;
        if (requestBody.getDashaPrefix() != null && !requestBody.getDashaPrefix().isEmpty()) {
            // If prefix is provided, get Maha Dasas and Antar Dasas
            apiUrl = AstrologyServiceConfig.FREE_ASTROLOGY_API_MAHA_ANTAR_DASAS_URL;
        } else {
            // If no prefix, get only Maha Dasas
            apiUrl = AstrologyServiceConfig.FREE_ASTROLOGY_API_MAHA_DASAS_URL;
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", this.astrologyApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Free Astrology API dasha response: " + response);
            
            // Transform response to match expected format
            return transformThirdPartyDashaResponseToExpectedFormat(response, requestBody);
        } else {
            throw new RuntimeException("Free Astrology API dasha request failed with status code: " + httpResponse.statusCode());
        }
    }
    
    /**
     * Transform Free Astrology API dasha response to match the expected format from Python server
     */
    private String transformThirdPartyDashaResponseToExpectedFormat(String thirdPartyResponse, RequestBody requestBody) {
        try {
            // Parse the third-party response
            Map<String, Object> responseMap = gson.fromJson(thirdPartyResponse, Map.class);
            
            // Transform to match Python server format
            Map<String, Object> transformedResponse = new HashMap<>();
            
            // Extract dasha entries and transform them
            for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Map) {
                    Map<String, Object> dashaData = (Map<String, Object>) value;
                    
                    // Create dasha entry in expected format
                    Map<String, Object> dashaEntry = new HashMap<>();
                    dashaEntry.put("planet_name", dashaData.get("Lord"));
                    dashaEntry.put("date_str", dashaData.get("start_time"));
                    
                    transformedResponse.put(key, dashaEntry);
                }
            }
            
            // Add metadata
            transformedResponse.put("current_level_calculated", 1); // Maha Dasa level
            transformedResponse.put("prefix_used", requestBody.getDashaPrefix() != null ? requestBody.getDashaPrefix() : new ArrayList<>());
            
            return gson.toJson(transformedResponse);
        } catch (Exception e) {
            System.err.println("Error transforming Free Astrology API dasha response: " + e.getMessage());
            throw new RuntimeException("Failed to transform Free Astrology API dasha response", e);
        }
    }

    /**
     * Get dasha details from Python server (existing implementation)
     */
    private String getDashaDetailsFromPythonServer(RequestBody requestBody) throws Exception {
        Map<String, Object> dashaApiRequestBody = new HashMap<>();
        dashaApiRequestBody.put("date", requestBody.getDashaDate());
        dashaApiRequestBody.put("month", requestBody.getDashaMonth());
        dashaApiRequestBody.put("year", requestBody.getDashaYear());
        dashaApiRequestBody.put("hour", requestBody.getDashaHour());
        dashaApiRequestBody.put("minute", requestBody.getDashaMinute());
        dashaApiRequestBody.put("latitude", requestBody.getDashaLatitude());
        dashaApiRequestBody.put("longitude", requestBody.getDashaLongitude());
        dashaApiRequestBody.put("prefix", requestBody.getDashaPrefix());
        dashaApiRequestBody.put("api_token", AstrologyServiceConfig.PYTHON_SERVER_API_TOKEN);
        
        String API_URL = AstrologyServiceConfig.PYTHON_SERVER_BASE_URL + "/dasha";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(dashaApiRequestBody)))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Dasha API response: " + response);
            return response;
        } else {
            throw new RuntimeException("Dasha API request failed with status code: " + httpResponse.statusCode());
        }
    }
    
    /**
     * Get divisional charts from Python server (existing implementation)
     */
    private String getDivisionalChartsFromPythonServer(RequestBody requestBody) throws Exception {
        Map<String, Object> divisionalChartsApiRequestBody = new HashMap<>();
        divisionalChartsApiRequestBody.put("date", requestBody.getHoroscopeDate());
        divisionalChartsApiRequestBody.put("month", requestBody.getHoroscopeMonth());
        divisionalChartsApiRequestBody.put("year", requestBody.getHoroscopeYear());
        divisionalChartsApiRequestBody.put("hour", requestBody.getHoroscopeHour());
        divisionalChartsApiRequestBody.put("minute", requestBody.getHoroscopeMinute());
        divisionalChartsApiRequestBody.put("latitude", requestBody.getHoroscopeLatitude());
        divisionalChartsApiRequestBody.put("longitude", requestBody.getHoroscopeLongitude());
        divisionalChartsApiRequestBody.put("divisional_chart_numbers", requestBody.getDivisionalChartNumbers());
        divisionalChartsApiRequestBody.put("api_token", AstrologyServiceConfig.PYTHON_SERVER_API_TOKEN);
        
        String API_URL = AstrologyServiceConfig.PYTHON_SERVER_BASE_URL + "/divisional_charts";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(divisionalChartsApiRequestBody)))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            System.out.println("Divisional Charts API response: " + response);
            return response;
        } else {
            throw new RuntimeException("Divisional Charts API request failed with status code: " + httpResponse.statusCode());
        }
    }
    
    /**
     * Get divisional charts from Free Astrology API
     */
    private String getDivisionalChartsFromFreeAstrologyApi(RequestBody requestBody) throws Exception {
        // Calculate timezone from coordinates
        double timezone = TimezoneUtils.getTimezoneOffset(
                requestBody.getHoroscopeLatitude(), 
                requestBody.getHoroscopeLongitude()
        );
        
        // Create settings
        Map<String, String> config = new HashMap<>();
        config.put("observation_point", AstrologyServiceConfig.OBSERVATION_POINT);
        config.put("ayanamsha", AstrologyServiceConfig.AYANAMSHA);
        
        Map<String, Object> charts = new HashMap<>();
        
        // Get API key from secrets.json
        if (this.astrologyApiKey == null || this.astrologyApiKey.isEmpty()) {
            throw new RuntimeException("ASTROLOGY_API_KEY not found in secrets.json");
        }
        
        // Fetch each divisional chart individually
        for (Integer chartNumber : requestBody.getDivisionalChartNumbers()) {
            try {
                String chartData = fetchSingleDivisionalChart(
                    requestBody, chartNumber, timezone, config, this.astrologyApiKey
                );
                charts.put("D" + chartNumber, chartData);
            } catch (Exception e) {
                System.err.println("Failed to fetch D" + chartNumber + " chart: " + e.getMessage());
                // Continue with other charts even if one fails
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("charts", charts);
        return gson.toJson(response);
    }
    
    /**
     * Fetch a single divisional chart from Free Astrology API
     */
    private String fetchSingleDivisionalChart(RequestBody requestBody, int chartNumber, 
                                            double timezone, Map<String, String> config, 
                                            String apiKey) throws Exception {
        // Create request payload
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
        
        String payload = gson.toJson(chartRequest);
        
        // Get the appropriate URL for this chart
        String apiUrl = AstrologyServiceConfig.getDivisionalChartUrl(chartNumber);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", this.astrologyApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String responseBody = httpResponse.body();
            System.out.println("Free Astrology API D" + chartNumber + " response: " + responseBody);
            
            // Transform response to match expected format
            return transformFreeAstrologyDivisionalChartResponse(responseBody);
        } else {
            throw new RuntimeException("Free Astrology API D" + chartNumber + " request failed with status code: " + httpResponse.statusCode());
        }
    }
    
    /**
     * Transform Free Astrology API divisional chart response to match expected format
     */
    private String transformFreeAstrologyDivisionalChartResponse(String freeAstrologyResponse) {
        try {
            // Parse the Free Astrology API response
            FreeAstrologyDivisionalChartResponse response = gson.fromJson(freeAstrologyResponse, FreeAstrologyDivisionalChartResponse.class);
            
            Map<String, Object> output = response.getOutput();
            Map<String, Object> transformedChart = new HashMap<>();
            
            // Transform planet data to match expected format
            for (Map.Entry<String, Object> entry : output.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> planetData = (Map<String, Object>) value;
                    
                    String planetName = (String) planetData.get("name");
                    Integer currentSign = (Integer) planetData.get("current_sign");
                    
                    if (planetName != null && currentSign != null) {
                        Map<String, Object> planetInfo = new HashMap<>();
                        planetInfo.put("sign", currentSign);
                        
                        // Map planet names to expected keys
                        String planetKey = mapPlanetNameToKey(planetName);
                        if (planetKey != null) {
                            transformedChart.put(planetKey, planetInfo);
                        }
                    }
                }
            }
            
            return gson.toJson(transformedChart);
        } catch (Exception e) {
            System.err.println("Error transforming Free Astrology API divisional chart response: " + e.getMessage());
            throw new RuntimeException("Failed to transform Free Astrology API divisional chart response", e);
        }
    }
    
    /**
     * Map planet names from Free Astrology API to expected keys
     */
    private String mapPlanetNameToKey(String planetName) {
        switch (planetName.toLowerCase()) {
            case "ascendant": return "ascendant";
            case "sun": return "sun";
            case "moon": return "moon";
            case "mars": return "mars";
            case "mercury": return "mercury";
            case "jupiter": return "jupiter";
            case "venus": return "venus";
            case "saturn": return "saturn";
            case "rahu": return "rahu";
            case "ketu": return "ketu";
            default: return null; // Skip unknown planets
        }
    }
}
