/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidonboarding.groupmanagement.transformer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.AccountGroupService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;
import sailpoint.transformer.AbstractTransformer;
/**
 * Transformer class which transforms one object to another.Examples are:
 * 1. toMap method to Convert a ManagedAttribute(LDAP Group) to a HashMap which is referenced as the GroupModel in the library
 * 2. mapToPlans method to generate the list of object and Identity plans from the Group Model
 */
public class ManagedAttributeTransformer extends AbstractTransformer<ManagedAttribute> {
    private static Log groupManagementLogger = LogFactory.getLog("rapidapponboarding.rules");
    SailPointContext context;
    public static final String ATTR_HIERARCHY_IDS = "inheritedGroupIds";
    public static final String ATTR_IDENTITY_MEMBERSHIP_IDS = "identityMembership";
    public static final String ATTR_OPTION_INCLUDE_PREVIOUS = "includePrevious";
    public static final String ATTR_OPTION_INCLUDE_MEMBERS = "includeMembership";
    public static final String ATTR_OPTION_INCLUDE_INHERITANCE = "includeInheritance";
    public static final String ATTR_OPTION_EXPAND_WORKGROUP_OWNERS = "expandOwners";
    public static final String ATTR_OPTION_MAX_MEMBERSHIP_TO_MANAGE = "maxMembershipToManage";
    private final List<String> reservedKeys = Arrays.asList(
			new String[]{ATTR_TRANSFORMER_OPTIONS,ATTR_TRANSFORMER_CLASS,ATTR_HIERARCHY_IDS,ATTR_IDENTITY_MEMBERSHIP_IDS,
					"sys","displayOnly","extended","previous","deltas"});
    private boolean includeMembers = false;
    private boolean includeInheritance = false;
    private boolean expandOwners = false;
    private int maxMembershipToManage = 1000;
    public ManagedAttributeTransformer(SailPointContext ctx) {
        this(ctx, null);
    }
    public ManagedAttributeTransformer(SailPointContext ctx, Map<String,Object> optMap) {
        context = ctx;
        setOptions(optMap);
        if(optMap != null) {
        	includeMembers = Util.otob(optMap.get(ATTR_OPTION_INCLUDE_MEMBERS));
        	includeInheritance = Util.otob(optMap.get(ATTR_OPTION_INCLUDE_INHERITANCE));
        	expandOwners = Util.otob(optMap.get(ATTR_OPTION_EXPAND_WORKGROUP_OWNERS));
        	maxMembershipToManage = Util.otoi(optMap.get(ATTR_OPTION_MAX_MEMBERSHIP_TO_MANAGE));
        }
    }
    /**
     * Method to transform the ManagedAttribute which is an LDAP group to a HashMap
     * This HashMap is known as the GroupModel in the Workflows
     *
     */
    @Override
	public Map<String, Object> toMap(ManagedAttribute group) throws GeneralException {
    	Map<String, Object> ops = getOptions();
    	boolean includePrevious = false;
    	if(ops != null) {
    		includePrevious = Util.otob(ops.get(ATTR_OPTION_INCLUDE_PREVIOUS));
    	}
    	return toMap(group, includePrevious);
    }
    @SuppressWarnings({ "unchecked", "rawtypes"})
	public Map<String, Object> toMap(ManagedAttribute group, boolean addPrevious) throws GeneralException {
        Map<String, Object> retMap = new HashMap<String, Object>();
        if (group == null) {
            return retMap;
        }
        Map<String, Object> ops = getOptions();
        Map<String,Object> sys = new HashMap<String,Object>();
        // Build some base info that should be included in most Models.Including the hibernate ID, object's name, objectClass,transformer class and transformer options.
        appendBaseSailPointObjectInfo(group, sys);
        retMap.put("sys", sys);
        retMap.put(ATTR_TRANSFORMER_CLASS, sys.get(ATTR_TRANSFORMER_CLASS));
        retMap.put(ATTR_TRANSFORMER_OPTIONS, sys.get(ATTR_TRANSFORMER_OPTIONS));
        Attributes<String,Object> attrs = group.getAttributes();
        if ( attrs != null ) {
            retMap.putAll(attrs);
        }
    	if(includeMembers) {
	        // Attributes which we do not want to send to provisioning are put with the 'sys' key
	        List<String> currentMembers = getIdentityMembership(group); // Add the members list to the model
	        //groupManagementLogger.debug("currentMembers.size(): " + currentMembers.size());
	        groupManagementLogger.debug("maxMembershipToManage: " + maxMembershipToManage);
	        if(currentMembers != null && currentMembers.size() > maxMembershipToManage) {
	        	//Cannot put all these users in the map otherwise we may kill the memory and form feature
	        	MapUtil.put(retMap, "sys.maxMembershipExceeded", true);
	        }
	        else {
	        	MapUtil.put(retMap, ATTR_IDENTITY_MEMBERSHIP_IDS, currentMembers);
	        }
    	}
		MapUtil.put(retMap, "sys.nativeIdentity", group.getValue()); // Account's unique Identifier.
        MapUtil.put(retMap, "sys.attribute", group.getAttribute());
        MapUtil.put(retMap, "sys.description", group.getDescription(Locale.getDefault()));
        MapUtil.put(retMap, "sys.displayName", group.getDisplayableName());
        MapUtil.put(retMap, "sys.type", group.getType());
        MapUtil.put(retMap, "sys.requestable", group.isRequestable());
        Application app = group.getApplication();
        if ( app != null ) {
            MapUtil.put(retMap, "sys.application.name", app.getName());
            MapUtil.put(retMap, "sys.application.id", app.getId());
            /*
             * Set the type if no type, but attribute included
             * Set the attribute if no type, but type included
             */
            String attribute = group.getAttribute();
            String type = group.getType();
            if(attribute == null && type != null) {
            	Schema accountSchema = app.getAccountSchema();
            	attribute = accountSchema.getGroupAttribute(type);
            	group.setAttribute(attribute);
            	MapUtil.put(retMap, "sys.attribute", attribute);
            	groupManagementLogger.debug("Got attribute from schema for type " + type + ": " + attribute);
            }
            else if(attribute != null && type == null) {
            	Schema accountSchema = app.getAccountSchema();
            	type = accountSchema.getAttributeType(attribute);
            	group.setType(type);
            	MapUtil.put(retMap, "sys.type", type);
            	groupManagementLogger.debug("Got attribute from schema for type " + attribute + ": " + type);
            }
        }
        // Set the owner and primary owner ... primary owner can be the owner of the workgroup
        Identity groupOwner = group.getOwner();
        groupManagementLogger.debug("Group Owner in toMap:"+groupOwner);
    	if ( groupOwner != null) {
        	//Since there can be multiple owners, we have a Workgroup. Retrieve all owners from the Workgroup
        	if(groupOwner.isWorkgroup() && expandOwners){
        		List<String> owners = getGroupOwners(groupOwner);
        		MapUtil.put(retMap, "sys.owner", owners);
        		MapUtil.put(retMap, "sys.workgroupOwner", groupOwner.getName());
        	}else if(expandOwners){
        		MapUtil.put(retMap, "sys.owner", Util.asList(groupOwner.getName()));
        	}
        	else {
        		MapUtil.put(retMap, "sys.owner", groupOwner.getName());
        	}
        }
        // Add in extended attributes under sys
        ObjectConfig maOC = ManagedAttribute.getObjectConfig();
        List<ObjectAttribute> extendedAttributes = maOC.getObjectAttributes();
        if(extendedAttributes != null) {
        	for(ObjectAttribute oa: extendedAttributes) {
        		groupManagementLogger.debug("Adding extendedAttribute: " + oa.getName());
        		String oaName = oa.getName();
        		Object oaValue = group.getAttribute(oaName);
        		MapUtil.put(retMap, "extended." + oaName, oaValue);
        	}
        }
        if(includeInheritance) {
	        // Get parent groups (i.e. inheritance)
	        List<ManagedAttribute> parents = group.getInheritance();
	        if (null != parents && !parents.isEmpty()) {
	        	List<String> inheritedGroupIds = new ArrayList<String>();
	        	for (ManagedAttribute parent : parents) {
	        		inheritedGroupIds.add(parent.getId());
	        	}
	        	MapUtil.put(retMap, ATTR_HIERARCHY_IDS, inheritedGroupIds);
	        }
        }
        /*
         *
         * Need to create a separate map for the previous map so just call it again with addPrevious set to false
         */
        if(addPrevious) {
        	Map<String, Object> previousMap = toMap(group, false);
        	MapUtil.put(retMap, "previous", previousMap);
        }
        groupManagementLogger.debug(retMap.toString());
        return retMap;
    }
    /**
     * If owner is a Workgroup, get the list of owners
     * @param ownerWorkgroup
     * @return
     * @throws GeneralException
     */
    private List<String> getGroupOwners(Identity ownerWorkgroup) throws GeneralException{
    	List<String> identityProps = new ArrayList<String>();
    	List<String> owners = new ArrayList<String>();
    	identityProps.add("name");
    	try {
    		Iterator<Object[]> workgroupMembers = ObjectUtil.getWorkgroupMembers(context,ownerWorkgroup,identityProps);
			groupManagementLogger.debug("In getGroupOwners:"+workgroupMembers);
	        if ( workgroupMembers != null ) {
	            while ( workgroupMembers.hasNext() ) {
	                Object[] workgroupMember = workgroupMembers.next();
	                if ( workgroupMember != null ) {
	                    if ( workgroupMember.length > 0 ) {
	                        String name = (String)workgroupMember[0];
	                        if ( name != null && !owners.contains(name)){
	                        	owners.add(name);
	                        	groupManagementLogger.debug(name);
	                        }
	                    }
	                }
	            }
	        }
		} catch (GeneralException e) {
			owners = null;
			throw new GeneralException("Group Owners not found");
		}
    	return Util.size(owners) > 0 ? owners : null;
    }
    /**
     * Use the AccountGroupService API to get the list of member Identities of a group
     * @param managedAttribute
     * @return
     * @throws GeneralException
     */
    private List<String> getIdentityMembership(ManagedAttribute managedAttribute)
            throws GeneralException {
        if ( managedAttribute == null || managedAttribute.getId() == null )
            return null;
        AccountGroupService svc = new AccountGroupService(context);
        QueryOptions qo = svc.getMembersQueryOptions(managedAttribute);
        return getMembers(qo);
    }
    /**
     * Heper to above method
     * @param qo
     * @return
     * @throws GeneralException
     */
    private List<String> getMembers(QueryOptions qo) throws GeneralException {
        List<String> list = new ArrayList<String>();
        Iterator<Object[]> rows = context.search(IdentityEntitlement.class, qo, "identity.name");
        if ( rows != null ) {
            while ( rows.hasNext() ) {
                Object[] row = rows.next();
                if ( row != null ) {
                    if ( row.length > 0 ) {
                        String name = (String)row[0];
                        if ( name != null )
                            list.add(name);
                    }
                }
            }
        }
        return Util.size(list) > 0 ? list : null;
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    // Map Model to ProvisioningPlan
    //
    ///////////////////////////////////////////////////////////////////////////
    ManagedAttribute cachedCurrent = null;
    /**
     * Get the existing group which is persisted in IdentityIQ. The details retrieved are presented for editing
     * @param groupModel
     * @return
     * @throws GeneralException
     */
    private ManagedAttribute getCurrent(Map<String,Object> groupModel)
        throws GeneralException {
        if ( cachedCurrent != null ) {
            return cachedCurrent;
        }
        String id = (String)MapUtil.get(groupModel, "sys.id");
        if ( id != null ) {
             cachedCurrent = context.getObject(ManagedAttribute.class, id);
        }
        return cachedCurrent;
    }
    /**
     * Method called from Workflow to generate the set of Provisioning Plans from the managedAttribute Model
     * @param groupModel
     * @return
     * @throws GeneralException
     */
    public List<ProvisioningPlan> mapToPlans(Map<String,Object> groupModel)
        throws GeneralException {
        List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
        // Group Plan is an ObjectRequest
        ProvisioningPlan groupPlan = getGroupPlan(groupModel);
        if ( groupPlan != null ) {
            plans.add(groupPlan);
        }
        if(includeMembers) {
	        // Identity Plans are Identity Requests
	        List<ProvisioningPlan> identityPlans = getIdentityPlans(groupModel);
	        if ( identityPlans != null ) {
	            plans.addAll(identityPlans);
	        }
        }
        return Util.size(plans) > 0 ? plans : null;
    }
    /**
     * Method called from Workflow to generate the set of Identity Provisioning Plans from the managedAttribute Model
     * @param groupModel
     * @return
     * @throws GeneralException
     */
    public List<ProvisioningPlan> mapToIdentityPlans(Map<String,Object> groupModel)
        throws GeneralException {
        List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
        // Identity Plans are Identity Requests
        List<ProvisioningPlan> identityPlans = getIdentityPlans(groupModel);
        if ( identityPlans != null ) {
            plans.addAll(identityPlans);
        }
        return Util.size(plans) > 0 ? plans : null;
    }
    /**
     * Method called from Workflow to generate just the group plan from the managedAttribute Model
     * @param groupModel
     * @return
     * @throws GeneralException
     */
    public ProvisioningPlan mapToPlan(Map<String,Object> groupModel)
        throws GeneralException {
        return getGroupPlan(groupModel);
    }
    /**
     * Method to create the ObjectRequest Group provisioning plan
     * @param groupModel
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings({ "unchecked", "unchecked" })
	private ProvisioningPlan getGroupPlan(Map<String,Object> groupModel)
        throws GeneralException {
        ProvisioningPlan plan = null;
        if ( groupModel == null )
            return plan;
        ManagedAttribute current = getCurrent(groupModel);
        Attributes<String,Object> currentAttrs = (current != null ) ? current.getAttributes() : new Attributes<String,Object>();
        if(groupManagementLogger.isTraceEnabled()) {
        	groupManagementLogger.trace("Build Group Plan against model: " + groupModel);
        	groupManagementLogger.trace("Current Entitlement: " + ((current != null ) ? current.toXml() : "NULL"));
        }
        String appId = (String)MapUtil.get(groupModel, "sys.application.id");
        String appName = (String)MapUtil.get(groupModel, "sys.application.name");
        String type = (String)MapUtil.get(groupModel, "sys.type");
        Application app = null;
        if ( appId != null )
            context.getObject(Application.class, appId);
        if ( app == null ) {
            if ( appName != null )
                app = context.getObject(Application.class, appName);
        }
        if(app == null) {
        	groupManagementLogger.error("No Application found for group");
        	throw new GeneralException("No Application found for group");
        }
        Schema groupSchema = app.getSchema(type);
        if(groupSchema == null) {
        	groupManagementLogger.error("No defined schema type: " + type);
        	throw new GeneralException("No defined schema type: " + type);
        }
        String identityAttribute = groupSchema.getIdentityAttribute();
        String displayAttribute = groupSchema.getDisplayAttribute();
        String instanceAttribute = groupSchema.getInstanceAttribute();
        String groupHierarchyAttribute = groupSchema.getHierarchyAttribute();
        String nativeIdentity = (String)MapUtil.get(groupModel, "sys.nativeIdentity");
        if(nativeIdentity == null) {
        	nativeIdentity = (String)MapUtil.get(groupModel, identityAttribute);
        	groupManagementLogger.debug("Setting nativeIdentity from attribute " + identityAttribute + ": " + nativeIdentity);
        	MapUtil.put(groupModel, "sys.nativeIdentity", nativeIdentity);
        }
        String displayName = (String)MapUtil.get(groupModel, "sys.displayName");
        if(displayName == null) {
        	displayName = (String)MapUtil.get(groupModel, displayAttribute);
        	groupManagementLogger.debug("Setting displayName from attribute " + displayAttribute + ": " + displayName);
        	MapUtil.put(groupModel, "sys.displayName", displayName);
        }
        String instance = (String)MapUtil.get(groupModel, "sys.instance");
        if(instance == null) {
        	instance = (String)MapUtil.get(groupModel, instanceAttribute);
        	groupManagementLogger.debug("Setting instance from attribute " + instanceAttribute + ": " + instance);
        	MapUtil.put(groupModel, "sys.instance", instance);
        }
        String groupName = displayName;
        /*
         * Put Extended attributes at the base of the model
         */
        Map<String, Object> extendedMap = (Map<String, Object>)groupModel.get("extended");
        if(extendedMap != null) {
        	for(Map.Entry<String, Object> e: extendedMap.entrySet()) {
        		String key = e.getKey();
        		Object value = e.getValue();
        		groupManagementLogger.debug("Putting entitlement extended attribute at the base. " + key + ": " + value);
        		MapUtil.put(groupModel, key, value);
        	}
        }
        plan = new ProvisioningPlan();
        ObjectRequest req = new ObjectRequest();
        req.setType(type);
        req.setApplication(appName);
        req.setNativeIdentity(nativeIdentity);
        req.setInstance(instance);
        String maId = (String)MapUtil.get(groupModel, "sys.id");
        if ( maId == null) {
            req.setOp(ObjectOperation.Create);
        } else {
            req.setOp(ObjectOperation.Modify);
        }
        Set<String> keys = groupModel.keySet();
        if ( keys != null ) {
        	for ( String key : keys ) {
        		if ( key != null ) {
        			if (reservedKeys.contains(key))
        				continue;
        			Object value = groupModel.get(key);
        			Object currentValue = currentAttrs.get(key);
        			Difference diff = Difference.diff(currentValue, value);
        			if ( diff != null ) {
        				if (key.equals(ATTR_HIERARCHY_IDS) && includeInheritance) {
        					List<String> parents = (List<String>) value;
        					List<ManagedAttribute> newParents = new ArrayList<ManagedAttribute>();
        					List<String> newParentValues = new ArrayList<String>();
        					if (null != parents) {
        						for (String p : parents) {
        							ManagedAttribute ma = context.getObject(ManagedAttribute.class, p);
        							newParents.add(ma);
        							newParentValues.add(ma.getValue());
        						}
        					}
        					if (req.getOp().equals(ObjectOperation.Modify)) {
        						current.setInheritance(newParents);
        						AttributeRequest attr = new AttributeRequest(groupHierarchyAttribute, Operation.Set, newParentValues);
    	        				req.add(attr);
        					} else {
        						// What do we do here; MA will be created as a result of plan submission as well
        						groupManagementLogger.warn("Setting inheritance is not supported for Create operations ... ignoring");
        					}
        				}
        				else {
	        				AttributeRequest attr = new AttributeRequest(key, Operation.Set, value);
	        				req.add(attr);
        				}
        			}
        		}
        	} // End of Group Model keys iterator
        }
        //
        // check owner
        // There can be multiple owners, so we need to dynamically create a Workgroup to hold them
        // For edits we'll aslo need to update the Workgroups
        /*
         * Handle Ownership
         * - sys.owner
         *
         *
         * If sys.owner is a single user, then we just need to update the ManagedAttribute's owner to the 1 user
         * If it is a workgroup, we need to check to see if a WG already exists. If not, dynamically create it
         * The Workgroup owner is the first user in the group.
         */
        String currentOwnerName = null;
        boolean currentOwnerIsWG = false;
        if(current != null) {
        	Identity currentOwnerObj = current.getOwner();
        	if(currentOwnerObj != null) {
        		currentOwnerName = currentOwnerObj.getName();
        		if(currentOwnerObj.isWorkgroup()) {
        			currentOwnerIsWG = true;
        		}
        	}
        }
        String groupOwnerId = null;
        String groupOwnerName = null;
        List<String> groupOwners = Util.asList(MapUtil.get(groupModel, "sys.owner"));
        if(groupOwners != null && groupOwners.size() == 1) {
        	groupOwnerName = groupOwners.get(0);
        }
        else if(groupOwners != null && groupOwners.size() > 1) {
        	if(!expandOwners) {
        		groupManagementLogger.error("Cannot have multiple owners since the transformer does not have expandOwners");
        		throw new GeneralException("Cannot have multiple owners since the transformer does not have expandOwners");
        	}
        	//Need owner to be a Workgroup
        	groupManagementLogger.debug("Need to assign workgroup as owner of ManagedAttribute");
        	String dynamicWGName = getAutoGeneratedWGName(groupName, groupOwners);
        	/*
        	 * If the current owner is a Workgroup and is not the same as this Workgroup, then we cannot modify the
        	 * owners of the group ... throw an exception since we don't know what to do
        	 */
        	if(currentOwnerIsWG && currentOwnerName != null && !currentOwnerName.equals(dynamicWGName)) {
        		throw new GeneralException("Current Owner of group is a Workgroup that was created outside the Group Management Feature. Cannot modify members");
        	}
        	Identity ownerWorkgroup = context.getObject(Identity.class, dynamicWGName);
        	if(ownerWorkgroup == null) {
        		ownerWorkgroup = new Identity();
	        	ownerWorkgroup.setWorkgroup(true);
	    		ownerWorkgroup.setEmail("no-reply@grpMgmt.com");
	    		ownerWorkgroup.setPreference("workgroupNotificationOption",Identity.WorkgroupNotificationOption.MembersOnly);
	    		ownerWorkgroup.setName(dynamicWGName);
	    		ownerWorkgroup.setDisplayName(dynamicWGName);
				for(String name : groupOwners){
			        Identity idObj = context.getObject(Identity.class, name);
			        idObj.add(ownerWorkgroup);
			        context.saveObject(idObj);
	        	}
	    		context.saveObject(ownerWorkgroup);
	    		context.commitTransaction();
	        	ownerWorkgroup = context.getObject(Identity.class, dynamicWGName);
	        	groupOwnerName = ownerWorkgroup.getName();
        	}
        	else {
        		groupOwnerName = ownerWorkgroup.getName();
        		//WG Already exists ... need to update members
        		List<String> currentMemberIds = new ArrayList<String>();
        		QueryOptions ops = new QueryOptions();
        		ops.addFilter(Filter.ignoreCase(Filter.eq("workgroups.name", dynamicWGName)));
        		Iterator<Object[]> it = context.search(Identity.class, ops, "name");
        		if(it != null) {
        			while(it.hasNext()) {
        				Object[] next = it.next();
        				String wgMemberName = (String)next[0];
        				if(groupOwners.contains(wgMemberName)) {
        					currentMemberIds.add(wgMemberName);
        				}
        				else {
        					//Need to remove member from Workgroup
        					Identity wgMember = context.getObjectByName(Identity.class, wgMemberName);
        					if(wgMember != null) {
        						groupManagementLogger.debug("Removing WG " + ownerWorkgroup.getName() + " from " + wgMember.getName());
        						wgMember.remove(ownerWorkgroup);
        						context.saveObject(wgMember);
        					}
        				}
        			}
        			for(String id : groupOwners){
    			        if(currentMemberIds.contains(id)) {
    			        	//Already has workgroup
    			        	continue;
    			        }
    			        Identity idObj = context.getObject(Identity.class, id);
    			        if(idObj != null) {
    			        	groupManagementLogger.debug("Adding WG " + ownerWorkgroup.getName() + " to " + idObj.getName());
	    			        idObj.add(ownerWorkgroup);
	    			        context.saveObject(idObj);
    			        }
    	        	}
        		}
	    		context.commitTransaction();
        	}
        }
        groupManagementLogger.debug("In ManagedAttributeTransformer::groupOwner=" + groupOwnerId);
        doUpdateIfChanged(req, groupOwnerName, currentOwnerName, "sysOwner");
        String attribute = (String) MapUtil.get(groupModel, "sys.attribute");
        String currentAttribute = ( current != null ) ? current.getAttribute() : null;
        doUpdateIfChanged(req, attribute, currentAttribute, "sysAttribute" );
        String maDisp = (String) MapUtil.get(groupModel, "sys.displayName");
        String currentMaDisp = ( current != null && current.getDisplayName() != null ) ? current.getType().toString() : null;
        doUpdateIfChanged(req, maDisp, currentMaDisp, "sysDisplayName" );
        Map<String, String> currentDescriptions = current != null ? current.getDescriptions() : null;
        if(currentDescriptions == null) {
        	currentDescriptions = new HashMap<String, String>();
        }
        String maDesc = (String) MapUtil.get(groupModel, "sys.description");
        String currentDesc = current != null ? current.getDescription(Locale.getDefault()) : null;
        Difference descDiff = Difference.diff(maDesc, currentDesc);
        if ( descDiff != null ) {
        	Map<String, String> newDescs = new HashMap<String, String>();
        	newDescs.putAll(currentDescriptions);
        	newDescs.put(Locale.getDefault().toString(), maDesc);
        	AttributeRequest attr = new AttributeRequest("sysDescriptions", Operation.Set, newDescs);
	        req.add(attr);
    	}
        Boolean requestable = Util.otob(MapUtil.get(groupModel, "sys.requestable"));
        Boolean currentRequestable = ( current != null ) ? current.isRequestable() : null;
        doUpdateIfChanged(req, requestable, currentRequestable, "sysRequestable" );
        String maType = (String) MapUtil.get(groupModel, "sys.type");
        String currentMaType = current != null ? current.getType() : null;
        doUpdateIfChanged(req, maType, currentMaType, "sysManagedAttributeType" );
        if ( Util.size(req.getAttributeRequests()) == 0 ) {
            plan = null;
        } else {
            plan.addRequest(req);
        }
        return plan;
    }
    private String getAutoGeneratedWGName(String groupName, List<String> groupOwners) {
		groupManagementLogger.trace("Enter getAutoGeneratedWGName(" + groupName + "," + groupOwners);
		String autoGenWGName = "GrpMgmt-" + groupName+ "-Approvers";
		groupManagementLogger.trace("Exit getAutoGeneratedWGName(" + autoGenWGName);
		return autoGenWGName;
	}
	/**
     * Utility method to prepare an attribute which has changed for provisioning
     * @param req
     * @param newValue
     * @param currentValue
     * @param wfAttr
     */
    private void doUpdateIfChanged(AbstractRequest req, Object newValue, Object currentValue,  String attrName) {
        Difference attrDiff = Difference.diff(newValue, currentValue);
        if ( attrDiff != null ) {
            AttributeRequest attr = new AttributeRequest(attrName, Operation.Set, newValue);
            req.add(attr);
        }
    }
    /**
     * Prepare the list of Identity Provisioning Plans
     * This method does a diff of the current and proposed membership and only creates plans for updates
     * It then calls getPlans twice once for Identities to be added to the group and once for those to be removed from the group.
     * But, this method also works for create group operation
     * @param groupModel
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
	private List<ProvisioningPlan> getIdentityPlans(Map<String,Object> groupModel)
        throws GeneralException {
    	List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
    	boolean maxMembershipExceeded = Util.otob(MapUtil.get(groupModel, "sys.maxMembershipExceeded"));
    	if(maxMembershipExceeded) {
    		groupManagementLogger.warn("Attempting to get identity plans when max membership is exceeded");
    		return plans;
    	}
    	if(!includeMembers) {
    		groupManagementLogger.warn("Attempting to get identity plans when include members is false");
    		return plans;
    	}
        List<String> currentMemberShip = null;
        List<String> modelMembership = (List<String>)MapUtil.get(groupModel, ATTR_IDENTITY_MEMBERSHIP_IDS);
        ManagedAttribute current = getCurrent(groupModel);
        if ( current != null ) {
            currentMemberShip = getIdentityMembership(current);
        }
        Difference diff = Difference.diff(currentMemberShip, modelMembership);
        if ( diff != null ) {
            List<String> added = diff.getAddedValues();
            if ( Util.size(added) > 0  ) {
                List<ProvisioningPlan> addPlans = getPlans(groupModel, added, true);
                if ( Util.size(addPlans) > 0 )
                    plans.addAll(addPlans);
            }
            List<String> removed = diff.getRemovedValues();
            if ( Util.size(removed) > 0  ) {
                List<ProvisioningPlan> removePlans = getPlans(groupModel, removed, false);
                if ( Util.size(removePlans) > 0 )
                    plans.addAll(removePlans);
            }
        }
        return plans;
    }
    /**
     * Prepare the list of Provisioning plans from the set of Identities provided
     * The boolean argument add tells whether this is an add/remove operation
     * @param groupModel
     * @param values
     * @param add
     * @return
     * @throws GeneralException
     */
    private List<ProvisioningPlan> getPlans(Map<String,Object> groupModel, List<String> values, boolean add)
        throws GeneralException {
        if ( values == null)
            return null;
        String appName = (String)MapUtil.get(groupModel, "sys.application.name");
        List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
        for ( String value : values ) {
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.setNativeIdentity(value);
            AccountRequest acct = new AccountRequest();
            acct.setNativeIdentity(getNativeIdentity(value, appName));
            acct.setApplication(appName);
            acct.setOperation(AccountRequest.Operation.Modify);
            AttributeRequest attr = new AttributeRequest();
            if ( add ) {
                attr.setOp(Operation.Add);
            } else {
                attr.setOp(Operation.Remove);
            }
            attr.setValue(MapUtil.get(groupModel, "sys.nativeIdentity"));
            attr.setName((String)MapUtil.get(groupModel, "sys.attribute"));
            acct.add(attr);
            plan.add(acct);
            plans.add(plan);
        }
        return Util.size(plans) > 0 ? plans : null;
    }
    /**
     * Get the nativeIdentity for the LDAP Application. What is provisioned is the account which is nothing but the DN in this case
     * The display attribute maybe different such as CN.
     * @param identityName
     * @param appName
     * @return
     * @throws GeneralException
     */
    private String getNativeIdentity(String identityName, String appName) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity.name", identityName));
        ops.add(Filter.eq("application.name", appName));
        String ni = null;
        Iterator<Object[]> objs = context.search(Link.class, ops, "nativeIdentity");
        if ( objs != null ) {
            while ( objs.hasNext() ) {
                Object[] row = objs.next();
                if ( row != null ) {
                    if ( row.length > 0 ) {
                        ni = (String)row[0];
                    }
                }
            }
        }
        return ni;
    }
}