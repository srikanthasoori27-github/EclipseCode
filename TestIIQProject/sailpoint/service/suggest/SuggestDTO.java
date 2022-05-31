/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.suggest;

import sailpoint.object.SailPointObject;
import sailpoint.service.BaseDTO;

/**
 * Created by ryan.pickens on 9/7/16.
 */
public class SuggestDTO extends BaseDTO {


    String _displayName;
    String _name;

    public SuggestDTO() {
        super(null);
    }

    /**
     * Create a SuggestDTO from a SailPointObject.
     * Note: this will set displayName to name. If the obj supports displayName, set this manually
     * @param obj
     */
    public SuggestDTO(SailPointObject obj) {
        super(obj.getId());
        _name = obj.getName();
        //Set the default displayName to name. Not all objects support displayName
        _displayName = obj.getName();
    }

    public String getDisplayName() { return _displayName; }
    public void setDisplayName(String s) { _displayName = s; }

    public String getName() { return _name; }
    public void setName(String s) { _name = s;}
}
