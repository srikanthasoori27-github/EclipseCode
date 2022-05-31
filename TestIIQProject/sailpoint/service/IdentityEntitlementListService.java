package sailpoint.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sailpoint.api.AbstractEntitlizer;
import sailpoint.api.AccountGroupService;
import sailpoint.api.Explanator;
import sailpoint.api.Explanator.Explanation;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.Classification;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.identity.IdentityEntitlementDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * Service created to recreate the logic used in {@link sailpoint.rest.IdentityEntitlementResource}
 * to get Entitlements for an Identity.
 *
 * @author brian.li
 */
public class IdentityEntitlementListService extends BaseListService<BaseListServiceContext> {

    AccountGroupService _accountGroupService;
    private UserContext _userContext;

    public IdentityEntitlementListService(SailPointContext context, UserContext userContext,
                                      BaseListServiceContext listServiceContext,
                                      ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
        _accountGroupService = new AccountGroupService(getContext());
        _userContext = userContext;
    }

    /**
     * Returns the Entitlements for a given Identity. Results are given as a ListResult
     *
     * @param identity Passed in Identity to search on
     * @param ops      QueryOptions for the service to use
     * @return ListResult of Map<String, Object> of Entitlements for the Identity
     * @throws GeneralException
     */
    public ListResult getEntitlements(Identity identity, QueryOptions ops) throws GeneralException {
        QueryOptions noLimits = new QueryOptions(ops);
        noLimits.setResultLimit(0);
        int total = countResults(IdentityEntitlement.class, noLimits);
        List<Map<String, Object>> results = getResults(IdentityEntitlement.class, ops);
        List<IdentityEntitlementDTO> entitlementDTOs = getEntitlementDTOs(results);
        ListResult lr = new ListResult(entitlementDTOs, total);
        return lr;
    }

