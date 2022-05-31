package sailpoint.service.certification.schedule;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.service.listfilter.CollectionConditionFilterHandler;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * ListFilterContext for generating filters for AdditionalEntitlements when scheduling a certification.
 * @author brian.li
 *
 */
public class CertificationScheduleAdditionalEntitlementsListFilterContext
        extends CertificationScheduleBaseListFilterContext {

    public static final String ATT_APPLICATION = "application";
    public static final String ATT_OWNER = "owner";
    public static final String ATT_TYPE = "type";
    public static final String ATT_ATTRIBUTE = "attribute";
    public static final String ATT_VALUE = "value";
    public static final String ATT_DISPLAY_NAME = "displayName";
    public static final String ATT_REQUESTABLE = "requestable";
    public static final String ATT_AGGREGATED = "aggregated";
    public static final String ATT_UNCORRELATED = "uncorrelated";
    public static final String ATT_LAST_REFRESH = "lastRefresh";
    public static final String ATT_LAST_TARGET_AGGREGATION = "lastTargetAggregation";
    // effective access
    public static final String ATT_TARGET_NAME = "associations.targetName";
    public static final String ATT_CLASSIFICATIONS = "classifications.classification.name";

    private CollectionConditionFilterHandler classificationFilterHandler;

    public CertificationScheduleAdditionalEntitlementsListFilterContext() {
        super(ManagedAttribute.class);
        this.classificationFilterHandler = new CollectionConditionFilterHandler(ManagedAttribute.class, ATT_CLASSIFICATIONS);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context,
            Locale locale) throws GeneralException {
        List<ListFilterDTO> filters = super.getDefaultFilters(context, locale);
        ListFilterDTO appFilter = new ListFilterDTO(ATT_APPLICATION, MessageKeys.UI_CERTIFICATION_SCHEDULE_APPLICATION, true, ListFilterDTO.DataTypes.Application, locale);
        ListFilterDTO ownerFilter = new ListFilterDTO(ATT_OWNER, MessageKeys.UI_CERTIFICATION_SCHEDULE_OWNER, true, ListFilterDTO.DataTypes.Identity, locale);
        ListFilterDTO typeFilter = new ListFilterDTO(ATT_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_TYPE, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO attrFilter = new ListFilterDTO(ATT_ATTRIBUTE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ATTRIBUTE, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO valueFilter = new ListFilterDTO(ATT_VALUE, MessageKeys.UI_CERTIFICATION_SCHEDULE_VALUE, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO displayNameFilter = new ListFilterDTO(ATT_DISPLAY_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_DISPLAY_NAME, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO requestableFilter = new ListFilterDTO(ATT_REQUESTABLE, MessageKeys.UI_CERTIFICATION_SCHEDULE_REQUESTBALE, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO aggregatedFilter = new ListFilterDTO(ATT_AGGREGATED, MessageKeys.UI_CERTIFICATION_SCHEDULE_AGGREGATED, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO uncorrelatedFilter = new ListFilterDTO(ATT_UNCORRELATED, MessageKeys.UI_CERTIFICATION_SCHEDULE_UNCORRELATED, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO lastRefreshFilter = new ListFilterDTO(ATT_LAST_REFRESH, MessageKeys.UI_CERTIFICATION_SCHEDULE_LAST_REFRESH, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO lastTargetAggregationFilter = new ListFilterDTO(ATT_LAST_TARGET_AGGREGATION, MessageKeys.UI_CERTIFICATION_SCHEDULE_LAST_TARGET_AGGREGATION, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO effectiveAccess = new ListFilterDTO(ATT_TARGET_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_EFFECTIVE_ACCESS, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO classificationsFilter = new ListFilterDTO(ATT_CLASSIFICATIONS, MessageKeys.UI_CERTIFICATION_SCHEDULE_CLASSIFICATIONS, true, ListFilterDTO.DataTypes.Object, locale);
        classificationsFilter.configureObjectSuggest(Classification.class.getSimpleName(), getSuggestUrl());
        filters.addAll(Arrays.asList(appFilter, ownerFilter, typeFilter, attrFilter, valueFilter, displayNameFilter, requestableFilter,
                aggregatedFilter, uncorrelatedFilter, lastRefreshFilter, lastTargetAggregationFilter, effectiveAccess, classificationsFilter));

        addSpecialFilterOptions(filters, locale);

        return filters;
    }

    @Override
    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        if (ATT_CLASSIFICATIONS.equals(filterDTO.getProperty())) {
            List<String> classificationNames = getValueStringList(filterValue);
            return this.classificationFilterHandler.convertFilter(filterValue, classificationNames);
        }

        return super.convertFilter(filterDTO, filterValue, context);
    }

    @Override
    public List<Filter> postProcessFilters(List<Filter> filters) throws GeneralException {
        return super.postProcessFilters(this.classificationFilterHandler.postProcessFilters(filters));
    }
}
