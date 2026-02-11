package in.co.kitree.handlers;

import com.google.cloud.firestore.Firestore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Handler for astrology and digital product fulfillment operations.
 * Extracted from Handler.java as part of refactoring.
 */
public class AstrologyHandler {

    private final Firestore db;
    private final AstrologyService astrologyService;
    private final PythonLambdaService pythonLambdaService;
    private final RashifalService rashifalService;
    private final Gson gson;
    private final HttpClient httpClient;

    public AstrologyHandler(Firestore db, AstrologyService astrologyService, PythonLambdaService pythonLambdaService, RashifalService rashifalService) {
        this.db = db;
        this.astrologyService = astrologyService;
        this.pythonLambdaService = pythonLambdaService;
        this.rashifalService = rashifalService;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String handleRequest(String action, String userId, RequestBody requestBody) throws Exception {
        return switch (action) {
            case "get_astrological_details" -> astrologyService.getAstrologicalDetails(requestBody);
            case "get_dasha_details" -> astrologyService.getDashaDetails(requestBody);
            case "get_divisional_charts" -> astrologyService.getDivisionalCharts(requestBody);
            case "get_gochar_details" -> handleGetGocharDetails(requestBody);
            case "generate_aura_report" -> handleGenerateAuraReport(userId, requestBody);
            case "get_certificate_courses" -> handleGetCertificateCourses(requestBody);
            case "generate_certificate" -> handleGenerateCertificate(requestBody);
            default -> gson.toJson(Map.of("success", false, "errorMessage", "Unknown action: " + action));
        };
    }

    // ============= Handler Methods =============

    private String handleGetGocharDetails(RequestBody requestBody) throws Exception {
        Map<String, Object> gocharApiRequestBody = new HashMap<>();
        if (requestBody.getHoroscopeDate() == null || requestBody.getHoroscopeMonth() == null ||
            requestBody.getHoroscopeYear() == null || requestBody.getHoroscopeHour() == null ||
            requestBody.getHoroscopeMinute() == null || requestBody.getHoroscopeLatitude() == null ||
            requestBody.getHoroscopeLongitude() == null) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required gochar details"));
        }
        gocharApiRequestBody.put("date", requestBody.getHoroscopeDate());
        gocharApiRequestBody.put("month", requestBody.getHoroscopeMonth());
        gocharApiRequestBody.put("year", requestBody.getHoroscopeYear());
        gocharApiRequestBody.put("hour", requestBody.getHoroscopeHour());
        gocharApiRequestBody.put("minute", requestBody.getHoroscopeMinute());
        gocharApiRequestBody.put("latitude", requestBody.getHoroscopeLatitude());
        gocharApiRequestBody.put("longitude", requestBody.getHoroscopeLongitude());
        gocharApiRequestBody.put("api_token", "D80FE645F582F9E0");
        return getGocharDetails(gson.toJson(gocharApiRequestBody));
    }

    private String handleGetCertificateCourses(RequestBody requestBody) {
        PythonLambdaEventRequest getCoursesEvent = new PythonLambdaEventRequest();
        getCoursesEvent.setFunction("get_certificate_courses");
        getCoursesEvent.setLanguage(requestBody.getLanguage() == null ? "en" : requestBody.getLanguage());

        PythonLambdaResponseBody pythonResponse = pythonLambdaService.invokePythonLambda(getCoursesEvent);

        if (pythonResponse != null && pythonResponse.getCourses() != null) {
            return gson.toJson(Map.of("courses", pythonResponse.getCourses()));
        } else {
            LoggingService.error("python_lambda_courses_fetch_failed");
            return gson.toJson(Map.of("success", false, "errorMessage", "Failed to fetch courses."));
        }
    }

    private String handleGenerateCertificate(RequestBody requestBody) {
        PythonLambdaEventRequest genCertEvent = new PythonLambdaEventRequest();
        genCertEvent.setFunction("generate_certificate");
        genCertEvent.setName(requestBody.getCertificateHolderName());
        genCertEvent.setDate(requestBody.getCertificateDate());
        genCertEvent.setCourse(requestBody.getCertificateCourse());
        return pythonLambdaService.invokePythonLambda(genCertEvent).getCertificate();
    }

    private String handleGenerateAuraReport(String userId, RequestBody requestBody) {
        if (requestBody.getUserName() == null || requestBody.getDob() == null || requestBody.getScannerDetails() == null) {
            LoggingService.error("aura_report_missing_fields");
            return gson.toJson(Map.of("success", false, "errorMessage", "Missing required user details or scanner data."));
        }
        try {
            if (!isAdmin(userId)) {
                LoggingService.warn("aura_report_unauthorized_attempt", Map.of("userId", userId));
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized action."));
            }
        } catch (Exception e) {
            return gson.toJson(Map.of("success", false, "errorMessage", "Authorization check failed."));
        }

        PythonLambdaEventRequest auraEvent = new PythonLambdaEventRequest();
        auraEvent.setFunction("generate_aura_report");
        auraEvent.setUserName(requestBody.getUserName());
        auraEvent.setDob(requestBody.getDob());
        auraEvent.setScannerDetails(requestBody.getScannerDetails());
        auraEvent.setLanguage(requestBody.getLanguage() == null ? "en" : requestBody.getLanguage());

        PythonLambdaResponseBody pythonResponse = pythonLambdaService.invokePythonLambda(auraEvent);

        if (pythonResponse != null && pythonResponse.getAuraReportLink() != null) {
            LoggingService.info("aura_report_generated", Map.of("link", pythonResponse.getAuraReportLink()));
            return gson.toJson(Map.of("auraReportLink", pythonResponse.getAuraReportLink()));
        } else {
            LoggingService.error("aura_report_generation_failed");
            return gson.toJson(Map.of("success", false, "errorMessage", "Report generation failed on the backend."));
        }
    }

