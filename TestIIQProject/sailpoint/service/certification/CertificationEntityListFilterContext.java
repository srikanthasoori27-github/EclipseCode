/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.api.SailPointContext;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of ListFilterContext for certification entity filters
 */
public abstract class CertificationEntityListFilterContext extends AbstractCertificationListFilterContext {

    /**
     *  Filter string format for certItem type filter
     */
    protected static final String CERT_ITEM_FILTER_STRING_FORMAT = "parent.certification.id == \"%s\"";

    protected static final String IDENTITY_PREFIX = "Identity.";
    private static final String CERTIFICATION_ITEM_PREFIX = "CertificationItem.";

    private static final String SUMMARY_STATUS_FILTER = "summaryStatus";

    // CertificationItem properties around additional entitlements
    private static final String CERT_ITEM_APPLICATION = "exceptionApplication";
    private static final String CERT_ITEM_TYPE = "type";

    private static final int IDENTITY_PROPERTIES_CATEGORY_INDEX = 0;
    private static final int ACCOUNT_PROPERTIES_CATEGORY_INDEX = 1;

    private static final String BUSINESS_ROLE_FILTER = "businessRole";
    private static final String HAS_ADDITIONAL_ENTITLEMENTS_FILTER = "hasAdditionalEntitlements";
    private static final String HAS_POLICY_VIOLATIONS_FILTER = "hasPolicyViolations";
    private static final String ITEM_TYPE_FILTER = "itemType";
    protected static final String TARGET_DISPLAY_NAME = "targetDisplayName";
    protected static final String CLASSIFICATION_FILTER = "classificationNames";

    private static final String CHANGES_DETECTED_NEW_USER_PROP = "newUser";

    /**
     * Constructor
     * @param certificationId ID of the certification
     */
    public CertificationEntityListFilterContext(String certificationId) {
        super(certificationId);
    }

    /**
     * Override this to handle making object attribute filters ourselves, since we need a bunch of extra handling
     * and use multiple ObjectConfigs.
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> listFilterDTOList = new ArrayList<>();

        // Create filters for identity attributes.
        ObjectConfig identityObjectConfig = getIdentityObjectConfig(context);
        if (identityObjectConfig != null) {
            List<ObjectAttribute> identityAttributes = new ArrayList<>(identityObjectConfig.getSearchableAttributes());

            // Get the top level object attributes. This will remove them from identityAttributes list.
            // Create DTOs for them and do not set the additional flag, but remember them in the base class.
            List<ObjectAttribute> topLevelIdentityAttributes = getTopLevelIdentityAttributes(context, identityAttributes);
            List<ListFilterDTO> topLevelFilters = createListFilterDTOs(topLevelIdentityAttributes, Identity.class, IDENTITY_PREFIX, getIdentityFilterStringFormat(context), locale, false);
            listFilterDTOList.addAll(topLevelFilters);
            addTopLevelFilters(topLevelFilters);

            // Create filters for the Identity category with the remaining identity attributes.
            listFilterDTOList.addAll(createFiltersForCategory(context, identityAttributes, Identity.class, IDENTITY_PREFIX,
                    getIdentityFilterStringFormat(context), MessageKeys.UI_IDENTITY_PROPERTIES_CATEGORY_NAME, IDENTITY_PROPERTIES_CATEGORY_INDEX, locale));

            // SPECIAL CASE: Replace the label for 'type' filter to be more specific, since there are a number of types in play
            for (ListFilterDTO filterDTO : listFilterDTOList) {
                if ((IDENTITY_PREFIX + Identity.ATT_TYPE).equals(filterDTO.getProperty())) {
                    filterDTO.setLabel(new Message(MessageKeys.UI_CERT_FILTER_IDENTITY_TYPE).getLocalizedMessage(locale, null));
                }
            }
        }

        // Create filters for CertificationItem attributes.
        ObjectConfig certItemObjectConfig = ObjectConfig.getObjectConfig(CertificationItem.class);

        if (certItemObjectConfig != null) {
            listFilterDTOList.addAll(createFiltersForCategory(context, certItemObjectConfig.getSearchableAttributes(), CertificationItem.class, CERTIFICATION_ITEM_PREFIX,
                    CERT_ITEM_FILTER_STRING_FORMAT, MessageKeys.UI_ACCOUNT_PROPERTIES_CATEGORY_NAME, ACCOUNT_PROPERTIES_CATEGORY_INDEX, locale));
        }

        return listFilterDTOList;
    }

    /**
     * Override this to handle some custom filters we need for the different item grids.
     */
    @Override
    public Filter convertFilter(String propertyName, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter;

        if (SUMMARY_STATUS_FILTER.equals(propertyName)) {
            filter = getEqualityFilter(CertificationEntity.class, "summaryStatus", filterValue, true, true);
        }else {
            if (filterValue.getValue() == null) {
                filter = FilterHelper.getNullFilter(propertyName);
            } else if (ITEM_TYPE_FILTER.equals(propertyName)) {
                filter = getSimpleFilter(propertyName, filterValue.getValue(), ListFilterValue.Operation.Equals);
            } else {
                // For group decisions, we need to support any old property name for filtering, with a basic equality filter.
                filter = getEqualityFilter(CertificationEntity.class, propertyName, filterValue, true, false);
            }
        }

        filter = checkJoin(getCertificationType(context), filter, propertyName);

        return filter;
    }

