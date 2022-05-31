/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.pam;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Target;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

import static sailpoint.tools.Util.otoi;

public class PamPrivilegedDataSuggestService {

    private static Log log = LogFactory.getLog(PamPrivilegedDataSuggestService.class);

    private SailPointContext context;
    private Identity loggedInUser;
    private QueryOptions queryOptions;

    private static final String PD_VALUE = "privilegedData.value";

    public PamPrivilegedDataSuggestService(SailPointContext context, Identity loggedInUser,String containerId,
                                           Map<String, Object> requestInputs) throws GeneralException {
        this.context = context;
        this.loggedInUser = loggedInUser;
        if (requestInputs == null) {
            requestInputs = new HashMap<>();
        }
        initQueryOptions(containerId, requestInputs);
    }

    /**
     * Get all the privileged items that can be added to the given container.
     *
     * The results will be all privileged data managed attributes on the same application as the container.
     * If a "query" query param was provided it will be treated as a starts with search on display name.
     * A privilegedItemSelectorRule can be used to provide additional filters to the search.
     * @return
     * @throws GeneralException
     */
    public Iterator<ManagedAttribute> getPrivilegedItemsForContainer() throws GeneralException {
        return context.search(ManagedAttribute.class, queryOptions);
    }

    public int getPrivilegedItemsForContainerCount() throws GeneralException {
        return context.countObjects(ManagedAttribute.class, queryOptions);
    }

    private void initQueryOptions(String containerId, Map<String, Object> requestInputs) throws GeneralException {
        Target container = context.getObjectById(Target.class, containerId);
        if (container == null) {
            throw new GeneralException("No container found");
        }
        Application app = PamUtil.getApplicationForTarget(context, container);
        if (app == null) {
            throw new GeneralException("No application found for container");
        }
        // get the current container PD values so we can exclude them from the list
        ManagedAttribute containerMA =
                ManagedAttributer.get(context, app.getId(), false, null, container.getNativeObjectId(), ContainerService.OBJECT_TYPE_CONTAINER);
        if (containerMA == null) {
            throw new GeneralException("No container managed attribute found for container");
        }

        List<String> values = sailpoint.tools.Util.otol(containerMA.getAttribute(PD_VALUE));
        queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("application.id", app.getId()));
        queryOptions.add(Filter.eq("type", "PrivilegedData"));
        if (!Util.isEmpty(values)) {
            queryOptions.add(Filter.not(Filter.in("value", values)));
        }
        if (Util.isNotNullOrEmpty((String) requestInputs.get("query"))) {
            queryOptions.add(Filter.like("displayableName", requestInputs.get("query"), Filter.MatchMode.START));
        }
        Filter ruleFilter = getPrivilegedItemSelectorFilter(app, containerMA);
        if (ruleFilter != null) {
            queryOptions.add(ruleFilter);
        }

        if (requestInputs.get("limit") != null) {
            queryOptions.setResultLimit(WebUtil.getResultLimit(otoi(requestInputs.get("limit"))));
        }
        queryOptions.setFirstRow(otoi(requestInputs.get("start")));
        queryOptions.setDistinct(true);
        queryOptions.add(Filter.notnull("value"));
        queryOptions.addOrdering("displayableName", true, true);
    }

    private Filter getPrivilegedItemSelectorFilter(Application application, ManagedAttribute container)
            throws GeneralException {
        Filter selectorFilter = null;
        String ruleName = Configuration.getSystemConfig().getString(Configuration.PAM_PRIVILEGED_ITEM_SELECTOR_RULE);
        if (ruleName != null) {
            Rule rule = context.getObjectByName(Rule.class, ruleName);
            if (rule == null) {
                log.error("Invalid rule name: " + ruleName);
            } else {
                Map<String, Object> inputs = new HashMap<String, Object>();
                inputs.put("requester", loggedInUser);
                inputs.put("application", application);
                inputs.put("container", container);

                Object result = context.runRule(rule, inputs);
                if (result instanceof Filter) {
                    selectorFilter = (Filter) result;
                }
                else {
                    log.debug("Invalid response from privileged item selector rule: " + result);
                }
            }
        }
        return selectorFilter;
    }
}
