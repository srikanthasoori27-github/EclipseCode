/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

/**
 * This class converts the signed status input into a filter that can be used to query for appropriate certifications
 * @author  <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class CertificationStatusFilterBuilder extends BaseFilterBuilder {
    private static final Log log = LogFactory.getLog(CertificationStatusFilterBuilder.class);
    
    
    @Override
    public Filter getFilter() throws GeneralException {
        Filter filter;
        String status = this.value.toString();
        
        if (status == null || status.equals("all")) {
            filter = null;
        } else if (status.equals("signed")) {
            filter = Filter.notnull("signed");
        } else if (status.equals("unsigned")) {
            filter = Filter.isnull("signed");
        } else {
            log.error("The CertificationStatusFilterBuilder attempted to build a filter for an supported status value of: " + status + ".  The attempt was aborted and no filter was built.");
            filter = null;
        }
        
        return filter;
    }
}
