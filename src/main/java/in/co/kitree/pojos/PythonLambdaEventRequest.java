package in.co.kitree.pojos;

import java.util.Map;

public class PythonLambdaEventRequest {

    String function;

    String name;
    String date;
    String course;

    String userId;
    String userName;
    String userPreferredName;
    String expertId;
    String expertName;
    String expertPhoneNumber;
    String expertImageUrl;
    String aboutExpert;
    String dob;
    String userPhoneNumber;
    String orderId;

    String webinarId;
    String type;

    String language;

    Map<String, Object> scannerDetails;

    boolean isVideo = false;
    boolean isTest = false;

    String sentryTrace;
    String baggage;

    public Map<String, Object> getScannerDetails() {
        return scannerDetails;
    }

    public void setScannerDetails(Map<String, Object> scannerDetails) {
        this.scannerDetails = scannerDetails;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getUserPreferredName() {
        return userPreferredName;
    }

    public void setUserPreferredName(String userPreferredName) {
        this.userPreferredName = userPreferredName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getWebinarId() {
        return webinarId;
    }

    public void setWebinarId(String webinarId) {
        this.webinarId = webinarId;
    }

    public String getExpertPhoneNumber() {
        return expertPhoneNumber;
    }

    public void setExpertPhoneNumber(String expertPhoneNumber) {
        this.expertPhoneNumber = expertPhoneNumber;
    }

    public String getExpertImageUrl() {
        return expertImageUrl;
    }

    public void setExpertImageUrl(String expertImageUrl) {
        this.expertImageUrl = expertImageUrl;
    }

    public String getAboutExpert() {
        return aboutExpert;
    }

    public void setAboutExpert(String aboutExpert) {
        this.aboutExpert = aboutExpert;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getUserPhoneNumber() {
        return userPhoneNumber;
    }

    public void setUserPhoneNumber(String userPhoneNumber) {
        this.userPhoneNumber = userPhoneNumber;
    }

    public boolean isTest() {
        return isTest;
    }

    public void setTest(boolean test) {
        isTest = test;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideo(boolean video) {
        isVideo = video;
    }

    public String getExpertName() {
        return expertName;
    }

    public void setExpertName(String expertName) {
        this.expertName = expertName;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }


    public PythonLambdaEventRequest() {
    }


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getSentryTrace() {
        return sentryTrace;
    }

    public void setSentryTrace(String sentryTrace) {
        this.sentryTrace = sentryTrace;
    }

    public String getBaggage() {
        return baggage;
    }

    public void setBaggage(String baggage) {
        this.baggage = baggage;
    }
}
