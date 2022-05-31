/**
 * 
 */
package sailpoint.rest;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.service.LCMConfigService;
import sailpoint.web.modeler.RoleConfig;

/**
 * @author peter.holcomb
 *
 */
public class RoleSearchUtil {
    // TODO:  Do we really need the LCMConfigService and RoleConfig members here?  It seems like 
    // we're always passing in the context anyway so we can instantiate them as needed.  If we're 
    // going to maintain one we should force it to be set in the constructor.  Arguably all the 
    // methods in this class could be made static and the member variables scrapped.
    RoleConfig rc;
    LCMConfigService lcmConfig;
    
    public RoleSearchUtil() {
        rc = new RoleConfig(); 
    }
    
    public RoleSearchUtil(LCMConfigService lcmConfig) {
        this();
        this.lcmConfig = lcmConfig;
    }

    /**
     * Utility for determining whether or not the current request is allowed to retrieve permitted roles for someone
     * @param isSelfService true for self-service requests; false for other requests or searches
     * @return true if the current request is allowed to include permitted roles; false otherwise
     */
    public boolean isAllowPermittedRoles(SailPointContext context, boolean isSelfService) {
        if (lcmConfig == null) {
            lcmConfig = new LCMConfigService(context);
        }
        return lcmConfig.isRequestPermittedRolesAllowed(isSelfService);
    }

    /**
     * @param isSelfService true for self-service requests; false for other requests or searches
     * @return true if the specified request allows manually assignable roles to be requested; false otherwise
     */
    public boolean isAllowManuallyAssignableRoles(SailPointContext context, boolean isSelfService) {
        if (lcmConfig == null) {
            lcmConfig = new LCMConfigService(context);
        }
        return lcmConfig.isRequestAssignableRolesAllowed(isSelfService);
    }

    
    /** Builds a list of filters for non lcm searches **/
    public List<Filter> getNonLCMFilters(Identity identity, boolean excludeCurrentAccess, SailPointContext context) {
        // It's admittedly strange to rely on a LCM API to get non-LCM results, but because these exclusion
        // filters are usually applied in the context of a LCM request that's where they are
        if (lcmConfig == null) {
            lcmConfig = new LCMConfigService(context);
        }
        List<Filter> filters = lcmConfig.getRoleExclusionFilters(identity, excludeCurrentAccess);
        /** only return assignables **/
        filters.add(Filter.in("type", rc.getAssignableRoleTypes()));
        
        return filters;
    }
}
