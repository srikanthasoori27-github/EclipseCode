/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.Filter;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Pulled out the certification finishing process from the Housekeeper class
 * so that it can be multi-threaded.
 * 
 * @author patrick.jeong
 *
 */
public class CertificationFinisher {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configured number of threads to use
     */
    public static final String ARG_FINISHER_THREADS = "finisherThreads";
    /**
     * Default number of threads to use
     */
    public static final int DEFAULT_FINISHER_THREADS = 1;
    /**
     * Max number of threads to use
     */
    public static final int MAX_FINISHER_THREADS = 10;

    /**
     * Enables running trace messages to stdout. 
     */
    public static final String ARG_TRACE = AbstractTaskExecutor.ARG_TRACE;

    /**
     * Result value
     */
    public static final String RET_CERTS_FINISHED = "certificationsFinished";
    public static final String RET_CERT_FINISH_FAILURES = "certificationsFinishFailures";

    private static Log log = LogFactory.getLog(CertificationFinisher.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private ExecutorService certificationFinishExecutor;
    private SailPointContext context;
    private Attributes<String,Object> args;
    private boolean terminate;
    private boolean trace;

    /**
     * Message holder
     */
    private BasicMessageRepository finisherMessages;
    private int certificationsFinished;
    private int certificationFinishFailures;


    /**
     * Constructor
     */
    public CertificationFinisher(SailPointContext context, Attributes<String, Object> args) {
        this.context = context;
        this.args = args;
        this.finisherMessages = new BasicMessageRepository();
    }

    /**
     * Each callable task will return one of these results
     * 
     * @author patrick.jeong
     *
     */
    class CertificationFinishResult {
        /**
         * Certification Id for which this result is for
         */
        String certId;

        /**
         * Message holder
         */
        BasicMessageRepository resultMessages;

        /**
         * Did the certification finish successfully?
         */
        boolean success;

        boolean finishRequired = true;

        /**
         */
        public CertificationFinishResult() {}

        public boolean isFinishRequired() {
            return finishRequired;
        }

        public void setFinishRequired(boolean required) {
            finishRequired = required;
        }

        public String getCertId() {
            return certId;
        }
        public void setCertId(String certId) {
            this.certId = certId;
        }
        public boolean isSuccess() {
            return success;
        }
        public void setSuccess(boolean success) {
            this.success = success;
        }
        public BasicMessageRepository getResultMessages() {
            return resultMessages;
        }
        public void setResultMessages(BasicMessageRepository messages) {
            this.resultMessages = messages;
        }
        public void addMessages(List<Message> messages) {
            if (messages == null) return;

            initMessages();

            resultMessages.addMessages(messages);
        }
        public void addMessage(Message message) {
            if (message == null)
                return;

            initMessages();
            resultMessages.addMessage(message);

            if (Message.Type.Error.equals(message.getType())){
                log.error(message.getMessage());
            } else if (Message.Type.Warn.equals(message.getType())){
                log.warn(message.getMessage());
            }
        } 
        private void initMessages(){
            if (resultMessages == null)
                resultMessages = new BasicMessageRepository();
        }
    }

    /**
     * Callable inner class that creates the Certificationer object
     * and executes finish().
     * 
     * Finishes a single certification in its own thread.
     * 
     * Will return a CertificationFinishResult that will contain
     * messages and a finish success flag.
     * 
     * @author patrick.jeong
     *
     */
    class CertificationFinisherWorker implements Callable<CertificationFinishResult> {

        String certificationId;
        String user;
        String certName;
        SailPointContext context;

        /**
         * Constructor
         * 
         * @param user
         * @param certificationId
         */
        CertificationFinisherWorker(String user, String certificationId) {
            this.certificationId = certificationId;
            this.user = user;
        }

        /**
         * Main callable method
         */
        public CertificationFinishResult call() {
            // Lock cert
            String lockType = SailPointContext.LOCK_TYPE_PERSISTENT;
            // Don't bother trying to get the lock again
            // just assume some other thread already is processing this cert
            int lockTimeout = 0;

            CertificationFinishResult finishResult = new CertificationFinishResult();
            if (!terminate) {
                Certification certification = null;
                try {
                    context = SailPointFactory.createContext(user);
                    // allow modifying signed certs since this is a system
                    // level deal
                    PersistenceOptions ops = new PersistenceOptions();
                    ops.setAllowImmutableModifications(true);
                    context.setPersistenceOptions(ops);
                    
                    certification = ObjectUtil.lockObject(context, 
                            Certification.class, 
                            certificationId, null, 
                            lockType,
                            lockTimeout);
                }
                catch (GeneralException e) {
                    // Create context failed?  Lock object failed?
                    finishResult.setSuccess(false);
                    String cause = "Another thread is processing the certification";
                    Message err = new Message(Message.Type.Warn, MessageKeys.ERR_CERT_LOCK_FAILED, certificationId, cause);

                    if (log.isWarnEnabled())
                        log.warn(err.getLocalizedMessage());

                    finishResult.addMessage(err);

                } finally {
                    // could get an error locking the cert or creating the context
                    // either way, the cert should be null and thus the later context release never happens
                    // be safe about it here
                    if (context != null && certification == null) { // got a context but never got a cert, release here
                        try {
                            SailPointFactory.releaseContext(context);
                        } catch (GeneralException ex) {
                            if (log.isWarnEnabled())
                                log.warn("Failed releasing SailPointContext: "
                                         + ex.getLocalizedMessage(), ex);
                        }
                    }
                }

                if (certification != null) {

                    finishResult.setCertId(certificationId);

                    if (certification.getFinished() != null) {
                        // something already finished this, let's call it "not required"
                        finishResult.setFinishRequired(false);
                        finishResult.setSuccess(true); // otherwise, it counts as a failure and I'm not sure this is a failure
                        
                        try {
                            SailPointFactory.releaseContext(context);
                        } catch (GeneralException ex) {
                            if (log.isWarnEnabled())
                                log.warn("Failed releasing SailPointContext: "
                                         + ex.getLocalizedMessage(), ex);
                        }
                    } else {

                        try {
                            Certificationer certificationer = new Certificationer(context);

                            boolean returnToParent = certificationer.isReturnToParent(certification);

                            // finish not required if returning to parent
                            finishResult.setFinishRequired(!returnToParent);

                            certificationer.finish(certification);

                            context.decache();

                            // The cert may have been deleted, so reload it and only
                            // advance phase if it is still around.
                            certification = context.getObjectById(Certification.class, certification.getId());

                            if (null != certification && !Phase.Remediation.equals(certification.getPhase())) {
                                // skip this if the phase is in remediation.  It has to be allow to run its natural life
                                certName = certification.getName();
                                // After finishing, we'll automatically advance to the
                                // remediation or end phase.
                                CertificationPhaser phaser = new CertificationPhaser(context, finishResult.getResultMessages());
                                phaser.advancePhase(certification);
                                context.commitTransaction();
                                context.decache(certification);
                            }

                            finishResult.setSuccess(true);

                            if (certificationer.getErrors() != null)
                                finishResult.addMessages(certificationer.getErrors());
                            if (certificationer.getWarnings() != null)
                                finishResult.addMessages(certificationer.getWarnings());
                        }
                        catch (Throwable t) {
                            log.error(t.getMessage(), t);
                            String name = certification.getName();
                            Message err = new Message(Message.Type.Error, MessageKeys.ERR_INDIVIDUAL_CERT_FINISH_FAILED,
                                    certification.getId(), name != null ? name : "");
                            finishResult.addMessage(err);
                            finishResult.setSuccess(false);
                        }
                        finally {
                            try {
                                if (certification != null && certification.getLock() != null) {
                                    certification.setLock(null);
                                    context.saveObject(certification);
                                }
                                context.commitTransaction(); 
                                context.decache();
                                SailPointFactory.releaseContext(context);
                            }
                            catch (Throwable t) {
                                log.error("Unable to unlock certification during exception recovery", t);
                            }
                        }
                    }
                }
            } else {
                // terminated
                Message terminated = new Message(Message.Type.Warn, MessageKeys.WARN_TERMINATED_FINISHING_TASK,
                        certificationId);
                finishResult.addMessage(terminated);
                finishResult.setSuccess(false); // not successful?
            }
            return finishResult;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Main interface methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     */
    public void execute() {

        // Setup some args
        trace = args.getBoolean(ARG_TRACE, false);
        int numThreads = args.getInt(ARG_FINISHER_THREADS);

        if (numThreads <= 0) {
            numThreads = DEFAULT_FINISHER_THREADS;
        }
        else if (numThreads > MAX_FINISHER_THREADS) {
            numThreads = MAX_FINISHER_THREADS;
        }

        // Get certification ids that are ready for finishing
        Iterator<Object[]> certIdIterator = null;
        try {
            certIdIterator = getCertificationsThatNeedFinishing();
        }
        catch(GeneralException ge) {
            // Problem getting our starting data set
            // Cleanup and return
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_CERT_FINISH_FAILED, ge);
            finisherMessages.addMessage(err);
        }

        // Throw ready certifications at the finisher executor
        if (certIdIterator == null || !certIdIterator.hasNext()) {
            // No certifications to finish
        }
        else {
            // Setup the threadpoolexecuter
            CompletionService<CertificationFinishResult> ecs = null;

            certificationFinishExecutor = Executors.newFixedThreadPool(numThreads);
            ecs = new ExecutorCompletionService<CertificationFinishResult>(certificationFinishExecutor);

            // Let's count what's actually sent to be finished
            int certificationsActuallyFinished = 0;
            while (certIdIterator.hasNext() && ! terminate) {
                // Get the certification id of the certification to finish
                String id = (String)(certIdIterator.next()[0]);
                ecs.submit(new CertificationFinisherWorker(context.getUserName(), id));
                certificationsActuallyFinished++;
            }

            // Wait for results and total them
            try {
                // and then iterate over what's actually been sent for finishing
                for (int i=0; i<certificationsActuallyFinished; ++i) {
                    CertificationFinishResult result  = ecs.take().get();
                    if (result != null) {
                        if (!result.isSuccess())
                            certificationFinishFailures++;
                        else  if (result.isSuccess() && result.isFinishRequired())  
                            certificationsFinished++;

                        // Add messages
                        BasicMessageRepository resultMsgs = result.getResultMessages();
                        if (resultMsgs != null) {
                            finisherMessages.addMessages(result.getResultMessages().getMessages());
                        }
                    }
                }
            }
            catch(InterruptedException ie) {
                // Interrupted while waiting
                log.error(ie.getMessage(), ie);
            }
            catch(ExecutionException ee) {
                // Processing threw exception
                log.error(ee.getMessage(), ee);
            }

            // shut it down
            if (certificationFinishExecutor != null) {
                certificationFinishExecutor.shutdown();  
            }
        }
    }

    /**
     * Stop processing thread.
     * 
     * @return
     */
    public boolean terminate() {
        terminate = true;
        return true;
    }

    /**
     * Add this tasks results
     * 
     * @param result
     */
    public void addTaskResults(TaskResult result) {
        result.setAttribute(RET_CERTS_FINISHED, Util.itoa(certificationsFinished));
        result.setAttribute(RET_CERT_FINISH_FAILURES, Util.itoa(certificationFinishFailures));
        result.addMessages(finisherMessages.getMessages());
    }

    private Iterator<Object[]> getCertificationsThatNeedFinishing() throws GeneralException {

        QueryOptions ops = new QueryOptions();

        // Look for certs that are signed but not finished.
        ops.add(Filter.and(
                Filter.isnull("finished"),
                Filter.notnull("signed"), 
                Filter.ne("phase", "Staged")));
        // For consistency, process the certs in the order they were signed.
        ops.addOrdering("signed", true);

        List<String> props = new ArrayList<String>();
        props.add("id");

        // Bug 10513 -- read and exhaust the db cursor to avoid keeping it open for too long
        Iterator<Object[]> it = context.search(Certification.class, ops, props);
        List<Object[]> fullResults = new ArrayList<Object[]>();
        while (it.hasNext()) {
            fullResults.add(it.next());
        }

        return fullResults.iterator();
    }

    public int getCertificationsFinished() {
        return certificationsFinished;
    }

    public void setCertificationsFinished(int certificationsFinished) {
        this.certificationsFinished = certificationsFinished;
    }

}
