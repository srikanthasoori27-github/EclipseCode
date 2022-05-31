/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class used internally by PlanCompiler to expand an Application's provisioning
 * policies.  This also handles expansions necessary to maintain application
 * dependency ordering.
 * 
 * Author: Jeff/Dan
 * 
 * Factored out of PlanCompiler in 6.3 because it was getting too big.
 *
 * Though ordering is technically not a "policy" the way we usually talk about it,
 * it's a short blob of code and this is a reasonable home for it since we do
 * all of our application specific expansions here.
 *
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Template;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * A class used internally by PlanCompiler to expand an Application's provisioning
 * policies.  This also handles expansions necessary to maintain application
 * dependency ordering.
 */
public class ApplicationPolicyExpander {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(ApplicationPolicyExpander.class);

    /**
     * Action name used to mean no templates were expanded.
     */
    private static final String ACTION_NONE = "none";
    
    public static final String PROVISIONING_POLICIES = "provisioningPolicies";
    public static final String CHANGE_PASSWORD_POLICY = "ChangePassword";
    private static final String ARG_NO_LINK_NEEDED = "noLinkNeeded";

    /**
     * Parent compiler.
     */
    PlanCompiler _comp;

    /**
     * Project we're compiling.
     */
    ProvisioningProject _project;

    //////////////////////////////////////////////////////////////////////  
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////  

