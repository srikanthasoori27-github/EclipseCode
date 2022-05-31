/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.fam.FAMService;
import sailpoint.fam.model.FAMConfigDTO;
import sailpoint.fam.service.FAMConfigurationService;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.ConfigService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Configuration endpoint for the Configuration object named {@link sailpoint.object.Configuration.FAM_CONFIG}
 */
public class FAMConfigResource extends BaseResource {

    private static Log _log = LogFactory.getLog(FAMConfigResource.class);

    /**
     * Default constructor.
     */
    public FAMConfigResource() {
        super();
    }

    public FAMConfigResource(BaseResource parent) {
        super(parent);
    }
    
    /**
     * Gets a map of Attributes from the FAMConfiguration object
     * @return Map of FAM configuration values
     * @throws GeneralException when bad things happen
     */
    @GET
    public Map<String, Object> getFAMConfiguration() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessFAMConfiguration));

        FAMConfigDTO dto = new FAMConfigurationService(getContext()).getConfigDTO();
        // Replace encrypted secret fields with dummy values if populated
        if (Util.isNotNullOrEmpty(dto.getClientId())) {
            dto.setClientSecret(FAMConfigurationService.SECRET_DUMMY_VALUE);
        }
        if (Util.isNotNullOrEmpty(dto.getPassword())) {
            dto.setPassword(FAMConfigurationService.SECRET_DUMMY_VALUE);
        }
        return dto.getConfig();
    }

    /**
     * Save a map of Attributes to the FAMConfiguration object
     * @param map of Attributes to save
     * @return Response of 200 if successful
     * @throws GeneralException when bad things happen
     */
    @PUT
    public Response putFAMConfiguration(Map<String, Object> map) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessFAMConfiguration));
        
        new ConfigService(getContext()).saveFAMConfiguration(map);
        return Response.ok().build();
    }

    /**
     * Patch FAMConfiguration from payload.
     * @param map - serialized representation of FAMConfigDTO
     * @return Response of 200 when success
     * @throws GeneralException
     */
    @PATCH
    public Response patchFAMConfig(Map<String, Object> map) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessFAMConfiguration));

        try {
            new FAMConfigurationService(getContext()).patchFAMConfiguration(map);
        } catch (Exception e) {
            _log.warn("Unable to update FAMConfiguration[" + map + "]", e);
            throw new GeneralException(e);
        }
        return Response.ok().build();
    }

    /**
     * Test connection on a provided map of Attributes
     * @param map of Attributes to test
     * @return Response of 200 if successful
     * @throws GeneralException when bad things happen
     */
    @POST
    @Path(Paths.TEST)
    public Response testConnection(Map<String, Object> map) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessFAMConfiguration));
        FAMConfigDTO dto = new FAMConfigDTO(map);
        FAMConfigurationService famConfigurationService = new FAMConfigurationService(getContext());
        // make sure auth params are set
        famConfigurationService.validateFAMAuth(dto);
        // overlay config
        Configuration cfg = famConfigurationService.getConfig(dto);
        FAMService svc = new FAMService(getContext(), cfg);
        svc.testConnection();
        return Response.ok().build();
    }

    @Path("suggest")
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition));

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext
                .add(Rule.class.getSimpleName())
                .add(Application.class.getSimpleName());
        return new SuggestResource(this, authorizerContext);
    }

}
