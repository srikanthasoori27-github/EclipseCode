/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used to maintain entitlement connections and
 * encapsulate some certification and request meta data  
 * for an identity.
 * 
 * In hibernate these object are modeled as a <Bag> structure which
 * provides the ability to query through the identity object,
 * (for example, identity.identityEntitlements.name), but prevents
 * them from being like an ordered collection or maintained
 * on the Identity. All operations on these values
 * (outside searching) must be performed directly on the
 * IdentityEntitlement object
 * 
 * This object models application entitlements, IdentityIQ assigned 
 * and IdentityIQ detected roles using this same structure 
 * and table.
 * 
 * These objects are typically created during 
 * aggregation.
 * 
 * @see sailpoint.api.Entitlizer
 * 
 * The role data is also handled by the RoleEntitlizer
 * and is typically executed during the refresh
 * process following entitlement correlation.
 * 
 * The meta data is currently targeted at certification
 * and request information. Hard links to the
 * CertificationItem and IdentityRequestItem are kept so they
 * can be queried and joined for querying/reporting 
 * richness.
 * 
 * The life-cycle of the meta-data is handled in 
 * api classes that execute during the certification
 * generation, certification finalization, request
 * approval and request verification apis.
 * 
 * @see sailpoint.api.CertificationEntitlizer
 * @see sailpoint.api.RequestEntitlizer
 * @see sailpoint.api.Entitlizer
 * @see sailpoint.api.RoleEntitlizer
 * 
 * Two references are kept for both certs and requests.
 * One for the PENDING certification which is one that has
 * not been finished and CURRENT certification which
 * is the last certification that was completed/finalized.
 * 
.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * @since 6.0
 * 
 */
@XMLClass
@Indexes({
  @Index(name="identity_entitlement_comp",property="identity"), 
  @Index(name="identity_entitlement_comp",property="application"),
  @Index(name="identity_entitlement_comp",property="nativeIdentity", caseSensitive=false), 
  @Index(name="identity_entitlement_comp",property="instance", caseSensitive=false),
  @Index(name="ident_entit_comp_name",property="identity"),
  @Index(name="ident_entit_comp_name",property="name", caseSensitive=false)
})
public class IdentityEntitlement extends PersistentIdentityItem {
    
    /**
     * The state of the Entitlement, which is how 
     * origination and current state of the entitlement are tracked.
     */
    @XMLClass(xmlname = "AggregationState")    
    public static enum AggregationState {        
        Connected, // found natively
        Disconnected,  // not found during the last scan
    }

    /**
     * Key of the attribute kept in the attribute map, which holds
     * a csv list of role names that indirect permitted an 
     * entitlement. 
     */
    public final static String SOURCE_ASSIGNABLE = "sourceAssignableRoles";

    /**
     * Key of the attribute kept in the attribute map, which holds
     * a csv list of role names that could have contributed
     * to the entitlement.
     */
    public final static String SOURCE_DETECTED = "sourceDetectedRoles";

    /**
     * Key of the boolean attribute that indicates when the permission
     * came from the "target" permission list and not the direct permissions
     * list.
     */
    private static final String TARGET_PERMISSION = "isTargetPermission";

    /**
     * Name of the identity column for reference.
     */
    public static final String IDENTITY_COLUMN = "identity";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -61388123987227261L;
    
    /**
     * The identity that the attribute is associated. We want a full
     * reference here to make use of searching on identity properties.
     * 
     * @ignore
     * Will need to consider how this relationship is managed.
     */
    Identity _identity;
    
    /**
     * The application Application where the entitlement originated.
     * 
     * This will be null in the case of IdentityIQ assignedRoles and detectedRoles
     * entitlements.
     */
    Application _application;
    
    /**
     * Applies only to IdentityIQ based "detectedRoles" entitlements.
     * 
     * When set to true it indicates that the detected role is 
     * allowed as part of the permits or requires list through 
     * one of the Identity's assignedRoles.
     */
    boolean _allowed;
    
    /**
     * Boolean indicating if an attribute was directly assigned.
     * 
     * Assigned for entitlements indicates it was requested
     * through LCM or marked assigned by some other means.
     * 
     * Assigned is not applicable to roles and the "assigned"
     * concept is implied by the "assignedRoles" attribute
     * name.
     */
    boolean _assigned;
    
