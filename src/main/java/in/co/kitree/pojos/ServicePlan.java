package in.co.kitree.pojos;

import com.google.cloud.Timestamp;


public class ServicePlan {
    private String planId;

    private String category;
    private String type;
    private String subtype;
    private String razorpayId;
    private Double amount;

    private String currency;
    private boolean isSubscription;
    private boolean isVideo;

    private Timestamp date;
    private String title;

    private Timestamp sessionStartedAt;
    private Timestamp sessionCompletedAt;

    private long duration;
    private String durationUnit;

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(String durationUnit) {
        this.durationUnit = durationUnit;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public Timestamp getSessionCompletedAt() {
        return sessionCompletedAt;
    }

    public void setSessionCompletedAt(Timestamp sessionCompletedAt) {
        this.sessionCompletedAt = sessionCompletedAt;
    }

    public Timestamp getSessionStartedAt() {
        return sessionStartedAt;
    }

    public void setSessionStartedAt(Timestamp sessionStartedAt) {
        this.sessionStartedAt = sessionStartedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPlanId() {
        return planId;
    }

    public Timestamp getDate() {
        return date;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideo(boolean video) {
        isVideo = video;
    }

    public ServicePlan() {
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRazorpayId() {
        return razorpayId;
    }

    public void setRazorpayId(String razorpayId) {
        this.razorpayId = razorpayId;
    }


    public Double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public boolean isSubscription() {
        return isSubscription;
    }

    public void setSubscription(boolean subscription) {
        isSubscription = subscription;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    @Override
    public String toString() {
        return "ServicePlan{" +
                "planId='" + planId + '\'' +
                ", category='" + category + '\'' +
                ", type='" + type + '\'' +
                ", razorpayId='" + razorpayId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", isSubscription=" + isSubscription +
                ", isVideo=" + isVideo +
                ", date=" + date +
                '}';
    }
}
