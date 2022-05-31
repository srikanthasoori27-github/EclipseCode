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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class ScoreCategoryBean extends BaseListBean<Identity> {

    private static Log log = LogFactory.getLog(ScoreCategoryBean.class);

    public static final String ATT_CATEGORY_ID = "ScoreCategoryId";
    
    private String categoryName;
    private int minScore;
    private int maxScore;
    private String color;
    private List<String> projectionColumns;
    List<ColumnConfig> columns;

    public ScoreCategoryBean()
    {
        super();
        super.setScope(Identity.class);
    }    
    

    public ScoreCategoryBean(String categoryName, int minScore, int maxScore, String color)
    {
        super();
        super.setScope(Identity.class);
        this.categoryName = categoryName;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.color = color;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDDEN METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public QueryOptions getQueryOptions() throws GeneralException
    {
        QueryOptions ops = super.getQueryOptions();
        
        Filter groupFilter = 
            (Filter)getSessionScope().get(ScoreCategoryListBean.ATT_SCORE_GROUP_FILTER);
        if(groupFilter!=null) {
            ops.add(groupFilter);
        }
        
        ops.add(Filter.ge("scorecard.compositeScore", getMinScore()));
        ops.add(Filter.le("scorecard.compositeScore", getMaxScore()));

        ops.setScopeResults(true);
        
        return ops;
    }
    
    /**
     * Return a list of attribute/value maps for each row returned by the query.
     */
    public List<Map<String,Object>> getRows() throws GeneralException {

        if (null == _rows) {
            _rows = new ArrayList<Map<String,Object>>();

            List<String> cols = getProjectionColumns();
            assert (null != cols) : "Projection columns required for using getRows().";

            Iterator<Object[]> results =
                getContext().search(_scope, getQueryOptions(), cols);
            while (results.hasNext()) {
                _rows.add(convertRow(results.next(), cols));
            }
        }
        return _rows;
    }


    /**
     * Convert an Object[] row from a projection query into a attribute/value
     * Map.  This creates a HashMap that has a key/value pair for every column
     * names in the cols list. If the value of hte object implements Localizable,
     * the value will be localized.
     * 
     * The interesting bit here is that we need to look for an Attributes object
     * in the row values.  If it exists, split its contents out into the map.
     * This lets us get to scorecard attributes in a friendly manner.
     *
     * @param  row   The row to convert to a map.
     * @param  cols  The names of the projection columns returned in the object
     *               array.  The indices of the column names correspond to the
     *               indices of the array of values.
     * 
     * @return An attribute/value Map for the converted row.
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {

        Map<String,Object> map = new HashMap<String,Object>(row.length);
        int i = 0;
        for (String col : getProjectionColumns()) {

            // if the value can be localized, do so
            Object value = row[i];
            if (value != null && Localizable.class.isAssignableFrom(value.getClass())){
                value = ((Localizable)value).getLocalizedMessage(getLocale(), getUserTimeZone());
                map.put(col, value);
            } else if (value instanceof Attributes){
                Attributes<String, Object> atts = (Attributes<String, Object>)value;
                for (String key : atts.keySet()) {
                    String prop = col + "." + key;                    
                    map.put(prop, convertColumn(prop, atts.get(key)));
                }
            } else {
                map.put(col, convertColumn(col, value));
            }

            i++;
        }
        
        return map;
    }

    @Override
    public Object convertColumn(String name, Object value) {
        try {
            if (name != null && name.startsWith("scorecard.")) {
                //We want to make a score/color map for each scorecard type
                Map<String, Object> converted = new HashMap<String, Object>();
                int score = 0;
                if (value != null) {
                    if (value instanceof Integer) {
                        score = (Integer)value;
                    } else { 
                        score = new Integer((String)value);
                    }
                }
                converted.put("score", score);
                converted.put("color", WebUtil.getScoreColor(score));
                return converted;
            } else {
                return value;
            }
        } catch (GeneralException e) {
            //convertColumn doesn't throw GeneralException, so throw this isntead.
            throw new RuntimeException(e);
        }
    }

    
    /**
     *  Gets the columns from the UI config.
     *  
     * @throws GeneralException 
     */
    public List<ColumnConfig> getColumns() throws GeneralException {
        
        if (columns == null) 
            columns = super.getUIConfig().getRiskScoreTableColumns();

        return columns;
    }
    
    /**
     * We need to differentiate between the scorecard fields like 
     * businessRowScore and custom score components that will be contained
     * in the scorecard's attributes map.  It's the projection columns that
     * will actually be used to generate the HQL, so filter out any columns
     * from the UI config that reference attributes, then add the scorecard
     * itself to the projection columns.  This enables us to continue sorting
     * on the scorecard fields, as well as returning the (unfortunately 
     * unsortable) custom score components in the scorecard's attributes field.
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if (projectionColumns == null) {
            projectionColumns = new ArrayList<String>();
            
            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {  
                    if (!col.getProperty().contains("attributes"))
                        projectionColumns.add(col.getProperty());
                }                
            }
            
            // Need to add the columns that are static to this list
            if (!projectionColumns.contains("id")) {
                projectionColumns.add("id");
            }
            if (!projectionColumns.contains("name")) {
                projectionColumns.add("name");
            }
            projectionColumns.add("scorecard.attributes");
        }

        return projectionColumns;
    }

    
    /*
    @Override
    public Map<String, String> getSortColumnMap()
    {
        Map<String,String> sortMap = new HashMap<String,String>();
        sortMap.put("name", "name");
        sortMap.put("firstname", "firstname");
        sortMap.put("lastname", "lastname");
        sortMap.put("composite", "scorecard.compositeScore");
        sortMap.put("businessRole", "scorecard.businessRoleScore");
        sortMap.put("entitlement", "scorecard.entitlementScore");
        sortMap.put("policy", "scorecard.policyScore");
        sortMap.put("certification", "scorecard.certificationScore");
        return sortMap;
    }
    */ 
    
    @Override
    public String getDefaultSortColumn() throws GeneralException
    {
        if (getSortColumnMap().containsKey("scorecard.compositeScore"))
            return "scorecard.compositeScore";
        else
            // just in case the column config doesn't include the 
            // compositeScore for some weird reason
            return "name";
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS/SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @return the categoryName
     */
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * @param categoryName the categoryName to set
     */
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    /**
     * @return the maxScore
     */
    public int getMaxScore() {
        return maxScore;
    }

    /**
     * @param maxScore the maxScore to set
     */
    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    /**
     * @return the minScore
     */
    public int getMinScore() {
        return minScore;
    }

    /**
     * @param minScore the minScore to set
     */
    public void setMinScore(int minScore) {
        this.minScore = minScore;
    }
    
    /**
     * @return the color
     */
    public String getColor() {
        return color;
    }
    /**
     * @param color the color to set
     */
    public void setColor(String color) {
        this.color = color;
    }

}
