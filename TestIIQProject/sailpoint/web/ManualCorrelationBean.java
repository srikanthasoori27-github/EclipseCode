/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.IdentityLifecycler;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Attributes;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.Source;
import sailpoint.object.WorkflowTarget;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityLibrary;

/**
 * Handles moving uncorrelated links to an identity and updating link attributes.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ManualCorrelationBean extends BaseListBean<Identity> {

    private static Log log = LogFactory.getLog(ManualCorrelationBean.class);

    private static final String SELECTED_ACCOUNTS_PARAM = "selected";
    private static final String EXCLUDED_ACCOUNTS_PARAM = "excluded";
    private static final String SELECT_ALL_ACCOUNTS_PARAM = "selectAll";
    private static final String TARGET_IDENTITY_PARAM = "target";


    private List<String> selectedIds;
    private List<String> excludedIds;
    private String targetId;
    private boolean selectAll;
    private List<Link> selectedLinks;


    public ManualCorrelationBean() {
        if (super.getRequestParameter(SELECTED_ACCOUNTS_PARAM) != null)
            selectedIds = Util.csvToList(super.getRequestParameter(SELECTED_ACCOUNTS_PARAM));
        if (super.getRequestParameter(EXCLUDED_ACCOUNTS_PARAM) != null)
            excludedIds = Util.csvToList(super.getRequestParameter(EXCLUDED_ACCOUNTS_PARAM));
        if (super.getRequestParameter(SELECT_ALL_ACCOUNTS_PARAM) != null)
            selectAll = Boolean.parseBoolean(super.getRequestParameter(SELECT_ALL_ACCOUNTS_PARAM));

        if (super.getRequestParameter(TARGET_IDENTITY_PARAM) != null)
            targetId = super.getRequestParameter(TARGET_IDENTITY_PARAM);
    }


    /**
     * Gets links that were selected in the grid.
     *
     * @return
     * @throws GeneralException
     */
    private Iterator<Link> getSelectedLinksIterator() throws GeneralException{

        Iterator<Link> links = null;
        if (selectAll){

            ManualCorrelationLinkBean linkBean = new ManualCorrelationLinkBean();
            QueryOptions ops = linkBean.getQueryOptions();
            ops.setResultLimit(0); // override limit so we get all
            if (excludedIds != null){
                for(String id : excludedIds){
                    ops.add(Filter.ne("id", id));
                }
            }
            links = getContext().search(Link.class, ops);
        }
         else if (selectedIds != null && !selectedIds.isEmpty()){
             links =getContext().search(Link.class, new QueryOptions(Filter.in("id", selectedIds)));
         }

        return links;
    }
    
    private void loadSelectedLinks() throws GeneralException {
        
        this.selectedLinks = new ArrayList<Link>();

        Iterator<Link> iterator = getSelectedLinksIterator();
        if(iterator != null) { //bug 8724 - iterator is null if no accounts are selected.  Should get caught in the UI now.
            while (iterator.hasNext()) {
                this.selectedLinks.add(iterator.next());
            }
        }
    }
    
    private ProvisioningPlan buildProvisioningPlan(Identity target) throws GeneralException {
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(target);
        // metadata for assignments and logging
        plan.addRequester(getLoggedInUser());
        plan.setSource(Source.UI);

        AccountRequest account = new AccountRequest();
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        account.setApplication(ProvisioningPlan.APP_IIQ);
        plan.add(account);
        
        for (Link link : this.selectedLinks) {
            AttributeRequest req = new AttributeRequest();
            req.setName(ProvisioningPlan.ATT_IIQ_LINKS);
            req.setOperation(ProvisioningPlan.Operation.Add);
            //TODO: !!split up per identity
            req.put(ProvisioningPlan.ARG_SOURCE_IDENTITY, link.getIdentity().getName());
            req.setValue(link.getId());
            req.setDisplayValue(getLinkDisplayValue(link));
            account.add(req);
        }
        
        return plan;
    }
    
    private String getLinkDisplayValue(Link link) {
        return String.format("%s: %s (%s)", link.getApplicationName(), link.getNativeIdentity(), link.getIdentity().getName());
    }
    
    private WorkflowSession performMerge(Identity target, ProvisioningPlan plan) throws GeneralException {
        WorkflowSession session = null;
        if (shouldLaunchWorkflow()) {
            session = launchWorkflow(target, plan);
        } else {
            executePlan(target, plan);
        }
        return session;
    }
    
    private boolean shouldLaunchWorkflow() throws GeneralException {
        Configuration config = getContext().getConfiguration();
        String workflowName = config.getString(Configuration.WORKFLOW_IDENTITY_CORRELATION);
        return !Util.isNullOrEmpty(workflowName);
    }
    
    private WorkflowSession launchWorkflow(Identity target, ProvisioningPlan plan) throws GeneralException{
        SailPointContext context = getContext();
        IdentityLifecycler cycler = new IdentityLifecycler(context);

        Attributes<String,Object> args = new Attributes<String,Object>();
        args.put(IdentityLibrary.VAR_FLOW, IdentityRequest.IDENTITY_UPDATE_FLOW_CONFIG_NAME);
        return cycler.launchUpdate(null, target, plan, Configuration.WORKFLOW_IDENTITY_CORRELATION, args, fetchSecondaryTargets());
    }
    
    private List<WorkflowTarget> fetchSecondaryTargets() {
        
        List<WorkflowTarget> secondaryTargets = new ArrayList<WorkflowTarget>();
        for (Link link : this.selectedLinks) {
            WorkflowTarget target = new WorkflowTarget();
            target.setName(link.getDisplayableName());
            target.setClassName(Link.class.getName());
            target.setObjectId(link.getId());
            target.setObjectName(link.getName());
            secondaryTargets.add(target);
        }
        return secondaryTargets;
    }
    
    private void executePlan(Identity target, ProvisioningPlan plan) throws GeneralException {
        Provisioner prov = new Provisioner(getContext());
        prov.compileOld(target, plan, true);
        ProvisioningProject proj = prov.getProject();
        if (proj == null) {throw new IllegalStateException("No project compiled\n");}
        prov.execute();
    }
    
    /**
     * Moves selected links onto the the selected identity. Any link which is
     * moved is flagged as manually correlated. The target identity plus all
     * identities that the links came off of are refreshed.
     *
     * @return Json result string
     */
    public String getMerge() throws GeneralException {
        Identity target = null;
        try {
            target = getContext().getObjectById(Identity.class, targetId);
            loadSelectedLinks();
        } catch (GeneralException e) {
            log.error("Error attempting to retrieve merge source and target.", e);
            return JsonHelper.failure();
        }
        if (target == null){
            String err = Internationalizer.getMessage(MessageKeys.IDENTITY_CORRELATION_MISSING_TARGET_IDENTITY, getLocale());
            return JsonHelper.failure(err);
        }
        if (this.selectedLinks.size() == 0){
             return JsonHelper.failure();
        }
        
        ProvisioningPlan plan = buildProvisioningPlan(target);

        try {
            WorkflowSession session = performMerge(target, plan);
        
            // A null or complete session means that the merge is done.
            if ((null == session) || session.getWorkflowLaunch().isComplete()) {
                return JsonHelper.success();
            }
            else {
                // Otherwise, we have an incomplete session, which likely means
                // that we are waiting for an approval.
                return JsonHelper.success("pendingApproval", true);
            }
        }
        catch (ObjectAlreadyLockedException e) {
            String err = Internationalizer.getMessage(MessageKeys.IDENTITY_CORRELATION_TARGET_LOCKED, getLocale());
            return JsonHelper.failure(err);
        }

    }
    
    /**
     * Updates the value for the given link attribute.
     *
     * @return Json result string
     */
    public String getUpdateAttribute() {

         Boolean newVal = Boolean.parseBoolean(getRequestParameter("value"));
         String attributeName = getRequestParameter("attribute");
         String linkId = getRequestParameter("id");

         try {
             ObjectConfig config = super.getLinkConfig();
             ObjectAttribute updatedAttribute = config.getExtendedAttributeMap().get(attributeName);
             Link link = getContext().getObjectById(Link.class, linkId);

             // Save the old identity for the change event.
             Identity prevId =
                 (Identity) link.getIdentity().deepCopy((Resolver) getContext());

             ObjectUtil.editObjectAttribute(link, updatedAttribute, newVal,
                     getLoggedInUser().getName());

             AuditConfig auditConf = getContext().getObjectByName(AuditConfig.class, AuditConfig.OBJ_NAME);
             AuditConfig.AuditAttribute conf = auditConf.getAuditAttribute("Link", attributeName);
             if (conf != null && conf.isEnabled()){
                 AuditEvent e = new AuditEvent();
                 e.setAction(AuditEvent.ActionChange);
                 e.setTarget(link.getNativeIdentity());
                 e.setString1(link.getApplicationName());
                 e.setString2(updatedAttribute.getName());
                 e.setString3(AuditEvent.ChangeSet);
                 e.setString4(newVal.toString());

                 Auditor.log(e);
             }

              // Save the link and the identity (identity needs to increment its
             // modified date to be picked up as changed by the certification
             // refresher on a full scan).
             getContext().saveObject(link);
             getContext().saveObject(link.getIdentity());
             getContext().commitTransaction();

             Identity newId =
                 getContext().getObjectById(Identity.class, prevId.getId());


             // Let the Identitizer perform side-effects related to the
             // changes to this identity.  Background this so we don't
             // hang the UI.  This is not as big of a deal since it comes from
             // an AJAX request, but if someone changes a lot of these at once
             // we could have a bunch of threads running at once.  Backgrounding
             // will help throttle this.
             Identitizer identitizer = new Identitizer(getContext());
             identitizer.setRefreshSource(Source.UI, getLoggedInUserName());
             identitizer.doTriggers(prevId, newId, true);

         } catch (GeneralException e) {
             log.error(e);
             return JsonHelper.failure();
         }

         // currently the success message doesn't do anything...
         return JsonHelper.success();
     }

    /**
       * Updates the correlation status for a given identity. Note that
       * this is a getter since it returns json and must be called from
       * an xhtml template.
       *
       * @return Json response indicating success or failure.
       */
      public String getCorrelationStatusUpdateResults(){

          Boolean newValue = null;
          String selectedId = null;
          Identity selectedIdentity;

          if (super.getRequestParameter("value") != null)
              newValue = Boolean.parseBoolean(super.getRequestParameter("value"));
          else
              return JsonHelper.failure();

          if (super.getRequestParameter("id") != null)
              selectedId =super.getRequestParameter("id");

          try {
              selectedIdentity = getContext().getObjectById(Identity.class, selectedId);
              selectedIdentity.setCorrelated(newValue);
              selectedIdentity.setCorrelatedOverridden( newValue);

              AuditConfig config = AuditConfig.getAuditConfig();

              if (config.isEnabled(Identity.class, AuditEvent.AttCorrelationStatus)) {
                  AuditEvent e = new AuditEvent();
                  e.setAction(AuditEvent.ActionChange);
                  e.setTarget(selectedIdentity.getName());
                  e.setString1(AuditEvent.AttCorrelationStatus);
                  e.setString2(AuditEvent.ChangeSet);
                  e.setString3(newValue.toString());

                  Auditor.log(e);
              }

              getContext().saveObject(selectedIdentity);
              getContext().commitTransaction();

          } catch (GeneralException e) {
              log.error(e);
              return JsonHelper.failure();
          }

          return  JsonHelper.success();
      }

}
