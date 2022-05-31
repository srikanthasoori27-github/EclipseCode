/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * The LCMProvisioningScanner is in charge of looking for 
 * requests made through LCM and checking the status
 * of each item in the cart.  Each item is represented
 * by and ApprovalItem, which has a plan that will be 
 * executed to fulfill the request.
 * 
 * As of 5.5 this class has been depreciated and replaced
 * with functionality added in api.IdentityRequestProvisioningScanner.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@Deprecated
public class LCMProvisioningScanner {

    private static final Log log = LogFactory.getLog(LCMProvisioningScanner.class);
    
    private SailPointContext _context;

    /**
     * Object in charge of checking if things requested have been
     * reflected on the Identity.
     */
    private ProvisioningChecker _checker;

    /**
     * Constructor
     */
    public LCMProvisioningScanner(SailPointContext ctx) {
        _context = ctx;
        _checker = new ProvisioningChecker(ctx);
    }

    /**
     * Check an LCM TaskResult and check the provisioning status
     * for each of the requests that were made. Returns the 
     * number of ApprovalItems that were checked.
     */
    public int scan(TaskResult result) throws GeneralException {
        
        Attributes<String,Object> attrs = result.getAttributes();
        if ( attrs == null ) 
            attrs = new Attributes<String,Object>();

        // At this point we should have an identity, create and modify identity
        // requests are marked provisioning complete by the workflow so those
        // requests will already be marked finished
        Identity identity = getIdentity(result);
        if ( identity == null ) {
            // This would be unusual and unrecoverable
            if (log.isErrorEnabled())
                log.error("LCM task result's identity was null. '" + result.getName() + "'.");
            
            finishResult(result);
            return 0;
        }
        
        WorkflowSummary summary = (WorkflowSummary)attrs.get(WorkflowCase.RES_WORKFLOW_SUMMARY);
        if ( summary == null ) {
            // This would be unusual and unrecoverable
            if (log.isErrorEnabled())
                log.error("Workflow summary was null in LCM task result '" + result.getName() + "'.");
            
            finishResult(result);
            return 0;
        }
        
        ApprovalSet set = summary.getApprovalSet();
        if ( set == null ) {
            if (log.isErrorEnabled())
                log.error("LCM task result had null approval set. '" + result.getName() + "'.");
            
            finishResult(result);
            return 0;
        } 
        
        // if they are all rejected or already provisioned mark them verified
        // this happens first to avoid problems in the next check when
        // create requests are rejected
        if ( !set.hasApproved() || set.isAllProvisioned() ) {
            // no sense in going any further just mark it done
            finishResult(result);
            return 0;
        }        
        
        if ( Util.size(set.getApproved()) == 0 ) {
            if (log.isErrorEnabled())
                log.error("LCM TaskResult had an ApprovalSet which has no approval items. '" + 
                          result.getName() + "'.");
            
            finishResult(result);
            return 0;
        }
        
        // Check the approval items in the approval set 
        // to see if the changes have been applied to 
        // the identity
        int processed = checkApprovalSet(result, identity, set);        
        if ( result.isTerminated() || result.isError() ){
            if ( log.isDebugEnabled() ) 
                log.debug("Workflow was canceled or has errors. task result '" + result.getName() + "'.");
            
            if ( result.hasErrors() ) {
                auditTaskFailure(result, identity);
            }
            finishResult(result);            
        }   
        return processed;
    }
            
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Using the target Id and/or target name to resolve the Identity
     * we are updating. 
     */
    private Identity getIdentity(TaskResult result) 
        throws GeneralException { 

        Identity identity = null;
        String targetId = result.getTargetId();
        if ( targetId != null ) {
            identity = _context.getObjectById(Identity.class, targetId);
        } else {
            targetId = result.getTargetName();
            if (targetId != null) {
                identity = _context.getObjectByName(Identity.class, targetId);
            }
        }
        return identity;
    }

    /**
     * Mark the result completed and commit the change.
     */
    private void finishResult(TaskResult result) throws GeneralException {
        result.setVerified(new Date());
        _context.saveObject(result);
        _context.commitTransaction();
    }

