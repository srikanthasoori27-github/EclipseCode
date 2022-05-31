/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import sailpoint.object.AlertDefinition;

/**
 * Created by ryan.pickens on 7/5/16.
 */
public class AlertHandlerFactory {

    public AlertHandler getAlertHandler(AlertDefinition definition) {
        if (definition == null || definition.getActionConfig() == null) {
            return null;
        }
        switch (definition.getActionConfig().getActionType()) {
            case WORKFLOW:
                return new AlertWorkflowHandler();
            case CERTIFICATION:
                return new AlertCertificationHandler();
            case NOTIFICATION:
                return new AlertNotificationHandler();
            default:
                return null;
        }
    }
}
