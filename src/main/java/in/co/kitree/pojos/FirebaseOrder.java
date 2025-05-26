package in.co.kitree.pojos;


import java.util.List;

public class FirebaseOrder {
    private String orderId;
    private String userId;
    private String userName;
    private String expertId;
    private String planId;
    private List<String> profileIds;
    private boolean isVideo = false;

    public List<String> getProfileIds() {
        return profileIds;
    }

    public void setProfileIds(List<String> profileIds) {
        this.profileIds = profileIds;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideo(boolean video) {
        isVideo = video;
    }

    public FirebaseOrder() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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
}
