package sailpoint.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.FileBucket;
import sailpoint.object.Filter;
import sailpoint.object.PersistedFile;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * File reader which reads a file from a series of
 * FileBucket objects stored in the database.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class PersistedFileInputStream extends InputStream {

    private static Log log = LogFactory.getLog(PersistedFileInputStream.class);

    private SailPointContext context;
    private PersistedFile file;
    private FileBucket currentBucket;
    private int startingBucket;

    private InputStream currentStream;

    private Iterator<FileBucket> buckets;

    public PersistedFileInputStream(SailPointContext context, PersistedFile file) {
        this.context = context;
        this.file = file;
    }

    public PersistedFileInputStream(SailPointContext context, PersistedFile file, int startBucket) {
        this(context, file);
        this.startingBucket = startBucket;
    }

    private Iterator<FileBucket> getIterator(){

        QueryOptions ops = new QueryOptions(Filter.eq("parent", file));
        ops.addOrdering("fileIndex", true);

        ops.add(Filter.ge("fileIndex", startingBucket));

        return new IncrementalObjectIterator<FileBucket>(context, FileBucket.class, ops);
    }

    @Override
    public int read() throws IOException {

        if (buckets == null)
            buckets = getIterator();

        if (currentStream != null){
            int v = currentStream.read();
            if (v != -1)
                return v;
        }

        if (buckets.hasNext()){
            try {
                nextBucket();
            } catch (GeneralException e) {
                IOException io = new IOException("Error reading PersistedFile buckets");
                io.initCause(e);
                throw io;
            }
            return read();
        }

        return -1;
    }

    private boolean nextBucket() throws GeneralException {

        if (currentBucket != null) {
            context.decache(currentBucket);
        }

        if (buckets != null && buckets.hasNext()){
            currentBucket = buckets.next();
            if (currentBucket != null && currentBucket.getData() != null)
                currentStream = new ByteArrayInputStream(currentBucket.getData());
            return true;
        }

        return false;
    }
}
