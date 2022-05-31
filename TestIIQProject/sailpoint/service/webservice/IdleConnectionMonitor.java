/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;

import javax.ws.rs.client.Client;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * From Apache httpclient docs:
 *        https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html
 *
 * One of the major shortcomings of the classic blocking I/O model is that the network socket
 * can react to I/O events only when blocked in an I/O operation. When a connection is released
 * back to the manager, it can be kept alive however it is unable to monitor the status of the
 * socket and react to any I/O events. If the connection gets closed on the server side, the
 * client side connection is unable to detect the change in the connection state (and react
 * appropriately by closing the socket on its end).
 *
 * HttpClient tries to mitigate the problem by testing whether the connection is 'stale', that
 * is no longer valid because it was closed on the server side, prior to using the connection
 * for executing an HTTP request. The stale connection check is not 100% reliable. The only
 * feasible solution that does not involve a one thread per socket model for idle connections
 * is a dedicated monitor thread used to evict connections that are considered expired due to a
 * long period of inactivity. The monitor thread can periodically call
 * ClientConnectionManager#closeExpiredConnections() method to close all expired connections
 * and evict closed connections from the pool. It can also optionally call
 * ClientConnectionManager#closeIdleConnections() method to close all connections that have been
 * idle over a given period of time.
 */
public class IdleConnectionMonitor extends TimerTask {

    private static final Log LOG = LogFactory.getLog(IdleConnectionMonitor.class);
    private final PoolingHttpClientConnectionManager connMgr;

    public IdleConnectionMonitor(Client client) {
        super();
        this.connMgr = getConnectionManager(client);
    }

    @Override
    public void run() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting AIServices IdleConnectionMonitorThread");
        }

        // Close expired connections
        connMgr.closeExpiredConnections();

        // Optionally, close connections
        // that have been idle longer than 30 sec
        connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
    }

    private PoolingHttpClientConnectionManager getConnectionManager(Client client) {
        PoolingHttpClientConnectionManager connMgr = null;
        if (client != null) {
            Object connMgrObj = client.getConfiguration().getProperty(ApacheClientProperties.CONNECTION_MANAGER);
            if (connMgrObj instanceof PoolingHttpClientConnectionManager) {
                connMgr = (PoolingHttpClientConnectionManager)connMgrObj;
            }
        }
        return connMgr;
    }
}