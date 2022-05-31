package sailpoint.rest;

/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
public class AuthenticationResult {
    public static enum Reason {
        UNSPECIFIED,
        OAUTH_TOKEN_EXPIRED;
    };
    
    private boolean _authenticated;
    private Reason _reason;

    public AuthenticationResult(boolean authenticated) {
        this(authenticated, Reason.UNSPECIFIED);
    }
    
    public AuthenticationResult(boolean authenticated, Reason reason) {
        _authenticated = authenticated;
        _reason = reason;
    }
    
    public boolean isAuthenticated() {
        return _authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        _authenticated = authenticated;
    }

    public Reason getReason() {
        return _reason;
    }

    public void setReason(Reason reason) {
        _reason = reason;
    }
    
    public String getReasonName() {
        return _reason.name();
    }
}