    /**
     * Add the join filter for Identity properties, if needed.
     */
    protected Filter checkJoin(Certification.Type type, Filter filter, String property) {
        if (isIdentityFilter(property)) {
            // Add a join to the identity table if we haven't joined yet.
            Filter join;
            // DataOwner and Account Group Membership certs have the name of the entitlement being certified in the
            // CertificationEntity's identity column and the identity id in the CertificationItem's targetId column.
            // So the join has to be different.
            if (type != null && type.isObjectType()) {
                // We should never get here; object type cert entities should not have identity filters
                throw new UnsupportedOperationException();
            } else {
                join = Filter.join("identity", "Identity.name");
            }
            filter = Filter.and(join, filter);
            this.setJoinedToIdentity(true);
        }

        return filter;
    }


    @Override
    public List<Filter> postProcessFilters(List<Filter> filters) throws GeneralException {
        List<Filter> processedFilters = super.postProcessFilters(filters);

        return handleItemCollections(processedFilters);
    }


    /**
     * Find all the item conditions and put them in a collection condition filter.
     */
    private List<Filter> handleItemCollections(List<Filter> filters) throws GeneralException {
        List<Filter> returnFilters = new ArrayList<>();
        List<Filter> itemCollectionConditions = new ArrayList<>();

        for (Filter filter : filters) {
            if (isItemsCollectionCondition(filter)) {
                itemCollectionConditions.add(convertItemsCollectionConditionFilter(filter));
            } else {
                returnFilters.add(filter);
            }
        }

        if (!itemCollectionConditions.isEmpty()) {
            returnFilters.add(Filter.collectionCondition("items", Filter.and(itemCollectionConditions)));
        }

        return returnFilters;
    }

    /**
     * Return whether the filter is a collection condition on the items collection.
     *
     * @param filter the filter
     * @return True if filter is a collection condition on the items collection.
     */
    private boolean isItemsCollectionCondition(Filter filter) {

        if (filter instanceof Filter.CompositeFilter) {
            for (Filter leaf : ((Filter.CompositeFilter) filter).getChildren()) {
                // Recurse to hit all the leaves. Honestly this will probably only happen for additional entitlements
                // but might as well make it general while we're here.
                if (isItemsCollectionCondition(leaf)) {
                    return true;
                }
            }
            return false; // If we didn't find it this way skip the leaf processing below
        }
        String propertyName = ((Filter.LeafFilter)filter).getProperty();

        return isItemFilter(propertyName) ||
                isAdditionalEntitlementFilter(propertyName) ||
                BUSINESS_ROLE_FILTER.equals(propertyName) ||
                HAS_ADDITIONAL_ENTITLEMENTS_FILTER.equals(propertyName) ||
                HAS_POLICY_VIOLATIONS_FILTER.equals(propertyName) ||
                ITEM_TYPE_FILTER.equals(propertyName) ||
                CLASSIFICATION_FILTER.equals(propertyName);
    }

    private boolean isItemFilter(String propertyName) {
        return propertyName != null && propertyName.startsWith(CERTIFICATION_ITEM_PREFIX);
    }

    private boolean isAdditionalEntitlementFilter(String propertyName) {
        return propertyName != null && propertyName.equals(CERT_ITEM_APPLICATION);
    }

