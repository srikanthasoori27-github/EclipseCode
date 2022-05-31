/*
*  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
*/

package sailpoint.connector;

import connector.common.http.client.HttpConfigConstants;
import openconnector.ConnectorServices;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import sailpoint.api.TargetAggregator;
import sailpoint.connector.webservices.EndPoint;
import sailpoint.connector.webservices.RestRequestExecutor;
import sailpoint.connector.webservices.WebServicesClient;
import sailpoint.integration.JsonUtil;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Partition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.object.TargetHostConfig;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;
import sailpoint.unstructured.SecurityIQTargetCollector;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
* Created by ryan.pickens on 1/16/17.
*/
public class SecurityIQConnector extends AbstractConnector {

    private static Log log = LogFactory.getLog(SecurityIQConnector.class);
    public static String CONNECTOR_TYPE = "File Access Manager";
    //JDBC Connector used for Unstructured Data
    SIQTargetConnector _unstructuredConnector;

    //ElasticSearch connector used for Alerts
    SIQElasticSearchConnector _alertConnector;

    ////////Statics
    //Configs
    //Maximum number of hits to return with each batch of results
    public static final String CONFIG_FETCH_SIZE = "size";
    //True to disable using the ElasticSearch Search API
    public static final String CONFIG_SEARCH_DISABLED = "searchApiDisabled";
    //True to disable scrolling
    public static final String CONFIG_SCROLL_DISABLED = "scrollDisabled";
    //Time in minutes to keep the search context alive
    public static final String CONFIG_SCROLL_KEEP_ALIVE = "scrollKeepAlive";
    //Query to filter results. This will override the default
    public static final String CONFIG_QUERY = "query";
    public static final String CONFIG_QUERY_VARIABLES = "queryVariables";
    public static final String CONFIG_QUERY_VARIABLE_IDENTITY = "identity";

    //Sort payload
    public static final String CONFIG_SORT = "sort";


    //Endpoints to list objects. Used when iterating
    public static final String CONFIG_LIST_ENDPOINTS = "listEndpoint";
    //Endpoints to list delta objects. User when iterating with Delta enabled
    public static final String CONFIG_DELTA_LIST_ENDPOINTS = "deltaListEndpoint";
    //Endpoints to get a specific object. used with getObject
    public static final String CONFIG_GET_ENDPOINTS = "getEndpoint";

    //EndPoint rule params
    //Application Object
    public static final String RULE_PARAM_APPLICATION = "application";
    //EndPoint object
    public static final String RULE_PARAM_ENDPOINT = "requestEndPoint";
    //WebServicesClient
    public static final String RULE_PARAM_REST_CLIENT = "restClient";
    public static final String RULE_PARAM_RULE_STATE = "state";
    public static final String RULE_PARAM_SCHEMA = "schema";
    public static final String RULE_PARAM_RESULT = "result";

    //EndPoint attribute storing scrollId
    public static final String CONFIG_SCROLL_ID = "scrollId";


    public SecurityIQConnector(Application application)
        throws Exception {
            super(application);
            ConnectorServices svc = new DefaultConnectorServices();
            try {
                getSchema(Schema.TYPE_UNSTRUCTURED);
                _unstructuredConnector = new SIQTargetConnector(application);
                _unstructuredConnector.setConnectorServices(svc);
            } catch (SchemaNotDefinedException e) {
                if (log.isWarnEnabled()) {
                    log.warn(Schema.TYPE_UNSTRUCTURED + " schema not defined");
                }
            }
            try {
                getSchema(Schema.TYPE_ALERT);
                _alertConnector = new SIQElasticSearchConnector(application);
                _alertConnector.setConnectorServices(svc);
            } catch (SchemaNotDefinedException e) {
                if (log.isWarnEnabled()) {
                    log.warn(Schema.TYPE_ALERT + " schema not defined");
                }
            }

    }

    public SIQTargetConnector getTargetConnector() {
        return _unstructuredConnector;
    }

    /**
     * Test the connection to the SIQ DB via JDBC. This will not test the connection for Alerts
     * @throws ConnectorException
     */
    @Override
    public void testConfiguration() throws ConnectorException {

        try {
            if (getSchema(Schema.TYPE_ALERT) != null)
                _alertConnector.testConfiguration();
        } catch (SchemaNotDefinedException snd) {
            //No schema found, no worries
        }

        try {
            if (getSchema(Schema.TYPE_UNSTRUCTURED) != null) {
                _unstructuredConnector.testConfiguration();
            }
        } catch (SchemaNotDefinedException e) {
            //No worries, no schema
        }
    }

