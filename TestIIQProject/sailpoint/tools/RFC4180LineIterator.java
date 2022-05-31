/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedReader;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *  
 *  Takes in a BufferedReader and returns each line following 
 *  the RFC4180. The biggest difference between this readLine
 *  and the readLine on the BufferedReader object is that 
 *  this iterator allows CRLF to be embeded in quotes.
 *
 *  More specific information : 
 *  @see http://www.rfc-editor.org/rfc/rfc4180.txt
 *
 *  Here are the rules outlinesin RFC4180.
 *
 *   1.  Each record is located on a separate line, delimited by a line
 *       break (CRLF).  For example:
 *       aaa,bbb,ccc CRLF
 *       zzz,yyy,xxx CRLF
 *
 *   2.  The last record in the file may or may not have an ending line
 *       break.  For example:
 *
 *       aaa,bbb,ccc CRLF
 *       zzz,yyy,xxx
 *
 *   3.  There maybe an optional header line appearing as the first line
 *       of the file with the same format as normal record lines.  This
 *       header will contain names corresponding to the fields in the file
 *       and should contain the same number of fields as the records in
 *       the rest of the file 
 *       For example:
 *
 *       field_name,field_name,field_name CRLF
 *       aaa,bbb,ccc CRLF
 *       zzz,yyy,xxx CRLF
 *
 *   4.  Within the header and each record, there may be one or more
 *       fields, separated by commas.  Each line should contain the same
 *       number of fields throughout the file.  Spaces are considered part
 *       of a field and should not be ignored.  The last field in the
 *       record must not be followed by a comma.  For example:
 *
 *       aaa,bbb,ccc
 *
 *   5.  Each field may or may not be enclosed in double quotes (however
 *       some programs, such as Microsoft Excel, do not use double quotes
 *       at all).  If fields are not enclosed with double quotes, then
 *       double quotes may not appear inside the fields.  For example:
 *       "aaa","bbb","ccc" CRLF
 *       zzz,yyy,xxx
 *
 *   6.  Fields containing line breaks (CRLF), double quotes, and commas
 *       should be enclosed in double-quotes.  For example:
 *       "aaa","b CRLF
 *       bb","ccc" CRLF
 *       zzz,yyy,xxx
 *
 *   7.  If double-quotes are used to enclose fields, then a double-quote
 *       appearing inside a field must be escaped by preceding it with
 *       another double quote.  For example:
 *       "aaa","b""bb","ccc"
 */
public class RFC4180LineIterator {

    private BufferedReader _reader;
    private static Log _log = LogFactory.getLog(RFC4180LineIterator.class);

    /**
     * Maximum number of characters too allow before throwing up.
     * To prevent overflow the the heap if there are data problems. 
     */
    private int MAX_CHARS_PER_LINE = 1000000;

    /**
     * A flag to indicate if the parsing should ignore quotes.
     * False by default, but if true quotes are handled just like
     * any other character.
     */
    private boolean _ignoreQuotes;
    
    
    /**
     * A flag to indicate whether or not the reader has been examined
     * for a UTF-8 Byte Order Marker.
     */
    private boolean _bomChecked;
    

    public RFC4180LineIterator(BufferedReader reader) {
        _reader = reader;
        _ignoreQuotes = false;
        _bomChecked = false;
    }

    /**
     * Looks for the optional Byte Order Marker (BOM) in UTF-8 encoded
     * files and skip it if it exists; otherwise, reset the reader.
     * 
     * This only needs to be done once per file
     * 
     * @throws IOException
     */
    private void checkBOM() throws IOException {
        
        // flip the bit so we don't try to do this again
        _bomChecked = true;
        /**
         * Variable declared to check the End of File.
         */
        int bomInt = 0;
        _reader.mark(1);
        bomInt = _reader.read();
        /**
         * End of file check is added for CONETN-99-Blank Delimited file with 
         * Dos/Windows line ending format throws an invalid mark error. 
         */
        if ( bomInt != -1 ) {
            char bomChar = (char) bomInt;
                if (bomChar == 0xFEFF) 
                    _log.debug("BOM found - this file is UTF-8 encoded");
                else
                    _reader.reset();    
        }
    }

