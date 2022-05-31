/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification.schedule;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.IdentityExternalAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.service.listfilter.IdentityFilterUtil;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of ListFilterContext for filtering identities when scheduling a certification
 */
public class CertificationScheduleIdentityListFilterContext extends CertificationScheduleBaseListFilterContext {

    /**
     * Constructor
     */
    public CertificationScheduleIdentityListFilterContext() {
        super(Identity.class);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filters = super.getDefaultFilters(context, locale);
        filters = IdentityFilterUtil.getDefaultFilters(filters, locale);
        addSpecialFilterOptions(filters, locale);

        return filters;
    }

    @Override
    protected List<ObjectAttribute> getFilterObjectAttributes(SailPointContext context) throws GeneralException {
        List<ObjectAttribute> objectAttributes = super.getFilterObjectAttributes(context);
        objectAttributes = IdentityFilterUtil.getFilterObjectAttributes(objectAttributes);
        return objectAttributes;
    }

    @Override
    protected String getExternalAttributeTable() {
        return IdentityExternalAttribute.class.getSimpleName();
    }

    protected boolean includeMultiAttributes() {
        return true;
    }

}


