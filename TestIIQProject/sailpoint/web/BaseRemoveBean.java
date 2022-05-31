/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.faces.model.SelectItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A base JSF bean for removing a list of objects (objectsToRemove) from another
 * object (owningObject) and optionally deleting the them (objectsToDelete).
 * This can also optionally cleanup references that will be broken if the object
 * is deleted by implementing cleanupReferences().  If this is not implemented,
 * the UI should take care to not specify any objects to be deleted that still
 * have references.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BaseRemoveBean<R extends SailPointObject, O extends SailPointObject>
    extends BaseBean {

    public static final String OWNING_OBJECT_ID = "owningObjectId";
    public static final String IDS_TO_REMOVE = "idsToRemove";

    private Class<R> toRemoveObjectClass;
    private Class<O> owningObjectClass;

    private String owningObjectId;
    private List<String> idsToRemove;
    private List<String> idsToDelete;

    // Fields that are calculated and cached by the getters.
    private List<R> objectsToRemove;
    private List<R> objectsToDelete;
    private Map<R, List<? extends SailPointObject>> referencedObjectMap;
    private List<R> nonReferencedObjects;
    private O owningObject;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.  Pulls the ID's of the objects to remove and the owning
     * object ID from the request if present.  Subclasses should provide a
     * default constructor that calls to this constructor setting the object
     * classes.
     * 
     * @param  toRemoveObjectClass  The Class of object to remove.
     * @param  owningObjectClass    The Class of the object from which the
     *                              objects will be removed.
     */
    public BaseRemoveBean(Class<R> toRemoveObjectClass,
                          Class<O> owningObjectClass) {

        super();

        this.toRemoveObjectClass = toRemoveObjectClass;
        this.owningObjectClass = owningObjectClass;

        String toRemove = super.getRequestParameter(IDS_TO_REMOVE);
        if (null != toRemove) {
            this.idsToRemove = Util.csvToList(toRemove);
        }

        String id = Util.getString(super.getRequestParameter(OWNING_OBJECT_ID));
        if (null != id) {
            this.owningObjectId = id;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ABSTRACT METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Find all references to the given object specified for removal.
     * 
     * @param  r  The object for which to return all references.
     * 
     * @return A List of all objects that reference the given object, or null
     *         or an empty List if there are none.
     */
    abstract List<? extends SailPointObject> findReferences(R r)
        throws GeneralException;

    /**
     * Remove the given object from the owning object.
     * 
     * @param  r  The object to remove from the owning object.
     */
    abstract void removeFromOwningObject(R r) throws GeneralException;

    /**
     * Clean up all references to the given object that is being deleted from
     * all objects in the list of refs.  This is an optional operation that
     * should throw an UnsupportedOperationException if this bean does not allow
     * deleting objects that have other references.
     * 
     * @param  r     The object that is to be deleted.
     * @param  refs  A list of objects that reference the object that is about
     *               to be deleted.  These should be acted upon to remove all
     *               references to the given object.
     * 
     * @throws UnsupportedOperationException  If the implementing class only
     *    allows deleting objects that will be orphaned after removing from
     *    the owning object.
     */
    abstract void cleanupReferences(R t, List<? extends SailPointObject> refs)
        throws GeneralException, UnsupportedOperationException;

    /**
     * Subclasses can override this to return a JSF outcome after the call to
     * remove().  If not implemented, remove() return the "removeSuccess"
     * outcome.
     * 
     * @return The JSF outcome to return from remove().
     */
    String handleNavigation() {
        return null;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ/WRITE PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public List<String> getIdsToRemove() {
        return this.idsToRemove;
    }

    public void setIdsToRemove(List<String> idsToRemove) {
        this.idsToRemove = idsToRemove;
    }

    public List<String> getIdsToDelete() throws GeneralException {

        // Default to the non-referenced objects.
        if (null == this.idsToDelete) {

            List<R> nonRefd = this.getNonReferencedObjects();

            if (null != nonRefd) {
                this.idsToDelete = new ArrayList<String>();
    
                for (R r : nonRefd) {
                    this.idsToDelete.add(r.getId());
                }
            }
        }

        return this.idsToDelete;
    }

    public void setIdsToDelete(List<String> selectedIds) {
        this.idsToDelete = selectedIds;
    }

    public String getOwningObjectId() {
        return owningObjectId;
    }

    public void setOwningObjectId(String ownerObjectId) {
        this.owningObjectId = ownerObjectId;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the list of objects selected for removal from the owning object.
     * 
     * @return The list of objects selected for removal from the owning object.
     */
    public List<R> getObjectsToRemove() throws GeneralException {

        if (null == this.objectsToRemove) {
            if (null != this.idsToRemove) {
                this.objectsToRemove = new ArrayList<R>(this.idsToRemove.size());
                for (String id : this.idsToRemove) {
                    this.objectsToRemove.add(getContext().getObjectById(this.toRemoveObjectClass, id));
                }
            }
        }

        return this.objectsToRemove;
    }

    /**
     * Get the list of objects selected for deletion.
     * 
     * @return The list of objects selected for deletion.
     */
    public List<R> getObjectsToDelete() throws GeneralException {

        if (null == this.objectsToDelete) {
            if (null != this.idsToDelete) {
                this.objectsToDelete = new ArrayList<R>(this.idsToDelete.size());
                for (String id : this.idsToDelete) {
                    this.objectsToDelete.add(getContext().getObjectById(this.toRemoveObjectClass, id));
                }
            }
        }

        return this.objectsToDelete;
    }

    /**
     * Get a Map that maps each object being removed to the list of objects that
     * reference it.  This does not include the owning object.  If an object
     * that is being removed is not referenced, it is not included in the map.
     * 
     * @return A Map that maps each object being removed to the list of objects
     *         that reference it.
     */
    public Map<R,List<? extends SailPointObject>> getReferencedObjectMap()
        throws GeneralException {

        if (null == this.referencedObjectMap) {

            List<R> objsToRemove = this.getObjectsToRemove();
            if (null != objsToRemove) {

                this.referencedObjectMap = new HashMap<R, List<? extends SailPointObject>>();

                for (R obj : objsToRemove) {
                    List<? extends SailPointObject> refs = findReferences(obj);
                    if (null != refs) {
                        if (null != this.getOwningObject()) {
                            refs.remove(this.getOwningObject());
                        }
                        this.referencedObjectMap.put(obj, refs);
                    }
                }
            }
        }

        return this.referencedObjectMap;
    }

    /**
     * Get the object that owns the objects to be removed.  This is the object
     * from which the request was removal request was initiated.
     * 
     * @return The object that owns the objects to be removed.
     */
    protected O getOwningObject() throws GeneralException {
        
        if ((null == this.owningObject) && (null != this.owningObjectId)) {
            this.owningObject =
                getContext().getObjectById(this.owningObjectClass, this.owningObjectId);
        }
        return this.owningObject;
    }

    /**
     * Return a list of SelectItems for the non-referenced objects.
     * 
     * @return A list of SelectItems for the non-referenced objects.
     */
    public List<SelectItem> getNonReferencedObjectSelectItems()
        throws GeneralException {

        List<SelectItem> items = new ArrayList<SelectItem>();

        List<R> nonRefd = this.getNonReferencedObjects();
        if (null != nonRefd) {
            for (R r : nonRefd) {
                items.add(new SelectItem(r.getId(), r.getName()));
            }
        }

        return items;
    }

    /**
     * Get the list of objects selected for removal that are not referenced by
     * any other objects.
     * 
     * @return The list of objects selected for removal that are not referenced by
     *         any other objects.
     */
    public List<R> getNonReferencedObjects() throws GeneralException {

        if (null == this.nonReferencedObjects) {

            Map<R,List<? extends SailPointObject>> refs = this.getReferencedObjectMap();

            if (null != refs) {

                this.nonReferencedObjects = new ArrayList<R>();

                for (Map.Entry<R,List<? extends SailPointObject>> entry : refs.entrySet()) {
                    if ((null == entry.getValue()) || entry.getValue().isEmpty()) {
                        this.nonReferencedObjects.add(entry.getKey());
                    }
                }
            }
        }
        
        return this.nonReferencedObjects;
    }

    /**
     * Get the list of objects selected for removal that are referenced by at
     * least one other object.
     * 
     * @return The list of objects selected for removal that are referenced by
     *         at least one other object.
     */
    public List<R> getReferencedObjects() throws GeneralException {

        List<R> refd = null;

        List<R> toRemove = this.getObjectsToRemove();
        if (null != toRemove) {
            refd = new ArrayList<R>(getObjectsToRemove());
            refd.removeAll(getNonReferencedObjects());
        }

        return refd;
    }

    /**
     * Return true if any of the idsToRemove will be orphaned (ie - not have any
     * other references) as a result of removing them from the owning object.
     * 
     * @return  True if any of the idsToRemove will be orphaned (ie - not have
     *          any other references) as a result of removing them from the
     *          owning object.
     */
    public boolean getWillAnyBeOrphaned() throws GeneralException {

        List<R> nonRefd = this.getNonReferencedObjects();
        return ((null != nonRefd) && !nonRefd.isEmpty());
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to remove and possibly delete the objects.  This will remove any
     * object returned by getObjectsToRemove() from the owning object, and will
     * delete it if it is in the getObjectsToDelete() list.  If there are any
     * references to this object and the object is specified to be deleted, they
     * will be cleaned up with the cleanupReferences() method.  The UI should
     * not allow selecting objects to be deleted if the object is known to have
     * references and cleanupReferences() throws an
     * UnsupportedOperationException.
     * 
     * @return The JSF outcome.
     * 
     * @throws UnsupportedOperationException  If any objects are specified to be
     *   deleted, are referenced by objects other than the owning object, and
     *   the subclass does not support cleanupReferences().
     */
    public String remove() throws GeneralException, UnsupportedOperationException {

        if (null != this.idsToRemove) {

            List<R> toDelete = this.getObjectsToDelete();
            for (String toRemoveId : this.idsToRemove) {

                R obj = getContext().getObjectById(this.toRemoveObjectClass, toRemoveId);
                this.removeFromOwningObject(obj);

                // Delete it if it is marked for deletion.
                if ((null != toDelete) && toDelete.contains(obj)) {

                    // Cleanup the references if there are any.
                    Map<R, List<? extends SailPointObject>> allRefs =
                        this.getReferencedObjectMap();
                    List<? extends SailPointObject> refs = allRefs.get(obj);
                    if ((null != refs) && !refs.isEmpty()) {
                        this.cleanupReferences(obj, refs);
                    }

                    getContext().removeObject(obj);
                }
            }

            getContext().commitTransaction();
        }

        String outcome = this.handleNavigation();
        return (null != outcome) ? outcome : "removeSuccess";
    }
}
