/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A variant of the TaskResult used to hold the results of one 
 * task partition.
 * 
 * Author: Jeff
 *
 * This started out just being a TaskResult but I wanted these
 * in a different table so we don't clutter the main TaskResult
 * table with a tone of little partition results and have to remember
 * to filter every query.
 * 
 */


package sailpoint.object;

import sailpoint.tools.xml.XMLClass;

// extending TaskResult confuses XML serializer...
    //public class PartitionResult extends SailPointObject {

@XMLClass
public class PartitionResult extends TaskResult {

    /**
     * Pointer back to our parent result.
     */
    TaskResult _parent;

    public PartitionResult() {
        super();
    }

    // this won't let me use INLINE for some reason, why not?
    //@XMLProperty(mode=SerializationMode.INLINE)

    public void setTaskResult(TaskResult res) {
        _parent = res;
    }

    public TaskResult getTaskResult() {
        return _parent;
    }

}
