package in.co.kitree.pojos;

public class ThirdPartyDashaEntry {
    private String lord;
    private String start_time;
    private String end_time;

    public ThirdPartyDashaEntry() {
    }

    public ThirdPartyDashaEntry(String lord, String start_time, String end_time) {
        this.lord = lord;
        this.start_time = start_time;
        this.end_time = end_time;
    }

    // Getters and Setters
    public String getLord() {
        return lord;
    }

    public void setLord(String lord) {
        this.lord = lord;
    }

    public String getStart_time() {
        return start_time;
    }

    public void setStart_time(String start_time) {
        this.start_time = start_time;
    }

    public String getEnd_time() {
        return end_time;
    }

    public void setEnd_time(String end_time) {
        this.end_time = end_time;
    }
}
