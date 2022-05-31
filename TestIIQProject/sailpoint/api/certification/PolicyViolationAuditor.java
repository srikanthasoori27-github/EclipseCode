/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.CertificationAction;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;

/**
 * This class provides a consistent interface to audit actions on policy violations
 *
 * @author <a href="mailto:tim.faehnle@sailpoint.com">Tim Faehnle</a>
 */

public class PolicyViolationAuditor {
    
    static private Log log = LogFactory.getLog(PolicyViolationAuditor.class);
    
    public static final String ATT_POLICY = "policy";
    public static final String ATT_RULE = "rule";
    
    // the identity that is making the decision
    public static final String ATT_SOURCE = "source";
    
    // the identity on whom the violation was detected
    public static final String ATT_TARGET = "target";
    
    // owner of the policy violation
    public static final String ATT_VIOLATION_OWNER = "violationOwner";
    
    public static final String ATT_RULE_SUMMARY = "ruleSummary";
    
    // where was the decision made?  manage -> policyViolations or in a cert?
    public static final String ATT_ACTION_SOURCE = "actionSource";
    public static final String ATT_EXPIRATION_DATE = "expiration";
    public static final String ATT_NEW_OWNER = "newOwner";
    public static final String ATT_PREVIOUS_OWNER = "previousOwner";
    public static final String ATT_COMMENTS = "comments";
    public static final String ATT_REMEDIATION_DETAILS = "remediationDetails";
    public static final String ATT_REMEDIATION_ACTION = "remediationAction";
    public static final String ATT_REMEDIATION_OWNER = "remediationOwner";
    public static final String ATT_DESCRIPTION = "description";
    
    PolicyViolationAuditor() {
        
    }
    
    /**
     * mitigation audit on policy violation.  Saves the AuditEvent, but doesn't commit (depending on whether Auditor.log(event) commits).
     *
     * @param violation The violation that's been mitigated
     * @param actor The identity who made the mitigation decision
     * @param actionSource The source of the action (from Manage -> Policy Violation, an Access Review, etc.) 
     * @param comments Comments made on the mitigation by the actor
     * @param expires The date the mitigation expires
     */
    public static void auditMitigation(PolicyViolation pv, Identity actor, String actionSource, String comments, Date expires) {
        String action = AuditEvent.ActionViolationAllowException;
        Attributes<String, Object> attrs = new Attributes<String, Object>();
        
        attrs.put(ATT_EXPIRATION_DATE, expires);
        AuditEvent evt = createViolationEvent(pv, actor, actionSource, action, comments, attrs);
        
        Auditor.log(evt);
    }
    
    /**
     * delegation audit on policy violation. Saves the AuditEvent, but doesn't commit (depending on whether Auditor.log(event) commits).
     *
     * @param violation The violation that's been mitigated
     * @param actor The identity who made the mitigation decision
     * @param actionSource The source of the action (from Manage -> Policy Violation, an Access Review, etc.) 
     * @param comments Comments made on the mitigation by the actor
     * @param assignee The Identity to whom the delegation has been assigned
     * @param previousOwner The name of the Identity who previously owned this issue
     */
    public static void auditDelegation(PolicyViolation pv, Identity actor, String actionSource, String comments, Identity assignee, String previousOwner) {
        String action = AuditEvent.ActionViolationDelegate;
        Attributes<String, Object> attrs = new Attributes<String, Object>();
        
        if (null != assignee) attrs.put(ATT_NEW_OWNER, assignee.getName());
        if (null != previousOwner) attrs.put(ATT_PREVIOUS_OWNER, previousOwner);
        AuditEvent evt = createViolationEvent(pv, actor, actionSource, action, comments, attrs);
        
        Auditor.log(evt);
    }
    
