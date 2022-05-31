package sailpoint.object;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class WorkflowTarget extends SailPointObject {

    private static final long serialVersionUID = 1L;

    private String className;
    private String objectId;
    private String objectName;
    private WorkflowCase workflowCase;

    @XMLProperty
    public String getClassName() {
        return this.className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @XMLProperty
    public String getObjectId() {
        return this.objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    @XMLProperty
    public String getObjectName() {
        return this.objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname="TargetWorkflowCase")
    public WorkflowCase getWorkflowCase() {
        return this.workflowCase;
    }

    public void setWorkflowCase(WorkflowCase workflowCase) {
        this.workflowCase = workflowCase;
    }

}
