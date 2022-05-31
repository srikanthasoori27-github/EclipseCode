package sailpoint.service;

import sailpoint.object.Filter;

/**
 * Contains common options for identity list requests to be passed from a resource to a service layer object.
 */
public interface IdentityListServiceContext extends BaseListServiceContext {

    boolean isCurrentUserFirst();

    boolean isRemoveCurrentUser();
    
    Filter getNameFilter();

    Filter getIdFilter();

    Filter getSessionFilter();

    String getQuickLink();
    
}
