package sailpoint.service;

import static sailpoint.service.LCMConfigService.SelectorObject.Application;
import static sailpoint.service.LCMConfigService.SelectorObject.ManagedAttribute;
import static sailpoint.service.LCMConfigService.SelectorObject.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.Version;
import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.BulkIdJoin;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicScope;
import sailpoint.object.DynamicScope.PopulationRequestAuthority;
import sailpoint.object.DynamicScope.PopulationRequestAuthority.MatchConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.IdentityAttributeFilterControl;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;
import sailpoint.object.Rule;
import sailpoint.rest.RoleSearchUtil;
import sailpoint.server.ResultScoper;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.service.quicklink.QuickLinksService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.VelocityUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleConfig;

/**
 * A service class that helps accessing LCM configuration information.
 */
public class LCMConfigService {

    private static final Log log = LogFactory.getLog(LCMConfigService.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private static String APPLICATION_QUERY_PREFIX = "application.";

    public static enum SelectorObject {
        Application,
        Role,
        ManagedAttribute
    }

	/**
	 * Constant that holds the key for the action the user is attempting stored in the session.
	 * The value should be the same as a quicklink action and navigation from faces-config.
	 */
    public static final String ATT_LCM_CONFIG_SERVICE_ACTION = "lcmConfigServiceAction";
    /**
     * Constant that holds the quicklink name the user clicked in the session
     */
    public static final String ATT_LCM_CONFIG_SERVICE_QUICKLINK = "lcmConfigServiceQuicklink";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private SailPointContext context;
    private Locale locale;
    private TimeZone timezone;
    private String quickLinkName;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public LCMConfigService(SailPointContext context) {
        this(context, null, null);
    }

    public LCMConfigService(SailPointContext context, Locale locale, TimeZone tz) {
        this.context = context;
        this.locale = locale;
        this.timezone = tz;
    }

    public LCMConfigService(SailPointContext context, Locale locale, TimeZone tz, String quickLinkName) {
        this(context, locale, tz);
        this.quickLinkName = quickLinkName;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    public void setQuickLinkName(String qlName) { this.quickLinkName = qlName; }
    /** 
    * Add the LCM object request authority filters to the given QueryOptions for Applications.
    * 
     * @param qo the QueryOptions to add to
     * @param loggedInUser the user to check authority for
     * @param requesteeId the target
     * @return true if search should return nothing, false otherwise
    */
   public boolean addLCMApplicationAuthorityFilters(QueryOptions qo, Identity loggedInUser, String requesteeId) throws GeneralException {
        return addLCMApplicationAuthorityFilters(qo, loggedInUser, requesteeId, true);
    }
    /** 
     * Add the LCM object request authority filters to the given QueryOptions for Applications or to assist ManagedAttributes.
     * 
     * @param qo the QueryOptions to add to
     * @param loggedInUser the user to check authority for
     * @param requesteeId the target
     * @param appOnly true if this is for applications only, false if assisting for a ManagedAttribute search
     * @return true if search should return nothing, false otherwise
     */
    public boolean addLCMApplicationAuthorityFilters(QueryOptions qo, Identity loggedInUser, String requesteeId, boolean appOnly) throws GeneralException {
        // Application filter
        QueryInfo appQuery = getLCMAuthorityFilter(Application,
                loggedInUser, requesteeId);
        boolean returnNothing = appQuery.isReturnNone();

        if (!returnNothing) {
            Filter appFilter = appQuery.getFilter();
            if (null != appFilter) {
                if (!appOnly) {
                    // This filter assumes it is querying over applications.  Need to
                    // prefix all filters with "application.".
                    qo.addFilter(prefixApplication(appFilter));
                } else {
                    qo.add(appFilter);
                }
                returnNothing = false;
            }
        }
        return returnNothing;
    }

    /** 
     * Add the LCM object request authority filters to the given QueryOptions for ManagedAttributes.
     * 
     * @param qo the QueryOptions to add to
     * @param loggedInUser the user to check authority for
     * @param requesteeId the target
     * @return true if search should return nothing, false otherwise
     */
    public boolean addLCMAttributeAuthorityFilters(QueryOptions qo, Identity loggedInUser, String requesteeId) throws GeneralException {
        boolean returnNothing = addLCMApplicationAuthorityFilters(qo, loggedInUser, requesteeId, false);

        // Managed Attribute filter -- Don't bother if all the apps are out of scope anyway
        if (!returnNothing) {
            QueryInfo managedAttributeQuery = getLCMAuthorityFilter(ManagedAttribute,
                    loggedInUser, requesteeId);
            returnNothing = managedAttributeQuery.isReturnNone();
            if (!returnNothing) {
                Filter managedAttributeFilter = managedAttributeQuery.getFilter();
                if (null != managedAttributeFilter) {
                    qo.add(managedAttributeFilter);
                }
            }
        }
        return returnNothing;
    }

    /**
     * Return the LCM "object authority" QueryInfo for the given selectorobject, if any are defined.
     */
    public QueryInfo getLCMAuthorityFilter(SelectorObject selectorObject, Identity loggedInUser, String requesteeId) throws GeneralException {
        Identity requestor = loggedInUser;
        boolean isSelfService =
            (requesteeId != null && requesteeId.equals(requestor.getId()));
        Identity requestee = null;
        if (!Util.isNullOrEmpty(requesteeId)) {
            requestee =
                    (isSelfService) ? requestor
                            : context.getObjectById(Identity.class, requesteeId);
        }

        QueryInfo selectorQueryInfo = getSelectorQueryInfo(requestor, requestee, selectorObject, isSelfService);
 
        // Before changing the return type consider that a lone Filter is not enough to go on because 
        // the meaning of a null Filter is ambiguous.  Does it mean that no restrictions were placed
        // on the query or does it mean that nothing was supposed to be returned?  Just because a Filter
        // is simpler doesn't mean it's better.
        return selectorQueryInfo;
    }

    public boolean isRequestAssignableRolesAllowed(boolean isSelfService) {
        boolean allowAssignable = false;
        try {
            // if no role types are assignable then don't bother
            RoleConfig roleConfig = new RoleConfig();
            if (!Util.isEmpty(roleConfig.getAssignableRoleTypes())) {
                Configuration config = context.getConfiguration();

                String target = Configuration.LCM_SUBORDINATE;
                if(isSelfService) {
                    target = Configuration.LCM_SELF;
                }
                String key = Configuration.LCM_REQUEST_ROLES_PREFIX + Configuration.LCM_REQUEST_ROLES_ASSIGNABLE
                        + target + Configuration.LCM_OP_ENABLED_SUFFIX;

                allowAssignable = config.getBoolean(key);
            }           
        } catch(GeneralException ge) {
            log.error("Failed to retrieve LCM options regarding whether or not assignable roles are enabled", ge);
        }
        return allowAssignable;
    }
    
    public boolean isRequestPermittedRolesAllowed(boolean isSelfService) {
        boolean allowPermitted = false;
        try {
            Configuration config = context.getConfiguration();

            String target = Configuration.LCM_SUBORDINATE;
            if(isSelfService) {
                target = Configuration.LCM_SELF;
            }
            String key = Configuration.LCM_REQUEST_ROLES_PREFIX + Configuration.LCM_REQUEST_ROLES_PERMITTED
                    + target + Configuration.LCM_OP_ENABLED_SUFFIX;

            allowPermitted = config.getBoolean(key);
        } catch(GeneralException ge) {
            log.error("Failed to retrieve LCM options regarding whether or not permitted roles are enabled", ge);
        }
        return allowPermitted;
    }
    
    public static boolean isLCMEnabled() {
        return Version.isLCMEnabled();
    }

    /**
     * Returns true if self service is enabled for the quicklink
     * @param requestingUser The user making the request
     * @param dynamicScopeNames List of names of dynamic scopes that match the requesting user
     * @param quickLinkName     The name of the quicklink. Either this or action must be specified.
     * @param action The LCM action to match instead of a quick link. Used only if quickLinkName is not specified.
     * @return true if any of the specified dynamic scopes allow self-service for the given quick link or lcm action
     */
    public boolean isSelfServiceEnabled(Identity requestingUser, List<String> dynamicScopeNames, String quickLinkName, String action)
            throws GeneralException {
        QuickLinksService qlService = new QuickLinksService(context, requestingUser, dynamicScopeNames);
        List<QuickLink> selfServiceQuickLinks = qlService.getSelfQuickLinks();
        for (QuickLink selfServiceQuickLink : Util.iterate(selfServiceQuickLinks)) {
            // If quickLinkName is specified, use that.
            if (!Util.isNothing(quickLinkName)) {
                if (quickLinkName.equals(selfServiceQuickLink.getName())) {
                    return true;
                }
            }
            // Otherwise fall back to action
            else if (!Util.isNothing(action) && action.equals(selfServiceQuickLink.getAction())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @deprecated QuickLinks are no longer disabled. This functionality is controlled by the QuickLinkOptionsConfigService.
     * Use {@link sailpoint.service.quicklink.QuickLinkOptionsConfigService#isQuickLinkEnabled(Identity, String, boolean)} instead.
     */
    @Deprecated
    public boolean isQuickLinkEnabled(Identity currentUser, String quickLinkName, boolean isSelfService)
    throws GeneralException {
        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(context);
    	return svc.isQuickLinkEnabled(currentUser, quickLinkName, isSelfService);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FILTERING RULES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @param user Identity whose request rules will be returned
     * @param requestee Identity on whose behalf the request is being made
     * @param selectorObject SelectorObject to which the rules apply (i.e. Role or Application)
     * @param isSelfService true to include self-service rules; false otherwise
     * @return Set of rules that apply to the given requester for the given SelectorObject 
     */
    public Set<Rule> getRequestRules(Identity user, Identity requestee, SelectorObject selectorObject, boolean isSelfService)
            throws GeneralException {
        Set<Rule> rules = new HashSet<Rule>();
        boolean matched = false;

        try {
            DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
            List<String> populations = matcher.getMatches(user);

            // I first tried getting the dynamic scope directly but could not honor the isSelfService
            // flag without querying the quick link options service
            QuickLinkOptionsConfigService qloService = new QuickLinkOptionsConfigService(context);
            QueryOptions qo = qloService.getQueryOptions(populations, quickLinkName, QuickLink.LCM_ACTION_REQUEST_ACCESS,
                    isSelfService, !isSelfService, false);
            List<QuickLinkOptions> qloList = context.getObjects(QuickLinkOptions.class, qo);

            for (QuickLinkOptions oneQLO : Util.safeIterable(qloList)) {
                //If requestee doesn't belong to PopulationRequestAuthority, don't bother
                if (requestee != null && !isSelfService) {
                    if (!matcher.isMember(user, requestee, oneQLO.getDynamicScope().getPopulationRequestAuthority())) {
                        continue;
                    }
                }
                Rule ruleToAdd = null;

                switch (selectorObject) {
                case Application:
                    if (oneQLO.isAllowRequestEntitlements()) {
                        //TODO: The Application/Entitlement rules are coupled. Evaluating each independently is not the
                        //correct way to handle this. This lends itself to strange results -rap
                        ruleToAdd = oneQLO.getDynamicScope().getApplicationRequestControl();
                        if (ruleToAdd == null) {
                            //Found a DynamicScope without a rule configured, default to AllowAll
                            return null;
                        }
                        //We found a QuickLinkOptions with the selectorObject enabled. If we don't
                        matched = true;
                    }
                    break;
                case Role:
                    //Only return if the qlO supports role request
                    if (oneQLO.isAllowRequestRoles()) {
                        ruleToAdd = oneQLO.getDynamicScope().getRoleRequestControl();
                        if (null == ruleToAdd) {
                            //Role Request enabled, but no rule configured, show All
                            return null;
                        }
                        matched = true;
                    }
                    break;
                case ManagedAttribute:
                    // Only reutrn if qlO supports Entitlement request
                    if (oneQLO.isAllowRequestEntitlements()) {
                        ruleToAdd = oneQLO.getDynamicScope().getManagedAttributeRequestControl();
                        if (null == ruleToAdd) {
                            //Entitlements request enabled, but no rule configured, show All
                            return null;
                        }
                        matched = true;
                    }
                    break;
                default:
                    // As of now this is not a possibility.  However, if someone adds a new type 
                    // in the future we need to throw a meaningful exception to make them aware 
                    // that this needs to be changed as well.
                    throw new IllegalArgumentException(
                            "The LCMConfigService is not aware of the SelectorObject, " + selectorObject.toString() + 
                            ".  The getRules() method needs to be updated for this object type to be supported.");
                }

                if (ruleToAdd != null) {
                    rules.add(ruleToAdd);
                }
            }

        } catch (GeneralException e) {
            log.error("The populations to which user " + user.getDisplayableName() + " belongs could not be determined by the LCMConfigService.", e);
        }

        if (rules.isEmpty() && !matched) {
            //There was not a QuickLinkOptions configured for the given requestee/selectorType.
            //This is different than the DynamicScope not having a rule configured for a given QuickLinkOptions. -rap
            if (log.isInfoEnabled()) {
                log.info("No applicable QuickLinkOptions for requestor[" + user.getName() + "], requestee[" +
                        (Util.isNullOrEmpty(requestee.getName()) ? "bulk" : requestee.getName()) +
                        "] for type[" + selectorObject.name() + "]");
            }
            throw new QuickLinkOptionsNotFoundException();
        }
        
        return rules;
    }
    

    /**
     * Return a selector based on the rule in the LCM system configuration for the specified object type
     * @param requestor Identity that is requesting the objects
     * @param requestee Identity for which the request is being made
     * @param selectorObject SelectorObject for which the request is being made (i.e. Role or Application)
     * @param isSelfService true if this is a self-service request; false otherwise
     * @return QueryInfo object containing either a returnNone value of true to indicate that nothing should be returned or
     * a Filter that will the specified objects within the context of their selector rule.  Note that if this Filter
     * is null and returnNone is false then there are effectively no restrictions, so that case should be anticipated
     * as well
     * @throws GeneralException when the user-defined rule fails while generating a Filter
     */
    public QueryInfo getSelectorQueryInfo(Identity requestor, Identity requestee, SelectorObject selectorObject, boolean isSelfService, QuickLinkRuleOption option) throws GeneralException {
        QueryInfo result;
        
        if (requestor != null && requestor.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
            // System Administrators get to see everything
            result = new QueryInfo(null, false);
        } else {
           // Allowed, get the query info from the rules
           result = getQueryInfoFromRules(requestor, requestee, selectorObject, isSelfService, option);
        }
        
        return result;
    }

    public QueryInfo getSelectorQueryInfo(Identity requestor, Identity requestee, SelectorObject selectorObject, boolean isSelfService) throws GeneralException {

        return getSelectorQueryInfo(requestor, requestee, selectorObject, isSelfService, QuickLinkRuleOption.Request);

   }

    /**
     * Rule options used to specify which type of rules associated with the selector objects
     *
     */
    public static enum QuickLinkRuleOption {
        Request,
        Remove
    }

    /**
     * @param user Identity whose rules will be returned
     * @param requestee Identity on whose behalf the request is being made
     * @param selectorObject SelectorObject to which the rules apply (i.e. Role or Application)
     * @param isSelfService true to include self-service rules; false otherwise
     * @return Set of remove rules that apply to the given requester for the given SelectorObject 
     */
    public Set<Rule> getRemoveRules(Identity user, Identity requestee, SelectorObject selectorObject, boolean isSelfService) throws GeneralException {
        Set<Rule> rules = new HashSet<Rule>();
        boolean matched = false;

        try {
            DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
            List<String> populations = matcher.getMatches(user);

            // I first tried getting the dynamic scope directly but could not honor the isSelfService
            // flag without querying the quick link options service
            QuickLinkOptionsConfigService qloService = new QuickLinkOptionsConfigService(context);
            QueryOptions qo = qloService.getQueryOptions(populations, quickLinkName, QuickLink.LCM_ACTION_REQUEST_ACCESS,
                    isSelfService, !isSelfService, false);
            List<QuickLinkOptions> qloList = context.getObjects(QuickLinkOptions.class, qo);

            for (QuickLinkOptions oneQLO : Util.safeIterable(qloList)) {
                //If requestee doesn't belong to PopulationRequestAuthority, don't bother
                if (requestee != null && !isSelfService) {
                    if (!matcher.isMember(user, requestee, oneQLO.getDynamicScope().getPopulationRequestAuthority())) {
                        continue;
                    }
                }
                Rule ruleToAdd = null;

                switch (selectorObject) {
                case Application:
                 // Only reutrn if qlO supports Entitlement remove
                    if (oneQLO.isAllowRemoveEntitlements()) {
                        //TODO: The Application/Entitlement rules are coupled. Evaluating each independently is not the
                        //correct way to handle this. This lends itself to strange results -rap
                        ruleToAdd = oneQLO.getDynamicScope().getApplicationRemoveControl();
                        if (ruleToAdd == null) {
                            //Found a DynamicScope without a rule configured, default to AllowAll
                            return null;
                        }
                        //We found a QuickLinkOptions with the selectorObject enabled. If we don't
                        matched = true;
                    }
                    break;
                case Role:
                    //Only return if the qlO supports role remove
                    if (oneQLO.isAllowRemoveRoles()) {
                        ruleToAdd = oneQLO.getDynamicScope().getRoleRemoveControl();
                        if (null == ruleToAdd) {
                            //Role Request enabled, but no rule configured, show All
                            return null;
                        }
                        matched = true;
                    }
                    break;
                case ManagedAttribute:
                    // Only reutrn if qlO supports Entitlement remove
                    if (oneQLO.isAllowRemoveEntitlements()) {
                        ruleToAdd = oneQLO.getDynamicScope().getManagedAttributeRemoveControl();
                        if (null == ruleToAdd) {
                            //Entitlements request enabled, but no rule configured, show All
                            return null;
                        }
                        matched = true;
                    }
                    break;
                default:
                    // As of now this is not a possibility.  However, if someone adds a new type 
                    // in the future we need to throw a meaningful exception to make them aware 
                    // that this needs to be changed as well.
                    throw new IllegalArgumentException(
                            "The LCMConfigService is not aware of the SelectorObject, " + selectorObject.toString() + 
                            ".  The getRules() method needs to be updated for this object type to be supported.");
                }

                if (ruleToAdd != null) {
                    rules.add(ruleToAdd);
                }
            }

        } catch (GeneralException e) {
            log.error("The populations to which user " + user.getDisplayableName() + " belongs could not be determined by the LCMConfigService.", e);
        }

        if (rules.isEmpty() && !matched) {
            //There was not a QuickLinkOptions configured for the given requestee/selectorType.
            //This is different than the DynamicScope not having a rule configured for a given QuickLinkOptions. -rap
            if (log.isInfoEnabled()) {
                log.info("No applicable QuickLinkOptions for requestor[" + user.getName() + "], requestee[" +
                        (Util.isNullOrEmpty(requestee.getName()) ? "bulk" : requestee.getName()) +
                        "] for type[" + selectorObject.name() + "]");
            }
            throw new QuickLinkOptionsNotFoundException();
        }
        
        return rules;
    }

    /**
     * Get the query info from the rules for the selector object type 
     * @param requestor Identity that is requesting the objects
     * @param requestee Identity for which the request is being made
     * @param selectorObject SelectorObject for which the request is being made (i.e. Role or Application)
     * @param isSelfService true if this is a self-service request; false otherwise
     * @param option QuickLinkRuleOption to specify the rule type: remove or request
     * @return Valid QueryInfo object
     * @throws GeneralException
     */
    private QueryInfo getQueryInfoFromRules(Identity requestor, Identity requestee, SelectorObject selectorObject, boolean isSelfService, QuickLinkRuleOption option) throws GeneralException {
        try {
            Set<Rule> rules = null;
            if (option.equals(QuickLinkRuleOption.Request)) {
                rules = getRequestRules(requestor, requestee, selectorObject, isSelfService);
            }
            else if (option.equals(QuickLinkRuleOption.Remove)) {
                rules = getRemoveRules(requestor, requestee, selectorObject, isSelfService);
            }
            else {
                throw new IllegalArgumentException("Ilegal rule option: " + option.toString());   
            }

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("requestor", requestor);
            params.put("requestee", requestee);
            List<Filter> filters = new ArrayList<Filter>();
            if (rules != null && !rules.isEmpty()) {
                for (Rule rule : rules) {
                    Object requestControlResult = context.runRule(rule, params);
                    Filter filter = null;
                    if (requestControlResult instanceof Filter) {
                        // Support legacy rules
                        filter = (Filter) requestControlResult;
                    } else if (requestControlResult instanceof QueryInfo){
                        QueryInfo queryInfo = (QueryInfo) requestControlResult;
                        if (queryInfo.isReturnAll()) {
                            // If one of the rules gives the user access to everything there's no need for further evaluation
                            // because the access is eventually determined in a union and the union of anything with everything
                            // is still everything
                            return new QueryInfo(new QueryOptions());
                        } else {
                            filter = queryInfo.getFilter();
                        }
                    }

                    if (filter != null) {
                        filters.add(filter);
                    }
                }
            } else {
                // no rule, default to all objects
                // Emulating the 'All Objects' rule logic
                return new QueryInfo(null, false);
            }

            QueryOptions resultingOptions;
            if (filters.isEmpty()) {
                resultingOptions = null;
            } else if (filters.size() == 1) {
                resultingOptions = new QueryOptions(filters.get(0));
            } else {
                resultingOptions = new QueryOptions(Filter.or(filters));
            }

            return new QueryInfo(resultingOptions);

        } catch (QuickLinkOptionsNotFoundException e) {
            //Could not find any applicable QuickLinkOptions. Return QueryInfo with returnNone
            return new QueryInfo(null);
        }
    }
    
    /**
     * Return a role selector that accounts for additional options that only apply to roles.  LCM splits role retrievals
     * into two parts:  permitted roles and other manually-assignable roles.  This method returns a selector that will
     * return permitted, manually-assignable, or both types of roles within the scope defined by the role selector rule, 
     * depending on what is specified.  The selector also pre-excludes the standard LCM role exclusions:  disabled roles, 
     * previously assigned roles, and roles pending assignment
     * @param requestor Identity making the request
     * @param requestee Identity on whose behalf the request is being made
     * @param returnManuallyAssignable true if the consumer wants to attempt to return manually assignable roles.  Note that just because
     * they are requested doesn't mean they will be returned.  Success depends on whether the requestor is authorized to get
     * them by the LCM configuration
     * @param returnPermitted true if the requestor wants to attempt to return permitted roles.  Note that just because
     * they are requested doesn't mean they will be returned.  Success depends on whether the requestor is authorized to get
     * them by the LCM configuration
     * @param exclude Exclude roles on the assignedRoles and bundles list.
     * @param role When querying for inherited roles, this will be non-null; otherwise, it will be null.
     * @return QueryInfo object containing either a returnNone value of true to indicate that nothing should be returned or
     * a Filter that will retrieve permitted roles within the context of the role selector rule.  Note that if this Filter
     * is null and returnNone is false then there are effectively no restrictions (should not happen in practice, but the 
     * consumer should handle the case to be safe)
     * @throws GeneralException when the user-defined role selector rule fails while generating a Filter
     */
    public QueryInfo getRoleSelectorQueryInfo(Identity requestor, Identity requestee, boolean returnManuallyAssignable, boolean returnPermitted, boolean exclude, Bundle role)
            throws GeneralException {
        // Check for permitted roles if the identity has assigned roles
        RoleSearchUtil roleSearchUtil = new RoleSearchUtil(this);

        // Determine whether or not this is a self-service request
        boolean isSelfService;
        if (requestor != null && requestee != null) {
            isSelfService = requestor.equals(requestee);
        } else {
            isSelfService = false;
        }
        
        QueryInfo compositeQueryInfo = getSelectorQueryInfo(requestor, requestee, Role, isSelfService);
        if (!compositeQueryInfo.isReturnNone()) {
            // These two arguments track whether or not we're going to honor the request.  Just because someone 
            // wants manually assignable and/or permitted roles doesn't mean they're authorized to get them.
            boolean assignableAllowed = false;
            boolean permittedAllowed = false;
            if (returnManuallyAssignable) {
                // Just because we're requesting assignable doesn't mean we're allowed to get it.  First we need to make sure
                assignableAllowed = roleSearchUtil.isAllowManuallyAssignableRoles(context, isSelfService);
            }
            
            if (returnPermitted) {
                permittedAllowed = roleSearchUtil.isAllowPermittedRoles(context, isSelfService);
            }
            
            QueryInfo additionalRoleOptions = getAdditionalRoleOptions(requestee, assignableAllowed, permittedAllowed, role);
            if (additionalRoleOptions.isReturnNone()) {
                // If the additional options indicate that nothing is being returned then we're done
                compositeQueryInfo = additionalRoleOptions;
            } else {
                // Otherwise it's time to compile our results into a single filter
                List<Filter> compositeFilter = new ArrayList<Filter>();
                    
                // Combine the filters into one:
                // First we start with the basic LCM filter that we got from running the 
                // Role Object Selector rule at the beginning
                Filter baseFilter = compositeQueryInfo.getFilter();
                if (baseFilter != null) {
                    compositeFilter.add(baseFilter);
                }
                    
                // Then we mix in the filters that exclude disabled, previously assigned, and pending roles
                if (exclude) {
                    List<Filter> exclusionFilters = getRoleExclusionFilters(requestee, true);
                    if (!Util.isEmpty(exclusionFilters)) {
                        compositeFilter.addAll(exclusionFilters);
                    }
                } else {
                    // bug 22438 - always exclude disabled roles
                    compositeFilter.add(Filter.eq("disabled", false));
                }

                // Finally add the the manually assignable and/or permitted role filter(s)
                Filter additionalOptionsFilter = additionalRoleOptions.getFilter(); 
                if (additionalOptionsFilter != null) {
                    compositeFilter.add(additionalOptionsFilter);
                }
                    
                // Swizzle these into a composite QueryOptions object
                QueryOptions options;
                if (Util.isEmpty(compositeFilter)) {
                    // This is a return all
                    options = new QueryOptions();
                } else if (compositeFilter.size() == 1) {
                    options = new QueryOptions(compositeFilter.get(0));
                } else {
                    options = new QueryOptions(Filter.and(compositeFilter));                    
                }
                    
                compositeQueryInfo = new QueryInfo(options);
            }
        } // Else nothing is in scope anyway so we're done without having to check anything else
        
        // Before changing the return type on this method, be aware that returning simple Filters keeps this method's consumers 
        // from determining whether they should fetch everything or nothing at all.  The QueryInfo contains that information.  
        // Any potential replacement for the QueryInfo must provide that distinction to this method's consumers.
        return compositeQueryInfo;
    }
    
    public QueryInfo getRoleSelectorQueryInfo(Identity requestor, Identity requestee, boolean returnManuallyAssignable, boolean returnPermitted, boolean exclude) throws GeneralException {
        return getRoleSelectorQueryInfo(requestor, requestee, returnManuallyAssignable, returnPermitted, exclude, null);
    }
    
    /*
     * This method handles additional role options.  Before publicizing it consider using the getRoleSelectorQueryInfo instead.
     * In addition to using the logic here, it also checks the requestor's credentials to ensure that they have proper access to 
     * the assignable and/or permitted roles that this method finds 
     * @param requestee User on whose behalf the request is made
     * @param includeAssignable true to request manually assignable roles; false otherwise
     * @param includePermitted true to request permitted roles; false otherwise
     * @param role is used for inherited roles.  When null, make the subquery for directly assigned roles.
     * @return QueryInfo object containing a Filter that fetches manually assignable and/or permitted roles for the specified user.  
     * If no such roles are available the QueryInfo object's returnNone property is set to true
     */
    
    private QueryInfo getAdditionalRoleOptions(Identity requestee, final boolean includeAssignable, final boolean includePermitted, Bundle role) {
        // TODO: At some point we should migrate this out of Java code and into a role selector
        RoleConfig rc = new RoleConfig();
        Filter assignableFilter;

        // iiqbugs-121 - Don't create the assignableFilter if we don't have any manually 
        // assignable roles types.
        if (includeAssignable && !Util.isEmpty(rc.getManuallyAssignableRoleTypes())) {
            //Because this is only used in LCM cases, only need the manually assignable types
            assignableFilter = Filter.in("type", rc.getManuallyAssignableRoleTypes());
        } else {
            assignableFilter = null;
        }
        
        Filter permittedFilter;
        if (requestee != null && includePermitted) {
            List<Bundle> assignedRoles = requestee.getAssignedRoles();
            if (assignedRoles != null && !assignedRoles.isEmpty()) {
                if (role != null)
                    permittedFilter = Filter.subquery("id", Bundle.class, "permits.id", Filter.eq("id", role.getId()));
                else
                    permittedFilter = Filter.subquery("id", Identity.class, "assignedRoles.permits.id", Filter.eq("id", requestee.getId()));
            } else {
                permittedFilter = null;
            }
        } else {
            permittedFilter = null;
        }
        
        Filter additionalOptionsFilter;
        if (assignableFilter != null && permittedFilter != null) {
            additionalOptionsFilter = Filter.or(assignableFilter, permittedFilter);
        } else if (assignableFilter == null) {
            additionalOptionsFilter = permittedFilter;
        } else if (permittedFilter == null) {
            additionalOptionsFilter = assignableFilter;
        } else {
            // Neither are configured
            additionalOptionsFilter = null;
        }
        
        // Generate a QueryInfo object that recognizes null additional options as meaning "return nothing"
        QueryInfo optionsQuery = new QueryInfo(additionalOptionsFilter, additionalOptionsFilter == null);
        return optionsQuery;
    }
    
    /**
     * @param requestee Identity for whom the current request is being made.  For non-LCM requests (like searches)
     * this is typically null
     * @param excludeCurrentAccess
     * @return list of filters that exclude disabled, previously assigned, and pending roles
     */
    public List<Filter> getRoleExclusionFilters(Identity requestee, boolean excludeCurrentAccess) {
        List<Filter> filters = new ArrayList<Filter>();
        
        /** exclude disabled roles **/
        filters.add(Filter.eq("disabled", false));
        if(excludeCurrentAccess && requestee != null) {
            // Filter out any roles that are already assigned, detected, or pending requests
            Filter exclusionsFilter = getExclusionsFilter(requestee);
            if (exclusionsFilter != null) {
                filters.add(exclusionsFilter);            
            }
        }
        return filters;
    }
    
    private Filter getExclusionsFilter(Identity requestee) {
        List<Filter> exclusionsFilters = null;
        List<Bundle> assignedRoles = requestee.getAssignedRoles();
        List<Bundle> detectedRoles = requestee.getBundles();
        if(requestee != null) {
            exclusionsFilters = new ArrayList<Filter>();

            if (assignedRoles != null && !assignedRoles.isEmpty()) {
                exclusionsFilters.add(Filter.not(Filter.subquery("id", Identity.class, "assignedRoles.id", Filter.eq("id", requestee.getId()))));
            } 
            
            if (detectedRoles != null && !detectedRoles.isEmpty()) {
                exclusionsFilters.add(Filter.not(Filter.subquery("id", Identity.class, "bundles.id", Filter.eq("id", requestee.getId()))));
            }
            
            Set<String> pendingRequests = new HashSet<String>();
            /** Need to get any pending requests for this identity **/
            pendingRequests = getPendingRoleRequests(requestee);

            if (!pendingRequests.isEmpty()) {
                if (pendingRequests.size() == 1) {
                    exclusionsFilters.add(Filter.ne("id", pendingRequests.toArray()[0]));
                } else {
                    exclusionsFilters.add(Filter.not(Filter.in("id", pendingRequests)));
                }
            }
        }
        
        Filter exclusionsFilter;
        if (exclusionsFilters == null || exclusionsFilters.isEmpty()) {
            exclusionsFilter = null;
        } else if (exclusionsFilters.size() == 1) {
            exclusionsFilter = exclusionsFilters.get(0);
        } else {
            exclusionsFilter = Filter.and(exclusionsFilters);
        }
        
        return exclusionsFilter;
    }
    
    /**
     * @param requestor Identity requesting the object
     * @param requesteeIds IDs of Identities on whose behalf the object is being requested
     * @param requestedObject Name or ID of the object being requested
     * @param type SelectorObject that is being requested (i.e. Role or Application)
     * @return Set of display names of requestees who are found to be ineligible to request the object;
     *         an empty Set is returned if no such requestees are found
     */
    //TODO: DON'T DUPLICATE LOGIC IN TWO getInvalidRequestees METHODS!!
    public Set<String> getInvalidRequestees(Identity requestor, List<String> requesteeIds, String requestedObject, SelectorObject type) throws GeneralException {
        // Validate that this role is available to all users in the request
        Set<String> invalidRequestees = new HashSet<String>();
        if (requesteeIds != null && !requesteeIds.isEmpty()) {
            for (String requesteeId : requesteeIds) {
                Identity requestee = context.getObjectById(Identity.class, requesteeId);
                boolean isSelfService = requestor.getId().equals(requesteeId);
                QueryInfo selectorQueryInfo = getSelectorQueryInfo(requestor, requestee, type, isSelfService);
                if (selectorQueryInfo.isReturnNone()) {
                    invalidRequestees.add(requestee.getDisplayableName());
                } else if (!isRequestAllowedForSelectorType(type, isSelfService, requestor)) {
                    invalidRequestees.add(requestee.getDisplayableName());
                }  else {
                    Filter validationFilter = selectorQueryInfo.getFilter();
                    if (validationFilter != null) {
                        boolean hasName = !(ManagedAttribute.equals(type));
                        Filter requestObjectFilter = !hasName ? Filter.eq("id", requestedObject) :
                                Filter.or(Filter.eq("id", requestedObject),Filter.eq("name", requestedObject));
                        QueryOptions validationQuery = new QueryOptions(Filter.and(requestObjectFilter, validationFilter));
                        int numValidObjects = -1;

                        switch (type) {
                            case Role:
                                numValidObjects = context.countObjects(Bundle.class, validationQuery);
                                break;
                            case ManagedAttribute:
                                numValidObjects = context.countObjects(ManagedAttribute.class, validationQuery);
                                //if they have auth for the ManagedAttribute check the app too
                                if (numValidObjects > 0) {
                                    //reset
                                    ManagedAttribute attr = context.getObjectById(ManagedAttribute.class, requestedObject);
                                    String appId = attr.getApplicationId();
                                    List<String> requesteeSet = new ArrayList<String>();
                                    requesteeSet.add(requesteeId);
                                    Set<String> appInvalidRequestees = getInvalidRequestees(requestor, requesteeSet,appId, SelectorObject.Application);
                                    // if user is returned, mark invalid, if not pass through
                                    numValidObjects = 1 - appInvalidRequestees.size();
                                }
                                break;
                            case Application:
                                numValidObjects = context.countObjects(Application.class, validationQuery);
                        }
                        
                        if (numValidObjects <= 0) {
                            invalidRequestees.add(requestee.getDisplayableName());
                        }
                    }
                }
            }
        }
        return invalidRequestees;
    }


    /**
     * @param requestor Identity requesting the object
     * @param requesteeIds IDs of Identities on whose behalf the object is being requested
     * @param requestedObjects Set of Names or IDs of the objects being requested
     * @param type SelectorObject that is being requested (i.e. Role or Application)
     * @return Set of display names of requestees who are found to be ineligible to request the object;
     *         an empty Set is returned if no such requestees are found
     */
    //TODO: DON'T DUPLICATE LOGIC IN TWO getInvalidRequestees METHODS!!
    public Set<String> getInvalidRequestees(Identity requestor, List<String> requesteeIds, Set<String> requestedObjects, SelectorObject type) throws GeneralException {
        // We don't anticipate having to validate more than this many objects at once, but if we do
        // we'll just break the validation up into multiple queries.
        final int MAX_OBJECTS_PER_VALIDATION = 10;

        // List of Subsets of the objects that need to be validated.  Each subset is no larger than the MAX_OBJECTS_PER_VALIDATION  
        List<Set<String>> objectsToValidate = new ArrayList<Set<String>>();
        int numValidations;
        // Initialize the number of validations and break the requested objects into subsets if needed
        if (requestedObjects.size() <= MAX_OBJECTS_PER_VALIDATION) {
            numValidations = 1;
            objectsToValidate.add(requestedObjects);
        } else {
            // iiqtc-99 - if the requestedObjects size is not a multiple of 10 then we need
            // an extra validation set to handle the overflow. If the requestedObjects size is a
            // multiple of 10 then an extra validation set is not needed.
            if ((requestedObjects.size() % MAX_OBJECTS_PER_VALIDATION) == 0) {
                numValidations = (requestedObjects.size() / MAX_OBJECTS_PER_VALIDATION);
            } else {
                numValidations = (requestedObjects.size() / MAX_OBJECTS_PER_VALIDATION) + 1;
            }

            Iterator<String> requestedObjectsIterator = requestedObjects.iterator();

            for (int i = 0; i < numValidations; ++i) {
                Set<String> validationSet = new HashSet<String>();
                for (int j = 0; j < MAX_OBJECTS_PER_VALIDATION; ++j) {
                    if (requestedObjectsIterator.hasNext()) {
                        validationSet.add(requestedObjectsIterator.next());
                    }
                }
                objectsToValidate.add(validationSet);
            }
        }

        // Validate that these objects are available to all users in the request
        Set<String> invalidRequestees = new HashSet<String>();
        for (int i = 0; i < numValidations; ++i) {
            if (requesteeIds != null && !requesteeIds.isEmpty()) {
                for (String requesteeId : requesteeIds) {
                    Identity requestee = context.getObjectById(Identity.class, requesteeId);
                    boolean isSelfService = requestor.getId().equals(requesteeId);
                    QueryInfo selectorQueryInfo = getSelectorQueryInfo(requestor, requestee, type, isSelfService);

                    if (selectorQueryInfo.isReturnNone()) {
                        invalidRequestees.add(requestee.getDisplayableName());
                    } else if (!isRequestAllowedForSelectorType(type, isSelfService, requestor)) {
                        invalidRequestees.add(requestee.getDisplayableName());
                    } else {
                        Filter validationFilter = selectorQueryInfo.getFilter();
                        if (validationFilter != null) {
                            boolean hasName = !(ManagedAttribute.equals(type));
                            Set<String> currentValidationSet = objectsToValidate.get(i);
                            Filter currentValidationSetFilter = !hasName ? Filter.in("id", currentValidationSet) :
                                    Filter.or(Filter.in("id", currentValidationSet),Filter.in("name", currentValidationSet));
                            QueryOptions validationQuery = new QueryOptions(Filter.and(currentValidationSetFilter, validationFilter));

                            int numValidObjects = -1;
                            
                            switch (type) {
                                case Role:
                                    numValidObjects = context.countObjects(Bundle.class, validationQuery);
                                    break;
                                case ManagedAttribute:
                                    numValidObjects = context.countObjects(ManagedAttribute.class, validationQuery);
                                    //if they have auth for all ManagedAttributes check all the apps too
                                    if (numValidObjects == currentValidationSet.size()) {
                                        //reset
                                        QueryOptions maQuery = new QueryOptions(Filter.in("id", requestedObjects));
                                        List<ManagedAttribute> attrs = context.getObjects(ManagedAttribute.class, maQuery);
                                        Set<String> appIds = new HashSet<String>();
                                        for (ManagedAttribute attr : attrs) {
                                            appIds.add(attr.getApplicationId());
                                        }
                                        List<String> requesteeSet = new ArrayList<String>();
                                        requesteeSet.add(requesteeId);
                                        Set<String> appInvalidRequestees = getInvalidRequestees(requestor, requesteeSet, appIds, SelectorObject.Application);

                                        // bug 30241 - Fixing an issue where someone is requesting more than
                                        // one entitlement for someone else but can't unless they do it one at
                                        // a time.
                                        // If we get here then numValidObjects is equal to the number of items
                                        // in the current validation set (in this case, this is the number of
                                        // entitlements and there is more than one). When getInvalidRequestees() is
                                        // called above and there are no invalid requestees returned then the size
                                        // of appInvalidRequestees is zero. If we didn't have any invalid requestees
                                        // then numValidObject should be equal to the currentValidationSet.size().

                                        // if user is returned, mark invalid, if not pass through
                                        numValidObjects = numValidObjects - appInvalidRequestees.size();
                                    }
                                    break;
                                case Application:
                                    numValidObjects = context.countObjects(Application.class, validationQuery);
                            }
                            if (numValidObjects < currentValidationSet.size()) {
                                invalidRequestees.add(requestee.getDisplayableName());
                            }
                        }
                    }
                }
            }
        }
        return invalidRequestees;
    }

    private boolean isRequestAllowedForSelectorType(SelectorObject type, boolean isSelfService, Identity requestor) 
            throws GeneralException {
        boolean isAllowed = true;
        // TODO: Defaulting to check Request Access quicklink. Consider generifying this. But its hardcoded other places too, so OK for now. 
        if (SelectorObject.Role.equals(type) || SelectorObject.ManagedAttribute.equals(type)) {
            String requestControlOption = Configuration.LCM_ALLOW_REQUEST_ENTITLEMENTS;
            if (SelectorObject.Role.equals(type)) {
                isAllowed = isRequestPermittedRolesAllowed(isSelfService) || isRequestAssignableRolesAllowed(isSelfService);
                requestControlOption = Configuration.LCM_ALLOW_REQUEST_ROLES;
            }

            isAllowed &= new QuickLinkOptionsConfigService(this.context).isRequestControlOptionEnabled(requestor,
                    quickLinkName, QuickLink.LCM_ACTION_REQUEST_ACCESS, requestControlOption, isSelfService);
        }

        return isAllowed;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // POPULATION FILTERING
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return whether the given logged in user can make requests for others by
     * virtue of having "population authority" rules that are available to them.
     * Note that this doesn't necessarily mean that anyone matches their
     * population rules, just that they have rules available to them.
     */
    public boolean canRequestForOthers(Identity currentUser, List<String> dynamicScopes) throws GeneralException {
        QueryOptions qo = getConfiguredIdentityQueryOptions(currentUser, dynamicScopes);
        return (null != qo);
    }

    public boolean canRequestForOthers(Identity currentUser) throws GeneralException {
        QueryOptions qo = getConfiguredIdentityQueryOptions(currentUser, null);
        return (null != qo);
    }

    /** Returns a set of QueryOptions to the caller that can be applied to an identity search to return
     * a list of identities, based on user's dynamic scopes. 
     * @param currentUser
     * @param dynamicScopes the DynamicScope names of current user
     * @return set of QueryOptions to the caller that can be applied to an identity search to return
     * a list of identities that apply.  Returns null to indicate that no identities should be returned
     * and no search is necessary.
     * @throws GeneralException
     */
    public QueryOptions getConfiguredIdentityQueryOptions(Identity currentUser, List<String> dynamicScopes) throws GeneralException {
        return getRequestableIdentityOptions(currentUser, dynamicScopes, null, null);
    }
    
    /**
     * Provided a user and action,  based on user's dynamic scopes, returns a set of QueryOptions 
     * to the caller that can be applied to an identity search to return a list of identities.  
     * If the action does not apply to user's dynamic scope, query options
     * from that dynamic scope are not applied.
     * @param currentUser
     * @param dynamicScopes The DynamicScope names of current user
     * @param quicklinkName the name of a quickink the user has clicked
     * @param action action of a quicklink
     * @return a set of QueryOptions to the caller that can be applied to an identity search to return
     * a list of identities that apply.  Returns null to indicate that no identities should be returned
     * and no search is necessary.
     * @throws GeneralException
     */
    public QueryOptions getRequestableIdentityOptions(Identity currentUser, List<String> dynamicScopes, String quicklinkName, String action) throws GeneralException {
        if (dynamicScopes == null) {
            dynamicScopes = getDynamicScopeNames(currentUser);
        }


        //If a quicklinkName is specified, find the dynamic scopes that support the quicklink
        if (Util.isNotNullOrEmpty(quicklinkName)) {
            dynamicScopes = findDynamicScopesWithQuicklink(dynamicScopes, quicklinkName);
        } else if (Util.isNotNullOrEmpty(action)) {
            //Last resort, fall back to action
            // If an action is specified, find which dynamic scopes support this quicklink.
            // Otherwise, we will use look in the populations for all dynamic scopes.
            dynamicScopes = findDynamicScopesWithAction(dynamicScopes, action);
        }

        List<Filter> restrictions = new ArrayList<Filter>();
        for (String dynamicScopeName : dynamicScopes) {
            DynamicScope ds = context.getObjectByName(DynamicScope.class, dynamicScopeName);
            
            QueryOptions qo = buildFilter(currentUser, ds.getPopulationRequestAuthority(), isSelfServiceEnabled(currentUser, dynamicScopes, quicklinkName, action));
            if (qo != null) {
                List<Filter> currentRestrictions = qo.getRestrictions();
                if (currentRestrictions == null || currentRestrictions.isEmpty()) {
                    // We're done because either one of the user's population groups allows them to request for anyone
                    return new QueryOptions();
                } else {
                    restrictions.addAll(currentRestrictions);
                }
            }
        }
        
        QueryOptions identityQueryOptions;
        if (restrictions.isEmpty()) {
            // No options were enabled for this user, so we return a null to indicate that no search
            // is required.
            identityQueryOptions = null;
        } else if (restrictions.size() == 1) {
            // If there's only one restriction go with it
            identityQueryOptions = new QueryOptions(restrictions.get(0));
        } else {
            // If we have multiple restrictions from the various capabilities  
            // allow the user to request on behalf of users specified by any of them
            identityQueryOptions = new QueryOptions(Filter.or(restrictions));
        }

        // Scoping is applied explicitly in the filters of the QueryOptions, so
        // turn off scoping.
        if (null != identityQueryOptions) {
            identityQueryOptions.setScopeResults(false);
            identityQueryOptions.setDistinct(true);  //  bug 19897
        }

        return identityQueryOptions;
    }

    /**
     * Return the names of the dynamic scopes in the given list that support the given action for
     * others.
     */
    public List<String> findDynamicScopesWithAction(List<String> dynamicScopeNames, String action)
        throws GeneralException {

        List<String> ids = new ArrayList<String>();

        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(this.context);
        QueryOptions qo = svc.getQueryOptions(dynamicScopeNames, null, action, false, true, true);

        Iterator<Object[]> it = this.context.search(QuickLinkOptions.class, qo, "dynamicScope.name");
        while (it.hasNext()) {
            String id = (String) it.next()[0];
            ids.add(id);
        }

        return ids;
    }

    /**
     * Return the names of the dynamic scopes in the given list that support the given quicklink.
     */
    public List<String> findDynamicScopesWithQuicklink(List<String> dynamicScopeNames, String quickLinkName)
            throws GeneralException {

        List<String> ids = new ArrayList<String>();

        QuickLinkOptionsConfigService svc = new QuickLinkOptionsConfigService(this.context);
        QueryOptions qo = svc.getQueryOptions(dynamicScopeNames, quickLinkName, null, false, true, true);

        Iterator<Object[]> it = this.context.search(QuickLinkOptions.class, qo, "dynamicScope.name");
        while (it.hasNext()) {
            String id = (String) it.next()[0];
            ids.add(id);
        }

        return ids;
    }
    
    public QueryOptions buildFilter(Identity currentUser,
            PopulationRequestAuthority populationRequestAuthority) throws GeneralException {
        return buildFilter(currentUser, populationRequestAuthority, false);
    }

    public QueryOptions buildFilter(Identity currentUser,
                                     PopulationRequestAuthority populationRequestAuthority, boolean allowSelfService) 
            throws GeneralException {

        if (currentUser != null) {
            CapabilityManager capabilityManager = currentUser.getCapabilityManager();
            boolean isSysAdmin = capabilityManager.hasCapability(Capability.SYSTEM_ADMINISTRATOR);
            if (isSysAdmin) {
                // If the user has SYSTEM_ADMINISTRATOR capabilities return empty QueryOptions to indicate there are no restrictions.
                return new QueryOptions();
            }
        }

        if (populationRequestAuthority == null) {
            return null;
        }
        
        final boolean isAllowAll = populationRequestAuthority.isAllowAll();
        final boolean isScopingDisabled = populationRequestAuthority.isIgnoreScoping();
        final boolean mustApplyScoping = needToScope(currentUser, isScopingDisabled);
        Filter scopingFilter = null;
        ResultScoper scoper = null;
        //if scoping is needed, use ResultScoper to get the scoping filter
        if (mustApplyScoping) {
            // Use QueryOptions to force scoping
            QueryOptions qo = new QueryOptions();
            qo.setScopeResults(true);
            scoper = new ResultScoper(this.context, currentUser, qo);
            scopingFilter = scoper.getScopeFilter();
        }

        QueryOptions identityQueryOptions;
        if (isAllowAll) {
            if (mustApplyScoping) {
                if (scopingFilter == null) {
                    // If scoping is being applied but the scopingFilter indicates that nothing should be shown, return null
                    identityQueryOptions = null;
                } else {
                    // Only restrict by scopes
                    identityQueryOptions = new QueryOptions(scopingFilter);
                }
            } else {
                // Don't restrict at all because scoping is off and allow all is on
                identityQueryOptions = new QueryOptions();
            }
        } else {
            // Use the configured populations to indicate what should be returned
            List<Filter> subFilters = createFiltersByPopulation(currentUser, populationRequestAuthority);

            if (subFilters.isEmpty()) {
                // If we're here the correct behavior is to prevent any identities from showing up at all because no 
                // filters could be built.  We'll null out the query options to indicate that nothing should be 
                // returned for users that fall exclusively under this capability.
                identityQueryOptions = null;
            } else {
                Filter filter = null;
                if (subFilters.size() == 1) {
                    filter = applySinglePopulation(subFilters.get(0), scopingFilter, mustApplyScoping);
                } else {
                    filter = applyMultiplePopulations(populationRequestAuthority, subFilters, scopingFilter, mustApplyScoping);
                }    
                
                if(filter != null && !allowSelfService) {
                    filter = Filter.and(filter, Filter.not(Filter.eq("id", currentUser.getId())));                    
                }
                identityQueryOptions = new QueryOptions(filter);
            }
        }
        return identityQueryOptions;
    }

    /**
     * 
     * @param template Template that is being validated
     * @param testUser User that will be used as a test input to the template
     * @return null if the template is valid; an error message if it's not
     */
    public String isTemplateValid(String template, Identity testUser) {
        String errorMsg;
        if (template == null || template.trim().length() == 0) {
            errorMsg = null;
        } else {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("requester", testUser);
            try {
                String filterToCompile = VelocityUtil.render(template, args, locale, timezone);
                if (filterToCompile != null && filterToCompile.trim().length() > 0) {
                    Filter.compile(filterToCompile);
                }
                errorMsg = null;
            } catch (Exception e) {
                errorMsg = new Message(MessageKeys.LCM_CONFIG_TEMPLATE_INVALID, template).getLocalizedMessage(locale, timezone);
            }
        }
        
        return errorMsg;
    }
    
    /**
     * @param requestType Request type for which the key is being fetched
     * @return message key for the specified request type
     */
    public String getRequestTypeMessageKey(String requestType) {
        return "request_type_" + Util.splitCamelCase(requestType).replace(" ","_").toLowerCase();
    }

    /**
     * @param requestType request type whose localized name is being fetched
     * @param locale Locale in which to fetch the type's name
     * @return Localized version of the specified request type
     */
    public String getRequestTypeMessage(String requestType, Locale locale) {
        String messageKey = getRequestTypeMessageKey(requestType);
        String message = new Message(messageKey).getLocalizedMessage(locale, null);
        if (messageKey.equals(message)) {
            // If no message corresponding to the key for this request type was found
            // set the display name to the raw request type
            message = requestType;
        }

        return message;
    }

    /**
     * Get the maximum result count that should be returned for LCM access search
     * @return Integer value of max count
     * @throws GeneralException
     */
    public int getSearchMaxResultCount() throws GeneralException {
        Configuration configuration = this.context.getConfiguration();
        return configuration.getInt(Configuration.LCM_SEARCH_MAX_RESULTS);
    }

    /**
     * Get the search mode to use for LCM searches.
     * @return Filter.MatchMode
     */
    public Filter.MatchMode getSearchMode() throws GeneralException {
        // Pull searchType from system config and use for all searches.
        Filter.MatchMode searchMode = Filter.MatchMode.START;
        Configuration systemConfig = context.getConfiguration();
        String sMode = systemConfig.getString(Configuration.LCM_SEARCH_TYPE);
        // If sMode is null that means LCM_SEARCH_TYPE was never set, therefore default to contains
        if(sMode == null || sMode.equals(Configuration.LCM_SEARCH_TYPE_CONTAINS)) {
            searchMode = Filter.MatchMode.ANYWHERE;
        }
        return searchMode;
    }

    /**
     * Get the configured population minimum for either roles or entitlements
     * @param roles True if this is for roles, false for entitlements
     * @return Integer value for population minimum percentage, or 0
     */
    public int getPopulationMinimum(boolean roles) {
        boolean baselineAllowed = false;
        int baselinePercent = 0;
        try {
            Configuration sysConfig = this.context.getConfiguration();

            baselineAllowed = (roles) ? sysConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_PERCENT_LIMIT)
                    : sysConfig.getBoolean(Configuration.LCM_ALLOW_ENTITLEMENT_PERCENT_LIMIT);
            baselinePercent = (roles) ? sysConfig.getInt(Configuration.LCM_ALLOW_ROLE_PERCENT_LIMIT_PERCENT)
                    : sysConfig.getInt(Configuration.LCM_ALLOW_ENTITLEMENT_PERCENT_LIMIT_PERCENT);
        } catch (GeneralException ge) {
            log.warn("Unable to determine whether to show population slider: " + ge.getMessage());
        }

        int popMin = 0;

        /** There are two limits: First we check to see if the baseline configuration is set that says that
         * any results must have populations greater than X.  This is set in the system config (LCM->Additional Options)
         * and is turned on through a checkbox.
         *
         * Second, we check to see if they've used the slider to set a minimum.  If they have, we use that instead
         */

        if(baselineAllowed && baselinePercent>0) {
            popMin = baselinePercent;
        }

        return popMin;
    }
    
    private Filter getAttributeControlFilter(Identity currentUser, PopulationRequestAuthority populationRequestAuthority) {
        Filter attributeControlFilter = null;        
        MatchConfig matchConfig = populationRequestAuthority.getMatchConfig();
        if (matchConfig == null) {
            return null;
        }

        final boolean isEnableAttributeControl = matchConfig.isEnableAttributeControl();
        if (isEnableAttributeControl) {
            IdentityAttributeFilterControl filterControl = populationRequestAuthority.getMatchConfig().getIdentityAttributeFilterControl();
            if (filterControl != null) {
                Filter filterToAdd = filterControl.getFilter(currentUser);
                if (filterToAdd != null) {
                    attributeControlFilter = filterToAdd;
                }
            }
        }
        
        return attributeControlFilter;
    }
    
    private Filter getSubordinateControlFilter(Identity currentUser, 
                                               PopulationRequestAuthority populationRequestAuthority) 
                                                       throws GeneralException {
        Filter subordinateControlFilter = null;
        MatchConfig matchConfig = populationRequestAuthority.getMatchConfig();
        if (matchConfig == null) {
            return null;
        }
        
        final boolean isEnableSubordinateControl = matchConfig.isEnableSubordinateControl();
        if (isEnableSubordinateControl) {
            final String subordinateOption = matchConfig.getSubordinateOption();

            if (Configuration.LCM_REQUEST_CONTROLS_DIRECT_OR_INDIRECT.equals(subordinateOption)) {
                int MAX_HIERARCHY_DEPTH = matchConfig.getMaxHierarchyDepth();
                int MAX_HIERARCHY_COUNT = matchConfig.getMaxHierarchyCount();
                if (MAX_HIERARCHY_DEPTH < 1) {
                    MAX_HIERARCHY_DEPTH = 5;
                }
                
                if (MAX_HIERARCHY_COUNT < 1) {
                    MAX_HIERARCHY_COUNT = 8192;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Processing population authority " + populationRequestAuthority.toXml());
                }
                
                Set<String> managerIdsInHierarchy = new HashSet<String>();
                managerIdsInHierarchy.add(currentUser.getId());
                
                // If we want the whole hierarchy we need to add all managers in the chain to the filter
                Iterator<Object[]> subordManagerIds = 
                    context.search(Identity.class, new QueryOptions(Filter.and(Filter.eq("manager", currentUser), Filter.eq("managerStatus", true))), "id");
                Set<String> managersToInclude = new HashSet<String>();
                populateSet(managersToInclude, subordManagerIds, managerIdsInHierarchy);
                int currentDepth = 1;
                //Sanity check if we have a ton of managers in the hierarchy, we
                //also want to make sure to not blow away the max packet size of the db.
                //This loop will quite once it reaches the max identities or hierarchy level.
                int maxManagersAtATime = MAX_HIERARCHY_COUNT;
                while (managersToInclude != null && !managersToInclude.isEmpty() && currentDepth < MAX_HIERARCHY_DEPTH && managerIdsInHierarchy.size() <= MAX_HIERARCHY_COUNT) {
                    managerIdsInHierarchy.addAll(managersToInclude);
                    Set<String> moreManagersToCheck = new HashSet<String>();
                    Set<String> subManagersToInclude = new HashSet<String>();
                    int numOfManagers = managersToInclude.size();
                    if (numOfManagers > maxManagersAtATime) {
                        List<String> includedManagerIds = Arrays.asList(managersToInclude.toArray(new String[numOfManagers]));
                        int i = 0;
                        while (i < numOfManagers) {
                            List<String> subListManagerIds = includedManagerIds.subList(i, Math.min(i + maxManagersAtATime, numOfManagers));
                            Iterator<Object[]> subordManagersSubords = 
                                    context.search(Identity.class, new QueryOptions(Filter.and(Filter.in("manager.id", subListManagerIds), Filter.eq("managerStatus", true))), "id");
                            populateSet(moreManagersToCheck, subordManagersSubords, managerIdsInHierarchy);
                            i += maxManagersAtATime;
                        }
                    } else {
                        Iterator<Object[]> subordManagersSubords = 
                            context.search(Identity.class, new QueryOptions(Filter.and(Filter.in("manager.id", managersToInclude), Filter.eq("managerStatus", true))), "id");
                        populateSet(moreManagersToCheck, subordManagersSubords, managerIdsInHierarchy);
                    }
                        
                    managersToInclude = moreManagersToCheck;
                    ++currentDepth;
                }
                
                if (!managerIdsInHierarchy.isEmpty()) {
                    if (managerIdsInHierarchy.size() > maxManagersAtATime) {
                        log.warn("Manager hierarchy within quicklink population needs to be reduced! " +
                                "Currently " + managerIdsInHierarchy.size() + " managers are in a hierarchy of depth " +
                                currentDepth + ". Showing only those managers up to that depth.");
                    }

                    // if managerIds exceeds IN query limit use id join table
                    if (managerIdsInHierarchy.size() > ObjectUtil.MAX_IN_QUERY_SIZE) {
                        subordinateControlFilter = getJoinSubquery(currentUser, managerIdsInHierarchy);
                    } else {
                        subordinateControlFilter = Filter.in("manager.id", managerIdsInHierarchy);
                    }
                }
            } else {
                subordinateControlFilter = Filter.eq("manager", currentUser);
            }
        }

        return subordinateControlFilter;
    }

    /**
     * For IN queries that exceed the MAX_IN_QUERY_SIZE number of params use the join table.
     * @param currentUser the logged in user
     * @param managerIdsInHierarchy set of manager ids for the query
     * @return Filter subquery filter
     * @throws GeneralException
     */
    private Filter getJoinSubquery(Identity currentUser, Set<String> managerIdsInHierarchy) throws GeneralException {
        if (currentUser == null) {
            return Filter.in("manager.id", managerIdsInHierarchy);
        }
        // clear out the join table
        Filter joinFilter = Filter.and(Filter.eq("userId", currentUser.getId()),
                Filter.eq("joinProperty", "manager.id"));

        context.removeObjects(BulkIdJoin.class, new QueryOptions(joinFilter));

        for (String id : managerIdsInHierarchy) {
            BulkIdJoin idJoin = new BulkIdJoin("manager.id", id, currentUser.getId());
            context.saveObject(idJoin);
        }
        context.commitTransaction();

        return Filter.subquery("manager.id", BulkIdJoin.class, "joinId", joinFilter);
    }

    private Filter getCustomControlFilter(Identity currentUser, 
                                          PopulationRequestAuthority populationRequestAuthority) {
        Filter customFilter = null;
        MatchConfig matchConfig = populationRequestAuthority.getMatchConfig();
        if (matchConfig == null) {
            return null;
        }

        final boolean isEnableCustomControl = matchConfig.isEnableCustomControl();
        if (isEnableCustomControl) {
            String templateString = matchConfig.getCustomControl();
            if (templateString == null) {
                templateString = "";
            }
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("requester", currentUser);
            try {
                String filterToCompile = VelocityUtil.render(templateString, args, locale, timezone);
                if (filterToCompile != null && filterToCompile.trim().length() > 0) {
                    customFilter = Filter.compile(filterToCompile);
                } else {
                    customFilter = null;
                }
            } catch (GeneralException e) {
                log.warn("Unable to compile the custom filter, [" + templateString + "], while fetching identities for a request.  No custom filter was applied.", e);
                customFilter = null;
            }
        }
        
        return customFilter;
    }
    
    private List<Filter> createFiltersByPopulation(Identity currentUser, PopulationRequestAuthority populationRequestAuthority)
            throws GeneralException {
        List<Filter> subFilters = new ArrayList<Filter>();
        
        Filter attributeControlFilter = getAttributeControlFilter(currentUser, populationRequestAuthority);
        if (attributeControlFilter != null) {
            subFilters.add(attributeControlFilter);
        }
        
        Filter subordinateControlFilter = getSubordinateControlFilter(currentUser, populationRequestAuthority);
        if (subordinateControlFilter != null) {
            subFilters.add(subordinateControlFilter);
        }
        
        Filter customControlFilter = getCustomControlFilter(currentUser, populationRequestAuthority);
        if (customControlFilter != null) {
            subFilters.add(customControlFilter);
        }
        
        return subFilters;
    }

    /**
     * @param identityFilter Filter that constrains the population of identities being requested for
     * @param scopingFilter Filter that limits the scope of this population
     * @param mustApplyScoping true to apply the scoping filter; false otherwise
     * @return QueryOptions that conform to the specified parameters
     */
    private Filter applySinglePopulation(Filter identityFilter, Filter scopingFilter, boolean mustApplyScoping) {
        if (mustApplyScoping) {
            if (scopingFilter == null) {
                // If nothing appeared in this person's scope, don't return anything
                return null;
            } else {
                return Filter.and(identityFilter, scopingFilter);
            }
        } 
        
        return identityFilter;
    }
    
    /**
     * @return QueryOptions that conform to the specified parameters
     */
    private Filter applyMultiplePopulations(PopulationRequestAuthority populationRequestAuthority, 
                                            List<Filter> populationFilters, Filter scopingFilter, 
                                            boolean mustApplyScoping) {
        MatchConfig matchConfig = populationRequestAuthority.getMatchConfig();
        if (matchConfig == null) {
            return null;
        }

        final boolean isMatchAll = matchConfig.isMatchAll();
        
        Filter identityFilter;
        if (isMatchAll) {
            identityFilter = Filter.and(populationFilters);
        } else {
            identityFilter = Filter.or(populationFilters);
        }

        if (mustApplyScoping) {
            if (scopingFilter == null) {
                // If we need to scope but this user doesn't have access to any scopes they can't see anything
                return null;
            } else {
                // Apply scoping
                identityFilter = Filter.and(identityFilter, scopingFilter);
            }
        } 
        return identityFilter;
    }
    
    private void populateSet(Set<String> setToPopulate, Iterator<Object[]> contents, Set<String> masterSet) {
        // masterSet avoids duplicate searches and infinite recursion for screwy daisy-chain manager hierarchies        
        while (contents != null && contents.hasNext()) {
            Object[] content = contents.next();
            if (content != null && content.length > 0) {
                String contentString = (String) content[0];
                if (!masterSet.contains(contentString)) {
                    setToPopulate.add(contentString);
                }
            }
        }
    }

    /**
     * Return a copy of the given filter with all properties prefixed with
     * "application.".
     */
    private static Filter prefixApplication(Filter f) {
        // Clone it since we're going to be changing it.  Just in case...
        f = Filter.clone(f);

        if (f instanceof LeafFilter) {
            LeafFilter leaf = (LeafFilter) f;
            String prop = leaf.getProperty();
            if (!prop.startsWith(APPLICATION_QUERY_PREFIX)) {
                leaf.setProperty(APPLICATION_QUERY_PREFIX + prop);
            }
        }
        else if (f instanceof CompositeFilter) {
            List<Filter> modifiedChildren = new ArrayList<Filter>();
            for (Filter child : ((CompositeFilter) f).getChildren()) {
                modifiedChildren.add(prefixApplication(child));
            }
            if (!modifiedChildren.isEmpty()) {
                ((CompositeFilter)f).setChildren(modifiedChildren);
            }
        }
        
        return f;
    }

    /*
     * Return true if we determine that we need to scope.  Return false if scoping is not applicable.
     */
    private boolean needToScope(Identity userToScope, boolean isScopingDisabled) throws GeneralException {
        boolean scopeResults;
        // Ignore scoping for system administrators
        scopeResults = !userToScope.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
        if (scopeResults) {
            Configuration sysConfig = Configuration.getSystemConfig();
            scopeResults = Util.otob(sysConfig.get(Configuration.SCOPING_ENABLED));
        }

        return scopeResults && !isScopingDisabled;
    }

    /**
     * Returns a list of pending role requests for the given identity 
     *
     * @param identity The identity being searched for
     * @return A list of strings with each being the role Id
     */
    private Set<String> getPendingRoleRequests(Identity identity) {
        Set<String> pendingRequests = new HashSet<String>();

        try {
            CurrentAccessService currentAccessService = new CurrentAccessService(this.context, identity);
            List<CurrentAccessService.CurrentAccessRole> pendingRoles = currentAccessService.getRoles(CurrentAccessService.CurrentAccessStatus.Requested);

            for (CurrentAccessService.CurrentAccessRole role: pendingRoles) {
                Bundle roleObject = role.getObject(this.context);
                if (roleObject != null) {
                    pendingRequests.add(roleObject.getId());
                }
            }
        } catch (GeneralException e) {
            log.error("The pending role requests for " + identity.getName() + " could not be found.", e);
        }

        return pendingRequests;
    }
    
    
    /**
     * Replaces deprecated getLCMUserTypes() NOTE: getLCMUserTypes did not return "Self Service", however
     * this method will if the dynamic scope applies.
     * 
     * @param currentUser
     * @return list of dynamic scope names that apply to the currentUser parameter
     * @throws GeneralException
     */
    List<String> getDynamicScopeNames(Identity currentUser) throws GeneralException {
        
        DynamicScopeMatchmaker matcher = new DynamicScopeMatchmaker(context);
        List<String> dynamicScopeNames = matcher.getMatches(currentUser);

        return dynamicScopeNames;
    }

    public static class QuickLinkOptionsNotFoundException extends GeneralException { }

    /**
     * Return whether external ticket ids should be included in request searches
     * @return True if enabled, otherwise false.
     * @throws GeneralException
     */
    public boolean isShowExternalTicketId() throws GeneralException {
        return this.context.getConfiguration().getBoolean(Configuration.LCM_SHOW_EXTERNAL_TICKET_ID);
    }
}
