/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Simple class that takes in a line and will 
 * return a list of Strings that represent each
 * column, following the csv RFC4180 to guide
 * its way through the parsing.
 * 
 *  
 */
public class RFC4180LineParser {


    private static Log _log = LogFactory.getLog(RFC4180LineParser.class);

    public static final char QUOTE_CHAR = '"';

    /**
     * The delimter that separates the field in the text we are going to parse.
     * RFC says this has to be a comma, but we are just using the RFC as 
     * a guide.
     */
    private char _delimiter;

    /**
     * If we know how many columns there are, a flag to indicate if the parsing 
     * routines should throw an exception if the columns returned from
     * the parsing is different then the expected size.
     */
    private boolean _tolerateColumnSizeMismatch;

    /**
     * The number of columns expected from the data source.  This is used for
     * two purposes if set.  First, it will pad the returned array of tokens
     * during parsing and secondly, it will optionally be used to check 
     * the data coming back after its parsed.
     */
    private int _numberOfColumns;

    /**
     * A flag to indicate if the parsing should ignore quotes.
     * False by default, but if true quotes are handled just like
     * any other character.
     */
    private boolean _ignoreQuotes;

    /** 
     * If set to true all of the values will be trimmed.
     * Each token parsed will be have leading and trailing 
     * whitespace omitted. 
     */
    private boolean _trimValues;

    /**
     * If a parsed token is empty or null and this flag is set to
     * true, it'll be excluded from the parsed token list. 
     * The default value for this flag is false.
     */
    private boolean _filterEmpty;

    private RFC4180LineParser() {
        _tolerateColumnSizeMismatch = false;
         // Use -1 to ignore this check for cases when we 
         // want to parse a header and we are trying to compute the columns
        _numberOfColumns = -1;
        _ignoreQuotes = false;
        _trimValues = false;
        _filterEmpty = false;
    }

    public RFC4180LineParser(char delimiter) {
        this();
        _delimiter = delimiter != 0 ? delimiter : Rfc4180CsvBuilder.COMMA.charAt(0);
    }

    /**
     * Create a new parser specifying the delimiter as a string. 
     * If the string is a single character the character is used
     * as a delimiter. Otherwise, if the string starts with \\u
     * it can be any UNICODE string representation.
     */
    public RFC4180LineParser(String delimiter) {
       this();
        setDelimiter(delimiter);
    }

    public RFC4180LineParser(char delimiter, int numCols) {
        this(delimiter);
        _numberOfColumns = numCols;
    }

    public void tolerateMissingColumns(boolean tolerate ) {
        _tolerateColumnSizeMismatch = tolerate;
    }

    public boolean tolerateMissingColumns() {
        return _tolerateColumnSizeMismatch;
    }

    public void setNumberOfColumns(int cols) {
        _numberOfColumns = cols; 
    }
 
    public int getNumberOfColumns() {
        return _numberOfColumns;
    }

    public void setIgnoreQuotes(boolean ignore) {
        _ignoreQuotes = ignore;
    }

    public boolean ignoreQuotes() {
        return _ignoreQuotes;
    }

    public void setTrimValues(boolean trim) {
        _trimValues = trim;
    }

    public boolean trimValues() {
        return _trimValues;
    }

    public void setDelimiter(String delimiter) {
        if ( delimiter == null ) 
            throw new UnsupportedOperationException("Delimiter cannot be null, you must specify the delimiter either as a single character or a unicode String value. For example  \\u0009 to represent a tab.");

        if ( delimiter.length() > 1 ) {
            if ( delimiter.startsWith("\\u") ) {
                _delimiter = (char)Integer.parseInt(delimiter.substring(2));
                if ( _log.isDebugEnabled() ) {
                    _log.debug("Delimiter '" + delimiter + "' was more then one character and started with \\u treating it as a unicode character.");
                }
            } else {
                throw new UnsupportedOperationException("'" + delimiter+ "' is invalid you must specify the delimiter either as a single character or a unicode String value. For example  \\u0009 to represent a tab.");
            }
        }
        if ( delimiter.length() == 1 ) {
             _delimiter = delimiter.charAt(0);
        }
    }

    public void setDelimiter(char c) {
        _delimiter = c;
    }

    public char getDelimiter() {
        return _delimiter;
    }

    public boolean filterEmpty() {
        return _filterEmpty;
    }

    public void setFilterEmpty(boolean filter) {
        _filterEmpty = filter;
    }

