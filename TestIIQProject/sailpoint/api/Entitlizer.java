/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating various maintenance of the IdentityEntitlement
 * objects added in 6.0, specifically around Link entitlements that are 
 * created as part of the aggregation or link refresh process.
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.Link;
import sailpoint.object.LinkInterface;
import sailpoint.object.ManagedAttribute.Type;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleDetection;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * An API class that handles the create/update/delete of 
 * IdentityEntitlement objects as they pertain to
 * Link attribute assignment.
 *  
 * Mainly in charge of promoting entitlement attributes from 
 * links during the aggregation and also optionally during the
 * refresh process.
 * 
 * There is no optimization code here, we always fetch and
 * update all of the entitlement on a link. Up front we
 * query for all of the IdentityEntitlement for the identity
 * on a given app and stores them in a map keyed by
 * the attribute name and attribute value concatenated together.
 *       
 * This class delegates the promotion of detected and assigned 
 * roles the RoleEntitlizer. 
 *      
 * @See {@link AbstractEntitlizer}
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * @since 6.0
 *
 */
public class Entitlizer extends AbstractEntitlizer {
    
    private static Log log = LogFactory.getLog(Entitlizer.class);
    
    /**
     * Flag that forces the entitlements to be refreshed.
     */
    public static final String ARG_FORCE_IDENTITY_ENTITLEMENT_REFRESH = 
        "forceIdentityEntitlementPromotion";
    
    /**
     * Flag that will prevent an extra check that is done for each
     * Identity when ARG_REFRESH_IDENITY_ENTITLEMENTS is enabled
     * and going over all of the user's links.  The extra check
     * walks over the entitlements and make sure there aren't 
     * any entitlements missing source Links.
     * M
     * Must be used with ARG_REFRESH_IDENTITY_ENTITLEMENTS.
     */
    public static final String ARG_DISABLE_IDENTITY_ENTITLEMENT_CHECK = 
        "disableIdentityEntitlementOrphanCheck";

    /**
     * Flag to indicate if we should be disable promoting IdentityEntitlments
     * as we aggregateLinks or refreshing an Identity.  This is
     * typically specified during aggregation so we get "group"
     * membership immediately.  It can also be added to the arg
     * list during refresh with our without the 
     * ARG_REFRESH_IDENTITY_ENTITLEMENTS.
     */
    public static final String ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION = 
        "disableIdentityEntitlementPromotion";

    /**
     * Flag to indicate if we should be refreshing IdentityEntitlments
     * as we aggregateLinks or refreshing an Identity. On its own this
     * flag will enable assigned and detectedRoles be pushed into
     * the IdentityEntitlement table during refresh.  If in the arg
     * list together with ARG_PROMOTE_IDENTITY_ENTITLEMENTS then
     * it'll also go through all of identity links and promote
     * the entitlement attributes.
     */
    public static final String ARG_REFRESH_IDENTITY_ENTITLEMENTS = 
        "refreshIdentityEntitlements";
    
    /**
     * Argument put into the result ( if saveResults ) is called to indicate
     * how many entitlements were created.
     */
    public static final String RET_CREATED = "identityEntitlementsCreated";
    
    /**
     * Argument put into the result ( if saveResults ) is called to indicate
     * how man entitlements were removed because they were not found in
     * the feed.
     */
    public static final String RET_REMOVED = "identityEntitlementsRemoved";
    
    /**
     * Argument added to the result during saveResults() to indicate how 
     * many identity entitlements were visited and likely updated. 
     */
    public static final String RET_UPDATED = "identityEntitlementsUpdated";

    /**
     * We try and do entitlement promotion during link promotion when possible.
     * Keep a separate counter in the result so we can figure out how man are updated
     * here, vs during role promotion.
     */
    private static final String RET_INDIRECT_DURING_LINK = "identityEntitlementsIndirectLinkUpdates";
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Feilds
    //    
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Flag that will drive a total refresh of all the
     * identity links.
     */
    private boolean _refreshIdentityEntitlements;

    /**
     * Flag that can be set to force the promotion of the entitlements
     * regardless of the differences we detect.
     */
    private boolean _forcePromotion;
    
    /**
     * Flag to indicate if we should disable the promotion of entitlement 
     * attributes to IdentityEntitlement (sailpoint.object.IdentityEntitlement) 
     * objects. 
     */
    boolean _disableIdentityEntitlementPromotion;
    
    /**
     * Defer the role handing to an alternate class
     * since its distinctly different then promoting
     * link attributes.
     */
    private RoleEntitlizer _roleEntitlizer;    
    
    /**
     * Store off the CorrelationModel, typically comes in from the 
     * Identitizer which sets the Model stored on the EntitlementCorrelator. This is 
     * only used during Role IdentityEntitlement promotion and initializing
     * this can be expensive.
     */
    private CorrelationModel _correlationModel;

    
    /**
     * Mapping between the entitlement and the role data associated with the 
     * entitlement.  This model is built using the entitlement correlators
     * entitlement mapping.
     */
    Map<String, RoleData> roleDataMap;
    
    /**
     * The entitlement mapping retrieved from the entitlement correlator. 
     */
    Map<Bundle, List<EntitlementGroup>> _entitlementMapping;
        
    /**
     * A flag can be set to disable this explicitly.
     * 
     * Otherwise it runs everytime we process all of the Links
     * from an identity.
     * 
     */
    private boolean _disableOrphanChecks;
    
    /**
     * Flag to tell us if we are going to refresh all of the Links.
     * This is typically set by the refresh process when we are
     * refreshing all of the links.
     * 
     * This flag tells the Entitlizer to keep a list of the 
     * links that were visited to help us find orphaned 
     * entitlements after they are all visited.
     * 
     * It can only be used in cases where we are visting the
     * entire list of links for an identity.
     */
    private boolean _orphanTracking;
    
    /**
     * List of appName, nativeIdentity and instances that
     * we can use to find orphaned entitlements.
     */
    private Set<LinkInfo> _linksVisited;
    
    /**
     * Service class used by various methods.
     */
    private IdentityService _identityService;
    
    /**
     * List of IdentityEntitlements that need to be saved. 
     * Defer this as the last thing we do to avoid inserts/then updates.
     * 
     * Also this is passed into the RoleEntitlizer so it can be
     * used when promoting indirect entitlements.
     * 
     * @param context
     * @param args
     */
    List<IdentityEntitlement> _toBeSaved;
    
    /**
     * List of entitlements that were not found in the feed and will be
     * removed when the transaction is committed.  Need this so that
     * when an entitlement is marked for removal during IdentityEntitlement
     * promotion (link) we can relay the information to the RoleEntitlizer 
     * for use during indirect entitlement promotion for cases where
     * they are being done in the same transaction.
     */
    private List<IdentityEntitlement> _toBeRemoved;
    
