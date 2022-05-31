/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Category;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;

public class ActivityCategoryList extends BaseListBean<Category> {
    private static Log log = LogFactory.getLog(ActivityCategoryList.class);
    private static final String GRID_STATE = "activityCategoryListGridState";
    
    public ActivityCategoryList() {
        super();
        setScope(Category.class);
        setSelected(new HashMap<String, Boolean>());
    }
    
    public String getCategoriesJSON() {
        Map<String, Object> response = new HashMap<String, Object>();

        List<Map<String, Object>> responseRows = new ArrayList<Map<String, Object>>();
        response.put("totalCount", 0);
        response.put("categories", responseRows);

        try {
            List<Category> categories = getObjects();
            if (categories != null) {
                response.put("totalCount", this.getCount());
                for (Category category : categories) {
                    Map<String, Object> responseRow = new HashMap<String, Object>();
                    responseRow.put("id", category.getId());
                    responseRow.put("name", category.getName());
                    responseRow.put("targets", category.getTargetsAsCSV());
                    responseRows.add(responseRow);
                }
            }
        } catch (GeneralException e) {
            log.error(e);
            response.put("success", false);
            response.put("errorMsg", "Error retrieving categories");
        }

        return JsonHelper.toJson(response);
    }
    
    public String deleteCategoryAction() {
        final String deletedCategoryId = getRequestParameter("editForm:deletedCategoryId");
        String result;
        
        try {
            if (deletedCategoryId != null && deletedCategoryId.length() != 0) {
                getContext().removeObject(getContext().getObjectById(Category.class, deletedCategoryId));
                getContext().commitTransaction();
            }
            
            result = "delete";
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ACTIVITY_CANT_DELETE, deletedCategoryId);
            log.error(msg.getLocalizedMessage(), e);
            addMessage(msg, null);
            result = null;
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    public String editCategoryAction() {
        getSessionScope().put(BaseObjectBean.FORCE_LOAD, true);
        return "edit";
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }
    
    @Override
    public Map<String, String> getSortColumnMap() {
        HashMap<String, String> columnMap = new HashMap<String, String>();
        columnMap.put("name", "name");
        return columnMap;
    }   
    public String getGridStateName() { 
		return GRID_STATE; 
	}
}
