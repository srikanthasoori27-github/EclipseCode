/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for editing Generic and SOD policies.
 *
 * Author: Jeff
 *
 * This evolved from the original PolicyBean which used
 * the traditional approach of more-or-less direct editing
 * of an attached SailPointObject.  Starting in 3.0 we moved
 * to a DTO model for editing which solves a number of thorny
 * Hibernate issues and is generally cleaner, though it does result
 * in some model duplication.
 *
 * PolicyBean is still being used forb Activity policies so
 * we can't delete it just yet.
 *
 * Some old notes on the Hibernate issues:
 *
 *   - Keeping the object attached to a Hibernate session
 *     causes ids for new objects to be generated that can't
 *     be committed in a different session.  This is commonly the
 *     case when you edit the object over several HTTP requests.
 *     This requires you to maintain a list of all new objects and
 *     null their ids at the start of every request before they
 *     are attached.
 *
 *   - Keeping the object detached means you lose the Hibernate
 *     session's ability to maintain single copies of referenced
 *     objects such as Applications.  As you add new references
 *     to the policy you have to always check to see if there
 *     is another Java object somewhere in the tree and use that
 *     one, otherwise you get the "multiple representations of
 *     the same object" exception during commit.
 *
 *   - Maintaining object references as SailPointObjects
 *     is awkward in JSF, it is much easier to represent
 *     references as strings (names or unique ids) during editing,
 *     then convert them back into SailPointObjects when we're
 *     ready to commit.  This usually causes us to have a bean
 *     wrapper around any object that contains a reference. So we
 *     rarely directly edit the SailPointObject anyway, we end up
 *     with partial DTO's.
 *
 *   - We are likely to start having problems with clustering
 *     if we rely on SailPointObjects to transfer themselves
 *     between machines with native Java serialization.  This probably
 *     needs to be resolved but a simpler DTO model can use default
 *     Java serialization, it doesn't have to rely on XML.
 *     Hmm, well maybe making these AbstractXmlObjects would actually
 *     be easier?
 *
 */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Describer;
import sailpoint.api.IdentityService;
import sailpoint.api.Localizer;
import sailpoint.api.PolicyImpactAnalysisCalculator;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Policy;
import sailpoint.object.Policy.State;
import sailpoint.object.Policy.ViolationOwnerType;
import sailpoint.object.PolicyImpactAnalysis;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Parser.ParseException;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;

public class PolicyBean extends BaseObjectBean<Policy> {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(PolicyBean.class);

    /**
     * The base name of the HttpSession attribute where we store the
     * extended editing state.
     */
    public static final String ATT_SESSION = "NewPolicySession";

    /**
     * The name of the GridState in the identity preferences
     * for the SOD constraint grid.  We have not historically
     * maintained grid state for any of the other policies, why?
     */
    public static final String PROP_GRID_STATE = "sodConstraintListGridState";

    /**
     * Items for the policy state selector.
     */
    SelectItem[] _policyStates;

    /**
     * Cached list of roles for SOD policies.
     */
    SelectItem[] _businessRoles;

    /**
     * The editing state saved on the HttpSession.
     */
    PolicySession _session;

    /**
     * Transient field holding the value of the role selector in
     * the Role SOD policy page. Set prior to calling the left/right
     * add action handlers.
     */
    String _role;

    /**
     * parameter to indicate whether request is for policy or constraint
     */
    String isPolicy;

    /**
     * Grid preferences for SOD policy constraints.
     */
    public GridState _gridState;

    private Localizer localizer;

    /**
     * result id that we are going to persist on rule or policy objects as argument.
     */
    public static final String TASK_RESULT_ID = "taskResultId";

    /**
     * parameter to indicate whether request is for policy or constraint
     */
    public static final String IS_POLICY = "isPolicy";

    /**
     * parameter to indicate whether request is for policy or constraint
     */
    public static final String CONSTRAINT_ID = "constraintId";

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyBean() {
        super();
        setScope(Policy.class);

        // jsl - started fully loading the objects in 3.0 so
        // we don't have to mess with attaching issues, this
        // should simplify a number of kludges in the other
        // policy beans
        setNeverAttach(true);

        restoreObjectId();

        // jsl - this is what SODPolicyBean has been doing
        // for some time, is it appropriate for all?

        // If forceLoad is on clear out the extended editing state.
        // BaseObjectBean handles the object being edited but does not
        // know about the extended state.  Note though that it is
        // important NOT to trash the ATT_OBJECT_ID that PolicyListBean
        // will have set.  policy.xhtml will instantiate two beans,
        // GenericPolicyBean to redirect to the proper config page,
        // then another bean to manage that page.  The second bean
        // needs to find ATT_OBJECT_ID as well.
        // Hmm, since we normally load the object in the first bean
        // why can't we just find that on the HttpSession in the
        // second bean?  Something in BaseObjectBean doesn't like that.

        Map ses = getSessionScope();
        boolean forceLoad = Util.otob(ses.get(FORCE_LOAD));
        if (forceLoad)
            cancelSession();
    }

