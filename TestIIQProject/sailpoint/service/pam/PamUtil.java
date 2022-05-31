/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;
import sailpoint.object.Target;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *         <p>
 *         Utility class used for common functionality shared across the various pam services
 */
public class PamUtil {

    private static final Log log = LogFactory.getLog(PamUtil.class);

    public static final String PAM_APPLICATION_TYPE = "Privileged Account Management";

    /**
     * The list of priveleged access management permissions that a user can request when adding identities
     * to a pam app
     */
    public static final String ATTR_PAM_PERMISSIONS = "pamPermissions";

    public static final String TYPE = "type";

    private static final String NAME = "name";

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public PamUtil(SailPointContext context) {
        this.context = context;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONFIGURATION
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Returns All PAM applications that are defined in the system.
     * They will be used to get access to the containers.
     *
     * @return Application the PAM application
     * @throws GeneralException
     */
    public List<Application> getPamApplications() throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq(TYPE, PAM_APPLICATION_TYPE));
        qo.setOrderBy(NAME);
        List<Application> applications = context.getObjects(Application.class, qo);

        if (!Util.isEmpty(applications)) {
            return applications;
        } else {
            log.warn("There are no PAM applications defined.");
            throw new GeneralException(MessageKeys.UI_PAM_APPLICATION_ERROR);
        }
    }

    /**
     * Get the name of the configured workflow from the pam config
     * @return
     * @throws GeneralException
     */
    public String getPamProvisioningWorkflowName() throws  GeneralException {
        String pamWorkflowName = this.context.getConfiguration().getString(Configuration.PAM_WORKFLOW_IDENTITY_PROVISIONING);
        if (pamWorkflowName == null) {
            throw new GeneralException(MessageKeys.UI_PAM_WORKFLOW_ERROR);
        }
        return pamWorkflowName;
    }

    /**
     * Retrieve the list of permissions available on the pam app
     * @return A list of strings that represent the requestable permissions on a pam application
     * @throws GeneralException
     */
    @SuppressWarnings({ "unchecked"})
    public List<String> getPamPermissions(String applicationName) throws GeneralException {
        Application pamApplication = this.context.getObjectByName(Application.class, applicationName);

        if (pamApplication == null) {
            throw new GeneralException(MessageKeys.UI_PAM_APPLICATION_ERROR);
        }

        List<String> permissions = (List<String>)pamApplication.getAttributes().getList(ATTR_PAM_PERMISSIONS);
        return permissions;
    }

    public static  Application getApplicationForTarget(SailPointContext context, Target target)
            throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("targetSources.id", target.getTargetSource().getId()));
        List<Application> applications = context.getObjects(Application.class, ops);
        if (!Util.isEmpty(applications)) {
            return applications.get(0);
        }
        return null;
    }

    /**
     * Check if the system config for modifying privileged data is enabled. Error if not
     * @throws GeneralException
     */
    public static void checkModifyPrivDataEnabled() throws GeneralException {
        if (!Configuration.getSystemConfig().getBoolean(Configuration.PAM_MODIFY_PRIVILEGED_DATA_ENABLED, false)) {
            throw new GeneralException("Modifying privileged items is disabled.");
        }
    }

    /**
     * Check if system config for create container is enabled. throws exception if not
     * @throws GeneralException
     */
    public static void checkCreateContainerEnabled() throws GeneralException  {
        if (!Configuration.getSystemConfig().getBoolean(Configuration.PAM_CREATE_CONTAINER_ENABLED, false)) {
            throw new GeneralException("Create container is disabled.");
        }
    }
    /**
     * Check if the system config for modifying identities on containers is enabled
     * @throws GeneralException
     */
    public static void checkProvisionIdentitiesEnabled() throws GeneralException {
        if (!Configuration.getSystemConfig().getBoolean(Configuration.PAM_PROVISIONING_ENABLED)) {
            throw new GeneralException("Modifying container identities is disabled.");
        }
    }

    /**
     * If the system config to allow PAM container owners to modify their containers is enabled check that the
     * logged in user is either the owner of the container or a member of the workgroup that owns the container
     * @param loggedInUser
     * @param containerId
     * @param context
     * @return
     * @throws GeneralException
     */
    public static boolean isContainerOwnerAndCanEdit(Identity loggedInUser, String containerId, SailPointContext context)
            throws GeneralException {
        if (!Configuration.getSystemConfig().getBoolean(Configuration.PAM_OWNER_CAN_EDIT_CONTAINER)) {
            // config is not enabled so return false
            return false;
        } else {
            Target container = context.getObjectById(Target.class, containerId);
            if (container == null) {
                throw new GeneralException("No container found");
            }
            Application app = PamUtil.getApplicationForTarget(context, container);
            if (app == null) {
                throw new GeneralException("No application found for container");
            }
            // get the container managed attribute to check the owner
            ManagedAttribute containerMA =
                    ManagedAttributer.get(context, app.getId(), false, null, container.getNativeObjectId(), ContainerService.OBJECT_TYPE_CONTAINER);
            if (containerMA == null) {
                throw new GeneralException("No container managed attribute found for container");
            }
            Identity containerOwner = containerMA.getOwner();
            if (containerOwner == null) {
                // container has no owner so return false
                return false;
            } else {
                // If the logged in user is the container owner or belongs to the workgroup that owns the container return true
                if (containerOwner.getId().equals(loggedInUser.getId())) {
                    return true;
                } else if (containerOwner.isWorkgroup() && loggedInUser.isInWorkGroup(containerOwner)) {
                    return true;
                }
            }
        }
        return false;
    }
}