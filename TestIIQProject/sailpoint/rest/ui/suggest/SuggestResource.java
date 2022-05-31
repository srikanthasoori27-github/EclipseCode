package sailpoint.rest.ui.suggest;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ManagedAttributesResource;
import sailpoint.rest.ui.Paths;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.suggest.SuggestAuthorizer;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestService;
import sailpoint.service.suggest.SuggestServiceOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * Root resource for suggest components.
 */
@Path(Paths.SUGGEST)
public class SuggestResource extends BaseSuggestResource {
    private static Log log = LogFactory.getLog(SuggestResource.class);

    public SuggestResource() {
        super();
    }

    public SuggestResource(BaseResource parent) {
        super(parent);
    }

    public SuggestResource(BaseResource parent, SuggestAuthorizerContext authorizerContext) {
        super(parent, authorizerContext);
    }

    public SuggestResource(BaseResource parent, SuggestAuthorizerContext authorizerContext, boolean exclude) {
        super(parent, authorizerContext, exclude);
    }

    /**
     * Returns a json data feed for a suggest component for the given
     * class type.
     * @param suggestClass SailPoint object simple class name
     * @return ListResult that contains the list of objects that are of the class that was requested
     * and that match the 'query' param
     * @throws GeneralException
     */
    @GET
    @Path("object/{class}")
    public ListResult getSuggestViaHttpGet(@PathParam("class") String suggestClass)
            throws GeneralException {
       return getSuggest(suggestClass);
    }

    /**
     * Returns a json data feed for a suggest component for the given
     * class type.  This method was added to address IIQMAG-1339.  This issue with only supporting
     * http GET is that the query parameters were getting to large.  This was causing errors due to
     * the URL getting to long.
     * @param suggestClass SailPoint object simple class name
     * @param inputs the post data may contain start, limit, query, sortBy, sortDirection, and filterString.
     * @return ListResult that contains the list of objects that are of the class that was requested
     * and that match the 'query' param
     * @throws GeneralException
     */
    @POST
    @Path("object/{class}")
    public ListResult getSuggestViaHttpPost(@PathParam("class") String suggestClass, Map<String, Object> inputs)
            throws GeneralException {
        this.start = Util.otoi(inputs.get(PARAM_START));
        this.limit = WebUtil.getResultLimit(Util.otoi(inputs.get(PARAM_LIMIT)));
        this.query = (String)inputs.get(PARAM_QUERY);
        this.sortBy = (String)inputs.get(PARAM_SORT);
        this.sortDirection = (String)inputs.get(PARAM_DIR);
        this.filterString = (String)inputs.get(PARAM_FILTER_STRING);
        this.extraParamsMap = (Map)inputs.get(PARAM_EXTRA_PARAMS);
        this.extraParams = getExtraParamsFromMap(extraParamsMap);

        return getSuggest(suggestClass);
    }

    private ListResult getSuggest(String suggestClass)
            throws GeneralException {

        authorize(new AllowAllAuthorizer(), new SuggestAuthorizer(this.authorizerContext, suggestClass));

        this.suggestClass = suggestClass;
        SuggestService suggestService = new SuggestService(this);

        // Punt to the identity suggest resource for identities and to the
        // managed attribute resource for managed attributes
        Class<?> cls = suggestService.getSuggestClass(suggestClass);
        if (cls == null) {
            throw new InvalidParameterException("Invalid suggest class!");
        }

        try {
            if(cls.equals(Identity.class)) {
                IdentitySuggestResource identitySuggestResource = new IdentitySuggestResource(this);
                return identitySuggestResource.getIdentities();
            } else if (cls.equals(ManagedAttribute.class)) {
                ManagedAttributesResource managedAttributesResource = new ManagedAttributesResource(this);
                return managedAttributesResource.getManagedAttrSuggestResult(start, limit, query, sortBy, sortDirection, filterString);
            } else if (cls.equals(Application.class)) {
                ApplicationSuggestResource applicationSuggestResource = new ApplicationSuggestResource(this);
                return applicationSuggestResource.getApplications();
            }

            return suggestService.getObjects(getQueryOptions());
        } catch(GeneralException ge) {
            log.error("Exception while retrieving suggest: " + ge.getMessage(), ge);
            return new ListResult(new HashMap());
        }
    }

