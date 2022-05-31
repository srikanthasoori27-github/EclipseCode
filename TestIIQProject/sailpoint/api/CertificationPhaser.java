/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.ActivePhaseHandler;
import sailpoint.api.certification.ChallengePhaseHandler;
import sailpoint.api.certification.FinishPhaseHandler;
import sailpoint.api.certification.RemediationPhaseHandler;
import sailpoint.api.certification.StagedPhaseHandler;
import sailpoint.object.AuditEvent;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.Filter;
import sailpoint.object.Phaseable;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.server.Auditor;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;


/**
 * The CertificationPhaser is used to perform business logic around phase
 * transitions in a certification (eg - a certification becoming active,
 * challenge period beginning, etc...), and refreshing when a certification is
 * in a given phase.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationPhaser {

    private static final Log log = LogFactory.getLog(CertificationPhaser.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER INTERFACES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A PhaseHandler is an internal interface that provides behavior around
     * certification phase transitions. Some PhaseHandlers may need to decache
     * in order to properly process their work. Reattached objects should be
     * returned
     */
    public static interface PhaseHandler {

        /**
         * Perform the business logic that should occur when entering the phase
         * handled by this PhaseHandler.
         * 
         * @param  phaseable  The Phaseable which is entering this phase.
         * @return The same Phaseable in good standing with the SailPointContext.
         *      This may be a different object.
         */
        public Phaseable enterPhase(Phaseable phaseable) throws GeneralException;

        /**
         * Perform the business logic that should occur when exiting the phase
         * handled by this PhaseHandler.
         * 
         * @param  phaseable  The Phaseable which is exiting this phase.
         * @return The same Phaseable in good standing with the SailPointContext.
         *      This may be a different object.
         */
        public Phaseable exitPhase(Phaseable phaseable) throws GeneralException;

        /**
         * Perform the business logic that should occur after all items the
         * given certification have entered into this phase.  This is used when
         * transitioning items to allow some operations to be batched and
         * executed after all items in the certification have done their
         * transitioning.  This is only used when transitioning certification
         * items (ie - not certifications).
         * 
         * @param  cert  The certification for which to run the batched post
         *               entry business logic.
         * @return The same Certification in good standing with the SailPointContext.
         *      This may be a different object.
         */
        public Certification postEnter(Certification cert) throws GeneralException;

        /**
         * Perform the business logic that should occur after all items the
         * given certification have exited into this phase.  See postEnter()
         * javadoc for more explanation.
         * 
         * @param  cert  The certification for which to run the batched post
         *               exit business logic.
         * @return The same Certification in good standing with the SailPointContext.
         *      This may be a different object.
         */
        public Certification postExit(Certification cert) throws GeneralException;


        /**
         * Return whether the phase handled by this PhaseHandler should be
         * skipped for the given phaseable.  Some phases can be skipped even
         * if they are enabled.  For example, if the certification is signed and
         * there are no remediations, the challenge phase can be skipped.
         * 
         * @param  phaseable  The Phaseable to check.
         * 
         * @return True if the phase should be skipped, false otherwise.
         */
        public boolean isSkipped(Phaseable phaseable) throws GeneralException;


        /**
         * Refresh the given CertificationItem in the Certification according
         * to the rules of this phase.
         * 
         * @param  cert  The Certification in which the entity lives.
         * @param  item  The CertificationItem to refresh.
         */
        public void refresh(Certification cert, CertificationItem item)
            throws GeneralException;


        /**
         * Transition the given item if it should leave the current state -
         * either advance or rewind.
         * 
         * @param  item    The item that should possibly be transitioned.
         * @param  phaser  The phaser to use to do the transitioning.
         */
        public void handleRollingPhaseTransition(CertificationItem item,
                                                 CertificationPhaser phaser)
            throws GeneralException;

        /**
         * Return whether entering this phase should set a next phase transition
         * date on the phaseable.  This should return false if the phase config
         * has a duration and that duration should not be used for timing
         * phases.  The only example now is that Active phase has a duration for
         * periodic certs, but this should not be used if the cert uses rolling
         * phases.
         * 
         * @param  phaseable  The phaseable that is being transitioned.
         */
        public boolean updateNextPhaseTransition(Phaseable phaseable);
    }

    public interface IPostExitAndEnterHandler {
        void postExitAndEnter(Certification cert, Collection<Certification.Phase> exited, Collection<Certification.Phase> entered);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static final String RET_CERTS_PHASED = "certificationsPhased";
    public static final String RET_CERT_ITEMS_PHASED = "certificationItemsPhased";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private MessageRepository errorHandler;
    private Monitor monitor;
    private EmailSuppressor emailSuppressor;
    private IPostExitAndEnterHandler postExitAndEnterHandler;
    private Map<Certification.Phase, PhaseHandler> handlerMap;

    private boolean terminate;
    
    private int certificationsPhased;
    private int certificationItemsPhased;
    private CertificationService certService;
    private Set<Certification.Phase> skippedPhases;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public CertificationPhaser(SailPointContext ctx,
                               MessageRepository errorHandler) {
        this(ctx, errorHandler, null);
    }

    /**
     * Constructor.
     */
    public CertificationPhaser(SailPointContext ctx,
                               MessageRepository errorHandler,
                               EmailSuppressor emailSuppressor) {
        this(ctx, errorHandler, emailSuppressor, null);
    }
    
    /**
     * Constructor. This is used by unit test.
     */
    public CertificationPhaser(SailPointContext ctx,
                               MessageRepository errorHandler,
                               EmailSuppressor emailSuppressor,
                               IPostExitAndEnterHandler postExitAndEnterHandler) {
        this.context = ctx;
        this.errorHandler = errorHandler;
        this.emailSuppressor = emailSuppressor;
        this.postExitAndEnterHandler = postExitAndEnterHandler;
        this.handlerMap = new HashMap<Certification.Phase, PhaseHandler>();
        this.certService = new CertificationService(ctx);
        this.skippedPhases = new HashSet<Certification.Phase>();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // TASK METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    public void terminate() {
        this.terminate = true;
    }
    
    public void saveResults(TaskResult result) {
        result.setAttribute(RET_CERTS_PHASED, this.certificationsPhased);
        result.setAttribute(RET_CERT_ITEMS_PHASED, this.certificationItemsPhased);

        audit(AuditEvent.ActionCertificationsPhased, this.certificationsPhased);
        audit(AuditEvent.ActionCertificationItemsPhased, this.certificationItemsPhased);
    }

    private void audit(String action, int statistic) {
        if (statistic > 0)
            Auditor.log(action, statistic);
    }

    public void traceResults() {
        System.out.println(this.certificationsPhased + " certifications phased");
        System.out.println(this.certificationItemsPhased + " certification items phased");
    }

    private void updateProgress(String msg) throws GeneralException {
        if (null != this.monitor) {
            this.monitor.updateProgress(msg);
        }
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Find all phaseables that are due to be transitioned and transition them.
     * This method should be the entry point for the tasks want to transition
     * all phaseable objects.
     */
    public void transitionDuePhaseables(Monitor monitor) throws GeneralException {
        
        this.monitor = monitor;

        QueryOptions ops = new QueryOptions();

        // Look for certs that are past their nextPhaseTransition date.
        ops.add(Filter.lt("nextPhaseTransition", new Date()));
                
        int certTotal = context.countObjects(Certification.class, ops);
        int certCount = 1;
        
        List<String> ids = ObjectUtil.getObjectIds(this.context, Certification.class, ops);
        if (ids == null) {
        	ids = new ArrayList<String>();
        }
        
        // Handle certifications.
        Iterator<String> it = ids.iterator();
     
        while (it.hasNext() && !this.terminate) {
            String id = it.next();
            final Certification cert = 
                this.context.getObjectById(Certification.class, id);

            if (cert != null) {
                updateProgress("Transitioning phase for access review " + certCount + " of " + certTotal + ": " + cert.getName());
                Callable<Void> doWhat = new Callable<Void>() {
                    
                    public Void call() throws Exception {
                
                        advancePhase(ObjectUtil.reattach(context, cert));
                        
                        return null;
                    }
                };
                ObjectUtil.doWithCertLock(context, cert, doWhat, true, 0);
    
                this.context.decache();
    
                this.certificationsPhased++;
                certCount++;
            }
        }

        // Handle CertificationItems for rolling phases.  We order these by
        // certification, so we can do the postPhase() handling grouped by
        // certification.
        ops.addOrdering("parent.certification.id", true);
        
        ids = ObjectUtil.getObjectIds(this.context, CertificationItem.class, ops);
        if (ids == null) {
        	ids = new ArrayList<String>();
        }
        it = ids.iterator();

        final Collection<Certification.Phase> phasesEntered =
            new HashSet<Certification.Phase>();
        final Collection<Certification.Phase> phasesExited =
            new HashSet<Certification.Phase>();
        
        final ChangeTracker<Certification> certChangeTracker = new ChangeTracker<Certification>();
        Certification locked = null;
        while (it.hasNext() && !this.terminate) {
            String id = it.next();
            CertificationItem certItem = this.context.getObjectById(CertificationItem.class, id);

            // Locking mechanism is a little tricky please be careful
            // when making modifications.
            if (certItem != null) {
                // Make sure the cert is at least minimally loaded before we
                // potentially decache it.
                Certification cert = certItem.getCertification();
                
                // Call reattach here to shallowly initialize the cert (load
                // at least the ID and class).  Otherwise, if this cert was
                // previously partially loaded by reference, the call to
                // reattach the cert below will throw a lazy initialization
                // exception.
                cert = ObjectUtil.reattach(context, cert);

                certChangeTracker.setCurrent(cert);
                if (certChangeTracker.hasNewStarted()) {
                    if (locked != null) {
                        // release the last lock
                        ObjectUtil.unlockCert(context, locked);

                        // Unlocking decaches, so reattach the objects.
                        cert = ObjectUtil.reattach(context, cert);
                        certItem = ObjectUtil.reattach(context, certItem);
                    }

                    // obtain new lock
                    locked = ObjectUtil.lockCert(context, cert, 0);
                    if (locked == null) {
                        continue;
                    }
                } else {
                    // if there, that means we are in a cert item which
                    // belongs to another cert.
                    
                    if (locked == null) {
                        // if we couldn't lock it earlier
                        // no point in trying again
                        continue;
                    }
                }

                try {
                    // If we've moved on to processing a new cert, postEnter the
                    // last cert that we were dealing with.
                    if (certChangeTracker.hasFinishedLast()) {
                        // this cert is guaranteed to be different than the previous one that we
                        // are already locking on this is "last" and the earlier is "current"
                        ObjectUtil.doWithCertLock(context, certChangeTracker.getLast(), new Callable<Boolean>() {
                            public Boolean call() throws Exception {
                                postExitAndEnter(ObjectUtil.reattach(context, certChangeTracker.getLast()), phasesExited, phasesEntered);
                                return true;
                            }
                        }, true, 0);
                        
                    }
                    
                    //MEH 16785, cert can be null here, no assumptions
                    if(cert != null) {
                        updateProgress("Transitioning certification item on: " + cert.getName());
                    }
                    this.advancePhase(ObjectUtil.reattach(context, certItem), phasesExited, phasesEntered);
                    this.certificationItemsPhased++;
                    // We may want to batch this up instead of doing it
                    // for every item
                    this.context.commitTransaction();
                    this.context.decache();

                } finally {
                    if (locked != null) {
                        ObjectUtil.unlockCert(context, locked);
                    }
                }
                
                certChangeTracker.setLast(cert);
            }
        }

        if (locked != null) {
            postExitAndEnter(ObjectUtil.reattach(context, certChangeTracker.getLast()), phasesExited, phasesEntered);
            ObjectUtil.unlockCert(context, locked);
        }
    }
    
    /**
     * 
     * In the beginning of a loop we set current.
     * In the end of a loop we set last.
     * This class will tell us when a new item
     * has started or when the current item has finished.
     *
     */
    private class ChangeTracker<T> {
        
        private T current;
        private T last;
        
        @SuppressWarnings("unused")
        public T getCurrent() {
        
            return current;
        }
        
        public void setCurrent(T current) {
        
            this.current = current;
        }
        
        public T getLast() {
        
            return last;
        }
        
        public void setLast(T last) {
        
            this.last = last;
        }
        
        @SuppressWarnings("unused")
        public boolean hasNewStarted() {

            if (last == null) {
                return true;
            }
            
            return !last.equals(current);
        }
        
        public boolean hasFinishedLast() {
            
            if (last == null) {
                return false;
            }
            
            return !last.equals(current);
        }
    }
    
    private void postExitAndEnter(Certification cert,
                                  Collection<Certification.Phase> exited,
                                  Collection<Certification.Phase> entered)
        throws GeneralException {

        if (postExitAndEnterHandler != null) {
            postExitAndEnterHandler.postExitAndEnter(cert, exited, entered);
        } else {
            postExit(cert, exited);
            postEnter(cert, entered);
            exited.clear();
            entered.clear();
        }
    }
    
    /**
     * Run post enter on the given certification for the given phases.
     */
    private void postEnter(Certification cert, Collection<Certification.Phase> entered)
        throws GeneralException {
        
        for (Certification.Phase phase : entered) {
            PhaseHandler handler = getPhaseHandler(phase);
            handler.postEnter(cert);
        }
    }

    /**
     * Run post exit on the given certification for the given phases.
     */
    private void postExit(Certification cert, Collection<Certification.Phase> exited)
        throws GeneralException {
        
        for (Certification.Phase phase : exited) {
            PhaseHandler handler = getPhaseHandler(phase);
            handler.postExit(cert);
        }
    }

    /**
     * Transition the given phaseable (not including child certifications)
     * to the next phase.
     * 
     * @param  phaseable The Phaseable to transition.
     */
    public Phaseable advancePhase(Phaseable phaseable) throws GeneralException {

        Certification.Phase currentPhase = phaseable.getPhase();
        Certification.Phase nextPhase = getNextPhase(phaseable);
        return changePhase(phaseable, currentPhase, nextPhase);
    }

    /**
     * Transition the given phaseable to the next phase.  This saves the phase
     * that was exited and the phase that was entered in the given collections
     * so that postEnter() and postExit() can be called later.
     * 
     * @param  phaseable     The object to transition.
     * @param  exited   A collection to which to add the exited phase.
     * @param  entered  A collection to which to add the entered phase.
     */
    private void advancePhase(Phaseable phaseable,
                              Collection<Certification.Phase> exited,
                              Collection<Certification.Phase> entered)
        throws GeneralException {

        Certification.Phase currentPhase = phaseable.getPhase();
        Certification.Phase nextPhase = getNextPhase(phaseable);

        // Save the phases that we enter and exit for this cert.
        if ((null != exited) && (null != currentPhase)) {
            exited.add(currentPhase);
        }
        if (null != entered) {
            entered.add(nextPhase);
        }
        
        changePhase(phaseable, currentPhase, nextPhase);
    }
    
    /**
     * Transition the given phaseable (not including child certifications)
     * to the previous phase.
     * 
     * @param  phaseable The Phaseable to transition.
     */
    public void rewindPhase(Phaseable phaseable) throws GeneralException {

        Certification.Phase currentPhase = phaseable.getPhase();
        Certification.Phase prevPhase = getPreviousPhase(phaseable);
        changePhase(phaseable, currentPhase, prevPhase);
    }

    /**
     * Change from the given current phase to the given new phase for the given
     * Phaseable.
     */
    public Phaseable changePhase(Phaseable phaseable,
                            Certification.Phase currentPhase,
                            Certification.Phase newPhase)
        throws GeneralException {
        
        // Exit the current phase (can be null if the certification is not active).
        if (null != currentPhase) {
            PhaseHandler handler = getPhaseHandler(currentPhase);
            if (null != handler) {
                Meter.enter(150, "CertificationPhaseHandler - Exit Phase");
                phaseable = handler.exitPhase(phaseable);

                // Set the phase before we postExit().  If this isn't changed,
                // this can get in an infinite loop calling back to
                // Certificationer.refresh() and handling rolling phases.
                phaseable.setPhase(newPhase);
                handler.postExit(phaseable.getCertification());
                Meter.exit(150);
            }
        }

        // Advance the phase on the certification.
        phaseable.setPhase(newPhase);
        this.context.saveObject((SailPointObject)phaseable);
        
        // Enter the next phase.
        Date nextPhaseTransition = null;
        if (null != newPhase) {
            PhaseHandler handler = getPhaseHandler(newPhase);
            if (null != handler) {
                Meter.enter(151, "CertificationPhaseHandler - Enter next phase");
                phaseable = handler.enterPhase(phaseable);
                handler.postEnter(phaseable.getCertification());
                Meter.exit(151);
            }

            // If we're not supposed to increment the next phase transition,
            // just leave the nextPhaseTransition as null.
            Meter.enter(152, "CertificationPhaseHandler - Update next phase transition");
            if (handler.updateNextPhaseTransition(phaseable)) {

                // If there is no config (ie - for the End phase) we default to a
                // null nextPhaseTransition).  Also, make sure that the phase has
                // a duration (not the case always for continuous certs).
                CertificationPhaseConfig config =
                    phaseable.getCertification().getPhaseConfig(newPhase);
                if ((null != config) && (null != config.getDuration())) {
    
                    // Next phase transition should be calculated from the time
                    // of this phase transition unless it is not available.
                    Date thisPhaseTransition = phaseable.getNextPhaseTransition();
                    if (null == thisPhaseTransition) {
                        thisPhaseTransition = new Date();
                    }

                    nextPhaseTransition =
                        config.getDuration().addTo(thisPhaseTransition);
                }
            }
            Meter.exit(152);
        }

        // Set the next phase transition date (can be null if this is the last phase).
        phaseable.setNextPhaseTransition(nextPhaseTransition);
        this.context.saveObject((SailPointObject) phaseable);
        return phaseable;
    }

    /**
     * Transition any items on the given entity if they should leave the current
     * state - either advance or rewind the phase.  This only rolls the phase if
     * the certification is using rolling phases.
     * 
     * @param  cert    The Certification that the entity lives in.
     * @param  entity  The entity that should possibly be transitioned.
     * @param refreshAll 
     */
    public void handleRollingPhaseTransitions(Certification cert,
                                              CertificationEntity entity, boolean refreshAll)
        throws GeneralException {
        Iterator<CertificationItem> itemsIt = certService.getItemsToRefresh(entity, refreshAll);
        if (cert.isUseRollingPhases() && (null != itemsIt)) {
            while (itemsIt.hasNext()) {
                CertificationItem item = itemsIt.next();
                PhaseHandler handler = getPhaseHandler(item.getPhase());
                if (null != handler) {
                    handler.handleRollingPhaseTransition(item, this);
                }
            }
        }
    }
    
    /**
     * Refresh the given CertificationEntity in the Certification using the
     * appropriate PhaseHandler.  This is given a single entity because the
     * Certificationer handles deciding which entities to refresh (based on
     * which ones are marked for refresh) and handles iterating over these
     * entities and refreshing them.
     * 
     * @param  cert    The Certification in which the entity lives..
     * @param  entity  The CertificationEntity to refresh.
     * @param refreshAll 
     */
    public void refresh(Certification cert, CertificationEntity entity, boolean refreshAll)
        throws GeneralException {
    
        Iterator<CertificationItem> itemsIt = certService.getItemsToRefresh(entity, refreshAll);
        while (itemsIt != null && itemsIt.hasNext()) {
            CertificationItem item = itemsIt.next();
            // Get phase off the items or cert - it will be on the item for
            // rolling challenge/remediation periods.  This assumes that the
            // item or cert is already in the desired state when the refresh
            // happens.
            Certification.Phase currentPhase =
                (null != item.getPhase()) ? item.getPhase() : cert.getPhase();
            if (null != currentPhase) {
                PhaseHandler handler = getPhaseHandler(currentPhase);
                if (null != handler) {
                    handler.refresh(cert, item);
                }
            }
        }
    }

    /**
     * Get the next enabled phase for the given Phaseable that is not being
     * skipped according to its PhaseHandler.
     * 
     * @param  phaseable  The Phaseablefor which to get the next phase.
     * 
     * @return The next enabled phase for the given Phaseable that is not
     *         being skipped, or null.
     */
    public Certification.Phase getNextPhase(Phaseable phaseable)
        throws GeneralException {
        
        Certification.Phase phase = null;

        boolean skip = false;

        // Ye ol' do...while loop doesn't get enough play.  Let's let him
        // out of his cage for a while (no pun intended).
        do {
            phase = phaseable.getNextPhase();

            if (null != phase) {
                PhaseHandler handler = getPhaseHandler(phase);
                skip = handler.isSkipped(phaseable);
                if (skip) {
                    phaseable.setPhase(phase);
                    skippedPhases.add(phase);
                    if (log.isDebugEnabled()) {
                        log.debug("The CertificationPhaser is skipping the " + phase.name() + " phase.");
                    }
                }
            }
        } while (skip && (null != phase));

        return phase;
    }

    /**
     * Check whether the specified phase has been skipped by the phaser
     * @param phase
     * @return true if the specified phase has been skipped; false otherwise
     */
    public boolean isSkipped(Certification.Phase phase) {
        return skippedPhases.contains(phase);
    }

    /**
     * Get the previous enabled phase for the given Phaseable that is not being
     * skipped according to its PhaseHandler.
     * 
     * @param  phaseable  The Phaseable for which to get the previous phase.
     * 
     * @return The previous enabled phase for the given Phaseable that is not
     *         being skipped, or null.
     */
    public Certification.Phase getPreviousPhase(Phaseable phaseable)
        throws GeneralException {
        
        Certification.Phase phase = null;
        // keep track of what it was
        Certification.Phase current = phaseable.getPhase();

        boolean skip = false;

        // Ye ol' do...while loop doesn't get enough play.  Let's let him
        // out of his cage for a while (no pun intended).
        do {
            phase = phaseable.getPreviousPhase();

            if (null != phase) {
                PhaseHandler handler = getPhaseHandler(phase);
                skip = handler.isSkipped(phaseable);
                if (skip) {
                    phaseable.setPhase(phase);
                }
            }
        } while (skip && (null != phase));

        // in the process, we probably clobbered the phase.  Reset it
        phaseable.setPhase(current);
        return phase;
    }

    /**
     * Get the PhaseHandler for the requested phase.
     * 
     * @param  phase  The Phase for which to retrieve the handler.
     * 
     * @return The PhaseHandler for the requested phase.
     */
    private PhaseHandler getPhaseHandler(Certification.Phase phase)
        throws GeneralException {

        // Try from the handlerMap first. This avoids expensive instantiation
        // time.
        PhaseHandler handler = this.handlerMap.get(phase);
         
        if (handler == null) {
             switch (phase) {
             case Staged: handler = new StagedPhaseHandler(this.context, this.errorHandler, this.emailSuppressor); break;
             case Active: handler = new ActivePhaseHandler(this.context, this.errorHandler, this.emailSuppressor); break;
             case Challenge: handler = new ChallengePhaseHandler(this.context, this.errorHandler, this.emailSuppressor); break;
             case Remediation: handler = new RemediationPhaseHandler(this.context, this.errorHandler, this.emailSuppressor); break;
             case End: handler = new FinishPhaseHandler(this.context, this.errorHandler, this.emailSuppressor); break;
             }
             this.handlerMap.put(phase, handler);
        }

        return handler;
    }
}
