package sailpoint.service.quicklink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * A service class that helps accessing Quick Link Options information.
 * This class was made using pieces of the class LCMConfigService. This is the result of a seperation of concerns
 * with legacy LCMConfigService and placing QuickLinkOptions specific methods in here
 * @author brian.li
 *
 */
public class QuickLinkOptionsConfigService {
    
    private SailPointContext context;
    private Locale locale;
    private TimeZone timezone;

    public static final String DYNAMIC_SCOPE_QUERY_PROPERTY = "dynamicScope";
    public static final String DYNAMIC_SCOPE_NAME_QUERY_PROPERTY = "dynamicScope.name";
    protected static final String QUICK_LINK_HIDDEN_QUERY_PROPERTY = "quickLink.hidden";
    public static final String QUICK_LINK_NAME_QUERY_PROPERTY = "quickLink.name";
    public static final String QUICK_LINK_ACTION_QUERY_PROPERTY = "quickLink.action";
    protected static final String QUICK_LINK_OPTIONS_ATTRIBUTES_QUERY_PROPERTY = "options";
    protected static final String ALLOW_SELF_QUERY_PROPERTY = "allowSelf";
    protected static final String ALLOW_OTHER_QUERY_PROPERTY = "allowOther";
    protected static final String ALLOW_BULK_QUERY_PROPERTY = "allowBulk";

    // Cached copy of matching DynamicScope names keeyed by Identity ID
    private Map<String, List<String>> dynamicScopeMatches;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public QuickLinkOptionsConfigService(SailPointContext context) {
        this(context, null, null);
    }

    public QuickLinkOptionsConfigService(SailPointContext context, Locale locale, TimeZone tz) {
        this.context = context;
        this.locale = locale;
        this.timezone = tz;
        dynamicScopeMatches = new HashMap<String, List<String>>();
    }
    
    /**
     * Checks to see if the specified control name exists within the QuickLinkOptions' option map
     * @param user User to check against.
     * @param quickLinkAction The quick link action that the quick link does. Should now query based on action instead of name since the user
     * can modify it to name it whatever they want
     * @param ctrlName Name of the request control to look for within the options map.
     * @param selfService Will use the self service dynamic scope to find the quick link option.
     * @return boolean Whether or not the option is enabled.
     * @throws GeneralException
     */
    public boolean isRequestControlOptionEnabled(Identity user, String quickLinkName, String quickLinkAction, String ctrlName, boolean selfService)
                    throws GeneralException {

        List<String> dynamicScopeNames = getDynamicScopeMatches(user);

        return isRequestControlOptionEnabled(user, dynamicScopeNames, quickLinkName, quickLinkAction, ctrlName, selfService);
    }

    private List<String> getDynamicScopeMatches(Identity user) throws GeneralException {
        final String identityId = user.getId();
        List<String> dynamicScopeNames = dynamicScopeMatches.get(identityId);
        if (dynamicScopeNames == null) {
            DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
            dynamicScopeNames = matcher.getMatches(user);
            dynamicScopeMatches.put(identityId, dynamicScopeNames);
        }
        return dynamicScopeNames;
    }
    
