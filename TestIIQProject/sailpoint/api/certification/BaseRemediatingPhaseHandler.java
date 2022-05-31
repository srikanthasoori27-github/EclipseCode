/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import sailpoint.api.Certificationer;
import sailpoint.api.EmailSuppressor;
import sailpoint.api.MessageRepository;
import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.tools.GeneralException;


/**
 * A base for phase handlers that can kick off remediations.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BaseRemediatingPhaseHandler extends BasePhaseHandler {

    /**
     * A cached certificationer to use.
     */
    private Certificationer certificationer;


    /**
     * Constructor.
     * 
     * @param  context       The SailPointContext to use.
     * @param  errorHandler  The ErrorHandler to use.
     */
    public BaseRemediatingPhaseHandler(SailPointContext context,
                                       MessageRepository errorHandler,
                                       EmailSuppressor emailSuppressor) {
        super(context, errorHandler, emailSuppressor);
    }

    /**
     * Should be implemented by subclasses to perform any pertinent postEnter
     * logic.  The postEnter() implementation already sends batched
     * notifications and updates the remediation statistics on the cert.
     */
    abstract Certification internalPostEnter(Certification cert) throws GeneralException;
    
    
    /**
     * Return a certificationer to use.
     */
    Certificationer getCertificationer() throws GeneralException {

        if (null == this.certificationer) {
            this.certificationer = new Certificationer(this.context);

            // Point back to this error handler.
            if ((null != this.errorHandler))
                this.certificationer.setErrorHandler(this.errorHandler);
        }

        return this.certificationer;
    }

    /**
     * If the item is remediated but not yet kicked off, kick it off.
     */
    @Override
    public CertificationItem enterPhase(CertificationItem item) throws GeneralException {
        
        // These remediation notifications are batched - postEnter() will handle
        // sending them off.
        getCertificationer().handleRemediation(item);
        return item;
    }

    
    /**
     * Send batched remediation notifications, update the remediation stats on
     * the certification, and set the next remediation scan date on the cert.
     */
    @Override
    public Certification postEnter(Certification cert) throws GeneralException {

        // Send the notifications that were batched.
        RemediationManager remedMgr = new RemediationManager(this.context);
        remedMgr.flush(cert);
        
        // Roll up the number of remediations kicked off.
        getCertificationer().updateRemediationsKickedOff(cert, true);
        internalPostEnter(cert);
        return cert;
    }
}
