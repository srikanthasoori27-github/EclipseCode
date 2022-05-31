/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web.group;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.Consts;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.identity.IdentityPagingBean;
import sailpoint.web.util.NavigationHistory;

/**
 * @author peter.holcomb
 *
 */
public class SubGroupDefinitionBean extends BaseObjectBean<GroupDefinition> 
	implements NavigationHistory.Page{

    private static Log log = LogFactory.getLog(SubGroupDefinitionBean.class);

    public static final String GRID_STATE = "subgroupIdentitiesGridState";

    String selectedIdentityId;
    
    private GridState gridState;
    
    /**
     * The configured attributes we will display for the edit subgroup grid.
     */
    List<ColumnConfig> subgroupColumns;
    
    /**
    *
    */
   public SubGroupDefinitionBean() {
       super();
       setScope(GroupDefinition.class);
       restoreObjectId();
   }

    /**
     * Non-private groups are visibile to all users, so when
     * we validate a user's ability to view an object, add 
     * a scope extension.
     *
     * @return
     */
    @Override
    protected QueryOptions addScopeExtensions(QueryOptions ops){
        ops = super.addScopeExtensions(ops);
        ops.extendScope(Filter.eq("private", false));
        return ops;
    }
	
    public void restoreObjectId() {

        Map session = getSessionScope();

        // since we're managing our own object ids, have to
        // handle the FORCE_LOAD option oursleves
        boolean forceLoad = Util.otob(session.get(FORCE_LOAD));
        session.remove(FORCE_LOAD);
        if (forceLoad)
            clearHttpSession();

        // list page will set this initially, thereafter we keep it
        // refreshed as we transition among our pages
        String id = (String)session.get(GroupDefinitionListBean.ATT_OBJECT_ID);
        if (id == null) {
            // the other convention on some pages
            Map map = getRequestParam();
            id = (String) map.get("selectedId");
            if (id == null)
                id = (String) map.get("editForm:selectedId");
        }
        setObjectId(id);
    }
    
    /** If an identity is selected from the list of results, we need to get all of the ids
	 * of the list of results and store them on the session so that when they go to the identity
	 * page, they can page through all of the results.
	 */
	public String selectIdentity() throws GeneralException{
		List<String> ids = new ArrayList<String>();
		List<String> cols = new ArrayList<String>();
		cols.add("id");
		
		/** Read the sort order from the grid state object and sort the list of identity ids */
		
		String sortCol = getGridState().getSortColumn();
		String sortOrder = getGridState().getSortOrder();
		
		/** For some reason, ie passes a null string as "null" so we have to check if it's literally
		 * equal to "null"
		 */
		try {
		    GroupDefinition groupDef = getObject();
			QueryOptions ops = new QueryOptions(groupDef.getFilter());
			
			// sort by name by default
			ops.addOrdering("name", true);
			
			if(sortCol!=null && !sortCol.equals("") && !sortCol.equals("null")) {
				ops.addOrdering(0, sortCol, "ASC".equalsIgnoreCase(sortOrder));
			}
			
			Iterator<Object[]> results =getContext().search(Identity.class, ops, cols);
			while (results.hasNext()) {
				ids.add((String)results.next()[0]);
			}		
			getSessionScope().put(IdentityPagingBean.ATT_IDENTITY_IDS, ids);
		} catch (GeneralException e) {
		    if (log.isErrorEnabled())
		        log.error(e.getMessage(), e);
		}
		NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        getSessionScope().put(BaseListBean.ATT_EDITFORM_ID, getSelectedIdentityId());
        getSessionScope().put(IdentityDTO.VIEWABLE_IDENTITY, getSelectedIdentityId());
        
        return IdentityDTO.createNavString(Consts.NavigationString.edit.name(), getSelectedIdentityId());
	}

    public int getCount() throws GeneralException {
        GroupDefinition def = getObject();
        if (null == def) {
            throw new GeneralException("Could not get Group Definition object.");
        }
        return getContext().countObjects(Identity.class, new QueryOptions(def.getFilter()));
    }

    private void setCurrentTab() {
        Object bean = super.resolveExpression("#{groupNavigation}");
        if ( bean != null )
            ((GroupNavigationBean)bean).setCurrentTab(1);
    }

    @Override
    public String saveAction() throws GeneralException {
        setCurrentTab();
        authorize(new RightAuthorizer(SPRight.FullAccessGroup));
        return super.saveAction();
    }

    @Override
    public String cancelAction() throws GeneralException {
        setCurrentTab();
        return super.cancelAction();
    }
    
    public void setSelectedIdentityId(String selectedIdentityId) {
		this.selectedIdentityId = selectedIdentityId;
	}

    public String getSelectedIdentityId() {
		return selectedIdentityId;
	}
    
    /** load a configured gridstate object based on what type of cert it is **/
	public GridState loadGridState(String gridName) {
		GridState state = null;
		String name = "";
		IdentityService iSvc = new IdentityService(getContext());
		try {
			if(gridName!=null)
				state = iSvc.getGridState(getLoggedInUser(), gridName);
		} catch(GeneralException ge) {
			log.info("GeneralException encountered while loading gridstates: "+ge.getMessage());
		}

		if(state==null) {
			state = new GridState();
			state.setName(name);
		}
		return state;
	}
    
    public GridState getGridState() {
		if (null == this.gridState) {
			this.gridState = loadGridState(GRID_STATE);
		}
		return this.gridState;
	}

	public void setGridState(GridState gridState) {
		this.gridState = gridState;
	}
	
	public List<ColumnConfig> getColumns() 
            throws GeneralException {

        if (subgroupColumns == null) {
        	subgroupColumns = super.getUIConfig().getPopulationEditTableColumns();
            
            /** Do special processing to grab the display names from the object config **/
            ObjectConfig identityConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
            if(identityConfig!=null) {
                for(ColumnConfig column : subgroupColumns) {
                    String property = column.getProperty();
                    ObjectAttribute attribute = identityConfig.getObjectAttribute(property);                    
                    if(attribute!=null && attribute.getDisplayName()!=null) {
                        column.setHeaderKey(attribute.getDisplayName());
                    } else if(attribute!=null) {
                        column.setHeaderKey(attribute.getName());
                    }
                }
            }
            
        }
        return subgroupColumns;
    }   
	
	/** Returns a grid meta data that can be used to build grid column configs and provide
     * the list of fields to the datasource.  Normally, we would inherit this from BaseListBean,
     * but we aren't in that hierarchy.
     * 
     * @return
     * @throws GeneralException
     */
    public String getColumnJSON() throws GeneralException{
        return super.getColumnJSON(getDefaultSortColumn(), getColumns());
    }
    
    /**
     * Overload this in the subclass to provide the name of the default sorting
     * attribute.  This now attempts to use the column configuration from
     * getColumns() to sort by the first non-hidden column.  If getColumns() is
     * not implemented, this must be overridden to make the results not be
     * randomly ordered.  This can be overridden to allow a column other than
     * the first column to be the default sort column.
     */
    public String getDefaultSortColumn() throws GeneralException {

        String defaultSortCol = null;

        List<ColumnConfig> cols = this.getColumns();
        if ((null != cols) && !cols.isEmpty()) {
            for (ColumnConfig col : cols) {
                if (!col.isHidden() && col.isSortable()) {
                    defaultSortCol = col.getProperty();
                    break;
                }
            }
        }

        return defaultSortCol;
    }
	
	////////////////////////////////////////////////////////////////////////////
	//
	// NavigationHistory.Page methods
	//
	////////////////////////////////////////////////////////////////////////////

	public String getPageName() {
		return "Edit Subgroup Page";
	}

	public String getNavigationString() {
		return "editSubGroup";
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