    /**
     * Returns a list of results for a suggest containing distinct column values on a given class type
     * @param suggestClass SailPoint object simple class name
     * @param suggestColumn Column/property name on the class
     * @return ListResult that contains list of values that match the query and extra params
     * @throws GeneralException
     * TODO: work this out with quicklinkName
     */
    @GET
    @Path("column/{class}/{column}")
    public ListResult getColumnSuggestViaHttpGet(@PathParam("class") String suggestClass,
                                                 @PathParam("column") String suggestColumn)
            throws GeneralException, ClassNotFoundException {
        return getColumnSuggest(suggestClass, suggestColumn);
    }

    /**
     * Returns a list of results for a suggest containing distinct column values on a given class type.
     * This method was added to address IIQMAG-1339.  The issue with only supporting http GET is that the
     * query parameters were getting to large.  This was causing errors due to the URL getting to long.
     * @param suggestClass SailPoint object simple class name
     * @param suggestColumn Column/property name on the class
     * @param inputs the post data may contain start, limit, query, sortBy, sortDirection, and filterString.
     * @return ListResult that contains list of values that match the query and extra params
     * @throws GeneralException
     * TODO: work this out with quicklinkName
     */
    @POST
    @Path("column/{class}/{column}")
    public ListResult getColumnSuggestViaHttpPost(@PathParam("class") String suggestClass,
                                                  @PathParam("column") String suggestColumn, Map<String, Object> inputs)
    throws GeneralException, ClassNotFoundException {
        this.start = Util.otoi(inputs.get(PARAM_START));
        this.limit = WebUtil.getResultLimit(Util.otoi(inputs.get(PARAM_LIMIT)));
        this.query = (String)inputs.get(PARAM_QUERY);
        this.sortBy = (String)inputs.get(PARAM_SORT);
        this.sortDirection = (String)inputs.get(PARAM_DIR);
        this.filterString = (String)inputs.get(PARAM_FILTER_STRING);

        this.extraParamsMap = (Map)inputs.get(PARAM_EXTRA_PARAMS);
        this.extraParams = getExtraParamsFromMap(extraParamsMap);

        return getColumnSuggest(suggestClass, suggestColumn);
    }

    private ListResult getColumnSuggest(String suggestClass, String suggestColumn)
            throws GeneralException, ClassNotFoundException {
        authorize(new AllowAllAuthorizer(), new SuggestAuthorizer(this.authorizerContext, suggestClass, suggestColumn));

        this.suggestClass = suggestClass;
        SuggestService suggestService = new SuggestService(this);

        Map<String, Object> extraParamMap = getExtraParamMap();

        return suggestService.getColumnValues(SuggestServiceOptions.getInstance().
                setColumn(suggestColumn).
                setDirection(Util.getString(extraParamMap, ListFilterDTO.ATTR_SUGGEST_DIRECTION)).
                setLcm(Util.getBoolean(extraParamMap, ListFilterDTO.ATTR_SUGGEST_IS_LCM)).
                setLcmAction(Util.getString(extraParamMap, ListFilterDTO.ATTR_SUGGEST_LCM_ACTION)).
                setLcmQuicklinkName(Util.getString(extraParamMap, ListFilterDTO.ATTR_SUGGEST_LCM_QUICKLINK)).
                setTargetIdentityId(Util.getString(extraParamMap, ListFilterDTO.ATTR_SUGGEST_TARGET_ID)).
                setExclude(exclude));
    }

    @Path("applications")
    public ApplicationSuggestResource getApplicationSuggestResource() throws GeneralException {
        return new ApplicationSuggestResource(this);
    }
}