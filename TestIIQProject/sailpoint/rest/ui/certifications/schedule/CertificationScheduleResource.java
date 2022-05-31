package sailpoint.rest.ui.certifications.schedule;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ObjectResult;
import sailpoint.object.EmailTemplate;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.object.Scope;
import sailpoint.object.Tag;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.certification.schedule.CertificationScheduleAccountListFilterContext;
import sailpoint.service.certification.schedule.CertificationScheduleDTO;
import sailpoint.service.certification.schedule.CertificationScheduleIdentityListFilterContext;
import sailpoint.service.certification.schedule.CertificationScheduleService;
import sailpoint.service.certification.schedule.CertificationScheduleAdditionalEntitlementsListFilterContext;
import sailpoint.service.certification.schedule.CertificationScheduleRoleListFilterContext;
import sailpoint.service.certification.schedule.CertificationScheduleTargetPermissionListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

/**
 * Resource implementation of the CertificationSchedule endpoints.
 * This is work for the new CertificationSchedule pages.
 * @author brian.li
 *
 */
@Path(Paths.CERTIFICATION_SCHEDULE)
public class CertificationScheduleResource extends BaseResource{

    /**
     * Gets a single certificationScheduleDTO based off of a task schedule id or certification group id.
     * Used a query param because Tomcat does not like having encoded forward slashes without
     * server modification. Our default naming for creating cert schedules will often
     * include slashes in the dates.
     *
     * If neither taskScheduleId nor certificationGroupId are specified, will throw exception.
     **
     * @param taskScheduleId ID of the task schedule for this certification schedule.
     * @param certificationGroupId ID of the certification group id
     * @return ObjectResult with CertificationScheduleDTO as the object and metadata set.
     * @throws GeneralException
     */
    @GET
    public ObjectResult getCertSchedule(@QueryParam("taskScheduleId") String taskScheduleId, @QueryParam("certificationGroupId") String certificationGroupId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
        CertificationScheduleService service = new CertificationScheduleService(this);
        CertificationScheduleDTO dto;
        if (!Util.isNullOrEmpty(taskScheduleId)) {
            dto = service.getCertificationScheduleDTO(taskScheduleId);
        } else if (!Util.isNullOrEmpty(certificationGroupId)) {
            dto = service.getCertificationScheduleDTOFromCertificationGroup(certificationGroupId);
        } else {
            throw new InvalidParameterException("taskScheduleId or certificationGroupId is required");
        }

        ObjectResult objectResult = new ObjectResult(dto);
        objectResult.setMetaData(service.getMetaData(dto));

        return objectResult;
    }

