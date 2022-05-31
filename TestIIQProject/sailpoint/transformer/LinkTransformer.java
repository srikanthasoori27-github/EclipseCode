package sailpoint.transformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;

/**
 * 
 * A Transformer that is in charge of converting over a Link object
 * to a Map and from a Map to a ProvisioningPlan.
 * 
 * All of the Link's native attribute values will typically be stored in the
 * attributes namespace, but are also include on the top level
 * for convenience.
 * 
 * All of the top level attributes are kept in a "sys." namespace to avoid
 * collisions with native attribute names.
 * 
 * Default behavior for this transformation does not include any of the MapModel
 * in its transformation.  If you want the transformer to perform any work, include
 * the OPT_LINK_EXPAND to include all the attributes listed below.
 * 
 * The MapModel for a link is as follows:
 * <pre>
 * attribute                    type         description
 * ------------------------------------------------------
 * sys.identity                 string       Name of the owning identity
 * sys.identityDisplayName      string       Display name of the owning identity
 * sys.id                       string       Hibernate Id of the link 
 * sys.nativeIdentity           string       The native identifier that can be used to bind to a Link natively ( accountId ) 
 * sys.uuid                     string       GUID for the object only used in some applications
 * sys.displayName              string       Display name for the account
 * sys.locked                   boolean      flag to indicate if account is locked
 * sys.disabled                 boolean      flag to indicate if the account is disabled
 * sys.hasEntitlements          boolean      flag to indicate if the link hasEntitlements
 * sys.lastRefresh              date         Date of the last refresh
 * sys.delete                   boolean      When set to true an AccountRequest with op=Delete will be added to the
 *                                           account request.
 * sys.operation                string       String representing the operation that should be applied to the generated 
 *                                           AccountRequest when a provisioning plan is assembled. By default the
 *                                           operation will be computed based on the changes.
 *                                           e.g.
 *                                           If the object doesn't exist it's a Create, if there is more 
 *                                           than just IIQ_DISABLED, IIQ_ENABLED,  or IIQ_UNLOCK in the 
 *                                           plan we set the operation is modify.  If any of the IIQ_ attributes
 *                                           are in the plan alone, we swap the operation to the IIQ_
 *                                           specified operation.
 *                                            
 * application.name             string       name of the application where the links live
 * application.type             string       type of the application
 * application.features         stringList   list of feature strings
 * application.connector        string       connector class
 * application.ownerDisplayName string       owner name
 * 
 * And an an attribute for each value in the link.attributes map 
 * where the name of the attribute is found in the application's
 * schema AND the value is non-null.
 * 
 * </pre>
 * 
 * 
 * @author dan.smith
 *
 */
public class LinkTransformer extends AbstractTransformer<Link> {
    
    private static Log log = LogFactory.getLog(LinkTransformer.class);

    /**
     * Operational prefixes we will apply to multivalued attributes so
     * allow things like:
     * 
     * e.g.
     * 
     * addMemberOf - list of values to add to the memberOf attribute
     * removeMemberOf - list of values to remove from the memberOf attribute
     * 
     */
    private static final String[] OP_PREFIXES = { "add", "remove" };

    
    public static final String ATTR_NATIVE_IDENTITY = "nativeIdentity";
    public static final String ATTR_LAST_REFRESH = "lastRefresh";
    public static final String ATTR_INSTANCE = "instance";    
    public static final String ATTR_HAS_ENTITLEMENTS = "hasEntitlements";
    public static final String ATTR_UUID = "uuid";
    public static final String ATTR_ATTRIBUTES = "attributes";    
    public static final String ATTR_LOCKED = "locked";
    public static final String ATTR_DISABLED = "disabled";
    public static final String ATTR_IDENTITY = "identity";
    public static final String ATTR_IDENTITY_DISPLAY_NAME = "identityDisplayName";
    
    /**
     * Special sys level flag that indicates the IIQ_DISABLED 
     * value.  If this is changed, the ProvisioningPlan will be updated
     * to include an AccountRequest for IIQ_DISABLED.  
     */
    public static final String ATTR_SYS_DISABLED = "sys.disabled";
    
