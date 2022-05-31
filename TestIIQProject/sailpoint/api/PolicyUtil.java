/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Utilities for analyzing policies and policy violations.
 *
 * Author: Jeff
 *
 * This was added to support a customer requirement for entitlement
 * details in the policy violation summary string.  Rather than
 * extend the policy model this was done with a custom 
 * violation rule to calculate the summary.  Since the logic
 * is relatively complex it was implemented as a Java utility class.
 *
 * NOTE: 
 *
 * After the EntitlementSummary model was developed we evolved
 * the the more general sailpoint.object.IdentityItem and 
 * sailpoint.object.ViolationDetails models.  It would be nice
 * if we could deprecate EntitlementSummary and move to the new
 * model but as usualy we have backward compatibility issues
 * with old rendering rules.  For now, we'll deal with
 * ViolationModel/IdentityItem internally and convert it
 * into EntitlementSummary just before returning it to the caller.
 * 
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Rule;
import sailpoint.object.SODConstraint;
import sailpoint.object.Script;
import sailpoint.object.ViolationDetails;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Utilities for analyzing policies and policy violations.
 */
public class PolicyUtil {

    private static final Log log = LogFactory.getLog(PolicyUtil.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Entitlement Details
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Model for representing entitlements related to SOD violations.
     * This is returned by summarizeViolationEntitlements
     * and is intended to be easy to use from Beanshell rules.
     * Keep lists non-null and use .length regularly in the JSf pages.
     *
     * Use the same model to convey information about both Role SOD
     * violations and Entitlement SOD violations.  
     * 
     * In practice there will only be one RoleSummary on each list because
     * we always OR the roles in the SOD constraint sides, even though
     * the model supports ANDing. So the first one that correlates
     * becomes the one for the violation.
     *
     * For Entitlement SOD there is one RoleSummary on each list but
     * the role name will be null to indicate they are "free" 
     * entitlements. The rendering rule needs to check this to 
     * avoid omitting a Role: label.
     *
     * @ignore
     * NOTE: This model is used only in combination with older
     * policy rendering rules.  Internally we use the newer
     * ViolationDetails/IdentityItem classes, we should eventually
     * move the rendering rules to use the new model and deprecate
     * EntitlementSummary.
     */
    static public class EntitlementSummary {
        
        /**
         * The "left" RoleSummaries. This should be modified using
         * {@link #assimilate(boolean, String, boolean, String, Object)}.
         */
        public List<RoleSummary> left = new ArrayList<RoleSummary>();

        /**
         * The "right" RoleSummaries. This should be modified using
         * {@link #assimilate(boolean, String, boolean, String, Object)}.
         */
        public List<RoleSummary> right = new ArrayList<RoleSummary>();


        /**
         * Assimilate the given entitlement SOD item into the
         * EntitlementSummary.
         *
         * @param bleft  True if this is a "left" item, false for "right".
         * @param appname  The name of the application for the entitlement.
         * @param permission  True if this is a permission.
         * @param name  The attribute name or permission target.
         * @param value  The attribute value or permission right.
         */
        public void assimilate(boolean bleft, String appname,
                               boolean permission, 
                               String name, Object value) {
            if (bleft)
                assimilate(left, appname, permission, name, value);
            else
                assimilate(right, appname, permission, name, value);
        }

        private void assimilate(List<RoleSummary> list, 
                                String appname, boolean permission,
                                String name, Object value) {
            RoleSummary role;
            if (list.size() > 0)
                role = list.get(0);
            else {
                role = new RoleSummary();
                list.add(role);
            }
            role.assimilate(appname, permission, name, value);
        }

        /**
         * Return a non-null RoleSummary for the attribute with the given name
         * from the left or right list.
         */
        public RoleSummary getRoleSummary(boolean bleft, String name) {

            RoleSummary found = null;
            List<RoleSummary> list = (bleft) ? left : right;
            if (list != null) {
                for (RoleSummary summary : list) {
                    if ((name == null && summary.name == null) ||
                        (name != null && name.equals(summary.name))) {
                        found = summary;
                        break;
                    }
                }
            }

            if (found == null) {
                found = new RoleSummary();
                found.name = name;  
                if (list == null) {
                    list = new ArrayList<RoleSummary>();
                    if (bleft)
                        left = list;
                    else
                        right = list;
                }
                list.add(found);
            }

            return found;
        }
    }

    /**
     * Represents a collection of application-specific entitlements, 
     * either "free" (the name is null) or associated with a role.
     *
     * We provide construction utilities to collapse entitlements for
     * the same application, attributes, and permission into the same 
     * summary objects to reduce clutter in the UI.
     */
    static public class RoleSummary {

        /**
         * The name of the role or null if this is a "free" entitlement.
         */
        public String name;

        /**
         * The ApplicationSummary objects for this role summary.  Manipulate
         * this list using {@link #assimilate(String, boolean, String, Object)}.
         */
        public List<ApplicationSummary> applications = 
        new ArrayList<ApplicationSummary>();

        /**
         * Locate or create an application summary with the given name and
         * add the item to it.
         */
        public void assimilate(String appname, boolean permission, 
                               String name, Object value) {
            ApplicationSummary app = intern(appname);
            app.assimilate(permission, name, value);
        }

        /**
         * Locate an application summary by name.
         * Assuming that these will not be large so linear is okay.
         */
        private ApplicationSummary getApplication(String name) {
            ApplicationSummary found = null;
            if (name != null) {
                for (ApplicationSummary app : applications) {
                    if (name.equals(app.name)) {
                        found = app;
                        break;
                    }
                }
            }
            return found;
        }
        
        /**
         * Locate or create an application summary with the given name.
         */
        private ApplicationSummary intern(String name) {
            ApplicationSummary app = getApplication(name);
            if (app == null) {
                app = new ApplicationSummary();
                app.name = name;
                applications.add(app);
            }
            return app;
        }
    }

    /**
     * Model for entitlements on a specific application account.
     * While the attribute/permission models are similar, 
     * it is nice to render them in slightly different ways
     * so keep them on different lists.
     */
    static public class ApplicationSummary {

        /**
         * The name of the application.
         */
        public String name;

        /**
         * The ItemSummary objects for the attributes. Manipulate this list
         * using {@link #assimilate(boolean, String, Object)}.
         */
        public List<ItemSummary> attributes = new ArrayList<ItemSummary>();

        /**
         * The ItemSummary objects for the permissions. Manipulate this list
         * using {@link #assimilate(boolean, String, Object)}.
         */
        public List<ItemSummary> permissions = new ArrayList<ItemSummary>();

        /**
         * Assimilate the given permission target/right or attribute value into
         * this ApplicationSummary. Items are collapsed for the same
         * name/target.
         *
         * @param permission  True if the item is a permission.
         * @param name  The name of the attribute or target of the permission.
         * @param value  The attribute value or permission right.
         */
        public void assimilate(boolean permission, String name, Object value) {
            if (permission)
                assimilate(permissions, permission, name, value);
            else
                assimilate(attributes, permission, name, value);
        }
        
        /**
         * Add an item to a list collapsing by name if possible. 
         * Also filter duplicate values.
         *
         * @param items  The list to add the item to.
         * @param permission  True if the item is a permission.
         * @param name  The name of the attribute or target of the permission.
         * @param value  The attribute value or permission right.
         */
        public void assimilate(List<ItemSummary> items, boolean permission, 
                               String name, Object value) {

            ItemSummary existing = getItem(items, name);
            if (existing != null)
                existing.assimilate(value);
            else {
                ItemSummary item = new ItemSummary();
                item.permission = permission;
                item.name = name;
                item.assimilate(value);
                items.add(item);
            }
        }
        
        /**
         * Look for an item on a list.
         */
        public ItemSummary getItem(List<ItemSummary> items, String name) {
            ItemSummary found = null;
            if (name != null && items != null) {
                for (ItemSummary att : items) {
                    if (name.equals(att.name)) {
                        found = att;
                        break;
                    }
                }
            }
            return found;
        }
    }

    /**
     * Model for one entitlement in an application.
     * Values will be unique.
     */
    static public class ItemSummary {

        /**
         * Whether this item is a permission or not. False if this is an
         * attribute value.
         */
        boolean permission;

        /**
         * The name of the attribute or target of the permission.
         */
        public String name;

        /**
         * The values for the attribute or rights of the permission.
         */
        public List<String> values = new ArrayList<String>();

        /**
         * The source role when doing role assignment side effect analysis.
         */
        String source;

        /**
         * Assimilate the given value into this item, filtering duplicates.
         */
        public void assimilate(Object value) {
            if (value instanceof Collection) {
                Collection col = (Collection)value;
                for (Object o : col) {
                    if (o != null && !values.contains(o))
                        values.add(o.toString());
                }
            }
            else if (value != null && !values.contains(value))
                values.add(value.toString());
        }

        /**
         * Rendering utility to get a nice csv.
         */
        public String getCsv() {
            return Util.listToCsv(values);
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // SOD "relevant entitlement" summary
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate a PolicyViolation summary object containing details
     * about the entitlements for a particular identity that were 
     * responsible for the violation.
     *
     * For Role SOD this will be the entitlements that gave them the
     * role involved in the violation. For Entitlement SOD it will
     * be the entitlements that caused a match in the policy rule.
     *
     * Note that the left/right roles in the violation will be 
     * the ones actually held by the identity. These are not necessarily
     * the same as the roles in the SODConstraint definition, they can be
     * SUBROLES of the roles in the policy. Only need to include 
     * entitlements from the policy roles not the sub roles.
     *
     * @param context  The SailPointContext.
     * @param identity  The Identity.
     * @param pv  The PolicyViolation for which to create the summary.
     * @param correlator  The EntitlementCorrelator to use - if null this will
     *    be created automatically.
     *
     * @ignore
     * For Role SOD we use EntitlementCorrelator to determine which
     * entitlements were required by the roles.
     * There are two options for managing the EntitlementCorrelator, 
     * prepare it every time with a limited set of "sourceRoles" taken
     * from the violation, or prepare it once with all roles
     * and only ask for the left/right entitlements we're interested in.  
     * Doing a full prepare is expensive so we would need to reuse
     * the same correlator for every violation during the Interrogator scan.
     * AbstractPolicyExecutor will now maintain a Map object passed into
     * the rule script as "state" that  can be used to cache a correlator.
     * This must be passed in as an argument here.  If this is null
     * we'll take the "prepare every time" approach.
     *
     * !! Need to retool this to make use of the persistent 
     * role entitlement log maintained in the cube as of 3.2.
     */
    public static EntitlementSummary 
    summarizeViolationEntitlements(SailPointContext context,
                                   Identity identity,
                                   PolicyViolation pv,
                                   EntitlementCorrelator correlator)
        throws GeneralException {

        // if something goes wrong, stick with the default summary
        ViolationDetails details = null;

        Identity ident = pv.getIdentity();
        if (ident == null)
            log.error("Missing identity");
        else {
            BaseConstraint basecon = pv.getConstraint(context);
            if (basecon == null) 
                log.error("Unable to locate constraint");

            else if (basecon instanceof SODConstraint) {
                SODConstraint sod = (SODConstraint)basecon;
                details = getRoleViolationDetails(context, identity, sod, correlator, pv);
            }
            else if (basecon instanceof GenericConstraint) {
                GenericConstraint sod = (GenericConstraint)basecon;
                details = getEntitlementViolationDetails(context, identity, sod);
            }
            else {
                log.error("Not a Role or Entitlement SOD violation");
            }

            // hack: try to determine where the conflicting entitlements came from
            // this is temporary for the Fortis POC, we need to find a better
            // way to do this.
            //findEntitlementSources(context, identity, summary);

        }

        // convert to the old model for the rendering rule
        // we don't have to worry about collapsing redundancies in the
        // ViolationDetails as long as we do this conversion
        return convertEntitlementSummary(details);
    }

    static private EntitlementSummary 
    convertEntitlementSummary(ViolationDetails details) {

        EntitlementSummary summary = null;
        if (details != null) {
            summary = new EntitlementSummary();
            convertEntitlementSummary(summary, true, details.getLeft());
            convertEntitlementSummary(summary, false, details.getRight());
        }

        return summary;
    }

    static private void convertEntitlementSummary(EntitlementSummary summary,
                                                  boolean left, 
                                                  List<IdentityItem> items) {
            if (items != null) {
                for (IdentityItem item : items) {
                    RoleSummary rs = summary.getRoleSummary(left, item.getRole());
                    rs.assimilate(item.getApplication(), 
                                  item.isPermission(),
                                  item.getName(),
                                  item.getValue());
                }
            }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role SOD Entitlement Mining
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Summarize the entitlements involved in a role SOD violation.
     * 
     * This takes a peculiar two-phase approach since the customer has
     * both assignment selectors and profiles in the same hierarchy
     * and they want to see the match items for both.  
     * EntitlementCorrelator is used in a focused correlation to get
     * the detected role entitlements from profile matching.
     * 
     * Then, for each policy role that has an assignment
     * selector either directly or inherited, run the Matchmaker
     * and accumulate the matching items. This is similar to what
     * CorrelationModel does during normal role assignment.  
     *
     * Finally merge the two lists and filter duplicates.
     *
     * @ignore
     * This is all horribly confusing and bassackwards, 
     * eventually the CorrelationModel will keep track of
     * all this as roles are assigned/detected and we can
     * pull that analysis into the PolicyViolation as the
     * violations are created.
     */
    static private ViolationDetails
    getRoleViolationDetails(SailPointContext context,
                            Identity identity, 
                            SODConstraint sod,
                            EntitlementCorrelator correlator,
                            PolicyViolation pv) 
        throws GeneralException {

        ViolationDetails details = new ViolationDetails();
        List<Bundle> left = sod.getLeftBundles();
        List<Bundle> right = sod.getRightBundles();

        List<Bundle> matches = null;
        Map<Bundle, List<EntitlementGroup>> mappings = null;

        // old way, we run entitlement correlation which is accurate
        // but expensive
        boolean oldWay = true;
        
        if (oldWay) {
            // allowing this to be passed in was expected to be
            // an option that we never used, presumably it would
            // do correlation over the entire role model which
            // isn't optimial, just do a targeted correlation
            // on the roles we're interested in
            if (correlator == null) {
                // focused correlation
                correlator = new EntitlementCorrelator(context);

                // NOTE: Originally worked from the policy roles
                // but those are often just container roles with
                // no profiles.  Instead have to work from the
                // current detected roles and work back up to the
                // policy roles.
                // pass through a HashSet to remove duplicates,
                // which are not uncommon in the test data
                //Collection<Bundle> col = new HashSet<Bundle>();
                //col.addAll(left);
                //col.addAll(right);
                //List<Bundle> sources = new ArrayList<Bundle>(col);

//                List<Bundle> sources = identity.getDetectedRoles();
//                correlator.setSourceRoles(sources);
                List<Bundle>  roles = new ArrayList<Bundle>();
                if( identity.getDetectedRoles() != null ) {
                    roles.addAll( identity.getDetectedRoles() );
                }
                if( identity.getAssignedRoles() != null ) {
                    roles.addAll( identity.getAssignedRoles() );
                }
                correlator.setSourceRoles( roles );
            }

            correlator.analyzeIdentity(identity);
            mappings = correlator.getEntitlementMappings();

            // this is residue from the previous call
            matches = correlator.getDetectedRoles();
        }
        else {
            // matches are just those detected roles that appear somewhere in the SOD
            matches = new ArrayList<Bundle>();
            List<Bundle> detected = identity.getDetectedRoles();
            if (detected != null) {
                for (Bundle detrole : detected) {
                    if (left.contains(detrole) || right.contains(detrole))
                        matches.add(detrole);                
                }
            }

            // mappings were left on the identity
            mappings = identity.getRoleEntitlementMappings(context);
        }

        details.addLeft(getRoleDetails(identity, matches, left, mappings));
        details.addRight(getRoleDetails(identity, matches, right, mappings));

        // now do assignment rules
        // this turns out not to be necessary so don't risk it until
        // we know for sure
        boolean includeAssignmentRules = false;
        if (includeAssignmentRules) {
            Matchmaker matchmaker = new Matchmaker(context);
            matchmaker.setArgument("identity", identity);

            details.addLeft(getRoleAssignmentDetails(matchmaker, identity, left));
            details.addRight(getRoleAssignmentDetails(matchmaker, identity, right));
        }
        
        if( details.getLeft() == null ) {
            Matchmaker matchmaker = new Matchmaker(context);
            matchmaker.setArgument("identity", identity);
            details.addLeft( getRoleAssignmentDetails( matchmaker, identity, pv.getLeftBundles( context ) ) );
        }
        if( details.getRight() == null ) {
            Matchmaker matchmaker = new Matchmaker(context);
            matchmaker.setArgument("identity", identity);
            details.addRight( getRoleAssignmentDetails( matchmaker, identity, pv.getRightBundles( context ) ) );
        }

        return details;
    }

    /**
     * Summarize a list of roles representing one side of the SOD violation.
     * Currently the roles are always OR'd so it can stop after the first
     * one that matched.
     */
    static private List<IdentityItem> 
    getRoleDetails(Identity identity,
                   List<Bundle> matchedRoles,
                   List<Bundle> policyRoles,
                   Map<Bundle, List<EntitlementGroup>> entitlements) {

        List<IdentityItem> items = new ArrayList<IdentityItem>();

        if (matchedRoles != null && policyRoles != null) {
            List<IdentityItem> roleItems = null;
            for (Bundle prole : policyRoles) {
                Bundle mrole = findMatchingRole(matchedRoles, prole);
                if (mrole != null) {
                    // TODO: if !prole.equals(mrole) we had to look up
                    // the inheritance hierarchy, the entitlements granting
                    // the inherited role may be less than those required for
                    // the sub role.  Still, it seems to make sense to show
                    // all the subrole entitlements since that's what you're
                    // likely going to need to remediate.
                    roleItems = getRoleDetails(mrole, entitlements);
                    break;
                }
            }

            if (roleItems != null && roleItems.size() > 0)
                items.addAll(roleItems);
            else {
                if (log.isWarnEnabled()) {
               		log.warn("Unable to find matching roles in identity: " + identity.getName());
	                log.warn("Policy roles: " + getNameCsv(policyRoles));
    	            log.warn("Detected roles: " + getNameCsv(matchedRoles));
                }
            }
        }
            
        return items;
    }

    static private String getNameCsv(List<Bundle> roles) {

        List<String> names = new ArrayList<String>();
        if (roles != null) {
            for (Bundle role : roles)
                names.add(role.getName());
        }
        return Util.listToCsv(names);
    }

    /**
     * Locate a role in the detected role list for an identity
     * that could have caused a policy violation on the given policy role.
     * If policyRole does not appear directly in matchedRoles, walk up
     * the hierarchy of each matched role to see if the policy role
     * was inherited which would also trigger the violation.
     *
     * The customer makes use of inheritance with SOD policies on the super
     * roles. Super roles are like categories of roles (they call these
     * "clusters") and they want to check for conflicts between any roles
     * in two categories.
     *
     * Note that the resulting entitllement details will be for the
     * sub role which can include entitlements that are not defined
     * on the super role. In theory they might want the details filtered
     * to contain only those that got them the super role but that is not
     * easy with the way EntitlementCorrelator works. Also you are likely
     * to want all the entitlements for remediation.
     *
     */
    static private Bundle findMatchingRole(List<Bundle> matchedRoles, 
                                           Bundle policyRole) {

        Bundle found = null;
        if (matchedRoles != null) {
            for (int i = 0 ; i < matchedRoles.size() && found == null ; i++) {
                Bundle mrole = matchedRoles.get(i);
                if (mrole.equals(policyRole))
                    found = mrole;
                else {
                    Bundle inherited = findMatchingRole(mrole.getInheritance(), policyRole);
                    // note that we always return the element from matchedRoles
                    // not the super role, since this is the one that
                    // is used to index the entitlements map built
                    // by the correlator
                    if (inherited != null)
                        found = mrole;
                }
            }
        }
        return found;
    }

    /**
     * Derive the list of items for a particular role.
     */
    static private List<IdentityItem>
    getRoleDetails(Bundle role, 
                   Map<Bundle, List<EntitlementGroup>> entitlements) {

        List<IdentityItem> items = new ArrayList<IdentityItem>();

        List<EntitlementGroup> groups = entitlements.get(role);
        if( role.getRoleTypeDefinition().isAssignable() ) {
            IdentityItem item = new IdentityItem();
            item.setRole(role.getName());
            items.add(item);
        }
        if (groups == null || groups.size() == 0) {
            // cube must have changed since the last detection
            if (log.isInfoEnabled())
                log.info("Identity lost role: " + role.getName());
        }
        else {
            for (EntitlementGroup group : groups) {
                Application app = group.getApplication();

                Map<String,Object> atts = group.getAttributes();
                if (atts != null) {
                    Iterator<Map.Entry<String,Object>> it = atts.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String,Object> entry = it.next();
                        IdentityItem item = new IdentityItem();
                        item.setRole(role.getName());
                        item.setApplication(app.getName());
                        item.setName(entry.getKey());
                        item.setValue(entry.getValue());
                        items.add(item);
                    }
                }

                List<Permission> perms = group.getPermissions();
                if (perms != null) {
                    for (Permission perm : perms) {
                        IdentityItem item = new IdentityItem(perm);
                        item.setRole(role.getName());
                        item.setApplication(app.getName());
                        items.add(item);
                    }
                }
            }
        }

        return items;
    }

    /**
     * After calculating entitlement summaries for detected roles,
     * look for any roles in the policy that have assignment rules.
     * This gets these cases: roles that were on the assigned role
     * list but not the detected role list (normal), roles that
     * were on both the assigned role and detected role list (maybe
     * indirectly through inheritance, the weird JPMC case), and
     * roles that were on the detected list, not on the assignment list, 
     * but might have pieces of the assignment rule matching (a partial
     * provisioning case I'm not sure JPMC needs?).
     *
     * This does not seem exactly right in the general case but it
     * should work until a redesign of this can be done
     * to save IdentityItems as a side effect of the assignment
     * detection process.
     */
    static private List<IdentityItem> 
    getRoleAssignmentDetails(Matchmaker matchmaker, 
                             Identity identity,
                             List<Bundle> policyRoles) {

        List<IdentityItem> details = new ArrayList<IdentityItem>();

        for (Bundle role : policyRoles) {
            // accumulate item matches as we evaluate each role, but
            // add it to the master summary only if we have a full match
            List<IdentityItem> items = new ArrayList<IdentityItem>();
            if (isMatch(matchmaker, identity, role, items)) {
                for (IdentityItem item : items) {
                    item.setRole(role);
                    details.add(item);
                }
            }
        }

        return details;
    }

    /**
     * Do assignment selector match analysis for one role.
     * This must include the role hierarchy so it behaves 
     * similar to what CorrelationModel does during assignment.
     * Accumulate IdentityItems, return true only if
     * the full hierarchy matches.
     */
    static private boolean isMatch(Matchmaker matchmaker,
                                   Identity identity,
                                   Bundle role,
                                   List<IdentityItem> items) {

        boolean match = true;

        List<Bundle> inheritance = role.getInheritance();
        if (inheritance != null) {
            for (Bundle inherit : inheritance) {
                match = isMatch(matchmaker, identity, inherit, items);
                if (!match)
                    break;
            }
        }
        
        if (match) {
            IdentitySelector selector = role.getSelector();
            if (selector != null) {
                try {
                    match = matchmaker.isMatch(selector, identity);
                }
                catch (GeneralException e) {
                    // for our purposes, just eat it
                    log.error(e.getMessage(), e);
                    
                    match = false;
                }

                if (match) {
                    List<IdentityItem> matchItems = matchmaker.getLastMatchItems();
                    if (matchItems != null)
                        items.addAll(matchItems);
                }
            }
        }

        return match;
    }

    /**
     * Calculate the relationship path from one role to another.
     * Both roles must be in the same Hibernate session.
     */
    static public List<IdentityItem.Path> getRolePath(Bundle src, Bundle target)
        throws GeneralException {

        List<IdentityItem.Path> path = new ArrayList<IdentityItem.Path>();

        getRolePath(src, target, path, null);

        return path;
    }

    static private boolean getRolePath(Bundle src, Bundle target, 
                                       List<IdentityItem.Path> path,
                                       String relationship)
        throws GeneralException {

        boolean found = false;

        if (src == target) {
            IdentityItem.Path element = new IdentityItem.Path(target, relationship);
            path.add(0, element);
            found = true;
        }
        else {
            found = getRolePath(src.getInheritance(), target, path, 
                                IdentityItem.RELATIONSHIP_INHERITS);

            if (!found)
                found = getRolePath(src.getRequirements(), target, path, 
                                    IdentityItem.RELATIONSHIP_REQUIRES);

            if (!found)
                found = getRolePath(src.getPermits(), target, path, 
                                    IdentityItem.RELATIONSHIP_PERMITS);

            // if we found it within ourselves, we become one with the path
            // is that zen or what!
            if (found) {
                IdentityItem.Path element = new IdentityItem.Path(src, relationship);
                path.add(0, element);
            }
        }
        
        return found;
    }

    static private boolean getRolePath(List<Bundle> roles, Bundle target,
                                       List<IdentityItem.Path> path,
                                       String relationship)
        throws GeneralException {

        boolean found = false;
        if (roles != null) {
            for (Bundle role : roles) {
                found = getRolePath(role, target, path, relationship);
                if (found)
                    break;
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Entitlement SOD Summary
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Determine which entitlements held by an identity were
     * responsible for triggering the violation and build
     * out an ViolationDetails object to describe them.
     */
    static private ViolationDetails getEntitlementViolationDetails(SailPointContext context,
                                   Identity identity, 
                                   GenericConstraint sod) throws GeneralException{

        ViolationDetails details = new ViolationDetails();
        IdentitySelector left = sod.getLeftSelector();
        IdentitySelector right = sod.getRightSelector();

        // Role and Entitlement SOD will have both left and right
        // selectors.  Advanced will have only left.  If one is missing
        // continue to build it out and let the other side be null,
        // formatting rule should detect this and suppress the
        // "--conflicts with--.  If a rule uses the SODLeft or
        // SODRight pragmas, this overrides the left/rigth flag we
        // pass down here.

        if (left != null)
            buildItems(context, details, true, identity, left);

        if (right != null)
            buildItems(context, details, false, identity, right);

        return details;
    }

    /**
     * Build out one side of EntitlementSummary comparing
     * an Identity to an IdentitySelector from the policy.
     */
    static private void buildItems(SailPointContext ctx, ViolationDetails details,
                                   boolean left,
                                   Identity ident, 
                                   IdentitySelector selector) throws GeneralException{

        MatchExpression exp = selector.getMatchExpression();
        if (exp != null) 
            buildItems(ctx, details, left, ident, exp);
        else {
            Rule rule = selector.getRule();
            if (rule != null)
                buildItems(details, left, rule.getSource());
            else {
                Script script = selector.getScript();
                if (script != null)
                    buildItems(details, left, script.getSource());
                else {
                    // can't use this with selectors that use populations
                    // or compound filters
                    log.warn("Unable to analyze selector entitlements");
                }
            }
        }
    }

    /**
     * For each term in a match expression, check to see
     * if the identity has an attribute that satisfies that term
     * and build a representation of the matches.
     */
    static private void buildItems(SailPointContext ctx, ViolationDetails details,
                                   boolean left,
                                   Identity ident, 
                                   MatchExpression exp) throws GeneralException{

        List<MatchTerm> terms = exp.getTerms();
        if (terms != null) {
            for (MatchTerm term : terms)
                buildItem(ctx, details, left, ident, exp, term);
        }
    }

    /**
     * Look for an Identity or Link attribute that would
     * satisfy the MatchTerm. If found return an ItemSummary.
     *
     * !! This is too much like IdentitySelector$MatchExpression,
     * need to be doing a true evaluation and collecting
     * the IdentityItem side effects.
     */
    static private void buildItem(SailPointContext ctx, ViolationDetails details,
                                  boolean left,
                                  Identity ident, 
                                  MatchExpression exp, 
                                  MatchTerm term) throws GeneralException{

        String name = term.getName();
        String value = term.getValue();
        boolean match = false;

        // sanity check
        if (name != null && value != null) {

            if (log.isDebugEnabled())
                log.debug("Looking for " + name + " = " + value);

            Application app = term.getApplication();
            if (app == null)
                app = exp.getApplication();

            if (app == null) {
                log.debug("Looking in identity");
                // term applies to an identity attribute
                match = compare(value, ident.getAttribute(name));
            }
            else {
                IdentityService identSvc = new IdentityService(ctx);
                List<Link> links = identSvc.getLinks(ident, app);
                if (links == null || links.size() == 0) 
                    log.debug("Link not found");
                else {
                    log.debug("Looking in links");
                    for (int i = 0 ; i < links.size() && !match ; i++) {
                        Link link = links.get(i);

                        if (!term.isPermission()) {
                            Object v = link.getAttribute(name);
                            match = compare(value, v);
                        }
                        else {
                            // don't assume there is only one permission per target!
                            List<Permission> perms = link.getPermissions();
                            if (perms != null) {
                                for (Permission p : perms) {
                                    if (name.equals(p.getTarget()) &&
                                        p.hasRight(value)) {
                                        match = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!match)
                log.debug("no match");
            else {
                log.debug("match!");
                String appname;
                if (app != null)
                    appname = app.getName();
                else {
                    // probably should be getting this from the 
                    // catalog?
                    appname = "IIQ";
                }
                
                IdentityItem item = new IdentityItem(); 
                item.setApplication(appname);
                item.setPermission(term.isPermission());
                item.setName(name);
                item.setValue(value);
                if (left)
                    details.addLeft(item);
                else
                    details.addRight(item);
            }
        }
        List<MatchTerm> children = term.getChildren();
        if(children != null) {
            for (MatchTerm child : children) {
                buildItem(ctx, details, left, ident, exp, child);
            }
        }
    }

    /**
     * Compare an attribute value from a Link or Identity with a selector value.
     */
    static private boolean compare(String value, Object ivalue) {

        boolean match = false;

        if (ivalue == null) {
            match = (value == null);
        }
        else if (ivalue instanceof List) {
            List l = (List)ivalue;
            match = l.contains(value);
        }
        else {
            match = ivalue.equals(value);
        }

        return match;
    }


    /**
     * Pragma used in rules/scripts that appear in one of the two
     * IdentitySelectors in an Entitlement SOD policy. Example:
     *
     * <pre>
     *   // @SOD application='AD' name='memberOf' value='Domain Admins'
     * </pre>
     */
    static public final String PRAGMA_SOD = "SOD";

    /**
     * Pragma used in advanced policy rules/scripts where one rule
     * checks both "sides".
     *
     * <pre>
     *   // @SODLeft application='AD' name='memberOf' value='Domain Admins'
     * </pre>
     */
    static public final String PRAGMA_SOD_LEFT = "SODLeft";

    /**
     * Pragma used in advanced policy rules/scripts where one rule
     * checks both "sides".
     *
     * <pre>
     *   // @SODRight application='AD' name='memberOf' value='Domain Admins'
     * </pre>
     */
    static public final String PRAGMA_SOD_RIGHT = "SODRight";


    /**
     * Argument for the SOD pragmas used to indicate the application.
     */
    static public final String ATT_APPLICATION = "application";

    /**
     * Argument for the SOD pragmas used to indicate whether the item is
     * a permission.  Should be true or false.
     */
    static public final String ATT_PERMISSION = "permission";

    /**
     * Argument for the SOD pragmas used to indicate the attribute name.
     */
    static public final String ATT_NAME = "name";

    /**
     * Argument for the SOD pragmas used to indicate the attribute value.
     */
    static public final String ATT_VALUE = "value";


    /**
     * Build items by looking for pragmas in a rule or script
     * source string. This one is interestin in that if the source
     * uses the SODLeft or SODRight pragmas those determine
     * which side of the ViolationDetails they go on and ignore
     * the "left" flag passed down.
     */
    static private void buildItems(ViolationDetails details,
                                   boolean left,
                                   String source) {

        if (source != null) {

            // look for non-sided pragmas
            List<IdentityItem> items = parsePragmas(source, PRAGMA_SOD);
            if (items != null) {
                if (left)
                    details.addLeft(items);
                else
                    details.addRight(items);
            }

            // then for sided pragmas
            items = parsePragmas(source, PRAGMA_SOD_LEFT);
            if (items != null)
                details.addLeft(items);

            items = parsePragmas(source, PRAGMA_SOD_RIGHT);
            if (items != null)
                details.addRight(items);
        }
    }

    /**
     * Look for one or more SOD pragmas, and if found parse
     * and return them.
     */
    static private List<IdentityItem> parsePragmas(String source, String pragma) {

        List<IdentityItem> items = null;

        // pragmas are not prefix unique (e.g. @SOD and @SODleft)
        // there must be a space between the pragma name and the args
        // sigh, supporting a tab delimiter would be nice
        String token = "@" + pragma + " ";
        int psn = source.indexOf(token);
        int max = source.length();
        while (psn > 0) {
            int from = psn + token.length();
            int end = from + 1;
            while (end < max && source.charAt(end) != '\n')
                end++;

            if (end < max) {
                String remainder = source.substring(from, end);

                Map<String,String> atts = parsePragma(remainder);
                String appname = atts.get(ATT_APPLICATION);
                boolean permission = Util.otob(atts.get(ATT_PERMISSION));
                String name = atts.get(ATT_NAME);
                String value = atts.get(ATT_VALUE);
                                               
                if (name == null) {
                    log.error("Malformed pragma");
                }
                else {
                    IdentityItem item = new IdentityItem(); 
                    item.setApplication(appname);
                    item.setPermission(permission);
                    item.setName(name);
                    item.setValue(value);

                    if (items == null)
                        items = new ArrayList<IdentityItem>();
                    items.add(item);
                }

                psn = source.indexOf(token, end);
            }
            else {
                // overflow trying to find the end of a pragma line, stop
                psn = -1;
            }
        }

        return items;
    }

    /**
     * Parse a pragma line into a Map.
     * The line is expected to follow pseudo-XML attribute syntax:
     *   
     *     foo='x' bar='y'
     *
     * Character entities (&lt;, &quot) are not recognized.
     * Backslash is an escape character.
     */
    static public Map<String,String> parsePragma(String pragma) {

        Map<String,String> map = new HashMap<String,String>();
        
        int psn = 0;
        int max = pragma.length();
        while (psn < max) {
            psn = skipWhitespace(pragma, psn);
            int start = psn;
            while (psn < max && pragma.charAt(psn) != '=')
                psn++;
            if (psn >= max)
                log.error("Malformed pragma");
            else {
                String name = pragma.substring(start, psn);
                name = name.trim();
                psn = skipWhitespace(pragma, psn + 1);
                if (psn >= max)
                    log.error("Malformed pragma");
                else {
                    // allow ' " or unquoted with no spaces
                    char delim = pragma.charAt(psn);
                    if (delim != '\'' && delim != '"') 
                        delim = 0;
                    else
                        psn++;

                    start = psn;
                    while (psn < max) {
                        char ch = pragma.charAt(psn++);
                        if (delim != 0 && ch == delim) {
                            break;
                        }
                        else if (delim == 0 && Character.isWhitespace(ch)) {
                            break;
                        }
                        else if (ch == '\\') {
                            psn++;
                        }
                    }

                    String value = pragma.substring(start, psn);
                    
                    map.put(name, value);
                }
            }
        }

        return map;
    }
    
    static private int skipWhitespace(String string, int psn) {

        int len = string.length();
        while (psn < len && 
              Character.isWhitespace(string.charAt(psn)))
            psn++;

        return psn;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Entitlement Sources
    //
    // EXPERIMENTAL, NOT CURRENTLY USED
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given an EntitlementSummary calculated for an SOD violation try
     * to figure out which assigned role was responsible for
     * provisioning that entitlement.
     *
     * This is not ideal, but the alternative requires
     * a different form of policy executor.
     */
    static private void findEntitlementSources(SailPointContext context,
                                               Identity identity, 
                                               EntitlementSummary summary) 
        throws GeneralException {

        findEntitlementSources(context, identity, summary.left);
        findEntitlementSources(context, identity, summary.right);
    }

    static private void findEntitlementSources(SailPointContext context,
                                               Identity identity, 
                                               List<RoleSummary> roles)
        throws GeneralException {
        if (roles != null) {
            for (RoleSummary role : roles) {
                List<ApplicationSummary> apps = role.applications; 
                if (apps != null) {
                    for (ApplicationSummary app : apps) {
                        findEntitlementSources(context, identity, app.name, 
                                               app.attributes, false);
                        findEntitlementSources(context, identity, app.name,
                                               app.permissions, true);
                    }
                }
            }
        }
    }

    static private void findEntitlementSources(SailPointContext context,
                                               Identity identity, 
                                               String appname,
                                               List<ItemSummary> items,
                                               boolean permissions)
        throws GeneralException {

        if (items != null) {
            for (ItemSummary item : items) {
                Bundle source = null;
                List<Bundle> roles = identity.getAssignedRoles();
                if (roles != null) {
                    for (Bundle role : roles) {
                        source = findEntitlementSource(role, appname, item, permissions);
                        if (source != null)
                            break;
                    }
                }

                if (source != null)
                    item.source = source.getName();
            }
        }
    }

    static private Bundle findEntitlementSource(Bundle role,
                                                String appname,
                                                ItemSummary item,
                                                boolean permission)
        throws GeneralException {

        Bundle source = null;
        
        // first look locally
        ProvisioningPlan plan = role.getProvisioningPlan();
        if (plan != null) {
            if (hasEntitlement(plan, appname, item, permission))
                source = role;
        }
        else {
            // derived from profiles
            List<Profile> profiles = role.getProfiles();
            if (profiles != null ) {
                for (Profile p : profiles) {
                    Application app = p.getApplication();
                    if (app != null && 
                        appname.equals(app.getName()) &&
                        hasEntitlement(p, item, permission)) {
                        source = role;
                        break;
                    }
                    else if (role.isOrProfiles()) {
                        // we would only provision the first one
                        // so if it didn't have it we can stop
                        break;
                    }
                }
            }
        }

        // look at the requirements
        if (source == null) {
            List<Bundle> requirements = role.getRequirements();
            if (requirements != null) {
                for (Bundle required : requirements) {
                    source = findEntitlementSource(required, appname, item, permission);
                    if (source != null)
                        break;
                }
            }
        }

        // then the permits, policy checking will look at both
        // lists so we have to look at both here too
        if (source == null) {
            List<Bundle> permits = role.getPermits();
            if (permits != null) {
                for (Bundle permitted : permits) {
                    source = findEntitlementSource(permitted, appname, item, permission);
                    if (source != null)
                        break;
                }
            }
        }

        // then up the inheritance tree
        if (source == null) {
            List<Bundle> inheritance = role.getInheritance();
            if (inheritance != null) {
                for (Bundle inherited : inheritance) {
                    source = findEntitlementSource(inherited, appname, item, permission);
                    if (source != null)
                        break;
                }
            }
        }
                    
        return source;
    }

    /**
     * Return true if this provisioning plan contains an entitlement.
     */
    static private boolean hasEntitlement(ProvisioningPlan plan, String appname,
                                          ItemSummary item, boolean permission) {

        boolean hasit = false;

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                if (appname.equals(account.getApplication())) {
                    if (permission)
                        hasit = hasEntitlement(account.getPermissionRequests(), item);
                    else
                        hasit = hasEntitlement(account.getAttributeRequests(), item);
                }
                if (hasit)
                    break;
            }
        }

        return hasit;
    }

    static private boolean hasEntitlement(List requests, ItemSummary item) {

        boolean hasit = false;

        if (requests != null) {
            for (int i = 0 ; i < requests.size() && !hasit ; i++) {
                GenericRequest req = (GenericRequest)requests.get(i);
                String name = req.getName();
                if (name != null && name.equals(item.name)) {
                    List<String> need = item.values;
                    if (need != null) {
                        Object avail = req.getValue();
                        if (avail instanceof Collection) {
                            Collection c = (Collection)avail;
                            hasit = c.containsAll(need);
                        }
                        else if (avail != null && need.size() == 1) {
                            hasit = avail.equals(need.get(0));
                        }
                    }
                }
                if (hasit) 
                    break;
            }
        }
        return hasit;
    }

    static private boolean hasEntitlement(Profile profile, ItemSummary item, boolean permission) {

        boolean hasit = false;

        if (permission) {
        }
        else {
        }

        return hasit;
    }

}                                          
                                          
