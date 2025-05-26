package in.co.kitree.pojos;

import java.util.List;
import java.util.Map;

public class RequestBody {
    private String function;
    private String serviceName; // To buy a service
    private String base64Image;
    private String planName; // To buy a service
    private String razorpaySubscriptionId; // To verify payment
    private String razorpayOrderId; // To verify payment
    private String razorpayPaymentId; // To verify payment
    private String razorpaySignature; // To verify payment
    private String adminSecret;
    private String adminUid;
    private String certificateHolderName;
    private String certificateDate;
    private String certificateCourse;
    private RazorpayWebhookBody razorpayWebhookBody;

    private String orderId;
    private String versionCode;

    private String couponCode;
    private String expertId;
    private String expertName;
    private String planId;

    // For filtering category in metrics
    private String category;

    // For filtering date range in metrics
    private List<String> dateRangeFilter;

    private String rangeStart;
    private String rangeEnd;

    private String userTimeZone;

    private String userId;

    private String appointmentDate;

    private Map<String, String> appointmentSlot;

    private String language;
    private String type;
    private Long giftAmount; // Virtual Gifts within Webinar

    private Double amount; // Currently for personalized bracelets, also have a backend validation for this
    private List<String> beads;
    private Map<String, Object> address;
    private Map<String, Object> scannerDetails;
    private String userName;
    private String dob;

    public Map<String, Object> getScannerDetails() {
        return scannerDetails;
    }

    public void setScannerDetails(Map<String, Object> scannerDetails) {
        this.scannerDetails = scannerDetails;
    }

    public Map<String, Object> getAddress() {
        return address;
    }

    public void setAddress(Map<String, Object> address) {
        this.address = address;
    }

    public List<String> getBeads() {
        return beads;
    }

    public void setBeads(List<String> beads) {
        this.beads = beads;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Long getGiftAmount() {
        return giftAmount;
    }

    public void setGiftAmount(Long giftAmount) {
        this.giftAmount = giftAmount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public String getExpertName() {
        return expertName;
    }

    public void setExpertName(String expertName) {
        this.expertName = expertName;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }


    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }


    public RequestBody() {
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }

    public String getAdminSecret() {
        return adminSecret;
    }

    public void setAdminSecret(String adminSecret) {
        this.adminSecret = adminSecret;
    }

    public String getAdminUid() {
        return adminUid;
    }

    public void setAdminUid(String adminUid) {
        this.adminUid = adminUid;
    }

    public String getRazorpaySubscriptionId() {
        return razorpaySubscriptionId;
    }

    public void setRazorpaySubscriptionId(String razorpaySubscriptionId) {
        this.razorpaySubscriptionId = razorpaySubscriptionId;
    }

    public RazorpayWebhookBody getRazorpayWebhookBody() {
        return razorpayWebhookBody;
    }

    public void setRazorpayWebhookBody(RazorpayWebhookBody razorpayWebhookBody) {
        this.razorpayWebhookBody = razorpayWebhookBody;
    }

    public String getCertificateHolderName() {
        return certificateHolderName;
    }

    public void setCertificateHolderName(String certificateHolderName) {
        this.certificateHolderName = certificateHolderName;
    }

    public String getCertificateDate() {
        return certificateDate;
    }

    public void setCertificateDate(String certificateDate) {
        this.certificateDate = certificateDate;
    }

    public String getCertificateCourse() {
        return certificateCourse;
    }

    public void setCertificateCourse(String certificateCourse) {
        this.certificateCourse = certificateCourse;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getDateRangeFilter() {
        return dateRangeFilter;
    }

    public void setDateRangeFilter(List<String> dateRangeFilter) {
        this.dateRangeFilter = dateRangeFilter;
    }

    public String getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(String rangeStart) {
        this.rangeStart = rangeStart;
    }

    public String getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(String rangeEnd) {
        this.rangeEnd = rangeEnd;
    }

    public String getUserTimeZone() {
        return userTimeZone;
    }

    public void setUserTimeZone(String userTimeZone) {
        this.userTimeZone = userTimeZone;
    }

    public Map<String, String> getAppointmentSlot() {
        return appointmentSlot;
    }

    public void setAppointmentSlot(Map<String, String> appointmentSlot) {
        this.appointmentSlot = appointmentSlot;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }
}
