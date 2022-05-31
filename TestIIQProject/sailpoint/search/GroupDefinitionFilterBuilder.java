/**
 * 
 */
package sailpoint.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.group.GroupFilterBean;
import sailpoint.web.group.GroupFilterBean.GroupFilter;
import sailpoint.web.util.FilterConverter;

/**
 * @author peter.holcomb
 *
 */
public class GroupDefinitionFilterBuilder extends BaseFilterBuilder {
    
    private static final Log log = LogFactory.getLog(GroupDefinitionFilterBuilder.class);
    
    @Override
    public Filter getFilter() throws GeneralException {
        Filter filter = null;
        
        GroupFilterBean bean = new GroupFilterBean(null, "", this.value.toString());
        List<Filter> filters = new ArrayList<Filter>();
        
        for(GroupFilter filterBean : bean.getFilters()) {
			if(filterBean.getDefinition()!=null) {
				try {
					GroupDefinition def = resolver.getObjectById(GroupDefinition.class, filterBean.getDefinition());
					if(def!=null) {
						filters.add(def.getFilter());
					}
				} catch(Exception e) {
					log.warn("Unable to load group definition for id: " + filterBean.getDefinition() + " Exception: " + e.getMessage());
				}
			}
		}
		
		if(filters.size()>1) {
			filter = Filter.and(filters);
		} else if(!filters.isEmpty()){
			filter = filters.get(0);
		}
		filter = FilterConverter.convertFilter(filter, Identity.class);
        return filter;
    }
    
    public Filter getJoin() throws GeneralException {
		return Filter.join("certifiers", "Identity.name");
	}

}
