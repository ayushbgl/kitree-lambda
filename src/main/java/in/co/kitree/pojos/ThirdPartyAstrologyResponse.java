package in.co.kitree.pojos;

import java.util.Map;

public class ThirdPartyAstrologyResponse {
    private int statusCode;
    private ThirdPartyAstrologyRequest input;
    private ThirdPartyPlanetData[] output;

    public ThirdPartyAstrologyResponse() {
    }

    public ThirdPartyAstrologyResponse(int statusCode, ThirdPartyAstrologyRequest input, 
                                     ThirdPartyPlanetData[] output) {
        this.statusCode = statusCode;
        this.input = input;
        this.output = output;
    }

    // Getters and Setters
    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public ThirdPartyAstrologyRequest getInput() {
        return input;
    }

    public void setInput(ThirdPartyAstrologyRequest input) {
        this.input = input;
    }

    public ThirdPartyPlanetData[] getOutput() {
        return output;
    }

    public void setOutput(ThirdPartyPlanetData[] output) {
        this.output = output;
    }
}
