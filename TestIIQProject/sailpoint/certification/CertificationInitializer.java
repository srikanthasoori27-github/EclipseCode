/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Initialize a Certification object, prior to adding entities.
 * 
 * Author: Jeff
 *
 * This is adapted from various classes including: 
 *
 *    CertificationExecutor
 *    CertificationBuilderFafctory
 *    Certificationer.generateCertififcation
 *    BaseCertificationBuilder.initializeCertification
 *
 * This is initially used by IdentityCertificdationStarter to build
 * the root certification, then later each properly owned cert once the
 * collections of entities and certifiers is known.
 * 
 * NAMES
 *
 * In the UI the certification name field is actually a template.  It appears
 * this template is stored as certificationNameTemplate in the definition
 * but that is also rendered and used as the name of the definition.
 * The default template is "Manager Certification [${fullDate}]".  
 *
 * The certificationNameTemplate is used to generate the
 * CertificationGroup name.  Note that since time can pass after the 
 * saving of the CertificationDefinition, groups will usually have different
 * timestamps.
 *
 * The definition also has keys nameTemplate and shortNameTemplate which
 * are used to name the Certification objects.  These are not there by 
 * default so it falls back to a Message that combines the cert type with
 * the manager name. The default in my testing was 
 * "Manager Access Review for Mary Johnson", note that these are not 
 * timestamped so it is easy to create several.
 *
 * TODO: Change console to display the Certification id since they don't
 * have unique names.
 *
 * Certifier, and certOwner, and owners, oh my!
 * 
 * A manager certification has both an owner and a certifier.
 * These are in the definition map with keys certOwner and certifier.
 * In the UI the labels are "Certification Owner" and "Recipient".
 * The definition also has an owner Identity reference which I think
 * is always the same as certOwner.
 * 
 * The definition also has keys "certifiers" and "owners".
 * "owners" appears to be related to application owner certs, and contains
 * the names of the app owners. This appears to override certifier and
 * certifiers if it is set.
 *
 * I'm not sure what uses "certifiers".
 *
 * A cert will have only one owner but may have multiple certifiers.
 * The certOwner defaults to "spadmin" in the UI.  For a manager
 * cert the certifier will the name of the selected manager.
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationNamer;

