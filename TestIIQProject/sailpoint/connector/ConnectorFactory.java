/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connector;

import connector.common.logging.LogContext;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.bundleinfo.ConnectorBundleVersionable;
import sailpoint.credential.CredentialRetriever;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * The ConnectorFactory factory to aid in getting connectors.
 */
public class ConnectorFactory {

	private static Log log = LogFactory.getLog(ConnectorFactory.class);
	
    /**
     * Build a connector object using a clone of the given application.<br/>
     *
     * This method would normally be called getConnectorWithClone, but remains getConnector
     * for backward compatibility.
     *
     * @param applicationToClone the application which is cloned and used to build the Connector
     * @param instance the instance for template redirection
     * @return a ConnectorProxy object that provides transformation and
     * redirection services around the real connector class.
     * @throws GeneralException
     */
    public static Connector getConnector(Application applicationToClone,
            String instance) throws GeneralException {
        return getConnector(applicationToClone, instance, true);
    }

    /**
     * Build a connector object directly from the given application
     *
     * @param application the application used to build the Connector
     * @param instance the instance for template redirection
     * @return a ConnectorProxy object that provides transformation and
     * redirection services around the real connector class.
     * @throws GeneralException
     */
    public static Connector getConnectorNoClone(Application application,
                                         String instance) throws GeneralException {
        return getConnector(application, instance, false);
    }


    /**
     * Build a connector object for an application
     *
     * @param application the application for which to build the Connector
     * @param instance the instance for template redirection
     * @param cloneApp if true, use a clone of the application to build the connector.
     *                 Otherwise, use application directly
     * @return a ConnectorProxy object that provides transformation and
     * redirection services around the real connector class.
     * @throws GeneralException
     */
    private static Connector getConnector(Application application,
                                         String instance, boolean cloneApp) throws GeneralException {
        ConnectorProxy result = null;

        Application app = null;
        if (cloneApp) {
            app = getApplication(application);
        } else {
            // decrypt secret attributes to the connector is not
            // dependent on core IIQ cryptography
            decryptApplication(SailPointFactory.getCurrentContext(),
                    application);
            app = application;
        }

        String connectorClass = app.getConnector();
        Application proxy = app.getProxy();

        if (proxy == null) {
            if (connectorClass == null) {
                throw new GeneralException("Application does not specify"
                        + " a Connector class");
            }
            Connector connector = createConnector(connectorClass, app, instance);
            result = new ConnectorProxy(connector);
        } else {
            // if proxied app specifies a Connector instantiate that too
            // bug#25984 IdN doesn't always deploy all the classes used
            // by the CCG on the IIQ side, must tolerate missing classes
            Connector targetConnector = null;
            if (connectorClass != null) {
                Class cls = null;
                try {
                      cls = (Class<Connector>)ConnectorClassLoaderUtil.getConnectorClass(app, app.getConnector());
                } catch (ClassNotFoundException e) {
                }
                if (cls != null) {
                    targetConnector = createConnector(connectorClass, app,
                            instance);
                }
            }

            proxy = (Application) proxy.clone();
            String proxyClass = proxy.getConnector();
            if (proxyClass == null) {
                throw new GeneralException("Proxy Application does not specify"
                        + " a Connector class");
            }

            Connector pcon = createConnector(proxyClass, proxy, null);
            result = new ConnectorProxy(pcon);
            result.setTarget(app, instance, targetConnector);
        }

        return result;
    }

    /**
     * Special factory method to see if the connector supports the
     * CompositeConnector interface and returns that instead of Connector. This
     * is necessary because getConnector returns a ConnectorProxy so we can't
     * use instanceof to tell if the actual connector also implements
     * CompositeConnector.
     */
    public static LogicalConnector getCompositeConnector(Application app)
            throws GeneralException {

        LogicalConnector comp = null;
        Application application = getApplication(app);
        String connectorClass = application.getConnector();
        if (connectorClass == null) {
            throw new GeneralException("Application does not specify"
                    + " a Connector class");
        }
        Connector connector = createConnector(connectorClass, application, null);
        if (connector instanceof LogicalConnector)
            comp = (LogicalConnector) connector;

        // these don't have proxies, yet anyway
        return comp;
    }