    /* 
     * Statistics kept to help us understand what was changed.
     */
    int _totalCreated;    
    int _totalRemoved;
    int _totalUpdated;
    int _totalIndirectUpdated;
    
    public Entitlizer(SailPointContext context, Attributes<String,Object> args) {
        super(context, args);
        _totalCreated = 0;
        _totalUpdated = 0;
        _totalRemoved = 0;
        _toBeSaved = new ArrayList<IdentityEntitlement>();
        _forcePromotion = Util.getBoolean(args, ARG_FORCE_IDENTITY_ENTITLEMENT_REFRESH);
        _orphanTracking = false;
        _linksVisited = new HashSet<LinkInfo>();
        _identityService = new IdentityService(_context);
        _disableOrphanChecks = Util.getBoolean(args, ARG_DISABLE_IDENTITY_ENTITLEMENT_CHECK);
        
        if ( !_forcePromotion )
            _disableIdentityEntitlementPromotion =  Util.getBoolean(args, ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION);
        if ( !_disableIdentityEntitlementPromotion )
            _refreshIdentityEntitlements = Util.getBoolean(args, ARG_REFRESH_IDENTITY_ENTITLEMENTS);
        
        _toBeRemoved = new ArrayList<IdentityEntitlement>();
    }
     
    ///////////////////////////////////////////////////////////////////////////
    // 
    // Properties
    //
    /////////////////////////////////////////////////////////////////////////
    
    public void setForcePromotion(boolean forceIdentityEntitlementPromotion) {
        _forcePromotion = forceIdentityEntitlementPromotion;
    }
    
    public boolean isForcePromotion() {
        return _forcePromotion;
    }
    
    public void setCorrelationModel(CorrelationModel model) {
        _correlationModel = model;
    }

    
    /**
     * 
     * Using the entitlement correlation mapping, build an model that will make this 
     * data simpler to adorn as we visit each Link's entitlement.
     * 
     * This is typically only called when entitlement correlation occurs during
     * IdentityRefresh, not during "normal" aggregation of accounts.
     * 
     * The code that clearsRole data uses a non-null test on roleDataMap to 
     * indicate that we don't have RoleData to set and just dealing with
     * "base" Entitlement data.
     * 
     */
    public void setEntitlementMapping(Identity id, Map<Bundle, List<EntitlementGroup>> map)
        throws GeneralException {

        // Always set this to non-null indicating that we have the
        // roleData for assimilation onto the entitlement
        roleDataMap = new HashMap<String,RoleData>();        
        _entitlementMapping = map;
        
        Iterator<Bundle> it = null;
        if ( !Util.isEmpty(map ) ) {
            if ( map != null ) {
                Set<Bundle> keySet = map.keySet();
                if ( keySet != null ) {
                    it = keySet.iterator();
                }
            }
        }
        
        if ( it != null ) {            
            while ( it.hasNext() ) {
                Bundle role = it.next();
                if ( role == null ) 
                    continue;

                List<EntitlementGroup> groups = map.get(role);
                if ( groups == null ) 
                    continue;

                RoleDetection detection = id.getRoleDetection(id.getRoleDetections(), role.getId(), role.getName());
                if ( detection == null ) {
                    log.info("Detection not found for " + role.getName() + " on identity " + id.getName());
                    continue;
                }
                // todo rshea TA4710: Need to figure out if it's ok to have duplicate roles in allowedBy list
                List<String> allowedBy = getAllowedBy(id.getRoleAssignments(), detection);
                
                for ( EntitlementGroup group : groups  ) {
                    Attributes<String,Object> attrs = group.getAttributes();
                    if ( attrs != null ) {
                        Iterator<String> keys = attrs.keySet().iterator();
                        if ( keys != null ) {
                            while ( keys.hasNext() ) {
                                String key = keys.next();
                                if ( key != null ) {
                                    List<String> strList = valueToStringList(attrs.get(key));
                                    if ( strList != null ) {   
                                        for ( String str : strList ) {
                                            String index = getKey(group,key, str);
                                            RoleData data = roleDataMap.get(index);
                                            if ( data == null ) {
                                                data = new RoleData();
                                                roleDataMap.put(index, data);
                                            }
                                            data.addSourceDetectable(role.getName());
                                            data.addSourceAssignables(allowedBy);
                                        }
                                    }
                                }                                        
                            }
                        }
                    }
                    
                    List<Permission> perms = group.getPermissions();
                    if ( perms != null ) {
                        for ( Permission perm : perms ) {
                            List<String> rights = perm.getRightsList();
                            if ( rights != null ) {
                                for ( String right : rights ) {
                                    String index = getKey(group, perm.getTarget(), right); 
                                    RoleData data = roleDataMap.get(index);                                            
                                    if ( data == null ) {
                                        data = new RoleData();
                                        roleDataMap.put(index, data);
                                    }
                                    data.addSourceDetectable(role.getName());
                                    data.addSourceAssignables(allowedBy);
                                } 
                            }
                        }
                    }
                }                    
            }
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Refresh Identity Entitlements
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Refresh the entitlements for a single Identity.
     * 
     * This takes into account the sources being aggregated/refreshed and
     * only updates the role assignment and detections when the
     * correlator is passed in non-null.
     * 
     * This method called finish(identity) so there is no need for it
     * to be called after this method. 
     * 
     * When the EntitlementCorrelator is null the role assignments and
     * detections will not be promoted.
     * 
     * @param identity The identity.
     * @param correlator The entitlement correlator.
     * @throws GeneralException
     */
    public void refreshEntitlements(Identity identity, List<Application> sources, EntitlementCorrelator correlator)
        throws GeneralException {        
        
        // no source and explicit refresh flag means refresh
        // entitlements on all links.        
        if ( _disableIdentityEntitlementPromotion ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Entitlement promotion has been explicity disabled ignoring promotion for '" + identity.getName()+ "'");
            }
            return;
        }   
        
        // Set this up-front
        if ( correlator != null ) {
            correlator.analyzeContributingEntitlements(identity);

            Map<Bundle, List<EntitlementGroup>> map = correlator.getEntitlementMappings(true);

            setEntitlementMapping(identity, map);
        }
        
        int linkCount = _identityService.countLinks(identity);
        if ( Util.size(sources) > 0  && linkCount  > 0 ) {
            for ( Application source : sources ) {
                List<Link> links = _identityService.getLinks(identity, source);
                if ( links != null) {
                    for ( Link link : links ) {
                        promoteLinkEntitlements(link);
                    }
                }
            }
        } else 
        if ( _refreshIdentityEntitlements ) {
            if ( linkCount == 0 ) {
                if ( identity.getId() != null ) {
                    // in case all of the links were removed from out under
                    // us, clean up any existing entitlements.
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("identity", identity));
                    ops.add(Filter.notnull("application"));
                    _context.removeObjects(IdentityEntitlement.class, ops);
                }
            } else {
                // we can track orphans when going over all the links
                if (! _disableOrphanChecks )
                    _orphanTracking = true;
                
                for ( Link link : identity.getLinks() )  {
                    promoteLinkEntitlements(link);
                }
            }    
        }
        
        //
        // Deal with Role assignments and detections
        //
        if ( correlator != null ) {
            Meter.enter(39, "promoteIdentityEntitlementRoleAssignments");
            promoteRoleAssignments(identity);
            Meter.exit(39);
            
            Meter.enter(40, "promoteIdentityEntitlementRoleDetections");                    
            promoteRoleDetections(identity);
            Meter.exit(40);
        }
        
        // call finish to persist the data we've queued up
        finish(identity);
    }

