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

    // Horoscope fields
    private Integer horoscopeDate;
    private Integer horoscopeMonth;
    private Integer horoscopeYear;
    private Integer horoscopeHour;
    private Integer horoscopeMinute;
    private Double horoscopeLatitude;
    private Double horoscopeLongitude;

    // Dasha fields
    private Integer dashaDate;
    private Integer dashaMonth;
    private Integer dashaYear;
    private Integer dashaHour;
    private Integer dashaMinute;
    private Double dashaLatitude;
    private Double dashaLongitude;
    private List<String> dashaPrefix;

    // Divisional charts fields
    private List<Integer> divisionalChartNumbers;

    // Wallet and On-Demand Consultation fields
    private String currency; // Currency for wallet operations (e.g., "INR", "USD")
    private String consultationType; // "audio", "video", or "chat" for on-demand consultations
    private Double additionalAmount; // For mid-consultation recharge to extend duration

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

    public Integer getHoroscopeDate() {
        return horoscopeDate;
    }

    public void setHoroscopeDate(Integer horoscopeDate) {
        this.horoscopeDate = horoscopeDate;
    }

    public Integer getHoroscopeMonth() {
        return horoscopeMonth;
    }

    public void setHoroscopeMonth(Integer horoscopeMonth) {
        this.horoscopeMonth = horoscopeMonth;
    }

    public Integer getHoroscopeYear() {
        return horoscopeYear;
    }

    public void setHoroscopeYear(Integer horoscopeYear) {
        this.horoscopeYear = horoscopeYear;
    }

    public Integer getHoroscopeHour() {
        return horoscopeHour;
    }

    public void setHoroscopeHour(Integer horoscopeHour) {
        this.horoscopeHour = horoscopeHour;
    }

    public Integer getHoroscopeMinute() {
        return horoscopeMinute;
    }

    public void setHoroscopeMinute(Integer horoscopeMinute) {
        this.horoscopeMinute = horoscopeMinute;
    }

    public Double getHoroscopeLatitude() {
        return horoscopeLatitude;
    }

    public void setHoroscopeLatitude(Double horoscopeLatitude) {
        this.horoscopeLatitude = horoscopeLatitude;
    }

    public Double getHoroscopeLongitude() {
        return horoscopeLongitude;
    }

    public void setHoroscopeLongitude(Double horoscopeLongitude) {
        this.horoscopeLongitude = horoscopeLongitude;
    }

    public Integer getDashaDate() {
        return dashaDate;
    }

    public void setDashaDate(Integer dashaDate) {
        this.dashaDate = dashaDate;
    }

    public Integer getDashaMonth() {
        return dashaMonth;
    }

    public void setDashaMonth(Integer dashaMonth) {
        this.dashaMonth = dashaMonth;
    }

    public Integer getDashaYear() {
        return dashaYear;
    }

    public void setDashaYear(Integer dashaYear) {
        this.dashaYear = dashaYear;
    }

    public Integer getDashaHour() {
        return dashaHour;
    }

    public void setDashaHour(Integer dashaHour) {
        this.dashaHour = dashaHour;
    }

    public Integer getDashaMinute() {
        return dashaMinute;
    }

    public void setDashaMinute(Integer dashaMinute) {
        this.dashaMinute = dashaMinute;
    }

    public Double getDashaLatitude() {
        return dashaLatitude;
    }

    public void setDashaLatitude(Double dashaLatitude) {
        this.dashaLatitude = dashaLatitude;
    }

    public Double getDashaLongitude() {
        return dashaLongitude;
    }

    public void setDashaLongitude(Double dashaLongitude) {
        this.dashaLongitude = dashaLongitude;
    }

    public List<String> getDashaPrefix() {
        return dashaPrefix;
    }

    public void setDashaPrefix(List<String> dashaPrefix) {
        this.dashaPrefix = dashaPrefix;
    }

    public List<Integer> getDivisionalChartNumbers() {
        return divisionalChartNumbers;
    }

    public void setDivisionalChartNumbers(List<Integer> divisionalChartNumbers) {
        this.divisionalChartNumbers = divisionalChartNumbers;
    }

    // Wallet and On-Demand Consultation getters/setters
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getConsultationType() {
        return consultationType;
    }

    public void setConsultationType(String consultationType) {
        this.consultationType = consultationType;
    }

    public Double getAdditionalAmount() {
        return additionalAmount;
    }

    public void setAdditionalAmount(Double additionalAmount) {
        this.additionalAmount = additionalAmount;
    }
}
