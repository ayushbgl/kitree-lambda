package in.co.kitree.pojos;

import org.json.JSONObject;

import java.util.Map;

public class RazorpayWebhookBody {
    Map<String, String> headers;
    String body;

    public RazorpayWebhookBody() {
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}


