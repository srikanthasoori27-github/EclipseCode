/* (c) Copyright 2008-2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Class to help manage applying plans to Identity/Link objects.
 * 
 * Most of this code originated in IIQEvalulator and  was pushed into 
 * its own class so it could be shared in other parts of the
 * product.
 * 
 * The main purpose of this class is to apply changes that are
 * committed or optimistically provisioned on accounts or 
 * group objects.
 *
 * In the account case we notify the native change detector
 * of the changes so we keep track of native changes
 * that come in here through our change inteceptors.
 * There is no matching functionality for groups at this 
 * point.
 * 
 * For group changes we call through to the Aggregator
 * to bring in the latest version of the AccountGroup/ManagedAttribute
 * object.
 * 
 * @Since 6.2
 * 
 * @author jeff.larson
 * @author dan.smith@sailpoint.com
 *
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Aggregator;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.Entitlizer;
import sailpoint.api.Explanator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.NativeChangeDetector;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeMetaData;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Field;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Class to help manage applying plans to Identity/Link objects.
 * 
 * @Since 6.2
 */
public class PlanApplier {
    
    private static Log log = LogFactory.getLog(PlanApplier.class);

    /**
     * Identity we are dealing with, used to lookup existing Link data. (if the plan has AccountRequests).
     */
    Identity _identity;
    
    /**
     * Good ol' context.
     */
    SailPointContext _context;
    
    /**
     * Need project for some options.
     */
    ProvisioningProject _project;
    
    /**
     * Native change detector, which helps out for native change detection.
     * 
     * We call this object when Links are added/updated or deleted.
     * This code is called when we are doing interception so its
     * important in some cases to detect these changes so we can
     * react based on the defined triggers.
     *  
     */
    NativeChangeDetector _nativeChangeDetector;
    
    /**
     * List of links that is kept as accounts are delete when we apply account changes.
     */
    List<Link> _deletedLinks;

    /**
     * List of links that is kept as accounts are updated when we apply account changes.
     */
    List<Link> _updatedLinks;
    
    /**
     * List of links that are candidates to have their entilements refreshed because
     * of a change in the account's entitlements.
     */
    List <Link> _linksForEntitlementRefresh;
    
    /**
     * Cached system object used when creating new ManagedAttribute 
     * from ResourceObjects.  
     * 
     * Only used during ObjectRequest processing.
     */
    Aggregator _aggregator;

    EntitlementCorrelator _correlator;
    
    /**
     * A cache of the system and extended attributes that can
     * be found in ObjectRequest plans that target ManagedAttribute objects.
     * Used to differentiate between AttributeRequests that can
     * be made locally to the MA and those that have been
     * sent to the connector.
     * 
     * Only used during ObjectRequest processing.
     */
    Map<String,String> _managedAttributeSystemAttributes;
    
    private PlanApplier() {
        _deletedLinks = new ArrayList<Link>();
        _updatedLinks = new ArrayList<Link>();
        _linksForEntitlementRefresh = new ArrayList<Link>();
    }
    
    public PlanApplier(SailPointContext context, ProvisioningProject project) {
        this();
        _context = context;
        _project = project;
        _nativeChangeDetector = new NativeChangeDetector(context);
        
    }
    
    /**
     * Apply the plans to the Identity object.  The object should be in a locked
     * state before calling this method.
     * 
     * Caller's must call finish() to complete the removal of any deleted
     * links.
     *      
     * @param identity
     * @throws GeneralException
     */
    public void applyAcccountPlans(Identity identity) throws GeneralException {
        _identity = identity;
        List<ProvisioningPlan> toApply = getApplyablePlans(_project, true);
        if ( toApply != null ) {
            for ( ProvisioningPlan plan : toApply ) {
                applyAccountPlan(plan);   
            }            
        }        
    }
    
    public void applyAcccountPlans(Identity identity, List<ProvisioningPlan> plans) throws GeneralException {
        _identity = identity;
        if ( plans != null ) {
            for ( ProvisioningPlan plan : plans ) {
                applyAccountPlan(plan);   
            }            
        }        
    }

    /**
     * Evaluation in IIQ applies the accounts, the IIQ changes and then finally removes deleted
     * links and performs entitlement refresh.
     * 
     * Because of the order in the process there are two distinct phases, one for
     * Applying the accounts and one for cleaning up the links and performing
     * other modifications.
     * 
     * If the identity was updated during the evaluation of the plan 
     * IdentityEntitlement recalculation will also occur.
     *  
     * @param identityUpdated If true, will reconcile Identity entitlements
     * @param identity Identity to finish, or null to use Identity from applyAccountPlans
     * @throws GeneralException
     */
    public void finish(Identity identity, boolean identityUpdated) throws GeneralException {
        if (identity != null) {
            _identity = identity;
        }
        finish(identityUpdated);
    }

    /**
     * Evaluation in IIQ applies the accounts, the IIQ changes and then finally removes deleted
     * links and performs entitlement refresh.
     *
     * Because of the order in the process there are two distinct phases, one for
     * Applying the accounts and one for cleaning up the links and performing
     * other modifications.
     *
     * If the identity was updated during the evaluation of the plan 
     * IdentityEntitlement recalculation will also occur.
     *
     * @param identityUpdated
     * @throws GeneralException
     */
    public void finish(boolean identityUpdated) throws GeneralException {
        if ( !isSimulating() ) {
            if ( Util.size(_deletedLinks) > 0 ) {        
                Terminator t = new Terminator(_context);
                t.setNoDecache(true);
                for (Link link : _deletedLinks) {
                    if ( Util.getBoolean(_project.getAttributes(), PlanEvaluator.ARG_NATIVE_CHANGE) )  {
                        _nativeChangeDetector.detectDelete(_identity, link);          
                    }
                    t.deleteObject(link);
                }
            }
            reconcileIdentityEntitlements(identityUpdated);
        }
    }
    
