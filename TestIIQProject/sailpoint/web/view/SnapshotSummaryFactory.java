package sailpoint.web.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.AccountGroupService;
import sailpoint.api.Explanator;
import sailpoint.api.Iconifier;
import sailpoint.api.Iconifier.Icon;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * Converts EntitlementSnapshot into a summary object which ca
 * easily be displayed in the UI.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class SnapshotSummaryFactory {

    private SailPointContext context;
    private Locale locale;
    private AccountGroupService accountGrpSvc;
    private Iconifier iconifier;

    public SnapshotSummaryFactory(SailPointContext context, Locale locale) {
        this.context = context;
        this.locale = locale;
        accountGrpSvc = new AccountGroupService(context);
    }

    /**
     * Convert the snapshot into a UI-friendly summary.
     *
     * @param snapshot The EntitlementSnapshot to convert.
     * @param extendedValues Map of extended link values. Keys should be extended1, extended2, etc
     */

    public SnapshotSummary getSummary(CertificationItem item, EntitlementSnapshot snapshot, Map<String, Object> extendedValues) throws GeneralException{
        SnapshotSummary summary = new SnapshotSummary(snapshot);
        summary.setAccountIcons(this.getAccountIcons(extendedValues));

        Application app = context.getObjectByName(Application.class, summary.getApplication());

        if (snapshot.getAttributeNames() != null){

            for(String attr : snapshot.getAttributeNames()){
                SnapshotSummary.Entitlement ent = new SnapshotSummary.Entitlement(attr,
                        accountGrpSvc.isGroupAttribute(summary.getApplication(), attr));

                Object attrValue = snapshot.getAttributes().get(attr);
                List values = null;
                if (attrValue instanceof List){
                    values = (List)attrValue;
                } else {
                    values = Arrays.asList(attrValue);
                }

                for(Object value : values){
                    if (value == null) {
                        continue;
                    }

                    Explanator.Explanation exp = Explanator.get(app, attr, value.toString());
                    if (exp == null) {
                        ent.addValue(value.toString(), value.toString(),  null);
                    }
                    else {
                        ent.addValue(value.toString(), exp.getDisplayValue(),  exp.getDescription(locale));
                    }
                }

                summary.addAttribute(ent);
            }
        }

        if (snapshot.getPermissions() != null){
            for(Permission permission : snapshot.getPermissions()){
                String targetDesc = Explanator.getPermissionDescription(app, permission.getTarget(), locale);
                SnapshotSummary.Entitlement ent = new SnapshotSummary.Entitlement(permission.getTarget(), targetDesc);
                for(String right : permission.getRightsList()){
                    ent.addValue(right, right, null);
                }
                summary.addPermissions(ent);
            }
        }
        
        summary.setIsNew(calculateIsNewEntitlement(item));

        // Look for violations that include this entitlement.
        // Todo: If the entity has a lot of violations on the same application this
        // could cause performance problems. We potentially need to flatten the
        // list of roles/entitlements referenced by an item so it's searchable
        List<String> violations = new ArrayList<String>();
        QueryOptions ops = new QueryOptions(Filter.eq("type", CertificationItem.Type.PolicyViolation));
        ops.add(Filter.eq("parent.id", item.getCertificationEntity().getId()));
        ops.add(Filter.containsAll("applicationNames", Arrays.asList(item.getExceptionApplication())));
        Iterator<Object[]> violationItems = context.search(CertificationItem.class, ops,
                Arrays.asList("policyViolation"));
        while(violationItems.hasNext()){
            PolicyViolation violation = (PolicyViolation)violationItems.next()[0];
            if (violation.getViolatingEntitlements() != null){
                for(IdentitySelector.MatchTerm term : violation.getViolatingEntitlements()){
                    if (matchTerm(term, item.getExceptionEntitlements())){
                        violations.add(violation.getDisplayableName());
                    }
                }
            }
        }

        summary.setRelatedViolations(violations);

        return summary;
    }

    private boolean matchTerm(IdentitySelector.MatchTerm term, EntitlementSnapshot snapshot) throws GeneralException{

        if (term.getApplication() != null && snapshot.getApplication().equals(term.getApplication().getName())){
             if (snapshot.isValueGranularity()){
                if (term.isPermission()){
                    return snapshot.getPermissionTarget() != null && snapshot.getPermissionTarget().equals(term.getName())
                            && snapshot.getPermissionRight() != null && snapshot.getPermissionRight().equals(term.getValue());
                } else {
                    return snapshot.getAttributeName() != null && snapshot.getAttributeName().equals(term.getName())
                            && snapshot.getAttributeValue() != null && snapshot.getAttributeValue().equals(term.getValue());
                }
            }
        }

        return false;
    }
    
    /**
     * Calculates whether or not the specified CertificationItem is a new entitlement.
     * @param item The certification item.
     * @return True if the item is new, false otherwise.
     * @throws GeneralException
     */
    private boolean calculateIsNewEntitlement(CertificationItem item)
    	throws GeneralException
    {
    	Map<String, Map<String,Boolean>> newAttrs = new HashMap<String,Map<String,Boolean>>();
    	Map<String, Map<String,Boolean>> newPerms = new HashMap<String,Map<String,Boolean>>();
    	
        IdentityDifference idDiff = item.getParent().getDifferences();
        if (null != idDiff) {
            item.calculateExceptionDifferences(idDiff, newAttrs, newPerms);
        }

        return !newAttrs.isEmpty() || !newPerms.isEmpty();
    }

    /**
     * Gets a List of AccountIcons relevant to the given account
     * @param extendedValues Map of extended link values. Keys should be extended1, extended2, etc
     */
    private List<Icon> getAccountIcons(Map<String, Object> extendedValues)
        throws GeneralException {

        if (null == this.iconifier) {
            this.iconifier = new Iconifier();
        }
        return this.iconifier.getAccountIcons(extendedValues);
    }
    
    /**
     * Return the extended attribute properties used by account icons.
     */
    public List<String> getAccountIconExtendedProperties() {
        if (null == this.iconifier) {
            this.iconifier = new Iconifier();
        }
        return this.iconifier.getExtendedAccountAttributeProperties();
    }
}
