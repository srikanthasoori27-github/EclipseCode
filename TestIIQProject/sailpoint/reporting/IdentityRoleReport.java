/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

public class IdentityRoleReport extends UserReport {
	
	private static final String GRID_REPORT = "IdentityRoleGridReport";

        @Override	
	public String getJasperClass() {
            String className = GRID_REPORT;
            return className;
        }
}
