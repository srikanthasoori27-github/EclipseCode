/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.external;

import org.glassfish.jersey.message.MessageProperties;
import org.glassfish.jersey.server.ResourceConfig;
import sailpoint.rest.ProductionBinder;
import sailpoint.rest.jaxrs.JsonMessageBodyReader;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;
import sailpoint.rest.ui.jaxrs.AllExceptionMapper;
import sailpoint.rest.ui.jaxrs.GeneralExceptionMapper;
import sailpoint.rest.ui.jaxrs.InvalidParameterExceptionMapper;
import sailpoint.rest.ui.jaxrs.ObjectNotFoundExceptionMapper;
import sailpoint.rest.ui.jaxrs.ValidationExceptionMapper;


/**
 * The external rest application which has no authentication.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class SailPointExternalRestApplication extends ResourceConfig {
    
    public SailPointExternalRestApplication() {
        register(FormResource.class);
        register(MFAResource.class);

        register(GeneralExceptionMapper.class);
        register(AllExceptionMapper.class);
        register(InvalidParameterExceptionMapper.class);
        register(ValidationExceptionMapper.class);
        register(ObjectNotFoundExceptionMapper.class);
        register(JsonMessageBodyReader.class);
        register(JsonMessageBodyWriter.class);

        property(MessageProperties.LEGACY_WORKERS_ORDERING, true);

        register(new ProductionBinder());
    }

}
