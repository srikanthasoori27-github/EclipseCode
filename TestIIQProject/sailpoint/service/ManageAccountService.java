package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Application;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItem;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.service.suggest.SuggestService;
import sailpoint.service.suggest.SuggestServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.util.Sorter;

public class ManageAccountService {
    public static final String ACCOUNT_REQUEST_FLOW = IdentityResetService.Consts.Flows.ACCOUNTS_REQUEST_FLOW.value();

    private final QuickLink quickLink;
    private final Identity requester;
    private final Identity requestee;
    private final SailPointContext context;
    private final String externalApp;
    private final String ticketApp;
    private final UserContext userContext;
    private WorkItem.Level priority = WorkItem.Level.Normal;
    private static final Log log = LogFactory.getLog(ManageAccountService.class);
    
    private class ManageAccountApplicationSuggestContext implements SuggestServiceContext {
        private UserContext userContext;
        private static final String APPLICATION = "Application";
        private String suggestClass;
        private String filterString;
        private int start = 0;
        private int limit = 0;
        private String query;
        private Sorter sorter;

        ManageAccountApplicationSuggestContext(UserContext userContext) {
            this.userContext = userContext;
        }
        
        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) {
            return (sorter == null) ? null : Arrays.asList(sorter);
        }

        public void setSorter(Sorter sorter) {
            this.sorter = sorter;
        }
        
        public String getGroupBy() {
            // Group By not supported
            return null;
        }

        public List<Filter> getFilters() {
            // filters handled with filterString
            return null;
        }
        
        @Override
        public String getSuggestClass() {
            return APPLICATION;
        }

        public void setSuggestClass(String suggestClass) {
            this.suggestClass = suggestClass;
        }

        @Override
        public String getFilterString() {
            return filterString;
        }
        
        public void setFilterString(String filterString) {
            this.filterString = filterString;
        }
        
        
        @Override
        public Collection<String> getLoggedInUserRights() {
            return this.userContext.getLoggedInUserRights();
        }
 
