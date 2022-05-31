/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.ScoreBandConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.certification.BulkCertificationHelper;
import sailpoint.web.group.GroupDefinitionListBean;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.NavigationHistory.Page;

/**
 * @author peter.holcomb
 *
 */
public class ScoreCategoryListBean extends BaseListBean implements Page {

    private static Log log = LogFactory.getLog(ScoreCategoryListBean.class);

    public static final String ATT_CATEGORY_ID = "ScoreCategoryId";
    public static final String ATT_SCORE_GROUP_CHOICE = "ScoreGroupChoice";
    public static final String ATT_SCORE_GROUP_COMPONENT_CHOICE = "ScoreGroupComponentChoice";
    public static final String ATT_SCORE_GROUP_FILTER = "ScoreGroupFilter";
    public static final String ATT_SCORE_GROUP_FILTER_NAME = "ScoreGroupFilterName";
    
    public static final String CATEGORY_NAME_HIGH_RISK = "High Risk";
    public static final String CATEGORY_NAME_MEDIUM_RISK = "Medium Risk";
    public static final String CATEGORY_NAME_LOW_RISK = "Low Risk";
    
    
    public static final String GRID_STATE = "scoreCategoryListGridState";

    List<ScoreCategoryBean> categories;
    int categoryId;
    String selectedCategoryName;

    int identityCount;
    List<ScoreDefinition> scoreDefs;
    private String groupChoice;
    private String componentChoice;
    private String filterName;  
    
    /** The count of all the scores for this category can take a long time to run
     * given a high number of identities and can keep the page from rendering quickly.
     * We want to calculate the count after the page renders.
     */
    private boolean showCount;
    
    private BulkCertificationHelper bulkCertification;

	private List<ColumnConfig> columns;

    /****************************************************************************
     *
     * Constructors/Helpers
     * 
     * ***************************************************************************/

    public ScoreCategoryListBean()
    {
        super();
        String reset = getRequestOrSessionParameter("reset");
        
        /** Flush the session if we are hitting this page from the menu **/
        if(reset!=null && reset.equals("true")) {
            clearSession();
        }
        restore();
        bulkCertification = new BulkCertificationHelper(getSessionScope(), getContext(), this);
    }

    public void initCategories()
    {
        categories = new ArrayList<ScoreCategoryBean>();
        try {

            ScoreConfig scoreConfig = (ScoreConfig)getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            List<ScoreBandConfig> configs = scoreConfig.getBands();
            for(Iterator<ScoreBandConfig> iter = configs.iterator(); iter.hasNext(); ) {
                ScoreBandConfig config = iter.next();
                String color = config.getColor();
                String label = config.getLabel();
                int minScore = config.getLowerBound();
                int maxScore = config.getUpperBound();
                categories.add(new ScoreCategoryBean(label, minScore, maxScore, color));
            }
        } catch (GeneralException ge) {
            log.error("GeneralException: [" + ge.getMessage() + "]");
        }
    }
    
    private void clearSession() {
        getSessionScope().remove(ATT_CATEGORY_ID);
        getSessionScope().remove(ATT_SCORE_GROUP_CHOICE);
        getSessionScope().remove(ATT_SCORE_GROUP_COMPONENT_CHOICE);
        getSessionScope().remove(ATT_SCORE_GROUP_FILTER_NAME);
    }

