/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.web.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.NavigationHistory;

public class AdvancedAccountGroupSearchBean extends AccountGroupSearchBean
        implements Serializable, NavigationHistory.Page{

    private static final long serialVersionUID = 1196859611651387845L;
    private static final Log log = LogFactory.getLog(AdvancedAccountGroupSearchBean.class);

    private static final String ATT_ADV_ACCOUNT_GROUP_SEARCH_ITEM = "AdvancedAccountGroupSearchItem";
    private static final String GRID_STATE = "advancedAccountGroupSearchGridState";

    /** A delegated advanced search bean in charge of all of the generalized search operations
     * This bean is instantiated by all of the types of advanced searches. */
    private transient AdvancedSearchBean searchBean;

    public AdvancedAccountGroupSearchBean()
    {
        super();
        restore();
    }

    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }

    protected void restore() {
        Map session = getSessionScope();
        Object o = session.get(ATT_ADV_ACCOUNT_GROUP_SEARCH_ITEM);
        if (o != null) {
            setSearchItem((SearchItem)o);
            setSelectedAccountGroupFields(getSearchItem().getAccountGroupFields());
        }
        else {
            setSearchItem(new SearchItem());
            setSelectedAccountGroupFields(getDefaultFieldList());
        }

        setSearchType(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP);
        setSearchItemName(getSearchItem().getName());
        setSelectedSearchItemName(getSearchItem().getName());
        setSearchItemDescription(getSearchItem().getDescription());
        taskMonitor = (ReportExportMonitor) session.get(EXPORT_MONITOR);

        searchBean =
                new AdvancedSearchBean(getSearchItem(), getInputs(), getSearchType(), ATT_ADV_ACCOUNT_GROUP_SEARCH_ITEM);
        searchBean.setGlobalBooleanOp(getSearchItem().getOperation());
    }

    protected void save() {
        if(getSearchItem() == null) {
            setSearchItem(new SearchItem());
        }
        log.debug("Filter Beans: " + searchBean.getFilterBeans());
        searchBean.saveFilterBeans(getSearchItem());
        getSearchItem().setOperation(searchBean.getGlobalBooleanOp());
        getSearchItem().setType(SearchItem.Type.AdvancedAccountGroup);
        setFields();
        getSessionScope().put(ATT_ADV_ACCOUNT_GROUP_SEARCH_ITEM, getSearchItem());
    }

    @Override
    protected void clearSession() {
        log.debug("[clearSession]");
        getSessionScope().remove(ATT_ADV_ACCOUNT_GROUP_SEARCH_ITEM);
        super.clearSession();
    }

    @Override
    public String clearSearchItem() {
        log.debug("[clearSearchItem]");
        clearSession();
        setSearchItem(null);
        setSelectedSearchItemName(null);
        restore();
        return "clearSearchItem";
    }

    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP;
    }

    /**
     * @return The String used to prefix identifiers to make them unique across shared advanced search pages.
     */
    public String getSearchPrefix() {
        return "advAccountGroupSearch";
    }

    /**
     * @return The base type that this Advanced search bean builds upon.
     */
    public String getBaseSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_ACCOUNT_GROUP;
    }

    /**
     * @return the searchBean
     */
    public AdvancedSearchBean getSearchBean() {
        if (searchBean == null) {
            searchBean = new AdvancedSearchBean();
            searchBean.setSearchType(ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP);
        }
        return searchBean;
    }

    /**
     * @param searchBean the searchBean to set
     */
    public void setSearchBean(AdvancedSearchBean searchBean) {
        this.searchBean = searchBean;
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        ops.setDistinct(true);
        return ops;
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP);
        return allowableTypes;
    }

    @Override
    public Map<String, SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> inputMap = super.buildInputMap();

        for (String key : inputMap.keySet()) {
            if (key.startsWith(ATT_IDT_SEARCH_MA_HTML_PREFIX)) {
                // For Advanced Search, update name to have underscore instead of dot.
                // This allows us to look it up later if selected.
                SearchInputDefinition value = inputMap.get(key);
                value.setName(key);
            }
        }

        return inputMap;
    }
}
