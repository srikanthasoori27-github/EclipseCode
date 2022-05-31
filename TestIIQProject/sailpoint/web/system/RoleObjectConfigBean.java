/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bean for editing ObjectConfig:Bundle.
 * We only allow the definition of simple attributes.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

public class RoleObjectConfigBean extends ObjectConfigBean {
    private static final Log log = LogFactory.getLog(RoleObjectConfigBean.class);
    public static final String ATT_ROLE_SESSION = "RoleObjectConfigSession";

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Session
    //
    //////////////////////////////////////////////////////////////////////

    public RoleObjectConfigBean() throws GeneralException {
        super();
        setObjectName(ObjectConfig.BUNDLE);
    }

    /**
     * BaseObjectBean overload in case we don't have
     * one of these yet.
     */
    public ObjectConfig createObject() {

        ObjectConfig config = new ObjectConfig();
        config.setName(ObjectConfig.BUNDLE);

        return config;
    }

    /**
     * We overload this to create a more specialized DTO containing
     * the role type definitions.
     */
    public ObjectConfigSession createSession(ObjectConfig src) {

        return new RoleObjectConfigSession(src);
    }

    /**
     * Overload this so we can downcast.
     */ 
    public RoleObjectConfigSession getRoleSession() {

        return (RoleObjectConfigSession)getSession();
    }
    
    // Return a different ID so the ObjectConfig doesn't conflict with the type config
    protected String getSessionId() {
        return ATT_ROLE_SESSION;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Used by the attribute LiveGrid.
     */
    public int getAttributeCount() {
        ObjectConfigSession ses = getSession();
        ObjectConfigDTO dto = ses.getDto();
        List<ObjectAttributeDTO> atts = dto.getAttributes();
        return atts.size();
    }

    /**
     * Derived property used by the attribute LiveGrid.
     * May want to support sorting here...
     */
    @SuppressWarnings("unchecked")
    public List<ObjectAttributeDTO> getAttributes() {

        ObjectConfigSession ses = getSession();
        ObjectConfigDTO dto = ses.getDto();
        return dto.getAttributes();
    }
    
    public String getRoleTypeGridJson() {
        RoleObjectConfigSession roleSession = getRoleSession();
        RoleObjectConfigDTO roleDto = roleSession.getRoleDto();
        List<RoleTypeDefinitionDTO> roleTypes = roleDto.getTypes();
        
        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, Object>> typeRows = new ArrayList<Map<String, Object>>();
        response.put("types", typeRows);
        if (Util.isEmpty(roleTypes)) {
            response.put("totalCount", 0);
        } else {
            for (RoleTypeDefinitionDTO type : roleTypes) {
                Map<String, Object> typeRow = new HashMap<String, Object>();
                typeRow.put("id", type.getUid());
                typeRow.put("name", type.getDisplayableName());
                typeRow.put("description", type.getDescription());
                typeRows.add(typeRow);
            }
        }

        return JsonHelper.toJson(response);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Note that the "edit" outcome is reserved for transitioning
     * to the attribute editing page, we have to use "editType".
     */
    public String editTypeAction() {
        String next = null;
        try {
            RoleObjectConfigSession ses = getRoleSession();
            ses.editType();
            next = "editType";
        }
        catch (GeneralException e) {
            addMessage(e);
        }
        catch (Throwable t) {
            addMessage(t);
        }
        return next;
    }

    public String newTypeAction() {

        RoleObjectConfigSession ses = getRoleSession();
        ses.newType();

        return "editType";
    }

    public String deleteTypeAction() {
    	// first make sure there are no roles currently using the given type
        try {
	        RoleObjectConfigSession ses = getRoleSession();
	        ses.deleteType();
	        super.saveAction();
	        resetSession();        
        }
        catch (GeneralException e) {
            addMessage(e);
        }

        return null;
    }

    public String saveTypeAction() {
        RoleObjectConfigSession ses = getRoleSession();
        if (!ses.validateType()) {
            return null;
        }

        ses.saveType();
        super.saveAction();
        resetSession();

        return "save";
    }

    public String cancelTypeAction() {

        RoleObjectConfigSession ses = getRoleSession();
        ses.cancelType();

        return "cancel";
    }
}
