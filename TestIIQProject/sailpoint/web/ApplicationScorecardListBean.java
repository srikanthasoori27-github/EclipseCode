/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Date;
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
import sailpoint.object.GridState;
import sailpoint.object.QueryOptions;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.web.messages.MessageKeys;

/**
 * JSF UI bean used for listing application scorecard items.
 *
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
public class ApplicationScorecardListBean extends BaseListBean<Application> {
	private static final Log log = LogFactory.getLog(ApplicationScorecardListBean.class);
	
	List<ColumnConfig> columns;
    private GridState gridState;
    private List<ColumnConfig> attributeColumns;
    private List<String> projectionAttributes;
    private static String GRID_STATE = "dashApplicationStatisticsGridState";
    
    /**
     * Default constructor.
     */
    public ApplicationScorecardListBean()
    {
        super();
        super.setScope(Application.class);
    }
    
    public String getRowsJSON() throws GeneralException {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put("totalCount", getCount());
        
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        
        for (Map<String,Object> row : getRows()) {
            Map<String,Object> result = new HashMap<String, Object>();
            result.put("id", row.get("id"));
            for (ColumnConfig column : getColumns()) {
                if ("scorecard.created".equals(column.getProperty())) {
                    Object created = row.get("scorecard.created");
                    Object value = getMessage(MessageKeys.CREATION_NEVER);
                    if (created != null) {
                        value = Internationalizer.getLocalizedDate((Date)created, 
                                column.getDateStyleValue() == null ? Internationalizer.IIQ_DEFAULT_DATE_STYLE : column.getDateStyleValue(),
                                column.getTimeStyleValue(),
                                getLocale(), getUserTimeZone());
                    }
                    result.put(column.getDataIndex(), value);
                } else {
                    result.put(column.getDataIndex(), row.get(column.getProperty() != null ? column.getProperty() : column.getDataIndex()));
                }
            }
            results.add(result);
        }
        jsonMap.put("results", results);
        return JsonHelper.toJson(jsonMap);
    }
    
    @Override
    public QueryOptions getQueryOptions() throws GeneralException
    {
        QueryOptions qo = super.getQueryOptions();
        getQueryOptionsFromRequest(qo);
        
        return qo;
    }
    
    public void getQueryOptionsFromRequest(QueryOptions qo) throws GeneralException 
    {
    	if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals(""))
    		qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"))));
    }
    
    @Override
    public Map<String,String> getSortColumnMap() throws GeneralException
    {
    	Map<String,String> sortMap = new HashMap<String,String>();        
        List<ColumnConfig> columns = getColumns();
        if (null != columns && !columns.isEmpty()) {
            for(int j =0; j < columns.size(); j++) {
            	sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
            }
        }
        return sortMap;
    }
    
    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if (projectionAttributes == null) {
            projectionAttributes = new ArrayList<String>();
            
            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    if (col.getProperty()!=null) {
                        projectionAttributes.add(col.getProperty());
                    }
                }
            }
            projectionAttributes.add("id");
        }
        
        return projectionAttributes;
    }
    
    public List<ColumnConfig> getAttributeColumns() throws GeneralException {
    	
    	if(attributeColumns==null) {
    		attributeColumns = new ArrayList<ColumnConfig>();
    		
    		//Add Total Links
    		ColumnConfig totLinkcc = new ColumnConfig();
    		totLinkcc.setFieldOnly(true);
    		totLinkcc.setDataIndex(ApplicationScorecard.ATT_TOTAL_LINKS);
    		attributeColumns.add(totLinkcc);
    		ColumnConfig totEntcc = new ColumnConfig();
    		totEntcc.setFieldOnly(true);
    		totEntcc.setDataIndex(ApplicationScorecard.ATT_TOTAL_ENTITLEMENTS);
    		attributeColumns.add(totEntcc);
    		
	    	ScoreConfig config = 
	            (ScoreConfig)getContext().getObjectByName(ScoreConfig.class, 
	                                                      "ScoreConfig");
	    	List<ScoreDefinition> scores = config.getApplicationScores();
	    	if (scores!=null) {
	    		for (ScoreDefinition score : scores) {
	    			if(score.getName()!=null) {
	    				ColumnConfig cc = new ColumnConfig();
	    				cc.setFieldOnly(true);
	    				cc.setDataIndex(score.getName());
	    				attributeColumns.add(cc);
	    			}
	    		}
	    	}
    	}
    	return attributeColumns;
    }
    
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
	throws GeneralException {

		Map<String,Object> map = new HashMap<String,Object>(row.length);
		int i = 0;
		for (String col : getProjectionColumns()) {
			if(col.equals("scorecard.attributes")) {
				Attributes attr = (Attributes)row[i];
				if ( attr != null ) {
					for(ColumnConfig attributeColumn : getAttributeColumns()) {
						if ( attributeColumn != null ) {
							Object value = attr.get(attributeColumn.getDataIndex());
							map.put(attributeColumn.getDataIndex(), value == null ? 0 : value);
						}
					}
				}
			} else {
				map.put(col, row[i]);
			}
			i++;
		}
		return map;
	}

    void loadColumnConfig() {
    	try {
    		this.columns = super.getUIConfig().getDashboardApplicatonStatisticsTableColumns();
    	}catch(GeneralException ge) {
    		log.info("GeneralException encountered while loading columnconfig: " + ge.getMessage());
    	}
    }

	public List<ColumnConfig> getColumns() throws GeneralException{
		if(columns==null) {
			loadColumnConfig();		
			//Add all fieldOnly DataIndex attribute columns
			columns.addAll(getAttributeColumns());
		}
		
		return columns;
	}

	public void setColumns(List<ColumnConfig> columns) {
		this.columns = columns;
	}

	public String getGridStateName() { 
		return GRID_STATE; 
	}

}
