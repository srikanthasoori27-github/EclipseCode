/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A utility class that provides a few shared services for processing
 * ResourceObjects from the Connector.
 *
 * This was factored out of ConnectorProxy because we need the same
 * things for ResourceEventService.
 *
 * Author: Jeff (ConnecctorProxy Kelly & Dan)
 *
 */

package sailpoint.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.ManagedResource;
import sailpoint.object.ManagedResource.ResourceAttribute;
import sailpoint.object.ProvisioningConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;

public class ConnectorUtil  {

    //////////////////////////////////////////////////////////////////////
    //
    // Customization Rule
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ConnectorUtil.class);

    /**
     * Run the customization rule on a ResourceObject.
     * The "state" map is used by the Aggregator but
     * not by the ResourceEventService.
     */
    static public ResourceObject runCustomizationRule(SailPointContext context, 
                                                      Connector connector, 
                                                      ResourceObject object, 
                                                      Map<String,Object> state)
        throws GeneralException {

        Application app = connector.getApplication();
        Rule rule = app.getCustomizationRule();
        if (rule != null) {
            HashMap<String,Object> args = new HashMap<String,Object>();
            args.put("connector", connector);
            args.put("application", app);
            args.put("object", object);
            // jsl - what is this for, to save state during aggregation?
            if (state == null)
                state = new HashMap<String,Object>();
            args.put("state", state);

            Object result = context.runRule(rule, args);
            if (result instanceof ResourceObject)
                object = (ResourceObject)result;
        }
        return object;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Name Mapping
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Map names in a ResourceObject returnerd by the connector before
     * passing it up to IIQ.
     *
     * TODO: Need to decide how the ProvisoiningConfig interacts
     * with the customization rule.  The customization rule is being applied
     * when returning ResourceObjects, but it is not used when 
     * provisioning.  This seems to be okay because we tend to use
     * customization rules only for read-only file connectors.  But
     * to be consistent we may want another rule or pass a direction
     * argument to the current rule.
     *
     * Originally name mapping was defined with a ProvisioningConfig
     * object which contained ManagedResources and ResourceAttributes.
     * This was a vestige of the old IntegrationConfig model that is
     * overly complicated for simple read/write connectors.  Now, the
     * expectation is that name mapping will be defined by setting
     * the _internalName property of the AttributeDefinition in the
     * Schemas which is much simpler and supported in the UI.
     */
    static public void mapNamesFromConnector(Application app, ResourceObject obj) {

        if ( obj == null ) {
            log.info("Mapping names for null object, returning...");
            return;
        }
        
        if (log.isInfoEnabled())
            log.info("Mapping names for account " + obj.getIdentity());

        // note that we must use the source application if we are 
        // redirecting
        String type = obj.getObjectType();
        if (type == null) {
            // is this safe?
            type = Connector.TYPE_ACCOUNT;
        }

        Attributes<String,Object> objatts = obj.getAttributes();
        if (objatts != null) {

            // new way
            Schema schema = app.getSchema(type);
            if (schema != null) {
                List<AttributeDefinition> atts = schema.getAttributes();
                if (atts != null) {
                    for (AttributeDefinition att : atts) {

                        String local = att.getName();
                        String internal = att.getInternalName();
                        if (internal != null) {
                            if (objatts.containsKey(internal)) {
                                if (log.isInfoEnabled())
                                    log.info("Changing attribute " + internal + " to " + local);
                                
                                Object value = objatts.get(internal);
                                objatts.remove(internal);
                                objatts.put(local, value);
                            }
                        }
                    }
                }
            }

            // old way
            if (type.equals(Connector.TYPE_ACCOUNT)) {
                ProvisioningConfig config = app.getProvisioningConfig();
                if (config != null) {
                    ManagedResource mr = config.getManagedResource(app.getName());
                    if (mr == null) {
                        // since there will almost always be just one, don't
                        // require it to have a name, convenience for XML writers
                        List<ManagedResource> resources = config.getManagedResources();
                        if (resources != null && resources.size() == 1) {
                            mr = resources.get(0);
                            if (mr.getApplication() != null)
                                mr = null;
                        }
                    }

                    if (mr != null) {
                        List<ResourceAttribute> atts = mr.getResourceAttributes();
                        if (atts != null) {
                            for (ResourceAttribute att : atts) {
                                String local = att.getLocalName();
                                String internal = att.getName();

                                if (local != null && internal != null &&
                                    objatts.containsKey(internal)) {
                                    
                                    if (log.isDebugEnabled())
                                        log.info("Changing attribute " + internal + " to " + local);
                                    
                                    Object value = obj.get(internal);
                                    obj.remove(internal);
                                    obj.put(local, value);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Map names in an incoming ProvisioningPlan before passing
     * it off to the Connector.
     *
     * We are only handling AccountRequests right now, in theory
     * will need to handle ObjectRequests for groups some day.
     */
    static public void mapNamesToConnector(Application app, ProvisioningPlan plan) {

        log.info("Mapping names for incomming provisioning plan");

        // note that we must use the source application if redirecting
        ProvisioningConfig pconfig = app.getProvisioningConfig();

        // For requests targeted at this application, the Schema can
        // now have name mappings.  Request for other apps being
        // proxied through this one won't use this
        Schema ourSchema = app.getSchema(Connector.TYPE_ACCOUNT);

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                
                String targetapp = account.getApplication();

                // find the Schema
                Schema schema = null;
                if (app.getName().equals(targetapp))
                    schema = ourSchema;
                else {
                    // !! TODO: If we're using proxied apps the mappings must
                    // be configured in our ManagedResource list, we should also
                    // allow the mappings in the proxied app's schema?  
                    // It's nice to have them encapsulated here...
                }

                // and a ManagedResource
                ManagedResource resource = null;
                if (pconfig != null) {
                    resource = pconfig.getManagedResource(targetapp);
                    if (resource == null) {
                        // since there will almost always be just one, don't
                        // require it to have a name, a minor convenience for XML writers
                        List<ManagedResource> resources = pconfig.getManagedResources();
                        if (resources != null && resources.size() == 1) {
                            ManagedResource res = resources.get(0);
                            if (res.getApplication() == null) {
                                // assume it's for this one
                                resource = res;
                            }
                        }
                    }
                }

                // map the resource name itself
                if (resource != null && resource.getName() != null) {
                    if (log.isInfoEnabled())
                        log.info("Changing application name from " + targetapp + 
                                 " to " + resource.getName());
                    
                    account.setUnmappedApplication(account.getApplication());
                    account.setApplication(resource.getName());
                }

                // map the attribute names
                List<AttributeRequest> atts = account.getAttributeRequests();
                if (atts != null) {
                    for (AttributeRequest req : atts) {
                        String localName = req.getName();
                        boolean mapped = false;

                        // first try the Schema
                        if (schema != null) {
                            AttributeDefinition def = schema.getAttributeDefinition(localName);
                            if (def != null) {

                                String internal = def.getInternalName();
                                if (internal != null) {
                                    if (log.isInfoEnabled())    
                                        log.info("Changing attribute " + localName + 
                                                 " to " + internal);
                                    
                                    req.setUnmappedName(req.getName());
                                    req.setName(internal);
                                    mapped = true;
                                }
                            }
                        }

                        // then the ManagedResource
                        if (!mapped && resource != null) {

                            ResourceAttribute ratt = resource.getResourceAttribute(localName);
                            if (ratt != null) {
                                String internal = ratt.getName();
                                if (internal != null) {
                                    if (log.isInfoEnabled())
                                        log.info("Changing attribute " + localName + 
                                                 " to " + internal);
                                    
                                    req.setUnmappedName(req.getName());
                                    req.setName(internal);
                                }
                            }
                        }
                    }
                }

                // TODO: What about PermissioNRequests?
                // assuming those don't have mappings
            }
        }

        // TODO: Support ObjectRequests for groups

    }
    
    /**
     * Given a plan that has just been processed by the Connector's
     * provision() method, undo the name transformations
     * we did in mapNamesToConnector().  This relies on the original names having
     * been saved in transient properties.  We could do unmapping of attributes
     * by reversing the logic but it's harder for the application name and it's
     * easier just to save them.
     */
    static public void mapNamesFromConnector(Application app, ProvisioningPlan plan) {

        log.info("Unmapping names for returned provisioning plan");

        // note that we must use the source application if redirecting
        ProvisioningConfig pconfig = app.getProvisioningConfig();
        Schema ourSchema = app.getSchema(Connector.TYPE_ACCOUNT);

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                
                String orig = account.getUnmappedApplication();
                if (orig != null) {
                    account.setApplication(orig);
                    account.setUnmappedApplication(null);
                }

                List<AttributeRequest> atts = account.getAttributeRequests();
                if (atts != null) {
                    for (AttributeRequest req : atts) {
                        orig = req.getUnmappedName();
                        if (orig != null) {
                            req.setName(orig);
                            req.setUnmappedName(null);
                        }
                    }
                }

                // TODO: What about PermissioNRequests?
                // assuming those don't have mappings
            }
        }

        // TODO: Support ObjectRequests for groups

    }    

    


}

