/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 *
 * NOTE: This class is included in the pubic javadocs.
 *
 */

package sailpoint.web.certification;

import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.CertificationService;
import sailpoint.api.Certificationer;
import sailpoint.api.IdentityService;
import sailpoint.api.Meter;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.DataOwnerCertifiableEntity;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.AbstractCertificationItem.ContinuousState;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.ContinuousStateConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.SailPointObject;
import sailpoint.object.SignOffHistory;
import sailpoint.object.UIPreferences;
import sailpoint.service.ScorecardDTO;
import sailpoint.service.certification.CertificationEntityService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.LoadingMap;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.AbstractQueryingPager;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.LoginBean;
import sailpoint.web.group.AccountGroupBean;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.workitem.WorkItemNavigationUtil;


/**
 * A JSF UI bean which is used for a user to certify to entitlements.
 */
public class CertificationBean
    extends BaseObjectBean<Certification>
    implements NavigationHistory.Page {
    
    private static final Log log = LogFactory.getLog(CertificationBean.class);

    private static final int DEFAULT_SUBCERTS_PER_PAGE = 10;

    public static final String CONTINUOUS_GRID_STATE_PREFIX = "continuous_";
    
    private static final String CERT_PREV_IDENTITY_ID = "CertificationPreviousIdentityId";
    private static final String CERT_NEXT_IDENTITY_ID = "CertificationNextIdentityId";
    private static final String CERT_CURRENT_INDEX = "CertificationCurrentIndex";
    private static final String TOTAL_CERTIFICATION_IDENTITIES = "totalCertificationIdentities";
    private static final String ENTITY_PAGING_INFO_CALCULATED = "certEntityPagingInfoCalculated";

    private static final String DETAILED_VIEW_FILTER_SET = "detailedViewFilterSet";
    private static final String CHECK_PRF = "checkPrf_";
    private static final String CHECKED_ALREADY = "checkedAlready";
    private static final String IMPLIED_FILTER_STATE = "impliedFilterState";
    
    /**
     * The ID of the current CertificationEntity being viewed.
     */
    private String currentCertificationIdentityId;

    /**
     * A cached copy of the current CertificationEntity being viewed.
     */
    private transient CertificationEntity currentCertificationEntity;
    
    /**
     * The ID of the CertificationEntity that is being navigated to.
     */
    private String previousOrNextCertificationIdentityId;

    /**
     * Read-only fields to keep track of the prev/next certification entity.
     * These are stored on the session and cached in these fields once read.
     */
    private String nextCertificationIdentityId;
    private String prevCertificationIdentityId;
    private int currentCertificationIdentityIndex;
    private int totalCertificationIdentities;

    private List<String> certifierDisplayNames;

    /**
     * Whether the certification live grid should show entities or (if false)
     * items.
     */
    private Boolean listEntities;

    private Boolean showDelegateLegend;
    private Boolean showRemedationCompletion;
    
    /**
     * Certification Entity Bulk Operations
     */
    
    private Boolean allowCertificationEntityBulkApprove;
    private Boolean allowCertificationEntityBulkRevocation;
    private Boolean allowCertificationEntityBulkAccountRevocation;
    
    /**
     * Whether any of the exception beans have an instance (for example - for a template
     * application).
     */
    private boolean exceptionsHaveInstance;
    
    private List<CertificationItemBean.BusinessRoleBean> businessRoles;
    private List<CertificationItemBean.ExceptionBean> exceptions;
    private List<CertificationItemBean.ViolationBean> violations;
    private List<CertificationItemBean.ProfileBean> profiles;
    private List<CertificationItemBean.GenericCertifiableBean> businessRoleRelationshipBeans;
    private List<CertificationItemBean.GenericCertifiableBean> businessRoleGrantBeans;

    private boolean detailViewPostback;
    private String entityCustom1;
    private String entityCustom2;
    private Map<String,Object> entityCustomMap;
    
    private IdentityDTO identityDTO;
    private CertificationIdentityDifferencesBean diffs;

    /**
     * Map of item ID to the decision status for items that have been modified.
     */
    private Map<String,String> itemDecisions;

    private String customEntityPageInclude;
    private String customEntityHeadersInclude;
    private String customEntityColumnsInclude;
    private String customItemRowsInclude;

    private Date expirationDate;
    private boolean expirationDateCalculated = false;
    private boolean editable;

    private SubCertPager subCertPager;
    private List<CertificationPercentBean.CertificationExpirationBean> subCertifications;

    // Used to navigate to another object from a certification.
    private String selectedWorkItemId;
    private String selectedCertificationId;

    // This bean is stored within the certification bean because sometimes
    // decisions are made (and unsaved) when an action that requires a popup
    // is saved.  The popup action is copied into this bean upon save so that
    // both the popup action and any unsaved approvals can be saved.
    private CertificationActionBean action;

    private SaveListener listener;
    private String workItemId;

    // TODO: Push this down to the AbstractCertificationContentsListBean if we
    // start letting the CertificationBean manage its list bean.  This will
    // allow different states for different lists (identity vs. item).
    private GridState gridState;
    
    /**
     * Identity snapshot for certification entity
     */
    private IdentitySnapshot identitySnapshot = null;

    /**
     * Scores are displayed from here at the ui level. This bean is created from 
     * score card in identity snapshot. 
     */
    private ScorecardDTO scorecardBean = null;
    
    /**
     * Track certificate Summary Display last state in terms of collapsed (minimized)
     * or expanded. 
     */
    private boolean trackSummaryLastStateMinimized = false;    
    
    private boolean checkDisplayDetailedViewFilterSet = false;

    /**
     * Indicates whether to display an entitlement's name or description
     * when displaying in the identity view. The value comes from the certification
     * but might be overridden by the user's preference settings.
     */
    private Boolean displayEntitlementDescription;

    private Boolean enableApproveAccount;

    private Boolean enableRevokeAccount;
    
    private CertificationDefinition definition;
    
    private Boolean entityDelegationEnabled;
    
    private Boolean delegationForwardDisabled;

    private Boolean requireBulkCertificationConfirmation;

    private Boolean allowExceptionPopup;
    
    private Boolean automateSignoffPopup;
    
    private boolean displayStartupHelp = true;


    /**
     * Used to construct paged results within a certification
     */
    public static class SubCertPager
        extends AbstractQueryingPager
        implements Serializable {

        // Transient because this needs to be reattached explicitly.
        private transient CertificationBean certBean;
        
        
        /**
         * Default constructor required for reflective instantiation.
         */
        public SubCertPager(int num, CertificationBean bean, QueryOptions qo) {
            super(num, qo, Certification.class);
            this.attach(bean);
        }

        /**
         * Attach this pager to the given CertificationBean.
         */
        public void attach(CertificationBean bean) {
            super.attach(bean);
            this.certBean = bean;
        }

        protected void prevNextFired() throws GeneralException {
            // Reset the items so they get recalculated.
            this.certBean.resetSubCerts();
        }
    } 

    /**
     * A listener interface that is notified when the certification bean is saved.
     */
    public static interface SaveListener
    {
        public void certificationSaved();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     * 
     * @deprecated Instead use {@link #CertificationBean(Certification, CertificationEntity, SaveListener, String)}.
     *             The default constructor should only be used when JSF auto-creates the bean because
     *             certain request parameters are expected.
     */
    @Deprecated
    public CertificationBean() throws GeneralException
    {
        super();
        setScope(Certification.class);
        setStoredOnSession(false);

        setSkipAuthorization(true);

        // Try to pull this off of the request, so we can access the certification
        // bean from AJAX requests (ie - from the live grid).
        String certId = Util.getString(super.getRequestParameter("certificationId"));
        if (certId == null)
            certId = getRequestParameter("editForm:certificationId");
        if (certId != null)
            this.setObjectId(certId);


        // Retrieve the work item ID for requests from delegated items when this
        // is coming from a popup.
        String workItemId = Util.getString(super.getRequestParameter("workItemId"));
        if (null != workItemId) {
            this.workItemId = workItemId;
        }

        String entityId = super.getRequestOrSessionParameter("editForm:currentCertificationIdentityId");
        if (entityId== null)
            entityId = super.getRequestOrSessionParameter("certificationIdentityId");
        if (entityId== null)
            entityId = super.getRequestOrSessionParameter("entityId");
        if (null != entityId)
        {
            this.currentCertificationIdentityId = entityId;
        }

        if (getObjectId() == null && this.currentCertificationIdentityId != null){
            QueryOptions ops = new QueryOptions(Filter.eq("id", currentCertificationIdentityId));
            Iterator<Object[]> result = getContext().search(CertificationEntity.class,
                    ops, Arrays.asList("certification.id"));
            if (result != null && result.hasNext())
                certId = (String)result.next()[0];
            setObjectId(certId);
        }


        Identity currentUser = retrieveCurrentUserLoggedIn();
        Certification cert = this.getObject();
        if ((null != currentUser) && (null != cert)) {

            // Don't allow loading by name to protect against security breaches!
            // Hackers beware!  See bug 6099.  We'll get rid of this nonsense
            // soon and really protect like we should.
            String id = Util.getString(super.getObjectId());
            if ((null != id) && (!id.equals(cert.getId()))) {
                throw new GeneralException("Stop intruder!!  Cannot load certification by name: " + id);
            }

            // Check if the user is an owner of the cert.
            if (null != cert.getCertifiers()) {
                this.editable = CertificationAuthorizer.isCertifier(currentUser,cert.getCertifiers());
            }

            // Check if this user is the certifier for a parent cert if this is
            // a bulk reassignment.
            if (!this.editable) {
                this.editable = CertificationAuthorizer.isReassignmentParentOwner(currentUser, cert);
            }

            // If not the owner of the cert, check to see if the logged in user
            // is a cert super admin.
            if (!this.editable) {
                this.editable = CertificationAuthorizer.isCertificationAdmin(this);
            }   
        }

        if (cert != null) {
            definition = cert.getCertificationDefinition(getContext());
            if (definition == null) {
                definition = new CertificationDefinition();
                definition.initialize(getContext());
            }
            
            // Check for a staged phase or pending certification with staging enabled..
            if (Phase.Staged.equals(cert.getPhase()) || (cert.getPhase() == null && definition.isStagingEnabled())) {
                this.editable = false;
            }
        }
    }
    
    /**
     * Constructor to edit an CertificationIdentity.
     * 
     * @param  cert        The Certification that the identity is a part of.
     * @param  identity    The CertificationIdentity to be edited.
     * @param  listener    The SaveListener to notify when the certification is
     *                     saved.
     * @param  workItemId  The ID of the work item in which this certification
     *                     is being edited.
     */
    public CertificationBean(Certification cert, CertificationEntity identity,
                             SaveListener listener, String workItemId)
        throws GeneralException {
        
        super();

        setSkipAuthorization(true);

        setScope(Certification.class);
        setObjectId(cert.getId());
        setStoredOnSession(false);

        this.currentCertificationIdentityId =
            (null != identity) ? identity.getId() : null;
        this.listener = listener;
        this.workItemId = workItemId;

        selectChosenEntity(true);
        
        if (cert != null) {
            definition = cert.getCertificationDefinition(getContext());
        }
    }




    ////////////////////////////////////////////////////////////////////////////
    //
    // NAVIGATION HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * This stores information in the session so that after redirecting, the
     * CertificationBean will have the information it needs. This also clears
     * the filter on the CertificationIdentitiesListBean.
     * 
     * @param  context  The FacesContext to use.
     * @param  certId   The ID of the certification is about to be viewed.
     */
    public static void viewCertification(FacesContext context, String certId) {
        viewCertification(context, certId, true);
    }

    /**
     * This stores information in the session so that after redirecting, the
     * CertificationBean will have the information it needs. This also clears
     * the filter on the CertificationIdentitiesListBean if resetFilter is true.
     * 
     * @param  context      The FacesContext to use.
     * @param  certId       The ID of the certification is about to be viewed.
     * @param  resetFilter  Whether to reset the filter or not.
     */
    public static void viewCertification(FacesContext context, String certId,
                                         boolean resetFilter) {

        context.getExternalContext().getSessionMap().put("editForm:id", certId);
        if (resetFilter) {
            CertificationEntityListBean.resetFilter(context);
            CertificationItemsListBean.resetFilter(context);
            resetCheckDisplayDetailedFilterSet(context);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // UI BEAN PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    
    public List<Certification.Phase> getPhases () {
    	List<Certification.Phase> list = new ArrayList<Certification.Phase>();
    	for (Certification.Phase phase : Certification.Phase.values())
        {
            list.add(phase);
        }
    	return list;
    	
    }
    
    public List<Certification.Type> getTypes () {
    	List<Certification.Type> list = new ArrayList<Certification.Type>();
    	for (Certification.Type type : Certification.Type.values())
        {
            list.add(type);
        }
    	return list;
    	
    }

    private boolean calculatedCustomEntityPageInclude;
    private boolean calculatedCustomItemRowsInclude;
    
    /**
     * 
     * @return The path to an xhtml file to include to render custom information at the
     * top of the certification entity page.
     * @throws GeneralException
     */
    public String getCustomEntityPageInclude() throws GeneralException {
        if (!this.calculatedCustomEntityPageInclude) {
            this.customEntityPageInclude =
                super.getUIConfig().getCustomCertificationEntityPageInclude();
            this.calculatedCustomEntityPageInclude = true;
        }
        return this.customEntityPageInclude;
    }

    /**
     * 
     * @return The path to an xhtml file to include to render custom rows for the
     * certification entity page (for delegated items).
     * @throws GeneralException
     */
    public String getCustomItemRowsInclude() throws GeneralException {
        if (!this.calculatedCustomItemRowsInclude) {
            this.customItemRowsInclude =
                super.getUIConfig().getCustomCertificationItemRowsInclude();
            this.calculatedCustomItemRowsInclude = true;
        }
        return this.customItemRowsInclude;
    }

    /**
     * 
     * @return Certification expiration date
     * @throws GeneralException
     */
    public Date getExpirationDate() throws GeneralException
    {
        if (!this.expirationDateCalculated && super.getObject() != null)
        {
            this.expirationDate = super.getObject().getExpiration();
            this.expirationDateCalculated = true;
        }
        return this.expirationDate;
    }

    /**
     * Return the number of days left to complete this certification. If the
     * certification does not have an expiration date, this returns
     * Integer.MIN_VALUE.
     * 
     * @return  The number of days left to complete this certification, or
     *          Integer.MIN_VALUE if the certification does not have an expiration
     *          date.
     */
    public int getDaysRemaining() throws GeneralException
    {
        Date expiration = getExpirationDate();
        Date now = new Date();
        return Util.getDaysDifference(expiration, now);
    }

    /**
     * Return the number of days left in the current phase. If there is not a
     * duration for the current phase, this returns Integer.MIN_VALUE.
     * 
     * @return  The number of days left in the current phase, or
     *          Integer.MIN_VALUE if the phase does not have a duration
     */
    public int getDaysRemainingInPhase() throws GeneralException
    {
        Date expiration = super.getObject().getNextPhaseTransition();
        Date now = new Date();
        return Util.getDaysDifference(expiration, now);
    }

    /**
     * @return the localized duration of the continuous "certified" period.
     */
    public String getCertifiedDuration() throws GeneralException {
        return getDuration(ContinuousState.Certified);
    }

    /**
     * @return the localized duration of the continuous "certification required"
     * period.
     */
    public String getCertificationRequiredDuration() throws GeneralException {
        return getDuration(ContinuousState.CertificationRequired);
    }

    /**
     * Return the localized duration of the continuous state.
     */
    private String getDuration(ContinuousState state) throws GeneralException {
        
        String duration = null;
        Certification cert = getObject();
        if (null != cert) {
            ContinuousStateConfig config = cert.getContinuousConfig(state);
            if ((null != config) && (null != config.getDuration())) {
                duration = config.getDuration().format(super.getLocale());
            }
        }
        return duration;
    }

    /**
     * Return a list of certifier display names. If the identity can be looked up successfully,  
     * the identity's displayable name is returned, otherwise whatever is on the certification certifiers list is returned.
     * 
     * @return list of certifier display names
     * @throws GeneralException
     */
    public List<String> getCertifiersDisplayNames() throws GeneralException{

        if (certifierDisplayNames == null){
            List<String> names = getObject() != null ? getObject().getCertifiers() : null;
            if (names != null){
                certifierDisplayNames = CertificationUtil.getCertifiersDisplayNames(getContext(), names);
            }
        }

        return certifierDisplayNames;
    }

    
    public void setCurrentCertificationIdentityId(String s)
    {
        this.currentCertificationIdentityId = s;
    }

    public String getCurrentCertificationIdentityId()
    {
        return this.currentCertificationIdentityId;
    }

    public String getPreviousOrNextCertificationIdentityId() {
        return previousOrNextCertificationIdentityId;
    }

    public void setPreviousOrNextCertificationIdentityId(String id) {
        this.previousOrNextCertificationIdentityId = id;
    }

    /**
     * 
     * True if preferences stored on the logged in identity are set to show list entities view, or
     * the certification definition is configured to default to list entities, or the global certification config
     * is configured to show list entities by default. The checks are done in that order.
     * 
     * @return true if list entities view should be shown
     * @throws GeneralException
     */
    public boolean isListEntities() throws GeneralException {

        // If this hasn't been set, decide which view to use as the default.
        if (null == this.listEntities) {

            // If we support listing items for this certification, check the
            // preference.
            if (this.getSupportsListItems()) {
                Identity user = retrieveCurrentUserLoggedIn();
                Object pref = user.getUIPreference(UIPreferences.PRF_CERT_PAGE_LIST_ITEMS);
                if (null != pref) {
                    this.listEntities = !Util.otob(pref);
                }
                else if (definition != null) {
                    this.listEntities = !definition.getCertPageListItems(getContext());
                }
                else {
                    // Look in system config for a global default.  If not found,
                    // show the item view.
                    Configuration sysConfig = Configuration.getSystemConfig();
                    this.listEntities =!sysConfig.getBoolean(Configuration.CERT_PAGE_LIST_ITEMS, true);
                }
            }
            else {
                this.listEntities = true;
            }
        }

        return this.listEntities;
    }

    public void setListEntities(boolean b) {
        this.listEntities = b;
    }

    /**
     * True if the certification allows item delegation
     * 
     * @return True if the delegate legend should be shown
     * @throws GeneralException
     */
    public boolean isShowDelegateLegend() throws GeneralException{
        if (null == this.showDelegateLegend) {

            if (definition != null) {
                this.showDelegateLegend = definition.isAllowItemDelegation(getContext());
            }
            else {
                Configuration config = Configuration.getSystemConfig();
                this.showDelegateLegend =
                    config.getBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED, false);
            }
        }
        
        return this.showDelegateLegend;
    }
    
    /**
     * True if identity type certification and the definition allows for account revocation.
     * If there is no definition, fall back to the global certification configuration.
     * 
     * @return true if the certification allows for revoking accounts
     * @throws GeneralException
     */
    public boolean isRevokeAccountAllowed() throws GeneralException {

        if (enableRevokeAccount == null) {
            enableRevokeAccount = false;
            boolean revokeAccountAllowed = false;
            Certification cert = getObject();
            if (null != cert) {
                Certification.Type type = cert.getType();

                // Allow revoke account for identity certs (except for business
                // role
                // membership - we don't allow revoking accounts through roles
                // yet).
                revokeAccountAllowed = (type.isIdentity() && !Certification.Type.BusinessRoleMembership
                        .equals(type));

                if (revokeAccountAllowed) {
                    if (definition != null) {
                        enableRevokeAccount = definition.isAllowAccountRevocation(getContext());
                    }
                }
            }

            if (enableRevokeAccount == null) {
                Configuration sysConfig = getContext().getConfiguration();
                enableRevokeAccount = sysConfig.getBoolean(Configuration.ENABLE_ACCOUNT_REVOKE_ACTION);
            }
        }
        
        return enableRevokeAccount;
    }
    
    public boolean isIdentitySelected()
    {
        return (null != this.currentCertificationIdentityId) &&
               (this.currentCertificationIdentityId.trim().length() > 0);
    }

    public boolean isEditable() {
        return this.editable;
    }

    /**
     * 
     * @return true if the Certification is ready for sign~off
     * @throws GeneralException
     */
    public boolean isReadyForSignOff() throws GeneralException
    {
        boolean ready = false;

        Certification cert = super.getObject();
        if (null != cert) {

            CertificationService svc = new CertificationService(getContext());
            ready = svc.isReadyForSignOff(cert);
        }

        return ready;
    }
    
    /**
     * 
     * @return true if the Certification is ready for signoff, and is configured for default signoff popup.
     * @throws GeneralException
     */
    public boolean isPromptForSignOff() throws GeneralException 
    {
        if (automateSignoffPopup == null) {
            if (definition != null) {
                automateSignoffPopup = definition.isAutomateSignoffPopup(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                automateSignoffPopup = sysConfig.getBoolean(Configuration.AUTOMATE_SIGNOFF_POPUP, false);
            }
        }
        
        return automateSignoffPopup && isReadyForSignOff() && isEditable();
    }
    
    /**
     * 
     * @return true if the Certification has been signed
     * @throws GeneralException
     */
    public boolean isSignedOff() throws GeneralException
    {
        boolean signOffDone = false;

        Certification cert = super.getObject();
        if (null != cert) {

            signOffDone = cert.hasBeenSigned();
        }

        return signOffDone;
    }
    
    /**
     * 
     * @return true if the certification has been electronically signed
     * @throws GeneralException
     */
    public boolean isElectronicSignedOff() throws GeneralException {
    	boolean electronicSignOffDone = false;
    	
    	Certification cert = super.getObject();
    	if (null != cert) {
    		List<SignOffHistory> sohList = cert.getSignOffHistory();
    		for (SignOffHistory soh : sohList) {
				if (soh.isElectronicSign()) {
					electronicSignOffDone = true;
					break;
				}
			}
    	}
    	
    	return electronicSignOffDone;
    }
    
    /**
     * 
     * @return JSON representation of certification SignOffHistory
     */
    public String getSignOffHistoryJSON() {
    	final Writer jsonString = new StringWriter();
    	final JSONWriter jsonWriter = new JSONWriter(jsonString);
    	
    	try {
    		
			jsonWriter.object();
			jsonWriter.key("electronicSignedOff");
			jsonWriter.value(isElectronicSignedOff());
			
			List<SignOffHistory> sohList = super.getObject().getSignOffHistory();
			List<JSONObject> jsonSOHList = new ArrayList<JSONObject>();
			for (SignOffHistory soh : sohList) {
				jsonSOHList.add(new JSONObject(soh));
			}
			
			jsonWriter.key("items");
			jsonWriter.value(jsonSOHList);
			
			jsonWriter.endObject();
			
    	} catch (JSONException e) {
			log.error(e);
			return JsonHelper.failure();
		} catch (GeneralException e) {
			log.error(e);
			return JsonHelper.failure();
		}
    	
    	return jsonString.toString();
    }

    /**
     * @return the appropriate instruction text based on the state of the
     * certification.
     */
    public String getInstructionText() throws GeneralException {
        // Note: This logic was extracted out of the xhtml page because using
        // rendered attributes with a bunch of duplicated logic was getting
        // cumbersome.  Still not pretty, but I'd rather have all of the if
        // statements here than in the xhtml.

        String msg = null;
        if (this.isEditable()) {
            Certification cert = this.getObject();
            CertificationService svc = new CertificationService(getContext());
            msg = svc.getSignOffBlockedReason(cert);
        }
        
        return (null != msg) ? Internationalizer.getMessage(msg, super.getLocale()) : null;
    }
    
    /**
     * @return whether or not to show the "remediation completion" progress bar
     * in the remediation grid.
     */
    public boolean isShowRemediationCompletion() throws GeneralException {
        
        if (null == this.showRemedationCompletion) {
            Certification cert = getObject();
            if (null != cert) {
                if (cert.isRemediationPhaseEnabled()) {
                    // If processing revokes immediately, count the items in
                    // remediation phase.
                    if (cert.isProcessRevokesImmediately()) {
                        List<Certification.Phase> remedPhases = new ArrayList<Certification.Phase>();
                        remedPhases.add(Certification.Phase.Remediation);
                        remedPhases.add(Certification.Phase.End);

                        QueryOptions qo = new QueryOptions();
                        qo.add(Filter.in("phase", remedPhases));
                        qo.add(Filter.eq("parent.certification", cert));
                        int cnt = getContext().countObjects(CertificationItem.class, qo);
                        this.showRemedationCompletion = (cnt > 0);
                    }
                    else {
                        // Check if the cert is on or past remediation phase.
                        this.showRemedationCompletion = cert.isOnOrPastRemediationPhase();
                    }
                }
            }
        }

        return (null != this.showRemedationCompletion) ? this.showRemedationCompletion : false;
    }

    /**
     * Indicates that remediations (revokes or approves with provisioning) will
     * be executed immediately after the decision is made. As a result, the
     * decision radios will lock once a decision is made.
     * 
     * @return true if the certification is configured to process revokes immediately
     */
    public boolean isProcessRevokesImmediately() throws GeneralException{
        Certification cert = getObject();
        return cert != null && cert.isProcessRevokesImmediately();
    }

    protected QueryOptions addScopeExtensions(QueryOptions ops){
        ops.setUnscopedGloballyAccessible(false);
        return ops;
    }

    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException{

        return CertificationAuthorizer.isAuthorized((Certification) object, this.workItemId, this);
    }
    
    /**
     * If there is a certification definition present, return true if the definition is configured to allow approve accounts
     * If there is not certification definition, return the System configuration value.
     * 
     * @return true if approve account is allowed
     * @throws GeneralException
     */
    public boolean isApproveAccountAllowed() throws GeneralException {

        if (enableApproveAccount == null){

            if (definition != null) {
                enableApproveAccount = definition.isAllowApproveAccounts(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                enableApproveAccount = sysConfig.getBoolean(
                                     Configuration.ENABLE_ACCOUNT_APPROVE_ACTION);
            }
        }
        return enableApproveAccount;
    }
    
    /**
     * If there is a certification definition present, return true if the definition is configured to allow entity delegation
     * If there is no certification definition, return the value from System Configuration
     * 
     * @return true if entity delegation is configured
     * @throws GeneralException
     */
    public boolean isEntityDelegationEnabled()  throws GeneralException {
        if (entityDelegationEnabled == null){
            if (definition != null) {
                entityDelegationEnabled = definition.isAllowEntityDelegation(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                entityDelegationEnabled = sysConfig.getBoolean(
                                     Configuration.CERTIFICATION_ENTITY_DELEGATION_ENABLED);
            }
        }
        return entityDelegationEnabled;
    }

    /**
     * If there is a certification definition present, return true if the definition is configured to disable delegation forwarding
     * If there is no definition, return the value from system configuration 
     * 
     * @return true if delegation forwarding is disabled
     * @throws GeneralException
     */
    public boolean isDelegationForwardingDisabled()  throws GeneralException {
        if (delegationForwardDisabled == null){
            if (definition != null) {
                delegationForwardDisabled = definition.isDelegationForwardingDisabled();
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                delegationForwardDisabled = sysConfig.getBoolean(
                                     Configuration.CERTIFICATION_DISABLE_DELEGATION_FORWARDING);
            }
        }
        return delegationForwardDisabled;
    }

    public void setEntityDelegationEnabled(boolean entityDelegationEnabled) {
        this.entityDelegationEnabled = entityDelegationEnabled;
    }

    /**
     * If there is a certification definition present, return the configured value in the definition
     * If there is no definition, return the value from system configuration
     * 
     * @return true if the certification requires confirmation to bulk certify
     * @throws GeneralException
     */
    public boolean isRequireBulkCertificationConfirmation() throws GeneralException {
        if (requireBulkCertificationConfirmation == null) {
            if (definition != null) {
                requireBulkCertificationConfirmation = definition.isRequireBulkCertifyConfirmation(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                requireBulkCertificationConfirmation = sysConfig.getBoolean(
                                     Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION);
            }
        }
        return requireBulkCertificationConfirmation;
    }
    
    public void setRequireBulkCertificationConfirmation(boolean require) {
        this.requireBulkCertificationConfirmation = require;
    }
    
    /**
     * Specifies that approve all should be available on the certification
     * entity page.
     * 
     * If certification definition present, get the value from the definition
     * If no certification definition, return the system configuration value
     * 
     * @return true if allowed to approval all on the certification entity page
     * @throws GeneralException
     */
    public boolean isAllowCertificationEntityBulkApprove()  throws GeneralException {
        if (allowCertificationEntityBulkApprove == null) {
            if (definition != null) {
                allowCertificationEntityBulkApprove = definition.isAllowEntityBulkApprove(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                allowCertificationEntityBulkApprove = 
                    sysConfig.getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_APPROVE, false);
            }
        }
        return allowCertificationEntityBulkApprove;
    }
    
    public void setAllowCertificationEntityBulkApprove(boolean allow) {
        allowCertificationEntityBulkApprove = allow;
    }
    
    /**
     * Specifies that revoke all should be available on the certification
     * entity page.
     * 
     * Value from certification definition config if definition is present.
     * If no definition, return system config value
     * 
     * @return true if allowed to revoke all on the certification entity page
     * @throws GeneralException
     */
    public boolean isAllowCertificationEntityBulkRevocation()  throws GeneralException {
        if (allowCertificationEntityBulkRevocation == null) {
            if (definition != null) {
                allowCertificationEntityBulkRevocation = definition.isAllowEntityBulkRevocation(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                allowCertificationEntityBulkRevocation = 
                    sysConfig.getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_REVOCATION, false);
            }
        }
        return allowCertificationEntityBulkRevocation;
    }
    
    public void setAllowCertificationEntityBulkRevocation(boolean allow) {
        allowCertificationEntityBulkRevocation = allow;
    }
    
    /**
     * Specifies that revoke all accounts should be available on the
     * certification entity page.
     * 
     * Value from certification definition config if definition is present.
     * If no definition, return system config value
     * 
     * @return true if revoke allowing bulk account revocation on the certification entity page
     * @throws GeneralException
     */
    public boolean isAllowCertificationEntityBulkAccountRevocation()  throws GeneralException {
        if (allowCertificationEntityBulkAccountRevocation == null) {
            if (definition != null) {
                allowCertificationEntityBulkAccountRevocation = definition.isAllowEntityBulkAccountRevocation(getContext());
            }
            else {
                Configuration sysConfig = getContext().getConfiguration();
                allowCertificationEntityBulkAccountRevocation = 
                    sysConfig.getBoolean(Configuration.ALLOW_CERT_ENTITY_BULK_ACCOUNT_REVOCATION, false);
            }
        }
        return allowCertificationEntityBulkAccountRevocation;
    }
    
    public void setAllowCertificationEntityBulkAccountRevocation(boolean allow) {
        allowCertificationEntityBulkAccountRevocation = allow;
    }
    
    /**
     * Enables certifiers to view the allow exception pop-up and manually set expiration dates and add comments. 
     * This applies to both violation and non-violation items.
     * 
     * This can only be set on the definition. There is no system config option for this.
     * 
     * @return True if the certification is configured to allow setting exception pop-up
     * @throws GeneralException
     */
    public boolean isAllowExceptionPopup() throws GeneralException {
        if (allowExceptionPopup == null) {
            if (definition != null) {
                allowExceptionPopup = definition.isAllowExceptionPopup(getContext());
            }
            else {
                allowExceptionPopup = false; // no sys config value exists.
            }
        }
        return allowExceptionPopup;
    }
    
    public void setAllowDefaultExceptionDuration(boolean allow) {
        allowExceptionPopup = allow;
    }
    
    public GridState getGridState() {
        if (null == this.gridState) {
            this.gridState = loadGridState();
        }
        return this.gridState;
    }

    public void setGridState(GridState gridState) {
        this.gridState = gridState;
    }

    public String getSelectedWorkItemId() {
        return this.selectedWorkItemId;
    }

    public void setSelectedWorkItemId(String workItem) {
        this.selectedWorkItemId = workItem;
    }

    public String getSelectedCertificationId() {
        return selectedCertificationId;
    }

    public void setSelectedCertificationId(String selectedCertificationId) {
        this.selectedCertificationId = selectedCertificationId;
    }

    public CertificationEntity getCertificationIdentity(String identId) throws GeneralException
    {
        if(null != identId) 
            return getContext().getObjectById(CertificationEntity.class, identId);
        return null;
    }

    public CertificationEntity getCurrentCertificationIdentity() throws GeneralException
    {
        if ((null == this.currentCertificationEntity) && 
            (null != this.currentCertificationIdentityId)) {
            this.currentCertificationEntity =
                getContext().getObjectById(CertificationEntity.class, this.currentCertificationIdentityId);
        }

        return this.currentCertificationEntity;
    }
    
    public String getCurrentCertificationEntityName() 
        throws GeneralException {
        
        CertificationEntity entity = getCurrentCertificationIdentity();
        if (entity == null) {
            return null;
        }
        
        if (isDataOwnerCertification()) {
            return getDataOwnerEntityName(entity);
        } else {
            return getDefaultEntityName(entity);
        }
    }

    public String getCurrentCertificationEntityDescription() 
        throws GeneralException {
        
        CertificationEntity entity = getCurrentCertificationIdentity();
        if (entity == null) {
            return null;
        }
        
        if (isDataOwnerCertification()) {
            return getDataOwnerEntityDescription(entity);
        } else {
            return getDefaultEntityDescription(entity);
        }
    }
    
    private String getDefaultEntityName(CertificationEntity entity) {
        return (entity.getFullname() != null ? entity.getFullname() : entity.getIdentity());
    }

    private String getDefaultEntityDescription(CertificationEntity entity) {
        return (entity.getFullname() != null ? entity.getFullname() : entity.getIdentity());
    }
    
    private String getDataOwnerEntityName(CertificationEntity entity) throws GeneralException {
        
        DataOwnerCertifiableEntity dataOwnerEntity = DataOwnerCertifiableEntity.createFromCertificationEntity(entity);
        return dataOwnerEntity.getDisplayName(getContext(), getLocale());
    }
    
    private String getDataOwnerEntityDescription(CertificationEntity entity) throws GeneralException {

        DataOwnerCertifiableEntity dataOwnerEntity = DataOwnerCertifiableEntity.createFromCertificationEntity(entity);
        return dataOwnerEntity.getDisplayDescription(getContext(), getLocale());
    }
    
    /**
     * @return IdentitySnapshot identity snapshot reference from current certification entity
     */
    public IdentitySnapshot getCurrentCertificationIdentitySnapshot() throws GeneralException
    {
    	if (identitySnapshot == null)
    	{
    		CertificationEntity certEntity = getCurrentCertificationIdentity();
    		if (certEntity != null){
    			identitySnapshot = certEntity.getIdentitySnapshot(getContext());
    		}
    	}
        return identitySnapshot;
    }

    /**
     * @return the nextCertificationIdentityId
     */
    public String getNextCertificationIdentityId() throws GeneralException {
        if(nextCertificationIdentityId == null) {
            calculateIdentityPagingInfoIfNeeded();
            nextCertificationIdentityId = (String)getSessionScope().get(CERT_NEXT_IDENTITY_ID);
        }
        return nextCertificationIdentityId;
    }

    public void setNextCertificationIdentityId(String id) {
        this.nextCertificationIdentityId = id;
        getSessionScope().put(CERT_NEXT_IDENTITY_ID, id);
    }
    
    /**
     * @return the prevCertificationIdentityId
     */
    public String getPrevCertificationIdentityId() throws GeneralException {
        if(prevCertificationIdentityId==null) {
            calculateIdentityPagingInfoIfNeeded();
            prevCertificationIdentityId = (String)getSessionScope().get(CERT_PREV_IDENTITY_ID);
        }
        return prevCertificationIdentityId;
    }

    public void setPrevCertificationIdentityId(String id) {
        this.prevCertificationIdentityId = id;
        getSessionScope().put(CERT_PREV_IDENTITY_ID, id);
    }

    /**
     * @return the currentCeritifcationIdentityIndex
     */
    @SuppressWarnings("rawtypes")
    public int getCurrentCertificationIdentityIndex() throws GeneralException {
        Map session = getSessionScope();
        if((Integer)session.get(CERT_CURRENT_INDEX)!=null) {
            calculateIdentityPagingInfoIfNeeded();
            currentCertificationIdentityIndex = (Integer)session.get(CERT_CURRENT_INDEX);
        }
        else {
            currentCertificationIdentityIndex = 0;
        }
        return currentCertificationIdentityIndex;
    }

    public void setCurrentCertificationIdentityIndex(Integer idx) {
        int i = (null == idx) ? 0 : idx;
        this.currentCertificationIdentityIndex = i;
        getSessionScope().put(CERT_CURRENT_INDEX, i);
    }

    public int getTotalCertificationIdentities() throws GeneralException {
        if(totalCertificationIdentities==0) {
            if((Integer)getSessionScope().get(TOTAL_CERTIFICATION_IDENTITIES)!=null) {
                calculateIdentityPagingInfoIfNeeded();
                totalCertificationIdentities = (Integer)getSessionScope().get(TOTAL_CERTIFICATION_IDENTITIES);
            }
            else {
                totalCertificationIdentities = 0;
            }
        }
        
        /** If it's still 0, set it to 1. **/
        if(totalCertificationIdentities==0)
            setTotalCertificationIdentities(1);

        return totalCertificationIdentities;
    }

    public void setTotalCertificationIdentities(Integer totalCertificationIdentities) {     
        int totalIdentities = (null == totalCertificationIdentities) ? 0 : totalCertificationIdentities;
        this.totalCertificationIdentities = totalIdentities;
        getSessionScope().put(TOTAL_CERTIFICATION_IDENTITIES, this.totalCertificationIdentities);
    }
    
    public boolean getSupportsExportToCSV() throws GeneralException {
        return this.isIdentityCertification() || isDataOwnerCertification();
    }

    public boolean getSupportsListItems() throws GeneralException {
        return this.isIdentityCertification() || isDataOwnerCertification();
    }

    /**
     * Returns true if roles can be created from the certification
     * items in the current certification. This excludes non-identity
     * certifications. Business role membership certifications are
     * also excluded because while they deal with identities they
     * do not include additional entitlements.
     *
     * @return True if roles can be created from certification items.
     * @throws GeneralException
     */
    public boolean isAllowCreatingRolesFromCertification() throws GeneralException{
        return this.isIdentityCertification() && !this.isBusinessRoleMembershipCertification() &&!this.isDataOwnerCertification();
    }

    public boolean isIdentityCertification() throws GeneralException {
        boolean isIdentity = true;
        setSkipAuthorization(true);
        Certification cert = super.getObject();
        if (null != cert) {
            isIdentity = cert.getType().isIdentity();
        }
        return isIdentity;
    }

    public boolean isAccountGroupCertification() throws GeneralException{
        return isAccountGroupMembersCertification() ||
                isAccountGroupPermissionsCertification();
    }

    /**
     * @return True if the current certification is an account group membership
     * certification.
     */
    public boolean isAccountGroupMembersCertification() throws GeneralException{
        return isCertificationType(Certification.Type.AccountGroupMembership);
    }

    /**
     * @return True if the current certification is an account group permissions 
     * certification.
     */
    public boolean isAccountGroupPermissionsCertification() throws GeneralException{
        return isCertificationType(Certification.Type.AccountGroupPermissions);
    }

    public boolean isBusinessRoleCertification() throws GeneralException{
        return isBusinessRoleCompositionCertification();
    }

    public boolean isBusinessRoleCompositionCertification() throws GeneralException{
        return isCertificationType(Certification.Type.BusinessRoleComposition);
    }

    public boolean isBusinessRoleMembershipCertification() throws GeneralException{
        return isCertificationType(Certification.Type.BusinessRoleMembership);
    }
    
    public boolean isDataOwnerCertification() throws GeneralException {
        return isCertificationType(Certification.Type.DataOwner);
    }
    
    private boolean isCertificationType(Certification.Type type) throws GeneralException {

        boolean isType = false;
        Certification cert = super.getObject();
        if (null != cert) {
            isType = type.equals(cert.getType());
        }
        return isType;
    }
    
    public String getEntityTypeName() throws GeneralException {
        
        String key;
        
        if (isAccountGroupCertification()) {
            key = MessageKeys.ACCOUNT_GROUP;
        } else if (isBusinessRoleCertification()) {
            key = MessageKeys.BIZ_ROLE;
        } else if (isDataOwnerCertification()) {
            key = MessageKeys.CERT_ITEM_TBL_HEADER_DATA_ITEM;
        } else {    
            key = MessageKeys.IDENTITY;
        }
        
        return getMessage(key);
    }

    /**
     *
     * @return Descriptive name of the entity type being certified.
     * @throws GeneralException
     */
    public String getEntityDescription() throws GeneralException{
        if (isBusinessRoleCertification()){
            return getMessage(MessageKeys.BUSINESS_ROLE_LCASE);
        }else if(isAccountGroupCertification()){
            return getMessage(MessageKeys.ACCOUNT_GROUP_LCASE);
        }
        else if (!isListEntities()) { 
            return getMessage(MessageKeys.ITEM_LCASE);
        }

        return getMessage(MessageKeys.USER_LCASE);
    }

    public Identity getCurrentIdentity() throws GeneralException
    {
        CertificationEntity certId = getCurrentCertificationIdentity();
        return (null != certId) ? certId.getIdentity(getContext()) : null;
    }
    
    /**
     * Retrieves scorecard for the identity bean and creates a scorecard DTO for the ui
     * @return ScorecardDTO reference to scorecard DTO
     */
    public ScorecardDTO getIdentitySnapshotScorecardBean() throws GeneralException {
        if (null == this.scorecardBean) {
            CertificationEntityService svc = new CertificationEntityService(this.getObject(), this);
            this.scorecardBean = svc.getScorecard(getCurrentCertificationIdentity());
        }
        return scorecardBean;
    }

    public IdentityDTO getIdentityDTO() 
        throws GeneralException {
        
        if (null == this.identityDTO) {
            Identity identity = getCurrentIdentity();
            if (null != identity)
                this.identityDTO = new IdentityDTO(identity);
        }
        return this.identityDTO;
    }

    /**
     * @return the name of the application that is being certified if this is
     * an application owner certification.
     */
    public String getApplicationName() throws GeneralException
    {
        Application app = super.getObject().getApplication(getContext());
        return (null != app) ? app.getName() : null;
    }

    public boolean isExceptionsHaveInstance() throws GeneralException {
        if (null == this.exceptions) {
            initIdentityBeans();
        }

        return this.exceptionsHaveInstance;
    }
    
    public List<CertificationItemBean.BusinessRoleBean> getBusinessRoles() throws GeneralException
    {
        if (null == this.businessRoles)
        {
            initIdentityBeans();
        }
        return this.businessRoles;
    }

    public void setBusinessRoles(List<CertificationItemBean.BusinessRoleBean> businessRoles)
    {
        this.businessRoles = businessRoles;
    }

    public List<CertificationItemBean.ExceptionBean> getExceptions() throws GeneralException
    {
        if (null == this.exceptions)
        {
            initIdentityBeans();
        }
        return this.exceptions;
    }

    public void setExceptions(List<CertificationItemBean.ExceptionBean> exceptions)
    {
        this.exceptions = exceptions;
    }

    public List<CertificationItemBean.ViolationBean> getViolations() throws GeneralException
    {
        if (null == this.violations)
        {
            initIdentityBeans();
        }
        return this.violations;
    }



    public void setViolations(List<CertificationItemBean.ViolationBean> violations)
    {
        this.violations = violations;
    }


    public List<CertificationItemBean.ProfileBean> getProfiles() throws GeneralException{
        if (null == this.profiles)
        {
            initIdentityBeans();
        }
        return this.profiles;
    }

    public void setProfiles(List<CertificationItemBean.ProfileBean> profiles) {
        this.profiles = profiles;
    }


    public List<CertificationItemBean.GenericCertifiableBean> getBusinessRoleRelationshipBeans() {
        return businessRoleRelationshipBeans;
    }

    public void setBusinessRoleRelationshipBeans(List<CertificationItemBean.GenericCertifiableBean>
            businessRoleRelationshipBeans) {
        this.businessRoleRelationshipBeans = businessRoleRelationshipBeans;
    }

    public List<CertificationItemBean.GenericCertifiableBean> getBusinessRoleGrantBeans() {
        return businessRoleGrantBeans;
    }

    public void setBusinessRoleGrantBeans(List<CertificationItemBean.GenericCertifiableBean> businessRoleGrantBeans) {
        this.businessRoleGrantBeans = businessRoleGrantBeans;
    }

    private int getSubCertPagerSize() {
        Configuration config = Configuration.getSystemConfig();
        int size =
            config.getAttributes().getInt(Configuration.CERTIFICATION_SUBCERT_PAGE_SIZE, DEFAULT_SUBCERTS_PER_PAGE);
        return (size <= 0) ? Integer.MAX_VALUE : size;
    }

    public SubCertPager getSubCertPager() {
        if ((null == this.subCertPager) && (null != getObjectId())) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("parent.id", getObjectId()));
            ops.setOrderBy("name");
            this.subCertPager = new SubCertPager(getSubCertPagerSize(), this, ops);
        }
        return subCertPager;
    }

    public void setSubCertPager(SubCertPager subCertPager) {
        this.subCertPager = subCertPager;
        if (subCertPager != null){
            this.subCertPager.attach(this);
        }
    }
    
    private void initIdentityBeans() throws GeneralException
    {
        Meter.enterByName("initIdentityBeans");

        CertificationEntity certIdentity = getCurrentCertificationIdentity();
        if (null == certIdentity)
            return;

        this.businessRoles = new ArrayList<CertificationItemBean.BusinessRoleBean>();
        this.exceptions = new ArrayList<CertificationItemBean.ExceptionBean>();
        this.violations = new ArrayList<CertificationItemBean.ViolationBean>();
        this.profiles = new ArrayList<CertificationItemBean.ProfileBean>();
        this.businessRoleRelationshipBeans = new ArrayList<CertificationItemBean.GenericCertifiableBean>();
        this.businessRoleGrantBeans = new ArrayList<CertificationItemBean.GenericCertifiableBean>();

        Map<String,Application> appMap =
            new LoadingMap<String,Application>(new CertificationItemBean.ApplicationLoader());

        String lastApp = null;
        String lastInstance = null;
        String lastNativeId = null;
        CertificationItemBean.ExceptionBean lastExBean = null;


        for (CertificationItem item : certIdentity.getItems()) {

            switch (item.getType()) {
            case Bundle:

                // We're passing in null for the non-flattened entitlement
                // mappings.  This is now loaded with an AJAX call.
                this.businessRoles.add(new CertificationItemBean.BusinessRoleBean(this, item, certIdentity,
                                                                                  workItemId, null));
                break;

            case AccountGroupMembership: case Exception: case DataOwner: case Account:


                CertificationItemBean.ExceptionBean exBean =
                    new CertificationItemBean.ExceptionBean(this, item, certIdentity, workItemId,
                                                            appMap, lastNativeId, lastInstance, lastApp);
                this.exceptions.add(exBean);
                
                if (exBean.isFirstInGroup() && (null != lastExBean)) {
                    lastExBean.setLastInGroup(true);
                }

                lastApp = exBean.getApplication();
                lastInstance = exBean.getInstance();
                lastNativeId = exBean.getNativeIdentity();
                lastExBean = exBean;

                this.exceptionsHaveInstance |= (null != exBean.getInstance());

                break;

            case PolicyViolation:

                this.violations.add(new CertificationItemBean.ViolationBean(this, item, certIdentity, 
                                                                            workItemId));
                break;

            case BusinessRoleProfile:

                this.profiles.add(new CertificationItemBean.ProfileBean(this, item, certIdentity,
                                                                            workItemId));
                break;
            case BusinessRoleGrantedCapability:
            case BusinessRoleGrantedScope:

                this.businessRoleGrantBeans.add(new CertificationItemBean.GenericCertifiableBean(this, item, certIdentity,
                                                                                  workItemId));
                break;
            case BusinessRolePermit:
            case BusinessRoleRequirement:
            case BusinessRoleHierarchy:

                this.businessRoleRelationshipBeans.add(new CertificationItemBean.GenericCertifiableBean(this, item, certIdentity,
                                                                                  workItemId));
                break;

            default:
                throw new GeneralException("Unhandled certification type: " + item.getType());
            }
        }
        
        Meter.exitByName("initIdentityBeans");
    }

    /**
     * @return the CertificationIdentityDifferencesBean for the currently selected
     * identity, or null if there is not a selected user.
     */
    public CertificationIdentityDifferencesBean getDiffs() throws GeneralException {
        if (null == this.diffs) {
            CertificationEntity id = this.getCurrentCertificationIdentity();
            if (null != id) {
                IdentityDifference idDiff = id.getDifferences();
                if (idDiff != null) {
                    //we have full strings in the diff, so truncate the 
                    //long strings here to use in the bean
                    idDiff = idDiff.truncateStrings();
                }
                this.diffs = new CertificationIdentityDifferencesBean(idDiff);
            }
        }
        return this.diffs;
    }

    /**
     * NOTE: Not currently filling in expiration info on these beans since it is
     * not being used in the UI. The expiration beans are used because they
     * wrap the getShortName() method.
     * 
     * @return the sub-certifications directly under this certification.
     */
    public List<CertificationPercentBean.CertificationExpirationBean> getSubCertifications()
        throws GeneralException {

        this.subCertifications = new ArrayList<CertificationPercentBean.CertificationExpirationBean>();
        Certification cert = getObject();
        if ( cert != null ) {
            QueryOptions ops = getSubCertPager().getPagedQueryOptions();
            Iterator<Certification> certs = getContext().search(Certification.class, ops);
            if ( certs != null ) {
                while (certs.hasNext()) {
                    Certification subCert = certs.next();
                    this.subCertifications.add(new CertificationPercentBean.CertificationExpirationBean(subCert, null, getContext()));
                }
            }
        } 
        return this.subCertifications;
    }

    public boolean isParent() throws GeneralException {
        Certification cert = getObject();
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent", cert));
        int i = getContext().countObjects(Certification.class, ops);
        return (i>0) ? true : false;
    }
    
    /**
     * Toggle whether the postback is going to come from the detail view (for example -
     * not the grid).
     * 
     * NOTE: This can go away if the grid and detail views are split into two
     * separate pages.
     */
    private void toggleDetailViewPostback(boolean detailViewPostback)
        throws GeneralException {

        // Set this value in the component so that it gets rendered back out
        // correctly onto the page.
        final String DETAIL_VIEW_POSTBACK_COMPONENT_ID = "detailViewPostback";
        UIInput input =
            (UIInput) super.findComponentInRoot(DETAIL_VIEW_POSTBACK_COMPONENT_ID);
        if (null != input) {
            input.setValue(detailViewPostback);
        }

        // Set the value in the bean, too.
        this.detailViewPostback = detailViewPostback;
    }

    /**
     * Return whether the postback is going to come from the detail view (for example -
     * not the grid).
     * 
     * NOTE: This can go away if the grid and detail views are split into two
     * separate pages.
     */
    public boolean isDetailViewPostback() {
        return this.detailViewPostback;
    }
    
    /**
     * Set that this postback is coming from the detail view (for example - not the
     * grid).
     * 
     * NOTE: This can go away if  the grid and detail views are split into two
     * separate pages.
     */
    public void setDetailViewPostback(boolean detailViewPostback) {
        this.detailViewPostback = detailViewPostback;
    }
    
    /**
     * @return the custom1 field from the currently selected CertificationEntity,
     * or null if no entity is currently selected.
     */
    public String getEntityCustom1() throws GeneralException {
        if (null == this.entityCustom1) {
            CertificationEntity entity = this.getCurrentCertificationIdentity();
            if (null != entity) {
                this.entityCustom1 = entity.getCustom1();
            }
        }
        return this.entityCustom1;
    }

    /**
     * Set the custom1 field on the currently selected CertificationEntity.
     */
    public void setEntityCustom1(String value) {
        // This field can get nulled out when selecting an entity from the grid
        // view, so make sure that we only assimilate the value on a postback
        // from the detail view.  This check can go away if we split the grid
        // and detail views into two separate pages.
        if (this.detailViewPostback) {
            this.entityCustom1 = value;
        }
    }

    /**
     * @return the custom2 field from the currently selected CertificationEntity,
     * or null if no entity is currently selected.
     */
    public String getEntityCustom2() throws GeneralException {
        if (null == this.entityCustom2) {
            CertificationEntity entity = this.getCurrentCertificationIdentity();
            if (null != entity) {
                this.entityCustom2 = entity.getCustom2();
            }
        }
        return this.entityCustom2;
    }

    /**
     * Set the custom1 field on the currently selected CertificationEntity.
     */
    public void setEntityCustom2(String value) {
        // This field can get nulled out when selecting an entity from the grid
        // view, so make sure that we only assimilate the value on a postback
        // from the detail view.  This check can go away if we split the grid
        // and detail views into two separate pages.
        if (this.detailViewPostback) {
            this.entityCustom2 = value;
        }
    }

    /**
     * @return the customMap from the currently selected CertificationEntity,
     * or an empty map if no entity is currently selected.
     */
    public Map<String,Object> getEntityCustomMap() throws GeneralException {
        if (null == this.entityCustomMap) {
            CertificationEntity entity = this.getCurrentCertificationIdentity();
            if (null != entity) {
                this.entityCustomMap = entity.getCustomMap();
            }
        }

        // Return an empty map even if an entity is not selected so that fields
        // that reference map keys don't blow up.
        Map<String,Object> map =
            (null != this.entityCustomMap) ? this.entityCustomMap
                                           : new HashMap<String,Object>();

        // This values in this map can get nulled out when selecting an entity
        // from the grid view, so make sure that we only assimilate the value on
        // a postback from the detail view by using the ChangeIgnoringMap.  This
        // can go away if we split the grid and detail views into two separate
        // pages.
        return (this.detailViewPostback) ? map
                                         : new ChangeIgnoringMap<String,Object>(map);
    }

    public Map<String,String> getItemDecisions() {
        return this.itemDecisions;
    }

    public void setItemDecisions(Map<String,String> decisions) {
        this.itemDecisions = decisions;
    }
    
    public AccountGroupBean getAccountGroupBean() throws GeneralException {
        AccountGroupBean retval = null;
        CertificationEntity currentCert = getCurrentCertificationIdentity();
        if (isAccountGroupCertification() && currentCert != null) {
            ManagedAttribute group = currentCert.getAccountGroup(getContext());
            retval = new AccountGroupBean(group);
        } 
        return retval;
    }

    public RoleSnapshot getRoleSnapshot() throws GeneralException {
        if (isBusinessRoleCertification()){
            CertificationEntity currentCertEntity = getCurrentCertificationIdentity();
            if (currentCertEntity != null){
                RoleSnapshot snap = currentCertEntity.getRoleSnapshot();
                return snap;
            }
        }

        return null;
    }

    
    /** load a configured gridstate object based on what type of certification it is **/
    private GridState loadGridState() {
    	GridState gState = null;
    	String name = "";
    	IdentityService iSvc = new IdentityService(getContext());
    	
    	try {

	    	if(this.isBusinessRoleMembershipCertification() ) {
	    		if(!this.isListEntities()) {
	    			name = CertificationEntityListBean.BUSINESS_ROLE_MEMBERSHIP_GRID_STATE;
	    		} else {
	    			name = CertificationEntityListBean.IDENTITY_GRID_STATE;
	    		}
	    	} else if(this.isIdentityCertification()) {
	    		if(!this.isListEntities()) {
	    			name = CertificationItemsListBean.GRID_STATE;
	    		} else {
	    			name = CertificationEntityListBean.IDENTITY_GRID_STATE;
	    		}
	    	} else if(this.isAccountGroupCertification()) {
	    		name = CertificationEntityListBean.ACCOUNT_GROUP_GRID_STATE;
	    	} else if(this.isBusinessRoleCompositionCertification()) {
	    		name = CertificationEntityListBean.BUSINESS_ROLE_COMPOSITION_GRID_STATE;
	    	}
            else if(this.isDataOwnerCertification()) {
                name = CertificationEntityListBean.DATA_OWNER_GRID_STATE;
            }

            if (null != Util.getString(name)) {
                name = addContinuous(getObject(), name);
                gState = iSvc.getGridState(getLoggedInUser(), name);
            }
        } catch(GeneralException ge) {
    		log.info("GeneralException encountered while loading gridstates: "+ge.getMessage());
    	}
    	
    	if(gState==null) {
    		gState = new GridState();
    		gState.setName(name);
    	}
    	return gState;
    }
    
    /**
     * Add the continuous prefix to the given grid state key if the certification is a
     * continuous certification.
     */
    private String addContinuous(Certification cert, String gridStateKey) {
        
        if ((null != cert) && cert.isContinuous()) {
            gridStateKey = CONTINUOUS_GRID_STATE_PREFIX + gridStateKey;
        }
        return gridStateKey;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTION HANDLING
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The save button was clicked. Save any unsaved decisions on the
     * certification item beans.
     */
    public String save() throws GeneralException {
        
        try {
            saveChangedItems(null);
            saveCustomEntityFields();
            
            // Save it and store the errors.  Any really bad exception will be
            // thrown, otherwise the errors will be displayed on the next page.
            if (!saveAndRefreshCertification(getObject(), getContext(), this))
            	return null;
        }
        catch (GeneralException e) {
            // Go back to the same page if an error occurred.
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e);
            addMessage(msg, msg);
            return null;
        }

        // Save the history so we'll come back to this page.  Clear the current
        // identity if so we go back to the grid rather than the view identity
        // page.
        if (this.action != null && null == this.action.getCertificationItemId()) {
            resetCurrentIdentity();
            this.currentCertificationIdentityId = null;
        }

        // Not in the detail view anymore.
        toggleDetailViewPostback(false);
        
        NavigationHistory.getInstance().saveHistory(this);

        // Notify the listener that a save just occurred.
        notifyOfSave();

        return NavigationHistory.getInstance().back();
    }

    /**
     * Action method that will save decisions on items in the itemDecision map.
     */
    public String saveItems() throws GeneralException {

        try {
            saveChangedItems(null);
            saveAndRefreshCertification(getObject(), getContext(), this);
        }
        catch (GeneralException e) {
            // Go back to the same page if an error occurred.
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e);
            addMessage(msg, msg);
            return null;
        }
            
        return "";
    }
    
    /**
     * Save any changed items - except for the item with the given ID.
     * 
     * @param  itemToIgnore  The ID of the item to not save.
     */
    void saveChangedItems(String itemToIgnore) throws GeneralException {

        // This will be non-null if using the image radio buttons.
        if (null != this.itemDecisions) {
            for (Map.Entry<String,String> entry : this.itemDecisions.entrySet()) {
                String itemId = entry.getKey();

                // Only save if this is not the item to ignore.
                if (!itemId.equals(itemToIgnore)) {
                    String itemDecision = entry.getValue();
    
                    CertificationItem item =
                        getContext().getObjectById(CertificationItem.class, itemId);
                    if ( item != null ) {
                        CertificationItemBean itemBean =
                            new CertificationItemBean(this, item, item.getParent(), null);
                        if( Util.getString( itemDecision ) == null ) {
                            item.clearDecision(getContext(), getCurrentIdentity(), null );
                        } else {
                            itemBean.setStatus(CertificationAction.Status.valueOf(itemDecision));
                            itemBean.saveIfChanged(null);                            
                        }
                    }
                }
            }
        }

        saveChangedItems(this.getBusinessRoles(), itemToIgnore);
        saveChangedItems(this.getExceptions(), itemToIgnore);
        saveChangedItems(this.getViolations(), itemToIgnore);
        saveChangedItems(this.getProfiles(), itemToIgnore);
        saveChangedItems(this.businessRoleRelationshipBeans, itemToIgnore);
        saveChangedItems(this.businessRoleGrantBeans, itemToIgnore);
    }

    /**
     * Save any items that have been changed but not saved.
     * 
     * @param  items  The list of items to possibly save.
     */
    private void saveChangedItems(List<? extends CertificationItemBean> items,
                                  String itemToIgnore)
        throws GeneralException {
        
        if (null != items) {
            for (CertificationItemBean item : items) {
                if (((null == itemToIgnore) || !itemToIgnore.equals(item.getId()))) {
                    item.saveIfChanged(this.workItemId);
                }
            }
        }
    }

    /**
     * Save the custom fields on the CertificationEntity if they have been
     * modified.
     */
    public void saveCustomEntityFields() throws GeneralException {

        CertificationEntity entity = this.getCurrentCertificationIdentity();
        if (null != entity) {
            // Only save if the values have changed since this marks the entity
            // to be refreshed.
            if (!Util.nullSafeEq(entity.getCustom1(), this.entityCustom1, true)) {
                entity.setCustom1(this.entityCustom1);
            }
            if (!Util.nullSafeEq(entity.getCustom2(), this.entityCustom2, true)) {
                entity.setCustom2(this.entityCustom2);
            }
            if (!Util.nullSafeEq(entity.getCustomMap(), this.entityCustomMap, true)) {
                entity.setCustomMap(this.entityCustomMap);
            }
        }
    }
    
    /**
     * Action to cancel the selection and any pending changes on the currently
     * selected identity.
     */
    public String cancel() throws GeneralException
    {
        resetCurrentIdentity();

        // Not in the detail view anymore.
        toggleDetailViewPostback(false);

        this.currentCertificationIdentityId = null;
        return "";
    }

    /**
     * This changes the current view of the list to either display entities or
     * items.
     */
    public String toggleListView() throws GeneralException {
        
        this.gridState = null;
        return cancel();
    }

    /**
     * Refreshes the page. This is called after submitting an entitlement comment to refresh
     * the certification decision history list. 
     *
     * @throws GeneralException
     */
    public void refresh() throws GeneralException {

    }

    
    public List<CertificationEntity> getSortedIdentityList() throws GeneralException{
		List<CertificationEntity> entities = null;		

		/** If the entities are being viewed, get the entity list from the query options on 
		 * this list **/
		if(isListEntities()) {
			CertificationEntityListBean entityListBean = getCertIdentListBeanFromValueBinding();
			if(entityListBean!=null) {
			QueryOptions qo = entityListBean.getQueryOptions();			
			if(getGridState().getSortColumn()!=null) {
				String sortCol = entityListBean.getSortColumnMap().get(getGridState().getSortColumn());
				
				boolean ascending = getGridState().getSortOrder().equals("ASC");
				if(sortCol!=null) {
					qo.addOrdering(0, sortCol, ascending);
				}
			}
			entities = getContext().getObjects(CertificationEntity.class, qo);
			}
			/** If the items are being viewed, get the entity list from the items list query options on 
			 * this list **/
		} else {
			CertificationItemsListBean itemListBean = getCertItemsListBeanFromValueBinding();
			entities = new ArrayList<CertificationEntity>();
			if(itemListBean!=null) {
				QueryOptions qo = itemListBean.getQueryOptions();
				qo.setOrderings(new ArrayList<QueryOptions.Ordering>());
				qo.setDistinct(true);
				if(getGridState().getSortColumn()!=null) {
					String sortCol = itemListBean.getSortColumnMap().get(getGridState().getSortColumn());
					
					boolean ascending = getGridState().getSortOrder().equals("ASC");
					if(sortCol!=null) {	
						qo.getOrderings().clear();
						List<String> cols = Util.csvToList(sortCol);
						if (null != cols) {
							for (String col : cols) {
								qo.addOrdering(col, ascending);
							}
						}
					}
				}
				boolean containsOrdering = false;
				if(qo.getOrderings()!=null) {
					for(Ordering ordering : qo.getOrderings()) {
						if(ordering.getColumn().equals("parent.identity"))
							containsOrdering = true;
					}
				}
				if(!containsOrdering)
					qo.addOrdering(qo.getOrderings().size(), "parent.identity", true);
				List<String> props = Arrays.asList(new String[] {"parent"});
				Iterator<Object[]> rows = getContext().search(CertificationItem.class, qo, props);
				while(rows.hasNext()) {
					CertificationEntity entity = (CertificationEntity)rows.next()[0];
					if(!entities.contains(entity))
						entities.add(entities.size(), entity);
				}				
			}
		}
		return entities;
	}

    /**
     * Action to select the identity being edited.
     */
    public String selectIdentity() throws GeneralException {

        selectChosenEntity(false);

        return "";
    }

    /**
     * 
     * @param forceReset
     * initIdentityBeans() will load the exception beans
     * in the apply_request_values phase of the lifecycle
     * Loaded values do not need to be reset if
     * selecting the identity for the first time.
     * So the parameter which will be set to false is known when 
     * called from selectIdentity() action method. It will be set to 
     * true otherwise
     */
    private void selectChosenEntity(boolean forceReset) throws GeneralException {
        
        if (null != getCurrentCertificationIdentityId()) {

            // Calculate the identity paging info and store it in the session.
            calculateIdentityPagingInfo();

            // We're now in the detail view, so posting back will come from
            // the detail view.
            toggleDetailViewPostback(true);

            if (forceReset) {
                resetCurrentIdentity();
            }
        }
    }

    /**
     * The identity paging information should already be calculated when an attempt
     * is made to retrieve it because the actions to view the detail view set this up.
     * However, in some cases (for example - when JSF validation fails) these will not be
     * set. If they have not been calculated yet, do it now. See bug #4967.
     */
    private void calculateIdentityPagingInfoIfNeeded() throws GeneralException {
        if ((null == getSessionScope().get(ENTITY_PAGING_INFO_CALCULATED)) &&
            (null != getCurrentCertificationIdentityId())) {
            log.debug("Identity paging info should already be calculated but wasn't - forcing calculation.");
            calculateIdentityPagingInfo();
        }
    }
    
    /**
     * Calculate the identity paging information (current index, total entities,
     * previous/next ids) and store them in the session.
     */
    private void calculateIdentityPagingInfo() throws GeneralException {
        
        if (null != getCurrentCertificationIdentityId()) {
            // Need to allow for a user to choose "previous" and "next" certs from the list
            // Stick them in the session.
            List<CertificationEntity> certs = null;
            String prevId = null;
            String nextId = null;
            int idx = 0;

            // Only need the paging info if we're not in a delegation.
            if (null == this.workItemId) {
                certs = getSortedIdentityList();
            }

            if(certs!=null) {
                for(int i=0; i<certs.size(); i++) {
                    if(certs.get(i).getId().equals(getCurrentCertificationIdentityId())) {
                        idx = i+1;

                        if(i>0)
                            prevId = certs.get(i-1).getId();                            

                        if(i<certs.size()-1)
                            nextId = certs.get(i+1).getId();

                        break;
                    }
                }
            }

            setCurrentCertificationIdentityIndex(idx);
            setTotalCertificationIdentities((null != certs) ? certs.size() : 0);
            setNextCertificationIdentityId(nextId);
            setPrevCertificationIdentityId(prevId);

            // We've now calculated this stuff, so remember that we have.
            getSessionScope().put(ENTITY_PAGING_INFO_CALCULATED, true);
        }
    }
    
    public String selectFirstIdentityImpliedFilterAlreadySet() throws GeneralException
    {
    	CertificationEntityListBean certIdentListBean = getCertIdentListBeanFromValueBinding();
    	if (null != certIdentListBean) {
    		// Need to allow for a user to choose "previous" and "next" certs from the list
    		// Stick them in the session.
    		List<CertificationEntity> certs = certIdentListBean.getObjects();

            String prevId = null;
            String nextId = null;
            String currentId = null;
            int idx = 0;

            if(certs != null && certs.size() > 0) {
            	int i = 0;
            	currentId = certs.get(i).getId();
            	idx = i+1;

            	if(i>0) {
            		prevId = certs.get(i-1).getId();
            	}

            	if(i<certs.size()-1) {
            		nextId = certs.get(i+1).getId();
            	}
            }
            setCurrentCertificationIdentityIndex(idx);
            setTotalCertificationIdentities((null != certs) ? certs.size() : 0);
			setNextCertificationIdentityId(nextId);
			setPrevCertificationIdentityId(prevId);
			setCurrentCertificationIdentityId(currentId);	

            // We've now calculated this stuff, so remember that we have.
            getSessionScope().put(ENTITY_PAGING_INFO_CALCULATED, true);

            // We're now in the detail view, so posting back will come from
            // the detail view.
            toggleDetailViewPostback(true);
    	}

    	resetCurrentIdentity();
        return "";
    }
    

    
    /**
     * This controls the switch from the list to detailed view according to user preferences.
     * To do the switch, it Checks and sets up the detailed view with implied filter and automatically
     * selects the first identity as per the implied filter.  
     * Continues retaining the Implied filter unless filter is explicitly
     * changed. Explicit changes could happen.
     *
     * @ignore
     * jsl - these generate warnings because the classes are not
     * included in the public javadocs
     * 
     * @see AbstractCertificationContentsListBean#search()
     * @see AbstractCertificationContentsListBean#reset()
     * @see CertificationEntityListBean#initializeImpliedFilterForDetailedView()
     * @see CertificationItemsListBean#initializeImpliedFilterForDetailedView()
     * @see #selectFirstIdentityImpliedFilterAlreadySet()
     */
    public boolean getCheckDisplayDetailedViewFilterSet() throws GeneralException
    {
    	boolean pref = false;    	
    	if (!isPrefDetailedViewFilterSetChecked())
    	{
    	    Boolean bval = getDetailedViewFilterSetPref();  
    	    if (bval != null)
    	        pref = bval;
    	    else if (definition != null) {
    	        pref = definition.isCertificationDetailedViewFilterSet(getContext());
    	    }
    		saveInSessionCheckedPrefDetailedView(CHECKED_ALREADY);	 
    	}    	
    	
    	boolean detailedViewSetUp = isDetailedViewFilterSetInSession();    	
		boolean needToSetDetailedView = !detailedViewSetUp && pref;
		if(needToSetDetailedView)
		{
			setupImpliedFilterForDetailedView();
			selectFirstIdentityImpliedFilterAlreadySet();
			saveInSessionStateDetailedViewFilterSet(IMPLIED_FILTER_STATE);
		}
    	
    	boolean impliedState = isImpliedStateDetailedViewFilterSetInSession();
    	if(impliedState)
    	{
    		//once in the implied filter state, continue setting the implied filter for detailed view
    		//unless filter is explicitly changed by the user in the grid list view 
    		setupImpliedFilterForDetailedView();
    	}
    	
    	checkDisplayDetailedViewFilterSet = needToSetDetailedView || impliedState;
    	return checkDisplayDetailedViewFilterSet;
    }


    public void setCheckDisplayDetailedViewFilterSet(boolean checkDisplayDetailedViewFilterSet) throws GeneralException
    {
    	this.checkDisplayDetailedViewFilterSet = checkDisplayDetailedViewFilterSet;
    	
    }
	

	private void setupImpliedFilterForDetailedView()
			throws GeneralException {
		CertificationEntityListBean certIdentListBean = getCertIdentListBeanFromValueBinding();
		CertificationItemsListBean certItemsListBean = getCertItemsListBeanFromValueBinding();
		
		if (null != certIdentListBean) {
			certIdentListBean.initializeImpliedFilterForDetailedView();
		}

		if (null != certItemsListBean) {
			certItemsListBean.initializeImpliedFilterForDetailedView();
		}
	}

	

	private boolean isDetailedViewFilterSetInSession() {
		return (getSessionScope().get(DETAILED_VIEW_FILTER_SET) != null);
	}
	
	public boolean isImpliedStateDetailedViewFilterSetInSession() {
		boolean impliedFilterStateSet = false;
		if(isDetailedViewFilterSetInSession())
		{
			String state = getStateDetailedViewFilterSet();
			if (state.equals(IMPLIED_FILTER_STATE))
			{
				impliedFilterStateSet = true;
			}
		}
		return impliedFilterStateSet;
	}
	
	public void saveInSessionStateDetailedViewFilterSet(String state) {
		getSessionScope().put(DETAILED_VIEW_FILTER_SET, state);
	}
	
	public String getStateDetailedViewFilterSet() {
		String state = (String)getSessionScope().get(DETAILED_VIEW_FILTER_SET);
		return state;
	}
	
	
    

	static void resetCheckDisplayDetailedFilterSet(FacesContext context) {
		context.getExternalContext().getSessionMap().remove(CHECK_PRF + DETAILED_VIEW_FILTER_SET);
		context.getExternalContext().getSessionMap().remove(DETAILED_VIEW_FILTER_SET);
	}
	
	private CertificationEntityListBean getCertIdentListBeanFromValueBinding() {
		String valueBindingString = "#{certificationEntityList}";
		try{
		    Certification.Type certType = getObject() != null ? getObject().getType() : null;
		    if (Certification.Type.BusinessRoleComposition.equals(certType)){
		        valueBindingString = "#{certificationBusinessRoleEntityList}";
		    }                
		}catch(GeneralException e){
		    throw new RuntimeException(e);
		}
       ValueBinding vb =
		    getFacesContext().getApplication().createValueBinding(valueBindingString);
		CertificationEntityListBean certIdentListBean =
		    (CertificationEntityListBean) vb.getValue(getFacesContext());
		return certIdentListBean;
	}
	
	private CertificationItemsListBean getCertItemsListBeanFromValueBinding() {
		String valueBindingString = "#{certificationIdentityItemsList}";
		ValueBinding vb =
			getFacesContext().getApplication().createValueBinding(valueBindingString);
		CertificationItemsListBean certItemsListBean =
			(CertificationItemsListBean) vb.getValue(getFacesContext());
		return certItemsListBean;
	}

    private void notifyOfSave() {
        if (null != this.listener)
            this.listener.certificationSaved();

        // Null this out so that we calculate it again.  This can change based
        // on the decisions on the items.
        this.showRemedationCompletion = null;
    }

    /**
     * Save the given certification and refresh it with the Certificationer,
     * storing any errors encountered in the given bean.
     * 
     * @param  cert  The Certification to save and refresh.
     * @param  ctx   The SailPointContext to use.
     * @param  bean  The JSF bean into which errors will be saved.
     * 
     * @return True if there were no errors, false otherwise.
     */
    public static boolean saveAndRefreshCertification(Certification cert,
                                                      SailPointContext ctx,
                                                      BaseBean bean)
        throws GeneralException {

        Certification certification = cert;
        ctx.saveObject(certification);

        Certificationer certificationer = new Certificationer(ctx);
        List<Message> errors = certificationer.refresh(certification);
        return !saveErrors(errors, bean);
    }

    /**
     * Save the errors in the given bean and return true if there were any
     * errors in the given list, false otherwise.
     * 
     * @param  errors  The possibly-null list of errors to save in the bean.
     * 
     * @return True if there were any errors in the given list, false otherwise.
     */
    static boolean saveErrors(List<Message> errors, BaseBean bean) {

        if ((null != errors) && !errors.isEmpty())
        {
            for (Message s : errors) {
                bean.addMessageToSession(s, null);
            }
            return true;
        }
        return false;
    }

    /**
     * Action to sign off on the given certification.
     */
    public String sign() throws GeneralException, ExpiredPasswordException {
        log.debug("Sign");

        try {
            // Sign off on the certification. If there were any problems,
            // send them back to the UI.
            Certificationer certificationer = new Certificationer(getContext());
            List<Message> errors;
            try {
                errors = certificationer.sign(this.getObject(),
                        retrieveCurrentUserLoggedIn(), true, getNativeAuthId(), getSignaturePass(), getLocale());
            } catch (ObjectAlreadyLockedException e) {
                // Swallow this, just use the error messages
                errors = certificationer.getErrors();
            }
    
            // If username was passed in, store it for future use.
            String sai = getSignatureAuthId();
            String oai = getOriginalAuthId();
            if(sai != null && !sai.equals("") && (oai == null || oai.equals(""))) {
                getSessionScope().put(LoginBean.ORIGINAL_AUTH_ID, sai);
            }
            
            // Go back to the same page if there were errors.
            if (saveErrors(errors, this)) {
                return null;
            }
        }
        finally {
            setSignaturePass(null); // Don't store the password!
            setSignatureAuthId(null);
        }

        String returnStr = NavigationHistory.getInstance().back();
        if(returnStr==null)
            return "signSuccess";
        else
            return returnStr;
    }

    /**
     * Action to rescind the given certification back into its parent.
     */
    public String rescindChildCertification() throws GeneralException {
        
        Certificationer certificationer = new Certificationer(getContext());
        certificationer.rescindChildCertification(this.getObject());
        return "rescindChildCertificationSuccess";
    }
    
    /**
     * An action listener method that saves the page in the navigation history.
     *
     * @param  evt  The JSF action event.
     */
    public void saveNavigationHistoryActionListener(ActionEvent evt) {
        NavigationHistory.getInstance().saveHistory(this);
    }

    /**
     * Action to go back to the previous page in the navigation history.
     */
    public String back() throws GeneralException
    {
        return NavigationHistory.getInstance().back();
    }

    /**
     * Action to view a work item from within a certification - this saves state
     * in the navigation history.
     */
    public String viewWorkItem() throws GeneralException {
        NavigationHistory.getInstance().saveHistory(this);
        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
        return navigationUtil.navigate(this.selectedWorkItemId, true /* check archive */, super.getSessionScope());
    }

    private void resetCurrentIdentity()
    {
        resetItems();
        this.action = null;
        this.entityCustom1 = null;
        this.entityCustom2 = null;
        this.entityCustomMap = null;
        this.currentCertificationEntity = null;
        this.diffs = null;
        this.identityDTO = null;
        //reset these for scores
        this.identitySnapshot = null;
        this.scorecardBean = null;
    }

    private void resetSubCerts() {
        this.subCertifications = null;
    } 

    private void resetItems() {

        this.businessRoles = null;
        this.exceptions = null;
        this.violations = null;
        this.profiles = null;
        this.businessRoleRelationshipBeans = null;
        this.businessRoleGrantBeans = null;
        this.exceptionsHaveInstance = false;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods - allows a certification to participate in
    // navigation history.
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Certification";
    }

    public String getNavigationString() {
        CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(this.getObjectId());
        return certPrefBean.getDefaultView();
    }

    public Object calculatePageState() {
        Object[] state = new Object[16];
        state[0] = this.getObjectId();
        state[1] = this.getCurrentCertificationIdentityId();
        try {
            state[2] = this.getNextCertificationIdentityId();
            state[3] = this.getPrevCertificationIdentityId();
            state[4] = this.getCurrentCertificationIdentityIndex();
            state[5] = this.isListEntities();
            state[15] = this.getTotalCertificationIdentities();
        }
        catch (GeneralException e) {
            // Default to null if this throws - no big deal.
        }

        state[11] = this.getGridState();
        state[12] = this.getSubCertPager().saveState();
        state[13] = this.detailViewPostback;
        
        //NOTE: state[15] in use 
        
        return state;
    }

    public void restorePageState(Object state) {
        if (null == getObjectId()) {
            Object[] myState = (Object[]) state;
            setObjectId((String) myState[0]);
            setCurrentCertificationIdentityId((String) myState[1]);
            setNextCertificationIdentityId((String) myState[2]);
            setPrevCertificationIdentityId((String) myState[3]);
            setCurrentCertificationIdentityIndex((Integer) myState[4]);
            setTotalCertificationIdentities((Integer) myState[15]);
            setListEntities((Boolean) myState[5]);
            setGridState((GridState) myState[11]);
            getSubCertPager().restoreState(myState[12]);
            setDetailViewPostback((Boolean) myState[13]);
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Tracking Certification UI preferences per current user logged in
    // These methods track and control display as per Identity preferences
    // the display to the user.
    //
    ////////////////////////////////////////////////////////////////////////////
    //Methods for Certification Summary Last State Minimized
    /**
     * Summary now starts with expanded and in subsequent views shows as per whatever
     * the user did last with it as it persists that state.
     * However, always when Sign off button appears this is expanded.
     * @return String controls the way summary display looks
     *          for parameter passing to the template
     * @throws GeneralException
     */
    public String getDisplaySummaryStyle() throws GeneralException {
    	String defaultDisplaySummaryStyle ="";
        boolean signOffExpand = (isReadyForSignOff() && isEditable()) || isSignedOff();
        //when in signoff mode it does not need to retrieve what the user did last
        //also, when already signed off it does not need to retrieve what the user did last
        if (!signOffExpand)
        {
        	boolean currentUserLastStateMinimized = retrieveDisplaySummaryLastState();
        	if (currentUserLastStateMinimized)
        	{
        		defaultDisplaySummaryStyle = "display:none";
        	}
        }        
        return defaultDisplaySummaryStyle;
    }

    public boolean isAllowProvisioningRequirements() throws GeneralException{
        return getObject().isAllowProvisioningRequirements();
    }                               

    public boolean isRequireApprovalComments()  throws GeneralException{
        return getObject().isRequireApprovalComments();
    }

    public boolean isDisplayEntitlementDescription() throws GeneralException {
        if (displayEntitlementDescription == null) {
            Identity user = retrieveCurrentUserLoggedIn();
            Object pref = user.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC);
            if (null != pref) {
                this.displayEntitlementDescription = Util.otob(pref);
            }
            else if (getObject().getDisplayEntitlementDescription() != null) {
                displayEntitlementDescription = getObject().getDisplayEntitlementDescription();
            }
            else {
                displayEntitlementDescription = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
            }
        }
        return displayEntitlementDescription;
    }
    
    /**
     * Retrieves the display certificate summary last state in terms of expanded and
     * collapsed. This is saved in the User Preference and as per the requirements
     * currently is valid of multiple certificates for the current user
     * @throws GeneralException
     */
    public boolean retrieveDisplaySummaryLastState() throws GeneralException {
    	return retrieveCurrentUserPreference(UIPreferences.PRF_CERTIFICATION_SUMMARY_DISPLAY_MINIMIZED, false);
    }
    
    /**
     * Saves the display summary last state in terms of expanded or collapsed.
     * @throws GeneralException
     */
    public void saveDisplaySummaryLastState() throws GeneralException {
    	updateCurrentUserPreference(trackSummaryLastStateMinimized, UIPreferences.PRF_CERTIFICATION_SUMMARY_DISPLAY_MINIMIZED);
    }
    
    /**
     * @return boolean true if the current user last summary state is minimized
     */
	public boolean isTrackSummaryLastStateMinimized() {
		return this.trackSummaryLastStateMinimized;
	}
	
	/**
     * @param trackCertificationSummaryLastStateMinimized true if the current user last summary state is minimized
     */
	public void setTrackSummaryLastStateMinimized(boolean trackCertificationSummaryLastStateMinimized) {
		this.trackSummaryLastStateMinimized = trackCertificationSummaryLastStateMinimized;
	}   
	
    //Methods for Display Start Up Help Certification Grid View
	/**
     * Gets the Display Start Up Help Certification Grid View
     * @throws GeneralException
     */
    public boolean getDisplayStartUpHelpCertificationGridView() throws GeneralException {
    	return retrieveCurrentUserPreference(UIPreferences.StartUpHelp.CertGridView.getKey(), true);
    }
    
    /**
     * Sets the Display Start Up Help Certification Grid View
     * @throws GeneralException
     */
    public void setDisplayStartUpHelpCertificationGridView(boolean displayHelp) {
        this.displayStartupHelp = displayHelp;
    }
    
    /**
     * Updates state for Display Start Up Help Certification Grid View 
     * to be displayed or not. Returns JSON String
     * @throws GeneralException
     */
    public String updateDisplayStartUpHelpCertificationGridView(ActionEvent e) throws GeneralException {
        return updateDisplayStartUpHelp(UIPreferences.StartUpHelp.CertGridView);
    }

    /**
     * Updates state for the given help to be displayed or not. Returns JSON
     * string.
     */
    private String updateDisplayStartUpHelp(UIPreferences.StartUpHelp help)
        throws GeneralException {
        updateCurrentUserPreference(this.displayStartupHelp, help.getKey());
        return JsonHelper.success();
    }

    //Methods for Display Start Up Help Certification Entity View
    
    /**
     * Gets the Display Start Up Help Certification Entity View
     */
    public boolean getDisplayStartUpHelpCertificationEntityView() throws GeneralException {
        return retrieveCurrentUserPreference(UIPreferences.StartUpHelp.CertEntityView.getKey(), true);
    }

    /**
     * Sets the Display Start Up Help Certification Entity View
     * @throws GeneralException
     */
    public void setDisplayStartUpHelpCertificationEntityView(boolean displayHelp) {
        this.displayStartupHelp = displayHelp;
    }
    
    /**
     * Updates state for Display Start Up Help Certification Entity View
     * to be displayed or not. Returns JSON String
     */
    public String updateDisplayStartUpHelpCertificationEntityView(ActionEvent e) throws GeneralException {
        return updateDisplayStartUpHelp(UIPreferences.StartUpHelp.CertEntityView);
    }

	//Methods for Detailed View Filter Set preference
    
	/**
     * @return boolean true if detailed view filter set preference is true.
     *         Default is from the System config
     */
	public Boolean getDetailedViewFilterSetPref() throws GeneralException {
	    
        Identity currentUser = retrieveCurrentUserLoggedIn();
        Boolean bval = null;
        if(currentUser != null)
        {
            Object val = currentUser.getUIPreference(UIPreferences.PRF_CERTIFICATION_DETAILED_VIEW_FILTER_SET);
            if (val != null) {
                bval = Util.otob(val);
            }
        }    
        return bval;
    }
	
    public void saveInSessionCheckedPrefDetailedView(String checked) {
		getSessionScope().put(CHECK_PRF + DETAILED_VIEW_FILTER_SET, checked);
	}
	
	public boolean isPrefDetailedViewFilterSetChecked() {
		String state = (String)getSessionScope().get(CHECK_PRF + DETAILED_VIEW_FILTER_SET);
		if (state != null && state.equalsIgnoreCase(CHECKED_ALREADY))
		{
			return true;
		}
		return false;
	}
	
	
	/**
     * Updates the Current User Preferences for the certification ui as per the key
     * @param state boolean value to store in preference map
     * @param key user preference certification key
     * @throws GeneralException
     */
    private void updateCurrentUserPreference(boolean state, String key) throws GeneralException {
    	Identity currentUser = retrieveCurrentUserLoggedIn();
    	Boolean stateObject = new Boolean(state);
    	if (currentUser != null) {
    		currentUser.setUIPreference(key, stateObject.toString());
    		log.debug("Saving content list for user: [" + currentUser.getName()
    				+ "] and lastState being saved is "+ stateObject.toString());
    		try {
    			getContext().saveObject(currentUser);
    			getContext().commitTransaction();
    		} catch (GeneralException ge) {
    			log.info("Unable to save user due to exception: " + ge.getMessage());
    			getContext().rollbackTransaction();
    		}
    	}
    }
    
    
    /**
     * Retrieves current user preference for the certification ui as per the key
     * @param key user preference certification key
     * @param defaultResult if the key does not exist this is used
     * @return boolean state result
     * @throws GeneralException
     */
    public boolean retrieveCurrentUserPreference(String key, boolean defaultResult) throws GeneralException {
    	boolean evaluateResult = defaultResult;
    	Identity currentUser = retrieveCurrentUserLoggedIn();
    	Object lastStateObject = null;
    	if(currentUser!= null)
    	{
    		lastStateObject = currentUser.getUIPreference(key);
    		if (lastStateObject != null)
    		{
    			evaluateResult = Util.otob(lastStateObject);
    		}
    	}    
    	return evaluateResult;
    }

    /**
     * Retrieves current user logged in.
     * This when called each time it creates a database call, but since
     * multiple preferences might be saved/changed always get the most current 
     * identity current user instance
     * @return Identity current user with the latest preferences
     * @throws GeneralException
     */
	private Identity retrieveCurrentUserLoggedIn() throws GeneralException {
        Identity loggedInUser = super.getLoggedInUser();
		return loggedInUser;
	}

}
