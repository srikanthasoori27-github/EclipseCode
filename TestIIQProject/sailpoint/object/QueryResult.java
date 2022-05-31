/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

public interface QueryResult<T extends SailPointObject>
{
    /**
     * Returns the array of projection results or null if the QueryOptions
     * did not have any projections
     */
    public Object [] getProjectionResult();


    /**
     * Returns the object or null if the query had a projection
     */
    public T getObject();
}
