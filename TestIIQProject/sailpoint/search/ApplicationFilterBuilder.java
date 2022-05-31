/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;

public class ApplicationFilterBuilder extends BaseFilterBuilder {
	private static final Log log = LogFactory.getLog(ApplicationFilterBuilder.class);
	
	@Override 
	public Filter getJoin() {
		Filter join = Filter.join("applicationId", "Application.id");
		return join;
	}
	
	/** Build Equals Filter **/
	protected Filter getEQFilter() {
		
		Filter filter = null;
		if (value instanceof Application)
			filter= (LeafFilter) Filter.eq(propertyName, (((Application)value).getId()));

		else
			filter= (LeafFilter) Filter.eq(propertyName, value);

		return filter;
	}

}