    /**
     * Method will create a new Connector instance, calling the constructor that
     * takes in an application.
     */
    @SuppressWarnings("unchecked")
    public static Connector createConnector(String name, Application app,
            String instance) throws GeneralException {
        setLogContext(app);
        Connector connector = null;
        Class<Connector> c = null;

        try {
            c = (Class<Connector>)ConnectorClassLoaderUtil.getConnectorClass(app,name);
        } catch (Exception e) {
            log.error("Exception while loading connector class "+ e);
            StringBuffer sb = new StringBuffer();
            sb.append("Couldn't load connector class: ");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }

        if (c == null) {
            StringBuffer sb = new StringBuffer();
            sb.append("Unknown error loading connector class: ");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }

        try {
            Class<Application> applicationClass = (Class<Application>) Class
                    .forName("sailpoint.object.Application");
            Constructor<Connector> constructor = c
                    .getConstructor(applicationClass);
            if (constructor == null) {
                throw new GeneralException("Failed to get a Constructor for ["
                        + name + "] which takes an application.");
            }
            connector = (Connector) constructor.newInstance(app);
            if (connector == null) {
                throw new GeneralException("Connector class instance is null.");
            }
            connector.setInstance(instance);
            connector.setConnectorServices(new DefaultConnectorServices());
        } catch (Exception e) {
            throw new GeneralException(
                    "Failed to create connector object for the " + name
                            + " connector: " + e.toString(), e);
        }

        return connector;
    }

    /**
     * Starting in 6.0 we do not want Connector implementations to have access
     * to core IIQ cryptography services. Some do, mostly through methods
     * inherited from AbstractConnector but those will be gradually weeded out.
     * In 6.0 the ConnectorFactory will decrypt any secret configuration
     * attributes in the Application that will be passed to the Connectors.
     *
     * The Application here must be a transient clone of a persistent app.
     * @throws GeneralException 
     */
    private static void decryptApplication(SailPointContext spc, Application app)
        throws GeneralException {
        List<String> secrets = getSecrets(app);
        Attributes<String,Object> atts = app.getAttributes();
        if (atts != null) {
            // IMPORTANT: Currently, InternalContext doesn't have ability to encrypt 
            // passwords within complex attribute values like map/ list etc.
            // Due to which, passwords were currently getting saved in plain text.
            // To resolve this issue, fix in InternalContext is also required which 
            // needs to be taken care of in future.
            // For short term fix, we are adding the check of "instanceof String",
            // which means ConnectorFactory will decrypt only application level 
            // attributes only!
            // MORE IMPORTANT: InternalContext, EncryptedDataSyncExecutor as well as this class
            // now use the MapUtil API to decrypt bottom-level secret attributes.
            // We used to iterate attributes here and check if the attribute key is a part of the
            // secrets List. We expect less secrets than attributes, so it's more efficient
            // to iterate secrets than attributes.
            for(String key : Util.safeIterable(secrets)) {
                MapUtil.putAll(atts, key, new Function<Object, Object>() {
                    @Override
                    public Object apply(Object x) {
                        String result = Util.otos(x);
                        try {
                            if (result != null)
                                result = spc.decrypt(result);
                        } catch (GeneralException e) {
                            log.error("unable to decrypt strings for: " + key);
                        }
                        return result;
                    }
                });
            }
        }
    }
    
    private static void retrieveCredentials(SailPointContext spc, Application app) throws GeneralException {
        CredentialRetriever credConfig = new CredentialRetriever(spc);
        credConfig.updateCredentials(app);
    }

