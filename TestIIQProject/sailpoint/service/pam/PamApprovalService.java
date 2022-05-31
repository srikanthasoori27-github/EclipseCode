/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.WorkItem;
import sailpoint.service.ApprovalDTO;
import sailpoint.service.pam.PamRequest.PamAccountRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * A service that can deal with PAM approvals to add/remove identities from containers.
 */
public class PamApprovalService {

    private static final Log LOG = LogFactory.getLog(PamApprovalService.class);

    /**
     * Name of the workflow argument that has the PAMRequestDTO
     */
    public static final String ARG_PAM_REQUEST = "pamRequest";


    private SailPointContext context;
    private UserContext userContext;


    /**
     * Constructor.
     *
     * @param context  The SailPointContext.
     * @param userContext  The UserContext.
     */
    public PamApprovalService(SailPointContext context, UserContext userContext) {
        this.context = context;
        this.userContext = userContext;
    }

    /**
     * Approve the requested PAM approval.
     *
     * @param workItemId  The ID of the work item for the PAM approval to approve.
     */
    public void approve(String workItemId) throws GeneralException {
        this.approveOrReject(workItemId, true);
    }

    /**
     * Reject the requested PAM approval.
     *
     * @param workItemId  The ID of the work item for the PAM approval to reject.
     */
    public void reject(String workItemId) throws GeneralException {
        this.approveOrReject(workItemId, false);
    }

    /**
     * Approve or reject the requested PAM approval.
     *
     * @param workItemId  The ID of the work item for the PAM approval to approve or reject.
     * @param approve  If true then the approval is approved, if false it is rejected.
     */
    private void approveOrReject(String workItemId, boolean approve) throws GeneralException {
        WorkItem workItem = this.context.getObjectById(WorkItem.class, workItemId);
        if (null == workItem) {
            throw new ObjectNotFoundException(WorkItem.class, workItemId);
        }

        PamRequest request = getPamRequest(workItem);
        request.setApprovalState((approve) ? WorkItem.State.Finished : WorkItem.State.Rejected);
        request.setApprover(this.userContext.getLoggedInUserName());

        this.context.saveObject(workItem);
        this.context.commitTransaction();
    }

    /**
     * Return the PamRequest from the given WorkItem.
     *
     * @param workItem  The WorkItem for the PAM approval.
     *
     * @return The PamRequest.
     *
     * @throws GeneralException  If the given work item does not contain a PamRequest.
     */
    public static PamRequest getPamRequest(WorkItem workItem) throws GeneralException {
        PamRequest requestDTO = (PamRequest) workItem.get(ARG_PAM_REQUEST);
        if (null == requestDTO) {
            throw new GeneralException("Malformed PAM approval work item - missing the pamRequest");
        }
        return requestDTO;
    }

    /**
     * Returns the PamRequest for a given projection query result or the attributes map from the workItem
     * 
     * @param row BaseListService projection query result
     * @return PamRequest extracted from the Attributes
     * @throws GeneralException If the given parameters does not contain a PamRequest.
     */
    public PamRequest getPamRequest(Map<String,Object> row) throws GeneralException {
        PamRequest requestDTO = null;
        Attributes<?, ?> attrs = null;
        if (null != row && null != row.get(ApprovalDTO.ATTRIBUTES_CALC_ATTR)) {
            Object o = row.get(ApprovalDTO.ATTRIBUTES_CALC_ATTR);
            attrs = Attributes.castAttributes(o);
            requestDTO = (PamRequest) attrs.get(ARG_PAM_REQUEST);
        }
        else {
            // check for the attributes from the workItem object
            if (null != row && null != (String) row.get("id")) {
                WorkItem workItem = this.context.getObjectById(WorkItem.class, (String)row.get("id"));
                if (null != workItem && null != workItem.getAttributes()) {
                    attrs = workItem.getAttributes();
                    requestDTO = (PamRequest) attrs.get(ARG_PAM_REQUEST);
                }
            }
        }
        if (attrs != null && null == requestDTO) {
            throw new GeneralException("Malformed PAM approval work item - missing the pamRequest");
        }
        return requestDTO;
    }

    /**
     * Create a PamApprovalDTO for the given work item.
     *
     * @param workItem  The WorkItem with the PAM approval.
     *
     * @return A PamApprovalDTO for the given work item.
     *
     * @throws GeneralException  If the given work item does not have the PAMRequestDTO.
     */
    public PamApprovalDTO createPamApprovalDTO(WorkItem workItem) throws GeneralException {
        PamApprovalDTO approvalDTO = new PamApprovalDTO(this.userContext, workItem);

        PamRequest request = getPamRequest(workItem);
        amendContainerDescription(approvalDTO, request);

        return approvalDTO;
    }

