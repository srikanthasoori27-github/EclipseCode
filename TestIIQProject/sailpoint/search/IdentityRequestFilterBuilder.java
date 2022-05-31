/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.SearchInputDefinition;
import sailpoint.persistence.Sequencer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.search.IdentityRequestSearchBean;
import sailpoint.web.search.SearchBean;

public class IdentityRequestFilterBuilder extends BaseFilterBuilder {
	private static final Log log = LogFactory.getLog(IdentityRequestFilterBuilder.class);
	
	public Filter getFilter() throws GeneralException {
		// This filter builder should always run for standard search. For advanced search, we're restricting it to
		// queries that contain a value and use the Equals/!Equals operator -- this allows some of the 'advanced' operators
		// such as 'is null' and 'like' to work correctly and as they would in a regular database query.
		boolean isStandardSearch = definition.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_IDENTITY_REQUEST);
		boolean isAdvancedSearch = definition.getSearchType().equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENTITY_REQUEST)
				&& !Util.isNullOrEmpty((String)value)
				&& (inputType.equals(SearchInputDefinition.InputType.Equal) || inputType.equals(SearchInputDefinition.InputType.NotEqual));

	    if(propertyName.equals(IdentityRequestSearchBean.COL_NAME) && (isStandardSearch || isAdvancedSearch)) {
	        value = getPaddedNameFilterValue((String)value);
	    }
	    return super.getFilter();
	}

	/**
	 * Get the padded value for the identity request name filter value, i.e.
	 * 0000000021 from 21
	 * @param original Original search value
	 * @return Actual padded value that will in name attribute
	 */
	public static String getPaddedNameFilterValue(String original) {
		//** zero pad to length of 10 **//
		return String.format("%0" + Sequencer.DEFAULT_LEFT_PADDING + "d", Long.parseLong(original));
	}

}
