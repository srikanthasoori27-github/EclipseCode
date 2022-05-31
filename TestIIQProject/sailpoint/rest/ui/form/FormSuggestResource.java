/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.form;

import sailpoint.integration.ListResult;
import sailpoint.object.Field;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.SessionStorage;
import sailpoint.service.form.BaseFormStore;
import sailpoint.service.form.FormService;
import sailpoint.service.form.object.FormOptions;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

import java.util.Map;

/**
 * Override of suggest resource to use with forms. We do this so we can use the POST
 * data for authorized of the object type being queried. Only object suggests using POST
 * are supported here.
 */
public class FormSuggestResource extends SuggestResource {

    private static final String FIELD_NAME_INPUT = "fieldName";

    private SessionStorage sessionStorage;

    public FormSuggestResource(BaseResource parent, SessionStorage sessionStorage) {
        super(parent);

        this.sessionStorage = sessionStorage;
    }

    @Override
    public ListResult getSuggestViaHttpPost(String suggestClass, Map<String, Object> inputs) throws GeneralException {
        String fieldName = (String)inputs.get(FIELD_NAME_INPUT);
        if (Util.isNothing(fieldName)) {
            throw new InvalidParameterException(FIELD_NAME_INPUT);
        }

        // Authorize against the form options
        FormOptions formOptions = new FormOptions(inputs);
        authorize(formOptions);

        // Find the field so we can get the type of it to authorize the suggest against
        FormService formService = new FormService(this, this.sessionStorage);
        Field field = formService.getField(formOptions, fieldName);
        if (field == null) {
            throw new InvalidParameterException(FIELD_NAME_INPUT);
        }

        // If the field has a sailpoint type class, add it to the authorizer. Otherwise it will fail authorization.
        Class spClass = field.getTypeClass();
        BaseSuggestAuthorizerContext baseSuggestAuthorizerContext = new BaseSuggestAuthorizerContext();
        if (spClass != null) {
            baseSuggestAuthorizerContext.add(spClass.getSimpleName());
        } else if (Field.TYPE_MANAGED_ATTR.equals(field.getType())) {
            // Ugh, MA is still handled weirdly and does not get returned from getTypeClass, this is no longer
            // necessary but not changing it now.
            baseSuggestAuthorizerContext.add(ManagedAttribute.class.getSimpleName());
        }

        // Set the class level authorizer before calling real method.
        this.authorizerContext = baseSuggestAuthorizerContext;

        return super.getSuggestViaHttpPost(suggestClass, inputs);
    }

    @Override
    public ListResult getSuggestViaHttpGet(String suggestClass) throws GeneralException {
        return ListResult.getInstance();
    }

    @Override
    public ListResult getColumnSuggestViaHttpGet(String suggestClass, String suggestColumn) throws GeneralException {
        return ListResult.getInstance();
    }

    @Override
    public ListResult getColumnSuggestViaHttpPost(String suggestClass, String suggestColumn , Map<String, Object> inputs)
            throws GeneralException, ClassNotFoundException {
        return ListResult.getInstance();
    }

    protected void authorize(FormOptions options) throws GeneralException {
        BaseFormStore formStore = FormService.storeFromOptions(options, this, this.sessionStorage);
        authorize(formStore.getAuthorizer(false));
    }
}
