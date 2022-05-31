/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Session state for ObjectConfig editing.
 * 
 * Author: Jeff
 * 
 * There's not anything interesting in here other than the
 * DTO so we could simplify it and just save the DTO directly 
 * on the HttpSession.  But other pages use the session wrapper
 * convention and this allows ObjectConfigBean subclasses
 * (especially RoleObjectConfigBean) to create ObjectConfigSession
 * subclasses with more stuff in them (e.g. role type definition state).
 *
 */

package sailpoint.web.system;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.messages.MessageKeys;

public class ObjectConfigSession extends BaseDTO
{
    private static Log log = LogFactory.getLog(ObjectConfigSession.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The DTO representation of the ObjectConfig.  
     * This may be a subclass of ObjectConfigDTO for more
     * complex configurations (e.g. Bundle).
     */
    ObjectConfigDTO _dto;

    /**
     * The uid of the ObjectAttributeDTO currently being edited.
     */
    String _attributeId;

    /**
     * A copy of the ObjectAttributeDTO being edited.
     */
    ObjectAttributeDTO _attribute;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ObjectConfigSession() {
    }

    /**
     * This constructor builds the defualt simple DTO representation
     * of an ObjectConfig.  You can overload this in a subclass
     * if you want to create a more complex DTO.
     */
    public ObjectConfigSession(ObjectConfig src) {
        
        _dto = new ObjectConfigDTO(src);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ObjectConfigDTO getDto() {
        return _dto;
    }

    /**
     * This is intended for use only by subclasses that want to build
     * a more complex DTO.
     */
    protected void setDto(ObjectConfigDTO dto) {
        _dto = dto;
    }

    /**
     * Must be posted by the LiveGrid before transitioning.
     */
    public void setAttributeId(String uid) {
        _attributeId = uid;
    }

    public String getAttributeId() {
        return _attributeId;
    }

    /**
     * Return the object being edited.
     */
    public ObjectAttributeDTO getAttribute() {
        return _attribute;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Start editing an attribute definition.
     * The id must have been posted.
     */
    public void editAttribute() throws GeneralException {
        if (_attributeId != null) {
            ObjectAttributeDTO src = _dto.getAttribute(_attributeId);
            if (src != null)
                _attribute = new ObjectAttributeDTO(src);
        }

        if (_attribute == null)
            throw new GeneralException("No attribute selected for editing");
    }
    
    /**
     * Begin creation of a new attribute.
     */
    public void newAttribute() {
        // leave this null so we know it's new
        _attributeId = null;
        _attribute = new ObjectAttributeDTO(_dto.getClassName());
    }

    /**
     * Delete the selected attribute.
     */
    public void deleteAttribute() {

        if (_attributeId != null) {
            ObjectAttributeDTO att = _dto.getAttribute(_attributeId);
            if (att != null)
                _dto.remove(att);
        }

        // clear selection state
        cancelAttribute();
    }

    /**
     * Commit the changes to the attribute back into the DTO.
     */
    public boolean saveAttribute() {

        if (_attribute != null) {
            if (Util.isNullOrEmpty(_attribute.getName())) {
                addMessage(new Message(Type.Error, MessageKeys.LABEL_NAME_IS_REQUIED));
                return false;
            }
            _dto.replace(_attribute);
        }

        cancelAttribute();
        return true;
    }

    /**
     * Forget about attribute editing state.
     */
    public void cancelAttribute() {
        _attribute = null;
        _attributeId = null;
    }

    /**
     * Commit the changes from our DTO into the persistent object.
     * This should be overloaded int he subclass if it
     * has more to do.
     */
    public void commit(ObjectConfig config)
        throws GeneralException {

        _dto.commit(config);

    }

    public boolean validate(ObjectConfig config, Class<? extends SailPointObject> spObjClass) throws GeneralException {
        boolean isValid = true;
        
        if (_attribute != null) {
            String originalName = _attribute.getOriginalName();
            if (originalName == null) {
                originalName = "";
            }
            String newName = _attribute.getName();
            if (newName == null) {
                newName = "";
            }

            if (Util.isNullOrEmpty(_attribute.getName())) {
                addMessage(new Message(Type.Error, MessageKeys.LABEL_NAME_IS_REQUIED));
                isValid = false;
            } else {
                if (_attributeId == null || !newName.equals(originalName)) {
                    isValid = !config.getObjectAttributeMap().containsKey(_attribute.getName());
                    if (!isValid) {
                        addMessage(new Message(Type.Error, MessageKeys.ERR_DUPLICATE_ATTR_NAME, _attribute.getName()));
                    } else {

                        if (spObjClass != null) {
                            isValid &= !isReserved(_attribute.getName(), spObjClass);
                        }

                        if (!isValid) {
                            addMessage(new Message(Type.Error, MessageKeys.ERR_RESERVED_ATTR_NAME, _attribute.getName()));
                        }
                    }
                }
            }
        } else {
            throw new GeneralException("No attribute selected for editing");
        }
        
        return isValid;
    }
    
    private boolean isReserved(String name, Class<? extends SailPointObject> spObjClass) {        
        return ObjectUtil.isReservedAttributeName(getContext(), spObjClass, name);
    }
}
