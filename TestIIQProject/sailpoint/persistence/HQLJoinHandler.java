/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;


/**
 * An HQLJoinHandler is capable of adding joins to a query.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
interface HQLJoinHandler {

    /**
     * Add a new Join to the given class in the from statement.
     *
     * @param  clazz  The class from which we are joining.
     * @param  join   The Join to add.  Contextual information may be added to
     *                this join as a side effect (ie - the join alias and the
     *                HQLAliasContext).
     * 
     * @return The alias of the Join.
     */
    public String addJoin(Class<?> clazz, HQLFilterVisitor.Join join);
}
