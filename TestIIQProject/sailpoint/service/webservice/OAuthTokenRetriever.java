/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.UnknownHostException;
import java.util.HashMap;

public class OAuthTokenRetriever {

    private static final Log LOG = LogFactory.getLog(OAuthTokenRetriever.class);
    static ObjectMapper objectMapper = new ObjectMapper();

    public Response requestNewToken(WebTarget webTarget, String hostname, String path,
                                    String clientId, String clientSecret) throws Exception {
        Response response;
        try {

            response = webTarget.queryParam("grant_type", "client_credentials").
                    register(HttpAuthenticationFeature.basic(clientId, clientSecret)).
                    path(path).request().
                    accept(MediaType.APPLICATION_JSON_TYPE).post(null);
        }
        catch (Exception e) {
            Throwable cause = e.getCause();
            if (e instanceof UnknownHostException || cause instanceof UnknownHostException) {
                //Use Generic error messages -rap
                String msg = new Message(MessageKeys.WS_CONNECTION_ERR_CONNECT_UNKNOWN_HOST, hostname).getLocalizedMessage();
                throw new GeneralException(msg);
            }
            else {
                String msg = new Message(MessageKeys.WS_CONNECTION_ERR_CONNECT_FAIL, hostname, e.getLocalizedMessage()).getLocalizedMessage();
                throw new GeneralException(msg);
            }
        }

        return response;
    }

    public OAuthToken getTokenFromResponse(Response response, int forcedExpirySecs) throws Exception {
        OAuthToken oauthToken;

        try {
            int statusCode = response.getStatus();
            if (LOG.isDebugEnabled()) {
                LOG.debug("credentials provider HTTP responseCode = " + statusCode);
            }
            if (statusCode == 401) {
                String msg = new Message(MessageKeys.WS_CONNECTION_ERR_UNAUTH_TOKEN_PROVIDER).getLocalizedMessage();
                throw new GeneralException(msg);
            }
            else if (statusCode < 200 || statusCode >= 300) {
                String msg = new Message(MessageKeys.WS_CONNECTION_ERR_OTHER_TOKEN_PROVIDER, statusCode).getLocalizedMessage();
                throw new GeneralException(msg);
            }

            String json = response.readEntity(String.class);
            HashMap result;
            try {
                result = objectMapper.readValue(json, HashMap.class);
            }
            catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to parse map from JSON: " + json);
                }
                String msg = new Message(MessageKeys.WS_CONNECTION_ERR_BAD_RESPONSE_TOKEN_PROVIDER).getLocalizedMessage();
                throw new GeneralException(msg);
            }

            String access_token;
            Integer expires_in_secs;

            if (!Util.isEmpty(result)) {
                access_token = (String)result.get("access_token");
                if (Util.isNullOrEmpty(access_token)) {
                    String msg = new Message(MessageKeys.WS_CONNECTION_ERR_MISSING_ACCESS_TOKEN).getLocalizedMessage();
                    throw new GeneralException(msg);
                }

                expires_in_secs = (Integer)result.get("expires_in");
                if (expires_in_secs == null || expires_in_secs < 1) {
                    String msg = new Message(MessageKeys.WS_CONNECTION_ERR_MISSING_EXPIRY_TOKEN).getLocalizedMessage();
                    throw new GeneralException(msg);
                }

                if (forcedExpirySecs > 0) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Overriding token expiry length from " + expires_in_secs + " to " + forcedExpirySecs + " secs");
                    }
                    expires_in_secs = forcedExpirySecs;
                }

                oauthToken = new OAuthToken(access_token, expires_in_secs);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("New OAuth token: " + oauthToken.toString());
                }
            }
            else {
                String msg = new Message(MessageKeys.WS_CONNECTION_ERR_EMPTY_RESPONSE_TOKEN_PROVIDER).getLocalizedMessage();
                throw new GeneralException(msg);
            }

        } finally {
            if (response != null) {
                response.close();
            }
        }

        return oauthToken;
    }
}
