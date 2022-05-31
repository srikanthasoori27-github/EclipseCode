/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Collections;
import java.util.List;

import sailpoint.authorization.AuthorizationUtility;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.service.form.FormService;
import sailpoint.service.form.object.FormOptions;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.extjs.Component;
import sailpoint.web.util.Sorter;

/**
 * Bean for suggests on ExtJS forms. These used to go through the "classic" SuggestResource at /rest/suggest
 * but that left things completely unauthorized. Instead we need to use the FormBean to authorize that the
 * object and column they want matches the field. To do this, we need to use a bean instead of REST so that the
 * FormBean can be correctly instantiated, similar to DynamicFieldBean
 */
public class FormSuggestBean extends BaseBean implements ClassicSuggestService.ClassicSuggestServiceContext, ClassicManagedAttributeSuggestService.ClassicManagedAttributeSuggestServiceContext {

    private int start;
    private int limit;
    private String query;
    private String sortBy;
    private String sortDirection;
    private String filterString;
    private String suggestContext;
    private String suggestId;
    private String suggestClass;
    private String suggestColumn;
    private String fieldName;

    /**
     * Get the JSON suggest results
     * @return JSON string of suggest results.
     * @throws GeneralException
     * @throws ClassNotFoundException
     */
    public String getSuggestObjectsJSON() throws GeneralException, ClassNotFoundException {
        // Load the things off the request
        processRequestParameters();

        // Authorize that the object/column matches what the field says.
        // The FormBean should authorize against the object in most cases.
        authorize();

        ClassicSuggestService suggestService = new ClassicSuggestService(this);
        ListResult result;
        if (Util.isNothing(suggestColumn)) {
            if (ManagedAttribute.class.getSimpleName().equals(this.suggestClass)) {
                // SuggestService cannot handle MA's so we have to use this.
                ClassicManagedAttributeSuggestService managedAttributeSuggestService = new ClassicManagedAttributeSuggestService(this);
                result = managedAttributeSuggestService.getManagedAttributeSuggestResult();
            } else {
                result = suggestService.getObjectSuggestResult();
            }
        } else {
            // no value for exclude so it will use default behavior of true
            result = suggestService.getColumnSuggestResult(suggestColumn, false, null, true);
        }

        return JsonHelper.toJson(result);
    }

    private void processRequestParameters() {
        this.start = Util.atoi(getRequestParameter("start"));
        this.limit = getResultLimit();
        this.query = getRequestParameter("query");
        this.sortBy = getRequestParameter("sort");
        this.sortDirection = getRequestParameter("dir");
        this.filterString = getRequestParameter("filter");
        this.suggestContext = getRequestParameter("context");
        this.suggestId = getRequestParameter("suggestId");
        this.suggestClass = getRequestParameter("suggestClass");
        this.suggestColumn = getRequestParameter("suggestColumn");
        this.fieldName = getRequestParameter("fieldName");
    }

    private void authorize() throws GeneralException {
        FormOptions options = new FormOptions(getRequestParam());

        // Find the field so we can get the type of it to authorize the suggest against
        FormService formService = new FormService(this, null);
        FormBean formBean = formService.instantiateFormBean(options);
        FormRenderer formRenderer = formService.getFormRenderer(formBean, options);
        Field field = formRenderer.getForm().getField(this.fieldName);
        if (field == null) {
            throw new GeneralException("Invalid field!");
        }

        // If the field has a sailpoint type class, add it to the authorizer. Otherwise it will fail authorization.
        Class spClass = field.getTypeClass();
        String className = null;
        if (spClass != null) {
            className = spClass.getSimpleName();
        } else if (Field.TYPE_MANAGED_ATTR.equals(field.getType())) {
            // Handled specially since MA's cant go through normal SuggestService.
            className = Field.TYPE_MANAGED_ATTR;
        } else if ("entitlementselector".equals(Util.otos(field.getAttribute(Component.PROPERTY_XTYPE)))) {
            // renders as EntitlementSelector.js, which uses Application suggest
            className = Application.class.getSimpleName();
        }

        String objectColumn = (String)field.getAttribute(Field.ATTR_VALUE_OBJ_COLUMN);
        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        if (objectColumn == null) {
            authorizerContext.add(className);
        } else {
            authorizerContext.add(className, false, objectColumn);
        }

        // Do the authorization against the expected class and column
        AuthorizationUtility.authorize(this, new SuggestAuthorizer(authorizerContext, this.suggestClass, this.suggestColumn));
    }

    @Override
    public String getSuggestClass() {
       return this.suggestClass;
    }

    @Override
    public String getFilterString() {
        return this.filterString;
    }

    @Override
    public int getStart() {
        return this.start;
    }

    @Override
    public int getLimit() {
        return this.limit;
    }

    @Override
    public String getQuery() {
        return this.query;
    }

    @Override
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) {
        if (this.sortBy != null) {
            return Collections.singletonList(new Sorter(this.sortBy, this.sortDirection, false));
        }

        return null;
    }

    @Override
    public String getGroupBy() {
        return null;
    }

    @Override
    public List<Filter> getFilters() {
        return null;
    }

    @Override
    public String getSuggestContext() {
        return this.suggestContext;
    }

    @Override
    public String getSuggestId() {
        return this.suggestId;
    }

    @Override
    public String getSortBy() {
        return this.sortBy;
    }

    @Override
    public String getSortDirection() {
        return this.sortDirection;
    }

    @Override
    public String getRequestParameter(String name) {
        return super.getRequestParameter(name);
    }

    @Override
    public String getRequesteeId() {
        return null;
    }
}
