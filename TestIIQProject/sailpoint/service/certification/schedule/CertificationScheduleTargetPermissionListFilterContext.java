/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification.schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Target;
import sailpoint.object.TargetAssociation;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class CertificationScheduleTargetPermissionListFilterContext extends CertificationScheduleBaseListFilterContext {

    public static final String APPLICATION = "application";
    public static final String TARGET_FULL_PATH = "target.fullPath";
    public static final String TARGET_UNIQUE_NAME_HASH = "target.uniqueNameHash";
    public static final String TARGET_NAME = "target.name";
    public static final String TARGET_DISPLAY_NAME = "target.displayName";
    public static final String TARGET_HOST = "target.targetHost";
    public static final String INHERITED = "inherited";

    private List<Filter> applicationFilters;

    public CertificationScheduleTargetPermissionListFilterContext() {
        super(TargetAssociation.class);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {

        ListFilterDTO appFilter = new ListFilterDTO(APPLICATION, MessageKeys.UI_CERTIFICATION_SCHEDULE_APPLICATION, true, ListFilterDTO.DataTypes.Application, locale);
        appFilter.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, getSuggestUrl());
        ListFilterDTO fullPathFilter = new ListFilterDTO(TARGET_FULL_PATH, MessageKeys.UI_CERTIFICATION_SCHEDULE_TARGET_FULL_PATH, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO nameFilter = new ListFilterDTO(TARGET_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_TARGET_NAME, true, ListFilterDTO.DataTypes.Column, locale);
        nameFilter.configureColumnSuggest(Target.class.getSimpleName(), "name", null, getSuggestUrl());
        ListFilterDTO displayNameFilter = new ListFilterDTO(TARGET_DISPLAY_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_TARGET_DISPLAY_NAME, true, ListFilterDTO.DataTypes.Column, locale);
        displayNameFilter.configureColumnSuggest(Target.class.getSimpleName(), "displayName", null, getSuggestUrl());
        ListFilterDTO hostFilter = new ListFilterDTO(TARGET_HOST, MessageKeys.UI_CERTIFICATION_SCHEDULE_TARGET_HOST, true, ListFilterDTO.DataTypes.Column, locale);
        hostFilter.configureColumnSuggest(Target.class.getSimpleName(), "targetHost", null, getSuggestUrl());
        ListFilterDTO inheritedFilter = new ListFilterDTO(INHERITED, MessageKeys.UI_CERTIFICATION_SCHEDULE_INHERITED, false, ListFilterDTO.DataTypes.Boolean, locale);

        List<ListFilterDTO> filterDTOS = Arrays.asList(appFilter, fullPathFilter, nameFilter, displayNameFilter, hostFilter, inheritedFilter);

        // Add these directly. We don't want to go through addSpecialFilterOptions because we dont want our string filter to be a suggest
        // Also our colum suggests are based on Target object, not TargetAssociation, which is our top level clazz
        addOperations(filterDTOS, locale);

        return filterDTOS;
    }

    private void addOperations(List<ListFilterDTO> filterDTOS, Locale locale) {
        for (ListFilterDTO filterDTO : Util.iterate(filterDTOS)) {
            addFilterOperations(filterDTO, locale);
        }
    }

    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        // Cant query on the fullPath itself, have to hash it and query on that.
        if (TARGET_FULL_PATH.equals(filterDTO.getProperty())) {
            return getSimpleFilter(TARGET_UNIQUE_NAME_HASH, Target.createUniqueHash(value), operation);
        }

        return super.convertFilterSingleValue(filterDTO, value, operation, context);
    }

    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter = super.convertFilter(filterDTO, filterValue, context);
        // This is weird, right? Application filter is a special case, it is not used when querying TargetAssociations but instead on filtering
        // Application table directly (see CertifiableAnalyzer for details)
        // So if we come across application filters, we want to store them separately in the CertificationDefinition. Strip them from the "normal"
        // converted filter and store for later fetching.
        if (filter != null && APPLICATION.equals(filterDTO.getProperty())) {
            if (this.applicationFilters == null) {
                this.applicationFilters = new ArrayList<>();
            }
            this.applicationFilters.add(filter);
            return null;
        }
        return filter;
    }

    @Override
    protected Filter getApplicationFilter(String property, String value, ListFilterValue.Operation operation) {
        // Application filter will be directly on Application table, so override to just use name property in filter.
        return getSimpleFilter("name", value, operation);
    }

    public void reset() {
        this.applicationFilters = null;
    }

    public Filter getApplicationFilter() {
        Filter filter = null;
        int size = Util.size(this.applicationFilters);
        if (size > 0) {
            filter = (size == 1) ? this.applicationFilters.get(0) : Filter.and(this.applicationFilters);
        }
        return filter;
    }
}
