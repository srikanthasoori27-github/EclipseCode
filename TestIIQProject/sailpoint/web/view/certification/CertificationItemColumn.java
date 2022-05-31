/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.web.Authorizer;
import sailpoint.web.view.DefaultColumn;


/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationItemColumn extends DefaultColumn {

    protected static String CACHE_USR_CERT_ROLE = "userCertRole";

    protected final static String COL_ID = "id";
    protected final static String COL_CERT_ID = "parent.certification.id";
    protected final static String COL_CERT_ENTITY_ID = "parent.id";
    protected final static String COL_CERT_DEF_ID = "parent.certification.certificationDefinitionId";
    public final static String COL_CERT_TYPE = "parent.certification.type";
    protected final static String COL_CERT_ENTITY_TYPE = "parent.type";
    protected final static String COL_CERT_ITEM_TYPE = "type";
    protected final static String COL_IDENTITY = "parent.identity";
    protected final static String COL_ROLE = "bundle";
    protected static final String COL_EXCEPTION_ENTITLEMENTS = "exceptionEntitlements";
    protected static final String COL_POLICY_VIOLATION = "policyViolation";

    public final static String BUILDER_ATTR_WORKITEM_ID = "workItemid";
    protected final static String BUILDER_ATTR_ENTITY_CACHE = "certEntityCache";
    protected final static String BUILDER_ATTR_CERT = "certification";
    protected final static String BUILDER_ATTR_CERT_DEF = "certificationDefinition";

    protected final static String ROW_ATTR_ITEM = "certificationItem";
    protected final static String ROW_ATTR_IDENTITY = "certificationIdentity";
    protected final static String ROW_ATTR_ROLE = "bundle";

    private static final int MAX_IDENTITY_NAME_LENGTH = 128;

    // Special keyword to look for which indicates we need to evaluate
    // a property on the role for the given cert item. property should look like
    // role.description
    protected final static String KEYWORD_ROLE = "IIQ_role";


    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_ID);
        cols.add(COL_CERT_ID);
        cols.add(COL_CERT_DEF_ID);
        cols.add(COL_CERT_ENTITY_ID);
        cols.add(COL_CERT_TYPE);
        cols.add(COL_IDENTITY);
        cols.add(COL_ROLE);

        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException{

        String property = getColumnConfig().getProperty();
        if (row.containsKey(property))
            return row.get(property);

        if (property.startsWith(KEYWORD_ROLE + ".")){
            Bundle role = getRole(row);
            return evaluate(role, property.substring(9, property.length()));
        }

        CertificationItem item = getCertificationItem(row);
        Object val = null;
        if (item != null){
            val = evaluate(item, property);
        }

        return val;
    }

    protected Bundle getRole(Map<String, Object> row) throws GeneralException{

        String roleName = (String)row.get(COL_ROLE);

        if (roleName == null)
            return null;

        if (getContext().getRowAttributes().containsKey(ROW_ATTR_ROLE)){
            return (Bundle)getContext().getRowAttributes().get(ROW_ATTR_ROLE);
        }

        Bundle role = getContext().getSailPointContext().getObjectByName(Bundle.class, roleName);
        getContext().getRowAttributes().put(ROW_ATTR_ROLE, role);

        return role;
    }
    
    protected PolicyViolation getPolicyViolation(Map<String, Object> row) throws GeneralException {
        if (row.containsKey(COL_POLICY_VIOLATION)) {
            return (PolicyViolation)row.get(COL_POLICY_VIOLATION);
        }
        
        return null;
    }

    protected Certification.Type getCertificationType(Map<String, Object> row) throws GeneralException {
        return (Certification.Type)row.get(COL_CERT_TYPE);
    }

    protected CertificationItem.Type getCertificationItemType(Map<String, Object> row) throws GeneralException {
        return (CertificationItem.Type)row.get(COL_CERT_ITEM_TYPE);
    }

    protected CertificationItem getCertificationItem(Map<String, Object> row) throws GeneralException {

        if (getContext().getRowAttributes().containsKey(ROW_ATTR_ITEM)){
            return (CertificationItem)getContext().getRowAttributes().get(ROW_ATTR_ITEM);
        }

        CertificationItem item = getContext().getSailPointContext().getObjectById(CertificationItem.class,
                (String)row.get(COL_ID));
        getContext().getRowAttributes().put(ROW_ATTR_ITEM, item);
        return item;
    }

    protected Certification getCertification(Map<String, Object> row) throws GeneralException {

        if (getContext().getBuilderAttributes().containsKey(BUILDER_ATTR_CERT)){
            return (Certification)getContext().getBuilderAttributes().get(BUILDER_ATTR_CERT);
        }

        Certification cert = getContext().getSailPointContext().getObjectById(Certification.class, (String)row.get(COL_CERT_ID));
        getContext().getBuilderAttributes().put(BUILDER_ATTR_CERT, cert);
        return cert;
    }

    protected Identity getIdentity(Map<String, Object> row) throws GeneralException {

        String identityName = (String)row.get(COL_IDENTITY);
        if (identityName == null)
            return null;

        if (getContext().getRowAttributes().containsKey(ROW_ATTR_IDENTITY)){
            return (Identity)getContext().getRowAttributes().get(ROW_ATTR_IDENTITY);
        }

        Identity identity = null;
        // bug27722 - We need to check to see if the identityName here is too big, as it's a concatenation,
        // I.e. "value ... on ..." where the first part is the value column on spt_identity_entitlement, which
        // could be up to 450 chars. In non-DB2 cases, the query will return null, but DB2 throws an
        // exception if the select has a where clause with values greater than the size of the column
        if (identityName.length() <= MAX_IDENTITY_NAME_LENGTH) {
            identity = getContext().getSailPointContext().getObjectByName(Identity.class, identityName);
        }
        getContext().getRowAttributes().put(ROW_ATTR_IDENTITY, identity);
        return identity;
    }

    protected CertificationDefinition getCertificationDefinition(Map<String, Object> row) throws GeneralException {

        CertificationDefinition def = null;
        if (getContext().getBuilderAttributes().containsKey(BUILDER_ATTR_CERT_DEF)){
            def = (CertificationDefinition)getContext().getBuilderAttributes().get(BUILDER_ATTR_CERT_DEF);
        }

        if (def == null){
            String id = (String)row.get(COL_CERT_DEF_ID);

            if (id != null){
                def = getContext().getSailPointContext().getObjectById(CertificationDefinition.class, id);
                if (def != null)
                    getContext().getBuilderAttributes().put(BUILDER_ATTR_CERT_DEF, def);
            }
        }

        if (def == null){
            def = new CertificationDefinition();
            def.initialize(getContext().getSailPointContext());
        }

        return def;
    }

    protected CertificationEntity getCertificationEntity(Map<String, Object> row) throws GeneralException {

        String id = (String)row.get(COL_CERT_ENTITY_ID);

        if (!getContext().getBuilderAttributes().containsKey(BUILDER_ATTR_ENTITY_CACHE)){
            getContext().getBuilderAttributes().put(BUILDER_ATTR_ENTITY_CACHE,
                    new HashMap<String, CertificationEntity>());
        }

        Map<String, CertificationEntity> entityCache =
                (Map<String, CertificationEntity>)getContext().getBuilderAttributes().get(BUILDER_ATTR_ENTITY_CACHE);

        if (entityCache.containsKey(id))
            return entityCache.get(id);

        CertificationEntity entity = getContext().getSailPointContext().getObjectById(CertificationEntity.class, id);

        if (entity != null)
            entityCache.put(entity.getId(), entity);

        return entity;
    }



    protected String getWorkItemId(){
        return getContext().getBuilderAttributes().getString(BUILDER_ATTR_WORKITEM_ID);
    }

    protected Role getCertRole(Map<String, Object> row) throws GeneralException{

        Role role = (Role)getContext().getRowAttributes().get(CACHE_USR_CERT_ROLE);

        if (role == null){
            role = new Role(getContext().getSailPointContext(), getContext().getUserContext().getLoggedInUser(),
                    getContext().getUserContext().getLoggedInUserRights(),
                    getCertificationItem(row), getCertificationEntity(row), getWorkItemId());
            getContext().getRowAttributes().put(CACHE_USR_CERT_ROLE, role);
        }

        return role;
    }

    /**
     * A helper class that holds the current user's relation to an entitlement
     * and the context in which they are viewing the entitlement.  This is used
     * to help figure out whether an entitlement is read-only, how it should be
     * displayed on the current page, etc...  All properties are public, but
     * should be considered read-only - I was just too lazy to make getters for
     * all of them.
     */
    public static class Role {
        boolean isItemActionActor;
        boolean isItemDelegationRequester;
        boolean isCertifierItemDelegationRequester;
        boolean isItemDelegationOwner;
        boolean isIdentityDelegationRequester;
        boolean isIdentityDelegationOwner;
        boolean isCertificationOwner;
        boolean isViewingCertification;
        boolean isViewingItemWorkItem;
        boolean isViewingIdentityWorkItem;
        boolean wasItemDecidedDuringIdentityDelegation;
        boolean wasItemDecidedOutsideOfIdentityDelegation;

        /**
         * Constructor.
         */
        public Role(SailPointContext ctx, Identity loggedInUser, Collection<String> flattenedUserRights,
                    CertificationItem item,  CertificationEntity identity, String workItem)
            throws GeneralException {

            CertificationAction action = item.getAction();
            CertificationDelegation itemDel = item.getDelegation();
            CertificationDelegation identityDel = identity.getDelegation();
            Certification cert = identity.getCertification();

            Identity itemActionActor = (null != action) ? action.getActor(ctx) : null;
            Identity itemDelActor = (null != itemDel) ? itemDel.getActor(ctx) : null;
            Identity identityDelActor = (null != identityDel) ? identityDel.getActor(ctx) : null;
            String itemOwner = (null != itemDel) ? itemDel.getOwnerName() : null;
            String identityOwner = (null != identityDel) ? identityDel.getOwnerName() : null;

            // Owners get reset when forwarded.
            List<String> certifiers = cert!=null ? cert.getCertifiers() : null;

            isCertificationOwner = CertificationAuthorizer.isCertifier(loggedInUser, certifiers);
            // If not the owner, a "certification admin" should be treated as an
            // owner (ie - have full read/write access).
            if (!isCertificationOwner) {
                isCertificationOwner = Authorizer.hasAccess(loggedInUser.getCapabilityManager().getEffectiveCapabilities(),
                        flattenedUserRights,
                        new String[]{SPRight.CertifyAllCertifications});
            }

            // Allow owners of parents of reassignments act as owners.
            if (!isCertificationOwner) {
                isCertificationOwner =
                    CertificationAuthorizer.isReassignmentParentOwner(loggedInUser, cert);
            }

            isItemDelegationOwner = matchesOwner(loggedInUser, itemOwner);
            isIdentityDelegationOwner = matchesOwner(loggedInUser, identityOwner);

            // Actors don't get reset when forwarded (we retain them for
            // auditing), so we might have to look through the work item owner
            // history to see if the actor was a previous owner.
            isItemActionActor =
                usersEqual(itemActionActor, loggedInUser) ||
                hasBuckBeenPassed(ctx, action, cert, loggedInUser);
            isItemDelegationRequester =
                usersEqual(itemDelActor, loggedInUser) ||
                hasBuckBeenPassed(ctx, itemDel, cert, loggedInUser);
            isIdentityDelegationRequester =
                usersEqual(identityDelActor, loggedInUser) ||
                hasBuckBeenPassed(ctx, identityDel, cert, loggedInUser);

            // Check if this item was delegated by the certifier.
            isCertifierItemDelegationRequester =
                ((null != itemDel) && (null == itemDel.getActingWorkItem()));

            // Figure out if an item was decided outside of an identity
            // delegation.
            boolean actionOccurredInCert =
                ((null != action) && (null == action.getActingWorkItem()));
            wasItemDecidedDuringIdentityDelegation =
                item.wasDecidedInIdentityDelegationChain(identityDel);
            wasItemDecidedOutsideOfIdentityDelegation =
                actionOccurredInCert ||
                ((null != action) && !wasItemDecidedDuringIdentityDelegation);

            isViewingCertification = (null == workItem);
            isViewingItemWorkItem = isViewingWorkItem(workItem, itemDel);
            isViewingIdentityWorkItem = isViewingWorkItem(workItem, identityDel);

        }

        private static boolean matchesOwner(Identity user, String target) {
            return (usersEqual(target, user) ||
                    CertificationAuthorizer.isAssignedWorkgroup(user, target));
        }
        
        private static boolean usersEqual(String user1Name, Identity user2) {
            if ((null != user1Name) && (null != user2)) {
                return user1Name.equals(user2.getName());
            }
            return false;
        }

        private static boolean usersEqual(Identity user1, Identity user2) {
            if ((null != user1) && (null != user2)) {
                return user1.equals(user2);
            }
            return false;
        }

        /**
         * Check to see if the given monitor was acted upon in a work item that
         * is now owned by the given loggedInUser.
         */
        private boolean hasBuckBeenPassed(SailPointContext ctx,
                                          WorkItemMonitor monitor,
                                          Certification cert,
                                          Identity loggedInUser)
            throws GeneralException {

            if (null != monitor) {
                Identity actor = monitor.getActor(ctx);
                if (null != actor) {

                    List<WorkItem> items = null;
                    String actingWorkItemId = monitor.getActingWorkItem();
                    if (null != actingWorkItemId) {
                        WorkItem item =
                            ctx.getObjectById(WorkItem.class, actingWorkItemId);
                        if (null != item) {
                            items = new ArrayList<WorkItem>();
                            items.add(item);
                        }
                    }
                    else {
                        // No acting work item - this was done in the cert.
                        items = cert != null ? cert.getWorkItems() : null;
                    }

                    if (null != items) {
                        // Buck has been passed if the logged in user is the
                        // current owner of the work item and the actor is in
                        // the owner history.
                        for (WorkItem item : items) {
                            if (loggedInUser.equals(item.getOwner()) &&
                                identityInOwnerHistory(actor, item.getOwnerHistory())) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Check if the given actor was once an owner in the given owner history
         * list.
         */
        private boolean identityInOwnerHistory(Identity actor,
                                               List<WorkItem.OwnerHistory> history) {

            if ((null != actor) && (null != history)) {
                for (WorkItem.OwnerHistory historyEntry : history) {
                    if (actor.getDisplayName().equals(historyEntry.getOldOwnerDisplayName())) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Return whether or not we're currently viewing the work item for the
         * given delegation.
         *
         * @param  workItem    The ID of the work item currently being viewed.
         * @param  delegation  The delegation to compare against.
         *
         * @return True if we're currently viewing the work item for the given
         *         delegation, false otherwise.
         */
        private static boolean isViewingWorkItem(String workItem,
                                                 CertificationDelegation delegation) {
            String delWorkItem = (null != delegation) ? delegation.getWorkItem() : null;
            return (null != delWorkItem) && delWorkItem.equals(workItem);

        }
    }


}
