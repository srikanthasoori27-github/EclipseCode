/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * The definition of the possible rights that may
 * exist in a Permission.  The Right class represents
 * each right, we collect them here.
 *
 * Author: Jeff?
 * 
 * I'm stopping short of making Rights individual
 * SailPointObjects because if you need them at all
 * you usually need the entire collection.  This also
 * makes it easier to have multiple RightConfigs for
 * score testing. 
 *
 * Permission objects still reference the individual
 * Right objects by name.  If we blew them out into
 * SailPointObjects they could in theory be formal 
 * references but this seems overkill since these
 * are relatively static and we're trying to keep the
 * Permission model simple so it can be a component.
 * 
 * Note that the rights we enumerate will be very close
 * the event types we enumerate for activity log records.
 * We may wish to combine these, but they feel different
 * enough to keep seperate for now.
 * 
 * I'm making these SailPointObjects in case we want to 
 * have a global set of these that can be shared in a few
 * places.   These can also be stored inline for the unit
 * tests, such as within a ScoreDefinition.
 */

package sailpoint.object;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of the possible rights that can
 * exist in a Permission.  The Right class represents
 * each right, they are collected here.
 *
 * There is normally only one of these objects 
 * in the database.
 */
@XMLClass
public class RightConfig extends SailPointObject
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the default right configuration object.
     * This is the one the system normally uses, but there could be
     * others for use in unit tests and what-if analysis.
     */
    public static final String OBJ_NAME = "RightConfig";

    /**
     * The set of rights.
     */
    List<Right> _rights;

    /**
     * Name lookup cache.
     */
    Map<String,Right> _map;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RightConfig() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.LIST)
    public void setRights(List<Right> rights) {
        _rights = rights;
    }
    
    public List<Right> getRights() {
        return _rights;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lookup a right definition by name.
     * Since this is used in scoring, might want to give out the entire
     * Map so it can be cached by the Scorekeeper.
     */
    public Right getRight(String name) {

        if (_map == null) {
            _map = new HashMap<String,Right>();
            if (_rights != null) {
                for(Right r : _rights) {
                    if (r.getName() != null)
                        _map.put(r.getName(), r);
                }
            }
        }
        
        return _map.get(name);
    }

}
