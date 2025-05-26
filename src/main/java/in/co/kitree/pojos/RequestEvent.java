package in.co.kitree.pojos;

public class RequestEvent {
    private RequestContext requestContext;
    private String source;
    private String body;

//    private String headers; // TODO

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
}
