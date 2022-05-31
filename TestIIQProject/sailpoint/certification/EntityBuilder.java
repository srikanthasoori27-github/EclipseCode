/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Logic related to the construction of CertificationEntity from Identity.
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
 * The old framework is horribly difficult to understand the way control
 * flow bounces between the various classes.  It is true that some amount
 * of refactoring may be desireable as we use the new framework more, but
 * I'm starting by just getting the code in one place so we can understand
 * it and start modifying it without breaking things.  At the end there
 * will likely be a fair bit that could be factored out and shared between
 * the new and old frameworks but that will be done later.  
 *
 * Unfortunately what this means is that we essentially have a fork of
 * a pile of very complicated code that will need to be kept in sync.  Since
 * Certificationer isn't under active development, this shouldn't be much
 * of a problem but we will need to communicate with sustaining.
 *
 * TODO: 
 * 
 * Try to have this not get things from the Certification, get them
 * consistently from the CertificationDefinition so we can be more flexible
 * in what Certification the entities will be attached to.
 * 
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CachedManagedAttributer;
import sailpoint.api.CertificationEntitlizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.Differencer;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Explanator;
import sailpoint.api.Explanator.Explanation;
import sailpoint.api.IdentityArchiver;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.ArchivedCertificationEntity.Reason;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.Certifiable;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationLink;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.Rule;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class EntityBuilder {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(EntityBuilder.class);
    
    SailPointContext _context;

    /**
     * TaskMonitor to update progress into.
     * Also has a handle to the TaskResult
     */
    TaskMonitor _monitor;

    /**
     * The bag of cert generation options.
     */
    CertificationDefinition _definition;

    /**
     * The root Certification we're building around.
     * We won't actually add entities to this, but it is needed in 
     * old code and to pass to rules.
     */
    Certification _certification;

    /**
     * BaseCertificationBuilder warnings list which was only used by
     * getDelegate.  Revisit this and see if we can remove it.
     */
    List<Message> _warnings;

    //
    // Various rule caches
    // 
    
    Rule _exclusionRule;
    Rule _entityCustomizationRule;
    Rule _itemCustomizationRule;
    Rule _preDelegationRule;

    /**
     * A rule that is run when refreshing the completion of a CertificationItem
     * to provide a hook that can limit completion of a certification.
     */
    Rule _itemCompletionRule;

    /**
     * A rule that is run when refreshing the completion of a
     * CertificationEntity to provide a hook that can limit completion of a
     * certification.
     */
    Rule _entityCompletionRule;
    
    /**
     * Rule called to determine the certifiers for an entity.
     */
    Rule _certifierRule;

    /**
     * Partitially constructed map of arguments to the rules.
     */
    Map<String,Object> _certifierRuleArgs;
    Map<String,Object> _delegationRuleArgs;
    Map<String,Object> _customizationRuleArgs;
    Map<String,Object> _exclusionRuleArgs;

    /**
     * Map passed into every rule call allowing rules to to save
     * state between calls.  Not sure what this has been used for but
     * in theory it could be to cache the result of an expensive 
     * operation.
     */
    Map<String,Object> _ruleState;
    
    /**
     * The archiver is used to make IdentitySnapshots that capture
     * the state of an Identity at the moment it was prepared for
     * certificdation.
     *
     * jsl - should REALLY try to get rid of this
     */
    IdentityArchiver _archiver;

    /**
     * The correlator is used to derive the specific attributes and
     * permissions held by an identity that caused it to be assigned
     * to a bundle.
     * 
     * jsl - should REALLY try to make this optional
     */
    EntitlementCorrelator _correlator;
    
    // various things from Certificationer
    
    /**
     * From Certificationer, part of the refresh process after
     * generating the cert.  This looks horribly expensive.
     */
    CertificationEntitlizer _entitlizer;
    
    /**
     * Utility class that knows how to create Certiable objects from
     * an Identity.
     */
    CertifiableAnalyzer _certifiableAnalyzer;

    /**
     * Utility class that converts Certifiable to CertificationItem.
     */
    ItemBuilder _itemBuilder;

    /**
     * Utility class that maintains a set of Certification objects keyed
     * by certifier names and bootstraps them if they do not exist.
     */
    CertificationCache _certificationCache;

    /**
     * Name of the default certifier that is assigned the root cert.
     * This will either be the backup certifier or the manually selected one.
     * Used by Owner certifier type to locate the root cert since the logic
     * is more complicated than just referencing _certification
     */
    String _defaultCertifier;
    
    /**
     * Number of entities to process before decaching.
     */
    int _maxCacheAge;

    // Parsed certifier selection options from the definition so we don't
    // have to keep comparing strings
    // There is a CERTIFIER_OWNER_TYPE_ROLE_OWNER but that's the only option right?

    CertifierSelectionType _certifierType;

    // valid only when _certifierType=Owner
    boolean _certifierApplicationOwner;
    boolean _certifierEntitlementOwner;

    /**
     * Cache of information about application entitlements.
     */
    ApplicationCache _applications;

    /**
     * Cache of Identity type to Manager Certifier Attribute.
     * Ex. cache.get("rpa") returns "administrator" OOTB
     */
    Map<String, String> _identityTypeAttributeCache;

    /**
     * Termination flag that can be propagated from the RequestExecutor.
     */
    boolean _terminate;

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Build the builder.
     * This is approxomately equal to the constructors for 
     * BaseCertificationBuilder and BaseIdentityCertificationBuilder,
     * and the constructors for BaseCertificationContext and
     * BaseIdentityCertificationContext.
     *
     * After creating the context CBC and BICB have an init() and
     * finalize() methods.  Will need those.
     *
     * If CertifierSelectionType is Manual, the passed cert will be owned
     * by the selected identity.  For other types it will be owned
     * by the backup certifier.
     */
    public EntityBuilder(SailPointContext context,
                         TaskMonitor monitor,
                         CertificationDefinition def,
                         CertificationGroup group,
                         Certification cert,
                         boolean showRecommendations)
        throws GeneralException {

        _context = context;
        _monitor = monitor;
        _definition = def;
        _certification = cert;

        // will want this configurable in the CertificationDefinition
        // Certificationer defaults to 100 and it can be overridden in
        // system config, but I'd rather to it from the TaskDefinition
        // Note that this is significant.  Dropping this to 1 doubles the amount
        // of time it takes to do exception items, proabbly because of the refetch
        // of the Application.  
        _maxCacheAge = 100;
        
        Configuration config = _context.getConfiguration();
        if (config.containsAttribute(Configuration.CERTIFICATION_ENTITY_DECACHE_INTERVAL)){
            _maxCacheAge = config.getInt(Configuration.CERTIFICATION_ENTITY_DECACHE_INTERVAL);
        }

        initRules();
        
        _applications = new ApplicationCache(_context);
        
        // BaseCertificationBuilder
        // what is results for?
        //this.results = new HashMap<String,Object>();

        //this.global = definition.isGlobal();
        
        // is this the right place to initialize?
        //this.includedAppIds = ObjectUtil.convertToIds(ctx, Application.class, definition.getIncludedApplicationIds());
        
        // stuff from BaseIdentityCertificationBuilder.initialize
        // it has a way to pass in an EntitlementCorrelator, we don't
        // need that, I don't understand the entitlementMappingCache...
        initializeCorrelator();
        
        // if you make one of these without a correlator, 
        // it will also use the cache in the Identity
        // jsl - what does that mean?
        _archiver = new IdentityArchiver(_context, _correlator);

        // BaseIdentityCertitifationContext can take a list of owners
        // how we manage ownership is still TBD

        CachedManagedAttributer cma = new CachedManagedAttributer(_context);
        _certifiableAnalyzer = new CertifiableAnalyzer(_context, _definition, _applications);
        _itemBuilder = new ItemBuilder(_context, _definition, _itemCustomizationRule, _correlator, showRecommendations, cma);
        _certificationCache = new CertificationCache(_context, _definition, group, _certification);

        // root cert will be the cache, get the certifier
        // name for use later for Owner type certifier assignment
        // if we need to get to the backup cert
        _defaultCertifier = _certificationCache.getDefaultCertifier();

        // at roughly this point, CertificationBuilderFactory.setCommonOptions
        // would give the builder a List<Identity> of "owners" and give
        // it the task schedule id

        // disable dirty checking
        PersistenceOptions ops = new PersistenceOptions();
        ops.setExplicitSaveMode(true);
        _context.setPersistenceOptions(ops);

        // Extract method of certifier selection
        _certifierType = _definition.getCertifierSelectionType();
        // should be set, but for testing fallback to manual
        if (_certifierType == null)
            _certifierType = CertifierSelectionType.Manual;
        
        if (_certifierType == CertifierSelectionType.Owner) {
            // set booleans so we don't have to keep comparing strings
            String ownerType = _definition.getCertifierOwnerEntitlement();
            if (CertificationDefinition.CERTIFIER_OWNER_TYPE_APPLICATION_OWNER.equals(ownerType)) {
                _certifierApplicationOwner = true;
            }
            else if (CertificationDefinition.CERTIFIER_OWNER_TYPE_ENTITLEMENT_OWNER.equals(ownerType)) {
                _certifierEntitlementOwner = true;
            }
            else {
                log.error("Invalid certifier owner type: " + ownerType);
                _certifierApplicationOwner = true;

            }
        }
        else if (_certifierType == CertifierSelectionType.Manual) {
            // currently assuming that the root cert was assignged
            // to a selected identity rather than the backup certifier
            // if this changes, will have to keep both around
            /*
            String certifierName = _definition.getCertifier();
            if (certifierName == null) {
                // TODO: fall back to root cert or throw?
                log.error("Manual certifier selection with missing certifier name");
                _manualCertification = _certification;
            }
            else {
                Identity ident = _context.getObject(Identity.class, certifierName);
                if (ident == null) {
                    // TODO: fall back to root cert or throw?
                    log.error("Manual certifier selection with invalid certifier name: " + certifierName);
                    _manualCertification = _certification;
                }
                else {
                    _manualCertification = _certificationCache.getCertification(certifierName);
                }
            }
            */
        }
        else if (_certifierType == CertifierSelectionType.Rule) {
            // initRules will have resolved this
            // how tolerant should we be here?  This should have been caught
            // by IdentityCertificationStarter before we even got around to creating partitions
            if (_certifierRule == null) {   
                log.error("Missing certifier rule");
            }
        }

        buildIdentityTypeAttributeCache();
    }

    /**
     * Build a cache of identity types to manager certifier attributes.
     * This is used when calculating the manager owner of a certification entity and if there
     * are custom overrides to choose someone else besides the manager.
     */
    private void buildIdentityTypeAttributeCache() {
        if (_identityTypeAttributeCache == null) {
            _identityTypeAttributeCache = new HashMap<>();
        }

        ObjectConfig config = Identity.getObjectConfig();
        if (config != null) {
            for (IdentityTypeDefinition typeDef : Util.iterate(config.getIdentityTypesList())) {
                addToCache(typeDef, config);
            }
            IdentityTypeDefinition defaultTypeDef = config.getDefaultIdentityTypeDefinition();
            addToCache(defaultTypeDef, config);
        }

    }

    /**
     * Helper to add an Identity type to the cache
     * @param typeDef The IdentityTypeDefinition to add
     * @param config Identity ObjectConfig to lookup attribute types
     */
    private void addToCache (IdentityTypeDefinition typeDef, ObjectConfig config) {
        if (typeDef != null) {
            String attr = typeDef.getManagerCertifierAttribute();
            if (Util.isNotNullOrEmpty(attr)) {
                ObjectAttribute objAttr = config.getObjectAttribute(attr);

                // first validate if it is an Identity type
                if (objAttr != null) {
                    if (!ObjectAttribute.TYPE_IDENTITY.equals(objAttr.getType())) {
                        if (log.isWarnEnabled()) {
                            log.warn("The manager certifier attribute [" + attr + "] is not an Identity type. " + 
                                    "Please check your override settings for [" + typeDef.getName()  +"] Identity Type Definition.");
                        }
                    }
                }
                // regardless, add the type so that the overriding behavior knows that there was an override attempted
                _identityTypeAttributeCache.put(typeDef.getName(), attr);
            }
        }
    }
    /**
     * BaseIdentityCertificationBuilder
     */
    private void initializeCorrelator() {
        
        boolean useEntitlementMappingCache = false;
        try {
            Configuration syscon = _context.getConfiguration();
            useEntitlementMappingCache = syscon.getBoolean("useEntitlementMappingCache");
        }
        catch (Throwable t) {}

        if (!useEntitlementMappingCache) {

            // since these use each other, construct them now
            _correlator = new EntitlementCorrelator(_context);

            // let this load things now so we can clean up the metering
            try {
                _correlator.prepare();
            }
            catch (GeneralException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Rules
    //
    // Some rule names are in the CertificationDefinition and some
    // are in the Certification, and some in the system Configuration.
    // Check on this, the Certification ones are probably just
    // copies from the CertificationDefinition.  Would like to not
    // require a Certification here.
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Pre load all of the rules we may want to use.
     * This is different than the way Certificationer loaded on demand
     * so in the usual case where we have no rules configured we don't
     * keep looking for them every time.
     *
     * Rules names can be in several places and it is not consistent.
     * Consider whether it should be - jsl
     */
    private void initRules() throws GeneralException {

        // from only the CertificationDefinition
        _exclusionRule =  loadRule(_definition.getExclusionRuleName());

        // from only the CertificationDefinition
        _preDelegationRule = loadRule(_definition.getPreDelegationRuleName());
        
        // context is passed to fall back in system config
        _itemCustomizationRule = loadRule(_definition.getItemCustomizationRuleName(_context));

        // oddly enough this does not fall back to system config
        _entityCustomizationRule = loadRule(_definition.getEntityCustomizationRuleName());

        // Certificationer had it's own getRule method that would let the
        // Certification define this or else fall back to system config, but
        // that is just a copy of what was in the CertificationDefinition
        // so can simplify here and not require the Certification
        _entityCompletionRule =
            getRule(Configuration.CERTIFICATION_ENTITY_COMPLETION_RULE, _certification);

        // Certificationer always got this from just the system config
        Configuration config = _context.getConfiguration();
        String ruleName = config.getString(Configuration.CERTIFICATION_ITEM_COMPLETION_RULE);
        _itemCompletionRule = loadRule(ruleName);

        // this is a new flexi cert rule for entity assignment
        _certifierRule = loadRule(_definition.getCertifierRule());

        // since we're calling this all the time, initialize the args map
        // and just change the entity
        _ruleState = new HashMap<String,Object>();
        _certifierRuleArgs = new HashMap<String,Object>();
        _certifierRuleArgs.put("certification", _certification);
        // this is new, prefer this over getting it from the Certification
        _certifierRuleArgs.put("definition", _definition);
        _certifierRuleArgs.put("state", _ruleState);

        // this can change the certification on each call
        _delegationRuleArgs = new HashMap<String,Object>();
        _delegationRuleArgs.putAll(_certifierRuleArgs);
        
        // adds certifiableEntity and entity
        _customizationRuleArgs = new HashMap<String,Object>();
        _customizationRuleArgs.putAll(_certifierRuleArgs);
        
        // adds several things
        _exclusionRuleArgs = new HashMap<String,Object>();
        _exclusionRuleArgs.putAll(_certifierRuleArgs);
    }
    
    /**
     * Read the rule object and load it so it can be called across
     * decaches.  Probably have a dozen of these around the system.
     * Used for rules named in the CertificationDefinition
     */
    private Rule loadRule(String name) throws GeneralException{

        Rule rule = null;

        if (name != null) {
            rule = _context.getObjectByName(Rule.class, name);
            if (rule != null) {
                rule.load();
            }
            else {
                // now that we pre load rules once, we can warn here
                log.error("Invalid rule name: " + name);
            }
        }
        
        return rule;
    }

    /**
     * Return a rule configured either in the given certification or in the
     * system configuration with the given configuration key.
     * 
     * jsl - I don't like how we're requiring the Certification here, 
     * check to see if this is just a copy from the CertificationDefinition
     * and use that instead.
     */
    private Rule getRule(String configAttrName, Certification cert)
        throws GeneralException {

        Rule rule = null;

        String ruleName = (String)cert.getAttribute(configAttrName, _context.getConfiguration().getAttributes());

        if (ruleName != null) {
            rule = loadRule(ruleName);
        }

        return rule;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // CertificationEntity Generation
    // 
    //////////////////////////////////////////////////////////////////////

    public void terminate() {
        _terminate = true;
    }

    /**
     * Create entities for a list of identities.
     */
    public void buildEntities(List<String> names)
        throws GeneralException {

        final String MeterName = "EntityBuilder: buildEntities";
        Meter.enter(MeterName);
        try {
            int count = 0;
            int logCount = 0;
        
            for (String name : Util.iterate(names)) {
                Identity ident = _context.getObjectByName(Identity.class, name);
                if (ident == null) {
                    log.warn("Identity was deleted: " + name);
                }
                else {
                    CertificationEntity ent = buildEntity(ident);
                    count++;
                    if (count >= _maxCacheAge) {
                        count = 0;
                        _context.decache();
                    }
                    logCount++;
                    if (log.isInfoEnabled()) {
                        if ((logCount % 500) == 0) {
                            log.info(Util.itoa(logCount));
                        }
                    }
                }

                if (_terminate)
                    break;
            }

            if (!_terminate)
                saveEntitiesToRefresh();
        }
        finally {
            Meter.exit(MeterName);
        }
    }

    /**
     * Create an unattached CertificationEntity from an Identity.
     * This is approxomately what Certificationer.addEntity does.
     * 
     * There are three main phases to this:
     *
     *    - basic construction of the entity
     *    - refreshEntityStatus which can run completion rules
     *    - renderDifferences
     *
     * Entity construction is what BaseIdentityCertificationBuilder
     * did.  Status refresh and difference rendering was done by
     * Certificationer.
     *
     * Auto-completion and the execution of completion rules has been
     * removed.  On generation, we should not have to worry about objects
     * being deleted out from under the cert.  
     */
    public CertificationEntity buildEntity(Identity ident)
        throws GeneralException {

        if (log.isDebugEnabled()) {
            log.debug("Building entity for: " + ident.getName());
        }
        
        CertificationEntity entity = null;
        final String MeterName = "EntityBuilder: buildEntity";
        Meter.enter(MeterName);
        try {
            entity = createCertificationEntity(_certification, ident);

            if (entity != null) {

                // assign ownership, this can split the entity
                List<CertificationEntity> owned = assignOwnership(ident, entity);
                for (CertificationEntity split : Util.iterate(owned)) {

                    // Pre-delegate the entity if a rule is defined, must
                    // be in a cert by now
                    preDelegate(split);
            
                    // Set entity status.
                    // jsl - since we are not part of general refresh, we can just
                    // create them with the right status can't we?
                    refreshEntityStatus(_certification, split);

                    // calculate changes since the previous certification and copy
                    // over some of the previous state.
                    // !! make this conditional
                    // !! how is this going to work with split entities?
                     renderDifferences(_certification, split, ident);
                     
                    final String CommitName = "EntityBuilder: commit";
                    Meter.enter(CommitName);
                    try {
                        _context.saveObject(split);
                        // commit every time for now
                        // remake this to use explicit save mode
                        _context.commitTransaction();
                    }
                    finally {
                        Meter.exit(CommitName);
                    }
                }
            }
        
            // at this point Certificationer.addEntities would process
            // subordinate contexts
        }
        finally {
            Meter.exit(MeterName);
        }

        return entity;
    }

    /**
     * If we pre-delegated, the Certification will have a list of entity
     * ids on the entitiesToRefresh list.  For old cert gen this would pass 
     * directly to Certificationer.start.  For focused cert gen, we don't
     * have exlusive ownership of the Certification and cert starting 
     * will not happen until another phase, so we have to persist the list
     * before this partition ends.
     *
     * Think about reworking this so Certificationer can make this determination
     * without needing the entitiesToRefresh list immediately after generation.
     * It can assume that if the cert phases to Active then any entity with
     * a delegation requires a work item.  That would be so much easier and faster.
     */
    private void saveEntitiesToRefresh() throws GeneralException {


        for (Certification cert : Util.iterate(_certificationCache.getCertifications())) {

            // glenn was seeing NPEs on the next line, why
            // actually, I think this was due to the bug in CertificationCache
            // that was creating entries with null values, take this out if
            // we don't see any warnings
            if (cert == null) {
                log.warn("Null certification in cache");
                continue;
            }

            // ugh, this is awkward
            // since entities will not have had an id at the time they
            // were delegated, it will be added to this list
            // the assumption here is that this is the same object that
            // was eventually saved and will now have an id
            Set<CertificationEntity> ents = cert.getFullEntitiesToRefresh();
            if (ents != null && ents.size() > 0) {
                try {
                    _context.decache();
                    Certification locked = ObjectUtil.transactionLock(_context, Certification.class, cert.getId());
                    if (locked == null) {
                        log.error("Certification evaporated");
                    }
                    else {
                        for (CertificationEntity ent : ents) {
                            String id = ent.getId();
                            if (id == null) {
                                log.error("Delegated entity without id: " + ent.getName());
                            }
                            else {
                                locked.addEntityToRefresh(ent);
                            }
                        }
                    }
                    _context.saveObject(locked);
                    _context.commitTransaction();
                }
                finally {
                    // ensure the lock is released if we threw
                    _context.rollbackTransaction();
                }
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Ownership
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Assign the entity to a Certification
     * Depending on ownership options this may end up splitting the entity
     * items into multiple entities.
     */
    private List<CertificationEntity> assignOwnership(Identity ident, CertificationEntity entity)
        throws GeneralException {

        List<CertificationEntity> ents = null;

        if (_certifierType == CertifierSelectionType.Owner) {
            ents = assignOwnershipByOwner(entity);
        }
        else {
            // the rest do not split the entity
            ents = new ArrayList<CertificationEntity>();
            ents.add(entity);

            // ugh, I hate how emacs formats switches
            switch (_certifierType) {
            case Manual: {
                // root cert will have the certifier selected in the definition
                // rather than the backup certifier
                // TODO: if this changes, if we have both a backup cert and
                // a manual owner cert then will need to adjust the logic
                entity.setCertification(_certification);
            } break;
                
            case Manager: {
                // Calculate the owner based on the IdentityTypeDefinition override if there is one
                Identity owner = calculateManagerOwner(ident);
                if (owner == null) {
                    // TODO: explore options, leave in root cert for now
                    // could have options to just ignore
                    entity.setCertification(_certification);
                }
                else {
                    Certification target = _certificationCache.getCertification(owner.getName());
                    entity.setCertification(target);
                }
            } break;
                
            case Rule: {
                assignOwnershipWithRule(entity);
            } break;
            }
        }

        return ents;
    }

    /**
     * Calculates the owner based on if there is an override for the type of Identity it is.
     * When the Owner is set to Manager, look first if there is a Manager Override to be used instead of the Manager.
     * This is used OOTB for RPA/Bots where it will look in the Administrator field instead of the Manager field
     * @param ident The Identity to look in the ObjectConfig for an override for the type
     * @return The Owner of the certification entity based on the override attribute, otherwise the Manager of the Identity
     */
    private Identity calculateManagerOwner(Identity ident) {
        Identity owner = null;
        if (ident != null) {
            // set manager as the default behavior as the owner.
            owner = ident.getManager();

            String identityType = ident.getType();
            if (identityType == null) {
                // Identity doesn't have a type, try the default type definition
                identityType = IdentityTypeDefinition.DEFAULT_TYPE_NAME;
            }

            if (_identityTypeAttributeCache.containsKey(identityType)) {
                String overrideAttr = _identityTypeAttributeCache.get(identityType);

                if (Util.isNotNullOrEmpty(overrideAttr) && !Identity.ATT_MANAGER.equals(overrideAttr)) {
                    // If manager is defined as the override attribute, it will already be set as the default behavior
                    if (Identity.ATT_ADMINISTRATOR.equals(overrideAttr)) {
                        owner = ident.getAdministrator();
                    }
                    // If we add more Identity type standard attrs, add along this if block.
                    else {
                        // getAttribute should return the identity of the attribute of non standard attribute
                        // cannot use this for manager and administrator since those return the names.
                        Object ownerOverride = ident.getAttribute(overrideAttr);
                        if (ownerOverride == null || !(ownerOverride instanceof Identity)) {
                            // if the user defined a custom override and it doesn't exist, we must still respect it and not just
                            // default to the manager.
                            owner = null;
                        } else {
                            owner = (Identity) ownerOverride;
                        }
                    }
                }
            }
        }

        return owner;
    }

    /**
     * Determine the Certification that an entity should be a member of
     * using a Rule. If the rule returns NULL, the entity
     * will go in the root cert.  If it returns non-null we use 
     * CertificationCache to locate or bootstrap the correct one.
     *
     * This is similar to the old delegation/reassignment rule.
     * We still use that rule for delegation but not for reassignment.
     * 
     * Rule arguments:
     *    context (implicit)
     *    certification (root cert)
     *    definition (new)
     *    entity 
     *    state
     *
     * Old framework also passed certContext, we no longer have that.
     * Old framework expected these returns:
     * 
     *    recipient (Identity)
     *    recipientName
     *    delegate, delgateName (backward compatibility)
     *    description
     *    commands
     *    certificationName
     *    reassign (boolean)
     *
     * certificationName was never used.  reassign is no longer supported.
     * delegate and delegateName are no longer supported.
     *
     * We could support return an Identity but since this is a new rule
     * I'd rather just simplify it to String name or List<String>
     * 
     * description and comments were stored on the reassignment cert
     * and later used in notifications.  Consider supporting that.
     *
     */
    private void assignOwnershipWithRule(CertificationEntity entity)
        throws GeneralException {

        // if they didn't specify a rule or the rule doesn't return
        // anything leave it in the root cert
        Certification target = _certification;
        
        if (_certifierRule != null) {
            _certifierRuleArgs.put("entity", entity);
            Object result = _context.runRule(_certifierRule, _certifierRuleArgs);
            if (result != null) {
                if (result instanceof String) {
                    target = _certificationCache.getCertification((String)result);
                }
                else if (result instanceof List) {
                    // it must be a list of names
                    target = _certificationCache.getCertification((List<String>)result);
                }
                else {
                    // TODO: Support Map like the old rule...
                    log.error("Invalid certifier rule result: " + result);
                }
            }
        }
        
        entity.setCertification(target);
    }

    /**
     * Split the items in this entity by owner and assign them
     * to owner certs.
     *
     * First we build a collection of entities for each owner 
     * we encounter in the item list.  Then we iterate over
     * that and assign them to the certs.
     *
     * Items may remain in the source entity if we decide
     * to use the backup certification.
     */
    private List<CertificationEntity> assignOwnershipByOwner(CertificationEntity src)
        throws GeneralException {

        // split entities, keyed by owner
        Map<String,CertificationEntity> entities = new HashMap<String,CertificationEntity>();

        List<CertificationItem> items = src.getItems();
        if (items != null) {
            ListIterator<CertificationItem> it = items.listIterator();
            while (it.hasNext()) {
                CertificationItem item = it.next();
                CertificationItem.Type type = item.getType();
                
                if (type == CertificationItem.Type.Bundle) {
                    // we've also got SubType.AssignedRole if we need to distinguish
                    // consider saving the owner in a transient field on the CertificationItem
                    // so we don't have to hit the session
                    String roleName = item.getTargetName();
                    Bundle role = _context.getObjectByName(Bundle.class, roleName);
                    if (role == null) {
                        log.error("Invalid role name: " + roleName);
                    }
                    else {
                        Identity owner = role.getOwner();
                        if (owner != null)  {
                            it.remove();
                            addItem(src, item, owner.getName(), entities);
                        }
                        else {
                            // warn?  
                            // else leave it in the source entity so it remains
                            // in the root cert
                        }
                    }
                }
                else if (type == CertificationItem.Type.Exception) {
                    addExceptionItems(src, item, entities);
                    // the item may have been split so remove the whole
                    // thing and rely on the distribution in the entities map
                    it.remove();
                }
                else if (type == CertificationItem.Type.PolicyViolation) {
                    // leave in root until we discuss othter options
                }
                else if (type == CertificationItem.Type.Account) {
                    addAccountItem(src, item, entities);
                    it.remove();
                }
                else if (type != null) {
                    // others should not appear in an identity cert
                    log.error("Unsupported item type: " + type.toString());;
                }
                else {
                    log.error("Missing item type");
                }
            }
        }

        List<CertificationEntity> result = new ArrayList<CertificationEntity>();

        Set<String> owners = entities.keySet();
        for (String owner : owners) {
            CertificationEntity ownerEnt = entities.get(owner);
            Certification target = _certificationCache.getCertification(owner);
            ownerEnt.setCertification(target);
            result.add(ownerEnt);
        }

        // if the original source entity is not empty, the remainder goes to the root
        if (items.size() > 0) {
            src.setCertification(_certification);
            result.add(src);
        }
        
        return result;
    }

    /**
     * Helper for assignOwnershipByOwner.
     * Given an item and an owner, look for a CertificationEntity created
     * for that owner, and create one if not found yet.
     */
    private void addItem(CertificationEntity src, CertificationItem item,
                         String owner, Map<String,CertificationEntity> entities)
        throws GeneralException {

        CertificationEntity ent = entities.get(owner);
        if (ent == null) {
            // clone it with base properties
            ent = new CertificationEntity(src);
            entities.put(owner, ent);
        }

        ent.add(item);
    }        

    /**
     * Distribute exception entitlement items to owners.
     * This is harder than the others since the item itself
     * may have to be split based on the ownership of the ManagedAttribute.
     * hmm, I think this may not be the case now that we use force Value granularity
     * I'm going with that for now since it really complicates things to 
     * have to split items.
     */
    private void addExceptionItems(CertificationEntity src, CertificationItem item,
                                   Map<String,CertificationEntity> entities)
        throws GeneralException {
    
        EntitlementSnapshot snapshot = item.getExceptionEntitlements();
        String owner = _defaultCertifier;

        if (_certifierApplicationOwner) {
            String appName = snapshot.getApplication();
            ApplicationCache.ApplicationInfo info = _applications.get(appName);
            // if null it means that app is missing and has already beenlogged
            if (info != null) {
                String appOwner = info.getOwner();
                if (appOwner != null)
                    owner = appOwner;
            }
            addItem(src, item, owner, entities);
        }
        else {
            String appName = snapshot.getApplication();
            Attributes<String,Object> atts = snapshot.getAttributes();
            List<Permission> perms = snapshot.getPermissions();
            int numAtts = ((atts != null) ? atts.size() : 0);

            if (perms != null && numAtts > 0) {
                // we have merged atts and perms would have to split the item
                log.error("Exception snapshot with both entitlements and permissions");
            }
            else if (perms != null) {
                // these don't have owners so I guess dump them all in
                // the default cert
                addItem(src, item, _defaultCertifier, entities);
            }
            else if (numAtts > 1) {
                // mulitiple entitlements, would have to split
                // omg, with value granularity the item model must be ENORMOUS
                // would really like to change this
                log.error("Exception snapshot with multiple entitlements");
            }
            else if (numAtts == 1) {
                Set<String> keySet = atts.keySet();
                // gak, no indexing in Sets, how much garbage can we generate!
                Object[] keys = keySet.toArray();
                String name = (String)(keys[0]);
                Object value = atts.get(name);
                if (value instanceof Collection) {
                    // someone didn't turn on Value granularity
                    log.error("Exception snapshot with multiple entitlement values");
                }
                else if (value != null) {
                    String strValue = value.toString();
                    owner = getEntitlementOwner(appName, name, strValue);
                    addItem(src, item, owner, entities);
                }
            }
        }
    }

    /**
     * Distribute account items to owners.
     * The only possible owner is the application owner.
     * These are modeled with type=Account but the payload
     * is an EntitlementSnapshot like exceptions.  The snapshot is 
     * however empty apart from the account identity.
     */
    private void addAccountItem(CertificationEntity src, CertificationItem item,
                                Map<String,CertificationEntity> entities)
        throws GeneralException {
    
        EntitlementSnapshot snapshot = item.getExceptionEntitlements();
        String owner = _defaultCertifier;

        String appName = snapshot.getApplication();
        ApplicationCache.ApplicationInfo info = _applications.get(appName);
        // if null it means that app is missing and has already been logged
        if (info != null) {
            String appOwner = info.getOwner();
            if (appOwner != null)
                owner = appOwner;
        }
        addItem(src, item, owner, entities);
    }
    
    /**
     * Given an entitlement, return the owner.
     * If this maps to a ManagedAttribute the owner is taken from there,
     * otherwise it is the default certifier.
     *
     * This is the most expensive parts of owner determination because we have
     * to hit the MA table.  We have a cache of MA properties called the
     * Explanator that is currently only used for display names but could be
     * expanded to have the owner.  That's better than us keeping another huge cache.
     * 
     * To call ManagedAttributer we need:
     *    applicationId (NOT name)
     *    permission flag
     *    attributeName
     *    value
     *    objectType
     *
     * One of attributeName or objectType must be passed.
     *
     * To call Explanator we need:
     *
     * applicationId
     * permission flag
     * attributeName
     * value
     *
     * Explanator is probably broken for dual-group applications since
     * it was not modified to understand objectType.
     *
     * TODO: Blowing off objectType since it requires an AttributeInfo
     * and I'm not sure we need it.
     */
    private String getEntitlementOwner(String appName, String attName, String value)
        throws GeneralException {

        String owner = _defaultCertifier;

        ApplicationCache.ApplicationInfo app = _applications.get(appName);
        if (app != null && app.isManaged(attName)) {

            boolean useExplanator = true;

            if (useExplanator) {
                Explanation exp = Explanator.get(app.getId(), attName, value);
                if (exp != null) {
                    // don't overwrite _defaultCertifier
                    String ownerName = exp.getOwner();
                    if (ownerName != null)
                        owner = ownerName;
                }
            }
            else {
                // projection search would be faster but really if we're
                // optimizing this should be using Explanator
                ManagedAttribute att = ManagedAttributer.get(_context, app.getId(), false, attName, value);
                if (att != null) {
                    Identity ident = att.getOwner();
                    if (ident != null) {
                        owner = ident.getName();
                    }
                }
            }
        }
        
        return owner;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Core CertificationEntity Building
    //
    // Copied from a combination of places in BaseCertificationContext,
    // BaseIdentityCertificationContext and BaseCertificationBuilder
    //
    // The bulk of the work is in the calculation of a Certifiables list
    // from the Identity.  The exclusion rule is then applied to this.
    // This is where we'll have to inject the new filtering options
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Copied from BaseCertificationContext
     *
     * Create an CertificationEntity for an entity.  This runs the exclusion
     * rule (if configured) and as a side-effect will add entities to the
     * exclusion list on the Certification if saveExclusions is true.
     */
    private CertificationEntity createCertificationEntity(Certification cert,
                                                          AbstractCertifiableEntity thing)
        throws GeneralException {

        final String MeterName = "EntityBuilder: createCertificationEntity";
        CertificationEntity entity = null;

        Meter.enter(MeterName);
        try {
            List<Certifiable> certifiables = 
                _certifiableAnalyzer.getCertifiables(thing);
            
            // First, check if the entity is inactive and we're excluding
            // inactive identities.  We always exclude inactive entities from
            // continuous certs.  If so, we'll stop now.
            if (_definition.isExcludeInactive() && thing.isInactive()) {
                // made archive optional for targeted certs
                if (_definition.isIncludeArchivedEntities()) {
                    addArchivedEntity(thing, certifiables, cert, Reason.Inactive, null);
                }
                return null;
            }

            // if we have an exclusion rule, then run it
            // jsl - this is no longer in the targeted cert UI since we have more
            // flexibility with identity filtering, but I left it behind since the
            // maintenance of the archive list is different than just simple filtering
            // and someone may want that someday
            if ( _definition.getExclusionRuleName() != null ) {
                if ( certifiables != null && certifiables.size() > 0 ) {
                    // run an exclude rule that is allowed to filter the
                    // certifiables list by removing items or moving the
                    // items to an exclusion list that can be persisted
                    List<Certifiable> itemsToExclude = new ArrayList<Certifiable>();
                    String explanation =
                        runExcludeRule(thing, cert, certifiables, itemsToExclude);

                    // getSaveExclusions is the old name for this but it was used
                    // inconsistently.  Used to archive the excluded items but not
                    // for excluding inactive entities.  For targeted using the
                    // new name for both
                    if (_definition.isIncludeArchivedEntities() && (itemsToExclude != null) &&
                        (itemsToExclude.size() > 0)) {
                        addArchivedEntity(thing, itemsToExclude, cert, Reason.Excluded, explanation);
                    }
                }
            }

            // Only create the CertificationEntity if there are items to certify.
            if ((certifiables != null) && !certifiables.isEmpty()) {

                entity = createCertificationEntityInternal(cert, thing, true);

                // jsl - this deserves it's own meter
                List<CertificationItem> items = _itemBuilder.execute(thing, certifiables, true);
                // add() also sets a parent backref, setItems is only supposed
                // to be used by Hibernate, not sure why
                for (CertificationItem item : Util.iterate(items)) {
                    entity.add(item);

                    // jsl - as an experiment prune the EntitlementSnapshot which we don't need
                    // with Value granularity
                    // item.setExceptionEntitlements(null);
                }

                // Run the customization rule if one is configured.
                if (_entityCustomizationRule != null) {
                    _customizationRuleArgs.put("certifiableEntity", thing);
                    _customizationRuleArgs.put("entity", entity);
                    _context.runRule(_entityCustomizationRule, _customizationRuleArgs);
                }
            }
        }
        finally {
            Meter.exit(MeterName);
        }

        return entity;
    }

    /**
     * Copied from BaseCertificationBuilder
     *
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

        List<CertificationItem> items = _itemBuilder.execute(entity, certifiables, false);
        // add() also sets a parent backref, setItems is only supposed
        // to be used by Hibernate, not sure why
        for (CertificationItem item : Util.iterate(items)) {
            excluded.add(item);
        }
        
        ArchivedCertificationEntity archived =
            new ArchivedCertificationEntity(excluded, reason, explanation);
            
        if (!cert.mergeArchivedEntity(archived, _context)) {
            archived.setCertification(cert);
            _context.saveObject(archived);
            _context.commitTransaction();
        }
    }

    /**
     * Copied from BaseIdentityCertificationContext
     *
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
                                                                    AbstractCertifiableEntity thing,
                                                                    boolean snapshot)
        throws GeneralException {

        CertificationEntity entity = null;
            
        final String MeterName = "EntityBuilder: createInternal";
        Meter.enter(MeterName);
        try {
            if (!Identity.class.isAssignableFrom(thing.getClass()))
                throw new RuntimeException("Could not create an identity certification entity with class of type '"
                                           + entity.getClass().getName() + "'");
            
            Identity identity = (Identity) thing;
            entity = new CertificationEntity(identity);
            
            if (snapshot) {
                // create new snapshot if there were changes since the last one
                createSnapshot(identity, entity);
            }
        }
        finally {
            Meter.exit(MeterName);
        }
        
        return entity;
    }

    /**
     * Copied from BaseIdentityCertificationContext
     *
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
            
        final String MeterName = "EntityBuilder: createSnapshot";
        Meter.enter(MeterName);
        try {
            // generate a new snapshot
            IdentitySnapshot snap = _archiver.createSnapshot(id);
            
            // locate the previous one
            IdentitySnapshot prev = ObjectUtil.getRecentSnapshot(_context, id);
            
            if (prev != null) {
                // ignore the previous one if there were changes since then
                Differencer diff = new Differencer(_context);
                if (!diff.equal(prev, snap))
                    prev = null;
            }
            
            if (prev != null) {
                // remember just the id, we don't need to include it locally
                aid.setSnapshotId(prev.getId());
            }
            else {
                // Go ahead and persist since we're saving certifications
                // incrementally.  We used to just store the snapshot directly
                // on the certification and wait until the "save" phase to
                // persist the snapshots.
                _context.saveObject(snap);
                
                aid.setSnapshotId(snap.getId());
            }
        }
        finally {
            Meter.exit(MeterName);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Exclusion Rule
    //
    // Called by createCertificationEntity after building the list
    // of Certifiables
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Run the exclusion rule (if configured) for the given entity.
     */
    private String runExcludeRule(AbstractCertifiableEntity entity,
                                  Certification cert,
                                  List<Certifiable> items,
                                  List<Certifiable> itemsToExclude )
        throws GeneralException {

        String explanation = null;
            
        if (_exclusionRule != null) {
            _exclusionRuleArgs.put("entity", entity);
            _exclusionRuleArgs.put("items", items);
            _exclusionRuleArgs.put("itemsToExclude", itemsToExclude);

            // Left for backwards-compatibility
            // jsl - now would be a good time to move
            //if (entity instanceof Identity) {
            //_exclusionRuleArgs.put("identity", entity);
            //}

            explanation = (String) _context.runRule(_exclusionRule, _exclusionRuleArgs);
        }

        return explanation;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pre Delegation
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Derived from BaseCertificationContext
     *
     * Run the pre-delegation rule and save the delegation state in the
     * Certification.
     * 
     * Formerly this also did reassignment, that is no longer supported,
     * use the owner rule.
     *
     * Note that unlike the other rules, this one adds things to the
     * refresh list on the Certification so we must pass the cert the
     * entity is actually in, not the root cert.
     * 
     * Continuing to support the old return values though we could get
     * rid of the backward compatibility returns.
     */
    private void preDelegate(CertificationEntity entity)
        throws GeneralException {

        if (_preDelegationRule != null) {
            // must use the cert the entity is in
            Certification cert = entity.getCertification();
            _delegationRuleArgs.put("certification", cert);
            _delegationRuleArgs.put("entity", entity);
            Object results = _context.runRule(_preDelegationRule, _delegationRuleArgs);

            if (null != results) {
                if (results instanceof Map) {
                    Map map = (Map) results;
                    Identity recipient = (Identity) map.get("recipient");
                    String recipientName = (String) map.get("recipientName");
                    String description = (String) map.get("description");
                    String comments = (String) map.get("comments");
                    String certName = (String) map.get("certificationName");
                    boolean reassign = Util.getBoolean(map, "reassign");

                    if (reassign) {
                        // don't support this, make them use the new way
                        log.warn("Pre-delegation rule attempted to reassign: " + _preDelegationRule.getName());
                    }
                    else {
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
                                requestor = _context.getObjectByName(Identity.class, cert.getCertifiers().get(0));
                            }
                            else {
                                requestor = cert.getCreator(_context);
                            }

                            // This adds a CertificationDelegation to the entity
                            // and adds the entity to the entitiesToRefresh list on
                            // the owning Certification
                            entity.delegate(requestor, null, recipient.getName(), description, comments);
                        }
                    }
                }
            }
        }
    }

    /**
     * BaseCertificationBuilder
     *
     * Return the Identity with the given name if it exists, otherwise log
     * a warning.
     */
    private Identity getDelegate(String name, CertificationEntity entity)
        throws GeneralException {

        Identity delegate = _context.getObjectByName(Identity.class, name);
        if (null != delegate) {

        }
        else {
            addWarning(new Message(MessageKeys.COULD_NOT_LOAD_PREDELEGATE_ID, name,
                                   entity.getFullname()));
        }

        return delegate;
    }

    /**
     * BaseCertificationBuilder
     * A general list of warning messages that was only ever used
     * by getDelegate().
     *
     * @return Non-null list of warning messages.
     */
    public List<Message> getWarnings() {
        if (_warnings == null)
            _warnings = new ArrayList<Message>();
        return _warnings;
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
     * BaseCertificationBuilder
     *
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

    //////////////////////////////////////////////////////////////////////
    //
    // Entity Status Refresh
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Adapted from Certificationer and simplified because we're not 
     * doing partitial refresh and we don't have to worry about
     * completion rules at this point.
     *
     * I believe the only time that completion processing would be 
     * necessary is if autoApproveItem() marked some things approved and
     * the only way that would happen is if they were malformed.  The code
     * even says "Hack - if an item is malformed we want to auto approve it."
     * It is far simpler to avoid all that by not creating any malformed
     * items.  This may be more of an issue for post-creation refresh but
     * not during creation.
     */
    private void refreshEntityStatus(Certification cert, CertificationEntity entity)
        throws GeneralException {

        final String MeterName = "EntityBuilder: refresh status";
        Meter.enter(MeterName);
        try {
        
            for (CertificationItem item : Util.iterate(entity.getItems())) {
                refreshItemStatus(item);
            }
        
            // djs:
            // Since we've just loaded all of the current items, this is likely
            // the most oportune time to go over the items again and adorn 
            // the entitlements.  This avoids having to reload, the snapshot,
            // permission and cert item again later.
            // jsl:
            // We'll need to add Type.Focused to the list of "applicable"
            // cert types for this to do anything
            promoteEntityEntitlements(cert, entity);

            // This is what Certificationer.refreshSummaryStatus would
            // do if fullRefresh was enabled, which is implicit here
            // the null argument is "completeOverride"
            entity.refreshSummaryStatus(null);
        
            // Run the completion rule to allow the completion status to be tweaked.
            // runCompletionRule(entity, cert, _entityCompletionRule);
        }
        finally {
            Meter.exit(MeterName);
        }
    }
    
    /**
     * Simplified version of method from Certificationer.
     * We won't be doing autoApproveItem() so we don't have to worry
     * about completion rules.
     */
    private void refreshItemStatus(CertificationItem item)
        throws GeneralException {

        // Refresh the summary status and completion state of this item.
        item.refreshSummaryStatus();

        // Run the completion rule to allow the completion status to be tweaked.
        // runCompletionRule(item, cert, _itemCompletionRule);

        // this is probably redundant - jsl
        item.setNeedsRefresh(false);
    }
    
    /**
     * Copied from Certificationer
     */
    private void promoteEntityEntitlements(Certification cert, CertificationEntity entity) 
        throws GeneralException {
        
        final String MeterName = "EntityBuilder: promote entitlements";
        Meter.enter(MeterName);
        try {
            getEntitlizer(cert).setPending(entity);
        }
        finally {
            Meter.exit(MeterName);
        }
    }
    
    /**
     * Create one of these per cert.
     * 
     * @param cert
     * @return
     * @throws GeneralException
     */
    CertificationEntitlizer getEntitlizer(Certification cert) throws GeneralException {
        if ( _entitlizer == null ) {
            _entitlizer = new CertificationEntitlizer(_context);            
        }
        _entitlizer.prepare(cert);            
        return _entitlizer;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Differencing
    //
    // This is done by Certificationer after it builds an entity.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Copied from Certificationer
     * Changed call to CertificationContext.isIncludePolicyViolations
     * to use CertificationDefinition instead.
     *
     * Calculate the changes made to this entity since the last
     * time an certification of this type was performed.
     *
     * This actually does two things, calculates the differences and
     * copies over the certification history.  The history includes
     * the last certification action, the names of the delegates, etc.
     *
     * For some objects we don't store any history or differences. Currently
     * that only inlcudes AccountGroups. Check the isDifferencable method
     * ont the AbstractCertifiableEntity.
     *
     * @see sailpoint.object.AbstractCertifiableEntity#isDifferencable
     *
     * @param certCtx CertificationContext
     * @param cert The current Certification
     * @param certEntity This CertificationEntity
     * @param entity The entity being certified.
     * @throws GeneralException
     */
    private void renderDifferences(Certification cert,
                                   CertificationEntity certEntity,
                                   AbstractCertifiableEntity entity)
        throws GeneralException {

        final String MeterName = "EntityBuilder: renderDifferences";
        Meter.enter(MeterName);
        try {

            // locate a reference to the previous certification of this type
            IdentitySnapshot snapshot = null;
            CertificationLink link = entity.getLatestCertification(cert.getType());

            //bug 30233. Special case were link does not have id.
            //CertificationLink constructor is assuming that the id could be null.
            if (link != null && link.getId() != null) {
                // Try to get the snapshot from the link.
                snapshot = link.getIdentitySnapshot(_context);

                // If the link does not have a snapshot ID, it was probably created
                // before we were storing these on the links.  Dig into the previous
                // certification or (shudder) archive.
                if ((null == snapshot) && (null == link.getIdentitySnapshotId())) {
                    snapshot = getIdentitySnapshotLegacy(link, entity);
                }

                // If we didn't save a snapshot, could try to locate one based
                // on the date of the certification completion, but that's
                // inaccurate, not sure we should do this
                if (snapshot == null) {
                    //snapshot = locateSnapshot(link, prevCert, prevId, id);
                    if (log.isWarnEnabled())
                        log.warn("Unable to locate identity snapshot from previous certification: " + 
                                 entity.getName());
                }
                else {
                    // must have generated a current one by now
                    IdentitySnapshot current = certEntity.getIdentitySnapshot(_context);
                    if (current != null) {
                        //do not truncate strings in this Difference so that we can compare values
                        //from it later.  We will truncate in the CertificationBean before using it in
                        //this UI. 
                        Differencer differ = new Differencer(_context);
                        differ.setMaxStringLength(0);
                        differ.setIncludeNativeIdentity(true);
                        IdentityDifference diff =
                            differ.diff(snapshot, current, cert.getApplication(_context),
                                        _definition.isIncludePolicyViolations());
                        certEntity.refreshHasDifferences(diff, _context);
                        certEntity.setDifferences(diff);
                    }
                }
            }
        
            // This is a new user if we don't have a previous snapshot.
            certEntity.setNewUser((snapshot == null));
        }
        finally {
            Meter.exit(MeterName);
        }
    }
    
    /**
     * Certificationer
     * 
     * We used to not store the IdentitySnapshot ID on the CertificationLink,
     * but would instead search for the entity within the previous Certification
     * (or CertificationArchive) to get the ID.  CertificationLinks that were
     * previously created will not have the ID yet, so this method searches in
     * the old certification (or arhive) to find the snapshot.
     */
    private IdentitySnapshot getIdentitySnapshotLegacy(CertificationLink link,
                                                       AbstractCertifiableEntity entity)
        throws GeneralException {

        IdentitySnapshot snapshot = null;
        boolean isArchive = false;

        // Try to load the previous certification.  This will be null if the
        // certification has been archived.
        Certification prevCert =
            _context.getObjectById(Certification.class, link.getId());

        // Did not find the previous Certification.  Look for an archive.
        boolean noArchiveSearching =
            _context.getConfiguration().getBoolean(Configuration.CERTIFICATION_DIFFERENCING_NO_ARCHIVE_SEARCHING, false);
        if ((null == prevCert) && !noArchiveSearching) {
            CertificationArchive arch =
                ObjectUtil.getCertificationArchive(_context, link.getId());
            // No archive either - remove the CertificationLink.
            if (null == arch) {
                entity.remove(link);
                if (log.isWarnEnabled())
                    log.warn("Could not find certification or archive: " + link.getId());
            }
            else {
                prevCert = arch.decompress(_context, link.getId());
                isArchive = true;
            }
        }

        if (null != prevCert) {
            CertificationEntity prevEntity = null;

            // Load the CertificationEntity from the previous certification.
            // If this isn't an archive, we can run a query to find the
            // entity very quickly.
            if (!isArchive) {
                Filter f = Filter.and(Filter.eq("certification", prevCert),
                                      Filter.eq("identity", entity.getName()));
                prevEntity = _context.getUniqueObject(CertificationEntity.class, f);
            }
            else {
                // Have to dig this out of the archive the slow way.
                prevEntity = prevCert.getEntity(entity);
            }

            if (prevEntity == null) {
                // a bad link, or someone tampered with the Certification
                // or the identity was renamed
                if (log.isWarnEnabled())
                    log.warn("Unable to locate entity '" + entity.getName() + 
                             "' in certification '" + link.getId());
            }
            else {
                // Lazy upgrade - add the snapshot ID to the link.
                link.setIdentitySnapshotId(prevEntity.getSnapshotId());

                snapshot = prevEntity.getIdentitySnapshot(_context);
            }
        }

        return snapshot;
    }

}