    /**
     * Special sys level flag that indicates the IIQ_LOCKED 
     * value.  If this is changed, the ProvisioningPlan will be updated
     * to include an AccountRequest for IIQ_DISABLED.  
     */
    public static final String ATTR_SYS_LOCKED = "sys.locked";
    
    /**
     * Special sys leve flag that indicates the link should be deleted
     * when the provisioning plan is assembled from the model.
     */
    public static final String ATTR_SYS_DELETE = "sys.delete";
    
    /**
     * The path that holds the application name.
     * 
     * Started off as name, was moved to sysName to avoid conflicts with
     * schema attribute names. ( specifically named 'name' )
     * 
     */
    public static final String ATTR_APP_NAME = "sysName";
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Options
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Flag that when enabled, before pushing the plan will check for account
     * existence on a Create.
     */    
    public static final String OPT_CHECK_ACCOUNT_EXISTANCE = "checkAccountExists";

    
    public LinkTransformer(SailPointContext ctx) {
        this(ctx, null);
    }
    
    public LinkTransformer(SailPointContext ctx, Map<String,Object> optMap) {
        context = ctx;
        setOptions(optMap);
    }

    /**
     * Build a Map out of a single link.   
     * 
     * In this Map we flatten all of the attribute so they appear at the "root"
     * of the MapModel.
     * 
     * All of our other attributes are prefixed with out "sys." namespace to
     * avoid conflicts with schema attribute names.
     * 
     */
    @Override
    public Map<String, Object> toMap(Link link) throws GeneralException {
        
        Map<String, Object> linkMap = new HashMap<String, Object>();
        
        Map<String,Object> sys = buildBaseSailPointObjectInfo(link);
        MapUtil.put(linkMap, "sys", sys);        
        if ( link == null )
            return linkMap;
        
        appendApplicationMap(sys, link.getApplication());
        
        // Adorn some information about the "owning" identity where it is current correlated
        Identity id = link.getIdentity();
        String identityName = null;
        String identityDisplayName = null;
        if ( id != null ) {
            identityName = id.getName();
            identityDisplayName = id.getDisplayableName();
        }
        MapUtil.put(sys, ATTR_IDENTITY, identityName);
        MapUtil.put(sys, ATTR_IDENTITY_DISPLAY_NAME, identityDisplayName);        
        MapUtil.put(sys, ATTR_ID, link.getId());
        MapUtil.put(sys, ATTR_NATIVE_IDENTITY, link.getNativeIdentity());
        MapUtil.put(sys, ATTR_INSTANCE, link.getInstance());
        MapUtil.put(sys, ATTR_DISPLAY_NAME, link.getDisplayName());
        MapUtil.put(sys, ATTR_DISABLED, link.isDisabled());
        MapUtil.put(sys, ATTR_LOCKED, link.isLocked());
        MapUtil.put(sys, ATTR_DISPLAY_NAME, link.getDisplayableName());
        MapUtil.put(sys, ATTR_LAST_REFRESH, link.getLastRefresh());
        MapUtil.put(sys, ATTR_UUID, link.getUuid());
        MapUtil.put(sys, ATTR_HAS_ENTITLEMENTS, link.hasEntitlements());
       
        MapUtil.put(linkMap, ATTR_APP_NAME, link.getApplicationName());        
        flattenLinkAttributes(linkMap, link.getAttributes());
        return linkMap;
    }
    
