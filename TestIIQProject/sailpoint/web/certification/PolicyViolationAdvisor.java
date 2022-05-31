package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Helper class to give advice around how to handle remediations of policy
 * violations and that allows selecting which business roles that constitute a
 * policy violation should be removed.
 * 
 * @author matt.tucker
 *
 */
public class PolicyViolationAdvisor {
    private static final Log log = LogFactory.getLog(PolicyViolationAdvisor.class);
    
    private PolicyViolation policyViolation;
    private SailPointContext context;
    private Locale locale;
    private PolicyTreeNode entitlementViolations;
    
    private boolean entitlementViolationsInitialized = false;
    
    public PolicyViolationAdvisor(SailPointContext context, PolicyViolation violation, Locale locale)
            throws GeneralException {
        this.policyViolation = violation;
        this.context = context;
        this.locale = locale;
    }
    
    public PolicyViolation getPolicyViolation() {
        return this.policyViolation;
    }

    public PolicyTreeNode getEntitlementViolations(String certEntityId, boolean includeClassifications) throws GeneralException {
        if( !entitlementViolationsInitialized ) {
            Policy policy = null;
            try {
                if (getPolicyViolation() != null) {
                    policy = getPolicyViolation().getPolicy( context );
                }
            } catch ( GeneralException e ) {
                throw new RuntimeException( "Unable to get Policy: " + getPolicyViolation().getPolicyId(), e );
            }
            if (policy == null) {
                return null;
            }

            // Create the violations.
            entitlementViolations = createEntitlementViolations( policy, policyViolation, policyViolation.getIdentity() );

            // This will be null if this is not an entitlement SoD violation.
            if (null != this.entitlementViolations) {
                // Decorate them with all kinds of good stuff.
                if (null != certEntityId) {
                    List<EntitlementStatus> statuses = getState(certEntityId, entitlementViolations);
                    populateNodeState(entitlementViolations, statuses);
                }
                populateDisplayValueAndClassifications(entitlementViolations, includeClassifications);
                populateDescription(entitlementViolations);
            }

            entitlementViolationsInitialized  = true;
        }
        return entitlementViolations;
    }

    private void populateDisplayValueAndClassifications(PolicyTreeNode node, boolean includeClassifications)
    {
        Filter filters;
        QueryOptions queryOptions;
        List<ManagedAttribute> attributes;

        //Update displayValue is node is a leaf node
        if(node.isLeaf())
        {
          	filters = Filter.eq( "application.id", node.getApplicationId() );
            filters = Filter.and( filters, Filter.eq( "attribute", node.getName() ) );
            if( node.isPermission() ) {
            	filters = Filter.and( filters, Filter.eq( "type", ManagedAttribute.Type.Permission.name() ) );
            } else {
                filters = Filter.and( filters, Filter.ne( "type", ManagedAttribute.Type.Permission.name() ) );
                filters = Filter.and( filters, Filter.eq( "value", node.getValue() ) );
            }
            queryOptions = new QueryOptions( filters );
            try {
                attributes = SailPointFactory.getCurrentContext().getObjects( ManagedAttribute.class, queryOptions );
                if( attributes != null && attributes.size() == 1 ) {
                     ManagedAttribute attribute = attributes.get( 0 );
                     node.setDisplayValue(attribute.getDisplayName());
                     if (includeClassifications) {
                         node.setClassificationNames(attribute.getClassificationDisplayNames());
                     }
                }
            } catch ( GeneralException e ) {
                log.error(e.getMessage(), e);
            }
       }
       if (node.getChildCount() > 0){
            for (PolicyTreeNode child : node.getChildren()) {
                populateDisplayValueAndClassifications(child, includeClassifications);
            }
       }
    }

    private void populateDescription(PolicyTreeNode node) {
        if (node.isLeaf()) {
            if ((null != node.getApplicationId()) && (null != node.getName()) && (null != node.getValue())) {
                String description =
                    Explanator.getDescription(node.getApplicationId(), node.getName(), node.getValue(), locale);
                node.setDescription(description);
            }
        }
        else {
            for (PolicyTreeNode child : Util.iterate(node.getChildren())) {
                populateDescription(child);
            }
        }
    }

    private void populateNodeState(PolicyTreeNode node, final List<EntitlementStatus> statuses){

        List<EntitlementStatus> matches = new ArrayList<EntitlementStatus>();
        if (node.getApplication() != null){
            for(EntitlementStatus status : statuses){
                if (status.match(node)){
                    matches.add(status);
                }
            }
        }

        for(EntitlementStatus match : matches){
            node.addStatus(new PolicyTreeNode.PolicyTreeNodeState(match.getItemId(), match.getEntityId(),
                    match.getStatus()));
        }

        if (node.getChildren() != null){
            for(PolicyTreeNode child : node.getChildren()){
                populateNodeState(child, statuses);
            }
        }
    }

