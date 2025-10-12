package in.co.kitree.pojos;

import java.util.Map;

public class FreeAstrologyDivisionalChartResponse {
    private int statusCode;
    private Map<String, Object> output;

    public FreeAstrologyDivisionalChartResponse() {
    }

    public FreeAstrologyDivisionalChartResponse(int statusCode, Map<String, Object> output) {
        this.statusCode = statusCode;
        this.output = output;
    }

    // Getters and Setters
    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }
}