    /**
     * Flatten out the attributes from the Link attributes for 
     * easier access.
     * 
     * 
     * @param linkModel
     * @param attributes
     */
    private void flattenLinkAttributes(Map<String,Object> linkModel, 
                                   Attributes<String,Object> attributes) 
        throws GeneralException {
                
        if ( !Util.isEmpty(attributes) ) {
            Set<String> keys = attributes.keySet();
            if ( keys != null ) {
                Schema accountSchema = getAccountSchema(linkModel);
                if ( accountSchema == null ) {
                    throw new GeneralException("No account schema found for link model.");
                }
                List<String> other = this.getOtherAttributeNames(accountSchema);
                Iterator<String> it = keys.iterator();
                if ( it != null ) {
                    while ( it.hasNext() ) {
                        String key = it.next();
                        if ( key == null ) continue;
                        if ( accountSchema.getAttributeDefinition(key) != null || other.contains(key) ) {
                            Object value = attributes.get(key);
                            /*
                            * IIQETN-282 :- Enabling schema attribute name to support slashes in the name.
                            */
                            if ( key.contains(" " ) || key.contains(".") || key.contains("/")) {
                                // attributes with spaces or periods must be wrapped in quotes
                                key = "\"" + key + "\"";
                            }
                            MapUtil.put(linkModel, key, value);
                        }
                    }
                }
                //bug 23172 We need to add the linkkeys to the linkmodel even we do not have a value
                List<String> linkKeys = accountSchema.getAttributeNames();
                for(String linkKey : linkKeys){
                    if(!keys.contains(linkKey)){
                        /*
                        * IIQETN-282 :- Enabling schema attribute name to support slashes in the name.
                        */
                        if(linkKey.contains(".") || linkKey.contains(" ") || linkKey.contains("/")){
                            linkKey = "\"" + linkKey + "\"";
                        }
                        MapUtil.put(linkModel, linkKey, null);
                    }
                }
            }
        }        
    }
    
    private Schema getAccountSchema(Map<String,Object> linkModel) 
        throws GeneralException {
        
        String appName = (String)MapUtil.get(linkModel, ATTR_APP_NAME);
        if ( appName != null ) {
            Application app = context.getObjectByName(Application.class, appName);
            return ( app != null ) ? app.getAccountSchema() : null;
        }
        return null;
    }
    
    /**
     * Build some data around each application, if this gets too unwildly then 
     * lets push it into its own transformer.
     * 
     * TODO: Think about extended application attributes.
     * 
     * @param linkMap
     * @param app
     * @throws GeneralException
     */
    private void appendApplicationMap(Map<String,Object> linkMap, Application app) 
        throws GeneralException {
        
        HashMap<String,Object> appMap = new HashMap<String,Object>();
        if ( app != null ) {
            appendBaseSailPointObjectInfo(app, appMap);
            MapUtil.put(appMap,"features" , buildFeatureStrings(app.getFeatures()));
            MapUtil.put(appMap,"type", app.getType());
            MapUtil.put(appMap,"connector", app.getConnector());
            Identity owner = app.getOwner();
            if ( owner != null ) {
                HashMap<String,Object> ownerMap = new HashMap<String,Object>();
                appendBaseSailPointObjectInfo(owner,ownerMap);
                MapUtil.put(appMap, "owner", ownerMap);
            }
            MapUtil.put(linkMap, "application", appMap);
        }   
    }
    
    /**
     * Keep this list of Features as String inside the MapModel. 
     * 
     * @param features
     * @return
     */
    private List<String> buildFeatureStrings(List<Feature> features) {
        List<String> featureStrings = new ArrayList<String>();
        if ( features != null ) {
            for ( Feature feature : features ) {
                if ( feature == null ) 
                    continue;
                featureStrings.add(feature.toString());
            }
        }
        return featureStrings;
    }            
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Map Model to ProvisioningPlan 
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Build a provisioning plan out of the model changes.
     * 
     * @param linkModel
     * @param ops
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlan mapToPlan(Map<String,Object> linkModel, Map<String,Object> ops) 
        throws GeneralException {        
        
        ProvisioningPlan plan = null;        
        if ( linkModel != null ) {
            Map<String,Object> existingModel = null;
            Identity identity = null;
            String id = (String)MapUtil.get(linkModel, "sys.id");
            if ( id == null ) {
                identity = resolveIdentity(linkModel);               
            } else {
                Link link = context.getObjectById(Link.class, id);
                if ( link != null ) {
                    existingModel = toMap(link);
                    identity = link.getIdentity();
                }
            }               
            AccountRequest acct = buildAccountRequest(existingModel, linkModel);                        
            if ( acct != null ) {                        
                plan = new ProvisioningPlan();                           
                plan.setIdentity(identity);
                plan.add(acct);
            }
        }
        return plan;
    }  
        
