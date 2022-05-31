package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.service.certification.CertificationItemStatusCount;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.service.identity.TargetAccountService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * Common certification related utility functions
 *
 * @author patrick.jeong
 */
public class CertificationUtil {

    public static final String ALLOW_BULK_APPROVE = "allowBulkApprove";
    public static final String ALLOW_BULK_REVOKE = "allowBulkRevoke";
    public static final String ALLOW_BULK_REVOKE_ACCOUNT = "allowBulkRevokeAccount";
    public static final String ALLOW_BULK_MITIGATE = "allowBulkMitigate";
    public static final String ALLOW_BULK_REASSIGN = "allowBulkReassign";
    public static final String ALLOW_BULK_UNDO = "allowBulkUndo";
    public static final String ALLOW_BULK_SAVE_CUSTOM_ENTITY_FIELDS = "saveEntityCustomField";

    /**
     * Get list of certification certifiers names by certification id.
     * We use this when we don't want to load the entire cert object.
     *
     * @param context
     * @param certificationId
     * @return List of certifier names
     * @throws GeneralException
     */
    public static List<String> getCertifiers(SailPointContext context, String certificationId) throws GeneralException {
        List<String> certifiersList = null;

        if (certificationId != null) {
            QueryOptions ops = new QueryOptions(Filter.eq("id", certificationId));
            ops.add(Filter.join("certifiers", "Identity.name"));
            Iterator<Object[]> iter = context.search(Certification.class, ops, Arrays.asList("Identity.name"));
            if (iter != null && iter.hasNext()) {
                certifiersList = new ArrayList<String>();
                while (iter.hasNext()) {
                    certifiersList.add((String) iter.next()[0]);
                }
            }
        }

        return certifiersList;
    }

    /**
     * Get the certifiers displayable names
     *
     * @return
     * @throws GeneralException
     */
    public static List<String> getCertifiersDisplayNames(SailPointContext context, List<String> certifiers) throws GeneralException{
        List<String> certifierDisplayNames = new ArrayList<String>();

        for (String name : certifiers) {
            Identity identity = context.getObjectByName(Identity.class, name);
            if (identity != null){
                certifierDisplayNames.add(identity.getDisplayableName());
            } else {
                certifierDisplayNames.add(name);
            }
        }

        return certifierDisplayNames;
    }

    /**
     * Check if certification is editable
     *
     * @param userContext
     * @param certification
     * @return whether or not the certification is editable
     * @throws GeneralException
     */
    public static boolean isEditable(UserContext userContext, Certification certification) throws GeneralException {
        boolean editable = false;

        // signed certs are not editable
        if (certification != null && certification.getSigned() == null) {
            editable = CertificationAuthorizer.isCertificationAdmin(userContext);

            // Check if the user is an owner of the cert.
            List<String> certifiers = certification.getCertifiers();

            if (!editable && certifiers != null) {
                editable = CertificationAuthorizer.isCertifier(userContext.getLoggedInUser(), certifiers);
            }

            // Check if this user is the certifier for a parent cert if this is
            // a bulk reassignment.
            if (!editable && certification.isBulkReassignment() && certification.getParent() != null) {
                List<String> parentCertifiers = CertificationUtil.getCertifiers(userContext.getContext(), certification.getParent().getId());
                if (parentCertifiers != null) {
                    editable = CertificationAuthorizer.isCertifier(userContext.getLoggedInUser(), parentCertifiers);
                }
            }

            Certification.Phase phase = certification.getPhase();
            CertificationDefinition definition = certification.getCertificationDefinition(userContext.getContext());

            // Check for a staged phase or pending certification with staging enabled..
            if (Certification.Phase.Staged.equals(phase) || (phase == null && definition != null && definition.isStagingEnabled())) {
                editable = false;
            }
        }

        return editable;
    }

    /**
     * Get list of allowable bulk actions for certification when editable and not signed off yet.
     *
     * @param userContext
     * @param certification
     * @return List of allowable actions
     * @throws GeneralException
     */
    public static List<String> getAvailableBulkDecisions(UserContext userContext, Certification certification) throws GeneralException {
        List<String> availableBulkDecisions = new ArrayList<String>();

        boolean editable = CertificationUtil.isEditable(userContext, certification);
        boolean signedOff = certification.getSigned() != null;

        // no bulk decisions when cert is not editable or already signed off on
        if (!editable || signedOff) {
            return availableBulkDecisions;
        }

        SailPointContext context = userContext.getContext();

        Configuration sysConfig = context.getConfiguration();

        CertificationDefinition definition = certification.getCertificationDefinition(context);

        if (definition.isAllowListBulkApprove(context)) {
            availableBulkDecisions.add(ALLOW_BULK_APPROVE);
        }

        if (definition.isAllowListBulkRevoke(context)) {
            availableBulkDecisions.add(ALLOW_BULK_REVOKE);
        }

        if (definition.isAllowListBulkAccountRevocation(context) && !certification.isBulkReassignment()) {
            availableBulkDecisions.add(ALLOW_BULK_REVOKE_ACCOUNT);
        }

        if (definition.isAllowListBulkMitigate(context)) {
            boolean entityCanBeMitigated = !certification.getType().isType(Certification.Type.AccountGroupMembership,
                    Certification.Type.AccountGroupMembership,
                    Certification.Type.BusinessRoleComposition);

            if (entityCanBeMitigated && definition.isAllowExceptions(context)) {
                availableBulkDecisions.add(ALLOW_BULK_MITIGATE);
            }
        }

        if (definition.isAllowListBulkReassign(context)) {
            availableBulkDecisions.add(ALLOW_BULK_REASSIGN);
        }

        if (sysConfig.getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_SAVE_CUSTOM_ENTITY_FIELDS)) {
            availableBulkDecisions.add(ALLOW_BULK_SAVE_CUSTOM_ENTITY_FIELDS);
        }

