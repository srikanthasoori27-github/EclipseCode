/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.el.ValueBinding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.PolicyTreeNodeSummarizer;
import sailpoint.api.ProvisioningPlanSummarizer;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.Sorter;


/**
 * JSF UI bean that can list remediation items for a given work item.
 *
 * The workItemId parameter is expected on the request.
 *
 * @author Kelly Grizzle
 */
public class RemediationItemListBean extends BaseListBean<RemediationItem> {

    private static Log log = LogFactory.getLog(RemediationItemListBean.class);

    private static final String ENTITY_TYPE_IDENTITY = "Identity";
    private static final String ENTITY_TYPE_ROLE = "BusinessRole";

    private String workItemId;
    private String entityType;

    private List<ColumnConfig> roleColumns;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor. 
     */
    public RemediationItemListBean() throws GeneralException {
        super();
        super.setScope(RemediationItem.class);
        super.setDisableOwnerScoping(true);

        // This will come in on the request for the Live Grid AJAX request.
        this.workItemId = super.getRequestParameter("workItemId");


        // the calling page should pass us an entity type so we can adjust
        // the columns we return.  
        this.entityType = super.getRequestParameter("entityType");

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

        authorize();
    }
    
    private void authorize() throws GeneralException {
        
        WorkItem workItem = getContext().getObjectById(WorkItem.class, this.workItemId);

        Certification cert = workItem.getCertification(getContext());

        authorize(new CertificationAuthorizer(cert, workItem));
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

        Certification cert = workItem.getCertification(getContext());
        
        boolean isAuthorized = CertificationAuthorizer.isAuthorized(cert, workItemId, this);
        
        // todo refactor - this same logic is in WorkItemBean.isObjectInUserScope()
        if (!Capability.hasSystemAdministrator(getLoggedInUserCapabilities()) &&
              !getLoggedInUser().equals(workItem.getOwner()) && !getLoggedInUser().equals(workItem.getRequester()) && !isAuthorized &&
              !getLoggedInUser().isInWorkGroup(workItem.getOwner())) {
            log.error("User '"+getLoggedInUserName()+"' attempted to access remediation items for a " +
                    "work item which they do not have access to. Work item id="+ this.workItemId + ".");
            throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
        }

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("workItem", workItem));

        // If the entity type is null we assume it's an identity
        if (ENTITY_TYPE_IDENTITY.equals(entityType)){
            filters.add( Filter.join("remediationIdentity", "Identity.name"));
        }

        ops.add(Filter.and(filters));

