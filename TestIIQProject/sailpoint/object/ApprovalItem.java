/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * A class used during the approval process. This class will represent
 * a line item in an approval workitem, or business process.
 * 
 * These objects are typically contained by an ApprovalSet.
 * ApprovalSets. Which typically live in the attributes
 * map of an Approval WorkItem.
 * 
 * In 5.5 the ProvisioningPlan field was deprecated. This is used to 
 * use the approvalItems as the basis for Provisioning Checking,
 * but this is now happening using the new IdentityRequest 
 * objects.  
 * 
 */
@XMLClass
public class ApprovalItem extends IdentityItem {

    //////////////////////////////////////////////////////////////////////  
    // 
    // Enumerations
    //
    //////////////////////////////////////////////////////////////////////  

    // djs: not sure if we need something to indicate the item hasn't been 
    // provisioned.  For now we'll let null indicate that state.
    
    //
    // TODO : think about if there is more then just
    // Pending and Finished?
    //
    @XMLClass(xmlname = "ProvisioningState")
    public static enum ProvisioningState implements MessageKeyHolder {

        /**
         * Null state indicates the request queued.
         */

        /**
         * The plan has been executed, but the changes
         * have not been completed. This is the default
         * state the UI maps to null.       
         */
        Pending(MessageKeys.APPROVALITEM_PROVISIONING_STATE_PENDING),

        /**
         * The plan has been executed and the changes have
         * been applied and verified.
         */
        Finished(MessageKeys.APPROVALITEM_PROVISIONING_STATE_FINISHED),
        
        /**
         * The plan has been executed but this item was not found 
         * in the application's schema and therefore can not be verified by
         * the scanner.  
         */
        Unverifiable(MessageKeys.APPROVALITEM_PROVISIONING_STATE_NOT_VERIFIABLE),

        /**
         * The plan has been executed and successfully applied
         * by an integration.  The object has yet to be verify,
         * which will take it to the Finished state.
         */
        Commited(MessageKeys.APPROVALITEM_PROVISIONING_STATE_COMMITED),

        /**
         * The plan has been executed and there was a
         * failure indicated by the integration.
         */
        Failed(MessageKeys.APPROVALITEM_PROVISIONING_STATE_FAILED),

        /**
         * The plan has been executed but had a non fatal
         * error that will be retried.
         */
        Retry(MessageKeys.APPROVALITEM_PROVISIONING_STATE_RETRY); 
        
        private String messageKey;
        
        private ProvisioningState(String messageKey) {
            this.messageKey = messageKey;
        }
        
        public String getMessageKey() {
            return this.messageKey;
        }
    } 

    //////////////////////////////////////////////////////////////////////  
    // 
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static final long serialVersionUID = -61384782522227261L;

    /**   
     * Unique ID used in matching ProvisioningPlan fragments to an Item
     */
    String _id;

    /**
     * Display name of the attribute that was changed.
     */
    private String _displayName;

    /**
     * Display value of the attribute that was changed 
     */
    private String _displayValue;
    
    /**
     * The string representation of ProvisioningPlan.AttributeRequest.Operation
     * or ProvisioningPlan.Operation
     */
    private String _operation;

    /**
     * Optional time at which a requested item will be given.
     * Sometimes known as a "sunrise" date.
     * This is only relevant if _operation is "Add".
     */
    private Date _startDate;

    /**
     * Optional time at which a requested item will be taken away.
     * Sometimes known as a "sunset" date.
     */
    private Date _endDate;

    /**
     * Requester comments
     */
    private String _requesterComments;

    /**
     * Comments added to this Item as it goes through
     * the approval process.
     */
    private List<Comment> _comments;

    /**
     * The approval state copied from the work item.
     * @ignore
     * WorkItem.State is more general than needed here but
     * it carries over in case it is needed in the future.
     * Use the isApproved and isRejected methods to make the
     * usual boolean decision.
     */
    private WorkItem.State _state;

    /**
     * The owner of the approval item.
     */
    private String _owner;
    
    /**
     * The approver of the approval item. This is necessary in case the owner is a workgroup
     * and an individual member of the workgroup approves the work item
     */
    private String _approver;

    /**
     * A bag of attributes that can store any other interesting information
     * about the approval item.
     */
    private Attributes<String,Object> _attributes;
    
    /**
     * the ProvisioningPlan that will be executed if this item is approved.
     * This will be evaluated by a ProvisioningStatusScanner to 
     * compute if the item has been applied successfully.
     */
    private ProvisioningPlan _plan;

    /**
     * The Provisioning status of this item, that will be used to convey
     * if this item has been successfully applied to the Identity.  
     * The provisioning request is kicked off, then periodically checked for
     * the identity to see if the changes have been made.
     */
    private ProvisioningState _provisioningState;
    
    /**
     * localized description of the item
     */
    private String _description;
   
    /**
     * assignment id
     */
    private String _assignmentId;

