/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.Target;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;

/**
 * The service responsible for listing the group's permissions associated with a container.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerGroupPermissionListService extends BaseListService<BaseListServiceContext> {
    String groupId;
    String containerId;

    public ContainerGroupPermissionListService(SailPointContext context, BaseListServiceContext serviceContext,
                                               ListServiceColumnSelector columnSelector, String groupId, String containerId) {
        super(context, serviceContext, columnSelector);
        this.groupId = groupId;
        this.containerId = containerId;
    }


    /**
     *
     * Return the list of groups on the container for the given query options
     * @param containerOnly Whether to trim the list to only this container
     * @return
     * @throws GeneralException
     */
    public ListResult getPermissions(boolean containerOnly) throws GeneralException {
        ManagedAttribute group = this.getGroup();
        if(group==null) {
            throw new GeneralException("Cannot find ManagedAttribute with id: " + groupId);
        }
        List<Permission> permissions = group.getTargetPermissions();
        List<ContainerGroupPermissionDTO> dtos = this.mergePermissionDTOs(this.getPermissionDTOs(permissions, containerOnly));
        return new ListResult(this.trimAndSortResults(this.convertObjects(dtos)), dtos.size());
    }


    /**
     * Iterate over the permissions on the target and create ContainerGroupPermissionDTO out of the rights
     * @param permissions The list of permissions on the ManagedAttribute
     * @param containerOnly Whether we only want to list the permissions that apply to the current Container or not
     * @return
     * @throws GeneralException
     */
    protected List<ContainerGroupPermissionDTO> getPermissionDTOs(List<Permission> permissions, boolean containerOnly) throws GeneralException {
        List<ContainerGroupPermissionDTO> dtos = new ArrayList<ContainerGroupPermissionDTO>();
        Application application = null;
        for(Permission permission : permissions) {
            Target currentTarget = getContext().getObjectById(Target.class, containerId);
            boolean matches = permission.getTarget().equals(currentTarget.getName());
            if((containerOnly && matches) || (!containerOnly)) {
                if(permission.getRights()!=null) {
                    List<String> rights = permission.getRightsList();
                    for (String right : rights) {
                        ContainerGroupPermissionDTO dto = new ContainerGroupPermissionDTO(right);

                        Target target = null;
                        // If we are only interested in the permissions on the current container, just use the current
                        // target to get the managed attribute
                        if(containerOnly && matches) {
                            target = currentTarget;
                        }
                        // Otherwise, we'll try to look up the managed attribute from the target on the permission
                        else {
                            //let's use the currentTarget to pull the application
                            if (application == null) {
                                application = PamUtil.getApplicationForTarget(context, currentTarget);
                            }

                            QueryOptions ops = new QueryOptions();
                            ops.add(Filter.eq("name", permission.getTarget()));

                            //This logic is to avoid conflicts in case we have targets with the same
                            //name, each one from a different application
                            List<Target> targets = context.getObjects(Target.class, ops);

                            for (Target t : targets) {
                                Application ap = PamUtil.getApplicationForTarget(context, t);
                                if (application.getId().equals(ap.getId())) {
                                    target = t;
                                    break;
                                }
                            }
                        }

                        if(target!=null) {
                            if (application == null) {
                                application = PamUtil.getApplicationForTarget(context, target);
                            }
                            ManagedAttribute ma = ManagedAttributer.get(this.context, application, false, null, target.getNativeObjectId(),
                                    ContainerService.OBJECT_TYPE_CONTAINER);

                            if (ma != null) {
                                dto.addTarget(ma.getDisplayableName());
                                dtos.add(dto);
                            }
                        }
                    }
                }
            }
        }

        return dtos;
    }

    /**
     * Iterates over the list of dtos that we have and merges any dtos that have the same rights so that we have
     * multiple targets for the same right instead of one dto for each right
     * @param dtos The list of dtos that we need to merge
     * @return
     */
    protected List<ContainerGroupPermissionDTO> mergePermissionDTOs(List<ContainerGroupPermissionDTO> dtos) {
        List<ContainerGroupPermissionDTO> mergedDTOs = new ArrayList<ContainerGroupPermissionDTO>();
        for(ContainerGroupPermissionDTO dto : dtos) {
            boolean found = false;
            for(ContainerGroupPermissionDTO testDto : mergedDTOs) {
                if(testDto.getRights().equals(dto.getRights())) {
                    found = true;
                    testDto.addTargets(dto.getTargets());
                }
            }
            if(!found) {
                mergedDTOs.add(dto);
            }
        }
        return mergedDTOs;
    }

    /**
     * Load the ManagedAttribute by the id
     * @return
     * @throws GeneralException
     */
    public ManagedAttribute getGroup() throws GeneralException {
        return this.getContext().getObjectById(ManagedAttribute.class, this.groupId);
    }

}