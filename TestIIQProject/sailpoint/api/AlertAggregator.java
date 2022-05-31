/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.alert.AlertBuilder;
import sailpoint.alert.AlertService;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Alert;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.task.Monitor;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * Created by ryan.pickens on 6/27/16.
 */
public class AlertAggregator {

    private static Log log = LogFactory.getLog(AlertAggregator.class);

    SailPointContext _context;

    public boolean _terminate;

    boolean _deltaAggregation;

    Monitor _monitor;

    BasicMessageRepository _messages;

    Attributes<String,Object> _arguments;

    List<Application> _sources;

    Application _currentSource;

    Schema _currentSchema;

    Rule _creationRule;

    Rule _correlationRule;

    Connector _connector;

    //Number of errors to reach before bailing
    int _errorThreshold = 100;


    ///////////////////////////////////////////////////////////////////
    ///ARGUMENT names
    //////////////////////////////////////////////////////////////////
    public static final String ARG_SOURCES = "sources";
    /**
     * A boolean option that when true will enable delta aggregation.
     * Whether or not this happens depends on the Connector.  The
     * flag will be passed through to the Connector.iterateObjects
     * method and it may or may not act upon it.
     */
    public static final String ARG_DELTA_AGGREGATION =
            Application.ATTR_DELTA_AGGREGATION;

    public static final String ARG_ERROR_THRESHOLD = "errorThreshold";

    /**
     * The maximum number of account or group errors we'll put
     * into the error list, which in turn ends up in the TaskResult.
     * When we hit errors processing alerts it's nice to have
     * some indiciation of that in the task result, but if there
     * is something seriously wrong we could try to dump thousands
     * of errors into the result.  Put a limit on this so we don't
     * make the result too large.  Could have this configurable.
     *
     * This will apply to both the _errors and _warnings list.
     */
    public static final int MAX_RESULT_ERRORS = 100;


    ///////////////////////////////////////////////////////////////////
    /// Runtime Statistics
    //////////////////////////////////////////////////////////////////
    public int _total;
    public int _errors;
    public int _created;
    public int _ignored;
    public List<String> _aggregatedSources;




    public AlertAggregator(SailPointContext con) {
        _context = con;
    }

    public AlertAggregator(SailPointContext con, Attributes<String, Object> args) {
        this(con);
        _arguments = args;
    }

    public void setMonitor(Monitor m) { _monitor = m; }

    public Monitor getMonitor() { return _monitor; }

    /**
     * Prepare for blast off
     * @throws GeneralException
     */
    protected void prepare() throws GeneralException {

        updateProgress("Preparing for aggregation");

        if (_context == null) {
            throw new GeneralException("No Context Provided");
        }

        if (_arguments == null) {
            //Create empty
            _arguments = new Attributes<>();
        } else {
            //Parse arguments
            //TODO: What args do we need?

            if (Util.isEmpty(_sources)) {
                //If sources not set manually, look in the args
                _sources = ObjectUtil.getObjects(_context, Application.class,
                        _arguments.getStringList(ARG_SOURCES));
            }

            _deltaAggregation |= _arguments.getBoolean(ARG_DELTA_AGGREGATION);

            if (_arguments.containsKey(ARG_ERROR_THRESHOLD)) {
                int threshold = _arguments.getInt(ARG_ERROR_THRESHOLD);
                if (threshold > 0) {
                    _errorThreshold = _arguments.getInt(ARG_ERROR_THRESHOLD);
                }
            }
        }
    }