    private List<EntitlementStatus> getState(String certificationEntityId,
                                                        PolicyTreeNode node) throws GeneralException{

        Set<String> appSet = node.getApplicationNames(new HashSet<String>());

        QueryOptions ops = new QueryOptions(Filter.eq("parent.id", certificationEntityId));
        ops.add(Filter.in("exceptionApplication", new ArrayList<String>(appSet)));
        List<String> fields = Arrays.asList("id", "action.status", "exceptionApplication",
                "exceptionAttributeName", "exceptionPermissionTarget", "exceptionEntitlements");

        Iterator<Object[]> results = context.search(CertificationItem.class, ops, fields);
        List<EntitlementStatus> statuses = new ArrayList<EntitlementStatus>();
        while(results.hasNext()){
            Object[] next = results.next();
            CertificationAction.Status action = (CertificationAction.Status)next[1];
            if (CertificationAction.Status.Cleared.equals(action)) {
                action = null;
            }
            statuses.add(new EntitlementStatus((String)next[0], certificationEntityId, action, (String)next[2],
                    (String)next[3], (String)next[4], (EntitlementSnapshot)next[5]));
        }

        return statuses;
    }

    public static class EntitlementStatus{

        String itemId;
        String entityId;
        CertificationAction.Status status;
        String application;
        String attribute;
        String permissionTarget;
        EntitlementSnapshot snapshot;

        public EntitlementStatus(String itemId, String entityId, CertificationAction.Status status, String application,
                               String attribute, String permissionTarget, EntitlementSnapshot snapshot) {
            this.itemId = itemId;
            this.entityId = entityId;
            this.status = status;
            this.application = application;
            this.attribute = attribute;
            this.permissionTarget = permissionTarget;
            this.snapshot = snapshot;
        }
        
        public boolean match(PolicyTreeNode node){

            if (!application.equals(node.getApplication()))
                    return false;

            if (attribute != null && attribute.equals(node.getName())){
                Object val = snapshot.getAttributes() != null ?
                    snapshot.getAttributes().get(attribute) : null;

                if (val != null){
                    return val.equals(node.getValue());
                }
            } else if (permissionTarget != null && permissionTarget.equals(node.getName())){
                List<Permission> permissions = snapshot.getPermissionsByTarget(permissionTarget);

                if (!permissions.isEmpty()){
                    Permission perm = permissions.get(0);
                    return perm.getRights() != null && perm.getRights().equals(node.getValue());
                }
            }

            return false;
        }

        public String getItemId() {
            return itemId;
        }

        public CertificationAction.Status getStatus() {
            return status;
        }

        public String getApplication() {
            return application;
        }

        public String getAttribute() {
            return attribute;
        }

        public String getPermissionTarget() {
            return permissionTarget;
        }

        public EntitlementSnapshot getSnapshot() {
            return snapshot;
        }

        public String getEntityId() {
            return entityId;
        }
    }



    private PolicyTreeNode createEntitlementViolations( Policy policy, PolicyViolation violation, Identity identity ) throws GeneralException {
        /* if not an entitlement sod violation or effective entitlement sod return no results */
        if( !Policy.TYPE_ENTITLEMENT_SOD.equals( policy.getType() ) && !Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD.equals(policy.getType())) {
            return null;
        }

        /* Fetch constraints that are in violation from policy */
        GenericConstraint constraint = ( GenericConstraint ) policy.getConstraint( policyViolation );

        List<MatchTerm> matches = violation.getViolatingEntitlements();
        if (matches == null && constraint != null){

            // We should be able to get the entitlements from the violation, if
            // not use legacy method.

            /* Process selectors of violated constraint to get violated match terms */
            matches = new ArrayList<MatchTerm>();
            for( IdentitySelector selector : constraint.getSelectors() ) {
                try {
                    Matchmaker matchMaker = new Matchmaker(context);
                    Matchmaker.ExpressionMatcher expMatcher = matchMaker.new ExpressionMatcher(selector.getMatchExpression());
                    matches.addAll(expMatcher.getMatches(identity));
                } catch ( GeneralException e ) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        PolicyTreeNode violationTree = null;
        if (constraint != null){
            PolicyTreeNode policyTree = PolicyTreeNodeFactory.create( constraint );
            List<ViolationSource> violationSources = new ArrayList<ViolationSource>( 2 );
            violationSources.add( ViolationSourceFactory.create( MatchTerm.class, matches ) );
            violationSources.add( ViolationSourceFactory.create( PolicyTreeNode.class, policyViolation.getEntitlementsToRemediate() ) );
            ViolationSource metaViolationSource = ViolationSourceFactory.create( ViolationSource.class, violationSources );
            violationTree = ViolationTreeFactory.createViolationTree( policyTree, metaViolationSource );
        }

        return violationTree;
    }

    public List<Bundle> getLeftBusinessRoles() throws GeneralException {
        return this.policyViolation == null ? null : this.policyViolation.getLeftBundles(context);
    }
    
    public List<Bundle> getRightBusinessRoles() throws GeneralException {
        return this.policyViolation == null ? null : this.policyViolation.getRightBundles(context);
    }
    
    public boolean isBusinessRoleSelected(Bundle bundle) throws GeneralException {
        return this.policyViolation == null ? false : this.policyViolation.isMarkedForRemediation(bundle);
    }
}