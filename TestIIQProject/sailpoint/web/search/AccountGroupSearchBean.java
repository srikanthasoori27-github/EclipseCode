/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.FullTextifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.reporting.JasperExecutor;
import sailpoint.reporting.SearchReport;
import sailpoint.search.SelectItemComparator;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.analyze.SearchUtil;
import sailpoint.web.group.EntitlementCatalogListBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author peter.holcomb
 *
 */
public class AccountGroupSearchBean extends FullTextSearchBean<ManagedAttribute>
implements NavigationHistory.Page  {

    private static final Log log = LogFactory.getLog(AccountGroupSearchBean.class);

    private static final String GRID_STATE = "accountGroupSearchGridState";
    private static final String APPLICATION_PROPERTY = "application.id";
    private static final String APPLICATION_NAME = "application.name";
    private static final String CLASSIFICATIONS_ID = "SPT_classifications.classification.id";
    private static final String CLASSIFICATIONS_ID_DASHES = "SPT_classifications-classification-id";
    public static final String ATT_IDT_SEARCH_MA_PREFIX = "ManagedAttribute.";
    public static final String ATT_IDT_SEARCH_MA_HTML_PREFIX = "ManagedAttribute_";
    private List<String> defaultFieldList;

    private List<String> selectedAccountGroupFields;
    private Map<String,Object> extendedAttributes;
    private Map<String, SearchInputDefinition> inputs;
    private String _keyWord;
    protected Boolean _useLuceneForSearch;
    private FullTextifier _fullTextifier;

    /**
     * 
     */
    public AccountGroupSearchBean() {
        super();
        super.setScope(ManagedAttribute.class);
        restore();
    }

    protected void restore() {
        super.restore();
        if(getSearchItem() == null) {
            setSearchItem(new SearchItem());
            selectedAccountGroupFields = getDefaultFieldList();
        }
        else {
            selectedAccountGroupFields = getSearchItem().getAccountGroupFields();
        }
        setSearchType(SearchBean.ATT_SEARCH_TYPE_ACCOUNT_GROUP);
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(1);
            defaultFieldList.add("accountGroup.nativeIdentity");
        }
        return defaultFieldList;
    }

    protected void save() throws GeneralException{
        if(getSearchItem()==null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.AccountGroup);
        setFields();

        super.save();
    }
    
    /**
     * Method that stores the query on the user's preferences object when the user choses to
     * remember the query.  Extremely similar to bugzilla's "remember this query as".
     *
     * @return Jsf navigation string
     */
    public String saveQueryActionForIdentitySearch() {
        SearchItem searchItem = (SearchItem) getSessionScope().get(getSearchItemId());
        searchItem.setName(getSearchItemName());
        searchItem.setDescription(getSearchItemDescription());
        
        try {
            if(searchItem != null &&  searchItem.getName() != null) {
                List<SearchItem> savedSearches = SearchUtil.getAllMySearchItems(this);

                if(savedSearches==null)
                    savedSearches = new ArrayList<SearchItem>();

                for(Iterator<SearchItem> searchItemIter = savedSearches.iterator(); searchItemIter.hasNext(); ) {
                    SearchItem item = searchItemIter.next();
                    if(item.getName().equals(getSearchItemName())) {
                        searchItemIter.remove();
                    }
                }

                searchItem.setTypeValue(SearchItem.Type.Identity.name());
                
                /** Mark the search as converted and store the new filters
                 * in the list of converted filters
                 */
                searchItem.setConverted(true);
                searchItem.setConvertedFilters(convertToIdentitySearch());
                
                savedSearches.add(searchItem);

                getLoggedInUser().setSavedSearches(savedSearches);
                SearchUtil.saveMyUser(this, getCurrentUser());
            }
        } catch (Exception e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    new Message(Message.Type.Error, e));
            log.error("Exception: [" + e.getMessage() + "]");
        }
        addMessageToSession(new Message(Message.Type.Info, MessageKeys.SEARCH_SAVED, getSearchItemName())
        , null);
        return "rememberIdentitySearchItem";
    }
    
    /** When the user elects to save the search for use on the identity slicer/dicer,
     * we need to convert the filters that apply to account group so that they apply
     * to the identity object.
     * @return
     * @throws GeneralException
     */
    public List<Filter> convertToIdentitySearch() throws GeneralException{
        List<Filter> convertedFilters = null;
        
        List<Filter> originalFilters = getSearchItem().getFilters();
        
        log.info("Original Filters: " + originalFilters);
        
        if(originalFilters!=null && !originalFilters.isEmpty()) {
            convertedFilters = new ArrayList<Filter>();
            for(Filter filter : originalFilters) {
                Filter newFilter = Filter.clone(filter);
                if(newFilter instanceof LeafFilter) {
                    LeafFilter leaf = (LeafFilter)newFilter;
                    String property = leaf.getProperty();
                    
                    /** Prepend with ManagedAttribute and add to converted list **/
                    leaf.setProperty("ManagedAttribute."+property);
                    convertedFilters.add(leaf);
                }
            }
            /** Add necessary joins **/
            convertedFilters.add(0,Filter.join("LinkExternalAttribute.value", "ManagedAttribute.displayableName"));
            convertedFilters.add(0,Filter.join("links.id", "LinkExternalAttribute.objectId"));
            
        }
        /* For Debug
        QueryOptions qo = new QueryOptions();
        for(Filter filter : convertedFilters) {
            qo.add(filter);
        }
        
        log.warn("Count: " + getContext().countObjects(Identity.class, qo));
        */
        log.info("Converted Filters: " + convertedFilters);
        return convertedFilters;
        
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        ops.setDistinct(true);
        ops.setScopeResults(true);
        return ops;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Account Group Search Page";
    }

    public String getNavigationString() {
        return "accountGroupSearchResults";
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
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedAccountGroupFields!=null)
                selectedColumns.addAll(selectedAccountGroupFields);
        }
        return selectedColumns;
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "displayableName";
    }

    protected void setFields() {
        super.setFields();
        getSearchItem().setAccountGroupFields(selectedAccountGroupFields);
    }

    /**
     * @return the roleFields
     */
    public List<SelectItem> getAccountGroupFieldList() {
        List<SearchInputDefinition> definitions = getAllowableInputDefinitions();
        List<SelectItem> list = new ArrayList<SelectItem>();
        // Use a set to avoid any duplicates found in extended attributes
        Set<SelectItem> set = new TreeSet<SelectItem>(new SelectItemComparator(getLocale()));

        if (definitions != null && !definitions.isEmpty()) {
            for (SearchInputDefinition definition : definitions) {
                if (!definition.isExcludeDisplayFields()) {
                    set.add(new SelectItem(definition.getName(), getMessage(definition.getHeaderKey())));
                }
            }
        } else {
            // Hardcode some defaults if we were unable to get the definitions
            set.add(new SelectItem("accountGroup.application", getMessage("application")));
            set.add(new SelectItem("accountGroup.name", getMessage("attribute")));
            set.add(new SelectItem("accountGroup.value", getMessage("srch_input_def_value")));
            set.add(new SelectItem("accountGroup.owner", getMessage("owner")));
        }

        try {
            // Load Extended Managed Attributes
            ObjectConfig maConfig = getEntitlementConfig();
            if(maConfig != null) {
                List<ObjectAttribute> attributes = maConfig.getExtendedAttributeList();
                if(attributes != null) {
                    for(ObjectAttribute attr : attributes) {
                        set.add(new SelectItem(attr.getName(), attr.getDisplayableName(getLocale())));
                    }
                }
            }
        }
        catch(GeneralException ignore) {
            log.error("Exception during getAccountGroupFieldList", ignore);
        }

        // 'convert' to a List return type
        list.addAll(set);

        return list;
    }

    /**
     * @return the searchType
     */
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_ACCOUNT_GROUP;
    }


    public String getKeyWord() { return this._keyWord; }

    public void setKeyWord(String word) { this._keyWord = word; }
    
    /** A flag to tell the UI that a user can take the search criteria that they've
     * entered and 'save' it to be used as a search on the identity search.
     * @return
     */
    @Override
    public boolean isSupportsConversionToIdentity() {
        return true;
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = new ArrayList<String>();
        allowableTypes.add(ATT_SEARCH_TYPE_ACCOUNT_GROUP);
        allowableTypes.add(ATT_SEARCH_TYPE_EXTENDED_MANAGED_ATTRIBUTE);
        return allowableTypes;
    }
    
    /**
     * Action taken when a user selects an account group in the results grid
     */
    @Override
    public String select() throws GeneralException {
        super.select();
        return "editAccountGroup";
    }

    public List<String> getSelectedAccountGroupFields() {
        return selectedAccountGroupFields;
    }

    public void setSelectedAccountGroupFields(List<String> selectedAccountGroupFields) {
        this.selectedAccountGroupFields = selectedAccountGroupFields;
    }

    public String getGridStateName() {
        return GRID_STATE;
    }
    
    /**
     * Replace the application column with a name instead of an id
     * 
     * @param columnNames List of column names
     * 
     * @return List of ColumnConfigs generated from the given column names
     */
    @Override
    public List<ColumnConfig> buildColumnConfigs(List<String> columnNames) {

        // First add all the extended attribute column names
        List<String> exToAdd = new ArrayList<String>();
        List<String> exKeys = getExtendedAttributeKeys();
        for(String colName : columnNames) {
            for(String key : exKeys) {
                if(key.contains(colName)) {
                    exToAdd.add(key);
                    break;
                }
            }
        }
        columnNames.addAll(exToAdd);

        // call the super to get the column configs (with the extended attribute columns)
        List<ColumnConfig> columnConfigs = super.buildColumnConfigs(columnNames);
        if (columnConfigs != null && !columnConfigs.isEmpty()) {
            for (ColumnConfig columnConfig : columnConfigs) {
                if (columnConfig.getProperty() != null && columnConfig.getProperty().equals(APPLICATION_PROPERTY)) {
                    columnConfig.setProperty("application.name");
                } else if (Util.nullSafeEq(columnConfig.getProperty(), CLASSIFICATIONS_ID)) {
                    // Do not allow sorting on classification column
                    columnConfig.setSortable(false);
                }
            }
        }
        
        return columnConfigs;
    }

    // Overriding from SearchBean so we can convert application id into application name.
    @Override
    protected Attributes buildReportArgs(String renderType, String searchType) {
        Attributes args = this.buildReportAttributes();

        // Convert application.id into human readable application.name
        List<SearchInputDefinition> definitionList = args.getList(SearchReport.ARG_ADV_SEARCH_REPORT_DEFINITIONS);
        if (definitionList != null && !definitionList.isEmpty()) {
            for (SearchInputDefinition definition : definitionList) {
                if (definition.getPropertyName() != null && definition.getPropertyName().equals(APPLICATION_PROPERTY)) {
                    definition.setPropertyName(APPLICATION_NAME);
                }
            }
        }

        args.put(JasperExecutor.OP_LOCALE, getLocale().toString());
        args.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());
        args.put(JasperExecutor.OP_RENDER_TYPE, renderType);
        return args;
    }

    /** Returns the path to the form to edit a report that was saved from this query **/
    @Override
    public String getFormPath() {
        return "/analyze/accountGroup/accountGroupSearch.jsf";
    }
    
    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.ENTITLEMENTS_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.ENTITLEMENTS_LCASE);
    }

    public String getKeywordHelpMsg() {
        return this.getKeywordHelpMsg(MessageKeys.ENTITLEMENTS_LCASE);
    }

    @Override
    public Map<String, SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> argMap = super.buildInputMap();
        extendedAttributeKeys = new ArrayList<String>();
        extendedAttributes = new HashMap<String,Object>();

        List<String> disabledAttributeSuggests = new ArrayList<String>();
        try {
            disabledAttributeSuggests = getUIConfig().getList(DISABLED_SUGGEST_ATTRIBUTES);
        }
        catch(GeneralException ge) {
            log.error("Unable to load UIConfig due to exception: " + ge.getMessage());
        }

        try {
            // managed attributes
            ObjectConfig maConfig = getEntitlementConfig();
            if(maConfig != null) {
                if(maConfig.getExtendedAttributeList() != null) {
                    for(ObjectAttribute attr : maConfig.getExtendedAttributeList()) {

                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(SearchInputDefinition.PropertyType.Date)) {
                            continue;
                        }

                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(ATT_IDT_SEARCH_MA_PREFIX + attr.getName());

                        if(disabledAttributeSuggests.contains(attr.getName())) {
                            def.setSuggestType(SearchInputDefinition.SUGGEST_TYPE_NONE);
                        }
                        def.setInputType(SearchInputDefinition.InputType.Like);
                        def.setMatchMode(Filter.MatchMode.START);
                        def.setHeaderKey(attr.getDisplayableName());
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_MANAGED_ATTRIBUTE);
                        def.setPropertyName(attr.getName());
                        def.setPropertyType(attr.getPropertyType());
                        def.setExtendedAttribute(true);
                        // this is not always true, starting in 7.1 let the persistence layer figure it out
                        // note that this means that pre 7.1 it would be REQUIRED to have a _ci index
                        // on every extended searchable attribute, otherwise Oracle would not match
                        // and DB2 would get a syntax error
                        //def.setIgnoreCase(true);
                        def.setDescription(attr.getDisplayableName(getLocale()));

                        argMap.put(ATT_IDT_SEARCH_MA_HTML_PREFIX + attr.getName(), def);
                        extendedAttributeKeys.add(ATT_IDT_SEARCH_MA_HTML_PREFIX + attr.getName());
                        extendedAttributes.put(ATT_IDT_SEARCH_MA_HTML_PREFIX + attr.getName(), attr);
                    }
                }
            }

        } catch (GeneralException ge) {
            log.error("Exception during buildInputMap: [" + ge.getMessage() + "]");
        }

        // Restore any exiting search definitions
        SearchItem sItem = getSearchItem();
        if(sItem != null) {
            List<SearchInputDefinition> defs = sItem.getInputDefinitions();
            if(defs != null) {
                for(SearchInputDefinition d : defs) {
                    if(d.getName().startsWith(ATT_IDT_SEARCH_MA_PREFIX)) {
                        String name = d.getName();
                        name = name.substring(name.indexOf(ATT_IDT_SEARCH_MA_PREFIX) + ATT_IDT_SEARCH_MA_PREFIX.length());
                        argMap.put(ATT_IDT_SEARCH_MA_HTML_PREFIX + name, d);
                    }
                }
            }
        }

        return argMap;
    }

    public Map<String, Object> getExtendedAttributes() {
        return extendedAttributes;
    }

    public void setExtendedAttributes(Map<String, Object> extendedAttributes) {
        this.extendedAttributes = extendedAttributes;
    }

    /**
     * @return the extendedAttributeKeys
     */
    @Override
    public List<String> getExtendedAttributeKeys() {
        if(extendedAttributeKeys == null) {
            buildInputMap();
        }
        return extendedAttributeKeys;
    }

    /**
     * @param extendedAttributeKeys the extendedAttributeKeys to set
     */
    public void setExtendedAttributeKeys(List<String> extendedAttributeKeys) {
        this.extendedAttributeKeys = extendedAttributeKeys;
    }

    /**
     * Called by convertRow for each column value to give the class an 
     * opportunity to customize the value before returning it to the UI
     * for display.
     * 
     * @param name the column name
     * @param value the column value
     * @return the modified value
     */
    @Override
    public Object convertColumn(String name, Object value) {
       
        // bug 25655 - Overloaded BaseListBean method to do handle cases 
        // where an extended attribute type of Identity is used to search 
        // the entitlement catalog. We need to return a UI-friendly name
        //  instead of an id. If we can't find the identity then use the 
        //  results as is.
        if (name.startsWith(ATT_IDT_SEARCH_MA_PREFIX)) {
            String htmlSearchName = name.replaceFirst(ATT_IDT_SEARCH_MA_PREFIX, ATT_IDT_SEARCH_MA_HTML_PREFIX);

            if (getExtendedAttributeKeys().contains(htmlSearchName)) {
                ObjectAttribute searchAttr = (ObjectAttribute)getExtendedAttributes().get(htmlSearchName);

                if (searchAttr != null && SearchInputDefinition.PropertyType.Identity.equals(searchAttr.getPropertyType())) {
                    try {
                        Identity identity = getContext().getObjectById(Identity.class, (String)value);
                        if (identity != null) {
                            return identity.getDisplayableName();
                        }
                    } catch (GeneralException e) {
                        log.debug("Exception while searching for identity in convertColumn: [" + e.getMessage() + "]");
                    }
                }
            }
        }

        return super.convertColumn(name, value);
    }

    @Override
    public int getCount() throws GeneralException {
        int count = 0;

        if (useLuceneForSearch()) {
            List rows = getRows();
            count = rows.size();
        } else {
            count = super.getCount();
        }

        return count;
    }

    /**
     * Retrieves all Classification displayable names for each MA, if classification needs to be returned.
     * The list query returns at most one Classification displayable name, 
     * even there are more classifications associated with the MA.
     * 
     */
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {
        Map<String,Object> map = super.convertRow(row, cols);

        for (ColumnConfig column : Util.safeIterable(getColumns())) {
            if(column.getProperty().equals(CLASSIFICATIONS_ID)) {
                String id = (String) map.get("id");
                if (Util.isNotNullOrEmpty(id)) {
                    List<String> displayableNames = new ClassificationService(getContext()).getClassificationNames(ManagedAttribute.class, id);
                    map.put(CLASSIFICATIONS_ID_DASHES, Util.listToCsv(displayableNames));
                }
            }
        }
        return map;
    }
    
    public boolean isUseLuceneForSearch() throws GeneralException {
        if (_useLuceneForSearch == null) {
            isLuceneEnabled();
        }
        return _useLuceneForSearch.booleanValue();
    }

    public boolean useLuceneForSearch() throws GeneralException {

        //Lucene will return emptySet if no keyWord present
        return isLuceneEnabled() && Util.isNotNullOrEmpty(_keyWord);
    }

    public boolean isLuceneEnabled() throws GeneralException {
        _useLuceneForSearch = false;

        // jsl - we decided not to use this but leaving the framework in place in case we want it
        // later, commenting out the check of the system config just in case
        /*
        SailPointContext context = getContext();
        Configuration config = context.getConfiguration();
        _useLuceneForSearch = getFullTextifier().isSearchEnabled() && config.getBoolean(Configuration.ENTITLEMENT_FULLTEXT);
        */
        
        return _useLuceneForSearch;
    }

    public List<Map<String, Object>> getRowsLucene() throws GeneralException {

        List<Map<String,Object>> result = null;

        // TODO: Scoping

        FullTextifier ft = getFullTextifier();

        List<String> terms = new ArrayList<String>();
        if (Util.isNotNullOrEmpty(_keyWord)) {
            terms.add(_keyWord);
        }

        QueryOptions ops = getQueryOptions();

        FullTextifier.SearchResult ftResult = ft.search(terms, ops);
        result = ftResult.rows;

        // UserAccessSearcher has an "enhancement" phase here
        fixRows(result);

        return result;
    }

    public static String FULL_TEXT_INDEX_NAME = "EntitlementSearch";

    protected FullTextifier getFullTextifier() throws GeneralException {
        if (_fullTextifier == null) {
            SailPointContext context = getContext();
            Configuration config = context.getConfiguration();

            FullTextIndex index = context.getObjectByName(FullTextIndex.class, FULL_TEXT_INDEX_NAME);
            if (index == null) {
                throw new GeneralException("Missing FullTextIndex object for " + FULL_TEXT_INDEX_NAME);
            }

            // why is this set in the constructor rahter than passed in through
            // QueryOptions?

            int max = config.getInt(Configuration.ENTITLEMENT_FULLTEXT_MAX_RESULT);
            if (max <= 0) {
                max = EntitlementCatalogListBean.DEFAULT_FULLTEXT_MAX_RESULT;
            }
            _fullTextifier = new FullTextifier(context, index, max);
        }

        return _fullTextifier;
    }

    /**
     * Post process the rows that came back from Lucene.  This does the same
     * thing as BaseListBean.convertRow but we're dealing with a different
     * source model.
     *
     * Currently leaving whatever Lucene returns that isn't in the column list
     * in the Map rather than making more garbage since our list is going
     * to be larger.
     */
    private void fixRows(List<Map<String,Object>> rows) {

        List<ColumnConfig> columns = getColumns();

        if (rows != null) {
            for (Map<String,Object> row : rows) {
                for (ColumnConfig col : columns) {

                    String prop = col.getProperty();
                    Object value = row.get(col.getProperty());

                    // TODO: BaseListBean allows the values to be message keys
                    // and will localize them, skip that since it can't apply to anything
                    // in the entitlement catalog, right?

                    //Call ConvertColumn
                    convertColumn(prop, value);

                    // name transformation for the grid
                    String gridprop = col.getDataIndex();
                    if (gridprop != null && !gridprop.equals(prop)) {
                        row.put(gridprop, value);
                        row.remove(prop);
                    }
                }
            }
        }
    }

    /**
     * AccountGroupSearchBean override of SearchBean.buildReportAttributes.
     * This method is based on SearchUtil.buildReportAttributes with small adjustments.
     * We need to include inputs that are prefixed with ATT_IDT_SEARCH_MA_HTML_PREFIX.
     * This must be an override so that reports saved for AccountGroupSearchBean are correct.
     * SearchBean.saveAsReportAction calls buildReportAttributes.
     */
    @SuppressWarnings("unchecked")
    @Override
    Attributes buildReportAttributes(){

        List<String> columnNames = this.getSelectedColumns();
        List<String> supplimentalCols = this.getSupplimentalColumns();
        if (supplimentalCols != null)
            columnNames.addAll(supplimentalCols);
        List<SearchInputDefinition> definitions = new ArrayList<SearchInputDefinition>();
        Map<String, SearchInputDefinition> inputs = this.getInputs();
        for(String column : columnNames) {
            // Remove the "id" column from the list
            if(!"id".equals(column)){
                SearchInputDefinition input = inputs.get(column);
                if (input == null)
                    input = inputs.get(ATT_IDT_SEARCH_MA_HTML_PREFIX + column);
                if(input != null) {
                    log.debug("Adding Input: " + input.getName());
                    definitions.add(input);
                }
            }
        }
        
        Attributes args = new Attributes();
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_TYPE, getSearchType());
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_DEFINITIONS, definitions);
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_FILTERS, this.getFilter());
        return args;
    }

}
