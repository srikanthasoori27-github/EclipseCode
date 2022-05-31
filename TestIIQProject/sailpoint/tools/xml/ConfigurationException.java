/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Exception thrown within the XML tools package.
 * TODO: This looks relatively generic, should this be promoted
 * to the tools package?
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

public class ConfigurationException extends RuntimeException
{
    private static final long serialVersionUID = 1;

    public ConfigurationException(String msg)
    {
        super(msg);
    }
}
