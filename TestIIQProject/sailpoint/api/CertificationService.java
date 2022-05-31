/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.CertificationAuditor;
import sailpoint.api.certification.CertificationAuditor.CertificationRescindAudit;
import sailpoint.api.certification.CertificationAuditor.DelegationAudit;
import sailpoint.api.certification.CertificationAuditor.DelegationCompletionAudit;
import sailpoint.api.certification.CertificationAuditor.DelegationRevocationAudit;
import sailpoint.api.certification.CertificationAuditor.ReassignmentAudit;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.AbstractCertificationItem.Status;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationStatistic;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.search.ExternalAttributeFilterBuilder;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A service layer that deals with certifications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationService {

    private static final Log log = LogFactory.getLog(CertificationService.class);
    
    public static final String PARENT_PREFIX = "parent.";
	
	public static final String PARENT_CERTIFICATION = PARENT_PREFIX  + "certification";
	
	public static final String PARENT_LASTNAME = PARENT_PREFIX  + "lastname";

	public static final String PARENT_FIRSTNAME = PARENT_PREFIX  +"firstname";


    private SailPointContext context;

    private EmailTemplateRegistry emailTemplateRegistry;

    private IdentityHistoryService identityHistoryService;

    /**
     * Constructor.
     */
    public CertificationService(SailPointContext ctx) {
        this.context = ctx;
        emailTemplateRegistry = new EmailTemplateRegistry(ctx);
        identityHistoryService = new IdentityHistoryService(context);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // STATIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static void filterEmptyCerts(SailPointContext context, QueryOptions ops, CertificationGroup group) throws GeneralException {
        // bug 7800 - Don't get certs that have 0 entities and have no children
        // This was originally meant to avoid showing stub certs that will end up being deleted because they are empty.
        // However, we don't want to always hide empty certs. We can have empty ones now due to self certification
        // reassignment, which does not result in child certs. So lets keep this, but only for pending certification groups.
        if ((group == null) || (CertificationGroup.Status.Pending.equals(group.getStatus()))) {
            ops.add(Filter.or(
                    Filter.gt("statistics.totalEntities", 0),
                    Filter.not(Filter.isempty("hibernateCertifications")))
            );
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS TO DEAL WITH ADDITIONAL ENTITLEMENTS IN A CERTIFICATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return all of the applications on which there are additional entitlements
     * on the given certification.
     * 
     * @param  cert  The Certification on which to find additional entitlement
     *               applications.
     *
     * @return A non-null list of the applications on which there are additional
     *         entitlements on the given certification.
     */
    public List<String> getAdditionalEntitlementApplications(Certification cert)
    throws GeneralException {

        Filter f = Filter.and(Filter.eq(PARENT_CERTIFICATION, cert),
                Filter.notnull("exceptionApplication"));

        return getResults(f, "exceptionApplication");
    }

    public List<String> getBusinessRoles(Certification cert) 
    throws GeneralException{

        Filter f = Filter.and(Filter.eq(PARENT_CERTIFICATION, cert),
                Filter.eq("type", CertificationItem.Type.Bundle));
        //bug 20109 - Changed the Prop to targetDisplayName to get the dispayable name
        return getResults(f, "targetDisplayName");
    }
    
    /** Returns a certification statistic object representing the totals for all open
     * certifications currently owned by this certifier
     * @param identity Certifier
     * @return CertificationStatistic object
     */
    public CertificationStatistic getCertStatisticsForCertifier(Identity identity) 
    	throws GeneralException{
    	CertificationStatistic statistic = null;
    	
    	if(identity!=null) {
    		QueryOptions qo = new QueryOptions();
            qo.add(Filter.containsAll("certifiers", Arrays.asList(new String [] {identity.getName()})));
            qo.add(Filter.isnull("signed"));
            qo.add(Filter.ne("phase", Certification.Phase.Staged));

            String[] props = {"statistics.totalItems","statistics.completedItems",
                    "statistics.delegatedItems","statistics.overdueItems"};
            
            Iterator<Object[]> results =
                this.context.search(Certification.class, qo, Arrays.asList(props));
            if (null != results) {
            	statistic = new CertificationStatistic();
                while (results.hasNext()) {
                    Object[] result = results.next();
                    
                    statistic.setTotal(statistic.getTotal() + (Integer)result[0]);
                    statistic.setCompleted(statistic.getCompleted() + (Integer)result[1]);
                    statistic.setDelegated(statistic.getCompleted() + (Integer)result[2]);
                    statistic.setOverdue(statistic.getOverdue() + (Integer)result[3]);
                }
            }
            
    	}
    	
    	return statistic;
    }
    
    /**
     * This takes care of the Query to populate the last names select
     */
    public List<String> getLastNames(Certification cert) 
    throws GeneralException{
       
        Filter f1 = Filter.eq(PARENT_CERTIFICATION, cert);
        Filter f2 = Filter.notnull(PARENT_LASTNAME);
        Filter f= Filter.and(f1, f2);
        return getResults(f, PARENT_LASTNAME);
    }
    
    /**
     * This takes care of the Query to populate the first names select
     */
    public List<String> getFirstNames(Certification cert) 
    throws GeneralException{
       
        Filter f1 = Filter.eq(PARENT_CERTIFICATION, cert);
        Filter f2 = Filter.notnull(PARENT_FIRSTNAME);
        Filter f= Filter.and(f1, f2);
        return getResults(f, PARENT_FIRSTNAME);
    }

    /**
     * Return all of the attribute names for which there are additional
     * entitlements on the given certification.
     * 
     * @param  cert         The Certification on which to find additional
     *                      entitlement attribute names.
     * @param  application  The application for which to return attribute names.
     *
     * @return A non-null list of the attribute namesfor which there are
     *         additional entitlements on the given certification.
     *
     * @throws GeneralException  If the entitlement granularity of the
     *                           certification is coarser than "Attribute".
     */
    public List<String> getAdditionalEntitlementAttributes(Certification cert,
            String application)
            throws GeneralException {

        // Throw if the granularity is coarser than Attribute.
        if (Certification.EntitlementGranularity.Attribute.compareTo(cert.getEntitlementGranularity()) > 0) {
            throw new GeneralException("Cannot calculate additional entitlement attributes " +
                    "for certification with entitlement granularity: " +
                    cert.getEntitlementGranularity());
        }

        Filter f = Filter.and(Filter.eq(PARENT_CERTIFICATION, cert),
                Filter.eq("exceptionApplication", application),
                Filter.notnull("exceptionAttributeName"));

        return getResults(f, "exceptionAttributeName");
    }

    /**
     * Return all of the permission targets for which there are additional
     * entitlements on the given certification.
     * 
     * @param  cert         The Certification on which to find additional
     *                      entitlement permission targets.
     * @param  application  The application for which to return permissions.
     *
     * @return A non-null list of the permission targets for which there are
     *         additional entitlements on the given certification.
     *
     * @throws GeneralException  If the entitlement granularity of the
     *                           certification is coarser than "Attribute".
     */
    public List<String> getAdditionalEntitlementPermissionTargets(Certification cert,
            String application)
            throws GeneralException {

        // Throw if the granularity is coarser than Attribute.
        if (Certification.EntitlementGranularity.Attribute.compareTo(cert.getEntitlementGranularity()) > 0) {
            throw new GeneralException("Cannot calculate additional entitlement permissions " +
                    "for certification with entitlement granularity: " +
                    cert.getEntitlementGranularity());
        }

        Filter f = Filter.and(Filter.eq(PARENT_CERTIFICATION, cert),
                Filter.eq("exceptionApplication", application),
                Filter.notnull("exceptionPermissionTarget"));

        return getResults(f, "exceptionPermissionTarget");
    }

    /**
     * Return all of the additional entitlement values found for the given
     * application/attribute in the given certification.
     * 
     * @param  cert         The Certification on which to find the values.
     * @param  application  The application for which to find the values.
     * @param  attribute    The attribute for which to find the values.
     *
     * @return A non-null list of the additional entitlement values found for
     *         the given application/attribute on the given certification.
     *
     * @throws GeneralException  If the entitlement granularity of the
     *                           certification is coarser than "Value".
     */
    public List<String> getAdditionalEntitlementAttributeValues(Certification cert,
            String application,
            String attribute)
            throws GeneralException {

        // Throw if the granularity is coarser than Value.
        if (Certification.EntitlementGranularity.Value.compareTo(cert.getEntitlementGranularity()) > 0) {
            throw new GeneralException("Cannot calculate additional entitlement attributes " +
                    "for certification with entitlement granularity: " +
                    cert.getEntitlementGranularity());
        }

        Filter f = Filter.and(Filter.eq(PARENT_CERTIFICATION, cert),
                Filter.eq("exceptionApplication", application),
                Filter.eq("exceptionAttributeName", attribute),
                Filter.notnull("exceptionAttributeValue"));
        // Bug #22578 - ignoreCase boolean added so getLevel2SelectItems are sorted case insensitive to match mysql results 
        // when using non mysql dbs
        return getResults(f, "exceptionAttributeValue", true);
    }

    /**
     * Return all of the additional entitlement values found for the given
     * application/attribute in the given certification.
     * 
     * @param  cert         The Certification on which to find the values.
     * @param  application  The application for which to find the values.
     * @param  target    The attribute for which to find the values.
     *
     * @return A non-null list of the additional entitlement values found for
     *         the given application/attribute on the given certification.
     *
     * @throws GeneralException  If the entitlement granularity of the
     *                           certification is coarser than "Value".
     */
    public List<String> getAdditionalEntitlementPermissionRights(Certification cert,
            String application,
            String target)
            throws GeneralException {

        // Throw if the granularity is coarser than Value.
        if (Certification.EntitlementGranularity.Value.compareTo(cert.getEntitlementGranularity()) > 0) {
            throw new GeneralException(
                    new Message(Message.Type.Error, MessageKeys.ERR_CALCULATING_ATTRS_W_ENT_GRAN,
                            cert.getEntitlementGranularity()));                                    
        }

        Filter f = Filter.and(Filter.eq(PARENT_CERTIFICATION, cert),
                Filter.eq("exceptionApplication", application),
                Filter.eq("exceptionPermissionTarget", target),
                Filter.notnull("exceptionPermissionRight"));

        return getResults(f, "exceptionPermissionRight");
    }

    /**
     * Return the name of the identity attribute if the given property name
     * (which may be prefixed with "Identity.") is a mutli-valued identity
     * attribute, or false if it is not.
     */
    public static String getIdentityPropertyIfMulti(String propName) {

        String prop = null;

        final String IDENTITY_PREFIX = "Identity.";
        if (propName.startsWith(IDENTITY_PREFIX)) {
            String identityProp = propName.substring(IDENTITY_PREFIX.length());
            ObjectConfig oc = Identity.getObjectConfig();
            ObjectAttribute attr = oc.getObjectAttribute(identityProp);
            if ((null != attr) && attr.isMulti()) {
                prop = identityProp;
            }
        }

        return prop;
    }
    
    public void refreshSummaryStatus(CertificationEntity entity) throws GeneralException {
        refreshSummaryStatus(entity, null, false);
    }

    /**
     * A less intense way of refreshing the summary status for a CertificationEntity
     * that requires the SailPointContext
     * 
     * Derive a status summary for the entity. This does not refresh the status
     * of the child items - it is assumed that these are already refreshed.
     *
     * Some ambiguity on what "reviewed" means for the entity.
     * If the delegation says reviewRequired, then it seems redundant
     * to require that the entity have an CertificationAction with the reviewed
     * flag set, it can be imply that this is true if all of the child items have been
     * reviewed.
     */
    public void refreshSummaryStatus(CertificationEntity entity, Boolean completeOverride, Boolean fullRefresh) throws GeneralException {
        // If the fullRefresh flag is false, use a projection search. During certification generation, it's preferable
        // to avoid the projection search as the certification object is still being built
        if (!fullRefresh) {
            // If we're doing a projection search, we probably need to commit anything outstanding first
            context.commitTransaction();

            QueryOptions opts = new QueryOptions();
            opts.add(Filter.eq("parent", entity));
            Iterator<Object[]> results = context.search(CertificationItem.class, opts, "summaryStatus, completed");
            boolean completed = true;
            Status status = Status.Complete;
            while (results.hasNext()) {
                Object[] result = results.next();
                Status itemStatus = (Status)result[0];
                Date completedDate = (Date)result[1];
                completed &= completedDate != null;
                if (itemStatus != null && itemStatus.compareTo(status) > 0) {
                    status = itemStatus;
                }
            }
            entity.applySummaryStatus(completeOverride, status, completed);
        } else {
            entity.refreshSummaryStatus(completeOverride);
        }
    }
    
    /**
     * Returns items that are to be refreshed for the provided entity. If refreshAll is true, then
     * all items are returned
     */
    public Iterator<CertificationItem> getItemsToRefresh(AbstractCertificationItem item, boolean refreshAll) 
            throws GeneralException {
        if (item == null) {
            return null;
        }

        if (refreshAll) {
            return item.getItems() != null ? item.getItems().iterator() : null;
        }
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent", item), Filter.eq("needsRefresh", true));
        Iterator<CertificationItem> results = context.search(CertificationItem.class, ops);
        List<CertificationItem> items = new ArrayList<CertificationItem>();
        while (results.hasNext()) {
            items.add(results.next());
        }
        // to be consistent with the possible return values of entity.getItems, I want to return
        // a null value instead of an empty list
        return items.isEmpty() ? null : items.iterator();
    }
    
    public List<String> getIdentityAttributes(Certification cert, String attrName) 
    throws GeneralException{

        List<String> resultList = null;

        // Check whether the attribute is multi-valued or not.
        String multiProp = getIdentityPropertyIfMulti(attrName);
        boolean isMulti = (null != multiProp);
        attrName = (isMulti) ? multiProp : attrName;

        // The column we're selecting will change if this is a multi.
        String attrProp =
            (!isMulti) ? attrName : ExternalAttributeFilterBuilder.IDENTITY_EXTERNAL + ".value";

        // Join the identity to the certification.
        Filter f = Filter.and(Filter.join("name", "CertificationEntity.identity"),
                Filter.eq("CertificationEntity.certification", cert));

        // If this is a multi-valued property, we have to join to the external
        // attribute table.
        if (isMulti) {
            List<Filter> filters =
                ExternalAttributeFilterBuilder.buildBaseAttributeFilters(
                      ExternalAttributeFilterBuilder.IDENTITY_EXTERNAL,
                      ExternalAttributeFilterBuilder.IDENTITY_JOIN, attrName);
            filters.add(f);
            f = Filter.and(filters);
        }
        
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        qo.add(Filter.notnull(attrProp));
        qo.setDistinct(true);
        qo.setOrderBy(attrProp);

        List<String> props = new ArrayList<String>();
        props.add(attrProp);

        Iterator<Object[]> results =
            this.context.search(Identity.class, qo, props);
        if (null != results) {
            resultList = new ArrayList<String>();
            while (results.hasNext()) {
                Object[] result = results.next();
                resultList.add((String) result[0]);
            }
        }

        return resultList;
    }

    /**
     * Query the CertificationItems for distinct values found 
     * for an extended attribute.
     */
    public List<String> getExtendedItemAttributes(Certification cert, 
                                                  String attrName) 
        throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq(PARENT_CERTIFICATION, cert));
        qo.add(Filter.notnull(attrName));
        qo.setDistinct(true);
        qo.setOrderBy(attrName);

        List<String> props = new ArrayList<String>();
        props.add(attrName);

        List<String> resultList = null;
        Iterator<Object[]> results =
            this.context.search(CertificationItem.class, qo, props);
        if (results != null) {
            resultList = new ArrayList<String>();
            while (results.hasNext()) {
                Object[] result = results.next();
                resultList.add((String) result[0]);
            }
        }
        return resultList;
    }

    /**
     * Run a projection query using the given Filter and retrieve a list with
     * the values for the requested String attribute name.
     * 
     * @param  f         The filter to use.
     * @param  attrName  The name of the String attribute to get the values of.
     * 
     * @return A non-null list with the values for the requested String
     *         attribute name.
     */
    private List<String> getResults(Filter f, String attrName)
    throws GeneralException {
        return getResults(f, attrName, false);
    }

    /**
     * Overloaded version of getResults to support ignore casing in the query
     *
     * @param  f                  The filter to use.
     * @param  attrName           The name of the String attribute to get the values of.
     * @param  orderingIgnoreCase To ignore case for the ordering(order by) in the query. Not associated with 
     *         ignoring case in the where clause such as in a Filter.
     * @return A non-null list with the values for the requested String
     *         attribute name.
     * @throws GeneralException
     */
	private List<String> getResults(Filter f, String attrName, boolean orderingIgnoreCase) 
	throws GeneralException {

        List<String> resultList = new ArrayList<String>();

        QueryOptions qo = new QueryOptions();
        qo.add(f);
        qo.setDistinct(true);
        // Bug #22578 - Changed to addOrdering from setOrderBy to support ignore case with the ordering
        qo.addOrdering(attrName, true, orderingIgnoreCase);

        List<String> props = new ArrayList<String>();
        props.add(attrName);

        Iterator<Object[]> results =
            this.context.search(CertificationItem.class, qo, props);
        if (null != results) {
            while (results.hasNext()) {
                Object[] result = results.next();
                resultList.add((String) result[0]);
            }
        }

        return resultList;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // CALCULATED CERTIFICATION STATUS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate whether this certification can be signed or not.
     * 
     * @param  cert  The Certification fow which to calculate the sign off
     *               status.
     *
     * @return True if cert can be signed off, otherwise false.  
     */
    public boolean isReadyForSignOff(Certification cert) throws GeneralException {
        return getSignOffBlockedReason(cert) == null;
    }
    
    /**
     * Get message key indicating why the certification cannot be signed.
     * 
     * @param  cert  The Certification fow which to calculate the sign off
     *               status.
     *               
     * @return String message key with reason for signoff being blocked, or null if not blocked.              
     */
    public String getSignOffBlockedReason(Certification cert)
            throws GeneralException {

        if (cert.hasBeenSigned()) {
            return MessageKeys.INST_CERT_COMPLETE;
        }

        if (!cert.isComplete()) {
            // Some items are not complete.
            if (cert.getCompletedEntities() != cert.getTotalEntities()) {
                return this.getCompleteMessage(cert.getType());
            }
            else {
                // All items are complete but the cert isn't complete.
                // We're being limited by the sub-certs or reassignments.
                return MessageKeys.INST_COMPLETE_SUB_CERTS;
            }
        }
        else if (cert.isCompleteHierarchy() && !Certification.isChildCertificationsComplete(context, cert.getId())) {
            // Parent cert is complete, but sub-certs still need to be signed off.
            return MessageKeys.INST_COMPLETE_SUB_CERTS;
        }
        
        // If we're assimilating bulk reassignments, don't allow signing off
        // until all child certs have been assimilated.  This will ensure
        // that the parent is able to review all subordinate items before
        // signing off.
        // Also verify all reassignments are signed off, if requiring reassignment 
        // completion was selected. 
        if (this.isSignOffBlockedBySubCertAssimilation(cert) || this.isSignOffBlockedByReassignedCerts(cert)) {
            return MessageKeys.INST_SIGN_OFF_AFTER_SUB_CERTS_ASSIMILATED;
        }

        if (this.isSignoffBlockedByChallengePeriod(cert)) {
            return MessageKeys.INST_CHALLENGE_AFTER_SIGN_OFF;
        }

        return null;
    }

    /**
     * Return the "items need to be completed" message that is appropriate for
     * the certification type.
     */
    private String getCompleteMessage(Certification.Type type) throws GeneralException {
        String message = null;
        if (type.isIdentity()) {
            message = MessageKeys.INST_COMPLETE_CERT_ID;
        } else if (Certification.Type.AccountGroupMembership.equals(type) || Certification.Type.AccountGroupPermissions.equals(type)) {
            message = MessageKeys.INST_COMPLETE_CERT_ACT_GRP;
        } else if (Certification.Type.BusinessRoleComposition.equals(type)) {
            message = MessageKeys.INST_COMPLETE_CERT_BIZ_ROLE_PROFILES;
        } else {
            message = MessageKeys.INST_COMPLETE_CERT_TYPE;
        }
        return message;
    }
    
    /**
     * Return true if signing off on the certification is being prevented due
     * to sub-cert assimilation being enabled and not all sub-certs being
     * assimilated yet.
     * 
     */
    public boolean isSignOffBlockedBySubCertAssimilation(Certification cert)
    throws GeneralException {

        // If we're assimilating bulk reassignments, don't allow signing off
        // until all bulk reassignment child certs have been assimilated.  This
        // will ensure that the parent is able to review all subordinate items
        // before signing off.
        Boolean assimilateBulkReassignments = null;
        // for older certs this attribute will live on the cert object
        if (cert.getAttribute(Configuration.ASSIMILATE_BULK_REASSIGNMENTS, null) != null) {
            assimilateBulkReassignments = cert.isAssimilateBulkReassignments(context.getConfiguration().getAttributes());
        }
        else {
            // for the newer certs this value lives in the cert definition object
            CertificationDefinition certDef = cert.getCertificationDefinition(context);
            if (certDef != null) {
                assimilateBulkReassignments = certDef.isAssimilateBulkReassignments(context);
            }
            else {
                assimilateBulkReassignments = false;
            }
        }
        
        return assimilateBulkReassignments && cert.hasBulkReassignments();
    }
    
    /**
     * Return true if signing off on the certification is being prevented due
     * to certification reassignments not being signed off yet.
     * 
     */
    public boolean isSignOffBlockedByReassignedCerts(Certification cert)
    throws GeneralException {
        Attributes<String,Object> sysConfig =
            this.context.getConfiguration().getAttributes();

        // Don't allow signing off unless all reassignments are signed off
        if (cert.isRequireReassignmentCompletion(sysConfig) && cert.hasBulkReassignments()) {
            List<Certification> children = cert.getCertifications();
            if (children != null) {
                for (Certification child : children) {
                    if (child.isBulkReassignment()) {
                        if (!child.hasBeenSigned()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;    
    }
    
    /**
     * Check if signoff should be blocked due to challenge period related business.
     */
    public boolean isSignoffBlockedByChallengePeriod(Certification cert) throws GeneralException {
        boolean ready = false;

        // Can be signed if Challenge phase is not enabled
        if (!cert.isPhaseEnabled(Certification.Phase.Challenge)) {
            ready = true;
        }
        else {

            // Challenge phase is enabled.  Allow sign off in two situations:
            //  1) Before challenge period but there are no remediation
            //     decisions (ie - no challenges will be generated).
            //  2) During or after challenge period, or using rolling phases,
            //     where there are no active challenges.
            if (cert.isUseRollingPhases() == false && 
                    (cert.getPhase() == null || Certification.Phase.Challenge.compareTo(cert.getPhase()) > 0)) {

                // Before the challenge period and not using rolling phases - look for remediations.
                QueryOptions qo = new QueryOptions();
                Filter f =
                    Filter.and(Filter.eq("certification", cert),
                            Filter.eq("items.action.status", CertificationAction.Status.Remediated));
                qo.add(f);
                int count = this.context.countObjects(CertificationEntity.class, qo);

                // Ready for sign off if there are no remediations.
                ready = (0 == count);
            }
            else {

                ready = false;

                // We're either in or past the challenge period, or using rolling phases.  
                // Look for active challenges (note - see
                // CertificationChallenge.isActive() for a description of
                // this logic.

                // First, look for items that still have challenge work items.
                Filter f = Filter.and(Filter.eq("certification", cert),
                        Filter.notnull("items.challenge.workItem"));
                QueryOptions qo = new QueryOptions();
                qo.add(f);
                int count = this.context.countObjects(CertificationEntity.class, qo);
                
                if (log.isInfoEnabled())
                    log.info("Found " + count + " active challenge work items " +
                             "for certification: " + cert);

                // If there weren't any challenge work items still alive,
                // see if there are any challenged items that do not have
                // decisions and aren't yet expired.
                if (count == 0) {
                    ready = !isSignoffBlockedByChallengeDecisions(cert); 
                }
            }
        }

        return !ready;
    }
    
    /**
     * Check for challenge decisions that need some action
     */
    public boolean isSignoffBlockedByChallengeDecisions(Certification cert) throws GeneralException {
        Filter f = Filter.and(Filter.eq("certification", cert),
                Filter.eq("items.challenge.challenged", true),
                Filter.isnull("items.challenge.decision"),
                Filter.eq("items.challenge.challengeDecisionExpired", false));

        QueryOptions qo = new QueryOptions();
        qo.add(f);
        int count = this.context.countObjects(CertificationEntity.class, qo);
        
        if (log.isInfoEnabled())
            log.info("Found " + count + " non-expired challenged items awaiting " + 
                     "decisions on certification: " + cert);

        return (0 < count);
    }
    
    
    /** 
     * Determines whether this certification item contains 
     * a comment in its history that match the entitlement
     * certified in the given CertificationItem
     */
    public boolean hasComment(String identityId, String certItemId) throws GeneralException{

        Iterator<Object[]> iter = this.context.search(CertificationItem.class,
                new QueryOptions(Filter.eq("id", certItemId)), Arrays.asList("type", "bundle",
                        "policyViolation", "exceptionEntitlements", "parent.certification.entitlementGranularity", "action"));
        if (iter != null && iter.hasNext()){
            Object[] row = iter.next();
            CertificationItem.Type type = (CertificationItem.Type)row[0];
            // bug 16792, we also want to check if there's any comment from certification action
            CertificationAction itemAction = (CertificationAction)row[5];
            if(itemAction != null && itemAction.getComments() != null){
                return true;
            }
            if (CertificationItem.Type.Bundle.equals(type)){
                String role = (String)row[1];
                return identityHistoryService.countComments(identityId, role) > 0;
            } else if (CertificationItem.Type.PolicyViolation.equals(type)){
                PolicyViolation violation = (PolicyViolation)row[2];
                return identityHistoryService.countComments(identityId, violation) > 0;
            } else if (CertificationItem.Type.Exception.equals(type) ||
                    CertificationItem.Type.AccountGroupMembership.equals(type)){
                EntitlementSnapshot ents = (EntitlementSnapshot)row[3];
                Certification.EntitlementGranularity granularity = 
                        (Certification.EntitlementGranularity)row[4];
                return identityHistoryService.countComments(identityId, ents, granularity) > 0;
            }
        }

        return false;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CHALLENGE A DECISION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The given user is saying that they accept a decision that is available
     * to be challenged via the given WorkItem.
     * 
     * @param  who       The Identity accepting the certification decision.
     * @param  workItem  The challenge WorkItem.
     * @param  certItem  The CertificationItem available for challenge.
     */
    public void acceptCertificationDecision(Identity who, WorkItem workItem,
                                            CertificationItem certItem)
    throws GeneralException {

        certItem.getChallenge().acceptDecision(who, workItem.getId());

        // Set the completion state on the work item.
        workItem.setState(WorkItem.State.Finished);

        // This assimilates the challenge back into the certification.
        Workflower wf = new Workflower(context);
        wf.process(workItem, true);
    }

    /**
     * The given user is challenging the given CertificationItem.  Challenge the
     * item and send out the appropriate emails.
     * 
     * @param  who       The Identity challenging the certification item.
     * @param  workItem  The WorkItem from which the challenge is coming.
     * @param  certItem  The CertificationItem being challenged.
     * @param  comments  The comments from the challenger about the challenge.
     * 
     * @throws EmailException  If any of the emails failed to be sent.  This
     *                         does not prevent the item from being challenged.
     */
    public void challengeItem(Identity who, WorkItem workItem,
                              CertificationItem certItem, String comments)
        throws GeneralException, EmailException {

        // Challenge the item.
        certItem.getChallenge().challenge(who, workItem.getId(), comments);

        // Save the info on the work item.
        workItem.setState(WorkItem.State.Finished);
        workItem.setCompletionComments(comments);

        // This assimilates the challenge back into the certification.
        // This assimilates the challenge back into the certification.
        Workflower wf = new Workflower(context);
        wf.process(workItem, true);

        // Send the notifications - this can throw an exception.
        sendChallengeNotification(who, certItem, comments, workItem);
    }

    /**
     * The certifier is accepting the challenge from the challenger, and will
     * choose a new decision for this item.
     * 
     * @param  who       The person accepting the challenge.
     * @param  comments  The comments about the accepted challenge.
     * @param  item      The item on which the challenge is being accepted.
     * 
     * @throws EmailException  If the email failed to be sent.  This does not
     *                         prevent challenge from being accepted.
     */
    public void acceptChallenge(Identity who, String comments,
                                CertificationItem item)
        throws GeneralException, EmailException {

        // Save the name away.  We'll need it to send the email later and
        // accepting the challenge may null it out.
        String challengerName = item.getChallenge().getOwnerName();

        CertificationAction action = item.getAction();
        
        // If this is a response to a challenge on a revoke account
        // handle the associated items as well.
        if (null != action) {
            if (action.isRevokeAccount()) {
                acceptChallengeAccount(who, comments, item);
            }
        }
        
        // Accept the challenge.
        item.acceptChallenge(who, comments);

        // jsl - this is no longer assimilating automatically   
        // via special code in InternalContext, is that ok?
        this.context.saveObject(item);
    
        // Send the notification - this can throw an exception.
        sendChallengeAcceptedNotification(who, comments, item, challengerName);
    }
    
    /**
     * This is a helper function in the case that someone is accepting a
     * challenge of a revoke account.  The challenge is stored on only
     * one item, so we have to process the rest of the associated items.
     * 
     * @param  who       The person accepting the challenge.
     * @param  comments  The comments about the accepted challenge.
     * @param  item      The item on which the challenge is being accepted.
     */
    private void acceptChallengeAccount(Identity who, String comments,
                                       CertificationItem item)
            throws GeneralException {
        
        // if this is a revoke account, we need to accept every item that 
        // is on the account, otherwise only one item (the one with the challenge
        // on it) is accepted - see bug 7162
        CertificationEntity ent = item.getParent();
        List<CertificationItem> certItems = ent.getItemsOnSameAccount(item);
        for (CertificationItem certItem : certItems) {
            // when a revoke account is decided and then challenged, the other entitlements
            // don't get pushed into the challenge phase.  We need to mark it here.
            certItem.setPhase(Certification.Phase.Challenge);
            certItem.acceptChallenge(who, comments);
            this.context.saveObject(certItem);
        }
    }

    /**
     * The certifier is rejecting the challenge from the challenger, and the
     * original decision for the item will remain.
     * 
     * @param  who       The person rejecting the challenge.
     * @param  comments  The comments about the rejection.
     * @param  item      The item on which the challenge is being rejected.
     * 
     * @throws EmailException  If the email failed to be sent.  This does not
     *                         prevent challenge from being rejected.
     */
    public void rejectChallenge(Identity who, String comments,
                                CertificationItem item)
        throws GeneralException, EmailException {

        CertificationAction action = item.getAction();
        
        // If this is a response to a challenge on a revoke account
        // handle the associated items as well.
        if (null != action) {
            if (action.isRevokeAccount()) {
                rejectChallengeAccount(who, comments, item);
            }
        }
        
        // Reject the challenge.
        item.rejectChallenge(who, comments);
        this.context.saveObject(item);

        // Send the notification - this can throw an exception.
        sendChallengeRejectedNotification(who, comments, item);
    }
    
    /**
     * This is a helper function in the case that we encounter a challenge that
     * is associated with a revoke account.  We have to process all of the items
     * on the account.  The certifier is rejecting the challenge from the challenger, and the
     * original decision for the item will remain.
     * 
     * @param  who       The person rejecting the challenge.
     * @param  comments  The comments about the rejection.
     * @param  item      The item on which the challenge is being rejected.
     */
    private void rejectChallengeAccount(Identity who, String comments, CertificationItem item)
            throws GeneralException {
        // if this is a revoke account, we need to reject every item that 
        // is on the account, otherwise only one item (the one with the challenge
        // on it) is rejected - see bug 7162
        CertificationEntity ent = item.getParent();
        List<CertificationItem> certItems = ent.getItemsOnSameAccount(item);
        for (CertificationItem certItem : certItems) {
            // when a revoke account is decided and then challenged, the other entitlements
            // don't get pushed into the challenge phase.  We need to mark it here.
            certItem.setPhase(Certification.Phase.Challenge);
            certItem.rejectChallenge(who, comments);
            this.context.saveObject(certItem);
        }
    }

    /**
     * Send a notification to the certifiers that an item is being challenged.
     * 
     * @param  who       The challenger.
     * @param  certItem  The item being challenged.
     * @param  comments  The comments from the challenger.
     */
    private void sendChallengeNotification(Identity who,
                                           CertificationItem certItem,
                                           String comments,
                                           WorkItem workItem)
        throws GeneralException, EmailException {

        EmailTemplate et = emailTemplateRegistry.getTemplate(certItem.getCertification(),
                Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE);

        if (null == et) {
            log.warn("Email template for challenge notification not found.");
        }
        else {
            Certification cert = certItem.getCertification();

            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put("challengeItem", certItem.getShortDescription());
            vars.put("certificationName", cert.getName());
            vars.put("challengerName", Util.getFullname(who.getFirstname(), who.getLastname()));
            vars.put("challengeComments", comments);
            vars.put("certificationItem", certItem);
            vars.put("workItem", workItem);

            List<String > emails =  getCertifierEmails(cert,et);
            EmailException emailException = null;
            EmailOptions options = new EmailOptions(emails, vars);
            try {
                this.context.sendEmailNotification(et, options);
            }
            catch (EmailException e) {
                // Save and continue, we'll throw this later.  We could
                // store all of these in a compound exception.
                emailException = e;
            }

            // Now that we've finished iterating, throw if there was an
            // exception sending the email.
            if (null != emailException) {
                throw emailException;
            }
        }
    }

    /**
     * Send a notification to the challenger that the challenge is being
     * accepted.
     * 
     * @param  who       The certifier accepting the challenge.
     * @param  certItem  The item with the challenge being accepted.
     */
    private void sendChallengeAcceptedNotification(Identity who, String comments,
                                                   CertificationItem certItem,
                                                   String challengerName)
        throws GeneralException, EmailException {

        EmailTemplate et = emailTemplateRegistry.getTemplate(certItem.getCertification(),
                Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE);

        if (null == et) {
            log.warn("Email template for challenge accepted notification not found.");
        }
        else {
            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put("challengeItem", certItem.getShortDescription());
            vars.put("decisionComments", comments);
            vars.put("certifierName", Util.getFullname(who.getFirstname(), who.getLastname()));
            vars.put("certificationItem", certItem);
            CertificationChallenge challenge = certItem.getChallenge();
            WorkItem workItem = null;
            if (challenge != null) {
                workItem = challenge.getWorkItem(context);
            }
            vars.put("workItem", workItem);


            List<String> emails = getChallengerEmails(challengerName, et);
            if ( emails != null )  {
                EmailOptions options = new EmailOptions(emails,vars);
                this.context.sendEmailNotification(et, options);
            }
        }
    }

    /**
     * Send a notification to the challenger that the challenge is being
     * rejected.
     * 
     * @param  who       The certifier rejecting the challenge.
     * @param  comments  The comments about the rejection.
     * @param  certItem  The item with the challenge being rejected.
     */
    private void sendChallengeRejectedNotification(Identity who, String comments,
                                                   CertificationItem certItem)
        throws GeneralException, EmailException {

        EmailTemplate et = emailTemplateRegistry.getTemplate(certItem.getCertification(),
                Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE);

        if (null == et) {
            log.warn("Email template for challenge rejection not found.");
        }
        else {
            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put("challengeItem", certItem.getShortDescription());
            vars.put("rejectionComments", comments);
            vars.put("certifierName", Util.getFullname(who.getFirstname(), who.getLastname()));

            vars.put("certificationItem", certItem);
            CertificationChallenge challenge = certItem.getChallenge();
            WorkItem workItem = null;
            if (challenge != null) {
                workItem = challenge.getWorkItem(context);
            }
            vars.put("workItem", workItem);
            
            List<String> emails = getChallengerEmails(certItem.getChallenge().getOwnerName(),et);
            if ( emails != null )  {
                EmailOptions options = new EmailOptions(emails,vars);
                this.context.sendEmailNotification(et, options);
            }
        }
    }

    /**
     * Get the email address of the given challenger.
     * 
     * @param  name  The name of the challenger for which to get the email.
     * @param  et    The EmailTemplate that we're trying to send.
     * 
     * @return The email address of the challenger of the given item, or null if
     *         there is none.
     */
    private List<String> getChallengerEmails(String name, EmailTemplate et)
        throws GeneralException {

        List<String> email = null;

        Identity identity = this.context.getObjectByName(Identity.class, name);
        if (null != identity) {
            email = ObjectUtil.getEffectiveEmails(this.context, identity);
            if (null == email) {
                if (log.isWarnEnabled())
                    log.warn("Challenger (" + name + ") has no email. " +
                             "Could not send email: " + et.getName());
            }
        }
        return email;
    }

    /**
     * Get the email addresses of all of the certifiers for the given
     * certification.  This logs warnings for any certifiers that do not have
     * email addresses.
     * 
     * @param  cert  The Certification from which to get the emails.
     * @param  et    The EmailTemplate being sent.
     * 
     * @return A non-null list of email addresses of all of the certifiers for
     *         the given certification.
     */
    public List<String> getCertifierEmails(Certification cert, EmailTemplate et)
        throws GeneralException {

        List<String> emails = new ArrayList<String>();

        List<String> certifiers = cert.getCertifiers();
        for (String certifier : certifiers) {

            Identity owner = context.getObjectByName(Identity.class, certifier);
            if (null != owner) {
                List<String> ownerEmails = ObjectUtil.getEffectiveEmails(context, owner);
                if ( ownerEmails == null ) {
                    String tempName = (et != null ) ? et.getName() : "unknown";
                    if (log.isWarnEnabled())
                        log.warn("Certification owner (" + owner.getName() + ") has no email. " +
                                 "Could not send email: " + tempName);
                } else {
                    emails.addAll(ownerEmails);
                }
            }
        }

        return emails;
    }

    /**
     * Get the Identity object for all of the certifiers for the given
     * certification.  
     * 
     * @param  cert  The Certification from which to get the emails.
     * 
     * @return A non-null list of Identity objects for all of the certifiers for
     *         the given certification.
     */
    public List<Identity> getCertifierIdentities(Certification cert) 
        throws GeneralException {

        List<Identity> identities = new ArrayList<Identity>();
        List<String> certifiers = cert.getCertifiers();
        for (String certifier : certifiers) {
            Identity owner = context.getObjectByName(Identity.class, certifier);
            if (null != owner)
                identities.add(owner);
        }
        return identities;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CERTIFICATION ITEM OWNER HISTORY
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * CertItemOwnerHistory represents historical information about how a
     * certification item was decided upon - mainly focusing on ownership
     * changes.
     */
    public static class CertItemOwnerHistory {

        /**
         * An enumeration of the reasons that a certification item was owned
         * by a particular identity.
         */
        public static enum Reason {
            // Certification creation reasons.
            CertificationOwner,
            Reassignment,
            CertificationRescinded,

            // Delegation reasons.
            Delegation,
            DelegationCompletion,
            DelegationExpiration,
            DelegationReturned,
            DelegationRevoked,
            
            // Forwarding reasons.
            ManualForward,
            AutoForwardUser,
            AutoForwardRule,
            Escalation,
            Inactive,
            SecondaryApproval;

            /**
             * Convert a ForwardType to a Reason.
             */
            public static Reason from(Workflower.ForwardType type) {
                Reason reason = null;
                switch (type) {
                    case Escalation: reason = Escalation; break;
                    case ForwardingUser: reason = AutoForwardUser; break;
                    case ForwardingRule: reason = AutoForwardRule; break;
                    case Manual: reason = ManualForward; break;
                    case Inactive: reason = Inactive; break;
                    case SecondaryApproval: reason = SecondaryApproval; break;
                }
                return reason;
            }

            /**
             * Convert a delegation work item state to a Reason.
             */
            public static Reason from(WorkItem.State state) {
                Reason reason;
                switch (state) {
                    case Finished: reason = DelegationCompletion; break;
                    case Expired: reason = DelegationExpiration; break;
                    case Returned: reason = DelegationReturned; break;
                    default: throw new RuntimeException("Unhandled state: " + state);
                }
                return reason;
            }
        }
        
        private String previousOwner;
        private String newOwner;
        private Reason reason;
        private Date date;
        private String comments;
        private String source;
        private boolean decisionMade;

        /**
         * Constructor.
         */
        public CertItemOwnerHistory(String prevOwner, String newOwner, Reason reason,
                                    Date date, String comments, String source) {
            this.previousOwner = prevOwner;
            this.newOwner = newOwner;
            this.reason = reason;
            this.date = date;
            this.comments = comments;
            this.source = source;
        }

        /**
         * Return the name of the identity (or workgroup) that previously owned
         * the item.
         */
        public String getPreviousOwner() {
            return this.previousOwner;
        }

        /**
         * Return the name of the identity (or workgroup) that now owns the item.
         */
        public String getNewOwner() {
            return this.newOwner;
        }

        /**
         * Return the reason that the new owner received the item.
         */
        public Reason getReason() {
            return this.reason;
        }

        /**
         * Return the date at which the new owner gained ownership of the item.
         */
        public Date getDate() {
            return this.date;
        }

        /**
         * Return any comments about the ownership change.
         */
        public String getComments() {
            return this.comments;
        }

        /**
         * Return the name of the identity that caused the ownership change.
         */
        public String getSource() {
            return this.source;
        }

        /**
         * Return whether the decision was made in this item.
         */
        public boolean isDecisionMade() {
            return this.decisionMade;
        }

        /**
         * Set whether the decision was made in this item.  Unlike other
         * properties, this can be set after the object has been constructed.
         */
        public void setDecisionMade(boolean decisionMade) {
            this.decisionMade = decisionMade;
        }
        
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    /**
     * A comparator that can compare CertItemOwnerHistory objects by their dates.
     */
    public static class CertItemOwnerHistoryDateComparator
        implements Comparator<CertItemOwnerHistory> {
        
        private boolean ascending;
        
        public CertItemOwnerHistoryDateComparator(boolean asc) {
            this.ascending = asc;
        }

        public int compare(CertItemOwnerHistory o1, CertItemOwnerHistory o2) {
            Date d1 = (this.ascending) ? o1.getDate() : o2.getDate();
            Date d2 = (this.ascending) ? o2.getDate() : o1.getDate();
            return d1.compareTo(d2);
        }
    }

    /**
     * Sort the CertItemOwnerHistory objects, first by date, then secondarily by
     * ownership chain if dates match
     * @param histories List of CertItemOwnerHistory objects
     * @param ascending True if order is ascending, otherwise false
     */
    protected void sortHistories(List<CertItemOwnerHistory> histories, boolean ascending) {

        Collections.sort(histories, new CertItemOwnerHistoryDateComparator(ascending));

        // Now iterate through and look for matching dates, find the set of histories with the same date, and 
        // try to order based on previous owner/new owner.
        if (Util.size(histories) > 1) {
            for (int i = 0; i + 1 < histories.size(); i++) {
                int startIndex = i;
                int endIndex = i+1;
                
                CertItemOwnerHistory h1 = histories.get(startIndex);
                CertItemOwnerHistory h2 = histories.get(endIndex);
                if (Util.nullSafeEq(h1.getDate(), h2.getDate(), true)) {
                    // If the dates match, keep iterating till we find one that doesn't match
                    // Then sort that sub list by ownership chain
                    i++;
                    while (i < histories.size() && Util.nullSafeEq(h1.getDate(), histories.get(i).getDate(), true)) {
                        endIndex = i++;
                    }

                    sortMatchingDateHistories(histories, ascending, startIndex, endIndex);
                }
            }
        }
    }

    /**
     * Sort a part of the list based on ownership chain
     */
    private void sortMatchingDateHistories(List<CertItemOwnerHistory> histories, boolean ascending, int startIndex, int endIndex) {
        
        int limit = endIndex + 1;
        CertItemOwnerHistory[] historyArray = new CertItemOwnerHistory[limit - startIndex];
        histories.subList(startIndex, limit).toArray(historyArray);
        sortMatchingDateHistories(historyArray, 0, historyArray.length - 1);
        int newIndex = (ascending) ? startIndex : endIndex;
        for (CertItemOwnerHistory history : historyArray) {
            histories.set(newIndex, history);
            newIndex = (ascending) ? newIndex + 1 : newIndex - 1;
        }
    }

    /**
     * Sort the given array with matching dates. The logic is to find the first and last elements 
     * based on ownership chain, set those, then recurse to sort the remaining inner parts of the array.
     * 
     * [3, 4, 0, 1, 5, 2]
     * [0, 4, 3, 1, 2, 5]
     * [0, 1, 3, 2, 4, 5]
     * [0, 1, 2, 3, 4, 5]
     */
    private void sortMatchingDateHistories(CertItemOwnerHistory[] histories, int startIndex, int endIndex) {

        // If there is 1 or 0 elements, or the indices have overlapped, nothing more to do. 
        if (histories.length <= 1 || startIndex >= endIndex || endIndex > histories.length - 1 || startIndex < 0) {
            return;
        }

        int firstItem = -1;
        int lastItem = -1;

        // Iterate through to find the first and last index. 
        for (int i = startIndex; i <= endIndex && (firstItem == -1 || lastItem == -1); i++) {
            CertItemOwnerHistory history = histories[i];
            boolean isFirst = true;
            boolean isLast = true;
            for (int j = startIndex; j <= endIndex && (isFirst || isLast); j++) {
                // Skip the same element
                if (j == i) {
                    continue;
                }
                CertItemOwnerHistory compHistory = histories[j];
                // If my new owner matches their previous owner, I cannot be last
                if (Util.nullSafeEq(history.getNewOwner(), compHistory.getPreviousOwner(), true)) {
                    isLast = false;
                // If my previous owner matches their new owner, I cannot be first    
                } else if (Util.nullSafeEq(history.getPreviousOwner(), compHistory.getNewOwner(), true)) {
                    isFirst = false;
                }
            }
            if (isFirst) {
                firstItem = i;
            } else if (isLast) {
                lastItem = i;
            }
        }

        // Swap out first item if we need to.
        swapElements(histories, startIndex, firstItem);

        // Correct the last item if it has been moved due to the swap.
        if (lastItem == startIndex) {
            lastItem = firstItem;
        }

        // Swap out the last item if we need to. 
        swapElements(histories, endIndex, lastItem);
        
        // Recurse on the inner part of our list, excluding first and last items
        sortMatchingDateHistories(histories, startIndex + 1, endIndex - 1);
    }
    
    private void swapElements(Object[] array, int index1, int index2) {

        if ((index1 >= 0 && index1 < array.length) &&
                (index2 >= 0 && index2 < array.length) &&
                index1 != index2) {
            Object temp = array[index1];
            array[index1] = array[index2];
            array[index2] = temp;
        }
    }


    /**
     * Return a list of CertItemOwnerHistory for the certification item with the
     * given ID, sorted descending by date.  Note that this relies upon audit
     * log entries for information about forwarding, reassignment, and delegation.
     * These actions must be turned on before the certification is used in order
     * to retrieve the history.  The required actions include:
     * 
     * AuditEvent.ActionReassign
     * AuditEvent.ActionDelegate
     * AuditEvent.ActionCompleteDelegation
     * AuditEvent.ActionRevokeDelegation
     * 
     * @param  certItemId  The ID of the CertificationItem for which to return
     *                     ownership history.
     */
    public List<CertItemOwnerHistory> getCertItemOwnerHistory(String certItemId)
        throws GeneralException {
        
        List<CertItemOwnerHistory> history = new ArrayList<CertItemOwnerHistory>();
        
        CertificationItem item =
            this.context.getObjectById(CertificationItem.class, certItemId);
        
        // Get the ID of the cert in which the decision was made.
        String certId = null;
        if (null != item.getAction()) {
            certId = item.getAction().getDecisionCertificationId();
        }

        // If the cert ID wasn't on the action (either a decision has not been
        // made or an older cert that doesn't have this), just use the cert ID.
        certId = (null != certId) ? certId : item.getCertification().getId();
        
        // Step #1: The genesis of all items ... the original certification.
        // Add all of the history for this cert, including reassignments,
        // forwards, etc...
        history.addAll(getCertificationOwnerHistory(certId, null));

        // Step #2: Get the information about the item and entity delegations
        // (if there were any), including forwards.
        history.addAll(getDelegationOwnerHistory(item));
        
        // Sort by date descending.
        sortHistories(history, false);

        // Finally, mark the entry in which the decision was made.
        if (null != item.getAction()) {
            Date decisionDate = item.getAction().getDecisionDate();
            if (null != decisionDate) {
                // Only looking at the decision date now.  If two dates are
                // exactly the same, this might not do the right thing.  If this
                // turns out to be inaccurate, we can do more work by looking at
                // the actorName and actingWorkItem ID from the action.
                for (CertItemOwnerHistory current : history) {
                    if (decisionDate.after(current.getDate()) ) {
                        current.setDecisionMade(true);
                        break;
                    }
                }
            }
        }
        
        return history;
    }

    /**
     * Return the CertItemOwnerHistory for the certifications that the item was
     * a part of - including the original certification, bulk reassignments,
     * and any forwards.
     */
    private List<CertItemOwnerHistory> getCertificationOwnerHistory(String certId, Date before)
        throws GeneralException {

        List<CertItemOwnerHistory> history = new ArrayList<CertItemOwnerHistory>();
        
        // FIRST - Get the information about any forwards for this cert.
        List<CertItemOwnerHistory> forwards = getWorkItemOwnerHistory(certId, before);
        history.addAll(forwards);

        
        // SECOND - Get a history item for the creation of this cert - either
        // when the cert was generated or from a bulk reassignment.
        CertificationAuditor auditor = new CertificationAuditor(this.context);
        ReassignmentAudit reassignAudit = auditor.getReassignmentAudit(certId);

        String newOwner = null;
        String prevOwner = null;
        CertItemOwnerHistory.Reason reason = null;
        Date date = null;
        String comments = null;
        String source = null;

        // Use the reassignment audit event if there was one, otherwise look 
        // at the certification itself.
        if (null != reassignAudit) {
            reason = CertItemOwnerHistory.Reason.Reassignment;
            prevOwner = reassignAudit.getPreviousOwner();
            newOwner = reassignAudit.getNewOwner();
            date = reassignAudit.getDate();
            comments = reassignAudit.getComments();
            source = reassignAudit.getSource();
        }
        else {
            Certification cert =
                this.context.getObjectById(Certification.class, certId);
            if (null == cert) {
                throw new GeneralException("Certification and bulk reassignment audit event not found.");
            }
            
            // Might be able to make a guess on the certifier if there are
            // multiple based on the source, but we'll just turn it into a
            // list for simplicity.
            String certifiers = Util.listToCsv(cert.getCertifiers());
            newOwner = getNewOwner(certifiers, forwards);

            // Creator is the person that scheduled the cert.
            source = cert.getCreator();

            date = cert.getCreated();
            reason = CertItemOwnerHistory.Reason.CertificationOwner;
        }
        
        // Create the reassignment history for this cert.
        history.add(new CertItemOwnerHistory(prevOwner, newOwner, reason, date,
                                             comments, source));


        // THIRD - Now recurse up the stack for bulk reassignments.
        if (null != reassignAudit) {
            String parentId = reassignAudit.getParentCertificationId();
            history.addAll(getCertificationOwnerHistory(parentId, date));
        }
        
        
        // FOURTH - Look to see if this certification was rescinded.
        CertificationRescindAudit rescind = auditor.getRescindAudit(certId);
        if (null != rescind) {
            history.add(new CertItemOwnerHistory(rescind.getPreviousOwner(),
                                                 rescind.getNewOwner(),
                                                 CertItemOwnerHistory.Reason.CertificationRescinded,
                                                 rescind.getDate(),
                                                 null,
                                                 rescind.getSource()));
        }
        
        return history;
    }

    /**
     * Return the owner history for the work item (or certification) with the
     * given ID that fall before the given date.
     * 
     * @param  objectId  The ID of either the certification (for a certification
     *                   work item), or the work item for all others.
     * @param  before    The date at which to stop returning entries.
     */
    private List<CertItemOwnerHistory> getWorkItemOwnerHistory(String objectId, Date before)
        throws GeneralException {

        List<CertItemOwnerHistory> history = new ArrayList<CertItemOwnerHistory>();

        Workflower wf = new Workflower(this.context);
        List<Workflower.ForwardAudit> audits = wf.getForwardAudits(objectId, before);

        for (Workflower.ForwardAudit audit : audits) {
            Workflower.ForwardType type = audit.getForwardType();
            CertItemOwnerHistory.Reason reason =
                (null != type) ? CertItemOwnerHistory.Reason.from(type) : null;
            CertItemOwnerHistory hist =
                new CertItemOwnerHistory(audit.getOldOwner(), audit.getNewOwner(),
                                         reason, audit.getDate(), audit.getComment(),
                                         audit.getSource());
            history.add(hist);
        }
        
        return history;
    }

    /**
     * Return the delegation owner history for the given CertificationItem -
     * including item and entity delegations, their result (completed or revoked),
     * and any forwards.
     */
    private List<CertItemOwnerHistory> getDelegationOwnerHistory(CertificationItem item)
        throws GeneralException {
        
        List<CertItemOwnerHistory> history = new ArrayList<CertItemOwnerHistory>();

        // Look for any delegation audits for this item or its entity.
        CertificationAuditor auditor = new CertificationAuditor(this.context);
        List<DelegationAudit> delegations = auditor.findDelegations(item);

        if (null != delegations) {
            for (DelegationAudit delegation : delegations) {

                // Get any forward/escalation/etc... history for the delegation work item.
                List<CertItemOwnerHistory> forwards =
                    getWorkItemOwnerHistory(delegation.getWorkItemId(), null);
                history.addAll(forwards);

                // Add an event for the delegation itself.
                history.add(new CertItemOwnerHistory(delegation.getPreviousOwner(),
                                                     delegation.getNewOwner(),
                                                     CertItemOwnerHistory.Reason.Delegation,
                                                     delegation.getDate(),
                                                     delegation.getComments(),
                                                     delegation.getSource()));

                // Figure out the outcome of the delegation.  Will either be a
                // completion (complete, expired, reject) or revocation if the
                // delegation is done.
                DelegationCompletionAudit completion =
                    auditor.getDelegationCompletion(delegation.getWorkItemId());
                if (null != completion) {
                    CertItemOwnerHistory.Reason reason =
                        CertItemOwnerHistory.Reason.from(completion.getCompletionState());
                    history.add(new CertItemOwnerHistory(completion.getPreviousOwner(),
                                                         completion.getNewOwner(),
                                                         reason,
                                                         completion.getDate(),
                                                         completion.getComments(),
                                                         completion.getSource()));
                }

                DelegationRevocationAudit revocation =
                    auditor.getDelegationRevocation(delegation.getWorkItemId());
                if (null != revocation) {
                    history.add(new CertItemOwnerHistory(revocation.getPreviousOwner(),
                                                         revocation.getNewOwner(),
                                                         CertItemOwnerHistory.Reason.DelegationRevoked,
                                                         revocation.getDate(),
                                                         null,
                                                         revocation.getSource()));
                }
            }
        }

        return history;
    }

    /**
     * Return the name of the new owner.  Sometimes it is hard to tell who the
     * actual new owner was, so if there are forwards we will look at the oldest
     * previous owner.
     */
    private String getNewOwner(String owner, List<CertItemOwnerHistory> forwards) {
        // If forwarded, get the oldest forward to determine the original owner.
        if (!forwards.isEmpty()) {
            sortHistories(forwards, true);
            owner = forwards.get(0).getPreviousOwner();
        }
        return owner;
    }
}
