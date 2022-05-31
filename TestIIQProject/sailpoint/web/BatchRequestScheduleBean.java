/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.primefaces.model.UploadedFile;


import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.BatchRequest;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.task.BatchRequestTaskExecutor;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.NavigationHistory;
/**
 *
 * Bean used for creating and scheduling batch requests.
 *
 */
public class BatchRequestScheduleBean extends BaseTaskBean<TaskSchedule> {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    private static final String BATCH_REQUEST_TASK_DEF_NAME = "Batch Request Processing Task";

    private static final Log log = LogFactory.getLog(BatchRequestScheduleBean.class);

    UploadedFile batchFile;

    boolean runNow = true;
    boolean ignoreErrors = true;

    // Skip requests that require additional info through forms
    boolean skipProvisioningForms = true;

    // Skip requests that open additional work items
    boolean skipManualWorkItems = true;

    boolean handleExistingCreate = true; // handle create as modify identity requests if identity already exists

    String policyScheme = "none";

    int stopNumber = 0;

    boolean generateIdentityRequests = false;

    Date runDate = new Date();

    BatchRequest batchRequest;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public BatchRequestScheduleBean() throws GeneralException, ParseException {
        super();

    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Create a batch request from the uploaded file and
     * initialize a task schedule to run it using the specified config options
     *
     * If the batch request is set to run now the task manager is invoked to run the task schedule.
     * Otherwise, the batch request task schedule is saved off and scheduled for the specified run date.
     *
     * The task schedule is setup with an arg that identifies the batch request by id.
     *
     * @return
     * @throws GeneralException
     * @throws IOException
     */
    public String save() throws GeneralException, IOException {

        if (batchFile == null || Util.isNullOrEmpty(batchFile.getFileName())) {
            // no batch file specified
            throw new GeneralException("No batch file specified");
        }

        batchRequest = createBatchRequest();

        // Need to save here so that we can get the id in the next step
        getContext().saveObject(batchRequest);

        // Create TaskSchedule to run BatchRequestTask
        TaskSchedule batchRequestTaskSchedule = setupTaskSchedule();

        // If run now
        if (runNow) {
            batchRequest.setRunDate(new Date());
            batchRequest.setStatus(BatchRequest.Status.Running);
            getContext().commitTransaction();
            TaskManager tm = new TaskManager(getContext());
            tm.runNow(batchRequestTaskSchedule);
        }
        else {
            // Generate cron string for scheduled run date
            CronString cs = new CronString(runDate, CronString.FREQ_ONCE);
            String generatedCronExpression = cs.toString();
            batchRequestTaskSchedule.addCronExpression(generatedCronExpression);
            batchRequestTaskSchedule.setNextExecution(runDate);
            getContext().saveObject(batchRequestTaskSchedule);

            batchRequest.setStatus(BatchRequest.Status.Scheduled);
            batchRequest.setRunDate(runDate);
        }

        getContext().saveObject(batchRequest);
        getContext().commitTransaction();

        return "batchRequestList";
    }

    public String cancel() {
        cleanSession();
        String result = NavigationHistory.getInstance().back();

        if (result == null) {
            result = "cancel";
        }

        return result;
    }

    /**
     *
     * @return
     * @throws IOException
     * @throws GeneralException
     */
    private BatchRequest createBatchRequest() throws IOException, GeneralException {

        // Get batch file info and contents to create BatchRequest object
        String fileName = batchFile.getFileName();
        byte[] content = batchFile.getContents();

        // Create and save batch request object
        BatchRequest newBatchRequest = new BatchRequest();

        String fileContentString = new String(content);
        String[] lines = fileContentString.split("\r\n|\r|\n");

        List<String> filteredLines = new ArrayList<String>();

        // filter out empty lines
        for (int i=0; i<lines.length; ++i) {
            if (Util.isNotNullOrEmpty(lines[i])) {
                filteredLines.add(lines[i]);
            }
        }

        newBatchRequest.setOwner(getLoggedInUser());
        newBatchRequest.setCreated(new Date());
        newBatchRequest.setFileName(fileName);
        newBatchRequest.setRecordCount(filteredLines.size()-1);
        newBatchRequest.setFileContents(Util.join(filteredLines, "\n"));

        // Set args
        Attributes<String, Object> configs = new Attributes<String, Object>();
        configs.put("runNow", runNow);
        configs.put("runDate", runDate);
        configs.put("handleExistingCreate", handleExistingCreate);
        configs.put("ignoreErrors", ignoreErrors);
        configs.put("stopNumber", stopNumber);
        configs.put("skipProvisioningForms", skipProvisioningForms);
        configs.put("skipManualWorkItems", skipManualWorkItems);
        configs.put("generateIdentityRequests", generateIdentityRequests);

        configs.put("policyScheme", policyScheme);

        newBatchRequest.setRunConfig(configs);

        return newBatchRequest;
    }

    /**
     * Setup a task schedule to run the batch request.
     * The batch request id is passed into the task schedule as an arg, "batchRequestId".
     *
     * @return
     * @throws GeneralException
     */
    private TaskSchedule setupTaskSchedule() throws GeneralException {

        TaskSchedule batchRequestTaskSchedule = new TaskSchedule();

        TaskDefinition batchRequestTask = getContext().getObjectByName(TaskDefinition.class, BATCH_REQUEST_TASK_DEF_NAME);

        // Task may not have been imported
        if (batchRequestTask == null) {
            throw new GeneralException("Batch processing task not found.");
        }

        // enable concurrent runs
        batchRequestTask.setConcurrent(true);

        // Set the task definition
        batchRequestTaskSchedule.setDefinitionName(batchRequestTask.getName());

        // Set the task schedule name. Added filename and date to make it unique.
        batchRequestTaskSchedule.setName(batchRequestTask.getName() + " - " + batchRequest.getFileName() + " - " + new Date());
        batchRequestTaskSchedule.setDescription(batchRequestTask.getDescription());

        // Set the batch request id argument for the task schedule.
        batchRequestTaskSchedule.setArgument(BatchRequestTaskExecutor.ARG_BATCH_REQUEST, batchRequest.getId());

        return batchRequestTaskSchedule;
    }

    /**
     * Validate run date when run later is selected.
     *
     * @param context
     * @param component
     * @param value
     * @throws ValidatorException
     */
    public void validateRunDate(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        boolean runNowParam = Util.atob(getRequestParameter("editForm:runNow"));

        if (value==null || runNowParam) {
            return;
        }

        if((Calendar.getInstance().getTime().after((Date)value)))
        {
            FacesMessage message = new FacesMessage();
            message.setSeverity(FacesMessage.SEVERITY_ERROR);
            throw new ValidatorException(message);
        }
    }

    /**
     * Validate the stop on errors number. Should be between 0 and 999. Only check if not ignoring errors.
     *
     * @param context
     * @param component
     * @param value
     * @throws ValidatorException
     */
    public void validateStopNumber(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        boolean errorHandlingParam = Util.atob(getRequestParameter("editForm:errorHandling"));

        if (value == null || errorHandlingParam) {
            return;
        }

        Integer stopNumber = (Integer)value;

        if (stopNumber < 1 || stopNumber > 999) {
            FacesMessage message = new FacesMessage();
            message.setSeverity(FacesMessage.SEVERITY_ERROR);
            throw new ValidatorException(message);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean isHandleExistingCreate() {
        return handleExistingCreate;
    }

    public void setHandleExistingCreate(boolean handleExistingCreate) {
        this.handleExistingCreate = handleExistingCreate;
    }

    public boolean isGenerateIdentityRequests() {
        return generateIdentityRequests;
    }

    public void setGenerateIdentityRequests(boolean opt) {
        this.generateIdentityRequests = opt;
    }

    public int getStopNumber() {
        return stopNumber;
    }

    public void setStopNumber(int stopNumber) {
        this.stopNumber = stopNumber;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public void setIgnoreErrors(boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }

    public UploadedFile getBatchFile() {
        return batchFile;
    }

    public void setBatchFile(UploadedFile batchFile) {
        this.batchFile = batchFile;
    }

    public boolean isRunNow() {
        return runNow;
    }

    public void setRunNow(boolean runNow) {
        this.runNow = runNow;
    }

    public Date getRunDate() {
        return runDate;
    }

    public void setRunDate(Date runDate) {
        this.runDate = runDate;
    }

    public boolean isSkipProvisioningForms() {
        return skipProvisioningForms;
    }

    public void setSkipProvisioningForms(boolean skipProvisioningForms) {
        this.skipProvisioningForms = skipProvisioningForms;
    }

    public boolean isSkipManualWorkItems() {
        return skipManualWorkItems;
    }

    public void setSkipManualWorkItems(boolean skipManualWorkItems) {
        this.skipManualWorkItems = skipManualWorkItems;
    }

    public String getPolicyScheme() {
        return policyScheme;
    }

    public void setPolicyScheme(String policyScheme) {
        this.policyScheme = policyScheme;
    }
}
