/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import sailpoint.integration.Util;
import sailpoint.object.AlertAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ryan.pickens on 8/30/16.
 */
public class AlertActionResultDTO {

    public AlertActionResultDTO(AlertAction.AlertActionResult result) {
        _resultId = result.getResultId();
        _resultName = result.getResultName();
        _workflowName = result.getWorkflowName();

        if (!Util.isEmpty(result.getNotifications())) {
            getNotificationDtos(result.getNotifications());
        }
    }

    String _resultId;

    String _resultName;

    String _workflowName;

    List<AlertNotificationDTO> _notifications;

    public List<AlertNotificationDTO> getNotifications() {
        return _notifications;
    }

    public void setNotifications(List<AlertNotificationDTO> notifications) {
        this._notifications = notifications;
    }

    public String getResultId() {
        return _resultId;
    }

    public void setResultId(String resultId) {
        this._resultId = resultId;
    }

    public String getResultName() {
        return _resultName;
    }

    public void setResultName(String resultName) {
        this._resultName = resultName;
    }

    public String getWorkflowName() {
        return _workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this._workflowName = workflowName;
    }

    protected void getNotificationDtos(List<AlertAction.AlertNotification> notifications) {
        for (AlertAction.AlertNotification notif : sailpoint.tools.Util.safeIterable(notifications)) {
            if (_notifications == null) {
                _notifications = new ArrayList<AlertNotificationDTO>();
            }
            _notifications.add(new AlertNotificationDTO(notif));
        }
    }


    public static class AlertNotificationDTO {
        String _displayName;
        String _name;
        List<String> _emails;

        public AlertNotificationDTO(AlertAction.AlertNotification notification) {
            _displayName = notification.getDisplayName();
            _name = notification.getName();
            _emails = notification.getEmailAddresses();
        }

        public String getDisplayName() {
            return _displayName;
        }

        public void setDisplayName(String displayName) {
            this._displayName = displayName;
        }

        public String getName() {
            return _name;
        }

        public void setName(String name) {
            this._name = name;
        }

        public List<String> getEmails() {
            return _emails;
        }

        public void setEmails(List<String> emails) {
            this._emails = emails;
        }
    }
}
