package sailpoint.api;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.object.ApplicationActivity;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * Class to pull out interesting details from the PolicyViolation 
 * and make them ready for anyone interested.  Both beans and rest
 * resources can consume this safely.
 * 
 * Most of the details are stored locally even though it 
 * all technically exists on the violation or associated policy.
 * However, due to known lazy initialization issues, just load it all 
 * upfront.  Also, for a few things like constraint.description we need
 * to resolve the SODConstraint object.
 *
 * Naming is confusing here due to model/ui evolution.
 *
 * What we display as "Rule" should be a brief "name" of the
 * SODConstraint object.  Normally SODConstraints don't have names since
 * it is hard to keep them unique, so the SODConstraint.violationSummary
 * was added to contain the brief description, suitable for a table cell.
 *
 * There is also the SODConstraint.description which is intended to 
 * have a much longer description of the constraint and is generally
 * not suitable for table cells.
 *
 * The policy unit tests started giving some of the constraints
 * names so we could correlate them on import.  These often don't
 * have summary strings.
 * 
 * @author matt.tucker
 *
 */
public class ViolationDetailer {
    
    private SailPointContext context;
    private PolicyViolation violation;
    
    private Policy policy;
    private String policyName;
    private String policyType;
    private String id;
    private String compensatingControl;
    private String constraintDescription;
    private String constraintWeight;
    private String constraint;
    private String constraintPolicy;
    private String remediationAdvice;
    private String summary;
    private String sodConflict;
    private String status;
    private String renderer;
    private Date createdDate;
    private Map<String, Object> args;
    private ApplicationActivity activity;
    
    private String identityName;
    private String identityFirstname;
    private String identityLastname;
    private String identityId;
    private String ownerName;
    
    /**
     * Last decision made on this violation. Pulled from the identity history.
     */
    private IdentityHistoryItem lastDecision;

    /**
     * Indicates whether the decision search has been performed. If
     * this value is not null, no search will be performed
     */
    private Boolean lastDecisionFound = null;
    
    public ViolationDetailer(SailPointContext context, PolicyViolation violation, 
            Locale locale, TimeZone timeZone)  throws GeneralException {
        this.context = context;
        this.violation = violation;
        
        if (this.violation != null && this.context !=null) {
            id = violation.getId();
            constraint = violation.getDisplayableName();
            policy = violation.getPolicy(context);
            policyName = violation.getPolicyName();
            args = violation.getArguments();
            if(violation.getStatus()!=null)
                status = violation.getStatus().getMessageKey();
            createdDate = violation.getCreated();
            renderer = violation.getRenderer();
            
            BaseConstraint base = null;
            Identity identity = violation.getIdentity();

            if (null != policy) {
                policyType = policy.getType();
                base = policy.getConstraint(violation);

                Localizer localizer = new Localizer(context);
                constraintPolicy = localizer.getLocalizedValue(policy, Localizer.ATTR_DESCRIPTION, locale);
            } else {
                // use the values on the violation
                constraintPolicy = violation.getPolicyName();
            }

            if (null != base) {
                constraintWeight = String.valueOf(base.getWeight());
                compensatingControl = base.getCompensatingControl();
                constraintDescription = base.getDescription();
                remediationAdvice = base.getRemediationAdvice();
            } else {
                constraintDescription = violation.getDescription();
            }

            if (null != identity) {
                identityId = violation.getIdentity().getId();
                identityName = violation.getIdentity().getDisplayableName();
                identityFirstname = violation.getIdentity().getFirstname();
                identityLastname = violation.getIdentity().getLastname();
            }
           
            // sniff the violation and see if we can tell the type
            // by the fields on the violation for cases where type
            // is null because the policy and/or contraint has been
            // removed.
            // jsl - to better support custom policy summary rules
            // we always obey the PolicyViolation.description if it
            // is non-null.  There is similar logic in 
            // PolicyViolationDatasource for reports.

            if (policy != null && policy.isType(Policy.TYPE_SOD)) {

                String left = getDisplayableBundleNames(violation.getLeftBundles(context));
                String right = getDisplayableBundleNames(violation.getRightBundles(context));

                if (left != null && right != null) {
                    // The message Should look something like 'A,B,C conflicts with D,E'
                    //TODO: Will need and/or grammar
                    Message msg = new Message(MessageKeys.POLICY_VIOLATION_SOD_SUMMARY,
                            Util.csvToList(left), Util.csvToList(right));
                    sodConflict = msg.getLocalizedMessage(locale, timeZone);
                }
            }

            summary = violation.getDescription();
            if (summary == null) {
                if (sodConflict != null) {
                    summary = sodConflict;
                } else if ( base != null ) {
                    summary = base.getDescription();
                }
            }

            if ( (policy != null && policy.isType(Policy.TYPE_ACTIVITY)) || 
                 ( violation.getActivityId() != null ) ) {
                activity = context.getObjectById(ApplicationActivity.class, violation.getActivityId());
            }
            
            /* Owner is lazily Loaded.  The bean needs owner.getName() after 
             * the object may have been disconnected from the session.  Load
             * the name locally...this also has the effect of loading the whole 
             * Owner locally into the stored _violation variable... 
             */
            if(violation.getOwner() != null ) {
                ownerName = violation.getOwner().getDisplayName();
            }
        }
    }

