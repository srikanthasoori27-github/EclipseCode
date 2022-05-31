/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.classification;

import javax.ws.rs.GET;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.SailPointObject;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.classification.ClassificationListService;
import sailpoint.tools.GeneralException;

/**
 * List resource for Classification endpoints.
 * Currently used for managedAttributeDetailsResource and roleDetailsResource
 * @author brian.li
 *
 */
public class ClassificationListResource extends BaseListResource implements BaseListServiceContext {

    /**
     * The classifiable object id whether it is a Bundle or MA
     */
    private String classifiableId;

    /**
     * The specific class that implements Classifiable
     */
    private Class<? extends SailPointObject> clazz;
    
    /**
     * Constructor for the ClassificationListResource
     *
     * Due to a weird glassfish warning on some environments, putting a wildcard or type on the Class
     * causes a warning that the class cannot be resolved to a concrete type.
     *
     * Hence the need to suppress both unchecked and rawtype warnings. However these will be safe due to
     * the check that makes sure the class extends SailPointObject
     * @param clazz The Classifiable Class that contains classifications
     * @param id The ID of the Classifiable
     * @param parent The parent BaseResource
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public ClassificationListResource(@SuppressWarnings("rawtypes") Class clazz, String id, BaseResource parent)
            throws GeneralException {
        super(parent);

        if (!SailPointObject.class.isAssignableFrom(clazz)) {
            throw new GeneralException(String.format("Class %s is not a SailPointObject for use of this resource",
                    clazz.getSimpleName()));
        }

        // Implicit unchecked cast here that casts it to <? extends SailPointObject>
        this.clazz = clazz;
        this.classifiableId = id;
    }

    /**
     * Public GET method to retrieve a ListResult of ClassificationDTOs
     * @return ListResult<ClassificationDTO> of the current page of dtos
     * @throws GeneralException
     */
    @GET
    public ListResult getClassifications() throws GeneralException {
        // Proper auth checks will be done in the parent resources
        authorize(new AllowAllAuthorizer());

        ClassificationListService service = getService();
        return service.getClassifications(this.clazz, this.classifiableId);
    }

    private ClassificationListService getService() {
        BaseListResourceColumnSelector columnSelector = new BaseListResourceColumnSelector(UIConfig.UI_CLASSIFICATIONS_COLUMNS);
        return new ClassificationListService(getContext(), this, columnSelector);
    }

}
