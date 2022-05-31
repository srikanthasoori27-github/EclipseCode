/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
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

/**
 * Implementation of ListFilterContext for certification item filters
 */
public abstract class CertificationItemListFilterContext extends AbstractCertificationListFilterContext {

    /**
     *  Filter string format for certItem type filter 
     */
    protected static final String CERT_ITEM_FILTER_STRING_FORMAT = "parent.certification.id == \"%s\"";

    protected static final String IDENTITY_PREFIX = "Identity.";
    
    private static final String EXCLUDED_TYPE_FILTER = "excludedType";
    private static final String INCLUDED_TYPE_FILTER = "includedType";
    private static final String SUMMARY_STATUS_FILTER = "summaryStatus";
    private static final String ENTITY_FILTER = "entity";
    
    private static final int IDENTITY_PROPERTIES_CATEGORY_INDEX = 0;
    private static final int ACCOUNT_PROPERTIES_CATEGORY_INDEX = 1;

    private static final String CHANGES_DETECTED_NEW_USER_PROP = "parent.newUser";

    /**
     * Constructor 
     * @param certificationId ID of the certification
     */
    public CertificationItemListFilterContext(String certificationId) {
        super(certificationId);
    }

    /**
     * Override this to handle making object attribute filters ourselves, since we need a bunch of extra handling
     * and use multiple ObjectConfigs.
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> listFilterDTOList = new ArrayList<ListFilterDTO>();

        // Create filters for identity attributes.
        ObjectConfig identityObjectConfig = ObjectConfig.getObjectConfig(Identity.class);

        if (identityObjectConfig != null) {
            List<ObjectAttribute> identityAttributes = new ArrayList<ObjectAttribute>(identityObjectConfig.getSearchableAttributes());

            // Get the top level object attributes. This will remove them from identityAttributes list.
            // Create DTOs for them and do not set the additional flag, but remember them in the base class.
            List<ObjectAttribute> topLevelIdentityAttributes = this.getTopLevelIdentityAttributes(context, identityAttributes);
            List<ListFilterDTO> topLevelFilters = this.createListFilterDTOs(topLevelIdentityAttributes, Identity.class, CertificationItemListFilterContext.IDENTITY_PREFIX, this.getIdentityFilterStringFormat(context), locale, false);
            listFilterDTOList.addAll(topLevelFilters);
            this.addTopLevelFilters(topLevelFilters);

            // Create filters for the Identity category with the remaining identity attributes.
            listFilterDTOList.addAll(this.createFiltersForCategory(context, identityAttributes, Identity.class, CertificationItemListFilterContext.IDENTITY_PREFIX,
                    this.getIdentityFilterStringFormat(context), MessageKeys.UI_IDENTITY_PROPERTIES_CATEGORY_NAME, CertificationItemListFilterContext.IDENTITY_PROPERTIES_CATEGORY_INDEX, locale));

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
            listFilterDTOList.addAll(this.createFiltersForCategory(context, certItemObjectConfig.getSearchableAttributes(), CertificationItem.class, null,
                    CertificationItemListFilterContext.CERT_ITEM_FILTER_STRING_FORMAT, MessageKeys.UI_ACCOUNT_PROPERTIES_CATEGORY_NAME, CertificationItemListFilterContext.ACCOUNT_PROPERTIES_CATEGORY_INDEX, locale));
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
            filter = getEqualityFilter(CertificationItem.class, "summaryStatus", filterValue, true, true);
        } else if (EXCLUDED_TYPE_FILTER.equals(propertyName)){
            filter = getEqualityFilter(CertificationItem.class, "type", filterValue, false, true);
        } else if (INCLUDED_TYPE_FILTER.equals(propertyName)) {
            filter = getEqualityFilter(CertificationItem.class, "type", filterValue, true, true);
        } else if (ENTITY_FILTER.equals(propertyName)) {
            filter = getEqualityFilter(CertificationItem.class, "parent.id", filterValue, true, true);
        } else {
            if (filterValue.getValue() == null) {
                filter = FilterHelper.getNullFilter(propertyName);
            } else {
                // For group decisions, we need to support any old property name for filtering, with a basic equality filter.
                filter = getEqualityFilter(CertificationItem.class, propertyName, filterValue, true, false);
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
                join = Filter.join("targetId", "Identity.id");
            } else {
                join = Filter.join("parent.identity", "Identity.name");
            }
            filter = Filter.and(join, filter);
            this.setJoinedToIdentity(true);
        }

        return filter;
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

    @Override
    protected List<String> getPreferredTopLevelIdentityAttributeNames() {
        return null;
    }
}
