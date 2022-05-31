package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.*;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Service to handle listing effective access
 */
public class EffectiveAccessListService extends BaseListService<BaseListServiceContext> {

    /**
     * Create a base list service.
     *
     * @param context            sailpoint context
     * @param listServiceContext list service context
     * @param columnSelector list service column selector
     */
    public EffectiveAccessListService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    /**
     * Get a ListResult with the effective access for the given object id
     * @param objectId ID of the object to get associated effective access
     * @return ListResult
     * @throws GeneralException
     */
    public ListResult getEffectiveAccess(String objectId) throws GeneralException {
        QueryOptions qo = createQueryOptions();
        qo.addFilter(Filter.eq("objectId", objectId));
        int count = countResults(TargetAssociation.class, qo);
        List<Map<String, Object>> results = getResults(TargetAssociation.class, qo);
        List<TargetAssociationDTO> processedResults = convertResults(results);
        return new ListResult(processedResults, count);
    }

    /**
     * Count the effective access (TargetAssociation) objects for the given object
     * @param objectId ID for the object
     * @return Count of objects
     * @throws GeneralException
     */
    public int getEffectiveAccessCount(String objectId) throws GeneralException {
        QueryOptions qo = createQueryOptions();
        qo.addFilter(Filter.eq("objectId", objectId));
        return countResults(TargetAssociation.class, qo);
    }

    /**
     * Convert the given result maps into TargetAssociationDTOs.
     * For TargetPermissions, set the type to the displayName
     */
    @SuppressWarnings("unchecked")
    private List<TargetAssociationDTO> convertResults(List<Map<String,Object>> results)
            throws GeneralException {
        final String ATT_ATTRIBUTES = "attributes";

        List<TargetAssociationDTO> access = new ArrayList<>();
        List<ColumnConfig> cols = this.columnSelector.getColumns();
        for (Map<String,Object> row : Util.safeIterable(results)) {
            TargetAssociationDTO dto = new TargetAssociationDTO(row, cols);
            //Use the DisplayName for TargetPermissions and Permissions
            if (Util.nullSafeCaseInsensitiveEq(dto.getTargetType(), TargetAssociation.TargetType.TP.name())) {
                dto.setTargetType(TargetAssociation.TargetType.TP.getDisplayName());
            } else if (Util.nullSafeCaseInsensitiveEq(dto.getTargetType(), TargetAssociation.TargetType.P.name())) {
                dto.setTargetType(TargetAssociation.TargetType.P.getDisplayName());
            }

            // If the query returned an attirbutes map, check it for classifications on the TargetAssociation
            if (row.containsKey(ATT_ATTRIBUTES)) {
                Attributes<String, Object> attrs = (Attributes<String, Object>) row.get(ATT_ATTRIBUTES);
                if (attrs != null && attrs.containsKey(TargetAssociation.ATT_CLASSIFICATIONS)) {
                    List<String> classifications = (List<String>) attrs.get(TargetAssociation.ATT_CLASSIFICATIONS);
                    if (!Util.isEmpty(classifications)) {
                        ClassificationService cs = new ClassificationService(getContext());
                        List<String> displayNames = cs.getDisplayableNames(classifications);

                        dto.setClassificationNames(displayNames);
                    }
                }
            }

            access.add(dto);
        }
        return access;
    }


    public ListResult getIdentityEffectiveAccess(String identId, QueryOptions queryOps) throws GeneralException {
        List<TargetAssociationDTO> processedResults = new ArrayList<>();
        int count = 0;
        QueryOptions taOps = getEffectiveSearchQueryOps(identId);
        if (taOps != null) {

            QueryOptions effectiveOps = queryOps != null ? new QueryOptions(queryOps) : new QueryOptions();

            for (Filter f : Util.safeIterable(taOps.getFilters())) {
                effectiveOps.add(f);
            }

            QueryOptions noLimits = new QueryOptions(effectiveOps);
            noLimits.setResultLimit(0);

            count = countResults(TargetAssociation.class, noLimits);
            List<Map<String, Object>> results = getResults(TargetAssociation.class, effectiveOps);
            processedResults = convertResults(results);
        }

        return new ListResult(processedResults, count);
    }

