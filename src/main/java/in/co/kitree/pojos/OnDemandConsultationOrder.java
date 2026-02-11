package in.co.kitree.pojos;

import com.google.cloud.Timestamp;

/**
 * POJO representing an on-demand consultation order.
 * This extends the base order structure with additional fields specific to on-demand consultations.
 * Stored in users/{userId}/orders/{orderId} with type = "ON_DEMAND_CONSULTATION"
 */
public class OnDemandConsultationOrder {
    // Base order fields (inherited from existing order structure)
    private String orderId;
    private String userId;
    private String userName;
    private String expertId;
    private String expertName;
    private String planId;
    private Timestamp createdAt;
    
    // Order type: "ON_DEMAND_CONSULTATION"
    private String type;
    
    // Consultation type: "audio", "video", or "chat"
    private String consultationType;
    
    // Category from plan (e.g., "HOROSCOPE", "TAROT")
    private String category;
    
    // Rate per minute at consultation start (from plan)
    private Double expertRatePerMinute;
    
    // Currency used for this consultation
    private String currency;
    
    // Maximum duration in seconds based on wallet balance
    private Long maxAllowedDuration;
    
    // When consultation actually started (call connected)
    private Timestamp startTime;
    
    // When consultation ended
    private Timestamp endTime;
    
    // Final consultation duration in seconds
    private Long durationSeconds;
    
    // Final cost calculated
    private Double cost;
    
    // Platform fee percentage at time of consultation (stored with order)
    private Double platformFeePercent;
    
    // Calculated platform fee amount
    private Double platformFeeAmount;
    
    // Amount credited to expert (cost - platformFeeAmount)
    private Double expertEarnings;
    
    // Consultation status: INITIATED, CONNECTED, COMPLETED, TERMINATED, FAILED
    private String status;
    
    // GetStream call ID (e.g., "consultation_audio:{orderId}") - only for audio/video
    private String streamCallCid;

    // Chat session ID - only for chat consultations (future)
    private String chatSessionId;

    // Dual-participant billing tracking (simple single-interval)
    // When user joined the Stream call
    private Timestamp userJoinedAt;

    // When expert joined the Stream call
    private Timestamp expertJoinedAt;

    // When both participants were in the call together (billing starts)
    private Timestamp bothParticipantsJoinedAt;

    // Interval-based billing (supports reconnections)
    // Stream session ID for this call
    private String streamSessionId;

    // User's join/leave intervals: [{joined_at, left_at}, ...]
    // Stored as List<Map<String, Object>> in Firestore
    private java.util.List<java.util.Map<String, Object>> userIntervals;

    // Expert's join/leave intervals: [{joined_at, left_at}, ...]
    private java.util.List<java.util.Map<String, Object>> expertIntervals;

    // Billing status: NOT_STARTED, ACTIVE, FINALIZED
    private String billingStatus;

    // Running totals for real-time billing
    private Long totalBilledSeconds;
    private Double totalBilledAmount;

    // Timestamp of last billing event (for cron staleness check)
    private Timestamp lastBillingEventAt;

    // Actual billable seconds (time when both participants were present)
    private Long billableSeconds;

    // Failure reason (for FAILED orders)
    private String failureReason;

    // Consultation summary (AI-generated analysis of the call)
    // Stored as a Map with structured fields: headline, brief_summary, topics, predictions, remedies, etc.
    private java.util.Map<String, Object> summary;

    // Summary generation tracking
    // Status: PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    private String summaryStatus;

    // Number of retry attempts for summary generation
    private Long summaryRetryCount;

    // Timestamp of last summary generation attempt
    private Timestamp summaryLastAttempt;

    // Last error message from summary generation (for debugging)
    private String summaryError;

