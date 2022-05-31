/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.Workflower;
import sailpoint.authorization.TaskResultAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Argument;
import sailpoint.object.AuditEvent;
import sailpoint.object.Comment;
import sailpoint.object.Signature;
import sailpoint.object.Source;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.server.Auditor;
import sailpoint.service.BaseObjectService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import static sailpoint.tools.Message.localize;

/**
 * Created by ryan.pickens on 6/16/17.
 */
public class TaskResultService extends BaseObjectService<TaskResult> {

    private static final Log log = LogFactory.getLog(TaskResultService.class);

    private TaskResult taskResult;
    private String workItemId;
    private SailPointContext context;
    private UserContext userContext;


    public TaskResultService(String taskResultId, UserContext userContext) throws GeneralException {
        if (Util.isNullOrEmpty(taskResultId)) {
            throw new InvalidParameterException("taskResultId");
        }

        initContext(userContext);

        this.taskResult = this.context.getObjectById(TaskResult.class, taskResultId);
        if (this.taskResult == null) {
            throw new ObjectNotFoundException(TaskResult.class, taskResultId);
        }

        //TODO: Authorize in constructor?
        //authorize();
    }

    public TaskResultService(String taskResultIdOrName, UserContext context, String workItemId)
        throws GeneralException {

        this(taskResultIdOrName, context);
        this.workItemId = workItemId;
    }

    /**
     * Initialize the context
     * @param userContext UserContext
     * @throws GeneralException
     */
    private void initContext(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        this.userContext = userContext;
        this.context = userContext.getContext();

    }

    public void authorize() throws GeneralException {
        TaskResultAuthorizer authorizer = new TaskResultAuthorizer(this.taskResult, this.workItemId);
        authorizer.authorize(this.userContext);
    }

    @Override
    protected TaskResult getObject() {
        return this.taskResult;
    }

    @Override
    protected SailPointContext getContext() {
        return this.context;
    }

    @Override
    protected List<String> getAllowedPatchFields() {
        return null;
    }

    @Override
    protected boolean validateValue(String field, Object value) {
        return false;
    }

    @Override
    protected void patchValue(String field, Object value) {

    }

    //for ordering
    static final Integer ERROR      = 0;
    static final Integer RUNNING    = 2;
    static final Integer COMPLETED  = 4;
    static final Integer WARNING    = 6;
    static final Integer TERMINATED = 8;
    static final Integer WAITING    = 10;

    public ListResult getPartitionedResults(int start, int limit)
        throws GeneralException {

        TaskResult task = getObject();
        List<Map> allResults = new ArrayList<Map>();
        int totalCount = 0;
        if (task.isPartitioned()) {
            // read the partition result list
            List<TaskResult> partitions = task.getPartitionResults(getContext());

            if (partitions != null) {
                totalCount = partitions.size();
                for (TaskResult r : partitions) {
                    if (!isHiddenPartition(r)) {
                        Map resultMap = new HashMap();
                        resultMap.put("name", r.getName());
                        resultMap.put("host", r.getHost());
                        TaskResult.CompletionStatus completeStatus = r.getCompletionStatus();
                        if (completeStatus == null) {
                            if (r.getHost() != null) {
                                resultMap.put("statusInt", RUNNING);
                                resultMap.put("progress", r.getProgress());
                            } else {
                                resultMap.put("statusInt", WAITING);
                            }
                        } else if (TaskResult.CompletionStatus.Error == completeStatus) {
                            resultMap.put("statusInt", ERROR);
                            resultMap.put("attributes", r.getAttributes());
                        } else if (TaskResult.CompletionStatus.Success == completeStatus) {
                            resultMap.put("statusInt", COMPLETED);
                            resultMap.put("attributes", r.getAttributes());
                        } else if (TaskResult.CompletionStatus.Warning == completeStatus) {
                            resultMap.put("statusInt", WARNING);
                            resultMap.put("attributes", r.getAttributes());
                        } else if (TaskResult.CompletionStatus.Terminated == completeStatus) {
                            resultMap.put("statusInt", TERMINATED);
                            resultMap.put("attributes", r.getAttributes());
                        }

                        allResults.add(resultMap);
                    }
                }                    
            } else {
                log.warn("Null partitions inside parent task result:" + task.getId());
            }
        }

        allResults = getPagedPartitionedResults(allResults, start, limit, task);

        return new ListResult(allResults, totalCount);
    }
    
