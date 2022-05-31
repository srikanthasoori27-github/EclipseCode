/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AccountGroupService;
import sailpoint.api.FullTextifier;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.AccountGroupSearchBean;

public class EntitlementCatalogListBean extends AccountGroupListBean {

    private static Log log = LogFactory.getLog(EntitlementCatalogListBean.class);
    private String searchField;
    private Map<String,ObjectAttribute> _customSearchableAttrs = new HashMap<String, ObjectAttribute>();

    public static final String SEARCH_FIELD = "entitlementCatalogSearchField";

    /**
     * Default maximum result size when using the fulltext index.
     */
    public static final int DEFAULT_FULLTEXT_MAX_RESULT = 1000;
    
    /**
     * Cached full text search object if we're using Lucene.
     */
    FullTextifier _textifier;

    public EntitlementCatalogListBean() {
        super();
        boolean forceLoad = Util.otob(getRequestParameter("forceLoad"));
        if (!forceLoad) {
            searchField = (String) getSessionScope().get(SEARCH_FIELD);            
        }
    }
    
    @Override
    public String getGridStateName() {
        return "entitlementCatalogGridState";
    }

    @Override
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    /**
     * @return The value entered into the quicksearch box for the grid
     */
    public String getSearchField() {
        return searchField;
    }
    
    public void setSearchField(String searchField) {
        this.searchField = searchField;
    }
    
