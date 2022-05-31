/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.Iconifier;
import sailpoint.api.Iconifier.Icon;
import sailpoint.api.IdentityHistoryService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Type;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.IdentitySelector;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Recommendation;
import sailpoint.object.SailPointObject;
import sailpoint.object.UIConfig;
import sailpoint.recommender.RecommendationDTO;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListService;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.policyviolation.PolicyViolationDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.DecisionSummary;
import sailpoint.web.view.IdentitySummary;
import sailpoint.web.view.ViewBuilder;
import sailpoint.web.view.ViewEvaluationContext;
import sailpoint.web.view.certification.CertificationDecisionStatus;

/**
 * Service to get a list of CertificationItems fit for UI consumption
 */
public class CertificationItemListService extends BaseListService<CertificationItemListServiceContext> {

    public static final String COL_EXCEPTION_APPLICATION = "exceptionApplication";
    public static final String COL_EXCEPTION_ATTR_NAME = "exceptionAttributeName";
    public static final String COL_EXCEPTION_ATTR_VALUE = "exceptionAttributeValue";
    public static final String COL_EXCEPTION_PERM_TARGET = "exceptionPermissionTarget";
    public static final String COL_EXCEPTION_PERM_RIGHT = "exceptionPermissionRight";
    public static final String COL_EXCEPTION_ENTITLEMENTS = "exceptionEntitlements";
    public static final String COL_BUNDLE = "bundle";
    public static final String COL_ID = "id";
    public static final String COL_POLICY_VIOLATION = "policyViolation";
    public static final String COL_SUMMARY_STATUS = "summaryStatus";
    public static final String COL_TYPE = "type";
    public static final String COL_TARGET_NAME = "targetName";
    public static final String COL_PROFILE_APPLICATION = "profile-application";
    public static final String COL_SUB_TYPE = "subType";
    public static final String COL_CHALLENGE = "challenge";
    public static final String COL_ACTION = "action";
    public static final String COL_DELEGATION = "delegation";
    public static final String COL_PARENT_DELEGATION = "parent.delegation";
    public static final String COL_DISPLAY_NAME = "displayName";
    public static final String COL_BUNDLE_ASSIGNMENT_ID = "bundleAssignmentId";
    public static final String COL_IDENTITY = "parent.identity";
    public static final String COL_PARENT_TARGET = "parent.targetId";
    public static final String COL_PERMISSION_VALUE = "parent.nativeIdentity";
    public static final String COL_CLASSIFICATION_NAMES = "classificationNames";
    public static final String PROP_CLASSIFICATION_NAMES = "IIQ_classificationNames";

    public static final String METADATA_KEY_GROUPS = "groups";
    
    private static final String IDENTITY_JOIN_PREFIX = "Identity.";

    /**
     * Column config to use for exporting certification items
     */
    private static final String IDENTITY_EXPORT_COLUMN_CONFIG = "uiCertificationExportColumns";
    private static final String IDENTITY_EXPORT_COLUMN_CONFIG_REC = "uiCertificationExportWithRecommendationColumns";
    private static final String DATA_OWNER_EXPORT_COLUMN_CONFIG = "uiDataOwnerCertificationExportColumns";
    private static final String ROLE_COMP_EXPORT_COLUMN_CONFIG = "uiRoleCompCertificationExportColumns";
    private static final String ACCOUNT_GROUP_PERMISSION_COLUMN_CONFIG = "uiAcctGroupPermCertificationExportColumns";
    private static final String ACCOUNT_GROUP_MEMBERSHIP_COLUMN_CONFIG = "uiAcctGroupMembershipCertificationExportColumns";

    private static final Log log = LogFactory.getLog(CertificationItemListService.class);

    /**
     * A ListResourceColumnSelector that returns columns for certification items.
     */
    public static class CertificationItemListColumnSelector extends BaseListResourceColumnSelector {

        private List<ColumnConfig> columns;
        private Certification certification;
        private SailPointContext context;

        /**
         * Constructor
         * @param columnsKey UIConfig entry key
         */
        public CertificationItemListColumnSelector(String columnsKey, Certification certification, SailPointContext context) {
            super(columnsKey);
            this.certification = certification;
            this.context = context;
        }

        /**
         * @return projection columns we need for internal purposes. These will 
         * be passed to the ViewBuilder eventually so no need to include standard 
         * projection columns
         */
        public static List<String> getAdditionalProjectionColumns() throws GeneralException {
            List<String> columns = new ArrayList<String>();

            /* Projection columns needed for calculated columns */
            addColumnToProjectionList(COL_ID, columns);
            addColumnToProjectionList(COL_TYPE, columns);
            addColumnToProjectionList(COL_SUB_TYPE, columns);
            addColumnToProjectionList(COL_SUMMARY_STATUS, columns);
            addColumnToProjectionList(COL_IDENTITY, columns);

            //Role data
            addColumnToProjectionList(COL_BUNDLE, columns);
            addColumnToProjectionList(COL_BUNDLE_ASSIGNMENT_ID, columns);

            // Entitlement data
            addColumnToProjectionList(COL_EXCEPTION_APPLICATION, columns);
            addColumnToProjectionList(COL_EXCEPTION_ATTR_NAME, columns);
            addColumnToProjectionList(COL_EXCEPTION_ATTR_VALUE, columns);
            addColumnToProjectionList(COL_EXCEPTION_PERM_TARGET, columns);
            addColumnToProjectionList(COL_EXCEPTION_PERM_RIGHT, columns);
            addColumnToProjectionList(COL_EXCEPTION_ENTITLEMENTS, columns);
            addColumnToProjectionList(COL_PARENT_TARGET, columns);
            addColumnToProjectionList(COL_PERMISSION_VALUE, columns);

            // Policy violation details data
            addColumnToProjectionList(COL_POLICY_VIOLATION, columns);

            // Other stuff needed for calculating whether decision is editable.
            addColumnToProjectionList(COL_ACTION, columns);
            addColumnToProjectionList(COL_DELEGATION, columns);
            addColumnToProjectionList(COL_PARENT_DELEGATION, columns);

            return columns;
        }

