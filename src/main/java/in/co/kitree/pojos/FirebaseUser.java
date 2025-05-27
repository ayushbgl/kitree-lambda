package in.co.kitree.pojos;

import java.util.Map;
import java.util.HashMap;

public class FirebaseUser {
    // --- New fields for new code/tests ---
    private String uid;
    private String email;
    private String name;
    private Map<String, Long> couponUsageFrequency;
    private String phoneNumber;
    private String photoUrl;
    private boolean emailVerified;
    private long createdAt;
    private long lastLoginAt;

    // --- Legacy fields (do not remove) ---
    private Map<String, String> referredBy;

    public FirebaseUser() {
        // TODO: Consider raising errors or handling nulls for name/phoneNumber in the future
        this.name = "";
        this.phoneNumber = "";
        this.referredBy = new HashMap<>();
    }

    // --- New getters/setters ---
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Long> getCouponUsageFrequency() { return couponUsageFrequency; }
    public void setCouponUsageFrequency(Map<String, Long> couponUsageFrequency) { this.couponUsageFrequency = couponUsageFrequency; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    // --- Legacy getters/setters (do not remove) ---
    public Map<String, String> getReferredBy() { return referredBy; }
    public void setReferredBy(Map<String, String> referredBy) { this.referredBy = referredBy; }

    @Override
    public String toString() {
        return "FirebaseUser{" +
                "uid='" + uid + '\'' +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", couponUsageFrequency=" + couponUsageFrequency +
                ", referredBy=" + referredBy +
                ", photoUrl='" + photoUrl + '\'' +
                ", emailVerified=" + emailVerified +
                ", createdAt=" + createdAt +
                ", lastLoginAt=" + lastLoginAt +
                '}';
    }
}
