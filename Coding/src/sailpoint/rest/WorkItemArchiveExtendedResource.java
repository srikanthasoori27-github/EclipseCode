/**
 * @author michael.hide
 */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.web.workitem.WorkItemUtil;


/**
 *
 *
 */
@Path("workitemarchiveextended")
public class WorkItemArchiveExtendedResource extends WorkItemArchiveResource {
    private static final Log log = LogFactory.getLog(WorkItemArchiveExtendedResource.class);

    @GET @Path("workitems")
    public ListResult getWorkItems() throws GeneralException {
        ListResult listresult= getListResult(UIConfig.WORKITEMS_ARCHIVE_TABLE_COLUMNS, WorkItemArchive.class, getQueryOptions(UIConfig.WORKITEMS_ARCHIVE_TABLE_COLUMNS));
        log.debug("The attributes are "+listresult.getAttributes());
        log.debug("The map values are "+listresult.toMap());
        
        List objects = listresult.getObjects();
     
        
   
       
        
        return listresult;
    }

    @Override
    public QueryOptions getQueryOptions(String columnsKey) throws GeneralException {
        QueryOptions qo = super.getQueryOptions(columnsKey);

        ArrayList<Filter> ownerFilters = new ArrayList<Filter>();

        /** Admins can see all work items **/
        // IIQETN-14 we were using an API that did not return a boolean value.
        Identity user = getLoggedInUser();
        boolean canManageAllWorkItems = false;

        if (user != null) {
            // IIQETH-14 part deux we need to check for system administrator capability too
            canManageAllWorkItems = user.getCapabilityManager().hasRight(SPRight.FullAccessWorkItems) ||
                    Capability.hasSystemAdministrator(getLoggedInUserCapabilities());
        }

        if(!canManageAllWorkItems) {
            ownerFilters.add(Filter.eq("ownerName", getLoggedInUser().getName()));
            // Loop through all the groups this user is a member of and add those
            // to the filter list.
            List<Identity> workgroupList = getLoggedInUser().getWorkgroups();
            for(Identity group : workgroupList) {
                ownerFilters.add(Filter.eq("ownerName", group.getName()));
            }

            qo.add(Filter.or(ownerFilters));
        }

        // Add all the request params to QueryOptions
        WorkItemUtil.getQueryOptionsFromRequest(qo, WorkItemUtil.convertMultiToSingleMap(uriInfo.getQueryParameters()), true);

        if (log.isDebugEnabled()) {
            log.debug("QueryOptions: " + qo.toString());
        }

        return qo;
    }

    @Override
    protected Object convertColumn(Map.Entry<String,Object> entry, ColumnConfig config, Map<String,Object> rawObject) {
        log.debug("The entry is "+entry);
        String key = entry.getKey();
    	Object value = entry.getValue();
        if (value != null && MessageKeyHolder.class.isAssignableFrom(value.getClass())) {
            String messageKey = ((MessageKeyHolder)value).getMessageKey();
            if (messageKey != null) {
                value = localize(messageKey);
            }
        } else {
            value = super.convertColumn(entry, config, rawObject);
        }
        if(key.equalsIgnoreCase("created"))
        {
        	value=value+"123";
        }
        return value;
    }
}
