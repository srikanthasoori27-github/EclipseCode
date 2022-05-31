/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.TaskResultAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.AlertAction;
import sailpoint.object.Certification;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;


/**
 * Created by ryan.pickens on 8/29/16.
 */
public class AlertActionDTO {

    private static Log _log = LogFactory.getLog(AlertActionDTO.class);

    String _actionType;

    String _actionTypeDisplay;

    String _resultId;

    String _created;

    String _definitionName;

    AlertActionResultDTO _result;

    boolean _canViewCertification;

    boolean _canViewTaskResult;

    public AlertActionDTO(AlertAction action, UserContext context) {
        if (action != null) {
            _actionType = action.getActionType().name();
            _actionTypeDisplay = new Message(action.getActionType().getMessageKey()).getLocalizedMessage(context.getLocale(),
                    context.getUserTimeZone());
            _resultId = action.getResultId();
            _result = getResultDto(action, context);
            _created = action.getCreated() != null ? String.valueOf(action.getCreated().getTime()) : null;
            _definitionName = action.getAlertDef().getName();
            getUserRights(action, context);
        }
    }

    public String getActionType() { return _actionType; }

    public void setActionType(String s) { _actionType = s; }

    public String getActionTypeDisplay() { return _actionTypeDisplay; }

    public void setActionTypeDisplay(String s) { _actionTypeDisplay = s; }

    public String getResultId() { return _resultId; }

    public void setResultId(String s) { _resultId = s; }

    public AlertActionResultDTO getResult() { return _result; }

    public void setResult(AlertActionResultDTO res) { _result = res; }

    public String getCreated() { return _created; }

    public void setCreated(String s) { _created = s; }

    public String getDefinitionName() { return _definitionName; }

    public void setDefinitionName(String s) { _definitionName = s; }

    public boolean isCanViewCertification() { return _canViewCertification; }

    public void setCanViewCertification(boolean b) { _canViewCertification = b; }

    public boolean isCanViewTaskResult() { return _canViewTaskResult; }

    public void setCanViewTaskResult(boolean b) { _canViewTaskResult = b; }

    /**
     * Determine if the result is found, and the user has access to the result.
     * @param action
     * @param context
     */
    protected void getUserRights(AlertAction action, UserContext context) {

        try {
            switch (action.getActionType()) {
                case CERTIFICATION:
                    Certification cert = context.getContext().getObjectById(Certification.class, action.getResultId());
                    if (cert != null) {
                        _canViewCertification = CertificationAuthorizer.isAuthorized(cert, "", context);
                    }
                    break;
                case WORKFLOW:
                    TaskResult result = context.getContext().getObjectById(TaskResult.class, action.getResultId());
                    if (result != null) {
                        _canViewTaskResult = true;
                        try {
                            new TaskResultAuthorizer(result).authorize(context);
                        } catch (UnauthorizedAccessException uae) {
                            _canViewTaskResult = false;
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (GeneralException ge) {
            _log.error("Error getting User Rights" + ge);
        }
    }

    protected AlertActionResultDTO getResultDto(AlertAction action, UserContext context) {
        AlertActionResultDTO dto = null;
        if (action != null) {
            dto = new AlertActionResultDTO(action.getResult());
        }

        return dto;
    }
}
