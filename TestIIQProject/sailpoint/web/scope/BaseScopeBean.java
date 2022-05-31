/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.scope;

import sailpoint.object.Scope;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseObjectBean;


/**
 * Base bean for dealing with scopes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class BaseScopeBean extends BaseObjectBean<Scope> {

    static final String SELECTED_SCOPE_ID = "selectedScopeId";


    /**
     * Constructor.
     */
    public BaseScopeBean() {
        super();
        super.setScope(Scope.class);
        super.setStoredOnSession(false);

        String selectedScopeId =
            super.getRequestOrSessionParameter(SELECTED_SCOPE_ID);
        if (null != selectedScopeId) {
            super.setObjectId(selectedScopeId);
        }
    }


    @SuppressWarnings("unchecked")
    static void saveSelectedScopeId(String selectedScopeId, BaseBean bean) {
        bean.getSessionScope().put(SELECTED_SCOPE_ID, selectedScopeId);
    }
}
