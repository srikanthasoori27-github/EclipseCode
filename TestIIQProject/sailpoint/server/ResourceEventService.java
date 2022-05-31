/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service object that does periodic processing of ResourceEvent 
 * objects that have been stored in the database. This was originally
 * part of SMListenerService but it was factored out because it
 * is generic and can handle events from any form of interceptor
 * or custom web request.
 *
 * It is important that intercepted events be queued and processed
 * in a service thread so that the speed of handling the events
 * does not cause the interception thread to clog.
 * 
 * Author: Jeff
 *
 * This service is typcially running all the time, whereas 
 * SMListenerService is optional.
 *
 * NOTE: Since processing the events could take time, we really
 * need to think about launching worker threads to handle them
 * so we don't bog down the Servicer thread.  Now that we've
 * made Request more general consider converting these into 
 * Request objects and letting RequestProcessor deal with the
 * thread pool.  
 * 
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Aggregator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ConnectorUtil;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceEvent;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


public class ResourceEventService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the ServiceDefinition object that configures this service,
     * and the name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "ResourceEvent";

    /**
     * Configuration attriubute that can be used to disable refresh
     * after processing.  Usually you don't want this.
     */
    public static final String ATT_NO_REFRESH = "noRefresh";
    
    /** If we get a change interception on the account, go back to the aggregator
     *and pull it from the connector so that associated transformations and rules are applied.
     */
    public static final String GET_ENTIRE_ACCOUNT = "getEntireAccount";
    
    /**
     * If we get a change interception on a group, go back to the aggregator
     * and pull it from the connector so that associated transformations and rules are applied.
     */
    public static final String GET_ENTIRE_GROUP = "getEntireGroup";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ResourceEventService.class);

    /**
     * Flag from the configuration indicating that we should NOT refresh
     * each Identity after updating.
     */
    boolean _noRefresh;

    /**
     * Options to the refresh process used when _noRefresh is false.
     * If not set default options will be used, 
     * see PlanEvaluator.ARG_REFRESH_OPTIONS.
     */
    Map _refreshOptions;

    // misc statistics
    
    int _total;
    int _cacheCount;
    int _errors;

    // internal state

    /**
     * Current context.
     */
    SailPointContext _context;

    /**
     * Provisioner we use to implement changes to existing account links.
     */
    Provisioner _provisioner;

    /**
     * Aggregator we use to implement discovery of new accounts.
     */
    Aggregator _aggregator;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public ResourceEventService() {
        _name = NAME;
        _interval = 60 * 1;
    }

    /**
     * This constructor is used only for unit testing.
     */
    public ResourceEventService(SailPointContext con) throws GeneralException {
        _context = con;
        configure(con);
        prepare(con);
    }

    /**
     * Process configuration optins.
     */
    @Override
    public void configure(SailPointContext context) {

        // should always be set
        if (_definition != null) {

            _noRefresh = _definition.getBoolean(ATT_NO_REFRESH);

            // assume everything's a refresh option except ours
            Attributes<String,Object> atts = _definition.getAttributes();
            _refreshOptions = new Attributes<String,Object>(atts);
            _refreshOptions.remove(ATT_NO_REFRESH);

            // if we have nothing, null this so PlanEvaluator will
            // use default options
            if (_refreshOptions.isEmpty()) 
                _refreshOptions = null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Consumer
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Initialize execution context.
     * Factored out of execute() so we can use it in the unit 
     * test constructor.
     */
    private void prepare(SailPointContext context) {

        _context = context;

        // This can maintain some large cahces if refresh is enabled
        // so reuse it for every event.
        _provisioner = new Provisioner(context);
        // this is the magic flag that causes us to update
        // the cube without sending provisioning requests back
        // to the managed msystems
        _provisioner.setLocalUpdate(true);
        // set optional refresh options
        _provisioner.setDoRefresh(!_noRefresh);
        _provisioner.setRefreshOptions(_refreshOptions);
        
        // set a flag to tell the PlanEvaluator that
        // we these are intercepted native 
        // changes. This will create native change
        // events, to simmulate what would have
        // happened had we aggregated the changes
        _provisioner.setNativeChange(true);

        // This can also maintain the same large caches
        Attributes<String,Object> args = new Attributes<String,Object>();
        if (_refreshOptions != null)
            args.putAll(_refreshOptions);
        else {
            // the defaults
            args.put("promoteAttributes", "true");
            args.put("correlateEntitlements", "true");
        }
        _aggregator = new Aggregator(context, args);
    }

    /**
     * Called each execution interval.
     * When called for the first time we'll start the listener threads.
     *
     * Each duty cycle we'll consume the change events and do a targeted
     * aggregation.  At the moment we'll try to do all the pending events
     * in this cycle, eventually we may want a governor.
     *
     * Note that this doesn't bother with object locking because we're
     * assuming there can be only one machine that listens for events
     * and processes events.  While there always has to be a single
     * machine listening, we could have all machines processing the events
     * and get some parallelism.  Think about that...
     */
    public void execute(SailPointContext context) throws GeneralException {

        log.info("SMListenerService executing");

        prepare(context);

        // query for the objects that came in since the last cycle
        List<String> props = new ArrayList<String>();
        props.add("id");
            
        Iterator<Object[]> result = context.search(ResourceEvent.class, null, props);
        // materialize the entire result so we don't have to depend
        // on cursor stability
        List<String> ids = getIds(result);

        int total = 0;
        int cacheCount = 0;

        if ( ids != null ) {
            for (String id : ids) {
    
                ResourceEvent event = context.getObjectById(ResourceEvent.class, id);
                if (event == null) {
                    log.warn("Event evaporated during processing!\n");
                }
                else {
                    _total++;
                    processEvent(event);
                    // It is important to keep the Hibernate cache clean,
                    // decache each event after processing, but we also
                    // need to a full periodic decache
                    context.decache(event);
                    
                    context.removeObject(event);
                    context.commitTransaction();
                    _cacheCount++;
                    if (_cacheCount >= 100) {
                        context.decache();
                        _cacheCount = 0;
    
                    }
                }
            }
        }

        log.info("Processed " + Util.itoa(_total) + " events");
    }

    private List<String> getIds(Iterator<Object[]> result) {

        List<String> ids = null;
        while (result.hasNext()) {
            String id = (String)(result.next()[0]);
            if (ids == null)
                ids = new ArrayList<String>();
            ids.add(id);
        }
        return ids;
    }

    /**
     * Process one resource event.
     * NOTE: From here on down this is generic code could be used by
     * any change listener.  Should factor out an AbstractChangeListener
     * service so we can  share.
     *
     * The target Identity in the plan is not required or expected.
     * We normally correlate the AccountRequest to a Link and then find
     * the parent Identity.  If there is no corresponding Link we 
     * go through the full aggregation process.
     *
     * This is made public for unit testing.
     */
    public void processEvent(ResourceEvent event) {

        try {
            Application app = event.getApplication();
            if (app == null)
                log.error("Missing application");
            else {
                ProvisioningPlan plan = event.getPlan();
                if (plan == null) 
                    log.error("Missing plan");
                else {
                    List<AccountRequest> accounts = plan.getAccountRequests();
                    if (accounts != null) {
                        for (AccountRequest account : accounts) {
                            processEvent(app, account); 
                        }
                    }

                    List<ObjectRequest> objects= plan.getObjectRequests();
                    if (objects != null) {
                        for (ObjectRequest obj : objects)
                            processEvent(app, obj);
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error("Exception processing event");
            log.error(t);
            log.error(Util.stackToString(t));
            _errors++;
        }
    }

    /**
     * Process one account request from a ResourceEvent.
     */
    private void processEvent(Application app, AccountRequest req) 
        throws Exception {

        Identity ident = getTargetIdentity(app, req);
        ResourceObject ro = null;
        if (ident != null) {
            // This is an update to an existing identity.
            
            boolean getEntireAccount = _definition.getBoolean(GET_ENTIRE_ACCOUNT);
            boolean isDelete = req.getOp() == ObjectOperation.Delete;
            
            // We assume that there don't need to be any transformations on the account,
            // but some people think it's necessary.  Get the account again through
            // the connector and then aggregate so that all of the associated
            // rules will run.  The down side is that it's another round trip.
            // If this is an account deletion, then we need to send it to
            // the provisioner.
            if (getEntireAccount && !isDelete) {
                String acctSchema = Connector.TYPE_ACCOUNT;
                String instance = null;
                String nativeIdentity = req.getNativeIdentity();
                Connector connector = ConnectorFactory.getConnector(app, instance);

                try {
                    ro = connector.getObject(acctSchema, nativeIdentity, null);
                    
                    if (log.isInfoEnabled()) {
                        log.info("Aggregating ResourceObject for an existing identity/account: ");
                        log.info(ro.toXml());
                    }
                } catch (ObjectNotFoundException o) {
                    // didn't find the object.  Maybe it's been deleted since the intercept.
                    if (log.isWarnEnabled()) {
                        log.warn("Did not find the account while attempting to acquire the complete " +
                                 "updated object: [" + nativeIdentity + "] on [" + app.getName() + "]");
                    }
                }
                
                if (null != ro) {
                    _aggregator.aggregate(app, ro);
                } else {
                    if (log.isWarnEnabled()) {
                        log.warn("Can not aggregate updated account because there is no resource " +
                                 "object available for [" + nativeIdentity + "] on [" + app.getName() + "]");
                    }
                }
            } else {

                // though we have an outer plan, fake up temporary plans
                // for each account
                // !! what about options should we copy them from the 
                // original plan?
                ProvisioningPlan plan = new ProvisioningPlan();
                plan.setIdentity(ident);
                plan.add(req);
                // AccountRequest.application is optional since we have
                // Application reference in the ResourceEvent, but Provisioner
                // needs it
                req.setApplication(app.getName());
    
                if (log.isInfoEnabled()) {
                    log.info("Applying plan to identity: " + ident.getName());
                    log.info(plan.toXml());
                }
    
                _provisioner.execute(plan);
            
            }
        }
        else if (req.getOp() != ObjectOperation.Delete) {

            // Assume this is a new account, make it look like a single item
            // aggregationm.  
            ro = convertResourceObject(app, req);

            if (log.isInfoEnabled()) {
                log.info("Aggregating new ResourceObject:");
                log.info(ro.toXml());
            }

            _aggregator.aggregate(app, ro);
        }
        else {
            // If we got an event for Delete but couldn't find the matching
            // Link just ignore it
            log.warn("Delete request for unknown Link: " + 
                     req.getApplication() + ":" + 
                     req.getNativeIdentity());
        }
    }

    /**
     * Locate the Identity for one account request.
     */
    private Identity getTargetIdentity(Application app, AccountRequest req) 
        throws GeneralException {

        Identity target = null;

        // Currently the ResourceEvent is specific to an Application
        // referenced in the event, we do not need the same
        // application to be in the plan.  May want to change this
        // if we can get changes from several managed systems in one event.
        String appname = req.getApplication();
        if (appname != null && !appname.equals(app.getName()))
            log.warn("Mismatched app names: " + app.getName() + ", " + appname);

        String id = req.getNativeIdentity();
        if (id == null)
            log.error("Event with no account identity");

        else {
            String instance = req.getInstance();
                        
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("application", app));
            ops.add(Filter.ignoreCase(Filter.eq("nativeIdentity", id)));
            if (instance != null)
                ops.add(Filter.eq("instance", instance));

            List<Link> links = _context.getObjects(Link.class, ops);
            if (links == null || links.size() == 0) {
                // this is not necessarily a problem, it just
                // means this is a new account
                log.info("Unable to find Link matching resource event: " + 
                         app.getName() + ":" + id);
            }
            else {
                if (links.size() > 1) {
                    // not supposed to happen
                    log.warn("More than one Link matched a resource event");
                }
                Link link = links.get(0);
                target = link.getIdentity();
                if (target == null)
                    log.error("Event correlated to orphaned link!");
            }
        }

        return target;
    }

    /**
     * If we couldn't find an existing Link to modify, convert the
     * request to a ResourceObject and call the aggregator.
     */
    private ResourceObject convertResourceObject(Application app, 
                                                 AbstractRequest req)
        throws Exception {

        ResourceObject obj = new ResourceObject();

        String identityAttribute = null;
        String displayAttribute = null;

        // this is kludgey, we'll assume from the request
        // class whether we're dealing with an account or a group
        // ideally there would be an object type in the request,
        // but that's always going to be missed since we really
        // only have two types
        Schema schema;
        if (req instanceof AccountRequest)
            schema = app.getAccountSchema();
        else
            schema = app.getSchema(Application.SCHEMA_GROUP);

        if (schema != null) {
            identityAttribute = schema.getIdentityAttribute();
            displayAttribute = schema.getDisplayAttribute();
        }

        // This should be set, though we'll also allow it to be in
        // an AttributeRequest for the identity attribute
        obj.setIdentity(req.getNativeIdentity());

        //Set the objectType on the ResourceObject.
        obj.setObjectType(req.getType());

        List<AttributeRequest> atts = req.getAttributeRequests();
        if (atts != null) {
            for (AttributeRequest att : atts) {
                // ignore op, it must be Set
                String name = att.getName();

                if (Connector.ATTR_DIRECT_PERMISSIONS.equals(name)) {
                    // these have to come in with PermissionRequests
                    log.warn("AttributeRequest tried to set directPermissions, ignoring");
                }
                else if (name != null)  {
                    Object value = att.getValue();
                    obj.put(name, value);

                    // these get promoted
                    if (name.equals(identityAttribute)) {
                        if (value != null) {
                            String id = value.toString();
                            if (obj.getIdentity() == null)
                                obj.setIdentity(id);

                            // if appears in both places it should be the same
                            String reqid = req.getNativeIdentity();
                            if (reqid != null && !reqid.equals(id))
                                log.warn("Mismatched native identity: " + 
                                         reqid + ", " + id);
                        }
                    }
                    else if (name.equals(displayAttribute)) {
                        if (value != null) {
                            String dname = value.toString();
                            if (obj.getDisplayName() == null)
                                obj.setDisplayName(dname);
                            else
                                log.warn("Multiple display names");
                        }
                    }
                }
            }
        }

        List<PermissionRequest> perms = req.getPermissionRequests();
        if (perms != null) {
            List<Permission> plist = null;
            for (PermissionRequest preq : perms) {
                String target = preq.getTarget();
                if (target != null)  {
                    Permission perm = new Permission();
                    perm.setTarget(target);
                    perm.setRights(preq.getRights());
                    if (plist == null)
                        plist = new ArrayList<Permission>();
                    plist.add(perm);
                }
            }
            if (plist != null)
                obj.put(Connector.ATTR_DIRECT_PERMISSIONS, plist);
        }

        // If we were doing a real aggregation, ConnectorProxy would
        // run the customizationrule.  Since we're not using
        // ConnectorProxy we have to do it.
        // Rule needs a Connector though I suspect most ignore that
        if (app.getCustomizationRule() != null) {
            Connector con = ConnectorFactory.getConnector(app, null);
            obj = ConnectorUtil.runCustomizationRule(_context, con, obj, null);
        }

        // connector proxy does this after customization rule
        ConnectorUtil.mapNamesFromConnector(app, obj);

        return obj;
    }

    /**
     * Process one object request from a ResourceEvent.
     * In practice these are always used for groups.  Interceptions
     * for accounts end up being converted into an AccountRequest that
     * is applied to a Link in an Identity.
     *
     * Group interceptions end up being converted into an ObjectRequest
     * that is applied to an ManagedAttribute object, there is no
     * notion of a containing Identity.
     *
     */
    private void processEvent(Application app, ObjectRequest req) 
        throws Exception {
        ResourceObject ro = null;

        // The schema must have a group attribute defined, 
        // if not then we ignore the event.
        AttributeDefinition att = app.getGroupAttribute();
        if (att == null) {
            // Make this soft so we don't pollute the 
            // error log table with gobs of errors while
            // the application is being fixed?
            log.warn("Application " + app.getName() + 
                     " has no defined group attribute");
        } else {
            ManagedAttribute group = ManagedAttributer.get(_context, app, false,
                                                           att.getName(),
                                                           req.getNativeIdentity());

            if (group != null) {
                // This is an update to an existing group.
                
                boolean getEntireGroup = _definition.getBoolean(GET_ENTIRE_GROUP);
                boolean isDelete = req.getOp() == ObjectOperation.Delete;
                
                // We assume that there don't need to be any transformations on the object,
                // but some people think it's necessary.  Get the group again through
                // the connector and then aggregate so that all of the associated
                // rules will run.  The down side is that it's another round trip.
                // If this is a delete then send it to the provisioner.
                if (getEntireGroup && !isDelete) {
                    String groupSchema = req.getType();
                    String instance = null;
                    String nativeIdentity = req.getNativeIdentity();
                    Connector connector = ConnectorFactory.getConnector(app, instance);

                    try {
                        ro = connector.getObject(groupSchema, nativeIdentity, null);
                        
                        if (log.isInfoEnabled()) {
                            log.info("Aggregating ResourceObject for an existing group/object: ");
                            log.info(ro.toXml());
                        }
                    } catch (ObjectNotFoundException o) {
                        // didn't find the object.  Maybe it's been deleted since the intercept.
                        if (log.isWarnEnabled()) {
                            log.warn("Did not find the group while attempting to acquire the complete " +
                                     "updated object: [" + nativeIdentity + "] on [" + app.getName() + "]");
                        }
                    }
                    
                    if (null != ro) {
                        _aggregator.aggregateGroup(app, ro);
                    } else {
                        if (log.isWarnEnabled()) {
                            log.warn("Can not aggregate updated group because there is no resource " +
                                     "object available for [" + nativeIdentity + "] on [" + app.getName() + "]");
                        }
                    }
                    
                } else {
                    // Just get the changes and apply them directly to the object.
                    
                    // though we have an outer plan, fake up temporary plans
                    // for each account
                    // !! what about options should we copy them from the 
                    // original plan?
                    ProvisioningPlan plan = new ProvisioningPlan();
                    // we don't have this and don't really need it, 
                    // IIQEvaluator can do the same lookup we just did
                    //plan.setGroup(group);
                    plan.addObjectRequest(req);
    
                    // AccountRequest.application is optional since we have
                    // Application reference in the ResourceEvent, but Provisioner
                    // needs it
                    req.setApplication(app.getName());
    
                    if (log.isInfoEnabled()) {
                        log.info("Applying plan to object: " + group.getName());
                        log.info(plan.toXml());
                    }
    
                    _provisioner.execute(plan);
                }
            }
            else if (req.getOp() != ObjectOperation.Delete) {

                // Assume this is a new object, make it look like a single item
                // aggregationm.  
                ro = convertResourceObject(app, req);

                if (log.isInfoEnabled()) {
                    log.info("Aggregating new ResourceObject:");
                    log.info(ro.toXml());
                }

                _aggregator.aggregateGroup(app, ro);
            }
            else {
                // If we got an event for Delete but couldn't find the matching
                // Link just ignore it
                log.warn("Delete request for unknown group: " + 
                         req.getApplication() + ":" + 
                         req.getNativeIdentity());
            }
        }
    }

}
