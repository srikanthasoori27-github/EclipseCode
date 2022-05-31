/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Compression tools.
 *
 * Author: Jeff
 *
 * I wrote these before realizing that the Base64 utility
 * we found already has options for GZIPping.  This was
 * surprising because we would B64 a compressed byte[] but
 * when we decoded it it would mysteriously get uncompressed.
 * I'm not sure I like that.  It is convenient for our case
 * but it means that you can't decode a compressed byte[]
 * and leave it that way.  Could add an options argument
 * to decode to prevent that.
 *
 * I'm leaving the byte[] compression methods here for now
 * but the ones that do B64 encoding won't use them.
 *
 * Another thing I don't like about the way Base64 does it
 * is that it uses an ObjectOutputStream and compresses
 * the serialized representation of any Object rather than
 * just compressing the string characters.  This means if you
 * just to simple decoding/decompress you will get some
 * Java Serializable garbage at the front, you must
 * use an ObjectInputStream to get the original string back.
 * 
 */

package sailpoint.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class Compressor {

    static public byte[] compressToBytes(String str) throws GeneralException {

        byte[] compressed = null;

        if (str != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                GZIPOutputStream gz = new GZIPOutputStream(out);
                gz.write(str.getBytes());
                gz.flush();
                gz.close();
                out.close();
                compressed = out.toByteArray();
            }
            catch (IOException ioe) {
                throw new GeneralException(ioe);
            }
        }

        return compressed;
    }

    public static final String decompress(byte[] compressed) throws GeneralException {

        String decompressed = null;
        
        if (compressed != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayInputStream in = new ByteArrayInputStream(compressed);
                GZIPInputStream gz = new GZIPInputStream(in);
                byte[] buffer = new byte[1024];
                int offset = -1;
                while((offset = gz.read(buffer)) != -1) {
                    out.write(buffer, 0, offset);
                }
                decompressed = out.toString();
                out.close();
                gz.close();
            }
            catch (IOException ioe) {
                throw new GeneralException(ioe);
            }
        }

        return decompressed;
    }

    /**
     * Compress to a string that could be included within an XML element.
     */
    static public String compress(String str) throws GeneralException {

        String compressed = null;
        if (str != null) {
            //byte[] bytes = compressToBytes(str);
            //compressed = Base64.encodeBytes(bytes);

            // this does it all
            compressed = Base64.encodeObject(str, Base64.GZIP);
        }
        return compressed;
    }

    static public String decompress(String str) throws GeneralException {

        String decompressed = null;
        if (str != null) {
            //byte[] bytes = Base64.decode(str);
            //decompressed = decompress(bytes);

            // this will also decompress, even without asking!
            decompressed = (String)Base64.decodeToObject(str);
        }

        return decompressed;
    }


}
