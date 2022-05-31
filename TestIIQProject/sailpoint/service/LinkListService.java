/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.PasswordPolice;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.link.AccountLinkDTO;
import sailpoint.web.link.LinkDTO;
import sailpoint.web.link.PasswordLinkDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.Sorter;

/**
 * @author: cindy.he
 *
 * link list service provides service to get link maps of given identity
 */

public class LinkListService extends BaseListService<BaseListServiceContext> {

    public static final String PASSWORDS_REQUEST = "PasswordsRequest";
    public static final String ACCOUNTS_REQUEST = "AccountsRequest";
    private static final String ACTION_STATUS = "actionStatus";
    private static final String APPROVAL_STATUS = "approvalStatus";
    private static final String PASSWORD_CHANGE_DATE = "passwordChangeDate";
    private static final String PREVIOUS_ACTION = "previousAction";
    private static final String PREVIOUS_ERRORS = "errors";
    private static final String LINK_PROPERTIES = "id, displayName, application.name, lastRefresh";
    public static final String COL_LINK_STATUS = "status";

    private static Log log = LogFactory.getLog(LinkListService.class);

    private String identityId;
    private PlanCompiler compiler;


    /**
     * Constructor.
     *
     * @param context  The SailPointContext.
     * @param listServiceContext  The list service context that provides information about the list.
     * @param columnSelector Column selector used
     */
    public LinkListService(String identityId, SailPointContext context, BaseListServiceContext listServiceContext, 
            ListServiceColumnSelector columnSelector) throws GeneralException {
        super(context, listServiceContext, columnSelector);
        if (identityId == null) {
            throw new InvalidParameterException("certificationId");
        }
        this.identityId = identityId;
    }

    /**
     *
     * @return list of all links of the given identity
     * @throws GeneralException
     */
    public List<Link> getLinks() throws GeneralException{
        if (getIdentity() != null) {
            return getIdentity().getLinks();
        }
        else {
            throw new ObjectNotFoundException(Identity.class, "Identity is null");
        }
    }

    /**
     * Returns the links indicated filtering out links that do not support password change
     *
     * @param selectionModel selection model
     * @param identity - the owner of the links
     * @return list of links selected
     * @throws GeneralException
     */
    public List<Link> getManagePasswordLinks(SelectionModel selectionModel, Identity identity) throws GeneralException {
        List<String> selectedIds = selectionModel.getItemIds();
        List<Link> links = null;
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        if (selectionModel.isInclude() && !Util.isEmpty(selectedIds)) {
            ops.add(Filter.in("id", selectedIds));
        }
        else {
            if(!Util.isEmpty(selectedIds)) {
                ops.add(Filter.not(Filter.in("id", selectedIds)));
            }
        }
        links = Util.iteratorToList(getContext().search(Link.class, ops));
        List<Link> passwordChangeLinks = new ArrayList<Link>();
        for (Link link : links) {
            if(supportsSetPassword(link)) {
                passwordChangeLinks.add(link);
            }
        }
        return passwordChangeLinks;
    }

