/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.api.DynamicValuator;
import sailpoint.api.Formicator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Argument;
import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.LiveReport;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Signature;
import sailpoint.object.Sort;
import sailpoint.object.TaskDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.messages.MessageKeys;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ReportInitializer {

    private SailPointContext context;
    private Locale locale;
    private TimeZone timezone;

    public ReportInitializer(SailPointContext context, Locale locale, TimeZone timezone) {
        this.context = context;
        this.locale = locale;
        this.timezone = timezone;
    }

    public LiveReport initReport(TaskDefinition definition) throws GeneralException{

        if (definition == null)
            return null;

        Attributes<String, Object> attrs = definition.getEffectiveArguments();
        if (attrs != null){
            LiveReport originalReport = (LiveReport)attrs.get(LiveReportExecutor.ARG_REPORT);
            if (originalReport != null){

                LiveReport reportCopy = (LiveReport)originalReport.deepCopy((XMLReferenceResolver)context);

                if (reportCopy.getForm() != null){
                    Form formCopy = (Form)reportCopy.getForm().deepCopy((XMLReferenceResolver)context);
                    reportCopy.setForm(formCopy);
                }

                runReportInitializer(reportCopy, definition);

                String groupBy = (String)definition.getArgument(LiveReportExecutor.ARG_GROUP_BY);
                if (groupBy != null){
                    reportCopy.getDataSource().setGridGrouping(groupBy);
                }

                String sortBy = (String)definition.getArgument(LiveReportExecutor.ARG_SORT_BY);
                if (sortBy != null){
                    boolean sortAsc =  Util.otob(definition.getArgument(LiveReportExecutor.ARG_SORT_BY_ASC));
                    reportCopy.getDataSource().setSortOrder(new ArrayList<Sort>());
                    reportCopy.getDataSource().addSortOrder(new Sort(sortBy, sortAsc), true);
                }

                List<String> columnOrder =
                        Util.csvToList((String)definition.getArgument(LiveReportExecutor.ARG_COLUMN_ORDER));

                initForm(definition, reportCopy);

                // Add any extended columns
                if (reportCopy.hasExtendedColumns()){
                    List<ReportColumnConfig> newCols = getUpdatedColumns(reportCopy, reportCopy.getForm());
                    reportCopy.mergeExtendedColumns(newCols);
                }

                if (columnOrder != null && !columnOrder.isEmpty()){
                    reportCopy.reorderGridColumns(columnOrder);
                }

                resetColumnFields(reportCopy, reportCopy.getForm());

                return reportCopy;
            }
        }

        return null;
    }

    public LiveReport handleUpdatedForm(LiveReport report, Form submittedForm) throws GeneralException{

        report.setForm(submittedForm);

        List<String> visibleColumns = (List<String>)submittedForm.getField("columns").getValue();

        // Add any extended columns
        if (report.hasExtendedColumns()){
            List<ReportColumnConfig> newCols = getUpdatedColumns(report, submittedForm);
            report.mergeExtendedColumns(newCols);
        }

        // Mark hidden columns as such and re-order the form fields based on
        // user input
        report.reorderGridColumns(visibleColumns);

        ColumnCollection columns = new ColumnCollection(report.getGridColumns(), locale, timezone);

        Field sort = report.getForm().getField("sort");
        sort.addAttribute("columns", columns.getSortableColumns());

        Field groupBy = report.getForm().getField("gridGrouping");
        groupBy.setAllowedValues((List)columns.getSortableColumns());

        Field columnsField = report.getForm().getField("columns");
        columnsField.setAllowedValues((List)columns.getColumns());
        columnsField.setValue(columns.getActiveColumns());

        return report;
    }

    public TaskDefinition copyFormToDefinition(TaskDefinition def, Form submittedForm) {
        return copyFormToDefinition(def, submittedForm, null);
    }

    public TaskDefinition copyFormToDefinition(TaskDefinition def, Form submittedForm, Map<String, Object> args) {

        String name = submittedForm.getField("name").getValue() != null ?
                submittedForm.getField("name").getValue().toString() : null;
        def.setName(name);
        def.setDescription((String)submittedForm.getField("description").getValue());
        def.setConcurrent(Util.otob(submittedForm.getField("allowConcurrency").getValue()));
        def.setHost(Util.otoa(submittedForm.getField("taskHost").getValue()));
        def.setResultAction(
                TaskDefinition.ResultAction.valueOf(submittedForm.getField("resultActions").getValue().toString()));

        // Set arg for sending empty reports
        boolean dontEmailEmptyReport = Util.otob(submittedForm.getField("dontEmailEmptyReport").getValue());
        def.setArgument(LiveReportExecutor.ARG_DONT_EMAIL_EMPTY_REPORT, dontEmailEmptyReport);

        List<String> emailFileFormat =
                (List<String>)submittedForm.getField(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT).getValue();
        if (!Util.isEmpty(emailFileFormat)){
            def.setArgument(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT, emailFileFormat);
        } else {
            def.getArguments().remove(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT);
        }

        Object disableHeader = submittedForm.getField(LiveReportExecutor.ARG_DISABLE_HEADER) != null ?
                submittedForm.getField(LiveReportExecutor.ARG_DISABLE_HEADER).getValue() : null;
        if (disableHeader != null){
            def.setArgument(LiveReportExecutor.ARG_DISABLE_HEADER, disableHeader.toString());
        } else {
            def.getArguments().remove(LiveReportExecutor.ARG_DISABLE_HEADER);
        }

        Object disableSummary = submittedForm.getField(LiveReportExecutor.ARG_DISABLE_SUMMARY) != null ?
                submittedForm.getField(LiveReportExecutor.ARG_DISABLE_SUMMARY).getValue() : null;
        if (disableSummary != null){
            def.setArgument(LiveReportExecutor.ARG_DISABLE_SUMMARY, disableSummary.toString());
        } else {
            def.getArguments().remove(LiveReportExecutor.ARG_DISABLE_SUMMARY);
        }

        Object disableDetail = submittedForm.getField(LiveReportExecutor.ARG_DISABLE_DETAIL) != null ?
                submittedForm.getField(LiveReportExecutor.ARG_DISABLE_DETAIL).getValue() : null;
        if (disableDetail != null){
            def.setArgument(LiveReportExecutor.ARG_DISABLE_DETAIL, disableDetail.toString());
        } else {
            def.getArguments().remove(LiveReportExecutor.ARG_DISABLE_DETAIL);
        }

        Object includeCsvHeader = submittedForm.getField(LiveReportExecutor.ARG_ENABLE_CSV_HEADER) != null ?
              submittedForm.getField(LiveReportExecutor.ARG_ENABLE_CSV_HEADER).getValue() : null;
        if (includeCsvHeader != null){
            def.setArgument(LiveReportExecutor.ARG_ENABLE_CSV_HEADER, includeCsvHeader.toString());
        } else {
            def.getArguments().remove(LiveReportExecutor.ARG_ENABLE_CSV_HEADER);
        }
        
        if (submittedForm.getField("emailIdentities").getValue() != null){
            def.setArgument(JasperExecutor.OP_EMAIL_IDENTITIES, submittedForm.getField("emailIdentities").getValue());
        } else if (def.getArguments().containsKey(JasperExecutor.OP_EMAIL_IDENTITIES)) {
            def.getArguments().remove(JasperExecutor.OP_EMAIL_IDENTITIES);
        }

        if (submittedForm.getField("resultScope") != null && submittedForm.getField("resultScope").getValue() != null){
            def.setArgument(TaskDefinition.TASK_DEFINITION_RESULT_SCOPE, submittedForm.getField("resultScope").getValue());
        } else {
            def.getArguments().remove(TaskDefinition.TASK_DEFINITION_RESULT_SCOPE);
        }

        if (submittedForm.getField("sort").getValue() != null){
            Map sortBy = (Map)submittedForm.getField("sort").getValue();
            String col = (String)sortBy.get("field");
            boolean sortAsc =  Util.otob(sortBy.get("ascending"));
            if (col != null && !"".equals(col)){
                setSort(def, col, sortAsc);
            } else if (def.getArguments() != null){
                removeSort(def);
            }
        }

        if (submittedForm.getField("gridGrouping").getValue() != null){
            Field groupByField = submittedForm.getField("gridGrouping");
            if ( groupByField != null ) {
                String groupBy = (String)groupByField.getValue();
                def.setArgument(LiveReportExecutor.ARG_GROUP_BY, groupBy);
            }
        } else if (def.getArguments().containsKey(LiveReportExecutor.ARG_GROUP_BY)){
            def.getArguments().remove(LiveReportExecutor.ARG_GROUP_BY);
        }

        if (submittedForm.getField("columns").getValue() != null){
            List<String> visibleColumns = (List<String>)submittedForm.getField("columns").getValue();
            if (!visibleColumns.isEmpty()){
                def.setArgument(LiveReportExecutor.ARG_COLUMN_ORDER, Util.listToCsv(visibleColumns));
            } else if (def.getArguments().containsKey(LiveReportExecutor.ARG_COLUMN_ORDER)){
                def.getArguments().remove(LiveReportExecutor.ARG_COLUMN_ORDER);
            }
        }

        // Copy argument values from the form onto the task def
        Signature sig = def.getEffectiveSignature();

        // Boolean to keep track of whether the arguments in this report include a Date arg.
        // We need this to normalize the date object back from 12 noon GMT during searching.
        // see bug #27954
        boolean hasDateArguments = false;

        if (sig != null){
            for (Argument arg : sig.getArguments()) {
                Object value = null;
                if (submittedForm.getField(arg.getName()) != null) {
                    value = submittedForm.getField(arg.getName()).getValue();
                } else if (args != null && args.containsKey(arg.getName())) {
                    value = args.get(arg.getName());
                }
                if (value != null) {
                    def.getArguments().put(arg.getName(), value);
                    if (value instanceof Date)
                        hasDateArguments = true;
                } else {
                    def.getArguments().remove(arg.getName());
                }
            }

            // Save the client timezone information to the report TaskDefinition
            // if Date arguments are included.  This is so that the report infosee bug #27954
            if (hasDateArguments)
                def.getArguments().put("reportCreateTimeZone", this.timezone.getID());
        }

        return def;
    }

    public static void setSort(TaskDefinition def, String column, boolean isAscending){
        def.setArgument(LiveReportExecutor.ARG_SORT_BY, column);
        def.setArgument(LiveReportExecutor.ARG_SORT_BY_ASC , isAscending);
    }

    public static void removeSort(TaskDefinition def){
        def.getArguments().remove(LiveReportExecutor.ARG_SORT_BY);
        def.getArguments().remove(LiveReportExecutor.ARG_SORT_BY_ASC);
    }

    /**
     * Runs the validation script for a given report.
     * 
     * @param report report to run the validation script for
     * @return a list of messages, or null, returned from the script
     */
    public List<Message> runValidation(LiveReport report) throws GeneralException {
        if (null != report && report.hasValidation()) {
            DynamicValuator valuator = new DynamicValuator(report.getValidationDef());
    
            Map<String, Object> validationArgs = new HashMap<String, Object>();
            validationArgs.put("report", this);
            validationArgs.put("form", report.getForm());
            validationArgs.put("locale", locale);
    
            return (List<Message>)valuator.evaluate(context, validationArgs);
        }
        return null;
    }

    private void runReportInitializer(LiveReport report, TaskDefinition def) throws GeneralException{

        if (!report.hasInitializer())
            return;

        DynamicValuator valuator = new DynamicValuator(report.getInitializerDef());

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("context", context);
        args.put("taskDefinition", def);
        args.put("report", report);
        args.put("locale", locale);

        valuator.evaluate(context, args) ;
    }

    private List<ReportColumnConfig> getUpdatedColumns(LiveReport report, Form form) throws GeneralException{

        if (!report.hasExtendedColumns())
            return null;

        DynamicValuator valuator = new DynamicValuator(report.getExtendedColumnsDef());

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("context", context);
        args.put("form", form);
        args.put("report", report);
        List<ReportColumnConfig> newCols = (List<ReportColumnConfig>)valuator.evaluate(context, args) ;

        return newCols;
    }

    private void initForm(TaskDefinition def, LiveReport report) throws GeneralException {

        Formicator formicator = new Formicator(context);
        Form skeletonFormOriginal = context.getObjectByName(Form.class, "Report Form Skeleton");

        Form skeletonForm = (Form)skeletonFormOriginal.deepCopy((XMLReferenceResolver)context);

        Map<String, Object> fieldValues = new HashMap<String, Object>();

         // Populate standard form field values
        if (!def.isTemplate())
            fieldValues.put("name", def.getName());
        fieldValues.put("description", def.getDescription());
        fieldValues.put("allowConcurrency", def.isConcurrent());
        fieldValues.put("taskHost", def.getHost());
        fieldValues.put("assignedScope", def.getAssignedScope() != null ? def.getAssignedScope().getId() : null);
        fieldValues.put("resultActions", def.getResultAction() != null ?
                def.getResultAction().toString() : TaskDefinition.ResultAction.RenameNew.toString());
        fieldValues.put(JasperExecutor.OP_EMAIL_IDENTITIES, def.getArgument(JasperExecutor.OP_EMAIL_IDENTITIES));
        fieldValues.put("signoffRequired", def.getSignoffConfig() != null &&
                !def.getSignoffConfig().isDisabled());

        fieldValues.put(LiveReportExecutor.ARG_DISABLE_HEADER,
                Util.otob(def.getArgument(LiveReportExecutor.ARG_DISABLE_HEADER)));
        fieldValues.put(LiveReportExecutor.ARG_DISABLE_SUMMARY,
                Util.otob(def.getArgument(LiveReportExecutor.ARG_DISABLE_SUMMARY)));
        fieldValues.put(LiveReportExecutor.ARG_DISABLE_DETAIL,
                Util.otob(def.getArgument(LiveReportExecutor.ARG_DISABLE_DETAIL)));
        fieldValues.put(LiveReportExecutor.ARG_ENABLE_CSV_HEADER,
                Util.otob(def.getArgument(LiveReportExecutor.ARG_ENABLE_CSV_HEADER)));
        fieldValues.put(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT,
                        def.getArgument(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT));

        fieldValues.put(LiveReportExecutor.ARG_DONT_EMAIL_EMPTY_REPORT,
                def.getArgument(LiveReportExecutor.ARG_DONT_EMAIL_EMPTY_REPORT));

        List<Field> argFields = new ArrayList<Field>();

        Form reportForm = report.getForm();
        if (reportForm == null){
            argFields = buildArgumentFields(def, skeletonForm);
        } else {
            for(Form.Section section : reportForm.getSections()){
                skeletonForm.add(section);
            }
        }

        // Get submitted field values from arguments list and
        // overwrite that with any values we've set for standard
        // report properties
        Map<String, Object> formValues = getFieldValues(def);
        formValues.putAll(fieldValues);

        formicator.assemble(skeletonForm, argFields);
        Form form = formicator.expand(skeletonForm, formValues);

        // Move the layout section to the end of the form wizard
        // so that it can be updated by anything the user might add
        Form.Section layoutSection = form.getSection("layout");
        form.remove(layoutSection);
        form.add(layoutSection);

        Field scopeField = form.getField("resultScope");
        if (scopeField != null){
            scopeField.setValue(def.getArgument(TaskDefinition.TASK_DEFINITION_RESULT_SCOPE));
        }

        resetColumnFields(report, form);

        // Perform any localizations
        Field resultActions = form.getField("resultActions");
        localizeLabels(resultActions);

        Field escalationStyle = form.getField("signoffEscalationStyle");
        localizeLabels(escalationStyle);

        for(Form.Section section : form.getSections()){
            if (section.getAttributes() != null && section.getAttributes().containsKey("subtitle")){
                String subtitle = (String)section.getAttributes().get("subtitle");
                String localized = Internationalizer.getMessage(subtitle, locale);
                if (localized != null)
                    section.getAttributes().put("subtitle", localized);
            }
        }

        report.setForm(form);
    }

    private void resetColumnFields(LiveReport report, Form form){
        ColumnCollection columns = new ColumnCollection(report.getGridColumns(), locale, timezone);

        Field sort = form.getField("sort");
        sort.addAttribute("columns", columns.getSortableColumns());

        if (report.getDataSource().hasSortOrder()){
            sort.setValue(report.getDataSource().getSortOrder().get(0));
        } else {
            sort.setValue(null);
        }

        Field groupBy = form.getField("gridGrouping");
        groupBy.setAllowedValues((List)columns.getSortableColumns());

        if (report.getDataSource().getGridGrouping() != null){
            groupBy.setValue(report.getDataSource().getGridGrouping());
        } else {
            groupBy.setValue(null);
        }

        Field columnsField = form.getField("columns");
        columnsField.setAllowedValues((List)columns.getColumns());
        columnsField.setValue(columns.getActiveColumns());
    }

    /**
     * Build a list of fields based on the task arguments. This list is used if the
     * form creator did not specify a custom form. Fields that
     * are already included in the skeleton form arg not included;
     */
    private List<Field> buildArgumentFields(TaskDefinition definition, Form skeletonForm){
        List<Field> argFields = new ArrayList<Field>();
        Signature sig = definition.getEffectiveSignature();
        Attributes<String, Object> args = definition.getEffectiveArguments();
        if (sig != null) {
            for (Argument sigArg : sig.getArguments()) {
                if (skeletonForm.getField(sigArg.getName()) == null){
                    Field argField = new Field();
                    argField.setName(sigArg.getName());
                    String displayName = sigArg.getDisplayableName() != null ? sigArg.getDisplayLabel() : sigArg.getDescription();
                    argField.setDisplayName(getMessage(displayName));
                    argField.setType(sigArg.getType());
                    argField.setMulti(sigArg.isMulti());
                    if (sigArg.getDescription() != null){
                        Message help = Message.localize(sigArg.getDescription());
                        argField.setHelpKey(help.getLocalizedMessage(locale, timezone));
                    }
                    // jsl - started seeing these come back as empty string, use nullify
                    String section = Util.trimnull(sigArg.getSection());
                    if (section == null)  section = MessageKeys.REPORT_OPTIONS;
                    argField.setSection(section);
                    argField.setValue("ref:" + argField.getName());

                    if (sigArg.getFilterString() != null){
                        argField.setFilterString(sigArg.getFilterString());
                    }

                    argFields.add(argField);
                }
            }
        }
        return argFields;
    }


    private static Map<String, Object> getFieldValues(TaskDefinition def){
        Map<String, Object> values = new HashMap<String, Object>();
        Signature sig = def.getEffectiveSignature();
        Attributes<String, Object> args = def.getEffectiveArguments();
        if (sig != null) {
            for (Argument sigArg : sig.getArguments()) {
                if (args.containsKey(sigArg.getName()))
                    values.put(sigArg.getName(), args.get(sigArg.getName()));
            }
        }

        return values;
    }

    private String getMessage(String key){
        if (key != null){
            String val = Internationalizer.getMessage(key, locale);
            if (val != null)
                return val;
        }

        return key;
    }

    private void localizeLabels(Field field){
        if (field != null && field.getAllowedValuesDefinition() != null){
            List allowedValues = (List)field.getAllowedValuesDefinition().getValue();
            if (allowedValues != null){
                for(Object val : allowedValues){
                    List<String> valList = (List<String>)val;
                    if (valList.size() == 2 && valList.get(1) != null){
                        String label = valList.get(1);
                        String localized = Internationalizer.getMessage(label, locale);
                        if (localized != null)
                            valList.set(1, localized);
                    }
                }
            }
        }
    }

    public static final class ColumnCollection{

        private Locale locale;
        private TimeZone timezone;
        private List<ReportColumnConfig> columns;

        public ColumnCollection(List<ReportColumnConfig> columns, Locale locale, TimeZone timezone) {
            this.columns = columns;
            this.locale = locale;
            this.timezone = timezone;
        }

        public void addColumn(ReportColumnConfig column){
            if (columns == null)
                columns = new ArrayList<ReportColumnConfig>();
            columns.add(column);
        }

        public List<List<String>> getColumns(){
            List<List<String>> columnValues = new ArrayList<List<String>>();
            if (columns != null){
                for(ReportColumnConfig column : columns){
                    List<String> columnValue = new ArrayList<String>();
                    String description = Message.info(column.getHeader()).getLocalizedMessage(locale,
                            timezone);
                    columnValue.add(column.getField());
                    columnValue.add(description);
                    columnValues.add(columnValue);
                }
            }
            return columnValues;
        }

        public List<List<String>> getSortableColumns(){
            List<List<String>> columnValues = new ArrayList<List<String>>();

            // add a no selection option, so they can clear the list
            columnValues.add(Arrays.asList("",
                    Internationalizer.getMessage(MessageKeys.REPT_ARG_OPTION_NO_SELECTION, locale)));

            if (columns != null){
                for(ReportColumnConfig column : columns){
                    if (column.isSortable()){
                        List<String> columnValue = new ArrayList<String>();
                        String description = Message.info(column.getHeader()).getLocalizedMessage(locale,
                                timezone);
                        columnValue.add(column.getField());
                        columnValue.add(description);
                        columnValues.add(columnValue);
                    }
                }
            }
            return columnValues;
        }

        public List<String> getActiveColumns(){
            List<String> columnsNames = new ArrayList<String>();
            if (columns != null){
                for(ReportColumnConfig column : columns){
                    if (!column.isHidden()){
                        columnsNames.add(column.getField());
                    }
                }
            }
            return columnsNames;
        }
    }


}
