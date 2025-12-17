package in.co.kitree.pojos;

import com.google.cloud.Timestamp;

/**
 * POJO representing a wallet transaction record.
 * Stored in users/{userId}/wallet_transactions/{transactionId}
 */
public class WalletTransaction {
    // Transaction type: RECHARGE, CONSULTATION_DEDUCTION, REFUND, CASHBACK, REFERRAL_BONUS
    private String type;
    
    // Source of funds: PAYMENT, CASHBACK, REFERRAL, COUPON, REFUND
    private String source;
    
    // Transaction amount (positive for credits, negative for debits)
    private Double amount;
    
    // ISO currency code (e.g., "INR", "USD")
    private String currency;
    
    // Razorpay payment ID for recharges
    private String paymentId;
    
    // Reference to order document for consultation deductions
    private String orderId;
    
    // Transaction status: PENDING, COMPLETED, FAILED
    private String status;
    
    // Transaction creation timestamp
    private Timestamp createdAt;
    
    // Human-readable description (translated in frontend)
    private String description;
    
    // Flattened fields (instead of nested metadata)
    private String couponCode;        // Coupon code if source is COUPON
    private String referralCode;      // Referral code if source is REFERRAL
    private String referrerId;        // User ID of referrer if source is REFERRAL
    private String cashbackSourceOrderId;  // Order ID that generated cashback if source is CASHBACK
    private String cashbackReason;    // Reason for cashback (e.g., "FIRST_CONSULTATION", "REFERRAL_BONUS")
    private String refundReason;      // Reason for refund if source is REFUND

    public WalletTransaction() {}

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public String getReferrerId() { return referrerId; }
    public void setReferrerId(String referrerId) { this.referrerId = referrerId; }

    public String getCashbackSourceOrderId() { return cashbackSourceOrderId; }
    public void setCashbackSourceOrderId(String cashbackSourceOrderId) { this.cashbackSourceOrderId = cashbackSourceOrderId; }

    public String getCashbackReason() { return cashbackReason; }
    public void setCashbackReason(String cashbackReason) { this.cashbackReason = cashbackReason; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    @Override
    public String toString() {
        return "WalletTransaction{" +
                "type='" + type + '\'' +
                ", source='" + source + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", paymentId='" + paymentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", description='" + description + '\'' +
                '}';
    }
}
