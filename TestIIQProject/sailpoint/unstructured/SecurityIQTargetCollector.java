package sailpoint.unstructured;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.TargetHostConfig;
import sailpoint.tools.Util;
import sailpoint.object.AccessMapping;
import sailpoint.object.Rule;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Created by ryan.pickens on 8/20/15.
 */
public class SecurityIQTargetCollector extends AbstractTargetCollector {

    ///////////////////////////////////////////////////////////////////////
    //Static Variables
    ///////////////////////////////////////////////////////////////////////

    //Connection Information
    public static final String CONFIG_DBURL = JdbcUtil.ARG_URL;
    public static final String CONFIG_DRIVER_CLASS = JdbcUtil.ARG_DRIVER_CLASS;
    public static final String CONFIG_DB_USER = JdbcUtil.ARG_USER;
    public static final String CONFIG_DB_PASS = JdbcUtil.ARG_PASSWORD;
    public static final String CONFIG_SCHEMA_NAME = "schemaName";


    //Statement config
    public static final String CONFIG_RESULT_SET_TYPE = "resultSetType";
    public static final String CONFIG_RESULT_SET_CONCURRENCY = "resultSetConcurrency";



    //SQL config
    //Default schema is "whiteops"
    public static String DEFAULT_SCHEMA_NAME = "whiteops";
    //View used to query if we want all Targets/Permissions for a given host without hierarchy
    public static final String UNFILTERED_VIEW_NAME = "ra_entitlements_user_role_view";
    //View used to get the Hierarchy of Targets/Permissions. This is used when a specific path is set in the
    //targetHostConfig
    public static final String HIERARCHY_VIEW_NAME = "ra_view_user_role_function_hierarchy";
    //BAM Table. Used to get information regarding TargetHosts
    public static final String TARGET_HOST_QUERY = "SELECT b.id, b.name, bt.full_path_case_sensitive FROM {0}.bam b," +
            " {1}.bam_type bt where b.bam_type_id = bt.id";
    public static final String BAM_TABLE = "bam";


    //Function used to compute unique_path_hash
    public static final String UNIQUE_PATH_COMPUTATION = " (CONVERT([uniqueidentifier],hashbytes('md5',CONVERT([nvarchar](max),?,(0))+CONVERT([nvarchar](max),?,(0))),(0)))";
    //Target Table. Used to lookup the id given a unique hash
    public static final String BUSINESS_RESOURCE_TABLE = "business_service";
    public static final String UNIQUE_PATH_COLUMN = "unique_path_hash";
    public static final String PATH_HASH_FUNC = "uniqueHashFunction";


    //TargetHost Configurations
    public static final String INCLUDED_TARGET_HOSTS = "targetHosts";
    public static final String EXTRA_VIEW_COLUMNS = "extraColumns";
    public static final String TARGET_HOST_BAM_NAME = "bamName";
    public static final String TARGET_HOST_BAM_ID = "bamId";
    public static final String TARGET_HOST_CASE_SENSITIVE = "caseSensitive";


    //Note: We MUST order by br_id in order for the targets to be built correctly
    public static final String orderBy = defaultIterateColumns.br_id.name();
    //TODO:Need to get the UID added (waiting on SIQ team)
    // role_uid, user_uid
    //TODO:Need to add size to the view
    public static enum defaultIterateColumns {
        //ID of the Business Resource
        br_id,
        //Name of the Business Resource
        br_name,
        //Size of the Business Resource
        size,
        //Full path of the Business Resource
        full_path,
        //Name of the BAM
        bam_name,
        //Permission name
        permission_type_name,
        is_allow,
        is_inherited,
        is_effective,
        description,
        row_type,
        user_uid,
        //SIQ Id for the User
        user_id,
        //Will use this to correlate (May be better to use UID)
        user_full_name,
        role_uid,
        //SIQ ID for the Role
        role_id,
        //Will use this to correlate (UID may be better)
        role_name,
        role_entity_type_name,
        role_domain


    }




    public static final String USER_ROW_TYPE = "U";
    public static final String GROUP_ROW_TYPE = "R";
    public static final String CONFIG_AGG_INHERITED = "aggregateInherited";



