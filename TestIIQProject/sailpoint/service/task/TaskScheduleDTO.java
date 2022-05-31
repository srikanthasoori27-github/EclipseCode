/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.service.BaseDTO;
import sailpoint.tools.GeneralException;

import java.util.Date;

/**
 * Created by ryan.pickens on 7/3/17.
 */
public class TaskScheduleDTO extends BaseDTO {

    String name;
    String definitionName;
    String host;
    Date lastExecution;
    Date nextExecution;
    Date nextActualExecution;
    Date resumeDate;
    int runLengthAverage;
    TaskResult.CompletionStatus latestResult;
    String launcher;
    Type type;


    public TaskScheduleDTO(TaskSchedule schedule) throws GeneralException {
        if (schedule != null) {
            setName(schedule.getName());
            setHost(schedule.getHost());
            setLastExecution(schedule.getLastExecution());
            setNextExecution(schedule.getNextExecution());
            setNextActualExecution(schedule.getNextActualExecution());
            setResumeDate(schedule.getResumeDate());
            if (schedule.getDefinition() != null) {
                setDefinitionName(schedule.getDefinition().getName());
                setRunLengthAverage(schedule.getDefinition().getRunLengthAverage());
                setType(schedule.getDefinition().getType());
            }
            setLatestResult(schedule.getLatestResultStatus());
            setLauncher(schedule.getLauncher());
        }
    }


    public String getName() {
        return name;
    }

    public void setDefinitionName(String name) {
        this.definitionName = name;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Date getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(Date lastExecution) {
        this.lastExecution = lastExecution;
    }

    public Date getNextActualExecution() {
        return nextActualExecution;
    }

    public void setNextActualExecution(Date nextExecution) {
        this.nextActualExecution = nextExecution;
    }

    public Date getNextExecution() {
        return nextExecution;
    }

    public void setNextExecution(Date nextExecution) {
        this.nextExecution = nextExecution;
    }

    public int getRunLengthAverage() {
        return runLengthAverage;
    }

    public void setResumeDate(Date d) { this.resumeDate = d; }

    public Date getResumeDate() { return this.resumeDate; }

    public void setRunLengthAverage(int runLengthAverage) {
        this.runLengthAverage = runLengthAverage;
    }

    public TaskResult.CompletionStatus getLatestResult() {
        return latestResult;
    }

    public void setLatestResult(TaskResult.CompletionStatus latestResult) {
        this.latestResult = latestResult;
    }

    public String getLauncher() {
        return launcher;
    }

    public void setLauncher(String owner) {
        this.launcher = owner;
    }


    public Type getType() {
        return type;
    }


    public void setType(Type type) {
        this.type = type;
    }


}