    /**
     * Build an entitlement that represents each of the assigned
     * roles. Fetch the current list of assigned role entitlements
     * so we are able to update and prune the ones no longer
     * assigned.
     * 
     * @param identity
     */
    public void promoteRoleAssignments(Identity identity) 
        throws GeneralException {
        
        if ( log.isDebugEnabled() ) {
            _context.printStatistics();
            log.debug("promoteRoleAssignments start " + identity.getName()); 
            Util.enableSQLTrace();
        }
        
        getRoleEntitlizer().promoteRoleAssignments(identity);
        
        if ( log.isDebugEnabled() ) {
            _context.printStatistics();
            log.debug("promoteRoleAssignments end " + identity.getName()); 
            Util.disableSQLTrace();
        }  
    }

    /**
     * 
     * Promote any IT Role detections including adorning other
     * entitlements with the "indirectly" assigned relationship.
     * 
     * @param identity
     * @throws GeneralException
     */
    public void promoteRoleDetections(Identity identity)  
        throws GeneralException {
        
        if ( log.isDebugEnabled() ) {
            _context.printStatistics();
            log.debug("promoteRoleDetections start " + identity.getName()); 
            Util.enableSQLTrace();
        }

        getRoleEntitlizer().promoteRoleDetections(identity, _entitlementMapping); 
        
        if ( log.isDebugEnabled() ) {
            _context.printStatistics();
            log.debug("promoteRoleDetections end " + identity.getName()); 
            Util.disableSQLTrace();
        }     
    }       
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Link -> IdentityEntitlement promotion
    //
    /////////////////////////////////////////////////////////////////////////

    /**
     * Promote all of the attributes on the Link's application
     * marked entitlement. When updating, mark all natively 
     * removed entitlements "Disconnected"  when assigned 
     * otherwise, just remove the entitlement.
     * 
     * This is typically called when running aggregation or 
     * refreshing an identity.
     * 
     * In this method we get all of the entitlements
     * for each application and deal with them in 
     * a map as we visit.
     * 
     * This method adds any IdentityEntitlement objects fetched
     * created or deleted during this process to a CacheTracker
     * which is set on this object. Caller is responsible for 
     * de-caching the objects via the cache tracker, 
     * which is typically automatic when dealing
     * with the aggregation or refresh task.
     * 
     * We are not currently doing any optimization, but when
     * used with optimized aggregation there should be some
     * performance increases. There is room for querying up front 
     * to get the current entitlements and only updating 
     * when reaggregating.
     *  
     * @param link
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public void promoteLinkEntitlements(Link link) 
        throws GeneralException {

        if ( log.isDebugEnabled() ) {
            _context.printStatistics();
            log.debug("PromoteLinkEntitlements start " + link); 
            Util.enableSQLTrace();
        }
        
        if ( _disableIdentityEntitlementPromotion ) {
            log.debug("PromoteLinkEntitlements is explicity disabled did not promote for: " + link);
            return;
        }

        // Promote the attributes that are marked as entitlements.
        Application app = link.getApplication();       
        if ( app != null ) {            
            if ( _orphanTracking ) {
                // store this off when enabled so we can easily find orphaned links
                _linksVisited.add(new LinkInfo(link));
            }
            Identity identity = link.getIdentity();
            Map<String, IdentityEntitlement> entitlementMap = getLinkEntitlements(link);
            List<String> attrs = getEntitlementAttributes(app);
            if ( attrs != null ) {
                for ( String attr : attrs ) {
                    if ( attr == null ) continue;
                    Object linkValue = link.getAttribute(attr);
                    List<Object> values = Util.asList(linkValue);
                    if ( values != null ) {                    
                        for ( Object obj : values ) {
                            if( obj != null ) {
                                String strVal = obj.toString();
                                IdentityEntitlement entitlement = createOrUpdateEntitlement(entitlementMap, identity, link, attr, strVal);

                                EntitlementUpdater updater = new EntitlementUpdater(entitlement);
                                setAssignmentInfo(entitlement, identity, updater);
                                
                                if (updater.hasUpdates()) {
                                    markToSave(entitlement);
                                }
                            }
                        }
                    }
                }
            }
            
            List<Permission> perms = (List<Permission>)link.getAttribute(Connector.ATTR_DIRECT_PERMISSIONS);
            processPermissions(entitlementMap, identity, link, perms, false);
            
            List<Permission> targetPerms = ObjectUtil.getTargetPermissions(_context, link);
            processPermissions(entitlementMap, identity, link, targetPerms, true);
                                    
            //
            // Go over the entitlements and make sure they'll
            // be decached by adding them to the cacheTracker and
            // mark any that weren't found in the feed as
            // disconnected.
            //
            if ( entitlementMap != null ) {
                for ( IdentityEntitlement ie : entitlementMap.values() ) {
                    // this is a transient field set as we see each 
                    // entitlement in the feed
                    if ( ie != null && !ie.foundInFeed() ) {
                        AttributeAssignment assignment = getAttributeAssignment(ie, identity.getAttributeAssignments());
                        if ( assignment != null ) {
                            ie.setAggregationState(AggregationState.Disconnected);
                            ie.setStartDate(assignment.getStartDate());
                            ie.setEndDate(assignment.getEndDate());
                            //TODO: Anything else need to be synced? -rap
                            markToSave(ie);
                        } else {                            
                            // only remove if thre's not pending request
                            IdentityRequestItem item = ie.getPendingRequestItem();
                            if ( isCandidateForRemoval(item) ) { 
                                _context.removeObject(ie);
                                _toBeRemoved.add(ie);
                            }
                        }
                    }
                }
            }            
            // Help out garbage collection
            if ( entitlementMap != null ) {
                entitlementMap = null;
            }
        }  
        
        if ( log.isDebugEnabled() ) {
            _context.printStatistics();
            Util.disableSQLTrace();
            log.debug("promoteLinkEntitlements end");
        }
    }

    /**
     * Set the assignment information (assigned flag, start/end dates)  on the given IdentityEntitlement using
     * the AttributeAssignements on the identity.
     *
     * @param entitlement  The IdentityEntitlement to update.
     * @param identity  The Identity with the AttributeAssignments to look at.
     * @param updater  The EntitlementUpdater to use to set entitlement values and track changes.
     */
    private void setAssignmentInfo(IdentityEntitlement entitlement, Identity identity, EntitlementUpdater updater) {
        AttributeAssignment assignment = getAttributeAssignment(entitlement, identity.getAttributeAssignments());
        if (null != assignment) {
            updater.setAssigned(true);
            updater.setStartDate(assignment.getStartDate());
            updater.setEndDate(assignment.getEndDate());
            //TODO: Anything else need to be synced? -rap
        }
        else {
            updater.setAssigned(false);
        }
    }

