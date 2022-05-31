/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Escalator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.PersistenceManager.LockParameters;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Notifiable;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * This task checks for Notifiables that require action and will send
 * reminders, escalate, and expire as appropriate.  This delegates the
 * meat of the logic to the Escalator.
 * 
 * TODO: Rename this class and the TaskDefinitions/TaskSchedules that
 * reference it.  Historically, WorkItems were the first object to be
 * subject to reminders, etc...  Now CertificationItems are also in the
 * mix, so this task is handling more than it used to.
 *
 * @author Bernie Margolis
 * @author Kelly Grizzle
 */
public class WorkItemExpirationScanner extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(WorkItemExpirationScanner.class);

    /**
     * Return arguments for this task, must match the Signature.
     * in init.xml
     */
    public static final String RET_TOTAL = "total";
    public static final String RET_TOTAL_WORK_ITEMS = "totalWorkItems";
    public static final String RET_TOTAL_CERT_ITEMS = "totalCertItems";
    public static final String RET_REMINDERS = "reminders";
    public static final String RET_EXPIRATIONS = "expirations";
    public static final String RET_ESCALATIONS = "escalations";
    public static final String RET_PUSHES = "pushes";
    public static final String RET_EMAILS_SUPPRESSED = "emailsSuppressed";

    private SailPointContext context;
    private Escalator escalator;

    private boolean isTerminated;

    private int total;
    private int totalWorkItems;
    private int totalCertItems;


    public WorkItemExpirationScanner() {
        isTerminated = false;
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        isTerminated = true;
        
        return isTerminated;
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and dissappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {
        
        try {
            processItems(context);
        }
        catch (Throwable t) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.MGR_EXECUTION_FAILED, t);
            result.addMessage(msg);
            log.error(msg.getMessage(), t);
        }

        result.setTerminated(isTerminated);
        result.setAttribute(RET_TOTAL, total);
        result.setAttribute(RET_TOTAL_WORK_ITEMS, totalWorkItems);
        result.setAttribute(RET_TOTAL_CERT_ITEMS, totalCertItems);
        result.setAttribute(RET_REMINDERS, escalator.getTotalReminders());
        result.setAttribute(RET_EXPIRATIONS, escalator.getTotalExpirations());
        result.setAttribute(RET_ESCALATIONS, escalator.getTotalEscalations());
        result.setAttribute(RET_PUSHES, escalator.getTotalPushes());
        result.setAttribute(RET_EMAILS_SUPPRESSED, escalator.getEmailsSuppressed());
    }

    /**
     * Process any item that needs to be processed.  This is public so that the
     * unit tests can use the scanner without having to run a task.
     */
    public void processItems(SailPointContext context) throws GeneralException {

        this.context = context;
        this.escalator = new Escalator(this.context);

        this.totalWorkItems = processItems(WorkItem.class);
        this.totalCertItems = processItems(CertificationItem.class);
        this.total = this.totalWorkItems + this.totalCertItems;
    }
    
    /**
     * Process items of the given class.  The type should extend Notifiable.
     */
    private int processItems(Class<? extends SailPointObject> clazz)
        throws GeneralException {

        int numProcessed = 0;

        // Get all items that are due to wake up.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.lt("wakeUpDate", DateUtil.getCurrentDate()));
        String[] props = new String [] { "id" };

        // Since this is potentially memory-intensive, only grab the IDs
        Iterator<Object[]> items = context.search(clazz, qo, Arrays.asList(props));
        
        // We need to copy the IDs to an array so that the SQL cursor doesn't get
        // screwed by workitems being modified or removed out from under it.
        List<String> idList = new ArrayList<String>();
        while (items.hasNext() && !this.isTerminated) {
            String id = (String) items.next()[0];
            idList.add(id);
        }
        
        int exceptions = 0;
        Iterator<String> ids = idList.iterator();
        while (ids.hasNext() && !this.isTerminated) {
            String id = ids.next();
            WorkItem workItem = null;
            WorkflowCase wfc = null;
            try {
                Notifiable item = (Notifiable) context.getObjectById(clazz, id);
            
                if (item != null) {
                    // Sanity check - this probably shouldn't happen since wake up
                    // date should get nulled out when we expire.
                    if ((item instanceof WorkItem)) {
                        if ((WorkItem.State.Expired.equals(((WorkItem) item).getState()))) {
                            log.debug("Work item is expired but is being processed for reminders and escalations: " + item);
                        }

                        workItem = (WorkItem)item;
                        //Lock the WorkItem
                        LockParameters lockParams = LockParameters.createById(item.getId(), PersistenceManager.LOCK_TYPE_PERSISTENT);
                        item = context.lockObject(WorkItem.class, lockParams);
    
                        wfc = ((WorkItem) item).getWorkflowCase();
                        if (wfc != null) {
                            //Lock the Workflow Case
                            //TOOD: We could be smarter about keeping a lock across all work items referencing the same workflow case.
                            //Not sure how often this happens tho -rap
                            LockParameters lockParamsWfc = LockParameters.createById(wfc.getId(), PersistenceManager.LOCK_TYPE_PERSISTENT);
                            wfc = context.lockObject(WorkflowCase.class, lockParamsWfc);
                        }
                    }
                }

                numProcessed++;
                escalator.handleNotification(item);
                context.commitTransaction();
                if (numProcessed % 20 == 0) {
                    // standard Hibernate procedure: decache as we iterate over large lists of objects
                    context.decache();
                }
            }
            catch (Throwable t) {
                // do not let failure on one object halt the task, need to keep going
                // though might want a threshold
                log.error("Exception during work item expiration",t);
                exceptions++;
                // on error, we should decache the context to purge unwanted leftovers for the next iteration
                context.decache();
            } finally {
                if (workItem != null) {
                    try {
                        workItem = context.getObjectById(WorkItem.class, workItem.getId());
                        ObjectUtil.unlockObject(context, workItem, PersistenceManager.LOCK_TYPE_PERSISTENT);
                        workItem = null;
                    } catch (Exception e) {
                        log.error("Error while unlocking workItem" + workItem.getId(), e);
                    }
                }
                if (wfc != null) {
                    try {
                        wfc = context.getObjectById(WorkflowCase.class, wfc.getId());
                        ObjectUtil.unlockObject(context, wfc, PersistenceManager.LOCK_TYPE_PERSISTENT);
                        wfc = null;
                    } catch (Exception e) {
                        log.error("Error while unlocking workflowCase" + wfc.getId(), e);
                    }
                }
            }
        }

        log.trace("The work item expiration scanner processed " + numProcessed + " " + clazz);
        if (exceptions > 0)
            log.info("The work item expiration scanner had " + exceptions + " " + clazz + " exceptions");

        return numProcessed;
    }
}
