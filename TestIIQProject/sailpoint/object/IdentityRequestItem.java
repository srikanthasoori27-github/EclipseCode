/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.api.SailPointFactory;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.service.AttachmentDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * An object to describe the items that were requested for
 * provisioning.
 *
 * This object is a just like ApprovalItem but is encapsulated in its 
 * own class because of they inheritance model of the ApprovalItem
 * objects.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@XMLClass
public class IdentityRequestItem extends PersistentIdentityItem {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -61388123987227261L;
    
    @XMLClass(xmlname = "CompilationStatus")    
    public static enum CompilationStatus implements MessageKeyHolder {

        /**
         * The item was requested, but was filtered
         * due to the compilation process detecting the
         * item already existed.
         */
        Filtered(MessageKeys.IDENTITY_REQUEST_ITEM_COMP_STATUS_FILTERED),
        
        /**
         * The item was added to the request during compilation
         * because it was necessary for a requested item to 
         * be fulfilled. For example, adding a Role might
         * require a new account and some number of entitlements.
         * 
         */
        Expansion(MessageKeys.IDENTITY_REQUEST_ITEM_COMP_STATUS_EXPANSION);
        
        private String messageKey;
        
        CompilationStatus(String messageKey) {
            this.messageKey = messageKey;
        }
        
        public String getMessageKey() {
            return this.messageKey;
        }
    }

    String _application;

    /**
     * Instance identifier for template applications.
     */
    String _instance;

    /**
     * Native identity of the application account link.
     */
    String _nativeIdentity;

    /**
     * Name of an attribute or target of a permission.
     */
    String _name;

    /**
     * Value of an attribute or rights of a permission.
     * Rights are represented as a CVS string.
     */
    Object _value;
   
    /**
     * Annotation of the permission. Undefined for attributes.
     */
    String _annotation;

    String _operation;

    /**
     * Optional time at which a requested item will be given.
     * Sometimes known as a "sunrise" date.
     * This is only relevant if _operation is "Add".
     */
    Date _startDate;

    /**
     * Optional time at which a requested item will be taken away.
     * Sometimes known as a "sunset" date.
     */
    Date _endDate;
    
    String _ownerName;
    
    String _approverName;

    WorkItem.State _approvalState;
    
    ProvisioningState _provisioningState;
    
    CompilationStatus _compilationStatus;      

    ExpansionItem.Cause _expansionCause;
    
    int _retries;

    String _provisioningEngine;
    
    IdentityRequest _identityRequest;

    List<Attachment> _attachments;
    
    /**
     * Comments that came part of the request for an item.
     */    
    public static final String ATT_REQUESTER_COMMENTS = "requesterComments";
    
    /**
     * The provisioning plan that was executed.
     */
    public static final String ATT_PROVISIONING_PLAN = "provisioningPlan";


    /**
     * The AssignmentID assoicated to the item
     */
    public static final String ATT_ASSIGNMENT_ID = "assignmentId";

    /**
     * List of Message objects store the warnings/errors specified at 
     * attribute and account request level on the item.
     */
    public static final String ATT_WARNINGS = "warnings";
    public static final String ATT_ERRORS = "errors";
    
    /**
     * An id or string that can be returned by an integration that
     * can be used for subsequent status checks.
     * 
     * This can be returned by a PE in the results of a Plan and used
     * when calling the Connector.checkStatus(String id) method 
     * when provisioning asynchronously.
     */    
    public static final String ATT_PROVISIONING_REQUEST_ID = "provisioningRequestId";

    /**
     * Additional information about the reason for the expansion, such as the
     * name of the role that was expanded, etc...  This will only be set if the
     * compilation status is expansion.
     */
    public static final String ATT_EXPANSION_INFO = "expansionInfo";

    /**
     * TrackingId set when splitting plans. This will be set if the IdentityRequest does not have a nativeIdentity. This
     * will be used to set the nativeIdentity on all corresponding items when the create happens.
     */
    public static final String ATT_TRACKING_ID = "trackingId";

    /**
     * Used to specify the managed attribute type so we can create the correct type of IdentityEntitlement from this request
     */
    public static final String ATT_MANAGED_ATTRIBUTE_TYPE = "managedAttributeType";
    

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityRequestItem () { }
    
    /**
     * Create an IdentityRequest based on an AccountRequest.
     * 
     * @param req AccountRequest to use as source
     */
    public IdentityRequestItem(AccountRequest req) throws GeneralException{
        this();
        String id = req.getNativeIdentity();
        setNativeIdentity(id);
        String app = req.getApplication();
        setApplication(app);
        String instance = req.getInstance();
        setInstance(instance);

        AccountRequest.Operation op = req.getOperation();
        if ( op == null ) 
            op = AccountRequest.Operation.Modify;
      
        setOperation(op.toString());
        Attributes<String,Object> attrs = req.getArguments();
        if ( attrs != null ) {
            setAttributes(new Attributes<String,Object>(attrs));
        }

        List<AttachmentDTO> attachmentDTOs = null != attrs ? (List<AttachmentDTO>) attrs.get("attachments") : new ArrayList<>();
        if (null != attachmentDTOs) {
            List<Attachment> attachments = new ArrayList<>();
            for (AttachmentDTO attach : attachmentDTOs) {
                attachments.add(SailPointFactory.getCurrentContext().getObjectById(Attachment.class, attach.getId()));
            }
            setAttachments(attachments);
        }
        String comment = req.getComments();
        if ( Util.getString(comment) != null ) {
            setRequesterComments(comment);
        }
        
        setApprovalState(WorkItem.State.Pending);
        setProvisioningState(ProvisioningState.Pending);
    }

