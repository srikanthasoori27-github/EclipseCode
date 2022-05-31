/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * The purpose of this class is to help the role editor to refresh portions of itself as needed
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
@Path("roleEditor")
public class RoleEditResource extends BaseResource {
    private static final Log log = LogFactory.getLog(RoleEditResource.class);
    

    /**
     * Returns List of extended attribute names that are unavailable for the specified role type
     * @param roleType type of role whose attributes are being queried
     * @param workItemId Optional ID of the work item we were sent to the role page from. Useful for authorization only.
     * @return ListResult containing the names of the attributes that are available for this role
     * @throws GeneralException
     */
    @POST
    @Path("disallowedAttributes")
    public List<String> getDisallowedAttributesForType(@FormParam("roleType") String roleType, @FormParam("workItemId") String workItemId) throws GeneralException {
        boolean authorized = false;
        if (!Util.isNothing(workItemId)) {
            WorkItem workItem = getContext().getObjectById(WorkItem.class, workItemId);
            if (workItem != null) {
                authorize(new WorkItemAuthorizer(workItem));
                authorized = true;
            }
        }
        if (!authorized) {
            authorize(new RightAuthorizer(SPRight.ManageRole));
        }

        List<String> disallowedAttributes = new ArrayList<String>();
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig != null && !Util.isNullOrEmpty(roleType)) {
            RoleTypeDefinition typeDef = roleConfig.getRoleType(roleType);
            if (typeDef != null) {
                List<String> disallowed = typeDef.getDisallowedAttributes();
                if (!Util.isEmpty(disallowed)) {
                    disallowedAttributes.addAll(disallowed);
                }
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Returning the following disallowed extended role attributes for type " + roleType + ":" + Util.listToCsv(disallowedAttributes));
        }
        
        return disallowedAttributes;
    }
}
