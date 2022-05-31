/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.object.RemediationItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

import javax.faces.el.ValueBinding;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BusinessRoleRemediationItemListBean extends BaseListBean<RemediationItem>{

     private String workItemId;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public BusinessRoleRemediationItemListBean() {
        super();
        super.setScope(RemediationItem.class);

        // This will come in on the request for the Live Grid AJAX request.
        this.workItemId = super.getRequestParameter("workItemId");

        // If we couldn't find the ID from the AJAX request, we're being
        // referenced from within the work item page to calculate the
        // total.  Get the work item bean from JSF to figure out the ID.
        if (null == this.workItemId) {
            ValueBinding vb =
                getFacesContext().getApplication().createValueBinding("#{workItem}");
            WorkItemBean wiBean = (WorkItemBean) vb.getValue(getFacesContext());
            if (null != wiBean) {
                this.workItemId = wiBean.getObjectId();
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDDEN METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {

        QueryOptions ops = super.getQueryOptions();

        WorkItem workItem = getContext().getObjectById(WorkItem.class, this.workItemId);
        ops.add(Filter.and(Filter.eq("workItem", workItem),
                           Filter.join("remediationIdentity", "Identity.name")));

        return ops;
    }

    @Override
    public List<String> getProjectionColumns() {

        List<String> cols = new ArrayList<String>();
        cols.add("id");
        cols.add("Identity.firstname");
        cols.add("Identity.lastname");
        cols.add("completionDate");
        cols.add("remediationDetails");
        return cols;
    }

    @Override
    public Map<String, String> getSortColumnMap() {

        Map<String,String> sortMap = new HashMap<String,String>();
        sortMap.put("s2", "Identity.firstname");
        sortMap.put("s3", "Identity.lastname");
        sortMap.put("s4", "completionDate");
        return sortMap;
    }

    @Override
    public String getDefaultSortColumn() {
        return "Identity.firstname";
    }

}