    /**
     * In this case the name field is not unique as
     * it holds the name of the attribute that
     * is being requested.
     */
    public boolean isNameUnique() {
        return false;
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }
    
    // need this now that IdentityEntitlements can reference
    // items
    public void visit(Visitor v) throws GeneralException {
        v.visitIdentityRequestItem(this);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Returns the application name or null for identity-level attributes.
     */
    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String s) {
        _application = s;
    }
    
    public boolean isIIQ() {
        return ( Util.nullSafeCompareTo(_application, "IIQ") == 0 ) ? true : false;
    }

    /**
     * Returns the string representation of ProvisioningPlan.AttributeRequest.Operation
     * or ProvisioningPlan.Operation
     */
    @XMLProperty
    public String getOperation() {
        return _operation;
    }

    public void setOperation(String op) {
        _operation = op;
    }

    /**
     * Returns the approval state copied from the approvalItems
     * produced by the workflow.
     * 
     * @ignore
     * WorkItem.State is more general than we need here but
     * I wanted to let it carry over in case we need it someday.
     * 
     * Use the isApproved and isRejected methods to make the
     * usual boolean decision.
     */
    @XMLProperty
    public WorkItem.State getApprovalState() {
        return _approvalState;
    }

    public void setApprovalState(WorkItem.State state) {
        _approvalState = state;
    }

    /**
     * Returns the name of the owner of the request item. This will
     * be set along with _approvalState when Approved and 
     * Rejected.
     */
    @XMLProperty
    public String getOwnerName() {
        return _ownerName;
    }

    public void setOwnerName(String owner) {
        _ownerName = owner;
    }
    
    /**
     * Returns the name of the approver of the request item. This will
     * be set along with _approvalState when Approved and Rejected.
     */
    @XMLProperty
    public String getApproverName() {
        return _approverName;
    }

    public void setApproverName(String approverName) {
        _approverName = approverName;
    }

    public boolean isProvisioningComplete() {
        return (_provisioningState == ProvisioningState.Finished);
    }
    
    public boolean isProvisioningFailed() {
        return ( _provisioningState == ProvisioningState.Failed );
    }
    
    public boolean isProvisioningCommited() {
        return ( _provisioningState == ProvisioningState.Commited );
    }
    
    public boolean isNonverifiable() {
        return ( _provisioningState == ProvisioningState.Unverifiable );
    } 
    
    /**
     * Returns the provisioning status of this item, that will be used to convey
     * if this item has been successfully applied to the Identity. 
     * The provisioning request is kicked off then the identity is periodically 
     * checked for see if the changes have been made.
     */
    @XMLProperty
    public ProvisioningState getProvisioningState() {
        return _provisioningState;
    }

    public void setProvisioningState(ProvisioningState provisioningState) {
        _provisioningState = provisioningState;
    }

    /**
     * Returns the name of the provisioning Engine that handled the request. If this was a manual
     * action it will be indicated by 'Manual".
     */
    @XMLProperty
    public String getProvisioningEngine() {
        return _provisioningEngine;
    }

    public void setProvisioningEngine(String pe) {
        _provisioningEngine = pe;
    }
    
    /**
     * Returns the compilation status. CompilationStatus is a setting that conveys if an item was added or removed as part of 
     * the plan compilation process. In many cases an role or entitlement can cause an account create, or account update. These would
     * be compiled into the project as Expansions. Likewise things removed from the plan will be marked Filtered.
     * Null indicates the item was directly requested.  
     */
    @XMLProperty
    public CompilationStatus getCompilationStatus() {
        return _compilationStatus;
    }
    
    public void setCompilationStatus(CompilationStatus compilationStatus) {
        _compilationStatus = compilationStatus;
    }

    public boolean isExpansion() {
        return ( _compilationStatus == null ) ? false : CompilationStatus.Expansion.equals(_compilationStatus);
    }

    public boolean isFiltered() {
        return ( _compilationStatus == null ) ? false : CompilationStatus.Filtered.equals(_compilationStatus);
    }
    
    /**
     * Returns the cause of the expansion. This will only be set if the compilation
     * status is expansion. Note that expansion information (such as the name
     * of the role that was expanded, etc...) will be stored in the attributes
     * map since it does not need to be searchable (yet).
     */
    @XMLProperty
    public ExpansionItem.Cause getExpansionCause() {
        return _expansionCause;
    }