    /***
     * Given a link build an account request, to target changes to the Link.
     * 
     * Always skip attributes not declared in the schema.
     * 
     * @param existingLink, Map<>
     * @return AccountRequest that will apply the changes to the Link
     */
    private AccountRequest buildAccountRequest(Map<String,Object> existingModel, Map<String,Object> linkModel) 
        throws GeneralException {
        
        AccountRequest acct = null;
        if ( linkModel != null ) {
            Schema accountSchema = getAccountSchema(linkModel);
            if ( accountSchema == null )
                throw new GeneralException("Could not find application account schema.");
            
            acct = new AccountRequest();
            setNativeIdentity(acct, accountSchema, linkModel);
            acct.setInstance((String)MapUtil.get(linkModel, ATTR_INSTANCE));
            String appName = (String)MapUtil.get(linkModel, ATTR_APP_NAME);
            acct.setApplication(appName);
            
            String linkId = (String)MapUtil.get(linkModel, "sys.id");
            if ( linkId != null ) {
                acct.setOperation(Operation.Modify);
            } else {
                acct.setOperation(Operation.Create);
                validateNewAccount(acct);
            }            
            
            // make a list of the things that are acceptable outside
            // the schema
            List<String> otherAttrs = getOtherAttributeNames(accountSchema);
            String identityAttribute = accountSchema.getIdentityAttribute();
            
            Iterator<String> keys = linkModel.keySet().iterator();
            if ( keys != null ) {
                while ( keys.hasNext() ) {
                    String key = keys.next();
                    
                    if ( key != null ) {
                        boolean multi = false;
                        AttributeDefinition def = accountSchema.getAttributeDefinition(key);
                        if ( def == null && !otherAttrs.contains(key)  ) {
                            log.debug("Attribute definition missing for [" + key +"], skipping...");
                            continue;
                        } else {
                            if ( def != null ) {
                                multi = def.isMulti();
                            }
                        }
                        Object linkValue = null;
                        if ( existingModel != null ) 
                            linkValue = existingModel.get(key);
                        Object value = MapUtil.get(linkModel, key);
                        ProvisioningPlan.Operation op = ProvisioningPlan.Operation.Set;
                        if ( multi ) {
                            boolean handled = handleGranularMulti(acct, key, linkModel);
                            if ( !handled ) {
                                // Compute the differences
                                Difference diff = Difference.diff(linkValue, value, Integer.MAX_VALUE);
                                if ( diff != null ) {
                                    List<String> addedValues = diff.getAddedValues();
                                    if ( Util.size(addedValues) > 0 ) {
                                        AttributeRequest attr = new AttributeRequest(key, ProvisioningPlan.Operation.Add, addedValues);                            
                                        acct.add(attr);  
                                    } 
                                    List<String> removedValues = diff.getRemovedValues();
                                    if ( Util.size(removedValues) > 0 ) {
                                        AttributeRequest attr = new AttributeRequest(key, ProvisioningPlan.Operation.Remove, removedValues);                            
                                        acct.add(attr);
                                    }
                                }
                            }
                        } else {
                            if ( Util.nullSafeEq(identityAttribute, key) ) {
                                // we strip and in some cases don't require the identityAttribute
                                // since we don't support rename right now ignore these type of changes.
                                continue;
                            }
                            Difference diff = Difference.diff(linkValue, value);
                            if ( diff != null ) {            
                                AttributeRequest attr = new AttributeRequest(key, op, value);                            
                                acct.add(attr);
                            }
                        }
                    }                            
                }                
            }
        }
        
        handleInternalAttributes(existingModel, linkModel, acct);        
        // normalize atomic account operations and ignore empty requests
        acct = normalize(acct);
        
        // BUG#16904
        // Allow the operation we've resolved to be explicitly configured
        // as part of the model
        if ( acct != null ) {
            Object override = MapUtil.get(linkModel, ATTR_SYS_OPERATION);
            if ( override != null ) {
                Operation op = null;
                try {
                    op = AccountRequest.Operation.valueOf((String)override.toString());
                } catch(Exception e) {
                    // don't choke because of this...
                    log.warn("Exception resolving operation override." + override);
                }
                if ( op != null ) {
                    acct.setOperation(op);
                }    
            }
        }
        return acct;
    }
    
