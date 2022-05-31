/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.ProvisioningTransaction.Level;
import sailpoint.object.ProvisioningTransaction.Status;
import sailpoint.object.ProvisioningTransaction.Type;
import sailpoint.object.Request;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.WorkItem;
import sailpoint.persistence.Sequencer;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityLibrary;
import sailpoint.workflow.IdentityRequestLibrary;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A service to handle creating and updating ProvisioningTransaction objects.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class ProvisioningTransactionService {

    /**
     * The log.
     */
    private static final Log log = LogFactory.getLog(ProvisioningTransactionService.class);

    /**
     * Constant used to retrieve the id of an existing provisioning transaction object
     * out of an attributes map.
     */
    public static final String ATT_PROV_TRANSACTION_ID = "provisioningTransactionId";

    /**
     * Integration name used when logging a transaction for a request that was
     * completely filtered out.
     */
    public static final String FILTERED_INTEGRATION = "Filtered";

    /**
     * Constant used to retrieve the project ouf the the Request object attributes.
     */
    private static final String ATT_PROJECT = "project";

    /**
     * Helper class used to pass the details of the
     * transaction for transaction logging.
     */
    public static class TransactionDetails {

        /**
         * The provisioning project. In most cases the identity
         * name and log level override value will be pulled from,
         * but in some cases we will not have a project at all.
         */
        private ProvisioningProject project;

        /**
         * The identity name. In some cases we will not have a
         * project so allow the identity name to be directly
         * specified. This value is checked first before looking
         * at the project for an identity name.
         */
        private String identityName;

        /**
         * The source of the transaction.
         */
        private String source;

        /**
         * The partitioned plan.
         */
        private ProvisioningPlan partitionedPlan;

        /**
         * The account or object request.
         */
        private AbstractRequest request;

        /**
         * The retry Request if one exists.
         */
        private Request retryRequest;

        /**
         * Flag that specifies a manual transaction.
         */
        private boolean manual;

        /**
         * The name of the manual work item opened when a
         * transaction is forced.
         */
        private String manualWorkItemName;

        /**
         * Gets the provisioning project.
         *
         * @return The provisioning project.
         */
        public ProvisioningProject getProject() {
            return project;
        }

        /**
         * Sets the provisioning project.
         *
         * @param project The provisioning project.
         */
        public void setProject(ProvisioningProject project) {
            this.project = project;
        }

        /**
         * Gets the identity name.
         *
         * @return The identity name.
         */
        public String getIdentityName() {
            return identityName;
        }

        /**
         * Sets the identity name.
         *
         * @param identityName The master plan.
         */
        public void setIdentityName(String identityName) {
            this.identityName = identityName;
        }

        /**
         * Gets the source of the transaction.
         *
         * @return The source.
         */
        public String getSource() {
            return source;
        }

        /**
         * Sets the source of the transaction.
         *
         * @param source The source.
         */
        public void setSource(String source) {
            this.source = source;
        }

        /**
         * Gets the partitioned plan.
         *
         * @return The partitioned plan.
         */
        public ProvisioningPlan getPartitionedPlan() {
            return partitionedPlan;
        }

        /**
         * Sets the partitioned plan.
         *
         * @param partitionedPlan The partitioned plan.
         */
        public void setPartitionedPlan(ProvisioningPlan partitionedPlan) {
            this.partitionedPlan = partitionedPlan;
        }

        /**
         * Gets the account or object request.
         *
         * @return The account or object request.
         */
        public AbstractRequest getRequest() {
            return request;
        }

        /**
         * Sets the account or object request.
         *
         * @param request The account or object request.
         */
        public void setRequest(AbstractRequest request) {
            this.request = request;
        }

        /**
         * Gets the associated retry request.
         *
         * @return The retry request.
         */
        public Request getRetryRequest() {
            return retryRequest;
        }

        /**
         * Sets the associated retry request.
         *
         * @param retryRequest The retry request.
         */
        public void setRetryRequest(Request retryRequest) {
            this.retryRequest = retryRequest;
        }

        /**
         * Gets the flag that determines if this is a manual transaction.
         *
         * @return True if manual transaction, false otherwise.
         */
        public boolean isManual() {
            return manual;
        }

        /**
         * Sets the flag that determines if this is a manual transaction.
         *
         * @param manual True if manual, false otherwise.
         */
        public void setManual(boolean manual) {
            this.manual = manual;
        }

        /**
         * Gets the name of the manual work item created when
         * a transaction was forced.
         *
         * @return The name.
         */
        public String getManualWorkItemName() {
            return this.manualWorkItemName;
        }

        /**
         * Sets the name of the manual work item that was created
         * when the transaction was forced.
         *
         * @param manualWorkItemName The name.
         */
        public void setManualWorkItemName(String manualWorkItemName) {
            this.manualWorkItemName = manualWorkItemName;
        }

        /**
         * Determines if the retry request exists.
         *
         * @return True if exists, false otherwise.
         */
        public boolean hasRetryRequest() {
            return retryRequest != null;
        }

        /**
         * Determines if the partitioned play contains a provisioning result.
         *
         * @return True if has a result, false otherwise.
         */
        public boolean hasPartitionedPlanResult() {
            return getPartitionedPlanResult() != null;
        }

        /**
         * Gets the provisioning result in the partitioned plan.
         *
         * @return The result.
         */
        public ProvisioningResult getPartitionedPlanResult() {
            return partitionedPlan == null ? null : partitionedPlan.getResult();
        }

    }

    /**
     * The SailPoint context.
     */
    private SailPointContext context;

    /**
     * Flag to determine whether or not the service should commit the
     * transaction. Default to true.
     */
    private boolean commit = true;

    /**
     * Constructs a new instance of the ProvisioningTransactionService.
     *
     * @param context The context.
     */
    public ProvisioningTransactionService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Calculates the provisioning result of the attribute
     * or permission request.
     *
     * @param planResult The plan result.
     * @param abstractResult The account/object request result.
     * @param genericResult The attribute/permission request result.
     * @param defaultResult The default result.
     * @param isTimedOut True if the transaction object was timed out.
     * @return The result.
     */
    public static String calculateGenericRequestResult(ProvisioningResult planResult,
                                                       ProvisioningResult abstractResult,
                                                       ProvisioningResult genericResult,
                                                       String defaultResult,
                                                       boolean isTimedOut) {
        String result;

        if (isValidResult(genericResult)) {
            if (isRetry(genericResult) && isTimedOut) {
                result = ProvisioningResult.STATUS_FAILED;
            } else {
                result = genericResult.getStatus();
            }
        } else {
            result = calculateAbstractRequestResult(planResult, abstractResult,
                                                    defaultResult, isTimedOut);
        }

        return result;
    }

    /**
     * Calculates the provisioning result of the account
     * or object request.
     *
     * @param planResult The plan result.
     * @param abstractResult The account/object request result.
     * @param defaultResult The default result.
     * @param isTimedOut True if the transaction object was timed out.
     * @return The result.
     */
    public static String calculateAbstractRequestResult(ProvisioningResult planResult,
                                                        ProvisioningResult abstractResult,
                                                        String defaultResult,
                                                        boolean isTimedOut) {

        String result = defaultResult;

        if (isValidResult(abstractResult)) {
            if (isRetry(abstractResult) && isTimedOut) {
                result = ProvisioningResult.STATUS_FAILED;
            } else {
                result = abstractResult.getStatus();
            }
        } else if (isValidResult(planResult)) {
            if (isRetry(planResult) && isTimedOut) {
                result = ProvisioningResult.STATUS_FAILED;
            } else {
                result = planResult.getStatus();
            }
        }

        return result;
    }

    /**
     * Set the flag that will determine whether or not the transaction
     * is committed by the service.
     *
     * @param commit True to commit transaction, false to let the calling code handle committing.
     */
    public void setCommit(boolean commit) {
        this.commit = commit;
    }

    /**
     * Creates or updates a ProvisioningTransaction object based on the specified
     * transaction details.
     *
     * @param details The transaction details.
     * @return The ProvisioningTransaction object.
     * @throws GeneralException
     */
    public ProvisioningTransaction logTransaction(TransactionDetails details) throws GeneralException {
        ProvisioningTransaction transaction = null;

        if (isUpdate(details.getRequest())) {
            transaction = updateTransaction(details);
        } else {
            // status for manual and local IIQ application targeted requests is always success
            Status status = isManualOrLocal(details) ?
                Status.Success : calculateStatus(details.getPartitionedPlanResult(), details.getRequest());

            // determine if a failed status should be forced
            if (isForceFailure(details.getSource(), status)) {
                status = Status.Failed;
            }

            if (isTransactionLoggable(status, details.getProject())) {
                transaction = createTransaction(details, status, false);
            }
        }

        return transaction;
    }

    /**
     * Logs a transaction for a request that was completely filtered and did
     * not go through the normal provisioning process.
     *
     * @param project The project.
     * @param request The request.
     * @return The transaction.
     * @throws GeneralException
     */
    public ProvisioningTransaction logTransactionForFilteredRequest(ProvisioningProject project, AbstractRequest request)
        throws GeneralException {

        // create details and then follow normal path
        AbstractRequest clone = request.cloneRequest();
        clone.setArguments(null);
        clone.setAttributeRequests(null);
        clone.setPermissionRequests(null);

        // be explicit about the result
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        clone.setResult(result);

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setTargetIntegration(FILTERED_INTEGRATION);

        TransactionDetails details = new TransactionDetails();
        details.setProject(project);
        details.setRequest(clone);
        details.setPartitionedPlan(plan);

        return logTransaction(details);
    }

    /**
     * Fails the provisioning transaction referenced in the request.
     *
     * @param request The account or object request.
     * @return The transaction or null if not found.
     * @throws GeneralException
     */
    public ProvisioningTransaction failTransaction(AbstractRequest request) throws GeneralException {
        ProvisioningTransaction provisioningTransaction = null;

        String provTransId = (String) request.get(ATT_PROV_TRANSACTION_ID);
        if (!Util.isNullOrEmpty(provTransId)) {
            provisioningTransaction = context.getObjectById(ProvisioningTransaction.class, provTransId);

            if (provisioningTransaction != null && !Status.Success.equals(provisioningTransaction.getStatus())) {
                provisioningTransaction.setStatus(Status.Failed);

                context.saveObject(provisioningTransaction);
                if (commit) {
                    context.commitTransaction();
                }
            }
        }

        return provisioningTransaction;
    }

    /**
     * Marks provisioning transaction as failed that are contained in the account/object request.
     * This method is not concerned with updating results within the transaction as that should
     * have been done when the plan went through the provisioner.
     *
     * @param details The transaction details.
     * @throws GeneralException
     */
    public ProvisioningTransaction timeOutTransaction(TransactionDetails details) throws GeneralException {
        ProvisioningTransaction transaction = null;
        AbstractRequest request = details.getRequest();

        // check if there is an existing transaction, if so then mark it as failed, otherwise
        // if logging is enabled create a new transaction with a failed status
        String transactionId = request.getString(ATT_PROV_TRANSACTION_ID);
        if (!Util.isNullOrEmpty(transactionId)) {
            transaction = context.getObjectById(ProvisioningTransaction.class, transactionId);
            if (transaction != null && !Status.Success.equals(transaction.getStatus())) {
                // we have a transaction so mark it as timed out and failed
                markTimedOut(transaction);
                transaction.setStatus(Status.Failed);

                context.saveObject(transaction);

                if (commit) {
                    context.commitTransaction();
                }

                return transaction;
            }
        } else if (isLoggingEnabled()) {
            // we only need to check if logging is enabled because if it is then failures
            // are always logged no matter what the level is configured as
            transaction = createTransaction(details, Status.Failed, true);
        }

        return transaction;
    }

    /**
     * Sets the passed in pto to retry its request immediately.
     *
     * @param pto The id of the pto to force it's request to retry.
     * @param launch The launch date for the request based retry.
     * @throws GeneralException
     */
    public void retry(ProvisioningTransaction pto, Date launch) throws GeneralException {
        String requestId = pto.getAttributes().getString(ProvisioningTransaction.ATT_RETRY_REQUEST_ID);
        String workItemId = (String) pto.getAttributes().remove(ProvisioningTransaction.ATT_WAIT_WORK_ITEM_ID);

        if (!Util.isNullOrEmpty(requestId)) {
            Request request = context.getObjectById(Request.class, requestId);
            if (null != request) {
                request.setNextLaunch(launch);

                context.saveObject(request);
                context.commitTransaction();

                return;
            }
        } else if (!Util.isNullOrEmpty(workItemId)) {
            if (processWaitWorkItem(workItemId)) {
                return;
            }
        }

        throw new GeneralException("Provisioning Transaction could not be retried");
    }

    /**
     * Forces provisioning of the transaction by opening a manual work item
     * for the specified identity. The transaction should be
     * in a failed state and contain an AccountRequest.
     *
     * @param transaction The transaction.
     * @param requester The requester.
     * @param workItemOwner The work item owner.
     * @param workItemDescription The work item description.
     * @throws GeneralException
     */
    public void force(ProvisioningTransaction transaction, Identity requester,
                      Identity workItemOwner, String workItemDescription, String comment)
        throws GeneralException {

        if (!Status.Failed.equals(transaction.getStatus())) {
            throw new GeneralException("Only failed transactions can be forced.");
        }

        AbstractRequest request = getRequestFromTransaction(transaction);
        if (request instanceof ObjectRequest) {
            throw new GeneralException("Unable to force object request.");
        }

        WorkItem workItem = openForceManualWorkItem(transaction, requester, workItemOwner, workItemDescription, comment);

        createForcedManualTransaction(transaction, workItem);

        // set the old transaction to forced
        transaction.setForced(true);
        transaction.getAttributes().put(ProvisioningTransaction.ATT_MANUAL_WORK_ITEM, workItem.getName());
        context.saveObject(transaction);

        if (commit) {
            context.commitTransaction();
        }
    }

    /**
     * Updates any provisioning transactions found in the retry project with the id
     * of the wait work item.
     *
     * @param project The retry project.
     * @param workItem The wait work item.
     * @throws GeneralException
     */
    public void updateTransactionsWithWaitWorkItem(ProvisioningProject project, WorkItem workItem)
        throws GeneralException {

        boolean needsCommit = false;

        for (ProvisioningPlan plan : Util.iterate(project.getIntegrationPlans())) {
            for (AbstractRequest request : Util.iterate(plan.getAllRequests())) {
                String transactionId = request.getString(ProvisioningTransactionService.ATT_PROV_TRANSACTION_ID);
                if (!Util.isNullOrEmpty(transactionId)) {
                    ProvisioningTransaction transaction = context.getObjectById(ProvisioningTransaction.class, transactionId);
                    if (transaction != null) {
                        transaction.getAttributes().put(
                            ProvisioningTransaction.ATT_WAIT_WORK_ITEM_ID, workItem.getId()
                        );

                        context.saveObject(transaction);
                        needsCommit = true;
                    }
                }
            }
        }

        if (needsCommit && commit) {
            context.commitTransaction();
        }
    }

    /**
     * Processes the wait work item for a workflow causing a retry of
     * the provisioning.
     *
     * @param workItemId The wait work item id.
     * @return True if successfully processed, false otherwise.
     * @throws GeneralException
     */
    private boolean processWaitWorkItem(String workItemId) throws GeneralException {
        boolean processed = false;

        WorkItem workItem = null;
        try {
            Map<String, Object> lockOptions = new HashMap<String, Object>();
            lockOptions.put(SailPointContext.LOCK_TYPE, PersistenceManager.LOCK_TYPE_PERSISTENT);

            workItem = context.lockObjectById(WorkItem.class, workItemId, lockOptions);
            if (workItem != null) {
                Workflower workflower = new Workflower(context);
                workflower.processEvent(workItem, true);

                processed = true;
            }
        } catch (ObjectAlreadyLockedException e) {
            log.debug("Unable to obtain lock for wait work item " + workItemId + ".");
        } finally {
            if (workItem != null) {
                try {
                    context.decache();
                    workItem = context.getObjectById(WorkItem.class, workItemId);
                    if (workItem != null) {
                        ObjectUtil.unlockObject(context, workItem, PersistenceManager.LOCK_TYPE_PERSISTENT);
                    }
                } catch (Exception e) {
                    log.error("An error occurred while trying to unlock the wait work item.", e);
                }
            }
        }

        return processed;
    }

    /**
     * Creates a new ProvisioningTransaction object given the transaction details. This
     * method will return null if the configured logging level is such that no transaction
     * should be created.
     *
     * @param details The transaction details.
     * @param status The status of the transaction.
     * @param isTimeOut True if this transaction is being created because of a time out.
     * @return The newly created ProvisioningTransaction object or null.
     * @throws GeneralException
     */
    private ProvisioningTransaction createTransaction(TransactionDetails details, Status status, boolean isTimeOut)
        throws GeneralException {

        ProvisioningTransaction transaction = new ProvisioningTransaction();
        Sequencer sequencer = new Sequencer();
        transaction.setName(sequencer.generateId(context, transaction));
        transaction.setStatus(status);

        String source = details.getSource();
        if (Util.isNullOrEmpty(source) && details.getProject() != null) {
            source = details.getProject().getString(ProvisioningPlan.ARG_SOURCE);
            if (Util.isNullOrEmpty(source) && details.getProject().getMasterPlan() != null) {
                source = details.getProject().getMasterPlan().getSource();
            }
        }

        transaction.setSource(source);

        // in the sunrise case op can be null on the IIQ plan so default
        // to modify which seems reasonable
        ProvisioningPlan.ObjectOperation op = details.getRequest().getOp();
        if (op == null) {
            op = ProvisioningPlan.ObjectOperation.Modify;
        }

        transaction.setOperation(op.toString());

        // if request is targeted at IIQ then use the branded application name
        String applicationName = isLocalRequest(details.getRequest()) ?
            getBrandedApplicationName() : details.getRequest().getApplicationName();
        transaction.setApplicationName(applicationName);

        // calculate the correct integration name for the transaction
        String integration = getIntegrationName(details);
        transaction.setIntegration(integration);

        // in most cases we will have a project that we can get the identity name
        // from but in the case of certification and policy violation revokes
        // we do not have a plan so the identity name is specified directly
        String identityName = details.getIdentityName();
        if (Util.isNullOrEmpty(identityName) && details.getProject() != null) {
            identityName = details.getProject().getIdentity();
        }

        transaction.setIdentityName(identityName);

        // grab displayable name
        Identity identity = context.getObjectByName(Identity.class, identityName);
        if (identity != null) {
            transaction.setIdentityDisplayName(identity.getDisplayableName());
        } else {
            // fallback to what name passed in
            transaction.setIdentityDisplayName(identityName);
        }

        // in requests for IIQ native identity is not set so use identity name
        // which is the native identity in this case
        String nativeId = isLocalRequest(details.getRequest()) ?
            identityName : details.getRequest().getNativeIdentity();

        transaction.setNativeIdentity(nativeId);
        // set the account display name to fallback to native identity or the identity display if it is a local request
        String accountDisplayName = isLocalRequest(details.getRequest()) ?
            transaction.getIdentityDisplayName() : nativeId;

        if (!isLocalRequest(details.getRequest()) && nativeId != null) {
            Application app = details.getRequest().getApplication(context);
            // grab the displayable name from a Link object if the identity is not null
            if (identity != null) {
                if (!ProvisioningPlan.ObjectOperation.Create.equals(details.getRequest().getOp())) {
                    IdentityService is = new IdentityService(context);
                    Link link = is.getLink(identity, app, details.getRequest().getInstance(), nativeId);
                    if (link != null) {
                        accountDisplayName = link.getDisplayableName();
                    }
                }
                // In the case of a create, a link does not exist
                // try and grab the displayable name using the display attribute on the account schema
                else if (app != null) {
                    String displayName = getDisplayNameFromRequest(app.getAccountSchema(), details.getRequest());
                    if (Util.isNotNullOrEmpty(displayName)) {
                        accountDisplayName = displayName;
                    }
                }
            }
            // if identity is null, try and grab it using "sysDisplayName" and then the display attribute on the schema
            else {
                String displayName = getValueFromAbstractRequest(details.getRequest(), ManagedAttribute.PROV_DISPLAY_NAME);
                if (Util.isNotNullOrEmpty(displayName)) {
                    accountDisplayName = displayName;
                }
                else if (app != null) {
                    // attempt to look for the display name using the displayAttribute defined in the type's schema
                    String type = getValueFromAbstractRequest(details.getRequest(), ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE);
                    displayName = getDisplayNameFromRequest(app.getSchema(type), details.getRequest());
                    if (Util.isNotNullOrEmpty(displayName)) {
                        accountDisplayName = displayName;
                    }
                }
            }
        }
        transaction.setAccountDisplayName(accountDisplayName);

        Type type = details.isManual() ? Type.Manual : Type.Auto;
        transaction.setType(type);

        // make sure the transaction has a valid attributes map
        ensureAttributes(transaction);

        // cert id is set in the sourceId arg of the master plan during the remediation process and
        // cert name is set in the sourceName arg
        if (isCertificationTransaction(transaction)) {
            // sometimes we will not have access to a project so default to partitioned plan,
            // but if there is a project use the master plan
            ProvisioningPlan planWithCertInfo = details.getPartitionedPlan();
            if (details.getProject() != null && details.getProject().getMasterPlan() != null) {
                planWithCertInfo = details.getProject().getMasterPlan();
            }

            if (planWithCertInfo != null) {
                String certId = (String) planWithCertInfo.get(ProvisioningPlan.ARG_SOURCE_ID);
                if (!Util.isNullOrEmpty(certId)) {
                    transaction.setCertificationId(certId);
                }

                String certName = (String) planWithCertInfo.get(ProvisioningPlan.ARG_SOURCE_NAME);
                if (!Util.isNullOrEmpty(certName)) {
                    transaction.getAttributes().put(ProvisioningTransaction.ATT_CERT_NAME, certName);
                }
            }
        }

        // clone the abstract request so that the original does not get modified
        //IIQETN-6539 :- Cloning provisioning result as long as there are errors.
        AbstractRequest clonedRequest = details.getRequest().cloneRequest(true);
        // here we need to modify the abstract request due to the arguments map in the attribute request
        // containing a currentPassword value in plain text
        scrubPasswords(clonedRequest);

        // set the account or object request and if it exists the plan result, the plan could have
        // multiple account requests for the same app but all we really need from the plan is
        // the result so just keep that instead of the entire plan. everything else we need
        // is in the account or object request
        transaction.getAttributes().put(ProvisioningTransaction.ATT_REQUEST, clonedRequest);

        // check for a ticket id
        setTicketId(transaction, details);

        setAccessRequestId(transaction, details);

        if (isTimeOut) {
            markTimedOut(transaction);
        }

        if (details.hasPartitionedPlanResult()) {
            transaction.getAttributes().put(
                ProvisioningTransaction.ATT_PLAN_RESULT,
                details.getPartitionedPlanResult()
            );
        }

        // set any filtered items
        if (details.getProject() != null) {
            AbstractRequest filtered;

            // have to handle things a little bit differently for a local request because
            // the native identity is not always set so we have to do the comparison manually
            // instead of using the general one
            if (isLocalRequest(details.getRequest())) {
                filtered = findLocalFilteredRequest(details.getProject(), details.getRequest());
            } else {
                filtered = details.getProject().getFiltered(details.getRequest());
            }

            if (filtered != null) {
                transaction.getAttributes().put(
                    ProvisioningTransaction.ATT_FILTERED,
                    filtered.cloneRequest()
                );

                filtered.put(ProvisioningProject.ATT_FILTER_LOGGED, "true");
            }
        }

        // hang request id on transaction if specified
        if (details.hasRetryRequest()) {
            transaction.getAttributes().put(
                ProvisioningTransaction.ATT_RETRY_REQUEST_ID,
                details.getRetryRequest().getId()
            );
        }

        // set the manual work item name if specified
        if (!Util.isNullOrEmpty(details.getManualWorkItemName())) {
            transaction.getAttributes().put(
                ProvisioningTransaction.ATT_MANUAL_WORK_ITEM,
                details.getManualWorkItemName()
            );
        }

        context.saveObject(transaction);

        // put the trans id in the account request in the partitioned plan, we will need this
        // later in the workflow retry case
        details.getRequest().put(ATT_PROV_TRANSACTION_ID, transaction.getId());
        clonedRequest.put(ATT_PROV_TRANSACTION_ID, transaction.getId());

        // update request with transaction id
        Request retryRequest = details.getRetryRequest();
        if (retryRequest != null) {
            // the retry project should contain one integration plan
            ProvisioningProject project = (ProvisioningProject) retryRequest.get(ATT_PROJECT);
            if (project != null && !Util.isEmpty(project.getIntegrationPlans())) {
                ProvisioningPlan retryPlan = project.getIntegrationPlans().get(0);

                // loop through the requests and put the transaction id in the matching one
                for (AbstractRequest ar : Util.iterate(retryPlan.getAllRequests())) {
                    if (isMatchingRequest(details.getRequest(), ar)) {
                        ar.put(ATT_PROV_TRANSACTION_ID, transaction.getId());
                    }
                }

                context.saveObject(retryRequest);
            }
        }

        if (commit) {
            context.commitTransaction();
        }

        return transaction;
    }

    /**
     * Helper method to try and grab the display name using the schema defined display attribute
     * @param schema Application schema of the type
     * @param request The AbstractRequest contained in the TransactionDetails
     * @return A String of the display name or null if there was none
     */
    private String getDisplayNameFromRequest(Schema schema, AbstractRequest request) {
        String displayAttribute = null;
        if (schema != null && schema.getDisplayAttribute() != null) {
            displayAttribute = schema.getDisplayAttribute();
        }
        if (Util.isNotNullOrEmpty(displayAttribute)) {
            // attempt to look for the display name using the displayAttribute in the attribute requests
            String displayName = getValueFromAbstractRequest(request, displayAttribute);
            if (Util.isNotNullOrEmpty(displayName)) {
                return displayName;
            } 
        }
        return null;
    }

    /**
     * Helper method to try and grab the value from the passed in attributeRequest name
     * of attribute requests from the Abstract Request
     * @param request The AbstractRequest that was contained in the TransactionDetails
     * @param attributeRequest Name of the attribute to look for in the attribute requests 
     * @return A String of the value of the attribute request or null if there was none.
     */
    private String getValueFromAbstractRequest(AbstractRequest request, String attributeRequest) {
        AttributeRequest ar = request.getAttributeRequest(attributeRequest);
        if (ar != null && ar.getValue() != null) {
            return (String)ar.getValue();
        }

        return null;
    }

    /**
     * Updates a ProvisioningTransaction object that is specified in the account or object
     * request contained in the transaction details. This method will updated the transaction
     * object whose id will be contained in the account/object request based on the retry
     * transaction details.
     *
     * @param details The transaction details.
     * @return The updated ProvisioningTransaction object or null if one could not be found.
     * @throws GeneralException
     */
    private ProvisioningTransaction updateTransaction(TransactionDetails details) throws GeneralException {
        String transactionId = details.getRequest().getString(ATT_PROV_TRANSACTION_ID);

        ProvisioningTransaction transaction = context.getObjectById(ProvisioningTransaction.class, transactionId);
        if (transaction == null) {
            log.warn("Unable to find specified provisioning transaction object to update");

            return null;
        }

        // safeguard against updating a successful transaction
        if (Status.Success.equals(transaction.getStatus())) {
            return transaction;
        }

        // increment retries
        incrementRetries(transaction);

        // if a ticket id already exists assume it will be used through the life of this transaction,
        // may need to revisit this at some point
        if (!transaction.getAttributes().containsKey(ProvisioningTransaction.ATT_TICKET_ID)) {
            setTicketId(transaction, details);
        }

        // always remove the wait work item id when we update, if the transaction goes into another
        // retry cycle then it will be updated later when that work item is created
        transaction.getAttributes().remove(ProvisioningTransaction.ATT_WAIT_WORK_ITEM_ID);

        // update results at attribute/permission request, account/object request and plan levels
        AbstractRequest transRequest = (AbstractRequest) transaction.getAttributes().get(ProvisioningTransaction.ATT_REQUEST);
        if (transRequest != null) {
            AbstractRequest newRequest = details.getRequest();

            // update the status of the acct or object request
            transRequest.setResult(newRequest.getResult());

            for (AttributeRequest attrRequest : Util.iterate(transRequest.getAttributeRequests())) {
                for (AttributeRequest retryAttrRequest : Util.iterate(details.getRequest().getAttributeRequests())) {
                    if (attrRequest.equals(retryAttrRequest)) {
                        attrRequest.setResult(retryAttrRequest.getResult());
                    }
                }
            }

            for (PermissionRequest permRequest : Util.iterate(transRequest.getPermissionRequests())) {
                for (PermissionRequest retryPermRequest : Util.iterate(details.getRequest().getPermissionRequests())) {
                    if (permRequest.equals(retryPermRequest)) {
                        permRequest.setResult(retryPermRequest.getResult());
                    }
                }
            }

            transaction.getAttributes().put(ProvisioningTransaction.ATT_REQUEST, transRequest);

            // update the plan result
            if (details.hasPartitionedPlanResult()) {
                transaction.getAttributes().put(
                    ProvisioningTransaction.ATT_PLAN_RESULT,
                    details.getPartitionedPlanResult()
                );
            }

            // recalculate the status
            transaction.setStatus(calculateStatus(details.getPartitionedPlanResult(), transRequest));
        }

        context.saveObject(transaction);

        if (commit) {
            context.commitTransaction();
        }

        return transaction;
    }

    /**
     * Gets the correct integration name for the transaction.
     *
     * @param details The transaction details.
     * @return The integration name.
     */
    private String getIntegrationName(TransactionDetails details) {
        // first check if the transaction is manual and if so use work item as
        // the integration, next check to see if this plan is targeted at the
        // local IIQ app, if so use the application name from the branding service
        // otherwise use the target integration in the plan
        String integration;
        if (details.isManual()) {
            integration = ProvisioningTransaction.MANUAL_INTEGRATION;
        } else if (isLocalRequest(details.getRequest())) {
            integration = getBrandedApplicationName();
        } else {
            integration = details.getPartitionedPlan().getTargetIntegration();
        }

        return integration;
    }

    /**
     * Gets the branded name of the local application.
     *
     * @return The name.
     */
    private String getBrandedApplicationName() {
         return BrandingServiceFactory.getService().getApplicationName();
    }

    /**
     * Sets any ticket id found in the transaction results into the transaction
     * object.
     *
     * @param transaction The transaction.
     * @param details The transaction details.
     */
    private void setTicketId(ProvisioningTransaction transaction, TransactionDetails details) {
        // store any ticket id found in the plan or account result
        String ticketId = findTicketId(details.getPartitionedPlanResult(), details.getRequest());
        if (!Util.isNullOrEmpty(ticketId)) {
            transaction.getAttributes().put(ProvisioningTransaction.ATT_TICKET_ID, ticketId);
        }
    }

    /**
     * Sets an access request id if one exists on the Provisioning Project.
     * This comes in as an entry on the Project's attribute map keyed by
     * IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID
     *
     * @param transaction The Provisioning Transaction
     * @param details The Transaction details
     */
    private void setAccessRequestId(ProvisioningTransaction transaction, TransactionDetails details) {
        if (details.getProject() != null) {
            ProvisioningProject project = details.getProject();

            if (project.getAttributes() != null && project.getAttributes().containsKey(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID)) {
                String irId = project.getString(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID);
                transaction.getAttributes().put(ProvisioningTransaction.ATT_ACCESS_REQUEST_ID, irId);
            }
        }


    }

    /**
     * Increments the retry count contained in the transaction object.
     *
     * @param transaction The transaction.
     */
    private void incrementRetries(ProvisioningTransaction transaction) {
        // attributes should never be null at this point but does not hurt
        if (transaction.getAttributes() != null) {
            int retries = transaction.getAttributes().getInt(ProvisioningTransaction.ATT_RETRY_COUNT);

            // put it as a string to save some xml characters
            transaction.getAttributes().put(ProvisioningTransaction.ATT_RETRY_COUNT, Integer.toString(retries + 1));

            // store last time a retry happened
            transaction.getAttributes().put(ProvisioningTransaction.ATT_LAST_RETRY, new Date().getTime());
        }
    }

    /**
     * Sets the flag that indicates that the transaction timed out.
     *
     * @param transaction The transaction.
     */
    private void markTimedOut(ProvisioningTransaction transaction) {
        // attributes should never be null at this point but does not hurt, save
        // value as a string to save some xml characters
        if (transaction.getAttributes() != null) {
            transaction.getAttributes().put(ProvisioningTransaction.ATT_TIMED_OUT, Boolean.TRUE.toString());
        }
    }

    /**
     * Determines if a new transaction object should be created or an existing one
     * should be updated.
     *
     * @param request The account or object request.
     * @return True if updating, false otherwise.
     */
    private boolean isUpdate(AbstractRequest request) {
        return request.getArguments() != null &&
               request.getArguments().containsKey(ATT_PROV_TRANSACTION_ID);
    }

    /**
     * Determines if the two requests match.
     *
     * @param x The first request.
     * @param y The second request.
     * @return True if matching, false otherwise.
     */
    private boolean isMatchingRequest(AbstractRequest x, AbstractRequest y) {
        return ((x.isAccountRequest() && y.isAccountRequest()) || (!x.isAccountRequest() && !y.isAccountRequest())) &&
               Util.nullSafeEq(x.getOp(), y.getOp()) &&
               Util.nullSafeEq(x.getApplication(), y.getApplication()) &&
               Util.nullSafeEq(x.getNativeIdentity(), y.getNativeIdentity());
    }

    /**
     * Determines if this transaction should be logged based on log level configuration
     * and the status.
     *
     * @param status The status.
     * @param project The provisioning project.
     * @return True if logging should be skipped, false otherwise.
     */
    private boolean isTransactionLoggable(Status status, ProvisioningProject project) {
        // if logging is disabled completely then bail
        if (!isLoggingEnabled()) {
            return false;
        }

        Level configuredLevel = getConfiguredLoggingLevel(project);

        // compare the ordinal of the status to the ordinal of the level, this
        // could be done with some maps but these enums shouldn't be changing
        // so this seems ok
        return status.ordinal() >= configuredLevel.ordinal();
    }

    /**
     * Determines if transaction logging is enabled in the system configuration.
     *
     * @return True if enabled, false otherwise.
     */
    private boolean isLoggingEnabled() {
        return Configuration.getSystemConfig().getBoolean(Configuration.ENABLE_PROVISIONING_TRANSACTION_LOG);
    }

    /**
     * Gets the configured transaction logging level. First looks at the project arguments
     * then moves to the system configuration.
     *
     * @param project The provisioning project.
     * @return The logging level.
     */
    private Level getConfiguredLoggingLevel(ProvisioningProject project) {
        Level level = null;

        // the logging level is allowed to specified in the project and if it is
        // it will be used instead of the system configuration level
        if (project != null) {
            String projLevel = (String) project.get(PlanCompiler.ARG_PROV_TRANS_LOG_LEVEL);
            if (projLevel != null) {
                level = parseLogLevel(projLevel);
            }
        }

        // if no or invalid level specified in plan use system config
        if (level == null) {
            String configuredLevel = Configuration.getSystemConfig().getString(Configuration.PROVISIONING_TRANSACTION_LOG_LEVEL);
            if (configuredLevel != null) {
                level = parseLogLevel(configuredLevel);
            } else {
                // nothing configured so just default to failure
                level = Level.Failure;
            }
        }

        return level;
    }

    /**
     * Converts the string representation of the log level to the corresponding enum constant.
     *
     * @param levelStr The string representation of the level.
     * @return The level.
     */
    private Level parseLogLevel(String levelStr) {
        Level parsed;

        try {
            parsed = Level.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid value specified for the provisioning transaction logging level");
            parsed = null;
        }

        return parsed;
    }

    /**
     * Calculates the overall status of the transaction. The most severe
     * status will be returned, i.e. Failed > Pending > Success on the
     * severity scale.
     *
     * @param planResult The result for the partitioned plan.
     * @param request The account or object request.
     * @return The status.
     */
    private Status calculateStatus(ProvisioningResult planResult, AbstractRequest request) {
        // status is success unless we find otherwise
        Status status = Status.Success;

        // collapse attribute and permission requests into one list
        List<GenericRequest> genericRequests = new ArrayList<GenericRequest>();
        if (!Util.isEmpty(request.getAttributeRequests())) {
            genericRequests.addAll(request.getAttributeRequests());
        }

        if (!Util.isEmpty(request.getPermissionRequests())) {
            genericRequests.addAll(request.getPermissionRequests());
        }

        boolean hasRetry = false;
        for (GenericRequest genericRequest : genericRequests) {
            ProvisioningResult result = genericRequest.getResult();
            if (isValidResult(result)) {
                if (isFailed(result)) {
                    // we found at least one failure so we can break
                    status = Status.Failed;
                    break;
                } else if (isRetry(result)) {
                    hasRetry = true;
                }
            }
        }

        if (isValidResult(request.getResult())) {
            if (isFailed(request.getResult())) {
                status = Status.Failed;
            } else if (isRetry(request.getResult())) {
                hasRetry = true;
            }
        } else if (isValidResult(planResult)) {
            if (isFailed(planResult)) {
                status = Status.Failed;
            } else if (isRetry(planResult)) {
                hasRetry = true;
            }
        }

        // if a Failure status has not been set yet and we have
        // seen a retry then the status is Pending
        if (Status.Success.equals(status) && hasRetry) {
            status = Status.Pending;
        }

        return status;
    }

    /**
     * Attempts to find a generated ticket id in the request result
     * or the plan result for this transaction.
     *
     * @param planResult The plan result.
     * @param request The account or object request.
     * @return The ticket id or null if none found.
     */
    private String findTicketId(ProvisioningResult planResult, AbstractRequest request) {
        String ticketId = null;

        // first check the request result
        if (isValidResult(request.getResult())) {
            String acctResultTicket = request.getResult().getRequestID();
            if (!Util.isNullOrEmpty(acctResultTicket)) {
                ticketId = acctResultTicket;
            }
        }

        // if no ticket id in request result then check the plan result
        if (Util.isNullOrEmpty(ticketId) && isValidResult(planResult)) {
            String planResultTicket = planResult.getRequestID();
            if (!Util.isNullOrEmpty(planResultTicket)) {
                ticketId = planResultTicket;
            }
        }

        return ticketId;
    }

    /**
     * Determines if the details represent a manual transaction or if they
     * represent a transaction against the local IIQ application.
     *
     * @param details The transaction details.
     * @return True if manual or local, false otherwise.
     */
    private boolean isManualOrLocal(TransactionDetails details) {
        return details.isManual() || isLocalRequest(details.getRequest());
    }

    /**
     * Determines if the request is for the local IIQ application.
     *
     * @param request The account request.
     * @return True if request for IIQ application, false otherwise.
     */
    private boolean isLocalRequest(AbstractRequest request) {
        return ProvisioningPlan.APP_IIQ.equals(request.getApplication());
    }

    /**
     * Determines if a status of Failed should be forced based on the
     * calculated status and the source.
     *
     * @param source The source.
     * @param status The status calculated based on the results.
     * @return True if status should be forced to Failed, false otherwise.
     */
    private boolean isForceFailure(String source, Status status) {
        // role propagation disables the retry mechanism so if a status
        // of Pending is calculated then the status should be forced to
        // Failed since no updating of that status will ever occur
        return Source.RoleChangePropagation.toString().equals(source) &&
               Status.Pending.equals(status);
    }

    /**
     * Determines if this is a valid provisioning result. The result is valid
     * if it is not null and the status contained in it is also not null.
     *
     * @param result The result.
     * @return True if valid, false otherwise.
     */
    private static boolean isValidResult(ProvisioningResult result) {
        return result != null && !Util.isNullOrEmpty(result.getStatus());
    }

    /**
     * Determines if the result was a failure.
     *
     * @param result The result.
     * @return True if failure, false otherwise.
     */
    private boolean isFailed(ProvisioningResult result) {
        return ProvisioningResult.STATUS_FAILED.equals(result.getStatus());
    }

    /**
     * Determines if the result is retry.
     *
     * @param result The result.
     * @return True if retry, false otherwise.
     */
    private static boolean isRetry(ProvisioningResult result) {
        return ProvisioningResult.STATUS_RETRY.equals(result.getStatus());
    }

    /**
     * Ensures that the transaction object has a non-null attributes property.
     *
     * @param transaction The transaction.
     */
    private void ensureAttributes(ProvisioningTransaction transaction) {
        if (transaction.getAttributes() == null) {
            transaction.setAttributes(new Attributes<String, Object>());
        }
    }

    /**
     * Creates a new manual provisioning transaction based on the
     * specified forced transaction.
     *
     * @param forcedTransaction The forced transaction.
     * @param workItem The manual work item.
     * @throws GeneralException
     */
    private void createForcedManualTransaction(ProvisioningTransaction forcedTransaction, WorkItem workItem)
        throws GeneralException {

        AbstractRequest request = getRequestFromTransaction(forcedTransaction);
        AbstractRequest requestCopy = (AbstractRequest) request.deepCopy(context);

        filterRequestForOverrideTransaction(requestCopy);

        ProvisioningPlan plan = new ProvisioningPlan();
        plan.addRequest(requestCopy);

        TransactionDetails details = new TransactionDetails();
        details.setIdentityName(forcedTransaction.getIdentityName());
        details.setPartitionedPlan(plan);
        details.setRequest(requestCopy);
        details.setSource(forcedTransaction.getSource());
        details.setManual(true);
        details.setManualWorkItemName(workItem.getName());

        logTransaction(details);
    }

    /**
     * Filters the request for the transaction to be created when a
     * failed transaction is forced to a manual work item.
     *
     * @param request The request.
     */
    private void filterRequestForOverrideTransaction(AbstractRequest request) {
        // remove the old transaction id so we don't mistake this for an update
        // it should always exist but doesn't hurt
        if (request.getArguments().containsKey(ATT_PROV_TRANSACTION_ID)) {
            request.getArguments().remove(ATT_PROV_TRANSACTION_ID);
        }

        // remove any results
        request.setResult(null);

        for (AttributeRequest attributeRequest : Util.iterate(request.getAttributeRequests())) {
            attributeRequest.setResult(null);
        }

        for (PermissionRequest permRequest : Util.iterate(request.getPermissionRequests())) {
            permRequest.setResult(null);
        }
    }

    /**
     * Creates and opens a manual action work item for the owner.
     *
     * @param transaction The transaction.
     * @param requester The requester.
     * @param owner The work item owner.
     * @param workItemDescription The work item description.
     * @return The work item.
     * @throws GeneralException
     */
    private WorkItem openForceManualWorkItem(ProvisioningTransaction transaction, Identity requester,
                                             Identity owner, String workItemDescription, String comment)
        throws GeneralException {

        WorkItem workItem = new WorkItem();
        workItem.setOwner(owner);
        workItem.setRequester(requester);
        workItem.addComment(comment, requester);
        workItem.setType(WorkItem.Type.ManualAction);
        workItem.setRenderer("lcmManualActionsRenderer.xhtml");
        workItem.setHandler("sailpoint.api.Workflower");

        Identity identity = context.getObjectByName(Identity.class, transaction.getIdentityName());
        if (identity != null) {
            workItem.setTargetName(identity.getName());
            workItem.setTargetId(identity.getId());
            workItem.setTargetClass(identity.getClass().getName());
        } else {
            log.warn("Identity to open manual work item for does not exist");
        }

        workItem.setDescription(workItemDescription);

        ApprovalSet approvalSet = new ApprovalSet();

        // at this point we know that this is an account request
        AccountRequest originalRequest = (AccountRequest) getRequestFromTransaction(transaction);

        // copy the request and filter out any successful items
        AccountRequest request = (AccountRequest) originalRequest.deepCopy(context);
        filterRequest(transaction, request);

        // The logic below duplicates some of the logic in the rule that builds the approval
        // set in the workflow step with the exception that this code knows that it will
        // never accept a request targeted at the local IIQ app so some things are left out.
        //
        // I am not sure why that code lives in a rule but at some point we could
        // possibly refactor it out into a class that can be used here and in the rule.
        if (ProvisioningPlan.ObjectOperation.Create.equals(request.getOp())) {
            if (!isAccountRequestSuccess(transaction, request)) {
                ApprovalItem item = new ApprovalItem();
                item.setApplication(request.getApplication());
                item.setInstance(request.getInstance());
                item.setNativeIdentity(request.getNativeIdentity());
                item.setOperation(request.getOp().toString());

                List<String> flattened = new ArrayList<String>();
                for (AttributeRequest attr : Util.iterate(request.getAttributeRequests())) {
                    if (attr.getDisplayValue() == null) {
                        String displayName = attr.getName();
                        List val = Util.asList(attr.getValue());
                        if (val != null) {
                            String type = (String) attr.get(ProvisioningPlan.ARG_TYPE);
                            if (type != null && type.equals(ProvisioningPlan.ARG_TYPE_DATE)) {
                                Object obj = val.get(0);

                                Date date = null;
                                if (obj instanceof Date) {
                                    date = (Date) obj;
                                } else {
                                    date = new Date((Long) obj);
                                }
                                val = new ArrayList<Object>();
                                val.add(Util.dateToString(date, "M/d/y"));
                            }
                        }

                        if (Util.size(val) > 0) {
                            flattened.add(displayName + " = '" + Util.listToCsv(val) + "'");
                        }
                    } else {
                        flattened.add(attr.getDisplayValue());
                    }
                }

                for (PermissionRequest permissionRequest : Util.iterate(request.getPermissionRequests())) {
                    String flat = permissionRequest.getTarget() + "= '" + permissionRequest.getRights() + "'";
                    flattened.add(flat);
                }

                item.setValue(flattened);
                approvalSet.add(item);
            }
        } else {
            if (identity != null) {
                IdentityLibrary.addApprovalItems(identity, request, approvalSet, context);
            } else {
                log.warn("Identity to open manual work item for does not exist");
            }
        }

        workItem.setApprovalSet(approvalSet);
        if (identity != null) {
            workItem.put("identityName", identity.getName());
            workItem.put("identityDisplayName", identity.getDisplayableName());
        } else {
            log.warn("Identity to open manual work item for does not exist");
        }

        EmailTemplate template = ObjectUtil.getSysConfigEmailTemplate(
            context,
            Configuration.WORK_ITEM_ASSIGNMENT_EMAIL_TEMPLATE
        );

        Map<String, Object> templateArgs = new HashMap<String, Object>();
        templateArgs.put("item", workItem);
        templateArgs.put("workItemName", workItem.getDescription());
        templateArgs.put("requesterName", requester.getDisplayableName());

        Workflower workflower = new Workflower(context);
        workflower.open(workItem, template, templateArgs);

        return workItem;
    }

    /**
     * Filters out any successful or secret requests.
     *
     * @param transaction The transaction.
     * @param request The account request.
     */
    private void filterRequest(ProvisioningTransaction transaction, AccountRequest request) {
        if (!Util.isEmpty(request.getAttributeRequests())) {
            Iterator<AttributeRequest> itr = request.getAttributeRequests().iterator();
            while (itr.hasNext()) {
                AttributeRequest attributeRequest = itr.next();
                if (attributeRequest.isSecret() ||
                    isGenericRequestSuccess(transaction, request, attributeRequest)) {
                    itr.remove();
                }
            }
        }

        if (!Util.isEmpty(request.getPermissionRequests())) {
            Iterator<PermissionRequest> itr = request.getPermissionRequests().iterator();
            while (itr.hasNext()) {
                PermissionRequest permissionRequest = itr.next();
                if (isGenericRequestSuccess(transaction, request, permissionRequest)) {
                    itr.remove();
                }
            }
        }
    }

    /**
     * Determines if the account request was successful.
     *
     * @param transaction The transaction.
     * @param accountRequest The account request.
     * @return True if the account request was successful, false otherwise.
     */
    private boolean isAccountRequestSuccess(ProvisioningTransaction transaction,
                                            AccountRequest accountRequest) {

        boolean isSuccess = false;

        ProvisioningResult planResult = (ProvisioningResult) transaction.getAttributes().get(
            ProvisioningTransaction.ATT_PLAN_RESULT
        );

        ProvisioningResult accountResult = accountRequest.getResult();

        String status = null;

        // look from most specific to least
        if (isValidResult(accountResult)) {
            status = accountResult.getStatus();
        } else if (isValidResult(planResult)) {
            status = planResult.getStatus();
        }

        if (!Util.isNullOrEmpty(status)) {
            isSuccess = !ProvisioningResult.STATUS_FAILED.equals(status) &&
                        !ProvisioningResult.STATUS_RETRY.equals(status);
        }

        return isSuccess;
    }

    /**
     * Determines if the generic request was successful.
     *
     * @param transaction The transaction.
     * @param accountRequest The account request.
     * @param genericRequest The generic request.
     * @return True if the generic request was successful, false otherwise.
     */
    private boolean isGenericRequestSuccess(ProvisioningTransaction transaction,
                                            AccountRequest accountRequest,
                                            GenericRequest genericRequest) {
        boolean isSuccess;

        ProvisioningResult requestResult = genericRequest.getResult();
        if (isValidResult(requestResult)) {
            String status = requestResult.getStatus();

            isSuccess = !ProvisioningResult.STATUS_FAILED.equals(status) &&
                        !ProvisioningResult.STATUS_RETRY.equals(status);
        } else {
            isSuccess = isAccountRequestSuccess(transaction, accountRequest);
        }

        return isSuccess;
    }

    /**
     * Gets the abstract request contained in the provisioning transaction.
     *
     * @param transaction The transaction.
     * @return The request.
     */
    private AbstractRequest getRequestFromTransaction(ProvisioningTransaction transaction) {
        return (AbstractRequest) transaction.getAttributes().get(ProvisioningTransaction.ATT_REQUEST);
    }

    /**
     * Finds the matching filtered request for the local IIQ application.
     *
     * @param project The project.
     * @param request The request. Application should be set to IIQ.
     * @return
     */
    private AbstractRequest findLocalFilteredRequest(ProvisioningProject project, AbstractRequest request) {
        List<AbstractRequest> filteredRequests = project.getFiltered();
        for (AbstractRequest filteredRequest : Util.iterate(filteredRequests)) {
            if (isMatchingLocalRequest(request, filteredRequest)) {
                return filteredRequest;
            }
        }

        return null;
    }

    /**
     * Determines if the application and operation are matching.
     *
     * @param x The local request. Application should be set to IIQ.
     * @param y The other request.
     * @return True if matching, false otherwise.
     */
    private boolean isMatchingLocalRequest(AbstractRequest x, AbstractRequest y) {
        return ProvisioningPlan.isIIQ(x.getApplication()) &&
               Util.nullSafeEq(x.getApplication(), y.getApplication()) &&
               Util.nullSafeEq(x.getOp(), y.getOp());
    }

    /**
     * Determines if the transaction was generated from a certification.
     *
     * @param transaction The transaction.
     * @return True if a certification transaction, false otherwise.
     */
    private boolean isCertificationTransaction(ProvisioningTransaction transaction) {
        return Source.Certification.toString().equals(transaction.getSource());
    }

    /**
     * Helper method to scrub the passwords in any attribute requests in the abstract request
     * that are secret fields.
     *
     * @param request The abstract request to look for attribute requests under
     */
    private void scrubPasswords(AbstractRequest request) {
        if (request != null) {
            for (AttributeRequest attrRequest : Util.iterate(request.getAttributeRequests())) {
                if (attrRequest != null) {
                    if (attrRequest.isSecret()) {
                        ObjectUtil.scrubPasswords(attrRequest);
                    }
                }
            }
        }
    }

}
