/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.Target;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * The service responsible for listing the identity's permissions associated with a container.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerIdentityPermissionListService extends BaseListService<BaseListServiceContext> {
    String identityId;
    String containerId;

    public ContainerIdentityPermissionListService(SailPointContext context, BaseListServiceContext serviceContext,
                                                  ListServiceColumnSelector columnSelector, String containerId, String identityId) {
        super(context, serviceContext, columnSelector);
        this.identityId = identityId;
        this.containerId = containerId;
    }


    /**
     * Return the list of permissions for the identity on the container for the given query options
     *
     * @return
     * @throws GeneralException
     */
    public ListResult getPermissions() throws GeneralException {
        List<ContainerPermissionDTO> dtos = this.getDirectPermissions();
        dtos.addAll(this.getEffectivePermissions());
        List<ContainerPermissionDTO> mergedDTOs = this.mergePermissionDTOs(dtos);
        int count = mergedDTOs.size();

        return new ListResult(this.trimAndSortResults(this.convertObjects(mergedDTOs)), count);
    }

    /**
     * Get the identity's effective access to a container and iterate over the entitlements to find all of the permissions
     * @return
     * @throws GeneralException
     */
    private List<ContainerPermissionDTO> getEffectivePermissions() throws GeneralException {
        List<ContainerPermissionDTO> dtos = new ArrayList<>();

        Target target = getContext().getObjectById(Target.class, containerId);
        ContainerService containerService = new ContainerService(this.context);
        containerService.setTarget(target);

        QueryOptions qo = containerService.getIdentityEffectiveQueryOptions(IdentityEntitlement.class);
        qo.add(Filter.eq("identity.id", this.identityId));
        List<String> props = Arrays.asList("application.name", "ManagedAttribute.id");

        Iterator<Object[]> it = this.getContext().search(IdentityEntitlement.class, qo, props);
        if (it != null) {
            PamExternalUserStoreService svc = new PamExternalUserStoreService(this.context, target);

            while (it.hasNext()) {
                Object[] row = it.next();
                String applicationName = (String)row[0];
                String id = (String)row[1];

                ManagedAttribute ma = getContext().getObjectById(ManagedAttribute.class, id);

                // The name of the group should always come from the group that grants membership.
                String groupName = ma.getDisplayableName();

                List<Permission> permissions = ma.getTargetPermissions();

                // If the group is external, get the stub group that the permissions will live on.
                if (svc.isExternalGroup(ma)) {
                    ManagedAttribute stubGroup = svc.findStubGroupForExternalGroup(ma);
                    if (null != stubGroup) {
                        permissions = stubGroup.getTargetPermissions();
                    }
                }

                dtos.addAll(this.createPermissionDTOs(permissions, target, applicationName, null, groupName));

            }
        }
        return dtos;
    }

    /**
     * Return the list of permissions that the identity has with direct access to the container.  This would be any
     * permissions contained on links to the Pam Application
     * @return
     * @throws GeneralException
     */
    private List<ContainerPermissionDTO> getDirectPermissions() throws GeneralException {
        List<ContainerPermissionDTO> dtos = new ArrayList<ContainerPermissionDTO>();

        Target target = getContext().getObjectById(Target.class, containerId);
        Application application = PamUtil.getApplicationForTarget(context, target);

        if (application == null) {
            throw new GeneralException("Application cannot be null for getDirectPermissions() query on container identity.");
        }

        ContainerService containerService = new ContainerService(this.context);
        containerService.setTarget(target);
        Map<Link,List<Permission>> permissionsByLink = containerService.getDirectPermissionsForIdentityOnTarget(this.identityId);

        if (!permissionsByLink.isEmpty()) {
            for (Map.Entry<Link,List<Permission>> entry : permissionsByLink.entrySet()) {
                // We'll always show direct permissions on the Link on the PAM app, since this is what would need to
                // be modified to remove access.
                Link link = entry.getKey();
                List<Permission> permissions = entry.getValue();

                dtos.addAll(this.createPermissionDTOs(permissions, target, link.getApplicationName(), link.getDisplayableName(), null));
            }
        }

        return dtos;
    }

    /**
     * Build a list of ContainerPermissionDTO object from a list of permissions
     * @param permissions The List of Permission objects to iterate over
     * @param target The Target object we are looking for permissions on
     * @param applicationName The name of the application
     * @param nativeIdentity The native identity of the user on the application
     * @param groupName The name of the group
     * @return
     */
    protected List<ContainerPermissionDTO> createPermissionDTOs(List<Permission> permissions, Target target, String applicationName,
                                                        String nativeIdentity, String groupName) {
        List<ContainerPermissionDTO> dtos = new ArrayList<ContainerPermissionDTO>();
        if(!Util.isEmpty(permissions)) {
            for(Permission permission: permissions) {
                if(permission.getTarget().equals(target.getName())) {
                    //Split the rights on the permission and create a dto for each permission.
                    if(permission.getRights()!=null) {
                        List<String> rights = permission.getRightsList();
                        for(String right : rights) {
                            ContainerPermissionDTO dto = new ContainerPermissionDTO(right);
                            dto.addGrantingAccount(applicationName, nativeIdentity, groupName);
                            dtos.add(dto);
                        }
                    }
                }
            }
        }

        return dtos;
    }

    /**
     * Iterates over the list of dtos that we have and merges any dtos that have the same rights so that we have
     * multiple granting accounts for the same right instead of one dto for each right
     * @param dtos The list of dtos that we need to merge
     * @return
     */
    protected List<ContainerPermissionDTO> mergePermissionDTOs(List<ContainerPermissionDTO> dtos) {
        List<ContainerPermissionDTO> mergedDTOs = new ArrayList<ContainerPermissionDTO>();
        for(ContainerPermissionDTO dto : dtos) {
            boolean found = false;
            for(ContainerPermissionDTO testDto : mergedDTOs) {
                if(testDto.getRights().equals(dto.getRights())) {
                    found = true;
                    testDto.addGrantingAccounts(dto.getGrantingAccounts());
                }
            }
            if(!found) {
                mergedDTOs.add(dto);
            }
        }
        return mergedDTOs;
    }

}