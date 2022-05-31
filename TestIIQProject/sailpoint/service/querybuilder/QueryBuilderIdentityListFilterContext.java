/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.querybuilder;

import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.IdentityExternalAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.IdentityFilterUtil;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;

public class QueryBuilderIdentityListFilterContext extends BaseListFilterContext {

    /**
     * Constructor
     */
    public QueryBuilderIdentityListFilterContext() {
        super(ObjectConfig.getObjectConfig(Identity.class));
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filters = super.getDefaultFilters(context, locale);
        filters = IdentityFilterUtil.getDefaultFilters(filters, locale);
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
}
