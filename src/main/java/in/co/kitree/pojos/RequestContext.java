package in.co.kitree.pojos;

public class RequestContext {
    private RequestContextAuthorizer authorizer;

    public RequestContext() {
    }

    public RequestContextAuthorizer getAuthorizer() {
        return this.authorizer;
    }

    public void setAuthorizer(RequestContextAuthorizer authorizer) {
        this.authorizer = authorizer;
    }
}
