/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.FilterConverter;

public class IpopFilterBuilder extends BaseFilterBuilder {
	private static final Log log = LogFactory.getLog(IpopFilterBuilder.class);

	@Override 
	public Filter getJoin() {
		Filter join = Filter.join("identityName", "Identity.name");
		return join;
	}
	
	/** Build Equals Filter **/
	@Override
	protected Filter getEQFilter() {
		Filter filter = null;
		try {

			GroupDefinition groupDef = getResolver().getObjectByName(GroupDefinition.class, (String)value);
	        
			Filter identityFilter = groupDef.getFilter();
			if (identityFilter != null && (getScope() != null && getScope() != Identity.class)) {
			    // i.e., it is being called to be used with another class such as ApplicationActivity.class
				filter = FilterConverter.convertFilter(identityFilter, Identity.class);
			} else {
			    filter = identityFilter;
			}
		} catch(GeneralException ge) {
			log.warn("Unable to retrieve group definition for IPOP filter: " + ge.getMessage());
		}
		
		return filter;
	}
}
