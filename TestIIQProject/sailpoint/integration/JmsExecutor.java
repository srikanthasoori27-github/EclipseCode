/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of IntegrationExecutor that writes requests to
 * a JMS message queue.
 * 
 * Author: Jeff, Kelly
 *
 * This is relatively general, you make customizations by specifying
 * rules in the IntegrationConfig.  But complex customizations
 * would be more easily handled by subclassing.
 *
 * I decided pull this into the core product rather than making
 * it part of the integration/common package, since it isn't
 * likely to be useful on the "agent" side of an integration
 * and it adds build dependencies on integration/common.  This also
 * allows us to use Script and Rule in the IntegrationConfig.
 *
 * This only does provisioning plans, role sync is not supported
 * due to the inherently unidirectionl nature of message queues.
 * 
 * CUSTOMIZATION SCRIPTS
 *
 *     factory - script or Rule to produce a ConnectionFactory
 *
 *     planRenderer - convert a ProvisioningPlan to a JMS message
 * 
 * TEST NOTES
 *
 * At this time there is an IBM Websphere MQ version 6 system running
 * on skimmer.test.sailpoint.com.  Connectivity information should
 * be kept current in the example JMS IntegrationConfig in src/config/integration.
 * (May want to move this out since it has names and passwords).
 *
 * To monitor the queue, establish a remote desktop to skimmer, login as
 * Administrator with password Fir3bird.  Go to Start->All Programs->
 * IBM WebSphere MQ and select WebSphere MQ Explorer.
 *
 * In the tree on the left select QueueManagers/QM_skimmer/Queues
 * Right click on kelly_test from the list on the right and select
 * Browse Messages.  Right click on a message and click Properties.
 * Click on Data.  You will see the raw data in bytes with an ascii
 * rendering on the right.  There is some header junk, then you should
 * see the text of the message.
 * 
 * KELLY'S DESIGN NOTES
 * 
 * Test code for sending and receiving JMS messages.  This uses Spring's
 * JmsTemplate rather than the JMS API directly.  I chose to go this route for
 * a couple of reasons:
 * 
 * 1) JmsTemplate hides the differences between the 1.0.2 and 1.1 JMS APIs.
 * 2) JmsTemplate has some nice helpers for looking up destinations either by
 *    name or in JNDI.
 * 3) The code using the JmsTemplate is a bit more compact and simple.
 * 4) Pluggable message converters seem like they could be useful.
 *
 * 
 */

package sailpoint.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;

// using Spring to do some of the work and hide JMS version inconsistencies
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.destination.JndiDestinationResolver;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.ProvisioningPlan;

import sailpoint.api.SailPointContext;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.tools.Util;


