/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import sailpoint.api.Certificationer;
import sailpoint.api.EmailSuppressor;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.MessageRepository;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;

import java.util.Date;


/**
 * PhaseHandler for the remediation period, which does the following:
 * 
 * <ul>
 *   <li>Phase entry: Kicks off remediation requests for any remediation that
 *       has not yet been launched.</li>
 *   <li>Refresh: If an item is remediated and does not have a remediation
 *       kicked off for it, the remediation request is launched.</li>
 * </ul>
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class RemediationPhaseHandler extends BaseRemediatingPhaseHandler {

    /**
     * Constructor.
     * 
     * @param  context       The SailPointContext to use.
     * @param  errorHandler  The ErrorHandler to use.
     */
    public RemediationPhaseHandler(SailPointContext context,
                                   MessageRepository errorHandler,
                                   EmailSuppressor emailSuppressor) {
        super(context, errorHandler, emailSuppressor);
    }

    public Rule getEnterRule(Certification cert) throws GeneralException{
        return getRule(Configuration.CERTIFICATION_REMEDIATION_PHASE_ENTER_RULE, cert);
    }
    
    public Rule getExitRule(Certification cert) throws GeneralException{
        return getRule(Configuration.CERTIFICATION_REMEDIATION_PHASE_EXIT_RULE, cert);
    }
    
    /**
     * Set the next remediation scan date so that we start scanning for
     * remediations.
     */
    @Override
    Certification internalPostEnter(Certification cert) throws GeneralException {

        // Set the nextRemediationScan to now so the housekeeper can start doing
        // it's thang.  Only change this if it's null, otherwise we'll be
        // pushing the date back.
        if (null == cert.getNextRemediationScan()) {
            cert.setNextRemediationScan(new Date());
        }
        return cert;
    }

    /**
     * Handle any remediation requests that have not yet been kicked off.
     */
    @Override
    public Certification enterPhase(Certification cert) throws GeneralException {

        // Get all the item(s) that haven't yet had remediations
        // kicked off, and kick them off.
        IncrementalObjectIterator<CertificationItem> itemsIt = getItemsToRemediate(cert);
        while (itemsIt.hasNext()) {
            getCertificationer().handleRemediation(itemsIt.next());
            if (itemsIt.getCount() % 50 == 0) {
                this.context.commitTransaction();
                this.context.decache();
                this.context.attach(cert);
            }
        }

        // Save the certification and commit the transaction so we can query
        // for batched notifications to send in postEnter().
        ObjectUtil.saveDecacheAttach(this.context, cert);

        postEnter(cert);
        return cert;
    }

    /**
     * Get the CertificationItems in the given Certification that have
     * remediation requests that need to be kicked off.
     */
    private IncrementalObjectIterator<CertificationItem> getItemsToRemediate(Certification cert)
        throws GeneralException {

        // Query for these items rather than walk through the whole certification,
        // since this may be a sparse list.  Look for items that are remediated
        // but have not been kicked off.
        Filter f =
            Filter.and(Filter.eq("parent.certification", cert),
                       Filter.eq("action.status", CertificationAction.Status.Remediated),
                       Filter.eq("action.remediationKickedOff", false));
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        qo.setDistinct(true);
        qo.addOrdering("parent.identity", true);

        return new IncrementalObjectIterator<CertificationItem>(this.context, CertificationItem.class, qo);
    }

    /**
     * Launch a remediation request for this item if it is remediated but has
     * not yet been kicked off.
     */
    @Override
    public void refresh(Certification cert, CertificationItem item)
        throws GeneralException {

        // Kick off the remediation.  The notifications are batched and will
        // be sent later by the certificationer.  The certificationer will also
        // update the remediation statistics later.
        Certificationer certificationer = getCertificationer();
        certificationer.handleRemediation(item);
    }

    /**
     * If this was the last item in the remediation phase, null out the
     * nextRemediationScan so we quit scanning.
     */
    @Override
    public CertificationItem exitPhase(CertificationItem item) throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("parent.certification", item.getCertification()));
        qo.add(Filter.eq("phase", Certification.Phase.Remediation));
        qo.add(Filter.ne("id", item.getId()));

        int count = this.context.countObjects(CertificationItem.class, qo);

        // No items left in the remediation phase, null it out.
        if (count < 1) {
            item.getCertification().setNextRemediationScan(null);
        }
        return item;
    }
    
    /**
     * Null out the nextRemediationScan, so we quit scanning.
     */
    @Override
    public Certification exitPhase(Certification cert) throws GeneralException {

        cert.setNextRemediationScan(null);
        return cert;
    }
}