    /**
     * Returns a list of entitlements for the given identity based on passed in query params.  Here's the list of
     * supported queryParams:
     *
     * name - The name of the entitlement
     * value - The value of the entitlement
     * nameOrValue - A value to check against either the name or the value of the entitlement
     * applicationName - The name of the application associated with the entitlement
     * nativeIdentity - The native identity of the entitlement
     * accountDisplayName - The displayName associated with the entitlement
     * instance - The instance of the application
     * additionalOnly - Whether to check if this entitlement is granted by a role or not
     * source - The source value on the entitlement
     * aggregationState - Whether the application is connected or not
     * assigner - The user who granted the entitlement
     * type - The type of the entitlement(Entitlement or Permission)
     * hasCurrentCert - Whether there is a cert currently open on this entitlement
     * hasPendingCert - Whether there is a cert currently pending on this entitlement
     * hasCurrentRequest - Whether there is an identity request for this entitlement
     * hasPendingRequest - Whether there is a pending identity request on this entitlement
     *
     * @param identity The identity we are searching for entitlements on
     * @param ops The QueryOptions passed from the calling method
     * @param queryParams The QueryParameters from the url
     * @return ListResult A list of entitlement DTOs
     */
    public ListResult getEntitlementsWithParams(Identity identity, QueryOptions ops, Map<String, String> queryParams)
            throws GeneralException {

        // always in the scope of the identity
        ops.add(Filter.eq("identity", identity));
        ops.add(Filter.not(AbstractEntitlizer.ROLE_FILTER));

        if ( queryParams.get("nativeIdentity") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("nativeIdentity", queryParams.get("nativeIdentity"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("instance") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("instance", queryParams.get("instance"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("source") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("source", queryParams.get("source"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("assigner") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("assigner", queryParams.get("assigner"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("accountDisplayName") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("displayName", queryParams.get("accountDisplayName"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("type") != null ) {
            ops.add(Filter.eq("type", ManagedAttribute.Type.valueOf(queryParams.get("type"))));
        }

        if ( queryParams.get("aggregationState") != null ) {
            ops.add(Filter.eq("aggregationState", IdentityEntitlement.AggregationState.valueOf(queryParams.get("aggregationState"))));
        }

        //Not sure if this is used, or if it was translated incorrectly in the service migration. Keep around in case. -rap
        if ( queryParams.get("applicationName") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("application.name", queryParams.get("applicationName"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("application") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("application.name", queryParams.get("application"), Filter.MatchMode.START)));
        }

        //
        // This includes all entitlements not assigned indirectly by a role
        //
        if ( Util.otob(queryParams.get("additionalOnly")) ) {
            ops.add(Filter.eq("grantedByRole", false));
        }

        // Create a large or filter over the name of the application, name of the attribute, or value
        // Also includes entitlement display value
        if(Util.isNotNullOrEmpty(queryParams.get("applicationOrNameOrValue"))) {
            String nameOrValue = queryParams.get("applicationOrNameOrValue");

            Filter managedAttributeValueJoin = Filter.join("value", "ManagedAttribute.value");
            Filter managedAttributeApplicationJoin = Filter.join("application", "ManagedAttribute.application");
            Filter managedAttributeAttributeJoin = Filter.join("name", "ManagedAttribute.attribute");
            Filter managedAttributeDisplayableNameFilter = Filter.ignoreCase(Filter.like("ManagedAttribute.displayableName", nameOrValue, Filter.MatchMode.START));
            Filter displayableNameFilter = Filter.and(managedAttributeValueJoin, managedAttributeApplicationJoin,
                    managedAttributeAttributeJoin, managedAttributeDisplayableNameFilter);

            Filter nameOrValueFilter = Filter.or(
                    Filter.ignoreCase(Filter.like("application.name", nameOrValue, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("name", nameOrValue, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("value", nameOrValue, Filter.MatchMode.START)),
                    displayableNameFilter
            );

            ops.setDistinct(true);
            ops.add(nameOrValueFilter);
        }

        if ( Util.isNotNullOrEmpty(queryParams.get("nameOrValue"))) {
            String nameOrValue = queryParams.get("nameOrValue");
            Filter nameOrValueFilter = Filter.or(Filter.ignoreCase(Filter.like("name", nameOrValue, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("value", nameOrValue, Filter.MatchMode.START)));
            ops.add(nameOrValueFilter);
        }
        else {
            String name = queryParams.get("name");
            String value = queryParams.get("value");
            if ( name != null ) {
                ops.add(Filter.ignoreCase(Filter.like("name", name, Filter.MatchMode.START)));
            }
            if ( value != null ) {
                ops.add(Filter.ignoreCase(Filter.like("value", value, Filter.MatchMode.START)));
            }
        }

        //IIQSAW-2125: Adding secondary order to make the order deterministic.
        ops.addOrdering("id", true);
        
        addIsNull(ops, "certificationItem", queryParams.get("hasCurrentCert"));
        addIsNull(ops, "pendingCertificationItem", queryParams.get("hasPendingCert"));
        addIsNull(ops, "requestItem", queryParams.get("hasCurrentRequest"));
        addIsNull(ops, "pendingRequestItem", queryParams.get("hasPendingRequest"));

        // assigned is separate from grantedByRole
        addIsNull(ops, "assigned", queryParams.get("assigned"));
        if ( Util.isNotNullOrEmpty(queryParams.get("assigned")) ) {
            ops.add(Filter.eq("assigned", Util.otob(queryParams.get("assigned"))));
        }

        return this.getEntitlements(identity, ops);
    }

    /**
     * Helper method, if the value is non-null add in the correct null non null filter.
     *
     * @param ops QueryOptions The QueryOptions we are adding the filter to
     * @param name The name of the filter
     * @param value The value we are checking for null
     */
    public static void addIsNull( QueryOptions ops, String name, Object value) {
        if ( value != null) {
            if ( Util.otob(value) ) {
                ops.add(Filter.notnull(name));
            } else {
                ops.add(Filter.isnull(name));
            }
        }
    }

    /**
     * Build a list of entitlement DTOs from the map returned from the db layer
     * @param entitlements The Map of key/values for the entitlements returned from the layer
     * @return List<IdentityEntitlementDTO> a list of entitlement dtos
     * @throws GeneralException
     */
    private List<IdentityEntitlementDTO> getEntitlementDTOs(List<Map<String, Object>> entitlements) throws GeneralException {
        List<IdentityEntitlementDTO> entitlementDTOs = new ArrayList<IdentityEntitlementDTO>();

        IdentityEntitlementService entitlementService = new IdentityEntitlementService(this.context);

        for (Map<String, Object> map : entitlements) {
            IdentityEntitlementDTO entitlementDTO = new IdentityEntitlementDTO(map, getColumns());
            // Store whether this entitlement is a group.
            AccountGroupService svc = new AccountGroupService(getContext());
            boolean isGroup = svc.isGroupAttribute(entitlementDTO.getApplication(), entitlementDTO.getAttribute());
            entitlementDTO.setGroup(isGroup);

            ManagedAttribute managedAttribute = entitlementService.getManagedAttribute(entitlementDTO);
            if (managedAttribute != null && !Util.isEmpty(managedAttribute.getClassifications())) {
                List<String> classificationNames = managedAttribute.getClassifications().stream()
                        .filter(e -> Util.nullSafeEq(e.getOwnerType(), ManagedAttribute.class.getSimpleName()))
                        .map(classification -> classification.getClassification().getDisplayableName())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                entitlementDTO.setClassificationNames(classificationNames);
            }

            entitlementDTOs.add(entitlementDTO);
        }
        return entitlementDTOs;
    }

    /**
     * Overridden method to populate surrounding metadata needed to populate the Entitlement information
     * Teken from the Resource layer
     */
    @Override
    protected void calculateColumns(Map<String, Object> rawQueryResults,
                                    Map<String, Object> map)
            throws GeneralException {

        // should I just require these in column config
        super.calculateColumns(rawQueryResults, map);

        // fill in role info        
        String name = Util.getString(rawQueryResults, "name");
        String applicationName = Util.getString(rawQueryResults, "application.name");

        // TODO: should I just check the MA?
        boolean isGroupAttribute = _accountGroupService.isGroupAttribute(applicationName, name);
        if (isGroupAttribute) {
            map.put("isGroup", true);
        }

        //
        // Display value.. TODO: should this be a column?
        // this may have to be a field if we want searchable
        // calculate display value based on explanator
        //
        Application app = getContext().getObjectByName(Application.class, applicationName);
        String value = Util.getString(rawQueryResults, "value");
        String displayValue = value;

        if (value != null && app != null) {
            if (app != null) {
                String explanatorDisplayValue = Explanator.getDisplayValue(app, name, value);
                if (Util.getString(explanatorDisplayValue) != null) {
                    displayValue = explanatorDisplayValue;
                }
                String entitlementDescription = Explanator.getDescription(app, name, value, _userContext.getLocale());
                if (Util.getString(entitlementDescription) != null)
                    map.put("entitlementDescription", entitlementDescription);
            }
        }
        map.put("displayValue", displayValue);
        //
        // Assimilate a flag for pending requests        
        //
        String requestId = Util.getString(map, "pendingRequestId");
        if (requestId != null) {
            map.put("hasPendingRequest", "true");
        } else {
            map.put("hasPendingRequest", "false");
        }

        Explanation exp = Explanator.get(app, name, value);
        if (exp != null ) {
            List<String> cNames = exp.getClassificationNames();

            if (!Util.isEmpty(cNames)) {
                List<String> dNames = getClassificationDisplayNames(cNames);

                if (!Util.isEmpty(dNames)) {
                    map.put("classificationNames", dNames);
                }

            }
        }
    }

    /**
     * Helper to get the classification display names, given the classification names
     * @param classificationNames The names of the Classifications
     * @return A List of Classification display names of the names passed in
     * @throws GeneralException
     */
    private List<String> getClassificationDisplayNames(List<String> classificationNames)
            throws GeneralException {
        List<String> displayNames = new ArrayList<>();
        QueryOptions qops = new QueryOptions(Filter.in("name", classificationNames));
        Iterator<Object[]> it = context.search(Classification.class, qops, "displayableName");

        if (it != null) {
            it.forEachRemaining(row -> {
                displayNames.add((String) row[0]);
            });
        }

        return displayNames;
    }

    /**
     *
     * @return List<ColumnConfig> list of column configs
     * @throws GeneralException
     */
    private List<ColumnConfig> getColumns() throws GeneralException{
        return this.columnSelector.getColumns();
    }

    /**
     * Helper method to help create a QueryOptions to search for Entitlements
     * @param identity Identity passed in
     * @return QueryOptions to be used to search for Entitlements
     */
    public QueryOptions createOptionsForEntitlements(Identity identity) {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));
        ops.add(Filter.not(AbstractEntitlizer.ROLE_FILTER));
        return ops;
    }

    /**
     * Override date convertion to do nothing
     * @param value entry value
     * @param config column config
     * @return Object value
     */
    @Override
    protected Object convertDate(Object value, ColumnConfig config) {
        //Override date convertion to do nothing
        return value;
    }
}
