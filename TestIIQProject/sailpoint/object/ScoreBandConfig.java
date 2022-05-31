/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object describing a band range
 *
 * Authors: Jeff and Bernie
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.util.WebUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Defines a band range within a ScoreConfig.
 */
@XMLClass
public class ScoreBandConfig extends AbstractXmlObject
    implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 1064819719521880518L;

    /**
     * A list of light colors that need black text.
     */
    public static List<String> LIGHT_COLORS = Arrays.asList("#ffff00", "#ccff00", "#ffcc00");

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * HTML-style string representing the color.  Example:  "#000000"
     */
    String _color;

    /**
     * HTML Hex color value for text. Sometimes we need to specify the text color of any text that
     * will be displayed over the _color property.  Example "#000000"
     */
    String _textColor;

    /**
     * Configurable description of this band.  Examples: "High Risk," "Low Risk"
     */
    String _label;

    /**
     * This band's upper bound 
     */
    int _upperBound;

    /**
     * This band's lower bound
     */
    int _lowerBound;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public ScoreBandConfig() {
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * HTML-style string representing the color.  Example:  "#000000"
     */
    @XMLProperty
    public String getColor() {
        return _color;
    }

    public void setColor(String c) {
        _color = nullSafeColor(c, "#FFFFFF");
    }

    /**
     * HTML style string representing the text color shown on-top of the color. Example #000000
     * @return the html style string representing the text color shown on-top of the color
     */
    @XMLProperty
    public String getTextColor() {
        return _textColor;
    }

    public void setTextColor(String c) {
        _textColor = nullSafeColor(c, "#FFFFFF");
    }

    private String nullSafeColor(String color, String fallBackColor) {
        if(WebUtil.isSafeValue(color)) {
            return color;
        } else {
            return fallBackColor;
        }
    }

    /**
     * Configurable description of this band.  Examples: "High Risk," "Low Risk"
     */
    @XMLProperty
    public String getLabel() {
        return _label;
    }

    public void setLabel(String d) {
        _label = d;
    }

    /**
     * This band's upper bound 
     */
    @XMLProperty
    public int getUpperBound() {
        return _upperBound;
    }

    public void setUpperBound(int i) {
        _upperBound = i;
    }

    /**
     * This band's lower bound
     */
    @XMLProperty
    public int getLowerBound() {
        return _lowerBound;
    }

    public void setLowerBound(int i) {
        _lowerBound = i;
    }
    
    public String toString() {
        StringBuilder str = new StringBuilder("[" + ScoreBandConfig.class.getName() + ": [color = '");
        str.append(_color);
        str.append("'], [label = '");
        str.append(_label);
        str.append("'], [lowerBound = ");
        str.append(_lowerBound);
        str.append("], [upperBound = ");
        str.append(_upperBound);
        str.append("]]");
        
        return str.toString();
    }
};

