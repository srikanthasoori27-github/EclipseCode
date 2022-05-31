package sailpoint.object;

import sailpoint.tools.GeneralException;

/**
 * Simple representation of a file persisted to the database.
 * The actual file contents are persisted in a set
 * of associated FileBucket objects which hold the raw
 * data.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class PersistedFile extends SailPointObject {

    public static String CONTENT_TYPE_PDF = "application/pdf";
    public static String CONTENT_TYPE_CSV = "application/CSV";

    private String contentType;
    private long contentLength;

    public PersistedFile() {
        super();
    }

    public PersistedFile(String name, String contentType){
        this.setName(name);
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public boolean isCsv(){
        return CONTENT_TYPE_CSV.equals(contentType);
    }

    public boolean isPdf(){
        return CONTENT_TYPE_PDF.equals(contentType);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This helps remove the associated file buckets.
     */
    public void visit(Visitor v) throws GeneralException {
        v.visitPersistedFile(this);
    }
}
