/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


/**
 * A class encapsulating various maintenance of the IdentityEntitlement
 * objects added in 6.0.
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.Link;
import sailpoint.object.LinkInterface;
import sailpoint.object.NativeChangeDetection;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Schema;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningRequest;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * An API class that handles dealing with native
 * changes to accounts.
 * 
 * Generate an "event" stored on the identity that 
 * can be picked up later by the refresh to invoke
 * behavior.  Typically launches a workflow on
 * a per identity basis.
 * 
 * This class is primarily called from two main
 * areas that include aggregation ( to store
 * off the detections ) and during PlanEvaluation
 * when native detections come through a 
 * Intercepter.
 *
 * TODO
 *  1) Set...
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 * @since 6.0
 * 
 */
public class NativeChangeDetector  {
    
    private static Log log = LogFactory.getLog(NativeChangeDetector.class);
    
    /**
     * Context needed for various things, like fetching the 
     * existing Triggers.
     */
    SailPointContext _context;    
    
    /**
     * List of NativeChange triggers that need to be evaluated.
     */
    List<IdentityTrigger> _triggers;
    
    /**
     * For now, just indicates if we've alrady loaded of the 
     * interesting triggers. 
     */
    boolean _initialized;
    
    /**
     * Give a context, which will be used to query for 
     * defined triggers.
     *  
     * @param context
     */
    public NativeChangeDetector(SailPointContext context) {
        _context = context;
        _initialized = false;
        _triggers = null;
    }
    
