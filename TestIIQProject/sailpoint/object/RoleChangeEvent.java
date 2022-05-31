package sailpoint.object;

import java.util.HashSet;
import java.util.Set;

import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * Class used to represent role change event whenever role is modified
 */
@XMLClass
@Indexes({@Index(name="spt_role_change_event_created", property="created")})
public class RoleChangeEvent extends SailPointObject
    implements Cloneable{

    @XMLClass(xmlname="RoleChangeEventStatus")
    public enum Status implements MessageKeyHolder {
        Pending(MessageKeys.PENDING),
        Failed(MessageKeys.FAILED),
        Success(MessageKeys.SUCCESS),
        Pruned(MessageKeys.PRUNED);

        private String messageKey;

        private Status(String messageKey){
            this.messageKey = messageKey;
        }

        public String getMessageKey(){
            return messageKey;
        }

        public boolean isEqual(String name) {
            return this.toString().equals(name);
        }
    }

    private static final long serialVersionUID = 5362470191828135048L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Represents a bundle object for which event is generated
     */
    String _bundleId;

    /**
     * Name of the bundle for which events will be generated
     */
    String _bundleName;

    /**
     * ProvisioningPlan contains the entitlements to be added or removed
     */
    ProvisioningPlan _provisioningPlan;

    /**
     * True if the specified bundle is pending for delete.
     */
    boolean _bundleDeleted;
    
    /**
     * Count of all Identities that are affected by this RoleChangeEvent
     */
    int _affectedIdentityCount;
    
    /**
     * List of skipped Identity IDs due to failure in previous events.
     */
    Set<String> _skippedIdentityIds;
        
    /**
     * List of Identity IDs failed in processing.
     */
    Set<String> _failedIdentityIds;
        
    /**
     * Status of this RoleChangeEvent.
     */
    Status _status;
    
    /**
     * Number of runs for this RoleChangeEvenet.
     */
    int _runCount = 0;

    /**
     * Number of failed attempts for this RoleChangeEvenet.
     */
    int _failedAttempts = 0;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    public RoleChangeEvent() {
    }

    public RoleChangeEvent(String bundleId, String bundleName, ProvisioningPlan plan) {
        _bundleId = bundleId;
        _bundleName = bundleName;
        _provisioningPlan = plan;
        _bundleDeleted = false;
    }

    public RoleChangeEvent(String bundleId, String bundleName, ProvisioningPlan plan, boolean isDeleted) {
        _bundleId = bundleId;
        _bundleName = bundleName;
        _provisioningPlan = plan;
        _bundleDeleted = isDeleted;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the bundle id associated with this event 
     */
    @XMLProperty
    public String getBundleId() {
        return _bundleId;
    }

    /**
     * Set the bundle associated with this event 
     * @param bundleId
     */
    public void setBundleId(String bundleId) {
        _bundleId = bundleId;
    }

    /**
     * Get name of the bundle associated with this event 
     */
    @XMLProperty
    public String getBundleName() {
        return _bundleName;
    }

    /**
     * Set the bundle name associated with this event 
     * @param bundleName
     */
    public void setBundleName(String bundleName) {
        _bundleName = bundleName;
    }

    /**
     * Get the ProvisioningPlan associated with this event 
     * @return ProvisioningPlan
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ProvisioningPlan getProvisioningPlan() {
        return _provisioningPlan;
    }

    /**
     * Set the ProvisioningPlan associated with this event
     * @param plan
     */
    public void setProvisioningPlan(ProvisioningPlan plan) {
        _provisioningPlan = plan;
    }

    /**
     * Returns true if the bundle is pending for delete.
     */
    @XMLProperty
    public boolean isBundleDeleted() {
        return _bundleDeleted;
    }

    public void setBundleDeleted(boolean b) {
        _bundleDeleted = b;
    }

    
    @XMLProperty
    public Status getStatus() {
        if (_status == null) {
            return Status.Pending;
        } else {
            return _status;
        }
    }

    public void setStatus(Status status) {
        this._status = status;
    }

    @XMLProperty
    public int getRunCount() {
        return _runCount;
    }

    public void setRunCount(int runCount) {
        _runCount = runCount;
    }

    @XMLProperty
    public int getFailedAttempts() {
        return _failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        _failedAttempts = failedAttempts;
    }
    
    public void incrementFailedAttempts() {
        _failedAttempts++;
    }
    
    public void resetFailedAttempts() {
        _failedAttempts = 0;
    }

    
    /**
     * Returns the Set of Identity IDs that skipped due to failure in previous events.
     * 
     * @return The set of Identity IDs that skipped due to failure in previous events.
     */
    @XMLProperty(mode = SerializationMode.SET)
    public Set<String> getSkippedIdentityIds()
    {
        return _skippedIdentityIds;
    }

    public void setSkippedIdentityIds(Set<String> skippedIdentityIds)
    {
        _skippedIdentityIds = skippedIdentityIds;
    }

    public void clearSkippedIdentityIds()
    {
        if (null != _skippedIdentityIds) {
            _skippedIdentityIds.clear();
        }
    }

    public void addSkippedIdentityId(String skippedIdentityId) {
        if (null == _skippedIdentityIds) {
            _skippedIdentityIds = new HashSet<String>();
        }
        _skippedIdentityIds.add(skippedIdentityId);
    }

    public void removeSkippedIdentityId(String skippedIdentityId) {
        if (null == _skippedIdentityIds) {
            _skippedIdentityIds = new HashSet<String>();
        }
        _skippedIdentityIds.remove(skippedIdentityId);
    }
    
    /**
     * Returns the Set of Identity Ids that failed in processing.
     * 
     * @return The set of Identity Ids that failed.
     */
    @XMLProperty(mode = SerializationMode.SET)
    public Set<String> getFailedIdentityIds()
    {
        return _failedIdentityIds;
    }

    public void setFailedIdentityIds(Set<String> failedIdentityIds)
    {
        _failedIdentityIds = failedIdentityIds;
    }

    public void clearFailedIdentityIds() {
        if (null != _failedIdentityIds) {
            _failedIdentityIds.clear();
        }
    }

    public void addFailedIdentityId(String failedIdentityId) {
        if (null == _failedIdentityIds) {
            _failedIdentityIds = new HashSet<String>();
        }
        _failedIdentityIds.add(failedIdentityId);
    }

    public void removeFailedIdentityId(String failedIdentityId) {
        if (null == _failedIdentityIds) {
            _failedIdentityIds = new HashSet<String>();
        }
        _failedIdentityIds.remove(failedIdentityId);
    }
    
    @XMLProperty
    public int getAffectedIdentityCount() {
        return _affectedIdentityCount;
    }
    
    public void setAffectedIdentityCount(int affectedIdentityCount) {
        _affectedIdentityCount = affectedIdentityCount;
    }

    /**
     * This does not have a name
     */
    @Override
    public boolean hasName(){
        return false;
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }
}
