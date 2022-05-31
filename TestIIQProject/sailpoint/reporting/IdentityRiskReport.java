/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

public class IdentityRiskReport extends UserReport {
	
	private static final String GRID_REPORT = "IdentityRiskGridReport";

        @Override	
	public String getJasperClass() {
            return GRID_REPORT;
        }	
}
