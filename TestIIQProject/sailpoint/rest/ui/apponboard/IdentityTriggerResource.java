/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rest.ui.apponboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Duration;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterDTO.SelectItem;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue.Operation;
import sailpoint.service.querybuilder.QueryBuilderIdentityListFilterContext;
import sailpoint.service.querybuilder.QueryBuilderService;
import sailpoint.service.querybuilder.QueryBuilderService.DataTypes;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

@Path(Paths.IDENTITY_TRIGGERS)
public class IdentityTriggerResource extends BaseResource {

    @Path("operations")
    @GET
    public Map<String, List<SelectItem>> getOperationsByDataType() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup, SPRight.FullAccessRapidSetupConfiguration,
                SPRight.ViewRapidSetupConfiguration, SPRight.ViewRapidSetup));

        Map<String, List<SelectItem>> result = new HashMap<>();
        Map<DataTypes, List<Operation>> operationMap = QueryBuilderService.getOperationsByDataType();

        for(DataTypes dataType : operationMap.keySet()) {
            ArrayList<SelectItem> operations = new ArrayList<>();

            for(Operation operation : operationMap.get(dataType)) {
                operations.add(new SelectItem(operation, getLocale()));
            }

            result.put(dataType.toString(), operations);
        }

        return result;
    }

    @Path("durations")
    @GET
    public List<SelectItem> getDurations() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup,  SPRight.FullAccessRapidSetupConfiguration,
                SPRight.ViewRapidSetupConfiguration, SPRight.ViewRapidSetup));

        List<SelectItem> result = new ArrayList<>();
        List<Duration.Scale> excluded = new ArrayList<Duration.Scale>() {{
            add(Duration.Scale.Millisecond);
            add(Duration.Scale.Second);
            add(Duration.Scale.Minute);
            add(Duration.Scale.Hour);
        }};

        for (Duration.Scale duration : Duration.Scale.values()) {
            if (!excluded.contains(duration)) {
                result.add(new SelectItem(duration, getLocale()));
            }
        }

        return result;
    }

    /**
     * Gets a list of Identity attributes (as filters).
     * @return A list of available filters for Identity attributes
     * @throws GeneralException persistence context
     */
    @Path("identityAttributeFilters")
    @GET
    public List<ListFilterDTO> getIdentityAttributeFilters() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup, SPRight.FullAccessRapidSetupConfiguration,
                SPRight.ViewRapidSetupConfiguration, SPRight.ViewRapidSetup));
        QueryBuilderIdentityListFilterContext identityAttributeFilterContext =
                new QueryBuilderIdentityListFilterContext();
        String suggestUrl = getMatchedUri().replace("identityAttributeFilters", "identityAttributeFiltersSuggest");
        identityAttributeFilterContext.setSuggestUrl(suggestUrl);
        return new ListFilterService(getContext(), getLocale(), identityAttributeFilterContext).getListFilters();
    }

    /**
     * Pass through to suggest resource for the identity attribute filters,
     * allows only the filters in {@link #getIdentityAttributeFilters()}
     * @throws GeneralException persistence context
     */
    @Path("identityAttributeFiltersSuggest")
    public SuggestResource getIdentityAttributeFiltersSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessRapidSetup, SPRight.FullAccessRapidSetupConfiguration));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getIdentityAttributeFilters()));
    }
}