public class JmsExecutor extends AbstractIntegrationExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Configuration Arguments
    //

    /**
     * Argument with the identifier of the destination queue.
     * If jndiUrl is not set this is a topic or queue name.
     * If jndiUrl is set this is the JDNI name containing a topic or queue name.
     * Whether this is a topic or queue depends on the pubSub argument.
     */
    public static final String ARG_DESTINATION = "destination";

    /**
     * When true it enables the Publish/Subscribe messaging domain.
     * When false it enables the Point-to-Point messaging domain.
     */
    public static final String ARG_PUB_SUB = "pubSub";

    /**
     * The receive timeout in seconds.
     * NOTE: I could not find reliable documentation of the unit here,
     * but various web pages suggest the timeout is in seconds.
     */
    public static final String ARG_RECEIVE_TIMEOUT = "receiveTimeout";

    /**
     * The default receive timeout in seconds.
     * The JmsTemplate internal default is -1 which means no timeout.
     */
    public static final int DEFAULT_RECEIVE_TIMEOUT = 10000;

    /**
     * Argument with the user name used to connect to the queue.
     * This is different than the JNDI credentials and is usually required.
     */
    public static final String ARG_CONNECT_USER = "connectUser";

    /**
     * Password of the user used to connect to the queue.
     * This is different than the JNDI credentials and is usually required.
     */
    public static final String ARG_CONNECT_PASSWORD = "connectPassword";

    /**
     * A Script or Rule name to render a ProvisioningPlan into a text
     * message to send to the queue.
     */
    public static final String ARG_PLAN_RENDERER = "planRenderer";

    /**
     * When true goes through the motions of building a message
     * and establishing a connection, but instead of sending dumps
     * the rendered message to the console.
     */
    public static final String ARG_SIMULATE = "simulate";

    /**
     * Enables debugging trace messages.
     */
    public static final String ARG_TRACE = "trace";

    //
    // ConnectionFactory - JNDI or Script
    //

    /**
     * The URL of a JNDI store.
     * If this is set it forces the use of JNDI for both locating
     * the provider and resolving destinations.
     * A user and password are not required, if missing we will
     * attempt an anonymous connection.
     */
    public static final String ARG_JNDI_URL = "jndiUrl";
    public static final String ARG_JNDI_USER = "jndiUser";
    public static final String ARG_JNDI_PASSWORD = "jndiPassword";

    /**
     * The JNDI resource name of the ConnectionFactory.
     */
    public static final String ARG_JNDI_FACTORY = "jndiFactory";

    /**
     * The JNDI resource name of the default topic when using pub/sub.
     */
    public static final String ARG_JNDI_TOPIC = "jndiTopic";

    /**
     * The JNDI resource name of the default queue when using point-to-point.
     */
    public static final String ARG_JNDI_QUEUE = "jndiQueue";

    /**
     * Argument containing either a Script or Rule name to
     * create the ConnectionFactory. This is an alternative to using JNDI.
     */
    public static final String ARG_FACTORY = "factory";



    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(JmsExecutor.class);
    
    /**
     * Context passed 

    /**
     * Save the configuration args for connection.
     */
    Map _arguments;

    /**
     * Cached JMS template used for sending and receiving messages.
     */
    JmsTemplate _template;

    boolean _trace;
    boolean _simulate;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public JmsExecutor() {
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // JMS Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build a Spring JmsTemplate for sending or receiving messages.
     * The template can be reused.
     */
    private JmsTemplate getJmsTemplate() throws Exception {
        
        if (_template == null) {

            // if we have enough to build one of these, then we use JNDI
            Properties jndiEnv = createJndiEnv();
            int timeout = Util.getInt(_arguments, ARG_RECEIVE_TIMEOUT);
            boolean pubSub = Util.getBoolean(_arguments, ARG_PUB_SUB);

            if (timeout == 0) timeout = DEFAULT_RECEIVE_TIMEOUT;

            _template = new JmsTemplate(getConnectionFactory(jndiEnv));
            _template.setPubSubDomain(pubSub);
            _template.setReceiveTimeout(timeout);

            // this is what disables the use of RFH2 headers
            //_template.setDefaultDestinationName("queue:///default?targetClient=1");

            // using the specified destination
            String dest = Util.getString(_arguments, ARG_DESTINATION);
            if (dest == null)
                throw new Exception("Missing JMS destination");
            String destpath = "queue:///" + dest + "?targetClient=1";
            System.out.println("JmsExecutor destination " + destpath);
            _template.setDefaultDestinationName(destpath);

            // If using JNDI, resolve destinations via JNDI and fallback to
            // dynamic destinations if not found in JNDI.
            if (jndiEnv != null) {
                JndiDestinationResolver resolver = new JndiDestinationResolver();
                resolver.setFallbackToDynamicDestination(true);
                resolver.setJndiEnvironment(jndiEnv);
                _template.setDestinationResolver(resolver);
            }
        }

        return _template;
    }

    /**
     * Create a JNDI environment suitable for use with InitialDirContext.
     * If this returns null we are not configured for JNDI.
     */
    private Properties createJndiEnv() {

        Properties env = null;

        String url = Util.getString(_arguments, ARG_JNDI_URL);
        if (url != null) {

            env = new Properties();

            // any need to configure this?
            env.put(Context.INITIAL_CONTEXT_FACTORY,
                    "com.sun.jndi.ldap.LdapCtxFactory");

            env.put(Context.PROVIDER_URL, url);

            // If these aren't set we'll bind anonymously
            String user = Util.getString(_arguments, ARG_JNDI_USER);
            if (user != null) {
                String pass = Util.getString(_arguments, ARG_JNDI_PASSWORD);
                env.put(Context.SECURITY_PRINCIPAL, user);
                env.put(Context.SECURITY_CREDENTIALS, pass);
            }
        }

        return env;
    }
    
    /**
     * Derive the ConnectionFactory for the JMS provider we will use.
     */
    private ConnectionFactory getConnectionFactory(Properties jndiEnv) 
        throws Exception {

        ConnectionFactory factory = null;
            
        if (jndiEnv != null) {
            // lookup the factory in JNDI
            InitialDirContext ctx = new InitialDirContext(jndiEnv);
            String name = Util.getString(ARG_JNDI_FACTORY);
            if (name == null)
                throw new Exception("Missing JNDI factory name");

            factory = (ConnectionFactory)ctx.lookup(name);

            // do we have to do this or will ctx.lookup throw?
            if (factory == null)
                throw new Exception("Unknown JNDI factory: " + name);
        }
        else {
            // subclass may overload this
            factory = createConnectionFactory();
        }

        // Use Spring's adapter connection factory to supply credentials
        // when connections are created.  This is required because the
        // JmsTemplate uses createConnection() and does not allow for
        // passing username and password in.

        String user = Util.getString(_arguments, ARG_CONNECT_USER);
        String password = Util.getString(_arguments, ARG_CONNECT_PASSWORD);

        // jsl are these required?
        if (user != null) {
            UserCredentialsConnectionFactoryAdapter credFactory =
                new UserCredentialsConnectionFactoryAdapter();
            credFactory.setTargetConnectionFactory(factory);
            credFactory.setUsername(user);
            credFactory.setPassword(password);
            factory = credFactory;
        }

        return factory;
    }

    /**
     * Instantiate a connection factory without going through JNDI.
     * This can be overloaded in a subclass for complex construction.
     * By default we require a Script or Rule.
     */
    private ConnectionFactory createConnectionFactory()
        throws Exception {
        
        ConnectionFactory factory = null;

        Object o = _arguments.get(ARG_FACTORY);
        if (o != null)  {
            o = doSomething(null, o);
            if (o instanceof ConnectionFactory)
                factory = (ConnectionFactory)o;
            else
                throw new Exception("Script did not return a ConnectionFactory");
        }

        return factory;
    }

    /**
     * Send a message.
     */
    private void sendMessage(String msg) throws Exception {

        // destination is now set in the template
        /*
        String dest = Util.getString(_arguments, ARG_DESTINATION);
        if (dest == null)
            throw new Exception("Missing JMS destination");
        */

        JmsTemplate tmp = getJmsTemplate();

        if (_trace || _simulate) {
            println("*** Sending JMS Message ***");
            println(msg);
        }

        if (!_simulate) {
            //tmp.convertAndSend(dest, msg);
            tmp.convertAndSend(msg);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Script Evaluation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given the value of an argument, evaluate it as either a Script 
     * or Rule and return the result.
     */
    private Object doSomething(Map<String,Object> args, Object o) throws Exception {

        Object result = null;

        SailPointContext context = getContext();
        if (args == null)
            args = new HashMap<String,Object>();
        getScriptArgs(args);

        if (o instanceof Script) {
            result = context.runScript((Script)o, args);
        }
        else if (o instanceof String) {
            String name = (String)o;
            Rule rule = context.getObjectByName(Rule.class, name);
            if (rule == null)
                throw new Exception("Unknown rule: " + name);
            else
                result = context.runRule(rule, args);
        }

        return result;
    }

    /**
     * Build an argument map for either Script or Rule evaluation.  
     * This will include all of the IntegrationConfig arguments.
     */
    private void getScriptArgs(Map<String,Object> args) {

        // be selective?
        args.putAll(_arguments);

    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInterface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Dig our JDBC connection params out of the config map.
     */
    public void configure(Map args) throws Exception {

        if (args == null) {
            // so we don't have to check for null
            _arguments = new HashMap();  
        }
        else {
            _arguments = args;
            _trace = Util.getBoolean(args, ARG_TRACE);
            _simulate = Util.getBoolean(args, ARG_SIMULATE);
        }
    }

    /**
     * Verify that we can connect to the queue.
     * Not sure if there is a universal ping message, just
     * make sure we have everything we need to create the ConnectionFactory,
     * The JmsTemplate, resolve the destination, etc.
     */
    public String ping() throws Exception {

        // This will throw if anything goes wrong.
        // These arent stateful connections so we don't have to close them
        // like JDBC connections??
        JmsTemplate jms = getJmsTemplate();

        String dest = Util.getString(ARG_DESTINATION);
        if (dest == null)
            throw new Exception("Missing JMS destination");

        // send something?  we would need a formatting script for a 
        // harmless message
        sendMessage("ping");

        return "Connection sucessful";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provision
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert a ProvisioningPlan into JMS message text.
     */
    public RequestResult provision(String identity, 
                                   ProvisioningPlan plan)
        throws Exception {

        RequestResult result = new RequestResult();
        try {
            String msg = renderProvisioningPlan(plan);
            sendMessage(msg);
        }
        catch (Throwable t) {
            result.addError(t);
        }

        return result;
    }

    /**
     * Expected to be overloaded in subclasses that do not want to use
     * the rendering script.
     */
    private String renderProvisioningPlan(ProvisioningPlan plan) 
        throws Exception {

        String msg = null;
        Object o = _arguments.get(ARG_PLAN_RENDERER);
        if (o != null)  {
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("plan", plan);
            o = doSomething(args, o);
            if (o instanceof String)
                msg = (String)o;
            else
                throw new Exception("Script did not return a message string");
        }
        else {
            throw new Exception("No plan rendering script");
        }

        return msg;
    }

}
