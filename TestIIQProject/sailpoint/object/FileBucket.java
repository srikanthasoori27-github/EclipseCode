package sailpoint.object;

/**
 * Chunk of a file written to the database.
 * For larger files, break them up into
 * FileBuckets so that they can be read into
 * and out of the database efficiently.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class FileBucket extends SailPointObject {

    private PersistedFile parent;
    private int fileIndex;
    private byte[] data;

    public FileBucket() {
    }

    public FileBucket(PersistedFile parent, int index, byte[] data) {
        this.parent = parent;
        this.fileIndex = index;
        this.data = data;
    }

    public PersistedFile getParent() {
        return parent;
    }

    public void setParent(PersistedFile parent) {
        this.parent = parent;
    }

    public int getFileIndex() {
        return fileIndex;
    }

    public void setFileIndex(int fileIndex) {
        this.fileIndex = fileIndex;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
 	public boolean hasAssignedScope() {
 	    return false;
 	}
    
    @Override
    public boolean hasName() {
        return false;
    }
}
