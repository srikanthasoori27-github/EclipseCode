/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Target;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

/**
 * The service responsible for listing the identities on a containers.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerIdentityListService extends BaseListService<BaseListServiceContext> {

    public static String PROPERTY_DIRECT = "direct";

    public ContainerIdentityListService(SailPointContext context, BaseListServiceContext serviceContext,
                                ListServiceColumnSelector columnSelector) {
        super(context, serviceContext, columnSelector);
    }


    /**
     * Return the list of identities on the container for the given query options
     * @param identityOptions A set of queryoptions to filter the list of identities by.  Should be either the direct or indirect queryoptions
     * @param isDirect Whether we want to get identities with direct access or with effective access (through a group)
     * @return
     * @throws GeneralException
     */
    public ListResult getIdentities(QueryOptions identityOptions, boolean isDirect) throws GeneralException {

        QueryOptions qo = this.getQueryOptions(identityOptions);
        int count = countResults(Identity.class, this.getQueryOptions(qo));
        List<Map<String,Object>> results = super.getResults(Identity.class, qo);
        for(Map<String,Object> result : results) {
            result.put(PROPERTY_DIRECT, isDirect);
        }
        return new ListResult(results, count);
    }

    /**
     * Return the count of identities with the given query options
     * @param identityOptions A set of queryoptions to filter the list of identities by.  Should be either the direct or indirect queryoptions
     * @return
     * @throws GeneralException
     */
    public int getIdentitiesCount(QueryOptions identityOptions) throws GeneralException {
        QueryOptions qo = this.getQueryOptions(identityOptions);
        return countResults(Identity.class, this.getQueryOptions(qo));
    }

    /**
     * Return the members of the given PAM group that have access to the given container.  If the given group is
     * an external group, the membership is loaded from the external group rather than the PAM group.
     *
     * @param containerId  The ID of the Target container.
     * @param groupId  The ID of the PAM group ManagedAttribute for which to return members.
     *
     * @return A ListResult with the members of the given group on the given container.
     */
    public ListResult getGroupMembers(String containerId, String groupId) throws GeneralException {
        ContainerService svc = new ContainerService(this.context);
        Target target = this.context.getObjectById(Target.class, containerId);
        svc.setTarget(target);

        // Get the effective query options.
        QueryOptions qo = svc.getIdentityEffectiveQueryOptions();

        // We want to limit the members to only those that are members of this group.
        PamExternalUserStoreService externalSvc = new PamExternalUserStoreService(getContext(), target);
        ManagedAttribute group = externalSvc.getExternalOrLocalGroup(groupId);
        qo.add(Filter.eq("ManagedAttribute.id", group.getId()));

        return this.getIdentities(qo, false);
    }

    /**
     * Create a new set of query options (with sorting, paging, etc...) and merge the provided options into
     * it.  Used to
     * @param identityOptions A set of queryoptions to filter the list of identities by.  Should be either the direct or indirect queryoptions
     * @return
     * @throws GeneralException
     */
    private QueryOptions getQueryOptions(QueryOptions identityOptions) throws GeneralException {
        QueryOptions qo = this.createQueryOptions();
        if(identityOptions != null) {
            qo.setDistinct(identityOptions.isDistinct());
            qo.getFilters().addAll(identityOptions.getFilters());
        }
        return qo;
    }

}