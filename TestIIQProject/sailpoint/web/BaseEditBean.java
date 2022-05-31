/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */

package sailpoint.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;


/**
 * We extend PageCodeBase to get some useful general purpose JSF utilities.
 */
public abstract class BaseEditBean<E extends SailPointObject> extends BaseBean
{
    private static Log log = LogFactory.getLog(BaseEditBean.class);

    /**
     * If the FORCE_LOAD option is set to true in the session, getObject() will
     * reload the object instead of attempting to load the object from the
     * session.  This option is removed from the session when read.
     */
    public static final String FORCE_LOAD = "forceLoad";
    public static final String EDITED_OBJ_ID = "id";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Unique id of the selected object.  May be null until one is selected
     * from the list page.
     */
    protected String _objectId;

    /**
     * Key used to store this object on the HTTP session
     */
    protected String _sessionKey;

    /**
     * Currently selected object.  May be null until one is
     * selected from the list page.  Used by both the details
     * and edit pages.
     */
    protected E _object;
    
    private Map<String, Object> editState;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public BaseEditBean() {
        super();
        initObjectId();
        restoreFromHistory();
    }
    
    /**
     * This method sets up the ID of the object that is being edited.  If a subclass needs
     * to special-case this (for example if two objects are being edited in the same form),
     * it should override this method.
     */
    protected void initObjectId() {
        _objectId = super.getRequestOrSessionParameter("id");
        if (_objectId == null)
            _objectId = super.getRequestOrSessionParameter("editForm:id");

        // If we haven't yet retrieved the object ID, look for the object ID
        // that was posted from the logout form.  This is a trick that happens
        // when we auto-logout due to session expiration.  When the user clicks
        // the Login button, we use javascript to try to copy the ID of the
        // object into the logout form so that we can automatically redirect to
        // the right place after the login.
        if (_objectId == null)
            _objectId = super.getRequestOrSessionParameter("logoutForm:id");
    }

