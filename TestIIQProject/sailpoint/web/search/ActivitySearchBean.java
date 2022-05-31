/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApplicationActivity;
import sailpoint.object.Category;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.object.TimePeriod;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.group.GroupDefinitionListBean;
import sailpoint.web.group.GroupFactoryListBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class ActivitySearchBean extends SearchBean<ApplicationActivity>
    implements Serializable {
    
    private static final long serialVersionUID = 3087778928758318978L;
    private static final Log log = LogFactory.getLog(ActivitySearchBean.class);  
    public static final String ATT_ACT_SEARCH_INCLUDE_ACTIVITY = "activityInclude";    
    public static final String ATT_IDT_SEARCH_APPLICATION_STRING_NAME = "applicationString";
    public static final String ATT_ACT_SEARCH_TIMEPERIOD_DEF = "timePeriods";
    private static final String SEEDED_IDENTITY_INPUTS = "SEEDED_IDENTITY_INPUTS";
    
    private static final String ACTIVITY_SEARCH_COL_ACTION = "action";
    private static final String ACTIVITY_SEARCH_COL_RESULT = "result";
    
    private static final String GRID_STATE = "activitySearchGridState";
   
    private List<String> selectedActivityFields;
    private List<TimePeriod> timePeriods;
    private Map<TimePeriod, Boolean> timePeriodSelections;
    /** Is the activity search a category or a target search? */
    private String targetMode;
    /** Is the activity search using Time Periods or start and end dates? */
    private String dateMode;
    /** Is the activity search using IPOP or Identities? */
    private String identityMode;
    private List<String> defaultFieldList;
    
	public ActivitySearchBean () {
        super();
        super.setScope(ApplicationActivity.class);
        restore();
        initTimePeriods();
        initTargetMode();
        initDateMode();
        initIdentityMode();
    } 
    
    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();
        qo.setScopeResults(true);
        qo.setDistinct(true);
        return qo;
    }

    protected void restore() {
        super.restore();
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            /** Add Name by default **/
            selectedActivityFields = getDefaultFieldList();
        }
        else {
            selectedActivityFields = getSearchItem().getActivityFields();
        }
        setSearchType(SearchBean.ATT_SEARCH_TYPE_ACT);
        buildActivityInputMap();
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(1);
            defaultFieldList.add("sourceApplication");
        }
        return defaultFieldList;
    }
    
    protected void save() throws GeneralException{
        if(getSearchItem() == null) 
            setSearchItem(new SearchItem());
        
        // Special-case categories.  Specifically, we need to replace the name of the category with the list 
        // of targets to which it points before running our query.  I apologize for this ugliness, but I'm 
        // starting to run way behind on my 1.5 bugs...  I'll try to come up with a more elegant solution later
        // --Bernie Margolis
        final SearchInputDefinition categoryDef = (SearchInputDefinition) getInputs().get("category");
        final List<String> targets;
        final String categoryName;
        
        if (categoryDef != null) {
            categoryName = (String)categoryDef.getValue();
            if (categoryName != null) {
                Category cat = getContext().getObjectByName(Category.class, categoryName);
                if (cat != null) {
                    targets = cat.getTargets();
                    // Replace the input's name-based value with the actual targets
                    categoryDef.setValue(targets);
                } 
            }
        } else {
            categoryName = null;
        }
        
        //More magic that has to happen.  The things we do under pressure... -- Bernie Margolis
        populateTimePeriodsInput();
        
        getSearchItem().setType(SearchItem.Type.Activity);
        setFields();
        
        
        super.save();
        
        // Now that the query has been built, let's restore the category to the name so that 
        // the UI doesn't blow chunks.  This is more of that special-casing that I added for
        // categories.
        // -- Bernie Margolis
        if (categoryDef != null && categoryName != null) {
            categoryDef.setValue(categoryName);
        } 
    }
    
    /**
     * Simply return the localized string value of the action and result enum values.
     * 
     * @param name The column name
     * @param value The column value
     * @return The processed value
     */
    @Override
    public Object convertColumn(String name, Object value) {
        if (name.equals(ACTIVITY_SEARCH_COL_ACTION) && value != null) {
        	ApplicationActivity.Action action = (ApplicationActivity.Action) value;
        	
        	return WebUtil.localizeMessage(action.getMessageKey());
        } else if (name.equals(ACTIVITY_SEARCH_COL_RESULT) && value != null) {
        	ApplicationActivity.Result result = (ApplicationActivity.Result) value;
        	
        	return WebUtil.localizeMessage(result.getMessageKey());
        }
        
        return super.convertColumn(name, value);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////
    
    @SuppressWarnings("unchecked")
    public String searchActivitiesForIdentitiesAction() {
        String identityIds = (String) getRequestParam().get("editForm:selectedIdentityIds");
        String allSelected = (String) getRequestParam().get("editForm:allSelected");
        
        if (allSelected !=null && "true".equals(allSelected)) {
            List<String> ids = new ArrayList<String>();
            IdentitySearchBean identSearchBean = new IdentitySearchBean();
            try { 
                if(identSearchBean.getRows()!=null) {
                    for (Map<String, Object> idents : identSearchBean.getRows()) {
                        ids.add((String)idents.get("id"));
                    }
                }             //Remove any ids that were excluded
                if (identityIds != null && identityIds.trim().length() > 0) {
                	List<String> excludedList = Util.csvToList(identityIds);
                	for(String excludedId : excludedList) {
                		ids.remove(excludedId);
                	}
                }
            } catch (GeneralException ge) {
                log.warn("Unable to get rows from identity search bean");
            }
            
            getSessionScope().put(SEEDED_IDENTITY_INPUTS, ids);
        } else if (identityIds != null && identityIds.trim().length() > 0){
            getSessionScope().put(SEEDED_IDENTITY_INPUTS, Util.csvToList(identityIds));
        }
        
        return "searchForActivitiesOnIdentities";
    }
    
    @Override
    public String clearSearchItem() {
        updateDateMode();
        return super.clearSearchItem();
    }
        
    ////////////////////////////////////////////////////////////////////////////
    //
    // Helpers
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @SuppressWarnings("unchecked")
    private void addSeededIdentities(Map<String, SearchInputDefinition> inputDefs) 
        throws GeneralException {
        List<String> seededIdentities = (List<String>) getSessionScope().get(SEEDED_IDENTITY_INPUTS);
        
        if (seededIdentities != null && !seededIdentities.isEmpty()) {    
            log.debug("Adding seededIdentities: " + seededIdentities.toString());
            // Update the input
            SearchInputDefinition identityInputs = (SearchInputDefinition) inputDefs.get("identity");
            if (identityInputs != null) {
                if (identityInputs.getObjectListValue() != null)
                    log.debug("Old identities: " + identityInputs.getObjectListValue().toString());
                identityInputs.setObjectListValue(seededIdentities);
                log.debug("New identities: " + identityInputs.getObjectListValue().toString());
            } else {
                identityInputs = new SearchInputDefinition();
                identityInputs.setObjectListValue(seededIdentities);
                inputDefs.put("identity", identityInputs);
                log.debug("New identities: " + identityInputs.getObjectListValue().toString());
            }
            
            /** Clear out the Ipop **/
            SearchInputDefinition ipopInput = (SearchInputDefinition) getInputs().get(ATT_ACT_SEARCH_IPOP_NAME);
            if (ipopInput != null) {
            	ipopInput.setValue(null);
            }
            
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, SearchInputDefinition> buildActivityInputMap() {
        Map<String, SearchInputDefinition> argMap = getInputs();
        try{          
            addSeededIdentities(argMap);
        } catch (GeneralException ge) {
            log.error("Exception during buildInputMap: [" + ge.getMessage() + "]");
        }
        
        return argMap;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @return the activityFields
     */
    public List<SelectItem> getActivityFieldList() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("sourceApplication", getMessage(MessageKeys.APPLICATION)));
        list.add(new SelectItem("timeStamp", getMessage(MessageKeys.TIMESTAMP)));        
        list.add(new SelectItem("action", getMessage(MessageKeys.ACTION)));
        list.add(new SelectItem("result", getMessage(MessageKeys.RESULT)));
        list.add(new SelectItem("identityName", getMessage(MessageKeys.IDENTITYNAME)));
        list.add(new SelectItem("target", getMessage(MessageKeys.TARGET)));
        list.add(new SelectItem("info", getMessage(MessageKeys.INFO)));

        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }
    
    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedActivityFields!=null)
                selectedColumns.addAll(selectedActivityFields);
        }
        return selectedColumns;
    }
    
    /**
     * @return the searchType
     */
    @Override
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_ACT;
    }
    
    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_ACT);
        return allowableTypes;
    }

    /**
     * @return the selectedActivityFields
     */
    public List<String> getSelectedActivityFields() {
        return selectedActivityFields;
    }

    /**
     * @param selectedActivityFields the selectedActivityFields to set
     */
    public void setSelectedActivityFields(List<String> selectedActivityFields) {
        this.selectedActivityFields = selectedActivityFields;
    }
    
    @Override
    protected void setFields() {
    	super.setFields();
        getSearchItem().setActivityFields(selectedActivityFields);
    }
    
    public String getTargetMode() {
        return targetMode;
    }

    public void setTargetMode(String targetMode) {        
        this.targetMode = targetMode;
    }

    public String getDateMode() {
        return dateMode;
    }

    public void setDateMode(String dateMode) {        
        this.dateMode = dateMode;
    }
    
    public String getIdentityMode() {
		return identityMode;
	}

	public void setIdentityMode(String identityMode) {
		this.identityMode = identityMode;
	}

    public List<SelectItem> getCurrentTargets() {
        SearchInputDefinition currentCategory = (SearchInputDefinition) getInputs().get("category");
        List<SelectItem> currentTargets = new ArrayList<SelectItem>();

        if (currentCategory != null) {
            String currentCategoryName = (String) currentCategory.getValue();
            if (currentCategoryName != null && !currentCategoryName.equals("")) {
                try {
                    Category currentCategoryObj = getContext().getObjectByName(Category.class, currentCategoryName);

                    if (currentCategoryObj != null && currentCategoryObj.getTargets()!=null) {
                        for (String target : currentCategoryObj.getTargets()) {
                            currentTargets.add(new SelectItem(target));
                        }
                    } else {
                        currentTargets.add(new SelectItem("", getMessage(MessageKeys.NO_TARGETS_SELECT_ITEM)));
                    }
                } catch (GeneralException e) {
                    log.error("The Categories cannot be fetched from the data store at this time.", e);
                }
            }
        }

        return currentTargets;
    }

    public String updateTargetMode() {
        if ("Category".equals(targetMode)) {
            getInputs().remove("target");
        } else if ("Target".equals(targetMode)) {
            getInputs().remove("category");            
        }

        try {
            save();
        } catch (GeneralException e) {
            log.error("Could not persist settings while updating the target mode ", e);
        }
        return "updateTargetMode";
    }

    public String updateTargetList() {
        try {
            save();
        } catch (GeneralException e) {
            log.error("Could not persist settings while updating the target mode ", e);
        }
        return "updateTargetList";
    }

    private void initTargetMode() {
        if (getInputs() == null) {
            setInputs(buildInputMap());
        }

        SearchInputDefinition targetDef = (SearchInputDefinition) getInputs().get("target");
        if (targetDef.getValue() != null && targetDef.getValue().toString().length() > 0) {
            targetMode = "Target";
        } else {
            targetMode = "Category";
        }

        // Defer to the request parameters (workaround for weird a4j behavior)
        Map requestParams = getFacesContext().getExternalContext().getRequestParameterMap();
        String catOrTargetSelection = (String) requestParams.get("editForm:catOrTargetSelection");

        if (catOrTargetSelection != null && catOrTargetSelection.length() > 0) {
            targetMode = catOrTargetSelection;
        }         

    }
    
    private void initIdentityMode() {
        if (getInputs() == null) {
            setInputs(buildInputMap());
        }

        SearchInputDefinition ipopDef = (SearchInputDefinition) getInputs().get("ipop");
        if (ipopDef.getValue() != null && ipopDef.getValue().toString().length() > 0) {
            identityMode = ATT_ACT_SEARCH_IPOP_NAME;
        } else {
            identityMode = ATT_ACT_SEARCH_IDENTITY_NAME;
        }

        // Defer to the request parameters (workaround for weird a4j behavior)
        Map requestParams = getFacesContext().getExternalContext().getRequestParameterMap();
        String ipopOrIdentitySelection = (String) requestParams.get("editForm:ipopOrIdentitySelection");

        if (ipopOrIdentitySelection != null && ipopOrIdentitySelection.length() > 0) {
            identityMode = ipopOrIdentitySelection;
        }
    }

    private void initDateMode() {
        if (getInputs() == null) {
            setInputs(buildInputMap());
        }

        SearchInputDefinition startDateDef = (SearchInputDefinition) getInputs().get("activityStartDate");
        SearchInputDefinition endDateDef = (SearchInputDefinition) getInputs().get("activityEndDate");
        if ((startDateDef.getValue() != null && startDateDef.getInputType() != InputType.None) || 
                (endDateDef.getValue() != null && endDateDef.getInputType() != InputType.None)) {
            dateMode = "activityDate";
        } else {
            dateMode = "timePeriod";
        }

        // Defer to the request parameters (workaround for weird a4j behavior)
        Map requestParams = getFacesContext().getExternalContext().getRequestParameterMap();
        String dateOrTimePeriodSelection = (String) requestParams.get("editForm:dateOrTimePeriodSelection");

        if (dateOrTimePeriodSelection != null && dateOrTimePeriodSelection.length() > 0) {
            dateMode = dateOrTimePeriodSelection;
        }         
    }
    
    public String updateIdentityMode() {
        if (ATT_ACT_SEARCH_IDENTITY_NAME.equals(identityMode)) {
            SearchInputDefinition ipopInput = (SearchInputDefinition) getInputs().get(ATT_ACT_SEARCH_IPOP_NAME);
            if (ipopInput != null) {
            	ipopInput.setValue(null);
            }
            
            
        } else if (ATT_ACT_SEARCH_IPOP_NAME.equals(identityMode)) {
            SearchInputDefinition identity = (SearchInputDefinition) getInputs().get(ATT_ACT_SEARCH_IDENTITY_NAME);
            if (identity != null)
            	identity.setValue(null);
        }

        try {
            save();
        } catch (GeneralException e) {
            log.error("Could not persist settings while updating the input mode ", e);
        }
        return "updateIdentityMode";
    }

    public String updateDateMode() {
        if ("activityDate".equals(dateMode)) {
            SearchInputDefinition timePeriodsInput = (SearchInputDefinition) getInputs().get("timePeriods");
            if (timePeriodsInput != null)
                timePeriodsInput.setInputType(InputType.None);
            // The activity start and end dates are reactivated by the browser, so we don't 
            // have to do it here
        } else if ("timePeriod".equals(dateMode)) {
            SearchInputDefinition activityStartDate = (SearchInputDefinition) getInputs().get("activityStartDate");
            if (activityStartDate != null)
                activityStartDate.setInputType(InputType.None);

            SearchInputDefinition activityEndDate = (SearchInputDefinition) getInputs().get("activityEndDate");
            if (activityEndDate != null)
                activityEndDate.setInputType(InputType.None);

            SearchInputDefinition timePeriodsInput = (SearchInputDefinition) getInputs().get("timePeriods");
            if (timePeriodsInput != null)
                timePeriodsInput.setInputType(InputType.ContainsAll);
        }

        try {
            save();
        } catch (GeneralException e) {
            log.error("Could not persist settings while updating the target mode ", e);
        }
        return "updateDateMode";
    }

    public List<TimePeriod> getTimePeriods() {
        return timePeriods;
    }

    public void setTimePeriods(List<TimePeriod> timePeriods) {
        this.timePeriods = timePeriods;
    }

    public Map<TimePeriod, Boolean> getTimePeriodSelections() {
        return timePeriodSelections;
    }

    public void setTimePeriodSelections(Map<TimePeriod, Boolean> timePeriodSelections) {
        this.timePeriodSelections = timePeriodSelections;
    }

    @SuppressWarnings("unchecked")
    public void initTimePeriods() {
        if (getInputs() == null) {
            setInputs(buildInputMap());
        }

        if (timePeriodSelections == null) {
            timePeriodSelections = new HashMap<TimePeriod, Boolean>();
        }

        List<TimePeriod> allTimePeriods = new ArrayList<TimePeriod>();

        try {
            allTimePeriods.addAll(getContext().getObjects(TimePeriod.class));
        } catch (GeneralException e) {
            log.error("The time periods could not be retrieved from the data store", e);
        }

        List<TimePeriod> tpList = new ArrayList<TimePeriod>();

        SearchInputDefinition timePeriodDef = (SearchInputDefinition) getInputs().get("timePeriods");
        if (timePeriodDef != null && timePeriodDef.getInputType() != InputType.None) {
            List<TimePeriod> existingTimePeriods = timePeriodDef.getObjectListValue();
            if (existingTimePeriods != null) {
                tpList.addAll(existingTimePeriods);
            }
        }

        if (allTimePeriods.isEmpty()) {
            for (TimePeriod timePeriod : tpList) {
                timePeriodSelections.put(timePeriod, true);
            }
        } else {
            if (timePeriods == null) {
                timePeriods = new ArrayList<TimePeriod>();
                timePeriods.addAll(allTimePeriods);
            }
            for (TimePeriod timePeriod : allTimePeriods) {
                if (tpList.contains(timePeriod)) {
                    timePeriodSelections.put(timePeriod, true);
                } else {
                    timePeriodSelections.put(timePeriod, false);
                }
            }
        }

    }
    void populateTimePeriodsInput() {
        log.debug("[populateTimePeriods]");
        SearchInputDefinition timePeriodsDef = (SearchInputDefinition) getInputs().get(ATT_ACT_SEARCH_TIMEPERIOD_DEF);

        if (timePeriodsDef != null) {
            List<TimePeriod> selectedTimePeriods = new ArrayList<TimePeriod>();

            for (TimePeriod tp : timePeriodSelections.keySet()) {
                Boolean isSelected = timePeriodSelections.get(tp);

                if (isSelected != null && isSelected.booleanValue()) {
                    selectedTimePeriods.add(tp);
                }
            }

            if (selectedTimePeriods.isEmpty()) {
                timePeriodsDef.setInputType(InputType.None);
            } else {
                timePeriodsDef.setValue(selectedTimePeriods);
                timePeriodsDef.setInputType(InputType.ContainsAll);
            }
            
            log.debug("[populateTimePeriods] " + selectedTimePeriods);
        }
        
    }
    
    /** 
     * The IPOP options for an activity search.  Allows the user to search using any
     * IPOPs in the system to filter the activity results.
     */
    public List<SelectItem> getIpopOptions() {
        GroupDefinitionListBean groupList = new GroupDefinitionListBean();
        List<SelectItem> selectItems = new ArrayList<SelectItem>();

        try {
            SelectItem[] selectArray = groupList.getDefinitionOptions(GroupFactoryListBean.ATT_UNGROUPED, false);
            if(selectArray!=null && selectArray.length>0) {
                for(int i=0; i < selectArray.length; i++) {
                    selectItems.add(selectArray[i]);
                }
            } 
        } catch (GeneralException ge) {
            log.error("Unable to populate IPOP options list. " + ge.getMessage());
        }
        selectItems.add(0,new SelectItem("", getMessage(MessageKeys.SELECT_IPOP)));
        return selectItems;
    }

    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.ACTIVITY_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.ACTIVITIES_LCASE);
    }
}
