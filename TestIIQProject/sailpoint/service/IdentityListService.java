/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Correlator;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SailPointObject;
import sailpoint.object.UIConfig;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityListBean;
import sailpoint.web.messages.MessageKeys;

/**
 * Service for managing lists of identities, with paging, etc.
 */
public class IdentityListService extends BaseListService<IdentityListServiceContext>{

    private static Log log = LogFactory.getLog(IdentityListService.class);

    /**
     * Create an identity list service.
     * @param context sailpoint context
     * @param serviceContext the identity list service context
     */
    public IdentityListService(SailPointContext context, IdentityListServiceContext serviceContext,
                               ListServiceColumnSelector columnSelector)
    {
        super(context, serviceContext, columnSelector);
    }

    /**
     * Modified from the similarly-named method in BaseRequestBean.
     * 
     * This is for the BMC IRM integration and ServiceNow Catalog Integration.
     *
     * The name argument is the identityName parameter from the request and is used for normal deep linking
     * rather than BMC/ServiceNow integration. It is plain text. 
     *
     * BMC IRM Integration:
     * From BMC launcher, the "in" parameter is expected to be base 64 encoded.
     *
     * ServiceNow Catalog Integration:
     * If ServiceNow knows identity name, then "in" parameter is specified. If ServiceNow doesn't know identity
     * name, then "requesteeNativeIdentiy" and "requesteeApp" parameter are specified.
     * If "requesteeNativeIdentiy" is specified, then we have to return the correlated identity id for
     * nativeIdentity(Account). Both of these are base 64 encoded parameters.
     *
     * @return id
     * @throws GeneralException if no id can be resolved from any of the parameters
     */
    public String resolveIdFromExternalParameter(String name,
                                                 String in,
                                                 String requesteeNativeIdentity,
                                                 String requesteeApp) throws GeneralException {
        String id = null;

        if ( Util.isNotNullOrEmpty(name) ) {
            id = getIdentityIdByName(name);
            if ( Util.isNullOrEmpty(id) ) {
                throw new ObjectNotFoundException(new Message(MessageKeys.WARN_IDENTITY_NAME_NOT_FOUND, name));
            }
        }

        else if ( Util.isNotNullOrEmpty(in) ) {
            in = Util.bytesToString(Base64.decode(in));
            id = getIdentityIdByName(in);
            if ( Util.isNullOrEmpty(id) ) {
                throw new ObjectNotFoundException(new Message(MessageKeys.WARN_IDENTITY_NAME_NOT_FOUND, in));
            }
        }

        else if ( Util.isNotNullOrEmpty(requesteeNativeIdentity) && Util.isNotNullOrEmpty(requesteeApp) ) {
            requesteeNativeIdentity = Util.bytesToString(Base64.decode(requesteeNativeIdentity));
            requesteeApp = Util.bytesToString(Base64.decode(requesteeApp));
            Application app = getContext().getObjectByName(Application.class, requesteeApp);
            if ( null == app ) {
                throw new ObjectNotFoundException(new Message(MessageKeys.WARN_REQUESTEE_APP_NOT_FOUND, requesteeApp));
            }
            Link uniqueLink = new Correlator(getContext()).findLinkByNativeIdentity(app, null, requesteeNativeIdentity);
            if ( null == uniqueLink ) {
                throw new ObjectNotFoundException(new Message(MessageKeys.WARN_REQUESTEE_NATIVE_IDENTITY_NOT_FOUND, requesteeNativeIdentity));
            }
            id = uniqueLink.getIdentity().getId();
            if ( Util.isNullOrEmpty(id) ) {
                throw new ObjectNotFoundException(new Message(MessageKeys.WARN_REQUESTEE_NATIVE_IDENTITY_NOT_FOUND, requesteeNativeIdentity));
            }
        }
        
        else {
            throw new ObjectNotFoundException(new Message(MessageKeys.WARN_COULD_NOT_RESOLVE_IDENTITY_ID));
        }

        return id;
    }

