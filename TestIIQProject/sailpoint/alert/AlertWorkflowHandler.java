/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.integration.Util;
import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ryan.pickens on 6/23/16.
 */
public class AlertWorkflowHandler implements AlertHandler {

    Log _log = LogFactory.getLog(AlertWorkflowHandler.class);

    String _workflowName;
    Map<String, Object> _workflowArgs;
    Workflower _workflower;


    public static final String ARG_ALERT_ID = "alertId";
    public static final String ARG_ALERT_DEF = "alertDefinition";

    public static final String WORKFLOW_HANDLER_LAUNCHER = "AlertProcessor";

    @Override
    public AlertAction handleAlert(Alert a, AlertDefinition def, SailPointContext ctx)
        throws GeneralException {
        AlertAction action = null;

        if (a == null) {
            _log.error("Null Alert");
            return null;
        }

        if (def == null) {
            _log.error("Null AlertDefinition");
            return null;
        }

        if (ctx == null) {
            _log.error("No Context Available");
            return null;
        }

        parseActionConfig(a, def);

        if (Util.isNullOrEmpty(_workflowName)) {
            _log.error("No Workflow specified in ActionConfig");
            throw new GeneralException("Not Workflow specified in ActionConfig for AlertDef[" + def.getName() + "]");
        }

        // Must set the target because WorkItem form expansion needs to
        // know how to get back to an Identity.
        WorkflowLaunch wflaunch = new WorkflowLaunch();
        wflaunch.setTarget(a);
        wflaunch.setWorkflowRef(_workflowName);
        wflaunch.setCaseName(_workflowName + " - Alert[" + a.getName() + "] AlertDefinition[" +
                def.getName() + "]");
        wflaunch.setLauncher(WORKFLOW_HANDLER_LAUNCHER);
        wflaunch.setVariables(_workflowArgs);
        //Do we want to have private context for this? -rap
        wflaunch.setPrivateSailPointContext(true);


        // not sure how important it is to reuse this
        if (_workflower == null)
            _workflower = new Workflower(ctx);


        try {
            wflaunch = _workflower.launch(wflaunch);
            WorkflowCase wfcase = wflaunch.getWorkflowCase();

            //Create the Alert Action
            action = new AlertAction();
            action.setAlertDef(def);
            action.setActionType(AlertDefinition.ActionType.WORKFLOW);
            action.setResultId(wflaunch.getTaskResult().getId());
            AlertAction.AlertActionResult result = new AlertAction.AlertActionResult();
            result.setResultId(wflaunch.getTaskResult().getId());
            result.setResultName(wflaunch.getTaskResult().getName());
            result.setWorkflowName(_workflowName);

            action.setResult(result);


            if (a.getId() != null) {
                //If alert already persisted,
                //Reattach alert in case WF decached. Should not happen since WF is launched in private context -rap
                a = ObjectUtil.reattach(ctx, a);
            }

            //Update the Alert
            a.addAction(action);
            a.setLastProcessed(new Date());

            ctx.saveObject(a);
            ctx.commitTransaction();

            AlertService.auditAlertAction(a, action, ctx);

        } catch (Exception e) {
            _log.error("Error Launching workflow for alert " + a.getName(), e);
            if (e instanceof GeneralException) {
                throw (GeneralException) e;
            }
        }

        return action;

    }

    protected void parseActionConfig(Alert a, AlertDefinition def) {
        AlertDefinition.ActionConfig cfg = def.getActionConfig();
        if (cfg == null) {
            _log.error("Null AlertDefinition ActionConfig");
            return;
        }

        Attributes atts = cfg.getAttributes();

        if (atts == null) {
            _log.error("No ActionConfig present");
            return;
        }

        _workflowName = atts.getString(AlertDefinition.ActionConfig.ARG_WORKFLOW_NAME);

        _workflowArgs = new HashMap<String, Object>();

        Map m = (Map)atts.get(AlertDefinition.ActionConfig.ARG_WORKFLOW_ARGS);
        if (m != null) {
            _workflowArgs.putAll(m);
        }

        _workflowArgs.put(ARG_ALERT_ID, a.getId());
        _workflowArgs.put(ARG_ALERT_DEF, def);


    }



}
