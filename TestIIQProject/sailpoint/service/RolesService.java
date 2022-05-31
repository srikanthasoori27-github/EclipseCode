package sailpoint.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Bundle;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.modeler.RoleUtil;


/**
 * Service for working with Roles
 */
public class RolesService {
    /**
     * Key for metadata value indicating the total number of entitlements on a Role
     */
    public static String TOTAL_ENTITLEMENT_COUNT = "totalEntitlementCount";

    private static Log log = LogFactory.getLog(RolesService.class);

    private SailPointContext context;
    private UserContext userContext;

    public RolesService(UserContext userContext) {
        this.userContext = userContext;
        this.context = this.userContext.getContext();
    }

    /**
     * Merges together the list of direct simple entitlements and inherited simple entitlements and returns a ListResult
     * containing the data.
     *
     * @param roleId ID of the role to get entitlements for
     * @return ListResult of data
     * @throws GeneralException
     */
    public ListResult getAllSimpleEntitlements(String roleId) throws GeneralException {
        Bundle role = this.context.getObjectById(Bundle.class, roleId);

        List<Map<String, Object>> result = Collections.emptyList();
        int totalEntitlementCount = 0;

        RoleUtil.SimpleEntitlementCriteria criteria = new RoleUtil.SimpleEntitlementCriteria();

        // If this isn't simple, set total entitlement count to -1. The UI will look at this to figure out what kind
        // of message to display. Ideally we would have a boolean flag here and not a magic number but it's late.
        if (!RoleUtil.hasAllSimpleEntitlements(role, criteria)) {
            totalEntitlementCount = -1;
        }
        
        // If it is simple, collect all the simple entitlements. (There might be no entitlements at all though.)
        if (totalEntitlementCount != -1) {
            try {
                result = RoleUtil.getAllSimpleEntitlements(role, context, userContext.getLocale(), userContext.getUserTimeZone(), userContext.getLoggedInUser(), criteria);
                totalEntitlementCount = result.size();
            } catch (JSONException jsoe) {
                log.error("Exception while fetching read only entitlement data for role: " + roleId + ". Exception: " + jsoe.getMessage(), jsoe);
                throw new GeneralException(jsoe);
            }
        }

        ListResult listResult = new ListResult(result, result.size());

        // Add the total entitlement count to the metadata
        HashMap<String, Object> meta = new HashMap<String, Object>();
        meta.put(RolesService.TOTAL_ENTITLEMENT_COUNT, totalEntitlementCount);
        listResult.setMetaData(meta);

        return listResult;
    }
}
