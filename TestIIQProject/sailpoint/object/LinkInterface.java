/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.Date;


/**
 * An read-only interface for Links (for example - application accounts). This provides
 * a common interface to Links and LinkSnapshots.
 */
public interface LinkInterface {

    /**
     * Return a unique identifier for this Link.
     */
    public String getId();

    /**
     * Return the friendly name for this account if it exists.
     */
    public String getDisplayName();

    /**
     * Return the native identity of the account.
     */
    public String getNativeIdentity();

    /**
     * Return the instance identifier for template applications.
     */
    public String getInstance();

    /**
     * Return the name of the application referenced by this link.
     */
    public String getApplicationName();

    /**
     * Return the name to display in the UI.
     */
    public String getDisplayableName();

    /**
     * Return the date this Link was last refreshed.
     */
    public Date getLastRefresh();

    /**
     * Return the attributes on this link.
     */
    public Attributes<String, Object> getAttributes();
}
