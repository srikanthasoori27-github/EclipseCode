package sailpoint.object;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Holds information about optional charts included as part of live reports.
 * 
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class Chart extends AbstractXmlObject {

    private final static String ATTR_DATA = "chartData";

    public static String ATTR_ROWS = "rows";

    public final static String CHART_TYPE_PIE = "pie";
    public final static String CHART_TYPE_COLUMN= "column";
    public final static String CHART_TYPE_LINE = "line";

    // Keys used for the properties that make up a chart
    public final static String FIELD_VALUE = "value";
    public final static String FIELD_CATEGORY = "category";
    public final static String FIELD_SERIES = "series";

    /**
     * Used when rendering jasper charts. If this is
     * left null sailpoint.reporting.DefaultCustomizerClass
     * is used.
     */
    private String customizerClass;

    /**
     * Chart title, this is displayed above the chart.
     */
    private String title;

    /**
     * Label displayed along the Y-axis
     */
    private String leftLabel;

    /**
     * Label displayed along the X-axis
     */
    private String bottomLabel;

    /**
     * Type of chart. Currently pie, column and line are supported.
     */
    private String type;

    /**
     * Script which generates the data for the chart. The script should
     * return a List<Map<String, Object>>. Each row will contain three
     * keys - value, category and series.
     *
     * Arguments:
     * context - SailPoint Context
     * args - The report task arguments
     * report - The report LiveReport object.
     * baseHql - Base HQL for the report. Null if this is not an HQL report
     * baseQueryOptions - Base QueryOptions for this report, null if this is an HQL report.
     */
    private Script script;

    /**
     * Optional datasource rule. Should follow the same behavior as the script property.
     */
    private Rule dataSourceRule;

    /**
     * Csv list of properties to group the datasource by.
     */
    private String groupBy;

    /**
     * Sorts the datasource results.
     */
    private List<Sort> sortBy;

    /**
     * Limits the number of records to return from the datasource.
     * This can be used in cases where only the top or bottom set
     * of results need to be displayed.
     */
    private int limit;

    /**
     * Datasource property to use for the chart category.
     */
    private String category;

    /**
     * Datasource property to use for the chart value.
     */
    private String value;

    /**
     * Datasource property to use for the chart series.
     */
    private String series;

    /**
     * String to display if the category value is null.
     */
    private String nullCategory;

    /**
     * String to display if the series value is null.
     */
    private String nullSeries;

    /**
     * Attributes map used by the chart to store internal data.
     */
    private Attributes<String, Object> attributes;

    public Chart() {
    }

    //////////////////////////////////////////////////////////
    //
    //  Member Properties
    //
    //////////////////////////////////////////////////////////

    @XMLProperty
    public String getCustomizerClass() {
        return customizerClass;
    }

    public void setCustomizerClass(String customizerClass) {
        this.customizerClass = customizerClass;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    @XMLProperty
    public void setTitle(String title) {
        this.title = title;
    }

    @XMLProperty
    public void setType(String type) {
        this.type = type;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public List<String> getGroupByList(){
        return groupBy != null ? Util.csvToList(groupBy) : Collections.EMPTY_LIST;
    }

    @XMLProperty
    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public List<Sort> getSortBy() {
        return sortBy;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setSortBy(List<Sort> sortBy) {
        this.sortBy = sortBy;
    }

    public int getLimit() {
        return limit;
    }

    @XMLProperty
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getCategory() {
        return category;
    }

    @XMLProperty
    public void setCategory(String category) {
        this.category = category;
    }

    public String getNullCategory() {
        return nullCategory;
    }

    @XMLProperty
    public void setNullCategory(String nullCategory) {
        this.nullCategory = nullCategory;
    }

    public String getValue() {
        return value;
    }

    @XMLProperty
    public void setValue(String value) {
        this.value = value;
    }

    public String getSeries() {
        return series;
    }

    @XMLProperty
    public void setSeries(String series) {
        this.series = series;
    }

    public String getNullSeries() {
        return nullSeries;
    }

    @XMLProperty
    public void setNullSeries(String nullSeries) {
        this.nullSeries = nullSeries;
    }

    @JsonIgnore
    public Script getScript() {
        return script;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setScript(Script script) {
        this.script = script;
    }

    @JsonIgnore
    public Rule getDataSourceRule() {
        return dataSourceRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setDataSourceRule(Rule rule) {
        this.dataSourceRule = rule;
    }

    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String name, Object value){
        if (attributes==null)
            attributes = new Attributes<String, Object>();
        attributes.put(name, value);
    }

    public Object getAttribute(String key){
        return attributes != null && attributes.containsKey(key) ? attributes.get(key) : null;
    }

    public String getBottomLabel() {
        return bottomLabel;
    }

    @XMLProperty
    public void setBottomLabel(String bottomLabel) {
        this.bottomLabel = bottomLabel;
    }

    public String getLeftLabel() {
        return leftLabel;
    }

    @XMLProperty
    public void setLeftLabel(String leftLabel) {
        this.leftLabel = leftLabel;
    }

    //////////////////////////////////////////////////////////
    //
    //  Additional Properties (Stored in attrs map)
    //
    //////////////////////////////////////////////////////////

    public List<Map<String, Object>> getData() {
        return (List<Map<String, Object>>)getAttribute(ATTR_DATA);
    }

    public void setData(List<Map<String, Object>> data) {
        this.addAttribute(ATTR_DATA, data);
    }
}
