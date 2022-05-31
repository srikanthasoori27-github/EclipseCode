/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import static sailpoint.service.pam.ContainerService.OBJECT_TYPE_CONTAINER;

/**
 * The service responsible for listing containers.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerListService extends BaseListService<BaseListServiceContext> {

    public ContainerListService(SailPointContext context, BaseListServiceContext serviceContext,
                                ListServiceColumnSelector columnSelector) {
        super(context, serviceContext, columnSelector);
    }


    /**
     * Create the search query options to apply to Target objects
     * @param searchTerm String The optional search term to filter the targets by
     * @return QueryOptions The set of query options to apply to the Target search
     * @throws GeneralException
     */
    protected QueryOptions createQueryOptions(String searchTerm, List<Application> applications) throws GeneralException {
        QueryOptions qo = super.createQueryOptions();
        List<String> targetSourceIds = new ArrayList<String>();
        for (Application application : applications) {
            if (application != null) {
                for (TargetSource targetSource : Util.iterate(application.getTargetSources())) {
                    targetSourceIds.add(targetSource.getId());
                }
                if (targetSourceIds.isEmpty()) {
                    Message msg = Message.info(MessageKeys.UI_PAM_APPLICATION_TARGET_SOURCES_ERROR);
                    throw new GeneralException(msg.getLocalizedMessage(listServiceContext.getLocale(), listServiceContext.getUserTimeZone()));
                }
            }
        }
        qo.add(Filter.in("targetSource.id", targetSourceIds));
        qo.add(Filter.join("nativeObjectId", "ManagedAttribute.value"));
        qo.add(Filter.eq("ManagedAttribute.type", OBJECT_TYPE_CONTAINER));
        qo.add(Filter.in("ManagedAttribute.application", applications));
        qo.addOrdering("Target.displayName", true);
        qo.setDistinct(true);

        if(Util.isNotNullOrEmpty(searchTerm)) {

            Filter filterDisplayName = Filter
                    .ignoreCase(Filter.like("ManagedAttribute.displayName", searchTerm, Filter.MatchMode.START));
            Filter filterValue = Filter
                    .ignoreCase(Filter.like("ManagedAttribute.value", searchTerm, Filter.MatchMode.START));
            Filter filterNull = Filter.isnull("ManagedAttribute.displayName");
            Filter and = Filter.and(filterValue, filterNull);
            Filter or = Filter.or(filterDisplayName, and);
            qo.add(or);
        }

        return qo;
    }

    /**
     *
     * Return the list of containers that match the provided search filters
     *
     * @param searchTerm String an optional search term to filter the container list by
     * @param includeCounts Whether to run the queries over each target to get their counts
     * @return ListResult of ContainerDTO objects
     * @throws GeneralException
     */
    public ListResult getContainers(String searchTerm, List<Filter> filters, boolean includeCounts) throws GeneralException {
        List<ContainerDTO> dtos = new ArrayList<ContainerDTO>();
        int count = 0;

        List<Application> applications = new PamUtil(this.context).getPamApplications();
        if (!Util.isEmpty(applications)) {
            QueryOptions qo = this.createQueryOptions(searchTerm, applications);
            for (Filter filter : Util.safeIterable(filters)) {
                qo.add(filter);
            }

            count = getContext().countObjects(Target.class, qo);
            List<Target> targets = getContext().getObjects(Target.class, qo);
            for (Target target : targets) {
                Application app = PamUtil.getApplicationForTarget(this.context, target);

                ContainerService containerService = new ContainerService(getContext(), target, app);
                dtos.add(containerService.createContainerDTO(target, app, includeCounts));
            }
        }

        return new ListResult(dtos, count);
    }

    /**
     * Get the list of containers based on their id
     * @param ids A csv list of ids to retrieve containers by
     * @return ListResult of ContainerDTO objects
     * @throws GeneralException
     */
    public ListResult getContainersByIds(String ids) throws GeneralException{
        List<ContainerDTO> dtos = new ArrayList<ContainerDTO>();

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.in("id", Util.csvToList(ids)));

        int count = getContext().countObjects(Target.class, qo);
        List<Target> targets = getContext().getObjects(Target.class, qo);
        for (Target target : targets) {
            Application app = PamUtil.getApplicationForTarget(this.context, target);

            ContainerService containerService = new ContainerService(getContext(), target, app);
            dtos.add(containerService.createContainerDTO(target, app, true));
        }

        return new ListResult(dtos, count);
    }
}