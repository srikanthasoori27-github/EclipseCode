
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.object.FileBucket;
import sailpoint.object.Filter;
import sailpoint.object.PersistedFile;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.persistence.PersistedFileInputStream;
import sailpoint.persistence.PersistedFileOutputStream;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of PluginFileHandler that writes and reads plugin
 * files to and from the database.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class DatabaseFileHandler implements PluginFileHandler {

    /**
     * Constant used to set the content type of the persisted file.
     */
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    /**
     * The SailPoint context.
     */
    private SailPointContext _context;

    /**
     * Constructor.
     *
     * @param context The context.
     */
    public DatabaseFileHandler(SailPointContext context) {
        _context = context;
    }

    /**
     * Creates an input stream which can be used to read the file stored
     * in the database. The caller is responsible for closing this stream.
     *
     * @param plugin The plugin.
     * @return The InputStream which can be used to read the file.
     * @throws GeneralException
     */
    @Override
    public InputStream readPluginFile(Plugin plugin) throws GeneralException {
        return new PersistedFileInputStream(_context, plugin.getFile());
    }

    /**
     * Reads the input stream and writes the plugin zip file to the database.
     *
     * @param plugin The plugin.
     * @param fileName The file name.
     * @param fileInputStream The input stream to read for writing.
     * @throws GeneralException
     */
    @Override
    public void writePluginFile(Plugin plugin, String fileName, InputStream fileInputStream) throws GeneralException {
        PersistedFileOutputStream fileOutputStream = null;

        try {
            PersistedFile persistedFile = new PersistedFile();
            persistedFile.setName(fileName);
            persistedFile.setContentType(ZIP_CONTENT_TYPE);

            fileOutputStream = new PersistedFileOutputStream(_context, persistedFile);
            long contentLength = IOUtil.copy(fileInputStream, fileOutputStream);
            fileOutputStream.flush();

            persistedFile.setContentLength(contentLength);

            _context.saveObject(persistedFile);

            plugin.setFile(persistedFile);
        } catch (IOException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(fileOutputStream);
        }
    }

    /**
     * Removes the plugin zip file from the database.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    @Override
    public void removePluginFile(Plugin plugin) throws GeneralException {
        // if an upgrade has import XML object files then the plugin object
        // could become detached so reattach it just to be safe
        _context.attach(plugin);

        PersistedFile pluginFile = plugin.getFile();
        if (pluginFile != null) {
            // remove file buckets
            _context.removeObjects(FileBucket.class, new QueryOptions(
                Filter.eq("parent", pluginFile)
            ));

            // remove persisted file from plugin
            plugin.setFile(null);

            // remove persisted file
            _context.removeObject(pluginFile);
            _context.commitTransaction();

            _context.decache(pluginFile);
        }
    }

}
