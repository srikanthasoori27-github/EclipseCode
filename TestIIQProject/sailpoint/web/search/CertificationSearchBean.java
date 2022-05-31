/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.certification.CertificationBean;
import sailpoint.web.certification.CertificationPreferencesBean;
import sailpoint.web.group.GroupFilterBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class CertificationSearchBean extends SearchBean<Certification> 
implements NavigationHistory.Page  {

    private static final Log log = LogFactory.getLog(CertificationSearchBean.class);

    private static final String CERTIFICATION_PREFIX = "certification.";
    private static final String CERTIFICATION_TYPE = "certification.type";
    private static final String CERTIFICATION_DATE = "certification.date";
    private static final String CERTIFICATION_START_DATE = "certification.startDate";
    private static final String CERTIFICATION_END_DATE = "certification.endDate";
    private static final String CERTIFICATION_ACCOUNT_GROUP = "certification.accountGroup";
    private static final String CERTIFICATION_ROLE = "certification.role";
    private static final String CERTIFICATION_APPLICATION = "certification.applicationId";
    private static final String CERTIFICATION_ACCOUNT_GROUP_APPLICATION = "certification.accountGroupApplicationId";
    private static final String CERTIFICATION_MANAGER = "certification.manager";
    private static final String CERTIFICATION_MEMBER = "certification.member";
    private static final String CERTIFICATION_GROUPS = "certification.groups";
    private static final String CERTIFICATION_STATUS = "certification.status";
    private static final String CERTIFICATION_SIGNED_BY = "signOffHistory.signerDisplayName";
    private static final String CERTIFICATION_E_SIGNED = "electronicallySigned";
    private static final String CERTIFICATION_TOTAL_ENTITIES = "statistics.totalEntities";
    private static final String CERTIFICATION_COMPLETED_ENTITIES = "statistics.completedEntities";
    private static final String CERTIFICATION_TOTAL_ITEMS = "statistics.totalItems";
    private static final String CERTIFICATION_COMPLETED_ITEMS = "statistics.completedItems";
    private static final String CERTIFICATION_SIGNED = "certification.signed";
    
    private static final String CALCULATED_ACCOUNT_GROUP = SearchBean.CALCULATED_COLUMN_PREFIX + "accountGroups";
    private static final String CALCULATED_ROLE = SearchBean.CALCULATED_COLUMN_PREFIX + "roles";
    private static final String CALCULATED_CERTIFIERS = SearchBean.CALCULATED_COLUMN_PREFIX + "certifiers";
    private static final String CALCULATED_CERT_GRP = SearchBean.CALCULATED_COLUMN_PREFIX + "certificationGroup";
    private static final String PERCENT_COMPLETE = "statistics.itemPercentComplete";
    private static final String NUM_CERTIFIERS = "numCertifiers";
    private static final String IS_STAGED = "isStaged";

    private static final String CALCULATED_TAGS = CALCULATED_COLUMN_PREFIX + "tags.id";
    
    private static final String GRID_STATE = "certificationSearchGridState";
    private static String GROUP_FILTER = "certificationSearchGroupFilter";
    
    private List<String> selectedCertificationFields;
    private List<String> selectedSearchTypeFields;
    
    private String certificationMemberName;
    
    private GroupFilterBean groupFilter;

    private static final String LABEL = "label_";
    
    private static final String CERTIFICATION_COL_TYPE = "type";
    private static final String CERTIFICATION_COL_PHASE = "phase";
    private static final String CERTIFICATION_LIMIT_REASSIGNMENTS = "limitReassignments";

    private List<String> defaultFieldList;

    /**
     * 
     */
    public CertificationSearchBean() {
        super();
        super.setScope(Certification.class);
        restore();
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();
        qo.setScopeResults(true);

        // Let certifiers see their own certs.
        List<String> certifiers = new ArrayList<String>();
        certifiers.add(super.getLoggedInUserName());
                List<String> wgs =  getLoggedInUsersWorkgroupNames();
                if ( Util.size(wgs) > 0 ) {
                    certifiers.addAll(wgs);
                }
        qo.extendScope(Filter.containsAll("certifiers", certifiers));

        return qo;
    }
    
    
    /** We are overriding so we can stick in lists of values for account groups and roles
     * in case they've chosen to display those lists
     */
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {
        
        Map<String,Object> map = super.convertRow(row, cols);
        if(getSelectedColumns().contains(CALCULATED_ACCOUNT_GROUP)) {
            loadAccountGroupsForCert(map);
        }
        if(getSelectedColumns().contains(CALCULATED_ROLE)) {
            loadRolesForCert(map);
        }

        loadCertifiersForCert(map);

        if(getSelectedColumns().contains(CALCULATED_CERT_GRP)) {
            loadGroupForCert(map);
        }
        if(getSelectedColumns().contains(CALCULATED_TAGS)) {
            loadTagsForCert(map);
        }
        if(map.containsKey(PERCENT_COMPLETE)) {
            String percent = getMessage(MessageKeys.PERCENT_COMPLETE_WITH_COUNT, 
                    map.get(PERCENT_COMPLETE), map.get(CERTIFICATION_COMPLETED_ITEMS), map.get(CERTIFICATION_TOTAL_ITEMS));
            map.put(PERCENT_COMPLETE, percent);
        }

        loadReassignmentDecisionForCert(map);

        loadIsCertStaged(map);

        return map;
    }

    /**
     * Modify the JSON results metadata by adding other fields
     */
    public void modifyMetadata(JSONObject metaData) {
        if (metaData.has("fields")) {
            try {
                JSONArray fields = metaData.getJSONArray("fields");
                
                JSONObject field = new JSONObject();
                field.put("name", NUM_CERTIFIERS);
                fields.put(field);

                field = new JSONObject();
                field.put("name", CERTIFICATION_LIMIT_REASSIGNMENTS);
                fields.put(field);
                
                //bug 21595 - add the certification signed date to the list of fields
                // returned to the UI if it hasn't already been selected
                if(!getSelectedColumns().contains(CERTIFICATION_SIGNED)) {
                    field = new JSONObject();
                    field.put("name", "signed");
                    fields.put(field);
                }

                // bug 23422 - Add boolean indicating whether certification is staged
                // to the list of fields.
                field = new JSONObject();
                field.put("name", IS_STAGED);
                fields.put(field);
            }
            catch (JSONException e) {
                log.error("Unable to find [fields] in JSON metadata", e);
            }
        }
    }

    /**
     * Simply return the localized string value of the type and phase enum values.
     * 
     * @param name The column name
     * @param value The column value
     * @return The processed value
     */
    @Override
    public Object convertColumn(String name, Object value) {
        if (name.equals(CERTIFICATION_COL_TYPE) && value != null) {
        	Certification.Type type = (Certification.Type) value;
        	
        	return WebUtil.localizeMessage(type.getMessageKey());
        } else if (name.equals(CERTIFICATION_COL_PHASE) && value != null) {
        	Certification.Phase phase = (Certification.Phase) value;
        	
        	return WebUtil.localizeMessage(phase.getMessageKey());
        }
        
        return super.convertColumn(name, value);
    }
    
    /** Since we can't fetch the list of roles during the main search, we need to fetch them 
     * for each row in the query.
     * @param map
     */
    private void loadRolesForCert(Map<String, Object> map) {
        String id = (String)map.get("id");
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("certification.id", id));
        qo.setDistinct(true);
        /** Limit to 100 by default **/
        qo.setResultLimit(100);
        List<String> props = Arrays.asList(new String[] {"targetName"});
        try {
            Iterator<Object[]> rows = getContext().search(CertificationEntity.class, qo, props);
            List<String> roles = new ArrayList<String>();
            while(rows.hasNext()) {
                roles.add((String)rows.next()[0]);
            }
            if(!roles.isEmpty()) {
                map.put(CALCULATED_ROLE, Util.listToCsv(roles));
            }
        } catch (GeneralException ge) {
            log.warn("Unable to load Roles for Cert.  Exception: " + ge.getMessage());
        }
    }

    /** We have to load the cert because there is no way to get the list of certifiers
     * out of the database using context.search() because it is store so weirdly **/
    private void loadCertifiersForCert(Map<String, Object> map) {
        String id = (String)map.get("id");
        try {
            Certification cert = getContext().getObjectById(Certification.class, id);

            List<String> certifiers = cert.getCertifiers();
            
            // If the list of certifers was selected in the search filter then add it
            // to the results.
            if (!certifiers.isEmpty() && getSelectedColumns().contains(CALCULATED_CERTIFIERS)) {
                map.put(CALCULATED_CERTIFIERS, Util.listToCsv(cert.getCertifiers()));
            }
            
            // Add the number of certifiers so we can disable the forward button if necessary
            map.put(NUM_CERTIFIERS, certifiers.size());
            
            getContext().decache();
        } catch (GeneralException ge) {
            if (log.isWarnEnabled())
                log.warn("Unable to load Certifiers for Cert.  Exception: " + ge.getMessage(), ge);
        }
    }

    private void loadGroupForCert(Map<String, Object> map) {
        String id = (String)map.get("id");
        try {
            Certification cert = getContext().getObjectById(Certification.class, id);

            if (!Util.isNullOrEmpty(cert.getCertificationDefinitionId())) {
                // Bug 7907: Since we only need name here, no need to load whole CertificationGroup object (perf hit)
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("definition.id", cert.getCertificationDefinitionId()));
                List<String> props = Arrays.asList(new String[] {"name"});

                Iterator<Object[]> rows = getContext().search(CertificationGroup.class, qo, props);
                if (rows.hasNext() == true){
                    map.put(CALCULATED_CERT_GRP, rows.next()[0]);
                }
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled())
                log.warn("Unable to load certification groups for Cert.  Exception: " + ge.getMessage(), ge);
        }
    }

    /**
     * Load the tags off the Certification since projection queries can't handle
     * lists.
     */
    private void loadTagsForCert(Map<String, Object> map) {
        String id = (String)map.get("id");
        try {
            Certification cert = getContext().getObjectById(Certification.class, id);
            
            if(!cert.getTags().isEmpty()) {
                map.put(CALCULATED_TAGS, WebUtil.objectListToNameString(cert.getTags()));
            }
            else {
                // Since CALCULATED_TAGS includes a '.' it will blow up JSON if it's not
                // converted in makeJsonSafeKeys.  BUT since some rows may not have a tag,
                // we need to include this on all rows to ensure it will get converted.
                map.put(CALCULATED_TAGS, null);
            }

            getContext().decache();
        } catch (GeneralException ge) {
            log.warn("Unable to load tags for Cert.  Exception: " + ge.getMessage(), ge);
        }
    }

    /** Since we can't fetch the list of account groups during the main search, we need to fetch them 
     * for each row in the query.
     * @param map
     */
    private void loadAccountGroupsForCert(Map<String, Object> map) {
        String id = (String)map.get("id");
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("certification.id", id));
        qo.setDistinct(true);
        /** Limit to 100 by default **/
        qo.setResultLimit(100);
        List<String> props = Arrays.asList(new String[] {"accountGroup"});
        try {
            Iterator<Object[]> rows = getContext().search(CertificationEntity.class, qo, props);
            List<String> accountGroups = new ArrayList<String>();
            while(rows.hasNext()) {
                accountGroups.add((String)rows.next()[0]);
            }
            if(!accountGroups.isEmpty()) {
                map.put(CALCULATED_ACCOUNT_GROUP, Util.listToCsv(accountGroups));
            }
        } catch (GeneralException ge) {
            log.warn("Unable to load Account Groups for Cert.  Exception: " + ge.getMessage());
        }
    }

    /** Since we can't get the reassignment limit to check whether or not reassignment is allowed during
     * the main search, we need to get it for each row.
     */
    private void loadReassignmentDecisionForCert(Map<String, Object> map) {
        String id = (String) map.get("id");
        try {
            if (Util.isNotNullOrEmpty(id)) {
                SailPointContext context = getContext();
                if (context != null) {
                    Certification cert = context.getObjectById(Certification.class, id);
                    if (cert != null) {
                        map.put(CERTIFICATION_LIMIT_REASSIGNMENTS, cert.limitCertReassignment(context));
                    }
                }
            }
        }
        catch (GeneralException ge) {
            log.warn("Unable to load Reassignment Decision for Cert.  Exception: " + ge.getMessage());
        }
    }

    /**
     * Populate a boolean indicating whether the certification phase is staged or not.
     * @param map
     */
    private void loadIsCertStaged(Map<String, Object> map) {
        // bug 23422 - Add boolean to indicate if the certification is staged. This 
        // will be used in the UI to determine if the certification can be forwarded.
        String id = (String)map.get("id");
        try {
            if (Util.isNotNullOrEmpty(id)) {
                Certification cert = getContext().getObjectById(Certification.class, id);
                if (null != cert && null != cert.getPhase()) {
                    map.put(IS_STAGED, cert.getPhase() == Certification.Phase.Staged ? true : false);
                }
            }
        } catch (GeneralException ge) {
            if (log.isWarnEnabled())
                log.warn("Unable to load Phase for Cert.  Exception: " + ge.getMessage(), ge);
        }
    }

    protected void restore() {
        super.restore();
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            selectedCertificationFields = getDefaultFieldList();
        }
        else {
            selectedCertificationFields = getSearchItem().getCertificationFields();
            selectedSearchTypeFields = getSearchItem().getSearchTypeFields();
        }
        setSearchType(SearchBean.ATT_SEARCH_TYPE_CERTIFICATION);
        restoreGroupFilter();
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(1);
            defaultFieldList.add("certification.name");
        }
        return defaultFieldList;
    }
    
    public String clearSearchItem() {
        groupFilter = null;
        super.getSessionScope().remove(GROUP_FILTER);

        // iiqetn-3072 Also need to clear the group filter on the input map
        // otherwise it won't be cleared for the following search 
        SearchInputDefinition groupsInputDef = getInputs().get(CERTIFICATION_GROUPS);
        if(groupsInputDef != null) {
            groupsInputDef.setValue(null);
        }
        
        return super.clearSearchItem();
    }

    protected void save() throws GeneralException{
        if(getSearchItem()==null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.Certification);
        setFields();
        clearTypeDefinitions();

        SearchInputDefinition date = getInputs().get(CERTIFICATION_DATE);
        if(date!=null)
        {
            SearchInputDefinition startDate = getInputs().get(CERTIFICATION_START_DATE);
            SearchInputDefinition endDate = getInputs().get(CERTIFICATION_END_DATE);
            startDate.setPropertyName((String)date.getValue());
            endDate.setPropertyName((String)date.getValue());
        }
        
        saveGroupFilter();
        
        super.save();
    }
    
    public String loadSearchItem() {
        String returnStr = super.loadSearchItem();
        SearchInputDefinition groupsInputDef = getInputs().get(CERTIFICATION_GROUPS);
        if(groupsInputDef.getValue()!=null && !groupsInputDef.getValue().equals("")) {
            this.groupFilter = new GroupFilterBean(this, GROUP_FILTER, (String)groupsInputDef.getValue());
        }
        
        return returnStr;
    }

    /** If the user has entered something into a type attribute field, but has then 
     * chosen a different type, we want to cleanse the type field.
     */
    private void clearTypeDefinitions() {
        String type = (String)getInputs().get(CERTIFICATION_TYPE).getValue();
        boolean nullField = (type==null || type.equals(""));
        /** If it's not an account group cert, clear the account group field **/
        if(nullField || (!type.equals(Certification.Type.AccountGroupMembership.name())
                && !type.equals(Certification.Type.AccountGroupPermissions.name()))) {
            SearchInputDefinition def = getInputs().get(CERTIFICATION_ACCOUNT_GROUP);
            def.setValue(null);
            SearchInputDefinition def2 = getInputs().get(CERTIFICATION_ACCOUNT_GROUP_APPLICATION);
            def2.setValue(null);
        }

        /** If it's not a manager cert, clear the manager field **/
        if(nullField || !type.equals(Certification.Type.Manager.name())) {
            SearchInputDefinition def = getInputs().get(CERTIFICATION_MANAGER);
            def.setValue(null);
        }
        
        /** If it's not a manager cert, clear the manager field **/
        if(nullField || !shouldShowApplication(type)) {
            SearchInputDefinition def = getInputs().get(CERTIFICATION_APPLICATION);
            def.setValue(null);
        }

        /** If it's not a business role cert, clear the role field **/
        if(nullField || (!type.equals(Certification.Type.BusinessRoleMembership.name())
                && !type.equals(Certification.Type.BusinessRoleComposition.name()))) {
            SearchInputDefinition def = getInputs().get(CERTIFICATION_ROLE);
            def.setValue(null);
        }
    }
    
    private boolean shouldShowApplication(String type) {
        
        return type.equals(Certification.Type.ApplicationOwner.name()) 
                    || type.equals(Certification.Type.DataOwner.name());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public String chooseCertificationSearchType() {
        return "";		
    }

    /**
     * Action handler called when a certification is selected from the list.
     * Similar to CertificationListBean.select() but this also saves the fact
     * that we're viewing the result list (not the search panel).
     */
    @SuppressWarnings("unchecked")
    public String select() throws GeneralException
    {
        String selected = super.getSelectedId();

        // This stores information so that the correct certification will be
        // displayed after redirecting to the next page.
        CertificationBean.viewCertification(FacesContext.getCurrentInstance(), selected);

        getSessionScope().put(AnalyzeControllerBean.CURRENT_CARD_PANEL,
                              AnalyzeControllerBean.CERTIFICATION_SEARCH_RESULTS);
        NavigationHistory.getInstance().saveHistory(this);

        CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(selected);
        return certPrefBean.getDefaultView();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Certification Search Page";
    }

    public String getNavigationString() {
        return "certificationSearchResults";
    }

    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns == null) {
            selectedColumns = new ArrayList<String>();
            if(selectedCertificationFields!=null)
                selectedColumns.addAll(selectedCertificationFields);
            if(selectedSearchTypeFields!=null) {
                for(String field : selectedSearchTypeFields) {
                    selectedColumns.add(field);
                }
            }
        }
        return selectedColumns;
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if(projectionColumns == null) {
            projectionColumns = super.getProjectionColumns();
            projectionColumns.add(CERTIFICATION_COMPLETED_ENTITIES);
            projectionColumns.add(CERTIFICATION_TOTAL_ENTITIES);
            projectionColumns.add(CERTIFICATION_COMPLETED_ITEMS);
            projectionColumns.add(CERTIFICATION_TOTAL_ITEMS);
            projectionColumns.add(CERTIFICATION_SIGNED_BY);
            projectionColumns.add(CERTIFICATION_E_SIGNED);

            //bug 21595 - If not already selected add the certification.signed date to 
            // help determine whether to allow the certification to be forwarded.
            if(!getSelectedColumns().contains(CERTIFICATION_SIGNED)) {
                projectionColumns.add("signed");
            }
        }
        return projectionColumns;
    }

    @Override
    public List<ColumnConfig> getColumns() {
    	List<ColumnConfig> cols = super.getColumns();
    	//Add the renderPercent renderer for percentComplete column
    	for(ColumnConfig c : cols) {
    		if(c.getDataIndex().equals("percentComplete")) {
    			c.setRenderer("renderPercent");
    		}
    			
    	}
    	return cols;
    }
    
    protected void setFields() {
        super.setFields();
        
        getSearchItem().setSearchTypeFields(selectedSearchTypeFields);
        getSearchItem().setCertificationFields(selectedCertificationFields);
    }

    /**
     * @return the certificationFields
     */
    public List<SelectItem> getCertificationFieldList() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("certification.name", getMessage("name")));
        list.add(new SelectItem(CALCULATED_CERT_GRP, getMessage("srch_input_def_cert_grp_name")));
        list.add(new SelectItem(CALCULATED_CERTIFIERS, getMessage("srch_input_def_certifier")));
        list.add(new SelectItem(CERTIFICATION_TYPE, getMessage("type")));
        list.add(new SelectItem("certification.phase", getMessage("phase")));
        list.add(new SelectItem("certification.creator", getMessage("creator")));
        list.add(new SelectItem("certification.percentComplete", getMessage("percentage_complete")));
        list.add(new SelectItem("certification.completedItems", getMessage("completed_items")));
        list.add(new SelectItem("certification.totalItems", getMessage("total_items")));
        list.add(new SelectItem("certification.signed", getMessage("date_signed")));
        list.add(new SelectItem("certification.signOffHistory.signerDisplayName", getMessage("srch_input_def_signed_by")));
        list.add(new SelectItem("certification.electronicallySigned", getMessage("srch_input_def_e_signed")));
        list.add(new SelectItem("certification.created", getMessage("date_created")));
        list.add(new SelectItem("certification.expiration", getMessage("date_expires")));
        list.add(new SelectItem("certification.finished", getMessage("date_finished")));
        list.add(new SelectItem("certification.delegatedEntities", getMessage("delegatedEntities")));
        list.add(new SelectItem(CALCULATED_TAGS, getMessage("cert_tags")));

        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }
    
    public List<SelectItem> getSearchTypeFieldList() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        
        /** Return an empty list if the search type is null **/
        if(getCertificationSearchType()==null)
            return null;
        
        if (getCertificationSearchType().equals(Certification.Type.ApplicationOwner.name())) {
            list.add(new SelectItem(CERTIFICATION_PREFIX + "application", getMessage("application")));
            return list;
        }
        else if (getCertificationSearchType().equals(Certification.Type.Manager.name())) {
            list.add(new SelectItem(CERTIFICATION_PREFIX + "manager", getMessage("manager")));
            return list;
        }
        else if (getCertificationSearchType().equals(Certification.Type.BusinessRoleComposition.name())) {
            list.add(new SelectItem(CALCULATED_ROLE, getMessage("role")));
            return list;
        }
        else if (getCertificationSearchType().equals(Certification.Type.AccountGroupPermissions.name())
                || getCertificationSearchType().equals(Certification.Type.AccountGroupMembership.name())) {
            list.add(new SelectItem(CERTIFICATION_PREFIX + "application", getMessage("application")));
            list.add(new SelectItem(CALCULATED_ACCOUNT_GROUP, getMessage("account_group")));
            return list;
        }
        return null;
    }

    /**
     * @return the dateOptions
     */
    public List<SelectItem> getDateOptions() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("created", getMessage("created")));
        list.add(new SelectItem("expiration", getMessage("expiration")));
        list.add(new SelectItem("signed", getMessage("signed")));           
        list.add(new SelectItem("finished", getMessage("finished")));
        return list;
    }
    
    /**
     * @return the stateOptions;
     */
    public List<SelectItem> getStatusOptions() {
        List<SelectItem> statusOptions = new ArrayList<SelectItem>();
        statusOptions.add(new SelectItem("all", ""));
        statusOptions.add(new SelectItem("signed", getMessage("txt_true")));
        statusOptions.add(new SelectItem("unsigned", getMessage("txt_false")));
        return statusOptions;
    }
    
    /**
     * @return the boolean esignedOptions;
     */
    public List<SelectItem> getEsignedOptions() {
        List<SelectItem> statusOptions = new ArrayList<SelectItem>();
        statusOptions.add(new SelectItem("", ""));
        statusOptions.add(new SelectItem("true", getMessage("txt_true")));
        statusOptions.add(new SelectItem("false", getMessage("txt_false")));
        return statusOptions;
    }

    public String getCertificationSearchType() {
        SearchInputDefinition input = getInputs().get(CERTIFICATION_TYPE);
        if(input!=null) {
            return (String)input.getValue();
        }
        return null;
    }

    /**
     * Retrieve the search fields label associated with the given cert type
     * @return
     */
    public String getCertificationSearchTypeLabel() {
        SearchInputDefinition input = getInputs().get(CERTIFICATION_TYPE);
        if(input!=null) {
            return LABEL + (String)input.getValue();
        }
        return null;
    }

    /**
     * @return the searchType
     */
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_CERTIFICATION;
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_CERTIFICATION);
        return allowableTypes;
    }

    
    public List<String> getSelectedCertificationFields() {
        return selectedCertificationFields;
    }

    public void setSelectedCertificationFields(List<String> selectedCertificationFields) {
        this.selectedCertificationFields = selectedCertificationFields;
    }
    
    public String getGridStateName() {
        return GRID_STATE;
    }
    
    public List<String> getSelectedSearchTypeFields() {
        return selectedSearchTypeFields;
    }

    public void setSelectedSearchTypeFields(List<String> selectedSearchTypeFields) {
        this.selectedSearchTypeFields = selectedSearchTypeFields;
    }

    /** Returns the path to the form to edit a report that was saved from this query **/
    @Override
    public String getFormPath() {
        return "/analyze/certification/certificationSearch.jsf";
    }

    /** This is a workaround for the 'Identity' suggest field on the certification search.  We
     * don't want to use convertIdentity which will cause lazily loaded exceptions when saving
     * searches (See Bug 4256) but we still need to load the display name from the identity in order
     * to see the suggest when a user hits "refine search"
     * Bug IIQENT-55 We need to the name back and not the displayName
     * @return
     */
    public String getCertificationMemberName() {
        if(certificationMemberName == null) {
            if(getInputs()!=null) {
                Object id = (getInputs().get(CERTIFICATION_MEMBER)).getValue();
                if(id!=null) {
                    try {
                        Identity identity = null;
                        if(id instanceof String) {
                            identity = getContext().getObjectById(Identity.class, (String)id);
                        } else if(id instanceof Identity) {
                            identity = (Identity)id;
                        }
                        if(identity!=null)
                            certificationMemberName = identity.getName();
                    } catch(GeneralException ge) {
                        
                    }
                }
            }
        }
        return certificationMemberName;
    }

    public void setCertificationMemberName(String certificationMemberName) {
        this.certificationMemberName = certificationMemberName;
    }

    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.CERTIFICATION_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.CERTIFICATIONS_LCASE);
    }
    
    public GroupFilterBean getGroupFilter() {
        return groupFilter;
    }

    public void setGroupFilter(GroupFilterBean groupFilter) {
        this.groupFilter = groupFilter;
        super.getSessionScope().put(GROUP_FILTER, this.groupFilter);
    }

    private void restoreGroupFilter() {
        groupFilter =
            (GroupFilterBean) super.getSessionScope().get(GROUP_FILTER);
        if(groupFilter==null) {
            this.groupFilter = new GroupFilterBean(this, GROUP_FILTER);
        }
        groupFilter.attachContext(this);
    }
    
    /**Takes the filters on the group factory/definitions from the search page and stores
    * as a string of factory:definition pairs */
    private void saveGroupFilter() {
        if (groupFilter != null) {
            SearchInputDefinition groupsInputDef = getInputs().get(CERTIFICATION_GROUPS);
            String filterString = groupFilter.toString();
            if (filterString != null && !filterString.equals("")) {
                groupsInputDef.setValue(filterString);
            } else {
                groupsInputDef.setValue(null);
            }
        }
    }
}
