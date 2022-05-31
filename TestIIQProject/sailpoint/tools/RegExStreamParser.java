/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *  A simple parsing class that can take in a stream and break it into 
 *  tokens.  
 *
 * TODO:
 *   o support "comment character" in multi-lined mode? necessary? possible?
 *
 *  Author: Dan Smith
 */
public class RegExStreamParser {

    private static Log _log = LogFactory.getLog(RegExStreamParser.class);

    private final int DEFAULT_BLOCK_SIZE = 1024 * 50;

    /**   
     * String representation of the regular expression.
     */
    private String _regEx;

    /**
     * Incomming file stream;
     */
    private Reader _reader;

    /** 
     * Chacter that if at the begining of a line will be skipped.
     */
    private String _commentCharacter;

    /**
     * The number of lines that should be skipped from the top of the
     * the file.
     */
    private int _linesToSkip;

    /** Should the MULTI_LINED mode be enabled */
    private boolean _multiLined;

    /** Cache the pattern during iteration to avoid the cost. */
    private Pattern _pattern;

    /** Size of the chunks to fetch from a file at a time. */
    private int _blockSize;

    private RegExStreamParser() {
        _commentCharacter = null;
        _linesToSkip = 0;
        _multiLined = false;
        _blockSize = DEFAULT_BLOCK_SIZE;
    }

    private RegExStreamParser(String regEx) {
        this();
        _regEx = regEx;
    }

    /**
     * @throws GeneralException 
     * @deprecated Use {@link @RegExStreamParser(Reader, String)} instead.
     */
    @Deprecated
    public RegExStreamParser(InputStream stream, String regEx) throws GeneralException {
        this(regEx);
            try {
                _reader = new BufferedReader(new InputStreamReader(stream, "UTF8"));
            } catch (UnsupportedEncodingException e) {
                throw new GeneralException(e);
            }
    }

    public RegExStreamParser(Reader reader, String regEx) {
        this(regEx);
        _reader = reader;
    }


    private Pattern getPattern() {
        if ( _pattern == null ) {
            int flags = 0;
            if ( _multiLined ) {
                flags |= Pattern.MULTILINE;
                _log.debug("MultiLineMode enabled");
            }
            _pattern = Pattern.compile(_regEx, flags);
            _log.debug("Using Pattern:" + _pattern);
        }
        return _pattern;
    }

    public void setCommentCharacter(String s) {
        _commentCharacter = s;
    }

    public String getCommentCharacter() {
        return _commentCharacter;
    }

    public int getNumLinesToSkip() {
        return _linesToSkip;
    }

    public void setNumLinesToSkip(int num) {
        _linesToSkip = num;
    }

    public void setMultiLinedMode(boolean enabled) {
       _multiLined = enabled;
    }

    public boolean isMultiLinedMode() {
       return _multiLined;
    }

    /**
     * When in MULTI_LINE mode, we have to get blocks of the
     * file to prevent memory usage problems. This method
     * sets this block size to 100k buffer size.
     */
    public void setBlockSize( int blockSize ) {
        _blockSize = blockSize;
    }

    /**
     * Retrieve the block size that should be used when processing
     * large files in multi-line mode.
     */
    public int getBlockSize() {
        return _blockSize;
    }

    /**
     * Returns an iterator over a List<String> each list representing
     * the tokens for a record.  Must call close on returned iterator
     * in order to close the underlying file stream.
     */
    @SuppressWarnings("unchecked")
    public CloseableIterator<List<String>> getTokenIterator() 
        throws GeneralException {
        return new LineIterator(_reader);
    }

    /**
     * If there are any lines to skip iterate the reader past
     * the skipping point.
     */
    private void skipLines(BufferedReader reader) throws IOException {
        for ( int i=0; i<_linesToSkip; i++ ) {
            String line = reader.readLine();
            if ( line == null ) {
                break;
            }
        }
    }

    /** 
     * An iterator that parses through the vile using a Buffered 
     * reader.  It reads the file as hasNext() is called to prevent
     * memory problems with large files.
     */
    private class LineIterator implements CloseableIterator {
 
        private BufferedReader _reader;
        private FileBlockReader _blockReader;

        private List<String> _nextElement;

        public LineIterator(Reader reader) throws GeneralException {
            try {
                if ( reader != null ){
                    if ( _reader instanceof BufferedReader ) {
                        _reader = (BufferedReader)reader;
                    } else {
                        _reader = new BufferedReader(reader);
                    }
                }
                // skip any lines that have been configured
                skipLines(_reader);
                _blockReader = new FileBlockReader(_reader);
            } catch (Exception e ) {
                throw new GeneralException(e);
            }
        }

        public boolean hasNext() {

            boolean hasNext = false;
            try {
                _nextElement = getNextElement();
                if ( _nextElement != null ) {
                    hasNext = true;
                }
            } catch (Exception e ) {
                throw new RuntimeException(e);
            }
            return hasNext;
        }
   
