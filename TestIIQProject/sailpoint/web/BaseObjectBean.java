/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */

package sailpoint.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

import javax.faces.event.ActionEvent;

import java.util.List;
import java.util.Map;

/**
 * We extend PageCodeBase to get some useful general purpose JSF utilities.
 */
public class BaseObjectBean<E extends SailPointObject> extends BaseBean
{
    private static Log log = LogFactory.getLog(BaseObjectBean.class);

    /**
     * If the FORCE_LOAD option is set to true in the session, getObject() will
     * reload the object instead of attempting to load the object from the
     * session.  This option is removed from the session when read.
     */
    public static final String FORCE_LOAD = "forceLoad";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The class of object we're editing.  Must be set by the
     * subclass.
     */
    Class<E> _scope;

    /**
     * Unique id of the selected object.  May be null until one is selected
     * from the list page.
     */
    String _objectId;

    /**
     * Unique name of the object to edit.
     *
     * This is used with a few singleton configuration objects. Where the
     * name is hard coded in the bean but we don't know the id.
     * I tried to use _objectId for this but there is sensitive logic
     * around that that assumes it is a unique Hibernate id.
     */
    String _objectName;

    /**
     * Key used to store this object on the HTTP session
     */
    String _sessionKey;

    /**
     * Currently selected object.  May be null until one is
     * selected from the list page.  Used by both the details
     * and edit pages.
     */
    E _object;

    /**
     * Whether the object should be stored on the session or not.
     */
    private boolean _storedOnSession = true;

    /**
     * Temporary kludge for some pages that must always have the
     * objects reattached.
     * !! This should always be true, but need to carefully consider
     * the consequences, may need to push the "take away stale id from
     * last transaction" logic lower into SailPointContext.
     */
    boolean _alwaysAttach;

    /**
     * Yet another "temporary" kludge to manage objects in a detached
     * state until we're ready to save.  You would think this would
     * be just !_alwaysAttach but attachObject() has logic that will
     * attach if getId() != null.  This really needs to be refactored
     * into a different base bean and we need to migrate all
     * over to fully loading the object up front and keeping
     * it detached rather than messing with Hibernate during a
     * long conversation.
     */
    boolean _neverAttach;


    /**
     * When we initially fetch the object load the contents.
     * Probably not necessary in most cases, but for objects
     * that have Parent->Child relationships its typically
     * necessary.
     * Initially target for the TaskDefinitionBean.
     */
    boolean _loadOnFetch;

	GridState _gridState;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public BaseObjectBean() {
        super();

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

        restoreFromHistory();
    }

    /**
     * Restore state from the NavigationHistory if we can retrieve state for
     * this page.
     */
    public void restoreFromHistory() {

        if ((null == _objectId) && (this instanceof NavigationHistory.Page)) {
            NavigationHistory.getInstance().restorePageState((NavigationHistory.Page) this);
        }
    }

    public void setAlwaysAttach(boolean b) {
        _alwaysAttach = b;
    }

    public void setNeverAttach(boolean b) {
        _neverAttach = b;
    }

    public void setLoadOnFetch(boolean b) {
        _loadOnFetch = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Specify the persistent object class we will be dealing with.
     * This must be set by the subclass.
     */
    public void setScope(Class<E> c) {
        _scope = c;
    }

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
     * Should only be used by beans that edit singleton
     * configuration objects, like the ObjectConfigBeans.
     */
    public void setObjectName(String name) {
        _objectName = name;
    }

    /**
     *
     */
    public String getSessionKey() {
        if ( _sessionKey == null ) {
            if ( _scope != null ) {
                if ( _objectId != null ) {
                    _sessionKey = _scope.getName() + ":" + _objectId;
                } else {
                    _sessionKey = _scope.getName() + ":new";
                }
            }
        }

        return _sessionKey;
    }  // getSessionKey()
    
    public String getSignatureMeaning() {
        try {
            return getNotary().getSignatureMeaning(this.getObject());
        }
        catch (GeneralException e) {
            log.error(e);
        }
        return null;
    }

    /**
     * Set whether the object being managed by this bean should be stored on
     * the HTTP session to retain state between requests or not.
     *
     * @param  b  Whether to store the object on the HTTP session.
     */
    public void setStoredOnSession(boolean b) {
        _storedOnSession = b;
    }

    /**
     * Get whether the object being managed by this bean is stored on the HTTP
     * session to retain state between requests.
     *
     * @return Whether the object being managed by this bean is stored on the
     *         HTTP session to retain state between requests.
     */
    public boolean isStoredOnSession() {
        return _storedOnSession;
    }

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
    }

    /**
     * Attach the object being edited to the Hibernate session
     * after we've resurrected it from the HttpSession.
     * This may be overloaded by subclasses if they have other
     * things to attach.
     */
    public void attachObject(E object) throws GeneralException {

        if (!_neverAttach) {
            // note that we need to check again since some subclasses
            // may overload this and null the _object id
            if (_alwaysAttach || object.getId() != null) {
                // having bizarre problems with id's changing for object
                // with _alwaysAttach, try to use a very big hammer
                if (_alwaysAttach)
                    getContext().decache();

                getContext().attach(object);
            }
        }
    }