        return ops;
    }

    @Override
    public List<ColumnConfig> getColumns() throws GeneralException {
        if (ENTITY_TYPE_ROLE.equals(this.entityType)) {
            if (this.roleColumns == null) {
                this.roleColumns = getUIConfig().getBusinessRoleRemediationItemTableColumns();
            }
            return this.roleColumns;
        } else {
            return super.getColumns();
        }
    }
    
    public String getBusinesRoleColumnJSON() throws GeneralException {
        String oldEntityType = this.entityType;
        try {
            this.entityType = ENTITY_TYPE_ROLE;
            return getColumnJSON();
        } finally {
            this.entityType = oldEntityType;
        }
    }
    
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        
        if (ENTITY_TYPE_ROLE.equals(this.entityType)) {
            return super.getProjectionColumns();
        }  else {
            List<String> cols = new ArrayList<String>();
            cols.add("id");
            cols.add("remediationIdentity");

            if (ENTITY_TYPE_IDENTITY.equals(entityType)){
                cols.add("Identity.name");
                cols.add("Identity.firstname");
                cols.add("Identity.lastname");
            }

            cols.add("assignee.name");

            cols.add("remediationDetails");
            cols.add("attributes");
            cols.add("completionDate");
            return cols;
        }
    }

    @Override
    public Map<String,ColumnConfig> getSortColumnConfigMap() throws GeneralException {
        Map<String,ColumnConfig> sortMap = null;

        // bug 22698 - There is no entry in UIConfig for this bean to use to determine 
        // additional search parameters like secondary sort. This causes an ordering 
        // problem in the UI with Oracle when there are several pages of remediation
        // items since we could be searching on non-unique properties, like Identity.name.
        // (The remediation items may randomly repeat across pages.)
        // Manually adding a ColumnConfig for Identity.name, Identity.firstname,
        // Identity.lastname and completionDate with a secondary sort of id to fix
        // this issue. When we add the appropriate ColumnConfigs to UIConfig for this
        // bean we won't need these changes.
        sortMap = super.getSortColumnConfigMap();
        
        if (sortMap == null || sortMap.isEmpty() && ENTITY_TYPE_IDENTITY.equals(entityType)) {
            sortMap = new HashMap<String,ColumnConfig>();
            ColumnConfig identityName = new ColumnConfig("Identity.name", "Identity.name");
            identityName.setSortable(true);
            identityName.setSecondarySort("id");
            sortMap.put("name", identityName);

            ColumnConfig completionDate = new ColumnConfig("completionDate", "completionDate");
            completionDate.setSortable(true);
            completionDate.setSecondarySort("id");
            sortMap.put("completionDate", completionDate);

            ColumnConfig firstName = new ColumnConfig("Identity.firstname", "Identity.firstname");
            firstName.setSortable(true);
            firstName.setSecondarySort("id");
            sortMap.put("firstname", firstName);

            ColumnConfig lastName = new ColumnConfig("Identity.lastname", "Identity.lastname");
            lastName.setSortable(true);
            lastName.setSecondarySort("id");
            sortMap.put("lastname", lastName);
        }
        
        return sortMap;
    }

    @Override
    public Map<String, String> getSortColumnMap() {

        Map<String,String> sortMap = new HashMap<String,String>();
        
        if (ENTITY_TYPE_IDENTITY.equals(entityType)){
            sortMap.put("s2", "Identity.name");
            sortMap.put("s3", "Identity.firstname");
            sortMap.put("s4", "Identity.lastname");
            
            sortMap.put("name", "Identity.name");
            sortMap.put("firstname", "Identity.firstname");
            sortMap.put("lastname", "Identity.lastname");

        } else {
            sortMap.put("name", "remediationIdentity");
            sortMap.put("s3", "completionDate");
        }
        
        //cannot sort on entitlements because it is ProvisioningPlan 
        sortMap.put("entitlements", null);

        return sortMap;
    }

    /**
     *
     * @return
     */
    @Override
    public String getSort() throws GeneralException {
        String s = super.getSort();

        Map<String, String> sortMap = getSortColumnMap();

        // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
        if(s.startsWith("[")) {
            @SuppressWarnings("unchecked")
            List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, s);
            String col;
            for(Sorter sorter : sorters) {
                col = sorter.getProperty();
                // Just grab the first one?
                if (sortMap.containsKey(col)) {
                    return sortMap.get(col);
                }
            }
        }

        if (sortMap.containsKey(s)){
            return sortMap.get(s);
        }

        return s;
    }

    @Override
    public String getDefaultSortColumn() {
        return "remediationIdentity";
    }

    /**
     * Overloaded BaseListBean method to do seletive localization of
     * the projection query results.
     */
    public Object convertColumn(String name, Object value) {
        if (name.equals("Identity.name") || name.equals("assignee.name")) {
            Identity requestorOrOwner;
            try {
                requestorOrOwner = getContext().getObjectByName(Identity.class, (String)value);
            } catch (GeneralException e) {
                requestorOrOwner = null;
                log.debug("The work item view failed to get a friendly name for requestor with username " + value, e);
            }
            if (requestorOrOwner != null) {
                value = requestorOrOwner.getDisplayableName();
            } else {
                log.debug("The work item view failed to get a friendly name for requestor with username " + value + ".  The raw username will be displayed.");
            }
        } else if ("completionDate".equals(name) && (value == null || value instanceof Date)) {
            value = formatDate((Date)value);
        } 
        
        return value;
    }
    
    @Override
    public Map<String, Object> convertRow(Object[] row, List<String> cols)
        throws GeneralException {

        Map<String, Object> map = super.convertRow(row, cols);

        //Convert provisioning plan to nice summary
        ProvisioningPlan plan = (ProvisioningPlan) map.get("remediationDetails");
        if (plan != null) {
            map.remove("remediationDetails");
            populateMapWithPlan(map, plan);
        }
        else {
            Attributes<String,Object> attrs = (Attributes<String,Object>)map.get("attributes");
            if (attrs != null) {
                map.remove("attributes");
                populateMapWithContributingEntitlements(map, attrs);
            }
        }
        return map;
    }

    private void populateMapWithPlan(Map<String,Object> map, ProvisioningPlan plan) throws GeneralException {
        map.put("entitlements", getProvisioningPlanSummary(plan));
        if (ENTITY_TYPE_IDENTITY.equals(this.entityType)) {
            // Pull out application and account names from the remediation details
            String application = "";
            String account = "";

            if (plan != null && !Util.isEmpty(plan.getAccountRequests())) {
                //should only be one account request per remediation item, but
                //can be more with it roles with more than one app.
                for (AccountRequest req : Util.iterate(plan.getAccountRequests())) {
                    if (!Util.isNullOrEmpty(application)) {
                        application = application + ", ";
                    }
                    application = application + req.getApplication();
                    if (!Util.isNullOrEmpty(req.getInstance())) {
                        application += " : " + req.getInstance();
                    }
                    if (!Util.isNullOrEmpty(account)) {
                        account = account + ", ";
                    }
                    account = account + req.getNativeIdentity();
                }
            }

            map.put("application", application);
            map.put("account", account);
        }
    }

    private void populateMapWithContributingEntitlements(Map<String,Object> map, Attributes<String,Object> attrs) throws GeneralException {
        map.put("entitlements", getContributingEntitlementsSummary(attrs));

        if (ENTITY_TYPE_IDENTITY.equals(this.entityType)) {
            // Pull out application and account names from the remediation details
            String account = "";

            String application = "";
            Set<String> appSet = new HashSet<String>();
            List<PolicyTreeNode> nodes = (List<PolicyTreeNode>)attrs.get(RemediationItem.ARG_CONTRIBUTING_ENTS);
            if (nodes != null && !Util.isEmpty(nodes)) {
                for (PolicyTreeNode node : nodes) {
                    if (node.getApplication() != null) {
                        String currentApp = node.getApplication();
                        if (currentApp != null) {
                            if (!appSet.contains(currentApp)) {
                                if (!Util.isNullOrEmpty(application)) {
                                    application = application + ", ";
                                }
                                application = application + currentApp;
                                appSet.add(currentApp);
                            }
                        }

                    }
                }
            }

            map.put("application", application);
            map.put("account", account==null ? "" : account);
        }
    }

    public String getDataSourceJSON()
        throws GeneralException {

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("totalCount", getCount());
        result.put("workItems", getWorkItems());

        return JsonHelper.toJson(result);
    }

    private List<Map<String, Object>> getWorkItems()
        throws GeneralException {

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : getRows()) {
            result.add(getWorkItem(row));
        }

        return result;
    }

    private Map<String, Object> getWorkItem(Map<String, Object> row) 
        throws GeneralException {

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", row.get("id"));
        result.put("name", row.get("Identity.name"));
        result.put("assignee", row.get("assignee.name"));
        result.put("firstname", row.get("Identity.firstname"));
        result.put("lastname", row.get("Identity.lastname"));
        result.put("application", row.get("application"));
        result.put("account", row.get("account"));
        result.put("completionDate", row.get("completionDate"));
        result.put("entitlements", row.get("entitlements"));

        return result;
    }

    private String formatDate(Date date) {
        if (date == null) {
            return getMessage(MessageKeys.NOT_APPLICABLE);
        } else {
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, getLocale());
            dateFormat.setTimeZone(getUserTimeZone());

            return dateFormat.format(date);
        }
    }

    private String getProvisioningPlanSummary(ProvisioningPlan plan) 
        throws GeneralException {

    	return new ProvisioningPlanSummarizer(plan,  getLocale(), getUserTimeZone()).getSummary();
    }

    private String getContributingEntitlementsSummary(Attributes<String,Object> attrs) throws GeneralException{
        List<PolicyTreeNode> policyTreeNodes =
                (List<PolicyTreeNode> )attrs.get(RemediationItem.ARG_CONTRIBUTING_ENTS);

        // the policyTreeNodes is really just a list (not a tree) which describes everything
        // which needs to be removed

        StringBuilder sb = new StringBuilder();

        if (!Util.isEmpty(policyTreeNodes)) {

            // just print summary for first one, and add an ellipsis if there
            // is more than one
            PolicyTreeNode policyTreeNode = policyTreeNodes.get(0);
            String summary = new PolicyTreeNodeSummarizer(policyTreeNode, getLocale(), getUserTimeZone()).getSummary();
            sb.append(summary);

            if (policyTreeNodes.size() > 1) {
                sb.append("...");
            }
        }
        return sb.toString();
    }
}
