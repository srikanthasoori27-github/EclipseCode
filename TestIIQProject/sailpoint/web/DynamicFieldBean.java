/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.service.form.DynamicValuesService;
import sailpoint.service.form.object.DynamicValuesOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/**
 * A JSF bean that provides a REST service to return dynamic allowed values for
 * a field in a Form.
 * 
 * Note that this would ideally be a JAX-RS resource, but currently has to
 * instantiate and use JSF beans (ie - the FormBeans) to do its work.  Because
 * of this, it is much easier for this to run as a JSF bean rather than a
 * JAX-RS resource so the FormBeans can run in their normal environment.
 * Eventually, we should try to separate the required form logic out of the
 * FormBeans so that this could become a JAX-RS resource again.
 */
public class DynamicFieldBean extends BaseBean {

    private static final Log log = LogFactory.getLog(DynamicFieldBean.class);
    
    /**
     * Return the JSON of a ListResult with the allowed values for a dynamic
     * field.
     */
    @SuppressWarnings("unchecked")
    public String getDynamicFieldValuesJSON() throws GeneralException {

        authorize(new AllowAllAuthorizer());

        DynamicValuesOptions options = new DynamicValuesOptions();
        options.setFieldName(getRequestParameter("fieldName"));
        options.setFormData(getRequestParameter("data"));
        options.setFormBeanClass(getRequestParameter("formBeanClass"));
        options.setFormBeanStateString(getRequestParameter("formBeanState"));
        options.setFormId(getRequestParameter("formId"));
        options.setStart(getRequestParameter("start"));
        options.setLimit(Util.itoa(getResultLimit()));
        options.setQuery(getRequestParameter("query"));

        // ok to pass null here because classic FormBean instances have no
        // notion of the SessionStorage interface
        DynamicValuesService valuesService = new DynamicValuesService(this, null);

        ListResult result = valuesService.calculateAllowedValues(options);

        return JsonHelper.toJson(result);
    }
}