    public void refreshAttributeAssignmentEntitlements(List<AttributeAssignment> assignments, Identity identity)
            throws GeneralException {
        if (!Util.isEmpty(assignments) && identity != null) {
            for (AttributeAssignment ass : Util.safeIterable(assignments)) {
                List<IdentityEntitlement> ents = getAssignedAttributeEntitlements(identity, ass);
                if (ents != null && !Util.isEmpty(ents)) {
                    if (ents.size() == 1) {
                        IdentityEntitlement entitlement = ents.get(0);
                        entitlement.setStartDate(ass.getStartDate());
                        entitlement.setEndDate(ass.getEndDate());
                        entitlement.setNativeIdentity(ass.getNativeIdentity());
                        //TODO:Anything else need synced?
                        _context.saveObject(entitlement);

                    } else {
                        log.warn("Found " + ents.size() + " IdentityEntitlements for AttributeAssignment[" + ass + "]");
                    }
                } else {
                    log.warn("Could not find associated IdentityEntitlement for AttributeAssignment[" + ass + "]");
                }
            }
        }
    }
    
    /**
     * 
     * Save off the pending changes held locally in this object.
     * 
     * We queue them and save at the end to avoid carrying around
     * these entitlements and letting hibernate manage them...
     * 
     * 
     * @throws GeneralException
     */
    public void finish(Identity identity) throws GeneralException {
        savePending();
        if ( roleDataMap != null ) 
            roleDataMap = new HashMap<String,RoleData>();
        
        // Clean orphans - remove "Connected" entitlements that references
        // a missing link
        if ( _orphanTracking ) {
            removeOrphans(identity);
        }
        _linksVisited.clear();
        _orphanTracking = false;
        if ( _toBeRemoved != null )
            _toBeRemoved.clear();
        
        _toBeRemoved = new ArrayList<IdentityEntitlement>();                
    }
    
    /**
     * Save off any queued entitlements.
     * 
     * @return true if anything was saved
     * @throws GeneralException
     */
    private boolean savePending() throws GeneralException {
        boolean saved = false;
        if ( _toBeSaved != null ) {
            for ( IdentityEntitlement toSave : _toBeSaved ) {
                if ( toSave != null )  {
                    _context.saveObject(toSave);
                    saved = true;
                }
            }
            _toBeSaved = null;
        }
        _toBeSaved = new ArrayList<IdentityEntitlement>();
        
        return saved;
    }

    /**
     * Mark the given IdentityEntitlement to be saved when savePending() is called.
     */
    private void markToSave(IdentityEntitlement ie) {
        if (!_toBeSaved.contains(ie)) {
            _toBeSaved.add(ie);
        }
    }

    /**
     * Build a Map of Entitlements stored for this identity, using a map
     * here to avoid iterating over them in the cases where there are 
     * several hundred values for a single aplication/link.
     * 
     * Scope this down to the application, nativeIdentity, instance and identity.
     * Cache it based on the  attribute name and value combination.
     * 
     * @param link
     * @param name
     * @param value
     * 
     * @return HashMap<String,IdentityEntitlement> 
     * 
     * @throws GeneralException
     */
    protected HashMap<String,IdentityEntitlement> getLinkEntitlements(Link link) 
        throws GeneralException {
        
        HashMap<String,IdentityEntitlement> linkEnts = new HashMap<String,IdentityEntitlement>();
        if ( link == null ) return linkEnts;
        
        Identity id = link.getIdentity();
        Application app = link.getApplication();
        
        // New identities will not have entitlements so no since querying
        // New links may have entitlements awaiting their desired state 
        if ( id == null || id.getId() == null || app == null ) 
            return linkEnts;               
        
        //
        // Query for the list of current entitlements for this link
        //
        Filter filter = buildAccountFilter(id, app, link.getNativeIdentity(), link.getInstance());
        linkEnts = buildLinkEntitlementMap(filter, false);
        
        return linkEnts;
    }
    
    /**
     * Using the cache built on the query results find or create the IE
     * based on the link, attribute name and value.
     * 
     * 
     * @param entitlementMap
     * @param link
     * @param attrName
     * @param value
     * @return
     * @throws GeneralException
     */
    private void createOrUpdatePermission(Map<String,IdentityEntitlement> entitlementMap, 
                                          Identity identity, LinkInterface link, Permission perm,
                                          boolean targetPermissions) 
        throws GeneralException {
         
        String target = perm.getTarget();
        if ( target == null ) {
            if ( log.isErrorEnabled() ) {
                String permString = (perm!=null) ? perm.toString() : null;
                log.error("Permission object without a target,  ignored and not promoted." + permString);
            }
        }
        else {
            // Break each right into its own entitlement and set the annotation on all of them
            List<String> rights = perm.getRightsList();
            if (Util.size(rights) > 0) {
                for ( String right : rights ) {
                    createOrUpdatePermission(entitlementMap, identity, link, perm, right, targetPermissions);
                }
            }
            else {
                // jsl - what does an empty rights list even mean?
                // prior to 7.2 we were not overriding the annotation, type and source
                createOrUpdatePermission(entitlementMap, identity, link, perm, null, targetPermissions);
            }
        }
    }
    
