/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of IntegrationExecutor interface used to route requests
 * to Connectors that support provisioning.
 * 
 * Author: Jeff
 *
 * This is a transitional adapter between IntegrationExecutor and Connector 
 * until we can retool PlanCompiler and PlanEvaluator to use Connectors
 * directly, at which point IntegrationExecutor will be deprecated and
 * accessed through a special Connector proxy.
 *
 */

package sailpoint.integration;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.AbstractIntegrationExecutor;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Application;
import sailpoint.object.Field;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Template;
import sailpoint.provisioning.TemplateFieldIterator;
import sailpoint.tools.Util;

/**
 * Implementation of IntegrationExecutor interface used to route requests
 * to Connectors that support provisioning.
 */
public class ConnectorExecutor extends AbstractIntegrationExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(ConnectorExecutor.class);
    
    public static final String ATT_APPLICATION = "application";

    SailPointContext _context;
    IntegrationConfig _config;
    Connector _connector;

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInterface
    //
    //////////////////////////////////////////////////////////////////////

    public ConnectorExecutor() {
    }

    public void configure(SailPointContext context,
                          IntegrationConfig config)
        throws Exception {

        _context = context;
        _config = config;

        // the name of the config must be the name of the Application
        String appname = config.getName();
        if (appname == null) {
            log.error("Unnamed IntegrationConfig");
        }
        else {
            Application app = context.getObjectByName(Application.class, appname);
            if (app == null) {
                if (log.isErrorEnabled())
                    log.error("Invalid application name: " + appname);
            }
            else {
                // NOTE: This MUST return a ConnectorProxy that will
                // be responsible for name mapping in the provision() method
                _connector = ConnectorFactory.getConnector(app, null);
                if (_connector == null) {
                    if (log.isErrorEnabled())
                        log.error("Unable to instantiate connector for application: " + appname);
                }
                else {
                    app = _connector.getApplication();
                    if(app != null) {
                        List<Template> templates = app.getTemplates();
                        for(Template template : Util.iterate(templates)) {
                            if(template.getFormRef() != null) {
                                TemplateFieldIterator fieldIterator = new TemplateFieldIterator(template, _context);
                                List<Field> fields = new ArrayList<Field>();
                                while(fieldIterator.hasNext()) {
                                    fields.add(fieldIterator.next());
                                }
                                template.setFields(fields);
                            }
                        }
                    }
                }
            }
        }
    }

    public Connector getConnector() {
        return _connector;
    }

    /**
     * Translate the IntegrationExecutor.ping request into 
     * Connector.testConnection. In theory we might want a 
     * Connector.ping to do more than just test the connection.
     */
    public String ping() throws Exception {

        String result = null;
        
        if (_connector != null) {
            try {
                _connector.testConfiguration();
                result = "Connection test sucessful";
            }
            catch (Throwable t) {
                result = "Connection test failed: " + t.toString();
            }
        }

        return result;
    }

    /**
     * Make changes to an identity defined by a ProvisioningPlan.
     *
     * The interface takes a sailpoint.integration.ProvisioningPlan
     * rather than a sailpoint.object.ProvisioningPlan. It would be nice
     * if executors that operated mostly within IdentityIQ could get the 
     * better plan, it is easier to deal with and something could get
     * lost in the translation?
     *
     */
    public ProvisioningResult provision(ProvisioningPlan plan)
        throws Exception {

        ProvisioningResult result = null;

        if (_connector == null) {
            // we will have logged this in configure() don't log again
            // for every request...too much clutter
        }
        else {
            result = _connector.provision(plan);
        }

        return result;
    }

    /**
     * Check the status of a queued request.
     */
    public ProvisioningResult checkStatus(String requestId)
        throws Exception {

        ProvisioningResult result = null;
        if (_connector != null) {
            result = _connector.checkStatus(requestId);
            // jsl - this also potentially needs to update config
            // rather than two ObjectUtil calls, just merge these into one
            Application connApp = ObjectUtil.getLocalApplication(_connector);
            ObjectUtil.updateApplicationConfig(_context, connApp);
        }
        return result;
    }

    
}

