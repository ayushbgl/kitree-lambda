package in.co.kitree.pojos;

import java.util.List;
import java.util.Map;

public class RequestBody {
    private String function;
    private String serviceName; // To buy a service
    private String base64Image;
    private String planName; // To buy a service

    // Payment gateway fields (gateway-agnostic)
    private String gatewayOrderId; // Payment gateway's order ID (for verification)
    private String gatewayPaymentId; // Payment gateway's payment ID
    private String gatewaySignature; // Payment gateway's signature for verification
    private String gatewaySubscriptionId; // Payment gateway's subscription ID
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
    private Boolean useWalletBalance; // Whether to use wallet balance for checkout payment

    // Booking metrics fields
    private Long startDate; // Start date in milliseconds for metrics filtering
    private Long endDate; // End date in milliseconds for metrics filtering
    private String bookingType; // "all", "scheduled", "onDemand", "product"

    // Payout fields (for admin recording payouts)
    private String payoutMethod; // "BANK_TRANSFER", "UPI", etc.
    private String payoutReference; // Transaction reference ID
    private String notes; // Optional notes for payout

    // Platform fee config fields (for admin setting platform fees)
    private Double defaultFeePercent; // Default platform fee percentage (e.g., 10.0 for 10%)
    private Map<String, Double> feeByType; // Fee by order type (e.g., "CONSULTATION": 10.0, "ON_DEMAND_CONSULTATION": 15.0)
    private Map<String, Double> feeByCategory; // Fee by category (e.g., "HOROSCOPE": 10.0, "TAROT": 12.0)

    // Billing recalculation fields
    private String callCid; // Stream call CID for recalculate_charge function (format: {type}:{id})

    // Product ecommerce fields
    private String productId; // Platform product ID
    private Integer quantity; // Quantity of product to order
    private List<Map<String, Object>> items; // Multi-item order: list of {productId, quantity}
    private String trackingNumber; // Shipping tracking number
    private String newStatus; // New order status for updates
    private Double sellerPriceInr; // Seller's price for a product
    private Boolean isWhiteLabel; // Whether to use white-label (no platform branding)
    private String shippingMode; // "PLATFORM" or "SELF"
    private Double selfShippingCostInr; // Seller's shipping cost for self-shipping
    private Integer selfStockQuantity; // Seller's inventory count for self-shipping
    private String customDescription; // Seller's custom product description
    private Boolean isEnabled; // Whether product is enabled for selling
    private String statusFilter; // Filter orders by status
    private List<PlatformProduct> productsToSeed; // Products to seed into platform catalog

    // Session/Webinar/Course fields
    private String title;                    // Session title
    private String description;              // Session description
    private Long scheduledStartTime;         // Scheduled start time in milliseconds
    private Integer durationMinutes;         // Planned duration in minutes
    private Double price;                    // Session price (0 for free)
    private Integer maxParticipants;         // Maximum registrations allowed
    private Integer sessionCount;            // 1 for standalone, N for course
    private String interactionMode;          // "cozy" | "classroom" | "broadcast"
    private Boolean giftsEnabled;            // Whether gifts are enabled
    private List<Map<String, Object>> giftOptions; // [{id, amount}, ...]
    private String targetUserId;             // For promote/demote/kick operations
    private String targetOrderId;            // For promote/demote/kick operations
    private String giftId;                   // Gift ID for send_gift
    private Integer sessionNumber;           // For course sessions (1, 2, 3...)
    private String userPhotoUrl;             // User's photo URL for participant display
    private Integer limit;                   // Pagination limit for queries

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

