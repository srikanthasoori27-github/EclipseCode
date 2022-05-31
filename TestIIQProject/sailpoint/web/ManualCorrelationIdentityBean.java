/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.extjs.Component;
import sailpoint.web.extjs.GenericJSONObject;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponse;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.SearchBean;
import sailpoint.web.util.WebUtil;
import sailpoint.web.view.ViewBuilder;
import sailpoint.web.view.ViewEvaluationContext;

/**
 * Backing bean for the 'Target Identity' grid on the manual correlation page.
 * This bean performs the filter search for this grid, as well as defininng
 * the searchable extended attributes to be added to the Advanced Search expando.
 *
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ManualCorrelationIdentityBean extends BaseListBean<Identity> {

    /**
     * Extend the filter list context and override the convertFilterSingleValue to search on id instead of name for Identity filters. 
     */
    private class ManualCorrelationIdentityBeanFilterContext extends BaseListFilterContext {
        
        public ManualCorrelationIdentityBeanFilterContext() {
            super(ObjectConfig.getObjectConfig(Identity.class));
        }

        @Override
        protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
            Filter filter;
            if (ListFilterDTO.DataTypes.Identity == filterDTO.getDataType()) {
                filter = getSimpleFilter(filterDTO.getProperty() + ".id", value, operation);
            } else {
                filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
            }
            
            return filter;
        }
    }

    private static Log log = LogFactory.getLog(ManualCorrelationIdentityBean.class);

    private static final String MAN_CORRELATION_IDENTITY_GRID = "manualCorrelationIdentityGrid";
    public static final String IDENTITY_TYPE_DEFINITION_NAME_PROP = "name";
    public static final String IDENTITY_TYPE_DEFINITION_DISPLAY_NAME_PROP = "displayName";

    private static final List<String> ALLOWED_SEARCH_TYPES = Arrays.asList(SearchBean.ATT_SEARCH_TYPE_IDENT, SearchBean.ATT_SEARCH_TYPE_NONE);

    private Map<String, SearchInputDefinition> standardSearchDefinitions;
    private Map<String, SearchInputDefinition> extendedSearchDefintions;
    private List<String> columnNames;


    public ManualCorrelationIdentityBean() {
        setScope(Identity.class);
    }

    /**
     * Returns standard SearchInputDefinitions for Identities. This is be used
     * to build the grid filter query
     *
     * @return
     * @throws GeneralException
     */
    public Map<String, SearchInputDefinition> getStandardSearchItems() throws GeneralException {

        if (standardSearchDefinitions != null)
            return standardSearchDefinitions;

        standardSearchDefinitions = new HashMap<String, SearchInputDefinition>();

        Configuration systemConfig = getContext().getConfiguration();
        List<SearchInputDefinition> inputDefinitions = (List<SearchInputDefinition>)
                systemConfig.get(Configuration.SEARCH_INPUT_DEFINITIONS);

        if (inputDefinitions != null) {
            for (SearchInputDefinition input : inputDefinitions) {
                if (ALLOWED_SEARCH_TYPES.contains(input.getSearchType())) {
                    
                    /** Clone the input definition so that we don't change the system defs **/
                    SearchInputDefinition cloneInput = input.copy();
                    
                    standardSearchDefinitions.put(cloneInput.getName(), cloneInput);
                }
            }
        }

        
        
        return standardSearchDefinitions;
    }

    /**
     * Builds SearchInputDefinitions for Identity extended attributes. This iss used
     * to create handle search input fields on the identity grid advanced search
     * panel.
     * @return
     * @throws GeneralException
     */
    public Map<String, SearchInputDefinition> getExtendedSearchItems() throws GeneralException {

        if (extendedSearchDefintions != null)
            return extendedSearchDefintions;

        extendedSearchDefintions = new HashMap<String, SearchInputDefinition>();

        ObjectConfig identityConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        List<ObjectAttribute> attributes = identityConfig.getExtendedAttributeList();

        if (attributes != null) {
            for (ObjectAttribute attr : attributes) {
                SearchInputDefinition def = new SearchInputDefinition();
                def.setName(attr.getName());
                def.setInputType(SearchInputDefinition.InputType.Like);
                def.setMatchMode(Filter.MatchMode.START);
                def.setPropertyName(attr.getName());
                def.setPropertyType(attr.getPropertyType());
                def.setExtendedAttribute(true);
                def.setDescription(attr.getDisplayName());
                extendedSearchDefintions.put(def.getPropertyName(), def);
            }
        }

        return extendedSearchDefintions;
    }

    /**
     * Builds json representation of SearchInputDefinitions for Identity extended attributes.
     * This json is used to create search input fields on the identity grid advanced search
     * panel.
     * @return
     * @throws GeneralException
     */
    public String getExtendedSearchFormDefinition() throws GeneralException {

        List<GenericJSONObject> components = new ArrayList<GenericJSONObject>();

        if (getExtendedSearchItems() != null) {
            for (String key : getExtendedSearchItems().keySet()) {
                SearchInputDefinition def = getExtendedSearchItems().get(key);
                GenericJSONObject component = new GenericJSONObject();
                if (def.getPropertyType() == SearchInputDefinition.PropertyType.Identity) {
                    component.set(Component.PROPERTY_XTYPE, Component.XTYPE_IDENTITY_SUGGEST);
                }
                else {
                    component.set(Component.PROPERTY_XTYPE, Component.XTYPE_TEXT_FIELD);
                }
                component.set("fieldLabel", WebUtil.escapeHTML(getMessage(def.getDescription()), false));
                component.set("name", "q_" + def.getName());
                components.add(component);
            }
        }

        String out = "{}";
        if (components != null && !components.isEmpty()) {
            Writer jsonString = new StringWriter();
            JSONWriter writer = new JSONWriter(jsonString);
            try {
                writer.array();
                for (GenericJSONObject comp : components) {
                    comp.getJson(writer);
                }
                writer.endArray();
            } catch (JSONException e) {
                log.error(e);
                throw new GeneralException(MessageKeys.ERR_EXCEPTION, e);
            }
            out = jsonString.toString();
        }

        return out;
    }

    /**
     * Return columns used in the
     * @return
     * @throws GeneralException
     */
    public List<String> getProjectionColumns() throws GeneralException {

        if (columnNames != null)
            return columnNames;

        columnNames = new ArrayList<String>();
        columnNames.add("id");

        List<ColumnConfig> columnConf = getColumns();
        if (columnConf != null) {
            for (ColumnConfig conf : columnConf) {
                columnNames.add(conf.getProperty());
            }
        }

        return columnNames;
    }

    public Map<String, String> getSortColumnMap() throws GeneralException {
        Map<String, String> mapping = new HashMap<String, String>();
        for (String col : getProjectionColumns()) {
            mapping.put(Util.getJsonSafeKey(col), col);
        }
        return mapping;
    }

    public String getSearchResults() {

        Map<String, String> queryParams = new HashMap<String, String>();

        for (Object param : this.getRequestParam().keySet()) {
            if (param != null && param.toString().startsWith("q_")) {
                String p = param.toString();
                String val = this.getRequestParameter(p);

                if (val != null && val.trim().length() > 0) {
                    String propertyName = Util.getKeyFromJsonSafeKey(p.substring(2, p.length()));
                    queryParams.put(propertyName, val);
                }
            }
        }

        boolean isQuickSearch = "true".equals(this.getRequestParameter("quickSearch"));

        String responseJson = "";
        try {
            SailPointContext ctx = getContext();
            ctx.setScopeResults(true);
            QueryOptions ops = super.getQueryOptions();
            ops.setRestrictions(buildFilters(queryParams, isQuickSearch));

            // show all Identities not just those in scope bug#3616
            ops.setScopeResults(false);

            List<ColumnConfig> columnConf = getColumns();
            //Need to add id to the Config so that we get it from the Database
            ColumnConfig identId = new ColumnConfig("id","id");
            identId.setFieldOnly(true);
            columnConf.add(identId);
            
            ViewEvaluationContext viewContext = new ViewEvaluationContext(this, columnConf);

            ViewBuilder viewBuilder = new ViewBuilder(viewContext, Identity.class, columnConf);

            ListResult res = viewBuilder.getResult(ops);          
            GridResponseMetaData meta = viewBuilder.calculateGridMetaData();


             for(Map<String, Object> row : (List<Map>)(res.getObjects())) {
                 //Convert Date to Time
                 Date d = (Date) row.get("lastRefresh");
                 row.put("lastRefresh", d != null ? d.getTime() : null);

                 //convert IdentityTypeDefinition displayName
                 String type = (String) row.get(Identity.ATT_TYPE);
                 if (Util.isNotNullOrEmpty(type)) {
                     ObjectConfig identityConfig = Identity.getObjectConfig();
                     IdentityTypeDefinition def = identityConfig.getIdentityType(type);
                     row.put(Identity.ATT_TYPE, def != null ? getMessage(def.getDisplayableName()) : type);
                 }
             }
                        
            res.setMetaData(meta.asMap());
            responseJson = JsonHelper.toJson(res);

        } catch (GeneralException e) {
            log.error(e);
            return JsonHelper.failure();
        }


        return responseJson;
    }

    /**
     * Builds Filter list for the Target Identity grid search.
     *
     * @param queryParams
     * @param doOrQuery
     * @return
     * @throws GeneralException
     */
    private List<Filter> buildFilters(Map<String, String> queryParams, boolean doOrQuery) throws GeneralException {

    	//Bug #24352 - Use a filter service to ensure Identity filters search on id instead of name 
        ListFilterService filterService = new ListFilterService(getContext(), getLocale(), new ManualCorrelationIdentityBeanFilterContext()); 
        List<Filter> filters = filterService.convertQueryParametersToFilters(queryParams, false);

        for (String param : queryParams.keySet()) {
            SearchInputDefinition def = null;

            // Creates a filter using the SearchInputDefinitions defined for identities
            // in the iiq configuration
            if ("correlated".equals(param) && !"".equals(queryParams.get("correlated"))){
                String val = queryParams.get("correlated");
                filters.add(Filter.eq("correlated", "true".equals(val)));
            } else if (getStandardSearchItems().containsKey(param)) {
                def = getStandardSearchItems().get(param);
                def.setValue(queryParams.get(param));
                Filter f = def.getFilter(this.getContext());
                if (f != null)
                    filters.add(f);
            } else if (getExtendedSearchItems().containsKey(param)) {
                def = getExtendedSearchItems().get(param);
                def.setValue(queryParams.get(param));
                Filter f = def.getFilter(this.getContext());
                if (f != null)
                    filters.add(f);
            }
        }

         if (doOrQuery)
            return Arrays.asList(Filter.or(filters));
        else
            return filters;

    }

    /**
     * Map of attribute values for the given identity in json format. This is used by
     * the popup you get when clicking on the identity name in the Target Identity grid.
     * @return
     */
    public String getIdentityAttributes() {

        String id = this.getRequestParameter("id");

        GenericJSONObject obj = new GenericJSONObject();
        String out = "{}";
                                   
        try {
            getContext().setScopeResults(true);
            Identity identity = getContext().getObjectById(Identity.class, id);
            if (identity.getAttributes() != null) {
                ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);

                for (String attrName : identity.getAttributes().keySet()) {
                    
                    ObjectAttribute attribute = identityConfig.getObjectAttribute(attrName);
                    
                    if(attribute==null || (!attribute.isSystem() && !attribute.isExtended() && !attribute.isStandard()  && !attribute.isMulti())) {
                        continue;
                    }

                    String displayName = attrName;
                    if (identityConfig != null) {
                        displayName = identityConfig.getDisplayName(attrName, getLocale());
                    }
                    
                    // display name could be a msg key
                    String localizedName = Internationalizer.getMessage(displayName, getLocale());
                    
                    displayName = localizedName != null ? localizedName : displayName;

                    //Encode displayName to avoid XSS
                    displayName = WebUtil.escapeHTML(displayName, false);

                    // stuff the value in a msg object which handles localizing lists, dates, bools, etc.
                    Message attrVal = new Message(MessageKeys.MSG_PLAIN_TEXT,
                            identity.getAttributes().get(attrName));

                    obj.set(displayName, attrVal.getLocalizedMessage(getLocale(), getUserTimeZone()));
                }
                
                addIdentityToIdentityRelationships(obj, identity);
                
                out = obj.getJson();
            }
        } catch (Exception e) {
            log.error(e);
        }

        return out;
    }
    
    private void addIdentityToIdentityRelationships(GenericJSONObject obj, Identity identity) throws GeneralException {

        ObjectConfig config = Identity.getObjectConfig();
        for (ObjectAttribute attributeDefinition : config.getObjectAttributes()) {
            if (identity.isExtendedIdentityType(attributeDefinition)) {
                String attributeDisplayName = config.getDisplayName(attributeDefinition.getName(), getLocale());
                //Encode displayName to avoid XSS
                attributeDisplayName = WebUtil.escapeHTML(attributeDisplayName, false);
                Identity relatedIdentity = identity.getExtendedIdentity(attributeDefinition.getExtendedNumber());
                String attributeValue = (relatedIdentity == null) ? "" : relatedIdentity.getDisplayableName();
                //Escape to avoid XSS
                attributeValue = WebUtil.escapeHTML(attributeValue, false);
                obj.set(attributeDisplayName, attributeValue);
            }
        }
    }

    public List<ColumnConfig> getColumns() throws GeneralException {
        List<ColumnConfig> columns = new ArrayList<ColumnConfig>();
        // copy columns and localize header
        for(ColumnConfig c : super.getUIConfig().getManualCorrelationIdentityTableColumns()){
            ColumnConfig column = new ColumnConfig(c);
            column.setHeaderKey(getMessage(c.getHeaderKey()));
            columns.add(column);
        }

        return columns;
    }

    /**
     * Returns the column model for the target identity grid on the identity correlation page.
     * This is built using the column model and converted for json so it can be passed
     * to the Ext grid.
     * @return
     */
    public String getIdentityGridColModel() {

        String out = JsonHelper.emptyList();
        try {
            List<ColumnConfig> columnConf = getColumns();

            GridResponseMetaData meta = new GridResponseMetaData(columnConf, null);
            out = JsonHelper.toJson(meta);

        } catch (Exception e) {
            log.error(e);
            return JsonHelper.failure();
        }

        return out;
    }

    public String getGridStateName(){
        return MAN_CORRELATION_IDENTITY_GRID;
    }

    /**
     * Build a list of Map objects that contain the name and displayName for the Identity types on the system.
     * @return JSON representation of List of Map objects that contain the data for type objects used by an ExtJs combobox
     */
    public String getIdentityTypesJson() {
        List<Map<String, String>> types = new ArrayList<>();

        ObjectConfig config = Identity.getObjectConfig();
        if (config != null) {
            for (IdentityTypeDefinition typeDef : Util.iterate(config.getIdentityTypesList())) {
                Map<String, String> type = new HashMap<>();
                type.put(IDENTITY_TYPE_DEFINITION_NAME_PROP, typeDef.getName());

                final String localized = Internationalizer.getMessage(typeDef.getDisplayableName(), Locale.getDefault());
                type.put(IDENTITY_TYPE_DEFINITION_DISPLAY_NAME_PROP, localized != null ? localized : typeDef.getDisplayableName());
                types.add(type);
            }
        }
        GridResponseMetaData meta = new GridResponseMetaData(null, Arrays.asList(new GridColumn(IDENTITY_TYPE_DEFINITION_NAME_PROP),
                                                                                 new GridColumn(IDENTITY_TYPE_DEFINITION_DISPLAY_NAME_PROP)));
        GridResponse response = new GridResponse(meta, types, types.size());

        return JsonHelper.toJson(response);
    }

}