        if (definition.isAllowListBulkClearDecisions(context)) {
            availableBulkDecisions.add(ALLOW_BULK_UNDO);
        }

        return availableBulkDecisions;
    }

    /**
     * Returns a map of certification item status count.
     * @param userContext UserContext
     * @param certification Certification
     * @param entity CertificationEntity optional
     * @return a map of counts for status
     * @throws GeneralException
     */
    public static CertificationItemStatusCount getItemStatusCount(
         UserContext userContext,
         Certification certification,
         CertificationEntity entity) throws GeneralException
    {
        String entityId = (null != entity) ? entity.getId() : null;
        return getItemStatusCount(userContext, certification.getId(), entityId);
    }

    /**
     * Returns a map of certification item status count.
     * @param userContext UserContext
     * @param certId The ID of the certification.
     * @param entityId The ID of the CertificationEntity optional
     * @return a map of counts for status
     * @throws GeneralException
     */
    public static CertificationItemStatusCount getItemStatusCount(
            UserContext userContext,
            String certId,
            String entityId) throws GeneralException {

        CertificationItemStatusCount statusCount = new CertificationItemStatusCount();
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.certification.id", certId));
        if (entityId != null) {
            ops.add(Filter.eq("parent.id", entityId));
        }
        ops.addGroupBy("summaryStatus");
        ops.addGroupBy("type");

        List<String> cols = new ArrayList<String>();
        cols.add("type");
        cols.add("summaryStatus");
        cols.add("count(*)");

        Iterator<Object[]> rows = userContext.getContext().search(CertificationItem.class, ops, cols);
        if (rows != null) {
            while (rows.hasNext()) {
                Object[] row = rows.next();
                statusCount.addStatusCount(
                        ((CertificationItem.Type) row[0]).name(),
                        ((CertificationItem.Status) row[1]).name(),
                        ((Long) row[2]).intValue());
            }
        }
        return statusCount;
    }

    /**
     * Retrieve the number of auto approvals in this certification.
     * This may need to be modified when there are more than just auto approvals.
     * @param userContext UserContext
     * @param certId The ID of the certification.
     * @return Count of auto approvals
     * @throws GeneralException
     */
    public static int getAutoApprovalCount(UserContext userContext, String certId) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.certification.id", certId));
        ops.add(Filter.eq("action.autoDecision", true));
        ops.addGroupBy("action.status");

        List<String> cols = new ArrayList<>();
        cols.add("action.status");
        cols.add("count(*)");

        Iterator<Object[]> rows = userContext.getContext().search(CertificationItem.class, ops, cols);
        if (rows != null && rows.hasNext()) {
            Object[] row = rows.next();
            return ((Long)row[1]).intValue();
        }

        return 0;
    }

    /**
     * Calculate the description for the certification item type and put it into the map with IIQ_revoked key
     *
     * @param map map of data objects
     * @param locale
     */
    public static void calculateDescription(Map<String, Object> map, Locale locale) {
        CertificationItem.Type type = (CertificationItem.Type) map.get("type");
        switch (type) {
            case Bundle:
                map.put("IIQ_revoked", map.get("bundle"));
                break;
            case AccountGroupMembership:
            case Exception:
            case Account:
            case DataOwner:
                String description = new String();
                EntitlementSnapshot snap = (EntitlementSnapshot) map.get("exceptionEntitlements");
                if (snap != null) {
                    Message msg = EntitlementDescriber.summarize(snap);
                    description = (msg != null) ?
                            msg.getLocalizedMessage(locale, null) : null;
                }
                map.put("IIQ_revoked", description);

                break;
            case PolicyViolation:
                PolicyViolation pv = (PolicyViolation) map.get("policyViolation");
                map.put("IIQ_revoked", (pv.getDescription() != null) ?
                        pv.getDescription() : pv.getConstraintName());
                break;
        }
    }

    /**
     * Determines if this is a responsive certification type that needs to be redirected to the new UI.
     * @param type Certification.Type of the cert
     * @param isContinuous True if this is a continuous cert, false otherwise.
     * @return True if this is a responsive type, False otherwise.
     */
    public static boolean isResponsiveCertType(Certification.Type type, boolean isContinuous) {

       return true;
    }

    /**
     * Create a filter to limit a Certification search to responsive certification types
     * @return Filter object
     */
    public static Filter getResponsiveCertTypeFilter() {
        return null;
    }

    /**
     * Simple helper class to hold applications/accounts for bundle assignment info.
     */
    public static class BundleAccountInfo {
        public String applications;
        public String accounts;
    }

    /**
     * Get the assignment account info for a bundle, for display in the UI
     *
     * @param item CertificationItem
     * @param identity Identity
     * @param context SailPointContext
     * @return BundleAccountInfo
     */
    public static BundleAccountInfo getBundleAccountInfo(CertificationItem item, Identity identity, SailPointContext context) throws GeneralException {
        BundleAccountInfo accountInfo = new BundleAccountInfo();

        TargetAccountService accountService = new TargetAccountService(context, identity.getId());
        List<TargetAccountDTO> targetAccounts = accountService.getTargetsForCertificationItem(item);

        if (!Util.isEmpty(targetAccounts)) {
            Set<String> applicationNames = new LinkedHashSet<>();
            Set<String> accountNames = new LinkedHashSet<>();
            for (TargetAccountDTO accountDTO : Util.iterate(targetAccounts)) {
                applicationNames.add(accountDTO.getApplication());
                accountNames.add(accountDTO.getAccount());
            }

            accountInfo.applications = Util.join(applicationNames, ", ");
            accountInfo.accounts = Util.join(accountNames, ", ");
        }

        return accountInfo;
    }
}
