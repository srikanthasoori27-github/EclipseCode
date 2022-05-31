/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

/**
 * Interface used to create and retrieve aliases for HQL queries.
 * 
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
interface HQLAliasContext {

    /**
     * Set whether we are inside an OR condition.
     * 
     * @param  insideOr  Whether we are currently inside an OR condition.
     * 
     * @return Whether we were previously inside an OR condition.
     */
    public boolean setInsideOr(boolean insideOr);

    /**
     * Retrieve the default alias for this context - that is for an empty path.
     * 
     * @return The default alias for this context - that is for an empty path.
     */
    public String getDefaultAlias();

    /**
     * Retrieve the alias for the given Class.
     * 
     * @param  clazz  The Class for which to retrieve the alias.
     * 
     * @return The alias for the given Class.
     */
    public String getAlias(Class clazz);

    /**
     * Retrieve the alias for the given property path.
     * 
     * @param  propertyPath  The property path for which to retrieve the alias.
     * 
     * @return The alias for the given property path.
     */
    public String getAlias(String propertyPath);

    /**
     * Set the alias for the given Class.
     * 
     * @param  clazz  The Class to set the alias.
     * @param  alias  The alias for the given class.
     */
    public void setClassAlias(Class clazz, String alias);


    /**
     * Substitute aliases into the given property path.  Don't force a unique
     * join.
     *
     * @see #substituteAlias(String, boolean, boolean)
     */
    public String substituteAlias(String propertyPath);
    
    /**
     * Substitute aliases into the given property path.  This will create joins
     * if they are needed.
     *
     * @param  prop  The property into which to substitute aliases.  For example,
     *               Identity.name would translate into something like
     *               identityAlias.name.
     *
     * @param  forceUniqueJoin
     *               Whether a unique join should be created for this property
     *               reference.  When false, we'll reuse a join in the joinMap.
     *
     * @param  forceOuterJoin
     *               Whether to force an outer join or not if we're creating a
     *               new join.  If this is true or we are visiting an OR, outer
     *               joins are created instead of inner joins.
     */
    public String substituteAlias(String propertyPath, boolean forceUniqueJoin, boolean forceOuterJoin);
}
