package in.co.kitree.pojos;

import java.util.Map;

public class FirebaseUser {
    private String uid;
    private String name;
    private String phoneNumber;
    private Map<String, Long> couponUsageFrequency;

    private Map<String, String> referredBy;

    public FirebaseUser() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Map<String, Long> getCouponUsageFrequency() {
        return couponUsageFrequency;
    }

    public void setCouponUsageFrequency(Map<String, Long> couponUsageFrequency) {
        this.couponUsageFrequency = couponUsageFrequency;
    }

    public Map<String, String> getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(Map<String, String> referredBy) {
        this.referredBy = referredBy;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "FirebaseUser{" +
                "uid='" + uid + '\'' +
                "name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", couponUsageFrequency=" + couponUsageFrequency +
                ", referredBy=" + referredBy +
                '}';
    }
}