    @Override
    public ResourceObject getObject(String objectType, String identity, Map<String, Object> options) throws ConnectorException {
        switch (objectType) {
            case Schema.TYPE_UNSTRUCTURED:
                //Don't support getObject for Unstructured Targets
                return null;
            case Schema.TYPE_ALERT:
                return _alertConnector.getObject(objectType, identity, options);
            default:
                throw new ConnectorException("Unknown objectType");
        }
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(String objectType, Filter filter, Map<String, Object> options) throws ConnectorException {
        switch (objectType) {
            case Schema.TYPE_UNSTRUCTURED:
                return _unstructuredConnector.iterateObjects(objectType, filter, options);
            case Schema.TYPE_ALERT:
                return _alertConnector.iterateObjects(objectType, filter, options);
            default:
                throw new ConnectorException("Unknown objectType");
        }
    }

    //Connector used to fetch Targets/TargetPermissions.
    public class SIQTargetConnector extends JDBCConnector {


        public String DEFAULT_SCHEMA_NAME = "whiteops";

        private Connection _connection;

        //BAM Config
        List<TargetHostConfig> _bamConfigs;
        Iterator _bamConfigIter;
        boolean _aggregateInherited;
        //Used to override the function used to convert full_path+bamId to unique_path_hash
        String _hashFunction;
        String _schemaName = DEFAULT_SCHEMA_NAME;
        Schema _targetSchema;
        Schema _associationSchema;
        String _associationAttribute;

        public static final String CONFIG_AGG_INHERITED = "aggregateInherited";
        public static final String PATH_HASH_FUNC = "uniqueHashFunction";
        public static final String ARG_SCHEMA_NAME = "schemaName";

        public SIQTargetConnector(Application application)
            throws ConnectorException {
            super(application);
            try {
                _targetSchema = getSchema(Schema.TYPE_UNSTRUCTURED);
                addSchemaConfigs(_targetSchema);
                if (Util.isNotNullOrEmpty(_targetSchema.getStringAttributeValue(ARG_SCHEMA_NAME))) {
                    _schemaName = _targetSchema.getStringAttributeValue(ARG_SCHEMA_NAME);
                }
                _associationSchema = getSchema(_targetSchema.getAssociationSchemaName());
                _associationAttribute = _targetSchema.getStringAttributeValue(Schema.ATTR_ASSOCIATION_ATTRIBUTE);
                if (Util.isNullOrEmpty(_associationAttribute)) {
                    throw new ConnectorException("Null AssociationAttribute on Unstructured schema");
                }
                getBamConfigs();
                _aggregateInherited = _targetSchema.getBooleanAttributeValue(CONFIG_AGG_INHERITED);
                _hashFunction = _targetSchema.getStringAttributeValue(PATH_HASH_FUNC);
            } catch (Exception e) {
                throw new ConnectorException(e);
            }
        }

        @Override
        public CloseableIterator<ResourceObject> iterateObjects(Partition partition)
                throws ConnectorException {

            throw new ConnectorException("Partitioning not supported for Unstructured");

        }


        public CloseableIterator<ResourceObject> iterateObjects(String objectType,
                                                                Filter filter,
                                                                Map<String, Object> options)
                throws ConnectorException {

                    //Set to true if exception is caught
                    boolean error = false;

                    TargetIterator iter = null;
                    try {

                        //Must specify bamNames to iterate
                        if (Util.isEmpty(_bamConfigs)) {
                            error = true;
                            throw new GeneralException("No TargetHosts specified on the TargetSource.");
                        }

                        //Get Connection
                        _connection = getConnection(_targetSchema, _targetSchema.getConfig());

                        iter = getTargetIterator(_connection);

                    } catch (Exception e) {
                        throw new ConnectorException(e);
                    }
                    finally {
                        if (error) {
                            closeConnection(_connection);
                        }
                    }

                    return iter;
        }

        @Override
        public void testConfiguration() throws ConnectorException {
            Connection conn = null;
            try {
                conn = getConnection(_targetSchema, _targetSchema.getConfig());
            } catch (Exception ge) {
                throw new ConnectorException(ge);
            } finally {
                closeConnection(conn);
            }
        }

        /**
         * Override this method from the Abstract class so we can automatically
         * add in the mergeColumns to the do not coerce list as default
         * behavior.
         */
        @Override
        public ResourceObject buildResourceObjectFromMap(Schema schema,
                                                         Map<String,Object> mergedObject) {

                ResourceObject obj = super.buildResourceObjectFromMap(schema, mergedObject);

                if (_targetSchema.getObjectType().equals(schema.getObjectType())) {
                    //Add in the Associations.
                    if (obj.get(_associationAttribute) == null) {
                        obj.put(_associationAttribute, mergedObject.get(_associationAttribute));
                    }
                }

                return obj;
        }

        protected TargetIterator getTargetIterator(Connection conn) {
            return new TargetIterator(conn);
        }

        public static final String INCLUDED_TARGET_HOSTS = "targetHosts";
        /**
         * Parse the INCLUDED_TARGET_HOSTS into a List of TargeTHostConfigs
         */
        public void getBamConfigs() {
            if (_bamConfigs == null) {
                _bamConfigs = _targetSchema.getListAttributeValue(INCLUDED_TARGET_HOSTS);
                if (!Util.isEmpty(_bamConfigs)) {
                    _bamConfigIter = _bamConfigs.iterator();
                }
            }

        }

        public static final String BAM_TABLE = "bam";
        /**
         * Return the ID associated with a given BAM
         * @param hostName - BAM name
         * @return
         * @throws SQLException
         */
        protected String getBamIdFromName(String hostName)
                throws SQLException {

            String bamId = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                if (Util.isNotNullOrEmpty(hostName)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("SELECT id, name FROM ");
                    sb.append(_schemaName+".");
                    sb.append(BAM_TABLE);
                    sb.append(" WHERE name = ?");

                    stmt = getStatement(_connection, sb.toString());
                    stmt.setString(1, hostName);


                    rs = stmt.executeQuery();
                    while (rs.next()) {
                        bamId = rs.getString(1);
                        break;
                    }

                }
            } finally {
                closeResult(rs);
                closeStatement(stmt);
            }

            return bamId;
        }

        //Function used to compute unique_path_hash
        public static final String UNIQUE_PATH_COMPUTATION = " (CONVERT([uniqueidentifier],hashbytes('md5',CONVERT([nvarchar](max),?,(0))+CONVERT([nvarchar](max),?,(0))),(0)))";
        /**
         * Compute the unique_path_hash given a full_path and a bamId
         * @param path - full_path of the business resource
         * @param bamId - id of the BAM
         * @return
         */
        protected String computeUniquePathHash(String path, String bamId)
                throws SQLException {
            PreparedStatement stmt = null;
            ResultSet rs = null;
            String hash = null;
            try {

                StringBuilder sb = new StringBuilder();
                sb.append("SELECT ");
                if (Util.isNotNullOrEmpty(_hashFunction)) {
                    //Hash Function Overwritten.
                    sb.append(_hashFunction);
                } else {
                    sb.append(UNIQUE_PATH_COMPUTATION);
                }


                stmt = getStatement(_connection, sb.toString());
                stmt.setString(1, path);
                stmt.setString(2, bamId);

                rs = stmt.executeQuery();
                while(rs.next()) {
                    hash = rs.getString(1);
                    break;
                }

            } finally {
                closeResult(rs);
                closeStatement(stmt);
            }

            return hash;
        }

        public static final String BUSINESS_RESOURCE_TABLE = "business_service";
        public static final String UNIQUE_PATH_COLUMN = "unique_path_hash";

        /**
         * Return the ID's of a set of BR given the BAM and full_path
         * @param thc TargetHostConfig to fetch BR id's for
         * @return
         * @throws SQLException
         *
         * @ignore
         * We could do this when the TargetSource is created to save from doing this every time we agg
         */
        protected List<String> getBRIds(TargetHostConfig thc)
                throws SQLException, GeneralException {

            String hostName = thc.getHostName();
            List<String> paths = thc.getPaths();


            List<String> brIds = new ArrayList<String>();

            //If hostConfig has ID, use the id stored. Else, look up BAM ID from hostName
            String bamId = thc.getHostId() != null ? thc.getHostId() : getBamIdFromName(hostName);
            if (Util.isNullOrEmpty(bamId)) {
                if (log.isWarnEnabled()) {
                    log.warn("Could not get BAM["+hostName+"]");
                }
                throw new GeneralException("Exception getting BAM["+hostName+"]");
            }

            //Get the unique_path_hash for each BR
            List<String> brHash = new ArrayList<String>();
            for (String path : Util.safeIterable(paths)) {
                if (!thc.isPathCaseSensitive()) {
                    path = path.toLowerCase();
                }
                String hash = computeUniquePathHash(path, bamId);
                if (Util.isNotNullOrEmpty(hash)) {
                    brHash.add(hash);
                } else {
                    if (log.isWarnEnabled()) {
                        log.warn("Could not get hash for path["+path+"] on targetHost["+hostName+"]");
                    }
                }
            }

            if (Util.isEmpty(brHash)) {
                if (log.isErrorEnabled()) {
                    log.error("Error getting hash for BRs["+Util.listToCsv(paths)+"]");
                }
                throw new GeneralException("Could not get Business Resources["+Util.listToCsv(paths)+"]");
            }


            StringBuilder sb = new StringBuilder();
            sb.append("SELECT id, full_path FROM ");
            sb.append(_schemaName+"."+BUSINESS_RESOURCE_TABLE);
            sb.append(" WHERE ");
            sb.append(UNIQUE_PATH_COLUMN);
            sb.append(" IN (");
            String separator = "";
            for(int i=0; i < brHash.size(); i++) {
                sb.append(separator);
                sb.append("?");
                separator = ",";
            }
            sb.append(")");

            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = getStatement(_connection,sb.toString());

                for(int i=0; i<brHash.size(); i++) {
                    stmt.setString(i+1, brHash.get(i));
                }

                rs = stmt.executeQuery();

                List<String> foundPaths = new ArrayList<String>();
                while(rs.next()) {
                    brIds.add(rs.getString(1));
                    foundPaths.add(rs.getString(2));
                }

                if (!Util.isEmpty(foundPaths) && foundPaths.size() != paths.size()) {
                    //If empty, this will be logged when returned
                    for (String s : paths) {
                        if(!foundPaths.contains(s)) {
                            if (log.isWarnEnabled()) {
                                log.warn("Could not find BusinessResource["+s+"] on targetHost["+hostName+"]");
                            }
                        }
                    }
                }

            } finally {
                closeResult(rs);
                closeStatement(stmt);
            }

            return brIds;
        }

        //View used to query if we want all Targets/Permissions for a given host without hierarchy
        public static final String UNFILTERED_VIEW_NAME = "ra_entitlements_user_role_view";
        //View used to get the Hierarchy of Targets/Permissions. This is used when a specific path is set in the
        //targetHostConfig
        public static final String HIERARCHY_VIEW_NAME = "ra_view_user_role_function_hierarchy";

