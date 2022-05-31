/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;

/**
 * Created by ryan.pickens on 6/16/16.
 */
public class AlertAction extends SailPointObject {

    //Store this as XML
    AlertDefinition _alertDef;

    AlertDefinition.ActionType _actionType;

    //Id of the result object generated
    String _resultId;

    AlertActionResult _result;

    @XMLProperty
    public AlertDefinition getAlertDef() { return _alertDef; }

    public void setAlertDef(AlertDefinition def) { _alertDef = def; }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public AlertActionResult getResult() { return _result; }

    public void setResult(AlertActionResult res) { _result = res; }

    @XMLProperty
    public AlertDefinition.ActionType getActionType() { return _actionType; }

    public void setActionType(AlertDefinition.ActionType action) { _actionType = action; }

    @XMLProperty
    public String getResultId() { return _resultId; }

    public void setResultId(String s) { _resultId = s; }

    @Override
    public boolean hasName() {
        return false;
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @XMLClass
    public static class AlertActionResult extends AbstractXmlObject {
        //The ID of the result object
        String _resultId;
        //The name of the result Object
        String _resultName;

        //List of identity/group notified
        List<AlertNotification> _notifications;

        String _workflowName;

        @XMLProperty
        public String getResultId() { return _resultId; }
        public void setResultId(String s) { _resultId = s; }

        @XMLProperty
        public String getResultName() { return _resultName; }
        public void setResultName(String s ) { _resultName = s; }

        @XMLProperty(mode=SerializationMode.LIST)
        public List<AlertNotification> getNotifications() { return _notifications; }

        public void setNotifications(List<AlertNotification> notified) { _notifications = notified; }

        @XMLProperty
        public String getWorkflowName() { return _workflowName; }
        public void setWorkflowName(String s) { _workflowName = s; }


    }

    @XMLClass
    public static class AlertNotification extends AbstractXmlObject {
        String _displayName;
        List<String> _emailAddresses;
        String _name;

        @XMLProperty
        public String getDisplayName() {
            return _displayName;
        }

        public void setDisplayName(String displayName) {
            this._displayName = displayName;
        }

        @XMLProperty(mode=SerializationMode.LIST)
        public List<String> getEmailAddresses() {
            return _emailAddresses;
        }

        public void setEmailAddresses(List<String> emailAddress) {
            this._emailAddresses = emailAddress;
        }

        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String name) {
            this._name = name;
        }

    }
}
