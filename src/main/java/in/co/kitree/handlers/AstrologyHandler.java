package in.co.kitree.handlers;

import com.google.cloud.firestore.Firestore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;

import java.util.Map;

public class AstrologyHandler {

    private final Firestore db;
    private final AstrologyService astrologyService;
    private final PythonLambdaService pythonLambdaService;
    private final RashifalService rashifalService;
    private final Gson gson;

    public AstrologyHandler(Firestore db, AstrologyService astrologyService, PythonLambdaService pythonLambdaService, RashifalService rashifalService) {
        this.db = db;
        this.astrologyService = astrologyService;
        this.pythonLambdaService = pythonLambdaService;
        this.rashifalService = rashifalService;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public String handleRequest(String action, String userId, RequestBody requestBody) throws Exception {
        return switch (action) {
            case "get_astrological_details" -> astrologyService.getAstrologicalDetails(requestBody);
            case "get_dasha_details" -> astrologyService.getDashaDetails(requestBody);
            case "get_divisional_charts" -> astrologyService.getDivisionalCharts(requestBody);
            case "get_gochar_details" -> astrologyService.getGocharDetails(requestBody);
            case "generate_aura_report" -> handleGenerateAuraReport(userId, requestBody);
            case "get_certificate_courses" -> handleGetCertificateCourses(requestBody);
            case "generate_certificate" -> handleGenerateCertificate(requestBody);
            default -> gson.toJson(Map.of("success", false, "errorMessage", "Unknown action: " + action));
        };
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
            if (!AuthenticationService.isAdmin(userId)) {
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

}
