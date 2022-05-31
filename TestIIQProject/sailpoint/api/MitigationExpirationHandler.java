/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Identity;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.PolicyViolation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * This component performs actions when mitigations expire on users.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class MitigationExpirationHandler {

    private static final Log log = LogFactory.getLog(MitigationExpirationHandler.class);

    private SailPointContext ctx;


    /**
     * Constructor.
     */
    public MitigationExpirationHandler(SailPointContext ctx) {
        this.ctx = ctx;
    }


    /**
     * Handle all mitigations that have expired for the given Identity.
     * 
     * @param  identity  The Identity for which to handle the mitigation
     *                   expirations.
     */
    public int handleMitigationExpirations(Identity identity)
        throws GeneralException {

        int numHandled = 0;
        List<MitigationExpiration> exps = identity.getMitigationExpirations();
        if (null != exps) {
            Date now = new Date();
            for (MitigationExpiration exp : exps) {
                // Handled if the expiration has past and this hasn't been acted
                // upon.
                if ((exp.getExpiration().compareTo(now) < 0) &&
                    (null == exp.getLastActionDate())) {
                    handleMitigationExpiration(identity, exp);
                    numHandled++;
                }
            }
        }
        return numHandled;
    }

    private void handleMitigationExpiration(Identity identity,
                                            MitigationExpiration exp)
        throws GeneralException {

        exp.setLastActionDate(new Date());
        ctx.saveObject(exp);
        
        boolean isLastCreatedMitigation = true;
        
        //re-open policy violation if it is still in Mitigated state
        PolicyViolation descViolation = exp.getPolicyViolation();
        if (descViolation != null) {
            //gotta get the real violation, assuming its still around. 
            PolicyViolation violation = ctx.getObjectById(PolicyViolation.class, descViolation.getId()); 
            if (violation != null &&
                    PolicyViolation.Status.Mitigated.equals(violation.getStatus()) ) {
                
                //cleanup any mitigation created before this mitigation
                cleanupPreviouslyCreatedMitigations(identity, exp);
                
                if (isLastCreatedMitigation(identity, exp)) {
                    violation.setStatus(PolicyViolation.Status.Open);
                    ctx.saveObject(violation);
                } else {
                    isLastCreatedMitigation = false;
                }
            }
        }

        //do not notify if it is not last created PV mitigation
        if (isLastCreatedMitigation && MitigationExpiration.Action.NOTIFY_CERTIFIER.equals(exp.getAction())) {

            // Is this OK or do we need to send an email to the certification owner(s)?
            Identity mitigator = exp.getMitigator();
            List<String> emails = ObjectUtil.getEffectiveEmails(ctx,mitigator);
            if (null == emails) {
                if (log.isWarnEnabled())
                    log.warn("Mitigator " + mitigator.getName() + " has no email " +
                             "address, couldn't be notified of mitigation expiration");
            }

            EmailTemplate template =
                ObjectUtil.getSysConfigEmailTemplate(ctx, Configuration.MITIGATION_EXPIRATION_EMAIL_TEMPLATE);
            if (null != template) {
                Message itemDescription = null;
                Bundle bundle = exp.getBusinessRole(ctx);
                if (null != bundle) {
                    itemDescription =
                        new Message(MessageKeys.MITIGATION_EXP_ROLE_DESC, bundle.getName());
                }
                else if (null != exp.getPolicyViolation()) {
                    BaseConstraint constraint =
                        exp.getPolicyViolation().getConstraint(ctx);

                    // This can be null if the policy has been deleted or for
                    // a policy that doesn't use constraints.
                    if (null == constraint) {
                        itemDescription =
                            new Message(MessageKeys.MITIGATION_EXP_GENERIC_VIOLATION_DESC);
                    }
                    else {
                        String con = constraint.getDisplayableName();
                        itemDescription =
                            new Message(MessageKeys.MITIGATION_EXP_SPECIFIC_VIOLATION_DESC, con);
                    }
                }
                else if (null != exp.getEntitlements()) {
                    EntitlementSnapshot ents = exp.getEntitlements();
                    itemDescription =
                        new Message(MessageKeys.MITIGATION_EXP_ENTITLEMENTS_DESC, ents.getApplication());
                }

                String mitigatorName =
                    (null != mitigator) ? Util.getFullname(mitigator.getFirstname(), mitigator.getLastname())
                                        : null;
                String itemDesc =
                    (null != itemDescription) ? itemDescription.getLocalizedMessage() : null;

                Map<String,Object> args = new HashMap<String,Object>();
                args.put("identityName", Util.getFullname(identity.getFirstname(),
                                                          identity.getLastname()));
                args.put("itemDescription", itemDesc);
                args.put("mitigationExpiration", exp);
                args.put("mitigator", mitigatorName);
                args.put("comments", exp.getComments());
                EmailOptions ops = new EmailOptions(emails, args);
                try {
                    ctx.sendEmailNotification(template, ops);
                } catch (RetryableEmailException e) {
                    if (log.isWarnEnabled())
                        log.warn("Certifier " + mitigator.getName() + " has not been " +
                                 "notified of mitigation expiration - the email has been queued", e);
                }
            }
            else {
                if (log.isWarnEnabled())
                    log.warn("Certifier " + mitigator.getName() + " could not be " +
                             "notified of mitigation expiration - no email template");
            }
        }
        else {
            if (log.isWarnEnabled())
                log.debug("Not acting on mitigation expiration for " + identity +
                          ": " + exp.toString());
        }

    }
    
    //Cleanup all mitigations that created before current mitigation
    private void cleanupPreviouslyCreatedMitigations(Identity identity,
                                                    MitigationExpiration curExp) throws GeneralException {
        for (MitigationExpiration mitigation : Util.safeIterable(identity.getMitigationExpirations())) {
            if (!mitigation.getId().equals(curExp.getId()) && 
                    !mitigation.hasBeenActedUpon() &&
                    mitigation.getCertifiableDescriptor().isSimiliarViolation(curExp.getPolicyViolation())) {
                if (mitigation.getCreated().before(curExp.getCreated())) {
                    mitigation.setLastActionDate(new Date());
                    ctx.saveObject(mitigation);
                }
            }
        }
    }


    /**
     * Check if it is last created active mitigation expiration for this violation.
     * IIQSAW-2193 -- Last created MitigationExpiration takes precedence.
     * 
     * When the interrogator is looking for mitigation it loops through them all in 
     * case there is more than one for a given violation.  We will do the same thing here.
     */
    private boolean isLastCreatedMitigation(Identity identity, MitigationExpiration curExp) 
    throws GeneralException {
        boolean isLastCreated = true;
        
        for (MitigationExpiration mitigation : Util.safeIterable(identity.getMitigationExpirations())) {
            if (!curExp.getId().equals(mitigation.getId()) && 
                    !mitigation.hasBeenActedUpon() &&
                    curExp.getCertifiableDescriptor().isSimiliarViolation(mitigation.getPolicyViolation())) {
                if (mitigation.getCreated().after(curExp.getCreated())) {
                    isLastCreated = false;
                    break;
                }
            }
        }

        return isLastCreated;
    }
}