    /**
     * A flag to indicate that the entitlement was granted indirectly
     * by one or more of the Identity's roles. This flag is set during
     * refresh and is used to find the additional entitlements.
     * for an identity at the UI Tier and in reports.
     */
    boolean _grantedByRole; 
        
    /**
     * The displayname of the user that assigned the value, 
     * if it was assigned. Can be null, or system.
     */
    String _assigner;
    
    /**
     * Did we find this down on the application when we aggregated?
     * This will always be null for the IdentityIQ role assignments and 
     * detections entitlements.
     *
     * This flag is set during aggregation and allows IdentityIQ to indicate
     * when an entitlement was not found during the last aggregation.
     * 
     */
    AggregationState _aggregationState;
    
    /**
     * Is the row a Permission, Entitlement, or Role( null).
     * 
     * @ignore
     * djs: can this be in the attributes map?
     */
    ManagedAttribute.Type type;
        
    /**
     * The source of the entitlement, typical values would 
     * be Task or LCM.  
     * 
     */
    String _source;

    /**
     * The id of the assignment.
     */
    String _assignmentId;

    /**
     * The note made by the requester during assignment.
     */
    String _assignmentNote;
    
    //
    // Certification
    //
    
    /**
     * Reference to the pending certification item that references
     * this entitlement. From the item to the entity,
     * and from the entity back to the parent
     * certification.
     * 
     * Something like this  :
     * <code>
     *    pendingCertificationItem.parent.certification.name
     *    pendingCertificationItem.parent.certification.create
     *    pendingCertificatoinItem.parent.certification.entitlmentGrainularity
     *    
     *    pendingCertificationItem.certifier
     *    pendingCertificationItem.finishedDate
     *    pendingCertificationItem.summaryStatus
     * </code>       
     */
    CertificationItem pendingCertificationItem;

    /**
     * Reference to the current certification item that references
     * this entitlement.
     */
    CertificationItem certificationItem;
    
    //
    // Request Info 
    //

    /**
     * The request item that references this entitlement.
     * From this the request information can be accessed:
     * 
     * <code>
     * requestItem.identityRequest.name ( sequencial request id )
     * requestItem.identityRequest.verified
     * requestItem.identityRequest.created
     * 
     * requestItem.operation
     * </code>
     */
    IdentityRequestItem requestItem;
    
    /**
     * The identity request item that is pending.
     */
    IdentityRequestItem pendingRequestItem;
    
