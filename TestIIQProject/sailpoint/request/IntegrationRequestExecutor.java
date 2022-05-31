/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Request handler used by the Provisioner to manage retryable
 * calls to an IntegrationExecutor.  Since the common case is to 
 * support retryable failures, this saves every IntegrationExecuctor
 * from having to define and launch its own RequestExecutor.
 *
 * Author: Jeff
 *
 */

package sailpoint.request;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Request;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.service.ProvisioningTransactionService.TransactionDetails;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * A RequestExecutor that handles calls to an IntegrationExecutor.
 */
public class IntegrationRequestExecutor extends AbstractRequestExecutor {

    private static final Log log = LogFactory.getLog(IntegrationRequestExecutor.class);
    
    public static final String ARG_IDENTITY = "identity";
    public static final String ARG_REQUESTOR = "requestor";
    public static final String ARG_RESULT_MODE = "resultMode";
    public static final String ARG_INTEGRATION = "integration";
    public static final String ARG_PROJECT = "project";
    public static final String ARG_PLAN = "plan";
    public static final String ARG_SOURCE = "source";

    public IntegrationRequestExecutor() {
        super();
    }


    public void execute(SailPointContext context, 
                        Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {


        // technically we only require this for certain resultModes, we
        // will not have an identity in the case of group provisioning retries
        String identname = args.getString(ARG_IDENTITY);
        Identity identity = null;
        if (!Util.isNullOrEmpty(identname)) {
            try {
                identity = context.getObjectByName(Identity.class, identname);
            } catch (GeneralException e) {
                log.error(e);
            }
        }

        String intname = args.getString(ARG_INTEGRATION);
        if (intname == null)
            throw new RequestPermanentException("Missing integration name");

        IntegrationConfig config = null;
        try {
            config = context.getObjectByName(IntegrationConfig.class, intname);
            if (config == null) {
                // it may be a RW connector
                Application app = context.getObjectByName(Application.class, intname);
                if (app != null) {
                    config = new IntegrationConfig(app);
                    config.load();
                }
            }
        }
        catch (GeneralException e) {
            log.error(e);
        }
        if (config == null)
            throw new RequestPermanentException("Invalid IntegrationConfig: " + 
                                                intname);


        ProvisioningProject project = (ProvisioningProject)args.get(ARG_PROJECT);
        if (project == null) {
            // must be a pre 5.0 request, convert it
            ProvisioningPlan plan = (ProvisioningPlan)args.get(ARG_PLAN);

            if (plan != null) {
                // old style, only passed a few options
                // fake up a project to hold evaluation options
                project = new ProvisioningProject();
                project.add(plan);

                // "assigner" is used both for building RoleAssignments
                // and for audit logging, here it is only for logging
                project.put(PlanEvaluator.ARG_REQUESTER, request.getLauncher());

                // upgrade the args to look like 5.0 for the retry
                args.put(ARG_PROJECT, project);
                args.remove(ARG_PLAN);
            }
        }
        else {
            // shold only have one plan in here for a single connector
            List<ProvisioningPlan> plans = project.getIntegrationPlans();
            if (plans == null || plans.size() == 0)
                log.error("Provisioning retry with no plans");
            else {
                if (plans.size() > 1) 
                    log.error("Retry request with more than one plan");
            }

            // allow malformed projects? I guess just go ahead with
            // the first plan
        }

        if (project == null)
            throw new RequestPermanentException("Missing project");

        // now that we have a project make sure that if no identity
        // was specified that there are not any account requests
        if (identity == null && hasAccountRequests(project)) {
            updateProvisioningTransactions(context, request, project, identity);

            throw new RequestPermanentException("No identity specified");
        }

        // don't bother with Provisioner, go directly to PlanEvaluator
        // since the plan is already compiled and initialized

        String status = null;
        try {
            PlanEvaluator p = new PlanEvaluator(context);
            status = p.retry(identity, config, project);
        }
        catch (GeneralException e) {
            updateProvisioningTransactions(context, request, project, identity);

            // I guess convert it into this.
            // What does this actually do?  audit something?
            throw new RequestPermanentException(e);
        }

        // this is the "normalized" status
        if (ProvisioningResult.STATUS_FAILED.equals(status)) {
            updateProvisioningTransactions(context, request, project, identity);

            throw new RequestPermanentException(getExceptionMessage(project));
        }

        else if (ProvisioningResult.STATUS_RETRY.equals(status)) {
            updateProvisioningTransactions(context, request, project, identity);

            throw new RequestTemporaryException(getExceptionMessage(project));
        }

    }

    /**
     * Determines if the plan in the retry project has any account requests.
     *
     * @param project The project.
     * @return True if the project contains account requests, false otherwise.
     */
    private boolean hasAccountRequests(ProvisioningProject project) {
        boolean hasRequests = false;

        // should be one plan, even if more than on the retry method of the
        // PlanEvaluator will pull out the first plan and use that one
        if (Util.size(project.getIntegrationPlans()) > 0) {
            ProvisioningPlan plan = project.getIntegrationPlans().get(0);

            hasRequests = Util.size(plan.getAccountRequests()) > 0;
        }

        return hasRequests;
    }

    /**
     * If the specified request has met its max retry count then update the
     * status of any provisioning transactions contained in the account/object
     * requests in the plan to failed.
     *
     * @param context The context.
     * @param retryRequest The retry Request object.
     * @param project The project.
     * @param identity The identity. Could be null.
     */
    private void updateProvisioningTransactions(SailPointContext context, Request retryRequest,
                                                ProvisioningProject project, Identity identity) {
        if (isTimedOut(retryRequest)) {
            // at this point we know that there was one plan, see the execute method for the
            // logic that tests this
            ProvisioningPlan plan = project.getIntegrationPlans().get(0);

            // try to get the source from the plan first, it can also be
            // in the attribute map of the request
            String source = plan.getSource();
            if (Util.isNullOrEmpty(source)) {
                source = retryRequest.getString(ARG_SOURCE);
            }

            ProvisioningTransactionService transactionService = new ProvisioningTransactionService(context);

            for (ProvisioningPlan.AbstractRequest request : Util.iterate(plan.getAllRequests())) {
                try {
                    TransactionDetails details = new TransactionDetails();
                    details.setProject(project);
                    details.setSource(source);
                    details.setPartitionedPlan(plan);
                    details.setRequest(request);

                    // there wont be an identity in the case of group provisioning
                    if (identity != null) {
                        details.setIdentityName(identity.getName());
                    }

                    transactionService.timeOutTransaction(details);
                } catch (GeneralException ex) {
                    log.error("An error occurred while trying to update a provisioning transaction", ex);
                }
            }
        }
    }

    /**
     * Determines if the Request object has been retried the maximum number of times.
     *
     * @param request The request.
     * @return True if timed out, false otherwise.
     */
    private boolean isTimedOut(Request request) {
        boolean timedOut = false;

        if (request.getDefinition() != null) {
            int maxRetries = request.getDefinition().getRetryMax();

            // if its not set to forever and we have reached the max tries
            if (maxRetries != -1 && request.getRetryCount() >= maxRetries) {
                timedOut = true;
            }
        }

        return timedOut;
    }

    /**
     * Given a plan that was just retried, fish out a message we can
     * include in the exception.  This has never worked very well, it just
     * returns the first one it can find from the root result.
     * I guess that's okay it leaves the onus on the Connector
     * to put something interesting in the root result.
     */
    private Message getExceptionMessage(ProvisioningProject project) {
        
        Message msg = null;

        // retry projects are only supposed to have one plan
        List<ProvisioningPlan> plans = project.getIntegrationPlans();
        if (plans != null && plans.size() > 0) {
            ProvisioningPlan plan = plans.get(0);

            ProvisioningResult result = plan.getResult();
            if (result == null) {
                // TODO: I guess we could traverse down to 
                // account/attribute requests
            }

            if (result != null) {
                List<Message> errors = result.getErrors();
                if (errors != null && errors.size() > 0)
                    msg = errors.get(0);
                else {
                    List<Message> warnings = result.getWarnings();
                    if (warnings != null && warnings.size() > 0)
                        msg = warnings.get(0);
                }
            }

        }

        if (msg == null)
            msg = new Message("Unspecified failure");
            
        return msg;
    }

}
