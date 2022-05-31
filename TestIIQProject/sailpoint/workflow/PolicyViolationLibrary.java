/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A workflow library with methods related to policy violation workflows.
 *
 * Author: Jeff
 *
 * Categories of services:
 *
 * LCM Audit Events
 *    specially constructed audit events for use in the request status 
 *    dashboard and reports
 *
 */


package sailpoint.workflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.certification.PolicyViolationCertificationManager;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SailPointObject;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A workflow library with methods related to policy violation workflows.
 */
public class PolicyViolationLibrary extends ApprovalLibrary {

    private static Log log = LogFactory.getLog(PolicyViolationLibrary.class);

    public PolicyViolationLibrary() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Delete/Ignore Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Delete an object associated with this workflow.
     * 
     * @ignore
     * This was added for PolicyViolation workflows since they're
     * not exactly like edit approval workflows.  One behavior we
     * support is to delete the violation if it is "approved".
     * We can't use commit() since wfcase.isDeleteApproval will be false,
     * and I don't like that convention anyway.  
     *
     * Instead the workflow will call the delete action when appropriate.
     * We expect the object to be identified with "approvalObject"
     * but we should accept other options, such as passing the class/id
     * in arguments.
     *
     * jsl - should this go in ApprovalLibrary?  It's only
     * been used for policy violations so far.
     */
    public Object delete(WorkflowContext wfc) 
        throws GeneralException {

        SailPointObject spo = getCurrentObject(wfc);
        if (spo != null) {
            SailPointContext con = wfc.getSailPointContext();
            Terminator t = new Terminator(con);
            t.deleteObject(spo);
        }
        return null;
    }

    /**
     * End the workflow associated with an object without actually
     * doing anything to it.  This is an alternative to commit()
     * which will remove the WorkflowCase reference but won't
     * save the changes to the approvalObject in the workflow.
     *
     * @ignore
     * jsl - should this go in StandardWorkflowHandler?  It's only
     * been used for policy violations so far.
     */
    public Object ignore(WorkflowContext wfc) 
        throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        context.decache();

        SailPointObject spo = getCurrentObject(wfc);
        if (spo != null) {
            SailPointContext con = wfc.getSailPointContext();
            spo.setPendingWorkflow(null);
            // can't be sure of the cache state of this object
            // so reswizzle to make sure external references are
            // all in the current session
            spo = ObjectUtil.recache(context, spo);
            con.saveObject(spo);
            con.commitTransaction();
        }
        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Mitigate/Remediate Actions
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ARG_ACTOR = "actor";
    public static final String ARG_EXPIRATION = "expiration";
    public static final String ARG_REMEDIATIONS = "remediations";
    public static final String ARG_COMMENTS = "comments";
    public static final String ARG_REMEDIATOR = "remediator";

    /**
     * Determine the Identity that will be considered the "actor"
     * for mitigation and remediation actions.
     */
    private Identity getActor(WorkflowContext wfc) 
        throws GeneralException {

        Identity actor = null;

        SailPointContext con = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        String name = args.getString(ARG_ACTOR);
        if (name != null) {
            actor = con.getObjectByName(Identity.class, name);
            if (actor == null) {
                // if they bothered to pass an arg it should be right
                log.warn("Invalid actor name: " + name);
            }
        }
        
        // fall back to the approver variable if we have one
        if (actor == null) {
            String approver = args.getString(Workflow.VAR_APPROVER);
            if (approver != null) {
                actor = con.getObjectByName(Identity.class, approver);
                // this should always be a real name
                if (actor == null)
                    log.warn("Invalid approver name: " + approver);
            }
        }

        // then to the case launcher
        // this is commonly a non-identity name like "Scheduler" so
        // don't warn
        if (actor == null) {
            String launcher = args.getString(Workflow.VAR_LAUNCHER);
            if (launcher != null)
                actor = con.getObjectByName(Identity.class, launcher);
        }

        // we must have an actor so I guess fall back here
        if (actor == null) {
            String adminName = BrandingServiceFactory.getService().getAdminUserName();
            actor = con.getObjectByName(Identity.class, adminName );
            if (actor == null)
                throw new GeneralException("Unable to determine actor for certification action");
        }

        return actor;
    }

