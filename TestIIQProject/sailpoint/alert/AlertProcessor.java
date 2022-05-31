/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.alert;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.util.ThreadInterruptedException;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Alert;
import sailpoint.object.AlertAction;
import sailpoint.object.AlertDefinition;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * Process a single Alert
 * Created by ryan.pickens on 6/22/16.
 */
public class AlertProcessor implements Runnable {

    Log _log = LogFactory.getLog(AlertProcessor.class);

    List<AlertDefinition> _alertDefs;
    Alert _alert;
    SailPointContext _context;
    Map<String, Object> _arguments;
    Monitor _monitor;
    BasicMessageRepository _messages;
    boolean _terminate;


    //////////////////////////////////////////////////////////////
    //  RESULTS
    //////////////////////////////////////////////////////////////
    public int _definitionsMatched;
    public int _definitionsUnMatched;
    public int _totalAlertDefinitions;
    public int _actionsCreated;

    public AlertProcessor(Alert a, List<AlertDefinition> defs, SailPointContext ctx, Map<String, Object> args) {
        _alert = a;
        _alertDefs = defs;
        _context = ctx;
        _arguments = args;
    }

    public AlertProcessor(String alertId, List<AlertDefinition> defs, SailPointContext ctx, Map<String, Object> args)
            throws GeneralException {
        this(ctx.getObjectById(Alert.class, alertId), defs, ctx, args);
    }

    /**
     * Process an alert against a set of AlertDefintions defined by a Filter to query for the AlertDefintiions
     * @param a
     * @param alertDefFilter
     */
    public AlertProcessor(Alert a, Filter alertDefFilter, SailPointContext ctx, Map<String, Object> args) throws GeneralException {
        this(a, (List<AlertDefinition>) null, ctx, args);
        setAlertDefinitions(alertDefFilter);
    }

    public void setMonitor(Monitor m) {
        _monitor = m;
    }

    public BasicMessageRepository getMessageRepo() { return _messages; }

    private void updateProgress(String progress){
        if ( _monitor != null ) {
            try {
                _monitor.updateProgress(progress);
            } catch (GeneralException e) {
                _log.warn("Exception updating progress in Monitor");
            }
        }
    }

    private void setAlertDefinitions(Filter f) throws GeneralException {
        QueryOptions ops = null;
        if (f != null) {
            ops = new QueryOptions();
            ops.add(f);
        }

        _alertDefs = _context.getObjects(AlertDefinition.class, ops);
    }

    static final int MAX_RESULT_ERRORS = 100;

    /**
     * Adds the given message to the internal message list. Logs the message
     * and the given exception. If the message list has already exceeded the
     * maximum allowed messages, specified by MAX_RESULT_ERRORS, the message
     * is logged but not stored.
     *
     * @param message Message to add to the internal message list.
     * @param t Exception to log, or null if no exception is required.
     */
    private void addMessage(Message message, Throwable t){

        if (message != null) {

            if (_messages == null) {
                _messages = new BasicMessageRepository();
            }

            if (_messages.getMessages().size() < MAX_RESULT_ERRORS) {
                _messages.addMessage(message);
            }

            String msg = message.getMessage();
            if (Message.Type.Error.equals(message.getType())){
                if (t != null)
                    _log.error(msg, t);
                else
                    _log.error(msg);
            }
            else if (Message.Type.Warn.equals(message.getType())){
                if (t != null)
                    _log.warn(msg, t);
                else
                    _log.warn(msg);
            }
        }
    }

    public void setTerminate(boolean b) { _terminate = b; }

    public boolean isTerminated() {
        return _terminate || Thread.currentThread().isInterrupted();
    }