    private boolean aggregateSource(Application app) throws GeneralException {
        boolean exceptions = false;
        _errors=0;

        updateProgress("Beginning to aggregate " + app.getName());
        _currentSource = app;
        addAggregatedSource(_currentSource.getName());

        //Ensure app fully loaded
        _currentSource.load();

        _currentSchema = _currentSource.getSchema(Schema.TYPE_ALERT);

        if (_currentSchema == null) {
            addMessage(Message.error("No Alert schema found on Source " + _currentSource.getName()), null);
            exceptions = true;

        } else {


            _creationRule = _currentSource.getCreationRule(Schema.TYPE_ALERT);
            _correlationRule = _currentSource.getCorrelationRule(Schema.TYPE_ALERT);

            Object deltaAggState = _currentSource.getAttributeValue(Application.ATTR_DELTA_AGGREGATION);
            CloseableIterator<ResourceObject> it = null;
            try {

                _connector = ConnectorFactory.getConnector(_currentSource, null);

                Map<String, Object> options = new HashMap<>();
                if (_deltaAggregation) {
                    options.put(ARG_DELTA_AGGREGATION, _deltaAggregation);
                }

                //TODO: Partitioning??


                it = _connector.iterateObjects(Schema.TYPE_ALERT, null, options);

                ResourceObject obj = null;
                while (it.hasNext() && !_terminate) {
                    long startTime = System.currentTimeMillis();
                    obj = it.next();

                    try {
                        AlertBuilder b = new AlertBuilder(_context);
                        Alert a = b.build(obj, _currentSource, _currentSchema);

                        //Correlate before creation. This allows creation to update target info
                        AlertService alertService = new AlertService(_context);
                        alertService.correlateAlert(a, _currentSource, _correlationRule);

                        Alert neuAlert = alertService.runCreationRule(a, _currentSource, _creationRule);
                        if (neuAlert == null) {
                            //Creation Rule returned null Alert, ignore
                            _ignored++;
                        } else {

                            alertService.promoteAttributes(neuAlert);

                            _context.saveObject(neuAlert);
                            _context.commitTransaction();
                            _context.decache(neuAlert);
                            _created++;
                        }

                        if (log.isDebugEnabled()) {
                            log.debug("Time to aggregate alert[" + (System.currentTimeMillis() - startTime) + "ms]");
                        }
                    } catch (Exception e) {
                        _errors++;
                        exceptions=true;
                        addMessage(new Message(Message.Type.Error, "Exception creating Alert"), e);
                        if (shouldContinue()) {
                            continue;
                        } else {
                            log.error("Hit error Threshold of " + _errorThreshold + ". Bailing out of aggregation for " +
                                    "source[" + _currentSource.getName() + "]");
                            break;
                        }
                    }

                    _total++;

                }

            } catch (ConnectorException connectorException) {
                addMessage(Message.error("Error in Connector"), connectorException);
                exceptions = true;
            } catch (GeneralException e) {
                log.error("Error while attempting to iterate Alerts", e);
                //TODO: Throw or Continue?
                throw e;
            } finally {
                if (it != null) {
                    it.close();
                    it = null;
                }
                saveSourceAggState(deltaAggState);
                updateProgress("Finishing Aggregation  for " + app.getName());

            }
        }

        return exceptions;
    }

    private boolean shouldContinue() {
        if (_errors > _errorThreshold) {
            return false;
        }
        
        return true;
    }

    /**
     * Save the Delta Aggregation state for the current Source
     * @param lastDeltaAggState
     * @throws GeneralException
     */
    private void saveSourceAggState(Object lastDeltaAggState) throws GeneralException {
        Object newDeltaAggState = lastDeltaAggState;
        boolean shouldSave = false;

        if (_connector != null) {
            Application connectorApp = ObjectUtil.getLocalApplication(_connector);
            newDeltaAggState = connectorApp.getAttributeValue(Application.ATTR_DELTA_AGGREGATION);
            shouldSave = !Differencer.objectsEqual(newDeltaAggState, lastDeltaAggState);
        }

        if (shouldSave) {
            _context.decache();
            String lockMode = PersistenceManager.LOCK_TYPE_TRANSACTION;
            Application src = ObjectUtil.lockObject(_context, Application.class, _currentSource.getId(), null, lockMode);
            try {
                src.setAttribute(Application.ATTR_DELTA_AGGREGATION, newDeltaAggState);
                _context.saveObject(src);
            } finally {
                ObjectUtil.unlockObject(_context, src, lockMode);
            }

        }
    }

    private void updateProgress(String progress) throws GeneralException {
        if ( _monitor != null ) {
            _monitor.updateProgress(progress);
        }
    }


    private void addAggregatedSource(String sourceName) {
        if (_aggregatedSources == null) {
            _aggregatedSources = new ArrayList<>();
        }
        _aggregatedSources.add(sourceName);
    }

    public void setTerminate(boolean b) { _terminate = b; }

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
                    log.error(msg, t);
                else
                    log.error(msg);
            }
            else if (Message.Type.Warn.equals(message.getType())){
                if (t != null)
                    log.warn(msg, t);
                else
                    log.warn(msg);
            }
        }
    }

    public BasicMessageRepository getMessageRepo() { return _messages; }


    public void execute() throws GeneralException {
        if (_context == null) {
            throw new GeneralException("No Context Provided");
        }

        updateProgress("Starting Alert Aggregation");

        prepare();

        if (_sources != null) {
            Iterator<Application> srcIter = _sources.iterator();
            while (srcIter.hasNext() && !_terminate) {
                Application src = srcIter.next();
                if (src == null) {
                    log.error("Source disappeared");
                } else {
                    aggregateSource(src);
                }

            }
        } else {
            log.warn("No Aggregation Source Provided");
        }

        updateProgress("Finished Aggregating");


    }
}