    protected Filter convertItemsCollectionConditionFilter(Filter filter) throws GeneralException {
        if (!(filter instanceof Filter.LeafFilter)) {
            // Nothing to do for composite filters
            return filter;
        }

        Filter.LeafFilter leaf = ((Filter.LeafFilter)filter);
        Filter newFilter = leaf;
        if (BUSINESS_ROLE_FILTER.equals(leaf.getProperty())) {
            newFilter = Filter.and(Filter.eq(CERT_ITEM_TYPE, CertificationItem.Type.Bundle),
                    Filter.eq(TARGET_DISPLAY_NAME, leaf.getValue()));
        }
        else if (HAS_ADDITIONAL_ENTITLEMENTS_FILTER.equals(leaf.getProperty())) {
            newFilter = getBooleanFilter(CERT_ITEM_TYPE, CertificationItem.Type.Exception, (boolean)leaf.getValue());
        }
        else if (HAS_POLICY_VIOLATIONS_FILTER.equals(leaf.getProperty())) {
            newFilter = getBooleanFilter(CERT_ITEM_TYPE, CertificationItem.Type.PolicyViolation, (boolean)leaf.getValue());
        }
        else if (ITEM_TYPE_FILTER.equals(leaf.getProperty())) {
            newFilter = getSimpleFilter(CERT_ITEM_TYPE, leaf.getValue(), ListFilterValue.Operation.Equals);
        } else if (CLASSIFICATION_FILTER.equals(leaf.getProperty())) {
            newFilter = Filter.contains(CLASSIFICATION_FILTER, leaf.getValue());
        }
        else if (isItemFilter(leaf.getProperty())) {
            // Trim the prefix before building filter.
            String propertyName = leaf.getProperty().substring(CERTIFICATION_ITEM_PREFIX.length());

            // Boolean filters need special construction, everything else gets a simple filter.
            String attrType = getObjectAttributeType(propertyName);
            if (BaseAttributeDefinition.TYPE_BOOLEAN.equals(attrType)) {
                newFilter = getCollectionBooleanFilter(propertyName, leaf.getValue());
            }
            else {
                newFilter = getSimpleFilter(propertyName, leaf.getValue(), convertOperation(leaf.getOperation()));
            }
        }

        return newFilter;
    }

    /**
     * Construct a boolean filter that will work for nulls and missing properties.
     * @param propertyName the property name
     * @param value the test value, should be Boolean
     * @return the filter
     */
    private Filter getCollectionBooleanFilter(String propertyName, Object value) {
        Filter filter;

        if (value.toString() == "true") {
            filter = Filter.eq(propertyName, "true");
        }
        else {
            // Testing prop == false would give incorrect results for nulls or non-existent properties.
            filter = Filter.ne(propertyName, "true");
        }

        return filter;
    }

    /**
     * Returns the type of the names attribute in the CertificationItem object config, or null if it's not found.
     * @param attrName the attribute name
     * @return the type
     */
    private String getObjectAttributeType(String attrName) {
        ObjectConfig objectConfig = ObjectConfig.getObjectConfig(CertificationItem.class);
        List<ObjectAttribute> objAttrs = objectConfig.getSearchableAttributes();
        for (ObjectAttribute objAttr : objAttrs) {
            if (Util.nullSafeEq(objAttr.getName(), attrName)) {
                return objAttr.getType();
            }
        }

        return null;
    }

    /**
     * Convert a filter LogicalOperation into a ListFilterValue Operation. This only works for the logical operations
     * which are supported in ListFilterValue (EQ, LT, GT, NE). Any other filter operation will throw.
     * @param logicalOperation the filter LogicalOperations
     * @return the corresponding ListFilterValue operation
     */
    private ListFilterValue.Operation convertOperation(Filter.LogicalOperation logicalOperation) {
        ListFilterValue.Operation filterOperation;

        switch (logicalOperation) {
            case EQ:
                filterOperation = ListFilterValue.Operation.Equals;
                break;
            case LT:
                filterOperation = ListFilterValue.Operation.LessThan;
                break;
            case GT:
                filterOperation = ListFilterValue.Operation.GreaterThan;
                break;
            case NE:
                filterOperation = ListFilterValue.Operation.NotEquals;
                break;
            default:
                throw new UnsupportedOperationException();
        }

        return filterOperation;

    }
    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        Filter filter = super.convertFilterSingleValue(filterDTO, value, operation, context);

        if (Util.nullSafeEq(filterDTO.getProperty(), CHANGES_DETECTED_FILTER)) {
            CertificationItemDTO.ChangesDetected changesDetected = CertificationItemDTO.ChangesDetected.valueOf(value);
            if (CertificationItemDTO.ChangesDetected.NewUser.equals(changesDetected))  {
                filter = Filter.eq(CHANGES_DETECTED_NEW_USER_PROP, true);
            } else {
                boolean hasDifferences = CertificationItemDTO.ChangesDetected.Yes.equals(changesDetected);
                filter = Filter.and(Filter.eq(CHANGES_DETECTED_NEW_USER_PROP, false), Filter.eq(CHANGES_DETECTED_PROP, hasDifferences));
            }
        }

        return filter;
    }

    private Filter getBooleanFilter(String propertyName, Object value, boolean condition) {
        Filter filter;

        if (condition) {
            filter = Filter.eq(propertyName, value);
        }
        else {
            filter = Filter.ne(propertyName, value);
        }

        return filter;
    }

    @Override
    protected List<String> getPreferredTopLevelIdentityAttributeNames() {
        return null;
    }
}
