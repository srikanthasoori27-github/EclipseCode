/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.object.SearchItem;
import sailpoint.object.UIConfig;
import sailpoint.search.LinkFilterBuilder;
import sailpoint.search.SelectItemComparator;
import sailpoint.server.ResultScoper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Consts;
import sailpoint.web.EntitlementMiningBean;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.certification.BulkCertificationHelper;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.identity.IdentityListBean;
import sailpoint.web.identity.IdentityPagingBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.IdentityEntitlementFilterBean.IdentityEntitlementFilter;
import sailpoint.web.util.FilterConverter;
import sailpoint.web.util.FilterHelper;
import sailpoint.web.util.NavigationHistory;

/**
 * @author peter.holcomb
 *
 */
public class IdentitySearchBean extends SearchBean<Identity>
implements Serializable, NavigationHistory.Page  {
    private static final long serialVersionUID = -3251256650055285611L;

    private static final Log log = LogFactory.getLog(IdentitySearchBean.class);

    private static final String GRID_STATE = "identitySearchGridState";

    /** Mining bean used by the advanced search to show an entitlement breakdown **/
    private EntitlementMiningBean miningBean;
    private List<String> selectedIdentityFields;
    private List<String> selectedRiskFields;
    private List<Filter> bundleFilters;
    private List<Filter> assignedRoleFilters;
    private List<String> multiValuedIdentityAttributeKeys;
    private List<String> multiValuedLinkAttributeKeys;

    private Map<String,Object> extendedAttributes;

    public static final String ATT_IDT_SEARCH_LINK_PREFIX = "Link.";
    public static final String ATT_IDT_SEARCH_LINK_HTML_PREFIX = "Link_";
    public static final String ATT_IDT_SEARCH_APPLICATION_NAME = "miningApplication";
    public static final String ATT_IDT_SEARCH_ROLE_HIERARCHY = "roleHierarchy";
    public static final String ATT_IDT_SEARCH_ROLE = "businessRole";
    public static final String ATT_IDT_SEARCH_ASSIGNED_ROLE_HIERARCHY = "assignedRoleHierarchy";
    public static final String ATT_IDT_SEARCH_ASSIGNED_ROLE = "assignedRole";
    private BulkCertificationHelper bulkCertification;

    public static final String ATT_SEARCH_COL_APPLICATION_ID = "links.application.id";
    public static final String ATT_SEARCH_COL_WORKGROUP_ID = "workgroups.id";

    public static final String PROPERTY_BUNDLES_ID = "bundles.id";
    public static final String PROPERTY_ASSIGNED_ROLES_ID = "assignedRoles.id";
    public static final String PROPERTY_BUNDLES_NAME = "bundles.name";
    public static final String PROPERTY_ASSIGNED_ROLES_NAME = "assignedRoles.name";
    
    /**
     * Name of the input that will hold the serialized json of the 
     * complex filter properties, used for IdentityEntitlement
     * filtering.
     */
    public static final String ATT_IDENTITY_ENTITLEMENTS = "identityEntitlements"; 

    private static final String SESSION_ENTITLEMENT_FILTER_BEAN = "IdentityEntitlementFilterBean";

    private List<String> defaultFieldList;
    
    /**
     * Cached list of rows to be displayed.  This should be used when there are
     * projection columns.  We have to override this from base list bean because we
     * have to deal with lists of applications and business processes
     */
    List<Map<String,Object>> rows;
    
    /**
     * Bean to handle identity entitlement filters.
     */
    protected IdentityEntitlementFilterBean _entitlementFilterBean;

    /**
     * Override value for "limit" when fetching rows, since we need to get a full set when scheduling certifications
     */
    private Integer limitOverride;

    public IdentitySearchBean () {
        super();
        super.setScope(Identity.class);
        bulkCertification = new BulkCertificationHelper(getSessionScope(), getContext(), this);
    }  

    public IdentitySearchBean(boolean restore) {
        this();
        if (restore)
            restore();
    }

    protected void restore() {
        super.restore();
        // Check if it's already on the session first
        if (getSearchItem() == null) {
            setSearchItem((SearchItem) getSessionScope().get(SearchBean.ATT_SEARCH_TYPE_IDENT + SearchBean.ATT_SEARCH_ITEM));
        }
        /** Do some default behavior when the search item is null **/
        if (getSearchItem() == null) {
            setSearchItem(new SearchItem());
            selectedIdentityFields = getDefaultFieldList();
        }
        else {
            selectedIdentityFields = getSearchItem().getIdentityFields();
        }
        selectedRiskFields = getSearchItem().getRiskFields();
        setSearchType(SearchBean.ATT_SEARCH_TYPE_IDENT);
        restoreIdentityEntitlementFilter();
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(1);
            defaultFieldList.add("userName");
        }
        return defaultFieldList;
    }

    protected void save() throws GeneralException{
        if(getSearchItem()==null) 
            setSearchItem(new SearchItem());
        for(Iterator<String> keyIter = getInputs().keySet().iterator(); keyIter.hasNext(); ) {
            String key = keyIter.next();
            SearchInputDefinition def = (SearchInputDefinition)getInputs().get(key);

            if(def!=null && def.getValue()!=null && !def.getValue().toString().equals("")) {
                log.debug("Key: " + key + " Value: " + def.getValue());
                /* Check to see if this extended attribute is no longer available (if it
                 * has been removed from the identity mapping */
                if(def.isExtendedAttribute()) {
                    if(!extendedAttributeKeys.contains(key)){
                        def.setValue(null);
                        continue;
                    }
                }
            }
        }
        
        // convert the bean filters values into json so it can be handled
        // by the registered entitlement filter builder
        IdentityEntitlementFilterBean bean = getIdentityEntitlementFilter();
        if ( bean != null ) {
            List<String> jsonStrings = new ArrayList<String>();
            List<IdentityEntitlementFilter> filters =  bean.getFilters();
            if ( Util.size(filters) > 0 ) { 
                 jsonStrings = new ArrayList<String>();
                 for ( IdentityEntitlementFilter filter : filters  ) {
                     if ( filter != null ) { 
                         String json = filter.toJSON();
                         if ( json != null ) {
                             jsonStrings.add(json);
                         }
                     }
                 }
            }
            SearchInputDefinition def = getInputs().get(ATT_IDENTITY_ENTITLEMENTS);
            if ( def != null ) {                
                def.setValue(jsonStrings);
                getInputs().put(ATT_IDENTITY_ENTITLEMENTS, def);
            }                

        }
        getSearchItem().setType(SearchItem.Type.Identity);
        setFields();
        super.save();
    }

    /** If an identity is selected from the list of results, we need to get all of the ids
     * of the list of results and store them on the session so that when they go to the identity
     * page, they can page through all of the results.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public String select() throws GeneralException{
        restore();
        List<String> ids = new ArrayList<String>();
        List<String> cols = new ArrayList<String>();
        cols.add("id");

        /** Read the sort order from the grid state object and sort the list of identity ids */

        String sortCol = getGridState().getSortColumn();
        String sortOrder = getGridState().getSortOrder();

        /** For some reason, ie passes a null string as "null" so we have to check if it's literally
         * equal to "null"
         */
        try {
            // Override the result limit to force 0 so we get all of the IDs
            this.limitOverride = 0;
            QueryOptions ops = getQueryOptions();

            if(sortCol!=null && !sortCol.equals("") && !sortCol.equals("null")) {
            	sortCol = sortCol.replaceAll("\\-", ".");     // sortCol can be manager-displayname
                ops.addOrdering(0, sortCol, "ASC".equalsIgnoreCase(sortOrder));
            }		

            Iterator<Object[]> results = getContext().search(getScope(), ops, cols);
            while (results.hasNext()) {
                ids.add((String)results.next()[0]);
            }

            Map session = getSessionScope();
            session.put(IdentityPagingBean.ATT_IDENTITY_IDS, ids);
            String selectedId = getSelectedId();
            session.put(IdentityPagingBean.ATT_CURRENT_IDENTITY_ID, selectedId);
            session.put(IdentityListBean.ATT_SELECTED_ID, selectedId);
            session.put(IdentityDTO.VIEWABLE_IDENTITY, selectedId);
            session.put(AnalyzeControllerBean.CURRENT_CARD_PANEL, AnalyzeControllerBean.IDENTITY_SEARCH_RESULTS);
            NavigationHistory.getInstance().saveHistory(this);
        } catch (GeneralException ge) {
            log.error("Unable to select an identity in the search bean", ge);
        } finally {
            this.limitOverride = null;
        }
        
        return IdentityDTO.createNavString(Consts.NavigationString.editIdentity.name(), getSelectedId());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /** This is a pre-action that's run before the showEntitlements window is popped up
     * that checks to see if any filters were chosen.  If not, we show an error because
     * you can't show entitlements without filters - Bug 8043 PH
     */
    public String preShowEntitlements() throws GeneralException {
        Filter filter = getFilter();
        if(filter!=null){
            return "";
        } else{
            addMessageToSession(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_FILTERS_SHOW_ENTITLEMENTS), null);
            return null;
        }	    
    }

    /** Takes the filters used to produce the list of identities and saves them as a 
     * group object (IPOP) so that it can be used for reporting and analyzing later
     */
    public String saveAsIpopAction () throws GeneralException {

        if(getSearchItem()!=null && getSearchItem()!=null) {
            GroupDefinition gD = new GroupDefinition();

            /** Add any necessary joins **/
            if(getFilter()!=null){
                Filter gdFilter = getFilter();
                // Default to the logged in user's assigned scope.
                Identity loggedInUser = super.getLoggedInUser();
                if (null != loggedInUser) {
                    Scope assignedScope = loggedInUser.getAssignedScope();
                    if (null != assignedScope) {
                        gD.setAssignedScope(assignedScope);
                        //Get the proper assigned scope filter
                        QueryOptions qo = new QueryOptions();
                        qo.setScopeResults(true);
                        ResultScoper scoper = new ResultScoper(getContext(), loggedInUser, qo);
                        Filter scopingFilter = scoper.getScopeFilter();
                        //AND the filter to the base filter
                        if (null != scopingFilter) {
                            gdFilter = Filter.and(gdFilter, scopingFilter);
                        }
                    }
                }

                log.info("Setting Ipop Filter: " + gdFilter);
                gD.setFilter(gdFilter);
            } else{
                addMessageToSession(new Message(Message.Type.Error,
                        MessageKeys.ERR_NO_FILTERS_TO_SAVE), null);
                return null;
            }
            gD.setName(getSearchItemName());
            gD.setDescription(getSearchItemDescription());
            gD.setIndexed(true);
            gD.setPrivate(true);
            try {
                gD.setOwner(getLoggedInUser());
            } catch (GeneralException ge) {
                log.error("Unable to set owner of IPOP." + ge.getMessage());
            }



            //TODO: check to make sure there isn't a group def in the context that 
            //already exists with these attributes.

            //Save the group def to the context.
            try {
                getContext().saveObject(gD);
                getContext().commitTransaction();
            } catch (GeneralException ge) {
                log.error("Unable to save Group Definition [" + getSearchItemName() + "]. Exception: " + ge.getMessage());
                addMessageToSession(new Message(Message.Type.Error, MessageKeys.CANT_SAVE_IPOP), null);
                return null;
            }
            addMessageToSession(new Message(Message.Type.Info, MessageKeys.IPOP_SAVED,
                    getSearchItemName()), null);
        }
        return "saveIdentitySearchAsIpop";
    }

    /** Shows a list of entitlements for the user population, similar to what is done during 
     * entitlement mining when a user chooses to create a new profile from entitlement mining
     */
    public String showEntitlementBreakdown() {
        miningBean = new EntitlementMiningBean();

        /** We copy the search item and set the derived filters on it so that
         * the mining bean will get a set of compiled/converted filters with 
         * any necessary joins
         */
        SearchItem copy = new SearchItem();
        SearchItem item = this.getSearchItem();
        copy.setType(item.getType());
        copy.setCalculatedFilters(Arrays.asList(new Filter[]{getFilter()}));

        miningBean.setSearchItem(copy);
        setSearchComplete(true);
        return "showEntitlementBreakdown";
    }

    public String scheduleCertificationAction() throws GeneralException{
        restore();
    	List<Map<String, String>> availableIdentities = new ArrayList<>();
        
        /** If this is a certify all action, go through the rows and get all the identities that have been
         * chosen, otherwise, just send an empty list to the bulk certification since it has the list of ids to select */
        if(getBulkCertification().isCertifyAll()) {
            try {
                // Set our override here to force limit of zero to get all results.
                this.limitOverride = 0;
                List<Map<String, Object>> identitiesToCertify = getRows();
                for (Map<String, Object> identity : identitiesToCertify) {
                    Map<String, String> identityAttrs = new HashMap<>();
                    identityAttrs.put("id", (String)identity.get("id"));
                    identityAttrs.put("name", (String)identity.get("name"));
                    availableIdentities.add(identityAttrs);
                }
            } finally {
                this.limitOverride = null;
            }
        }
        return getBulkCertification().scheduleBulkCertificationAction(availableIdentities, getLoggedInUser());
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Identity Search Page";
    }

    public String getNavigationString() {
        return "identitySearchResults";
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
    // Overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    /** We do special row conversion in order to populate certain columns that return lists of values **/
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {
        Map<String,Object> map = new HashMap<String,Object>(row.length);

        map = super.convertRow(row, cols);

        List<ColumnConfig> columns = getColumns();
        for(ColumnConfig column : columns) {			
            /** If we have the list of applications in the list, run a special query to retrieve them **/
            if(column.getProperty().equals(SearchBean.CALCULATED_COLUMN_PREFIX + ATT_SEARCH_COL_APPLICATION_ID)) {
                String identityId = (String)row[cols.indexOf("id")];
                List<String> props = new ArrayList<String>();
                props.add("application.name");
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("identity.id", identityId));
                qo.setOrderBy("application.name");
                Iterator<Object[]> iter = getContext().search(Link.class, qo, props);
                String appString = "";
                while(iter.hasNext()){
                    appString += (iter.next()[0]);
                    if(iter.hasNext())
                        appString += ", ";
                }
                map.put(column.getProperty(), appString);
            } 

            /** If we have the list of workgroups in the list, run a special query to retrieve them **/
            if(column.getProperty().equals(SearchBean.CALCULATED_COLUMN_PREFIX + ATT_SEARCH_COL_WORKGROUP_ID)) {
                String identityId = (String)row[cols.indexOf("id")];

                List<String> props = new ArrayList<String>();
                props.add("workgroups.name");

                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("id", identityId));
                qo.setOrderBy("name");
                Iterator<Object[]> iter = getContext().search(Identity.class, qo, props);
                String wgString = "";
                while(iter.hasNext()){
                    String wg = (String)iter.next()[0];
                    if(wg!=null) {
                        wgString += wg;
                        if(iter.hasNext())
                            wgString += ", ";
                    }
                }
                map.put(column.getProperty(), wgString);
            } 
            
            //convert to IdentityType displayName
            if(column.getProperty().equals("type")) {
                String typeName = (String)row[cols.indexOf("type")];;
                IdentityTypeDefinition def = Identity.getObjectConfig().getIdentityType(typeName);
                if (def != null) {
                    map.put(column.getProperty(), getMessage(def.getDisplayableName()));
                }
            }
        }
        return map;
    }

    @Override
    public int getLimit() {
        // If our override is set, use it directly.
        return (this.limitOverride != null) ? this.limitOverride : super.getLimit();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @return the columnList
     */
    public List<SelectItem> getIdentityFieldList() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        // Use a set to avoid any duplicates found in extended attributes
        Set<SelectItem> set = new TreeSet<SelectItem>(new SelectItemComparator(getLocale()));

        // !! jsl - this needs to be configurable
        // This is distasteful, we've got this nice data model to define
        // all the inputs but we hard code the return fields. Someday, let's
        // discuss a more complex SearchConfig object that lets you 
        // define both the inputs and the return fields.
        set.add(new SelectItem("userName", getMessage(MessageKeys.USER_NAME)));
        set.add(new SelectItem("displayName", getMessage(MessageKeys.DISPLAY_NAME)));
        set.add(new SelectItem("firstName", getMessage(MessageKeys.FIRST_NAME)));
        set.add(new SelectItem("lastName", getMessage(MessageKeys.LAST_NAME)));
        set.add(new SelectItem("manager", getMessage(MessageKeys.MANAGER)));
        set.add(new SelectItem("email", getMessage(MessageKeys.EMAIL)));
        set.add(new SelectItem("type", getMessage(MessageKeys.TYPE)));
        set.add(new SelectItem("softwareVersion", getMessage(MessageKeys.ATT_SOFTWARE_VERSION)));
        set.add(new SelectItem("administrator", getMessage(MessageKeys.ATT_ADMINISTRATOR)));
        set.add(new SelectItem("businessRoles", getMessage(MessageKeys.SRCH_INPUT_DEF_DETECTED_ROLES)));
        set.add(new SelectItem("assignedRoles", getMessage(MessageKeys.ASSIGNED_ROLES)));
        set.add(new SelectItem("application", getMessage(MessageKeys.APPLICATIONS)));

        // jsl - this would be nice but it doesn't display properly, I guess because
        // it's a single String in the filter, but it is actually multi-valued in the result.
        // It's also weird because you can't see the application/instance correspondence
        //set.add(new SelectItem("instance", getMessage(MessageKeys.INSTANCE)));

        set.add(new SelectItem("workgroup", getMessage(MessageKeys.SRCH_INPUT_DEF_WORKGROUP)));

        // Add extended attributes, but don't overwrite any of the above items
        try {
            /** Load the extended attributes of the identity **/
            ObjectConfig identityConfig = getIdentityConfig();
            if(identityConfig != null) {
                List<ObjectAttribute> attributes = identityConfig.getExtendedAttributeList();
                if(attributes != null) {
                    for(ObjectAttribute attr : attributes) {
                        set.add(new SelectItem(attr.getName(), attr.getDisplayableName(getLocale())));
                    }
                }
            }
        } catch (GeneralException ge) {
            log.error("Unable to get extended identity attributes for displaying on the advanced search page. " + ge.getMessage());
        }

        // 'convert' to a List return type
        list.addAll(set);

        return list;
    }

    public List<SelectItem> getIdentityTypeSelectItems() {
        List<SelectItem> result = new ArrayList<SelectItem>();

        result.add(new SelectItem("", ""));

        List<IdentityTypeDefinition> types = Identity.getObjectConfig().getIdentityTypesList();
        for (IdentityTypeDefinition type : Util.safeIterable(types)) {
            result.add(new SelectItem(type.getName(), getMessage(type.getDisplayName())));
        }
        
        return result;
    }

    public List<SelectItem> getRiskFieldList() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("compositeScore", getMessage(MessageKeys.RISK_COMPOSITE_SCORE)));
        list.add(new SelectItem("businessRoleScore", getMessage(MessageKeys.RISK_BUSINESS_ROLE_SCORE)));
        list.add(new SelectItem("businessRoleRawScore", getMessage(MessageKeys.RISK_BUSINESS_ROLE_BASE_SCORE)));
        list.add(new SelectItem("entitlementScore", getMessage(MessageKeys.RISK_ENTITLEMENT_SCORE)));
        list.add(new SelectItem("entitlementRawScore", getMessage(MessageKeys.RISK_ENTITLEMENT_BASE_SCORE)));
        list.add(new SelectItem("policyScore", getMessage(MessageKeys.RISK_POLICY_SCORE)));
        list.add(new SelectItem("certificationScore", getMessage(MessageKeys.RISK_CERTIFICATION_SCORE)));
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));
        return list;
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        if (projectionColumns == null) {
            super.getProjectionColumns();

            // If name isn't already in the column list, add it so that
            // we can use it later to construct a cert.
            if (!projectionColumns.contains("name")) {
                projectionColumns.add("name");
            }
        }

        return projectionColumns;
    }

    /**
     * @return the selectedColumns
     */
    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(getSelectedIdentityFields()!=null)
                selectedColumns.addAll(selectedIdentityFields);
            if(getSelectedRiskFields()!=null)
                selectedColumns.addAll(selectedRiskFields);
        }
        return selectedColumns;
    }

    @Override
    public List<String> getSelectedColumnHeaders() {
        List<String> columnHeaders = new ArrayList<String>();
        if(getSelectedColumns()!=null) {
            for(String col : selectedColumns) {
                if(extendedAttributeKeys.contains(col)) {
                    SearchInputDefinition def = getInputs().get(col);
                    if(def!=null) {
                        col = def.getDescription();
                    }
                }
                columnHeaders.add(col);
            }
        }
        return columnHeaders;
    }


    protected void setFields() {
        super.setFields();
        getSearchItem().setIdentityFields(getSelectedIdentityFields());
        getSearchItem().setRiskFields(getSelectedRiskFields());
    }

    @Override
    public Map<String, SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> argMap = super.buildInputMap();
        extendedAttributeKeys = new ArrayList<String>();
        extendedAttributes = new HashMap<String,Object>();

        List<String> disabledAttributeSuggests = new ArrayList<String>();
        try {
            UIConfig uiConfig = getUIConfig();
            disabledAttributeSuggests = uiConfig.getList(DISABLED_SUGGEST_ATTRIBUTES);
        } catch(GeneralException ge) {
            log.error("Unable to load UIConfig due to exception: " + ge.getMessage());
        }
        try{           
            /**
             * Get the extended attribute fields from the ObjectConfig so that the slicer/dicer
             * can search over those as well
             */
            ObjectConfig identityConfig = getIdentityConfig();

            if(identityConfig != null) {
                if(identityConfig.getExtendedAttributeList()!=null) {
                    for(ObjectAttribute attr : identityConfig.getExtendedAttributeList()) {

                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(PropertyType.Date)) {
                            continue;
                        }

                        SearchInputDefinition def = new SearchInputDefinition();
                        if (ObjectAttribute.TYPE_IDENTITY.equals(attr.getType())) {
                            def.setPropertyName(attr.getName() + ".displayName");
                        } else {
                            def.setPropertyName(attr.getName());
                        }
                        def.setName(attr.getName());

                        if(disabledAttributeSuggests.contains(attr.getName())) {
                            def.setSuggestType(SearchInputDefinition.SUGGEST_TYPE_NONE);
                        }

                        def.setHeaderKey(attr.getDisplayableName(getLocale()));
                        def.setInputType(InputType.Like);
                        def.setMatchMode(Filter.MatchMode.START);
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_IDENT);
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
                /**
                 * Get the multivalued attributes and put them in a separate map since they'll
                 * be handled differently.
                 */
                if(identityConfig.getMultiAttributeList() != null) {
                    multiValuedIdentityAttributeKeys = new ArrayList<String>();
                    for(ObjectAttribute attr : identityConfig.getMultiAttributeList()) {
                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(attr.getName());
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        def.setHeaderKey(attr.getDisplayableName());
                        def.setPropertyName(attr.getName());
                        def.setPropertyType(PropertyType.StringList);
                        def.setFilterBuilder("sailpoint.search.ExternalAttributeFilterBuilder");
                        def.setExtendedAttribute(false);
                        // see comments about ignorecase in the clause above
                        //def.setIgnoreCase(true);
                        argMap.put(attr.getName(), def);
                        multiValuedIdentityAttributeKeys.add(attr.getName());
                        // Not really an extended ident but has to match a value
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_IDENT);
                    }
                }   
            } 	

            /**
             * Get the extended attribute fields from the ObjectConfig so that the slicer/dicer
             * can search over those as well
             */
            ObjectConfig linkConfig = getLinkConfig();
            if(linkConfig != null) {
                if(linkConfig.getExtendedAttributeList()!=null) {
                    for(ObjectAttribute attr : linkConfig.getExtendedAttributeList()) {

                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(PropertyType.Date)) {
                            continue;
                        }

                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName());

                        if(disabledAttributeSuggests.contains(attr.getName())) {
                            def.setSuggestType(SearchInputDefinition.SUGGEST_TYPE_NONE);
                        }
                        def.setInputType(InputType.Like);
                        def.setMatchMode(Filter.MatchMode.START);
                        def.setHeaderKey(attr.getDisplayableName(getLocale()));
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT);
                        def.setPropertyName(ATT_IDT_SEARCH_LINK_PREFIX+attr.getName());
                        def.setFilterBuilder("sailpoint.search.LinkFilterBuilder");
                        def.setPropertyType(attr.getPropertyType());
                        def.setExtendedAttribute(true);
                        // don't do this after 7.1, it doesn't always match the hbm.xml
                        //def.setIgnoreCase(true);
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        argMap.put(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName(), def);
                        extendedAttributeKeys.add(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName());
                        extendedAttributes.put(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName(), attr);
                    }
                }

                if (linkConfig.getMultiAttributeList() != null) {
                    multiValuedLinkAttributeKeys = new ArrayList<String>();
                    for(ObjectAttribute attr : linkConfig.getMultiAttributeList()) {
                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(attr.getName());
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        def.setHeaderKey(attr.getDisplayableName(getLocale()));
                        def.setFilterBuilder("sailpoint.search.ExternalAttributeFilterBuilder");
                        def.setPropertyType(PropertyType.StringList);
                        def.setExtendedAttribute(false);
                        // don't do this after 7.1 it doesn't always match the hbm.xml
                        //def.setIgnoreCase(true);
                        def.setPropertyName(attr.getName());
                        argMap.put(attr.getName(), def);
                        multiValuedLinkAttributeKeys.add(attr.getName());
                        def.setSearchType(ATT_SEARCH_TYPE_EXTERNAL_LINK);
                    }
                }
            }

        } catch (GeneralException ge) {
            log.error("Exception during buildInputMap: [" + ge.getMessage() + "]");
        }

        return argMap;
    }

    /**
     * @return the searchType
     */
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_IDENT;
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
        allowableTypes.add(ATT_SEARCH_TYPE_EXTENDED_IDENT);
        allowableTypes.add(ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT);
        allowableTypes.add(ATT_SEARCH_TYPE_EXTERNAL_LINK);
        allowableTypes.add(ATT_SEARCH_TYPE_IDENT);
        allowableTypes.add(ATT_SEARCH_TYPE_RISK);
        return allowableTypes;
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

    /**
     * @param extendedAttributeKeys the extendedAttributeKeys to set
     */
    public void setExtendedAttributeKeys(List<String> extendedAttributeKeys) {
        this.extendedAttributeKeys = extendedAttributeKeys;
    }


    public List<String> getMultiValuedIdentityAttributeKeys() {
        if(multiValuedIdentityAttributeKeys==null) {
            buildInputMap();
        }
        return multiValuedIdentityAttributeKeys;
    }

    public void setMultiValuedIdentityAttributeKeys(List<String> keys) {
        this.multiValuedIdentityAttributeKeys = keys;
    }

    public List<String> getMultiValuedLinkAttributeKeys() {
        if(multiValuedLinkAttributeKeys==null) {
            buildInputMap();
        }
        return multiValuedLinkAttributeKeys;
    }

    public void setMultiValuedLinkAttributeKeys(List<String> keys) {
        this.multiValuedLinkAttributeKeys = keys;
    }

    /**
     * @return the miningBean
     */
    public EntitlementMiningBean getMiningBean() {
        return miningBean;
    }

    /**
     * @param miningBean the miningBean to set
     */
    public void setMiningBean(EntitlementMiningBean miningBean) {
        this.miningBean = miningBean;
    }

    /**
     * @return the selectedIdentityFields
     */
    public List<String> getSelectedIdentityFields() {
        return selectedIdentityFields;
    }

    /**
     * @param selectedIdentityFields the selectedIdentityFields to set
     */
    public void setSelectedIdentityFields(List<String> selectedIdentityFields) {
        this.selectedIdentityFields = selectedIdentityFields;
    }

    /**
     * @return the selectedRiskFields
     */
    public List<String> getSelectedRiskFields() {
        return selectedRiskFields;
    }

    /**
     * @param selectedRiskFields the selectedRiskFields to set
     */
    public void setSelectedRiskFields(List<String> selectedRiskFields) {
        this.selectedRiskFields = selectedRiskFields;
    }

    public BulkCertificationHelper getBulkCertification() {
        return bulkCertification;
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();

        /** If this search includes link fields, we need to join to the link table so the
         * columns will be recognized by hibernate  */
        boolean foundLinkField = false;
        if(getSelectedIdentityFields()!=null) {
            for(String field: getSelectedIdentityFields()) {
                if(field.startsWith(ATT_IDT_SEARCH_LINK_PREFIX)) {
                    foundLinkField = true; 
                }
            }
            if(foundLinkField) {
                LinkFilterBuilder builder = new LinkFilterBuilder();
                ops.add(builder.getJoin());
            }
        }

        ops.setDistinct(true);
        ops.setScopeResults(true);
        return ops;
    }

    @Override 
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Filter> convertAndCloneFilters(List<Filter> filters, String operation, 
            Map<String, SearchInputDefinition> inputs) {
        List<Filter> newFilters = FilterConverter.convertAndCloneFilters(filters, operation, inputs);

        /** Bundles are now hierarchical.  Because of this, we need to check these filters
         * for the existence of bundle related filters and add filters to find their parent 
         * filters **/
        if(newFilters!=null && !newFilters.isEmpty()) {
            boolean useHierarchy = (inputs.get(ATT_IDT_SEARCH_ROLE_HIERARCHY).getValue()!=null && 
                    (Boolean)inputs.get(ATT_IDT_SEARCH_ROLE_HIERARCHY).getValue());
            List<String> detectedRoles = (List)inputs.get(ATT_IDT_SEARCH_ROLE).getValue();
            List<String> assignedRoles = (List)inputs.get(ATT_IDT_SEARCH_ASSIGNED_ROLE).getValue();

            if(useHierarchy && detectedRoles!=null && !detectedRoles.isEmpty()) {
                bundleFilters = FilterHelper.getExtraBundleFilters(newFilters, "bundles", this);
                if(bundleFilters!=null)
                    newFilters.addAll(bundleFilters);
            }

            boolean useAssignedHierarchy = (inputs.get(ATT_IDT_SEARCH_ASSIGNED_ROLE_HIERARCHY).getValue()!=null && 
                    (Boolean)inputs.get(ATT_IDT_SEARCH_ASSIGNED_ROLE_HIERARCHY).getValue());
            if(useAssignedHierarchy && assignedRoles!=null && !assignedRoles.isEmpty()) {
                assignedRoleFilters = FilterHelper.getExtraBundleFilters(newFilters, "assignedRoles", this);
                if(assignedRoleFilters!=null)
                    newFilters.addAll(assignedRoleFilters);
            }            
        }
        return newFilters;
    }

    public String getGridStateName() {
        return GRID_STATE;
    }

    /** Returns the path to the form to edit a report that was saved from this query **/
    @Override
    public String getFormPath() {
        return "/analyze/analyzeTabs.jsf";
    }

    /**
     * Action to go back to the previous page in the navigation history.
     */
    public String cancel() throws GeneralException {
        String result = NavigationHistory.getInstance().back();

        if (result == null) {
            result = "searchAgain";
        }

        return result;
    }

    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.IDENTITY_LCASE);
    }

    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.IDENTITIES_LCASE);
    }

    public Map<String, Object> getExtendedAttributes() {
        return extendedAttributes;
    }

    public void setExtendedAttributes(Map<String, Object> extendedAttributes) {
        this.extendedAttributes = extendedAttributes;
    }
    
    @Override
    public String clearSearchItem() {
        _entitlementFilterBean = null;
        super.getSessionScope().remove(SESSION_ENTITLEMENT_FILTER_BEAN);
        return super.clearSearchItem();  
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Identity Entitlement filtering
    //
    /////////////////////////////////////////////////////////////////////////
    
    public IdentityEntitlementFilterBean getIdentityEntitlementFilter() {        
        return _entitlementFilterBean;
    }
    
    public void setIdentityEntitlementFilter(IdentityEntitlementFilterBean bean ) {
        _entitlementFilterBean = bean;
        super.getSessionScope().put(SESSION_ENTITLEMENT_FILTER_BEAN, _entitlementFilterBean);
    }    
    
    public void restoreIdentityEntitlementFilter() {
        _entitlementFilterBean =
            (IdentityEntitlementFilterBean) super.getSessionScope().get(SESSION_ENTITLEMENT_FILTER_BEAN);
        if( _entitlementFilterBean == null ) {
            _entitlementFilterBean = new IdentityEntitlementFilterBean(this, SESSION_ENTITLEMENT_FILTER_BEAN);            
        }            
        _entitlementFilterBean.setBaseBean(this);
    }

    /**
     * Override SearchBean.loadSearchItem so we can add the IdentityEntitlementFilters
     *
     * @return navigation string
     */
    @Override
    public String loadSearchItem() {
        super.loadSearchItem();

        SearchItem item = (SearchItem)getSessionScope().get(getSearchItemId());
        if (item != null && _entitlementFilterBean != null) {
            _entitlementFilterBean.reset(); // Clear out old filters
            _entitlementFilterBean.addFilters(item); // Add current search item filters
        }

        return "loadSearchItem";
    }
}
