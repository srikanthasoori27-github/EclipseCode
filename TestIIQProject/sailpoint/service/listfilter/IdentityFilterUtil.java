/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.listfilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.web.messages.MessageKeys;

public class IdentityFilterUtil {

    public static final String MODIFIED = "modified";
    public static final String CREATED = "created";
    public static final String LAST_REFRESH = "lastRefresh";
    public static final String CORRELATED = "correlated";

    /**
     * List of attributes that we always want to include, even if not marked standard in the ObjectConfig
     */
    public static final String[] STANDARD_PROPERTY_ATTRS = {
            Identity.ATT_MANAGER,
            Identity.ATT_LASTNAME,
            Identity.ATT_FIRSTNAME,
            Identity.ATT_USERNAME,
            Identity.ATT_DISPLAYNAME,
            Identity.ATT_INACTIVE,
            Identity.ATT_TYPE,
            Identity.ATT_SOFTWARE_VERSION,
            Identity.ATT_ADMINISTRATOR,
            Identity.ATT_MANAGER_STATUS
    };

    public static List<ListFilterDTO> getDefaultFilters(List<ListFilterDTO> filters, Locale locale) {
        if (filters == null) {
            filters = new ArrayList<>();
        }

        ListFilterDTO modified = new ListFilterDTO(MODIFIED, MessageKeys.UI_IDENTITY_MODIFIED, false, ListFilterDTO.DataTypes.Date, locale);

        ListFilterDTO created = new ListFilterDTO(CREATED, MessageKeys.UI_IDENTITY_CREATED, false, ListFilterDTO.DataTypes.Date, locale);

        ListFilterDTO correlated = new ListFilterDTO(CORRELATED, MessageKeys.UI_IDENTITY_CORRELATED, false, ListFilterDTO.DataTypes.Boolean, locale);

        ListFilterDTO lastRefresh = new ListFilterDTO(LAST_REFRESH, MessageKeys.UI_IDENTITY_LAST_REFRESH, false, ListFilterDTO.DataTypes.Date, locale);

        filters.addAll(Arrays.asList(modified, created, correlated, lastRefresh));

        return filters;
    }

    public static List<ObjectAttribute> getFilterObjectAttributes(List<ObjectAttribute> objectAttributes) {
        ObjectConfig objectConfig = ObjectConfig.getObjectConfig(Identity.class);
        for (String standardAttr : STANDARD_PROPERTY_ATTRS) {
            ObjectAttribute objectAttribute = objectConfig.getObjectAttribute(standardAttr);
            if (objectAttribute != null && !objectAttributes.contains(objectAttribute)) {
                objectAttributes.add(objectAttribute);
            }
        }

        return objectAttributes;
    }
}
