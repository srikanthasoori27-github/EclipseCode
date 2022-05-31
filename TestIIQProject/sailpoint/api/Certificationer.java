/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object providing certification services.
 *
 * Author: Jeff, Kelly
 *
 * Meter range: 40-59
 *
 * This defines the API for certification, built on top of the
 * SailPointContext.  Former Wavesetters can think of this
 * as a collection of "workflow services" methods: units
 * of business logic that are called during the execution
 * of the certification process.
 *
 * The process is currently hard coded in the certification
 * UI, though to the extent possible business logic should
 * be placed here so that we may introduce a real workflow
 * engine at a later time.
 *
 * There are several phases of the certification "process":
 *
 *   Generate
 *     The generation of a Certification object for a certifier.
 *     The certification objects are persisted.  These will be
 *     persisted incrementally while generated to improve memory
 *     efficiency for large certification hierarchies.
 *
 *   Start
 *     Generate the necessary work items for the top-level certifier,
 *     and send notifications.  This could be merged with the
 *     Generate phase, but we'll keep it separate for now in case
 *     we want to pre-create certifications.
 *
 *   Update
 *     Normal modification to the Certification object hierarchy,
 *     mark items complete by setting a status code.  Any
 *     number of users may edit different parts of the Certification.
 *     A refresh happens in two contexts: during direct editing
 *     of the Certification object (or one of its children) in the
 *     UI, and as a side effect of editing a WorkItem associated
 *     with the Certification.
 *
 *   Refresh
 *     Performs these operations:
 *     1) Walk over the Certification object to see if all of the
 *     items have been completed.  If so, assign completion dates
 *     to the parent nodes, and ultimately mark the top-level
 *     Certification as complete.
 *     2) Look for delegation triggers and generate the
 *     appropriate work items.
 *     3) Look for any revoked delegations and remove the corresponding
 *     work items.
 *
 *   Assimilate
 *     After a work item has been completed, copy the completion
 *     status back into the Certification model for review and
 *     archival.
 *
 *   Sign
 *     The certifier is "signing off" on all decisions made in the
 *     certification.  This puts the certification into a read-only
 *     mode that disallows changing the decisions.  To be called when
 *     the certifier believes the certification is finished.  All
 *     certification items must be approved, mitigated, or remediated.
 *     All delegations must have been completed.
 *
 *   Finish
 *     Called in the background after the certification has been signed.
 *     This verifies that the certification is complete, and performs all
 *     finish operations, such as process remediation request, store
 *     certification information on the identity, etct...
 *
 *   Archive
 *     Convert the Certification object to an CertificationArchive.
 *
 *   Delete
 *     Remove the Certification object and any associated residue
 *     (e.g. WorkItems)
 *
 * The Certificationer class provides methods for all of these phases
 * except for Update, which is just normaly modification of
 * persistent objects.
 *
 * During rendering, the Certification created is one of several types.
 *
 *    Manager/Employee
 *      A designated user will be asked to certify to their subordinates.
 *      Subordinates are determined by walking the manager attribute
 *      hierarchy.
 *
 *    Application Owner
 *      The designated owner of an application (aka resource) will
 *      certify to all identities that have accounts on that application.
 *
 *    Identity
 *      A designated user will be asked to certify to a single identity.
 *
 *
 * Part of rendering is to generate a summary of the attribute and
 * entitlment changes made to each identity since the last time the
 * identity was certified.  In order to have a stable basis for comparison,
 * we must generate an IdentitySnapshot that has the current state
 * of the Identity whenever we create an AttesationIdentity.  Besides
 * being used for change detection, this can also be used when an
 * certification archive is decompressed.  Note though that the
 * CertificationArchive and IdentitySnapshots have different life cycles.
 * There is no guarantee that the IdentitySnapshot created for a given
 * Certification will still exist after it has been archived.  For change
 * detection we only need the IdentitySnapshot from the previous
 * certification, so the IdentitySnapshot will typically be expired
 * and deleted after a period of time.
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Workflower.ForwardType;
import sailpoint.api.certification.AttributeAssignmentHandler;
import sailpoint.api.certification.CertificationAuditor;
import sailpoint.api.certification.CertificationStatCounter;
import sailpoint.api.certification.MitigationManager;
import sailpoint.api.certification.PolicyViolationAuditor;
import sailpoint.api.certification.RemediationManager;
import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.certification.CertificationDeleter;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.CertifiableDescriptor;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationArchive;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationCommand;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationLink;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.LimitReassignmentException;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.Resolver;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.SelfCertificationException;
import sailpoint.object.SignOffHistory;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemMonitor;
import sailpoint.provisioning.PlanUtil;
import sailpoint.server.Auditor;
import sailpoint.server.WorkItemHandler;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ModifyImmutableException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

