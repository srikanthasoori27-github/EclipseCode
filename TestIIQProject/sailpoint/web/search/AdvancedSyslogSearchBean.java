/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.web.search;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.GridState;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchItem;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.NavigationHistory;

public class AdvancedSyslogSearchBean extends SyslogSearchBean
        implements Serializable, NavigationHistory.Page {

    private static final Log log = LogFactory.getLog(AdvancedSyslogSearchBean.class);

    private static final String ATT_ADV_SYSLOG_SEARCH_ITEM = "AdvancedSyslogSearchItem";
    private static final String GRID_STATE = "advancedSyslogSearchGridState";

    /** A delegated advanced search bean in charge of all of the generalized search operations
     * This bean is instantiated by all of the types of advanced searches. */
    private transient AdvancedSearchBean searchBean;

    public AdvancedSyslogSearchBean()
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
        Object o = session.get(ATT_ADV_SYSLOG_SEARCH_ITEM);
        if (o != null) {
            setSearchItem((SearchItem)o);
            setSelectedSyslogFields(getSearchItem().getSyslogFields());
        }
        else {
            setSearchItem(new SearchItem());
            setSelectedSyslogFields(getDefaultFieldList());
        }

        setSearchType(SearchBean.ATT_SEARCH_TYPE_ADVANCED_SYSLOG);
        setSearchItemName(getSearchItem().getName());
        setSelectedSearchItemName(getSearchItem().getName());
        setSearchItemDescription(getSearchItem().getDescription());
        taskMonitor = (ReportExportMonitor) session.get(EXPORT_MONITOR);

        searchBean =
                new AdvancedSearchBean(getSearchItem(), getInputs(), getSearchType(), ATT_ADV_SYSLOG_SEARCH_ITEM);
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
        getSearchItem().setType(SearchItem.Type.AdvancedSyslog);
        setFields();
        getSessionScope().put(ATT_ADV_SYSLOG_SEARCH_ITEM, getSearchItem());
    }

    @Override
    protected void clearSession() {
        log.debug("[clearSession]");
        getSessionScope().remove(ATT_ADV_SYSLOG_SEARCH_ITEM);
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
        return SearchBean.ATT_SEARCH_TYPE_ADVANCED_SYSLOG;
    }

    /**
     * @return The String used to prefix identifiers to make them unique across shared advanced search pages.
     */
    public String getSearchPrefix() {
        return "advSyslogSearch";
    }

    /**
     * @return The base type that this Advanced search bean builds upon.
     */
    public String getBaseSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_SYSLOG;
    }

    /**
     * @return the searchBean
     */
    public AdvancedSearchBean getSearchBean() {
        if (searchBean == null) {
            searchBean = new AdvancedSearchBean();
            searchBean.setSearchType(ATT_SEARCH_TYPE_ADVANCED_SYSLOG);
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
        allowableTypes.add(ATT_SEARCH_TYPE_ADVANCED_SYSLOG);
        return allowableTypes;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Syslog Search Page";
    }

    public String getNavigationString() {
        return "syslogSearchResults";
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
}
