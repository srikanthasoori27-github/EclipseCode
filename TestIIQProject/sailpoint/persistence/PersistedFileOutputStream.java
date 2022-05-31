package sailpoint.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.FileBucket;
import sailpoint.object.PersistedFile;
import sailpoint.tools.GeneralException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * File writer which writes a given file to the database
 * as a series of FileBucket objects.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class PersistedFileOutputStream extends OutputStream {

    private static Log log = LogFactory.getLog(PersistedFileOutputStream.class);

    private SailPointContext context;
    private PersistedFile file;
    private int index;
    private int bucketSize;
    private ByteArrayOutputStream stream = new ByteArrayOutputStream();

    private static int DEFAULT_BUCKET_SIZE = 1000000;

    public PersistedFileOutputStream(){
        bucketSize = DEFAULT_BUCKET_SIZE;
    }

    public PersistedFileOutputStream(SailPointContext context, PersistedFile file) {
        this();
        this.context = context;
        this.file = file;
    }

    public PersistedFileOutputStream(SailPointContext context, PersistedFile file, int bucketSize) {
        this();
        this.context = context;
        this.file = file;

        if (bucketSize < 1)
            throw new IllegalArgumentException("Invalid bucket size. Bucket size must have a position non-zero value.");
        this.bucketSize = bucketSize;
    }

    @Override
    public void write(int i) throws IOException {

        stream.write(i);

        if (stream.size() > bucketSize){
            flush();
        }

    }

    @Override
    public void flush() throws IOException {
        try {
            flushStream(stream.toByteArray());
        } catch (GeneralException e) {
            log.error(e);
            IOException io = new IOException("Error reading PersistedFile buckets");
            io.initCause(e);
            throw io;
        }
        stream.reset();
    }

    private void flushStream(byte[] bytes) throws GeneralException {

        if (bytes == null || bytes.length == 0)
            return;

        context.attach(file);

        FileBucket bucket = new FileBucket(file, index++, bytes);
        context.saveObject(bucket);
        context.commitTransaction();

        context.decache();
    }
}