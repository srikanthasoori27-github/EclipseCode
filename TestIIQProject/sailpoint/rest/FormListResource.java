/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * FormListResource.
 *
 * The resource which handles forms.
 */
@Path("forms")
public class FormListResource extends BaseListResource {

    /**
     * Get forms from the database.
     * If arguments are null, then returns all the forms in the db.
     * If name parameter is passed, filters forms based on like filter.
     *
     * @param nameFilter - filter parameter for formName
     * @param typeFilter - filter parameter for formType
     * @return - list of forms in ListResult format
     * @throws GeneralException
     */
    @GET
    public ListResult list(@QueryParam("type") String typeFilter,
            @QueryParam("name") String nameFilter) throws GeneralException {

        if (Util.isNullOrEmpty(typeFilter)) {
            authorize(new RightAuthorizer(SPRight.FullAccessForms));
        }
        else if (typeFilter.equals(Form.Type.Application.toString())) {
            authorize(new RightAuthorizer(SPRight.ViewApplication,SPRight.ManageApplication));
        }
        else if (typeFilter.equals(Form.Type.Role.toString())) {
            authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
        }
        else if (typeFilter.equals(Form.Type.Workflow.toString())) {
            authorize (new RightAuthorizer(SPRight.FullAccessWorkflows));
        }
        else {
            throw new GeneralException((new Message(MessageKeys.INVALID_FORM_TYPE).getLocalizedMessage()));
        }
        QueryOptions qo = getQueryOptions(UIConfig.FORMS_COLUMNS);
        if (Util.isNotNullOrEmpty(typeFilter)) {
            qo.add(Filter.eq("type", typeFilter));
        } else {
            qo.add(Filter.eq("hidden", false));

            // Add filter for form type as Application, Workflow, Role and form which don't have type
            Configuration conf = getContext().getConfiguration();
            if (null != conf) {
                @SuppressWarnings("unchecked")
                List<String> filter = conf.getList(Configuration.FORMS_TYPE_FILTER);
                qo.add(Filter.or(Filter.in("type", filter), Filter.isnull("type")));
            }
        }
        if (Util.isNotNullOrEmpty(nameFilter)) {
            qo.add(Filter.ignoreCase(Filter.like("name", nameFilter)));
        }
        int total = countResults(Form.class, qo);
        List<Map<String, Object>> results = getResults(UIConfig.FORMS_COLUMNS, Form.class, qo);
        return new ListResult(results, total);
    }
}
