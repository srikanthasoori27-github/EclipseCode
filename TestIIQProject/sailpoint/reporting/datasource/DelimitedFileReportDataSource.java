/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.DelimitedFileConnector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Sort;
import sailpoint.reporting.JasperExecutor;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class DelimitedFileReportDataSource implements JavaDataSource{

    private static final Log log = LogFactory.getLog(DelimitedFileReportDataSource.class);
    Attributes<String,Object> _arguments;
    String _groupBy;
    List<Sort> _sortBy;
    SailPointContext _context;
    protected Integer _dataSize;
    QueryOptions _qopts;
    /**
     * Locale for report
     */
    private Locale _locale;

    /**
     * Timezone for report.
     */
    private TimeZone _timezone;

    /**
     * Current application object
     */
    private Application _object;

    /**
     * FileInfo object for the current application object
     */
    private FileInfo currentFileInfo;

    /**
     * Iterator over the applications
     */
    private IncrementalObjectIterator<Application> iterator;


    private class FileInfo {
        public String fileName;
        public Long lastModified;
        public Long fileSize;
        public boolean exists = false;
    }

    public SailPointContext getContext() {
        return _context;
    }

    public DelimitedFileReportDataSource() {

    }

    public void initialize(SailPointContext context,
                           LiveReport report, Attributes<String, Object> arguments, String groupBy, List<Sort> sort)
            throws GeneralException {
        this._context = context;
        this._arguments = (Attributes<String,Object>)arguments;
        this._groupBy = groupBy;
        this._sortBy = sort;
        this.currentFileInfo = null;
        this._timezone = (TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE);
        this._locale = (Locale)arguments.get(JRParameter.REPORT_LOCALE);
        _qopts = buildQueryOptions();
        this.iterator = new IncrementalObjectIterator<>(_context, Application.class, _qopts);
    }

    public int getSizeEstimate() throws GeneralException {
        if(this._dataSize == null) {
            this._dataSize = getContext().countObjects(Application.class, _qopts);
        }
        return _dataSize;
    }

    public Object getFieldValue(String fieldName) throws GeneralException {

        Object value = null;

        if (fieldName.equals("fileName")) {
            value = this.currentFileInfo.fileName;

        } else if (fieldName.equals("fileDate")) {
            if (this.currentFileInfo.exists) {
                value = new Date(this.currentFileInfo.lastModified);
            }

        } else if (fieldName.equals("fileSize")) {
            if (this.currentFileInfo.exists) {
                java.text.DecimalFormat thousandths = new java.text.DecimalFormat();
                thousandths.setMaximumFractionDigits(3);
                value = thousandths.format(this.currentFileInfo.fileSize/1000000F);
            } else {
                value = getMessage(MessageKeys.REPT_APP_DELIMITED_MISSING_FILE);
            }

        } else if (fieldName.equals("refreshDate")) {
            value = _object.getDateAttributeValue("acctAggregationStart");
        } else if (fieldName.equals("daysOld")) {
            if (this.currentFileInfo.exists) {
                java.text.DecimalFormat tenths = new java.text.DecimalFormat();
                tenths.setMaximumFractionDigits(1);
                Date now = new Date();
                value = tenths.format((now.getTime() - this.currentFileInfo.lastModified) / (double)(24*60*60*1000));
            } else {
                value = getMessage(MessageKeys.REPT_APP_DELIMITED_MISSING_FILE);
            }
        }
        else if(fieldName.equals("owner")) {
            Identity owner = _object.getOwner();
            if(owner!=null) {
                String ownerName = new String(owner.getName());
                value = ownerName;
            }
        }
        else if(fieldName.equals("remediator")) {
            List<Identity> revoker = _object.getRemediators();
            if(!revoker.isEmpty()) {
                //We can only have one revoker so just reference the first element
                String revokerName = new String(revoker.get(0).getDisplayableName());
                value = revokerName;
            }
            else {
                //If there is no remediator assigned to this application
                value = getMessage(MessageKeys.REPT_APP_NO_REVOKERS);
            }
        }
        else if(fieldName.equals("application")) {
            value = _object.getName();
        }

        return value;
    }

    public void setMonitor(Monitor monitor) {
        // We will use the Task Monitor to take care of Monitoring
    }

    public void close() {

    }

    public Object getFieldValue(JRField arg0) throws JRException {
        String fieldName = arg0.getName();
        try {
            return getFieldValue(fieldName);
        } catch (GeneralException e) {
            log.error("Exception thrown while getting field value for Delimited File Report. "
                    + "Exception [" + e.getMessage() + "].");

            throw new JRException(e);
        }
    }

    private File getFileHandle(String fileName) throws GeneralException {
        // sniff the file see if it's relative
        File file = new File(fileName);
        if ( ( !file.isAbsolute() ) && ( !file.exists() ) ) {
            // see if we can append sphome and find it
            String appHome = Util.getApplicationHome();
            if ( appHome != null ) {
                file = new File(appHome + File.separator + fileName);
                if ( !file.exists() )
                    file = new File(fileName);
            }
        }
        return file;
    }

    private FileInfo getFileInfo(Application app) throws JRException {
        FileInfo fileInfo = null;

        try {
            // Filter out applications that are not local files.
            if (app != null && "local".equals(app.getStringAttributeValue("filetransport"))) {
                String fileName = null;
                DelimitedFileConnector dfc = new DelimitedFileConnector(app);

                try {
                    Schema accountSchema = app.getSchema("account");
                    if (accountSchema != null) {
                        fileName = dfc.getFileName(accountSchema);
                    }
                } catch (ConnectorException ce) {
                    log.debug("Exception thrown while acquiring schema or file name from application "
                            + app.getName() + ". " + "Exception [" + ce.getMessage() + "].");
                    // bug #5146: At this point, either there is no schema or there is no file name.
                    // Either way, it's as if the filename is not defined, so we continue with fileName = null.
                }

                if (fileName != null) {
                    File file = getFileHandle(fileName);

                    fileInfo = new FileInfo();
                    fileInfo.fileName = fileName;
                    fileInfo.fileSize = file.length();
                    fileInfo.lastModified = file.lastModified();
                    fileInfo.exists = file.exists();
                } else {
                    fileInfo = new FileInfo();
                }
            }
        } catch (GeneralException ge) {
            log.error("Exception thrown while preparing data for Delimited File Report. "
                    + "Exception [" + ge.getMessage() + "].");

            throw new JRException(ge);
        }

        return fileInfo;
    }

    public boolean next() throws JRException {

        boolean hasMore = false;
        while (iterator != null && iterator.hasNext()) {
            _object = iterator.next();
            this.currentFileInfo = getFileInfo(_object);
            // If its null, then this was not a local file and therefore was skipped, so move on to the next
            if (this.currentFileInfo != null) {
                hasMore = true;
                break;
            }
        }

        return hasMore;
    }

    public void setLimit(int startPage, int pageSize) {
        // We do not support live preview
    }

    protected QueryOptions buildQueryOptions() {
        List<Filter> filters = new ArrayList<Filter>();

        JasperExecutor.addEQFilter(filters, this._arguments, "applications", "id", null);

        if(this._arguments.get("owners") !=null) {
            Object owners =this._arguments.get("owners");
            if(owners instanceof String && (owners.toString().indexOf(",")>=0)) {
                List<String> list = Util.csvToList(owners.toString());
                List<Filter> ownerFilters = new ArrayList<Filter>();
                List<Filter> secondaryOwnerFilters = new ArrayList<Filter>();
                for(String owner : list) {
                    ownerFilters.add(Filter.eq("owner.id", owner));
                    secondaryOwnerFilters.add(Filter.eq("secondaryOwners.id", owner));
                }
                Filter ownerOr = Filter.or(ownerFilters);
                Filter secondaryOwnerOr = Filter.or(secondaryOwnerFilters);

                filters.add(Filter.or(ownerOr, secondaryOwnerOr));
            } else {
                filters.add(Filter.or(Filter.eq("owner.id", owners),Filter.eq("secondaryOwners.id", owners)));
            }
        }

        //"Delimited File Parsing Connector" is kept as a possible type for backward compatibility
        filters.add(Filter.or(
                Filter.eq("type", "Delimited File Parsing Connector"),
                Filter.eq("type", DelimitedFileConnector.CONNECTOR_TYPE)));


        QueryOptions qo = new QueryOptions();
        qo.setFilters(filters);

        //Build Grouping and Ordering
        if(_groupBy != null) {
            qo.addGroupBy(_groupBy);
        }

        if(_sortBy != null) {
            for(Sort s : _sortBy) {
                if(s.getField().equals("application")) {
                    qo.addOrdering("name", s.isAscending());
                }
                else if(s.getField().equals("owner")) {
                    qo.addOrdering("owner.name", s.isAscending());
                }
            }
        }

        return qo;
    }


    public String getMessage(String key, Object... args) {
        if (key == null)
            return null;

        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(_locale, _timezone);
    }

    public QueryOptions getBaseQueryOptions() {
        return _qopts;
    }

    public String getBaseHql() {
        return null;
    }
}