    /**
     * Create a PamApprovalDTO for the given row and column configuration.
     * 
     * @param row BaseListService row that has all the data to create a PAM request
     * @param cols ColumnConfig to create the PamApprovalDTO
     * @return PamApprovalDTO representing the approval
     * @throws GeneralException
     */
    public PamApprovalDTO createPamApprovalDTO(Map<String,Object> row, List<ColumnConfig> cols) throws GeneralException {
        PamApprovalDTO approvalDTO = new PamApprovalDTO(this.userContext, row, cols);

        PamRequest request = getPamRequest(row);
        amendContainerDescription(approvalDTO, request);

        return approvalDTO;
    }

    /* Utility method for cloning the PamRequest and setting the localized container description */
    private void amendContainerDescription(PamApprovalDTO approvalDTO, PamRequest request) throws GeneralException {
        // Get the request and clone it since we'll be setting the description.
        request = (PamRequest) request.clone();
        approvalDTO.setRequest(request);

        // The description is locale-specific, so it is not set on the PamRequest in the approval work item.
        // Load it here and set it.
        setContainerDescription(request);
    }

    /**
     * Calculate the container description (from the container ManagedAttribute) and set it on the given PamRequest.
     *
     * @param request  The PamRequest to set the container description on.
     */
    private void setContainerDescription(PamRequest request) throws GeneralException {
        if (!Util.isEmpty(request.getAccountRequests())) {
            // There should just be one account request, grab the app from the first.
            PamAccountRequest acctReq = request.getAccountRequests().get(0);
            String appName = acctReq.getApplication();
            String nativeIdentity = getContainerNativeIdentity(request.getContainerName(), appName);

            Application app = this.context.getObjectByName(Application.class, appName);

            // Look up the ManagedAttribute for the container.
            ManagedAttribute ma =
                ManagedAttributer.get(this.context, app.getId(), false, null, nativeIdentity, ContainerService.OBJECT_TYPE_CONTAINER);
            if (null != ma) {
                String description = ma.getDescription(this.userContext.getLocale());
                request.setContainerDescription(description);
            }
        }
    }

    /**
     * Return the nativeIdentity of the container object with the given name on the given app.
     *
     * @param containerName  The name of the container.
     * @param appName  The name of the application the container was found on.
     *
     * @return The nativeIdentity of the container object with the given name on the given app, or null if it cannot
     *     be found.
     */
    private String getContainerNativeIdentity(String containerName, String appName) throws GeneralException {
        String nativeIdentity = null;

        // We have to do a bit of jiggery-pokery to get the native identity of the container, since we just have the name.
        // Find the target on the application that has this containerName as it's name, and return the nativeObjectId.
        Application app = this.context.getObjectByName(Application.class, appName);
        if (null != app) {
            List<String> targetSourceIds = new ArrayList<>();
            for (TargetSource ts : app.getTargetSources()) {
                targetSourceIds.add(ts.getId());
            }

            if (!targetSourceIds.isEmpty()) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("name", containerName));
                qo.add(Filter.in("targetSource.id", targetSourceIds));
                List<Target> targets = this.context.getObjects(Target.class, qo);
                if (!targets.isEmpty()) {
                    if (targets.size() > 1) {
                        LOG.warn("Found multiple targets for container '" + containerName + "' on app '" + appName + "'");
                    }

                    nativeIdentity = targets.get(0).getNativeObjectId();
                }
            }
        }

        return nativeIdentity;
    }

    /**
     * Return whether the given work item is for a PAM approval.
     *
     * @param workItem  The work item to check.
     *
     * @return True if the given work item is a PAM approval, false otherwise.
     */
    public static boolean isPamApproval(WorkItem workItem) {
        return WorkItem.Type.Approval.equals(workItem.getType()) && (null != workItem.get(ARG_PAM_REQUEST));
    }

    /**
     * Assumption is the row parameter has already been filtered to contain only WorkItems
     * of type Approval.
     * @param row Row result from a BaseListService class
     * @return true if an Attributes column contains a PamRequest object
     */
    public boolean isPamApproval(Map<String, Object> row) {
        boolean result = false;
        try {
            result = null != getPamRequest(row);
        } catch (Exception e) { /* ignore, must not be PAM */ }
        return result;
    }

}
