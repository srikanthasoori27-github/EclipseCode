/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.listfilter.ListFilterDTO.SelectItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterHelper;

/**
 * Implementation of ListFilterContext for policy violations
 */
public class PolicyViolationListFilterContext extends BaseListFilterContext {

    public static final String IDENTITY_PREFIX = "identity.";
    public static final String POLICY_PREFIX = "Policy.";

    // Policy violation filters
    private static final String TYPE_FILTER = "type";
    private static final String STATUS_FILTER = "status";
    public static final String INCLUDED_STATUS_FILTER = "includedStatus";
    private static final String ID_FILTER = "id";

    // Identity filters
    private static final String FIRST_NAME_FILTER = "firstname";
    private static final String LAST_NAME_FILTER = "lastname";
    private static final String IDENTITY_FILTER = "identity";

    private static final String IDENTITY_SUGGEST_ID = "policyViolations_identity";

    /**
     * Constructor 
     */
    public PolicyViolationListFilterContext() {
        super(null);
    }

    /**
     * Overriding to get the default filters for policy violations
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {

        // Policy Type
        ListFilterDTO typeFilter = new ListFilterDTO(POLICY_PREFIX + TYPE_FILTER, MessageKeys.LABEL_POLICY_TYPE, true, ListFilterDTO.DataTypes.String, locale);
        List<ListFilterDTO.SelectItem> typeItems = this.getPolicyTypes(context, locale);
        typeFilter.setAllowedValues(typeItems);

        // Policy violation status
        ListFilterDTO statusFilter = new ListFilterDTO(STATUS_FILTER, MessageKeys.LABEL_STATUS, true, ListFilterDTO.DataTypes.String, locale);
        List<ListFilterDTO.SelectItem> statusItems = new ArrayList<>();
        statusItems.add(new SelectItem(PolicyViolation.Status.Delegated, locale));
        statusItems.add(new SelectItem(PolicyViolation.Status.Mitigated, locale));
        statusItems.add(new SelectItem(PolicyViolation.Status.Open, locale));
        statusItems.add(new SelectItem(PolicyViolation.Status.Remediated, locale));
        statusFilter.setAllowedValues(statusItems);

        // Policy Violation id (limits page to a single policy violation)
        ListFilterDTO violationIdFilter = new ListFilterDTO(ID_FILTER, MessageKeys.POLICY_VIOLATION_ID, false, ListFilterDTO.DataTypes.String, locale);

        // Identity attributes
        ListFilterDTO firstNameFilter = new ListFilterDTO(IDENTITY_PREFIX + FIRST_NAME_FILTER, MessageKeys.FIRST_NAME, true, ListFilterDTO.DataTypes.Column, locale);
        firstNameFilter.configureColumnSuggest(Identity.class.getSimpleName(), FIRST_NAME_FILTER, null, getSuggestUrl());

        ListFilterDTO lastNameFilter = new ListFilterDTO(IDENTITY_PREFIX + LAST_NAME_FILTER, MessageKeys.LAST_NAME, true, ListFilterDTO.DataTypes.Column, locale);
        lastNameFilter.configureColumnSuggest(Identity.class.getSimpleName(), LAST_NAME_FILTER, null, getSuggestUrl());

        ListFilterDTO identityFilter = new ListFilterDTO(IDENTITY_FILTER, MessageKeys.IDENTITY, true, ListFilterDTO.DataTypes.Identity, locale);
        identityFilter.configureSuggest(IdentityFilter.GLOBAL_FILTER, IDENTITY_SUGGEST_ID, getSuggestUrl());

        return Arrays.asList(statusFilter, typeFilter, firstNameFilter, lastNameFilter, identityFilter, violationIdFilter);
    }

    /**
     * Override filter conversion to add a join to the Policy table
     */
    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context)
        throws GeneralException {

        Filter filter;
        if (value == null) {
            filter = FilterHelper.getNullFilter(filterDTO.getProperty());
        } else {
            filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
        }

        if (this.isPolicyFilter(filterDTO.getProperty())) {
            filter = Filter.and(Filter.join("PolicyViolation.policyId", "Policy.id"), filter); 
        }

        return filter;
    }

    /**
     * Override this to handle some custom filters we need for the policy violation tabs
     */
    @Override
    public Filter convertFilter(String propertyName, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter;
        if (INCLUDED_STATUS_FILTER.equals(propertyName)) {
            filter = Filter.in("status", getValueStringList(filterValue));
        } else {
            filter = super.convertFilter(propertyName, filterValue, context);
        }

        return filter;
    }

    /**
     * Get the list of types for policy templates.
     * @param context SailPointContext
     * @param locale Locale
     * @return List of SelectItems with the policy types
     * @throws GeneralException
     */
    private List<SelectItem> getPolicyTypes(SailPointContext context, Locale locale) throws GeneralException {

        List<SelectItem> items = new ArrayList<SelectItem>();

        List<Policy> templates = getPolicyTemplates(context);
        if (templates != null) {
            for (Policy p : templates) {
                String type = p.getType();
                String typeKey = p.getTypeKey();
                // if there is a type key use it instead
                String descriptionKey = Util.isNotNullOrEmpty(typeKey) ? typeKey : type;
                items.add(new SelectItem(new Message(descriptionKey).getLocalizedMessage(locale, null), type));
            }
        }
        // Sort the policy types by display name
        Collections.sort(items, new Comparator<SelectItem>() {
            @Override
            public int compare(SelectItem o1, SelectItem o2) {
                return Util.nullSafeCompareTo(o1.getDisplayName(), o2.getDisplayName());
            }
        });
        return items;
    }

    /**
     * Get the list of policy templates.
     * @param context SailPointContext
     * @return List<Policy> List of Policy objects that are templates
     * @throws GeneralException
     */
    private List<Policy> getPolicyTemplates(SailPointContext context) throws GeneralException {

        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("template", new Boolean(true)));
        return context.getObjects(Policy.class, options);
    }

    /**
     * Return whether the given filter is searching on a policy property.
     */
    private boolean isPolicyFilter(String property) {
        return property != null && property.startsWith(POLICY_PREFIX);
    }

}
