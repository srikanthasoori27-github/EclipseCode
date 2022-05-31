/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignSubreport;
import sailpoint.api.DynamicValuator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PropertyInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.ReportDataSource;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.object.Sort;
import sailpoint.object.TaskDefinition;
import sailpoint.reporting.datasource.HqlDataSource;
import sailpoint.reporting.datasource.JavaDataSource;
import sailpoint.reporting.datasource.LiveReportDataSource;
import sailpoint.reporting.datasource.ProjectionDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ReportHelper {

    private static final Log log = LogFactory.getLog(ReportHelper.class);

    // CSV list of columns names which should be added to
    // the query for testing purposes. This is to ensure that
    // the report results are always sorted during testing.
    public static String ARG_TEST_SORT = "TEST_sortOrder";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private Locale locale;
    private TimeZone timezone;
    private SailPointContext context;

    //////////////////////////////////////////////////////////////////////
    //
    // Public Methods and Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ReportHelper(SailPointContext context, Locale locale, TimeZone timezone) {
        this.context = context;
        this.locale = locale;
        this.timezone = timezone;
    }

    public LiveReportDataSource initDataSource(LiveReport report, Attributes<String, Object> args)
            throws GeneralException {
        return this.initDataSource(report, args, null, null);
    }



    public LiveReportDataSource initDataSource(LiveReport report, Attributes<String, Object> args,
                                               Integer start, Integer limit) throws GeneralException {

        LiveReportDataSource reportDs = null;

        ReportDataSource ds = report.getDataSource();

        if (ReportDataSource.DataSourceType.Hql.equals(ds.getType())){

            Class clazz = ds.getObjectClass();

            QueryOptions ops = initQueryOptions(report, ds, args, start, limit);

            String query = initQuery(report, args);

            if (log.isDebugEnabled()){
                log.debug("Initial HQL report query was:" + query);
            }

            Map<String, Object> queryArgs = getHqlQueryArgs(ds, args);

            if (log.isDebugEnabled()){
                String argStr = "";
                if (queryArgs != null){
                    for(String key : queryArgs.keySet()){
                        String val = queryArgs.get(key) != null ? queryArgs.get(key).toString() : "NULL";
                        argStr += argStr.length() > 0 ? ", " : " " + key + ":" + val;
                    }
                }
                log.debug("Initial HQL report arguments were:" + argStr);
            }

            reportDs = new HqlDataSource(report, clazz, query, ops, report.getGridColumns(), queryArgs,
                                    locale, timezone);

        } else if (ReportDataSource.DataSourceType.Java.equals(ds.getType())){
            try {
                Class dsClass = Class.forName(ds.getDataSourceClass());
                JavaDataSource javaDataSource = (JavaDataSource)dsClass.newInstance();
                javaDataSource.initialize(context, report, args, ds.getGroupBy(), ds.getSortOrder());
                if (start != null){
                    javaDataSource.setLimit(start, limit != null ? limit : 20);
                }
                reportDs = javaDataSource;
            } catch (Throwable e) {
               throw new GeneralException(e);
            }
        } else if (ReportDataSource.DataSourceType.Filter.equals(ds.getType())){
            QueryOptions ops = getFilterQueryOps(report, args, start, limit);
            reportDs = new ProjectionDataSource(ds.getObjectClass(), ops ,report.getGridColumns(), locale,timezone);
        }

        return reportDs;
    }

    public QueryOptions getFilterQueryOps(LiveReport report, Attributes<String, Object> args) throws GeneralException{
        return this.getFilterQueryOps(report, args, null, null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Private Methods
    //
    //////////////////////////////////////////////////////////////////////

    private QueryOptions initQueryOptions(LiveReport report, ReportDataSource ds, Attributes<String, Object> args,
                                         Integer start, Integer limit) throws GeneralException{
        QueryOptions ops = new QueryOptions();

        if(ReportDataSource.DataSourceType.Filter.equals(ds.getType())){
            String query = initQuery(report, args);

            if (query != null){
                Filter filter = Filter.compile(ds.getQuery());
                ops.add(filter);
            }
        }

        if (start != null){
            ops.setFirstRow(start);
            ops.setResultLimit(limit != null ? limit : 20);
        }

        return ops;
    }

    private String initQuery(LiveReport report, Attributes<String, Object> args) throws GeneralException{

        ReportDataSource ds = report.getDataSource();

        String query = ds.getQuery() != null && ds.getQuery().trim().length() > 0 ? ds.getQuery().trim() :  null;
        if (query != null && ds.getQueryScript() != null){
            Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("args", args);
            arguments.put("query", query);
            query = (String)context.runScript(ds.getQueryScript(), arguments);
        }

        return query;
    }

    public Map<String, Object> getHqlQueryArgs(ReportDataSource ds, Attributes<String, Object> args)
            throws GeneralException{
        Map<String,Object> queryArgs = new HashMap<String, Object>();
        if (args != null){
            for(String arg : args.keySet()){
                ReportDataSource.Parameter param = ds.getParameter(arg);
                if (param != null){

                    Object val = parseValue(param, ds.getObjectClass(), args.get(arg));

                    if (val != null && !param.isExcludeHqlParameter())
                        queryArgs.put(arg, val);
                }
            }
        }

        return queryArgs;
    }

    private QueryOptions getFilterQueryOps(LiveReport report, Attributes<String, Object> args,
                                           Integer start, Integer limit) throws GeneralException{

        ReportDataSource ds = report.getDataSource();

        Class<? extends SailPointObject> datasourceClass = ds.getObjectClass();

        QueryOptions ops = initQueryOptions(report, ds, args, start, limit);

        if (ds.getJoins() != null && !ds.getJoins().isEmpty()){

            // Joins must be the first filter items.
            List<Filter> currentFilters = ops.getFilters();
            ops.setFilters(new ArrayList<Filter>());

            for(ReportDataSource.Join join : ds.getJoins()){
                ops.add(Filter.join(join.getProperty(), join.getJoinProperty()));
            }

            if (currentFilters != null){
                for(Filter f : currentFilters){
                    ops.add(f);
                }
            }

            ops.setDistinct(true);
        }

        if (ds.getQueryParameters() != null){
            for(ReportDataSource.Parameter param : ds.getQueryParameters()){
                ops = evalParameter(datasourceClass, param, ops, args);
            }
        }

        if (ds.getGridGrouping() != null){
            String fieldName = ds.getGridGrouping();
            ReportColumnConfig column = report.getGridColumnByFieldName(fieldName);
            ops.addOrdering(column.getProperty(), true);
        }

        if (ds.getSortOrder() != null){
            for(Sort s : ds.getSortOrder()){
                String fieldName = s.getField();
                ReportColumnConfig column = report.getGridColumnByFieldName(fieldName);
                if (column.getProperty() == null){
                    // In this case the sort field is some kind of derived property,
                    // not an actual column, so insert the field name and trust the
                    // datasource to handle it.
                    ops.addOrdering(column.getField(), s.isAscending());
                } else {
                    for(String prop : column.getSortProperties()){
                        ops.addOrdering(prop.trim(), s.isAscending());
                    }
                }
            }
        }

        // Used for unit tests
        if (ds.getSortOrder() == null && args.containsKey("TEST_sortOrder") && args.get("TEST_sortOrder") != null){
            List<String> sortColumns = Util.csvToList((String) args.get("TEST_sortOrder"));
            for(String col : sortColumns)
                ops.addOrdering(col, false);
        }

        if (ops.getOrderings() == null || ops.getOrderings().isEmpty()){
            for(String sort : report.getDataSource().getDefaultSortList()){
                ops.addOrdering(sort, true);
            }
        }

        // We always need at least one sort on a non-null
        // property or we will have issues sorting
        ops.addOrdering("id", true);

        if (ds.hasCustomOptionsHandler()){
            Map<String, Object> scriptArgs = new HashMap<String, Object>();
            scriptArgs.put("context", context);
            scriptArgs.put("options", ops);
            scriptArgs.put("args", args);
            if (ds.getOptionsRule() != null){
                ops = (QueryOptions)context.runRule(ds.getOptionsRule(), scriptArgs);
            } else if (ds.getOptionsScript() != null){
                ops = (QueryOptions)context.runScript(ds.getOptionsScript(), scriptArgs);
            }
        }

        return ops;
    }

    /**
     * Returns the header for the LiveReport.
     * The header will include Creator, Creation Date and other user inputs relevant to the report.
     * 
     * @param report The LiveReport object
     * @param definition TaskDefinition
     * @param reportDs The LiveReportDataSource object
     * @param args Map of inputs
     * @return Header of the LiveReport
     * @throws GeneralException
     */
    public LiveReport.LiveReportSummary initHeader(LiveReport report, TaskDefinition definition, 
                                                   Attributes<String, Object> args) throws GeneralException{

       LiveReport.LiveReportSummary header = new LiveReport.LiveReportSummary();

       header.setTitle(report.getTitle());

       List<LiveReport.LiveReportSummaryValue> values = new ArrayList<>();

       LiveReport.LiveReportSummaryValue creator = new LiveReport.LiveReportSummaryValue();
       creator.setName("creator");
       creator.setLabel(Internationalizer.getMessage("rept_header_creator", locale));
       String creatorName = args.getString("launcher");
       //preview does not have launcher
       if (Util.isNothing(creatorName)) {
           creatorName = this.context.getUserName();
       }
       creator.setValue(creatorName);
       values.add(creator);

       LiveReport.LiveReportSummaryValue date = new LiveReport.LiveReportSummaryValue();
       date.setName("date");
       date.setLabel(Internationalizer.getMessage("rept_header_creation_date", locale));
       date.setValue(Internationalizer.getLocalizedDate(new Date(),  locale, null));
       values.add(date);

       ReportDataSource ds = report.getDataSource();
       //we need to get the original form for this report.
       Form form = null;
       Attributes<String, Object> attrs = definition.getEffectiveArguments();
       if (attrs != null){
           LiveReport originalReport = (LiveReport)attrs.get(LiveReportExecutor.ARG_REPORT);
           if (originalReport != null){
               form = originalReport.getForm();
           }
       }

       if (ds != null && form != null) {
           for(Field field : Util.safeIterable(form.getEntireFields())) {
               String name = field.getName();
               Object fieldValue = Util.get(args, name);

               if (fieldValue != null && !"null".equalsIgnoreCase(fieldValue.toString())) {
                   LiveReport.LiveReportSummaryValue data = new LiveReport.LiveReportSummaryValue();
                   data.setName(name);
                   data.setLabel(field.getDisplayableName(locale));

                   Object valueToDisplay = getFieldValue(field, fieldValue);
                   
                   data.setValue(valueToDisplay);

                   values.add(data);
               }
           }
       }

       header.setValues(values);
       report.setReportHeader(header);
       return header;
   }

    public String getDateRangeString(Map mapValue) {
        Long start = (Long)mapValue.get("start");
        Long end = (Long)mapValue.get("end");
        Date startDate = start != null ? new Date(start) : null;
        Date endDate = end != null ? new Date(end) : null;
        
        return getDateRangeString(startDate, endDate);
    }        

    public String getDateRangeString(Date startDate, Date endDate) {
        StringBuffer buf = new StringBuffer();
        if (startDate != null) {
            buf.append(Internationalizer.getMessage("start", locale));
            buf.append(": ");
            buf.append(Internationalizer.getLocalizedDate(startDate, true, locale, null));
            if (endDate != null) {
                buf.append(",  ");
            }
        }
        if (endDate != null) {
            buf.append(Internationalizer.getMessage("end", locale));
            buf.append(": ");
            buf.append(Internationalizer.getLocalizedDate(endDate, true, locale, null));
        }
        return buf.toString();
    }
    
    //Retrieves the display text for the field based on its value. 
    public Object getFieldValue(Field field, Object fieldValue) throws GeneralException {
        if (fieldValue == null) return null;
        
        Class fieldTypeClass = field.getTypeClass();
        String fieldType = field.getType();
        if (fieldTypeClass != null) {
            Object convertedFieldValue = ObjectUtil.convertIdsToNames(context, fieldTypeClass, fieldValue);
            if (convertedFieldValue instanceof List) {
                return Util.listToCsv((List)convertedFieldValue);
            } else {
                return convertedFieldValue;
            }
        } else {
            if ("date".equalsIgnoreCase(fieldType)) {
                Date dateValue = Util.getDate(fieldValue);
                if (dateValue != null) {
                    return Internationalizer.getLocalizedDate(dateValue, true, locale, null);
                } else {
                    return fieldValue;
                }
            } else if ("daterange".equalsIgnoreCase(Util.getString(field.getAttributes(), "xtype"))) {
                Map mapValue = (Map) fieldValue;
                return getDateRangeString(mapValue);
            } else if (fieldValue instanceof List) {
                List<String> allowedValuesDisplay = new ArrayList<>();
                List<Object> allowedValues = getAllowedValues(field);
                for (Object value : ((List)fieldValue)) {
                    String display = getAllowedValueDisplay(allowedValues, value);
                    allowedValuesDisplay.add(display);
                }
                return Util.listToCsv(allowedValuesDisplay);
            } else {
                return getAllowedValueDisplay(getAllowedValues(field), fieldValue);
            }
        }
    }

    //Retrieves the allowed value display text for the corresponding field value.
    //If the field has no allowed values, then the original field value is returned.
    private String getAllowedValueDisplay(List allowedValues, Object fieldValue) throws GeneralException {
        String display = fieldValue == null ? null : fieldValue.toString();

        for (Object item : Util.safeIterable(allowedValues)) {
            if (item instanceof List) {
                List av = (List) item;
                if (av.size() == 2 && fieldValue.equals(av.get(0))) {
                    display = Internationalizer.getMessage((String) av.get(1), locale);
                    break;
                }
            }
        }
        return display;
    }

    //Retrieves allowed value list from the field.
    //If  it is null, then evaluate Allowed Values Definition.
    private List<Object> getAllowedValues(Field field) throws GeneralException {
        List<Object> allowedValues = field.getAllowedValues();

        if (Util.isEmpty(allowedValues) && field.getAllowedValuesDefinition() != null) {
            // get allowed values
            DynamicValue dv = field.getAllowedValuesDefinition();
            DynamicValuator valuator = new DynamicValuator(dv);
            Object allowed = valuator.evaluate(context, null);
        
            if (allowed != null) {
                allowedValues = new ArrayList<Object>();
                if (Collection.class.isAssignableFrom(allowed.getClass()))
                    allowedValues.addAll((Collection)allowed);
                else
                    allowedValues.add(allowed);
            }
        }
        return allowedValues;
    }

    public LiveReport.LiveReportSummary initSummary(LiveReport report, LiveReportDataSource reportDs,
                                                    Attributes<String, Object> args) throws GeneralException{

        LiveReport.LiveReportSummary summary = null;

        if (report.getReportSummary() != null){

            summary = (LiveReport.LiveReportSummary)report.getReportSummary().deepCopy(context);

            if (summary.getTitle() != null){
                String localizedTitle = Internationalizer.getMessage(summary.getTitle(), locale);
                summary.setTitle(localizedTitle != null ? localizedTitle : summary.getTitle());
            }

            Attributes<String, Object> summaryDsArgs  = new Attributes<String, Object>();
            summaryDsArgs.put("context", context);
            summaryDsArgs.put("reportArgs", args);
            summaryDsArgs.put("report", report);

            if (summary.getValues() != null){
                for(LiveReport.LiveReportSummaryValue value : summary.getValues()){
                    String label = Internationalizer.getMessage(value.getLabel(), locale);
                    if (label != null)
                        value.setLabel(label);
                }
            }

            String baseHql = reportDs.getBaseHql();
            QueryOptions baseOpts = new QueryOptions(reportDs.getBaseQueryOptions());

            if (baseHql != null)
                summaryDsArgs.put("baseHql", baseHql);

            if (baseOpts != null){
                baseOpts.setOrderings(new ArrayList<QueryOptions.Ordering>());
                summaryDsArgs.put("baseQueryOptions", baseOpts);
            }

            Map<String, Object> summaryData = null;
            if (summary.getDataSourceClass() != null){
                LiveReport.LiveReportSummaryDataSource ds = summary.getDataSourceInstance();
                summaryData = ds.getData(context, summaryDsArgs);
            } else if (summary.getDataSourceRule() != null){
                summaryData = (Map<String, Object>)context.runRule(summary.getDataSourceRule(),
                        summaryDsArgs);
            } else if (summary.getDataSourceScript() != null){
                Script dsScript = summary.getDataSourceScript();
                summaryData = (Map<String, Object>)context.runScript(dsScript, summaryDsArgs);
            }

            summary.setData(summaryData);
        }

        return summary;
    }

    private QueryOptions evalParameter(Class<? extends SailPointObject> objectClass, ReportDataSource.Parameter param,
                                            QueryOptions ops, Attributes<String, Object> args) throws GeneralException{

        Object val = getParameterValue(objectClass, param, args);

        DynamicValue queryDef = param.getQueryDef();
        if (queryDef != null){

            Map<String, Object> scriptArgs = new HashMap<String, Object>();
            scriptArgs.put("value", val);
            scriptArgs.put("arguments", args);
            scriptArgs.put("queryOptions", ops);
            scriptArgs.put("property", param.getProperty());

            if (param.getExternalAttributeClass() != null){
                scriptArgs.put("objectClass", param.getExternalAttributeClass());
            }

            DynamicValuator valuator = new DynamicValuator(queryDef);
            ops = (QueryOptions)valuator.evaluate(context, scriptArgs);
            if (ops == null)
                throw new RuntimeException("Parameter script for '"+param.getArgument()+
                        "' did not return query options.");
        } else if (param.getExternalAttributeClass() != null){
            Filter f = buildExternalAttrFilter(param.getExternalAttributeClass(), param.getProperty(),
                    (Map<String, Object>)val);
            if (f != null){
                ops.add(f);
                ops.setDistinct(true); // an OR query requires the results be distinct

                if (log.isDebugEnabled()){
                    log.debug("Report extended attribute argument '"+param.getArgument()+"' generated a filter '"
                            + f.toString() +"'");
                }
            }
        } else {
            Filter f = buildFilter(param, val);
            if (f != null){
                ops.add(f);

                if (log.isDebugEnabled()){
                    log.debug("Report argument '"+param.getArgument()+"' generated a filter '"+ f.toString() +"'");
                }
            }
        }

        return ops;
    }

    private ObjectAttribute getAttributeDefinition(Class<? extends SailPointObject> objectClass, String propertyName){
        ObjectConfig config = ObjectConfig.getObjectConfig(objectClass);
        if (config == null){
            throw new RuntimeException("Could not find object configuration for class '"+objectClass.getName()+"'");
        }

        ObjectAttribute attrDefinition = null;
        if (config != null) {
            if (config.getSearchableAttributes() != null){
                for (ObjectAttribute att : config.getSearchableAttributes()) {
                    if (att.getName().equals(propertyName)){
                        return att;
                    }
                }
            }

            if (attrDefinition == null && config.getMultiAttributeList() != null){
                for (ObjectAttribute att : config.getMultiAttributeList()) {
                    if (att.getName().equals(propertyName)){
                        return att;
                    }
                }
            }
        }

        return null;
    }

    private Object getParameterValue(Class<? extends SailPointObject> objectClass, ReportDataSource.Parameter param, Attributes<String, Object> args)
            throws GeneralException{

        // Get initial value
        Object val = null;
        if (param.getArgument() != null){
            val = args.get(param.getArgument());
        }

        // Check for a default value
        if (val == null){
            val = param.getDefaultValue();
        }

        // If the parameter is multi-valued, val will be a Map
        // that represents the various values and filters that
        // have been configured.  In order to create the correct
        // query options, the entire map needs to be intact, and
        // parseValue can't really process multi-valued parameters.
        // We'll continue using the val as a Map if param is multi-valued.
        if (!param.isMulti()) {
            val = parseValue(param, objectClass, val);
        }

        // if we have a valueScript, re-evaluate the parameter value
        DynamicValue valueDef = param.getValueDef();
        if (valueDef != null){
            Map<String, Object> scriptArgs = new HashMap<String, Object>();
            scriptArgs.put("value", val);
            scriptArgs.put("arguments", args);
            scriptArgs.put("context", context);

            DynamicValuator valuator = new DynamicValuator(valueDef);

            val =  valuator.evaluate(context, scriptArgs);
        }

        if (log.isDebugEnabled()){
            log.debug("Report argument parameter '"+param.getArgument()+"' evaluated to '"+ (val != null ? val.toString() : "NULL") +"'");
        }

        return val;
    }

    private Object parseValue(ReportDataSource.Parameter param,
                              Class<? extends SailPointObject> objectClass, Object rawVal)
            throws GeneralException{

        Object value = null;
        if (rawVal != null){

            if (PropertyInfo.TYPE_STRING.equals(param.getValueClass())){
                value = rawVal.toString();
            } else if (PropertyInfo.TYPE_LONG.equals(param.getValueClass())){
                value = Util.atol(rawVal.toString());
            } else if (PropertyInfo.TYPE_INT.equals(param.getValueClass())){
                value = Util.otoi(rawVal);
            } else if (PropertyInfo.TYPE_DATE.equals(param.getValueClass())){
                value = rawVal;
            } else if (PropertyInfo.TYPE_BOOLEAN.equals(param.getValueClass())){
                if (rawVal instanceof Boolean){
                    value = ((Boolean) rawVal).booleanValue();
                } else if (rawVal.toString().equalsIgnoreCase("true")) {
                    value = true;
                } else if (rawVal.toString().equalsIgnoreCase("false")) {
                    value = false;
                }
            } else if (param.isDateRange()){
                if (rawVal != null){
                    Map rangeMap = (Map)rawVal;
                    DateRange range = new DateRange(param.getProperty(), rangeMap);
                    if (!range.isEmpty()){
                        value = range;
                    }
                }
            }else if (param.getValueClass() != null) {
                //Require ID's to be passed, since name won't always be unique
                Class<? extends SailPointObject> spclass = ObjectUtil.getMajorClass(param.getValueClass());
                if (spclass != null){
                    value = context.getObjectById(spclass, rawVal.toString());
                } else {
                    try {
                        // Handle enums here since they would be a common parameter value
                        Class clazz = Class.forName(param.getValueClass());
                        if (clazz.isEnum()){
                            if (Collection.class.isAssignableFrom(rawVal.getClass())){
                                List result = new ArrayList();
                                for (Iterator iterator = ((Collection)rawVal).iterator(); iterator.hasNext(); ) {
                                    Object next =  iterator.next();
                                    result.add(Enum.valueOf(clazz, next.toString()));
                                }
                                value = !result.isEmpty() ? result : null;
                            } else {
                                Enum en = Enum.valueOf(clazz, rawVal.toString());
                                value =  en;
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Could not evaluate value class '"+param.getValueClass()+"'");
                    }
                }
            } else {
                value = rawVal;
            }
        }

        return value;
    }

    private Filter buildFilter(ReportDataSource.Parameter param, Object val) throws GeneralException{

        Filter filter = null;

        if (Filter.LogicalOperation.NOTNULL.getStringRepresentation().equals(param.getOperation())){
            return Filter.notnull(param.getProperty());
        } else if (Filter.LogicalOperation.ISNULL.getStringRepresentation().equals(param.getOperation())){
            return Filter.isnull(param.getProperty());
        } else if (Filter.LogicalOperation.ISEMPTY.getStringRepresentation().equals(param.getOperation())){
            return Filter.isempty(param.getProperty());
        } else if (val != null){
            if (FilterParameter.class.isAssignableFrom(val.getClass())){
                filter = ((FilterParameter)val).getFilter();
            } else if (param.getOperation() != null){
                Filter.LogicalOperation selectedOp = Filter.LogicalOperation.valueOf(param.getOperation().toUpperCase());
                if (selectedOp != null){

                    if (Filter.LogicalOperation.LIKE.equals(selectedOp)){
                        filter = new Filter.LeafFilter(selectedOp, param.getProperty(), val, Filter.MatchMode.START);
                    } else {
                        filter = new Filter.LeafFilter(selectedOp, param.getProperty(), val);
                    }

                    if (param.isIgnoreCase()){
                        filter = Filter.ignoreCase(filter);
                    }
                } else {
                    throw new GeneralException("Unknown filter operation '"+param.getOperation()+
                            "' was specified on property '"+param.getProperty()+"'");
                }
            } else {
                if (val instanceof List){
                    filter = createListFilter(param, (List) val);
                } else {
                    filter = Filter.eq(param.getProperty(), val);
                }

                if (param.isIgnoreCase()){
                    filter = Filter.ignoreCase(filter);
                }
            }
        }

        return filter;
    }

    /**
     * Given a list of values it will create a 'in' filter taking into account
     * the value in the list may be null also.∞
     */
    private Filter createListFilter(ReportDataSource.Parameter param, List values) {
        List inValuesList = new ArrayList();
        boolean nullPresent = false;
        for (Object value : values) {
            if (Field.NULL_CONST.equals(value)) {
                nullPresent = true;
            } else {
                inValuesList.add(value);
            }
        }
        Filter filter = null;
        if (!Util.isEmpty(inValuesList)) {
            filter = Filter.in(param.getProperty(), (List)inValuesList);
        }
        if (nullPresent) {
            if (filter == null) {
                filter = Filter.isnull(param.getProperty());
            } else {
                filter = Filter.or(filter, Filter.isnull(param.getProperty()));
            }
        }
        return filter;
    }

    public Filter buildExternalAttrFilter(String className, String property, Map<String, Object> value){

        if (value == null)
            return null;

        String operation = (String)value.get("operator");
        List<String> values = (List<String>)value.get("selections");

        Class clazz = ObjectUtil.getMajorClass(className);
        if (clazz == null){
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not evaluate value class '"+className+"'");
            }
        }

        if (!Identity.class.equals(clazz) && !Link.class.equals(clazz)) {
            throw new IllegalArgumentException("Report filters currently " +
                    "only support Link and Identity objects.");
        }

        List<Filter> filters = new ArrayList<Filter>();

        String externalTbl = Identity.class.equals(clazz) ? "IdentityExternalAttribute" : "LinkExternalAttribute";

        String extVal = externalTbl + ".value";
        String extName = externalTbl + ".attributeName";
        String extId = externalTbl +  ".objectId";

        // determine the query operation. We're expecting a value of
        // "and" or "or"
        boolean isOr = "or".equals(operation);

        // figure out the join property. Look for a dot since
        // this will indicate that the ext attr is actually
        // on a child object, like Link.identity for example.
        String joinProperty = "id";
        String attrName = property;
        if (property.indexOf(".") > -1){
            joinProperty = property.substring(0, property.indexOf(".")) + ".id";
            attrName = property.substring(property.indexOf(".") + 1);
        }

        if (property != null && values != null && !values.isEmpty()){
            if (isOr){
                filters.add(Filter.join(joinProperty, extId));
                filters.add(Filter.eq(extName, attrName));
                filters.add(Filter.in(extVal, values));
            } else {
                List<Filter> ccFilters = new ArrayList<Filter>();
                for ( int i=0; i<values.size(); i++ ) {
                    Filter filter = Filter.and(Filter.join(joinProperty, extId),
                                               Filter.eq(extName, attrName),
                                               Filter.eq(extVal, values.get(i)));
                    ccFilters.add(filter);
                }
                filters.add(Filter.collectionCondition(externalTbl, Filter.and(ccFilters)));
            }
        }

        if (filters.size() == 0){
            return null;
        } else {
            return filters.size() > 1 ? Filter.and(filters) : filters.get(0);
        }
    }

    //convert List of items to row List, each row contains 2 items. 
    public List<Map<String,String>> convertToRowList(List<Map<String,String>> list) {
        List<Map<String,String>> rowList = new ArrayList<Map<String,String>>();
        Iterator<Map<String,String>> it = list.iterator();
        while (it.hasNext()) {
            Map<String,String> row = new HashMap<String, String>();
            Map<String,String> item1 = it.next();
            row.put("label1", item1.get("label"));
            row.put("value1", item1.get("value"));
            if (it.hasNext()) {
                Map<String,String> item2 = it.next();
                row.put("label2", item2.get("label"));
                row.put("value2", item2.get("value"));
            } else {
                row.put("label2", "");
                row.put("value2", "");
            }
            rowList.add(row);
        }
        
        return rowList;
    }

    //Builds header row DS, each row has two items.
    public JRDesignDataset buildHeaderRowDataTableDS() throws JRException{
        JRDesignDataset dataset = new JRDesignDataset(false);
        dataset.setName("headerRowTableDS");

        JRDesignField label1 = new JRDesignField();
        label1.setName("label1");
        label1.setValueClass(java.lang.String.class);
        dataset.addField(label1);

        JRDesignField value1 = new JRDesignField();
        value1.setName("value1");
        value1.setValueClass(java.lang.String.class);
        dataset.addField(value1);

        JRDesignField label2 = new JRDesignField();
        label2.setName("label2");
        label2.setValueClass(java.lang.String.class);
        dataset.addField(label2);

        JRDesignField value2 = new JRDesignField();
        value2.setName("value2");
        value2.setValueClass(java.lang.String.class);
        dataset.addField(value2);

        return dataset;
    }

    //Builds Header Row subreport
    public JRDesignSubreport buildHeaderSubReport(int x, int y, int width, int height, 
             String dsExpression, String templateExpression) throws GeneralException{
        JRDesignSubreport subReport = new JRDesignSubreport(null);
        subReport.setDataSourceExpression(new JRDesignExpression(dsExpression));
        subReport.setExpression(new JRDesignExpression(templateExpression));
        subReport.setX(x);
        subReport.setY(y);
        subReport.setWidth(width);
        subReport.setHeight(height);

        return subReport;
    }



}
