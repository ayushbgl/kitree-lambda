package in.co.kitree.pojos;

import com.google.cloud.Timestamp;

public class ServicePlan {
    // --- New fields for new code/tests ---
    private String id;
    private String name;
    private String description;
    private Double amount;
    private String expertId;
    private boolean active;
    private long createdAt;
    private long updatedAt;

    // --- Legacy fields (do not remove) ---
    private String planId;
    private String category;
    private String type;
    private String subtype;
    private String razorpayId;
    private String currency;
    private boolean isSubscription;
    private boolean isVideo;
    private Timestamp date;
    private String title;
    private Timestamp sessionStartedAt;
    private Timestamp sessionCompletedAt;
    private long duration;
    private String durationUnit;

    // On-demand consultation rate fields
    private Double onDemandRatePerMinuteAudio;
    private Double onDemandRatePerMinuteVideo;
    private Double onDemandRatePerMinuteChat;
    private String onDemandCurrency; // Currency for on-demand rates (defaults to plan currency)

    public ServicePlan() {}

    // --- New getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getExpertId() { return expertId; }
    public void setExpertId(String expertId) { this.expertId = expertId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    // --- Legacy getters/setters (do not remove) ---
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }
    public String getRazorpayId() { return razorpayId; }
    public void setRazorpayId(String razorpayId) { this.razorpayId = razorpayId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isSubscription() { return isSubscription; }
    public void setSubscription(boolean subscription) { isSubscription = subscription; }
    public boolean isVideo() { return isVideo; }
    public void setVideo(boolean video) { isVideo = video; }
    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Timestamp getSessionStartedAt() { return sessionStartedAt; }
    public void setSessionStartedAt(Timestamp sessionStartedAt) { this.sessionStartedAt = sessionStartedAt; }
    public Timestamp getSessionCompletedAt() { return sessionCompletedAt; }
    public void setSessionCompletedAt(Timestamp sessionCompletedAt) { this.sessionCompletedAt = sessionCompletedAt; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }

    // On-demand consultation rate getters/setters
    public Double getOnDemandRatePerMinuteAudio() { return onDemandRatePerMinuteAudio; }
    public void setOnDemandRatePerMinuteAudio(Double onDemandRatePerMinuteAudio) { this.onDemandRatePerMinuteAudio = onDemandRatePerMinuteAudio; }
    public Double getOnDemandRatePerMinuteVideo() { return onDemandRatePerMinuteVideo; }
    public void setOnDemandRatePerMinuteVideo(Double onDemandRatePerMinuteVideo) { this.onDemandRatePerMinuteVideo = onDemandRatePerMinuteVideo; }
    public Double getOnDemandRatePerMinuteChat() { return onDemandRatePerMinuteChat; }
    public void setOnDemandRatePerMinuteChat(Double onDemandRatePerMinuteChat) { this.onDemandRatePerMinuteChat = onDemandRatePerMinuteChat; }
    public String getOnDemandCurrency() { return onDemandCurrency; }
    public void setOnDemandCurrency(String onDemandCurrency) { this.onDemandCurrency = onDemandCurrency; }

    @Override
    public String toString() {
        return "ServicePlan{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", expertId='" + expertId + '\'' +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", planId='" + planId + '\'' +
                ", category='" + category + '\'' +
                ", type='" + type + '\'' +
                ", razorpayId='" + razorpayId + '\'' +
                ", currency='" + currency + '\'' +
                ", isSubscription=" + isSubscription +
                ", isVideo=" + isVideo +
                ", date=" + date +
                '}';
    }
}
