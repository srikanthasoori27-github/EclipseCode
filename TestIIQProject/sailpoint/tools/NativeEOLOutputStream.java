/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 
 * 
 */
public class NativeEOLOutputStream extends FilterOutputStream {
    private int prevByte = -1;

    private static byte[] eolChars;
    static {
        String eolString = System.getProperty("line.separator");
        if (eolString == null)
            eolString = "\n";
        eolChars = eolString.getBytes();
    }

    /**
     * 
     * @param out
     */
    public NativeEOLOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * 
     * @return
     */
    public static byte[] getEolChars() {
        return eolChars;
    }

    /**
     * 
     * @param b
     */
    public void write(int b) throws IOException {
        if (b == '\r') {
            out.write(eolChars);
        } else if (b == '\n') {
            if (prevByte != '\r')
                out.write(eolChars);
        } else {
            out.write(b);
        }

        prevByte = b;
    } // lcass write(int)
} // class NativeEOLOutputStream
