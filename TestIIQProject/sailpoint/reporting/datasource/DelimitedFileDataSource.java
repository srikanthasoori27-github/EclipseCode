/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.DelimitedFileConnector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;

/**
 * @author dan.smith
 *
 * Datasource that can be used to read a delimited file
 * to fill a report with data.
 *
 * If there are columns defined in the file then it'll
 * automatically be read from the first line and used
 * as the column names.  
 *
 */
public class DelimitedFileDataSource extends SailPointDataSource {

    private static final Log log = LogFactory.getLog(DelimitedFileDataSource.class);

    /**
     * Iterator over each line in the file.
     */
    RFC4180LineIterator _iterator;

    /**
     * Parser used to parse tokens from each line.
     */
    RFC4180LineParser _parser;

    /**
     * FileName 
     */
    String _fileName;

    /**
     * Column Names
     */
    List<String> _columnNames;

    /**
     * The current assembled object.
     */
    Map<String,Object> _currentRow;

    public DelimitedFileDataSource(String fileName, char delimiter) {
        _parser = new RFC4180LineParser(delimiter);
        _fileName = fileName;
    }

    public DelimitedFileDataSource(String fileName, char delimiter, 
                                  List<String> colNames) {
        this(fileName,delimiter);
        _columnNames = colNames;
    }

    public DelimitedFileDataSource(String fileName) {
        this(fileName,',');
    }

    public DelimitedFileDataSource(String fileName, List<String> colNames) {
        this(fileName,',', colNames);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        try {
            _iterator = 
                new RFC4180LineIterator(
                    new BufferedReader(new FileReader(_fileName)));

            // if column names are defined assume the first line 
            // defines the columns
            if ( _columnNames == null ) {
                String line = _iterator.readLine();
                _columnNames = _parser.parseLine(line);
            }

        } catch(Exception e) {
            throw new GeneralException(e);
        }
    }

    /* 
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        if ( log.isDebugEnabled() ) {
            log.debug("Getting Field: " + fieldName);
        }
        return _currentRow.get(fieldName);
    }


    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if ( _iterator != null ) {
            try {
                String line = _iterator.readLine();
                if ( line != null ) {
                    List<String> tokens = _parser.parseLine(line);
                    if ( _currentRow != null ) {
                        // Help out garbage collection
                        _currentRow.clear();
                        _currentRow = null;
                    }
                    _currentRow = DelimitedFileConnector.defaultBuildMap(_columnNames, tokens);
                    hasMore = true;
                } 
            } catch (Exception e) {
                log.warn("Unable to read line" + e.toString());
            }
        }
        return hasMore;
    }
}
