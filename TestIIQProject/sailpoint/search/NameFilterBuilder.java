/**
 * 
 */
package sailpoint.search;

import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class NameFilterBuilder extends BaseFilterBuilder {

    private static final String ATTR_NAME = "name";
    private static final String ATTR_LASTNAME = "lastname";
    private static final String ATTR_FIRSTNAME = "firstname";
    
    @Override
    public Filter getFilter() throws GeneralException {
        Filter filter = null;
        
        if (value != null && ((String)value).trim().length() > 0) {
            String[] parts = ((String)value).split(" ");
            if(parts.length==2) {
                filter = Filter.and(Filter.like(ATTR_LASTNAME, parts[1], MatchMode.START), Filter.like(ATTR_FIRSTNAME, parts[0], MatchMode.START));
            } else {
                filter = Filter.or(Filter.like(ATTR_NAME, value, MatchMode.START), 
                        Filter.like(ATTR_LASTNAME, value, MatchMode.START), 
                        Filter.like(ATTR_FIRSTNAME, value, MatchMode.START));
            }
        }    
        
        return filter;
    }

}