    // Payment gateway getters/setters
    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public void setGatewayOrderId(String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public String getGatewaySignature() {
        return gatewaySignature;
    }

    public void setGatewaySignature(String gatewaySignature) {
        this.gatewaySignature = gatewaySignature;
    }

    public String getGatewaySubscriptionId() {
        return gatewaySubscriptionId;
    }

    public void setGatewaySubscriptionId(String gatewaySubscriptionId) {
        this.gatewaySubscriptionId = gatewaySubscriptionId;
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

    public Boolean getUseWalletBalance() {
        return useWalletBalance;
    }

    public void setUseWalletBalance(Boolean useWalletBalance) {
        this.useWalletBalance = useWalletBalance;
    }

    // Booking metrics getters/setters
    public Long getStartDate() {
        return startDate;
    }

    public void setStartDate(Long startDate) {
        this.startDate = startDate;
    }

    public Long getEndDate() {
        return endDate;
    }

    public void setEndDate(Long endDate) {
        this.endDate = endDate;
    }

    public String getBookingType() {
        return bookingType;
    }

    public void setBookingType(String bookingType) {
        this.bookingType = bookingType;
    }

    // Payout getters/setters
    public String getPayoutMethod() {
        return payoutMethod;
    }

    public void setPayoutMethod(String payoutMethod) {
        this.payoutMethod = payoutMethod;
    }

    public String getPayoutReference() {
        return payoutReference;
    }

    public void setPayoutReference(String payoutReference) {
        this.payoutReference = payoutReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Platform fee config getters/setters
    public Double getDefaultFeePercent() {
        return defaultFeePercent;
    }

    public void setDefaultFeePercent(Double defaultFeePercent) {
        this.defaultFeePercent = defaultFeePercent;
    }

    public Map<String, Double> getFeeByType() {
        return feeByType;
    }

    public void setFeeByType(Map<String, Double> feeByType) {
        this.feeByType = feeByType;
    }

    public Map<String, Double> getFeeByCategory() {
        return feeByCategory;
    }

    public void setFeeByCategory(Map<String, Double> feeByCategory) {
        this.feeByCategory = feeByCategory;
    }

    // Billing recalculation getters/setters
    public String getCallCid() {
        return callCid;
    }

    public void setCallCid(String callCid) {
        this.callCid = callCid;
    }

    // Product ecommerce getters/setters
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public Double getSellerPriceInr() {
        return sellerPriceInr;
    }

    public void setSellerPriceInr(Double sellerPriceInr) {
        this.sellerPriceInr = sellerPriceInr;
    }

    public Boolean getIsWhiteLabel() {
        return isWhiteLabel;
    }

    public void setIsWhiteLabel(Boolean isWhiteLabel) {
        this.isWhiteLabel = isWhiteLabel;
    }

    public String getShippingMode() {
        return shippingMode;
    }

    public void setShippingMode(String shippingMode) {
        this.shippingMode = shippingMode;
    }

    public Double getSelfShippingCostInr() {
        return selfShippingCostInr;
    }

    public void setSelfShippingCostInr(Double selfShippingCostInr) {
        this.selfShippingCostInr = selfShippingCostInr;
    }

    public Integer getSelfStockQuantity() {
        return selfStockQuantity;
    }

    public void setSelfStockQuantity(Integer selfStockQuantity) {
        this.selfStockQuantity = selfStockQuantity;
    }

    public String getCustomDescription() {
        return customDescription;
    }

    public void setCustomDescription(String customDescription) {
        this.customDescription = customDescription;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public String getStatusFilter() {
        return statusFilter;
    }

    public void setStatusFilter(String statusFilter) {
        this.statusFilter = statusFilter;
    }

    public List<PlatformProduct> getProductsToSeed() {
        return productsToSeed;
    }

    public void setProductsToSeed(List<PlatformProduct> productsToSeed) {
        this.productsToSeed = productsToSeed;
    }

    // Session/Webinar/Course getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(Long scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public Integer getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(Integer sessionCount) {
        this.sessionCount = sessionCount;
    }

    public String getInteractionMode() {
        return interactionMode;
    }

    public void setInteractionMode(String interactionMode) {
        this.interactionMode = interactionMode;
    }

    public Boolean getGiftsEnabled() {
        return giftsEnabled;
    }

    public void setGiftsEnabled(Boolean giftsEnabled) {
        this.giftsEnabled = giftsEnabled;
    }

    public List<Map<String, Object>> getGiftOptions() {
        return giftOptions;
    }

    public void setGiftOptions(List<Map<String, Object>> giftOptions) {
        this.giftOptions = giftOptions;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetOrderId() {
        return targetOrderId;
    }

    public void setTargetOrderId(String targetOrderId) {
        this.targetOrderId = targetOrderId;
    }

    public String getGiftId() {
        return giftId;
    }

    public void setGiftId(String giftId) {
        this.giftId = giftId;
    }

    public Integer getSessionNumber() {
        return sessionNumber;
    }

    public void setSessionNumber(Integer sessionNumber) {
        this.sessionNumber = sessionNumber;
    }

    public String getUserPhotoUrl() {
        return userPhotoUrl;
    }

    public void setUserPhotoUrl(String userPhotoUrl) {
        this.userPhotoUrl = userPhotoUrl;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
