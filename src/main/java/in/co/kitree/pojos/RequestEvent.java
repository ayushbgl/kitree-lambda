package in.co.kitree.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class RequestEvent {
    private RequestContext requestContext;
    private String source;
    private String body;
    private Map<String, String> headers;
    @JsonProperty("detail-type")
    private String detailType; // For EventBridge scheduled events
    private String rawPath;           // Lambda Function URL path, e.g., "/webhooks/stream"
    private String rawQueryString;    // Query params, e.g., "param1=value1"
    private String httpMethod;        // HTTP method, e.g., "POST", "GET"

    public RequestEvent() {
    }

    public RequestContext getRequestContext() {
        return this.requestContext;
    }

    public void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getDetailType() {
        return detailType;
    }

    public void setDetailType(String detailType) {
        this.detailType = detailType;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getRawQueryString() {
        return rawQueryString;
    }

    public void setRawQueryString(String rawQueryString) {
        this.rawQueryString = rawQueryString;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
}
