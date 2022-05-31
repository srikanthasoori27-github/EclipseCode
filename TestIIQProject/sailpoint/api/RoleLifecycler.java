/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to manage approvals and impact analysis on roles.
 * 
 * Author: Jeff
 *
 * Now also manages BundleArchives.
 */

package sailpoint.api;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.BundleDifference;
import sailpoint.object.CandidateRole;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.FilterRenderer;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.ImpactAnalysis;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.ProfileDifference;
import sailpoint.object.PropertyInfo;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.RoleLibrary;

public class RoleLifecycler {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(RoleLifecycler.class);

    /**
     * You have to love context.
     */
    private SailPointContext _context;

    /**
     * locale used for translation
     */
    private Locale _locale;

    public static final String ARCHIVE_ATT_DESCRIPTIONS = "sysDescriptions";

    /**
     * Class to help us in adding/replacing activation/deactivation
     * events.
     */
    RoleEventGenerator _eventGenerator;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleLifecycler(SailPointContext context) {
        _context = context;
        _eventGenerator = new RoleEventGenerator(_context);
        _locale = new Localizer(_context).getDefaultLocale();
    }

    public RoleLifecycler(SailPointContext context, Locale locale) {
        _context = context;
        _eventGenerator = new RoleEventGenerator(_context);
        _locale = locale;
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Common Workflow Launching
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Common workflow launching for approvals with or without
     * impact analysis.
     * 
     * The object must not have been saved.  The object must
     * either be attached to the _context, or be loaded to the extent
     * necessary to complete an XML serialization.
     * (Actualy it doesn't have to be attached but everything it references
     * needs to be if it is not fully loaded).
     *
     * The method will rollback the current transaction and decache
     * so the caller must make no assumptions about the state of 
     * the SailPointContext and attached objects when it returns.
     *
     * Upon return, the caller must not assume that the SailPointObject 
     * passed in will reflect the state of the object in the repository or
     * in the workflow case.
     * 
     * If a WorkflowCase was peristed the id is returned.
     * This may be used as a flag to indiciate that an approval
     * is pending, and to fetch the case if desired.
     *
     * !! Need to think about new objects.  They won't show up in the
     * tree until approved.  That's not bad, but may want an option
     * to save them in a disabled state before launching the workflow.
     *
     * The "neu" argument has the object to be approved.
     * The "invars" argument has the initial set of workflow variables.
     */
    private String launchApproval(SailPointObject neu,
                                  Map<String,Object> invars)
        throws GeneralException {

        String caseId = null;
        
        // serialize the new object to XML before we get too far because
        // the workflow won't launch without this
        String xml = neu.toXml();

        _context.rollbackTransaction();
        _context.decache();

        // get current version
        SailPointObject current = null;
        if (neu.getId() != null)
            current = _context.getObjectById(neu.getClass(), neu.getId());
        else
            current = _context.getObjectByName(neu.getClass(), neu.getName());

        // sigh, can have only one outstanding workflow,
        // the modeler should have prevented this
        if (current != null && current.getPendingWorkflow() != null) {

            String ref = neu.getClass().getSimpleName() + " '" +
                    current.getName() + "'";
            if (!current.getPendingWorkflow().isAutoCreated()) {

                String msg = "Unable to start approval process for " + ref +
                    ", there is already a pending workflow";

                throw new GeneralException(msg);
            } else {

                log.warn("Found a pending workflow without an approval object for " + ref +
                ", starting a new approval process");
            }
        }

        // get rid of the current version just to be safe since
        // we're going to parse the new one again
        _context.decache();
        
        // Parse the new XML to make sure the object references
        // are in the session and can be dereferenced without lazy
        // load exceptions.   The object itself won't be in the cache
        // but the things it refrerences will be.
        // Trying to load() this up front doesn't always
        // work and there are sometimes steps in the workflow that expect
        // to be able to walk over the role and do lazy loading.  This
        // makes the object at the initial launch look the same as it would
        // if it the workflow were resumed and we parsed the variables.
        XMLObjectFactory xof = XMLObjectFactory.getInstance();
        neu = (SailPointObject)xof.parseXml(_context, xml, false);

        // this is the workflow we'll run
        String workflowRef = Configuration.WORKFLOW_ROLE_APPROVAL;

        // generate a case name
        String clsname = neu.getClass().getSimpleName();
        String name = neu.getName();
        if (neu instanceof Bundle) {
            Bundle bundle = (Bundle)neu;
            
            name = bundle.getDisplayableName();
            clsname = "Role";
        }

        String caseName = "Approve " + clsname + ": " + name;

        Workflower wf = new Workflower(_context);
        caseId = wf.launchApproval(workflowRef, caseName, 
                                   neu, invars);

        return caseId;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Modeler Approval
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch an approval workflow for an object.
     * See comments above launchApproval for requirements about the
     * arguments and the ending state.
     *
     * This is what the role modeler should call for the Save action
     * on the role editing page.
     */
    public String approve(SailPointObject neu)
        throws GeneralException {
        
        return approve(neu, null);
    }

    /**
     * Approval interface that allows launch variables to be 
     * passed in.  This is intended for unit tests to set config
     * variables but I imagine some of the object editing UIs could
     * present forms for these before saving.
     */
    public String approve(SailPointObject neu, 
                          Attributes<String,Object> invars)
        throws GeneralException {
        
        if (invars == null)
            invars = new Attributes<String,Object>();

        invars.put(RoleLibrary.VAR_APPROVAL_SOURCE,
                   RoleLibrary.APPROVAL_SOURCE_MODELER);

        return launchApproval(neu, invars);
    }

    /**
     * Called from various places to cancel an approval and/or
     * impact analysis process.
     */
    public void cancelApproval(SailPointObject obj)
        throws GeneralException {

        cancelApproval(obj.getPendingWorkflow());
    }

    public void cancelApproval(String caseId)
        throws GeneralException {

        WorkflowCase wfcase = _context.getObjectById(WorkflowCase.class, caseId);
        cancelApproval(wfcase);
    }

    public void cancelApproval(WorkflowCase wfcase)
        throws GeneralException {

        // TODO: only the person that laumched the task
        // or someone with the right SPRights should
        // be able to cancel a process

        Workflower flower = new Workflower(_context);
        flower.terminate(wfcase);
    }

    /**
     * Update the object held in an approval workflow.
     * This may be used by the modeler to allow editing
     * of the object as it goes through the approval process.
     */
    public void updateApproval(WorkflowCase wfcase, SailPointObject obj) 
        throws GeneralException {

        if (wfcase != null) {
            Workflower flower = new Workflower(_context);
            // be safe and pass down the id so we'll fetch it,
            // depending on the modeler convention obj may be detached?
            flower.updateApprovalObject(wfcase, obj);
        }
    }

    public void updateApproval(SailPointObject obj) throws GeneralException {
        updateApproval(obj.getPendingWorkflow());
    }

    /**
     * Called when the object is to be deleted.
     * Returns the id of a WorkflowCase if one was created.
     * If a workflow is not configured we delete the object.
     */
    public String approveDelete(SailPointObject obj)
        throws GeneralException {

        String caseId = null;

        // generate a case name
        String clsname = obj.getClass().getSimpleName();
        if (obj instanceof Bundle)
            clsname = "Role";

        String caseName = "Approve delete " + clsname + ": " + 
            obj.getName();

        Attributes<String,Object> invars = new Attributes<String,Object>();
        invars.put(Workflow.VAR_APPROVAL_TYPE,
                   Workflow.APPROVAL_TYPE_DELETE);

        Workflower wf = new Workflower(_context);
        caseId = wf.launchDeleteApproval(Configuration.WORKFLOW_ROLE_APPROVAL,
                                         caseName, obj, invars);

        return caseId;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Modeler Impact analysis
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch an impact analysis of the given object from the modeler.
     * See comments above launchApproval for requirements about the
     * arguments and the ending state.
     *
     * This is what the role modeler should call for the ImpacAnalysis
     * action on the role editing page.
     *
     * This is just the normal approval process passing in 
     * an "impactAnalysisOwner" name which in the standard workflows
     * will trigger the impact analysis.
     * 
     */
    public String impactAnalysis(SailPointObject neu, Identity owner)
        throws GeneralException {
        
        String caseId = null;

        if (owner == null)
            owner = ObjectUtil.getContextIdentity(_context);

        if (owner == null) 
            throw new GeneralException("Unable to determine impact analysis owner");

        // tell it to do impact analysis before approvals
        Attributes<String,Object> invars = new Attributes<String,Object>();
        invars.put(RoleLibrary.VAR_APPROVAL_SOURCE,
                   RoleLibrary.APPROVAL_SOURCE_MODELER);

        invars.put(RoleLibrary.VAR_IMPACT_ANALYSIS_OWNER, 
                   owner.getName());

        caseId = launchApproval(neu, invars);

        return caseId;
    }

    /**
     * This is a variant of the background impact analysis that will
     * be run synchronously and return results that can be displayed
     * in the modeler.  This was added primarily to show conflicts
     * between the role and sod policies but we will eventualy
     * use it for other things like finding similar roles.
     */
    public ImpactAnalysis quickImpactAnalysis(Bundle neu)
        throws GeneralException {

        Wonderer w = new Wonderer(_context);
        return w.quickAnalysis(neu);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Undirected Mining
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch an approval process for an undirected mining candidate.
     *
     * NOTE: We currnetly prompt for an approver, should we continue
     * with that or always do it in the workflow?
     */
    public String approveMiningCandidate(CandidateRole cand, Identity approver)
        throws GeneralException {

        // these don't usually come in with owners so assume
        // the approver is th eowner
        if (cand.getOwner() == null)
            cand.setOwner(approver);

        Bundle role = convert(cand);

        Attributes<String,Object> invars = new Attributes<String,Object>();
        invars.put(RoleLibrary.VAR_APPROVAL_SOURCE,
                   RoleLibrary.APPROVAL_SOURCE_UNDIRECTED);

        if (approver != null)
            invars.put(Workflow.VAR_APPROVER, approver.getName());

        return launchApproval(role, invars);
    }
    
    /**
     * Called to immediately save a mining candidiate without approvals.
     * We may want to disallow this or leave it up to the workflow.
     */
    public void commitMiningCandidate(CandidateRole cand)
        throws GeneralException {
        
        Bundle role = convert(cand);
        _context.saveObject(role);
        _context.commitTransaction();
    }

    /**
     * Convert a candidate role into a real/live Bundle/Profile/Filter.
     */
    public Bundle convert(CandidateRole cand) 
        throws GeneralException {

        Identity owner = cand.getOwner();
        if (owner != null) {
            // we don't know where this came from so freshen it
            owner = _context.getObjectById(Identity.class, owner.getId());
        }

        if (owner == null) {
            // I guess default to the context owner
            // TODO: should have an owner rule?
            owner = ObjectUtil.getContextIdentity(_context);
        }

        // The default role names generated by the classifier and
        // are not necessarily unique so we must add a qualifier.
        // The reviewer may however have entered a preferred name which
        // we don't want to qualify. 
        String name = cand.getName();
        if (cand.isDefaultName()) {
            name = "Role " + Util.ltoa(System.currentTimeMillis()) + " " + 
                cand.getName();
        }
        else {
            // Probe before we get too far so we can soften the 
            // Hibernate error.  There is still a small window where
            // someone could claim the name but that's unlikely and they
            // just get an uglier error.
            Bundle existing = _context.getObjectByName(Bundle.class, name);
            if (existing != null) {
                // This can bubble out to the UI, so localize it
                throw new GeneralException(new Message(Message.Type.Error, MessageKeys.NAME_ALREADY_EXISTS, MessageKeys.ROLE, "'"+name+"'"));
            }
        }

        Bundle role = new Bundle();
        role.setOwner(owner);
        role.setName(name);

        Map<String, String> descMap = (HashMap)cand.getAttribute(ARCHIVE_ATT_DESCRIPTIONS);
        if(!Util.isEmpty(descMap)) {
            for (Map.Entry<String, String> entry : descMap.entrySet()) {
                role.addDescription(entry.getKey(), entry.getValue());
            }
        }

        String roleType = cand.getRoleType();

        // For backwards compatibility
        if (roleType == null)
            roleType = "it";

        role.setType(roleType);

        List<CandidateRole> children = cand.getChildren();
        if (children != null && children.size() > 0) {
            // It is a cross-application role.  We're assuming that if
            // there is a child list that the top-level role can't 
            // have it's own filters.
            for (int i = 0 ; i < children.size() ; i++) {
                role.add(convertProfile(role, children.get(i)));
            }
        }
        else {
            role.add(convertProfile(role, cand));
        }

        return role;
    }

    /**
     * Convert a leaf candidate role into a Profile.
     */
    private Profile convertProfile(Bundle role, CandidateRole cand) 
        throws GeneralException {

        Profile p = null;
        
        // filter out malformed roles, don't bother throwing at this point?
        Application app = cand.getApplication();
        if (app != null) {
            // this is typically disconnected, fetch a fresh one 
            // so we can load() the Bundle before pushing it into workflow
            app = _context.getObjectById(Application.class, app.getId());
            if (app != null) {
                p = new Profile();
                // these no longer have names but take the description
                p.setDescription(cand.getDescription());
                p.setApplication(app);
                p.setConstraints(cand.getFilters());
                p.setPermissions(cand.getPermissions());
            }
        }

        return p;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification 
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch an approval process for a new role defined during
     * certification.
     * 
     * NOTE: We currnetly prompt for an approver, should we continue
     * with that or always do it in the workflow?
     */
    public String approveCertificationCandidate(CandidateRole cand, Identity approver)
        throws GeneralException {

        // these don't usually come in with owners so assume
        // the approver is the owner
        // actually this MUST be set to the role owner because that's the
        // only thing the default workflows look at?
        if (cand.getOwner() == null)
            cand.setOwner(approver);

        Bundle role = convert(cand);

        Attributes<String,Object> invars = new Attributes<String,Object>();
        invars.put(RoleLibrary.VAR_APPROVAL_SOURCE,
                   RoleLibrary.APPROVAL_SOURCE_CERTIFICATION);

        if (approver != null)
            invars.put(Workflow.VAR_APPROVER, approver.getName());

        return launchApproval(role, invars);
    }

    /**
     * Launch an approval process for a an incremental role modification
     * defined during certification.
     * 
     * The name of the candidate must match the name of an existing
     * role.  There must be no candidate hierarchy, all filter
     * or permission additions must be represented on the
     * top-level candidate.  We are only supporting additions to the
     * role, you can't take anything away yet.  If we need to support
     * that we'll probably need use something besides CandidateRole.
     *
     * NOTE: Prior to 3.0 this created a WorkItem that displayed
     * the requested changes but didn't actually do anything, the
     * approver was expected to make the changes manually.  In 3.0
     * we read the current version and merge the additions, then submit
     * that through the normal approval process.
     */
    public String approveCertificationAdditions(CandidateRole cand, Identity approver)
        throws GeneralException {
        
        // this must reference an existing role
        String roleName = cand.getName();
        Bundle role = _context.getObjectByName(Bundle.class, roleName);
        if (role == null) 
            throw new GeneralException("Unknown role " + roleName);

        // merge into the existing role
        merge(role, cand);

        Attributes<String,Object> invars = new Attributes<String,Object>();
        invars.put(RoleLibrary.VAR_APPROVAL_SOURCE,
                   RoleLibrary.APPROVAL_SOURCE_CERTIFICATION);

        if (approver != null)
            invars.put(Workflow.VAR_APPROVER, approver.getName());

        return launchApproval(role, invars);
    }

    /**
     * Merge a CandidateRole into an existing Bundle.
     */
    public void merge(Bundle role, CandidateRole cand) 
        throws GeneralException {

        List<CandidateRole> children = cand.getChildren();
        if (children != null && children.size() > 0) {
            // It is a cross-application role.  We're assuming that if
            // there is a child list that the top-level role can't 
            // have it's own filters.
            for (int i = 0 ; i < children.size() ; i++) {
                mergeProfile(role, children.get(i));
            }
        }
        else {
            mergeProfile(role, cand);
        }
    }

    /**
     * Merge a leaf candidiate role into an existing role.
     * Hmm, this is actually rather dangerous.  If there is more
     * than one profile for this application pick the first one,
     * the resulting filter could be damaged if it has complex
     * logic beyond just ANDing entitlements.
     * 
     * I guess since we're in an approval process and the approver
     * can edit the role it is relatively safe, but we might
     * want an option to do it the old way, just presenting a list
     * of the suggested changes and expecting them to be done 
     * manually?
     *
     * Hmm, perhaps it would be safer to add a NEW profile
     */
    private void mergeProfile(Bundle role, CandidateRole cand) 
        throws GeneralException {

        // filter out malformed roles, don't bother throwing at this point?
        Application app = cand.getApplication();
        if (app != null) {
            Profile target = null;
            List<Profile> profiles = role.getProfiles();
            if (profiles != null) {
                for (Profile p : profiles) {
                    if (app.equals(p.getApplication())) {
                        target = p;
                        break;
                    }
                }
            }

            // experiment with both ways
            boolean newProfileAlways = false;
                
            if (target == null || newProfileAlways) {
                target = new Profile();
                target.setApplication(app);
                target.setConstraints(cand.getFilters());
                target.setPermissions(cand.getPermissions());
                role.add(target);
            }
            else {
                List<Filter> filters = cand.getFilters();
                if (filters != null) {
                    for (Filter f : filters)
                        target.addConstraint(f);
                }
                List<Permission> perms = cand.getPermissions();
                if (perms != null) {
                    for (Permission p : perms)
                        target.addPermission(p);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Bundle Differencing
    //
    // This may still be useful with the new workflow engine, 
    // should we move it somewhere else?
    //
    //////////////////////////////////////////////////////////////////////

    // The names we display for bundle and profile attribute differences.

    public static final String ATT_NAME = "label_name";
    public static final String ATT_TYPE = "label_type";
    public static final String ATT_DESCRIPTION = "label_description";
    public static final String ATT_DISPLAY_NAME = "label_display_name";
    public static final String ATT_OWNER = "label_owner";
    public static final String ATT_SCOPE = "label_scope";
    public static final String ATT_OR_PROFILES = "label_role_or_profiles";
    public static final String ATT_WEIGHT = "label_risk_score_weight";
    public static final String ATT_ACTIVITY = "label_activity_monitoring";
    public static final String ATT_DISABLED = "label_disabled";
    public static final String ATT_INHERITANCE = "label_role_inherited";
    public static final String ATT_PERMITS = BundleDifference.ATT_PERMITS;
    public static final String ATT_REQUIREMENTS = BundleDifference.ATT_REQUIREMENTS;
    public static final String ATT_MERGE_TEMPLATES = "label_merge_templates";
    public static final String ATT_CLASSIFICATIONS = "ui_classifications";
    public static final String ATT_PROVISIONING_POLICY = "ui_role_modification_provisioning_policy";
    public static final String ATT_CAPABILITIES_LABEL = "ui_role_capabilities";
    public static final String ATT_CAPABILITIES_NAME = "Capabilities";
    public static final String ATT_AUTH_SCOPES_LABEL ="ui_role_authorized_scopes";
    public static final String ATT_AUTH_SCOPES_NAME = "Authorized Scopes";
    // should use txt_true and txt_false??
    public static final String VAL_TRUE = "true";
    public static final String VAL_FALSE = "false";

    /**
     * Compare two Bundle objects and return a simplified representation
     * of the differences for display.  We do something very similar
     * for IdentitySnapshots over in Differencer but almost all of that
     * code is involved in identity diffing so it seemed logical to 
     * keep role diffing over here.
     *
     */
    public BundleDifference diff(Bundle old, Bundle neu) 
        throws GeneralException {
        return diff(old, neu, false);
    }


    /**
     * Compare two Bundle objects and return a simplified representation
     * of the differences for display.  We do something very similar
     * for IdentitySnapshots over in Differencer but almost all of that
     * code is involved in identity diffing so it seemed logical to 
     * keep role diffing over here.  If isForDisplay is set to true,
     * the BundleDifference will contain the owner's displayable name
     * rather than the internal name
     */
    public BundleDifference diff(Bundle old, Bundle neu, boolean isForDisplay) 
        throws GeneralException {
        BundleDifference diffs = new BundleDifference();

        if (old == null) {
            if (neu != null) {
                // it's all new! should we even bother with this or
                // just display a message indiciating that this is a new 
                // thing
                
                addDiff(diffs, ATT_NAME, null, neu.getName());
                addDiff(diffs, ATT_TYPE, null, neu.getType());
                addDiff(diffs, ATT_DISPLAY_NAME, null, neu.getDisplayName());
                addDiff(diffs, ATT_DESCRIPTION, null, neu.getDescription(_locale));
                addDiff(diffs, ATT_OWNER, null, neu.getOwner());
                addDiff(diffs, ATT_SCOPE, null, neu.getAssignedScope());
                addDiff(diffs, ATT_CLASSIFICATIONS, null, neu.getClassificationDisplayNames());
                addDiff(diffs, ATT_PROVISIONING_POLICY, null, this.getProvisioningPolicyNames(neu));

                // get the capabilities change
                addDiff(diffs, ATT_CAPABILITIES_LABEL, null, this.getCapabilities(neu));

                // get the authorized scopes change
                addDiff(diffs, ATT_AUTH_SCOPES_LABEL, null, this.getAuthorizedScopes(neu));

                // won't these automatically box?

                addDiff(diffs, ATT_MERGE_TEMPLATES, null,
                        getBoolean(neu.isMergeTemplates()));

                addDiff(diffs, ATT_ACTIVITY, null, 
                        getBoolean(neu.getActivityConfig() != null));

                addDiff(diffs, ATT_OR_PROFILES, null, 
                        getBoolean(neu.isOrProfiles()));

                addDiff(diffs, ATT_WEIGHT, 
                        Util.itoa(0), Util.itoa(neu.getRiskScoreWeight()));

                // leave this out? need to think about what it means
                // to ask for approval to *disable* a bundle, the bundle
                // would have to remain enabled until after approval...
                addDiff(diffs, ATT_DISABLED, null, 
                        getBoolean(neu.isDisabled()));

                // extended attributes
                List<ObjectAttribute> atts = getExtendedRoleAttributes();
                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        if (Util.nullSafeEq(att.getType(),PropertyInfo.TYPE_RULE)) {
                            //IIQCB-2595 Rule attribute has id as value, when it gets stringified, it display rule id instead of rule name
                            //This is a workaround to display the rule name in role differences
                            String ruleName = "";
                            if((String)neu.getAttribute(att.getName()) != null) {
                                ruleName = ObjectUtil.getName(_context, Rule.class, (String)neu.getAttribute(att.getName()));
                            }
                            addDiff(diffs, att.getDisplayableName(), "", ruleName);
                        }
                        else {
                            addDiff(diffs, att.getDisplayableName(), null,  
                                    neu.getAttribute(att.getName()));
                        }
                    }
                }

                // relationships
                addDiff(diffs, ATT_INHERITANCE, null, neu.getInheritance());
                addDiff(diffs, ATT_PERMITS, null, neu.getPermits());
                addDiff(diffs, ATT_REQUIREMENTS, null, neu.getRequirements());

                addProfileDiffs(diffs, null, neu);

                IdentitySelector sel = neu.getSelector();
                if (sel != null) {
                    Difference diff = new Difference();
                    diff.setNewValue(sel.generateSummary());
                    diffs.setSelectorDifference(diff);
                }
            }
        }
        else if (neu == null) {
            // this is the delete case, I'm not going to bother with
            // a Difference representation here since this should be
            // presented in a different way.
        }
        else {
            addDiff(diffs, ATT_NAME, old.getName(), neu.getName());
            addDiff(diffs, ATT_TYPE, old.getType(), neu.getType());
            addDiff(diffs, ATT_DISPLAY_NAME, old.getDisplayName(), neu.getDisplayName());
            addDiff(diffs, ATT_DESCRIPTION, old.getDescription(_locale), neu.getDescription(_locale));
            addDiff(diffs, ATT_CLASSIFICATIONS, old.getClassificationDisplayNames(), neu.getClassificationDisplayNames());
            // get the provisioning policy form changes
            addDiff(diffs, ATT_PROVISIONING_POLICY, this.getProvisioningPolicyNames(old), this.getProvisioningPolicyNames(neu));

            // get the capabilities change
            addDiff(diffs, ATT_CAPABILITIES_LABEL, this.getCapabilities(old), this.getCapabilities(neu));

           // get the authorized scopes change
            addDiff(diffs, ATT_AUTH_SCOPES_LABEL, this.getAuthorizedScopes(old), this.getAuthorizedScopes(neu));

            // Get the displayable name if we're presenting this through the UI.
            // Otherwise the name is ultimately used
            Identity oldOwner = old.getOwner();
            Identity newOwner = neu.getOwner();
            if (isForDisplay) {
                addDiff(diffs, ATT_OWNER, oldOwner.getDisplayableName(), newOwner.getDisplayableName());
            } else {
                addDiff(diffs, ATT_OWNER, old.getOwner(), neu.getOwner());
            }

            addDiff(diffs, ATT_SCOPE, 
                    old.getAssignedScope(),
                    neu.getAssignedScope());

            addDiff(diffs, ATT_ACTIVITY, 
                    getBoolean(old.getActivityConfig() != null), 
                    getBoolean(neu.getActivityConfig() != null));

            addDiff(diffs, ATT_OR_PROFILES, 
                    getBoolean(old.isOrProfiles()),
                    getBoolean(neu.isOrProfiles()));

            addDiff(diffs, ATT_WEIGHT,
                    Util.itoa(old.getRiskScoreWeight()), 
                    Util.itoa(neu.getRiskScoreWeight()));

            addDiff(diffs, ATT_DISABLED, 
                    getBoolean(old.isDisabled()),
                    getBoolean(neu.isDisabled()));

            addDiff(diffs, ATT_MERGE_TEMPLATES,
                    getBoolean(old.isMergeTemplates()),
                    getBoolean(neu.isMergeTemplates()));

            // extended attributes
            List<ObjectAttribute> atts = getExtendedRoleAttributes();
            if (atts != null) {
                for (ObjectAttribute att : atts) {
                    if (Util.nullSafeEq(att.getType(), PropertyInfo.TYPE_RULE)) {
                        //IIQCB-2595 Rule attribute has id as value, when it gets stringified, it display rule id instead of rule name
                        //This is a workaround to display the rule name in role differences
                        String newRuleName = "";
                        String oldRuleName = "";
                        if ((String)neu.getAttribute(att.getName()) != null) {
                            newRuleName = ObjectUtil.getName(_context, Rule.class, (String)neu.getAttribute(att.getName()));
                        }
                        if ((String)old.getAttribute(att.getName()) != null) {
                            oldRuleName = ObjectUtil.getName(_context, Rule.class, (String)old.getAttribute(att.getName()));
                        }
                        addDiff(diffs, att.getDisplayableName(), oldRuleName, newRuleName);
                    }
                    else {
                        addDiff(diffs, att.getDisplayableName(), 
                                old.getAttribute(att.getName()),
                                neu.getAttribute(att.getName()));
                    }
                }
            }

            // relationships
            addDiff(diffs, ATT_INHERITANCE, old.getInheritance(), neu.getInheritance());
            addDiff(diffs, ATT_PERMITS, old.getPermits(), neu.getPermits());
            addDiff(diffs, ATT_REQUIREMENTS, old.getRequirements(), neu.getRequirements());

            addProfileDiffs(diffs, old, neu);

            // assignment selector
            IdentitySelector oldSel = old.getSelector();
            IdentitySelector newSel = neu.getSelector();

            String oldSelText = (oldSel != null) ? oldSel.generateSummary() : null;
            String newSelText = (newSel != null) ? newSel.generateSummary() : null;
            
            Difference diff = Difference.diff(oldSelText, newSelText);
            diffs.setSelectorDifference(diff);
        }

        return diffs;
    }

    private List<String> getProvisioningPolicyNames(Bundle bundle) {
        List<String> policyNames = new ArrayList<String>();
        List<Form> provisioningForms = bundle.getProvisioningForms();
        if (!Util.isEmpty(provisioningForms)) {
            for (Form form : provisioningForms) {
                policyNames.add(form.getName());
            }
        }
        return policyNames;
    }

    /**
     * get capability list
     * @param bundle
     * @return
     * @throws GeneralException 
     */
    private List<String> getCapabilities(Bundle bundle) throws GeneralException {
        List<String> capabilities = new ArrayList<String>();
        List<AccountRequest> accountRequests;
        List<AttributeRequest> attributeRequests;
        if(bundle.getProvisioningPlan() != null && bundle.getProvisioningPlan().getAccountRequests() != null) {
            //assume bundle provisioning plan account request always adds capabilities
            accountRequests = bundle.getProvisioningPlan().getAccountRequests();
            for(AccountRequest accountRequest : accountRequests) {
                attributeRequests = accountRequest.getAttributeRequests(ATT_CAPABILITIES_NAME);
                for(AttributeRequest attributeRequest : attributeRequests) {
                    Object value = attributeRequest.getValue(_context);
                    capabilities.addAll(new ArrayList<String>((List<String>)value));
                }
            }
        }
        return capabilities;
    }

    /**
     * get authorized scope list
     * @param bundle
     * @return
     * @throws GeneralException 
     */
    private List<String> getAuthorizedScopes(Bundle bundle) throws GeneralException {
        List<String> scopes = new ArrayList<String>();
        List<String> scopeIds = new ArrayList<String>();
        List<AccountRequest> accountRequests;
        List<AttributeRequest> attributeRequests;
        if(bundle.getProvisioningPlan() != null && bundle.getProvisioningPlan().getAccountRequests() != null) {
            //assume bundle provisioning plan account request always adds capabilities
            accountRequests = bundle.getProvisioningPlan().getAccountRequests();
            for(AccountRequest accountRequest : accountRequests) {
                attributeRequests = accountRequest.getAttributeRequests(ATT_AUTH_SCOPES_NAME);
                for(AttributeRequest attributeRequest : attributeRequests) {
                    Object value = attributeRequest.getValue(_context);
                    scopeIds.addAll(new ArrayList<String>((List<String>)value));
                }
            }
            for(String id : scopeIds) {
                Scope scope = _context.getObjectById(Scope.class, id);
                if (scope != null) {
                    scopes.add(scope.getName());
                }
            }
        }
        return scopes;
    }

    private List<ObjectAttribute> getExtendedRoleAttributes() {
        List<ObjectAttribute> atts = null;
        ObjectConfig config = Bundle.getObjectConfig();
        if (config != null)
            atts = config.getObjectAttributes();
        return atts;
    }

    private String getBoolean(boolean b) {
        return (b) ? VAL_TRUE : VAL_FALSE;
    }

    /**
     * We have historically considered null and the empty string
     * to be equal, but Difference hasn't.  Consder adding that
     * to Difference so we do it consistently everywhere.
     *
     * SailPointObjects will be converted into names within
     * the Difference.
     */
    private void addDiff(BundleDifference diffs, String attname, 
                         Object old, Object neu) {

        if ((old instanceof String) && (((String)old).length() == 0))
            old = null;

        if ((neu instanceof String) && (((String)neu).length() == 0))
            neu = null;

        Difference diff = Difference.diff(old, neu);
        if (diff != null) {
            diff.setAttribute(attname);
            diffs.add(diff);
        }
    }

    /**
     * To compare the profile lists on a bundle, convert them
     * to a sort list of names then use the Difference utility
     * to generate the diff with old/new values enumerated.
     */
    private void addProfileDiffs(BundleDifference diffs, 
                                 Bundle old, Bundle neu) 
        throws GeneralException {

        if (old != null) {
            List<Profile> oldProfiles = old.getProfiles();
            if (oldProfiles != null) {
                for (Profile p : oldProfiles) {
                    Profile newp = null;
                    if (neu != null)
                        newp = neu.findProfile(p.getId());
                    ProfileDifference pd = diff(p, newp);
                    if (pd.hasDifferences())
                        diffs.add(pd);
                }
            }
        }

        if (neu != null) {
            List<Profile> newProfiles = neu.getProfiles();
            if (newProfiles != null) {
                for (Profile newp : newProfiles) {
                    Profile oldp = null;
                    if (old != null)
                        oldp = old.findProfile(newp.getId());
                    // we will already have diffed old ones, 
                    // only need the new ones
                    if (oldp == null)
                        diffs.add(diff(null, newp));
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Profile Differencing
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ATT_APPLICATION = "Application";

    /**
     * We are assumign that you can't change the application of a profile
     * without deleting it and creating a new one, but go ahead
     * and detect.
     */
    public ProfileDifference diff(Profile old, Profile neu) 
        throws GeneralException {

        ProfileDifference diffs = new ProfileDifference();

        if (old == null) {
            if (neu != null) {
                diffs.setApplication(neu.getApplication());
                addDiff(diffs, ATT_DESCRIPTION, null, neu.getDescription());
                addDiff(diffs, ATT_APPLICATION, null, neu.getApplication());

                addDiff(diffs, null, neu.getConstraints());
                
                diffs.setPermissionDifferences(diff(null, neu.getPermissions()));
            }
        }
        else if (neu == null) {
            diffs.setApplication(old.getApplication());
            addDiff(diffs, ATT_DESCRIPTION, old.getDescription(), null);
            addDiff(diffs, ATT_APPLICATION, old.getApplication(), null);

            addDiff(diffs, old.getConstraints(), null);

            diffs.setPermissionDifferences(diff(old.getPermissions(), null)); 
        }
        else {
            diffs.setApplication(old.getApplication());
            addDiff(diffs, ATT_DESCRIPTION, 
                    old.getDescription(), neu.getDescription());
            addDiff(diffs, ATT_APPLICATION, 
                    old.getApplication(), neu.getApplication());

            addDiff(diffs, old.getConstraints(), neu.getConstraints());

            diffs.setPermissionDifferences(diff(old.getPermissions(),
                                                neu.getPermissions()));
        }

        return diffs;
    }

    /**
     * We have historically considered null and the empty string
     * to be equal, but Difference hasn't.  Consder adding that
     * to Difference so we do it consistently everywhere.
     *
     * SailPointObjects will be converted into names within
     * the Difference.
     */
    private void addDiff(ProfileDifference diffs, String attname, 
                         Object old, Object neu) {

        if ((old instanceof String) && (((String)old).length() == 0))
            old = null;

        if ((neu instanceof String) && (((String)neu).length() == 0))
            neu = null;

        Difference diff = Difference.diff(old, neu);
        if (diff != null) {
            diff.setAttribute(attname);
            diffs.add(diff);
        }
    }

    private void addDiff(ProfileDifference diffs,
                         List<Filter> old, List<Filter> neu) {

        // for simplicity combine these into a single filter
        Filter oldRoot = simplify(old);
        Filter newRoot = simplify(neu);

        // and diff the text
        // note that we're using the new renderer that generates
        // a more SQL-ish syntax, though it probably isn't parsable
        FilterRenderer ren = new FilterRenderer();
        String oldText = ren.render(oldRoot);
        String newText = ren.render(newRoot);
        
        // this is stored outside the attribute diff list so we
        // can more easily get to it and display it differently
        Difference diff = Difference.diff(oldText, newText);
        if (diff != null)
            diffs.setFilterDifference(diff);
    }

    /**
     * Collapse a list of filters into a single filter with the
     * list elements AND'd.
     */
    private Filter simplify(List<Filter> filters) {

        Filter root = null;
        if (filters != null) {
            for (Filter f : filters) {
                if (root == null)
                    root = f;
                else 
                    root = Filter.and(root, f);
            }
        }
        return root;
    }

    /**
     * Compare two permission lists.
     * This is similar to code over in Differencer but we generate
     * a slightly different model (Difference rather than 
     * PermissionDifference).  We generate a simpler model that 
     * shows right differences as a CSV rather than blown out
     * into several PermissionDifference objects.  
     */
    private List<Difference> diff(List<Permission> oldPerms, 
                                  List<Permission> newPerms) {

        List<Difference> diffs = new ArrayList<Difference>();

        if (oldPerms == null) {
            if (newPerms != null) {
                for (Permission np : newPerms)
                    diffPermission(diffs, null, np);
            }
        }
        else if (newPerms == null) {
            if (oldPerms != null) {
                for (Permission op : oldPerms)
                    diffPermission(diffs, op, null);
            }
        }
        else {
            // matching permissions assumes that there can be only
            // one Permission object per target
            oldPerms = new ArrayList<Permission>(oldPerms);
            for (Permission np : newPerms) {
                Permission op = getPermission(oldPerms, np);
                diffPermission(diffs, op, np);
                if (op != null)
                    oldPerms.remove(op);
            }
            for (Permission op : oldPerms)  
                diffPermission(diffs, op, null);
        }

        return (diffs.size() > 0) ? diffs : null;
    }

    /**
     * Search a list for a Permission with a matching target.
     */
    private Permission getPermission(List<Permission> perms, 
                                     Permission src) {
        Permission found = null;
        if (perms != null && src != null) {
            String target = src.getTarget();
            if (target != null) {
                for (Permission p : perms) {
                    if (target.equals(p.getTarget())) {
                        found = p;
                        break;
                    }
                }
            }
        }
        return found;
    }

    private void diffPermission(List<Difference> diffs,
                                Permission p1, Permission p2) {

        String target = null;
        List<String> oldRights = null;
        List<String> newRights = null;

        if (p1 != null) {
            target = p1.getTarget();
            oldRights = p1.getRightsList();
            Collections.sort(oldRights);
        }
        if (p2 != null) {
            target = p2.getTarget();
            newRights = p2.getRightsList();
            Collections.sort(newRights);
        }

        Difference diff = Difference.diff(oldRights, newRights);
        if (diff != null) {
            diff.setAttribute(target);
            diffs.add(diff);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Archival
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by RoleLibrary when it wants to commit a role
     * change and archive the previous version.
     * 
     * Role comes from the workflow case and is not yet in the session.
     *
     * NOTE WELL: We have to use ObjectUtil.recache to avoid Hibernate
     * cache problems if this role has references to other objects.
     * While the Bundle won't be in the session, things it references
     * may be and we have to reswizzle those after we do a decache()
     * during the archival process.
     */
    public void save(Bundle role, String creator, boolean archive)
        throws GeneralException {

        // Grab the role's current ID.
        // This should be null for new roles.
        String id = role.getId();
        List<Profile> deletedProfiles = null;

        // If no ID, this is likely a new role.
        // Save the object to assign an ID. This is mostly for the
        // benefit of event generator, so it can save the role's ID
        // when creating an event.
        if (id == null) {
            _context.saveObject(role);
        }

        _eventGenerator.scheduleRoleChanges(role);

        // If the role originally had an ID, then it isn't new. Archive if necessary.
        // Note that this isn't the role's current ID, so this won't run if we
        // just saved a new role.
        if (id != null) {
            if ( archive ) {
                // TODO: should check for significant differences before rolling...
                archive(id, creator);

                // archive did a decache() have to reswizzle
                role = (Bundle)ObjectUtil.recache(_context, role);
            }
            
            deletedProfiles = getDeletedProfiles(id, role);
        }
        
        role.setPendingWorkflow(null);
        _context.saveObject(role);

        Describer describer = new Describer((Describable)role);
        describer.saveLocalizedAttributes(_context);
        _context.commitTransaction();

        // Gotta delete any profiles that were removed from the Role
        if (!Util.isEmpty(deletedProfiles)) {
            Terminator t = new Terminator(_context);
            for (Profile p : deletedProfiles) {
                t.deleteObject(p);
            }
        }
    }
    
    /**
     * Compare the modified role with the existing one and return a 
     * list of removed profiles which should be deleted.
     * 
     */
    private List<Profile> getDeletedProfiles(String id, Bundle newRole) 
        throws GeneralException {
        List<Profile> deletedProfiles = null;
        Bundle oldRole = _context.getObjectById(Bundle.class, id);
        if (oldRole != null && oldRole.getProfiles() != null) {
            for (Profile p : oldRole.getProfiles()) {
                if (newRole.findProfile(p.getId()) == null) {
                    if (deletedProfiles == null) {
                        deletedProfiles = new ArrayList<Profile>();
                    }
                    deletedProfiles.add(p);
                }
            }
        }
        
        return deletedProfiles;
    }

    /**
     * Internal helper to capture an archive of a role.
     * This has to commit and clear cache to get around the usual
     * Hibernate crap about having two versions of the same object
     * in the session. 
     */
    private BundleArchive archive(String id, String creator)
        throws GeneralException {
        
        BundleArchive arch = null;
        
        Bundle current = _context.getObjectById(Bundle.class, id);
        if (current == null) {
            // not supposed to happen, must have been deleted
            // out from under the workflow, just proceed with the save
            log.warn("Role not found for archiving!");
        }
        else {
            arch = new BundleArchive(current);

            // normally the workflow case owner
            if (creator == null)    
                creator = _context.getUserName();
            arch.setCreator(creator);

            // should we bother with a rolling version number
            // or just use the creation date?  Could keep the 
            // version number in the Bundle to save a db hit
            int version = getPreviousVersionNumber(current);
            version++;
            arch.setVersion(version);

            _context.saveObject(arch);
            _context.commitTransaction();

            // MUST get the current version out of the cache!
            _context.decache();
        }

        return arch;
    }

    /**
     * Setup a search for version info for a given role.
     * Returned columns will be: id, version, created, creator
     * The result will be sorted by version descending.
     */
    private Iterator<Object[]> searchArchives(Bundle role)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.addOrdering("version", false);
        ops.add(Filter.eq("sourceId", role.getId()));

        List<String> props = new ArrayList<String>();
        props.add("id");
        props.add("version");
        props.add("created");
        props.add("creator");
        
        Iterator<Object[]> result = _context.search(BundleArchive.class, ops, props);

        return result;
    }
    
    /**
     * Return version history info as an ordered list of sparse
     * BundleArchive objects.  Avoids having to drag in the XML which
     * we generally don't need until a specific version is selected.
     * We need to convey the version, created, and creator values somehow
     * so making transient instanes of this class is as good as anything.
     *
     * Intended for use in the modeler to display a table of versions
     * for browsing.
     */
    public List<BundleArchive> getArchiveInfo(Bundle role)
        throws GeneralException {
        
        List<BundleArchive> archives = null;
        Iterator<Object[]> result = searchArchives(role);
        while (result.hasNext()) {
            Object[] row = result.next();
            if (archives == null)
                archives = new ArrayList<BundleArchive>();
            BundleArchive arch = new BundleArchive();
            archives.add(arch);

            arch.setId((String)row[0]);
            arch.setVersion((Integer)row[1]);
            arch.setCreated((Date)row[2]);
            arch.setCreator((String)row[3]);

        }

        return archives;
    }

    /**
     * Find the most recent version number.
     * Could use hql to find the max() version, but we're not expecting
     * too many of these so just reuse searchArchives.
     */
    public int getPreviousVersionNumber(Bundle role)
        throws GeneralException {
        
        int number = 0;

        Iterator<Object[]> result = searchArchives(role);
        if (result.hasNext()) {
            Integer version = (Integer)(result.next()[1]);
            if (version != null)
                number = version.intValue();
        }
        
        return number;
    }

    /**
     * Load the most recent archive.
     */
    public BundleArchive getPreviousVersion(Bundle role)
        throws GeneralException {
        
        BundleArchive arch = null;

        Iterator<Object[]> result = searchArchives(role);
        if (result.hasNext()) {
            String id = (String)(result.next()[0]);
            arch = _context.getObjectById(BundleArchive.class, id);
        }
        
        return arch;
    }

    /**
     * Extract the archived Bundle.
     */
    public Bundle rehydrate(BundleArchive arch)
        throws GeneralException {

        String xml = arch.getArchive();
        if (xml == null) {
            // this may be one of the "info" archives returned
            // by getArchiveInfo, get the real one
            arch = _context.getObjectById(BundleArchive.class, arch.getId());
            xml = arch.getArchive();
        }

        if (xml == null)
            throw new GeneralException("Role archive empty");

        XMLObjectFactory factory = XMLObjectFactory.getInstance();
        Object obj = factory.parseXml(_context, xml, false);
        if (!(obj instanceof Bundle))
            throw new GeneralException("Invalid object in archive");

        return (Bundle)obj;
    }

    /**
     * Roll back to a previous version.
     * 
     * The when the archiver value is non-null, it is a signal that
     * the current object should be archived before we overwrite it with
     * the older version.  When archiver is null we simply restore the 
     * old version without creating a new one.
     *
     * This would be used from the modeler if you wanted to immediately
     * restore a version without going through a workflow process.
     */
    public void rollback(BundleArchive arch, String archiver)
        throws GeneralException {

        Bundle old = rehydrate(arch);

        // optionally save the current version
        if (archiver != null) {
            archive(arch.getSourceId(), archiver);
            // archive did a decache() have to reswizzle
            old = (Bundle)ObjectUtil.recache(_context, old);
        }

        // !! what about the name, should we restore the
        // old definition but keep the new name, or restore
        // everything?
        _context.saveObject(old);

        // Roll back the descriptions
        Describer describer = new Describer((Describable)old);
        describer.saveLocalizedAttributes(_context);
        _context.commitTransaction();
    }

    /**
     * Roll back to the most recent version.
     * This would be called by the modeler if you just wanted
     * a little "revert to previous version" and no workflow.
     */
    public void rollback(Bundle role, String archiver)
        throws GeneralException {

        BundleArchive arch = getPreviousVersion(role);
        if (arch != null)
            rollback(arch, archiver);
    }

    /**
     * Launch a workflow to approve an archive restoration.
     * This would be used by the modeler instead of restoreArchive
     * if you want workflow involved.  This is probably the best choice.
     *
     * Current SailPointContext owner will become the workflow "launcher"
     * and eventually the archive "creator".
     *
     * This ends up looking almost identical to the edit approval
     * workflow launched with approve() but the case name is different
     * and we set VAR_APPROVAL_TYPE to APPROVAL_TYPE_ROLLBACK.  The old
     * version is rehydrated and put into the workflow so the approver
     * can inspect it like we do for normal edits.
     */
    public String approveRollback(BundleArchive arch)
        throws GeneralException {

        String caseId = null;

        // get current version
        Bundle current = _context.getObjectById(Bundle.class, arch.getSourceId());
        if (current == null) {
            // I suppose we could allow this as a delete rollback?
            // But when would we ever prune the old versions?
            throw new GeneralException("Role does not exist: " + arch.getName());
        }

        // can have only one outstanding workflow,
        // the modeler should have prevented this
        if (current.getPendingWorkflow() != null) {
            String msg = "Role already has a pending workflow: " + 
                current.getName();
            throw new GeneralException(msg);
        }

        // this is the workflow we'll run
        String workflowRef = Configuration.WORKFLOW_ROLE_APPROVAL;

        // generate a case name
        String caseName = "Approve rollback of role: " + 
            current.getName();

        Attributes<String,Object> invars = new Attributes<String,Object>();
        invars.put(RoleLibrary.VAR_APPROVAL_SOURCE,
                   RoleLibrary.APPROVAL_SOURCE_MODELER);
        invars.put(Workflow.VAR_APPROVAL_TYPE,
                   Workflow.APPROVAL_TYPE_ROLLBACK);

        // this won't put the object into the Hibernate session cache, 
        // so I don't think we need to evict current first
        Bundle old = rehydrate(arch);

        Workflower wf = new Workflower(_context);
        caseId = wf.launchApproval(workflowRef, caseName, 
                                   old, invars);

        return caseId;
    }
}
