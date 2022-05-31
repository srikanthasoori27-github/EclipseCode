/**
 * 
 */
package sailpoint.web.lcm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.Source;
import sailpoint.service.LCMConfigService;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.UserAccessEntitlementFilterContext;
import sailpoint.service.listfilter.UserAccessPopulationFilterContext;
import sailpoint.service.listfilter.UserAccessRoleFilterContext;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseListBean;
import sailpoint.web.extjs.GridResponse;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.SelectItemByLabelComparator;

/**
 * @author Peter Holcomb
 *
 */
public abstract class BaseRequestBean extends BaseBean {
    
    private static final Log log = LogFactory.getLog(BaseRequestBean.class);
    
    public static final String ATT_REQUEST_REQUESTS = "baseRequestRequests";
    public static final String ATT_REQUEST_CLASS = "baseRequestClass";
    public static final String ATT_IDENTITY_IDS = "lcmRequestIdentityIds"; 
    
    public static final String ATT_SUMMARY_REQUESTS = "summaryOfRequests";
    public static final String ATT_SUNRISE_DATE = "lcmSunriseDate";
    public static final String ATT_SUNSET_DATE = "lcmSunsetDate";
    
    public static final String CHANGE_PASSWORD_REQ = "PasswordsRequest";
    public static final String CHANGE_PASSWORD_POLICY = "ChangePassword";
    public static final String PROVISIONING_POLICIES = "provisioningPolicies";

    public static final String ATT_TYPE_ROLE = "role";
    public static final String ATT_TYPE_ENTITLEMENT = "entitlement";

    // This is a flag that will prevent loading an identity that is
    // making an unauthorized request for another identity
    // by specifying the requested identity in the query string.
    public static final String ATT_UNAUTHORIZED_PARAM_REQUEST = "unauthorizedParamRequest";

    Identity identity;
    /** The current identity that this request is being created for **/
    String identityId;
    
    /** The list of identities that this request is being created for - only applies to bulk requests **/
    Set<String> identityIds;
    
    /** Flag for determining whether this is a bulk request or not **/
    boolean bulk;
    
    /** The json version of the requests that have been created.  This should
     * be converted by the overriding class into AccountRequests.
     */
    String requestsJSON;
    
    /** The list of account requests just created by the current submitRequests() **/
    List<AccountRequest> createdRequests;
    
    /** The current list of requests created by the logged in user **/
    List<AccountRequest> currentRequests;

    /** A requestID - the id of the request to remove when removeRequest() is called **/
    String requestId;
    
    List<ColumnConfig> columns;
    List<ColumnConfig> searchColumns;
    List<ColumnConfig> cartGridColumns;

    /**
     * A transient boolean that can be set by sub-classes in createRequests()
     * that will prevent submission from going on to the summary page.
     */
    boolean returnToRequestsPage;
    
    boolean _hasError;

    // an identity specified in the URl may not be accessible by the logged in identity
    // use this to call that out.
    protected boolean unauthorized = false;
    
    List<String> errors;


    //////////////////////////////////////////////////////////////////////
    //
    // Abstract methods
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Return a list of data to display in the summary page grid for requests
     * created from this bean.
     */
    abstract List<Map<String,Object>> getRequestSummary(List<AccountRequest> requests)
        throws GeneralException;
    
    abstract String getFlowName()
        throws GeneralException;

    /**
     * This is a non-translated string that gives us a key that can be used :
     *  o Map the 'flow' to a workflow .
     *
     *  :. When submitting the flow we lookup the workflow using a standard convention.
     * 
     * i.e. format = "workflowLCM" + FlowConfigurationName
     *
     *    NOTE :Can't use getFlowName() on the beans because that is internationalized and
     *    could be different depending on the locale.
     *
     *  o As an argument to the flow so the workflow can add logic for specific flow logic
     *  :. For instance in the update identity case we check the flow name to figure
     *     out if we should look up previous values. 
     */
    abstract String getFlowConfigName()
       throws GeneralException;
    
    

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////
    
    public String cancel() {
        clearSession();
        NavigationHistory.getInstance().clearHistory();
        return NAV_OUTCOME_HOME;
    }
    
