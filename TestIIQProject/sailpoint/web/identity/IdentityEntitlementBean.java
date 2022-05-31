/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.IdentityService;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.service.LinkService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;

/**
 *
 * The bean that is called when an row is clicked on in the
 * entitlements grid(s). The expand sends over the "id", which
 * is assimilated by the base bean and the initialize method.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 */
public class IdentityEntitlementBean 
       extends BaseObjectBean<IdentityEntitlement> {
    
    ////////////////////////////////////////////////////////////////////////////
    // 
    // Fields
    // 
    ////////////////////////////////////////////////////////////////////////////
    
    private static final Log log = LogFactory.getLog(IdentityEntitlementBean.class);

    /**
     * Entitlement's hibernate ID. 
     */
    String _entitlementId;

    /**
     * Start date (sunrise) of an assignment, currently only applies to roles.
     */
    Date startDate;
    
    /**
     * End date (sunset) of an assignment, currently only applies to roles.
     */
    Date endDate;

    /**
     * Name of the entitlement, this is the attribute name or the role attribute.
     * (assigned/detectedRoles)
     */
    String name;
    
    /**
     * Raw value of the entitlement, this is typically a dn type string.
     */
    String value;
    
    /**
     * Display value from the MA.
     */
    String displayValue;
    
    /**
     * Application name, will be null for roles.
     */
    String appName;
    
    /**
     * Application Id, will be null for roles.
     */
    String appId;

    /**
     * Link's instance and native identity, where the entitlement is assigned
     */
    String nativeIdentity;
    String instance;
    
    /**
     * The display name of the accountId.
     */
    String accountName;

    /**
     * Entitlement OR Permission. ( does group matter here too? )
     */
    ManagedAttribute.Type type;    
    
    /**
     * Localized version of the managed attribute type.
     */
    String localizedType;

    /**
     * Localized source string, where the entitlement
     * originated.
     */    
    String source;
    
    /**
     * Directly assigned entitlments will have this flag set to true.
     * This applies only to entitlements. 
     */
    boolean assigned;
    
    /**
     * The name of the identity that assiged the entitlement or role?
     */
    String assigner;

    /**
     * List of assignable role names that could grant an entitlement.
     * Property is displayed as csv in the ui popup.  
     */
    List<String> sourceAssignableRoleNames;
    
    /**
     * List of detectable role names that could have granted an entitlement.
     * 
     */
    List<String> sourceDetectableRoleNames;

    /**
     * Not shown directly in the ui, but a the "existsOnLink"
     * property is derived from the value.
     */
    IdentityEntitlement.AggregationState aggState;
    
    /**
     * Valid if the entitlement is a Permission type and has an annotiation  
     */
    String annotation;
    
    /**
     * Valid for IT (ahem.. detectable ) roles only, and will be set to
     * true IF the role is allowed by one of the assigned roles 
     * permits or required list.  
     */
    boolean allowed;
    
    /**
     * Valud for entitlements only.  Flag will be set to true
     * if one of user's current detectable roles grants this
     * entitlement. 
     */
    boolean grantedByRole;    
    
    //
    // Certification Information
    //
    
    /**
     * DTO Bean for summarizing the interesting bits
     * from the certification items that may be associated
     * with the entitlement.
     */
    CertSummaryBean lastCertBean;
    CertSummaryBean pendingCertBean; 

    //
    // Identity Request Information 
    //

    /**
     * DTO Bean for summarizing the interesting bits
     * from the IdentityRequestItem(s) that
     * may be associated with the entitlement.
     */
    RequestSummaryBean lastRequestBean;    
    RequestSummaryBean pendingRequestBean;
            
    ////////////////////////////////////////////////////////////////////////////
    // 
    // Constructor
    // 
    ////////////////////////////////////////////////////////////////////////////

    public IdentityEntitlementBean() throws GeneralException {
        super();
        setScope(IdentityEntitlement.class);
        init();
    }
    
    /**
     * Initialize fields from hibernate object, localize
     * and transform where applicable.
     * 
     * @throws GeneralException
     */
    private void init() throws GeneralException {
        IdentityEntitlement _entitlement = getObject();
        if (_entitlement == null  ) {
            // hey what happened?
            log.warn("Entitlement was null when attempting to expand details..");
            return;
        }        
        checkAuthorization();
        
        _entitlementId = _entitlement.getId();        
        name = _entitlement.getName();
        endDate = _entitlement.getEndDate();
        startDate = _entitlement.getStartDate();
        annotation = _entitlement.getAnnotation();
        
        type = _entitlement.getType();
        if ( type != null ) {
            localizedType = type.getLocalizedMessage(getLocale(), getUserTimeZone());
            if ( localizedType == null )
                localizedType = type.toString();
        }
        
        nativeIdentity = _entitlement.getNativeIdentity();
        accountName = _entitlement.getDisplayName();
        instance = _entitlement.getInstance();
        
        Application app = _entitlement.getApplication();
        if ( app != null ) {
            appName = app.getName();
            appId = app.getId();          
        }        

        if (null == accountName && null != nativeIdentity && null != appName) {
            //convert nativeIdentity to account display name
            LinkService linksService = new LinkService(getContext());
            accountName = linksService.getAccountDisplayName(
                    _entitlement.getIdentity(), appName, instance, nativeIdentity);
        }

        source = _entitlement.getSource();        
        // try and resolve the source to a message catalog entry
        if ( source != null ) {
            Source src = Source.valueOf(source);
            if ( src != null ) {
                String localizedSource = null;
                try {
                    localizedSource = src.getLocalizedMessage(getLocale(), getUserTimeZone());
                } catch (Exception e) {
                    // swallow to support unregistered sources
                    if ( log.isDebugEnabled() )
                        log.debug(e);
                } 
                if ( localizedSource != null ) 
                    source = localizedSource;
            }
        }
        
        assigned = _entitlement.isAssigned();
        aggState = _entitlement.getAggregationState();
        assigner = _entitlement.getAssigner();
        allowed = _entitlement.isAllowed();
        
        grantedByRole = _entitlement.isGrantedByRole();
        sourceDetectableRoleNames = Util.csvToList(_entitlement.getSourceDetectedRoles());
        sourceAssignableRoleNames = Util.csvToList(_entitlement.getSourceAssignableRoles());
        
        Object o = _entitlement.getValue();
        value = ( o != null ) ? o.toString() : "";    
        if ( value != null && appId != null ) {
            displayValue = Explanator.getDisplayValue(appId, name, value);
        }  
        if ( displayValue == null ) 
            displayValue = value;
        
        CertificationItem item = _entitlement.getCertificationItem();
        if ( item != null ) {
            lastCertBean = new CertSummaryBean(item);            
        }
        
        CertificationItem pitem = _entitlement.getPendingCertificationItem();
        if ( pitem != null ) {
            pendingCertBean = new CertSummaryBean(pitem);
        }        
        
        IdentityRequestItem ritem = _entitlement.getRequestItem();
        if ( ritem != null ) {
            lastRequestBean = new RequestSummaryBean(ritem);                        
        }
        IdentityRequestItem pritem = _entitlement.getPendingRequestItem();
        if ( pritem != null ) {
            pendingRequestBean = new RequestSummaryBean(pritem);
        }
    }
       
    public String getAssigner() {
        return assigner;
    }
    
    public String getAssignerDisplayName() {
       try {
        if(this.assigner != null) {
            Identity i = getContext().getObjectByName(Identity.class, assigner);
            if(i!=null)
                return i.getDisplayableName();
        }
       } catch (GeneralException ge) {
           log.warn("Exception getting Assigner DisplayName", ge);
       }
       return assigner;
    }

    public void setAssigner(String assigner) {
        this.assigner = assigner;
    }

    public ManagedAttribute.Type getType() {
        return type;
    }

    public void setType(ManagedAttribute.Type type) {
        this.type = type;
    }
    
    public String getLocalizedType() {
        return localizedType;
    }

    public void setLocalizedType(String localizedType) {
        this.localizedType = localizedType;
    }
   
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getEntitlementId() throws GeneralException {
        return _entitlementId;
    }

    public void setEntitlementId(String entitlementId) {
        this._entitlementId = entitlementId;
    }
    
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }
    
    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
    
    public boolean isDetectedRole() {
        return ( Util.nullSafeCompareTo(name, ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) == 0 ) ? true : false;
    }
    
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) throws GeneralException {
        this.nativeIdentity = nativeIdentity;
    }
        
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }

    public List<String> getSourceRoleNames() {
        return sourceAssignableRoleNames;
    }
    
    public void setSourceRolesNames(List<String> sourceRoleNames) {
        this.sourceAssignableRoleNames = sourceRoleNames;
    }    
    
    public String getSourceAssignableRolesString() { 
        return Util.listToCsv(sourceAssignableRoleNames);
    }
    
    public List<String> getSourceDetectableRoleNames() {
        return sourceDetectableRoleNames;
    }

    public void setSourceDetectableRoleNames(List<String> sourceDetectableRoleNames) {
        this.sourceDetectableRoleNames = sourceDetectableRoleNames;
    }
    
    public String getSourceDetectableRolesString() {
        return Util.listToCsv(sourceDetectableRoleNames);
    }
    
    public AggregationState getAggregationState() {
        return this.aggState;
    }
    
    public boolean isFoundOnAccount() {
        return Util.nullSafeEq(getAggregationState(), AggregationState.Connected );
    }
    
    public void setAggregationState(AggregationState state) {
        this.aggState = state;
    }
    
    public String getAggregationStateString() {
        return ( aggState != null ) ? aggState.toString() : null;
    }
    
    public boolean isIiqEntitlement() {
        if ( appName == null ) 
            return true;
        return false;
    }
    
    public boolean isGrantedByRole() {
        return grantedByRole;
    }

    public void setGrantedByRole(boolean grantedByRole) {
        this.grantedByRole = grantedByRole;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Authorization
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Make sure the user is allowed to view this entitlement using the
     * Identity stored on the identity.
     * 
     * Took most of this from the IdentityDTO since the access restrictions
     * are simmular. 
     * 
     */
    private boolean isAuthorized() throws GeneralException {

        IdentityEntitlement ie = getObject();
        
        if ( ie == null ) {
           return true ;
        }
        
        Identity id = ie.getIdentity();
        if ( id == null)
            return true;

        
        if (getLoggedInUser().equals(id.getManager()))
            return true;

        String authorizedId = (String) getSessionScope().get(IdentityDTO.VIEWABLE_IDENTITY);
        if (getObject().getId().equals(authorizedId)) {
            return true;
        } else            
            return this.checkAuthorization(id);
        
    }
    
    private void checkAuthorization() throws GeneralException {

        if (!isAuthorized()){
            clearHttpSession();
            throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
        }
    }    

    ///////////////////////////////////////////////////////////////////////////
    //
    // Request properties
    //
    ///////////////////////////////////////////////////////////////////////////
    
    public RequestSummaryBean getLastRequestBean() {
        return lastRequestBean;
    }

    public void setLastRequestBean(RequestSummaryBean lastRequestBean) {
        this.lastRequestBean = lastRequestBean;
    }

    public RequestSummaryBean getPendingRequestBean() {
        return pendingRequestBean;
    }

    public void setPendingRequestBean(RequestSummaryBean pendingRequestBean) {
        this.pendingRequestBean = pendingRequestBean;
    }
    
    /**
     * 
     * DTO around the IdentityRequest(s) assoicated with this entitlement.
     * 
     * @author dan.smith     
     */
    public class RequestSummaryBean {
        /*
         * Date the item was requested.
         */
        Date date;
        
        /**
         * Operation of the request.
         */
        String op;
        
        /**
         * Full request id with leading zeros.
         */
        String requestId;
        
        /**
         * Requested Id with the leading zeros trimmed.
         */
        String trimmedRequestId;
        
        /**
         * Source of the entitlement, LCM, Aggregation, etc..
         */
        String source;
        
        /**
         * Name of the requestor.
         */
        String requester;
        
        /**
         * Current execution status of the request
         */
        IdentityRequest.ExecutionStatus executionStatus;
        
        public RequestSummaryBean(IdentityRequestItem ritem) {
            requestId = ritem.getIdentityRequest().getName();
            if ( requestId != null ) {
                trimmedRequestId = Util.stripLeadingChar(requestId, '0');
            }
            op = ritem.getOperation();
            requester = ritem.getIdentityRequest().getRequesterDisplayName();
            date = ritem.getIdentityRequest().getCreated();
            source = ritem.getIdentityRequest().getSource();
            executionStatus = ritem.getIdentityRequest().getExecutionStatus();
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getTrimmedRequestId() {
            return trimmedRequestId;
        }

        public void setTrimmedRequestId(String trimedRequestId) {
            this.trimmedRequestId = trimedRequestId;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getRequester() {
            return requester;
        }

        public void setRequester(String requester) {
            this.requester = requester;
        }
        
        public IdentityRequest.ExecutionStatus getExecutionStatus() {
            return executionStatus;
        }

        public void setExecutionStatus(IdentityRequest.ExecutionStatus executionStatus) {
            this.executionStatus = executionStatus;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Certification properties
    //
    //////////////////////////////////////////////////////////////////////////
    
    public CertSummaryBean getLastCertBean() {
        return lastCertBean;
    }

    public void setLastCertBean(CertSummaryBean lastCertBean) {
        this.lastCertBean = lastCertBean;
    }

    public CertSummaryBean getPendingCertBean() {
        return pendingCertBean;
    }

    public void setPendingCertBean(CertSummaryBean pendingCertBean) {
        this.pendingCertBean = pendingCertBean;
    }
    
    /**
     * DTO around the IdentityRequest(s) associated with this entitlement.
     * 
     * @author dan.smith     
     */
    public class CertSummaryBean {
    	/**
    	 * Comes from the certification object
    	 */
        String certId;
        
        /**
         * Certification name
         */
        String certName;
        
        /**
         * Granularity of the certification comes from the certification object.
         */
        Certification.EntitlementGranularity granularity;
        
        /**
         * Action comes from the Certification Item.
         */
        CertificationAction action;
        
        /**
         * the date the item was mitigated, comes from the Action. 
         */
        Date mitigationExpiration;
        
        /**
         *  1) Use the actor on the action if exists ( using the actor display name )
         *  2) Otherwise if null, use cert.getCertifiers and turn it into a list of displayNames
         */
        String certifier;
        
        /**
         * Resolve this from the action, but fall back to the action? 
         */
        String localizedActionStatus;
        
        /**
         * The date the item was finished.
         */
        Date finishDate;
        
        /**
         * The date the certification was created/generated.
         */
        Date certDate;
        
        public CertSummaryBean(CertificationItem item) throws GeneralException {            
                  
            
            AbstractCertificationItem.Status status = item.getSummaryStatus();
            action = item.getAction();
            if ( action != null ) {
                CertificationAction.Status actionStatus = action.getStatus();
                if ( action != null ) {
                    localizedActionStatus = actionStatus.getMessageKey();
                    if ( localizedActionStatus != null ) {
                        localizedActionStatus = getMessage(localizedActionStatus);
                    }                    
                }
                finishDate = action.getDecisionDate();
                certifier = action.getActorDisplayName();
                mitigationExpiration = action.getMitigationExpiration();
            }
            if ( localizedActionStatus == null && status != null ) 
                localizedActionStatus = status.getLocalizedMessage(getLocale(), getUserTimeZone());
            
            CertificationEntity entity = item.getParent();
            if ( entity != null ) {
                Certification cert = entity.getCertification();
                if ( cert != null ) {
                    certDate = cert.getCreated();
                    certId = cert.getId();
                    certName = cert.getName();
                    granularity = cert.getEntitlementGranularity();                       
                    //
                    // If we can't get it from the action use the cert's certifiers
                    //
                    if ( certifier == null ) {
                        List<String> certifiers = cert.getCertifiers();
                        if ( Util.size(certifiers) > 0 )  {

                            IdentityService identityService = new IdentityService(getContext());
                        	List<String> certifiersDisplayNames = new ArrayList<String>();
                        	for ( String idName : certifiers ) {                            	
                        		String certifierDisplayName = identityService.resolveIdentityDisplayName(idName);
                        		if ( certifierDisplayName != null ) {
                        			certifiersDisplayNames.add(certifierDisplayName);
                        		} else {
                        			certifiersDisplayNames.add(idName);
                        		}
                        	}
                        	if ( certifiersDisplayNames.size() > 0 )
                        	    certifier = Util.listToCsv(certifiersDisplayNames);
                        }                        
                    }
                }                
            }            
        }

        public String getCertId() {
            return certId;
        }

        public void setCertId(String certId) {
            this.certId = certId;
        }

        public String getCertName() {
            return certName;
        }

        public void setCertName(String certName) {
            this.certName = certName;
        }

        public Date getCertDate() {
            return certDate;
        }

        public void setCertDate(Date certDate) {
            this.certDate = certDate;
        }

        public Certification.EntitlementGranularity getGranularity() {
            return granularity;
        }

        public void setGranularity(Certification.EntitlementGranularity granularity) {
            this.granularity = granularity;
        }

        public Date getMitigationEndDate() {
            return mitigationExpiration;
        }

        public void setMitigationEndDate(Date mitigationEndDate) {
            this.mitigationExpiration = mitigationEndDate;
        }

        public Date getFinishDate() {
            return finishDate;
        }

        public void setFinishDate(Date finishDate) {
            this.finishDate = finishDate;
        }

        public String getCertifier() {
            return certifier;
        }

        public void setCertifier(String certifier) {
            this.certifier = certifier;
        }
        
        public String getLocalizedActionStatus() {
            return localizedActionStatus;
        }

        public void setLocalizedActionStatus(String localizedActionStatus) {
            this.localizedActionStatus = localizedActionStatus;
        }

    }
}