    /**
     * Locate the unique id of the object we are editing.
     *
     * NOTE: I'm tired of fighting with redirect in faces-config,
     * pages may simply pass the id on the _session.  If not there
     * fall back to the common post parameter conventions.
     */
    protected void restoreObjectId() {

        Map ses = getSessionScope();

        // list page will set this initially, thereafter we keep it
        // refreshed as we transition among our pages
        String id = (String)ses.get(PolicyListBean.ATT_OBJECT_ID);
        if (id == null) {
            // the other convention on some pages
            Map map = getRequestParam();
            id = (String) map.get("selectedId");
            if (id == null)
                id = (String) map.get("policyForm:currentObjectId");
        }
        setObjectId(id);

        localizer = new Localizer(getContext(), id);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Session Management
    //
    //////////////////////////////////////////////////////////////////////

    public PolicySession getSession() {
        if (_session == null) {
            try {
                Map hsession = getSessionScope();
                _session = (PolicySession)hsession.get(ATT_SESSION);
                if (_session == null) {

                    // subclass must overload this
                    _session = createSession();

                    // save this immediately so any caches created by
                    // the constructor will be saved
                    saveSession();
                }
            }
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error("Unable to restore policy session. Exception: " + t.getMessage(), t);
            }
        }
        return _session;
    }

    public boolean isSupportsTargetPermissions() throws GeneralException {
        boolean result = false;
        Policy p = getObject();
        if (p != null) {
            String type = p.getType();
            result= Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD.equals(type);
        }
        return result;
    }

    /**
     * Any need to override this?
     */
    protected PolicySession createSession() throws GeneralException {

        return new PolicySession(getObject());

    }

    public void saveSession() {
        // BaseObjectBean should handle the root object
        Map ses = getSessionScope();
        ses.put(PolicyListBean.ATT_OBJECT_ID, getObjectId());
        ses.put(ATT_SESSION, _session);
    }

    public void cancelSession() {
        // BaseObjectBean should handle the root object
        Map ses = getSessionScope();
        ses.remove(ATT_SESSION);

        // NO! see comments in the constructor about why
        // this has to be preserved
        //ses.remove(PolicyListBean.ATT_OBJECT_ID);
    }

    /**
     * Overload from BaseObjectBean called to create a new object.
     */
    public Policy createObject() {

        Policy newPolicy = null;

        // we saved the type name though now that we have template
        // objects it would be somewhat more conveneint to save the
        // template id
        Map session = getSessionScope();
        String type = (String)session.get(PolicyListBean.ATT_POLICY_TYPE);
        if (type != null) {
            Policy template = null;
            SailPointContext con = getContext();
            try {
                List<Policy> templates = PolicyListBean.getPolicyTemplates(con);
                if (templates != null) {
                    for (Policy p : templates) {
                        if (type.equals(p.getType())) {
                            template = p;
                            break;
                        }
                    }
                    if (template != null)
                        newPolicy = template.derive(con);
                    else
                        log.error("No policy template for " + type);
                }
            }
            catch (Throwable t) {
                log.error("Unable to create object: " + t.toString());
            }
        }

        // have to have something...
        if (newPolicy == null)
            newPolicy = new Policy();

        return newPolicy;
    }

    /**
     * Fish a GridState out of the current user.
     * This is generic, should be on BaseObjectBean?
     */
    public GridState loadGridState(String gridName) {
        GridState state = null;
        String name = "";
        IdentityService iSvc = new IdentityService(getContext());
        try {
            if(gridName!=null)
                state = iSvc.getGridState(getLoggedInUser(), gridName);
        } catch(GeneralException ge) {
            log.info("GeneralException encountered while loading gridstates: "+ge.getMessage());
        }

        if(state==null) {
            state = new GridState();
            state.setName(name);
        }
        return state;
    }

