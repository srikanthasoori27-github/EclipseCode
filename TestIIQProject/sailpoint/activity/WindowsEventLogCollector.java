/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.RPCService;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.RpcRequest;
import sailpoint.object.RpcResponse;
import sailpoint.object.Rule;
import sailpoint.object.WindowsEventLogEntry;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 */
public class WindowsEventLogCollector extends AbstractActivityCollector {
   
    private static Log log = LogFactory.getLog(WindowsEventLogCollector.class);
    private static XMLObjectFactory _factory = XMLObjectFactory.getInstance();

    public final static String CONFIG_USER = "user";
    public final static String CONFIG_PASSWORD = "password";

    public final static String CONFIG_MANAGEMENT_SCOPE = "managementScope";
    public final static String CONFIG_LOG_SERVER = "eventLogServer";
    public final static String CONFIG_AUTHORITY = "authority";
    public final static String CONFIG_QUERY_STRING = "queryString";

    private final static String RPC_SERVICE = "RPCServer";
    private final static String RPC_METHOD = "QueryEventLog";

    private final static String TIME_WRITTEN = "TimeWritten";
    
    public static final  String CONFIG_IQSERVICE_USER = "IQServiceUser";
    public static final  String CONFIG_IQSERVICE_PASS = "IQServicePassword";
    public static final  String CONFIG_IQSERVICE_TLS = "useTLSForIQService";
    /**
     */
    public WindowsEventLogCollector(ActivityDataSource ds) {
        super(ds);
    }

    /**
     * To test this collector try and iterate over the events and make sure we
     * get back a non-null activity. Stop at one for convience.
     */
    public void testConfiguration(Map<String, Object> options)
        throws GeneralException {
        
        EventIterator iterator = new EventIterator();
        if ( iterator == null ) {
            throw new GeneralException("Iterator was returned null.");
        }

        int count = 0;
        while ( iterator.hasNext() ) {
            ApplicationActivity activity = iterator.next();
            if ( activity == null ) {
                throw new GeneralException("While testings, there was a problem iterating activities, returned activity was null.");
            } 
            count++;
            // just iterate one and then bail
            break;
        }
        if ( count == 0 ) {
            throw new GeneralException("No activities were returned during test phase.");
        }
    }

    /**
     * Returns an iterator over ApplicationActivity objects.
     * @param options Map of options
     */
    @SuppressWarnings("unchecked")
    public CloseableIterator<ApplicationActivity>
              iterate( Map<String, Object> options) throws GeneralException {

        return new EventIterator();
    }

    /**
     * Given the stored position config information for this 
     * build a string that can be used to query the log
     * for records that have been added since the last pull.
     */
    public String getPositionConditionString(Map<String,Object> config) 
        throws GeneralException {

        String condition = null;
        if ( config != null ) {
            String timeWritten = (String)config.get(TIME_WRITTEN);
            if ( timeWritten != null ) {
                condition= "(" + TIME_WRITTEN + " > '" + timeWritten +"')";
            }
        }
        return condition;
    }