    /**
     * Get a list of the password supported links
     * apply paging and sorting information
     * apply additional password information
     * create password link DTOs
     * @return listResult of password link DTOs
     * @throws GeneralException
     */
    public ListResult getManagePasswordLinkListResult() throws GeneralException {
        LinkService linkService = new LinkService(this.context);
        List<Map<String, Object>> passwordLinks = getManagePasswordLinks(linkService);
        
        //trim and sort password supported links
        addCalculatedSortColumns(passwordLinks);
        List<Map<String, Object>> links = trimAndSortResults(passwordLinks);
        List<PasswordLinkDTO> linkDTOList = new ArrayList<PasswordLinkDTO>();
        for (Map<String, Object> map : links) {
            Link link = this.getContext().getObjectById(Link.class, map.get("id").toString());
            List<PasswordChangeError> passwordChangeErrors = null;
            /* Extract properties from previous request */
            IdentityRequestItem previousRequest = linkService.getPreviousRequestItem(link, PASSWORDS_REQUEST);
            if(previousRequest != null) {
                // add action status
                map.put(ACTION_STATUS, previousRequest.getProvisioningState());
                map.put(APPROVAL_STATUS, previousRequest.getApprovalState());
                map.put(PASSWORD_CHANGE_DATE, previousRequest.getCreated());
                /* Collect errors */
                passwordChangeErrors = getPasswordChangeErrors(link, previousRequest);
            }
            // create linkDTO
            PasswordLinkDTO linkDTO = new PasswordLinkDTO(link, map, getColumns());
            // set identity id
            linkDTO.setIdentityId(getIdentity().getId());
            // check if current password is required
            linkDTO.setRequiresCurrentPassword(requiresCurrentPassword(link));
            // set approvalStatus
            linkDTO.setApprovalStatus((WorkItem.State) map.get(APPROVAL_STATUS));
            // add password policy information
            PasswordPolicy policy = getPasswordPolicy(link);
            if (policy == null) {
                linkDTO.setPasswordPolicy(null);
            }
            else {
                linkDTO.setPasswordPolicy(getConstraints(policy));
            }
            /* Add the errors to the dto */
            if(!Util.isEmpty(passwordChangeErrors)) {
                linkDTO.setPasswordChangeErrors(passwordChangeErrors);
            }
            // add linkDTO to list
            linkDTOList.add(linkDTO);
        }
        return new ListResult(linkDTOList, passwordLinks.size());
    }

    /**
     * Returns all links that support account change
     *
     * @return list of links
     * @throws GeneralException
     */
    private List<Map<String, Object>> getManageAccountLinks() throws GeneralException {
        QueryOptions ops = new QueryOptions();
        if (this.getIdentity() != null) {
            ops.add(Filter.eq("identity", getIdentity()));
        }

        // iiqetn-5445 - the queryProperties should contain the list of ColumnConfig
        // names from the UIConfig, not just those in LINK_PROPERTIES
        List<String> queryProperties = getQueryProperties();
        Iterator<Object[]> links = getContext().search(Link.class, ops, queryProperties);
        return Util.iteratorToMaps(links, Util.listToQuotedCsv(queryProperties, null, true));
    }
    
    /**
     * return configuration object
     * @return {Configuration} configuration
     * @throws GeneralException
     */
    private Configuration getConfig() throws GeneralException{
        return getContext().getConfiguration();
    }
    
    /**
     * Get a list of the account supported links
     * apply paging and sorting information
     * apply additional account information
     * create account link DTOs
     * @param supportsManageExistingAccounts boolean Whether the given link list should include account operations
     * @return listResult of account link DTOs
     * @throws GeneralException
     */
    public ListResult getManageAccountLinkListResult(boolean supportsManageExistingAccounts) throws GeneralException {
        LinkService linkService = new LinkService(this.context);
        List<Map<String, Object>> accountLinks = getManageAccountLinks();
        //trim and sort links
        List<AccountLinkDTO> linkDTOList = getAccountLinkDTOs(linkService, accountLinks, supportsManageExistingAccounts);
        return new ListResult(linkDTOList, accountLinks.size());
    }