    /**
     * Restore state from the NavigationHistory if we can retrieve state for
     * this page.
     */
    public void restoreFromHistory() {
        editState = retrieveEditStateFromSession();
        if ((null == _objectId) && (this instanceof NavigationHistory.Page)) {
            NavigationHistory.getInstance().restorePageState((NavigationHistory.Page) this);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     *
     */
    public void setObjectId(String id) {
        _objectId = id;
    }

    /**
     *
     */
    public String getObjectId() {
        return _objectId;
    }

    /**
     *
     */
    public String getSessionKey() {
        if ( _sessionKey == null ) {
            Class scope = getScope();
            if ( scope != null ) {
                if ( _objectId != null ) {
                    _sessionKey = scope.getName() + ":" + _objectId;
                } else {
                    _sessionKey = scope.getName() + ":new";
                }
            }
        }

        return _sessionKey;
    }  // getSessionKey()
    
    public String getEditStateSessionKey() {
        return getSessionKey() + ":editState";
    }

    /**
     * Get whether the object being managed by this bean is stored on the HTTP
     * session to retain state between requests.
     *
     * @return Whether the object being managed by this bean is stored on the
     *         HTTP session to retain state between requests.
     */
    public abstract boolean isStoredOnSession();
    
    /**
     * The class of object we're editing.  Must be set by the
     * subclass.
     */
    protected abstract Class<E> getScope();

    /**
     *
     */
    public void setObject(E object) {
        _object = object;
    }

    /**
     * Flush any HttpSession state when we're logically
     * done with an object.  May be overloaded if the subclass
     * has its own attributes.
     */
    public void clearHttpSession() {

        Map session = getSessionScope();
        session.remove(getSessionKey());
        session.remove(getEditStateSessionKey());
    }

    /**
     * @throws GeneralException
     *
     */
    public E getObject() throws GeneralException {
        initFacesVariables();

        // Clear out anything we have cached if FORCE_LOAD is true.
        Map session = getSessionScope();
        boolean forceLoad = Util.otob(session.get(FORCE_LOAD));
        session.remove(FORCE_LOAD);
        if (forceLoad) {
            _object = null;
            clearHttpSession();
            // TODO: Need to clear the edit state out here as well.  In fact, this forceLoad funny business 
            // should take place in the constructor, not here, when it may be too late.  I am reluctant to 
            // fix this properly right now because we are looking to release 4.0 and I don't know what side 
            // effects this could have elsewhere, but I want to keep a reminder for later.  The clearEditState() 
            // method should be removed once this is fixed because it will no longer be needed. -- Bernie, 10/17/09
        }
        
        if ( _object == null ) {
            if ( _objectId != null && _objectId.trim().length() > 0) {
                _object = retrieveObjectFromSession();
                if ( _object == null ) {
                    _object = getContext().getObjectById(getScope(), _objectId);
                    if (_object == null) {
                        _object = createObjectInternal();
                    }
                    // Load everything here because it will be detached once the request ends
                    // and everything will be rendered inaccessible
                    if (_object != null) {
                        _object.load();
                    }

                    storeObjectOnSession(_object);
                }
            } else {
                _object = retrieveObjectFromSession();
                if ( _object == null ) {
                    _object = createObjectInternal();
                    // Load everything here because it will be detached once the request ends
                    // and everything will be rendered inaccessible
                    if (_object != null) {
                        _object.load();
                    }
                    storeObjectOnSession(_object);
                }
            }            
        } else {
            String id = _object.getId();

            if ( ( id != null ) && _objectId != null && 
                 _objectId.trim().length() > 0 && ( id.compareTo(_objectId) != 0 ) ) {
                // _object was already populated, but it's id
                // this bean's objectId don't currently match, so
                // re-get from db
                clearHttpSession();
                if (_objectId == null) {
                    _object = null;
                }
                else {
                    _object = getContext().getObjectById(getScope(), _objectId);

                    storeObjectOnSession(_object);
                }
            }
        }
        
        // this will throw an exception if the user does not have rights to the object
        if (!validateUserAccess(_object)){
            clearHttpSession();
            throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
        }

        return _object;
    }

    /**
     * Call down to the subclass to create the object, then attempt to set a
     * default assigned scope.
     */
    private E createObjectInternal() throws GeneralException {
        
        E obj = createObject();

        if (null != obj) {
            // Attempt to pre-populate the assigned scope field.
            List<Scope> controlled = getEffectiveControlledScopes();
            if ((null != controlled) && !controlled.isEmpty()) {
                // There is a single controlled scope, automatically choose
                // this one.
                if (1 == controlled.size()) {
                    obj.setAssignedScope(controlled.get(0));
                }
            }
            else {
                Configuration config = getContext().getConfiguration();
                boolean unscopedViewable =
                    config.getBoolean(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, true);

                // If the user doesn't control a scope and unscoped objects
                // are hidden we'll show an error.
                Identity user = super.getLoggedInUser();
                if (!unscopedViewable &&
                    ((null == user) || !Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities()))) {
                    Message msg =
                        new Message(Message.Type.Warn, MessageKeys.ERR_NO_CTRL_SCOPE_FOR_CREATE);
                    super.addMessage(msg, msg);
                }
            }
        }
        
        return obj;
    }

    @SuppressWarnings("unchecked")
    private E retrieveObjectFromSession() {
        if (isStoredOnSession())
            return (E) getSessionScope().get(getSessionKey());
        return null;
    }

    @SuppressWarnings("unchecked")
    private void storeObjectOnSession(E o) {
        if (isStoredOnSession()) {
            getSessionScope().put(getSessionKey(), o);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> retrieveEditStateFromSession() {
        if (isStoredOnSession()) {
            return (Map<String, Object>) getSessionScope().get(getEditStateSessionKey());
        }
        return null;
    }
    
    
    @SuppressWarnings("unchecked")
    private void storeEditStateOnSession() {
        if (isStoredOnSession() && editState != null) {
            String editStateKey = getEditStateSessionKey();
            getSessionScope().put(editStateKey, editState);
        }
    }
    
    private void initEditState() {
        editState = new HashMap<String, Object>();
        storeEditStateOnSession();
    }

    /**
     * Must be overridden by subclass.
     */
    public E createObject() {
        return null;
    }
    
    /**
     * Specify an object that this bean should track
     * @param key String key by which the specified object will be identified
     * @param object Object that needs to be stored and fetched in/from the session
     */
    public void addEditState(String key, Object object) {
        if (editState == null) {
            initEditState();
        }
        
        editState.put(key, object);
    }
    
    /**
     * Fetch a tracked object
     * @param key String key that identifies the object being fetched
     * @return the tracked object
     */
    public Object getEditState(String key) {
        Object editStateObj = null;
        
        getEditState();
        
        if (editState != null && key != null) {
            editStateObj = editState.get(key);
        }
        
        return editStateObj;
    }
    
    /**
     * Stop tracking the specified object
     * @param key String key that identifies the object that we want to quit tracking
     */
    public void removeEditState(String key) {
        getEditState();
        
        if (editState != null && key != null) {
            editState.remove(key);
        }
    }

    private Map<String, Object> getEditState() {
        if (editState == null) {
            editState = retrieveEditStateFromSession();
        }
        
        return editState;
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Default Action Handlers
    //
    //////////////////////////////////////////////////////////////////////

    /*
     * Refresh handler.
     * This just provides a way to re-render the page after some selection
     * is made, useful in places where client-side sliding divs don't
     * work, or you just to tired to try.
     */
    public String refreshAction() {
        return null;
    }

    /**
     * "Edit" action handler for the edit page.
     */
    public String editAction() throws GeneralException {
        return "edit";
    }  // editAction()

    /**
     * "New" action handler for the edit page.
     */
    public String newAction() throws GeneralException {
        _object = createObjectInternal();
        return "new";
    }

    /**
     * "Cancel" action handler for the edit page.
     * Throw away any pending changes by refreshing the selected user.
     */
    public String cancelAction() {
        clearHttpSession();
        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null)
            outcome = "cancel";

        return outcome;
    }

    /**
     * "Save" action handler for the edit page.
     * Commit the changes to the persistent store, and refresh the object
     * in case the save had side effects on some of the properties.
     */
    public String saveAction() {
        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null)
            outcome = "save";

        try {
            if (getObject() != null) {
                SailPointContext context = getContext();
                ObjectUtil.checkIllegalRename(context, _object);
                context.saveObject(_object);
                context.commitTransaction();
                clearHttpSession();
            }
        } catch (GeneralException ex) {
            String msg = "Unable to save object named '" +
                _object.getName() + "': " + 
                ex;
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, ex), null);
            outcome = "";
        }  

        return outcome;
    }  // saveAction()

    /**
     * "Delete" action handler for the list page.
     * Remove the object from the persistent store.
     *
     * TODO need support for a confirmation page
     * @throws GeneralException
     */
    public void deleteObject(ActionEvent event) throws GeneralException {
        initFacesVariables();
        E obj = getObject();
        if (obj != null) {
            getContext().attach(obj);
            getContext().removeObject(obj);
            getContext().commitTransaction();
        }
    }

    public int getCount() throws GeneralException {
        return getContext().countObjects(getScope(), new QueryOptions());
    }
    
    /**
     * This is an interim hack that lets subclasses reset the edit state.  Obviously
     * this is a very dangerous operation that should only be undertaken when one absolutely
     * needs it.  This works around the problem described in the comment found in the forceLoad
     * section of getObject()
     * @private 
     */
    protected void clearEditState() {
        editState = null;
    }

}  // class BaseObjectBean
