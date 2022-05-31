/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */


/**
 * A class encapsulating various maintenance of the IdentityEntitlement
 * objects added in 6.0.
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.provisioning.AssignmentExpander;
import sailpoint.service.LinkService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * An API class that is deals with adorning LCM Request information
 * to each IdentityRequest.  This class is called during the LCM
 * request workflow and updated using the IdentityRequest 
 * model.
 * 
 * Extended to allow us to pre-create entitlements from projects
 * created from scheduled assignments.
 *
 * TODO:
 * 1) How about expansions during a request, can that wait until 
 *    next refresh?
 *    
 * @See {@link AbstractEntitlizer}
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * @since 6.0
 *
 */
public class RequestEntitlizer extends AbstractEntitlizer {
    
    private static Log log = LogFactory.getLog(RequestEntitlizer.class);
    
    /**
     * Constructor that take a SailPointContext.
     * 
     * @param context
     */
    public RequestEntitlizer(SailPointContext context) {
        super(context);         
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Methods to update IdentityEntitlement request meta data
    //
    //////////////////////////////////////////////////////////////////////////    
    
    /**
     * Set the current request details on the entitlement.
     * 
     * Use the identity request items to find the matching entitlements
     * so we can set the correct access request on each entitlement.
     * 
     * Typically the assignements on the Identity get configured during
     * plan evaluation, but for cases where we might have manual workitems
     * we need to make sure this code adjusts the assignements correctly.
     * 
     * @param ir
     * @throws GeneralException
     */
    public void setCurrent(IdentityRequest ir) 
        throws GeneralException {

        if ( ir != null && ir.getItems() != null ) {

            List<IdentityRequestItem> items = ir.getItems();
            String identityId = ir.getTargetId();
            Filter identityFilter = Filter.eq("identity.id", identityId);            
            // find interesting items and annotate the entitlements
            for (IdentityRequestItem item : items) {
                if (item != null && 
                    isEntitlement(item) && 
                    itemIsApprovedOrHasNoApprovals(ir, item) &&
                    item.isProvisioningComplete()) {
                    
                    IdentityEntitlement entitlement = resolveEntitlement(item);
                    if ( entitlement == null ) {
                        // try and search    
                        Filter itemFilter = buildFilterFromItem(item);
                        if (itemFilter != null) {
                            entitlement = _context.getUniqueObject(IdentityEntitlement.class,
                                                                                       Filter.and(identityFilter, itemFilter));
                        }
                    }
                    if (entitlement != null) {
                        IdentityRequestItem requestItem = entitlement.getPendingRequestItem();
                        String currentId = (requestItem != null) ? requestItem.getId() : null;
                        // If this is the same request that's marked pending
                        // fix it up.
                        if (Util.nullSafeCompareTo(item.getId(), currentId) == 0) {
                            entitlement.setPendingRequestItem(null);
                        }
                        entitlement.setRequestItem(item);                        
                        //
                        // Refresh the account id in case it had changed                        
                        //
                        //Item is not updated with nativeId for scheduled assignment. Don't set null
                        if (Util.isNotNullOrEmpty(item.getNativeIdentity())) {
                            entitlement.setNativeIdentity(item.getNativeIdentity());
                        }

                        
                        QueryOptions ops = new QueryOptions();
                        ops.add(Filter.eq("application", entitlement.getApplication()));
                        ops.add(Filter.ignoreCase(Filter.eq("nativeIdentity", entitlement.getNativeIdentity())));
                        if ( entitlement.getInstance() != null ) 
                            ops.add(Filter.eq("instance", entitlement.getInstance()));
                            
                        Iterator<Object[]> rows = _context.search(Link.class, ops, "displayName");                        
                        if ( rows != null ) {
                            int i = 0;
                            while ( rows.hasNext() ) {
                                if ( ++i > 1 ) 
                                   log.error("Found more then one link for the entitlement!" + entitlement.toXml());
                                Object[] row = rows.next();
                                if ( row != null && row.length == 1 ) {
                                    String displayName = (String)row[0];
                                    if ( displayName != null ) {
                                        entitlement.setDisplayName(displayName);
                                    }
                                }                                
                            }
                        }

                        // we've succeeded make sure we have the assignment where
                        // applicable
                        Identity id = entitlement.getIdentity();
                        if ( id != null ) {                            
                            //
                            // djs : we have to be careful the assignment flag is set
                            // correctly on the entitlement.  It should always be 
                            // set if we have found the assignment in the plan.                                                        
                            AttributeRequest attr = getAssignedAttributeRequest(ir, item);                            
                            if ( attr != null ) {                                
                                if ( !Util.nullSafeEq(attr.getOp(), Operation.Remove) ) {
                                    entitlement.setAssigned(true);
                                } else {
                                    entitlement.setAssigned(false);
                                }
                            } 

                            boolean toAdd = false;
                            boolean toRemove = false;
                               
                            // Stick on any assignments that were not already found on the
                            // identity
                            if ( entitlement.isAssigned() && 
                                 !isAssignedOnIdentity(entitlement, id.getAttributeAssignments()) ) {
                                toAdd = true;   
                            }
                            
                            if ( !entitlement.isAssigned() && 
                                 isAssignedOnIdentity(entitlement, id.getAttributeAssignments()) ) {
                                toRemove = true;   
                            }
                            if ( toAdd || toRemove ) {
                                // iiqpb-418 getting weird Hiberate cache problems during locking, probably
                                // some misconnected things after evicting the Identity.  The transaction
                                // state is messy and several levels deep at this point, normally I would add
                                // a decache here, but that is likely to have consequences here.  Do the
                                // identity update in a private session.  This really should never be happening
                                // AttributeAssignments should be added unconditionally by IIQEvaluator - jsl
                                SailPointContext idcontext = SailPointFactory.createPrivateContext();
                                try {
                                    Identity ident = ObjectUtil.lockIdentity(idcontext, id.getId());
                                    if (ident != null) {
                                        try {
                                            AttributeAssignment assignment = new AttributeAssignment(entitlement);
                                            if (attr != null) {
                                                //bug23272
                                                assignment.setStartDate(attr.getAddDate());
                                                assignment.setEndDate(attr.getRemoveDate());
                                            }

                                            if ( toAdd )
                                                ident.add(assignment);
                                            if ( toRemove)
                                                ident.remove(assignment);
                                        
                                            ident.validateAttributeAssignments(idcontext);
                                        }
                                        finally {
                                            ObjectUtil.unlockIdentity(idcontext, ident);
                                        }
                                    }
                                }
                                finally {
                                    SailPointFactory.releasePrivateContext(idcontext);
                                }
                            }
                        }
                    
                    }
                    else {                        
                        if ( log.isDebugEnabled())
                            log.debug("Unable to find entitlement for item " + item.toXml());
                    }
                }
                else if ( items != null && (item.isProvisioningFailed() || ir.isTerminated())) {
                    IdentityEntitlement entitlement = resolveEntitlement(item);
                    if ( entitlement != null ) {
                        if ( isAssignedOnIdentity(entitlement, entitlement.getIdentity().getAttributeAssignments() ) ) {
                            // need to remove it from identity??
                        }
                        boolean removeEntitlement = true;
                        ProvisioningPlan planFragment = item.getProvisioningPlan();
                        //A failed remove means we don't remove the entitlement!
                        if (planFragment != null) {
                            List<AccountRequest> requestFragments = planFragment.getAccountRequests();
                            if (!Util.isEmpty(requestFragments)) {
                                for (AccountRequest acctReq : requestFragments) {
                                    for (AttributeRequest attReq : Util.iterate(acctReq.getAttributeRequests())) {
                                        if (Operation.Remove.equals(attReq.getOperation())) {
                                            removeEntitlement = false;
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (removeEntitlement) {
                            Terminator terminator = new Terminator(_context);
                            terminator.deleteObject(entitlement);
                        }
                    }
                }
                else {
                     if ( log.isDebugEnabled() ) {
                        log.debug("Item was null, not an entitlement, not approved to not provisioning complete" + item.toXml());
                    }
                }
            } 
        }
    }

    private boolean itemIsApprovedOrHasNoApprovals(
            IdentityRequest ir, IdentityRequestItem item) throws GeneralException {
        if(item.isApproved()) {
            return true;
        }

        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("identityRequestId", ir.getName()));
        ops.addFilter(Filter.eq("type", WorkItem.Type.Approval));
        SailPointContext context = SailPointFactory.getCurrentContext();
        return (context.countObjects(WorkItem.class, ops) == 0);
    }
    
    /**
     * Dig through the provisioning plan and find the item in the
     * attribute request list.  If found look and see if the 
     * attribute's name and value are marked with the assigned flag.
     * 
     * If so return true, otherwise return false
     * 
     * @param request
     * @param item
     * 
     * @return true if the plan has an assigned flag, false if it does not
     */
    private AttributeRequest getAssignedAttributeRequest(IdentityRequest request, IdentityRequestItem item) {
       
        ProvisioningProject project = request.getProvisionedProject();
        if (project != null ) {
            List<ProvisioningPlan> plans = project.getPlans();
            if ( plans != null ) {
                for ( ProvisioningPlan plan : plans ) {
                    List<AccountRequest> acctReqs = plan.getAccountRequests(item.getApplication());
                    if ( acctReqs != null ) {
                        for ( AccountRequest acctReq : acctReqs ) {
                            String nativeIdentity = acctReq.getNativeIdentity();
                            String instance = acctReq.getInstance();
                            if ( Util.nullSafeEq(nativeIdentity, item.getNativeIdentity()) && 
                                 Util.nullSafeEq(instance, item.getInstance(), true) ) {
                                List<AttributeRequest> attrs = acctReq.getAttributeRequests(item.getName());  
                                if ( attrs == null ) continue;
                                    
                                for ( AttributeRequest attr : attrs  ) {
                                    if ( attrs != null ) {
                                        Attributes<String,Object> map = attr.getArguments();
                                        if ( map != null ) {
                                            if ( Util.getBoolean(map, ProvisioningPlan.ARG_ASSIGNMENT) && valuesMatch(attr, item)) {
                                                return attr;   
                                            }                                            
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //If nothing in the integration plans, check the masterPlan. In the case of sunrised
            //ent, no integration plan will exist. -rap
            ProvisioningPlan master = project.getMasterPlan();
            if (master != null) {
                List<AccountRequest> acctReqs = master.getAccountRequests(item.getApplication());
                if ( acctReqs != null ) {
                    for ( AccountRequest acctReq : acctReqs ) {
                        String nativeIdentity = acctReq.getNativeIdentity();
                        String instance = acctReq.getInstance();
                        if ( Util.nullSafeEq(nativeIdentity, item.getNativeIdentity(), true) &&
                                Util.nullSafeEq(instance, item.getInstance(), true) ) {
                            List<AttributeRequest> attrs = acctReq.getAttributeRequests(item.getName());
                            if ( attrs == null ) continue;

                            for ( AttributeRequest attr : attrs  ) {
                                if ( attrs != null ) {
                                    Attributes<String,Object> map = attr.getArguments();
                                    String assignmentId = attr.getAssignmentId();
                                    if ( map != null ) {
                                        if ( Util.getBoolean(map, ProvisioningPlan.ARG_ASSIGNMENT) &&
                                                valuesMatch(attr, item) &&
                                                Util.nullSafeEq(item.getAssignmentId(), assignmentId, true)) {
                                            return attr;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        return null;
    }
    
    /**
     * Look for values in the IdentityRequestItem in the attribute request.
     * The attribute request here should be something that has the 
     * assignment flag enabled in the attribute map.
     * 
     * @param req
     * @param item
     * @return true if the value(s) in the IdentityRequestItem are found in the AttributeRequest
     */
    private boolean valuesMatch(AttributeRequest req, IdentityRequestItem item) {
        
         if ( Util.size(item.getValueList()) == 0 )  
             return false;
     
         List<String> itemVals = new ArrayList<String>(item.getValueList());         
         List<Object> vals = Util.asList(req.getValue());         
         if ( vals  != null ) {
             for ( Object val : vals ) {
                 if ( val == null ) 
                     continue;
                 String strVal = val.toString();
                 if ( itemVals.contains(strVal) ) {
                     itemVals.remove(strVal);
                 }
             }
         }         
         if ( itemVals.size() == 0 )
             return true;
         
         return false;
    }
    
    private String ATT_ENTITLEMENT_ID = "identityEntitlementId";
    
    /**
     * Use our breadcrumb on the item to look up the entitlement by ID.
     * @param item
     * @return
     * @throws GeneralException
     */
    private IdentityEntitlement resolveEntitlement(IdentityRequestItem item) 
        throws GeneralException {
        
        IdentityEntitlement entitlement = null;
        String entitlementId = (String)item.getAttribute(ATT_ENTITLEMENT_ID);
        if ( entitlementId != null ) {
            entitlement = _context.getObjectById(IdentityEntitlement.class, entitlementId);
        }         
        return entitlement;
    }
    
    public void setPending(IdentityRequest ir) 
    		throws GeneralException {
    	setPending(ir, null);
    }
    
    /**
     * Called when a request has been provisioned and awaiting
     * verification.  Its typically called post approval,
     * so before adding new entitlements make sure the item
     * can be created.  We must check it's approvalSchema
     * and if it was approved and it wasn't rejected.
     * 
     * @param ir
     * @throws GeneralException
     * 
     */
    public void setPending(IdentityRequest ir, String approvalScheme) 
        throws GeneralException {
        
        if ( ir != null ) {
            List<IdentityRequestItem> items = ir.getItems();
            if ( items != null ) {                
                // Hack to work around inner join that will be formed if we query on
                // identity id.
                String identityId = ir.getTargetId(); 
                
                Identity mockup =  new Identity();
                mockup.setId(identityId);
                Filter identityFilter = Filter.eq("identity", mockup);                
                for ( IdentityRequestItem item : items ) {
                	//IIQETN-4875 This used to just assume that anything not rejected was approved
                	//but that is not true with split provisioning.  We can come through here with 
                	//IdentityRequestsItems that have not been approved. 
                	//IdentityRequestsItems that have not been approved.
                	boolean create = isEntitlement(item) && !item.isRejected();
                	//when the approvalScheme = "none" we can not check isApproved()
                	if (null == approvalScheme || !approvalScheme.equalsIgnoreCase("none")) {
                		create = create && item.isApproved();
                	}
                	if ( create) { 
                        // Approved items will be added
                        Filter itemFilter = buildFilterFromItem(item);
                        if ( itemFilter == null) {
                            if ( log.isDebugEnabled() ) 
                                log.debug("Unable to find item for " + itemFilter);                            
                            return;
                        }
                        
                        // If the identityId is null there won't be any existing entitlements
                        IdentityEntitlement entitlement = null;                        
                        if ( identityId != null ) {
                            entitlement = _context.getUniqueObject(IdentityEntitlement.class,
                                                                   Filter.and(identityFilter,itemFilter));
                        }
                        if ( entitlement == null ) {
                            entitlement = entitlementFromRequest(ir, item);
                            if ( entitlement != null )  {
                                entitlement.setPendingRequestItem(item);
                                _context.saveObject(entitlement);
                            }
                        } 
                            
                        if ( entitlement != null ) {       
                            // djs : store off the id of the entitlement to help us when 
                            // we update it because its completed
                            item.setAttribute(ATT_ENTITLEMENT_ID, entitlement.getId());
                        }
                    }
                }         
            }
        }        
    }
    
    /**
     * Called when a project is about to be provisioned and awaiting
     * verification.
     * 
     * @param project
     * @throws GeneralException
     * 
     */
    public void setPending(Identity identity, ProvisioningProject project) 
        throws GeneralException {
        
        if ( project != null ) {
            List<ProvisioningPlan> plans = project.getPlans();
            ProvisioningPlan masterPlan = project.getMasterPlan();
            Attributes<String, Object> masterPlanAttrs = null;
            if(masterPlan != null) {
                masterPlanAttrs = masterPlan.getArguments();
            }

            for( ProvisioningPlan plan : Util.iterate(plans)) {                
                if( plan.getTargetIntegration() != null ) {
                    List<AccountRequest> accountRequests = plan.getAccountRequests();
                    createEntitlementsForAccountRequests(identity, masterPlanAttrs, accountRequests);
                }
            }
        }        
    }
    
    private void createEntitlementsForAccountRequests(Identity identity, 
            Attributes<String, Object> masterPlanAttributes, List<AccountRequest> accountRequests) 
            throws GeneralException {
        Filter identityFilter = Filter.eq("identity", identity); 
        for( AccountRequest request : Util.iterate(accountRequests) ) {
            List<AttributeRequest> singleValuedAttributeRequests = getSingleValuedAttributeRequests(request.getAttributeRequests());
            for( AttributeRequest attributeRequest : Util.iterate(singleValuedAttributeRequests)) {
                if( isEntitlement(request, attributeRequest) ) { 
                    // Approved items will be added
                    Filter itemFilter = buildFilterFromAccountRequest(request, attributeRequest);
                    Filter itemNullNativeIdentityFilter = buildFilterFromAccountRequest(request, attributeRequest, true);
                    if ( itemFilter == null) {
                        if ( log.isDebugEnabled() ) 
                            log.debug("Unable to find item for " + itemFilter);                            
                        return;
                    }
                    
                    // If the identityId is null there won't be any existing entitlements
                    IdentityEntitlement entitlement = _context.getUniqueObject(IdentityEntitlement.class,
                                                               Filter.and(identityFilter,itemFilter));

                    if ( entitlement == null ) {
                        //Sigh, we pre-create entitlements sometimes before we have a native identity,
                        //take care of that and any affected attribute assignments.
                        entitlement = _context.getUniqueObject(IdentityEntitlement.class,
                                Filter.and(identityFilter,itemNullNativeIdentityFilter));
                        if (entitlement == null) {
                            entitlement = entitlementFromAccountRequest(identity, masterPlanAttributes, request, attributeRequest);
                            if ( entitlement != null )  {
                                _context.saveObject(entitlement);
                            }
                        } else {
                            if( entitlement.getNativeIdentity() == null) {
                                AttributeAssignment assignment = getAssignmentFromIdentity(entitlement, identity.getAttributeAssignments());
                                if (assignment != null) {
                                    //we need to update the assignment to have a native identity or else it will
                                    //get removed
                                    if (assignment.getNativeIdentity() == null) {
                                        assignment.setNativeIdentity(request.getNativeIdentity());
                                        _context.saveObject(identity);
                                    }
                                }
                                entitlement.setNativeIdentity(request.getNativeIdentity());
                                _context.saveObject(entitlement);
                            }
                        }
                    }
                        
                }
            }
        }
    }

    /*
     * This method breaks any multi-valued attribute requests in the list and splits them into single-valued ones
     * so that we build an IdentityEntitlement for each entitlement, rather than attempting to build one giant
     * IdentityEntitlement that contains all the values.  This is done because the IdentityEntitlement value column
     * simply can't handle more than one value at a time
     */
    private List<AttributeRequest> getSingleValuedAttributeRequests(List<AttributeRequest> attributeRequests) {
        List<AttributeRequest> singleValuedAttributeRequests = new ArrayList<AttributeRequest>();
        for( AttributeRequest attributeRequest : Util.iterate(attributeRequests)) {
            Object value = attributeRequest.getValue();
            if (value instanceof Collection) {
                singleValuedAttributeRequests.addAll(splitAttributeRequest(attributeRequest));
            } else {
                singleValuedAttributeRequests.add(attributeRequest);
            }
        }
        return singleValuedAttributeRequests;
    }

    private List<AttributeRequest> splitAttributeRequest(AttributeRequest multiValuedAttributeRequest) {
        List<AttributeRequest> splitRequests = new ArrayList<AttributeRequest>();
        Collection multiValue = (Collection)multiValuedAttributeRequest.getValue();
        for (Object singleValue : Util.iterate(multiValue)) {
            AttributeRequest splitRequest = (AttributeRequest)multiValuedAttributeRequest.clone();
            splitRequest.setValue(singleValue);
            splitRequests.add(splitRequest);
        }

        return splitRequests;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * Return true when the item is either and IIQ assigned and detectedRoles 
     * or attributes that are marked entitlement in the schema.
     * 
     */
    private boolean isEntitlement(IdentityRequestItem item) throws GeneralException {
        // Kludge alert!  IdentityRequestItem does not currently have any indication that an item is a permission.
        // This model should be updated to include this, but we are trying to keep these sorts of changes to a
        // minimum in a patch.  We know that everything that comes from the PAM source is a permission - which is an
        // entitlement - so we will return true.
        if (null != item.getIdentityRequest() && Source.PAM.name().equals(item.getIdentityRequest().getSource())) {
            return true;
        }

        String itemAttributeName = item.getName();
        String app = item.getApplication();
        if ( app == null  || itemAttributeName == null ) 
            return false;

        if ( "IIQ".equals(app) ) {
            if ( itemAttributeName.equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES) ||
                    itemAttributeName.equals(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) ) {
                return true;
            }
        } else {
            Application appObject = _context.getObjectByName(Application.class, app);
            if ( appObject != null ) {
                Schema account = appObject.getAccountSchema();
                if ( account != null ) {
                    List<String> entitlementAttributes = account.getEntitlementAttributeNames();
                    if ( entitlementAttributes != null  ) {
                        if ( entitlementAttributes.contains(itemAttributeName) )
                            return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 
     * Return true when the item is either and IIQ assigned and detectedRoles 
     * or attributes that are marked entitlement in the schema.
     * 
     */
    private boolean isEntitlement(AccountRequest request, AttributeRequest attributeRequest) throws GeneralException {
        String attributeName = attributeRequest.getName();
        String app = request.getApplication();
        if ( app == null  || attributeName == null ) 
            return false;

        if ( "IIQ".equals(app) ) {
            if ( attributeName.equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES) ||
                    attributeName.equals(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) ) {
                return true;
            }
        } else {
            Application appObject = _context.getObjectByName(Application.class, app);
            if ( appObject != null ) {
                Schema account = appObject.getAccountSchema();
                if ( account != null ) {
                    List<String> entitlementAttributes = account.getEntitlementAttributeNames();
                    if ( entitlementAttributes != null  ) {
                        if ( entitlementAttributes.contains(attributeRequest.getName()) )
                            return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Construct a filter for the unique entitlement that would
     * represent the IdentityRequestItem.
     *
     * @param item
     * @return
     * @throws GeneralException
     */
    private Filter buildFilterFromItem(IdentityRequestItem item)
        throws GeneralException {
        
        List<Filter> filters = new ArrayList<Filter>();
        
        String app = item.getApplication();
        if ( app != null ) {            
            if ( Util.nullSafeCompareTo(ProvisioningPlan.APP_IIQ, app) == 0 )  {                
                filters.add(Filter.isnull("application"));
            } else {
                Application appObject = _context.getObjectByName(Application.class, app);
                if ( appObject != null ) {
                    filters.add(Filter.eq("application", appObject));
                }
                String instance = item.getInstance();
                if ( instance != null ) {
                    // jsl - unlike Application.instance this one has a case insensitive index
                    filters.add(Filter.ignoreCase(Filter.eq("instance", instance)));
                }
                String accountId = item.getNativeIdentity();
                if ( accountId != null ) {
                    filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", accountId)));
                }
            }
        }
        String name = item.getName();
        if ( name != null ) 
            filters.add(Filter.ignoreCase(Filter.eq("name", name)));
        
        String value = item.getStringValue();
        if ( value != null )
            filters.add(Filter.ignoreCase(Filter.eq("value", value)));

        String assignmentId = item.getAssignmentId();
        if (assignmentId != null && !AssignmentExpander.isTemporaryAssignmentId(assignmentId)) {
            filters.add(Filter.eq("assignmentId", assignmentId));
        }

        return (filters != null) ? Filter.and(filters): null;
    }
    
    /**
     * Constructs a filter for the unique entitlement that would
     * represent the IdentityRequestItem.
     *
     * @param item
     * @return
     * @throws GeneralException
     */
    @Deprecated
    private Filter buildFilterFromItem(IdentityRequestItem item, boolean pendingItem)
            throws GeneralException {
        
        return buildFilterFromItem(item);
    }
    
    /**
     * Original call for buildFilterFromAccountRequest
     * @param request
     * @param attributeRequest
     * @return Filter
     * @throws GeneralException
     */
    private Filter buildFilterFromAccountRequest(AccountRequest request, AttributeRequest attributeRequest) throws GeneralException {
        return buildFilterFromAccountRequest(request, attributeRequest, false);
    }
    
    /**
     * Construct a filter for the unique entitlement that would
     * represent the attribute request.
     * 
     * @param request
     * @param attributeRequest
     * @param includeNullNativeIdentity
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    private Filter buildFilterFromAccountRequest(AccountRequest request, AttributeRequest attributeRequest, boolean includeNullNativeIdentity) 
        throws GeneralException {
        
        List<Filter> filters = new ArrayList<Filter>();
        
        String app = request.getApplication();
        if ( app != null ) {            
            if ( Util.nullSafeCompareTo(ProvisioningPlan.APP_IIQ, app) == 0 )  {                
                filters.add(Filter.isnull("application"));
            } else {
                Application appObject = _context.getObjectByName(Application.class, app);
                if ( appObject != null ) {
                    filters.add(Filter.eq("application", appObject));
                }
                String instance = request.getInstance();
                if ( instance != null ) {
                    // jsl - unlike Application.instance this one has a case insensitive index
                    filters.add(Filter.ignoreCase(Filter.eq("instance", instance)));
                }
                String accountId = request.getNativeIdentity();
                if ( accountId != null && !includeNullNativeIdentity) {
                    String assignmentId = attributeRequest.getAssignmentId();
                    if (Util.isNotNullOrEmpty(assignmentId)) {
                        //If entitlement request was scheduled without an account yet existing, nativeId
                        //would be null. Try nativeId, OR assignmentId -rap
                        filters.add(Filter.or(Filter.ignoreCase(Filter.eq("nativeIdentity", accountId)),
                                Filter.eq("assignmentId", assignmentId)));
                    } else {
                        filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", accountId)));
                    }
                }
                
            }
        }
        String name = attributeRequest.getName();
        if ( name != null ) 
            filters.add(Filter.ignoreCase(Filter.eq("name", name)));

        Object objVal = attributeRequest.getValue();
        String value = null;
        if( objVal != null && objVal instanceof String ) {
            value = objVal.toString();
        } else if( objVal != null && objVal instanceof List ) {
            value = Util.listToCsv((List)objVal);
        }
        
        if ( value != null )
            filters.add(Filter.ignoreCase(Filter.eq("value", value)));

        return (filters != null) ? Filter.and(filters): null;
    }
    
    /**
     * Build an IdentityEntitlement object from the request item and IdentityRequest 
     * object.
     * 
     * Just after approval, before provisioning we create the entitlement if not already
     * existing.
     * 
     * @param ir
     * @param item
     * @return
     * @throws GeneralException
     */
    private IdentityEntitlement entitlementFromRequest(IdentityRequest ir, 
                                                      IdentityRequestItem item) 
        throws GeneralException {
   
        if ( ir == null || ir.getTargetId() == null )
            return null;
               
        Identity id = _context.getObjectById(Identity.class, ir.getTargetId() );
        if ( id == null ) {
            log.warn("Unable to resolve identity from the IdentityRequest when building the IDentityEntitlement object.");
            return null;
        }
       
        IdentityEntitlement entitlement = new IdentityEntitlement();
        entitlement.setIdentity(id);
        
        String app = item.getApplication();
        if ( app != null ) {
            Application appObject = _context.getObjectByName(Application.class, app);
            if ( appObject != null ) {
                entitlement.setApplication(appObject);
            }
            String instance = item.getInstance();
            if ( instance != null ) {
                entitlement.setInstance(instance);
            }
            String accountId = item.getNativeIdentity();
            if ( accountId != null ) {
                entitlement.setNativeIdentity(accountId);
            }            
            // this not working?
            entitlement.setDisplayName(item.getDisplayName());
            if ( entitlement.getDisplayName() == null ) {
                LinkService linksService = new LinkService(_context);
                String displayName = linksService.getAccountDisplayName(id, app, instance, accountId);
                if(displayName !=null ) {
                    entitlement.setDisplayName(displayName);
                } else {
                    entitlement.setDisplayName(accountId);
                }
            }
        }
       
        String name = item.getName();
        if ( name != null ) 
            entitlement.setName(name);
            
        String value = item.getStringValue();
        if ( value != null )
            entitlement.setValue(value);

        Date startDate = item.getStartDate();
        if (startDate != null) {
            entitlement.setStartDate(startDate);
        }

        Date endDate = item.getEndDate();
        if (endDate != null) {
            entitlement.setEndDate(endDate);
        }

        String assignmentId = item.getAssignmentId();
        
        // The IdentityRequestItem has a plan fragment to reflect the item.  If we find an assignment id, use it.
        // If we don't set an assignment id, then we may not get an exact match later with the entitlement and request. 
        if (Util.isNullOrEmpty(assignmentId)) {
            ProvisioningPlan planFragment = item.getProvisioningPlan();
            if (planFragment != null) {
                List<AccountRequest> requestFragments = planFragment.getAccountRequests();
                if (!Util.isEmpty(requestFragments)) {
                    for (AccountRequest acctReq : requestFragments) {
                        for (AttributeRequest attReq : Util.iterate(acctReq.getAttributeRequests())) {
                            assignmentId = attReq.getAssignmentId();
                            if (assignmentId != null)
                                break;
                        }
                    }
                }
            }
        }
        
        if (Util.isNotNullOrEmpty(assignmentId)) {
            entitlement.setAssignmentId(assignmentId);
        }
        
        // NOTE: DO not mark the entitlement assigned the IIQEvaluator
        // will take care of marking it assigned, if it is successfully
        // commited. Otherwise, the scanner will take care of the 
        // assignment bit.        
       
        // since its just requested...
        entitlement.setAggregationState(AggregationState.Disconnected);        
        entitlement.setSource(ir.getSource());

        // Try to get the type off of the attributes map.
        if(item.getManagedAttributeType()!=null) {
            entitlement.setType(ManagedAttribute.Type.valueOf(item.getManagedAttributeType()));
        } else {
            entitlement.setType(ManagedAttribute.Type.Entitlement);
        }
        entitlement.setAssigner(ir.getRequesterDisplayName());
        
        return entitlement;
    }
    
    /**
     * Build an IdentityEntitlement object from the request item and IdentityRequest 
     * object.
     * 
     * Just after approval, before provisioning we create the entitlement if not already
     * existing.
     * 
     * @param identity
     * @param masterAttributes
     * @param request
     * @param attributeRequest
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    private IdentityEntitlement entitlementFromAccountRequest(Identity identity,
                                                              Attributes<String, Object> masterAttributes,
                                                              AccountRequest request, 
                                                              AttributeRequest attributeRequest) 
        throws GeneralException {
               
        if ( identity == null ) {
            log.warn("Unable to resolve identity from the IdentityRequest when building the IDentityEntitlement object.");
            return null;
        }
       
        IdentityEntitlement entitlement = new IdentityEntitlement();
        entitlement.setIdentity(identity);
        
        String app = request.getApplication();
        if ( app != null ) {
            Application appObject = _context.getObjectByName(Application.class, app);
            if ( appObject != null ) {
                entitlement.setApplication(appObject);
            }
            String instance = request.getInstance();
            if ( instance != null ) {
                entitlement.setInstance(instance);
            }
            String accountId = request.getNativeIdentity();
            if ( accountId != null ) {
                entitlement.setNativeIdentity(accountId);
                entitlement.setDisplayName(accountId);
            }            
        }
       
        String name = attributeRequest.getName();
        if ( name != null ) 
            entitlement.setName(name);
            
        Object objVal = attributeRequest.getValue();
        String value = null;
        if( objVal != null && objVal instanceof String ) {
            value = objVal.toString();
        } else if( objVal != null && objVal instanceof List ) {
            value = Util.listToCsv((List)objVal);
        }
        if ( value != null )
            entitlement.setValue(value);

        Date startDate = attributeRequest.getAddDate();
        if (startDate != null) {
            entitlement.setStartDate(startDate);
        }

        Date endDate = attributeRequest.getRemoveDate();
        if (endDate != null) {
            entitlement.setEndDate(endDate);
        }

        if (Util.isNotNullOrEmpty(attributeRequest.getAssignmentId())) {
            entitlement.setAssignmentId(attributeRequest.getAssignmentId());
        }
       
        // NOTE: DO not mark the entitlement assigned the IIQEvaluator
        // will take care of marking it assigned, if it is successfully
        // commited. Otherwise, the scanner will take care of the 
        // assignment bit.        
       
        // since its just requested...
        entitlement.setAggregationState(AggregationState.Disconnected);        
        entitlement.setSource((String)masterAttributes.get(ProvisioningPlan.ARG_SOURCE));
        entitlement.setType(ManagedAttribute.Type.Entitlement);        
        entitlement.setAssigner((String)masterAttributes.get(ProvisioningPlan.ARG_REQUESTER));
        
        return entitlement;
    } 
}
