/**
 * 
 */
package sailpoint.reporting;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;

/**
 * @author peter.holcomb
 *
 */
public class RoleChangeMgmtReport extends BusinessRoleReport {

    private static Log log = LogFactory.getLog(RoleChangeMgmtReport.class);
    /**
     * 
     */
    public static final String ATTRIBUTE_CHANGE = "changeDate";
    
    public static final String START = "Start";
    public static final String END = "End";
    
    public List<Filter> buildFilters(Attributes<String,Object> inputs) {
        List<Filter> filters = super.buildFilters(inputs);
        filters.add(Filter.join("id", "BundleArchive.sourceId"));
        addDateTypeFilter(filters, inputs, ATTRIBUTE_CHANGE+START, REPORT_FILTER_TYPE_AFTER, "BundleArchive.created", null);
        addDateTypeFilter(filters, inputs, ATTRIBUTE_CHANGE+END, REPORT_FILTER_TYPE_BEFORE, "BundleArchive.created", null);
        
        return filters;
    }

}
