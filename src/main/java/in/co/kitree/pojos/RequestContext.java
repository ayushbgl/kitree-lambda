package in.co.kitree.pojos;

public class RequestContext {
    private RequestContextAuthorizer authorizer;
    private RequestContextHttp http;

    public RequestContext() {
    }

    public RequestContextAuthorizer getAuthorizer() {
        return this.authorizer;
    }

    public void setAuthorizer(RequestContextAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public RequestContextHttp getHttp() {
        return http;
    }

    public void setHttp(RequestContextHttp http) {
        this.http = http;
    }
}
