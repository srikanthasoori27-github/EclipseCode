/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Source;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;


/**
 * The RemediationScanner is in charge of looking for remediation requests that
 * have been kicked off (currently only from the context of a certification) and
 * determining whether the requested remediation has actually been applied to
 * the native resource accounts.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class RemediationScanner {

    private static final Log log = LogFactory.getLog(RemediationScanner.class);
    
    private SailPointContext context;
    private Identitizer identitizer;
    private ProvisioningChecker checker;


    /**
     * Constructor.
     */
    public RemediationScanner(SailPointContext ctx) {
        this.context = ctx;
        checker = new ProvisioningChecker(ctx);
    }

    /**
     * Look for items in the given certification that were remediated but have
     * not yet been marked as having the remediation completed, and check to
     * see if the remediation has been completed.  Note that this may commit
     * the transaction.
     * 
     * @param  cert  The Certfication to scan.
     * 
     * @return The number of identities we scan.
     */
    public int scan(final Certification cert) throws GeneralException {
        
        Callable<Integer> doWhat = new Callable<Integer>() {
            
            public Integer call() throws Exception {
        
                return scanInternal(ObjectUtil.reattach(context, cert));
            }
        };
        
        Pair<Boolean, Integer> result = ObjectUtil.doWithCertLock(context, cert, doWhat, true, 0);
        if (result.getFirst()) {
            return result.getSecond();
        } else {
            if (log.isInfoEnabled()) {
                log.info("Locking failed for cert: " + cert);
            }
            return 0;
        }
    }

    private int scanInternal(Certification cert) throws GeneralException {

        // 1) Find all entities that have remediations that haven't had their
        //    remediations verified.
        // 2) Run a targeted reaggregation and refresh for the identity.
        // 3) Mark whether entitlements to be remediated have been removed.
        int numScanned = 0;

        Iterator<Object[]> it = getEntitiesToScan(cert);
        while (it.hasNext()) {
            String id = (String)(it.next()[0]);
            CertificationEntity entity = context.getObjectById(CertificationEntity.class, id);
            // jsl - this probably can't happen but I'm being thorough 
            // for 16466 
            if (entity == null) continue;

            switch(entity.getType()){
                case Identity:
                    scanIdentityRemediations(entity);
                    numScanned++;
                    break;
                case AccountGroup:
                    numScanned += scanAccountGroupRemediations(entity);
                    break;
                case BusinessRole:
                    numScanned += scanRoleRemediations(entity);
                    break;
                case DataOwner:
                    numScanned += scanDataOwner(entity);
                    break;
                default:
                    throw new GeneralException("Unhandled CertificationEntity type:" + entity.getType());
            }

        }

        // Pull up the completed remediations statistics.
        Certificationer certificationer = new Certificationer(this.context);
        certificationer.updateRemediationsCompleted(ObjectUtil.reattach(context, cert), true);

        return numScanned;
    }

    private int scanIdentityRemediations(CertificationEntity entity) throws GeneralException{
        Identity identity = entity.getIdentity(this.context);
        if (null != identity) {
            identity = refresh(identity, entity);
        }
        
        refreshRemediationCompletions(entity, identity);
        this.context.saveObject(entity);
        this.context.commitTransaction();
        this.context.decache();

        return 1;
    }

    private int scanRoleRemediations(CertificationEntity entity) throws GeneralException{
        Bundle role = context.getObjectById(Bundle.class, entity.getTargetId());
        if (role == null)
            return 0;
        refreshRemediationCompletions(entity, role);
        this.context.saveObject(entity);
        this.context.commitTransaction();
        this.context.decache();

        return 1;
    }

    private int scanAccountGroupRemediations(CertificationEntity entity) throws GeneralException{

        ManagedAttribute grp = null;

        // try the quick and easy method
        if (entity.getTargetId() != null){
            grp = context.getObjectById(ManagedAttribute.class, entity.getTargetId());
        }

        // if we cant find the group by ID, lookup the group by it's attr and attr val
        if (grp == null){
            AccountGroupService svc = new AccountGroupService(context);
            grp = svc.getAccountGroup(entity.getApplication(), entity.getReferenceAttribute(),
                    entity.getNativeIdentity());
        }

        if (grp == null)
            return 0;

        refreshRemediationCompletions(entity, grp);
        this.context.saveObject(entity);
        this.context.commitTransaction();
        this.context.decache();

        return 1;
    }
    
    private int scanDataOwner(CertificationEntity entity) throws GeneralException {

        int remediationsScanned = 0;

        for (CertificationItem item : entity.getItems()) {
            Identity ident = this.context.getObjectById(Identity.class, item.getTargetId());

            // We need to perform a targeted aggregation and refresh of the identity involved in this remediation.
            if (null != ident) {
                ident = refresh(ident, entity);
            }

            if (needToRefresh(item)) {
                refreshRemediationCompletion(item, ident);
                ++remediationsScanned;
            }
        }

        return remediationsScanned;
    }

    /**
     * Get CertificationEntities in the given Certification that have
     * remediations that have been kicked off but not yet completed.
     * 
     * @param  cert  The Certification in which to look for entities.
     * 
     * @return An iterator over the CertificationEntities in the given
     *         Certification that have remediations that have been kicked
     *         off but not yet completed.
     */
    private Iterator<Object[]> getEntitiesToScan(Certification cert)
        throws GeneralException {
        
        Filter f =
            Filter.and(Filter.eq("certification", cert),
                       Filter.eq("items.action.remediationKickedOff", true),
                       Filter.eq("items.action.remediationCompleted", false));
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        qo.setDistinct(true);
        qo.setCloneResults(true);

        // originally returned an object iterator but that can't
        // be done with the distinct option on most databases
        List<String> props = new ArrayList<String>();
        props.add("id");
        return this.context.search(CertificationEntity.class, qo, props);
    }

    /**
     * Performed a targeted re-aggregation and refresh on the given identity.
     * 
     * @param  identity  The Identity to refresh.
     * @param  entity    The CertificationEntity for which we're refreshing.
     *                   This can be used to scope the application accounts that
     *                   we re-aggregate.
     * 
     * @return The refreshed Identity.
     */
    private Identity refresh(Identity identity, CertificationEntity entity)
        throws GeneralException {

        getIdentitizer(entity).refresh(identity);
        return identity;
    }

    /**
     * Get an Identitizer configured to refresh the stuff we'll need.
     */
    private Identitizer getIdentitizer(CertificationEntity entity)
        throws GeneralException {

        if (null == this.identitizer) {
            // Turn on link refresh (re-aggregate).
            Attributes<String,Object> args = new Attributes<String,Object>();
            args.put(Identitizer.ARG_REFRESH_LINKS, true);
            // bug 22585 - after an account is revoked the link attributes 
            // need to be refreshed
            args.put(Identitizer.ARG_FORCE_LINK_ATTRIBUTE_PROMOTION, true);
            this.identitizer = new Identitizer(this.context, args);
            this.identitizer.setRefreshSource(Source.Task, "Revocation scan");
        }

        // Scope the sources to the applications that are being remediated.
        Set<Application> sources = new HashSet<Application>();
        for (CertificationItem item : entity.getItems()) {
            CertificationAction action = item.getAction();
            if ((null != action) && action.isRemediationKickedOff() &&
                !action.isRemediationCompleted()) {
                
                ProvisioningPlan plan = action.getRemediationDetails();
                if (null != plan) {
                    sources.addAll(plan.getApplications(this.context));
                }
            }
        }
        this.identitizer.setSources(new ArrayList<Application>(sources));
        
        return this.identitizer;
    }

    /**
     * Look at the given Identity to see if the remediations that were kicked
     * off have been completed, and store this info on the items in the entity.
     * 
     * @param  entity    The CertificationEntity for which to look for
     *                   remediation completions.
     * @param  identity  The refreshed Identity.
     */
    private void refreshRemediationCompletions(CertificationEntity entity,
                                               Identity identity)
        throws GeneralException {

        List<CertificationItem> items = entity.getItems();
        if (null != items) {
            for (CertificationItem item : items) {
                if (needToRefresh(item)) {
                    refreshRemediationCompletion(item, identity);
                }
            }
        }
    }

    private void refreshRemediationCompletions(CertificationEntity entity,
                                               Bundle role)
        throws GeneralException {

        List<CertificationItem> items = entity.getItems();
        if (null != items) {
            for (CertificationItem item : items) {
                if (needToRefresh(item)) {
                    refreshRemediationCompletion(item, role);
                }
            }
        }
    }

    private void refreshRemediationCompletions(CertificationEntity entity,
                                               ManagedAttribute grp)
        throws GeneralException {

        List<CertificationItem> items = entity.getItems();
        if (null != items) {
            for (CertificationItem item : items) {
                if (needToRefresh(item)) {
                    if (CertificationItem.Type.AccountGroupMembership.equals(item.getType())){
                        Identity identity = item.getIdentity(context);
                        refreshRemediationCompletion(item, identity);
                    } else if (CertificationItem.Type.Exception.equals(item.getType())){
                        refreshRemediationCompletion(item, grp);
                    }
                }
            }
        }
    }

    /**
     * Return whether we need to refresh the remediation completion for the
     * given certification item.
     */
    private boolean needToRefresh(CertificationItem item) {
        CertificationAction action = item.getAction();
        return (null != action) && action.isRemediationKickedOff() &&
               !action.isRemediationCompleted();
    }

    /**
     * Refresh whether the remediation has been completed for this item.
     */
    private void refreshRemediationCompletion(CertificationItem item,
                                              Identity identity)
        throws GeneralException {
        
        CertificationAction action = item.getAction();
        assert (null != action) : "Should have already checked for a non-null action";
        ProvisioningPlan plan = action.getRemediationDetails();

        // We've seen a null plan before ... not sure why ... log the anomaly.
        if (null == plan) {
            if (log.isWarnEnabled())
                log.warn("Provisioning plan is null for certification item: " + item);
        }

        action.setRemediationCompleted(checker.hasBeenExecuted(plan, identity));

        // Sanity check that the bundle or policy violation was removed?
        // We're not refreshing these right now.
    }

    private void refreshRemediationCompletion(CertificationItem item,
                                              Bundle bundle)
        throws GeneralException {

        CertificationAction action = item.getAction();
        assert (null != action) : "Should have already checked for a non-null action";
        ProvisioningPlan plan = action.getRemediationDetails();

        // We've seen a null plan before ... not sure why ... log the anomaly.
        if (null == plan) {
            if (log.isWarnEnabled())
                log.warn("Provisioning plan is null for certification item: " + item);
        }

        action.setRemediationCompleted(checker.hasBeenExecuted(plan, bundle));
    }

    private void refreshRemediationCompletion(CertificationItem item,
                                              ManagedAttribute group)
        throws GeneralException {

        CertificationAction action = item.getAction();
        assert (null != action) : "Should have already checked for a non-null action";
        ProvisioningPlan plan = action.getRemediationDetails();

        // We've seen a null plan before ... not sure why ... log the anomaly.
        if (null == plan) {
            if (log.isWarnEnabled())
                log.warn("Provisioning plan is null for certification item: " + item);
        }

        action.setRemediationCompleted(checker.hasBeenExecuted(plan, group));
    }

}
