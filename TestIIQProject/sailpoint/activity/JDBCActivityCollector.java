/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.ActivityFieldMap;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Rule;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;


/**
 * An ActivityCollector that can read data from a database 
 * and turn them into ApplicationActivity objects.
 * <p>
 * The first job of this collector is to read in the data
 * from one or more tables. Then for each resulting row 
 * returned from a the supplied sql query this collector
 * will attempt to TRANSFORM the data in the database into
 * our ApplicationActivityObject.
 * </p>
 * <p>
 * Much of the transformation functionality is supplied by the abstract
 * collector. To transform native values, tou can specify a rule 
 * per field or execute a global transoformation script which 
 * will be given the entire row data and returns an activity object.
 * 
 * NOTE: The field level rules are mostly worthless and should either 
 * be moved to a more "expression" type language or removed all 
 * together.  In practice, most of the transformations are likely to
 * happen in a global transformation rule.
 */
public class JDBCActivityCollector extends AbstractActivityCollector {
   
    private static Log log = LogFactory.getLog(JDBCActivityCollector.class);

    // Configuration attributes
    public static final String CONFIG_CONDITION_BUILDER = 
        "conditionBuilderRule";
    public static final String CONFIG_POSITION_BUILDER = 
        "positionConfigBuilderRule";
    public static final String CONFIG_SQL = "sql";
    // for cases where we can't get the columns from the 
    // result set's metadata
    public static final String CONFIG_COLUMN_NAMES =
        "columnNames";