        @Override
        public SailPointContext getContext() {
            return userContext.getContext();
        }
        @Override
        public String getLoggedInUserName() throws GeneralException {
            return userContext.getLoggedInUserName();
        }
        @Override
        public Identity getLoggedInUser() throws GeneralException {
            return userContext.getLoggedInUser();
        }
        @Override
        public List<Capability> getLoggedInUserCapabilities() {
            return userContext.getLoggedInUserCapabilities();
        }
        @Override
        public List<String> getLoggedInUserDynamicScopeNames()
                throws GeneralException {
            return userContext.getLoggedInUserDynamicScopeNames();
        }
        @Override
        public Locale getLocale() {
            return userContext.getLocale();
        }
        @Override
        public TimeZone getUserTimeZone() {
            return userContext.getUserTimeZone();
        }
        @Override
        public boolean isMobileLogin() {
            return userContext.isMobileLogin();
        }
        @Override
        public boolean isObjectInUserScope(SailPointObject object)
                throws GeneralException {
            return userContext.isObjectInUserScope(object);
        }
        @Override
        public boolean isObjectInUserScope(String id, Class clazz)
                throws GeneralException {
            return userContext.isObjectInUserScope(id, clazz);
        }
        @Override
        public boolean isScopingEnabled() throws GeneralException {
            return userContext.isScopingEnabled();
        }
    }
    
    /**
     * Constructor.
     *
     * @param quickLink   The quicklink authorizing access to the identity
     * @param requester   The Identity doing the requesting
     * @param requestee   The Identity that was requested for
     * @param externalApp External application
     * @param ticketApp   External ticketing application
     * @param context     Context to look stuff up in
     */
    public ManageAccountService(QuickLink quickLink, Identity requester, Identity requestee, String ticketApp, String externalApp,
                                SailPointContext context, UserContext userContext) {
        this.quickLink = quickLink;
        this.requester = requester;
        this.requestee = requestee;
        this.context = context;
        this.externalApp = externalApp;
        this.ticketApp = ticketApp;
        this.userContext = userContext;
    }

    /**
     * @param accountActions
     * @return
     */
    public WorkflowResultItem submitAccountDecisions(List<AccountAction> accountActions, WorkItem.Level priority) throws GeneralException {
        this.priority = priority;
        List<AccountRequest> accountRequests = createAccountRequestList(accountActions);
        WorkflowSession workflowSession = createLCMRequestProcessor().startWorkflow(accountRequests, ACCOUNT_REQUEST_FLOW);
        WorkflowSessionService workflowSessionService = new WorkflowSessionService(context, null, workflowSession);
        return workflowSessionService.createWorkflowResult(this.userContext, false);
    }

    /**
     * create a LCMRequestProcessor object to help start request workflow
     *
     * @return LCMRequestProcessor object
     */
    private LCMRequestProcessor createLCMRequestProcessor() {
        return new LCMRequestProcessor(this.context, this.requester, this.requestee, this.quickLink, this.ticketApp, this.externalApp, this.priority);
    }



    /**
     * Create a list of AccountRequests using AccountAction list
     *
     * @param accountActions - list of AccountAction objects
     * @return list of AccountRequests
     */
    private List<AccountRequest> createAccountRequestList(List<AccountAction> accountActions) throws GeneralException {
        List<AccountRequest> accountRequests = new ArrayList<AccountRequest>();
        for (AccountAction accountAction : accountActions) {
            Link link = accountAction.getLink();
            accountRequests.add(createAccountRequest(link, accountAction));
        }
        return accountRequests;
    }

    /**
     * create AcountRequest with given link and action decision
     *
     * @param link
     * @return AccountRequest
     * @throws GeneralException
     */
    private AccountRequest createAccountRequest(Link link, AccountAction action) throws GeneralException {
        AccountRequest.Operation op = selectOperation(action.getAction());
        AccountRequest accountRequest = new AccountRequest(op,
                link.getApplicationName(),
                link.getInstance(),
                link.getNativeIdentity());
        if (AccountRequest.Operation.Create.equals(op)) {
            if (validateCreateRequest(link.getApplication(), accountRequest)) {
                if (log.isDebugEnabled()) 
                    log.debug("Request for new account on app " + link.getApplicationName() + " is valid.");
                IdentityService identityService = new IdentityService(context);
                List<Link> links = identityService.getLinks(requestee, link.getApplication());
                if (links != null && !Util.isEmpty(links)) {
                    accountRequest.put(ProvisioningPlan.ARG_FORCE_NEW_ACCOUNT, "true");
                }
            } else {
                // you don't have access to this app
                String errorMsg = "Logged in identity: " + this.userContext.getLoggedInUser().getId() + " may not request this new account.";
                if (log.isErrorEnabled())
                    log.error(errorMsg);
                throw new GeneralException(errorMsg);
            }
        }

        // Add in operation arg for labels in manage accounts page
        accountRequest.put("operation", op);

        if (!Util.isNullOrEmpty(action.getComment())) {
            accountRequest.put(ProvisioningPlan.ARG_COMMENTS, action.getComment());
        }
        createLCMRequestProcessor().validate(accountRequest);
        return accountRequest;
    }

    private AccountRequest.Operation selectOperation(String action) throws GeneralException {
        AccountRequest.Operation op = null;
        try {
            op = AccountRequest.Operation.valueOf(action);
        } catch (Exception ex) {
            throw new GeneralException("Action selected is not available", ex);
        }
        return op;
    }
    
    /**
     * Create SuggestService
     * @return SuggestService
     */
    private SuggestService getSuggestService() {
        ManageAccountApplicationSuggestContext appSuggestContext = new ManageAccountApplicationSuggestContext(userContext);
        return new SuggestService(appSuggestContext);
    }
    
    /**
     *  Validate a create request 
     * 
     *  Query the db using a count query to make sure that the 
     *  application being requested exists and that the user
     *  has access to the application. Down in getLCMAccountOnlyAppFilter
     *  it builds a filter that applies any LCM rules.
     *  
     *  If the request is valid and we find an account that
     *  already exists on the Application add the ARG_FORCE_NEW_ACCOUNT
     *  so a new account gets created vs. changing the existing one.
     *  @param app - application
     *  @param request - account request to validate
     *  @return true if request is valid
     *  @throws GeneralException
     */
    private boolean validateCreateRequest(Application app, AccountRequest request) throws GeneralException {
        if (log.isDebugEnabled()) 
            log.debug("Checking to see if the request for new account on app " + app.getName() + " is valid...");
        
        // validate that the user has permissions to request this app
        Filter f = getSuggestService().getLCMAccountOnlyAppFilter(requestee.getId());
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        qo.add(Filter.eq("id", app.getId()));
        int cnt = context.countObjects(Application.class, qo);
        boolean validRequest = (cnt > 0);
        return validRequest;
    }

    /**
     * Query the list of applications that have been configured in the lcm config that are available for
     * creating a new account
     * @return boolean
     * @throws GeneralException
     */
    public boolean isAccountOnlyAppsAvailable() throws GeneralException {
        Filter f = getSuggestService().getLCMAccountOnlyAppFilter(requestee.getId());
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        int cnt = context.countObjects(Application.class, qo);
        return (cnt > 0);
    }

    /**
     * Return whether the current user is allowed to request new accounts for themselves or others
     * @param dynamicScopeNames
     * @return boolean
     * @throws GeneralException
     */
    public boolean isAllowAccountOnlyRequests(List<String> dynamicScopeNames) throws GeneralException {
        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(context);
        return svc.isRequestControlOptionEnabled(requester,
                dynamicScopeNames,
                quickLink.getName(),
                quickLink.getAction(),
                Configuration.LCM_ALLOW_ACCOUNT_ONLY_REQUESTS,
                Util.nullSafeEq(requestee, requester));
    }


    /**
     * Determines whether the current user is allowed to manage existing accounts or not.  If this is false,
     * we don't return any accounts
     * @param dynamicScopeNames A list of names of dynamic scopes to apply to the query
     * @return Whether the current user can manage existing accounts for the requestee
     * @throws GeneralException
     */
    public boolean isAllowManageExistingAccounts(List<String> dynamicScopeNames) throws GeneralException {
        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(context);
        return svc.isRequestControlOptionEnabled(requester,
                dynamicScopeNames,
                null,
                QuickLink.LCM_ACTION_MANAGE_ACCOUNTS,
                Configuration.LCM_ALLOW_MANAGE_EXISTING_ACCOUNTS,
                Util.nullSafeEq(requestee, requester));
    }
}