/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.WorkItem;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A PamRequest takes a PAM request and converts it into a DTO format that may be easier to deal with in workflows,
 * email templates, and the UI.
 *
 * This also serves as the persistent data about a PAM request in the workflow.  Specifically by keeping track
 * of whether the request has been approved or not through the "approved" flag.
 */
@XMLClass
public class PamRequest implements Cloneable {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A PamAccountRequestDTO is a representation of the requests to add or remove permissions for an account.
     */
    @XMLClass
    public static class PamAccountRequest implements Cloneable {
        private String application;
        private String nativeIdentity;
        private String displayName;
        private List<String> addedRights;
        private List<String> removedRights;

        /**
         * Default constructor - required for XML framework.
         */
        public PamAccountRequest() {
        }

        /**
         * Constructor.
         *
         * @param application  The application of the account (ie - the PAM application).
         * @param nativeIdentity  The native identity of the account.
         * @param displayName  The display name of the account.
         */
        public PamAccountRequest(String application, String nativeIdentity, String displayName) {
            this.application = application;
            this.nativeIdentity = nativeIdentity;
            this.displayName = displayName;
            this.addedRights = new ArrayList<>();
            this.removedRights = new ArrayList<>();
        }

        @XMLProperty
        public String getApplication() {
            return this.application;
        }

        public void setApplication(String application) {
            this.application = application;
        }

        @XMLProperty
        public String getNativeIdentity() {
            return this.nativeIdentity;
        }

        public void setNativeIdentity(String nativeIdentity) {
            this.nativeIdentity = nativeIdentity;
        }

        @XMLProperty
        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        @XMLProperty
        public List<String> getAddedRights() {
            return this.addedRights;
        }

        public void setAddedRights(List<String> addedRights) {
            this.addedRights = addedRights;
        }

        public String getAddedRightsCSV() {
            return Util.listToCsv(this.addedRights);
        }

        @XMLProperty
        public List<String> getRemovedRights() {
            return this.removedRights;
        }

        public void setRemovedRights(List<String> removedRights) {
            this.removedRights = removedRights;
        }

        public String getRemovedRightsCSV() {
            return Util.listToCsv(this.removedRights);
        }

        /**
         * Add the rights from the given PermissionRequest to the appropriate list - addedRights or removedRights.
         */
        public void addRights(PermissionRequest permReq) throws GeneralException {
            List<String> list = null;

            if (Operation.Add.equals(permReq.getOperation())) {
                list = this.addedRights;
            }
            else if (Operation.Remove.equals(permReq.getOperation())) {
                list = this.removedRights;
            }
            else {
                throw new GeneralException("Expected Add or Remove operations: " + permReq.toXml());
            }

            if (null != permReq.getRightsList()) {
                list.addAll(permReq.getRightsList());
            }
        }

        /**
         * Return a clone of this PamAccountRequest.
         */
        @Override
        protected Object clone() {
            Object clone = null;
            try {
                clone = super.clone();
            }
            catch (CloneNotSupportedException e) {
                // Won't happen.
            }
            return clone;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private String identityName;
    private String identityDisplayName;
    private String containerName;
    private String containerDisplayName;
    private String containerOwnerName;
    private List<PamAccountRequest> accountRequests;

    /**
     * The localized description of the container.  Note that since this is localized, it is only available in contexts
     * that can provide a locale (ie - the UI), and is not stored in the XML representation of this object.
     */
    private String containerDescription;


    // The following fields mimic things in the ApprovalSet model.  At some point we should consider getting rid of
    // this and just using ApprovalSet.

    /**
     * The name of the owner of the approval work item (if one was generated).  This may be different than the
     * approver - for instance if the owner is a workgroup.  This will be updated when the approval is completed
     * to keep track of any changes in ownership.
     */
    private String approvalOwner;

    /**
     * The name of the identity that approved this request.  Non-null if approved is true and the item was not
     * auto-approved.
     */
    private String approver;

    /**
     * The state of the approval.  This is null if there is no decision, Finished if approved, and Rejected if not
     * approved.
     */
    private WorkItem.State approvalState;


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor - required for XML framework.
     */
    public PamRequest() {
    }

    /**
     * Constructor.
     *
     * @param identityName  The name of the identity.
     * @param identityDisplayName  The display name of the identity.
     * @param containerName  The name of the container.
     * @param containerDisplayName  The display name of the container.
     * @param containerOwnerName The name of the identity that owns the container.
     */
    public PamRequest(String identityName, String identityDisplayName,
                      String containerName, String containerDisplayName, String containerOwnerName) {
        this.identityName = identityName;
        this.identityDisplayName = identityDisplayName;
        this.containerName = containerName;
        this.containerDisplayName = containerDisplayName;
        this.containerOwnerName = containerOwnerName;
        this.accountRequests = new ArrayList<>();
    }

    /**
     * Return a clone of this PamRequest.
     */
    @Override
    protected Object clone() {
        Object clone = null;
        try {
            clone = super.clone();
        }
        catch (CloneNotSupportedException e) {
            // Won't happen.
        }
        return clone;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getIdentityName() {
        return this.identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    @XMLProperty
    public String getIdentityDisplayName() {
        return this.identityDisplayName;
    }

    public void setIdentityDisplayName(String identityDisplayName) {
        this.identityDisplayName = identityDisplayName;
    }

    @XMLProperty
    public String getContainerName() {
        return this.containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @XMLProperty
    public String getContainerDisplayName() {
        return this.containerDisplayName;
    }

    public void setContainerDisplayName(String containerDisplayName) {
        this.containerDisplayName = containerDisplayName;
    }

    @XMLProperty
    public String getContainerOwnerName() {
        return this.containerOwnerName;
    }

    public void setContainerOwnerName(String containerOwnerName) {
        this.containerOwnerName = containerOwnerName;
    }

    /**
     * Return the localized description of the container.  Note that since this is localized, it is only available in
     * contexts that can provide a locale (ie - the UI), and is not stored in the XML representation of this object.
     */
    public String getContainerDescription() {
        return this.containerDescription;
    }

    public void setContainerDescription(String containerDescription) {
        this.containerDescription = containerDescription;
    }

    @XMLProperty
    public List<PamAccountRequest> getAccountRequests() {
        return this.accountRequests;
    }

    public void setAccountRequests(List<PamAccountRequest> accountRequests) {
        this.accountRequests = accountRequests;
    }

    public void addAccountRequest(PamAccountRequest acct) {
        this.accountRequests.add(acct);
    }

    @XMLProperty
    public String getApprovalOwner() {
        return this.approvalOwner;
    }

    public void setApprovalOwner(String approvalOwner) {
        this.approvalOwner = approvalOwner;
    }

    @XMLProperty
    public String getApprover() {
        return this.approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    @XMLProperty
    public WorkItem.State getApprovalState() {
        return this.approvalState;
    }

    public void setApprovalState(WorkItem.State approvalState) {
        this.approvalState = approvalState;
    }

    /**
     * Return whether this request is approved or not.
     */
    public boolean isApproved() {
        // We'll assume that null or Finished are approved.
        return (null == this.approvalState) || WorkItem.State.Finished.equals(this.approvalState);
    }
}