    /**
     * Return a list of all available identities
     * @return a list of all available identities
     * @throws GeneralException
     */
    public ListResult getAllIdentities() throws GeneralException {
        return getAllIdentities(/*isIdNameOnly*/ false);
    }

    /**
     * Return a list of names and ids of all available identities
     * @return
     * @throws GeneralException
     */
    public ListResult getAllIdNameIdentities() throws GeneralException {
        return getAllIdentities(/*isIdNameOnly*/ true);
    }

    /**
     * Return a list of all available identities
     * @param isIdNameOnly true is return only a list of names and ids, used for mobile view
     * @return a list of all available identities
     * @throws GeneralException
     */
    public ListResult getAllIdentities(boolean isIdNameOnly) throws GeneralException {
        IdentityListServiceContext listServiceContext = getListServiceContext();

        if (listServiceContext == null) {
            return getEmptyListResult();
        }

        QueryOptions identityOptions = new QueryOptions();

        List results;
        int total = 0;
        boolean filterSpecified = false;

        Filter nameFilter = listServiceContext.getNameFilter();
        if (nameFilter != null) {
            identityOptions.add(nameFilter);
            filterSpecified = true;
        }

        Filter idFilter = listServiceContext.getIdFilter();
        if (idFilter != null) {
            identityOptions.add(idFilter);
            filterSpecified = true;
        }

        Filter sessionFilter = listServiceContext.getSessionFilter();
        if (sessionFilter != null) {
            identityOptions.add(sessionFilter);
            filterSpecified = true;
        }

        // Always set distinct = true due to filters on links and other multi-valued items
        identityOptions.setDistinct(true);

        // Get the total count of matching objects for the result.
        total = getContext().countObjects(Identity.class, identityOptions);

        // If we're only returning ids ignore sorting and paging options, but respect mobileMaxSelectableUsers.
        if (isIdNameOnly) {
            int maxSelectableUsers = getContext().getConfiguration().getInt(Configuration.LCM_MOBILE_MAX_SELECTABLE_USERS);
            identityOptions.setFirstRow(0);
            identityOptions.setResultLimit(maxSelectableUsers);
            results = getIdentityIdNameResults(identityOptions);
        } else {
            handleOrdering(identityOptions);
            if (listServiceContext.getStart() > -1) {
                identityOptions.setFirstRow(listServiceContext.getStart());
            }
    
            if (listServiceContext.getLimit() > 0) {
                identityOptions.setResultLimit(listServiceContext.getLimit());
            }
    
            results = getResults(Identity.class, identityOptions);
        }
        return new ListResult(results, total);
    }

    /**
     * Return a grid compatible list of identities that the given identity can "manage".
     * This includes identities that are under the given identity in the manager
     * hierarchy or all identities (within scope) if the identity is an
     * IdentityAdministrator.
     *
     * @param identity the given identity
     * @param action an LCM config action
     *
     * @return A list of identities "managed" by the given identity, represented
     *         as maps.
     */
    public ListResult getManagedIdentitiesByAction(Identity identity, String action)
            throws GeneralException
    {
        return getManagedIdentities(identity, action, null, /*isIdNameOnly*/false);
    }

    public ListResult getManagedIdentitiesByQuicklink(Identity ident, String quicklinkName) throws GeneralException {
        return getManagedIdentities(ident, null, quicklinkName, false);
    }

    /**
     * Returns a full list of ids of identities that the given identity can "manage".
     * This includes identities that are under the given identity in the manager
     * hierarchy or all identities (within scope) if the identity is an
     * IdentityAdministrator.

     * @param identity the given identity
     * @param action an LCM config action
     *
     * @return A list of String ids of identities "managed" by the given identity
     */
    public ListResult getManagedIdentityIdNamesByAction(Identity identity, String action)
            throws GeneralException
    {
        return getManagedIdentities(identity, action, null, /*isIdNameOnly*/true);
    }

