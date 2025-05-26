package in.co.kitree.pojos;

public class RequestContextAuthorizerJwtClaims {
    private String email;
    private String user_id;
    private String name;

    public RequestContextAuthorizerJwtClaims() {
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUser_id() {
        return this.user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