    public ApplicationPolicyExpander(PlanCompiler comp) {
        _comp = comp;
        _project = _comp.getProject();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Application Order Dependencies
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Add in any dependencies that are required for a particualr application.
     * 
     * Add a Create AccountRequest for any dependencies and let the filtering
     * filter out anything we already have.
     */
    public boolean expandApplicationDependencies() 
        throws GeneralException {
        
        int dependencies = 0;

        List<ProvisioningPlan> plans = null;
        if ( Util.size(_project.getPlans()) > 0 ) 
            plans = new ArrayList<ProvisioningPlan>(_project.getPlans());  
        
        if ( Util.size(plans) > 0 ) {
            for ( ProvisioningPlan plan : plans ) {
                SailPointContext context = _comp.getContext();
                List<Application> apps =  plan.getApplications(context);
                for ( Application app : apps ) {
                    // throw if we have recursive issues.
                    List<Application> dependentApps = app.getDependencies();
                    if ( Util.size(dependentApps) > 0 ) {
                        ObjectUtil.validateDependencies(app);                    
                        // make sure we have a link and if not we need to expand into a create
                        addDependencies(dependentApps);
                        dependencies++;
                    }
                }
            }        
        }

        return (dependencies > 0);
    }
    
    /**
     * Add any missing dependencies to the plan.
     * 
     * @param dependencies
     * @throws GeneralException
     */
    private void addDependencies(List<Application> dependencies) throws GeneralException {
        
        if ( dependencies != null ) {
            for ( Application dependency : dependencies ) {
                if ( dependency != null ) {               
                    //If the user has a link, no reason to expand
                    Identity identity = _comp.getIdentity();

                    // jsl - need to think about how this relates to the new
                    // target memory stuff in 6.3.  Roles and entitlmenets have
                    // target memory, what about dependencies?

                    //get the account of the identity from the dependent application
                    Link link = identity.getLink(dependency);

                    //if link is null, it means the identity does not has account
                    //in the dependent application, we need to validate if we have
                    //to create a new account for dependent application.
                    //A new AccountRequest will be created only if op is (Create, Modify, Enable or Unlock)
                    if (link == null && _comp.projectNeedsNewAccountForDependentApp(dependency) ) {
                        AccountRequest acct = new AccountRequest();
                        acct.setApplication(dependency.getName());                                
                        acct.setOperation(AccountRequest.Operation.Create);

                        ProvisioningPlan appPlan = _comp.getApplicationPlan(acct);
                        _comp.assimilateRequest(acct, appPlan);

                        Template tmp = dependency.getOldTemplate(Template.Usage.Create, Connector.TYPE_ACCOUNT);
                        expandTemplate(dependency, tmp, acct);

                        // Shove this into a project...
                        _comp.partition(appPlan);
                    }

                    List<Application> subDependencies = dependency.getDependencies();
                    if ( subDependencies != null ) {
                        addDependencies(subDependencies);
                    }
                }
            }
        }
    }    

    //////////////////////////////////////////////////////////////////////
    //
    // Proviisoning Policy (aka Template) Expansion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Expand application templates.
     *
     * For each account in the partitioned plans, check to see if the
     * identity has a link to this account.  If not, convert the
     * account request to Operation.Create and assimilate the
     * creation template from the Application.
     *
     * This will commonly result in the addition of Questions to the project.
     *
     * If the application does not have a creation template, a warning
     * is left in the project's message list.  If the REQUIRE_CREATION_TEMPLATES
     * option is true, the account request is transfered from the integration
     * plans to the unmanaged plan.
     *
     */
    public boolean expandApplicationTemplates() throws GeneralException {

        int expansions = 0;

        // ignore if we have no target Identity
        if (_comp.getIdentity() != null) {

            // We can encounter the same app several times in
            // the unsimplified plans so make sure we only
            // process it once.  
            // !! I'm hating this, can't we have a particular 
            // simplification pass that merges AccountRequests for
            // the same app.  For cert tracking ids I think the most
            // important thing is that the AttributeRequests not be
            // simplified, right?
            Map<String,String> processed = new HashMap<String,String>();

            List<ProvisioningPlan> plans = _project.getPlans();
            if (plans != null) {
                
                // Sigh, if REQUIRE_CREATE_TEMPLATES is on we
                // may bootsrap an unmanaged plan which results in 
                // a concurrent mod exception if we iterate over the
                // original list.  Have to make a copy to prevent this.
                plans = new ArrayList<ProvisioningPlan>(plans);

                for (ProvisioningPlan plan : plans) {
                    if (!plan.isIIQ()) {
                        expandTemplates(plan, processed);
                    }
                }
            }

            // Update templates need to be processed regardless of whether
            // the plan has any requests for the application.  This allows
            // update templates to calculate field values using values on
            // other links or on the identity.
            expandUpdateTemplates(processed);

            // Results are more complicated here because we can't
            // pass an int back up the stack, look for processed
            // actions other than "none".  This is just for limiting
            // project trace.
            Iterator<String> it = processed.values().iterator();
            while (it.hasNext()) {
                String action = it.next();
                if (!ACTION_NONE.equals(action))
                    expansions++;
            }
        }
        else {
            // no identity, must be a ManagedAttribute plan
            // We have Create, Update, and Delete policies for groups
            // but Create and Update aren't really policies the way
            // they are for accounts, they're really Forms that are always
            // presented to the user.  DeleteGroup is like a policy, we
            // use it to inject things into the plan when a group is deleted
            // but it is never shown.

            List<ProvisioningPlan> plans = _project.getPlans();
            if (plans != null) {
                for (ProvisioningPlan plan : plans) {
                    if (!plan.isIIQ()) {
                        List<ObjectRequest> oreqs = plan.getObjectRequests();
                        if (oreqs != null) {
                            for (ObjectRequest oreq : oreqs) {
                                expandTemplates(oreq);                                
                            }
                        }
                    }
                }
            }
        }

        return (expansions > 0);
    }
    
    /**
     * Expand templates for one project plan.
     */
    private void expandTemplates(ProvisioningPlan plan,
                                 Map<String,String> processed)
        throws GeneralException {

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            ListIterator<AccountRequest> it = accounts.listIterator();
            while (it.hasNext()) {
                AccountRequest account = it.next();
                if ((account.isCleanable() || account.isEmpty())) {
                    log.info("Ignoring and removing empty account request for template expansion");
                    it.remove();
                } else {
                    // returns true if it needs to be moved to the unmanagd plan
                    if (expandTemplates(account, processed, plan)) {
                        it.remove();
                        ProvisioningPlan uplan = _project.internUnmanagedPlan();
                        _comp.assimilateRequest(account, uplan);
                    }
                }
                
                
            }
        }
    }
    
    /**
     * Expand templates based on template usage type Return value: false if no
     * template existed for given usage type
     */
    private String expandTemplates(AccountRequest account, Template.Usage usage) 
        throws GeneralException {

        return expandTemplates(account, usage, null, false);
    }

