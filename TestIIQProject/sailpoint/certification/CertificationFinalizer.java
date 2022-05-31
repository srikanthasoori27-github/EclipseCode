/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Finalize the generation of a certification, by moving it to an active state
 * and sending notifications.
 * 
 * Author: Jeff
 *
 * Called by PartitionHandler to implement the "finalize" phase.
 * At thils point the EntityBuilder partitions have all completed.
 * Perform a set of steps simlar to the end of CertificationExecutor.execute
 * 
 *   Start each certification in the group
 *   Store final information in the TaskResult
 *   Copy error and warning messages accumulated in the task result to the grop
 *   Save the group status
 *   Refresh group statistics
 * 
 * Currently still using Certificationer.start but start bringing that logic over here.
 * This should be generic enough to use with other cert types.
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.SailPointContext;
// if we factor out a new stat refresher, combine with this
import sailpoint.api.certification.CertificationStatCounter;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
// jsl - review why we need to be using this
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class CertificationFinalizer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////
    
	private static Log log = LogFactory.getLog(CertificationFinalizer.class);

    // TaskResult names

    public static final String RET_TOTAL = "total";
    public static final String RET_TYPE = "type";
    public static final String RET_EMAILS_SUPPRESSED = "emailsSuppressed";

    private static final String RET_ACTIVE_PERIOD_DURATION_AMOUNT =
        "activePeriodDurationAmount";
    private static final String RET_ACTIVE_PERIOD_DURATION_SCALE =
        "activePeriodDurationScale";
    private static final String RET_CHALLENGE_PERIOD_DURATION_AMOUNT =
        "challengePeriodDurationAmount";
    private static final String RET_CHALLENGE_PERIOD_DURATION_SCALE =
        "challengePeriodDurationScale";
    private static final String RET_REMEDIATION_PERIOD_DURATION_AMOUNT =
        "remediationPeriodDurationAmount";
    private static final String RET_REMEDIATION_PERIOD_DURATION_SCALE =
        "remediationPeriodDurationScale";
    public static final String RET_CERT_GROUP_IDS =
        "resultingCertGroupIds";
    
    // The usual suspects
    
    SailPointContext _context;
    TaskResult _result;
    CertificationDefinition _definition;
    CertificationGroup _group;

    Certificationer _certificationer;
    int _totalCerts;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    // 
    //////////////////////////////////////////////////////////////////////

    public CertificationFinalizer(SailPointContext context, TaskResult result,
                                  CertificationDefinition def,
                                  CertificationGroup group) {
        _context = context;
        _result = result;
        _definition = def;
        _group = group;
    }

    /**
     * Funny story...despite the class name, do not name this method "finalize".
     * Java reserves the finalize() method to be called by the garbage collector when
     * an object has no more references.  Something everyone forgets about.
     * This causes random obscure failures due to reuse of a closed Hibernate session.
     */
    public void execute() throws GeneralException {

        if (log.isInfoEnabled()) {
            log.info("CertificationFinalizer starting");
        }
        
        Message errorMessage = null;
        
        try {
            startCertifications();

            // this will have decached so have to refresh
            // the definition and result
            _definition = _context.getObjectById(CertificationDefinition.class, _definition.getId());
            _result = _context.getObjectById(TaskResult.class, _result.getId());
            
            // CertificationExecutor leaves this out of the finally
            // but that seems wrong, move it?
            saveFinalResults();
        }
        catch (Throwable t) {
            // This determines whether the CertificationGroup
            // gets a status of Error.  Doing it the way CertificationExecutor
            // does it for now, but we'll likely need more here since the
            // concept of failure is now distributed.
            errorMessage = new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t);
            log.error(errorMessage.getMessage(), t);
            _result.addMessage(errorMessage);
        }
        finally {
            finalizeGroup(errorMessage);
        }

        // Old logic is weird (who knew!?)
        // In the executor finally block it calls BaseCertificationBuilder.finalize.
        // finalize() stores final results and saves the group.
        // finalise() then calls Certificationer.refreshStatistics on the group.
        // This can throw for several reasons, so I guess the double save is to ensure
        // that the group state is at least set correctly even if stats are off?
        refreshGroupStatistics();
        
        if (log.isInfoEnabled()) {
            log.info("CertificationFinalizer finished");
        }

    }

    /**
     * Get the ids of all Certifications that belong to the group.
     */
    private List<String> getCertificationIds() throws GeneralException {

        List<String> ids = new ArrayList<String>();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.contains("certificationGroups", _group));

        List<String> props = new ArrayList<String>();
        props.add("id");

        Iterator<Object[]> result = _context.search(Certification.class, ops, props);
        while (result.hasNext()) {
            Object[] row = result.next();
            String id = (String)(row[0]);
            ids.add(id);
        }

        return ids;
    }
    
    /**
     * Find all the Certifications in this group and start them.
     * If any are empty delete them. This is how we will clean up
     * the initial root cert if everything was delegated.
     */
    private void startCertifications()
        throws GeneralException {

        List<String> ids = getCertificationIds();
        for (String id : Util.iterate(ids)) {
            Certification cert = _context.getObjectById(Certification.class, id);
            if (cert == null) {
                // shouldn't happen, we just queried
                log.error("Certification evaporated!");
            }
            else {
                // if cert is empty it can be deleted
                List<CertificationEntity> entities = cert.getEntities();
                int entityCount = Util.nullSafeSize(entities);
                int archiveCount = countArchivedEntities(cert);
                    
                if (entityCount == 0 && archiveCount == 0) {
                    // this should only be the case for the root certification
                    // that is used for backup if everything was reassigned
                    if (log.isInfoEnabled()) {
                        log.info("Deleting empty certification: " + cert.getName());
                    }
                    _context.removeObject(cert);
                    _context.commitTransaction();
                }
                else {
                    startCertification(cert);
                }
            }
            // keep the cache clean
            _context.decache();
        }
    }

    /**
     * Count the number of ArchivedCertificationEntity objects
     * associated with this Certification.  These are mapped as one-to-many bag
     * though the code associated with them seems to always treat them as
     * independent objects with a back pointer.  They're not added to a collection
     * by EntityBuilder.addArchivedEntities but it looks like you could just
     * call cert.getArchivedEntities.  Avoiding this since I'm not sure what
     * state that collection would be in here.  Query like the rest of the code.
     */
    private int countArchivedEntities(Certification cert)
        throws GeneralException {
        
        QueryOptions ops = new QueryOptions(Filter.eq("certification.id", cert.getId()));
        ops.setScopeResults(false);
        return _context.countObjects(ArchivedCertificationEntity.class, ops);
    }

    /**
     * Start one certification.  Still using the Certificationer but
     * I'd like to bring all that logic over here.  The problem though
     * is if we need to support staging, the transaction from Staged to 
     * Active will still go through Certificationer logic unless we can 
     * hook into that and redirect it back here.
     *
     * Certificationer.start returns a CertificationStartResults which has
     * the number of certs started, and the list of work item ids
     * generated.  CertificationExecutor only cared about the total
     * cert count and since we're we don't have parent/child relationships
     * the count will always be one.
     */
    private void startCertification(Certification cert)
        throws GeneralException {
        
        // reuse the same one for each cert so we can accumulate staistics
        if (_certificationer == null) {
            _certificationer = new Certificationer(_context);
        }

        if (log.isInfoEnabled()) {
            log.info("Starting certification: " + cert.getName());
        }
        
        // CertificationExecutor would do this for each context
        // it builds some helper objects and caches some options
        // Even though we're not using it for generating, it may be necessary
        // for starting? 
        _certificationer.init(_context);

        _certificationer.start(cert);
        // don't need CertificationStartResults, can only be one
        _totalCerts++;
            
        if (log.isInfoEnabled()) {
            log.info("Finished certification: " + cert.getName());
        }
    }

    /**
     * Save the results accumulating during the certification starting.
     * This is approxomately equal to the code in CertificationExecutor
     * after the loop over CertificationContexts.
     *
     * In CertificadtionExecutor, this would genereate an map of reuilts
     * from the CertificationBuilder and replace the map in the Taskresult.
     * BaseCertificationBuilder.  There are very few calls to add things to this
     * Map, IdentityCertificationBuilder and ManagerCertificationBuilder had
     * something ot add the name of the manager to the result but that's it.
     * Going to punt on that for awhile.
     *
     * The executor uses the same Certificationer object for the entire process
     * so there would be more results in that than we have here.  The other
     * partitions will have to have already saved their results.
     * TODO: That needs work, what about counters that have to be merged
     * from each partition?
     */
    private void saveFinalResults()
        throws GeneralException {
        
        // nothing much was in here, punt
        // result.setAttributes(getResults(builder));

        // why is it important to save the type?
        // !! why are we localiing it early?
        _result.setAttribute(RET_TYPE, Internationalizer.getMessage(_definition.getType().getMessageKey(),
                                                                   Locale.getDefault()));
        _result.setAttribute(RET_TOTAL, _totalCerts);

        // certificationer can be null if we ended up deleting all the empty ones
        // !! but in that case, aren't we supposed to delete the CertificationGroup too?
        if (_certificationer != null)
            _result.setAttribute(RET_EMAILS_SUPPRESSED, _certificationer.getEmailsSuppressed());
        
        addCertificationDefinitionResults();

        // Here CertificationExecutor would store a list of group ids
        // as a csv, even though we can only ever have one.
        _result.setAttribute(RET_CERT_GROUP_IDS, _group.getId());
            
        // Add any errors or warnings to the result
        // CetficiationExecutor may have had more here since it used
        // the same object for the entire process
        if (_certificationer != null) {
            _result.addMessages(_certificationer.getErrors());
            _result.addMessages(_certificationer.getWarnings());
        }

        // here the executor would add CertificationBuilder warnings
        // assume for now the entity partitions have already done this
        _context.saveObject(_result);
        _context.commitTransaction();
    }

    /**
     * Copied from CertificationExecutor
     * 
     * Add interesting parameters about the certification definition to the
     * given task result.
     *
     * jsl - is all this particularly interesing?
     */
    private void addCertificationDefinitionResults() {

        _result.setAttribute(RET_ACTIVE_PERIOD_DURATION_AMOUNT,
                            String.valueOf(_definition.getActivePeriodDurationAmount()));
        
        if (_definition.getActivePeriodDurationScale() != null) {
            _result.setAttribute(RET_ACTIVE_PERIOD_DURATION_SCALE,
                                String.valueOf(_definition.getActivePeriodDurationScale().getMessageKey()));
        }
        
        if (_definition.isChallengePeriodEnabled()) {
            _result.setAttribute(RET_CHALLENGE_PERIOD_DURATION_AMOUNT,
                                String.valueOf(_definition.getChallengePeriodDurationAmount()));
            
            if (_definition.getChallengePeriodDurationScale() != null) {
                _result.setAttribute(RET_CHALLENGE_PERIOD_DURATION_SCALE,
                                    String.valueOf(_definition.getChallengePeriodDurationScale().getMessageKey()));
            }
        }
        
        if (_definition.isRemediationPeriodEnabled()) {
            _result.setAttribute(RET_REMEDIATION_PERIOD_DURATION_AMOUNT,
                                String.valueOf(_definition.getRemediationPeriodDurationAmount()));
            
            if (_definition.getRemediationPeriodDurationScale() != null) {
                _result.setAttribute(RET_REMEDIATION_PERIOD_DURATION_SCALE,
                                    String.valueOf(_definition.getRemediationPeriodDurationScale().getMessageKey()));
            }
        }
    }
    
    /**
     * Set final state in the CertificationGroup.
     * Adapted from CertificationExecutor and BaseCertificationBuilder.finalize.
     *
     * BCB.finalize is called with a boolean "success" argument that is false
     * if CertificationExecutor caught an exception.  Here that is indiciated
     * by the non-nullness of errorMessage.  This was the only thing that
     * controlled the status being Error, which feels like it should be more
     * complicated.  We could have a tone of errors in the partition results.
     *
     * BSB.finalize is also called with a Message list that was built
     * by CertificationExecutor containing all of the error and warning messages
     * from the TaskResult.
     */
    public void finalizeGroup(Message exceptionMessage)
        throws GeneralException {

        List<Message> messages = new ArrayList<Message>();

        List<Message> errors = _result.getErrors();
        if (errors != null)
            messages.addAll(errors);

        List<Message> warnings = _result.getWarnings();
        if (warnings != null)
            messages.addAll(warnings);
        
        // executor would add exceptionMessage here, but that's
        // already in the TaskResult
        _group.addMessages(messages);

        CertificationGroup.Status status = CertificationGroup.Status.Active;
        if (exceptionMessage != null) {
            status = CertificationGroup.Status.Error;
        }
        else if (_definition.isStagingEnabled()) {
            status = CertificationGroup.Status.Staged;
        }
                
        _group.setStatus(status);
        _context.saveObject(_group);
        _context.commitTransaction();
    }

    /**
     * Refresh group statistics.
     * 
     * Copied from Certificationer since it isn't so big and we may want to 
     * adjust some things.  I'm not understanding how at this point anything
     * could be completed, do we even need this?  We've already got _totalCerts.
     * This is part of Certificadtioner.refresh so I see why it has to be more
     * general, but why just after generation?
     */
    private void refreshGroupStatistics()
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("certificationGroups.id", _group.getId()));

        // MT: group status is always Active, Staged or Error here, so no need to filter out empty certs

        List<String> projectionCols = new ArrayList<String>();
        projectionCols.add("count(id)");
        projectionCols.add("count(signed)");

        Iterator<Object[]> iter = _context.search(Certification.class, ops, projectionCols);
        if (iter != null && iter.hasNext()){
            Object[] results=iter.next();
            int total = ((Long)results[0]).intValue();
            int completed = ((Long)results[1]).intValue();
            
            int percentComplete = CertificationStatCounter.calculatePercentComplete(completed, total);

            // Don't overwrite an Error or Pending status regardless of completion status.
            // Pending cert groups will get set to Complete in BaseCertificationBuilder.finalize
            // Add an 'is continuous' check - don't set continuous certs to be complete
            CertificationGroup.Status status = _group.getStatus();
            if (percentComplete == 100 &&
                !CertificationGroup.Status.Error.equals(status) &&
                !CertificationGroup.Status.Pending.equals(status)) {
                _group.setStatus(CertificationGroup.Status.Complete);
            }

            _group.setCompletedCertifications(completed);
            _group.setTotalCertifications(total);
            _group.setPercentComplete(percentComplete);

            _context.saveObject(_group);
            _context.commitTransaction();
        }
    }
    
}