    public void setIgnoreQuotes(boolean ignore) {
        _ignoreQuotes = ignore;
    }

    public boolean ignoreQuotes() {
        return _ignoreQuotes;
    }
   
    /*
     * Read ahead to the next character and return true if its
     * the character we are looking for...
     */
    private boolean checkNextChar(char lookingFor) throws IOException {
        boolean found = false;

        int next = 0;
        next = _reader.read();
        if ( next != -1 ) {
            char nextChar =(char)next;
            if  ( nextChar == lookingFor) {
                found = true;
            } 
        }
        return found;
    }

    /**
     * Read the next line from the stream, paying attention to quotes. 
     * If we are in quotes and hit CRLF then add em to the buffer,
     * otherwise it indicates the end of line.
     */
    public String readLine() throws IOException {
        StringBuffer nextLine = new StringBuffer();
        boolean inQuote = false;
        int charsProcessed = 0;

        int n = 0;

        if ( _reader == null ) return null;
        
        if (!_bomChecked) 
            checkBOM();
        
        while ( ( n = _reader.read() ) != -1 ) {
            char ch = (char)n;
            
            if ( charsProcessed++ >= MAX_CHARS_PER_LINE )  {
                // something so we don't run out of memory while we are process
                throw new IOException("Max number of characters per line have"
                                     + " been read, here are the first 1000"
                                     + " character of the line ["
                                     +nextLine.toString().substring(0,1000)
                                     +"...]");
            }

            // todo we should be only checking for /r here. This
            // code effectively treats /r/n and /n as a line end.
            // See bug#12504
            if ( (ch == '\n') || (ch == '\r') ) {
                // 
                // In this case the \n or \r or \r\n get 
                // consumed 
                // 
                // Spec says if we are in a quote we need to 
                // allow the \n \r. Otherwise, its marks the
                // end of aline
                if ( !inQuote )  {
                    // check for trailing \n for the \r\n case 
                    // to see if we should consume it
                    _reader.mark(1);
                    boolean isLineEndingNext  = checkNextChar('\n');
                    // consume it so the next iteration doesn't have to deal
                    if ( !isLineEndingNext ) {
                        // reset it back before we read
                        _reader.reset();
                    }
                    break;
                } else {
                    // line endings nested in a quote get appended
                    nextLine.append(ch);
                }
            } else {
                // Append the character to the line
                nextLine.append(ch);
                if ( !_ignoreQuotes ) {
                    if  ( ch == RFC4180LineParser.QUOTE_CHAR ) {
                        if ( inQuote ) {
                            _reader.mark(1);
                            boolean isQuoteNext = 
                                checkNextChar(RFC4180LineParser.QUOTE_CHAR);
                            if ( isQuoteNext ) {
                                nextLine.append( RFC4180LineParser.QUOTE_CHAR);
                            } else {
                                _reader.reset(); 
                                // means just an ending quote
                                inQuote = false;
                            }
                        } else {
                            inQuote = true;
                        }
                    } 
                }
            }
        }

        if ( (n == -1 ) && ( nextLine.length() == 0 ) ) {
            // end of stream, close here for convience 
            // we don't allow rewinding the buffer so
            // close it...
            close();
            return null;
        } else {
            return nextLine.toString();
        }
    }

    /**
     * Close the underlying reader. This should be called
     * when you are done with the iterator.
     */
    public void close() {
        try {
            if ( _reader != null ) {
                _reader.close();    
                _reader = null;
            }
        } catch(IOException io ) {
            _log.warn("Problem closing stream from RFC4180LineIterator: " + 
                      io.toString());
        }
    }
}
