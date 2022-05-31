/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class used internally by PlanCompiler to process Identity attributes
 * that have application targets.  This is also known as "attribute synchronization".
 * 
 * Author: Kelly
 * 
 * Factored out of PlanCompiler in 6.3 because it was getting too big.
 *
 */

package sailpoint.provisioning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeTarget;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A class used internally by PlanCompiler to process Identity attributes
 * that have application targets.  This is also known as "attribute synchronization".
 */
public class IdentityAttributeSynchronizer {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(IdentityAttributeSynchronizer.class);

    /**
     * Parent compiler.
     */
    PlanCompiler _comp;

    //////////////////////////////////////////////////////////////////////  
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////  

    public IdentityAttributeSynchronizer(PlanCompiler comp) {
        _comp = comp;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Attribute Expansion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Expand any attributes in the IIQ plan to the targets defined in the
     * identity attributes.
     */
    public boolean expandIdentityAttributes() throws GeneralException {
        
        boolean expanded = false;
        
        ProvisioningProject project = _comp.getProject();
        ProvisioningPlan iiqPlan = project.getIIQPlan();
        if (null != iiqPlan) {
            ObjectConfig config = Identity.getObjectConfig();
            if (null != config) {
                Map<String,ObjectAttribute> atts = config.getTargetedAttributes();
    
                // Consider ordering by dependency at some point.  Not a huge
                // problem since attribute promotion always happens before
                // expansion.
                if (!Util.isEmpty(atts)) {
                    List<AccountRequest> acctReqs = iiqPlan.getAccountRequests();
                    if (!Util.isEmpty(acctReqs)) {
                        for (AccountRequest acctReq : acctReqs) {
                            List<AttributeRequest> attrReqs =
                                acctReq.getAttributeRequests();
                            if (!Util.isEmpty(attrReqs)) {
                                for (AttributeRequest attrReq : attrReqs) {
                                    expanded |= expandIdentityAttribute(attrReq, atts);
                                }
                            }
                        }
                    }
                }
            }
        }

        return expanded;
    }
    
    /**
     * Expand a single IIQ attribute request into the project.
     */
    private boolean expandIdentityAttribute(AttributeRequest attrReq,
                                            Map<String,ObjectAttribute> atts)
        throws GeneralException {
        
        boolean expanded = false;

        String attrName = attrReq.getName();
        ObjectAttribute attr = atts.get(attrName);

        // We have targets.  Yippee!
        if (null != attr) {
            ProvisioningPlan plan = new ProvisioningPlan();

            List<AttributeTarget> targets = attr.getTargets();
            Set<String> appsWithAssimilatedCreations = new HashSet<String>();
            for (AttributeTarget target : targets) {
                Application app = target.getApplication();
                Identity identity = _comp.getIdentity();
                List<Link> links = identity.getLinks(app);

                // The easy case - one account.  Just add the request.
                if (1 == Util.size(links)) {
                    expandIdentityAttribute(plan, attr, target, links.get(0), attrReq);
                    expanded = true;
                }
                else if (Util.size(links) > 1) {
                    // If provisioning all, add a request for each account.
                    if (target.isProvisionAllAccounts()) {
                        for (Link link : links) {
                            expandIdentityAttribute(plan, attr, target, link, attrReq);
                        }
                    }
                    else {
                        // If not provisioning all, add a request without a
                        // native identity.  This will cause account selection.
                        // TODO, we're broken here.  Account selection does not occur
                        // because the evaluator will set the native identity on the plan
                        // of the first account found by identity.getLink(app).
                        expandIdentityAttribute(plan, attr, target, null, attrReq);
                    }
                    expanded = true;
                }
                else {
                    // The user doesn't have any accounts on this app.  We don't
                    // allow attribute sync to create new accounts, but we do
                    // want to expand this target if there is a creation request
                    // in the project.  Pull an empty copy of the creation request 
                    // into the plan, if we haven't already, so that we can expand into it
                    String appId = app.getId();
                    if (!appsWithAssimilatedCreations.contains(appId)) {
                        AccountRequest creationRequest = _comp.getProject().getCreateOrModifyRequest(app);
                        if (creationRequest != null) {
                            appsWithAssimilatedCreations.add(appId);
                            AccountRequest copyOfCreationRequest = new AccountRequest();
                            copyOfCreationRequest.cloneRequestProperties(creationRequest);
                            // Null out the attribute requests because we're going to repartition them back into
                            // the project, and that will generate duplicate requests.  This may not seem
                            // like a big deal for a couple of attributes, but when we've done this 20 or 30 times,
                            // the amount of duplication gets out of hand.  Imagine three copies on the first pass,
                            // seven copies on the next pass, fifteen copies on the one after, etc., etc.
                            copyOfCreationRequest.setAttributeRequests(null);
                            plan.add(copyOfCreationRequest);
                        }
                    }

                    // Only expand if we were able to find a plan that we could expand into
                    if (appsWithAssimilatedCreations.contains(appId)) {
                        expandIdentityAttribute(plan, attr, target, null, attrReq);
                        expanded = true;
                    } else {
                        log.debug("Could not find an account to associate with application " + 
                                app.getName() + ".  Ignoring its target for the " + attrName + " attribute.");
                    }
                }
            }

            if (expanded) {
                // Slap this stuff into the project.
                _comp.partition(plan);
    
                // Add expansion items for every target value.  The plan should
                // have native identities after partitioning unless account
                // selection is required.
                _comp.addExpansionItems(plan, ExpansionItem.Cause.AttributeSync, attrName);
            }
        }
        
        return expanded;
    }

    /**
     * Expand the given IIQ source AttributeRequest into the given plan per the
     * target definition.  If the link is null this is either a creation request
     * or account selection is required.
     */
    private void expandIdentityAttribute(ProvisioningPlan plan,
                                         ObjectAttribute attr,
                                         AttributeTarget target,
                                         Link link,
                                         AttributeRequest srcReq)
        throws GeneralException {

        String appName = target.getApplication().getName();
        String instance = (null != link) ? link.getInstance() : null;
        String nativeIdentity = (null != link) ? link.getNativeIdentity() : null;

        AccountRequest acctReq =
            plan.getAccountRequest(appName, instance, nativeIdentity);
        if (null == acctReq) {
            acctReq = new AccountRequest();
            acctReq.setOperation(AccountRequest.Operation.Modify);
            acctReq.setApplication(appName);
            acctReq.setInstance(instance);
            acctReq.setNativeIdentity(nativeIdentity);
            plan.add(acctReq);
        }

        Object val = srcReq.getValue();

        // If there is a rule, let it transform the value.
        Rule rule = target.getRule();
        if (null != rule) {
            // If this is null do we still want to push it?  I think so.
            // For example, if an identity attribute has privacy = true
            // set, the rule may want to null out sensitive values on
            // target applications.
            val = transformValue(rule, val, srcReq, attr, target, link);
        }

        AttributeRequest targetAttrReq = new AttributeRequest();
        // We're assuming that attribute sync is authoritative and will
        // overwrite any other values (hence the Set operation).
        // Is this alright or do we need an option on the target that
        // specifices whether to merge or overwrite?
        targetAttrReq.setOperation(Operation.Set);
        targetAttrReq.setName(target.getName());
        targetAttrReq.setValue(val);
        acctReq.add(targetAttrReq);
    }
    
    /**
     * Run the identity attribute target transformation rule.
     */
    private Object transformValue(Rule rule, Object val,
                                  AttributeRequest sourceAttrReq,
                                  ObjectAttribute attr,
                                  AttributeTarget target,
                                  Link link)
        throws GeneralException {
        
        Map<String,Object> args = new HashMap<String,Object>();
        args.put("value", val);
        args.put("sourceIdentityAttribute", attr);
        args.put("sourceIdentityAttributeName", attr.getName());
        args.put("sourceAttributeRequest", sourceAttrReq);
        args.put("target", target);
        args.put("link", link);
        args.put("identity", _comp.getIdentity());
        args.put("project", _comp.getProject());

        return _comp.getContext().runRule(rule, args);
    }


}
