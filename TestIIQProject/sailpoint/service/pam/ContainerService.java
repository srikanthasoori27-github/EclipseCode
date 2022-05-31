/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Target;
import sailpoint.object.TargetAssociation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A service to provide access to various properties of a PAM container/safe
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerService {
    private SailPointContext context;
    private Target target;
    private Application application;

    public static String OBJECT_TYPE_CONTAINER = "Container";
    public static String MA_ATTRIBUTE_NAMES = "privilegedData.display";
    public static String MA_ATTRIBUTE_TYPES = "privilegedData.type";
    public static String MA_ATTRIBUTE_VALUES = "privilegedData.value";


    public ContainerService(SailPointContext context, Target target, Application application) {
        this.context = context;
        this.target = target;
        this.application = application;
    }

    public ContainerService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Return a ContainerDTO for the given container id
     *
     * @param targetId The ID of the target we are interested in
     * @return
     * @throws GeneralException
     */
    public ContainerDTO getContainerDTO(String targetId) throws GeneralException {
        if (targetId == null) {
            throw new GeneralException("Target ID cannot be null for ContainerService.getContainerDTO().");
        }
        this.target = context.getObjectById(Target.class, targetId);
        if (this.target == null) {
            throw new GeneralException("Target cannot be null for ContainerService.getContainerDTO().");
        }

        this.application = PamUtil.getApplicationForTarget(context, target);

        return this.createContainerDTO(this.target, this.application, true);
    }

    /**
     * Create a ContainerDTO for the given Target on the given PAM application.
     *
     * @param target      The Target for which to create the ContainerDTO.
     * @param application The PAM application.
     * @param includeCounts Whether to run the queries over each target to get their counts
     * @return A ContainerDTO.
     */
    public ContainerDTO createContainerDTO(Target target, Application application, boolean includeCounts) throws GeneralException {
        ManagedAttribute ma =
                ManagedAttributer.get(this.context, application, false, null, target.getNativeObjectId(), OBJECT_TYPE_CONTAINER);
        ContainerDTO dto = new ContainerDTO(target, ma, application.getName());
        if(includeCounts) {
            dto.setIdentityTotalCount(this.getIdentityTotalCount());
            dto.setPrivilegedItemCount(this.getPrivilegedItemsCount());
            dto.setGroupCount(this.getGroupsCount());
        }
        return dto;
    }

    /**
     * Returns the query options for querying over the identities that have direct access
     * to a container/safe
     *
     * @return QueryOptions
     * @throws GeneralException
     */
    public QueryOptions getIdentityDirectQueryOptions() throws GeneralException {
        if (this.target != null) {
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            qo.add(Filter.join("links.id", "TargetAssociation.objectId"));
            qo.add(Filter.eq("TargetAssociation.target.id", this.target.getId()));
            qo.add(Filter.eq("TargetAssociation.ownerType", TargetAssociation.OwnerType.L.name()));
            return qo;
        } else {
            throw new GeneralException("Target cannot be null for identity query on container.");
        }
    }

    /**
     * Return the count of identities who have direct access to the container
     *
     * @return the count of identities
     */
    public int getIdentityDirectCount() throws GeneralException {
        return this.context.countObjects(Identity.class, this.getIdentityDirectQueryOptions());
    }

    /**
     * Returns the query options for querying over the identities that have indirect access
     * to a container/safe
     *
     * @return QueryOptions
     * @throws GeneralException
     */
    public QueryOptions getIdentityEffectiveQueryOptions() throws GeneralException {
        return getIdentityEffectiveQueryOptions(Identity.class);
    }

    /**
     * Returns the query options for querying over the identities that have indirect access
     * to a container/safe
     *
     * @param queryClass  The class that is being queried against - either Identity or IdentityEntitlement.
     *
     * @return QueryOptions
     * @throws GeneralException
     */
    public QueryOptions getIdentityEffectiveQueryOptions(Class<? extends SailPointObject> queryClass) throws GeneralException {
        if (this.target != null) {
            String idEntPrefix = "";
            boolean joinToIdEnt = false;

            if (Identity.class.equals(queryClass)) {
                idEntPrefix = "IdentityEntitlement.";
                joinToIdEnt = true;
            }

            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);

            // Tricky stuff going on here...  We need to find all identities that are members of groups that have
            // target permissions on a safe.  There are two types of PAM groups - local and external.  A local PAM
            // group is a group that is defined on the PAM system and whose membership is maintained with local PAM
            // accounts.  An external PAM group is a group whose membership lives on an external system (eg - an
            // Active Directory group).  This group has a "stub" group on the PAM system, on which the target
            // permissions are mapped to the safe.  Pay attention....

            // First, figure out the group members for all groups by joining the IdentityEntitlements to the
            // ManagedAttribute.
            if (joinToIdEnt) {
                qo.add(Filter.join("id", idEntPrefix + "identity.id"));
            }

            // Only return connected identity entitlements (ones that still exist).
            qo.add(Filter.eq(idEntPrefix + "aggregationState", IdentityEntitlement.AggregationState.Connected));
            // Join IdentityEntitlement to ManagedAttribute.
            qo.add(Filter.join(idEntPrefix + "name", "ManagedAttribute.attribute"));
            qo.add(Filter.join(idEntPrefix + "value", "ManagedAttribute.value"));
            qo.add(Filter.join(idEntPrefix + "application", "ManagedAttribute.application"));

            // Next, we only look for ManagedAttributes (groups) that are associated with this target.  This subquery
            // takes care of local PAM groups because the group membership and target association are attached to the
            // same ManagedAttribute.
            Filter taFilter = Filter.and(Filter.eq("target.id", this.target.getId()),
                    Filter.eq("ownerType", TargetAssociation.OwnerType.A.name()));
            Filter pamGroupFilter =
                Filter.subquery("ManagedAttribute.id", TargetAssociation.class, "objectId", taFilter);

            // Finally, also look for external groups that are associated with this target.  This is a bit trickier
            // because the TargetAssociation is related to the PAM "stub" group ManagedAttribute, but the membership
            // is associated with the AD group ManagedAttribute.  To join between these two worlds, we are promoting
            // the external group's native identifier to a correlation key on the stub PAM group.  This subquery is
            // looking for any PAM stub group with a sourceNativeIdentifier (the correlation key) that matches the
            // external group's native identity (ie - the ManagedAttribute.value) ... and of course is limiting this
            // to stub PAM groups that have TargetAssociations on this container.
            String linkedGroupNativeIdAttr = getManagedAttributeSourceNativeIdentityAttr();
            if(linkedGroupNativeIdAttr != null) {
                Filter maFilter =
                        Filter.and(Filter.join("id", "TargetAssociation.objectId"),
                                Filter.eq("TargetAssociation.target.id", this.target.getId()),
                                Filter.eq("TargetAssociation.ownerType", TargetAssociation.OwnerType.A.name()));
                Filter externalGroupFilter =
                        Filter.subquery("ManagedAttribute.value", ManagedAttribute.class, linkedGroupNativeIdAttr, maFilter);

                // We need to OR these together since people can get indirect access through local or external groups.
                qo.add(Filter.or(pamGroupFilter, externalGroupFilter));
            } else {
                qo.add(pamGroupFilter);
            }
            return qo;
        } else {
            throw new GeneralException("Target cannot be null for identity query on container.");
        }
    }

    /**
     * Return the attribute name on ManagedAttribute for the correlation key that has the source native identifier.
     */
    private String getManagedAttributeSourceNativeIdentityAttr() throws GeneralException {
        PamExternalUserStoreService svc = new PamExternalUserStoreService(this.context, target);
        return svc.getNativeIdentifierCorrelationKey(false);
    }

    /**
     * Returns the count of identities who have access to a PAM container indirectly (they are a member of a
     * group that gives them access to one or more rights on the container/safe)
     *
     * @return the count of identities
     * @throws GeneralException
     */
    public int getIdentityEffectiveCount() throws GeneralException {
        return this.context.countObjects(Identity.class, this.getIdentityEffectiveQueryOptions());
    }

    /**
     * Get the total count of all identities that have access to the safe/container
     *
     * @return the count of identities
     * @throws GeneralException
     */
    public int getIdentityTotalCount() throws GeneralException {
        if (this.target != null) {
            Map<String, Object> queryArgs = new HashMap<>();
            queryArgs.put("targetId", target.getId());
            queryArgs.put("targetType1", TargetAssociation.OwnerType.A.name());
            queryArgs.put("targetType2", TargetAssociation.OwnerType.L.name());
            Iterator<?> it = null;
            try {
                it = this.context.search(getIdentityTotalCountHQL(), queryArgs, null);
                if (it.hasNext()) {
                    Object o = it.next();
                    if (o != null) {
                        return ((Long) o).intValue();
                    }
                }
            } finally {
                Util.flushIterator(it);
            }
        } else {
            throw new GeneralException("Target cannot be null for identity query on container.");
        }

        return 0;
    }

    /**
     * Return a query that returns the identities who have either indirect access or direct access to the PAM
     * container/safe.  We are using HQL here because this query is complicated enough to where we can't do it with
     * filters.
     */
    private String getIdentityTotalCountHQL() throws GeneralException {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(
            "SELECT count(distinct i.id) " +
            "  FROM Identity i " +

            // QUERY FOR GROUP/INDIRECT ACCESS THROUGH A PAM GROUP
            " WHERE i.id IN " +
            "  (SELECT distinct i1.id" +
            "     FROM IdentityEntitlement ie1 inner join ie1.identity i1, " +
            "          ManagedAttribute ma1," +
            "          TargetAssociation ta1" +
            "    WHERE (ie1.name = ma1.attribute AND ie1.value = ma1.value AND ie1.application = ma1.application)" +
            "      AND ma1.id = ta1.objectId" +
            "      AND ie1.aggregationState = 'Connected' " +
            "      AND ta1.ownerType = (:targetType1)" +
            "      AND ta1.target.id = (:targetId)" +
            " ) ");

        String externalNativeIdAttr = this.getManagedAttributeSourceNativeIdentityAttr();
        if(externalNativeIdAttr != null) {
            // QUERY FOR GROUP/INDIRECT ACCESS THROUGH AN EXTERNAL GROUP
            queryBuilder.append(
                " OR i.id IN " +
                "  (SELECT distinct i1.id " +
                "     FROM IdentityEntitlement ie1 inner join ie1.identity i1, " +
                "          ManagedAttribute externalMa, " +
                "          ManagedAttribute pamMa, " +
                "          TargetAssociation ta1 " +
                "    WHERE (ie1.name = externalMa.attribute AND ie1.value = externalMa.value AND ie1.application = externalMa.application) " +
                "      AND pamMa." + externalNativeIdAttr + " = externalMa.value " +
                "      AND pamMa.id = ta1.objectId " +
                "      AND ie1.aggregationState = 'Connected' " +
                "      AND ta1.ownerType = (:targetType1) " +
                "      AND ta1.target.id = (:targetId) " +
                " ) ");
        }

        // QUERY FOR DIRECT ACCESS
        queryBuilder.append(
            " OR i.id IN " +
            "  (SELECT distinct i2.id" +
            "     FROM Link l2 inner join l2.identity i2, " +
            "          TargetAssociation ta2" +
            "    WHERE l2.id = ta2.objectId" +
            "      AND ta2.ownerType = (:targetType2)" +
            "      AND ta2.target.id = (:targetId)" +
            " )");

        return queryBuilder.toString();
    }

    /**
     * Return a list of privileged items for a given container.  These are stored in the managed attribute that represents
     * this container.  They are stored in an attribute on the MA in the 'accounts' attribute.
     * <p>
     * Convert them to a list of ContainerPrivilegedItemDTO
     *
     * @return the list of privileged items on the safe
     * @throws GeneralException
     */
    public List<Map<String, Object>> getPrivilegedItems() throws GeneralException {
        if (this.application == null) {
            this.application = PamUtil.getApplicationForTarget(context, target);

            if (this.application == null) {
                throw new GeneralException("Application cannot be null for privileged item query on container.");
            }
        }

        if (this.target != null) {
            List<Map<String, Object>> items = new ArrayList<>();

            ManagedAttribute ma =
                    ManagedAttributer.get(context, this.application.getId(), false, null, this.target.getNativeObjectId(), OBJECT_TYPE_CONTAINER);
            if (ma != null) {
                //The list of privileged items are stored on the managedAttribute in the accounts attribute
                List<String> names = Util.otol(ma.getAttribute(MA_ATTRIBUTE_NAMES));
                List<String> types = Util.otol(ma.getAttribute(MA_ATTRIBUTE_TYPES));
                List<String> values = Util.otol(ma.getAttribute(MA_ATTRIBUTE_VALUES));
                if (names != null && types != null) {
                    for (int i = 0; i < names.size(); i++) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("name", names.get(i));
                        if (types.size() > i) {
                            result.put("type", types.get(i));
                        }
                        if (values.size() > i) {
                            result.put("value", values.get(i));
                        }
                        items.add(result);
                    }
                }
            }

            return items;
        } else {
            throw new GeneralException("Target cannot be null for privileged item query on container.");
        }
    }

    /**
     * Return a simple count of the privileged items that are contained in a container/safe
     *
     * @return The count of items
     * @throws GeneralException
     */
    public int getPrivilegedItemsCount() throws GeneralException {
        List<Map<String, Object>> items = this.getPrivilegedItems();
        return Util.isEmpty(items) ? 0 : items.size();
    }


    /**
     * Returns the query options for querying over the groups that have access to a container/safe
     * In the case of PAM containers, each group is stored as a TargetAssociation with the OwnerType set to 'A' for
     * managed attribute.
     * <p>
     * For each group, it can be stored multiple times if the group has multiple permissions on the container, so
     * we need to join to the ManagedAttribute table and only return the distinct managed attributes
     *
     * @return QueryOptions
     * @throws GeneralException
     */
    public QueryOptions getGroupQueryOptions() throws GeneralException {
        if (this.target != null) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.join("id", "TargetAssociation.objectId"));
            qo.add(Filter.eq("TargetAssociation.target.id", this.target.getId()));
            qo.add(Filter.eq("TargetAssociation.ownerType", TargetAssociation.OwnerType.A.name()));
            qo.setDistinct(true);
            return qo;
        } else {
            throw new GeneralException("Target cannot be null for group query on container.");
        }
    }

    /**
     * Get the count of the groups on the safe/container
     *
     * @return
     * @throws GeneralException
     */
    public int getGroupsCount() throws GeneralException {
        return this.context.countObjects(ManagedAttribute.class, this.getGroupQueryOptions());
    }

    /**
     * Return a non-null map of Link to direct permissions for the given identity on the container.
     *
     * @param identityId  The ID of the Identity for which to return the direct permissions.
     *
     * @return A non-null map of Link to direct permissions for the given identity on the container.
     */
    public Map<Link,List<Permission>> getDirectPermissionsForIdentityOnTarget(String identityId) throws GeneralException {
        Map<Link,List<Permission>> permissionsByLink = new HashMap<>();

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity.id", identityId));
        qo.add(Filter.join("id", "TargetAssociation.objectId"));
        qo.add(Filter.eq("TargetAssociation.target.id", target.getId()));
        qo.add(Filter.eq("TargetAssociation.ownerType", TargetAssociation.OwnerType.L.name()));

        List<Link> links = this.context.getObjects(Link.class, qo);
        for (Link link : links) {
            Permission permission = ObjectUtil.getTargetPermission(this.context, link, this.target.getName());
            if (null != permission) {
                List<Permission> permissionsOnTarget = new ArrayList<>();
                permissionsOnTarget.add(permission);
                permissionsByLink.put(link, permissionsOnTarget);
            }
        }

        return permissionsByLink;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    /**
     * Convenience function for getting the display name of a target
     * @param target
     * @param ma
     * @return
     */
    public static String getTargetDisplayName(Target target, ManagedAttribute ma) {
        String name = null;
        if (null != ma) {
            name = ma.getDisplayName();

            // Fallback to the value of the ManagedAttribute
            if(Util.isNullOrEmpty(name)) {
                name = ma.getValue();
            }
        }

        if (Util.isNullOrEmpty(name)) {
            name = target.getDisplayableName();
        }
        return name;
    }
}