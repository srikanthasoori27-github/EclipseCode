/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of an object interested in accumulating error and
 * warning messages.
 *
 * Author: Jeff
 *
 * I added this to solve some control tradeoff problems between
 * Interrogator and Workflower.  Workflower is trying to encapsulate
 * all the logic for notifications and work items, but Interrogator
 * likes to accumulate any error and warning messages in the task result.
 *
 * This interface is implemented by Interrogator which passes itself
 * down into Workflower so that any errors detected during notification
 * processing can be saved.
 *
 */

package sailpoint.api;

import sailpoint.tools.Message;


public interface MessageAccumulator {

    /**
     * @param message Message to add to the accumulator's message collection
     */
    void addMessage(Message message);

}