    /**
     * Generates AccountLinkDTOs from projection query
     * @param linkService LinkService
     * @param accountLinks projection query to AccountLinkDTO-ify
     * @param supportsManageExistingAccounts boolean Whether the given link list should include account operations
     * @return List of AccountLinkDTOs
     * @throws GeneralException
     */
    private List<AccountLinkDTO> getAccountLinkDTOs(LinkService linkService, List<Map<String, Object>> accountLinks, boolean supportsManageExistingAccounts) throws GeneralException {
        addCalculatedSortColumns(accountLinks);
        List<Map<String, Object>> links = trimAndSortResults(accountLinks);
        MessageService messageService = new MessageService(this.getListServiceContext());
        List<AccountLinkDTO> linkDTOList = new ArrayList<AccountLinkDTO>();
        for (Map<String, Object> map : links) {
            Link link = this.getContext().getObjectById(Link.class, map.get("id").toString());
            /* Extract properties from previous request */
            IdentityRequestItem previousRequestItem = linkService.getPreviousRequestItem(link, ACCOUNTS_REQUEST, true);
            if(previousRequestItem != null) {
                // add action status from the provisioning state or the approval state
                map.put(ACTION_STATUS, previousRequestItem.getProvisioningState());
                map.put(APPROVAL_STATUS, previousRequestItem.getApprovalState());
                map.put(PREVIOUS_ACTION, previousRequestItem.getAttribute("operation"));
                if(!Util.isEmpty(previousRequestItem.getErrors())) {
                    List<String> localizedErrors = messageService.getLocalizedMessages(previousRequestItem.getErrors());
                    map.put(PREVIOUS_ERRORS, localizedErrors);
                }
            }
            // create linkDTO
            AccountLinkDTO linkDTO = new AccountLinkDTO(link, isSelfService(link), getConfig(), map, getColumns());

            // Don't send any available actions if the account can't be managed.
            if(!supportsManageExistingAccounts) {
                linkDTO.setAvailableOperations(new ArrayList<ProvisioningPlan.AccountRequest.Operation>());
            }

            // Set whether the link should be automatically refreshed when loaded.
            linkDTO.setAutoRefresh(linkService.isAutoRefreshEnabled(link.getApplication()));

            // add linkDTO to list
            linkDTOList.add(linkDTO);
        }
        return linkDTOList;
    }

    /**
     * Adds columns to the linkMap that need to be calculated
     * @param linkMapList List of Maps of link data
     */
    private void addCalculatedSortColumns(List<Map<String, Object>> linkMapList) throws GeneralException {
        List<Sorter> sorters = listServiceContext.getSorters(getColumns());
        if (!Util.isEmpty(sorters)) {
            for (Sorter sorter : sorters) {
                /* This sucks.  The values used to determine the status are in the attributes map.  We need to
                 * fetch the actual link.  And then the logic to decipher the meaning of the various attributes
                 * lives in LinkDTO, so create one of those to get the sortable value. */
                if(COL_LINK_STATUS.equals(sorter.getProperty())) {
                    for (Map<String, Object> linkMap : linkMapList) {
                        Link link = getContext().getObjectById(Link.class, (String) linkMap.get("id"));
                        LinkDTO linkDTO = new LinkDTO(link);
                        linkMap.put("status", linkDTO.getStatus());
                    }
                }
            }
        }
    }

    /**
     * Builds PasswordChangeErrors from the provided IdentityRequestItem
     * @param link The Link the reqest is for
     * @param previousRequest The request to get errors from
     * @return PasswordChangeErrors representing the IdentityRequestItem
     */
    private List<PasswordChangeError> getPasswordChangeErrors(Link link, IdentityRequestItem previousRequest) {
        List<PasswordChangeError> errors = new ArrayList<PasswordChangeError>();
        MessageService messageService = new MessageService(this.getListServiceContext());
        /* Get errors from request item */
        if(!Util.isEmpty(previousRequest.getErrors())) {
            List<String> messages = messageService.getLocalizedMessages(previousRequest.getErrors());
            LinkDTO linkDTO = new LinkDTO(link);
            linkDTO.setPasswordChangeDate(previousRequest.getCreated());
            errors.add(new PasswordChangeError(linkDTO, messages, false));
        }
        return errors;
    }

