/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import sailpoint.api.CertificationService;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.util.NavigationHistory;


/**
 * JSF bean for dealing with challenges in a certification.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ChallengeBean extends BaseObjectBean<CertificationItem> {

    /**
     * Comments about the decision.
     */
    private String comments;
    
    /**
     * Whether to reject or approve the challenge.
     */
    private boolean reject;

    
    /**
     * Default constructor.
     */
    public ChallengeBean() {
        super();
        super.setScope(CertificationItem.class);
        super.setStoredOnSession(false);

        // Get the object ID off the request if we don't have it.
        if (null == getObjectId()) {
            setObjectId(super.getRequestParameter("certificationItemId"));
        }
    }


    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public boolean isReject() {
        return reject;
    }

    public void setReject(boolean reject) {
        this.reject = reject;
    }


    /**
     * Action to accept or reject the challenge based on the <code>reject</code>
     * field.  The certifier will have to choose a new decision if they accept
     * the challenge.
     * 
     * @return The navigation outcome.
     */
    public String saveChallengeDecision() throws GeneralException {

        CertificationPreferencesBean certPrefBean = null;
        CertificationItem item = super.getObject();
        if (null != item) {

            CertificationService svc = new CertificationService(getContext());
            try {
                if (this.reject) {
                    svc.rejectChallenge(getLoggedInUser(), this.comments, item);
                }
                else {
                    svc.acceptChallenge(getLoggedInUser(), this.comments, item);
                }
            }
            catch (GeneralException e) {
                // This gets thrown if the decision is made after the challenge
                // period is over.  Consider making this a more granular
                // exception.               
                Message.Type newType = e instanceof EmailException ? Message.Type.Warn
                        :  Message.Type.Error;
                Message msg = e.getMessageInstance();
                msg.setType(newType);
                super.addMessageToSession(msg, msg);
            }

            Certification cert = item.getCertification();
            CertificationBean.saveAndRefreshCertification(cert, getContext(), this);
            certPrefBean  = new CertificationPreferencesBean(cert.getId());
        }
        
        String outcome = NavigationHistory.getInstance().back();       
        if (null == outcome) {
            outcome =
                (null != certPrefBean) ? certPrefBean.getDefaultView() : "";
        }
        
        return outcome;
    }
}