    /**
     * Transient field that indicates that this was seen 
     * during the feed. This is not stored on the model, and
     * is used when processing in-coming data to detect if it
     * should be marked removed. 
     */
    transient boolean _foundInFeed;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityEntitlement () { }
    
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "id");
        cols.put("application.name", "Application");
        cols.put("nativeIdentity", "Account ID");
        cols.put("name", "Entitlement");
        cols.put("value", "Value");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-32.32s %-32.32s %-64.64s %-24.24s %-24s\n";        
    }
        
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application a) {
        _application = a;
    }    

    @XMLProperty
    public ManagedAttribute.Type getType() {
        return type;
    }

    public void setType(ManagedAttribute.Type type) {
        this.type = type;
    }
        
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="IdentityRef")
    public Identity getIdentity() {
        return _identity;        
    }

    public void setIdentity(Identity id) {
        _identity = id;
    }
    
    @XMLProperty
    public String getAssigner() {
        return _assigner;
    }

    public void setAssigner(String assigner) {
        _assigner = assigner;
    }    

    @XMLProperty
    public String getSource() {
        return _source;
    }

    public void setSource(String source) {
        _source = source;
    }

    @XMLProperty
    public String getAssignmentId() {
        return _assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        _assignmentId = assignmentId;
    }

    @XMLProperty
    public String getAssignmentNote() {
        return _assignmentNote;
    }

    public void setAssignmentNote(String assignmentNote) {
        _assignmentNote = assignmentNote;
    }

    @XMLProperty
    public boolean isAllowed() {
        return _allowed;
    }

    public void setAllowed(boolean allowed) {
        this._allowed = allowed;
    }

    @XMLProperty
    public boolean isAssigned() {
        return _assigned;
    }
    
    public void setAssigned(boolean isAssigned) {
        _assigned = isAssigned;
    }
    
    public boolean foundInFeed() {
        return _foundInFeed;
    }
    
    public void setFoundInFeed(boolean found) {
        _foundInFeed = true;
    }
    
    @XMLProperty
    public void setAggregationState(AggregationState aggregationState) {
        _aggregationState = aggregationState;
    }
    
    public AggregationState getAggregationState() {
        return _aggregationState;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Certification properties
    //
    //////////////////////////////////////////////////////////////////////////
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public CertificationItem getPendingCertificationItem() {
        return pendingCertificationItem;
    }

    public void setPendingCertificationItem(CertificationItem pendingCertificationItem) {
        this.pendingCertificationItem = pendingCertificationItem;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="CurrentCertificationItem")
    public CertificationItem getCertificationItem() {
        return certificationItem;
    }

    public void setCertificationItem(CertificationItem certificationItem) {
        this.certificationItem = certificationItem;
    }
    
    @Override
    public String toString() {
        String str = "appname='"+getAppName() + "' nativeIdentity='" + getNativeIdentity() + "' ("+getInstance()+")" + " attrName="+ getName() + " value="  + getValue();
        return str;           
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Identity Request properties
    //
    //////////////////////////////////////////////////////////////////////////
    
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="CurrentRequestItem")
    public IdentityRequestItem getRequestItem() {
        return requestItem;
    }

    public void setRequestItem(IdentityRequestItem requestItem) {
        this.requestItem = requestItem;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public IdentityRequestItem getPendingRequestItem() {
        return pendingRequestItem;
    }

    public void setPendingRequestItem(IdentityRequestItem pendingRequestItem) {
        this.pendingRequestItem = pendingRequestItem;
    }
    
    @XMLProperty
    public boolean isGrantedByRole() {
        return _grantedByRole;
    }

    public void setGrantedByRole(boolean grantedByRole) {
        this._grantedByRole = grantedByRole;
    }

    public String getSourceAssignableRoles() {
        return (String)getAttribute(SOURCE_ASSIGNABLE);
    }
    
    public void setSourceAssignableRoles(String sourceRoles) {
        setAttribute(SOURCE_ASSIGNABLE, sourceRoles);
    }
    
    public String getSourceDetectedRoles() {
        return (String)getAttribute(SOURCE_DETECTED);
    }
    
    public void setSourceDetectedRoles(String sourceRoles) {
        setAttribute(SOURCE_DETECTED, sourceRoles);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Util
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Returns true if the aggregation state of this entitlement is Connected.
     */
    public boolean isConnected() {
        return (Util.nullSafeEq(AggregationState.Connected,getAggregationState()));
    }

    /**
     * Returns true if the aggregation state of this entitlement is Disconnected.
     */
    public boolean isDisconnected() {
        return (Util.nullSafeEq(AggregationState.Disconnected,getAggregationState()));
    }
    
    public Source getSourceObject() {
        Source source = null;
        if  ( _source != null ) 
           source = Source.fromString(_source);
        return source;
    }
    
    public String getAppName() {
        String name = null;
        if ( _application != null ) 
            name = _application.getName();
        return name;
    }
    
    /**
     * Method help to locate a matching AttributeAssignment while
     * iterating over a list of entitlements or vice-versa.
     * 
     * @param assignment AttributeAssignment to compare
     * @return true when the app, nativeIdentity, instance, name 
     *         and value from both object match.
     */
    public boolean matches(AttributeAssignment assignment) {
        String appName = getAppName();
        String nativeIdentity = getNativeIdentity();
        String instance = getInstance();
        String attrName = getName();
        Object val = getValue();
        if ( Util.nullSafeEq(appName, assignment.getApplicationName()) &&
             Util.nullSafeEq(nativeIdentity, assignment.getNativeIdentity()) &&
             Util.nullSafeEq(instance, assignment.getInstance(), true ) &&
             Util.nullSafeEq(attrName, assignment.getName() ) &&
             Util.nullSafeEq(val, assignment.getValue() ) ) {
            return true;
        }
        return false;        
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        
        // this will get id and name
        int result = super.hashCode();        
        
        result = prime * result + (_allowed ? 1231 : 1237);
        result = prime * result + (_assigned ? 1231 : 1237);
        result = prime * result + (_grantedByRole ? 1231 : 1237);

        result = prime * result;
        if ( _aggregationState != null ) 
            result += _aggregationState.hashCode();
        
        result = prime * result;
        if ( _application != null )
            result +=  _application.hashCode();        
        
        result = prime * result;
        if ( _assigner != null )
            result +=  _assigner.hashCode();       
        
        result = prime * result;
        if ( _identity != null )
            result +=  _identity.hashCode();    
        
        result = prime * result;
        if ( _source != null )
            result +=  _source.hashCode();  
        
        result = prime * result;
        if ( certificationItem != null )
            result +=  certificationItem.hashCode();  
        
        result = prime * result;
        if ( pendingCertificationItem != null )
            result +=  pendingCertificationItem.hashCode();  
        
        result = prime * result;
        if ( pendingRequestItem != null )
            result +=  pendingRequestItem.hashCode();  
        
        result = prime * result;
        if ( requestItem != null )
            result +=  requestItem.hashCode();  
        
        result = prime * result;
        if ( type != null )
            result +=  type.hashCode(); 
        
        result = prime * result;
        if ( _value != null )
            result += _value.hashCode();
        
        result = prime * result;
        if ( _instance != null )
            result += _instance.hashCode();
        
        result = prime * result;
        if ( _annotation != null )
            result += _annotation.hashCode();
        
        result = prime * result;
        if ( _nativeIdentity != null )
            result += _nativeIdentity.hashCode();
        
        result = prime * result;
        if ( _attributes != null )
            result += _attributes.hashCode();

        result = prime * result;
        if ( _displayName != null )
            result += _displayName.hashCode();
        
        result = prime * result;
        if ( _endDate != null )
            result += _endDate.hashCode();
        
        result = prime * result;
        if ( _startDate != null )
            result += _startDate.hashCode();
        
        return result;
    }

    @Override
    public boolean equals(Object e) {
        IdentityEntitlement ent = null;
                
        if ( e != null && e instanceof IdentityEntitlement ) {
           ent = (IdentityEntitlement)e; 
        }
        if ( ent == null ) 
            return false;
        
        if ( !super.equals(e) ) 
            return false;
        
        if (( Util.nullSafeEq(getValue(), ent.getValue() )) &&
            ( Util.nullSafeEq(getIdentity(), ent.getIdentity() )) &&
            ( Util.nullSafeEq(getNativeIdentity(), ent.getNativeIdentity() )) &&
            ( Util.nullSafeEq(isAllowed(), ent.isAllowed())) && 
            ( Util.nullSafeEq(isAssigned(), ent.isAssigned())) && 
            ( Util.nullSafeEq(isGrantedByRole(), ent.isGrantedByRole())) && 
            ( Util.nullSafeEq(getType(), ent.getType(), true )) &&
            ( Util.nullSafeEq(getInstance(), ent.getInstance(), true )) && 
            ( Util.nullSafeEq(getSource(), ent.getSource(), true )) && 
            ( Util.nullSafeEq(getInstance(), ent.getInstance(), true )) && 
            ( Util.nullSafeEq(getAssigner(), ent.getAssigner(), true )) && 
            ( Util.nullSafeEq(getAggregationState(), ent.getAggregationState(), true )) && 
            ( Util.nullSafeEq(getStartDate(), ent.getStartDate(), true)) &&
            ( Util.nullSafeEq(getEndDate(), ent.getEndDate(), true)) &&
            ( Util.nullSafeEq(getPendingCertificationItem(), ent.getPendingCertificationItem(), true)) &&
            ( Util.nullSafeEq(getCertificationItem(), ent.getCertificationItem(), true)) &&
            ( Util.nullSafeEq(getRequestItem(), ent.getRequestItem(), true)) &&
            ( Util.nullSafeEq(getPendingRequestItem(), ent.getPendingRequestItem(), true)) &&
            ( Util.nullSafeEq(getApplication(), ent.getApplication(), true)) ) {
           
            return true;
        }        
        return false;      
    }
}