    /**
     * Retrieves all of the identity's links that support password change
     * @return list of links that support password change
     * @throws GeneralException
     */
     private List<Map<String, Object>> getManagePasswordLinks(LinkService linkService) throws GeneralException {

         Set<String> provisioningApplications = linkService.getProvisioningApplications(true);

         List<Map<String, Object>> linkResultList = new ArrayList<Map<String, Object>>();

         if (!Util.isEmpty(provisioningApplications)) {
             // iiqetn-5445 - the queryProperties should contain the list of ColumnConfig
             // names from the UIConfig, not just those in LINK_PROPERTIES
             List<String> queryProperties = getQueryProperties();

             // IIQSR-211: Each of the databases have a limit on the number of parameters that can be passed.
             // Seems like oracle may be the smallest number which is around 1000 parameters.  If the number of
             // IDs returned in the provisioningApplications is greater than the database specific limit on the query, then
             // an error will be thrown about too many parameters.  To get past this issue, we will batch the IDs
             // into search groups of 100 and then combine the results after each search is returned.
             int end = 0;
             int queryEach = 100;
             int listSize = provisioningApplications.size();

             List<String> provisioningApplicationsList = new ArrayList<String>(provisioningApplications);
             for (int start = 0; start < listSize; start += queryEach) {
                 // Ternary: end value is either the listSize or start incremented by queryEach, whichever is lower
                 end = ((start + queryEach) > listSize) ? listSize : start + queryEach;

                 QueryOptions ops = new QueryOptions();
                 if (this.getIdentity() != null) {
                     ops.add(Filter.eq("identity", getIdentity()));
                 }
                 ops.add(Filter.in("application.id", provisioningApplicationsList.subList(start, end)));

                 Iterator<Object[]> links = getContext().search(Link.class, ops, queryProperties);
                 List<Map<String, Object>> linkMapList =
                         Util.iteratorToMaps(links, Util.listToQuotedCsv(queryProperties,null, true));
                 linkResultList.addAll(linkMapList);
             }
         }

         return linkResultList;
    }

    /**
     * Return a list of links that require the user to enter their current password based on what the user has selected
     * from the ui
     * @param selectionModel The selectionModel that contains the list of links that the user has selected
     * @param identity The user that the links belong to
     * @return List<PasswordLinkDTO> the list of links that require the entry of the current password
     * @throws GeneralException
     */
    public List<PasswordLinkDTO> getCurrentPasswordLinks(SelectionModel selectionModel, Identity identity) throws GeneralException {
        List<Link> links = getManagePasswordLinks(selectionModel, identity);
        return this.getCurrentPasswordLinks(links);
    }

    /**
     * Return a sublist of links that require the current password from the provided list of links
     * @param links The list of links to search through
     * @return List<PasswordLinkDTO> the list of links that require the entry of the current password
     */
    public List<PasswordLinkDTO> getCurrentPasswordLinks(List<Link> links) {
        List<PasswordLinkDTO> requiresCurrentPasswordLinks = new ArrayList<PasswordLinkDTO>();
        for(Link link : links) {
            if(requiresCurrentPassword(link)) {
                PasswordLinkDTO linkDTO = new PasswordLinkDTO(link);
                requiresCurrentPasswordLinks.add(linkDTO);
            }
        }

        return requiresCurrentPasswordLinks;
    }


    /**
     * Refresh a single link and get the latest status of it
     * @param linkId The id of the link       *
     * @param supportsManageExistingAccounts boolean Whether the given link list should include account operations
     * @return AccountLinkDTO A representation of the current status of the link
     * @throws GeneralException
     */
    public AccountLinkDTO refreshLinkStatus(String linkId, boolean supportsManageExistingAccounts) throws GeneralException {

        List<AccountRefreshResult> results =
            this.refreshLinkStatuses(Collections.singletonList(linkId), supportsManageExistingAccounts);

        AccountRefreshResult result = null;

        if(!results.isEmpty()) {
            result = results.get(0);
        }

        if (result == null || result.isDeleted()) {
            Message msg = new Message(MessageKeys.LCM_MANAGE_ACCOUNTS_ERROR_LINK_NOT_EXISTS);
            throw new GeneralException(new Message(Message.Type.Error, msg.getLocalizedMessage(getListServiceContext().getLocale(),
                                                                                               getListServiceContext().getUserTimeZone())));
        }
        else if (result.getError() != null) {
            throw new GeneralException(result.getError());
        }

        return result.getAccount();
    }

