/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.acceleratorpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * ListFilterContext implementation for returning identity attribute filters.
 * A lot of the logic here is borrowed from CorrelationConfigBean, which provides the same
 * functionality in ExtJS.
 */
public class ApplicationOnboardIdentityAttributeListFilterContext extends BaseListFilterContext {

    public ApplicationOnboardIdentityAttributeListFilterContext() {
        super(null);
    }

    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> names = new ArrayList<>();

        ObjectConfig config = Identity.getObjectConfig();
        if ( config != null ) {
            // Create a new list so we don't modify the cached object config.
            List<ObjectAttribute> attrs = new ArrayList<ObjectAttribute>();
            List<ObjectAttribute> multis = config.getMultiAttributeList();
            List<ObjectAttribute> searchables = config.getSearchableAttributes();
            if ( Util.size(searchables) > 0 ) {
                attrs.addAll(searchables);
            }
            if ( Util.size(multis) > 0 ) {
                attrs.addAll(multis);
            }

            if ( Util.size(attrs) > 0 ) {
                for ( ObjectAttribute attr : attrs ) {
                    String name = attr.getName();
                    String displayName = attr.getDisplayableName(locale);
                    if ( "manager".compareTo(name) != 0 ) {
                        ListFilterDTO listFilterDTO = new ListFilterDTO(name, displayName,
                                false, ListFilterDTO.DataTypes.String, locale);
                        names.add(listFilterDTO);
                    }
                }

                // add this as a default value so we can match on an identities
                // name. This isn't included in the default searchable list...
                ListFilterDTO listFilterDTO = new ListFilterDTO("name", "User Name", false,
                        ListFilterDTO.DataTypes.String, locale);
                names.add(listFilterDTO);
            }
        }

        return names;
    }
}
