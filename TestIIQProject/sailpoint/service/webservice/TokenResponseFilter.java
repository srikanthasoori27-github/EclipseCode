/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import sailpoint.object.Configuration;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

/**
 * This ResponseFilter is responsible for detecting that a 401 has occurred, and marking
 * the token as failing so that we can fix soon
 */
public class TokenResponseFilter implements ClientResponseFilter {

    OAuthPooledConnectionManager _manager;
    public TokenResponseFilter(OAuthPooledConnectionManager manager) {
        _manager = manager;
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) {

        // This is in place for testing
        if (getIdentityAIConfig().getBoolean("alwaysFail", false)) {
            clientResponseContext.setStatus(500);
        }

        boolean isFailing = isTokenFailing(clientResponseContext);
        _manager.markFailingToken(isFailing);
    }

    protected Configuration getIdentityAIConfig() {
        return Configuration.getIdentityAIConfig();
    }

    private boolean isTokenFailing(ClientResponseContext clientResponseContext) {
        return clientResponseContext.getStatus() == 401;
    }
}
