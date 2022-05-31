/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.integration.ListResult;
import sailpoint.object.CertifiableDescriptor;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.IdentityHistoryItem.Type;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.policy.AccountPolicyExecutor;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Service class used to query for identity history items. 
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class IdentityHistoryService extends BaseListService<BaseListServiceContext> {
    public static final String COL_COMMENTS = "comments";
    public static final String COL_ID = "id";

    private static final String ACTOR = "actor";
    private static final String STATUS = "status";
    private static final String ENTRY_DATE = "entryDate";
    private static final String COMMENTS = "comments";

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    public IdentityHistoryService(SailPointContext context) {
        super(context, null, null);
    }

    public IdentityHistoryService(BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(listServiceContext.getContext(), listServiceContext, columnSelector);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // QUERY BUILDING
    //
    ////////////////////////////////////////////////////////////////////////////

    public static QueryOptions buildQuery(QueryOptions ops,
                                          IdentityHistoryItem.Type type,
                                          String identityId,
                                          CertificationItem item) {
        if (item.getPolicyViolation() != null) {
            ops = buildQuery(ops, type, identityId, item.getPolicyViolation());
        }
        else if (item.getBundle() != null) {
            ops = buildQuery(ops, type, identityId, item.getBundle());
        }
        else  if (item.getExceptionEntitlements() != null) {
            ops = buildQuery(ops, type, identityId,
                             Arrays.asList(item.getExceptionEntitlements()),
                             item.getCertification().getEntitlementGranularity());
        } else { //TODO handle more items - like ones for role profiles etc
            ops = buildBaseQuery(ops, type, identityId);
        }

        return ops;
    }
    
    private static QueryOptions buildBaseQuery(QueryOptions ops, IdentityHistoryItem.Type type, 
                                               String identityId) {
        if (null == ops)
            ops = new QueryOptions();
        
        ops.add(Filter.eq("identity.id", identityId));
        if (type != null)
            ops.add(Filter.eq("type", type));

        return ops;
    }

    /**
     * Generic query builder which can be used when getting counts or fetching objects.
     */
    private static QueryOptions buildQuery(QueryOptions ops, IdentityHistoryItem.Type type, 
                                           String identityId, String role) {
        
        ops = buildBaseQuery(ops, type, identityId);
        if (role != null)
            ops.add(Filter.eq("role", role));

        return ops;
    }

    private static QueryOptions buildQuery(QueryOptions ops, IdentityHistoryItem.Type type, 
                                           String identityId, PolicyViolation violation) {
        
        ops = buildBaseQuery(ops, type, identityId);

        if (violation != null) {
            if (violation.getPolicyName() != null)
                ops.add(Filter.eq("policy", violation.getPolicyName()));
            if (violation.getConstraintName() != null)
                ops.add(Filter.eq("constraintName", violation.getConstraintName()));
            if (violation.getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME) != null) {
                // bug 26326 add application name as a search parameter for account 
                // policy violations
                ops.add(Filter.eq("application", violation.getArgument(AccountPolicyExecutor.VIOLATION_APP_NAME)));
            }
        }

        return ops;
    }

    private static QueryOptions buildQuery(QueryOptions ops, IdentityHistoryItem.Type type, 
                                           String identityId, List<EntitlementSnapshot> entitlements,
                                           Certification.EntitlementGranularity granularity) {
        ops = buildBaseQuery(ops, type, identityId);

        if (entitlements != null && entitlements.size() > 0) {

            Set<String> apps = new HashSet<String>();
            Set<String> nativeIdentities = new HashSet<String>();

            List<Filter> instanceFilter = new ArrayList<Filter>();
            boolean hasNullInstance = false;
            for(EntitlementSnapshot snap : entitlements){
                apps.add(snap.getApplicationName());
                nativeIdentities.add(snap.getNativeIdentity());
                if (snap.getInstance() != null){
                    instanceFilter.add(Filter.eq("instance", snap.getInstance()));
                } else if (!hasNullInstance) {
                    hasNullInstance =  true;
                    instanceFilter.add(Filter.isnull("instance"));
                }
            }

            if (!instanceFilter.isEmpty())
                ops.add(Filter.or(instanceFilter));
            ops.add(Filter.in("application", apps));
            ops.add(Filter.in("nativeIdentity", nativeIdentities));

            if (entitlements.size() == 1){
                EntitlementSnapshot snap = entitlements.get(0);

                // if we have a single attribute or permission we can query by the permission target
                // or attribute name. Additionally, if the permission or attribute only have a single
                // right or attribute value we can also query by that.
                if (Certification.EntitlementGranularity.Application.equals(granularity) ||
                        snap.isAccountOnly()){
                    ops.add(Filter.isnull("attribute"));
                    ops.add(Filter.isnull("value"));
                } else if (Certification.EntitlementGranularity.Attribute.equals(granularity) ||
                        Certification.EntitlementGranularity.Value.equals(granularity)) {
                    String attributeFilter = null;
                    String valueFilter = null;
                    if (snap.hasAttributes()) {
                        attributeFilter = snap.getAttributes().getKeys().get(0);
                        if (Certification.EntitlementGranularity.Value.equals(granularity)) {
                            Object attrObj = snap.getAttributes().get(attributeFilter);
                            if (attrObj != null) {
                                Object valueObject =Util.asList(attrObj).get(0);
                                if (valueObject != null) {
                                    valueFilter = valueObject.toString();
                                }
                            }
                        }
                    } else if (snap.hasPermissions()) {
                        attributeFilter = snap.getPermissions().get(0).getTarget();
                        if (Certification.EntitlementGranularity.Value.equals(granularity))
                            valueFilter = snap.getPermissions().get(0).getRightsList().get(0);
                    }

                    if (attributeFilter != null)
                        ops.add(Filter.eq("attribute", attributeFilter));

                    if (Certification.EntitlementGranularity.Value.equals(granularity)){
                        if (valueFilter != null)
                            ops.add(Filter.eq("value", valueFilter));
                    } else {
                        ops.add(Filter.isnull("value"));
                    }
                }
            }
        }

        return ops;
    }

    @Override
    protected Object convertColumn(Map.Entry<String,Object> entry, ColumnConfig config, Map<String,Object> rawObject ) throws GeneralException {

        Object value = super.convertColumn(entry, config, rawObject);
        if (COL_COMMENTS.equals(config.getProperty()) && Util.isNullOrEmpty((String)value)) {
            // Need to load the item and get the history comments directly
            String historyId = (String)rawObject.get(COL_ID);
            if (historyId != null) {
                IdentityHistoryItem item = getContext().getObjectById(IdentityHistoryItem.class, historyId);
                value = item.getHistoryComments();
            }
        }
        
        return value;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Identity History
    //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Get identity history by cert item
     *
     * @param identityId
     * @param item certification item
     * @return List result with details about the history items
     * @throws GeneralException
     */
    public ListResult getIdentityHistory(String identityId, CertificationItem item) throws GeneralException {
        QueryOptions qo = createQueryOptions();

        qo = buildQuery(qo, null, identityId, item);

        int resultCount = countResults(IdentityHistoryItem.class, qo);

        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();

        if (resultCount  > 0) {
            results = getResults(IdentityHistoryItem.class, qo);
        }

        return new ListResult(results, resultCount);
    }

    /**
     * Gets a map representation of a pending decision for the given cert item. This reflects a decision
     * on an unsigned cert which will not have a corresponding IdentityRequestItem yet, so will not be included
     * in getIdentityHistory results
     * @param identityId Identity ID
     * @param item CertificationItem
     * @return Null if there is no non-included pending decision, or map if there is
     * @throws GeneralException
     */
    public Map<String, Object> getPendingDecision(String identityId, CertificationItem item) throws GeneralException {
        CertificationAction itemAction = item.getAction();
        Map<String, Object> pendingDecision = null;

        if (itemAction != null && !item.getCertification().hasBeenSigned()) {
            // Get the decision details from the action, then search for a matching history item.
            // If we cant find it, then include as a pendingDecision in the metadata
            QueryOptions qo = buildQuery(null, Type.Decision, identityId, item);
            qo.add(Filter.eq("status", itemAction.getStatus()));
            qo.add(Filter.eq("entryDate", itemAction.getDecisionDate()));
            if (this.context.countObjects(IdentityHistoryItem.class, qo) == 0) {
                pendingDecision = new HashMap<>();
                pendingDecision.put(ACTOR, itemAction.getActorDisplayName());
                pendingDecision.put(STATUS, itemAction.getStatus().name());
                pendingDecision.put(ENTRY_DATE, itemAction.getDecisionDate());
                pendingDecision.put(COMMENTS, itemAction.getComments());
                // this converts the date
                pendingDecision = convertRow(pendingDecision);
            }
        }

        return pendingDecision;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // COMMENTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Count number of history items that exist for the given identity, type and role.
     */
    public int countComments(String identityId, String role) throws GeneralException {
        QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Comment, identityId, role);
        return context.countObjects(IdentityHistoryItem.class, ops);
    }

    /**
     * Count number of history items that exist for the given identity, type and violation.
     */
    public int countComments(String identityId, PolicyViolation violation) throws GeneralException {
        QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Comment, identityId, violation);
        return context.countObjects(IdentityHistoryItem.class, ops);
    }

    /**
     * Count number of history items that exist for the given identity, type and entitlements.
     */
    public int countComments(String identityId, EntitlementSnapshot entitlements,
                             Certification.EntitlementGranularity granularity) throws GeneralException {

        // if this is a group membership entitlement, we may need to check the source attribute as well
        CertifiableDescriptor descriptor = new CertifiableDescriptor(entitlements);

        // if the snapshot covers a single permission right or attribute value,
        // we can perform a simple query.
        if (descriptor.getAttributeSources().size() < 2 &&
                Certification.EntitlementGranularity.Value.equals(granularity)) {
            QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Comment, identityId,
                    Arrays.asList(entitlements), granularity);
            return context.countObjects(IdentityHistoryItem.class, ops);
        }

        // Since we have multiple permissions and or attribute values we
        // have to walk the objects performing comparisons.
        return getItems(IdentityHistoryItem.Type.Comment, identityId, entitlements, granularity).size();
    }

    /**
     * Get the comments for the identity's certification item.
     */
    public List<IdentityHistoryItem> getComments(String identityId, CertificationItem item)
        throws GeneralException {

        List<IdentityHistoryItem> items = null;
        if (item.getPolicyViolation() != null) {
            QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Comment, identityId, item.getPolicyViolation());
            items = context.getObjects(IdentityHistoryItem.class, ops);
        }
        else if (item.getBundle() != null){
            QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Comment, identityId, item.getBundle());
            items = context.getObjects(IdentityHistoryItem.class, ops);
        }
        else  if (item.getExceptionEntitlements() != null){
            items = this.getItems(IdentityHistoryItem.Type.Comment, identityId,
                                  item.getExceptionEntitlements(),
                                  item.getCertification().getEntitlementGranularity());
        }
        
        return items;
    }

    /**
     * Gets the history items that exist for the given identity, type and entitlements. If type
     * is null, all history items are returned.
     *
     * @param identityId ID of the identity to search for.
     * @param type Type of history item to retrieve, or null for all types.
     * @param entitlements EntitlementSnapshot to search for
     * @return List of IdentityHistoryItem matching the given parameters.
     * @throws GeneralException
     */
    public List<IdentityHistoryItem> getItems(IdentityHistoryItem.Type type, String identityId,
                                              EntitlementSnapshot entitlements,
                                              Certification.EntitlementGranularity granularity)
        throws GeneralException {

        CertifiableDescriptor descriptor = new CertifiableDescriptor(entitlements);

        // if the entitlement is a group membership entitlement, we may also need to query on
        // its attribute sources
        QueryOptions ops = buildQuery(new QueryOptions(), type, identityId, descriptor.getAttributeSources(), granularity);

        List<IdentityHistoryItem> items = context.getObjects(IdentityHistoryItem.class, ops);

        // if the snapshot covers a single permission right or attribute value,
        // we can perform a simple query.
        if (descriptor.getAttributeSources().size() < 2 && entitlements.isValueGranularity())
            return items;

        // because the entitlement covers multiples permissions or attributes
        // we have to iterate over all the matching history items looking for exact matches
        List<IdentityHistoryItem> matches = new ArrayList<IdentityHistoryItem>();
        for (IdentityHistoryItem item : items) {
            if (descriptor.matches(item.getCertifiableDescriptor()))
                matches.add(item);
        }

        return matches;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // RECENT DECISIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Get the most recent decision on the given role for the identity.
     */
    public IdentityHistoryItem getLastRoleDecision(String identityId, String roleName) throws GeneralException {
        QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Decision, identityId, roleName);
        return getMostRecentDecision(ops);
    }

    /**
     * Get the most recent decision on the given role for the identity.
     */
    public IdentityHistoryItem getLastViolationDecision(String identityId, PolicyViolation violation) throws GeneralException {
        QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Decision, identityId, violation);
        return getMostRecentDecision(ops, violation);
    }

    /**
     * Get the most recent decision on the given CertificationItem for the identity.
     */
    public IdentityHistoryItem getLastDecision(String identityId, CertificationItem item) throws GeneralException {
        QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Decision, identityId, item);
        return getMostRecentDecision(ops);
    }

    /**
     * Get the most recent decision on the given role for the identity.
     */
    public IdentityHistoryItem getLastEntitlementDecision(String identityId, EntitlementSnapshot snapshot,
                                                          Certification.EntitlementGranularity granularity) throws GeneralException {
        QueryOptions ops = buildQuery(new QueryOptions(), IdentityHistoryItem.Type.Decision, identityId,
                Arrays.asList(snapshot), granularity);
        return getMostRecentDecision(ops);
    }

    /**
     * Get the most recent decision on the given application account for the identity.
     */
    public List<IdentityHistoryItem> getMostRecentAccountDecisions(String identityId, String app,
                                                                   String instance, String nativeIdentity)
            throws GeneralException {
        QueryOptions ops = buildBaseQuery(new QueryOptions(), IdentityHistoryItem.Type.Decision, identityId);
        ops.add(Filter.eq("application", app));
        if (instance != null)
            ops.add(Filter.eq("instance", instance));
        else
            ops.add(Filter.isnull("instance"));
        ops.add(Filter.eq("nativeIdentity", nativeIdentity));
        ops.setOrderBy("created");
        ops.setOrderAscending(false);
        return context.getObjects(IdentityHistoryItem.class, ops);
    }

    private IdentityHistoryItem getMostRecentDecision(QueryOptions ops) throws GeneralException{
        ops.setOrderBy("created");
        ops.setOrderAscending(false);
        ops.setResultLimit(1);
        List<IdentityHistoryItem> items = context.getObjects(IdentityHistoryItem.class, ops);
        if (items != null && !items.isEmpty()){
            return items.get(0);
        } else {
            return null;
        }
    }
    
    // bug 21444 not only we need to check the constraint, also need to check violation
    private IdentityHistoryItem getMostRecentDecision(QueryOptions ops, PolicyViolation violation) throws GeneralException{
        ops.setOrderBy("created");
        ops.setOrderAscending(false);
        List<IdentityHistoryItem> items = context.getObjects(IdentityHistoryItem.class, ops);
        IdentityHistoryItem result = null;
        if (items != null && !items.isEmpty()){
            if(violation != null)
            {
                for(IdentityHistoryItem item : items)
                {
                    if (item.getCertifiableDescriptor()!=null && item.getCertifiableDescriptor().getPolicyViolation()!=null)
                    {
                        if (item.getCertifiableDescriptor().getPolicyViolation().getId().equals(violation.getId()))
                        {
                            result = item;
                            break;
                        }
                    }
                }
            }
        } 
        return result;
    }

    /**
     * Count the number of entitlement decisions for the given identity.
     */
    public int countEntitlementDecisions(String identityId) throws GeneralException {
        QueryOptions qo = buildBaseQuery(null, Type.Decision, identityId);
        qo.add(Filter.notnull("application"));
        return this.context.countObjects(IdentityHistoryItem.class, qo);
    }

    /**
     * Count the number of role decisions for the given identity.
     */
    public int countRoleDecisions(String identityId) throws GeneralException {
        QueryOptions qo = buildBaseQuery(null, Type.Decision, identityId);
        qo.add(Filter.notnull("role"));
        return this.context.countObjects(IdentityHistoryItem.class, qo);
    }

    /**
     * Count the number of policy violation decisions for the given identity.
     */
    public int countViolationDecisions(String identityId) throws GeneralException {
        QueryOptions qo = buildBaseQuery(null, Type.Decision, identityId);
        qo.add(Filter.notnull("policy"));
        return this.context.countObjects(IdentityHistoryItem.class, qo);
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // DEPRECATED
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @deprecated this should only be used in tests for now, and should be removed in 6.0.
     */
    public List<IdentityHistoryItem> getDecisions(String identityId) throws GeneralException {
       QueryOptions ops = buildBaseQuery(new QueryOptions(), IdentityHistoryItem.Type.Decision, identityId);
       ops.setOrderBy("created");
       ops.setOrderAscending(false);
       return context.getObjects(IdentityHistoryItem.class, ops);
    }
}