    /**
     * Expand templates based on template usage type Return value: false if no
     * template existed for given usage type
     */
    private String expandTemplates(AccountRequest account, Template.Usage usage, ProvisioningPlan plan, boolean needsNativeIdentity) 
        throws GeneralException {

        Application app = _comp.getApplication(account);
        Link link = _comp.getLink(app, account.getInstance(),
                                  account.getNativeIdentity());
        String action = ACTION_NONE;
        if (!usage.equals(Template.Usage.Create) &&!usage.equals(Template.Usage.Update)  && link == null){
            log.error(account.getOp().toString()
                    + " request for a missing link!");
        }
        else {
            Template tmp = app.getOldTemplate(usage, Connector.TYPE_ACCOUNT);
            if (tmp != null) {
                action = account.getOp().toString().toLowerCase();
                expandTemplate(app, tmp, account, plan, link, needsNativeIdentity);
            }
        }
        return action;
    }

    /**
     * Expand templates for one account request.
     * Return true if the request should be moved to the unmanaged plan
     * because REQUIRE_CREATE_TEMPLATE is on.
     */
    private boolean expandTemplates(AccountRequest account,
                                    Map<String,String> processed,
                                    ProvisioningPlan plan)
        throws GeneralException {

        boolean moveToUnmanged = false;
        // resolve the Application
        Application app = _comp.getApplication(account);
        if (app != null) {
            List<PermissionRequest> perms = account.getPermissionRequests();
            String targetCollector = null;
            if (!Util.isEmpty(perms)) {
                targetCollector = perms.get(0).getTargetCollector();
            }

            String key = createKey(app.getName(), account.getInstance(),
                                   account.getNativeIdentity(), targetCollector);

            if (processed.get(key) == null) {
                String action = ACTION_NONE;
                // note that if there is no instance or native id specified,
                // it doesn't matter if there are more than one matching
                // link since any of them satisfy the request target
                //TODO: This seems wrong? We should not be picking at random any more -rap
                Link link = _comp.getLink(app, account.getInstance(), account.getNativeIdentity());

                ObjectOperation op = account.getOp();
                // normalize missing op
                if (op == null) {
                    op = ObjectOperation.Modify;
                    account.setOp(op);
                }

                // Fix the operation if we're targeting a missing link
                // This is necessary for AccountRequests generatd by
                // role expansion.  
                // Note that this only works for op=Modify, if you manually
                // create a Disable, Enable, Lock, or Unlock request 
                // for a missing account it will not trigger the create 
                // template because we would lose the operation.  
                // That is a programmer error.
                if (ObjectOperation.Modify.equals(op)) {
                    // only promote if there is something in the plan
                    // that requires a link
                    if (link == null && needsAccount(account)) {
                        log.info("Changing operation from Modify to Create");
                        op = ObjectOperation.Create;
                        account.setOp(op);
                    }
                }

                if (ObjectOperation.Delete.equals(op)) {
                    // should this prevent expansion of the template?
                	action = expandTemplates(account, Template.Usage.Delete, plan, true);
                }
                else if (ObjectOperation.Create.equals(op)) {
                    // this probably should prevent expansion?
                    // or should we convert this to op=Modify?
                    // in case of create account on an app we already have a link
                    // on, we might find a link,  but its not the same link
                    // we will be trying to create, so no error please.  
                    if (link != null && account.getNativeIdentity() != null)
                        log.error("Create request for existing link!");
                    // IIQSR-49 -- If the account has a native identity on it, use it to prevent its 
                    // Questions from being merged with those from other accounts on the same Application
                    boolean needsNativeIdentity = account.getNativeIdentity() != null;
                    action = expandTemplates(account, Template.Usage.Create, plan, needsNativeIdentity);
                    if (action.equals(ACTION_NONE)) {
                        action="move";
                        Message msg = new Message("No account creation template for application: " + 
                                                  app.getName());
                        _project.addMessage(msg);
                        // and optinally move it to the unmanged plan
                        moveToUnmanged = _project.getBoolean(PlanCompiler.ARG_REQUIRE_CREATE_TEMPLATES);
                    }

                    // Update templates are also processed for creates.  This
                    // allows rule-based values to be defined in one place and
                    // used for creation or enforcing value restrictions.
                    action = expandTemplates(account, Template.Usage.Update, plan, needsNativeIdentity);
                }
                else {
                    // then do Operation specific templates
                    if (ObjectOperation.Enable.equals(op)) {
                        action = expandTemplates(account, Template.Usage.Enable, plan, true);
                    }
                    else if (ObjectOperation.Disable.equals(op)) {
                        action = expandTemplates(account, Template.Usage.Disable, plan, true);
                    }
                    else if (ObjectOperation.Unlock.equals(op)) {
                        action = expandTemplates(account, Template.Usage.Unlock, plan, true);
                    }
                    else {
                        if(account.getArgument(PROVISIONING_POLICIES)!=null){
                            List<String> policies = new ArrayList<String>();
                            policies = (List<String>) account.getArgument(PROVISIONING_POLICIES);
                            if(policies != null&&(policies.size() > 0 && policies.contains(CHANGE_PASSWORD_POLICY))){
                                action = expandTemplates(account, Template.Usage.ChangePassword, plan, true);
                            }
                        }
                    }
                    // if we didn't have a specific policy for Enable/Disable/Unlock
                    // fall back to Update
                    if (ACTION_NONE.equals(action) &&
                        !_project.getBoolean(PlanCompiler.ARG_AUTO_EXPAND_UPDATE_TEMPLATES)) {
                        action = expandTemplates(account, Template.Usage.Update, plan, true);
                    }
                }

                // add it to the processed map so we don't do it again
                processed.put(key, action);
            }
        }

        return moveToUnmanged;
    }

