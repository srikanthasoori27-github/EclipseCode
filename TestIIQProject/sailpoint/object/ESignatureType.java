/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding the definition of one electronic signature "meaning".
 *
 * Author: Jeff
 *
 */

package sailpoint.object;

import java.util.Map;
import java.util.HashMap;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class holding the definition of one electronic signature "meaning".
 */
@XMLClass
public class ESignatureType extends AbstractXmlObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The well known name of the Configuration object that
     * contains the list of ESignatureTypes.
     */
    public static final String CONFIG_NAME = Configuration.ELECTRONIC_SIGNATURE;

    /**
     * The name of the attribute inside the Configuration object
     * that holds the List of ESignatureTypes.
     */
    public static final String CONFIG_ATT_TYPES = "types";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The canonical internal name used for referencing from workflows
     * and certification schedules.
     */
    String _name;

    /**
     * Optional display name to show in selection menus.
     */
    String _displayName;
     
    /**
     * Map of "meaning" text, keyed by locale.
     */
    Map<String,String> _meanings;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public ESignatureType() {
    }

    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String name) {
        _displayName = name;
    }

    public String getDisplayableName() {
        return !Util.isNullOrEmpty(_displayName) ? _displayName : _name;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Map<String, String> getMeanings() {
        return _meanings;
    }
    
    public void setMeanings(Map<String,String> meanings) {
        _meanings = meanings;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Look up a meaning by locale.
     */
    public String getMeaning(String locale) {
        return (_meanings != null) ? _meanings.get(locale) : null;
    }

    /**
     * Add a meaning for a locale.
     */
    public void addMeaning(String locale, String text) {

        if (locale != null) {
            if (_meanings == null)
                _meanings = new HashMap<String,String>();
            _meanings.put(locale, text);
        }
    }


}
