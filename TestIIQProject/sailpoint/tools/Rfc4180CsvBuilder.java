/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;

public class Rfc4180CsvBuilder {

    /**
     * Default constructor, will initialize delimiter with a comma.
     */
    public Rfc4180CsvBuilder() {
        this(null);
    }

    /**
     * Constructor to initialize delimiter.
     *
     * @param delimiter The delimiter to use when parsing CSV data.  Defaults to COMMA if not provided.
     */
    public Rfc4180CsvBuilder(String delimiter) {
        if(delimiter == null || delimiter.isEmpty()) {
            Rfc4180CsvBuilder.delimiter = COMMA;
        }
        else {
            Rfc4180CsvBuilder.delimiter = delimiter;
        }
    }

    /**
     * Adds value to the current record
     * @param value The raw value to add to the current record
     */
    public void addValue( final String value ) {
        if( !expectNumberOfValuesFinalized ) {
            expectedNumberOfValues++;
        } 
        String preparedValue = escapeFormulaInjection(value);
        if( value == null ) {
            preparedValue = "null";
        } else if( shouldEncloseInQuotes( preparedValue ) ) {
            preparedValue = encloseInQuotes( preparedValue );
        }
        this.record.addValue( preparedValue );
    }

    /**
     * IIQETN-4954 Util method for escaping possible excel formula injection string values.
     * @param value String value to check
     * @return String escaped value
     */
    public static String escapeFormulaInjection(final String value) {
        if (Util.isNotNullOrEmpty(value) && "=@+-".indexOf(value.charAt(0)) != -1) {
           return SINGLE_QUOTE + value;
        }
        return value;
    }

    /**
     * Ends the current record and starts a new record.  Each record in a CSV
     * must contain the same number of values ( RFC 4180 2.4 )
     */
    public void endCurrentRecord() {
        if( record.getNumberOfValues() != expectedNumberOfValues ) {
            throw new Rfc4180Exception( "Current record has incorrect number of values: " + record.getNumberOfValues() + "!=" + expectedNumberOfValues );
        }
        expectNumberOfValuesFinalized = true;
        if( !record.isEmpty() ) {
            records.add( record );
        }
        record = new Rfc4180Record( expectedNumberOfValues );
    }
    
    /**
     * Returns a RFC 4180 compatible CSV from the records added.
     * Ends the current record if it has not yet been ended.
     * @return RFC 4180 compatible CSV
     */
    public String build() {
       return build(true);
    }

    public String build(boolean trimCarriageReturn) {
        if( !record.isEmpty() ) {
            endCurrentRecord();
        }
        StringBuilder responseBuilder = new StringBuilder();
        for( Rfc4180Record record : records ) {
            responseBuilder.append( record.getRecord() );
            responseBuilder.append( CRLF );
        }
         /* Trim trailing CRLF */
        if(trimCarriageReturn && responseBuilder.length() >= 2 ) {
            responseBuilder.setLength( responseBuilder.length() - 2 );
        }
        return responseBuilder.toString();
    }

    public void flush(){
        this.records.clear();
        expectedNumberOfValues=0;
        expectNumberOfValuesFinalized=false;
        record = new Rfc4180Record();
    }

    /**
     * If set to true, the csv builder will
     * enclose a LineFeed(\n) in quotes. Even
     * though this is included in the spec, we're doing
     * this here due to a bug in the
     * RFC4180LineIterator. See bug#12504
     * @param quoteLineFeed Should the line feed be quoted.
     */
    public void setQuoteLineFeed(boolean quoteLineFeed) {
        this.quoteLineFeed = quoteLineFeed;
    }

    private boolean shouldEncloseInQuotes( String value ) {

        //See bug#12504
        if (quoteLineFeed && value.contains( LF )){
            return true;
        }

        //See bug#28397
        if (quoteLineFeed && value.contains( CR )){
            return true;
        }

        /* RFC 4810 2.6 
         *  Fields containing line breaks (CRLF), double quotes, and commas
         *  should be enclosed in double-quotes
         */
        return value.contains( CRLF ) || value.contains( DOUBLE_QUOTE ) || value.contains( delimiter );
    }

    private String encloseInQuotes( String value ) {
        /* RFC 4810 2.7 
         *  If double-quotes are used to enclose fields, then a double-quote
         *  appearing inside a field must be escaped by preceding it with
         * another double quote.
         */
        return DOUBLE_QUOTE + value.replace( DOUBLE_QUOTE, DOUBLE_QUOTE + DOUBLE_QUOTE ) + DOUBLE_QUOTE;
    }

    /* Data members */
    private Rfc4180Record record = new Rfc4180Record();
    private List<Rfc4180Record> records = new ArrayList<Rfc4180Record>();
    private int expectedNumberOfValues = 0;
    private boolean expectNumberOfValuesFinalized = false;
    private static String delimiter;

    private boolean quoteLineFeed;
    
    /* public constants */
    public static final String COMMA = ",";

    /* Private constants */
    private static final String DOUBLE_QUOTE = "\"";
    private static final String SINGLE_QUOTE = "\'";
    private static final String CRLF = "\r\n";
    private static final String LF = "\n";
    private static final String CR = "\r";

    
    private static final class Rfc4180Record {
        public Rfc4180Record() {
            this( 0 );
        }
        
        public Rfc4180Record( int expectedFieldCount ) {
            this.expectedNumberOfValues = expectedFieldCount;
        }
        
        public boolean isEmpty() {
            return record.length() == 0;
        }

        public String getRecord() {
            return record.toString();
        }

        public void addValue( String value ) {
            numberOfValues++;
            if( expectedNumberOfValues > 0 ) {
                if( numberOfValues > expectedNumberOfValues ) {
                    throw new Rfc4180Exception( "Added more values in this record than previous record(s): " + numberOfValues + ">" + expectedNumberOfValues );
                }
            }
            if( numberOfValues > 1 ){
                record.append( delimiter );
            }
            record.append( value );
        }
        
        public int getNumberOfValues() {
            return numberOfValues;
        }

        private final int expectedNumberOfValues;
        private int numberOfValues = 0;
        private StringBuilder record = new StringBuilder();
    }
}
