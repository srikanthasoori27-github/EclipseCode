/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import java.util.Date;
import java.util.Map;

import sailpoint.object.Filter;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class DateRange implements FilterParameter{

    private String property;
    private Long start;
    private Long end;

    public DateRange() {
    }

    public DateRange(String property, Long start, Long end) {
        this.property = property;
        this.start = start;
        this.end = end;
    }

    public DateRange(String property, Map paramMap) {
        this.property = property;
        start = (Long)paramMap.get("start");
        end = (Long)paramMap.get("end");
    }

    public Date getStartDate(){
        return start != null ? new Date(start) : null;
    }

    public boolean isEmpty(){
        return end==null && start==null;
    }

    public Filter getFilter(){
        if (property != null && !isEmpty()){
            Date startDate = start != null ? new Date(start) : null;
            Date endDate = end != null ? new Date(end) : null;
            return ReportParameterUtil.getDateRangeFilter(property, startDate, endDate);
        } else {
            return null;
        }
    }
}