    /**
     * Create or update an entitlement for a single permission right.
     */
    private void createOrUpdatePermission(Map<String,IdentityEntitlement> entitlementMap, 
                                          Identity identity, LinkInterface link,
                                          Permission perm,
                                          String right,
                                          boolean targetPermission) 
        throws GeneralException {

        // this does the base build with type=Entitlement
        IdentityEntitlement permRight = createOrUpdateEntitlement(entitlementMap, identity, link, perm.getTarget(), right);

        EntitlementUpdater updater = new EntitlementUpdater(permRight);

        updater.setAnnotation(perm.getAnnotation());
        updater.setType((targetPermission) ? Type.TargetPermission : Type.Permission);

        // The source for target permissions should be TargetAggregation (instead of Aggregation), unless
        // the permission was assigned.  In this case, use the source from the assignment.
        if (targetPermission && !Source.TargetAggregation.toString().equals(permRight.getSource())) {
            AttributeAssignment ass = getAttributeAssignment(permRight, identity.getAttributeAssignments());
            String source =
                ((null != ass) && (null != ass.getSource())) ? ass.getSource() : Source.TargetAggregation.toString();
            updater.setSource(source);

            // TODO: Callers of ObjectUtil.getTargetPermissions expect to have
            // the aggregationSource attribute set to the TargetSource name.
            // That's available here from perm.getAggregationSource but we have
            // no way to store it in the IdentityEntitlement except for the attributes
            // map.  That prevents us from using a projection search to convert IdentityEntitlements
            // back into Permissions.  Need to extend that model...
        }

        setAssignmentInfo(permRight, identity, updater);

        if (updater.hasUpdates()) {
            markToSave(permRight);
        }
    }

    /**
     * Lookup the entitlement value in the table, if not found generate a new entitlement
     * that will be saved ready for commit.
     * 
     * Be very careful here about pushing updates to entitlements.  These are typically
     * run in combination with the "explicitSave" mode functionality which will not do any 
     * dirty checking at the hibernate layer. If something is saved, it'll be pushed to
     * the database.  Typically this is handled by using a EntitlementUpdater object 
     * that acts as a proxy and tracks if changes were made.
     * 
     * @param entitlementMap
     * @param identity
     * @param link
     * @param attrName
     * @param value
     * @return
     * @throws GeneralException
     */ 
    private IdentityEntitlement createOrUpdateEntitlement(Map<String,IdentityEntitlement> entitlementMap, 
                                                          Identity identity, 
                                                          LinkInterface link, 
                                                          String attrName, 
                                                          String value)                                                            
        throws GeneralException {
           
        IdentityEntitlement existing = null;        
        if ( entitlementMap != null )
            existing = entitlementMap.get(buildKey(attrName, value));

        // jsl - why do this rather than just link.getApplication?
        // you're going to force a l2cache hit!!
        Application application = _context.getObjectByName(Application.class, link.getApplicationName());
        if ( existing == null ) {
            int linkCount = _identityService.countLinks(identity, application);
            if ( linkCount == 1 ) {
                //
                // make sure there aren't any existing requests for this entitlement
                // that differ only by native identity.  There are cases where the native
                // identity gets generated or changed  during evaluation
                //
                // jsl - how does this work, what about nativeIdentity?  probably won't handle
                // multiple accounts on the same application, also not handling instance
                List<Filter> filters = new ArrayList<Filter>();
                filters.add(Filter.eq("identity", identity));
                filters.add(Filter.eq("application", application));
                filters.add(Filter.eq("aggregationState", AggregationState.Disconnected));
                filters.add(Filter.ignoreCase(Filter.eq("name", attrName)));
                filters.add(Filter.ignoreCase(Filter.eq("value", value)));
                //We no longer limit ourselves to pending as only non-provisioning connectors
                //will result in pending items at this time - we have to get all the entitlements
                //in case this is a provisioning connector
                //filters.add(Filter.notnull("pendingRequestItem"));
                //filters.add(Filter.eq("pendingRequestItem.operation", "Add"));
               
                existing = _context.getUniqueObject(IdentityEntitlement.class, Filter.and(filters));
            }
        } 
        
        if ( existing == null ) {
            existing = new IdentityEntitlement();
            existing.setApplication(application);
            existing.setIdentity(identity);
            existing.setName(attrName);
            existing.setValue(value);   
            if ( application != null ) {
                existing.setType(Type.Entitlement);
            } 
            existing.setSource(Source.Aggregation.toString());            
            if ( identity == null ) {
                // shouldn't happen but log an error if it happens
                throw new GeneralException("Transient identity object found while creating a new Identity Entitlement, object was not saved.");
            }                       
            entitlementMap.put(buildKey(attrName, value),existing);
            _totalCreated++;
        } 
        // this is transient and does not get persisted            
        // used to find things removed from the feed.
        existing.setFoundInFeed(true);
        
        // Wrap this in a EntitlementUpdater object that can keep track of 
        // changes and help us know if we need to explicitly save the object
        EntitlementUpdater updater = new EntitlementUpdater(existing);
        updater.setAggregationState(AggregationState.Connected);
        updater.setLinkDetails(link);
        
        // set role data if we have that available
        RoleData data = ( roleDataMap != null ) ? roleDataMap.get(getKey(existing)) : null;
        if ( data != null ) {
            updater.setGrantdByRole(true);
            updater.setSourceDetectedRoles(data.getSourceDetectablesCsv());
            updater.setSourceAssignableRoles(data.getSourceAssignablesCsv());
            this._totalIndirectUpdated++;
            // mark this updated so we don't do it again later
            data.updateDuringLinkRefresh(true);
        } else   
        if ( data == null && roleDataMap != null ) {
            updater.clearRoleData();
        }
        
        if ( updater.hasUpdates() ) {
            markToSave(existing);
        }
        return existing;
    }
    
    /**
     *
     * To check if we should remove or keep an entitlement, check with 
     * the request item and see what state the request is in before
     * removing it.
     * 
     * Basically, we don't want aggregation runs that happen before
     * a request has been fully-fulfilled to remove the pending entitlement
     * data we want to show customers. 
     * 
     * The only time we want to keep an entitlement around is if the pending
     * provisioning request has an add operation and is still in-flight.
     * 
     * The queued state is one that's needs special handling as they are 
     * may or not be polled by the workflow depending on if the result
     * also returns at request id that can be periodically checked.
     * 
     * For cases like OIM where we don't return a requestId it may 
     * happen in some point in the future and we don't want 
     * aggregations that happen in between the full-fillment and 
     * to remove the entitlement and meta-data.
     * 
     * If we are in retry or pending AND the request is no longer 
     * executing (which would be odd) remove the entitlement.
     * 
     * @param item
     * @return
     */ 
    protected boolean isCandidateForRemoval(IdentityRequestItem item) {
        boolean toRemove = true;
        if ( item != null ) {            
            if ( Util.nullSafeEq(item.getOperation(), ProvisioningPlan.OP_ADD) ) {
                IdentityRequest req = item.getIdentityRequest();
                ProvisioningState state = item.getProvisioningState();
                // NOTE : when state is null == queued
                if ( ( state == null ) ||
                     ( ( Util.nullSafeEq(state, ProvisioningState.Pending) || Util.nullSafeEq(state, ProvisioningState.Retry) )) && (null != req && req.isExecuting()) ) {
                    // keep it around
                    toRemove = false;
                //IIQETN-4800: Logging if we get a null IdentityRequest object
                } else if (null == req) {
                    log.warn("IdentityRequestItem: " + item.getId() + " does not have an IdentityRequest parent object");
                }
            }            
        } 
        return toRemove;
    }    
    
