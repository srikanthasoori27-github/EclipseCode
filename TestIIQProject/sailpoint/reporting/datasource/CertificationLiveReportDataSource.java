/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import sailpoint.api.EntitlementDescriber;
import sailpoint.api.SailPointContext;
import sailpoint.object.ArchivedCertificationEntity;
import sailpoint.object.ArchivedCertificationItem;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.messages.MessageKeys;

/**
 * Backing datasource for certification reports. We have to use
 * this because users can optionally include the excluded items
 * in the report, which are stored in a separate table :(
 *
 * This means we have to merge the data semi-manually by using two
 * separate datasources. Each datasource is sorted independantly, then
 * we manually compare each item to detemine which should be
 * returned next.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationLiveReportDataSource extends ProjectionDataSource implements JavaDataSource {

    private static final Log log = LogFactory.getLog(CertificationLiveReportDataSource.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments and constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final String COL_EXCLUSION_PARENT = "__parent";
    private static final String COL_EXCLUSION_TYPE = "__type";
    private static final String COL_EXCLUSION_ENTITLEMENTS = "__entitlements";
    private static final String COL_EXCLUSION_ROLE = "__bundle";
    private static final String COL_EXCLUSION_VIOLATION = "__violation";

    private static Map<String, String> replacementColumns = new HashMap<String, String>();

    static{
        replacementColumns.put("exceptionEntitlements.application", "exceptionApplication");
        replacementColumns.put("exceptionEntitlements.nativeIdentity", "exceptionNativeIdentity");
        replacementColumns.put("summaryStatus", "parent.reason");
        replacementColumns.put("exceptionEntitlements", "entitlements");
        // iiqtc-183 Without a replacementColumn entry the property exceptionEntitlements.displayName will
        // resolve to null when building the ReportColumnConfig to be used in the projection search when
        // excluded items are included in the report. When the row is built from results of the query
        // a null property will not be found in the results and there will no value displayed in the UI.
        replacementColumns.put("exceptionEntitlements.displayName", "targetName");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private LiveReport report;
    private TimeZone timezone;
    private Locale locale;
    private List<Sort> sort;
    Attributes<String, Object> arguments;
    private int start;
    private int limit;
    private boolean exclusionsIncluded;

    private QueryOptions baseOptions;
    private QueryOptions certItemOptions;
    private QueryOptions exclusionOptions;

    // The live work item datasource, may be null
    private ProjectionDataSource certItems;

    // The archived live work item datasource, may be null
    private ProjectionDataSource excludedItems;

    // The current item being displayed
    private Map<String, Object> currentItem;

    // The most recent live and archived work item objects
    // popped from the respective datasources. Every iteration
    // we compare the two and determine which should be displayed
    // based on sort order.
    private Map<String, Object> currentCertItem;
    private Map<String, Object> currentExcludedItem;


    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {

        this.context = getContext();
        this.timezone = (TimeZone) arguments.get(JRParameter.REPORT_TIME_ZONE);
        this.locale = (Locale) arguments.get(JRParameter.REPORT_LOCALE);
        this.sort = sort;
        this.report = report;
        this.arguments = arguments;

        exclusionsIncluded = arguments.getBoolean("exclusions");

        ReportHelper helper = new ReportHelper(context, locale, timezone);

        certItemOptions = helper.getFilterQueryOps(report, arguments);
        certItemOptions.setDistinct(true);

        // baseOptions are used to create charts, so they should
        // not include any sorting.
        baseOptions = new QueryOptions(certItemOptions);
        baseOptions.setOrderings(new ArrayList<QueryOptions.Ordering>());
    }

    private void prepareObject() throws GeneralException{

        ReportHelper helper = new ReportHelper(context, locale, timezone);

        certItemOptions.setResultLimit(limit);
        certItemOptions.setFirstRow(start);

        certItems = new ProjectionDataSource(CertificationItem.class,  certItemOptions, report.getGridColumns(), locale, timezone);


        if (exclusionsIncluded){

            Attributes<String, Object> archiveArguments = (Attributes<String, Object>)arguments.clone();

            exclusionOptions = helper.getFilterQueryOps(report, archiveArguments);

            // remove any columns or sorting that aren't present on the WorkItemArchive object
            filterSortOrder(exclusionOptions, true);

            List<ReportColumnConfig> cols = new ArrayList<ReportColumnConfig>();
            for(ReportColumnConfig col : report.getGridColumns()){
                cols.add(filterProperties(col));
            }

            // Add columns to get additional data we need to handle exclusions.
            cols.add(new ReportColumnConfig(COL_EXCLUSION_PARENT, "parent"));
            cols.add(new ReportColumnConfig(COL_EXCLUSION_ENTITLEMENTS, "entitlements"));
            cols.add(new ReportColumnConfig(COL_EXCLUSION_TYPE, "type"));
            cols.add(new ReportColumnConfig(COL_EXCLUSION_ROLE, "bundle"));
            cols.add(new ReportColumnConfig(COL_EXCLUSION_VIOLATION, "violationSummary"));

            excludedItems = new ProjectionDataSource(ArchivedCertificationItem.class, exclusionOptions,
                    cols, locale, timezone);
        }
    }

    private ReportColumnConfig filterProperties(ReportColumnConfig col) throws GeneralException{

        ReportColumnConfig archiveCol = (ReportColumnConfig)col.deepCopy((XMLReferenceResolver)context);
        archiveCol.setProperty(filterProperty(archiveCol.getProperty()));
        archiveCol.setIfEmpty(filterProperty(archiveCol.getIfEmpty()));

        List<String> scriptArgs = col.getScriptArgumentsList();
        if (scriptArgs != null && !scriptArgs.isEmpty()){
            List<String> updatedArgs = new ArrayList<String>();
            for(String arg : scriptArgs){
                String updatedVal = filterProperty(arg);
                if (updatedVal != null){
                    updatedArgs.add(updatedVal);
                }
            }
            archiveCol.setScriptArguments(Util.listToCsv(updatedArgs));
        }

        return archiveCol;
    }

    private String filterProperty(String property){

        if (property != null){
            if (property != null && replacementColumns.containsKey(property))
                return replacementColumns.get(property);

            // excluded items dont have actions
            if ("action".equals(property) ||
                    property.startsWith("action.") ||
                    property.startsWith("parent.firstname") ||
                    property.startsWith("parent.lastname") ||
                    property.startsWith("summaryStatus") ||
                    property.startsWith("exceptionEntitlements") ||
                    property.startsWith("recommendValue")) {
                return null;
            }
        }

        return property;
    }

    public boolean next() throws JRException {

        currentItem = null;

        if (certItems == null){
            try {
                prepareObject();
            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }


        // get candidate objects to display from each datasource
        try {
            if (currentCertItem == null && certItems != null && certItems.next()){
                currentCertItem = itemToMap(certItems, false);
            }

            if (currentExcludedItem == null && excludedItems != null && excludedItems.next()){
                currentExcludedItem = itemToMap(excludedItems, true);
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


    }

    private Map<String, Object> itemToMap(ProjectionDataSource ds, boolean isArchive) throws GeneralException{

        Map<String, Object> result = new HashMap<String, Object>();

        for(ReportColumnConfig col : report.getGridColumns()){

            Object val = null;

            if (isArchive && "firstName".equals(col.getField())){
                ArchivedCertificationEntity archiveEntity = (ArchivedCertificationEntity)ds.getFieldValue(COL_EXCLUSION_PARENT);
                val =  archiveEntity.getEntity().getFirstname();
            } else if (isArchive && "lastName".equals(col.getField())){
                ArchivedCertificationEntity archiveEntity = (ArchivedCertificationEntity)ds.getFieldValue(COL_EXCLUSION_PARENT);
                val =  archiveEntity.getEntity().getLastname();
            } else if (isArchive && "instance".equals(col.getField())){
                CertificationItem.Type type = (CertificationItem.Type)ds.getFieldValue(COL_EXCLUSION_TYPE);
                if (CertificationItem.Type.Exception.equals(type)){
                    List<EntitlementSnapshot> snap =
                            (List<EntitlementSnapshot>)ds.getFieldValue(COL_EXCLUSION_ENTITLEMENTS);
                    if (snap != null && !snap.isEmpty()){
                        val = snap.get(0).getInstance();
                    }
                }
            } else if (isArchive && "entitlements".equals(col.getField())){

                String roleName = (String)ds.getFieldValue(COL_EXCLUSION_ROLE);
                String violationSummary = (String)ds.getFieldValue(COL_EXCLUSION_VIOLATION);
                CertificationItem.Type type = (CertificationItem.Type)ds.getFieldValue(COL_EXCLUSION_TYPE);
                if (roleName != null){
                    val = roleName;
                } else if (violationSummary != null){
                    val = violationSummary;
                } else if (CertificationItem.Type.Exception.equals(type)){
                    List<EntitlementSnapshot> snap =
                            (List<EntitlementSnapshot>)ds.getFieldValue(COL_EXCLUSION_ENTITLEMENTS);
                    if (snap != null && !snap.isEmpty()){
                        val = EntitlementDescriber.summarize(snap.get(0));
                    }
                }
            } else if(isArchive && "exclusionExplanation".equals(col.getField())) {
                /* isArchive here means that this is an excluded item so we want to get the explanation from the parent
                 * ArchivedCertifcationEntity */
                ArchivedCertificationEntity archivedEntity = (ArchivedCertificationEntity) ds.getFieldValue(COL_EXCLUSION_PARENT);
                if(archivedEntity != null) {
                    val = archivedEntity.getExplanation();
                }
            } else if (isArchive &&
                    ("recommendation".equals(col.getField()) ||
                            "recommendationReasons".equals(col.getField()) ||
                            "recommendationTimestamp".equals(col.getField()) ||
                            "autoDecisionGenerated".equals(col.getField()) ||
                            "autoDecisionAccepted".equals(col.getField()) ||
                            "classifications".equals(col.getField()))) {
                // Excluded items never have any recommendation information or classifications stored so just skip them all
                // Also skipping cols autoDecisionGenerated and autoDecisionAccepted for excluded items
                val = null;
            } else {
                 val = ds.getFieldValue(col.getField());
            }

            result.put(col.getField(), val);
        }

        return result;
    }

    private Map<String, Object> compareItems(){

        if (currentCertItem == null && currentExcludedItem == null)
            return null;

        Map<String, Object> selected = null;

        int comparison = performComparison(currentCertItem, currentExcludedItem);

        // Find the next item in the sort order and null
        // out the current item placeholder
        if (comparison > 0){
            selected = currentExcludedItem;
            currentExcludedItem = null;
        } else {
            selected = currentCertItem;
            currentCertItem = null;
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
    //
    //
    //
    //////////////////////////////////////////////////////////////////////

    public void setLimit(int startPage, int pageSize) {
        start = startPage;
        limit = pageSize;
    }

    public int getSizeEstimate() throws GeneralException {
        int cnt = context.countObjects(CertificationItem.class, baseOptions);

        if (exclusionsIncluded){
            cnt += context.countObjects(ArchivedCertificationItem.class, exclusionOptions);
        }

        return cnt;
    }

    public String getBaseHql() {
        return null;
    }

    public QueryOptions getBaseQueryOptions() {
        return baseOptions;
    }

    public void setMonitor(Monitor monitor) {

    }

    @Override
    public void close() {
        super.close();

        if (excludedItems != null) {
            excludedItems.close();
        }

        if (certItems != null) {
            certItems.close();
        }
    }


}
