/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class TaskEvent extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Phase Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Events marked with this phase will be processed once
     * the task is completed.
     */
    public static final String PHASE_COMPLETION = "Completion";

    //////////////////////////////////////////////////////////////////////
    //
    // Rule result keys
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Key used to return an updated task result from Rule execution.
     */
    public static final String RULE_RETURN_TASK_RESULT= "taskResult";

    //////////////////////////////////////////////////////////////////////
    //
    // Attributes
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ATTR_EMAIL_RECIP= "completionEmailRecipient";

    private TaskResult taskResult;
    private String phase;
    private Rule rule;
    private Attributes<String,Object> attributes;

    public TaskEvent() {
    }

    public TaskEvent(TaskResult taskResult, String phase) {
        this.taskResult = taskResult;
        this.phase = phase;
    }

    @XMLProperty
    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public TaskResult getTaskResult() {
        return taskResult;
    }

    @XMLProperty(xmlname="EventTaskResult",mode = SerializationMode.REFERENCE)
    public void setTaskResult(TaskResult taskResult) {
        this.taskResult = taskResult;
    }

    public Rule getRule() {
        return rule;
    }

    @XMLProperty(xmlname="EventRule",mode = SerializationMode.REFERENCE)
    public void setRule(Rule rule) {
        this.rule = rule;
    }

    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String name, Object value){
        if (attributes == null)
            attributes = new Attributes<String, Object>();

        attributes.put(name, value);
    }

    public Object getAttribute(String name){
        return attributes != null ? attributes.get(name) : null;
    }
    
    @Override
    public boolean hasName() {
        return false;
    }
}
