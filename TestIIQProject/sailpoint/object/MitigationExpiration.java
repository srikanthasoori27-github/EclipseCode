/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A MitigationExpiration holds information about a business role or entitlement
 * list mitigation for the purpose of executing some action when the mitigation
 * expires.
 * 
 * TODO: We're starting to copy a good bit of data from the CertificationAction
 * and CertificationItem into the MitigationExpiration.  This makes loading the
 * information easy (and is necessary for the mitigation expiration date so that
 * we can query it), but is redundant.  Consider storing a soft link to the
 * CertificationItem and making the handler smart enough to resolve this for
 * both live and archived certifications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * A MitigationExpiration holds information about a business role or entitlement
 * list mitigation for the purpose of executing some action when the mitigation
 * expires.
 */
@XMLClass
public class MitigationExpiration extends SailPointObject {
    private static final long serialVersionUID = -7087658348492662632L;

    private static final int MAX_LEN_NATIVE_ID = 322;
    private static final int MAX_LEN_ATTR_NAME = 450;
    private static final int MAX_LEN_ATTR_VALUE = 450;
    
    private static Log log = LogFactory.getLog(MitigationExpiration.class); 

    /**
     * Enumeration of the different types of actions that can be executed when
     * a mitigation expires.
     * 
     * @ignore
     * TODO: Consider adding actions to:
     *  1) Kick-off a certification for this user.
     *  2) Execute a rule.
     */
    @XMLClass(xmlname="MitigationExpirationAction")
    public static enum Action {
        NOTHING(MessageKeys.MITIGATION_EXP_ACTION_DO_NOTHING),
        NOTIFY_CERTIFIER(MessageKeys.MITIGATION_EXP_ACTION_NOTIFY_CERTIFIER);

        private String messageKey;

