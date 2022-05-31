/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the consolodated application risk scorecard.
 *
 * Author: Jeff
 * 
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.ApplicationScorecard;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.QueryOptions;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory.Page;
import sailpoint.web.util.WebUtil;

public class ApplicationRiskBean extends BaseListBean<Application> implements Page {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ApplicationRiskBean.class);

    public static final String ATT_CATEGORY_ID = "ScoreCategoryId";
    public static final String ATT_SCORE_GROUP_CHOICE = "ScoreGroupChoice";
    public static final String ATT_SCORE_GROUP_COMPONENT_CHOICE = "ScoreGroupComponentChoice";
    public static final String ATT_SCORE_GROUP_FILTER = "ScoreGroupFilter";
    public static final String ATT_SCORE_GROUP_FILTER_NAME = "ScoreGroupFilterName";
    
    private static String GRID_STATE = "applicationScoresListGridState";

    ScoreConfig _config;
    List<ScoreDefinition> _componentScores;
    List<ColumnConfig> _columns;
    boolean _scorecardsChecked;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ApplicationRiskBean() {

        super();
        setScope(Application.class);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Navigation History
    //
    //////////////////////////////////////////////////////////////////////

    public Object calculatePageState() {
        return null;
    }

    /**
     * This will become the outcome of a save/cancel operation
     * from the Application edit page if we navigated there from
     * the application risk scorcard.
     */
    public String getNavigationString() {
        return "showApplicationScores";
    }

    public String getPageName() {
        return "Application Risk Scores";
    }

    public void restorePageState(Object state) {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        // Application list is scoped by owner and scope.
        QueryOptions qo = super.getQueryOptions();
        qo.add(Filter.eq("noAggregation", false));
        
        getQueryOptionsFromRequest(qo);
        
        return qo;
    }
    
    /** Retrieves any passed in filters from the request **/
    public void getQueryOptionsFromRequest(QueryOptions qo) throws GeneralException
    {
        if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals(""))
            qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), MatchMode.START)));
        
    }
    

    @Override
    public Map<String,String> getSortColumnMap()
    {
        Map<String,String> sortCols = new HashMap<String,String>();
        
        sortCols.put("application", "name");
        sortCols.put("composite", "scorecard.compositeScore");
        
        return sortCols;
    }
    
    
    /**
     * Return the Application list but make sure they all
     * have scorecards.  Had trouble overloading getObjects,
     * probably something to do with the type qualifier.
     */
    public List<Application> getApplications() throws GeneralException {

        List<Application> apps = super.getObjects();
        if (apps != null && !_scorecardsChecked) {
            for (Application app : apps) {
                if (app.getScorecard() == null)
                    app.setScorecard(new ApplicationScorecard());
            }
            _scorecardsChecked = true;
        }
        return apps;
    }

    /**
     * Override to get the JSON dynamically fronm configured component scores
     */
    @Override
    public String getGridResponseJson() throws GeneralException {
        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, Object>> appRows = new ArrayList<Map<String, Object>>();
        response.put("totalCount", getCount());
        response.put("appRiskScores", appRows);
        
        List<Application> applications = getApplications();
        List<ScoreDefinition> componentScores = getComponentScores();
        if (!Util.isEmpty(applications)) {
            for (Application application : applications) {
                Map<String, Object> appRow = new HashMap<String, Object>();
                appRow.put("id", application.getId());
                appRow.put("application", application.getName());
                //We made sure all applications had scorecards in getApplications();
                ApplicationScorecard scorecard = application.getScorecard();
                appRow.put("composite", getScoreMap(scorecard.getCompositeScore()));
                if (!Util.isEmpty(componentScores)) {
                    Attributes<String, Object> attributes = scorecard.getAttributes();
                    for (ScoreDefinition componentScore : componentScores) {
                        appRow.put(componentScore.getName(), 
                                getScoreMap(attributes == null ? 0 : attributes.getInt(componentScore.getName())));
                    }
                }
                appRows.add(appRow);
            }
        }
        return JsonHelper.toJson(response);
    }
    
    private Map<String, Object> getScoreMap(int score) throws GeneralException {
        Map<String, Object> scoreMap = new HashMap<String, Object>();
        scoreMap.put("score", score);
        scoreMap.put("color", WebUtil.getScoreColor(score));
        return scoreMap;
    }


    public ScoreConfig getScoreConfig() throws GeneralException {

        if (_config == null) {
            _config = getContext().getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
            if (_config == null) {
                // shouldn't happen
                _config = new ScoreConfig();
            }
        }
        return _config;
    }

    public List<ScoreDefinition> getComponentScores()
        throws GeneralException {

        if (_componentScores == null) {
            _componentScores = new ArrayList<ScoreDefinition>();
            ScoreConfig config = getScoreConfig();
            List<ScoreDefinition> scores = config.getApplicationScores();
            if (scores != null) {
                for (ScoreDefinition score : scores) {
                    // unlike identity scores these aren't explicitly
                    // marked since we don't have the raw/compensated dichotomy
                    if (!score.isDisabled() && !score.isComposite())
                        _componentScores.add(score);
                }
            }
        }
        return _componentScores;
    }
    
    public String getDefaultSortColumn() throws GeneralException {

        return "name";
    }

    /**
     * Return the LiveGrid column titles.
     * We have a hidden id column zero, followed by a checkbox column.
     */
    public List<ColumnConfig> getColumns()
        throws GeneralException {

        if (_columns == null) {
            _columns = new ArrayList<ColumnConfig>();

            // the fixed data columns
            ColumnConfig app = new ColumnConfig(getMessage(MessageKeys.APPLICATION), "application", 0);
            app.setSortProperty("name");
            app.setFlex(1);
            _columns.add(app);
            
            ColumnConfig composite = new ColumnConfig(getMessage(MessageKeys.COMPOSITE), "composite", 0);
            composite.setSortProperty("scorecard.compositeScore");
            composite.setRenderer("SailPoint.Manage.Grid.AppRiskScores.renderScore");
            _columns.add(composite);

            List<ScoreDefinition> scores = getComponentScores();
            if (scores != null) {
                for (ScoreDefinition score : scores) {
                    ColumnConfig config = new ColumnConfig(getMessage(score.getDisplayableName()), score.getName(), 0);
                    config.setRenderer("SailPoint.Manage.Grid.AppRiskScores.renderScore");
                    // these aren't currently sortable from the UI, so...
                    config.setSortable(false);
                    
                    _columns.add(config);
                }
            }
        }

        return _columns;
    }

    public int getColumnCount() throws GeneralException {
        return getColumns().size();
    }
    
    public String getGridStateName() {
        return GRID_STATE;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Like ApplicationListBean we have to clear out some lingering
     * session state.
     */
    @SuppressWarnings("unchecked")
    @Override
    public String select() throws GeneralException {
        getSessionScope().put(ApplicationObjectBean.CURRENT_TAB, null);
        //Append appId to the navigation token so SailPointNavigationHandler can pick it up
        String nav = super.select();
        if(nav != null) {
            return nav+"?appId="+getSelectedId();
        }
        else {
            return nav;
        }
    }
}
