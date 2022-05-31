/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Sort;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.reporting.ReportHelper;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class WorkItemArchiveDataSource implements JavaDataSource {

    private static final Log log = LogFactory.getLog(WorkItemArchiveDataSource.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments and constants
    //
    //////////////////////////////////////////////////////////////////////

    // Argument passed to the datasource to let it know
    // which table we are searching
    private static final String ARG_IS_ARCHIVE = "isArchive";

    private static final String ARG_REMINDERS_MAX = "remindersMax";
    private static final String ARG_REMINDERS_MIN = "remindersMin";

    // This arg is used to determine whether live or archived records
    // are displayed
    private static final String ARG_STATUS_OPTIONS = "statusOptions";

    // The status column, which requires special handlign because it
    // derived based upon which table to record comes from
    private static final String COL_ARCHIVE_STATUS = "archiveStatus";

    // List of columns that are not included with archived work items
    private static final List<String> WORKITEM_COLS = Arrays.asList("expiration", "reminders", "escalationCount");


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private TimeZone timezone;
    private Locale locale;
    private List<Sort> sort;

    // The live work item datasource, may be null
    private ProjectionDataSource workItems;

    // The archived live work item datasource, may be null
    private ProjectionDataSource workItemArchives;

    // The current item being displayed
    private Map<String, Object> currentItem;

    // The most recent live and archived work item objects
    // popped from the respective datasources. Every iteration
    // we compare the two and determine which should be displayed
    // based on sort order.
    private Map<String, Object> currentWorkItem;
    private Map<String, Object> currentWorkItemArchive;

    private String archiveStatus;
    private String workItemStatus;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {

        this.context = context;
        this.timezone = (TimeZone) arguments.get(JRParameter.REPORT_TIME_ZONE);
        this.locale = (Locale) arguments.get(JRParameter.REPORT_LOCALE);
        this.sort = sort;

        ReportHelper helper = new ReportHelper(context, locale, timezone);
        QueryOptions ops = null;

        // if active work items are being displayed, create a
        // projection datasource over that table.
        if (!"archive".equals(arguments.get(ARG_STATUS_OPTIONS))){

            // let our datasource know we're querying work items
            arguments.put(ARG_IS_ARCHIVE, false);
            ops = helper.getFilterQueryOps(report, arguments);

            // remove any sorting that isn't present on the WorkItem object
            filterSortOrder(ops, false);

            workItems = new ProjectionDataSource(WorkItem.class,  ops, report.getGridColumns(), locale, timezone);
        }

        // check the min reminders param, any value here means that we can
        // include archived items in the report since they do not store reminders
        int remindersMinCount = Util.otoi(arguments.get(ARG_REMINDERS_MIN));

        // Create archived work items datasource
        if (remindersMinCount == 0 && !"active".equals(arguments.get(ARG_STATUS_OPTIONS))){

            Attributes<String, Object> archiveArguments = (Attributes<String, Object>)arguments.clone();

            // let our datasource know we're querying archives
            archiveArguments.put(ARG_IS_ARCHIVE, true);
            // remove reminders query
            archiveArguments.remove(ARG_REMINDERS_MIN);
            archiveArguments.remove(ARG_REMINDERS_MAX);
            QueryOptions archiveOps = helper.getFilterQueryOps(report, archiveArguments);

            // remove any columns or sorting that aren't present on the WorkItemArchive object
            filterSortOrder(archiveOps, true);
            List<ReportColumnConfig> archiveCols = new ArrayList<ReportColumnConfig>();
            for(ReportColumnConfig col : report.getGridColumns()){
                // ignore columns that are only present on live workitems
                if (!WORKITEM_COLS.contains(col.getField())){
                    archiveCols.add(col);
                }
            }

            // Add an additional property for work item archive - ownerName
            // since archives have a null owner
            archiveCols.add(new ReportColumnConfig("owner", "ownerName"));

            workItemArchives = new ProjectionDataSource(WorkItemArchive.class,  archiveOps, archiveCols,
                    locale, timezone);
        }

        // Go ahead and generate this text now
        archiveStatus = Internationalizer.getMessage(MessageKeys.WORK_ITEM_STATUS_ARCHIVED, locale);
        workItemStatus = Internationalizer.getMessage(MessageKeys.WORK_ITEM_STATUS_ACTIVE, locale);
    }

    public boolean next() throws JRException {

        currentItem = null;

        // get candidate objects to display from each datasource
        try {
            if (currentWorkItem == null && workItems != null && workItems.next()){
                currentWorkItem = itemToMap(workItems, false);
            }

            if (currentWorkItemArchive == null && workItemArchives != null && workItemArchives.next()){
                currentWorkItemArchive = itemToMap(workItemArchives, true);
            }
        } catch (GeneralException e) {
            log.error(e);
            throw new JRException(e);
        }

        // determine which item should be displayed first
        currentItem = compareItems();

        return currentItem != null;
    }

    private void filterSortOrder(QueryOptions ops, boolean isArchive){

        int idx = ops.getOrderingIndex(COL_ARCHIVE_STATUS);
        if (idx > -1){
            List<QueryOptions.Ordering> order = ops.getOrderings();
            order.remove(idx);
            ops.setOrderings(order);
        }

        if (isArchive){
            // remove any work item columns from this sort which
            // are not included in the archive model
            for(String col : WORKITEM_COLS){
                List<QueryOptions.Ordering> order = ops.getOrderings();
                idx = ops.getOrderingIndex(col);
                if (idx > -1){
                    order.remove(idx);
                    ops.setOrderings(order);
                }
            }
        }
    }

    private Map<String, Object> itemToMap(ProjectionDataSource ds, boolean isArchive) throws GeneralException{

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("name", Util.stripLeadingChar((String) ds.getFieldValue("name"), '0'));
        result.put("description", ds.getFieldValue("description"));

        Object owner = ds.getFieldValue("owner");
        if (owner != null && owner instanceof Identity)
            owner = ((Identity) owner).getName();
        result.put("owner", owner);

        Object requester = ds.getFieldValue("requester");
        if (requester != null && requester instanceof Identity)
            requester = ((Identity) requester).getName();
        result.put("requester", requester);

        result.put("type", ds.getFieldValue("type"));
        result.put("level", ds.getFieldValue("level"));
        if (result.get("level") == null)
            result.put("level", WorkItem.Level.Normal);
        result.put("expiration", ds.getFieldValue("expiration"));
        result.put("state", ds.getFieldValue("state"));
        result.put("reminders", ds.getFieldValue("reminders"));
        result.put("escalationCount", ds.getFieldValue("escalationCount"));
        result.put("archiveStatus", isArchive ? archiveStatus : workItemStatus);

        return result;
    }

    private Map<String, Object> compareItems(){

        if (currentWorkItem == null && currentWorkItemArchive == null)
            return null;

        Map<String, Object> selected = null;

        int comparison = performComparison(currentWorkItem, currentWorkItemArchive);

        // Find the next item in the sort order and null
        // out the current item placeholder
        if (comparison > 0){
            selected = currentWorkItemArchive;
            currentWorkItemArchive = null;
        } else {
            selected = currentWorkItem;
            currentWorkItem = null;
        }

        return selected;
    }

    private int performComparison(Map<String, Object> row1, Map<String, Object> row2){

        if (row1 == null && row2 == null){
            return 0;
        } else if (row1 == null){
            return 1;
        } else if (row2 == null){
            return -1;
        }

        if (sort != null){
            for(Sort s : sort){
                Object val1 = row1.get(s.getField());
                Object val2 = row2.get(s.getField());

                int c = compare(val1, val2);
                if (c != 0)
                    return c * (s.isAscending() ? -1 : 1);
            }
        }

        // default sort by name
        Long name1 = row1.get("name") != null ? Long.parseLong(row1.get("name").toString()) : null;
        Long name2 = row2.get("name") != null ? Long.parseLong(row2.get("name").toString()) : null;
        return compare(name1, name2);
    }

    private int compare(Object o1, Object o2){

        if (o1 == null && o2 == null){
            return 0;
        } else if (o1 == null){
            return 1;
        } else if (o2 == null){
            return -1;
        }

        if (Comparable.class.isAssignableFrom(o1.getClass())){
            return ((Comparable)o1).compareTo(o2);
        }

        return 0;
    }


    public Object getFieldValue(String fieldName) throws GeneralException {

        Object value = currentItem.get(fieldName);

        if (fieldName.equals("state") && value == null) {
            value = Internationalizer.getMessage(MessageKeys.WORK_ITEM_STATE_OPEN, locale);
        }

        return value;
    }

    public Object getFieldValue(JRField jrField) throws JRException {
        String name = jrField.getName();
        Object value = null;
        try {
            value = getFieldValue(name);
        } catch (GeneralException e) {
            log.error(e);
            throw new JRException(e);
        }
        return value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Unimplemented interface methods - since this report does not include
    //   a preview, we can ignore some of these
    //
    //////////////////////////////////////////////////////////////////////

    public void setLimit(int startPage, int pageSize) {

    }

    public int getSizeEstimate() throws GeneralException {
        return 0;
    }

    public String getBaseHql() {
        return null;
    }

    public QueryOptions getBaseQueryOptions() {
        return null;
    }

    public void setMonitor(Monitor monitor) {

    }

    public void close() {

        if (workItems != null) {
            workItems.close();
        }
        if (workItemArchives != null) {
            workItemArchives.close();
        }
    }

}
