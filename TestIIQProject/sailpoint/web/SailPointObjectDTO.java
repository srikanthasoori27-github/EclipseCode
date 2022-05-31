/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for the common SailPointObject fields.
 *
 * Author: Jeff
 *
 * Since SailPointObjects almost always have at least one list of
 * child objects, we'll extend the ParentDTO class to get common
 * child DTO management methods.  If you don't have a child list
 * or don't want to use it, just pass the DTO class itself, e.g.
 *
 *    SomeDTO extends SailPointObjectDTO<SomeDTO>
 *
 * You'll get a child list of type List<SomeDTO> but you don't 
 * have to use it.
 * 
 * Note that like most of the DTO classes this isn't a complete DTO because
 * we don't copy the immutable fields like created, modified, lock, etc.   
 * We cannot therefore fully reconstruct a SailPointObject from this, 
 * the contents must to be merged into an existing SailPointObject.  
 * This object has typically been saved on the HttpSession but it could
 * also be fetched fresh from the database.
 * 
 */

package sailpoint.web;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class SailPointObjectDTO<E extends BaseDTO> extends ParentDTO<E>
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SailPointObjectDTO.class);

    /**
     * Repository id for existing objects. May not be changed.
     */
    String _id;

    /**
     * User defined name, may be changed.
     */
    String _name; 

    /**
     * Standard description.
     */
    String _description;

    /**
     * Repository id of the owner.
     */
    String _owner;

    /**
     * Repostitory id of the assigned scope.
     */
    String _scope;

    /**
     * True if object is considered disabled.
     */
    boolean _disabled;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public SailPointObjectDTO() {
    }

    public SailPointObjectDTO(SailPointObject src) {
        if (src != null) {
            _id = src.getId();
            _name = src.getName();
            _description = src.getDescription();
            _disabled = src.isDisabled();

            Identity owner = src.getOwner();
            if (owner != null)
                _owner = owner.getId();

            Scope scope = src.getAssignedScope();
            if (scope != null)
                _scope = scope.getId();
        }
    }

    /**
     * Clone for editing.
     * NOTE: this was before these became XML objects, should
     * use xmlclone now.
     */
    public SailPointObjectDTO(SailPointObjectDTO src) {
        _id = src.getPersistentId();
        _name = src.getName();
        _description = src.getDescription();
        _disabled = src.isDisabled();
        _owner = src.getOwner();
        _scope = src.getAssignedScope();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Note that we give this a more obvious name to avoid confusion
     * with the transient _uid we inherit from BaseDTO.
     */
    @XMLProperty
    public String getPersistentId() {
        return _id;
    }

    public void setPersistentId(String s) {
        _id = s;
    }

    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = trim(s);
    }

    @XMLProperty
    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = s;
    }

    @XMLProperty
    public String getOwner() {
        return _owner;
    }

    public void setOwner(String s) {
        _owner = trim(s);
    }

    @XMLProperty
    public String getAssignedScope() {
        return _scope;
    }

    public void setAssignedScope(String s) {
        _scope = trim(s);
    }

    @XMLProperty
    public boolean isDisabled() {
        return _disabled;
    }

    public void setDisabled(boolean b) {
        _disabled = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Suggest Properties
    //
    // The older suggest component wants to deal with objects rather than
    // names.  If you want to use it you have to use these property
    // names instead.  We could probably fix sp:suggest to give it an option
    // to only consume and produce names but we're moving toward Ext
    // sugggests anyway so do it there.
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public Identity getOwnerObject() {
        Identity owner = null;
        if (_owner != null) {
            try {
                //Will this always be id? -rap
                owner = getContext().getObjectById(Identity.class, _owner);
            }
            catch (GeneralException e) {
                // propagate this or allow it to become null?
                addMessage(e);
            }
        }
        return owner;
    }

    public void setOwnerObject(Identity owner) {
        if (owner != null)
            _owner = owner.getId();
        else
            _owner = null;
    }

    @XMLProperty
    public Scope getAssignedScopeObject() {
        Scope scope = null;
        if (_scope != null) {
            try {
                scope = getContext().getObjectById(Scope.class, _scope);
            }
            catch (GeneralException e) {
                // propagate this or allow it to become null?
                addMessage(e);
            }
        }
        return scope;
    }

    public void setAssignedScopeObject(Scope scope) {
        if (scope != null)
            _scope = scope.getName();
        else
            _scope = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Commit the changes into the target object.
     * The target object must already be attached to the Hibernate
     * session used by the SailPointContext.
     */
    public void commit(SailPointObject obj) throws GeneralException {

        obj.setName(trim(_name));
        obj.setDescription(trim(_description));
        obj.setDisabled(_disabled);
        
        String id = trim(_scope);
        if (id != null) {
            Scope scope = resolveById(Scope.class, id);
            if (scope != null)
                obj.setAssignedScope(scope);
        } else {
            obj.setAssignedScope(null);
        }

        id = trim(_owner);
        if (id == null) {
            // don't allow this to become ownerless?
            //At least Policy can set owner to null
            obj.setOwner(null);
        }
        else {
            Identity owner = resolveById(Identity.class, id);
            if (owner != null)
                obj.setOwner(owner);
        }
    }

}