    /**
     * If AlertDefintion has notifications configured, send the notifications and update
     * the AlertAction with the NotificationResult
     * @param a
     * @param def
     * @param ctx
     * @param action
     * @param m Error Message if error encountered
     * @return true if notifications were sent
     * @throws GeneralException
     */
    public boolean handleNotifications(Alert a, AlertDefinition def, SailPointContext ctx, AlertAction action, Message m)
            throws GeneralException {
        //If Primary handler wasn't notification, and notifications configured, Notify.
        if ((def.getActionConfig().getActionType() != AlertDefinition.ActionType.NOTIFICATION) && def.shouldNotify()) {
            AlertNotificationHandler handler = new AlertNotificationHandler();
            handler.sendNotificationsForAction(action, a, def, ctx, m);
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        if (_alert == null) {
            addMessage(Message.error("Alert cannot be null"), null);
            return;
        }

        if (Util.isEmpty(_alertDefs)) {
            addMessage(Message.error("No Alert Definitions configured"), null);
            return;
        } else {
            _totalAlertDefinitions = Util.size(_alertDefs);
        }

        updateProgress("Processing alert " + _alert.getName());

        AlertMatcher matcher = new AlertMatcher(_context, _arguments);
        AlertHandlerFactory factory = new AlertHandlerFactory();
        try {
            for (AlertDefinition def : sailpoint.tools.Util.safeIterable(_alertDefs)) {
                try {
                    if (!isTerminated()) {
                        if (!def.isDisabled() && matcher.isMatch(_alert, def)) {
                            _definitionsMatched++;
                            AlertHandler handler = factory.getAlertHandler(def);
                            if (handler != null) {
                                updateProgress("Executing AlertDefintion " + def.getName() + " for alert " + _alert.getName());
                                //NOTE: this will commit. Should be fine with the same context
                                AlertAction action = null;
                                Message errorMessage = null;
                                boolean handled = false;
                                try {
                                    action = handler.handleAlert(_alert, def, _context);
                                    if (action == null) {
                                        errorMessage = Message.error("Error processing alert[" + _alert.getName() +"] alertDef[" + def.toXml() +"]");
                                        //TODO: Do we want to keep track of # of errored Alert handlers? -rap
                                        throw new GeneralException(errorMessage);
                                    }

                                    _actionsCreated++;
                                    handled = true;
                                } catch (GeneralException ge) {
                                    //Allow handler to throw with meaningful message and propagate to task result
                                    errorMessage = Message.error("Error handling alert[" + _alert.getName() + "]: " + ge.getLocalizedMessage());
                                    addMessage(errorMessage, ge);
                                }
                                //Try to send Notifications
                                try {
                                    boolean notified = handleNotifications(_alert, def, _context, action, errorMessage);
                                    if (notified && !handled) {
                                        //Action wasn't created from primary handler, but notification succeeded
                                        _actionsCreated++;
                                    }
                                } catch (GeneralException ge) {
                                    addMessage(Message.error("Error Notifying for alert[" + _alert.getName() + "] AlertDefinition["
                                            + def.getName() + "]"), ge);
                                }

                                if (action != null) {
                                    //Decache the action
                                    _context.decache(action);
                                }


                            } else {
                                _log.info("No Handler found for AlertDefinition[" + def.toXml() + "]");
                            }
                        } else {
                            _log.info("Failed to match alert[" + _alert.getName() + "] with Definition[" + def.toXml() + "]");
                            _definitionsUnMatched++;
                        }
                    } else {
                        addMessage(Message.warn("AlertProcessor thread interrupted"), null);
                        return;
                    }

                } catch (ThreadInterruptedException tie) {
                    addMessage(Message.warn("AlertProcessor thread interrupted"), tie);
                    return;
                }
                catch (Throwable t) {
                    addMessage(Message.error("Error Processing alert[" + _alert.getName() + "] alertDef[" + def.getName() +"]"), t);
                }
            }
        } finally {
            //Save the alert
            try {
                _alert.setLastProcessed(new Date());
                _context.saveObject(_alert);
                _context.commitTransaction();
                AlertService.auditAlertProcessing(_alert, _context);
                _context.decache(_alert);
            } catch (GeneralException e) {
                _log.error("Error saving Alert " + _alert.getName());
            }
        }



    }
}
