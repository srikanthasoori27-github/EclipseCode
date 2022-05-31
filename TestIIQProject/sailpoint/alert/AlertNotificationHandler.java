/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 7/5/16.
 */
public class AlertNotificationHandler implements AlertHandler {

    Log _log = LogFactory.getLog(AlertNotificationHandler.class);

    List<String> _emailAddresses;
    List<AlertAction.AlertNotification> _notifications = new ArrayList<>();
    EmailTemplate _emailTemplate;
    Map<String,Object> _emailVars;
    SailPointContext _context;



    public static final String ARG_EMAIL_VAR_ALERT = "alert";
    public static final String ARG_EMAIL_VAR_ALERT_DEF = "alertDefinitionName";
    public static final String ARG_EMAIL_VAR_ERROR_MSG = "errorMessage";


    @Override
    public AlertAction handleAlert(Alert a, AlertDefinition def, SailPointContext ctx)
        throws GeneralException {

        AlertAction action = null;

        if (def == null) {
            _log.error("AlertDefintiion must not be null");
            throw new GeneralException("Null AlertDefinition encountered");
        }

        if (a == null) {
            _log.error("Alert must not be null");
            throw new GeneralException("Null Alert encountered");
        }

        if (ctx == null) {
            _log.error("No Context supplied");
            throw new GeneralException("No Context supplied");
        }

        _context = ctx;
        try {

            processArguments(a, def, null);

            boolean success = sendNotification();

            //TODO: Should we set this regardless of any possible errors in the Notificaion processing? - rap
            a.setLastProcessed(new Date());
            if (success) {
                //Only create action if notification was successful
                action = new AlertAction();
                action.setActionType(AlertDefinition.ActionType.NOTIFICATION);
                AlertAction.AlertActionResult res = new AlertAction.AlertActionResult();
                res.setNotifications(_notifications);
                action.setResult(res);
                action.setAlertDef(def);

                a.addAction(action);
            }

            _context.saveObject(a);
            _context.commitTransaction();

            AlertService.auditAlertAction(a, action, ctx);


        } catch (GeneralException ge) {
            _log.error("Error sending notifications", ge);
            throw ge;
        }

        return action;
    }

    public void sendNotificationsForAction(AlertAction action, Alert a, AlertDefinition def, SailPointContext ctx,
                                           Message errorMessage)
            throws GeneralException {

        if (def == null) {
            _log.error("AlertDefintiion must not be null");
            throw new GeneralException("Null AlertDefinition encountered");
        }

        if (a == null) {
            _log.error("Alert must not be null");
            throw new GeneralException("Null Alert Encountered");
        }

        if (ctx == null) {
            _log.error("No Context supplied");
            throw new GeneralException("Not Context supplied");
        }

        _context = ctx;

        try {
            processArguments(a, def, errorMessage);

            sendNotification();

            if (action == null) {
                //Failed to create an action in the Primary handler, create an action for the notification
                action = new AlertAction();
                action.setAlertDef(def);
                action.setResult(new AlertAction.AlertActionResult());
                a.addAction(action);
                _context.saveObject(a);

            }
            AlertAction.AlertActionResult res = action.getResult();
            if (res != null) {
                res.setNotifications(_notifications);
            }

            _context.saveObject(action);
            _context.commitTransaction();
        } catch (GeneralException ge) {
            _log.error("Error sending notifications", ge);
            throw ge;
        }

    }

    protected void processArguments(Alert a, AlertDefinition def, Message errorMessage)
            throws GeneralException {

        AlertDefinition.ActionConfig cfg = def.getActionConfig();

        if (cfg == null || cfg.getAttributes() == null) {
            _log.error("ActionConfig not configured");
            throw new GeneralException("ActionConfig not configured for AlertDefinition[" + def.getName() + "]");
        }

        Attributes atts = cfg.getAttributes();

        List<String> idents = atts.getList(AlertDefinition.ActionConfig.ARG_IDENTITY_EMAIL_RECIP);
        if (Util.isEmpty(idents)) {
            _log.error("No Identities configured for Notification");
            throw new GeneralException("IdentityRecipients not configured for AlertDefinition[" + def.getName() + "]");
        }
        for (String s : Util.safeIterable(idents)) {
            if (_emailAddresses == null) {
                _emailAddresses = new ArrayList<String>();
            }
            Identity id = _context.getObjectByName(Identity.class, s);
            if (id != null) {
                AlertAction.AlertNotification notification = new AlertAction.AlertNotification();
                notification.setName(id.getName());
                notification.setDisplayName(id.getDisplayableName());
                List<String> emails = ObjectUtil.getEffectiveEmails(_context, id);
                if (!Util.isEmpty(emails)) {
                    _emailAddresses.addAll(emails);
                    notification.setEmailAddresses(emails);
                    _notifications.add(notification);
                } else {
                    Message msg = new Message(Message.Type.Warn,
                            MessageKeys.NOTIFICATION_FAILED_NO_EMAIL, id.getName());

                    if (_log.isWarnEnabled())
                        _log.warn(msg.getMessage());
                }
            } else {
                _log.error("Could not find Identity " + s);
            }
        }

        String templateName = atts.getString(AlertDefinition.ActionConfig.ARG_EMAIL_TEMPLATE);
        if (Util.isNotNullOrEmpty(templateName)) {
            _emailTemplate = _context.getObjectByName(EmailTemplate.class, templateName);
        } else {
            _log.error("Email Template name not provided");
        }

        if (atts.get(AlertDefinition.ActionConfig.ARG_EMAIL_VARS) != null) {
            _emailVars = (Map)atts.get(AlertDefinition.ActionConfig.ARG_EMAIL_VARS);
        }

        if (_emailVars == null) {
            _emailVars = new HashMap<String, Object>();
        }

        //Ensure the alert is fully loaded
        a.load();
        _emailVars.put(ARG_EMAIL_VAR_ALERT, a);
        _emailVars.put(ARG_EMAIL_VAR_ALERT_DEF, def.getName());

        if (errorMessage != null) {
            _emailVars.put(ARG_EMAIL_VAR_ERROR_MSG, errorMessage);
        }
    }

    /**
     * True if successfull
     * @return
     * @throws GeneralException
     */
    private boolean sendNotification() throws GeneralException {
        if (!Util.isEmpty(_emailAddresses) && _emailTemplate != null) {

            EmailOptions ops = new EmailOptions(_emailAddresses, _emailVars);

            try {
                _context.sendEmailNotification(_emailTemplate, ops);
            }
            catch (RetryableEmailException e) {
                _log.warn(e.getMessage(), e);
                throw e;
            }
            catch (Throwable t) {
                Message msg = new Message(Message.Type.Warn,
                        MessageKeys.ERR_SENDING_EMAIL, _emailTemplate.getName(), t);

                if (_log.isErrorEnabled()) {
                    _log.error(msg.getMessage(), t);
                }

                return false;

            }
            return true;
        }
        return false;
    }
}
