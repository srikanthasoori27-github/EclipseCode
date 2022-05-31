package sailpoint.service.certification.schedule;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Link;
import sailpoint.service.certification.schedule.CertificationScheduleBaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * LiftFilterContext implementation for generating filters for Accounts when scheduling a certification.
 * @author brian.li
 *
 */
public class CertificationScheduleAccountListFilterContext 
        extends CertificationScheduleBaseListFilterContext {

    public static final String ATT_APPLICATION = "application";
    public static final String ATT_INSTANCE = "instance";
    public static final String ATT_NATIVE_IDENTITY = "nativeIdentity";
    public static final String ATT_DISPLAY_NAME = "displayName";
    public static final String ATT_MANUALLY_CORRELATED = "manuallyCorrelated";
    public static final String ATT_ENTITLEMENTS = "entitlements";
    public static final String ATT_LAST_REFRESH = "lastRefresh";
    public static final String ATT_LAST_TARGET_AGGREGATION = "lastTargetAggregation";


    public CertificationScheduleAccountListFilterContext() {
        super(Link.class);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context,
            Locale locale) throws GeneralException {
        List<ListFilterDTO> filters = super.getDefaultFilters(context, locale);
        ListFilterDTO appFilter = new ListFilterDTO(ATT_APPLICATION, MessageKeys.UI_CERTIFICATION_SCHEDULE_APPLICATION,
                true, ListFilterDTO.DataTypes.Application, locale);
        ListFilterDTO instanceFilter = new ListFilterDTO(ATT_INSTANCE, MessageKeys.UI_CERTIFICATION_SCHEDULE_INSTANCE, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO nativeIdFilter = new ListFilterDTO(ATT_NATIVE_IDENTITY, MessageKeys.UI_CERTIFICATION_SCHEDULE_NATIVE_IDENTITY, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO displayNameFilter = new ListFilterDTO(ATT_DISPLAY_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_DISPLAY_NAME, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO manuallyCorrelatedFilter = new ListFilterDTO(ATT_MANUALLY_CORRELATED, MessageKeys.UI_CERTIFICATION_SCHEDULE_IS_MANUALLY_CORRELATED, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO entitlementsFilter = new ListFilterDTO(ATT_ENTITLEMENTS, MessageKeys.UI_CERTIFICATION_SCHEDULE_HAS_ENTITLEMENTS, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO lastRefreshFilter = new ListFilterDTO(ATT_LAST_REFRESH, MessageKeys.UI_CERTIFICATION_SCHEDULE_LAST_REFRESH, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO lastTargetAggregationFilter = new ListFilterDTO(ATT_LAST_TARGET_AGGREGATION, MessageKeys.UI_CERTIFICATION_SCHEDULE_LAST_TARGET_AGGREGATION, false, ListFilterDTO.DataTypes.Date, locale);

        filters.addAll(Arrays.asList(appFilter, instanceFilter, nativeIdFilter, displayNameFilter, manuallyCorrelatedFilter, entitlementsFilter,
                lastRefreshFilter, lastTargetAggregationFilter));

        addSpecialFilterOptions(filters, locale);

        return filters;
    }

}