    private boolean isHiddenPartition(TaskResult partitionResult) {
        return Util.otob(partitionResult.get(TaskResult.ATT_HIDDEN_PARTITION));
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Map> getPagedPartitionedResults(List<Map> allResults, int start, int limit, TaskResult task)
            throws GeneralException {

        if (allResults == null || start > allResults.size()) {
            return Collections.emptyList();
        }

        //sort based on status and name
        Collections.sort(allResults, TASK_RESULT_SORTER);

        //get items for current page
        int endIndex = ((start+limit) < allResults.size()) ? start+limit : allResults.size();
        List<Map> paged = allResults.subList(start, endIndex);

        TaskDefinition def = getContext().getObjectById(TaskDefinition.class, task.getDefinition().getId());
        Signature sig = def.getEffectiveSignature();
        List<Argument> returns = sig == null ? null : sig.getReturns();

        //assign status string
        //populate stats map
        //only for paged items to impove performance
        for (Map result : paged) {

            Integer statusInt = (Integer) result.get("statusInt");
            String messageKey = null;
            if (statusInt == ERROR) {
                messageKey = MessageKeys.TASK_RESULT_ERR;
                Map attributes = (Map) result.get("attributes");
            } else if (statusInt == RUNNING) {
                if (result.get("progress") != null) {
                    result.put("status", result.get("progress"));
                } else {
                    messageKey = MessageKeys.TASK_RESULT_IN_PROGRESS;
                }
            } else if (statusInt == COMPLETED) {
                messageKey = MessageKeys.TASK_RESULT_SUCCESS;
            } else if (statusInt == WARNING) {
                messageKey = MessageKeys.TASK_RESULT_WARN;
            } else if (statusInt == TERMINATED) {
                messageKey = MessageKeys.TASK_RESULT_TERMINATED;
            } else if (statusInt == WAITING) {
                messageKey = MessageKeys.TASK_RESULT_WAITING;
            }
            if (messageKey != null) {
                result.put("status", localize(messageKey).getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone()) );
            }

            Map attributes = (Map) result.get("attributes");

            if (attributes != null && !attributes.isEmpty()) {
                Map<String, Object> stats = new LinkedHashMap<String, Object>();

                if (returns == null) {
                    //stats = attributes;
                } else {
                    for (Argument arg : returns) {
                        Object value = attributes.get(arg.getName());
                        if (value != null) {
                            stats.put(localize(arg.getDisplayLabel()).getLocalizedMessage(this.userContext.getLocale(),
                                    this.userContext.getUserTimeZone()), value);
                        }
                    }
                }

                result.put("stats", JsonHelper.toJson(stats));
            }

            //clean up
            result.remove("statusInt");
            result.remove("attributes");
            result.remove("progress");
        }

        return paged;
    }


    /**
     * Comparator for sorting partitioned task results.
     * Sorts on status, then partition number or name.  assume name is unique.
     */
    private static Comparator<Map> TASK_RESULT_SORTER =
            new Comparator<Map>(){


                public int compare(Map o1, Map o2) {
                    Integer s1 = (Integer) o1.get("statusInt");
                    Integer s2 = (Integer) o2.get("statusInt");

                    if (s1 == null || s2 == null) {
                        return 1;
                    } else if (s1.equals(s2)) {
                        String name1 = (String) o1.get("name");
                        String name2 = (String) o2.get("name");
                        if (name1 == null || name2 == null) {
                            return 1;
                        } else {
                            //Regular expression that will extract the first number
                            //from the following sentence "words 10 to 80 words", it
                            //will return "10"
                            String regex = "(?<= )([0-9]+)(?= to [0-9]+)";
                            Matcher m1 = Pattern.compile(regex).matcher(name1);
                            Matcher m2 = Pattern.compile(regex).matcher(name1);

                            Integer n1 = m1.find() ? Integer.valueOf(m1.group()) : null;
                            Integer n2 = m2.find() ? Integer.valueOf(m2.group()) : null;

                            if (n1 != null && n2 != null) {
                                //if we have arrived here, let's sort these two records
                                //by their natural order example, 1, 2, 3, 4, ...
                                return n1 - n2;
                            } else {
                                //if we cannot find a partition number, defaults to
                                //compare by name.
                                return name1.compareToIgnoreCase(name2);
                            }
                        }
                    } else {
                        return s1.compareTo(s2);
                    }
                }
            };


