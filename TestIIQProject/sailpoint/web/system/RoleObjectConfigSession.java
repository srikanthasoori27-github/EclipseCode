/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Extended editing session state for roles.
 *
 * Author: Jeff
 *
 * ObjectConfigSession has the state and action handlers
 * for ObjectAttribute editing, here we add a similar set
 * of handlers for role type editing.
 * 
 */

package sailpoint.web.system;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class RoleObjectConfigSession extends ObjectConfigSession {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The uid of the RoleTypeDefinitionDTO currently being edited.
     */
    String _typeId;

    /**
     * A copy of the RoleTypeDefinitionDTO being edited.
     */
    RoleTypeDefinitionDTO _type;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Overload the constructor so we can make a DTO subclass
     * that has more in it than ObjectConfigDTO.
     */
    public RoleObjectConfigSession(ObjectConfig src) {
        super(src);

        setDto(new RoleObjectConfigDTO(src));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Alternate accessor that does a downcast.
     */
    public RoleObjectConfigDTO getRoleDto() {

        return (RoleObjectConfigDTO)getDto();
    }

    /**
     * Must be posted by the LiveGrid before transitioning.
     */
    public String getTypeId() {
        return _typeId;
    }

    public void setTypeId(String id) {
        _typeId = id;
    }

    /**
     * Return the object being edited.
     */
    public RoleTypeDefinitionDTO getType() {
        return _type;
    }
    
    public void newAttribute() {
        super.newAttribute();
        // All role attributes have an edit mode of 'permanent'
        _attribute.setEditMode(ObjectAttribute.EditMode.Permanent.name());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Start editing a type definition by cloning the selected type.
     * The id must have been posted.
     */
    public void editType() throws GeneralException {
        if (_typeId != null) {
            RoleObjectConfigDTO dto = getRoleDto();
            RoleTypeDefinitionDTO src = dto.getType(_typeId);
            if (src != null)
                _type = new RoleTypeDefinitionDTO(src);
        }

        if (_type == null)
            throw new GeneralException("No type selected for editing");
    }
    
    /**
     * Begin creation of a new type.
     */
    public void newType() {
        _typeId = null;
        _type = new RoleTypeDefinitionDTO();
    }

    /**
     * Delete the selected attribute.
     */
    public void deleteType() throws GeneralException {

        if (_typeId != null) {
            RoleObjectConfigDTO dto = getRoleDto();
            RoleTypeDefinitionDTO type = dto.getType(_typeId);
            
            if (type != null) {
	        	// first make sure there are no roles currently using the given type
	            SailPointContext ctx = SailPointFactory.getCurrentContext();
	            QueryOptions qo = new QueryOptions();
	            qo.add(Filter.eq("type", type.getName()));
	            
	            int count = ctx.countObjects(Bundle.class, qo);
	            if (count == 0)
	            	dto.remove(type);
	            else
	            	throw new GeneralException(new Message(Message.Type.Error,
	                    MessageKeys.ERR_TYPE_HAS_ROLE_REFS, count));
            }
        }

        // clear selection state
        cancelType();
    }

    /**
     * Commit the changes to the type back into the DTO.
     */
    public void saveType() {

        if (_type != null) {
            RoleObjectConfigDTO dto = getRoleDto();
            dto.replace(_type);
        }

        // clear selection state
        cancelType();
    }

    /**
     * Forget about type editing state.
     */
    public void cancelType() {
        _type = null;
        _typeId = null;
    }


    public boolean validateType() {
        if (getType() == null || Util.isNullOrEmpty(getType().getName())) {
            addMessage(new Message(Message.Type.Error, MessageKeys.LABEL_NAME_IS_REQUIED));
            return false;
        }
        return true;
    }
}


