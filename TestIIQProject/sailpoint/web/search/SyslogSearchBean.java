/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.faces.model.SelectItem;

import sailpoint.api.logging.SyslogAppender;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.object.SyslogEvent;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author derry.cannon
 *
 */
public class SyslogSearchBean extends SearchBean<SyslogEvent> {

    //private static final Log log = LogFactory.getLog(SyslogSearchBean.class);
    
    private static final String SYSLOG_DATE = "created";
    private static final String SYSLOG_QUICK_KEY = "quickKey";
    private static final String SYSLOG_EVENT_LEVEL = "eventLevel";
    private static final String SYSLOG_MESSAGE = "message";
    private static final String SYSLOG_STACKTRACE = "stacktrace";

    private static final String GRID_STATE = "syslogSearchGridState";
    
    private List<String> selectedSyslogFields;
    private List<SelectItem> syslogFields;
    private List<String> defaultFieldList;

    private int purgeAge;

    public SyslogSearchBean () {
        super();
        super.setScope(SyslogEvent.class);
        restore();

        // no scoping on syslog events
        setDisableOwnerScoping(true);
        
        try {
            Configuration config = getContext().getConfiguration();
            purgeAge = config.getInt(SyslogAppender.SYSLOG_PURGE_AGE);
        } catch (GeneralException e) {
            purgeAge = SyslogAppender.DEFAULT_PURGE_AGE;
        }   
    }  

    public String clearSearchItem() {
        super.clearSearchItem();
        
        this.restore();
        
        return "searchResults";
    }
    
    protected void restore() {
        setSearchType(SearchBean.ATT_SEARCH_TYPE_SYSLOG);
        super.restore();
        
        // add the default syslog search fields
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            selectedSyslogFields = getDefaultFieldList();
        }
        else {
            selectedSyslogFields = getSearchItem().getSyslogFields();
        }
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(4);
            defaultFieldList.add(SYSLOG_DATE);
            defaultFieldList.add(SYSLOG_QUICK_KEY);
            defaultFieldList.add(SYSLOG_EVENT_LEVEL);
            defaultFieldList.add(SYSLOG_MESSAGE);
        }
        return defaultFieldList;
    }

    
    protected void save() throws GeneralException{
        if(getSearchItem() == null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.Syslog);
        setFields();
        formatInputs();
        super.save();
    }

    
    /**
     * The quickKey value needs to be properly formatted before searching
     */
    protected void formatInputs() {
        for(Iterator<String> keyIter = getInputs().keySet().iterator(); keyIter.hasNext(); ) {
            String key = keyIter.next();
            if (key.equals(SYSLOG_QUICK_KEY)) {
                SearchInputDefinition def = getInputs().get(key);
                def.setValue(Util.padID((String)def.getValue()));
            }
        }
    }
    
    
    /**
     * The stacktrace column is excluded from the display and therefore will 
     * never be visible for the user to select.  However, there's not much 
     * point in exporting syslog data without the stacktrace, so we need to
     * add it to the list of columns for exporting.
     */
    public void exportToCSV() {
        List<ColumnConfig> columns = getColumns();
        
        List<ColumnConfig> supplimentals = 
            buildColumnConfigs(getSupplimentalColumns());
        
        if (!supplimentals.isEmpty()) 
            columns.addAll(supplimentals);

        super.exportToCSV(columns);
    }   


    protected void setFields() {
    	super.setFields();
        getSearchItem().setSyslogFields(selectedSyslogFields);
    }

    /**
     * @return the syslogFields
     */
    public List<SelectItem> getSyslogFieldList() {

        syslogFields = new ArrayList<SelectItem>();

        // crap, the input definitions have two fields for a before/after
        // comparison on the creation date, but there is only one return field
        syslogFields.add(new SelectItem("created", getMessage(MessageKeys.LABEL_DATE)));

        // this will cache _inputDefinitions, should do this in the constructor!
        getInputs();
        List<SearchInputDefinition> definitions = getInputDefinitions();
        if (definitions != null) {
            for (SearchInputDefinition def : definitions) {
                if(def.isExcludeDisplayFields()) 
                    continue;

                // have to filter out the two before/after input definitions
                if (SearchItem.Type.Syslog.name().equals(def.getSearchType()) &&
                        !def.getPropertyName().equals("created")) {
                    syslogFields.add(new SelectItem(def.getName(),
                                                    getMessage(def.getDescription())));
                }
            }
        }

        // Sort the list based on localized labels
        Collections.sort(syslogFields, new SelectItemComparator(getLocale()));

        return syslogFields;
    }

    /**
     * Add any extra columns needed for a projection search.
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        
        if (projectionColumns == null) {
            super.getProjectionColumns();
            projectionColumns.add(SYSLOG_STACKTRACE);
        }
        
        return projectionColumns;
    }
    
    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedSyslogFields!=null)
                selectedColumns.addAll(selectedSyslogFields);
        }
        
        return selectedColumns;
    }
    
    /**
     * Get any columns that are needed in the CSV/PDF export that are not
     * returned by getColumns().
     */
    @SuppressWarnings("unchecked")
    public List<String> getSupplimentalColumns() {
        if (supplimentalColumns == null) {
            supplimentalColumns = Util.stringToList(SYSLOG_STACKTRACE);
        }
        
        return supplimentalColumns;
    }

    @Override
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_SYSLOG;
    }
    
    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return SYSLOG_DATE;
    }

    @Override
    public String getDefaultSortOrder() throws GeneralException {
        return "DESC";
    }

    
    /**
     * Called by convertRow for each column value.
     * This is a hook for subclasses to process the value before display.
     * The initial use was to localize WorkItem types.
     */
    @Override
    public Object convertColumn(String name, Object value) {
        if (name.equals(SYSLOG_QUICK_KEY)) {
            return Util.stripLeadingChar((String)value, '0');
        } else {
            return value;
        }
    }
    
    
    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_SYSLOG);
        return allowableTypes;
    }

    /**
     * @return the selectedSyslogFields
     */
    public List<String> getSelectedSyslogFields() {
        return selectedSyslogFields;
    }

    /**
     * @param selectedSyslogFields the selectedSyslogFields to set
     */
    @SuppressWarnings("unchecked")
    public void setSelectedSyslogFields(List<String> selectedSyslogFields) {
        SearchItem item = (SearchItem) getSessionScope().get(getSearchItemId());
        if (item == null)
            item = getSearchItem();
        item.setSyslogFields(selectedSyslogFields);
        this.selectedSyslogFields = selectedSyslogFields;
        getSessionScope().put(getSearchItemId(), item);
    }
    
    public int getPurgeAge() {
        return purgeAge;
    }
    
    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.SYSLOG_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.SYSLOGS_LCASE);
    }
    
}