    /**
     * Gets a new CertificationScheduleDTO either based on system defaults, or based on an existing certification group ID
     * @param certificationGroupId ID of the certification group to use as template. Optional.
     * @return ObjectResult with CertificationScheduleDTO as the object and metadata set.
     *
     * @throws GeneralException
     */
    @GET
    @Path("new")
    public ObjectResult getNewCertSchedule(@QueryParam("certificationGroupId") String certificationGroupId) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));
        CertificationScheduleService service = new CertificationScheduleService(this);
        CertificationScheduleDTO dto = service.getDefaultCertificationScheduleDTO(getLoggedInUser(), certificationGroupId);

        ObjectResult objectResult = new ObjectResult(dto);
        objectResult.setMetaData(service.getMetaData(dto));

        return objectResult;
    }

    /**
     * Saves a CertificationDefinition object based on the passed in DTO from the client.
     *
     * @param data JSON representation of the DTO.
     * @return Response object
     * @throws GeneralException
     */
    @POST
    public Response saveCertDefinition(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));


        CertificationScheduleDTO dto = new CertificationScheduleDTO(data);
        CertificationScheduleService service = new CertificationScheduleService(this);
        Map<String, List<String>> errors = service.saveCertificationSchedule(dto);
        
        if (!Util.isEmpty(errors)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(errors).build();
        } else {
            return Response.ok().build();
        }
    }

    /**
     * Get the basic suggest resource for certification schedule.
     * @return SuggestResource.
     * @throws GeneralException
     */
    @Path("suggest")
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext
            .add(Rule.class.getSimpleName())
            .add(GroupDefinition.class.getSimpleName())
            .add(EmailTemplate.class.getSimpleName())
            .add(Identity.class.getSimpleName())
            .add(Tag.class.getSimpleName())
            .add(Scope.class.getSimpleName());
        return new SuggestResource(this, authorizerContext);
    }


    /**
     * Get the available filters for identities. Used in entity panel.
     * @return List of ListFilterDTO for all filterable attributes on identities.
     * @throws GeneralException
     */
    @Path("identityFilters")
    @GET
    public List<ListFilterDTO> getIdentityFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
        CertificationScheduleIdentityListFilterContext listFilterContext = new CertificationScheduleIdentityListFilterContext();
        String suggestUrl = getMatchedUri().replace("identityFilters", "identityFiltersSuggest");
        listFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), listFilterContext).getListFilters(true);
    }

    /**
     * Pass through to suggest resource for the identity filters, allows only the filters in {@link #getIdentityFilters()}
     * @throws GeneralException
     */
    @Path("identityFiltersSuggest")
    public SuggestResource getIdentityFiltersSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getIdentityFilters()));
    }

    /**
     * Gets the filters for the certification schedule UI for Roles.
     * Used in the item panel
     * @return A list of available filters for Roles
     * @throws GeneralException
     */
    @Path("roleFilters")
    @GET
    public List<ListFilterDTO> getRoleFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
        CertificationScheduleRoleListFilterContext roleFilterContext = new CertificationScheduleRoleListFilterContext();
        String suggestUrl = getMatchedUri().replace("roleFilters", "roleFiltersSuggest");
        roleFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), roleFilterContext).getListFilters(true);
    }

    /**
     * Pass through to suggest resource for the role filters, allows only the filters in {@link #getRoleFilters()}
     * @throws GeneralException
     */
    @Path("roleFiltersSuggest")
    public SuggestResource getRoleFiltersSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getRoleFilters()));
    }

    /**
     * Gets the filters for the certification schedule UI for Additional Entitlements.
     * Used in the item panel
     * @return A list of available filters for Additional Entitlements
     * @throws GeneralException
     */
    @Path("additionalEntitlementsFilters")
    @GET
    public List<ListFilterDTO> getAdditionalEntitlementsFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
        CertificationScheduleAdditionalEntitlementsListFilterContext additionalEntitlementsFilterContext =
                new CertificationScheduleAdditionalEntitlementsListFilterContext();
        String suggestUrl = getMatchedUri().replace("additionalEntitlementsFilters", "additionalEntitlementsFiltersSuggest");
        additionalEntitlementsFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), additionalEntitlementsFilterContext).getListFilters(true);
    }

    /**
     * Pass through to suggest resource for the additional entitlement filters,
     * allows only the filters in {@link #getAdditionalEntitlementsFilters()}
     * @throws GeneralException
     */
    @Path("additionalEntitlementsFiltersSuggest")
    public SuggestResource getAdditionalEntitlementsFiltersSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getAdditionalEntitlementsFilters()));
    }

    /**
     * Gets the filters for the certification schedule UI for Accounts.
     * Used in the item panel
     * @return A list of available filters for Accounts
     * @throws GeneralException
     */
    @Path("accountFilters")
    @GET
    public List<ListFilterDTO> getAccountFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
        CertificationScheduleAccountListFilterContext linkFilterContext = new CertificationScheduleAccountListFilterContext();
        String suggestUrl = getMatchedUri().replace("accountFilters", "accountFiltersSuggest");
        linkFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), linkFilterContext).getListFilters(true);
    }

    /**
     * Pass through to suggest resource for the account filters, allows only the filters in {@link #getAccountFilters()}
     * @throws GeneralException
     */
    @Path("accountFiltersSuggest")
    public SuggestResource getAccountFiltersSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getAccountFilters()));
    }

    /**
     * Gets the filters for the certification schedule UI for Target Permissions.
     * Used in the item panel
     * @return A list of available filters for Target Permissions
     * @throws GeneralException
     */
    @Path("targetPermissionFilters")
    @GET
    public List<ListFilterDTO> getTargetPermissionFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
        CertificationScheduleTargetPermissionListFilterContext filterContext = new CertificationScheduleTargetPermissionListFilterContext();
        String suggestUrl = getMatchedUri().replace("targetPermissionFilters", "targetPermissionFiltersSuggest");
        filterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), filterContext).getListFilters(true);
    }

    /**
     * Pass through to suggest resource for the account filters, allows only the filters in {@link #getAccountFilters()}
     * @throws GeneralException
     */
    @Path("targetPermissionFiltersSuggest")
    public SuggestResource getTargetPermissionFiltersSuggest() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessCertificationSchedule));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getTargetPermissionFilters()));
    }

}
