/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// Author(s): Jeff Larson
//
// Description:
//
// A bag of JDBC utilities.
//

package sailpoint.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;

import connector.common.messages.ConnectorMessageKeys;


/**
 * Various JDBC utilities.
 */
public class JdbcUtil {

    //////////////////////////////////////////////////////////////////////
    // 
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the oracle driver -- used to support Oracle clobs as a special case
     */
    private static final String ORACLE_DRIVER_NAME = "Oracle JDBC driver";
    
    /**
     * if this pattern case insensitive is present in jdbc driver name
     * it is mysql
     */
    private static final String MYSQL_PATTERN = "mysql";

    /**
     * Name of our specialized Oracle CLOB handler.
     */
    public static String ORACLE_CLOB_HANDLER = "sailpoint.tools.OracleClobHandler";

    /**
     * Since this handler uses oracle specific code and we do not want the
     * the runtime dependency only load this by name.  
     */
    private static ClobHandler _handler;

    /**
     * Static Map of PoolingDataSource to control the pool of connections
     * to each database. The default configuration of the pool will evict
     * idle connections so the data in this map should be fairly contained
     * to a Datasource without connections when its not being used.
     * 
     * This Map is keyed by the user@url if user is present otherwise
     * just the url is used as the key;
     */
    private static Map<String,PoolingDataSource> _dataSourcePool = new HashMap<String,PoolingDataSource>();
    private static org.apache.commons.logging.Log log = LogFactory.getLog(JdbcUtil.class);
    private static List<String> _unWantedSymbolList=new ArrayList<String>();
    private static boolean _b_IsUnWantedListSizeZero=true;

    /**
     * The DB2 product name.
     */
    public static final String DB2_PRODUCT_NAME = "DB2";

    /**
     * The MySQL product name.
     */
    public static final String MYSQL_PRODUCT_NAME = "MySQL";

    /**
     * The MS SQL Server product name.
     */
    public static final String MS_SQL_PRODUCT_NAME = "Microsoft SQL Server";

    /**
     * The Oracle product name.
     */
    public static final String ORACLE_PRODUCT_NAME = "Oracle";

    /**
     * The DB2 script file extension.
     */
    public static final String DB2_EXT = "db2";

    /**
     * The MySQL script file extension.
     */
    public static final String MYSQL_EXT = "mysql";

    /**
     * The MS SQL Server script file extension.
     */
    public static final String MSSQL_EXT = "sqlserver";

    /**
     * The Oracle script file extension.
     */
    public static final String ORACLE_EXT = "oracle";
    
