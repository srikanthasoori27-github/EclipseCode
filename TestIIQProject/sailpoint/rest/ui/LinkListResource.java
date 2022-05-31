/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import sailpoint.api.PasswordPolicyException;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.PasswordGenerationAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QuickLink;
import sailpoint.object.WorkItem;
import sailpoint.object.Application.Feature;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.AccountAction;
import sailpoint.service.AccountRefreshResult;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.BulkPasswordChangeResult;
import sailpoint.service.LinkListService;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.ManageAccountService;
import sailpoint.service.ManagePasswordConfigDTO;
import sailpoint.service.ManagePasswordService;
import sailpoint.service.MessageService;
import sailpoint.service.PasswordChangeError;
import sailpoint.service.RequestAccessService;
import sailpoint.service.SelectionModel;
import sailpoint.service.WorkflowResultItem;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.link.PasswordLinkDTO;


/**
 * @author: cindy.he
 * 
 * This link list resource has been built to help retrieve links belongs to the identity
 * 
 */

public class LinkListResource extends BaseListResource implements BaseListServiceContext{

    public final static String MANAGE_PASSWORD_LINK_COL_CONFIG = "managePasswordLinkColConfig";
    public final static String ACCOUNT_LINK_COL_CONFIG = "manageAccountLinkColConfig";
    public final static String SELECTION_MODEL = "selectionModel";
    public final static String DECISIONS = "decisions";
    public final static String PRIORITY = "priority";
    public final static String LINK_PASSWORD_MAP = "linkPasswordMap";
    public static final String SYNCHRONIZED = "synchronized";
    public static final String LINK_IDS = "linkIds";

    private String identityId;
    private String action;
    private String quickLinkName;
    
    public LinkListResource(BaseResource parent, String identityId, String lcmAction,
            String quickLinkName) throws GeneralException {
        super(parent);
        if (identityId == null) {
            throw new InvalidParameterException("identityId");
        }
        this.identityId = identityId;
        this.action = lcmAction;
        this.quickLinkName = quickLinkName;
    }

    /**
     * ex: /ui/rest/managePasswords/identities/12345/links/1234/?limit=12
     *
     * Returns a list of links that the identity has that supports password change
     * quick link action decides what types of links will be returned
     * <p/>
     * The url for this looks something like
     * /ui/rest/managePasswords/identities/12345/links/1234?start=0&limit=12
     * <p/>
     * where the defined query parameters are:
     * <p/>
     * start: the index of the first item to return (for paging)
     * limit: the maximum number of identities to return (for paging)
     * <p/>
     *
     * @return ListResult JSON with representations of links
     * @throws GeneralException
     */

    @GET
    public ListResult getLinks() throws GeneralException {
      
        if (action.equals(QuickLink.LCM_ACTION_MANAGE_PASSWORDS)) {                       
            return getManagePasswordLinks();
        }
        else if (action.equals(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS)){
            return getManageAccountLinks();
        }    
        else {
            throw new GeneralException("Unknow quick link action.");
        }
    }

    /**
     * return list result of manage password links
     * trim and sorted using the following
     * start: the index of the first item to return (for paging)
     * limit: the maximum number of identities to return (for paging)
     * sorter: sortable columns
     * @return ListResult JSON with representations of links
     * @throws GeneralException
     */
    public ListResult getManagePasswordLinks() throws GeneralException{
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        LinkListService listService = createLinkListService(MANAGE_PASSWORD_LINK_COL_CONFIG);
        return listService.getManagePasswordLinkListResult();
    }
        