        @Override
        public List<ColumnConfig> getColumns() throws GeneralException {
            if (this.columns == null) {
                // Check the certification definition and remove the classification names column if classifications are
                // not enabled for this certification.
                List<ColumnConfig> allColumns = new ArrayList<>(super.getColumns());
                CertificationDefinition definition = this.certification.getCertificationDefinition(this.context);
                if (definition != null && !definition.isIncludeClassifications()) {
                    allColumns.removeIf((ColumnConfig config) -> COL_CLASSIFICATION_NAMES.equals(config.getDataIndex()));
                }

                this.columns = allColumns;
            }

            return this.columns;
        }
    }

    private UserContext userContext;

    /**
     * Constructor
     * @param userContext UserContext
     * @param listServiceContext ListServiceContext
     * @param columnSelector CertificationItemListColumnSelector                          
     */
    public CertificationItemListService(UserContext userContext, CertificationItemListServiceContext listServiceContext,
                                        CertificationItemListColumnSelector columnSelector) {
        super(userContext.getContext(), listServiceContext, columnSelector);
        this.userContext = userContext;
    }

    /**
     * List the CertificationItems for the given certification id and summaryStatus.
     * This uses ViewBuilder and supports evaluators for the ColumnConfigs 
     * @param certification Certification object
     * @return ListResult containing CertificationItemDTO items
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public ListResult getCertificationItems(Certification certification, String entityId) throws GeneralException {
        if (certification == null) {
            throw new GeneralException("certification is required!");
        }

        QueryOptions queryOptions = super.createQueryOptions();

        if (entityId != null) {
            queryOptions.add(Filter.eq("parent.id", entityId));
        }
        queryOptions.add(Filter.eq("parent.certification", certification));

        // only add summary status sorting for action required items
        if (this.columnSelector.getColumnsKey().equals(UIConfig.UI_CERTIFICATION_ITEM_RETURNED_ITEMS_COLUMNS)) {
            queryOptions.addOrdering(COL_SUMMARY_STATUS, false);
        }

        // Add join to identity table if needed. The join is different for data owner certs.
        // Only need check column config columns, since none of our standard columns require join
        if (!this.listServiceContext.isJoinedToIdentity() && hasIdentityProjectionColumn(this.columnSelector.getProjectionColumns())) {
            if (certification.getType().isObjectType()) {
                queryOptions.add(Filter.leftJoin("targetId", "Identity.id"));
            }
            else {
                queryOptions.add(Filter.leftJoin("parent.identity", "Identity.name"));
            }
        }

        // Hard-code this if the client doesn't pass up any sorting.
        if (Util.isEmpty(queryOptions.getOrderings())) {
            queryOptions.setOrderBy(getOrderBy(certification.getType()));
        }

        // IIQMAG-1422: Add secondary ordering on a unique field to make sure this is a total ordering. Otherwise
        // paging weirdness can result, especially on Oracle.
        queryOptions.addOrdering("id", true);

        List<Filter> newFilters = new ArrayList<>();

        for (Filter f : queryOptions.getFilters()) {

            // If we have a filter for "No Recommendations", include other visually empty items too.
            if (isNoRecommendationFilter(f)) {
                Filter noRecFilter = buildNotActionableRecommendationsFilter();
                newFilters.add(noRecFilter);
            // If we have an "Auto Decisions" filter, adjust the filter criteria if necessary.
            } else if (isAutoDecisionFalseFilter(f)) {
                Filter autoDecFilter = buildAutoDecisionFalseFilter();
                newFilters.add(autoDecFilter);
            } else {
                newFilters.add(f);
            }
        }

        queryOptions.setFilters(newFilters);

        int count = countResults(CertificationItem.class, queryOptions);

        // Use the ViewBuilder for this since we have evaluator ViewColumns
        List<ColumnConfig> columns = this.columnSelector.getColumns();
        ViewEvaluationContext evaluationContext = new ViewEvaluationContext(this.userContext, columns);
        ViewBuilder viewBuilder = new ViewBuilder(evaluationContext, CertificationItem.class, columns, CertificationItemListColumnSelector.getAdditionalProjectionColumns());

        ListResult viewResults = viewBuilder.getResult(queryOptions);
        List<CertificationItemDTO> dtos = new ArrayList<>();
        List<Map<String, Object>> convertedRows = new ArrayList<>();
        for (Object result : viewResults.getObjects()) {
            // ViewBuilder returns maps in the list result
            Map<String, Object> resultMap = convertRow((Map<String, Object>) result, true);
            convertedRows.add(resultMap);
            CertificationItemDTO dto = createDTO(resultMap, this.columnSelector.getColumns(), certification);
            
            if (dto.getType().equals(CertificationItem.Type.BusinessRoleProfile)) {
                CertificationItem item = this.context.getObjectById(CertificationItem.class, dto.getId());
                String description = item.getParent().getRoleSnapshot() != null ?
                    item.getParent().getRoleSnapshot().getProfileSnapshot(item.getTargetId()).getObjectDescription(this.userContext.getLocale()) : null;
               if(dto.getDescription() == null ) {
                   dto.setDescription(description);
               }
            }
            
            dtos.add(dto);
        }

        ListResult certItemListResult = new ListResult(dtos, count);
        if (certItemListResult.getAttributes() == null) {
            certItemListResult.setAttributes(new Attributes<String, Object>());
        }

        List<ListResultGroup> groups = getResultGroups(convertedRows, count, CertificationItem.class, queryOptions);
        if (!Util.isEmpty(groups)) {
            Map<String, Object> metaData = new HashMap();
            metaData.put(METADATA_KEY_GROUPS, groups);
            certItemListResult.setMetaData(metaData);
        }
        
        return certItemListResult;
    }

    /**
     * @return a Filter that will only allow cert items that have a recommendValue that is null,
     * or is not actionable
     */
    private Filter buildNotActionableRecommendationsFilter() {
        List<Filter> noRecFilters = new ArrayList<>();
        noRecFilters.add(Filter.isnull(IdentityCertItemListFilterContext.RECOMMEND_FILTER));

        for (Recommendation.RecommendedDecision decision : Recommendation.RecommendedDecision.values()) {
            if (!decision.isActionable()) {
                noRecFilters.add(Filter.eq(IdentityCertItemListFilterContext.RECOMMEND_FILTER, decision.name()));
            }
        }

        return new Filter.CompositeFilter(Filter.BooleanOperation.OR, noRecFilters);
    }