    /**
     * Returns a comma separated string containing the display names for the bundles
     * @param bundles The bundles to string-ify
     * @return A comma separated string containing the display names for the bundles
     */
    private String getDisplayableBundleNames(final List<Bundle> bundles) {
        StringBuilder builder = new StringBuilder();
        if(!Util.isEmpty(bundles)) {
            /* Add the first name outside the loop so in the loop we can add comma space name
             * and not have to trim or subString or something at the end. */
            builder.append(bundles.get(0).getDisplayableName());
            for (int i = 1; i < bundles.size(); i++) {
                builder.append(", ");
                builder.append(bundles.get(i).getDisplayableName());
            }

        }
        return builder.toString();
    }

    public Policy getPolicy() {
        return policy;
    }
    
    public String getPolicyType() {
        return policyType;
    }
    
    public String getPolicyName() {
        return policyName;
    }
    
    public String getId() {
        return id;
    }
    
    public String getCompensatingControl() {
        return WebUtil.sanitizeHTML(compensatingControl);
    }

    public String getConstraintDescription() {
        return WebUtil.sanitizeHTML(constraintDescription);
    }

    public String getConstraintWeight() {
        return constraintWeight;
    }

    public String getConstraintPolicy() {
        return WebUtil.sanitizeHTML(constraintPolicy);
    }

    public String getRemediationAdvice() {
        return WebUtil.sanitizeHTML(remediationAdvice);
    }
    
    public String getConstraint() {
        return constraint;
    }

    public String getSodConflict() {
        return sodConflict;
    }
    
    public String getSummary() {
        return WebUtil.sanitizeHTML(summary);
    }

    public String getStatus() {
        return status;
    }
    
    public ApplicationActivity getActivity() {
        return activity;
    }
    
    public Map<String, Object> getArgs() {
        return args;
    }
    
    public Date getCreatedDate() {
        return createdDate;
    }
    
    public String getIdentityName() {
        return identityName;
    }

    public String getIdentityFirstname() {
        return identityFirstname;
    }

    public String getIdentityLastname() {
        return identityLastname;
    }

    public String getIdentityId() {
        return identityId;
    }
    
    public String getOwner(){
        return ownerName;
    }
    
    public boolean isAllowRemediation() {
        return (policy == null) ? false : policy.isActionAllowed(CertificationAction.Status.Remediated);
    }

    public String getRenderer() {
        return renderer;
    }

    /**
     * Search for the last decision made on the violation.
     * @return Last decision or null if none found
     */
    public IdentityHistoryItem getLastDecision() throws GeneralException {

        // check lastDecisionFound so we don't search more than once
        if (lastDecisionFound == null){
            lastDecision = violation.getIdentity().getLastViolationDecision(context, violation);
            lastDecisionFound = lastDecision != null;
        }

        return lastDecision;
    }

    /**
     * The decision made on this violation. This method checks the history for
     * the identity, gets the latest decision and checks to see if it occurred 
     * on this violation instance, i.e., since the last policy scan.
     *
     * @return CertificationAction if applicable, or null if no action has been taken.
     */
    public IdentityHistoryItem getCurrentDecision() throws GeneralException {
        IdentityHistoryItem decision = null;

        if (getLastDecision() != null && getLastDecision().getAction() != null){
            // if the decision was made after the last policy scan then display it. We don't want to
            // display old decisions, just those made for the current violation. If no decisions have been made on
            // the violation, display any mitigation if it is still active.         
            if (getLastDecision().getAction().getCreated().compareTo(createdDate) > 0){
                decision = getLastDecision();
            } else if (getLastDecision().getAction().isMitigation()){
                decision = getLastDecision();
            }
        }

        return decision;
    }
}