    /**
     * the optional recommendation from a Recommender, suggesting whether
     * or not this should be approved.
     */
    Recommendation _recommendation;

    private static final String ATTR_REJECTERS = "rejecters";

    public static final String ATT_UPDATED_NATIVE_ID = "nativeIdUpdated";

    //////////////////////////////////////////////////////////////////////  
    // 
    // Properties
    //
    //////////////////////////////////////////////////////////////////////  

    public ApprovalItem () {
       _id = Util.uuid();
    }

    public ApprovalItem(ApprovalItem item) {
        _id = item.getId();
        _accountDisplayName = item._accountDisplayName;
        _annotation = item._annotation;
        _application = item._application;
        _applicationId = item._applicationId;
        _approver = item._approver;
        _assignmentId = item._assignmentId;
        //This will not clone the attributes, do we need to?
        _attributes = item._attributes;
        _comments = item._comments;
        _description = item._description;
        _displayName = item._displayName;
        _displayValue = item._displayValue;
        _endDate = item._endDate;
        _instance = item._instance;
        _name = item._name;
        _nativeIdentity = item._nativeIdentity;
        _operation = item._operation;
        _owner = item._owner;
        _path = item._path;
        _permission = item._permission;
        _plan = item._plan;
        _provisioningState = item._provisioningState;
        _requesterComments = item._requesterComments;
        _recommendation = item._recommendation;
        _role = item._role;
        _startDate = item._startDate;
        _state = item._state;
        _value = item._value;
    }

    public ApprovalItem clone() {
        return new ApprovalItem(this);
    }

    @XMLProperty
    public String getId() {
        return _id;
    }