    /**
     * Expand the update templates for all application on which the
     * identity has accounts (unless this is disabled using
     * ARG_NO_UPDATE_TEMPLATE_AUTO_EXPANSION).
     */
    private void expandUpdateTemplates(Map<String,String> processed)
        throws GeneralException {
        
        // Don't run these if this is disabled.
        if (!_project.getBoolean(PlanCompiler.ARG_AUTO_EXPAND_UPDATE_TEMPLATES)) {
            return;
        }
        
        Identity identity = _comp.getIdentity();

        if ((null != identity) && !Util.isEmpty(identity.getLinks())) {
            // !! It would be great to only do this for applications that we know
            // have update templates rather than iterating over all links.
            // Since templates are currently in XML we don't have a good way to
            // query for links that are relevant.  We could consider caching
            // this.
            for (Link link : identity.getLinks()) {

                Application app = link.getApplication();
                String key = createKey(app.getName(), link.getInstance(),
                                       link.getNativeIdentity(), null);

                // Don't process this twice.
                if (null == processed.get(key)) {
                    Template tmp = app.getOldTemplate(Template.Usage.Update, Connector.TYPE_ACCOUNT);
                    if (null != tmp) {
                        boolean skip = false;
    
                        // Get or create the account request.
                        boolean createdAcctReq = false;
                        AccountRequest account = getAccountRequest(link);
                        if (null == account) {
                            account = new AccountRequest();
                            account.setApplication(app.getName());
                            account.setInstance(link.getInstance());
                            account.setNativeIdentity(link.getNativeIdentity());
                            createdAcctReq = true;
                        }
                        else {
                            // Don't process the update policy if there is a
                            // delete request in the plan for this account.
                            skip = AccountRequest.Operation.Delete.equals(account.getOperation());
                        }

                        if (!skip) {
                            String action = "update";
                            expandTemplate(app, tmp, account);
        
                            if (createdAcctReq) {
                                if (!account.isEmpty()) {
                                    // Shove this into a project.
                                    ProvisioningPlan plan = _comp.getApplicationPlan(account);
                                    _comp.assimilateRequest(account, plan);
                                }
                                else {
                                    // Nothing to see here.
                                    action = ACTION_NONE;
                                }
                            }
                            processed.put(key, action);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Return the AccountRequest in the project for the given link, or null if
     * there is not one in the project.
     */
    private AccountRequest getAccountRequest(Link link) {
        AccountRequest acctReq = null;
        
        if (!Util.isEmpty(_project.getPlans())) {
            for (ProvisioningPlan plan : _project.getPlans()) {
                if (!Util.isEmpty(plan.getAccountRequests())) {
                    for (AccountRequest current : plan.getAccountRequests()) {
                        if (link.getApplication().getName().equals(current.getApplication()) &&
                            Util.nullSafeEq(link.getInstance(), current.getInstance(), true) &&
                            Util.nullSafeEq(link.getNativeIdentity(), current.getNativeIdentity())) {
                            acctReq = current;
                            break;
                        }
                    }
                }
            }
        }
        
        return acctReq;
    }

    /**
     * Create a key for the "processed" map that gives a unique identifier based
     * on account information.
     */
    private static String createKey(String app, String instance,
                                    String nativeIdentity, String targetCollector) {
        // The "key" for this request is a combination of the
        // application name, instance name, and native identity.
        // While unusual you can have several AccoountRequests for
        // the same application in the plan, but which target different
        // instances or different accounts.

        // Bug#22330: In case operation is not specified on an Account Request, the key
        // remains same for the two plans containing Attribute Request in one and Permission
        // Request in the other for the same combination of application and nativeIdentity.
        // So, to distinguish the keys, append target collector from permission request to key.
        String key = app + "/" + instance + "/" + nativeIdentity;

        if (Util.isNotNullOrEmpty(targetCollector)) {
            key += "/" + targetCollector;
        }

        return key;
    }
    
    /**
     * Return true if an account request contains something of interest that
     * would require the creation of an new account if we didn't have one.
     * 
     * The most important thing this does is ignore Remove and Revoke requests
     * so removing a role assignment that was never provisioned doesn't cause
     * the creation of a new account just so we can remove something that
     * never existed.
     * 
     * Also ignore Retains for similar reasons, though we should have done
     * retain filtering by now.
     */
    private boolean needsAccount(AccountRequest req) {

        boolean needs = needsAccount(req.getAttributeRequests());
        if (!needs)
            needs = needsAccount(req.getPermissionRequests());

        return needs;
    }

    private <T extends GenericRequest> boolean needsAccount(List<T> reqs) {
        boolean needs = false;
        if (reqs != null) {
            for (T req : reqs) {
                Operation op = req.getOp();
                /*
                 * ARG_NO_LINK_NEEDED is introduced for AD RecycleBin feature
                 * where restore request is not associated with any identity and
                 * hence link. Still the restore request is a modify request.
                 */
                if (Util.isNullOrEmpty(req.getString(ARG_NO_LINK_NEEDED))) {
                    if (op == Operation.Set || op == Operation.Add) {
                        needs = true;
                        break;
                    }
                }
            }
        }
        return needs;
    }

    /**
     * After looking for a suitable application template, expand it
     * into the AccountRequest.
     * 
     * Note that we're not setting tracking ids for these since
     * they shouldn't be necessary for certification and it's ambiguous
     * what the id should be anyway.  For example, two roles need the
     * same account, which role's tracking id would we use?
     *
     * !! TODO
     * In theory a role could have supplied values for attributes that
     * are also in the creation template.  In that case who wins?  
     * Because we're processing creation templates last, they will but
     * it feels like the role should be able to override something?  
     * If so then the value behavior needs to be SET_IF_NULL rather
     * than REPLACE.
     * 
     */
    private void expandTemplate(Application app, Template tmp, AccountRequest req)
        throws GeneralException {

        expandTemplate(app, tmp, req, null, null, false);
    }
    
    /**
     * After looking for a suitable application template, expand it
     * into the AccountRequest.
     * 
     * Note that we're not setting tracking ids for these since
     * they shouldn't be necessary for certification and it's ambiguous
     * what the id should be anyway.  For example, two roles need the
     * same account, which role's tracking id would we use?
     *
     * !! TODO
     * In theory a role could have supplied values for attributes that
     * are also in the creation template.  In that case who wins?  
     * Because we're processing creation templates last, they will but
     * it feels like the role should be able to override something?  
     * If so then the value behavior needs to be SET_IF_NULL rather
     * than REPLACE.
     * 
     */
    private void expandTemplate(Application app, Template tmp, AccountRequest req, ProvisioningPlan plan, Link link, boolean needsNativeIdentity)
        throws GeneralException {

        // Create a copy of the AccountRequest so we can look for
        // differences after expanding the template.
        AccountRequest copy = req.clone();
        
        // TemplateCompiler does all the work now
        TemplateCompiler tc = new TemplateCompiler(_comp);
        tc.compile(app, tmp, req, plan, link, needsNativeIdentity);

        // Add expansions for anything that has changed.
        _comp.addExpansionItems(copy, req, ExpansionItem.Cause.ProvisioningPolicy, app.getName());
    }

    /**
     * Expand templates for one object request.
     * This is simplar than templates for account requests, we only
     * have to deal with the delete policy and we don't have to 
     * worry about running more than once.
     */
    private void expandTemplates(ObjectRequest req)
        throws GeneralException {

        Application app = _comp.getApplication(req);
        if (app != null) {
            if (ObjectOperation.Delete.equals(req.getOp())) {
                Template tmp = app.getOldTemplate(Template.Usage.Delete, req.getType());
                if (tmp != null) {
                    // not adding expansion items for these, just
                    // compile in place
                    TemplateCompiler tc = new TemplateCompiler(_comp);
                    tc.compile(app, tmp, req);
                }
            }
        }
    }

}