    private static Log _log = LogFactory
            .getLog(AbstractTargetCollector.class);


    Connection _connection;
    String _dburl;
    String _userName;
    String _password;
    String _driverClass;
    String _schemaName = DEFAULT_SCHEMA_NAME;
    //Allow the configuration to provide extra columns from the view that may be of interest
    List<String> _extraColumns;
    //BAM Config
    List<TargetHostConfig> _bamConfigs;
    Iterator _bamConfigIter;
    boolean _aggregateInherited;
    //Used to override the function used to convert full_path+bamId to unique_path_hash
    String _hashFunction;


    public SecurityIQTargetCollector(TargetSource source) throws GeneralException {
        super(source);
        init();
    }

    public void init() throws GeneralException {

        _dburl = getStringAttribute(CONFIG_DBURL);
        _userName = getStringAttribute(CONFIG_DB_USER);
        _password = getStringAttribute(CONFIG_DB_PASS);
        SailPointContext context = SailPointFactory.getCurrentContext();
        _password = context.decrypt(_password);
        _driverClass = getStringAttribute(CONFIG_DRIVER_CLASS);
        _connection = null;
        if (Util.isNotNullOrEmpty(getStringAttribute(CONFIG_SCHEMA_NAME))) {
            _schemaName = getStringAttribute(CONFIG_SCHEMA_NAME);
        }
        _extraColumns = getListAttribute(EXTRA_VIEW_COLUMNS);
        getBamConfigs();
        _aggregateInherited = getBooleanAttribute(CONFIG_AGG_INHERITED);
        _hashFunction = getStringAttribute(PATH_HASH_FUNC);
    }

    /**
     * Parse the INCLUDED_TARGET_HOSTS into a List of TargeTHostConfigs
     */
    public void getBamConfigs() {
        if (_bamConfigs == null) {
            _bamConfigs = getListAttribute(INCLUDED_TARGET_HOSTS);
            if (!Util.isEmpty(_bamConfigs)) {
                _bamConfigIter = _bamConfigs.iterator();
            }
        }

    }

    /**
     * This checks the connection paramaters provided to the collector.
     * @throws GeneralException
     *
     * @ignore
     * We should test the queries in the future
     */
    @Override
    public void testConfiguration() throws GeneralException {
        Connection conn = null;
        //Ensure we can successfully get a connection
        try {
            conn = getConnection(null);
        } finally {
            closeConnection(conn);
        }
    }

    @Override
    public CloseableIterator<Target> iterate(Map<String, Object> ops) throws GeneralException {

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
        _connection = getConnection(null);

        iter = getTargetIterator(_connection);

        }
        finally {
            if (error) {
                closeConnection(_connection);
            }
        }

