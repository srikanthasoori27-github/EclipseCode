package sailpoint.object;

import sailpoint.api.ObjectUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Datasource configuration object for LiveReports. The LiveReportExecutor
 * will convert this into a LiveReportDataSource implementation
 * based on the DataSourceType.
 *
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class ReportDataSource extends AbstractXmlObject {

    @XMLClass
    public enum DataSourceType{
        Hql,
        Filter,
        Java
    }

    /**
     * The datasource type
     */
    private DataSourceType type;

    /**
     * List of report parameters. Parameters provide the mapping between
     * the TaskDefinition arguments list and object properties to be
     * queried. The queryParameters are not used for Java datasources,
     * or for HQL datasources.
     */
    private List<Parameter> queryParameters;

    /**
     * The name of the java class which will serve as the
     * datasource for this report. This is only required
     * for datasources of type Java.
     */
    private String dataSourceClass;

    /**
     * The type of object to be queried in this report. This property
     * is required for Filter and HQL datasources. It is also used
     * for charting, so it might also be helpful to specify the object
     * class when creatign a Java datasource.
     */
    private String objectType;

    /**
     * The base query for this datasource. This is required for HQL datasources
     * and optional for Filter datasources.
     */
    private String query;

    /**
     * Script which can be used to modify the base query of
     * the datasource based on task arguments. The queryScript is
     * essential when using an HQL datasource since it does not
     * support query building.
     */
    private Script queryScript;

    /**
     * Script which can be used to modify the QueryOptions built
     * by a filter datasource.
     */
    private Script optionsScript;

     /**
     * Rule which can be used to modify the QueryOptions built
     * by a filter datasource.
     */
    private Rule optionsRule;

    /**
     * Column which the report results grid should be grouped by.
     */
    private String gridGrouping;

    /**
     * Csv list of columns to be used in the sql group by clause.
     */
    private String groupBy;

    /**
     * List of sort objects which define the sort applied to this datasource.
     */
    private List<Sort> sort;

    /**
     * List of Joins to be included in this datasource. This is only
     * applicable for Java datasources.
     */
    private List<Join> joins;

    /**
     * A csv list of columns to use as an initial sort for this report.
     * Only ascending sorting is supported by this property.
     * 
     * @ignore
     * todo: support desc
     */
    private String defaultSort;


    public ReportDataSource() {
    }

    public DataSourceType getType() {
        return type;
    }

    @XMLProperty
    public void setType(DataSourceType type) {
        this.type = type;
    }

    public String getDataSourceClass() {
        return dataSourceClass;
    }

    @XMLProperty
    public void setDataSourceClass(String dataSourceClass) {
        this.dataSourceClass = dataSourceClass;
    }

    public String getQuery() {
        return query;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setQuery(String query) {
        this.query = query;
    }

    public Script getQueryScript() {
        return queryScript;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setQueryScript(Script queryScript) {
        this.queryScript = queryScript;
    }

    public Script getOptionsScript() {
        return optionsScript;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setOptionsScript(Script optionsScript) {
        this.optionsScript = optionsScript;
    }

    public Rule getOptionsRule() {
        return optionsRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setOptionsRule(Rule optionsRule) {
        this.optionsRule = optionsRule;
    }

    public boolean hasCustomOptionsHandler(){
        return optionsRule != null || optionsScript != null;
    }

    public String getGridGrouping() {
        return gridGrouping;
    }

    @XMLProperty
    public void setGridGrouping(String gridGrouping) {
        this.gridGrouping = gridGrouping;
    }

    public String getGroupBy() {
        return groupBy;
    }

    @XMLProperty
    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public List<String> getGroupByColumns(){
        return groupBy != null ? Util.csvToList(groupBy) : null;
    }

    public List<Sort> getSortOrder() {
        return sort;
    }

    public void addSortOrder(Sort newSort, boolean append){

        if (newSort == null)
            return;

        if (this.sort == null){
            this.sort = new ArrayList<Sort>();
        }

        // Don't add duplicate sort ordering
        for(Sort sortOrder : this.sort){
            if (newSort.isSame(sortOrder))
                return;
        }

        if (append){
            this.sort.add(newSort);
        } else {
            this.sort.add(0, newSort);
        }
    }

    public boolean hasSortOrder(){
        return this.sort != null && !this.sort.isEmpty();
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setSortOrder(List<Sort> sort) {
        this.sort = sort;
    }

    public String getDefaultSort() {
        return defaultSort;
    }

    @XMLProperty
    public void setDefaultSort(String defaultSort) {
        this.defaultSort = defaultSort;
    }

    public List<String> getDefaultSortList(){
        return defaultSort != null ? Util.csvToList(defaultSort) : Collections.EMPTY_LIST;
    }

    public String getObjectType() {
        return objectType;
    }

    @XMLProperty
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Class<? extends SailPointObject> getObjectClass() throws GeneralException {

        if (getObjectType() == null)
            return null;

        Class clazz = ObjectUtil.getMajorClass(getObjectType());

        if ("CertificationItem".equals(getObjectType()))
            clazz = CertificationItem.class;

        if ("Link".equals(getObjectType()))
            clazz = Link.class;

        if (clazz == null){
            try {
                clazz = Class.forName(getObjectType());
            } catch (ClassNotFoundException e) {
                throw new GeneralException("Invalid object type '"+getObjectType()+
                        "' specified on report datasource.", e);
            }
        }

        if (clazz == null)
             throw new GeneralException("Could not determine the object type '"+getObjectType()+
                        "' specified on report datasource.");

        return clazz;
    }

    public List<Join> getJoins() {
        return joins;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setJoins(List<Join> joins) {
        this.joins = joins;
    }

    public List<Parameter> getQueryParameters() {
        return queryParameters;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setQueryParameters(List<Parameter> parameters) {
        this.queryParameters = parameters;
    }

    public Parameter getParameter(String argument){
        if (argument != null && queryParameters != null){
            for(Parameter p : queryParameters){
                if (argument.equals(p.getArgument())){
                    return p;
                }
            }
        }

        return null;
    }

    public void addParameter(Parameter param){
        if (queryParameters == null)
            queryParameters = new ArrayList<Parameter>();
        queryParameters.add(param);
    }

    public void addParameter(String name, String property){
        ReportDataSource.Parameter param = new ReportDataSource.Parameter();
        param.setArgument(name);
        param.setProperty(property);
        addParameter(param);
    }

    @XMLClass
    public static class Parameter extends AbstractXmlObject {

        public static String PARAM_TYPE_DATE_RANGE = "daterange";

        /**
         * Name of the argument the value of this parameter
         * will be sourced from.
         */
        private String argument;

        /**
         * Name of the object property the parameter will be
         * applied to in the final query.
         */
        private String property;

        /**
         * Filter operation to apply when building query.
         */
        private String operation;

        /**
         * Class of the value. Constants from
         * sailpoint.object.PropertyInfo may be used,
         * 'daterange' for Date Range values, the simple
         * sailpoint class name, or a fully qualified class
         * name.
         */
        private String valueClass;

        /**
         * Script which is executed when the parameter is evaluated.
         * This script can be used to alter the QueryOptions.
         *
         * value : Parameter value
         * arguments : Report arguments map
         * queryOptions : Current QueryOptions
         */
        private Script queryScript;

        /**
         * Query rule - see queryScript
         */
        private Rule queryRule;

        /**
         * Script that can be used to modify the value of this parameter.
         * Script args are:
         *
         * value : Value of the parameter
         * arguments : report arguments map
         * context : SailPointContext
         */
        private Script valueScript;

        private Rule valueRule;

        /**
         * Ignore case when searching with this parameter.
         */
        private boolean ignoreCase;

        /**
         * Default value to use for this parameter.
         */
        private String defaultValue;

        /**
         * If true, this parameter will be passed to the queryScript,
         * but not to the hql query executor.
         */
        private boolean excludeHqlParameter;

        private String externalAttributeClass;

        /**
         * If true, this parameter is multi-valued.
         * Default value for primitive boolean is false,
         * which is the way we likes it.
         */
        private boolean multi;

        public String getArgument() {
            return argument;
        }

        @XMLProperty
        public void setArgument(String argument) {
            this.argument = argument;
        }

        public Script getValueScript() {
            return valueScript;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        @XMLProperty
        public void setDefaultValue(String value) {
            this.defaultValue = value;
        }

        @XMLProperty(mode=SerializationMode.INLINE, xmlname="ValueScript")
        public void setValueScript(Script valueScript) {
            this.valueScript = valueScript;
        }

        public Rule getValueRule() {
            return valueRule;
        }

        @XMLProperty(mode=SerializationMode.REFERENCE)
        public void setValueRule(Rule valueRule) {
            this.valueRule = valueRule;
        }

        public DynamicValue getValueDef(){
            if (valueRule != null || valueScript != null)
                return new DynamicValue(valueRule, valueScript, null);

            return null;
        }

        public String getValueClass() {
            return valueClass;
        }

        @XMLProperty
        public void setValueClass(String valueClass) {
            this.valueClass = valueClass;
        }

        public String getProperty() {
            return property;
        }

        @XMLProperty
        public void setProperty(String property) {
            this.property = property;
        }

        public String getOperation() {
            return operation;
        }

        @XMLProperty
        public void setOperation(String operation) {
            this.operation = operation;
        }

        public void setOperation(Filter.LogicalOperation operation) {
            this.operation = operation != null ? operation.name() : null;
        }

        public Script getQueryScript() {
            return queryScript;
        }

        @XMLProperty(mode=SerializationMode.INLINE, xmlname="QueryScript")
        public void setQueryScript(Script queryScript) {
            this.queryScript = queryScript;
        }

        @XMLProperty(mode=SerializationMode.REFERENCE)
        public Rule getQueryRule() {
            return queryRule;
        }

        public void setQueryRule(Rule queryRule) {
            this.queryRule = queryRule;
        }

        public DynamicValue getQueryDef(){
            if (queryRule != null || queryScript != null)
                return new DynamicValue(queryRule,  queryScript, null);

            return null;
        }

        public boolean isDateRange(){
            return PARAM_TYPE_DATE_RANGE.equals(this.valueClass);
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        @XMLProperty
        public void setIgnoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
        }

        public boolean isExcludeHqlParameter() {
            return excludeHqlParameter;
        }

        @XMLProperty
        public void setExcludeHqlParameter(boolean excludeHqlParameter) {
            this.excludeHqlParameter = excludeHqlParameter;
        }

        public String getExternalAttributeClass() {
            return externalAttributeClass;
        }

        @XMLProperty
        public void setExternalAttributeClass(String externalAttributeClass) {
            this.externalAttributeClass = externalAttributeClass;
        }

        @XMLProperty
        public void setMulti(boolean b) {
            this.multi = b;
        }

        public boolean isMulti() {
            return this.multi;
        }
    }

    @XMLClass
    public static class Join{

        private String property;
        private String joinProperty;

        public String getProperty() {
            return property;
        }

        @XMLProperty
        public void setProperty(String property) {
            this.property = property;
        }

        public String getJoinProperty() {
            return joinProperty;
        }

        @XMLProperty
        public void setJoinProperty(String joinProperty) {
            this.joinProperty = joinProperty;
        }
    }

}
