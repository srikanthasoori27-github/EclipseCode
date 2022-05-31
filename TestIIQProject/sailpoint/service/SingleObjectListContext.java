/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.web.UserContext;

/**
 * A BaseListServiceContext that allows using a BaseListService to return a single object instead of
 * a list of objects.  This delegates to SimpleListServiceContext for much of its implementation.
 */
public class SingleObjectListContext extends SimpleListServiceContext implements BaseListServiceContext {
    public SingleObjectListContext(UserContext userContext) {
        super(userContext);
    }

    @Override
    public int getLimit() {
        // Return more than one so the caller can check for non-unique results.
        return 2;
    }
}
