/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A workflow library with methods related to identity updates.
 *
 * Author: Jeff, Dan, others
 *
 *
 * Most of the methods are intended to be referenced with a "call" action
 * and take a WorkflowContext as an argument.  A few are intended
 * for use in rules and scripts and are static methods that have
 * random argument lists.  
 *
 * Categories of services:
 *
 * Misc Utilities
 *    fetch identity, locate manager, etc.
 *
 * Policy Checking
 *    simulate provisioning and check policies before submitting request
 *
 * Approval Sets
 *     assembly and assimilation of approval sets 
 * 
 * Refresh
 *    Identitizer refresh of individual or groups of identities
 *
 * Provisioning Forms
 *     build, present, and assimilate provisioning forms
 * 
 * Role Sunrise/Sunset
 *      assign or deassign a schedule role change
 *
 * Provisioning
 *     evaluation of a completed provisioning project
 * 
 * Refresh Task Completion
 *     special support for two-phase refresh
 * 
 * LCM Audit Events
 *     method to generate LCM request tracking events
 * 
 * Account Management
 *     methods to disable and enable application accounts
 * 
 */

package sailpoint.workflow;

import static sailpoint.service.IdentityResetService.Consts.Flows.UNLOCK_ACCOUNT_FLOW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;

import sailpoint.alert.AlertWorkflowHandler;
import sailpoint.api.CachedManagedAttributer;
import sailpoint.api.Differencer;
import sailpoint.api.Formicator;
import sailpoint.api.IdIterator;
import sailpoint.api.Identitizer;
import sailpoint.api.Identitizer.RefreshResult;
import sailpoint.api.InterrogatorFacade;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.Provisioner;
import sailpoint.api.RequestEntitlizer;
import sailpoint.api.RoleEventGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.Workflower;
import sailpoint.object.AccountSelection;
import sailpoint.object.Alert;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Comment;
import sailpoint.object.Configuration;
import sailpoint.object.Difference;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Form.Button;
import sailpoint.object.Identity;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.NativeChangeDetection;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QueryOptions;
import sailpoint.object.Recommendation;
import sailpoint.object.Request;
import sailpoint.object.Resolver;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleRequest;
import sailpoint.object.Rule;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.WorkflowCase;
import sailpoint.provisioning.IIQEvaluator;
import sailpoint.provisioning.PlanApplier;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.provisioning.PlanUtil;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.server.Auditor;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.service.ProvisioningTransactionService.TransactionDetails;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.transformer.IdentityTransformer;
import sailpoint.web.lcm.LcmAccessRequestHelper;
import sailpoint.web.messages.MessageKeys;

/**
 * Workflow library containing utilities for identity management.
 *
 */
public class IdentityLibrary extends WorkflowLibrary {

    private static Log log = LogFactory.getLog(IdentityLibrary.class);

    public IdentityLibrary() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Process Variables
    //
    // We need a place to hang constants for the workflows that
    // deal with identities, this is a good a place as any.
    //
    // These are workflow launch variables for the identity refresh,
    // identity update, and LCM workflows.  Since we commonly pass variables
    // as arguments with the same name, some of these are also
    // used as argument name constants, but we don't bother with another
    // set of ARG_ constants.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Workflow variable containing the name of the identity
     * we're dealing with for identity update and refresh workflows.
     */
    public static final String VAR_IDENTITY_NAME = "identityName";

    /**
     * The master ProvisioningPlan to be applied to the identity.
     */
    public static final String VAR_PLAN = "plan";

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Refresh Variables
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Transient workflow variable holding the Identitizer object
     * that launched the workflow.  
     * 
     * @ignore 
     * This is a performance hack so 
     * we can avoid creating a new Identitizer in all its glory
     * if the workflow doesn't end up suspending.
     */
    public static final String VAR_IDENTITIZER = "identitizer";

    /**
     * Transient variable holding the Identity object being 
     * refreshed.
     */
    public static final String VAR_IDENTITY = "identity";

    /**
     * Variable used in identity refresh workflows to hold options
     * for the Identitizer when we're ready to finish the refresh process.  
     */
    public static final String VAR_REFRESH_OPTIONS = "refreshOptions";

    /**
     * Variable holding a version of an Identity before we began the
     * refresh process.
     *
     * @ignore
     * TEMPORARY!  Need to work out a way to store this in 
     * an IdentityArchive so we don't have to drag it around in the
     * WorkflowCase?
     */
    public static final String VAR_PREVIOUS_VERSION = "previousVersion";

    /**
     * For identity refresh workflows, a variable containing a list
     * of IdentityChangeEvent objects fired from the pre-provisioning
     * triggers.
     */
    public static final String VAR_CHANGE_EVENTS = "changeEvents";

    /**
     * For identity refresh workflows, a ProvisioningProject containing the
     * questions to ask to flesh out the missing account attributes.
     */
    public static final String VAR_PROJECT = "project";

    /**
     * For identity refresh workflows, a Difference object holding
     * the change to the detected role list.
     */
    public static final String VAR_DETECTION_DIFFERENCE =
        "detectionDifference";

    /**
     * For identity refresh workflows, set to true to enable the
     * generation of work items to handle the unmanaged parts of 
     * the provisioning plan.
     */
    public static final String VAR_DO_MANUAL_ACTIONS =
        "doManualActions";

    /**
     * For identity refresh workflows, set to true to enable the manual
     * account selection in case of ambiguity.
     */
    public static final String ARG_ENABLE_MANUAL_ACCOUNT_SELECTION = 
        "enableManualAccountSelection";


    //////////////////////////////////////////////////////////////////////
    //
    // LCM Workflow Variables
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the LCM "flow" this workflow is used for.
     * Flows are a UI concept, we share the same workflow for several
     * flows but may need to conditionalize behavior based on the flow.
     */
    public static final String VAR_FLOW = "flow";

    /**
     * Variable for LCM workflows that specifies the names of policies
     * to check.
     */
    public static final String VAR_POLICIES = InterrogatorFacade.VAR_POLICIES;


    /**
     * Variable for LCM Provisioning used to store the List of Returned ProvisioningProjects
     * after splits
     */
    public static final String VAR_SPLIT_PROJECTS = "splitProjects";

    /**
     * Varaible for LCM Provisioning used to store the list of returned WorkItemComments after splits.
     */
    public static final String VAR_SPLIT_COMMENTS = "splitWorkItemComments";

    /**
     * Variable for LCM Provisioning used to store the list of ApprovalSets
     */
    public static final String VAR_SPLIT_APPROVALSETS = "splitApprovalSet";

    /**
     * Arg to determine if Split Provisioning plans with Overlapping creates should be combined
     */
    public static final String ARG_SPLIT_DISABLE_COMBINE = "splitDisableCombineCreates";

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Event Variables
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Variable that holds the IdentityTrigger for lifecycle event workflows.
     */
    public static final String VAR_TRIGGER = "trigger";

    /**
     * Variable that holds the IdentityChangeEvent for lifecycle event
     * workflows.
     */
    public static final String VAR_EVENT = "event";

    /**
     * Attribute key to get an old attribute value that was placed in the
     * attribute map of an approval item generated by a native change
     * detection for a single-valued attribute.
     */
    private static final String ATTR_DIFF = "difference";

    //////////////////////////////////////////////////////////////////////
    //
    // Predefined Form Owner Types
    //
    // identity
    // manager
    // applicationOwner
    // roleOwner
    //
    //////////////////////////////////////////////////////////////////////
    public static final String FORM_OWNER_IDENTITY = "identity";
    public static final String FORM_OWNER_MANAGER = "manager";
    public static final String FORM_OWNER_APPOWNER = "applicationOwner";
    public static final String FORM_OWNER_ROLEOWNER = "roleOwner";

    /**
     * Key for account selector create case
     */
    public static final String ACCOUNT_SELECTOR_CREATE = "doCreateOpt";

    /**
     * Attribute name for ApprovalItem to indicate it came from Identity Update workflow
     */
    public static final String ATTR_IDENTITY_UPDATE = "identityUpdate";

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the Identity object associated with this workflow.
     * 
     * Unlike other approval flows, identity workflows do not have
     * a copy of the "pending" identity in them.  Instead the workflow
     * contains a description of the desired changes in a ProvisioningPlan
     * and the name of the identity in the database.
     *
     * @ignore
     * !! What about the IdentityRefresh workflows, those will
     * have the partially refreshed Identity passed in so it dan 
     * 
     */
    private Identity getIdentity(WorkflowContext wfc) throws GeneralException {
        Identity identity = null;

        Attributes<String,Object> args = wfc.getArguments();
        String name = args.getString(VAR_IDENTITY_NAME);
        if (name == null) {
            // We have historically fallen back on this workflow variable
            // so we didn't have to pass an arg.  I no longer like doing this
            // but we have to support it for older flows.
            name = wfc.getString(VAR_IDENTITY_NAME);
        }

        if (name == null)
            log.error("Missing identity name");
        else {        
            SailPointContext context = wfc.getSailPointContext();
            identity = context.getObjectByName(Identity.class, name);
            if (identity == null)  {
                // djs: 
                // This used to log an error moved to debug because 
                // during IIQ create cases we won't have the identity
                // in the database
                //
                log.debug("Invalid identity: " + name);
            }
        }

        return identity;
    }