        return iter;
    }



    public Connection getConnection(Map<String, Object> options) throws GeneralException {

        Connection conn = null;
        Map<String, Object> connectionParams = new HashMap<String, Object>();
        if (options != null) {
            connectionParams = options;
        } else {
            //Build up connectionParams
            connectionParams.put(CONFIG_DRIVER_CLASS, _driverClass);
            connectionParams.put(CONFIG_DBURL, _dburl);
            connectionParams.put(CONFIG_DB_USER, _userName);
            connectionParams.put(CONFIG_DB_PASS, _password);

        }

        //Assume adaptive buffering is enabled
        conn = JdbcUtil.getConnection(connectionParams);

        return conn;

    }

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
            if (_log.isWarnEnabled()) {
                _log.warn("Could not get BAM["+hostName+"]");
                throw new GeneralException("Exception getting BAM["+hostName+"]");
            }
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
                if (_log.isWarnEnabled()) {
                    _log.warn("Could not get hash for path["+path+"] on targetHost["+hostName+"]");
                }
            }
        }

        if (Util.isEmpty(brHash)) {
            if (_log.isErrorEnabled()) {
                _log.error("Error getting hash for BRs["+Util.listToCsv(paths)+"]");
                throw new GeneralException("Could not get Business Resources["+Util.listToCsv(paths)+"]");
            }
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
                        if (_log.isWarnEnabled()) {
                            _log.warn("Could not find BusinessResource["+s+"] on targetHost["+hostName+"]");
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


    /**
     * Return the SQL statement to iterate over all Target/TargetPermissions for the given TargetHostConfig
     * @return
     */
    protected PreparedStatement getIterateStatement(TargetHostConfig thc)
        throws SQLException, GeneralException {

        if (Util.isNullOrEmpty(thc.getHostName())) {
            throw new GeneralException("No hostName provided in TargetHostConfig"+thc.toXml());
        }

        PreparedStatement stmt = null;

        StringBuffer sql = new StringBuffer();

        sql.append("select ");
        sql.append(sailpoint.tools.Util.listToCsv(Arrays.asList(defaultIterateColumns.values())));

        // wrap the columns in double quotes. Currently only need to worry about SQL Server but this
        // need to be revisited if SIQ supports other types of DBs
        if (!Util.isEmpty(_extraColumns)) {
            sql.append(", ");
            sql.append(sailpoint.tools.Util.listToQuotedCsv(_extraColumns, '"', true));
        }
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
                if (_log.isWarnEnabled()) {
                    _log.warn("Skipping TargetHost["+thc.getHostName()+"]. Could not get paths["+Util.listToCsv(thc.getPaths())+"]");
                    throw new GeneralException("Could not get paths["+Util.listToCsv(thc.getPaths())+"] " +
                            "for targetHost["+thc.getHostName()+"]");
                }
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
            sql.append(defaultIterateColumns.bam_name.name());
            sql.append(" = ");
            sql.append("?");
            sql.append(" and ");

        }


        //Should we allow user defined filters?
        sql.append(createFilterString());

        sql.append(" order by " + orderBy);

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

    /**
     * Return the SQL statement to get the list of available TargetHosts (BAM)
     * @return
     */
    protected String getTargetHostSQL(Map<String, Object> ops) {

        String s = MessageFormat.format(TARGET_HOST_QUERY, _schemaName, _schemaName);
        return s;
    }

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

            conn = getConnection(null);
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
        } finally {
            closeResult(rs);
            closeStatement(stmt);
            closeConnection(conn);
        }

        return hosts;
    }

    /**
     * Create a PreparedStatement to execute the given SQL.
     */
    protected PreparedStatement getStatement(Connection connection, String sql)
            throws java.sql.SQLException {

        // result type
        int rType =  ResultSet.TYPE_FORWARD_ONLY;
        String resultsetTypeStr = getStringAttribute(CONFIG_RESULT_SET_TYPE);
        if ( resultsetTypeStr != null ) {
            if (_log.isInfoEnabled()) {
                _log.info("Switching resultsettype to " + resultsetTypeStr);
            }

            if ( "TYPE_SCROLL_INSENSITIVE".equals(resultsetTypeStr) ) {
                rType = ResultSet.TYPE_SCROLL_INSENSITIVE;
            } else if ( "TYPE_SCROLL_SENSITIVE".equals(resultsetTypeStr) ) {
                rType = ResultSet.TYPE_SCROLL_SENSITIVE;
            }
        }

        // Concurrency settings
        int rConncurrent =  ResultSet.CONCUR_READ_ONLY;
        String concurancyStr = getStringAttribute(CONFIG_RESULT_SET_CONCURRENCY);
        if ( concurancyStr != null ) {
            if ( "CONCUR_UPDATABLE".equals(concurancyStr) ) {
                rConncurrent =  ResultSet.CONCUR_UPDATABLE;
            }
        }

        if ( _log.isDebugEnabled() ) {
            _log.debug("SQL statement[" + sql + "].");
        }

        PreparedStatement statement = null;
        statement = connection.prepareStatement(sql,
                rType,
                rConncurrent);


        return statement;
    }



    /**
     * Call close on the connection, this method performs
     * null check and catches exceptions which are logged
     * to the error log if thrown.
     *
     * @param connection Open Connection
     */
    protected void closeConnection(Connection connection ) {
        try {
            if ( connection != null ) {
                connection.close();
                connection = null;
            }
        }
        catch (java.sql.SQLException e) {
            if (_log.isErrorEnabled())
                _log.error("Failure closing JDBC connection: " + e.getMessage(), e);
        }
    }

    /**
     * Call  close() on the statement, performs null check
     * and logs exceptions thrown when trying to close.
     * <p>
     * All statements should be closed when they are no
     * longer needed.
     * </p>
     *
     * @param statement
     */
    protected void closeStatement(Statement statement) {
        try {
            if ( statement != null ) {
                statement.close();
                statement = null;
            }
        }
        catch (java.sql.SQLException e) {
            if (_log.isErrorEnabled())
                _log.error("Failure closing JDBC statement: " + e.getMessage(), e);
        }
    }

    /**
     * Call close() on the ResultSet, performs null check
     * and logs exceptions thrown when trying to close.
     * <p>
     * All results should be closed when they are no
     * longer needed.
     * </p>
     *
     * @param resultset The resultSet the needs to be closed
     */
    protected void closeResult(ResultSet resultset) {
        try {
            if ( resultset != null ) {
                resultset.close();
                resultset = null;
            }
        }
        catch (java.sql.SQLException e) {
            if (_log.isErrorEnabled())
                _log.error("Failure closing JDBC result set: " + e.getMessage(), e);
        }
    }

    protected TargetIterator getTargetIterator(Connection conn) {
        return new TargetIterator(conn);
    }


    public class TargetIterator implements CloseableIterator<Target> {

        private ResultSet _resultSet;

        Connection _connection;
        PreparedStatement _statement;

        Target _nextTarget;


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
        public Target next() {
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
        public Target getNextTarget() {
            Target nextTarg = null;

            try {
                if (_resultSet == null || _resultSet.isAfterLast()) {
                    //If no resultSet, or resultSet has been exhausted get the next TargetHost
                    getNextHost();
                }


                if (_resultSet != null) {

                    boolean hasElements = true;
                    if (_resultSet.isBeforeFirst()) {
                        //Need to call next to advance to the first record
                        hasElements = _resultSet.next();
                    }
                    if (hasElements) {
                        if (!_resultSet.isAfterLast()) {
                            int currentBR = _resultSet.getInt(defaultIterateColumns.br_id.name());
                            do {
                                //Get all rows for this BR
                                if (currentBR == _resultSet.getInt(defaultIterateColumns.br_id.name())) {
                                    nextTarg = buildTarget(nextTarg);



                                } else {
                                    //New BR_ID break out
                                    break;
                                }
                            }
                            while(_resultSet.next());

                            //Run Transformation Rule
                            Target transformedTarget = transformTarget(nextTarg);
                            if (transformedTarget != null) {
                                nextTarg = transformedTarget;
                            } else {
                                if ( _log.isDebugEnabled() ) {
                                    _log.debug("Target was returned null from transformation.");
                                }
                            }
                        }
                    }

                    if ( _log.isDebugEnabled() ) {
                        _log.debug("NormalizedTarget" + toXml(nextTarg));
                    }

                } else {
                    if (_log.isDebugEnabled()) {
                        _log.debug("Iteration Complete.");
                    }
                }


            } catch (SQLException e) {
                if (_log.isErrorEnabled()) {
                    _log.error("Error getting next Target" + e);
                }
                //Should we fail here, or try to keep iterating?
            } catch (GeneralException ge) {
                if (_log.isErrorEnabled()) {
                    _log.error("Error getting next Target" + ge);
                }
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


                    if (_log.isDebugEnabled()) {
                        _log.debug("Fetching targets for BAM["+config.getHostName()+"]");
                    }
                    _statement = getIterateStatement(config);
                    _resultSet = _statement.executeQuery();
                }
                catch (SQLException e) {
                    if (_log.isErrorEnabled()) {
                        _log.error("Error getting Targets for BAM["+config.getHostName()+"]" + e);
                    }
                    //If Exception, try next BAM
                    return getNextHost();
                }
                catch (GeneralException ge) {
                    if (_log.isErrorEnabled()) {
                        _log.error("Error getting Targets for BAM["+config.getHostName()+"]" + ge);
                    }
                    //If Exception, try next BAM
                    return getNextHost();
                }
            }

            return _resultSet;
        }


        //Create/Update the current target with the current resultSet row
        public Target buildTarget(Target target) throws SQLException {
            if (target == null) {
                //If null, create the target
                target = new Target();
                target.setName(_resultSet.getString(defaultIterateColumns.br_name.name()));
                target.setNativeObjectId(_resultSet.getString(defaultIterateColumns.br_id.name()));
                target.setFullPath(_resultSet.getString(defaultIterateColumns.full_path.name()));
                target.setTargetSize(_resultSet.getLong(defaultIterateColumns.size.name()));
                target.setTargetHost(_resultSet.getString(defaultIterateColumns.bam_name.name()));
            }

            //Build the AccessMapping
            AccessMapping mapping = new AccessMapping();
            mapping.setRights(_resultSet.getString(defaultIterateColumns.permission_type_name.name()));
            mapping.setInherited(_resultSet.getBoolean(defaultIterateColumns.is_inherited.name()));
            mapping.setEffective(_resultSet.getInt(defaultIterateColumns.is_effective.name()));
            mapping.setAllow(_resultSet.getBoolean(defaultIterateColumns.is_allow.name()));


            if (USER_ROW_TYPE.equalsIgnoreCase(_resultSet.getString(defaultIterateColumns.row_type.name()))) {
                //User Permission
                mapping.addNativeId(_resultSet.getString(defaultIterateColumns.user_id.name()));
                mapping.setAttribute(defaultIterateColumns.user_full_name.name(),
                        _resultSet.getString(defaultIterateColumns.user_full_name.name()));
                mapping.setAttribute(defaultIterateColumns.user_uid.name(),
                        _resultSet.getString(defaultIterateColumns.user_uid.name()));
                target.addAccountAccess(mapping);
            } else {
                //Group Permission
                mapping.addNativeId(_resultSet.getString(defaultIterateColumns.role_id.name()));
                mapping.setAttribute(defaultIterateColumns.role_name.name(),
                        _resultSet.getString(defaultIterateColumns.role_name.name()));
                mapping.setAttribute(defaultIterateColumns.role_entity_type_name.name(),
                        _resultSet.getString(defaultIterateColumns.role_entity_type_name.name()));
                mapping.setAttribute(defaultIterateColumns.role_domain.name(),
                        _resultSet.getString(defaultIterateColumns.role_domain.name()));
                mapping.setAttribute(defaultIterateColumns.role_uid.name(),
                        _resultSet.getString(defaultIterateColumns.role_uid.name()));
                target.addGroupAccess(mapping);
            }

            return target;

        }

        public Target transformTarget(Target nativeTarget) throws GeneralException {
            Target target = nativeTarget;
            TargetSource source = getTargetSource();
            if ( source == null) {
                throw new GeneralException("Source was not found!");
            }
            Rule rule = source.getTransformationRule();
            if ( rule != null ) {
                HashMap<String,Object> ruleContext = new HashMap<String,Object>();
                ruleContext.put("collector", this);
                ruleContext.put("target", nativeTarget);
                ruleContext.put("targetSource", source);
                //Add resultSet as a map to allow mapping extraColumns to the Target
                ruleContext.put("results", createResultMap());

                Object o = null;
                try {
                    o = getConnectorServices().runRule(rule, ruleContext);
                } catch (Exception e) {
                    throw new GeneralException(e);
                }
                if (o instanceof Target) {
                    target = (Target) o;
                } else {
                    throw new GeneralException("Rule did not return a target object.");
                }
            }

            return target;
        }

        //Create a map representation of the resultSet
        private Map<String, Object> createResultMap() {
            Map<String, Object> resultMap = new HashMap<String, Object>();
            try {
                ResultSetMetaData meta = _resultSet.getMetaData();
                int columnCount = meta.getColumnCount();
                for (int i=0; i< columnCount; i++) {
                    resultMap.put(meta.getColumnName(i), _resultSet.getObject(i));
                }
            } catch (SQLException e) {
                if(_log.isErrorEnabled()) {
                    _log.error("Error generating map from resultSet");
                }
            }

            return resultMap;
        }

    }
}
