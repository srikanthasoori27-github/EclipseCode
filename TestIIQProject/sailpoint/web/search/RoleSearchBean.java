/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.ColumnConfig;
import sailpoint.object.ExtState;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PropertyInfo;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchItem;
import sailpoint.search.SelectItemComparator;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.extjs.SessionStateBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleUtil;
import sailpoint.web.util.NavigationHistory;

/**
 * @author peter.holcomb
 *
 */
public class RoleSearchBean extends SearchBean<Bundle> 
implements NavigationHistory.Page  {

    private static final Log log = LogFactory.getLog(RoleSearchBean.class);

    private static final String GRID_STATE = "roleSearchGridState";
    private static final String ROLE_DATE = "role.date";
    private static final String ROLE_START_DATE = "role.startDate";
    private static final String ROLE_END_DATE = "role.endDate";
    private static final String ROLE_DISABLED = "disabled";
    private static final String ROLE_DESCRIPTION = "description";
    private static final String ROLE_TYPE = "type";
    private static final String ROLE_CLASSIFICATIONS_ID = "SPT_classifications.classification.id";
    private static final String ROLE_CLASSIFICATIONS_ID_DASHES = "SPT_classifications-classification-id";
    private static final String ROLE_SEARCH_ITEM_ID = "RoleSearchItem";
    private List<String> selectedRoleFields;   
    private ObjectConfig roleConfig;
    private List<String> defaultFieldList;

    /**
     * 
     */
    public RoleSearchBean() {
        super();
        super.setScope(Bundle.class);
        restore();
        roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
    }

    protected void restore() {
        super.restore();
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            selectedRoleFields = getDefaultFieldList();
        }
        else {
            selectedRoleFields = getSearchItem().getRoleFields();
        }
        setSearchType(SearchBean.ATT_SEARCH_TYPE_ROLE);
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(1);
            defaultFieldList.add("role.name");
        }
        return defaultFieldList;
    }

    protected void save() throws GeneralException{
        if(getSearchItem()==null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.Role);
        setFields();
        
        SearchInputDefinition date = getInputs().get(ROLE_DATE);
        if(date!=null)
        {
            SearchInputDefinition startDate = getInputs().get(ROLE_START_DATE);
            SearchInputDefinition endDate = getInputs().get(ROLE_END_DATE);
            SearchInputDefinition def = getInputs().get(date.getValue());
            if(def!=null){
                startDate.setPropertyName(def.getPropertyName());
                endDate.setPropertyName(def.getPropertyName());
            }
        }

        super.save();
    }
    
    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.BUSINESS_ROLE_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.BUSINESS_ROLES_LCASE);
    }

    /** If a role is selected, we need to create a ExtState object and store it on 
     * the session.
     */
    @Override
    public String select() throws GeneralException{

        ExtState stateObj = (ExtState) getSessionScope().get(
                getLoggedInUser().getId() + ":" + 
                RoleUtil.ROLE_VIEWER_STATE + ":" + 
                SessionStateBean.SESSION_STATE);
       
        if(stateObj == null) {
            stateObj = new ExtState();
            stateObj.setName(RoleUtil.ROLE_VIEWER_STATE);
        }
        String state = stateObj.getState();
        
        if (state == null)
            state = "";
        
        String updatedState = RoleUtil.updateValueInState(state, "selectedRoleId", getSelectedId());
        updatedState = RoleUtil.updateValueInState(updatedState, "selectedTopDownNodeId", getSelectedId());
        updatedState = RoleUtil.updateValueInState(updatedState, "selectedBottomUpNodeId", getSelectedId());
        updatedState = RoleUtil.updateValueInState(updatedState, "filteredNode", "");
        stateObj.setState(updatedState);
        
        getSessionScope().put(getLoggedInUser().getId() + ":" + 
                RoleUtil.ROLE_VIEWER_STATE + ":" + 
                SessionStateBean.SESSION_STATE, stateObj);
        
        return super.select();
        //return null;
    }
    
    @Override
    public Map<String, SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> argMap = super.buildInputMap();
        extendedAttributeKeys = new ArrayList<String>();
        try{           
            /**
             * Get the extended attribute fields from the ObjectConfig so that the slicer/dicer
             * can search over those as well
             */
            ObjectConfig roleConfig = getBundleConfig();
            List<ObjectAttribute> attributes = roleConfig.getExtendedAttributeList();
            if(attributes!=null) {
                for(ObjectAttribute attr : attributes) {
                    SearchInputDefinition def = new SearchInputDefinition();
                    def.setName(Util.deleteWhitespace(attr.getName()));
                    def.setHeaderKey(attr.getDisplayableName());
                    def.setInputType(InputType.Like);
                    def.setMatchMode(Filter.MatchMode.START);
                    def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_ROLE);
                    def.setPropertyName(attr.getName());
                    // this is not always true, starting in 7.1 let the persistence layer figure it out
                    // note that this means that pre 7.1 it would be REQUIRED to have a _ci index
                    // on every extended searchable attribute, otherwise Oracle would not match
                    // and DB2 would get a syntax error
                    //def.setIgnoreCase(true);
                    
                    if(attr.getType()==null) 
                        def.setPropertyType(SearchInputDefinition.PropertyType.String);
                    else if(attr.getType().equals(PropertyInfo.TYPE_BOOLEAN))
                        def.setPropertyType(SearchInputDefinition.PropertyType.Boolean);
                    else if(attr.getType().equals(PropertyInfo.TYPE_INT))
                        def.setPropertyType(SearchInputDefinition.PropertyType.Integer);
                    else if (attr.getType().equals(PropertyInfo.TYPE_IDENTITY))
                        def.setPropertyType(SearchInputDefinition.PropertyType.Identity);
                    else if(attr.getType().equals(PropertyInfo.TYPE_DATE)) {
                        def.setPropertyType(SearchInputDefinition.PropertyType.Date);
                        
                        /**Add start date and end date **/
                        SearchInputDefinition start = new SearchInputDefinition();
                        start.setName(def.getName()+".startDate");
                        start.setDescription("start_date");
                        start.setPropertyType(SearchInputDefinition.PropertyType.Date);
                        start.setPropertyName(attr.getName());
                        start.setSearchType(ATT_SEARCH_TYPE_EXTENDED_ROLE);  
                        start.setInputType(InputType.None);
                        argMap.put(start.getName(), start);
                        
                        SearchInputDefinition end = new SearchInputDefinition();
                        end.setName(def.getName()+".endDate");
                        end.setDescription("end_date");
                        end.setPropertyType(SearchInputDefinition.PropertyType.Date);
                        end.setPropertyName(attr.getName());
                        end.setSearchType(ATT_SEARCH_TYPE_EXTENDED_ROLE); 
                        end.setInputType(InputType.None);
                        argMap.put(end.getName(), end);
                    }
                        
                    def.setExtendedAttribute(true);
                    def.setDescription(Util.isNullOrEmpty(attr.getDisplayName()) ? attr.getName() : attr.getDisplayName());
                    argMap.put(def.getName(), def);
                    extendedAttributeKeys.add(def.getName());
                }
            }
        } catch (GeneralException ge) {
            log.error("Exception during buildInputMap: [" + ge.getMessage() + "]");
        }
        return argMap;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Role Search Page";
    }

    public String getNavigationString() {
        return "roleSearchResults";
    }

    @Override
    protected String getSearchItemId() {
        return ROLE_SEARCH_ITEM_ID;
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


    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedRoleFields!=null)
                selectedColumns.addAll(selectedRoleFields);
        }
        return selectedColumns;
    }
    
    /**
     * @return the extendedAttributeKeys
     */
    @Override
    public List<String> getExtendedAttributeKeys() {
        if(extendedAttributeKeys==null) {
            buildInputMap();
        }
        return extendedAttributeKeys;
    }

    protected void setFields() {
        super.setFields();
        getSearchItem().setRoleFields(selectedRoleFields);
    }
    
    /**
     * @return the dateOptions
     */
    public List<SelectItem> getDateOptions() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("role.lastCertifiedMembership", getMessage("srch_input_def_last_certified_membership")));
        list.add(new SelectItem("role.lastCertifiedComposition", getMessage("srch_input_def_last_certified_composition")));
        list.add(new SelectItem("role.lastAssigned", getMessage("srch_input_def_last_assignment")));
        return list;
    }

    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {

        Map<String,Object> map = super.convertRow(row, cols);
        List<ColumnConfig> columns = getColumns();
        for(ColumnConfig column : columns) {    
            SearchInputDefinition def = getInputs().get(column.getProperty());
            
            /** If we have the list of applications in the list, run a special query to retrieve them **/
            if(column.getProperty().equals(ROLE_DISABLED)) {
                boolean disabled = (Boolean) map.get(column.getProperty());

                map.put(column.getProperty(), disabled);
            }

            if(column.getProperty().equals(ROLE_CLASSIFICATIONS_ID)) {
                String id = (String) map.get("id");
                if (Util.isNotNullOrEmpty(id)) {
                    List<String> displayableNames = new ClassificationService(getContext()).getClassificationNames(Bundle.class, id);
                    map.put(ROLE_CLASSIFICATIONS_ID_DASHES, Util.listToCsv(displayableNames));
                }
            }
        }
        return map;
    }
    
    @Override
    public Object convertColumn(String name, Object value) {
        Object convertedVal = value;
        
        if (name.equals(ROLE_TYPE)) {
            String rawType = (String) value;
            RoleTypeDefinition typeDef = roleConfig.getRoleType(rawType);
            if (typeDef != null)
                convertedVal = typeDef.getDisplayableName();
        }
        
        return convertedVal;
    }
    
    //** Overriding to prevent sorting and add renderer on description field
    @Override
    public List<ColumnConfig> getColumns() {
        List<ColumnConfig> columns = super.getColumns();
        if(columns!=null) {
            for(ColumnConfig column : columns) {
                String property = column.getProperty();
                if(property.equals(ROLE_DESCRIPTION)) {
                    column.setSortable(false);
                    column.setRenderer("SailPoint.Analyze.Role.renderDescription");
                } else if (property.equals(ROLE_DISABLED)) {
                    column.setRenderer(getDisabledColumnRenderer());
                } else if (property.equals(ROLE_CLASSIFICATIONS_ID)) {
                    // Do not allow sorting on classification column
                    column.setSortable(false);
                }
            }
        }
        
        return columns;
    }

    /**
     * @return the roleFields
     */
    public List<SelectItem> getRoleFieldList() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        // Use a set to avoid any duplicates found in extended attributes
        Set<SelectItem> set = new TreeSet<SelectItem>(new SelectItemComparator(getLocale()));
        // Why isnt this using SearchInputDefinition?
        set.add(new SelectItem("role.name", getMessage("name")));
        set.add(new SelectItem("role.displayName", getMessage("display_name")));
        set.add(new SelectItem("role.owner", getMessage("owner")));
        set.add(new SelectItem("role.description", getMessage("description")));
        set.add(getStatusSelectItem());
        set.add(new SelectItem("role.riskScoreWeight", getMessage("srch_input_def_risk_score_weight")));
        set.add(new SelectItem("role.type", getMessage("type")));
        set.add(new SelectItem("role.entitlementCount", getMessage("srch_input_def_entitlement_count")));
        set.add(new SelectItem("role.assignedCount", getMessage("srch_input_def_assigned_count")));
        set.add(new SelectItem("role.detectedCount", getMessage("srch_input_def_detected_count")));
        set.add(new SelectItem("role.lastCertifiedMembership", getMessage("srch_input_def_last_certified_membership")));
        set.add(new SelectItem("role.lastCertifiedComposition", getMessage("srch_input_def_last_certified_composition")));
        set.add(new SelectItem("role.lastAssigned", getMessage("srch_input_def_last_assignment")));
        set.add(new SelectItem("role.associatedToRole", getMessage("srch_input_def_associated")));
        //Update this with new Classification model?
        set.add(new SelectItem("role.classification", getMessage("ui_classifications")));
        
        /** Load the extended attributes of the bundle **/
        try {
            ObjectConfig roleConfig = getBundleConfig();
            List<ObjectAttribute> attributes = roleConfig.getExtendedAttributeList();
            if(attributes!=null) {
                for(ObjectAttribute attr : attributes) {
                    set.add(new SelectItem(attr.getName(), attr.getDisplayableName(getLocale())));
                }
            }
        } catch (GeneralException ge) {
            log.error("Unable to get extended identity attributes for displaying on the advanced search page. " + ge.getMessage());
        }

        list.addAll(set);
        return list;
    }

    /**
     * @return the searchType
     */
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_ROLE;
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
        allowableTypes.add(ATT_SEARCH_TYPE_EXTENDED_ROLE);
        allowableTypes.add(ATT_SEARCH_TYPE_ROLE);
        return allowableTypes;
    }

    public List<String> getSelectedRoleFields() {
        return selectedRoleFields;
    }

    public void setSelectedRoleFields(List<String> selectedRoleFields) {
        this.selectedRoleFields = selectedRoleFields;
    }

    public String getGridStateName() {
        return GRID_STATE;
    }

    /** Returns the path to the form to edit a report that was saved from this query **/
    @Override
    public String getFormPath() {
        return "/analyze/role/roleSearch.jsf";
    }

    protected String getDisabledColumnRenderer() {
        return "SailPoint.grid.Util.renderBooleanNot";
    }

    protected SelectItem getStatusSelectItem() {
        return new SelectItem("role.status", getMessage("status"));
    }
}