    public String viewSummary() {
        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        }
        return ATT_SUMMARY_REQUESTS;
    }
    
    /** Clears the session of all lcm related entities **/
    public static void clearSession() {
        Map sessionScope = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
        sessionScope.remove(ATT_IDENTITY_IDS);
        sessionScope.remove(ATT_REQUEST_REQUESTS);
        sessionScope.remove(ATT_REQUEST_CLASS);
        sessionScope.remove(ATT_SUNRISE_DATE);
        sessionScope.remove(ATT_SUNSET_DATE);
        sessionScope.remove(RequestPopulationBean.ATT_SEARCH_INPUTS);
        sessionScope.remove(RequestPopulationBean.ATT_SEARCH_FILTER);
        sessionScope.remove(RequestPopulationBean.ATT_ALLOW_BULK);
//        sessionScope.remove(EntitlementsRequestBean.ATT_ENTITLEMENT_IDENTITIES);
//        sessionScope.remove(EntitlementsRequestBean.ATT_INCOMPLETE_ENTITLEMENT_IDENTITIES);
//        sessionScope.remove(PasswordsRequestBean.ATT_PASSWORD_SYNCHRONIZED);
//        sessionScope.remove(RolesRequestBean.ATT_REQUEST_ROLE_ID_LIST);
//        sessionScope.remove(PasswordsRequestBean.ATT_PASSWORD_SYNCPASS);
//        sessionScope.remove(PasswordsRequestBean.ATT_PASSWORD_GENERATED);
//        sessionScope.remove(AttributesRequestBean.ATT_EXPANDED_FORM);
//        sessionScope.remove(AttributesRequestBean.ATT_MASTER_FORM);
        sessionScope.remove(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
    }
    
    @SuppressWarnings("unchecked")
    public String submitRequest() {
        
        /** We want to prevent requesting different types of object in the same request
         * so we check to see if the current flow being entered into is not the same as the
         * previous one.  If it isn't, we clear the session.
         */
        try {
            if(getRequestClass()!=null) {
                if(!this.getClass().getName().equals(getRequestClass())){
                    getSessionScope().remove(ATT_REQUEST_REQUESTS);
                    setCurrentRequests(null);
                }
            }
        } catch(GeneralException ge) {
            log.debug("Unable to fetch requesting bean: " + ge.getMessage());
        }
        
        /** Unserialize the json data into request objects **/
        try {
            createdRequests = createRequests();

            // The call to createRequests() may choose to have the caller stay
            // on the current page (eg - when refreshing a form).  In this case,
            // don't add anything to the cart.  Just return to the same page.
            if (this.returnToRequestsPage) {
                return null;
            }
            else if (!Util.isEmpty(createdRequests)) {
                log.debug("Requests: " + createdRequests);
                try {
                    addRequestsToCart(createdRequests, true);
                }
                catch (DuplicateRequestException e) {
                    Message msg = e.getDuplicateMessage();
                    if (null == msg) {
                        msg = new Message(Message.Type.Error, getMessage(MessageKeys.LCM_ERR_DUPLICATE_REQUEST));
                    }
                    addMessage(msg);
                    return null;
                }
                getSessionScope().put(ATT_REQUEST_REQUESTS, currentRequests);
            } 
        } catch(GeneralException ge) {
            Message msg = new Message(Message.Type.Error, ge.getMessage());
            addMessage(msg);
            _hasError = true;
        }
        
        // Put this class name into the session so the SubmitRequestBean will be
        // able to call back to us for more information.
        saveRequestClass();
        
        if(_hasError) 
            return null;
        
        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        }
        
        return ATT_SUMMARY_REQUESTS;
    }
    
    /**
     * Add the given new requests to the cart.  If any of the requests are
     * already in the cart, this throws a DuplicateRequestException.
     * 
     * @param  requests  The new requests to add to the cart.
     * @param  throwDuplicate  Whether to ignore the duplicate request or to throw an exception
     * when it is discovered
     */
    void addRequestsToCart(List<AccountRequest> requests, boolean throwDuplicate)
        throws GeneralException, DuplicateRequestException {
        
        if (null != requests) {

            List<AccountRequest> requestsToAdd = new ArrayList<AccountRequest>();
            // First check for duplicates.
            for (AccountRequest request : requests) {
                boolean isDuplicate = checkDuplicate(request, throwDuplicate);
                if(!isDuplicate) {
                    try{
                        String reqFlow = this.getFlowConfigName();                        
                        if(reqFlow!=null && reqFlow.toString().equalsIgnoreCase(CHANGE_PASSWORD_REQ)){
                            List<String> policies = new ArrayList<String>();
                            policies.add(CHANGE_PASSWORD_POLICY);
                            request.addArgument(PROVISIONING_POLICIES,policies);
                        }
                    }
                    catch(Exception e)
                    {
                        if(log.isDebugEnabled())
                            log.debug("There is no flow for account request");
                    }
                    requestsToAdd.add(request);
                }
            }
            getCurrentRequests().addAll(requestsToAdd);
        }
    }

    /**
     * Inspect the current cart to determine if the given request is already in
     * the cart.  If so, this throws a DuplicateRequestException.
     */
    boolean checkDuplicate(AccountRequest newRequest, boolean throwDuplicate)
        throws GeneralException, DuplicateRequestException {

        boolean isDuplicate = false;
        
        if(getCurrentRequests()!=null && !getCurrentRequests().isEmpty()) {
            Iterator<AccountRequest> iter = getCurrentRequests().iterator();
            while(iter.hasNext()) {
                AccountRequest request = iter.next();
                
                /** First check to see if they both have ids in their args...if they do, do the equals on that  **/
                String newRequestId = (String)newRequest.getArgument("id");
                String existingRequestId =(String)request.getArgument("id");
                log.info("new id: " + newRequestId + " existing id:" + existingRequestId);
                
                if(newRequestId!=null && existingRequestId!=null && newRequestId.equals(existingRequestId)) {
                    isDuplicate = true;
                    break;
                } else if (newRequest.equals(request)) {
                    isDuplicate = true;
                    break;
                }
            }
        }
        
        /** If we want to throw an exception, do so -- otherwise return the false/true result **/
        if(isDuplicate && throwDuplicate) {
            throw new DuplicateRequestException(newRequest);
        } 
        
        String name = (String)newRequest.getArgument("name");        
        if(name!=null) {
            String msg = getMessage(MessageKeys.LCM_ERR_DUPLICATE_REQUEST_PARAMED, newRequest.getArgument("name"));
            addError(msg);
            _hasError = true;
        }
        return isDuplicate;
    }
    
    /**
     * Exception that is thrown when a duplicate request is added to the cart.
     */
    @SuppressWarnings("serial")
    static class DuplicateRequestException extends Exception {
        private AccountRequest duplicate;
        private Message duplicateMessage;
        
        public DuplicateRequestException(AccountRequest duplicate) {
            this(duplicate, null);
        }

        public DuplicateRequestException(AccountRequest duplicate, Message msg) {
            this.duplicate = duplicate;
            this.duplicateMessage = msg;
        }

        public AccountRequest getDuplicate() {
            return this.duplicate;
        }

        public Message getDuplicateMessage() {
            return this.duplicateMessage;
        }
    }
    
    public String back() throws GeneralException {
        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null) {
            outcome = NAV_OUTCOME_HOME;
        }
        
        /** If we are moving back to the request population page through the cancel button,
         * we need to clear the cart of requested items - Bug 7512: PH
         */
        if(outcome=="requestPopulation") {
            Map sessionScope = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
            sessionScope.remove(ATT_REQUEST_REQUESTS);
        }
        return outcome;
    }
    
    /** Go back twice -- for breadcrumbs **/
    public String back2() throws GeneralException {
        this.back();
        return this.back();
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Utility Methods
    //
    //////////////////////////////////////////////////////////////////////
    
    public List<SelectItem> getFlowNames() {
        Configuration systemConfig = Configuration.getSystemConfig();
        LCMConfigService lcmConfigSvc = new LCMConfigService(getContext());
        List<SelectItem> requestTypesAsSelectItems = new ArrayList<SelectItem>();
        List<String> requestTypes = (List<String>) systemConfig.get(Configuration.ACCESS_REQUEST_TYPES);
        if (!Util.isEmpty(requestTypes)) {
            for (String requestType : requestTypes) {
                String displayName = lcmConfigSvc.getRequestTypeMessage(requestType, getLocale());
                SelectItem requestTypeAsSelectItem = new SelectItem(requestType, displayName);
                requestTypesAsSelectItems.add(requestTypeAsSelectItem);
            }
        }

        Collections.sort(requestTypesAsSelectItems, new SelectItemByLabelComparator(getLocale(), true));
        return requestTypesAsSelectItems;
    }
    
    /** On the UI, we've set various properties on the AccountRequest with values that
     * we utilize to look other things up (such as identity name, application id, etc...
     * Need to clean these up before submitting them to the workflows.
     */
    public List<AccountRequest> prepareRequests() {
        if(getCurrentRequests()!=null) {
            for(AccountRequest request : currentRequests) {
                resetRequest(request);
            }
        }
        return currentRequests;
    }
    
    protected void resetRequest(AccountRequest request) {
        request.setRequestID(null);
        request.setTargetIntegration(null);
        request.setTrackingId(null);        
        if (request.getArguments() != null) {
            /** Remove unused arguments **/
            request.getArguments().remove("id");
            request.getArguments().remove("action");
            request.getArguments().remove("description");
        }
    }
    
    /**
     * Creates a ProvisioningPlan object with the current requests and identity
     * @param identity
     * @param comments
     * @return
     * @throws GeneralException
     */
    protected ProvisioningPlan createPlan(Identity identity, String comments, List<AccountRequest> requests) throws GeneralException{
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identity);
        plan.setSource(Source.LCM);

        if(identity!=null) {
            for(AccountRequest request : requests) {
                AccountRequest copy = (AccountRequest)request.deepCopy((XMLReferenceResolver)getContext());
                resetRequest(copy);
                plan.add(copy);
            }
        }

        if(comments!=null && !comments.equals("")) {
            plan.setComments(comments);
        }
        return plan;
    }

    protected List<AccountRequest> createRequests() throws GeneralException{
        List<AccountRequest> requests = null;
        
        if(requestsJSON!=null && !requestsJSON.equals("[]")) {
            log.info("Request JSON: " + requestsJSON);
            requests = JsonHelper.listFromJson(AccountRequest.class, requestsJSON);
        }
        if(requests!=null) {
            for(AccountRequest request : requests) {
                if(request.getArgument("id")==null) {
                    /** Add a unique identifier so we can get at it from the summary page grid **/
                    request.addArgument("id", Util.uuid());
                }
            }
        }
        return requests;
    }
    
    public void addComment(String comment, String id) {
        if(comment!=null && id!=null && getCurrentRequests()!=null) {
            for(AccountRequest request : currentRequests) {
                if(request.getArgument("id")!=null && request.getArgument("id").equals(id)) {
                    
                    List<AttributeRequest> attrs = request.getAttributeRequests();
                    if(attrs!=null && !attrs.isEmpty()) {
                        for(AttributeRequest attr : attrs) {
                            attr.setComments(comment);
                        }
                    } else {
                        request.setComments(comment);
                    }
                }
            }
            
            getSessionScope().put(ATT_REQUEST_REQUESTS, currentRequests);
        }        
    }
    
    public void addAssignmentNote(String note, String id) {
        if(note!=null && id!=null && getCurrentRequests()!=null) {
            for(AccountRequest request : currentRequests) {
                if(request.getArgument("id")!=null && request.getArgument("id").equals(id)) {
                    
                    List<AttributeRequest> attrs = request.getAttributeRequests();
                    if(attrs!=null && !attrs.isEmpty()) {
                        for(AttributeRequest attr : attrs) {
                            attr.put(ProvisioningPlan.ARG_ASSIGNMENT_NOTE, note);
                        }
                    }
                }
            }
            
            getSessionScope().put(ATT_REQUEST_REQUESTS, currentRequests);
        }        
    }
    
    public String removeRequest() {
        Iterator<AccountRequest> requestIter = getCurrentRequests().iterator();
        while(requestIter.hasNext()) {
            AccountRequest request = requestIter.next();
            if(request.getArgument("id")!=null && request.getArgument("id").equals(getRequestId())) {
                requestIter.remove();
            }
        }
        return "";
    }
  
    /**
     * The given request was removed from the cart.  Subclasses can override
     * this to clean up any state they may have associated with this request.
     */
    protected void requestRemoved(AccountRequest request) {
        // No-op.
    }
    
    /**
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getColumns() throws GeneralException {
        if (null == columns) {
            Object cols = getUIConfig().getAttributes().get(getClass().getName());
            if ((null != cols) && (cols instanceof List)) {
                columns = (List<ColumnConfig>) cols;
            }
        }

        return columns;
    }
    
    public String getColumnJSON() throws GeneralException{
        return getColumnJSON("displayName", getColumns());
    }

    /**
     * Return column config for the search grid (if there is one).
     */
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getSearchColumns() throws GeneralException {
        if (null == searchColumns) {
            Object cols = getUIConfig().getAttributes().get(getClass().getName() + "_search");
            if ((null != cols) && (cols instanceof List)) {
                searchColumns = (List<ColumnConfig>) cols;
            }
        }
        return searchColumns;
    }

    // jsl - why does this belong in a base bean?
    public String getSearchColumnJSON() throws GeneralException{
        return getColumnJSON("Application.name", getSearchColumns());
    }
    
    public String getBackButtonText() {
        return getMessage(MessageKeys.LCM_MAKE_ADDITIONAL_CHANGES);
    }
    
    public boolean isShowComments() {
        return false;
    }
    
    /**
     * Check whether the provided id can be accessed by the
     * logged in user.  
     * @param requestedId
     * @return true if logged in user can make requests for user, false if not
     * @throws GeneralException
     */
    protected boolean isAuthorized(String requestedId) throws GeneralException {
        Identity requestingIdent = getLoggedInUser();
        
        if (Util.isNothing(requestedId)) {
        	return true; // Yes, you may have access to nothing
        }
        
        List<Capability> capabilities = requestingIdent.getCapabilityManager().getEffectiveCapabilities();        
        if (Capability.hasSystemAdministrator(capabilities)) {
            return true;
        }

        LCMConfigService LCMsvc = new LCMConfigService(getContext(), getLocale(), getUserTimeZone());
        String quicklinkName = (String) getSessionScope().get(LCMConfigService.ATT_LCM_CONFIG_SERVICE_QUICKLINK);
        List<String> dynamicScopeNames = getLoggedInUserDynamicScopeNames();
        if (requestedId.equals(requestingIdent.getId())) {
            return LCMsvc.isSelfServiceEnabled(requestingIdent, dynamicScopeNames, quicklinkName, QuickLink.LCM_ACTION_REQUEST_ACCESS);
        }
        QueryOptions idsAllowedQo = LCMsvc.getRequestableIdentityOptions(
                requestingIdent, dynamicScopeNames, quicklinkName, null);
        
        // queryoptions are null, you don't have access to anything
        if (null == idsAllowedQo) return false;
        
        // verify that the id we have is accessible by the logged in user
        idsAllowedQo.add(Filter.eq("id", requestedId));
        if (0 == getContext().countObjects(Identity.class, idsAllowedQo)) {
           // you don't have access to this ID
           return false;
        }
        
        return true;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // ObjectConfig-based Searching
    //
    //////////////////////////////////////////////////////////////////////
    
    public static final String ATT_IDENTITY_ATTRIBUTES = "identityAttributes";

    public List<Map<String,String>> getIdentitySearchAttributes()
            throws GeneralException {
        return buildAttributeMap(new UserAccessPopulationFilterContext(),
                "Identity.",
                true);
    }

    public List<Map<String,String>> getRoleSearchAttributes()
            throws GeneralException {
        return buildAttributeMap(new UserAccessRoleFilterContext(getLoggedInUser(), getIdentity()),
                "",
                false);
    }

    public List<Map<String,String>> getEntitlementSearchAttributes()
            throws GeneralException {

        return buildAttributeMap(
                new UserAccessEntitlementFilterContext(getLoggedInUser(), getIdentity()),
                "",
                false);
    }

    private List<Map<String,String>> buildAttributeMap(ListFilterContext filterContext,
                                                       String prefix,
                                                       boolean isIdentity)
        throws GeneralException {


        List<Map<String, String>> attrList = new ArrayList<Map<String, String>>();
        ListFilterService filterService = new ListFilterService(getContext(), getLocale(), filterContext);
        List<ListFilterDTO> filterDTOs = filterService.getListFilters(false);
        for (ListFilterDTO filterDTO : Util.safeIterable(filterDTOs)) {

            Map<String,String> map = new HashMap<String,String>();
            map.put("name", filterDTO.getProperty());
            map.put("displayName", filterDTO.getLabel());

            // jsl - this is not supposed to be meaningful at this level,
            // HibernatePersistenceManager will do the necessary
            // name mapping if you use a Filter. What was this for?
            // cga - used in RequestAccessSidebarPanel.js during lcm access requests
            // to control the creation of ui components. removed javascript dependency 
            // on extended property. see bug # 16029.
            //if(attr.getExtendedName()!=null) {
            //map.put("extended", attr.getExtendedName());
            //}

            map.put("standard", String.valueOf(filterDTO.isStandard()));

            /** If this is from the Identity config, prefix it with Identity. so we know to join **/
            map.put("prefix", prefix);

            map.put("type", filterDTO.getDataType().getPropertyType().name());

            // Apparently combobox requires a display AND a data field,
            // so create a new list with duplicate values.
            if(filterDTO.getAllowedValues() != null) {
                List<ListFilterDTO.SelectItem> av = filterDTO.getAllowedValues();
                List<String> tmp;
                List<List<String>> allowed = new ArrayList<List<String>>();
                for(ListFilterDTO.SelectItem v : av){
                    tmp = new ArrayList<String>(2);
                    tmp.add((String)v.getId());
                    tmp.add((String)v.getId());
                    allowed.add(tmp);
                }
                map.put("allowed", JsonHelper.toJson(allowed));
            }
            else {
                map.put("allowed", null);
            }

            /** Set the value if this is a single identity request **/
            if (isIdentity) {
                setFilterValue(map, filterDTO);
            }

            attrList.add(map);
        }
        /** Sort the list so that the system attributes are first **/
        Collections.sort(attrList, new Comparator<Map<String,String>>() {
            public int compare(Map<String,String> a, Map<String,String> b) {
                boolean v1 = Util.atob((String)a.get("standard"));
                boolean v2 = Util.atob((String)b.get("standard"));
                return (v1 ^ v2) ? ((v1 ^ true) ? 1 : -1) : 0;
            }}
        );

        return attrList;
    }
    
    
    /** If this is a single identity request, we want to set the value on each of the
     * attributes on the "Search by Population"
     * @param map Map representing the filter 
     * @param filterDTO Filter
     */
    private void setFilterValue(Map<String,String> map, ListFilterDTO filterDTO)
        throws GeneralException {

        map.put("value", "");

        if(getIdentityId()!=null) {
            Object value = getIdentity().getAttribute(filterDTO.getProperty());
            if(value!=null) {                    
                /** Special handling for identity types **/
                if(ListFilterDTO.DataTypes.Identity.equals(filterDTO.getDataType())) {
                    if(value instanceof String) {
                        Identity identity = getContext().getObjectByName(Identity.class, value.toString());
                        map.put("value", identity.getDisplayableName());
                        map.put("valueId", identity.getId());
                    }
                    
                    if(value instanceof sailpoint.object.Identity) {
                        map.put("value", ((Identity)value).getDisplayableName());
                        map.put("valueId", ((Identity)value).getId());
                    }
                    
                } else {                    
                    map.put("value", value.toString());
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Resolve the identity that is being managed. Typically this comes from the session,
     * but in special cases when we are launched through the SRM interface or ServiceNow
     * Catalog Integration then we have to accept a the identity through a parameter.
     * 
     * @return
     * @throws GeneralException
     */
    public String getIdentityId() throws GeneralException {
        // This could be stale, remove it.
        getSessionScope().remove(ATT_UNAUTHORIZED_PARAM_REQUEST);
        
        if (identityId == null) {
            identityId = resolveIdFromExternalParameter();
            if (Util.isNotNullOrEmpty(identityId)) {
                getSessionScope().put(BaseListBean.ATT_EDITFORM_ID, identityId);
            }

            // the normal way to resolve the identity id, through a session variable
            // or through the bulk iterator
            if (identityId == null) {
                identityId = (String) getSessionScope().get(BaseListBean.ATT_EDITFORM_ID);
                if (identityId == null || identityId.equals("")) {
                    if (getIdentityIds() != null && identityIds.size() == 1) {
                        Iterator<String> idsIter = getIdentityIds().iterator();
                        identityId = idsIter.next();
                    }
                }

                // Assume that the request is for self-service and get loggedInUser before authorizing same user
                if (null == identityId && !isBulk()) {
                    log.debug("Assuming logged in user");
                    Identity loggedInUser = getLoggedInUser();
                    identityId = loggedInUser.getId();
                }
            }

            if ( !isAuthorized(identityId) ) {
                identityId = null;
                addMessage(new Message(Message.Type.Error, getMessage(MessageKeys.LCM_NO_ACCESS_TO_REQUESTED_IDENTITY)));
            }
        }
        if(identityId!=null && (identityId.equals("null") || identityId.equals("")))
            identityId = null;

        return identityId;
    }
    
    /**
     * Return Identity id for the specified name.
     * if identity with the specified name not found, return null
     * @param name - name of the identity 
     * @return - identityId or null if not found
     * @throws GeneralException 
     */
    private String getIdentityIdByName(String name) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("name", name));
        Iterator<Object[]> rows = getContext().search(Identity.class,ops,"id");
        if ( (rows != null) && ( rows.hasNext() )  )  {
            Object[] row = rows.next();
            return (String)row[0];
        }
        return null;
    }

    /**
     * Get the identity id from query parameter. This supports only "identityName" for backward
     * compatibility.
     * @throws GeneralException
     */
    protected String resolveIdFromExternalParameter() throws GeneralException {
        String id = null;
        // Support identityName query parameter for request pages. Useful for Direct Report home page widget, for example.
        String parameterIdentityName = this.getRequestParameter("identityName");
        if (Util.isNotNullOrEmpty(parameterIdentityName)) {
            id = getIdentityIdByName(parameterIdentityName);
            if (Util.isNullOrEmpty(id)) {
                throw new GeneralException("Identity with name " + parameterIdentityName + " not found");
            }
        }
        
        return id;
    }

    /**
     * Returns base64 decoded value of a parameter.
     * It checks if encoded string is specified, and then decodes it.
     * 
     * @param name - name of request parameter
     */
    private String getDecodedRequestParameter(String name) {
        String paramValue = (String) getRequestParameter(name);

        if (Util.isNotNullOrEmpty(paramValue)) {
            try {
                // need to consider utf characters while constructing a string
                return Util.bytesToString(Base64.decode(paramValue));
            } catch (GeneralException e) {
                addMessage(e);
            }
        }
        return paramValue;
    }

    public boolean isSelfService() throws GeneralException {
        String identityId = getIdentityId();
        return (null != identityId) && identityId.equals(getLoggedInUser().getId());
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    public Identity getIdentity() throws GeneralException {
        if(identity==null) {
            if(getIdentityId()!=null)
                identity =  getContext().getObjectById(Identity.class, getIdentityId());
        }
        
        if(identity==null && getIdentityIds()==null) {
            try {
                /** Redirect to dashboard if identity is null **/
                getFacesContext().getExternalContext().redirect("../home.jsf");
            } catch (IOException ioe) {
                log.warn("Unable to redirect to home page.  Exception: " + ioe.getMessage());
            }
        }
        return identity;
    }
    
    public String getIdentityNameAttribute() throws GeneralException {
        if (getIdentity() != null) {
            return getIdentity().getName();
        }
        
        return null;
    }

    public String getIdentityName() throws GeneralException {
        if(getIdentity()!=null)
            return getIdentity().getDisplayableName();
        return null;
    }

    public String getRequestsJSON() {
        if(requestsJSON==null)
            return JsonHelper.emptyList();
        return requestsJSON;
    }

    public void setRequestsJSON(String requestsJSON) {
        this.requestsJSON = requestsJSON;
    }

    public List<AccountRequest> getCurrentRequests() {
        if(currentRequests==null) {
            currentRequests = (List<AccountRequest>)getSessionScope().get(ATT_REQUEST_REQUESTS);
        }
        
        if(currentRequests == null) {
            currentRequests = new ArrayList<AccountRequest>();
            getSessionScope().put(ATT_REQUEST_REQUESTS, currentRequests);
        }
        return currentRequests;
    }

    public String getCurrentRequestsJSON() {
        return JsonHelper.toJson(getCurrentRequests());
    }
    
    public void setCurrentRequests(List<AccountRequest> currentRequests) {
        this.currentRequests = currentRequests;
    }

    public Set<String> getIdentityIds() {
        if(identityIds==null) {
            identityIds = (Set<String>)getSessionScope().get(ATT_IDENTITY_IDS);
        }
        return identityIds;
    }

    
    public void setIdentityIds(Set<String> identityIds) {
        this.identityIds = identityIds;
    }
    
    public boolean isBulk() {
        if(!bulk) {
            if(getIdentityIds()!=null && getIdentityIds().size()>1) 
                bulk = true;
        }        
        return bulk;
    }

    public void setBulk(boolean bulk) {
        this.bulk = bulk;
    }

    public List<AccountRequest> getCreatedRequests() {
        return createdRequests;
    }

    public void setCreatedRequests(List<AccountRequest> createdRequests) {
        this.createdRequests = createdRequests;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }    
    
    public void addError(String msg) {
        if(errors==null) {
            errors = new ArrayList<String>();
        }
        errors.add(msg);
    }
    
    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    /** Returns the column metadata for the grid on the right side of the page that contains the cart items **/
    public String getCartColumnJSON() throws GeneralException{
        return getColumnJSON("name", getCartGridColumns());
    }
    
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getCartGridColumns() throws GeneralException {
        if(cartGridColumns==null) {
            cartGridColumns = 
                (List<ColumnConfig>)getUIConfig().getAttributes().get(getClass().getName() + "_cart");
        }
        
        return cartGridColumns;
    }
    
    /** Use reflection to walk over the list of account requests in the cart and produce a json representation
     * for the grid on the request access page.
     * @return
     * @throws GeneralException
     */
    public String getCartItemsJSON() throws GeneralException {
       List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
       if(!getCurrentRequests().isEmpty()) {
           for(AccountRequest request : currentRequests) {
               Map<String,Object> row = new HashMap<String,Object>();
               
               for(ColumnConfig config : getCartGridColumns()) {
                   Object value = null;
                   try {              
                       value = PropertyUtils.getNestedProperty(request, config.getProperty());
                   } catch(NoSuchMethodException nsme) {
                       if(log.isDebugEnabled()) 
                           log.debug("Unable to get value of ["+config.getProperty()+"] from Account Request Object, trying Arguments...");
                       
                       /** Try looking in the arguments bag next **/
                       value = request.get(config.getProperty());
                       row.put(config.getDataIndex(), value);
                   } catch(Exception e) {
                       /** I guess this is more serious if we hit this point, so log and throw? **/
                       if(log.isWarnEnabled()) {
                           log.warn("Unable to get value of ["+config.getProperty()+"] from Account Request", e);
                       }
                   }

                   row.put(config.getDataIndex(), value);
               }
               rows.add(row);
           }
       }
       GridResponse response = new GridResponse(null, rows, rows.size());
       return JsonHelper.toJson(response);
    }

    /**
     * If an identity is specified over the querystring, we need to provide immediate feedback to the user that they can't
     * request for this identity.  In some cases, a user may drop into this request url from a third party and so
     * they may not have used IIQ to choose the identity for whom they are requesting.
     *
     * Authorization is actually determined in the <code>getIdentityId()</code> method.  We evaluate a session
     * parameter here to determine if that was successful.
     *
     * @return true if the request is unauthorized.
     */
    public boolean isUnauthorizedParam() {
        unauthorized = false;
        if( null != getSessionScope().get(ATT_UNAUTHORIZED_PARAM_REQUEST) ) {
            if ((Boolean) getSessionScope().get(ATT_UNAUTHORIZED_PARAM_REQUEST) ) {

                addMessage(new Message(Message.Type.Error, getMessage(MessageKeys.LCM_NO_ACCESS_TO_REQUESTED_IDENTITY)));
                addMessage(new Message(Message.Type.Error, getMessage("invalid_session_msg")));

                _hasError = true;
                unauthorized = true;
            }
        }

        return unauthorized;
    }

    public void setUnauthorized(boolean b) {
        unauthorized = b;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Request bean information
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @SuppressWarnings("unchecked")
    void saveRequestClass() {
        getSessionScope().put(ATT_REQUEST_CLASS, getClass().getName());
    }

    String getRequestClass() throws GeneralException {
        String clazz = (String) getSessionScope().get(ATT_REQUEST_CLASS);
        if (null == clazz) {
            return null;
        }
        
        return clazz;
    }

    BaseRequestBean getRequestingBean() throws GeneralException {
        String clazz = getRequestClass();
        if(clazz!=null)
            return (BaseRequestBean) Reflection.newInstance(clazz);
        
        return null;
    }
    
    public String getPageTitle() {
        // default to 'Select Access'
        return getMessage(MessageKeys.LCM_BREADCRUMB_SELECT_ACCESS);
    }

    
}