    /**
     * Return a list result of manage account password link
     * @return {ListResult} JSON with representations of account link DTOs
     * @throws GeneralException
     */
    public ListResult getManageAccountLinks() throws GeneralException{
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS);
        ManageAccountService service = getAccountService();
        boolean supportsManageExistingAccounts = service.isAllowManageExistingAccounts(getLoggedInUserDynamicScopeNames());
        LinkListService listService = createLinkListService(ACCOUNT_LINK_COL_CONFIG);
        return listService.getManageAccountLinkListResult(supportsManageExistingAccounts);
    }
    
    /**
     * Call service to get account attributes map
     * @param linkId link id
     * @return Map of link attributes that has non-null value
     * @throws GeneralException
     */
    @Path("{linkId}")
    public LinkResource getLinkResource(@PathParam("linkId") String linkId) throws GeneralException {
        if (linkId == null) {
            throw new InvalidParameterException("Missing linkId");
        }

        Link link = getLink(linkId);
        if (!this.identityId.equalsIgnoreCase(link.getIdentity().getId())) {
            throw new ObjectNotFoundException(Link.class, linkId);
        }
        if (link.getIdentity() == null) {
            throw new GeneralException("link missing identity");
        }

        return new LinkResource(this, link, quickLinkName, action, identityId);
    }


    /**
     * Return the config for a user to see if they can request a new accounts
     * @return A simple map that contains the boolean values
     * @throws GeneralException
     */
    @GET
    @Path("createAccountConfig")
    public Map<String, Object> getCreateAccountConfig() throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS);
        ManageAccountService service = getAccountService();
        Map<String, Object> result = new HashMap<String,Object>();
        result.put("allowAccountOnlyRequests", service.isAllowAccountOnlyRequests(getLoggedInUserDynamicScopeNames()));
        result.put("accountOnlyAppsAvailable", service.isAccountOnlyAppsAvailable());
        return result;
    }

    /**
     * Refresh the status of the links with IDs found in the given data by reaggregating them.
     *
     * @param data  The POST data with a "linkIds" property that has a list of IDs.
     *
     * @return A List of AccountRefreshResults.
     */
    @POST
    @Path("refresh") @SuppressWarnings("unchecked")
    public List<AccountRefreshResult> refreshLinks(Map<String,Object> data) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS);

        List<String> linkIds = (List<String>) data.get(LINK_IDS);
        if (null == linkIds) {
            throw new InvalidParameterException("linkIds are required in data");
        }

        ManageAccountService service = getAccountService();
        boolean supportsManageExistingAccounts = service.isAllowManageExistingAccounts(getLoggedInUserDynamicScopeNames());
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(LinkListResource.ACCOUNT_LINK_COL_CONFIG);
        LinkListService llService = new LinkListService(identityId, getContext(), this, selector);
        return llService.refreshLinkStatuses(linkIds, supportsManageExistingAccounts);
    }

    /**
     * REST endpoint to merge password policy constraints for bulk password request
     * @param input Map with selectionModel to hold information on which links are selected
     * @return response from passwordService mergeConstraints()
     */
    @POST
    @Path("mergeConstraints")
    public Response mergeConstraints(Map<String, Object> input)
        throws GeneralException {      
        ManagePasswordService pwordService = getPasswordService();
        /* Ensure we have the correct parameters */
        if(input.get(SELECTION_MODEL) == null) {
            throw new InvalidParameterException("selectionModel must be provided");
        }
        Map<String, Object> selectionModelMap = new HashMap<String, Object> ();
        if (input.get(SELECTION_MODEL) instanceof Map<?,?>) {
            selectionModelMap = (Map<String, Object>)input.get(SELECTION_MODEL);
            if (Util.isEmpty(selectionModelMap)) {
                throw new InvalidParameterException("selectionModel must be provided");
            }
        }
        else {
            throw new InvalidParameterException("selectionModel should be an instance of Map");
        }
        
        SelectionModel selectionModel = new SelectionModel(selectionModelMap);
        LinkListService linkListService = createLinkListService(ACCOUNT_LINK_COL_CONFIG);
        List<Link> links = linkListService.getManagePasswordLinks(selectionModel, this.getIdentity());

        try {  
            return Response.ok(pwordService.mergeConstraints(links)).build();
        } catch(PasswordPolicyException ex) {
            MessageService messageService = new MessageService(this);
            return Response.status(Response.Status.BAD_REQUEST).entity(messageService.getLocalizedMessages(ex.getAllMessages())).build();
        } 
    }

    /**
     * REST endpoint for submitting synchronize password requests
     * Posted data
     * - selectionModel - a SelectionModel
     * - newPassword - the password to synchronize across accounts
     *
     * @param input Map with selectionModel and newPassword
     * @return Response wrapping BulkPasswordChangeRequest representing the workflow status and status of individual
     *  password changes or if failed from password policy constraint violations a BAD_REQUEST response wrapping a
     *  PasswordChangeError
     */
    @POST
    @Path("synchronizePassword")
    public Response synchronizePassword(Map<String, Object> input) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        /* Ensure we have the correct parameters */
        if(input.get(SELECTION_MODEL) == null) {
            throw new InvalidParameterException("selectionModel must be provided");
        }
        if (Util.isNullOrEmpty((String)input.get(ManagePasswordService.NEW_PASSWORD))) {
            throw new InvalidParameterException("newPassword must be provided");
        }
            
        /* Read off the parameters */
        String newPassword = (String) input.get(ManagePasswordService.NEW_PASSWORD);
        Map<String, Object> selectionModelMap = new HashMap<String, Object> ();
        Map<String, String> linkPasswordMap = new HashMap<String, String> ();
        if (input.get(SELECTION_MODEL) instanceof Map<?,?>) {
            selectionModelMap = (Map<String, Object>)input.get(SELECTION_MODEL);
            if (Util.isEmpty(selectionModelMap)) {
                throw new InvalidParameterException("selectionModel must be provided");
            }
        }
        else {
            throw new InvalidParameterException("selectionModel should be an instance of Map");
        }
        
        if(input.containsKey(LINK_PASSWORD_MAP)) {
            linkPasswordMap = (Map<String, String>)input.get(LINK_PASSWORD_MAP);
        }
        
        SelectionModel selectionModel = new SelectionModel(selectionModelMap);
        ManagePasswordService passwordService = getPasswordService();
        LinkListService linkListService = createLinkListService(ACCOUNT_LINK_COL_CONFIG);
        List<Link> links = linkListService.getManagePasswordLinks(selectionModel, this.getIdentity());

        //IIQETN-4792 :- Verifying if the selected application is requiring current password.
        for (Link link : links) {
            String currentPass = linkPasswordMap.get(link.getId());
            if (currentPass == null || (currentPass != null && currentPass.length() == 0)) {
                String features = link.getApplication().getFeaturesString();
                boolean isCurrentPassRequired = false;
                if (features != null) {
                    isCurrentPassRequired = features.indexOf(Feature.CURRENT_PASSWORD.toString()) >= 0;
                }
                if (isCurrentPassRequired && identityId.equalsIgnoreCase(getLoggedInUser().getId())) {
                    throw new InvalidParameterException("Current password must be provided.");
                }
            }
        }

        try {
            BulkPasswordChangeResult changeResult = passwordService.submitSynchronizePasswordRequest(links, newPassword, linkPasswordMap);
            return Response.ok(changeResult).build();
        } catch(ManagePasswordService.PasswordChangeException ex) {
            /* There is no link for bulk password change violation constraints */
            PasswordChangeError passwordChangeError = new PasswordChangeError(null, ex.getMessages(), ex.isConstraintViolation());
            return Response.status(Response.Status.BAD_REQUEST).entity(passwordChangeError).build();
        }
    }

    /**
     * REST endpoint for submitting generate synchronized and unique password requests
     * Posted data
     *  - selectionModel - The selection model indicating what accounts to change password on
     *  - synchronized - if true all accounts get the same password
     *
     * @param input Map with selectionModel and syncrhonized
     * @return Response wrapping BulkPasswordChangeRequest representing the workflow status and status of individual
     *  password changes or if failed a BAD_REQUEST response wrapping a PasswordChangeError
     * @throws GeneralException
     */
    @POST
    @Path("generatePassword")
    public BulkPasswordChangeResult generatePassword(Map<String, Object> input) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        authorize(new PasswordGenerationAuthorizer());
        Map<String, Object> selectionModelMap = new HashMap<String, Object> ();
        /* Ensure we have the correct parameters */
        if (input.get(SELECTION_MODEL) == null) {
            throw new InvalidParameterException("selectionModel must be provided");
        }
        if (input.get(SYNCHRONIZED) == null) {
            throw new InvalidParameterException("synchronized must be provided");
        }
        /* Read off the parameters */
        Boolean isSynchronize = (Boolean) input.get(SYNCHRONIZED);
        if (input.get(SELECTION_MODEL) instanceof Map<?,?>) {
            selectionModelMap = (Map<String, Object>)input.get(SELECTION_MODEL);
            if (Util.isEmpty(selectionModelMap)) {
                throw new InvalidParameterException("selectionModel must be provided");
            }
        }
        else {
            throw new InvalidParameterException("selectionModel should be an instance of Map");
        }
        
        SelectionModel selectionModel = new SelectionModel(selectionModelMap);
        ManagePasswordService passwordService = getPasswordService();

        LinkListService linkListService = createLinkListService(ACCOUNT_LINK_COL_CONFIG);
        List<Link> links = linkListService.getManagePasswordLinks(selectionModel, this.getIdentity());
        BulkPasswordChangeResult changeResult;
        if(isSynchronize) {
            changeResult = passwordService.submitGenerateSynchronizedPasswordRequest(links);
        } else {
            changeResult = passwordService.submitGenerateUniquePasswordsRequest(links);
        }

        return changeResult;
    }

    /**
     * REST endpoint for fetching any links that require the user to enter their current password from the
     * backend
     * @param input Map with selectionModel
     * @return Response wrapping List<PasswordLinkDTO> representing the list of links that require the user to
     * enter their current password
     * @throws GeneralException
     */
    @POST
    @Path("currentPasswordLinks")
    public Response getCurrentPasswordLinks(Map<String, Object> input) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_PASSWORDS);
        Map<String, Object> selectionModelMap = new HashMap<String, Object> ();
        /* Ensure we have the correct parameters */
        if(input.get(SELECTION_MODEL) == null) {
            throw new InvalidParameterException("selectionModel must be provided");
        }
        if (input.get(SELECTION_MODEL) instanceof Map<?,?>) {
            selectionModelMap = (Map<String, Object>)input.get(SELECTION_MODEL);
            if (Util.isEmpty(selectionModelMap)) {
                throw new InvalidParameterException("selectionModel must be provided");
            }
        }
        else {
            throw new InvalidParameterException("selectionModel should be an instance of Map");
        }
        
        SelectionModel selectionModel = new SelectionModel(selectionModelMap);
        LinkListService linkListService = createLinkListService(ACCOUNT_LINK_COL_CONFIG);
        List<PasswordLinkDTO> linkDTOs = linkListService.getCurrentPasswordLinks(selectionModel, getIdentity());
        return Response.ok(linkDTOs).build();
    }

    /**
     * Take decisions and priorty from input, build account requests from the decisions, call
     * manageAccountService submit account decisions
     * @param input Map contains priority and decisions
     * @return WorkflowResultItem get status of workflow
     * @throws GeneralException
     */
    @POST
    @Path("submitAccountDecisions")
    public WorkflowResultItem submitAccountDecisions(Map<String, Object> input) throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS);
        WorkItem.Level priority = WorkItem.Level.Normal;
        Map<String, Object> decisionMap = new HashMap<String, Object> ();
        if(input.get(PRIORITY) != null) {
            priority = WorkItem.Level.valueOf((String)input.get(PRIORITY));
        }
        if(input.get(DECISIONS) == null) {
            throw new InvalidParameterException("Account decisions must be provided");
        }
        if (input.get(DECISIONS) instanceof Map<?,?>) {
            decisionMap = (Map<String, Object>)input.get(DECISIONS);
            if (Util.isEmpty(decisionMap)) {
                throw new InvalidParameterException("Account decisions must be provided");
            }
        }
        else {
            throw new InvalidParameterException("Account decisions should be an instance of Map");
        }
       return getAccountService().submitAccountDecisions(buildAccountRequests(decisionMap), priority);
    }

    /**
     * Get the SuggestResource for the application suggest. Used when creating new account.
     * @return SuggestResource
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorizeByQuickLink(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS);

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext().add(Application.class.getSimpleName());
        return new SuggestResource(this, authorizerContext);
    }

    /**
     * Gets a ManagePassword configuration object.
     * @return ManagePasswordConfigDTO object.
     * @throws GeneralException
     */
    @GET
    @Path("passwordConfig")
    public ManagePasswordConfigDTO getPasswordConfig() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        ManagePasswordService service = getPasswordService();
        return service.getConfig();
    }

    /**
     * Build AccountAction list from decisionMap
     * @param decisionMap - key: linkId, value AccountAction
     * @return List<AccountRequest> list of AccountRequest objects
     * @throws GeneralException is decisionMap is invalid
     */
    private List<AccountAction> buildAccountRequests(Map<String, Object> decisionMap) throws GeneralException{
        List<AccountAction> accountActions = new ArrayList<AccountAction>();
        Iterator it = decisionMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> decision = (Map.Entry<String, Object>)it.next();
            Object accountActionObj = decision.getValue();
            if ( accountActionObj instanceof Map<?,?>) {
                Map<String, Object> accountAction = (Map<String, Object>) accountActionObj;
                Link link = null;

                // If this is a create request, create a new link to hold onto a few properties
                if(Util.nullSafeEq(accountAction.get(AccountAction.ACTION), AccountAction.ACTION_CREATE)) {
                    link = createLinkFromCreateRequest(accountAction);

                } else {
                    String linkId = decision.getKey();
                    link = this.getContext().getObjectById(Link.class, linkId);
                    if (link == null) {
                        throw new InvalidParameterException("Account with ID " + decision.getKey() + " does not exist");
                    }
                }
                accountActions.add(new AccountAction(link, accountAction));

            } else {
                throw new InvalidParameterException("Account decision should be an instance of Map");
            }
        }
        return accountActions;
    }

    /**
     * Build the acount service
     * @return A acount service bound to the requester, requestee, and quicklink
     * @throws GeneralException If no quicklink is found
     */
    private ManageAccountService getAccountService() throws GeneralException {
        QuickLink quickLink = getContext().getObjectByName(QuickLink.class, quickLinkName);
        if(quickLink == null) {
            throw new InvalidParameterException("No quicklink specified");
        }
        String ticketApp = (String)getSession().getAttribute(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        String externalSource = (String)getSession().getAttribute(RequestAccessService.ATT_EXTERNAL_SOURCE);

        return new ManageAccountService(quickLink, getLoggedInUser(), this.getIdentity(), ticketApp, externalSource, getContext(), this);
    }

    /**
     * Build the password service
     * @return A password service bound to the requester, requestee, and quicklink
     * @throws GeneralException If no quicklink is found
     */
    private ManagePasswordService getPasswordService() throws GeneralException {
        QuickLink quickLink = getContext().getObjectByName(QuickLink.class, quickLinkName);
        if(quickLink == null) {
            throw new InvalidParameterException("No quicklink specified");
        }
        String ticketApp = (String)getSession().getAttribute(RequestAccessService.ATT_TICKET_MANAGEMENT_APPLICATION);
        String externalSource = (String)getSession().getAttribute(RequestAccessService.ATT_EXTERNAL_SOURCE);

        return new ManagePasswordService(quickLink, getLoggedInUser(), this.getIdentity(), ticketApp, externalSource, getContext(), this);
    }
    
    /**
     * create link list service base on column key
     * @param columnKey
     * @return LinkListService
     * @throws GeneralException
     */
    private LinkListService createLinkListService (String columnKey) throws GeneralException {
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(columnKey);
        return new LinkListService(this.identityId, getContext(), this, selector);
    }
    
    /**
     * Return the identity we're operating on.
     * @return Identity
     */
    private Identity getIdentity() throws GeneralException {
        return getContext().getObjectById(Identity.class, this.identityId);
    }

    /**
     * Fetches the link object
     * @param linkId the link id
     * @return The link this resource is for
     * @throws GeneralException  If no link is found
     */
    private Link getLink(String linkId) throws GeneralException {
        if (linkId == null) {
            throw new InvalidParameterException("Missing linkId");    
        }
        
        Link link = getContext().getObjectById(Link.class, linkId);
        if(link == null) {
            throw new ObjectNotFoundException(Link.class, linkId);
        }
        return link;
    }

    /**
     * Authorize by quick link name, default to quick link action
     * @param action The action to authorize
     * @throws GeneralException
     */
    private void authorizeByQuickLink(String action) throws GeneralException{
        LcmRequestAuthorizer authorizer = new LcmRequestAuthorizer(getIdentity());
        authorizer.setQuickLinkName(this.quickLinkName);
        authorizer.setAction(action);
        authorize(authorizer);
    }

    /**
     * Creates a dummy link from an accountAction object received from the client.  We do this so that we can
     * pass information to the ManageAccountService in order to submit requests to create new accounts
     * @param accountAction {Map} The map containing identityId, applicationId, action, etc...
     * @return {Link} The dummy link with just enough information
     * @throws GeneralException
     */
    private Link createLinkFromCreateRequest(Map<String,Object> accountAction) throws GeneralException {
        Link link = new Link();
        Application application = getContext().getObjectById(Application.class, (String)accountAction.get(AccountAction.APPLICATION_ID));
        if(application!=null) {
            link.setApplication(application);
        }
        link.setIdentity(this.getIdentity());
        return link;
    }
}