    /**
     * Refresh the status of multiple links.
     *
     * @param linkIds  A non-null list of link IDs to refresh.
     * @param supportsManageExistingAccounts boolean W hether the given link list should include account operations
     *
     * @return A List of the AccountRefreshResults in the same order as the linkIds.
     *
     * @throws GeneralException  If there is an unexpected error.
     */
    public List<AccountRefreshResult> refreshLinkStatuses(List<String> linkIds, boolean supportsManageExistingAccounts)
        throws GeneralException {

        List<AccountRefreshResult> results = new ArrayList<AccountRefreshResult>();
        Identitizer identitizer = new Identitizer(getContext());

        for (String linkId : Util.iterate(linkIds)) {
            AccountRefreshResult result = new AccountRefreshResult(linkId);
            results.add(result);

            Link link = getContext().getObjectById(Link.class, linkId);
            if (null == link) {
                result.setDeleted(true);
            }
            else {
                Identity identity = link.getIdentity();
                Application app = link.getApplication();

                if (app != null) {
                    // Validate refresh is enabled for link application
                    if (app.supportsFeature(Application.Feature.NO_RANDOM_ACCESS)) {
                        String errorMsg = "Account refresh is not supported by features of application " + app.getName();
                        if (log.isErrorEnabled())
                            log.error(errorMsg);

                        Message msg = new Message(MessageKeys.LCM_MANAGE_ACCOUNTS_ERROR_OP_UNSUPPORTED_APP, app.getName());
                        result.setError(msg.getLocalizedMessage(getListServiceContext().getLocale(),
                                                                getListServiceContext().getUserTimeZone()));
                        continue;
                    }

                    identitizer.setSources(Arrays.asList(app));
                    identitizer.refreshStatus(identity, link);

                    /** If the link hasn't been deleted **/
                    if (link.getApplication() != null) {
                        getContext().saveObject(link);
                        getContext().commitTransaction();
                    }
                }
            }
        }

        // Once everything has been updated, do a single fetch to get the AccountLinkDTOs.
        fillInUpdatedAccountInfo(results, supportsManageExistingAccounts);

        return results;
    }

