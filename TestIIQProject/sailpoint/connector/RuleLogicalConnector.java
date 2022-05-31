/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.CloseableIterator;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.object.Application.Feature;

import sailpoint.api.SailPointContext;

/**
 * @deprecated - use sailpoint.connector.DefaultCompositeConnector. This
 * will still work, but will not be editable from the UI.
 */
public class RuleLogicalConnector
    extends AbstractLogicalConnector  {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RuleLogicalConnector.class);

    public static final String CONNECTOR_TYPE = "Rule Based Logical";

    public static final String ATTR_ACCOUNT_RULE = "accountRule";
    public static final String ATTR_REMEDIATION_RULE = "remediationRule";

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RuleLogicalConnector(Application app) {
        super(app);
    }
 
    //////////////////////////////////////////////////////////////////////
    //
    // Connector
    //
    //////////////////////////////////////////////////////////////////////

    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }

    /**
     * There are no default schemas, they must be fully defined.
     */
    public List<Schema> getDefaultSchemas() {

        List<Schema> schemaList = new ArrayList<Schema>();
        return schemaList;
    }

    /**
     * This method should return a List of AttributeDefinitions that describe 
     * the configuration attributes that can be used to configure the behavior 
     * of a connector.
     */
    public List<AttributeDefinition> getDefaultAttributes() {

        List<AttributeDefinition> config = new ArrayList<AttributeDefinition>();
        AttributeDefinition att;

        att = new AttributeDefinition(ATTR_ACCOUNT_RULE,
                                      AttributeDefinition.TYPE_STRING);
        att.setDisplayName("Logical Account Rule");
        att.setRequired(true);
        config.add(att);

        att = new AttributeDefinition(ATTR_REMEDIATION_RULE,
                                      AttributeDefinition.TYPE_STRING);
        att.setDisplayName("Logical Provisioning Rule");
        config.add(att);

        return config;
    }

    /**
     * This method should return a List of Feature object that describe the 
     * optional Features that this connector may publish.
     */
    public List<Feature> getSupportedFeatures() {

        List<Feature> features = new ArrayList<Feature>();
        features.add(Feature.COMPOSITE);
        return features;
    }

    /**
     * Typically this method would test connectivity with the
     * target system.  Here it would be nice to verify that the rule
     * exists but we can't do that without a SailPointContext.  
     * Could pull one from SailPointContextFactory but I'm concerned
     * about the precedent that sets for connector writers.
     */
    public void testConfiguration()
        throws ConnectionFailedException, ConnectorException  {

        // we must have an account rule, a remediation rule is optional
        Application app = getApplication();
        String ruleName = (String)app.getAttributeValue(ATTR_ACCOUNT_RULE);
        if (ruleName == null)
            throw new ConnectorException("Missing rule name");
    }

    //
    // Stub methods
    //

    public ResourceObject getObject(String objectType, 
                                    String identity,
                                    Map<String, Object> options)

        throws SchemaNotDefinedException,
               ObjectNotFoundException,
               ConnectionFailedException,
               ConnectorException {

        throw new RuntimeException("getObject is not supported by this connector.");
    }

    public CloseableIterator<ResourceObject> iterateObjects(String objectType, 
                                                            Filter filter,
                                                            Map<String, Object> options) 
        throws ConnectorException {

        throw new RuntimeException("iterateObjects is not supported by this connector.");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CompositeConnector
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Ask the rule to analyze the identity and produce composite links.
     */
    @SuppressWarnings("unchecked")
    public List<Link> getCompositeAccounts(SailPointContext context,
                                           Identity identity)
        throws GeneralException, ConnectorException {

        List<Link> links = null;
        Rule rule = null;

        Application app = getApplication();
        String ruleName = (String)app.getAttributeValue(ATTR_ACCOUNT_RULE);
        if (ruleName != null)
            rule = context.getObject(Rule.class, ruleName);

        // throw or ignore?
        // I don't really want to break aggregation for a misconfigured
        // composite app so log and more on
        if (rule == null) {
            if (ruleName == null) {
                log.error("Missing composite rule");
            } else { 
                if (log.isErrorEnabled())
                    log.error("Invalid composite rule: " + ruleName);
            }
        }
        
        HashMap<String,Object> args = new HashMap<String,Object>();
        args.put("identity", identity);
        args.put("application", app);
        Object result = context.runRule(rule, args);

        if (result instanceof Link) {
            links = new ArrayList<Link>();
            links.add((Link)result);
        }
        else if (result instanceof Collection) {
            for (Object o : (Collection)result) {
                if (o instanceof Link) {
                    if (links == null)
                        links = new ArrayList<Link>();
                    links.add((Link)o);
                }
            }
        }
        
        return links;
    }

    public ProvisioningPlan compileProvisioningPlan(SailPointContext context,
                                                    Identity identity,
                                                    ProvisioningPlan src)
        throws GeneralException {

        ProvisioningPlan compiled = src;
        Application app = getApplication();
        Rule rule = null;

        String ruleName = (String)app.getAttributeValue(ATTR_REMEDIATION_RULE);
        if (ruleName != null)
            rule = context.getObject(Rule.class, ruleName);

        // throw or ignore?
        // I don't really want to break aggregation for a misconfigured
        // composite app so log and move on
        if (rule == null) {
            if (ruleName != null) {
                if (log.isErrorEnabled())
                    log.error("Invalid remediation rule: " + ruleName);
            }
        }
        else {
            HashMap<String,Object> args = new HashMap<String,Object>();
            args.put("identity", identity);
            args.put("plan", src);
            Object result = context.runRule(rule, args);
            // if this returns null, should we return null?
            if (result instanceof ProvisioningPlan)
                compiled = (ProvisioningPlan)result;
        }

        return compiled;
    }



}