    // not going to expose these low level attributes, but are here
    // in case we need them during pocs and/or deployments.
    public static final String CONFIG_RESULT_TYPE = "resultSetType";
    public static final String CONFIG_DISABLE_LAST_CHECK = 
        "disableLastRowCheck";
    public static final String CONFIG_RESULT_CONCURRENCY = 
        "resultSetConcurrency";
    /**
     * Create a JDBCActivityCollector given the datasouce
     */
    public JDBCActivityCollector(ActivityDataSource ds) {
        super(ds);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // ActivityConnector methods
    //
    ///////////////////////////////////////////////////////////////////////////

    public void testConfiguration(Map<String, Object> options)
        throws GeneralException {

        Connection connection =  null;
        try {
            connection = getConnection();
        } finally {
            closeConnection(connection);
            connection = null;
        }
    }

    /**
     * Returns an iterator over ApplicationActivity objects.
     * @param options Map of options
     */
    @SuppressWarnings("unchecked")
    public CloseableIterator<ApplicationActivity>
              iterate( Map<String, Object> options) throws GeneralException {

        CloseableIterator iterator = null;
        String sql = getRequiredStringAttribute(CONFIG_SQL);
        sql = processSQL(sql);
        if ( log.isDebugEnabled() ) 
            log.debug("SQL: " + sql);
        
        try {
            Connection connection = getConnection();           
            PreparedStatement statement = getStatement(connection, sql);
            boolean success = statement.execute();
            if ( success ) { 
                ResultSet results = statement.getResultSet();
                if ( results == null ) {
                    throw new GeneralException("Result set was null. SQL : "+
                                               sql);
                }
                iterator = new JDBCActivityIterator(connection, results);
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }
        return iterator;
    }

    /**
     * Returns a list of the attributes definitions that makeup the
     * the settings that are neccessary for the setup of this
     * ActivityCollector.
     */
    public List<AttributeDefinition> getDefaultConfiguration()  {
        List<AttributeDefinition> config = new ArrayList<AttributeDefinition>();
        config.add(new AttributeDefinition(JdbcUtil.ARG_USER,
                                           AttributeDefinition.TYPE_STRING,
                                           "Connection User",
                                           true,
                                           "system"));
        config.add(new AttributeDefinition(JdbcUtil.ARG_PASSWORD,
                                           AttributeDefinition.TYPE_SECRET,
                                           "Password"));
        config.add(new AttributeDefinition(JdbcUtil.ARG_URL,
                                           AttributeDefinition.TYPE_STRING,
                                           "url to the database.",
                                           true,
                                           "jdbc:mysql://localhost/db"));
        config.add(new AttributeDefinition(JdbcUtil.ARG_DRIVER_CLASS,
                                           AttributeDefinition.TYPE_STRING,
                                           "JDBC DriverClass",
                                           true,
                                           "com.mysql.cj.jdbc.Driver"));
        config.add(new AttributeDefinition(CONFIG_SQL,
                                           AttributeDefinition.TYPE_STRING,
                                           "Sql statement that can be used to query activity from the database.", true));
        config.add(new AttributeDefinition(CONFIG_CONDITION_BUILDER,
                                           AttributeDefinition.TYPE_STRING,
                                           "Rule that will convert the lastPosition configuration map into sql conditions."));
        config.add(new AttributeDefinition(CONFIG_POSITION_BUILDER,
                                           AttributeDefinition.TYPE_STRING,
                                           "Rule that will convert last row in the ResultSet into a configuration map so it can be persisted into the SailPoint database."));
        return config;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utilities
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
                }
            }
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("positionCondition", positionCondition);
            updatedSQL = Util.expandVariables(sql, map);
        }
        return updatedSQL;
    }

    /**
     * Build up a perpared statement for the given connection and sql.
     * Use the configuration of the collector to set some of the
     * low-level result configuration. Default to the typical settings
     * needed to read and re-read data.
     */
    protected PreparedStatement getStatement(Connection con, String sql) 
        throws java.sql.SQLException {

        int resultSetType =  ResultSet.TYPE_SCROLL_INSENSITIVE;
        String resultSetTypeStr = getStringAttribute(CONFIG_RESULT_TYPE);
        if ( resultSetTypeStr != null ) {
            if ( resultSetTypeStr.equals("TYPE_FORWARD_ONLY") ) {
                resultSetType = ResultSet.TYPE_FORWARD_ONLY;
            } else
            if ( resultSetTypeStr.equals("TYPE_SCROLL_INSENSITIVE") ) {
                resultSetType = ResultSet.TYPE_SCROLL_INSENSITIVE;
            } else
            if ( resultSetTypeStr.equals("TYPE_SCROLL_SENSITIVE") ) {
                resultSetType = ResultSet.TYPE_SCROLL_SENSITIVE;
            }
        }

        int resultSetConcurrency = ResultSet.CONCUR_READ_ONLY;
        String resultConcurrencyStr = 
            getStringAttribute(CONFIG_RESULT_CONCURRENCY);
        if ( resultConcurrencyStr != null ) {
            if ( resultConcurrencyStr.equals("CONCUR_READ_ONLY") ) {
               resultSetConcurrency = ResultSet.CONCUR_READ_ONLY;
            } else
            if ( resultConcurrencyStr.equals("CONCUR_UPDATABLE") ) {
                // can't think of when we'd want this
                // but its an option so expose it!
               resultSetConcurrency = ResultSet.CONCUR_UPDATABLE;
            }
        }
        return con.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }


    /**
     * Given the stored position config information for this databse
     * build a string that can be used to query the database
     * for records that have been updated. 
     */
    public String getPositionConditionString(Map<String,Object> config) 
        throws GeneralException {

        String condition = null;

        if ( config == null ) {
            return null;
        }

        SailPointContext context = getSailPointContext();
        String whereRuleName = getStringAttribute(CONFIG_CONDITION_BUILDER);
        if ( whereRuleName != null ) {
            Rule whereRule = context.getObjectByName(Rule.class, whereRuleName);
            if (whereRule != null) {
                Map<String,Object> ctx = new HashMap<String,Object>();
                ctx.put("config", config);
                Object o = context.runRule(whereRule, ctx);
                if ( o != null ) {
                    if ( o instanceof String ) {
                        condition = (String)o;
                    } else {
                        condition = o.toString();
                    }
                }
            }
        }
        return condition;
    }

    /**
     * Call down to JdbcUtil and create a connection to the 
     * configured datasource. The connection returned must 
     * also be closed, either via the closeConnection method
     * or by explicitly calling close() on the connection.
     */
    private Connection getConnection() throws GeneralException {
        String driverClass =
            getRequiredStringAttribute(JdbcUtil.ARG_DRIVER_CLASS);
        String url = getRequiredStringAttribute(JdbcUtil.ARG_URL);
        String user = getStringAttribute(JdbcUtil.ARG_USER);
        String password = getEncryptedAttribute(JdbcUtil.ARG_PASSWORD);

        Connection connection =  null;
        connection = JdbcUtil.getConnection(driverClass,
                                            null, 
                                            url, 
                                            user, 
                                            password);
        return connection;
    }

    /*
     * Simple close wrapper 
     */
    protected void closeConnection(Connection connection ) {
        try {
            if ( connection != null ) {
                connection.close();
            }
        } catch(SQLException e) {
            if (log.isErrorEnabled())
                log.error("Failure closing JDBC connection: " + e.getMessage(), e);
        }
    }

    @Override
    protected Object transformValue(SailPointContext sp,
                                    Map<String,Object> context, 
                                    ActivityFieldMap fieldMap) 
        throws GeneralException {

        Object obj = null;

        HashMap<String,Object> values = new HashMap<String,Object>(context);
        HashMap<String,Object> rowCols = (HashMap<String,Object>)values.get("rowColumns");
        if ( rowCols != null ) {
            // flatten these
            values.putAll(rowCols);
        }
        return super.transformValue(sp, values, context, fieldMap);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Closeable Iterator
    //
    ///////////////////////////////////////////////////////////////////////////

    protected class JDBCActivityIterator 
        implements CloseableIterator<ApplicationActivity> {

        protected ResultSet _result;
        protected Connection _connection;
        protected List<String> _colNames;
        protected List<ActivityFieldMap> _fieldMap;
        protected boolean _checkForLast;

        @SuppressWarnings("unchecked")
        public JDBCActivityIterator(Connection con, ResultSet result ) 
            throws GeneralException {

            _connection = con;
            _checkForLast = true;
            _result = result;
            _colNames = getColumnNames();
            if ( log.isDebugEnabled() ) 
                log.debug("ColumnNames : " + Util.listToCsv(_colNames));
            
            _fieldMap = 
               (List<ActivityFieldMap>)getRequiredAttribute(CONFIG_FIELD_MAP);

            String resultSetTypeStr = getStringAttribute(CONFIG_RESULT_TYPE);
            if ( resultSetTypeStr != null ) {
                if ( resultSetTypeStr.equals("TYPE_FORWARD_ONLY") ) {
                    _checkForLast = false;
                }
            }
            boolean disabled = getBooleanAttribute(CONFIG_DISABLE_LAST_CHECK);
            if ( disabled ) {
                _checkForLast = false;
            }
        }

        public boolean hasNext() {
            boolean hasNext = false;
            if ( _result == null ) {
                return hasNext;
            }
            try {
                hasNext = _result.next();
                if ( isLast(_result) )  {
                    Map<String,Object> cfg = buildPositionConfig(_result);
                    if ( cfg != null ) {
                        setPositionConfig(cfg);
                    }
                }
            } catch(Exception e ) {
                throw new RuntimeException(e);
            }
            return hasNext;
        }

        public ApplicationActivity next() {
            ApplicationActivity activity = nextActivity();
            if (activity == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            return activity;
        }

        private boolean isLast(ResultSet result) throws SQLException {
            if ( !_checkForLast ) {
                // always check
                return true;
            }
            return (_result != null  ) ? _result.isLast() : true;
        }

        /**
         * Build a Map<String,Object> for each column returned, keep nulls
         * so scripts can know the difference between null and empty.
         */
        private ApplicationActivity nextActivity() {

            ApplicationActivity activity = null;
            try {
                Map<String,Object> context = new HashMap<String,Object>();

                // 
                // Build a map of the row column values, so we can represent null.
                //
                Map<String,Object> rowColumns = new HashMap<String,Object>();
                for ( String colName : _colNames ) {
                    Object value = _result.getObject(colName);
                    rowColumns.put(colName, value);
                }
                context.put("rowColumns", rowColumns);

                activity = buildActivity(getSailPointContext(),
                                         _fieldMap, 
                                         context);
            } catch (Exception e ) {
               throw new RuntimeException(e);
            }
            if ( log.isDebugEnabled() ) 
                log.debug("activity:" + toXml(activity));
            
            return activity;
        }

        @SuppressWarnings("unchecked")
        private List<String> getColumnNames() throws GeneralException {

            List<String> colNames = getListAttribute(CONFIG_COLUMN_NAMES);
            if ( colNames != null ) {
                return colNames;
            }
            // else use the result meta data
            colNames = new ArrayList<String>();
            try {
                ResultSetMetaData metaData = _result.getMetaData();
                if ( metaData == null ) 
                    throw new GeneralException("Could not retrieve metaData!");

                int cols = metaData.getColumnCount();
                for ( int i=1 ; i<=cols; i++ ) {
                    String colName = metaData.getColumnName(i);
                    colNames.add(colName);
                }
            } catch(SQLException e) {
                throw new GeneralException(e);
            }
            return colNames;
        }

        /**
         * Given a current position in the result set, build a 
         * Map<String,String> that can be serialized to the sailpoint database.
         * This object will be used to build the where clause for 
         * subsequent incrmental calls into this collector.
         */
        @SuppressWarnings("unchecked")
        private Map<String,Object> buildPositionConfig(ResultSet row) 
            throws GeneralException {

            Map<String,Object> config = null;
            String ruleName = getStringAttribute(CONFIG_POSITION_BUILDER);
            if ( ruleName != null ) {
                SailPointContext context = getSailPointContext();
                Rule positionRule = context.getObjectByName(Rule.class,ruleName);
                if ( positionRule!= null ) {
                    Map<String,Object> ctx = new HashMap<String,Object>();
                    ctx.put("row", row);
                    Object o = context.runRule(positionRule, ctx);
                    if ( o != null ) {
                        if ( o instanceof Map ) {
                            config = (Map)o;
                        }
                    }
                }
            }
            return config;
        }

        public void close() {
            closeConnection(_connection);
            _connection = null;
        }
    }
}
