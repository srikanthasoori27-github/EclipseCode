/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * Represents a category of permission targets within an application.
 * Used in the configuration of activity policy.
 */
public class Category extends SailPointObject {
    private static final long serialVersionUID = 5018002114509770687L;
    
    private String _name;
    private List<String> _targets;

    @XMLProperty
    public String getName() {
        return _name;
    }
    
    public void setName(final String name) {
        _name = name;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getTargets() {
        return _targets;
    }

    public void setTargets(List<String> _targets) {
        this._targets = _targets;
    }
    
    public String getTargetsAsCSV() {
        final String result;
        
        if (_targets == null) {
            result = "";
        } else {
            StringBuffer targetsAsCSV = new StringBuffer(_targets.toString());
            targetsAsCSV.deleteCharAt(0);
            targetsAsCSV.deleteCharAt(targetsAsCSV.length() - 1);
            result = targetsAsCSV.toString();
        }
        
        return result;
    }
}
