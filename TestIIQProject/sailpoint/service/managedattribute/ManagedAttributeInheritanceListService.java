/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.managedattribute;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service to handle listing inheritance for a managed attribute
 */
public class ManagedAttributeInheritanceListService extends BaseListService<BaseListServiceContext> {

    public ManagedAttributeInheritanceListService(SailPointContext context, BaseListServiceContext listServiceContext) {
        super(context, listServiceContext, new BaseListResourceColumnSelector("uiInheritedGroupsTableColumns"));
    }

    /**
     * Checks if there is any inheritance in any direction for the given managed attribute. Method is static to
     * allow easy fetching from ManagedAttributeDetailService.
     * @param context SailPointContext
     * @param managedAttribute ManagedAttribute object
     * @return true if there is any inheritance, otherwise false.
     * @throws GeneralException
     */
    public static boolean hasInheritance(SailPointContext context, ManagedAttribute managedAttribute) throws GeneralException {
        if (context == null) {
            throw new InvalidParameterException("context");
        }
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }
        Filter childrenFilter = getChildrenFilter(managedAttribute);
        Filter parentFilter = getParentFilter(managedAttribute);
        QueryOptions ops = new QueryOptions();
        ops.add((parentFilter == null) ? childrenFilter : Filter.or(parentFilter, childrenFilter));
        return context.countObjects(ManagedAttribute.class, ops) > 0;
    }

    /**
     * Gets a list result representing the parent managed attributes of the given managed attribute
     * @param managedAttribute ManagedAttribute object
     * @return ListResult
     * @throws GeneralException
     */
    public ListResult getParents(ManagedAttribute managedAttribute) throws GeneralException {
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }
        Filter filter = getParentFilter(managedAttribute);
        if (filter == null) {
            return new ListResult(null, 0);
        }
        QueryOptions ops = createQueryOptions();
        ops.add(filter);
        List<Map<String, Object>> results = getResults(ManagedAttribute.class, ops);
        int count = countResults(ManagedAttribute.class, ops);
        return new ListResult(results, count);
    }

    /**
     * Creates a filter for parent inheritance from the given managed attribute. If none exists, returns null.
     */
    private static Filter getParentFilter(ManagedAttribute managedAttribute) throws GeneralException {
        List<ManagedAttribute> inheritance = managedAttribute.getInheritance();
        if (Util.isEmpty(inheritance)) {
            return null;
        }
        List<String> ids = ObjectUtil.getObjectIds(inheritance);
        return Filter.in("id", ids);
    }

    /**
     * Gets a list result representing the children managed attributes of the given managed attribute
     * @param managedAttribute ManagedAttribute object
     * @return ListResult
     * @throws GeneralException
     */
    public ListResult getChildren(ManagedAttribute managedAttribute) throws GeneralException {
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }

        Filter filter = getChildrenFilter(managedAttribute);
        QueryOptions ops = createQueryOptions();
        ops.add(filter);
        List<Map<String, Object>> results = getResults(ManagedAttribute.class, ops);
        int count = countResults(ManagedAttribute.class, ops);
        return new ListResult(results, count);
    }

    /**
     * Creates a filter for child inheritance from the given managed attribute. 
     */
    private static Filter getChildrenFilter(ManagedAttribute managedAttribute) throws GeneralException {
        return Filter.containsAll("inheritance", Collections.singletonList(managedAttribute));
    }

}