import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Tag;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class CertificationInitializer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(CertificationInitializer.class);

    SailPointContext _context;
    CertificationDefinition _definition;
    CertificationGroup _group;
    Identity _groupOwner;

    /**
     * Transient list of resolved certifier identities.
     * Normally this is calculated from the CertificationDefinition but
     * it can also be passed in for complex ownership rules.  This is
     * only valid for one call to createCertification
     */
    List<Identity> _certifiers;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Initialize the initializer.
     * Note that the Hibernate cache can be cleared during the lifespan
     * of this object so we must load any referenced objects that will be 
     * needed later.
     */
    public CertificationInitializer(SailPointContext con,
                                    CertificationDefinition def,
                                    CertificationGroup group)
        throws GeneralException {
        
        _context = con;
        _definition = def;
        _group = group;

        // the only ref the definition has is the tag list, which
        // is eventually used by CertificationDefinition.storeContext
        for (Tag tag : Util.iterate(def.getTags())) {
            tag.getName();
        }

        // The only thing we need from the group is the owner, load it
        // now so we don't have to depend on an active Hibernate session
        _groupOwner = group.getOwner();

        // only need the name for right now
        _groupOwner.getName();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Building
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build out an empty Certification.
     * This is approxomately equal to what is done by CertificationExecutor, 
     * CertificationBuilderFactory, and Certificationer.generateCertification
     * and BaseCertificationBuilder.initializeCertification do
     * but without adding entities.
     *
     * An optional list of certifier names may be passed that overrides the
     * CertificationDefinition.
     */
    public Certification createCertification(List<String> certifiers)
        throws GeneralException {

        Certification cert = new Certification();

        // this is needed several levels deep for naming so save it
        _certifiers = getCertifiers(certifiers);

        /* Start BaseCertificationBuilder.initializeCertification */

        // TODO: For normal certs, creator is taken from the "launcher"
        // attribute of the TaskSchedule.  Here I suppose we could
        // pass this down, until I determine how critical this is just
        // use owner.
        cert.setCreator(_groupOwner);

        cert.setType(_definition.getType());

        // The certifiers (aka owners in most of the code)
        cert.setCertifierIdentities(_certifiers);

        cert.setAssignedScope(_definition.getAssignedScope());

        // taskScheduleId is given to BaseCertificationBuilder by
        // CertifiationBuilderFactory if it was given a TaskSchedule by
        // CertificationExecutor, this would be normal for manager certs
        // We could try to accomplish the same thing but I'd like to know
        // what this is used for before we bother
        // cert.setTaskScheduleId(taskScheduleId);

        cert.setAllowProvisioningRequirements(_definition.isAllowProvisioningRequirements());
        cert.setRequireApprovalComments(_definition.isRequireApprovalComments());
        cert.setDisplayEntitlementDescription(_definition.isDisplayEntitlementDescriptions());

        // The cert model supports multiple groups but we have only
        // ever created one
        List<CertificationGroup> groups = new ArrayList<CertificationGroup>();
        groups.add(_group);
        cert.addCertificationGroups(groups);

        // phase config has been rewritten with a different code structure
        cert.setPhaseConfig(getPhaseConfig(cert.getCertificationGroups()));
                            
        // continuous certs always process revokes immediately
        // jsl - we should never have continuous, but could ask for immediate
        // revocations
        cert.setProcessRevokesImmediately(_definition.isProcessRevokesImmediately());

        // Here is where initializeCertification would set continuous config
        // we can ignore this
        
        // NOTE: If the certification ends up being forwarded,
        // the manager name in the shortName may not match the
        // owner.  Could try to track this in forwardWorkItem
        // but the CertificationContext is gone at that point.
        // In some ways, leaving the default title is correct
        // because it still is a certification for the original
        // manager, it's just been handed off to someone else. - jsl
        cert.setName(generateName());
        cert.setShortName(generateShortName());

        /* End BaseCertificationBuilder.initializeCertification */

        // Certificationer now calls CertificationContext.storeContext
        // This is what BaseCertificationContext.storeContext does,
        // which ends up copying most of the definition into the cert
        _definition.storeContext(_context, cert);

        // Certificationer will now call save() to persist the object
        // but first it calls validate() which checks for a non-empty
        // certifiers list, and a non-null name

        _context.saveObject(cert);
        _context.commitTransaction();
        
        return cert;
    }
    
    /**
     * Calculate the list of certifiers.  This is called "owners" in most
     * of the code which is confusing because owner is a different concept.
     * 
     * In old code this is calculated by CertificationBuilderFactory.  
     * It supports a certifiers list being passed in to the constructor, 
     * which I think only happens for certifications created from an
     * IdentityTrigger.  For normal certs they may be set in the 
     * CertificationDefinition.  If not in the definition then for
     * manager certs it defaults to the manager.
     *
     * The default is to get certifiers from the CertificationDefinition.
     * If a name list was passed down it overrides the hdefinition.
     *
     * If the override list is null this is the root cert.
     * If using CertifierSelectionType.Manual the identity name is
     * in the definition under ARG_CERTIFIER.  For other cert types
     * the root cert will be assigned to ARG_BACKUP_CERTIFIER.
     * It looks like ARG_BACKUP_CERTIFIER is not required for Manual,
     * check on that, maybe we still need both to prevent self-certification?
     */
    private List<Identity> getCertifiers(List<String> override)
        throws GeneralException {
        
        List<Identity> certifiers = new ArrayList<Identity>();

        if (!Util.isEmpty(override)) {
            for (String name : override) {
                Identity ident = resolveCertifier(name);
                certifiers.add(ident);
            }
        }
        else {
            CertifierSelectionType type = _definition.getCertifierSelectionType();
            String name = null;;
            if (type == CertifierSelectionType.Manual) {
                name = _definition.getCertifierName();
            }
            else {
                name = _definition.getBackupCertifierName();
            }
                    
            Identity ident = resolveCertifier(name);
            certifiers.add(ident);
        }
        
        return certifiers;
    }

    /**
     * Resolve a name to an Identity throwing exceptions.
     */
    private Identity resolveCertifier(String name)
        throws GeneralException {

        if (name == null)
            throw new GeneralException("Missing certifier name");

        Identity ident = _context.getObjectByName(Identity.class, name);

        if (ident == null)
            throw new GeneralException("Invalid certifier name: " + name);

        return ident;
    }
    
    /**
     * Build the phase config.
     * This simulates what is done by:
     * BaseCertificationContext.initializeCertification
     * BaseCertificationContext.shouldBypassStagingPeriod
     *
     * An extranal phase config can be set in BaseCertificationContext
     * to override the default config.  I'm not sure under what conditions
     * that is done.  We don't support that.
     */
    private List<CertificationPhaseConfig> getPhaseConfig(List<CertificationGroup> groups)
        throws GeneralException {

        // inline implementation of shouldBypassStagingPeriod()
        // the CertificationGroup we create will always be Pending at this
        // moment, so bypassStaging will always be false, could just remove
        // this logic

        boolean bypassStaging = false;
        for (CertificationGroup certGroup : Util.safeIterable(groups)) {
            if (!CertificationGroup.Status.Pending.equals(certGroup.getStatus()) && !CertificationGroup.Status.Staged.equals(certGroup.getStatus())) {
                bypassStaging = true;
                break;
            }
        }

        return _definition.createPhaseConfig(_context, bypassStaging);
    }

    /**
     * Copied from BaseCertificationContext
     * 
     * Generate the name for this certification.  This prefers the name
     * template if configured.  If not configured, this lets the subclass
     * generate the name with the generateDefaultName() method.
     */
    private String generateName() throws GeneralException {

        String nameTemplate = _definition.getNameTemplate();
        String name = new String();

        // IIQMAG-2969
        // If this is a targeted cert and there's only one identity being certified then
        // add the identity information to the namer so the identity full name can
        // be resolved and in the certification name.
        if (_definition.getType().equals(Certification.Type.Focused) && getIdentityCount() == 1) {
            if (null != nameTemplate) {
                CertificationNamer namer = getCertificationNamerWithIdentity();
                name = namer.render(nameTemplate);
            }
        }
        else {
            // No need to add the identity to the namer, just render the name
            name = renderName(nameTemplate);
        }

        if (null == name) {
            name = generateDefaultName();
        }

        // Cap the length of this.
        return Util.truncate(name, Certification.NAME_MAX_LENGTH);
    }

    /**
     * Adapted from ManagerCertificationBuilder.
     * Might want our own message keys?
     */
    private String generateDefaultName() throws GeneralException {

        String certifier = getNamingCertifier();
        
        Message name = new Message(MessageKeys.CERT_NAME_GENERIC,
                                   new Message(_definition.getType().getMessageKey()), certifier);
        
        return name.getLocalizedMessage();
    }

    /**
     * Derive the certifier name to be used when generating the Certification name.
     * For manager certs there was only one name, but here we can have several. 
     * TODO: What do the other certs do?
     */
    private String getNamingCertifier() throws GeneralException {

        // just do the first one for now
        return _certifiers.get(0).getName();
    }
    
    /**
     * Copied from BaseCertificationContext
     *
     * Generate the short name for this certification.  This prefers the
     * short name template if configured.  If not configured, this lets the
     * subclass generate the short name with the generateDefaultShortName()
     * method.
     */
    private String generateShortName() throws GeneralException {

        String nameTemplate = _definition.getNameTemplate();
        String shortName = new String();

        // IIQMAG-2969
        // If this is a targeted cert and there's only one identity being certified then
        // add the identity information to the namer so the identity full name can
        // be resolved and in the certification name.
        if (_definition.getType().equals(Certification.Type.Focused) && getIdentityCount() == 1) {
            if (null != nameTemplate) {
                CertificationNamer namer = getCertificationNamerWithIdentity();
                shortName = namer.render(nameTemplate);
            }
        }
        else {
            // No need to add the identity to the namer, just render the name
            shortName = renderName(nameTemplate);
        }

        if (null == shortName) {
            shortName = generateDefaultShortName();
        }

        // Cap the length of this.
        return Util.truncate(shortName, Certification.SHORT_NAME_MAX_LENGTH);
    }
    
    /**
     * Adapted from ManagerCertificationBuilder.
     * TODO: Since this includes _IDENTITY in the key we probably do want our own
     * key here to avoid conflict.
     */
    String generateDefaultShortName() throws GeneralException {
        String certifier = getNamingCertifier();
        Message name = new Message(MessageKeys.CERT_SHORTNAME_IDENTITY, certifier);
        return name.getLocalizedMessage();
    }
    
    /**
     * Copied from BaseCertificationContext
     * 
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
     * Adapted from BaseCertificationContext and CertificationNamer.
     * Note that this is different from the namer used for the CertificationGroups
     * which passes in the owners.  Confused, this may be wrong?
     * CertificationNamer has a constructor for this but it is passed
     * a CertificationContext which we don't have.
     * 
     * Q1: Under what conditions would we have more than one cert group?
     * Q2: If the group and the cert will always have the same name, why
     * not just get it from there?
     */
    private CertificationNamer getCertificationNamer()
        throws GeneralException {

        CertificationNamer namer = new CertificationNamer(_context);

        // this is what the constructor that takes a context adds
        // params.put(NAME_TEMPLATE_CONTEXT, context);
        namer.addParameter(CertificationNamer.NAME_TEMPLATE_TYPE, _definition.getType());
        
        // had to make this public
        namer.addOwners(_certifiers);

        // allow implementation-specific params to be added by subclasses
        // addNameParameters(namer);

        // the BaseIdentityCertificationContext.addNameParameters
        // overload would do this
        // I believe this.identityIds would be set only if a specific list
        // of identities was passed in the CertificationDefinition.  We may
        // want that but why the hell does that impact the namer?
        // namer.addParameter(NAME_TEMPLATE_IDENTITY_IDS, this.identityIds);

        // ManagerCertifcationContext would add this
        // namer.addIdentity(manager, CertificationNamer.NAME_TEMPLATE_MANAGER_PREFIX);
        // namer.addParameter(CertificationNamer.NAME_TEMPLATE_GLOBAL, global);


        // jsl - I'm going to stop here since this all needs to be redesigned
        // for flexi certs, will have to make it clear what parameters
        // are going to be available for the message template
        
        return namer;
    }

    private CertificationNamer getCertificationNamerWithIdentity()
            throws GeneralException {

        CertificationNamer namer = getCertificationNamer();

        // Add the identity info to the namer.
        Identity identity = getIdentity();
        if (null != identity) {
            namer.addIdentity(identity, CertificationNamer.NAME_TEMPLATE_TARGET_ENTITY_PREFIX);
        }

        return namer;
    }

    /*
     * Get the identity to certify. We're only expecting one here.
     */
    private Identity getIdentity() throws GeneralException {
        Filter filter = _definition.getEntityFilter();

        List<Identity> identities = null;
        if (null != filter) {
            List<String> props = new ArrayList<String>();
            props.add("name");
            QueryOptions ops = null;
            if (filter != null) {
                ops = new QueryOptions();
                ops.add(filter);
            }

            identities = _context.getObjects(Identity.class, ops);
        }

        return identities.get(0);
    }

    /*
     * Use the entity filter to get a count of the identities to certify.
     */
    private int getIdentityCount() throws GeneralException {

        Filter filter = _definition.getEntityFilter();
        int count = 0;

        if (null != filter) {
            QueryOptions ops = null;
            if (filter != null) {
                ops = new QueryOptions();
                ops.add(filter);
            }

            count = _context.countObjects(Identity.class, ops);
        }

        return count;
    }
}
