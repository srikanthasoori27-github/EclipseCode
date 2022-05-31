/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base bean for editing ObjectConfigs.  This is the base class
 * for all extended attribute definition pages but it will
 * be subclassed to set various options.
 *
 * Author: Jeff, Bernie
 *
 */
package sailpoint.web.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.util.MapListComparator;
import sailpoint.web.util.WebUtil;

/**
 * Note that this class is NOT abstract.  We allow it to be
 * instantiated for things that ony need to get to the base DTO fields.  
 * Specifically this is the Extjs grid datasource which needs to be used
 * with all ObjectConfigBean subclasses but it doesn't know which one
 * is active.
 */
public class ObjectConfigBean extends BaseObjectBean<ObjectConfig> {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ObjectConfigBean.class);

    private static final Map<String, Class<? extends SailPointObject>> OBJ_NAME_TO_CLASS_MAPPING;
    
    static {
        OBJ_NAME_TO_CLASS_MAPPING = new HashMap<String, Class<? extends SailPointObject>>();
        OBJ_NAME_TO_CLASS_MAPPING.put(ObjectConfig.IDENTITY, Identity.class);
        OBJ_NAME_TO_CLASS_MAPPING.put(ObjectConfig.LINK, Link.class);
        OBJ_NAME_TO_CLASS_MAPPING.put(ObjectConfig.APPLICATION, Application.class);
        OBJ_NAME_TO_CLASS_MAPPING.put(ObjectConfig.ROLE, Bundle.class);
        OBJ_NAME_TO_CLASS_MAPPING.put(ObjectConfig.BUNDLE, Bundle.class);
        OBJ_NAME_TO_CLASS_MAPPING.put(ObjectConfig.MANAGED_ATTRIBUTE, ManagedAttribute.class);
    }
    
    /**
     * HttpSession attribute where we store editing state.
     */
    public static final String ATT_SESSION = "ObjectConfigSession";

    /**
     * Editing session state and DTO.
     */
    ObjectConfigSession _session;

    /**
     * Configuration validation warnings cache.
     */
    List<String> _configWarnings;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Session
    //
    //////////////////////////////////////////////////////////////////////

    public ObjectConfigBean() throws GeneralException {
        super();
        setScope(ObjectConfig.class);

        // Using the DTO method so don't bother to keep attaching
        // shouldn't hurt though?
        setNeverAttach(true);
    }

    /**
     * Restore the session editing state from the HttpSession
     * or boostrap a new one.  Subclasses should not overload this
     * if you need a more complex session object overload createSession.
     */
    public ObjectConfigSession getSession() {
        if (_session == null) {
            try {
                Map hsession = getSessionScope();
                _session = (ObjectConfigSession)hsession.get(getSessionId());
                if (_session == null) {

                    // subclass may overload this
                    _session = createSession(getObject());

                    saveSession();
                }
            } 
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error("Unable to restore editing session. Exception: " + t.getMessage(), t);
            }
        }
        return _session;
    }

    /**
     * This may be overloaded by a subclass if it wants to create
     * a more specialized session object.  Currently this is only
     * done for roles.
     */
    public ObjectConfigSession createSession(ObjectConfig src) {
        
        return new ObjectConfigSession(src);
    }
    
    /**
     * @return String ID used to reference this ObjectConfig in the session
     */
    protected String getSessionId() {
        return ATT_SESSION;
    }

    public void saveSession() {
        // BaseObjectBean should handle the root object
        Map ses = getSessionScope();
        ses.put(getSessionId(), _session);
    }
    
    public void cancelSession() {
        // BaseObjectBean should handle the root object
        Map ses = getSessionScope();
        ses.remove(getSessionId());
    }
    
    @Override
    public void clearHttpSession() {
        super.clearHttpSession();
        Map ses = getSessionScope();
        ses.remove(getSessionId());
    }
    
    protected void resetSession() {
        setObject(null);
        getSessionScope().remove(getSessionId());
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return configuration validation messages to dispaly at the 
     * top of the page.
     */
    public List<String> getConfigWarnings() throws Exception {
        if (_configWarnings == null) {
            ObjectConfig config = getObject();
            if (config != null) {
                List<Message> warnings = ExtendedAttributeUtil.validate(getContext(), config);
                if (warnings != null) {
                    List<String> strings = new ArrayList<String>();
                    for (Message warning : warnings)
                        strings.add(warning.getLocalizedMessage(getLocale(), getUserTimeZone()));
                    _configWarnings = strings;
                }
            }
        }
        return _configWarnings;
    }

    /**
     * Used by the attribute grid.
     */
    public int getAttributeCount() {
        ObjectConfigSession ses = getSession();
        ObjectConfigDTO dto = ses.getDto();
        List<ObjectAttributeDTO> atts = dto.getAttributes();
        return atts.size();
    }

    /**
     * Derived property used by the attribute grid.
     * May want to support sorting here...
     */
    @SuppressWarnings("unchecked")
    public List<ObjectAttributeDTO> getAttributes() {

        ObjectConfigSession ses = getSession();
        ObjectConfigDTO dto = ses.getDto();
        return dto.getAttributes();
    }
    
    public String getAttributeGridJson() {
        Map<String, Object> response = new HashMap<String, Object>();
        List<ObjectAttributeDTO> attributes = getAttributes();
        List<Map<String, Object>> attributeRows = new ArrayList<Map<String, Object>>();
        response.put("attributes", attributeRows);
        if (Util.isEmpty(attributes)) {
            response.put("totalCount", 0);
        } else {
            response.put("totalCount", attributes.size());
            for (ObjectAttributeDTO attribute : attributes) {
                Map<String, Object> attributeRow = new HashMap<String, Object>();
                attributeRow.put("id", attribute.getUid());
                attributeRow.put("name", attribute.getDisplayableName(getLocale()));
                attributeRow.put("category", attribute.getCategoryName());
                attributeRow.put("description", attribute.getDescription());
                attributeRows.add(attributeRow);
            }
        }

        return JsonHelper.toJson(response);
    }
    
    public List<Map<String, Object>> getCategories() throws GeneralException {
        Map<String, Map<String,Object>> categories = new HashMap<String, Map<String, Object>>();

        ObjectConfigSession ses = getSession();
        ObjectConfigDTO dto = ses.getDto();
        for (ObjectAttributeDTO attr : dto.getAttributes()) {
            String category = attr.getCategoryName();
            if (category != null && category.trim().length() > 0) {
                if (!categories.containsKey(category)) {
                    Map<String,Object> categoryMap = new HashMap<String,Object>();
                    categoryMap.put("value", category);
                    categoryMap.put("displayName", attr.getCategoryDisplayName(getLocale(), getUserTimeZone()));
                    categories.put(category, categoryMap);
                }                
            }
        }
        
        ArrayList<Map<String,Object>> categoryList = new ArrayList<Map<String, Object>>(categories.values());
        Collections.sort(categoryList, new MapListComparator("displayName", false));
        
        return categoryList;
    }
    
    public String getCategoriesJson() throws GeneralException{
        return WebUtil.outputJSONFromList(getCategories());
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public String editAttributeAction() {
        String next = null;
        try {
            ObjectConfigSession ses = getSession();
            ses.editAttribute();
            next = "edit";
        }
        catch (GeneralException e) {
            log.error(e);
            addMessage(e);
        }
        catch (Throwable t) {
            log.error(t);
            addMessage(t);
        }
        return next;
    }

    public String newAttributeAction() {

        ObjectConfigSession ses = getSession();
        ses.newAttribute();

        return "edit";
    }

    public String deleteAttributeAction() {

        ObjectConfigSession ses = getSession();
        ses.deleteAttribute();
        saveAction();
        resetSession();

        return null;
    }

    public String saveAttributeAction() {
        String objectName = null;
        
        try {
            ObjectConfig config = getObject();
            objectName = getObject().getName();
            Class<? extends SailPointObject> spObjectClass = OBJ_NAME_TO_CLASS_MAPPING.get(objectName);
            
            if (spObjectClass == null) {
                log.error("Unable to validate attribute changes for the " + objectName + " config");
                return null;
            } else if (!validate(spObjectClass)) {
                return null;
            }
        } catch (GeneralException e) {
            log.error("Unable to validate attribute changes for the " + objectName + " config", e);
            return null;
        }
        
        if (getHasErrors()) {
            return null;
        }

        ObjectConfigSession ses = getSession();
        if (!ses.saveAttribute()) {
            return null;
        }

        saveAction();
        resetSession();

        return "save";
    }

    public String cancelAttributeAction() {

        ObjectConfigSession ses = getSession();
        ses.cancelAttribute();

        return "cancel";
    }

    public String saveAction() {
        String next = null;
        try {
            // copy the DTO changes back into the persistent object
            ObjectConfig obj = getObject();
            ObjectConfigSession ses = getSession();
            ses.commit(obj);
            
            // BaseObjectBean will save it and determine where to go
            next = super.saveAction();
        }
        catch (GeneralException e) {
            log.error(e);
            addMessage(e);
        }
        catch (Throwable t) {
            log.error(t);
            addMessage(t);
        }
        return next;
    }

    /**
     * BaseObjectBean says it can throw but it really shouldn't.
     */
    public String cancelAction() throws GeneralException {
        cancelSession();
        // BaseObjectBean knows where to go
        return super.cancelAction();
    }
    
    /**
     * Validate the attribute
     * @return true if the attribute was found to be valid; false otherwise
     */
    public boolean validate(Class<? extends SailPointObject> spObjectClass) throws GeneralException {
        ObjectConfigSession ses = getSession();
        boolean isValid;
        if (ses == null) {
            isValid = false;
        } else {
            isValid = ses.validate(getObject(), spObjectClass);
        }        
        return isValid;
    }
}