    public RequestResult getWorkflowSummary() throws GeneralException {
        WorkflowSummary summary = (WorkflowSummary) taskResult.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY);
        if (summary != null) {
            WorkflowSummaryDTO dto = new WorkflowSummaryDTO(summary, taskResult.isTerminated());
            return new ListResult(Arrays.asList(dto), 1);
        }
        return null;
    }

    public RequestResult cancelWorkflow(String comments) throws GeneralException {

        RequestResult result = new RequestResult();

        boolean terminated = false;

        if (taskResult.getCompleted() == null) {
            String caseId = (String) taskResult.getAttribute(WorkflowCase.RES_WORKFLOW_CASE);
            if (caseId != null) {
                WorkflowCase wfCase = getContext().getObjectById(WorkflowCase.class, caseId);
                Workflower workflower = new Workflower(getContext());
                workflower.terminate(wfCase);
                terminated = true;

                taskResult.setTerminated(true);
                taskResult.setCompleted(new Date());
                taskResult.setCompletionStatus(taskResult.calculateCompletionStatus());
                Comment comment = null;
                if ((comments != null) && (!comments.equals(""))) {
                    comment = new Comment();
                    comment.setComment(new Message(MessageKeys.COMMENT_TERMINATED_PREFIX).getLocalizedMessage() + " " + comments);
                    comment.setDate(new Date());
                    comment.setAuthor(userContext.getLoggedInUser().getDisplayableName());
                    taskResult.addMessage(new Message(Message.Type.Error, comment.toString()));
                }
                getContext().saveObject(taskResult);

                AuditEvent event = new AuditEvent();
                event.setSource(userContext.getLoggedInUserName());
                event.setTarget(taskResult.getTargetName());
                event.setTrackingId(wfCase.getWorkflow().getProcessLogId());
                event.setAction(AuditEvent.CancelWorkflow);
                // djs: for now set this in both places to avoid needing
                // to upgrade.  Once we have ui support for "interface"
                // we can remove the map version
                event.setAttribute("interface", Source.LCM.toString());
                event.setInterface(Source.LCM.toString());
                event.setAttribute(Workflow.VAR_TASK_RESULT, taskResult.getId());
                if (comment != null) {
                    // Storing as a list so we're consistent with other audit event types.
                    event.setAttribute("completionComments", Arrays.asList(comment));
                }
                Auditor.log(event);
                getContext().commitTransaction();

                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
        } else if (taskResult.getCompleted() != null) {
            // Somehow the task was completed before we got there.
            // Consider the request done and move on.
            terminated = true;
            result.setStatus(RequestResult.STATUS_SUCCESS);
        }

        if (!terminated) {
            result.setStatus(RequestResult.STATUS_FAILURE);
            // this is a edge case so not using a msg here
            result.addError("The workflow assigned to task result '" + taskResult + "' was not found.");
        }


        return result;
    }

    /**
     * Calls upon the TaskManager to terminate the current task result
     * This call to terminate a task result is asynchronous, so just call it
     * and return the terminated flag.
     *
     * @return True if the task was terminated successfully and False if the request to
     * terminate the task was sent.
     * @throws GeneralException
     */
    public boolean terminateTaskResult() throws GeneralException {
        TaskManager manager = new TaskManager(getContext());
        return manager.terminate(this.taskResult);
    }

    /**
     * Calls the TaskManager to send a restart command
     * @throws GeneralException
     */
    public void restartTaskResult() throws GeneralException {
        TaskManager manager = new TaskManager(getContext());
        manager.restart(this.taskResult);
    }

    /**
     * Calls the TaskManager to send a stack trace command
     * @throws GeneralException
     */
    public void requestStackTrace() throws GeneralException {
        TaskManager manager = new TaskManager(getContext());
        manager.sendStackCommand(getObject());
    }

    public RequestResult getProgress() throws GeneralException {
        RequestResult result;

        int percentComplete = taskResult.getPercentComplete();
        String progress = taskResult.getProgress();

        Map<String, Object> resultData = new HashMap<String, Object>();
        resultData.put("percentComplete", percentComplete);
        resultData.put("progress", progress);

        result = new ObjectResult(resultData);
        result.setStatus(RequestResult.STATUS_SUCCESS);

        return result;
    }

    public TaskResultDTO getTaskResultDTO() throws GeneralException {
        return new TaskResultDTO(this.taskResult);
    }

    
    /**
     * Returns paged list of TaskResult attribute value.
     * If the attribute value is not a List, empty list will be returned.
     * 
     * @param attributeName The name of the TaskResult attribute.
     * @param start The starting index
     * @param limit The size of the returned list
     * @return The paged list value of the TaskResult attribute.
     * @throws GeneralException
     */
    public ListResult getAttributeListValue(String attributeName, int start, int limit)
        throws GeneralException {

        TaskResult task = getObject();
        Object value = task.getAttribute(attributeName);
        
        if (value instanceof List) {
            List allResults = (List) value;
            //get items for current page
            int endIndex = ((start+limit) < allResults.size()) ? start+limit : allResults.size();
            List paged = allResults.subList(start, endIndex);
            
            return new ListResult(paged, allResults.size());
        } else {
            return ListResult.getInstance();
        }
        
    }

}
