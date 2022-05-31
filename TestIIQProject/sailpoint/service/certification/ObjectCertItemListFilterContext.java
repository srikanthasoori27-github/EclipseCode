/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.ObjectConfig;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * Implementation of ListFilterContext for certification item filters
 */
public class ObjectCertItemListFilterContext extends CertificationItemListFilterContext {

    /**
     * Filter String for the column suggests for the certification. Limits to Identities that are part of the certification.
     */
    private static final String IDENTITY_FILTER_STRING_FORMAT = "id.join(CertificationEntity.items.targetId) && CertificationEntity.certification.id == \"%s\"";

    // Filters
    private static final String ACCOUNT_GROUP_FILTER = "parent.accountGroup";
    private static final String SCHEMA_OBJECT_TYPE_FILTER = "parent.schemaObjectType";

    // CertificationItem properties around additional entitlements
    private static final String CERT_ITEM_ACCOUNT_GROUP = "parent.accountGroup";
    private static final String CERT_ITEM_SCHEMA_OBJECT_TYPE = "parent.schemaObjectType";

    private static final int CERT_ITEM_PROPERTIES_CATEGORY_INDEX = 2;

    private static List<String> PREFERRED_TOP_LEVEL_IDENTITY_ATTRIBUTE_NAMES = Arrays.asList("manager", "firstname", "lastname");

    /**
     * Constructor
     * @param certificationId ID of the certification
     */
    public ObjectCertItemListFilterContext(String certificationId) {
        super(certificationId);
    }

    /**
     * Override this to handle making object attribute filters with entitlement attribute and value up top.
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> listFilterDTOList = new ArrayList<>();

        ListFilterDTO entitlementNameFilter = new ListFilterDTO(
                CERT_ITEM_ATTRIBUTE_NAME, null, false, ListFilterDTO.DataTypes.String, locale);
        entitlementNameFilter.configureColumnSuggest(
                CertificationItem.class.getSimpleName(), CERT_ITEM_ATTRIBUTE_NAME, String.format(CERT_ITEM_FILTER_STRING_FORMAT, getCertificationId()), getSuggestUrl());
        entitlementNameFilter.setAttribute(ADDITIONAL_ENTITLEMENT_FLAG, true);
        entitlementNameFilter.setLabel(new Message("ui_cert_entitlement").getLocalizedMessage(locale, null));

        listFilterDTOList.addAll(Arrays.asList(entitlementNameFilter));
        // clear the top level filters that could have leftover data from multiple calls to add data
        clearTopLevelFilters();
        addTopLevelFilters(Arrays.asList(entitlementNameFilter));

        // Do not add identity or bundle attributes to account group permission attribute filter
        if (!getCertificationType(context).equals(Certification.Type.AccountGroupPermissions)) {
            // Add in the common ones from the parent class.
            listFilterDTOList.addAll(super.getObjectAttributeFilters(context, locale));
        }

        return listFilterDTOList;
    }

    /**
     * Overriding to get the default filters for some account group properties if necessary.
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOList = super.getDefaultFilters(context, locale);

        Certification.Type certType = getCertificationType(context);
        if (certType == Certification.Type.AccountGroupMembership || certType == Certification.Type.AccountGroupPermissions) {
            // Account Group
            ListFilterDTO accountGroupFilter = new ListFilterDTO(ACCOUNT_GROUP_FILTER, MessageKeys.ACCOUNT_GROUP, false, ListFilterDTO.DataTypes.String, locale);
            accountGroupFilter.configureColumnSuggest(
                    CertificationItem.class.getSimpleName(), CERT_ITEM_ACCOUNT_GROUP, String.format(CERT_ITEM_FILTER_STRING_FORMAT, getCertificationId()),getSuggestUrl());

            ListFilterDTO typeFilter = new ListFilterDTO(SCHEMA_OBJECT_TYPE_FILTER, MessageKeys.TYPE, false, ListFilterDTO.DataTypes.String, locale);
            typeFilter.configureColumnSuggest(
                    CertificationItem.class.getSimpleName(), CERT_ITEM_SCHEMA_OBJECT_TYPE, String.format(CERT_ITEM_FILTER_STRING_FORMAT, getCertificationId()), getSuggestUrl());

            List<ListFilterDTO> certItemPropertyFilters = Arrays.asList(accountGroupFilter, typeFilter);
            configureFilterCategory(context, null, MessageKeys.UI_CERT_ITEM_PROPERTIES_CATEGORY_NAME, CERT_ITEM_PROPERTIES_CATEGORY_INDEX, certItemPropertyFilters);
            filterDTOList.addAll(certItemPropertyFilters);
        }

        return filterDTOList;
    }

    @Override
    protected ObjectConfig getIdentityObjectConfig(SailPointContext context) throws GeneralException {
        if (Certification.Type.AccountGroupPermissions.equals(getCertificationType(context))) {
            return null;
        }
        return super.getIdentityObjectConfig(context);
    }


    @Override
    protected List<String> getExceptionApplicationNames(SailPointContext context) throws GeneralException {
        // We don't want any application names showing up, since we display entitlement filters differently.
        return Collections.emptyList();
    }

    @Override
    protected String getIdentityFilterStringFormat(SailPointContext context) throws GeneralException {
        if (Certification.Type.AccountGroupPermissions.equals(getCertificationType(context))) {
            // We should never get here.
            throw new UnsupportedOperationException();
        }        return IDENTITY_FILTER_STRING_FORMAT;
    }

    @Override
    protected List<String> getPreferredTopLevelIdentityAttributeNames() {
        return PREFERRED_TOP_LEVEL_IDENTITY_ATTRIBUTE_NAMES;
    }
}
