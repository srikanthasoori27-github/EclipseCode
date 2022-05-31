package sailpoint.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.AbstractEntitlizer;
import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.*;
import sailpoint.service.classification.ClassificationService;
import sailpoint.service.identity.EntitlementRoleDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.identity.RoleAssignmentUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * Service created to recreate the logic used in {@link sailpoint.rest.IdentityEntitlementRoleResource}
 * to get Roles for an Identity. 
 * @author brian.li
 *
 */
public class IdentityEntitlementRoleService extends BaseListService<BaseListServiceContext>{

    Localizer _localizer;
    Identity _identity;

    /**
     * Use this class to resolve the permits/allowed relationships
     * for each row we render into a grid.
     */
    private RoleRelationships _roleRelationships;
    private Map<String, String> roleTypeIcons;
    private UserContext _userContext;
     
    public IdentityEntitlementRoleService(SailPointContext context, UserContext userContext,
            BaseListServiceContext listServiceContext,
            ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
        _localizer = new Localizer(context);
        _userContext = userContext;
    }
    
    /**
     * Returns a ListResult of Roles for a given Identity
     * @param identity Passed in Identity to search on
     * @param ops QueryOptions to use for the search
     * @return ListResult of EntitlementRoleDTO of Roles for this Identity
     * @throws GeneralException
     */
    public ListResult getRoleEntitlements(Identity identity, QueryOptions ops) throws GeneralException {
        if (_roleRelationships == null) {
            _roleRelationships = new RoleRelationships();
            _roleRelationships.analyze(identity);
        }
        _identity = identity;
        QueryOptions noLimits = new QueryOptions(ops);
        noLimits.setResultLimit(0);
        int total = countResults(IdentityEntitlement.class, noLimits);
        List<Map<String, Object>> results = getResults(IdentityEntitlement.class, ops);
        if (results != null) {
            convertAssignerName(results);
            addRoleAssignmentInfo(results, identity);
        }
        List<EntitlementRoleDTO> entitlementDTOs = createEntitlementRoleDTOs(results);
        ListResult lr = new ListResult(entitlementDTOs, total);
        return lr;
    }
    
    /**
     * Return List result of role entitlements for given identity
     * @param identity
     * @param ops
     * @param queryParams
     * @return ListResult of Map<String, Object> for role entitlements
     * @throws GeneralException
     */
    public ListResult getRoleEntitlementsWithParams(Identity identity, QueryOptions ops, Map<String, String> queryParams) throws GeneralException {       
        return getRoleEntitlements(identity, getQueryOptions(identity, ops, queryParams));
    }

