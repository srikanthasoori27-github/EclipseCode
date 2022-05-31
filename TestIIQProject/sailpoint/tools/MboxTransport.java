/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;


/**
 * This class is kind of like a javax.mail.Transport, but it doesn't need all
 * of the fancy javax.mail.Session or URL handling, so the interface is
 * simpler.
 *
 */
public class MboxTransport {

    private String _fileName;

    /**
     *
     * @param fileName
     */
    public MboxTransport(String fileName) {
        _fileName = fileName;
    }

    /**
     *
     * @param msg
     * @param addresses
     * @throws MessagingException
     */
    public synchronized void sendMessage(Message msg, Address[] addresses)
            throws MessagingException {
        if ( _fileName == null ) return;

        String fromString = "IdentityIQ@localhost";

        Address[] fromAddrs = msg.getFrom();
        if ( fromAddrs != null && fromAddrs.length > 0 && fromAddrs[0] != null )
            fromString = fromAddrs[0].toString();

        String dateFormat = "EEE MMM dd HH:mm:ss yyyy";
        fromString = "From " + fromString + " " +
                             Util.dateToString(new Date(), dateFormat) + "\n";

        try {
            OutputStream out = new NativeEOLOutputStream(
                                   new BufferedOutputStream(
                                       new FileOutputStream(_fileName, true)));

            out.write(fromString.getBytes());
            msg.writeTo(out);
            out.write('\n');
            out.close();
        } catch ( IOException ex ) {}
    }  // sendMessage(Message, Address[])
}  // class MboxTransport
