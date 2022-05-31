/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationItem;
import sailpoint.object.Classification;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of ListFilterContext for certification item filters
 */
public class IdentityCertEntityListFilterContext extends CertificationEntityListFilterContext {

    /**
     * Filter String for the column suggests for the certification. Limits to Identities that are part of the certification.
     */
    protected static final String IDENTITY_FILTER_STRING_FORMAT = "name.join(CertificationEntity.identity) && CertificationEntity.certification.id == \"%s\"";

    private static final String ROLE_FILTER = "businessRole";
    private static final String CHANGES_DETECTED_FILTER = "changesDetected";
    private static final String ADDITIONAL_ENTITLEMENTS_DETECTED_FILTER = "hasAdditionalEntitlements";
    private static final String POLICY_VIOLATIONS_DETECTED_FILTER = "hasPolicyViolations";

    /**
     * Constructor
     *
     * @param certificationId ID of the certification
     */
    public IdentityCertEntityListFilterContext(String certificationId) {
        super(certificationId);
    }

    /**
     * Overriding to get some identity default filters.
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOList = super.getDefaultFilters(context, locale);

        // Changes Detected
        ListFilterDTO changesDetectedFilter = new ListFilterDTO(CHANGES_DETECTED_FILTER, MessageKeys.CHANGES_DETECTED, false, ListFilterDTO.DataTypes.String, locale);
        List<ListFilterDTO.SelectItem> changesDetectedValues = new ArrayList<>();
        for (CertificationItemDTO.ChangesDetected changesDetected : CertificationItemDTO.ChangesDetected.values()) {
            changesDetectedValues.add(new ListFilterDTO.SelectItem(changesDetected, locale));
        }
        changesDetectedFilter.setAllowedValues(changesDetectedValues);

        // Has additional entitlements
        ListFilterDTO entitlementsFilter = new ListFilterDTO(
                ADDITIONAL_ENTITLEMENTS_DETECTED_FILTER, MessageKeys.UI_ADDITIONAL_ENTITLEMENTS_DETECTED, false, ListFilterDTO.DataTypes.Boolean, locale);

        // Has policy violations
        ListFilterDTO violationsFilter = new ListFilterDTO(
                POLICY_VIOLATIONS_DETECTED_FILTER, MessageKeys.UI_POLICY_VIOLATIONS_DETECTED, false, ListFilterDTO.DataTypes.Boolean, locale);

        // Role Name
        ListFilterDTO roleFilter = new ListFilterDTO(
                ROLE_FILTER, MessageKeys.UI_ROLE_ATTRIBUTE_NAME, false, ListFilterDTO.DataTypes.String, locale);
        roleFilter.configureColumnSuggest(
                CertificationItem.class.getSimpleName(), TARGET_DISPLAY_NAME, String.format(CERT_ITEM_FILTER_STRING_FORMAT, getCertificationId()), getSuggestUrl());

        ListFilterDTO classificationFilter = null;
        if (getCertificationDefinition(context).isIncludeClassifications()) {
            classificationFilter = new ListFilterDTO(CLASSIFICATION_FILTER, MessageKeys.UI_CLASSIFICATIONS, false, ListFilterDTO.DataTypes.Object, locale);
            classificationFilter.configureObjectSuggest(Classification.class.getSimpleName(), getSuggestUrl());
        }

        List<ListFilterDTO> certItemPropertyFilters = new ArrayList<>(Arrays.asList(changesDetectedFilter, entitlementsFilter, violationsFilter, roleFilter));
        if (classificationFilter != null) {
            certItemPropertyFilters.add(classificationFilter);
        }
        configureFilterCategory(context, null, MessageKeys.UI_CERT_ITEM_PROPERTIES_CATEGORY_NAME, 2, certItemPropertyFilters);
        filterDTOList.addAll(certItemPropertyFilters);

        return filterDTOList;
    }

    @Override
    protected String getIdentityFilterStringFormat(SailPointContext context) throws GeneralException{
        return IDENTITY_FILTER_STRING_FORMAT;
    }
}