    /*
     * Get the names of the attributes that should be considered secret and
     * encrypted.
     * 
     * By default always add 'password' to the list of things that are secret.
     * Then see if there is any application specific attribute names.
     * 
     * @see Application#getEncrpytedConfigAttributes()
     * 
     * @param app
     * 
     * @return
     */
    private static List<String> getSecrets(Application app) {

        final String PASSWORD = "password";
        final String IQ_SERVICE_PASSWORD = "IQServicePassword";
        List<String> secrets = new ArrayList<String>();
        
        if (app != null) {
            List<String> atts = app.getEncrpytedConfigAttributes();
            if (Util.size(atts) > 0)
                secrets.addAll(atts);
        }

        // always add this unless it was defined in the schema (unusual)
        if (!secrets.contains(PASSWORD))
            secrets.add(PASSWORD);

        if(!secrets.contains(IQ_SERVICE_PASSWORD)) {
            secrets.add(IQ_SERVICE_PASSWORD);
        }
        
        return secrets;
    }

    /**
     * Clone the application ( deep-copy using xml ) and then decrypt any of the
     * passwords on the application.
     */
    private static Application getApplication(Application originalApp)
            throws GeneralException {

        SailPointContext context = SailPointFactory.getCurrentContext();
        // deep-copy/clone so we don't accidentally flush changes
        Application app = (Application) XMLObjectFactory.getInstance().clone(
                originalApp, context);

        retrieveCredentials(context, app);
        // decrypt secret attributes to the connector is not
        // dependent on core IIQ cryptography
        decryptApplication(context, app);
        return app;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Destroy
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Some connectors may hold onto resources when iterating across multiple
     * partitions (even after close() is called on the CloseableIterators). Call
     * this method to clean up after all partitions have been processed.
     *
     * @param application
     *            The Application for the connector to destroy.
     * @param optionsMap
     *            Map of options sent by aggregator
     */
    public static void destroyConnector(Application application, Map<String, Object> optionsMap)
            throws GeneralException {

        // Finally, let the connector take out the trash.
        try {
            //if connector class is not null then only proceed
            if(application != null && application.getConnector()!= null){
                Connector conn = ConnectorFactory.getConnector(application, null);
                conn.destroy(optionsMap);
            }
        } catch (ConnectorException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Return the connector bundle version information supplier.
     *
     * @throws GeneralException
     *             Thrown when lazy loading of a class fails.
     */
    @SuppressWarnings("unchecked")
    public static ConnectorBundleVersionable getCBVersionSupplier()
        throws GeneralException {

        ConnectorBundleVersionable cbVersionSupplier = null;
        Class<ConnectorBundleVersionable> clazz = null;
        String name = "sailpoint.connector.bundleinfo.Version";

        try {
            clazz = (Class<ConnectorBundleVersionable>) Class.forName(name);
        } catch (Exception e) {
            throw new GeneralException("Couldn't load the class " + name + ". " + e.getMessage(), e);
        }

        if (clazz == null) {
            throw new GeneralException("Unknown error loading the class " + name + ".");
        }

        try {
            cbVersionSupplier = (ConnectorBundleVersionable) clazz.newInstance();
        } catch (Exception e) {
            throw new GeneralException("Failed to instantiate the class " + name + ". " + e.getMessage(), e);
        }

        if (cbVersionSupplier == null) {
            throw new GeneralException("The ConnectorBundleVersionable instance is null.");
        }

        return cbVersionSupplier;
    }

    /**
     * Load the sensitive values for this particular thread.
     */
    private static void setLogContext(Application app) {
        // adding encrypted and sensitive values from application config to
        // logcontext. It will be used to prevent logging sensitive information

        List<String> secrets = app.getEncryptedAndSecretAttrList();
        Attributes<String, Object> atts = app.getAttributes();

        if (atts != null) {
            for (String key : Util.safeIterable(secrets)) {
                try {
                    for (String value : Util.safeIterable(Util.otol(MapUtil.get(atts, key)))) {
                        if (Util.isNotNullOrEmpty(value)) {
                            LogContext.addSensitiveValue(value);
                        }
                    }
                } catch (Exception e) {
                    // ignore : cases where the path syntax is not valid
                }
            }
        }
    }
}
