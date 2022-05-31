package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Configuration object for a 'live' report. LiveReports
 * are our latest report implementation. They allow
 * for previewing of report data before the report is run.
 *
 * LiveReports always display data in a grid format. They can
 * optionally include a table of summary data, or a chart.
 *
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class LiveReport extends AbstractXmlObject{

    //////////////////////////////////////////////////////////////////////
    //
    // Argument Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Report Title
     */
    private String title;

    /**
     * List of Columns included in the report.
     */
    private List<ReportColumnConfig> gridColumns;

    /**
     * Main grid data source for this report
     */
    private ReportDataSource dataSource;

    /**
     * Definition of the optional chart for this report
     */
    private Chart chart;

    /**
     * Definition of the optional creation from for this report
     */
    private Form form;

    /**
     * Definition of the optional header table for this report
     */
    private LiveReportSummary header;

    /**
     * Definition of the optional summary table for this report
     */
    private LiveReportSummary summary;

    /**
     * Script which will customize the column list defined for this report.
     * This script will be run each time the report is rendered, so it can take
     * into account task arguments.
     */
    private Script extendedColumnScript;

    /**
     * Rule which will customize the column list defined for this report.
     * See extendedColumnScript for more detail.
     */
    private Rule extendedColumnRule;

    /**
     * A Script which can be used to perform report-specific form validation.
     * This is useful in cases where the single field validation available in
     * the form model do not meet the needs of the report.
     *
     * Arguments:
     *  report - The current report instance
     *  form - The current form instance
     *  locale - The user's locale
     */
    private Script validationScript;

    /**
     * Validation rule. See validationScript for more detail.
     */
    private Rule validationRule;

    /**
     * A Script which can be used to initialize the report. This script
     * can be used to customize the report based on the customer environment.
     * A common use for this script it to customize the available form fields
     * based on the extended attributes available in a particular environment.
     */
    private Script initializationScript;

    /**
     * Initialization rule - see initializationScript;
     */
    private Rule initializationRule;

    /**
     * True if this report is able to display a preview.
     */
    private boolean disablePreview;

    /**
     * Custom message to be displayed when viewing a report
     * with a disabled preview. This message should explain to
     * the user why preview is not available.
     */
    private String disablePreviewMessage;

    /**
     * List of arguments that were added by report
     * customization, usually by the report InitializationRule.
     * Since the available extended arguments list might change when the
     * report changes, which arguments
     * are part of the report by default, and which
     * were added through customization needs to be tracked.
     */
    private List<Argument> extendedArguments;

    public String getTitle() {
        return title;
    }

    @XMLProperty
    public void setTitle(String title) {
        this.title = title;
    }

    public List<ReportColumnConfig> getGridColumns() {
        return gridColumns;
    }

    @XMLProperty(mode=SerializationMode.LIST,xmlname = "Columns")
    public void setGridColumns(List<ReportColumnConfig> gridColumns) {
        this.gridColumns = gridColumns;
    }

    public ReportColumnConfig getGridColumnByFieldName(String field) {
        if  (gridColumns != null && field != null){
            for(ReportColumnConfig col : gridColumns){
                if (field.equals(col.getField()))
                    return col;
            }
        }
        return null;
    }

    public boolean isDisablePreview() {
        return disablePreview;
    }

    @XMLProperty
    public void setDisablePreview(boolean disablePreview) {
        this.disablePreview = disablePreview;
    }

    public String getDisablePreviewMessage() {
        return disablePreviewMessage;
    }

    @XMLProperty
    public void setDisablePreviewMessage(String disablePreviewMessage) {
        this.disablePreviewMessage = disablePreviewMessage;
    }

    public Rule getInitializationRule() {
        return initializationRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setInitializationRule(Rule initializationRule) {
        this.initializationRule = initializationRule;
    }

    public Script getInitializationScript() {
        return initializationScript;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setInitializationScript(Script initializationScript) {
        this.initializationScript = initializationScript;
    }

    public boolean hasInitializer(){
        return initializationRule != null || initializationScript != null;
    }

    public DynamicValue getInitializerDef(){
        return new DynamicValue(initializationRule, initializationScript, null);
    }

    public Rule getValidationRule() {
        return validationRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setValidationRule(Rule validationRule) {
        this.validationRule = validationRule;
    }

    public Script getValidationScript() {
        return validationScript;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setValidationScript(Script validationScript) {
        this.validationScript = validationScript;
    }

    public boolean hasValidation(){
        return validationRule != null || validationScript != null;
    }

    public DynamicValue getValidationDef(){
        return new DynamicValue(validationRule, validationScript, null);
    }

    public Script getExtendedColumnScript() {
        return extendedColumnScript;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setExtendedColumnScript(Script extendedColumnScript) {
        this.extendedColumnScript = extendedColumnScript;
    }

    public Rule getExtendedColumnRule() {
        return extendedColumnRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setExtendedColumnRule(Rule extendedColumnRule) {
        this.extendedColumnRule = extendedColumnRule;
    }

    public DynamicValue getExtendedColumnsDef(){
        return new DynamicValue(extendedColumnRule, extendedColumnScript, null);
    }

    public boolean hasExtendedColumns(){
        return extendedColumnRule != null || extendedColumnScript != null;
    }

    /**
     * Merges in the new columns list into the existing list of columns for this report.
     * @param newColumns List of new <code>ReportColumnConfig</code> to merge
     */
    public void mergeExtendedColumns(List<ReportColumnConfig> newColumns){

        for (Iterator<ReportColumnConfig> iterator = getGridColumns().iterator(); iterator.hasNext(); ) {
            ReportColumnConfig column =  iterator.next();
            if (column.isExtendedColumn()){
                if (!listContainsColumn(column, newColumns)){
                    iterator.remove();
                }
            }
        }

        if (newColumns != null){
            for(ReportColumnConfig column : newColumns){
                if (!listContainsColumn(column, getGridColumns())){
                    addGridColumn(column);
                }
            }
        }
    }

    /**
     * Reorder column list using the given list of column fields.
     * @param visibleColumns List of names matching the column field property.
     */
    public void reorderGridColumns(List<String> visibleColumns){

        List<ReportColumnConfig> newCols = new ArrayList<ReportColumnConfig>();

        // Copy over the visible columns to our new list using the new order
        if (visibleColumns != null){
            for(String columnName : visibleColumns){
                ReportColumnConfig col = this.getGridColumnByFieldName(columnName);
                if (col != null){
                    col.setHidden(false);
                    newCols.add(col);
                }
            }
        }

        // Copy over any hidden columns
        for (int i=0;i<gridColumns.size();i++){
            ReportColumnConfig col = gridColumns.get(i);
            boolean isHidden = visibleColumns == null || !visibleColumns.contains(col.getField());
            if (isHidden){
                col.setHidden(isHidden);
                newCols.add(col);
            }
        }

        gridColumns = newCols;
    }

    public ReportDataSource getDataSource() {
        return dataSource;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setDataSource(ReportDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Chart getChart() {
        return chart;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setChart(Chart chart) {
        this.chart = chart;
    }

    public boolean hasChart(){
        return this.chart != null;
    }

    public Form getForm() {
        return form;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname = "ReportForm")
    public void setForm(Form form) {
        this.form = form;
    }

    public LiveReportSummary getReportHeader() {
        return header;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setReportHeader(LiveReportSummary header) {
        this.header = header;
    }

    public boolean hasHeader(){
        return header != null;
    }

    public LiveReportSummary getReportSummary() {
        return summary;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setReportSummary(LiveReportSummary summary) {
        this.summary = summary;
    }

    public boolean hasSummary(){
        return summary != null;
    }

    public List<Argument> getExtendedArguments() {
        return extendedArguments;
    }

    public void setExtendedArguments(List<Argument> extendedArguments) {
        this.extendedArguments = extendedArguments;
    }

    public void addExtendedArgument(Argument arg){

        if (arg==null)
            return;

        if (extendedArguments == null)
            extendedArguments = new ArrayList<Argument>();
        extendedArguments.add(arg);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Private Methods
    //
    //////////////////////////////////////////////////////////////////////


    private static boolean listContainsColumn(ReportColumnConfig column, List<ReportColumnConfig> list){
        if (list != null && !list.isEmpty()){
            for(ReportColumnConfig listCol : list){
                if (listCol.getField().equals(column.getField()))
                    return true;
            }
        }

        return false;
    }

    /**
     * Removes the specified column if it is referenced in
     * the sort or grouping of the report.
     */
    private void removeColumnReferences(ReportColumnConfig column){

        Form form = this.getForm();

        Field sortBy = form.getField("sortBy");
        if (sortBy != null && sortBy.getValue() != null){
            Map sortByVals = (Map)sortBy.getValue();
            String col = (String)sortByVals.get("field");
            if (column.getField().equals(col)){
                sortBy.setValue(null);
            }
        }

        Field groupBy = form.getField("groupBy");
        if (groupBy != null && column.getField().equals(groupBy.getValue())){
            groupBy.setValue(null);
        }
    }

    private void addGridColumn(ReportColumnConfig newColumn) {

        if (newColumn == null)
            return;

        if (newColumn.getField() == null){
            throw new IllegalArgumentException("A columns was added which does not specify a field value.");
        }

        if (gridColumns == null)
            gridColumns = new ArrayList<ReportColumnConfig>();

        // dont add duplicate columns
        for(ReportColumnConfig col : gridColumns){
            if (newColumn.getField().equals(col.getField())){
                return;
            }
        }

        gridColumns.add(newColumn);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Inner Classes
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configuration object for report summary tables. This includes the list
     * of values to display, the data source and a title.
     */
    @XMLClass
    public static final class LiveReportSummary extends AbstractXmlObject{

        /**
         * The title to display above the summary table.
         */
        private String title;

        /**
         * A script which generated a Map<String, Object> of data for the
         * table. The key in the map should match the name of the
         * LiveReportSummaryValue it will populate.
         */
        private Script dataSourceScript;

        /**
         * Datasource rule. See dataSourceScript.
         */
        private Rule dataSourceRule;

        /**
         * Datasource class name. See dataSourceScript.
         */
        private String dataSourceClass;

        /**
         *  List of items that will be displayed in the summary table.
         */
        private List<LiveReportSummaryValue> values;

        public LiveReportSummary() {
        }

        /**
         * Populates the table with the data from the Map
         * generated by the datasource.
         */
        public void setData(Map<String, Object> dataSource){
            if (values != null && dataSource != null){
                for(LiveReportSummaryValue value : values){
                    Object val = dataSource.containsKey(value.getName()) ? dataSource.get(value.getName()) : null;
                    value.setValue(val);
                }
            }
        }

        public String getTitle() {
            return title;
        }

        @XMLProperty
        public void setTitle(String title) {
            this.title = title;
        }

        @JsonIgnore
        public Script getDataSourceScript() {
            return dataSourceScript;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public void setDataSourceScript(Script dataSourceScript) {
            this.dataSourceScript = dataSourceScript;
        }

        @JsonIgnore
        public Rule getDataSourceRule() {
            return dataSourceRule;
        }

        @XMLProperty(mode=SerializationMode.REFERENCE)
        public void setDataSourceRule(Rule dataSourceRule) {
            this.dataSourceRule = dataSourceRule;
        }

        @JsonIgnore
        public String getDataSourceClass() {
            return dataSourceClass;
        }

        @XMLProperty
        public void setDataSourceClass(String dataSourceClass) {
            this.dataSourceClass = dataSourceClass;
        }

        public LiveReportSummaryDataSource getDataSourceInstance() throws GeneralException {
            LiveReportSummaryDataSource instance = null;
            if (this.getDataSourceClass() != null){
                try {
                    Class dataSoureClass = Class.forName(this.getDataSourceClass());
                    instance = (LiveReportSummaryDataSource)dataSoureClass.newInstance();
                } catch (Throwable e) {
                    String msg = "Could not initialize an instance of report summary DataSource '"+getDataSourceClass()+"'";
                    throw new GeneralException(msg, e);
                }
            }
            return instance;
        }

        public List<LiveReportSummaryValue> getValues() {
            return values;
        }

        @XMLProperty(mode=SerializationMode.LIST)
        public void setValues(List<LiveReportSummaryValue> values) {
            this.values = values;
        }

        public List<Map<String, String>> getValueMap() {

            List<Map<String, String>> rows = new ArrayList<Map<String, String>>();

            if (values != null){
                for(LiveReportSummaryValue value : values){
                    Map<String, String> row = new HashMap<String, String>();
                    rows.add(row);

                    row.put("label", value.getLabel());
                    row.put("value", value.getValue() != null ? value.getValue().toString() : "");
                }

            }

            return rows;
        }
    }

    /**
     * An individual value in a report summary table. They are made up of a
     * unique identifier - the name, a label and a value.
     */
    @XMLClass
    public static final class LiveReportSummaryValue extends AbstractXmlObject{

        /**
         * Unique identifier for this value. The value is retrieved
         * from the datasource using the name as a key.
         */
        private String name;

        /**
         * Descriptive label for the value.
         */
        private String label;

        /**
         * The actual value to display.
         */
        private Object value;

        public LiveReportSummaryValue() {
        }

        public String getName() {
            return name;
        }

        @XMLProperty
        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        @XMLProperty
        public void setLabel(String label) {
            this.label = label;
        }

        public Object getValue() {
            return value;
        }

        @XMLProperty
        public void setValue(Object value) {
            this.value = value;
        }
    }

    public static interface LiveReportSummaryDataSource{
        public Attributes<String, Object> getData(SailPointContext ctx, Attributes<String, Object> args) throws GeneralException;
    }


}