    private QueryOptions getEffectiveSearchQueryOps(String identId) throws GeneralException {

        QueryOptions taOps = null;

        Set<String> objectIds = new HashSet<String>();

        //Type != null && granted_by_role = 0 - Entitlements & Permissions not granted by roles
        //OR
        //type == null && name='assignedRole' - Assigned roles
        //type == null && name='detectedRole' && allowed = 0 - Detected Roles not part of an assigned role

        //Fetch Name/Value for roles. Name - Detected/assigned (do we care?) Value - Role Name
        //Fetch Name/Value/application/type for Entitlements - Name - attribute, Value - ent value, type - Entitlement


        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity.id", identId));
        ops.add(Filter.or(Filter.and(Filter.eq("type", ManagedAttribute.Type.Entitlement.name()), Filter.eq("grantedByRole", false)),
                Filter.and(Filter.isnull("type"),
                        Filter.or(Filter.eq("name", "assignedRoles"),
                                Filter.and(Filter.eq("name", "detectedRoles"), Filter.eq("allowed", false))))));

        //Exclude sunrised entitlements.
        ops.add(Filter.isnull("startDate"));


        List projCol = Arrays.asList("name", "value", "application.id");

        Iterator<Object[]> it = getContext().search(IdentityEntitlement.class, ops, projCol);

        Set<String> roleNames = new HashSet<String>();
        List<Filter> maFilters = new ArrayList<Filter>();
        while (it.hasNext()) {
            Object[] obj = it.next();

            String name = (String) obj[0];
            String value = (String) obj[1];
            String app = (String) obj[2];

            if (!Util.nullSafeEq(name, "detectedRoles") && !Util.nullSafeEq(name, "assignedRoles")) {
                //Entitlement - See if we can find a MA with the given attr/value/app
                maFilters.add(Filter.and(Filter.eq("attribute", name), Filter.eq("value", value), Filter.eq("application.id", app)));

            } else {
                //Role - Batch the names up and query for ids after
                roleNames.add(value);
            }

        }

        //Get all RoleIds
        if (!Util.isEmpty(roleNames)) {
            it = getContext().search(Bundle.class, new QueryOptions(Filter.in("name", roleNames)), Arrays.asList("id"));
            while (it.hasNext()) {
                Object[] obj = it.next();
                objectIds.add((String) obj[0]);
            }

        }

        //Get all MA ids
        if (!Util.isEmpty(maFilters)) {
            // Each maFilter is an AND of three leaf filters. So to keep it (way) less than 1,000 parameters per query,
            // we'll batch these in groups of 100
            int i = 0;
            int queryEach = 100;
            List<Filter> orFilters = new ArrayList<Filter>();
            for (Filter maFilter : maFilters) {
                i++;
                orFilters.add(maFilter);
                if (i % queryEach == 0) {
                    objectIds.addAll(doEffectiveEntSearch(orFilters));
                    orFilters = new ArrayList<Filter>();
                }
            }

            // Don't forget the left overs
            if (i % queryEach != 0) {
                objectIds.addAll(doEffectiveEntSearch(orFilters));
            }
        }

        if (!Util.isEmpty(objectIds)) {
            //Get all Target Associations with the given ids
            taOps = new QueryOptions();
            taOps.add(Filter.in("objectId", objectIds));

        }

        return taOps;
    }

    private Set<String> doEffectiveEntSearch(List<Filter> orFilters) throws GeneralException {
        Set<String> ids = new HashSet<String>();
        if (!Util.isEmpty(orFilters)) {
            QueryOptions maOps = new QueryOptions();
            maOps.add(Filter.or(orFilters));
            Iterator<Object[]> it = getContext().search(ManagedAttribute.class, maOps, "id");
            while (it.hasNext()) {
                Object[] obj = it.next();
                ids.add((String) obj[0]);
            }
        }
        return ids;
    }

    /**
     * Return effective Access for the given identity for a specific Entitlement/Permission
     * @param identId - Id of the identity
     * @param appName - Name of the application in which the access resides
     * @param attributeName - Name of the attribute. If type null, this will map to the attributeName
     * @param targetName - Name of the target
     * @param rights - rights to search on
     * @param type - Type of access. if null, assume Attribute and use attributeName in place
     * @return
     */
    public ListResult getIdentityEffectiveAccess(String identId, String appName, String attributeName, String targetName, String rights, TargetAssociation.TargetType type)
        throws GeneralException {

        QueryOptions ops = getTargetedEffectiveQueryOptions(identId, appName, attributeName, targetName, rights, type);

        return getIdentityEffectiveAccess(identId, ops);

    }

    /**
     * Return effective Access for the given identity for a specific Entitlement/Permission
     * @param identId - Id of the identity
     * @param appName - Name of the application in which the access resides
     * @param attributeName - Name of the attribute. If type null, this will map to the attributeName
     * @param targetName - Name of the target
     * @param rights - rights to search on
     * @param type - Type of access. if null, assume Attribute and use attributeName in place
     * @return
     */
    public boolean hasEffectiveAccess(String identId, String appName, String attributeName, String targetName, String rights, TargetAssociation.TargetType type)
            throws GeneralException {

        boolean hasEffective = false;

        //Query Options searching for particular entitlement
        QueryOptions ops = getTargetedEffectiveQueryOptions(identId, appName, attributeName, targetName, rights, type);

        //Query Options to fetch all TargetAssociations for the given identity
        QueryOptions taOps = getEffectiveSearchQueryOps(identId);

        if (taOps != null) {
            for (Filter f : Util.safeIterable(taOps.getFilters())) {
                ops.add(f);
            }

            return getContext().countObjects(TargetAssociation.class, ops) > 0;

        }

        return hasEffective;

    }