    /**
     * Called by provision() after we've provisioned any changes to identities.
     * Now we can look at the ObjectRequests in the plan and apply them
     * to the corresponding ManagedAttribute objects.  This is unrelated to
     * any identities, though possible you generally don't mix AttributeRequests
     * and ObjectRequests in a plan.
     *
     * Though originally intended only for editing AccountGroup objects, in
     * 6.0 it may edit any ManagedAttribute including those that can't
     * be provisioned.  Here we just apply the changes to the IIQ model.
     * PlanEvaluator is responsible for filtering out the requests that can't
     * be provisioned.
     */
    public void applyObjectPlans() 
        throws GeneralException {
        
        // Shouldn't have any ObjectRequests when simulating but
        // make sure we don't screw something up.  I suppose
        // we could load the object and modify it but not commit
        // but since the ManagedAttribute isn't attached to the plan,
        // it wouldn't be of much use.
        if (isSimulating())
            return;
        
        // Get just the plans with applyable ObjectRequests in them
        // ignore AccountRequests.
        // Actually they'll all come back since we have a mixture
        // of local requests and provisionable requests.
        List<ProvisioningPlan> plans = getApplyablePlans(_project, false);
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                List<ObjectRequest> requests = plan.getObjectRequests();
                if (requests != null) {
                    for (ObjectRequest req : requests) {
                        
                        String appname = req.getApplication();
                        Application app = req.getApplication(_context);
                        if (app == null) {
                            log.error("Missing or invalid application name: " + appname);
                        }
                        else {
                            // If this is a group request and the app supports aggregation for the group type
                            // try and refresh the manage attribute
                            if ( (app.hasGroupSchema(req.getType()) && !app.isNoAggregation(req.getType())) ||
                                 Util.nullSafeEq(ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE, req.getType()) ) {
                                    applyObjectRequest(plan, req, app);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @return list of links that were deleted during plan adornment.
     */
    public List<Link> getDeletedLinks() {
        return _deletedLinks;
    }
    
    /**
     * @return list of links that were updated during plan adornment.
     */
    public List<Link> getUpdatedLinks() {
        return _updatedLinks;
    }
    
    /**
     * @return true if there is a plan in the project that can be applied.
     */
    public boolean hasApplyablePlans( boolean account) throws GeneralException {
        if ( Util.size(getApplyablePlans(_project, account)) > 0  ) {
            return true;
        }
        return false;
    } 
    
    /**
     * Check if the status indicates it is ready to be applied. 
     * This is true if status is either COMMITTED or  
     * QUEUED with optimism. 
     *  
     * @param status Status of the plan or request. 
     * @param optimistic Are we optimistic? 
     * @return 
     */
    public static boolean isApplyable(String status, boolean optimistic) {
        return (ProvisioningResult.STATUS_COMMITTED.equals(status) ||
                (optimistic && ProvisioningResult.STATUS_QUEUED.equals(status)));
    }

    /**
     * Set the entitlement correlator
     * 
     * @param _correlator
     */
    public void setCorrelator(EntitlementCorrelator _correlator) {
        this._correlator = _correlator;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // 
    // Account Plans
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Apply a plan directly to the identity.
     * This can be used to simulate provisioning, or to get the
     * cube updated with what the connector says was committed
     * without waiting for the next aggregation.
     * 
     * See isApplyable for the conditions under which we will
     * apply something.  
     *
     * NOTE: If we don't find a Link for a given AccountRequest
     * we'll make one.  The problem is that we don't know what
     * the nativeIdentity should be.  For simulated provisioning
     * this doesn't matter but if we ever let these persist
     * it's likely someone would get an NPE if we don't set a name.
     * For projects that went through account completion forms we should
     * end up with an "accountId" attribute request which becomes
     * the nativeIdentity.
     *
     * If we persist a link with a generated name, we need to 
     * be smart enough during aggregation to reuse or remove this
     * link when the real one finally comes in.  Looking for a
     * special token like "???" seems reasonable.
     *
     * NOTE WELL: This must NOT commit transactions because it is used
     * to do  "simulated provisioning" for impact analysis.
     */
    private void applyAccountPlan(ProvisioningPlan plan)
        throws GeneralException {

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {                
                String instance = account.getInstance();
                String nativeId = account.getNativeIdentity();
                ObjectOperation aop = account.getOp();

                Application app = account.getApplication(_context);
                if (app != null) {

                    Link link = _identity.getLink(app, instance, nativeId);

                    if (aop == ObjectOperation.Delete) {
                        if (link == null) {
                            // shouldn't happen...how would we get this
                            // far without filtering?
                        }
                        else {
                            // determine provisioning status
                            String status = null;
                            ProvisioningResult result = account.getResult();
                            if (result == null)
                                result = plan.getResult();
                            if (result != null)
                                status = result.getStatus();
                            if (status == null)
                                status = ProvisioningResult.STATUS_QUEUED;
                        
                            boolean optimistic =  _project.getBoolean(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING);
                        
                            if (isApplyable(status, optimistic)) {
                                _deletedLinks.add(link);                        
                            }
                        }
                    }
                    else {
                        // consider all the others as updates
                        // Disable, Enable, Unlock, Lock can in theory
                        // have attribute requests too
                        applyAccount(plan, account, app, link);
                    }
                }
            }
        }
    }
    
    /**
     * Apply the attribute and permission requests to a link.
     * If the link does not exist one will be created and added to the
     * identity.  We may not start with a link, one will bootstrapped
     * only if we really need it.
     */
    @SuppressWarnings("deprecation")
    private void applyAccount(ProvisioningPlan plan, AccountRequest account, 
                              Application app, Link link) 
        throws GeneralException {

        // get the account schema so we can filter secret fields
        Schema schema = app.getSchema(Connector.TYPE_ACCOUNT);
        String identityAttribute = null;
        String displayAttribute = null;
        // only do this when we have to
        boolean identityEntitlementPromotionDisabled = _project.getBoolean(PlanEvaluator.ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION); 
        boolean hasEntitlementChanges = false;
        List<String> entitlementAttrNames = null;

        if (schema != null) {
            identityAttribute = schema.getIdentityAttribute();
            displayAttribute = schema.getDisplayAttribute();
            entitlementAttrNames = schema.getEntitlementAttributeNames(); 
        }
        
        // avoid the null checks
        if ( entitlementAttrNames == null )
            entitlementAttrNames = new ArrayList<String>();

        // only bootstrap a link if we absoultely need one
        Link startingLink = link;

        // TODO: If the connector returned a ResourceObject should
        // assimilate that instead of looking at the plan.  Do we
        // still need to look for link edits in that case?

        boolean optimistic =  _project.getBoolean(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING);
        String status = null;
        ProvisioningResult result = account.getResult();
        ResourceObject newobj = null;
        if (result != null) {
            status = result.getStatus();
            newobj = result.getObject();
        }
        else {
            // Unmanaged plan won't have a result
            result = plan.getResult();
            if (result != null)
                status = result.getStatus();
        }
        //Queued is our default if status is not set 
        if (status == null) {
            status = ProvisioningResult.STATUS_QUEUED;
        }

        boolean accountApply = isApplyable(status, optimistic);

        if (ProvisioningResult.STATUS_COMMITTED.equals(status) && 
            newobj != null) {

            // A special case where the connector returns an object containing
            // all new account attributes. We don't attempt to apply the
            // plan in this case but we still want to apply manual link edits,
            // which in practice shouldn't be here but I suppose we could
            // combine them.  Turn the apply flag off so we only do link 
            // edits in the loops below.
            accountApply = false;
            
            Attributes<String,Object> atts = newobj.getAttributes();
            if (atts != null) {
                // !! sigh, have to potentially filter out the
                // same gunk that Aggregator does, need to 
                // define this list somewhere so we can share
                atts.remove(Connector.ATT_SOURCE_APPLICATION);
                atts.remove(Connector.ATT_CIQ_SOURCE_APPLICATION);
                atts.remove(Connector.ATT_MULTIPLEX_IDENTITY);
                atts.remove(Connector.ATT_CIQ_MULTIPLEX_IDENTITY);
         
                if ( link == null ) {
                    link = createLink(account, app);
                    // Allow the connector to return the identity attribute
                    // if it decides not to use the one we sent.  There are
                    // two conventions for this, return ResourceObject.identity
                    // or return a value for the attribute declared as the
                    // identity attribute in the Schema.  Returning an attribute
                    // is what most connector writers do so let that have priority.
                    String newObjId = newobj.getIdentity();
                    if (identityAttribute != null) {
                        String id = newobj.getStringAttribute(identityAttribute);
                        if (id != null)
                            newObjId = id;
                    }

                    if ( Util.nullSafeCompareTo(newObjId, account.getNativeIdentity()) != 0 ) {
                        log.info("Connector returned a different account identity: " + newObjId);
                        link.setNativeIdentity(newObjId);
                        // update this too so plan inspectors know what happened
                        account.setNativeIdentity(newObjId);
                    }

                    // similar treatment for displayName
                    String newDisplay = newobj.getDisplayName();
                    if (displayAttribute != null) {
                        String id = newobj.getStringAttribute(displayAttribute);
                        if (id != null)
                            newDisplay = id;
                    }
                    link.setDisplayName(newDisplay);
                }                
                
                // NOTE: We will overwrite existing ones but not
                // completely replace the old map.  I'm worried about
                // things like the permissions list that we have a special
                // case for in Identitizer, and also some unit tests.
                // Really need to move this over to Identitizer.
                // This may actually be desirable though to the connector
                // doesn't have to return everything if that's expensive
                // to calculate, just as much updated stuff as it can.
                //
                // djs: the odd case here is if the last value of an multi
                // valued attribute like groups. These will typically 
                // be returned from the connector's RO but the key
                // will be missing. 
                Iterator<Map.Entry<String,Object>> it = atts.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String,Object> entry = it.next();
                    String attrName = entry.getKey();
                    if ( attrName != null ) {
                        if ( !hasEntitlementChanges && entitlementAttrNames.contains(attrName) ) 
                            hasEntitlementChanges = true;
                        
                        setLinkAttribute(null, schema, link, attrName, entry.getValue());
                    }
                }

                //set null attributes for any attribute not in the AccountRequest
                List<String> attsToRemove = new ArrayList<String>();
                Attributes<String,Object> linkatts = link.getAttributes();
                if (linkatts != null) {
                    Iterator<Map.Entry<String,Object>> linkattsIt = linkatts.entrySet().iterator();
                    while (linkattsIt.hasNext()) {
                        Map.Entry<String,Object> linkattsEntry = linkattsIt.next();
                        if (!atts.containsKey(linkattsEntry.getKey())) {
                            String linkattsAttrName = linkattsEntry.getKey();
                            attsToRemove.add(linkattsAttrName);
                        }
                    }
                }
                if ( !attsToRemove.isEmpty() ) {
                    Iterator<String> itr = attsToRemove.iterator();
                    while(itr.hasNext()) {
                        String attRemoved = itr.next();
                        log.debug("Setting link attribute " + attRemoved + " to null");
                        setLinkAttribute(null, schema, link, attRemoved, null);
                    }
                }
            }
        }

        List<AttributeRequest> atts = account.getAttributeRequests();
        if (atts != null) {
            for (AttributeRequest att : atts) {

                //What about the attribute requests to set null/remove attributes?
                // jsl - we can't do this, setLinkAttribute can only be called
                // if we have a "committed" status back from the Connector
                // or this is a manual link edit, need to revisit how we
                // apply the ResourceObject returned in the block above to 
                // the Link to handle null values which are no longer in the 
                // ResourceObject
                //if ((att.getValue() == null && att.getOp() == Operation.Set) || att.getOp() == Operation.Remove) {
                //    setLinkAttribute(null, schema, link, att.getName(), null);
                //}
                
                // attribute specific results override the account
                boolean attApply = accountApply;
                ProvisioningResult attres = att.getResult();
                if (attres != null) {
                    String attstatus = attres.getStatus();
                    if (attstatus != null)
                        attApply = isApplyable(attstatus, optimistic);
                }

                // 6.0 new
                if ((attApply || att.isLinkEdit())) {

                    Operation op = att.getOp();
                    String name = att.getName();
                    Object value = att.getValue();
                    
                    if ( name != null && entitlementAttrNames.contains(name) ) 
                        hasEntitlementChanges = true;

                    if (name == null) {
                        log.error("Ignoring request with no name");
                    }
                    else {
                        if (link == null)
                            link = createLink(account, app);

                        if (name.equals(identityAttribute) ||
                            name.equals(Field.ACCOUNT_ID)) {

                            // this goes in a special place
                            // note that like PlanCompiler, we support both the
                            // schema attribute and the reserved ACCOUNT_ID
                
                            if (link.getNativeIdentity() == null) {
                                if (value != null)
                                    link.setNativeIdentity(value.toString());
                            }
                            else {
                                // we don't support rename, ignore
                            }
                        }
                        else {
                            Object current = link.getAttribute(name);
                            // Using ProvisioningPlan.addValues which expects a case
                            // sensitivity flag.  Assume at this point that we've
                            // taken care of case issues and anything we need to add/remove
                            // really needs to be added/removed.  

                            if (op == null || op == Operation.Set) {
                                // leave value alone
                            }
                            else if (op == Operation.Add) {
                                // Need to handle ADD for a single valued attribute. 
                                // addValues will coerce to a list.  Only do 
                                // this if the attribute is multi-valued, otherwise 
                                // leave it alone. 
                                if (schema != null && schema.isMultiValued(name)) { 
                                    value = ProvisioningPlan.addValues(value, current, app.isCaseInsensitive()); 
                                } 
                            }
                            else if (op == Operation.Remove ||  
                                     op == Operation.Revoke) {
                                value = ProvisioningPlan.removeValues(value, current, app.isCaseInsensitive());
                            }

                            setLinkAttribute(att, schema, link, name, value);

                            // If this was a direct edit maintain metadata
                            // !! Role expansion does not pay attention to the
                            // edit mode of extended attributes, they will always win.
                            // You should not use an editable extended attribute in
                            // a role for that reson.  If we need that it will really
                            // make role expansion more complicated.
                            if (att.isLinkEdit()) {
                                updateAttributeMetaData(link, name, current);
                            }

                            // promote the display name 
                            if (name.equals(displayAttribute)) {
                                String displayNameValue =  ( value != null ) ? value.toString() : null;
                                link.setDisplayName(displayNameValue);
                            }                            
                        }
                    }
                }
            }
        }

        List<PermissionRequest> perms = account.getPermissionRequests();
        if (perms != null) {
            for (PermissionRequest perm : perms) {

                // attribute specific results override the account
                boolean permApply = accountApply;
                ProvisioningResult permres = perm.getResult();
                if (permres != null) {
                    String permstatus = permres.getStatus();
                    if (permstatus != null)
                        permApply = isApplyable(permstatus, optimistic);
                }


                if (permApply || perm.isLinkEdit()) {

                    if (link == null) {
                        link = createLink(account, app);
                    }

                    Operation op = perm.getOp();
                    String target = perm.getTarget();
                    List<String> rights = perm.getRightsListClone();

                    boolean isTargetPerm = (null != perm.getTargetCollector());
                    Permission current = null;

                    if (isTargetPerm) {
                        current = ObjectUtil.getTargetPermission(_context, link, target);
                    }
                    else {
                        // note that we'll be collapsing multiple perms for
                        // the same target into one when we do this, 
                        // should be okay for simulated provisioning, but
                        // may have consequences if they were broken up for
                        // a reason!!
                        current = link.getSinglePermission(target);
                    }

                    if (current == null) {
                        current = new Permission();
                        current.setTarget(target);
                        current.setAggregationSource(perm.getTargetCollector());
                    }

                    if (op == null || op == Operation.Set) {
                        current.setRights(rights);
                    }
                    else if (op == Operation.Add) {
                        current.addRights(rights);
                    }
                    else if (op == Operation.Remove ||  
                             op == Operation.Revoke) {
                        current.removeRights(rights);
                    }

                    if (isTargetPerm) {
                        ObjectUtil.setTargetPermission(_context, link, current);
                    }
                    else {
                        link.setSinglePermission(current);
                    }

                    link.setDirty(true);
                    // permissions are entitlement by definition
                    hasEntitlementChanges = true;

                }
            }
        }

        // These AccountRequest ops become link attributes
        ObjectOperation aop = account.getOp();
        if (aop == ObjectOperation.Disable ||
            aop == ObjectOperation.Enable ||
            aop == ObjectOperation.Lock ||
            aop == ObjectOperation.Unlock) {

            if (link == null)
                link = createLink(account, app);

            if (aop == ObjectOperation.Disable)
                link.setAttribute(Connector.ATT_IIQ_DISABLED, "true");

            else if (aop == ObjectOperation.Enable)
                link.setAttribute(Connector.ATT_IIQ_DISABLED, null);

            else if (aop == ObjectOperation.Lock)
                link.setAttribute(Connector.ATT_IIQ_LOCKED, "true");

            else if (aop == ObjectOperation.Unlock)
                link.setAttribute(Connector.ATT_IIQ_LOCKED, null);

            link.setDirty(true);
        }

        // if we created a new link and didn't find an accountId attribute
        // request, generate one since the system expects it to be non-null
        if (startingLink == null && link != null && link.getNativeIdentity() == null)
            link.setNativeIdentity("???");
        
        // If these came through interception and we have a special flag set
        // check the triggers and see if we need to stick something
        // on the identity about the native changes
        if ( Util.getBoolean(_project.getAttributes(), PlanEvaluator.ARG_NATIVE_CHANGE) )  {
            Attributes<String,Object> prevAttrs = (startingLink != null ) ? startingLink.getAttributes() : null;            
            _nativeChangeDetector.detectCreateAndModify(_identity, prevAttrs, link, app) ;          
        }

        // save these for later
        if (!_updatedLinks.contains(link))
            _updatedLinks.add(link);
        
        // Use the updated linke to update the entitlements if necessary
        // defer this till we have the lock on the identity
        if ( hasEntitlementChanges && !isSimulating() && !identityEntitlementPromotionDisabled ) {
            _linksForEntitlementRefresh.add(link);
        }
    }

    /**
     * Set a link attribute to what was in an AttributeRequest.
     *
     * Pay attention to the schema so we filter out attributes
     * that are typed secret (like passwords) so they won't
     * appear in the XML or in the account details page.
     *
     * NOTE: Some secret attributes are defined in the provisioning
     * template and not the schema, notably "*password*" for 
     * openconnector and "password" for IIQ connectors.  We could special
     * case those but I'm thinking that really we should only put something
     * in the link if it is defined in the Schema.  There could be other things
     * in templates designed to pass information to the connector that
     * are not really account attributes.
     *
     * Unfortunately we can't tell the difference between write-only
     * attributes like passwords and other attributes that may need 
     * to be saved on the link but encrypted.  If we need to tell the difference
     * we'll have to extend the AttributeDefinition model in the Scheema.
     *
     * For now, I'm assuming that all secret attributes can be filtered.
     */
    private void setLinkAttribute(AttributeRequest req, Schema schema, 
                                  Link link, String name, Object value) {

        // always pass our builtin attributes
        boolean pass = 
            Connector.ATT_IIQ_DISABLED.equals(name) ||
            Connector.ATT_IIQ_LOCKED.equals(name);
        
        // also pass direct permissions if enabled by the
        // application
        if (Connector.ATTR_DIRECT_PERMISSIONS.equals(name) && 
            (schema != null && schema.getIncludePermissions())) {
            pass = true;
        }

        // allow anything flagged as a link edit
        // to be extra cautious we could look in the ObjectConfig:Link
        // for a matching ObjectAttribute but it may be nice to create
        // a linkEdit request for other reasons
        if (!pass && req != null)
            pass = req.isLinkEdit();

        // else must have an attribute definition in the schema
        if (!pass) {
            AttributeDefinition def = null;
            if (schema != null) {
                def = schema.getAttributeDefinition(name);
                pass = (def != null);
            }
        }

        if (pass) {
            link.setAttribute(name, value);
            link.setDirty(true);
        }
    }

    /**
     * Update the metadata maintained for editable link attributes.
     * We can skip checking the ObjectConfig:Link to see if this is an editable
     * attribute since the only way an AttributeRequest can come down
     * with isLinkEdit true is if the UI already made this determination.
     * Still if this were a web request they could manufacture a provisioning
     * plan that set anything even if it wasn't in the ObjectConfig.  So it
     * would be a good idea to have an extra level of checking down here.
     */
    private void updateAttributeMetaData(Link link,
                                         String name, 
                                         Object previousValue ) {
        
        AttributeMetaData metadata = link.getAttributeMetaData(name);
        
        if (metadata == null) {
            metadata = new AttributeMetaData();
            metadata.setAttribute(name);
            link.addAttributeMetaData(metadata);
        }

        // Name of the user that made the modification, this
        // should come in through the "requester" property of the plan.
        // IIQEvaluator also has an ARG_ASSIGNER that is uses for both
        // role assignment and attribute metadata and it has priority
        // over the requester.  Not sure if we need to pay attention
        // to ARG_ASSIGNER here since link edits always come from the UI
        // but let's be consistent.
        String user = _project.getString(PlanEvaluator.ARG_ASSIGNER);
        if (user == null)
            user = _project.getRequester();

        metadata.setUser(user);

        // Only the feed is allowed to set the last value.  Once 
        // it's been established, don't override it.  See IIQSR-72.
        String lastValue = Util.otos(metadata.getLastValue());
        if (Util.isNullOrEmpty(lastValue)) {
            metadata.setLastValue(previousValue);
        }

        // this is a "manual edit" so the source always goes null
        metadata.setSource(null);

        // ObjectUtil only does this if the user started off non-null
        metadata.incrementModified();
    }

    /**
     * Helper for applyAccount and updatePasswordHitsory.
     * Bootstrap a new account Link if we can't find one.
     *
     * This is important for simulated provisioning, but for normal
     * "optimistic" provisioning his may cause trouble because we'll try
     * to commit it without saving.  I'm worried about calling saveObject
     * here though which may have unintended side effects, though at
     * this point we've scribbled all over the other Links so the transaction
     * has unwanted garbage in it anyway.
     *
     * Similar issue for updatePasswordHistory, but in practice we should
     * never get here for that since you won't be using the password management
     * interface unless we already had an account.
     */
    private Link createLink(AccountRequest account, Application app) {

        Link link = new Link();

        // Since we are explicitly creating this link for a particular identity,
        // make it sticky so it won't be moved on reaggregation if correlation
        // returns a different identity.
        link.setManuallyCorrelated(true);

        link.setApplication(app);
        link.setInstance(account.getInstance());
        link.setNativeIdentity(account.getNativeIdentity());
        link.setDirty(true);

        _identity.add(link);

        return link;
    }

    /**
     * Method that will update the IdentityEntitlements table with the
     * things that have been committed.
     * 
     * For workflows that use IdentityRequest logic, similar things are being
     * done there after we verify the change has been made on the system.
     * 
     * However, for things like certification revocation and custom workflows/tasks
     * that call provision we'd require a separate refresh. 
     * 
     * See bug#11949 for more information.
     */
    private void reconcileIdentityEntitlements(boolean doRolePromotion) 
        throws GeneralException {

        // IIQETN-6371 - The collection of ARG_REFRESH_OPTIONS is a Map of some kind and
        // not always Attributes (could be a simple HashMap).
        Map<String, Object> opts = (Map<String, Object>)_project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
        Attributes<String,Object> args = new Attributes<String,Object>(opts);

        if (_identity != null ) {
            Entitlizer entitlizer = new Entitlizer(_context, args);
            // since this is a transient instance pass along our CorrelationModel
            // so we don't keep querying for refresh
            if (_correlator != null)
                entitlizer.setCorrelationModel(_correlator.getCorrelationModel());

            if ( _identity.getId() == null ) {
                // We have to have an id so we can reference them if we create
                // any new entitlements
                // also check if it is not a restore deleted object request (AD RecycleBin)
                if(!Util.nullsafeBoolean(isIdentityNotRequired())) {
                    _context.saveObject(_identity);
                }
            }
            if ( doRolePromotion ) {
                if (_correlator != null) {
                    _correlator.analyzeContributingEntitlements(_identity);
                    Map<Bundle, List<EntitlementGroup>> entitlementMap = _correlator.getEntitlementMappings(true);
                    entitlizer.setEntitlementMapping(_identity, entitlementMap);
                }

                entitlizer.promoteRoleAssignments(_identity);
                entitlizer.promoteRoleDetections(_identity);
            }

            List<AttributeAssignment> updatedAssignments = getUpdatedAttributeAssignments();
            if (!Util.isEmpty(updatedAssignments)) {
                entitlizer.refreshAttributeAssignmentEntitlements(updatedAssignments, _identity);
            }

            // We keep track as we process account requests and 
            // only add the links that had entitlements refreshed
            if ( Util.size(_linksForEntitlementRefresh) > 0 ) {
                for ( Link link : _linksForEntitlementRefresh ) {
                    entitlizer.promoteLinkEntitlements(link);
                }
                entitlizer.finish(_identity);
            }


        }
    }

    /**
     * Return a list of AttributeAssignments derived from the project's
     * masterPlan containing AttributeRequests that would
     * cause an AttributeAssignment to be created/updated, therefore requiring
     * reconciliation with the corresponding IdentityEntitlement
     * @return
     */
    private List<AttributeAssignment> getUpdatedAttributeAssignments() {
        List<AttributeAssignment> assignments = new ArrayList<>();
        if (_project != null) {
            ProvisioningPlan plan = _project.getMasterPlan();
            if (plan != null) {
                List<AccountRequest> accounts = plan.getAccountRequests();
                if (accounts != null) {
                    for (AccountRequest account : accounts) {
                        List<AttributeRequest> atts = account.getAttributeRequests();
                        if (atts != null) {
                            for (AttributeRequest att : atts) {
                                if (att.isAssignment()) {
                                    AttributeAssignment assn = _identity.getAttributeAssignment(account.getApplication(),
                                            account.getNativeIdentity(), att.getName(), Util.otos(att.getValue()),
                                            account.getInstance(), att.getAssignmentId());
                                    if (assn != null) {
                                        assignments.add(assn);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return assignments;
    }


    
    //////////////////////////////////////////////////////////////////////
    //
    // ObjectRequests
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Apply an ObjectRequest to a ManagedAttribute
     *
     * Group requests are being handled differently than account requests
     * for op=Delete.  For accounts, if the connector doesn't return
     * COMMITTED we leave the Link and wait for aggregation to detect
     * the deletion.  For groups, this will be confusing unless
     * we don't display some kind of pending delete indiciator.  
     *
     * If this is a MA request for something that isn't aggregated,
     * then we need to always delete it.
     *
     * Pre 6.0, we deleted AccountGroups even though we couldn't 
     * provision them, so I'm restoring that behavior.  We can 
     * revisit this and possibly require an OPTIMISTIC_GROUP_PROVISIONING
     * option to be set.
     *
     */
    private void applyObjectRequest(ProvisioningPlan plan, ObjectRequest req,
                                    Application app)
        throws GeneralException {

        ManagedAttribute ma = ManagedAttributer.get(_context, req, app);
        boolean optimistic = _project.getBoolean(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING);
        
        if (req.getOp() == ObjectOperation.Delete) {
            // Derive the request level status
            String status = queueOrCommitMA(req, app, plan);

            // might want a distinct OPTIMISTIC_GROUP_PROVISIONING option?
            boolean applyRequest = PlanApplier.isApplyable(status, optimistic);

            if (applyRequest) {
                if (ma == null) {
                    log.info("Ignoring delete request for ManagedAttribute: " + 
                             app.getName() + ":" + req.getNativeIdentity());
                }
                else {
                    // for links we keep these on a list and delete
                    // later, I don't think we need the same deferal
                    // for these
                    if (!isSimulating()) {
                        _context.removeObject(ma);
                        _context.commitTransaction();
                    }
                }
            } else if (ProvisioningResult.STATUS_RETRY.equals(status)) {
                failProvisioningTransaction(req, true);
            }
        }
        else {
            // consider all the others as updates
            // Disable, Enable, Unlock, Lock can in theory
            // have attribute requests too
            applyObjectRequest(plan, req, app, ma);
        }
    }
    
    /*
     * Determine whether the ObjectRequest for the specified ManagedAttribute should be
     * queued or committed
     */
    private String queueOrCommitMA(ObjectRequest req, Application app, ProvisioningPlan plan) {
        String status = null;
        Schema schema = ManagedAttributer.getTargetSchema(req, app);
        //TODO: Need to look at the feature string for the schema
        if (schema == null || !app.supportsGroupProvisioning(schema.getObjectType())) {
            // this wasn't a provisionable request so it must be
            // for a local MA, always commit it
            status = ProvisioningResult.STATUS_COMMITTED;
        }
        else {
            ProvisioningResult result = req.getResult();
            if (result != null)
                status = result.getStatus();
            else {
                result = plan.getResult();
                if (result != null)
                    status = result.getStatus();
            }
            //Queued is our default if status is not set 
            if (status == null)
                status = ProvisioningResult.STATUS_QUEUED;
        }
        
        return status;
    }

    /**
     * Apply the ObjectRequest to a ManagedAttribute.
     * If the MA does not exist one will be created.
     *
     * This is very similar to the logic we use for AccountRequests and Links
     * but due to the sprinkled differences between ManagedAttribute and Link
     * it's hard to share without breaking this up into less comprehensible
     * pieces.  It might be nice to have a shared superclass for
     * Link and ManagedAttribute.
     *
     * Note that unlike Link we will always create an MA even if the
     * connector returned QUEUED.  The ObjectRequest will contain a mixture
     * of IIQ system attributes and application schema attributes, the
     * system attributes are always applied even though the connector
     * may not be done with the schema attributes.
     * 
     */
    private void applyObjectRequest(ProvisioningPlan plan, ObjectRequest req,
                                    Application app, ManagedAttribute object) 
        throws GeneralException {

        // only bootstrap an MA if we absoultely need one
        ManagedAttribute startingObject = object;
        Schema schema = ManagedAttributer.getTargetSchema(req, app);

        // If provisioning status is COMMITTTED we can treat it the same
        // as optimstic provisioning.
        // TODO: If the connector returned a ResourceObject should
        // assimilate that instead of looking at the plan.  Do we
        // still need to look for manual edits in that case?
        
        String status = null;
        ResourceObject newobj = null;

        if (schema == null || plan.getTargetIntegration() == null) {
            // this is a local MA request, or the unmanaged
            // plan for an application that doesn't support provisioning
            // treat these as success
            status = ProvisioningResult.STATUS_QUEUED;
            try {
                if (Configuration.getSystemConfig().getBoolean(Configuration.COMMIT_MANUAL_OBJECT_REQUESTS)) {
                    status = ProvisioningResult.STATUS_COMMITTED;
                }
            } catch (Throwable th) {
                log.error("Unable to load system configuration for COMMIT_MANUAL_OBJECT_REQUESTS", th);
            }
        }
        else {
            ProvisioningResult result = req.getResult();
            if (result != null) {
                status = result.getStatus();
                newobj = result.getObject();
            }
            else {
                // inherit the plan result
                result = plan.getResult();
                if (result != null)
                    status = result.getStatus();
            }
            //Queued is our default if status is not set 
            if (status == null) {
                status = ProvisioningResult.STATUS_QUEUED;
            }
        }

        // KLUDGE: The group workflows don't handle retry and it
        // is unclear what this means for optimistic provisioning.  
        // Do we create the MA now and retry the application part, 
        // or do we not do anything until the retry succeeds.
        // Until this is resolved treat as a falure, but note that
        // in order for this to dispay in the UI we have to 
        // change the plan status.
        if (ProvisioningResult.STATUS_RETRY.equals(status)) {
            ProvisioningResult result = req.getResult();
            if (result != null)
                result.setStatus(ProvisioningResult.STATUS_FAILED);
            else {
                result = plan.getResult();
                if (result != null)
                    result.setStatus(ProvisioningResult.STATUS_FAILED);
            }

            // need to mark provisioning transaction as failed if one exists
            failProvisioningTransaction(req, false);
        }

        // We've got an awkward split between part of the plan that
        // was sent to the connector and part that is just stored
        // locally.  So even if the connector failed we can still
        // update other parts of the ManagedAttribute model.  There
        // is some debate over whether creating a new MA should
        // succeed if provisioning fails though.  Catch that
        // for now, but we may want to reconsider this or
        // allow it to be configured.
        if (object != null ||
            (!ProvisioningResult.STATUS_FAILED.equals(status) &&
             !ProvisioningResult.STATUS_RETRY.equals(status))) {

            if (newobj == null)
                object = applyItemRequests(req, app, schema, object, status, true);
            else {
                // Connector returned a ResourceObject, this is expected
                // to be complete and authoritative.  
                // NOTE: I've seen connectors that returning the naming
                // attribute in the Map but not ResourceObject.identity, 
                // since this is a common error, promote it.
                if (newobj.getIdentity() == null) {
                    if (schema != null) {
                        String idatt = schema.getIdentityAttribute();
                        String id = newobj.getString(idatt);
                        newobj.setIdentity(id);
                    }
                }

                // This is the same thing that ResourceEventService uses
                // to delta agg groups. It should return the same object
                // we already have if this is an existing group.
                if (_aggregator == null)
                    _aggregator = new Aggregator(_context);

                ManagedAttribute neu = _aggregator.aggregateGroup(app, newobj);
                if (object != null && object != neu) {
                    // this isn't necessarily a problem but I want to know when
                    // it happens, should still be in the session
                    log.warn("Unexpected group object returned after delta aggregation");
                }
                object = neu;

                // still have to apply the system items
                object = applyItemRequests(req, app, schema, object, status, false);
            }


            // These AccountRequest ops become link attributes.
            // Don't know if these apply to groups, but support it
            // like we do for Links.  Only do this if the schema is non-null
            // which means there is managed object that can have these states.
            // !! Since this will result in the creation of an object,
            // we may want to skip this if they really aren't relevant.
            ObjectOperation reqop = req.getOp();
            if (schema != null && 
                (reqop == ObjectOperation.Disable ||
                 reqop == ObjectOperation.Enable ||
                 reqop == ObjectOperation.Lock ||
                 reqop == ObjectOperation.Unlock)) {

                if (object == null)
                    object = createManagedAttribute(req, app, schema);

                if (reqop == ObjectOperation.Disable)
                    object.put(Connector.ATT_IIQ_DISABLED, "true");

                else if (reqop == ObjectOperation.Enable)
                    object.put(Connector.ATT_IIQ_DISABLED, null);

                else if (reqop == ObjectOperation.Lock)
                    object.put(Connector.ATT_IIQ_LOCKED, "true");

                else if (reqop == ObjectOperation.Unlock)
                    object.put(Connector.ATT_IIQ_LOCKED, null);
            }

            // if we created a new group and didn't find a naming attribute
            // request, generate one since the system expects it to be non-null
            if (!ManagedAttributer.isPermissionRequest(req) && startingObject == null && 
                object != null && object.getValue() == null) {
                object.setValue("???");
                if (!isSimulating()) {
                    // shouldn't we have caught this by now?
                    log.error("Creating ManagedAttribute with unknwon value!");
                }
            }

            if (object != null) {
                // bug#13891 since on agg we initially promote the native display name
                // to the IIQ display name, we do the same on create for consistency.
                if (startingObject == null && object.getDisplayName() == null && schema != null) {
                    String dname = schema.getDisplayAttribute();
                    if (dname != null) {
                        object.setDisplayName(Util.otoa(object.getAttribute(dname)));
                    }
                }

                reconcileGroupInheritance(app, object);
            }

            // don't bother deferring these like we do for Links since
            // refreshing them doesn't have a lot of consequences
            if (!isSimulating() && object != null) {
                if(!Util.nullsafeBoolean(isIdentityNotRequired())) {
                    _context.saveObject(object);
                }
                _context.commitTransaction();
                // keep the Explanator cache up to date in this JVM
                Explanator.refresh(object);
            }
        }
    }

    /**
     * Marks the provisioning transaction object referenced in the request as failed.
     *
     * @param request The request.
     * @param commit True to commit the transaction when saving the transaction.
     */
    private void failProvisioningTransaction(AbstractRequest request, boolean commit) throws GeneralException {
        ProvisioningTransactionService transactionService = new ProvisioningTransactionService(_context);
        transactionService.setCommit(commit);
        transactionService.failTransaction(request);
    }

    /**
     * After committing changes to a ManagedAttribute, reconcile
     * the hierarchy attribute value with the ManagedAttribute.inheritance
     * list.
     */
    private void reconcileGroupInheritance(Application app, ManagedAttribute object)
        throws GeneralException {

        if (app != null && object != null) {
            String attname = app.getGroupHierarchyAttribute(object.getType());
            if (attname != null) {
                List<String> parents = Util.asList(object.get(attname));
                if (parents != null && parents.size() > 0) {
                    List<ManagedAttribute> inheritance = new ArrayList<ManagedAttribute>();
                    for (String name : parents) {
                        ManagedAttribute ma = ManagedAttributer.get(_context, app, false, 
                                                                    object.getReferenceAttribute(),
                                                                    name);
                        if (ma == null)
                            log.warn("Unable to resolve parent group: " + name);
                        else {
                            inheritance.add(ma);
                        }
                    }
                    object.setInheritance(inheritance);
                }
            }
        }
    }

    /**
     * Apply the Attribute and Permission requestsin the plan
     * to a ManagedAttribute, bootstrapping if necesssary.K
     */
    @SuppressWarnings("unchecked")
    private ManagedAttribute applyItemRequests(ObjectRequest req,
                                               Application app, 
                                               Schema schema,
                                               ManagedAttribute object,
                                               String accountStatus,
                                               boolean doNative)
        throws GeneralException {

        boolean optimistic = _project.getBoolean(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING);
        
        // for groups recognize requests for the naming attribute
        String namingAttribute = null;
        if (schema != null)
            namingAttribute = schema.getIdentityAttribute();

        List<AttributeRequest> atts = req.getAttributeRequests();
        if (atts != null) {
            for (AttributeRequest att : atts) {

                // ignore native attributes if we got a ResourceObject
                // back fromt the connector
                if (!doNative && !isManagedAttributeSystemAttribute(att))
                    continue;

                // attribute specific results override the account
                String attStatus = accountStatus;
                ProvisioningResult attres = att.getResult();
                if (attres != null) {
                    String s = attres.getStatus();
                    if (s != null)
                        attStatus = s;
                }

                // At this point for AccountRequest we would look at the
                // att.isLinkEdit flag to determine if this was a local edit
                // that is independet of the connector.  For ManagedAttributes
                // we don't have that concept, anything that is not in the
                // group schema is applied.

                if (PlanApplier.isApplyable(attStatus, optimistic) ||
                    att.isLinkEdit() ||
                    isManagedAttributeSystemAttribute(att)) {

                    Operation op = att.getOp();
                    String name = att.getName();
                    Object value = att.getValue();

                    if (name == null) {
                        log.error("Ignoring request with no name");
                    }
                    else {
                        if (object == null)
                            object = createManagedAttribute(req, app, schema);
                        
                        // If we find an AttributeRequest for the naming attribute of a 
                        // managed object (group) treat it like the MA value
                        // Field.ACCOUNT_ID is another common alias on create, but
                        // we really shouldn't see it here.
                        if (name.equals(namingAttribute) || 
                            name.equals(Field.ACCOUNT_ID)) {

                            // this goes in a special place
                            // note that like PlanCompiler, we support both the
                            // schema attribute and the reserved ACCOUNT_ID
                            Object current = object.getValue();
                            if (current == null && value != null) {
                                object.setValue(value.toString());
                            }
                            else if (current != null && value != null &&
                                     !current.toString().equals(value.toString())) {
                                // This is considered a rename which we don't support
                                log.warn("Ignnoring rename of ManagedAttribute");
                            }
                        }
                        else if (name.equals(ManagedAttribute.PROV_ATTRIBUTE)) {
                            // this became the referenceAttribute argument, ignore
                        }
                        else if (name.equals(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE)) {
                            if (ManagedAttribute.Type.Permission.name().equals(value)) {
                                object.setType(ManagedAttribute.Type.Permission.name());
                            }
                            // else leave it as ManagedAttribute.Type.Entitlement 
                            // because that's the only other possible choice right now
                        }
                        else if (name.equals(ManagedAttribute.PROV_DISPLAY_NAME)) {
                            // We've got the usual oddities around setting single
                            // valued attributes and add/remove.  
                            String svalue = stringify(value);
                            if (op == Operation.Remove && 
                                svalue != null && svalue.equals(object.getDisplayName()))
                                svalue = null;
                            object.setDisplayName(svalue);
                        }
                        else if (name.equals(ManagedAttribute.PROV_DESCRIPTIONS)) {
                            // only Set is supported at the moment, could support 
                            // add/remove but it's hard and the UI doesn't need it
                            if (value != null && !(value instanceof Map))
                                log.error("ManagedAttirbute descriptions value not a map!");

                            Map<String,String> map = (Map<String,String>)value;
                            if (op == Operation.Set)
                                object.setDescriptions(map);
                            else
                                log.error("ManagedAttribute descriptions: operation not supported");
                        }
                        else if (name.equals(ManagedAttribute.PROV_REQUESTABLE)) {
                            boolean b = Util.otob(value);
                            if (op == Operation.Remove && b == object.isRequestable())
                                b = false;
                            object.setRequestable(b);
                        }
                        else if (name.equals(ManagedAttribute.PROV_OWNER)) {
                            String svalue = stringify(value);
                            if (op == Operation.Remove) {
                                if (svalue != null) {
                                    Identity owner = object.getOwner();
                                    // UI passes id, name no longer allowed -rap
                                    if (owner != null && 
                                        (svalue.equals(owner.getId()) || svalue.equals(owner.getName())))
                                        object.setOwner(null);
                                }
                            }
                            else {
                                // Technically we should ignore op=Add with null but
                                // treat it the same as op=Set.  UI passes ID but allow
                                // name in hand built plans.
                                //We still want to allow this? -rap
                                Identity owner = null;
                                if (Util.isNotNullOrEmpty(svalue)) {
                                    owner = _context.getObjectById(Identity.class, svalue);
                                    if (owner == null) {
                                        log.warn("No owner found with id: " + svalue);
                                    }
                                }
                                object.setOwner(owner);
                            }
                        }
                        else if (name.equals(ManagedAttribute.PROV_SCOPE)) {
                            // scope names are not unique, this is expected to be the id
                            // but we could use path since it is clearer, 
                            // have refresh issues though...
                            String svalue = stringify(value);
                            if (op == Operation.Remove) {
                                if (svalue != null) {
                                    Scope scope = object.getAssignedScope();
                                    // these can only be referenced by id
                                    if (scope != null && svalue.equals(scope.getId()))
                                        object.setAssignedScope(null);
                                }
                            }
                            else {
                                Scope scope = _context.getObjectById(Scope.class, svalue);
                                object.setAssignedScope(scope);
                            }
                        }
                        else if (name.equals(ManagedAttribute.PROV_CLASSIFICATIONS)) {
                            if (op == Operation.Set) {
                                if (value == null) {
                                    object.setClassifications(null);
                                } else if (value instanceof String || value instanceof Collection) {
                                    Collection<String> newCls = null;
                                    if (value instanceof String) {
                                        newCls = Util.asList((String) value);
                                    } else {
                                        newCls = (Collection<String>) value;
                                    }
                                    Map<String, Classification> clsMap = new HashMap<>();
                                    for (ObjectClassification ocls : Util.safeIterable(object.getClassifications())) {
                                        clsMap.put(ocls.getClassification().getId(), ocls.getClassification());
                                    }
                                    
                                    //for add
                                    for (String newId : newCls) {
                                        if (!clsMap.containsKey(newId)) {
                                            Classification cls = _context.getObjectById(Classification.class, newId);
                                            if (cls != null) {
                                                object.addClassification(cls, Source.UI.name(), false);
                                            }
                                        }
                                    }
                                    
                                    //for remove
                                    for (String existId : clsMap.keySet()) {
                                        if (!newCls.contains(existId)) {
                                            object.removeClassification(clsMap.get(existId));
                                        }
                                    }
                                } else {
                                    log.error("ManagedAttirbute classifications value not a collection!");
                                }
                            }
                            else {
                                log.error("ManagedAttribute classifications: operation not supported");
                            }
                        }
                        else {
                            Object current = object.get(name);
                            // Using ProvisioningPlan.addValues which expects a case
                            // sensitivity flag.  Assume at this point that we've
                            // taken care of case issues and anything we need to add/remove
                            // really needs to be added/removed.  

                            if (op == null || op == Operation.Set) {
                                // leave value alone
                            }
                            else if (op == Operation.Add) {
                                value = ProvisioningPlan.addValues(value, current, false);
                            }
                            else if (op == Operation.Remove ||  
                                     op == Operation.Revoke) {
                                value = ProvisioningPlan.removeValues(value, current, false);
                            }

                            setObjectAttribute(att, schema, object, name, value);

                            // If this was a direct edit maintain metadata
                            // !!? Does this make sense for groups?  If so we'll
                            // need metadata like we do for Links
                            //if (att.isLinkEdit())
                            //updateAttributeMetaData(link, name, current);
                        }
                    }
                }
            }
        }

        // should only see these for MAs representing groups
        // there is no individual status on these
        List<PermissionRequest> perms = req.getPermissionRequests();
        if (perms != null) {
            for (PermissionRequest perm : perms) {

                // attribute specific results override the account
                String permStatus = accountStatus;
                ProvisioningResult permres = perm.getResult();
                if (permres != null) {
                    String s = permres.getStatus();
                    if (s != null)
                        permStatus = s;
                }

                if (PlanApplier.isApplyable(permStatus, optimistic)  ||
                    perm.isLinkEdit()) {

                    if (object == null)
                        object = createManagedAttribute(req, app, schema);

                    Operation op = perm.getOp();
                    String target = perm.getTarget();
                    List<String> rights = perm.getRightsListClone();

                    // note that we'll be collapsing multiple perms for
                    // the same target into one when we do this, 
                    // should be okay for simulated provisioning, but
                    // may have consequences if they were broken up for
                    // a reason!!
                    Permission current = object.getSinglePermission(target);
                    if (current == null) {
                        current = new Permission();
                        current.setTarget(target);
                    }

                    if (op == null || op == Operation.Set) {
                        current.setRights(rights);
                    }
                    else if (op == Operation.Add) {
                        current.addRights(rights);
                    }
                    else if (op == Operation.Remove ||  
                             op == Operation.Revoke) {
                        current.removeRights(rights);
                    }

                    object.setSinglePermission(current);
                }
            }
        }

        return object;
    }

    /**
     * Return true if this is one of the ManagedAttribute system attributes
     * rather than a group schema attribute.  This is necessary because
     * the AttributeRequsts have to be treated differently when applying
     * the plan.
     */
    private boolean isManagedAttributeSystemAttribute(AttributeRequest req) {
        if (_managedAttributeSystemAttributes == null) {
            Map<String,String> map = new HashMap<String,String>();

            // built-in attributes
            for (int i = 0 ; i < ManagedAttribute.PROV_ATTRIBUTES.length ; i++) {
                String name = ManagedAttribute.PROV_ATTRIBUTES[i];
                map.put(name, name);
            }

            // extended attributes
            ObjectConfig oc = ManagedAttribute.getObjectConfig();
            if (oc != null) {
                List<ObjectAttribute> atts = oc.getObjectAttributes();
                if (atts != null) {
                    for (ObjectAttribute att : atts)
                        map.put(att.getName(), att.getName());
                }
            }

            _managedAttributeSystemAttributes = map;
        }
        return (_managedAttributeSystemAttributes.get(req.getName()) != null);
    }

    /**
     * Convert to a string and collapse empty strings.
     */
    private String stringify(Object o) {
        String s = null;
        if (o != null) {
            s = o.toString();
            if (s.length() == 0)
                s = null;
        }
        return s;
    }

    /**
     * Create a new ManagedAttribute.
     * 
     */
    private ManagedAttribute createManagedAttribute(ObjectRequest req, Application app, 
                                                    Schema schema) 
        throws GeneralException {


        ManagedAttribute ma = new ManagedAttribute();
        ma.setApplication(app);

        String attribute = ManagedAttributer.getReferenceAttribute(req, app);
        ma.setAttribute(attribute);

        if (ManagedAttributer.isPermissionRequest(req)) {
            ma.setType(ManagedAttribute.Type.Permission.name());
            if (attribute == null) {
                //Permissions require a target. Groups no longer require an attribute, as they may be indirectly assignable groups
                throw new GeneralException("Unable to create ManagedAttribute without attribute/target name");
            }
        }
        else {
            if (Util.isNotNullOrEmpty(req.getType()) && app.hasGroupSchema(req.getType())) {
                //DO we want to set this here or should we always set to Entitlement and allow the account group agg to change this? -rap
                ma.setType(req.getType());
                ma.setAggregated(true);
            } else {
                ma.setType(ManagedAttribute.Type.Entitlement.name());
            }

            String id = ManagedAttributer.getObjectIdentity(req, app, schema);
            if (id == null) {
                throw new GeneralException("Unable to create ManagedAttribute: no value");
            }

            ma.setValue(id);
        }

        // it will be saved later
        return ma;
    }

    /**
     * Similar to setLinkAttribute but for ManagedAttributes.
     * There may be random data in the plan inserted for the connector
     * that we don't want to end up on the ManagedAttribute.
     *
     * First we filter based on the declared extended attributes.
     * If this is a group request, then we filter based on the 
     * group schema.
     */
    private void setObjectAttribute(AttributeRequest req, Schema schema, 
                                    ManagedAttribute group, 
                                    String name, Object value) {

        // If the request targeted a managed resource object (non-null schema)
        // then always pass our builtin state attributes.
        // always pass our builtin attributes
        // what about directPermissions ?
        boolean pass = (schema != null && 
                        Connector.ATT_IIQ_DISABLED.equals(name) ||
                        Connector.ATT_IIQ_LOCKED.equals(name));

        // Allow anything flaged as a direct edit 
        // "link edit" is a misnomer here.  This may not be
        // necessary now that we check the ObjectConfig below?
        if (!pass && req != null)
            pass = req.isLinkEdit();

        // Allow anything that is an extended attribute.
        if (!pass) {
            ObjectConfig config = ManagedAttribute.getObjectConfig();
            if (config != null)
                pass = (config.getObjectAttribute(name) != null);
        }

        // Allow anything declared in the schema (groups only)
        if (!pass && schema != null) {
            AttributeDefinition def = schema.getAttributeDefinition(name);
            pass = (def != null);
        }

        if (pass)
            group.put(name, value);
    }
    
    //////////////////////////////////////////////////////////////////////
    // 
    // Util - Connector/Integration Plan Application
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the non-IIQ plans in the project that can be immediately
     * applied to the identity cube.  See isApplyable for the reasons
     * why this would be true.
     */
    public List<ProvisioningPlan> getApplyablePlans(ProvisioningProject project, boolean accounts) 
        throws GeneralException {

        List<ProvisioningPlan> toApply = new ArrayList<ProvisioningPlan>();

        List<ProvisioningPlan> plans = project.getIntegrationPlans();
        if (plans != null) {
            ListIterator<ProvisioningPlan> it = plans.listIterator();
            while (it.hasNext()) {
                ProvisioningPlan plan = it.next();
                if (isApplyable(project, plan, accounts))
                    toApply.add(plan);
            }
        }

        ProvisioningPlan unPlan = project.getUnmanagedPlan();
        if (unPlan != null && isApplyable(project, unPlan, accounts))
            toApply.add(unPlan);

        if (toApply.size() == 0)
            toApply = null;

        return toApply;
    }

    /**
     * Return true if there is something in a plan that may be applied
     * immediately to an identity.  This is expected to be an
     * application plan, not an IIQ plan.
     *
     * This will be true if any of these conditions are met:
     *
     *    ARG_OPTIMISTIC_PROVISIONING
     *      This is a project option that causes every AccountRequest
     *      in the plan to be immediately applied to the corresponding
     *      Link in the identity.  
     *
     *    AccountRequest.ProvisioningResult.status=STATUS_COMMITTED
     *     If the connecor tells us that a request was committed, it
     *     will be immediately appled, even if optimistic provisioning is
     *     off.
     * 
     *    AttributeRequest.ProvisioningResult.status=STATUS_COMMITTED
     *     Same as above but at the attribute level rather than the
     *     account level.
     * 
     *    AccountRequest.ProvisioningResult.ResourceObject != null
     *     If the connecor sets the AccountRequest status to COMMITTED
     *     it may also return a ResourceObject containing all
     *     of the latest account attributes.  When this happens the
     *     attributes in this object are applied and the plan is ignored.
     *     Note that this changes what ARG_OPTIMISTIC_PROVISIONING will
     *     do though in practice you will not see them in combination.
     *
     *    AttributeRequest.islinkEdit
     *      This is a flag that may set on each AttributeRequest to 
     *      indicate that the change is being made directly to the 
     *      Link without sending a provisioning request.  You normally
     *      will not see these in combination with the other options
     *      but it is allowed.
     *
     * ObjectRequests
     *
     * ObjectRequest for ManagedAttributes are more complicated.  We'll 
     * get a single ObjectRequest for an MA but this may contain a 
     * mixture of AttributeRequests for system attributes like description
     * and owner and schema attributes provisioned by the connector.  
     * We always want to apply system attributes but the schema attributes
     * we have to wait for aggregation if the connector didn't say committed.
     * This is awkward  on several levels, the plan will contain things that
     * the connector may not recognize and the request state the connector
     * returns does not apply to every attribute in the requests.  Splitting
     * this up into two ObjectRequests has ordering issues on create.  Needs
     * more thought, for now consider everything applyable and let
     * applyObjectRequest sort out the mess.
     */
    private static boolean isApplyable(ProvisioningProject project, ProvisioningPlan plan, boolean accounts) {

        boolean applyable = false;
        boolean optimistic = project.getBoolean(PlanEvaluator.ARG_OPTIMISTIC_PROVISIONING);

        // kludge, see method comments about ObjectRequests
        if (!accounts) return true;

        // treat localUpdate like optimisticProvisioning
        // we actually shouldn't need this since PlanEvaluator
        // will store a result with STATUS_COMMITTED on each AccountRequest
        // which we'll find below
        if (project.getBoolean(PlanEvaluator.ARG_LOCAL_UPDATE))
            optimistic = true;

        // unmanaged plan won't have a result
        String planStatus = null;
        ProvisioningResult result = plan.getResult();
        if (result != null)
            planStatus = result.getStatus();

        //Queued is our default if status is not set 
        if (planStatus == null) {
            planStatus = ProvisioningResult.STATUS_QUEUED;
        }

        // if the overall status is failure, then we can stop now
        if (ProvisioningResult.STATUS_FAILED.equals(planStatus))
            return false;

        // even if optimistic provisioning is on, we still make sure
        // that the plan actually has something in it
        List requests;
        if (accounts)
            requests = plan.getAccountRequests();
        else
            requests = plan.getObjectRequests();

        if (requests != null) {
            for (int i = 0 ; i < requests.size() && !applyable ; i++) {
                AbstractRequest req = (AbstractRequest)requests.get(i);

                // first check result status
                String status = planStatus;
                ProvisioningResult res = req.getResult();
                if (res != null) 
                    status = res.getStatus();
                applyable = isApplyable(status, optimistic);

                // then link edits and optimism
                if (!applyable)
                    applyable = isApplyable(req.getAttributeRequests(), optimistic);

                if (!applyable)
                    applyable = isApplyable(req.getPermissionRequests(), optimistic);
            }
        }

        return applyable;
    }

    private static <T extends GenericRequest> boolean isApplyable(List<T> list, boolean optimistic) {

        boolean applyable = false;
        if (list != null && list.size() > 0) {
            for (T req : list) {
                ProvisioningResult result = req.getResult();
                String status = (result != null) ? result.getStatus() : null;
                // isLinkEdit could apply to object requests, it basically
                // means "localEdit" 
                if (req.isLinkEdit() || isApplyable(status, optimistic)) {
                    applyable = true;
                    break;
                }
            }
        }
        return applyable;
    }
    
    /**
     * Return true if we're simulating.
     * This disables locking and prevents the transaction from being committed.
     */
    private boolean isSimulating() {
        return (_project.getBoolean(PlanEvaluator.ARG_SIMULATE));
    }

    /**
     * Returns true if ARG_IDENTITY_NOT_REQUIRED is true.
     * ARG_IDENTITY_NOT_REQUIRED is used in the workflow 'Restore Deleted Objects'
     * for restoring deleted Object of Active Directory
     */
    private boolean isIdentityNotRequired() {
        return (_project.getBoolean(PlanEvaluator.ARG_IDENTITY_NOT_REQUIRED));
    }
}