        /**
         * Return the SQL statement to iterate over all Target/TargetPermissions for the given TargetHostConfig
         * @return
         */
        protected PreparedStatement getIterateStatement(TargetHostConfig thc)
                throws SQLException, GeneralException {

            if (Util.isNullOrEmpty(thc.getHostName())) {
                throw new GeneralException("No hostName provided in TargetHostConfig"+thc.toXml());
            }

            List<String> targetCols = _targetSchema.getAttributeInternalOrNames();
            List<String> assocCols = _associationSchema.getAttributeInternalOrNames();

            PreparedStatement stmt = null;

            StringBuffer sql = new StringBuffer();

            sql.append("select ");

            sql.append(sailpoint.tools.Util.listToCsv(targetCols));
            sql.append(", ");
            sql.append(sailpoint.tools.Util.listToCsv(assocCols));

            sql.append(" from ");
            sql.append(_schemaName+".");

            boolean filteredPaths = !Util.isEmpty(thc.getPaths());


            //If the config contains paths, get the ID's for the BRs
            if (filteredPaths) {

                sql.append(HIERARCHY_VIEW_NAME);
                sql.append("('");
                String separator = "";
                List<String> brIds = getBRIds(thc);
                if (Util.isEmpty(brIds)) {
                    if (log.isWarnEnabled()) {
                        log.warn("Skipping TargetHost["+thc.getHostName()+"]. Could not get paths["+Util.listToCsv(thc.getPaths())+"]");
                    }
                    throw new GeneralException("Could not get paths["+Util.listToCsv(thc.getPaths())+"] " +
                            "for targetHost["+thc.getHostName()+"]");
                }
                for(String s : Util.safeIterable(brIds)) {
                    sql.append(separator);
                    sql.append(s);
                    separator = ",";
                }
                sql.append("')");
                sql.append(" where ");

            } else {

                sql.append(UNFILTERED_VIEW_NAME);
                sql.append(" where ");
                //TODO: Use name or id here? (name can change, but does not happen often)
                sql.append("bam_name");
                sql.append(" = ");
                sql.append("?");
                sql.append(" and ");

            }


            //Should we allow user defined filters?
            sql.append(createFilterString());

            sql.append(" order by br_id");

            stmt = getStatement(_connection,sql.toString());

            int paramValue = 1;
            if (!filteredPaths) {
                stmt.setString(paramValue++, thc.getHostName());
            }

            return stmt;
        }

        //HardCoded for now, should allow user defined filters in the future
        protected String createFilterString() {
            StringBuilder sb = new StringBuilder();
            sb.append("br_deleted = 0");
            if (!_aggregateInherited) {
                sb.append(" and ");
                sb.append("is_inherited = 0");
                sb.append(" and ");
                sb.append("has_unique_permissions = 1");
            }
            return sb.toString();

        }

        //BAM Table. Used to get information regarding TargetHosts
        public static final String TARGET_HOST_QUERY = "SELECT b.id, b.name, bt.full_path_case_sensitive FROM {0}.bam b," +
                " {1}.bam_type bt where b.bam_type_id = bt.id";
        /**
         * Return the SQL statement to get the list of available TargetHosts (BAM)
         * @return
         */
        protected String getTargetHostSQL(Map<String, Object> ops) {

            String s = MessageFormat.format(TARGET_HOST_QUERY, _schemaName, _schemaName);
            return s;
        }


        public static final String TARGET_HOST_BAM_NAME = "bamName";
        public static final String TARGET_HOST_BAM_ID = "bamId";
        public static final String TARGET_HOST_CASE_SENSITIVE = "caseSensitive";
        /**
         * Fetches all BAMs from the server
         * @return A list of the configured BAM names
         * @throws GeneralException
         */
        public List<Map<String, Object>> getTargetHosts() throws GeneralException {

            List<Map<String, Object>> hosts = new ArrayList<Map<String, Object>>();
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {

                conn = getConnection(_targetSchema, _targetSchema.getConfig());
                stmt = conn.prepareStatement(getTargetHostSQL(null));
                rs = stmt.executeQuery();
                while(rs.next()) {
                    Map hostConfig = new HashMap<String, Object>();
                    hostConfig.put(TARGET_HOST_BAM_ID, rs.getString(1));
                    hostConfig.put(TARGET_HOST_BAM_NAME, rs.getString(2));
                    hostConfig.put(TARGET_HOST_CASE_SENSITIVE, rs.getBoolean(3));
                    hosts.add(hostConfig);
                }
            } catch(SQLException sqe) {
                throw new GeneralException(sqe);
            } catch(ConnectorException ce) {
                throw new GeneralException(ce);
            } finally {
                closeResult(rs);
                closeStatement(stmt);
                closeConnection(conn);
            }

            return hosts;
        }

        public class TargetIterator implements CloseableIterator<ResourceObject> {

            private ResultSet _resultSet;

            Connection _connection;
            PreparedStatement _statement;

            ResourceObject _nextTarget;


            public TargetIterator(Connection conn) {
                _connection = conn;
            }


            @Override
            public boolean hasNext() {
                boolean hasNext = false;

                _nextTarget = getNextTarget();
                if (_nextTarget != null) {
                    hasNext = true;
                }
                return hasNext;
            }

            @Override
            public ResourceObject next() {
                if (_nextTarget == null) {
                    throw new NoSuchElementException("No more Targets.");
                }
                return _nextTarget;
            }

            @Override
            public void close() {
                closeResult(_resultSet);
                closeStatement(_statement);
                closeConnection(_connection);
                _connection = null;
                _statement = null;
                _resultSet = null;
            }

            /**
             * Return the next Target containing the associated access mappings
             * @return
             */
            public ResourceObject getNextTarget() {
                ResourceObject nextTarg = null;
                Map<String, Object> objMap = null;

                try {
                    boolean hasElements = true;
                    if (_resultSet == null || _resultSet.isAfterLast()) {
                        //If no resultSet, or resultSet has been exhausted get the next TargetHost
                        getNextHost();
                        if (_resultSet != null) {
                            hasElements = _resultSet.next();
                            if (!hasElements) {
                                //Nothing in ResultSet, try next
                                closeResult(_resultSet);
                                _resultSet = null;
                                return getNextTarget();
                            }
                        }
                    }


                    if (_resultSet != null) {


                        if (_resultSet.isBeforeFirst()) {
                            //Need to call next to advance to the first record
                            hasElements = _resultSet.next();
                        }
                        if (hasElements) {
                            if (!_resultSet.isAfterLast()) {
                                int currentBR = _resultSet.getInt(SecurityIQTargetCollector.defaultIterateColumns.br_id.name());
                                do {
                                    //Get all rows for this BR
                                    if (currentBR == _resultSet.getInt(SecurityIQTargetCollector.defaultIterateColumns.br_id.name())) {
                                        objMap = buildTarget(objMap);



                                    } else {
                                        //New BR_ID break out
                                        break;
                                    }
                                }
                                while(_resultSet.next());

                                nextTarg = buildResourceObjectFromMap(_targetSchema, objMap);
                            }
                        }

                        if ( log.isDebugEnabled() ) {
                            log.debug("NormalizedTarget" + toXml(nextTarg));
                        }

                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Iteration Complete.");
                        }
                    }


                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error("Error getting next Target" + e);
                    }
                    throw new RuntimeException(e);
                    //Should we fail here, or try to keep iterating?
                }

