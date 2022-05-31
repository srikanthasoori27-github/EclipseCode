/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;
import net.sf.jasperreports.engine.design.JRDesignExpression;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.List;

/**
 * @author jonathan.bryant@sailpoint.com
 */
@SuppressWarnings("serial")
public class ReportColumnConfig extends AbstractXmlObject {

    public static final Integer DEFAULT_WIDTH = 110;

    /**
     * Convenience method for setting the design expression for this column. This
     * will evaluate to the Jasper expression $F{<FIELD>}. Note that the field should be
     * set OR the expression. Setting both will result in an exception.
     */
    private String field;

    /**
     * Name of the object property this column will be populated from.
     */
    private String property;

    /**
     * Optional property to use if the value of the primary
     * property is null or empty.
     */
    private String ifEmpty;

    /**
     * Csv list of additional properties to be
     * passed to a column renderScript.
     */
    private String scriptArguments;

    private String subQueryKey;

    private String sortExpression;

    /**
     * Sets the JRDesignExpression text used when evaluating the report column
     * value.
     * @deprecated
     */
    @Deprecated
    private String expression;

    /**
     * Column header text
     */
    private String header;

    /**
     * Column value class
     */
    private String valueClass;

    /**
     * Column width in pixels
     */
    private Integer width = DEFAULT_WIDTH;

    private boolean hidden;

    private boolean sortable;

    /**
     * Script used to render the report column value.
     *
     * Availabe arguments:
     * value : The value of the current column
     * context : sailpoint context
     * column : The current column configuration object (ReportColumnConfig)
     * scriptArgs : Map containing any additional column specified in the scriptArguments column property
     * locale : Current report locale.
     * timezone : Current report timezone
     */
    private Script renderScript;

    private Rule renderRule;

    /**
     * Date format string.
     */
    private String dateFormat;

    /**
     * Time format string.
     */
    private String timeFormat;

    /**
     * If true, this column was not part of the base report,
     * it is an extended column added by the Report's
     * extended column definition.
     * 
     * @ignore
     * Keeping track of this flag helps us reset the column
     * configuration when the report changes values.
     */
    private boolean extendedColumn;

    /**
     * Flag to tell the DataSourceColumnHelper to skip localization.
     * To be used for columns that hold reserved keywords,
     * used in DataSourceColumnHelper#getColumnValue(Object, ReportColumnConfig)
     * for where the flag is used.
     */
    private boolean skipLocalization;

    public ReportColumnConfig() {
    }

    public ReportColumnConfig(String field, String property) {
        this.field = field;
        this.property = property;
    }

    public ReportColumnConfig(String field, String header, String valueClass) {
        this.field = field;
        this.header = header;
        this.valueClass = valueClass;
    }

    public ReportColumnConfig(String field, String header, String valueClass, int width) {
        this.field = field;
        this.header = header;
        this.valueClass = valueClass;
        this.width = width;
        
        if (width == 0)
            width = DEFAULT_WIDTH;
    }

    public int getWidth() {
        return width;
    }

    @XMLProperty
    public void setWidth(int width) {
        this.width = width;
    }

    public String getProperty() {
        return property;
    }

    @XMLProperty
    public void setProperty(String property) {
        this.property = property;
    }

    public String getIfEmpty() {
        return ifEmpty;
    }

    @XMLProperty
    public void setIfEmpty(String ifEmpty) {
        this.ifEmpty = ifEmpty;
    }

    public String getScriptArguments() {
        return scriptArguments;
    }

    public String getSortExpression() {
        return sortExpression;
    }

    @XMLProperty
    public void setSortExpression(String sortExpression) {
        this.sortExpression = sortExpression;
    }

    public List<String> getSortProperties(){
        if (sortExpression != null){
            return Util.csvToList(sortExpression);
        } else {
            return Arrays.asList(property);
        }
    }

    @XMLProperty
    public void setScriptArguments(String scriptArguments) {
        this.scriptArguments = scriptArguments;
    }

