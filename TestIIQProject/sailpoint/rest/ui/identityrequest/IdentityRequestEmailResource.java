/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.identityrequest;

import sailpoint.authorization.identityrequest.IdentityRequestWorkItemAccessAuthorizer;
import sailpoint.object.EmailTemplate;
import sailpoint.object.WorkItem;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseEmailResource;
import sailpoint.service.EmailService;
import sailpoint.service.EmailTemplateDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * Resource for emails for identity request
 */
public class IdentityRequestEmailResource extends BaseResource {

    private String identityRequestId;

    /**
     * Implementation of EmailResourceContext for approval reminder emails
     */
    private class WorkItemReminderEmailContext extends BaseEmailResource.EmailResourceContext {
        private String workItemId;

        /**
         * Constructor
         * @param workItemId ID of the work item
         */
        public WorkItemReminderEmailContext (String workItemId) {
            this.workItemId = workItemId;
        }

        /**
         * Helper method to get the work item.
         * @param workItemId ID of the work item
         * @return WorkItem object
         * @throws GeneralException
         */
        private WorkItem getWorkItem(String workItemId) throws GeneralException {
            WorkItem workItem = getContext().getObjectById(WorkItem.class, workItemId);
            if (workItem == null) {
                throw new ObjectNotFoundException(WorkItem.class, workItemId);
            }
            return workItem;
        }

        /**
         * Implementation of getEmailTemplate to get the template for identity request work item reminders
         */
        @Override
        public EmailTemplateDTO getEmailTemplate(String comment) throws GeneralException {
            WorkItem workItem = getWorkItem(this.workItemId);
            EmailService service = new EmailService(getContext());
            EmailTemplate template = service.getIdentityRequestReminderTemplate(workItem, comment);
            EmailTemplateDTO templateDTO = new EmailTemplateDTO(template);
            templateDTO.setToIdentityWithIdentity(workItem.getOwner());
            templateDTO.setToIdentityReadOnly(true);
            return templateDTO;
        }
    }

    /**
     * Constructor
     * @param parent Parent BaseResource
     * @param identityRequestId String identity request name
     */
    public IdentityRequestEmailResource(BaseResource parent, String identityRequestId) {
        super(parent);
        this.identityRequestId = identityRequestId;
    }

    /**
     * Passthrough to the BaseEmailResource instance using WorkItemReminderEmailContext for identity
     * request reminder emails
     * @param workItemId ID of the work item
     * @return BaseEmailResource
     * @throws GeneralException
     */
    @Path("{workItemId}/reminder")
    public BaseEmailResource getWorkItemReminderEmailResource(@PathParam("workItemId") String workItemId) throws GeneralException {
        authorize(new IdentityRequestWorkItemAccessAuthorizer(workItemId, identityRequestId));
        return new BaseEmailResource(new WorkItemReminderEmailContext(workItemId), this);
    }
}
