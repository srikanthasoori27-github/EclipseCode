/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class LogField extends AbstractXmlObject {
    private static final long serialVersionUID = -3350185295675314550L;

    /**
     * Strip the parsed value. The default value of this is true.
     */
    private boolean _trimValue;

    /**
     * The name of this field, its important because this is how
     * the parsed value of this group will be referenced inside
     * scripts and rules.
     */
    private String _name;

    /**
     * Flag to indicate a record should be skipped if there are nulls.
     * Default value of this field is false.
     */
    private boolean _dropNulls;

    public LogField() {
        super();
    }

    @XMLProperty
    public boolean getTrim() {
        return _trimValue;
    }
    public boolean shouldTrim() {
        return getTrim();    
    }

    public void setTrim(boolean trim) {
        _trimValue = trim;
    }

    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    @XMLProperty
    public boolean getDropNulls() {
        return dropNulls();
    }
    public boolean dropNulls() {
        return _dropNulls;
    }
    public void setDropNulls(boolean drop) {
        _dropNulls = drop;
    }
}
