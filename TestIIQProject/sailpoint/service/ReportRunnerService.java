/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.SPRight;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskDefinition.ResultAction;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

public class ReportRunnerService {

    private static final Log log = LogFactory.getLog(ReportRunnerService.class);

    /**
     * Kicks off a report execution and returns the result id.
     *
     * @param SailPointContext context to hit the database with
     * @param String the name of the report to run
     * @param definitionArgs the arguments to use for the report
     * @return the id of the report result
     */
    public String runReport(UserContext userContext,
            String reportName,
            Attributes<String, Object> definitionArgs) throws Exception {
        SailPointContext context = userContext.getContext();
        final String automatedReportName = "Automated " + reportName + " for " + context.getUserName();
        TaskDefinition reportOutline;
        TaskDefinition newReport;
        // see if user has pressed export button before
        newReport = context.getObjectByName(TaskDefinition.class, automatedReportName);
        if (null == newReport) {
            // report doesn't exist create new one
            newReport = new TaskDefinition();
            reportOutline = context.getObjectByName(TaskDefinition.class, reportName);
            if (null == reportOutline) {
                throw new GeneralException("Invalid report name");
            }
            newReport.setParent(reportOutline);
            newReport.setName(automatedReportName);
            newReport.setResultAction(ResultAction.RenameNew);
            newReport.setType(Type.LiveReport);
            transferRights(newReport, reportOutline);
        }
        newReport.setHidden(true);
        newReport.setArguments(definitionArgs);
        newReport.setTemplate(false);
        newReport.setOwner(userContext.getLoggedInUser());
        context.saveObject(newReport);
        context.commitTransaction();

        Attributes<String, Object> scheduleArgs = new Attributes<String, Object>();

        TaskManager manager = new TaskManager(context);
        manager.setLauncher(context.getUserName());
        TaskResult result = manager.runWithResult(newReport, scheduleArgs);
        return result.getId();
    }

    private void transferRights(TaskDefinition target, TaskDefinition source) {
        List<SPRight> rights = source.getRights();
        for (SPRight right : rights) {
            target.add(right);
        }
    }
}
