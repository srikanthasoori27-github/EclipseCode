/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Terminator;
import sailpoint.object.BatchRequest;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;


/**
 * JSF bean to list batch requests.
 */
public class BatchRequestListBean extends BaseListBean<BatchRequest>
    implements NavigationHistory.Page {

    private static final Log log = LogFactory.getLog(BatchRequestListBean.class);
    private static final String GRID_STATE = "batchRequestListGridState";
    private static final String GRID_COLUMN_STATUS = "status";

    /**
     * HttpSession attribute we use to convey the selected batch request  id
     * to ApplicationObjectBean.  
     */
    public static final String ATT_OBJECT_ID = "batchRequestId";

    List<ColumnConfig> columns;

    /**
     *
     */
    public BatchRequestListBean() {
        super();
        setScope(BatchRequest.class);
        setDisableOwnerScoping(true);
    }  // BatchRequestListBean()

    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     *  Action for create new batch request. May not need this.
     *
     * @return "create"
     */
    @SuppressWarnings("unchecked")
    public String newBatchRequest() {
        return "createNew";
    }

    /**
	 *	Set the terminate flags for everything
     *
     * @throws GeneralException
     */
    public void terminate() throws GeneralException {
        if ( _selectedId == null ) return;
        BatchRequest req = getContext().getObjectById(BatchRequest.class, _selectedId);
        
        if (req == null) return;
        
        if (req.getStatus() == BatchRequest.Status.Terminated) {
        	return;
        }
        
    	req.setStatus(BatchRequest.Status.Terminated);
    	
    	getContext().saveObject(req);
    	getContext().commitTransaction();
    }

    /**
     * Deleting the batch request will also delete all related batch request items.
     * 
     */
    public void delete() {
    	 if ( _selectedId == null ) return;
    	 
    	 this.deleteObject(null);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public Object convertColumn(String name, Object value) {
        Object newValue = value;
        if (GRID_COLUMN_STATUS.equals(name) && value != null) {
            newValue = getMessage(((BatchRequest.Status)value).getMessageKey());
        }
        
        return newValue;
    }
    
    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();
        getQueryOptionsFromRequest(qo);

        qo.addOrdering("created", true);
        
        return qo;
    }
    
    /**
     * @param qo
     * @throws GeneralException
     */
    public void getQueryOptionsFromRequest(QueryOptions qo) throws GeneralException
    {
    	String searchText = getRequestParameter("searchText");
    	if((searchText != null) && (!searchText.equals(""))) {
    		List<Filter> filters = new ArrayList<Filter>();
    		filters.add(Filter.ignoreCase(Filter.like("fileName", searchText, MatchMode.START)));
    		qo.add(Filter.or(filters));
    	} 	
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "created";
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String select() throws GeneralException {
        getSessionScope().put(ATT_OBJECT_ID, getSelectedId());
        return super.select();
    }

    @Override
    public Map<String,String> getSortColumnMap()
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
    
    void loadColumnConfig() throws GeneralException {
        this.columns = super.getUIConfig().getBatchRequestTableColumns();
    }
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            try {
                loadColumnConfig();     
            } catch (GeneralException ge) {
                log.info("Unable to load columns: " + ge.getMessage());
            }
        return columns;
    }
 
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "BatchRequest List";
    }

    public String getNavigationString() {
        return "batchRequestList";
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

	public String getGridStateName() { 
		return GRID_STATE; 
	}

    @Override
    public void deleteObject(ActionEvent event) {
        try {
            Terminator term = new Terminator(getContext());
            if (term != null) {
                BatchRequest obj = getContext().getObjectById(BatchRequest.class, this.getSelectedId());
                if (obj != null) {
                    term.deleteObject(obj);
                }
            }
        } catch (GeneralException ex) {
            String msg = "Unable to remove object with id '" +
            _selectedId + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }
    }
}  // class ApplicationListBean
