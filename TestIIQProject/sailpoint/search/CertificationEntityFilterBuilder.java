/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;

public class CertificationEntityFilterBuilder extends BaseFilterBuilder {
	private static final Log log = LogFactory.getLog(CertificationEntityFilterBuilder.class);
	
	@Override 
	public Filter getJoin() {
		Filter join = Filter.join("id", "CertificationEntity.certification.id");
		return join;
	}

}
