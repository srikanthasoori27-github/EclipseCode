/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.SearchItem;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.analyze.SearchUtil;

/**
 * @author peter.holcomb
 *
 */
public class AdvancedActivitySearchBean extends ActivitySearchBean implements Serializable {

    private static final long serialVersionUID = 1196859611651387845L;
    public static final String ATT_ADV_ACT_SEARCH_ITEM = "AdvancedActivitySearchItem";
    private static final Log log = LogFactory.getLog(AdvancedIdentitySearchBean.class);
    
    /** A delegated advanced search bean in charge of all of the generalized search operations
     * This bean is instantiated by all of the types of advanced searches. */
    private AdvancedSearchBean searchBean;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////



    public AdvancedActivitySearchBean() 
    { 
        super();
        restore();
    }

    protected void restore() {
        log.debug("[restore]");
        Map session = getSessionScope();
        Object o = session.get(ATT_ADV_ACT_SEARCH_ITEM);
        if (o != null) {
            setSearchItem((SearchItem)o);
        }
        else {
            setSearchItem(new SearchItem());
        }
        
        /** Instantiate Search Bean **/
        restoreSearchBean();        

        /** Instantiate Base Activity Search Bean **/
        setSelectedActivityFields(getSearchItem().getActivityFields());
        setSearchItemName(getSearchItem().getName());
        setSelectedSearchItemName(getSearchItem().getName());
        setSearchItemDescription(getSearchItem().getDescription());
        taskMonitor = (ReportExportMonitor) session.get(EXPORT_MONITOR);
        try {
            if(getCurrentUser()==null) 
                setCurrentUser(getLoggedInUser());
        } catch (GeneralException ge) {
            log.error("Unable to get current user.  Exception: " + ge.getMessage());
        }
        setSearchType(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACT);
    }
    
    public void restoreSearchBean() {
        searchBean = 
            new AdvancedSearchBean(getSearchItem(), getInputs(), getSearchType(), ATT_ADV_ACT_SEARCH_ITEM);        
        searchBean.setGlobalBooleanOp(getSearchItem().getOperation());
    }

    @SuppressWarnings("unchecked")
    protected void save() throws GeneralException{
        if(getSearchItem()==null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.Activity);
        setFields();
        getSessionScope().put(ATT_ADV_ACT_SEARCH_ITEM, getSearchItem());
    }

    @Override
    protected void clearSession() {
        log.debug("[clearSession]");
        getSessionScope().remove(ATT_ADV_ACT_SEARCH_ITEM);
        super.clearSession();        
    }
    
    

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    @Override
    public String loadSearchItem() {
        log.debug("[loadSearchItem]");
        List<SearchItem> searchItems = SearchUtil.getAllMySearchItems(this);
        if(searchItems!=null && getSelectedSearchItemName()!=null) {
            for(SearchItem searchItem : searchItems) {
                if(searchItem.getName()!=null && searchItem.getName().equals(getSelectedSearchItemName())) {
                    getSessionScope().put(ATT_ADV_ACT_SEARCH_ITEM, searchItem);
                }
            }
        }
        restore();
        return "loadSearchItem";
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
        return SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACT;
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_ADVANCED_ACT);
        return allowableTypes;
    }

    /**
     * @return the filterValue
     */
    public String getFilterValue() {
        String filterValue = null;
        if(searchBean!=null) {
            filterValue = searchBean.getFilterValue();
        }
        return filterValue;
    }

    /**
     * @return the searchBean
     */
    public AdvancedSearchBean getSearchBean() {
    	if(searchBean==null)
			searchBean = new AdvancedSearchBean();
        return searchBean;
    }

    /**
     * @param searchBean the searchBean to set
     */
    public void setSearchBean(AdvancedSearchBean searchBean) {
        this.searchBean = searchBean;
    }


    @Override
	public boolean preValidateSearch() {
		return true;
	}
}
