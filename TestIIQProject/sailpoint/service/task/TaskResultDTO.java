/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import sailpoint.object.ColumnConfig;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskResult;
import sailpoint.service.BaseDTO;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 6/16/17.
 */
public class TaskResultDTO extends BaseDTO {

    TaskResult.CompletionStatus completionStatus;
    String host;
    String name;
    String targetClass;
    Type type;

    String targetId;
    String targetName;

    Boolean terminated;
    Boolean canRestart;

    Integer pendingSignoffs;
    Integer runLength;
    Integer runLengthAverage;
    Integer runLengthDeviation;

    //TODO: Do we want date objects or strings? BaseListService converts Date to String
    Date launched;
    Date completed;
    Date modified;

    IdentitySummaryDTO owner;
    //Going to use launcher for now. Launcher/owner will usually be the same, but owner
    // will not correlate to System/Console/etc.
    String launcher;

    String stack;

    public TaskResultDTO(UserContext context, Map<String,Object> result, List<ColumnConfig> cols)
            throws GeneralException {
        super(result, cols);
        if (getRunLength() == null || getRunLength() <= 0) {
            //See if we can calculate
            if (this.getCompleted() == null && this.getLaunched() != null) {
                setRunLength((int)((new Date().getTime() - getLaunched().getTime())/1000));
            }
        }

    }

    public TaskResultDTO(TaskResult result) {
        if (result != null) {
            setId(result.getId());
            setName(result.getName());
            setHost(result.getHost());
            setCompletionStatus(result.getCompletionStatus());
            setTargetClass(result.getTargetClass());
            setTargetId(result.getTargetId());
            setTargetName(result.getTargetName());
            setTerminated(result.isTerminated());
            setPendingSignoffs(result.getPendingSignoffs());
            setRunLength(result.getRunLength());
            if (result.getCompleted() == null && result.getRunLength() <= 0) {
                //Not yet completed
                if (result.getLaunched() != null) {
                    //Set runlength to diff between now and when it was launched
                    setRunLength((int)(new Date().getTime() - result.getLaunched().getTime())/1000);
                }
            }
            setRunLengthAverage(result.getRunLengthAverage());
            setRunLengthDeviation(result.getRunLengthDeviation());
            //Convert Date to String? Not sure i like how BaseListService converts these to Strings
            setLaunched(result.getLaunched());
            setCompleted(result.getCompleted());
            setModified(result.getModified());
            setOwner(new IdentitySummaryDTO(result.getOwner()));
            setStack(result.getStack());
            setLauncher(result.getLauncher());
            setType(result.getType());
            setCanRestart(result.canRestart());
        }

    }

    public TaskResult.CompletionStatus getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(TaskResult.CompletionStatus completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public Boolean isTerminated() {
        return terminated;
    }

    public void setTerminated(Boolean terminated) {
        this.terminated = terminated;
    }

    public Integer getPendingSignoffs() {
        return pendingSignoffs;
    }

    public void setPendingSignoffs(Integer pendingSignoffs) {
        this.pendingSignoffs = pendingSignoffs;
    }

    public Integer getRunLength() {
        return runLength;
    }

    public void setRunLength(Integer runLength) {
        this.runLength = runLength;
    }

    public Integer getRunLengthAverage() {
        return runLengthAverage;
    }

    public void setRunLengthAverage(Integer runLengthAverage) {
        this.runLengthAverage = runLengthAverage;
    }

    public Integer getRunLengthDeviation() {
        return runLengthDeviation;
    }

    public void setRunLengthDeviation(Integer runLengthDeviation) {
        this.runLengthDeviation = runLengthDeviation;
    }

    public Date getLaunched() {
        return launched;
    }

    public void setLaunched(Date launched) {
        this.launched = launched;
    }

    public Date getCompleted() {
        return completed;
    }

    public void setCompleted(Date completed) {
        this.completed = completed;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
        this.modified = modified;
    }

    public IdentitySummaryDTO getOwner() {
        return owner;
    }

    public void setOwner(IdentitySummaryDTO owner) {
        this.owner = owner;
    }

    public String getStack() {
        return stack;
    }

    public void setStack(String stack) {
        this.stack = stack;
    }

    public String getLauncher() { return launcher; }

    public void setLauncher(String s) { launcher = s; }

    public void setCanRestart(Boolean canRestart) { this.canRestart = canRestart; }
    public Boolean isCanRestart() { return this.canRestart; }

}
