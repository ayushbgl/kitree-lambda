package in.co.kitree.pojos;

public class RequestContextAuthorizer {
    private RequestContextAuthorizerJwt jwt;

    public RequestContextAuthorizer() {
    }

    public RequestContextAuthorizerJwt getJwt() {
        return this.jwt;
    }

    public void setJwt(RequestContextAuthorizerJwt jwt) {
        this.jwt = jwt;
    }
}
