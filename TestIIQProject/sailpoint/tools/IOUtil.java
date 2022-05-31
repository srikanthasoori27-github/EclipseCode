
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Collection of utility methods that work with streams of data.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class IOUtil {

    /**
     * Default buffer size to use when copying data from one stream to another.
     * The default size is 8KB.
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

    /**
     * Denotes the end of file or stream.
     */
    private static final int EOF = -1;

    /**
     * Private constructor. No instance of this class should be created.
     */
    private IOUtil() {}

    /**
     * Reads the data from the input stream and writes it to the output stream. Uses
     * the default buffer size of 8KB.
     *
     * @param inputStream The input stream.
     * @param outputStream The output stream.
     * @return The total number of bytes read.
     * @throws IOException
     */
    public static long copy(final InputStream inputStream, final OutputStream outputStream)
        throws IOException {

        return copy(inputStream, outputStream, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Reads the data from the input stream and writes it to the output stream.
     *
     * @param inputStream The input stream.
     * @param outputStream The output stream.
     * @param bufferSize The size of the buffer to use.
     * @return The total number of bytes read.
     * @throws IOException
     */
    public static long copy(final InputStream inputStream, final OutputStream outputStream, final int bufferSize)
        throws IOException {

        int bytesRead = 0;
        int totalBytes = 0;

        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = inputStream.read(buffer)) != EOF) {
            outputStream.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }

        return totalBytes;
    }

    /**
     * Closes the Closeable swallowing any IOException that might be thrown.
     *
     * @param closeable The Closeable instance to close.
     */
    public static void closeQuietly(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            // ignore, all error handling should have happened before now
        }
    }

    /**
     * Closes the AutoCloseable swallowing any Exception that might be thrown.
     *
     * @param closeable The AutoCloseable instance to close.
     */
    public static void closeQuietly(final AutoCloseable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception e) {
            // ignore, all error handling should have happened before now
        }
    }

}