    /**
     * 
     * Go through all of the entitlements and make sure there is an 
     * a Link for each entitlement. 
     * 
     * This is to handle cases where the Link has somehow disappeared
     * from the Identity without going through the Terminator or withot
     * the Entitlements getting cleaned up.
     * 
     * Typically this method is called by refresh process when refreshing
     * IdentityEntitlements with a special flag to the refresh process.
     * 
     * @param identity
     */
    private void removeOrphans(Identity identity) throws GeneralException {
                
        QueryOptions ops = new QueryOptions();
        ops.setDistinct(true);
        ops.add(Filter.eq("identity", identity));
        // Disconnected ones need special handling
        ops.add(Filter.eq("aggregationState", AggregationState.Connected));
        // don't include IIQ entitlements
        ops.add(Filter.notnull("application"));
                
        List<Filter> toRemove = new ArrayList<Filter>();
        Iterator<Object[]> ents = _context.search(IdentityEntitlement.class, ops, Util.csvToList("application, nativeIdentity, instance"));
        if ( ents != null ) {
            while ( ents.hasNext() ) {
                Object[] ent = ents.next();
                if ( ent.length == 3 ) {
                    Application app = (Application)ent[0];
                    String ni = (String)ent[1];
                    String instance = (String)ent[2];
                    if ( !visitedLink(app, ni, instance) ) {
                        if ( log.isDebugEnabled() ) {
                            log.debug("Removing orphaned entitlement app["+app+"] ni["+ni+"] instance["+instance+"]");
                        }
                        // instance is not required and it can be null
                        Filter instanceFilter = (null == instance) ? Filter.isnull("instance") : Filter.ignoreCase(Filter.eq("instance", instance));
                        toRemove.add(
                                Filter.and(Filter.eq("application", app),
                                           Filter.ignoreCase(Filter.eq("nativeIdentity",  ni)),
                                           instanceFilter ));
                    } 
                }
            }
        }
        
        if ( toRemove.size() > 0 ) {
            QueryOptions removeOptions = new QueryOptions();
            removeOptions.add(Filter.eq("identity", identity));
            removeOptions.add(Filter.or(toRemove));
            _context.removeObjects(IdentityEntitlement.class, removeOptions);
        }
    }
    
    /**
     * Check with out linksVisited set to see if we've refreshed
     * the entitlements from this Link.
     * 
     * @param app
     * @param nativeIdentity
     * @param instance
     * @return
     */
    private boolean visitedLink(Application app, String nativeIdentity, String instance) {
        
        String appId = (app != null ) ? app.getId() : null;
        if ( _linksVisited.contains(new LinkInfo(appId, nativeIdentity, instance) ) ) {
            return true;
        }
        return false;
    }
        
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////////
    
    private List<String> getEntitlementAttributes(Application app) throws GeneralException {

        List<String> list = null;

        Schema schema = ( app != null ) ?  app.getAccountSchema() : null;
        if ( schema != null ) {
            list = schema.getEntitlementAttributeNames();    
        }        
        return list;
    }
    
    protected static String getKey(Entitlements group, String attrName, String value) {
        return group.getApplicationName() + "/" + group.getNativeIdentity() + "/" + group.getInstance() + "/" + attrName + "/" + value;
    }
    
    private String getKey(IdentityEntitlement entitlement)  {
        return entitlement.getAppName() + "/" + entitlement.getNativeIdentity() + "/" + entitlement.getInstance() + "/" + entitlement.getName() + "/" + entitlement.getStringValue();
    }
       
    private RoleEntitlizer getRoleEntitlizer() {
        if ( _roleEntitlizer == null ) {
            _roleEntitlizer = new RoleEntitlizer(_context, getArguments());
            _roleEntitlizer.setCorrelationModel(_correlationModel);
        }
        
        if ( _roleEntitlizer != null ) {
            _roleEntitlizer.setPendingRemoval(_toBeRemoved);
            _roleEntitlizer.setPendingSave(_toBeSaved);
            _roleEntitlizer.setRoleData(roleDataMap);
        }
        
        return _roleEntitlizer;
    }
    
    /**
     * Role over the the list of permissions and create or update the necessary
     * entitlements to represent the permissions.  Flag for targetPermissions
     * indicates we are processing targetPermissions so we can add a flag
     * to the permissions comming from target aggregation.
     */
    private void processPermissions(Map<String,IdentityEntitlement> entitlementMap, 
                                    Identity identity, 
                                    Link link, 
                                    List<Permission> perms, boolean targetPermissions) 
        throws GeneralException {
        if ( perms != null ) {
            for ( Permission perm : perms ) {
                if ( perm == null ) continue;
                createOrUpdatePermission(entitlementMap, identity, link, perm, targetPermissions);
            }   
        }    
    }

    /**
     * Build a map of the entitlements found by the provided filter.
     * The map is keyed using the attrName+attrbuteValue. 
     *
     * The targetPermissions option determines whether we include or exclude
     * entitlements that are target permissions.  It is false when we are
     * reconciling entitlements during account aggregation or identity
     * refresh, true when we are reconciling during target collection.  
     * 
     * @param filter
     * @return
     * @throws GeneralException
     */
    private HashMap<String,IdentityEntitlement> buildLinkEntitlementMap(Filter filter,
                                                                        boolean targetPermissions)
        throws GeneralException {
        
        HashMap<String,IdentityEntitlement> ents = new HashMap<String,IdentityEntitlement>();
        if ( filter == null ) return ents;

        // Add a filter to either include only target permissions if we are only refreshing target permissions.
        if (targetPermissions) {
            // KG - Note that we're also including the "Permission" type here.  This is due to the fact that prior
            // to 7.2 we did not have a TargetPermission type.  Instead, TargetPermissions were stored with a type
            // of Permission and were denoted as target permissions by having a source of TargetAggregation or PAM.
            // There was also a bug in 7.1p2 that set the source of target permissions to "Aggregation" instead of
            // "TargetAggregation", so we cannot rely on this.  The only harm from returning non-target permissions
            // here is that there will be slightly more data to process.
            filter = Filter.and(filter, Filter.in("type", Arrays.asList(Type.TargetPermission, Type.Permission)));
        }

        QueryOptions qo = new QueryOptions();
        qo.addFilter(filter);
        
        List<IdentityEntitlement> ies = _context.getObjects(IdentityEntitlement.class, qo );
        if ( ies != null )  {
            for ( IdentityEntitlement ie : ies ) {
                _totalUpdated++;
                String key = buildKey(ie.getName(), ie.getStringValue());
                IdentityEntitlement existing = ents.get(key);
                if ( existing != null ) {
                    // this indicates we've got a duplicate entitlement somehow in the
                    // db. There were some issues in pre 6.1 releases that
                    // could cause duplicates to get created in certain 
                    // situations
                    ie = mergeDups(existing, ie);
                }
                ents.put(key, ie);
            }
        }
        
        return ents;
    }
    
