/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import sailpoint.tools.Util;
import sailpoint.tools.GeneralException;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.api.SailPointContext;

import java.util.*;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ReportParameterUtil {

    /**
     * Given a comma separated input string, splits the string and creates a
     * list. Each item is also trimmed to remove leading or trailing whitespace.
     *
     * @param attributeValue report input attribute
     * @return List of values in the attribute. If value is null or empty, an empty list is returned.
     *         Return value is never null.
     */
    public static List<String> splitAttributeValue(String attributeValue) {
        List<String> output = new ArrayList<String>();

        if (attributeValue == null || attributeValue.length() == 0)
            return output;

        String[] inputs = attributeValue.split(",");
        for (int i = 0; i < inputs.length; i++) {
            output.add(inputs[i].trim());
        }

        return output;
    }

    /**
     * Takes a date and sets the time to 00:00:00
     *
     * @param date Date to baseline. May be null
     * @return baselined date, or null if date was null.
     */
    private static Date baselineStartDate(Date date) {
        return date != null ? Util.baselineDate(date) : null;
    }

    /**
     * Takes a date and sets the time to 00:00:00. For the end date, we
     * also need to include the date specified, so we need  to push the
     * date forward one day.
     *
     * @param date Date to baseline. May be null
     * @return baselined date, or null if date was null.
     */
    private static Date baselineEndDate(Date date) {
        Date d = baselineStartDate(date);
        if (d == null)
            return null;

        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.add(Calendar.DAY_OF_YEAR, 1);

        return cal.getTime();
    }

    /**
     * Creates a date range filter for the given property.
     *
     * @param property  Name of the property,may not be null
     * @param startDate Start date, may be null
     * @param endDate   End date, may be null
     * @return Filter for querying the given property by date range
     */
    public static Filter getDateRangeFilter(String property, Date startDate, Date endDate) {

        if (property == null)
            throw new NullPointerException("Property name may not be null.");

        List<Filter> filters = new ArrayList<Filter>();

        if (startDate != null)
            filters.add(Filter.ge(property, baselineStartDate(startDate)));

        if (endDate != null)
            filters.add(Filter.le(property, baselineEndDate(endDate)));

        if (filters.size() > 1)
            return Filter.and(filters);
        else if (filters.size() == 1)
            return filters.get(0);
        else
            return null;
    }

    public static List<String> convertIdsToNames(SailPointContext ctx, Class clazz, List<String> ids)
            throws GeneralException {
        List<String> names = new ArrayList<String>();

        if (ids != null && !ids.isEmpty()){
            Iterator<Object[]> results = ctx.search(clazz, new QueryOptions(Filter.in("id", ids)), Arrays.asList("name"));
            if (results != null){
                while (results.hasNext()) {
                    Object[] row =  results.next();
                    names.add((String)row[0]);
                }
            }
        }

        return names;
    }

    public static List<String> getNames(SailPointContext ctx, Class clazz, String value)
            throws GeneralException {        
        List<String> ids = splitAttributeValue(value);
        return convertIdsToNames(ctx, clazz, ids);
    }

}
