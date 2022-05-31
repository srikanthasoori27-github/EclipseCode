/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

/**
 * An interface that provides information about filters being created when
 * listing the contents of a certification.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
interface CertificationFilterContext {

    /**
     * Return whether or not we are querying over CertificationItems.
     * 
     * @return True if we are querying over items, false if we are querying
     *         over entities.
     */
    boolean isDisplayingItems();

    /**
     * Return the prefix to put in front of properties that live on the
     * CertificationEntity (eg - "parent." for CertificationItems).  Return null
     * if there is no prefix required (ie - if {@link #isDisplayingItems()}
     * returns false).
     * 
     * @return The prefix to put in front of properties that live on the
     *         CertificationEntity, or null if a prefix is not required.
     */
    String getEntityPropertyPrefix();

    /**
     * Add the prefix returned by {@link #getEntityPropertyPrefix()} to the
     * given property if one exists.
     * 
     * @param  property  The property to which to prepend the prefix.
     * 
     * @return The given property with the entity property prefix prepended, or
     *         just the property if there is no entity property prefix.
     */
    String addEntityPropertyPrefix(String property);
}
