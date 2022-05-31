/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.WorkItem;
import sailpoint.object.SailPointObject;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import java.util.Date;


/**
 * JSF bean to deal with certification challenge work items.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ChallengeItemBean extends WorkItemBean {

    private static final Log LOG = LogFactory.getLog(ChallengeItemBean.class);

    private String challengeComments;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public ChallengeItemBean() throws GeneralException {
        super();
        
        if (checkIsCertLocked()) {
            addErrorMessage("", Message.warn(MessageKeys.CERT_LOCKED_WARN), null);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getChallengeComments() {
        return challengeComments;
    }

    public void setChallengeComments(String challengeComments) {
        this.challengeComments = challengeComments;
    }

    public Date getChallengeExpiration() throws GeneralException {

        Date expiration = null;

        // Maybe we should pull the expiration date off of the work item rather
        // than getting it from the cert?
        WorkItem item = super.getObject();
        if (null != item) {
            CertificationItem certItem = item.getCertificationItem(getContext());
            if (null != certItem) {
                Certification cert = certItem.getCertification();
                if (null != cert) {
                    
                    // If we're using rolling challenges, get the date from the
                    // item.
                    if (cert.isUseRollingPhases()) {
                        expiration = certItem.getNextPhaseTransition();
                    }
                    else {
                        // We may be able to use next phase transition here, too.
                        expiration = cert.calculatePhaseEndDate(Certification.Phase.Challenge);
                    }
                }
            }
        }

        return expiration;
    }

    public String getChallengeDescription() throws GeneralException {

        String description = null;

        WorkItem item = super.getObject();
        if (null != item) {
            CertificationItem certItem = item.getCertificationItem(getContext());
            if (null != certItem) {
                description = certItem.getShortDescription();
            }
        }

        return description;
    }

    private boolean checkIsCertLocked() throws GeneralException {

        boolean locked = false;

        String certId = getCertificationId();
        if (certId != null) {
            if (ObjectUtil.isLockedById(getContext(), Certification.class, certId)) {
                locked = true;
            }
        }

        return locked;
    }
    
    private String getCertificationId() throws GeneralException {

        String id = null;
        
        WorkItem item = super.getObject();
        if (null != item) {
            id = item.getCertification();
        }
        
        return id;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Challenge the decision with the challengeComments.
     */
    public String challenge() throws GeneralException {
        
        SailPointContext context = getContext();
        WorkItem item = super.getObject();
        if (null != item) {
            CertificationItem certItem = item.getCertificationItem(context);
            if (null != certItem) {
                
                if (checkIsCertLocked()) {
                    addMessageToSession(new Message(Message.Type.Warn, MessageKeys.CERT_LOCKED_FAIL_CHALLENGE), null);
                } else {
                
                    // Use the CertificationService so that we send emails and
                    // mark the WorkItem completion state appropriately.
                    CertificationService svc = new CertificationService(context);
                    try {
                        svc.challengeItem(getLoggedInUser(), item, certItem, this.challengeComments);
                    }
                    catch (EmailException e) {
                        // Swallow this - the end user probably shouldn't see it.
                        // The certifier can login and see that the item is challenged.
                        // Note that this does not prevent the item from being
                        // challenged.
                        LOG.warn("Could not send challenge notification: " + e);
                    }
    
                    context.commitTransaction();
                }
            }
        }

        clearHttpSession();
        return getNextPage();
    }

    /**
     * Accept the decision (ie - the challenger is giving up their right to
     * challenge this decision henceforth).
     */
    public String acceptDecision() throws GeneralException {

        SailPointContext context = getContext();
        WorkItem item = super.getObject();
        if (null != item) {
            CertificationItem certItem = item.getCertificationItem(context);
            if (null != certItem) {
                // Use the service so that the work item is tweaked appropriately.
                CertificationService svc = new CertificationService(context);
                svc.acceptCertificationDecision(getLoggedInUser(), item, certItem);
                context.commitTransaction();
            }
        }

        clearHttpSession();
        return getNextPage();
    }

    @Override
    String getDefaultNextPage() {
        return "success";
    }

    /**
     * Challenge items are visible globally due to the fact that an un-authenticated user
     * can access them from a link in an email.
     *
     * todo we need to patch this hole. We're not doing it in 3.0p2 because of
     * time constraints 
     *
     *
     * @param object
     * @return
     * @throws GeneralException
     */
    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException {
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods - allows a challenge to participate in
    // navigation history.
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPageName() {
        return "Challenge Item";
    }

    @Override
    public String getNavigationString() {
        return "viewChallengeItem";
    }
}