                return nextTarg;
            }

            /**
             * Return the resultSet for all targets on a given TargetHost
             * @return
             */
            public ResultSet getNextHost() {

                //Close previous ResultSet/Statement
                closeResult(_resultSet);
                _resultSet = null;
                closeStatement(_statement);
                _statement = null;

                if (_bamConfigIter.hasNext()) {
                    TargetHostConfig config = (TargetHostConfig)_bamConfigIter.next();

                    try {


                        if (log.isDebugEnabled()) {
                            log.debug("Fetching targets for BAM["+config.getHostName()+"]");
                        }
                        _statement = getIterateStatement(config);
                        _resultSet = _statement.executeQuery();
                    }
                    catch (SQLException e) {
                        if (log.isErrorEnabled()) {
                            log.error("Error getting Targets for BAM["+config.getHostName()+"]" + e);
                        }
                        //If Exception, try next BAM
                        return getNextHost();
                    }
                    catch (GeneralException ge) {
                        if (log.isErrorEnabled()) {
                            log.error("Error getting Targets for BAM["+config.getHostName()+"]" + ge);
                        }
                        //If Exception, try next BAM
                        return getNextHost();
                    }
                }

                return _resultSet;
            }


            //Create/Update the current target with the current resultSet row
            public Map<String,Object> buildTarget(Map<String,Object> target)
                    throws SQLException, ConnectorException, GeneralException {
                if (target == null) {
                    //If null, create the target
                    target = buildMap(_resultSet, _targetSchema, null, _connection);
                }

                Map<String, Object> accessMapping = buildMapFromResultSet(_associationSchema, _resultSet);
                addCorrelationAttributes(accessMapping);

                if (target.get(_associationAttribute) == null) {
                    List<Map> mappings = new ArrayList<Map>();
                    target.put(_associationAttribute, mappings);
                }

                ((List)target.get(_associationAttribute)).add(accessMapping);

                return target;

            }

            public static final String USER_ROW_TYPE = "U";

            protected void addCorrelationAttributes(Map<String,Object> map) throws SQLException {
                //Set Rights
                map.put(TargetAggregator.RIGHTS_ATTR, _resultSet.getString("permission_type_name"));
                if (USER_ROW_TYPE.equalsIgnoreCase(_resultSet.getString("row_type"))) {
                    map.put(TargetAggregator.NATIVE_ID_ATTR, _resultSet.getString("user_id"));
                } else {
                    map.put(TargetAggregator.NATIVE_ID_ATTR, _resultSet.getString("role_id"));
                }
            }


            protected Map<String,Object> buildMap(ResultSet result, Schema schema, Map<String,Object> ruleState, Connection conn)
                    throws GeneralException, ConnectorException {

                Map map = null;
                String buildRule = getSchemaConfigString(schema, Connector.CONFIG_BUILD_MAP_RULE);
                if (  buildRule == null ) {
                    buildRule = getSchemaConfigString(schema, Connector.CONFIG_JDBC_BUILD_MAP_RULE);
                }
                if (  buildRule != null ) {

                    Map<String,Object> ruleContext = new HashMap<String,Object>();
                    ruleContext.put("application", getApplication());
                    ruleContext.put("result", result);
                    ruleContext.put("schema", schema);
                    ruleContext.put("state", ruleState);
                    ruleContext.put("connection", conn);

                    Object o = runRule(buildRule, ruleContext);
                    if ( o instanceof Map ) {
                        map = (Map)o;
                    }
                    else {
                        throw new ConnectorException("Build Rule must return a Map.");
                    }
                }
                else {
                    map = buildMapFromResultSet(schema, result);
                }
                return map;
            }

            public Map<String, Object> buildMapFromResultSet(Schema s, ResultSet result)
                throws ConnectorException {
                Map<String,Object> map = new HashMap<String, Object>();

                List<String> columns = s.getAttributeInternalOrNames();
                for (String column : Util.safeIterable(columns)) {
                    if ( log.isDebugEnabled() )
                        log.debug("Building attribute [" + column + "]");
                    Object value = null;
                    try {
                        value = result.getObject(column);
                    } catch (SQLException e) {
                        log.error("Error getting column value" + e);
                    }
                    if ( value != null ) {
                        map.put(column, value);
                    }
                }

                return(map.size() != 0 ) ? map : null;
            }


        }
    }


    public static String CONFIG_BASE_URL = "baseURL";
    public static String CONFIG_USERNAME = "username";
    public static String CONFIG_PASSWORD = "password";
    public static String CONFIG_TIMEOUT = "timeout";
    public static String CONFIG_CONTEXT_URL = "contextURL";
    public static String CONFIG_HTTP_METHOD = "httpMethodType";
    public static String CONFIG_HEADER = "header";
    public static String CONFIG_BODY = "body";
    public static String CONFIG_AFTER_RULE = "afterRule";
    public static String CONFIG_BEFORE_RULE = "beforeRule";
    public static String CONFIG_PARSE_RULE = "parseRule";




    public class SIQElasticSearchConnector extends AbstractConnector {

        WebServicesClient _client;
        private boolean _isDeltaAggregation;
        Schema _schema;

        public static final String HTTP_GET = "GET";
        public static final String HTTP_POST = "POST";
        public static final String HTTP_DELETE = "DELETE";

        public SIQElasticSearchConnector(Application application) throws ConnectorException {
            super(application);
            try {
                _schema = getSchema(Schema.TYPE_ALERT);
                addSchemaConfigs(_schema);
            } catch (Exception e) {
                throw new ConnectorException(e);
            }
        }

        @Override
        public void testConfiguration() throws ConnectorException {
            EndPoint ep = getTestConfigEndPoint();

            //I don't like this RestExecutor, but it adheres to our WebService connector standards -rap
            RestRequestExecutor executor = new RestRequestExecutor();
            try {
                executor.executeHttpRequest(getClient(), ep);
            } catch(Exception e) {
                throw new ConnectorException(e);
            }
        }

        //Default Context URl for test Config
        protected static final String TEST_CONFIG_CTX = "_cat/health";
        protected EndPoint getTestConfigEndPoint() {
            EndPoint ep = new EndPoint();
            ep.setBaseUrl(getSchemaConfigString(_schema, CONFIG_BASE_URL));
            ep.setHttpMethodType(HTTP_GET);
            ep.setHeader(new HashMap<String, Object>());
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(ep.getBaseUrl());
            if (ep.getBaseUrl() != null && !ep.getBaseUrl().endsWith("/")) {
                //Append the slash
                urlBuilder.append("/");
            }

            urlBuilder.append(TEST_CONFIG_CTX);
            ep.setFullUrl(urlBuilder.toString());
            return ep;
        }

        @Override
        public ResourceObject getObject(String objectType, String identity, Map<String, Object> options) throws ConnectorException {
            ResourceObject ro = null;

            try {
                Schema schema = getSchema(objectType);
                if (schema != null) {
                    EndPoint ep = getEndPoint(schema, CONFIG_GET_ENDPOINTS);

                    if (ep != null) {
                        //Don't want to scroll for single object
                        ep.setAttribute(CONFIG_SCROLL_DISABLED, true);

                        Map<String, Object> queryVars = (Map)ep.getAttribute(CONFIG_QUERY_VARIABLES);
                        if (queryVars == null) {
                            queryVars = new HashMap<String, Object>();
                        }

                        queryVars.put(CONFIG_QUERY_VARIABLE_IDENTITY, identity);
                        ep.setAttribute(CONFIG_QUERY_VARIABLES, queryVars);

                        Iterator it = runEndPoint(ep, schema);
                        if (it != null) {
                            return transformObject(schema, (Map)it.next(), null);
                        } else {
                            throw new ObjectNotFoundException("Object not found: " + identity);
                        }

                    }
                }
            } catch (Exception e) {
                throw new ConnectorException(e);
            }

            return ro;
        }

        @Override
        public CloseableIterator<ResourceObject> iterateObjects(String objectType, Filter filter, Map<String, Object> options) throws ConnectorException {

            Schema schema = getSchema(objectType);
            EndPoint endpoint;
            if (schema != null) {
                if (options != null && Util.otob(options.get(Application.ATTR_DELTA_AGGREGATION))) {
                    //Delta Agg
                    _isDeltaAggregation = true;
                    endpoint = getEndPoint(schema, CONFIG_DELTA_LIST_ENDPOINTS);
                } else {
                    //Non Delta
                    endpoint = getEndPoint(schema, CONFIG_LIST_ENDPOINTS);
                }

                return new EndPointIterator(schema, endpoint);
            } else {
                throw new ConnectorException("Could not find Schema for objectType[" + objectType + "]");
            }
        }

        protected WebServicesClient getClient() throws Exception {
            if (_client == null) {
                _client = new WebServicesClient();

                Map config = new HashMap();
                //TODO: Store this in the app? or use an endpoint as reference?
                config.put(WebServicesClient.ARG_URL, getSchemaConfig(_schema, CONFIG_BASE_URL));
                config.put(WebServicesClient.ARG_USERNAME, getSchemaConfig(_schema, CONFIG_USERNAME));
                config.put(WebServicesClient.ARG_PASSWORD, getEncryptedSchemaConfig(_schema, CONFIG_PASSWORD));
                config.put(WebServicesClient.ARG_TIMEOUT, getSchemaConfig(_schema, CONFIG_TIMEOUT));
                //Backdoor to allow bypassing SSL Certificate Validation. Should be disabled for Production env
                config.put(HttpConfigConstants.OPT_TRUST_ALL_CERTS, getSchemaConfig(_schema, HttpConfigConstants.OPT_TRUST_ALL_CERTS));
                config.put(HttpConfigConstants.OPT_ALLOW_ALL_HOSTS, getSchemaConfig(_schema, HttpConfigConstants.OPT_ALLOW_ALL_HOSTS));


                _client.configure(config);
            }

            return _client;
        }

        /**
         * Run EndPoint before Rule. This provides the ability to modify the endpoint
         * in any way before making the REST call
         * @param endpoint
         */
        protected EndPoint runBeforeRule(EndPoint endpoint) {
            EndPoint ep = endpoint;
            try {
                if (Util.isNotNullOrEmpty(endpoint.getBeforeRule())) {
                    Map m = new HashMap<String, Object>();
                    m.put(RULE_PARAM_APPLICATION, getApplication());
                    m.put(RULE_PARAM_ENDPOINT, endpoint);
                    //Client will hold state form last request. getResponseBody, getResponseHeaders
                    m.put(RULE_PARAM_REST_CLIENT, getClient());
                    ep = (EndPoint)runRule(endpoint.getBeforeRule(), m);
                }

            } catch (Exception e) {
                log.error("Exception running before rule" + e);
            }

            return ep;
        }

        protected List<Map<String, Object>> runAfterRule(EndPoint endpoint, Object response) {
            List<Map<String, Object>> resp = null;
            try {
                if (Util.isNotNullOrEmpty(endpoint.getAfterRule())) {
                    Map m = new HashMap<String, Object>();
                    m.put(RULE_PARAM_APPLICATION, getApplication());
                    m.put(RULE_PARAM_ENDPOINT, endpoint);
                    //Client will hold state form last request
                    m.put(RULE_PARAM_REST_CLIENT, getClient());
                    m.put(RULE_PARAM_RESULT, response);
                    resp = (List)runRule(endpoint.getAfterRule(), m);
                }
            } catch (Exception e) {
                log.error("Exception running after rule" + e);
            }
            return resp;
        }

        protected Object parseHttpResponse(EndPoint ep, Schema schema, Object response)
            throws ConnectorException {

            Object transform = null;
            try {
                String parseRule = ep.getParseRule();
                if (Util.isNotNullOrEmpty(parseRule)) {

                    Map<String, Object> ruleContext = new HashMap<String, Object>();
                    ruleContext.put(RULE_PARAM_APPLICATION, getApplication());
                    ruleContext.put(RULE_PARAM_RESULT, response);
                    ruleContext.put(RULE_PARAM_SCHEMA, schema);
                    ruleContext.put(RULE_PARAM_REST_CLIENT, getClient());
                    transform = runRule(parseRule, ruleContext);
                } else {
                    transform = JsonUtil.parse((String)response);
                }
            } catch (Exception ge) {
                throw new ConnectorException(ge);
            }
            return transform;
        }

        private static final String RESPONSE_HITS = "hits";
        /**
         * Parse a response into a List of Maps
         * @return
         */
        protected List<Map<String, Object>> parseResponse(Object response)
            throws Exception {
            List<Map<String, Object>> resp = null;

            if (response instanceof Map) {
                Map<String, Object> respMap = (Map<String, Object>)response;
                //Default way for ElasticSearch response objects
                Map tmp = (Map<String, Object>)respMap.get(RESPONSE_HITS);
                if (tmp != null) {
                    resp = (List<Map<String, Object>>)tmp.get(RESPONSE_HITS);
                }
            } else if (response instanceof List) {
                //Custom endpoint without after rule? -rap
                resp = (List)response;
            } else if (response instanceof String) {
                Map m = Util.otom(JsonUtil.parse(Util.otos(response)));
                //Default way for ElasticSearch response objects
                Map tmp = Util.otom(m.get(RESPONSE_HITS));
                if (tmp != null) {
                    resp = (List<Map<String, Object>>)tmp.get(RESPONSE_HITS);
                }

            } else {
                throw new ConnectorException("Unable to parse response" + response);
            }
            return resp;
        }

        protected EndPoint getEndPoint(Schema schema, String entry)
                throws ConnectorException {
            EndPoint ep = null;
            try {
                Map<String, Object> config = Util.otom(getSchemaConfig(schema, entry));
                if (config != null && schema != null) {
                    ep = new EndPoint();
                    String baseUrl = null;
                    baseUrl = (String)config.get(CONFIG_BASE_URL);
                    if (Util.isNullOrEmpty(baseUrl)) {
                        //Try the app config
                        baseUrl = Util.otos(getSchemaConfig(schema, CONFIG_BASE_URL));
                    }

                    if (Util.isNullOrEmpty(baseUrl)) {
                        throw new ConnectorException("Missing base URL for schema[" + schema.getName() +"]");
                    } else {
                        ep.setBaseUrl(baseUrl);
                    }
                    ep.setContextUrl(Util.otos(config.get(CONFIG_CONTEXT_URL)));
                    ep.setHttpMethodType(Util.otos(config.get(CONFIG_HTTP_METHOD)));
                    ep.setHeader(Util.otom(config.get(CONFIG_HEADER)));
                    ep.setBody(Util.otom(config.get(CONFIG_BODY)));
                    ep.setAfterRule(Util.otos(config.get(CONFIG_AFTER_RULE)));
                    ep.setBeforeRule(Util.otos(config.get(CONFIG_BEFORE_RULE)));
                    ep.setParseRule(Util.otos(config.get(CONFIG_PARSE_RULE)));
                    ep.setFullUrl(buildFullUrl(ep.getBaseUrl(), ep.getContextUrl()));
                    //Add attributes
                    ep.setAttribute(CONFIG_SCROLL_KEEP_ALIVE, Util.otos(config.get(CONFIG_SCROLL_KEEP_ALIVE)));
                    ep.setAttribute(CONFIG_SCROLL_DISABLED, Util.otob(config.get(CONFIG_SCROLL_DISABLED)));
                    ep.setAttribute(CONFIG_SEARCH_DISABLED, Util.otob(config.get(CONFIG_SEARCH_DISABLED)));
                } else {
                    throw new ConnectorException("Error getting Endpoint configuration" + entry);
                }

            } catch (Exception e) {
                throw new ConnectorException("Error getting EndPoint configuration" + e);
            }

            return ep;
        }

        protected String buildFullUrl(String baseUrl, String contextUrl) {
            String fullUrl = null;
            if (Util.isNotNullOrEmpty(baseUrl)) {
                fullUrl = baseUrl;
                if (Util.isNotNullOrEmpty(contextUrl)) {
                    if (!baseUrl.endsWith("/") && !contextUrl.startsWith("/")) {
                        fullUrl = fullUrl.concat("/");
                    }
                    fullUrl = fullUrl.concat(contextUrl);
                }
            }
            return fullUrl;
        }


        /**
         * Run the EndPoint.
         * @param endpoint
         * @throws Exception
         */
        protected Iterator<Map<String, Object>> runEndPoint(EndPoint endpoint, Schema schema) throws Exception {
            List<Map<String, Object>> items = null;
            //Execute the endpoint
            if (endpoint != null) {
                //Run Before Rule
                if (Util.isNotNullOrEmpty(endpoint.getBeforeRule())) {
                    endpoint = runBeforeRule(endpoint);
                }

                //If searchApi disabled, use the RestExecutor, otherwise assume we're hitting the ElasticSearch Search APi
                Boolean searchApiDisabled = endpoint.getBooleanAttributeValue(CONFIG_SEARCH_DISABLED);

                Object resp = null;
                if (searchApiDisabled) {
                    //I don't like this RestExecutor, but it adheres to our WebService connector standards -rap
                    RestRequestExecutor executor = new RestRequestExecutor();
                    resp = executor.executeHttpRequest(getClient(), endpoint);
                } else {
                    //Assume we're using the ElasticSearch Search API
                    resp = runSearchEndpoint(endpoint, schema);
                }

                //Run After Rule.
                if (Util.isNotNullOrEmpty(endpoint.getAfterRule())) {
                    items = runAfterRule(endpoint, resp);
                } else {
                    items = parseResponse(resp);
                }

            }

            return items != null ? items.iterator() : null;
        }


        /**
         * Run an endpoint using the ElasticSearch Search APIs. We will use this by default. This can be
         * disabled with the CONFIG_DISABLE_SEARCH endpoint attribute
         * @param endpoint
         * @param schema
         * @return
         * @throws Exception
         */
        protected Object runSearchEndpoint(EndPoint endpoint, Schema schema) throws Exception {
            Object resp = null;

            //If Scrolling enabled, ensure we have a scrollId
            Boolean scrollDisabled = endpoint.getBooleanAttributeValue(CONFIG_SCROLL_DISABLED);
            if (scrollDisabled) {
                //Alow Expanding the queryString. Do we need to do this for any other payload params?
                String queryString = (String)endpoint.getBody().get(CONFIG_QUERY);
                if (queryString != null) {
                    Map<String, Object> deltaVars = new HashMap<String,Object>();
                    queryString = expandVariables(queryString, endpoint, deltaVars);
                    endpoint.getBody().put(CONFIG_QUERY, queryString);
                }

                String response = null;
                if (HTTP_POST.equals(endpoint.getHttpMethodType())) {
                    response = getClient().executePost(endpoint.getFullUrl(),
                            getJsonPayload(endpoint.getBody()), endpoint.getHeader(), endpoint.getResponseCode(), false);
                } else if (HTTP_DELETE.equals(endpoint.getHttpMethodType())) {
                    response = getClient().executeDelete(endpoint.getFullUrl(),
                            endpoint.getBody(), endpoint.getHeader(), endpoint.getResponseCode());
                } else {

                    //Assume a GET?
                    response = getClient().executeGet(endpoint.getFullUrl(),
                            endpoint.getHeader(), endpoint.getResponseCode());
                }
                resp = parseHttpResponse(endpoint, schema, response);
            } else {
                //Execute request
                resp = runEndPointWithScrolling(endpoint, schema);
            }

            return resp;
        }
        protected String RESPONSE_SCROLL_ID = "_scroll_id";
        //Param holding the scrollId
        protected String SCROLL_ID_PARAM = "scroll_id";

        /**
         * Run an EndPoint with paging enabled. This will use ElasticSearch scrolling
         * to handle paging.
         * @param endpoint
         * @return
         * @throws Exception
         */
        protected Map<String, Object> runEndPointWithScrolling(EndPoint endpoint, Schema schema) throws Exception {

            RestRequestExecutor executor = new RestRequestExecutor();
            if (Util.isNullOrEmpty(endpoint.getStringAttributeValue(CONFIG_SCROLL_ID))) {
                //execute GET to obtain Scroll_ID
                EndPoint scrollEndpt = getScrollEndPoint(endpoint, schema);

                String queryString = (String)scrollEndpt.getBody().get(CONFIG_QUERY);
                if (queryString != null) {
                    Map<String, Object> deltaVars = new HashMap<String,Object>();
                    if (_isDeltaAggregation) {
                        //Add the delta state to the vars
                        Map deltaAggState = Util.otom(getApplication().getAttributeValue(Application.ATTR_DELTA_AGGREGATION));
                        String deltaState = null;

                        if (deltaAggState != null) {
                            deltaState = Util.otos(deltaAggState.get(schema.getObjectType()));
                        }
                        if (deltaState == null) {
                            //Need to put something into the field.
                            deltaState = "0";
                        }
                        deltaVars.put(Application.ATTR_DELTA_AGGREGATION, deltaState);
                    }
                    queryString = expandVariables(queryString, scrollEndpt, deltaVars);
                    scrollEndpt.getBody().put(CONFIG_QUERY, queryString);
                }

                String response = getClient().executePost(scrollEndpt.getFullUrl(),
                        getJsonPayload(scrollEndpt.getBody()), scrollEndpt.getHeader(), null, false);
                Object o = parseHttpResponse(endpoint, schema, response);
                if (o instanceof Map) {
                    //Get the scrollId from the response and set it on the endpoint
                    String scrollId = (String)(((Map) o).get(RESPONSE_SCROLL_ID));
                    if (Util.isNullOrEmpty(scrollId)) {
                        throw new ConnectorException("No ScrollId found in response");
                    }
                    endpoint.setAttribute(CONFIG_SCROLL_ID, scrollId);
                    //This will contain first batch of results
                    return (Map<String, Object>)o;

                } else {
                    throw new ConnectorException("Unable to parse response " + response);
                }
            }

            //Execute scroll POST
            EndPoint scrollingEndpt = getScrollingEndpoint(endpoint, schema);
            String response = getClient().executePost(scrollingEndpt.getFullUrl(),
                    getJsonPayload(scrollingEndpt.getBody()), scrollingEndpt.getHeader(),
                    scrollingEndpt.getResponseCode(), false);
            Object o = parseHttpResponse(endpoint, schema, response);
            if (o instanceof Map) {
                return (Map<String, Object>)o;

            } else {
                throw new ConnectorException("Unable to parse response " + response);
            }

        }

        protected static final String SCROLL_PARAM = "scroll";
        protected static final String DEFAULT_SCROLL_KEEP_ALIVE = "5m";
        protected static final String DEFAULT_FETCH_SIZE = "50";
        protected final List<String> DEFAULT_STATUSES = Arrays.asList("200", "201");

        /**
         * Given a list EndPoint, get the endpoint to obtain the scrollId
         * @param ep
         * @return
         */
        private EndPoint getScrollEndPoint(EndPoint ep, Schema schema) throws Exception {

            EndPoint scrollEndPoint = new EndPoint();
            scrollEndPoint.setHttpMethodType(HTTP_POST);
            scrollEndPoint.setParseRule(ep.getParseRule());
            //Add scroll param to URL
            String keepAlive = ep.getStringAttributeValue(CONFIG_SCROLL_KEEP_ALIVE);
            if (Util.isNullOrEmpty(keepAlive)) {
                //Default the keepAlive to 5m
                keepAlive = DEFAULT_SCROLL_KEEP_ALIVE;
            }
            Map<String, String> queryParams = new HashMap<String, String>();
            queryParams.put(SCROLL_PARAM, keepAlive);
            scrollEndPoint.setFullUrl(appendQueryParams(ep.getFullUrl(), queryParams));
            scrollEndPoint.setContextUrl(ep.getContextUrl());

            Map m = ep.getBody();
            if (m == null) {
                m = new HashMap<String, String>();
            }
            if (!m.containsKey(CONFIG_FETCH_SIZE)) {
                //See if we have a schemaConfig set
                Object o = getSchemaConfig(schema, CONFIG_FETCH_SIZE);
                if (o != null) {
                    m.put(CONFIG_FETCH_SIZE, o.toString());
                } else {
                    //Default
                    m.put(CONFIG_FETCH_SIZE, DEFAULT_FETCH_SIZE);
                }

            }
            scrollEndPoint.setHeader(ep.getHeader());
            scrollEndPoint.setBody(m);
            List<String> allowedResponseCodes = ep.getResponseCode();
            if (Util.isEmpty(allowedResponseCodes)) {
                if (allowedResponseCodes == null) {
                    allowedResponseCodes = new ArrayList<String>();
                }
                allowedResponseCodes.addAll(DEFAULT_STATUSES);
            }
            scrollEndPoint.setResponseCode(allowedResponseCodes);

            return scrollEndPoint;

        }

        /**
         * Return an endpoint to fetch batch results using the scroll API
         */
        private EndPoint getScrollingEndpoint(EndPoint endpoint, Schema schema) throws Exception {

            EndPoint p = null;

            if (endpoint != null) {
                p = new EndPoint();

                p.setHttpMethodType(HTTP_POST);
                p.setParseRule(endpoint.getParseRule());
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append(endpoint.getBaseUrl());
                if (endpoint.getBaseUrl() != null && !endpoint.getBaseUrl().endsWith("/")) {
                    //Append the slash
                    urlBuilder.append("/");
                }

                urlBuilder.append("_search/scroll");

                p.setFullUrl(urlBuilder.toString());

                Map<String, String> scrollBody = new HashMap<String, String>();

                //Add scroll param to Body params
                String keepAlive = (String)getSchemaConfig(schema, CONFIG_SCROLL_KEEP_ALIVE);
                if (Util.isNullOrEmpty(keepAlive)) {
                    //Default the keepAlive to 5m
                    keepAlive = DEFAULT_SCROLL_KEEP_ALIVE;
                }

                Map<String, Object> obj = new HashMap<String, Object>();


                obj.put(SCROLL_PARAM, keepAlive);
                obj.put(SCROLL_ID_PARAM, endpoint.getStringAttributeValue(CONFIG_SCROLL_ID));
                p.setBody(obj);
            }

            return p;

        }

        private EndPoint getScrollCleanupEndpoint(EndPoint endpoint) {
            EndPoint p = new EndPoint();

            p.setHttpMethodType(HTTP_DELETE);
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(endpoint.getBaseUrl());
            if (endpoint.getBaseUrl() != null && !endpoint.getBaseUrl().endsWith("/")) {
                //Append the slash
                urlBuilder.append("/");
            }

            urlBuilder.append("_search/scroll");

            p.setFullUrl(urlBuilder.toString());

            Map<String, Object> scrollBody = new HashMap<String, Object>();
            scrollBody.put(SCROLL_ID_PARAM, Arrays.asList(Util.otos(endpoint.getAttribute(CONFIG_SCROLL_ID))));

            p.setBody(scrollBody);

            //Disable scrolling
            p.setAttribute(CONFIG_SCROLL_DISABLED, true);



            return p;
        }


        private String appendQueryParams(String url, Map<String, String> paramMap) throws UnsupportedEncodingException {
            StringBuilder sb = new StringBuilder();
            if (Util.isNotNullOrEmpty(url)) {
                sb.append(url);
                if (!Util.isEmpty(paramMap)) {
                    if (sb.charAt(sb.length()-1) != '?') {
                        sb.append("?");
                    }
                    boolean first = true;
                    for (String key : paramMap.keySet()) {
                        if (!first) {
                            sb.append("&");
                        }
                        sb.append(key);
                        sb.append("=");
                        try {
                            sb.append(java.net.URLEncoder.encode(paramMap.get(key) , "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            log.info("Using native encoding as UTF-8 is not supported ");
                            sb.append(java.net.URLEncoder.encode(paramMap.get(key)));
                        }
                        first = false;
                    }
                }
            }
            return sb.toString();
        }

        /**
         * Get the payload as a JSON string, keeping the needed entries as JSON types when applicable
         * @param ep
         * @return
         * @throws Exception
         */
        private String getJsonPayload(Map<String, Object> ep) throws Exception {
            String payload = null;
            if (ep != null) {
                JSONObject obj = new JSONObject(ep);
                if (ep.containsKey(CONFIG_QUERY)) {
                    //This needs to be kept as JSON
                    JSONObject queryJSON = new JSONObject(Util.otos(ep.get(CONFIG_QUERY)));
                    obj.put(CONFIG_QUERY, queryJSON);
                }

                if (ep.containsKey(CONFIG_SORT)) {
                    //This needs to be kept as JSON
                    JSONArray sortJSON = new JSONArray(Util.otos(ep.get(CONFIG_SORT)));
                    obj.put(CONFIG_SORT, sortJSON);
                }

                //TODO: Do we need any of the other search request payload options? -rap

                payload = obj.toString();
            }
            return payload;
        }


        private String expandVariables(String s, EndPoint endpoint, Map<String, Object> vars) throws Exception {
            Map<String, Object> variables;
            variables = (Map)endpoint.getAttribute(CONFIG_QUERY_VARIABLES);
            if (variables == null) {
                variables = new HashMap<String, Object>();
            }
            if (!Util.isEmpty(vars)) {
                variables.putAll(vars);
            }

            String expanded = Util.expandVariables(s, variables);
            if (log.isInfoEnabled()) {
                log.info("Expanded Variables: Original[" + s +"]" + "Expanded["+expanded+"]");
            }

            return expanded;
        }

        protected ResourceObject transformObject(Schema schema, Map<String, Object> restObj, Map<String,Object> ruleState)
            throws Exception {
            ResourceObject obj;
            Map map = restObj;
            String buildMapRule = (String)getSchemaConfig(schema, Connector.CONFIG_BUILD_MAP_RULE);
            if (Util.isNotNullOrEmpty(buildMapRule)) {
                Map<String,Object> ruleContext = new HashMap<String,Object>();
                ruleContext.put(RULE_PARAM_APPLICATION, getApplication());
                ruleContext.put(RULE_PARAM_RESULT, restObj);
                ruleContext.put(RULE_PARAM_SCHEMA, schema);
                ruleContext.put(RULE_PARAM_RULE_STATE, ruleState);
                ruleContext.put(RULE_PARAM_REST_CLIENT, getClient());

                Object o = runRule(buildMapRule, ruleContext);
                if ( o instanceof Map ) {
                    map = (Map)o;

                }
                else {
                    throw new ConnectorException("Build Rule must return a Map.");
                }
            }

            obj = buildResourceObjectFromMap(schema, map);

            return obj;
        }

        //Name of rule to assist with updating agg state
        public static final String CONFIG_AGG_STATE_RULE = "deltaAggStateRule";
        public static final String DEFAULT_DELTA_ATTRIBUTE = "_source.creation_timestamp";
        protected void updateApplicationAggState(Map<String,Object> resourceAtts, Schema schema)
            throws ConnectorException {
            //Check Application config to see if this is necessary?
            if (resourceAtts != null) {
                String ruleName = (String)getSchemaConfig(schema, CONFIG_AGG_STATE_RULE);
                if (Util.isNotNullOrEmpty(ruleName)) {
                    Map<String, Object> ruleParams = new HashMap<String, Object>();
                    ruleParams.put(RULE_PARAM_APPLICATION, getApplication());
                    ruleParams.put(RULE_PARAM_SCHEMA, schema);
                    ruleParams.put(RULE_PARAM_RESULT, resourceAtts);
                    ruleParams.put("isDeltaAggregation", _isDeltaAggregation);
                    try {
                        runRule(ruleName, ruleParams);
                    } catch (GeneralException ge) {
                        throw new ConnectorException("Error updating Delta Agg State with rule " + ruleName + "[" + ge + "]");
                    }
                } else {
                    //No rule, default to creation_timestamp
                    try {
                        String timestamp = (String)MapUtil.get(resourceAtts, DEFAULT_DELTA_ATTRIBUTE);
                        if (Util.isNotNullOrEmpty(timestamp)) {
                            Map<String, Object> currentAggState = (Map)getApplication().getAttributeValue(Application.ATTR_DELTA_AGGREGATION);
                            if (currentAggState == null) {
                                currentAggState = new HashMap<String, Object>();
                            }
                            //Should we only set if timestamp > current saved delta? -rap
                            currentAggState.put(schema.getObjectType(), timestamp);

                            getApplication().setAttribute(Application.ATTR_DELTA_AGGREGATION, currentAggState);
                        }
                    } catch (GeneralException ge) {
                        //Throw here, or just log? -rap
                        log.error("Unable to save Delta Aggregatino State" + ge);
                    }
                }
            }

        }

        //If true, only keep attributes found in the schema
        public static final String CONFIG_FILTER_SCHEMA_ATTRIBUTES = "filterSchemaAttributes";

        /**
         * Same as
         * @param schema
         * @param restObj
         * @return
         */
        @Override
        public ResourceObject buildResourceObjectFromMap(Schema schema, Map<String, Object> restObj) {
            ResourceObject object = null;
            try {
                String transformationRule = Util.otos(getSchemaConfig(schema, CONFIG_TRANSFORMATION_RULE));
                if ( transformationRule == null ) {
                    List<String> attrsToLeaveAlone = (List<String>)getListAttribute(CONFIG_DONT_TRANSFORM_CSV_ATTRS);
                    boolean transform = true;
                    boolean dontTransFormCsv = Util.otob(getSchemaConfig(schema, CONFIG_DONT_TRANSFORM_CSV));
                    if ( dontTransFormCsv ) transform = false;
                    try {
                        object = defaultTransform(schema, restObj, transform, attrsToLeaveAlone);
                    } catch (GeneralException ge) {
                        throw new RuntimeException(ge);
                    }
                } else {
                    // make sure that we do not add null values to the rule context
                    if ( schema == null ) schema = new Schema();
                    if ( restObj == null )
                        restObj = new HashMap<String,Object>();

                    Map<String,Object> ruleContext = new HashMap<String,Object>();
                    ruleContext.put("application", getApplication());
                    ruleContext.put("schema", schema);
                    ruleContext.put("object", restObj);
                    ruleContext.put("map", restObj);

                    Object o = null;
                    try {
                        o = runRule(transformationRule, ruleContext);
                    } catch(GeneralException e) {
                        throw new RuntimeException(e);
                    }
                    if ( ( o != null ) && ( o instanceof ResourceObject ) ) {
                        object = (ResourceObject)o;
                    } else {
                        throw new RuntimeException("Rule must return a ResourceObject.");
                    }
                }
            } catch (Exception e) {
                log.error("Error building Resource Object " + e);
                throw new RuntimeException(e);
            }
            return object;
        }


        /**
         * Same as {@link #defaultTransform(sailpoint.object.Schema, java.util.Map, boolean, java.util.List)} but will
         * accept dot notation in internal name and parse this with MapUtil
         * @param schema
         * @param object
         * @param coerceCsvsToList
         * @param attrsToLeaveAlone
         * @return
         * @throws Exception
         */
        public ResourceObject defaultTransform(Schema schema, Map<String,Object> object, boolean coerceCsvsToList,
               List<String> attrsToLeaveAlone) throws GeneralException {

            if ( object == null ) {
                return null;
            }

            if ( attrsToLeaveAlone == null ) {
                attrsToLeaveAlone = new ArrayList<String>();
            }

            String type = schema.getObjectType();
            // assemble the object
            String idAttrName = getEffectiveAttribute(schema, schema.getIdentityAttribute());

            // template def we can use to coerce any odd typed values that are
            // mapped to the identity or displayName
            AttributeDefinition stringDef = new AttributeDefinition("identifier", AttributeDefinition.TYPE_STRING);
            Object idObj = object.get(idAttrName);
            String identity = (String)coerceValue(stringDef, idObj);
            if ( identity == null ) {
                // djs: cannot toXml the resource object here since it can
                // contain any object type, even types that aren't known
                // to our xml factory. So just print out the string value of
                // the object.
                throw new RuntimeException("Identity attribute ["+idAttrName+"]"
                        + " was not found. Object ["+object+"]");
            }

            String nameAttr = getEffectiveAttribute(schema, schema.getDisplayAttribute());
            String displayName = null;
            if ( nameAttr != null ) {
                Object displayObj = object.get(nameAttr);
                displayName = (String)coerceValue(stringDef, displayObj);
            }

            String instanceAttr = getEffectiveAttribute(schema,schema.getInstanceAttribute());
            String instance = null;
            if ( instanceAttr != null ) {
                Object instanceObj = object.get(instanceAttr);
                instance = (String)coerceValue(stringDef, instanceObj);
            }

            ResourceObject ro= new ResourceObject(identity, displayName, type);
            ro.setInstance(instance);
            if (log.isDebugEnabled())
                log.debug("Started coercion for identity : " +  identity + " of Type : " + type +
                        " with display name: " + displayName );
            Attributes<String,Object> spAttrs = new Attributes<String,Object>();
            List<AttributeDefinition> defs = schema.getAttributes();
            for ( AttributeDefinition def : defs ) {
                // use internal name if there is one...
                String name = def.getInternalOrName();
                if ( name != null )  {
                    Object value = MapUtil.get(object, name);
                    if ( value != null )  {
                        if ( value instanceof List ) {
                            List newList = new ArrayList();
                            for ( Object listValue : (List)value ) {
                                //Don't add null values to list
                                Object v = AbstractConnector.coerceValue(def, listValue);
                                if (v != null)
                                    newList.add(v);
                            }
                            value = newList;
                            if ( Util.size((List)value) == 0 ) {
                                value = null;
                            }
                        } else  {
                            value = AbstractConnector.coerceValue(def, value);
                            if ( def.isMultiValued() ) {
                                if ( value instanceof String ) {
                                    if ( attrsToLeaveAlone.contains(name) )
                                        value = AbstractConnector.buildStringList((String)value, false);
                                    else
                                        value = AbstractConnector.buildStringList((String)value, coerceCsvsToList);
                                } else {
                                    value = AbstractConnector.buildList(value);
                                }
                                if ( Util.size((List)value) == 0 ) {
                                    value = null;
                                }
                            }
                        }
                        if ( value != null )
                            spAttrs.put(name,value);
                    }
                }
            }
            if (log.isDebugEnabled())
                log.debug("Finshed coercion for identity : " +  identity + " of Type : " + type +
                        " with display name: " + displayName );
            if ( schema.includePermissions() ) {
                Object perms = object.get(ATTR_DIRECT_PERMISSIONS);
                if ( perms != null ) {
                    spAttrs.put(ATTR_DIRECT_PERMISSIONS, perms);
                }
            }

            // check keys for names that start with "IIQ" (or "CIQ" for backward
            // compatibility) and add them automatically to avoid needing schema
            // entries for our internal attributes
            Iterator<String> keys = object.keySet().iterator();
            while ( keys.hasNext() ) {
                String key = keys.next();
                if ( ( key != null ) && ( key.startsWith("IIQ") || key.startsWith("CIQ") ) ) {
                    Object value = object.get(key);
                    spAttrs.put(key, value);
                }
            }
            ro.setAttributes(spAttrs);
            return ro;

        }

        /**
         * Return all resourceObjects for a given endpoint
         */
        protected class EndPointIterator extends AbstractObjectIterator<ResourceObject> {

            EndPoint _endpoint;
            Iterator<Map<String, Object>> _restIterator = null;
            Map<String, Object> _ruleState;
            Map<String, Object> _next;

            public EndPointIterator(Schema schema, EndPoint endpoint) {
                super(schema);
                _endpoint = endpoint;
                _ruleState = new HashMap<String, Object>();
            }

            @Override
            public boolean hasNext() {
                boolean hasNext = false;
                if (null != _restIterator) {
                    hasNext = _restIterator.hasNext();
                }

                if (!hasNext) {
                    _restIterator = null;

                    Iterator<Map<String, Object>> it;
                    try {
                        it = getIterator();
                        hasNext = (it != null) && it.hasNext();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return hasNext;
            }

            @Override
            public ResourceObject next() {
                try {
                    //Update aggState from previous record since it would have now been processed
                    updateApplicationAggState(_next, _schema);
                    _next = _restIterator.next();
                    if (_next != null) {
                        _restIterator.remove();
                    }
                    ResourceObject r = transformObject(getSchema(), _next, _ruleState);
                    return r;
                } catch (Exception ce) {
                    log.error("Exception getting next element" + ce);
                    throw new RuntimeException(ce);
                }
            }

            /**
             * This method is use to create iterator and start aggregation for group
             * and account.
             *
             * @return
             * @throws ConnectorException
             */
            @SuppressWarnings("unchecked")
            public Iterator<Map<String, Object>> getIterator()
                    throws ConnectorException {
                try {

                    //
                    if (_endpoint != null) {
                        _restIterator = runEndPoint(_endpoint, _schema);
                    }

                } catch (Exception e) {
                    if (log.isErrorEnabled())
                        log.error(
                                "Exception in REST Web Service " + getObjectType()
                                        + " aggregation: " + e.getMessage(), e);
                    throw new ConnectorException(e.getMessage(), e);
                }

                return _restIterator;
            }

            @Override
            public void close() {
                try {
                    if (_endpoint != null && Util.isNotNullOrEmpty(_endpoint.getStringAttributeValue(CONFIG_SCROLL_ID))) {
                        //Close the scroll
                        runEndPoint(getScrollCleanupEndpoint(_endpoint), _schema);
                    }
                } catch (Exception e) {
                    log.error("Exception closing iterator" + e);
                    //We can probably swallow this, since theres nothing we can really do. All work has already been
                    //completed.
                }
            }
        }
    }
}