    private QueryOptions getQueryOptions(Identity identity, QueryOptions ops, Map<String, String> queryParams) {
        ops.add(Filter.join("value", "Bundle.name"));
        
        // always in the scope of the identity        
        ops.add(Filter.eq("identity.id",identity.getId()));   
        // this will always be null for roles
        ops.add(Filter.isnull("application"));
        
        Date startBeforeDate = Util.getDate(queryParams.get("startDateBefore"));
        if ( startBeforeDate != null ) {
            ops.add(Filter.le("startDate", startBeforeDate));
        }
        
        Date startAfterDate = Util.getDate(queryParams.get("startDateAfter"));
        if ( startAfterDate != null ) {
            ops.add(Filter.ge("startDate", startAfterDate));
        }
        
        Date endBeforeDate  = Util.getDate(queryParams.get("endDateBefore"));
        if ( endBeforeDate != null ) {
            ops.add(Filter.le("endDate", endBeforeDate));
        }
        
        Date endAfterDate = Util.getDate(queryParams.get("endDateAfter"));
        if ( endAfterDate != null ) {
            ops.add(Filter.ge("endDate", endAfterDate));
        }
        
        if ( queryParams.get("source") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("source", queryParams.get("source"), Filter.MatchMode.START)));
        }
        
        if ( queryParams.get("assigner") != null ) {
            ops.add(Filter.ignoreCase(Filter.like("assigner", queryParams.get("assigner"), Filter.MatchMode.START)));
        }

        if ( queryParams.get("value") != null ) {
            // Bug #23756 - Search for name in IdentityEntitlement or displayName in Bundle - Allows
            // one to search by the role's display name
            ops.add(Filter.or(
                    Filter.ignoreCase(Filter.like("value", queryParams.get("value"), Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("Bundle.displayName", queryParams.get("value"), Filter.MatchMode.START))
            ));
        }
        
        addIsNull(ops, "certificationItem", queryParams.get("hasCurrentCert"));    
        addIsNull(ops, "pendingCertificationItem", queryParams.get("hasPendingCert"));  
        addIsNull(ops, "requestItem", queryParams.get("hasCurrentRequest"));    
        addIsNull(ops, "pendingRequestItem", queryParams.get("hasPendingRequest")); 

        if ( queryParams.get("allowed") != null ) {
            if ( !Util.otob(queryParams.get("allowed")) ) {
                ops.add(Filter.eq("allowed", false));                
            } else {
                ops.add(Filter.eq("allowed", true));                
            }
            ops.add(Filter.ignoreCase(Filter.eq("name",ProvisioningPlan.ATT_IIQ_DETECTED_ROLES)));
        } else {
            if ( queryParams.get("name") != null ) {
                ops.add(Filter.ignoreCase(Filter.like("name", queryParams.get("name"), Filter.MatchMode.START)));
            } else { 
                ops.add(AbstractEntitlizer.ROLE_FILTER);
            }
        }
        return ops;
    }
    /**
     * Helper methid, if the value is non-null add in the 
     * correct null non null filter.
     * 
     * @param ops
     * @param name
     * @param value
     */    
    private void addIsNull( QueryOptions ops, String name, Object value) {    
        if ( value != null) {               
            if ( Util.otob(value) ) {
                ops.add(Filter.notnull(name));
            } else {
                ops.add(Filter.isnull(name));
            }
        }
    } 

    /**
     * We store Identity Name as the assigner on the IdentityEntitlement Table. 
     * When fetching an IE for a grid, we want to convert this to DisplayName.
     * Would be nice to have Transformation on the ColumnConfig.
     * @param lr ListResult of IdentityEtitlements
     * @throws GeneralException
     */
    protected void convertAssignerName(List<Map<String, Object>> ls) throws GeneralException {
        if(ls != null) {
            for(Map<String,Object> m : ls) {
                String s = (String)m.get("assigner");
                if(s != null) {
                    Identity i = getContext().getObjectByName(Identity.class, s);
                    if(i!=null) {
                        m.put("assigner", i.getDisplayableName());
                    }
                }
            }
        }
    }
    
    /**
     * Adds the role assignment target information to the result list.
     * @param listResult The result list.
     * @throws GeneralException
     */
    private void addRoleAssignmentInfo(List<Map<String,Object>> listResult, Identity ident) throws GeneralException {
        if (ident != null && listResult != null) {
            for (Map<String,Object> row : listResult) {
                String assignmentId = (String) row.get("assignmentId");
                String name = (String) row.get("name");

                List<RoleTarget> targets = null;

                if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(name)) {
                    String value = (String) row.get("value");

                    // if no assignment id look for an unassigned detection
                    if (Util.isNullOrEmpty(assignmentId)) {
                        Bundle role = getContext().getObjectByName(Bundle.class, value);

                        RoleDetection detection = ident.getUnassignedRoleDetection(role);
                        if (detection != null) {
                            targets = detection.getTargets();
                        }
                    } else {
                        // assignment id exists so find the detection for the role that contains it
                        for (RoleDetection roleDetection : Util.iterate(ident.getRoleDetections())) {
                            if (roleDetection.hasAssignmentId(assignmentId) &&
                                roleDetection.getRoleName().equals(value)) {
                                targets = roleDetection.getTargets();

                                break;
                            }
                        }
                    }
                } else {
                    RoleAssignment assignment = ident.getRoleAssignmentById(assignmentId);
                    if (assignment == null) {
                        // couldn't find by assignment id.. fallback to old way
                        assignment = ident.getRoleAssignment((String) row.get("roleId"));
                    }

                    if (assignment != null) {
                        targets = assignment.getTargets();
                    }
                }

                RoleAssignmentUtil.addRoleAssignmentInfoToListResultRow(row, targets, "application", "accountName");
            }
        }
    }

    @Override
    protected void calculateColumns(Map<String,Object> rawQueryResults,
            Map<String,Object> map) throws GeneralException {
        super.calculateColumns(rawQueryResults, map);

        String value = Util.getString(rawQueryResults, "value");
        fillInRoleData(map, value);
    }

    private void fillInRoleData(Map<String,Object> map, String roleName)
            throws GeneralException {

        if ( roleName != null ) {

            Bundle role = getContext().getObjectByName(Bundle.class, roleName);

            if (role != null) {
                // set the value to the role type
                String type = role.getType();
                map.put("roleType", type);

                String typeIcon = getIconForType(type);
                if (!Util.isNullOrEmpty(typeIcon)) {
                    map.put("roleTypeIcon", typeIcon);
                }

                map.put("roleId", role.getId());

                String name = (String) map.get("name");
                final boolean detected = ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(name);
                final boolean assigned = ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name);

                if (detected) {
                    Message msgdetected = new Message(MessageKeys.DETECTED);
                    map.put("acquired", msgdetected.getLocalizedMessage(_userContext.getLocale(), _userContext.getUserTimeZone()));
                } else if (assigned) {
                    Message msgassigned = new Message(MessageKeys.ASSIGNED);
                    map.put("acquired", msgassigned.getLocalizedMessage(_userContext.getLocale(), _userContext.getUserTimeZone()));
                }

                String permitsNames = _roleRelationships.getPermittedNames(role);
                if ( permitsNames != null && assigned ) {
                    map.put("allows", permitsNames);
                }

                if (detected) {
                    String assignmentId = (String) map.get("assignmentId");
                    map.put("allowedBy", getAllowedBy(role, assignmentId, _identity));
                }

                String localized = _localizer.getLocalizedValue(role, Localizer.ATTR_DESCRIPTION, _userContext.getLocale());
                if ( localized != null ) {
                    //XSS prevention
                    map.put("roleDescription", WebUtil.sanitizeHTML(localized));
                }

                List<String> dNames = role.getClassificationDisplayNames();
                if (!Util.isEmpty(dNames)) {
                    map.put("classificationNames", dNames);
                }
            }
        }

        //
        // Assimilate a flag for pending requests
        //
        String requestId = Util.getString(map, "pendingRequestId");

        map.put("hasPendingRequest", requestId != null ?
                                     Boolean.TRUE.toString() :
                                     Boolean.FALSE.toString());

    }

    /**
     * Gets the allowedBy from all RoleAssignments of this RoleDetection.
     * 
     * @param role
     * @param assignmentId
     * @return
     * @throws GeneralException
     */
    private String getAllowedBy(Bundle role, String assignmentId, Identity identity) throws GeneralException {
        if (Util.isEmpty(assignmentId)) {
            return null;
        }
        List<String> roleNames = new ArrayList<String>();
        RoleDetection rd = identity.getRoleDetection(assignmentId, role.getId());
        if (rd != null) {
            for (String aid : Util.iterate(rd.getAssignmentIdList())) {
                RoleAssignment ra = identity.getRoleAssignmentById(aid);
                if (ra != null && !ra.isNegative()) {
                    // bug 22316 - Need to return the role display name if there is one
                    Bundle assignedRole = ra.getRoleObject(getContext());
                    if (assignedRole != null) {
                        roleNames.add(assignedRole.getDisplayableName());
                    }
                }
            }
        }
        return Util.listToCsv(roleNames);
    }

    /*
     * @return class of the icon corresponding to this role type
     */
    private String getIconForType(String roleType) {
        String icon = null;

        if (!Util.isNullOrEmpty(roleType)) {
            if (roleTypeIcons == null) {
                // Pull the type out of the ObjectConfig
                ObjectConfig config = ObjectConfig.getObjectConfig(Bundle.class);

                if (config != null) {
                    Object typeDefObj = config.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);
                    // Support the RoleTypeDefinitions as either a map or list
                    Collection<RoleTypeDefinition> typeDefs;
                    if (typeDefObj instanceof Map) {
                        typeDefs = ((Map<String,RoleTypeDefinition>)typeDefObj).values();
                    } else if (typeDefObj instanceof List) {
                        typeDefs = (Collection<RoleTypeDefinition>) typeDefObj;
                    } else {
                        typeDefs = null;
                    }

                    // Generate a map of icons keyed by type name
                    if (!Util.isEmpty(typeDefs)) {
                        roleTypeIcons = new HashMap<String, String>();
                        for (RoleTypeDefinition typeDef : typeDefs) {
                            roleTypeIcons.put(typeDef.getName(), typeDef.getIcon());
                        }
                    }
                }
            }

            if (!Util.isEmpty(roleTypeIcons)) {
                icon = roleTypeIcons.get(roleType);
            }
        }

        return icon;
    }

    /**
     * Helper method to create QueryOptions to search for Roles
     * @param identity Passed in Identity to be used to search on
     * @return QueryOptions for the search to use
     * @throws GeneralException
     */
    public QueryOptions createOptionsForRoles(Identity identity) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        // always in the scope of the identity        
        ops.add(Filter.eq("identity.id", identity.getId()));   
        // this will always be null for roles
        ops.add(Filter.isnull("application"));
        ops.add(Filter.join("value", "Bundle.name"));
        ops.add(AbstractEntitlizer.ROLE_FILTER);
        return ops;
    }

    /**
     * Build a list of entitlement DTOs from the map returned from the db layer
     * @param entitlements The Map of key/values for the entitlements returned from the layer
     * @return List<EntitlementRoleDTO> a list of entitlement dtos
     * @throws GeneralException
     */
    private List<EntitlementRoleDTO> createEntitlementRoleDTOs(List<Map<String, Object>> entitlements)
            throws GeneralException {
        List<EntitlementRoleDTO> entitlementRoleDTOs = new ArrayList<EntitlementRoleDTO>();

        ClassificationService classificationService = new ClassificationService(this.context);

        for (Map<String, Object> map : entitlements) {
            EntitlementRoleDTO entitlementRoleDTO = new EntitlementRoleDTO(map, getColumns());
            List<String> classificationNames = classificationService.getClassificationNames(Bundle.class, entitlementRoleDTO.getRoleId());
            entitlementRoleDTO.setClassificationNames(classificationNames);
            entitlementRoleDTOs.add(entitlementRoleDTO);
        }
        return entitlementRoleDTOs;
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