    public enum ConnectorType 
    {
        OracleApplications,
        MSSQLServerConnector,
        PeopleSoftHRMSConnector,
        SybaseDirectConnector;
    }
    //////////////////////////////////////////////////////////////////////
    // 
    // SQL Statement Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Perform a SQL statement for side effect.
     */
    public static void sql(Connection c, String stmt, String... arg)
        throws GeneralException {

        try {
            PreparedStatement s = c.prepareStatement(stmt);
            try {
                if (arg != null)
                    for (int i=0; i<arg.length; i++)
                        s.setString(i+1, arg[i]);
                s.execute();
                // flush the results?
            }
            finally {
                s.close();
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Static escapeSpecialCharactor() method will convert the special characters 
     * into the readable format which will be compatible for different managed system.
     * For the future use and compatibility of every database connector the connectorType 
     * is given in the signature of method which will recognize the different database.
     * 
     *@return String   formated string which will be compatible with managed system environment
     * 
     * NOTE this method is introduced for the e-fix of bug#20623-6.2p and 6.3 Oracle Apps 
     * connector fails on password update due to ' apostrophe character in description
     */
    public static String escapeSpecialCharactor(String name,ConnectorType type) {

        if(type.equals(ConnectorType.OracleApplications) || type.equals(ConnectorType.MSSQLServerConnector) || type.equals(ConnectorType.PeopleSoftHRMSConnector) || type.equals(ConnectorType.SybaseDirectConnector)) {

            if (name.indexOf("'") > -1) {
                name = name.replace("\'","\'\'");
            }  
        }
        return name;
    } 
       

    /**
     * Perform an UPDATE statement and return the number
     * of rows affected.
     */
    public static int update(Connection c, String stmt, String arg)
        throws GeneralException {

        int updates = 0;

        try {
            PreparedStatement s = c.prepareStatement(stmt);
            try {
                if (arg != null)
                    s.setString(1, arg);
                updates = s.executeUpdate();
            }
            finally {
                s.close();
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }

        return updates;
    }

    /**
     * Some drivers have a limit on the size of the string that
     * can be passed to setString(), the older Oracle driver we use
     * has a limit of 2000. This method will use setAsciiStream instead.
     * Could do this automatically in the update() method above, but
     * lots of other code uses that and the consequences are unknown.
     */
    public static int updateLong(Connection c, String stmt, String arg)
        throws GeneralException {
        int updates = 0;

        try {
            PreparedStatement s = c.prepareStatement(stmt);
            try {
                setClobParameter(s, 1, arg);
                updates = s.executeUpdate();
            }
            finally {
                s.close();
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }
        return updates;
    }

    /**
     * Set a clob parameter at a given position in the given PreparedStatement.
     * See {@link #updateLong(Connection, String, String)} for why we need to
     * use this.
     * See {@link #setOracleCLOBParameter(PreparedStatement, int, String)}
     * 
     * @param  ps     The PreparedStatement to set the parameter on.
     * @param  pos    The position of the clob parameter.
     * @param  value  The value of the clob parameter.
     * 
     * @return The PreparedStatement so calls can be chained.
     */
    public static PreparedStatement setClobParameter(PreparedStatement ps,
                                                     int pos,
                                                     String value)
        throws SQLException {
      
        if (value != null) {
            StringReader sr = new StringReader(value);
            ps.setCharacterStream(pos, sr, value.length());
        } else {
            ps.setNull(pos, Types.CLOB);
        }
        return ps;
    }

    /**
     * Use Oracle's extension's CLOB object to set a CLOB value.
     * The CLOB object returned from this method must be
     * freed using freeTemporary after the statement has been flushed 
     * to the database. 
     *
     * @param  st     The PreparedStatement to set the parameter on.
     * @param  index  The position of the clob parameter.
     * @param  val    The value of the clob parameter.
     *
     * @return the CLOB so it can be freed. Its return type is Object
     * so we do not have a dependency on the oracle jars.
     *
     */
    public static Object setOracleCLOBParameter(PreparedStatement st,
                                                int index, 
                                                String val) 
        throws SQLException {

        Object clob = null;
        try {
            if ( _handler == null ) {
                // Util method will throw if the class is not found or 
                // cannot be instantiated.
                _handler = (ClobHandler)Util.createObjectByClassName(ORACLE_CLOB_HANDLER);
            }
            clob = _handler.setClobParameter(st, index, val);
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
        return clob;
    }

    /**
     * Get a CLOB value as a String from the named field in the given ResultSet.
     * This streams the value using a character stream.
     * 
     * @param  rs    The ResultSet from which to retrieve the CLOB value.
     * @param  name  The name of the field in the ResultSet that holds the CLOB.
     * 
     * @return The String value of the CLOB (possibly null).
     */
    public static String getClobValue(ResultSet rs, String name)
        throws SQLException {
        
        // Use a reader to get the CLOB.
        Reader charReader = rs.getCharacterStream(name);

        // Null reader means the value was null.
        if (charReader==null)
            return null;

        // Read 2K blocks at a time into the string builder.
        StringBuilder sb = new StringBuilder();
        try {
            int amountRead;
            char[] buffer = new char[2048];

            while (-1 != (amountRead = charReader.read(buffer, 0, buffer.length))) {
                sb.append(buffer, 0, amountRead);
            }
        }
        catch (IOException ioe) {
            throw new RuntimeException("Error reading clob '" + name + "'", ioe);
        }
        finally {
            try {
                charReader.close();
            }
            catch (IOException e) {
                throw new RuntimeException("Error closing stream for clob '" + name +"'", e);
            }
        }

        return sb.toString();
    }

    public static int update(PreparedStatement s)
        throws GeneralException
    {
        int updates = 0;

        try {
            try {
                updates = s.executeUpdate();
            }
            finally {
                s.close();
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }
        
        return updates;
    }

    public static void sqlStream(Connection c, String stmt,
                 InputStream arg, int len)
        throws GeneralException {
        try {
            PreparedStatement s = c.prepareStatement(stmt);
            try {
                if (arg != null)
                    s.setAsciiStream(1, arg, len);
                s.execute();
            }
            finally {
                s.close();
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }
    }

    public static int queryInt(Connection c, String q, String arg)
        throws GeneralException {

        int value = 0;

        try {
            PreparedStatement s = c.prepareStatement(q);
            ResultSet rs = null;

            try {
                if (arg != null)
                    s.setString(1, arg);
                rs = s.executeQuery();
                if (rs.next()) {
                    value = rs.getInt(1);
                    // jdbc returns -1 to indicate null, convert to zero
                    if (rs.wasNull())
                        value = 0;
                }
            }
            finally {
                if (rs != null) rs.close();
                s.close();
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }

        return value;
    }

    public static java.util.Date queryDate(Connection c, String q, String arg)
        throws SQLException {

        java.sql.Timestamp value = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            rs = s.executeQuery();
            if (rs.next()) {
                // will this return null or do we have to check indicators?
                value = rs.getTimestamp(1);
            }
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return value;
    }

    public static String queryString(Connection c, String q, String arg)
        throws SQLException {

        String value = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            rs = s.executeQuery();
            if (rs.next())
                value = rs.getString(1);
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return value;
    }

    public static Clob queryClob(Connection c, String q, String arg)
        throws SQLException {

        Clob value = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            rs = s.executeQuery();
            if (rs.next()) {
                value = rs.getClob(1);
            }
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return value;
    }

    /** Returns the clob information in a db2 safe manner - See bug 6974 PH **/
    public static String queryClobString(Connection c, String q, String arg)
        throws SQLException {

        Clob value = null;
        String valueString = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            
            rs = s.executeQuery();
            if (rs.next()) {
                value = rs.getClob(1);
                if(value!=null) {
                    long len = value.length();
                    valueString = value.getSubString(1, (int)len);
                }
            }
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return valueString;
    }

    public static String getClobValue(Clob clob) throws SQLException {
        return clob.getSubString(1, (int) clob.length());
    }

    public static List queryList(Connection c, String q, String arg)
        throws SQLException {

        List value = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            rs = s.executeQuery();
            while (rs.next()) {
                if (value == null)
                    value = new ArrayList();
                value.add(rs.getString(1));
            }
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return value;
    }

    /**
     * Return a List of String[] containing the first n columns of the result.
     */
    public static List queryTableString(Connection c, String q, String arg, int cols)
        throws SQLException {

        List value = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            rs = s.executeQuery();

            while (rs.next()) {
                if (value == null)
                    value = new ArrayList();

                String[] row = new String[cols];
                value.add(row);

                for (int i = 0 ; i < cols ; i++)
                    row[i] = rs.getString(i+1);
            }
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return value;
    }

    public static List queryTable(Connection c, String q, String arg, int cols)
        throws SQLException {

        List value = null;

        PreparedStatement s = c.prepareStatement(q);
        ResultSet rs = null;
        ResultSetMetaData meta = null;

        try {
            if (arg != null)
                s.setString(1, arg);
            rs = s.executeQuery();
            meta = rs.getMetaData();

            while (rs.next()) {
                if (value == null)
                    value = new ArrayList();

                List row = new ArrayList();
                value.add(row);

                for (int col = 1 ; col <= cols ; col++) {
                    int type = meta.getColumnType(col);
                    if (type == Types.INTEGER) {
                        int ival = rs.getInt(col);
                        row.add(new Integer(ival));
                    }
                    else if (type == Types.BIGINT) {
                        long lval = rs.getLong(col);
                        row.add(new Long(lval));
                    }
                    else
                        row.add(rs.getString(col));
                }
            }
        }
        finally {
            if (rs != null) rs.close();
            s.close();
        }

        return value;
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Data Source
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Retrieves a JDBC 2.0 DataSource using JNDI.
     */
    public static DataSource getDataSourceObject(
    String factoryClass, String path)
        throws GeneralException {
        
        return getDataSourceObject(factoryClass, path, (String)null);
    }

    /**
     * Retrieves a JDBC 2.0 DataSource using JNDI.
     */
    public static DataSource getDataSourceObject(String factoryClass, 
                                                 String path, 
                                                 String providerUrl)
        throws GeneralException {

        Properties properties = null;
        if ( providerUrl!=null )
            {
                properties = new Properties();
                properties.put(Context.PROVIDER_URL, providerUrl);
            }
        return getDataSourceObject(factoryClass, path, properties);
    }

    /**
     * Retrieves a JDBC 2.0 DataSource using JNDI.
     */
    public static DataSource getDataSourceObject(String factoryClass, 
                                                 String path, 
                                                 Properties props)
        throws GeneralException {

        DataSource ds = null;
        try {
            if (props==null) {
                // If we have no Properties, create one.
                props = new Properties();
            }
            else {
                // Clone any Properties we are passed (bug#9871).
                // Not only is this practice nice and sanitary,
                // but we guard against passing an instance of 
                // an IDM-specific class into the directory service.
                Properties temp = new Properties();
                temp.putAll(props);
                props = temp;
            }
            if (factoryClass!=null) {
                props.setProperty(Context.INITIAL_CONTEXT_FACTORY, factoryClass);
            }
            //
            // Create the Initial Naming Context
            //
            Context ctx = new InitialContext(props);
            //
            // Retrieve a DataSource object through the naming service.
            //
            ds = (DataSource)ctx.lookup(path);
            if ( ds==null ) {
                String msg = "DataSource '"+path+"' was not found.";
                GeneralException ce = new GeneralException(msg);
                println(ce);
                throw ce;
            }
        }
        catch (NamingException ex) {
            println(ex);
            StringBuffer sb = new StringBuffer();
            sb.append("Failed to load JDBC DataSource '");
            sb.append( path );
            sb.append("':  ");
            GeneralException ce = new GeneralException(sb.toString(), ex);
            throw ce;
        }
        return ds;
    }

    static void println(Object o) { System.out.println(o); }

    //////////////////////////////////////////////////////////////////////
    // 
    // Connection with simple name/value pairs.
    //
    //////////////////////////////////////////////////////////////////////

    static public final String ARG_TYPE = "type";
    static public final String ARG_DRIVER_CLASS = "driverClass";
    static public final String ARG_DRIVER_PREFIX = "driverPrefix";
    static public final String ARG_URL = "url";
    static public final String ARG_HOST = "host";
    static public final String ARG_PORT = "port";
    static public final String ARG_DATABASE = "database";
    static public final String ARG_USER = "user";
    static public final String ARG_PASSWORD = "password";
    static public final String ARG_CON_PROPERTIES = "additionalConnProperties";
    static public final String ARG_SQL = "sql";
    static public final String ARG_ARG1 = "arg1";
    static public final String ARG_ARG2 = "arg2";
    static public final String ARG_ARG3 = "arg3";

    static public final String TYPE_ORACLE = "oracle";
    static public final String ORACLE_DRIVER = "oracle.jdbc.driver.OracleDriver";
    static public final String ORACLE_PREFIX = "java:oracle:thin";
    static public final String ORACLE_URL = "java:oracle:thin:@%h:%p:%d";

    /**
     * Given a URL template and arguments, format a template.
     * Recognized template characters are:
     *
     *   %  literal %
     *   h  host
     *   p  port
     *   d  database
     */
    static public String formatUrl(String template,
                   String host,
                   String port,
                   String database) {
        String url = null;

        // if there are no arguments, assume its a preformatted url
        if (host == null && port == null && database == null)
            url = template;
        else {
            StringBuffer b = new StringBuffer();

            int len = template.length();
            for (int i = 0 ; i < len ; i++) {
                char ch = template.charAt(i);
                if (ch != '%')
                    b.append(ch);
                else if (i+1 < len) {
                    i++;
                    ch = template.charAt(i);
                    if (ch == '%')
                        b.append(ch);
                    else if (ch == 'h')
                        b.append(host);
                    else if (ch == 'p')
                        b.append(port);
                    else if (ch == 'd')
                        b.append(database);
                }
            }
            url = b.toString();
        }
        return url;
    }

    /**
     * Establish a connection.  
     * It is relatively uncommon for a rule to call this directly,
     * though it might be used for more control
     * 
     * The caller must pass down sufficient arguments to establish a
     * connection.  The following arguments are recognized:
     * <pre>
     *   type            the name of one of the built in database types
     *   driverClass     JDBC driver class
     *   driverPrefix    name under which driver is registered
     *   url             full url or template
     *   host            host name/ip
     *   port            port
     *   database        database name (for example, master, personel)
     *   user            proxy user name
     *   password        password (text or encrypted)
     *   disablePooling  boolean to indicate if we should disable pooling
     *                   of connections. Pooling is enabled by default from
     *                   this method.
     * </pre>
     * 
     * Passing driverClass and driverPrefix or type is required
     * in order to register the JDBC driver. Passing type is easiest
     * if you want one of the common builtin types. If you pass none
     * of these, we will attempt the connection anyway hoping that the
     * driver has already been registered, this might fail.
     *
     * The recognized types are: oracle, sqlserver, sybase
     *
     * The "url" is required, it can either be a fullly formatted connection
     * url that includes host, port and database. Or it can be a template
     * into which the explicit host/port/database arguments will be inserted.
     * Using a url template is convenient if the host/port/database
     * are exposed as rule arguments.
     *
     * user and password are optionl if "url" is specified and the
     * user and password are encoded within the url. This is unusual
     * though some drivers support it. Normally they are passed
     * as explicit arguments.
     *
     * Connection pooling is handled via the commons-dbcp pooling.
     *
     * Beyond connection parameters the following arguments can be used:
     * <pre>
     *     sql          SQL statement to execute
     *     arg1         optional argument for sql statement
     *     maxActive    Maximum number of active connections in any one pool default 10
     *     maxIdle      Maximum idle object allowed in the thread defaults to 5
     *     evictRuns    Time in milliseconds to run the eviction thread defaults to 15mins
     *     minEvictIdle Time in milliseconds before an idle connection gets evicted
     * </pre>
     */
    static public Connection getConnection(Map args) 
            throws GeneralException {

        String type = (String)args.get(ARG_TYPE);
        String driverClass = (String)args.get(ARG_DRIVER_CLASS);
        String driverPrefix = (String)args.get(ARG_DRIVER_PREFIX);
        String url = (String)args.get(ARG_URL);

        // shouldn't have both type and explicit args but if we do,
        // prefer the explicit args
        if (type != null) {
            String typeClass = null;
            String typePrefix = null;
            String typeUrl = null;
            if (type.equals("oracle")) {
                typeClass = ORACLE_DRIVER;
                typePrefix = ORACLE_PREFIX;
                typeUrl = ORACLE_URL;
            }
            if (driverClass == null)
                driverClass = typeClass;
            if (driverPrefix == null)
                driverPrefix = typePrefix;
            if (url == null)
                url = typeUrl;
        }

        // url may be a template
        url = formatUrl(url,
                        (String)args.get(ARG_HOST),
                        (String)args.get(ARG_PORT),
                        (String)args.get(ARG_DATABASE));

        String user = (String)args.get(ARG_USER);
        String pass = null;
        Object o = args.get(ARG_PASSWORD);
        if (o != null)
            pass = o.toString();

        Connection con = null;

        Properties additionalProperties = null;
        // Check if there are any additional connection properties present in the args map.
        if (null != args.get(ARG_CON_PROPERTIES)) {
            additionalProperties = (Properties) args.get(ARG_CON_PROPERTIES);
        }
        Object disablePooling = args.get(ARG_POOL_DISABLE);
        if ( !Util.otob(disablePooling) ) {
            //
            //  Create a pooled connection using jcdc
            //
            if (driverClass != null)
                registerDriver(driverClass, driverPrefix);
            if (additionalProperties != null) {
                log.debug("Creating JDBC pooled connection with properties object");
                // Pooled connection with properties object
                con = getPooledConnection(url, user, pass, args, additionalProperties);
            } else {
                log.debug("Creating JDBC pooled connection without properties object");
                // Pooled connection without properties object
                con = getPooledConnection(url, user, pass, args);
            }

        } else {
            if (additionalProperties != null) {
                log.debug("Creating JDBC connection with properties object");
                // JDBC connection with properties object
                con = getConnection(driverClass, driverPrefix, url, user, pass, additionalProperties);
            } else {
                log.debug("Creating JDBC connection without properties object");
                // JDBC connection without properties object
                con = getConnection(driverClass, driverPrefix, url, user, pass);
            }
        }
        return con;
    }

    static public Connection getConnection(String driverClass, String driverPrefix,
            String url, String user, String password)
                    throws GeneralException {
        return getConnection(driverClass, driverPrefix, url, user, password, null);
    }

    /**
     * Simpler non-pooled connection.
     */
    static public Connection getConnection(String driverClass, String driverPrefix,
            String url, String user, String password, Properties additionalProperties)
                    throws GeneralException {

        if (driverClass != null)
            registerDriver(driverClass, driverPrefix);

        Connection con = null;
        try {
            if (null != additionalProperties) {
                if (user !=null)
                    additionalProperties.put(ARG_USER, user);
                if (password !=null)
                    additionalProperties.put(ARG_PASSWORD, password);
                con = DriverManager.getConnection(url, additionalProperties);
            } else {
                con = DriverManager.getConnection(url, user, password);
            }
        }
        catch (SQLException e) {
            throw new GeneralException(e);
        }
        return con;
    }


    /**
     * Get a pooled connection to the specified database.
     */
    private static Connection getPooledConnection(String url, String user, String password, Map<String,Object> poolOptions)
            throws GeneralException {
        return getPooledConnection(url, user, password, poolOptions, null);
    }

    public static void registerDriver(String driverClass, String prefix)
        throws GeneralException {

        Driver d = null;

        // First ask the DriverManager whether it recognizes the prefix.
        // Sometimes we may not have a prefix, in which case we just
        // register the driver class each time.

        if (prefix != null && prefix.length() > 0) {
            try {
                d = DriverManager.getDriver(prefix);
            }
            catch (SQLException e) {
            }
        }

        if (d == null) {
            // Assume the driver isn't registered, use reflection
            // to get the class and register it.
            try {
                Class dc = Class.forName(driverClass);
                if ( !isDriverRegistered(driverClass) ) {
                    d = (Driver)dc.newInstance();
                    DriverManager.registerDriver(d);
                }
            }
            catch (Exception e) {
                throw new GeneralException(e);
            }
        }
    }

    /**
     * Close a connection quietly.
     */
    static public void closeConnection(Connection con) {

        if (con != null) {
            try {
                con.close();
            }
            catch (SQLException e) {
                // ignore
                println("WARNING: Exception closing JDBC connection\n" +
                        e.toString());
            }
            con=null;
        }
    }

    /**
     * Run a query and return the value of the first column in the
     * first result row as a string.
     */
    public static String queryString(Map args) 
        throws GeneralException {

        String value = null;

        if (args.get("trace") != null)
            dumpArguments("queryString", args);

        String q = (String)args.get(ARG_SQL);
        if (q != null) {
            Connection c = getConnection(args);
            try {
                String arg = (String)args.get(ARG_ARG1);
                value = queryString(c, q, arg);
            }
            catch (SQLException e) {
                throw new GeneralException(e);
            }
            finally {
                closeConnection(c);
            }
        }
        return value;
    }

    /**
     * Run a query and return a List of the first columns in each row.
     */
    public static List queryList(Map args) 
        throws GeneralException {

        List values = null;

        if (args.get("trace") != null)
            dumpArguments("queryList", args);

        String q = (String)args.get(ARG_SQL);
        if (q != null) {
            Connection c = getConnection(args);
            try {
                String arg = (String)args.get(ARG_ARG1);
                values = queryList(c, q, arg);
            }
            catch (SQLException e) {
                throw new GeneralException(e);
            }
            finally {
                closeConnection(c);
            }
        }
        return values;
    }

    /**
     * Run a query and return a List of the first columns in each row.
     * @deprecated - use {@link #sql(Connection, String, String...)}
     * @ignore
     * Deprecate this in version 8.1. This method has been flagged as being vulnerable to SQL Injection
     * attacks. {@link #sql(Connection, String, String...)} is vulnerable as well, but let's reduce the
     * risk by removing this method.
     */
    @Deprecated
    public static void sql(Map args)
        throws GeneralException {

        if (args.get("trace") != null)
            dumpArguments("queryList", args);

        String q = (String)args.get(ARG_SQL);
        if (q != null) {
            Connection c = getConnection(args);
            try {
                String arg = (String)args.get(ARG_ARG1);
                sql(c, q, arg);
            } finally {
                closeConnection(c);
            }
        }
    }

    public static void dumpArguments(String method, Map map) {
        println("Calling " + method);
        if (map != null) {
            Iterator it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry)it.next();
                String name = (String)entry.getKey();
                String value = (String)entry.getValue();

                if (!name.equals("sessionToken")) {
                    println("    " + name + " = " + value);
                }
            }
        }
    }

    /**
     * Close a statement quietly.
     */
    static public void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * Close a result, quietly.
     */
    static public void closeResult(ResultSet res) {
        if (res != null) {
            try {
                res.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * Dig into the connection's metadata and see
     * if the driver is oracle.
     */
    public static boolean isOracle(PreparedStatement ps) 
        throws SQLException {

        return isOracle(ps.getConnection());
    }
    
    /**
     * Dig into the connection's metadata and see
     * if the driver is oracle.
     */
    public static boolean isOracle(Connection connection)
        throws SQLException 
    {
        DatabaseMetaData dbMetaData = connection.getMetaData();
        String driverName = dbMetaData.getDriverName();
        if ( ( driverName != null ) &&
             ( ORACLE_DRIVER_NAME.equals(driverName) ) ) {
            return true;
        } 
        return false;        
    }
    
    public static boolean isMySQL(Connection connection) {

        try {
            DatabaseMetaData dbMetaData = connection.getMetaData();
            String driverName = dbMetaData.getDriverName();
            if ( ( driverName != null ) &&
                 ( driverName.toLowerCase().contains(MYSQL_PATTERN) ) ) {
                return true;
            } 
        } catch (Throwable t) {
            log.error(t);
        }
        return false;
    }

    /**
     * Simple interface so the Oracle dependencies can be wrapped in a 
     * class that can be called by name.
     */
    public interface ClobHandler {
        public Object setClobParameter(PreparedStatement ps,
                                       int pos,
                                       String value) throws SQLException;

        public void free(Object lob);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Connection Pooling
    //
    ///////////////////////////////////////////////////////////////////////////

    final static int MILLIS_IN_MIN = 60000;
    
    // How many active connections are allowed in the pool
    // -1 = unlimited defaults to 10 connections
    final static int MAX_TOTAL = 10;

    // how many connection can sit in the pool idle
    // defaults to 5 connections
    final static int MAX_IDLE = 5;

    // how often to run the eviction thread
    // defaults to 5 minutes
    final static int EVICT_RUNS = MILLIS_IN_MIN * 5;

    // minimum time the connection can be idle before it gets evicted
    // defaults to 10 minutes
    final static int MIN_EVICT_IDLE = MILLIS_IN_MIN * 10;

    // maximum time waiting for a thread defaults to 1 minute
    final static int MAX_WAIT = MILLIS_IN_MIN * 1;

    // Pooling options to be used with getPooledConnection(...) or 
    // getConnection(Map)
    public static final String ARG_POOL_DISABLE = "disablePooling";
    public static final String ARG_POOL_MAX_WAIT = "maxWait";
    public static final String ARG_POOL_MAX_TOTAL = "maxTotal";
    public static final String ARG_POOL_MAX_IDLE = "maxIdle";
    public static final String ARG_POOL_EVICT_RUNS = "evictRuns";
    public static final String ARG_POOL_EVICT_IDLE = "minEvictIdle";
    public static final String ARG_POOL_DISABLE_AUTO_COMMIT = "disableAutoCommit";

    /**
     * @deprecated Use {@link #ARG_POOL_MAX_TOTAL} instead.
     */
    @Deprecated
    public static final String ARG_POOL_MAX_ACTIVE = "maxActive";

    /** Simpler non-pooled connection with additional properties.
     * 
     * @param driverClass
     *            : Database driver class
     * @param driverPrefix
     *            : Database driver prefix
     * @param url
     *            : URL for database connection.
     * @param user
     *            : User for database connection.
     * @param password
     *            : Password for database connection.
     * @param poolOptions
-    *            : Connection pool properties e.g. MAX_TOTAL, MAX_WAIT.
     * @param property
     *            : Additional properties.
     */
    private static Connection getPooledConnection(String url, String user, String password, Map<String,Object> poolOptions, Properties additionalProperties)
        throws GeneralException {

        GenericObjectPool connectionPool = null;
        Connection connection = null;
        String key = null;
        try {

            key = getKey(user, url);

            PoolingDataSource dataSource = _dataSourcePool.get(key);
            if ( dataSource == null ) {
                ConnectionFactory connectionFactory = null;

                if (null != additionalProperties) {
                    if (null != user)
                        additionalProperties.put(ARG_USER, user);
                    if (null != password)
                        additionalProperties.put(ARG_PASSWORD, password);
                    connectionFactory = new DriverManagerConnectionFactory(url, additionalProperties);
                } else {
                    connectionFactory = new DriverManagerConnectionFactory(url, user, password);
                }

                boolean defaultReadOnly = false;
                boolean autoCommit = true;
                if (Util.getBoolean(poolOptions, ARG_POOL_DISABLE_AUTO_COMMIT)) {
                    autoCommit = false;
                }

                // pool2 now keeps a reference to this factory
                PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory,
                        null);
                poolableConnectionFactory.setDefaultReadOnly(defaultReadOnly);
                poolableConnectionFactory.setDefaultAutoCommit(autoCommit);

                connectionPool = new GenericObjectPool(poolableConnectionFactory);
                poolableConnectionFactory.setPool(connectionPool);

                // Sets the maximum amount of time (in milliseconds) the borrowObject() method
                // should block before
                // throwing an exception when the pool is exhausted and the "when exhausted"
                // action is
                // WHEN_EXHAUSTED_BLOCK. When less than or equal to 0, the borrowObject() method
                // may block
                // indefinitely.
                if (Util.getInt(poolOptions, ARG_POOL_MAX_WAIT) != 0) {
                    connectionPool.setMaxWaitMillis(Util.getInt(poolOptions, ARG_POOL_MAX_WAIT));
                } else {
                    connectionPool.setMaxWaitMillis(MAX_WAIT);
                }

                // Sets the maximum number of objects that can be allocated by the pool (checked
                // out to clients,
                // or idle awaiting checkout) at a given time. When non-positive, there is no
                // limit to the
                // number of objects that can be managed by the pool at one time.
                if (Util.getInt(poolOptions, ARG_POOL_MAX_TOTAL) != 0 ) {
                    connectionPool.setMaxTotal(Util.getInt(poolOptions, ARG_POOL_MAX_TOTAL));
                } else if (Util.getInt(poolOptions, ARG_POOL_MAX_ACTIVE) != 0) {
                    log.warn("Database property maxActive is deprecated. maxTotal should be used instead.");
                    connectionPool.setMaxTotal(Util.getInt(poolOptions, ARG_POOL_MAX_ACTIVE));
                } else {
                    connectionPool.setMaxTotal(MAX_TOTAL);
                }

                // Set the cap on the number of "idle" instances in the pool.
                if (Util.getInt(poolOptions, ARG_POOL_MAX_IDLE) != 0) {
                    connectionPool.setMaxIdle(Util.getInt(poolOptions, ARG_POOL_MAX_IDLE));
                } else {
                    connectionPool.setMaxIdle(MAX_IDLE);
                }

                // Set the number of milliseconds to sleep between runs of the idle object
                // evictor thread.
                // When non-positive, no idle object evictor thread will be run.
                if (Util.getInt(poolOptions, ARG_POOL_EVICT_RUNS) != 0) {
                    connectionPool.setTimeBetweenEvictionRunsMillis(Util.getInt(poolOptions, ARG_POOL_EVICT_RUNS));
                } else {
                    connectionPool.setTimeBetweenEvictionRunsMillis(EVICT_RUNS);
                }

                // Sets the minimum amount of time an object may sit idle in the pool before it
                // is eligible for
                // eviction by the idle object evictor (if any)
                if (Util.getInt(poolOptions,ARG_POOL_EVICT_IDLE) != 0) {
                    connectionPool.setMinEvictableIdleTimeMillis(Util.getInt(poolOptions, ARG_POOL_EVICT_IDLE));
                } else {
                    connectionPool.setMinEvictableIdleTimeMillis(MIN_EVICT_IDLE);
                }

                dataSource = new PoolingDataSource(connectionPool);

            }
            connection = dataSource.getConnection();
            // only add it to the pool once we successfully
            // were able to get a connection
            _dataSourcePool.put(key, dataSource);
        } catch (SQLException e) {
            // remove the trouble datasource pool
            if (null!=connectionPool) {
            try{
                connectionPool.clear();
                connectionPool.close();
                connectionPool=null;
            } catch (Exception ex) {
                log.info("ConnectionPool is not closed");
              }
            }
            _dataSourcePool.remove(key);
            // Localizing and sending additional information to DB connector classes using
            // another constructor of GeneralException.
            // This information will be used for exception handling.
            throw new GeneralException(
                    new Message(ConnectorMessageKeys.CON_JDBC_CONNECTION_FAILURE_ERROR, url, e.toString())
                            .getLocalizedMessage(),
                    e);
        }
        return connection;
    }

    // IIQMAG-1670 - key for hash table cannot use encrypted values anymore, since the new encryption
    // will generate unique encrypted strings each time it is invoked for the same plain text value.
    static String getKey(String user, String url) {
        // be safe about this since it'll be in memory
        return Base64.getEncoder().encodeToString((user + "@" + url).getBytes());
    }
    
    /**
     * Clears a named datasource from our datasource cache. The datasource
     * handles the connection pooling.
     */
    public static void clearDataSource(String user, String url) {
        String key = getKey(user, url);

        if ( _dataSourcePool != null ) {
            if( _dataSourcePool.containsKey(key) ) {
                _dataSourcePool.remove(key);
            }
        }
    }

    /**
     * Closes and clears all datasources our datasource cache.
     */
    public static void clearDataSources() throws GeneralException {

        if ( _dataSourcePool != null ) {
            try {
                for (String key : _dataSourcePool.keySet()) {
                    PoolingDataSource source = _dataSourcePool.remove(key);
                    source.close();
                }
            }
            catch (Exception ex) {
                throw new GeneralException("Could not close PoolingDataSource", ex);
            }
        }
    }
    
    /**
     * Gets the length of the specified column in a table.
     * 
     * @param tableName The name of the table.
     * @param columnName The name of the column to check.
     * 
     * @return The length of the column.
     */
    public static int getColumnLength(Connection connection, String tableName, String columnName)
    {        
        try {
            // We have to treat oracle separately because of a JDBC driver bug that causes COLUMN_SIZE to return byte size
            // and not character length as specified by the API contract
            if (isOracle(connection)) {
                try {
                    return getColumnLengthOracle(connection, tableName, columnName);
                } catch (Exception ex) {
                    return getColumnLengthOracle(connection, tableName.toUpperCase(), columnName.toUpperCase());
                }
            } 
            
            ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName);
            if (!columns.next()) {
                columns = connection.getMetaData().getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase());
                
                if (!columns.next()) {                
                    throw new RuntimeException("Unable to get column length for table: " + tableName + " and column: " + columnName);
                }
            }
            
            return columns.getInt("COLUMN_SIZE");            
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private static int getColumnLengthOracle(Connection connection, String tableName, String columnName)
            throws SQLException
    {
        PreparedStatement ps = null;
        ResultSet rs = null;
        
        try {
            ps = connection.prepareStatement("select CHAR_LENGTH from ALL_TAB_COLUMNS where TABLE_NAME=? and COLUMN_NAME=?");
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            
            rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                throw new RuntimeException("Unable to retrieve column length");
            }
        } finally {
            try { rs.close(); } catch (Exception ex) { }
            try { ps.close(); } catch (Exception ex) { }
        }
    }


    // Function introduced for checking sql injection vulnarability
    public static void populateUnwantedSymbolList() {
        if (_b_IsUnWantedListSizeZero) {
            _unWantedSymbolList.add(";");
            _unWantedSymbolList.add("=");
            _b_IsUnWantedListSizeZero = false;
        }
    }

   // Introduced for BUG 22631 - DB Connector SQL Injection Vulnerabilities
   /**
    * Function to check whether any grant or revoke permission contains symbols like ; or = Arg - String
    **/
    public static String useQuoteForScripts(String val, String quoteForCaseInsensitveDatabase, String quoteForCaseSensitiveDatabase, boolean caseSensitive) {
        String quoteToUse = quoteForCaseInsensitveDatabase;
        populateUnwantedSymbolList();
        if (caseSensitive)
            quoteToUse = quoteForCaseSensitiveDatabase;
        for (String unWantedSymbol : _unWantedSymbolList) {
            if (val.contains(unWantedSymbol)) {
                val = quoteToUse + val + quoteToUse;
                break;
            }
        }
        if (log.isDebugEnabled())
            log.debug(" Modified Name of account or roles to grant or revoke permissions is " + val);
        return val;
    }
    /**
     * Method to check if driverClass is already registered with DriverManager or not
     * 
     * @param driverClass
     * @return boolean
     */
    public static boolean isDriverRegistered(String driverClass) {
        List<Driver> driverList = Collections.list(DriverManager.getDrivers());
        for ( Driver driver:driverList ) {
            if ( driverClass.equals(driver.getClass().getName()) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the db type file extension based on the connection metadata.
     *
     * @param connection The connection.
     * @return The file extension or null if not recognized or an exception is
     *         thrown trying to read the metadata.
     */
    public static String getScriptExtensionForDatabase(Connection connection) {
        String extension = null;

        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (!Util.isNullOrEmpty(productName)) {
                if (productName.startsWith(DB2_PRODUCT_NAME)) {
                    extension = DB2_EXT;
                } else if (MYSQL_PRODUCT_NAME.equals(productName)) {
                    extension = MYSQL_EXT;
                } else if (MS_SQL_PRODUCT_NAME.equals(productName)) {
                    extension = MSSQL_EXT;
                } else if (ORACLE_PRODUCT_NAME.equals(productName)) {
                    extension = ORACLE_EXT;
                }
            }
        } catch (SQLException e) {
            log.warn("An error occurred while trying to read database connection metadata", e);
        }

        return extension;
    }
}