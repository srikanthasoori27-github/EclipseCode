/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

/**
 * This ClientRequestFilter implementation is responsible for inserting the
 * Authorization header (from the OauthToken) into each request.
 */
public class TokenRequestFilter implements ClientRequestFilter {

    private static final Log LOG = LogFactory.getLog(TokenRequestFilter.class);

    private AccessTokenProvider tokenProvider;

    TokenRequestFilter(AccessTokenProvider provider) {
        this.tokenProvider = provider;
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext)  {
        MultivaluedMap<String, Object> headers = clientRequestContext.getHeaders();
        if (headers != null && tokenProvider != null) {
            String access_token = tokenProvider.getAccessToken();
            if (access_token != null) {
                String bearer = "Bearer " + access_token;
                headers.add("Authorization", bearer);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding Authorization header to request: " +
                            bearer.substring(0,15) + "..." + bearer.substring(bearer.length()-8));
                }
            }
        }
    }
}
