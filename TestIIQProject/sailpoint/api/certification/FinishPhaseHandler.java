/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import sailpoint.api.EmailSuppressor;
import sailpoint.api.SailPointContext;
import sailpoint.api.MessageRepository;
import sailpoint.object.Certification;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;


/**
 * A phase handler that handles moving into the end phase.  Note that for
 * periodic certs, all of the finishing activity happens in
 * Certificationer.finish().  This is only used when items enter the end phase
 * for rolling continuous certifications that may not have had their
 * remediations kicked off.  This is the case for continuous certs that use a
 * rolling challenge period with no remediation period.
 * 
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class FinishPhaseHandler extends BaseRemediatingPhaseHandler {

    /**
     * Constructor.
     * 
     * @param  context       The SailPointContext to use.
     * @param  errorHandler  The ErrorHandler to use.
     */
    public FinishPhaseHandler(SailPointContext context,
                              MessageRepository errorHandler,
                              EmailSuppressor emailSuppressor) {
        super(context, errorHandler, emailSuppressor);
    }

    public Rule getEnterRule(Certification cert) throws GeneralException{
        return getRule(Configuration.CERTIFICATION_FINISH_PHASE_ENTER_RULE, cert);
    }
    
    @Override
    public Certification internalPostEnter(Certification cert) throws GeneralException {
        // Don't need to do anything here.  The remediations get kicked off in
        // BaseRemediatingPhaseHandler's enterPhase(CertificationItem) method.
        return cert;
    }
}
