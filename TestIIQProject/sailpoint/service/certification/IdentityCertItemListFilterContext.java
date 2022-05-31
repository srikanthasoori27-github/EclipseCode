/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationItem;
import sailpoint.object.Classification;
import sailpoint.object.Filter;
import sailpoint.object.Recommendation;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Implementation of ListFilterContext for certification item filters
 */
public class IdentityCertItemListFilterContext extends CertificationItemListFilterContext {

    /**
     * Filter String for the column suggests for the certification. Limits to Identities that are part of the certification.
     */
    protected static final String IDENTITY_FILTER_STRING_FORMAT = "name.join(CertificationEntity.identity) && CertificationEntity.certification.id == \"%s\"";

    private static final String TYPE_FILTER = "type";
    private static final String ROLE_FILTER = "targetDisplayName";
    private static final String CHANGES_DETECTED_FILTER = "changesDetected";
    public static final String RECOMMEND_FILTER = "recommendValue";
    public static final String AUTO_DECIDE_FILTER = "action.autoDecision";
    protected static final String CLASSIFICATION_FILTER = "classificationNames";

    /**
     * Constructor
     *
     * @param certificationId ID of the certification
     */
    public IdentityCertItemListFilterContext(String certificationId) {
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

        // Type
        ListFilterDTO typeFilter = new ListFilterDTO(TYPE_FILTER, MessageKeys.ITEM_TYPE, false, ListFilterDTO.DataTypes.String, locale);
        List<ListFilterDTO.SelectItem> typeItems = new ArrayList<>();
        typeItems.add(getSelectItem(CertificationItem.Type.Bundle, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.PolicyViolation, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.Exception, locale));
        typeFilter.setAllowedValues(typeItems);

        // Role Name
        ListFilterDTO roleFilter = new ListFilterDTO(
                ROLE_FILTER, MessageKeys.UI_ROLE_ATTRIBUTE_NAME, false, ListFilterDTO.DataTypes.String, locale);
        roleFilter.configureColumnSuggest(
                CertificationItem.class.getSimpleName(), ROLE_FILTER, String.format(CERT_ITEM_FILTER_STRING_FORMAT, getCertificationId()), getSuggestUrl());

        // Return all filters so far
        List<ListFilterDTO> certItemPropertyFilters = Arrays.asList(typeFilter, changesDetectedFilter, roleFilter);
        configureFilterCategory(context, null, MessageKeys.UI_CERT_ITEM_PROPERTIES_CATEGORY_NAME, 2, certItemPropertyFilters);
        filterDTOList.addAll(certItemPropertyFilters);

        // Add Recommendations filter too, if enabled for this cert. Treat like a default filter so that it doesn't
        // need to be added manually by the user.
        if (RecommenderUtil.isRecommenderConfigured(context) && this.hasRecommendations(context)) {
            ListFilterDTO recommendFilter = new ListFilterDTO(RECOMMEND_FILTER, MessageKeys.UI_RECOMMENDATIONS, false,
                    ListFilterDTO.DataTypes.String, locale);
            List<ListFilterDTO.SelectItem> recommendItems = new ArrayList<>();
            recommendItems.add(getSelectItem(Recommendation.RecommendedDecision.YES, locale));
            recommendItems.add(getSelectItem(Recommendation.RecommendedDecision.NO, locale));
            recommendItems.add(getSelectItem(Recommendation.RecommendedDecision.NO_RECOMMENDATION, locale));
            recommendFilter.setAllowedValues(recommendItems);
            recommendFilter.configureCategory(MessageKeys.UI_CERT_ITEM_PROPERTIES_CATEGORY_NAME, 2);
            recommendFilter.setAdditional(false);

            filterDTOList.add(recommendFilter);

            // Add auto-decide filter as an option.
            if (this.hasAutoApprovals(context)) {
                ListFilterDTO autoDecideFilter = new ListFilterDTO(AUTO_DECIDE_FILTER, MessageKeys.UI_AUTO_APPROVED, false,
                        ListFilterDTO.DataTypes.Boolean, locale);
                configureFilterCategory(context, null, MessageKeys.UI_CERT_ITEM_PROPERTIES_CATEGORY_NAME, 2, Arrays.asList(autoDecideFilter));

                filterDTOList.add(autoDecideFilter);
            }
        }

        if (getCertificationDefinition(context).isIncludeClassifications()) {
            ListFilterDTO classificationFilter = new ListFilterDTO(CLASSIFICATION_FILTER, MessageKeys.UI_CLASSIFICATIONS, false, ListFilterDTO.DataTypes.Object, locale);
            classificationFilter.configureObjectSuggest(Classification.class.getSimpleName(), getSuggestUrl());
            classificationFilter.configureCategory(MessageKeys.UI_CERT_ITEM_PROPERTIES_CATEGORY_NAME, 2);
            classificationFilter.setAdditional(true);
            filterDTOList.add(classificationFilter);
        }

        return filterDTOList;
    }

    /**
     * Override this to make targetDisplayName filter exact.
     */
    @Override
    public Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation,
                                           SailPointContext context) throws GeneralException {
        Filter filter;

        if (Util.nullSafeEq(ROLE_FILTER, filterDTO.getProperty())) {
            filter = getSimpleFilter(ROLE_FILTER, value, operation);
        } else if (CLASSIFICATION_FILTER.equals(filterDTO.getProperty())) {
            filter = Filter.contains(CLASSIFICATION_FILTER, value);
        }
        else {
            filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
        }

        return filter;
    }

    @Override
    protected String getIdentityFilterStringFormat(SailPointContext context) throws GeneralException{
        return IDENTITY_FILTER_STRING_FORMAT;
    }

    /**
     * Override to return comparator that forces the recommendation filter to the end.
     */
    @Override
    public Comparator<ListFilterDTO> getFilterComparator(Locale locale) {
        return (listFilter1, listFilter2) -> {
            String  prop1 = listFilter1.getProperty(),
                    prop2 = listFilter2.getProperty();

            // We should only ever have one RECOMMEND_FILTER, but let's be thorough.
            if (prop1.equals(RECOMMEND_FILTER) && !prop2.equals(RECOMMEND_FILTER)) {
                return 1;
            } else if (prop2.equals(RECOMMEND_FILTER) && !prop1.equals(RECOMMEND_FILTER)) {
                return -1;
            } else {
                return 0;
            }
        };
    }
}
