package in.co.kitree.pojos;

public class RequestContextAuthorizerJwt {
    private RequestContextAuthorizerJwtClaims claims;

    public RequestContextAuthorizerJwt() {
    }

    public RequestContextAuthorizerJwtClaims getClaims() {
        return this.claims;
    }

    public void setClaims(RequestContextAuthorizerJwtClaims claims) {
        this.claims = claims;
    }
}
