/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.object.Certification;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * JSF UI bean used for listing certifications.
 * 
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationListBean
extends BaseListBean<Certification>
implements NavigationHistory.Page
{
	private static final Log log = LogFactory.getLog(CertificationListBean.class);
	private static final String COL_CERTIFIERS = "Certifiers.certifier";
	private static final String GRID_STATE = "myCertificationsListGridState";
	private static final String DASH_GRID_STATE = "dashboardCertificationListGridState";
	private static final String SIGNED = "signed";

    // bug 23422 - The isStaged boolean indicates whether this 
    // certification is staged. The phase gets returned as
    // a localized string ready for display in the UI which makes
    // it difficult to determine exactly what phase it's really in.
    private static final String IS_STAGED = "isStaged";
	
	List<ColumnConfig> columns;
	
	/** Columns for the "certification completion status" dashboard widget **/
	List<ColumnConfig> dashboardColumns;

	private List<Certification> objects;
	private GridState gridState;
    private boolean myObjectsOnly = false;

	/**
	 * Default constructor.
	 */
	public CertificationListBean()
	{
		super();
		super.setScope(Certification.class);
	}
    
    /** Retrieves any passed in filters from the request **/
    public void getQueryOptionsFromRequest(QueryOptions qo) throws GeneralException
    {
        // Non-null phase if cert has been started.
        if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals(""))
            qo.add(Filter.ignoreCase(Filter.like("shortName", getRequestParameter("name"))));
        
        if(getRequestParameter("phase")!=null && !((String)getRequestParameter("phase")).equals("")) {
            String phase = (String)getRequestParameter("phase");
            qo.add(Filter.eq("phase",phase ));
        } else {
            qo.add(Filter.notnull("phase"));
        }
        
        if(getRequestParameter("completed")!=null && !((String)getRequestParameter("completed")).equals("")) {
            String completed = (String)getRequestParameter("completed");
            qo.add(Filter.eq("complete", completed.equals("Yes")));
        }
        
        if(getRequestParameter("signed")!=null && !((String)getRequestParameter("signed")).equals("")) {
            String signed = (String)getRequestParameter("signed");
            if(signed.equals("Yes")) {
                qo.add(Filter.notnull("signed"));
            } else {
                qo.add(Filter.isnull("signed"));
            }
        }
        
        if(getRequestParameter("type")!=null && !((String)getRequestParameter("type")).equals("")) {
            /** The names of the types are getting split with a " " for ease of reading on the ui, so
             * we want to strip any of that out for the query.
             */
            String type = (String)getRequestParameter("type");
            qo.add(Filter.eq("type",type));
        }
        
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException
    {
        // bug 24494 - Moving this code to override the disableOwnerScoping
        // from getMyObjectsQueryOptions() because the getQueryOptions() in the
        // base class set the scoping based on that variable.
        boolean currentDisableScoping = this.getDisableOwnerScoping();

        if(this.myObjectsOnly == true) {
            // Since we are getting current users certifications, disable scoping.
            // They should see anything for which they are the certifier either
            // directly or as a member of a workgroup who is the certifier.
            setDisableOwnerScoping(true);
        }

        try {
            QueryOptions qo = super.getQueryOptions();

            getQueryOptionsFromRequest(qo);

            if (this.myObjectsOnly == true) {
                return getMyObjectsQueryOptions(qo);
            } else {
                return qo;
            }
        } finally {
            setDisableOwnerScoping(currentDisableScoping);
        }
    }

	/**
	 * Override to allow sorting by percent complete.  This can't be added
	 * to the QueryOptions since the percent is a runtime calculated field.
	 */
	@Override
	public List<Certification> getObjects() throws GeneralException {

		if(objects==null) {
			objects= super.getObjects();
		}
		return objects;
	}
	
    private QueryOptions getMyObjectsQueryOptions(QueryOptions qo) throws GeneralException
    {
        Identity user = getLoggedInUser();
        List<String> names = new ArrayList<String>();
        names.add(user.getName());

        // If user is in any workgroups, return certs with those
        // workgroups as certifiers too
        if (!Util.isEmpty(user.getWorkgroups())) {
            names.addAll(ObjectUtil.getObjectNames(user.getWorkgroups()));
        }

        qo.add(Filter.in("certifiers", names));

        qo.add(Filter.ne("phase", Certification.Phase.Staged));
        return qo;
    }

    public String getDashboardJSON() throws GeneralException {
        
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put("totalCount", getCount());
        List<Map<String, Object>> certsMap = new ArrayList<Map<String, Object>>();
        for (Certification cert : getObjects()) {
            Map<String, Object> certMap = new HashMap<String, Object>();
            certMap.put("id", cert.getId());
            certMap.put("shortName", cert.getShortName());
            certMap.put("phase", cert.getPhase() == null ? null : getMessage(cert.getPhase().getMessageKey()));
            certMap.put("statistics-itemPercentComplete", cert.getItemPercentComplete());
            certMap.put("certifiers", WebUtil.commify(cert.getCertifiers()));
            certMap.put("type", cert.getType() == null ? null : getMessage(cert.getType().getMessageKey()));
            certMap.put("totalItems", cert.getTotalItems());
            certMap.put("completedItems", cert.getCompletedItems());
            certMap.put("nextPhaseTransition", 
                    cert.getNextPhaseTransition() == null ? getMessage(MessageKeys.NOT_APPLICABLE) :
                            Internationalizer.getLocalizedDate(cert.getNextPhaseTransition(), getLocale(), getUserTimeZone()));
            certMap.put("creator", cert.getCreator() == null ? getMessage(MessageKeys.NOT_APPLICABLE) : cert.getCreator());
            certMap.put("created", Internationalizer.getLocalizedDate(cert.getCreated(), true, getLocale(), getUserTimeZone()));
            certMap.put("signed", cert.getSigned() == null ? getMessage(MessageKeys.NOT_APPLICABLE) : 
                    Internationalizer.getLocalizedDate(cert.getSigned(), getLocale(), getUserTimeZone()));
            certMap.put("expiration", cert.getExpiration() == null ? getMessage(MessageKeys.NOT_APPLICABLE) :
                     Internationalizer.getLocalizedDate(cert.getExpiration(), true, getLocale(), getUserTimeZone()));
            certMap.put(IS_STAGED, Certification.Phase.Staged.equals(cert.getPhase()) ? true : false);
            certsMap.add(certMap);
        }
        jsonMap.put("certifications", certsMap);
        return JsonHelper.toJson(jsonMap);
    }
    
    @Override
    public String getGridResponseJson() throws GeneralException {
        //authorize first like we used to do in getMyObjects
        authorize(new WebResourceAuthorizer("manage/certification/certifications.jsf"));
        try {
            this.myObjectsOnly = true;
            return super.getGridResponseJson();
        } finally {
            this.myObjectsOnly = false;
        }
    }
    
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
            throws GeneralException {
        Map<String, Object> converted = super.convertRow(row, cols);
        // Can't get list from project search, so have to load these up from the cert object.  
        Certification cert = getContext().getObjectById(Certification.class, (String)converted.get("id"));
        converted.put("tags", WebUtil.objectListToNameString(cert.getTags()));
        // Limit the amount of reassignments or forwards that can occur
        converted.put("limitReassignments", cert.limitCertReassignment(getContext()));
        return converted;
    }
    
    @Override
    public Object convertColumn(String name, Object value) {
        Object newValue = value;
        try{
            if ("phase".equals(name) && newValue != null) {
                Certification.Phase phase = (Certification.Phase)value;
                newValue = getMessage(phase.getMessageKey());
            }  else if ("nextPhaseTransition".equals(name)) {
                if (newValue == null) {
                    newValue = getMessage(MessageKeys.NOT_APPLICABLE);
                }
            } else if ("creator".equals(name)) {
                newValue = WebUtil.getDisplayNameForName("Identity", (String)newValue);
            }
        } catch (GeneralException e) {
            log.error("Error converting column value for :" + name, e);
            newValue = getMessage(MessageKeys.ERR_EXCEPTION, e);
        }
        return newValue;
    }

	@Override
	public String getDefaultSortColumn() throws GeneralException {
		return "phase";
	}

	public Map<String, String> getSortColumnMap() {
		HashMap<String,String> sortMap = new HashMap<String,String>();       
		columns = new ArrayList<ColumnConfig>();
        columns.addAll(getColumns());
		columns.addAll(getDashboardColumns());
		
		if (null != columns && !columns.isEmpty()) {
			final int columnCount = columns.size();        
			for(int j =0; j < columnCount; j++) {
				sortMap.put(columns.get(j).getDataIndex(), columns.get(j).getSortProperty());
			}
            sortMap.put("type", "type");
		}
		return sortMap;
	}

	/**
	 * Action handler called when a certification is selected from the list.
	 */
	public String select() throws GeneralException
	{
		String selected = super.getSelectedId();
		if (null == selected){
			Map map = getRequestParam();
			selected = (String) map.get("selectedId");
			if(null == selected)
				throw new GeneralException("No work item was selected.");
		}

		// This stores information so that the correct certification will be
		// displayed after redirecting to the next page.
		CertificationBean.viewCertification(FacesContext.getCurrentInstance(), selected);

		NavigationHistory.getInstance().saveHistory(this);

        CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(selected);
		return certPrefBean.getDefaultView();
	}



	////////////////////////////////////////////////////////////////////////////
	//
	// NavigationHandler.Page interface
	//
	////////////////////////////////////////////////////////////////////////////

	public String getNavigationString() {
	    String searchType = (String) getRequestParam().get("certificationResultForm:searchType");
	    if (searchType == null || searchType.trim().length() == 0) {
	        // Coming off the certifications view
	        return "viewCertifications";
	    } else {
	        // Coming off the certifications search page
	        return "certificationSearchResults";
	    }
	}

	public String getPageName() {
		return "My Certifications";
	}

	// No state.
	public Object calculatePageState() { 
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state; 
    }
    
	public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);   
    }

	void loadColumnConfig() {
		try {
			this.columns = super.getUIConfig().getMyCertificationsTableColumns();
			this.dashboardColumns = super.getUIConfig().getDashboardCertificationStatusTableColumns();
		} catch (GeneralException ge) {
			log.info("Unable to load column config: " + ge.getMessage());
		}
	}

	public List<ColumnConfig> getColumns() {
	    if(columns==null) {
	        loadColumnConfig();

	        // bug 21595 - Return "signed" to indicate to the UI that the certification
	        // has been completed. If "signed" was configured as a column to be returned 
	        // in UIConfig then there's nothing more to do. If not, then add "signed" as 
	        // a field only property in the response. 
	        for(ColumnConfig column : columns) {
	            if(SIGNED.equals(column.getProperty())) {
	                return columns;
	            }
	        }

	        // Add "signed" as a field only column. In this case we only care if "signed" 
	        // is not null so there's no need to format the date.
	        ColumnConfig signedColumn = new ColumnConfig(SIGNED, SIGNED);
	        signedColumn.setFieldOnly(true);            
	        addFieldColumn(signedColumn);
	    }

	    return columns;
	}

	
	public GridState getDashboardGridState() {
		if(gridState==null)
			gridState = loadGridState(DASH_GRID_STATE);
		return gridState;
	}

	public void setDashboardGridState(GridState gridState) {
		this.gridState = gridState;
	}


	public List<ColumnConfig> getDashboardColumns() {
	    if(dashboardColumns==null) {
	        loadColumnConfig();
	    }

	    // bug 22648 - Return "signed" to indicate to the UI that the certification
	    // has been completed. If "signed" was configured as a column to be returned 
	    // in UIConfig then there's nothing more to do. If not, then add "signed" as 
	    // a field only property in the response. 
	    for(ColumnConfig column : dashboardColumns) {
	        if(SIGNED.equals(column.getProperty())) {
	            return dashboardColumns;
	        }
	    }

	    // Add "signed" as a field only column. In this case we only care if "signed" 
	    // is not null so there's no need to format the date.
	    ColumnConfig signedColumn = new ColumnConfig(SIGNED, SIGNED);
	    signedColumn.setFieldOnly(true);            
	    dashboardColumns.add(signedColumn);	

        // bug 23422 - Add "isStaged" as a field only column and it's value will be
        // derived from the phase column.
        ColumnConfig isStagedColumn = new ColumnConfig(IS_STAGED, IS_STAGED);
        isStagedColumn.setFieldOnly(true);
        dashboardColumns.add(isStagedColumn);

	    return dashboardColumns;
	}
	
	/**
	 * 
	 * @return GridResponse Object to create the grid
	 * @throws GeneralException
	 */
	public String getDashboardColumnJSON() throws GeneralException {
		return super.getColumnJSON(getDefaultSortColumn(), getDashboardColumns());
	}

	public void setDashboardColumns(List<ColumnConfig> dashboardColumns) {
		this.dashboardColumns = dashboardColumns;
	}
	
	public String getGridStateName() {
		return GRID_STATE;
	}

}