    /**
     * Fetch the AccountLinkDTOs and set them on the given results (for results which are not errors).
     *
     * @param results  The AccountRefreshResults for which to fetch accounts.
     * @param supportsManageExistingAccounts  Whether to include the operations on the results.
     */
    private void fillInUpdatedAccountInfo(List<AccountRefreshResult> results, boolean supportsManageExistingAccounts)
        throws GeneralException {

        // Store the results that we're going to refresh in a map for easier update later.
        Map<String,AccountRefreshResult> resultsById = new HashMap<String,AccountRefreshResult>();

        // Collect the IDs of the links we should fetch (no errors).
        List<String> linkIds = new ArrayList<String>();
        for (AccountRefreshResult result : results) {
            if (result.wasRefreshed()) {
                linkIds.add(result.getId());
                resultsById.put(result.getId(), result);
            }
        }

        if (!linkIds.isEmpty()) {
            // Grab the AccountLinkDTOs
            LinkService linkService = new LinkService(this.context);
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.in("id", linkIds));

            // iiqetn-5445 - the queryProperties should contain the list of ColumnConfig
            // names from the UIConfig, not just those in LINK_PROPERTIES
            List<String> queryProperties = getQueryProperties();
            Iterator<Object[]> links = getContext().search(Link.class, ops, queryProperties);
            List<Map<String, Object>> accountLinks = Util.iteratorToMaps(links, Util.listToQuotedCsv(queryProperties, null, true));
            List<AccountLinkDTO> accountLinkDTOs =
                getAccountLinkDTOs(linkService, accountLinks, supportsManageExistingAccounts);

            // Fill the details in on the results.
            for (AccountLinkDTO account : accountLinkDTOs) {
                AccountRefreshResult result = resultsById.get(account.getId());
                result.setAccount(account);
            }
        }
    }

    /**
     * Current password is required for change password process
     *
     * @param link link to retrieve current password setting
     * @return boolean
     */    
    private boolean requiresCurrentPassword(Link link) {
        return link.getApplication().supportsFeature(Feature.CURRENT_PASSWORD);
    }
    
    /**
     * 
     * @param link link to retrieve password policy from
     * @return password policy
     * @throws GeneralException
     */
    private PasswordPolicy getPasswordPolicy(Link link) throws GeneralException {
        PasswordPolice police = new PasswordPolice(getContext());
        return police.getEffectivePolicy(link);
    }
    
    /**
     * Convert password policy to readable constrains
     * @param policy password policy
     * @return list of readable constrains
     * @throws GeneralException
     */
    private List<String> getConstraints(PasswordPolicy policy) throws GeneralException {
        return policy.convertConstraints(getListServiceContext().getLocale(), getListServiceContext().getUserTimeZone());
    }
    
    /**
     * Check if given application supports password
     * @param link link of the given identity
     * @return boolean true if application supports password change
     * @throws GeneralException
     */
    private boolean supportsSetPassword(Link link) throws GeneralException {
        return supportsSetPassword(link.getApplication());
    }

    /**
     * Check if given application supports password
     * @param app application to check password support
     * @return boolean return true if application supports password change
     * @throws GeneralException
     */
    private boolean supportsSetPassword(Application app) throws GeneralException {
        PlanCompiler compiler = getPlanCompiler();
        IntegrationConfig cfg =
            compiler.getResourceManager(IntegrationConfig.OP_SET_PASSWORD, app.getName());
        return (null != cfg && cfg.supportedOperation(IntegrationConfig.OP_SET_PASSWORD));
    }

    private PlanCompiler getPlanCompiler() {
        if (null == this.compiler) {
            this.compiler = new PlanCompiler(this.context);
        }
        return this.compiler;
    }
    
    /**
     * Get identity object
     * @return identity return identity of given identity id
     */
    private Identity getIdentity() throws GeneralException{
        return getContext().getObjectById(Identity.class, identityId); 
    }
    
    /**
     * 
     * @return List<ColumnConfig> list of column configs
     * @throws GeneralException
     */
    private List<ColumnConfig> getColumns() throws GeneralException{
        return this.columnSelector.getColumns();
    }

    private boolean isSelfService(Link link) {
        return link.getIdentity().getName().equals(context.getUserName());
    }

    /**
     * Return the QueryOptions used to query.
     * @return List<String> list of strings to use as query options
     */
    protected List<String> getQueryProperties() throws GeneralException {
        // iiqetn-5445 - we also need to include any additional ColumnConfigs.
        // The hard coded list in LINK_PROPERTIES is contained in the UIConfig
        // by default.
        List<String> linkProperties = getProjectionColumns();

        // The only way linkProperties is empty at this point is if
        // the key was eliminated from the UIConfig.
        if (linkProperties.isEmpty()) {
            linkProperties = Arrays.asList(LINK_PROPERTIES.split(","));
        }

        return linkProperties;
    }

    /**
     * Gets the list of projection columns for a resource based on the passed
     * in string that represents the place in the UI config to get the colums
     */
    protected List<String> getProjectionColumns() throws GeneralException{
        List<String> projectionColumns = new ArrayList<String>();
        List<ColumnConfig> columns = getColumns();

        if(columns!=null) {
            for (ColumnConfig col : columns) {
                /** Only add non-calculated columns **/
                if(null != col.getProperty())
                    projectionColumns.add(col.getProperty());
            }
        }
        return projectionColumns;
    }

}
