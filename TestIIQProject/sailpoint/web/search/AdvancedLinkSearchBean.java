/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.search;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchItem;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.analyze.SearchUtil;
import sailpoint.web.util.NavigationHistory;

/**
 * Advanced search bean for accounts
 */
public class AdvancedLinkSearchBean extends LinkSearchBean implements Serializable, NavigationHistory.Page {

    private static final Log log = LogFactory.getLog(AdvancedLinkSearchBean.class);

    private static final String ATT_ADV_LINK_SEARCH_ITEM = "AdvancedLinkSearchItem";
    private static final String GRID_STATE = "advancedLinkSearchGridState";
    private static final long serialVersionUID = -5990370442270331890L;

    /**
     * A delegated advanced search bean in charge of all of the generalized search operations
     * This bean is instantiated by all of the types of advanced searches.
     */
    private transient AdvancedSearchBean searchBean;

    public AdvancedLinkSearchBean() {
        super();
        restore();
    }

    protected void restore() {
        Map session = getSessionScope();
        Object o = session.get(ATT_ADV_LINK_SEARCH_ITEM);
        if (o != null) {
            setSearchItem((SearchItem)o);
            setSelectedLinkFields(getSearchItem().getLinkFields());
        }
        else {
            setSearchItem(new SearchItem());
            setSelectedLinkFields(getDefaultFieldList());
        }

        setSearchType(SearchBean.ATT_SEARCH_TYPE_ADVANCED_LINK);
        setSearchItemName(getSearchItem().getName());
        setSelectedSearchItemName(getSearchItem().getName());
        setSearchItemDescription(getSearchItem().getDescription());
        taskMonitor = (ReportExportMonitor) session.get(EXPORT_MONITOR);

        searchBean =
                new AdvancedSearchBean(getSearchItem(), getInputs(), getSearchType(), ATT_ADV_LINK_SEARCH_ITEM);
        searchBean.setGlobalBooleanOp(getSearchItem().getOperation());
        searchBean.setExcludeDateEquals(true);
    }

    protected void save() {
        if(getSearchItem() == null) {
            setSearchItem(new SearchItem());
        }
        log.debug("Filter Beans: " + searchBean.getFilterBeans());
        searchBean.saveFilterBeans(getSearchItem());
        getSearchItem().setOperation(searchBean.getGlobalBooleanOp());
        getSearchItem().setType(SearchItem.Type.AdvancedLink);
        setFields();
        getSessionScope().put(ATT_ADV_LINK_SEARCH_ITEM, getSearchItem());
    }

    @Override
    public String getGridStateName() {
        return GRID_STATE;

    }

    @Override
    protected void clearSession() {
        log.debug("[clearSession]");
        getSessionScope().remove(ATT_ADV_LINK_SEARCH_ITEM);
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
        return SearchBean.ATT_SEARCH_TYPE_ADVANCED_LINK;
    }

    /**
     * @return The String used to prefix identifiers to make them unique across shared advanced search pages.
     */
    public String getSearchPrefix() {
        return "advLinkSearch";
    }

    /**
     * @return The base type that this Advanced search bean builds upon.
     */
    public String getBaseSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_LINK;
    }

    /**
     * @return the searchBean
     */
    public AdvancedSearchBean getSearchBean() {
        if (searchBean == null) {
            searchBean = new AdvancedSearchBean();
            searchBean.setSearchType(ATT_SEARCH_TYPE_ADVANCED_LINK);
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
        allowableTypes.add(ATT_SEARCH_TYPE_ADVANCED_LINK);
        return allowableTypes;
    }
}