    public OnDemandConsultationOrder() {
        this.type = "ON_DEMAND_CONSULTATION";
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getExpertId() { return expertId; }
    public void setExpertId(String expertId) { this.expertId = expertId; }

    public String getExpertName() { return expertName; }
    public void setExpertName(String expertName) { this.expertName = expertName; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getConsultationType() { return consultationType; }
    public void setConsultationType(String consultationType) { this.consultationType = consultationType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getExpertRatePerMinute() { return expertRatePerMinute; }
    public void setExpertRatePerMinute(Double expertRatePerMinute) { this.expertRatePerMinute = expertRatePerMinute; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Long getMaxAllowedDuration() { return maxAllowedDuration; }
    public void setMaxAllowedDuration(Long maxAllowedDuration) { this.maxAllowedDuration = maxAllowedDuration; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }

    public Double getPlatformFeePercent() { return platformFeePercent; }
    public void setPlatformFeePercent(Double platformFeePercent) { this.platformFeePercent = platformFeePercent; }

    public Double getPlatformFeeAmount() { return platformFeeAmount; }
    public void setPlatformFeeAmount(Double platformFeeAmount) { this.platformFeeAmount = platformFeeAmount; }

    public Double getExpertEarnings() { return expertEarnings; }
    public void setExpertEarnings(Double expertEarnings) { this.expertEarnings = expertEarnings; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStreamCallCid() { return streamCallCid; }
    public void setStreamCallCid(String streamCallCid) { this.streamCallCid = streamCallCid; }

    public String getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(String chatSessionId) { this.chatSessionId = chatSessionId; }

    public Timestamp getUserJoinedAt() { return userJoinedAt; }
    public void setUserJoinedAt(Timestamp userJoinedAt) { this.userJoinedAt = userJoinedAt; }

    public Timestamp getExpertJoinedAt() { return expertJoinedAt; }
    public void setExpertJoinedAt(Timestamp expertJoinedAt) { this.expertJoinedAt = expertJoinedAt; }

    public Timestamp getBothParticipantsJoinedAt() { return bothParticipantsJoinedAt; }
    public void setBothParticipantsJoinedAt(Timestamp bothParticipantsJoinedAt) { this.bothParticipantsJoinedAt = bothParticipantsJoinedAt; }

    // Interval-based billing getters/setters
    public String getStreamSessionId() { return streamSessionId; }
    public void setStreamSessionId(String streamSessionId) { this.streamSessionId = streamSessionId; }

    public java.util.List<java.util.Map<String, Object>> getUserIntervals() { return userIntervals; }
    public void setUserIntervals(java.util.List<java.util.Map<String, Object>> userIntervals) { this.userIntervals = userIntervals; }

    public java.util.List<java.util.Map<String, Object>> getExpertIntervals() { return expertIntervals; }
    public void setExpertIntervals(java.util.List<java.util.Map<String, Object>> expertIntervals) { this.expertIntervals = expertIntervals; }

    public String getBillingStatus() { return billingStatus; }
    public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }

    public Long getTotalBilledSeconds() { return totalBilledSeconds; }
    public void setTotalBilledSeconds(Long totalBilledSeconds) { this.totalBilledSeconds = totalBilledSeconds; }

    public Double getTotalBilledAmount() { return totalBilledAmount; }
    public void setTotalBilledAmount(Double totalBilledAmount) { this.totalBilledAmount = totalBilledAmount; }

    public Timestamp getLastBillingEventAt() { return lastBillingEventAt; }
    public void setLastBillingEventAt(Timestamp lastBillingEventAt) { this.lastBillingEventAt = lastBillingEventAt; }

    public Long getBillableSeconds() { return billableSeconds; }
    public void setBillableSeconds(Long billableSeconds) { this.billableSeconds = billableSeconds; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public java.util.Map<String, Object> getSummary() { return summary; }
    public void setSummary(java.util.Map<String, Object> summary) { this.summary = summary; }

    public String getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(String summaryStatus) { this.summaryStatus = summaryStatus; }

    public Long getSummaryRetryCount() { return summaryRetryCount; }
    public void setSummaryRetryCount(Long summaryRetryCount) { this.summaryRetryCount = summaryRetryCount; }

    public Timestamp getSummaryLastAttempt() { return summaryLastAttempt; }
    public void setSummaryLastAttempt(Timestamp summaryLastAttempt) { this.summaryLastAttempt = summaryLastAttempt; }

    public String getSummaryError() { return summaryError; }
    public void setSummaryError(String summaryError) { this.summaryError = summaryError; }

    @Override
    public String toString() {
        return "OnDemandConsultationOrder{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", expertId='" + expertId + '\'' +
                ", type='" + type + '\'' +
                ", consultationType='" + consultationType + '\'' +
                ", category='" + category + '\'' +
                ", expertRatePerMinute=" + expertRatePerMinute +
                ", currency='" + currency + '\'' +
                ", maxAllowedDuration=" + maxAllowedDuration +
                ", status='" + status + '\'' +
                ", cost=" + cost +
                ", platformFeePercent=" + platformFeePercent +
                '}';
    }
}
