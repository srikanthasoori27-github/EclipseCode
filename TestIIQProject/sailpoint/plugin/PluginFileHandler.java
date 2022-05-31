
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.object.Plugin;
import sailpoint.tools.GeneralException;

import java.io.InputStream;

/**
 * Interface which allows for different implementations of
 * reading, writing and removing plugin files to and from storage.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public interface PluginFileHandler {

    /**
     * Creates an InputStream that allows the caller to read the
     * plugin zip file.
     *
     * @param plugin The plugin.
     * @return The InputStream which can be used to read the file.
     * @throws GeneralException
     */
    InputStream readPluginFile(Plugin plugin) throws GeneralException;

    /**
     * Writes the plugin zip file to storage.
     *
     * @param plugin The plugin.
     * @param fileName The file name.
     * @param fileInputStream The input stream to read for writing.
     * @throws GeneralException
     */
    void writePluginFile(Plugin plugin, String fileName, InputStream fileInputStream) throws GeneralException;

    /**
     * Removes the plugin zip file from storage.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    void removePluginFile(Plugin plugin) throws GeneralException;

}