    @SuppressWarnings("unchecked")
    private void restore() {
        Map session = getSessionScope();
      
        // Retrieve category id. First, check the request, then the session
        
        String category = getRequestParameter("selectedCategoryName");
        if(category!=null) {
            session.put(ATT_CATEGORY_ID, category);
            setSelectedCategoryName(category);
        } else if (session.get(ATT_CATEGORY_ID) != null){
            category = (String)session.get(ATT_CATEGORY_ID);
        	setSelectedCategoryName(category);        	
        } else if (getRequestParameter("dashboardform:selectedCategory") != null){
            if (getRequestParameter("dashboardform:selectedCategory").contains(CATEGORY_NAME_HIGH_RISK)){
                setSelectedCategoryName(CATEGORY_NAME_HIGH_RISK);
            }else if (getRequestParameter("dashboardform:selectedCategory").contains(CATEGORY_NAME_MEDIUM_RISK)){
                setSelectedCategoryName(CATEGORY_NAME_MEDIUM_RISK);
            }else{
                setSelectedCategoryName(CATEGORY_NAME_LOW_RISK);
            }
        }

        Object o2 = session.get(ATT_SCORE_GROUP_CHOICE);
        if(o2 !=null && !(o2.toString().equals(""))) {
            groupChoice = o2.toString();
        }
        else
            groupChoice = null;

        Object o3 = session.get(ATT_SCORE_GROUP_COMPONENT_CHOICE);
        
        if(o3 !=null && !(o3.toString().equals(""))) {
            componentChoice = o3.toString();
        }
        else
            componentChoice = null;

        Object o4 = session.get(ATT_SCORE_GROUP_FILTER_NAME);
        if(o4 !=null)
            filterName = o4.toString();
        else
            filterName = null;
    }
    
    /**
     * jsl - this is simpler now, the filter is stored in the GroupDefinition
     * rather than the GroupIndex.
     * 
     * ph - need to get the group using it's group factory due to the fact that group
     * definitions can have the same name.
     */
    private Filter getGroupFilter(String groupDefName, String groupFactoryName) throws GeneralException{
        Filter filter = null;
        List<GroupDefinition> groups = null;
        QueryOptions qo = new QueryOptions();
        if(groupDefName!=null) {
            qo.add(Filter.eq("name", groupDefName));
            if(groupFactoryName!=null) {
                GroupFactory factory = getContext().getObjectByName(GroupFactory.class, groupFactoryName);
                if(factory!=null)
                    qo.add(Filter.eq("factory", factory));
                else
                    qo.add(Filter.isnull("factory"));    
            }
            groups = getContext().getObjects(GroupDefinition.class, qo);
        }
        if(groups!=null && !groups.isEmpty()) {
            filter = groups.get(0).getFilter();
        }
        return filter;
    }


    /****************************************************************************
     *
     * ACTIONS
     * 
     * ***************************************************************************/

    /** An ajax call that forces the chosen category to run its count query **/
    public String showCategoryCount() 
    {
    	this.showCount = true;
    	return "showCount";
    }
    
    @SuppressWarnings("unchecked")
    public String showScores() throws GeneralException
    {
        getSessionScope().put(ATT_SCORE_GROUP_CHOICE, groupChoice);
        getSessionScope().put(ATT_SCORE_GROUP_COMPONENT_CHOICE, componentChoice);
        return "showScores";
    }

    @SuppressWarnings("unchecked")
    public String applyFilter() throws GeneralException {
        getSessionScope().put(ATT_CATEGORY_ID, selectedCategoryName);
        getSessionScope().put(ATT_SCORE_GROUP_CHOICE, groupChoice);
        getSessionScope().put(ATT_SCORE_GROUP_COMPONENT_CHOICE, componentChoice);
        if((groupChoice!=null && !(groupChoice.equals(""))) && (componentChoice !=null && !(componentChoice.equals("")))) {
            getSessionScope().put(ATT_SCORE_GROUP_FILTER, getGroupFilter(componentChoice, groupChoice));
            filterName = new String(groupChoice + " - " + componentChoice);
            getSessionScope().put(ATT_SCORE_GROUP_FILTER_NAME, filterName);
        } else
        {
            getSessionScope().put(ATT_SCORE_GROUP_FILTER, null);
            getSessionScope().put(ATT_SCORE_GROUP_FILTER_NAME, null);
        }
        return "applyFilter";
    }

    @SuppressWarnings("unchecked")
    public String updateChoices() {
        getSessionScope().put(ATT_SCORE_GROUP_CHOICE, groupChoice);
        return null;
    }

