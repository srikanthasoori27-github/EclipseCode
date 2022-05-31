/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object encapsulating various editing state for an object.
 * This is saved on the HttpSession between requests.
 *
 * I added this because BaseObjectBean only manages one SailPointObject,
 * and we often need more state.  Also, all of our backing beans
 * have historically been request scoped rather than session scoped,
 * in theory to support concurrent editing of different objects, 
 * though in practice that almost never happens.
 *
 * Think about making BaseObjectBean aware of this!!
 * Note that we don't store the actual SailPointObject we're editing,
 * BaseObjectBean currently manages that.  Need to merge these to 
 * to reduce complexity.
 *
 * This object must be completely serializable in order to support
 * clustering.
 */
package sailpoint.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.object.SailPointObject;
import sailpoint.api.SailPointContext;

/**
 * Backing bean for the Identity view page.
 */
public class EditSession<E extends SailPointObject>
    implements Serializable 
{
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(EditSession.class);

    /**
     * The id of the object we're maintaining state for.
     * Used to determine if an object we find on the HttpSession
     * is still relevant to the SailPointObject being edited.
     * This is needed by the policy pages because we've got this
     * awkward set of old and new beans that get involved in 
     * handling the request and they don't have the same conventions
     * for saving state, and in particular one clears the FORCE_LOAD
     * flag before the other has a chance to see it and clear the edit state.
     * This will be fixed if we ever migratae SOD and Activity policies
     * away from BaseObjectBean but until that happens we have to 
     * manage cache invalidation of the EditSession at a higher level.
     */
    String _objectId;

    /**
     * A list of objects to be deleted.
     * Necessary because we're not using cascade='delete-orphan' 
     * for several good reasons.  This means that simply removing an object
     * from a collection does *not* delete it from the Hibernate database.
     * You have to delete it manually.  In the UI tier, this is awkward
     * because we can't commit the transaction between page refreshes,
     * but we don't want the deleted objects to show up in the table.
     * 
     * This means we have to remove the objects from the collection, but
     * save them in an internal list so that when the Save button is
     * eventually clicked, we can whip through the list and call
     * getContext().removeObject before committing the transaction.
     *
     * Hibernate: "Transparent" Persistence For Java !
     *
     */
    List<SailPointObject> _deletedObjects;

    /**
     * A list of objects to be created.
     * Necessary because cascade="all" doesn't appear to be creating these
     * properly, or perhaps it is something in saveObject that isn't
     * wiring things up properly.
     */
    List<SailPointObject> _newObjects;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public EditSession()
    {
    }

    public EditSession(E obj)
    {
        if (obj != null)
            _objectId = obj.getId();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void setObjectId(String id) {
        _objectId = id;
    }

    public String getObjectId() {
        return _objectId;
    }

    public List<SailPointObject> getDeletedObjects() {
        return _deletedObjects;
    }

    public void addDeletedObject(SailPointObject o) {
        if (_deletedObjects == null) 
            _deletedObjects = new ArrayList<SailPointObject>();
        _deletedObjects.add(o);
    }

    public List<SailPointObject> getNewObjects() {
        return _newObjects;
    }

    public void addNewObject(SailPointObject o) {
        if (_newObjects == null) 
            _newObjects = new ArrayList<SailPointObject>();
        _newObjects.add(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called when we're resurecting the backing bean.  Before we attach
     * the root object, null out any ids for new objects that had ids 
     * generated in the previous transaction.
     */
    public void attachAction() throws GeneralException {

        if (_newObjects != null) {
            for (SailPointObject o : _newObjects) {
                o.setId(null);
            }
        }
    }

    /**
     * Called when we're processing a Save action.
     * Delete any objects marked for deletion.
     */
    public void saveAction(SailPointContext context) 
        throws GeneralException {

        // we can assume that _newObjects have already been attached?

        if (_deletedObjects != null) {
            for (SailPointObject o : _deletedObjects) {

                // can sometimes get here if we add something then
                // delete it before comitting the add, ignore
                // if there is no persistent id
                if (o.getId () != null) {
                    context.removeObject(o);
                }
            }
        }
    }


}
