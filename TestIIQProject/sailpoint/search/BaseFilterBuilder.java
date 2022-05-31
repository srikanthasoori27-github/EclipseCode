/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ValidationException;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Identity;
import sailpoint.object.Resolver;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.object.TimePeriod;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class BaseFilterBuilder implements FilterBuilder {
    private static final Log log = LogFactory.getLog(BaseFilterBuilder.class);

    SearchInputDefinition definition;
    InputType inputType;
    PropertyType propertyType;
    PropertyType nullPropertyType;
    String propertyName;
    String nullPropertyName;
    Object value;
    String searchType;
    boolean ignoreCase;
    Filter.MatchMode matchMode;
    Resolver resolver;
    String name;
    private Class<?> scope;
    private TimeZone timeZone;

    public BaseFilterBuilder() {}
    
    public BaseFilterBuilder(SearchInputDefinition def, Resolver r) {
        initialize(def);
        resolver = r;
    }
    
    public void initialize(SearchInputDefinition def) {
        this.definition = def;
        this.name = def.getName();
        this.searchType = def.getSearchType();
        this.inputType = def.getInputType();
        this.propertyType = def.getPropertyType();
        this.nullPropertyType = def.getNullPropertyType();
        this.propertyName = def.getPropertyName();
        this.nullPropertyName = def.getNullPropertyName();
        this.value = def.getValue();
        this.ignoreCase = def.isIgnoreCase();
        this.matchMode = def.getMatchMode();
        this.timeZone = def.getTimeZone();
    }
    
    public void setScope(Class<?> val) {
     
        this.scope = val;
    }
    
    protected Class<?> getScope() {

        return this.scope;
    }
    
    public Filter getJoin() throws GeneralException {
        return null;
    }

    /** Takes an input definition in and creates a filter based on all of the parameters
     * of the input definition
     * @return
     */
    public Filter getFilter() throws GeneralException{
        
        log.debug("Building Filter for Definition : " + definition.getDescription() + " Input Type: " + inputType);
        
        Filter filter = getFilterByType();

        if(ignoreCase)
            filter = Filter.ignoreCase(filter);

        log.debug("Filter: " + filter);
        return filter;
    }
    
    protected Filter getFilterByType() throws GeneralException {
        Filter filter = null;
        if(definition!=null) {
            
            if(propertyType.equals(PropertyType.Date) && null != value && value.toString().contains("=")) {
                return getDateFilter();
            }

            /**Get Equals Filter**/
            if(inputType.equals(InputType.Equal)) {
                filter = getEQFilter();
            }

            /**Get Like Filter**/
            if(inputType.equals(InputType.Like)) {
                filter = getLikeFilter();
            }

            /**Get In Filter**/
            if(inputType.equals(InputType.In)) {
                filter = getInFilter();
            }

            /**Get Contains All Filter **/
            if(inputType.equals(InputType.ContainsAll)) {
                filter = getContainsAllFilter();
            }

            /**Get Is Null Filter **/
            if(inputType.equals(InputType.Null)) {
                filter = getIsNullFilter();
            }
            
            /**Get Not Null Filter **/
            if(inputType.equals(InputType.NotNull)) {
                filter = getNotNullFilter();
            }

            /** Get Not Equals Filter **/
            else if(inputType.equals(InputType.NotEqual)) {
                filter = getNEFilter();
            }

            /** Get Greater Than/After Filter **/
            else if(inputType.equals(InputType.GreaterThan) || inputType.equals(InputType.After)) {
                filter = getGTFilter();
            }

            /** Get Greater Than Equal Filter **/
            else if(inputType.equals(InputType.GreaterThanEqual)) {
                filter = getGTEFilter();
            }

            /** Get Greater Than/After Filter **/
            else if(inputType.equals(InputType.LessThan) || inputType.equals(InputType.Before)) {
                filter = getLTFilter();
            }

            /** Get Greater Than Equal Filter **/
            else if(inputType.equals(InputType.LessThanEqual)) {
                filter = getLTEFilter();
            }
        }
        
        return filter;
    }

    /** Build Equals Filter **/
    protected Filter getEQFilter() throws ValidationException {
        
        Filter filter = null;
        if(propertyType!=null && propertyType.equals(PropertyType.Integer))
            filter= (LeafFilter) Filter.eq(propertyName, otoI(value));

        else if (value instanceof Identity)
            filter= (LeafFilter) Filter.eq(propertyName, (((Identity)value).getId()));

        else if(value instanceof String && (propertyType!=null &&propertyType.equals(PropertyType.Boolean)))
            filter= (LeafFilter) Filter.eq(propertyName, Boolean.parseBoolean((String)value));

        else
            filter= (LeafFilter) Filter.eq(propertyName, value);

        return filter;
    }

    /** Build Like Filter **/
    protected Filter getLikeFilter() {

        Filter filter = null;
        /** If Match Mode is null, default to anywhere **/
        if(matchMode == null) 
            matchMode = Filter.MatchMode.ANYWHERE;
        filter = (LeafFilter) Filter.like(propertyName, value, matchMode);

        return filter;
    }

    /** Build In Filter **/
    protected Filter getInFilter() throws GeneralException {

        Filter filter = null;
        
        /** Specialized handling of Identities in case they are passed in **/
        if(value instanceof Identity) {
            ArrayList<String> values = new ArrayList<String>();
            values.add(((Identity)value).getId());
            filter = Filter.containsAll(propertyName, values);
        }		
        else if (propertyType != null && propertyType.equals(PropertyType.StringList)) {
            filter = Filter.in(propertyName, (List<String>)value);
        } else {
            throw new GeneralException("The SearchBean can only search on properties of type " + PropertyType.StringList + " when given inputs of type " + InputType.In + ".");
        }

        return filter;
    }

    /** Build Contains All Filter **/
    protected Filter getContainsAllFilter() throws GeneralException {

        Filter filter = null;
        if (propertyType != null && propertyType.equals(PropertyType.TimePeriodList)) {
            List<TimePeriod> periods = (List<TimePeriod>)value;
            if(periods.isEmpty()){
                filter= null;
            } else
                filter = (LeafFilter) Filter.containsAll(propertyName, periods);
        } else if((propertyType != null && propertyType.equals(PropertyType.StringList))) {
            List<String> strings = (List<String>)value;
            log.debug("Strings: " + strings);
            if(strings.isEmpty()) {
                filter = null;
            }
            else
                filter = (LeafFilter) Filter.containsAll(propertyName, strings);
        } else if(value instanceof String) {
            List<String> strings = new ArrayList<String>();
            strings.add((String)value);
            filter = (LeafFilter) Filter.containsAll(propertyName, strings);
        }
        else {
            throw new GeneralException("The SearchBean can only search on properties of type StringList and TimePeriodList when given inputs of type " + InputType.ContainsAll + ".");
        }
        return filter;
    }

    /** Build Is Null Filter **/
    protected Filter getIsNullFilter() {

        Filter filter = null;

        /** If this def has a null property set to use, use it instead of the actual property name. */
        if(nullPropertyName!=null) {   
            propertyName = nullPropertyName;                     
        }
        
        /** If this is a collection, hibernate will puke on an isNull, so we need to use isEmpty instead */
        if((nullPropertyType!=null)){
            if(nullPropertyType.equals(PropertyType.Collection)) {
                filter = (LeafFilter) Filter.isempty(propertyName);
            } else {
                filter = (LeafFilter) Filter.isnull(propertyName);
            }
        } else if(propertyType!=null) {
            if(propertyType.equals(PropertyType.Collection)) {
                filter = (LeafFilter) Filter.isempty(propertyName);
            } else {
                filter = (LeafFilter) Filter.isnull(propertyName);
            }
        } else
            filter = (LeafFilter) Filter.isnull(propertyName);

        return filter;
    }
    
    /** Build Non Null Filter **/
    protected Filter getNotNullFilter() throws GeneralException {

        Filter filter;

        // Similar to isNull, notNull can fail for some Collection types.
        // Use isNull logic to build the filter, then do a NOT.
        if (Util.nullSafeEq(nullPropertyType, PropertyType.Collection) || Util.nullSafeEq(propertyType, PropertyType.Collection)) {
            filter = Filter.not(getIsNullFilter());
        } else {
            filter = (LeafFilter) Filter.notnull(propertyName);
        }

        return filter;
    }

    /** Build Not Equals Filter **/
    protected Filter getNEFilter() throws ValidationException {
        Filter filter = null;

        if(propertyType != null && propertyType.equals(PropertyType.Integer)) {
            filter = (LeafFilter) Filter.ne(propertyName, otoI(value));
        }
        else if (propertyType != null 
                && propertyType.equals(PropertyType.Identity)
                && value instanceof Identity) {
            filter = (LeafFilter) Filter.ne(propertyName, (((Identity) value).getId()));
        }
        else {
            filter = (LeafFilter) Filter.ne(propertyName, value);
        }

        return filter;
    }

    /** Build Greater Than Filter **/
    protected Filter getGTFilter() throws ValidationException {
        Filter filter = null;

        if((value instanceof String))
            filter = (LeafFilter) Filter.gt(propertyName, otoI(value));
        else if(value instanceof Date)
            filter = (LeafFilter) Filter.gt(propertyName, Util.baselineDate((Date)value, timeZone));
        else 
            filter = (LeafFilter) Filter.gt(propertyName, value);

        return filter;
    }

    /** Build Greater Than Equal Filter **/
    protected Filter getGTEFilter() throws ValidationException {
        Filter filter = null;

        if(value instanceof String)
            filter = (LeafFilter) Filter.ge(propertyName, otoI(value));
        else if(value instanceof Date)
            filter = (LeafFilter) Filter.ge(propertyName, Util.baselineDate(((Date)value), timeZone));
        else
            filter = (LeafFilter) Filter.ge(propertyName, value);

        return filter;
    }

    /** Build Less Than Filter **/
    protected Filter getLTFilter() throws ValidationException {
        Filter filter = null;

        if((value instanceof String))
            filter = (LeafFilter) Filter.lt(propertyName, otoI(value));
        else if(value instanceof Date) {
            /**Need to add 24 hours to date so that we treat it inclusively.**/
            Calendar cal = Calendar.getInstance();
            cal.setTime(Util.baselineDate((Date)value, timeZone));
            cal.add(Calendar.DAY_OF_YEAR, 1);
            filter = (LeafFilter) Filter.lt(propertyName, cal.getTime());

        }else 
            filter = (LeafFilter) Filter.lt(propertyName, value);

        return filter;
    }

    /** Build Less Than Equal Filter **/
    protected Filter getLTEFilter() throws ValidationException {
        Filter filter = null;

        if(value instanceof String)
            filter = (LeafFilter) Filter.le(propertyName, otoI(value));
        else if(value instanceof Date) {
            /**Need to add 24 hours to date so that we treat it inclusively.**/
            Calendar cal = Calendar.getInstance();
            cal.setTime(Util.baselineDate((Date)value, timeZone));
            cal.add(Calendar.DAY_OF_YEAR, 1);
            filter = (LeafFilter) Filter.le(propertyName, cal.getTime());

        }
        else
            filter = (LeafFilter) Filter.le(propertyName, value);

        return filter;
    }
    
    protected Filter getDateFilter() {
        String[] parts = ((String)value).split("=");
        if(parts.length>0) {
            if(parts.length>1) {
                long start = Long.parseLong(parts[0]);
                long end = Long.parseLong(parts[1]);
                
                return Filter.and(Filter.ge(propertyName, start),Filter.le(propertyName,end));
            } else {
                long date = Long.parseLong(parts[0]);
                if(value.toString().indexOf("|")==0) {
                    return Filter.le(propertyName, date);
                } else {
                    return Filter.ge(propertyName, date);    	            
                }
            }
        }
        
        return null;
    }
    
    protected Integer otoI(Object o) throws ValidationException {
        try {
            return Integer.parseInt((String)o);
        } catch (NumberFormatException e) {
            throw new ValidationException(new Message(MessageKeys.ERR_INVALID_NUMBER, o));
        }
    }

    public SearchInputDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(SearchInputDefinition def) {
        initialize(def);
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }
    
    public Resolver getResolver() {
        return resolver;
    }

    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }
    
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }
    
    public TimeZone getTimeZone() {
        return timeZone;
    }

}
