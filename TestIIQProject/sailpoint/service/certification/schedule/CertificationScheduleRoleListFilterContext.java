package sailpoint.service.certification.schedule;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.service.listfilter.CollectionConditionFilterHandler;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * ListFilterContext for generating filters for Roles when scheduling a certification.
 * @author brian.li
 *
 */
public class CertificationScheduleRoleListFilterContext extends
        CertificationScheduleBaseListFilterContext {

    public static final String ATT_NAME = "name";
    public static final String ATT_OWNER = "owner";
    public static final String ATT_RISK_SCORE_WEIGHT = "riskScoreWeight";
    public static final String ATT_DISABLED = "disabled";
    public static final String ATT_DISPLAY_NAME = "displayName";
    public static final String ATT_TYPE = "type";
    public static final String ATT_ACTIVATION_DATE = "activationDate";
    public static final String ATT_DEACTIVATION_DATE = "deactivationDate";
    public static final String ATT_ASSIGNED_COUNT = "roleIndex.assignedCount";
    public static final String ATT_DETECTED_COUNT = "roleIndex.detectedCount";
    public static final String ATT_ASSOCIATED_TO_ROLE = "roleIndex.associatedToRole";
    public static final String ATT_ENTITLEMENT_COUNT = "roleIndex.entitlementCount";
    public static final String ATT_ENTITLEMENT_COUNT_INHERITANCE = "roleIndex.entitlementCountInheritance";
    public static final String ATT_LAST_CERTIFIED_MEMBERSHIP = "roleIndex.lastCertifiedMembership";
    public static final String ATT_LAST_CERTIFIED_COMPOSITION = "roleIndex.lastCertifiedComposition";
    public static final String ATT_CLASSIFICATIONS = "classifications.classification.name";

    private CollectionConditionFilterHandler classificationFilterHandler;

    public CertificationScheduleRoleListFilterContext() {
        super(Bundle.class);

        this.classificationFilterHandler = new CollectionConditionFilterHandler(Bundle.class, ATT_CLASSIFICATIONS);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context,
            Locale locale) throws GeneralException {
        List<ListFilterDTO> filters = super.getDefaultFilters(context, locale);

        ListFilterDTO nameFilter = new ListFilterDTO(ATT_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_NAME, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO ownerFilter = new ListFilterDTO(ATT_OWNER, MessageKeys.UI_CERTIFICATION_SCHEDULE_OWNER, true, ListFilterDTO.DataTypes.Identity, locale);
        ListFilterDTO riskScoreWeightFilter = new ListFilterDTO(ATT_RISK_SCORE_WEIGHT, MessageKeys.UI_CERTIFICATION_SCHEDULE_RISK_SCORE_WEIGHT, false, ListFilterDTO.DataTypes.Number, locale);
        ListFilterDTO disabledFilter = new ListFilterDTO(ATT_DISABLED, MessageKeys.UI_CERTIFICATION_SCHEDULE_DISABLED, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO displayNameFilter = new ListFilterDTO(ATT_DISPLAY_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_DISPLAY_NAME, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO typeFilter = new ListFilterDTO(ATT_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_TYPE, true, ListFilterDTO.DataTypes.String, locale);
        ListFilterDTO activationDateFilter = new ListFilterDTO(ATT_ACTIVATION_DATE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ACTIVATION_DATE, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO deactivationDateFilter = new ListFilterDTO(ATT_DEACTIVATION_DATE, MessageKeys.UI_CERTIFICATION_SCHEDULE_DEACTIVATION_DATE, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO assignedCountFilter = new ListFilterDTO(ATT_ASSIGNED_COUNT, MessageKeys.UI_CERTIFICATION_SCHEDULE_ASSIGNED_COUNT, false, ListFilterDTO.DataTypes.Number, locale);
        ListFilterDTO detectedCountFilter = new ListFilterDTO(ATT_DETECTED_COUNT, MessageKeys.UI_CERTIFICATION_SCHEDULE_DETECTED_COUNT, false, ListFilterDTO.DataTypes.Number, locale);
        ListFilterDTO associatedToRoleFilter = new ListFilterDTO(ATT_ASSOCIATED_TO_ROLE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ASSOCIATED_TO_ROLE, false, ListFilterDTO.DataTypes.Boolean, locale);
        ListFilterDTO entitlementCountFilter = new ListFilterDTO(ATT_ENTITLEMENT_COUNT, MessageKeys.UI_CERTIFICATION_SCHEDULE_ENTITLEMENT_COUNT, false, ListFilterDTO.DataTypes.Number, locale);
        ListFilterDTO entitlementCountInheritanceFilter = new ListFilterDTO(ATT_ENTITLEMENT_COUNT_INHERITANCE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ENTITLEMENT_COUNT_INHERITANCE, true, ListFilterDTO.DataTypes.Number, locale);
        ListFilterDTO lastCertifiedMembershipFilter = new ListFilterDTO(ATT_LAST_CERTIFIED_MEMBERSHIP, MessageKeys.UI_CERTIFICATION_SCHEDULE_LAST_CERTIFIED_MEMBERSHIP, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO lastCertifiedCompositionFilter = new ListFilterDTO(ATT_LAST_CERTIFIED_COMPOSITION, MessageKeys.UI_CERTIFICATION_SCHEDULE_LAST_CERTIFIED_COMPOSITION, false, ListFilterDTO.DataTypes.Date, locale);
        ListFilterDTO classificationsFilter = new ListFilterDTO(ATT_CLASSIFICATIONS, MessageKeys.UI_CERTIFICATION_SCHEDULE_CLASSIFICATIONS, true, ListFilterDTO.DataTypes.Object, locale);
        classificationsFilter.configureObjectSuggest(Classification.class.getSimpleName(), getSuggestUrl());

        filters.addAll(Arrays.asList(nameFilter, ownerFilter, riskScoreWeightFilter, disabledFilter, displayNameFilter, typeFilter, activationDateFilter, deactivationDateFilter,
                assignedCountFilter, detectedCountFilter, associatedToRoleFilter, entitlementCountFilter, entitlementCountInheritanceFilter, lastCertifiedMembershipFilter, lastCertifiedCompositionFilter, classificationsFilter));

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
