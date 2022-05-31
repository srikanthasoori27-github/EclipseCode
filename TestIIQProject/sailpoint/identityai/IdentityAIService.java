/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.identityai;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Configuration;
import sailpoint.service.webservice.ConnectionManager;
import sailpoint.service.webservice.ConnectionPoolState;
import sailpoint.service.webservice.OAuthBaseConnectionManager;
import sailpoint.service.webservice.OAuthPooledConnectionManager;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.system.IdentityAIConfigBean;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


public class IdentityAIService {

    private static final Log LOG = LogFactory.getLog(IdentityAIService.class);

//    ConnectionManager connectionManager = null;

    private static ConnectionManager connectionManager;
    static {
        connectionManager = new OAuthPooledConnectionManager(
                new OAuthBaseConnectionManager.OAuthDynamicConfigProvider(() -> Configuration.getIdentityAIConfig()), new ConnectionPoolState());
    }

    public static ConnectionManager connectionManager() { return connectionManager; }

    public IdentityAIService(Configuration identityAIConfig) {
    }

    public boolean isConfigured() {
        return connectionManager.isConfigured();
    }

    public WebTarget getWebTarget() throws Exception {
        return connectionManager.getWebTarget();
    }

    /**
     * Using the given Configuration, attempt to make a connection to IdentityAI
     * @param configuration the Configuration to use for authentication setting
     * @throws GeneralException if cannot make successful connection to IdentityAI
     */
    public static void testUnpooledConnection(Configuration configuration) throws Exception {
        WebTarget webTarget = new OAuthBaseConnectionManager(new OAuthBaseConnectionManager.OAuthStaticConfigProvider(configuration)).getWebTarget();
        testWebTarget(webTarget);
    }

    public static void testPooledConnection() throws Exception{
        WebTarget webTarget = connectionManager.getWebTarget();
        testWebTarget(webTarget);
    }

    private static void testWebTarget(WebTarget webTarget) throws Exception {
        String endpoint = getRecommendationsEndpoint();

        Response response = null;
        try {
            response = webTarget.path(endpoint).
                    request().
                    header("Content-Type", MediaType.APPLICATION_JSON).
                    accept(MediaType.APPLICATION_JSON_TYPE).
                    post(Entity.json(null));

            // drain it, to be safe
            response.readEntity(String.class);

            if (response.getStatus() != 200) {
                Object[] msgArgs = new Object [] {
                        response.getStatusInfo().getStatusCode(),
                        response.getStatusInfo().getReasonPhrase()
                };
                String msg = Message.localize("IAIRecommender_error_result", msgArgs).getLocalizedMessage();
                throw new Exception(msg);
            }

        }
        finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private static String getRecommendationsEndpoint() {
        String endpoint = Configuration.getIdentityAIConfig().getString(Configuration.IAI_CONFIG_RECO_ENDPOINT);
        if (Util.isNullOrEmpty(endpoint)) {
            endpoint = IdentityAIConfigBean.RECO_ENDPOINT_DEFAULT;
        }
        else {
            endpoint = Util.trimWhiteSpaceAndSlash(endpoint);
        }
        return endpoint;
    }

}
