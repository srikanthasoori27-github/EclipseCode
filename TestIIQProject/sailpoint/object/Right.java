/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Part of the RightConfig model, a representation of a
 * specific right on a target object.  
 *
 * Author: Jeff?
 * 
 * There will be a default set of these, but the model needs
 * to be extensible to accomodate resources that have unusual rights.
 *
 * Note that the rights we enumerate will be very close
 * the event types we enumerate for activity log records.
 * We may wish to combine these, but they feel different
 * enough to keep seperate for now.
 *
 */

package sailpoint.object;

import sailpoint.tools.Internationalizer;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.Locale;

import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * Part of the RightConfig model, a representation of a
 * specific right on a target object. There will
 * be a default set of these, but the model is
 * extensible to accommodate resources that have unusual rights.
 */
@XMLClass
public class Right implements Cloneable, Serializable, IXmlEqualable<Right>
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The internal name of the right.
     */
    String _name;

    /**
     * The display name of the right. Eventually a message catalog key.
     */
    String _displayName;

    /**
     * Optional text description. Not sure these should be displayed,
     * but it helps for internal documentation.
     */
    String _description;

    /**
     * The risk score weight. This is used when calculating
     * the "extra entitlement" score.
     */
    int _weight;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Right() {
    }

    public boolean contentEquals(Right other) {
        
        return
        new EqualsBuilder()
            .append(getName(), other.getName())
            .append(getDisplayName(), other.getDisplayName())
            .append(getDescription(), other.getDescription())
            .append(getWeight(), other.getWeight())
            .isEquals();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setName(String s) {
        _name = s;
    }

    /**
     * The internal name of the right.
     */
    public String getName() {
        return _name;
    }

    @XMLProperty
    public void setDisplayName(String s) {
        _displayName = s;
    }

    /**
     * The display name of the right. Can be a message catalog key.
     */
    public String getDisplayName() {
        return _displayName;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setDescription(String s) {
        _description = s;
    }

    /**
     * Optional text description.
     */
    public String getDescription() {
        return _description;
    }

    @XMLProperty
    public void setWeight(int w) {
        _weight = w;
    }

    /**
     * The risk score weight. This is used when calculating
     * the "extra entitlement" score for identities.
     */
    public int getWeight() {
        return _weight;
    }

    
    /**
     * Helper method to return a displayable name. This looks at both the name
     * and display name and will attempt localization.
     */
    public String getDisplayableName(Locale locale) {
        
        String name = _name;
        
        if (null != _displayName) {
            name = Internationalizer.getMessage(_displayName, locale);

            // If we didn't find a message, just use the display name.
            if (null == name) {
                name = _displayName;
            }
        }

        return name;
    }

}
