/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.SearchItem;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.analyze.SearchUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class AdvancedIdentitySearchBean extends IdentitySearchBean 
implements Serializable, NavigationHistory.Page{

	private static final long serialVersionUID = 1196859611651387845L;
	public static final String ATT_ADV_IDT_SEARCH_ITEM = "AdvancedIdentitySearchItem";
	private static final Log log = LogFactory.getLog(AdvancedIdentitySearchBean.class);
	
    private static final String GRID_STATE = "advancedIdentitySearchGridState";

	/** A delegated advanced search bean in charge of all of the generalized search operations
	 * This bean is instantiated by all of the types of advanced searches. */
	private transient AdvancedSearchBean searchBean;

	//////////////////////////////////////////////////////////////////////
	//
	// Constructor
	//
	//////////////////////////////////////////////////////////////////////

	public AdvancedIdentitySearchBean() 
	{ 
		super();
		restore();
	}

	@Override
    public String getGridStateName() {
        return GRID_STATE;
    }

	@SuppressWarnings("rawtypes")
    protected void restore() {
		//log.debug("[restore]");
		Map session = getSessionScope();
		Object o = session.get(ATT_ADV_IDT_SEARCH_ITEM);
		if (o != null) {
			setSearchItem((SearchItem)o);
			setSelectedIdentityFields(getSearchItem().getIdentityFields());
		}
		else {
			setSearchItem(new SearchItem());
			ArrayList<String> selectedIdentityFields = new ArrayList<String>();
			/** Add Username by default **/
			selectedIdentityFields.add("userName");
			setSelectedIdentityFields(selectedIdentityFields);
		}
		
		/** Instantiate Search Bean **/
		restoreSearchBean();

		/** Instantiate Base Identity Search Bean **/
		
        setSearchType(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT);
		setSelectedRiskFields(getSearchItem().getRiskFields());
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
	}

	public void restoreSearchBean() {
		searchBean = 
			new AdvancedSearchBean(getSearchItem(), getInputs(), getSearchType(), ATT_ADV_IDT_SEARCH_ITEM);        
		searchBean.setGlobalBooleanOp(getSearchItem().getOperation());
	}

	@SuppressWarnings("unchecked")
	protected void save() {
		if(getSearchItem()==null) 
			setSearchItem(new SearchItem());
		log.debug("Filter Beans: " + searchBean.getFilterBeans());
		searchBean.saveFilterBeans(getSearchItem());
		getSearchItem().setOperation(searchBean.getGlobalBooleanOp());
		getSearchItem().setType(SearchItem.Type.AdvancedIdentity);
		setFields();
		getSessionScope().put(ATT_ADV_IDT_SEARCH_ITEM, getSearchItem());
	}

	// TODO: The saveQueryAction() has moved to the AnalyzeControllerBean.  Figure out a good way to save
	// this bean and call the other
//	public String saveQueryAction() {
//		save();
//		return super.saveQueryAction();
//	}

	@Override
	protected void clearSession() {
		log.debug("[clearSession]");
		getSessionScope().remove(ATT_ADV_IDT_SEARCH_ITEM);
		super.clearSession();        
	}

	////////////////////////////////////////////////////////////////////////////
	//
	// NavigationHistory.Page methods
	//
	////////////////////////////////////////////////////////////////////////////

	public String getPageName() {
		return "Advanced Identity Search Page";
	}

	public String getNavigationString() {
		return "advancedSearchResults";
	}

	public Object calculatePageState() {
		return super.calculatePageState();
	}

	public void restorePageState(Object state) {
		super.restorePageState(state);
	}


	//////////////////////////////////////////////////////////////////////
	//
	// Actions
	//
	//////////////////////////////////////////////////////////////////////

	@Override
    @SuppressWarnings("unchecked")
	public String loadSearchItem() {
		log.debug("[loadSearchItem]");
		List<SearchItem> searchItems = SearchUtil.getAllMySearchItems(this);
		if(searchItems!=null && getSelectedSearchItemName()!=null) {
			for(SearchItem searchItem : searchItems) {
				if(searchItem.getName()!=null && searchItem.getName().equals(getSelectedSearchItemName())) {
					getSessionScope().put(ATT_ADV_IDT_SEARCH_ITEM, searchItem);
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
		return SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT;
	}

	/**
	 * @return The String used to prefix identifiers to make them unique across shared advanced search pages.
	 */
	public String getSearchPrefix() {
		return "advIdentitySearch";
	}

	/**
	 * @return The base type that this Advanced search bean builds upon.
	 */
	public String getBaseSearchType() {
		return SearchBean.ATT_SEARCH_TYPE_IDENT;
	}

	/**
	 * List of allowable definition types that should be taken into
	 * account when building filters Should be overridden.*/
	@Override
	public List<String> getAllowableDefinitionTypes() {
		List<String> allowableTypes = super.getAllowableDefinitionTypes();
		allowableTypes.add(ATT_SEARCH_TYPE_ADVANCED_IDENT);
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
		if(searchBean==null) {
			searchBean = new AdvancedSearchBean();
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
	public boolean preValidateSearch() {
		return true;
	}
	
	@Override
	public String getCriteriaHelpMsg() {
	    final String msg = WebUtil.localizeMessage(MessageKeys.HELP_SEARCH_FILTERS, WebUtil.localizeMessage(MessageKeys.IDENTITIES));
        return msg;
	}
	
	@Override
	public String getSearchItemId() {
	    return ATT_ADV_IDT_SEARCH_ITEM;
	}
	
    @Override
    @SuppressWarnings("unchecked")
	public String select() throws GeneralException {
        String result = super.select();
        getSessionScope().put(AnalyzeControllerBean.CURRENT_CARD_PANEL, AnalyzeControllerBean.ADVANCED_IDENTITY_SEARCH_RESULTS);
        return result;
	}

}