public class Certificationer implements MessageRepository, WorkItemHandler
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(Certificationer.class);

    /**
     * The context providing persistence services.
     */
    SailPointContext _context;

    /**
     * Name of the User that requested this certification.
     * Stored in a field so we can access it as we walk down
     * the manager hierarchy.
     */
    Identity _requestor;

    /**
     * When true, we require all delegations to be reviewed before
     * allowing the associated item to be complete.
     */
    Boolean _delegationReview;

    /**
     * An error handler to use.  Warnings and errors are added to this handler
     * and can be retrieved through the delegated getErrors() and getWarnings()
     * methods.
     */
    MessageRepository _errorHandler;

    /**
     * An EmailSuppressor that is used to prevent duplicate emails from being
     * sent (if the option is enabled).
     */
    EmailSuppressor _emailSuppressor;

    private EmailTemplateRegistry emailTemplateRegistry;

    /**
     * A CertificationAuditor to use for auditing.
     */
    private CertificationAuditor auditor;

    // Notary object used to esign certs
    private Notary notary;

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
     * A rule that is run when refreshing a CertificationEntity to provide a
     * hook for customization.
     */
    Rule _entityRefreshRule;

    /**
     * for passing state to rules
     */
    private Map<String, Object> _state = new HashMap<String, Object>();

    private CertificationStatCounter _statCounter;

    private int _entityDecacheInterval = 100;
    
    /**
     * Store off the previous immutable flag so we can return
     * the context to its previousState when we are finished
     * with some of the operations.
     */
    private Boolean previousImmutableFlagValue = false;
    
    private Monitor monitor;

    private CertificationService _service;

    private MitigationManager _mitigationManager;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is required if we want to be a WorkItemHandler,
     * it should not be used anywhere else, though I suppose
     * if you really wanted to you could call init() immediately
     * afterward.
     */
    public Certificationer() {
    }

    /**
     * This can be built by any level of the system.
     */
    public Certificationer(SailPointContext con) throws GeneralException {
        init(con);
    }

    /**
     * Internal initialization, factored out of the constructor
     * so it can be called from the WorkItemHandler methods - jsl
     */
    @SuppressWarnings("unchecked")
    public void init(SailPointContext con) throws GeneralException {

        _context = con;

        _errorHandler = new BasicMessageRepository();

        _statCounter = new CertificationStatCounter(con);

        emailTemplateRegistry = new EmailTemplateRegistry(con);

        auditor = new CertificationAuditor(con);
        
        _service = new CertificationService(con);

        _mitigationManager = new MitigationManager(_context);
        
        // initialize options from the system configuration
        Configuration config = _context.getConfiguration();
        if (config != null) {

            String ruleName = config.getString(Configuration.CERTIFICATION_ITEM_COMPLETION_RULE);
            if (null != ruleName) {
                _itemCompletionRule = _context.getObjectByName(Rule.class, ruleName);
                if (null != _itemCompletionRule) {
                    _itemCompletionRule.load();
                }
            }

            if (config.containsAttribute(Configuration.CERTIFICATION_ENTITY_DECACHE_INTERVAL)){
                _entityDecacheInterval = config.getInt(Configuration.CERTIFICATION_ENTITY_DECACHE_INTERVAL);
            }
        }
    }

    public void setDelegationReview(boolean b) {
        _delegationReview = b;
    }
    
    public void setMonitor(Monitor val) {
        monitor = val;
    }

    /**
     * Return whether delegation review is enabled for the given certification.
     * This first looks to see if delegation review was explicitly enabled for
     * this Certificationer.  If not, we'll check the configuration on the
     * certification, and as a last resort fall back to the system config.
     */
    private boolean isDelegationReview(Certification cert)
        throws GeneralException {

        if (null == _delegationReview) {
            _delegationReview =
                Util.otob(cert.getAttribute(Configuration.CERTIFICATION_DELEGATION_REVIEW,
                                            _context.getConfiguration().getAttributes()));
        }

        return (null != _delegationReview) ? _delegationReview : false;
    }

    /**
     * Return the CertificationEntity completion rule if one is configured on
     * the given certification or in the system configuration.
     */
    private Rule getEntityCompletionRule(Certification cert)
        throws GeneralException {

        // I think we're alright to cache this even though it can be configured
        // per cert since the typical refresh pattern is to create a
        // Certificationer, refresh, then throw it away.
        if (null == _entityCompletionRule) {
            _entityCompletionRule =
                getRule(Configuration.CERTIFICATION_ENTITY_COMPLETION_RULE, cert);
        }

        return _entityCompletionRule;
    }

    /**
     * Return either the item or entity completion rule depending on the type of
     * item.
     */
    private Rule getCompletionRule(AbstractCertificationItem item)
        throws GeneralException {

        Rule rule = null;

        if (item instanceof CertificationItem) {
            rule = _itemCompletionRule;
        }
        else if (item instanceof CertificationEntity) {
            rule = getEntityCompletionRule(item.getCertification());
        }

        return rule;
    }

    /**
     * Return the CertificationEntity refresh rule if one is configured on the
     * given certification or in the system configuration.
     */
    private Rule getEntityRefreshRule(Certification cert)
        throws GeneralException {

        // I think we're alright to cache this even though it can be configured
        // per cert since the typical refresh pattern is to create a
        // Certificationer, refresh, then throw it away.
        if (null == _entityRefreshRule) {
            _entityRefreshRule =
                getRule(Configuration.CERTIFICATION_ENTITY_REFRESH_RULE, cert);
        }

        return _entityRefreshRule;
    }

    /**
     * Return a rule configured either in the given certification or in the
     * system configuration with the given configuration key.
     */
    private Rule getRule(String configAttrName, Certification cert)
        throws GeneralException {

        Rule rule = null;

        String ruleName =
            (String) cert.getAttribute(configAttrName, _context.getConfiguration().getAttributes());
        if (null != ruleName) {
            rule = _context.getObjectByName(Rule.class, ruleName);
            if (null != rule) {
                rule.load();
            }
        }

        return rule;
    }

    /**
     * Return the number of emails that were not sent because they would have
     * been duplicates.
     */
    public int getEmailsSuppressed() throws GeneralException {
        return getEmailSuppressor().getEmailsSuppressed();
    }

    /**
     * Set the email suppressor to use.
     */
    public void setEmailSuppressor(EmailSuppressor suppressor) {
        _emailSuppressor = suppressor;
    }

    /**
     * Return the EmailSuppressor to use to prevent sending multiple emails of
     * the same type to the same recipient.
     */
    private EmailSuppressor getEmailSuppressor() throws GeneralException {
        if (null == _emailSuppressor) {
            _emailSuppressor = new EmailSuppressor(_context);
        }
        return _emailSuppressor;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Error/Warning Capture
    //
    //////////////////////////////////////////////////////////////////////

    public void setErrorHandler(MessageRepository errorHandler) {
        _errorHandler = errorHandler;
    }

    public List<Message> getErrors() {
        return getMessagesByType(Message.Type.Error);
    }

    public List<Message> getWarnings() {
        return getMessagesByType(Message.Type.Warn);
    }

    public void addWarning(String msgKey, Object... args) {
        addMessage(new Message(Message.Type.Warn, msgKey, args));
    }

    public void addError(String msgKey, Object... args) {
        if (_errorHandler == null)
            _errorHandler = new BasicMessageRepository();

        _errorHandler.addMessage(new Message(Message.Type.Error, msgKey, args));
    }

    public List<Message> getMessagesByType(Message.Type type) {
        if (_errorHandler != null)
           return _errorHandler.getMessagesByType(type);
        else
            return null;
    }

    public void addMessage(Message message) {
        initErrorHandler();
        _errorHandler.addMessage(message);
    }

    public List<Message> getMessages() {
        return _errorHandler != null ? _errorHandler.getMessages() : null;
    }

    public void clear() {
        if (_errorHandler != null)
            _errorHandler.clear();
    }

    private void initErrorHandler(){
        if (_errorHandler == null)
            _errorHandler = new BasicMessageRepository();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common Rendering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add another entity an Certification.  If the application is non-null
     * the entity is being added for an app owner certification.
     *
     * @param cert    The certification to which the identity will be added.
     * @param entity  The entity to add to the certification.
     */
    private Certification addEntity(CertificationContext certCtx, Certification cert,
                                    AbstractCertifiableEntity entity)
        throws GeneralException {

        if (log.isInfoEnabled())
            log.info("addEntity " + entity.getName());

        Meter.enter(41, "Certificationer: Create certification entity");
        CertificationEntity certid = certCtx.createCertificationEntity(cert, entity);
        Meter.exit(41);

        if (certid != null) {
            cert.add(certid);

            // Go ahead and refresh the entity status so we don't have to walk
            // the entire certification hierarchy later.
            refreshEntityStatus(cert, certid);

            // calculate changes since the previous certification and copy
            // over some of the previous state. Only difference those
            // entities which can be differenced.
            if (entity.isDifferencable()) {
                Meter.enter(42, "Certificationer: Render certification differences");
                renderDifferences(certCtx, cert, certid, entity);
                Meter.exit(42);
            }
        }

        // Render the subordinate certification if there is one.
        Meter.enter(43, "Certificationer: Retrieve subordinate contexts");
        List<CertificationContext> subCtxs = certCtx.getSubordinateContexts(entity);
        Meter.exit(43);

        if ((null != subCtxs) && !subCtxs.isEmpty()) {

            // We may have unsaved entities, so before we let the sub-cert do
            // its thing, make sure to save the cert (this gives entities IDs)
            // and flush the fullEntitiesToRefresh.
            _context.saveObject(cert);
            _context.commitTransaction();
            cert.flushFullEntitiesToRefresh();
            cert.flushUnpersistedCommands();
            _context.saveObject(cert);
            _context.commitTransaction();

            for (CertificationContext subCtx : subCtxs) {

                Certification subCert = generateCertification(_requestor, subCtx);

                // Reattach the certification since the call to generate can
                // decache everything in the session.  Commit first to make
                // sure everything is flushed.
                _context.commitTransaction();
                cert = ObjectUtil.reattach(_context, cert);

                if (null != subCert) {
                    cert.add(subCert);

                    // Save and decache - We don't want the whole hierarchy in memory.
                    Meter.enter(44, "Certificationer: save and decache");
                    _context.saveObject(cert);
                    _context.saveObject(subCert);
                    _context.commitTransaction();
                    _context.decache(subCert);
                    Meter.exit(44);
                }
                else {
                    _errorHandler.addMessage(new Message(Message.Type.Warn,
                            MessageKeys.CERT_NOT_CREATED_NO_ENTITIES, subCtx.getOwners(),
                            entity.getTypeName(true).toLowerCase()));
                }
            }
        }
        return cert;
    }

    /**
     * Add a list of entities to a certification.
     */
    private Certification addEntities(CertificationContext certCtx, Certification cert,
                                      Iterator<? extends AbstractCertifiableEntity> entities)
        throws GeneralException {

        if (entities != null) {

            int count = 0;

            while (entities.hasNext()) {
                AbstractCertifiableEntity entity = entities.next();
                if (entity != null) {
                    cert = addEntity(certCtx, cert, entity);
    
                    ++count;
                    
                    if (log.isDebugEnabled())
                        log.debug("Adding " + entity.getName() + " to " + 
                                  cert.getCertifiers() + " certification");
    
                    if (count != 0 && ((count % _entityDecacheInterval) == 0 || !entities.hasNext())) {
                        if (log.isDebugEnabled())
                            log.debug("Entity count: " + count);
    
                        if (monitor != null) {
                            monitor.updateProgress("Entity count: " + count);
                        }
                        
                        printMemory("Add entities - pre-commit");
                        _context.saveObject(cert);
                        _context.commitTransaction();
    
                        // Flush the fullEntitiesToRefresh now that the entities
                        // are persisted and have IDs.
                        cert.flushFullEntitiesToRefresh();
                        cert.flushUnpersistedCommands();
    
                        cert = saveAndDecache(cert);
                        printMemory("Add entities - post-commit");
                    }
                }
            }
        }

        return cert;
    }

    /**
     * Save the given cert, commit the transaction, decache the session, and
     * return a reattached version of the cert.
     */
    private Certification saveAndDecache(Certification cert)
        throws GeneralException {

        _context.saveObject(cert);
        _context.commitTransaction();
        _context.decache();

        // Need to attach the Certification back to the session
        // since we decached.
        return ObjectUtil.reattach(_context, cert);
    }

    /**
     * Print some memory statistics.
     */
    private void printMemory(String fromWhence) {

        boolean printHibernateStats = false;

        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long total = rt.totalMemory();
        long used = total - free;

        if (log.isDebugEnabled())
            log.debug(fromWhence + ": memory usage " + used + "/" + total);

        if (printHibernateStats) {
            _context.printStatistics();
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Differencing
    //
    //////////////////////////////////////////////////////////////////////

    /**
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
    private void renderDifferences(CertificationContext certCtx,
                                   Certification cert,
                                   CertificationEntity certEntity,
                                   AbstractCertifiableEntity entity)
        throws GeneralException {

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
                                    certCtx.isIncludePolicyViolations());
                    certEntity.refreshHasDifferences(diff, _context);
                    certEntity.setDifferences(diff);
                }
            }
        }
        
        // This is a new user if we don't have a previous snapshot.
        certEntity.setNewUser((snapshot == null));
    }

    /**
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

    //////////////////////////////////////////////////////////////////////
    //
    // Certification Rendering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Render a certification and save it using the given CertificationContext.
     *
     * @param  requestor  The Identity requesting the certification.
     * @param  certCtx    The CertificationContext to use to generate the cert.
     */
    public Certification generateCertification(Identity requestor,
                                             CertificationContext certCtx)
        throws GeneralException {
        
        Certification cert = null;

        // sadly we can't use Meter at the entry/exit of this method
        // because it is called recursively and Meter can't handle recursion
        if (log.isInfoEnabled())
            log.info("generateCertification " + certCtx.generateShortName());

        // need this as we walk over the manager hierarchy
        _requestor = requestor;

        try {

            Meter.enter(46, "Certificationer: getPopulation");
            Iterator<? extends AbstractCertifiableEntity> population = certCtx.getPopulation();

            // return if pop is null
            if (population == null)
                return null;

            // call this once within the meter block it doesn't do anything until we ask
            population.hasNext();
            Meter.exit(46);

            // Only render if there are people to certify.
            if (population.hasNext()) {
                cert = certCtx.initializeCertification(_requestor);

                // TODO: Should we just store the context on the cert rather
                // than copying fields out of the context?
                certCtx.storeContext(cert);

                // Need to save the cert to allow committing the transaction and
                // decaching incrementally while adding entities.  If we don't
                // save as we go, we can create a HUGE cert in memory before we
                // save anything and blow out the heap.
                Meter.enter(47, "Certificationer: save");
                save(cert);
                Meter.exit(47);

                // recursive so Meter doesn't work
                //Meter.enter(48, "Certificationer: Add certification entities");
                cert = addEntities(certCtx, cert, population);
                //Meter.exit(48);

                // If the certifiation has nothing to certify, no subordinate certs and
                // no archived exclusions we can either delete it, or auto-signoff.
                if ( hasNothingToCertify( cert ) ){
                    // delete any certs that are completely empty
                    boolean autoSignOffWhenNothingToCertifyEnabled = isAutoSignOffWhenNothingToCertifyEnabled( cert ); 
                    if (Util.size(getChildCertInfos(cert)) == 0 && Util.size(cert.fetchArchivedEntities(_context))==0) {
                        delete(cert);
                        cert = null;
                    } else if (Certification.isChildCertificationsComplete(_context, cert.getId()) || autoSignOffWhenNothingToCertifyEnabled ){
                        // If the cert is empty and all subordinate certs are completed,
                        // auto-signoff since there's nothing to do. This should only occur
                        // where all items in a cert have been excluded.
                        refreshCompletion(cert, true, false);
                        CertificationService svc = new CertificationService(_context);
                        if (svc.isReadyForSignOff(cert)  && Certification.Phase.Staged != cert.getPhase()) {
                            sign(cert, _requestor);
                        }
                    } 
                }
            }
        }
        catch (GeneralException e) {
            // Rollback anything that hasn't yet been committed.
            _context.rollbackTransaction();

            // We save a certification before it is fully generated.  If we bump
            // into an error, we'll try to delete the cert (this is kind of a
            // manual rollback).
            if ((null != cert) && (null != cert.getId())) {
                try {
                    delete(cert);
                }
                catch (GeneralException e2) {
                    if (log.isErrorEnabled())
                        log.error("Error deleting cert during manual rollback: " + 
                                  cert.getName(), e2);
                }
            }

            throw e;
        }
        return cert;
    }

    private boolean isAutoSignOffWhenNothingToCertifyEnabled( Certification cert ) throws GeneralException {
        CertificationDefinition certificationDefinition = cert.getCertificationDefinition( _context );
        if( certificationDefinition == null ) {
            return false;
        }
        return certificationDefinition.isAutoSignOffWhenNothingToCertify();
    }

    private boolean hasNothingToCertify( Certification cert ) {
        boolean isEmpty = null == cert.getEntities() || cert.getEntities().isEmpty();
        return isEmpty;
    }

    /**
     * Check this certification to make sure that it can be saved.
     */
    private void validate(Certification cert)
        throws GeneralException {

        if ((cert.getCertifiers() == null) || cert.getCertifiers().isEmpty())
            throw new GeneralException("Can't commit an certification without an owner");

        // Should be set.  Do we just blow up if it isn't?
        if (null == cert.getName()) {
            throw new GeneralException("Can't commit a certification without a name");
        }

        // We no longer require unique names for certifications.
    }

    /**
     * Save the given certification.  This does NOT recurse into children
     * certifications.  Each level of certification needs to be saved
     * individually.  This commits the transaction so changes are flushed
     * from the session.
     */
    private void save(Certification cert)
        throws GeneralException {

        // First, validate to make sure the certification looks right.
        validate(cert);

        _context.saveObject(cert);
        _context.commitTransaction();
    }

    /**
     * Calculate some statistics about the certification and save them on the
     * cert.  This updates entity and items completion counts, item continuous
     * states, and remediation statistics.
     */
    public void updateCertificationStatistics(Certification cert)
        throws GeneralException {
        cert.resetStatistics();
        _statCounter.updateCertificationStatistics(cert, true);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Bulk Reassignment Rendering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Render a certification that contains the identities reassigned from
     * the given certification.
     */
    private Certification renderReassignmentCertification(Certification original,
                                                          Identity requester,
                                                          List<CertificationEntity> identities,
                                                          Identity origCertifier,
                                                          Identity actualCertifier,
                                                          String certName,
                                                          String comments, 
                                                          String description,
                                                          boolean selfCertificationReassignment)
        throws GeneralException {

        // Create a copy of the original without any of the contents.
        Certification cert = new Certification(original);

        // Use the actual certifier (after forwarding) to name the certification.
        String name =
            Util.getFullname(actualCertifier.getFirstname(), actualCertifier.getLastname());
        if (null == Util.getString(name)) {
            name = actualCertifier.getName();
        }

        // If a name was specified, use it for the name and shortname, otherwise
        // use the configured message keys to name the cert.
        String certShortname = certName;
        if (null == certName) {
            Certification root = getReassignmentRoot(original);
            Message certNameMsg =
                new Message(MessageKeys.CERT_NAME_REASSIGNMENT, root.getName(), name);
            Message certShortnameMsg =
                new Message(MessageKeys.CERT_SHORTNAME_REASSIGNMENT, root.getShortName());
            certName = certNameMsg.getLocalizedMessage();
            certShortname = certShortnameMsg.getLocalizedMessage();
        }

        // Set the new properties for the reassignment.
        cert.setName(certName);
        cert.setShortName(certShortname);
        cert.setBulkReassignment(!selfCertificationReassignment);
        cert.setSelfCertificationReassignment(selfCertificationReassignment);
        cert.setCreator(requester);
        cert.setComments(comments);
        cert.getAttributes().put(Certification.ATT_REASSIGNMENT_DESCRIPTION, description);

        // Set the certifier to the originally requested certifier.  Starting
        // the certification will handle auto-forwarding so that we get auditing
        // of the forwards.
        List<Identity> certifiers = new ArrayList<Identity>();
        certifiers.add(origCertifier);
        cert.setCertifierIdentities(certifiers);

        // Add all of the entities to the cert.  These may have delegations to
        // the bulk assignment recipient ... if so, these will be removed in
        // after this certification has been persisted in bulkReassign().
        cert.addAll(identities);

        // Save this object - this must be saved before refreshing completion
        // because we run queries to determine the statistics.
        _context.saveObject(cert);

        // Reset the work item references to the new reassign cert
        for(CertificationEntity entity : identities) {
            // Move work item references to point to new cert
            List<WorkItem> workItems = getWorkItems(this._context, entity);
            for (WorkItem workItem : workItems) {
                workItem.setCertification(cert);
                this._context.saveObject(workItem);
            }
        }

        // refresh statistics - do a full refresh since we haven't
        // calculated any of this yet.
        // TODO: get rid of the full refresh!!!
        refreshCompletion(cert, false, false);

        // Now, save it.
        save(cert);

        return cert;
    }

    /**
     * Return the root of the reassignments in a certification hierarchy by
     * walking up the parent chain until there are no more reassignment parents.
     */
    private Certification getReassignmentRoot(Certification cert) {

        // Keep looking if the current cert is a reassignment and there are
        // more that we can look at.
        if (cert.isBulkReassignment() && (null != cert.getParent())) {
            return getReassignmentRoot(cert.getParent());
        }

        return cert;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Notifications
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate a delegation notification from a work item.
     * Note that it is important that we work from the WorkItem
     * whose owner may have been forwarded from the originally
     * selected delegate.
     */
    private void notifyDelegation(Certification cert, WorkItemMonitor delegation, WorkItem item)
        throws GeneralException {

        EmailTemplate email = emailTemplateRegistry.getTemplate(cert, Configuration.DELEGATION_EMAIL_TEMPLATE);
        if (email != null) {

            Identity owner = item.getOwner();
            Identity requester = item.getRequester();
            List<String> ownerEmails = ObjectUtil.getEffectiveEmails(_context,owner);

            if (ownerEmails == null) {
                if (log.isWarnEnabled())
                    log.warn("Work item owner (" + owner.getName() + ") has no email. " +
                             "Could not send delegation notification.");
            }
            else {

                // For now, we'll just use a map with a few pre-selected properties.
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("workItemName", item.getDescription());
                args.put("comments", delegation.getComments());
                args.put("certification", cert);
                //IIQETN-5899 :- Sending workItem as parameter to be used in delegation template
                args.put("workItem", item);
                if (null != requester) {
                    args.put("requesterName", requester.getDisplayableName());
                }
                EmailOptions ops = new EmailOptions(ownerEmails, args);

                // Don't send duplicate delegation emails.  Multiple of these
                // can be sent at the same time if certification items are being
                // escalated on a continuous certification.
                if (this.getEmailSuppressor().shouldSend(email, ops.getTo())) {
                    sendEmailNotification(_context, email, ops);
                }
            }
        }
    }

    /**
     * Generate a delegation revocation notification from a work item.
     */
    private void notifyDelegationRevocation(Certification cert, WorkItemMonitor delegation, WorkItem item)
        throws GeneralException {

        EmailTemplate template = emailTemplateRegistry.getTemplate(cert,
                Configuration.DELEGATION_REVOCATION_EMAIL_TEMPLATE);

        if (null != template) {

            Identity owner = item.getOwner();
            Identity requester = item.getRequester();
            List<String> ownerEmails = ObjectUtil.getEffectiveEmails(_context, owner);
            if (ownerEmails == null) {
                if (log.isWarnEnabled())
                    log.warn("Work item owner (" + owner.getName() + ") has no email. " +
                             "Could not send delegation revocation notification.");
            }
            else {
                // For now, we'll just use a map with a few pre-selected properties.
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("workItemName", item.getDescription());
                args.put("certification", cert);
                if (null != requester) {
                    args.put("requesterName", requester.getDisplayableName());
                }
                EmailOptions ops = new EmailOptions(ownerEmails, args);
                sendEmailNotification(_context, template, ops);
            }
        }
    }

    /**
     * Generate bulk reassignment notification using information from cert itself.
     * This is useful when deferring email due to staging, for example.
     * @param cert
     */
    private void notifyBulkReassignment(Certification cert, List<WorkItem> items) 
            throws GeneralException {

        if (items != null) {
            for (WorkItem item : items) {
                notifyBulkReassignment(cert,
                        item.getOwner(),
                        item.getRequester(),
                        cert.getTotalEntities(),
                        cert.getComments(),
                        cert.getAttributes().getString(Certification.ATT_REASSIGNMENT_DESCRIPTION));
            }
        }
    }

    /**
     * Generate a bulk delegation notification when more identities are added
     * to a bulk reassignment certification.
     */
    private void notifyBulkReassignment(Certification cert,
                                        CertificationCommand.BulkReassignment cmd,
                                        List<CertificationEntity> addedEntities)
            throws GeneralException {

        notifyBulkReassignment(cert,
                cmd.getRecipient(),
                cmd.getRequester(),
                addedEntities.size(),
                cmd.getComments(),
                cmd.getDescription());
    }
    
    private void notifyBulkReassignment(Certification cert,
                                        Identity owner,
                                        Identity requester,
                                        int numNewIdentities, 
                                        String comments,
                                        String description)
        throws GeneralException {

    EmailTemplate template =
                emailTemplateRegistry.getTemplate(cert, Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE);

        if (template != null) {

            // Note that we assume the recipient has already been forwarded
            List<String> ownerEmails = ObjectUtil.getEffectiveEmails(_context,owner);

            if (ownerEmails == null) {
                if (log.isWarnEnabled())
                    log.warn("Certification owner (" + owner.getName() + ") has no email. " +
                             "Could not send bulk reassignment notification.");
            }
            else {
                // For now, we'll just use a map with a few pre-selected properties.
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("certificationName", cert.getName());
                args.put("certification", cert);
                args.put("owner", owner);
                args.put("comments", comments);
                args.put("description", description);
                args.put("numNewIdentities", Integer.toString(numNewIdentities));
                if (null != requester) {
                    args.put("requesterName", requester.getDisplayableName());
                }
                EmailOptions ops = new EmailOptions(ownerEmails, args);
                sendEmailNotification(_context, template, ops);
            }
        }
    }

    /**
     * Send the given email and save any errors encountered while mailing.
     *
     * @param  email  The EmailTemplate to send.
     * @param  ops    The EmailOptions to use.
     */
    public void sendEmailNotification(SailPointContext ctx, EmailTemplate email,
                                      EmailOptions ops) {
        new Emailer(_context, _errorHandler).sendEmailNotification(email, ops);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Start
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationStartResults start(Certification cert)
        throws GeneralException {

        return start(cert.getId(), new CertificationStartResults());
    }

    private boolean isStagingEnabled(Certification cert)
    	throws GeneralException {
    	
    	CertificationDefinition definition = cert.getCertificationDefinition(_context);
        if (definition != null) {
            return definition.isStagingEnabled();
        }
        
        return false;
    }
    
    private boolean shouldBypassStagingPeriod(Certification cert)
    	throws GeneralException {
    	
    	for (CertificationGroup certGroup : Util.safeIterable(cert.getCertificationGroups())) {
    		if (!CertificationGroup.Status.Pending.equals(certGroup.getStatus()) && !CertificationGroup.Status.Staged.equals(certGroup.getStatus())) {
    			return true;
    		}
    	}
    	
    	return false;
    }

    /**
     * Start the given certification and its children.  The certification should
     * have already been persisted during generation.  This will transition the
     * certification into the active or staged phase, open work items, send email
     * notifications, perform pre-activation activities (such as pre-delegation),
     * etc...
     *
     * @param  certId  The ID of the Certification start.
     *
     * @return The WorkItems generated for all certifications in the hierarchy.
     */
    public CertificationStartResults start(String certId, CertificationStartResults results, boolean recurse)
        throws GeneralException {
        
        return start(_context.getObjectById(Certification.class, certId), results, recurse);
    }
    
    /**
     * Start the given certification and its children.  The certification should
     * have already been persisted during generation.  This will transition the
     * certification into the active or staged phase, open work items, send email
     * notifications, perform pre-activation activities (such as pre-delegation),
     * etc...
     *
     * @param  cert  The Certification to start.
     *
     * @return The WorkItems generated for all certifications in the hierarchy.
     */
    public CertificationStartResults start(Certification cert, CertificationStartResults results, boolean recurse)
        throws GeneralException {
        
        results.incCertificationCount();
        try {
            if ( log.isInfoEnabled() ) {
                log.info("Starting certification ["+cert.getName()+"]");
            }
            boolean stageCertification = isStagingEnabled(cert) && !shouldBypassStagingPeriod(cert);
            
            // The phaser will create work items, generate notifications, etc...
            CertificationPhaser phaser =
                new CertificationPhaser(_context, _errorHandler, getEmailSuppressor());
            
            cert = advanceToPhase(cert, phaser, stageCertification ? Phase.Staged : Phase.Active);
            // Get all of the work items off of the certification.
            List<WorkItem> items = cert.getWorkItems();
            if (items != null) {
                for(WorkItem item : items){
                    results.getWorkItemIds().add(item.getId());
                }
            }
            
            boolean generateWorkItems = !stageCertification && (cert.getSigned() == null);

            // We now allow pre-activation decisions (such as pre-delegation).
            // Refresh the certification to kick these off.
            Meter.enter(51, "Certificationer - Refresh activated certification");
 
            refresh(cert, generateWorkItems);
            Meter.exit(51);

            _context.decache();

            if (recurse) {
                // Recurse into children.
                QueryOptions ops = new QueryOptions(Filter.eq("parent.id", cert.getId()));
                ops.setCloneResults(true);
                Iterator<Object[]> children = _context.search(Certification.class, ops, Arrays.asList("id"));
                if (null != children) {
                    while(children.hasNext()){
                        String childId = (String)children.next()[0];
                        start(childId, results);
                    }
                }
            }
            
            cert = ObjectUtil.reattach(_context, cert);

            if (generateWorkItems) {
                notifyStart(cert, false);                
            }

        }
        catch (GeneralException e) {
            // Instead of the typically prescribed 'log.error(String, Throwable)', just log an error msg
            // Callers will catch the exception up the stack and report the stack trace appropriately
            log.error("Error starting Certification " + cert);

            _context.rollbackTransaction();
            throw e;
        }
      
        return results;
    }
    
    public CertificationStartResults start(String certId, CertificationStartResults results)
            throws GeneralException {
        
        return start(certId, results, true);
    }    
    /**
     * Activates a staged certification.
     * @param certId The id of the certification to active.
     */
    public void activate(String certId)
        throws GeneralException
    {    
        try {
            Certification cert = _context.getObjectById(Certification.class, certId);
            
            // The phaser will create work items, generate notifications, etc...
            CertificationPhaser phaser =
                new CertificationPhaser(_context, _errorHandler, getEmailSuppressor());
            
            cert = advanceToPhase(cert, phaser, Phase.Active);
            
            markDelegatedEntitiesForRefresh(certId);
            
            Meter.enter(51, "Certificationer - Refresh activated certification");
            refresh(cert);
            Meter.exit(51);
            
            _context.decache();

            // Recurse into children.
            QueryOptions ops = new QueryOptions(Filter.eq("parent.id", certId));
            ops.setCloneResults(true);
            Iterator<Object[]> children = _context.search(Certification.class, ops, Arrays.asList("id"));
            if (null != children) {
                while(children.hasNext()){
                    String childId = (String)children.next()[0];
                    activate(childId);
                }
            }
            cert = ObjectUtil.reattach(_context, cert);
            notifyStart(cert, true);
            
            if( hasNothingToCertify(cert) ) {
                //Since we are now activating the staged cert, we need to make sure to signoff if needed
                final boolean autoSignOffWhenNothingToCertifyEnabled = isAutoSignOffWhenNothingToCertifyEnabled( cert ); 
                if (Certification.isChildCertificationsComplete(_context, cert.getId()) || autoSignOffWhenNothingToCertifyEnabled ){
                    autoSignoff(cert);
                } else if ( cert.hasBulkReassignments() ){
                    final boolean autoReassignmentSignoffEnabled = cert.isAutoSignoffOnReassignment();
                    if (autoReassignmentSignoffEnabled) {
                        autoSignoff(cert);
                    }
                }
            }

        }
        catch (GeneralException e) {
            _context.rollbackTransaction();
            throw e;
        }        
    }
    
    /**
     * Marks delegated items on the certification for refresh.
     * This is called when activating a staged certification to generate work items
     * and send notification emails that were suppressed.
     * 
     * @param certId The certification id.
     */
    private void markDelegatedEntitiesForRefresh(String certId)
        throws GeneralException
    {
        Certification cert = _context.getObjectById(Certification.class, certId);
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("certification.id", certId));
        options.add(Filter.notnull("delegation"));

        options.setCloneResults(true);

        Iterator<Object[]> delegatedEntities = _context.search(CertificationEntity.class, options, "id");
        
        long numProcessed = 0;
        
        while (delegatedEntities.hasNext()) {
            String entityId = (String)delegatedEntities.next()[0];
            CertificationEntity entity = _context.getObjectById(CertificationEntity.class, entityId);
            entity.markForRefresh();
            ++numProcessed;
            if (numProcessed % 5 == 0) {
                _context.commitTransaction();
                _context.decache();
            }  
            _context.saveObject(cert); 
            
        }        
        _context.commitTransaction();
    }
    
    private void notifyStart(Certification cert, boolean notifyBulkReassignment)
        throws GeneralException {

        // During execute command phase the cert may have been caused to be reattached
        // which means this instance of cert is may not be valid. Need to refetch for workitems
        cert = ObjectUtil.reattach(_context, cert);
        
        List<WorkItem> items = cert.getWorkItems();
        if (items != null) {

            boolean suppressInitialNotification = false;

            CertificationDefinition certDef = cert.getCertificationDefinition(_context);
            if (certDef != null) {
                suppressInitialNotification = certDef.isSuppressInitialNotification(_context);
                suppressInitialNotification |= ( certDef.isSuppressEmailWhenNothingToCertify() && hasNothingToCertify( cert ) );
            }

            if (!cert.isBulkReassignment()) {
                EmailTemplate notifEmail = this.emailTemplateRegistry.getTemplate(cert, Configuration.CERTIFICATION_EMAIL_TEMPLATE);
                if (!suppressInitialNotification && (null != notifEmail)) {
                    for (WorkItem item : items) {
                        Identity owner = item.getOwner();
                        Identity requester = item.getRequester();
                        List<String> ownerEmails = ObjectUtil.getEffectiveEmails(_context, owner);
                        if ( ownerEmails == null ) {
                            if (log.isWarnEnabled())
                                log.warn("Work item owner (" + owner.getName() + ") has no email. " +
                                        "Could not send certification notification.");
                        }
                        else {
                            // if we can't send the email, should store
                            // some indication of that in the work item and
                            // set up a retry timer

                            // For now, we'll just use a map with a few pre-selected properties.
                            Map<String,Object> args = new HashMap<String,Object>();
                            args.put("workItemName", item.getDescription());
                            args.put("workItem", item);
                            args.put("certification", cert);
                            if (null != requester) {
                                args.put("requesterName", requester.getDisplayableName());
                                args.put("sender", requester);
                            }
                            args.put("ownerName", owner.getDisplayableName());
                            args.put("recipient", owner);
                            EmailOptions ops = new EmailOptions(ownerEmails, args);
                            sendEmailNotification(_context, notifEmail, ops);
                        }
                    }
                }

                // IIQMAG-2935 In a non-staged certification, a bulk reassignment notification is also sent
                // for any self-certification. During creation, a certification can either be a bulk
                // reassignment or a self-certification but not both. We'll send out the bulk reassignment
                // notification if this is a self-cert when the certification is activated to match the
                // notifications sent out in the non-staged case.
                if (cert.isSelfCertificationReassignment()) {
                    notifyBulkReassignment(cert, items);
                }
            } else if (notifyBulkReassignment) {
                notifyBulkReassignment(cert, items);
            }
        }
    }
    

    /**
     * Transition the given certification (not the sub-certs) to a target phase
     * unless it has been skipped, in which case we transition to the next unskipped
     * phase beyond it.
     * @return The WorkItems that were generated for the given certification.
     */
    private Certification advanceToPhase(Certification cert, CertificationPhaser phaser, Phase targetPhase)
        throws GeneralException {

        Meter.enter(50, "Certificationer - Advance to Phase");

        // Advance the phase if we're not already there.
        while (!isReached(phaser, cert.getPhase(), targetPhase)) {
            cert = (Certification)phaser.advancePhase(cert);
            _context.commitTransaction();
        }

        Meter.exit(50);

        return cert;
    }

    /**
     * Determine whether or not the currentPhase has reached the target phase
     * @param phaser CertificationPhaser that is being used to advance the phase
     * @param currentPhase Phase that the Certification is currently in
     * @param targetPhase Phase that we are attempting to reach
     * @return true if the targetPhase has been reached; false otherwise
     */
    private boolean isReached(CertificationPhaser phaser, Phase currentPhase, Phase targetPhase) {
        return targetPhase == currentPhase || phaser.isSkipped(targetPhase) || Phase.End == currentPhase;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An iterator that will iterate over the identities to be refreshed in a
     * certification.
     */
    private class CertificationEntityIterator
        implements Iterator<CertificationEntity> {

        private Iterator<String> idIterator;
        private Iterator<CertificationEntity> fullEntityIterator;

        public CertificationEntityIterator(Certification cert) {

            // iiqetn-4339 - When remediating multiple certification items that have been
            // challenged and are now being saved, we end up in a circular loop between
            // Certificationer.refresh() and ChallengePhaseHandler.postExit() where we
            // iterate through the same _entitiesToRefresh list several times. When we
            // finally start to recurse back up the stack Certificationer.refreshCompletion()
            // clobbers the _entitiesToRefresh list before we've exited from all the levels
            // of nested calls and this next() method will throw a ConcurrentModificationException.
            // From the comments in those methods it looks like this circular nesting was
            // intentional so it could call whatever postprocessing code that might be needed.
            // To prevent the ConcurrentModificationException we'll make a copy of the
            // _entitiesToRefresh list so the nested iterators can continue on their merry way.
            Collection<String> identities = new ArrayList<String>();
            if (null != cert.getEntitiesToRefresh()) {
                identities.addAll(cert.getEntitiesToRefresh());
                this.idIterator = identities.iterator();
            }

            if (null != cert.getFullEntitiesToRefresh()) {
                this.fullEntityIterator = cert.getFullEntitiesToRefresh().iterator();
            }
        }

        public CertificationEntity next() {
            if (hasNextId()) {
                try {
                    return _context.getObjectById(CertificationEntity.class, this.idIterator.next());
                }
                catch (GeneralException e) {
                    // Iterator can't throw checked exception, so wrap in a runtime.
                    throw new RuntimeException(e);
                }
            }

            if (hasNextEntity()) {
                return this.fullEntityIterator.next();
            }

            throw new NoSuchElementException();
        }

        public boolean hasNext() {
            return hasNextId() || hasNextEntity();
        }

        private boolean hasNextId() {
            return ((null != this.idIterator) && this.idIterator.hasNext());
        }

        private boolean hasNextEntity() {
            return ((null != this.fullEntityIterator) && this.fullEntityIterator.hasNext());
        }

        // No-op
        public void remove() {}
    }

    /**
     * Return an iterator over the identities in the given certification.  If
     * <code>incremental</code> is true, this iterates over only the identities
     * in the identitiesToRefresh list on the certification, otherwise, this
     * iterates over all identities in the certification.
     */
    private Iterator<CertificationEntity> iterateIdentities(Certification cert,
                                                              boolean incremental)
        throws GeneralException {

        if (incremental) {
            return new CertificationEntityIterator(cert);
        }

        Set<CertificationEntity> empty = Collections.emptySet();
        return (null != cert.getEntities()) ? cert.getEntities().iterator() : empty.iterator();
    }

    public List<Message> refresh(Certification cert)
        throws GeneralException
    {
        return refresh(cert, true);
    }
    
    public List<Message> refresh(Certification cert, boolean generateWorkItems)
       throws GeneralException {

       return refresh(cert, generateWorkItems, true);
    }
    
    /**
     * Ponder the significance of changes made to an Certification
     * and perform requested operations such as sending email notifications
     * and managing delegation work items.
     *
     * Since the object may result in many operations, we generally do not
     * throw exceptions on errors and halt the refresh.  Instead errors
     * are accumulated in a message list and returned (could be left
     * in the certification!!).  That way, we do as much as we can rather
     * than halting on the first error.
     * Hmm, not sure that's really necessary?
     *
     * Note that calling refresh may decache all objects from the session
     * (currently only if there are bulk reassignments).  As such, the calling
     * code will need to take care to reattach any objects as necessary.
     */
    public List<Message> refresh(Certification cert, boolean generateWorkItems, boolean refreshAll)
        throws GeneralException {

        _errorHandler.clear();

        try {
            // Clear the errors left from the previous trip through the
            // certificationer.
            cert.setError(null);

            // Note that executing the commands will probably commit the
            // transaction and can call decache().
            Meter.enter(52,"Certificationer: Refresh - execute commands");
            cert = executeCommands(cert);

            // Check for any self certification issues in this cert and send them to the self certification reassignment
            cert = reassignSelfCertificationItems(cert);

            Meter.exit(52);

            // The phaser will get a chance to do phase-specific refreshing on
            // each entity.
            CertificationPhaser phaser =
                new CertificationPhaser(_context, _errorHandler, getEmailSuppressor());

            Meter.enter(53,"Certificationer: Refresh - iterate identities");
            Iterator<CertificationEntity> toRefresh = iterateIdentities(cert, true);
            if (toRefresh.hasNext()) {

                // look for new work items to generate
                while (toRefresh.hasNext()) {

                    CertificationEntity entity = toRefresh.next();
                    // Protect against entitiesToRefresh being out of date with actual entities in the cert.
                    if (entity == null) {
                        continue;
                    }

                    // Let the phaser advance the phase on this entity if any
                    // items are in a state where they can be advanced and we're
                    // using rolling phases.
                    phaser.handleRollingPhaseTransitions(cert, entity, refreshAll);

                    // Let the phaser do phase-specific refreshing first.
                    phaser.refresh(cert, entity, refreshAll);

                    // Refresh and roll-up bulk certification status.  If all
                    // actions are bulk certified the identity is bulk certified.
                    // If any are not bulk certified, the identity is not bulk
                    // certified.
                    boolean bulkCertified = refreshItem(cert, entity, generateWorkItems, refreshAll);
                    entity.setBulkCertified(bulkCertified);

                    // Allow a rule to do some of it's own refreshing.
                    runEntityRefreshRule(cert, entity);

                    // If we're processing revokes immediately, launch them.
                    // If we already rolled into the challenge phase for this
                    // item, we'll defer launching the remediation.
                    if (cert.isProcessRevokesImmediately()) {
                        handleRemediations(cert, entity);
                    }

                    cert = ObjectUtil.reattach(_context, cert);
                }

                // Always run postRemediate in case any remediations happened.
                postRemediate(cert);

                // Check for any delegations that are self certification violations and forward
                // them to the correct owner
                // Important not to do this if we are not generating work items, because the delegation forwarding
                // requires them to be generated already.
                if (generateWorkItems) {
                    cert = forwardSelfCertificationDelegations(cert);
                }
            }
            Meter.exit(53);

            // refresh completion status and dates
            // be sure to do this after refreshing the work items so we
            // get an accurate delegation count
            Meter.enter(54,"Certificationer: Refresh completion");
            refreshCompletion(cert, true, false, true, refreshAll);
            Meter.exit(54);

            Meter.enter(55,"Certificationer: Refresh - save and commit");
            _context.saveObject(cert);
            _context.commitTransaction();
            Meter.exit(55);
            
        }
        catch (GeneralException e) {
            _context.rollbackTransaction();
            throw e;
        }

        List<Message> msgs = new ArrayList<Message>();
        msgs.addAll(getErrors());
        msgs.addAll(getWarnings());

        return msgs;
    }

    /**
     * Based on the cert type, get the class that would target an identity. This is useful for the SelfCertificationChecker.
     */
    private Class<? extends AbstractCertificationItem> getIdentityTargetClass(Certification cert) {
        Certification.Type certType = cert.getType();
        Class<? extends AbstractCertificationItem> identityTargetClass = null;
        if (CertificationEntity.Type.Identity.equals(certType.getEntityType())) {
            identityTargetClass = CertificationEntity.class;
        } else if (Certification.Type.DataOwner.equals(certType) || Certification.Type.AccountGroupMembership.equals(certType)) {
            identityTargetClass = CertificationItem.class;
        }

        return identityTargetClass;
    }

    /**
     * Get the owner identity for self certification violations. This can be null, in which case we will
     * not reassign or forward any self certification violations.
\     */
    private Identity getSelfCertificationViolationOwner(Certification cert) throws GeneralException {
        CertificationDefinition certificationDefinition = cert.getCertificationDefinition(_context);
        // Really shouldnt be null, but can happen in unit tests
        if (certificationDefinition == null) {
            return null;
        }

        return certificationDefinition.getSelfCertificationViolationOwner(_context);
    }

    /**
     * Get the identity to use as the requestor for system reasssignments or forwards.
     */
    private Identity getDefaultRequestor(Certification cert) throws GeneralException {
        Identity requestor = null;
        if ((null != cert.getCertifiers()) && !cert.getCertifiers().isEmpty()) {
            requestor = _context.getObjectByName(Identity.class, cert.getCertifiers().get(0));
        }
        else {
            requestor = cert.getCreator(_context);
        }

        return requestor;
    }

    /**
     * Checks all entities or items in the certification for self certification against any of the certifiers (excluding workgroups)
     * and reassigns them all to the self certification reassignment cert. This uses the CertificationCommand infrastructure so if
     * reassignment is needed there will probably be commmitting and decaching.
     *
     * @param cert The certification
     * @return The resulting certification that might have been decached and refetched.
     * @throws GeneralException
     */
    private Certification reassignSelfCertificationItems(Certification cert) throws GeneralException {
        // Do not check the self certification reassignment again
        if (cert.isSelfCertificationReassignment()) {
            return cert;
        }

        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(_context, cert);
        if (selfCertificationChecker.isAllSelfCertifyAllowed()) {
            return cert;
        }

        // No owner is configured, so nothing to do.
        Identity selfCertOwner = getSelfCertificationViolationOwner(cert);
        if (selfCertOwner == null) {
            return cert;
        }

        Class<? extends AbstractCertificationItem> identityTargetClass = getIdentityTargetClass(cert);
        if (identityTargetClass != null) {
            List<String> allSelfCertIds = new ArrayList<>();
            for (String certifierName : cert.getCertifiers()) {
                Identity certifier = _context.getObjectByName(Identity.class, certifierName);
                if (selfCertificationChecker.isSelfCertifyAllowed(certifier)) {
                    continue;
                }

                List<String> ids = selfCertificationChecker.getIdentityTargetIds(certifier, null, identityTargetClass);
                if (!Util.isEmpty(ids)) {
                    allSelfCertIds.addAll(ids);
                }
            }

            // If we found some self certification violations in the cert, make a BulkReassignment command and execute it
            if (!Util.isEmpty(allSelfCertIds)) {
                CertificationCommand.BulkReassignment selfCertReassignment = new CertificationCommand.BulkReassignment(getDefaultRequestor(cert),
                        identityTargetClass,
                        allSelfCertIds,
                        selfCertOwner,
                        new Message(MessageKeys.CERT_NAME_SELF_CERTIFICATION_REASSIGNMENT, selfCertOwner.getDisplayableName()).getLocalizedMessage(),
                        new Message(MessageKeys.SELF_CERTIFICATION_REASSIGNMENT_DESCRIPTION).getLocalizedMessage(),
                        null);
                selfCertReassignment.setSelfCertificationReassignment(true);
                selfCertReassignment.setCheckLimitReassignments(false);
                selfCertReassignment.setCheckSelfCertification(false);
                cert.addCommand(selfCertReassignment);
                cert = executeCommands(cert);

                String messageKey = CertificationItem.class.equals(identityTargetClass) ? MessageKeys.SELF_CERTIFICATION_REASSIGNMENT_MESSAGE_ITEMS : MessageKeys.SELF_CERTIFICATION_REASSIGNMENT_MESSAGE_ENTITIES;
                addMessage(new Message(Message.Type.Warn, messageKey, Util.size(allSelfCertIds), cert.getName()));
            }
        }

        return cert;
    }

    /**
     * Checks all the delegations in this certification, both entity and item, looking for self certification situations.
     * If any are found, revoke those delegations.
     */
    private Certification forwardSelfCertificationDelegations(Certification cert) throws GeneralException {

        // Use HQL here because we want to filter on equality of two properties on the same object, which is not possible with current Filters.
        final String ENTITY_HQL = "SELECT ce.delegation.workItem " +
                "FROM CertificationEntity ce " +
                "WHERE ce.certification.id = :certificationId " +
                "AND ce.identity = ce.delegation.ownerName " +
                "AND ce.delegation.ownerName != :selfCertOwner";
        final String ITEM_HQL = "SELECT ci.delegation.workItem " +
                "FROM CertificationItem ci " +
                "WHERE ci.parent.certification.id = :certificationId " +
                "AND ci.parent.identity = ci.delegation.ownerName " + "" +
                "AND ci.delegation.ownerName != :selfCertOwner";
        final String IDENTITY_ON_ITEM_ENTITY_HQL = "SELECT DISTINCT ci.parent.delegation.workItem " +
                "FROM CertificationItem ci " +
                "WHERE ci.parent.certification.id = :certificationId " +
                "AND ci.targetName = ci.parent.delegation.ownerName " +
                "AND ci.parent.delegation.ownerName != :selfCertOwner";
        final String IDENTITY_ON_ITEM_ITEM_HQL = "SELECT ci.delegation.workItem " +
                "FROM CertificationItem ci " +
                "WHERE ci.parent.certification.id = :certificationId " +
                "AND ci.targetName = ci.delegation.ownerName " +
                "AND ci.delegation.ownerName != :selfCertOwner";

        Identity selfCertOwner = getSelfCertificationViolationOwner(cert);
        if (selfCertOwner == null) {
            return cert;
        }

        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(_context, cert);
        if (selfCertificationChecker.isAllSelfCertifyAllowed()) {
            return cert;
        }

        boolean isIdentityOnItem = Certification.Type.AccountGroupMembership.equals(cert.getType()) || Certification.Type.DataOwner.equals(cert.getType());

        cert = forwardSelfCertificationDelegations(cert, selfCertOwner, isIdentityOnItem ? IDENTITY_ON_ITEM_ENTITY_HQL : ENTITY_HQL);
        cert = forwardSelfCertificationDelegations(cert, selfCertOwner, isIdentityOnItem ? IDENTITY_ON_ITEM_ITEM_HQL : ITEM_HQL);

        return cert;
    }

    /**
     * Runs the HQL query to find delegation work items where the owner is the same as the target identity, then checks self certify on those
     * and forwards those to the self cert violation owner.
     *
     * Note this will commit and decache as necessary
     */
    private Certification forwardSelfCertificationDelegations(Certification cert, Identity selfCertOwner, String HQL) throws GeneralException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("certificationId", cert.getId());
        params.put("selfCertOwner", selfCertOwner.getName());

        //Need to clone since we will process each individually
        QueryOptions ops = new QueryOptions();
        ops.setCloneResults(true);
        Iterator results = _context.search(HQL, params, ops);
        if (!results.hasNext()) {
            return cert;
        }

        int itemCount = 0;
        Workflower wf = new Workflower(_context);
        Identity requestor = getDefaultRequestor(cert);
        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(_context, cert);
        while (results.hasNext()) {
            String workItemId = (String) results.next();
            // Shouldn't happen because we are not calling this for staged certs,
            // but no reason to fail the refresh either.
            if (workItemId == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Delegation found without work item, skipping forward.");
                }
                continue;
            }

            WorkItem workItem = _context.getObjectById(WorkItem.class, workItemId);
            if (workItem == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Delegation work item " + workItemId + " does not exist");
                }
                continue;
            }

            if (selfCertificationChecker.isSelfCertifyAllowed(workItem.getOwner())) {
                continue;
            }

            wf.forward(workItem,
                    requestor,
                    selfCertOwner,
                    new Message(MessageKeys.SELF_CERTIFICATION_DELEGATION_FORWARD_DESCRIPTION).getLocalizedMessage(),
                    true,
                    ForwardType.SelfCertification);

            if (++itemCount % 20 == 0) {
                // Decache to avoid bloat, wf.forward will commit so just decache and reattach
                _context.decache();
                cert = ObjectUtil.reattach(_context, cert);
            }
        }

        if (itemCount > 0) {
            addMessage(new Message(Message.Type.Warn, MessageKeys.SELF_CERTIFICATION_DELEGATION_MESSAGE, itemCount, cert.getName(), selfCertOwner.getDisplayableName()));
        }

        return cert;
    }

    /**
     * Run the entity refresh rule (if configured) for the given entity.
     */
    private void runEntityRefreshRule(Certification cert,
                                      CertificationEntity entity)
        throws GeneralException {

        Rule rule = getEntityRefreshRule(cert);
        if (null != rule) {
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("certification", cert);
            args.put("entity", entity);
            _context.runRule(rule, args);
        }
    }

    /**
     * Execute all CertificationCommands on the given Certification.
     */
    private Certification executeCommands(Certification cert)
        throws GeneralException {

        if (!Util.isEmpty(cert.getCommands())) {
            boolean incrementalRefreshAllowed = false;
            for (CertificationCommand cmd : cert.getCommands()) {
                try {
                    // The decache() may have hosed the command ... reattach it.
                    // jsl - in an odd state for the first one too, not sure why
                    // but start off with a fresh cache before we start reattaching
                    _context.decache();
                    cert = ObjectUtil.reattach(_context, cert);
                    cmd = reattach(cmd);
                    executeCommand(cert, cmd);
                    // executeCommand could have decached
                    cert = ObjectUtil.reattach(_context, cert);
                    //We now mark partially-removed entities with partial item reassignment for incremental refresh
                    //during bulk reassign.  Because entities are either fully or partially removed, we can
                    //now safely do an incremental refresh on the original cert from which reassignments were processed.
                    if(cmd instanceof CertificationCommand.BulkReassignment) {
                        incrementalRefreshAllowed = true;
                    }
                    cert = saveAndDecache(cert);
                } catch (SelfCertificationException sce) {
                    // Since this command would cause self certification, it will never
                    // succeed and must be removed from the cert.
                    // Rollback whereever we are, remove command and save cert
                    _context.rollbackTransaction();
                    cert = ObjectUtil.reattach(_context, cert);
                    cert.removeCommand(cmd);
                    saveAndDecache(cert);
                    if (log.isErrorEnabled())
                        log.error("Error self-certification: " + 
                                  cert.getName(), sce);
                    throw sce;
                } catch(LimitReassignmentException lre) {
                    // Since this command would cause reassignment limit, it will never
                    // succeed and must be removed from the cert.
                    // Rollback whereever we are, remove command and save cert
                    if (log.isErrorEnabled())
                        log.error("Failed to reassign due to reassignment limit: " + 
                                  cert.getName(), lre);
                    _context.rollbackTransaction();
                    cert = ObjectUtil.reattach(_context, cert);
                    cert.removeCommand(cmd);
                    saveAndDecache(cert);
                    throw lre;
                } catch (Throwable th) {
                    if (log.isErrorEnabled()) {
                        log.error("Failure executing command for certification: " +
                                cert.getName(), th);
                    }
                    // rollback the transaction to keep trash in the session from being saved
                    _context.rollbackTransaction();
                    // This is a catch-all and we don't know whey we got here. Don't mess
                    // with the cert any further and keep the throwable train rollin'
                    throw th;
                }
            }

            // Refresh the completion status on the original certification to
            // update the stats, etc...  Don't do an incremental refresh.
            // Can we make this incremental?  What if items were removed from
            // entities that caused the status to change.
            
            // Do we even need this?  We only call executeCommands from refresh() which 
            // will do this same thing at the end. If incremental is safe, then maybe
            // this is unnecessary. I am too scared to remove it though, so for now 
            // just leave the entitiesToRefresh alone in case two decisions affect the same entity. 
            // TK: Boolean parameters abound. Might be high time we implement a Certficiationer configuration parameter
            //     - incrementalRefreshAllowed: self described
            //     - false: don't refresh the children
            //     - false: don't clear the entities to refresh
            //     - true:  do refresh all items witin the cert
            refreshCompletion(cert, incrementalRefreshAllowed, false, false, true);

            // Now that they've all been executed, clear the commands.
            cert.clearCommands();

            cert = saveAndDecache(cert);
        }

        return cert;
    }

    /**
     * Reattach the given CertificationCommand to the session.  Ideally, this
     * would live in the CertificationCommand, but it has lots of dependencies
     * on sailpoint.api.
     */
    private CertificationCommand reattach(CertificationCommand cmd)
        throws GeneralException {

        Identity requester = cmd.getRequester();
        if (null != requester) {
            cmd.setRequester(ObjectUtil.reattach(_context, requester));
        }

        // jsl - have to downcast to get this too...it would be nicer
        // if this were polymorphasized in the CertificationCommand model,
        // but the reattach operation requres several things in ObjectUtil
        // that aren't technically accessible to the object package, among
        // them an org.hiberante call to pass through proxies (getTheRealClass).
        // It could be worth having a reattach() method on Resolver for this.
        if (cmd instanceof CertificationCommand.BulkReassignment) {
            CertificationCommand.BulkReassignment brcmd = (CertificationCommand.BulkReassignment)cmd;
            Identity recip = brcmd.getRecipient();
            if (recip != null) {
                brcmd.setRecipient(ObjectUtil.reattach(_context, recip));
            }
        }

        // Since we don't store items on the CertificationCommand anymore, 
        // no need to attach anything more

        return cmd;
    }

    /**
     * Execute the given command on the given certification.
     */
    private void executeCommand(Certification cert, CertificationCommand cmd)
        throws GeneralException {

        if (cmd instanceof CertificationCommand.BulkReassignment) {
            bulkReassign(cert, (CertificationCommand.BulkReassignment) cmd);
        }
        else {
            throw new GeneralException(new Message(MessageKeys.UNKNOWN_CERT_CMD, cmd.toString()));
        }
    }

    /**
     * Bulk reassign multiple AbstractCertificationItems according to the given
     * command.  This will remove the requested AbstractCertificationItems from
     * the original certification, and generate and start a new certification
     * for the recipient which contains all requested items.
     *
     * @param  cert  The Certification from which the items are being delegated.
     * @param  cmd   The BulkReassignment command.
     */
    @SuppressWarnings("unchecked")
    private void bulkReassign(Certification cert,
                              CertificationCommand.BulkReassignment cmd)
        throws GeneralException {

        if(cmd.isCheckLimitReassignments()) {
            if(cert.limitCertReassignment(_context)) {
                throw new LimitReassignmentException();
            }
        }

        // Do the forwarding check early since we need it for several things.
        // Starting with 5.0 we have to pass in a stub work item so the forwrading
        // rule can make decisions based on the item type.  If the rule can
        // be sensitive to content rather than just type, then we would need to
        // repeat this for every cert item in the bulk.
        WorkItem stub = new WorkItem();
        stub.setType(WorkItem.Type.Certification);

        Workflower wf = new Workflower(_context);
        Identity origRecipient = cmd.getRecipient();
        
        if (null == origRecipient) {
            log.error("bulkReassignment CertificationCommand in cert " + cert.getId() +
                    " did not have a recipient.  Continuing without processing this CertificationCommand.");
        } else {
            Identity actualRecipient = wf.checkForward(cmd.getRecipient(), stub);
            
            // since we don't have a real certification here for the reassigned items,
            // we need to check them one by one to see if we need to run the fallback
            // forward rule or not
            // Use requester for creator, and original recipient as certifier, to mimic later rule execution
            // when the work item opens
            if (!cmd.isSelfCertificationReassignment() &&
                    isSelfCertifier(_context, actualRecipient, cmd.getItemClass(), cmd.getItemIds(), cert)) {
                Identity fallbackRecipient = getFallbackForwardIdentity(_context, actualRecipient, stub, null, cert.getType(),
                        cmd.getRequester().getName(), Util.asList(origRecipient.getName()), false, null);

                // IIQSAW-2809: If this cert is already the result of a self-cert reassignment AND the fallback identity
                // is the same as the self-cert identity, then bail before we get caught in a loop of reassignments.
                if (cert.isSelfCertificationReassignment() && (fallbackRecipient == null || fallbackRecipient.equals(actualRecipient))) {
                    log.warn("Items cannot be reassigned because at least one would result in self-certification.");
                    throw new SelfCertificationException(actualRecipient);
                }

                actualRecipient = fallbackRecipient;
            }

            cmd.setRecipient(actualRecipient);
    
            // Protect against bad requests where the item has already been reassigned
            // and is no longer a part of this cert.  See bug 
            if (!cmd.isEmpty()) {  
                Filter filter = Filter.ne(
                        (CertificationEntity.class.equals(cmd.getItemClass())) ? "certification" : "parent.certification", 
                        cert);
                SearchResultsIterator iterator = ObjectUtil.searchAcrossIds(_context, cmd.getItemClass(),
                        cmd.getItemIds(), Util.asList(filter), Util.asList("id"));

                while (iterator != null && iterator.hasNext()) {
                    Object[] item = iterator.next();
                    if (item[0] != null) {
                        if (log.isWarnEnabled())
                            log.warn("Item no longer in certification - not reassigning: " + item);
                        cmd.remove((String)item[0]);
                    }
                }
            }

            if (!cmd.isEmpty()) {
    
                // Remove any delegation requests that were already assigned to
                // the recipient.
                removeDelegations(cmd.getRecipient(), cmd.getItemClass(), cmd.getItemIds());
    
                // Remove items from the original certification.
                List<CertificationEntity> entities = removeItems(cert, cmd);
                // Save the cert since the entitiesToRefresh may have changed with removed entities.
                _context.saveObject(cert);
    
                // Look to see if there is already a reassignment certification
                // for the requested recipient.
                Certification newCert = (cmd.isSelfCertificationReassignment()) ? findSelfCertificationReassignmentCertification(cert) :
                        findReassignmentCertification(cert, cmd.getRecipient());
    
                // We found an existing reassignment certification.  Just add the
                // identities to that one.
                if (null != newCert) {
    
                    CertificationSwizzler swizzler =
                        new CertificationSwizzler(_context, this);

                    // bug 28348 - update comments in the new cert
                    newCert.setComments(cmd.getComments());

                    // bug 28348 - follow-up reassignments need to get audited too
                    // Audit the reassignment
                    this.auditor.auditReassignment(cmd, origRecipient, cert, newCert);

                    // end bug 28348

                    // Merge with existing entities in the cert.
                    swizzler.merge(entities, newCert, false, false);
    
                }
                else {
                    // Create a new certification with the identities.
                    newCert =
                        renderReassignmentCertification(cert, cmd.getRequester(),
                                                        entities, origRecipient,
                                                        cmd.getRecipient(),
                                                        cmd.getCertificationName(),
                                                        cmd.getComments(),
                                                        cmd.getDescription(),
                                                        cmd.isSelfCertificationReassignment());
    
                    // Audit the reassignment.  Do this before we start so that the
                    // date on this event happens before any auto-forward audits.
                    this.auditor.auditReassignment(cmd, origRecipient, cert, newCert);
    
                    // Do this before we call start, since start will decache...
                    // We are assuming here that if there are any workitems they'll all 
                    // contain the same lifecycle information
                    WorkItem workItemCopy = null;
                    List<WorkItem> certWorkItems = cert.getWorkItems();
                    if ( Util.size(certWorkItems) > 0  ) {
                        workItemCopy = (WorkItem)XMLObjectFactory.getInstance().cloneWithoutId(certWorkItems.get(0), _context);
                    }                

                    // Fire up the new certification                
                    // NOTE: this decaches
                    CertificationStartResults startResults = start(newCert);

                    // reattach the original cert since start() has decached
                    cert = ObjectUtil.reattach(_context, cert);
                    newCert = ObjectUtil.reattach(_context, newCert);

                    // Now that we have started (ie - transitioned to Active), copy
                    // the current phase.  We don't need to transition this cert into
                    // the current phase because the transition will have already
                    // occurred in the original certification.
                    
                    // also copy the activated date since cert definitino
                    // modification relies on the activation date for 
                    // new end phase calculations
                    if (Certification.Phase.Active.equals(cert.getPhase())) {
                        newCert.setActivated(cert.getActivated());
                    }
                    newCert.setPhase(cert.getPhase());
                    newCert.setNextPhaseTransition(cert.getNextPhaseTransition());
    
                    // bug 20889 - we need to persist these changes otherwise they will be
                    // overwritten when this certification is linked to the old certification
                    // via the WorkItem below. 
                    // These changes will be committed in the refreshCertificationGroups
                    // method below.
                    _context.saveObject(newCert);
                    
                    // Copy the lifecycle info (expiration, reminders, escalation,
                    // etc...) from the original certification.
                    if (!startResults.getWorkItemIds().isEmpty()) {
                        if (workItemCopy != null) {
                            for (String itemId : startResults.getWorkItemIds()) {
                                WorkItem item = _context.getObjectById(WorkItem.class, itemId);
                                if (item != null) {
                                    item.copyLifecycleInfo(workItemCopy);
                                }
                            }
                        }
                    }

                    // Link the new certification to the original certification.
                    // Self certification reassignments should stay top level, so only add
                    // bulk reassignments to the original cert hierarchy
                    if (!cmd.isSelfCertificationReassignment()) {
                        cert.add(newCert);
                    }
                
                    // Refresh cert group stats to reflect new cert
                    refreshCertificationGroups(newCert);
                }

                // IIQMAG-2935 If this is a self-cert and it's staged then don't send the
                // bulk reassignment notification here. It will be sent later when the cert
                // is activated in notifyStart(). Note: if a cert is a bulk reassignment it
                // can't be a self-cert and vice versa.
                if (!Phase.Staged.equals(newCert.getPhase()) && newCert.isBulkReassignment()) {
                    // Do not send any notifications for staged certs, 
                    // we will send a single one later
                    notifyBulkReassignment(newCert, cmd, entities);
                }
    
                final boolean autoReassignmentSignoffEnabled = cert.isAutoSignoffOnReassignment();
                if (autoReassignmentSignoffEnabled) {
                    refreshCompletion(cert, true, false);
                    CertificationService svc = new CertificationService(_context);
                    if (svc.isReadyForSignOff(cert) && Certification.Phase.Staged != cert.getPhase() && cert.getCompletedEntities() == 0 && cert.getCompletedItems() == 0) {
                        sign(cert, cmd.getRequester());
                    }
                }
            }
            // KG - consider decaching since we may be processing a lot of these
            // for pre-delegation reassignment.
        }
    }

    /**
     * Look for a sub-certification on the given cert that is a bulk
     * reassignment to the given recipient.  Return null if none found that
     * are still live (ie - not signed).
     */
    private Certification findReassignmentCertification(Certification cert,
                                                        Identity recipient)
        throws GeneralException {

        Certification delCert = null;

        if ((null != cert) && (null != cert.getCertifications())) {
            for (Certification current : cert.getCertifications()) {
                if (current.isBulkReassignment() &&
                    isCertificationOwner(current, recipient) &&
                    !current.hasBeenSigned()) {
                    delCert = current;
                    break;
                }
            }
        }

        return delCert;
    }

    /**
     * Look for the active self certification reassignment cert if it has already been created for this
     * certification group.
     */
    private Certification findSelfCertificationReassignmentCertification(Certification cert) throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("selfCertificationReassignment", true));
        queryOptions.add(Filter.containsAll("certificationGroups", cert.getCertificationGroups()));
        queryOptions.add(Filter.isnull("signed"));

        Iterator<Object[]> selfCertificationCerts = _context.search(Certification.class, queryOptions, "id");
        Certification selfCert = null;
        while (selfCertificationCerts.hasNext()) {
            selfCert = _context.getObjectById(Certification.class, (String)selfCertificationCerts.next()[0]);
            if (selfCert != null) {
                Util.flushIterator(selfCertificationCerts);
            }
        }

        return selfCert;
    }

    private boolean isCertificationOwner(Certification cert, Identity certifier)
        throws GeneralException {

        List<String> certifiers = cert.getCertifiers();
        return (null != certifiers) && (1 == certifiers.size()) &&
               certifier.equals(_context.getObjectByName(Identity.class, certifiers.get(0)));
    }

    /**
     * Remove the given entities or items from the certification.  If this
     * completely removes the entity or all items from an entity, it is deleted
     * completely from the cert.  This returns a list of CertificationEntities
     * with the removed items that can added to another certification.
     *
     * @param  cert   The Certification from which to remove the items.
     * @param  cmd  The command with the information about what is being removed from the cert.
     *
     * @return A list of CertificationEntities with the removed items that can
     *         added to another certification.
     */
    private List<CertificationEntity> removeItems(Certification cert,
                                                  CertificationCommand cmd)
        throws GeneralException {

        // List of new entities created for adding to another certification
        List<CertificationEntity> removed = new ArrayList<>();

        // map of cert items that belong in the same entity
        Map<CertificationEntity, List<CertificationItem>> itemsMap = new HashMap<>();

        if (!cmd.isEmpty()) {
            for (String itemId : cmd.getItemIds()) {
                if (CertificationEntity.class.equals(cmd.getItemClass())) {
                    CertificationEntity entity = _context.getObjectById(CertificationEntity.class, itemId);
                    if (entity != null) {
                        removed.add(createEntity(entity, entity.getItems()));
                        deleteEntity(cert, entity);
                    } else {
                        log.error("entity doesn't exist! - " + itemId);
                    }
                }
                else if (CertificationItem.class.equals(cmd.getItemClass())) {
                    // Here we iterate through each certification item and create new cert entities
                    CertificationItem certItem = _context.getObjectById(CertificationItem.class, itemId);
                    if (certItem != null) {
                        CertificationEntity entity = certItem.getCertificationEntity();

                        List<CertificationItem> itemsList = itemsMap.get(entity);

                        // if we already have an entity entry add it there
                        if (itemsList != null) {
                            itemsList.add(certItem);
                        }
                        // otherwise generate a new list of cert items for that entity
                        else {
                            List<CertificationItem> certificationItemList = new ArrayList<>();
                            certificationItemList.add(certItem);
                            itemsMap.put(entity, certificationItemList);
                        }

                        // cleanup the old entity
                        entity.removeItem(certItem);

                        // If there is nothing left in the entity remove it.
                        if ((null == entity.getItems()) || entity.getItems().isEmpty()) {
                            deleteEntity(cert, entity);
                        }
                        else { //mark this entity for refresh
                            entity.markForRefresh();
                        }
                    }
                    else {
                        log.error("item doesn't exist! - " + itemId);
                    }
                }
                else {
                    throw new GeneralException(new Message(Message.Type.Error,
                            MessageKeys.UNKNOWN_TYPE, cmd.getItemClass()));
                }
            }

            // iterate through itemsMap and generate the new cert entities
            for (Map.Entry<CertificationEntity, List<CertificationItem>> entry : itemsMap.entrySet()) {
                removed.add(createEntity(entry.getKey(), entry.getValue()));
            }
        }

        return removed;
    }

    /**
     * Remove the given entity from the cert, delete it, and remove the
     * delegation (if delegated).
     */
    private void deleteEntity(Certification cert, CertificationEntity entity)
        throws GeneralException {

        deleteDelegation(entity);
        cert.removeEntity(entity);
        _context.removeObject(entity);
    }

    /**
     * Create a new CertificationEntity copied from the given entity containing
     * the given items.  As a side-effect, this clears the items list.
     */
    private CertificationEntity createEntity(CertificationEntity copyFrom,
                                             List<CertificationItem> items)
        throws GeneralException {

        // Create a deep copy and clear out the things we don't want copied onto
        // the new item.
        CertificationEntity newEntity =
            (CertificationEntity) copyFrom.deepCopy((Resolver) _context);
        newEntity.setCertification(null);
        newEntity.setItems(null);
        newEntity.setId(null);
        newEntity.setDelegation(null);

        // Add the new items to this entity.
        for (CertificationItem item : items) {
            newEntity.add(item);
        }

        // As a side-effect, clear the items list.
        items.clear();

        return newEntity;
    }

    /**
     * If the given item has a delegation, remove and delete it.
     */
    private void deleteDelegation(AbstractCertificationItem item)
        throws GeneralException {

        CertificationDelegation del = item.getDelegation();
        if (null != del) {

            // Try to delete the work item.
            String workItemId = del.getWorkItem();
            if (null != workItemId) {
                WorkItem workItem = _context.getObjectById(WorkItem.class, workItemId);
                if (null != workItem) {
                    _context.removeObject(workItem);
                }
            }

            // Clear the delegation.
            item.setDelegation(null);
            item.markForRefresh();
            // Don't leave a dangling delegation object
            _context.removeObject(del);
        }
    }

    /**
     * Remove any delegations assigned to the given identity because they are
     * about to be reassigned to him in a reassignment certification.
     *
     * @param  identity  The Identity for which to remove the delegations.
     * @param  itemClass Either CertificationItem or CertificationEntity, depending on what is being reassigned.
     * @param  itemIds  IDs of items being reassigned.
     */
    private void removeDelegations(Identity identity,
                                   Class<? extends AbstractCertificationItem> itemClass,
                                   List<String> itemIds)
            throws GeneralException {

        if (!Util.isEmpty(itemIds)) {

            Filter filter = Filter.eq("delegation.ownerName", identity.getName());
            SearchResultsIterator iterator = ObjectUtil.searchAcrossIds(_context, itemClass,
                    itemIds, Util.asList(filter), Util.asList("id"));
            deleteDelegationsFromIds(itemClass, iterator);

            //If our target is entities, we need to look for child items too 
            if (CertificationEntity.class.equals(itemClass)) {
                filter = Filter.eq("delegation.ownerName", identity.getName());
                SearchResultsIterator childIterator = ObjectUtil.searchAcrossIds(_context, CertificationItem.class,
                        itemIds, Util.asList(filter), Util.asList("id"), "parent.id");
                deleteDelegationsFromIds(CertificationItem.class, childIterator);
            }
        }
    }

    private void deleteDelegationsFromIds(Class<? extends AbstractCertificationItem> itemClass, SearchResultsIterator iterator)
            throws GeneralException {
        while (iterator != null && iterator.hasNext()) {
            Object[] result = iterator.next();
            if (result != null) {
                AbstractCertificationItem item = (AbstractCertificationItem)_context.getObjectById(itemClass, (String)result[0]);
                if (item != null) {
                    deleteDelegation(item);
                }
            }
        }
    }

    /**
     * Refresh the given AbstractCertificationItem and it's children.  This is
     * a bit overloaded to return whether all children were bulk certified or
     * not.  If we start doing much more in here, we might need to start
     * maintaining some per-identity refresh state.
     *
     * This method will not unset the 'needsRefresh' flag
     *
     * @param  cert  The certification.
     * @param  item  The item to refresh.
     * @param  generateWorkItems Whether or not to generate delegation work items.
     *
     * @return True if this item and all sub-items have been bulk certified,
     *         false otherwise.
     */
    private boolean refreshItem(Certification cert, AbstractCertificationItem item, boolean generateWorkItems,
            boolean refreshAll)
        throws GeneralException
    {
        boolean bulkCertified = false;

        Iterator<CertificationItem> itemsIt = _service.getItemsToRefresh(item, refreshAll);
        if (itemsIt != null) {
            bulkCertified = true;
            while (itemsIt.hasNext()) {
                CertificationItem subItem = itemsIt.next();
                bulkCertified &= refreshItem(cert, subItem, generateWorkItems, refreshAll);
            }
        }

        CertificationAction action = item.getAction();
        if ((null != action) && action.isBulkCertified())
            bulkCertified = true;
        if (generateWorkItems) {
            revokeDelegationWorkItem(item);
            generateDelegationWorkItems(cert, item);
        }
        item.refreshActionRequired();

        return bulkCertified;
    }

    /**
     * If this item's delegation is marked to be revoked, delete the associated
     * work item and clear out the delegation.
     *
     * @param  item  The item on which to revoke the work item if the revoke
     *               flag is set.
     */
    public void revokeDelegationWorkItem(AbstractCertificationItem item)
        throws GeneralException {

        CertificationDelegation delegation = item.getDelegation();

        // If this is revoked, null out the delegation and delete the associated
        // work item.
        if ((null != delegation) && delegation.isRevoked()) {
            String workItemId = delegation.getWorkItem();
            final WorkItem workItem;

            if (null == workItemId) {
                if (log.isWarnEnabled())
                    log.warn("Trying to revoke a delegation without a work item: " + item);
                
                workItem = null;
            } else {
                workItem = _context.getObjectById(WorkItem.class, workItemId);
            }

            // Null work item is OK.  This can happen if an identity is
            // delegated, an item is delegated, the item is completed (this
            // deletes the item's work item), and the identity is revoked.
            // We still want to roll back the stuff done in the item
            // delegation.
            if (null != workItem) {
                notifyDelegationRevocation(item.getCertification(), delegation, workItem);
                _context.removeObject(workItem);

                // Audit it.
                this.auditor.auditDelegationRevocation(workItem, item);
            }

            // Delete the delegation so that we don't leave an orphan object
            _context.removeObject(delegation);

            // Now that we have revoked the work item, clear the delegation.
            item.setDelegation(null);
        }
    }

    /**
     * Refresh the delegation work items for this identity.
     */
    private void generateDelegationWorkItems(Certification cert, AbstractCertificationItem item)
        throws GeneralException {

        CertificationDelegation delegation = item.getDelegation();

        // must have a owner, and must not be marked complete
        if (delegation != null &&
            delegation.getOwnerName() != null &&
            delegation.isActive()) {

            WorkItem witem = generateDelegationWorkItem(cert, item, delegation);
            if (witem != null)
                notifyDelegation(cert, delegation, witem);
        }
    }

    /**
     * Generate a work item for an CertificationAction that represents
     * a delegation (as opposed to a mitigation or remediation.
     * The caller will further decorate the WorkItem with the specific
     * object being delgated, CertificationEntity or CertificationItem.
     */
    private WorkItem generateDelegationWorkItem(Certification cert,
                                                AbstractCertificationItem certitem,
                                                CertificationDelegation delegation)
        throws GeneralException {

        WorkItem item = null;

        // note that we will do delegate forwarding immediately
        // before the work item is saved, if you need the ownerName
        // or delegate object for anything else until then then you will
        // have to do the forwarding up here
        String ownerName = delegation.getOwnerName();
        Identity delegate = _context.getObjectByName(Identity.class, ownerName);
        if (delegate == null) {
            // hmm, should we abort the entire refresh
            _errorHandler.addMessage(new Message(Message.Type.Error,
                    MessageKeys.INVALID_DELEGATE_IDENTITY, ownerName));
        }
        else {
            String itemId = delegation.getWorkItem();
            if (itemId == null)
                item = new WorkItem();
            else {
                item = _context.getObjectById(WorkItem.class, itemId);
                if (item == null) {
                    // A dangling reference.  This can happen after a work item
                    // is assimilated (ie - no longer active).  We shouldn't get
                    // here because this method assumes that the delegation is
                    // active.  Just log a warning and bail.
                    if (log.isWarnEnabled())
                        log.warn("No delegation work item being generated because of " +
                                 "dangling work item: " + itemId);
                    return null;
                }
                else {
                    Identity owner = item.getOwner();
                    if (owner == null) {
                        // this is really not supposed to happen
                        // reuse the WorkItem and assign the new owner
                    }
                    else if (owner.getName().equals(delegate.getName())) {
                        // already generated this - return null.
                        item = null;
                    }
                    else {
                        // delete the old one and make a new one
                        item.setOwner(null);
                        _context.removeObject(item);
                        item = new WorkItem();
                    }
                }
            }

            if (item != null) {
                item.setType(WorkItem.Type.Delegation);
                item.setHandler(Certificationer.class);

                // Owner is not always the requester.  Specifically, if an
                // identity is delegated, the delegate can further delegate an
                // item out of the delegation.  Try to get the requester from
                // the actor field first.
                Identity requester = delegation.getActor(_context);
                item.setRequester(requester);
                item.setOwner(delegate);
                item.setDescription(delegation.getDescription());
                item.addComment(delegation.getComments(), requester);

                // Pointers back to the cert and cert item.
                item.setCertification(cert);
                item.setAbstractCertificationItem(certitem);

                // Setup notifications if we should.
                cert.setupWorkItemNotifications(_context, item);

                // We need to save the ID of the work item on the delegation
                // before opening it.  This is used if the work item is auto
                // forwarded to update the ownerName property.
                _context.saveObject(item);
                delegation.setWorkItem(item.getId());
                _context.saveObject(delegation);
                // Audit the delegation.  Do this before the item is opened
                // so the delegation audit event occurs before the auto-forward
                // audit event (if there is one).
                this.auditor.auditDelegation(item, certitem);

                // allow it to be forwarded
                // note that this will commit!
                Workflower wf = new Workflower(_context);
                wf.open(item);

                // TODO: Think more about how we decide to require review
                // At minimum we probably want a global configuration option,
                // but it could also be different for each Identity.
                // Lacking a global option, we have to store a flag on each
                // CertificationDelegation because when the WorkItem is assimilated,
                // the Certificationer refresh happens as a side effect and the
                // application doesn't have a chance to set the _delegationReview flag.
                if (isDelegationReview(cert))
                    delegation.setReviewRequired(true);
            }
        }

        return item;
    }

    /**
     * Send batched remediation notifications and update the remediation stats
     * on the given certification.
     */
    private void postRemediate(Certification cert) throws GeneralException {

        // Launch the batched remediation notifications.
        RemediationManager remedMgr = new RemediationManager(_context);
        remedMgr.flush(cert);

        // Roll-up the kicked off remediations statististic.
        _statCounter.updateRemediationsKickedOff(cert, true);
    }

    public int updateRemediationsKickedOff(Certification cert, boolean flush) throws GeneralException{
        return _statCounter.updateRemediationsKickedOff(cert, flush);
    }


    public int updateRemediationsCompleted(Certification cert, boolean flush)  throws GeneralException{
        return _statCounter.updateRemediationsCompleted(cert, flush);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Completion Status
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Simple structure that holds changes to certification statistics.  The
     * differences here will be applied to the certification's statistics upon
     * refresh.  The properties are public so the caller can manipulate them.
     */
    private static class StatDiffs {

        public int totalDiff;
        public int completedDiff;
        public int delegatedDiff;

        public StatDiffs() {}
    }

    /**
     * Refresh the status on the items and entities in the certification and
     * roll up the statistics onto the certification.
     */
    private boolean refreshCompletion(Certification cert, boolean incrementalRefresh,
                              boolean refreshChildren) throws GeneralException {
         return refreshCompletion(cert, incrementalRefresh, refreshChildren, true, true);
    }
    
    /**
     * Refresh the status on the items and entities in the certification and
     * roll up the statistics onto the certification.
     * 
     * @param cert Certification to refresh
     * @param incrementalRefresh Flag to indicate refreshing only Identities in
     *          cert's 'identitiesToRefresh' list
     * @param refreshChildren Flag to indicate child certifications should also be
     *          refreshed
     * @param clearEntitiesToRefresh Flag to indicate if entitiesToRefresh list is
     *          cleared after refresh
     * @param refreshAllItems Flag to indicate if all related items should be refreshed
     *          or only related items with the 'needsRefresh' flag enabled
     * @return True if the certficiation is complete
     * @throws GeneralException
     */
    private boolean refreshCompletion(Certification cert, boolean incrementalRefresh,
                              boolean refreshChildren, boolean clearEntitiesToRefresh, 
                              boolean refreshAllItems)
        throws GeneralException {

        boolean wasComplete = cert.isComplete();

        // Refresh all of the pertinent entities.
        Meter.enter(58, "Certificationer: Refresh entity status");
        Iterator<CertificationEntity> it = iterateIdentities(cert, incrementalRefresh);
        while (it.hasNext()) {
            refreshEntityStatus(cert, it.next(), refreshAllItems);
        }
        Meter.exit(58);

        // I know that we just incrementally updated the stats, but this seems
        // to be getting off sometimes (see bug 3543).  Run some queries to get
        // the actual counts.
        this.updateCertificationStatistics(cert);

        // Recurse on the children.  Child certs will only get refreshed for
        // completion if:
        //  1) Complete hierarchy is enabled and the child is a non-reassignment.
        //  2) Require reassignment completion is enabled and the child is
        //     a reassignment.
        Attributes<String,Object> attrs = _context.getConfiguration().getAttributes();
        int incompleteChildren = 0;
        if (cert.isCompleteHierarchy() ||
            (cert.isRequireReassignmentCompletion(attrs) && cert.hasBulkReassignments())) {

            //Meter.enter(56, "Certificationer: Refresh children");
            List<CertInfo> childrenInfos = getChildCertInfos(cert);
            for (CertInfo childInfo : childrenInfos) {
                if ((cert.isCompleteHierarchy() && !childInfo.isBulkReassignment()) || 
                        (childInfo.calculateRequireReassignmentCompletion(attrs) && childInfo.isBulkReassignment())) {

                    // seems that refreshChildren below is never set to true anywhere
                    if (refreshChildren) {
                        refreshCompletion(_context.getObjectById(Certification.class, childInfo.getId()), incrementalRefresh, refreshChildren);
                    }

                    if (!childInfo.isComplete()) {
                        incompleteChildren++;
                    }
                }
            }
            //Meter.exit(56);
        }

        boolean isComplete =
            (cert.getTotalEntities() == cert.getCompletedEntities()) &&
            (incompleteChildren == 0);
        if (isComplete != cert.isComplete()) {
            cert.setComplete(isComplete);
        }

        if (incrementalRefresh && clearEntitiesToRefresh) {
            cert.clearEntitiesToRefresh();
        }

        // The completion status on this certification changed, roll it up to
        // the parent certification.
        //Only Roll it up to parent if Subordinate Signoff OR if the cert is a bulk reassign and we require child completion before parent
        //If we do not require child completion first, the parent may be immutable by the time child completes
        if ((cert.isCompleteHierarchy() || (cert.isBulkReassignment() && cert.isRequireReassignmentCompletion(attrs))) &&
            (wasComplete != cert.isComplete()) &&
            (null != cert.getParent())) {

            // we've had some odd hibernate errors, so just to be safe...
            Certification parent = cert.getParent();
            parent = ObjectUtil.reattach(_context, parent);
            
            // Only do an incremental refresh on the parent since none of the
            // identities changed - just the child certifications.
            refreshCompletion(parent, true, false);
        }

        return cert.isComplete();
    }
    
    private static class CertInfo {
        
        private String id;
        private boolean bulkReassignment;
        private boolean complete;
        private Attributes<String, Object> attributes;

        public String getId() {
            return this.id;
        }
        
        public void setId(String val) {
            this.id = val;
        }
        
        public Attributes<String, Object> getAttributes() {
            return this.attributes;
        }
        
        public void setAttributes(Attributes<String, Object> val) {
            this.attributes = val;
        }
        
        public boolean isBulkReassignment() {
            return this.bulkReassignment;
        }

        public void setBulkReassignment(boolean val) {
            this.bulkReassignment = val;
        }

        public boolean isComplete() {
            return this.complete;
        }

        public void setComplete(boolean val) {
            this.complete = val;
        }

        public boolean calculateRequireReassignmentCompletion(Attributes<String, Object> dflts) {
            Certification dummy = new Certification();
            dummy.setAttributes(getAttributes());
            return dummy.isRequireReassignmentCompletion(dflts);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<CertInfo> getChildCertInfos(Certification cert) throws GeneralException {
        
        List<CertInfo> children = new ArrayList<Certificationer.CertInfo>();
        
        Filter filter = Filter.eq("parent.id", cert.getId());
        QueryOptions options = new QueryOptions(filter);
        
        Iterator<Object[]> rowsIterator = _context.search(Certification.class, options, Arrays.asList("id", "bulkReassignment", "attributes", "complete"));
        while (rowsIterator.hasNext()) {
            Object[] row = rowsIterator.next();
            
            CertInfo child = new CertInfo();
            
            child.setId((String) row[0]);
            child.setBulkReassignment(Util.nullsafeBoolean((Boolean) row[1]));
            child.setAttributes((Attributes<String, Object>) row[2]);
            child.setComplete(Util.nullsafeBoolean((Boolean) row[3]));
            
            children.add(child);
        }
        
        return children;
    }

    CertificationEntitlizer _entitlizer = null;
    
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
    
    private void refreshEntityStatus(Certification cert, CertificationEntity entity)
        throws GeneralException {
        refreshEntityStatus(cert, entity, true);
    }
    
    /**
     * Refresh the summary status of the given CertificationEntity and its
     * items, and accumulate the status into the given stats (if given).
     * 
     * This will clear the 'refreshNeeded' flag
     */
    private void refreshEntityStatus(Certification cert, CertificationEntity entity,
                    boolean refreshAllItems)
        throws GeneralException {

        if (entity == null) {
            // callers might not be keeping up with the times and give us junk
            return;
        }
        // Refresh all of the items first.
        Iterator<CertificationItem> itemsIt = _service.getItemsToRefresh(entity, refreshAllItems);
        while (itemsIt != null && itemsIt.hasNext()) {
            refreshItemStatus(cert, itemsIt.next());
        } 
        // djs:
        // Since we've just loaded all of the current items, this is likely
        // the most oportune time to go over the items again and adorn 
        // the entitlements.  This avoids having to reload, the snapshot,
        // permission and cert item again later.
        //
        promoteEntityEntitlements(cert, entity);            

        // Refresh the summary status on the entity.

        _service.refreshSummaryStatus(entity, null, refreshAllItems);

        // Run the completion rule to allow the completion status to be tweaked.
        runCompletionRule(entity, cert, getEntityCompletionRule(cert));
        _context.saveObject(entity);
    }

    private void promoteEntityEntitlements(Certification cert, CertificationEntity entity) 
        throws GeneralException {
        
        Meter.enter(57,"Certificationer: Entitlement adornment.");  
        getEntitlizer(cert).setPending(entity);
        Meter.enter(57,"Certificationer: Entitlement adornment.");  
    }
    
    /**
     * An item is complete if it has been given a non-null status (Approve,
     * Mitigate, etc.).  This will also call through to the certification item
     * completion rule (if configured) to determine whether the item is really
     * complete.
     */
    private boolean refreshItemStatus(Certification cert, CertificationItem item)
        throws GeneralException {

        // In some cases, we want to auto-approve an item.  This is really a
        // hack to cope with a malformed data model, but I'm afraid to remove it
        // now.
        autoApproveItem(item);

        // Refresh the summary status and completion state of this item.
        item.refreshSummaryStatus();

        // Run the completion rule to allow the completion status to be tweaked.
        runCompletionRule(item, cert, _itemCompletionRule);
        item.setNeedsRefresh(false);
        _context.saveObject(item);
        return item.isComplete();
    }

    /**
     * Run the given completion rule (if not null) on the given item to allow
     * tweaking the summary status.
     */
    @SuppressWarnings("unchecked")
    private void runCompletionRule(AbstractCertificationItem item,
                                   Certification cert, Rule completionRule)
        throws GeneralException {

        // Allow the completionRule to override something that we deem complete
        // if it is configured.  Note that this is only run if we determined an
        // item is complete.
        if (item.isComplete() && (null != completionRule)) {
            // the completion rule originally returned a Boolean,
            // whereas the new version expects a list.  This section
            // preserves backwards compatibility.
            Object result = executeCompletionRule(item, cert, completionRule);
            if ((result instanceof Boolean) && !isCompletionRuleResultComplete(result)) {
                item.refreshSummaryStatus(false);

                // give a generic error msg since we don't have enough
                // info to get into specifics
                addMessage(new Message(Message.Type.Warn,
                    MessageKeys.CUSTOM_CERT_DATA_REQUIRED));
            }

            if ((result instanceof List) && !isCompletionRuleResultComplete(result)) {
                List<Object> messages = (List<Object>)result;
                item.refreshSummaryStatus(false);

                // bean shell can't handle the Java5 parts of the Message object, so
                // the completion rule can't return Message objects.  It can return
                // either a List of Strings for simple no-arg Messages, or a List of
                // Lists for variable-arg Messages.  In the latter case, the first
                // element of the member List is the message/key, and the rest of the
                // elements are the variable args.
                for (Object msg : messages) {
                    if (msg instanceof String) {
                        addMessage(new Message(Message.Type.Warn, (String)msg));
                    } else if (msg instanceof List) {
                        List<String> msgList = (List<String>)msg;
                        String msgStr = msgList.remove(0);
                        addMessage(new Message(Message.Type.Warn, msgStr,
                            msgList.toArray(new Object[0])));
                    } else {
                        // throwing an exception here creates a weird
                        // LazyInitializationException from Hibernate that
                        // hides what really happened
                        addMessage(new Message(Message.Type.Error,
                            MessageKeys.COMPLETION_RULE_INVALID_ERROR_MSG));
                    }
                }
            }
        }
    }

    /**
     * Run the given completion rule for the given item and return the result.
     */
    private Object executeCompletionRule(AbstractCertificationItem item,
                                         Certification cert, Rule rule)
        throws GeneralException {

        Map<String,Object> params = new HashMap<String,Object>();
        params.put("certification", cert);
        params.put("item", item);
        // Put entity in the map in case someone uses the wrong name.
        params.put("entity", item);
        params.put("state", _state);

        return _context.runRule(rule, params);
    }

    /**
     * Return whether the completion rule result is logically "complete".
     */
    @SuppressWarnings("rawtypes")
    private boolean isCompletionRuleResultComplete(Object result) {

        boolean complete = true;

        if (result instanceof Boolean) {
            complete = (Boolean) result;
        }
        else if (result instanceof List) {
            complete = ((List) result).isEmpty();
        }

        return complete;
    }

    /**
     * Return whether the given item is complete according to the item or entity
     * completion rule (depending on the type of item).  If there is not a rule
     * for the item type, this returns true.  This only returns the result of
     * running the rule, which may return true even if the item doesn't have a
     * decision.  It is the responsibility of the caller to check this.
     *
     * @param  item  The AbstractCertificationItem for which to run the rule.
     *
     * @return True if the item is complete according to the appropriate rule
     *         or there is no rule; false otherwise.
     */
    public boolean isCompletePerCompletionRule(AbstractCertificationItem item)
        throws GeneralException {

        boolean complete = true;

        Rule rule = getCompletionRule(item);
        if (null != rule) {
            Object result =
                executeCompletionRule(item, item.getCertification(), rule);
            complete = isCompletionRuleResultComplete(result);
        }

        return complete;
    }

    /**
     * Hack - if an item is malformed we want to auto approve it.
     */
    private void autoApproveItem(CertificationItem item) {
        CertificationAction action = item.getAction();

        // This is a leaf item, it must have an action set.
        // We seem to be getting some phantom Exceptions
        // certifications with no children on occasion.  Try
        // to detect malformed items and mark them complete.
        if (action == null) {
            boolean autoApprove = false;

            switch (item.getType()) {
            case Bundle: {
                // check for missing bundle, this is more serious
                // it indicates a construction error, or corruption
                // during the update phase
                autoApprove = (item.getBundle() == null);
            }
            break;
            case AccountGroupMembership: case Exception: case DataOwner: {
                EntitlementSnapshot ents = item.getExceptionEntitlements();
                if (ents == null) {
                    // no entitlements
                    autoApprove = true;
                }
            }
            case Account:
            break;
            case PolicyViolation: {
                // Shouldn't happen, but auto-approve just in case.
                autoApprove = (item.getPolicyViolation() == null);
                break;
            }
            case BusinessRoleGrantedScope:
            case BusinessRoleGrantedCapability:
            case BusinessRolePermit:
            case BusinessRoleRequirement:
            case BusinessRoleProfile:
            case BusinessRoleHierarchy: {
                autoApprove = (item.getTargetId() == null);
                break;
            }

            default: {
                // Some unknown type - shouldn't see these yet
                // tqm: I would seriously consider just throwing an
                // exception here. Please.
                autoApprove = true;
            }
            break;
            }

            if (autoApprove) {
                try {
                    item.approve(_context, null, null);
                }
                catch (GeneralException e) {
                    _errorHandler.addMessage(e.getMessageInstance());
                }
            }
        }
    }

    /**
     * Refresh the given "incremental completion refresh" statistics based on
     * the given entity's current status and its previous status.
     */
    @SuppressWarnings("unused")
    private StatDiffs refreshStats(AbstractCertificationItem entity,
                                   AbstractCertificationItem.Status prevStatus,
                                   boolean incremental) {

        StatDiffs stats = new StatDiffs();
        AbstractCertificationItem.Status newStatus = entity.getSummaryStatus();

        assert (!incremental || (null != prevStatus)) :
            "Previous status should already be calculated on an incremental refresh.";
        assert (null != newStatus) : "Summary status should have been set: " + entity;

        // If this is a full refresh, don't bother with the previous status.
        if (!incremental) {
            stats.totalDiff++;

            if (newStatus.isComplete()) {
                stats.completedDiff++;
            }
            else if (AbstractCertificationItem.Status.Delegated.equals(newStatus)) {
                stats.delegatedDiff++;
            }
        }
        else if ((null == prevStatus) || !prevStatus.equals(newStatus)) {

            // Incremental refresh - only need to tweak the stats if the status
            // changed.

            // entity changes from challenged -> open when a challenge
            // is accepted.  Decrement the complete count.
            if (AbstractCertificationItem.Status.Challenged.equals(prevStatus) &&
                AbstractCertificationItem.Status.Open.equals(newStatus)) {
                stats.completedDiff--;
            }
            // Don't change the counts if we're going to or coming from
            // Challenged - this doesn't currently affect the counts
            // (unless the item goes from challenged -> open).
            else if (!AbstractCertificationItem.Status.Challenged.equals(prevStatus) &&
                     !AbstractCertificationItem.Status.Challenged.equals(newStatus)) {

                // Changed from complete -> incomplete.
                if (AbstractCertificationItem.Status.Complete.equals(prevStatus)) {
                    stats.completedDiff--;
                }

                // Changed from delegated -> something else.
                if (AbstractCertificationItem.Status.Delegated.equals(prevStatus)) {
                    stats.delegatedDiff--;
                }

                // Changed from something -> complete.
                if (AbstractCertificationItem.Status.Complete.equals(newStatus)) {
                    stats.completedDiff++;
                }

                // Changed from something -> delegated.
                if (AbstractCertificationItem.Status.Delegated.equals(newStatus)) {
                    stats.delegatedDiff++;
                }
            }
        }

        return stats;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Assimilate
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A context for loading and saving Certification information for work item
     * assimilation.
     */
    private static class AssimilationContext {

        private Certification certification;
        private CertificationEntity certificationEntity;
        private CertificationItem certificationItem;

        /**
         * Constructor.
         *
         * @param  context         The SailPointContext to use.
         * @param  certId          The ID of the Certification.
         * @param  certEntityId  The ID of the CertificationEntity (optional).
         * @param  certItemId      The ID of the CertificationItem (optional).
         */
        public AssimilationContext(SailPointContext context, String certId,
                                   String certEntityId, String certItemId)
            throws GeneralException {

            this.certification = (null != certId)
                ? context.getObjectById(Certification.class, certId) : null;
            this.certificationEntity = (null != certEntityId)
                ? context.getObjectById(CertificationEntity.class, certEntityId) : null;
            this.certificationItem = (null != certItemId)
                ? context.getObjectById(CertificationItem.class, certItemId) : null;
        }

        public Certification getCertification() {
            return certification;
        }

        public CertificationEntity getCertificationEntity() {
            return certificationEntity;
        }

        public CertificationItem getCertificationItem() {
            return certificationItem;
        }

        public CertificationItem getCertificationItem(SailPointContext ctx,
                                                      String itemId)
            throws GeneralException {

            return ctx.getObjectById(CertificationItem.class, itemId);
        }

        /**
         * Save the Certification.
         *
         * @param  context  The SailPointContext to use for saving.
         */
        public void save(SailPointContext context) throws GeneralException {
            if (null != this.certification) {
                context.saveObject(this.certification);
            }
        }
    }

    /**
     * Assimilate a change to a single remediation work item into the
     * certification from which it was generated.  Note that remediation items
     * can be assimilated in bulk with assimilate(WorkItem).
     *
     * @param  item  The RemediationItem to assimilate.
     */
    public void assimilate(RemediationItem item) throws GeneralException {

        assert (null != item.getWorkItem()) : "Remediation not associated to work item.";

        PersistenceOptionsUtil forcer = new PersistenceOptionsUtil();
        try {
            forcer.configureImmutableOption(_context);
            String certItemId = item.getCertificationItem();
            String certId = item.getWorkItem().getCertification();

            // If certId is null the workitem is not part of a certification
            // Could be an item from the policy violation viewer for example
            if (certId == null) {
                return;
            }

            AssimilationContext ctx = new AssimilationContext(_context, certId,
                    null, certItemId);

            CertificationItem certItem = ctx.getCertificationItem();

            // Previously, continous certs could remove items without cleaning
            // up
            // the remediations. Check for null just in case.
            if (null != certItem) {
                assimilateRemediation(certItem, item);
                ctx.save(_context);
            }

            // Note that this is called as a side effect of calling
            // SailPointContext.saveObject so it isn't allowed to commit.
            // Can the ui layer just save changes to a RemediationItem
            // without knowing that it is supposed to be assimilated?
            // If not, then we should make it call Certificationer.assimilate()
            // rather than relying on the checkin side effect - jsl
        } finally {
            forcer.restoreImmutableOption(_context);
        }
    }

    /**
     * Assimilate a completed delegation or remediation Workitem back
     * into the Certification.
     *
     * This will be called indirectly by Workflower via the WorkItemHandler
     * interface.  We are allowed to commit.
     */
    public void assimilate(WorkItem item)
        throws GeneralException {

        PersistenceOptionsUtil forcer = new PersistenceOptionsUtil();
        try {             
            forcer.configureImmutableOption(_context);
            String certId = item.getCertification();
            String certEntityId = item.getCertificationEntity();
            String certItemId = item.getCertificationItem();

            AssimilationContext ctx =
                    new AssimilationContext(_context, certId, certEntityId, certItemId);
            Certification cert = ctx.getCertification();
            CertificationItem certitem = ctx.getCertificationItem();

            switch (item.getType()) {

                case Certification:
                    // We used to delete the work item if the certification is
                    // complete.  This is now explicitly done through finish(), so
                    // we won't do anything.
                    break;

                case Remediation:
                    // A Remediation work item - assimilate an remediation items that
                    // are complete but haven't been assimilated yet.
                    List<RemediationItem> items = item.getRemediationItems();
                    if (null != items) {
                        for (RemediationItem remediation : items) {
                            if (remediation == null) {
                                continue;
                            }
                            CertificationItem remedCertItem =
                                    ctx.getCertificationItem(_context, remediation.getCertificationItem());

                            // Previously, continous certs could remove items without
                            // cleaning up the remediations.  Check for null just in case.
                            if (null != remedCertItem) {
                                assimilateRemediation(remedCertItem, remediation);
                            }
                            else {
                                // Probably archived, just warn.
                                if (log.isWarnEnabled())
                                    log.warn("CertificationItem for remediation assimilation not found - " +
                                            remediation.getCertificationItem() + ". Not assimilating.");
                            }
                            
                            //IIQSAW-2251 -- create ProvisioningRequest for manual workitem, 
                            //to prevent triggering of native change. 
                            //This keeps in-sync with change of IIQSAW-1252 
                            // -- creating ProvisioningRequest for completion of manual workitem generated by lcm request. 
                            ProvisioningPlan plan = remediation.getRemediationDetails();
                            String identityName = remediation.getRemediationIdentity();
                            PlanUtil.createProvisioningRequest(_context, plan, identityName);
                        }
                    }

                    // Save the certification.
                    ctx.save(_context);

                    // If the remediation item has a completion state, delete it.
                    if (null != item.getState()) {
                        // TODO: verify that all remediation items are complete?
                        archiveWorkItem(item);
                        _context.removeObject(item);
                    }
                    break;

                case Delegation:
                    // Only assimilate delegations if there is a completion state.
                    if (null != item.getState()) {
                        // The state will either be Finished, Expired, or Returned.
                        // Locate the associated object and copy the completion status
                        // into the appropriate CertificationAction.

                        if (null != cert) {
                            CertificationEntity certificationEntity = ctx.getCertificationEntity();
                            if (certitem != null)
                                assimilateDelegation(item, certitem, cert);

                            else if (certificationEntity != null)
                                assimilateDelegation(item, certificationEntity, cert);

                            refresh(cert);
                            refreshCompletion(cert, true, false);

                            // Save the certification.
                            ctx.save(_context);
                        }

                        // in call cases, we no longer need this work item
                        item = ObjectUtil.reattach(_context, item);
                        archiveWorkItem(item);
                        _context.removeObject(item);
                    }
                    break;

                case Challenge:
                    // A challenge work item - assimilate when the item is completed
                    // (either finished or expired).
                    if (null != item.getState()) {

                        if ((null != cert) && (null != certitem)) {
                            // Previously, continous certs could remove items without
                            // cleaning up the challenges.  Check for null just in case.
                            assimilateChallenge(cert, item, certitem);

                            // Refresh completion to reflect challenged
                            refresh(cert);
                        }

                        // Delete the work item.
                        archiveWorkItem(item);
                        _context.removeObject(item);
                    }

                    // Save the certification.
                    ctx.save(_context);

                    break;

                default:
                    // In this case we may have been called incorrectly
                    // on a non-certification work item.  Could error,
                    // but just tolerate in case the work item handler
                    // registration is broken.
                    log.warn("Non certification work item passed to Certificationer");
                    break;
            }

            _context.commitTransaction();
        
        } finally {
            forcer.restoreImmutableOption(_context);
        }
    }

    /**
     * Called by assimilate() after we've processed the item and decided
     * it needs to be deletd.  If archiving is enabled for this item type,
     * create an archive before deleting it.
     */
    private void archiveWorkItem(WorkItem item)
        throws GeneralException {

        Workflower wf = new Workflower(_context);
        wf.archiveIfNecessary(item);
    }

    /**
     * Assimilate delegation work items back into the certification.
     */
    private void assimilateDelegation(WorkItem workItem,
                                      AbstractCertificationItem certItem,
                                      Certification cert)
        throws GeneralException {

        String itemid = workItem.getId();
        CertificationDelegation delegation = certItem.getDelegation();

        if (delegation != null && itemid.equals(delegation.getWorkItem())) {
            // Rollback anything that happened during the delegation if this is
            // being returned.
            if (WorkItem.State.Returned.equals(workItem.getState()) ||
                WorkItem.State.Expired.equals(workItem.getState())) {

                // Do rejected work items that have been forwarded go back
                // to the original requester, or do they just pop one level up
                // the forwarding stack??  I think for now that they go back to
                // the original requester.

                certItem.rollbackChanges(itemid);
            }
            else if (WorkItem.State.Finished.equals(workItem.getState())) {
                resetContextOnDelegatedSubitems(certItem, cert);
            }

            certItem.markForRefresh();
            assimilateDelegation(workItem, delegation);

            this.auditor.auditDelegationCompletion(workItem, certItem);
        }

        else {
            // The work item references an CertificationItem that
            // doesn't know about it.  Either the work item or
            // the certification item have been corrupted.
            log.error("Disassociated Work Item!");

            // should we move these to a special place for inspection?
        }
    }

    /**
     * This item is about to go away.  If any subitems are delegated, change
     * the context information so that they are now editable by the
     * certification owner.
     *
     * @param  entity  The certification entity for which the work item is
     *                   being assimilated.
     * @param  cert      The owning certification report.
     */
    private void resetContextOnDelegatedSubitems(AbstractCertificationItem entity,
                                                 Certification cert)
        throws GeneralException {

        if (null != entity.getItems()) {
            for (CertificationItem subitem : entity.getItems()) {
                CertificationDelegation del = subitem.getDelegation();
                if ((null != del) && del.isActive()) {
                    // Setting the actor and acting work item to null allow the
                    // certifier(s) to edit this.
                    del.setActor(null);
                    del.setActingWorkItem(null);
                }
            }
        }
    }

    /**
     * Assimilate the work item information back into its target delegation.
     * For now assuming that all we have to transfer are the
     * completion state and comments.  Might have other interesting
     * data on the WorkItem, but really the app should have been putting
     * extra comments and stuff directly in the Certification model.
     */
    private void assimilateDelegation(WorkItem item,
                                      CertificationDelegation delegation)
        throws GeneralException {

        delegation.setCompletionState(item.getState());
        delegation.setCompletionComments(item.getCompletionComments());

        // KPG - Keep the work item reference around so that we can later tell
        // which actions occurred within this delegation.
        // the non-null completion state will prevent us from
        // generating another one
        //delegation.setWorkItem((String)null);

        // TODO: Eventually might want to have policies for what
        // happens if the item was Expired.  For now, just leave
        // the certification item open and display it in the UI.

    }

    /**
     * Assimilate the given remediation item back into the certification item
     * if it is complete and has not yet been assimilated.
     */
    private void assimilateRemediation(CertificationItem certItem,
                                       RemediationItem remediationItem)
        throws GeneralException {

        CertificationAction action = certItem.getAction();

        // This can be null if this is from a continuous cert that has
        // transitioned back into the "certification required" phase and cleared
        // the action.
        if (null != action) {

            // Sanity check - we should be dealing with a remediation action.
            assert (CertificationAction.Status.Remediated.equals(action.getStatus()))
                : "Expected a remediated certification item.";

            // Assimilate if this remediation is complete and has not yet been
            // assimilated.
            if (remediationItem.isComplete() && !remediationItem.isAssimilated()) {
                action.setCompletionState(WorkItem.State.Finished);
                action.setCompletionComments(remediationItem.getCompletionComments());

                // The owner of the remediationItem is the person who actually
                // completed the remediationItem.
                Identity owner = remediationItem.getOwner();
                if (null != owner) {
                    action.setCompletionUser(owner.getName());
                }

                // Tell the remediation item that it has been assimilated so we don't
                // try to assimilate again.
                remediationItem.assimilate();
            }
        }
    }

    /**
     * Assimilate the challenge work item back into the certification.  This
     * will set the CertificationEntity summary status to Challenged if the item
     * was finished (ie - not expired) and the item was challenged.
     *
     * @param  cert      The Certification the challenge was on.
     * @param  workItem  The WorkItem for the challenge request.
     * @param  certItem  The CertificationItem possibly being challenged.
     *
     * @return If the item requires its completion status to be refreshed.
     */
    private boolean assimilateChallenge(Certification cert, WorkItem workItem,
                                        CertificationItem certItem) {

        boolean challenged = false;

        CertificationChallenge challenge = certItem.getChallenge();
        assert (null != challenge) : "Expected a challenge";

        // Assimlate the completion state regardless of the outcome.
        WorkItem.State state = workItem.getState();
        challenge.setCompletionState(state);

        if (WorkItem.State.Finished.equals(state)) {

            if (challenge.isChallenged()) {
                // TODO: notify certifier of challenge request?

                // The completionComments will have already been set with
                // CertificationChallenge.challenge().

                challenged = true;
            }
        }

        // Always mark for a refresh - this is only called when a challenge work
        // item is about to go away (either accepted or challenged by the end
        // user).  We may just need to update the status if the item is
        // challenged, or we may need to transition a rolling certification
        // phase during the refresh.
        certItem.markForRefresh();

        return challenged;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Sign Off
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Sign off on all certification decisions.  This stores the sign off
     * information on the certification.  Later a task will run that will
     * verify that the certification was completed, etc...
     * 
     * @param checkLocked If this is set we will make sure to only allow signing if 
     *        there is no lock on the cert. Typically we will check for the locked when
     *        the call is from the UI and won't check if it is called from elsewhere because
     *        we are the ones holding the lock when it is called from an non-ui thread. If locked,
     *        will throw an {@link ObjectAlreadyLockedException}
     *
     * Returns a non-null error list (or throws an exception) if
     * the certification could not be finished.
     */
    public List<Message> sign(Certification cert, Identity certifier, boolean checkLocked, String authId, String pass, Locale locale)
            throws GeneralException, ExpiredPasswordException {

        _errorHandler.clear();

        try {
            // Check the completion status on the certification and throw if
            // the certification is not marked as completed.  Later, we'll
            // perform a full refreshCompletion() in the background that will
            // rollback the signing if the certification is not really complete.
            if (!cert.isComplete()) {
                _errorHandler.addMessage(new Message(Message.Type.Error,
                        MessageKeys.CERT_REQUIRES_COMPLETE_BEFORE_SIGN));
            } else if (checkLocked && ObjectUtil.isLockedById(_context, Certification.class, cert.getId())) {
                _errorHandler.addMessage(new Message(Message.Type.Error, MessageKeys.CERT_LOCKED_SAVE_FAILURE));
                throw new ObjectAlreadyLockedException(new Message(MessageKeys.CERT_LOCKED_SAVE_FAILURE));
            } else {
                if (notary == null) {
                    notary = new Notary(_context, locale);
                }
                notary.setLocale(locale); // Always set this to ensure we have a current Locale
                
                if (notary.getSignatureMeaning(cert) != null) {
                    notary.sign(cert, authId, pass);
                    cert.setElectronicallySigned(true);
                }
                else {
                    // Add an entry to the sign off history.
                    cert.addSignOffHistory(certifier);
                }
                // If there were no approvals, set the signed date.
                if (!processSignOffApproverRule(cert, certifier)) {

                    cert.setSigned(new Date());
                    boolean setImmutable = false;
                    //This is the last signature, we need to lock the cert down if it was electronically signed
                    for(SignOffHistory sho : cert.getSignOffHistory()) {
                        if(sho.isElectronicSign()) {
                            setImmutable = true;
                            break;
                        }
                    }
                    deleteWorkItems(cert);
                    if (setImmutable) {
                        //Wait to setImmutable since deleteWorkItems can commit then dirty the cert -rap
                        cert.setImmutable(true);
                    }
                    // jsl - what else interesting should we include
                    // in the log?
                    Auditor.log(AuditEvent.ActionSignoff, cert.getName());
                }
            }

            _context.commitTransaction();
        }
        catch (AuthenticationFailureException ae) {
            _context.rollbackTransaction();
            _errorHandler.addMessage(new Message(Message.Type.Error, MessageKeys.ESIG_POPUP_AUTH_FAILURE));
        }
        catch (GeneralException | ExpiredPasswordException e) {
            _context.rollbackTransaction();
            throw e;
        }

        return _errorHandler.getMessagesByType(Message.Type.Error);
    }
    
    /**
     * This is used for all signing of certifications done by the system 
     */
    public List<Message> sign(Certification cert, Identity certifier)
        throws GeneralException {

        _errorHandler.clear();

        try {
            // Check the completion status on the certification and throw if
            // the certification is not marked as completed.  Later, we'll
            // perform a full refreshCompletion() in the background that will
            // rollback the signing if the certification is not really complete.
            if (!cert.isComplete()) {
                _errorHandler.addMessage(new Message(Message.Type.Error,
                        MessageKeys.CERT_REQUIRES_COMPLETE_BEFORE_SIGN));
            } else {
                    // Add an entry to the sign off history.
                    cert.addSignOffHistory(certifier);
                }
                // If there were no approvals, set the signed date.
                if (!processSignOffApproverRule(cert, certifier)) {

                    cert.setSigned(new Date());
                    //This is the last signature, we need to lock the cert down if it was electronically signed
                    for(SignOffHistory sho : cert.getSignOffHistory()) {
                        if(sho.isElectronicSign()) {
                            cert.setImmutable(true);
                            break;
                        }
                    }
                    deleteWorkItems(cert);

                    // jsl - what else interesting should we include
                    // in the log?
                    Auditor.log(AuditEvent.ActionSignoff, cert.getName());
                }

            _context.commitTransaction();
        } catch (GeneralException e) {
            _context.rollbackTransaction();
            throw e;
        }

        return _errorHandler.getMessagesByType(Message.Type.Error);
    }

    private void deleteWorkItems(Certification cert) throws GeneralException {

        List<WorkItem> wis = cert.getWorkItems();
        if (wis != null) {

            Workflower wf = new Workflower(_context);

            for (WorkItem wi : wis) {
                // Certification work items normally do not have
                // a completion state, so we set it prior to archiving  
                // so it looks consistent with other work items
                if (wi.getState() == null)
                    wi.setState(WorkItem.State.Finished);

                wf.archiveIfNecessary(wi);
                _context.removeObject(wi);
            }

            // Clear the WorkItem references.
            cert.setWorkItems(null);
        }
    }

    /**
     * Run the certification sign off approval rule if configured, and forward
     * the certification to the approver if this returns an identity.
     */
    @SuppressWarnings("rawtypes")
    private boolean processSignOffApproverRule(Certification cert,
                                               Identity certifier)
        throws GeneralException {

        boolean forwarded = false;

        Rule rule = cert.getApproverRule(_context);
        if (null != rule) {
            // Run the rule.
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("certification", cert);
            params.put("certifier", certifier);
            params.put("state", _state);
            Map result = (Map) _context.runRule(rule, params);

            // Peel the identity or identity name out of the result map.
            Object identity = null;
            if (null != result) {
                identity = result.get("identity");
                if (null == identity) {
                    identity = result.get("identityName");
                }
            }

            Identity approver =
                ObjectUtil.getObject(_context, Identity.class, identity);

            // If we found an approver, forward the certification to this guy.
            if (null != approver) {
                WorkItem workItem = getWorkItemByOwner(cert.getWorkItems(), certifier);

                // Bug #9047 - If this is null, then the cert is being signed off by 
                // some other party besides certifier.  In this case, just pick a workitem 
                // to forward. It's not perfect, for example if there are multiple work 
                // items due to multiple certifiers, we may pick the wrong one. 
                // But its the best we can do. 
                if (null == workItem) {
                    if (!Util.isEmpty(cert.getWorkItems())) {
                        workItem = cert.getWorkItems().get(0);
                        if (log.isWarnEnabled()) {
                            log.warn("Could not find work item for " + certifier +
                                     " on certification: " + cert + ". Forwarding work item" +
                                     " owned by " + workItem.getOwner().getDisplayableName());
                        }
                    } else {
                        if (log.isWarnEnabled())
                            log.warn("Should have found work item for " + certifier +
                                     " on certification: " + cert);
                    }
                }
                
                if (null != workItem) {
                    // Forward the workitem - this forwards the cert, too.
                    Workflower wf = new Workflower(_context);
                    wf.forward(workItem, certifier, approver,
                            "Certification requires approval from " + approver.getDisplayableName(),
                            false, Workflower.ForwardType.SecondaryApproval);

                    // Send the notification.
                    sendSignOffApprovalNotification(cert, certifier, workItem);

                    // Audit this differently from the final sign off.
                    String oldOwner =
                        (null != certifier) ? certifier.getName() : null;
                    String newOwner =
                        (null != workItem.getOwner()) ? workItem.getOwner().getName() : null;
                    Auditor.log(AuditEvent.ActionSignoffEscalation, cert.getName(),
                                oldOwner, newOwner);

                    forwarded = true;
                }
            }
        }

        return forwarded;
    }

    /**
     * Retrieve the work item from the given list that has the given certifier
     * as an owner.
     */
    private WorkItem getWorkItemByOwner(List<WorkItem> workItems,
                                        Identity certifier) {
        WorkItem response = null;
        if ( ( null != workItems ) && ( null != certifier ) ) {
            for ( WorkItem currentWorkItem : workItems ) {
                if( isWorkItemOwner( currentWorkItem, certifier ) ) {
                    response = currentWorkItem;
                    break;
                }
            }
        }
        return response;
    }

    /**
     * Returns true if possibleOwner is the owner of workItem or a member of the
     * WorkGroup owning workItem
     *
     * @param workItem The work item
     * @param possibleOwner The Identity to check for ownership of workItem
     * @return True is possibleOwner is the owner of workItem or a member of the group
     * owning workItem
     */
    private boolean isWorkItemOwner( WorkItem workItem, Identity possibleOwner ) {
        boolean response = false;
        Identity workorkItemOwner = workItem.getOwner();
        if( workorkItemOwner.isWorkgroup() ) {
            List<Identity> groups = possibleOwner.getWorkgroups();
            if( !Util.isEmpty(groups) && groups.contains( workorkItemOwner ) ) {
                response = true;
            }
        } else if ( possibleOwner.equals( workorkItemOwner ) ) {
            response = true;
        }
        return response;
    }

    /**
     * Send a notification to the certification approver - the new owner of the
     * given work item.
     */
    private void sendSignOffApprovalNotification(Certification cert,
                                                 Identity oldCertifier,
                                                 WorkItem workItem)
        throws GeneralException {

        EmailTemplate template = emailTemplateRegistry.getTemplate(cert,
                Configuration.CERT_SIGN_OFF_APPROVAL_EMAIL_TEMPLATE);

        if (null != template) {

            Identity approver = workItem.getOwner();
            List<String> emails = null;

            if ( approver != null )
                 emails = ObjectUtil.getEffectiveEmails(_context, approver);

            if ( emails == null ) {
                if (log.isWarnEnabled()) {
                    log.warn("Not sending sign off approval email to " + approver +
                             " for " + cert + " - there is either no approver or " +
                             "no email address.");
                }
            }
            else {
                Map<String,Object> params = new HashMap<String,Object>();
                params.put("certification", cert);
                params.put("workItem", workItem);
                if (null != oldCertifier) {
                    params.put("certifier", oldCertifier);
                }
                if (null != approver) {
                    params.put("approver", approver);
                }

                EmailOptions ops = new EmailOptions(emails, params);
                this.sendEmailNotification(_context, template, ops);
            }
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Finish
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Make a final pass over the Certification checking completion status and
     * any other final validations.  If the Certification is ready to be
     * finished, generate work items for any remediations.
     *
     * A CertificationLink is added to each Identity that was a part
     * of this certification.  This is necessary for difference detection
     * on the next certification.
     *
     * Returns a non-null error list (or throws an exception) if
     * the certification could not be finished.
     *
     * TODO: Since we can be modifying a large number of identities
     * should we be committing as we go?
     */
    public List<Message> finish(Certification cert)
        throws GeneralException {

        _errorHandler.clear();
        _mitigationManager.initialize(isMitigationDeprovisioningEnabled(cert));

        try {

            // If this is not a bulk reassigned cert that needs to return to its
            // parent, finish it.
            if (!isReturnToParent(cert)) {
                // record the completed certification in each Identity,
                // and look for remediation work items to generate
                QueryOptions ops = new QueryOptions(Filter.eq("certification", cert));
                ops.setCloneResults(true);
                Iterator<Object[]> entities = _context.search(CertificationEntity.class, ops, Arrays.asList("id"));
                while(entities != null && entities.hasNext()){
                    String entityId = (String)entities.next()[0];
                    CertificationEntity entity = _context.getObjectById(CertificationEntity.class, entityId);
                    finishEntity(entity);
                    _context.decache();
                }

                // Reattach since we've most likely cleared cache processing the entities
                cert = (Certification)ObjectUtil.reattach(_context, cert);

                // Run post remediation stuff - send batched notifications and
                // update the stats on the cert.
                postRemediate(cert);

                // Flush the mitigation expirations to the identities along with provisioning the sunset dates
                _mitigationManager.flush();

                cert = (Certification)ObjectUtil.reattach(_context, cert);
                deleteWorkItems(cert);

                // Mark the certification as finished.
                cert.setFinished(new Date());
            }

            // What do we do about the phase for a rolling phase periodic cert
            // (continuous don't currently have sign off, so this must be a
            // periodic cert)?  These phases are maintained on the items, but
            // we still show "active" on some pages.  For now, we'll just force
            // this thing into the end phase.
            if (null != cert.getPhase() && cert.isUseRollingPhases()) {
                CertificationPhaser phaser =
                    new CertificationPhaser(_context, _errorHandler, getEmailSuppressor());
                cert = (Certification) phaser.changePhase(cert, cert.getPhase(), Certification.Phase.End);
            }

            // Commit now before we assimlate the bulk reassignment.
            _context.commitTransaction();

            // If this is a bulk reassignment cert, merge the items back into
            // the parent cert if configured.
            if (isReturnToParent(cert)) {
                // Bug#18237, Comment#5,
                // The following method can't be called with lock check for the cert because
                // the cert is already locked during the finish phase.
                rescindChildCertification(cert, true, false, true);
            }

            // Assuming that we did finish the cert, refresh the statistics on it's
            // associated CertificationGroups
            if (cert.getFinished() != null){
                // get a fresh copy of the cert in case it was deleted.
                Certification c = _context.getObjectById(Certification.class, cert.getId());
                if (c!=null)
                    refreshCertificationGroups(c);
            }

        }
        catch (GeneralException e) {
            _context.rollbackTransaction();
            throw e;
        }

        return _errorHandler.getMessagesByType(Message.Type.Error);
    }

    private boolean isMitigationDeprovisioningEnabled(Certification certification) throws GeneralException {
        CertificationDefinition definition = certification.getCertificationDefinition(_context);
        // Do not fallback to the system config here to prevent changes to certs that existed before this feature.
        return (definition != null) && Util.otob(definition.isMitigationDeprovisionEnabled());
    }

    /**
     * Find out whether a cert needs to return to its parent.  This option
     * is enabled if "Return Reassignments to Original Certification" is enabled.
     * @param cert
     * certification object in question
     * @return  returnToParent
     * true if the cert is both a Bulk Reassignment and assimilateBulkReassignments is enabled, false otherwise.
     */
    public boolean isReturnToParent(Certification cert) {
        try {
            Boolean assimilateBulkReassignments = null;
            // for older certs this attribute will live on the cert object
            if (cert.getAttribute(Configuration.ASSIMILATE_BULK_REASSIGNMENTS, null) != null) {
                assimilateBulkReassignments = cert.isAssimilateBulkReassignments(_context.getConfiguration().getAttributes());
            }
            else {
                // for the newer certs this value lives in the cert definition object
                CertificationDefinition certDef = cert.getCertificationDefinition(_context);
                if (certDef != null) {
                    assimilateBulkReassignments = certDef.isAssimilateBulkReassignments(_context);
                }
                else {
                    assimilateBulkReassignments = false;
                }
            }

            boolean returnToParent = cert.isBulkReassignment() && assimilateBulkReassignments;
            return returnToParent;
        } catch (GeneralException ge) {
            log.error("Error attempting to find out whether certification needs to return to parent: ", ge);
            _errorHandler.addMessage(new Message(Message.Type.Error,
                    MessageKeys.CERT_NOT_FOUND));
            return false;
        }
    }

    private void refreshCertificationGroups(Certification cert) throws GeneralException{
        if (cert.getCertificationGroups() != null) {
            for (CertificationGroup group : cert.getCertificationGroups()){
                // Since the start() method decaches, the groups get lost.
                // We need to reattach them prior to calling refreshStatistics()
                // See bug 25958
                group = ObjectUtil.reattach(_context,  group);
                refreshStatistics(group);
            }
        }
    }

    /**
     * Refreshes statistics for the certgroup.
     */
    public static void refreshStatistics(SailPointContext context, CertificationGroup group) throws GeneralException{

        if (group == null) {
            return;
        }

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("certificationGroups.id", group.getId()));

        ops.setCloneResults(true);

        // Optionally filter empty certs for pending groups
        CertificationService.filterEmptyCerts(context, ops, group);
        
        List<String> projectionCols = new ArrayList<String>();
        projectionCols.add("count(id)");
        projectionCols.add("count(signed)");

        Iterator<Object[]> iter = context.search(Certification.class, ops, projectionCols);
        if (iter != null && iter.hasNext()){
            Object[] results=iter.next();
            int total = ((Long)results[0]).intValue();
            int completed = ((Long)results[1]).intValue();
            
            int percentComplete = CertificationStatCounter.calculatePercentComplete(completed, total);

            // Don't overwrite an Error or Pending status regardless of completion status.
            // Pending cert groups will get set to Complete in BaseCertificationBuilder.finalize
            if (percentComplete==100 && !CertificationGroup.Status.Error.equals(group.getStatus())
                    && !CertificationGroup.Status.Pending.equals(group.getStatus())){
                group.setStatus(CertificationGroup.Status.Complete);
            }

            group.setCompletedCertifications(completed);
            group.setTotalCertifications(total);
            group.setPercentComplete(percentComplete);

            context.saveObject(group);
            context.commitTransaction();
        }
    }
    
    public void refreshStatistics(CertificationGroup group) throws GeneralException{

        refreshStatistics(_context, group);
    }

    /**
     * Finish the given CertificationEntity.
     * 
     * For 6.0 added code here to update the entitlements table
     * when we finish a certification item.  This is done at a
     * per item level to avoid fetching and decaching all of the
     * items again.
     */
    private void finishEntity(CertificationEntity entity)
        throws GeneralException {

        Certification cert = entity.getCertification();
        CertificationEntitlizer entitlizer = new CertificationEntitlizer(_context);
        entitlizer.prepare(cert);

        // If this is an Entitlement Certification (i.e. DataOwner type), then we need to process the
        // CertificationEntity differently than if it is an Identity type
        if (CertificationEntity.Type.DataOwner.equals(entity.getType())) {
            QueryOptions ops = new QueryOptions(Filter.eq("parent.id", entity.getId()));
            ops.setCloneResults(true);
            Iterator<Object[]> items = _context.search(CertificationItem.class, ops, Arrays.asList("id"));
            int cnt = 0;
            while (items != null && items.hasNext()) {
                // keep track of the assignment adds and removes as we
                // go over the entity and reconcile afterwards. This is because to 
                // update the assignments we'll need to lock the identity.
                AttributeAssignmentHandler handler= new AttributeAssignmentHandler(_context);
                handler.prepare(cert);

                String itemId = (String)items.next()[0];
                CertificationItem item = _context.getObjectById(CertificationItem.class, itemId);
                if( !item.isFinished() ) {
                    finishItem(item);
                    if (handler.enabled()) {
                        handler.computeAssignment(item);
                    }
                    //
                    // If necessary update the Entitlements table with this data
                    //
                    entitlizer.setCurrent(entity, item, handler.getCurrentAdds(), handler.getCurrentRemoves());
                    //reset the current stats
                    handler.reset();

                    _context.commitTransaction();
                }

                _context.decache();

                // update the identity...
                entity = ObjectUtil.reattach(_context, entity);
                item = ObjectUtil.reattach(_context, item);

                Identity ident = ObjectUtil.lockIdentity(_context, item.getTargetId());
                if (ident != null) {
                    try {
                        handler.updateAssignments(ident);
                        setCertificationLink(ident, entity.getCertification(), entity);
                    }
                    finally {
                        ObjectUtil.unlockIdentity(_context, ident);
                    }
                }
            }
        } else {
            // keep track of the assignment adds and removes as we
            // go over the entity and reconcile afterwards. This is because to 
            // update the assignments we'll need to lock the identity.
            AttributeAssignmentHandler handler= new AttributeAssignmentHandler(_context);
            handler.prepare(cert);

            QueryOptions ops = new QueryOptions(Filter.eq("parent.id", entity.getId()));
            ops.setCloneResults(true);
            Iterator<Object[]> items = _context.search(CertificationItem.class, ops, Arrays.asList("id"));
            int cnt = 0;
            while (items != null && items.hasNext()){
                String itemId = (String)items.next()[0];
                CertificationItem item = _context.getObjectById(CertificationItem.class, itemId);
                if( !item.isFinished() ) {
                    finishItem(item);
                    if ( CertificationEntity.Type.Identity.equals(entity.getType()) && handler.enabled())  {
                        handler.computeAssignment(item);
                    }
                    //
                    // If necessary update the Entitlements table with this data
                    //
                    entitlizer.setCurrent(entity, item, handler.getCurrentAdds(), handler.getCurrentRemoves());
                    //reset the current stats
                    handler.reset();

                    _context.commitTransaction();
                }
                cnt++;
                if (cnt > 20) {
                    cnt = 0;
                    _context.decache();
                }
            }
            _context.decache();

            // update the identity...
            entity = ObjectUtil.reattach(_context, entity);
            if (CertificationEntity.Type.Identity.equals(entity.getType()) 
                    || CertificationEntity.Type.DataOwner.equals(entity.getType())) {
                Identity ident = ObjectUtil.lockIdentity(_context, entity.getIdentity());
                if (ident != null) {
                try {
                        handler.updateAssignments(ident);
                        setCertificationLink(ident, entity.getCertification(), entity);
                    }
                    finally {
                        ObjectUtil.unlockIdentity(_context, ident);
                    }
                }
            }
        }
    }


    /**
     * Finish the given item - tweak mitigation expirations on the identity,
     * update the decision history with this item, and handle the remediation.
     */
    private void finishItem(CertificationItem item)
        throws GeneralException {

        // If the identity is null it means we're in a non-identity cert
        // so the entity obj does not reference an identity. In that case
        // the item may reference the identity.
        Identity itemIdentity = item.getIdentity(_context);

        if (itemIdentity != null) {
            _mitigationManager.handle(item);
            updateDecisionHistory(itemIdentity, item.getParent(), item);
        }

        /** This updates the status of the actual policy violation object based on the
         * decision that was made on the item.
         */
        handlePolicyViolation(item);

        // This handles Identities and other types that can be remediated.
        handleRemediation(item);

        item.setFinishedDate( new Date() );

        _context.commitTransaction();


    }
    
    /**
     * Store the decision history for the given item on the identity.
     */
    public void updateDecisionHistory(CertificationItem item)
        throws GeneralException {

        CertificationEntity entity = item.getParent();

        Identity ident = ObjectUtil.lockIdentity(_context, item.getIdentity());
        if (ident != null) {
            try {
                updateDecisionHistory(ident, entity, item);
            }
            finally {
                ObjectUtil.unlockIdentity(_context, ident);
            }
        }

    }

    /**
     * If the entity is of a type that is historical, add the given
     * certification item to the Identity's certification decision history.
     */
    private void updateDecisionHistory(Identity identity,
                                       CertificationEntity entity,
                                       CertificationItem item)
        throws GeneralException {

        if (!item.isHistorical())
            return;

        identity.addCertificationDecision(_context, item);

        // if the user has decided to revoke any roles which were required or
        // permitted by the primary role, add history for those roles.
        if (item.getAction() != null && item.getAction().getAdditionalRemediations() != null){
            for(String revokedRoleId : item.getAction().getAdditionalRemediations()){
                Bundle role = _context.getObjectByName(Bundle.class, revokedRoleId);
                if (role != null) {
                    identity.addCertificationDecision(_context, item, new CertifiableDescriptor(role));
                }
            }

            _context.commitTransaction();
        }
    }

    /**
     * Save a reference to the completed certification in the Identity
     * for later use in locating the previous certification for
     * difference generation.
     *
     * TODO: May want some policies on how this list is managed,
     * currently only keeping one link for each type of certification,
     * may want to let these live longer.
     */
    private void setCertificationLink(Identity id, Certification cert,
                                      CertificationEntity cid)
        throws GeneralException {

        if (id != null) {
            // This removes any prior certifications of this type
            id.addLatestCertification(cert, cid);
        }
    }

    /**
     * Move the items from the given child certification into its parent and
     * delete this certification.
     * @param cert The certification which is being rescinded.
     * It will check whether this cert or the parent is locked. If either is locked then
     * it will not continue.
     */
    public void rescindChildCertification(Certification cert)
            throws GeneralException {
        rescindChildCertification(cert, true, true);
    }

    /**
     * Walks down the child tree and tests if any of the children are immutable.
     *
     * @param cert The cert to test if it is immutable
     * @throws ModifyImmutableException when cert or any of its children are immutable
     */
    private void checkImmutableChildren(Certification cert) throws ModifyImmutableException {

        if (!cert.isImmutable()) {
            List<Certification> children = cert.getCertifications();
            if ((null != children) && !children.isEmpty()) {
                for (Certification child : children) {
                    checkImmutableChildren(child);
                }
            }
        }
        else {
            throw new ModifyImmutableException(cert,
                    new Message(Message.Type.Error, MessageKeys.CERT_RESCIND_CHILD_IMMUTABLE, cert.getName()));
        }
    }

    /**
     * Move the items from the given child certification into its parent and
     * delete this certification.
     *
     * If lock checks fail, then the process will return and not continue.
     *
     * @param cert the child certification which is to be rescinded
     * @param checkParentLock whether to check if the parent cert is locked
     * @param checkCertLock whether to check this cert is locked
     */
    public void rescindChildCertification(Certification cert, boolean checkParentLock, boolean checkCertLock)
        throws ModifyImmutableException, GeneralException {

        rescindChildCertification(cert, checkParentLock, checkCertLock, false);
    }

    /**
     * Move the items from the given child certification into its parent and
     * delete this certification.
     *
     * If lock checks fail, then the process will return and not continue.
     *
     * @param cert the child certification which is to be rescinded
     * @param checkParentLock whether to check if the parent cert is locked
     * @param checkCertLock whether to check this cert is locked
     * @param ignoreImmutableChecks If true, the certifications are not checked for immutability before
     *     rescinding.  This is used when automatically assimilating e-signed certs into parents.
     */
    private void rescindChildCertification(Certification cert, boolean checkParentLock, boolean checkCertLock,
                                           boolean ignoreImmutableChecks)
        throws ModifyImmutableException, GeneralException {

        // Sanity check.
        if (null == cert.getParent()) {
            throw new GeneralException("Expected a parent certification for bulk reassignment.");
        }

        if (checkParentLock && ObjectUtil.isLockedById(_context, Certification.class, cert.getParent().getId())) {
        	if (log.isInfoEnabled()) {
        		log.info("parent is locked can't rescind.");
        	}
        	return;
        }
        
        if (checkCertLock && ObjectUtil.isLockedById(_context, Certification.class, cert.getId())) {
        	if (log.isInfoEnabled()) {
        		log.info("child cert is locked can't rescind.");
        	}
        	return;
        }

        // Can't rescind if any of the children are immutable.  For e-signed certs that are automatically being
        // assimilated into their parent, we skip this check.
        boolean ignore = ignoreImmutableChecks && cert.isElectronicallySigned();
        if (!ignore) {
            checkImmutableChildren(cert);
        }
        
        Certification parent = cert.getParent();

        try {
            // Lock the parent certification to keep other siblings in 
            // other threads from corrupting the parent's entities list
            ObjectUtil.lockObject(_context, 
                    Certification.class, 
                    parent.getId(), 
                    _context.getUserName(), 
                    SailPointContext.LOCK_TYPE_PERSISTENT, 
                    // If it's locked, we want it to keep trying
                    300);

            CertificationSwizzler swizzler =
                    new CertificationSwizzler(_context, this);

            // Remove the entities from this cert.
            List<CertificationEntity> entities =
                    new ArrayList<CertificationEntity>();
            entities.addAll(cert.getEntities());
            cert.getEntities().clear();

            // Add the entities to the parent cert.  Do we need to commit/flush
            // occasionally?
            swizzler.merge(entities, cert.getParent(), false, false);
            _context.commitTransaction();

            // If the child has any child certs, move them onto the parent.
            // reattach cert back to session since merge now calls refresh which triggers decache
            cert = ObjectUtil.reattach(_context, cert);
            List<Certification> children = cert.getCertifications();
            if ((null != children) && !children.isEmpty()) {
                // add to parent and remove from cert.
                children = new ArrayList<Certification>(children);
                cert.getCertifications().clear();

                for (Certification child : children) {
                    cert.getParent().add(child);
                }
                _context.saveObject(cert.getParent());
                _context.saveObject(cert);
                _context.commitTransaction();
            }

            // Audit the rescind.
            this.auditor.auditRescind(cert);
            // jsl - Auditor doesn't commit and delete can now decache
            _context.commitTransaction();

            // Delete the cert.
            this.deleteWithoutLock(cert);
        }
        catch (GeneralException ge) {
            // we might have to try and unlock the parent certification after this exception is caught. Unlocking the parent
            // might throw its own exception. So at least capture the incoming exception for logging purposes so it's not 
            // lost
            log.error(ge.getMessage(), ge);
            throw ge;
        } finally {
            // unlock the parent cert
            try {
                // delete will decache, refetch
                parent = _context.getObjectById(Certification.class, parent.getId());
                _context.unlockObject(parent);
            }
            catch (GeneralException ge) {
                // log it and capture the parent cert information
                log.error("Could not unlock parent cert " + parent.getId() , ge);
                // let it fly
                throw ge;
            }
        }
    }

    /**
     * Handle any remediation requests on the given CertificationEntity
     * according to the RemedationAction set on the action.
     * @deprecated Use a projection search for items and use {@link #handleRemediation(CertificationItem)}
     */
    public void handleRemediations(Certification cert,
                                   CertificationEntity entity)
        throws GeneralException {

        List<CertificationItem> items = entity.getItems();
        if (items != null) {
            for (CertificationItem subItem : items) {
                handleRemediation(subItem);
            }
        }
    }

    /**
     * Update the status of the policy violation based on the decision
     * on the CertificationItem
     * @param item
     */
    public void handlePolicyViolation(CertificationItem item)
        throws GeneralException {
        PolicyViolation itemViolation = item.getPolicyViolation();
        if(itemViolation==null)
            return;

        if (!item.isDelegatedOrWaitingReview() && !item.isChallengeActive()) {
            /** Have to load the actual violation from storage since the violation on the item is a copy */
            PolicyViolation violation = _context.getObjectById(PolicyViolation.class, itemViolation.getId());

            // Violation has been deleted since the certification
            // started. Bail
            if (violation == null)
                return;

            CertificationAction action = item.getAction();

            if(action.isMitigation()) {
                violation.setStatus(PolicyViolation.Status.Mitigated);
                
                // log this action for the violation
                if (Auditor.isEnabled(AuditEvent.ActionViolationAllowException) && null != action) {
                    // If the action is null, it's not worth logging...or is it?
                    PolicyViolationAuditor.auditMitigation(violation,
                            action.getActor(_context),
                            AuditEvent.SourceViolationCertification,
                            action.getComments(),
                            action.getMitigationExpiration());
                }
            } else if(action.isAcknowledgment()) {
                violation.setStatus(PolicyViolation.Status.Mitigated);
                
                // log this action for the violation
                if (Auditor.isEnabled(AuditEvent.ActionViolationAcknowledge) && null != action) {
                    // If the action is null, it's not worth logging...or is it?
                    PolicyViolationAuditor.auditAcknowledge(violation,
                            action.getActor(_context),
                            AuditEvent.SourceViolationCertification,
                            action.getComments());
                }
            } else if(action.isRemediation()) {
                violation.setStatus(PolicyViolation.Status.Remediated);
                
                // log this action for the violation
                if (Auditor.isEnabled(AuditEvent.ActionViolationCorrection) && null != action) {
                    // If the action is null, it's not worth logging...or is it?
                    PolicyViolationAuditor.auditCorrect(violation,
                            action.getActor(_context),
                            AuditEvent.SourceViolationCertification,
                            action.getComments(),
                            action.getOwner(),
                            action,
                            action.getDescription());
                }
            }

            _context.saveObject(violation);
            _context.commitTransaction();
        }

    }

    /**
     * Handle any remediation requests on the given CertificationItem
     * according to the RemedationAction set on the action.
     */
    public void handleRemediation(CertificationItem item)
        throws GeneralException {

        CertificationAction action = item.getAction();
        boolean hasValidStatus = action != null && (CertificationAction.Status.Remediated.equals(action.getStatus()) ||
                action.getAdditionalActions() != null);

        if (!hasValidStatus)
            return;

        // Don't launch the remediation now if there is an active challenge.
        // This can be the case if we're finishing during a continuous flush or
        // if we're processing revokes immediately with challenge phase enabled.
        // Also, don't fire off remediations if the item (or entity) is
        // delegated or waiting delegation review.  See bug 1838.
        if (!item.isDelegatedOrWaitingReview() && !item.isChallengeActive()) {

            // Batch the notifications (if not handling a one-off) since we roll
            // remediations that have the same owner into a single work item
            // incrementally.  These will all be sent at the end of the "finish"
            // method.
            RemediationManager remedMgr = new RemediationManager(_context, _errorHandler);
            remedMgr.markForRemediation(item);
            
            if (log.isDebugEnabled()) {
                log.debug(String.format("item %s has been marked for remediation", item.getId()));
            }
        }
        // Can't rely on the caller saving the parent object to preserve changes, so save the item now
        this._context.saveObject(item);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Delete
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Delete a hierarchy of certifications.
     * Might be able to let Hibernate handle this if we cascade
     * the Certification->Certification relationship.
     * This delete will lock the certification so that no operation
     * will happen while the delete is going on.
     */
    public boolean delete(final Certification cert)
        throws GeneralException {

        Callable<Void> doWhat = new Callable<Void>() {
            public Void call() throws Exception {
        
                delete(ObjectUtil.reattach(_context, cert), false, true);
                
                return null;
            }
        }; 
        Pair<Boolean, Void> result = ObjectUtil.doWithCertLock(_context, cert, doWhat, false, 0);
        if (result.getFirst()) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * This will do the same delete as {@link #delete(Certification)} above but without locking the cert.
     * This will usually be called by unit tests etc.
     * @param cert the certification to be deleted
     * @throws GeneralException
     */
    public void deleteWithoutLock(Certification cert) throws GeneralException {
        delete(cert, false, true);
    }
    
    private void delete(Certification cert, boolean forArchive, boolean removeFromParent)
        throws GeneralException {

        // Allow this to be  disabled until later in the release, then
        // just always go there.  I don't think it can happen but if we're
        // not a root cert (removeFromParent==false) we're already in
        // old code so don't use the new one
        boolean useNewFramework = true;
        if (useNewFramework && removeFromParent) {
            CertificationDeleter cd = new CertificationDeleter(_context);
            cd.delete(cert, forArchive);
            return;
        }
        
        try {
            
            //
            // This queries Identity Entitlements avoid it if at all possible
            //
            if ( cert != null && Util.size(cert.getEntities()) > 0)
                clearCertDataFromEntitlements(cert);
            
            deleteWorkItems(cert);

            // Look for any other work items generated by this certification and
            // delete them. Remediation work items stick around after archiving.
            if (!forArchive) {
                deleteWorkItems("action", cert, CertificationItem.class);
            }

            // Delete challenge and delegation work items - these should go away
            // regardless of whether we're archiving or not.
            deleteWorkItems("challenge", cert, CertificationItem.class);
            deleteWorkItems("delegation", cert, CertificationItem.class);
            deleteWorkItems("delegation", cert, CertificationEntity.class);

            _context.commitTransaction();

            // Delete the archived entities
            deleteArchivedEntities(cert);
            
            //Prevent any problems with foreign key refs from cert action
            //children and sources
            List<CertificationEntity> entities = cert.getEntities();
            for(CertificationEntity entity : Util.iterate(entities)) {
                CertificationAction action = entity.getAction();
                if (null != action) {
                    action.setParentActions(null);
                    action.setChildActions(null);
                    action.setSourceAction(null);
                    _context.saveObject(action);
                }
                List<CertificationItem> items = entity.getItems();
                for(CertificationItem item : Util.iterate(items)) {
                    action = item.getAction();
                    if (null != action) {
                        action.setParentActions(null);
                        action.setChildActions(null);
                        action.setSourceAction(null);
                        _context.saveObject(action);
                    }
                }
            }
            _context.commitTransaction();

            // Delete the children, too.  These come as a package deal.
            QueryOptions childOps = new QueryOptions();
            childOps.add(Filter.eq("parent.id", cert.getId()));
            
            cert.setCertifications(null);
            
            Iterator<Certification> children = new IncrementalObjectIterator<Certification>(_context, Certification.class, childOps);
            while (children.hasNext()) {
                Certification child = children.next();
                
                delete(child, forArchive, false);
                
                _context.decache(child);
            }

            _context.commitTransaction();

            // Need to remove this certification from it's parent.
            if (removeFromParent && null != cert.getParent()) {
                List<Certification> parentChildren =
                    cert.getParent().getCertifications();
                if (null != parentChildren) {
                    parentChildren.remove(cert);
                    
                }   
                _context.saveObject(cert.getParent());
            }

            _context.commitTransaction();

            List<CertificationGroup> updatedGroups = new ArrayList<CertificationGroup>();

            if (cert.getCertificationGroups() != null){
                for(CertificationGroup group : cert.getCertificationGroups()){
                    updatedGroups.add(group);
                }
            }

            // jsl - iffy transaction here
            _context.commitTransaction();

            cert = _context.getObjectById(Certification.class, cert.getId());
            _context.removeObject(cert);
            _context.commitTransaction();

            for(CertificationGroup group : updatedGroups){
                refreshStatistics(group);
            }
        }
        catch (GeneralException e) {
            _context.rollbackTransaction();
            throw e;
        }
    }
    
    private void deleteArchivedEntities(Certification certification) 
            throws GeneralException {
        
        for (ArchivedCertificationEntity entity : certification.fetchArchivedEntities(_context)) {
            _context.removeObject(entity);
        }
    }
    /**
     * Delete any work items that were created and had their information stored
     * in the WorkItemMonitor on the given class with the given name for the
     * given cert.
     *
     * @param  workItemMonitorName  The name of the work item monitor field.
     * @param  cert                 The cert from which to delete the items.
     * @param  clazz                The class which holds the work item monitor.
     */
    private void deleteWorkItems(String workItemMonitorName,
                                 Certification cert,
                                 Class<? extends SailPointObject> clazz)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();

        String certPath = "certification";
        if (CertificationItem.class.equals(clazz)) {
            certPath = "parent.certification";
        }

        // Look for WorkItemMonitors that have a non-null work item (ie - a work
        // item was generated) but null completion state (ie - it wasn't
        // completed).
        qo.add(Filter.and(Filter.notnull(workItemMonitorName + ".workItem"),
                          Filter.isnull(workItemMonitorName + ".completionState"),
                          Filter.eq(certPath, cert)));
        List<String> props = new ArrayList<String>();
        props.add(workItemMonitorName + ".workItem");

        qo.setCloneResults(true);

        Iterator<Object[]> it = _context.search(clazz, qo, props);
        if (null != it) {
            while (it.hasNext()) {
                Object[] row = it.next();
                String workItemId = (String) row[0];

                WorkItem workItem =
                    _context.getObjectById(WorkItem.class, workItemId);
                if (null != workItem) {
                    _context.removeObject(workItem);
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // IdentityEntitlement reference cleanup
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Bulk update Identity Entitlements that match this cert
     * and clear ant previous and current values that refrence
     * the cert that is about to be deleted.
     * 
     * This method performs bulk updates, which isn't complicated
     * but also not the norm.

     * 
     * @param cert
     * @throws GeneralException
     */
    private void clearCertDataFromEntitlements(Certification cert) 
        throws GeneralException {
       
        Meter.enter(59, "Certificationer - clearCetDataFromEntitlements");
        CertificationEntitlizer ce = new CertificationEntitlizer(_context);
        ce.prepare(cert); 
        ce.clearEntitlementCertInfo(cert, "certificationItem");
        ce.clearEntitlementCertInfo(cert, "pendingCertificationItem");
        Meter.exit(59);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Archive
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Check to see if a certification is archivable.
     * Added for the housekeeping task so it can avoid calling archive()
     * and putting exceptions in the task result when the certification
     * can't be archived.
     */
    public boolean isArchivable(Certification cert)
        throws GeneralException {

        boolean archivable = false;

        // just be a top-level cert
        if (cert.getParent() == null) {
            // and the tree must be signed
            archivable = isHierarchySigned(cert) && cert.getFinished() != null;
        }

        return archivable;
    }

    public boolean isHierarchySigned(Certification cert)
        throws GeneralException {

        boolean signed = cert.hasBeenSigned();
        if (signed) {
            signed = Certification.isChildCertificationsComplete(_context, cert.getId());
        }
        return signed;
    }

    /**
     * Archive an Certification.
     */
    public CertificationArchive archive(Certification cert, String name)
        throws GeneralException {

        CertificationArchive arch = null;

        // Only allow archiving top-level certification.
        if (null != cert.getParent()) {
            Certification root = cert;
            while (null != root.getParent()) {
                root = root.getParent();
            }

            throw new GeneralException(new Message(Message.Type.Error,
                    MessageKeys.CANNOT_ARCHIVE_CERT, root.getName()));
        }

        // Make sure that the given certification is signed.
        // TODO: Figure out if we want to loosen this requirement.  If so, then
        // our UI will have to be smart enough to deal with references to
        // certifications that aren't around as first class objects any more.
        // For example, the delegation work item UI assumes that it can load the
        // certification objects by ID.
        if (!isHierarchySigned(cert)){
            throw new GeneralException(new Message(Message.Type.Error,
                    MessageKeys.CANNOT_ARCHIVE_UNISIGNED_CERT,  cert.getName()));
        }

        String certificationGroupId = null;
        if (cert.getCertificationGroups() != null){
            for(CertificationGroup grp : cert.getCertificationGroups()){
                if (CertificationGroup.Type.Certification.equals(grp.getType())){
                    certificationGroupId = grp.getId();
                    break;
                }
            }
        }

        try {
            arch = new CertificationArchive();

            arch.setCertificationGroupId(certificationGroupId);

            if (name == null)
                name = generateArchiveName(cert);
            else {
                CertificationArchive current =
                    _context.getObjectByName(CertificationArchive.class, name);
                if (current != null)
                    throw new GeneralException("Archive already exists");
            }

            arch.setName(name);
            arch.setArchive(cert);
            
            // save this now, or let the application decide?
            _context.saveObject(arch);
            _context.commitTransaction();
        }
        catch (GeneralException e) {
            _context.rollbackTransaction();
            throw e;
        }

        // Delete after the archive is saved.
        delete(cert, true, true);

        // If there is a CertificationGroup associated with this certification,
        // check to see if there are any more certs attached to it. If not, it can be
        // archived as well
        if (certificationGroupId != null){
            QueryOptions qo = new QueryOptions(Filter.eq("certificationGroups.id", certificationGroupId));
            if (0 == _context.countObjects(Certification.class, qo)){
                CertificationGroup grp = _context.getObjectById(CertificationGroup.class, certificationGroupId);
                if (grp != null){
                    grp.setStatus(CertificationGroup.Status.Archived);
                    _context.saveObject(grp);
                    _context.commitTransaction();
                }
            }
        }

        return arch;
    }

    public CertificationArchive archive(Certification cert)
        throws GeneralException {

        return archive(cert, null);
    }

    /**
     * Generate a unique name for an certification archive.
     * TODO: Support various options and/or rules.
     * Need to timestamp if we get a collision!!
     */
    public String generateArchiveName(Certification cert)
        throws GeneralException {

        // should always have this...
        String name = cert.getName();
        if (name == null) {
            // don't worry about making these nice, should
            // have already had name on the Certification
            Message nameMsg = new Message(MessageKeys.ARCHIVE_NAME_ANONYMOUS,
                    Util.uuid());
            name = nameMsg.getLocalizedMessage();
        }
        else if (_context.getObjectByName(CertificationArchive.class, name) != null) {
            // sigh, already exists
            // Could be smarter about a nice numeric counter, but will
            // have transaction & race conditions issues.  Punt and
            // use a uuid.  Ideally the UI will be prompting for
            // a name anyway.
            name = name + " " + Util.uuid();
        }

        return name;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkItemHandler
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called via Workflower whenever ownership changes.  This should
     * only update any associated models to reflect the change, generic
     * operations like notifications and commenting have already
     * been done.
     *
     * Do not commit the transaction.
     */
    public void forwardWorkItem(SailPointContext context, WorkItem item,
                                Identity newOwner)
        throws GeneralException {

        Identity prevOwner = item.getOwner();

        // If you implement WorkItemOwnerChangeListener, you'll need to add code
        // for notification here.

        String id = item.getCertificationItem();
        if (null != id) {
            CertificationItem certItem =
                context.getObjectById(CertificationItem.class, id);
            if (null != certItem) {
                certItem.workItemOwnerChanged(context, item, newOwner, prevOwner);
            }
        }

        id = item.getCertificationEntity();
        if (null != id) {
            CertificationEntity entity =
                context.getObjectById(CertificationEntity.class, id);
            if (null != entity) {
                entity.workItemOwnerChanged(context, item, newOwner, prevOwner);
            }
        }

        id = item.getCertification();
        if (null != id) {
            Certification cert =
                context.getObjectById(Certification.class, id);
            if (null != cert) {
                // note that this will not change either the name or the shortName
                cert.workItemOwnerChanged(context, item, newOwner, prevOwner);
            }
        }
    }

    /**
     * Validate modifications to a work item before it is persisted.
     * You should only throw here for errors the user can do something
     * about.
     */
    public void validateWorkItem(SailPointContext con, WorkItem item)
        throws GeneralException {

    }

    /**
     * Perform side effects after an item has been persisted.
     */
    public void handleWorkItem(SailPointContext con, WorkItem item,
                               boolean foreground)
        throws GeneralException {

        init(con);
        assimilate(item);
    }
    
    /**
     * Specific Certificationer logic to check for forwarding user.  This is
     * called by Workflower after auto foward and forward user rule has run.
     * It allows custom logic in work item handler to adjust forward user.
     * 
     * In this case of certs, we need to check that forwarding will not lead to 
     * self certification, if that option is disabled in the configuration. If so,
     * then run the fallback forward rule to get a new user.
     */
    @Override
    public Identity checkForward(SailPointContext context, WorkItem item, Identity src, boolean audit, Identity requester)
        throws GeneralException {

        Identity actual = src;
        if (isSelfCertifier(context, item, actual)) {
            Certification cert = item.getCertification(context);
            String certCreator = (cert == null) ? null : cert.getCreator();
            List<String> certifiers = (cert == null) ? null : cert.getCertifiers();
            Certification.Type certType = (cert == null) ? null : cert.getType();
            String certName = (cert == null) ? null : cert.getName();
            actual = getFallbackForwardIdentity(context, src, item,
                        certName, certType, certCreator, certifiers, audit, requester) ;
        }
        
        return actual;
    }

    private Identity getFallbackForwardIdentity(SailPointContext context, Identity src, WorkItem item, String certName,
                                                Certification.Type certType, String certCreator, List<String> certifiers,
                                                boolean audit, Identity requester)
            throws GeneralException {

        Workflower workflower = new Workflower(context);

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("name", certName);
        params.put("type", certType);
        params.put("creator", certCreator);
        params.put("certifiers", certifiers);
        return workflower.runFallbackForwardRule(src, item, params, audit, requester, ForwardType.SelfCertification);
    }

    /**
     * Check some list of entities/items to look for self certification. Returns
     * true if any items involve the certifier. Assumes the items are already 
     * existing/committed somewhere.
     * @param context Current SailPointContext
     * @param certifier Identity that is currently set as a certifier
     * @param itemClass Class of the items being validated -- either CertificationItem or CertificationEntity
     * @param itemIds IDs of the items being validated
     * @param certification Certification on whose behalf validation is performed
     */
    private boolean isSelfCertifier(SailPointContext context, Identity certifier, 
            Class<? extends AbstractCertificationItem> itemClass, List<String> itemIds, Certification certification) 
        throws GeneralException {

        SelfCertificationChecker selfCertifyChecker = new SelfCertificationChecker(context, certification);
        boolean isSelfCertify = false;
        if (Util.isEmpty(itemIds)) {
            //check the whole cert
            isSelfCertify = selfCertifyChecker.isSelfCertify(certifier);
        }  else {
            isSelfCertify = selfCertifyChecker.isSelfCertify(certifier, itemIds, itemClass);
        }
        
        return isSelfCertify;
    }
    
    /**
     * Check a work item to look for self certification. Returns
     * true if any items involve the certifier. The certification 
     * should be part of the work item in order to be examined.
     * @param context Current SailPointContext
     * @param item WorkItem being validated
     * @param owner Identity who currently owns the WorkItem
     */
    private boolean isSelfCertifier(SailPointContext context, WorkItem item, Identity owner)
    throws GeneralException {
        
        if (item.isCertificationRelated() && 
                (WorkItem.Type.Certification.equals(item.getType()) ||
                 WorkItem.Type.Delegation.equals(item.getType()))) { 
            Certification cert = item.getCertification(context);

            //If the cert does not certify identities, then no-op.
            if (!cert.isCertifyingIdentities()) {
                return false;
            }

            Class<? extends AbstractCertificationItem> itemClass = null;
            String itemId = null;
            // Only check the full certification for Certification work types.
            // Otherwise, we should check the item/entity.
            if (!WorkItem.Type.Certification.equals(item.getType())) {
                // Check an item or entity if part of the work item.
                if (null != item.getCertificationItem()) {
                    itemClass = CertificationItem.class;
                    itemId = item.getCertificationItem();
                } else if (null != item.getCertificationEntity()) {
                    itemClass = CertificationEntity.class;
                    itemId = item.getCertificationEntity();
                }
            }

            return isSelfCertifier(context, owner, itemClass, Util.asList(itemId), cert);
        }
        
        return false;
    }

    /**
     * Return all work items in any of the WorkItemMonitors for this item and
     * all child items. This was pulled out of the CertificationSwizzler so it could be shared in the Certificationer.
     *
     * @param context SailPointContext
     * @param item the relevant cert item
     * @return list of work items for the certification item and all child items
     * @throws GeneralException
     */
    public static List<WorkItem> getWorkItems(SailPointContext context, AbstractCertificationItem item)
            throws GeneralException {
        List<WorkItem> workItems = new ArrayList<>();

        // Grab any delegation, action (ie - remediations), and challenge work
        // items.
        addWorkItem(item.getDelegation(), workItems, context);
        addWorkItem(item.getAction(), workItems, context);
        if (item instanceof CertificationItem) {
            addWorkItem(((CertificationItem) item).getChallenge(), workItems, context);
        }

        // DIVE!!
        if (null != item.getItems()) {
            for (CertificationItem child : item.getItems()) {
                workItems.addAll(getWorkItems(context, child));
            }
        }

        return workItems;
    }

    /**
     * Add the WorkItem for the given WorkItemMonitor to the given list.
     *
     * @param monitor WorkItemMonitor
     * @param items list to add items to
     * @param context SailPointContext
     * @throws GeneralException
     */
    private static void addWorkItem(WorkItemMonitor monitor, List<WorkItem> items, SailPointContext context)
            throws GeneralException {
        if (null != monitor) {
            WorkItem item = monitor.getWorkItem(context);
            if (null != item) {
                items.add(item);
            }
        }
    }
    
    public static class CertificationStartResults {

        private List<String> workItemIds;
        private int certificationCount;

        public CertificationStartResults() {
            workItemIds = new ArrayList<String>();
        }

        public List<String> getWorkItemIds() {
            return workItemIds;
        }

        public void setWorkItemIds(List<String> workItemIds) {
            this.workItemIds = workItemIds;
        }

        public int getCertificationCount() {
            return certificationCount;
        }

        public void setCertificationCount(int certificationCount) {
            this.certificationCount = certificationCount;
        }

        public void incCertificationCount(){
            certificationCount++;
        }
    }
    
    
    /*
     * Used to auto-signoff when either a cert is emtpy or if all items
     * are reassigned.  This is only called when a staged cert is activated
     * @param cert
     * @throws GeneralException
     */
    private void autoSignoff(Certification cert) throws GeneralException {
        CertificationService svc = new CertificationService(_context);
        if (svc.isReadyForSignOff(cert)) {
            sign(cert, _requestor);
        }
    }

}
