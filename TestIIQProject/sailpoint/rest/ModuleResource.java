/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.Module;
import sailpoint.object.SPRight;
import sailpoint.service.StatisticsService;
import sailpoint.service.module.ModuleStatusDTO;
import sailpoint.service.module.ModuleStatusService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import java.util.Map;


/**
 * A sub-resource for a module.
 *
 */
public class ModuleResource extends BaseResource {

    private static final Log log = LogFactory.getLog(ModuleResource.class);

    // The name or ID of the application we're dealing with.
    private String module;


    /**
     * Create an module resource for the given module.
     *
     * @param  module  The name or ID of the module.
     * @param  parent       The parent of this subresource.
     */
    public ModuleResource(String module, BaseResource parent) {
        super(parent);
        this.module = decodeRestUriComponent(module, false);
    }

    /**
     * Return the Module this resource is servicing.
     */
    private Module getModule() throws GeneralException {
        Module module = getContext().getObjectById(Module.class, this.module);
        if (module == null) {
            throw new ObjectNotFoundException(Application.class, this.module);
        } else {
            return module;
        }
    }

    
    @GET
    @Path("status")
    public ModuleStatusDTO getModuleStatus(@QueryParam("includeRequests") Boolean includeRequests) throws Exception {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

        ModuleStatusService svc = new ModuleStatusService(getContext());
        //Have to load the object because it could be ID -rap
        return svc.getModuleStatus(getModule().getName(), includeRequests);
    }

    public static final String ATTR_HOST_NAME = "hostName";
    @POST
    @Path("status")
    public void requestModuleStatus(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));
        String hostName = Util.otos(data.get(ATTR_HOST_NAME));
        if (Util.isNullOrEmpty(hostName)) {
            throw new InvalidParameterException("Host name required");
        }
        StatisticsService svc = new StatisticsService(getContext());
        svc.requestModuleStatus(getModule().getName(), hostName);
    }
    
    
}
