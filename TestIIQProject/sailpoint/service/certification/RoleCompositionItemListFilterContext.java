/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationItem;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.messages.MessageKeys;

/**
 * Implementation of ListFilterContext for certification item filters
 */
public class RoleCompositionItemListFilterContext extends CertificationItemListFilterContext {

    // Properties around roles
    private static final String CERT_ITEM_ROLE_NAME = "targetName";
    private static final String CERT_ITEM_TYPE = "type";

    /**
     * Constructor
     * @param certificationId ID of the certification
     */
    public RoleCompositionItemListFilterContext(String certificationId) {
        super(certificationId);
    }

    /**
     * Override this to handle making object attribute filters with role attributes up top.
     * Does not inherit any identity attribute and account group attribute filters from parent
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> listFilterDTOList = new ArrayList<>();
        
        //friendly type message
        List<ListFilterDTO.SelectItem> typeItems = new ArrayList<>();
        typeItems.add(getSelectItem(CertificationItem.Type.BusinessRoleHierarchy, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.BusinessRoleRequirement, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.BusinessRolePermit, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.BusinessRoleGrantedCapability, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.BusinessRoleGrantedScope, locale));
        typeItems.add(getSelectItem(CertificationItem.Type.BusinessRoleProfile, locale));

        ListFilterDTO relatedRoleNameFilter = new ListFilterDTO(
                CERT_ITEM_ROLE_NAME, Internationalizer.getMessage(MessageKeys.UI_ROLE_COMP_CERT_NAME_COL, locale),
                false, ListFilterDTO.DataTypes.String, locale);
        relatedRoleNameFilter.configureColumnSuggest(
                CertificationItem.class.getSimpleName(), CERT_ITEM_ROLE_NAME, String.format(CERT_ITEM_FILTER_STRING_FORMAT, getCertificationId()), getSuggestUrl());

        listFilterDTOList.addAll(Arrays.asList(relatedRoleNameFilter));
        addTopLevelFilters(Arrays.asList(relatedRoleNameFilter));

        ListFilterDTO relatedRoleTypeFilter = new ListFilterDTO(
                CERT_ITEM_TYPE, Internationalizer.getMessage(MessageKeys.CERT_ITEM_ROLE_COMP_RELATED_ROLES_TYPE, locale),
                false, ListFilterDTO.DataTypes.String, locale);
        relatedRoleTypeFilter.setAllowedValues(typeItems);

        listFilterDTOList.addAll(Arrays.asList(relatedRoleTypeFilter));
        addTopLevelFilters(Arrays.asList(relatedRoleTypeFilter));

        return listFilterDTOList;
    }

    @Override
    protected List<String> getExceptionApplicationNames(SailPointContext context) throws GeneralException {
        // We don't want any application names showing up.
        return Collections.emptyList();
    }

    @Override
    protected String getIdentityFilterStringFormat(SailPointContext context) throws GeneralException {
        // We should never get here.
        throw new UnsupportedOperationException();
    }
}
