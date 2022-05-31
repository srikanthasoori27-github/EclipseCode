/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 * This policy checks each identity passed to it to see if it has more
 * than one link to any particular application.  If the identity does, 
 * a policy violation is created.
 *
 * Modified in 3.1 to understand instances of a template application.
 * A violation is raised only if the user has more than one
 * link to an account on the same instance.  If they have several
 * accounts on different instances it is not a violation.
 */
public class AccountPolicyExecutor extends AbstractPolicyExecutor {
    
    private static Log log = LogFactory.getLog(AccountPolicyExecutor.class);
    
    private static final String DETAILS_RENDERER = "policyAccountDetails.xhtml";
    
    /** The name of the application on which the policy violation occurred **/
    public static final String VIOLATION_APP_NAME = "AccountPolicyViolationAppName";
    public static final String VIOLATION_ACCOUNTS = "AccountPolicyViolationAccounts";

    SailPointContext context;
    List<PolicyViolation> violations;

    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    public List<PolicyViolation> evaluate(SailPointContext context, Policy policy, Identity identity) throws GeneralException {
        
        Meter.enterByName("AccountPolicyExecutor:evaluate");        
        this.context = context;
        violations = new ArrayList<PolicyViolation>();

        if (isSimulating()) {
            evaluateUsingLinks(policy, identity);
        } else {
            evaluateUsingQuery(policy, identity);
        }

        if (violations != null) {
            if (log.isDebugEnabled()) {
                log.debug("Returning: " + violations.size() + " violations.");
            }
        }
        Meter.exitByName("AccountPolicyExecutor:evaluate");        
        return violations;
    }
    
    
    private void evaluateUsingLinks(Policy policy, Identity identity) throws GeneralException {
        
        if (log.isDebugEnabled()) {
            log.debug("Identity Id: " + identity.getName() + " Links: " + identity.getLinks());
        }
        
        //Load the identity's list of applications and check for accounts on more than one app
        List<Link> links = identity.getLinks();
        
        if (!Util.isEmpty(links)) {
            Map<String, List<String>> appMap = new HashMap<String, List<String>>();
            
            for (Link link : links) {
                Application app = link.getApplication();
                // bug#3865 prefer the display name if we have one
                String account = link.getDisplayableName();

                if (app != null && account != null) {
                    // simplest to include the instance name as
                    // part of the key rather than have nested maps
                    String appkey = app.getName();
                    String inst = link.getInstance();
                    if (inst != null)
                        appkey = appkey + ":" + inst;
                    addToMap(appMap, appkey, account);
                }
            }
            
            Set<String> keys = appMap.keySet();
            for (String key : keys) {
                List<String> accounts = appMap.get(key);
                
                if (log.isDebugEnabled()) {
                    log.debug("Key: " + key + " Accounts size: " + accounts.size() + " Accounts: " + accounts);
                }
                
                if (accounts != null && accounts.size()>1) {
                    addViolation(identity, policy, key, accounts);
                }
            }
        }
    }
    
    private void evaluateUsingQuery(Policy policy, Identity identity) throws GeneralException {
    
        String query = 
                "select l.application.id, l.instance " +
        		"from sailpoint.object.Link l where l.identity.name = :identity " +
        		"group by application, instance " +
        		"having count(*) > 1";
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("identity", identity.getName());
       
        @SuppressWarnings("unchecked")
        Iterator<Object[]> iterator = context.search(query, params, null);
        while (iterator.hasNext()) {
            Object[] row = iterator.next();
            String appId = (String) row[0];
            String instance = (String) row[1];
            
            Pair<String, List<String>> accountsInfo = findAccounts(identity, appId, instance);
            addViolation(identity, policy, accountsInfo.getFirst(), accountsInfo.getSecond());
        }
    }
    
    private Pair<String, List<String>> findAccounts(Identity identity, String appId, String instance) throws GeneralException {
        
        Filter filter = Filter.and(
                Filter.eq("identity.name", identity.getName()),
                Filter.eq("application.id", appId)
                );
        if (instance != null) {
            filter = Filter.and(filter, Filter.eq("instance", instance));
        }
        QueryOptions options = new QueryOptions(filter);

        String appName = null;
        List<String> accounts = new ArrayList<String>();
        
        Iterator<Object[]> iterator = context.search(Link.class, options, Arrays.asList("application.name", "displayName", "nativeIdentity"));
        boolean firstTime = true;
        while (iterator.hasNext()) {
            Object[] row = iterator.next();
            if (firstTime) {
                appName = (String) row[0];
                if (instance != null) {
                    appName = appName + ":" + instance;
                }
                firstTime = false;
            }
            String displayName = (String) row[1];
            String nativeIdentity = (String) row[2];
            
            accounts.add(calculateDisplayableName(displayName, nativeIdentity));
        }

        return new Pair<String, List<String>>(appName, accounts);
    }
    
    private String calculateDisplayableName(String displayName, String nativeIdentity) {
        
        return (displayName != null) ? displayName : nativeIdentity;
    }
    
    
    private void addToMap(Map<String, List<String>> appMap, String key, String account) {
        
        List<String> accounts = appMap.get(key);
        if (accounts == null) {
            accounts = new ArrayList<String>();
        }
        accounts.add(account);
        appMap.put(key, accounts);
    }
    
    private void addViolation(PolicyViolation v) {

        violations.add(v);
    }
    
    private void addViolation(Identity id, Policy p, String appName, List<String> accounts) {

        PolicyViolation v = new PolicyViolation();
        v.setStatus(PolicyViolation.Status.Open);
        v.setIdentity(id);
        v.setPolicy(p);
        v.setConstraint(p.getConstraint());
        v.setRenderer(DETAILS_RENDERER);
        v.setOwner(p.getViolationOwnerForIdentity(this.context, id));
        
        // this has to be set if you want alerts
        // !! this should be on by default or changed to a noAlert flag
        v.setAlertable(true);

        // Put the application name and the accounts on the violation's arguments so it
        // can be rendered by the UI
        v.setArgument(VIOLATION_APP_NAME, appName);
        v.setArgument(VIOLATION_ACCOUNTS, accounts);

        // Attribute used to link a violation to an application. Used when creating
        // reports that are concerned with a given application.
        v.setRelevantApps(Arrays.asList(appName));

        // Account policy is funny because there is only one rule and it is
        // displayed on the same page as the main policy fileds.  This gives
        // us two "Description" fields on the page and it is unclear which
        // one to use.  It is quite common to set the Policy description but
        // not the GenericConstraint description so we'll look at both.
        if (v.getDescription() == null) {
            v.setDescription(p.getDescription());
        }

        // allow a rule to post-process the violation
        v = formatViolation(context, id, p, null, v);

        addViolation(v);
        return;
    }

}