    @SuppressWarnings("unchecked")
    public String reset() {
        this.groupChoice = null;
        this.componentChoice = null;
        this.filterName = null;
        getSessionScope().put(ATT_SCORE_GROUP_FILTER_NAME, filterName);
        getSessionScope().put(ATT_SCORE_GROUP_CHOICE, groupChoice);
        getSessionScope().put(ATT_SCORE_GROUP_COMPONENT_CHOICE, componentChoice);
        getSessionScope().put(ATT_SCORE_GROUP_FILTER, null);
        return null;
    }

    /****************************************************************************
     *
     * Getters/Setters
     * 
     * ***************************************************************************/

    public List<ColumnConfig> getColumns() 
	    throws GeneralException {
	
	    if (columns == null) 
	    	columns = super.getUIConfig().getRiskScoreTableColumns();

	    return columns;
	}
    
    @Override 
    public int getCount() throws GeneralException {
        ScoreCategoryBean category = getCategory();
        if (category == null) {
            return 0;
        } else {
            return category.getCount();
        } 
    }
    
    @Override
    public List<Map<String,Object>> getRows() throws GeneralException {
        ScoreCategoryBean category = getCategory();
        if (category == null) {
            return new ArrayList<Map<String, Object>>();
        } else {
            return category.getRows();
        }
    }
    
    /**
     * @return the categories
     */
    public List<ScoreCategoryBean> getCategories() {
        if(categories==null)
            initCategories();        
        return categories;
    }

    /**
     * Gets the currently selected category. If no category has been selected,
     * it takes the first category from the list.
     *
     * @return Currently selected category
     */
    public ScoreCategoryBean getCategory() {
        ScoreCategoryBean category = null;
        if(categories==null)
            initCategories();

        if(getSelectedCategoryName()==null)
            category = categories.get(0);

        for(Iterator<ScoreCategoryBean> iter = categories.iterator(); iter.hasNext(); ) {
            ScoreCategoryBean tempCategory = iter.next();
            if(tempCategory.getCategoryName().equals(getSelectedCategoryName())){
                category = tempCategory;
            }
        }
        if(category==null)
            category = categories.get(0);
        return category;
    }

    public List<String> getColors() {
        List<String> colors = new ArrayList<String>();

        if(categories==null)
            initCategories();  
        for(Iterator<ScoreCategoryBean> iter = categories.iterator(); iter.hasNext(); ) {
            ScoreCategoryBean category = iter.next();
            colors.add(category.getColor());
        }
        return colors;
    }

    public String getColorsAsString() {
        List<String> colors = getColors();
        StringBuffer sb = new StringBuffer();
        for(int i=0; i<colors.size(); i++)
        {
            sb.append(colors.get(i));
            if(i<(colors.size()-1))
                sb.append(",");
        }
        return sb.toString();
    }

