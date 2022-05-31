/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.Explanator;
import sailpoint.api.Localizer;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.connector.Connector;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationChallenge;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.RoleRelationships;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Schema;
import sailpoint.object.UIPreferences;
import sailpoint.role.RoleAssignmentRelationships;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridField;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
import sailpoint.web.view.ViewEvaluationContext;
import sailpoint.web.view.certification.CertificationItemChangesDetectedColumn;
import sailpoint.web.view.certification.CertificationItemDecisionColumn;
import sailpoint.web.view.certification.CertificationItemDescriptionColumn;
import sailpoint.web.view.certification.CertificationItemDisplayNameColumn;

/**
 * A JSF bean for listing items in a certification.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationItemsListBean
    extends AbstractCertificationContentsListBean<CertificationItem> {

    private static final Log log = LogFactory.getLog(CertificationItemsListBean.class);
    public static final String FILTER_SESSION_ATTRIBUTE = "certItemsListFilter";
    public static final String GRID_STATE = "certItemsListGridState";

    // These are the names of calculated properties that get returned in the
    // row map.
    public static final String DESCRIPTION_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "description";

    // Json used by the grid renderer to generate complex description html
    public static final String DESCRIPTION_JSON_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "descriptionJson";

    public static final String CALCULATED_STATUS_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "calculatedStatus";
    public static final String DECISION_CHOICES =
        CALCULATED_COLUMN_PREFIX + "decisionChoices";
    public static final String CHALLENGED_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "challenged";
    public static final String REVIEW_REQUIRED_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "reviewRequired";
    public static final String HAS_COMMENT =
        CALCULATED_COLUMN_PREFIX + "hasComment";
    public static final String APPLICATION_KEY =
        CALCULATED_COLUMN_PREFIX + "applicationKey";
    public static final String CAN_CHANGE_DECISION =
        CALCULATED_COLUMN_PREFIX + "canChangeDecision";
    // only used by the export to CSV
    public static final String ENTITLEMENT_DESCRIPTION_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "entitlementDescription";

    public static final String APPLICATION_DESCRIPTION_PROPERTY =
        CALCULATED_COLUMN_PREFIX + "applicationDescription";
    
    public static final String[] DEFAULT_SORT_COLUMN_NAMES = 
            {"parent.identity", "id"};

    /** Keep a map of applicationName, applicationDescription to prevent duplicate
     * queries */
    Map<String, String> applicationDescriptions;

    /**
     * Certification property indicating that comments are
     * required on approval. This is used when creating the decision
     * radios.
     */
    private boolean requireApprovalComments;

    /**
     * True if the user preference, or certification configuration specifies
     * that an entitlement's description should be displayed rather than
     * it's value.
     */
    private Boolean displayEntDescriptions;

    /**
     * True if the approve account decision is enabled.
     */
    private boolean enableApproveAccountAction;

    private boolean enableRevokeAccountAction;

    /**
     * Cert level settings that override the system configs
     */
    private CertificationDefinition certDefinition;

    /**
     * Is line item delegation enabled?
     * This reflects the cert setting first and the checks the sysconfig
     */
    private boolean delegationEnabled = false;

    /** Set to true when the rows are being exported to csv -- helps us strip out
     * columns that are unneded **/
    private boolean isExport = false;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////


    /**
     * Constructor.
     */
    public CertificationItemsListBean() throws GeneralException {
        super();
        super.setScope(CertificationItem.class);

        Certification cert = super.getCertification();
        if ((null != cert) && cert.isContinuous()) {
            super.setDefaultSortColumn("overdueDate");
        }

        if (cert != null){
            requireApprovalComments= cert.isRequireApprovalComments();

            String entDescParm = getRequestParameter("showEntDesc");
            if (entDescParm != null) {
                displayEntDescriptions = Boolean.parseBoolean(entDescParm);
            }else if (getLoggedInUser().getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC) != null){
                displayEntDescriptions =
                        Util.otob(getLoggedInUser().getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC));
            } else if(cert.getDisplayEntitlementDescription() != null){
                displayEntDescriptions = cert.getDisplayEntitlementDescription().booleanValue();
            } else {
                displayEntDescriptions = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
            }

            certDefinition = cert.getCertificationDefinition(getContext());

            if (certDefinition == null) {
                certDefinition = new CertificationDefinition();
                certDefinition.initialize(getContext());
            }
            delegationEnabled = certDefinition.isAllowItemDelegation(getContext());

            // the presence of this param indicates that we have just landed
            // on the cert. Make sure that the filter bean stored on session
            // matches the current certification
            if ("true".equals(getRequestParameter("initFilter"))){
                validateFilter(getFacesContext(), cert.getId());
            }
        }

        if (certDefinition == null) {
            certDefinition = new CertificationDefinition();
            certDefinition.initialize(getContext());
        }
        delegationEnabled = certDefinition.isAllowItemDelegation(getContext());

        enableApproveAccountAction = certDefinition.isAllowApproveAccounts(getContext());
        enableRevokeAccountAction = certDefinition.isAllowAccountRevocation(getContext());

        applicationDescriptions = new HashMap<String,String>();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // LIST BEAN IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Clear the filter from the session.
     *
     * @param  context  The FacesContext to use.
     */
    static void resetFilter(FacesContext context) {
        context.getExternalContext().getSessionMap().remove(FILTER_SESSION_ATTRIBUTE);
    }

    public CertificationFilterBean getFilter() {
        validateFilter(getFacesContext(), getCertificationId());
        return super.getFilter();
    }
    
    static void validateFilter(FacesContext context, String certificationId) {

         CertificationFilterBean filter =
                 (CertificationFilterBean)context.getExternalContext().getSessionMap().get(FILTER_SESSION_ATTRIBUTE);

         if (filter != null && certificationId != null && !certificationId.equals(filter.getCertifictionId())){
            resetFilter(context);
         }
    }

    String getFilterSessionAttribute() {
        return FILTER_SESSION_ATTRIBUTE;
    }

    void loadColumnConfig() throws GeneralException {

        if (this.isBusinessRoleMembershipCert()) {
            // todo: shouldnt assign the UIConfig list directly to the columns property. See bug#3513
            this.columns = super.getUIConfig().getCertificationBusinessRoleMembershipTableColumns();
        }
        else {
            if (isDataOwnerCert()) {
                this.columns = super.getUIConfig().getCertificationItemDataOwnerTableColumns();
            } else {
                this.columns = super.getUIConfig().getCertificationItemTableColumns();
            }
        }

        // If UIConfig didn't have the information, throw an exception.
        if (null == this.columns) {
            throw new GeneralException("UIConfig does not specify columns for certification items list - certificationItemTableColumns");
        }
    }


    /**
     * Returns only those columns needed by the UI to build a display table,
     * excluding the data-only fields. 
     * 
     * @return
     * @throws GeneralException
     */
    public List<ColumnConfig> getRemediationColumns() throws GeneralException {
        List<ColumnConfig> remediationCols = new ArrayList<ColumnConfig>();                    
        for (ColumnConfig col : super.getUIConfig().getRemediationTableColumns()) {
            if (!col.isFieldOnly())
                remediationCols.add(col);
        }
        
        return remediationCols;
    }

    
    /**
     * Returns all columns, including the data-only fields. 
     * 
     * @return
     * @throws GeneralException
     */
    public List<ColumnConfig> getRemediationFields() throws GeneralException {
        return super.getUIConfig().getRemediationTableColumns();
    }
    
    /**
     * Get RemediationGrid GridResponseObj
     * @return 
     * @throws GeneralException
     */
    public String getRemediationColumnsJSON() throws GeneralException {
    	
    	return super.getColumnJSON(getDefaultSortColumn(), getRemediationColumns());
    }
    
    
    private boolean isDataOwnerCert() throws GeneralException {

        Certification cert = super.getCertification();
        if (null != cert) {
            return Certification.Type.DataOwner.equals(cert.getType());
        }

        return false;
    }

    private boolean isBusinessRoleMembershipCert() throws GeneralException {

        Certification cert = super.getCertification();
        if (null != cert) {
            return Certification.Type.BusinessRoleMembership.equals(cert.getType());
        }

        return false;
    }

    public boolean isDisplayingItems() {
        return true;
    }

    public String getEntityPropertyPrefix() {
        return "parent.";
    }

    public int getNonSortColumnCount() {
        return 6;
    }


    /** Return the default of the parent.identity if no other sort is provided **/
    public String getDefaultSortColumn() throws GeneralException {
        return getSortColumnMap().get("parent.identity");
    }

    void addCustomSortColumns(int startIdx, Map<String,String> sortMap) {

        // Description is a calculated property, so we will sort by the
        // contributing columns.
        replaceEntryWithValue(sortMap, DESCRIPTION_PROPERTY,
        "type, bundle, exceptionApplication, exceptionAttributeName, exceptionAttributeValue, exceptionPermissionTarget, exceptionPermissionRight, violationSummary");

        // Sort the exception app secondarily by the native identity.
        replaceEntryWithValue(sortMap, "exceptionApplication",
        "exceptionApplication, exceptionEntitlements.nativeIdentity");

        // Sort secondarily by identity name when sorting by overdue.
        replaceEntryWithValue(sortMap, "overdueDate", "overdueDate, parent.identity");

        replaceEntryWithValue(sortMap, "IIQ_changes_detected", "parent.newUser, hasDifferences");
    }

    void addCustomSortColumnConfigs(int startIdx, Map<String,ColumnConfig> sortMap) {

        // Description is a calculated property, so we will sort by the
        // contributing columns.
        replaceSortProperty(sortMap, DESCRIPTION_PROPERTY,
        "type, bundle, exceptionApplication, exceptionAttributeName, exceptionAttributeValue, exceptionPermissionTarget, exceptionPermissionRight, violationSummary");

        // Sort the exception app secondarily by the native identity.
        replaceSortProperty(sortMap, "exceptionApplication",
        "exceptionApplication, exceptionEntitlements.nativeIdentity");

        // Sort secondarily by identity name when sorting by overdue.
        replaceSortProperty(sortMap, "overdueDate", "overdueDate, parent.identity");

        replaceSortProperty(sortMap, "IIQ_changes_detected", "parent.newUser, hasDifferences");
    }

    void replaceEntryWithValue(Map<String,String> map, String existingValue,
            String newValue) {

        for (Map.Entry<String,String> entry : map.entrySet()) {
            if (existingValue.equals(entry.getValue())) {
                map.put(entry.getKey(), newValue);
                break;
            }
        }
    }

    void replaceSortProperty(Map<String,ColumnConfig> map, String propertyName, String newValue) {

        for (Map.Entry<String,ColumnConfig> entry : map.entrySet()) {
            if (entry.getKey().equals(propertyName)) {
                entry.getValue().setSortProperty(newValue);
                break;
            }
        }
    }

    void addDefaultProjectionAttributes(List<String> projectionAttrs) {

        projectionAttrs.add("id");

        projectionAttrs.add("action");
        projectionAttrs.add("delegation");
        projectionAttrs.add("challenge");
        projectionAttrs.add("parent.delegation");
        projectionAttrs.add("phase");

        projectionAttrs.add("parent.newUser");
        projectionAttrs.add("parent.targetId");
        projectionAttrs.add("parent.type");

        projectionAttrs.add("summaryStatus");
        projectionAttrs.add("hasDifferences");
        projectionAttrs.add("actionRequired");

        projectionAttrs.add("type");
        projectionAttrs.add("subType");

        projectionAttrs.add("bundle");

        projectionAttrs.add("exceptionEntitlements");
        projectionAttrs.add("exceptionAttributeName");
        projectionAttrs.add("exceptionAttributeValue");
        projectionAttrs.add("exceptionPermissionTarget");
        projectionAttrs.add("exceptionPermissionRight");

        projectionAttrs.add("violationSummary");

        projectionAttrs.add("continuousState");
        projectionAttrs.add("nextContinuousStateChange");

        projectionAttrs.add("policyViolation");

        projectionAttrs.add("targetDisplayName");
        projectionAttrs.add("targetName");
        projectionAttrs.add("targetId");
        
        projectionAttrs.add("bundleAssignmentId");


        CertificationItemDecisionColumn column = new CertificationItemDecisionColumn();
        try {
            List<String> decisionCols = column.getProjectionColumns();
            if (decisionCols != null){
                for(String colName : decisionCols){
                    if (!projectionAttrs.contains(colName))
                        projectionAttrs.add(colName);
                }
            }
        } catch (GeneralException e) {
            log.error("Error retrieving decision projects columns.", e);
            throw new RuntimeException(e);
        }

    }

    void addExtraDisplayableColumns(List<String> displayableColumns) {
        displayableColumns.add("Status");
        displayableColumns.add("Changes Detected");
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException{
        QueryOptions qo = super.getQueryOptions();

        /** Add a default sort by identity id and item id to make the sorting 
         * of values that are the same or empty consistent
            Need to ensure that the ordering doesn't already exist for MSSQL**/
        List<String> existingSortColumnNames = new ArrayList<String>();
        if(qo.getOrderings()!=null) {
            for(Ordering ordering : qo.getOrderings()) {
                existingSortColumnNames.add(ordering.getColumn());
            }
        }
        
        for (String orderColumn : DEFAULT_SORT_COLUMN_NAMES) {
            if (!existingSortColumnNames.contains(orderColumn)) {
                 qo.addOrdering(qo.getOrderings().size(), orderColumn, true);
            }
        }

        return qo;
    }

    /** Overriding to add the remediation columns to the list of sort columns **/
    @Override
    public Map<String, String> getSortColumnMap() throws GeneralException
    {
        Map<String,String> sortMap = super.getSortColumnMap();

        List<ColumnConfig> columns = getRemediationColumns();
        if (null != columns && !columns.isEmpty()) {
            final int columnCount = columns.size();

            for(int j =0; j < columnCount; j++) {
                sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
            }
        }

        return sortMap;
    }

    @Override
    public Map<String, ColumnConfig> getSortColumnConfigMap() throws GeneralException {

        Map<String,ColumnConfig> sortMap = super.getSortColumnConfigMap();

        List<ColumnConfig> columns = getRemediationColumns();
        if (null != columns && !columns.isEmpty()) {
            final int columnCount = columns.size();

            for(int j =0; j < columnCount; j++) {
                sortMap.put(columns.get(j).getJsonProperty(), columns.get(j));
            }
        }

        return sortMap;
    }

    /** Override **/
    public GridResponseMetaData getMetaData() {
        GridResponseMetaData meta = new GridResponseMetaData();
        for (ColumnConfig column : columns) {
            String name = column.getName() == null ? column.getDataIndex() : column.getName();
            meta.addField(new GridField(name, column.getDataIndex()));
        }
        meta.addField(new GridField(CALCULATED_STATUS_PROPERTY));
        meta.addField(new GridField(DECISION_CHOICES));
        meta.addField(new GridField("actionRequired"));
        meta.addField(new GridField(APPLICATION_KEY));
        meta.addField(new GridField(CAN_CHANGE_DECISION));
        meta.addField(new GridField("certificationId"));
        meta.addField(new GridField(CHALLENGED_PROPERTY));
        meta.addField(new GridField("IIQ_changes_detected"));
        meta.addField(new GridField("continuousState"));
        meta.addField(new GridField(CONTINUOUS_STATE_NAME));
        meta.addField(new GridField("description"));
        meta.addField(new GridField(DESCRIPTION_JSON_PROPERTY));
        meta.addField(new GridField(HAS_COMMENT));
        meta.addField(new GridField("identityId"));
        meta.addField(new GridField("missingRequiredRoles"));
        meta.addField(new GridField("nextContinuousChange"));
        meta.addField(new GridField("requireApprovalComments"));
        meta.addField(new GridField("review"));
        meta.addField(new GridField(REVIEW_REQUIRED_PROPERTY));
        meta.addField(new GridField("roleDescription"));
        meta.addField(new GridField("roleIcon"));
        meta.addField(new GridField("roleId"));
        meta.addField(new GridField("bundle"));
        meta.addField(new GridField("subType"));
        meta.addField(new GridField("type"));
        meta.addField(new GridField("violationId"));
        meta.addField(new GridField("accountKey"));

        return meta;
    }

    /**
     * Add some calculated properties to the Map that is returned.
     */
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {
        Localizer localizer = new Localizer(getContext());
        Map<String,Object> map = super.convertRow(row, cols);

        EntitlementSnapshot snap = (EntitlementSnapshot) map.get("exceptionEntitlements");
        if (snap != null){
            map.put("accountKey", snap.generateAccountKey());
        }

        map.put(DESCRIPTION_PROPERTY, calculateDescription(map));
        map.put(DESCRIPTION_JSON_PROPERTY, calculateDescriptionJson(map));
        map.put(CALCULATED_STATUS_PROPERTY, calculateStatus(map));
        map.put(DECISION_CHOICES, getDecisionChoices(map));

        map.put("certificationId", getCertificationId());

        // Don't do this if exporting. This is expensive.
        if (!isExport) {
            map.put(HAS_COMMENT, calculateHasComment(map));
        }

        map.put(APPLICATION_KEY, calculateApplicationKey(map));

        CertificationChallenge challenge = (CertificationChallenge) map.get("challenge");
        boolean challenged = ((null != challenge) && challenge.requiresDecision());
        map.put(CHALLENGED_PROPERTY, challenged);

        CertificationAction action = (CertificationAction) map.get("action");
        CertificationDelegation itemDel = (CertificationDelegation) map.get("delegation");
        CertificationDelegation entityDel = (CertificationDelegation) map.get("parent.delegation");
        boolean reviewRequired =
            CertificationItem.requiresReview(action, itemDel, entityDel);
        map.put(REVIEW_REQUIRED_PROPERTY, reviewRequired);

        map.put(CAN_CHANGE_DECISION, calculateCanChangeDecision(map));

        String bundleName = (String)map.get("bundle");
        String bundleAssignmentId = (String)map.get("bundleAssignmentId");

        Bundle bundle = null;
        if (bundleName != null)
            bundle = getContext().getObjectByName(Bundle.class, bundleName);

        Identity identity = convertIdentityFields(map);

        if (bundle != null && identity != null){
            map.put("roleId", bundle.getId());
            map.put("bundle", bundle.getName());
            map.put("roleDescription", localizer.getLocalizedValue(bundle, Localizer.ATTR_DESCRIPTION, getLocale()));

            RoleTypeDefinition typeDef = bundle.getRoleTypeDefinition();
            if (typeDef != null)
                map.put("roleIcon", typeDef.getIcon());

            // check if the identity has any missing requirements for the given role.
            // These will be ignored if the certification does not allow provisioning missing reqs
            boolean missingReqs = false;
            if (getCertification().isAllowProvisioningRequirements()){
                if(Util.isNotNullOrEmpty(bundleAssignmentId)) {
                    RoleAssignmentRelationships relationships = new RoleAssignmentRelationships(getContext());
                    relationships.analyze(identity);
                    missingReqs = !relationships.getMissingRequirements(bundleAssignmentId).isEmpty();
                } else {
                    //This could possibly be a RoleDetection not belonging to a RoleAssignment as well
                    //Could Test subtype here if we want
                    log.warn("Encountered Certificaion Item containing a bundle with no Assignment Id");
                    RoleRelationships relationships = new RoleRelationships();
                    relationships.analyze(identity);
                    missingReqs = !relationships.getMissingRequirements(bundle).isEmpty();
                }
            }

            map.put("missingRequiredRoles", missingReqs);
            //Overload the Application and AccountId columns. These columns were initially used for
            //Extra entitlements, but Roles now have TargetAccounts as well. -rap
            //MT: Bummer to load up the item just to get the assignment info but accuracy matters, yall.
            CertificationItem item = getContext().getObjectById(CertificationItem.class, (String)map.get("id"));
            CertificationUtil.BundleAccountInfo bundleAccountInfo = CertificationUtil.getBundleAccountInfo(item, identity, getContext());
            map.put("exceptionApplication", bundleAccountInfo.applications);
            map.put("exceptionEntitlements.displayName", bundleAccountInfo.accounts);
        }

        map.put("requireApprovalComments", requireApprovalComments);

        String appDescription = calculateApplicationDescription(map);
        if(isExport) {
            appDescription = WebUtil.stripHTML(appDescription);
        }
        map.put(APPLICATION_DESCRIPTION_PROPERTY, appDescription);

        String description = WebUtil.stripHTML(calculateEntitlementDescription(map));
        map.put(ENTITLEMENT_DESCRIPTION_PROPERTY, description);

        /** Remove exception entitlements to prevent json serialization errors**/
        map.remove("exceptionEntitlements");

        /** Changes Detected **/
        map.put("IIQ_changes_detected", calculateChangesDetected(map));

        /** Don't want to send the entire object down **/
        if(map.get("policyViolation")!=null) {
            PolicyViolation violation = (PolicyViolation)map.get("policyViolation");
            map.put("violationId", violation.getId());
        }
        map.remove("policyViolation");
        map.remove("action");
        map.remove("delegation");
        map.remove("parent.delegation");
        map.remove("challenge");

        fixExceptionEntitlementsDisplayName(map);

        return map;
    }

    /**
     * When exceptionEntitlements.displayName == null,
     * we need to use nativeIdentity instead of displayName.
     * @param map the row map
     */
    private void fixExceptionEntitlementsDisplayName(Map<String, Object> map) {
        final String displayNameKey = "exceptionEntitlements.displayName";
        final String nativeIdentityKey = "exceptionEntitlements.nativeIdentity";
        if (map.containsKey(displayNameKey)) {
            String displayName = (String) map.get(displayNameKey);
            if (Util.isNullOrEmpty(displayName)) {
                String nativeIdentity = (String) map.get(nativeIdentityKey);
                if (Util.isNotNullOrEmpty(nativeIdentity)) {
                    map.put(displayNameKey, nativeIdentity);
                }
            }
        }
    }

    /**
     * DataOwnerCertifications have identities in a different spot
     * than identity based certifications. It is in the targetId field
     * and not in the parent.identity we need to do a join for that
     */
    private Identity convertIdentityFields(Map<String, Object> map)
            throws GeneralException {
        
        Identity identity = null;

        if (this.certDefinition.getType() != Certification.Type.DataOwner && map.get("parent.identity") != null){
            identity = getContext().getObjectByName(Identity.class, map.get("parent.identity").toString());
            if (identity != null) {
                map.put("identityId", identity.getId());
            }
        }
        
        return identity;
    }
    
    @Override
    public void exportToCSV() throws Exception{

        /** The ui can call export to csv from the identity view.
         * If the export has been called from the identity view, we need to pull
         * in the filters from the identity view and apply those filters to this view.
         */
        ValueBinding vb =
            getFacesContext().getApplication().createValueBinding("#{certification}");
        CertificationBean certification =
            (CertificationBean) vb.getValue(getFacesContext());
        try {
            if(certification.isListEntities()){

                vb = getFacesContext().getApplication().createValueBinding("#{certificationEntityList}");
                CertificationEntityListBean certificationEntityList =
                    (CertificationEntityListBean) vb.getValue(getFacesContext());


                getFilter().setFilters((certificationEntityList.getFilter().getFilters()));
                getFilter().setStatus(certificationEntityList.getFilter().getStatus());
                getFilter().attachContext(this, this);
            }
        } catch (GeneralException ge) {
            log.warn("Unable to update filters for entitlement view: " + ge.getMessage());
        }
        this.setExport(true);
        super.exportToCSV();
    }

    private Object getDecisionChoices(Map<String,Object> resultRow) throws GeneralException{

        if (!resultRow.containsKey("parent.certification.id"))
            resultRow.put("parent.certification.id", this.getCertificationId());

        CertificationItemDecisionColumn col = new CertificationItemDecisionColumn();
        col.init(new ViewEvaluationContext(this, null), null);

        return col.getValue(resultRow);
    }

    private boolean calculateHasComment(Map<String, Object> map) throws GeneralException {
        CertificationService cSvc = new CertificationService(getContext());

        String identityId = (String)map.get("parent.targetId");
        CertificationEntity.Type type = (CertificationEntity.Type)map.get("parent.type");
        if (CertificationEntity.Type.AccountGroup.equals(type)){
            identityId = (String)map.get("targetId");
        }

        return cSvc.hasComment(identityId, (String)map.get("id"));
    }

    @SuppressWarnings("rawtypes")
    private String calculateDescription(Map<String, Object> row) throws GeneralException{

        CertificationItemDisplayNameColumn column = new CertificationItemDisplayNameColumn();
        column.init(new ViewEvaluationContext(this, null), null);
        return (String)column.getValue(row);
    }

    @SuppressWarnings("rawtypes")
    private String calculateEntitlementDescription(Map<String, Object> row) throws GeneralException{
        CertificationItemDescriptionColumn col = new CertificationItemDescriptionColumn();
        col.init(new ViewEvaluationContext(this, null), null);

        return (String)col.getValue(row);
    }

    /**
     * The description for exceptions an account group membership items may
     * require complex rendering within the grid. Specifically, we need
     * to allow the user to be able to view an attributes description or
     * value, and they can switch back and forth with a click.
     *
     * The method creates a json object which contains all the necessary
     * info to generate the required html on the client side. The actual work
     * of rendering is performed in IdentityItemsGrid.js.
     *
     * @param row
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    private String calculateDescriptionJson(Map row) throws GeneralException{

        Map<String, Object> descriptions = new HashMap<String, Object>();

        // This should not happen but we got a unreproducible NPE here once so
        // might as well be safe.
        if (row.get("type") == null)
            return null;

        CertificationItem.Type type = (CertificationItem.Type) row.get("type");
        if (CertificationItem.Type.AccountGroupMembership.equals(type) || CertificationItem.Type.Exception.equals(type) || CertificationItem.Type.DataOwner.equals(type)){
            EntitlementSnapshot snap = (EntitlementSnapshot) row.get("exceptionEntitlements");
            if (snap != null){

                CertificationEntity.Type entityType = (CertificationEntity.Type)row.get("parent.type");
                String objectType = Connector.TYPE_ACCOUNT;
                if ( !CertificationEntity.Type.Identity.equals(entityType) && !CertificationEntity.Type.DataOwner.equals(entityType) ) {
                    objectType = Connector.TYPE_GROUP;
                }

                List<Map<String, Object>>  entitlementDesc = getAttributes(snap, objectType);
                descriptions.put("entitlements", entitlementDesc);
                descriptions.put("descriptionFirst", displayEntDescriptions);
                return JsonHelper.toJson(descriptions);
            }
        }


        return null;
    }

    /**
     * Returns the snapshots attributes and permissions in list of maps that
     * can be easily serialized into json.
     *
     * @param snap
     * @param objectType
     * @return
     * @throws GeneralException
     */
    private List<Map<String, Object>> getAttributes(EntitlementSnapshot snap, String objectType) throws GeneralException
    {

        if (snap == null)
            return null;


        List<Map<String, Object>> attrs = new ArrayList<Map<String, Object>>();

        Application app = getContext().getObjectByName(Application.class, snap.getApplicationName());
        Schema schema = app != null ? app.getSchema(objectType) : null;

        if ((null != snap.getAttributes()) && (snap.getAttributes().size() > 0)) {

            // this does some work, then we ask ManagedAttriuteCache for the objects,
            // should we have an interface that just does that here?
            Map<String, Map<String, String>> descriptions = 
                Explanator.getDescriptions(app, snap.getAttributes(), getLocale());

            if (descriptions != null){
                for (Map.Entry<String, Map<String, String>> description : descriptions.entrySet()) {

                    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
                    for (Map.Entry<String, String> v : description.getValue().entrySet()) {
                        Map<String, Object> val = new HashMap<String, Object>();
                        
                        String attrName = description.getKey();
                        String value = v.getKey();

                        String displayValue = Explanator.getDisplayValue(app, attrName, value);

                        if (displayValue != null)
                            value = displayValue;

                        String i18nVal = Internationalizer.getMessage(value, getLocale());
                        val.put("value", i18nVal != null ? i18nVal : value);
                        String i18nDesc = Internationalizer.getMessage(v.getValue(), getLocale());
                        val.put("description", i18nDesc != null ? i18nDesc : v.getValue());

                        //
                        
                        if (WebUtil.isGroupAttribute(snap.getApplicationName(), attrName)) {
                            Map<String, String> popup = new HashMap<String, String>();
                            popup.put("appName", snap.getApplicationName());
                            popup.put("attrName", attrName);
                            popup.put("value", v.getKey());
                            val.put("popup", popup);

                            // overwrite the value with the group's displayable name
                            String displayableName =
                                WebUtil.getGroupDisplayableName(snap.getApplicationName(),
                                                                attrName,
                                                                v.getKey());
                            val.put("value", displayableName);
                        } else {
                            val.put("popup", false);
                        }

                        values.add(val);
                    }

                    Map<String, Object> entName = new HashMap<String, Object>();
                    entName.put("name", description.getKey());
                    entName.put("description", null);

                    Map<String, Object> attribute = new HashMap<String, Object>();
                    attribute.put("entName", entName);
                    attribute.put("entValues", values);
                    attribute.put("type", "attributes");

                    attrs.add(attribute);
                }
            } else {
                for(String key :snap.getAttributes().keySet()){
                    Map<String, Object> entName = new HashMap<String, Object>();
                    entName.put("name", key);
                    entName.put("description", null);

                    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();

                    Object val = snap.getAttributes().get(key);
                    if (val != null){
                        List<Object> valueList  = null;
                        if (val != null && Collection.class.isAssignableFrom(val.getClass())) {
                            valueList = new ArrayList((Collection)val);
                        } else {
                            valueList = new ArrayList<Object>();
                            valueList.add(val);
                        }

                        for (Object v : valueList){
                            Map<String, Object> valueMap = new HashMap<String, Object>();
                            valueMap.put("value", v.toString());
                            valueMap.put("description", null);
                            valueMap.put("popup", false);
                            values.add(valueMap);
                        }
                    }

                    Map<String, Object> attribute = new HashMap<String, Object>();
                    attribute.put("entName", entName);
                    attribute.put("entValues", values);
                    attribute.put("type", "attributes");

                    attrs.add(attribute);
                }
            }
        }

        if ((null != snap.getPermissions()) && (snap.getPermissions().size() > 0)) {
            if (schema != null){

                for (Permission perm :  snap.getPermissions()) {
                    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
                    for (String right : perm.getRightsList() ) {
                       Map<String, Object> val = new HashMap<String, Object>();
                       String i18nVal = Internationalizer.getMessage(right, getLocale());
                       val.put("value", i18nVal != null ? i18nVal : right);
                       val.put("description", null);
                       val.put("popup", false);
                       values.add(val);
                    }

                    Map<String, Object> entName = new HashMap<String, Object>();
                    String i18nVal = Internationalizer.getMessage(perm.getTarget(), getLocale());
                    entName.put("name", i18nVal != null ? i18nVal :perm.getTarget());
                    String msg =  Explanator.getPermissionDescription(app, perm.getTarget(), getLocale());
                    entName.put("description", msg);

                    Map<String, Object> attribute = new HashMap<String, Object>();
                    attribute.put("entValues", values);
                    attribute.put("entName", entName);
                    attribute.put("type", "permissions");

                    attrs.add(attribute);
                }
            }
        }

        return attrs;
    }


    private CertificationAction.Status calculateStatus(Map<String,Object> map) {

        CertificationAction.Status stat = null;

        CertificationAction action = (CertificationAction) map.get("action");
        CertificationDelegation delegation = (CertificationDelegation) map.get("delegation");
        CertificationDelegation parentDel = (CertificationDelegation) map.get("parent.delegation");

        if (isDelegationActive(delegation) || isDelegationActive(parentDel)) {
            stat = CertificationAction.Status.Delegated;
        }
        else if (null != action) {
            stat = action.getStatus();

            // Use the revoke account pseudo-status for account revokes.
            if (CertificationAction.Status.Remediated.equals(stat) &&
                action.isRevokeAccount()) {
                stat = CertificationAction.Status.RevokeAccount;
            }

            // acknowledges are presented as mitigations in the UI
            if (CertificationAction.Status.Acknowledged.equals(stat)) {
                stat = CertificationAction.Status.Mitigated;
            }
        }

        return stat;
    }

    /**
     * Calculate whether the decision can be changed or not.
     */
    private boolean calculateCanChangeDecision(Map<String,Object> map)
        throws GeneralException {

        Certification cert = super.getCertification();
        Certification.Phase itemPhase = (Certification.Phase) map.get("phase");
        CertificationAction action = (CertificationAction) map.get("action");
        CertificationDelegation delegation = (CertificationDelegation) map.get("delegation");
        CertificationDelegation parentDel = (CertificationDelegation) map.get("parent.delegation");

        return !CertificationItem.isDecisionLockedByPhase(cert, action, itemPhase) &&
               !CertificationItem.isDecisionLockedByRevokes(cert, delegation, parentDel, action);
    }

    private boolean isDelegationActive(CertificationDelegation del) {
        return ((null != del) && del.isActive());
    }

    /**
     * Calculate a unique key that can identity the application if this map is
     * for an "exception" item.
     */
    private String calculateApplicationKey(Map<String,Object> map) {

        String key = "";
        EntitlementSnapshot es =
            (EntitlementSnapshot) map.get("exceptionEntitlements");
        if (null != es) {
            key = WebUtil.getApplicationKey(es.getApplication(), es.getInstance(),
                                            es.getNativeIdentity());
        }

        return key;
    }

    /**
     * Grabs the application definition from the application using the app name
     * @param map
     * @return
     */
    private String calculateApplicationDescription(Map<String,Object> map) {
        Localizer localizer = new Localizer(getContext());
        String description = null;
        String applicationName = (String)map.get("exceptionApplication");
        if(applicationName!=null) {
            description = applicationDescriptions.get(applicationName);
            if(description==null) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("name", applicationName));
                try {
                    Iterator<Object[]> apps = getContext().search(Application.class, qo, Arrays.asList("id"));
                    if(apps.hasNext()) {
                        Object[] app = apps.next();
                        String id = (String)app[0];
                        
                        /** Localize the application description **/
                        description = localizer.getLocalizedValue(id, Localizer.ATTR_DESCRIPTION, getLocale());
                        
                        applicationDescriptions.put(applicationName, description);
                    }
                } catch(GeneralException ge) {
                    log.warn("Unable to query application for name: " + applicationName);
                }
            }
        }
        return description;
    }

    /**
     * Overload this in the subclass in order to provide different values
     * for each column depending on how the subclass wants to display
     */
    @Override
    public Object getColumnValue(Map<String, Object> row, String column) {

        Object value = null;
        if(column.equals("Identity"))
        {
            value = (String)row.get("parent.identity");
        } else if(column.equals("First Name")) {
            value = (String)row.get("parent.firstname");

        }else if(column.equals("Last Name")) {
            value = (String)row.get("parent.lastname");
        }
        else if(column.equals("Description"))
        {
            try {
                String description = this.calculateDescription(row);
                value = description;
            } catch (GeneralException ge) {
                log.info("Unable to extract description due to exception: " + ge.getMessage());
            }
        }
        else if(column.equals("Application"))
        {
            value = (String)row.get("exceptionApplication");
        }
        else if(column.equals("Status"))
        {
            value = row.get("summaryStatus");
        }
        else if(column.equals("Account ID")) {
            value = (String)row.get("exceptionEntitlements.nativeIdentity");
        }
        else if(column.equals("Account Name")) {
            value = (String)row.get("exceptionEntitlements.displayName");
        }
        else if(column.equals("Changes Detected")) {
            boolean newUser = (Boolean)row.get("parent.newUser");
            if(newUser){
                Message msg = new Message(MessageKeys.CERT_NEW_USER);
                value =  msg.getLocalizedMessage(getLocale(),getUserTimeZone());
            }
            else {
                Object hasDifferences = row.get("parent.hasDifferences");
                if(hasDifferences != null &&
                        hasDifferences instanceof Boolean &&
                        (Boolean)hasDifferences){
                    Message msg = new Message(MessageKeys.YES);
                    value = msg.getLocalizedMessage(getLocale(),getUserTimeZone());
                } else {
                    Message msg = new Message(MessageKeys.NO);
                    value = msg.getLocalizedMessage(getLocale(),getUserTimeZone());
                }
            }
        } else {
            value = row.get(column);
        }

        return value;
    }


    /**
     * Sets up the implied filter for the detailed view automatically.
     * This shows up in the Identity View Grid Filter UI.
     * This is used in setting up and retaining the Detailed View.
     * @see CertificationBean#setupImpliedFilterForDetailedView()
     * @throws GeneralException
     */
    public void initializeImpliedFilterForDetailedView() throws GeneralException
    {
        getFilter().setStatus(AbstractCertificationItem.Status.Open);
        //save it in the session
        save();
    }

    public boolean isExport() {
        return isExport;
    }
    public void setExport(boolean isExport) {
        this.isExport = isExport;
    }

    public String getGridResponseJson() throws GeneralException {

        authorize(new CertificationAuthorizer(getCertification()));
        
        /**
         * Bug #8650. Unicode nulls are causing problems. Need to remove them.
         */
        return super.getGridResponseJson().replaceAll("\\\\u0000", "");
    }

    /**
     * Get the localized string for changes detected column
     * @param resultRow Row result
     * @return Localized String
     * @throws GeneralException
     */
    private String calculateChangesDetected(Map<String, Object> resultRow) throws GeneralException {
        CertificationItemChangesDetectedColumn col = new CertificationItemChangesDetectedColumn();
        col.init(new ViewEvaluationContext(this, null), null);

        return (String)col.getValue(resultRow);
    }

}




