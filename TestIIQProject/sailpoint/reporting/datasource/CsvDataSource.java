/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.PersistedFile;
import sailpoint.object.ReportColumnConfig;
import sailpoint.persistence.PersistedFileInputStream;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CsvDataSource implements TopLevelDataSource {

    private static final Log log = LogFactory.getLog(CsvDataSource.class);

    private SailPointContext context;
    private PersistedFile file;
    private List<String> fields;
    private Map<String, ReportColumnConfig> columnConfigs;
    private Map<String, String> currentRow;
    private BufferedReader reader;
    private RFC4180LineParser parser;
    private RFC4180LineIterator iterator;
    private int lineCount;
    private String delimiter;

    private boolean supportComment = false;
    
    public CsvDataSource(SailPointContext ctx, List<ReportColumnConfig> columnConfigs, PersistedFile file, String delimiter) {
        this(ctx, columnConfigs, file, delimiter, false);
    }

    public CsvDataSource(SailPointContext ctx, List<ReportColumnConfig> columnConfigs, PersistedFile file, String delimiter, boolean supportComment) {
        this.context = ctx;
        this.file = file;
        this.columnConfigs = new HashMap<String, ReportColumnConfig>();
        this.fields = new ArrayList<String>();
        this.delimiter = delimiter;
        this.supportComment = supportComment;
        
        if (columnConfigs != null){
            for(ReportColumnConfig col : columnConfigs){
                if (!col.isHidden()){
                    fields.add(col.getField());
                    this.columnConfigs.put(col.getField(), col);
                }
            }
        }
    }

    public void setMonitor(Monitor monitor) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void close() {
        try {
            if (reader != null)
                reader.close();
        } catch (IOException e) {
            log.error("Could not close CSV datasource.", e);
        }
    }

    @Override
    public boolean next() throws JRException {

        currentRow = null;

        try {

            if (reader == null)
                startReading();

            List<String> values = readLine();
            if (values != null){
                currentRow = new HashMap<String, String>();
                for(int i=0;i<columnConfigs.keySet().size();i++){
                    currentRow.put(fields.get(i), values.get(i));
                }
            }
        } catch (Exception e){
            log.error("Error reading a reporting PersistedFile id=" + file.getId() + " at line " + lineCount, e);
             throw new RuntimeException(e);
        }

        return currentRow != null;
    }

    @Override
    public Object getFieldValue(JRField jrField) throws JRException {

        String field = jrField.getName();

        if (!currentRow.keySet().contains(field)){
            throw new JRException("The field '"+field+"' was not found in the list of field names in the report.");
        }

        String val = currentRow.get(field);

        ReportColumnConfig column = this.columnConfigs.get(field);
        if (val != null && column.getValueClass() != null && column.getValueClass().equals("java.util.List")){
            return Util.csvToList(val);
        }

        return val;
    }

    private void startReading() throws IOException, GeneralException{
        PersistedFileInputStream inStream = new PersistedFileInputStream(context, file);
        reader = new BufferedReader(new InputStreamReader(inStream, "UTF-8"));
        parser = new RFC4180LineParser(this.delimiter);

        iterator = new RFC4180LineIterator(reader);

        // Skip all comments if comment is supported, 
        // and read the column header line from the csv
        String line =  iterator.readLine();
        if (supportComment) {
            while (line != null && line.startsWith("#")) {
                line = iterator.readLine();
            }
        }
    }

    private List<String> readLine() throws IOException, GeneralException {
        String line =  iterator.readLine();
        lineCount++;
        if (line != null){
            List<String> parsedLine = parser.parseLine(line);
            return parsedLine;
        }

        return null;
    }
}