    public ListResult getManagedIdentityIdNamesByQuicklink(Identity identity, String quicklinkName)
            throws GeneralException
    {
        return getManagedIdentities(identity, null, quicklinkName, /*isIdNameOnly*/true);
    }

    /**
     * Return a grid compatible list of identities that the given identity can "manage".
     * This includes identities that are under the given identity in the manager
     * hierarchy or all identities (within scope) if the identity is an
     * IdentityAdministrator.
     *
     * In addition, this method will automatically prepend the current user onto the list if this is the first
     * page of results.
     *
     * If isIdNameOnly is specified true, this returns only the identity id/names in the result, up to the limit set
     * in SystemConfig option mobileMaxSelectableUsers, and paging params are ignored.
     *
     * @param identity the given identity
     * @param quicklinkName an LCM config action
     * @param isIdNameOnly if true, only ids/names are returned and paging is ignored.
     *
     * @return A list of identities "managed" by the given identity, represented
     *         as maps.
     */
    public ListResult getManagedIdentities(Identity identity, String action, String quicklinkName, boolean isIdNameOnly)
            throws GeneralException {
        IdentityListServiceContext listServiceContext = getListServiceContext();

        if (listServiceContext == null) {
            return getEmptyListResult();
        }

        LCMConfigService svc = new LCMConfigService(getContext(), listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
        svc.setQuickLinkName(quicklinkName);
        QuickLinkOptionsConfigService qloService = new QuickLinkOptionsConfigService(getContext(), listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
        QueryOptions identityOptions = svc.getRequestableIdentityOptions(
                identity, listServiceContext.getLoggedInUserDynamicScopeNames(), quicklinkName, action);

        /*
         Get the current identity.  If this is not null, it means the following:
            - The user is able to request for self based on their quicklinks
            - The user fits the specified filter criteria
            - This is the responsive ui where we want to add them to the front of the list
         */
        Map<String, Object> currentUser = queryCurrentUser(qloService, isIdNameOnly, quicklinkName, action);
        List results;
        int total = 0;

        /* If the user isn't allowed to request for others, the options will be null */
        if (identityOptions == null) {
            results = new ArrayList<Map<String, Object>>();
        } else {
            // If we are working with adding the current identity to the front of the list, we need to filter them out
            // Also, on the classic ui, we always want to remove the current user
            // Note, isRemoveCurrentUser is currently always true
            if (currentUser != null || listServiceContext.isRemoveCurrentUser()) {
                identityOptions.add(Filter.ne("id", listServiceContext.getLoggedInUser().getId()));
            }

            boolean filterSpecified = false;

            Filter nameFilter = listServiceContext.getNameFilter();
            if (nameFilter != null) {
                identityOptions.add(nameFilter);
                filterSpecified = true;
            }

            Filter idFilter = listServiceContext.getIdFilter();
            if (idFilter != null) {
                identityOptions.add(idFilter);
                filterSpecified = true;
            }

            Filter sessionFilter = listServiceContext.getSessionFilter();
            if (sessionFilter != null) {
                identityOptions.add(sessionFilter);
                filterSpecified = true;
            }

            if (isDisableInitialGridLoad(action, quicklinkName) && !filterSpecified) {
                return getEmptyListResult();
            } else {
                // Always set distinct = true due to filters on links and other multi-valued items
                identityOptions.setDistinct(true);

                // Get the total count of matching objects for the result.
                total = getContext().countObjects(Identity.class, identityOptions);

                // If we're only returning ids ignore sorting and paging options, but respect mobileMaxSelectableUsers.
                if (isIdNameOnly) {
                    int maxSelectableUsers = getContext().getConfiguration().getInt(Configuration.LCM_MOBILE_MAX_SELECTABLE_USERS);
                    identityOptions.setFirstRow(0);
                    identityOptions.setResultLimit(maxSelectableUsers);
                    results = getIdentityIdNameResults(identityOptions);
                } else {
                    handleOrdering(identityOptions);

                    if (listServiceContext.getStart() > -1) {
                        identityOptions.setFirstRow(calculateStart(currentUser));
                    }

                    if (listServiceContext.getLimit() > 0) {
                        identityOptions.setResultLimit(calculateLimit(currentUser));
                    }

                    results = getResults(Identity.class, identityOptions);
                }
            }
        }

        /* We are on the first page -- add the current user to the list of results and increase the total by 1 */
        if (currentUser != null) {
            if ((isIdNameOnly || listServiceContext.getStart() == 0)) {
                results.add(0, currentUser);
            }
            total++;
        }

        return new ListResult(results, total);

    }

    /**
     * Return a list of specific selected identities.
     *
     * Client will use this service by sending over a list of identity ids to get details for.
     *
     * @param selectedIdList List of IDs of selected identities
     * @param quickLinkAction The action being performed
     * @return A list of identities requested by the client specified by the ids in selectedIdList
     */
    public ListResult getSelectedIdentities(List selectedIdList, String quickLinkAction) throws GeneralException {
        if (selectedIdList == null || selectedIdList.isEmpty()) {
            return getEmptyListResult();
        }
        IdentityListServiceContext listServiceContext = getListServiceContext();
        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(getContext(), listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
        Map<String, Object> currentUser = queryCurrentSelectedUser(svc, selectedIdList, quickLinkAction);

        /* If the currentUser is not null, that means:
                - that we are on the responsive ui
                - the user can request for self
                - the user has been selected
                so we need to move them to the front of the list and remove them from the query options
         */
        if(currentUser!=null) {
            selectedIdList.remove(currentUser.get("id"));
        }
        List results = new ArrayList<Map<String,Object>>();
        int total = 0;

        /* In case the user has only chosen themselves */
        if(selectedIdList.size()>0) {
            QueryOptions ops = new QueryOptions(Filter.in("id", selectedIdList));

            total = getContext().countObjects(Identity.class, ops);

            if (listServiceContext.getStart() > -1) {
                ops.setFirstRow(calculateStart(currentUser));
            }

            if (listServiceContext.getLimit() > 0) {
                ops.setResultLimit(calculateLimit(currentUser));
            }

            handleOrdering(ops);

            results = getResults(Identity.class, ops);
        }

        /* We are on the first page -- add the current user to the list of results and increase the total by 1 */
        if (currentUser != null) {
            if (listServiceContext.getStart() == 0) {
                results.add(0, currentUser);
            }
            total++;
        }
        return new ListResult(results, total);
    }

    /**
     * Get a list of identities that match or don't match passed in role or entitlement and identity filter params.
     *
     * @param quicklinkName quicklink action or navigation rule the identity is trying to perform
     * @param roleOrEntitlement the role or entitlement that the identity should be matched against
     * @param showNonMatched true to show non matched identities false to show matched
     *
     * @return a list of identities matching role or entitlement and search params
     * @throws GeneralException
     */
    public ListResult getMatchingManagedIdentities(String quicklinkName,
                                                   SailPointObject roleOrEntitlement,
                                                   Boolean showNonMatched) throws GeneralException {

        IdentityListServiceContext listServiceContext = getListServiceContext();

        if (listServiceContext == null) {
            return getEmptyListResult();
        }

        boolean isRole = roleOrEntitlement instanceof Bundle;

        LCMConfigService svc = new LCMConfigService(getContext(), listServiceContext.getLocale(), listServiceContext.getUserTimeZone(), quicklinkName);

        QueryOptions identityOptions = svc.getRequestableIdentityOptions(listServiceContext.getLoggedInUser(),
                listServiceContext.getLoggedInUserDynamicScopeNames(), quicklinkName, null);

        // Null options means that the logged in user can't see anyone else.
        if (null == identityOptions) {
            return new ListResult(new ArrayList<Map<String,Object>>(), 0);
        }

        identityOptions.setFirstRow(listServiceContext.getStart());
        identityOptions.setResultLimit(listServiceContext.getLimit());

        Filter sessionFilter = listServiceContext.getSessionFilter();
        if (sessionFilter != null) {
            identityOptions.add(sessionFilter);
        }

        identityOptions.setDistinct(true);

        if (isRole) {
            String roleId = roleOrEntitlement.getId();
            if ((null != showNonMatched) && showNonMatched) {
                identityOptions.add(Filter.not(Filter.or(
                        Filter.subquery("id", Identity.class, "id", Filter.eq("assignedRoles.id", roleId)),
                        Filter.subquery("id", Identity.class, "id", Filter.eq("bundles.id", roleId)))));
            } else {
                identityOptions.add(Filter.or(Filter.eq("assignedRoles.id", roleId), Filter.eq("bundles.id", roleId)));
            }

            handleOrdering(identityOptions);
        }

        int total = 0;

        // For entitlements turn off limit
        if (!isRole) {
            identityOptions.setResultLimit(0);
            identityOptions.setFirstRow(0);
        }
        else {
            total = getContext().countObjects(Identity.class, identityOptions);
        }

        List<Map<String, Object>> results = getResults(Identity.class, identityOptions);

        if (!isRole) {
            filterByEntitlement(results, (ManagedAttribute)roleOrEntitlement, showNonMatched);
            total = results.size();
            results = trimAndSortResults(results);
        }

        return new ListResult(results, total);
    }

    /**
     * Remove any identities that do (or don't) have the given entitlement.
     */
    private void filterByEntitlement(List<Map<String,Object>> results,
                                     ManagedAttribute ma,
                                     Boolean showNonMatched)
            throws GeneralException {

        Iterator<Map<String,Object>> it = results.iterator();
        while (it.hasNext()) {
            Map<String,Object> row = it.next();
            String identityId = (String) row.get("id");

            boolean hasEntitlement = identityHasEntitlement(identityId, ma);

            if (hasEntitlement == showNonMatched) {
                it.remove();
            }
        }
    }

    /**
     * Check whether the identity has the given managed attribute.
     */
    private boolean identityHasEntitlement(String identityId, ManagedAttribute ma)
            throws GeneralException {

        Application app = ma.getApplication();
        if (null == app)
            throw new GeneralException("Managed Attribute has no Application");

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity.id", identityId));
        qo.add(Filter.eq("application.id", app.getId()));

        List<Link> links = getContext().getObjects(Link.class, qo);
        for (Link link : links) {
            if (!ManagedAttribute.Type.Permission.name().equals(ma.getType())) {
                Attributes<String,Object> attrs = link.getAttributes();
                if (null != attrs) {
                    String attr = ma.getAttribute();
                    String val = ma.getValue();

                    List<?> found = Util.asList(attrs.get(attr));
                    if ((null != found) && found.contains(val)) {
                        return true;
                    }
                }
            }
            else {
                throw new GeneralException("Cannot handle permissions yet.");
            }
        }

        return false;
    }

    /**
     * Does a search for all matching identities and returns a list of the id/name pairs.
     * @param identityOptions query options
     * @return list of id/names
     * @throws GeneralException
     */
    private List<Map<String, Object>> getIdentityIdNameResults(QueryOptions identityOptions) throws GeneralException {
        Set<Map<String, Object>> resultSet = new HashSet<Map<String, Object>>();

        // Search for identities, returning only the id.
        Iterator<Object[]> rows = getContext().search(Identity.class, identityOptions, Arrays.asList("id", "displayName"));

        // Create a set of the id strings (no dups, no nulls).
        if (rows != null) {
            while (rows.hasNext()) {
                Object[] row = rows.next();
                String id = (String) row[0];
                String displayName = (String) row[1];
                if (id != null) {
                    Map<String, Object> idMap = new HashMap<String, Object>();
                    idMap.put("id", id);
                    idMap.put("displayName", displayName);
                    resultSet.add(idMap);
                }
            }
        }

        return new ArrayList<Map<String, Object>>(resultSet);
    }


    /**
     * Creates a name filter for the specified name. Called by the client resource since it may need to set it
     * in the session before getting results.
     * @param name the filter name
     * @return the name filter
     * @throws GeneralException
     */
    static public Filter createNameFilter(String name) throws GeneralException {
        Filter nameFilter = null;

        if ((name != null) && (!name.equals(""))) {
            List<Filter> filters = new ArrayList<Filter>();
            List<String> searchAttributes = getSearchAttributes();
            for (String attr : searchAttributes) {

                /** Need to protect ourselves from non-string properties **/
                Class clazz = getIdentitySearchAttributeClazz(attr);
                
                // If clazz is null or not a String class then don't add it to the name filter.
                // clazz will be null if we didn't find a property descriptor while processing 
                // the nested property names. 
                if (clazz == null) {
                    log.error("Unable to locate property descriptor class for property " + attr + 
                            ". Identity search attribute " + attr + " will not be added to name filter.");
                    continue;
                }
                else if (!clazz.equals(String.class)) {
                    log.error("Property descriptor class for property " + attr + 
                            " is not of type String. Identity search attribute " + attr + 
                            " will not be added to name filter.");
                    continue;
                }

                Filter attributeFilter = IdentityListBean.isMultiProperty(attr) ?
                        IdentityListBean.getMultiFilter(attr, name) : IdentityListBean.getFilter(attr, name);

                filters.add(attributeFilter);
            }

            if (Util.size(filters) == 0) {
                nameFilter = null;
            } else if (Util.size(filters) == 1) {
                nameFilter = filters.get(0);
            } else {
                nameFilter = Filter.or(filters);
            }
        }

        return nameFilter;
    }

    /**
     * Given the search attribute name, get the class of the property.
     */
    static private Class getIdentitySearchAttributeClazz(String attribute) {
        if (Util.isNullOrEmpty(attribute)) {
            return null;
        }
        
        // bug 25084 - PropertyUtils.getPropertyDescriptor(class, propertyName) returns
        // a null PropertyDescriptor when property values are null. This was causing a
        // bug when looking for the property descriptor for nested properties like
        // manager.firstname because the value of the manager property was null. The
        // workaround here is to iterate through the property descriptors for each class
        // defined by the nested properties. We can add the search attribute to the name
        // filter when we find the class associated with the last property descriptor if
        // the class is of type String.

        // Split the search attribute into it's nested properties
        final String[] attrPath = attribute.split("\\.");

        // start with the Identity class
        Class<?> clazz = Identity.class;

        // Iterate through each nested property until we find the class associated with
        // that property descriptor. If the clazz is null then we didn't find the property 
        // descriptor in the previous class
        for (int i = 0; i < attrPath.length && clazz != null; i++)
        {
            String attrName = attrPath[i];

            PropertyDescriptor[] propDescs = PropertyUtils.getPropertyDescriptors(clazz);
            ObjectConfig objectConfig = ObjectConfig.getObjectConfig(clazz);
            clazz = null;

            // find the next property descriptor class
            for (PropertyDescriptor propDesc : propDescs) {
                if (propDesc.getName().equals(attrName)) {
                    clazz = propDesc.getPropertyType();
                    break;
                }
            }

            // if not a property on the class, look for extended attribute 
            if (clazz == null && objectConfig != null) {
                ObjectAttribute objectAttribute = objectConfig.getObjectAttribute(attrName);
                if (objectAttribute != null && objectAttribute.getPropertyType() != null) {
                    clazz = objectAttribute.getPropertyType().getTypeClazz();
                }
            }
        }
        
        return clazz;
    }

    /**
     * Return the list of configured identity attributes we can search on.
     */
    static private List<String> getSearchAttributes()
            throws GeneralException {

        List<String> searchAttributes = new ArrayList<String>();

        UIConfig uiConfig = UIConfig.getUIConfig();
        if (uiConfig != null) {
            List<String> attrs = uiConfig.getIdentitySearchAttributesList();
            if (attrs != null) {
                searchAttributes.addAll(attrs);
            }
        }

        return searchAttributes;
    }

    private ListResult getEmptyListResult() {
        return new ListResult(new ArrayList<Map<String, Object>>(), 0);
    }

    /**
     * Used to tweak the sql query limits to allow us to prepend the list with
     * the current identity
     *
     * In the case where we are on the first page of the results (start <= 0)
     * we want to decrease the actual start by one so that we can capture the
     * single result that we missed when we decreased the limit by one to add
     * the current identity to the list
     *
     * @param currentIdentity If this is not null, it means that the current identity fits the search
     *                        criteria and will be returned in the result set so we need to ajust
     *                        the query start/limit parameters
     * @return int The int that represents the index of the first row in the query
     */
    protected int calculateStart(Map<String, Object> currentIdentity) {
        IdentityListServiceContext listServiceContext = getListServiceContext();

        int start = listServiceContext.getStart();

        start = (currentIdentity == null || start <= 0) ? start : (start - 1);

        return start;
    }

    /**
     * Used to tweak the sql query limits to allow us to prepend the list with
     * the current identity
     *
     * When we are on the first page, we want to decrease the number of results
     * that we return by one so we can prepend the current identity onto the list
     *
     * @param currentIdentity If this is not null, it means that the current identity fits the search
     *                        criteria and will be returned in the result set so we need to ajust
     *                        the query start/limit parameters
     * @return int The number of results we want to return on this page of the query
     */
    protected int calculateLimit(Map<String, Object> currentIdentity) {
        IdentityListServiceContext listServiceContext = getListServiceContext();

        int limit = listServiceContext.getLimit();

        if (currentIdentity == null) {
            return limit;
        }
        limit = listServiceContext.getStart() <= 0 ? (limit - 1) : limit;

        return limit;
    }

    /**
     * Queries to see if the current user fits the chosen search criteria.   Adds "id == xxxx" to the
     * current search criteria to force the search to only return the identity logged in.
     * @param svc The QuickLinkOptionsConfigService so we can look at whether the user can request for self
     * @param isIdNameOnly Whether we want to return full objects or just ids and names
     * @param quickLinkName The name of the quicklink name has precedence over action
     * @param quickLinkAction The action to perform
     * @return either null if the user is not found or a Map of properties (according to the column config) for that user
     * @throws GeneralException
     */
    protected Map<String, Object> queryCurrentUser(QuickLinkOptionsConfigService svc, boolean isIdNameOnly, String quickLinkName, String quickLinkAction)
            throws GeneralException {

        IdentityListServiceContext listServiceContext = getListServiceContext();
        Identity currentUser = listServiceContext.getLoggedInUser();

        // If the user can request for self and this is the responsive ui, build up the query options based on the filter
        if (currentUser != null && svc.canRequestForSelf(currentUser, quickLinkName, quickLinkAction) && listServiceContext.isCurrentUserFirst()) {
            QueryOptions qo = new QueryOptions();

            Filter nameFilter = listServiceContext.getNameFilter();
            if (nameFilter != null) {
                qo.add(nameFilter);
            }

            Filter idFilter = listServiceContext.getIdFilter();
            if (idFilter != null) {
                qo.add(idFilter);
            }

            Filter sessionFilter = listServiceContext.getSessionFilter();
            if (sessionFilter != null) {
                qo.add(sessionFilter);
            }
            qo.setFirstRow(0);
            qo.setResultLimit(1);
            qo.add(Filter.eq("id", currentUser.getId()));

            List<Map<String, Object>> results = null;
            if (!isIdNameOnly) {
                results = getResults(Identity.class, qo);
            } else {
                results = getIdentityIdNameResults(qo);
            }

            if (results != null && results.size() > 0) {
                return results.get(0);
            }
        }

        return null;
    }

    /**
     * If the current user is in the list of results, return them so we know we need to adjust the query parameters
     * in the query that returns all users.
     *
     * @param svc The QuickLinkOptionsConfigService so we can look at whether the user can request for self
     * @param selectedIds The currently selected ids
     * @param quickLinkAction The action being performed
     * @throws GeneralException
     */
    protected Map<String, Object> queryCurrentSelectedUser(QuickLinkOptionsConfigService svc, List<String> selectedIds, String quickLinkAction) throws GeneralException {
        IdentityListServiceContext listServiceContext = getListServiceContext();
        Identity loggedInUser = listServiceContext.getLoggedInUser();

        if (loggedInUser != null && svc.canRequestForSelf(loggedInUser, null, quickLinkAction) && listServiceContext.isCurrentUserFirst()) {
            if(selectedIds.contains(loggedInUser.getId())) {

                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("id", loggedInUser.getId()));
                List<Map<String, Object>> results = getResults(Identity.class, qo);
                if (results != null && results.size() > 0) {
                    return results.get(0);
                }
            }
        }
        return null;
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
     * Return whether an initial load of the Grid Data should be done.
     * @param action - The quickLink action being performed
     * @param quicklinkName - The name of the QuickLink
     * @return - boolean value indicating if the load is disabled.
     */
    private boolean isDisableInitialGridLoad(String action, String quicklinkName) throws GeneralException {
        // Check if we need to return blank results
        if (Util.nullSafeEq(action,QuickLink.LCM_ACTION_REQUEST_ACCESS) || Util.nullSafeEq(quicklinkName,QuickLink.QUICKLINK_REQUEST_ACCESS)) {
            return(getContext().getConfiguration().getBoolean("disableInitialAccessRequestGridLoad", false));
        } else if (Util.nullSafeEq(action,QuickLink.LCM_ACTION_MANAGE_ACCOUNTS) || Util.nullSafeEq(quicklinkName,QuickLink.QUICKLINK_MANAGE_ACCOUNTS)) {
            return(getContext().getConfiguration().getBoolean("disableInitialManageAccountsGridLoad", false));
        } else if (Util.nullSafeEq(action,QuickLink.LCM_ACTION_MANAGE_PASSWORDS) || Util.nullSafeEq(quicklinkName,QuickLink.QUICKLINK_MANAGE_PASSWORDS)) {
            return(getContext().getConfiguration().getBoolean("disableInitialManagePasswordsGridLoad", false));
        } else if (Util.nullSafeEq(action,QuickLink.LCM_ACTION_EDIT_IDENTITY)) {
            return(getContext().getConfiguration().getBoolean("disableInitialManageAttributesGridLoad", false));
        } else if (Util.nullSafeEq(action,QuickLink.LCM_ACTION_VIEW_IDENTITY)) {
            return(getContext().getConfiguration().getBoolean("disableInitialViewIdentityGridLoad", false));
        } else {
            return(getContext().getConfiguration().getBoolean("disableInitialChooseIdentitiesGridLoad", false));
        }
    }

    @Override
    protected Object convertColumn(Map.Entry<String, Object> entry, ColumnConfig config, Map<String, Object> rawObject) throws GeneralException {

        // Convert type to display name,it is not a Localizable enum so it is not handled automagically
        Object value = entry.getValue();
        if (value != null && Identity.ATT_TYPE.equals(config.getProperty())) {
            IdentityTypeDefinition typeDefinition = Identity.getObjectConfig().getIdentityType((String)value);
            if (typeDefinition != null) {
                return localize(typeDefinition.getDisplayableName());
            }
        }

        return super.convertColumn(entry, config, rawObject);
    }

}
