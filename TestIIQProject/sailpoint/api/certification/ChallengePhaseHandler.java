/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationPhaser;
import sailpoint.api.CertificationService;
import sailpoint.api.Certificationer;
import sailpoint.api.EmailSuppressor;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.MessageRepository;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.api.EmailTemplateRegistry;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Phaseable;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.WorkItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Application;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * Handle transitioning into and out of the challenge period.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ChallengePhaseHandler extends BasePhaseHandler {

    private static final Log log = LogFactory.getLog(ChallengePhaseHandler.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private EmailTemplateRegistry templateRegistry;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     * 
     * @param  context       The SailPointContext to use.
     * @param  errorHandler  The ErrorHandler to use.
     */
    public ChallengePhaseHandler(SailPointContext context,
                                 MessageRepository errorHandler,
                                 EmailSuppressor emailSuppressor)
        throws GeneralException {

        super(context, errorHandler, emailSuppressor);

        templateRegistry = new EmailTemplateRegistry(context);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // PhaseHandler Overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    public Rule getEnterRule(Certification cert) throws GeneralException{
        Rule rule = getRule(Configuration.CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE, cert);
        /* Check for legacy Cert Challenge Phase Enter Rule Literal */
        if( rule == null ) {
            rule = getRule( Configuration.CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE_LEGACY, cert );
        }
        return rule;
    }
    
    public Rule getExitRule(Certification cert) throws GeneralException{
        return getRule(Configuration.CERTIFICATION_CHALLENGE_PHASE_EXIT_RULE, cert);
    }
    
    @Override
    public CertificationItem enterPhase(CertificationItem item) throws GeneralException {
        // Don't send notifications here since items transition independently.
        // We don't want to spam the certifier, or do we?
        generateChallenge(item.getCertification(), item, true);
        return item;
    }
    
    /**
     * Returns a QueryOptions with basic filters in place for searching out
     * CertificationItems of the given Certification, ordering by the parent
     * entity's Identity.
     */
    private QueryOptions getCertificationItemsQueryOptions(Certification cert) {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.certification", cert));
        ops.addOrdering("parent.identity", true);
        ops.setDistinct(true);
        return ops;
    }

    /**
     * Enters the Challenge phase for the Certification. Decaches and reattaches
     * the passed in Certification.
     */
    @Override
    public Certification enterPhase(Certification cert) throws GeneralException {

        // Send notification to certification owner.
        notifyPhaseStart(cert);

        QueryOptions ops = getCertificationItemsQueryOptions(cert);
        ops.add(Filter.eq("action.status", CertificationAction.Status.Remediated));
        ops.add(Filter.isnull("challenge"));

        IncrementalObjectIterator<CertificationItem> itemsIt = new IncrementalObjectIterator<CertificationItem>(this.context, CertificationItem.class, ops);
        while (itemsIt.hasNext()) {
            CertificationItem item = itemsIt.next();
            generateChallenge(cert, item, true);
            if (itemsIt.getCount() % 50 == 0) {
                context.commitTransaction();
                context.decache();
                context.attach(cert);
            }
        }

        // for good measure
        ObjectUtil.saveDecacheAttach(this.context, cert);
        return cert;
    }

    private void generateChallenge(Certification cert, CertificationItem item,
                                   boolean startOfPeriod)
        throws GeneralException {

        // Don't generate a challenge if the item is delegated or is
        // waiting for a delegation review.  See bug 1838.  If this is a revoke
        // account request, only generate a challenge if none of the other items
        // for this account have a challenge generated yet.
        if (!item.isDelegatedOrWaitingReview() && item.isActedUpon() &&
            !item.isChallengeGenerated() &&
            CertificationAction.Status.Remediated.equals(item.getAction().getStatus()) &&
            !item.getParent().isAccountRevokeChallengeGenerated(item)) {
            
            // Generate challenge work item and notify.
            WorkItem workItem = generateChallengeWorkItem(cert, item);
            if (null != workItem) {
                notifyChallengeCreation(cert, item, workItem, startOfPeriod);
            }
        }
    }

    /**
     * Handles exiting the phase for the item. Returned object is the same
     * one passed in
     */
    @Override
    public CertificationItem exitPhase(CertificationItem item) throws GeneralException {

        // Again, do we want to notify here?
        Certification cert = item.getCertification();
        exitItem(cert, item);
        // exitItem(cert, item) can pull up a significant number of objects into cache. One
        // caller to exitItem is iterating over a list of items and nicely cleaning up after itself.
        // The other isn't directly cleaning up after itself, but higher in its calls stack, it is.
        // In the end, it's the callers who will have to clean up after extItem(cert, item) and
        // not me. Decaching now might hinder more than help.
        return item;
    }

    /**
     * Refreshes the passed in Certification causing decache. Returned Certification
     * is a different object.
     */
    @Override
    public Certification postExit(Certification cert) throws GeneralException {

        // Refresh with the certificationer to refresh the completion status.
        // This commits the transaction.
        Certificationer certificationer = new Certificationer(context);
        certificationer.refresh(cert);
        return ObjectUtil.reattach(this.context, cert);
    }
    
    /**
     * Expires open challenges. Decaches and returns the Certification as
     * a different object.
     */
    @Override
    public Certification exitPhase(Certification cert) throws GeneralException {

        // Send an email telling the certifiers that they can sign off now.
        notifyPhaseEnd(cert);

        // Find all items with challenges, expire anything not yet completed
        // and delete the work items.
        QueryOptions qo = getCertificationItemsQueryOptions(cert);
        qo.add(Filter.not(Filter.isnull("challenge")));
        IncrementalObjectIterator<CertificationItem> items = new IncrementalObjectIterator<CertificationItem>(this.context, CertificationItem.class, qo);
        while (items.hasNext()) {
            CertificationItem item = items.next();
            exitItem(cert, item);
            context.saveObject(item);
            if (items.getCount() % 50 == 0) {
                context.commitTransaction();
                context.decache();
                context.attach(cert);
            }
        }
        // and then one for the road
        context.commitTransaction();
        context.decache();

        // Certification is decached and the items processed are not the same objects
        // Send in a re-loaded version
        cert = context.getObjectById(Certification.class, cert.getId());
        return postExit(cert);
    }

    private void exitItem(Certification cert, CertificationItem item)
        throws GeneralException {
        
        CertificationChallenge challenge = item.getChallenge();
        
        if (log.isDebugEnabled())
            log.debug("Exiting challenge for item: " + item.getId());
        
        // The challenge is still on the item, if it hasn't been
        // challenged we'll expire the item.  If it has been
        // challenged but not acted upon, then the certifier was
        // lazy and didn't get around to dealing with the challenge,
        // I guess we'll mark it as expired and send a note to the
        // challenger.
        if (null != challenge) {

            String workItemId = challenge.getWorkItem();

            // Expire the challenge work item if it wasn't challenged.
            // The work item may have already been deleted by
            // Certificationer.assimilate(WorkItem) - which doesn't
            // send a notification - when it expired.  We'll always
            // send a notification here.
            if (!challenge.didChallengerAct()) {
                item.challengeExpired();
                
                log.debug("\tExpiring challenge");
                notifyChallengeItemExpiration(item);
            }
            else if (challenge.isChallenged() && !challenge.hasBeenDecidedUpon()) {
                // If the item was challenged but not acted upon
                // (if accepted, the challenge is nulled), expire
                // the decision.
                log.debug("\tExpiring challenge decision");
                item.challengeDecisionExpired();
                notifyChallengeDecisionExpiration(cert, item);
            }

            // Delete the work item if it's still there.
            if (null != workItemId) {

                // Delete the work item and notify.
                WorkItem workItem =
                    context.getObjectById(WorkItem.class, workItemId);
                if (null != workItem) {
                    log.debug("\tDeleting challenge work item");
                    context.removeObject(workItem);
                }
            }
        }
    }
    
    /**
     * Skip the challenge phase if the certification has been signed.  A cert
     * can only be signed before the challenge phase if there aren't any
     * remediation decisions, hence - no challenges.
     */
    @Override
    public boolean isSkipped(Phaseable phaseable) {
        
        return phaseable.getCertification().hasBeenSigned();
    }
    
    /**
     * Immediately generate a challenge if this item was remediated but does not
     * yet have a challenge.
     * 
     * @param  cert  The Certification in which the entity lives.
     * @param  item  The CertificationItem to refresh.
     */
    @Override
    public void refresh(Certification cert, CertificationItem item)
        throws GeneralException {

        this.generateChallenge(cert, item, false);
    }
    
    /**
     * If the a challenge was accepted, a new decision needs to be made so roll
     * back to the active phase.  If the challenge isn't active any more,
     * advance the phase.
     */
    @Override
    public void handleRollingPhaseTransition(CertificationItem item,
                                             CertificationPhaser phaser)
        throws GeneralException {

        CertificationChallenge challenge = item.getChallenge();

        // If decision is null and challenge has been accepted (REWIND)
        if (!item.isActedUpon() && (null != challenge) &&
            CertificationChallenge.Decision.Accepted.equals(challenge.getDecision())) {

            // Also, clear the challenge since it may need to get regenerated.
            item.clearChallenge();

            // delete the challenge to avoid leaving an orphan
            context.removeObject(challenge);

            phaser.rewindPhase(item);
        }
        else if (!item.isChallengeActive()) {

            // Challenge is no longer active (ADVANCE)

            // Q: When do remediations get launched?
            //
            // A: Remediations will get launched in the remediation phase
            //    handler if the remediation phase is enabled, or in the finish
            //    phase handler if the remediation phase is not enabled.  This
            //    is done in the phase handler for continuous certs since
            //    Certificationer.finish() is never called for a continuous cert
            //    (this logic happens in Certificationer's flushContinuousChanges()
            //    method - which was already called when the remediation decision
            //    was made - and we don't want to call it again).
            //
            //    Think about this.  For periodic certs with rolling phases (but
            //    no remediation phase) should we kick off remediations in the
            //    FinishPhaseHandler, or should we wait until signed?  Maybe
            //    this is also configurable?
            phaser.advancePhase(item);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // NOTIFICATION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Notify the certifiers that we are entering the challenge phase.
     */
    private void notifyPhaseStart(Certification cert)
        throws GeneralException {

        EmailTemplate phaseStartEmailTemplate = templateRegistry.getTemplate(cert,
                Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE);

        if (null != phaseStartEmailTemplate) {

            CertificationService svc = new CertificationService(context);
            List<String> emails = svc.getCertifierEmails(cert, phaseStartEmailTemplate);

            if ( emails != null ) {
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("certificationName", cert.getName());
                args.put("certification", cert);
                EmailOptions ops = new EmailOptions(emails, args);
                super.sendEmail(phaseStartEmailTemplate, ops);
            }
        }
    }

    /**
     * Notify the certifiers that we are exiting the challenge phase and they
     * can now sign off.
     */
    private void notifyPhaseEnd(Certification cert) throws GeneralException {

       EmailTemplate phaseEndEmailTemplate = templateRegistry.getTemplate(cert,
               Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE);

        if (null != phaseEndEmailTemplate) {

            CertificationService svc = new CertificationService(context);
            List<String> emails = svc.getCertifierEmails(cert, phaseEndEmailTemplate);

            if ( emails != null ) {
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("certificationName", cert.getName());
                args.put("certification", cert);
                EmailOptions ops = new EmailOptions(emails, args);
                super.sendEmail(phaseEndEmailTemplate, ops);
            }
        }
    }

    /**
     * Notify the challenger that they have X number of days challenge.
     */
    private void notifyChallengeCreation(Certification cert,
                                         CertificationItem item,
                                         WorkItem workItem,
                                         boolean startOfPeriod)
        throws GeneralException {

        EmailTemplate template = templateRegistry.getTemplate(cert,
                  Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE);

        if (null != template) {

            Identity challenger = workItem.getOwner();
            List<String> challengerEmails = ObjectUtil.getEffectiveEmails(context,challenger);
            if (challengerEmails == null ) {
                if (log.isWarnEnabled())
                    log.warn("Challenger (" + challenger.getName() + ") has no email. " +
                             "Could not send challenge creation notification.");
            }
            else {
                String timeRemaining = null;
                Date now = new Date();

                // If this is being generated at the start of the period, calculate
                // the end date based on adding the challenge duration to the
                // current time.  This gives more accuracy with the message and
                // allows working with continuous certs or "process revokes
                // immediately" where the phase end date isn't a static point.
                if (startOfPeriod) {
                    CertificationPhaseConfig challengeConfig =
                        cert.getPhaseConfig(Certification.Phase.Challenge);
                    Date end = challengeConfig.getDuration().addTo(now);
                    timeRemaining =
                        Duration.formatTimeDifference(end, now, Locale.getDefault());
                }
                else {
                    // If this is being generated after the challenge period has already
                    // started, format the difference between now and the end of the phase.
                    // Truncate everything less than an hour.  We can assume that this is
                    // a periodic certifcation.
                    Date end = cert.calculatePhaseEndDate(Certification.Phase.Challenge);
                    timeRemaining =
                        Duration.formatTimeDifference(end, now, Locale.getDefault(), Duration.Scale.Hour);
                } 

                Map<String,Object> args = new HashMap<String,Object>();
                args.put("challengeItem", item.getShortDescription());
                args.put("timeRemaining", timeRemaining);
                args.put("challengeId", workItem.getId());
                args.put("certificationName", cert.getName());
                args.put("certification", cert);
                args.put("certificationItem", item);
                args.put("workItem", workItem);

                String entityName = Certification.Type.AccountGroupPermissions.equals(cert.getType()) ?
                        item.getAccountGroup() : item.getIdentity();
                args.put("entityName", entityName);

                EmailOptions ops = new EmailOptions(challengerEmails, args);
                super.sendEmail(template, ops);

            }
        }
    }

    /**
     * Notify the challenger that the challenge period expired, so they can no
     * longer challenge the decision.
     */
    private void notifyChallengeItemExpiration(CertificationItem item)
        throws GeneralException {

        EmailTemplate template = templateRegistry.getTemplate(item.getCertification(),
                  Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE);

        if (null != template) {
    
            Identity challenger =
                context.getObjectByName(Identity.class, item.getIdentity());
            List<String> challengerEmails = ObjectUtil.getEffectiveEmails(context,challenger);
    
            if (challengerEmails == null ) {
                if (log.isWarnEnabled())
                    log.warn("Challenger (" + challenger.getName() + ") has no email. " +
                             "Could not send challenge expiration notification.");
            }
            else {
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("challengeItem", item.getShortDescription());
                args.put("certificationName", item.getCertification().getName());
                args.put("certification", item.getCertification());
                args.put("certificationItem", item);
                // unlike other challenge notifications, there is no workitem this time. It's already deleted.
                EmailOptions ops = new EmailOptions(challengerEmails, args);
                super.sendEmail(template, ops);
            }
        }
    }

    /**
     * Notify the certifiers and challenger that the item that was challenged
     * was not dealt with in time so we're expiring the challenge decision (ie -
     * the decision stays).
     */
    private void notifyChallengeDecisionExpiration(Certification cert,
                                                   CertificationItem item)
        throws GeneralException {

        EmailTemplate template = templateRegistry.getTemplate(item.getCertification(),
                Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE);
                               
        if (null != template) {

            List<String> emails = new ArrayList<String>();

            // Add the challenger email.
            Identity challenger =
                context.getObjectByName(Identity.class, item.getIdentity());

            List<String> challengerEmails = ObjectUtil.getEffectiveEmails(context,challenger);
            if (challengerEmails == null) {
                if (log.isWarnEnabled())
                    log.warn("Challenger (" + challenger.getName() + ") has no email. " +
                             "Could not send challenge expiration notification.");
            }
            else {
                emails.addAll(challengerEmails);
            }
    
            // Add the certifier emails.  We could just use the action's actor
            // as the recipient here.
            CertificationService svc = new CertificationService(context);
            emails.addAll(svc.getCertifierEmails(cert, template));

            Map<String,Object> args = new HashMap<String,Object>();
            args.put("certificationName", cert.getName());
            args.put("certification", cert);
            args.put("challengeItem", item.getShortDescription());
            args.put("certificationItem", item);
            // unlike other challenge notifications, there is no workitem this time. It's already deleted.
            EmailOptions ops = new EmailOptions(emails, args);
            super.sendEmail(template, ops);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // WORK ITEM GENERATION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private WorkItem generateChallengeWorkItem(Certification cert,
                                               CertificationItem certItem)
        throws GeneralException {

        WorkItem item = null;

        // If the challenge has already been generated, do nothing.
        if (certItem.isChallengeGenerated()) {
            return null;
        }

        Identity owner = null;

        if (Certification.Type.AccountGroupPermissions.equals(cert.getType())){
            ManagedAttribute group = certItem.getParent().getAccountGroup(context);
            if (group != null && group.getOwner() != null){
                owner = group.getOwner();
            } else {
                Application app = certItem.getParent().getApplication(context);
                if (app != null){
                    owner = app.getOwner();
                }
            }
            if (owner == null) {
                errorHandler.addMessage(new Message(Message.Type.Error,
                    MessageKeys.INVALID_CHALLENGER_ACCOUNT_GROUP, certItem.getParent().getAccountGroup()));
            }
        } else {
            String challenger = certItem.getIdentity();
            owner = context.getObjectByName(Identity.class, challenger);
            if (owner == null) {
                errorHandler.addMessage(new Message(Message.Type.Error,
                    MessageKeys.INVALID_CHALLENGER_IDENTITY, challenger));
            }
        }


        if (owner != null) {
            item = new WorkItem();
            item.setType(WorkItem.Type.Challenge);
            item.setHandler(Certificationer.class);

            // Is the decision maker the requester, or this be the certification
            // requester?
            CertificationAction action = certItem.getAction();
            if (null != action) {
                item.setRequester(action.getActor(context));
            }

            item.setOwner(owner);
            Message desc =  null;
            if (Certification.Type.AccountGroupPermissions.equals(cert.getType())){
                desc = new Message(MessageKeys.CHALLENGE_ACCT_GRP_PERMS_WORKITEM_DESC,
                        certItem.getParent().getAccountGroup());
            } else {
                desc = new Message(MessageKeys.CHALLENGE_WORKITEM_DESC, certItem.getShortDescription());
            }

            //challenge_account_group_permissions_workitem_desc

            item.setDescription(desc.getLocalizedMessage());                

            // Are these the right contextual fields to set on the work item
            // (ie - do we need the certification)?
            item.setCertification(cert);
            item.setCertificationItem(certItem);

            // TODO: setup notification prefs (reminders, etc...)

            // Set this item to expire when the challenge phase ends.  The
            // Certificationer will assimilate the expiration onto the challenge
            // in the certification.
            item.setExpiration(cert.calculatePhaseEndDate(Certification.Phase.Challenge));

            // this may change the owner
            Workflower wf = new Workflower(context);
            wf.open(item);

            // Save the challenge work item info on the certification item.
            // jsl - note that the previous wf call will have committed, 
            // but this next modification needs to be committed again,
            // sadly we have to call open() first though to get
            // auto-forwarded user.  Could retool this to have
            // the WorkItemHandler.forwardWorkItem callback
            // change the CertificationChallenge?
            certItem.challengeGenerated(item, item.getOwner());
        }

        return item;
    }
}

