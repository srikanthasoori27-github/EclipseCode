package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.Certificationer;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.certification.CertificationDecisioner;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.Certification.Type;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIPreferences;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.ModifyImmutableException;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseBean;
import sailpoint.web.LoginBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public abstract class BaseCertificationViewBean extends BaseBean
        implements NavigationHistory.Page {

    private static final Log log = LogFactory.getLog(BaseCertificationViewBean.class);

    private String certificationId;

    private Certification.Type certType;
    private String name;
    private String definitionId;
    private Date signed;
    private boolean editable;
    private Boolean bulkReassignment;
    private String parentId;
    private List<String> certifiers;
    private boolean continuous;
    private List<ActionOption> actionChoices;
    private Boolean displayEntitlementDescription;
    private CertificationDefinition definition;
    private String subCertId;
    private String rescindCertId;
    private String workItemId;
    private String defaultRevoker;
    private boolean readyForSignoff;
    private Boolean showRemediationDialog;
    private Boolean automateSignoffPopup;
    private Certification.EntitlementGranularity granularity;
    private String defaultAccountReassignAssignee;

    protected GridState gridState;

    private static final String PAGE_STATE_FLAG = "certificationViewPage";
    
    public BaseCertificationViewBean() {
        super();
        restoreFromHistory();

        certificationId = this.getRequestParameter("certificationId");
        if (certificationId == null){
            // no me gusta el JSF
            certificationId = getRequestParameter("id") != null ? getRequestParameter("id") : getRequestParameter("editForm:certificationId");
        }

        String entityId = this.getRequestParameter("entityId");
        if (certificationId == null && entityId != null){
            try {
                QueryOptions ops = new QueryOptions(Filter.eq("id", entityId));
                Iterator<Object[]> result = getContext().search(CertificationEntity.class,
                        ops, Arrays.asList("certification.id"));
                if (result != null && result.hasNext())
                    certificationId = (String)result.next()[0];
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract GridState loadGridState();
    
    /**
     * Restore state from the NavigationHistory if we can retrieve state for
     * this page.
     */
    public void restoreFromHistory() {
        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().restorePageState((NavigationHistory.Page) this);
        }
    }
    
    /** 
     * Bit of a hack on the page state.  
     * We need to be able to get out of the cert page navigation
     * and to the history that preceded it.  So we will put this flag
     * in the page state of cert pages and then we can check for it
     * when navigating.
     * Use this in derived classes to get a standard Object array to start with
	 * 10 spots for their page state. Space 10 is RESERVED for this flag.
	 */
    public Object calculatePageState() {
        Object[] state = new Object[10];
        state[9] = PAGE_STATE_FLAG;
        return state;
    }

    protected void initCertification() throws GeneralException {
        if (certificationId != null) {
            Iterator<Object[]> results = getContext().search(Certification.class,
                    new QueryOptions(Filter.eq("id", certificationId)), Arrays.asList("name", "parent.id",
                    "certificationDefinitionId", "signed", "bulkReassignment", "type", "continuous", "creator",
                    "statistics.totalItems", "statistics.completedItems", "phase"));
            if (results != null && results.hasNext()) {
                Object[] row = results.next();
                name = (String) row[0];
                parentId = (String) row[1];
                definitionId = (String) row[2];
                signed = (Date) row[3];
                bulkReassignment = row[4] != null ? (Boolean) row[4] : false;
                certType = (Certification.Type) row[5];
                continuous = row[6] != null ? (Boolean) row[6] : false;
                String creator = (String)row[7];
                
                Certification.Phase phase = (Certification.Phase)row[10];

                CertificationDefinition definition = getDefinition(true);
                if (definition != null) {
                    granularity = definition.getEntitlementGranularity();
                }

                if (signed == null){
                    // check to see if the logged in user  is a cert super admin.
                    if (!this.editable) {
                        this.editable = CertificationAuthorizer.isCertificationAdmin(this);
                    }

                    // Check if the user is an owner of the cert.
                    if (!this.editable) {
                        if (getCertifiers() != null)
                            this.editable = CertificationAuthorizer.isCertifier(getLoggedInUser(), getCertifiers());
                    }

                    // Check if this user is the certifier for a parent cert if this is
                    // a bulk reassignment.
                    if (!this.editable && bulkReassignment) {
                        List<String> parentCertifiers = CertificationUtil.getCertifiers(getContext(), parentId);
                        if (parentCertifiers != null)
                            this.editable = CertificationAuthorizer.isCertifier(getLoggedInUser(), parentCertifiers);
                    }

                    // Check for a staged phase or pending certification with staging enabled..
                    if (Phase.Staged.equals(phase) || (phase == null && definition != null && definition.isStagingEnabled())) {
                        this.editable = false;
                    }
                }

                if (!isAuthorized(certificationId, creator, getCertifiers(), getWorkItemId(), this)){
                    throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
                }

                // Check to see if the cert looks complete. If so, we need
                // to see if it's ready for signoff, which means loading the whole cert.
                int totalItems = (Integer)row[8];
                int completedItems = (Integer)row[9];
                if (isPromptForSignOff() && !continuous && signed == null && this.editable &&
                        totalItems == completedItems){
                    CertificationService svc = new CertificationService(getContext());
                    Certification cert = getContext().getObjectById(Certification.class, certificationId);
                    readyForSignoff = svc.isReadyForSignOff(cert);
                }

            }
        }
    }

    protected CertificationDefinition getDefinition() throws GeneralException {
        return getDefinition(false);
    }

    protected CertificationDefinition getDefinition(boolean fromInit) throws GeneralException {
        if (definition == null) {
            if (definitionId == null) {
                if (!fromInit) {
                    // avoid recursion
                    initCertification();
                }
            }

            if (definitionId != null)
                definition = getContext().getObjectById(CertificationDefinition.class, definitionId);

            // if we cant get a definition, bootstrap one using sys config
            if (definition == null) {
                definition = new CertificationDefinition();
                definition.initialize(getContext());
            }
        }

        return definition;
    }

    public String getCertificationId() {
        return certificationId;
    }

    public void setCertificationId(String certificationId){
        this.certificationId = certificationId;
    }


    public boolean isContinuous() {
        return continuous;
    }

    public void setContinuous(boolean continuous) {
        this.continuous = continuous;
    }

    public String getName() {
        return name;
    }

    public Certification.Type getCertType() {
        return certType;
    }

    public boolean isFound() {
        return certificationId != null;
    }

    public boolean isEditable() throws GeneralException {
        return editable;
    }

    public boolean isSignedOff() throws GeneralException {
        return signed != null;
    }

    public Date getSignedDate() {
        return signed;
    }

    public String getGranularity() {
        return granularity != null ? granularity.name() : "";
    }

    public boolean isSupportsExportToCSV() {
        return getCertType() != null && (Certification.Type.DataOwner.equals(getCertType()) || getCertType().isIdentity());
    }

    public boolean isAllowEntitlementDescriptionToggle() {
        Type type = getCertType();
        return  type != null
                && !type.isType(Certification.Type.BusinessRoleMembership, Certification.Type.BusinessRoleComposition);
    }

    public boolean isRevokeAccountAllowed() throws GeneralException {
        boolean revokeAccountAllowed = false;

        // Allow revoke account for identity certs (except for business
        // role membership - we don't allow revoking accounts through roles yet
        revokeAccountAllowed = (getCertType().isIdentity() && !Certification.Type.BusinessRoleMembership
                .equals(getCertType()));

        if (revokeAccountAllowed) {
            revokeAccountAllowed = getDefinition().isAllowAccountRevocation(getContext());
        }
        

        return revokeAccountAllowed;
    }

    public boolean isShowDelegateLegend() throws GeneralException {
        return getDefinition().isAllowItemDelegation(getContext());
    }

    public boolean isAllowEntityDelegation() throws GeneralException {
        return getDefinition().isAllowEntityDelegation(getContext());
    }
    
    public boolean isDelegationForwardingDisabled() throws GeneralException {
        return getDefinition().isDelegationForwardingDisabled();
    }

    public boolean isApproveAccountAllowed() throws GeneralException {
        return getDefinition().isAllowApproveAccounts(getContext());
    }

    public boolean isDataOwnerCertification(){
        return Certification.Type.DataOwner.equals(getCertType());
    }

    public boolean isAccountGroupCertification(){
        return Certification.Type.AccountGroupMembership.equals(getCertType())
                || Certification.Type.AccountGroupPermissions.equals(getCertType());
    }

    public boolean isBusinessRoleCertification(){
        return Certification.Type.BusinessRoleComposition.equals(getCertType());
    }

    public boolean isBusinessRoleMembershipCertification(){
        return Certification.Type.BusinessRoleMembership.equals(getCertType());
    }

    /**
     * Retrieves the display certificate summary last state in terms of expanded and
     * collapsed. This is saved in the User Preference and as per the requirements
     * currently is valid of multiple certificates for the current user
     *
     * @throws GeneralException
     */
    public boolean retrieveDisplaySummaryLastState() throws GeneralException {
        return retrieveCurrentUserPreference(UIPreferences.PRF_CERTIFICATION_SUMMARY_DISPLAY_MINIMIZED, false);
    }

    public GridState getGridState() {
        if (this.certificationId != null && null == this.gridState) {
            this.gridState = loadGridState();
        }
        return this.gridState;
    }

    public void setGridState(GridState state) {
        this.gridState = state;
    }

    /**
     * Gets the Display Start Up Help Certification Grid View
     *
     * @throws GeneralException
     */
    public boolean getDisplayStartUpHelpCertificationGridView() throws GeneralException {
        return retrieveCurrentUserPreference(UIPreferences.StartUpHelp.CertGridView.getKey(), true);
    }

    /**
     * Gets the Display Start Up Help Certification Entity View
     */
    public boolean getDisplayStartUpHelpCertificationEntityView() throws GeneralException {
        return retrieveCurrentUserPreference(UIPreferences.StartUpHelp.CertEntityView.getKey(), true);
    }

    public boolean isDisplayEntitlementDescription() throws GeneralException {
        if (displayEntitlementDescription == null) {
            Identity user = getLoggedInUser();
            Object pref = user.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC);
            if (null != pref) {
                this.displayEntitlementDescription = Util.otob(pref);
            }
            else {
                displayEntitlementDescription = getDefinition().isDisplayEntitlementDescriptions(getContext());
            }

        }
        return displayEntitlementDescription;
    }

     /**
     * Retrieves current user preference for the certification ui as per the key
     *
     * @param key           user preference certification key
     * @param defaultResult if the key does not exist this is used
     * @return boolean state result
     * @throws GeneralException
     */
    private boolean retrieveCurrentUserPreference(String key, boolean defaultResult) throws GeneralException {
        boolean evaluateResult = defaultResult;
        Identity currentUser = this.getLoggedInUser();
        Object lastStateObject = null;
        if (currentUser != null) {
            lastStateObject = currentUser.getUIPreference(key);
            if (lastStateObject != null) {
                evaluateResult = Util.otob(lastStateObject);
            }
        }
        return evaluateResult;
    }

    public boolean isMitigationDialogEnabled() throws GeneralException {
        CertificationDefinition def = getDefinition();
        Boolean enabled = def.isAllowExceptionPopup(getContext());
        if (enabled != null) {
            return enabled.booleanValue();
        }
        return true;
    }
    
    public long getDefaultMitigationExpiration() throws GeneralException {
        CertificationDefinition def = getDefinition();
        Long amount = def.getAllowExceptionDurationAmount(getContext());
        Duration.Scale scale = def.getAllowExceptionDurationScale(getContext());
        if (amount != null && scale != null) {
            Duration duration = new Duration(amount, scale);
            Date expiration = duration.addTo(new Date());
            return expiration.getTime();
        } else {
            return new Date().getTime();
        }
    }

    public String getActionsRequiringComments() throws GeneralException {
        return JsonHelper.toJson(getDefinition().getStatusesRequiringComments(getContext()));
    }

    public String getBulkActionChoicesJson() throws GeneralException {
        if (null == this.actionChoices) {
            this.actionChoices = new ArrayList<ActionOption>();

            if (isEditable() && !isSignedOff()) {
                Configuration sysConfig = getContext().getConfiguration();

                CertificationDefinition definition = getDefinition();


                boolean allowBulkApprove = definition.isAllowListBulkApprove(getContext());

                if (allowBulkApprove) {
                    this.actionChoices.add(new ActionOption(CertificationAction.Status.Approved.name(),
                            super.getMessage(MessageKeys.CERT_DECISION_APPROVE)));
                }

                boolean allowBulkRevoke = definition.isAllowListBulkRevoke(getContext());

                if (allowBulkRevoke) {
                    this.actionChoices.add(new ActionOption(CertificationAction.Status.Remediated.name(),
                            super.getMessage(MessageKeys.CERT_DECISION_REMEDIATE)));
                }

                boolean allowBulkAccountRevoke = definition.isAllowListBulkAccountRevocation(getContext());

                if (allowBulkAccountRevoke && !bulkReassignment) {
                    this.actionChoices.add(new ActionOption(CertificationAction.Status.RevokeAccount.name(),
                            super.getMessage(MessageKeys.CERT_DECISION_REVOKE_ALL_ACCOUNTS)));
                }

                boolean allowBulkMitigate = definition.isAllowListBulkMitigate(getContext());

                if (allowBulkMitigate) {
                    boolean entityCanBeMitigated = !getCertType().isType(Certification.Type.AccountGroupMembership,
                            Certification.Type.AccountGroupMembership,
                            Certification.Type.BusinessRoleComposition);

                    boolean allowExceptions = definition.isAllowExceptions(getContext());

                    if (entityCanBeMitigated && allowExceptions) {
                        this.actionChoices.add(new ActionOption(CertificationAction.Status.Mitigated.name(),
                                super.getMessage(MessageKeys.CERT_DECISION_MITIGATE)));
                    }
                }

                boolean allowBulkReassign = definition.isAllowListBulkReassign(getContext());

                if (allowBulkReassign) {
                    this.actionChoices.add(new ActionOption(CertificationDecisioner.STATUS_REASSIGN,
                            super.getMessage(MessageKeys.CERT_DECISION_BULK_REASSIGN)));
                }

                if (sysConfig.getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_SAVE_CUSTOM_ENTITY_FIELDS)) {
                    this.actionChoices.add(new ActionOption(CertificationBulkActionBean.ACTION_SAVE_ENTITY_CUSTOM_FIELDS,
                            super.getMessage(MessageKeys.CERT_DECISION_BULK_SAVE_ENTITY_CUSTOM_FIELDS)));
                }

                boolean allowBulkUndo = definition.isAllowListBulkClearDecisions(getContext());

                if (allowBulkUndo) {
                    this.actionChoices.add(new ActionOption(CertificationDecisioner.STATUS_UNDO,
                                super.getMessage(MessageKeys.CERT_DECISION_BULK_UNDO)));
                }
            }
        }

        return JsonHelper.toJson(actionChoices);
    }

    public List<String> getCertifiers() throws GeneralException{
        if (certifiers == null){
            certifiers = CertificationUtil.getCertifiers(getContext(), certificationId);
        }
        return certifiers;
    }

    public String getSubCertId() {
        return subCertId;
    }

    public void setSubCertId(String subCertId) {
        this.subCertId = subCertId;
    }

    public String getRescindCertId() {
        return rescindCertId;
    }

    public void setRescindCertId(String rescindCertId) {
        this.rescindCertId = rescindCertId;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public void setDefaultRevoker(String def) throws GeneralException{
        defaultRevoker = def;
    }
    
    public String getDefaultRevoker() throws GeneralException{
        if (defaultRevoker == null){
            Configuration config = getContext().getConfiguration();
            defaultRevoker = config.getString(Configuration.DEFAULT_REMEDIATOR);
        }

        return defaultRevoker;
    }

    /**
     * In some cases, we may always want to show the remediation dialog.
     * As far as I know, this is only used in demos so that the
     * integration configuration can be demonstrated.
     */
    public boolean isShowRemediationDialog() throws GeneralException{

        if (showRemediationDialog == null){
            Configuration config = getContext().getConfiguration();
            showRemediationDialog = config.getBoolean(Configuration.SHOW_AUTO_REMEDIATION_DIALOG, false);
        }

        return showRemediationDialog;
    }

    public boolean isPromptForSignOff() throws GeneralException
    {
        if (automateSignoffPopup == null) {
            if (isContinuous()) {
                automateSignoffPopup = false;
            } else {
                if (getDefinition() != null) {
                    automateSignoffPopup = getDefinition().isAutomateSignoffPopup(getContext());
                } else {
                    Configuration sysConfig = getContext().getConfiguration();
                    automateSignoffPopup = sysConfig.getBoolean(Configuration.AUTOMATE_SIGNOFF_POPUP, false);
                }
            }
        }
        
        return automateSignoffPopup;
    }

    public boolean isShowSignoffPromptOnStartup() throws GeneralException
    {
        // Show the signoff if it's configured to, and it's ready, and it's not already signed off.
        return isPromptForSignOff() && readyForSignoff && !isSignedOff();
    }
    
    public int getBulkSelectionCountForConfirmation() {
        Configuration systemConfig = Configuration.getSystemConfig();
        return Util.otoi(systemConfig.get(Configuration.BULK_SELECTION_COUNT_FOR_CONFIRMATION));
    }

   /**
   * Determine if Reassignment should be allowed
   * @return
   * @throws GeneralException
   */
    public boolean isLimitReassign() throws GeneralException {
        Certification cert = getContext().getObjectById(Certification.class, certificationId);
        return (cert != null) ? cert.limitCertReassignment(getContext()) : false;
    }

    public boolean isAllowAccountReassign() throws GeneralException{
        return Util.isNullOrEmpty(getWorkItemId()) && getDefinition().isEnableReassignAccount(getContext());
    }

    public String getDefaultAccountReassignAssignee() throws GeneralException{
        if (defaultAccountReassignAssignee == null){
            defaultAccountReassignAssignee = "";
            if (getDefinition().isEnableReassignAccount(getContext())){
                Identity identity = getDefinition().getDefaultAssignee(getContext());
                if (identity != null)
                    defaultAccountReassignAssignee = identity.getName();
            }
        }

        return defaultAccountReassignAssignee;
    }

    /**
     * Returns the number of additional decision columns required for
     * this certification above and beyond the default (Approve, Revoke and Delegate).
     * This allows us calculate the width of the decision buttons column.
     * Note that Delegate is included in this default list because we always include
     * a hidden Delegate button for undoing reassignments.
     */
    public int getAdditionalDecisions() throws GeneralException{

        int cnt = 0;

        cnt += getDefinition().isAllowApproveAccounts(getContext()) ? 1 : 0;
        cnt += getDefinition().isAllowAccountRevocation(getContext()) ? 1 : 0;
        cnt += getDefinition().isAllowItemDelegation(getContext())  ? 1 : 0;
        cnt += getDefinition().isAllowExceptions(getContext()) ? 1 : 0;

        return cnt;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // AUTHORIZATION
    //
    ////////////////////////////////////////////////////////////////////////////

    boolean isAuthorized(String certId, String creator, List<String> certifiers, String workItemId,
                                BaseBean baseBean)
        throws GeneralException {

        //bug 21582 verify user has the scope access required by cert
        // This lets in system administrators or users that can read
        // (FullAccessCertifications) or write (CertifyAllCertifications).
        if (Authorizer.hasAccess(baseBean.getLoggedInUserCapabilities(),
                baseBean.getLoggedInUserRights(),
                new String[]{SPRight.CertifyAllCertifications,
                        SPRight.FullAccessCertifications})
                && (baseBean.isObjectInUserScope(certId, Certification.class))) {
            return true;
        }

        if (CertificationAuthorizer.isCertifier(baseBean.getLoggedInUser(), certifiers))
            return true;

        if (CertificationAuthorizer.isCreatorOrOwner(baseBean.getLoggedInUser(), creator))
            return true;

        if (workItemId != null) {
            WorkItem item = baseBean.getContext().getObjectById(WorkItem.class, workItemId);
            if ((null != item) && CertificationAuthorizer.isCreatorOrOwner(baseBean.getLoggedInUser(), item.getOwner().getName()))
                return true;
        }

        // If we've come this far we have to get the cert
        Certification cert = getContext().getObjectById(Certification.class, certId);
        if (CertificationAuthorizer.isReassignmentParentOwner(baseBean.getLoggedInUser(), cert))
            return true;

        // check certification hierarchy to determine if user has access to one of the parents.
        return (cert.getParent() != null && CertificationAuthorizer.isAuthorized(cert.getParent(), workItemId, baseBean));
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Action Methods
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to go back to the previous page in the navigation history.
     */
    public String back() throws GeneralException {
        return NavigationHistory.getInstance().back();
    }

    public String sign() throws GeneralException, ExpiredPasswordException {
        try {
            if(certificationId!=null) {
    
                Certification cert = getContext().getObjectById(Certification.class, certificationId);
                // Sign off on the certification.  If there were any problems,
                // send them back to the UI.
                Certificationer certificationer = new Certificationer(getContext());
                if(cert!=null) {
                    List<Message> errors;
                    try {
                        errors = certificationer.sign(cert, getLoggedInUser(), true, getNativeAuthId(), getSignaturePass(), getLocale());
                    } catch (ObjectAlreadyLockedException e) {
                        //Swallow this and get the error messages
                        errors = certificationer.getErrors();
                    }
                    
                    // If username was passed in, store it for future use.
                    String sai = getSignatureAuthId();
                    String oai = getOriginalAuthId();
                    if(sai != null && !sai.equals("") && (oai == null || oai.equals(""))) {
                        getSessionScope().put(LoginBean.ORIGINAL_AUTH_ID, sai);
                    }
    
                    // Go back to the same page if there were errors.
                    if (errors!=null && !errors.isEmpty()) {
                        for (Message s : errors) {
                            // no need to add to session since we are going back to same page
                            addErrorMessage("", s, null);
                        }
                        return null;
                    }
                    
                    String returnStr = backToNonCertPage();
    
                    if(returnStr==null)
                        return back();
                    else
                        return returnStr;
                }
            }
            return null;
        }
        finally {
            setSignaturePass(null); // Don't store the password!
            setSignatureAuthId(null);
        }
    }
    
    /**
     * Action method which forwards you to view the selected child certification
     */
    public String viewSubCert() throws GeneralException{
       NavigationHistory.getInstance().saveHistory(this);
       CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(getSubCertId());
       return certPrefBean.getDefaultView();
    }

    /**
     * Action to rescind the given certification back into its parent.
     */
    public String rescindChildCertification() throws GeneralException {

        Certificationer certificationer = new Certificationer(getContext());

        Certification cert = getContext().getObjectById(Certification.class, rescindCertId);

        if (cert != null) {
            try {
                certificationer.rescindChildCertification(cert);
            }
            catch(ModifyImmutableException exception) {
                this.addMessage(exception);
            }
        }

        return "rescindChildCertificationSuccess";
    }

    /**
     *
     * @return True if the the certification second pass is not necessary.
     * THe second pass determines whether we should display the remediation
     * pop-up. If the cert is not editable, or a default revoker is configured 
     * and there are no modifiable entitlements in the system, we can skip the 
     * second pass, which helps performance in many cases.
     * @throws GeneralException
     */
    public boolean isRequiresSecondPass() throws GeneralException{

        //If it's not editable, we don't need any second pass info
        boolean requiresSecondPass = isEditable();

        if (requiresSecondPass) {
            Configuration sysConfig = Configuration.getSystemConfig();

            // This is a really old option created for POCs and should almost never
            // be used. We still have to respect it though.
            boolean forceShowDialog = sysConfig.getBoolean(Configuration.SHOW_AUTO_REMEDIATION_DIALOG, false);

            String defaultRemediator = sysConfig.getString(Configuration.DEFAULT_REMEDIATOR);

            if (!forceShowDialog && defaultRemediator != null){
                QueryOptions ops = new QueryOptions();
                ops.setDistinct(true);
                ops.add(Filter.and(Filter.notnull("schemas.attributes.remediationModificationType"),
                        Filter.not(Filter.eq("schemas.attributes.remediationModificationType",
                                AttributeDefinition.UserInterfaceInputType.None))));

                requiresSecondPass = (getContext().countObjects(Application.class, ops) > 0);
            }
        }

        return requiresSecondPass;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Utility Methods
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Go back through the Navigation History until to last page 
     * that was not cert related
     * @return
     */
    protected String backToNonCertPage() throws GeneralException {
        String returnStr = null;
        while (true) {
            returnStr = NavigationHistory.getInstance().back();
            //if no history, break out and keep null
            if (returnStr == null) {
                // If we didn't find any history, maybe it's because
                // we got here directly from login.  Reload the cert.
                // Otherwise, actions that change the state of the cert
                // won't be recognized.
                initCertification();
                break;
            }
            
            //certification view page state will be a 10 element array 
            //with 'certificationViewPage' at last element
            //if not one of these, then use it to get out of the cert
            Object backState = NavigationHistory.getInstance().peekPageState();
            if (backState == null ||
                 !(backState instanceof Object[]) ||
                 ((Object[])backState).length != 10 ||
                 ((Object[])backState)[9] != PAGE_STATE_FLAG) {
                break;
            }
        }
        return returnStr;
    }



    public static class ActionOption {
        private String name;
        private String displayName;

        public ActionOption(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

}