    /**
     * Create an audit trail as we find items that have been provisioned.
     */
    private void auditCompletion(ApprovalItem item, TaskResult result, Identity identity) 
        throws GeneralException {

        if ( !Auditor.isEnabled(AuditEvent.ProvisioningComplete) ) 
            return;

        AuditEvent event = new AuditEvent();
        event.setApplication(item.getApplication());
        event.setAccountName(item.getNativeIdentity());
        event.setInstance(item.getInstance());
        event.setAction(AuditEvent.ProvisioningComplete);
        event.setAttribute("op", item.getOperation());
        event.setAttributeName(item.getName());
        event.setAttributeValue(item.getCsv());

        event.setTarget(identity.getName());
        event.setSource(result.getLauncher());
             
        event.setTrackingId((String)result.getAttribute(WorkflowCase.RES_WORKFLOW_PROCESS_ID)); 
        event.setAttribute(Workflow.VAR_TASK_RESULT, result.getId());

        // djs: for now set this in both places to avoid needing
        // to upgrade.  Once we have ui support for "interface"
        // we can remove the map version
        event.setAttribute("interface", Source.LCM.toString());
        event.setInterface(Source.LCM.toString());
        Auditor.log(event);
        _context.commitTransaction();
    }
    
    
    private void auditTaskFailure(TaskResult result, Identity identity) 
        throws GeneralException {

        if (!Auditor.isEnabled(AuditEvent.ProvisioningFailure))
            return;

        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.ProvisioningFailure);        
        event.setTarget(identity.getName());
        event.setSource(result.getLauncher());

        event.setTrackingId((String) result.getAttribute(WorkflowCase.RES_WORKFLOW_PROCESS_ID));
        event.setAttribute(Workflow.VAR_TASK_RESULT, result.getId());
                    

        // djs: for now set this in both places to avoid needing
        // to upgrade. Once we have ui support for "interface"
        // we can remove the map version
        event.setAttribute("interface", Source.LCM.toString());
        event.setInterface(Source.LCM.toString());
        
        List<Message> errors = result.getErrors();
        if ( Util.size(errors) > 0 ) {
            for ( int i=0; i<errors.size(); i ++ ) {
                Message message = (Message)errors.get(i);
                if ( message != null ) {
                    String localized = message.getLocalizedMessage();
                    if ( Util.getString(localized) != null ) {               
                        event.setAttribute("error"+(i+1), Util.listToCsv(errors));
                    }
                }
            }
        }
        Auditor.log(event);
        _context.commitTransaction();
    }
    
    /**
     * Go through the ApprovalSet's items and check the plan 
     * fragment stored on each item and see if it's been executed.
     * 
     * If it has been executed create an audit event to indicate
     * ProivisioningComplete. 
     * 
     * @param result
     * @param identity
     * @param set
     * @return
     * @throws GeneralException
     */
    private int checkApprovalSet(TaskResult result, Identity identity, ApprovalSet set)
        throws GeneralException {
        
        int checked = 0;        
        if ( set != null ) {            
            List<ApprovalItem> items = set.getApproved();
            boolean save = false;
            for ( ApprovalItem item : items ) {
                checked++;
                if ( !item.isProvisioningComplete() ) {
                    ProvisioningPlan plan  = item.getProvisioningPlan();
                    if ( plan == null ) {
                        // This would be unusual and unrecoverable
                        if (log.isErrorEnabled()) {
                            log.error("LCM task result approval item '" + item.toXml() + 
                                      "' is missing plan. Verification failed .'" +
                                      result.getName() +"'.");
                        }
                        
                        //TODO : do we need a state for failed to verify? Missing info?
                        continue;                        
                    }
                    if ( _checker.hasBeenExecuted(plan, identity) ) {
                        item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
                        auditCompletion(item, result, identity);
                        save = true;
                    } else {
                        if ( log.isDebugEnabled() ) {
                            log.debug("LCM TaskResult Plan has not been executed\n" +
                                      plan.toXml()+"]\n" + identity.toXml());
                        }
                    }
                }
            }
            
            // If we completed or updated the item during this mark it verified
            if ( set.isAllProvisioned() ) {
                result.setVerified(new Date());
                save = true;
            }

            if ( save ) {
                _context.saveObject(result);                
                _context.commitTransaction();
            }
          
        }
        return checked;
    }


}
