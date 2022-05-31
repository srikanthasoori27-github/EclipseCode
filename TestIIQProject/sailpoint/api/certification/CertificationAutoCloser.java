/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.MessageRepository;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AuditEvent;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.server.Auditor;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.web.messages.MessageKeys;


/**
 * @author derry.cannon
 *
 */
public class CertificationAutoCloser
    {
    public static final String RET_CERTS_AUTOMATICALLY_CLOSED = "certificationsAutomaticallyClosed";
    public static final String RET_CERT_ITEMS_AUTOMATICALLY_DECIDED = "certificationItemsAutomaticallyDecided";
    
    private static final Log log = LogFactory.getLog(CertificationAutoCloser.class);

    /**
     * Context from which objects will be looked up in the db.
     */
    private SailPointContext context;

    /**
     * Any error messages will be stored here.
     */
    private MessageRepository messages;

    /**
     * May be set by the task executor to indicate that we
     * should stop when convenient.
     */
    private boolean terminate;

    /**
     * The number of certs that were successfully closed.
     */
    private int certificationsClosed;

    /**
     * The number of cert items that were successfully decided.
     */
    private int certificationItemsDecided;

    /**
     * The default remediator when revoking a cert item.
     */
    private String defaultRemediator;

    /**
     * The Certificationer that will handle some cert housekeeping
     */
    private Certificationer certificationer;
    
    /**
     * The time that the auto closer started.  This lets us use the
     * exact same time when doing two different searches for unfinished
     * certs so that we don't have a time delta between searches.
     */
    private Date today;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The constructor pulls in a couple of configuration values and initializes
     * the Certificationer that will be used to refresh and sign the certs.
     */
    public CertificationAutoCloser(SailPointContext ctx, MessageRepository msgs)
        throws GeneralException
        {
        this.context = ctx;
        this.messages = msgs;
        
        this.today = new Date();

        // load in the system default for allow exceptions in case we need it
        Configuration config = context.getConfiguration();
        defaultRemediator = config.getString(Configuration.DEFAULT_REMEDIATOR);

        certificationer = new Certificationer(context);
        }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Find all certs that have an automatic closing date earlier than right
     * now and that are not yet marked signed.
     *
     * @return An Iterator of cert ids that are ready for automatic closing.
     *
     * @throws GeneralException
     */
    private Iterator<Object[]> getCertifications()
        throws GeneralException {
        
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.lt("automaticClosingDate", today));
        //IIQETN-4651 - changed from finished to signed.  We don't want or need to auto close certs
        //that are already signed.  When we say we will auto-close incomplete certs,
        //we mean unsigned certs.
        qo.add(Filter.isnull("signed"));
        
        // this should place any child certs in the front of the queue
        // so that they get processed before their parents
        qo.addOrdering("created", false);
        qo.setCloneResults(true);

        Iterator<Object[]> certs = context.search(Certification.class, qo, "id");

        return certs;
    }


    /**
     * Find all cert items for the given cert that do not yet have an action
     * (i.e., for which a decision has not yet been made on that item) or
     * which have not yet been completed (e.g., have been delegated and 
     * decided, but not completed). 
     *
     * @return An Iterator of cert item ids that need a decision.
     *
     * @throws GeneralException
     */
    protected Iterator<Object[]> getUndecidedCertItems(Certification cert)
        throws GeneralException
        {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("parent.certification", cert));
        qo.add(Filter.isnull("action"));
        qo.setCloneResults(true);

        Iterator<Object[]> items = context.search(CertificationItem.class, qo, "id");

        return items;
        }


    /**
     * Find all cert items for the given cert that have been delegated but 
     * have not been completed or revoked.
     *
     * @return An Iterator of cert item ids that need a decision.
     *
     * @throws GeneralException
     */
    private Iterator<Object[]> getIncompleteDelegatedItems(Certification cert)
        throws GeneralException
        {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("parent.certification", cert));
        qo.add(Filter.notnull("delegation"));
        qo.add(Filter.isnull("delegation.completionState"));
        qo.add(Filter.eq("delegation.revoked", false));
        qo.setCloneResults(true);

        Iterator<Object[]> items = context.search(CertificationItem.class, qo, "id");

        return items;
        }


    /**
     * Find all cert entities for the given cert that have been delegated but 
     * have not been completed or revoked.
     *
     * @return An Iterator of cert item ids that need a decision.
     *
     * @throws GeneralException
     */
    private Iterator<Object[]> getIncompleteDelegatedEntities(Certification cert)
        throws GeneralException
        {
        QueryOptions qo = new QueryOptions();
                
        qo.add(Filter.eq("certification", cert));
        qo.add(Filter.notnull("delegation"));
        qo.add(Filter.isnull("delegation.completionState"));
        qo.add(Filter.eq("delegation.revoked", false));
        qo.setCloneResults(true);

        Iterator<Object[]> items = context.search(CertificationEntity.class, qo, "id");

        return items;
        }


    /**
     * Run any auto cert closing rule, then walk through the cert's undecided
     * items.  Make decisions on those items using the auto close options
     * specified in the cert def, then refresh and sign the cert.
     *
     * @param cert
     * @return
     * @throws GeneralException
     */
    private boolean closeCertification(Certification cert)
        throws GeneralException {

        CertificationDefinition def = cert.getCertificationDefinition(context);
        if (def == null) {
            messages.addMessage(new Message(Message.Type.Error,
                MessageKeys.AUTO_CERT_CLOSE_NO_CERT_DEF, cert.getId()));

            return false;
        }

        // load and run any auto closing rule in the cert def
        Rule closeRule = def.getAutomaticClosingRule(context);
        if (null != closeRule) {
            closeRule.load();
            runCloseRule(closeRule, cert);
        }

        // load up the remaining auto close options from the cert def
        CertificationAction.Status action = def.getAutomaticClosingAction();
        String comments = def.getAutomaticClosingComments();
        Identity signer = def.getAutomaticClosingSigner(context);
        if (signer == null) {
            String adminName = BrandingServiceFactory.getService().getAdminUserName();
            signer = context.getObjectByName(Identity.class, adminName );
        }

        try {
            // process any delegated entities
            Iterator<Object[]> ids = getIncompleteDelegatedEntities(cert);
            //IIQETN-6052 :- Since "processCertItems" is reattaching "cert" object we need to
            //return back the "cert" object with the new reference.
            cert = processCertItems(CertificationEntity.class, ids, cert, action, comments, signer);

            // process the incomplete cert item delegations
            ids = getIncompleteDelegatedItems(cert);
            cert = processCertItems(CertificationItem.class, ids, cert, action, comments, signer);

            // process the unfinished cert items
            ids = getUndecidedCertItems(cert);
            cert = processCertItems(CertificationItem.class, ids, cert, action, comments, signer);
        } catch (GeneralException e) {
            // if any of the cert items don't process properly,
            // we can't finish closing this cert.  However, there's
            // no reason at this point not to go on to the next cert,
            // so catch the exception here, log it and allow the
            // calling code to continue.
            log.warn(e.getMessage(), e);

            return false;
        }

        // If there are any commands hanging around for whatever reason,
        // we really don't want them on the cert when doing a final refresh.
        cert.clearCommands();

        // dot the t's and cross the i's
        certificationer.refresh(cert);
        context.decache();
        cert = ObjectUtil.reattach(this.context, cert);
        signer = ObjectUtil.reattach(this.context, signer);

        certificationer.sign(cert, signer);

        return true;
    }


    /**
     * 
     * 
     * @param clazz
     * @param certItems
     * @param cert
     * @param action
     * @param comments
     * @param signer
     * @throws GeneralException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Certification processCertItems(Class clazz, Iterator<Object[]> certItems, Certification cert,
        CertificationAction.Status action, String comments, Identity signer)
        throws GeneralException {

        Duration allowExceptionDuration = getAllowExceptionDuration(cert);
        int itemCount = 0;
        while (certItems.hasNext() && !terminate) {
            String id = (String)certItems.next()[0];
            SailPointObject item = context.getObjectById(clazz, id);
            if (item == null) {
                messages.addMessage(new Message(Message.Type.Error,
                    MessageKeys.AUTO_CERT_CLOSE_CERT_ITEM_NOT_FOUND, id));

                continue;
            }

            try {
                // cert items need decisions, and if delegated, need 
                // the delegations revoked
                if (clazz == CertificationItem.class) {
                    CertificationItem certItem = (CertificationItem)item;
                    if (certItem.isDelegated())
                        certItem.revokeDelegation();

                    decideCertItem(certItem, cert, action, comments, signer, allowExceptionDuration);
                    context.saveObject(certItem);
                    certificationItemsDecided++;
                }

                // cert entities need delegations revoked
                if (clazz == CertificationEntity.class) {
                    CertificationEntity entity = (CertificationEntity)item;
                    entity.revokeDelegation();
                    context.saveObject(entity);
                }

                context.commitTransaction();

                // more cache mgmt
                itemCount++;
                if ((itemCount % 20) == 0) {
                    context.decache();
                    //IIQETN-6052 :- This is the main reason of the issue in 6052,
                    //if we are reattaching an object it will return a new reference that
                    //should be returned back when we are done processing this method.
                    cert = ObjectUtil.reattach(context, cert);
                }
            } catch (GeneralException e) {
                messages.addMessage(new Message(Message.Type.Error,
                    MessageKeys.AUTO_CERT_CLOSE_CERT_ITEM_PROBLEM, item.getId()));

                // throw the exception upstairs so processing 
                // of the current cert ends
                throw e;
            }
        }
        //IIQETN-6052 :- when we are done processing this method we should return back
        //the new reference pointing to "cert" object.
        return cert;
    }
    
    
    /**
     * Apply the given auto close options to the given undecided cert item.
     * There are a few wrinkles involving policy violations that take some
     * tweak and fiddle, but nothing too crazy.
     *
     * @param item      The cert item that needs a decision
     * @param cert      The parent certification
     * @param action    The action to apply to the cert item
     * @param comments  The comments to apply to the cert item
     * @param signer    The signer of the cert item
     *
     * @throws GeneralException
     */
    private void decideCertItem(CertificationItem item, Certification cert,
            CertificationAction.Status action, String comments,
            Identity signer, Duration allowExceptionDuration)
            throws GeneralException {
        CertificationAction certAction = new CertificationAction();
        certAction.setRevokeAccount(false);

        switch (action) {
        case Remediated:
            boolean remediable = true;
            if (item.getType() == CertificationItem.Type.PolicyViolation) {
                Policy policy = item.getPolicyViolation().getPolicy(context);
                if (policy != null && policy.getType().equalsIgnoreCase("SOD")) {
                    remediable = false;
                } else if (policy == null) {
                    log.warn("Generating remediation action for deleted policy: "
                            + item.getPolicyViolation().getName());
                }
            }

            if (remediable) {
                CertificationAction.Status status = CertificationAction.Status.Remediated;
                if (item.useRevokeAccountInsteadOfRevoke()) {
                    status = CertificationAction.Status.RevokeAccount;
                    certAction.setRevokeAccount(true);
                }

                CertificationActionDescriber describer = new CertificationActionDescriber(
                        item, status, this.context);
                String desc = describer.getDefaultRemediationDescription(null,
                        item.getParent());

                certAction.remediate(cert.getId(), signer, null,
                        CertificationAction.RemediationAction.OpenWorkItem,
                        defaultRemediator, desc, comments, null, null);
            } else {
                // since we can't remediate SODs via automation,
                // allow an exception instead
                certAction.mitigate(cert.getId(), signer, null,
                        getExpirationDate(allowExceptionDuration), comments);
            }

            break;
        case Approved:
            boolean approvable = true;
            if (item.getType() == CertificationItem.Type.PolicyViolation) {
                // determine if the violated policy supports approval
                Policy policy = item.getPolicyViolation().getPolicy(context);
                if (policy != null && policy.getCertificationActions() != null) {
                    String allowedActions = policy.getCertificationActions()
                            .toLowerCase();
                    if (!allowedActions.contains("approved")) {
                        approvable = false;
                    }
                } else if (policy == null) {
                    log.warn("Generating approval action for deleted policy: "
                            + item.getPolicyViolation().getName());
                }
            }

            if (approvable) {
                certAction.approve(cert.getId(), signer, null, comments);
            } else {
                certAction.mitigate(cert.getId(), signer, null,
                        getExpirationDate(allowExceptionDuration), comments);
            }
            break;
        case Mitigated:
            certAction.mitigate(cert.getId(), signer, null,
                    getExpirationDate(allowExceptionDuration), comments);
            break;
        default:
            // bad news
            throw new GeneralException("Invalid auto close action: " + action);
        }
        item.bulkCertify(signer, null, certAction, null, true);
    }
        
    private Duration getAllowExceptionDuration(Certification cert) throws GeneralException {
        Duration duration = null;
        if (cert != null) {
            CertificationDefinition definition = cert.getCertificationDefinition(this.context);
            if (definition != null) {
                duration = definition.getAllowExceptionDuration(this.context);
            }
        }
        return duration;
    }
        
    private Date getExpirationDate(Duration allowExceptionDuration) {
        return (allowExceptionDuration == null) ? null : allowExceptionDuration.addTo(new Date());
    }

    /**
     * Run the given closing rule for the given cert.
     *
     * @param rule Rule to run before automatically closing the cert
     * @param cert The cert to be closed
     *
     * @return An Object containing the results of the rule execution.
     *
     * @throws GeneralException
     */
    private void runCloseRule(Rule rule, Certification cert)
        throws GeneralException
        {
        Map<String,Object> params = new HashMap<String,Object>();
        params.put("certification", cert);

        context.runRule(rule, params);
        }


    public int getCertificationsClosed()
        {
        return certificationsClosed;
        }


    public int getCertificationItemsDecided()
        {
        return certificationItemsDecided;
        }

    ////////////////////////////////////////////////////////////////////////////
    //
    // TASK METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    public void execute() throws GeneralException {

        Iterator<Object[]> certs = getCertifications();
        while (certs.hasNext() && !terminate) {
            String id = (String) certs.next()[0];
            final Certification cert = context.getObjectById(Certification.class, id);
            // archived?
            if (cert == null) {
                messages.addMessage(new Message(Message.Type.Error, MessageKeys.AUTO_CERT_CLOSE_CERT_NOT_FOUND, id));
                continue;
            }

            Callable<Boolean> doWhat = new Callable<Boolean>() {
                public Boolean call() throws Exception {
            
                    return closeCertification(ObjectUtil.reattach(context, cert));
                }
            }; 
            Pair<Boolean, Boolean> result = ObjectUtil.doWithCertLock(context, cert, doWhat, true, 0);

            if (result.getFirst() && result.getSecond()) {
                certificationsClosed++;
                context.decache();
            } else {
                messages.addMessage(new Message(Message.Type.Error, MessageKeys.AUTO_CERT_CLOSE_CERT_PROBLEM, cert.getId()));
            }
        }
    }

    public void saveResults(TaskResult result)
        {
        result.setAttribute(RET_CERTS_AUTOMATICALLY_CLOSED, getCertificationsClosed());
        result.setAttribute(RET_CERT_ITEMS_AUTOMATICALLY_DECIDED, getCertificationItemsDecided());

        audit(AuditEvent.ActionCertificationsAutomaticallyClosed, getCertificationsClosed());
        audit(AuditEvent.ActionCertificationItemsAutomaticallyDecided, getCertificationItemsDecided());
        }


    public void traceResults()
        {
        System.out.println(getCertificationsClosed() + " certifications automatically closed");
        System.out.println(getCertificationItemsDecided() + " certification items automatically decided");
        }


    private void audit(String action, int statistic)
        {
        if (statistic > 0)
            Auditor.log(action, statistic);
        }


    public void terminate()
        {
        terminate = true;
        }
    }
