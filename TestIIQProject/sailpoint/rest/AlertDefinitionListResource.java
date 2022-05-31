/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.object.Workflow;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.alert.AlertDefinitionDTO;
import sailpoint.service.alert.AlertDefinitionListService;
import sailpoint.service.alert.AlertDefinitionService;
import sailpoint.service.alert.CreateAlertException;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;

/**
 * Created by ryan.pickens on 9/1/16.
 */
@Path("alertDefinitions")
public class AlertDefinitionListResource extends BaseListResource implements BaseListServiceContext {

    private static Log _log = LogFactory.getLog(AlertDefinitionListResource.class);

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.ALERT_DEF_COLUMNS;
    }

    @GET
    public ListResult getAlertDefinitions() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewAlertDefinition, SPRight.FullAccessAlertDefinition));

        QueryOptions ops = getQueryOptions(getColumnKey());

        if (Util.isNotNullOrEmpty(query)) {
            ops.add(Filter.or(
                    Filter.eq("id", query),
                    Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START))
            ));
        }

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(getColumnKey());
        AlertDefinitionListService svc = new AlertDefinitionListService(getContext(), this, selector);
        return svc.getAlertDefinitions(ops, this);
    }

    @Path("{alertDefId}")
    public AlertDefinitionResource getAlertDefinition(@PathParam("alertDefId") String alertDefId)
        throws GeneralException {
        return new AlertDefinitionResource(alertDefId, this);
    }

    @POST
    public Response create(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition));

        try {
            String json = JsonHelper.toJson(data);
            AlertDefinitionDTO alertDefDto = JsonHelper.fromJson(AlertDefinitionDTO.class, json);
            AlertDefinitionService svc = getService();
            AlertDefinitionDTO newDto = svc.createAlertDefinition(alertDefDto);
            if (newDto != null) {
                return Response.created(null).entity(newDto).build();
            } else {
                _log.warn("Unable to create AlertDefinition");
                return Response.serverError().build();
            }

        } catch(CreateAlertException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessages()).build();
        } catch(Exception e) {
            _log.warn("Unable to Create AlertDefinition" + data, e);
            //Throw the exception and let the exception mapper  build response
            throw new GeneralException(e);
        }
    }

    protected AlertDefinitionService getService() {
        return new AlertDefinitionService(this);
    }

    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition));

        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext
                .add(Rule.class.getSimpleName())
                .add(IdentityTrigger.class.getSimpleName())
                .add(Workflow.class.getSimpleName())
                .add(EmailTemplate.class.getSimpleName())
                .add(Identity.class.getSimpleName());
        return new SuggestResource(this, authorizerContext);
    }

}