    /**
     * Returns a list of the attributes definitions that makeup the
     * the settings that are neccessary for the setup of this
     * ActivityCollector.
     */
    public List<AttributeDefinition> getDefaultConfiguration()  {
        List<AttributeDefinition> config = new ArrayList<AttributeDefinition>();
        config.add(new AttributeDefinition(RPCService.CONFIG_IQSERVICE_HOST,
                                           AttributeDefinition.TYPE_STRING,
                                           "Host where IQService is running.", 
                                           true));
        config.add(new AttributeDefinition(RPCService.CONFIG_IQSERVICE_PORT,
                                           AttributeDefinition.TYPE_STRING,
                                           "Port IQService is listening.", 
                                           true));
        
        config.add(new AttributeDefinition(CONFIG_IQSERVICE_USER,
                                           AttributeDefinition.TYPE_STRING,
                                           "IQService User",
                                           false));

        config.add(new AttributeDefinition(CONFIG_IQSERVICE_PASS,
                                           AttributeDefinition.TYPE_SECRET,
                                           "IQService Password",
                                           false));

        config.add(new AttributeDefinition(CONFIG_IQSERVICE_TLS, 
                                           AttributeDefinition.TYPE_BOOLEAN,   
                                           "Use TLS for communication between IIQ and IQService",  
                                           false, false));

        config.add(new AttributeDefinition(CONFIG_LOG_SERVER,
                                           AttributeDefinition.TYPE_STRING,
                                           "Server to connect when querying events",
                                           true));
        config.add(new AttributeDefinition(CONFIG_USER,
                                           AttributeDefinition.TYPE_STRING,
                                           "Windows Username to use when executing event log queries.", 
                                           true));
        config.add(new AttributeDefinition(CONFIG_PASSWORD,
                                           AttributeDefinition.TYPE_SECRET,
                                           "User's Password", 
                                           true));
        config.add(new AttributeDefinition(CONFIG_QUERY_STRING,
                                           AttributeDefinition.TYPE_STRING,
                                           "WMI Query to remoteExecute when iterating over event log entries.",
                                           true,
                                           "select * from win32_ntlogevent where (logfile = 'security') $(positionCondition)"));
        config.add(new AttributeDefinition(CONFIG_BLOCKSIZE,
                                           AttributeDefinition.TYPE_STRING,
                                           "Number of events to retrieve per call to the IQService.",
                                           true,
                                           "500"));
        return config;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Util
    //
    ///////////////////////////////////////////////////////////////////////////
 
    /**
     * Given a sql string from the application configuration,
     * append on the position config where clause.
     */
    protected String processSQL(String sql) throws GeneralException {
        String updatedSQL = sql;
        String positionCondition = 
            getPositionConditionString(getPositionConfig()); 
        if ( positionCondition == null ) {
            log.debug("Position config was null");
            positionCondition = "";
        }
        // build up a new sql string with the last position
        // condition included.
        if ( sql.contains("$(positionCondition)") ) {
            if ( positionCondition.length() > 0 ) {
                if ( !sql.toLowerCase().contains("where") ) {
                    positionCondition = "where " + positionCondition;
                } else
                    positionCondition = "AND " + positionCondition;
            }
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("positionCondition", positionCondition);
            updatedSQL = Util.expandVariables(sql, map);
        }
        return updatedSQL;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Event Iterator
    //
    ///////////////////////////////////////////////////////////////////////////

    protected class EventIterator 
              implements CloseableIterator<ApplicationActivity> {
        public static final String DISABEL_HOSTNAME_VERIFICATION = "disableHostnameVerification";

        public static final String USE_TLS_FOR_IQSERVICE = "useTLSForIQService";

        private Iterator<WindowsEventLogEntry> _entries;

        /* set to true when there are no more events to remotely fetch */
        private boolean _complete;

        /* needed to get next block */
        private String _requestId;

        /* cache the rule here so we don't have to fetch it for each entry */
        private Rule _cachedRule;

        /* entry with the latest date */
        private WindowsEventLogEntry _lastEvent;

        private ApplicationActivity _nextActivity;

        // IQService Configuration Info
        private String _iqServiceHost = null;

        private String _iqServicePort = null;

        private boolean _isUseTls = false;

        private boolean _disableHostNameVerification = false;
        /* Helper class to invoke calls to our remote service */
        private RPCService _service;

        public EventIterator() throws GeneralException {
            _entries = null;            
            _lastEvent = null;
            _cachedRule = null;
            _requestId = null;
            _complete = false;
            _service = getService();
            _service.addEncryptedAttribute(CONFIG_PASSWORD);
        }

        public ApplicationActivity getNextNonNullActivity() throws GeneralException {
            ApplicationActivity activity = null;
            while ( ( _entries.hasNext() ) && ( activity == null ) ) {
                WindowsEventLogEntry event = _entries.next();
                updateLastProcessDate(event);
                activity = transformEvent(event);
            }
            return activity;
        }

        public boolean hasNext() {
            boolean more = false;

            try {
                if ( ( _entries == null ) || ( !_entries.hasNext() ) )  {
                    List<WindowsEventLogEntry> entries = null;
                    if ( !_complete ) {
                        entries = getNextBlock();
                    }
                    if ( entries != null ) {
                        _entries = entries.iterator();
                    } else  {
                        _entries = null;
                    }
                }
    
                if ( _entries != null ) {
                    _nextActivity = getNextNonNullActivity();
                    if ( _nextActivity != null ) {
                        more = true;
                    }
                } else {
                    // All done!
                    if ( _lastEvent  != null ) {
                        if ( log.isDebugEnabled() ) 
                            log.debug("COMPLETE: " + _lastEvent.getTimeWrittenString() ) ;
                        
                        if ( _positionConfig == null ) {
                            _positionConfig = new HashMap<String,Object>();
                        }
                        _positionConfig.put(TIME_WRITTEN, _lastEvent.getTimeWrittenString());
                    } else {
                        log.warn("COMPLETE: LATEST EVENT NULL." );
                    }
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
            return more;
        }

        public ApplicationActivity next() {
            if (_nextActivity == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            return _nextActivity;
        }

        public void close() {
            _service.close();
        }

        /**
         * Get the next block of events from the iqservice. 
         */
        @SuppressWarnings("unchecked")
        private List<WindowsEventLogEntry> getNextBlock() throws GeneralException {

            List<WindowsEventLogEntry> entries = null;

            Map<String,Object> methodArgs = new HashMap<String,Object>((Map<String,Object>)getAttributes());

            String queryString = getRequiredStringAttribute(CONFIG_QUERY_STRING);
            if ( queryString != null ) {
                String updatedQuery = processSQL(queryString);
                methodArgs.put(CONFIG_QUERY_STRING, updatedQuery);
            }
            RpcRequest request = new RpcRequest(RPC_SERVICE, RPC_METHOD, methodArgs);
            if ( _requestId != null ) {
                request.setRequestId(_requestId);
            }
            RpcResponse response = _service.execute(request);
            if ( response != null ) {
                _requestId = response.getRequestId(); 
                _complete = response.isComplete();
                Map attrs = response.getResultAttributes();
                if ( attrs != null ) {
                    entries = (List<WindowsEventLogEntry>)attrs.get("events");
                } else {
                    entries = null;
                }
            } else {
                throw new GeneralException("NULL Response returned from the remote rpc.");
            }

            return entries;
        }

        public ApplicationActivity transformEvent(WindowsEventLogEntry event) 
            throws GeneralException {

            ApplicationActivity activity = null;

            SailPointContext context = getSailPointContext();
            if ( log.isDebugEnabled() ) 
                log.debug("LogEntry:\n " +  _factory.toXml(event, false) );
            
            Map<String,Object> ruleContext = buildRuleContext(event);
            Object obj = context.runRule(getTransformationRule(), ruleContext);
            if ( obj != null ) {
                if ( obj instanceof ApplicationActivity ) {
                        activity = (ApplicationActivity)obj;
                } else {
                        throw new GeneralException("Rule must return an activity object.");
                }
            } else {
                log.debug("Rule returned a null activity!");
            }
            
            if ( log.isDebugEnabled() )  
                log.debug("TransformedActivity:\n " +  _factory.toXml(activity, false) );
            
            return activity;
        }


        private Map<String,Object> buildRuleContext(WindowsEventLogEntry event) 
            throws GeneralException {

            Map<String,Object> ruleContext = new HashMap<String,Object>();
            ruleContext.put("context", getSailPointContext());
            ruleContext.put("datasource", getDataSource());
            ruleContext.put("event", event);
            return ruleContext;
        }

        // djs: not exactly sure how the hibernate cache works, so just keep one here
        // to avoid the cost, if there is any.  I think since we are getting it by
        // name it'll always query the db.
        private Rule getTransformationRule() throws GeneralException {
            if ( _cachedRule == null ) {
                SailPointContext context = getSailPointContext();
                Rule transformationRule = getDataSource().getTransformationRule();
                if (transformationRule != null) {
                    String ruleName = transformationRule.getName();
                    _cachedRule = context.getObjectByName(Rule.class, ruleName);
                }
            }
            return _cachedRule;
        }

        private void updateLastProcessDate(WindowsEventLogEntry entry) throws GeneralException {

            if ( _lastEvent == null ) {
                _lastEvent = entry;
            } else {
                Date lastProcessedDate = _lastEvent.getTimeWrittenDate();
                Date recDate = entry.getTimeWrittenDate();
                if ( ( recDate != null ) && ( recDate.after(lastProcessedDate) ) )  {
                    _lastEvent = entry;
                }
            }
        }
        protected RPCService getService() throws GeneralException {
            return getService(false);
        }

        /**
         * Creates RPCService instance based on IQService configuration set on application.
         *
         * @param performInit
         * @return
         * @throws ConnectorException
         */
        protected RPCService getService(boolean performInit) throws GeneralException {
            RPCService service = null;
            try {
                _iqServiceHost = getRequiredStringAttribute(RPCService.CONFIG_IQSERVICE_HOST);
                _iqServicePort = getRequiredStringAttribute(RPCService.CONFIG_IQSERVICE_PORT);
                _isUseTls = getBooleanAttribute(USE_TLS_FOR_IQSERVICE);
                _disableHostNameVerification = getBooleanAttribute(DISABEL_HOSTNAME_VERIFICATION);
                int port= Integer.parseInt(_iqServicePort);

                // IQService host and port are mandatory, throw error if not available
                if (!Util.isNotNullOrEmpty(_iqServiceHost) || Integer.parseInt(_iqServicePort) <= 0) {
                    throw new GeneralException("IQService Host and/or Port must have a valid value.");
                }

                service = new RPCService(_iqServiceHost, Integer.parseInt(_iqServicePort), performInit,
                        _isUseTls, _disableHostNameVerification);
                service.setConnectorServices(getConnectorServices());
            } catch (GeneralException e) {
                throw e;
            }
            return service;
        }
    }
}
