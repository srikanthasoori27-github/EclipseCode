/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

/**
 * The service responsible for listing the groups attached to a containers.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerGroupListService extends BaseListService<BaseListServiceContext> {

    public ContainerGroupListService(SailPointContext context, BaseListServiceContext serviceContext,
                                     ListServiceColumnSelector columnSelector) {
        super(context, serviceContext, columnSelector);
    }


    /**
     * Return the list of groups on the container for the given query options
     *
     * @param groupOptions A set of queryoptions to filter the list of groups by.  Should be either the direct or indirect queryoptions
     * @return
     * @throws GeneralException
     */
    public ListResult getGroups(QueryOptions groupOptions) throws GeneralException {

        QueryOptions qo = this.getQueryOptions(groupOptions);
        int count = countResults(ManagedAttribute.class, this.getQueryOptions(qo));
        List<Map<String, Object>> results = super.getResults(ManagedAttribute.class, qo);
        return new ListResult(results, count);
    }

    /**
     * Return the count of groups with the given query options
     *
     * @param groupOptions A set of queryoptions to filter the list of groups by.  Should be either the direct or indirect queryoptions
     * @return
     * @throws GeneralException
     */
    public int getGroupsCount(QueryOptions groupOptions) throws GeneralException {
        QueryOptions qo = this.getQueryOptions(groupOptions);
        return countResults(ManagedAttribute.class, this.getQueryOptions(qo));
    }

    /**
     * Create a new set of query options (with sorting, paging, etc...) and merge the provided options into
     * it.  Used to
     *
     * @param groupOptions A set of queryoptions to filter the list of groups by.  Should be either the direct or indirect queryoptions
     * @return
     * @throws GeneralException
     */
    private QueryOptions getQueryOptions(QueryOptions groupOptions) throws GeneralException {
        QueryOptions qo = this.createQueryOptions();
        if (groupOptions != null) {
            qo.getFilters().addAll(groupOptions.getFilters());
        }
        // Query needs to be distinct since (IIQPM-436) there are now 1 TargetAssociation created per right on the Group.
        qo.setDistinct(true);
        return qo;
    }

    /**
     * We need to grab the description from the Explanator for ManagedAttributes, so convert the rows
     * coming back from the query to stick that description in there.
     *
     * @param rawObject               raw object
     * @param keepUnconfiguredColumns If true, keep values in row even if no corresponding column config
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Map<String, Object> convertRow(Map<String, Object> rawObject, boolean keepUnconfiguredColumns)
            throws GeneralException {
        Map<String, Object> convertedRow = super.convertRow(rawObject, keepUnconfiguredColumns);

        // 'description' will pull in the attributes map. we can get the descriptions off of that and use the Explanator for consistency
        Attributes attributes = getAttributes((String) convertedRow.get("id"));
        if (attributes != null) {
            Map<String, String> descriptionMap = (Map<String, String>) attributes.get(ManagedAttribute.ATT_DESCRIPTIONS);
            String description = null;
            if (descriptionMap != null) {
                description = Explanator.getDescription(descriptionMap, getListServiceContext().getLocale());
            }
            convertedRow.put("description", (description == null) ? "" : description);
        }

        return convertedRow;
    }

    /**
     * Does a projection search on the managed attribute to return the attributes for us in creating the description
     * @param id String The id of the managed attribute
     * @return
     * @throws GeneralException
     */
    private Attributes getAttributes(String id) throws GeneralException {
        Attributes attributes = null;
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("id", id));
        List<String> props = new ArrayList<String>();
        props.add("attributes");
        Iterator<Object[]> it = context.search(ManagedAttribute.class, ops, props);
        if (it.hasNext()) {
            Object[] row = it.next();
            attributes = (Attributes) row[0];
        }

        return attributes;
    }

}