    public List<ScoreDefinition> getScoreDefinitions() {
        scoreDefs = new ArrayList<ScoreDefinition>();
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
            scoreDefs = config.getIdentityScores();
            for(Iterator<ScoreDefinition> iter = scoreDefs.iterator(); iter.hasNext(); )
            {
                ScoreDefinition scoreDef = iter.next();
                if(scoreDef.isDisabled() || scoreDef.getWeight()<=0)
                    iter.remove();                    
            }

        } catch (GeneralException ge)
        {
            log.error("GeneralException Encountered: [ " + ge.getMessage() + "]");
            new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM,
                    new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM));
            //setRenderHeaderMsg(true);
        }
        return scoreDefs;
    }

    public int getIdentityCount() throws GeneralException{
        if(identityCount<=0)
        {
            QueryOptions ops = super.getQueryOptions();
            
            Filter groupFilter = (Filter)getSessionScope().get(ScoreCategoryListBean.ATT_SCORE_GROUP_FILTER);
            if(groupFilter!=null) {
                ops.add(groupFilter);
            }
            identityCount = getContext().countObjects(Identity.class, ops);
        }
        return identityCount;
    }

    public int getLargestCategorySize() throws GeneralException{
        int largestCount = 0;
        if(categories==null)
            initCategories();
        for(int i=0; i<categories.size(); i++)
        {
            int count = ((ScoreCategoryBean)categories.get(i)).getCount();
            if(count > largestCount)
                largestCount = count;
        }

        return largestCount;
    }


    /**
     * @param categories the categories to set
     */
    public void setCategories(List<ScoreCategoryBean> categories) {
        this.categories = categories;
    }

    /**
     * @return the selectedCategory
     */
    public String getSelectedCategoryName() {
        if(selectedCategoryName == null) {
            /** Pick the first category **/
            if(getCategories()!=null && !getCategories().isEmpty()) {
                ScoreCategoryBean category = getCategories().get(0);
                return category.getCategoryName();
            }
        }
        return selectedCategoryName;
    }

    /**
     * @param selectedCategoryName the selectedCategory to set
     */
    public void setSelectedCategoryName(String selectedCategoryName) {
        this.selectedCategoryName = selectedCategoryName;
    }

    /**
     * @return the componentChoice
     */
    public String getComponentChoice() {
        return componentChoice;
    }

    /**
     * @param componentChoice the componentChoice to set
     */
    public void setComponentChoice(String componentChoice) {
        this.componentChoice = componentChoice;
    }

    /**
     * @return the groupChoice
     */
    public String getGroupChoice() {
        return groupChoice;
    }

    /**
     * @param groupChoice the groupChoice to set
     */
    public void setGroupChoice(String groupChoice) {        
        this.groupChoice = groupChoice;
    }

    /**
     * Given a groupChoice (the name of a GroupFactory) return the
     * list of all GroupDefinitions generated by that factory.
     *
     * @return the componentOptions
     * @throws GeneralException
     */
    public SelectItem[] getComponentOptions() throws GeneralException{
        GroupDefinitionListBean defBean = new GroupDefinitionListBean();
        return defBean.getDefinitionOptions(groupChoice, true);
    }

    /**
     * @return the filterName
     */
    public String getFilterName() {
        return filterName;
    }

    /**
     * @param filterName the filterName to set
     */
    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }
    
    public BulkCertificationHelper getBulkCertification() {
        return bulkCertification;
    }
    
    public String scheduleBulkCertificationAction() throws GeneralException {
        List<Map<String, String>> availableIdentities = new ArrayList<>();
        
        try {
            List<Identity> identities = getCategory().getObjects();
            for (Identity identity : identities) {
                Map<String, String> identityAttrs = new HashMap<>();
                identityAttrs.put("id", identity.getId());
                identityAttrs.put("name", identity.getName());
                availableIdentities.add(identityAttrs);
            }
        } catch (GeneralException e) {
            log.error("The identities for the risk score page cannot be fetched at this time.", e);
        }
        
        return getBulkCertification().scheduleBulkCertificationAction(availableIdentities, getLoggedInUser());
    }

    /**
     * Indicates that the user has the right to schedule certifications or is a sys admin.
     * Used to hide or show the Schedule Certifications button on the identity risk scores page.
     * @return
     */
    public boolean isAllowScheduleCertifications(){

        if (Capability.hasSystemAdministrator(getLoggedInUserCapabilities()))
            return true;

        return getLoggedInUserRights() != null ?
                getLoggedInUserRights().contains(SPRight.FullAccessCertificationSchedule) : false;
    }
    
    public String select() throws GeneralException {
        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            // can get here by pressing return in the filter box without
            // clicking on the Search button, which I guess makes it
            // look like a click in the live grid
            next = null;
        }
        else {
            next = IdentityDTO.createNavString(Consts.NavigationString.edit.name(), selected);
        }

        NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);

        return next;
    }  
    
    // Navigation History methods
    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = selectedCategoryName;
        return state;
    }

    public String getNavigationString() {
        return "showScores";
    }

    public String getPageName() {
        return "Risk Score Categories";
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setSelectedCategoryName((String) myState[0]);
    }

	public boolean isShowCount() {
		return showCount;
	}

	public void setShowCount(boolean showCount) {
		this.showCount = showCount;
	}
	
	public String getGridStateName() {
		return GRID_STATE;
	}
}
