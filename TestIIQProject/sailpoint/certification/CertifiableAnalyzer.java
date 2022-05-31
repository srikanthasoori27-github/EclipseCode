/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Collect the list of Certifiable objects from an Identity.
 *
 * Author: Jeff
 *
 * This is an amalgam of code copied from the following classes:
 *
 * Certificationer
 * BaseCertificationBuilder
 * BaseCertificationBuilder.BaseCertificationContext
 * BaseIdentityCertificationBuilder
 * BaseIdentityCertificationBuilder.BaseIdentityCertificationContext
 *
 * Control flow is still weird.  First the List<Certifiable> is created
 * based on the options in the CertificationDefinition.  Then the
 * caller checks for archiving of inactive identities and if so creates
 * an archive with the entire Certifiable list.  If not inactive it then
 * runs exclusion rules with an option to create an archive of excluded 
 * items.  Once we start adding more complex filtering, we may want
 * to bring that archiving down here since in theory we would have
 * to archive those too.
 *
 */

package sailpoint.certification;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.api.Explanator;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;

// try to remove dependency on this
import sailpoint.object.Resolver;
import sailpoint.web.identity.RoleDetectionUtil;
import sailpoint.web.identity.RoleAssignmentUtil;

import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Certifiable;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Entitlements;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.Schema;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class CertifiableAnalyzer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(CertifiableAnalyzer.class);

    SailPointContext _context;
    CertificationDefinition _definition;
    
    // jsl - decide if we still need these or if the role filter is enough
    // if we do keep the role list, simplify this, we don't need both
    // and move it into RoleSelector
    
    /**
     * Some sort of application filter from the CertificationDefinigion.  
     * We may replace this with more
     * flexible filtering from the new definition options.
     * Do we really need both a List<String> and a List<Application>?
     * Used in the creation of the Certifiables list
     */
    List<String> _includedApplicationIds;

    /**
     * Resolved list of Application objects built from _includedApplicationIds;
     */
    List<Application> _includedApplications;

    /**
     * Utility class that determines if a role should be included.
     */
    RoleSelector _roleSelector;

    /**
     * Utility class that determines if an entitlement should be included.
     */
    EntitlementSelector _entitlementSelector;

    /**
     * Resolved list of applications for target permissions. This is pulled out of the
     * targetPermissionFilter since it is actually filtering on the Link application
     */
    List<Application> _targetPermissionApplications;

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    public CertifiableAnalyzer(SailPointContext context,
                               CertificationDefinition def,
                               ApplicationCache appcache)
        throws GeneralException {
        
        _context = context;
        _definition = def;
        _roleSelector = new RoleSelector(context, def);

        // we don't actually use ApplicationCache, passed down to EntitlementSelector
        _entitlementSelector = new EntitlementSelector(context, def, appcache);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * BaseCertificationContext initializes this in the constructor.
     * Here we'll do it like most other caches.
     * jsl - try to get rid of the parallel lists, can't we just use 
     * List<Appliation>?
     */
    private List<String> getIncludedApplicationIds() {

        if (_includedApplicationIds == null) {
            _includedApplicationIds = ObjectUtil.convertToIds(_context, Application.class, _definition.getIncludedApplicationIds());
        }
                
        return _includedApplicationIds;
    }
    
    /**
     * Copied from BaseCertificationBuilder but modified to handle
     * the app cache in a different way.
     */
    private List<Application> getIncludedApplications() throws GeneralException{

        if (_includedApplications == null) {

            List<String> ids = getIncludedApplicationIds();
            if (!Util.isEmpty(ids)) {
                _includedApplications = _context.getObjects(Application.class,
                                                          new QueryOptions(Filter.in("id", ids)));
            }
        }

        return _includedApplications;
    }

    /**
     * Cache the resolved applications for target permissions to use when fetching links.
     */
    private List<Application> getTargetPermissionApplications() throws GeneralException {
        if (_targetPermissionApplications == null) {
            if (_definition.getTargetPermissionApplicationFilter() != null) {
                _targetPermissionApplications = _context.getObjects(Application.class, new QueryOptions(_definition.getTargetPermissionApplicationFilter()));
            }
        }

        return _targetPermissionApplications;
    }

    /**
     * Copied from BaseIdentityCertificationContext
     * 
     * Overridden so that we can add IIQ entitlements to the list of
     * Certifiables if these options are enabled.
     * 
     * Logical Filtering ( a.k.a Composite Filtering)
     *
     * 1) excludeBaseAppAccounts
     *    Exclude Composite Tier Entitlements
     *    When enabled entitlements from the tier applications
     *    will be filtered from the certification.
     *
     * 2) filterLogicalEntitlements 
     *    Excludes values that are in the logical managed entitlement list from the tier
     *    apps to avoid manager's (certifiers) from seeing the same entitlement twice.
     *
     */
    public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
        throws GeneralException {

        final String MeterName = "CertifiableAnalyizer: getCertifiables";
        List<Certifiable> certifiables = null;

        Meter.enter(MeterName);
        try {
            boolean filterLogicalEntitlements = _definition.isFilterLogicalEntitlements();
            // formerly super.getCertifiables which
            // calls up to BaseCertificationContext
            certifiables = BaseGetCertifiables(entity);
            
            if ( (_definition.isExcludeBaseAppAccounts()) ||
                 (filterLogicalEntitlements) ) {

                List<String> appIds = getIncludedApplicationIds();
                boolean allAppsIncluded = appIds == null || appIds.isEmpty();

                Identity identity = (Identity)entity;
                for (Iterator<Certifiable> iterator = certifiables.iterator(); iterator.hasNext();) {
                    Certifiable certifiable =  iterator.next();
                    Application app = null;
                    //We can have multiple composite links references another applications links.
                    List<Link> compositeLinks = null;

                    // Try and find a composite link which owns the link referenced by the certifiable.
                    // If found we can remove the certifiable from the cert. However, if the composite
                    // app is not included in the cert, the entitlement should not be removed.
                    if (certifiable instanceof EntitlementSnapshot){
                        EntitlementSnapshot snap = (EntitlementSnapshot)certifiable;
                        app = _context.getObjectByName(Application.class, snap.getApplication());
                        if (app != null) {
                            compositeLinks = identity.getOwningCompositeLinks(app, snap.getInstance(),
                                                                              snap.getNativeIdentity());
                        }

                    } else if (certifiable instanceof EntitlementGroup){
                        EntitlementGroup group = (EntitlementGroup)certifiable;
                        app = group.getApplication();
                        if (app != null) {
                            compositeLinks = identity.getOwningCompositeLinks(app, group.getInstance(),
                                                                              group.getNativeIdentity());
                        }
                    }

                    // This removes all entitlements from the tier application
                    if ( _definition.isExcludeBaseAppAccounts()) {
                        // Don't remove the link unless the composite application is inluded in the certification
                        if (!Util.isEmpty(compositeLinks)) {
                            boolean removeLink = false;
                            for (Link compositeLink  : compositeLinks) {
                                if (compositeLink != null && 
                                    (allAppsIncluded || appIds.contains(compositeLink.getApplication().getId()))) {
                                    removeLink = true;
                                    break;
                                }
                            }
                            if (removeLink) {
                                iterator.remove();
                                //done with this certifiable
                                continue;
                            }
                        }
                    }
                    
                    if ( ( filterLogicalEntitlements ) && ( app != null ) ) {
                        if (!Util.isEmpty(compositeLinks) ) {
                            for (Link compositeLink : compositeLinks) {
                                if (compositeLink != null) {
                                    // If this is a tier application filter all of the logical application's entitlements
                                    certifiable = filterLogicalEntitlements(compositeLink.getApplication(), certifiable, app.isLogical());
                                    if ( certifiable == null ) {
                                        iterator.remove();
                                        break;
                                    }
                                }
                            }
                        } 
                    }          
                }
            }

            final String MeterIdentityCertifiables = "getCertifiables: identity";
            Meter.enter(MeterIdentityCertifiables);
            try {
                certifiables.addAll(getIdentityCertifiables(entity));
            }
            finally {
                Meter.exit(MeterIdentityCertifiables);
            }
        }
        finally {
            Meter.exit(MeterName);
        }
        
        return certifiables;
    }

    /**
     * BaseIdentityCertificationContext
     * 
     * When we are dealing with Logical applications filter out the
     * entitlements that are specific to the logical application when
     * dealing with tier apps.  Additionally when dealing with logical
     * application filter out any entitlements that are not specific
     * to the logical application.
     */        
    private Certifiable filterLogicalEntitlements(Application logicalApp,
                                                  Certifiable certifiable, 
                                                  boolean isLogical) 
        throws GeneralException {          

        if (certifiable instanceof EntitlementGroup){ 
            EntitlementGroup group = (EntitlementGroup)certifiable;
            filterEntitlementValues(logicalApp, group, isLogical);
            if ( group.isEmpty() ) return null;
        }
        if (certifiable instanceof EntitlementSnapshot){
            EntitlementSnapshot snap = (EntitlementSnapshot)certifiable;
            filterEntitlementValues(logicalApp, snap, isLogical);
            if ( snap.isEmpty() ) return null;
        }
        return certifiable;
    }
    
    /**
     * BaseIdentityCertificationContext
     */
    private void filterEntitlementValues(Application logicalApp,
                                         Entitlements group,
                                         boolean isLogical) 
        throws GeneralException {

        if ( group != null ) {

            Attributes<String,Object> attrs = group.getAttributes();
            if ( attrs != null ) {
                List<String> attrNames = group.getAttributeNames();
                for ( String name : attrNames ) {
                    Object val = attrs.get(name);

                    // If the entitlements are not on a logical app, the
                    // name of the schema attribute may be different than
                    // the tier attribute.  Get the correct name of the
                    // attribute on the logical application so we can lookup
                    // the ManagedAttribute appropriately.
                    String logicalAttrName = name;
                    if (!isLogical) {
                        String appName = group.getApplicationName();
                        logicalAttrName =
                            getLogicalAttrName(logicalApp, appName, name);
                    }

                    if ( val instanceof String ) {
                        String strVal = (String)val;
                        boolean entitlementFound =
                            (Explanator.get(logicalApp, logicalAttrName, strVal) != null);
                        if ( ( !entitlementFound  ) && ( isLogical ) ) {
                            attrs.remove(name);
                        } else
                            if ( ( entitlementFound  ) && ( !isLogical ) ) {
                                attrs.remove(name);
                            }
                    } else {
                        List listVal = Util.asList(val);
                        if ( Util.size(listVal) > 0 ) {
                            List neuList = new ArrayList(listVal);
                            for ( Object o : listVal ) {
                                String l = o.toString();
                                boolean entitlementFound =
                                    (Explanator.get(logicalApp, logicalAttrName, l) != null);
                                if ( ( !entitlementFound  ) && ( isLogical ) ) {
                                    attrs.remove(name);
                                }
                                if ( ( entitlementFound  ) && ( !isLogical ) ) {
                                    attrs.remove(name);
                                }
                            }               
                            if ( neuList.size() != Util.size(listVal) ) {
                                attrs.put(name,neuList);
                            }
                        }
                    }
                }
            }
            List<Permission> perms = group.getPermissions();
            if ( Util.size(perms) > 0 ) {
                Iterator<Permission> it = perms.iterator();
                while  ( it.hasNext() ) {
                    Permission perm = it.next();
                    String target = perm.getTarget();
                    List<String> rights = perm.getRightsList();
                    if ( Util.size(rights) > 0 ) {
                        List<String> neuRights = new ArrayList<String>(rights);
                        for ( String right: rights ) {
                            // !! jsl - this used to pass the right but that didn't
                            // work and still doesn't, we only test
                            // existance of the target, so the same decision
                            // applies to all rights.  Assuming that's okay
                            // this logic could be simplfieid.
                            boolean permissionFound = (Explanator.get(logicalApp, target) != null);
                            if ( ( !permissionFound ) && ( isLogical ) ) {
                                neuRights.remove(right);
                            }
                            if ( ( permissionFound ) && ( !isLogical ) ) {
                                neuRights.remove(right);
                            }
                        }
                        if ( Util.size(neuRights) > 0 ) {
                            perm.setRights(neuRights);
                        } else {
                            it.remove();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * BaseIdentityCertificationBuilder
     * This would be better over in Application - jsl
     * 
     * Return the name of the schema attribute on the given logical
     * application that has the given source, or null if an attribute
     * with the given source cannot be found.
     */
    private String getLogicalAttrName(Application logicalApp,
                                      String sourceApp,
                                      String sourceAttrName) {
        String logicalAttrName = sourceAttrName;
            
        Schema schema = logicalApp.getAccountSchema();
        if (null != schema) {
            AttributeDefinition attrDef =
                schema.getAttributeBySource(sourceApp, sourceAttrName);
            if (null != attrDef) {
                logicalAttrName = attrDef.getName();
            }
        }
            
        return logicalAttrName;
    }
    
    /**
     * Copied from BaseCertificationBuilder.BaseCertificationContext
     *
     * Retrieves a list of certifiables for the given entity. Builder properties
     * are used to filter the appropriate type of certifiables to return. This base
     * implementation only returns certifiables which relate to an application in
     * the includedApplications property on the builder.
     *
     * @param entity AbstractCertifiableEntity from which to retrieve the Certifiable items.
     * @return List of Certifiables
     * @throws GeneralException
     */
    private List<Certifiable> BaseGetCertifiables(AbstractCertifiableEntity entity)
        throws GeneralException {

        return getCertifiables(entity, getIncludedApplications());
    }

    /**
     * Copied from BaseCertificationContext
     *
     * Get a list of Certifiable objects from the given entity.  If any
     * applications are specified, only bundles and additional entitlements
     * that have entitlements on the given apps are returned.
     *
     * Business roles, additional entitlements, and policy violations are
     * returned with this list only if the associated include flag is set on the
     * builder. The flags are includedApplications, includeBusinessRoles,
     * includeAdditionalEntitlements, and includePolicyViolations.
     *
     * @param  entity           The AbstractCertifiableEntity from which to retrieve the
     *                            Certifiable items.
     * @param  apps               The Applications to use to filter bundles
     *                            and additional entitlements.  If not
     *                            specified all bundles and additional
     *                            entitlements are returned.
     *
     * @return A list of Certifiable objects filtered by application from
     *         the given identity.
     */
    protected List<Certifiable> getCertifiables(AbstractCertifiableEntity entity,
                                                List<Application> apps)
        throws GeneralException {

        List<Certifiable> certifiables = new ArrayList<Certifiable>();
        if (null != entity) {
            boolean certifyAccounts = _definition.isCertifyAccounts();
            if (!certifyAccounts && _definition.isIncludeRoles()) {
                // detected roles
                // Get da rolez. if a detected role is permitted or required by one or
                // more assigned roles, we need to certify the assigned role(s) and ignore
                // the detected role. If there are no such assigned roles, the detected
                // role should be certified.
                    
                /**
                 * Detected Roles without an assignment ID are truly Detected without assignment, they need to 
                 * be certified individually
                 */
                final String MeterDetected = "getCertifiables: detected roles";
                Meter.enter(MeterDetected);
                try {
                    // jsl - would be nice if RoleSelector handled this 
                    Collection<RoleDetection> detectedRoles = entity.getRoleDetections(apps);
                    
                    //Add all detected Roles without assignmentId
                    if(!Util.isEmpty(detectedRoles)) {
                        for(RoleDetection detected : detectedRoles) {
                            //Need to test detected for assignmentId
                            if(Util.isNullOrEmpty(detected.getAssignmentIds())) {
                                // jsl - ugh, why the cloning shit?
                                Bundle b = RoleDetectionUtil.getClonedBundleFromRoleDetection(_context, detected, null);
                                if (b != null) {
                                    if (_roleSelector.isIncluded(b)) {
                                        certifiables.add(b);
                                    }
                                }
                            }
                        }
                    }
                }
                finally {
                    Meter.exit(MeterDetected);
                }

                /**
                 * RoleDetections with an assignmentId belong to a RoleAssignment. We will role these up into the
                 * RoleAssignment when certifying. 
                 */
                final String MeterAssigned = "getCertifiables: assigned roles";
                Meter.enter(MeterAssigned);
                try {
                    //Need to use RoleAssignments here
                    if (isIncludeAssignedRoles() && entity.getAssignedRoles() != null){
                        List<RoleAssignment> roleAssignments = entity.getRoleAssignments();
                        if (!Util.isEmpty(roleAssignments)) {
                            for (RoleAssignment assign : roleAssignments) {
                                if (!assign.isNegative()) {
                                    Bundle b = RoleAssignmentUtil.getClonedBundleFromRoleAssignment(_context, assign);
                                    if (b != null) {
                                        if (_roleSelector.isIncluded(b)) {
                                            certifiables.add(b);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                finally {
                    Meter.exit(MeterAssigned);
                }
                
            }

            // Add any additional entitlements.
            if (!certifyAccounts && _definition.isIncludeAdditionalEntitlements()) {
                final String MeterExceptions = "getCertifiables: exceptions";
                Meter.enter(MeterExceptions);
                try {
                    certifiables.addAll(getExceptionCertifiables(entity, apps));
                }
                finally {
                    Meter.exit(MeterExceptions);
                }
            }

            if (!certifyAccounts && _definition.isIncludeTargetPermissions()) {
                final String MeterTargetPermissions = "getCertifiables: targetPermissions";
                Meter.enter(MeterTargetPermissions);
                try {
                    certifiables.addAll(getTargetPermissionCertifiables(entity));
                }
                finally {
                    Meter.exit(MeterTargetPermissions);
                }
            }

            if (certifyAccounts) {
                final String MeterAccounts = "getCertifiables: accounts";
                Meter.enter(MeterAccounts);
                try {
                    certifiables.addAll(getAccountCertifiables(entity));
                }
                finally {
                    Meter.exit(MeterAccounts);
                }
            }

            if (!certifyAccounts && _definition.isCertifyEmptyAccounts()){
                final String MeterEmpty = "getCertifiables: empty accounts";
                Meter.enter(MeterEmpty);
                try {
                    certifiables.addAll(getEmptyAccountCertifiables(entity));
                }
                finally {
                    Meter.exit(MeterEmpty);
                }
            }

            if (_definition.isIncludePolicyViolations()) {
                final String MeterPolicy = "getCertifiables: policy violations";
                Meter.enter(MeterPolicy);
                try {
                    List<PolicyViolation> violations = null;

                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("identity", entity));
                    ops.add(Filter.eq("active", true));

                    violations = _context.getObjects(PolicyViolation.class, ops);
                    if (violations != null) {
                        for (PolicyViolation v : violations){

                            // Check relevant apps if we're filtering by apps.
                            if ((null != apps) && !apps.isEmpty()) {
                                List<Application> relevantApps = v.getRelevantApps(_context);

                                // If there are no relevant apps or none of the relevant
                                // apps are in the filter list, we won't include this.
                                if ((null == relevantApps) || relevantApps.isEmpty() ||
                                    !Util.containsAny(relevantApps, apps)) {
                                    continue;
                                }
                            }
                            
                            Policy p = v.getPolicy(_context);
                            // jsl - Policy should have some kind of isCertifiable
                            // method so we don't have to hard code types?
                            if (p != null && !p.isType(Policy.TYPE_ACTIVITY)) {
                                certifiables.add(v);
                            }
                        }
                    }
                }
                finally {
                    Meter.exit(MeterPolicy);
                }
            }
        }
        return certifiables;
    }

    /** 
     * BaseCertifictionContext has this hard coded to true, 
     * doesn't look like it comes from the definition. If not, then
     * take the conditional out.
     */
    public boolean isIncludeAssignedRoles() {
        return true;
    }

    /**
     * BaseCertificationContext
     *
     * Get the list of certifiables for the additional entitlement on the
     * given identity (possibly constrained to the given applications).  This
     * factors in the exception granularity to determine which certifiables
     * should be returned (application level, attribute level, value level).
     *
     * @param  entity  The Identity from which to retrieve the additional
     *                   entitlement certifiable items.
     * @param  apps      A possibly-null list of applications to use to
     *                   filter the list of certifiable items.
     *
     * @return A list of certifiables for the additional entitlement on the
     *         given identity.
     *
     * @throws GeneralException  If the exception granularity is unsupported.
     */
    public List<Certifiable> getExceptionCertifiables(AbstractCertifiableEntity entity,
                                                      List<Application> apps)
        throws GeneralException {
            
        List<Certifiable> certifiables = new ArrayList<Certifiable>();
        
        final String MeterName = "CertifiableAnalyzier: getExceptions";
        Meter.enter(MeterName);
        try {
            // djs: tmp disable until sourceRoles/indirect is worked out
            // jsl - allow in definition for testing
            boolean useEntitlementsTable = _definition.getAttributes().getBoolean(CertificationDefinition.ARG_USE_ENTITLEMENTS_TABLE);
            if ( useEntitlementsTable ) {            
                certifiables = createCertifiablesFromIdentityEntitlements(entity, apps);
            } else {
                // certifiables = createCertifiables(entity.getExceptions(apps));
                certifiables = createGroupCertifiables(getCombinedExceptions(entity, apps));
            }
        }
        finally {
            Meter.exit(MeterName);
        }
        
        return certifiables;
    }

    /**
     * BaseCertificationBuilder
     *
     * Prior to 7.2 Certifiables were created from the "exceptions" list
     * of the Identity and this included target permissions.  In 7.2 we removed
     * targetPermissions from the Links and they will no longer appear in the
     * exceptions list, you have to query for them.
     *
     * In 8.1 we removed target permission from here and handled them separately in
     * {@link #getTargetPermissionCertifiables(sailpoint.object.AbstractCertifiableEntity)}
     *
     * I see there was a start toward something similar with 
     * createCertifiablesFromIdentityEntitlements but it has always been
     * disabled.  If we ever decide to finish that we could remove this
     * since target permissions will be IdentityEntitlements.
     * jsl
     */
    private List<EntitlementGroup> getCombinedExceptions(AbstractCertifiableEntity entity,
                                                         List<Application> apps)
        throws GeneralException {

        List<EntitlementGroup> exceptions = new ArrayList<>();
        // clone the old list
        for (EntitlementGroup eg : Util.safeIterable(entity.getExceptions(apps))) {
            exceptions.add((EntitlementGroup) eg.deepCopy((Resolver) _context));
        }

        if (entity instanceof Identity) {
            Identity identity = (Identity) entity;

            // Create entitlement groups for any links that dont' already have them
            for (Link link : Util.iterate(identity.getLinks())) {
                EntitlementGroup group = null;

                // Try to find an existing group
                for (EntitlementGroup eg : Util.iterate(exceptions)) {
                    if (Util.nullSafeEq(eg.getApplication(), link.getApplication(), true) &&
                            Util.nullSafeEq(eg.getInstance(), link.getInstance(), true) &&
                            Util.nullSafeEq(eg.getNativeIdentity(), link.getNativeIdentity())) {
                        group = eg;
                    }
                }

                if (group != null) {
                    List<Permission> perms = group.getPermissions();
                    // Identiies that haven't been refreshed in awhile may still
                    // have these on the exception list, remove them
                    // MT Do we still need this?  Continue to purge here for posterity, but in a fancier way.
                    if (perms != null) {
                        perms.removeIf((permission -> (permission.getAggregationSource() != null)));
                    }
                }
            }
        }

        return exceptions;
    }

    /**
     * In 8.1 we separated target permissions from other "exceptions" for targeted certifications,
     * they can be optionally excluded or have their own filter including application links to check.
     *
     * Note we pass the filter when querying for TargetAssociations here, instead of checking after the fact.
     */
    private List<Certifiable> getTargetPermissionCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {

        List<Certifiable> targetPermissionCertifiables = new ArrayList<>();
        if (entity instanceof Identity) {
            Identity identity = (Identity) entity;
            Filter targetPermissionFilter = _definition.getTargetPermissionFilter();
            IdentityService svc = new IdentityService(_context);
            List<Link> links = svc.getLinks(identity, getTargetPermissionApplications(), null);
            for (Link link : Util.iterate(links)) {
                List<Permission> targetPermissions = ObjectUtil.getTargetPermissions(_context, link, null, targetPermissionFilter);
                if (!Util.isEmpty(targetPermissions)) {
                    EntitlementGroup group = new EntitlementGroup(link.getApplication(), link.getInstance(), link.getNativeIdentity(),
                            link.getDisplayName());
                    for (Permission permission : targetPermissions) {
                        targetPermissionCertifiables.addAll(createPermissionCertifiables(permission, group));
                    }
                }
            }
        }

        return targetPermissionCertifiables;
    }
    
    /**
     * Copied from BaseIdentityCertificationContext
     *
     * Get the list of Certifiables on the given Identity to certify the
     * IIQ entitlements (authorized scopes and capabilities) if either of
     * these options are enabled.
     */
    private List<Certifiable> getIdentityCertifiables(AbstractCertifiableEntity entity)
        throws GeneralException {
            
        if (!(entity instanceof Identity))
            throw new IllegalArgumentException("Only identities may be certified in this certification type.");
        Identity identity = (Identity) entity;

        List<EntitlementSnapshot> entitlements = new ArrayList<EntitlementSnapshot>();

        if (_definition.isIncludeCapabilities()) {
            Attributes<String,Object> attrs =
                identity.getCapabilityManager().createCapabilitiesAttributes();
            @SuppressWarnings("unchecked")
                List<String> capabilities = (List<String>) attrs.get(Certification.IIQ_ATTR_CAPABILITIES);
            if (!Util.isEmpty(capabilities)) {
                entitlements.add(new EntitlementSnapshot(Certification.IIQ_APPLICATION, null,
                                                         identity.getName(), identity.getName(), null, attrs));
            }
        }

        if (_definition.isIncludeScopes()) {
            Attributes<String,Object> attrs =
                identity.createEffectiveControlledScopesAttributes(_context.getConfiguration());
            if (!attrs.isEmpty()) {
                entitlements.add(new EntitlementSnapshot(Certification.IIQ_APPLICATION, null,
                                                         identity.getName(), identity.getName(), null, attrs));
            }
        }

        return createSnapshotCertifiables(entitlements);
    }

    /**
     * Create a certifiables list from an EntitlementSnapshot list.
     * Used only by getIdentityCertifiables.  This used to be merged with
     * createCertifiables(EntitlementGroup) but I separated them when identity 
     * filtering was added so we could make the code simpler without
     * having to worry about the Entitlements abstraction and differences
     * in modeling.  
     *
     * TODO: Any need to filter here, seems like it's all or nothing.
     */
    List<Certifiable> createSnapshotCertifiables(List<EntitlementSnapshot> ents)
        throws GeneralException {

        List<Certifiable> certifiables = new ArrayList<Certifiable>();

        if (ents != null) {
            // assume value granularity, use the old utility since we don't
            // have filtering on these yet
            List<Entitlements> split = EntitlementGroup.splitToValues(ents);
            certifiables.addAll(split);
        }

        return certifiables;
    }
    
    /**
     * BaseCertificationContext
     * 
     * Query the IdentityEntitlement table for the entitlements that should be 
     * certified.
     * 
     * TODO: Should this go into IdentityCertificationBuilder?
     */
    public List<Certifiable> createCertifiablesFromIdentityEntitlements(AbstractCertifiableEntity entity, List<Application> apps) 
        throws GeneralException {
            
        List<EntitlementGroup> entitlementGroups = new ArrayList<EntitlementGroup>();

        if ( entity != null ) { 
            // query and get the entitlements
            QueryOptions ops = new QueryOptions();
            ops.addOrdering("application", true);
            ops.addOrdering("nativeIdentity", true);
            ops.addOrdering("instance", true);                    
            ops.add(Filter.eq("identity.id", entity.getId()));
            if ( Util.size(apps) > 0 )
                ops.add(Filter.in("application",apps));
                
            // these are extras/additional entitlements they'll have a null sourceRoles
            // ops.add(Filter.isnull("sourceRoles"));
            // jsl - we don't have sourceRoles but we have a granted by flag
            ops.add(Filter.eq("grantedByRole", false));
                
            List<String> fields = Arrays.asList("application", "instance", "nativeIdentity", "displayName", "name", "value");
            Iterator<Object[]> rows = _context.search(IdentityEntitlement.class, ops, fields);
            if ( rows != null ) {
                Application currentApp = null;
                String currentAccount = null;
                String currentInstance = null;
                EntitlementGroup currentGroup = null;
                while ( rows.hasNext() ) {
                    Object[] row = rows.next();
                    Application app = (Application)row[0];
                    String instance = (String)row[1];
                    String nativeIdentity = (String)row[2];
                    String displayName = (String)row[3];
                    String att = (String)row[4];
                    String attValue = (String)row[5];                                    
                    if ( !Util.nullSafeEq(currentApp, app) || 
                         Util.nullSafeCompareTo(currentInstance, instance) != 0 ||
                         Util.nullSafeCompareTo(nativeIdentity, currentAccount) != 0 )  {

                        currentGroup = new EntitlementGroup();
                        currentGroup.setApplication(app);
                        currentGroup.setNativeIdentity(nativeIdentity);
                        currentGroup.setInstance(instance);

                        currentGroup.setDisplayName(displayName);
                        currentGroup.setAccountOnly(false);
                        entitlementGroups.add(currentGroup);

                        currentApp = app;
                        currentInstance = instance;
                        currentAccount = nativeIdentity;
                    }
                        
                    Attributes<String,Object> attrs = currentGroup.getAttributes();
                    if  ( attrs == null ) {
                        attrs = new Attributes<String,Object>();
                        currentGroup.setAttributes(attrs);
                        attrs.put(att, attValue);
                    } else {
                        Object val = attrs.get(att);
                        List<String> asList = Util.asList(val);
                        if ( asList == null ) 
                            asList = new ArrayList<String>();
                            
                        asList.add(attValue);
                        attrs.put(att, asList);
                    }
                }
            }  
        }
        return createGroupCertifiables(entitlementGroups);            
    }

    /**
     * Create a list of certifiables from the given list of entitlements.
     *
     * Formerly supported three entitlement granularities: application, attribute
     * and value.  This has apparently defaulted to value for some time and is no
     * longer in the UI so we're diverging from the old framework in how we build 
     * these.  Value granularity is assumed and we combine the splitting of the values
     * with the entitlement filters.
     */
    private List<Certifiable> createGroupCertifiables(List<EntitlementGroup> ents)
        throws GeneralException {

        List<Certifiable> certifiables = new ArrayList<Certifiable>();

        for (EntitlementGroup ent : Util.iterate(ents)) {
            
            Application app = ent.getApplication();

            // TODO: quick test on app before doing deep
            
            Attributes<String,Object> attrs = ent.getAttributes();
            if (attrs != null) {
                for (Map.Entry<String,Object> entry : attrs.entrySet()) {
                    String name = entry.getKey();
                    Object value = entry.getValue();
                    // jsl - guess null is not a certifiable?
                    if (value != null) {
                        if (value instanceof Iterable) {
                            for (Object o : (Iterable<?>) value) {
                                if (o != null) {
                                    addEntitlement(ent, app, name, o.toString(), certifiables);
                                }
                            }
                        }
                        else {
                            addEntitlement(ent, app, name, value.toString(), certifiables);
                        }
                    }
                }
            }

            // Not sure about filtering on permissions
            // we could do the hash based filtering
            List<Permission> perms = ent.getPermissions();
            for (Permission perm : Util.iterate(perms)) {
                if (_entitlementSelector.isIncluded(app, perm)) {
                    certifiables.addAll(createPermissionCertifiables(perm, ent));
                }
            }
        }

        return certifiables;
    }

    private List<Certifiable> createPermissionCertifiables(Permission perm, EntitlementGroup ent) {
        List<Certifiable> certifiables = new ArrayList<>();
        String target = perm.getTarget();
        Map<String,Object> permAttributes = perm.getAttributes();
        for (String right : Util.iterate(perm.getRightsList())) {
            Permission newp = new Permission(right, target);
            // shallow copy is enough
            newp.setAttributes(permAttributes);
            List<Permission> newPerms = new ArrayList<Permission>();
            newPerms.add(newp);
            certifiables.add(ent.create(newPerms, null));
        }

        return certifiables;
    }

    /**
     * Helper for createCertifiables.
     * Check the EntitlementSelector then add a certifiable for one attribute.
     */
    private void addEntitlement(EntitlementGroup src, 
                                Application app, String name, String value,
                                List<Certifiable> certifiables)
        throws GeneralException {

        if (_entitlementSelector.isIncluded(app, name, value)) {
            Attributes<String,Object> atts = new Attributes<String,Object>();
            atts.put(name, value);
            certifiables.add(src.create(null, atts));
        }
    }

    /**
     * BaseCertificationContext
     */
    public List<Certifiable> getAccountCertifiables(AbstractCertifiableEntity entity)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity.id", entity.getId()));
        if (_definition.getAccountFilter() != null) {
            ops.add(_definition.getAccountFilter());
        }
        return createAccountCertifiables(ops);
    }
    
    /**
     * BaseCertificationContext
     */
    public List<Certifiable> getEmptyAccountCertifiables(AbstractCertifiableEntity entity)
        throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity.id", entity.getId()));
        ops.add(Filter.eq("entitlements", false));
        if (_definition.getAccountFilter() != null) {
            ops.add(_definition.getAccountFilter());
        }
        return createAccountCertifiables(ops);
    }
    
    /**
     * BaseCertificationContext
     */
    private List<Certifiable> createAccountCertifiables(QueryOptions ops) throws GeneralException{
        List<Certifiable> exps = new ArrayList<Certifiable>();
        List<String> fields = Arrays.asList("application", "instance", "nativeIdentity", "displayName");
        Iterator<Object[]> accounts = _context.search(Link.class, ops, fields);
        while(accounts.hasNext()){
            Object[] account = accounts.next();
            Application app = (Application)account[0];
            String instance = (String)account[1];
            String nativeIdentity = (String)account[2];
            String displayName = (String)account[3];
            EntitlementGroup grp = new EntitlementGroup(app, instance, nativeIdentity, displayName);
            exps.add(grp);
        }
        return exps;
    }


}