        protected List<String> getNextElement() throws Exception {

            List<String> nextRecord = null;
            if ( !_multiLined ) {
                String line = null;
                while ( ( line = _reader.readLine() ) != null ) {
                    if ( _commentCharacter != null ) {
                        if ( line.startsWith(_commentCharacter) ) {
                            continue; 
                        }
                    } 
                    List<String> tokens = tokenizeLine(line);
                    if ( ( tokens != null ) && ( tokens.size() > 0 ) ) {
                        nextRecord = tokens;
                        break;
                    }
                }
            } else {
                nextRecord = _blockReader.getNextRecord(); 
            }
            return nextRecord;
        }

        public List<String> next() {
            return _nextElement;
        }

        private List<String> tokenizeLine(String line) {
            if ( _log.isDebugEnabled() ) {
                _log.debug("Tokenizing [" + line + "]");
            }
            List<String> tokens = null;
            Pattern pattern = getPattern();
            Matcher m = pattern.matcher(line);
            while ( m.find() ) {
                int j = m.groupCount();
                tokens = new ArrayList<String>(j);
                if ( _log.isDebugEnabled() ) {
                    _log.debug("Groupcount: " + j);
                }
                for ( int i = 1; i <= j; i++ ) {
                    tokens.add(m.group(i));
                }
            }
            return tokens;
        }

        public void close() {
            if ( _reader != null ) {
                try {
                    _reader.close();
                    _reader = null;
                } catch(Exception e) { 
                    _log.error("Error closing reader:" + e.toString());
                }
            }
        }

        public void remove() {
            throw new UnsupportedOperationException("Remove is unsupported.");
        }

        ///////////////////////////////////////////////////////////////////////
        //
        // FileBlockReder  - class to fetch chunk of the file a block at a time
        //
        ///////////////////////////////////////////////////////////////////////

        /**  
         * Inner class used for reading blocks of data from a file to
         * prevent memory overflows in the case where we are using 
         * multi-lined mode. Where we have to present the data to the reg
         * express engine in chunks.
         */
        private class FileBlockReader {

            /* debugging */
            int _blockNum;
        
            /* Records parsed from the file */
            private List<List<String>> _records;
        
            /* Reader of the stream */
            private Reader _reader;

            /* Any data left over after we've parsed a given block. */
            private StringBuffer _leftOver;

            /* Current records within the block we are navigating. */
            private int _currentRecord;

            public FileBlockReader(Reader reader) {
                _reader = reader;
                _records = new ArrayList<List<String>>();
            }

            /**
             * Get the next record. First check to see if we have 
             * any in-memory stored records, if there aren't
             * any parse the next block into records.
             */
            private synchronized List<String> getNextRecord() 
                throws Exception {
           
                List<String> nextGroup = null; 
                if ( ( _records.size() == 0 ) ||
                     ( _currentRecord == _records.size() ) ) {
                    _records.clear();
                    _currentRecord = 0;

                    int lastGroup = 0;
                    while ( lastGroup == 0 ) {
                        String block = getNextBlock();
                        if ( block != null ) {
                            int bufferSize = block.length();  
                            lastGroup = parseBuffer(block);
                            if ( lastGroup < bufferSize ) {
                                int index = 0;
                                if ( lastGroup != 0 )  {
                                    index = lastGroup + 1;
                                }
                                if ( _leftOver == null ) {
                                    _leftOver = new StringBuffer();
                                }
                                _leftOver.append(block.substring(index));

                                if ( _leftOver != null ) {
                                    if ( _log.isDebugEnabled() ) {
                                        _log.debug("LeftOver: "+ _leftOver.toString());
                                    }
                                    if ( _leftOver.length() == 0 ) {
                                        _leftOver = null;
                                    }
                                }
                            }
                        } else {
                            break;
                        }
                    }
                }

                if ( _currentRecord < _records.size() ) {
                    nextGroup = _records.get(_currentRecord++);
                }
                return nextGroup;
            }

            /**
             * Get the next chunk from the stream, if there was
             * any left over unparsed data left behind from the
             * last block, add that to the front of the buffer.
             */
            private String getNextBlock() throws GeneralException {
                StringBuffer sb = null;
                try {
                    _blockNum++;
                    if ( _log.isDebugEnabled() ) {
                        _log.debug("Processing block: " + _blockNum);
                    }

                    int actualBlockSize = 0;
                    int blockSize = getBlockSize();
                    sb = new StringBuffer(blockSize);
                    if ( chunkLeftOver() ) {
                        sb.append(_leftOver);                   
                        _leftOver = null;
                        actualBlockSize += sb.length();
                    }

                    if ( _log.isDebugEnabled() ) {
                        _log.debug("Reading from inputstream");
                    }

                    char[] chars = new char[blockSize];
                    int num = _reader.read(chars, 0, getBlockSize());
                    if ( num != -1 ) {
                        if ( _log.isDebugEnabled()) {
                            _log.debug("NextBlockSize: " + num + " NextBlock: " 
                                     + new String(chars));
                        }
                        sb.append(chars);
                        // reset the length to avoid passing back 
                        // unnessary nulled chars
                        actualBlockSize += num;
                        sb.setLength(actualBlockSize);
                    } else {
                        sb = null;
                        if ( _log.isDebugEnabled()) {
                            _log.debug("End of Stream Reached");
                        }
                    }

                } catch(Exception e ){
                    throw new GeneralException(e);
                }
                return ( sb != null ) ? sb.toString() : null;
            }