    public void setId(String id) {
        _id = id;
    }

    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String name) {
        _displayName = name;
    }

    @XMLProperty
    public String getDisplayValue() {
        return this._displayValue;
    }

    public void setDisplayValue(String displayValue) {
        _displayValue = displayValue;
    }

    /**
     * Helper method to return either the displayValue or value as a string.
     */
    public String getDisplayableValue() {
        Object val = (!Util.isNullOrEmpty(_displayValue)) ? _displayValue : _value; 
        return (null != val) ? val.toString() : null;
    }
    
    @XMLProperty
    public String getOperation() {
        return _operation;
    }

    public void setOperation(String op) {
        _operation = op;
    }

    @XMLProperty
    public Date getEndDate() {
        return _endDate;
    }

    public void setEndDate(Date d) {
        _endDate = d;
    }

    @XMLProperty
    public Date getStartDate() {
        return _startDate;
    }

    public void setStartDate(Date d) {
        _startDate = d;
    }

    @XMLProperty
    public WorkItem.State getState() {
        return _state;
    }

    public void setState(WorkItem.State state) {
        _state = state;
    }

    @XMLProperty
    public String getRequesterComments() {
        return _requesterComments;
    }

    public void setRequesterComments(String comments) {
        _requesterComments = comments;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Recommendation getRecommendation() { return _recommendation; }

    public void setRecommendation(Recommendation recommendation) { _recommendation = recommendation; }

    @XMLProperty(mode = SerializationMode.LIST, xmlname = "ApprovalItemComments")
    public List<Comment> getComments() {
        return _comments;
    }

    public void setComments(List<Comment> comments) {
        _comments = comments;
    }

    @XMLProperty
    public String getOwner() {
        return _owner;
    }

    public void setOwner(String owner) {
        _owner = owner;
    }
    
    @XMLProperty
    public String getApprover() {
        return _approver;
    }

    public void setApprover(String approver) {
        _approver = approver;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> attrs) {
        _attributes = attrs;
    }

    public Object getAttribute(String key) {
        return (null != _attributes) ? _attributes.get(key) : null;
    }

    public void setAttribute(String key, Object value) {
        if (null == _attributes) {
            _attributes = new Attributes<String,Object>();
        }
        _attributes.put(key, value);
    }

    /**
     * @deprecated This is now handled by {@link IdentityRequest} and {@link IdentityRequestItem} objects
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    @Deprecated
    public ProvisioningPlan getProvisioningPlan() {
        return _plan;
    }

    /**
     * @deprecated This is now handled by {@link IdentityRequest} and {@link IdentityRequestItem} objects
     */
    @Deprecated
    public void setProvisioningPlan(ProvisioningPlan plan) {
        _plan = plan;
    }

    @XMLProperty
    public ProvisioningState getProvisioningState() {
        return _provisioningState;
    }

    public void setProvisioningState(ProvisioningState provisioningState) {
        _provisioningState = provisioningState;
    }
    
    //////////////////////////////////////////////////////////////////////  
    // 
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////  

    public void approve() {
        _state = WorkItem.State.Finished;
    }

    public void reject() {
        _state = WorkItem.State.Rejected;
    }
    
    public void undo() {
        _state = null;
    }

    public boolean isComplete() {
        return (_state != null);
    }

    public boolean isApproved() {
        return (_state == WorkItem.State.Finished);
    }

    /**
     * The WorkItem.State model is simplified and anything
     * other than Finished is treated as a reject.
     */
    public boolean isRejected() {
        return (_state != null && _state != WorkItem.State.Finished);
    }

    public boolean isProvisioningComplete() {
        return (_provisioningState == ProvisioningState.Finished);
    }

    public void add(Comment comment) {
        if ( _comments == null ) {
            _comments = new ArrayList<Comment>();
        }
        _comments.add(comment); 
    }

    /**
     * This uses similar logic as ApprovalSet.find(ApprovalItem).
     * @param ApprovalItem
     * @return true if the passed ApprovalItem matches this ApprovalItem
     */
    public boolean matches(ApprovalItem item) {

        if ( item == null ) return false;

        boolean isMatch = false;

        if ( ( !Util.nullSafeEq(item.getApplication(), this.getApplication(), true) ) ||
             ( !Util.nullSafeEq(item.getNativeIdentity(), this.getNativeIdentity(), true) ) ||
             ( !Util.nullSafeEq(item.getOperation(), this.getOperation(), true) ) ||
             ( !Util.nullSafeEq(item.getName(), this.getName(), true) ) ||
             ( !Util.nullSafeEq(item.getValueList(), this.getValueList(), true) ) ||
             ( !Util.nullSafeEq(item.getInstance(), this.getInstance(), true) ) ||
             ( !Util.nullSafeEq(item.getAssignmentId(), this.getAssignmentId(), true) ) ) {
            isMatch = false;
        } else {
            isMatch = true;
        }

        return isMatch;
    }

    /** A utility method that gets the attributes from the IIQPlan for this ApprovalItem
     */
    public List<AttributeRequest> getIIQAttributes() {
        List<AttributeRequest> attrs= null;
        if(_plan!=null) {
            AccountRequest request = _plan.getIIQAccountRequest();
            if(request!=null) {
                attrs = request.getAttributeRequests();
            }
        }
        return attrs;
    }
    
    /**
     * When application is "IIQ"  
     * "IdentityIQ" must be returned
     * 
     */
    public String getApplicationName() {
        
        return ProvisioningPlan.getApplicationDisplayName(getApplication());
    }

    /**
     * Add the name of one of the Identities who caused this ApprovalItem to be rejected
     * @param rejecter Name of an Identity who caused this ApprovalItem to be rejected
     */
    public void addRejecter(String rejecter) {
        if (!Util.isNullOrEmpty(rejecter)) {
            if (_attributes == null) {
                _attributes = new Attributes<String, Object>();
            }

            List<String> rejecters = (List<String>)_attributes.get(ATTR_REJECTERS);
            if (Util.isEmpty(rejecters)) {
                rejecters = new ArrayList<String>();
            }

            if (!rejecters.contains(rejecter)){
                rejecters.add(rejecter);
                _attributes.put(ATTR_REJECTERS, rejecters);
            }
        }
    }

    /**
     * @return CSV list of the Identities who rejected one or more portions of this ApprovalItem
     */
    public String getRejecters() {
        String rejectersString;

        List<String> rejecters = _attributes == null ? null : (List<String>)_attributes.get(ATTR_REJECTERS);
        if (Util.isEmpty(rejecters)) {
            // Assume that the owner rejected if no rejecters were added in order
            // to be consistent with the legacy behavior
            rejectersString = getOwner();
        } else {
            rejectersString = Util.listToCsv(rejecters);
        }

        return rejectersString;
    }

    /**
     * Set the entire rejecters list from a csv.
     * @ignore
     * For some reason this is stored internally as a List<String>
     * but you can only retrieve it as a csv.
     */
    public void setRejecters(String csv) {
        List<String> list = Util.csvToList(csv);
        if (Util.size(list) > 0) {
            setAttribute(ATTR_REJECTERS, list);
        }
        else if (_attributes != null) {
            _attributes.remove(ATTR_REJECTERS);
        }
    }

    @XMLProperty
    public String getAssignmentId() {
        return _assignmentId;
    }

    public void setAssignmentId(String _assignmentId) {
        this._assignmentId = _assignmentId;
    }

    /**
     * In approvalItems the value is in form [ name = 'value' ] for example: [ groupmbr = 'Benefits' ]
     * this returns the value part
     *
     * @param value
     * @return 'Benefits' in the example provided
     */
    public static String parseAprovalItemValue(String value) {
        int positionOfEqual = value.indexOf("=");
        String approvalItemValue = value.substring(positionOfEqual + 3, value.lastIndexOf("'"));

        return approvalItemValue;
    }

    /**
     * In approvalItems the value is in form [ name = 'value' ] for example: [ groupmbr = 'Benefits' ]
     * this returns the name part
     *
     * @param value
     * @return 'groupmbr' in the example provided
     */
    public static String parseApprovalItemName(String value) {
        int positionOfEqual = value.indexOf("=");
        String approvalItemName = value.substring(0, positionOfEqual - 1);

        return approvalItemName;
    }
}