/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.policyviolation;

import sailpoint.service.BaseListServiceContext;

/**
 * PolicyViolation request filters to be passed to the service.
 */
public interface PolicyViolationListServiceContext extends BaseListServiceContext {

    /**
     * @return showAll true if we need to show all policy violations
     */
    boolean isShowAll();
}