/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import net.sf.jasperreports.engine.JasperReport;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.tools.GeneralException;

public class BusinessRoleMembershipReport extends BusinessRoleReport {
	
	private static final String GRID_REPORT = "BusinessRoleMembershipGridReport";
	public static final String BUSINESS_ROLE_MEMBERSHIP_EXCEPTIONS_ARG = "membershipExceptions";

        @Override	
	public String getJasperClass() {
            return GRID_REPORT;
        }
	
	@Override
	public void preFill(SailPointContext ctx, Attributes<String, Object> args, JasperReport report)
	throws GeneralException {
        super.preFill(ctx, args, report);
		/** If the user has chosen to only show empty roles, we need to pass that down to the 
		 * datasource as an argument.  Can't do this with filters unfortunately
		 */
		String useExceptions = args.getString(BusinessRoleReport.BUSINESS_ROLE_EXCEPTIONS_ARG);
		if(useExceptions!=null) {
			args.put(BUSINESS_ROLE_MEMBERSHIP_EXCEPTIONS_ARG, useExceptions);
		}
	}

}