            private boolean chunkLeftOver() {
                if ( ( _leftOver != null ) && ( _leftOver.length() > 0 ) ) {
                    return true;
                }
                return false;
            }

            /** 
             * Parse the entire buffer of text in multiline mode.
             * In this case we get a return from find for each
             * record.  Return where the last place we found
             * a match.
             */
            private int parseBuffer(String buf) {
                Pattern pattern = getPattern();
                Matcher m = pattern.matcher(buf);
                int lastGroupEnd = 0;
                if ( _log.isDebugEnabled() ) {
                    _log.debug("Buffer [" + buf + "] length ["+buf.length()+"]"); 
                }          
                int num = 0 ;
                while ( m.find() ) {
                    int j = m.groupCount();
                    List<String> tokens = new ArrayList<String>(j);
                    for ( int i = 1; i <= j; i++ ) {
                        String groupToken = m.group(i);
                        if ( _log.isDebugEnabled() ) {
                            _log.debug("groupToken: " + groupToken);
                        }
                        tokens.add(m.group(i));
                        lastGroupEnd = m.end(i);
                    }
                    if ( _log.isDebugEnabled() ) {
                        _log.debug("[" + ++num + "] m.find() group count " +j
                                   + " lastGroupEnd: " + lastGroupEnd);
                    }    
                    _records.add(tokens);
                }
                return lastGroupEnd;
            }
        }
    }

    //////////////////////////////////////////////////////////////////
    //
    // Main 
    // 
    // Can be used for command line testing / data generation...
    //
    //////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws GeneralException, IOException {

        int nargs = (args != null ) ?  args.length : 0;
        if (nargs < 2) {
            throw new GeneralException("usage: RegExStreamParser expr filename true|false");
        }

        String regex = args[0];
        String fileName = args[1];

        boolean secret = false;
        if ( "doit".compareTo(regex) == 0 ) 
            secret = true; 
        if ( secret ) { 
            generateFile();
            System.exit(0);
        }

        FileInputStream fis =  new FileInputStream(new File(fileName));

        RegExStreamParser parser = new RegExStreamParser(fis, regex);
        if ( nargs == 3 ) {
            String multiLined = args[2];
            if ( multiLined.compareTo("true") == 0 ) {
                System.out.println("Multi-lined mode enabled.");
                parser.setMultiLinedMode(true); 
            }
        }

        CloseableIterator<List<String>> lines = parser.getTokenIterator();
        if ( lines == null ) {
            System.out.println("\n NO LINES\n");
            System.exit(1);
        }
        printTokens(lines); 
        lines.close();
    }

    private static void generateFile() throws IOException {
        File file = new File("c:/tmp/output.csv");
        FileWriter writer = new FileWriter(file);

        int threshold = 100000;
        int recNum = 0;

        Date date = getDate(-60);
        while ( recNum++ < threshold ) {
            date = incrementDate(date);
            String record = generateRandomRecord(date);
            writer.append(record);
        }
        writer.flush();
    }

    private static Date incrementDate(Date date) {
        GregorianCalendar cal =
            (GregorianCalendar) GregorianCalendar.getInstance();
	cal.setTime(date);
        cal.add(Calendar.SECOND,40);
        return cal.getTime();
    }

    private static Date getDate(int interval) {
        GregorianCalendar cal =
            (GregorianCalendar) GregorianCalendar.getInstance();
        Date retDate = null;
        cal.add(Calendar.DATE, interval);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 04);
        cal.set(Calendar.SECOND, 01);
        cal.set(Calendar.MILLISECOND, 98);
        cal.set(Calendar.AM_PM, Calendar.AM);
        retDate = cal.getTime();

        return retDate;
    }

    private static String generateRandomRecord(Date date) {

        StringBuffer sb = new StringBuffer();
        sb.append(Util.dateToString(date));
        sb.append(",");
        sb.append("Created");
        sb.append(",");
        sb.append("c:/data/customer.db");
        sb.append(",");
        sb.append("pholt");
        sb.append(",");
        sb.append("Info column");
	sb.append(",");
        sb.append("success\n");

        return sb.toString();
    }
 
    private static String printTokens(CloseableIterator<List<String>> lines ) 
        throws GeneralException {

        StringBuilder sb = new StringBuilder();
        int i=0;
        while ( lines.hasNext() ) {
            List<String> tokens = lines.next();
            int j=0;
            if ( ( tokens != null ) && ( tokens.size() > 0 ) ){
                sb.append("Line[" + i++ + "]\n");
            } else {
                sb.append("Line[" + i++ + "] HAS NO TOKENS.\n");
            }
            for ( String token : tokens ) {
                sb.append("\tToken[" + j++ + "] = " + token + "\n");
            }
        }
        System.out.println(sb.toString());
        return sb.toString();
    }
}