    /**
     * Called whenver we fetch an object from the database for
     * the first time.  May be overloaded to do interesting
     * things like call load() or prepare some DTOs.
     */
    public void afterFetch(E object) {
    }

    /**
     * @throws GeneralException
     *
     */
    public E getObject() throws GeneralException {
        initFacesVariables();

        Map session = getSessionScope();
        boolean forceLoad = Util.otob(getRequestParam().get(FORCE_LOAD));
        if (!forceLoad) {
            forceLoad = Util.otob(session.get(FORCE_LOAD));
        }
        session.remove(FORCE_LOAD);

        // Clear out anything we have cached if FORCE_LOAD is true.
        if (forceLoad) {
            _object = null;
            clearHttpSession();
        }

        if ( _object == null ) {
            _objectId = trim(_objectId);
            if ( _objectId != null || _objectName != null) {
                _object = retrieveObjectFromSession();
                if ( _object == null ) {

                    // if we have a name, we normally dont' have an id
                    if (_objectName != null)
                        _object = getContext().getObjectByName(_scope, _objectName);
                    else {
                        // We have historically searched by both name
                        // and id here. This is a dumb idea, and we will now use just id
                        _object = getContext().getObjectById(_scope, _objectId);
                    }

                    if (_object != null)
                        afterFetch(_object);

                    if (_object == null) {
                        _object = createObjectInternal();
                    }
                    else if (_neverAttach) {
                        // fully load the object so we don't have to deal
                        // with attaching and lazy loading
                        _object.load();
                    }
                    if ((  _loadOnFetch ) && ( _object!= null ))
                        _object.load();
                    storeObjectOnSession(_object);
                }
                else if (!_neverAttach) {
                    if (_alwaysAttach || (_object.getId() != null && _object.getId().trim().length() > 0))
                        attachObject(_object);
                }

            } else {
                _object = retrieveObjectFromSession();
                if ( _object == null ) {
                    _object = createObjectInternal();
                    storeObjectOnSession(_object);
                }
                else if (!_neverAttach) {
                    if (_alwaysAttach || (_object.getId() != null && _object.getId().trim().length() > 0))
                        attachObject(_object);
                }
            }

            fixObject();

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
                    _object = getContext().getObjectById(_scope, _objectId);

                    if (_neverAttach)
                        _object.load();

                    if ( _loadOnFetch )
                        _object.load();
                    storeObjectOnSession(_object);
                    fixObject();
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

    public String trim(String src) {
        if (src != null) {
            src = src.trim();
            if (src.length() == 0)
                src = null;
        }
        return src;
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
                    ((null == user) || !Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities())) && showScopeError()) {
                    Message msg =
                        new Message(Message.Type.Warn, MessageKeys.ERR_NO_CTRL_SCOPE_FOR_CREATE);
                    super.addMessage(msg, msg);
                }
            }
        }

        return obj;
    }
    
    /** Determines whether the error: "The authenticated user does not have any controlled scopes and
     * will not be able to view this object after it is created." should be presented to the UI 
     * Override this to turn it off **/
    public boolean showScopeError() {
        return true;
    }

    public void fixObject() throws GeneralException{
    }

    @SuppressWarnings("unchecked")
    private E retrieveObjectFromSession() {
        if (isStoredOnSession()) {
            String key = getSessionKey();
            if ( key != null )
                return (E) getSessionScope().get(key);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void storeObjectOnSession(E o) {
        if (isStoredOnSession())
            getSessionScope().put(getSessionKey(), o);
    }

    /**
     * Must be overridden by subclass.
     */
    public E createObject() {
        return null;
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
    public String cancelAction() throws GeneralException {
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
    // TODO - need to make subclasses saveAction implementations to not need
    //        a GeneralException so that it can be removed (this implementation
    //        is ready
    public String saveAction() throws GeneralException {

        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null)
            outcome = "save";

        if (_object != null) {
            try {
                SailPointContext context = getContext();
                ObjectUtil.checkIllegalRename(context, _object);
                context.saveObject(_object);
                context.commitTransaction();
                clearHttpSession();
            }
            catch (GeneralException ex) {
                String msg = "Unable to save object named '" +
                    _object.getName() + "': " + 
                    ex.getMessage();
                log.error(msg, ex);
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, ex), null);
                outcome = "";
            }

        }  // if _object != null

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
        return getContext().countObjects(_scope, new QueryOptions());
    }

	/** Must Override this in the subclass to provide the name of the grid state **/
	public String getGridStateName() { 
		return null; 
	}
	
	/** load a configured gridstate object based on what type of cert it is **/
	public GridState loadGridState(String gridName) {
		GridState state = null;
		String name = "";
		IdentityService iSvc = new IdentityService(getContext());
		try {
			if(gridName!=null)
				state = iSvc.getGridState(getLoggedInUser(), gridName);
		} catch(GeneralException ge) {
			log.info("GeneralException encountered while loading gridstates: "+ge.getMessage());
		}

		if(state==null) {
			state = new GridState();
			state.setName(name);
		}
		return state;
	}

	public GridState getGridState() {
		if (null == this._gridState) {
			_gridState = loadGridState(getGridStateName());
		}
		return _gridState;
	}

	public void setGridState(GridState gridState) {
		this._gridState = gridState;
	}

}  // class BaseObjectBean
