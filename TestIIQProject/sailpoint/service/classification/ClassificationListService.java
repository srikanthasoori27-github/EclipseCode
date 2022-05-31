/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.classification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.tools.Util;
import sailpoint.object.Classifiable;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

/**
 * List service for the ClassificationListResource.
 *
 * This service will accept a {@link Classifiable} implemented Class and
 * get the classifications for that Classifiable.
 * @author brian.li
 *
 */
public class ClassificationListService extends BaseListService<BaseListServiceContext> {

    public ClassificationListService(SailPointContext context, BaseListServiceContext listServiceContext,
            ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    /**
     * Gets the classification for a passed in Classifiable id and corresponding class
     * @param clazz The underlying actual SailPointObject that implemented Classifiable
     * @param classifiableId The ID of the Classifiable
     * @return A ListResult of ClassificationDTOs that represents the current page of results
     * @throws GeneralException
     */
    public ListResult getClassifications(Class<? extends SailPointObject> clazz, String classifiableId) throws GeneralException {
        if (!Classifiable.class.isAssignableFrom(clazz)) {
            throw new GeneralException(String.format("Class %s does not implement Classifiable for use of this service",
                    clazz.getSimpleName()));
        }

        List<ClassificationDTO> dtos = new ArrayList<>(this.getListServiceContext().getLimit());

        QueryOptions ops = new QueryOptions(Filter.eq("id", classifiableId));
        ops.add(Filter.eq("classifications.ownerType", clazz.getSimpleName()));
        int totalSize = ObjectUtil.countDistinctAttributeValues(getContext(), clazz, ops, "classifications.classification.id");

        if (totalSize > 0) {
            // Apply the ordering and pagination
            handleOrdering(ops);
            handlePagination(ops);

            //Only get distinct Classifications
            ops.setDistinct(true);
            // Process the current page of classification ids and turn them into DTOs for the front end
            Iterator<Object[]> ids = getContext().search(clazz, ops, "classifications.classification.id");
            if (ids != null) {
                while (ids.hasNext()) {
                    String id = (String) ids.next()[0];
                    if (Util.isNotNullOrEmpty(id)) {
                        Classification c = getContext().getObjectById(Classification.class, id);
                        if (c != null) {
                            dtos.add(new ClassificationDTO(c, getListServiceContext()));
                        }
                    }
                }
            }
        }

        return new ListResult(dtos, totalSize);
    }

}
