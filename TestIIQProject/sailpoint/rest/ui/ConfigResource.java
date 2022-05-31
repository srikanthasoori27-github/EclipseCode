/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.ConfigService;
import sailpoint.tools.GeneralException;

/**
 * Rest resource to get config entries.
 *
 * @author: michael.hide
 */
@Path(Paths.CONFIGURATION)
public class ConfigResource extends BaseResource {

    /**
     * @param keys List of keys of UIConfig entry objects.  If empty, will return ALL entries.
     * @return Map of entry objects with translated headerKeys (if available)
     * @throws GeneralException
     */
    @GET
    @Path(Paths.UICONFIG + Paths.ENTRIES)
    public Map<String, List<Map<String, Object>>> getUIConfigEntries(@QueryParam("key") List<String> keys) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return ConfigService.getUIConfigEntries(keys, getLocale());
    }

    /**
     * @return List of displayable identity attributes with translated labels
     * @throws GeneralException
     */
    @GET
    @Path(Paths.UICONFIG + Paths.IDENTITY)
    public List<Map<String, Object>> getIdentityDetailsConfig() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return ConfigService.getIdentityDetailsMetadata(getLocale());
    }

    @Path(Paths.UICONFIG + Paths.ACCESS_REQUEST)
    public AccessRequestConfigResource getAccessRequestConfigResource() {
       return new AccessRequestConfigResource(this);
    }

    @Path(Paths.FAMCONFIG)
    public FAMConfigResource getFAMConfigResource() {
        return new FAMConfigResource(this);
    }
}