    /**
     * 
     * When we see two entitlements referencing the same entitlement on the same
     * account merge the two and delete the extra.
     * 
     */
    private IdentityEntitlement mergeDups(IdentityEntitlement existing, IdentityEntitlement neu) 
        throws GeneralException {
        
        IdentityEntitlement ent = null;
        IdentityEntitlement other = null;
        
        if ( Util.nullSafeEq(existing.getSource(), Source.LCM ) ) {
            ent = existing;
            other = neu;
        } else { 
            int stuffSet = analyse(existing);
            int neuStuffSet = analyse(neu);
        
            if ( stuffSet > neuStuffSet ) {
                ent = existing;
                other = neu;
            } else {
                ent = neu;
                other = existing;
            }
        }
        
        if ( ent.isConnected() || neu.isConnected() )
            ent.setAggregationState(AggregationState.Connected);
        
        if ( ent.getPendingCertificationItem() == null && other.getPendingCertificationItem() != null )
            ent.setPendingCertificationItem(other.getPendingCertificationItem());
        
        if ( ent.getCertificationItem() == null && other.getCertificationItem() != null )
            ent.setCertificationItem(other.getCertificationItem());
        
        if ( ent.getPendingRequestItem() == null && other.getPendingRequestItem() != null )
            ent.setPendingRequestItem(other.getPendingRequestItem());
        
        if ( ent.getRequestItem() == null && other.getRequestItem() != null )
            ent.setRequestItem(other.getRequestItem());
        
        if ( other.getId() != null )
          _context.removeObject(other);
        
        return ent;
    }
    
    private int analyse(IdentityEntitlement neu) {
        int count = 0;
        
        if ( neu.getPendingCertificationItem() != null ) 
            count++;
            
        if ( neu.getPendingRequestItem() != null) 
            count++;
        
        if ( neu.getCertificationItem() != null ) 
            count++;
        
        if ( neu.getCertificationItem() != null ) 
            count++;    
        
        return count;
    }

    /**
     * jsl - For target permissions, the key isn't technically enough
     * because it does include the source collector name.
     * We are going to collapse target/right combos that came
     * from different collectors but just happen to share the same
     * target name.  You could have several AD instances each with
     * target c:\ but there is no way to distinguish them in the 
     * IdentityEntitlements table.  If we added the source to the key
     * we could do that, but since the UI probably doesn't display it it
     * may look confusing to see duplicates. 
     */
    protected String buildKey(String attrName, String value) {
        return attrName + "!!" + value;
    }   
    
    /**
     * Called during aggregation to populate the results
     * with some stats to help understand what we ended
     * up doing.
     * 
     * @param result
     */
    public void saveResults(TaskResult result) {
       result.setInt(RET_CREATED, _totalCreated);
       result.setInt(RET_REMOVED, _totalRemoved);
       result.setInt(RET_UPDATED, _totalUpdated);
       result.setInt(RET_INDIRECT_DURING_LINK, this._totalIndirectUpdated);

       if ( _roleEntitlizer != null )
           _roleEntitlizer.saveResults(result);
    }

    public void restoreFromTaskResult(TaskResult result) {
        if (result != null) {
            _totalCreated = result.getInt(RET_CREATED);
        }
    }
   
    public void traceStatistics() {
       System.out.println(_totalCreated + " Entitlements Created.");   
       System.out.println(_totalRemoved + " Entitlements Removed.");
       if ( _roleEntitlizer != null )
           _roleEntitlizer.traceStatistics();
    }

