/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Code to convert a filtered list of Certifiable objects from an Identity
 * into CertificationItems.
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
 * 
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CachedManagedAttributer;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Explanator;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.Certification.EntitlementGranularity;
import sailpoint.object.Certification.Phase;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.CertificationStateConfig;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Entitlements;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Rule;
import sailpoint.recommender.IdentityBatchRequestBuilder;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class ItemBuilder {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ItemBuilder.class);

    SailPointContext _context;
    CertificationDefinition _definition;
    Rule _customizationRule;
    EntitlementCorrelator _correlator;

    Boolean _useRollingPhases;
    List<CertificationPhaseConfig> _phaseConfig;
    EntitlementGranularity _entitlementGranularity;

    // Used to retrieve recommendations, if enabled
    IdentityBatchRequestBuilder _identityBatchRequestBuilder;
    boolean _showRecommendations;
    CachedManagedAttributer _cachedManagedAttributer;
    Boolean _includeClassifications;

    /**
     * Flag to indicate we have analyzed entitlements with EntitlementCorrelator for
     * the current execute. Will be reset to false on each call to execute(). We need this because things
     * can get out of whack with our shared EntitlementCorrelator if execute() is called twice on the same
     * identity due to exclusions (see IIQSAW-2247).
     */
    boolean _entitlementsCorrelated = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    // 
    //////////////////////////////////////////////////////////////////////

    public ItemBuilder(SailPointContext context,
                       CertificationDefinition definition,
                       Rule customizationRule,
                       EntitlementCorrelator correlator,
                       boolean showRecommendations,
                       CachedManagedAttributer cachedManagedAttributer)
        throws GeneralException {
        
        _context = context;
        _definition = definition;
        _customizationRule = customizationRule;

        // this is used by EntityBuilder to do re-correlation prior
        // to building, so let it be passed down
        // also used to build an IdentityArchiver, not sure we need
        // to be sharing this, the CorrelationModel is the global cache now?
        _correlator = correlator;

        // duplicates what is done in BaseCertificationBuilder.initializeCertification
        _phaseConfig = _definition.createPhaseConfig(_context, shouldBypassStagingPeriod());

        // use the cached value of 'show recommendations'
        _showRecommendations = showRecommendations;

        // Cache to use when looking up ManagedAttributes. Used for recommendations.
        _cachedManagedAttributer = cachedManagedAttributer;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Kludges
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Return whether this certification uses rolling phases or not. If true,
     * each item in the certification progresses through the phases
     * independently. If false, the entire certification progresses through
     * the phases at a timed interval.
     * 
     * This is a reimplementation of Certification.isUseRollingPhases
     * that relies only on the CertificationDefinition.  I don't want to 
     * require building a Certification object just to get this one
     * method.
     *
     * It also caches the result so we don't have to keep going through
     * this logic for every item.
     * 
     * The origin of the phaseConfig is somewhat complicated, it can be
     * set directly on the BaseCertificationBuilder or come from the
     * definition, comments say "allow phaseConfig to be overridden on
     * the builder. This should probably set the value on the certdef".
     *
     * jsl - Now that we introduced the concept of the root certification
     * we can take this out.  There will always be a stub Certifrication
     * with this stuff already cached.
     *
     */
    private boolean isUseRollingPhases() {

        if (_useRollingPhases == null) {

            // Rolling phases are always used for continuous certs.  They are also
            // used for periodic certs if we process remediations immediately and
            // the challenge or remediation phase is enabled.
            boolean eitherPhaseEnabled =
                isPhaseEnabled(Phase.Challenge) || isPhaseEnabled(Phase.Remediation);

            _useRollingPhases = new Boolean(_definition.isProcessRevokesImmediately() &&
                                             eitherPhaseEnabled);
        }

        return _useRollingPhases.booleanValue();
    }

    private boolean isPhaseEnabled(Phase phase) {

        return CertificationStateConfig.isEnabled(phase, _phaseConfig);
    }

    /**
     * This is what BaseCertificationContext does.
     * I don't want to follow how CertifidationGroup status is
     * calculated right now, but will need to enable this when
     * we start using groups.
     */
    private boolean shouldBypassStagingPeriod()
        throws GeneralException {

        /*
        for (CertificationGroup certGroup : Util.safeIterable(getCertificationGroups())) {
            if (!CertificationGroup.Status.Pending.equals(certGroup.getStatus()) && !CertificationGroup.Status.Staged.equals(certGroup.getStatus())) {
                return true;
            }
        }
        */
        	
        return false;
    }

    /**
     * Derive and cache the entitlement granularity.  In old code
     * this comes from the Certification, here we're requiring only 
     * the CertificationDefinition.  But since there is a minor transformation
     * cache it.
     */
    private Certification.EntitlementGranularity getEntitlementGranularity() {
        if (_entitlementGranularity == null) {
            _entitlementGranularity = _definition.getEntitlementGranularity();
        }
        return _entitlementGranularity;
    }

    private boolean isIncludeClassifications() {
        if (_includeClassifications == null) {
            _includeClassifications = _definition.isIncludeClassifications();
        }

        return _includeClassifications;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Build a list of items from a certifiables list.
     */
    public List<CertificationItem> execute(AbstractCertifiableEntity entity,
                                           List<Certifiable> certifiables,
                                           boolean isActive)
        throws GeneralException {

        List<CertificationItem> items = new ArrayList<CertificationItem>();

        // Reset flag so EntitlementCorrelator will do a fresh analysis on the identity for the first role certifiable
        _entitlementsCorrelated = false;

        this.createRecommendationRequestBuilder(entity);

        final String meterAddItems = "ItemBuilder: Build CertificationItems for CertificationEntity";
        Meter.enter(meterAddItems);

        for (Certifiable certifiable : certifiables) {
            CertificationItem item = createCertificationItem(certifiable, entity, isActive);
            items.add(item);
        }

        Meter.exit(meterAddItems);

        populateRecommendations();

        return items;
    }

    /**
     * Copied from BaseCertificationContext
     *
     * Create a CertificationItem using the createCertificationItemInternal()
     * template method and call out to the customization rule if it is
     * configured.
     *
     * @param  certifiable  The Certifiable item.
     * @param  entity       The entity that will own the item.
     * @param  isActive     Whether this item is active or not - false if
     *                      this is an excluded item.
     *
     * @return A CertificationItem for the given Certifiable that has been
     *         customized.
     */
    private CertificationItem createCertificationItem(Certifiable certifiable,
                                                        AbstractCertifiableEntity entity,
                                                        boolean isActive)
        throws GeneralException {

        CertificationItem item = null;
        final String MeterName = "ItemBuilder: create item";
        Meter.enter(MeterName);
        
        try {

            // Let the template method create the item.
            item = createCertificationItemInternal(certifiable, entity);

            // If this is active (not excluded), set some state information.
            if (isActive) {
                // If we're using rolling phases, initialize this item to active.
                // We don't need a nextPhaseTransition since this item will get
                // rolled appropriately when decisions are made.
                if (isUseRollingPhases()) {
                    item.setPhase(Certification.Phase.Active);
                }

                // If this is a continuous certification set the initial state and
                // configure notifications appropriately.
                // jsl - don't need
                //initializeContinuousState(item, cert);
            }

            // Now provide a hook to decorate the item that was created.
            // jsl - maybe better to move rules up a level?
            if (null != item) {
                if (_customizationRule != null) {
                    Map<String,Object> params = new HashMap<String,Object>();
                    params.put("context", _context);

                    // hmm, I REALLY don't want to require a Certification here
                    // see what this would impact
                    // params.put("certification", cert);
                
                    params.put("certifiable", certifiable);
                    params.put("certifiableEntity", entity);

                    // new rules won't have this any more
                    // what would they do with it?
                    // params.put("certContext", this);
                
                    params.put("item", item);
                    // params.put("state", this.state);
                    _context.runRule(_customizationRule, params);
                }
            }
        }
        finally {
            Meter.exit(MeterName);
        }

        return item;
    }

    /**
     * Copied from BaseIdentityCertificationContext
     */
    private CertificationItem createCertificationItemInternal(Certifiable certifiable,
                                                                AbstractCertifiableEntity entity)
        throws GeneralException {
            
        if (!(entity instanceof Identity))
            throw new IllegalArgumentException("Only identities may be certified in this certification type.");

        return createCertificationItemInternal(certifiable, (Identity) entity);
    }

    /**
     * Copied from BaseIdentityCertificationContext
     *
     * Create a CertificationItem for the given Certifiable object.
     *
     * @param certifiable The Certifiable for which to create the item.
     * @param identity    The Identity on which the Certifiable was found.
     * @return A CertificationItem created from the given Certifiable object.
     */
    private CertificationItem createCertificationItemInternal(Certifiable certifiable,
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

            // new signature that enables optimization

            CertificationItem.SubType subType = 
                (identity.getAssignedRole(b.getId()) != null ? CertificationItem.SubType.AssignedRole: null);

            item = new CertificationItem(b, entitlements, subType);

            if (assignmentId != null) {
                item.setRoleAssignment(roleAssignment);
            } else {
                item.setRoleDetection(identity.getUnassignedRoleDetection(b));
            }

            if (isIncludeClassifications() && b.getClassifications() != null) {
                classifications = b.getClassificationNames();
            }
        }
        else if (certifiable instanceof Entitlements) {
            Entitlements entitlements = (Entitlements) certifiable;

            // use the new method that assumes Value granularity
            // and enables persistence optimization
            item = new CertificationItem(entitlements);
            assimilateLinkAttributes(identity, entitlements, item);

            if (isIncludeClassifications()) {
                classifications = getEntitlementClassifications(item);
            }
        }
        else if (certifiable instanceof PolicyViolation) {
                
            // A local copy  will be kept in the CertificationItem so we can
            // be independent of the Interrogator.  Hmm, you won't be able to
            // search for these but if we leave them as references we'll have
            // to play the "convert references to inline" dance during archive
            // and have to carefully coordinate with Interrogator so it won't
            // delete these out from under us.
                
            item = new CertificationItem((PolicyViolation) certifiable);
        }
        else {
            throw new IllegalArgumentException("Unknown certifiable type on " +
                                               identity.getName() + ": " + certifiable);
        }

        if (!Util.isEmpty(classifications)) {
            item.setClassificationNames(classifications);
        }

        addItemToRecommendationRequest(item, certifiable);
        return item;
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
        Application app = snap.getApplicationObject(_context);

        // IIQCB-3420 - App could be 'IdentityIQ', in that case, no entitlement
        // classifications will be returned.
        if (app != null) {
            // MT: We dont really support anything besides Value granularity now
            // right? Does it make sense that we even need to iterate?
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
                    ManagedAttribute ma = _cachedManagedAttributer.get(app, true, p.getTarget(), null, null);
                    Explanator.Explanation explanation = Explanator.get(app.getId(), p.getTarget());
                    if (explanation != null && explanation.getClassificationNames() != null) {
                        classifications.addAll(explanation.getClassificationNames());
                    }
                }
            }
        }

        return Util.isEmpty(classifications) ? null : new ArrayList<>(classifications);
    }

    /**
     * BaseCertificationBuilder
     *
     * Move all of the extended Link attributes over to the 
     * Certification Item.
     *
     * This is a different form of "promotion" than the
     * other extendible objects do.  The extended attributes aren't 
     * stored in this object, they are copied from another.
     *
     * Note that this assumes the extended attribute numbers
     * are identical for Link and CertificationItem!
     *
     * jsl - In 6.1 the new rules are that all extended attributes
     * appear in the map returned by SailPointObject.getExtendedAttributes, 
     * or in this case since we're only dealing with Link you can call
     * Link.getAttribute(String) for each ObjectAttribute.  Don't assume
     * that if it has an extended number that getExtendedX() will return
     * anything and that setExtendedX() will do anything.
     */
    private void assimilateLinkAttributes(Identity identity, Entitlements entitlements,
                                            CertificationItem item) throws GeneralException {
                    
        if ( ( identity == null ) || ( entitlements == null ) ) {
            return;
        }
                    
        Application app = entitlements.getApplicationObject(_context);
        String instance = entitlements.getInstance();
        String nativeIdentity = entitlements.getNativeIdentity();
                    
        if ( ( app != null ) && ( nativeIdentity != null  ) ) {
            Link link = identity.getLink(app, instance, nativeIdentity);
            if ( link != null ) {
                ObjectConfig config = Link.getObjectConfig();
                if (config != null) {
                    List<ObjectAttribute> atts = config.getObjectAttributes();
                    if (atts != null) {
                        for (ObjectAttribute att : atts) {
                            int num = att.getExtendedNumber();
                            if (link.isExtendedIdentityType(att)) {
                                if ( num > 0 ) {
                                    Identity value = link.getExtendedIdentity(num);
                                    if ( value != null ) {
                                        //These don't even support extended identity??? -rap
                                        item.setExtendedIdentity(value, num);
                                        continue;
                                    }
                                }
                            }

                            // jsl - get the real value, the
                            // extended column may be stale
                            //String value = link.getExtended(num);
                            Object value = link.getAttribute(att.getName());
                            if ( value != null ) {
                                // convert Date to utime,
                                // when this is converted to an attributes map
                                // this will be handled by the getter
                                // this appears in a few places factor out a util
                                if (value instanceof Date) {
                                    Date date = (Date)value;
                                    long utime = date.getTime();
                                    value = Long.toString(utime);
                                }

                                item.setAttribute(att.getName(), value.toString());
                            }

                        }
                    }
                }
            }
        }
    }

    /** 
     * Copied from BaseIdentityCertificationContext
     *
     * Re-use the entitlement correlation result to avoid the overhead
     * of matching.
     * @param identity
     * @return
     * @throws GeneralException
     */
    private List<EntitlementGroup> getEntitlementMapping(Identity identity, RoleAssignment assignment, Bundle detection)
        throws GeneralException {
            
        if ( identity == null ) 
            return null;
        
        List<EntitlementGroup> contributingEnts = new ArrayList<EntitlementGroup>();
        //If this is our first time through here, do the full entitlement analysis for this identity
        if (!_entitlementsCorrelated && _correlator != null ) {
            // old way, full re-correlation
            _correlator.analyzeContributingEntitlements(identity);
            _entitlementsCorrelated = true;
        }

        if(assignment != null) {
            //Get all contributing entitlements for the assignment
            if(detection == null) {
                Map<Bundle, List<EntitlementGroup>> assignmentEntList = _correlator.getContributingEntitlements(assignment, true);
                for(List<EntitlementGroup> entGroup : assignmentEntList.values()) {
                    if (entGroup != null && !entGroup.isEmpty())
                        {
                            contributingEnts.addAll(entGroup);
                        }
                }
            } else {
                //Not sure this will ever be used since detections are always rolled up into the assignment
                contributingEnts = _correlator.getContributingEntitlements(assignment, detection, true);
            }
        } else {
            //Must be a detection without assignment
            contributingEnts = _correlator.getContributingEntitlements(null, detection, true);
        }
            
        return contributingEnts;
            
    }

    /**
     * Check if recommendations are enabled.
     *
     * @return true if recommendations are available, otherwise false
     */
    private boolean hasIdentityCertRecommendations() {

        return this._identityBatchRequestBuilder != null;
    }

    /**
     * Check if the 'Show Recommendations' option was enabled for this cert.
     *
     * @return true if enabled, otherwise false
     */
    private boolean isShowRecommendations() {

        return this._showRecommendations;
    }

    /**
     * Create a recommendation builder that is able to build recommendation requests
     * for the items certifiable in the given entity.
     *
     * @param entity The entity being certified.
     */
    private void createRecommendationRequestBuilder(AbstractCertifiableEntity entity) {

        if (isShowRecommendations() && entity instanceof Identity) {
            this._identityBatchRequestBuilder = new IdentityBatchRequestBuilder(_cachedManagedAttributer);
            this._identityBatchRequestBuilder.identityId(entity.getId());
        }
    }

    /**
     * Adds a certification item to the builder.
     *
     * @param item The CertificationItem to generate a request for.
     * @param certifiable The Certifiable that the CertificationItem is based on.
     */
    private void addItemToRecommendationRequest(CertificationItem item, Certifiable certifiable) {

        if (hasIdentityCertRecommendations()) {
            this._identityBatchRequestBuilder.certificationItem(item, RecommenderUtil.getApplicationFromCertifiable(certifiable));
        }
    }

    /**
     * Retrieves recommendations for the certification items provided to the builder.
     * If successful, the results are added to the certification items.
     */
    private void populateRecommendations() {
        final String meterFetchRecs = "ItemBuilder: Fetch Recommendations";

        if (hasIdentityCertRecommendations()) {
            Meter.enter(meterFetchRecs);

            try {
                RecommenderUtil.getCertificationRecommendations(this._identityBatchRequestBuilder.build(), _context);

            } catch (GeneralException ge) {
                log.info("Unable to retrieve recommendations for certification items.", ge);
            }

            Meter.exit(meterFetchRecs);
        }
    }
}

