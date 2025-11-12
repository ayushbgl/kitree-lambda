package in.co.kitree.pojos;

import java.util.Map;

public class RequestEvent {
    private RequestContext requestContext;
    private String source;
    private String body;
    private Map<String, String> headers;

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
}
