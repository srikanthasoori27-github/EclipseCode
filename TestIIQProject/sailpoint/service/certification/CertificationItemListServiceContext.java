package sailpoint.service.certification;

import sailpoint.object.Certification;
import sailpoint.service.BaseListServiceContext;

/**
 * Certification item list request filters to be passed to the service.
 */
public interface CertificationItemListServiceContext extends BaseListServiceContext {

    /**
     * Returns true if the the list filter context is already joined to the identity in the filter from
     * getFilters()
     */
    boolean isJoinedToIdentity();

    /**
     * Returns the certification.
     */
    Certification getCertification();

}
