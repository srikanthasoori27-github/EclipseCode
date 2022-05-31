/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * JSF bean for the role suggest component.
 * Author: Jeff
 *
 * Take your time, there's a lot to digest here.
 *
 */
package sailpoint.web.identity;

import java.util.List;
import java.util.Map;

import sailpoint.object.Bundle;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseObjectSuggestBean;

public class RolesSuggestBean extends BaseObjectSuggestBean<Bundle> {

    @Override
    public Map<String,Object> convertRow(Object[] row, List cols)
        throws GeneralException {

        Map<String,Object> converted = super.convertRow(row, cols);

        // think about showing a role hierarchy fragment
        converted.put("other", "");
        
        return converted;
    }

}