    /**
     * Load up an NativeChange triggers so we can match them as
     * we process identities.
     * 
     * @throws GeneralException
     */
    private void init() throws GeneralException {
        
        if ( _initialized ) return;
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("type", IdentityTrigger.Type.NativeChange));
        ops.add(Filter.eq("disabled", false));
        List<IdentityTrigger> triggers = _context.getObjects(IdentityTrigger.class, ops);
        if ( triggers != null && !triggers.isEmpty() ) {
            for (Iterator<IdentityTrigger> it=triggers.iterator(); it.hasNext(); ) {
                IdentityTrigger trigger = it.next();             
                trigger.load();
            }
            _triggers = triggers;
        }        
        _initialized = true;
    }
    
    /**
     * Simpler method interface, when the application is not
     * readily available we'll use the application from
     * the link.
     * 
     * @param identity
     * @param link
     * @throws GeneralException
     */
    public void detectDelete(Identity identity, Link link) 
        throws GeneralException {
        
        detectDelete(identity, link, link.getApplication());
    }
    
    /**
     * When an account is natively removed this method is called and
     * we store off a native change detection so it can be acted
     * upon at a later time.
     * 
     * Allow the application to be specified for cases where the
     * caller has already resolved it.
     * 
     * @param ident
     * @param link
     * @param app
     * @throws GeneralException
     */
    public void detectDelete(Identity identity, Link link, 
                             Application app ) 
        throws GeneralException {
        
        detectLinkChanges(identity, null, link, app, Operation.Delete);        
    }
    
    /**
     * This method uses the "transient" old attribute stored on the link 
     * during aggregation to compare the new and old values.
     *      
     * Not having an old value tells us the account is a create.
     * 
     * Storing these with the other trigger snapshots, since
     * they'll be processed and _cleaned-up_ by the trigger code after 
     * they are consumed. 
     *
     * @param ident
     * @param neuLink
     * @param app
     */
    public void detectCreateAndModify(Identity ident, 
                                      Attributes<String,Object> oldAttributes,
                                      Link neuLink, 
                                      Application app )
        throws GeneralException {
    
        Operation op = Operation.Modify;        
        if ( oldAttributes == null ) {
            op = Operation.Create;
        }        
        detectLinkChanges(ident, oldAttributes, neuLink, app, op);
    }         

    /**
     * Go through the oldAttributes and the neuLink and compute the
     * detected differences.
     * 
     * Also take into account the requests coming from IIQ so we
     * don't start reacting to things that we pushed in the first
     * place.
     * 
     * @param identity
     * @param neuLink
     * @param app
     * @param op
     * 
     * @throws GeneralException
     */
    private void detectLinkChanges(Identity identity,
                                   Attributes<String,Object> oldAttributes,
                                   Link neuLink, 
                                   Application app, 
                                   Operation op ) 
        throws GeneralException {
    
        if ( app == null || neuLink == null  ) {
            log.error("Account application or link was null when attempting to detect native changes." + app + " " + neuLink);
            return;
        }
        init();
        
        //        
        // Only if the application is specifically enabled for detection do 
        // we perform the analysis.
        //
        if ( shouldDetect(identity, app, op) ) {
            
            List<String> interestingAttributes = getNativeChangeDetectionAtrributes(app);
            if ( Util.size(interestingAttributes) == 0 ) {
                return;
            }

            List<ProvisioningPlan> pendingPlans = getPendingPlans(identity);
            
            // See if we initiated the request, if so return doing nothing...
            if ( findIIQAccountCreateDelete( neuLink, op, pendingPlans) ) {
                if ( log.isDebugEnabled() ) 
                    log.debug("Found pending IIQ plan. Filtering "+ op);
                return;
            }

            List<NativeChangeDetection> detections = identity.getNativeChangeDetections();
            if ( detections == null) {
                detections = new ArrayList<NativeChangeDetection>();
            }

            Schema accountSchema = app.getAccountSchema();
            boolean caseInsensitive = app.isCaseInsensitive();
            //
            // Do a diff and store away the detections so we can pick
            // them up sometime in the future at refresh time
            //
            NativeChangeDetection nativeChange = findExistingChangesForLink(detections, neuLink, op);

            boolean isNew = false;
            if ( nativeChange == null ) {
                isNew = true;
                nativeChange = new NativeChangeDetection();
                nativeChange.setOperation(op);
                if ( !Util.nullSafeEq(op, Operation.Modify) ) {
                    nativeChange.assimilate(neuLink);
                } else
                    nativeChange.assimilateAccountInfo(neuLink);
             }
            
            for ( String attrName : interestingAttributes ) {
                Object val =  neuLink.getAttribute(attrName);
                Object previous = null;
                if ( oldAttributes != null ){
                    previous = oldAttributes.get(attrName);
                }

                Difference diff = null;
                if(op.equals(Operation.Delete) && null == previous){
                    //bug19622 if deleting and the previous is null need to swap the values in the diff
                    diff = Difference.diff(val, previous, Integer.MAX_VALUE, caseInsensitive);
                } else {
                    diff = Difference.diff(previous, val, Integer.MAX_VALUE, caseInsensitive);
                }

                if ( diff != null ) {
                    diff.setAttribute(attrName);
                    if ( accountSchema != null ) {
                        AttributeDefinition attrDef = accountSchema.getAttributeDefinition(attrName);
                        if ( attrDef != null ) {
                            diff.setMulti(attrDef.isMulti());
                        }
                    }
                    diff = filterIIQAttributeChanges(neuLink, op, diff, pendingPlans);

                    // IIQMAG-3095 There is a window where the plans may have already been
                    // executed but the oldAttributes of the Link haven't been updated yet.
                    // Requested attributes may show up as native changes because the diff
                    // will compare the outdated Link attributes to the current ones (the
                    // current link attributes may contain the requested attributes). The
                    // pendingPlans used above in filterIIQAttributeChanges may be null in
                    // this case and the diff will remain unchanged. Because the plans have
                    // been executed those attributes may have been added as attribute
                    // assignments and need to be filtered out.
                    diff = filterRecentAttributeAssignments(neuLink, op, diff, identity, app);

                    if ( diff != null  ) {
                        nativeChange.add(diff);
                    }
                }
            }

            // only add it if there are some diffs that weren't filtered
            if ( !nativeChange.isEmpty() && isNew )
                detections.add(nativeChange);

            // set them on the identity
            identity.setNativeChangeDetections(detections);
        }
    }
    
    /**
     * Check to see if we have specific knowledge of a request that
     * caused the Create or the Delete of a link.
     * 
     * For updates we go through each of the changed attributes
     * and test to see if we requested each attribute
     * change.
     * 
     * @param link
     * @param app
     * @param nativeChange
     * @return true if the native change should be filtered
     * 
     * @throws GeneralException
     */
    private boolean findIIQAccountCreateDelete(Link link, 
                                               Operation operation,
                                               List<ProvisioningPlan> plans)
        throws GeneralException {
                
        if ( plans == null ) 
            return false;
        
        if ( operation == Operation.Create || operation == Operation.Delete ) {
            for ( ProvisioningPlan plan : plans ) {                    
                if ( plan == null ) 
                    continue;
                List<AccountRequest> acctReqs = plan.getAccountRequests();
                for ( AccountRequest req : acctReqs ) {
                    if ( req != null ) {
                        if ( matches(req, link, operation) ) {
                            return true;
                        }
                    }                        
                }
            }         
        } 
        return false;    
    }
    
    /**
     * 
     * Query back to the ProvisioningRequests table and see if IIQ
     * requested this change to be made or see if it appears 
     * to be a native change.
     * 
     * If we find a request that was approved and made by IIQ
     * we will assume it was something driven by IIQ and
     * ignore it as a native change.
     * 
     * In cases where we have optimistic provisioning turned
     * on OR when the integration returns committed we'll
     * update the link during provisioning and this 
     * becomes a non issue. In these cases the
     * native change won't be "detected" since the values
     * are already on the link at the time of 
     * the next aggregation.
     * 
     * When the integration/connector is ASYNC or the
     * actions are manual and there is a delay between when we 
     * instruct the connector to provision the changes and when
     * the changes are actually applied we run into issues. 
     * All of these type changes will appear to be native 
     * changes. This method is designed to helps us determine 
     * when IIQ was actually the cause of the native 
     * change.
     *
     * @param identity
     * @param link
     * @param app
     * @param diff
     * 
     * @return Difference
     */
    private Difference filterIIQAttributeChanges(Link link, 
                                                 Operation op, 
                                                 Difference diff,
                                                 List<ProvisioningPlan> plans) 
        throws GeneralException { 
        
        Difference filteredDiff = diff;
        if ( link != null && plans != null  && filteredDiff != null) {
            for (ProvisioningPlan plan : plans ) {
                if ( plan != null ) {
                    filterPlanItems(plan, link, op, filteredDiff);  
                }           
            }            
        }
        
        if ( filteredDiff.isMulti() && Util.size(filteredDiff.getAddedValues()) == 0  && Util.size(filteredDiff.getRemovedValues()) == 0 ) {
            // semantically the same, do it here instead of making the caller
            // do this..
            filteredDiff = null;    
        }
        return filteredDiff;
    }
    
    /**
     * 
     * Dig into the plan looking for any of the values
     * we found when we computed the differenced.
     * 
     * @param plan
     * @param link
     * @param op
     * @param diff
     */    
    private void filterPlanItems(ProvisioningPlan plan, 
                                 Link link, 
                                 Operation op,
                                 Difference diff) {
     
        
        List<AccountRequest> acctReqs = ( plan != null ) ? plan.getAccountRequests(): null;        
        if ( acctReqs == null || diff == null ) 
            return;
        

        
        for (AccountRequest req : acctReqs ) {        
            if ( req != null && matches(req, link, op) ) {
                List<AttributeRequest> attrReqs = req.getAttributeRequests();
                if ( attrReqs == null ) 
                    continue;
                
                // also compare the attribute values to see if we find this value
                // in a request
                for ( AttributeRequest attr : attrReqs ) {
                    if ( attr == null ) 
                        continue;
                    
                    String attrName = attr.getName();
                    if ( Util.nullSafeCompareTo(attrName, diff.getAttribute()) != 0 ) 
                        continue;                       

                    ProvisioningPlan.Operation attrOp = attr.getOp();
                    if ( attrOp == null ) 
                        attrOp = ProvisioningPlan.Operation.Add;
                    
                    if ( diff.isMulti() ) {                        
                        filterRequestedValues(diff, attr);
                    } else {                        
                        Object requestedValue = attr.getValue();
                        Object diffValue = diff.getNewValue();
                        if ( Util.nullSafeEq(ProvisioningPlan.Operation.Add, attrOp) || 
                             Util.nullSafeEq(ProvisioningPlan.Operation.Set, attrOp) ) {
                            if ( Util.nullSafeEq(requestedValue, diffValue) ) {
                                diff = null;     
                                break;
                            }
                        }
                    }
                }
            }
        }

    }
    
    /**
     * Remove things from the Diff that IIQ requested. 
     * 
     * Use the attribute request to figure out if the any of
     * the value(s) in the Difference should be filtered because
     * they were made by IIQ.
     * 
     * For Add and Remove we just want to make sure they show up in
     * the correct list. 
     * 
     * For Set, the computation is a little more difficult
     * because there may be one or more Set calls for a given change 
     * detection. For now, we will assume they must match exactly
     * during set for us to call it the same change.
     *
     * 
     * @param attrName
     * @param attrOp
     * @param vals
     * @param added
     * @param removed
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void filterRequestedValues(Difference diff,
                                       AttributeRequest req) {
                              
        ProvisioningPlan.Operation attrOp = req.getOperation();
        String attrName = diff.getAttribute();
        
        List<Object> vals = Util.asList(req.getValue());
        
        // we'll use these to filter by reference...
        List<String> added = diff.getAddedValues();
        if ( added == null )
            added = new ArrayList<String>();
        
        List<String> removed = diff.getRemovedValues();
        if ( removed == null ) 
            removed = new ArrayList<String>();

        if ( vals != null ) {
            if ( Util.nullSafeEq(ProvisioningPlan.Operation.Set, attrOp) ) {
                String detectedValueStr = diff.getNewValue();
                List detectedValues = Util.csvToList(detectedValueStr);
                Difference setDiff = Difference.diff(detectedValues, vals);
                if ( setDiff == null ) {
                    if ( log.isDebugEnabled() ) 
                        log.debug("Found pending IIQ attr request. Filtering "+ attrOp + " attr["+attrName+"] value["+vals+"]");    
                    diff = null;
                }
            } else
            // Go through each value and see if the values match what
            // we detected
            for ( Object o : vals ) {
                if ( o == null ) 
                    continue;
                String value = o.toString();                                                                
                if ( Util.nullSafeEq(ProvisioningPlan.Operation.Add, attrOp) ) { 
                    if ( added.contains(value) ) {
                        if ( log.isDebugEnabled() ) 
                            log.debug("Found pending IIQ attr request. Filtering "+ attrOp + " attr["+attrName+"] value["+value+"]");    
                        added.remove(value);
                    }
                } else 
                if ( Util.nullSafeEq(ProvisioningPlan.Operation.Remove, attrOp) ) {
                    if ( removed.contains(value) ) {
                        if ( log.isDebugEnabled() ) 
                            log.debug("Found pending IIQ attr request. Filtering " + attrOp + " attr["+attrName+"] value["+value+"]");    
                        removed.remove(value);
                    }
                } 
            } 
        }
    }

    /**
     *
     * Compare the AttributeAssignments of the Identity to the
     * detected native changes. There is a window where the plan may
     * have already been executed and an entitlement may have already
     * been applied to the identity as an attribute assignment.
     *
     * If we find an attribute assignment on the identity that matches a
     * native change then we can assume it was something driven by IIQ and
     * ignore it as a native change.
     *
     * @param link
     * @param op
     * @param diff
     * @param identity
     * @param application
     *
     * @return Difference
     */
    private Difference filterRecentAttributeAssignments(Link link,
                                                 Operation op,
                                                 Difference diff,
                                                 Identity identity,
                                                 Application application)
        throws GeneralException {

        Difference filteredDiff = diff;

        if ( link != null && identity != null  && filteredDiff != null && application != null) {

            // we'll use these to filter by reference...
            List<String> added = diff.getAddedValues();
            if ( null == added || added.isEmpty() ) {
                // No NCD was added, just return the original diff
                if ( log.isDebugEnabled() )
                    log.debug("diff contains no addedValues. Returning the original diff.");

                return filteredDiff;
            }

            for (String value : diff.getAllNewValues() ) {
                String attrName = diff.getAttribute();
                String appName = application.getName();
                String nativeId = link.getNativeIdentity();
                String instance = link.getInstance();
                
                if (identity.getAttributeAssignment(appName, nativeId, attrName, value, instance, null) != null) {

                    if ( Util.nullSafeEq(ProvisioningPlan.AccountRequest.Operation.Modify, op) ) {
                        // Remove the attribute from the addedValues list if it's there.
                        if ( added.contains(value) ) {
                            if ( log.isDebugEnabled() ) {
                                log.debug("Found match for attribute assignment. Removing attr["+attrName+"] value["+ value+"] from added values.");
                            }
                            added.remove(value);
                        }
                    }
                }
            }
        }

        if ( filteredDiff != null && filteredDiff.isMulti() && Util.size(filteredDiff.getAddedValues()) == 0  && Util.size(filteredDiff.getRemovedValues()) == 0 ) {
            // semantically the same, do it here instead of making the caller
            // do this..
            filteredDiff = null;
        }
        return filteredDiff;
    }


    /**
     * First off, check to see if the application participating in the detect 
     * native changes activities. This is enabled by toggling a boolean flag on 
     * the Application's  configuration options.
     * 
     * If that's enabled, look for an attribute named detectable
     * to help us scope the events down to the type of changes
     * the customer is interested in seeing.  By default this is only
     * Modify events.
     * 
     * Then, if both those are enabled, dig through the triggers and see if the identity 
     * matches the trigger.
     * 
     * If the trigger matches, return true otherwise false.
     * 
     * @param identity
     * @param app
     * @return
     * @throws GeneralException
     */
    private boolean shouldDetect(Identity identity, Application app, Operation op ) 
        throws GeneralException  {
        
        if ( Util.size(_triggers) == 0 ) {
            return false;
        }        
        if ( op == null ) 
            op = Operation.Modify;
        
        if ( app.isNativeChangeDetectionEnabled() ) {            
            // By default, if enabled support all operations. If the
            // app specifies an action use it 
            List<String> ops = (List<String>)app.getNativeChangeOperations();
            if ( ops != null ) {                
                if ( !ops.contains(op.toString())) {
                    return false;
                }
            } 
            // By default enable implies all operations
            Matchmaker matchmaker = new Matchmaker(_context);
            for ( IdentityTrigger trigger : _triggers) {
                if ( trigger == null ) 
                    continue;
                if ( trigger.matches(identity, identity, matchmaker, _context) ) {
                    return true;
                }
            }
        }
        return false;
    }
        
    /**
     * For now, include only the entitlement attributes by default.
     * 
     * Allow this to be any attribute specified by an application
     * attribute named "detectableAttributes" which should be defined
     * as a list of attribute names that are detection worthy.
     * 
     */
    private List<String> getNativeChangeDetectionAtrributes(Application app) {
        
        List<String> override  = null;        
        List<String> defaultAttrs  = app.getEntitlementAttributeNames();
        
        if ( Util.nullSafeCompareTo(app.getNativeChangeAttributeScope(), "userDefined") == 0 )  {
            override = app.getNativeChangeAttributes();
        } else
        if ( Util.nullSafeCompareTo(app.getNativeChangeAttributeScope(), "all") == 0 )  {
            Schema account = app.getAccountSchema();
            if ( account != null ) {
                override = account.getAttributeNames();                
            }
        }
        return ( Util.size(override) > 0 ) ? override : defaultAttrs;
    }
    
    /**
     * Look through the existing list of native changes and look one for 
     * a unique one for the Link and op combination.
     * 
     * @param current
     * @param link
     * @param op
     * @return
     */
    private NativeChangeDetection findExistingChangesForLink(List<NativeChangeDetection> current, LinkInterface link, Operation op) {
        if ( Util.size(current) > 0 ) {
            for ( NativeChangeDetection detection : current ) {
                if ( detection.matches(link) && Util.nullSafeEq(op, detection.getOperation()) ) {
                    return detection; 
                }
            }
        }
        return null;        
    }
    
    /**
     * Query the pending ProvisionnigRequests so that we can
     * filter out the changes that were requested by IIQ.
     * 
     * @param identity
     * @return
     * @throws GeneralException
     */
    private List<ProvisioningPlan> getPendingPlans(Identity identity) 
        throws GeneralException {

        List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        Iterator<Object[]> it = _context.search(ProvisioningRequest.class, ops, Util.csvToList("plan"));
        if ( it != null ) {
            while ( it.hasNext() ) {
                Object[] row = it.next();
                if ( row == null ) 
                    continue;
                ProvisioningPlan plan = (ProvisioningPlan)row[0];
                if ( plan != null )              
                    plans.add(plan);
            }                
        }
        return plans;
    }
    
    /**
     * See if the link matches the account request based
     * on nativeIdentity and instance.   
     * 
     * @param acct
     * @param link
     * @return
     */
    private boolean matches(AccountRequest acct, Link link, Operation op) {                
        if ( link != null && acct != null ) {
            if ( Util.nullSafeEq(op, acct.getOperation()) &&
                 Util.nullSafeCompareTo(link.getNativeIdentity(), acct.getNativeIdentity()) == 0 &&
                 Util.nullSafeCompareTo(link.getInstance(), acct.getInstance()) == 0 ) {
                return true;
            }
        }        
        return false;
    }      
}
