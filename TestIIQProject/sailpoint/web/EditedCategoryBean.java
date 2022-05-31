/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Category;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * This class acts as a container for keeping category editing information in the session
 * @author  Bernie Margolis
 */
public class EditedCategoryBean extends BaseObjectBean<Category> {
    public static final String EDITED_CATEGORY_ATTR = "EDITED_ACTIVITY_CATEGORY_ATTR";
    private static Log log = LogFactory.getLog(EditedCategoryBean.class);    
    private Map<String, Boolean> selectedTargets;
        
    public EditedCategoryBean() {
        super();
        setScope(Category.class);
        setStoredOnSession(true);
        selectedTargets = new HashMap<String, Boolean>();
    }
        
    public String addTargetsAction() {
        FacesContext ctx = FacesContext.getCurrentInstance(); 
        ValueBinding targetsToAddValueBinding = ctx.getApplication().createValueBinding("#{activityCategoryConfig.selectedTargets}");
        String [] targetsToAdd = (String []) targetsToAddValueBinding.getValue(ctx);

        if (targetsToAdd == null || targetsToAdd.length == 0) {
            targetsToAdd = (String []) ctx.getExternalContext().getRequestMap().get("editForm:targetSelection");
        }
        
        try {
            if (targetsToAdd != null && targetsToAdd.length > 0) {
                Category cat = getObject();
                List<String> currentTargets;
                
                currentTargets = cat.getTargets();
                
                if (currentTargets == null) {
                    currentTargets = new ArrayList<String>();
                }
                
                for (int i = 0; i < targetsToAdd.length; ++i) {
                    String targetToAdd = targetsToAdd[i];
                    
                    if (!currentTargets.contains(targetToAdd)) {
                        currentTargets.add(targetToAdd);
                    }
                }
                
                cat.setTargets(currentTargets);    
                targetsToAddValueBinding.setValue(ctx, new String [] {});
            }
        } catch (GeneralException e) {
            final String errMsg = "The category with id " + getObjectId() + " could not be retrieved.";
            log.error(errMsg, e);
        }
        
        return "add";
    }
    
    public String removeTargetsAction() {
        try {
            Category cat = getObject();
            
            List<String> currentTargets = cat.getTargets();
            
            if (currentTargets == null) {
                currentTargets = new ArrayList<String>();
            }

            
            for (String targetToRemove : selectedTargets.keySet()) {
                if (selectedTargets.get(targetToRemove)) {
                    if (currentTargets.contains(targetToRemove)) {
                        currentTargets.remove(targetToRemove);
                    }
                }
            }

            cat.setTargets(currentTargets);
     
            selectedTargets.clear();
            
            for (String target : currentTargets) {
                selectedTargets.put(target, false);
            }
        } catch (GeneralException e) {
            final String errMsg = "The category with id " + getObjectId() + " could not be retrieved.";
            log.error(errMsg, e);
        }
        
        return "add";
    }

    @SuppressWarnings("unchecked")
    public String finishMappingAction() {
        boolean error = false;

        try {
            SailPointContext spCtx = getContext();
            Category cat = getObject();

            if (validateName(cat)) {
                spCtx.saveObject(cat);
                spCtx.commitTransaction();
            } else {
                error = true;
            }
        } catch (GeneralException e) {
            Message errMsg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ACTIVITY_CAT_CANNOT_BE_EDITED, getObjectId());
            addMessage(errMsg, null);
            log.error(errMsg.getMessage(), e);
        }
        
        final String result;
        
        if (error) {
            result = "error";
        } else {
            setObjectId(null);
            setObject(null);
            getSessionScope().put(BaseObjectBean.FORCE_LOAD, true);
            result = "done";
        }
        
        return result;
    }

    @SuppressWarnings("unchecked")
    public String finishMappingActionWithoutRenaming() {
        setObjectId(null);
        setObject(null);
        getSessionScope().put(BaseObjectBean.FORCE_LOAD, true);
        return "done";
    }
    
    public Map<String, Boolean> getSelectedTargets() {
        return selectedTargets;
    }

    public void setSelectedTargets(Map<String, Boolean> selectedTargets) {
        this.selectedTargets = selectedTargets;
    }
    
    public boolean validateName(Category cat) throws GeneralException {
        boolean isNameValid = true;
        final String name = cat.getName();
        Category existingCategory = getContext().getObjectByName(Category.class, name);
        
        if (name == null || name.trim().length() == 0) {

            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_EMPTY_ACTIVITY_CAT_NAME), null);
            isNameValid = false;
        } else if (existingCategory != null && 
                  (!existingCategory.equals(cat) || !existingCategory.getId().equals(cat.getId()))) {

            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_DUPLICATE_ACTIVITY_CAT_NAME, name), null);
            isNameValid = false;
        }
        
        return isNameValid;
    }

    @Override
    public Category createObject() {
        return new Category();
    }
}
