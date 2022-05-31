/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import sailpoint.object.Filter;
import sailpoint.object.Resolver;
import sailpoint.object.SearchInputDefinition;
import sailpoint.tools.GeneralException;

public interface FilterBuilder {
	
	public Filter getFilter() throws GeneralException;
	
	public void setScope(Class<?> val);
	
	/** returns any necessary join needed to use the filter **/
	public Filter getJoin() throws GeneralException;
	
	public void setDefinition(SearchInputDefinition definition);
	
	public void setValue(Object value);
	
	public void setResolver(Resolver r);

}