    /**
     * Builds a composite filter for auto-decided items. This is needed because the criteria for what is
     * considered auto-decided or not depends on whether the cert is staged.
     *
     * @return Composite filter that returns the appropriate items
     */
    private Filter buildAutoDecisionFalseFilter() {

        List<Filter> autoDecFilters = new ArrayList<>();
        autoDecFilters.add(Filter.isnull("action"));
        autoDecFilters.add(Filter.isnull(IdentityCertItemListFilterContext.AUTO_DECIDE_FILTER));
        autoDecFilters.add(Filter.eq(IdentityCertItemListFilterContext.AUTO_DECIDE_FILTER, false));

        return new Filter.CompositeFilter(Filter.BooleanOperation.OR, autoDecFilters);
    }

    /**
     * Overridden to populate default values for certain columns based on cert item type.
     */
    @Override
    protected void calculateColumns(Map<String, Object> rawQueryResults,
                                    Map<String, Object> map)
            throws GeneralException {

        super.calculateColumns(rawQueryResults, map);

        // For Role Composition certs, set a useful default targetName in case the role profile name was not set.
        String type = Util.getString(rawQueryResults, COL_TYPE);
        if (CertificationItem.Type.BusinessRoleProfile.name().equals(type)) {
            String targetName = Util.getString(rawQueryResults, COL_TARGET_NAME);

            if (Util.isNullOrEmpty(targetName)) {
                Message msg = new Message(MessageKeys.TEXT_ENTITLEMENTS_ON_APP, Util.getString(rawQueryResults, COL_PROFILE_APPLICATION));
                map.put(COL_TARGET_NAME, msg.getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone()));
            }
        }
    }

    /**
     * Set the certification export target and call the super export method.
     *
     * @param certification Certification to export
     * @return CSV string representation of the certification items
     * @throws GeneralException
     */
    public String exportCertification(Certification certification) throws GeneralException {
        BaseListResourceColumnSelector certificationExportColumns = getCertificationExportColumns(certification);
        return super.exportToCSV(getExportData(certification), certificationExportColumns.getColumns());
    }

    public CertificationItemListColumnSelector getCertificationExportColumns(Certification certification) {
        Certification.Type certType = certification.getType();
        if (certType.equals(Certification.Type.Manager) ||
                certType.equals(Certification.Type.BusinessRoleMembership) ||
                certType.equals(Certification.Type.Identity) ||
                //IIQETN-6220 :- adding "Group" to the condition to allow export the data.
                certType.equals(Certification.Type.Group) ||
                certType.equals(Certification.Type.ApplicationOwner) ||
                certType.equals(Certification.Type.Focused)) {
            boolean hasRecommendations = false;

            try {
                CertificationDefinition def = certification.getCertificationDefinition(context);

                if (def != null) {
                    hasRecommendations = def.getShowRecommendations();
                }
            } catch (GeneralException ge) {
                log.warn("Unable to retrieve recommendation status from Certification definition", ge);
            }

            // Use a column config that includes recommendations if enabled for the cert.
            return hasRecommendations
                    ? new CertificationItemListColumnSelector(IDENTITY_EXPORT_COLUMN_CONFIG_REC, certification, this.context)
                    : new CertificationItemListColumnSelector(IDENTITY_EXPORT_COLUMN_CONFIG, certification, this.context);
        } else if (certType.equals(Type.AccountGroupMembership)) {
            return new CertificationItemListColumnSelector(ACCOUNT_GROUP_MEMBERSHIP_COLUMN_CONFIG, certification, this.context);
        } else if (certType.equals(Certification.Type.DataOwner)) {
            return new CertificationItemListColumnSelector(DATA_OWNER_EXPORT_COLUMN_CONFIG, certification, this.context);
        } else if (certType.equals(Certification.Type.BusinessRoleComposition)) {
            return new CertificationItemListColumnSelector(ROLE_COMP_EXPORT_COLUMN_CONFIG, certification, this.context);
        } else if (certType.equals(Certification.Type.AccountGroupPermissions)) {
            return new CertificationItemListColumnSelector(ACCOUNT_GROUP_PERMISSION_COLUMN_CONFIG, certification, this.context);
        }
        throw new IllegalArgumentException("Unsupported cert type: " + certType);
    }

    /**
     * Get the certification item data for export
     * @return list of certification item data
     */
    public List<Map<String, Object>> getExportData(Certification certification) throws GeneralException {
        // Setup the QueryOptions
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("parent.certification", certification));
        queryOptions.addOrdering(COL_SUMMARY_STATUS, false);

        CertificationItemListColumnSelector exportColumnSelector = this.getCertificationExportColumns(certification);
        List<ColumnConfig> columns = exportColumnSelector.getColumns();

        // Add join to identity table if needed
        if (!this.listServiceContext.isJoinedToIdentity() && hasIdentityProjectionColumn(exportColumnSelector.getProjectionColumns())) {
            if(certification.getType().isIdentity()) {
                queryOptions.add(Filter.join("parent.identity", "Identity.name"));
            } else {
                queryOptions.add(Filter.join("targetId", "Identity.id"));
            }
        }
        /* This is a little weird, but since we cannot have a hidden field in the export we need to
         * explicitly join the CertificationEntity.  In the normal query we have a hidden column that
         * does this and we are able to reference the snapshot that way*/
        if (certification.getType().equals(Certification.Type.BusinessRoleComposition)) {
            queryOptions.add(Filter.join("parent", "CertificationEntity.id"));
        }

        // The ViewBuilder uses the evaluators defined on the column config to do the column data calculations.
        ViewEvaluationContext evaluationContext = new ViewEvaluationContext(this.userContext, columns);

        ViewBuilder viewBuilder = new ViewBuilder(evaluationContext, CertificationItem.class, columns,
                CertificationItemListColumnSelector.getAdditionalProjectionColumns());

        ListResult viewResults = viewBuilder.getResult(queryOptions);

        List<Map<String,Object>> results = new ArrayList<>();

        for (Object result : viewResults.getObjects()) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            if (certification.getType().equals(Certification.Type.AccountGroupPermissions)) {
                // Account Group Perm Certs derives these values so we need to stuff them into the map so they casn get exported
                Object attribute = resultMap.get(COL_EXCEPTION_ATTR_NAME) == null ? resultMap.get(COL_EXCEPTION_PERM_TARGET) : resultMap.get(COL_EXCEPTION_ATTR_NAME);
                Object value = resultMap.get(COL_EXCEPTION_ATTR_VALUE) == null ? resultMap.get(COL_EXCEPTION_PERM_RIGHT) : resultMap.get(COL_EXCEPTION_ATTR_VALUE);
                resultMap.put("attribute", attribute);
                resultMap.put("value", value);
            }
            //IIQSR-187 Adding role applications and role account names from assigned and detected roles.
            if (resultMap.get(COL_BUNDLE) != null) {
                String roleName = (String)resultMap.get(COL_BUNDLE);
                resultMap.put("roleName", roleName);

                String identityName = (String)resultMap.get(COL_IDENTITY);
                if (!Util.isNullOrEmpty(identityName) && !Util.isNullOrEmpty(roleName)) {
                    CertificationItem item = getContext().getObjectById(CertificationItem.class, (String)resultMap.get("id"));
                    Identity identity = this.context.getObjectByName(Identity.class, identityName);
                    Bundle role = this.context.getObjectByName(Bundle.class, roleName);
                    String assignmentId = (String)resultMap.get(COL_BUNDLE_ASSIGNMENT_ID);
                    CertificationUtil.BundleAccountInfo bundleAccountInfo = CertificationUtil.getBundleAccountInfo(item, identity, this.getContext());
                    resultMap.put("roleApplications", bundleAccountInfo.applications);
                    resultMap.put("roleAccountNames", bundleAccountInfo.accounts);
                }
            }

            // This comes back as an array of strings, but convert to CSV for readability
            // Note that ViewBuilder puts this in the property, not the dataIndex.
            if (resultMap.containsKey(PROP_CLASSIFICATION_NAMES)) {
                resultMap.put(PROP_CLASSIFICATION_NAMES, Util.listToCsv((ArrayList<String>)resultMap.get(PROP_CLASSIFICATION_NAMES)));
            }

            results.add(resultMap);
        }

        return results;
    }

    /**
     * Helper method to set certification item dto challenge data
     *
     * @param dto CertificationItemDTO dto object
     * @param challenge CertificationChallenge challenge object
     * @throws GeneralException
     */
    private void setDTOChallengeData(CertificationItemDTO dto, CertificationChallenge challenge) throws GeneralException {
        if (challenge != null && dto != null &&
                (challenge.isChallenged() || challenge.getDecision() != null)) {

            dto.setChallengeOwnerName(challenge.getActorDisplayName());
            dto.setChallengeComment(challenge.getCompletionComments());

            if (challenge.getDecision() != null) {
                dto.setChallengeDecision(challenge.getDecision().getMessageKey());
            }

            dto.setChallengeDeciderName(challenge.getDeciderName());
            dto.setChallengeDecisionComment(challenge.getDecisionComments());
        }
    }

    /**
     * Set the delegation information on the given DTO using the delegation information.
     *
     * @param  dto  The DTO on which to set the delegation info.
     * @param  delegation  The delegation for this item - this is used if available.
     * @param  parentDelegation  The delegation for the entity - this is used if the item delegation is not available.
     */
    private void setDelegationData(CertificationItemDTO dto,
                                   CertificationDelegation delegation,
                                   CertificationDelegation parentDelegation)
        throws GeneralException {

        // Use the parent delegation if a line item delegation is not available.
        if (null == delegation) {
            delegation = parentDelegation;
        }

        if (null != delegation) {
            dto.setDelegationCompletionComments(delegation.getCompletionComments());

            String name = delegation.getCompletionUser();
            if (null == name) {
                name = delegation.getOwnerName();
            }

            if (null != name) {
                Identity identity = this.context.getObjectByName(Identity.class, name);
                if (null != identity) {
                    name = identity.getDisplayableName();
                }
            }

            dto.setDelegationCompletionUser(name);
        }
    }

    private CertificationItemDTO createDTO(Map<String, Object> resultMap, List<ColumnConfig> columns, Certification certification)
            throws GeneralException {
        CertificationItemDTO dto = new CertificationItemDTO(resultMap, columns, CertificationItemListColumnSelector.getAdditionalProjectionColumns());
        CertificationItem item = getContext().getObjectById(CertificationItem.class, dto.getId());

        PolicyViolation pv = (PolicyViolation)resultMap.get(COL_POLICY_VIOLATION);
        if (pv != null) {
            PolicyViolationDTO policyViolationDTO = new PolicyViolationDTO(pv, this.userContext);
            dto.setPolicyViolationDTO(policyViolationDTO);

            // for policy violation type cert items use the policy violation display name
            if (CertificationItem.Type.PolicyViolation.equals(dto.getType())) {
                dto.setDisplayName(policyViolationDTO.getDisplayName());
            }
        }

        CertificationChallenge challenge = (CertificationChallenge)resultMap.get(COL_CHALLENGE);
        setDTOChallengeData(dto, challenge);

        CertificationDelegation delegation = (CertificationDelegation) resultMap.get(COL_DELEGATION);
        CertificationDelegation parentDelegation = (CertificationDelegation) resultMap.get(COL_PARENT_DELEGATION);
        setDelegationData(dto, delegation, parentDelegation);

        // Set role and entitlement data explicitly since not in ColumnConfig
        if (resultMap.get(COL_BUNDLE) != null) {
            String roleName = (String)resultMap.get(COL_BUNDLE);
            dto.setRoleName(roleName);

            String identityName = (String)resultMap.get(COL_IDENTITY);
            if (!Util.isNullOrEmpty(identityName) && !Util.isNullOrEmpty(roleName)) {
                Identity identity = this.context.getObjectByName(Identity.class, identityName);
                if (identity != null) {
                    CertificationUtil.BundleAccountInfo bundleAccountInfo = CertificationUtil.getBundleAccountInfo(item, identity, this.context);
                    dto.setRoleApplications(bundleAccountInfo.applications);
                    dto.setRoleAccountNames(bundleAccountInfo.accounts);
                }
            }
        }

        if (resultMap.get(COL_EXCEPTION_APPLICATION) != null) {
            dto.setApplication((String) resultMap.get(COL_EXCEPTION_APPLICATION));

            // TODO: I think this is set from the ColumnConfig, so probably we do not need to do this again
            // here, but just in case I will leave it for now since it is late in 7.2
            EntitlementSnapshot snap = (EntitlementSnapshot) resultMap.get(COL_EXCEPTION_ENTITLEMENTS);
            if (snap != null) {
                dto.setNativeIdentity(snap.getNativeIdentity());
                dto.setInstance(snap.getInstance());
            }

            if (!CertificationItem.Type.Account.equals(dto.getType())) {
                if (isEntitlementCertItem(resultMap)) {
                    dto.setAttribute((String) resultMap.get(COL_EXCEPTION_ATTR_NAME));
                    dto.setValue((String) resultMap.get(COL_EXCEPTION_ATTR_VALUE));
                    dto.setPermission(false);
                } else {
                    // todo rshea: exceptionPermissionRight and exceptionPermissionTarget are deprecated in favor of
                    // entitlement snapshot, so this may need to be updated.
                    dto.setAttribute((String) resultMap.get(COL_EXCEPTION_PERM_TARGET));
                    dto.setValue((String) resultMap.get(COL_EXCEPTION_PERM_RIGHT));
                    dto.setPermission(true);
                }

                // PM does not want to display the value we have generated in the cert, but rather foo on bar for the display
                // name of entitlements and permissions.  So for DataOwner certs and Identity certs we need to compose the
                // display names differently
                if (certification.getType().equals(Certification.Type.DataOwner)) {
                    String targetManagedAttributeId = (String) resultMap.get(COL_PARENT_TARGET);
                    ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, targetManagedAttributeId);
                    // Set our group attribute flag here, DataOwner certs are doing wonky things with display value so we dont
                    // really have a true "value" anymore to query the ManagedAttributer generically
                    dto.setGroupAttribute(managedAttribute != null);
                    String managedAttributeValue = null;

                    // iiqetn-6275 - If an entitlement was deleted from the entitlement catalog after we've created a
                    // entitlement owner certification then we don't want to throw an exception here as that will prevent
                    // the certification from being displayed. Instead, we'll take the display name from the entitlement
                    // snapshot. It should be noted that the display name in the snapshot could be different than the
                    // display name taken from the managed attribute that previously existed.
                    if (!isEntitlementCertItem(resultMap)) {
                        // This is a permission and it's name and display value are taken from the snapshot.
                        managedAttributeValue = (String) resultMap.get(COL_PERMISSION_VALUE);
                        // The following looks idiotic, but BaseDTO.setAttribute(String, Object) is not in anyway related to
                        // CertificationItemDTO.getAttribute().  setAttribute(string, Object) sets a property in a map, and
                        // getAttribute() retrieves a String property.
                        dto.setAttribute(COL_EXCEPTION_ATTR_NAME, dto.getAttribute());
                    } else if (null != managedAttribute) {
                        managedAttributeValue = managedAttribute.getDisplayableName();
                    } else {
                        String snapshotAttrValue = (String)resultMap.get(COL_EXCEPTION_ATTR_VALUE);
                        log.debug("Unable to find ManagedAttribute identified by id " + targetManagedAttributeId +
                                ". Using value [" + snapshotAttrValue + "] from entitlement snapshot as display name.");
                        managedAttributeValue = snapshotAttrValue;
                    }

                    // displayName property is rendered in the entitlement column for dataowner cert
                    dto.setDisplayName(managedAttributeValue);
                    Message entitlementDisplayName = new Message(MessageKeys.UI_CERT_DATA_OWNER_ENTITLEMENT_NAME, managedAttributeValue, dto.getAttribute());
                    dto.setAttribute(COL_EXCEPTION_ATTR_VALUE, entitlementDisplayName.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
                    // Stuff the displayname back into the result map so later getResultGroups can later get the correct value
                    resultMap.put(COL_EXCEPTION_ATTR_VALUE, entitlementDisplayName.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
                } else {
                    // Non-DataOwner certs should be able to trust the attribute/value in the DTO at this point for both permissions and entitlements
                    // Use Explanator instead of ManagedAttributer to take advantage of caching
                    String appId = ObjectUtil.getId(this.context, Application.class, dto.getApplication());
                    Explanator.Explanation explanation = dto.isPermission() ? Explanator.get(appId, dto.getAttribute()) : Explanator.get(appId, dto.getAttribute(), dto.getValue());
                    dto.setGroupAttribute(explanation != null);

                    String displayName;
                    String attribute;
                    if (isEntitlementCertItem(resultMap)) {
                        // For entitlements get the managed attribute to get the display name
                        attribute = (String) resultMap.get(COL_EXCEPTION_ATTR_NAME);
                        String value = (String) resultMap.get(COL_EXCEPTION_ATTR_VALUE);
                        if (explanation == null) {
                            displayName = value;
                        } else {
                            displayName = explanation.getDisplayValue();
                        }
                    } else {
                        // For Permissions the values we want are actually on the cert
                        attribute = (String) resultMap.get(COL_EXCEPTION_PERM_TARGET);
                        displayName = (String) resultMap.get(COL_EXCEPTION_PERM_RIGHT);
                    }
                    Message entitlementDisplayName = new Message(MessageKeys.UI_CERT_DATA_OWNER_ENTITLEMENT_NAME, displayName, attribute);
                    dto.setDisplayName(entitlementDisplayName.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
                }
            }
        }

        // Calculate whether decision can be changed and whether review is required. Note these are related but may
        // not match.
        dto.setCanChangeDecision(calculateCanChangeDecision(resultMap));
        dto.setRequiresChallengeDecision(calculateRequiresChallengeDecision(challenge));
        dto.setRequiresReview(calculateRequiresReview(resultMap));

        // For identity type certs get the last decision details
        // and check if we want to include classifications
        if (certification != null && certification.getType() != null && certification.getType().isIdentity()) {
            //check for any previous decisions that have not been completed yet
            IdentityHistoryService svc = new IdentityHistoryService(getContext());
            IdentityHistoryItem lastDecision = svc.getLastDecision(item.getParent().getTargetId(), item);

            if (null != lastDecision) {
                setPreviousDecisionDetails(lastDecision, dto);
            }
        }

        if (resultMap.containsKey(COL_CLASSIFICATION_NAMES)) {
            dto.setClassificationNames((ArrayList<String>)resultMap.get(COL_CLASSIFICATION_NAMES));
        }

        // set the list of policy violations that this item is included in
        if (CertificationItem.Type.Bundle.equals(dto.getType()) ||
            (CertificationItem.Type.Exception.equals(dto.getType()))) {
            setDependentPolicyViolations(item, dto);
        }

        // set the account status icons
        Iconifier iconifier = new Iconifier();
        List<Icon> accountStatusIcons = iconifier.getAccountIcons(item);
        if (!Util.isEmpty(accountStatusIcons)) {
            dto.setAccountStatusIcons(accountStatusIcons);
        }

        dto.setDecision(createBaseDecision(item));

        // Set the recommendation on the DTO if one exists.
        Recommendation rec = item.getRecommendation();
        if (rec != null) {
            RecommendationDTO recDTO = new RecommendationDTO(userContext.getContext(), rec, userContext.getLocale(), userContext.getUserTimeZone());
            dto.setRecommendation(recDTO);
        }

        return dto;
    }

    private boolean isEntitlementCertItem(Map<String, Object> resultMap) {
        return resultMap.get(COL_EXCEPTION_ATTR_NAME) != null;
    }

    /**
     * Create the base decision from available parts. This should be an analog of the decision that was set on the item.
     * @return
     */
    private BaseDecision createBaseDecision(CertificationItem item) throws GeneralException {
        CertificationItemService itemService = new CertificationItemService(userContext);
        CertificationAction action = item.getAction();
        CertificationDelegation delegation = item.getDelegation();

        // No decision to return if:
        // 1. Item is returned delegation
        // 2. Item has no action or cleared action
        if ((delegation == null || item.isReturned()) &&
                (action == null || CertificationAction.Status.Cleared.equals(action.getStatus()))) {
            // No decision to create.
            return null;
        }

        CertificationAction.Status status = CertificationAction.Status.Delegated;
        boolean autoDecision = false;

        if (action != null) {
            status = action.getStatus();
            if (CertificationAction.Status.Remediated.equals(status) && action.isRevokeAccount()) {
                status = CertificationAction.Status.RevokeAccount;
            }
            autoDecision = action.isAutoDecision();
        }
        DecisionSummary decisionSummary = itemService.getDecisionSummary(item, status, null);

        BaseDecision decision = new BaseDecision();
        decision.setStatus(status.name());
        decision.setMitigationExpirationDate(decisionSummary.getMitigationExpiration());
        decision.setComments(Util.isNotNullOrEmpty(decisionSummary.getComments()) ? decisionSummary.getComments() :
                delegation != null ? delegation.getComments() : null);
        decision.setDescription(delegation != null ? delegation.getDescription() : decisionSummary.getDescription());
        decision.setRemediationDetails(decisionSummary.getRemediationDetails());
        decision.setAutoDecision(autoDecision);

        PolicyViolation violation = item.getPolicyViolation();
        if (violation != null) {
            decision.setRevokedRoles(violation.getBundleNamesMarkedForRemediation());
            List<PolicyTreeNode> entitlementsToRemediate = violation.getEntitlementsToRemediate();
            // JSON encode this array to send with the item.
            if (!Util.isEmpty(entitlementsToRemediate)) {
                String entitlementsJSON = PolicyViolationJsonUtil.encodeEntitlementsPolicyTreeNodeList(entitlementsToRemediate);
                decision.setSelectedViolationEntitlements(entitlementsJSON);
            }
        }

        IdentitySummary owner = null;
        DecisionSummary.DelegationSummary delegationSummary = decisionSummary.getDelegation();
        if (delegationSummary != null) {
            owner = delegationSummary.getOwner();
        }
        if (owner == null) {
            owner = decisionSummary.getOwner();
        }
        if (owner != null) {
            decision.setRecipient(owner.getId());
            decision.setRecipientSummary(new IdentitySummaryDTO(owner.getId(), owner.getName(), owner.getDisplayName()));
        }

        return decision;
    }


    /**
     * @param map the row map
     * @return true if the item's decision requires review, false otherwise.
     */
    private boolean calculateRequiresReview(Map<String,Object> map) {

        CertificationAction action = (CertificationAction)map.get(COL_ACTION);
        CertificationDelegation delegation = (CertificationDelegation)map.get(COL_DELEGATION);
        CertificationDelegation parentDelegation = (CertificationDelegation)map.get(COL_PARENT_DELEGATION);

        return CertificationItem.requiresReview(action, delegation, parentDelegation);
    }

    /**
     * @param challenge the cert challenge
     * @return true if the item requires a challenge decision, false otherwise.
     */
    private boolean calculateRequiresChallengeDecision(CertificationChallenge challenge) {
        return challenge != null && challenge.requiresDecision();
    }

    /**
     * @param map the row map
     * @return true if the item's decision can be changed, false otherwise.
     */
    private boolean calculateCanChangeDecision(Map<String,Object> map)
            throws GeneralException {
        CertificationDecisionStatus decisionStatus = (CertificationDecisionStatus)map.get("IIQ_decisionChoices");
        return decisionStatus.isCanChangeDecision();
    }

    /**
     * Returns a comma separated string containing the display names for the bundles
     * @param bundles The bundles to string-ify
     * @return A comma separated string containing the display names for the bundles
     */
    private String getDisplayableBundleNames(final List<Bundle> bundles) {
        StringBuilder builder = new StringBuilder();
        if(!Util.isEmpty(bundles)) {
            /* Add the first name outside the loop so in the loop we can add comma space name
             * and not have to trim or subString or something at the end. */
            builder.append(bundles.get(0).getDisplayableName());
            for (int i = 1; i < bundles.size(); i++) {
                builder.append(", ");
                builder.append(bundles.get(i).getDisplayableName());
            }

        }
        return builder.toString();
    }

    /**
     * Set the previous decision properties on the certification item depending on what decision it is
     * @param lastDecision Last decision made on the certification item that has not been completed yet
     * @param dto CertificationItemDTO object
     * @throws GeneralException
     */
    private void setPreviousDecisionDetails(IdentityHistoryItem lastDecision, CertificationItemDTO dto) throws GeneralException {
        CertificationAction action = lastDecision.getAction();
        if (action != null && CertificationAction.Status.Mitigated.equals(action.getStatus())) {
            dto.setLastMitigationDate(action.getMitigationExpiration());

            // If now < lastMitigationDate (last mitigation date hasn't passed).
            if ((new Date().compareTo(dto.getLastMitigationDate())) < 0) {
                dto.setCurrentMitigation(true);
            }
            else {
                dto.setExpiredMitigation(true);
            }
        }
        else if (action != null && CertificationAction.Status.Remediated.equals(action.getStatus())) {
            boolean isRemediationCompleted = action.isRemediationCompleted();
            // Get the updated action object to check if remediation is complete
            if (!isRemediationCompleted) {
                Iterator<Object[]> remediationActionResults = userContext.getContext().search(CertificationAction.class,
                    new QueryOptions(Filter.eq("id", action.getId())), Arrays.asList("remediationCompleted"));
                if (remediationActionResults != null && remediationActionResults.hasNext()){
                    isRemediationCompleted = (Boolean)remediationActionResults.next()[0];
                }
                Util.flushIterator(remediationActionResults);
            }

            dto.setUnremovedRemediation(!isRemediationCompleted);
        }
        else if (action != null && CertificationAction.Status.Approved.equals(action.getStatus()) &&
                action.getRemediationDetails() != null) {
            dto.setProvisionAddsRequest(true);
        }
    }

    /**
     * Set the list of policy violations that includes the certification item
     * @param item Certification item
     * @param dto CertificationItemDTO object
     * @throws GeneralException
     */
    private void setDependentPolicyViolations(CertificationItem item, CertificationItemDTO dto) throws GeneralException {
        // get the list of policy violations that includes this item
        // Use a set to avoid any duplicates
        Set<String> violations = new HashSet<String>();

        if (CertificationItem.Type.Bundle.equals(dto.getType())) {
            QueryOptions ops = new QueryOptions(Filter.eq("type", CertificationItem.Type.PolicyViolation));
            ops.add(Filter.eq("parent.id", item.getCertificationEntity().getId()));
            Iterator<Object[]> violationItems = userContext.getContext().search(CertificationItem.class, ops,
               Arrays.asList("policyViolation"));

            while (violationItems.hasNext()) {
                PolicyViolation violation = (PolicyViolation)violationItems.next()[0];
                if (violation != null && (violation.getRightBundles() != null &&
                    Util.csvToList(violation.getRightBundles()).contains(dto.getRoleName())) ||
                    (violation.getLeftBundles() != null &&
                    Util.csvToList(violation.getLeftBundles()).contains(dto.getRoleName()))) {
                    violations.add(violation.getDisplayableName());
                }
            }
        }
        else if (CertificationItem.Type.Exception.equals(dto.getType())) {
            QueryOptions ops = new QueryOptions(Filter.eq("type", CertificationItem.Type.PolicyViolation));
            ops.add(Filter.eq("parent.id", item.getCertificationEntity().getId()));
            ops.add(Filter.containsAll("applicationNames", Arrays.asList(dto.getApplication())));
            Iterator<Object[]> violationItems = userContext.getContext().search(CertificationItem.class, ops,
                Arrays.asList("policyViolation"));

            while (violationItems.hasNext()) {
                PolicyViolation violation = (PolicyViolation)violationItems.next()[0];
                if (violation != null && violation.getViolatingEntitlements() != null) {
                    for (IdentitySelector.MatchTerm term : violation.getViolatingEntitlements()) {
                        if (matchTerm(term, item.getExceptionEntitlements())) {
                            violations.add(violation.getDisplayableName());
                        }
                    }
                }
            }
        }
        dto.setPolicyViolations(new ArrayList<>(violations));
    }

    /**
     * Returns true if the entitlement is included in the violations
     * @param term MatchTerm  representing the attribute or permission value for the violating entitlements
     * @param snapshot Entitlement to be checked for
     * @return true if the entitlement is included in the violations, false otherwise
     * @throws GeneralException
     */
    private boolean matchTerm(IdentitySelector.MatchTerm term, EntitlementSnapshot snapshot) throws GeneralException {

        if (term.getApplication() != null && snapshot.getApplication().equals(term.getApplication().getName())) {
             if (snapshot.isValueGranularity()) {
                if (term.isPermission()) {
                    return snapshot.getPermissionTarget() != null && snapshot.getPermissionTarget().equals(term.getName())
                        && snapshot.getPermissionRight() != null && snapshot.getPermissionRight().equals(term.getValue());
                } else {
                    return snapshot.getAttributeName() != null && snapshot.getAttributeName().equals(term.getName())
                        && snapshot.getAttributeValue() != null && snapshot.getAttributeValue().equals(term.getValue());
                }
            }
        }
        return false;
    }

    /**
     * Get the result group meta data for the results. Usually this just defers to BaseListService, but we need to handle
     * displayName column specifically since it is calculated
     */
    private List<ListResultGroup> getResultGroups(List<Map<String, Object>> results, int count, Class<? extends SailPointObject> scope, QueryOptions ops) throws GeneralException {
        final int RESULT_GROUP_PAGE_SIZE = 10;
        
        String groupBy = this.listServiceContext.getGroupBy();
        if (!Util.nullSafeEq(groupBy, COL_DISPLAY_NAME) &&
            !Util.nullSafeEq(groupBy, COL_EXCEPTION_ATTR_VALUE)) {
            return super.getResultGroups(results, scope, ops);
        }
        
        List<ListResultGroup> resultGroups = new ArrayList<>();
        if (Util.isEmpty(results)) {
            return resultGroups;
        }
        
        // 1. First get groups from the results we have.
        String currentDisplayName = null;
        ListResultGroup currentResultGroup = null;

        // IIQMAG-1905: Role membership certs do a little sleight of hand with display name in other places, and here it was
        // tripping up creating groups that span multiple pages in the cert UI. Since we know in this case that the
        // groupBy column is the right one to use to bunch items into groups we'll just use it. Oddly enough this tactic does NOT work
        // for Entitlement Owner certs. Our different cert types are honestly all over the map in terms of how they deal
        // with display name and at some point we might want to look at refactoring this and the column configs, but not
        // so close to end of 7.3. This fix is narrowly constrained to just Role Mem certs since that's where the grouping
        // bug is.
        String displayNameProp =
                this.listServiceContext.getCertification().getType() == Type.BusinessRoleMembership ?
                groupBy : findColumnConfig(COL_DISPLAY_NAME).getJsonProperty();

        //initialize current matching display name to first display name in the result list
        String currentMatchDisplayName = (String)results.get(0).get(displayNameProp);
        String previousMatchDisplayName = currentMatchDisplayName;
        for (Map<String, Object> result : results) {
            String newDisplayName = (String)result.get(groupBy);
            if (!Util.nullSafeEq(currentDisplayName, newDisplayName)) {
                if (currentResultGroup != null) {
                    currentResultGroup.setCount(Util.size(currentResultGroup.getObjectIds()));
                    resultGroups.add(currentResultGroup);
                    currentMatchDisplayName = (String) result.get(displayNameProp);
                }
                currentDisplayName = newDisplayName;
                currentResultGroup = new ListResultGroup();
                currentResultGroup.addProperty(groupBy, currentDisplayName);
            }
            currentResultGroup.addObjectId((String) result.get(COL_ID));
        }
        
        if (currentResultGroup != null) {
            currentResultGroup.setCount(Util.size(currentResultGroup.getObjectIds()));
            resultGroups.add(currentResultGroup);
        }

        // 2. Then work backward to flesh out "first" group items
        // MATH ALERT! We are paging backwards, but need to make sure we do not duplicate results if we are less than
        // a page from the front
        boolean doMore = this.listServiceContext.getStart() > 0;
        int newStart = Math.max(0, this.listServiceContext.getStart() - RESULT_GROUP_PAGE_SIZE);
        int lastStart = this.listServiceContext.getStart();
        while (doMore && newStart >= 0) {
            doMore = addIdsToDisplayNameResultGroup(previousMatchDisplayName, resultGroups.get(0), ops, newStart, Math.min(RESULT_GROUP_PAGE_SIZE, lastStart));
            lastStart = newStart;
            newStart = newStart - RESULT_GROUP_PAGE_SIZE;
        }

        // 3. Work forward to flesh out "last" group items
        newStart = Math.min(this.listServiceContext.getStart() + this.listServiceContext.getLimit(), count);
        doMore = true;
        while (doMore && newStart < count) {
            doMore = addIdsToDisplayNameResultGroup(currentMatchDisplayName, resultGroups.get(resultGroups.size() - 1), ops, newStart, RESULT_GROUP_PAGE_SIZE);
            newStart = newStart + RESULT_GROUP_PAGE_SIZE;
        }

        return resultGroups;
    }

    /**
     * Helper method for getting IDs that are in the given resultGroup, if they span outside of our current page of results.
     * @return false if ANY of the items in the span do not match, which indicates that no more looking is needed, since it
     * assumes our results are sorted, true if all items match the resultGroup and another page should be loaded. 
     * 
     */
    @SuppressWarnings("unchecked")
    private boolean addIdsToDisplayNameResultGroup(String displayName, ListResultGroup resultGroup, QueryOptions queryOptions, int start, int limit)
            throws GeneralException {
        QueryOptions newOps = new QueryOptions(queryOptions);
        newOps.setFirstRow(start);
        newOps.setResultLimit(limit);
        ColumnConfig displayNameConfig = findColumnConfig(COL_DISPLAY_NAME);
        
        // Fudge a ViewBuild list result containing only id and displayName columns
        List<ColumnConfig> displayNameColumns = Collections.singletonList(displayNameConfig);
        ViewEvaluationContext evaluationContext = new ViewEvaluationContext(this.userContext, displayNameColumns);
        ViewBuilder viewBuilder = new ViewBuilder(evaluationContext, CertificationItem.class, displayNameColumns, Collections.singletonList("id"));
        ListResult viewResults = viewBuilder.getResult(newOps);

        boolean unmatched = false;
        // View results are a list of maps.
        for (Map<String, Object> result : (List<Map<String, Object>>)Util.iterate(viewResults.getObjects())) {
            String rs = (String)result.get(displayNameConfig.getJsonProperty());
            if (Util.nullSafeEq(displayName, result.get(displayNameConfig.getJsonProperty()))) {
                // Display name matches, add id to result object id list
                resultGroup.addObjectId((String)result.get(COL_ID));
            } else {
                // We had at least one result that did not match, flag it so we know we need to go no further
                unmatched = true;
            }
        }
       
        resultGroup.setCount(Util.size(resultGroup.getObjectIds()));
        return !unmatched;
    }

    @Override
    protected Object convertColumn(Entry<String, Object> entry, ColumnConfig config, Map<String, Object> rawObject)
        throws GeneralException {

        // The BaseListService will try to localize this, but we actually want the raw Status enum value.
        // override this to prevent converting to a localized string.
        if (COL_SUMMARY_STATUS.equals(config.getDataIndex())) {
            return entry.getValue();
        }

        return super.convertColumn(entry, config, rawObject);
    }

    /**
     * Check if any of the projection columns require an identity join
     */
    private boolean hasIdentityProjectionColumn(List<String> projectionColumns) throws GeneralException {
        for (String column : Util.iterate(projectionColumns)) {
            if (column.startsWith(IDENTITY_JOIN_PREFIX)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Get the correct default orderBy based on the cert type.
     * @param type - Certification type
     * @return column to order by
     */
    private String getOrderBy(Type type) {
        switch (type) {
            case AccountGroupPermissions:
            case AccountGroupMembership:
                return "parent.accountGroup";
            case DataOwner:
                return "exceptionAttributeValue";
            case BusinessRoleComposition:
                return "parent.targetDisplayName";
            default:
                return "parent.identity";
        }
    }

    private boolean isNoRecommendationFilter(Filter f) {
        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter lf = (Filter.LeafFilter) f;
            return lf.getProperty().equals(IdentityCertItemListFilterContext.RECOMMEND_FILTER) &&
                    lf.getValue().equals(Recommendation.RecommendedDecision.NO_RECOMMENDATION.name());
        }

        return false;
    }

    private boolean isAutoDecisionFalseFilter(Filter f) {
        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter lf = (Filter.LeafFilter) f;
            if (lf.getProperty().equals(IdentityCertItemListFilterContext.AUTO_DECIDE_FILTER)) {
                return lf.getValue().equals(false);
            }
        }

        return false;
    }
}
