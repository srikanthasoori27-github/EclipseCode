/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;

/**
 * Class that can be used to describe file attachments when sending email.
 * Basic idea here is to name the MimeType, data(filecontents, and the
 * name of the filename or attachment.
 * 
 * Attachments are passed into the notifier via the 
 * EmailOptions.addAttachment(EmailFileAttachment attachment) 
 * method.
 * 
 * Defaults to a application/octet-stream Mime type.
 * 
 */
@XMLClass
public class EmailFileAttachment implements Serializable {

    String _name;
    byte[] _data;
    MimeType _mimeType;

    // nice we to prevent mistyping the mimetype.
    // add as needed
    @XMLClass
    public enum MimeType {
        MIME_CSV("text/csv"),
        MIME_JAR("application/java-archive"),
        MIME_HTML("text/html"),
        MIME_OCTET("application/octet-stream"),
        MIME_PDF("application/pdf"),
        MIME_PLAIN("text/plain"),
        MIME_PS("application/postscript"),
        MIME_RICH("text/richtext"),
        MIME_WORD("application/msword"),
        MIME_ZIP("application/zip");

    	private String _type;

        MimeType(String typeString) {
            _type = typeString;
        }

        public String getType() {
            return _type;
        }
    }

    public EmailFileAttachment() {
        _name = null;
        _data = null;
        _mimeType = MimeType.MIME_OCTET;
    }

    /**
     * Construct a new EmailFileAttachment giving it a name
     * and the data for the attachment. This will default
     * the type to MIME_OCTET.
     */
    public EmailFileAttachment(String name, byte[] data) {
        this();
        _name = name;
        _data = data;
    }

    public EmailFileAttachment(String name, MimeType mimeType, byte[] data) {
        this(name, data);
        _mimeType = mimeType;
    }

    public String getFileName() {
        return _name;
    }

    @XMLProperty
    public void setFileName(String name) {
        _name = name;
    }

    public byte[] getData() { 
        return _data; 
    }

    @XMLProperty
    public void setData(byte[] data) {
        _data = data;
    }

    @XMLProperty
    public void setMimeType(MimeType type) {
        _mimeType = type;
    }

    public MimeType getMimeType() {
        return _mimeType;
    }
  
    /**
     * Get the mime string type. For example, "application/octet-stream"
     */
    public String getMimeTypeString() {
        return _mimeType.getType();
    }
}
