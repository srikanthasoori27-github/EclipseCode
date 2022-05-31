/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.oauth;

import java.util.Arrays;
import java.util.List;


import org.glassfish.jersey.message.MessageProperties;
import org.glassfish.jersey.server.ResourceConfig;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;
import sailpoint.rest.oauth.jaxrs.OAuthExceptionMapper;

/**
 * A JAX-RS application that enumerates all of our resources and providers for OAuth (/oauth2/*)  
 * If a new resource is added, it needs to be added to the list in getClasses().
 */
public class SailPointOAuthRestApplication extends ResourceConfig {

    public static final List<Class<?>> _classes = Arrays.asList(OAuthAccessTokenResource.class, OAuthExceptionMapper.class, JsonMessageBodyWriter.class);
    public SailPointOAuthRestApplication() {

        for (Class<?> c : _classes) {
            register(c);
        }
        register(OAuthAccessTokenResource.class);

        // Providers
        register(OAuthExceptionMapper.class);

        register(JsonMessageBodyWriter.class);

        property(MessageProperties.LEGACY_WORKERS_ORDERING, true);
    }

}
