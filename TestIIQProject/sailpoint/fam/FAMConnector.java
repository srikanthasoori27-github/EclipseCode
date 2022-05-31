/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.fam.model.SCIMObject;
import sailpoint.object.Configuration;
import sailpoint.object.QueryOptions;
import sailpoint.service.webservice.ConnectionManager;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Connector class used to retrieve data from SIQ via SCIM REST interface
 *
 */
public class FAMConnector {

    private static Log log = LogFactory.getLog(FAMConnector.class);

    public static enum AuthType {
        OAUTH,
        BASIC
    };

    //Config Attributes
    public static String CONFIG_AUTH_TYPE = "authType";

    //This need to be configurable, or is it static? -rap
    public static String CONFIG_SCIM_PATH = "scimPath";
    public static String SCIM_V2_PATH = "identityiqfamapi/scim/v2";

    private static ConnectionManager connectionManager;

    static {
        connectionManager = new HybridAuthConnectionManager(
                new HybridAuthConnectionManager.HybridAuthDynamicConfigProvider(() -> Configuration.getFAMConfig()));
    }


    private Configuration config;
    private AuthType authType;
    private String _scimPath;
    private ConnectionManager connectionManagerOverride;


    public FAMConnector(Configuration config) {
        this.config = config;
        setup();
        //Use a static ConfigProvider
        connectionManagerOverride = new HybridAuthConnectionManager(
                new HybridAuthConnectionManager.HybridAuthStaticConfigProvider(config)
        );
    }

    public FAMConnector() {
        setup();
    }

    private void setup() {
        //Parse Config

        if (this.config == null) {
            //No config specified, default to FAMConfiguration, and use dynamicConfigProvider
            this.config = Configuration.getFAMConfig();
        }

        if (this.config != null) {
            if (this.config.containsKey(CONFIG_SCIM_PATH)) {
                this._scimPath = this.config.getString(CONFIG_SCIM_PATH);
            } else {
                //Default
                this._scimPath = SCIM_V2_PATH;
            }

            if (connectionManager == null) {
                try {
                    connectionManager = new HybridAuthConnectionManager(
                            new HybridAuthConnectionManager.HybridAuthDynamicConfigProvider(() -> Configuration.getFAMConfig()));
                } catch (Exception e) {
                    log.error("Error configuring ConnectionManager");
                }
            }
        }

    }

    //Helps with unit tests. Override the static initialized connectionmanager
    public void setConnectionManager(ConnectionManager mgr) {
        this.connectionManagerOverride = mgr;
    }

    protected ConnectionManager getConnectionManager() throws GeneralException {
        if (this.connectionManagerOverride != null) {
            return this.connectionManagerOverride;
        } else if (connectionManager != null) {
            return connectionManager;
        } else {
            throw new GeneralException("No ConnectionManager Configured");
        }
    }

    /**
     *
     * @param clazz - SCIMObject class to get
     * @param ops - QueryOptions
     * @param attributes -
     * @param <T>
     * @return
     * @throws GeneralException
     */
    public <T extends SCIMObject> Response getObjects(Class<T> clazz, QueryOptions ops, List<String> attributes) throws GeneralException {

        Response response;

        FAMWebTargetBuilder builder = null;
        try {
            builder = new FAMWebTargetBuilder(getConnectionManager().getWebTarget());
        } catch (Exception e) {
            log.error("Error getting WebTarget from ConnectionManager");
            throw new GeneralException("Error getting WebTarget from ConnectionManager" + e);
        }

        WebTarget targ = builder.buildWebTargetPath(this._scimPath, clazz).
                addFilterParams(ops).
                addPagingParams(ops).
                addAttributeParams(attributes).
                buildWebTarget();
        Invocation.Builder invocationBuilder = targ.request();

        response = invocationBuilder.
                header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).
                accept(MediaType.APPLICATION_JSON).
                get();


        return response;

    }

    public <T extends SCIMObject> Response getObject(Class<T> clazz, String id) throws GeneralException {

        Response response;

        FAMWebTargetBuilder builder = null;
        try {
            builder = new FAMWebTargetBuilder(getConnectionManager().getWebTarget());
        } catch (Exception e) {
            log.error("Error getting WebTarget from ConnectionManager");
            throw new GeneralException("Error getting WebTarget from ConnectionManager" + e);
        }

        WebTarget targ = builder.buildWebTargetPath(this._scimPath, clazz).
                buildWebTarget();
        targ.path(id);
        Invocation.Builder invocationBuilder = targ.request();

        response = invocationBuilder.
                header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).
                accept(MediaType.APPLICATION_JSON).
                get();


        return response;
    }

    static class FAMWebTargetBuilder {

        protected WebTarget baseTarget;

        FAMWebTargetBuilder(WebTarget baseTarget) {
            this.baseTarget = baseTarget;
        }

        public WebTarget buildWebTarget() {
            return this.baseTarget;
        }


        protected FAMWebTargetBuilder buildWebTargetPath(String basePath, Class<? extends SCIMObject> clazz) throws GeneralException {
            //TODO: Figure out how to do this, ideally without factory/instantiation?
            try {
                if (baseTarget != null) {
                    String path = clazz.newInstance().getSCIMPath();
                    if (Util.isNotNullOrEmpty(path)) {
                        baseTarget = baseTarget.path(basePath).path(path);
                    } else {
                        throw new GeneralException("No Path for entity[" + clazz.getSimpleName() + "]");
                    }
                } else {
                    throw new GeneralException("No Base WebTarget provided");
                }
            } catch (IllegalAccessException | InstantiationException e) {
                throw new GeneralException("Error building WebTarget" + e);
            }

            return this;
        }

        protected FAMWebTargetBuilder addFilterParams(QueryOptions ops) throws GeneralException {
            if (baseTarget != null) {
                FAMFilterVisitor visitor = new FAMFilterVisitor();
                visitor.visitFAMFilter(ops);

                if (Util.isNotNullOrEmpty(visitor.getQueryString())) {
                    baseTarget = baseTarget.queryParam(SCIMObject.QUERY_PARAM_FILTER, visitor.getQueryString());
                }
            }

            return this;
        }

        protected FAMWebTargetBuilder addPagingParams(QueryOptions ops) {
            if (baseTarget != null) {
                if (ops != null) {
                    if (ops.getFirstRow() > 1) {
                        //startIndex is 1-based
                        baseTarget = baseTarget.queryParam(SCIMObject.QUERY_PARAM_START, ops.getFirstRow());
                    }

                    if (ops.getResultLimit() > 0) {
                        baseTarget = baseTarget.queryParam(SCIMObject.QUERY_PARAM_COUNT, ops.getResultLimit());
                    }
                }
            }

            return this;
        }

        /**
         * Used to retrieve specific attributes values.
         * Adds the attributeName to the attributes query part
         * @param attributes
         * @return
         */
        protected FAMWebTargetBuilder addAttributeParams(List<String> attributes) {
            if (baseTarget != null && !Util.isEmpty(attributes)) {
                //If a list, CSV? -rap
                baseTarget = baseTarget.queryParam(SCIMObject.QUERY_PARAM_ATTRIBUTES, Util.listToCsv(attributes));
            }
            return this;
        }


    }

}
