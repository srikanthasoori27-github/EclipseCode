package sailpoint.web.analyze;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.workflow.WorkflowBean;

public class ProcessMetricsEditBean extends BaseBean {
    private static final Log log = LogFactory.getLog(ProcessMetricsEditBean.class);
    private String processToEdit;
    
    public String getProcessToEdit() {
        return processToEdit;
    }

    public void setProcessToEdit(String processToEdit) {
        this.processToEdit = processToEdit;
    }

    public String editProcess() {
        String result;
        
        if (processToEdit != null) {
            Map session = getSessionScope();
            String workflowId;
            
            try {
                Workflow processObj = getContext().getObjectByName(Workflow.class, processToEdit);
                if (processObj != null) {
                    String editedProcessId = processObj.getId();
                    // Force workflow initialization
                    session.put(WorkflowBean.ATT_OBJECT_ID, editedProcessId);
                    session.put(WorkflowBean.ATT_WORKFLOW, null);
                    result = "editProcess";
                } else {
                    result = "";
                }
            } catch (GeneralException e) {
                log.error("Failed to find a process named " + processToEdit, e);
                result = "";
            }
        } else {
            result = "";
        }
        
        return result;
    }
}