    /**
     * Transfer ownership of link entitlememnts to a new identity.
     * 
     * @param link The link that has been moved
     * @param source Identity that previously owned link
     * @param destination Identity that now owns the link
     * @throws GeneralException
     */
    public void moveLinkEntitlements(Link link, Identity source, Identity destination)
            throws GeneralException {
        if (link != null && source != null) {
            List<IdentityEntitlement> entitlements = getAccountEntitlements(source, link.getApplication(), link.getNativeIdentity(), link.getInstance());
            if (!Util.isEmpty(entitlements)) {
                for (IdentityEntitlement entitlement : entitlements) {
                    if (entitlement != null) {
                        entitlement.setIdentity(destination);
                        _context.saveObject(entitlement);
                    }
                }
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Target Permissions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * New interface added in 7.2 to build IdentityEntitlements for a list
     * of target permissions.  Called by TargetAggregator.
     *
     * Formerly TargetAggregator would maintain a List<Permission> inside
     * the Link under the "targetPermissions" attribute.  Then during
     * a later agg/refresh, Entitlizer would convert that list into 
     * IdentityEntitlements.  There were two problems with that, first the
     * list of target permissions can become very large which can severely
     * bloat the Link XML clob and makes looking at Identity XML more difficult.
     * Second, it is annoying to have to do an extra agg/refresh just to get
     * the IdentityEntitlements built, people expect to see them in the UI
     * immediately after target collection.  
     * 
     * This reuses some of the same internal pieces for before/after 
     * reconciliation so I put it here rather than TargetAggregator.
     */
    public void refreshTargetPermissions(Link link, List<Permission> permissions) 
        throws GeneralException {        
        
        // we're not going to call finish() here so have to initialize
        // these each time
        _toBeSaved = new ArrayList<IdentityEntitlement>();
        _toBeRemoved = new ArrayList<IdentityEntitlement>();

        if (log.isDebugEnabled() ) {
            log.debug("refreshTargetPermissions start " + link); 
        }

        // get all of the current target permissions for this account
        Map<String, IdentityEntitlement> entitlementMap = getTargetPermissions(link);
        
        processPermissions(entitlementMap, link.getIdentity(), link, permissions, true);

        // processPermissions will have built the _toBeSaved list,
        // find the ones that need to be removed
        for (IdentityEntitlement ie : entitlementMap.values()) {
            // this is a transient field set as we see each 
            // entitlement in the feed
            if (ie != null && !ie.foundInFeed()) {
                // only remove if thre's not pending request
                // jsl - copied from promoteLinkEntitlements, this
                // shouldn't happen since you can't request removal
                // of target permissions
                //Should not remove if Assigned -rap
                AttributeAssignment assignment = getAttributeAssignment(ie, link.getIdentity().getAttributeAssignments());
                if (assignment != null) {
                    ie.setAggregationState(AggregationState.Disconnected);
                    ie.setStartDate(assignment.getStartDate());
                    ie.setEndDate(assignment.getEndDate());
                    //TODO: Anything else need to be synced? -rap
                    markToSave(ie);
                } else {
                    IdentityRequestItem item = ie.getPendingRequestItem();
                    if (isCandidateForRemoval(item)) {
                        _toBeRemoved.add(ie);
                    }
                }
            }
        }

        // save em
        for (IdentityEntitlement ie : Util.iterate(_toBeSaved)) {
            _context.saveObject(ie);
        }

        // remove em
        for (IdentityEntitlement ie : Util.iterate(_toBeRemoved)) {
            _context.removeObject(ie);
        }
        
        _context.commitTransaction();

        if ( log.isDebugEnabled() ) {
            log.debug("refreshTargetPermissions end " + link);
        }
    }

    /**
     * Get all of the current IdentityEntitlements for target permissions 
     * associated with an account.
     *
     * TODO: If we ever fix name collisions between multiple collectors 
     * assigned to the same application, then we will have to pass down
     * the collector name and use that to filter the result.
     */
    private Map<String, IdentityEntitlement> getTargetPermissions(Link link)
        throws GeneralException {

        HashMap<String, IdentityEntitlement> ents;
        
        Identity id = link.getIdentity();
        Application app = link.getApplication();
        
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("identity", id));
        filters.add(Filter.eq("application", app));
        filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", link.getNativeIdentity())));
        // gak, give Application a way to say it doesn't use instances
        // so we can compile out this crap in more places
        String instance = link.getInstance();
        if (Util.getString(instance) == null) {
            filters.add(Filter.isnull("instance"));
        } else {
            filters.add(Filter.ignoreCase(Filter.eq("instance", instance)));
        } 
        Filter filter = Filter.and(filters);

        // we need only the ones whose source is "TargetAggregation"
        ents = buildLinkEntitlementMap(filter, true);
        
        return ents;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Helper Classes
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 
     * Helper class to hold data around for each of the 
     * entitlements.  We turn the entitlementmapping from the 
     * entitlement correlation around so it's entitlement-centric
     * to allow us to update the links as they are visited
     * when possible.
     * 
     * @author dan.smith
     *
     */
    protected class RoleData {
        
        /**
         * Which of the user's detected role(s)s granted an entitlement.
         */
        List<String> sourceDetectables;
        
        /**
         * Which assignable roles could have indirectly granted this
         * entitlement.
         * 
         * List of all roles that are assigned to the identity and 
         * are the source of the detected roles. 
         */
        List<String> sourceAssignables;
        
        private boolean writtenDuringLinkUpdate;
        
        public RoleData() {
            sourceDetectables = new ArrayList<String>();
            sourceAssignables = new ArrayList<String>();            
        }
               
        public void addSourceAssignable(String roleName) {
            if ( roleName == null ) 
                return;
            
            if ( sourceAssignables == null ) 
                sourceAssignables = new ArrayList<String>();
            
            sourceAssignables.add(roleName);
        }
        
        public void addSourceAssignables(List<String> roles) {
            if ( roles != null ) {
                for ( String role : roles ) {
                    addSourceAssignable(role);
                }
            }
        }

        public void addSourceDetectable(String roleName) {
            if ( roleName == null ) 
                return;
            
            if ( sourceDetectables == null ) 
                sourceDetectables = new ArrayList<String>();
            
            sourceDetectables.add(roleName);
        }
        
        public String getSourceDetectablesCsv() {
            return Util.listToCsv(sourceDetectables);
        }
        
        public String getSourceAssignablesCsv() {
            return Util.listToCsv(sourceAssignables);
        }
        
        public List<String> getSourceDetectables() {
            return sourceDetectables;
        }

        public void setSourceDetectables(List<String> sourceDetectables) {
            this.sourceDetectables = sourceDetectables;
        }

        public void updateDuringLinkRefresh(boolean b) {
            writtenDuringLinkUpdate = b;
        }
        public boolean updatedDuringLinkRefresh() {
            return writtenDuringLinkUpdate;
        }
    }
    
    /**
     * 
     * Class to help hold the three idnetifying parts of the 
     * link object including the native identity, the instance
     * and the application.
     * 
     * These are kept as part of the Entitlizer state if we
     * are refreshing all of the links.  The data stored
     * here helps us identify any orphaned entitlements
     * that exist.
     * 
     * We keep a HashSet of these in memory as we refresh
     * when enabled, then we check the in-memory list with
     * against the values we find in the db. 
     * 
     * Because of we perform "contains" on the hashset 
     * its important to implement equals and hashCode and
     * keep them in sync with any model changes.
     * 
     * @author Dan.Smith
     *
     */
    private class LinkInfo {
        
        /**
         * Hibernate Id of the application for the Link.
         */
        private String appId;
        
        private String nativeIdentity;

        private String instance;
        
        public LinkInfo(Link link) {
            nativeIdentity = link.getNativeIdentity();
            instance = link.getInstance();
            Application application = link.getApplication();
            if ( application != null) 
                appId = application.getId();
        }
        
        public LinkInfo(String app, String nativeIdentity, String instance) {
            this.nativeIdentity = nativeIdentity;
            this.instance = instance;
            appId = app;
        }
        
        public String getAppId() {
            return appId;
        }

        public String getNativeIdentity() {
            return nativeIdentity;
        }

        public String getInstance() {
            return instance;
        }

        @Override
        public boolean equals(Object o) {
            if ( o != null && o instanceof LinkInfo ) {
                LinkInfo incomming = (LinkInfo)o;
                if ( Util.nullSafeEq(getNativeIdentity(), incomming.getNativeIdentity()) && 
                     Util.nullSafeEq(appId, incomming.appId ) &&
                     // null instances are equal
                     Util.nullSafeEq(instance, incomming.instance, true)) {
                    return true;
                }
            }            
            return false;
        }
        
        @Override
        public int hashCode() {
            final int PRIME = 41;
            int niHash = ( getNativeIdentity() != null ) ? getNativeIdentity().hashCode() : 0;
            int appIdHash = ( getAppId() != null ) ? getAppId().hashCode() : 0;
            int instanceHash = ( getInstance() != null ) ? getInstance().hashCode() : 0;
            
            int result = 1;
            result = PRIME * result + niHash;
            result = PRIME * result + appIdHash;
            result = PRIME * result + instanceHash;
            return result;
        }
    }

}
