/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Date;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.SPRight;
import sailpoint.service.ProvisioningTransactionDTO;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.messages.MessageKeys;

/**
 * REST methods for the ProvisioningTransaction resource.
 *
 * @author brian.li
 */
public class ProvisioningTransactionResource extends BaseResource {
    
    private String transactionId;

    public ProvisioningTransactionResource(String transactionId, BaseResource parent) {
        super(parent);
        this.transactionId = transactionId;
    }


    @GET
    public ProvisioningTransactionDTO getTransaction()
        throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessProvisioningTransaction, SPRight.ViewProvisioningTransaction));
        ProvisioningTransaction pto = getContext().getObjectById(ProvisioningTransaction.class, transactionId);
        if (pto == null) {
            throw new ObjectNotFoundException(ProvisioningTransaction.class, transactionId);
        }
        ProvisioningTransactionDTO dto = new ProvisioningTransactionDTO(pto, this, true);
        return dto;
    }


    /**
     * Method for the UI to force a PTO to retry the associated request.
     */
    @POST
    @Path("retry")
    public void retry(@PathParam("transactionId") String transactionId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessProvisioningTransaction));

        ProvisioningTransaction pto = getContext().getObjectById(ProvisioningTransaction.class, transactionId);
        if (pto == null) {
            throw new ObjectNotFoundException(ProvisioningTransaction.class, transactionId);
        }

        ProvisioningTransactionService service = new ProvisioningTransactionService(getContext());
        service.retry(pto, new Date());
    }

    /**
     * Attempts to force manual provisioning from a failed
     * provisioning transaction object.
     *
     * @param transactionId The transaction id.
     * @throws GeneralException
     */
    @POST
    @Path("force")
    public void force(@PathParam("transactionId") String transactionId,
                      Map<String, Object> data) throws GeneralException {

        authorize(new RightAuthorizer(SPRight.FullAccessProvisioningTransaction));

        ProvisioningTransaction transaction = getContext().getObjectById(ProvisioningTransaction.class, transactionId);
        if (transaction == null) {
            throw new ObjectNotFoundException(ProvisioningTransaction.class, transactionId);
        }

        Identity workItemOwner = null;
        String ownerId = (String) data.get("ownerId");
        String comment = (String) data.get("comment");
        if ("self".equals(ownerId)) {
            workItemOwner = this.getLoggedInUser();
        } else if ("owner".equals(ownerId)) {
            Application app = getContext().getObjectByName(Application.class, transaction.getApplicationName());
            workItemOwner = app.getOwner();
        } else {
            workItemOwner = getContext().getObjectById(Identity.class, ownerId);
        }
        if (workItemOwner == null) {
            throw new GeneralException(
                Message.localize(MessageKeys.UI_PROV_TRANS_ERR_NO_OWNER)
            );
        }

        ProvisioningTransactionService service = new ProvisioningTransactionService(getContext());
        service.force(transaction, getLoggedInUser(),
                      workItemOwner, getWorkItemDescription(transaction), comment);
    }

    /**
     * Gets the localized work item description for the
     * forced manual changes work item.
     *
     * @param transaction The transaction.
     * @return The work item description.
     */
    private String getWorkItemDescription(ProvisioningTransaction transaction) {
         Message workItemDescription = Message.localize(
             MessageKeys.UI_PROV_TRANS_WORK_ITEM_DESC,
             new Object[] { transaction.getIdentityName() }
         );

        return workItemDescription.toString();
    }

}
