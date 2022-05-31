/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.myfaces.component.html.ext.HtmlDataTable;
import org.apache.myfaces.component.html.ext.HtmlInputHidden;
import org.apache.myfaces.component.html.ext.HtmlOutputText;
import org.apache.myfaces.component.html.ext.HtmlPanelGroup;
import org.apache.myfaces.custom.column.HtmlSimpleColumn;

import sailpoint.object.Filter;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IdentityRequestItem.CompilationStatus;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.Sorter;
import sailpoint.web.workitem.WorkItemNavigationUtil;

/**
 * Used to populate some data on the 'My Access Request' page.
 * The main grid is populated using the IdentityRequestListResource object.
 *
 * There are three tables that need to get populated:
 *
 * 1) Requests grid
 * 2) Approvals grid
 * 3) Changes grid
 *
 * All the data we need should be in the IdentityRequest or IdentityRequestItem objects.
 *
 * @author patrick.jeong
 *
 */
@SuppressWarnings("deprecation")
public class IdentityRequestBean extends BaseObjectBean<IdentityRequest>
        implements NavigationHistory.Page {
    private static Log log = LogFactory.getLog(IdentityRequestBean.class);

    ////////////////////////////////////////////////////////////////////////////
    // FIELDS
    ////////////////////////////////////////////////////////////////////////////
    private HtmlPanelGroup requestsItemsTableGroup;
    private HtmlDataTable requestItemsTable;


    /**
     * djs: IdentityRequest.name now holds the sequence id, description
     * holds what was stored in name which is typically the task result
     * name.
     */

    // now actual hibernate id    
    private String requestId;
    // comes from object name
    private String requestName;
    private List<IdentityRequestItemUI> items;
    private List<ApprovalSummary> pendingApprovals;
    private IdentityRequest request;

    private Integer itemCount;
    private Integer filteredItemCount;

    ////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////////

    public IdentityRequestBean() throws GeneralException {
        super();
        setScope(IdentityRequest.class);
    }

    private ValueBinding createValueBinding(String valueExpression) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        return facesContext.getApplication().createValueBinding(valueExpression);
    }

    /**
     * Get the request items.
     *
     * @return List of all IdentityRequestItemUI
     * @throws GeneralException
     */
    public List<IdentityRequestItemUI> getItems() throws GeneralException {
        if (items != null) {
            return items;
        }

        items = getItemsInternal(false);

        return items;
    }

    private  List<IdentityRequestItemUI> getItemsInternal(boolean filtered) throws GeneralException {

        requestId = getRequestParameter("requestId");

        if (requestId == null) {
            IdentityRequest request = (IdentityRequest)getRequestObject();
            if (request != null) {
                requestId = request.getId();
            }
        }

        int start = 0;
        int limit = 0;

        if (getRequestParameter("start") != null) {
            start = Integer.parseInt(getRequestParameter("start"));
        }

        limit = getResultLimit();

        QueryOptions qo = new QueryOptions();

        if (filtered) {
            qo.add(Filter.and(Filter.eq("identityRequest.id", requestId), Filter.eq("compilationStatus", CompilationStatus.Filtered)));
        }
        else {
            qo.add(Filter.and(Filter.eq("identityRequest.id", requestId), Filter.isnull("compilationStatus")));
        }

        if (start > 0)
            qo.setFirstRow(start);

        if (limit > 0)
            qo.setResultLimit(limit);

        addSortOptions(qo);

        if (filtered) {
            filteredItemCount  = getContext().countObjects(IdentityRequestItem.class, qo);
        }
        else {
            itemCount  = getContext().countObjects(IdentityRequestItem.class, qo);

            // check to see if all items were filtered
            if (itemCount == 0) {
                QueryOptions filteredQuery = new QueryOptions();
                filteredQuery.add(Filter.and(Filter.eq("identityRequest.id", requestId), Filter.eq("compilationStatus", CompilationStatus.Filtered)));

                int filteredCount  = getContext().countObjects(IdentityRequestItem.class,  filteredQuery);

                if (filteredCount > 0) {
                    // show filtered instead
                    qo = filteredQuery;
                }
            }
        }

        List<IdentityRequestItem> queryItems  = getContext().getObjects(IdentityRequestItem.class, qo);

        ArrayList<IdentityRequestItemUI> returnitems = new ArrayList<IdentityRequestItemUI>();

        for (IdentityRequestItem item :  queryItems) {
            returnitems.add(new IdentityRequestItemUI(item, getContext(), getLocale(), getUserTimeZone()));
        }

        return returnitems;
    }

    private void addSortOptions(QueryOptions qo) throws GeneralException {
        String sortBy = this.getRequestParameter("sort");
        String sortDirection = this.getRequestParameter("dir");

        if (sortBy != null) {
            if(sortBy.startsWith("[")) {
                List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, sortBy);
                for (Sorter sorter : sorters) {
                    qo.addOrdering(sorter.getProperty(), sorter.isAscending());
                }
            }
            else {
                qo.addOrdering(sortBy, "ASC".equalsIgnoreCase(sortDirection));
            }
        }
    }

    /**
     * Add a column to a data table with the give columnName header and value binding.
     *
     * @param columnName
     * @param valueBinding
     * @throws IOException
     */
    private HtmlSimpleColumn addColumn(String columnName, String valueBinding) {
        return addColumnInternal(columnName, valueBinding);
    }

    private void addRoleColumn(String columnName, String valueBinding)  {
        HtmlSimpleColumn column = new HtmlSimpleColumn();
        requestItemsTable.getChildren().add(column);

        HtmlOutputText header = new HtmlOutputText();
        header.setValue(columnName);
        column.setHeader(header);
        column.setStyleClass("adornColumn");

        HtmlInputHidden entitlementIconHolder = new HtmlInputHidden();
        entitlementIconHolder.setId("entitlementIcon");
        entitlementIconHolder.setValueBinding("value", createValueBinding("#{requestItem.entitlementIcon}"));
        column.getChildren().add(entitlementIconHolder);

        HtmlInputHidden entitlementInfoHolder = new HtmlInputHidden();
        entitlementInfoHolder.setId("entitlementInfo");
        entitlementInfoHolder.setValueBinding("value", createValueBinding("#{requestItem.entitlementInfo}"));
        column.getChildren().add(entitlementInfoHolder);

        HtmlInputHidden entName = new HtmlInputHidden();
        entName.setId("entitlementName");
        entName.setValueBinding("value", createValueBinding("#{requestItem.name}"));
        column.getChildren().add(entName);

        //adding a value here as the none displayableValue
        HtmlInputHidden roleSearchName = new HtmlInputHidden();
        roleSearchName.setId("roleSearchName");
        roleSearchName.setValueBinding("value", createValueBinding("#{requestItem.value}"));
        column.getChildren().add(roleSearchName);

        HtmlInputHidden isGroupInfo = new HtmlInputHidden();
        isGroupInfo.setId("isGroupAttribute");
        isGroupInfo.setValueBinding("value", createValueBinding("#{requestItem.groupAttribute}"));
        column.getChildren().add(isGroupInfo);

        HtmlInputHidden roleId = new HtmlInputHidden();
        roleId.setId("roleId");
        roleId.setValueBinding("value", createValueBinding("#{requestItem.roleId}"));
        column.getChildren().add(roleId);

        HtmlInputHidden appName = new HtmlInputHidden();
        appName.setId("appName");
        appName.setValueBinding("value", createValueBinding("#{requestItem.applicationName}"));
        column.getChildren().add(appName);

        HtmlInputHidden requestId = new HtmlInputHidden();
        requestId.setId("requestId");
        requestId.setValueBinding("value", createValueBinding("#{requestItem.requestId}"));
        column.getChildren().add(requestId);

        HtmlInputHidden requestItemId = new HtmlInputHidden();
        requestItemId.setId("requestItemId");
        requestItemId.setValueBinding("value", createValueBinding("#{requestItem.requestItemId}"));
        column.getChildren().add(requestItemId);

        HtmlInputHidden assignmentId = new HtmlInputHidden();
        assignmentId.setId("assignmentId");
        assignmentId.setValueBinding("value", createValueBinding("#{requestItem.assignmentId}"));
        column.getChildren().add(assignmentId);

        HtmlInputHidden identityId = new HtmlInputHidden();
        identityId.setId("identityId");
        identityId.setValueBinding("value", createValueBinding("#{requestItem.targetId}"));
        column.getChildren().add(identityId);

        HtmlOutputText output = new HtmlOutputText();
        output.setValueBinding("value", createValueBinding(valueBinding));
        column.getChildren().add(output);
    }

    @SuppressWarnings("unused")
    private void addEntitlementColumn(String columnName, String valueBinding)  {
        HtmlSimpleColumn column = new HtmlSimpleColumn();
        requestItemsTable.getChildren().add(column);

        HtmlOutputText header = new HtmlOutputText();
        header.setValue(columnName);
        column.setHeader(header);
        column.setStyleClass("adornColumn");

        HtmlInputHidden isGroupInfo = new HtmlInputHidden();
        isGroupInfo.setId("isGroupAttribute");
        isGroupInfo.setValueBinding("value", createValueBinding("#{requestItem.groupAttribute}"));
        column.getChildren().add(isGroupInfo);

        HtmlInputHidden appName = new HtmlInputHidden();
        appName.setId("appName");
        appName.setValueBinding("value", createValueBinding("#{requestItem.applicationName}"));
        column.getChildren().add(appName);

        HtmlInputHidden entName = new HtmlInputHidden();
        entName.setId("entitlementName");
        entName.setValueBinding("value", createValueBinding("#{requestItem.name}"));
        column.getChildren().add(entName);

        HtmlOutputText output = new HtmlOutputText();
        output.setValueBinding("value", createValueBinding(valueBinding));
        column.getChildren().add(output);
    }

    private HtmlSimpleColumn addColumnInternal(String columnName, String valueBinding) {
        HtmlSimpleColumn column = new HtmlSimpleColumn();
        requestItemsTable.getChildren().add(column);

        HtmlOutputText header = new HtmlOutputText();
        header.setValue(columnName);
        column.setHeader(header);
        HtmlOutputText output = new HtmlOutputText();
        output.setValueBinding("value", createValueBinding(valueBinding));
        column.getChildren().add(output);
        return column;
    }

    /**
     * Add some common columns
     * @throws IOException
     */
    private void addCommonColumns() {
        // account
        addColumn(getMessage(MessageKeys.REQUEST_BEAN_ACCOUNT), "#{requestItem.nativeIdentity} #{requestItem.linkDisplayName}").setStyleClass("breakword");
        // application
        addColumn( getMessage(MessageKeys.REQUEST_BEAN_APPLICATION), "#{requestItem.applicationName}");

        // instance 
        addColumn(getMessage(MessageKeys.REQUEST_BEAN_INSTANCE), "#{requestItem.instance}");
    }

    /**
     * Load all the columns into the Requests data table.
     * Depending on the type of request the columns will be different.
     *
     * @throws GeneralException
     */
    private void loadRequestItemsTable() throws GeneralException {
        IdentityRequest request = getRequestObject();

        if (request == null) {
            return;
        }

        requestsItemsTableGroup = new HtmlPanelGroup();

        requestItemsTable = new HtmlDataTable();

        requestItemsTable.setValueBinding("value", createValueBinding("#{requestDetails.items}"));
        requestItemsTable.setStyleClass("spBlueTable");
        requestItemsTable.setVar("requestItem");

        addColumn(getMessage(MessageKeys.REQUEST_BEAN_OPERATION), "#{requestItem.operation}");

        // Add columns based on type of request
        String type = request.getType();

        // NOT AN ACCOUNT REQUEST? Add ITEM and VALUE columns
        if (!IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME.equals(type)) {
            addColumn(getMessage(MessageKeys.REQUEST_BEAN_ENTITLEMENT), "#{requestItem.name}");

            if (IdentityRequest.PASSWORD_REQUEST_FLOW.equals(type)
                    || IdentityRequest.FORGOT_PASSWORD_FLOW.equals(type)
                    || IdentityRequest.EXPIRED_PASSWORD_FLOW.equals(type)) {

                // value
                addColumn(getMessage(MessageKeys.REQUEST_BEAN_VALUE), "#{'****'}");
            }
            else if (RequestAccessService.FLOW_CONFIG_NAME.equals(type)
                    || IdentityRequest.ROLES_REQUEST_FLOW_CONFIG_NAME.equals(type)
                    || IdentityRequest.ENTITLEMENTS_REQUEST_FLOW_CONFIG_NAME.equals(type)) {
                addRoleColumn(getMessage(MessageKeys.REQUEST_BEAN_VALUE), "#{requestItem.displayableValue}");
            }
            else {
                addColumn(getMessage(MessageKeys.REQUEST_BEAN_VALUE), "#{requestItem.value}");
            }
        }

        if (!IdentityRequest.IDENTITY_CREATE_FLOW_CONFIG_NAME.equals(type)
                && !IdentityRequest.IDENTITY_UPDATE_FLOW_CONFIG_NAME.equals(type)) {
            addCommonColumns();
        }

        addColumn(getMessage(MessageKeys.REQUEST_BEAN_COMMENTS), "#{requestItem.requesterComments}");
        addColumn(getMessage(MessageKeys.REQUEST_BEAN_APPROVAL_STATUS), "#{requestItem.approvalState}");
        addColumn(getMessage(MessageKeys.REQUEST_BEAN_PROVISIONING_STATUS), "#{requestItem.provisioningState}");

        requestsItemsTableGroup.getChildren().add(requestItemsTable);
    }

    /**
     * Get the request items table
     *
     * @return
     * @throws GeneralException
     */
    public HtmlPanelGroup getRequestItemsTableGroup() throws GeneralException {
        if (requestsItemsTableGroup == null) {
            loadRequestItemsTable();
        }
        return requestsItemsTableGroup;
    }

    public void setRequestItemsTableGroup(HtmlPanelGroup panelGroup) {
        requestsItemsTableGroup = panelGroup;
    }

    /**
     * Get the pending approval work items from the work item table.
     *
     * @return
     * @throws GeneralException
     */
    public List<ApprovalSummary> getPendingApprovals() throws GeneralException {
        if (pendingApprovals != null) {
            return pendingApprovals;
        }
        pendingApprovals = new ArrayList<ApprovalSummary>();

        String props = "id, owner.displayName, description, created,  owner.id";

        // left pad with zeroes
        if ( this.requestName == null ) {
            IdentityRequest req = getRequestObject();
            if (req != null)
                requestName = req.getName();
        }

        this.requestName = String.format("%010d", Integer.parseInt(requestName));

        // Get the pending approvals from the work_item table
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identityRequestId", requestName));

        // set the value to the role type
        Iterator<Object[]> it = getContext().search(WorkItem.class, qo, props);
        while(it.hasNext()) {
            Object[] record = it.next();

            ApprovalSummary as = new ApprovalSummary();
            as.setWorkItemId((String)record[0]);
            as.setOwner((String)record[1]);
            as.setRequest((String)record[2]);
            as.setStartDate((Date)record[3]);
            as.setOwnerId((String)record[4]);
            as.setState(null); // null is for pending?

            pendingApprovals.add(as);
        }

        return pendingApprovals;
    }

    public String getRequestId() throws GeneralException {
        if (requestId != null)
            return requestId;

        if ( _objectId != null ) requestId = _objectId;

        if ( requestId == null ) {
            IdentityRequest req = getRequestObject();
            if ( req != null )
                requestId = req.getId();

        }
        return requestId;
    }

    public String getRequestName() throws GeneralException {
        if (requestName != null) {
            return requestName;
        } else {
            IdentityRequest req = getRequestObject();
            if (req != null) {
                requestName = req.getName();
            }
            this.requestName = String.format("%010d", Integer.parseInt(requestName));
        }
        return requestName;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @SuppressWarnings("unchecked")
    private IdentityRequest getRequestObject() throws GeneralException {
        if(request==null) {
            if (_objectId == null) {
                _objectId = requestId;
            }
            // if its still null try to get it from the request parms
            if (_objectId == null) {
                _objectId = requestId = getRequestOrSessionParameter("requestId");
            }

            if (_objectId == null) {
                _objectId = requestId = getRequestOrSessionParameter("editForm:requestId");
            }

            if (_objectId == null) {
                return null;
            }

            // left pad with zeroes

            if ( requestName != null )
                requestName = String.format("%010d", Integer.parseInt(requestName));

            getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");

            request = (IdentityRequest)getObject();

            if (request == null) {
                throw new GeneralException("Unable to access IdentityRequest object.");
            }
        }
        return request;
    }

    /**
     * Action for work item details link.
     *
     * @return
     * @throws GeneralException
     */
    public String viewWorkItem() throws GeneralException
    {
        String workItemId  =  getRequestOrSessionParameter("mainForm:workItemId");

        if (null == workItemId) {
            throw new GeneralException("No work item was selected.");
        }

        NavigationHistory.getInstance().saveHistory(this);

        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
        return navigationUtil.navigate(workItemId, true /* check archive */, super.getSessionScope());
    }

    protected boolean isAuthorized(SailPointObject object) throws GeneralException {

        return IdentityRequestAuthorizer.isAuthorized((IdentityRequest) object,  this);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods 
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Identity Request";
    }

    public String getNavigationString() {

        /** If the request id isn't null, we were looking at a specific request so we want to 
         * come back to the detail view
         */
        if (requestId!=null) {
            return "viewAccessRequestDetail#/request/" + requestId;
        }
        return "viewAccessRequests";
    }

    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = requestId;
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setObjectId((String)myState[0]);
    }
}