    /**
     * Handle non-schema other attributes here like sys.disabled and sys.locked.
     *  
     * @param existingModel
     * @param linkModel
     * @param acct
     * @throws GeneralException
     */
    private void handleInternalAttributes(Map<String,Object> existingModel, 
                                          Map<String,Object> linkModel, 
                                          AccountRequest acct) 
        throws GeneralException {
        
        //
        // Explicitly look for changes to sys.disabled and sys.unlock 
        //
        handleBoolean(existingModel, linkModel, acct, ATTR_SYS_DISABLED, Connector.ATT_IIQ_DISABLED );
        handleBoolean(existingModel, linkModel, acct, ATTR_SYS_LOCKED, Connector.ATT_IIQ_LOCKED );      
        
        boolean delete = Util.otob(MapUtil.get(linkModel, "sys.delete"));
        if ( delete ) {
            acct.setOperation(Operation.Delete);
        }
    }
    
    
    /**
     * If the native Identity wasn't explicitly set then try
     * and resolve it using one of the values on the 
     * model.
     * 
     * Use the schema's identity attribute to guide which of the values
     * to use.
     * 
     * @param acct
     * @param accountSchema
     * @param linkModel
     * @throws GeneralException
     */
    protected void setNativeIdentity(AccountRequest acct, Schema accountSchema, Map<String,Object> linkModel) 
        throws GeneralException {
        
        String nativeIdentity = (String)MapUtil.get(linkModel, prefixWithSystem(ATTR_NATIVE_IDENTITY) );
        if ( nativeIdentity == null ) {
            String identityAttr = accountSchema.getIdentityAttribute();
            if ( identityAttr != null ) {
                nativeIdentity = (String)MapUtil.get(linkModel,identityAttr);
            }
        }
        acct.setNativeIdentity(nativeIdentity);        
    }
    
    /**
     * Go through a request and if the only attribute request is
     * IIQ_DISABLED or or IIQ_UNLOCK, reduce this to just
     * and account request targeting the correct operation.
     * 
     * Filter empty account requests.
     * 
     * @param req The AccountRequest to normalize
     * 
     * @return The normalized AccountRequest
     */
    private AccountRequest normalize(AccountRequest req) {
        
        AccountRequest clone = null;        
        if ( req == null ) {
            return null;
        }                
        clone = req.clone();     
        
        List<AttributeRequest> attrs = clone.getAttributeRequests();
        int attrRequestCount = Util.size(attrs);
        if ( attrRequestCount == 0 && Util.nullSafeEq(req.getOperation(), Operation.Modify ) ) {
            // A modify without any attribute requests is a no-op
            return null;
        }

        if ( attrRequestCount == 1 ) {
            AttributeRequest attr = attrs.get(0);
            if ( attr != null ) {
                AccountRequest.Operation op = null;
                String name = attr.getName();
                if ( Util.nullSafeEq(Connector.ATT_IIQ_LOCKED, name) ) {
                    boolean boolVal = Util.otob(attr.getValue());
                    if ( !boolVal ) {
                        op = Operation.Unlock;
                    }
                } else
                if ( Util.nullSafeEq(Connector.ATT_IIQ_DISABLED, name) ) {
                    boolean boolVal = Util.otob(attr.getValue());
                    if ( boolVal ) {
                        op = Operation.Disable;
                    } else {
                        op = Operation.Enable;
                    }
                }
                if ( op != null ) {
                    // no reason to have attribute requests reset the modify
                    // to the explict operation and null attribute requests
                    req.setOperation(op);
                    req.setAttributeRequests(null);
                }
            }
        }
        return clone;
    }    
    
    /**
     * Build a list of things that can be pushed to the connector. 
     * In many instances there the identityAttribute and
     * displayName are missing from the schema, make sure
     * we add them here...
     * 
     * TODO: this is screwy, but we arn't consistent about this..
     * We should probably require it also be in the schema and returned on each object..
     *  
     * @param schema
     * @return
     */
    public static List<String> getOtherAttributeNames(Schema schema) {
        List<String> names = new ArrayList<String>();        
        // this won't be in the schema but we want to allow editing it.
            
        if ( schema != null ) {
            String identityAttribute = schema.getIdentityAttribute();
            if ( identityAttribute != null ) {
                names.add(identityAttribute);
            }
            
            String displayAttribute = schema.getDisplayAttribute();
            if ( displayAttribute != null && !names.contains(displayAttribute) ) {
                names.add(displayAttribute);
            }
        }
        names.add(Connector.ATT_IIQ_DISABLED);
        names.add(Connector.ATT_IIQ_LOCKED);
        List<String> secrets = ProvisioningPlan.getSecretProvisionAttributeNames();
        for ( String secret : secrets ) {
            names.add(secret);
        }
        return names;
    }
    
