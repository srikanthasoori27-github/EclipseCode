package sailpoint.web.lcm;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.Rule;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;

/**
 * @deprecated Used as a delegate class for backwards compatibility
 * @see #sailpoint.service.LCMConfigService
 * @author brian.li
 *
 */
@Deprecated
public class LCMConfigService {
    
    private sailpoint.service.LCMConfigService service;
    private QuickLinkOptionsConfigService qloService;
    public static final String ATT_LCM_CONFIG_SERVICE_ACTION = sailpoint.service.LCMConfigService.ATT_LCM_CONFIG_SERVICE_ACTION;
    
    public static enum SelectorObject {
        Application,
        Role,
        ManagedAttribute
    }
    
    private static sailpoint.service.LCMConfigService.SelectorObject enumToUse (SelectorObject original) {
        if (Util.nullSafeEq(original.toString(), sailpoint.service.LCMConfigService.SelectorObject.Application.toString())) {
            return sailpoint.service.LCMConfigService.SelectorObject.Application;
        }
        else if (Util.nullSafeEq(original.toString(), sailpoint.service.LCMConfigService.SelectorObject.Role.toString())) {
            return sailpoint.service.LCMConfigService.SelectorObject.Role;
        }
        else {
            return sailpoint.service.LCMConfigService.SelectorObject.ManagedAttribute;
        }
    }

    public LCMConfigService(SailPointContext context) {
        this(context, null, null);
    }

    public LCMConfigService(SailPointContext context, Locale locale, TimeZone tz) {
        service = new sailpoint.service.LCMConfigService(context, locale, tz);
        qloService = new QuickLinkOptionsConfigService(context, locale, tz);
    }


    @Override
    public int hashCode() {
        return service.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return service.equals(obj);
    }

    public boolean addLCMApplicationAuthorityFilters(QueryOptions qo,
            Identity loggedInUser, String requesteeId) throws GeneralException {
        return service.addLCMApplicationAuthorityFilters(qo, loggedInUser,
                requesteeId);
    }

    public boolean addLCMApplicationAuthorityFilters(QueryOptions qo,
            Identity loggedInUser, String requesteeId, boolean appOnly)
            throws GeneralException {
        return service.addLCMApplicationAuthorityFilters(qo, loggedInUser,
                requesteeId, appOnly);
    }

    public boolean addLCMAttributeAuthorityFilters(QueryOptions qo,
            Identity loggedInUser, String requesteeId) throws GeneralException {
        return service.addLCMAttributeAuthorityFilters(qo, loggedInUser,
                requesteeId);
    }

    public QueryInfo getLCMAuthorityFilter(SelectorObject selectorObject,
            Identity loggedInUser, String requesteeId) throws GeneralException {
        return service.getLCMAuthorityFilter(enumToUse(selectorObject), loggedInUser,
                requesteeId);
    }

    public boolean isRequestAssignableRolesAllowed(boolean isSelfService) {
        return service.isRequestAssignableRolesAllowed(isSelfService);
    }

    public boolean isRequestPermittedRolesAllowed(boolean isSelfService) {
        return service.isRequestPermittedRolesAllowed(isSelfService);
    }
    
    public static boolean isLCMEnabled() {
        return Version.isLCMEnabled();
    }

    public boolean isQuickLinkEnabled(Identity currentUser,
            String quickLinkName, boolean isSelfService)
            throws GeneralException {
        return service.isQuickLinkEnabled(currentUser, quickLinkName,
                isSelfService);
    }

    public Set<Rule> getRules(Identity user, SelectorObject selectorObject,
            boolean isSelfService) throws GeneralException {
        return service.getRequestRules(user, null, enumToUse(selectorObject), isSelfService);
    }

    public QueryInfo getSelectorQueryInfo(Identity requestor,
            Identity requestee, SelectorObject selectorObject,
            boolean isSelfService) throws GeneralException {
        return service.getSelectorQueryInfo(requestor, requestee,
                enumToUse(selectorObject), isSelfService);
    }

    public QueryInfo getRoleSelectorQueryInfo(Identity requestor,
            Identity requestee, boolean returnManuallyAssignable,
            boolean returnPermitted, boolean exclude) throws GeneralException {
        return service.getRoleSelectorQueryInfo(requestor, requestee,
                returnManuallyAssignable, returnPermitted, exclude);
    }

    public List<Filter> getRoleExclusionFilters(Identity requestee,
            boolean excludeCurrentAccess) {
        return service.getRoleExclusionFilters(requestee, excludeCurrentAccess);
    }

    public Set<String> getInvalidRequestees(Identity requestor,
            List<String> requesteeIds, String requestedObject,
            SelectorObject type) throws GeneralException {
        return service.getInvalidRequestees(requestor, requesteeIds,
                requestedObject, enumToUse(type));
    }

    public Set<String> getInvalidRequestees(Identity requestor,
            List<String> requesteeIds, Set<String> requestedObjects,
            SelectorObject type) throws GeneralException {
        return service.getInvalidRequestees(requestor, requesteeIds,
                requestedObjects, enumToUse(type));
    }

    public boolean canRequestForOthers(Identity currentUser)
            throws GeneralException {
        return service.canRequestForOthers(currentUser);
    }

    
    /**
     * @deprecated use {@link QuickLinkOptionsConfigService#isQuickLinkEnabled(Identity, String, boolean)}
     */
    @Deprecated
    public boolean canRequestForSelf(Identity user) throws GeneralException {
        return qloService.isQuickLinkActionEnabled(user, QuickLink.LCM_ACTION_REQUEST_ACCESS, null, null, true, false);
    }

    public QueryOptions getConfiguredIdentityQueryOptions(Identity currentUser, List<String> dynamicScopes)
            throws GeneralException {
        return service.getConfiguredIdentityQueryOptions(currentUser, dynamicScopes);
    }

    public QueryOptions getRequestableIdentityOptions(Identity currentUser,
            List<String> dynamicScopes, String action) throws GeneralException {
        return service.getRequestableIdentityOptions(currentUser, dynamicScopes, null, action);
    }

    public String isTemplateValid(String template, Identity testUser) {
        return service.isTemplateValid(template, testUser);
    }

    public String getRequestTypeMessageKey(String requestType) {
        return service.getRequestTypeMessageKey(requestType);
    }

    public String getRequestTypeMessage(String requestType, Locale locale) {
        return service.getRequestTypeMessage(requestType, locale);
    }

    public int getSearchMaxResultCount() throws GeneralException {
        return service.getSearchMaxResultCount();
    }

    public MatchMode getSearchMode() throws GeneralException {
        return service.getSearchMode();
    }

    public int getPopulationMinimum(boolean roles) {
        return service.getPopulationMinimum(roles);
    }


}