    /**
     * Mitigate a policy violation.
     * An expiration date argument is required.
     * 
     * @param wfc WorkflowContext
     * @return This always returns null.
     * 
     * @ignore 
     * We may be able to generalize this to other objects, but since
     * we use PolicyViolationCertificationManager it's specific to
     * violations right now.
     */
    public Object mitigateViolation(WorkflowContext wfc)
        throws GeneralException {

        SailPointContext con = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        con.decache();

        // do NOT call getCurrentObject, the object may have been modified
        // inside the workflow
        //SailPointObject spo = getCurrentObject(wfc);
        SailPointObject spo = getObject(wfc);
        if (spo != null) {
            spo = ObjectUtil.recache(con, spo);
        }
        
        if (!(spo instanceof PolicyViolation)) {
            log.error("Can only mitigate policy violations");
        }
        else {
            PolicyViolation violation = (PolicyViolation)spo;
            Attributes<String,Object> args = wfc.getArguments();
            
            Identity actor = getActor(wfc);
            Date expiration = args.getDate(ARG_EXPIRATION);
            String comments = args.getString(ARG_COMMENTS);

            // Skip it if the expiration isn't sufficiently future
            Date baseline = Util.baselineDate(new Date());

            if (expiration == null) {
                // hmm, should we make something up or ignore it?
                log.error("No expiration date for mitigation");
            }
            else if (baseline.compareTo(expiration) >= 0) {
                // the UI tries to display this
                // addMessage(new Message(Message.Type.Info, MessageKeys.ERR_EXPIRATION_PAST), null);
                // I guess it's enough just to log it?  
                // Ideally we could do the Waveset thing and have it
                // throw back the WorkItem with a validation error.
                log.error("Mitigation expiration has past");
            }
            else if (actor != null) {
                // getActor will already have logged failures

                PolicyViolationCertificationManager pvcm = 
                    new PolicyViolationCertificationManager(con);

                // Note that this has a phase where it will try
                // to delte any WorkItems that reference the violation
                // through the targetId/targetClass properties.  This is
                // for pre-workflow alerts I think but I'm not sure.

                pvcm.mitigate(violation, actor, expiration, comments);

                con.commitTransaction();
            }
        }

        return null;
    }

    /**
     * Given a policy violation assumed to be for an SOD policy, 
     * return a list of the conflicting entitlements that may be
     * remediated.
     *
     * @param wfc WorkflowContext
     * @return List of Strings representing entitlements
     * 
     * @ignore
     * This is yet another JPMC hack that is fairly general.
     *
     * Return a list of "things" suitable for use with the generic
     * workItem.attributes['selectables'] list.
     *
     * For JPMC these will always be bundle names formatted as a CSV.
     * You have to remove one complete side, if there are multiples
     * it means "any of these" which will be filtered in the violation
     * to have only entitlements the user actually holds.  So it
     * makes no sense to remediate just part of one side.
     */
    public Object getRemediatables(WorkflowContext wfc) 
        throws GeneralException {

        List<String> things = null;

        WorkflowCase wfcase = wfc.getWorkflowCase();
        SailPointObject obj = wfcase.getApprovalObject();
        if (obj instanceof PolicyViolation) {
            PolicyViolation v = (PolicyViolation)obj;
            String left = v.getLeftBundles();
            String right = v.getRightBundles();
            // normally both will be set
            if (left != null || right != null) {
                things = new ArrayList<String>();
                if (left != null) things.add(left);
                if (right != null) things.add(right);
            }
        }

        return things;
    }

    /**
     * Remediate a policy violation.
     * 
     * NOTE: For role SOD policies, you must have
     * prompted for roles to remediate and passed the result
     * in ARG_SELECTIONS.  This will be normally one or more
     * items returned by the {@link #getRemediatables} method.
     * 
     * @param wfc WorkflowContext
     * @return This always returns null           
     * 
     * @ignore
     * This will use PolicyViolationCertificationManager
     * which in turn uses RemediationManager to compile a 
     * ProvisioningPlan and pass it off to Provisioner.
     * The end result of this may just be more old-school 
     * WorkItems if you don't have any integrations.  So 
     * unless you have integrations configured you might
     * just as well let the workflow handle it's own
     * remediation work items.
     *
     * Since we're not expecting the simple work item case,
     * we don't bother trying to define and pass down
     * an "assigneeName", pass down the workflow launcher
     * just in case.  
     * 
     * Not sure what the difference is between description
     * and comments in PolicyViolationCertificationManager.remediate.
     * PolicyViolationBean does some stuff with business roles
     * so you nay need to have the work item form do some other
     * stuff like prompt for roles to remediate?
     */
    public Object remediateViolation(WorkflowContext wfc) 
        throws GeneralException {

        SailPointContext con = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        con.decache();
        
        // do NOT call getCurrentObject, the object may have been modified
        // inside the workflow
        //SailPointObject spo = getCurrentObject(wfc);
        SailPointObject spo = getObject(wfc);
        if (spo != null) {
            spo = ObjectUtil.recache(con, spo);
        }

        if (!(spo instanceof PolicyViolation)) {
            log.error("Can only mitigate policy violations");
        }
        else {
            PolicyViolation violation = (PolicyViolation)spo;
            Attributes<String,Object> args = wfc.getArguments();

            Identity actor = getActor(wfc);
            String remediator = args.getString(ARG_REMEDIATOR);
            
            //Bug 8142: Keep backward compatibility by falling back to "actor"
            // argument if "remediator" not specified 
            if (Util.isNullOrEmpty(remediator)) {
                remediator = args.getString(ARG_ACTOR);
            }
            
            String comments = args.getString(ARG_COMMENTS);

            if (actor != null) {

                List<String> names = getStringList(args.get(ARG_REMEDIATIONS));
                violation.setBundleNamesMarkedForRemediation(names);

                PolicyViolationCertificationManager pvcm = 
                    new PolicyViolationCertificationManager(con);

                // Note that this has a phase where it will try
                // to delte any WorkItems that reference the violation
                // through the targetId/targetClass properties.  This is
                // for pre-workflow alerts I think but I'm not sure.

                pvcm.remediate(violation, actor, remediator, null, comments);

                con.commitTransaction();
            }
        }

        return null;
    }

}