    /**
     * 
     * @param dynamicScopeNames If the dynamic scope name of the quick link option is provided, this method can be used for quicker lookup.
     * @param quickLinkAction The quick link action that the quick link does. Should now query based on action instead of name since the user
     * can modify it to name it whatever they want
     * @param ctrlName Name of the request control to look for within the options map.
     * @param selfService Will use the self service dynamic scope to find the quick link option.
     * @return boolean Whether or not the option is enabled.
     * @throws GeneralException
     */
    public boolean isRequestControlOptionEnabled(Identity user, List<String> dynamicScopeNames,
            String quickLinkName, String quickLinkAction, String ctrlName, boolean selfService)
            throws GeneralException {

        if (dynamicScopeNames == null) {
            dynamicScopeNames = getDynamicScopeMatches(user);
        }

        if ((Util.isNotNullOrEmpty(quickLinkAction) || Util.isNotNullOrEmpty(quickLinkName)) && !Util.isEmpty(dynamicScopeNames)) {
            QueryOptions qo = getQueryOptions(dynamicScopeNames, quickLinkName, quickLinkAction, selfService, !selfService, false);
            qo.addFilter(Filter.notnull(QUICK_LINK_OPTIONS_ATTRIBUTES_QUERY_PROPERTY));

            List<QuickLinkOptions> qlOptions = context.getObjects(QuickLinkOptions.class, qo);
            //at this point the qlOptions returned have the matching quick link AND dynamic scope
            for (QuickLinkOptions option : Util.safeIterable(qlOptions)) {
                Attributes<String, Object> options = option.getOptions();
                if (options!= null && options.containsKey(ctrlName)) {
                    if (Util.otob(options.get(ctrlName))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determine if the request Control option is enabled for a given requestee. This will be true if the requestee
     * is part of the request population authority of a given dynamic scope, and the request control is enabled for that
     * dynamic scope
     * @param user - Identity making the request
     * @param quickLinkName - Name of the quicklink
     * @param quickLinkAction - Quick Link Action. If no quickLinkName supplied, we will use all quicklinks of this action
     * @param ctrlName - Request control in question
     * @param selfService - True if self-service request
     * @param requestee - Identity being requested for. If none supplied, only look to ensure request scope enabled
     * @return True if the RequestControl option is enabled for the requestee
     * @throws GeneralException
     */
    public boolean isRequestControlOptionEnabledForRequestee(Identity user, String quickLinkName, String quickLinkAction,
                                                             String ctrlName, boolean selfService, Identity requestee)
            throws GeneralException {
        if ((Util.isNotNullOrEmpty(quickLinkAction) || Util.isNotNullOrEmpty(quickLinkName))) {
            List<String> dynamicScopeNames = getDynamicScopeMatches(user);
            if (Util.isEmpty(dynamicScopeNames)) {
                // Not in any dynamic scopes, cant do nuthin.
                return false;
            }

            QueryOptions qo = getQueryOptions(dynamicScopeNames, quickLinkName, quickLinkAction, selfService, !selfService, false);
            qo.addFilter(Filter.notnull(QUICK_LINK_OPTIONS_ATTRIBUTES_QUERY_PROPERTY));

            DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
            List<QuickLinkOptions> qlOptions = context.getObjects(QuickLinkOptions.class, qo);
            //at this point the qlOptions returned have the matching quick link
            for (QuickLinkOptions option : Util.safeIterable(qlOptions)) {
                Attributes<String, Object> options = option.getOptions();
                if (options!= null && options.containsKey(ctrlName)) {
                    if (Util.otob(options.get(ctrlName))) {
                        //Control Option Enabled. See if requestee in the dynamic scope
                        if (requestee != null && !selfService) {
                            DynamicScope scope = option.getDynamicScope();
                            if (!matcher.isMember(user, requestee, scope.getPopulationRequestAuthority())) {
                                continue;
                            } else {
                                //Requestee member of pop req auth, return true
                                return true;
                            }
                        } else {
                            //No Requestee specified, assume enabled
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns whether the user can make requests for themselves
     * @param user The identity in question
     * @param quickLinkName the name of the quicklink
     * @param lcmAction the lcm action
     * @return true if the user can perform the action on self
     * @throws GeneralException
     */
    public boolean canRequestForSelf(Identity user, String quickLinkName, String lcmAction) throws GeneralException {
        return isQuickLinkActionEnabled(user, lcmAction, quickLinkName, null, true, false);
    }

    /**
     * Return whether a user that is a member of the given dynamic scopes is allowed to make bulk
     * requests for the given action.  This will return false if the user does not have any access
     * to the action or if they can only make self-service or single-other requests.
     *
     * @param  dynamicScopes  The names of the dynamic scopes the user is a member of.
     * @param  action  The quick link action to check.
     *
     * @return True if a user with the given dynamic scopes can make bulk requests for the given
     *     action, or false otherwise.
     */
    public boolean isBulkEnabled(List<String> dynamicScopes, String action, String quickLinkName)
        throws GeneralException {

        // No scopes == no access.
        if (Util.isEmpty(dynamicScopes)) {
            return false;
        }

        // Get all "for others" quick link options - this includes single others and bulk.
        QueryOptions qo = this.getQueryOptions(dynamicScopes, quickLinkName, action, false, true, false);
        List<QuickLinkOptions> optionsList = this.context.getObjects(QuickLinkOptions.class, qo);
        for (QuickLinkOptions options : optionsList) {
            if (options.isAllowBulk()) {
                return true;
            }
        }
        return false;
    }

    public boolean isAllowOthersRequestEnabled(List<String> dynamicScopes, String action, String quickLinkName)
        throws GeneralException {
        if (Util.isEmpty(dynamicScopes)) {
            return false;
        }
        // Get all "for others" quick link options - this includes single others and bulk.
        QueryOptions qo = this.getQueryOptions(dynamicScopes, quickLinkName, action, false, true, false);
        return this.context.countObjects(QuickLinkOptions.class, qo) > 0;
    }

    public boolean isAllowSelfRequestEnabled(List<String> dynamicScopes, String action, String quickLinkName)
        throws GeneralException {
        if (Util.isEmpty(dynamicScopes)) {
            return false;
        }
        // Get all "for others" quick link options - this includes single others and bulk.
        QueryOptions qo = this.getQueryOptions(dynamicScopes, quickLinkName, action, true, false, false);
        return this.context.countObjects(QuickLinkOptions.class, qo) > 0;
    }

    /**
     * Checks to see that a quick link option relationship exists for the currentUser and quickLinkName
     * @param currentUser identity to match potential dynamic scopes against
     * @param quickLinkName name of the quick link to match against
     * @param isSelfService will check for quick link options with the allowSelf property set to true
     * @return does the currentUser match a dynamic scope and quick link
     * @throws GeneralException
     */
    public boolean isQuickLinkEnabled(Identity currentUser, String quickLinkName, boolean isSelfService)
            throws GeneralException {
        if (currentUser == null || Util.isNullOrEmpty(quickLinkName)) {
            return false;
        }

        List<String> dsMatchList = getDynamicScopeMatches(currentUser);
        QueryOptions qo = getQueryOptions(dsMatchList, quickLinkName, null, isSelfService, !isSelfService, true);
                
        return context.countObjects(QuickLinkOptions.class, qo) > 0;
    }
    
    /**
     * Similar to {@link #isQuickLinkEnabled(Identity, String, boolean)} but uses the javax faces action 
     * instead of the quick link name to determine if the quick link is enabled for a particular user. Useful
     * in authorizers when validating access from the rest and web layers.
     * @param currentUser identity to match potential dynamic scopes against
     * @param lcmAction javax faces action that is stored in the {@link QuickLink#getAction()} property
     * @param lcmRequestControl additional request control that may enable/disable feature of the quicklink
     * @param isSelfService will check for quick link options with the allowSelf property set to true
     * @param includeOthers will check of quick link options with the allowOther or allowBulk property set to true
     * @return does the currentUser match a dynamic scope and quick link
     * @throws GeneralException
     */
    public boolean isQuickLinkActionEnabled(Identity currentUser, String lcmAction, String quickLinkName,
                                            String lcmRequestControl, boolean isSelfService, boolean includeOthers)
            throws GeneralException {
        if (currentUser == null || (Util.isNullOrEmpty(lcmAction) && Util.isNullOrEmpty(quickLinkName))) {
            return false;
        }
        
        boolean isAuthorized = false;
        List<String> dsMatchList = getDynamicScopeMatches(currentUser);
        QueryOptions qo = getQueryOptions(dsMatchList, quickLinkName, lcmAction, isSelfService, includeOthers, true);
        List<QuickLinkOptions> qlos = context.getObjects(QuickLinkOptions.class, qo);
        for (QuickLinkOptions quickLinkOptions : Util.safeIterable(qlos)) {
            QuickLink link = quickLinkOptions.getQuickLink();
            if (link != null && (Util.nullSafeEq(lcmAction, link.getAction()) || Util.nullSafeEq(quickLinkName, link.getName()))) {
                // actions match, so far so good
                if (Util.isNotNullOrEmpty(lcmRequestControl)) {
                    if (quickLinkOptions.getBooleanOption(lcmRequestControl)) {
                        isAuthorized = true;
                        break;
                    }
                } else {
                    isAuthorized = true;
                    break;
                }
            }
        }
        
        return isAuthorized;
    }
    
    /**
     * Returns a list of quick link options available for given dynamic scopes
     * @param dynamicScopeNames list of dynamic scope names to check against
     * @param includeHidden flag to include quick link options associated with a hidden quick link
     * @return list of quick link options
     * @throws GeneralException
     */
    public List<QuickLinkOptions> getQuickLinkOptions(List<String> dynamicScopeNames, boolean includeHidden) 
        throws GeneralException {
        return getQuickLinkOptions(dynamicScopeNames, true, true, includeHidden);
    }
    
    /**
     * Returns a list of quick link options available for given dynamic scopes based on some flags.
     * @param dynamicScopeNames list of dynamic scope names to check against
     * @param includeSelf include quick link options that have the allowSelf flag to true
     * @param includeOthers include quick link options that have the allowOther or allowBulk flag to true
     * @param includeHidden flag to include quick link options associated with a hidden quick link 
     * @return list of quick link options
     * @throws GeneralException
     */
    public List<QuickLinkOptions> getQuickLinkOptions(List<String> dynamicScopeNames, boolean includeSelf, 
            boolean includeOthers, boolean includeHidden) throws GeneralException {
        
        // return an empty list if you passed me garbage
        if (Util.isEmpty(dynamicScopeNames)) {
            return new ArrayList<QuickLinkOptions>();
        }
        
        QueryOptions qo = getQueryOptions(dynamicScopeNames, null, null, includeSelf, includeOthers, includeHidden);
        
        return context.getObjects(QuickLinkOptions.class, qo);
    }
    
    /**
     * Helper method to toggle a request control option for a QuickLinkOptions object given a quick link name and dynamic scope name
     * @param context Sailpoint Context to use
     * @param dynamicScopeName name of the Dynamic Scope to look up the Quick Link Options
     * @param quickLinkName name of the Quick Link to look up the Quick Link Options
     * @param control Control to toggle 
     * @param enabled boolean value to set the control to
     * @return the original value of the boolean before toggling, or null if the control was not found
     * @throws GeneralException
     */
    public Boolean toggleRequestControlOption(SailPointContext context, String dynamicScopeName, String quickLinkName, String control, boolean enabled)
            throws GeneralException {
        QueryOptions qo = new QueryOptions();
        Filter qlNameFilter = Filter.eq(QuickLinkOptionsConfigService.QUICK_LINK_NAME_QUERY_PROPERTY, quickLinkName);
        Filter dsNameFilter = Filter.eq(QuickLinkOptionsConfigService.DYNAMIC_SCOPE_NAME_QUERY_PROPERTY, dynamicScopeName);
        qo.add(Filter.and(qlNameFilter, dsNameFilter));
        
        List<QuickLinkOptions> qlOptions = context.getObjects(QuickLinkOptions.class, qo);
        //at this point the qlOptions returned have the matching quick link AND dynamic scope
        for (QuickLinkOptions option : Util.safeIterable(qlOptions)) {
            Attributes<String, Object> options = option.getOptions();
            if (options!= null && options.containsKey(control)) {
                Object oldValue = options.get(control);
                options.put(control, enabled);
                context.saveObject(option);
                context.commitTransaction();
                return Util.otob(oldValue);
            }
        }
        return null;
    }

    /**
     * Delete the quick link options that matches the relationship of dynamic scope and quick link name. The object
     * returned is a copy of the object deleted from the database, useful when restoring the quick link option in
     * unit tests.
     * @param dynamicScopeName used in finding the quick link option
     * @param quickLinkName used in finding the quick link option
     * @return a copy of the object deleted
     * @throws GeneralException
     */
    public QuickLinkOptions delete(String dynamicScopeName, String quickLinkName) throws GeneralException {
        QuickLinkOptions qlo = null;
        if (Util.isNullOrEmpty(dynamicScopeName) || Util.isNullOrEmpty(quickLinkName)) {
            return null;
        }
        
        QueryOptions qo = getQueryOptions(Arrays.asList(dynamicScopeName), quickLinkName, null, true, true, true);
        List<QuickLinkOptions> qlOptions = context.getObjects(QuickLinkOptions.class, qo);
        if (!Util.isEmpty(qlOptions)) {
            qlo = new QuickLinkOptions(qlOptions.get(0));
            Terminator term = new Terminator(context);
            term.deleteObjects(QuickLinkOptions.class, qo);
        }
        
        return qlo;
    }
    
    /**
     * Helper method used by LCMConfigService to return the list of all QuickLinkOptions for a given action string. 
     * @param quickLinkAction javax faces action that is stored in the {@link QuickLink#getAction()} property
     * @param allowOthers returns QuickLinkOptions that are allowOther or allowBulk, if false will return allowSelf
     * @return list of QuickLinkOptions that are associated to a quicklink with the given action
     * @throws GeneralException
     */
    public List<QuickLinkOptions> getQuickLinkOptionsByAction(String quickLinkAction, boolean allowOthers) 
            throws GeneralException {
        if (Util.isNullOrEmpty(quickLinkAction)) {
            return new ArrayList<QuickLinkOptions>();
        }
        // Call with allowEmptyDynamicScopes set to true to get all of them unlimited by scope
        QueryOptions qo = getQueryOptions(null, null, quickLinkAction, !allowOthers, allowOthers, true, true);
        return context.getObjects(QuickLinkOptions.class, qo);
    }

    /**
     * Helper method to create query options used for searching, iterating or deleting quick link options.
     * @param dynamicScopeNames list of names to include. Should be non-empty. If empty, no options will be matched.
     * @param quickLinkName name of quick link to query upon. Will skip in the filter if passed in null
     * @param quickLinkAction action of the quick link. Will skip in the filter if passed in null
     * @param includeSelf include quick link options with the allowSelf property set to true
     * @param includeOthers include quick link options with the allowOther or allowBulk property set to true
     * @param includeHidden include quick links with the hidden property set to true
     * @return query options generated based on the parameters passed in
     */
    public QueryOptions getQueryOptions(List<String> dynamicScopeNames, String quickLinkName, String quickLinkAction, 
            boolean includeSelf, boolean includeOthers, boolean includeHidden) {
         return getQueryOptions(dynamicScopeNames, quickLinkName, quickLinkAction, includeSelf, includeOthers, includeHidden, false);
    }

    /**
     * Helper method to create query options used for searching, iterating or deleting quick link options.
     * @param dynamicScopeNames list of names to include. Should be non-empty. If empty, no options will be matched. To override this behavior,
     *                          set the allowEmptyDynamicScopes flag to true.
     * @param quickLinkName name of quick link to query upon. Will skip in the filter if passed in null
     * @param quickLinkAction action of the quick link. Will skip in the filter if passed in null
     * @param includeSelf include quick link options with the allowSelf property set to true
     * @param includeOthers include quick link options with the allowOther or allowBulk property set to true
     * @param includeHidden include quick links with the hidden property set to true
     * @return query options generated based on the parameters passed in
     * 
     * IMPORTANT! DO NOT MAKE THIS PUBLIC! WE WANT allowEmptyDynamicScopes false as much as possible.
     */
    private QueryOptions getQueryOptions(List<String> dynamicScopeNames, String quickLinkName, String quickLinkAction,
                                        boolean includeSelf, boolean includeOthers, boolean includeHidden, boolean allowEmptyDynamicScopeList) {
        
        QueryOptions qo = new QueryOptions();
        if (!Util.isEmpty(dynamicScopeNames)) {
            qo.add(Filter.in(DYNAMIC_SCOPE_NAME_QUERY_PROPERTY, dynamicScopeNames));
        } else if (!allowEmptyDynamicScopeList) {
            // KLUDGE: If user is in no dynamic scopes, we were previously not limiting them all, which in essence gave them membership in all dynamic scopes.
            // Really this should not be called in case of no dynamic scope membership, but we don't have a good
            // consistent way to avoid it if customers delete our allowAll dynamic scopes. So add this filter which
            // will never be true. 
            // TODO: Is there a better way to handle this? Maybe system dynamic scope that always applies? Return null queryOptions? Too risky right now.
            qo.add(Filter.isnull(DYNAMIC_SCOPE_QUERY_PROPERTY));
        }
        if (Util.isNotNullOrEmpty(quickLinkName)) {
            qo.add(Filter.eq(QUICK_LINK_NAME_QUERY_PROPERTY, quickLinkName));
        }
        if (Util.isNotNullOrEmpty(quickLinkAction)) {
            qo.add(Filter.eq(QUICK_LINK_ACTION_QUERY_PROPERTY, quickLinkAction));
        }
        // either you want to include self or others, if it's both then lets not filter at all
        if ( !(includeSelf && includeOthers) ) {
            if (includeSelf) {
                qo.add(Filter.eq(ALLOW_SELF_QUERY_PROPERTY, includeSelf));
            }
            if (includeOthers) {
                qo.add(Filter.or(Filter.eq(ALLOW_OTHER_QUERY_PROPERTY, includeOthers),
                                 Filter.eq(ALLOW_BULK_QUERY_PROPERTY, includeOthers)));
            }
        }
        // only add a hidden filter if include hidden is false, i.e. only visible quick links
        if (!includeHidden) {
            qo.add(Filter.eq(QUICK_LINK_HIDDEN_QUERY_PROPERTY, includeHidden));
        }
        
        return qo;
    }
}
