/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
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

import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.GridState;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * Mining bean used by the Advance Analytics's account search tab
 *
 */
public class LinkSearchBean extends SearchBean<Link> implements NavigationHistory.Page {

    private static final Log log = LogFactory.getLog(IdentitySearchBean.class);

    public static final String ATT_LINK_SEARCH_APPLICATION_NAME = "link.application";
    public static final String ATT_LINK_SEARCH_DISPLAY_NAME = "link.displayname";
    public static final String ATT_LINK_SEARCH_OWNER = "link.owner";
    public static final String ATT_LINK_SEARCH_NATIVE_IDENTITY = "link.nativeIdentity";
    public static final String ATT_LINK_SEARCH_CREATED = "link.created";
    public static final String ATT_LINK_SEARCH_ENTITLEMENTS = "entitlements";

    private static final String GRID_STATE = "linkSearchGridState";

    private Map<String,Object> extendedAttributes;
    private List<String> selectedLinkFields;
    private List<SelectItem> linkFields;
    private List<String> defaultFieldList;

    public LinkSearchBean () {
        super();
        super.setScope(Link.class);
        restore();
    }

    protected void restore() {
        setSearchType(SearchBean.ATT_SEARCH_TYPE_LINK);
        super.restore();
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            
            // add the default link search fields
            selectedLinkFields = getDefaultFieldList();
        }
        else {
            selectedLinkFields = getSearchItem().getLinkFields();
        }
    }

    protected void save() throws GeneralException{
        if(getSearchItem() == null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.Link);
        setFields();
        super.save();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(4);
            defaultFieldList.add(ATT_LINK_SEARCH_APPLICATION_NAME);
            defaultFieldList.add(ATT_LINK_SEARCH_DISPLAY_NAME);
            defaultFieldList.add(ATT_LINK_SEARCH_CREATED);
            defaultFieldList.add(ATT_LINK_SEARCH_ENTITLEMENTS);
        }
        return defaultFieldList;
    }

    protected void setFields() {
        super.setFields();
        getSearchItem().setLinkFields(selectedLinkFields);
    }
    
    public List<String> getSelectedLinkFields() {
        return selectedLinkFields;
    }

    /**
     * @return the Link Fields
     */
    public List<SelectItem> getLinkFieldList() {
        linkFields = new ArrayList<SelectItem>();

        // Use a set to avoid any duplicates found in extended attributes
        Set<SelectItem> fieldSet = new TreeSet<SelectItem>(new SelectItemComparator(getLocale()));

        // this will cache _inputDefinitions, should do this in the constructor!
        getInputs();
        List<SearchInputDefinition> definitions = getInputDefinitions();
        if (definitions != null) {
            for (SearchInputDefinition def : definitions) {
                // have to filter out the two "created" input definitions
                // we've got a constant over in SearchItem but we
                // don't use it in the definition, why?
                if (SearchItem.Type.Link.name().equals(def.getSearchType())) {
                    fieldSet.add(new SelectItem(def.getName(), getMessage(def.getDescription())));
                }
            }
        }

        // Add extended attributes, but don't overwrite any of the above items
        try {
            /** Load the extended attributes **/
            ObjectConfig linkConfig = getLinkConfig();
            if(linkConfig != null) {
                List<ObjectAttribute> attributes = linkConfig.getExtendedAttributeList();
                if(attributes != null) {
                    for(ObjectAttribute attr : attributes) {
                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(SearchInputDefinition.PropertyType.Date)) {
                            continue;
                        }
                        fieldSet.add(new SelectItem(attr.getName(), attr.getDisplayableName(getLocale())));
                    }
                }
            }
        } catch (GeneralException ge) {
            log.error("Unable to get extended attributes for displaying on the advanced search page. " + ge.getMessage());
        }

        linkFields.addAll(fieldSet);

        return linkFields;
    }

    public Map<String, Object> getExtendedAttributes() {
        return extendedAttributes;
    }

    public void setExtendedAttributes(Map<String, Object> extendedAttributes) {
        this.extendedAttributes = extendedAttributes;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public Map<String, SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> argMap = super.buildInputMap();
        extendedAttributeKeys = new ArrayList<>();
        extendedAttributes = new HashMap<>();

        List<String> disabledAttributeSuggests = new ArrayList<>();
        try {
            disabledAttributeSuggests = getUIConfig().getList(DISABLED_SUGGEST_ATTRIBUTES);
        }
        catch(GeneralException ge) {
            log.error("Unable to load UIConfig due to exception: " + ge.getMessage());
        }

        try {
            ObjectConfig linkConfig = getLinkConfig();
            if (linkConfig != null) {
                if (linkConfig.getExtendedAttributeList() != null) {
                    for(ObjectAttribute attr : linkConfig.getExtendedAttributeList()) {

                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(SearchInputDefinition.PropertyType.Date)) {
                            continue;
                        }

                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(attr.getName());

                        if(disabledAttributeSuggests.contains(attr.getName())) {
                            def.setSuggestType(SearchInputDefinition.SUGGEST_TYPE_NONE);
                        }
                        def.setInputType(SearchInputDefinition.InputType.Like);
                        def.setMatchMode(Filter.MatchMode.START);
                        def.setHeaderKey(attr.getDisplayableName());
                        def.setSearchType(ATT_SEARCH_TYPE_LINK);
                        def.setPropertyName(attr.getName());
                        def.setPropertyType(attr.getPropertyType());
                        def.setExtendedAttribute(true);
                        // this is not always true, starting in 7.1 let the persistence layer figure it out
                        // note that this means that pre 7.1 it would be REQUIRED to have a _ci index
                        // on every extended searchable attribute, otherwise Oracle would not match
                        // and DB2 would get a syntax error
                        //def.setIgnoreCase(true);
                        def.setDescription(attr.getDisplayableName(getLocale()));

                        argMap.put(attr.getName(), def);
                        extendedAttributeKeys.add(attr.getName());
                        extendedAttributes.put(attr.getName(), attr);
                    }
                }
            }

        } catch (GeneralException ge) {
            log.error("Exception during buildInputMap: [" + ge.getMessage() + "]");
        }

        return argMap;
    }

    /**
     * @return the extendedAttributeKeys
     */
    @Override
    public List<String> getExtendedAttributeKeys() {
        if (extendedAttributeKeys == null) {
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

    /** We do special row conversion in order to populate certain columns that return lists of values **/
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {
        Map<String,Object> map = new HashMap<String,Object>(row.length);
        String entitlementString = "";

        map = super.convertRow(row, cols);

        List<ColumnConfig> columns = getColumns();
        for(ColumnConfig column : columns) {
            // Get entitlements data from attributes field of Link
            if(column.getProperty().equals(ATT_LINK_SEARCH_ENTITLEMENTS)) {
                String linkId = (String)row[cols.indexOf("id")];
                Link link = getContext().getObjectById(Link.class, linkId);
                Attributes<String,Object> entitlementsAttribute = link.getEntitlementAttributes();
                if ( entitlementsAttribute != null && !entitlementsAttribute.isEmpty()) {
                    Iterator<String> keys = entitlementsAttribute.keySet().iterator();
                    while ( keys.hasNext() ) {
                        Object entitlementObject = entitlementsAttribute.get(keys.next());
                        if(entitlementObject != null){
                            if (entitlementObject instanceof List){
                                List entitlementObjectList = (List) entitlementObject;
                                entitlementString = Util.listToCsv(entitlementObjectList);
                            }else if (entitlementObject instanceof String){
                                entitlementString = entitlementObject.toString().trim();
                            }
                        }
                    }
                }
                if (entitlementString != null){
                    map.put(column.getProperty(), entitlementString);
                }

            }
        }
        return map;
    }

    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedLinkFields!=null)
                selectedColumns.addAll(selectedLinkFields);
        }
        return selectedColumns;
    }

    @Override
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_LINK;
    }

    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "created";
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_LINK);
        return allowableTypes;
    }

    /**
     * @param selectedLinkFields the selectedLinkFields to set
     */
    @SuppressWarnings("unchecked")
    public void setSelectedLinkFields(List<String> selectedLinkFields) {
        SearchItem item = (SearchItem) getSessionScope().get(getSearchItemId());
        if (item == null)
            item = getSearchItem();
        item.setLinkFields(selectedLinkFields);
        this.selectedLinkFields = selectedLinkFields;
        getSessionScope().put(getSearchItemId(), item);
    }

    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.LINK_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.LINKS_LCASE);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPageName() {
        return "Link Search Page";
    }

    @Override
    public String getNavigationString() {
        return "linkSearchResults";
    }

    @Override
    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state;
    }

    @Override
    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
    }
}
