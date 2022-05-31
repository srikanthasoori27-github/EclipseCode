/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.integration.itim;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.ProvisioningPlan;
import sailpoint.integration.RemoteIntegration;
import sailpoint.integration.RequestResult;
import sailpoint.integration.RoleDefinition;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.IntegrationExecutor;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.ldap.LDAPUtil;


/**
 * Extension of the RemoteIntegration that augments the role definition model
 * before we push it down to ITIM.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ITIMIntegrationExecutor
    extends RemoteIntegration
    implements IntegrationExecutor {

    private static final Log LOG = LogFactory.getLog(ITIMIntegrationExecutor.class);
    
    // Name of the configuration attribute that holds the name of the role
    // extended attribute which can specify a dynamic role's filter.
    private static final String CONFIG_DYNAMIC_FILTER_ATTR =
        "dynamicFilterAttribute";
    
    // Names of attributes in the RoleDefinition required by the ITIM
    // integration.  These need to be kept in sync with the attributes in
    // ITIMRoller on the ITIM side.
    private static final String ATT_MATCH_RULE = "matchRule";
    private static final String ATT_CONTAINER_OU = "containerOU";
    private static final String ATT_ASSIGNABLE = "assignable";
    private static final String ATT_DETECTABLE = "detectable";
    
    // Description of the role.  If not specified we'll generate a default
    // description in the integration code.
    private static final String ATT_DESCRIPTION = "description";

    // Attributes used to control role availability in the ITIM "access
    // interface".  These are pulled from the IntegrationConfig attributes
    // map if they are specified.
    private static final String ATT_SHOW_ROLE_IN_ACCESS_INTERFACE =
        "showRoleInAccessInterface";
    private static final String ATT_SHOW_ROLE_AS_COMMON_ACCESS =
        "showRoleAsCommonAccess";
    private static final String ATT_ACCESS_INTERFACE_ROLE_CATEGORY=
        "accessInterfaceRoleCategory";

    // For any error code from a post on the RestClient, we attempt a retry.
    // Some clients may request that certain errors not be retried.
    // If the text in any of this list of strings is found in the error message,
    // we will not retry the request.
    private static final String ATT_DO_NOT_RETRY_LIST = "doNotRetryList";


    /**
     * Default constructor.
     */
    public ITIMIntegrationExecutor() {
        super();
    }

    /**
     * This adds ITIM-specific information to the role model that we are pushing
     * down into ITIM.
     */
    public void finishRoleDefinition(IntegrationConfig config, Bundle src,
                                     RoleDefinition dest)
        throws Exception {

        // Don't fill in the containerOU if the role had specified this in an
        // extended attribute.
        if (null == dest.get(ATT_CONTAINER_OU)) {
        
            // Add the nearest containing organizational role.
            // Not sure that an org role -> ou mapping in ITIM is the right
            // thing.  The workflow could potentially calculate the mapping when
            // the role is saved and store it in the role.
            //
            // We'll store it in the generic containerOU field so that this can
            // be subclassed if a customer wants a different mapping strategy.
            // And doesn't want to put it in the workflow.
            Bundle containingOrgRole = findContainingOrgRole(src);
            LOG.debug("Found container: " + containingOrgRole);
            if(containingOrgRole != null) {
                dest.put(ATT_CONTAINER_OU, containingOrgRole.getName());
            }
        }

        // Add some characteristics of the role.
        RoleTypeDefinition type = src.getRoleTypeDefinition();
        boolean assignable = (null != type) && type.isAssignable();
        boolean detectable = (null != type) && type.isDetectable();
        dest.put(ATT_ASSIGNABLE, assignable);
        dest.put(ATT_DETECTABLE, detectable);
        
        // Add the assignment match expression.  First look for the custom role
        // attribute that explicitly sets the match rule.  If not there, we'll
        // try to create one.
        String matchRule = null;
        String filterAttrName =
            (String) config.getAttribute(CONFIG_DYNAMIC_FILTER_ATTR);
        if (null != Util.getString(filterAttrName)) {
            matchRule = (String) src.getAttribute(filterAttrName);
            if (null != matchRule) {
                matchRule = matchRule.trim();
            }
        }

        // No explicit match rule, try to manufacture one.
        if (null == Util.getString(matchRule)) {
            matchRule = convertMatchExpressionToLDAPFilter(src, config);
        }

        if (null != Util.getString(matchRule)) {
            dest.put(ATT_MATCH_RULE, matchRule);
        }

        if (null != src.getDescription()) {
            dest.put(ATT_DESCRIPTION, src.getDescription());
        }

        // Add ITIM "Access Interface" options.
        Attributes<String,Object> attrs = config.getAttributes();
        if (null != attrs) {
            dest.put(ATT_SHOW_ROLE_IN_ACCESS_INTERFACE,
                     attrs.getBoolean(ATT_SHOW_ROLE_IN_ACCESS_INTERFACE, false));
            dest.put(ATT_SHOW_ROLE_AS_COMMON_ACCESS,
                     attrs.getBoolean(ATT_SHOW_ROLE_AS_COMMON_ACCESS, false));
            dest.put(ATT_ACCESS_INTERFACE_ROLE_CATEGORY,
                     attrs.getString(ATT_ACCESS_INTERFACE_ROLE_CATEGORY));
        }
    }
    
    /**
     * Find the nearest ancestor organizational role in the hiearchy of the
     * given role.
     */
    private Bundle findContainingOrgRole(Bundle src)
        throws GeneralException {

        Bundle container = null;
        
        List<Bundle> parents = src.getInheritance();

        if (null != parents) {
            container = getOrgRole(parents);
            if (null != container) {
                return container;
            }
            else {
                for (Bundle parent : parents) {
                    container = findContainingOrgRole(parent);
                    if (null != container) {
                        return container;
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Return the first organizational role in the given list of roles.
     */
    private Bundle getOrgRole(List<Bundle> bundles) {
        Bundle orgRole = null;
        if (null != bundles) {
            for (Bundle bundle : bundles) {
                // TODO: This is hard-coded to one or our default types now.
                // Should be genericized if we decide to keep this mapping.
                if ("organizational".equals(bundle.getType())) {
                    orgRole = bundle;
                    break;
                }
            }
        }
        return orgRole;
    }

    /**
     * ITIM dynamic roles use an LDAP search string that search for attributes
     * on the ITIM person.  Convert the match expression on the given role
     * (if available) into an LDAP search string.  Note that any match terms
     * that are not on the ITIM application are ignored.
     * 
     * @param  src      The source role.
     * @param  config   The IntegrationConfig.
     * 
     * @return The LDAP search string built from the match expression on the
     *         given role, or null if there is no match terms on the ITIM
     *         application.
     */
    public static String convertMatchExpressionToLDAPFilter(Bundle src,
                                                      IntegrationConfig config) {
        
        StringBuilder filter = new StringBuilder();
        IdentitySelector selector = src.getSelector();
        if(selector != null) {
            MatchExpression matchExpr = selector.getMatchExpression();
            if ((matchExpr != null) && (null != matchExpr.getTerms())) {
                Application itimApp = config.getApplication();
                int count = 0;
                for (IdentitySelector.MatchTerm term : matchExpr.getTerms()) {
                    String val = convertMatchTermToLDAPFilter(term, itimApp);
                    if (val != null) {
                        filter.append(val);
                        count++;
                    }
                }
                // If this is a compound filter, wrap the whole thing in parens
                // and add the AND or OR prefix operator.
                if (count > 1) {
                    char op = (matchExpr.isAnd()) ? '&' : '|';
                    filter.insert(0, "(" + op);
                    filter.append(")");
                }
                if (LOG.isInfoEnabled()) {
                    LOG.info("Filter for dynamic role " + src + ": " + filter);
                }
            }
        }
        return (filter.length() > 0) ? filter.toString() : null;
    }

    private static String convertMatchTermToLDAPFilter(MatchTerm term, Application itimApp) {

        if ((null == itimApp) || !itimApp.equals(term.getApplication())) {
            LOG.warn("Found a match term on an application other than the ITIM application - ignoring: " + term.getApplication());
            return null;
        }

        if (term.isContainer()) {
            return convertContainerMatchTermToLDAPFilter(term, itimApp);
        } else {
            return convertLeafMatchTermToLDAPFilter(term, itimApp);
        }
    }
    
    private static String convertContainerMatchTermToLDAPFilter(MatchTerm term, Application itimApp) {
        
        StringBuilder filter = new StringBuilder();
        int count = 0;
        for (IdentitySelector.MatchTerm child : term.getChildren()) {
            String val = convertMatchTermToLDAPFilter(child, itimApp);
            if (val != null) {
                 filter.append(val);
                count++;
            }
        }
        // If this is a compound filter, wrap the whole thing in parens
        // and add the AND or OR prefix operator.
        if (count > 1) {
            char op = (term.isAnd()) ? '&' : '|';
            filter.insert(0, "(" + op);
            filter.append(")");
        }
        
        return filter.toString();
    }
    
    private static String convertLeafMatchTermToLDAPFilter(MatchTerm term, Application itimApp) {
        
        StringBuilder filter = new StringBuilder();
        filter.append("(").append(term.getName()).append("=");
        filter.append(LDAPUtil.escapeLDAPSearchStringValue(term.getValue()))
                .append(")");

        return filter.toString();
    }

    /**
     * The only reason we're overriding this is so that we can inspect the messages
     * returned from ITIM and do something different if we see a particular error message.
     * bug #6993
     */
    public RequestResult provision(String identity, ProvisioningPlan plan)
            throws Exception {
        
        Object noRetries = _intConfigArgs.get(ATT_DO_NOT_RETRY_LIST);
        ArrayList<String> doNotRetryList = new ArrayList<String>();
        
        if (null != noRetries) {
            doNotRetryList = (ArrayList<String>) noRetries;
        }
        
        // Hard coded this entry since no one would want to retry this:
        if (!doNotRetryList.contains("CTGIMI006E")) {
            doNotRetryList.add("CTGIMI006E");
        }
        
        RequestResult result = super.provision(identity, plan);
        result = translateSuccessfulResult(result);
        
        String status = result.getStatus();
        if (RequestResult.STATUS_RETRY.equals(status)) {
            // If this has been flagged as a retry, see if it is an error message
            // that we actually don't want to retry.  This is silly.
            List warnings = result.getWarnings();
            for (int i = 0; i < warnings.size(); i++) {
                String warning = (String) warnings.get(i);
                if (null != warning) {
                    for (int j = 0; j < doNotRetryList.size(); j++) {
                        if (warning.indexOf(doNotRetryList.get(j)) > -1) {
                            // If something is in the don't retry list, then don't retry
                            result.setStatus(RequestResult.STATUS_FAILURE);
                            LOG.warn("The IntegrationConfig restricts this request from being " + 
                                    "retried or the error is non-retryable.  Refusing to retry.");
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }
    

    public RequestResult getRequestStatus(String requestId) throws Exception {
        RequestResult result = super.getRequestStatus(requestId);
        return translateSuccessfulResult(result);
    }
    
    /**
     * Returning STATUS_SUCCESS from an ITIM provision gets translated into QUEUED
     * in our new provisioning status paradigm.  If we've gotten a successful
     * status from ITIM, we need to translate it to committed before it gets
     * modified in ProvisioningResult.setStatus
     * 
     * This both modifies the given parameter and returns it.
     */
    private RequestResult translateSuccessfulResult(RequestResult result) {
        if (null != result) {
            String status = result.getStatus();
            if (RequestResult.STATUS_SUCCESS.equals(status)) {
                result.setStatus(ProvisioningResult.STATUS_COMMITTED);
            }
        }

        return result;
    }
}
