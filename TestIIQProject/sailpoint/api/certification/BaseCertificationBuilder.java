/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 *
 */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationBuilder;
import sailpoint.api.CertificationContext;
import sailpoint.api.Certificationer;
import sailpoint.api.IdentityService;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Application;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.ArchivedCertificationEntity.Reason;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.Rule;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.identity.RoleAssignmentUtil;
import sailpoint.web.identity.RoleDetectionUtil;
import sailpoint.web.messages.MessageKeys;


/**
 * Abstract base class for CertificationBuilders to extend.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BaseCertificationBuilder implements CertificationBuilder {

    private Log log = LogFactory.getLog(BaseCertificationBuilder.class);

    SailPointContext context;
    List<Identity> owners;
    Map<String,Object> results;
    CertificationDefinition definition;
    private List<Message> warnings;
    Attributes<String,Object> attributes;

    String taskScheduleId;

    boolean global;
    List<String> includedAppIds;

    // cached copy of the included applications for the certification
    private List<Application> includedApplications;

    List<CertificationPhaseConfig> phaseConfig;

    /**
     * List of CertificationGroups to be assigned to all
     * certifications created by this builder.
     */
    private List<CertificationGroup> certificationGroups;

    /**
     * Cache the 'Show Recommendations' setting. The cert definition may specify
     * true, but a recommender may not be configured properly. In that case, we
     * disable recommendations temporarily for this execution.
     */
    private boolean showRecommendations;

    //private ICertTypeSpecificInput certTypeSpecificInput;
    
    /**
     * Constructor.
     *
     * @param  ctx             The SailPointContext to use for this builder.
     * @param  definition
     */
    public BaseCertificationBuilder(SailPointContext ctx, CertificationDefinition definition) {

        this.context = ctx;
        this.definition = definition;
        this.results = new HashMap<String,Object>();

        this.global = definition.isGlobal();
        this.includedAppIds = ObjectUtil.convertToIds(ctx, Application.class, definition.getIncludedApplicationIds());

        // Retrieve and cache the 'show recommendations' setting.
        this.showRecommendations = Util.nullsafeBoolean(definition.getShowRecommendations());
        if (this.showRecommendations && !RecommenderUtil.isRecommenderConfigured(context)) {
            this.showRecommendations = false;
            log.warn("Recommendations have been disabled for this certification because a Recommender could not be created.");
        }
    }

    /**
     * Return a single CertificationContext from this builder.  If this builder
     * produces more or less than one context this will throw a
     * GeneralException.
     *
     * @return The single CertificationContext from this builder.
     */
    public CertificationContext getContext() throws GeneralException {

        Iterator<CertificationContext> certCtxs = this.getContexts();

        if ((null == certCtxs) || (!certCtxs.hasNext())) {
            throw new GeneralException("Expected one certification context.");
        }

        CertificationContext ctx = certCtxs.next();

        if (certCtxs.hasNext()) {
            throw new GeneralException("Expected a single certification context.");
        }

        return ctx;
    }

    /**
     * Performs pre-generation initialization. This may commit.
     */
    public void init() throws GeneralException{
        if (certificationGroups != null && !certificationGroups.isEmpty()){
            for(CertificationGroup grp : certificationGroups){
                context.saveObject(grp);
            }
            context.commitTransaction();
        }
    }

      /**
     * Finishes of certification generation process, updating the CertificationGroups
     * and updating their statistics.
     *
     * @param success True if the certification generation process completed without
     * any errors.
     * @param messages List of messages (usually errors or warnings) to attach to the CertificationGroup
     * @throws GeneralException
     */
    public void finalize(boolean success, List<Message> messages) throws GeneralException{
         if (certificationGroups != null && !certificationGroups.isEmpty()){
            Certificationer certificationer = new Certificationer(context);

            for(CertificationGroup certGroup : certificationGroups){

                if (messages != null){
                    certGroup.addMessages(messages);
                }
                
                
                CertificationGroup.Status successStatus = CertificationGroup.Status.Active;
                if (certGroup.getDefinition().isStagingEnabled()) {
                    successStatus = CertificationGroup.Status.Staged;
                }
                
                certGroup.setStatus(success ? successStatus : CertificationGroup.Status.Error);
                context.saveObject(certGroup);
                context.commitTransaction();

                certificationer.refreshStatistics(certGroup);
            }
        }
    }

    public Map<String,Object> getResults() {
        return this.results;
    }

    @SuppressWarnings("unchecked")
    public void addResult(String key, String value) {

        Object existing = this.results.get(key);
        if (null == existing) {
            this.results.put(key, value);
        }
        else if (existing instanceof String) {
            List<String> list = new ArrayList<String>();
            list.add((String) existing);
            list.add(value);
            this.results.put(key, list);
        }
        else if (existing instanceof List) {
            ((List) existing).add(value);
        }
    }

    void addOwnerResult(String key, Identity... identity) {
        if (identity != null) {
            for (Identity current : identity) {
                if (current != null)
                    addResult(key, current.getName());
            }
        }
    }

    /**
     * @return Non-null list of warning messages.
     */
    public List<Message> getWarnings() {
        if (warnings == null)
            warnings = new ArrayList<Message>();
        return warnings;
    }

    /**
     * Add a warning to this certification context.
     *
     * @param msg warning msg to add. Null messages are ignored.
     */
    public void addWarning(Message msg){
        if(msg != null){
            // override the type so we don't have to set it when creating the msg
            msg.setType(Message.Type.Warn);
            getWarnings().add(msg);
        }
    }

    /**
     * List of Application object to be included in the certification.
     */
    public List<Application> getIncludedApplications() throws GeneralException{

        if (includedApplications == null && includedAppIds != null && !includedAppIds.isEmpty()){
            includedApplications = context.getObjects(Application.class,
                    new QueryOptions(Filter.in("id", includedAppIds)));
        }

        return includedApplications;
    }

    public List<Identity> getOwners() {
        return owners;
    }

    public void setOwners(List<Identity> owners) {
        this.owners = owners;
    }

    public List<CertificationGroup> getCertificationGroups() {
        return certificationGroups;
    }

    public void setCertificationGroups(List<CertificationGroup> groups) {
         certificationGroups = groups;
    }

    public void setTaskScheduleId(String id) {
        this.taskScheduleId = id;
    }

    public void setAttributes(Attributes<String,Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Return the value for a requested value, either from the attributes map or
     * from the system configuration.
     */
    Object getAttribute(String key) throws GeneralException {
        Object value = null;

        if (null != this.attributes) {
            value = this.attributes.get(key);
        }

        if (null == value) {
            value = context.getConfiguration().get(key);
        }

        return value;
    }

    public CertificationNamer getCertificationNamer(){
        return new CertificationNamer(this.context);
    }

    public void setPhaseConfig(List<CertificationPhaseConfig> phaseConfig) {
        this.phaseConfig = phaseConfig;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Base CertificationContext Implementation
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Abstract base implementation of the CertificationContext.
     */
    public abstract class BaseCertificationContext implements CertificationContext {

        private Log log = LogFactory.getLog(BaseCertificationContext.class);

        protected SailPointContext ctxt;
        private CertificationDefinition definition;
        private List<Identity> owners;
        Attributes<String,Object> attributes;

        private List<String> includedApplicationIds;

        /**
         * List of CertificationGroups to be assigned to all
         * Certifications created by this context.
         */
        private List<CertificationGroup> certificationGroups;

        /**
         * A rule to run to exclude certifiables from a certification.
         */
        private Rule exclusionRule;

        /**
         * Cached Rule that gets executed to customize CertificationItems.
         */
        private Rule itemCustomizationRule;

        /**
         * Cached Rule that gets executed to customize CertificationEntities.
         */
        private Rule entityCustomizationRule;

        /**
         * Cached Rule that gets executed to pre-delegate entities.
         */
        private Rule preDelegationRule;

        private Map<String, Object> state = new HashMap<String, Object>();

        /**
         * Default constructor.
         */
        public BaseCertificationContext(SailPointContext ctx) {
            this.ctxt=ctx;
        }

        /**
         * Constructor.
         */
        public BaseCertificationContext(SailPointContext ctx, CertificationDefinition definition,
                                        List<Identity> owners) {
            this.ctxt=ctx;
            this.definition = definition;
            this.owners = owners;
            this.includedApplicationIds = ObjectUtil.convertToIds(ctx, Application.class, definition.getIncludedApplicationIds());
        }

        private boolean shouldBypassStagingPeriod()
        	throws GeneralException {
        	
        	for (CertificationGroup certGroup : Util.safeIterable(getCertificationGroups())) {
        		if (!CertificationGroup.Status.Pending.equals(certGroup.getStatus()) && !CertificationGroup.Status.Staged.equals(certGroup.getStatus())) {
        			return true;
        		}
        	}
        	
        	return false;
        }
        
        public Certification initializeCertification(Identity requestor) throws GeneralException{

            Certification cert = new Certification();
            cert.setCreator(requestor);
            cert.setType(getType());
            cert.setCertifierIdentities(getOwners());
            cert.setAssignedScope(definition.getAssignedScope());
            cert.setTaskScheduleId(taskScheduleId);
            cert.setAllowProvisioningRequirements(definition.isAllowProvisioningRequirements());
            cert.setRequireApprovalComments(definition.isRequireApprovalComments());
            cert.setDisplayEntitlementDescription(definition.isDisplayEntitlementDescriptions());
            cert.addCertificationGroups(getCertificationGroups());

            // allow phaseConfig to be overridden on the builder. This should
            // probably set the value on the certdef
            if (phaseConfig != null)
                cert.setPhaseConfig(phaseConfig);
            else
                cert.setPhaseConfig(definition.createPhaseConfig(context, shouldBypassStagingPeriod()));

            cert.setProcessRevokesImmediately(definition.isProcessRevokesImmediately());

            // NOTE: If the certification ends up being forwarded,
            // the manager name in the shortName may not match the
            // owner.  Could try to track this in forwardWorkItem
            // but the CertificationContext is gone at that point.
            // In some ways, leaving the default title is correct
            // because it still is a certification for the original
            // manager, it's just been handed off to someone else. - jsl
            cert.setName(generateName());
            cert.setShortName(generateShortName());

            return cert;
        }


        /**
         * Store the context on the given certification.  Subclasses should call
         * this method first, then add their own behavior.
         *
         * @param  cert  The Certification on which to store the context info.
         */
        public void storeContext(Certification cert) throws GeneralException {

            definition.storeContext(context, cert);
        }

        public Certification.Type getType(){
            return definition.getType();
        }

        protected boolean isGlobal(){
            return definition.isGlobal();
        }

        protected List<String> getIncludedApplicationIds() {
            return includedApplicationIds;
        }

        protected void setIncludedApplicationIds(List<String> includedApplicationIds) {
            this.includedApplicationIds = includedApplicationIds;
        }

        public boolean isIncludeAdditionalEntitlements() {
            return definition.isIncludeAdditionalEntitlements();
        }

        public boolean isIncludeTargetPermissions() {
            return definition.isIncludeTargetPermissions();
        }

        public boolean isIncludePolicyViolations() {
            return definition.isIncludePolicyViolations();
        }

        public boolean isIncludeBusinessRoles() {
            return definition.isIncludeRoles();
        }

        public boolean isIncludeAssignedRoles() {
            return true;
        }

        /**
         * Generate the name for this certification.  This prefers the name
         * template if configured.  If not configured, this lets the subclass
         * generate the name with the generateDefaultName() method.
         */
        public String generateName() throws GeneralException {
            String name = renderName(definition.getNameTemplate());
            if (null == name) {
                name = generateDefaultName();
            }

            // Cap the length of this.
            return Util.truncate(name, Certification.NAME_MAX_LENGTH);
        }

        /**
         * Generate the short name for this certification.  This prefers the
         * short name template if configured.  If not configured, this lets the
         * subclass generate the short name with the generateDefaultShortName()
         * method.
         */
        public String generateShortName() throws GeneralException {
            String shortName = renderName(definition.getShortNameTemplate());
            if (null == shortName) {
                shortName = generateDefaultShortName();
            }

            // Cap the length of this.
            return Util.truncate(shortName, Certification.SHORT_NAME_MAX_LENGTH);
        }

        public CertificationNamer getCertificationNamer(){

            CertificationNamer namer = new CertificationNamer(this, getOwners(), this.ctxt);

            // allow implementation-specific params to be added by subclasses
            addNameParameters(namer);

            return namer;
        }

        protected abstract void addNameParameters(CertificationNamer namer);

        /**
         * Render the given name template using either simple variable
         * substitution or velocity.  The parameters used here defaulted by
         * createBaseNameTemplateParameters() and expanded by each subclass
         * with addNameTemplateParameters().
         */
        private String renderName(String nameTemplate)
            throws GeneralException {

            if (null != nameTemplate) {
                CertificationNamer namer = getCertificationNamer();

                nameTemplate = namer.render(nameTemplate);
            }

            return nameTemplate;
        }


        /**
         * Generate a name for this certification using the logic builtin to
         * this context.  This is only used if there is not a name template.
         */
        abstract String generateDefaultName() throws GeneralException;

        /**
         * Generate a short name for this certification using the logic builtin
         * to this context.  This is only used if there is not a short name
         * template.
         */
        abstract String generateDefaultShortName() throws GeneralException;

        /**
         * Base implementation of getOwners().  This will return any owners that
         * were specified with setOwners() on the BaseCertificationContext.  If
         * this is null, this falls back to the abstract method
         * getOwnersInternal(), which should be overridden by subclasses to
         * calculate owners based on the certification type.
         */
        public List<Identity> getOwners() {

            return (null != this.owners) ? this.owners : getOwnersInternal();
        }

        /**
         * Returns a list the owners (certifiers) of the certification.
         * This method will only be called if the owners collection of
         * the base class is empty.
         *
         * @return List of certification owners
         */
        protected abstract List<Identity> getOwnersInternal();

        /**
         * A template method that subclasses should implement to create a
         * CertificationEntity of the appropriate type.  Subclasses should *NOT*
         * add the certifiables to the entity - this will be done in the
         * createCertificationEntity() method.
         *
         * @param  cert      The Certification that will own the entity.
         * @param  entity    The certifiable for which to create the entity.
         * @param  snapshot  Whether to create a snapshot of the entity.
         *
         * @return A CertificationEntity of the appropriate type.
         */
        protected abstract CertificationEntity createCertificationEntityInternal(
                                                   Certification cert,
                                                   AbstractCertifiableEntity entity,
                                                   boolean snapshot)
            throws GeneralException;

        /**
         * A template method that subclasses should implement to create a
         * CertificationItem for the given Certifiable.  This is called by the
         * concrete createCertificationItem(), which provides a hook to allow
         * customization of the CertificationItem that is created.
         *
         * @param  cert         The Certification that will own the item.
         * @param  certifiable  The Certifiable item.
         * @param  entity       The entity that will own the item.
         *
         * @return A CertificationItem for the given Certifiable.
         */
        protected abstract CertificationItem createCertificationItemInternal(
                                                 Certification cert,
                                                 Certifiable certifiable,
                                                 AbstractCertifiableEntity entity)
            throws GeneralException;

        /**
         * Create an CertificationEntity for an entity.  This runs the exclusion
         * rule (if configured) and as a side-effect will add entities to the
         * exclusion list on the Certification if saveExclusions is true.
         *
         * @param  cert    The Certification for which we're creating the entity.
         * @param  entity  The certifiable for which to create a CertificationEntity.
         */
        public CertificationEntity createCertificationEntity(Certification cert,
                                                             AbstractCertifiableEntity entity)
            throws GeneralException {

            CertificationEntity aid = null;

            Meter.enter(110, "CertificationBuilder: Get certifiables");
            List<Certifiable> certifiables = new ArrayList<Certifiable>();
            if (entity != null)
                certifiables = getCertifiables(entity);
            Meter.exit(110);

            // First, check if the entity is inactive and we're excluding
            // inactive identities.  We always exclude inactive entities from
            // continuous certs.  If so, we'll stop now.
            if (definition.isExcludeInactive() && entity.isInactive()) {
                addArchivedEntity(entity, certifiables, cert, Reason.Inactive, null);
                return null;
            }

            // if we have an exclusion rule, then run it
            if ( definition.getExclusionRuleName() != null ) {
                if ( certifiables != null && certifiables.size() > 0 ) {
                        // run an exclude rule that is allowed to filter the
                        // certifiables list by removing items or moving the
                        // items to an exclusion list that can be persisted
                    List<Certifiable> itemsToExclude = new ArrayList<Certifiable>();
                    String explanation =
                        runExcludeRule(entity, cert, certifiables, itemsToExclude);

                    if (definition.getSaveExclusions() && (itemsToExclude != null) &&
                        (itemsToExclude.size() > 0)) {
                        addArchivedEntity(entity, itemsToExclude, cert, Reason.Excluded, explanation);
                    }
                }
            }

            // Only create the CertificationEntity if there are items to certify.
            if ((certifiables != null) && !certifiables.isEmpty()) {

                Meter.enter(111, "CertificiationBuilder: create internal");
                aid = createCertificationEntityInternal(cert, entity, true);
                Meter.exit(111);

                if (null != certifiables) {
                    createRecommendationRequestBuilder(entity);

                    final String meterAddItems = "BaseCertificationBuilder: Add CertificationItems to CertificationEntity";
                    Meter.enter(meterAddItems);

                    for (Certifiable certifiable : certifiables) {
                        CertificationItem item = createCertificationItem(cert, certifiable, entity, true);
                        aid.add(item);
                        addItemToRecommendationRequest(item, certifiable);
                    }

                    Meter.exit(meterAddItems);

                    populateRecommendations();
                }

                // Run the customization rule if one is configured.
                Rule customizationRule = this.getEntityCustomizationRule();
                if (null != customizationRule) {
                    Map<String,Object> params = new HashMap<String,Object>();
                    params.put("context", this.ctxt);
                    params.put("certification", cert);
                    params.put("certifiableEntity", entity);
                    params.put("entity", aid);
                    params.put("certContext", this);
                    params.put("state", this.state);
                    this.ctxt.runRule(customizationRule, params);
                }
                
                // Pre-delegate the entity if a rule is defined.
                aid.setCertification(cert);
                preDelegate(cert, aid);
            }

            return aid;
        }

        /**
         * Add an archived certification entity to the cert using the given
         * information.
         */
        private void addArchivedEntity(AbstractCertifiableEntity entity,
                                       List<Certifiable> certifiables,
                                       Certification cert, Reason reason,
                                       String explanation)
            throws GeneralException {

            CertificationEntity excluded =
                createCertificationEntityInternal(cert, entity, true);

            for (Certifiable certifiable : certifiables) {
                excluded.add(createCertificationItem(cert, certifiable, entity, false));
            }

            ArchivedCertificationEntity archived =
                new ArchivedCertificationEntity(excluded, reason, explanation);
            
            if (!cert.mergeArchivedEntity(archived, context)) {
                archived.setCertification(cert);
                context.saveObject(archived);
            }
        }

        /**
         * Run the exclusion rule (if configured) for the given entity.
         */
        private String runExcludeRule(AbstractCertifiableEntity entity,
                                      Certification cert,
                                      List<Certifiable> items,
                                      List<Certifiable> itemsToExclude )
                throws GeneralException {

            String explanation = null;
            
            if (this.exclusionRule == null) {
                this.exclusionRule =  loadRule(definition.getExclusionRuleName());
            }

            if (this.exclusionRule != null) {
                HashMap<String,Object> params = new HashMap<String,Object>();
                params.put("context", ctxt);
                params.put("entity", entity);
                params.put("certification", cert);
                params.put("certContext", this);
                params.put("items", items);
                params.put("itemsToExclude", itemsToExclude);
                params.put("state", this.state);

                // Left for backwards-compatibility.
                if (entity instanceof Identity) {
                    params.put("identity", entity);
                }

                explanation = (String) ctxt.runRule(this.exclusionRule, params);
            }

            return explanation;
        }

        /**
         * Create a CertificationItem using the createCertificationItemInternal()
         * template method and call out to the customization rule if it is
         * configured.
         *
         * @param  cert         The Certification that will own the item.
         * @param  certifiable  The Certifiable item.
         * @param  entity       The entity that will own the item.
         * @param  isActive     Whether this item is active or not - false if
         *                      this is an excluded item.
         *
         * @return A CertificationItem for the given Certifiable that has been
         *         customized.
         */
        protected CertificationItem createCertificationItem(Certification cert,
                                                            Certifiable certifiable,
                                                            AbstractCertifiableEntity entity,
                                                            boolean isActive)
            throws GeneralException {

            // Let the template method create the item.
            CertificationItem item =
                createCertificationItemInternal(cert, certifiable, entity);

            // If this is active (not excluded), set some state information.
            if (isActive) {
                // If we're using rolling phases, initialize this item to active.
                // We don't need a nextPhaseTransition since this item will get
                // rolled appropriately when decisions are made.
                if (cert.isUseRollingPhases()) {
                    item.setPhase(Certification.Phase.Active);
                }
            }

            // Now provide a hook to decorate the item that was created.
            if (null != item) {
                Rule rule = getItemCustomizationRule();
                if (null != rule) {
                    Map<String,Object> params = new HashMap<String,Object>();
                    params.put("context", this.ctxt);
                    params.put("certification", cert);
                    params.put("certifiable", certifiable);
                    params.put("certifiableEntity", entity);
                    params.put("certContext", this);
                    params.put("item", item);
                    params.put("state", this.state);
                    this.ctxt.runRule(rule, params);
                }
            }

            return item;
        }

        /**
         * Return the item customization rule if defined.
         */
        private Rule getItemCustomizationRule() throws GeneralException {

            if ((null == this.itemCustomizationRule) && (null != definition.getItemCustomizationRuleName(ctxt))) {
                this.itemCustomizationRule = loadRule(definition.getItemCustomizationRuleName(ctxt));
            }

            return this.itemCustomizationRule;
        }

        /**
         * Return the entity customization rule if defined.
         */
        private Rule getEntityCustomizationRule() throws GeneralException {

            if (null == this.entityCustomizationRule) {
                String entityCustomizationRuleName = definition.getEntityCustomizationRuleName();
                if (null != entityCustomizationRuleName) {
                    this.entityCustomizationRule =
                        this.ctxt.getObjectByName(Rule.class, entityCustomizationRuleName);
    
                    // Load the rule fully so we can decache and be alright later.
                    if (null != this.entityCustomizationRule) {
                        this.entityCustomizationRule.load();
                    }
                }
            }

            return this.entityCustomizationRule;
        }

        /**
         * Pre-delegate the given CertificationEntity if a pre-delegation rule
         * is defined and returns a value for this entity.
         *
         * @param  cert    The Certification to which the entity will be added.
         * @param  entity  The CertificationEntity to attempt to pre-delegate.
         */
        private void preDelegate(Certification cert, CertificationEntity entity)
            throws GeneralException {

            if (null != entity) {
                Rule rule = getPreDelegationRule();
                if (null != rule) {
                    Map<String,Object> params = new HashMap<String,Object>();
                    params.put("context", this.ctxt);
                    params.put("certification", cert);
                    params.put("entity", entity);
                    params.put("certContext", this);
                    params.put("state", this.state);
                    Object results = this.ctxt.runRule(rule, params);

                    // Handle a map that contains information about the delegation -
                    // the recipient (a string or identity), a description, and
                    // comments.
                    if (null != results) {
                        if (results instanceof Map) {
                            Map map = (Map) results;
                            Identity recipient = (Identity) map.get("recipient");
                            String recipientName = (String) map.get("recipientName");
                            String description = (String) map.get("description");
                            String comments = (String) map.get("comments");
                            String certName = (String) map.get("certificationName");
                            boolean reassign = Util.getBoolean(map, "reassign");

                            // Legacy - look for "delegate" args.
                            if (null == recipient) {
                                recipient = (Identity) map.get("delegate");
                            }
                            if (null == recipientName) {
                                recipientName = (String) map.get("delegateName");
                            }
                            
                            if ((null == recipient) && (null != recipientName)) {
                                recipient = getDelegate(recipientName, entity);
                            }

                            if (null != recipient) {
                                if (description == null) {
                                    description = getDelegationDescription(entity);
                                }

                                Identity requestor = null;
                                if ((null != cert.getCertifiers()) && !cert.getCertifiers().isEmpty()) {
                                    requestor = ctxt.getObjectByName(Identity.class, cert.getCertifiers().get(0));
                                }
                                else {
                                    requestor = cert.getCreator(ctxt);
                                }

                                if (reassign) {
                                    List<AbstractCertificationItem> items =
                                        new ArrayList<AbstractCertificationItem>();
                                    items.add(entity);
                                    cert.bulkReassign(requestor, items, recipient, certName,
                                                      description, comments, this.ctxt.getConfiguration());
                                }
                                else {
                                    entity.delegate(requestor, null, recipient.getName(), description, comments);
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Return the pre-delegation rule if defined.
         */
        private Rule getPreDelegationRule() throws GeneralException {

            if ((null == this.preDelegationRule) && (null != definition.getPreDelegationRuleName())) {
                this.preDelegationRule = loadRule(definition.getPreDelegationRuleName());
            }

            return this.preDelegationRule;
        }

        /**
         * Return the Identity with the given name if it exists, otherwise log
         * a warning.
         */
        private Identity getDelegate(String name, CertificationEntity entity)
            throws GeneralException {

            Identity delegate = ctxt.getObjectByName(Identity.class, name);
            if (null != delegate) {

            }
            else {
                addWarning(new Message(MessageKeys.COULD_NOT_LOAD_PREDELEGATE_ID, name,
                                entity.getFullname()));
            }

            return delegate;
        }

        /**
         * Generates description of the delegation. Description is localized using the
         * server default locale.
         *
         * @param entity Entity being delegated
         * @return Localized delegation description
         */
        private String getDelegationDescription(CertificationEntity entity) {
            Message desc = new Message(MessageKeys.CERT_DELEGATION_DESC,
                    entity.getFullname());
            return desc.getLocalizedMessage();
        }

        /**
         * Retrieves a list of certifiables for the given entity. Builder properties
         * are used to filter the appropriate type of certifiables to return. This base
         * implementation only returns certifiables which relate to an application in
         * the includedApplications property on the builder.
         *
         * @param entity AbstractCertifiableEntity from which to retrieve the Certifiable items.
         * @return List of Certifiables
         * @throws GeneralException
         */
        public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {

            return getCertifiables(entity, getIncludedApplications());
        }

        /**
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
                boolean certifyAccounts = definition.isCertifyAccounts();
                if (!certifyAccounts && isIncludeBusinessRoles()) {

                    // Get da rolez. if a detected role is permitted or required by one or
                    // more assigned roles, we need to certify the assigned role(s) and ignore
                    // the detected role. If there are no such assigned roles, the detected
                    // role should be certified.
                    
                    /**
                     * Detected Roles without an assignment ID are truly Detected without assignment, they need to 
                     * be certified individually
                     */
                    Collection<RoleDetection> detectedRoles = entity.getRoleDetections(apps);
                    
                    //Add all detected Roles without assignmentId
                    if(!Util.isEmpty(detectedRoles)) {
                        for(RoleDetection detected : detectedRoles) {
                            //Need to test detected for assignmentId
                            if(Util.isNullOrEmpty(detected.getAssignmentIds())) {
                                Bundle b = RoleDetectionUtil.getClonedBundleFromRoleDetection(context, detected, null);
                                if (b != null) {
                                    certifiables.add(b);
                                }
                            }
                        }
                    }

                    /**
                     * RoleDetections with an assignmentId belong to a RoleAssignment. We will role these up into the
                     * RoleAssignment when certifying. 
                     */
                    //Need to use RoleAssignments here
                    if (isIncludeAssignedRoles() && entity.getAssignedRoles() != null){
                        List<RoleAssignment> roleAssignments = entity.getRoleAssignments();
                        if (!Util.isEmpty(roleAssignments)) {
                            for (RoleAssignment assign : roleAssignments) {
                                if (!assign.isNegative()) {
                                    Bundle b = RoleAssignmentUtil.getClonedBundleFromRoleAssignment(context, assign);
                                    if (b != null) {
                                        certifiables.add(b);
                                    }
                                }
                            }
                        }
                        
                        
                    }
                }

                // Add any additional entitlements.
                if (!certifyAccounts && isIncludeAdditionalEntitlements()) {
                    certifiables.addAll(getExceptionCertifiables(entity, apps));
                }

                if (!certifyAccounts && isIncludeTargetPermissions()) {
                    certifiables.addAll(getTargetPermissionCertifiables(entity, apps));
                }

                if (certifyAccounts) {
                    certifiables.addAll(getAccountCertifiables(entity, apps));
                }

                if (!certifyAccounts && definition.isCertifyEmptyAccounts()){
                    certifiables.addAll(getEmptyAccountCertifiables(entity, apps));
                }

                if (isIncludePolicyViolations()) {
                     List<PolicyViolation> violations = null;

                     Meter.enter(112, "CertificationBuilder: getPolicyViolations");

                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("identity", entity));
                    ops.add(Filter.eq("active", true));

                    violations = context.getObjects(PolicyViolation.class, ops);
                    if (violations != null) {
                        for (PolicyViolation v : violations){

                            // Check relevant apps if we're filtering by apps.
                            if ((null != apps) && !apps.isEmpty()) {
                                List<Application> relevantApps = v.getRelevantApps(context);

                                // If there are no relevant apps or none of the relevant
                                // apps are in the filter list, we won't include this.
                                if ((null == relevantApps) || relevantApps.isEmpty() ||
                                    !Util.containsAny(relevantApps, apps)) {
                                    continue;
                                }
                            }
                            
                            Policy p = v.getPolicy(context);
                            // jsl - Policy should have some kind of isCertifiable
                            // method so we don't have to hard code types?
                            if (p != null && !p.isType(Policy.TYPE_ACTIVITY)) {
                                certifiables.add(v);
                            }
                        }
                    }

                    Meter.exit(112);
                }
            }
            return certifiables;
        }

        /**
         * Utility function which retrieves and loads a Rule object.
         */
        private Rule loadRule(String name) throws GeneralException{

            if (name==null)
                return null;

            Rule rule = ctxt.getObjectByName(Rule.class, name);

            // Load the rule fully so we can decache and be alright later.
            if (null != rule) {
                rule.load();
            }

            return rule;
        }

        /**
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
            // djs: tmp disable until sourceRoles/indirect is worked out
            boolean useEntitlementsTable = false;
            if ( useEntitlementsTable ) {            
                certifiables = createCertifiablesFromIdentityEntitlements(entity, apps);
            } else {
                // certifiables = createCertifiables(entity.getExceptions(apps));
                certifiables = createCertifiables(getCombinedExceptions(entity, apps));
            }
            return certifiables;
        }

        /**
         * Prior to 7.2 Certifiables were created from the "exceptions" list
         * of the Identity and this included target permissions.  In 7.2 we removed
         * targetPermissions from the Links and they will no longer appear in the
         * exceptions list, you have to query for them.  
         *
         * I see there was a start toward something similar with 
         * createCertifiablesFromIdentityEntitlements but it has always been
         * disabled.  If we ever decide to finish that we could remove this
         * since target permissions will be IdentityEntitlements.
         * jsl
         *
         * In 8.1 we removed target permission from here and handled them separately in
         * {@link #getTargetPermissionCertifiables(sailpoint.object.AbstractCertifiableEntity, List<Application>)}
         */
        private List<EntitlementGroup> getCombinedExceptions(AbstractCertifiableEntity entity,
                                                             List<Application> apps)
            throws GeneralException {


            List<EntitlementGroup> exceptions = new ArrayList<>();
            // clone the old list
            for (EntitlementGroup eg : Util.safeIterable(entity.getExceptions(apps))) {
                exceptions.add((EntitlementGroup)eg.deepCopy((Resolver)context));
            }

            if (entity instanceof Identity) {
                Identity identity = (Identity)entity;

                // Create entitlement groups for any links that dont' already have them
                IdentityService svc = new IdentityService(context);
                for (Link link : Util.iterate(svc.getLinks(identity, apps, null))) {
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
                        if (perms != null) {
                            perms.removeIf((permission -> (permission.getAggregationSource() != null)));
                        }
                    }
                }
            }

            return exceptions;
        }
        
        /**
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
                ops.add(Filter.isnull("sourceRoles"));
                
                List<String> fields = Arrays.asList("application", "instance", "nativeIdentity", "displayName", "name", "value");
                Iterator<Object[]> rows = context.search(IdentityEntitlement.class, ops, fields);
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
            return createCertifiables(entitlementGroups);            
        }


        /**
         * In 8.1 we separated target permissions from other "exceptions", they can be optionally excluded
         */
        private List<Certifiable> getTargetPermissionCertifiables(AbstractCertifiableEntity entity, List<Application> apps)
                throws GeneralException {

            List<Certifiable> targetPermissionCertifiables = new ArrayList<>();
            if (entity instanceof Identity) {
                Identity identity = (Identity) entity;
                IdentityService svc = new IdentityService(context);
                List<Link> links = svc.getLinks(identity, apps, null);
                for (Link link : Util.iterate(links)) {
                    List<Permission> targetPermissions = ObjectUtil.getTargetPermissions(context, link);
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

        public List<Certifiable> getAccountCertifiables(AbstractCertifiableEntity entity,
                                                   List<Application> apps)
            throws GeneralException {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity.id", entity.getId()));
            if (apps != null && !apps.isEmpty())
                ops.add(Filter.in("application", apps));
            return createAccountCertifiables(ops);
        }

        public List<Certifiable> getEmptyAccountCertifiables(AbstractCertifiableEntity entity,
                                                   List<Application> apps)
            throws GeneralException {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity.id", entity.getId()));
            ops.add(Filter.eq("entitlements", false));
            if (apps != null && !apps.isEmpty())
                ops.add(Filter.in("application", apps));
            return createAccountCertifiables(ops);
        }

        private List<Certifiable> createAccountCertifiables(QueryOptions ops) throws GeneralException{
            List<Certifiable> exps = new ArrayList<Certifiable>();
            List<String> fields = Arrays.asList("application", "instance", "nativeIdentity", "displayName");
            Iterator<Object[]> accounts = context.search(Link.class, ops, fields);
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

        /**
         * Create a list of certifiables from the given list of entitlements,
         * appropriately expanding them based on the entitlement granularity.
         *
         * @param  ents  The entitlements to use to create certifiables.
         */
        List<Certifiable> createCertifiables(List<? extends Entitlements> ents)
            throws GeneralException {

            List<Certifiable> certifiables = new ArrayList<Certifiable>();

            if (null != ents) {

                switch (definition.getEntitlementGranularity()) {

                case Application:
                    certifiables.addAll(ents);
                    break;

                case Attribute:
                    List<Entitlements> split =
                        EntitlementGroup.splitToAttributes(ents);
                    for (Entitlements ent : split) {
                        certifiables.add((Certifiable) ent);
                    }
                    break;

                case Value:
                    split = EntitlementGroup.splitToValues(ents);
                    for (Entitlements ent : split) {
                        certifiables.add((Certifiable) ent);
                    }
                    break;

                default:
                    throw new GeneralException("Cannot create exceptions for granularity: " +
                                               definition.getEntitlementGranularity());
                }
            }

            return certifiables;
        }

        /**
         * Default to returning no sub-contexts.  Subclasses should override if
         * they can generate subordinate certifications.
         */
        public List<CertificationContext> getSubordinateContexts(AbstractCertifiableEntity entity)
            throws GeneralException {

            return null;
        }

        /**
         * Get the name of the given entity - either the fullname or the name
         * from getName().
         *
         * @param  entity  The entity from which to get the name.
         *
         * @return The name of the given entity.
         */
        public String getName(AbstractCertifiableEntity entity) {
            String name = entity.getFullName();
            if ((null == name) || (0 == name.length())) {
                name = entity.getName();
            }
            return name;
        }

        public List<CertificationGroup> getCertificationGroups() {
            return certificationGroups;
        }

        public void setCertificationGroups(List<CertificationGroup> certificationGroups) {
            this.certificationGroups = certificationGroups;
        }

        /**
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
        protected void assimilateLinkAttributes(Identity identity, Entitlements entitlements,
                CertificationItem item) throws GeneralException {
                    
                    Meter.enter(118, "IdentityCertificationBuilder: Assimlate Link Attributes");
                    if ( ( identity == null ) || ( entitlements == null ) ) {
                        return;
                    }
                    
                    Application app = entitlements.getApplicationObject(context);
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
                    
                    Meter.exit(118);
                }

        /**
         * Check status of recommendations in this cert.
         *
         * @return true if recommendations are available, otherwise false
         */
        protected boolean areRecommendationsAvailable() {

            return false;
        }

        /**
         * Check if the 'Show Recommendations' option was enabled for this cert.
         *
         * @return true if enabled, otherwise false
         */
        protected boolean isShowRecommendations() {

            return showRecommendations;
        }

        /**
         * Create a recommendation builder that is able to build recommendation requests
         * for the items certifiable in the given entity.
         *
         * @param entity The entity being certified.
         */
        protected void createRecommendationRequestBuilder(AbstractCertifiableEntity entity) {

            if (log.isDebugEnabled()) {
               log.debug("This certification type does not support recommendations.");
            }
        }

        /**
         * Adds a certification item to the builder.
         *
         * @param item The CertificationItem to generate a request for.
         * @param certifiable The Certifiable that the CertificationItem is based on.
         */
        protected void addItemToRecommendationRequest(CertificationItem item, Certifiable certifiable) {

            if (log.isDebugEnabled()) {
                log.debug("This certification type does not support recommendations.");
            }
        }

        /**
         * Retrieves recommendations for the certification items provided to the builder.
         * If successful, the results are added to the certification items.
         */
        protected void populateRecommendations() {

            if (log.isDebugEnabled()) {
                log.debug("This certification type does not support recommendations.");
            }
        }

        @Override
        public boolean isExcluded(Certification cert, AbstractCertifiableEntity entity) throws GeneralException {
            boolean excluded = false;
            Meter.enter(110, "CertificationBuilder: Get certifiables");
            List<Certifiable> certifiables = new ArrayList<Certifiable>();
            if (entity != null)
                certifiables = getCertifiables(entity);
            Meter.exit(110);
            
            // if we have an exclusion rule, then run it
            if ( definition.getExclusionRuleName() != null ) {
                if ( certifiables != null && certifiables.size() > 0 ) {
                        // run an exclude rule that is allowed to filter the
                        // certifiables list by removing items or moving the
                        // items to an exclusion list that can be persisted
                    List<Certifiable> itemsToExclude = new ArrayList<Certifiable>();
                    String explanation =
                        runExcludeRule(entity, cert, certifiables, itemsToExclude);

                    if (definition.getSaveExclusions() && (itemsToExclude != null) &&
                        (itemsToExclude.size() > 0)) {
                        excluded = true;
                    }
                    
                    //May be an identity w/o items, but we still don't want it moved out of archive
                    if(certifiables != null || certifiables.size() == 0 || explanation != null) {
                        excluded = true;
                    }
                    
                } else {
                    excluded = true;
                }
            }
            
            return excluded;
        }
    }
}
