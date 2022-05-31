
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * Exception which can be thrown during plugin installation.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginInstallationException extends GeneralException {

    /**
     * Constructor.
     *
     * @param msg The message key.
     */
    public PluginInstallationException(String msg) {
        super(msg);
    }

    /**
     * Constructor.
     *
     * @param msg The message.
     */
    public PluginInstallationException(Message msg) {
        super(msg);
    }

    /**
     * Constructor.
     *
     * @param msg The message.
     * @param cause The cause.
     */
    public PluginInstallationException(Message msg, Throwable cause) {
        super(msg, cause);
    }

}
