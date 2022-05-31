package sailpoint.api.certification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EmailSuppressor;
import sailpoint.api.MessageRepository;
import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.tools.GeneralException;

/**
 * PhaseHandler to perform behavior when a certification transitions into or out
 * of the Staged state (ie - before the certification is started).
 *
 * @author Jeff Upton <jeffupt@gmail.com>
 */
public class StagedPhaseHandler extends BasePhaseHandler
{
    private static final Log log = LogFactory.getLog(StagedPhaseHandler.class);

    /**
     * Constructs a new StagedPhaseHandler
     * 
     * @param context The SailPointContext to use.
     * @param errorHandler The error handler to use.
     * @param emailSuppressor The (possibly null) EmailSuppressor to use.
     */
    public StagedPhaseHandler(SailPointContext context,
            MessageRepository errorHandler, EmailSuppressor emailSuppressor)
    {
        super(context, errorHandler, emailSuppressor);
    }

    @Override
    public Certification enterPhase(Certification cert) throws GeneralException {
        // Commit the cert state before moving on to making decisions.
        this.context.commitTransaction();
        return this.doAutoDecisions(cert);
    }
}
