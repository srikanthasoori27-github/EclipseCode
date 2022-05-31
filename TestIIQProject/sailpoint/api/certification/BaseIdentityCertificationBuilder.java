/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CachedManagedAttributer;
import sailpoint.api.Differencer;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Explanator;
import sailpoint.api.IdentityArchiver;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Entitlements;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Schema;
import sailpoint.recommender.IdentityBatchRequestBuilder;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public abstract class BaseIdentityCertificationBuilder extends BaseCertificationBuilder {
    
    private static final Log log = LogFactory.getLog(BaseIdentityCertificationBuilder.class);
    
    /**
     * The correlator is used to derive the specific attributes and
     * permissions held by an identity that caused it to be assigned
     * to a bundle.
     */
    private EntitlementCorrelator correlator;

    /**
     * The archiver is used to make IdentitySnapshots that capture
     * the state of an Identity at the moment it was prepared for
     * certificdation.
     */
    private IdentityArchiver archiver;
    
    /**
     * 
     * The entitlements mapping returned from the EntitlementCorrelator.
     * 
     */
    Map<Bundle, List<EntitlementGroup>> _corrlelatedEntitlements;
    
    /**
     * Caching the entitlements for a each identity to prevent calling
     * for each role.
     */
    String _lastIdentityId;
    
    protected BaseIdentityCertificationBuilder(SailPointContext ctx, CertificationDefinition definition) {

        super(ctx, definition);
        
        initialize(null);
    }

    protected BaseIdentityCertificationBuilder(SailPointContext ctx, CertificationDefinition definition, EntitlementCorrelator correlator) {
        
        super(ctx, definition);
        
        initialize(correlator);
    }

    
    private void initialize(EntitlementCorrelator correlator) {
        
        if (correlator == null) {
            initializeCorrelator();
        }
        else {
            this.correlator = correlator;
        }

        // if you make one of these without a correlator, 
        // it will also use the cache in the Identity
        archiver = new IdentityArchiver(context, correlator);
    }

    private void initializeCorrelator() {
        
        boolean useEntitlementMappingCache = false;
        try {
            Configuration syscon = context.getConfiguration();
            useEntitlementMappingCache = syscon.getBoolean("useEntitlementMappingCache");
        }
        catch (Throwable t) {}

        if (!useEntitlementMappingCache) {

            // since these use each other, construct them now
            correlator = new EntitlementCorrelator(context);

            // let this load things now so we can clean up the metering
            try {
                correlator.prepare();
            }
            catch (GeneralException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
    
    /**
     * Used to create UI messages which describe the type of entity
     * being certified.
     *
     * @param plural Whether or not the entity name should be plural
     * @return The name of the entity type being certified.
     */
    public Message getEntityName(boolean plural){
        String key = plural ? MessageKeys.USERS_LCASE : MessageKeys.USER_LCASE; 
        return new Message(key);
    }

    public abstract class BaseIdentityCertificationContext extends BaseCertificationContext {

        /**
         * Builder to create recommendation requests for identity certs.
         */
        private IdentityBatchRequestBuilder identityBatchRequestBuilder;
        private CachedManagedAttributer cachedManagedAttributer;
        private Boolean includeClassifications;

        protected BaseIdentityCertificationContext(SailPointContext context, CertificationDefinition definition,
                                                   List<Identity> owners) {
            super(context, definition, owners);

            cachedManagedAttributer = new CachedManagedAttributer(context);
        }

        /**
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
        @Override
        public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {

            boolean filterLogicalEntitlements = definition.isFilterLogicalEntitlements();
            List<Certifiable> certifiables = super.getCertifiables(entity);
            if ( (definition.isExcludeBaseAppAccounts()) ||
                 (filterLogicalEntitlements) ) {

                List<String> appIds = this.getIncludedApplicationIds();
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
                        app = context.getObjectByName(Application.class, snap.getApplication());
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
                    if ( definition.isExcludeBaseAppAccounts()) {
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
            certifiables.addAll(getIdentityCertifiables(entity));
            return certifiables;
        }

        /**
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
         * Get the list of Certifiables on the given Identity to certify the
         * IIQ entitlements (authorized scopes and capabilities) if either of
         * these options are enabled.
         */
        private List<Certifiable> getIdentityCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {
            
            if (!(entity instanceof Identity))
                throw new IllegalArgumentException("Only identities may be certified in this certification type.");
            Identity identity = (Identity) entity;

            List<Entitlements> entitlements = new ArrayList<Entitlements>();

            if (definition.isIncludeCapabilities()) {
                Attributes<String,Object> attrs =
                    identity.getCapabilityManager().createCapabilitiesAttributes();
                @SuppressWarnings("unchecked")
                List<String> capabilities = (List<String>) attrs.get(Certification.IIQ_ATTR_CAPABILITIES);
                if (!Util.isEmpty(capabilities)) {
                    entitlements.add(new EntitlementSnapshot(Certification.IIQ_APPLICATION, null,
                                                             identity.getName(), identity.getName(), null, attrs));
                }
            }

            if (definition.isIncludeScopes()) {
                Attributes<String,Object> attrs =
                    identity.createEffectiveControlledScopesAttributes(context.getConfiguration());
                if (!attrs.isEmpty()) {
                    entitlements.add(new EntitlementSnapshot(Certification.IIQ_APPLICATION, null,
                                                             identity.getName(), identity.getName(), null, attrs));
                }
            }

            return super.createCertifiables(entitlements);
        }

        protected CertificationItem createCertificationItemInternal(Certification cert,
                                                                    Certifiable certifiable,
                                                                    AbstractCertifiableEntity entity)
            throws GeneralException {
            
            if (!(entity instanceof Identity))
                throw new IllegalArgumentException("Only identities may be certified in this certification type.");
            
            return createCertificationItemInternal(cert, certifiable, (Identity) entity);
        }


        /**
         * Create a CertificationItem for the given Certifiable object.
         *
         * @param cert        The Certification to which we're adding the item.
         * @param certifiable The Certifiable for which to create the item.
         * @param identity    The Identity on which the Certifiable was found.
         * @return A CertificationItem created from the given Certifiable object.
         */
        private CertificationItem createCertificationItemInternal(Certification cert,
                                                                  Certifiable certifiable,
                                                                  Identity identity)
            throws GeneralException {
            
            CertificationItem item = null;
            List<String> classifications = null;
            
            if (certifiable instanceof Bundle) {

                List<EntitlementGroup> entitlements;
                Bundle b = (Bundle) certifiable;
                String assignmentId = b.getAssignmentId();
                RoleAssignment roleAssignment = null;
                if (assignmentId != null) {
                    //RoleAssignment. All RoleDetections with AssignmentId's 
                    //will be rolled up into the corresponding RoleAssignment
                    roleAssignment = identity.getRoleAssignmentById(assignmentId);
                    entitlements = getEntitlementMapping(identity, roleAssignment, null);
                } else {
                    //RoleDetection without AssignmentID
                    entitlements = getEntitlementMapping(identity, null, (Bundle)certifiable);
                }
                item = new CertificationItem(b, entitlements,
                        identity.getAssignedRole(b.getId()) != null ? CertificationItem.SubType.AssignedRole: null);

                if (assignmentId != null) {
                    item.setRoleAssignment(roleAssignment);
                } else {
                    item.setRoleDetection(identity.getUnassignedRoleDetection(b));
                }

                if (isIncludeClassifications() && b.getClassifications() != null) {
                    classifications = b.getClassificationNames();
                }
            } else if (certifiable instanceof Entitlements) {
                Entitlements entitlements = (Entitlements) certifiable;
                item = new CertificationItem(entitlements, 
                                             cert.getEntitlementGranularity());
                assimilateLinkAttributes(identity, entitlements, item);
                if (isIncludeClassifications()) {
                    classifications = getEntitlementClassifications(item);
                }
            } else if (certifiable instanceof PolicyViolation) {
                
                // A local copy  will be kept in the CertificationItem so we can
                // be independent of the Interrogator.  Hmm, you won't be able to
                // search for these but if we leave them as references we'll have
                // to play the "convert references to inline" dance during archive
                // and have to carefully coordinate with Interrogator so it won't
                // delete these out from under us.
                
                item = new CertificationItem((PolicyViolation) certifiable);
            } else {
                throw new IllegalArgumentException("Unknown certifiable type on " +
                                                   identity.getName() + ": " + certifiable);
            }

            if (classifications != null) {
                item.setClassificationNames(classifications);
            }

            return item;
        }
        
        /**
         * Create an CertificationEntity for an Identity - the super class will
         * add the certifiables.
         * <p/>
         * To provide a stable basis for comparison between certifications
         * we must force the generation of an IdentitySnahspot if any changes
         * have been made since the last one.
         *
         * @param  cert      The Certification for which we're creating the identity.
         * @param  entity    The certifiable for which to create a CertificationEntity.
         * @param  snapshot  Whether to create a snapshot.
         */
        protected CertificationEntity createCertificationEntityInternal(Certification cert,
                                                                        AbstractCertifiableEntity entity,
                                                                        boolean snapshot)
            throws GeneralException {
            
            assert(entity != null);
            
            if (!Identity.class.isAssignableFrom(entity.getClass()))
                throw new RuntimeException("Could not create an identity certification entity with class of type '"
                                           + entity.getClass().getName() + "'");
            
            Identity identity = (Identity) entity;
            CertificationEntity certId = new CertificationEntity(identity);
            
            if (snapshot) {
                // create new snapshot if there were changes since the last one
                createSnapshot(identity, certId);
            }
            
            return certId;
        }
        
        /**
         * Create an identity snapshot that holds the current state
         * of an Identity, or locate the previous snapshot if no changes
         * were made since the last one.
         * <p/>
         * To compare the current identity with the last snapshot we have
         * to create a new snapshot since we can only difference two
         * snapshots.  We could avoid the memory overhead by writing
         * an Identity/IdentitySnapshot differencer, but this results in
         * redundant logic that has to be kept in sync with the snapshot
         * differencer.
         */
        private void createSnapshot(Identity id, CertificationEntity aid)
            throws GeneralException {
            
            Meter.enter(114, "IdentityCertificationBuilder: createSnapshot");
            
            // generate a new snapshot
            Meter.enter(115, "IdentityCertificationBuilder: new snapshot");
            IdentitySnapshot snap = archiver.createSnapshot(id);
            Meter.exit(115);
            
            // locate the previous one
            Meter.enter(116, "IdentityCertificationBuilder: locate previous snapshot");
            IdentitySnapshot prev = ObjectUtil.getRecentSnapshot(context, id);
            Meter.exit(116);
            
            if (prev != null) {
                // ignore the previous one if there were changes since then
                Meter.enter(117, "IdentityCertificationBuilder: diff snapshots");
                Differencer diff = new Differencer(context);
                if (!diff.equal(prev, snap))
                    prev = null;
                Meter.exit(117);
            }
            
            if (prev != null) {
                // remember just the id, we don't need to include it locally
                aid.setSnapshotId(prev.getId());
            } else {
                // Go ahead and persist since we're saving certifications
                // incrementally.  We used to just store the snapshot directly
                // on the certification and wait until the "save" phase to
                // persist the snapshots.
                this.ctxt.saveObject(snap);
                
                aid.setSnapshotId(snap.getId());
            }
            
            Meter.exit(114);
        }
        
        /** 
         * Re-use the entitlement correlation result to avoid the overhead
         * of matching.
         * @param identity
         * @return
         * @throws GeneralException
         */
        public List<EntitlementGroup> getEntitlementMapping(Identity identity, RoleAssignment assignment, Bundle detection) 
            throws GeneralException {
            
            if ( identity == null ) 
                return null;
        
            List<EntitlementGroup> contributingEnts = new ArrayList<EntitlementGroup>();
            //If new identity, we need to analyze again
            if ( !Util.nullSafeEq(_lastIdentityId, identity.getId()) ) {
                if (correlator != null ) {
                    // old way, full re-correlation
                    correlator.analyzeContributingEntitlements(identity);
                    _lastIdentityId = identity.getId();
                    
                }
            }
            
            if(assignment != null) {
                //Get all contributing entitlements for the assignment
                if(detection == null) {
                    Map<Bundle, List<EntitlementGroup>> assignmentEntList = correlator.getContributingEntitlements(assignment, true);
                    for(List<EntitlementGroup> entGroup : assignmentEntList.values()) {
                        if (entGroup != null && !entGroup.isEmpty())
                        {
                            contributingEnts.addAll(entGroup);
                        }
                    }
                } else {
                    //Not sure this will ever be used since detections are always rolled up into the assignment
                    contributingEnts = correlator.getContributingEntitlements(assignment, detection, true);
                }
            } else {
                //Must be a detection without assignment
                contributingEnts = correlator.getContributingEntitlements(null, detection, true);
            }
            
            return contributingEnts;
            
        }

        /**
         * {@inheritDoc}
         */
        protected boolean areRecommendationsAvailable() {
            return this.identityBatchRequestBuilder != null;
        }

        /**
         * {@inheritDoc}
         */
        protected void createRecommendationRequestBuilder(AbstractCertifiableEntity entity) {
            if (isShowRecommendations()) {
                this.identityBatchRequestBuilder = new IdentityBatchRequestBuilder(cachedManagedAttributer);
                this.identityBatchRequestBuilder.identityId(entity.getId());
            }
        }

        /**
         * {@inheritDoc}
         */
        protected void addItemToRecommendationRequest(CertificationItem item, Certifiable certifiable) {
            if (areRecommendationsAvailable()) {
                this.identityBatchRequestBuilder.certificationItem(item, RecommenderUtil.getApplicationFromCertifiable(certifiable));
            }
        }

        /**
         * {@inheritDoc}
         */
        protected void populateRecommendations() {
            final String meterFetchRecs = "BaseIdentityCertificationBuilder: Fetch Recommendations";

            if (areRecommendationsAvailable()) {
                Meter.enter(meterFetchRecs);

                try {
                    RecommenderUtil.getCertificationRecommendations(this.identityBatchRequestBuilder.build(), this.ctxt);

                } catch (GeneralException ge) {
                    log.info("Unable to retrieve recommendations for certification items.", ge);
                }

                Meter.exit(meterFetchRecs);
            }
        }

        /**
         * Helper method to get the classification names from the Entitlement in the certification item.
         * @param item Certification item
         * @return List of classification names, or null.
         * @throws GeneralException
         */
        private List<String> getEntitlementClassifications(CertificationItem item) throws GeneralException {
            Set<String> classifications = new HashSet<>();
            EntitlementSnapshot snap = item.getExceptionEntitlements();
            Application app = snap.getApplicationObject(this.ctxt);

            // IIQCB-3420 - App could be 'IdentityIQ', in that case, no entitlement
            // classifications will be returned.
            if (app != null) {
                if (!Util.isEmpty(snap.getAttributes())) {
                    for (Map.Entry<String, Object> entry : snap.getAttributes().entrySet()) {
                        if (entry.getValue() != null) {
                            for (Object val : Util.asList(entry.getValue())) {
                                Explanator.Explanation explanation = Explanator.get(app.getId(), entry.getKey(),
                                        (String) val);
                                if (explanation != null && explanation.getClassificationNames() != null) {
                                    classifications.addAll(explanation.getClassificationNames());
                                }
                            }
                        }
                    }
                }

                if (!Util.isEmpty(snap.getPermissions())) {
                    for (Permission p : snap.getPermissions()) {
                        Explanator.Explanation explanation = Explanator.get(app.getId(), p.getTarget());
                        if (explanation != null && explanation.getClassificationNames() != null) {
                            classifications.addAll(explanation.getClassificationNames());
                        }
                    }
                }
            }

            return Util.isEmpty(classifications) ? null : new ArrayList<>(classifications);
        }

        protected boolean isIncludeClassifications() {
            if (this.includeClassifications == null) {
                this.includeClassifications = definition.isIncludeClassifications();
            }
            return this.includeClassifications;
        }
    }
}
