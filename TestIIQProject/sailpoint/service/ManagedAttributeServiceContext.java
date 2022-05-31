package sailpoint.service;

import sailpoint.tools.GeneralException;

/**
 * Interface for passing values to the ManagedAttributeService
 */
public interface ManagedAttributeServiceContext extends BaseListServiceContext {

    String getRequesteeId() throws GeneralException;

}
