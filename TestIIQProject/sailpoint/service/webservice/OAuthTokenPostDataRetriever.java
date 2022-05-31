/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.UnknownHostException;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

public class OAuthTokenPostDataRetriever extends OAuthTokenRetriever {

    @Override
    public Response requestNewToken(WebTarget webTarget, String hostname, String path,
                                    String clientId, String clientSecret) throws Exception {
        Response response;
        try {
            Form entity = new Form();
            entity.param("grant_type", "client_credentials");
            entity.param("client_id", clientId);
            entity.param("client_secret", clientSecret);

            response = webTarget.
                    path(path).request().
                    accept(MediaType.APPLICATION_JSON_TYPE).post(Entity.entity(entity, APPLICATION_FORM_URLENCODED_TYPE));
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
}
