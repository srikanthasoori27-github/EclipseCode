/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This along with SailPointConnection is a debugging hack to track
 * leaking database connections.
 *
 * Author: Jeff
 *
 * This can be configured in iiqBeans.xml as an alternative to
 * org.apache.commons.dbcp2.BasicDataSource that we normally use.
 * This cannot be used to track connections when using JNDI.
 * 
 */

package sailpoint.persistence;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.tools.Util;

public class SailPointDataSource extends BasicDataSource {

    static private Log log = LogFactory.getLog(SailPointDataSource.class);

    /**
     * This overloads the BasicDataSource method to wrap the Connection.
     */
    @Override
    public Connection getConnection() throws SQLException {

        Connection con = null;
        Meter.enter(18, "getConnection");
        con = new SPConnection(super.getConnection());
        Meter.exit(18);

        return con;
    }

    /**
     * Return a list of the allocated connections for the debug pages to render.
     */
    public static List<SPConnection> getConnectionList() {
        return SPConnection.getConnectionList();
    }

    /**
     * Return a String describing the allocated connections for the console.
     */
    public static String getConnectionInfo() {

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        getConnectionInfo(pw);
        return sw.toString();
    }

    public static void getConnectionInfo(PrintWriter out) {

        List<SPConnection> connections = SPConnection.getConnectionList();
        if (connections == null) {
            out.format("No open JDBC connections\n");
        }
        else {
            out.format("*** Open connections ***\n");
            int count = 0;
            for (SPConnection con : connections) {
                if (count > 0)
                    out.format("----------------------------------------------------------\n");
                out.format("Connection %s on thread %s - %s\n", con.getKey(), con.getThreadId(), con.getName());
                out.format("Opened at %s\n", con.getCreated());
                List<String> stack = con.getStack();
                if (stack != null) {
                    for (String frame : stack) 
                        out.format(frame);
                }
                count++;
            }
        }
    }

    /**
     * Wrapper around Connection so we can track the close() method.
     * Sigh, wish there was a nicer way to do this without dragging in
     * AOP.
     */
    public static class SPConnection extends ConnectionWrapper {

        static int ConnectionNumber = 1;
        
        static Map <String,SPConnection> _open = new HashMap<String,SPConnection>();

        String _key;
        String _context;
        StackTraceElement[] _stack;
        long _threadId;
        String _threadName;
        long _created;

        public SPConnection(Connection con) {
            super(con);
            synchronized (SPConnection.class) {
                _key = Util.itoa(ConnectionNumber++);
                _open.put(_key, this);
            }

            _stack = new Exception().getStackTrace();
            Thread currentThread = Thread.currentThread();
            _threadId = currentThread.getId();
            _threadName = currentThread.getName();
            _created = System.currentTimeMillis();

            if (log.isInfoEnabled()) {

                String tname = currentThread.getName();
                if (tname.indexOf("Request") < 0 && tname.indexOf("Quartz") < 0) {

                    log.info("getConnection " + _key);
                    /*
                    List<String> stack = getStack();
                    if (stack != null) {
                        for (String frame : stack) {
                            System.out.println(frame);
                        }
                    }
                    */
                }
            }

            // hack, add the SailPointContext if there is one
            sailpoint.api.SailPointContext spc = sailpoint.api.SailPointFactory.peekCurrentContext();
            if (spc != null) 
                _context = spc.toString();
        }

        public String getKey() {
            return _key;
        }

        public String getContext() {
            return _context;
        }

        /**
         * Combine the key and context for the debug pages.
         */
        public String getName() {
            String name = _key;
            if (_context != null)
                name += ": " + _context;
            return name;
        }

        /**
         * Easier for callers just to have a List<String>
         */
        public List<String> getStack() {
            List<String> list = new ArrayList<String>();
            if (_stack != null) {
                for (int i = 0 ; i < _stack.length ; i++)
                    list.add(_stack[i].toString());
            }
            return list;
        }

        public long getThreadId() {
            return _threadId;
        }

        public String getThreadName() {
            return _threadName;
        }

        public String getCreated() {
            return new SimpleDateFormat("yyy-MM-dd HH:mm:ss,SSS").format(new Date(_created));
        }

        public void close() throws SQLException {
            if (log.isInfoEnabled())
                log.info("closeConnection " + _key);

            Meter.enter(19, "closeConnection");
            super.close();
            
            synchronized (SPConnection.class) {
                _open.remove(_key);
            }
            Meter.exit(19);
        }

        public static List<SPConnection> getConnectionList() {
            List<SPConnection> connections = null;
            synchronized (SPConnection.class) {
                if (_open.size() > 0)
                    connections = new ArrayList<SPConnection>(_open.values());
            }
            return connections;
        }

    }

}