    /**
     * Return the name of the manager for an identity.
     * This is commonly the default approver.
     */
    public String getManager(WorkflowContext wfc) 
        throws GeneralException {
        
        String managerName = null;
        Attributes<String,Object> args = wfc.getArguments();
        String identityName = args.getString(VAR_IDENTITY_NAME);
        if (identityName != null) {
            SailPointContext con = wfc.getSailPointContext();
            Identity ident = con.getObjectByName(Identity.class, identityName);
            if (ident != null) {
                Identity manager = ident.getManager();
                if (manager != null)
                    managerName = manager.getName();
            }
        }
        return managerName;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Differencing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of a workflow variable to which calculateIdentityDifference
     * is to store the list of old role names.
     */
    public static final String ARG_OLD_ROLES_VARIABLE = "oldRoles";
    
    /**
     * The name of a workflow variable to which calculateIdentityDifference
     * is to store the list of new role names.
     */
    public static final String ARG_NEW_ROLES_VARIABLE = "newRoles";
    
    
    /**
     * The name of a workflow variable to which calculateIdentityDifference
     * is to store the list of links to add to identity
     */
    public static final String ARG_LINKS_TO_ADD = "linksToAdd";

    /**
     * The name of a workflow variable to which calculateIdentityDifference
     * is to store the list of links to add to identity
     */
    public static final String ARG_LINKS_TO_MOVE = "linksToMove";

    /**
     * The name of a workflow variable to which calculateIdentityDifference
     * is to store the list of links to remove from identity
     */
    public static final String ARG_LINKS_TO_REMOVE ="linksToRemove";
    
    public static final String ARG_PLAN = "plan";
    public static final String ARG_APPROVAL_SET_VARIABLE = "approvalSet";
    public static final String ARG_DISABLE_AUDIT = "disableAudit";
    public static final String ARG_HELP_TEXT = "helpText";

    // The historic renderer set for Approvals from the Identity Update workflow. This is
    // not actually used anymore, but we check it to inidcate if an approval came from updating
    // identity from the identity warehouse.
    public static final String IDENTITY_UPDATE_RENDERER = "identityUpdate.xhtml";

    /**
     * Derive a simplified representation of the changes being made to 
     * an identity for the approval work item.
     *
     * This is intended only for temporary backward compatibility with 
     * pre-5.0 approval workflows from the IIQ Define->Identities pages.
     *
     * @ignore
     * The main thing this needs to do is set the "oldRoles" and
     * "newRoles" workflow variables.  It would be more general if this
     * calculated a full IdentityDifference (or something similar) instead
     * so we could show changes to other attributes.  Need to revisit this
     * after 5.0 and try to do the same kinds of things that LCM does.
     */
    public Object calculateIdentityDifference(WorkflowContext wfc)
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        List oldRoles = null;

        Identity id = getIdentity(wfc);
        if (id != null) {
            List<Bundle> roles = id.getAssignedRoles();
            oldRoles = ObjectUtil.getObjectNames(roles);
        }

        // try not to hard code the variable names, let them 
        // be passed in, but fall back to the old names if not passed
        String oldRolesVar = args.getString(ARG_OLD_ROLES_VARIABLE);
        if (oldRolesVar == null) {
            oldRolesVar = ARG_OLD_ROLES_VARIABLE;
        }
        wfc.setVariable(oldRolesVar, oldRoles);

        // To get the new roles we have to pretent to apply
        // the master plan to the list of current roles.  This is
        // somewhat ugly, it would be nice to have some Provisioner
        // utilities to do an in-memory application of just the
        // IIQ part of the plan for impact analysis.

        List newRoles = new ArrayList<String>();
        if (oldRoles != null)
            newRoles.addAll(oldRoles);

        // Flag set to true if role requests exist in the plan
        boolean roleRequestsExist = false;

        Object o = args.get(VAR_PLAN);
        if (o instanceof ProvisioningPlan) {
            ProvisioningPlan plan = (ProvisioningPlan)o;
            // in theory could be more than one, we don't support that
            AccountRequest account = plan.getIIQAccountRequest();
            if (account != null) {
                List<AttributeRequest> attributes = account.getAttributeRequests();
                if (attributes != null) {
                    for (AttributeRequest att : attributes) {
                        String name = att.getName();
                        if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name)) {
                            newRoles = applyRequest(att, newRoles);
                            // Found a role request so set flag to true
                            roleRequestsExist = true;
                        }
                    }
                }
            }
        }

        // now that we know that role requests actually exist set the role related variables
        if (roleRequestsExist) {
            String var = args.getString(ARG_NEW_ROLES_VARIABLE);
            if (var == null) {
                var = ARG_NEW_ROLES_VARIABLE;
            }
            wfc.setVariable(var, newRoles);
        }
        else {
            // clear old roles var
            wfc.setVariable(oldRolesVar, null);
        }

        addLinksInformation(wfc);
        
        // jsl - 5.0p kludge: Marc wants some indiciation of attribute changes
        // we'll fake up an IdentityDifference object from the provisioning 
        // plan, this should be using nice args but don't require them
        o = wfc.get(ARG_PLAN);
        if (o instanceof ProvisioningPlan) {
            ApprovalSet aset = getApprovalSet((ProvisioningPlan)o);
            if (aset != null) {
                String var = args.getString(ARG_APPROVAL_SET_VARIABLE);
                if (var == null)
                    var = ARG_APPROVAL_SET_VARIABLE;
                wfc.setVariable(var, aset);
            }
        }

        return null;
    }
    
    /**
     * Build an ApprovalSet object holding the changes in a ProvisioningPlan.   
     * This is a temporary part for calculateIdentityDifferences added
     * for the 5.0p1 patch.  Need to sort out how to represent diffs in 
     * 5.1.
     */
    private ApprovalSet getApprovalSet(ProvisioningPlan plan)
        throws GeneralException {

        ApprovalSet set = null;
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                List<AttributeRequest> atts = account.getAttributeRequests();
                if (atts != null) {
                    for (AttributeRequest att : atts) {
                        // ignore role requests, we're still rendering them
                        // in the old way
                        String name = att.getName();
                        if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name))
                            continue;

                        // ignore link requests, rendering them differently
                        // for now
                        if (ProvisioningPlan.ATT_IIQ_LINKS.equals(name))
                            continue;

                        ApprovalItem item = new ApprovalItem();
                        item.setApplication(account.getApplication());
                        item.setName(name);
                        
                        Operation op = att.getOperation();
                        if (op != null)
                            item.setOperation(op.toString());
                        Object value = att.getValue();
                        if (value != null)
                            item.setValue(value.toString());

                        // store the password display value masked
                        if ( Util.nullSafeCompareTo(name, ProvisioningPlan.ATT_PASSWORD) == 0 ) {
                            item.setDisplayValue("******");
                        }

                        // Set attribute to indicate this is IdentityUpdate approval item. This is used by
                        // Approvals UI to render this approval set differently than normal, in the case other
                        // identity update specific variables are not set.
                        item.setAttribute(ATTR_IDENTITY_UPDATE, true);

                        if (set == null)
                            set = new ApprovalSet();
                        set.add(item);
                    }
                }
            }
        }

        return set;
    }

    private void addLinksInformation(WorkflowContext wfc)
        throws GeneralException {
        
        Identity id = getIdentity(wfc);
        if (id == null) {
            return;
        }
        
        Attributes<String,Object> args = wfc.getArguments();
        Object o = args.get(VAR_PLAN);
        if (!(o instanceof ProvisioningPlan)) {
            return;
        }
        ProvisioningPlan plan = (ProvisioningPlan)o;
        LinksInfoGenerator generator = new LinksInfoGenerator();
        generator.setContext(wfc.getSailPointContext());
        generator.setPlan(plan);
        generator.setPlanTarget(id);
        generator.generateLinksInfo();
        
        // try not to hard code the variable names, let them 
        // be passed in, but fall back to the old names if not passed
        String var = args.getString(ARG_LINKS_TO_ADD);
        if (var == null) {var = ARG_LINKS_TO_ADD;}
        wfc.setVariable(var, generator.getAddLinkInfos());

        var = args.getString(ARG_LINKS_TO_MOVE);
        if (var == null) {var = ARG_LINKS_TO_MOVE;}
        wfc.setVariable(var, generator.getMoveLinkInfos());
        
        var = args.getString(ARG_LINKS_TO_REMOVE);
        if (var == null) {var = ARG_LINKS_TO_REMOVE;}
        wfc.setVariable(var, generator.getDeleteLinkInfos());
        
    }

    /**
     * @exclude
     */
    public static class LinksInfoGenerator {
        private SailPointContext context;
        private Identity planTarget;
        private ProvisioningPlan plan;
        private List<String> moveLinkInfos = new ArrayList<String>();
        private List<String> deleteLinkInfos = new ArrayList<String>();
        private List<String> addLinkInfos = new ArrayList<String>();
        private Operation operation;

        public void setContext(SailPointContext context) {
            this.context = context;
        }
        public void setPlanTarget(Identity planTarget) {
            this.planTarget = planTarget;
        }
        public void setPlan(ProvisioningPlan plan) {
            this.plan = plan;
        }
        
        public List<String> getAddLinkInfos() {
            return this.addLinkInfos;
        }
        public List<String> getMoveLinkInfos() {
            return this.moveLinkInfos;
        }
        public List<String> getDeleteLinkInfos() {
            return this.deleteLinkInfos;
        }
        
        private String getMoveLinkDisplay(Link link, String destinationIdentityName) {
            return String.format("%s : %s (%s) => %s", link.getApplicationName(), link.getDisplayableName(), link.getNativeIdentity(), destinationIdentityName);
        }

        private String getDeleteLinkDisplay(Link link) {
            return String.format("%s : %s (%s)", link.getApplicationName(), link.getDisplayableName(), link.getNativeIdentity());
        }
        
        private String getAddLinkDisplay(Link link, Identity sourceIdentity) {
            return String.format("%s : %s (%s) => %s", link.getApplicationName(), link.getDisplayableName(), link.getNativeIdentity(), sourceIdentity.getName());
        }
        
        public void generateLinksInfo() throws GeneralException {
            List<AccountRequest> accounts = this.plan.getAccountRequests();
            if (accounts == null) {
                return;
            }
            for (AccountRequest account : accounts) {
                if (account == null) {
                    continue;
                }
                List<AttributeRequest> attributes = account.getAttributeRequests();
                if (attributes == null) {
                    continue;
                }
                for (AttributeRequest att : attributes) {
                    if (att == null) {
                        continue;
                    }
                    
                    if (att.getName().equals(ProvisioningPlan.ATT_IIQ_LINKS)) {
                        processLinkAttributeRequest(att);
                    }
                }
            }
        }
        
        private void processLinkAttributeRequest(AttributeRequest req) throws GeneralException {
            setOperation(req);
            if (this.operation.equals(Operation.Remove)) {
                // Note operation.remove comes from define => identity
                String destinationIdentityName = getDestinationIdentityName(req);
                if (destinationIdentityName == null) {
                    // means link is to be deleted
                    List<Link> links = IIQEvaluator.getLinksFromRequest(req, this.planTarget);
                    for (Link link : links) {
                        this.deleteLinkInfos.add(getDeleteLinkDisplay(link));
                    }
                } else {
                    // means link is to be moved to another identity, either or existing or not
                    List<Link> links = IIQEvaluator.getLinksFromRequest(req, this.planTarget);
                    for (Link link : links) {
                        this.moveLinkInfos.add(getMoveLinkDisplay(link, destinationIdentityName));
                    }
                }
            } else if (this.operation.equals(Operation.Add)) {
                Identity sourceIdentity = getSourceIdentity(req);
                if (sourceIdentity == null) {
                    throw new IllegalStateException("source identity arg must be defined");
                }
                List<Link> links = IIQEvaluator.getLinksFromRequest(req, sourceIdentity);
                for (Link link : links) {
                    this.addLinkInfos.add(getAddLinkDisplay(link, sourceIdentity));
                }
            } else {
                throw new IllegalStateException("unsupported operation: " + this.operation);
            }
        }
        
        private void setOperation(AttributeRequest req) {
            if (this.operation == null) {
                this.operation = req.getOperation();
            } else {
                if (!this.operation.equals(req.getOperation())) {
                    throw new IllegalStateException("Operation type add and remove can't be mixed at this point");
                }
            }
        }
        
        // to which identity will the link be moved
        private static String getDestinationIdentityName(AttributeRequest req) throws GeneralException {
            Attributes<String, Object> args = req.getArguments();
            if (args == null) {
                return null;
            }
            
            return (String) args.get(ProvisioningPlan.ARG_DESTINATION_IDENTITY);
        }
        
        // from which identity will the links be added to the targetIdentity
        private Identity getSourceIdentity(AttributeRequest req) throws GeneralException{
            Attributes<String, Object> args = req.getArguments();
            if (args == null) {
                return null;
            }
            
            String name = (String)args.get(ProvisioningPlan.ARG_SOURCE_IDENTITY);
            return this.context.getObjectByName(Identity.class, name);
        }
    }

    /**
     * @exclude
     * TODO:TQM REMOVE It is not needed
     */
    
    public static class LinkDifferencer {
        private SailPointContext context;
        private Identity planTarget;
        private ProvisioningPlan plan;
        private List<Link> newLinks = new ArrayList<Link>();
        private List<Link> linksToRemove = new ArrayList<Link>();
        private List<Link> linksToAdd = new ArrayList<Link>();
        private Operation operation;

        public void setContext(SailPointContext context) {
            this.context = context;
        }

        public void setPlanTarget(Identity planTarget) {
            this.planTarget = planTarget;
        }

        public void setPlan(ProvisioningPlan plan) {
            this.plan = plan;
        }
        
        public List<Link> getNewLinks() {
            return this.newLinks;
        }

        public void calculateLinkDifference() throws GeneralException {
            List<AccountRequest> accounts = this.plan.getAccountRequests();
            if (accounts == null) {
                return;
            }

            for (AccountRequest account : accounts) {
                if (account == null) {
                    continue;
                }
                List<AttributeRequest> attributes = account.getAttributeRequests();
                if (attributes == null) {
                    continue;
                }
                for (AttributeRequest att : attributes) {
                    if (att == null) {
                        continue;
                    }
                    
                    if (att.getName().equals(ProvisioningPlan.ATT_IIQ_LINKS)) {
                        processLinkAttributeRequest(att);
                    }
                }
            }
            
            for (Link link : this.planTarget.getLinks()) {
                this.newLinks.add(link);
            }
            
            for (Link link : this.linksToRemove) {
                removeLink(this.newLinks, link);
            }
            
            for (Link link : this.linksToAdd) {
                this.newLinks.add(link);
            }
        }
        
        private void removeLink(List<Link> links, Link link) {
            Iterator<Link> iterator = links.iterator();
            while (iterator.hasNext()) {
                Link oneLink = iterator.next();
                if (oneLink.getId().equals(link.getId())) {
                    iterator.remove();
                    return;
                }
            }
        }

        private void processLinkAttributeRequest(AttributeRequest req) throws GeneralException {
            setOperation(req);

            if (this.operation.equals(Operation.Remove)) {
                this.linksToRemove.addAll(IIQEvaluator.getLinksFromRequest(req, this.planTarget));
            } else if (this.operation.equals(Operation.Add)) {
                Identity sourceIdentity = getSourceIdentity(req);
                if (sourceIdentity == null) {
                    throw new IllegalStateException("source identity arg must be defined");
                }
                this.linksToAdd.addAll(IIQEvaluator.getLinksFromRequest(req, sourceIdentity));
            } else {
                throw new IllegalStateException("unsupported operation: " + this.operation);
            }
        }
        
        private void setOperation(AttributeRequest req) {
            if (this.operation == null) {
                this.operation = req.getOperation();
            } else {
                if (!this.operation.equals(req.getOperation())) {
                    throw new IllegalStateException("Operation type add and remove can't be mixed at this point");
                }
            }
        }

        // from which identity will the links be added to the targetIdentity
        private Identity getSourceIdentity(AttributeRequest req) throws GeneralException{
            Attributes<String, Object> args = req.getArguments();
            if (args == null) {
                return null;
            }
            
            String name = (String)args.get(ProvisioningPlan.ARG_SOURCE_IDENTITY);
            return this.context.getObjectByName(Identity.class, name);
        }
    }
    
    /**
     * Helper for calculateIdentityDifference, simulate the application
     * of an IIQ attribute request.
     */
    private List<String> applyRequest(AttributeRequest req, List<String> values) {

        Operation op = req.getOperation();
        if (op == Operation.Set) {
            values = listify(req.getValue());
        }
        else if (op == Operation.Add) {
            List<String> adds = listify(req.getValue());
            if (adds != null) {
                for (String value : adds) {
                    if (!values.contains(value))
                        values.add(value);
                }
            }
        }
        else if (op == Operation.Remove) {
            List<String> removes = listify(req.getValue());
            if (removes != null) {
                for (String value : removes) {
                    if (!values.contains(value))
                        values.remove(value);
                }
            }
        }

        return values;
    }

    /** 
     * This needs to be in Util or somewhere!
     */
    private List<String> listify(Object o) {

        List<String> list = null;

        if (o instanceof List) {
            // assume we can downcase, but should be smarter...
            list = (List<String>)o;
        }
        else if (o != null) {
            list = new ArrayList<String>();
            list.add(o.toString());
        }

        return list;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Policy Checking
    // 
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Policy violation checker for the new LCM and IIQ identity workflows.
     * This is based on the policy checker used by the old ArmWorkflowHandler.
     * 
     * Use an existing ProvisioningProject if we get one, otherwise support the
     * older interface that passes a ProvisioningPlan.
     * NOTE WELL: There are a few differences between the new way and the old way.
     *
     * @ignore 
     * When a plan was passed in we would first clone it so the original
     * could not be modified.  I think the way the compiler works now this
     * not cloning should be safe since we don't fundamentally change the
     * master plan.  I left the clone in just in case, but if this was necessary
     * then we either need to fix the side effect or clone the project too
     * if we get one.
     * 
     */
    public List<Map<String,Object>> checkPolicyViolations(WorkflowContext wfc)
        throws GeneralException {
        // note that arguments must be explicitly passed as step arguments,
        // we don't fall back to workflow variables
        Attributes<String,Object> stepArgs = wfc.getStepArguments();
        
        InterrogatorFacade sim = new InterrogatorFacade();
        return sim.checkPolicyViolations(wfc.getSailPointContext(), stepArgs);
    }
    

    /**
     * We return a fresh copy of an identity which will then be used to
     * add/remove roles, entitlements etc to check for any violations.
     *  
     * IMP: During we have to be guaranteed that IT WILL NOT BE COMMITTED
     * while we are using this testIdentity to check for violations.
     * 
     */
    public Identity getTestIdentityNew(WorkflowContext wfc, String name) 
        throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        return new InterrogatorFacade().getTestIdentityNew(context, name);
    }
    
    /**
     * @deprecated 
     * 
     * Build a detached copy of an Identity that we can use 
     * for policy checking and other questionable medical experiments.
     * 
     * If we find an existing Identity with the given name we clone it
     * to make sure that changes don't get persisted.  If there is no existing
     * identity, this is assumed to be a new identity being created in LCM
     * and we just make a stub.
     * 
     * @ignore
     * TQM: I am keeping this around because this is a public method 
     * and may have been used by workflows in the field.
     *
     * TODO: remove this and rename getTestIdentityNew to getTestIdentity
     * @see #getTestIdentityNew(WorkflowContext, String)
     */
    @Deprecated
    public Identity getTestIdentity(WorkflowContext wfc, String name) 
        throws GeneralException {

        Meter.enterByName("getTestIdentity");
        Identity identity = null;

        SailPointContext con = wfc.getSailPointContext();
        Identity original = con.getObjectByName(Identity.class, name);
        
        if (original == null) {
            identity = new Identity();
            identity.setName(name);
        }
        else {
            // jsl - out of curiosity, why the deep copy?  what was bad about
            // creating the stubbed out identity like what the catch clause
            // does?  As long as the roles are attached to the Hibernate
            // session it should be okay.
            // UIPreferences was causing issues on commit, prune it, 
            // I REALLY don't like this...
            boolean deepCopy = true;
            if (deepCopy) {
                try {
                    identity = (Identity) original.deepCopy((Resolver)con);
                    identity.setUIPreferences(null);
                } 
                catch (GeneralException ge) {
                    // fall back to using a stub instance with the same 
                    // assigned/detected roles
                    log.debug("Deep Cloning failed, using stub identity");
                    identity = new Identity();  
                    identity.setName(name);
                    identity.setDetectedRoles(original.getDetectedRoles());
                    identity.setAssignedRoles(original.getAssignedRoles());
                }
            }
            else {
                identity = new Identity();
                identity.setName(name);
                identity.setDetectedRoles(original.getDetectedRoles());
                identity.setAssignedRoles(original.getAssignedRoles());
                identity.setLinks(original.getLinks());
            }
        }
        
        Meter.exitByName("getTestIdentity");
        return identity;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Old Role Sunrise/Sunset
    //
    //////////////////////////////////////////////////////////////////////

    // !! NOTE: This section is obsolete, but I'm keeping it around
    // briefly for reference

    /**
     * Identity id to modify.  We store these in the Request as an id
     * since they can live a long time.
     */
    public static final String ARG_IDENTITY = "identity";

    /**
     * Role id to assign or deassign. 
     */
    public static final String ARG_ROLE = "role";

    /**
     * When truthy, indicates that we're operating on the detected
     * role list rather than the assigned list.
     *
     * This isn't used yet, but we're going to need it!
     */
    public static final String ARG_DETECTED = "detected";

    /**
     * Name of the user that was considered to be the assigner
     * of the role.  This is no longer necessary in 5.0 because we keep
     * the original assigner in the RoleAssignment metadata.
     * 
     * But we'll pass it along as the "requester" for the provisioning plan.
     * Also if this is an old request scheduled prior to 5.0 the
     * RoleMetadata will be missing so we use this name.
     */
    public static final String ARG_ASSIGNER = "assigner";

    /**
     * Anntoer name for ARG_ASSIGNER used in some older workflows.
     */
    public static final String ARG_ROLE_ASSIGNER = "roleAssigner";

    /**
     * When truthy, enables immediate provisioning of the role change.
     */
    public static final String ARG_PROVISION = "provision";

    /**
     * Perform a pending role assignment when the "sunrise" date is reached.
     * This is intended to be called by the workflow launched by the
     * event scheduled to perform the deferred assignment.  Normally there
     * will be RoleAssignment metadata containing details of the original
     * assignment.  If the RoleAssignment is missing, we'll still go ahead
     * and make the assignment but we'll have to guess at RoleAssignment.
     *
     * @ignore
     * !! This is the old interface and it expects to have everything
     * it needs to do the provisioning.  If this role required the gathering
     * of provisioning questions that would have to have been done
     * by now and left....where?   I guess it could go in a map inside the
     * RoleAssignment.  Without this, the activation workflow needs
     * to use the build/present/assimilate pattern to gather the
     * necessary information before provisioning.
     */
    public void activateRoleAssignment(WorkflowContext wfc) 
        throws GeneralException {

        handleRoleEvent(wfc, true);
    }

    /**
     * Perform a pending role deassignment when the "sunset" date is reached.
     * This is intended to be called by the workflow launched by the
     * event scheduled to perform the deferred deassignment.  Normally there
     * will be RoleAssignment metadata containing details of the original
     * assignment.  If the RoleAssignment is missing, we'll still go ahead
     * and make the deassignment.
     */
    public void deactivateRoleAssignment(WorkflowContext wfc) 
        throws GeneralException {

        handleRoleEvent(wfc, false);
    }

    // old name - temporary compatibility
    public void deActivateRoleAssignment(WorkflowContext wfc)
        throws GeneralException {
        deactivateRoleAssignment(wfc);
    }

    /**
     * Inner method to handle role sunrise and sunset assignments.
     * 
     * There are two ways we could go here.  First set up a provisioning
     * plan with a role request that drops either the sunrise or sunset
     * dates and let provisioner do the work. 
     *
     * Alternately we can just modify the assigned role list and the
     * associated RoleAssignment directly since we know what to do
     * then let the Provisioner "reconcile" the current assignments.
     *
     * The effect is about the same, though a full reconcile may do
     * things that aren't related to this particular role.
     * 
     */
    private void handleRoleEvent(WorkflowContext wfc, boolean assign) 
        throws GeneralException {

        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        // this will be an id in 5.0 requests, a name in 4.0 requests
        String identityId = args.getString(ARG_IDENTITY);
        if (identityId == null)
            throw new GeneralException("Missing argument: identity");

        String roleId = args.getString(ARG_ROLE);
        if (roleId == null)
            throw new GeneralException("Missing argument: role");

        // fetch the identity, we don't have to lock it yet, that will
        // be done Provisioner when it executes the plan, still it might
        // be wise to lock early so our analysis doesn't change?
        Identity ident =  spcon.getObjectById(Identity.class, identityId);
        if ( ident == null )
            throw new GeneralException("Invalid identity id: " + identityId);

        // provisioner would catch this eventually, but get it early
        Bundle role =  spcon.getObjectById(Bundle.class, roleId);
        if (role == null) 
            throw new GeneralException("Invalid role id: " + roleId);

        // supposed to have this in 5.0, but it will be missing in 4.0
        
        // Bug #19935 US3105 TA4868
        // This whole method is probably obsolete and is not called by IIQ code, but on the off chance that 
        // a custom workflow invokes it, we will look for an assignment id in the args and use it to find the correct
        // role assignment. If no assignment id is available, we'll use the deprecated getRoleAssignment which takes
        // a role as argument. If the identity has multiple role assignments for the same role, then this method
        // will just return the first role assignment found and drop a warning into the logs.
        //
        // The correct action for the customer to take if they see these warnings is to use the new workflow for 
        // scheduled role assignment and deassignment, which is Scheduled Assignment found in src/config/workflow.xml.  
        // It expects an assignmentId to be passed into the workflow and passes that to 
        // IdentityLibrary.compileScheduledAssignmentProject which knows to use the assignmentId to locate 
        // the RoleAssignment.
        
        String assignmentId = args.getString(RoleEventGenerator.ARG_ASSIGNMENT_ID);
        RoleAssignment assignment = ident.getRoleAssignmentById(assignmentId);
        if (assignment == null) {
            assignment = ident.getFirstRoleAssignment(role);
        }
        if (assignment == null) {
            log.warn("Missing RoleAssignment metadata for role: " + roleId);
        }

        // build a plan to represent the change

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);
        AccountRequest account = new AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        plan.add(account);
        AttributeRequest att = new AttributeRequest();
        account.add(att);

        // pick the right list 
        if (args.getBoolean(ARG_DETECTED))
            att.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
        else
            att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);

        att.setValue(role);

        if (assign) {
            att.setOperation(Operation.Add);
            // remove the sunrise date, but preserve the sunset
            // todo rshea TA4710: can we just get all the assignments for the role and take the latest sunrise date?
            if (assignment != null)
                att.setRemoveDate(assignment.getEndDate());
        }
        else {
            att.setOperation(Operation.Remove);
        }

        // Pass the step arguments for provisioner options.
        // This is alsy where you can pass random things for script args.
        Attributes<String,Object> provArgs = 
            getProvisionerArguments(wfc.getStepArguments());

        // The map passed to the constructor may contains options
        // for compilation and evaluation. These will be stored in the
        // returned project.
        Provisioner p = new Provisioner(wfc.getSailPointContext(), provArgs);

        // our "provision" arg must be on to enable provisioning
        // !! should this be on by default?
        if (!provArgs.getBoolean(ARG_PROVISION))
            p.setArgument(PlanCompiler.ARG_NO_ROLE_EXPANSION, "true");

        // If we had this from the Request, pass it along as the requester
        // for auditing.  If this is a 4.0 request there will be no RoleMetadata
        // and this will be used as the assigner for the metadata.
        String assigner = getRoleAssigner(wfc);
        p.setRequester(assigner);

        // The argument map in this method is used for "script args"
        // that are passed to any scripts or rules in the Templates
        // or Fields.  Here we use the step args for both the
        // options to the Provisioner and the script args during compilation.
        ProvisioningProject project = p.compile(plan, provArgs);

        if (project.hasQuestions()) {
            // nothing we can do about it here
            log.warn("Project has unanswered questions");
        }

        p.execute(project);
    }

    /**
     * Get the name to use as the assigner of a role.
     * This is what the pre-5.0 workflows did, we no longer need it
     * because the assigner was left in RoleAssignment metadata but we
     * use it as the "requester" of the plan for auditing.
     */
    private String getRoleAssigner(WorkflowContext wfc) {

        String assigner = null;

        Attributes<String,Object> args = wfc.getArguments();
        assigner = args.getString(ARG_ASSIGNER);
        if (assigner == null) {
            // another convention, was this ever actually used?
            assigner = args.getString(ARG_ROLE_ASSIGNER);
        }

        // fall back to the case launcher
        if (assigner == null) {
            WorkflowCase wfcase = wfc.getWorkflowCase();
            assigner = wfcase.getLauncher();
            if (assigner == null) {
                // really shouldn't happen
                assigner = "workflow";
            }
        }

        return assigner;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scheduled Assignments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is the common handler for all forms of scheduled assignment:
     * assigned roles, assigned entitlements, and requests for permitted roles.
     */
    public ProvisioningProject compileScheduledAssignmentProject(WorkflowContext wfc) 
        throws GeneralException {

        ProvisioningProject project = null;

        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        // We don't actually need the Date, it will also be stored
        // in the RoleAssignment, RoleRequest, or AttributeAssignment

        String identityId = args.getString(RoleEventGenerator.ARG_IDENTITY);
        String assigner = args.getString(RoleEventGenerator.ARG_ASSIGNER);
        String eventType = args.getString(Request.ATT_EVENT_TYPE);
        String assignmentId = args.getString(RoleEventGenerator.ARG_ASSIGNMENT_ID);
        String roleId = args.getString(RoleEventGenerator.ARG_ROLE);
        String appId = args.getString(RoleEventGenerator.ARG_APPLICATION);
        String instance = args.getString(RoleEventGenerator.ARG_INSTANCE);
        String accountId = args.getString(RoleEventGenerator.ARG_ACCOUNT_ID);
        String attributeName = args.getString(RoleEventGenerator.ARG_NAME);
        Object attributeValue = args.get(RoleEventGenerator.ARG_VALUE);
        
        // The enableManualAccountSelection workflow arg will affect how we compile scheduled assignment projects.
        boolean enableManualAccountSelection = Util.nullSafeEq(args.getString(ARG_ENABLE_MANUAL_ACCOUNT_SELECTION), "true");

        if (identityId == null)
            throw new GeneralException("Missing argument: identity");

        if (eventType == null) 
            throw new GeneralException("Missing argument: eventType");

        // fetch the identity, we don't have to lock it yet, that will
        // be done Provisioner when it executes the plan, still it might
        // be wise to lock early so our analysis doesn't change?
        Identity ident =  spcon.getObjectById(Identity.class, identityId);
        if ( ident == null )
            throw new GeneralException("Invalid identity id: " + identityId);

        ProvisioningPlan plan = null;

        if (RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
            RoleEventGenerator.EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType) ||
            RoleEventGenerator.EVENT_TYPE_ROLE_PROVISIONING.equals(eventType) ||
            RoleEventGenerator.EVENT_TYPE_ROLE_DEPROVISIONING.equals(eventType)) {

            // provisioner would catch this eventually, but get it early
            Bundle role =  spcon.getObjectById(Bundle.class, roleId);
            if (role == null) 
                throw new GeneralException("Invalid role id: " + roleId);

            plan = compileScheduledRolePlan(eventType, ident, assignmentId, role, enableManualAccountSelection);
        }
        else if (RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_ASSIGNMENT.equals(eventType) ||
                 RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT.equals(eventType)) {

            Application app = spcon.getObjectById(Application.class, appId);
            if (app == null)
                throw new GeneralException("Invalid application id: " + appId);

            plan = compileScheduledAttributePlan(eventType, ident, app, instance,
                                                 accountId, attributeName, attributeValue, assignmentId);
        }

        if (plan == null)
            throw new GeneralException("Invalid event type: " + eventType);

        else {
            if (log.isInfoEnabled()) {
                log.info("Compiling assignment plan:");
                log.info(plan.toXml());
            }

            // Pass the step arguments for provisioner options.
            // This is alsy where you can pass random things for script args.
            Attributes<String,Object> provArgs = 
                getProvisionerArguments(wfc.getStepArguments());

            // The map passed to the constructor may contains options
            // for compilation and evaluation. These will be stored in the
            // returned project.
            Provisioner p = new Provisioner(wfc.getSailPointContext(), provArgs);
            p.setRequester(assigner);
            p.setSource(Source.LCM);

            // The argument map in this method is used for "script args"
            // that are passed to any scripts or rules in the Templates
            // or Fields.  Here we use the step args for both the
            // options to the Provisioner and the script args during compilation.
            project = p.compile(plan, provArgs);
            
            //warn the user that having unanswered questions on a scheduled request
            //is not a good idea
            if (project.hasQuestions()) {
                log.warn("A scheduled request has been submitted that will result in questions and cannot continue until those questions are answered.");
            }
        }

        return project;
    }

    /**
     * Build the master provisioning plan for a scheduled role request.
     * The code here is closely related to what RoleEventGenerator builds,
     * should really have them together.
     *
     * In case it isn't clear we can get here with 4 events:
     *
     *    EVENT_TYPE_ROLE_ASSIGNMENT - top level assignment
     *    EVENT_TYPE_ROLE_DEASSIGNMENT - top level deassignemnt
     * 
     *    EVENT_TYPE_ROLE_PROVISIONING - permitted role assignment
     *    EVENT_TYPE_ROLE_DEPROVISIONING - permitted role deassignment
     * 
     * The first pair operate on the assignedRoles list and the second
     * on the detectedRoles list.  The use of the word "provisioning" makes
     * it sound more general than it is, these are just used for 
     * permitted roles by the system.
     *
     * If an assignmentId is passed it must match a top-level RoleAssignment 
     * in the Identity.  If that fails the request is ignored.
     *
     * If an assignmentId is not passed it is an old-style request
     * and we have to continue matching on the first one by name.
     */
    private ProvisioningPlan compileScheduledRolePlan(String eventType,
                                                      Identity ident,
                                                      String assignmentId,
                                                      Bundle role,
                                                      boolean enableManualAccountSelection)
        throws GeneralException {

        // build a plan to represent the change
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);
        AccountRequest account = new AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        plan.add(account);
        plan.setSource(Source.LCM);
        AttributeRequest attreq = new AttributeRequest();
        account.add(attreq);

        attreq.setValue(role);

        // this applies to both assigns and permits
        attreq.setAssignmentId(assignmentId);

        boolean adding = (RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
                          RoleEventGenerator.EVENT_TYPE_ROLE_PROVISIONING.equals(eventType));

        if (adding)
            attreq.setOperation(Operation.Add);
        else
            attreq.setOperation(Operation.Remove);

        if (RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
            RoleEventGenerator.EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType)) {
            
            // we're modifying the assignment list
            attreq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);



            RoleAssignment assignment = getRoleAssignment(ident, assignmentId, role, false);

            if (assignment != null) {
                if (adding) {
                    // this is the sunrise event, clear the sunrise date
                    // but keep the sunset date if any
                    attreq.setRemoveDate(assignment.getEndDate());
                    // when enabling manual account selection, clear out any existing RoleTarget.
                    if (enableManualAccountSelection) {
                        assignment.setTargets(null);
                    }
                } else {
                    // If removing an assigned role that was sourced from rule, set the negative flag so we keep a negative assignment
                    if (RoleEventGenerator.EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType) && !assignment.isManual()) {
                        attreq.put(ProvisioningPlan.ARG_NEGATIVE_ASSIGNMENT, true);
                    }
                }
            }
        }
        else {
            // we're modding the detected list
            attreq.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);

            Date endDate = null;
            RoleAssignment assignment = getRoleAssignment(ident, assignmentId, role, true);

            if (assignment != null) 
                endDate = assignment.getEndDate();
            else {
                // didn't match a RoleAssignemnt, look for an old RoleRequest
                RoleRequest request = ident.getRoleRequest(role);
                if (request != null)
                    endDate = request.getEndDate();
                else {
                    log.warn("Missing RoleRequest metadata for role: " + role.getName());
                }
            }
            
            if (adding) {
                // this is the sunrise event, clear the sunrise date
                // but keep the sunset date if any
                attreq.setRemoveDate(endDate);
            }
        }
        
        return plan;
    }
    
    /**
     * Locate the RoleAssignment that corresponds to a Request.
     * If an assignmentId is passed it must match, if an assignmentId
     * is not passed this must be an old assigned role request and we will match
     * by name but only for the assignment list.  If this is an old
     * permit request we'll handle it in the caller with the old
     * RoleRequest model.
     */
    private RoleAssignment getRoleAssignment(Identity ident,
                                             String assignmentId,
                                             Bundle role,
                                             boolean permitted) {

        RoleAssignment found = null;

        if (assignmentId != null) {
            found = ident.getRoleAssignmentById(assignmentId);
            // we really shouldn't guess if this happens
            if (found == null) 
                log.warn("Invalid assignmentId in role request: " + assignmentId);
            else if (permitted) {
                // we found the parent assignment, now have to look for the
                // permitted assignment
                RoleAssignment top = found;
                found = found.getPermittedRole(role);
                if (found == null) {
                    log.warn("Missing permit RoleAssignment for role " + 
                             role.getName() + 
                             " in " + top.getRoleName());
                }
            }
        }
        else if (!permitted) {
            // have to assume there is only one, and pick the first
            found = ident.getFirstRoleAssignment(role);
            if (found == null) {
                // this is supposed to exist, if not no sunset
                log.warn("Missing RoleAssignment for role: " + role.getName());
            }
        }

        return found;
    }
    
    /**
     * Build the master provisioning plan for a scheduled attribute assignment.
     */
    private ProvisioningPlan compileScheduledAttributePlan(String eventType,
                                                           Identity ident,
                                                           Application app,
                                                           String instance,
                                                           String nativeIdentity,
                                                           String attributeName,
                                                           Object attributeValue,
                                                           String assignmentId)
        throws GeneralException {

        // !! Native identity needs thought.  Currently it can come in from
        // the AttributeAssignment but I'm not sure that's right.
        if (nativeIdentity == null) {
            // leave it null and make plan compiler pick/create one?
        }

        // build a plan to represent the change
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);
        AccountRequest account = new AccountRequest();
        account.setApplication(app.getName());
        account.setInstance(instance);
        account.setNativeIdentity(nativeIdentity);
        plan.add(account);
        plan.setSource(Source.LCM);

        AttributeRequest attreq = new AttributeRequest();
        account.add(attreq);
        
        attreq.setName(attributeName);
        attreq.setValue(attributeValue);

        AttributeAssignment assignment = getAttributeAssignment(ident, app, instance,
                                                                nativeIdentity, attributeName,
                                                                attributeValue, assignmentId);
        if (assignment == null) {
            // this is supposed to exist, if not no sunset
            log.warn("Missing AttributeAssignment metadata for applciation: " + 
                     app.getName());
        }

        //needs an additional option to update the AttributeAssignment
        attreq.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");
        attreq.setAssignmentId(assignmentId);

        // TODO: could check the schema and use Operation.Set for single valued...
        if (RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT.equals(eventType)) {
            attreq.setOperation(Operation.Remove);
        }
        else { 
            attreq.setOperation(Operation.Add);
            if (assignment != null)
                attreq.setRemoveDate(assignment.getEndDate());
        }

        return plan;
    }
    
    /**
     * Locate the AttributeAssignment metadata that matches the arguments
     * in the Request.  
     *
     * TODO: !! Decide what to do about nativeIdentity
     */
    private AttributeAssignment getAttributeAssignment(Identity ident,
                                                       Application app,
                                                       String instance,
                                                       String accountId,
                                                       String attributeName,
                                                       Object attributeValue,
                                                       String assignmentId)
        throws GeneralException {


        AttributeAssignment found = null;

        List<AttributeAssignment> assignments = ident.getAttributeAssignments();
        if (assignments != null) {
            for (AttributeAssignment a : assignments) {

                if (safeEqual(app.getId(), a.getApplicationId()) &&
                    safeEqual(instance, a.getInstance()) &&
                    safeEqual(attributeName, a.getName()) &&
                    safeEqual(attributeValue, a.getValue()) &&
                    safeEqual(assignmentId, a.getAssignmentId())) {

                    // TODO: What to do about accountId
                    if (safeEqual(accountId, a.getNativeIdentity())) {
                        found = a;
                        break;
                    }
                }
            }
        }

        return found;
    }
    
    /**
     * Returns list of attribute assignments that match the given application and have non-null start dates
     * before  now
     * @param ident Identity
     * @param app Application
     * @return AttributeAssignments with non-null start dates before the current date
     */
    private List<AttributeAssignment> getStartDatedAttributeAssignmentsForApp(Identity ident, Application app) {
        List<AttributeAssignment> found = new ArrayList<AttributeAssignment>();

        if (ident != null) {
            List<AttributeAssignment> assignments = ident.getAttributeAssignments();
            for (AttributeAssignment a : Util.iterate(assignments)) {
                if (safeEqual(app.getId(), a.getApplicationId()) && 
                    a.getStartDate() != null && 
                    a.getStartDate().before(new Date())) {
                    found.add(a);
                }
            }
        }

        return found;
    }
    
    /**
     * Returns list of any role assignments with non null start dates before now.
     * @param ident
     * @return list of any role assignments with non null start dates before now.
     */
    private List<RoleAssignment> getStartDatedRoleAssignments(Identity ident) {
        List<RoleAssignment> found = new ArrayList<RoleAssignment>();

        if (ident != null) {
            List<RoleAssignment> assignments = ident.getRoleAssignments();
            for (RoleAssignment a : Util.iterate(assignments)) {
                if (a.getStartDate() != null && a.getStartDate().before(new Date())) {
                    found.add(a);
                }
            }
        }

        return found;
    }

    /**
     * Helper for getAttributeAssignment.
     * If one side is null the other side must be null,.
     */
    private boolean safeEqual(Object o1, Object o2) {

        return Differencer.objectsEqual(o1, o2);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Refresh
    //
    //////////////////////////////////////////////////////////////////////
    
    private void refreshInternal(SailPointContext ctx, Identitizer identitizer, String identityId)
        throws GeneralException {
        
        Identity identity = ObjectUtil.lockIdentity(ctx, identityId);
        if (identity == null) {
            throw new GeneralException("Invalid identity: " + identityId);
        }
        if ( log.isDebugEnabled() ) {
            log.debug("Refreshing ["+identity.getName()+"]");
        }

        RefreshResult refreshed = null;
        try {
            refreshed = identitizer.refresh(identity);
            // iiqetn-4759 - By calling Identitizer.refresh(identity) the Identity may be
            // modified and already committed. This was true in the case when processing the 
            // triggers caused changes to some of the identity attributes and the identity
            // modified had a different reference than this one. In this case calling 
            // saveObject() and commitTransaction() will overwrite the identity with
            // with the state of the Identity before Identitizer.refresh(identity) was called.
            // This is also similar to the code in RefreshWorker.process() in 
            // IdentityRefreshExecutor.
        } finally {
            if (refreshed == null || !refreshed.deleted) {
                identity = ctx.getObjectById(Identity.class, identity.getId());
                if (identity != null) {
                    try {
                        ObjectUtil.unlockIdentity(ctx, identity);
                    } catch (Throwable t) {
                        log.error("Unable to release lock; Decache and try again", t);
                        try {
                            ctx.decache();
                            ctx.rollbackTransaction();
                            identity = ctx.getObjectById(Identity.class, identityId);
                            if (identity != null) {
                                ObjectUtil.unlockIdentity(ctx, identity);
                            }
                        } catch (Throwable t2) {
                            log.error("Unable to release lock after decache", t2);
                        }
                    }
                }
            }
        }
    }

    /**
     * Perform an Identitizer refresh on one identity.
     * All step arguments will be passed into the Identitizer as arguments
     * to control what happens during the refresh.
     */
    public void refreshIdentity(WorkflowContext wfc) 
        throws GeneralException {

        // Note that we use getStepArguments since we're going to pass
        // the lot to Identitizer() during construction.  Identitizer can
        // launch workflows and passes everything it gets to the workflow as
        // task args.  So we don't want to include non-serializeable things
        // like "handler" and all the implicit workflow args.
        Attributes<String,Object> args = wfc.getStepArguments();

        String identityId = args.getString(ARG_IDENTITY);
        if (identityId == null) {
            // not passed look for this common variable convention
            identityId = args.getString(VAR_IDENTITY_NAME);
            if (identityId == null)
                throw new GeneralException("Missing identity name");
        }

        SailPointContext spcon = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        spcon.decache();

        Identitizer identitizer = new Identitizer(spcon, args);
        identitizer.setRefreshSource(Source.Workflow, wfc.getWorkflow().getName());
        refreshInternal(spcon, identitizer, identityId);
    }

    public static final String ARG_IDENTITIES_WITH_ROLES = "identitiesWithRoles";
    public static final String ARG_FILTER_STRING = "filterString";
    public static final String ARG_IDENTITY_NAMES = "identityNames";

    /**
     * Perform an Identitizer refresh on a set of identities.
     *
     * @ignore
     * !! jsl - We have the potential for cache bloat here if the number
     * of identities is large.  Should be using a CacheTracker or 
     * even better an instance of IdentityRefreshExecutor since it does
     * all the work we need, just simulate the task execution environment.
     */
    public void refreshIdentities(WorkflowContext wfc) throws GeneralException {

        SailPointContext spcon = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        spcon.decache();

        // Note that we use getStepArguments since we're going to pass
        // the lot to Identitizer() during construction.  Identitizer can
        // launch workflows and passes everything it gets to the workflow as
        // task args.  So we don't want to include non-serializeable things
        // like "handler" and all the implicit workflow args.
        Attributes<String,Object> args = wfc.getStepArguments();

        List<String> names = new ArrayList<String>();
        String identityName = args.getString(VAR_IDENTITY_NAME);
        String withRolesAssigned = args.getString(ARG_IDENTITIES_WITH_ROLES);
        String filterString = args.getString(ARG_FILTER_STRING);

        if ( identityName == null ) {
            Object identityNames = args.get(ARG_IDENTITY_NAMES);
            if ( identityNames != null ) {
                if ( identityNames instanceof String ) {
                    String str = (String)identityNames;
                    List<String> strList = Util.csvToList(str); 
                    if ( Util.size(strList) > 0 ) {
                        names.addAll(strList);
                    }
                } else
                    if ( identityNames instanceof Collection ) {
                        Collection collection = (Collection)identityNames;
                        names.addAll(collection);
                    }
            }
        } else {
            names.add(identityName);
        }
            
        Identitizer refresher = new Identitizer(spcon, args);
        refresher.setRefreshSource(Source.Workflow, wfc.getWorkflow().getName());

        if ( names.size() > 0 ) {
            for ( String name : names ) {
                refreshInternal(spcon, refresher, name);
            }
        }
        else {
            QueryOptions ops = new QueryOptions();
            Filter filter = null;
            if ( filterString != null ) {
                filter = Filter.compile(filterString);
                ops.add(filter);
            } 
            else if ( withRolesAssigned != null ) {
                List<Bundle> bundles = new ArrayList<Bundle>();
                List<String> roleNames = Util.csvToList(withRolesAssigned);
                if ( Util.size(roleNames) > 0 ) {
                    for ( String roleName :  roleNames ) {
                        Bundle bundle = spcon.getObjectByName(Bundle.class, roleName);
                        if ( bundle != null ) {
                            bundles.add(bundle);
                        }
                    }
                    ops.add(Filter.or(Filter.containsAll("bundles", bundles),
                                      Filter.containsAll("assignedRoles",bundles)));
                }
            }   

            List<String> attrs = new ArrayList<String>();
            attrs.add("id");
            Iterator<Object[]> it = spcon.search(Identity.class, ops, attrs);

            // bug 22744, pass a context so we can do a periodic refresh, but
            // really this should never be used any more
            IdIterator idit = new IdIterator(spcon, it);
            idit.setCacheLimit(100);
            while (idit.hasNext()) {
                String id = idit.next();
                refreshInternal(spcon, refresher, id);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Policy Forms
    //
    // Methods intended for use in workflows launched to handle provisioning 
    // requests.  They will compile the plan into a project, build forms
    // necessary to answer questions left in the project, and assimilate the
    // results of those forms.
    //
    // These are used in workflows launched by the UIs for making role requests,
    // and also the workflow launched by the Identity Refresh task to  
    // handle provisioning of automatically assigned roles.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Argument used by buildProvisioningForm to pass in the 
     * ProvisioningProject maintained by the workflow.
     */
    public static final String ARG_PROJECT = "project";

    /**
     * Argument used by buildProvisioningForm to pass in the name
     * of the form template.  If not specified the default "Identity Refresh"
     * form is used.
     */
    public static final String ARG_TEMPLATE = "template";

    /**
     * Argument to buildProvisioningForm to pass in the name of an 
     * identity that we must build a from for if there are questions
     * for that identity.  If there are none a null form must be returned.
     */
    public static final String ARG_OWNER = "owner";

    /**
     * Argument to buildProvisioningForm to pass in the name of an identity
     * we should we should try to process first when feeding forms back 
     * to the workflow.  If there are no questions for that identity and
     * there are questions for other identities, a form for one of the 
     * other identities is generated at random.
     */
    public static final String ARG_PREFERRED_OWNER = "preferredOwner";

    /**
     * Argument to specify account selection form owner
     */
    public static final String ARG_ACCOUNT_SELECTION_OWNER = "accountSelectionOwner";
    
    /**
     * Argument used by assimilateProvisioningForm to pass in the 
     * Form object that has been interacted with.
     */
    public static final String ARG_FORM = "form";

    /**
     * Fallback form owner. Used by provisioning form, account selection form, and manual provisioning form.
     */
    public static final String ARG_FALLBACK_OWNER = "fallbackOwner";


    /**
     * Argument to determine if re-compilation is needed prior to provisioning
     */
    public static final String ARG_RECOMPILE_BEFORE_PROVISIONING = "recompileBeforeProvisioning";

    /**
     * Compile the master plan passed into the workflow into a 
     * ProvisioningProject.  This is where Question objects may
     * be generated to represent forms that need to be presented to
     * one or more users.
     *
     * @ignore
     * We're in a very twitchy situation regarding the Hibernate
     * cache.  The Identity associated with this plan needs to
     * be fully traversable but depending on what happened
     * before us in the workflow the cache may be unstable.
     * One problem we've seen is that the Identity is in the cache
     * but when you descend through to the 
     * Bundle/IdentitySelector/Application/managerCorrelateRule chain
     * the rule isn't in the cache.  Why this happens I don't know, but
     * lets not trust the cache and start over with a clean one
     * and a new Identity.
     *
     * UPDATE: Since ProvisioningPlan.identity isn't in the XML model
     * we shouldn't be relying on this AT ALL.  
     * 
     */
    public Object compileProvisioningProject(WorkflowContext wfc)
        throws GeneralException {

        ProvisioningProject project = null;
        
        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        // only generate a work item if we have questions
        Object o = args.get(VAR_PLAN);
        if (o instanceof ProvisioningPlan) {
            ProvisioningPlan plan = (ProvisioningPlan)o;

            // Since ProvisioningPlan.identity isn't in the XML model
            // can't rely on it.
            // !! In the Create LCM flow it has to create a stub
            // Identity object since there was no other way to pass in 
            // the name.  We can make idenityName be passed too, but 
            // it won't resolve.  Need to clean up this mess,
            // either ProvisioningPlan needs to stop using an Identityu
            // ref or it needs to serialize it.  It's okay at the moment
            // becuase the workflow won't suspend before the project
            // is compiled, but it's messy.

            // use the stub if it has no id
            Identity ident = plan.getIdentity();
            if (ident == null || ident.getId() != null) {

                Identity original = ident;
                ident = null;

                // make sure we have a clean cache
                spcon.decache();

                // try to fetch a fresh one
                String idname = args.getString(VAR_IDENTITY_NAME);
                if (idname == null)
                    log.warn("Missing argument: identityName");
                else
                    ident = spcon.getObjectByName(Identity.class, idname);

                if (ident == null && original != null)
                    ident = spcon.getObjectById(Identity.class, original.getId());

                if (ident != null)
                    plan.setIdentity(ident);
                else
                    throw new GeneralException("Unable to load Identity for project");
            }

            // Pass the step arguments for provisioner options.
            // This is alsy where you can pass random things for script args.
            Attributes<String,Object> provArgs = 
                getProvisionerArguments(wfc.getStepArguments());
            
            // The map passed to the constructor may contains options
            // for compilation and evaluation. These will be stored in the
            // returned project.
            Provisioner p = new Provisioner(spcon, provArgs);

            // The argument map in this method is used for "script args"
            // that are passed to any scripts or rules in the Templates
            // or Fields.  Here we use the step args for both the
            // options to the Provisioner and the script args during compilation.
            project = p.compile(plan, provArgs);

            // Prune this reference just to make it harder to get into
            // Hibernate cache problems.  Once we have the project
            // we'll always fetch the identity by name on each recompile
            // and don't need this reference any more.
            // NO!! buildApprovalSet needs this, need to revisit
            // how many places we reference the Identity and try
            // to use names instead
            //plan.setIdentity(null);
        }
        else {
            log.error("Invalid object passed as plan argument");
        }

        return project;
    }
    
    /**
     * Recompile the ProvisioningProject in the WorkflowContext.  If a
     * ProvisioningPlan is found in the WorkflowContext, the master plan in
     * the project is replaced with this plan before recompiling.  This
     * allows a workflow to modify a plan (eg - through approvals) but
     * reuse input from a previous compilation (eg - account selection and
     * answered questions).  Return the recompiled project.
     */
    public Object recompileProvisioningProject(WorkflowContext wfc)
        throws GeneralException {

        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();
    
        ProvisioningProject project =
            (ProvisioningProject) args.get(VAR_PROJECT);
        if (null == project) {
            throw new GeneralException("Missing required provisioning project.");
        }
        
        // If a plan is specified in the args, replace the master plan in the
        // project with it before recompiling.
        Object o = args.get(VAR_PLAN);
        if (o instanceof ProvisioningPlan) {
            ProvisioningPlan plan = (ProvisioningPlan) o;
            project.setMasterPlan(plan);
        }

        // Pass the step arguments for provisioner options.
        // This is also where you can pass random things for script args.
        Attributes<String,Object> provArgs = 
            getProvisionerArguments(wfc.getStepArguments());

        // The map passed to the constructor may contains options
        // for compilation and evaluation. These will be stored in the
        // returned project.
        Provisioner p = new Provisioner(spcon, provArgs);

        // The argument map in this method is used for "script args"
        // that are passed to any scripts or rules in the Templates
        // or Fields.  Here we use the step args for both the
        // options to the Provisioner and the script args during compilation.
        project = p.recompile(project, provArgs);

        return project;
    }

    
    /**
     * Given the Step arguments for one of the methods that
     * call the Provisioner, gather all the arguments related to 
     * identity refresh option and leave them in a single argument
     * "refreshOptions".
     *
     * Provisioner requires that all refresh options be passed
     * in one Map to prevent random task args from task using
     * Provisioner getting in and polluting things.  But it is more
     * convenient for workflow authors to do this:
     *
     *     <Arg name='corrrelateEntitlements' value='true'/>
     *
     * than this, which I don't think is even possible in the GWE right now.
     *
     *     <Arg name='refreshArgs'>
     *       <value>
     *         <Map>
     *           <entry key='correlateEntitlements' value='true'/>
     *         </Map>
     *       </value>
     *     </Arg>
     * 
     *
     * So id ARG_DO_REFRESH is on, then we will automatically build
     * a refreshArgs Map containing all the step args that look related
     * to refresh.
     */
    public static Attributes<String,Object> 
        getProvisionerArguments(Attributes<String,Object> stepArgs) {

        Attributes<String,Object> provArgs = stepArgs;

        if (stepArgs.getBoolean(PlanEvaluator.ARG_DO_REFRESH)) {
            
            provArgs = new Attributes<String,Object>();
            provArgs.putAll(stepArgs);

            Map refreshOptions = null;

            // now add the step args that aren't provisioning args
            Iterator<String> it = stepArgs.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                if (key.equals(ARG_PLAN) ||
                    key.equals(VAR_IDENTITY_NAME)) {
                    // it's one of ours, ignore
                    continue;
                }
                else if (key.equals(PlanEvaluator.ARG_REFRESH_OPTIONS)) {
                    // they passed encapsulated argumets, 
                    // we could either treat these as authoritative
                    // or continue merging
                    Object o = stepArgs.getBoolean(key);
                    if (o instanceof Map) {
                        if (refreshOptions == null)
                            refreshOptions = new HashMap(); 
                        refreshOptions.putAll((Map)o);
                    }
                }
                else if (!Provisioner.isArgument(key)) {
                    if (refreshOptions == null)
                        refreshOptions = new HashMap();
                    refreshOptions.put(key, stepArgs.get(key));
                }
            }

            // this replaces the original options map if any
            provArgs.put(PlanEvaluator.ARG_REFRESH_OPTIONS, refreshOptions);
        }

        return provArgs;
    }

    /**
     * Build the next Form containing provisioning questions.
     *
     * Arguments:
     *
     *    preferredOwner - optional name of the Identity we'd like to process first
     *    requiredOwner - optional name of the Identity we must process first then stop
     *    template - optional name of a Form to serve as the page template
     *    project - ProvisioningProject to examine
     *    
     * Returns:
     *    Form if there is a form to present.
     *    null if the project is ready for evaluation.
     *
     * Note that we cannot currently process forms for different users in 
     * parallel.  The presentation and assimilation process is coded as
     * two steps in the workflow, so the Approval in the presentation step
     * must complete before we can move on to the assimilation step.  If we
     * tried to build out one hierarchical Approval for everyone that needed
     * to see a provisioning form, we would have to find a way to save 
     * result form that was brought back in from the work items, then assimilate
     * them all in the step after the approval.  Currently this can't be 
     * done without using WorkflowHandler callbacks which are confusing 
     * or "after scripts" would be more visible but we don't have them yet.
     *
     * Another option would be a way to replicate a call to a workfow subprocess
     * for each approver but there are design issues to work out there too.
     *
     * The easiest thing for now is to call buildProvisioningForm multiple times
     * in a loop and let it (internally using Provisioner) return a sequence 
     * of forms for each user that needs to be involved.  We will pass a
     * "preferredOwner" to influence the order of the forms.  If there is a
     * preferredOwner any forms for that identity will be returned first,
     * this is how we get forms presented to the current UI user immediately
     * without them having to go look in an inbox.
     *
     * The requiredOwner and preferredOwner are mutually exclusive.
     * When requiredOwner is set it means that we must return the forms for this
     * user and if there are none, null is returned.  When preferredOwner is 
     * set it means that forms for this user are returned if there are any, 
     * but if not then we return the forms for some other user selected at
     * random.
     *
     * requiredOwner is intended for workflows where you want to get the form
     * interaction with the requesting user out of the way up front, then go
     * into an approval process where some of the request items may be removed, 
     * then finish with presenting the provisioning forms to the remaining
     * users based on what was approved.
     *
     * preferredOwner is set when you want all the forms to be presented
     * now without any intervening steps, but you would rather handle the
     * ones for the given user first so they can be fed to them synchronously
     * without having to get them one at a time from the work item inbox.
     * 
     */
    public Object buildProvisioningForm(WorkflowContext wfc)
        throws GeneralException {

        Form form = null;
        
        Attributes<String,Object> args = wfc.getArguments();

        // only generate a work item if we have questions
        Object o = args.get(ARG_PROJECT);
        if (o instanceof ProvisioningProject) {
            ProvisioningProject project = (ProvisioningProject)o;

            // allow the form template to be specified in an argument, 
            // it will deafult if null
            String formTemplate = args.getString(ARG_TEMPLATE);

            // optional name for the current user
            // the different between required and preferred is that if there
            // are no more forms fo the required
            String requiredOwner = args.getString(ARG_OWNER);
            String preferredOwner = args.getString(ARG_PREFERRED_OWNER);

            // collapse empty strings since the logic is subtle and important
            if (requiredOwner != null && requiredOwner.length() == 0)
                requiredOwner = null;

            if (preferredOwner != null && preferredOwner.length() == 0)
                preferredOwner = null;


            // massage these into the form Provisioner wants
            String owner = ((requiredOwner != null) ? requiredOwner : preferredOwner);
            boolean required = (requiredOwner != null);
            Attributes<String,Object> formArgs = getFormArguments(wfc);

            Formicator cator = new Formicator(wfc.getSailPointContext());
            form = cator.buildProvisioningForm(project, formTemplate, 
                                               owner, required, formArgs);

            // NOTE WELL: Form has an "owner" property because it is a
            // SailPointObject but that isn't what Provisioner sets.  
            // To convey the form interaction user we use the "targetUser" 
            // property.  There are two reasons, the template form may want
            // an "owner" for management purposes and "owner" must be 
            // an Identity reference whereas targetUser can be something
            // abstract like "System", "UnitTest" or "CurrentUser".

            if (form != null && form.getTargetUser() == null) {
                // This could happen if preferredOwner wasn't passed in and
                // none of the Templates or Fields had an owner.
                // So the provisioning doesn't just die, hunt around for
                // the usual target users.  Might want this to be an option?

                String user = getFallbackProvisioningFormOwner(args, wfc);
                
                if (user != null)
                    form.setTargetUser(user);
                else {
                    log.error("Unable to return provisioning form with no owner");                
                    form = null;
                }
            }
        }
        else {
            log.error("Invalid object passed as project argument");
        }

        return form;
    }

    /**
     * Figure out the provisioning form owner after formicator
     */
    private String getFallbackProvisioningFormOwner(Attributes<String,Object> args, WorkflowContext wfc) throws GeneralException {

        // If set use provisioningFormOwner
        // jsl - what does that mean?
        String fallbackOwner = args.getString(ARG_FALLBACK_OWNER);
        String calculatedOwner = calculateOwner(fallbackOwner, wfc);

        if (calculatedOwner == null) {
            // we have to have something, could bump it up a level?
            calculatedOwner = "spadmin";
        }

        return calculatedOwner;
    }

    /**
     * Rebuild the provisioning project when account selection was create.
     * 
     * @param wfc WorkFlowContext
     * @return Compiled ProvisioningProject
     */
    public Object recompileProject(WorkflowContext wfc) throws GeneralException {
        Provisioner p = new Provisioner(wfc.getSailPointContext());
        Attributes args = wfc.getStepArguments();
        
        Object project = args.get(ARG_PROJECT);
        
        return p.recompile((ProvisioningProject)project, args); 
    }
    
    /**
     * Build account selection form
     * 
     * @param wfc WorkFlowContext
     * @return Form for account selection
     * @throws GeneralException
     */
    public Object buildAccountSelectionForm(WorkflowContext wfc)
            throws GeneralException {

        Form form = null;

        Attributes<String,Object> args = wfc.getArguments();

        // only generate a work item if we have questions
        Object o = args.get(ARG_PROJECT);
        if (o instanceof ProvisioningProject) {
            ProvisioningProject project = (ProvisioningProject)o;

            form = new Form();
            
            form.setPageTitle(MessageKeys.ACCOUNT_SELECTION_FORM_PAGETITLE);

            Gson gson = new Gson();
            Map<String, Object> formTitleLabel = new HashMap<String, Object>();
            formTitleLabel.put("messageKey", MessageKeys.ACCOUNT_SELECTION_TITLE_FOR);
            String[] formTitleLabelArgs = { (String)args.get(VAR_IDENTITY_NAME) };
            formTitleLabel.put("args", formTitleLabelArgs);
            
            form.setTitle(gson.toJson(formTitleLabel));
            
            form.setSubtitle(MessageKeys.ACCOUNT_SELECTION_FORM_SUBTITLE);

            form.setTargetUser(args.getString(ARG_OWNER));
            
            // If no target user was set return null form
            if (form.getTargetUser() == null) {
                return null;
            }
            
            List<ProvisioningTarget> targets = project.getProvisioningTargets(); 
            if (targets != null) {
                for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
                
                    ProvisioningTarget target = targets.get(targetIndex);
                    // jsl - there will be fully answered PT's in the project, skip those
                    if (target.isAnswered())
                        continue;

                    String roleName = target.getRole();

                    Form.Section roleSection = new Form.Section(roleName);
                
                    Map<String, Object> localLabel = new HashMap<String, Object>();
                    localLabel.put("messageKey", MessageKeys.ACCOUNT_SELECTION_ASSIGNED_ROLE);
                    String[] messageKeyArgs = { roleName };
                    localLabel.put("args", messageKeyArgs);
                    roleSection.setLabel(gson.toJson(localLabel));
                
                    List<AccountSelection> selections = target.getAccountSelections();
                    if (selections != null) {
                        for (int selectionIndex = 0; selectionIndex < selections.size(); selectionIndex++ ) {
                            AccountSelection selection = selections.get(selectionIndex);
                            // jsl - individual AccountSelections may be answered
                            if (selection.isAnswered()) 
                                continue;

                            String appName = selection.getApplicationName();
                    
                            List<AccountSelection.AccountInfo> accounts = selection.getAccounts();
                    
                            List<Object> allowedValues = new ArrayList<Object>();

                            if (selection.isAllowCreate())
                                {
                                    allowedValues.add(new ArrayList<String>(Arrays.asList(ACCOUNT_SELECTOR_CREATE,
                                                                                          MessageKeys.ACCOUNT_SELECTION_CREATE_ACCOUNT)));
                                }
                    
                            for (AccountSelection.AccountInfo accountInfo : Util.iterate(accounts)) {
                                List<String> pair = new ArrayList<String>(Arrays.asList(accountInfo.getNativeIdentity(), 
                                                                                        accountInfo.getDisplayName()));
                                allowedValues.add(pair);
                        
                            }
                    
                            Field accountSelectionField = new Field();

                            accountSelectionField.setAttribute("selectionIndex", selectionIndex);
                            accountSelectionField.setAttribute("targetIndex", targetIndex);
                    
                            if (!Util.isEmpty(selection.getRoleName())) {
                                Map<String, Object> fieldLabel = new HashMap<String, Object>();
                                fieldLabel.put("messageKey", MessageKeys.ACCOUNT_SELECTION_ACCOUNT_ON);
                                String[] fieldLabelArgs = { selection.getRoleName(), appName };
                                fieldLabel.put("args", fieldLabelArgs);
                                accountSelectionField.setPrompt(gson.toJson(fieldLabel));
                            }
                            else {
                                accountSelectionField.setPrompt(appName);
                            }
                            
                            accountSelectionField.setDisplayType(Field.DISPLAY_TYPE_COMBOBOX);
                            accountSelectionField.setSection(appName);
                            accountSelectionField.setRequired(true);
                            accountSelectionField.setAllowedValues(allowedValues);
                            accountSelectionField.setType("Link");

                            // make sure name is unique otherwise only one field will be rendered
                            String namePrefix = selection.getRoleName();
                            if (Util.isNullOrEmpty(namePrefix)) {
                                namePrefix = roleName;
                            }
                            
                            accountSelectionField.setName(namePrefix + "-" + appName);
                            
                            roleSection.add(accountSelectionField);
                        }
                    }
                                    
                    form.add(roleSection);
                }
            }
            
            List<Button> buttons = new ArrayList<Button>();
            
            Button submitButton = new Button(calcButtonLabel(Form.ACTION_NEXT, args, MessageKeys.BUTTON_SUBMIT), Form.ACTION_NEXT);
            buttons.add(submitButton);
            
            Button cancelButton = new Button(calcButtonLabel(Form.ACTION_CANCEL, args, MessageKeys.BUTTON_CANCEL), Form.ACTION_CANCEL);
            buttons.add(cancelButton);
            
            form.setButtons(buttons);
        }
        else {
            log.error("Invalid object passed as project argument");
        }

        return form;
    }

    /**
     * Figure out account selection form owner.
     *
     * bug#24447 this probably needs to be more complicated and/or
     * integrated with buildAccountSelectionForm.  The optional accountSelectionOwner
     * can be "applicationOwner" or "roleOwner" which are being calculated by 
     * assuming there are ProvisioningTargets and just taking the first one.
     *
     * I'm not sure roleOwner even makes sense down here, and applicationOwner
     * could be different for every AccountRequest in the plan. The crash
     * in calculateOwner was removed but we really need to rethink this.
     *
     */
    public Object getAccountSelectionOwner(WorkflowContext wfc) throws GeneralException {
        Attributes<String,Object> args = wfc.getArguments();
                
        String owner = null;

        String accountSelectionOwner = args.getString(ARG_ACCOUNT_SELECTION_OWNER);
        // Check if accountSelectionOwner is set and valid use it
        if (Util.isNotNullOrEmpty(accountSelectionOwner)) {
            owner = calculateOwner(accountSelectionOwner, wfc);
        }
        
        // Still no owner, try requiredOwner
        if (Util.isNullOrEmpty(owner)) {
            String requiredOwner = args.getString(ARG_OWNER);
            owner = isValidOwner(requiredOwner, wfc);
        }
        
        // Check preferredOwner next
        if (Util.isNullOrEmpty(owner)) {
            String preferredOwner = args.getString(ARG_PREFERRED_OWNER);
            owner = isValidOwner(preferredOwner, wfc);
        }
        
        // still no owner, check fallbackOwner
        if (Util.isNullOrEmpty(owner)) {
            String fallbackOwner = args.getString(ARG_FALLBACK_OWNER);
            owner = calculateOwner(fallbackOwner, wfc);
        }
        
        if (Util.isNullOrEmpty(owner)) {
            owner = "spadmin";
        }

        return owner;
    }

    /**
     * Calculate predefined owner string owners
     * 
     * @param owner Predefined owner string
     * @param wfc WorkFlowContext
     * @return Owner name
     */
    private String calculateOwner(String owner, WorkflowContext wfc) throws GeneralException {

        String calculatedOwner = null;
        Attributes<String,Object> args = wfc.getArguments();
        
        if (Util.isNullOrEmpty(owner)) {
            // this case needs to be handled by the caller or else fallback won't work
        }
        else if (owner.equals(FORM_OWNER_IDENTITY)) {
            calculatedOwner = args.getString(VAR_IDENTITY_NAME);
        }
        else if (owner.equals(FORM_OWNER_MANAGER)) {
            calculatedOwner = getManager(wfc);
        }
        else if (owner.equals(FORM_OWNER_APPOWNER)) {

            // jsl - this doesn't make sense if there is more than
            // one app invlolved in this project, and we may not even have
            // ProvisioningTargets, need to rewrite this

            ProvisioningProject project = (ProvisioningProject)args.get(ARG_PROJECT);
            List<ProvisioningTarget> targets = project.getProvisioningTargets();
            
            if (targets != null && !targets.isEmpty()) {
                ProvisioningTarget target = targets.get(0);
                List<AccountSelection> accountSelections = target.getAccountSelections();
                
                if (accountSelections != null && !accountSelections.isEmpty()) {
                    AccountSelection sel = accountSelections.get(0);
                    String appId = sel.getApplicationId();
                    Application app = wfc.getSailPointContext().getObjectById(Application.class, appId);

                    if (app != null) {
                        Identity ident = app.getOwner();
                        if (ident != null) {
                            calculatedOwner = ident.getName();
                        }
                    }
                }
            }
        }
        else if (owner.equals(FORM_OWNER_ROLEOWNER)) {

            // jsl - I don't think this option even makes any sense
            // what if there is more than one role in the plan?

            ProvisioningProject project = (ProvisioningProject)args.get(ARG_PROJECT);
            List<ProvisioningTarget> targets = project.getProvisioningTargets();
            
            if (targets != null && !targets.isEmpty()) {
                ProvisioningTarget target = targets.get(0);
                String roleName = target.getRole();
                
                if (roleName != null) {
                    Bundle bundle = wfc.getSailPointContext().getObjectByName(Bundle.class, roleName);
                    
                    if (bundle != null) {
                        Identity ident = bundle.getOwner();
                        if (ident != null) {
                            calculatedOwner = ident.getName();
                        }
                    }
                }
            }
        }
        else if (wfc.getSailPointContext().getObjectByName(Identity.class, owner) != null) {
            calculatedOwner = owner;
        }

        return calculatedOwner;
    }

    /**
     * Check if owner name is valid owner and not system or scheduled
     * 
     * @param owner Predefined owner string
     * @return Same owner string if valid, otherwise null
     */
    private String isValidOwner(String owner, WorkflowContext wfc) throws GeneralException {
        if (Util.isNotNullOrEmpty(owner)) {
            // make sure its not 'system' or 'scheduler'
            if (!owner.toLowerCase().equals("system")
                    && !owner.toLowerCase().equals("scheduler")
                    && wfc.getSailPointContext().getObjectByName(Identity.class, owner) != null) {

                return owner;
            }
        }
        
        return null;
    }
    
    /**
     * We use Formicator to assemble Forms, evaluate field scripts
     * and expand $() references to variables.  This defines the
     * set of variables that are accessible during expansion.
     * It is important that we maintain consistency for field
     * scripts.  See Field.java for more information.
     *
     * Form assembly may need to be sensitive to workflow variables so 
     * pass our arguments which includes not only the workflow 
     * variables but also any <Arg> values passed to this method.
     *
     * !! We have always passed the entire set of arguments
     * which includes all the workflow variables.  But for form
     * processing we should be limiting ourselves to Step or Approval
     * arguments since that's all the dynamic fields have access
     * to once we're in a WorkItem?  That may break backward compatibility...
     */
    private Attributes<String,Object> getFormArguments(WorkflowContext wfc) 
        throws GeneralException {

        // start with all args and workflow variables
        Attributes<String,Object> args = wfc.getArguments();

        // It is common for skelton forms to have read-only fields
        // that display things from the identity so make sure the
        // "identity" symbol has the full Identity object.  This is
        // also available when PlanCompiler processes provisioning policy
        // fields so Field authors expect it.
        Identity ident = getIdentity(wfc);
        args.put(ARG_IDENTITY, ident);

        return args;
    }

    /**
     * Assimilate the results of the form generated by
     * buildProvisioningForm.  The form is expected to have been 
     * processed by WorkItemFormBean with the posted values left in 
     * the Fields.
     * 
     * Regenerate the ProvisioningProject with the answers to the form
     * questions and return it.  The workflow is expected to present
     * another form if the project still contains questions.
     *
     * Arguments:
     * 
     *    form - the Form laden with Field values
     *    project - a ProvisioningProject to receive the form
     * 
     * Returns:
     *    new ProvisioningProject if there are still unanswered questions,
     *    null if the project is ready for evaluation
     */
    public Object assimilateProvisioningForm(WorkflowContext wfc)
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        // Project
        Object o = args.get(ARG_PROJECT);
        if (o == null)
            throw new GeneralException("No project argument");
        
        if (!(o instanceof ProvisioningProject))
            throw new GeneralException("Invalid project argument");
        
        ProvisioningProject project = (ProvisioningProject)o;

        // Form
        o = args.get(ARG_FORM);
        if (o == null)
            throw new GeneralException("No form argument");
        
        if (!(o instanceof Form))
            throw new GeneralException("Invalid form argument");

        Form form = (Form)o;

        // assimilate and recompile
        // Like we did in the initial compilation, pass the step
        // arguments so they can be referenced by scripts or rules
        // in the Templates or Fields.  Unlike compilation we don't
        // need to pass these into the Provisioner constructor, 
        // the provisioning options will have been stored in the project.
        Attributes<String,Object> provArgs = wfc.getStepArguments();

        Formicator cator = new Formicator(wfc.getSailPointContext());
        cator.assimilateProvisioningForm(form, project);

        Provisioner p = new Provisioner(wfc.getSailPointContext());
        return p.recompile(project, provArgs);
    }

    /**
     * Update the ApprovalSet with any changes to accountIds.  This
     * looks through the plans in the project and ensures that any
     * accountId entered for a creation template gets copied onto the
     * ApprovalItem and that any selected account IDs get copied into
     * the ProvisioningPlan inside the ApprovalItem.
     */
    public Object assimilateAccountIdChanges(WorkflowContext wfc)
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        // Project
        Object o = args.get(ARG_PROJECT);
        if (o == null)
            throw new GeneralException("No project argument");
        
        if (!(o instanceof ProvisioningProject))
            throw new GeneralException("Invalid project argument");
        
        ProvisioningProject project = (ProvisioningProject)o;
        // If the ApprovalSet present apply changes to the accountId made during the 
        // complilation back onto the ApprovalSet
        ApprovalSet set = null;
        Attributes<String,Object> stepArgs = wfc.getStepArguments();
        if ( stepArgs != null ) {
            set = (ApprovalSet)stepArgs.get(ARG_APPROVAL_SET);
            //TODO: Assimilate to IdentityRequestItems as well?
            //Assume presence of IR id on step is enough?
            boolean updateIR = true;

            IdentityRequest ir = null;
            if (updateIR) {
                String identityRequestId = Util.getString(args,"identityRequestId");
                if ( identityRequestId != null ) {
                    SailPointContext ctx = wfc.getSailPointContext();
                    if (ctx != null) {
                        ir = ctx.getObjectByName(IdentityRequest.class, identityRequestId);
                    }
                }
            }
            if ( ( set != null ) && ( project != null ) )  {
                if (assimilateAccountIdChanges(project, set, ir)) {
                    SailPointContext ctx = wfc.getSailPointContext();
                    if (ctx != null) {
                        ctx.saveObject(ir);
                        ctx.commitTransaction();
                    }
                }
            }
        }
        return set;
    }

    /**
     * Update the ApprovalSet with any changes to accountIds.  This
     * looks through the plans in the project and ensures that any
     * accountId entered for a creation template gets copied onto the
     * ApprovalItem or approval item plan's account requests 
     * and that any selected account IDs get copied into
     * the ProvisioningPlan inside the ApprovalItem.
     */
    public boolean assimilateAccountIdChanges(ProvisioningProject project, ApprovalSet set, IdentityRequest ir)
            throws GeneralException {
        boolean saveIr = false;
        if (set != null && project != null) {
            HashMap<String,String> accountIdMap = new HashMap<String,String>();
            List<ProvisioningPlan> plans = project.getPlans();
            for (ProvisioningPlan plan : Util.safeIterable(plans)) {
                //Load up all account ids on account requests.
                for (AccountRequest request : Util.safeIterable(plan.getAccountRequests())) {
                    if (!ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(request.getApplicationName()) 
                            && AccountRequest.Operation.Create.equals(request.getOperation())) {
                        if (request.getNativeIdentity() != null) {
                            accountIdMap.put(request.getApplication(), request.getNativeIdentity());
                        }
                    }
                }//end account request iteration
                List<ApprovalItem> items = set.getItems();
                for (ApprovalItem item : Util.safeIterable(items)) {
                    AccountRequest req = findCreateAccountRequest(plan, item);
                    if (null != req) {
                        String accountId = req.getNativeIdentity();
                        if (accountId != null) {
                            List<IdentityRequestItem> irItems = null;
                            if (ir != null) {
                                irItems = ir.findItems(item);
                            }
                            if (!ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(item.getApplicationName())) {
                                item.setNativeIdentity(accountId);
                                //Set attribute so we can match to masterPlan later -rap
                                item.setAttribute(ApprovalItem.ATT_UPDATED_NATIVE_ID, true);
                                for (IdentityRequestItem iri : Util.safeIterable(irItems)) {
                                    iri.setNativeIdentity(accountId);
                                    saveIr = true;
                                }
                            } else { //we potentially have an iiq request item with a non-iiq account request in the plan w/o native id
                                for (IdentityRequestItem iri : Util.safeIterable(irItems)) {
                                    ProvisioningPlan itemPlan = iri.getProvisioningPlan();
                                    if (itemPlan != null) {
                                        for (AccountRequest request : Util.safeIterable(itemPlan.getAccountRequests())) {
                                            if (!ProvisioningPlan.IIQ_APPLICATION_NAME.equalsIgnoreCase(request.getApplicationName())) {
                                                if (request.getNativeIdentity() == null) {
                                                    request.setNativeIdentity(accountIdMap.get(request.getApplication()));
                                                    saveIr = true;
                                                }
                                            }
                                        }//end account request iteration
                                    }//itemplan null check
                                }//end request item iteration
                            }//end native id checks
                        }//end null acount id check
                    }//end null account request check
                }//end approval item iteration
            }//end plan iteration
        }//end approval set and project null check
        return saveIr;
    }

    /**
     * Find an AccountRequest in the given plan to create a new account on the
     * application specified in the given ApprovalItem.
     */
    private AccountRequest findCreateAccountRequest(ProvisioningPlan plan,
                                                    ApprovalItem item) {
        AccountRequest req = null;

        String app = item.getApplication();
        String assignmentId = item.getAssignmentId();
        if ( app != null ) {
            List<AccountRequest> reqs = plan.getAccountRequests();
            for (AccountRequest curr : reqs) {
                if (log.isDebugEnabled()) {
                    try {
                        log.debug("Comparing " + curr.toXml());
                        log.debug(item.toXml());
                    } catch (GeneralException ge) {
                        
                    }
                }
                if (AccountRequest.Operation.Create.equals(curr.getOperation()) &&
                    (app.equals(curr.getApplication()) || Util.nullSafeContains(curr.getAssignmentIds(), assignmentId)) &&
                    Util.nullSafeEq(item.getInstance(), curr.getInstance(), true)) {
                    // We should only have a single request for this application
                    // since we're creating the account, so just return it.  No
                    // need to check the nativeIdentity because at this point
                    // the ApprovalItem doesn't have a nativeIdentity.  In cases where we are
                    // assigning a role that has expansions that include native identities, this will just
                    // return the first so we get a native identity instead of "New Account" in the UI.
                    req = curr;
                    break;
                }
            }
        }

        return req;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Create/Update Forms
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build a Form representing all of the attributes in a provisioning 
     * plan.  This is used by workflows that take a plan and want to 
     * present the things in it as an editable form for approval before
     * sending it off for provisioning.
     *
     * Note that the form here isn't intended to be rendered
     * by WorkItemFormBean yet so it normally won't have Buttons.
     *
     */
    public Object buildPlanApprovalForm(WorkflowContext wfc) 
        throws GeneralException {

        Form form = null;

        Attributes<String,Object> args = wfc.getArguments();

        Object o = args.get(VAR_PLAN);
        if (!(o instanceof ProvisioningPlan))
            throw new GeneralException("Missing argument: plan");

        ProvisioningPlan plan = (ProvisioningPlan)o;

        // Allow the form template to be specified in an argument, 
        // if not specified we build one with auto-generated
        // sections for each AccountRequest and it will have no buttons.

        String formTemplate = args.getString(ARG_TEMPLATE);
        Attributes<String,Object> formArgs = getFormArguments(wfc);

        Formicator cator = new Formicator(wfc.getSailPointContext());
        form = cator.buildPlanApprovalForm(plan, formTemplate, formArgs);
        
        return form;
    }
        
    /**
     * Assimilate a Form assumed to have been created by
     * buildPlanApprovalForm back into the plan.
     */
    public Object assimilatePlanApprovalForm(WorkflowContext wfc) 
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        Object o = args.get(ARG_FORM);
        if (!(o instanceof Form)) {
            log.warn("Missing argument: form");
            return args.get(VAR_PLAN);
        }

        Form form = (Form)o;

        o = args.get(VAR_PLAN);
        if (!(o instanceof ProvisioningPlan))
            throw new GeneralException("Missing argument: plan");

        ProvisioningPlan plan = (ProvisioningPlan)o;

        Formicator cator = new Formicator(wfc.getSailPointContext());
        cator.assimilatePlanApprovalForm(form, plan);
        
        return plan;
    }
    
    /**
     * This will create a readOnly version of the form
     * to show for approval. This will be usually used 
     * to confirm a form. It will present a
     * "Submit" and "Cancel" button along with the read
     * only fields. It will also remove 'secret' fields.
     */
    public Form buildReadOnlyForm(WorkflowContext wfc) throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        Object formArg = args.get(ARG_FORM);
        if (formArg == null) {
            throw new GeneralException("Expecting argument: " + ARG_FORM);
        }
        
        Form form = wfc.getWorkflower().getForm(formArg);
        if (form == null) {
            throw new GeneralException(formArg + " did not evaluate to a Form Object");
        }
        
        Form formCopy = (Form) form.deepCopy((Resolver)wfc.getSailPointContext());

        setHelpText(formCopy, (String) args.get(ARG_HELP_TEXT));

        Iterator<Field> iterator = formCopy.iterateFields();
        List<Field> fieldsToRemove = new ArrayList<Field>();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            if (Field.TYPE_SECRET.equals(field.getType())) {
                fieldsToRemove.add(field);
            } else {
                field.setReadOnly(true);
                
                // Clear out the read-only script/rule if there one.  This makes
                // the the above call to setReadOnly() be authoritative.
                field.setReadOnlyDefinition(null);
            }
        }
        for (Field field : fieldsToRemove) {
            formCopy.removeField(field.getName());
        }

        formCopy.clearButtons();

        Button submitButton = new Button(calcButtonLabel(Form.ACTION_NEXT, args, MessageKeys.BUTTON_SUBMIT), Form.ACTION_NEXT);
        formCopy.add(submitButton);
        Button backButton = new Button(calcButtonLabel(Form.ACTION_BACK, args, MessageKeys.BUTTON_BACK), Form.ACTION_BACK);
        formCopy.add(backButton);
        Button cancelButton = new Button(calcButtonLabel(Form.ACTION_CANCEL, args, MessageKeys.BUTTON_CANCEL), Form.ACTION_CANCEL);
        formCopy.add(cancelButton);
        
        boolean includeExit = Util.getBoolean(args, "includeExitButton");
        if ( includeExit ) {
            Button exitWorkflow = new Button("Exit Workflow", Form.ACTION_NEXT);
            String exitParameter = Util.getString(args, "exitParameter");
            if ( exitParameter != null ) {
                exitWorkflow.setParameter(exitParameter);
            } else {
                exitWorkflow.setParameter("exitWorkflow");
            }
            
            String exitValue = Util.getString(args, "exitParameter");
            if ( exitValue != null ) {
                exitWorkflow.setValue(exitValue);
            } else {
                exitWorkflow.setValue("true");
            }
            formCopy.getButtons().add(exitWorkflow);
        }
        
        return formCopy;
    }

    private void setHelpText(Form formCopy, String helpTextKey) {

        if (helpTextKey == null) {
            return;
        }

        formCopy.setSubtitle(helpTextKey);
    }

    /**
     * The step should have argument names such as
     *  "nextButtonLabel", "backButtonLabel", "cancelButtonLabel"
     *  if this arg value is set, that is used as the message key
     *  otherwise defaultMessageKey will be used
     */
    private static String calcButtonLabel(String action, Attributes<String, Object> args, String defaultMessageKey) {
        
        String name = action + "ButtonLabel";
        
        String messageKey = (String) args.get(name);
        if (messageKey == null) {
            messageKey = defaultMessageKey;
        }

        return messageKey;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When true indicates that trigger processing should not be done
     * implicitly after provisionProject.
     */
    public static final String ARG_NO_TRIGGERS = "noTriggers";


    /**
     * Called by the Identity Update and LCM workflows after all the
     * provisioning forms have been completed.  Provision what remains
     * in the project.
     *
     * You cannot pass execution options here, those must have been
     * set in the call to compileProvisioningProject and thereafter
     * were stored within the project.
     *
     * Besides evaluating the project this is also where we will evaluate
     * identity change triggers after provisioning.  To do this we have to
     * get a complete copy of the identity before provisioning so that
     * we can compare it later to detect the diffs.
     */
    public Object provisionProject(WorkflowContext wfc) throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        // Project
        Object o = args.get(ARG_PROJECT);
        if (o == null)
            throw new GeneralException("Missing argument: project");

        if (!(o instanceof ProvisioningProject))
            throw new GeneralException("Invalid argument: project");
        
        ProvisioningProject project = (ProvisioningProject)o;

        // load the current version of the Identity for trigger processing
        Identity previous = null;

        String name = project.getIdentity();
        // This can be null if we're creating a new one
        // OR if we are provisioning a plan that modified
        // something other then accounts
        SailPointContext context = wfc.getSailPointContext();
        Identity ident = context.getObjectByName(Identity.class, name);
        if (ident != null) {
            // do this in another context to avoid ugly Hibernate
            // cache games
            previous = getPreviousVersion(ident);
        }

        
        // there are no script args for execution
        // NOTE: If you want to enable refresh you have to pass
        // ARG_DO_REFRESH and ARG_REFRESH_OPTIONS when the plan is *compiled*
        // this works but it would be more obvious if these could
        // be passed here when the plan is executed...
        // !! yes fix this
        log.info("Starting project execution");
        Provisioner p = new Provisioner(wfc.getSailPointContext());

        // Since we're using p.execute, Configuration.ASYNC_CACHE_REFRESH should be a refreshOption in the project
        Object asyncCache = args.get(Configuration.ASYNC_CACHE_REFRESH);
        if (asyncCache != null) {
            // IIQETN-6371 - ARG_REFRESH_OPTIONS is typically some type of Map so cast appropriately
            Map<String, Object> refreshOptions = (Map<String, Object>) project.get(PlanEvaluator.ARG_REFRESH_OPTIONS);
            if (refreshOptions == null) {
                refreshOptions = new Attributes<String, Object>();
                project.put(PlanEvaluator.ARG_REFRESH_OPTIONS, refreshOptions);
            }
            refreshOptions.put(Configuration.ASYNC_CACHE_REFRESH, asyncCache);
        }

        //Check if re-compilation needed
        // IIQTC-339 :- Do not call recompile when the identity is null
        if (args.getBoolean(ARG_RECOMPILE_BEFORE_PROVISIONING) && ident !=null) {
            //Recompile
            p.recompile(project, getProvisionerArguments(wfc.getStepArguments()));
        }


        p.execute(project);
        log.info("Finished project execution");

        // copy errors from the executors back to the workflow case
        // so they can be seen in the task result
        
        assimilateProvisioningErrors(wfc, project);
        
        // If we were in the refresh task, at this point we would
        // check policies, update the scorecard, promote attributes, etc.
        // Might want an option to peform a targeted cube refresh
        // after provisioning, but this would probably be better as
        // an explicit step.

        // !! Figure out what to do with evaluation errors.  We can't
        // depend on them since IntegrationExecutors may be called in   
        // other threads, but there will usually be something interesting
        // to say.

        // Do change events and trigger evaluation, we might want an option to 
        // disable this though compared to provisioning it probably isn't
        // that expensive
        // !! jsl - this should NOT be here, the workflow should
        // either have a step that does this or we should use
        // the new refresh options that can be passed in the project.
        // I'm afraid of taking this out for backward compatibility
        // so at least let this be disalbed to prevent infinite recursion.
        // Also if they passed explicit refresh arguments in the project,
        // let those override.

        // IIQSAW-1251 -- to prevent infinit looping on Identitizer.doTriggers(),
        // we define ARG_NO_TRIGGERS in parent workflow context.
        if (name != null &&
            !Util.otob(wfc.getHierararchical(ARG_NO_TRIGGERS)) &&
            !project.getBoolean(PlanEvaluator.ARG_DO_REFRESH)) {
            Identity neu = null;
            try {
                PersistenceOptions ops = new PersistenceOptions();
                ops.setExplicitSaveMode(true);
                context.setPersistenceOptions(ops);
                //Save off the workflow case to ensure we can restore it post decache
                WorkflowCase workflowCase = wfc.getWorkflowCase();
                //So much detritus attached to a recently-provisioned user,
                //especially in a highly contentious environment.
                context.saveObject(workflowCase);
                context.decache();
                if(!project.getBoolean(PlanEvaluator.ARG_NO_LOCKING)) {
                    neu = ObjectUtil.lockIdentity(context, name);
                } else {
                    neu = ObjectUtil.getObject(context, Identity.class, name);
                }
                if (neu == null) {
                    // This should only happen if the plan ended up deleting
                    // the target identity.  Ordinarily we won't see op=Delete
                    // in the master plan but it could have been deleted
                    // as a side effect of Link moves if the "pruneIdentities" option
                    // was on during the refresh after the move.  I disabled this.
                    log.error("Unable to load identity after provisioning");
                } else {
                    Identitizer idz = new Identitizer(context);
                    setSource(idz, project);
    
                    // turn off the executeInBackground flag since we're already
                    // supposed to be in the background
                    idz.doTriggers(previous, neu, false);
                    //make sure we see any changes made by trigger processing prior
                    //to unlocking.
                    neu = ObjectUtil.reattach(context, neu);
                    
                    if(workflowCase != null) {
                        workflowCase = ObjectUtil.reattach(context, workflowCase);
                    }
                }
            } finally {
                if(!project.getBoolean(PlanEvaluator.ARG_NO_LOCKING) && neu != null) {
                    ObjectUtil.unlockIdentity(context, neu);
                }
                context.setPersistenceOptions(null);
            }
        }
        return null;
    }

    /**
     * Creates ProvisioningRequest for unmanaged plan.
     * ProvisioningRequest will be used by NativeChangeDetector to filter changes initiated by IIQ.
     * Once these changes are aggregated to Identity, corresponding ProvisioningRequest will be removed.
     * 
     */
    public static void saveUnmanagedPlan(WorkflowContext wfc) throws GeneralException {
        Attributes<String,Object> args = wfc.getArguments();

        Object projectObj = Util.get(args,ARG_PROJECT);
        if ( projectObj == null ) {
            WorkflowContext top = wfc.getRootContext();
            projectObj = top.getVariable(ARG_PROJECT);
        }

        if (projectObj == null)
            throw new GeneralException("Missing argument: project");

        if (!(projectObj instanceof ProvisioningProject))
            throw new GeneralException("Invalid argument: project");
        
        saveUnmanagedPlan(wfc, (ProvisioningProject)projectObj);
    }

    /**
     * Creates ProvisioningRequest for unmanaged plan.
     * ProvisioningRequest will be used by NativeChangeDetector to filter changes initiated by IIQ.
     * Once these changes are aggregated to Identity, corresponding ProvisioningRequest will be removed.
     *
     */
    public static void saveUnmanagedPlan(WorkflowContext wfc, ProvisioningProject project) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();

        if (project == null) {
            throw new GeneralException("Missing argument: project");
        }

        String name = project.getIdentity();

        ProvisioningPlan unmanagedPlan = project.getUnmanagedPlan();

        PlanUtil.createProvisioningRequest(context, unmanagedPlan, name);
    }

    /**
     * Walk over the compiled plans in the project looking for 
     * ProvisioningResults that have errors or warnings.  Copy
     * these onto the case so they'll be included in the task result.
     * 
     * We have to dig into the results stored on both account requests
     * and attribute requests to make sure we include all errors/warnings
     * in the case so they make their way to the task result.
     *  
     */
    public static void assimilateProvisioningErrors(WorkflowContext wfc, 
                                                    ProvisioningProject project) {
        
        WorkflowCase wfcase = wfc.getRootWorkflowCase();
        List<ProvisioningPlan> plans = project.getPlans();
        int totalFailures = 0;
        int totalErrors = 0;

        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                ProvisioningResult result = plan.getResult();
                if (result != null) {
                    if (result.isFailed()) totalFailures++;
                    appendNewMessages(wfcase, result.getWarnings());
                    totalErrors += appendNewErrors(wfcase, result.getErrors());
                }

                List<AbstractRequest> requests = plan.getAllRequests();
                if (requests != null) {
                    for (AbstractRequest req  : requests) {
                        ProvisioningResult objResult = req.getResult();
                        if (objResult != null) {
                            if (objResult.isFailed()) totalFailures++;
                            appendNewMessages(wfcase, objResult.getWarnings());
                            totalErrors += appendNewErrors(wfcase, objResult.getErrors());
                        }
                        List<AttributeRequest> attrReqs = req.getAttributeRequests();
                        if ( attrReqs != null ) {
                            for ( AttributeRequest attrReq : attrReqs ) {
                                ProvisioningResult attrResult = attrReq.getResult();
                                if ( attrResult != null ) {                                    
                                    if (attrResult.isFailed()) totalFailures++;
                                    appendNewMessages(wfcase, attrResult.getWarnings());
                                    totalErrors += appendNewErrors(wfcase, attrResult.getErrors());                                    
                                }
                            }
                        }                        
                        List<PermissionRequest> permReqs = req.getPermissionRequests();
                        if ( permReqs != null ) {
                            for ( PermissionRequest permReq : permReqs ) {
                                ProvisioningResult permResult = permReq.getResult();
                                if ( permResult != null ) {               
                                    if (permResult.isFailed()) totalFailures++;
                                    appendNewMessages(wfcase, permResult.getWarnings());
                                    totalErrors += appendNewErrors(wfcase, permResult.getErrors());                                    
                                }
                            }
                        }                        
                    }
                }
            }
        }

        // Kludge: in some cases connectors return status FAILED but they don't
        // include any messages.  The UI usually looks for the presence of error
        // messages to decide whether to show a green "succesfully launched" or 
        // a red error message.  If we had failures but no errors add something
        // so the UI has something to show.
        if (totalFailures > 0 && totalErrors == 0)
            wfcase.addMessage(Message.error(MessageKeys.WORKFLOW_UNSPECIFIED_CONNECTOR_FAILURE));
    }

    /**
     * In some cases connectors may have erroneously returned Info messages 
     * as errors.
     * 
     * This method just assures that the type is correctly set on any
     * errors.
     * 
     * @param wfCase WorkflowCase
     * @param messages List of Message objects
     */
    private static int appendNewErrors(WorkflowCase wfCase, List<Message> messages) {
        if ( messages != null ) {
            for ( Message msg : messages ) 
                msg.setType(Message.Type.Error);
        
            appendNewMessages(wfCase, messages);
        }
        // we don't really care if we filtered them or not, only that some were added
        return (messages != null) ? messages.size() : 0;
    }

    /**
     * Add new messages to the case object.  The Messages here
     * must have the correct type specified.
     *  
     * @param wfCase WorkflowCase
     * @param messages List of Message objects
     */
    public static void appendNewMessages(WorkflowCase wfCase, List<Message> messages) {
        if ( wfCase != null && Util.size(messages)  > 0 ) {
            List<Message> caseMsgs = wfCase.getMessages();
            if ( caseMsgs == null ) {
                wfCase.addMessages(messages);                
            } else            
            for ( Message message : messages ) {
                if ( !caseMsgs.contains(message)) {
                    wfCase.addMessage(message);
                }
            }
        }
    }

    /**
     * Set the source on the Identitizer using information from the project.
     */
    private void setSource(Identitizer idz, ProvisioningProject project) {

        ProvisioningPlan master = project.getMasterPlan();
        
        if ( master != null )  {
            String sourceStr = master.getSource();
            Source source =  (null != sourceStr) ? Source.valueOf(sourceStr) : null;
            String who = null;
            List<Identity> requesters = master.getRequesters();
            if (null != requesters) {
                for (Identity requester : requesters) {
                    if (null == who) {
                        who = requester.getName();
                    }
                    else {
                        who += ", " + requester.getName();
                    }
                }
            }
            idz.setRefreshSource(source, who);
        }
    }
    
    /**
     * Fetch the previous version of an object in a private 
     * transaction so we don't pollute the one containing the modified object.
     * Trying to do this with cache attach/deattach almost always
     * causes problems.  Though this might be better?
     *
     *     - fully decache current context
     *     - load previous version
     *     - fully decache current context
     *     - attach neu identity
     *
     */
    private Identity getPreviousVersion(Identity neu)
        throws GeneralException {

        Identity prev = null;

        SailPointContext save = SailPointFactory.pushContext();
        try {
            SailPointContext mycon = SailPointFactory.getCurrentContext();
            prev = mycon.getObjectById(Identity.class, neu.getId());
            if (prev != null) {
                prev.loadForChangeDetection();

                // crap, this isn't enough apparently, CertificationRefresher
                // needs to XML serialize the previous version to put into a Request,
                prev.toXml();
            }
        }
        finally {
            SailPointFactory.popContext(save);
        }

        return prev;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Refresh Task Completion
    //
    // These methods are intended for use only in the workflow
    // that is launched by the identity refresh task, or more accurately
    // the Identitizer when provisioning is enabled.  These workflows
    // normally present provisioning forms but not approvals, and when 
    // they're done they call back to the Identitizer to finish the
    // refresh process.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by the Identity Refresh workflow after it has done approvals 
     * and gathered the account completion attributes.  Provision what we can
     * and complete the refresh process.
     * 
     * Arguments:
     * 
     *    identitizer - original Identizer if we didn't suspend
     *    refreshOptions - args for creating a new Identitizer if we need one
     *    project - ProvisioningProject that should be fully compiled
     *    
     *
     * If we didn't suspend then the identity is assumed to still be
     * locked by the Aggregator or IdentityRefreshExecutor.  If we did
     * suspend then we have to lock it.  
     *
     * @ignore
     * !! This may fail and if it does we should return something so the
     * workflow can suspend again for awhile and try again.  As it is now
     * if we can't lock now then the refresh will fail.
     */
    public Object finishRefresh(WorkflowContext wfc) throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        // Identitizer
        // hack, try to resuse this if possible 
        Identitizer idz = (Identitizer)args.get(VAR_IDENTITIZER);
        boolean passThrough = (idz != null);
        if (idz == null) {
            // make an identitizer restoring the original arguments
            Attributes<String,Object> refreshArgs = null;
            Object o = args.get(VAR_REFRESH_OPTIONS);
            if (o instanceof Attributes) {
                refreshArgs = (Attributes<String,Object>)o;
            }
            else if (o instanceof Map) {
                // sadly have to convert
                refreshArgs = new Attributes<String,Object>();
                refreshArgs.putAll((Map)o);
            }
            else {
                log.error("Invalid argument: " + VAR_REFRESH_OPTIONS);
            }
           
            idz = new Identitizer(context, refreshArgs);
        }

        // Identity
        // this may pass through a transient variable, if not we have to 
        // fetch it by name
        Identity identity = (Identity)args.get(VAR_IDENTITY);
        if (identity != null && !passThrough) {
            // got an identity but not the identitizer, shouldn't happen
            // and don't trust it
            log.error("Pass through Identity without Identitizer");
            identity = null;
        }

        if (identity == null) {
            if (passThrough) {
                // got an identitizer without an identity, have to lock
                log.error("Pass through Identitizer without Identity");
                passThrough = false;
            }

            String name = args.getString(VAR_IDENTITY_NAME);
            if (name == null)  {
                // this is required to make it through
                throw new GeneralException("Missing identity name");
            }
            else {
                // have to lock if we're not a pass through
                identity = ObjectUtil.lockIdentity(context, name);
                if (identity == null)
                    throw new GeneralException ("Unable to lock identity");
            }
        }

        try {
            // Previous identity, optional and only valid if passThrogh
            // is looking right
            Identity previous = null;
            if (passThrough) {
                Object o = args.get(VAR_PREVIOUS_VERSION);
                if (o instanceof Identity)
                    previous = (Identity)o;
            }

            // Project, this is optional if the workflow was launched
            // for triggers or other reasons
            ProvisioningProject project = null;
            Object o = args.get(ARG_PROJECT);
            if (o instanceof ProvisioningProject)
                project = (ProvisioningProject)o;

            // Have fun!
            idz.finishRefresh(identity, previous, project);
        }
        finally {
            if (!passThrough)
                ObjectUtil.unlockIdentity(context, identity);
        }

        return null;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Approval Sets
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Build an ApprovalSet for postSplit approvals. This will build the ApprovalSet corresponding to the split
     * ProvisioningPlan. We need to copy over any previous decisions made from preSplit approvals to this ApprovalSet
     * @param wfc
     * @return ApprovalSet
     * @throws GeneralException
     */
    public Object buildSplitApprovalSet(WorkflowContext wfc)
        throws GeneralException {

        ApprovalSet globalSet = null;
        Attributes<String,Object> args = wfc.getArguments();
        if (args!= null) {
            globalSet = (ApprovalSet)args.get(ARG_APPROVAL_SET);
        }


        ApprovalSet splitSet = (ApprovalSet)buildApprovalSet(wfc);

        // Project
        Object o = args.get(ARG_PROJECT);
        if (o != null && (o instanceof ProvisioningProject)) {
            //IIQHH-575 has assimilated changes from project into global set. need to do the same for the newly created
            // split set
            assimilateAccountIdChanges((ProvisioningProject) o, splitSet, null);
        }


        if (globalSet != null) {
            //in a split we want to update the approvalSet
            refreshApprovalSet(wfc,globalSet);
            for (ApprovalItem item : Util.safeIterable(splitSet.getItems())) {
                ApprovalItem globalItem = globalSet.find(item);
                if (globalItem != null) {
                    //Set state to that of the globalSet
                    item.setState(globalItem.getState());
                }

            }
        }

        return splitSet;

    }

    //IIQETN304 during a split apporval workflow we have a case when we create 
    //new accounts and we auto approve that we can end up with an approval set 
    //that is slightly worng.  In the split one of the provisioning actions has
    //occurred and we now move to the next one.  in our search for the approval 
    //item we include the native identity that we now have. The approval set does
    //not have it. here we are updating the approval set with the native identity
    private static void refreshApprovalSet(WorkflowContext wfc, ApprovalSet set){

        Attributes<String,Object> args = wfc.getArguments();

        if(null != set && null != args){
            IdentityRequest ir = null;
            String identityRequestId = Util.getString(args,"identityRequestId");
            SailPointContext ctx = wfc.getSailPointContext();
            if ( identityRequestId != null ) {
                try{
                    ir = ctx.getObjectByName(IdentityRequest.class, identityRequestId);
                } catch (GeneralException e){
                    //do nothing
                }
                if (null != ir){
                    List<ApprovalItem> approvalItems = set.getItems();
                    if ( approvalItems != null ) {
                        for ( ApprovalItem approvalItem : Util.safeIterable(approvalItems) ) {
                            //if this is not null we presume we have the correct one
                            if(null == approvalItem.getNativeIdentity()){
                                List<IdentityRequestItem> items = ir.findItems(approvalItem);
                                if ( items != null ) {
                                    for ( IdentityRequestItem item : Util.safeIterable(items) ) {
                                        if(null != item.getNativeIdentity()){
                                            approvalItem.setNativeIdentity(item.getNativeIdentity());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }    
    
    /**
     * Called by the LCM workflows to build a simplified ApprovalSet 
     * representation of the things in the provisioning plan.  This is used
     * to represent the "shopping cart".
     *
     */
    public Object buildApprovalSet(WorkflowContext wfc)
        throws GeneralException {
        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningPlan plan = (ProvisioningPlan) args.get(VAR_PLAN);
        if ( plan == null ) 
            throw new GeneralException("Provisioning plan required.");

        Identity identity = plan.getIdentity();
        if (identity == null){
            identity = getIdentity(wfc);
        }
        ApprovalSet globalSet = new ApprovalSet();
        if ( plan != null ) {
            List<AccountRequest> requests = plan.getAccountRequests();
            if ( Util.size(requests) > 0 ) {
                for ( AccountRequest request : requests ) {
                    if ( request != null ) {
                        addApprovalItems(identity,request, globalSet, wfc.getSailPointContext());
                    }
                }
            }
        }
        return globalSet;
    }

    /**
     * Add recommendations, if possible, to an existing ApprovalSet
     * @param wfc the workflow, with the "approvalSet" and "identitySource" arguments.
     *            The "identitySource" is used a source from which to get the id of the Identity
     *            that the approvalSet is for.
     *            The "identitySource" argument value should be:
     *              - a ProvisioningPlan object, or
     *              - a IdentityChangeEvent object, or
     *              - an Identity object, or
     *              - a String (the actual identity id)
     * @return the approvalSet, modified with added recommendations
     * @throws GeneralException if the identitySource is an unsupported object type, or if an
     * identity could not be found using the identitySource
     */
    public Object populateRecommendationsInApprovalSet(WorkflowContext wfc) throws GeneralException {
        Attributes<String,Object> args = wfc.getArguments();
        ApprovalSet set = (ApprovalSet) args.get("approvalSet");
        if (set == null) {
            return null;
        }

        String identityId = null;

        Object identitySource = args.get("identitySource");
        if (identitySource instanceof ProvisioningPlan) {
            ProvisioningPlan plan = (ProvisioningPlan)identitySource;
            Identity identity = plan.getIdentity();
            if (identity == null){
                identity = getIdentity(wfc);
            }
            if (identity != null) {
                identityId = identity.getId();
            }
        }
        else if (identitySource instanceof IdentityChangeEvent) {
            IdentityChangeEvent event = (IdentityChangeEvent)identitySource;
            String identityName = event.getIdentityName();
            SailPointContext ctx = wfc.getSailPointContext();
            Identity identity = ctx.getObjectByName(Identity.class, identityName);
            if (identity != null) {
                identityId = identity.getId();
            }
        }
        else if (identitySource instanceof Identity) {
            Identity identity = (Identity)identitySource;
            if (identity != null) {
                identityId = identity.getId();
            }
        }
        else if (identitySource instanceof String) {
            identityId = (String)identitySource;
        }
        else if (identitySource != null) {
            throw new GeneralException("Identity source of type '" + identitySource.getClass().getName() + "' is not supported");
        }

        if (identityId == null) {
            // impossible to get a recommendation for a not-yet-existing identity, so
            // do nothing
            return set;
        }

        SailPointContext ctx = wfc.getSailPointContext();
        CachedManagedAttributer cachedManagedAttributer = new CachedManagedAttributer(ctx);

        for(ApprovalItem approvalItem : Util.safeIterable(set.getItems()) ) {
            calculateAndSetItemRecommendation(identityId, approvalItem, ctx, cachedManagedAttributer);
        }

        return set;
    }

    public static void addApprovalItems(Identity identity, AccountRequest request, ApprovalSet set)
        throws GeneralException {
        addApprovalItems(identity, request, set, SailPointFactory.getCurrentContext());
    }

    /**
     * Assemble ApprovalItems for an AccountRequest.
     * Go through the accountRequests in a provisioning plan and
     * build an approval item.
     *
     * @ignore
     * jsl - this is static so it can be called by 
     * Rule:Build Manual Action Approvals.  
     */
    public static void addApprovalItems(Identity identity, AccountRequest request, ApprovalSet set, SailPointContext ctx) 
        throws GeneralException {

        // jsl - I don't see why we need to make special cases for
        // roles vs. attributes, etc.  revisit this!

        String app = request.getApplication();
        if ( ProvisioningPlan.APP_IIQ.compareTo(app) == 0 ) {
            AccountRequest.Operation op = request.getOperation();
            List<AttributeRequest> attributeRequests = request.getAttributeRequests();
            boolean handled = false;
            if ( ( AccountRequest.Operation.Modify.equals(op) ) && 
                 ( Util.size(attributeRequests) > 0 ) ) {
                for ( AttributeRequest attrRequest : attributeRequests ) {
                    String attrName = attrRequest.getName();
                    if ( ( ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrName) ) ||
                         ( ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrName) ) ) {
                        addApprovalItem(identity, request, attrRequest, set, ctx);
                        // djs: some assumptions about mix and matching not being allowed 
                        handled = true;
                    } else if ( ProvisioningPlan.ATT_IIQ_LINKS.equals(attrName) ) {
                        addApprovalItem(identity, request, attrRequest, set, ctx);
                        handled = true;
                    }
                }
            } 
            if ( !handled ) {
                addApprovalItem(identity, request, set);
            }
        } 
        else {
            AccountRequest.Operation op = request.getOperation();
            if ( op == null ) 
                op = AccountRequest.Operation.Modify;
            
            List<AttributeRequest> attributes = request.getAttributeRequests();
            List<PermissionRequest> permissions = request.getPermissionRequests();
            
            // one item per attribute or permission
            if ( !Util.isEmpty(attributes) || !Util.isEmpty(permissions) ) {
                if (attributes != null) {
                    for ( AttributeRequest attribute : attributes)
                        addApprovalItem(identity, request, attribute, set, ctx);
                }

                if (permissions != null) {
                    for (PermissionRequest permission : permissions)
                        addApprovalItem(identity, request, permission, set, ctx);
                }
            } 

            if ( needsRequestLevelItem(request) ) {
                addApprovalItem(identity, request, set);
            }
        }
    }
    
    /**
     * Figure out if we need to include an approval item for the 
     * accountRequestItem. 
     * 
     * We do this when the operation is one of the AccountLevel 
     * operations like Enable/Disable/Unlock or if there
     * are no attribute/permission requests included.
     * 
     * @param req AccountRequest
     * @return true if the request level item should be added
     */
    private static boolean needsRequestLevelItem(AccountRequest req) {
        AccountRequest.Operation op = req.getOperation();
        if (op == null) 
            op = AccountRequest.Operation.Modify;
        
        List<AttributeRequest> attrs = req.getAttributeRequests();
        List<PermissionRequest> perms = req.getPermissionRequests();
        
        if ( Util.isEmpty(attrs) && Util.isEmpty(perms) ) {
            return true;
        } else
        if ( op != AccountRequest.Operation.Create && op != AccountRequest.Operation.Modify ) {
            return true;
        }
        return false;
    }

    /**
     * Add a representation of an account request to the approval set.
     * Only call this for non-modify events: create, disable, enable, etc.
     */
    public static void addApprovalItem(Identity identity, AccountRequest account, ApprovalSet set)
        throws GeneralException {

        ApprovalItem item = new ApprovalItem();

        item.setApplication(account.getApplication());
        item.setInstance(account.getInstance());
        item.setNativeIdentity(account.getNativeIdentity());

        // Copy the arguments from the account request onto the approval item.
        item.setAttributes(account.getArguments());
        
        AccountRequest.Operation op = account.getOperation();
        if (op == null) {
            // shouldn't be here but assume modify
            op = AccountRequest.Operation.Modify;
        }
        String comment = account.getComments();
        if ( Util.getString(comment) != null ) {
            item.setRequesterComments(comment);
        }
        item.setOperation(op.toString());

        set.add(item);

    }

    /**
     * Add a representation of a single attribute/permission request
     * to the approval set.  If the request has multiple-values
     * this may expand into several items.
     * 
     * @ignore
     * jsl - does this need to be configurable?  Would we ever
     * want the values treated atomically?
     */
    private static void addApprovalItem(Identity identity,
                                        AccountRequest account,
                                        GenericRequest req, 
                                        ApprovalSet set,
                                        SailPointContext ctx) 
        throws GeneralException {

        // configurable ?
        boolean expandList = true;
        Object o = req.getValue();
        
        if ((o instanceof List) && !expandList)
            o = Util.listToCsv((List)o);

        if (o instanceof List) {
            List list = (List)o;
            for (Object el : list) 
                addApprovalItem(identity, account, req, el, set, ctx);
        }
        else {
            addApprovalItem(identity, account, req, o, set, ctx);
        }
    }

    private static void addApprovalItem(Identity identity, AccountRequest account, GenericRequest req, Object value, ApprovalSet set, SailPointContext ctx) 
        throws GeneralException {

        ApprovalItem item = new ApprovalItem();

        // account information
        item.setApplication(account.getApplication());
        item.setInstance(account.getInstance());
        item.setNativeIdentity(account.getNativeIdentity());

        // Copy any account request arguments onto the item.  May eventually
        // want to get arguments off the GenericRequest, too.  Leaving them
        // out for now since some of the interesting things (start/end date)
        // get their own fields.
        item.setAttributes(account.getArguments());
        
        // attribute/permission information
        String attrName = req.getName();
        item.setName(attrName);
        
        //copy assignment id
        item.setAssignmentId(req.getAssignmentId());
        
        //TODO: think about adding displayName to GenericRequest
        if ( ( attrName != null ) &&
             ( ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrName) ) ||
             ( ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrName) ) ) {
            item.setDisplayName(new Message(MessageKeys.APPROVALITEM_ATTRIBUTE_ROLE).getLocalizedMessage());
            
            // need to get the displayable name of the role so we can display it in the work item
            if (value != null) {
                Bundle bundle = ctx.getObjectByName(Bundle.class, value.toString());
                if (null != bundle) {
                    item.setDisplayValue(bundle.getDisplayableName());
                }
            }
        }
        if ( ( attrName != null ) &&
             ( ProvisioningPlan.ATT_IIQ_LINKS.equals(attrName) )) {
            item.setDisplayName(new Message(MessageKeys.LABEL_ACCOUNT).getLocalizedMessage());
        }
        
        if ( req.getDisplayValue() != null) {
            item.setDisplayValue(req.getDisplayValue());
        }
        // Value is coerced to a String, will need to be smarter
        // here if we allow complex object values
        String str = null;
        if (value != null) {
            str = value.toString().trim();
            // collapse these?
            if (str.length() == 0)
                str = null;
        }
        item.setValue(str);

        // store the password display value masked when not generated
        if ( Util.nullSafeCompareTo(attrName, ProvisioningPlan.ATT_PASSWORD) == 0 ) {
            // Don't show non generated passwords in the email
            if (req.get(ProvisioningPlan.ATT_GENERATED) != null) {
                item.setDisplayValue(str);
            }
            else {
                item.setDisplayValue("******");
            }
        }
        
        // Should always be non-null, but default if not
        Operation op = req.getOperation();
        if (op != null)
            item.setOperation(op.toString());
        else
            item.setOperation(Operation.Set.toString());

        // iiqetn-5243 if the attribute or permission request has a comment then use it
        // as the requester comment. If not and there's a comment on the account request 
        // then use it as the requester comment.
        String requesterComments = null;
        String genReqComments = req.getComments();
        if (Util.getString(genReqComments) != null ) {
            requesterComments = genReqComments;
        }

        if (requesterComments == null) {
            String accountComments = account.getComments();
            if (Util.getString(accountComments) != null) {
               requesterComments = accountComments;
            }
        }

        item.setRequesterComments(requesterComments);

        item.setStartDate(req.getAddDate());
        item.setEndDate(req.getRemoveDate());

        // build up an attribureRequest for the value passed in
        GenericRequest attrReq = req.clone();
        attrReq.setValue(value);

        set.add(item);

    }

    private static void calculateAndSetItemRecommendation(String identityId, ApprovalItem item, SailPointContext ctx,
                                                          CachedManagedAttributer cma) {
        if (identityId == null || item == null) {
            return;
        }

        try {
            Recommendation recommendation = RecommenderUtil.getApprovalItemRecommendation(identityId, item, ctx, cma);
            item.setRecommendation(recommendation);
        }
        catch (GeneralException e) {
            log.warn("Failed to calculate recommendation for approval item", e);
            // continue, because we don't let a recommendation failure stop
            // the generation of the approval
        }
    }

    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Approval Generation
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Build an approval for the identity's manager based on the
     * approvalSet that's passed in and should represent "the cart".
     *
     * @param wfc WorkflowContext
     * 
     * @return List of Workflow.Approvals objects for the Manager
     * @throws GeneralException
     * 
     * @ignore
     * NOTE: We used to build these approvals in a rule, just by name
     * but wanted parity with our other approvals so the 
     * approval is explicitly marked approved by the launcher
     * to help the code that is filtering requests based
     * on the decisions. 
     *
     */
    public Object buildManagerApproval(WorkflowContext wfc) 
        throws GeneralException {
        
        return new IdentityApprovalGenerator(wfc).buildManagerApproval();
    }
    
    /**
     * Security Officer approvals are typically decared by the officers
     * name on the workflow.  Build an approval for the security officer
     * and auto approve if security officer is the launcher.
     * 
     * Auto approval will be disabled if an electronic signature
     * is required for the ApprovalStep.
     * 
     * @param wfc WorkflowContext
     * 
     * @return List of Approval objects for the security officer
     * @throws GeneralException
     * 
     *
     * This also used to happen in a rule, but moved it to java-code
     * to make it simpler to read and easier to debug.
     */
    public Object buildSecurityOfficerApproval(WorkflowContext wfc) 
        throws GeneralException {
        
        return new IdentityApprovalGenerator(wfc).buildSecurityOfficerApproval();
    }
     
    /**
     *
     * Owner approvals are the most complex type of OOTB 
     * approvals.  This takes the approvalSet representing
     * the cart and breaks it each item into a separate
     * approval built for the owner.
     * 
     * Auto approval will be disabled if an electronic signature
     * is required for the ApprovalStep.
     * 
     * @param wfc WorkflowContext
     * 
     * @return List of Approval objects for the owners
     * 
     * @since 6.2
     *      
     * @ignore     
     * This also used to happen in a rule, but moved it to java-code
     * to make it simpler to read and easier to debug.
     */
    public Object buildOwnerApprovals(WorkflowContext wfc) 
        throws GeneralException {
        
        return new IdentityApprovalGenerator(wfc).buildOwnerApprovals();
    }
    
    /**
     * For Identity Create and Update this logic was moved
     * to java code from  Rules to more easily maintainable.
     * 
     * For Identity Create and Update the approvals are editable
     * and have a single approval item for all the attributes. The
     * attributes are flattened to name=value pairs. A nested
     * Form displays each value and allows approvers to update
     * any of the values.   
     * 
     * @return a list of Approvals one for each approver  
     * 
     * @since 6.2
     */
    public Object getIdentityCreateUpdateApprovals(WorkflowContext wfc) 
        throws GeneralException {        
        
        return new IdentityApprovalGenerator(wfc).getIdentityApprovals();
    }
    
    /**
     * 
     * Based on the 'approvalScheme' value generate a list of workflow.Approval
     * objects for each approver. If 'clearApprovalDecisions' variable is true,
     * all approval decisions will be nulled out
     * 
     * @since 6.2
     * 
     * @param wfc WorkflowContext
     * @return List of Approvals one for each approval
     * 
     * @throws GeneralException
     */
    public Object buildCommonApprovals(WorkflowContext wfc) 
        throws GeneralException {
        List<Approval> approvals = new IdentityApprovalGenerator(wfc).buildCommonApprovals();

        Attributes<String, Object> approvalArgs = wfc.getApprovalArguments();
        if (approvalArgs != null) {
            boolean clearDecisions = approvalArgs.getBoolean(ARG_CLEAR_APP_DECISIONS);
            if (clearDecisions) {
                clearApprovalDecisions(approvals);
            }
        }
        return approvals;
    }

    /**
     * Add all plans in the projects returned into 'splitProjects' into the master Project.
     * @param wfc
     * @throws GeneralException
     */
    public void assimilateSplitProjects(WorkflowContext wfc) throws GeneralException {
        SailPointContext spcon = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();

        List<ProvisioningProject> projects = Util.asList(args.get(VAR_SPLIT_PROJECTS));

        //Get the plans from the projects
        List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
        for (ProvisioningProject p : Util.safeIterable(projects)) {
            if (!Util.isEmpty(p.getPlans())) {
                plans.addAll(p.getPlans());
            }
        }

        ProvisioningProject masterProject = (ProvisioningProject) args.get(VAR_PROJECT);
        if (!Util.isEmpty(plans) && masterProject != null) {
            //Update the plans on the GlobalProject with the plans returned from the split
            masterProject.setPlans(plans);
        }
    }

    /**
     * Join the workItem comments from the split workflows stored in 'splitWorkItemComments' into the master 'workItemComments'
     * @param wfc
     * @throws GeneralException
     */
    public void assimilateSplitWorkItemComments(WorkflowContext wfc)
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        List<Comment> comments = Util.asList(args.get(VAR_SPLIT_COMMENTS));
        Object current = args.get(Workflow.VAR_WORK_ITEM_COMMENTS);

        Workflower.mergeValues(current, comments);

    }

    /**
     * Join the ApprovalSets from the split workflows stored in 'splitApprovalSet into the master 'approvalSet'.
     *
     * @param wfc
     * @throws GeneralException
     */
    public void assimilateSplitApprovalSets(WorkflowContext wfc)
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        List<ApprovalSet> retSets = Util.asList(args.get(VAR_SPLIT_APPROVALSETS));

        ApprovalSet currentSet = (ApprovalSet)args.get(ARG_APPROVAL_SET);

        if (currentSet != null && retSets != null) {
            for(ApprovalSet set : Util.safeIterable((List<ApprovalSet>)retSets)) {
                for(ApprovalItem item : set.getItems()) {
                    currentSet.findAndMergeItem(item, item.getOwner(), null, true);
                }
            }
        }

    }

    /**
     * If using split in the LCM Provisioning workflow, this will join all returns back into the master workflow
     * once all splits have completed.
     * Assimilate the ProvisioningPlans in the joins into the master project
     * Assimilate the ApprovalSet from the joins into the master project
     * Assimilate the WorkItemComments from the joins into the master project
     *
     * This will commit the workflowContext after assimilation.
     * @param wfc
     * @throws GeneralException
     */
    public void joinLCMProvWorkflowSplits(WorkflowContext wfc)
        throws GeneralException {

        //Assimilate the ApprovalSets
        assimilateSplitApprovalSets(wfc);

        //Assimilate the ProvisioningPlans into the master project
        assimilateSplitProjects(wfc);

        //Assimilate the WorkItemComments into the master
        assimilateSplitWorkItemComments(wfc);

        //Refresh the IdentityRequest
        IdentityRequestLibrary.refreshIdentityRequestAfterJoin(wfc);

    }

    /**
     * Null decisions for all Approval Items within a list of Approvals. When building postSplitApprovals, the ApprovalSet
     * may contain previous decisions. In this case, the Approvals automatically inherit these decisions. We need to clear
     * these decisions. If 'setPreviousApprovalDecisions' is set, the interceptor script will set the decisions to the
     * global ApprovalSet decision.
     * @param approvals
     */
    private static void clearApprovalDecisions(List<Approval> approvals) {
        for (Approval app : Util.safeIterable(approvals)) {
            ApprovalSet appSet = app.getApprovalSet();
            if (appSet != null) {
                for (ApprovalItem item : Util.safeIterable(appSet.getItems())) {
                    //Set State null
                    item.setState(null);
                }
            }

            if (!Util.isEmpty(app.getChildren())) {
                //Recursively null decisions for children
                clearApprovalDecisions(app.getChildren());
            }
        }
    }

    /**
     * Split the masterPlan into itemized ProvisioningPlans. This will create a ProvisioningPlan per independent
     * AttributeRequest when dealing wiht Modify AccountRequest. When splitting, we will keep all AttributeRequests
     * with the same assignmentId together under the same ProvisioningPlan.
     *
     * If AccountRequest is not a modify operation, we will keep the AccountRequest as a whole.
     *
     * @param wfc
     * @return List of ProvisioningPlan
     * @throws GeneralException
     *
     * @ignore
     * This will not work with PermissionRequests yet. We do not currently support requesting permissions, so this is
     * not an issue
     *
     */
    public Object splitProvisioningPlan(WorkflowContext wfc)
        throws GeneralException {
        List<ProvisioningPlan> splitPlans = new ArrayList<ProvisioningPlan>();
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject proj = (ProvisioningProject) args.get("project");
        if ( proj == null ) {
            throw new GeneralException("Provisioning project required.");
        }

        ProvisioningPlan plan = proj.getMasterPlan();
        if ( plan == null ) {
            throw new GeneralException("Provisioning master plan required.");
        }
        //Map to store the AccountRequests associated to a given AssignmentId. When splitting, we need to keep
        //all accountRequests with a given assignmentId together.
        Map assignments = new HashMap<String, ProvisioningPlan>();

        //Map to store the trackingId associated with a given applicationName. This will be used to update the
        //Identity Requests with the native Identity after the create has taken place.
        Map trackingIds = new HashMap<String, String>();

        boolean updateIdentRequestItems = false;

        IdentityRequest ir = null;
        String identityRequestId = Util.getString(args,"identityRequestId");
        SailPointContext ctx = wfc.getSailPointContext();
        if ( identityRequestId != null ) {
            ir = ctx.getObjectByName(IdentityRequest.class, identityRequestId);
        }


        for(AccountRequest ar : Util.safeIterable(plan.getAccountRequests())) {

            if (AccountRequest.Operation.Modify == ar.getOperation()) {

                for(AttributeRequest attR : Util.safeIterable(ar.getAttributeRequests())) {
                    if (Util.isNotNullOrEmpty(attR.getAssignmentId())) {
                        String trackId = null;
                        if (ProvisioningPlan.isIIQ(ar.getApplication())) {
                            //See if we have a ProvisioningTarget with doCreate
                            for(ProvisioningTarget targ : Util.safeIterable(plan.getProvisioningTargets())) {
                                if (Util.nullSafeEq(targ.getAssignmentId(), attR.getAssignmentId())) {
                                    for(AccountSelection selection : Util.safeIterable(targ.getAccountSelections())) {
                                        if (selection.isDoCreate()) {
                                            //Create for this app. Set the trackingId
                                            trackId = (String)trackingIds.get(selection.getApplicationName());
                                            if (Util.isNullOrEmpty(trackId)) {
                                                trackId = Util.uuid();
                                                trackingIds.put(selection.getApplicationName(), trackId);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        //See if we have already encountered this assignmentId
                        ProvisioningPlan prevPlan = (ProvisioningPlan)assignments.get(attR.getAssignmentId());
                        if (prevPlan != null) {
                            //Update plan with AttributeRequest
                            AccountRequest prevAr = prevPlan.getAccountRequest(ar.getApplicationName(), ar.getInstance(), ar.getNativeIdentity());
                            //If we have a matching AccountRequest, add the attributeRequest to the AccountRequest
                            if (prevAr != null) {
                                prevAr.add(attR);
                            } else {
                                AccountRequest neuAcctReq = new AccountRequest();
                                //Clone the AccountRequest properties on the new AccountRequest
                                neuAcctReq.cloneAccountProperties(ar);
                                //Set the AttributeRequest on the AccountRequest
                                neuAcctReq.setAttributeRequests(Arrays.asList(attR));
                                prevPlan.add(neuAcctReq);
                            }
                        } else {
                            ProvisioningPlan neuPlan = createSplitPlan(proj, plan, ar, Arrays.asList(attR));
                            boolean irNeedsSaved = addTrackingIdsToPlan(neuPlan, ar, ir, trackingIds, attR);
                            if (irNeedsSaved) {
                                updateIdentRequestItems = true;
                            }
                            assignments.put(attR.getAssignmentId(), neuPlan);
                        }

                    } else {
                        ProvisioningPlan neuPlan = createSplitPlan(proj, plan, ar, Arrays.asList(attR));
                        boolean irNeedsSaved = addTrackingIdsToPlan(neuPlan, ar, ir, trackingIds, attR);
                        if (irNeedsSaved) {
                            updateIdentRequestItems = true;
                        }
                        splitPlans.add(neuPlan);
                    }

                }
            } else {
                //Create/Delete/Enable/Disable/Lock/Unlock - Keep any associated attributeRequests with the AccountRequest
                splitPlans.add(createSplitPlan(proj, plan, ar, ar.getAttributeRequests()));

            }
        }

        //Add all Plans with AssignmentIds to the splitPlans
        if (!assignments.isEmpty()) {
            splitPlans.addAll(assignments.values());
        }

        //Combine all plans with overlapping creates
        if (!args.getBoolean(ARG_SPLIT_DISABLE_COMBINE)) {
            splitPlans = combineCreatePlans(splitPlans);
        }

        //Could be smarter about not doing this if there is not more than one
        if (updateIdentRequestItems) {
            //Need to update the IdentityRequestItems with the tracking id's
            ctx.saveObject(ir);
            ctx.commitTransaction();

        }

        return splitPlans;

    }

    /**
     * Combine ProvisioningPlans with overlapping creates.
     *
     * If 1 overlaps with 2 && 2 overlaps with 3 -- 1/2/3 will all get combined
     * @param splitPlans
     * @return
     */
    protected List<ProvisioningPlan> combineCreatePlans(List<ProvisioningPlan> splitPlans) {

        if (log.isInfoEnabled()) {
            StringBuilder builder = new StringBuilder();
            try {
                builder.append("splitPlans prior to combination[");
                for (ProvisioningPlan p : Util.safeIterable(splitPlans)) {
                    builder.append(p.toXml());
                }
                builder.append("]");
            } catch (GeneralException ge) {
                log.warn("Error serializing ProvisioningPlan");
            }
            log.info(builder.toString());
        }
        List<ProvisioningPlan> combinedPlans = new ArrayList<ProvisioningPlan>();

        if (splitPlans != null) {
            ListIterator<ProvisioningPlan> iter = splitPlans.listIterator();
            while (iter.hasNext()) {
                ProvisioningPlan p = iter.next();
                ProvisioningPlan combined = new ProvisioningPlan(p);
                //Questions don't come across
                combined.setQuestionHistory(p.getQuestionHistory());
                iter.remove();
                for (AccountRequest accntReq : Util.safeIterable(p.getAccountRequests())) {
                    for (AttributeRequest attR : Util.safeIterable(accntReq.getAttributeRequests())) {
                        //Find ProvisioningTarget
                        ProvisioningTarget targ = getProvisiongTarget(accntReq, attR, p.getProvisioningTargets());
                        if (targ != null) {
                            //See if any creates
                            for (AccountSelection selection : Util.safeIterable(targ.getAccountSelections())) {
                                if (selection.isImplicitCreate() || selection.isDoCreate()) {
                                    //See if anything else is overlapping
                                    List<ProvisioningPlan> overlaps = findOverlap(selection, splitPlans);
                                    if (!Util.isEmpty(overlaps)) {
                                        //Combine the overlaps with the current
                                        for (ProvisioningPlan overlapPlan : Util.safeIterable(overlaps)) {
                                            combined.merge(overlapPlan);
                                        }
                                        //Update Iterator, findOverlap removed some elements
                                        iter = splitPlans.listIterator();
                                    }
                                }
                            }
                        }


                    }
                }
                combinedPlans.add(combined);
            }
        }

        if (log.isInfoEnabled()) {
            StringBuilder builder = new StringBuilder();
            try {
                builder.append("splitPlans after combination[");
                for (ProvisioningPlan p : Util.safeIterable(combinedPlans)) {
                    builder.append(p.toXml());
                }
                builder.append("]");
            } catch (GeneralException ge) {
                log.warn("Error serializing ProvisioningPlan");
            }
            log.info(builder.toString());
        }

        return combinedPlans;

    }

    private List<ProvisioningPlan> findOverlap(AccountSelection selection, List<ProvisioningPlan> plans) {

        List<ProvisioningPlan> overlap = new ArrayList<>();

        if (plans != null) {
            ListIterator<ProvisioningPlan> iter = plans.listIterator();
            while (iter.hasNext()) {
                ProvisioningPlan p = iter.next();
                if (p != null) {
                    boolean removed = false;
                    for (AccountRequest accntReq : Util.safeIterable(p.getAccountRequests())) {
                        for (AttributeRequest attR : Util.safeIterable(accntReq.getAttributeRequests())) {
                            //Find ProvisioningTarget
                            ProvisioningTarget targ = getProvisiongTarget(accntReq, attR, p.getProvisioningTargets());
                            if (targ != null) {
                                List<AccountSelection> skippedCreates = new ArrayList<>();
                                boolean foundOverlap = false;
                                //See if any creates
                                for (AccountSelection sel : Util.safeIterable(targ.getAccountSelections())) {
                                    if (sel.isImplicitCreate() || sel.isDoCreate()) {
                                        if ((Util.nullSafeEq(sel.getApplicationName(), selection.getApplicationName())
                                                || Util.nullSafeEq(sel.getApplicationId(), selection.getApplicationId()))
                                            && Util.nullSafeEq(sel.getSelection(), selection.getSelection(), true)) {
                                            //Remove this from the iterator, since it's now grouped
                                            if (!removed) {
                                                //Only remove once
                                                overlap.add(p);
                                                iter.remove();
                                                removed = true;
                                            }

                                            //Need to iterate all other AccountSelections and find overlap on them as well
                                            foundOverlap = true;

                                        } else {
                                            //we've found a create that's not the same as the original, traverse to find others
                                            skippedCreates.add(sel);
                                        }
                                    }
                                }


                                if (foundOverlap) {
                                    //We found some overlap, need to traverse the rest of the creates in this ProvTarg and find their overlap
                                    for (AccountSelection skippedSelection : Util.safeIterable(skippedCreates)) {
                                        List<ProvisioningPlan> secondaryOverlaps = findOverlap(skippedSelection, plans);
                                        if (!Util.isEmpty(secondaryOverlaps)) {
                                            overlap.addAll(secondaryOverlaps);
                                            //Items were removed from underlying collection, update Iterator
                                            iter = plans.listIterator();
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        return overlap;

    }

    // This method was ungracefully factored out from splitProvisioningPlan() method, for IIQPB-564
    // Returns true if the IdentityRequest changes need to be committed now, otherwise false.
    private boolean addTrackingIdsToPlan(ProvisioningPlan neuPlan, AccountRequest ar, IdentityRequest ir, Map trackingIds, AttributeRequest attR) {
        boolean needToUpdateIR = false;

        //If no Native Identity on a non IIQ request, will be compiled into a create. First one will create
        //and second will modify. Need to set the trackingId so when create happens, the nativeIdentity can
        //be set on all corresponding IdentityRequestItems
        if (!ProvisioningPlan.isIIQ(ar.getApplication()) && Util.isNullOrEmpty(ar.getNativeIdentity())) {
            if (trackingIds.containsKey(ar.getApplication())) {
                neuPlan.setRequestTrackingId((String) trackingIds.get(ar.getApplication()));
                addTrackingIdsToIdentityRequestItem(ir, ar, attR, (String) trackingIds.get(ar.getApplication()));
                needToUpdateIR = true;
            } else {
                String trackingId = Util.uuid();
                trackingIds.put(ar.getApplication(), trackingId);
                neuPlan.setRequestTrackingId(trackingId);
                addTrackingIdsToIdentityRequestItem(ir, ar, attR, trackingId);
            }
        }
        return needToUpdateIR;
    }

    //Set the trackingId attribute on the IdentityRequestItem. This will be used to set the native identity on all corresponding
    //IdentityRequestItems after the create has succeeded.
    private void addTrackingIdsToIdentityRequestItem(IdentityRequest ir, AccountRequest accntReq, AttributeRequest attReq, String trackingId) {

        IdentityRequestItem item = ir.findItem(accntReq, attReq, Util.otoa(attReq.getValue()));
        if (item != null) {
            item.setAttribute(IdentityRequestItem.ATT_TRACKING_ID, trackingId);
        }
    }

    /**
     * Create a provisioning plan with the Given AccountRequest and AttributeRequest. This will clone
     * the previousPlan, and copy over QuestionHistory to the new plan from the ProvisioningProject
     * @param proj
     * @param previousPlan
     * @param acctR
     * @param attR
     * @return ProvisioningPlan
     * @throws GeneralException
     */
    private ProvisioningPlan createSplitPlan(ProvisioningProject proj, ProvisioningPlan previousPlan, AccountRequest acctR, Collection<AttributeRequest> attR)
        throws GeneralException {
        ProvisioningPlan neuPlan = new ProvisioningPlan(previousPlan, false);
        if (proj != null) {
            //Add QuestionHistory and ProvisioningTargets to the new plan so they can be used when compiling.
            neuPlan.setQuestionHistory(proj.getQuestionHistory());
            neuPlan.setProvisioningTargets(proj.getProvisioningTargets());
        }

        AccountRequest neuAcctReq = new AccountRequest();
        //Clone the AccountRequest properties on the new AccountRequest
        neuAcctReq.cloneAccountProperties(acctR);

        //Set the AttributeRequest on the AccountRequest
        neuAcctReq.addAll(attR);

        neuPlan.add(neuAcctReq);

        // Assign a trackingId to the splitPlan if there is not already one.  This will help when updating
        // the IdentityRequest project information during the split provisioning.
        if (Util.isNullOrEmpty(neuPlan.getTrackingId())) {
            neuPlan.setTrackingId(Util.uuid());
        }

        return neuPlan;
    }


    private ProvisioningTarget getProvisiongTarget(AccountRequest accntReq, AttributeRequest req, List<ProvisioningTarget> targs) {

        ProvisioningTarget matchedTarget = null;

        for (ProvisioningTarget t : Util.safeIterable(targs)) {
            if (Util.nullSafeEq(req.getAssignmentId(), t.getAssignmentId(), false)) {
                //match assignmentId, call it good
                matchedTarget = t;
                break;
            } else if (Util.isNullOrEmpty(req.getAssignmentId())) {
                //If attributeReq has an assignmentId, find the target matching the assignmentId

                if (LcmAccessRequestHelper.isRoleRequest(req)) {
                    //Shouldn't get here, shoud have assignmentId
                    //Compare on roleName
                    if (Util.nullSafeEq(req.getValue(), t.getRole(), false)) {
                        matchedTarget = t;
                        break;
                    }
                } else {
                    //Attribute
                    if (Util.nullSafeEq(accntReq.getApplicationName(), t.getApplication()) &&
                            Util.nullSafeEq(req.getName(), t.getAttribute()) &&
                            Util.nullSafeEq(req.getValue(), t.getValue())){
                        matchedTarget = t;
                        break;
                    }

                }
            }

        }

        return matchedTarget;
    }
    /**
     * Static because we need it to be called by Rules that are generating
     * approvals.  Check with the approval step arguments and see if we
     * can find an associated electronic signature.
     * 
     * @param wfc WorkflowContext
     * @return true if there is an electronic signature
     *   
     * @ignore
     * BUG#16568 : eSignatures: need to prevent auto-approval of LCM requests 
     * when signature required.
     */
    public static boolean isElectronicSignatureEnabled(WorkflowContext wfc) {    
        boolean hasEsig = false;

        Attributes<String,Object> approvalArgs = wfc.getApprovalArguments();
        if ( approvalArgs != null ) {
            String esig = approvalArgs.getString(WorkItem.ATT_ELECTRONIC_SIGNATURE);
            if ( esig != null ) {
                hasEsig = true;
            }
        }
        return hasEsig;
    }  

    ///////////////////////////////////////////////////////////////////////////
    //
    // Approval Set Assimilation
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Option to processApprovalDecisions to prevent modification 
     * of the plan.
     * 
     * @ignore
     * jsl - I think I'd like this on by default, rename to "updatePlan"
     * or better just have it sensitive to the plan being passed in.
     */
    public static final String ARG_DONT_UPDATE_PLAN = "dontUpdatePlan";

    /**
     * Argument that specifies an ApprovalSet object.
     */ 
    public static final String ARG_APPROVAL_SET = "approvalSet";

    /**
     * Argument to specify if Approval decisions should be cleared
     */
    public static final String ARG_CLEAR_APP_DECISIONS = "clearApprovalDecisions";
    
    /**
     * Variable that's included in all of the default workflows
     * to drive how/if approvals are generated.
     */
    public static final String VAR_APPROVAL_SCHEME = "approvalScheme";

    /**
     * Variable used to determine where to split approvals via Replicator
     * and run the rest of the way in parallel
     */
    public static final String VAR_APPROVAL_SPLIT_POINT = "approvalSplitPoint";

    /**
     * Flag that can be added to the system config or the 
     * workflow to change the behavior so that items
     * with a null decision are considered approved.
     */
    private static final String VAR_NULL_DECISION_APPROVED = "nullDecisionApproved";

    /**
     * Variable used to store workItemComments
     */
    public static final String VAR_WORK_ITEM_COMMENTS = "workItemComments";

    /**
     * Process the decisions that were made during the approval process
     * audit and react. This takes in a project and modifies
     * the project's masterPlan.  It will also optionally recompile the project
     * if the "recompile" flag is set to true.
     */
    public Object processApprovalDecisions(WorkflowContext wfc ) 
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject) args.get("project");
        if ( project == null ) 
            throw new GeneralException("Provisioning project required.");

        ProvisioningPlan plan = project.getMasterPlan();
        if ( plan == null ) 
            throw new GeneralException("Provisioning master plan required.");

        processDecisions(wfc, plan);

        boolean recompile = args.getBoolean("recompile");
        if ( recompile ) {
            Provisioner provisioner = new Provisioner(wfc.getSailPointContext());
            project = provisioner.recompile(project, wfc.getStepArguments());
        }
        return project;
    } 

    /**
     * Process the decisions that were made during the approval process
     * audit and modify the plan. This takes in a plan and modifies
     * the plan and passes back a modified plan.
     */
    public Object processPlanApprovalDecisions(WorkflowContext wfc ) 
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningPlan plan = (ProvisioningPlan) args.get("plan");
        if ( plan == null ) 
            throw new GeneralException("Provisioning plan required.");

        processDecisions(wfc, plan);
        return plan;
    }

    /**
     * Check with the item to see if its approved or rejected if there
     * is a state on the item, it wins.
     * 
     * If the item has a null decision default to rejected.
     * 
     * Implicitly approve password based approvals ( using the flow)
     * and cases where the approvalScheme is "none".
     * 
     * In case we get into a bind allow a system configuration and
     * workflow level variable to configure the behavior.
     * 
     * @param wfc WorkflowContext
     * @param item ApprovalItem
     * @return True if approved, otherwise false
     * @throws GeneralException
     */
    private boolean isApproved(WorkflowContext wfc, ApprovalItem item) 
        throws GeneralException {
        
        if ( item == null ) 
            return false;
        
        WorkItem.State itemState = item.getState();
        
        // If its explicitly approved or rejected
        // use the decision
        if ( Util.nullSafeEq(WorkItem.State.Finished, itemState ) ) {
            return true;
        } else
        if ( Util.nullSafeEq(WorkItem.State.Rejected, itemState ) ) {
            return false;
        }
        
        // No decision...
        if ( itemState == null ) {
            Attributes<String,Object> args = wfc.getArguments();
            // Password flows do not have approvals, so if they don't have a state
            // and we are in the known flow, implicitly approve
            String flowName = args.getString(VAR_FLOW);       
            if ( Util.nullSafeEq(IdentityRequest.PASSWORD_REQUEST_FLOW, flowName ) ||
                 Util.nullSafeEq(IdentityRequest.EXPIRED_PASSWORD_FLOW, flowName ) ||
                 Util.nullSafeEq(UNLOCK_ACCOUNT_FLOW.value(), flowName) ||
                 Util.nullSafeEq(IdentityRequest.FORGOT_PASSWORD_FLOW, flowName ) ) {
                return true;    
            }

            //
            // If there are no approvals then it's always implictly
            // approved.
            //
            String approvalScheme = getApprovalScheme(wfc);
            if ( Util.nullSafeEq("none", approvalScheme) ) {
                return true;
            }

            // This changed slightly in 6.0p2 - prior to this change (BUG#14342)
            // we only considered things that were explicitly rejected --
            // rejected.  Moving forward the inverse will be assumed, if there
            // is no decision it will be considered rejected.  Only explicityApproved 
            // items are approved. Allow a WF argument or system config setting to allow
            // reverting to the old behavior. If the WF argument is not present, we will
            // default to the SystemConfig setting
            String nullDecisionApproved  = null;
            if (args.containsKey(VAR_NULL_DECISION_APPROVED)) {
                nullDecisionApproved = args.getString(VAR_NULL_DECISION_APPROVED);
            } else {
                Configuration config = wfc.getSailPointContext().getConfiguration();
                if ( config != null ) {
                    nullDecisionApproved = config.getString(VAR_NULL_DECISION_APPROVED);
                }
            }

            if ( Util.otob(nullDecisionApproved ) ) {
                return true;
            }             
        }        
        // all other states (which aren't valid here ) are considered REJECTED
        return false;
    }
    
    /**
     * 
     * The decisions are processed in the "Identity Request Provisioning"
     * subprocess.  Prior to the changes for bug #1432 the approval
     * scheme was not passed into the subprocess.
     * 
     * This method checks the top level context for non-null schema
     * and continues digging into parent context until the 
     * approvalSchema attribute is found.
     * 
     * @param wfc WorkflowContext
     * @return String approval scheme
     */
    private String getApprovalScheme(WorkflowContext wfc) {
        Attributes<String,Object> args = wfc.getArguments();
        String approvalScheme = args.getString(VAR_APPROVAL_SCHEME);
        if ( approvalScheme == null && !args.containsKey(VAR_APPROVAL_SCHEME) ) {
        	WorkflowContext parent = wfc.getParent();
            while ( parent != null ) {
                Attributes<String,Object> parentArgs = parent.getArguments();
                if ( parentArgs != null && parentArgs.containsKey(VAR_APPROVAL_SCHEME) ) {
                    approvalScheme = parentArgs.getString(VAR_APPROVAL_SCHEME);                        
                    break;
                }
                parent = parent.getParent();
            }
        }
        return approvalScheme;
    }

    private ProvisioningPlan processDecisions(WorkflowContext wfc, ProvisioningPlan plan) 
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        boolean dontModifyPlan = args.getBoolean(ARG_DONT_UPDATE_PLAN);
        boolean disableAudit = args.getBoolean(ARG_DISABLE_AUDIT);
        
        ApprovalSet set = (ApprovalSet)args.get(ARG_APPROVAL_SET);
        if ( set == null ) 
            throw new GeneralException("ApprovalSet arg required.");
        
        if ( set != null ) {
            List<ApprovalItem> items = set.getItems();
            if ( Util.size(items) > 0 ) {
                for ( ApprovalItem item : items ) {
                    if ( item == null) continue;
                    if ( !dontModifyPlan ) {                     
                        boolean approved = isApproved(wfc, item);
                        if ( !approved ) {  
                            String flowName = args.getString(VAR_FLOW);//
                            /** If this is an rejected assigned role request, we need to analyze it to see 
                             * if this request contains roles that are permits of it...if there are, we need to remove them */
                            if(RequestAccessService.FLOW_CONFIG_NAME.equals(flowName)) {
                                checkAssignedRejection(item, set, plan, wfc);
                            }
                            removeFromPlan(plan, item);
                        } else {
                            updatePlan(plan, item);
                        }
                    }
                    if ( !disableAudit ) 
                        auditDecision(wfc, item);
                }
            }

            // not sure if we want to include the updateTaskResultArtifacts step
            IdentityRequestLibrary.assimilateWorkItemApprovalSetToIdentityRequest(wfc, set);
        }
        return plan;
    }
    
    /** 
     * Inspect each sub-approval to determine if it corresponds to the specified rejected role assignment.
     * If so, strip it from the plan
     * @param rejectedAssignmentApproval ApprovalItem corresponding to the rejected role assignment
     * @param set ApprovalSet containing the role assignment as well as all subapprovals
     * @param plan ProvisioningPlan for the rejected role assignment
     */
    public void checkAssignedRejection(ApprovalItem rejectedAssignmentApproval, ApprovalSet set, ProvisioningPlan plan, WorkflowContext wfc) {
        if(rejectedAssignmentApproval.getName().equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES)) {
            Object name = rejectedAssignmentApproval.getValue();
            String approvalRoleName = null;
            if(name instanceof String) {
                approvalRoleName = (String)name;
            } else if(name instanceof List) {
                approvalRoleName = Util.listToCsv((List)name);
            }
            
            SailPointContext context = wfc.getSailPointContext();
            try {
                if(approvalRoleName!=null) {
                    Bundle approvalRole = context.getObjectByName(Bundle.class, approvalRoleName);
                    if(approvalRole!=null && approvalRole.getRequirements()!=null) {
                        String rejecter = rejectedAssignmentApproval.getOwner();
                        Set<String> permitNames = getPermitNames(approvalRole);

                        for(ApprovalItem potentialSubApproval : set.getItems()) {
                            Object potentialSubApprovalValue = potentialSubApproval.getValue();
                            String subApprovalRoleName = null;
                            if(potentialSubApprovalValue instanceof String) {
                                subApprovalRoleName = (String)potentialSubApprovalValue;
                            } else if(potentialSubApprovalValue instanceof List) {
                                subApprovalRoleName = Util.listToCsv((List)potentialSubApprovalValue);
                            }
                            if(permitNames.contains(subApprovalRoleName)) {
                                // Only reject this item one time
                                if (WorkItem.State.Rejected != potentialSubApproval.getState()) {
                                    String comment = getMessage(MessageKeys.COMMENT_PARENT_ROLE_REJECTED, approvalRoleName);
                                    potentialSubApproval.setState(WorkItem.State.Rejected);
                                    potentialSubApproval.add(new Comment(comment, rejecter));
                                    potentialSubApproval.addRejecter(rejecter);
                                }
                                // We should always remove the rejected sub-approval from the plan even 
                                // if the item had been rejected before because there is no guarantee that 
                                // we're always rejecting in the same plan.
                                // i.e. sometimes we're removing from the project's master plan and sometimes 
                                // we're removing from the step's plan
                                removeFromPlan(plan, potentialSubApproval);
                            }
                        }
                    }
                }
            } catch(GeneralException ge) {
                log.warn("Unable to fetch role with name: " + approvalRoleName + ". Exception: " + ge.getMessage());
            }
        }
    }

    /*
     * @return Set of names of the permitted roles for the given role
     */
    private Set<String> getPermitNames(Bundle role) {
        Set<String> permitNames = new HashSet<String>();
        List<Bundle>permits = role.getPermits();
        if (!Util.isEmpty(permits)) {
            for(Bundle permit : permits) {
                permitNames.add(permit.getName());
            }
        }
        return permitNames;
    }

    /**
     * Use the ApprovalItem to update the plan's start and end 
     * dates. Approvers can change these dates, so assimilate
     * them back into the plan, last one in wins for parallel
     * schemes.
     */
    private void updatePlan(ProvisioningPlan plan, ApprovalItem item) 
        throws GeneralException {

        List<AccountRequest> planRequests = plan.getAccountRequests();
        if ( Util.size(planRequests) > 0 ) {
            for ( AccountRequest planAcct : planRequests ) {
                if ( !matchesAccountRequest(planAcct, item) )
                    continue;

                // Match on app, accountId and instance so now lets find the 
                // attribute request
                List<AttributeRequest> attrReqs = planAcct.getAttributeRequests();
                if ( Util.size(attrReqs) > 0  ) {
                    // Find matching attribute request
                    for ( AttributeRequest attrReq : attrReqs ) {
                        if ( matchesAttributeRequest(attrReq, item) ) {
                            // If the values match update the request with the approval 
                            // items sunrise and sunset dates
                            attrReq.setAddDate(item.getStartDate());
                            attrReq.setRemoveDate(item.getEndDate());
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Use the ApprovalItem to find the Account/Attribute Request
     * and remove the rejected item from the plan. 
     */
    private void removeFromPlan(ProvisioningPlan plan, ApprovalItem item)
        throws GeneralException {

        String attrName = item.getName();
        List<String> itemValues = item.getValueList();        

        List<AccountRequest> planRequests = plan.getAccountRequests();
        if ( Util.size(planRequests) > 0 ) {
            // make a copy to prevent concurrent modificatoin when updating 
            List<AccountRequest> acctRequestCopy = new ArrayList<AccountRequest>(planRequests);
            for ( AccountRequest planAcct : acctRequestCopy ) {
                if ( !matchesAccountRequest(planAcct, item) ) {
                    continue;
                }
                if ( ( attrName == null ) && ( Util.size(itemValues) == 0 ) ) {
                    // this is a simple account operation disable/unlock/enable ...
                    // remove it from the plan and break out
                    plan.remove(planAcct);
                    break;
                }
 
                // Dig through the AttributeRequests and remove the value(s)
                // that were rejected
                List<AttributeRequest> requests = planAcct.getAttributeRequests();
                if ( Util.size(requests) > 0 ) { 
                    List<AttributeRequest> attrReqsCopy = new ArrayList<AttributeRequest>();                
                    for ( AttributeRequest attrReq : requests ) {
                        if ( attrReq == null ) {
                            // shouldn't happen
                            continue;
                        }
                        Operation op = attrReq.getOperation();
                        String attrReqOp = (op != null) ? op.toString() : null;
                        // match on operation and attributeName
                        if ( ( Util.nullSafeEq(attrName, attrReq.getName()) ) &&
                             ( Util.nullSafeEq(item.getOperation(), attrReqOp) ) &&
                             ( Util.nullSafeEq(item.getAssignmentId(), attrReq.getAssignmentId(), true) ) ) {
                        
                            List vals = Util.asList(attrReq.getValue());
                            if ( Util.size(vals) > 0 ) {
                                for ( String toRemove : itemValues ) {
                                    vals.remove(toRemove);
                                }
                                if ( Util.size(vals) > 0 ) {
                                    // if there are still values after removing the 
                                    // item values set the vals 
                                    attrReq.setValue(vals);
                                    attrReqsCopy.add(attrReq);                            
                                }
                            }
                        } else {
                            attrReqsCopy.add(attrReq);
                        }
                    }

                    // set the new attribute list or remove the 
                    // request from the plan if there are no more 
                    // attribute requests
                    if ( Util.size(attrReqsCopy) > 0 ) {
                        planAcct.setAttributeRequests(attrReqsCopy);
                    } else  {
                        plan.remove(planAcct); 
                    }
                } 
            } 
        }
    }

    /**
     * Compare the values in the AccountRequest against the ApprovalItem content to see
     * if they are equal. This checks the account level detail specifically application
     * accountId and instance.
     */
    private boolean matchesAccountRequest(AccountRequest accountRequest, ApprovalItem item ) 
        throws GeneralException {

        if ( ( accountRequest == null ) || ( item == null ) ) return false;

        if ( Util.nullSafeEq(item.getApplication(), accountRequest.getApplication()) ) {
            if (!ProvisioningPlan.APP_IIQ.equals(accountRequest.getApplication())) {
                // first make sure this matches the account request we were handed
                String accountId = item.getNativeIdentity();            
                String instance = item.getInstance();
                if ( ( Util.nullSafeEq(accountId, accountRequest.getNativeIdentity(), true) ) &&
                     ( Util.nullSafeEq(instance, accountRequest.getInstance(), true) ) ) {
                    return true;
                } else if (Util.otob(item.getAttribute(ApprovalItem.ATT_UPDATED_NATIVE_ID))
                            && Util.isNullOrEmpty(accountRequest.getNativeIdentity()) && Util.isNotNullOrEmpty(accountId)) {
                    //Approval Item may have been updated with nativeId. IIQHH-575
                    return true;
                }
            } else {
                // requests for iiq application would not have instance and nativeidentity etc
                return true;
            }
        }
        return false;
    }

    /**
     * Compare the values in the AttributeRequest against the ApprovalItem content to see
     * if they are equal. This method checks the attribute, operation and values for
     * equality and returns true when all are equal.
     */
    private boolean matchesAttributeRequest(AttributeRequest attrReq, ApprovalItem item)
        throws GeneralException {

        if ( ( attrReq == null ) || ( item == null ) ) return false;

        String itemAttr = item.getName();
        String attrName = attrReq.getName();
        Operation operation = attrReq.getOperation();
        String op = ( operation != null ) ? operation.toString() : null;

        if ( ( Util.nullSafeEq(itemAttr, attrName) ) && 
             ( Util.nullSafeEq(op, item.getOperation()) ) &&
             ( Util.nullSafeEq(item.getAssignmentId(), attrReq.getAssignmentId(), true) ) ) {
            List attrReqVal = Util.asList(attrReq.getValue());
            List itemVal = Util.asList(item.getValue());
            if ( Util.orderInsensitiveEquals(attrReqVal, itemVal) ) {
                return true;
            }
        }
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // LCM Audit Events
    // 
    ///////////////////////////////////////////////////////////////////////////

    public Object auditLCMStart(WorkflowContext wfc ) 
        throws GeneralException {

        return auditLCMEvents("Start", wfc);
    }

    public Object auditLCMCompletion(WorkflowContext wfc ) 
        throws GeneralException {

        return auditLCMEvents(null, wfc);
    }

    /**
     * Log an Audit event for a decision made on an Approval item.
     * NOTE: this will decache and commit the SailPointContext contained in the WorkflowContext
     *
     * @param ctx WorkflowContext
     * @param item ApprovalItem being audited
     * @throws GeneralException
     */
    public static void auditDecision(WorkflowContext ctx, ApprovalItem item)
        throws GeneralException  {

        if ( item == null ) return;

        AuditEvent event = buildBaseEvent(ctx, item);
        event.setAttribute("operation", item.getOperation());
        event.setAttribute("requester", ctx.getString("launcher"));

        List<Comment> comments = item.getComments();
        if ( Util.size(comments) > 0 ) {
            event.setAttribute("completionComments", comments);
        }

        String requesterComments = item.getRequesterComments();
        if ( Util.getString(requesterComments) != null ) 
            event.setAttribute("requesterComments", requesterComments);
 
        // bug#8337 we're supposed to be auditing the name of the
        // user that actually made the decision and closed the work item
        // since the owner may be the name of a work group.  We no longer
        // have the WorkItem so we don't know who the owning Identity 
        // was, assume that we transitioned here within the same
        // workflow session so the current usre is still set.
        SailPointContext spcon = ctx.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        spcon.decache();

        String actor = spcon.getUserName();
        Identity ident = spcon.getObjectByName(Identity.class, actor);
        if (ident != null)
            actor = ident.getDisplayName();
        else {
            // not a real user, must be in the background
            // fall back to the ApprovalItem model
            actor = item.getOwner();
        }

        // if we couldn't find an actor, buildBaseEvent will
        // default it to the workflow launcher which is probably
        // wrong but better than nothing
        if (actor != null)
            event.setSource(actor);

        if ( item.isApproved() ) 
            event.setAction(AuditEvent.ActionApproveLineItem);
        else
            event.setAction(AuditEvent.ActionRejectLineItem);

        if ( Auditor.isEnabled(event.getAction()) ) {
            // !! This is using getCurrentContext but we're committing
            // a passed context.  In practice they will be the same
            // but it would be better if Auditor had some interfaces
            // that passed in the context.
            Auditor.log(event);
            spcon.commitTransaction();
        }
    }

    public Object auditLCMEvents(String eventType, WorkflowContext wfc ) 
        throws GeneralException {

        // bug#7698 do not trust the context when we need to commit
        wfc.getSailPointContext().decache();

        Attributes<String,Object> args = wfc.getArguments();

        // It's possible to end up with a null approval set if the
        // workflow is terminated before initialization is complete
        ApprovalSet set = (ApprovalSet) args.get(ARG_APPROVAL_SET);
        if ( set == null ) {
            return null;
        }

        String flow = args.getString(VAR_FLOW);
        List<ApprovalItem> items = set.getItems();
        if ( eventType == null ) {
            List<ApprovalItem> approvedItems = set.getApproved();
            // if the list of approved items is not empty, then audit them.
            // otherwise check the items for special cases.
            if (!Util.isEmpty(approvedItems)) {
                items = approvedItems;
            }
            // if the approved items are empty, check the original list
            // anyway because approvalScheme could be set to none
            else if (items != null) {
                Iterator<ApprovalItem> it = items.iterator();
                while (it.hasNext()) {
                    ApprovalItem item = it.next();
                    // isApproved() has special case checks including approvalScheme
                    if (!isApproved(wfc, item)) {
                        it.remove();
                    }
                }
            }
        }

        if ( Util.size(items) > 0 ) {
            for ( ApprovalItem item : items ) {
                String auditEventOperation = getAuditEventOperationFromApprovalItem(item, flow);
                //skip sunset/sunrise
                //These will be audited when real add/remove happens. 
                if (!"Start".equals(eventType) && (item.getStartDate() != null || item.getEndDate() != null)) {
                    continue;
                }
                
                String op = item.getOperation();
                String attrName = item.getName();
                AuditEvent event = buildBaseEvent(wfc, item);
                if (item.getName() != null && ProvisioningPlan.ATT_PASSWORD.compareTo(item.getName()) == 0 ) {
                    event.setAttributeValue(null);
                }
                event.setAction(auditEventOperation);
                if ( "Start".equals(eventType) ) {
                    event.setAction("Start");
                    if ( flow != null ) 
                        event.setAction(flow+"Start");
                    event.setAttribute("operation", auditEventOperation);
                    if (item.getStartDate() != null) {
                        event.setAttribute("startDate", item.getStartDate());
                    }
                    if (item.getEndDate() != null) {
                        event.setAttribute("endDate", item.getEndDate());
                    }
                }
                if ( Auditor.isEnabled(event.getAction()) ) {
                    Auditor.log(event);
                    wfc.getSailPointContext().commitTransaction();
                }
            }
        } 
        return null;
    }

    /**
     * Factored out so that can also be called from RecommenderUtil
     * @param item the approvalItem to look into
     * @param flow the source workflow
     * @return the associated AuditEvent operation based on the given ApprovalItem
     */
    public static String getAuditEventOperationFromApprovalItem(ApprovalItem item, String flow) {
        String auditEventOp = item.getOperation();
        String attrName = item.getName();
        if ( attrName != null ) {
            if ( ProvisioningPlan.APP_IIQ.equals(item.getApplication()) ) {
                if ( ( ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrName) ) ||
                        ( ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrName) ) ) {
                    auditEventOp = AuditEvent.RoleAdd;
                    if ( ProvisioningPlan.Operation.Remove.toString().equals(item.getOperation()) )
                        auditEventOp = AuditEvent.RoleRemove;
                }
            } else {
                if ( ProvisioningPlan.ATT_PASSWORD.compareTo(attrName) == 0 ) {
                    auditEventOp = AuditEvent.PasswordChange;
                    if( IdentityRequest.EXPIRED_PASSWORD_FLOW.equals( flow ) ) {
                        auditEventOp = AuditEvent.ExpiredPasswordChange;
                    } else if( IdentityRequest.FORGOT_PASSWORD_FLOW.equals( flow ) ) {
                        auditEventOp = AuditEvent.ForgotPasswordChange;
                    }
                } else {
                    auditEventOp = AuditEvent.EntitlementAdd;
                    if ( ProvisioningPlan.Operation.Remove.toString().equals(item.getOperation()) )
                        auditEventOp = AuditEvent.EntitlementRemove;
                }
            }
        }
        return auditEventOp;
    }

    public static AuditEvent buildBaseEvent(WorkflowContext wfc, 
                                           ApprovalItem item) {

        Attributes<String,Object> args = wfc.getArguments();
        if ( args == null ) args = new Attributes<String,Object>();
        AuditEvent event = new AuditEvent();

        event.setSource(args.getString(Workflow.VAR_LAUNCHER));
        event.setTarget(args.getString(VAR_IDENTITY_NAME));

        event.setApplication(item.getApplication());

        if (item.getNativeIdentity() == null) {
            ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
            if (project != null) {
                event.setAccountName(project.getIdentity());
            }
        }
        else {
            event.setAccountName(item.getNativeIdentity());
        }
        
        event.setInstance(item.getInstance());
        event.setAction(item.getOperation());
        event.setAttributeName(item.getName());
        event.setAttributeValue(item.getCsv());
     
        // Copy the attributes from the approval item onto the audit event.
        event.setAttributes(item.getAttributes());
        /* Replace encrypted password in AuditEvent with stars */
        if(event.getAttributes() != null ) {
            for(String key: event.getAttributes().keySet()) {
                if(key.startsWith("password:")) {
                    event.getAttributes().put("password:********", event.getAttribute(key));
                    event.getAttributes().remove(key);
                    break;
                }
            }
        }

        // group them by a generatedId
        event.setTrackingId(wfc.getWorkflow().getProcessLogId());
        String interfaceName = wfc.getString("interface");
        if ( interfaceName == null)
            interfaceName = Source.LCM.toString();

        // djs: for now set this in both places to avoid needing
        // to upgrade.  Once we have ui support for "interface"
        // we can remove the map version
        event.setAttribute("interface", interfaceName);
        event.setInterface(interfaceName);
        
        /* Passwords do not have approvals so we are posting the requester 
         * comments into the completionComments attribute */
        if( isPasswordItem( item ) ) {
            String requesterComment = item.getRequesterComments();
            if( requesterComment != null ) {
                List<Comment> comments = ( List<Comment> ) event.getAttribute( "completionComments" );
                if( comments == null ) {
                    comments = new ArrayList<Comment>();
                    event.setAttribute( "completionComments", comments );
                }
                
                Comment comment = new Comment();
                comment.setComment( requesterComment );
                comment.setAuthor( item.getOwner() );
                comment.setDate( new Date() );
    
                comments.add( comment );
            }
        }

        String taskResultId = wfc.getString(Workflow.VAR_TASK_RESULT);
        if ( taskResultId != null ) {
            event.setAttribute(Workflow.VAR_TASK_RESULT, taskResultId);
        }

        String flow = args.getString("flow");
        if ( flow != null ) 
            event.setAttribute("flow", flow);
        return event;
    }

    private static boolean isPasswordItem( ApprovalItem item ) {
        if( item.getName() != null ) {
            return item.getName().equals( "password" );
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Account Management
    //
    // These are used by the identity trigger workflows to enable or
    // disable managed accounts in response to some stimulus.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Argument that may be passed to the bulk account operation
     * methods to force locking of the Identity.  Normally locking
     * is not done because these are almost always called within 
     * aggregation or refresh tasks and the identity is already locked.
     */
    public static final String ARG_DO_LOCKING = "doLocking";

    /**
     * Disable all accounts on the identity specified in the workflow.
     */
    public void disableAllAccounts(WorkflowContext wfc)
        throws GeneralException {

        provisionAllAccounts(wfc, AccountRequest.Operation.Disable);
    }
    
    /**
     * Enable all accounts on the identity specified in the workflow.
     */
    public void enableAllAccounts(WorkflowContext wfc)
        throws GeneralException {
        
        provisionAllAccounts(wfc, AccountRequest.Operation.Enable);
    }

    /**
     * Delete all accounts on the identity specified in the workflow.
     */
    public void deleteAllAccounts(WorkflowContext wfc)
        throws GeneralException {
        
        provisionAllAccounts(wfc, AccountRequest.Operation.Delete);
    }

    /**
     * Execute the given account-level operation on all accounts on the identity
     * specified in the workflow.
     */
    public void provisionAllAccounts(WorkflowContext wfc, AccountRequest.Operation op)
        throws GeneralException {

        Identity identity = getIdentity(wfc);
        if (null != identity) {
            List<Link> links = identity.getLinks();
            if ((null != links) && !links.isEmpty()) {
                ProvisioningPlan plan = new ProvisioningPlan();
                plan.setIdentity(identity);

                for (Link link : links) {
                    AccountRequest acctReq = new AccountRequest();
                    acctReq.setApplication(link.getApplicationName());
                    acctReq.setInstance(link.getInstance());
                    acctReq.setNativeIdentity(link.getNativeIdentity());
                    acctReq.setOperation(op);
                    plan.add(acctReq);
                }

                Provisioner provisioner =
                    new Provisioner(wfc.getSailPointContext());

                // bug#10468 - locking is normally off since we are
                // almost always called from an identity trigger workflow
                // and making everyone remember to pass in a "noLocking"
                // flag is error prone.  Instead, no locking is assumed
                // and you have to pass in doLocking.
                if (!wfc.getBoolean(ARG_DO_LOCKING))
                    provisioner.setNoLocking(true);

                provisioner.processWithoutFiltering(plan);
            }
        }
    }
    
    /**
     * 
     * A method called by some of our default Identity Lifecycle
     * events where we want to create a plan that enabled/disables
     * all of an Identities accounts.
     * 
     * It takes requires both an arg that specifies identityName
     * and the operation (string version of 
     * AccountRequest.operation) that should be performed.
     * 
     * This method goes through all of the user's 
     * links and generates AccountRequests.
     *  
     * @param wfc WorkflowContext
     * @return ProvisioningPlan
     * @throws GeneralException
     */
    public ProvisioningPlan buildEventPlan(WorkflowContext wfc) 
        throws GeneralException {

        Attributes args = wfc.getArguments();
        sailpoint.object.ProvisioningPlan.AccountRequest.Operation operation = null;
        String op = Util.getString(args, "op");
        if ( op != null ) {
            operation = AccountRequest.Operation.valueOf(op);
        }
        
        if ( operation == null )
            throw new GeneralException("Operation (op) must be specified.");
        
        ProvisioningPlan plan = new ProvisioningPlan();
        Identity identity = getIdentity(wfc);
        if (null != identity) {
            List<Link> links = identity.getLinks();
            if ((null != links) && !links.isEmpty()) {
                plan = new ProvisioningPlan();
                plan.setIdentity(identity);

                for (Link link : links) {
                    AccountRequest acctReq = new AccountRequest();
                    acctReq.setApplication(link.getApplicationName());
                    acctReq.setInstance(link.getInstance());
                    acctReq.setNativeIdentity(link.getNativeIdentity());
                    acctReq.setOperation(operation);
                    plan.add(acctReq);
                }
            }
        }
        return plan;
    }

    public ProvisioningPlan buildAlertPlan(WorkflowContext wfc)
        throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();
        Attributes args = wfc.getArguments();
        sailpoint.object.ProvisioningPlan.AccountRequest.Operation operation = null;
        String op = Util.getString(args, "op");
        if ( op != null ) {
            operation = AccountRequest.Operation.valueOf(op);
        }

        if ( operation == null )
            throw new GeneralException("Operation (op) must be specified.");

        String alertId = args.getString(AlertWorkflowHandler.ARG_ALERT_ID);
        Alert a = null;
        if (Util.isNotNullOrEmpty(alertId)) {
            a = context.getObjectById(Alert.class, alertId);
        }

        if (a == null) {
            throw new GeneralException("No Alert found for id["+alertId+"]");
        }

        ProvisioningPlan plan = new ProvisioningPlan();
        if (Identity.class.getSimpleName().equals(a.getTargetType())) {
            //Set Identity inactive.
            Identity ident = context.getObjectById(Identity.class, a.getTargetId());
            if (ident != null) {
                plan.setIdentity(ident);

                if (operation == AccountRequest.Operation.Disable) {
                    AccountRequest accntReq = new AccountRequest();
                    accntReq.setApplication(ProvisioningPlan.APP_IIQ);
                    accntReq.setOperation(AccountRequest.Operation.Modify);
                    AttributeRequest attReq = new AttributeRequest(Identity.ATT_INACTIVE, Operation.Set, true);

                    accntReq.add(attReq);

                    plan.add(accntReq);
                }

            } else {
                throw new GeneralException("Could not find identity associated to alert");
            }

        } else if (Link.class.getSimpleName().equals(a.getTargetType())) {
            //Disable the account associated to the link
            Link link = context.getObjectById(Link.class, a.getTargetId());
            if (link != null) {

                plan.setIdentity(link.getIdentity());

                AccountRequest acctReq = new AccountRequest();
                acctReq.setApplication(link.getApplicationName());
                acctReq.setInstance(link.getInstance());
                acctReq.setNativeIdentity(link.getNativeIdentity());
                acctReq.setOperation(operation);
                plan.add(acctReq);

            } else {
                throw new GeneralException("Could not find Link associated to alert");
            }


        } else {
            throw new GeneralException("TargetType must be "+ Identity.class.getSimpleName() + " or " + Link.class.getSimpleName());
        }

        return plan;

    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Password History
    //
    // Add password to link password history
    //
    //////////////////////////////////////////////////////////////////////

    public void updatePasswordHistory(WorkflowContext wfc) 
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();

        Object o = args.get(VAR_PLAN);
        if (o instanceof ProvisioningPlan) {
            ProvisioningPlan plan = (ProvisioningPlan)o;
            List<AccountRequest> requests = plan.getAccountRequests();
            if (requests.size() > 0) {

                SailPointContext spcon = wfc.getSailPointContext();
                // bug#7698 do not trust the context when we need to commit
                spcon.decache();

                for (AccountRequest request : requests) {
                    if (request != null) {
                        Identity identity = plan.getIdentity();
                        Link link = identity.getLink(request.getApplication(spcon), 
                                                     request.getInstance(), 
                                                     request.getNativeIdentity());
                        AttributeRequest attReq = request.getAttributeRequest(ProvisioningPlan.ATT_PASSWORD);

                        if (attReq != null && link != null) {

                            String passVal = (String) attReq.getValue();
                            PasswordPolice police = new PasswordPolice(spcon);
                            police.addPasswordHistory(link, passVal);
                            spcon.saveObject(link);
                            spcon.commitTransaction();
                        }
                    }
                }
            }
        }
        else {
            log.error("Invalid object passed as plan argument");
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Provisioning Retries
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Variable that holds the total number of retries that should be 
     * attempted.
     */   
    public static final String ATT_MAX_RETRIES = "provisioningMaxRetries";

    /**
     * Variable that holds the maximum number of minutes to wait in
     * between retry attempts.
     */ 
    public static final String ATT_RETRY_THRESHOLD = "provisioningRetryThreshold";
    
    /**
     * 
     * Go through the provisioning plan and look for any plans
     * that are retryable and add them to a returned 
     * "retry" provisioning plan.
     * 
     * The returned ProvisioningProject will contain only the account requests 
     * from the original plan that have to be retried.  
     * 
     * If the returned project is null, there are no items that can be 
     * retried.
     * 
     * @param wfc WorkflowContext
     * @return ProvisioningProject for retry
     */
    public ProvisioningProject assembleRetryProject(WorkflowContext wfc ) {
        ProvisioningProject retryProject = null;
        
        Attributes<String,Object> args = wfc.getStepArguments();        
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
        if ( project != null ) {
            
            List<ProvisioningPlan> retryPlans = new ArrayList<ProvisioningPlan>();
            List<ProvisioningPlan> plans = project.getPlans();
            if ( Util.size(plans) > 0 ) {
                for ( ProvisioningPlan plan : plans )  {
                    if ( plan.needsRetry() ) {                                                
                        // make a retry plan
                        ProvisioningPlan retryPlan = PlanEvaluator.buildRetryPlan(plan);
                        if ( !retryPlan.isEmpty()  ) {
                            clearRetryResults(retryPlan);
                            retryPlans.add(retryPlan);
                        }                        
                    }                    
                }
                // build a project based on the plans
                if ( Util.size(retryPlans) > 0 ) {
                    retryProject = new ProvisioningProject();
                    retryProject.setIdentity(project.getIdentity());
                    retryProject.setAttributes(project.getAttributes());
                    retryProject.setPlans(retryPlans);                    
                }
            }
        }
        return retryProject;
    }
    
    /**
     * Clear previous retries indicated at any level of 
     * the plan.  
     * 
     * This is necessary to help in situations where the
     * connector is not consistent in the way it handles
     * its results.  In some cases connectors are throwing
     * exceptions or adding results inconsistently at the 
     * plan/account level.
     * 
     * @param plan ProvisioningPlan
     *      
     * @ignore
     * BUG#11629
     */
    private void clearRetryResults(ProvisioningPlan plan) {        
        ProvisioningResult result = null;        
        List<AccountRequest> accts = plan.getAccountRequests();
        if ( accts != null ) {
            for ( AccountRequest acct : accts) {
                if ( acct != null) {                   
                    result = acct.getResult();                    
                    clearRetry(result);
                    List<AttributeRequest> attrs = acct.getAttributeRequests();
                    if ( attrs != null ) {
                        for (  AttributeRequest attr : attrs ) {
                            result = attr.getResult();
                            clearRetry(result);
                        }
                    }
                }
            }        
        }    
        result = plan.getResult();
        clearRetry(result);        
    }
    
    /**
     * If it's a retry result null the status.
     * 
     * @param result ProvisioningResult
     */
    private void clearRetry(ProvisioningResult result) {        
        if (result != null && result.isRetry() ) {
            result.setStatus(null);
        }
    }
    
    /**
     * 
     * Retry a project and for plan plan call a special retry method on 
     * the plan evaluator. 
     * 
     * @param wfc WorkflowContext
     * @return ProvisioningProject, possibly retried
     * @throws GeneralException
     */
    public Object retryProvisionProject(WorkflowContext wfc)    
        throws GeneralException {
        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
        if ( project != null ) {
            SailPointContext ctx = wfc.getSailPointContext();
            Identity identity = project.resolveIdentity(ctx);

            PlanEvaluator pe = new PlanEvaluator(ctx);
            List<ProvisioningPlan> plans = project.getPlans();
            if ( plans != null ) {
                for ( ProvisioningPlan plan : plans ) {
                    String configName = plan.getTargetIntegration();
                    if ( configName != null ) {

                        // Check if plan contains unstructured target collector name. If plan
                        // contains target collector name, then get target collector application name.
                        // Going ahead target collector application name is used to generate the
                        // IntegrationConfig for target collector. If target collector application name
                        // is null then IntegrationConfig for application is generated.
                        // Starting release 6.4, IdentityIQ supports provisioning through unstructured
                        // target collector.
                        String targetCollectorAppName = plan.getAppForTargetCollector();

                        IntegrationConfig config = pe.getIntegration(configName,
                                                                     targetCollectorAppName);
                        if ( config != null ) {
                            ProvisioningProject configProject = new ProvisioningProject();
                            configProject.setMasterPlan(plan);
                            pe.retry(identity, config, configProject);
                        }
                    }
                }
            }
        }
        return project;
    }

    /**
     * Merge the results from the retry project onto the main project.
     * This is called during workflow execution in between retries
     * to merge the results.
     */
    public Object mergeRetryProjectResults(WorkflowContext wfc) 
        throws GeneralException {
        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
        ProvisioningProject retry = (ProvisioningProject)Util.get(args, "retryProject");

        if ( project != null ) {
            List<ProvisioningPlan> retryPlans = retry.getPlans();
            if ( retryPlans != null ) {
                /*
                 * IIQBUGS-84 : Changing approach to find the provisioning plan,
                 * we should not use project.getPlan(retryPlan.getTargetIntegration()); ,
                 * turn out that getPlan method contains a HashMap that will store provisioning
                 * plans using the same key for all the plans. Perhaps the best fix should change the
                 * method project.getPlan(), but according to my research it has many dependencies and
                 * it is too risky to change the approach.
                 */
                Map<String,ProvisioningPlan> mapPlans = null;
                for ( ProvisioningPlan retryPlan : retryPlans ) {                    
                    ProvisioningPlan orig = getPlan(mapPlans, project.getPlans(), retryPlan.getApplicationNames());
                    if ( orig != null ) {                        
                        updateRetryStatus(orig, retryPlan);
                    }
                }
            }            
        } 
        
        //
        // copy over any errors found during retry to the task result
        //
        assimilateProvisioningErrors(wfc, project);
        
        return project;        
    }

    /**
     * IIQBUGS-84 : Helper method that will be called from mergeRetryProjectResults,
     * it will find the correct provisioning plan to perform a correct update.
     * @param mapPlans Temporal HashMap used to avoid multiple loops
     * @param plans The current provisioning plans in the project
     * @param appNames Applications list names that belong to each plan
     * @return ProvisioningPlan The correct provisioning plan that we are looking for.
     * @throws GeneralException
     */
    private ProvisioningPlan getPlan(Map<String, ProvisioningPlan> mapPlans, List<ProvisioningPlan> plans,
            List<String> appNames) throws GeneralException {
        if (mapPlans == null) {
            mapPlans = new HashMap<String, ProvisioningPlan>();
            for (ProvisioningPlan plan : plans) {
                if (plan.getApplicationNames().size() > 0) {
                    mapPlans.put(plan.getApplicationNames().get(0), plan);
                }
            }
        }
        if (appNames.size() > 0 ){
            return mapPlans.get(appNames.get(0));
        } else {
            return null;
        }
    }

    /**
     * 
     * When we've tried the max number of times to retry the result
     * force an failure status for each of the "pending retry" items in
     * the retry plan.
     * 
     * @param wfc WorkflowContext
     * @return ProvisioningProject with failed retries
     * @throws GeneralException
     */
    public Object forceRetryTimeoutFailure(WorkflowContext wfc) 
            throws GeneralException {
            
        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
        ProvisioningProject retryProject = (ProvisioningProject)Util.get(args, "retryProject");


        if ( retryProject != null && project != null) {
            List<ProvisioningPlan> retryPlans = retryProject.getPlans();
            for ( ProvisioningPlan retryPlan : Util.iterate(retryPlans) ) {
                failRetryParts(retryPlan, project);

                if (!ProvisioningPlan.APP_IIQ.equals(retryPlan.getTargetIntegration())) {
                    // mark any transaction objects referenced in the plan as timed out for non IIQ targets
                    updateProvisioningTransactions(wfc, project, retryPlan);
                }
            }
            //
            // copy over any errors found during to the task result
            //
            assimilateProvisioningErrors(wfc, project);
        } 

        return project;        
    }

    /**
     * Marks any provisioning transaction objects referenced in the plan as timed out.
     *
     * @param wfc The workflow context.
     * @param project The project. Used to get the source and identity name if we need to create a new transaction.
     * @param plan The plan.
     */
    private void updateProvisioningTransactions(WorkflowContext wfc, ProvisioningProject project, ProvisioningPlan plan) {
        String source = project.getString(ProvisioningPlan.ARG_SOURCE);

        for (AbstractRequest request : Util.iterate(plan.getAllRequests())) {
            try {
                TransactionDetails details = new TransactionDetails();
                details.setPartitionedPlan(plan);
                details.setRequest(request);
                details.setProject(project);
                details.setSource(source);

                ProvisioningTransactionService transactionService = new ProvisioningTransactionService(wfc.getSailPointContext());
                transactionService.timeOutTransaction(details);
            } catch (GeneralException ex) {
                log.error("An error occurred while trying to update provisioning transaction", ex);
            }
        }
    }

    private static final ProvisioningResult getTimeoutResult() {
        ProvisioningResult retryTimeoutResult = new ProvisioningResult();
        retryTimeoutResult.setStatus(ProvisioningResult.STATUS_FAILED);
        retryTimeoutResult.addError(Message.error("Item failed due to retry timeout."));
        return retryTimeoutResult;
    }
    
    /**
     * Iterate through the plan looking for things marked retry,
     * anything found should be marked failed as we are giving
     * up on retrying.
     * 
     */
    private void failRetryParts(ProvisioningPlan retryPlan, ProvisioningProject project) throws GeneralException {
        
        if ( retryPlan == null )
            return;

 
        List<ProvisioningPlan> originalPlans = project.getPlans();
        ProvisioningPlan oGPlan = matchPlan(originalPlans, retryPlan);

        if (oGPlan != null) {
            //
            // Account Request
            //
            List<AccountRequest> retryAcctReqs = ( retryPlan != null ) ?  retryPlan.getAccountRequests() : null;
            for ( AccountRequest retryReq : Util.safeIterable(retryAcctReqs) ) {
                AccountRequest originalAccntReq = oGPlan.getAccountRequest(retryReq.getApplication(),
                        retryReq.getInstance(), retryReq.getNativeIdentity());
                if (originalAccntReq != null) {
                    ProvisioningResult origAccntRes = originalAccntReq.getResult() != null ? originalAccntReq.getResult() : null;
                    ProvisioningResult retryAccntRes = retryReq.getResult() != null ? retryReq.getResult() : null;
                    //Something in this AccntReq has failed, fail the AccntRequest
                    ProvisioningResult acctRetryTimeoutRes = getTimeoutResult();
                    //Assimilate original messages
                    acctRetryTimeoutRes.assimilateMessages(origAccntRes);
                    //Assimilate any retry messages.
                    acctRetryTimeoutRes.assimilateMessages(retryAccntRes);
                    originalAccntReq.setResult(acctRetryTimeoutRes);

                    //
                    // Attribute Requests
                    //
                    List<AttributeRequest> retryAttrReqs = retryReq.getAttributeRequests();
                    for ( AttributeRequest retryAttrReq : Util.iterate(retryAttrReqs) ) {
                        AttributeRequest originalAttReq = originalAccntReq.getAttributeRequest(retryAttrReq.getName(),
                                retryAttrReq.getValue());
                        if (originalAttReq != null) {
                            ProvisioningResult origAttRes = originalAttReq.getResult() != null ? originalAttReq.getResult() : null;
                            ProvisioningResult retryTimeoutResult = getTimeoutResult();
                            retryTimeoutResult.assimilateMessages(origAttRes);
                            retryTimeoutResult.assimilateMessages(retryAttrReq.getResult());
                            originalAttReq.setResult(retryTimeoutResult);
                        }
                    }
                }
            }

            //
            // Plan level
            //
            ProvisioningResult retryTimeoutResult = getTimeoutResult();
            retryTimeoutResult.assimilateMessages(oGPlan.getResult());
            retryTimeoutResult.assimilateMessages(retryPlan.getResult());
            oGPlan.setResult(retryTimeoutResult);
        }
    }

    private ProvisioningPlan matchPlan(List<ProvisioningPlan> plans, ProvisioningPlan toMatch ) {
        if ( plans == null || toMatch == null) {
            return null;
        }
        for (ProvisioningPlan plan : plans ) {
             if ( plan != null ) {
                 List<String> appNames = plan.getApplicationNames();
                 List<String> matchAppNames = toMatch.getApplicationNames();
                 if ( Util.nullSafeEq(matchAppNames, appNames) ) {
                     return plan;
                 }
             }
        }
        return null;        
    }
    
    private boolean inRetry(AccountRequest retryAccount, AttributeRequest req) {
        
        if ( retryAccount != null  && req != null ) {
            AttributeRequest retryAttr = retryAccount.getAttributeRequest(req.getName());
            if ( retryAttr != null ) {
                ProvisioningResult retryResult = retryAttr.getResult();
                if ( retryResult != null && retryResult.isRetry() ) {
                     return true;       
                }
            }
        }
        return false;
    }
    
    /**
     * Dig into the retryPlan and adorn any new results that
     * were added to any level of the plan to the original
     * plan.
     * 
     * Allow a forced result for cases where we've timeout
     * due to retry or queue checking.  If forced result is 
     * non-null it takes precedence over the result on the 
     * retry results.
     */
    private void updateRetryStatus(ProvisioningPlan plan, ProvisioningPlan retryPlan) {

        List<AccountRequest> retryAcctReqs = null;        
        if ( retryPlan != null )
            retryAcctReqs = retryPlan.getAccountRequests();

        if ( retryAcctReqs != null ) {

            for ( AccountRequest retryAcctReq : retryAcctReqs ) {                
                if ( retryAcctReq == null )
                    continue;                
                
                AccountRequest planReq =
                    plan.getAccountRequest(retryAcctReq.getApplication(),
                                           retryAcctReq.getInstance(),
                                           retryAcctReq.getNativeIdentity());    
                if ( planReq == null ) {
                    // likely an account ID change
                    List<AccountRequest> planReqs = plan.getAccountRequests(retryAcctReq.getApplication());
                    if ( planReqs != null )  {
                        for ( AccountRequest req : planReqs ) {
                            if ( req.getOp().compareTo(ObjectOperation.Create) == 0 ) {
                                // djs: is this enough?
                                planReq = req;
                                continue;
                            }
                        }
                    }
                }
                
                //
                // Attribute Requests
                //
                List<AttributeRequest> retryAttrReqs = retryAcctReq.getAttributeRequests();
                if ( retryAttrReqs != null && planReq != null ) {
                    for ( AttributeRequest retryAttrReq : retryAttrReqs ) {
                        ProvisioningResult result = retryAttrReq.getResult();
                        if ( result != null ) {                              
                            AttributeRequest planAttrReq = getMatchingAttributeRequest(planReq, retryAttrReq);
                            if ( planAttrReq != null ) {
                                ProvisioningResult retryResult = retryAttrReq.getResult();
                                if ( retryResult != null ) {
                                    ProvisioningResult planAttrResult = planAttrReq.getResult();
                                    if ( planAttrResult == null )
                                        planAttrReq.setResult(retryResult);
                                    else 
                                        assimilateRetryResult(retryResult, planAttrResult);
                                }             
                            }
                        }                        
                    }    
                }

                //
                // Account Request
                //
                ProvisioningResult retryAcctResult = retryAcctReq.getResult();
                if ( retryAcctResult != null && planReq != null ) {      
                    ProvisioningResult planReqResult = (planReq != null ) ? planReq.getResult() : null;
                    if ( planReqResult == null )
                        planReq.setResult(retryAcctResult);
                    else 
                        assimilateRetryResult(retryAcctResult, planReqResult);
                }

            }               
            //
            //  Plan
            //
            ProvisioningResult retryPlanResult = retryPlan.getResult();
            if ( retryPlanResult != null ) {
               ProvisioningResult planResult = plan.getResult();    
               if ( planResult == null )
                  plan.setResult(retryPlanResult);
               else 
                  assimilateRetryResult(retryPlanResult, planResult);
            }
        }
    }
    
    /**
     * Dig through the account request looking for a matching attribute request.
     * 
     * There are cases where there will be more then one attribute request per
     * attribute name, so we must iterate through all of them and then
     * also match on operation and value.
     * 
     * @return Matching AttributeRequest or null if not found
     */
    private AttributeRequest getMatchingAttributeRequest(AccountRequest req, AttributeRequest attr) {
        AttributeRequest match = null;
        List<AttributeRequest> requests = req.getAttributeRequests(attr.getName());
        if ( requests != null ) {
            for ( AttributeRequest attrReq : requests ) {
                Object value = attrReq.getValue();
                Operation op = attrReq.getOperation();
                
                if ( Util.nullSafeEq(value, attr.getValue()) && Util.nullSafeEq(op, attr.getOperation())) {
                    match = attrReq;
                    break;
                }
            }
        }
        return match;
    }

    private ProvisioningResult assimilateRetryResult(ProvisioningResult retryResult, ProvisioningResult origResult) {        
        if ( origResult != null ) {
            origResult.setStatus(retryResult.getStatus());
            origResult.setRequestID(retryResult.getRequestID());
            List<Message> msgs = retryResult.getErrors();
            if ( msgs != null ) {
                for ( Message msg : msgs ) {
                    origResult.addError(msg);
                }
            }
        }
        return origResult;                        
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Status Check
    //
    ///////////////////////////////////////////////////////////////////////////    
    
    /**
     * Attribute name holding the configuration that controls how
     * frequently in seconds to call checkStatus.
     */ 
    public static final String ATT_PROVISIONING_STATUS_CHECK_INTERVAL = "provisioningStatusCheckInterval";

    /**
     * Attribute name holding the configuration that controls the
     * maximum number of times to recheck status.
     */ 
    public static final String ATT_PROVISIONING_MAX_STATUS_CHECKS = "provisioningMaxStatusChecks";
    
    /**
     * Dig into the plan and look for Results that are queued AND
     * have a requestID stored on the result. Return true if any 
     * are found.
     * 
     * If there are cases where the request is queued with a result
     * id AND the checkStatus method is not implemented. ( and
     * throws OperationNotSupportedException ) then the
     * result status will be changed to committed.
     * 
     * NOTES:
     * 
     * The Results can be placed at at any level of the project:
     * 
     * plan 
     *    The result here applies to all account/attribute requests
     *    that don't have results.
     *    
     * accountRequest
     *    The results at this level "over-ride" the plan result.
     *    
     * attributeRequest 
     *    The results stored at this level will over-ride any values
     *    from the accountRequest OR plan.
     * 
     */
    public Boolean requiresStatusCheck(WorkflowContext wfc) 
        throws Exception {
        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
        if ( project != null ) {
            List<ProvisioningPlan> plans = project.getPlans();   
            if ( plans != null ) {
                for ( ProvisioningPlan plan : plans ) {
                    ProvisioningResult planResult = plan.getResult();
                    if ( isQueuedWithId(planResult) ) {
                        return true;
                    }
                    List<AccountRequest> acctReqs = plan.getAccountRequests();
                    if ( acctReqs != null ) {
                        for ( AccountRequest acctReq : acctReqs ) {
                            String appName = acctReq.getApplication();
                            // The IIQ app does not suppoort check status, all
                            // of its operations are synchronous
                            if ( Util.nullSafeCompareTo(appName, ProvisioningPlan.APP_IIQ) != 0 ) {                                
                                ProvisioningResult result = acctReq.getResult();
                                if ( isQueuedWithId(result) ) 
                                    return true;
                                List<AttributeRequest> attrReqs = acctReq.getAttributeRequests();
                                if ( attrReqs != null ) {
                                    for ( AttributeRequest req : attrReqs ) {
                                        ProvisioningResult attResult = req.getResult();
                                        if  ( isQueuedWithId(attResult) )
                                            return true;
                                    }
                                }
                            }
                        }
                    }                 
                }   
            }            
        }
        return false;
    }
    
    /**
     * Call down to the Connector for each Result in the plan
     * that is marked queued and has a request ID specified.
     * 
     * @param wfc WorkflowContext
     * @return ProvisioningProject project the project with merged results
     * @throws Exception
     */
    public Object checkProvisioningStatus(WorkflowContext wfc) 
        throws Exception {
        
        Attributes<String,Object> args = wfc.getArguments();
        SailPointContext ctx = wfc.getSailPointContext();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);

        if ( project != null ) {            
            List<ProvisioningPlan> plans = project.getPlans();   
            if ( plans != null ) {
                for ( ProvisioningPlan plan : plans ) {
                    if ( plan == null ) {
                       // this would be odd but guard against it
                       continue;
                    }
                    String targetIntegration = plan.getTargetIntegration();
                    if ( targetIntegration == null ) {
                        log.warn("Target Integration was null while checking provisioing status" + plan);
                        continue;
                    }

                    // Check if plan contains unstructured target collector name. If plan
                    // contains target collector name, then get target collector application name.
                    // Going ahead target collector application name is used to generate the
                    // IntegrationConfig for target collector. If target collector application name
                    // is null then IntegrationConfig for application is generated.
                    // Starting release 6.4, IdentityIQ supports provisioning through unstructured
                    // target collector.
                    String targetCollectorAppName = plan.getAppForTargetCollector();

                    List<AccountRequest> acctReqs = plan.getAccountRequests();
                    if ( acctReqs != null ) {
                        for ( AccountRequest acctReq : acctReqs ) {
                            ProvisioningResult acctResult = acctReq.getResult();
                            if ( isQueuedWithId(acctResult) ) {
                                checkStatus(ctx, acctResult, targetIntegration, targetCollectorAppName);
                                acctReq.setResult(acctResult);
                            }
                            List<AttributeRequest> attrReqs = acctReq.getAttributeRequests();
                            if ( attrReqs != null ) {
                                for ( AttributeRequest attrReq : attrReqs ) {
                                    ProvisioningResult attResult = attrReq.getResult();
                                    if  ( isQueuedWithId(attResult) ) {
                                        checkStatus(ctx, attResult, targetIntegration, targetCollectorAppName);
                                        attrReq.setResult(attResult);
                                    }                                        
                                }
                            }
                            //6.4 onward, revoke permission is supported
                            List<PermissionRequest> permReqs = acctReq.getPermissionRequests();
                            if ( permReqs != null ) {
                                for ( PermissionRequest permReq : permReqs ) {
                                    ProvisioningResult permResult = permReq.getResult();
                                    if  ( isQueuedWithId(permResult) ) {
                                        checkStatus(ctx, permResult, targetIntegration, targetCollectorAppName);
                                        permReq.setResult(permResult);
                                    }
                                }
                            }
                        }
                    } 
                    
                    //
                    // Lastly process the results from the plan
                    //                    
                    ProvisioningResult result = plan.getResult();
                    if ( isQueuedWithId(result) ) {
                        checkStatus(ctx, result, targetIntegration, targetCollectorAppName);
                    }
                }
            }
        }
        return project;
    }
    
    /**
     * Return true if the result is marked Queued AND
     * has a requestID specified on the provisioning
     * result.
     */
    private boolean isQueuedWithId(ProvisioningResult result) {
        if ( result != null ) {
            String requestId = result.getRequestID();
            if ( ( result.isQueued() ) && ( requestId != null ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * 
     * Check the status of any queued items that were returned with
     * a requestID and Commited.
     */
    private void checkStatus(SailPointContext ctx, 
                             ProvisioningResult result, 
                             String integration,
                             String targetCollectorAppName)
        throws Exception {
        
        if ( result != null ) {
            String reqId = result.getRequestID();
            if ( reqId != null ) {
                PlanEvaluator pe = new PlanEvaluator(ctx);
                IntegrationConfig config = pe.getIntegration(integration,
                                                             targetCollectorAppName);
                if ( config != null )
                    pe.checkStatus(config, result);
                else
                    log.error("IntegrationConfig not resolvable for ["+integration+"]");
            }
        }        
    }
    
    /**
     * 
     * Compute the status check interval for a request.   This value
     * comes from either the application, the application's proxy,
     * or as a workflow variable named 'provisioningStatusCheckInterval'.
     * 
     * If not found on the apps or any workflows the default value will 
     * be 60 minutes.
     * 
     * @param wfc WorkflowContext
     * @return Integer interval  
     * @throws GeneralException
     */
    public Integer getProvisioningStatusCheckInterval(WorkflowContext wfc)
        throws GeneralException {
        
        return getIntegerConfig(wfc, ATT_PROVISIONING_STATUS_CHECK_INTERVAL, 60);
    }
    
    /**
     * 
     * Compute the maximum status checks allowed during a request.   This value
     * comes from either the application, the application's proxy,
     * or as a workflow variable named 'provisioningStatusCheckInterval'.
     * 
     * If not found on the apps or any workflows the default value will 
     * be -1 which indicates to the checks will execute forever until
     * the result is returned commited.
     * 
     * @param wfc WorkflowContext
     * @return Integer maximum number of status checks
     * @throws GeneralException
     */    
    public Integer getProvisioningMaxStatusChecks(WorkflowContext wfc) 
        throws GeneralException {
        
        return getIntegerConfig(wfc, ATT_PROVISIONING_MAX_STATUS_CHECKS, -1);
    }

    /**
     * 
     * Compute the maximum retries allowed during a request.   This value
     * comes from either the application, the application's proxy,
     * or as a workflow variable named 'provisioningMaxRetries'.
     * 
     * If not found on the apps or any workflows the default value will 
     * be -1 which indicates to execute forever.
     * 
     * @param wfc WorkflowContext
     * @return Integer maximum number of retries
     * @throws GeneralException
     */
    public Integer getProvisioningMaxRetries(WorkflowContext wfc )
        throws GeneralException {
        
        return getIntegerConfig(wfc, ATT_MAX_RETRIES, -1);
    }

    /**
     * Compute the provisioning retry threshold used during a request.   
     * This value * comes from either the application, the application's 
     * proxy, * or as a workflow variable named 'provisioningMaxRetries'.
     * 
     * If not found on the apps or any workflows the default value will 
     * be 60 minutes in between retries.
     * 
     * @param wfc WorkflowContext
     * @return Integer retry threshold
     * @throws GeneralException
     */
    public Integer getProvisioningRetryThreshold(WorkflowContext wfc )
        throws GeneralException {
        
        return getIntegerConfig(wfc, ATT_RETRY_THRESHOLD, 60);
    }

    /** 
     * Go through the project checking each app ( and its proxy ) for the key. 
     * If not found, try to resolve it from a workflow variable and default to 
     * the value passed into the method.
     */
    private Integer getIntegerConfig(WorkflowContext wfc, String keyName, Integer defaultVal )
        throws GeneralException { 

        Attributes<String,Object> args = wfc.getArguments();               
        //
        // Dig through the applications and see if there are apps
        // that specify the number of retries.
        //        
        Integer max = null;
        ProvisioningProject project = (ProvisioningProject)Util.get(args, VAR_PROJECT);
        if ( project != null ) {
            List<ProvisioningPlan> plans = project.getPlans();
            if ( Util.size(plans) > 0 )  {
                for ( ProvisioningPlan plan : plans ) {
                    List<String> names = plan.getApplicationNames();                    
                    if ( names == null ) 
                        continue;
                    
                    for ( String appName : names ) {
                        if ( appName != null && appName.compareTo(ProvisioningPlan.APP_IIQ) != 0 ) {
                            Application app = wfc.getSailPointContext().getObjectByName(Application.class, appName);
                            if ( app != null ) {
                                Attributes<String,Object> config = app.getAttributes();
                                if ( config == null || ( config != null && !config.containsKey(keyName) ) ) {
                                    Application proxy = app.getProxy();
                                    if ( proxy != null ) {
                                        Attributes<String,Object> proxyConfig = proxy.getAttributes();
                                        if ( proxyConfig != null && proxyConfig.containsKey(keyName) ) {
                                            config = proxyConfig;
                                        }
                                    }
                                }                            
                                
                                // check the config
                                if ( config != null ) {
                                    Integer configMax = config.getInteger(keyName);
                                    if ( configMax != null ) {
                                        // Take the largest of the numbers across
                                        // all applications involved
                                        if ( max == null || configMax > max ) {
                                            max = configMax;
                                        }
                                    }                                
                                }               
                            }
                        }
                    }
                }
            }            
        }
        
        // If we didn't resolve a max retry on one of the apps
        // use the workflow variable or default to -1 which
        // is forever
        if ( max == null ) {
            // allow it to be set by the arguments
            Integer wfVal = (Integer)Util.get(args, keyName);
            if ( wfVal == null ) {            
                // Default to forever if not found on any app 
                // and not specified on the workflow.
                max = defaultVal;
            } else {
                max = wfVal;
            }
        }
        return max;
    }    
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Native change detection workflow methods
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * Build an ApprovalSet that can be used to approve
     * the changes that are detected by our native
     * change detection.
     * 
     * The event is passed in using the 'event' 
     * argument name.
     * 
     * @param wfc WorkflowContext
     * @return ApprovalSet
     * @throws GeneralException
     */
    public ApprovalSet buildApprovalSetFromNativeChanges(WorkflowContext wfc )
        throws GeneralException {
        
        Attributes<String,Object> args = wfc.getArguments();
        IdentityChangeEvent event = (IdentityChangeEvent) Util.get(args, "event");
        ApprovalSet set = null;
        if ( event != null ) {
            List<NativeChangeDetection> changes = event.getNativeChanges();
            if ( Util.size(changes) > 0 ) {
                set = changesToApprovalSet(wfc.getSailPointContext(),changes);
            }
        } 
        return set;
    }

    /**
     * Convert the NativeChangeDetections into ApprovalSet/ApprovalItem
     * if the so they can be approved granularly through our
     * detection.
     */
    private ApprovalSet changesToApprovalSet(SailPointContext context, List<NativeChangeDetection> changes) {
        
        if ( Util.size(changes) == 0 ) return null;
        
        ApprovalSet set = new ApprovalSet();
        for ( NativeChangeDetection change : changes ) {
            List<Difference> diffs = change.getDifferences();
            if ( Util.size(diffs) > 0 ) {
                ApprovalItem baseItem = new ApprovalItem();
                baseItem.setNativeIdentity(change.getNativeIdentity());
                baseItem.setApplication(change.getApplicationName());
                baseItem.setInstance(change.getInstance());
                String opString = "Modify";
                ProvisioningPlan.AccountRequest.Operation op = change.getOperation();
                if ( op != null ) 
                    opString = op.toString();
                baseItem.setOperation(opString);

                if ( diffs != null ) {
                    for ( Difference diff : diffs ) {
                        baseItem.setName(diff.getAttribute());

                        if (diff.isMulti()) {
                            List<String> added = diff.getAddedValues();
                            if ( Util.size(added) > 0 ) {
                                for ( String add : added ) {
                                    ApprovalItem item = createNativeChangeApprovalItemFromBaseItem(context, baseItem, Operation.Add, add, diff);

                                    set.add(item);
                                }
                            }

                            List<String> removed = diff.getRemovedValues();
                            if ( Util.size(removed) > 0 ) {
                                for ( String remove : removed ) {
                                    ApprovalItem item = createNativeChangeApprovalItemFromBaseItem(context, baseItem, Operation.Remove, remove, diff);

                                    set.add(item);
                                }
                            }
                        } else {
                            // for the single-value case, if the new value is null then treat it as a remove
                            Operation operation = null == diff.getNewValue() ? Operation.Remove : Operation.Set;
                            ApprovalItem item = createNativeChangeApprovalItemFromBaseItem(context, baseItem, operation, diff.getNewValue(), diff);

                            set.add(item);
                        }
                    }
                }
            }            
        }
        return set;
    }

    /**
     * Clones the base ApprovalItem and then applies the supplied values to it.
     *
     * @param context The context.
     * @param baseItem The base ApprovalItem to clone.
     * @param op The operation.
     * @param value The value.
     * @param diff The difference object.
     * @return The new ApprovalItem instance.
     */
    private ApprovalItem createNativeChangeApprovalItemFromBaseItem(SailPointContext context, ApprovalItem baseItem, Operation op, Object value, Difference diff) {
        ApprovalItem item = (ApprovalItem)XMLObjectFactory.getInstance().cloneWithoutId(baseItem, context) ;
        item.setOperation(op.toString());
        item.setValue(value);

        if (null == item.getAttributes()) {
            item.setAttributes(new Attributes<String, Object>());
        }

        item.getAttributes().put(ATTR_DIFF, diff);

        return item;
    }
    
    /**
     * For each rejected item in the plan, reverse it so the 
     * native changes get "undone".
     * 
     * @param wfc WorkflowContext
     * @return ProvisioningPlan
     */    
    public ProvisioningPlan processNativeChangesApprovalDecisions(WorkflowContext wfc) {
        Attributes<String,Object> args = wfc.getArguments();
        ApprovalSet set = (ApprovalSet) Util.get(args, "approvalSet");
        ProvisioningPlan plan = null;
        if ( set != null ) {
            // for anything rejected build a plan to reverse the native change...
            List<ApprovalItem> rejects = set.getRejected();
            if ( Util.size(rejects) > 0 ) {
                plan = new ProvisioningPlan();
                for ( ApprovalItem rejected  : rejects ) {
                    if ( rejected == null ) continue;
                    
                    AccountRequest request = new AccountRequest();
                    request.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
                    request.setNativeIdentity(rejected.getNativeIdentity());
                    request.setInstance(rejected.getInstance());
                    request.setApplication(rejected.getApplication());                    
                    // 
                    // TODO: should this be assigned?
                    //                  
                    AttributeRequest attr = new AttributeRequest();
                    attr.setName(rejected.getName());

                    // default to multi-valued
                    boolean isMultiValued = true;

                    Difference diff = null;
                    if (null != rejected.getAttributes() && rejected.getAttributes().containsKey(ATTR_DIFF)) {
                        diff = (Difference) rejected.getAttributes().get(ATTR_DIFF);

                        isMultiValued = null == diff || diff.isMulti();
                    }

                    if (isMultiValued) {
                        attr.setValue(rejected.getValue());
                        if (  Util.nullSafeCompareTo(rejected.getOperation(), Operation.Add.toString()) == 0 )  {
                            attr.setOperation(Operation.Remove);
                        } else {
                            attr.setOperation(Operation.Add);
                        }
                    } else {
                        attr.setValue(diff.getOldValue());
                        attr.setOperation(Operation.Set);
                    }

                    request.add(attr);                    
                    
                    plan.add(request);
                }                
            }
        }
        return plan;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Password Intercept
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ARG_PASSWORD = "password";
    public static final String ARG_IDENTITY_NAME = "identityName";
    public static final String ARG_SOURCE_APPLICATION = "sourceApplication";
    public static final String ARG_TARGET_APPLICATIONS = "targetApplications";
    public static final String ARG_SYNC_ALL = "syncAll";

    /**
     * Compile a provisioning project to implement the propagation
     * of an intercepted password.
     */
    public ProvisioningProject compilePasswordInterceptProject(WorkflowContext wfc) 
        throws GeneralException {

        ProvisioningProject project = null;

        String name = wfc.getString(ARG_IDENTITY_NAME);
        String password = wfc.getString(ARG_PASSWORD);

        if (name == null)
            log.error("Missing identity name");
        
        else if (password == null) 
            log.error("Missing password");

        else {
            SailPointContext spc = wfc.getSailPointContext();
            Identity ident = spc.getObjectByName(Identity.class, name);
            if (ident == null) {
                log.error("Invalid identity: " + name);
            }
            else {
                // supposed to be non-null but tolerate
                String srcApplication = wfc.getString(ARG_SOURCE_APPLICATION);
                boolean syncAll = wfc.getBoolean(ARG_SYNC_ALL);

                List<String> targets = null;
                Object o = wfc.get(ARG_TARGET_APPLICATIONS);
                if (o instanceof List)
                    targets = (List<String>)o;
                else if (o instanceof String)
                    targets = Util.csvToList((String)o);

                List<Link> targetLinks = new ArrayList<Link>();
                List<Link> links = ident.getLinks();
                if (links != null) {    
                    for (Link link : links) {
                        String appname = link.getApplication().getName();
                        if ((srcApplication == null ||
                             !srcApplication.equals(appname)) &&
                            (syncAll || 
                             (targets != null && targets.contains(appname)))) {
                            targetLinks.add(link);
                        }
                    }
                }

                if (targetLinks == null || targetLinks.size() == 0) {
                    if (log.isInfoEnabled())
                        log.info("No password synchronization targets");
                }
                else {
                    ProvisioningPlan plan = new ProvisioningPlan();
                    plan.setIdentity(ident);

                    for (Link link : targetLinks) {
                        Application app = link.getApplication();
                        //System.out.println("Synchronizing password to: " + 
                        //app.getName());

                        AccountRequest req = new AccountRequest();
                        plan.add(req);
                        req.setApplication(app.getName());
                        req.setNativeIdentity(link.getNativeIdentity());
                        req.setInstance(link.getInstance());
                        
                        AttributeRequest areq = new AttributeRequest();
                        req.add(areq);
                        // TODO: can we assume the name will always be this?
                        areq.setName("password");
                        areq.setOperation(Operation.Set);
                        areq.setValue(password);
                    }

                    if (log.isInfoEnabled()) {
                        log.info("Password synchronization plan:");
                        log.info(plan.toXml());
                    }

                    // For consistency with other compilations, pass the step
                    // arguments for provisioner options and script args though
                    // we shouldn't have anything fancy for password intercept.
                    Attributes<String,Object> provArgs = 
                        getProvisionerArguments(wfc.getStepArguments());
            
                    // The map passed to the constructor may contains options
                    // for compilation and evaluation. These will be stored in the
                    // returned project.
                    Provisioner p = new Provisioner(spc, provArgs);

                    // The argument map in this method is used for "script args"
                    // that are passed to any scripts or rules in the Templates
                    // or Fields.  Here we use the step args for both the
                    // options to the Provisioner and the script args during compilation.
                    project = p.compile(plan, provArgs);

                    if (log.isInfoEnabled()) {
                        log.info("Password synchronization project:");
                        log.info(plan.toXml());
                    }
                }
            }
        }

        return project;
    }    
    
    ///////////////////////////////////////////////////////////////////////////
    //
    //  Ticketing System Plan Building
    //
    /////////////////////////////////////////////////////////////////////////
    
    public ProvisioningPlan generateTicketPlan(WorkflowContext wfc) 
        throws GeneralException {
        
        ProvisioningPlan ticketPlan = null;
        
        SailPointContext ctx = wfc.getSailPointContext();        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, "project");
        if ( project == null ) {
            throw new GeneralException("Provisioning project was null, unable to transform provisioning data into ticket data.");
        }
        
        String applicationName = Util.getString(args, "application");
        if ( applicationName == null ) {
            throw new GeneralException("Must specify an application when generating a ticket plan.");
        }
        Application app = (Application)ctx.getObjectByName(Application.class, applicationName);
        if ( app == null ) {
            throw new GeneralException("Unable to find application named '"+applicationName+"'");
        }
        
        String ruleName = Util.getString(args, "rule");
        if ( Util.getString(ruleName) == null ) {
            // check with the application
            ruleName = app.getStringAttributeValue("ticketDataGenerationRule");
        }        
        if ( Util.getString(ruleName) == null ) { 
            throw new GeneralException("Must specify a ticketDataGenerationRule either as a variable to the subprocess or on the ticket management application.");
        }
        
        Rule rule = ctx.getObjectByName(Rule.class, ruleName);
        if ( rule == null ) {
            throw new GeneralException("Specified rule '"+ruleName+"' was not found, unable to transform provisioning data into ticket data.");
        }
        
        IdentityRequest ir = null;
        String identityRequestId = Util.getString(args,"identityRequestId");
        if ( identityRequestId != null ) {
            ir = ctx.getObjectByName(IdentityRequest.class, identityRequestId);
        }
        
        // Set up context and run the rule
        Attributes<String,Object> inputs = new Attributes<String,Object>(args);
        inputs.put("wfc", wfc);
        inputs.put("project", project);        
        inputs.put("identityRequest", ir);
        inputs.put("application", app);
        inputs.put("appName", app.getName());
        
        Object o = ctx.runRule(rule, inputs);
        if ( o != null ) {
            if ( o instanceof ProvisioningPlan )
                ticketPlan = (ProvisioningPlan)o;
            else 
            if (o instanceof Map ) {
                //TODO: is this enought for object requests?
                ticketPlan = new ProvisioningPlan((Map)o);
            }
        }         
        return ticketPlan;               
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Map Model
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * Build the Map model of the identity.
     *
     * NOTE: currently builds one manually until we get the IdentityTransformer
     * ready.
     * 
     * @param wfc WorkflowContext
     * @return Map of values modeling the identity
     * @throws GeneralException
     * 
     */
    public Map<String,Object> getIdentityModel(WorkflowContext wfc) 
        throws GeneralException {
        
        SailPointContext ctx = wfc.getSailPointContext();        
        Attributes<String,Object> args = wfc.getArguments();
               
        String identityName = Util.getString(args, "identityName");
        String identityId = Util.getString(args, "identityId");
        
        Identity id = null;
        if ( identityId != null ) {
            id = ctx.getObjectById(Identity.class, identityId);
        } else {
            if ( identityName != null ) {
                identityName = identityName.trim();
                id = ctx.getObjectByName(Identity.class, identityName);
                if ( id == null ) {
                   //This must be a new identity
                   id = new Identity();
                   id.setName(identityName);
                }
            } else {
                id = new Identity();
            }
        }
        
        Map<String,Object> stepArgs = wfc.getStepArguments();        
        HashMap<String,Object> ops = null;
        if ( stepArgs != null ) {
            ops = new HashMap<String,Object>(stepArgs);
        } else {
            ops = new HashMap<String,Object>();
        }
        
        IdentityTransformer optimus = new IdentityTransformer(ctx, ops);
        Map<String,Object> mapModel = optimus.toMap(id);
                
        return mapModel;
    }
    
    /**
     * Build up a ProvisioningPlan from the map model that has been updated.
     * 
     * The returned ProvisioningPlan will be null if nothing has changed.
     * 
     * @param wfc WorkflowContext
     * @return ProvisioningPlan
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public ProvisioningPlan buildPlanFromIdentityModel(WorkflowContext wfc) 
        throws GeneralException {
        
        Attributes<String,Object> args = wfc.getArguments();
        Map<String,Object> identityModel = (Map<String,Object>)Util.get(args, "identityModel");
        if ( identityModel == null ) {
            throw new GeneralException("Identity map model was null.");
        }
        
        Map<String,Object> stepArgs = wfc.getStepArguments();        
        HashMap<String,Object> ops = null;
        if ( stepArgs != null ) {
            ops = new HashMap<String,Object>(stepArgs);
        } else {
            ops = new HashMap<String,Object>();
        }
        
        IdentityTransformer optimus = new IdentityTransformer(wfc.getSailPointContext(), ops);
        ProvisioningPlan plan = optimus.mapToPlan(identityModel, ops);
        
        return plan;
    }   
        
    /**
     * Library call that will apply the committed changes for a request
     * that was in the check for status loop.
     * 
     * Ultimately complete the request as committed and make sure the
     * link was created after we've successfully completed the check
     * status loop.
     * 
     * @param wfc WorkflowContext
     * @throws GeneralException
     * 
     * @ignore : Bug#16804
     */
    public void applyCommittedResults(WorkflowContext wfc)  
        throws GeneralException {
            
        SailPointContext ctx = wfc.getSailPointContext();        
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
        if ( project == null ) {
            throw new GeneralException("Provisioning project was null, unable to transform provisioning data into ticket data.");
        }
        
        String name = project.getIdentity();
        if (name == null) {
            throw new GeneralException("Missing identity name on project.");
        }
        
        SailPointContext spc = wfc.getSailPointContext();
        Identity identity = spc.getObjectByName(Identity.class, name);
        if (identity == null) {
            throw new GeneralException("Invalid identity passed to applyCommittedResults: " + name);
        }
        
        PlanApplier applier = new PlanApplier(ctx, project);
        if ( applier.hasApplyablePlans(true) ) {
            applier.applyAcccountPlans(identity);
            applier.finish(false);
            ctx.commitTransaction();        
        }
    }
    
    /**
     * Library call that returns whether or not there are outstanding forms
     * for the associated identity.
     * @param wfc
     * @return whether or not there are outstanding forms for the associated identity
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public boolean outstandingProvisioningFormsForIdentity(WorkflowContext wfc) 
        throws GeneralException {
        boolean outstandingFormsOrDualRequestsForIdentity = false;
        SailPointContext ctx = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        //VAR_IDENTITY looks to be an Identity object? -rap
        Identity ident = ctx.getObjectById(Identity.class, (String)Util.get(args, VAR_IDENTITY));
        ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
        
        String eventType = args.getString(Request.ATT_EVENT_TYPE);
        String assignmentId = args.getString(RoleEventGenerator.ARG_ASSIGNMENT_ID);
        String roleId = args.getString(RoleEventGenerator.ARG_ROLE);
        String appId = args.getString(RoleEventGenerator.ARG_APPLICATION);
        String instance = args.getString(RoleEventGenerator.ARG_INSTANCE);
        String accountId = args.getString(RoleEventGenerator.ARG_ACCOUNT_ID);
        String attributeName = args.getString(RoleEventGenerator.ARG_NAME);
        Object attributeValue = args.get(RoleEventGenerator.ARG_VALUE);
        
        if (RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
                RoleEventGenerator.EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType) ||
                RoleEventGenerator.EVENT_TYPE_ROLE_PROVISIONING.equals(eventType) ||
                RoleEventGenerator.EVENT_TYPE_ROLE_DEPROVISIONING.equals(eventType)) {
            
            Bundle role =  ctx.getObjectById(Bundle.class, roleId);
            
            if (role != null) {
                RoleAssignment assignment = getRoleAssignment(ident, assignmentId, role, false);
                
                if (assignment != null) {
                    List<RoleAssignment> startingAssignments = getStartDatedRoleAssignments(ident);
                    //Two scheduled role assignments, somone is going to wait.
                    if (startingAssignments != null && startingAssignments.size() > 1 && !assignment.equals(startingAssignments.get(0))) {
                        outstandingFormsOrDualRequestsForIdentity = true;
                    }
                }
            }

                
        } else if (RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_ASSIGNMENT.equals(eventType) ||
                     RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT.equals(eventType)){
            
            Application app = ctx.getObjectById(Application.class, appId);
            
            if (app != null) {
                AttributeAssignment assignment = getAttributeAssignment(ident, app, instance,
                        accountId, attributeName,
                        attributeValue, assignmentId);
                
                if (assignment != null) {
                    List<AttributeAssignment> startingAssignments = getStartDatedAttributeAssignmentsForApp(ident, app);
                    //Two scheduled entitlemnt assignmetns on the same app, someone is going to wait.
                    if (startingAssignments != null && startingAssignments.size() > 1 && !assignment.equals(startingAssignments.get(0))) {
                        outstandingFormsOrDualRequestsForIdentity = true;
                    }
                }
            }
        }
        
        //No need to check for forms if we've already determined there were multiple requests outstanding.
        if (!outstandingFormsOrDualRequestsForIdentity) {
            boolean enableManualAccountSelection = Util.atob((String)Util.get(args, ARG_ENABLE_MANUAL_ACCOUNT_SELECTION));
            //TODO We can dig throught the project the ensure we only wait when apps match,
            //otherwise, to keep things simple, users should not have outstanding forms
            //when attempting to compile a project anyhow.
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("targetName", ident.getName()));
            List<String> wfcaseIds = ObjectUtil.getObjectIds(ctx, WorkflowCase.class, qo);
            for(String wfcaseId : Util.iterate(wfcaseIds)) {
                QueryOptions workItemqo = new QueryOptions();
                workItemqo.add(Filter.eq("workflowCase.id", wfcaseId));
                workItemqo.add(Filter.eq("type", WorkItem.Type.Form));
                if(ctx.countObjects(WorkItem.class, workItemqo) > 0) {
                    outstandingFormsOrDualRequestsForIdentity = (project != null && (project.hasQuestions() || (enableManualAccountSelection && project.hasUnansweredAccountSelections()) ||  project.hasUnansweredProvisioningTargets()));
                }
            }
        }

        return outstandingFormsOrDualRequestsForIdentity;
    }
    /**
     * Meant to be used by Scheduled Assignment so that IdentityEntitlements can
     * be pre-created prior to provisioning to ensure we know the entitlements came
     * from a request and not aggregation.
     * @param wfc
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public void processProject(WorkflowContext wfc) throws GeneralException
    {
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        Identity ident = context.getObjectById(Identity.class, (String)Util.get(args, VAR_IDENTITY));
        ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
        new RequestEntitlizer(context).setPending(ident, project);
    }
    
    /**
     * Audit Role/Entitlement Add/Remove events for the provisioning project.  
     * This is used in "Scheduled Assignment" workflow. 
     * 
     * @param wfc The WorkflowContext
     * @throws GeneralException
     */
    public void auditScheduledProject(WorkflowContext wfc) throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();
        Attributes<String, Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
        
        for (AccountRequest req : Util.safeIterable(project.getMasterPlan().getAccountRequests())) {
            for (ProvisioningPlan.AttributeRequest attrReq : Util.safeIterable(req.getAttributeRequests())) {
                Operation op = attrReq.getOperation();
                AuditEvent event = buildBaseEventForAttributeRequest(wfc, attrReq);
                event.setAttribute("requester", project.getString("requester"));
                event.setSource(project.getString("requester"));
                event.setApplication(req.getApplication());
                event.setInstance(req.getInstance());
                if (req.getNativeIdentity() != null) {
                    event.setAccountName(req.getNativeIdentity());
                } else {
                    event.setAccountName(project.getIdentity());
                }
                
                if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrReq.getName()) ||
                    ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrReq.getName())) {

                    if (Operation.Add.equals(op)) {
                        event.setAction(AuditEvent.RoleAdd);
                    } else if (Operation.Remove.equals(op) || Operation.Revoke.equals(op)) {
                        event.setAction(AuditEvent.RoleRemove);
                    }
                } else {
                    if (Operation.Add.equals(op)) {
                        event.setAction(AuditEvent.EntitlementAdd);
                    } else if (Operation.Remove.equals(op) || Operation.Revoke.equals(op)) {
                        event.setAction(AuditEvent.EntitlementRemove);
                    }
                }

                if ( event.getAction() != null && Auditor.isEnabled(event.getAction()) ) {
                    Auditor.log(event);
                    context.commitTransaction();
                }
            }
       }
    }

    //create AuditEvent for the AttributeRequest
    private AuditEvent buildBaseEventForAttributeRequest(WorkflowContext wfc, 
                                            AttributeRequest item) {

        AuditEvent event = new AuditEvent();
        Attributes<String,Object> args = wfc.getArguments();
        if ( args == null ) args = new Attributes<String,Object>();
        
        event.setAttribute("operation", item.getOperation());
        event.setTarget(args.getString(VAR_IDENTITY_NAME));
        
        event.setAttributes(item.getArguments());
        event.setAttributeName(item.getName());
        
        Object value = item.getValue();
        if (value instanceof Bundle) {
            event.setAttributeValue(((Bundle)value).getName());
        } else {
            event.setAttributeValue(Util.otos(value));
        }
         
        event.setTrackingId(wfc.getWorkflow().getProcessLogId());
        String interfaceName = wfc.getString("interface");
        if ( interfaceName == null)
            interfaceName = Source.LCM.toString();
        
        event.setAttribute("interface", interfaceName);
        event.setInterface(interfaceName);
         
        String taskResultId = wfc.getString(Workflow.VAR_TASK_RESULT);
        if ( taskResultId != null ) {
            event.setAttribute(Workflow.VAR_TASK_RESULT, taskResultId);
        }
        
        event.setAttribute("flow", wfc.getWorkflow().getName());
        
        return event;
    }
}
