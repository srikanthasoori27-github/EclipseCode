/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.rapidSetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AsynchronousRapidSetupWorkflowLauncher;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Configuration;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

@Path("identityOperations")
public class IdentityOperationsResource extends BaseResource {

    private static final Log log = LogFactory.getLog(IdentityOperationsResource.class);
    public static final String OP_TERMINATE = "Terminate";

    @GET
    /**
     * @return - list of all identity operations that are currently enabled, and user is
     * authorized for.  At present, this could only be terminate, but more operations
     * are anticipated.
     */
    public Response getConfiguredIdentityOperations() throws GeneralException {
        List<String> enabledIdentityOperations =
                getEnabledIdentityOperations(Configuration.RAPIDSETUP_CONFIG_TERMINATE);
        return Response.ok(enabledIdentityOperations).build();
    }

    private List<String> getEnabledIdentityOperations(String ... businessProcesses) throws GeneralException{
        List<String> enabledIdentityOperations = new ArrayList<String>();
        if(businessProcesses != null) {
            for (String businessProcess : businessProcesses) {
                boolean enabled = RapidSetupConfigUtils.getBoolean(
                        Configuration.RAPIDSETUP_CONFIG_SECTION_BUSINESS_PROCESSES + ", " +
                                businessProcess + ", " + Configuration.RAPIDSETUP_CONFIG_ENABLED);
                if (enabled) {
                    if (isAuthorizedFor(businessProcess)) {
                        enabledIdentityOperations.add(businessProcess);
                    }
                }
            }
        }

        return enabledIdentityOperations;
    }

    /**
     * Check if the current user is authorized for to run an identity operation
     * for the given RapidSetup process.  This needs to be added to as we support
     * more identity operations.
     * @param businessProcess for example. "terminate"
     * @return true of current user is authorized to run an identity operation for the process
     * @throws GeneralException an unexpected error occurs
     */
    private boolean isAuthorizedFor(String businessProcess) throws GeneralException {
        boolean authorized = false;
        if (Configuration.RAPIDSETUP_CONFIG_TERMINATE.equalsIgnoreCase(businessProcess)) {
            authorized = isAuthorized(new RightAuthorizer(SPRight.FullAccessTerminateIdentity));
        }
        return authorized;
    }

    @POST
    public Response identityOperations(Map<String, Object> params) throws GeneralException {
        String json = JsonHelper.toJson(params);
        IdentityOperationDTO identityOperationDTO = null;
        identityOperationDTO = JsonHelper.fromJson(IdentityOperationDTO.class, json);
        if((identityOperationDTO != null) &&
                (Util.nullSafeCaseInsensitiveEq(identityOperationDTO.getOperation(), OP_TERMINATE))) {
            return terminateIdentity(identityOperationDTO);
        }

        log.warn("The requested identity operation is not supported");
        throw new GeneralException("The requested identity operation is not supported");
    }

    private Response terminateIdentity(IdentityOperationDTO identityOperationDTO) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessTerminateIdentity));

        if(!RapidSetupConfigUtils.getBoolean("businessProcesses,terminate,enabled")) {
            throw new GeneralException("Identity Operations / Terminate Identity is not enabled");
        }

        AsynchronousRapidSetupWorkflowLauncher launcher = new AsynchronousRapidSetupWorkflowLauncher();

        String workflowName = RapidSetupConfigUtils.getBusinessProcessWorkflowName(Configuration.RAPIDSETUP_CONFIG_TERMINATE);

        if (Util.nullSafeSize(identityOperationDTO.getIdentityNames()) != 1) {
            log.error("Exactly one identity was expected for terminate identity process");
            throw new GeneralException("Exactly one identity was expected for terminate identity process");
        }

        String identityName = identityOperationDTO.getIdentityNames().get(0);

        // Workflow launchArguments
        Map launchArgsMap = new HashMap();
        launchArgsMap.put("identityName", identityName);
        launchArgsMap.put("plan", new ProvisioningPlan());
        launchArgsMap.put("isTerminateIdentity", true);
        launchArgsMap.put("isIdentityOperation", true);
        if(Util.isNotNullOrEmpty(identityOperationDTO.getReason())) {
            launchArgsMap.put("reasonComments", identityOperationDTO.getReason());
        }

        long current = System.currentTimeMillis();
        String requestName = "Terminate identity FOR " + identityName + " " + current;

        log.debug("Launch Asynchronous Terminate Workflow for identity " + identityName);
        launcher.launchWorkflow(getContext(), workflowName, requestName, launchArgsMap);

        log.debug("Asynchronous Terminate Workflow Launched");

        return Response.accepted().build();
    }

    @Path("identities")
    public IdentityOperationsIdentityListResource getIdentitiesResource() {
        return new IdentityOperationsIdentityListResource(this);
    }

    @Path("selectedIdentities")
    public IdentityOperationsSelectedIdentityListResource getSelectedIdentitiesResource() {
        return new IdentityOperationsSelectedIdentityListResource(this);
    }

    @Path("identityIdNames")
    public IdentityOperationsIdentityIdNameListResource getIdentityIdNamesResource() {
        return new IdentityOperationsIdentityIdNameListResource(this);
    }
}
