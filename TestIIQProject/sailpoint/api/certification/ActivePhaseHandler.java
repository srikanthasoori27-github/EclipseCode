/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationPhaser;
import sailpoint.api.Certificationer;
import sailpoint.api.EmailSuppressor;
import sailpoint.api.EmailTemplateRegistry;
import sailpoint.api.MessageRepository;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.Phaseable;
import sailpoint.object.Rule;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * PhaseHandler to perform behavior when a certification transitions into or out
 * of the Active state (ie - when the certification is started and actively
 * being worked on).  This creates start work items and notifies the owners.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ActivePhaseHandler extends BasePhaseHandler {

    private static final Log log = LogFactory.getLog(ActivePhaseHandler.class);



    private EmailTemplateRegistry emailRegistry;

    /**
     * Constructor.
     *
     * @param  context        The SailPointContext to use.
     * @param  errorHandler   The ErrorHandler to use.
     */
    public ActivePhaseHandler(SailPointContext context,
                              MessageRepository errorHandler,
                              EmailSuppressor emailSuppressor) {
        super(context, errorHandler, emailSuppressor);
        emailRegistry = new EmailTemplateRegistry(context);
    }

    public Rule getEnterRule(Certification cert) throws GeneralException{
        return getRule(Configuration.CERTIFICATION_ACTIVE_PHASE_ENTER_RULE, cert);
    }

    public Rule getExitRule(Certification cert) throws GeneralException{
        return getRule(Configuration.CERTIFICATION_ACTIVE_PHASE_EXIT_RULE, cert);
    }


    /**
     * Calculate the expiration based on a start time of now, generate the
     * start work items, and send email notifications to the owners.  If this
     * is a continuous cert, start scanning for items that need to transition.
     *
     * @param  cert  The Certification that is being started.
     */
    @Override
    public Certification enterPhase(Certification cert) throws GeneralException {
        cert.setActivated(new Date());
        
        // Set the expiration now that we have a start date (may be null).
        // Only set one if there is not already an expiration.  On a
        // reassignment this will be copied from the original cert.
        if (null == cert.getExpiration()) {
            Date expiration = cert.calculateExpirationDate();
            cert.setExpiration(expiration);
        }

        // set the auto close date, if any. On a reassignment this will be
        // copied from the original cert.
        if (null == cert.getAutomaticClosingDate()) {
            Date autoCloseDate = cert.calculateAutomaticClosingDate(context);
            cert.setAutomaticClosingDate(autoCloseDate);
        }

        // decorate with work items, if this fails you can try the
        // commit again, perhaps we need a distinct interface
        // for work item management?
        if (null == cert.getSigned()) {
            generateStartWorkItems(cert);

            this.context.commitTransaction();

            if (!this.wasStaged(cert)) {
                cert = this.doAutoDecisions(cert);
            }

            // bug #8292 moved notify to
            // certificationer so that it
            // can be called after bulkreassignments
        }
        else {
            this.context.commitTransaction();
        }
        return cert;
    }
    
    private boolean wasStaged(Certification cert)
    	throws GeneralException {
    	
    	CertificationDefinition def = cert.getCertificationDefinition(context);
    	if (def != null) {
    		return def.isStagingEnabled();
    	}
    	
    	return false;
    }

    /**
     * Create a WorkItem for each owner of each certification.
     */
    private void generateStartWorkItems(Certification cert)
        throws GeneralException {

        // normally this should be null, but support incremental decoration
        if ((cert.getWorkItems() == null) || cert.getWorkItems().isEmpty()) {

            // Generate a work item per certifier.
            // should have caught missing owners by now
            List<String> certifiers = cert.getCertifiers();
            if ((null != certifiers) && !certifiers.isEmpty()) {

                // keep track of the actual certifiers to save later
                Collection<String> actualCertifiers = new LinkedHashSet<String>();

                // duplicate the certifiers list to dis-associate it from
                // the hibernate-attached certification
                certifiers = new ArrayList<String>(certifiers);

                // gotta commit certification here to get entities/items in for 
                // forward rules
                this.context.commitTransaction();
                
                // this will handle the final opening of the work items
                Workflower wf = new Workflower(this.context);

                for (String certifier : certifiers) {

                    Identity owner = this.context.getObjectByName(Identity.class, certifier);
                    if (owner != null) {
                        // check to see who the actual owner will be based
                        // on forwarding, and don't create a work item if
                        // this owner has already or will have a work item
                        // created
                        // jsl - updated to pass in the stub work item with the type
                        // set so the forwarding rule can make decisions based on type
                        // (bug#5814) may want to build out the complete work item so the
                        // rule has more info? 
                        // bug 8092 requires certification to be passed here for self 
                        // certification check.
                        WorkItem item = new WorkItem();
                        item.setType(WorkItem.Type.Certification);
                        item.setHandler(Certificationer.class);
                        item.setCertification(cert);
                        
                        Identity newOwner = wf.checkForward(owner, item);
                        if ( newOwner != null && newOwner != owner &&
                                     certifiers.contains(newOwner.getName()) )
                            continue;

                        actualCertifiers.add(newOwner.getName());

                        String reqname = cert.getCreator();
                        if (reqname != null) {
                            Identity req = this.context.getObjectByName(Identity.class, reqname);
                            item.setRequester(req);
                        }

                        item.setDescription(cert.getName());
                        item.setOwner(owner);


                        // Setup notifications.
                        cert.setupWorkItemNotifications(context, item);

                        // Store a back-reference to the work item on the certification.
                        cert.addWorkItem(item);

                        // manufacture a dislay name
                        owner = item.getOwner();
                        String name = Util.getFullname(owner.getFirstname(), owner.getLastname());
                        if (null == Util.getString(name)) {
                            name = owner.getName();
                        }

                        // open the item
                        // Note that this will check auto-forwarding
                        // and might change the display name.
                        // !! This will commit, if we need to keep the
                        // transaction open until all work items are opened
                        // Workflower will need a different method.

                        wf.open(item);
                        this.context.decache(item);
                        // jsl - up until the Hibernate upgrade we decached the owner here
                        // but that may still be referenced by the Certification object
                        // being modified, have to leave it in the cache!
                    }
                }

                // persist the actual certifiers
                cert.setCertifierNames(new ArrayList<String>(actualCertifiers));
            }
        }
    }

    /**
     * Walk the Certification hierarchy sending notifications to each
     * Identity that has been assigned a WorkItem.
     */
    //TODO: remove this
    @SuppressWarnings("unused")
    private void notifyStart(Certification cert)
        throws GeneralException {

        List<WorkItem> items = cert.getWorkItems();
        if (items != null && !cert.isBulkReassignment()) {

            EmailTemplate notifEmail = emailRegistry.getTemplate(cert, Configuration.CERTIFICATION_EMAIL_TEMPLATE);

            boolean suppressInitialNotification = false;

            CertificationDefinition certDef = cert.getCertificationDefinition(context);
            if (certDef != null) {
                suppressInitialNotification = certDef.isSuppressInitialNotification(context);
            }

            if (!suppressInitialNotification && (null != notifEmail)) {
                for (WorkItem item : items) {

                    Identity owner = item.getOwner();
                    Identity requester = item.getRequester();
                    List<String> ownerEmails = ObjectUtil.getEffectiveEmails(context,owner);
                    if ( ownerEmails == null ) {
                        if (log.isWarnEnabled())
                            log.warn("Work item owner (" + owner.getName() + ") has no email. " +
                                     "Could not send certification notification.");
                    }
                    else {
                        // if we can't send the email, should store
                        // some indication of that in the work item and
                        // set up a retry timer

                        // For now, we'll just use a map with a few pre-selected properties.
                        Map<String,Object> args = new HashMap<String,Object>();
                        args.put("workItemName", item.getDescription());
                        args.put("workItem", item);
                        args.put("certification", cert);
                        if (null != requester) {
                            args.put("requesterName", requester.getDisplayableName());
                        }
                        args.put("ownerName", owner.getDisplayableName());
                        EmailOptions ops = new EmailOptions(ownerEmails, args);
                        sendEmail(notifEmail, ops);
                    }
                }
            }
        }
    }


    /**
     * If the item is remediated, blast forward.
     */
    public void handleRollingPhaseTransition(CertificationItem item,
                                             CertificationPhaser phaser)
        throws GeneralException {

        CertificationAction.Status status =
            (null != item.getAction()) ? item.getAction().getStatus() : null;

        // Decision is remediated - advance phase if we're not delegated or
        // waiting for review.  If this is a revoke account request, only
        // generate a challenge if none of the other items for this account have
        // a challenge generated yet.
        if (!item.isDelegatedOrWaitingReview() &&
            CertificationAction.Status.Remediated.equals(status) &&
            !item.getParent().isAccountRevokeChallengeGenerated(item)) {

            phaser.advancePhase(item);
        }
    }

    /**
     * Don't increment the next phase transition for certs that use rolling
     * phases, since we wait for an action to occur to kick this off.
     */
    @Override
    public boolean updateNextPhaseTransition(Phaseable phaseable) {

        Certification cert = phaseable.getCertification();
        return ((null == cert) || !cert.isUseRollingPhases());
    }
}