    public List<String> getScriptArgumentsList(){
        return scriptArguments != null ? Util.csvToList(scriptArguments) : null;
    }

    public String getHeader() {
        return header;
    }

    @XMLProperty
    public void setHeader(String header) {
        this.header = header;
    }

    @XMLProperty
    public String getField() {
        return field;
    }

    public String getSubQueryKey() {
        return subQueryKey;
    }

    @XMLProperty
    public void setSubQueryKey(String subQueryKey) {
        this.subQueryKey = subQueryKey;
    }

    public void setField(String field) {
        this.field = field;
    }

    /**
     * @return JRDesignExpression text to be used to evaluate the column value.
     * @deprecated 
     */
    @Deprecated
    public String getExpression() {
        return expression;
    }

    @Deprecated
    @XMLProperty
    public void setExpression(String expression) {
        this.expression = expression;
    }

    @XMLProperty
    public String getValueClass() {
        return valueClass;
    }

    public void setValueClass(String valueClass) {
        this.valueClass = valueClass;
    }

    public boolean isHidden() {
        return hidden;
    }

    @XMLProperty
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isSortable() {
        return sortable;
    }

    @XMLProperty
    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public Script getRenderScript() {
        return renderScript;
    }

    @XMLProperty(mode= SerializationMode.INLINE)
    public void setRenderScript(Script renderScript) {
        this.renderScript = renderScript;
    }

    public Rule getRenderRule() {
        return renderRule;
    }

    @XMLProperty(mode= SerializationMode.REFERENCE)
    public void setRenderRule(Rule renderRule) {
        this.renderRule = renderRule;
    }

    public DynamicValue getRenderDef(){
        if (renderRule != null || renderScript != null)
            return new DynamicValue(renderRule, renderScript , null);

        return null;
    }

    public boolean isExtendedColumn() {
        return extendedColumn;
    }

    @XMLProperty
    public void setExtendedColumn(boolean extendedColumn) {
        this.extendedColumn = extendedColumn;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    @XMLProperty
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Integer getDateStyle(){
        if (dateFormat != null){
            if ("long".equals(dateFormat.toLowerCase())){
                return DateFormat.LONG;
            } else if ("medium".equals(dateFormat.toLowerCase())){
                return DateFormat.MEDIUM;
            } else if ("short".equals(dateFormat.toLowerCase())){
                return DateFormat.SHORT;
            }
        }
        return null;
    }

    public boolean isCustomDateStyle(){
        return getDateStyle() == null && getDateFormat() != null;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    @XMLProperty
    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public Integer getTimeStyle(){
        if (timeFormat != null){
            if ("long".equals(timeFormat.toLowerCase())){
                return DateFormat.LONG;
            } else if ("medium".equals(timeFormat.toLowerCase())){
                return DateFormat.MEDIUM;
            } else if ("short".equals(timeFormat.toLowerCase())){
                return DateFormat.SHORT;
            }
        }
        return null;
    }

    public boolean isCustomTimeStyle(){
        return getTimeStyle() == null && getTimeFormat() != null;
    }

    public boolean hasDateFormatting(){
        return getDateFormat() != null || getTimeFormat() != null;
    }

    public JRDesignExpression getJRDesignExpression() {
        JRDesignExpression exp = new JRDesignExpression();
        exp.setValueClassName(valueClass);

        if (field != null && expression != null)
            throw new RuntimeException("Report column was defined with both a field name and an expression. " +
                    "The column should be defined with either a field name or an expression but not both.");

        if (field != null)
            exp.setText("$F{" + field + "}");
        else if (expression != null)
            exp.setText(expression);
        return exp;
    }

    public boolean isSkipLocalization() {
        return skipLocalization;
    }

    @XMLProperty
    public void setSkipLocalization(boolean skipLocalization) {
        this.skipLocalization = skipLocalization;
    }

}
