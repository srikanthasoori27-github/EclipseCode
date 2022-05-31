/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Correlator;
import sailpoint.api.Explanator;
import sailpoint.api.IdentityService;
import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.CompositeDefinition;
import sailpoint.object.CompositeDefinition.Tier;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * Analyzes an identity's account to determine if he should be assigned
 * links on the given logical application. If so the connector
 * creates new links and returns them to the caller.
 * 
 * Originally known as the Composite Connector renamed to Logical 
 * Connector in 5.1.
 * <p>
 * In addition to this implementation which has a model around the
 * Tier relationships  there is also a RuleBased implementation
 * named RuleLogicalConnector.
 * <p>
 * Starting in 5.1 Tier objects can contain an IdentitySelector.
 * <p>
 * The selector to help identify the conditions that an Identity 
 * should have from an attribute matching perspective. These conditions
 * are the mandatory conditions necessary.  The "extra" attributes
 * are specified in the ManagedEntitlements table for the 
 * logical application.
 * <p>
 * The Schema as it did prior to 5.1 contains a mapping back to the
 * source attribute that "sourced" the logical application. 
 * <p>
 * The ManagedEntitlements table is used to filter the values that
 * should appear in the logical Link.  This means the managed
 * attribute table needs to have all of the values in the selector
 * in addition to any attributes that are part of the logical 
 * application.
 * <p>
 * During provisioning this connector uses the Schema to unwind
 * the logical plan and break it down into what is required down
 * at each of the tier levels.  
 * <p>
 * Modify requests are pretty straight forward, the create case
 * is murky.  Account requests like Enable, Disable and Unlock
 * are by default just returned so it can be handled downstream
 * in a manual action. 
 * <p>
 * Account removal and revoke optionally removes all of the
 * entitlement values found on the logical account from the
 * account on the tiers.
 *   
 * TODOs:
 * 
 *   1) Should we store which attributes caused the match is that interesting?
 *   2) We have work todo from a Form relationship in order to handle Creates here
 *      nicely 
 *   
 * @See {@link sailpoint.connector.RuleLogicalConnector#RuleLogicalConnector(Application)}
 * @See sailpoint.object.CompositeDefinition
 * @see sailpoint.object.CompositeDefinition.Tier  
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class DefaultLogicalConnector extends AbstractLogicalConnector {

    private static Log log = LogFactory.getLog(DefaultLogicalConnector.class);
    public static final String CONNECTOR_TYPE = "Logical";

    /**
     * When enabled entitlement filtering will not be performed during
     * link promotion. Otherwise the entitlement values from the source 
     * links(tiers) will be filtered using the Managed Attributes for
     * the logical application.
     */
    public static final String CONFIG_DISABLE_ENTITLEMENT_FILTERING = "disableEntitlementFiltering";
    public static final String CONFIG_DISABLE_PERMISSION_FILTERING = "disablePermissionFiltering";
    
    /**
     * New flag added in 6.0 that controls both entitlement and permission filtering.
     */
    public static final String CONFIG_DISABLE_LINK_FILTERING = "disableLinkFiltering";
    
    /**
     * Used by the MissingEntitlementScan so each application can disable the filtering
     * that happens by default based on the attribute names, values in each
     * Tier selector object.  Added in 5.2.
     */
    public static String CONFIG_DONT_FILTER_MANAGED_ENTITLEMENTS = "disableManagedEntitlementFiltering";
    
    /**
     * Suffix appended to the AccountOperation to enable the "fanning out"
     * of a request to the configured tiers.
     */
    public static final String CONFIG_TARGET_TIER_SUFFIX = ".targetTierApps";

    /**
     * Flag that can be set so that when revokes OR removes requests
     * for a logical accounts ALL of the entitlements will be removed
     * from the tiers.
     */
    public static final String CONFIG_DELETE_REMOVES_ENTITLEMENTS = "onDeleteRemoveEntitlements";
    
    /**
     * Normally we want to remove the entitlement on the teir application 
     * when we remove the entitlement on the logical application. You can 
     * set this to make them more sticky
     */
    
    public static final String CONFIG_RETAIN_ENTITLEMENT_ON_REMOVE = "RetainEntitlementOverRemove";
    
    /**
     * Map keyed by the application name, which corresponds to the tier application.  
     * Each value in the Map is another map, which is keyed by the logcal attribute 
     * name ( from the logical schema ) and has a value of the tier application's
     * name.
     * 
     * TODO : can this be re-factored seems clumsy? 
     */
    private Map<String, Map<String, String>> attributeSources;

    /**
     * When comparing composite tiers we use this to match 
     * Identities to tier selectors.  Create one up front
     * and re-use it.
     */
    Matchmaker _matcher;

    /**
     * Base constructor.
     * @param composite The composite application
     */
    public DefaultLogicalConnector(Application composite) {
        super(composite);        
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Logical Application Interface
    //
    ///////////////////////////////////////////////////////////////////////////
   
    /**
     * Given an Identity compute the Logical Links.  This method will first
     * check for an "Account Rule" defined on the CompositeDefinition.
     * If the account rule is defined it takes precedence over the 
     * logical computation using the tier configuration.
     * <br>
     * If the Account rule is not defined attempt to match the Identity
     * to the tiers using the IdentitySelector defined on the Tier.
     *
     * @param ctx
     * @param identity
     * @return
     * @throws GeneralException
     * @throws ConnectorException
     */
    public List<Link> getCompositeAccounts(SailPointContext ctx,
                                           Identity identity)
        throws GeneralException, ConnectorException {

        List<Link> newLogicalLinks = new ArrayList<Link>();
        if ( _matcher == null ) _matcher = new Matchmaker(ctx);

        Application composite = getApplication();

        if ( !composite.isLogical() ) {
            // this would be odd, but guard against it
            if (log.isErrorEnabled())
                log.error(composite.getName() + " is not a logical application.");
            
            return null;
        }

        CompositeDefinition compositeDef = composite.getCompositeDefinition();
        if ( composite.getCompositeDefinition() == null ){
            if (log.isErrorEnabled())
                log.error(composite.getName() + " does not have a logical definition.");
            
            return null;
        }
        // if the composite definition gives us a single account rule which does all the correlation
        // for us, this is simple. If not, we have to manually perform the correlation using
        // the correlation strategy defined on each of the composite's tiers.
        if (compositeDef.hasAccountRule()) {
            newLogicalLinks = runAccountRule(ctx, getApplication(), identity);
        } else 
        if (compositeDef.getTiers() != null) {
            // Initialize tiers list 
            this.attributeSources = new HashMap<String, Map<String, String>>();
            for(String tierName : compositeDef.getTierAppList()){
                QueryOptions ops = new QueryOptions(Filter.eq("name", tierName));
                int foundApps = ctx.countObjects(Application.class, ops);
                // if a tier can't be found, quit, but don't throw so we don't kill aggregation
                if (foundApps == 0){
                    if (log.isErrorEnabled())
                        log.error("Could not find tier application '" + tierName + "'");
                    
                    return null;
                }
                this.attributeSources.put(tierName, new HashMap<String, String>());
            }

            Schema compositeSchema = getApplication().getSchema(Application.SCHEMA_ACCOUNT);            
            if (compositeSchema != null && compositeSchema.getAttributes() != null){
                for (AttributeDefinition def : compositeSchema.getAttributes()){
                    if (def.getCompositeSourceApplication() != null && def.getCompositeSourceAttribute() != null){
                        //Prevent NPE
                        //Ensure that we have the application from the AttributeDefintiion in our Attribute Sources
                        Map attSource = this.attributeSources.get(def.getCompositeSourceApplication());
                        if(attSource!=null) {
                            attSource.put(def.getCompositeSourceAttribute(), def.getInternalOrName());
                        } else {
                            log.debug("Skipping "+def.getInternalOrName() + ". The application corresponding to the Attribute Definition could not be found in the tier apps.");
                        }
                    }
                }
            }
            newLogicalLinks = correlateTiers(ctx, compositeDef, identity);
        }
        return newLogicalLinks;
    }

    /**
     * Using the IdentitySelector defined on the Tier object attempt to match the
     * Identity.
     */
    private List<Link> correlateTiers(SailPointContext ctx, CompositeDefinition compositeDef, Identity identity)
            throws ConnectorException, GeneralException{

        List<Link> newLogicalLinks = new ArrayList<Link>();

        Application primary = ctx.getObjectByName(Application.class, compositeDef.getPrimaryTier());
        if (primary != null){
            IdentityService identSvc = new IdentityService(ctx);
            // First off get all of the primary links from the user.
            List<Link> primaryLinks = identSvc.getLinks(identity, primary);
            Tier primaryTier = compositeDef.getTierByAppName(compositeDef.getPrimaryTier());
            
            // If the user has one or more accounts on the primary check it.
            if ( Util.size(primaryLinks) > 0 ) {                
                                           
                // Iterate over all of the primary links and see if we can
                // match them with the tiers..
                //                
                // Assume links can be consumed more then once
                //

                // TODO: Do we want to allow a way to configure the logical
                // so tiers can be consumed only once?                
                for (Link primaryLink : primaryLinks) {            
                    // Check to make sure this primary link matches the
                    // requirements of the primary link selector
                    if ( !matchesPrimarySelector(ctx, identity, primaryLink, primaryTier) ) {
                        // no match on primary... move on...
                        continue;
                    }
                    
                    List<Link> tierLinks = new ArrayList<Link>();
                    List<Tier> tiers = compositeDef.getTiers();
                    if ( tiers == null ) 
                        throw new GeneralException("Logical definition does not contain any tier defintions.");
                    
                    int totalNumTiersMinusPrimary = Util.size(tiers) - 1 ;
                    int numTierMatches = 0;
                    
                    // Iterate over the tiers defined on the logical config, if one
                    // of the defined tiers is not found consider it not matched.
                    // 
                    // This causes an ANDing of the tiers, there has been some
                    // talk about allowing the Or'ing of the tiers but that's
                    // harder in general because it's not "reversible"
                    //
                    for (CompositeDefinition.Tier tier : tiers){                        
                        // If primary igore, we've already matched on that above...
                        if ( Util.nullSafeCompareTo(primary.getName(), tier.getApplication()) != 0 )  {
                            List<Link> links = findTierLinks(ctx, identity, primaryLink, tier);
                            if ( ( Util.size(links) != 0 )  &&
                                 ( matchesSelector(identity, primary, tier) ) ) {
                                numTierMatches++;                                
                            }
                            tierLinks.addAll(links);
                        }
                    }

                    // if all tiers were matched, add the new composite to the logical link
                    if ( numTierMatches ==  totalNumTiersMinusPrimary ){
                        newLogicalLinks.add(createLogicalLink(primaryLink, tierLinks));
                    }
                }
            }
        }
        return newLogicalLinks;
    }

    /**
     * When testing a primary link and the selector mock up an identity with a single
     * Link to see if the selector matches.  Clone the entire identity, clear
     * the links the assign a clone of the primaryLink.
     * 
     * Make sure this identity or the Link is note saved or associated with 
     * a hibernate session. 
     * 
     * @param ctx
     * @param identity
     * @param primaryLink
     * @param tier
     * @return
     * @throws GeneralException
     */
    private boolean matchesPrimarySelector(SailPointContext ctx, Identity identity, 
                                           Link primaryLink, CompositeDefinition.Tier tier) 
        throws GeneralException {

        boolean matches = false;
        if ( tier == null ) {
            log.error("Tier definition cannot be null when matching users to logical applicatoin using a selector.");
            throw new GeneralException("Tier cannot be null when matching users to logical app using a selector.");
        }
        if ( primaryLink == null  ) {
            log.error("Link from primary cannot be null. skipping...");
            return false;
        }
        IdentitySelector selector = tier.getIdentitySelector();
        if ( selector != null ) {
            if ( _matcher == null ) {
                log.error("Configuration problem, matcher is null");
                throw new GeneralException("Configuration problem, matcher is null.");
            } 
            else {
                // Clone the identity here because we care about the multi-account
                // scenario where we might have more then one primary account                
                Identity clonedIdentity = (Identity)XMLObjectFactory.getInstance().clone(identity, ctx);
                if ( clonedIdentity == null )
                    throw new GeneralException("Unable to clone identity to check primary match.");                
                // remove all links except a clone of the primary
                clonedIdentity.setLinks(null);                
                Link clone = (Link)XMLObjectFactory.getInstance().cloneWithoutId(primaryLink, ctx);
                if ( clone == null ) {
                    throw new GeneralException("Unable to clone primary Link!");
                }
                clonedIdentity.add(clone);
                if ( _matcher.isMatch( selector, clonedIdentity) ) {
                    matches = true;
                }
                // just to be safe remove the link from our transient 
                // identity
                clonedIdentity.remove(clone);
            }
        }        
        return matches;
    }
    
    /**
     * Returns true if the the Identity matches to the Tier Selector. 
     */
    private boolean matchesSelector(Identity identity, Application application, 
                                    CompositeDefinition.Tier tier) 
        throws GeneralException {

        if ( tier == null ) {
            log.error("Tier cannot be null when matching users to logical applicatoin using a selector.");
            throw new GeneralException("Tier cannot be null when matching users to logical app using a selector.");
        }

        IdentitySelector selector = tier.getIdentitySelector();
        if ( selector != null ) {
            if ( _matcher == null ) {
                log.error("Configuration problem, matcher is null");
                throw new GeneralException("Configuration problem, matcher is null.");
            }
            if ( !_matcher.isMatch( selector, identity) ) {
                if ( log.isDebugEnabled() ) 
                    log.debug("[" + identity.getName() + "] did not match.");
                
                return false;
            }
            List<IdentityItem> items = _matcher.getLastMatchItems();
            if ( items != null ) {
                if ( log.isDebugEnabled() ) 
                    log.debug("[" + identity.getName() + "] MATCHED on [" + 
                              XMLObjectFactory.getInstance().toXml(items)+"]");               
            }
        }
        return true;
    }

    /**
     * Given all of the Links that make up the tierLinks and the primary
     * Link from an Identity create a single "Logical" Link.
     * 
     * @param primaryLink
     * @param tierLinks
     * @return
     * @throws GeneralException
     */
    private Link createLogicalLink(Link primaryLink, List<Link> tierLinks)
            throws GeneralException {

        Link newLogicalLink = new Link();

        newLogicalLink.setApplication(getApplication());
        
        List<Link> allTiers = new ArrayList<Link>();
        if ( tierLinks != null )
            allTiers.addAll(tierLinks);
        if (primaryLink != null )
            allTiers.add(primaryLink);        

        // copy attribute values that have been marked as having a source from the
        // tier to the composite link. Since we may have multiple tier links, one
        // link may overwrite the value from another.
        for( Link link :allTiers ) {
            newLogicalLink.addComponent(link);
            applyLinkToLogical(newLogicalLink, link);
        }
        // Now copy attributes from the primary, so if there's a collision with an attribute
        // from another tier, the primary takes precedence.
        // TODO: djs is this necessary order is implied by allTiers list.
        applyLinkToLogical(newLogicalLink,primaryLink);
        
        // 
        // Assign the native identity and display name based on
        // schema definition
        //
        Application app = getApplication();
        if ( app != null ) {
            Schema schema = app.getSchema(Connector.TYPE_ACCOUNT);
            if ( schema != null ) {                
                String identityAttribute = schema.getIdentityAttribute();
                if ( identityAttribute != null ) {
                    AttributeDefinition def = schema.getAttributeDefinition(identityAttribute);
                    String identity = getStringTierAttribute(def, allTiers);
                    if ( identity != null ) {
                        newLogicalLink.setNativeIdentity(identity);       
                    }
                }
                String displayAttribute = schema.getDisplayAttribute();
                if ( displayAttribute != null ) { 
                    AttributeDefinition def = schema.getAttributeDefinition(displayAttribute);
                    String displayName = getStringTierAttribute(def, allTiers);
                    if ( displayName != null ) {
                        newLogicalLink.setDisplayName(displayName);       
                    }
                }  

            }        
        }
        // Set default values for identity, display name from the primary link if they 
        // haven't been configured
        if ( newLogicalLink.getNativeIdentity() == null ) { 
            newLogicalLink.setNativeIdentity(primaryLink.getNativeIdentity());
        }
        if ( newLogicalLink.getDisplayName() == null ) {
            newLogicalLink.setDisplayName(primaryLink.getDisplayName());
        }
        return newLogicalLink;
    }
    
    /** 
     * Resolve the attribute from the tier and cast its value to a 
     * String.
     * 
     * @param def
     * @param links
     * @return
     */
    private String getStringTierAttribute(AttributeDefinition def, List<Link> links) {
        if ( def == null ) return null;
        
        String sourceApp = def.getCompositeSourceApplication();
        String sourceAttribute = def.getCompositeSourceAttribute();
        
        String strVal = null;
        if ( sourceApp != null && sourceAttribute != null ) {
            Object val = resolveTierValue(links, sourceApp, sourceAttribute);
            if ( val != null ) {
                strVal = Util.getString(val.toString());
            }
        }        
        return strVal;
    }
    
    /**
     * Dig through the tier list finding the link for appName
     * and a non-null value for the attributeName.
     * 
     * Returns null if it could not be resolved.
     * 
     * @param links
     * @param appName
     * @param attributeName
     * @return
     */
    private Object resolveTierValue(List<Link> links, String appName, String attributeName) {
        Object val = null;
        if ( links != null ) {
            for ( Link link : links ) {
                if ( link != null ) {
                    if ( link.getApplication().getName().compareTo(appName) == 0 ) {
                        val = link.getAttribute(attributeName);
                        if ( val != null) 
                            return val;
                    }
                }
            }
        }
        return val;
    }

    /**
     * Apply the tier link to the newly created logical link. Take into effect the
     * attribute list that has been created along with filtering of the entitlements
     * based on the logical's application.
     *
     * Permissions are a bit odd here since we only have one attribute 'directPermissions'
     * for each of the applications.  Assume targets are unique and just merge the
     * permissions together.
     * 
     * djs: why not just compute it based on the links, what's the purpose of 
     * attributeSources.
     * 
     */
    @SuppressWarnings("unchecked")
    private void applyLinkToLogical(Link logicalLink, Link tierLink) throws GeneralException {
        Application app = logicalLink.getApplication();        
        Schema accountSchema = app.getAccountSchema();
        if ( accountSchema != null ) {
            Map<String, String> srcs = attributeSources.get(tierLink.getApplicationName());
            if ( !Util.isEmpty(srcs) ) {
                for (String tierAttr : srcs.keySet() ){
                    Object val = tierLink.getAttribute(tierAttr);
                    if ( val != null ) {
                        String logicalAttributeName = srcs.get(tierAttr);
                        if  ( logicalAttributeName != null ) {
                            AttributeDefinition def = accountSchema.getAttributeDefinition(logicalAttributeName);
                            if ( def.isEntitlement() ) {
                                Object filteredValue = filterEntitlementValues(app, def, val);
                                if ( filteredValue != null ) 
                                    logicalLink.setAttribute(logicalAttributeName, filteredValue);
                                else 
                                    logicalLink.setAttribute(ATTR_DIRECT_PERMISSIONS, null);
                            } else {
                                logicalLink.setAttribute(logicalAttributeName, val);
                            }
                        }                
                    }               
                }
            }            
            // If the logical application indicates to include permissions merge the permissions from the tiers
            if ( accountSchema.getIncludePermissions() ) {
                List<Permission> tierList = (List<Permission>)tierLink.getAttribute(Connector.ATTR_DIRECT_PERMISSIONS); 
                if ( Util.size(tierList) > 0 ) {                    
                    List<Permission> perms = (List<Permission>)logicalLink.getAttribute(ATTR_DIRECT_PERMISSIONS);
                    if ( Util.size(perms) == 0 ) {
                        logicalLink.setAttribute(ATTR_DIRECT_PERMISSIONS, tierList);
                    } else {
                        // merge these together
                        List<Permission> mergedList = mergePermissions(perms, tierList);
                        mergedList = filterPermissions(app, mergedList);
                        if ( Util.size(mergedList) > 0  ) 
                            logicalLink.setAttribute(ATTR_DIRECT_PERMISSIONS, mergedList);
                        else
                            logicalLink.setAttribute(ATTR_DIRECT_PERMISSIONS, null);
                    }
                }                
            }            
            
        }
    }

    /**
     * Use the Managed Attribute definitions to drive what to include in the 
     * merged list of Permissions.
     * 
     * Permissions are modeled incorrectly in the table so we can model rights
     * for now.
     * jsl - what does the previous comment mean, still relevant?
     */
    private List<Permission> filterPermissions(Application app, List<Permission> perms) throws GeneralException {
        // TODO: come back to this since this doesn't work because permissions are screwed up
        // BUG#6753
        if ( getBooleanAttribute(CONFIG_DISABLE_LINK_FILTERING) || 
             getBooleanAttribute(CONFIG_DISABLE_PERMISSION_FILTERING) ) 
            return perms;

        List<Permission> filteredList = new ArrayList<Permission>();
        if (  Util.size(perms) > 0 ) {   
            for ( Permission perm  : perms ) {
                String target = perm.getTarget();
                List<String> neuRights = new ArrayList<String>();
                List<String> rights = perm.getRightsList();
                for ( String right : rights ) {
                    // The target as name and the rights are represented in the value
                    // jsl - this is proabbly the source of the "screwd up" comments above,
                    // the ManagedAttribute table does not have one row per right
                    if (Explanator.get(app, target) != null) {
                        neuRights.add(right);
                    }
                }
                if (neuRights.size() > 0 ) {
                    Permission neuPerm = new Permission(perm);
                    neuPerm.setRights(Util.listToCsv(neuRights));
                    filteredList.add(neuPerm);
                }                
            }            
        }        
        return ( filteredList.size() > 0 ) ? filteredList : null;
    }

    /**
     * Filter the values of an entitlement based on the managedAttribute for 
     * the composite application.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object filterEntitlementValues(Application app, AttributeDefinition def, Object val ) 
        throws GeneralException {

        
        if ( getBooleanAttribute(CONFIG_DISABLE_LINK_FILTERING) || 
             getBooleanAttribute(CONFIG_DISABLE_ENTITLEMENT_FILTERING) ) 
            return val;

        if ( val instanceof String ) {
            String strVal = (String)val;
            boolean entitlementFound = (Explanator.get(app, def.getInternalOrName(), strVal) != null);
            if ( !entitlementFound ) {
                strVal = null;
            }
            return strVal;
        } else 
        if (  val instanceof List ) {
            List listVal = Util.asList(val);
            if ( Util.size(listVal) > 0 ) {
                List neuList = new ArrayList(listVal);
                for ( Object o : listVal ) {
                    String str = o.toString();
                    boolean entitlementFound = (Explanator.get(app, def.getInternalOrName(), str) != null);
                    if ( !entitlementFound ) {
                        neuList.remove(str);
                    }
                }
                return ( Util.size(neuList) == 0 ) ? null : neuList;
            }
        }
        return val;
    }

    /**
     * Checks the given identity to determine if there are any links for the given tier
     * which correlate to the identitie's primary tier link.
     *
     * If there is a correlation rule define on the tier its used to determine
     * correlation to the primary tier. If a rule is not defined, we attempt
     * to use the defined correlation map defined on the tier. If both are
     * null having an account on the tier will be enough correlate.
     *
     * @param identity
     * @param primaryLink
     * @param tier
     * @return
     * @throws GeneralException
     */
    private List<Link> findTierLinks(SailPointContext ctx, Identity identity, Link primaryLink, CompositeDefinition.Tier tier)
            throws GeneralException, ConnectorException{

        List<Link> foundLinks = new ArrayList<Link>();

        Application tierApp = ctx.getObjectByName(Application.class, tier.getApplication());
        if (tierApp == null){
            if (log.isErrorEnabled())
                log.error("Could not find application " + tier.getApplication());
            
            return null;
        }

        if (tier.hasCorrelationRule()){
            foundLinks = runTierCorrelationRule(ctx, primaryLink, tierApp, tier.getCorrelationRule(), identity);
            return foundLinks;
        }
        
        Map<String,String> correlationMap = tier.getCorrelationMap();
        // get links from both the tier and primary app. Quit if there are no links on either side
        IdentityService identitySvc = new IdentityService(ctx);
        List<Link> tierLinks = identitySvc.getLinks(identity, tierApp);
        if ( Util.isEmpty(correlationMap) ) {
            // djs: allow for "no correlation" where just having an account is ok
            if ( Util.size(tierLinks) > 0 ) {
                foundLinks.addAll(tierLinks);
            }
        } else
        if (Util.size(tierLinks) > 0 ) {
            for (Link tierLink : tierLinks){
                if (correlateLinks(primaryLink, tierLink, tier.getCorrelationMap()))
                    foundLinks.add(tierLink);
            }
        }
        return foundLinks;
    }

    /**
     * Compares the attributes on the primary tier link and another tier link to determine
     * whether there is a match. The attributes to compare are specified in the correlationMap
     * parameter.
     *
     * @param primaryLink
     * @param tierLink
     * @param correlationMap Map of attribute names, key is the tier attribute, value is the primary.
     * @return
     */
    private boolean correlateLinks(Link primaryLink, Link tierLink, Map<String, String> correlationMap){

        if (primaryLink == null || tierLink == null)
            return false;

        for(String tierAttrName : correlationMap.keySet()){
            Object tierAttrVal = tierLink.getAttribute(tierAttrName);

            String primaryAttrName = correlationMap.get(tierAttrName);
            Object primaryAttrVal = primaryLink.getAttribute(primaryAttrName);

            if (tierAttrVal == null || !tierAttrVal.equals(primaryAttrVal))
                return false;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Logical Provisioning 
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * If there is a rule defined run it, otherwise try and reverse the plan
     * using the tier configurations.
     */
    public ProvisioningPlan compileProvisioningPlan(SailPointContext ctx,
                                                    Identity identity,
                                                    ProvisioningPlan src)
        throws GeneralException {

        ProvisioningPlan reswizled = null;
        Application app = getApplication();
        CompositeDefinition def = app.getCompositeDefinition();
        String ruleName = def != null ? def.getRemediationRule() : null;
        if (Util.getString(ruleName) != null) {
            Rule rule = ctx.getObject(Rule.class, ruleName);            
            if (rule == null) {
                log.error("Error converting incomming logical plan.");
                throw new GeneralException("Invalid provisioning plan transformation rule:" + ruleName + " specified on logical app: " + app.getName());
            }
            else {
                HashMap<String,Object> args = new HashMap<String,Object>();
                args.put("application", getApplication());
                args.put("identity", identity);
                args.put("plan", src);
                Object result = ctx.runRule(rule, args);
                // if this returns null, should we return null?
                if (result instanceof ProvisioningPlan)
                    reswizled = (ProvisioningPlan)result;
            }
        } else {
            try {
                reswizled = reswizzlePlan(ctx, identity, src);
            } catch(ConnectorException e) {
                if (log.isErrorEnabled())
                    log.error("Error converting incomming logical plan." + e.getMessage(), e);
                
                throw new GeneralException(e);
            }
        }
        return reswizled;
    }
    
    /**
     * The plan here should come in with reference to the Logical application.
     * Use the schema to convert it to one or more AccountRequests for 
     * each of the "real" tier applications.
     * 
     * This method will try and compute the tier plans based on the
     * Incoming plan AND the logical application's schema.
     * 
     * For modify requests, we simply map the logical application schema
     * back to the tiers and perform and transform the AttributeRequests
     * into requests for the tier applications.
     *
     * @Ignore 
     * NOTE: Making this public in case we want to tweak the behavior
     * and allow rules to call back to the connector to reswizzle 
     * what it can and allow the rule to go further.
     * 
     */
    public ProvisioningPlan reswizzlePlan(SailPointContext ctx, Identity identity, ProvisioningPlan incommingPlan) 
        throws GeneralException, ConnectorException {

        if ( incommingPlan != null ) {
            if (log.isDebugEnabled())
                log.debug("Original Plan : " + incommingPlan.toXml());
        }

        ProvisioningPlan reswizzledPlan = new ProvisioningPlan();
        List<AccountRequest> requests = incommingPlan.getAccountRequests();
        if ( Util.size(requests) > 0 ) {
            Schema schema = getSchema(Connector.TYPE_ACCOUNT);
            for ( AccountRequest req : requests ) {
                if ( req == null ) {
                    log.debug("An Account request was null ignoring...");
                    continue;
                }
                ProvisioningPlan.AccountRequest.Operation requestOp = req.getOperation();
                if ( requestOp.equals(AccountRequest.Operation.Delete) ) {
                    if ( !getBooleanAttribute(CONFIG_DELETE_REMOVES_ENTITLEMENTS) ) {
                        if (log.isDebugEnabled())
                            log.debug(CONFIG_DELETE_REMOVES_ENTITLEMENTS + 
                                      " was not enabled, Remove request ignored.");
                        
                        // stick back the original account request
                        reswizzledPlan.add(req);
                        continue;
                    } else {
                        // roll back all entitlements from the tier that are defined on 
                        // the logical link
                        ProvisioningPlan convertedPlan = requestAllTierEntitlementRemoved(ctx, identity, schema, req);
                        if ( convertedPlan != null ) {
                            reswizzledPlan.merge(convertedPlan);
                        }
                    }
                } else 
                if ( ( requestOp.equals(AccountRequest.Operation.Modify ) ) ||
                     ( requestOp.equals(AccountRequest.Operation.Create ) ) ) {
                    ProvisioningPlan convertedPlan = convertCreateOrModifyRequest(ctx, identity, req, schema);
                    if ( convertedPlan != null )
                        reswizzledPlan.merge(convertedPlan);
                } else {
                    //
                    // Enable/Disable or Unlock requests
                    //
                    ProvisioningPlan convertedPlan = convertAccountStateChangeRequest(ctx, identity, req); 
                    if ( convertedPlan != null ) 
                        reswizzledPlan.merge(convertedPlan);
                }
            }

        } else {
           log.debug("There were no account requests in the plan to reswizzle.");
        }

        if ( reswizzledPlan!= null ) {
            if (log.isDebugEnabled())
                log.debug("Converted Plan : " + reswizzledPlan.toXml());
        }

        return ( Util.size(reswizzledPlan.getAccountRequests()) == 0 ) ? null : reswizzledPlan;
    }
    
    /**
     * Fan out an incoming request and fan it out to target the tiers.
     * 
     * @param ctx The context.
     * @param identity The identity.
     * @param logicalRequest The account request for the logical application.
     * @param schema The schema.
     * @return The plan.
     * @throws GeneralException
     */
    private ProvisioningPlan convertCreateOrModifyRequest(SailPointContext ctx, Identity identity, 
                                                          AccountRequest logicalRequest, Schema schema) 
        throws GeneralException {
        
        ProvisioningPlan plan = new ProvisioningPlan();
        
        List<AttributeRequest> attrReqs =  logicalRequest.getAttributeRequests();
        if ( Util.size(attrReqs) > 0 ) {
            for ( AttributeRequest areq : attrReqs ) {
                String name = areq.getName();
                AttributeDefinition def = schema.getAttributeDefinition(name);
                if ( def == null ) {
                    throw new GeneralException("While attempting to convert attribute request for logical application an error was encountered. " 
                            +"Unable to find attribute named["+name+"] on the logical application["+getApplication().getName()+"] account schema.");
                }
                AttributeRequest converted = convertAttributeRequest(def, areq);
                if ( converted != null ) {
                    String sourceApp = def.getCompositeSourceApplication();
                    if ( sourceApp == null)
                        sourceApp = getApplication().getName();
                    AccountRequest req = null;
                    List<AccountRequest> reqs = plan.getAccountRequests(sourceApp);
                    
                    // There should be 0 or 1 request for the source app.
                    // Create one if we don't have it yet.
                    if ( Util.isEmpty(reqs) ) {
                        req = initAccountRequest(ctx, identity, logicalRequest, sourceApp);
                        plan.add(req);
                    }
                    else {
                        // Assume there is just one.
                        req = reqs.get(0);
                    }

                    req.add(converted);
                }
            }
        }
        
        List<PermissionRequest> permRequests = logicalRequest.getPermissionRequests();
        if (Util.size(permRequests) > 0) {
        	// get the tier links for the logical application
        	List<Link> tierLinks = getTierLinks(ctx, identity, logicalRequest.getApplication(),
                    logicalRequest.getNativeIdentity());
        	
        	// keep track of the permission requests not found
        	List<PermissionRequest> permRequestsNotFound = new ArrayList<PermissionRequest>(permRequests);
        	
        	for (PermissionRequest permRequest : permRequests) {
        		String targetToFind = permRequest.getTarget();
        		
        		// find the link with permission containing the target
        		for (Link link : tierLinks) {
        			Permission permWithTarget = link.getSinglePermission(targetToFind);
        			if (permWithTarget != null) {
        				// ensure we have an AccountRequest for the link
        				AccountRequest acctRequest = getAccountRequestInPlanForLink(plan, link, ctx, identity, logicalRequest);
        				
        				// we found the link permission with the target so add it to account request's permission requests
        				List<PermissionRequest> acctRequestPermRequests = acctRequest.getPermissionRequests();
        				if (acctRequestPermRequests == null) {
        					acctRequest.setPermissionRequests(new ArrayList<PermissionRequest>());	
        				}
        				
        				acctRequest.getPermissionRequests().add(permRequest);
        				
        				// remove the permission request from the not found list
        				permRequestsNotFound.remove(permRequest);
        				
    					break;
        			}
        		}
        	}
        	
        	// do our best to handle permission requests that were not found by finding the
        	// first tier that supports direct permissions and assigning the requests to it
        	if (Util.size(permRequestsNotFound) > 0) {
	        	for (Link link : tierLinks) {
	        		if (link.getApplication().getAccountSchema().includePermissions()) {
	        			AccountRequest acctRequest = getAccountRequestInPlanForLink(plan, link, ctx, identity, logicalRequest);
	        			
	        			List<PermissionRequest> acctRequestPermRequests = acctRequest.getPermissionRequests();
	    				if (acctRequestPermRequests == null) {
	    					acctRequest.setPermissionRequests(new ArrayList<PermissionRequest>());	
	    				}
	    				
	    				acctRequest.getPermissionRequests().addAll(permRequestsNotFound);
	        			
	        			break;
	        		}
	        	}
        	}
        }
        
        return plan;
    }
    
    /**
     * Gets an account request for a in the specified provisioning plan for the specified link. If none exists in the plan, 
     * then a new account request is initialized from the logical account request.
     * @param plan The provisioning plan.
     * @param link The link.
     * @param ctx The context.
     * @param identity The identity.
     * @param logicalRequest The account request for the logical link.
     * @return The account request for the specified link.
     * @throws GeneralException
     */
    private AccountRequest getAccountRequestInPlanForLink(ProvisioningPlan plan, Link link, SailPointContext ctx, Identity identity, AccountRequest logicalRequest)
    	throws GeneralException
    {
        // Try to get a request for this specific link.
        Application app = link.getApplication();
    	AccountRequest acctRequest =
    	    plan.getAccountRequest(app.getName(), link.getInstance(), link.getNativeIdentity());

    	// If null, just look for any account request for this app.  There should
    	// just be one.
    	List<AccountRequest> acctReqs = plan.getAccountRequests(app.getName());
    	if (!acctReqs.isEmpty()) {
    	    acctRequest = acctReqs.get(0);
    	}
    	
    	// Still didn't find one.  Create it.
    	if (acctRequest == null) {
			// no account request found so initialize a new one and add it to the plan
			acctRequest = initAccountRequest(ctx, identity, logicalRequest, link.getApplication().getName());
			plan.add(acctRequest);
		}
		
		return acctRequest;
    }
    
    /**
     * Gets the tier links that make up the specified logical application for the specified identity.
     * @param identity The identity to search.
     * @param logicalAppName The logical application name.
     * @return The tier links.
     */
    private List<Link> getTierLinks(SailPointContext ctx, Identity identity, String logicalAppName,
                                    String logicalNativeIdentifier) throws GeneralException
    {
    	Link composite = null;

    	List<Link> result = new ArrayList<Link>();

        // find the specified logical link
        QueryOptions ops = new QueryOptions(Filter.eq("identity", identity));
        ops.add(Filter.eq("application.name", logicalAppName));
        ops.add(Filter.eq("nativeIdentity", logicalNativeIdentifier));

        List<Link> identityLinks = ctx.getObjects(Link.class, ops);
    	if (!identityLinks.isEmpty())
            composite = identityLinks.get(0);

    	// if the logical link was found aggregate the tiers
    	if (composite != null) {
    		List<String> componentIds = Util.csvToList(composite.getComponentIds());
    		for (Link link : identityLinks) {
    			for (String componentId : componentIds) {
    				if (Util.nullSafeEq(link.getId(), componentId)) {
    					result.add(link);
    					break;
    				}
    			}
    		}
    	}
    	
    	return result;
    }

    /**
     * This method, when enabled will go through the logical
     * schema and request all of the entitlement values 
     * from the schema are requested to be removed.
     */ 
    @SuppressWarnings("unchecked")
    private ProvisioningPlan requestAllTierEntitlementRemoved(SailPointContext ctx,
                                                              Identity identity, 
                                                              Schema schema, 
                                                              AccountRequest logicalRequest) 
        throws GeneralException {
                
        
        // This plan will be merged
        ProvisioningPlan plan = new ProvisioningPlan();

        Application app  = ctx.getObject(Application.class, logicalRequest.getApplication());
        if ( app == null ) {
            if (log.isErrorEnabled())
                log.error("Unable to find application [" + logicalRequest.getApplication() + "]");
            
            throw new GeneralException("Unable to find application ["+logicalRequest.getApplication()+"].");
        }
        
        String nativeIdentity = logicalRequest.getNativeIdentity();
        String instance = logicalRequest.getInstance();
        //  Find the logical link
        Link link = new Correlator(ctx).findLinkByNativeIdentity(app, instance, nativeIdentity);
        if ( link != null ) {            
            Attributes<String,Object> attrs = link.getEntitlementAttributes();            
            if ( attrs != null ) {
                Iterator<String> keys = attrs.keySet().iterator();
                while ( keys.hasNext() ) {
                    String logicalName  = keys.next();                    
                    AttributeDefinition def = schema.getAttributeDefinition(logicalName);
                    if ( def != null ) {
                        String nativeName = def.getCompositeSourceAttribute();                                            
                        String nativeApp = def.getCompositeSourceApplication();              
                        Object value = attrs.get(logicalName);  
                       
                        // this is the converted attribute request
                        AttributeRequest attrRequest = new AttributeRequest(nativeName, value);
                        attrRequest.setOperation(ProvisioningPlan.Operation.Remove);
                        
                        List<AccountRequest> acctReqs = plan.getAccountRequests(nativeApp);
                        if (!Util.isEmpty(acctReqs)) {
                            for (AccountRequest existing : acctReqs) {
                                existing.add(attrRequest);
                            }
                        }
                        else {
                            AccountRequest acctReq =
                                initAccountRequest(ctx, identity, logicalRequest, nativeApp);
                            // djs: we don't want this to trickel down to the tier as a Delete since 
                            // all we want to do here is remove the entitlement
                            acctReq.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
                            plan.add(acctReq);
                            acctReq.add(attrRequest);
                        } 
                    }
                }
            }            
        } else {
            // TODO: Should this be an exception? We want others to be able to call
            // this without exception in rules?
            if (log.isErrorEnabled())
                log.error("Could not locate link using '" + Filter.and(Filter.eq("nativeIdentity", nativeIdentity),
                          Filter.eq("application", app), Filter.eq("instance", instance))+"'.");
        }
        return plan;
    }

    /***
     * Handle the other types account requests that are asked to be provisioned.
     * These are typically requests for Enable, Disable and Unlock.
     * 
     * By default just put the original request into the Plan.
     * 
     * This supports some level of configuration.  By adding keys in 
     * this format you can "redirect" these type of requests to the
     * configured tiers.
     * 
     * The format of the configuration key is $Operation.targetTierApp
     * and the value is List of apps to be targeted during those 
     * operations.
     * 
     * e.g.  This entry would configure the connector so that
     * the "Enable" operation account requests made against
     * the Logical Application will be forwarded to the 
     * "ActiveDirectory" application.
     * 
     * <entry key='Enable.targetTierApps'>
     *   <value>
     *     <List>
     *       <String>ActiveDirectory</String>
     *     <List>
     *   </value>
     * </entry>
     * 
     * 
     * @param logicalRequest
     */   
    @SuppressWarnings("unchecked")
    private ProvisioningPlan convertAccountStateChangeRequest(SailPointContext ctx, Identity identity, 
                                                              AccountRequest logicalRequest)
        throws GeneralException {

        ProvisioningPlan plan = new ProvisioningPlan();
        if ( logicalRequest  != null ) {
            ProvisioningPlan.AccountRequest.Operation op = logicalRequest.getOperation();
            String key = op + CONFIG_TARGET_TIER_SUFFIX;
                      
            List<String> tierApps = getListAttribute(key);
            //
            // Iterate over the tiers and fan out the request
            //
            if ( Util.size(tierApps) > 0 ) {
                for ( String tier : tierApps ) {
                    AccountRequest tierRequest = initAccountRequest(ctx, identity, logicalRequest, tier);
                    plan.add(tierRequest);
                }
            } else {
                // If we don't retarget to a tier put the original 
                // back into the plan so it can be handled manually
                plan.add(logicalRequest);
            }
        }
        return plan;
    }

    /**
     * Convert the logical application's AccountRequest into 
     * one targeted at a tier.
     * 
     * This method will look up the appropriate application
     * based on the schemas source application and will create
     * a new AttributeRquest for use by the caller. 
     *
     */
    private AttributeRequest convertAttributeRequest( AttributeDefinition def, 
                                                      AttributeRequest origAttrReq) {
   
        AttributeRequest convertedRequest = new AttributeRequest(origAttrReq); 
        if ( def != null ) {           
            // use the existing request as the starter since it
            // has most everything we need except possibly
            // the correct attribute name.                
            String attrName = def.getCompositeSourceAttribute();
            if  ( attrName != null ) {                
                convertedRequest.setName(attrName);
            }             
        }    
        return convertedRequest;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Connector Interface
    //
    ///////////////////////////////////////////////////////////////////////////

    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }
    
    /**
     * This method should return a List of Feature object that describe the
     * optional Features that this connector may publish.
     */
    public List<Application.Feature> getSupportedFeatures() {

        List<Application.Feature> features = new ArrayList<Application.Feature>();
        features.add(Application.Feature.COMPOSITE);
        features.add(Application.Feature.DISCOVER_SCHEMA);
        return features;
    }

    /**
     * This ensures that the composite has a definition which includes a
     * list of tiers, with one tier marked as the primary tier.
     */
    public void testConfiguration()
        throws ConnectionFailedException, ConnectorException  {

        CompositeDefinition def = getApplication().getCompositeDefinition();

        if (def == null)
            throw new ConnectorException("The logical application definition was missing.");

        if ( Util.size(def.getTiers()) == 0 ) 
            throw new ConnectorException("The logical does not contain any tier applications.");

        if (def.getPrimaryTier() == null)
            throw new ConnectorException("The logical's primary tier application has not been defined.");

    }

    /**
     * Read the tier configuration data and attempt to discover the schema
     * using the underlying tier applications.
     * Since this is typically called from the ui tier when building new
     * Application objects Use the SailPointFactory to get the
     * current context.
     */
    @Override   
    public Schema discoverSchema(String objectType, Map<String,Object> options) 
        throws ConnectorException {

        Schema schema = null;        
        CompositeDefinition definition = getApplication().getCompositeDefinition();
        if ( definition != null ) {     
            List<Tier> tiers = definition.getTiers();
            if ( Util.size(tiers) > 0 ) {
                for ( Tier tier : tiers ) {
                    String tierApp = tier.getApplication();
                    try {
                        SailPointContext ctx = SailPointFactory.getCurrentContext();
                        Application app = ctx.getObject(Application.class, tierApp);
                        if ( app != null ) { 
                            Schema objectSchema = app.getSchema(objectType);
                            if ( schema == null ){
                                Schema clone = (Schema)XMLObjectFactory.getInstance().cloneWithoutId(objectSchema, ctx);
                                schema = new Schema(objectType, objectType);
                                List<AttributeDefinition> defs = clone.getAttributes();
                                if ( Util.size(defs) > 0 ) {
                                    for ( AttributeDefinition def : defs ) {
                                        def.setCompositeSource(tierApp, def.getInternalOrName());
                                    }
                                }
                                schema.setAttributes(clone.getAttributes());                            
                            } else {
                                mergeSchemas(ctx, schema, objectSchema, tierApp);
                            }
                        }
                    } catch(GeneralException e ) {
                        if (log.isErrorEnabled())
                            log.error("Error generating the schema." + e.getMessage(), e);
                        
                        throw new ConnectorException("Problem generating schema." + e);
                    }     
                }
            }
        }             
        return schema;
    }
    

    ///////////////////////////////////////////////////////////////////////////
    //
    // Deprecated Method - Please use alternatives 
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * This method is depreciated in favor of the version that takes
     * an identity.  We need the identity to target the
     * correct Links when fanning out the request to the different
     * tiers. 
     * 
     * @See reswizzlePlan(SailPointContext ctx, Identity identity, ProvisioningPlan incommingPlan);
     */
    @Deprecated
    public ProvisioningPlan reswizlePlan(SailPointContext ctx, ProvisioningPlan incommingPlan) 
        throws GeneralException, ConnectorException {
        return reswizzlePlan(ctx, null, incommingPlan);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    ///////////////////////////////////////////////////////////////////////////
        
    /**
     * Resolve the tier account nativeIdentity and instance information
     * and adorn it to the account request.
     * 
     *  1) Try first by drilling into the logical link and see if we can
     *     use the components.
     *     
     *  2) If that fails try to use the identity links, but there are compilcations
     *     if there is more then one account on the tier app. 
     * 
     */
    private void resolveTierAccountInfo(SailPointContext ctx, Identity identity, AccountRequest tierRequest ) 
        throws GeneralException    {

        String nativeIdentity = null; 
        String instance = null;

        String sourceApp = tierRequest.getApplication();
        // 
        // Try and figure out which account we need to map
        // to when directing the provisioning request

        //
        // 1 ) try to use the component ids stored on the logical
        //     application. This one requires we have the logical
        //     link already and is dependent on the default implementation
        //     or the rule calling setComponentIds on the logical
        //     link when it's created.  Seems reasonable...
        //
        Application logicalApp = getApplication();                            
        Link logicalLink  = identity.getLink(logicalApp);
        if ( logicalLink != null ) {
            String logicalComponentsCsv = logicalLink.getComponentIds();
            if ( logicalComponentsCsv != null ) {
                List<String> ids = Util.csvToList(logicalComponentsCsv);
                if ( ids != null ) {
                    for ( String id : ids ) {
                        Link component = findLinkById(ctx, identity, id);
                        if ( component != null ) {
                            if ( Util.nullSafeCompareTo(component.getApplicationName(), sourceApp) == 0 ) {
                                nativeIdentity = component.getNativeIdentity();
                                instance = component.getInstance();
                                break;
                            }
                        }
                    }
                }
            }
        }

        //
        // Inspect the tiers for two reasons:
        // 1.  If we don't yet have a component try and see
        //     if the we have a single link on the tier and use
        //     that native identity, otherwise log a message
        // 2.  If we are attempting to create a tier account for
        //     one that already exists then modify it instead
        //
        Operation tierOperation = tierRequest.getOperation();
        boolean tryToFixNativeIdentity = nativeIdentity == null; 
        if ( tryToFixNativeIdentity || tierOperation == Operation.Create) {
            Application sourceAppObject = ctx.getObject(Application.class, sourceApp);
            if ( sourceAppObject != null ) {
                IdentityService identitySvc = new IdentityService(ctx);
                List<Link> tierLinks = identitySvc.getLinks(identity, sourceAppObject);
                if ( tierLinks != null ) {
                    if ( tierLinks.size() > 0 ) {
                        // At this point we know we have an account on the tier already.  Amend the request
                        // for a new account to a modification of the existing one - Bug #23788
                        if (tierOperation == Operation.Create) {
                            tierRequest.setOperation(Operation.Modify);
                        }

                        if (tryToFixNativeIdentity) {
                            Link tierLink = tierLinks.get(0);
                            if ( tierLink != null ) {
                                nativeIdentity = tierLink.getNativeIdentity();
                                instance = tierLink.getInstance();
                            }
                        }
                    } 
                    if ( tierLinks.size() > 1 && tryToFixNativeIdentity) {
                        if (log.isDebugEnabled())
                            log.debug("More then one account found for source application [" +
                                      sourceApp + "] Defaulting to the first link.");
                    }
                }
            }
        }

        if ( nativeIdentity == null )
            nativeIdentity = tierRequest.getNativeIdentity();
        if ( instance == null )
            instance = tierRequest.getInstance();

        tierRequest.setNativeIdentity(nativeIdentity);
        tierRequest.setInstance(instance);
    }


    private void mergeSchemas(SailPointContext ctx, Schema s1, Schema s2, String appName ) 
        throws GeneralException {

        if ( s1 == null ) {
            s1 = s2; 
        } else {
            if ( s2 != null ) {
                Schema clone = (Schema)XMLObjectFactory.getInstance().cloneWithoutId(s2, ctx);
                List<AttributeDefinition> defs = clone.getAttributes();
                if ( Util.size(defs) > 0 ) {
                    for ( AttributeDefinition def : defs ) {
                        AttributeDefinition existing = s1.getAttributeDefinition((def.getInternalOrName()));
                        // capture this before we potentially rename it
                        def.setCompositeSource(appName, def.getInternalOrName());
                        if ( existing != null ) {
                            // rename it
                            def.setName(appName+"."+existing.getInternalOrName());
                            
                        }
                        s1.addAttributeDefinition(def);                        
                    }
                }
            }            
        }
    }

    /**
     * Executes a rule which analyzes the identity and returns any composite links.
     * This rule is called only when there is no CompositeDefintion and Tier 
     * configurations.  Its responsible for everything including matching and
     * correlation.
     *
     * This rule was the first generation of the logical connector (composite),
     * the alternative is to define CompositeDefintions which contains the 
     * Tier's and matching rules.
     *
     * @param app
     * @param identity
     * @return
     * @throws GeneralException
     * @throws ConnectorException
     */
    private List<Link> runAccountRule(SailPointContext ctx, Application app, Identity identity)
            throws GeneralException, ConnectorException {

        HashMap<String,Object> args = new HashMap<String,Object>();
        args.put("identity", identity);
        args.put("application", app);

        String ruleName = app.getCompositeDefinition().getAccountRule();

        return runRule(ctx, ruleName, args);
    }

    /** 
     * This rule is defined on each Tier when simple attribute matching isn't 
     * enough to correlate back to the Primary Tier.
     */
    private List<Link> runTierCorrelationRule(SailPointContext ctx, Link primaryLink, Application tier, String ruleName, Identity identity)
            throws GeneralException, ConnectorException {

        HashMap<String,Object> args = new HashMap<String,Object>();
        args.put("identity", identity);
        args.put("tierApplication", tier);
        args.put("primaryLink", primaryLink);

        return runRule(ctx, ruleName, args);
    }

    @SuppressWarnings("rawtypes")
    private List<Link> runRule(SailPointContext ctx, String ruleName, HashMap<String,Object> args)

        throws GeneralException, ConnectorException {

        List<Link> links = null;

        Rule rule = null;
        if (ruleName != null)
            rule = ctx.getObject(Rule.class, ruleName);

        if (rule == null) {
            if (ruleName == null) {
                log.error("Missing logical rule.");
            } else {
                if (log.isErrorEnabled())
                    log.error("Invalid logical rule: " + ruleName);
            }
            
            return null;
        }

        Object result = ctx.runRule(rule, args);
        if (result instanceof Link) {
            links = new ArrayList<Link>();
            links.add((Link)result);
        }
        else 
        if (result instanceof Collection) {
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
    
    
    /**
     * Dig through the Identity and find the link that
     * matches one of our compoentn  ids.
     * 
     * @param identity
     * @param id
     * @return
     * @throws GeneralException
     */
    private Link findLinkById(SailPointContext ctx, Identity identity,String id ) throws GeneralException {
        if ( identity != null ) {
            IdentityService svc = new IdentityService(ctx);
            return svc.getLinkById(identity, id);
        }
        return null;
    }
    
    /**
     * Initialze the tier account request with the incomming
     * logical request.  
     */
    private AccountRequest initAccountRequest(SailPointContext ctx, Identity identity, 
                                              AccountRequest logicalRequest, 
                                              String tierAppName) 
        throws GeneralException {

        AccountRequest tierRequest = new AccountRequest();
        tierRequest.cloneAccountProperties(logicalRequest);
        tierRequest.setApplication(tierAppName); 
        if ( identity != null ) {          
            resolveTierAccountInfo(ctx, identity, tierRequest);
        }
        return tierRequest;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Connector Interface methods that don't make any sense 
    // since these aren't real accounts.
    //
    ///////////////////////////////////////////////////////////////////////////

    public ResourceObject getObject(String objectType,
                                    String identity,
                                    Map<String, Object> options)

        throws ObjectNotFoundException,
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

    /**
     * This method should return a List of AttributeDefinitions that describe
     * the configuration attributes that can be used to configure the behavior
     * of a connector.  In this case we use CompositeDefinition to define
     * the behavior with a custom Logical UI.
     */
    public List<AttributeDefinition> getDefaultAttributes() {
        return new ArrayList<AttributeDefinition>();
    }

    /**
     * There are no default schemas, they must be fully defined.
     */
    public List<Schema> getDefaultSchemas() {

        List<Schema> schemaList = new ArrayList<Schema>();
        schemaList.add(new Schema(Connector.TYPE_ACCOUNT, null));
        return schemaList;
    }
}
