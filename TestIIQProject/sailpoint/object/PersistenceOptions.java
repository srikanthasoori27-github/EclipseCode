/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * An object encapsulating options to control how SailPointContext 
 * manages objects in the Hibernate session.  To set options, 
 * create a PersistenceOptions object and call 
 * SailPointContext.setPersistenceOptions().
 *
 * Author: Jeff, Tapash
 * 
 * This was factored out of the PersistenceManager interface to make 
 * it easier to add options without having to change every 
 * implementation of  PersistenceManager to add methods.
 *
 * There are two things in here, first an experiment by Tapash
 * to declare classes as read-only which would prevent us from flushing
 * them.  Second, an option by Jeff to disable dirty checking and reqiure
 * that SailPointContext.saveObject be called for every object you want
 * saved.  
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An object encapsulating options to control how SailPointContext 
 * manages objects in the Hibernate session. To set options, 
 * create a PersistenceOptions object and call 
 * SailPointContext.setPersistenceOptions().
 */
public class PersistenceOptions {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When true dirty checking by comparing objects is disabled and
     * the application is required to call SailPointObject.setDirty, 
     * or one of the PersistenceManager/SailPointContext methods 
     * saveObject, removeObject, unlockObject, etc. This is intended
     * for use in tasks that operate on large numbers of objects,
     * notably aggregation and refresh.
     */
    private boolean _explicitSaveMode;

    /**
     * A flag to control when things that are marked immutable
     * can be modified. Used only in a few cases where an object 
     * needs to be locked down because it has been electronically 
     * signed (or some other reason marked readonly)
     * but still allow the system to update it for 
     * system purposes.
     */
    private boolean _allowImmutableModifications;

    /**
     * An older experiment for optimizing dirty checking, 
     * declaring classes that were to be considered read-only.  
     * This was never enabled in production.
     * In practice, this is difficult to use because it is not always
     * easy to predict what you will need to save.
     */
    private boolean _optimizeDirtyClasses = false;//off by default
    private boolean _currentlyOptimizing = false;
    private boolean _useUnmodifiableList = true;
    private List<Class<?>> _unModifiableClasses = new ArrayList<Class<?>>();
    private List<Class<?>> _modifiableClasses = new ArrayList<Class<?>>();

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public PersistenceOptions() {
    }

    public void setExplicitSaveMode(boolean b) {
        _explicitSaveMode = b;
    }
    
    public boolean isExplicitSaveMode() {
        return _explicitSaveMode;
    }

    public void setAllowImmutableModifications(boolean b) {
        _allowImmutableModifications = b;
    }

    public boolean isAllowImmutableModifications() {
        return _allowImmutableModifications;
    }


    public void setOptimizeDirtyClasses(boolean val) {
        _optimizeDirtyClasses = val;
    }

    public boolean isOptimizeDirtyActive() {
        return _optimizeDirtyClasses && _currentlyOptimizing;
    }
    
    public boolean shouldDirtyCheck(Object entity) {
        
        if (_useUnmodifiableList) {
            if (_unModifiableClasses.contains(entity.getClass())) {
                return false;
            } else {
                return true;
            }
        } else {
            if (_modifiableClasses.contains(entity.getClass())) {
                return true;
            } else {
                return false;
            }
        }
    }

    public void setModifiableClasses(List<Class<?>> value) {
        clearAllClasses();
        _useUnmodifiableList = false;
        _modifiableClasses.addAll(value);
        _currentlyOptimizing = true;
    }
    
    public void setModifiableClasses(Class<?>... value) {
        setModifiableClasses(Arrays.asList(value));
    }
    
    public void setUnModifiableClasses(List<Class<?>> value) {
        clearAllClasses();
        _useUnmodifiableList = true;
        _unModifiableClasses.addAll(value);
        _currentlyOptimizing = true;
    }

    public void setUnModifiableClasses(Class<?>... value) {
        setUnModifiableClasses(Arrays.asList(value));
    }

    public void clearModifiableClasses() {
        clearAllClasses();
        _currentlyOptimizing = false;
    }

    public void clearUnModifiableClasses() {
        clearAllClasses();
        _currentlyOptimizing = false;
    }
    
    private void clearAllClasses() {
        _modifiableClasses.clear();
        _unModifiableClasses.clear();
    }
    
}