    public void setExpansionCause(ExpansionItem.Cause cause) {
        _expansionCause = cause;
    }

    /**
     * Returns the number of retries performed on this item.
     */
    @XMLProperty
    public int getRetries() {
        return _retries;
    }

    public void setRetries(int i) {
        _retries = i;
    }

    public void incrementRetries() {
        _retries++;
    }

    /**
     * Returns the reference back to the IdentityRequest object.
     * This is not serialized into xml and is only here for the hibernate inverse relationship. 
     */
    public IdentityRequest getIdentityRequest() {
        return _identityRequest;
    }
    
    public void setIdentityRequest(IdentityRequest ir) {
        _identityRequest = ir;
    }

    public List<Attachment> getAttachments() { return _attachments; }

    public void setAttachments(List<Attachment> attachments) { _attachments = attachments; }
    
    //////////////////////////////////////////////////////////////////////
    //
    // XML Properties ( stored in the attributes Map and not queryable )
    //  
    //////////////////////////////////////////////////////////////////////

    public ProvisioningPlan getProvisioningPlan() {
        return (ProvisioningPlan)Util.get(_attributes, ATT_PROVISIONING_PLAN);
    }

    public void setProvisioningPlan(ProvisioningPlan plan) {
        setPseudo(ATT_PROVISIONING_PLAN, plan);
    }

    public String getAssignmentId() {
        return Util.getString(_attributes, ATT_ASSIGNMENT_ID);
    }

    public void setAssignmentId(String s) {
        setPseudo(ATT_ASSIGNMENT_ID, s);
    }

    public String getRequesterComments() {
        return Util.getString(_attributes, ATT_REQUESTER_COMMENTS);
    }

    public void setRequesterComments(String comments) {
        setPseudo(ATT_REQUESTER_COMMENTS, comments);
    }

    public String getProvisioningRequestId() {
        return Util.getString(_attributes, ATT_PROVISIONING_REQUEST_ID);
    }

    public void setProvisioningRequestId(String provisioningRequestId) {
        setPseudo(ATT_PROVISIONING_REQUEST_ID, provisioningRequestId);
    }

    public String getExpansionInfo() {
        return Util.getString(_attributes, ATT_EXPANSION_INFO);
    }

    public void setExpansionInfo(String info) {
        setPseudo(ATT_EXPANSION_INFO, info);
    }

    public String getManagedAttributeType() { return Util.getString(_attributes, ATT_MANAGED_ATTRIBUTE_TYPE); }

    public void setManagedAttributeType(ManagedAttribute.Type type) {
        setPseudo(ATT_MANAGED_ATTRIBUTE_TYPE, type.name());
    }

    //////////////////////////////////////////////////////////////////////  
    // 
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////  

    public boolean isApprovalComplete() {
        return (_approvalState != null || _approvalState != WorkItem.State.Pending);
    }

    public boolean isApproved() {
        return (_approvalState == WorkItem.State.Finished);
    }

    /**
     * Helper to indicate if the item has been rejected.
     * The WorkItem.State model will be simplified and anything
     * other than Finished will be treated as a reject.
     */
    public boolean isRejected() {
        return (_approvalState != null && _approvalState != WorkItem.State.Pending && _approvalState != WorkItem.State.Finished);
    }
    
    /**
     * Helper to indicate if the item has a provisioning state of committed.
     * @return true if the item has a provisioning state of committed, false otherwise.
     */
    public boolean isCommitted() {
        if ( Util.nullSafeEq(this._provisioningState, ProvisioningState.Commited) ) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<Message> getErrors() {
        return (List<Message>)Util.get(_attributes, ATT_ERRORS);    
    }
    
    public void addError(Message message) {
        if ( message == null ) return;
        
        List<Message> messages = getErrors();        
        if ( messages == null ) {
            messages = new ArrayList<Message>();
            setErrors(messages);
        }
        if ( !messages.contains(message) ) {
            messages.add(message);
        }
    }
    
    public void addErrors(List<Message> messages) {
        if ( Util.size(messages) > 0 ) {
            for ( Message message : messages ) {
                addError(message);
            }
        }
    }
    
    public void setErrors(List<Message> errors) {
        setPseudo(ATT_ERRORS, errors);
    }
    
    @SuppressWarnings("unchecked")
    public List<Message> getWarnings() {
        return (List<Message>)Util.get(_attributes, ATT_WARNINGS);    
    }
    
    public void setWarnings(List<Message> warnings) {
        this.setPseudo(ATT_WARNINGS, warnings);        
    }
    
    public void addWarning(Message message) {
        if ( message == null ) return;
        
        List<Message> messages = getWarnings();        
        if ( messages == null ) {
            messages = new ArrayList<Message>();
            setWarnings(messages);
        }
        if ( !messages.contains(message) ) {
            messages.add(message);
        }
    }
    
    public void addWarnings(List<Message> messages) {
        if ( Util.size(messages) > 0 ) {
            for ( Message message : messages ) {
                addWarning(message);
            }
        }
    }

  
}
