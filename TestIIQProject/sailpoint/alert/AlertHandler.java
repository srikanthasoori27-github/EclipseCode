/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.AlertDefinition;
import sailpoint.tools.GeneralException;

/**
 * Interface for handling alerts with matching AlertDefinition
 * Created by ryan.pickens on 6/23/16.
 */
public interface AlertHandler {

    AlertAction handleAlert(Alert a, AlertDefinition def, SailPointContext ctx) throws GeneralException;

}