    // ============= Private Helpers =============

    private String fulfillDigitalOrders(String userId, String orderId, String language) throws ExecutionException, InterruptedException {
        var orderDocument = this.db.collection("users").document(userId).collection("orders").document(orderId).get().get();
        String type = String.valueOf(orderDocument.get("type"));
        if (!"DIGITAL_PRODUCT".equals(type)) {
            return "Bad Request";
        }
        if (orderDocument.contains("reportLink")) {
            return "Already fulfilled";
        }
        if (!orderDocument.contains("profileId")) {
            return "Bad Request";
        }
        String profileId = String.valueOf(orderDocument.get("profileId"));
        var profileDocument = this.db.collection("users").document(userId).collection("profiles").document(profileId).get().get();
        if (!profileDocument.contains("preferredName") || !profileDocument.contains("dob")) {
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("errorCode", "N1").get();
            return "Bad Request";
        }
        if (!profileDocument.contains("dob")) {
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("errorCode", "N2").get();
            return "Bad Request";
        }
        if (orderDocument.contains("errorCode")) {
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("errorCode", com.google.cloud.firestore.FieldValue.delete()).get();
        }
        com.google.cloud.Timestamp dob = ((com.google.cloud.Timestamp) java.util.Objects.requireNonNull(profileDocument.get("dob")));
        String dobString = toYYYYMMDD(dob);
        String category = String.valueOf(orderDocument.get("category"));
        if ("NUMEROLOGY_REPORT".equals(category)) {
            FirebaseUser user = UserService.getUserDetails(this.db, userId);
            String expertId = String.valueOf(orderDocument.get("expertId"));
            FirebaseUser expert = UserService.getUserDetails(this.db, expertId);
            PythonLambdaEventRequest createNumerologyReportEvent = new PythonLambdaEventRequest();
            ExpertStoreDetails expertStoreDetails = fetchExpertStoreDetails(expertId);
            createNumerologyReportEvent.setFunction("numerology_report");
            createNumerologyReportEvent.setExpertName(expert.getName());
            createNumerologyReportEvent.setExpertPhoneNumber(expert.getPhoneNumber());
            createNumerologyReportEvent.setExpertImageUrl(expertStoreDetails.getImageUrl());
            createNumerologyReportEvent.setAboutExpert(expertStoreDetails.getAbout());
            createNumerologyReportEvent.setUserName(String.valueOf(profileDocument.get("name")));
            createNumerologyReportEvent.setUserPreferredName(String.valueOf(profileDocument.get("preferredName")));
            createNumerologyReportEvent.setDob(dobString);
            createNumerologyReportEvent.setUserPhoneNumber(user.getPhoneNumber());
            createNumerologyReportEvent.setOrderId(orderId);
            createNumerologyReportEvent.setLanguage(language);
            String reportLink = pythonLambdaService.invokePythonLambda(createNumerologyReportEvent).getReportLink();
            this.db.collection("users").document(userId).collection("orders").document(orderId).update("reportLink", reportLink).get();
        }
        return "Done Successfully";
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

    private String getGocharDetails(String requestBody) throws Exception {
        String baseUrl;
        if (AstrologyServiceConfig.isAwsLambdaProviderSelected()) {
            baseUrl = AstrologyServiceConfig.AWS_LAMBDA_BASE_URL;
        } else if (AstrologyServiceConfig.isPythonServerProviderSelected()) {
            baseUrl = AstrologyServiceConfig.PYTHON_SERVER_BASE_URL;
        } else {
            baseUrl = AstrologyServiceConfig.AWS_LAMBDA_BASE_URL;
        }

        String API_URL = baseUrl + "/gochar";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            String response = httpResponse.body();
            return parseLambdaResponse(response);
        } else {
            throw new RuntimeException("Gochar API request failed with status code: " + httpResponse.statusCode());
        }
    }

    private ExpertStoreDetails fetchExpertStoreDetails(String expertId) {
        ExpertStoreDetails expertStoreDetails = new ExpertStoreDetails();
        try {
            var doc = this.db.collection("users").document(expertId).collection("public").document("store");
            var documentSnapshot = doc.get().get();
            if (documentSnapshot.exists()) {
                expertStoreDetails.setAbout((String) java.util.Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("about", ""));
                expertStoreDetails.setStoreName((String) java.util.Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("storeName", ""));
                expertStoreDetails.setImageUrl((String) java.util.Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("displayPicture", ""));
            }
        } catch (Exception e) {
            expertStoreDetails.setStoreName("");
            expertStoreDetails.setAbout("");
            expertStoreDetails.setImageUrl("");
        }
        return expertStoreDetails;
    }

    private boolean isAdmin(String userId) throws com.google.firebase.auth.FirebaseAuthException {
        return Boolean.TRUE.equals(com.google.firebase.auth.FirebaseAuth.getInstance().getUser(userId).getCustomClaims().get("admin"));
    }

    private static String toYYYYMMDD(com.google.cloud.Timestamp timestamp) {
        java.util.Date date = timestamp.toDate();
        java.time.Instant instant = date.toInstant();
        java.time.ZonedDateTime zonedDateTime = instant.atZone(java.time.ZoneId.of("UTC"));
        java.time.LocalDateTime localDateTime = zonedDateTime.toLocalDateTime();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return formatter.format(localDateTime);
    }
}