    /**
     * Look for specially named attributes in the model to help support
     * incremental updates.  This allows callers to set values on a 
     * on an attribute named addXYZ and removeXYZ for any multi-valued
     * attributes without having the entire list to set.
     * 
     * @param acct
     * @param attrName
     * @param linkModel
     * @return true when the attribute had either and add or remove attribute
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    private boolean handleGranularMulti(AccountRequest acct, String attrName, Map<String,Object> linkModel) 
        throws GeneralException {
        
        boolean foundOp = false;
        for ( String op : OP_PREFIXES ) {
             String opAtt = op + Util.capitalize(attrName);
             if ( linkModel.containsKey(opAtt) ) { 
                 Object opValue = MapUtil.get(linkModel, opAtt);
                 if ( opValue == null ) {
                     opAtt = op + attrName;
                     opValue = MapUtil.get(linkModel, opAtt);
                 }
                 foundOp = true;
                 List opList = null;
                 if ( opValue != null ) 
                     opList = Util.asList(opValue);
                 if ( Util.size(opList) > 0 ) {
                     AttributeRequest attr = new AttributeRequest(attrName, ProvisioningPlan.Operation.valueOf(Util.capitalize(op)), opList);
                     acct.add(attr);
                 }
             } 
        }
        return foundOp;
    }
        
    /**
     * Resolve the Identity object associated with the Link view.
     * 
     * @param model
     * @return
     * @throws GeneralException
     */
    private Identity resolveIdentity(Map<String,Object> model ) throws GeneralException {
        String identityName = (String)MapUtil.get(model, "sys.identity");
        if ( identityName != null ) {
            return context.getObjectByName(Identity.class, identityName);
        }
        return null;
    }
    
    /**
     * Reach out to the connector and verify the account doesn't
     * already exist.
     * 
     * @param req
     * @throws GeneralException
     */
    private void validateNewAccount(AccountRequest req) throws GeneralException {
        Application app = req.getApplication(context);
        
        boolean checkExistence = Util.getBoolean(getOptions(), OPT_CHECK_ACCOUNT_EXISTANCE);
        if ( checkExistence && !app.supportsFeature(Feature.NO_RANDOM_ACCESS) ) {
            Connector con = ConnectorFactory.getConnector(app, null);
            ResourceObject ro = null;
            try {
                ro = con.getObject(Connector.TYPE_ACCOUNT, req.getNativeIdentity(), null);
            } catch (ObjectNotFoundException nf) {
                // this is ok
            } catch (ConnectorException e) {
                throw new GeneralException("Problem contacting the connector during new account validation." + e + " " + req.toXml());
            }
            if ( ro != null )
                throw new GeneralException("Existing account with the id " + req.getNativeIdentity() + " already found on application");
        }
    }
    
    /**
     * 
     * Check for a change to the model path provided, if there is a change
     * merge the change into the plan's acount request.
     * 
     * @param existingModel
     * @param linkModel
     * @param acct
     * @param modelPath
     * @param attrName
     * 
     * @throws GeneralException
     */
    private void handleBoolean(Map<String,Object> existingModel, 
                               Map<String,Object> linkModel, 
                               AccountRequest acct, 
                               String modelPath, 
                               String attrName) throws GeneralException {
                
        Boolean updated = false;        
        if ( linkModel != null ) 
            updated = Util.otob(MapUtil.get(linkModel, modelPath));
        
        Boolean current = false;
        if ( existingModel != null )
            current = Util.otob(MapUtil.get(existingModel, modelPath));
        
        if ( !Util.nullSafeEq(updated, current) ) {
            AttributeRequest attr = acct.getAttributeRequest(attrName);
            // prevent duplicates and let the override any other changes?
            if ( attr == null ) {
                attr = new AttributeRequest(attrName, ProvisioningPlan.Operation.Set, updated);
            } else {
                attr.setValue(updated);
            }
            acct.add(attr);
        }
    }
    
}
