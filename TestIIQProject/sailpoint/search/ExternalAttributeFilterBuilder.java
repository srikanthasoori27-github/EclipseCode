/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.api.SailPointFactory;
import sailpoint.api.SailPointContext;

/**
 * This class handles both Identity and Link queries that
 * include multi-valued attributes that have been externalized
 * to a separate table.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 */
public class ExternalAttributeFilterBuilder extends BaseFilterBuilder {

    // attribute names
    public static final String IDENTITY_EXTERNAL = "IdentityExternalAttribute";
    public static final String LINK_EXTERNAL = "LinkExternalAttribute";

    // join properties
    public static final String LINK_JOIN = "links.id";
    public static final String IDENTITY_JOIN = "id";

    private final String LINK_EXTERNAL_SEARCH_TYPE = "ExternalLinkAttribute";

    public ExternalAttributeFilterBuilder() { }

    @Override 
    public Filter getFilter() throws GeneralException {

        String tableName = IDENTITY_EXTERNAL;
        String joinProperty = IDENTITY_JOIN;
        String attrName = this.propertyName;
        String searchType = this.definition.getSearchType();

        if ( LINK_EXTERNAL_SEARCH_TYPE.equals(searchType) ) {
            tableName = LINK_EXTERNAL;
            joinProperty = LINK_JOIN;
        }

        //TODO: need a better ( ui based ) way to configure the type 
        // of query to build
        String operator = null;
        SailPointContext ctx = SailPointFactory.getCurrentContext();
        Configuration config = ctx.getConfiguration();
        if ( config != null ) {
            String configOp = config.getString("slicerDicerMultivaluedOperator");
            if ( configOp != null )
                operator = configOp;
        }

        Filter filter = null;
        if(inputType !=null && inputType.equals(InputType.Null)) {
            filter = buildNullFilters(tableName, joinProperty, attrName);
        } else if(inputType !=null && inputType.equals(InputType.NotNull)) {
            filter = buildNotNullFilters(tableName, joinProperty, attrName);
        } else if ( ( attrName != null ) && ( value != null ) ) {
            List<String> values = asList(this.value);
            if ( values != null ) {
               if ( "OR".equals(this.definition.getListOperation())) {
                   filter = buildOrFilter(tableName, joinProperty, attrName, values, operator);
               } else {
                   if (inputType != null && inputType.equals(InputType.In)) {
                       filter = buildOrFilter(tableName, joinProperty, attrName, values, operator);
                   } else {
                       filter = buildAndFilter(tableName, joinProperty, attrName, values, operator);
                   }
               }
            }
        }
        return filter;
    }

    /**
     * Build OR Filter for each attribute value.
     * 
     * Build a Filter that represents all of the values
     * that are allowed.  Then we join it one time ANDed 
     * with the attribute * name.  
     * 
     * NOTE: Introducing multiple joins here during an OR operation 
     * will have a negative performance impact.
     * 
     * If you specify op = "EQ", the query will do exact match ( eq) instead of the like.
     *
     */
    public static Filter buildOrFilter(String tableName, String joinProperty, String attrName, 
                                       List<String> values, String op) {
        ArrayList<Filter> valueFilters = new ArrayList<Filter>();
        for ( String val : values ) {
            if ( val != null ) {                         
                Filter f = Filter.like(tableName+".value", val, MatchMode.START);
                if ( ( op != null ) && op.equals("EQ") ) {
                    f = Filter.eq(tableName+".value", val);
                }
                valueFilters.add(Filter.ignoreCase(f));
            }
        }
        List<Filter> attrFilters = buildBaseAttributeFilters(tableName, joinProperty, attrName);
        return Filter.and(Filter.and(attrFilters),Filter.or(valueFilters));
    }

    /**
     * Build OR Filter for each attribute value.
     * Method does aa LIKE query coupled with the MatchMode.START which will
     * still use an index to lookup values.
     */ 
    public static Filter buildOrFilter(String tableName, String joinProperty, String attrName, List<String> values) {
        return buildOrFilter(tableName, joinProperty, attrName, values, null);
    }

    /**
     * Build AND Filter for each attribute value.
     * 
     * We have to describe this query as a collectionCondition in order to 
     * to get a separate join per attribute value.  Its hairy and 
     * is handled in the FilterVisitor if this pattern is followed.
     *
     * Null op builds a LIKE query coupled with the MatchMode.START which will
     * use an index to lookup values. 
     *
     * If you specify the op "EQ", the query will do exact match instead of the like.
     */
    public static Filter buildAndFilter(String tableName, String joinProperty, String attrName, 
                                        List<String> values, String op) {
        List<Filter> filters = new ArrayList<Filter>();
        for ( String val : values ) {
            if ( val != null ) {                         
                List<Filter> subFilter = buildBaseAttributeFilters(tableName, joinProperty, attrName);
                Filter f = Filter.like(tableName+".value", val, MatchMode.START);
                if ( ( op != null ) && op.equals("EQ") ) {
                    f = Filter.eq(tableName+".value", val);
                }
                subFilter.add(Filter.ignoreCase(f));
                filters.add(Filter.and(subFilter));
            }
        }
        return Filter.collectionCondition(tableName, Filter.and(filters));
    }


    /**
     * Build AND Filter for each attribute value.
     * This method builds a LIKE query coupled with the MatchMode.START which will
     * use an index to lookup values. 
     */
    public static Filter buildAndFilter(String tableName, String joinProperty, String attrName, List<String> values) {
        return buildAndFilter(tableName, joinProperty, attrName, values, null);
    }
  
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Build a filter that will filter based on attribute name and join it to
     * the correct table for the objectId field match. This is used in both
     * ORing and ANDing of values.
     */
    public static List<Filter> buildBaseAttributeFilters(String tableName, String joinProperty, String attrName) {
        ArrayList<Filter> filters= new ArrayList<Filter>();
        filters.add(Filter.join(joinProperty, tableName+".objectId"));
        filters.add(Filter.ignoreCase(Filter.eq(tableName+".attributeName", attrName)));
        return filters;
    }
    
    public static Filter buildNullFilters(String tableName, String joinProperty, String attrName) {
        List<Filter> subFilters = buildBaseAttributeFilters(tableName, joinProperty, attrName);
        Filter f = Filter.isnull(tableName+".value");
        subFilters.add(f);
        return Filter.and(subFilters);
    }
    
    public static Filter buildNotNullFilters(String tableName, String joinProperty, String attrName) {
        List<Filter> subFilters = buildBaseAttributeFilters(tableName, joinProperty, attrName);
        Filter f = Filter.notnull(tableName+".value");
        subFilters.add(f);
        return Filter.and(subFilters);
    }

    /**
     * Parse the values comming in from the multiselect which
     * is separated by \n.  Always return a list to simply the
     * handling code.
     */ 
    private static List<String> asList(Object value) {
    	List<String> strs = new ArrayList<String>();
    	
    	if (value != null && value instanceof List) {
    		strs.addAll((List<String>)value);
    	} else if ( ( value != null ) && ( value instanceof String ) ) {
            strs = Util.delimToList("\n", value.toString(),true);
        } else if (value != null) {
        	throw new IllegalArgumentException("External attribute Filters can only contain Strings or Lists of Strings.");
        }
    	
        return strs;
    }
}