        Action(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    /**
     * A Comparator that can be used to tell if two MitigationExpirations are
     * referring to the same mitigation request on the same type of
     * certification. See the javadoc for
     * @see #compare(MitigationExpiration, MitigationExpiration) for more
     * information about the comparison.
     */
    static class UniquenessComparator implements Comparator<MitigationExpiration> {

        /**
         * Compare the given MitigationExpirations to look for equality.
         * Use a combination of certification type and unique certification
         * information (the application being certified or the manager) to determine
         * if the request is coming from the same type of certification. 
         * Compare the business role, entitlements list, or constraint violation
         * info to see if the same item is being mitigated. There is really no
         * natural order to these, so try not to fake one.
         */
        public int compare(MitigationExpiration m1, MitigationExpiration m2) {

            if ((null == m1) || (null == m2))
                return -1;

            // Check for both links == null b/c the migiation may not be associated
            // with a certification, such as a standalone policy violation
            boolean linksEq =
                Util.nullSafeEq(m1.getCertificationLink(), m2.getCertificationLink(), true);
            
            if (linksEq) {
                boolean itemsEq = false;
                try {
                    if (null != m1.getCertifiableDescriptor()) {
                        itemsEq = m1.getCertifiableDescriptor().matches(m2.getCertifiableDescriptor());
                    }
                }
                catch (GeneralException e) {
                    // Comparator doesn't throw, so throw a RuntimeException.
                    throw new RuntimeException(e);
                }

                if (itemsEq) {
                    return 0;
                }
            }

            return -1;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////////

    private Date expiration;
    // jsl - would be nice if we could use _owner for this so we can 
    // search consistently
    private Identity mitigator;
    private Identity identity;
    private String comments;

    private CertificationLink certificationLink;

    private CertifiableDescriptor certifiableDescriptor;
    
    private Action action;
    private Map<String,Object> actionParameters;

    // Do we need this or should we just remove the MitigationExpiration after
    // it has been acted upon.  Storing the last date that the expiration was
    // acted on allows multiple actions - not sure if we want this or not.
    private Date lastActionDate;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public MitigationExpiration() {
        super();
    }

    /**
     * Constructor.
     * @param  cert      The Certification in which this mitigation request was
     *                   made.
     * @param  item      The CertificationItem for which this mitigation request
     *                   was made.
     * @param  resolver  The Resolver to use to resolve object references.
     */
    public MitigationExpiration(Certification cert, CertificationItem item,
                                Resolver resolver)
        throws GeneralException {

        this(item.getAction().getMitigationExpiration(), item.getAction().getActor(resolver),
             item.getAction().getComments(), new CertifiableDescriptor(item));
        this.certificationLink = new CertificationLink(cert, item.getParent());
        
        initializeMitigator(cert, resolver);
    }

    /**
     * Constructor.
     * @param  violation  The violation being mitigated
     * @param  mitigator  The identity performing the mitigation.
     * @param  expiration The date the mitigation expires.
     * @param  comments   Comments to include in the mitigation.
     */
    public MitigationExpiration(PolicyViolation violation, Identity mitigator,
                                Date expiration, String comments)
        throws GeneralException {

        this(expiration, mitigator, comments, new CertifiableDescriptor(violation));
    }

    private MitigationExpiration(Date expiration, Identity mitigator,
                                 String comments, CertifiableDescriptor desc) {
        super();
        this.expiration = expiration;
        this.mitigator = mitigator;
        this.comments = comments;
        this.certifiableDescriptor = desc;
    }
    
    private void initializeMitigator(Certification cert, Resolver resolver)
            throws GeneralException {
        
        // bug 21307 - It's possible mitigator: item.getAction().getActor() Identity no longer exists
        // If so, we'll try to set the mitigator to one of these: 
        // 1. cert.getCertifiers() 2. cert.getCertificationGroups().getOwnerIds(), 3. IIQ admin
        if (this.mitigator == null) {
            if (log.isInfoEnabled()) {
                log.info("Mitigator Identity no longer exists, we'll try to assign to one of the certification certifiers.");
            }
            
            List<String> certifiers = cert.getCertifiers();
            if (certifiers != null) {
                for (String certifier : certifiers) {
                    this.mitigator = resolver.getObjectByName(Identity.class, certifier);
                    if (this.mitigator != null) {
                        if (log.isInfoEnabled()) {
                            log.info("Set mitigator to certification certifier: " + this.mitigator.getDisplayName());
                        }
                        break;
                    }
                }
            }
        }
        if (this.mitigator == null) {
            List<CertificationGroup> certGroups = cert.getCertificationGroups();
            for (CertificationGroup certGroup : certGroups) {
                CertificationDefinition certDef = certGroup.getDefinition();
                if (certDef != null) {
                    List<String> ownerIds = certDef.getOwnerIds();
                    if (ownerIds != null) {
                        for (String ownerId : ownerIds) {
                            this.mitigator = resolver.getObjectById(Identity.class, ownerId);
                            if (this.mitigator != null) {
                                if (log.isInfoEnabled()) {
                                    log.info("Set mitigator to certification group owner: " + this.mitigator.getDisplayName());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
            
        if (this.mitigator == null) {
            BrandingService bs = BrandingServiceFactory.getService();
            if (bs != null) {
                this.mitigator = resolver.getObjectByName(Identity.class, bs.getAdminUserName());
            }
            if (this.mitigator != null && log.isInfoEnabled()) {
                log.info("Set mitigator to admin user: " + this.mitigator.getDisplayName());
            }
        }
    }
    
    
    @Override
    public boolean hasName() {
        return false;
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitMitigationExpiration(this);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters and setters
    //
    ////////////////////////////////////////////////////////////////////////////

    public Date getExpiration() {
        return expiration;
    }

    @XMLProperty
    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    public Identity getMitigator() {
        return mitigator;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setMitigator(Identity mitigator) {
        this.mitigator = mitigator;
    }

    public String getComments() {
        return comments;
    }

    @XMLProperty
    public void setComments(String comments) {
        this.comments = comments;
    }

    public CertificationLink getCertificationLink() {
        return this.certificationLink;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setCertificationLink(CertificationLink link) {
        this.certificationLink = link;
    }
    
    public CertifiableDescriptor getCertifiableDescriptor() {
        return this.certifiableDescriptor;
    }
    
    @XMLProperty(mode=SerializationMode.INLINE)
    public void setCertifiableDescriptor(CertifiableDescriptor d) {
        this.certifiableDescriptor = d;
    }
    
    public Identity getIdentity() {
        return identity;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "IdentityRef")
    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public Action getAction() {
        return action;
    }

    @XMLProperty
    public void setAction(Action action) {
        this.action = action;
    }

    public Map<String,Object> getActionParameters() {
        return actionParameters;
    }

    @XMLProperty
    public void setActionParameters(Map<String,Object> params) {
        this.actionParameters = params;
    }

    public Date getLastActionDate() {
        return lastActionDate;
    }

    @XMLProperty
    public void setLastActionDate(Date lastActionDate) {
        this.lastActionDate = lastActionDate;
    }

    @Override
    public boolean equals(Object o) {
        if (super.equals(o)) {
            MitigationExpiration otherObj = (MitigationExpiration)o;
            return (getExpiration() == otherObj.getExpiration() ||
                    getExpiration().equals(otherObj.getExpiration())) && 
                   (getCertificationLink() == otherObj.getCertificationLink()) ||
                    getCertificationLink().equals(otherObj.getCertificationLink()) &&
                   (getCertifiableDescriptor() == otherObj.getCertifiableDescriptor() ||
                    getCertifiableDescriptor().equals(otherObj.getCertifiableDescriptor())) &&
                   (getMitigator() == otherObj.getMitigator() ||
                    getMitigator().equals(otherObj.getMitigator()));
        } else {
            return false;            
        }
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("action", "Action");
        cols.put("mitigator", "Mitigator");
        cols.put("expiration", "Expiration");
        cols.put("lastActionDate", "Last Action Date");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-32s %-16s %-12s %-24s %-24s\n";
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Pseudo-properties
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean hasBeenActedUpon() {
        return (null != this.lastActionDate);
    }
    
    public Bundle getBusinessRole(Resolver resolver) throws GeneralException {
        Bundle bundle = null;
        if (null != this.certifiableDescriptor) {
            bundle = this.certifiableDescriptor.getBusinessRole(resolver);
        }
        return bundle;
    }

    public EntitlementSnapshot getEntitlements() {
        return (null != this.certifiableDescriptor) ?
            this.certifiableDescriptor.getExceptionEntitlements() : null;
    }

    public PolicyViolation getPolicyViolation() {
        return (null != this.certifiableDescriptor) ?
            this.certifiableDescriptor.getPolicyViolation() : null;
    }

    /*
     * Used by Jasper reports.
     */
    public List<EntitlementSnapshot> getEntitlementsList() {
        List<EntitlementSnapshot> list = null;
        EntitlementSnapshot es = getEntitlements();

        if (null != es) {
            list = new ArrayList<EntitlementSnapshot>();
            list.add(es);
        }

        return list;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Search properties
    //
    // These properties are exposed to improve the searchability of our model
    // for internal and external reporting.
    //
    ////////////////////////////////////////////////////////////////////////////


    /**
     * Get the role name
     * @return Name of the role mitigated, or null if this not a role certification item.
     */
    public String getRoleName(){
        return certifiableDescriptor != null ? certifiableDescriptor.getBusinessRole() : null;
    }

    /**
     * 
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setRoleName(String value){}

    /**
     * Get the Policy name
     * @return Name of the policy of the violation mitigated, or null if this not a violation certification item.
     */
    public String getPolicy(){
        return certifiableDescriptor != null && certifiableDescriptor.getPolicyViolation() != null ?
                certifiableDescriptor.getPolicyViolation().getPolicyName() : null;
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setPolicy(String policy){}

    /**
     * Get the constraint name
     * @return Name of the constraint of the violation mitigated, or null if this not a violation certification item.
     */
    public String getConstraintName(){
        return certifiableDescriptor != null && certifiableDescriptor.getPolicyViolation() != null ?
                certifiableDescriptor.getPolicyViolation().getConstraintName() : null;
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setConstraintName(String value){}


    /**
     * Get the application name
     * @return Name of the application of the entitlement(s) mitigated, or null if this
     * not an entitlement certification item.
     */
    public String getApplication() {
        if (certifiableDescriptor != null && certifiableDescriptor.getExceptionEntitlements() != null){
            return certifiableDescriptor.getExceptionEntitlements().getApplicationName();
        }

        return null;
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setApplication(String application) {}

    /**
     * Get the application instance
     * @return Name of the application instance of the entitlement(s) mitigated, or null if this
     * not an entitlement certification item.
     */
    public String getInstance() {
        if (certifiableDescriptor != null && certifiableDescriptor.getExceptionEntitlements() != null){
            return certifiableDescriptor.getExceptionEntitlements().getInstance();
        }

        return null;
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setInstance(String instance) {}

    /**
     * Get the native identity
     * @return The application account native identity of the entitlement(s) mitigated, or null if this
     * not an entitlement certification item.
     */
    public String getNativeIdentity() {
        if (certifiableDescriptor != null && certifiableDescriptor.getExceptionEntitlements() != null){
            return certifiableDescriptor.getExceptionEntitlements().getNativeIdentity();
        }

        return null;
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setNativeIdentity(String nativeIdentity) {}

    /**
     * Get the account display name
     * @return The name of the account of the entitlement(s) mitigated, or null if this
     * not an entitlement certification item.
     */
    public String getAccountDisplayName() {

        String val = null;

        if (certifiableDescriptor != null && certifiableDescriptor.getExceptionEntitlements() != null){
            val = certifiableDescriptor.getExceptionEntitlements().getDisplayName();
        }

        return truncate(val, MAX_LEN_NATIVE_ID);
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setAccountDisplayName(String displayName) {}

    /**
     * Get the attribute name
     * @return The attribute name OR permission target of the entitlement(s) mitigated, or null if this
     * not an entitlement certification item.
     */
    public String getAttributeName() {

        String val = null;

        EntitlementSnapshot snapshot = certifiableDescriptor != null ?
                certifiableDescriptor.getExceptionEntitlements() : null;
        if (snapshot != null && (snapshot.isAttributeGranularity() || snapshot.isValueGranularity())){
            if (snapshot.hasAttributes()){
                val = snapshot.getAttributes().getKeys().get(0);
            } else if (snapshot.hasPermissions()){
                val = snapshot.getPermissions().get(0).getTarget();
            }
        }

        return truncate(val, MAX_LEN_ATTR_NAME);
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setAttributeName(String value) {}

    /**
     * Get the attribute value
     * @return The attribute value OR permission rights of the entitlement(s) mitigated, or null if this
     * not an entitlement certification item.
     */
    public String getAttributeValue() {

        String val = null;

        EntitlementSnapshot snapshot = certifiableDescriptor != null ?
                certifiableDescriptor.getExceptionEntitlements() : null;
        if (snapshot != null && snapshot.isValueGranularity()){
            if (snapshot.hasAttributes()){
                List vals = Util.asList(snapshot.getAttributes().get(getAttributeName()));
                //IIQETN-4674 :- using a safer way to cast an Object to String
                val = String.valueOf(vals.get(0));
            } else if (snapshot.hasPermissions()){
                val= snapshot.getPermissions().get(0).getRights();
            }
        }

        return truncate(val, MAX_LEN_ATTR_VALUE);
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setAttributeValue(String value) {}

    /**
     * Check if this is a permission
     * @return True if the entitlement mitigated was a permission.
     */
    public boolean isPermission(){
        EntitlementSnapshot snapshot = certifiableDescriptor != null ?
                certifiableDescriptor.getExceptionEntitlements() : null;
        return snapshot != null &&
                (snapshot.isAttributeGranularity() || snapshot.isValueGranularity()) && snapshot.hasPermissions();
    }

    /**
     * @deprecated This property is derived and cannot be set.
     */
    @Deprecated
    public void setPermission(boolean value){}

    private static String truncate(String value, int size){

        if (value != null && value.length() > size){
            return value.substring(size);
        }

        return value;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Legacy properties
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @deprecated  Kept for legacy MitigationExpirations - now stored in the
     *              CertifiableDescriptor. 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE, legacy=true)
    public void setBusinessRole(Bundle businessRole) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        String name = (null != businessRole) ? businessRole.getName() : null;
        this.certifiableDescriptor.setBusinessRole(name);
    }

    /**
     * @deprecated  Kept for legacy MitigationExpirations - now stored in the
     *              CertifiableDescriptor. 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST, legacy=true)
    public void setEntitlements(List<EntitlementSnapshot> entitlements) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        // These aren't really lists ... just grab the first one.
        EntitlementSnapshot ents =
            ((null != entitlements) && !entitlements.isEmpty()) ? entitlements.get(0) : null; 
        this.certifiableDescriptor.setExceptionEntitlements(ents);
    }

    /**
     * @deprecated  Kept for legacy MitigationExpirations - now stored in the
     *              CertifiableDescriptor. 
     */
    @Deprecated
    @XMLProperty(legacy=true)
    public void setPolicyId(String id) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setPolicyId(id);
    }

    /**
     * @deprecated  Kept for legacy MitigationExpirations - now stored in the
     *              CertifiableDescriptor. 
     */
    @Deprecated
    @XMLProperty(legacy=true)
    public void setConstraintId(String id) {
        if (null == this.certifiableDescriptor) {
            this.certifiableDescriptor = new CertifiableDescriptor();
        }
        this.certifiableDescriptor.setConstraintId(id);
    }
}
