/* (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Type;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.certification.CertificationUtil;


/**
 * A service for listing and filtering CertificationEntity objects.
 */
public class CertificationEntityListService extends BaseListService<CertificationEntityListServiceContext> {

    private Type certificationType;
    private boolean isAutoApproveEnabled;

    /**
     * Constructor that defaults to disabled auto approvals.
     *
     * @param  listServiceContext  The entity list context.
     * @param  type  Certification type
     */
    public CertificationEntityListService(CertificationEntityListServiceContext listServiceContext,
            BaseListResourceColumnSelector columnSelector, Type type) {
        this(listServiceContext, columnSelector, type, false);
    }

    /**
     * Constructor to specify auto approval state.
     *
     * @param  listServiceContext  The entity list context.
     * @param  type  Certification type
     * @param  isAutoApproveEnabled True if auto approvals are enabled for the cert.
     */
    public CertificationEntityListService(CertificationEntityListServiceContext listServiceContext,
            BaseListResourceColumnSelector columnSelector, Type type, boolean isAutoApproveEnabled) {
        super(listServiceContext.getContext(), listServiceContext, columnSelector);
        this.certificationType = type;
        this.isAutoApproveEnabled = isAutoApproveEnabled;
    }

    /**
     * Get certification entities using the CertificationEntityListServiceContext parameters.
     *
     * @return ListResult containing the certification entities
     * @throws GeneralException
     */
    public ListResult getCertificationEntities() throws GeneralException {
        QueryOptions qo = this.createEntityListQueryOptions();
        List<CertificationEntityDTO> entityDTOs = new ArrayList<CertificationEntityDTO> ();    
        List<Map<String,Object>> objects = super.getResults(CertificationEntity.class, qo);
        for (Map<String, Object>object : objects) {
            entityDTOs.add(new CertificationEntityDTO(object, columnSelector.getColumns()));
        }

        if (listServiceContext.isIncludeStatistics()) {
            addStatistics(entityDTOs);
        }

        addTypeSpecificProperties(entityDTOs);
        addAutoApproved(entityDTOs);

        int count = super.countResults(CertificationEntity.class, qo);

        return new ListResult(entityDTOs, count);
    }

    /**
     * Return all entity IDs using the CertificationEntityListServiceContext parameters.
     *
     * @return A non-null List with all entity IDs.
     */
    public List<String> getCertificationEntityIds() throws GeneralException {
        QueryOptions qo = this.createEntityListQueryOptions();
        Iterator<Object[]> it = this.context.search(CertificationEntity.class, qo, "id");

        List<String> ids = new ArrayList<>();
        while (it.hasNext()) {
            ids.add((String) it.next()[0]);
        }

        return ids;
    }

    /**
     * Add properties to entity DTO list based on cert type
     * @param entityDTOs entity DTO list
     * @throws GeneralException
     */
    private void addTypeSpecificProperties(List<CertificationEntityDTO> entityDTOs) throws GeneralException {

        if (!Util.isEmpty(entityDTOs)) {
            String certId = this.listServiceContext.getCertificationId();
            Certification cert = getContext().getObjectById(Certification.class, certId);
            CertificationEntityService entityService = new CertificationEntityService(cert, listServiceContext);
            for (CertificationEntityDTO entityDTO : entityDTOs) {
                entityService.addTypeSpecificProperties(entityDTO);
            }
        }

        if(this.certificationType.equals(Type.DataOwner)) {
            addItemCount(entityDTOs);
        }
    }

    /**
     * Add certification item count to entity DTO List
     * @param entityDTOs entity DTO list
     * @throws GeneralException
     */
    private void addItemCount(List<CertificationEntityDTO> entityDTOs) throws GeneralException {
        for (CertificationEntityDTO entityDTO : entityDTOs) {
            CertificationEntity entity = this.getContext().getObjectById(CertificationEntity.class, entityDTO.getId());
            entityDTO.setCertificationItemCount(entity.getItems().size());
        }
    }

