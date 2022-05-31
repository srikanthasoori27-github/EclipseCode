/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.ListResult;
import sailpoint.service.SessionStorage;
import sailpoint.service.form.object.DynamicValuesOptions;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.FormBean;
import sailpoint.web.UserContext;
import sailpoint.web.util.WebUtil;

/**
 * Service used to process the dynamic allowed values for a suggest field
 * in a form.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class DynamicValuesService extends FormService{

    /**
     * The log instance.
     */
    private static final Log log = LogFactory.getLog(DynamicValuesService.class);

    /**
     * Constructs a new instance of DynamicValuesService.
     *
     * @param userContext The user context.
     * @param sessionStorage The session storage.
     */
    public DynamicValuesService(UserContext userContext, SessionStorage sessionStorage) {
        super(userContext, sessionStorage);
    }

    /**
     * Calculates a page of dynamic allowed values based on the specified options.
     *
     * @param options The options.
     * @return The list result.
     * @throws GeneralException
     */
    public ListResult calculateAllowedValues(DynamicValuesOptions options) throws GeneralException {
        if (options == null) {
            throw new GeneralException("No dynamic value options specified.");
        }

        // if no field name specified return empty result
        if (Util.isNullOrEmpty(options.getFieldName())) {
            return ListResult.getInstance();
        }

        // instantiate form bean and form renderer
        FormBean formBean = getFormBean(options);
        FormRenderer formRenderer = getFormRenderer(formBean, options);

        // if no renderer then return empty result
        if (formRenderer == null) {
            log.error("Unable to locate form containing the dynamic field");
            return ListResult.getInstance();
        }

        // assimilate whatever was posted
        formRenderer.setData(options.getFormData());
        formRenderer.populateForm();

        Object allowed = calculateFieldValues(formBean, formRenderer, options.getFieldName());

        List<Map<String, String>> results = parseResults(allowed ,options.getQuery());
        List<Map<String, String>> resultPage = pageResults(results, options);

        return new ListResult(resultPage, Util.size(results));
    }

    /**
     * Creates the FormBean instance using the specified options.
     *
     * @param options The options.
     * @return The form bean.
     * @throws GeneralException
     */
    private FormBean getFormBean(DynamicValuesOptions options) throws GeneralException {
        FormBean formBean = instantiateFormBean(options);

        // not real happy with this... another option would be to add
        // setSessionStorage method to FormBean interface
        if (formBean instanceof BaseFormStore) {
            BaseFormStore formStore = (BaseFormStore) formBean;
            formStore.setSessionStorage(sessionStorage);
        }

        return formBean;
    }

    /**
     * Calculates the dynamic allowed values for the specified field.
     *
     * @param formBean The form bean.
     * @param formRenderer The form renderer.
     * @param fieldName The field name.
     * @return The allowed values object.
     * @throws GeneralException
     */
    private Object calculateFieldValues(FormBean formBean, FormRenderer formRenderer, String fieldName)
        throws GeneralException {

        return formRenderer.calculateAllowedValues(fieldName, formBean.getFormArguments());
    }

    /**
     * Parses the results out of the allowed values including filtering by the specified query.
     *
     * @param allowed The allowed values object.
     * @param query The query.
     * @return The result list.
     */
    private List<Map<String, String>> parseResults(Object allowed, String query) {
        // Result needs to be converted into a List of Maps where
        // the map contains at least an entry named "id" that has
        // the value to post and an optional "displayName" that has
        // the value to display.  Be tolerant of bad rule results.
        List<Map<String, String>> results = null;

        //This will create a list of all the values from the Rule or Script
        //including adjusting the list based on whatever query string is past
        //in for typeAhead.  if there is no query string it will be the entire
        //list.  It currently does a contains on the lowercase for the comparison
        //We are not sorting this list, we presume we are handed the
        //list from the rule the way the customer expects it
        if (allowed instanceof List) {
            results = new ArrayList<Map<String, String>>();
            for (Object el : (List)allowed) {
                String id = null;
                String displayName = null;
                if (el instanceof List) {
                    List stuff = (List)el;
                    if (stuff.size() > 0) {
                        Object o = stuff.get(0);
                        if (o != null)
                            id = o.toString();
                        if (stuff.size() > 1) {
                            o = stuff.get(1);
                            if (o != null)
                                displayName = o.toString();
                        }
                    }
                }
                else if (el != null) {
                    id = el.toString();
                }

                if (id != null) {
                    // strip any malicious tags, then allow common DN characters
                    String safeId = allowCommonDNChars(WebUtil.sanitizeHTML(id));
                    String safeDisplayName = allowCommonDNChars(WebUtil.sanitizeHTML(displayName));

                    Map<String,String> item = new HashMap<String,String>();
                    item.put("id", safeId);

                    if (displayName != null){
                        item.put("displayName", safeDisplayName);
                    } else {
                        item.put("displayName", safeId);
                        displayName = id; //doing this for the comparison below
                    }

                    if (null == query || displayName.toLowerCase().contains(query.toLowerCase())) {
                        results.add(item);
                    }

                }
            }
        }

        return results;
    }

    /**
     * Extracts the page of results based on the parameters specified in the options.
     *
     * @param resultSet The full result set.
     * @param options The options.
     * @return The page of results.
     */
    private List<Map<String, String>> pageResults(List<Map<String, String>> resultSet, DynamicValuesOptions options) {
        List<Map<String, String>> pageResults = new ArrayList<Map<String, String>>();

        if (resultSet == null) {
            return pageResults;
        }

        // this is where we take the list and pair it down based on
        // what paging information passed in
        String startStr = options.getStart();
        String limitStr = options.getLimit();
        int count = resultSet.size();

        if (null != limitStr) {
            int start = 0;
            if (!Util.isNullOrEmpty(startStr)) {
                start = Integer.parseInt(startStr);
            }

            int limit = 0;
            if (!Util.isNullOrEmpty(limitStr)) {
                limit = Integer.parseInt(limitStr);
            }

            int end = count;

            if (start + limit < count){
                end = start + limit;
            }

            for (int i = start; i < end; i++){
                pageResults.add(resultSet.get(i));
            }
        } else {
            pageResults = resultSet;
        }

        return pageResults;
    }

    /**
     * Un-escapes HTML encoded chars in strings returned by WebUtil.sanitizeHTML().
     *
     * @param str The string to un-escape
     * @return {String} with allowed chars.
     */
    private String allowCommonDNChars(String str) {
        if (str != null) {
            return str.replace("&#61;", "=")
                    .replace("&#43;", "+")
                    .replace("&#35;", "#")
                    .replace("&#60;", "<")
                    .replace("&#62;", ">");
        }
        return null;
    }

}