    @Override
    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getEntitlementCatalogTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }
    
    public String updateGridState() {
        return "";
    }
    
    public boolean isProvisioningEnabled() {
        return AccountGroupService.isProvisioningEnabled(getLoggedInUserCapabilities(), getLoggedInUserRights());
    }
    
    protected void getQueryOptionsFromRequest(QueryOptions qo, Map<String, String> params) throws GeneralException {
        super.getQueryOptionsFromRequest(qo, params);

        // Add extended managed attributes to the query
        Iterator keys = params.keySet().iterator();
        String key;
        while(keys.hasNext()) {
            key = (String)keys.next();
            if(key.startsWith(AccountGroupSearchBean.ATT_IDT_SEARCH_MA_PREFIX)){
                String keyName = key.substring(key.indexOf(AccountGroupSearchBean.ATT_IDT_SEARCH_MA_PREFIX) + AccountGroupSearchBean.ATT_IDT_SEARCH_MA_PREFIX.length(), key.length());
                if (isExtendedIdentity(keyName)) {
                    // IIQSR-64 We changed extended managed attributes of type Identity to be stored as Identity name and not id
                    Identity referenceIdentity = ObjectUtil.getIdentityOrWorkgroup(getContext(), params.get(key));
                    if(referenceIdentity != null) {
                        qo.add(Filter.eq(keyName, referenceIdentity.getName()));
                    }
                } else {
                    qo.add(Filter.eq(keyName, params.get(key)));
                }
            }
        }

        // items covers attribute, application, and value
        String items = params.get("items");
        searchField = items;
        getSessionScope().put(SEARCH_FIELD, searchField);
    }

    /**
     * Return true if this is an extended Identity attribute.
     */
    private boolean isExtendedIdentity(String name) {
        ObjectConfig oconfig = ManagedAttribute.getObjectConfig();
        if (oconfig != null) {
            ObjectAttribute att = oconfig.getObjectAttribute(name);
            if (att != null && ObjectAttribute.TYPE_IDENTITY.equals(att.getType())) {
                return true;
            }
        }
        return false;
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
       
        // bug 25655 - Overloaded BaseListBean method to handle cases 
        // where an extended attribute type of Identity is used to search 
        // the entitlement catalog. We need to return a UI-friendly name
        //  instead of an id. If we can't find the identity then use the 
        //  results as is.
        Map <String, ObjectAttribute> customSearchAttrs = getCustomSearchableAttributes();

        if (!customSearchAttrs.isEmpty() && customSearchAttrs.containsKey(name)) {
            ObjectAttribute searchAttr = customSearchAttrs.get(name);

            if (searchAttr != null && SearchInputDefinition.PropertyType.Identity.equals(searchAttr.getPropertyType())) {
                try {
                    Identity identity = getContext().getObjectByName(Identity.class, (String)value);
                    if (identity != null) {
                        return identity.getDisplayableName();
                    }
                } catch (GeneralException e) {
                    log.debug("Exception while searching for identity in convertColumn: [" + e.getMessage() + "]");
                }
            }
        }

        if ("type".equals(name)) {
            String val = (String) value;
            if (val.equalsIgnoreCase(ManagedAttribute.Type.Entitlement.toString())) {
                return new Message(MessageKeys.ENTITLEMENT).getLocalizedMessage(getLocale(), null);
            } else if (val.equalsIgnoreCase(ManagedAttribute.Type.Permission.toString())) {
                return new Message(MessageKeys.PERMISSION).getLocalizedMessage(getLocale(), null);
            }
            return new Message(val).getLocalizedMessage(getLocale(),null);
        }

        return super.convertColumn(name, value);
    }

    /**
     * Returns the list of extended attributes.
     * 
     * @return a map of the extended attributes keyed by their name
     */
    protected Map<String,ObjectAttribute> getCustomSearchableAttributes() 
    {
        if (_customSearchableAttrs.isEmpty()) {
            // Retrieve the extended attributes and put them in a map keyed
            // by the attribute name for easy searching.
            try {
                ObjectConfig maConfig = getEntitlementConfig();
                if (maConfig != null) {
                    if (maConfig.getExtendedAttributeList() != null) {
                        for (ObjectAttribute attr : maConfig.getExtendedAttributeList()) {
                            _customSearchableAttrs.put(attr.getName(), attr);
                        }
                    }
                }
            } catch (GeneralException ge) {
                log.error("Exception during getCustomSearchableAttributes: [" + ge.getMessage() + "]");
            }
        }

        return _customSearchableAttrs;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Search Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if Lucene is enabled for this search.
     */
    private boolean isLuceneEnabled() throws GeneralException {

        boolean useLucene = false;

        // jsl - we decided not to use this but leaving the framework in place in case we want it
        // later, commenting out the check of the system config just in case
        /*
        Map<String,String> params = getRequestParam();
        String term = Util.getString(params.get(PARAM_ITEMS));

        SailPointContext context = getContext();
        Configuration config = context.getConfiguration();
        useLucene = Util.isNotNullOrEmpty(term) && getFullTextifier().isSearchEnabled()
                && config.getBoolean(Configuration.ENTITLEMENT_CATALOG_FULLTEXT);
        */
        
        return useLucene;
    }

    /**
     * Overload the BaseListBean.getRows method so we can decide whether to use Lucene.
     *
     * Filter building is based on AccountGroupListBean.getQueryOptionsFromRequest.
     * I thought about just calling that and unpacking the QueryOptions, but 
     * given the transformatinos we have to do it's easier to do directly to the
     * request parameters.  This does however mean that there are assumptions
     * about names of things that have to change in both places.
     *
     */
    public List<Map<String,Object>> getRows() throws GeneralException {

        if (_rows == null) {
            if (isLuceneEnabled()) {
                _rows = getRowsLucene();
            }
            else {
                // this will already set _row, but make it clear
                _rows = super.getRows();
            }
        }
        
        return _rows;
    }

    /**
     * Have to overload this too, the grid calls this first to display how
     * many pages there are.  We're not supporting pages, just read in 
     * the maximum and scroll over them.  
     */
    public int getCount() throws GeneralException {

        int count = 0;

        if (isLuceneEnabled()) {
            List rows = getRows();
            count  = rows.size();
        }
        else {
            count = super.getCount();
        }
        
        return count;
    }

    private List<Map<String,Object>> getRowsLucene()
        throws GeneralException {

        List<Map<String,Object>> result = null;
        
        Map<String,String> params = getRequestParam();
        String term = Util.getString(params.get(PARAM_ITEMS));
        
        List<Filter> filters = new ArrayList<Filter>();

        // this has a menu which would ordinarilly be used, but you can also
        // type in free form text, and the SQL generator will turn those into
        // a startsWith like query.  For Lucene, we're already getting startsWith for free
        // so just making these be .eq for now, need to revisist this
        String attribute = Util.getString(params.get(PARAM_ATTRIBUTE));
        if (attribute != null) {
            filters.add(Filter.eq("attribute", attribute));
        }

        // this one we don't actually show, at least not by default but it is in there
        String owner = Util.getString(params.get(PARAM_OWNER_ID));
        if (owner != null) {
            filters.add(Filter.eq("owner.id", owner));
        }
                        
        // also not in the default UI, it uses Filter.eq in SQL which is odd
        // since this is something you might actually want partial match on
        String dname = Util.getString(params.get(PARAM_NATIVE_ID));
        if (dname != null) {
            filters.add(Filter.eq("displayableName", dname));
        }

        String application = Util.getString(params.get(PARAM_APPLICATION));
        if (application != null) {
            filters.add(Filter.eq("application.id", application));
        }
                
        // permissions.target
        // SQL does a Filter.like("permissions.target", ...) which we can't do in Lucene
        // but if you have direct permission indexing on, you'll get these as TargetAssociations
        // which are automatically included as analyzed fields.  So this field isn't necessary
        // though if we display it, they may expect it to actually filter.  If we get pushed
        // into that it will have to be post processing.

        // permissions.rights
        // permissions.annotation
        // these aren't included in the Lucene index and aren't on by default, they are unlikely
        // to be used, but if necessary then you don't get Lucene

        String type = Util.getString(params.get(PARAM_TYPE));
        if (type != null) {
            if (PARAM_TYPE_ENTITLEMENTS.equals(type)) {
                // sql does an or with isnull, I don't think we need that any more
                filters.add(Filter.eq("type", ManagedAttribute.Type.Entitlement.name()));
            }
            else if (PARAM_TYPE_PERMISSIONS.equals(type)) {
                filters.add(Filter.eq("type", ManagedAttribute.Type.Permission.name()));
            }
            else {
                filters.add(Filter.eq("type", type));
            }
        }

        // also not shown in the default UI
        String requestable = Util.getString(params.get(PARAM_REQUESTABLE));
        if (requestable != null) {
            filters.add(Filter.eq("requestable", Util.otob(requestable)));
        }

        //classification
        String classification = Util.getString(params.get(PARAM_CLASSIFICATION));
        if (classification != null) {
            filters.add(Filter.eq("classifications.classification.id", classification));
        }
        

        // TODO: Scoping, see crap in AccountGroupListBean
        // we're not going to be handling workgroup ownership and assigned scopes in the same way

        FullTextifier ft = getFullTextifier();

        // we have only term from the "items" box
        List<String> terms = new ArrayList<String>();
        if (Util.isNotNullOrEmpty(term)) {
            terms.add(term);
        }

        // TODO: Not sure why the max results isn't in here instead
        // of the FullTextifier constructor?

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.and(filters));

        FullTextifier.SearchResult ftResult = ft.search(terms, ops);
        result = ftResult.rows;
            
        // UserAccessSearcher has an "enhancement" phase here
        fixRows(result);

        return result;
    }

    public static String FULL_TEXT_INDEX_NAME = "EntitlementSearch";
    /**
     * Copied from service/useraccess/UserAccessSearcher
     *
     * Build a FullTextifier object with suitable options.
     * Using the same one as LCM for now, will need to change it, maxResultCount is hard coded
     */
    protected FullTextifier getFullTextifier() throws GeneralException {
        if (_textifier == null) {
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
                max = DEFAULT_FULLTEXT_MAX_RESULT;
            }
            _textifier = new FullTextifier(context, index, max);
        }

        return _textifier;
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

                    // not calling our convertColumn method since the Entitlement Catalog
                    // doesn't allow any extended attributes?

                    // Lucene is sending back "t" but the UI needs "true", did this change?
                    // there is no data type in ColumnConfig, could use renderer but that's
                    // not necessarily stable
                    if ("requestable".equals(prop)) {
                        if ("t".equals(value)) {
                            row.put("requestable", "true");
                        }
                        else {
                            row.put("requestable", "false");
                        }
                    }

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


    
}
