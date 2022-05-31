package sailpoint.service.oauth;

import java.security.GeneralSecurityException;

/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
public class OAuthTokenExpiredException extends GeneralSecurityException {
    private static final long serialVersionUID = 1L;

    public OAuthTokenExpiredException(String msg) {
        super(msg);
    }
}