    /**
     *
     * Parse a line into tokens using the description in RFC4180 which
     * is designed for CSV but this method will support any delimiter
     * under the same contract.
     * <p>
     * Empty values are represented as null in the returned List.
     * </p>
     *
     * @see <a href="http://www.rfc-editor.org/rfc/rfc4180.txt">http://www.rfc-editor.org/rfc/rfc4180.txt</a>
     */
    public ArrayList<String> parseLine(String line) 
        throws GeneralException  {

        if ( _log.isDebugEnabled() ) {
            _log.debug("Line to Parse["+line+"]");
        }

        ArrayList<String> tokens = null;
        if ( _numberOfColumns > 0 ) {
            // pad the array in the case where we know the size
            // to avoid consuming code from having to worry 
            // about the array size being different then the
            // number of columns
            tokens = new ArrayList<String>(_numberOfColumns);
        } else {
            tokens = new ArrayList<String>();
        }

        if ( line != null ) {
            // Flag to indicate when we are in quotes 
            boolean inQuotes = false;
            StringBuffer token = new StringBuffer();
            char[] chars = line.toCharArray();
            for ( int i=0; i<chars.length; i++ ) {
                char ch = chars[i];
                //
                // QUOTE:
                //
                //  1) Can be escaped with two double quotes "", although 
                //  the two double quotes must also be enclosed in 
                //  double quotes
                //  2) Used to include both CRLF and delimiters inside text
                //
                if ( ( !_ignoreQuotes ) && ( ch == QUOTE_CHAR ) ) {
                   if ( inQuotes ) {
                       // check for nested quotes
                       boolean nestedQuotes = false;
                       int j = i + 1;
                       // Peek ahead and see if this is an escaped quote
                       if ( j < chars.length ) {
                           char nextChar = chars[j];
                           if  ( nextChar == QUOTE_CHAR ) {
                               token.append(ch);
                               i++;
                               nestedQuotes = true;
                           }
                       }
                       if ( !nestedQuotes ) {
                           inQuotes = false;
                       }
                   } else {
                       inQuotes = true;
                   }
                } else
                // 
                //  DELIMITER
                //
                //  1) If we are "in quotes" the char is part of the token
                //     otherwise 
                //  2) Marks the the end of a token
                // 
                if ( ch == _delimiter ) {
                    if ( inQuotes ) {
                        // in quotes so just append it
                        token.append(ch);
                    } else {
                        // This indicates we've hit the end of a token
                        String tokenValue = token.toString();
                        if ( tokenValue.length() == 0 ) {
                            tokenValue = null;
                        }

                        if ( _trimValues ) {
                            if ( tokenValue != null) 
                                tokenValue = tokenValue.trim();
                        }
                        if ( ( Util.getString(tokenValue) != null ) || ( !_filterEmpty ) ) {
                            tokens.add(tokenValue);
                        }
                        token = new StringBuffer();
                    }
                } else {
                    token.append(ch);
                }
                // check to see if we are at the end of the string,
                // if we are write the token to the token list
                if ( i == ( chars.length - 1 ) ) {
                    String tokenValue = token.toString();
                    if ( tokenValue.length() == 0 ) {
                        tokenValue = null;
                    }
                    if ( _trimValues ) {
                        if ( tokenValue != null) 
                            tokenValue = tokenValue.trim();
                    }

                    if ( ( Util.getString(tokenValue) != null ) || ( !_filterEmpty ) ) 
                        tokens.add(tokenValue);
                }
            } // for each character

            // If we are in quotes AND at the end of the line,  that indicates an error.
            if ( inQuotes ) {
                throw new GeneralException("\nLine ["+line+"]\n" + "\nProblem: Line has mis-matched quotes.\n");
            }
            if ( !tolerateMissingColumns() ) {
                // throw if there are missing columns in a line
                if ( ( _numberOfColumns > 0 ) && 
                     ( tokens.size() != _numberOfColumns ) ) {
                    throw new GeneralException("\nLine ["+line+"]\n" + "\nProblem: Line has invalid number of columns. Expected [" + _numberOfColumns + "] but found [" + tokens.size() +"]");
                }
            } else {
                // pad the results with spaces if necessary to make the results positional
                if ( ( _numberOfColumns > 0 ) && 
                     ( tokens.size() != _numberOfColumns ) ) {

                    int currentSize = tokens.size();
                    for ( int i=currentSize; i<_numberOfColumns; i++ ) {
                        tokens.add("");
                    }
                }
            }
        }
        return (tokens.size() > 0) ? tokens : null;
    }

    /**
     * Use the rules outline in RFC4180 to parse the line into tokens. 
     */
    public static List<String> parseLine(String delimiter, String src, boolean filterEmpty) {
        RFC4180LineParser parser =  new RFC4180LineParser(delimiter);
        parser.setTrimValues(true);
        parser.setFilterEmpty(filterEmpty);

        List<String> tokens = new ArrayList<String>();
        try {
            tokens = parser.parseLine(src);
        } catch(Exception e) {
            _log.error("RFC4180LineParser.parseLine: "+ e.toString());
        }
        return tokens;
    }
}