    /**
     * True if the Identity has direct access to the given Entitlement/Permission.
     *
     * As of 7.3, this should only be used for UnstructuredTarget Permissions.
     *
     * @param identId
     * @param aggSource
     * @param targetname
     * @param rights
     * @return
     * @throws GeneralException
     */
    public boolean hasDirectAccess(String identId, String aggSource, String targetname, String rights)
        throws GeneralException {

        boolean hasDirect = false;

        if (identId != null) {
            Identity ident = this.context.getObjectById(Identity.class, identId);

            if (ident != null) {
                Set<String> linkIds = new HashSet<String>();
                for (Link l : Util.safeIterable(ident.getLinks())) {
                    linkIds.add(l.getId());
                }

                if (!Util.isEmpty(linkIds)) {
                    QueryOptions qo = new QueryOptions();

                    qo.add(Filter.in("objectId", linkIds));

                    if (targetname != null) {
                        // We need to match on the unique key for the target - which may be the hash of the name or fullPath.
                        String uniqueHash = Target.createUniqueHash(targetname);
                        qo.add(Filter.eq("target.uniqueNameHash", uniqueHash));
                    }

                    qo.add(Filter.or(Filter.eq("target.targetSource.name", aggSource),
                            Filter.eq("target.application.name", aggSource)));

                    qo.add(Filter.eq("rights", rights));

                    qo.add(Filter.eq("targetType", TargetAssociation.TargetType.TP.name()));

                    hasDirect = this.context.countObjects(TargetAssociation.class, qo) > 0;
                }
            }

        }

        return hasDirect;

    }

    private QueryOptions getTargetedEffectiveQueryOptions(String identId, String appName, String attributeName,
                                                          String targetName, String rights, TargetAssociation.TargetType type)
                                                                  throws GeneralException {
        QueryOptions ops = new QueryOptions();

        //IIQMAG-3096 :- build effective query option with displayableName and value
        List<String> targetNames = getTargetNames(appName, attributeName, targetName);
        //Should we join to Target table in case of target permission, or just rely on the target_name?
        ops.add(Filter.ignoreCase(Filter.in("targetName", targetNames)));
        if (Util.isNotNullOrEmpty(appName)) {
            //For Roles this will be the name of the App in which the MA lives
            Filter sourceFilter = Filter.eq("applicationName", appName);
            if (type == TargetAssociation.TargetType.TP) {
                //Need to include targetSource as well. We could pass this as another arg to avoid the case
                //Where an app and TargetSource have the same name. If both, false positives are possible, but unlikely.
                sourceFilter = Filter.or(Filter.eq("target.application.name", appName),
                        Filter.eq("target.targetSource.name", appName));
            }
            ops.add(sourceFilter);
        }

        if (type == null) {
            //Attribute
            ops.add(Filter.ignoreCase(Filter.eq("targetType", attributeName)));

        } else if (type == TargetAssociation.TargetType.P) {
            //Permission
            ops.add(Filter.eq("targetType", type.P.name()));

        } else if (type == TargetAssociation.TargetType.TP) {
            //TargetPermission
            ops.add(Filter.eq("targetType", type.TP.name()));
            ops.add(Filter.ignoreCase(Filter.eq("rights", rights)));
        }

        return ops;
    }

    /**
     * Helper Method that will return back the value and the displayable name
     * @param appName           The application name
     * @param attributeName     The attribute name such as "memberOf"
     * @param targetName        at this point we are receiving the value of the managed attribute
     *
     * @return List<String>     List of target names
     */
    private List<String> getTargetNames(String appName, String attributeName, String targetName)
            throws GeneralException {

        List<String> targetNames = new ArrayList<>();
        targetNames.add(targetName);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("attribute", attributeName));
        ops.add(Filter.eq("value", targetName));
        ops.add(Filter.eq("application.name", appName));

        List<String> projCol = Arrays.asList("displayableName");

        Iterator<Object[]> it = getContext().search(ManagedAttribute.class, ops, projCol);
        while (it.hasNext()) {
            Object[] obj = it.next();
            targetNames.add((String) obj[0]);
        }

        return targetNames;
    }

}