    /**
     * Add the item status count statistics to the entities in the given entity DTO list.
     * @param entityDTOs entity DTO list
     */
    private void addStatistics(List<CertificationEntityDTO> entityDTOs) throws GeneralException {
        if (!Util.isEmpty(entityDTOs)) {
            String certId = this.listServiceContext.getCertificationId();

            for (CertificationEntityDTO entityDTO : entityDTOs) {
                CertificationItemStatusCount stats =
                    CertificationUtil.getItemStatusCount(this.listServiceContext, certId, (String) entityDTO.getId());

                entityDTO.setItemStatusCount(stats);
            }
        }
    }

    /**
     * Add a flag indicating whether this entity contains auto-approved items.
     *
     * @param entityDTOs Entity DTOs to check.
     * @throws GeneralException
     */
    private void addAutoApproved(List<CertificationEntityDTO> entityDTOs) throws GeneralException {
        if (this.isAutoApproveEnabled && !Util.isEmpty(entityDTOs)) {
            for (CertificationEntityDTO entityDTO : entityDTOs) {
                CertificationEntity entity = this.getContext().getObjectById(CertificationEntity.class, entityDTO.getId());
                entityDTO.setHasAutoApprovals(entity.hasAutoApprovals());
            }
        }
    }

    /**
     * Create a QueryOptions that can find the CertificationEntities using the list context parameters.
     */
    private QueryOptions createEntityListQueryOptions() throws GeneralException {
        QueryOptions qo = super.createQueryOptions();
        qo.setDistinct(true);

        CertificationEntityListServiceContext listCtx = this.getListServiceContext();

        // Set the certification ID ... this is required.
        if (null == listCtx.getCertificationId()) {
            throw new GeneralException("Certification ID is required");
        }
        qo.add(Filter.eq("certification.id", listCtx.getCertificationId()));

        // Filter by query if specified.
        String query = listCtx.getQuery();
        if (Util.isNotNullOrEmpty(query)) {
            addFilterByQuery(qo, query);
        }

        // Filter by statuses if supplied.
        if (!Util.isEmpty(listCtx.getStatuses())) {
            qo.add(Filter.in("summaryStatus", listCtx.getStatuses()));
        }
        if (!Util.isEmpty(listCtx.getExcludedStatuses())) {
            qo.add(Filter.not(Filter.in("summaryStatus", listCtx.getExcludedStatuses())));
        }

        // Hard-code this if the client doesn't pass up any sorting.
        if (Util.isEmpty(qo.getOrderings())) {
            qo.setOrderBy(getOrderBy());
        }

        return qo;
    }

    /**
     * Add the correct filter implementing the query string based on the cert type, including necessary joins
     * @param qo QueryOptions
     * @param query query string
     */
    private void addFilterByQuery(QueryOptions qo, String query) {
        /* Role Comp is not an identity type, but it still needs to be searched by targetDisplayName */
        if (certificationType.isObjectType() && !certificationType.getEntityType().equals(CertificationEntity.Type.BusinessRole)) {
            // Only add this join if we need it for the query, otherwise we hide entities for deleted MA's
            // TODO: We really should be using display names stored in the CertificationEntity
            qo.add(Filter.join("targetId", "ManagedAttribute.id"));
            qo.add(Filter.ignoreCase(Filter.like("ManagedAttribute.displayableName", query, Filter.MatchMode.START)));
        }
        else {
            qo.add(Filter.or(Filter.ignoreCase(Filter.like("targetDisplayName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("firstname", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("lastname", query, Filter.MatchMode.START))));
        }
    }

    /**
     * Get the correct default orderby based on the cert type.
     * @return orderby column name
     */
    private String getOrderBy() {
        switch (certificationType) {
            case DataOwner:
                return "identity";
            case AccountGroupMembership:
            case AccountGroupPermissions:
                return "accountGroup";
            case BusinessRoleComposition:
                return "targetDisplayName";
            default:
                return "identity";
        }
    }

}
