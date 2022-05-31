/* (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.certification;

import java.util.List;

import sailpoint.object.AbstractCertificationItem;
import sailpoint.service.BaseListServiceContext;

/**
 * A list service context used for listing CertificationEntity objects.
 */
public interface CertificationEntityListServiceContext extends BaseListServiceContext {

    /**
     * @return The ID of the certification for the entities - this must return non-null.
     */
    public String getCertificationId();

    /**
     * @return An optional list of statuses that will cause only entities with these statuses to be returned.
     */
    public List<AbstractCertificationItem.Status> getStatuses();

    /**
     * @return An optional list of statuses that will cause only entities that don't have these statuses to be returned.
     */
    public List<AbstractCertificationItem.Status> getExcludedStatuses(); 

    /**
     * @return Whether the completion statistics for the items within the entities should be returned.
     */
    public boolean isIncludeStatistics();

    /**
     * Returns true if the the list filter context is already joined to the identity in the filter from
     * getFilters()
     */
    boolean isJoinedToIdentity();

}
