/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import sailpoint.reporting.datasource.BaseCertificationDataSource;
import sailpoint.reporting.datasource.CertificationSignoffReportDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.GroupDefinition;

import java.util.List;

/**
 * Provides details on certification sign-offs so users can
 * determine who signed-off on a certification and when the sign-off was completed.
 */
public class CertificationSignoffReport extends BaseCertificationReport{

    private static final String ATTRIBUTE_APPLICATIONS = "applications";
    private static final String ATTRIBUTE_MANAGERS = "managers";
    private static final String ATTRIBUTE_GROUPS = "groups";
    private static final String ATTRIBUTE_SIGNERS = "signers";

    protected BaseCertificationDataSource internalCreateDataSource() throws GeneralException {

        Attributes<String,Object> args = getInputs();

        List<String> applicationIds =  ReportParameterUtil.splitAttributeValue(args.getString(ATTRIBUTE_APPLICATIONS));
        List<String> managerNames =  ReportParameterUtil.getNames(getContext(), Identity.class,
                args.getString(ATTRIBUTE_MANAGERS));
        List<String> groupNames =  ReportParameterUtil.getNames(getContext(), GroupDefinition.class,
                args.getString(ATTRIBUTE_GROUPS));
        List<String> signerIds =  ReportParameterUtil.splitAttributeValue(args.getString(ATTRIBUTE_SIGNERS));

        CertificationSignoffReportDataSource datasource = new CertificationSignoffReportDataSource(_locale, _timezone);
        datasource.setApplications(applicationIds);
        datasource.setManagers(managerNames);
        datasource.setGroups(groupNames);
        datasource.setSigners(signerIds);

        return datasource;

    }


}