    /**
     * remediation audit on policy violation. Saves the AuditEvent, but doesn't commit (depending on whether Auditor.log(event) commits).
     *
     * @param violation The violation that's been mitigated
     * @param actor The identity who made the mitigation decision
     * @param actionSource The source of the action (from Manage -> Policy Violation, an Access Review, etc.) 
     * @param comments Comments made on the mitigation by the actor
     * @param assignee The Identity to whom the remediation has been assigned (if it's a workitem)
     * @param remediation The RemediationAction performed
     * @param description The description of the remediation
     */
    public static void auditCorrect(PolicyViolation pv, Identity actor, String actionSource, String comments, Identity assignee, CertificationAction certAction, String description) {
        String action = AuditEvent.ActionViolationCorrection;
        Attributes<String, Object> attrs = new Attributes<String, Object>();
        
        ProvisioningPlan plan = null;
        if (certAction != null) {
            plan = certAction.getRemediationDetails();
            attrs.put(ATT_REMEDIATION_ACTION, certAction.getRemediationAction());
        }
        
        if (null != plan) {
            try {
                attrs.put(ATT_REMEDIATION_DETAILS, plan.toXml());
            } catch (GeneralException ge) {
                if (log.isErrorEnabled())   
                    log.error("Could not describe provisioning plan for policy " + 
                              "violation correction audit: " + ge.getMessage(), ge);
            }
        }
            
        attrs.put(ATT_DESCRIPTION, description);
        if (null != assignee) attrs.put(ATT_REMEDIATION_OWNER, assignee.getName());
        AuditEvent evt = createViolationEvent(pv, actor, actionSource, action, comments, attrs);
        
        Auditor.log(evt);
    }
    
    /**
     * acknowledgement audit on policy violation. Saves the AuditEvent, but doesn't commit (depending on whether Auditor.log(event) commits).
     *
     * @param violation The violation that's been mitigated
     * @param actor The identity who made the mitigation decision
     * @param actionSource The source of the action (from Manage -> Policy Violation, an Access Review, etc.) 
     * @param comments Comments made on the mitigation by the actor
     */
    public static void auditAcknowledge(PolicyViolation pv, Identity actor, String actionSource, String comments) {
        String action = AuditEvent.ActionViolationAcknowledge;
        Attributes<String, Object> attrs = new Attributes<String, Object>();
        
        AuditEvent evt = createViolationEvent(pv, actor, actionSource, action, comments, attrs);
        
        Auditor.log(evt);
    }
    
    /**
     * private method to audit the common properties.  Maintains consistency for logging.
     *
     * @param violation The violation that's been mitigated
     * @param actor The identity who made the mitigation decision
     * @param actionSource The source of the action (from Manage -> Policy Violation, an Access Review, etc.) 
     * @param action The AuditEvent that is being audited
     * @param comments Comments made on the mitigation by the actor
     * @param attrs The attributes that may be adorned by the caller prior to calling this method
     */
    private static AuditEvent createViolationEvent(PolicyViolation pv, Identity actor, String actionSource, String action, String comments, Attributes<String, Object> attrs) {
        AuditEvent event = null;
        
        // if the policy violation is null, there's no action, so nothing to audit.
        if (null != pv) {
            Identity target = pv.getIdentity();
            event = new AuditEvent();
            event.setAction(action);
            
            // set properties on event
            if (null != actor)  event.setSource(actor.getDisplayableName());
            if (null != target) event.setTarget(target.getDisplayableName());
            if (null != actor)  event.setString1(ATT_SOURCE + "=" + actor.getName());
            if (null != target) event.setString2(ATT_TARGET + "=" + target.getName());
            event.setString3(ATT_POLICY + "=" + pv.getPolicyName());
            event.setString4(ATT_RULE + "=" + pv.getConstraintName());
            
            // set attributes on event
            if (null != pv.getOwner()) attrs.put(ATT_VIOLATION_OWNER, pv.getOwner().getName());
            attrs.put(ATT_RULE_SUMMARY, pv.getDescription());
            attrs.put(ATT_ACTION_SOURCE, actionSource);
            attrs.put(ATT_COMMENTS, comments);
            
            event.setAttributes(attrs);
        }
        
        return event;
    }
}