    /**
     * Get the GridState for SOD policy constraints.
     */
    public GridState getGridState() {
        if (_gridState==null) {
            _gridState = loadGridState(PROP_GRID_STATE);
        }
        return _gridState;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by the outermost page (policy.xhtml) to get the
     * type-specific included page.
     */
    public String getConfigPage() {
        String page = "genericpolicy.xhtml";
        try {
            Policy p = getObject();
            if (p != null) {
                String specificPage = p.getConfigPage();
                if (specificPage != null)
                    page = specificPage;
            }
            else {
                // can this happen?
                // prior to 3.0 we would look at the posted ATT_POLICY_TYPE
                // and get it from the enumeration but we should have
                // either fetched or created a Policy object from
                // a template by now?
                log.error("Config page requested before object created!");
            }
        }
        catch (Throwable t) {
            log.error(t.toString());
        }
        return page;
    }

    /**
     * Most pages use bean.object to get to the object being edited.
     * But policy pages have to use this!
     * I'm not sure it's wise to overload getObject to return
     * a different type?
     */
    public PolicyDTO getPolicy() {
        PolicySession session = getSession();
        return session.getPolicy();
    }

    /**
     * Hmm, does policy.dto this look better than policy.policy?
     */
    public PolicyDTO getDto() {
        PolicySession session = getSession();
        return session.getPolicy();
    }

    /**
     * This is used by the constraints data source.
     */
    public List<BaseConstraintDTO> getConstraints() {

        PolicyDTO p = getPolicy();
        return p.getConstraints();
    }

    public int getConstraintCount() {

        List<BaseConstraintDTO> cons = getConstraints();
        return (cons != null) ? cons.size() : 0;
    }

    /**
     * For the original simple pages this returns the first constraint.
     */
    public BaseConstraintDTO getSimpleConstraint() {

        PolicyDTO p = getPolicy();

        // this may be null, the page needs to conditionalize itself
        BaseConstraintDTO con = p.getConstraint();

        return con;
    }

    public SelectItem[] getPolicyStates() {

        if (_policyStates == null) {
            _policyStates = new SelectItem[2];
            _policyStates[0] = new SelectItem(Policy.State.Inactive.toString(),
                                              getMessage(MessageKeys.POLICY_STATUS_INACTIVE));
            _policyStates[1] = new SelectItem(Policy.State.Active.toString(),
                                              getMessage(MessageKeys.POLICY_STATUS_ACTIVE));
        }

        return _policyStates;
    }

    public String getSelectedRole() {
        return _role;
    }

    public void setSelectedRole(String s) {
        _role = s;
    }

    public String getIsPolicy() {
        return isPolicy;
    }

    public void setIsPolicy(String isPolicy) {
        this.isPolicy = isPolicy;
    }

    /**
     * Return the list of roles that may be added to a role SOD constraint.
     * Not doing any filtering here, will filter when the selection is added.
     * If we do want filtering, then we'll need two properties, one for each side.
     * !! Should  be using an Ext suggest for this.
     */
    public SelectItem[] getRoles() throws GeneralException {

        PolicySession session = getSession();
        return session.getRoles(getContext());
    }

    public String getConstraintJson()  {

        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, Object>> constraintRows = new ArrayList<Map<String, Object>>();
        List<BaseConstraintDTO> constraintDTOs = getConstraints();
        if (constraintDTOs != null) {
            for (BaseConstraintDTO constraint : constraintDTOs) {
                constraintRows.add(constraint.getJsonMap());
            }
        }

        response.put("totalCount", getConstraintCount());
        response.put("constraints", constraintRows);
        return JsonHelper.toJson(response);
    }

    public String getLeftConstraintRolesJson() {
        SODConstraintDTO sodConstraintDTO = getSodConstraintDto();
        List<SODConstraintDTO.SODRole> roles = (sodConstraintDTO == null) ? null : sodConstraintDTO.getLeftRoles();
        int count = (sodConstraintDTO == null) ? 0 : sodConstraintDTO.getLeftCount();
        return getSodRolesJson(roles, count);
    }

    public String getRightConstraintRolesJson() {
        SODConstraintDTO sodConstraintDTO = getSodConstraintDto();
        List<SODConstraintDTO.SODRole> roles = (sodConstraintDTO == null) ? null : sodConstraintDTO.getRightRoles();
        int count = (sodConstraintDTO == null) ? 0 : sodConstraintDTO.getRightCount();
        return getSodRolesJson(roles, count);
    }

    /**
     * Quick helper to get the SODConstraintDTO
     * @return The SODConstraintDTO from the session, or null
     */
    private SODConstraintDTO getSodConstraintDto() {
        BaseConstraintDTO constraintDTO = getSession().getConstraint();
        if (constraintDTO instanceof SODConstraintDTO) {
            return (SODConstraintDTO)constraintDTO;
        } else {
            return null;
        }
    }

    /**
     * Get the JSON from a list of SODRoles
     * @param roles SODRoles
     * @param count How many results
     * @return JSON String
     */
    private String getSodRolesJson(List<SODConstraintDTO.SODRole> roles, int count) {
        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, Object>> sodRoleRows = new ArrayList<Map<String, Object>>();

        if (roles != null) {
            for (SODConstraintDTO.SODRole role : roles) {
                Map<String, Object> sodRoleRow = new HashMap<String, Object>();
                sodRoleRow.put("id", role.getUid());
                sodRoleRow.put("name", role.getDisplayableName());
                sodRoleRow.put("description", role.getDescription());
                sodRoleRows.add(sodRoleRow);
            }
        }

        response.put("count", count);
        response.put("rows", sodRoleRows);
        return JsonHelper.toJson(response);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // GenericPolicy selector.xhtml action listeners
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Since there can be more than one selector on the page
     * we rely on a component id convention to know which
     * one we're acting on.  selector.xhtml generate ids like this:
     *
     *  id="#{prefix}_AddAttributeButton"
     *
     * Where "prefix" is a parmaeter passed from the calling page.
     * We require that the prefix be formatted as any single non-digit
     * followed by one or more digits.  Example: S0, _1, etc.
     * The reason we need the silly starter character is because you apparently
     * can't have component ids that begin with a digit or you get the
     * extremely helpful "OSelector" exception during rendering.
     *
     */
    private int getSelectorIndex(ActionEvent e)
        throws GeneralException {

        javax.faces.component.UIComponent uic = e.getComponent();
        String id = uic.getId();
        // strip off non-numeric prefix
        id = id.substring(1);
        int under = id.indexOf("_");
        if (under <= 0)
            throw new GeneralException("Malformed component id");

        String s = id.substring(0, under);
        int index = Util.atoi(s);

        return index;
    }

    public void addSelectorAttribute(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            addMatchAction(index, IdentitySelector.MatchTerm.Type.Entitlement.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorIdentityAttribute(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            PolicySession session = getSession();
            GenericConstraintDTO constraint = (GenericConstraintDTO)session.getConstraint();

            if (constraint != null) {
                IdentitySelectorDTO sel = constraint.getSelector(index);
                sel.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                addMatchAction(index, IdentitySelector.MatchTerm.Type.IdentityAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorPermission(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            addMatchAction(index, IdentitySelector.MatchTerm.Type.Permission.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorTargetPermission(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            addMatchAction(index, IdentitySelector.MatchTerm.Type.TargetPermission.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorRoleAttribute(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            PolicySession session = getSession();
            GenericConstraintDTO constraint = (GenericConstraintDTO)session.getConstraint();

            if (constraint != null) {
                IdentitySelectorDTO sel = constraint.getSelector(index);
                sel.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                addMatchAction(index, IdentitySelector.MatchTerm.Type.RoleAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorEntitlementAttribute(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            PolicySession session = getSession();
            GenericConstraintDTO constraint = (GenericConstraintDTO)session.getConstraint();

            if (constraint != null) {
                IdentitySelectorDTO sel = constraint.getSelector(index);
                sel.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                addMatchAction(index, IdentitySelector.MatchTerm.Type.EntitlementAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }


    private String addMatchAction(int psn, String type) {
        try {
            PolicySession session = getSession();
            session.addMatchTerm(psn, type);
        }
        catch (GeneralException e) {
            addMessage(e.getMessageInstance());
        }
        catch (Throwable t) {
            log.error(t.toString());
            addMessage(new Message("err_fatal_system"));
        }
        return null;
    }

    public void deleteSelectorTerms(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            PolicySession session = getSession();
            session.deleteMatchTerms(index);
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void groupSelectedTerms(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            PolicySession session = getSession();
            session.groupMatchTerms(index);
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void ungroupSelectedTerms(ActionEvent e) {
        try {
            int index = getSelectorIndex(e);
            PolicySession session = getSession();
            session.ungroupMatchTerms(index);
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // SODPolicy Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Action for the "New" button on the left constraint item table.
     */
    public String newLeftItemAction() throws GeneralException {

        if (_role != null) {
            PolicySession session = getSession();
            SODConstraintDTO constraint = (SODConstraintDTO)session.getConstraint();
            if (constraint != null) {
                constraint.add(_role, false);
            }
        }

        // clear this so we display the "Select..." message again
        _role = null;

        saveSession();
        return null;
    }

    /**
     * Action for the "New" button on the right constraint item table.
     */
    public String newRightItemAction() throws GeneralException {

        if (_role != null) {
            PolicySession session = getSession();
            SODConstraintDTO constraint = (SODConstraintDTO)session.getConstraint();
            if (constraint != null) {
                constraint.add(_role, true);
            }
        }

        // clear this so we display the "Select..." message again
        _role = null;

        saveSession();
        return null;
    }

    /**
     * Action for the "Delete" menu item on the constraint item table(s).
     * Will be here for both tables, have to determine which
     * side we're on by mapping the generated uuid for the item.
     */
    public String deleteItemAction() throws GeneralException {

        PolicySession session = getSession();
        SODConstraintDTO constraint = (SODConstraintDTO)session.getConstraint();
        String item = session.getItemId();

        if (constraint != null && item != null) {
            constraint.remove(item);
        }

        session.setItemId(null);
        saveSession();
        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * KLUDGE: !! We need something in the Policy or the
     * Policy.configPage that tells us which faces outcome name
     * to return when creating or editing constraints.
     *
     * I'd rather not clutter the Policy with this since we already
     * have a configPage.  The configPage should have a hidden property
     * for this?
     *
     * Given that we have to code the transitions in faces-config
     * anyway it isn't really all that data driven.
     *
     */
    private String getConstraintOutcome() throws GeneralException {

        String outcome = null;
        Policy p = getObject();
        String page = p.getConfigPage();
        if (page != null) {
            // to make it slightly data driven, assume the convention
            // fooPolicy.xhtml has fooConstraint.xhtml
            int psn = page.indexOf("Policy");
            if (psn > 0) {
                String type = page.substring(0, psn);
                outcome = type + "Constraint";
            }
            else {
                // sigh, the old sod and activity policy pages
                // didn't capitalize "policy" and changing caps
                // in files is always strange with Windows source
                // control
                psn = page.indexOf("policy");
                if (psn > 0) {
                    String type = page.substring(0, psn);
                    outcome = type + "Constraint";
                }
            }
        }
        return outcome;
    }

    /**
     * Action for the "New" menu item on the policy constraint table.
     */
    public String newConstraintAction() {

        String next = null;
        try {
            PolicySession session = getSession();
            if (session.editNewConstraint()) {

                next = getConstraintOutcome();
                saveSession();
            }
        }
        catch (GeneralException e) {
            addMessage(e.getMessageInstance());
        }
        catch (Throwable t) {
            log.error(t.toString());
            addMessage(new Message(MessageKeys.ERR_FATAL_SYSTEM));
        }

        return next;
    }

    /**
     * Action for the "Edit" menu item on the policy constraint table.
     * The session.constraintId must have already been set.
     */
    public String editConstraintAction() {

        String next = null;
        try {
            PolicySession session = getSession();
            if (session.editSelectedConstraint()) {

                next = getConstraintOutcome();
                saveSession();
            }
        }
        catch (GeneralException e) {
            addMessage(e.getMessageInstance());
        }
        catch (Throwable t) {
            // probably not recoverable but stay
            log.error(t.toString());
            addMessage(new Message(MessageKeys.ERR_FATAL_SYSTEM));
        }

        return next;
    }

    /**
     * Action for the "Delete" menu item on the policy constraint table.
     */
    public String deleteConstraintAction() {

        String next = null;
        PolicySession session = getSession();
        session.deleteSelectedConstraint();
        saveSession();

        return next;
    }

    /**
     * Cancel handler for the constraint editing page.
     * Since we're using cloned copies we don't have to unwind
     * anything, just clear session state.
     */
    public String cancelConstraintAction() {

        PolicySession session = getSession();
        session.cancelConstraint();
        saveSession();

        return "cancel";
    }

    /**
     * Handler for rule simulation and save rule
     */
    public String saveAndSimulatePolicyRuleAction() {
        return runSimulation(true, false);
    }

    public String runSimulation(boolean saveConstraint, boolean isPolicy) {
        PolicySession session = getSession();

        Attributes<String,Object> attrs = new Attributes<String,Object>();

        String next = null;

        try {

            if (saveConstraint) {
                if (needCheckViolationOwnerSelectionForConstraint()) {
                    if (!checkViolationOwnerSelectionForConstraint()) {
                        return null;
                    }
                }
            }

            TaskManager tm = new TaskManager(getContext());

            Policy p = getObject();

            BaseConstraintDTO selectedConstraint = null;

            //check if simulation is already running
            deleteTaskResultIfAny(Util.otob(isPolicy));

            //on edit rule, the selected constraint is available in session.getConstraint()
            //while from grid it can be get using session.getSelectedConstraint()
            if (!Util.otob(isPolicy)) {
                if (getConstraint() != null) {
                    selectedConstraint = getConstraint();
                    attrs.put(PolicyImpactAnalysisCalculator.ARG_TARGET_RULE, getConstraint().getName());
                }
            }

            if (saveConstraint) {
                session.commitConstraint();
                next = "return";
            }

            // copy the DTO changes into the Policy
            // we need to send whole policy as xml to task executor
            if (_session != null) {
                _session.saveAction(getContext(), getObject());
            }

            attrs.put(PolicyImpactAnalysisCalculator.ARG_TARGET_POLICY, p);
            String policyName = p.getName();
            if (Util.isNotNullOrEmpty(policyName)) {
                String _resultName = PolicyImpactAnalysisCalculator.TASK_DEFINITION + ": " + policyName;
                //adding meaning full name to task result object being generated
                attrs.put(TaskSchedule.ARG_RESULT_NAME, _resultName);
            }
            //execute the background task
            TaskDefinition def = tm.getTaskDefinition(PolicyImpactAnalysisCalculator.TASK_DEFINITION);
            TaskResult result = tm.runWithResult(def, attrs);

            if (result != null) {
                if (log.isDebugEnabled())
                    log.debug("result.getId() ---> " + result.getId());

                //set the task result id as argument in policy or constraint
                if (Util.otob(isPolicy)) {
                    p.setArgument(TASK_RESULT_ID, result.getId());
                    //disable the policy
                    p.setState(State.Inactive);
                    session.getPolicy().setState(State.Inactive.toString());
                } else {
                    if (selectedConstraint != null) {
                        selectedConstraint.setTaskResultId(result.getId());
                        //disable the rule
                        selectedConstraint.setDisabled(true);
                    }
                }
            }

            saveSession();

        } catch (GeneralException e) {
            if (log.isErrorEnabled()) {
                log.error("Exception while simulation " , e);
            }
            addMessage(e.getMessageInstance());
        } catch (Throwable t) {
            // probably not recoverable but stay
            log.error(t.toString());
            addMessage(new Message(MessageKeys.ERR_FATAL_SYSTEM));
        }

        return next;
    }

    /**
     * Handler for rule as well as policy simulation
     */
    public String simulatePolicyRuleAction() {
        //check if simulation is for policy or rule
        String isPolicy = getIsPolicy();
        return runSimulation(false, Util.otob(isPolicy));
    }

    /**
     * Button handler for the constraint editing page to return
     * to the main policy page.
     */
    public String saveConstraintAction() {

        String next = null;
        PolicySession session = getSession();

        if (needCheckViolationOwnerSelectionForConstraint()) {
            if (!checkViolationOwnerSelectionForConstraint()) {
                return null;
            }
        }

        try {

            //check if there is another simulation task that is already started for this rule
            //if yes throw exception
            //Changes in rule not allowed if simulation is in progress
            if ((session.getSelectedConstraint() != null) && (session.getSelectedConstraint().getTaskResultId() != null)) {
                String taskId = session.getSelectedConstraint().getTaskResultId();

                TaskResult result = getContext().getObjectById(TaskResult.class, taskId);

                if (result != null && !result.isComplete()) {
                    throw new GeneralException(MessageKeys.ERROR_EDIT_RULE_SIMULATION_RUNNING);
                }
            }

            session.commitConstraint();
            next = "return";
        }
        catch (GeneralException e) {
            addMessage(e.getMessageInstance());
        }
        catch (ParseException pe) {
            // This is one of ours but it isn't a GeneralException
            // which also means it isn't located.  We'll need to
            // fix this!!
            addMessage(new Message(Message.Type.Error, pe.toString()));
        }
        catch (Throwable t) {
            // probably not recoverable but stay
            log.error(t.toString());
            addMessage(new Message(MessageKeys.ERR_FATAL_SYSTEM));
        }

        saveSession();

        return next;
    }

    private boolean needCheckViolationOwnerSelectionForConstraint() {

        String policyType = getPolicy().getType();
        if (policyType.equals(Policy.TYPE_SOD) || policyType.equals(Policy.TYPE_ACTIVITY)) {
            return true;
        }
        return false;
    }


    /**
     * Handler for rule simulation and save policy
     */
    public String saveAndSimulatePolicyAction() {
        return savePolicy(true);
    }

    /**
     * Anything special we want to add here?
     */
    public String savePolicy(boolean simulate) {

        String next = null;

        try {

            if (checkRequiredFields()) {

             // Simulate the policy if this was requested.
                if (simulate) {
                    simulatePolicyRuleAction();
                }

                if (!simulate) {
                    //check if there is another simulation task that is already started for this policy
                    //Changes in rule not allowed if simulation is in progress
                    //simulation makes the policy inactive so it should remain inactive till simulation is running
                    //The method saveAndSimulatePolicyAction will do both save and simulate

                    Object taskId = getObject().getArgument(TASK_RESULT_ID);

                    if (taskId != null) {

                        TaskResult result = getContext().getObjectById(TaskResult.class, taskId.toString());

                        if (result != null && !result.isComplete()) {
                            throw new GeneralException(MessageKeys.ERROR_EDIT_POLICY_SIMULATION_RUNNING);
                        }
                    }

                 // copy the DTO changes into the Policy and process pending deletes
                    if (_session != null) {
                        _session.saveAction(getContext(), getObject());
                    }
                }

                // attach and save the Policy
                next = super.saveAction();

                // Persist descriptions to LocalizedAttributes
                Describer describer = new Describer((Describable)getObject());
                SailPointContext ctx = getContext();
                describer.saveLocalizedAttributes(ctx);
                ctx.commitTransaction();
                // remove our HttpSession state
                cancelSession();
            }
        }
        catch (GeneralException e) {
            // assume this is recoverable stay on the page
            addMessage(e.getMessageInstance());
        }
        catch (ParseException pe) {
            // This is one of ours but it isn't a GeneralException
            // which also means it isn't located.  We'll need to
            // fix this!!
            addMessage(new Message(Message.Type.Error, pe.toString()));
        }
        catch (Throwable t) {
            // probably not recoverable but stay
            log.error(t.toString());
            // TODO: Need some kind of "System Error"
            addMessage(new Message(MessageKeys.ERR_FATAL_SYSTEM));
        }

        return next;
    }

    /**
     * Anything special we want to add here?
     */
    public String saveAction() {
        return savePolicy(false);
    }

    private boolean checkRequiredFields() throws GeneralException {

        if (!checkName()) {
            return false;
        }

        if (!checkViolationOwnerSelectionForPolicy()) {
            return false;
        }

        return true;
    }

    private boolean checkName() throws GeneralException {

        if (Util.isNullOrEmpty(getDto().getName())) {
            addMessage(new Message(Message.Type.Error, MessageKeys.LABEL_NAME_IS_REQUIED));
            return false;
        }

        return checkDuplicateName();
    }

    private boolean checkViolationOwnerSelectionForPolicy() {

        PolicyDTO dto = getDto();

        return checkViolationOwnerSelection(
                dto.getViolationOwnerType(),
                dto.getViolationOwner(),
                dto.getViolationOwnerRule());
    }

    private boolean checkViolationOwnerSelectionForConstraint() {

        BaseConstraintDTO constraint = getSession().getConstraint();

        return checkViolationOwnerSelection(
                constraint.getViolationOwnerType(),
                constraint.getViolationOwnerId(),
                constraint.getViolationOwnerRule());
    }

    private boolean checkViolationOwnerSelection(ViolationOwnerType ownerType, String identityId, String ruleName) {

        if (ownerType == ViolationOwnerType.Identity) {
            if (Util.isNullOrEmpty(identityId)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.POLICY_VIOLATION_IDENTITY_MUST_BE_SELECTED));
                return false;
            }
        } else if (ownerType == ViolationOwnerType.Rule) {
            if (Util.isNullOrEmpty(ruleName)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.POLICY_VIOLATION_RULE_MUST_BE_SELECTED));
                return false;
            }
        }

        return true;
    }

    private boolean checkDuplicateName() throws GeneralException {
        Policy currObject = getObject();
        if (currObject != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", getPolicy().getName()));
            if (currObject.getId() != null) {
                ops.add(Filter.ne("id", currObject.getId()));
            }

            if (getContext().countObjects(Policy.class, ops) > 0) {
                addMessage(Message.error(MessageKeys.POLICY_DUPLICATE_NAME, getPolicy().getName()));
                return false;
            }
        }

        return true;
    }

    public String cancelAction() {

        String outcome = "cancel";
        try {
            outcome = super.cancelAction();
        }
        catch (Throwable t) {
            // this shouldn't throw any more but we haven't
            // changed all the signatures yet
            log.error(t.toString());
        }

        cancelSession();

        return outcome;
    }

    /**
     * Check if result are to displayed for policy or rule
     * Then get taskresultId and query taskresult object where
     * we are storing results in form of PolicyImpactAnalysis object
     */
    public String getViewSimulateResultJson()
    {
        TaskResult taskResult;
        PolicyImpactAnalysis pa = null;
        String taskId = null;
        String isPolicy = getRequestParameter(IS_POLICY);
        try {
            if (Util.otob(isPolicy)) {
                Object _taskId = getObject().getArgument(TASK_RESULT_ID);
                if(null != _taskId)
                    taskId = (String)_taskId;
            }
            else {
                if (getConstraint() != null) {
                    taskId = getConstraint().getTaskResultId();
                } else {
                    throw new GeneralException("Cannot find either taskresultId or constraintId");
                }
            }
            if (taskId != null) {
                taskResult = getContext().getObjectById(TaskResult.class, taskId);
                if(taskResult != null){
                    if (!taskResult.isComplete()) {
                        throw new GeneralException("Simulation task is still running");
                    }
                    pa = (PolicyImpactAnalysis)taskResult.getAttribute(PolicyImpactAnalysisCalculator.RET_ANALYSIS);
                }
            }
        } catch (GeneralException e) {
            if(log.isErrorEnabled()){
                log.error("Unable to fetch simulation results :" + e.getMessage());
            }
        } catch(Throwable t) {
            if(log.isErrorEnabled()){
                log.error("Unable to fetch simulation results :" + t.getMessage());
            }
        }
        return JsonHelper.toJson(pa);
    }

        /*
         * This will cancel simulation task if it is running.
         * taskresultID is passed as request parameter.
         */
        public String getCancelSimulationTaskResultJson() {

            Map<String, Object> response = new HashMap<String, Object>();
            String result = "success";
            String message="" ;
            String resultid = null;

            String isPolicy = getRequestParameter(IS_POLICY);

            try {
                if (Util.otob(isPolicy)) {
                    resultid = getObject().getArgument(TASK_RESULT_ID).toString();
                } else {
                    if (getConstraint() != null) {
                        resultid = getConstraint().getTaskResultId();
                    }
                }

                TaskResult tresult;
                TaskManager tm;
                if(Util.isNullOrEmpty(resultid)){
                    throw new GeneralException(MessageKeys.TASK_TERMINATE_ERROR_NOTASK);
                }
                tresult = getContext().getObjectById(TaskResult.class, resultid);
                if (tresult != null) {
                    if (tresult.isComplete())
                        throw new GeneralException(MessageKeys.TASK_TERMINATE_ERROR_TASKCOMPLETED);

                    tm = new TaskManager(getContext());
                    tm.terminate(tresult);
                }

                //clear taskresultID in session
                //will get saved in DB on saveAction
                if (Util.otob(isPolicy)) {
                    getObject().setArgument(TASK_RESULT_ID, "");
                } else {
                    if (getConstraint() != null) {
                        getConstraint().setTaskResultId("");
                    }
                }

                saveSession();
            } catch (Throwable t) {
                result = "failure";
                message = t.getMessage();
                if(log.isErrorEnabled()){
                    log.error("Unable to terminate task with taskresultID[ " + resultid + " ]" + message);
                }
            }

            response.put("result",result);
            response.put("message", message);
            return JsonHelper.toJson(response);
        }

        /*
         * This will get status of simulation task.
         * taskresultID is passed as request parameter.
         */
        public String getSimulationTaskStatusJson() {

            Map<String, Object> response = new HashMap<String, Object>();
            String status = "";
            String message="" ;
            String lastRunDate = "";
            String policyType = getPolicy().getType();
            String isPolicy = getRequestParameter(IS_POLICY);
            String resultid = null;
            try {
                //if it is run first time pick from arguments bag on constraint
                //as it won't be available in grid store.

                if (Util.otob(isPolicy)){
                    resultid = getObject().getArgument(TASK_RESULT_ID).toString();
                } else {
                    if (getConstraint() != null)
                        resultid = getConstraint().getTaskResultId();
                }

                TaskResult tresult;
                if(Util.isNullOrEmpty(resultid)){
                    throw new GeneralException("Cannot find task with this taskresultId");
                }
                tresult = getContext().getObjectById(TaskResult.class, resultid);
                if(tresult != null){
                    if (tresult.isComplete()) {
                        status = "complete";
                    }
                    else {
                        status = "inprogress";
                    }
                    lastRunDate = Internationalizer.getLocalizedDate(tresult.getCreated(), getLocale(), getUserTimeZone());
                }else
                {
                    if(log.isErrorEnabled())
                        log.error("Unable to get status of task with taskresultID[ " + resultid + " ]");

                    message = "Unable to get status of task with taskresultID[ " + resultid + " ]";
                }
            } catch (Throwable t) {
                status = "notpresent";
                message = t.getMessage();
                if(log.isErrorEnabled()){
                    log.error("Unable to get status of task with taskresultID[ " + resultid + " ]" + message);
                }
            }
            response.put("status",status);
            response.put("message", message);
            response.put("lastRunDate", lastRunDate);
            response.put("policyType", policyType);

            return JsonHelper.toJson(response);
        }

    /**
     * Override this method so that it uses the multi language option
     *
     */
    public Locale getLocale(){
        boolean useLocalizedDescriptions = Util.otob(Configuration.getSystemConfig().get("enableLocalizePolicyDescriptions"));
        Locale locale = null;
        if (useLocalizedDescriptions) {
            FacesContext o = FacesContext.getCurrentInstance();
            if (o != null && o.getViewRoot() != null) {
                locale = o.getViewRoot().getLocale();
                // check if locale is supported, if not fallback to default locale
                if (!localizer.isLocaleSupported(locale.toString())) {
                    locale = localizer.getDefaultLocale();
                }
            }
        }
        else {
            locale = localizer.getDefaultLocale();
        }
        return locale;
    }

    private BaseConstraintDTO getConstraint() {
        PolicySession session = getSession();
        //on edit rule, the selected constraint is available in session.getConstraint()
        //while from grid it can be get using session.getSelectedConstraint()
        //in result popup window, it gets CONSTRAINT_ID as request parameter

        //first check in request parameter
        if (Util.isNotNullOrEmpty(getRequestParameter(CONSTRAINT_ID))) {
            return session.findConstraint(getRequestParameter(CONSTRAINT_ID));
        } else if (session.getConstraint() != null) {
            return session.getConstraint();
        } else if (session.getSelectedConstraint() != null) {
            return session.getSelectedConstraint();
        }

        return null;
    }

    /**
     * delete the task result if any
     */
    private void deleteTaskResultIfAny(boolean isPolicy) throws GeneralException {

        Object taskId = null;
        Policy p = getObject();

        if (isPolicy) {
            taskId = p.getArgument(TASK_RESULT_ID);
        } else {
            if (getConstraint() != null) {
                taskId = getConstraint().getTaskResultId();
            }
        }

        if (taskId != null) {
            TaskResult result = getContext().getObjectById(TaskResult.class, taskId.toString());

            if (result != null) {
                if (!result.isComplete()) {
                    throw new GeneralException(MessageKeys.ERROR_SIMULATION_ALREADY_RUNNING);
                } else {
                    //delete the old task result
                    Terminator terminator = new Terminator(getContext());
                    terminator.deleteObject(result);
                }
            }
        }
    }
}