/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidonboarding.groupmanagement.workflow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Aggregator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.ChangeSummary;
import sailpoint.object.Custom;
import sailpoint.object.Difference;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.object.FormRef;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.rapidonboarding.groupmanagement.transformer.ManagedAttributeTransformer;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.workflow.WorkflowContext;
/**
 * Workflow library containing utilities for LDAP Group management Management.
 *
 */
public class ManagedAttributeLibrary{
    private static Log groupManagementLogger = LogFactory.getLog("rapidapponboarding.rules");
    /**
     * Localize a message.
     */
    public static String getMessage(String key, Object arg) {
        Message lm = new Message(key, arg);
        return lm.getLocalizedMessage();
    }
    /**
     * Coerce an argument into a list of strings.
     * Created for the ARG_SELECTIONS argument used with remediateViolation
     * but relatively general.
     *
     * The value of the selections argument may be one of:
     *
     *    - String
     *    - List<String>
     *
     * Further, each String may be a CSV.  Items returned by
     * getRemediatables will be CSVs, and since you can select both
     * left and right sides the selection may be a List of CSVs.
     * We need to flatten all this into a list of role names
     * for remediation.
     */
    protected static List<String> getStringList(Object value) {
        List<String> strings = null;
        if (value instanceof String) {
            strings = Util.csvToList((String)value);
        }
        else if (value instanceof Collection) {
            strings = new ArrayList<String>();
            Collection c = (Collection)value;
            for (Object el : c) {
                if (el instanceof String)
                    strings.addAll(Util.csvToList((String)el));
            }
        }
        return strings;
    }
    public ManagedAttributeLibrary() {
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    // Map Model
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * An LDAP Group is nothing but a ManagedAttribute. This method retrieves the ManagedAttribute object and its associations with other objects.
     * For a Group Modify operation this method retrieves the existing state of the group including Identity membership and group hierarchy.
     * It also retrieves the group owners which are managed in IIQ.
     * For a Create Group operation it just instantiates a new GroupModel object and returns it to the calling Workflow.
     * @param wfc
     * @return Map
     * @throws GeneralException
     */
    public Map<String,Object> getManagedAttributeModel(WorkflowContext wfc)
        throws GeneralException {
        SailPointContext ctx = wfc.getSailPointContext();     // The WorkflowCase has a handle to the SailpointContext
        Attributes<String,Object> args = wfc.getArguments();  // Retrieve the Workflow arguments
        String managedAttributeId = Util.getString(args, "maId"); // Get the ManagedAttributeId or in other words the Group Object's Id in IdentityIQ
        String appId = Util.getString(args, "appId"); // Get Application Id of the LDAP system
        if ( appId == null ) {
            throw new GeneralException("appId was null and is required.");
        }
        Application app = ctx.getObject(Application.class, appId); // Get the LDAP Application object
        if ( app == null ) {
            throw new GeneralException("Unable to resolve application for '"+appId+"'");
        }
        String value = Util.getString(args, "value"); //Get the String native Identity value of the ManagedAttribute, which in this case is the Group's distinguishedName
        String name = Util.getString(args, "name"); // The Group attribute name passed in from the Workflow. This is usually 'memberOf'
        String type = Util.getString(args, "type");
        Object maxMembers = Util.get(args, "maxMembersToManage");
        // Next get the managedAttribute or Group object for editing. Alternately create a new one for Create operation.
        ManagedAttribute attr = null;
        if ( managedAttributeId != null ) {
            attr = ctx.getObject(ManagedAttribute.class, managedAttributeId);
        } else {
            if ( appId != null) {
            	groupManagementLogger.debug("Search for attribute with appId: " + appId + ", name: " + name + ", value: "+ value + ", type: " + type);
            	attr = ManagedAttributer.get(ctx, appId, false, name, value, type);
                if ( attr == null ) {
                	groupManagementLogger.debug("Creating Attribute");
                	attr = new ManagedAttribute();
                    attr.setApplication(app);
                    attr.setName(name);
                    attr.setType(type);
                    attr.setValue(value);
                }
                else {
                	groupManagementLogger.debug("Found Attribute");
                }
                if(groupManagementLogger.isTraceEnabled()) {
                	groupManagementLogger.trace("Have Attribute: " + attr.toXml());
            	}
            }
        }
        //Instantiate the transformer and offload the responsibility to generate the map to it.
        // The transformer will also query the associated objects like group hierarchy, identityMembership and Group Ownership
        groupManagementLogger.debug("Instantiating ManagedAttributeTransformer with context..."+ctx);
        Map<String, Object> ops = new HashMap<String, Object>();
        ops.put(ManagedAttributeTransformer.ATTR_OPTION_INCLUDE_PREVIOUS, true);
        ops.put(ManagedAttributeTransformer.ATTR_OPTION_INCLUDE_MEMBERS, true);
        ops.put(ManagedAttributeTransformer.ATTR_OPTION_INCLUDE_INHERITANCE, true);
        ops.put(ManagedAttributeTransformer.ATTR_OPTION_EXPAND_WORKGROUP_OWNERS, true);
        if(maxMembers != null) {
        	ops.put(ManagedAttributeTransformer.ATTR_OPTION_MAX_MEMBERSHIP_TO_MANAGE, maxMembers);
        }
        ManagedAttributeTransformer transformer = new ManagedAttributeTransformer(ctx, ops);
        Map<String,Object> mapModel = transformer.toMap(attr);
        return mapModel;
    }
    public static Form getReadOnlyForm(SailPointContext context, Form form) throws GeneralException{
    	groupManagementLogger.trace("Enter getReadOnlyForm");
    	if(form == null) {
    		throw new GeneralException("Passed in Form is null");
    	}
    	Form readOnlyForm = (Form) form.deepCopy((sailpoint.tools.xml.XMLReferenceResolver) context);
    	readOnlyForm.setReadOnly(true);
    	List<FormItem> newFormItems = new ArrayList<FormItem>();
    	List<FormItem> oldFormItems = readOnlyForm.getItems();
    	if(oldFormItems != null) {
    		for(FormItem item: oldFormItems) {
    			addFormItemToList(context, newFormItems, item, false, true);
    		}
    	}
    	readOnlyForm.setItems(newFormItems);
    	if(groupManagementLogger.isDebugEnabled()) {
    		groupManagementLogger.debug("readOnlyForm: " + readOnlyForm.toXml());
    	}
    	groupManagementLogger.trace("Exit getReadOnlyForm");
    	return readOnlyForm;
    }
    private static void addFormItemToList(SailPointContext context, List<FormItem> newFormItems, FormItem item, boolean ignoreButtons, boolean makeReadOnly) throws GeneralException {
		groupManagementLogger.trace("Enter addFormItemToList");
		if(item == null || newFormItems == null) {
			groupManagementLogger.warn("Exit addFormItemToList: Invalid args");
			return;
		}
		if(item instanceof Field) {
			Field field = (Field) ((Field)item).deepCopy(context);
			if(makeReadOnly) {
				field.setReadOnly(true);
				field.setRequired(false);
				if(field.getName() != null) {
					field.setAllowedValuesDefinition(null);
					field.setScript(null);
					field.setValue(null);
					field.setValueDefinition(null);
				}
				field.setValidationScript(null);
				field.setValidationRule(null);
			}
			newFormItems.add(field);
		}
		else if(item instanceof Form.Button && !ignoreButtons) {
			newFormItems.add(item);
		}
		else if(item instanceof Form.Section) {
			Form.Section section = (Form.Section) ((Form.Section)item).deepCopy(context);
			if(makeReadOnly) {
				List<FormItem> sectionItems = section.getItems();
				if(sectionItems != null) {
					for(FormItem sectionItem: sectionItems) {
						if(sectionItem instanceof Field) {
							Field field = (Field)sectionItem;
							field.setReadOnly(true);
							field.setRequired(false);
							if(field.getName() != null) {
								field.setAllowedValuesDefinition(null);
								field.setScript(null);
								field.setValue(null);
								field.setValueDefinition(null);
							}
							field.setValidationScript(null);
							field.setValidationRule(null);
						}
					}
				}
			}
			newFormItems.add(section);
		}
		else if(item instanceof FormRef) {
			String name = ((FormRef)item).getName();
			Form referencedForm = context.getObject(Form.class, name);
			if(referencedForm == null) {
				throw new GeneralException("Could not find referenced form: " + name);
			}
			List<FormItem> formItems = referencedForm.getItems();
	    	if(formItems != null) {
	    		for(FormItem subItem: formItems) {
	    			addFormItemToList(context, newFormItems, subItem, true, makeReadOnly);
	    		}
	    	}
		}
	}
	/**
     * Updates the system deltas. These include
     * -- membership adds/removes
     * -- owner adds/removes
     * -- hierarchy adds/removes
     * @param wfc
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
	public void updateSysDeltas(WorkflowContext wfc) throws GeneralException {
    	groupManagementLogger.trace("Enter updateSysDeltas");
    	Attributes<String,Object> args = wfc.getArguments();
        // Get the ManagedAttribute Model stored in the Workflow
        Map<String,Object> groupModel = (Map<String,Object>)Util.get(args, "maModel");
        SailPointContext ctx = wfc.getSailPointContext();
        /*
        	Calculate new members and removed members and put them at
        	deltas.addedMembers
        	deltas.removedMembers
        */
        List<String> modelMembership = (List<String>)MapUtil.get(groupModel, "identityMembership");
    	List<String> currentMemberShip = (List<String>)MapUtil.get(groupModel, "previous.identityMembership");
	    List<String> addedMembers = new ArrayList<String>();
	    List<String> removedMembers = new ArrayList<String>();
        Identity diffMember = null;
	    Difference diff = Difference.diff(currentMemberShip, modelMembership);
        if ( diff != null ) {
            List<String> added = diff.getAddedValues();
            if ( Util.size(added) > 0  ) {
				for(String addedMember : added){
					groupManagementLogger.debug("addedMember: " + addedMember);
					diffMember = ctx.getObject(Identity.class, addedMember);
					if(diffMember != null) {
						addedMembers.add(diffMember.getName());
					}
				}
            }
            List<String> removed = diff.getRemovedValues();
            if ( Util.size(removed) > 0  ) {
				for(String removedMember : removed){
					groupManagementLogger.debug("removedMember: " + removedMember);
					diffMember = ctx.getObject(Identity.class, removedMember);
					if(diffMember != null) {
						removedMembers.add(diffMember.getName());
					}
				}
            }
        }
        MapUtil.put(groupModel, "sys.addedMembers", addedMembers);
        MapUtil.put(groupModel, "sys.removedMembers", removedMembers);
        /*
        	Calculate new members and removed members and put them at
        	deltas.addedMembers
        	deltas.removedMembers
        */
        List<String> modelOwners = (List<String>) MapUtil.get(groupModel, "sys.owner");
    	List<String> currentOwners = (List<String>) MapUtil.get(groupModel, "previous.sys.owner");
	    List<String> addedOwners = new ArrayList<String>();
	    List<String> removedOwners = new ArrayList<String>();
        Identity diffOwner = null;
	    diff = Difference.diff(currentOwners, modelOwners);
        if ( diff != null ) {
            List<String> added = diff.getAddedValues();
            if ( Util.size(added) > 0  ) {
				for(String addedOwner : added){
					groupManagementLogger.debug("addedOwner: " + addedOwner);
					diffOwner = ctx.getObject(Identity.class, addedOwner);
					if(diffOwner != null) {
						addedOwners.add(diffOwner.getName());
					}
				}
            }
            List<String> removed = diff.getRemovedValues();
            if ( Util.size(removed) > 0  ) {
				for(String removedOwner : removed){
					groupManagementLogger.debug("removedOwner: " + removedOwner);
					diffOwner = ctx.getObject(Identity.class, removedOwner);
					if(diffOwner != null) {
						removedOwners.add(diffOwner.getName());
					}
				}
            }
        }
        MapUtil.put(groupModel, "sys.addedOwners", addedOwners);
        MapUtil.put(groupModel, "sys.removedOwners", removedOwners);
        /*
        	Calculate new members and removed members and put them at
        	deltas.addedMembers
        	deltas.removedMembers
        */
        List<String> modelParents = (List<String>) MapUtil.get(groupModel, ManagedAttributeTransformer.ATTR_HIERARCHY_IDS);
    	List<String> currentParents = (List<String>) MapUtil.get(groupModel, "previous." + ManagedAttributeTransformer.ATTR_HIERARCHY_IDS);
	    List<String> addedParents = new ArrayList<String>();
	    List<String> removedParents = new ArrayList<String>();
	    ManagedAttribute diffParent = null;
	    diff = Difference.diff(currentParents, modelParents);
        if ( diff != null ) {
            List<String> added = diff.getAddedValues();
            if ( Util.size(added) > 0  ) {
				for(String addedParent : added){
					groupManagementLogger.debug("addedParent: " + addedParent);
					diffParent = ctx.getObject(ManagedAttribute.class, addedParent);
					if(diffParent != null) {
						addedParents.add(diffParent.getDisplayableName());
					}
				}
            }
            List<String> removed = diff.getRemovedValues();
            if ( Util.size(removed) > 0  ) {
				for(String removedParent : removed){
					groupManagementLogger.debug("removedParent: " + removedParent);
					diffParent = ctx.getObject(ManagedAttribute.class, removedParent);
					if(diffParent != null) {
						removedParents.add(diffParent.getDisplayableName());
					}
				}
            }
        }
        MapUtil.put(groupModel, "sys.addedParents", addedParents);
        MapUtil.put(groupModel, "sys.removedParents", removedParents);
    }
    /**
     * Build up the list of provisioning plans from the map model that has been updated.
     * The list will have 1 ObjectRequestPlan for the group create/edit
     * And a list of Identity Plans for addition/removal of the newly created group to the selected Identities
     * For Create all Identity plans have operation 'Add'
     * For Modify each Identity plan maybe either an 'Add' or 'Remove'
     * The returned ProvisioningPlans will be null if nothing has changed.
     * @param wfc
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ProvisioningPlan> buildPlansFromManagedAttributeModel(WorkflowContext wfc)
        throws GeneralException {
        // Retrieve the Workflow arguments
        Attributes<String,Object> args = wfc.getArguments();
        // Get the ManagedAttribute Model stored in the Workflow
        Map<String,Object> maModel = (Map<String,Object>)Util.get(args, "maModel");
        if ( maModel == null ) {
            throw new GeneralException("ManagedAttributebuildPlansFromManagedAttributeModel map model was null.");
        }
        // Instantiate the transformer and call the method to return the list of Provisioning Plans (1 group and 'n' number of Identity plans)
        groupManagementLogger.debug("Instantiating ManagedAttributeTransformer with context..."+wfc.getSailPointContext());
        ManagedAttributeTransformer transfomer = new ManagedAttributeTransformer(wfc.getSailPointContext(), (Map<String,Object>)maModel.get("transformerOptions"));
        return transfomer.mapToPlans(maModel);
    }
    /**
     * Build the ObjectRequest plan for the group change.
     * @param wfc
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public ProvisioningPlan buildGroupPlanFromManagedAttributeModel(WorkflowContext wfc)
        throws GeneralException {
        // Retrieve the Workflow arguments
        Attributes<String,Object> args = wfc.getArguments();
        // Get the ManagedAttribute Model stored in the Workflow
        Map<String,Object> maModel = (Map<String,Object>)Util.get(args, "maModel");
        if ( maModel == null ) {
            throw new GeneralException("ManagedAttribute map model was null.");
        }
        // Instantiate the transformer and call the method to return the list of Provisioning Plans (1 group and 'n' number of Identity plans)
        groupManagementLogger.debug("Instantiating ManagedAttributeTransformer with context..."+wfc.getSailPointContext());
        ManagedAttributeTransformer transfomer = new ManagedAttributeTransformer(wfc.getSailPointContext(), (Map<String,Object>)maModel.get("transformerOptions"));
        return transfomer.mapToPlan(maModel);
    }
    /**
     * Build the ObjectRequest plan for the group change.
     * @param wfc
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public void updateModelWithWG(WorkflowContext wfc)
        throws GeneralException {
    	SailPointContext ctx = wfc.getSailPointContext();     // The WorkflowCase has a handle to the SailpointContext
        Attributes<String,Object> args = wfc.getArguments();  // Retrieve the Workflow arguments
        String wgName = Util.getString(args, "wgName"); // Get the ManagedAttributeId or in other words the Group Object's Id in IdentityIQ
        // Get the ManagedAttribute Model stored in the Workflow
        Map<String,Object> maModel = (Map<String,Object>)Util.get(args, "maModel");
        if ( maModel == null ) {
            throw new GeneralException("ManagedAttribute map model was null.");
        }
        String groupOwnerId = null;
        List<String> selectedOwners = (List<String>)MapUtil.get(maModel, "sys.owner");
        if(selectedOwners != null && selectedOwners.size() > 1) {
	        Identity ownerWorkgroup = ctx.getObject(Identity.class, wgName);
	    	if(ownerWorkgroup == null) {
	    		ownerWorkgroup = new Identity();
	        	ownerWorkgroup.setWorkgroup(true);
	    		ownerWorkgroup.setEmail("no-reply@grpMgmt.com");
	    		ownerWorkgroup.setPreference("workgroupNotificationOption",Identity.WorkgroupNotificationOption.MembersOnly);
	    		ownerWorkgroup.setName(wgName);
	    		ownerWorkgroup.setDisplayName(wgName);
	    		ctx.saveObject(ownerWorkgroup);
	    		ctx.commitTransaction();
	        	ownerWorkgroup = ctx.getObject(Identity.class, wgName);
	        	groupOwnerId = ownerWorkgroup.getId();
	    	}
	    	else {
	    		groupOwnerId = ownerWorkgroup.getId();
	    	}
	    	if(groupOwnerId != null) {
	    		MapUtil.put(maModel, "sys.owner", groupOwnerId);
	    		MapUtil.put(maModel, "displayOnly.owners", selectedOwners);
	    	}
        }
    }
    /**
     * Build up the list of user provisioning plans from the map model that has been updated.
     * The list of Identity Plans for addition/removal of the group to the selected Identities
     * For Create all Identity plans have operation 'Add'
     * For Modify each Identity plan maybe either an 'Add' or 'Remove'
     * The returned ProvisioningPlans will be null if nothing has changed.
     * @param wfc
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ProvisioningPlan> buildIdentityPlansFromManagedAttributeModel(WorkflowContext wfc)
        throws GeneralException {
        // Retrieve the Workflow arguments
        Attributes<String,Object> args = wfc.getArguments();
        // Get the ManagedAttribute Model stored in the Workflow
        Map<String,Object> maModel = (Map<String,Object>)Util.get(args, "maModel");
        if ( maModel == null ) {
            throw new GeneralException("ManagedAttribute map model was null.");
        }
        // Instantiate the transformer and call the method to return the list of Provisioning Plans (1 group and 'n' number of Identity plans)
        groupManagementLogger.debug("Instantiating ManagedAttributeTransformer with context..."+wfc.getSailPointContext());
        ManagedAttributeTransformer transfomer = new ManagedAttributeTransformer(wfc.getSailPointContext(), (Map<String,Object>)maModel.get("transformerOptions"));
        return transfomer.mapToIdentityPlans(maModel);
    }
    /**
     * Execute only the Group creation plan. If there are errors in this then do a retry after pausing for the time interval in minutes
     * set in the Workflow call. The retry logic will keep calling this method till max. number of retries or success.
     * Once the Group has been created or updated, the identity membership provisioning plans will be executed.
     * @param wfc
     * @return
     * @throws GeneralException
     */
    public String executeGroupObjectRequestPlan(WorkflowContext wfc) throws GeneralException{
        //Get the Workflow arguments
        Attributes<String,Object> args = wfc.getArguments();
        // Retries is the current sequence of the retry operation
        int retries = ((Integer)(Util.get(args,"retries"))).intValue();
        // maxRetries is the maximum times the provisioning should be attemped befpre erroring out.
        // This is read from the Application object in the Workflow variable initialization and passed here from the call step
        int maxRetries = ((Integer)(Util.get(args,"maxRetries"))).intValue();
        // A boolean to decide if the maxRetries has been attempted and thus we need to audit and notify the error
        boolean auditError = false;
        String operation = (String)Util.get(args,"operation"); // Whether its an 'Modify' or 'Add' operation
        String launcher = (String)Util.get(args,"launcher"); // The cube name of person who launched the Workflow
        String sAMAccountName = (String)Util.get(args,"sAMAccountName"); // The group object's sAMAccountName
        String application = (String)Util.get(args,"application"); // Get Application Id of the LDAP system
        // if retries  > maxRetries then send error notification to the Support team and audit the error
        if(retries >= maxRetries){
            auditError = true;
        }
        // Get the list of plans passed in from the Workflow
        List<ProvisioningPlan> plans = (List<ProvisioningPlan>)Util.get(args, "plans");
        if ( plans == null ) {
            throw new GeneralException("Plans to execute were null.");
        }
        // Get the gMgmtIIQSupportMailbox from Workflow. This is read from the GrpMgmt-Custom-Config-Common in Workflow
        String grpMgmtIIQSupportMailbox = (String)Util.get(args, "grpMgmtIIQSupportMailbox");
        String groupDN = (String)Util.get(args, "groupDN");
        groupManagementLogger.debug("groupDN:"+groupDN);
        ProvisioningPlan groupPlan = null;
        // The first plan added to the list was the Group Plan.
        // But doesn't harm to verify if the plan really has a group object request and not an Identity request
        ProvisioningPlan tmpPlan = plans.get(0);
        if(tmpPlan.getObjectRequests() != null){
            groupPlan = tmpPlan;
        }
        if(groupPlan != null){
            groupManagementLogger.debug("We have a task at hand to execute the Group Plan:"+groupPlan.toXml());
            WorkflowCase wfcase = null;
            String groupWorkflow = Util.getString(args, "groupWorkflow");
            if ( groupWorkflow == null ) {
                groupWorkflow = "Entitlement Update"; // This is the OOTB IIQ Workflow to create or update Groups.
            }
            // Execute the thread to instantiate and run the groupWorkflow for Group Provisioning.
            // The returned WorkflowCase holds the live object instance of the Group Workflow with all variables populated
            // and provisioning status & error messages too.
            wfcase = runWorkflow(groupWorkflow, wfc.getSailPointContext(), groupPlan, args);
            // If the call to execute the Workflow gets into error, it will end the particular instance of the Group Workflow
            // and set the error messages in Errors. Use isError() to get if there was an error and getErrors to get the list of error messages
            if(wfcase.isError()){
                // Send a notification to the GrpMgmt IIQ Support Team with the proper error message
                sendGroupErrorEmailNotificationNAudit(wfc.getSailPointContext(),grpMgmtIIQSupportMailbox,wfcase.getErrors(),plans,groupDN,auditError,operation,launcher,sAMAccountName,application);
                return "Error";
            }else{
                return "Success";
            }
        }
        return "Success"; // This return message helps the Workflow decide if it needs to call this method again
    }
    @SuppressWarnings("unchecked")
    /* Execute the Identity plans. One plan for each Identity.
     * This method is called IF and ONLY IF the Group provisioning is successful.
     * The grpMgmt copy of the OOTB Workflow LCM Provisioning is called for each Identity
     * LCM Provisioning has retries built-in and is driven by the threshold and maxRetries set in the Application XML.
     *
     */
    public void executeIdentityPlans(WorkflowContext wfc) throws GeneralException
         {
    	SailPointContext ctx = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();
        String identityWorkflow = Util.getString(args, "identityWorkflow");
        if(identityWorkflow == null) {
        	identityWorkflow = "LCM Provisioning";
        }
        String removeApprovalScheme = Util.getString(args, "removeApprovalScheme");
        String addApprovalScheme = Util.getString(args, "addApprovalScheme");
        List<ProvisioningPlan> plans = (List<ProvisioningPlan>)Util.get(args, "plans");
        // Group plans first. But no need to order the plans as they were already added in order.
        //plans = orderPlans(plans);
        // Since we are here the Group Object Request must have been complete. So remove the Group plan
        groupManagementLogger.debug("Identity Plans before removing objectrequests:"+plans.size());
        ProvisioningPlan tmpPlan = plans.get(0);
        if(tmpPlan.getObjectRequests() != null){
            plans.remove(tmpPlan);
        }
        groupManagementLogger.debug("Identity Plans after removing objectrequests:"+plans.size());
        String flow = Util.getString(args, "flow");
        String source = Util.getString(args, "source");
        String launcher = Util.getString(args, "launcher");
        String foregroundProvisioning = Util.getString(args, "foregroundProvisioning");
        if(plans.size() > 0){
            groupManagementLogger.debug("Identity Plans to be executed:"+plans.size());
            for ( ProvisioningPlan plan : plans ) {
            	Map<String, Object> wfArgs = new HashMap<String, Object>();
            	String approvalScheme = null;
                wfArgs.put("flow", flow);
                wfArgs.put("source", source);
                wfArgs.put("launcher", launcher);
                wfArgs.put("foregroundProvisioning", foregroundProvisioning);
                groupManagementLogger.debug(plan.toXml());
                boolean isAddRequest = isIdentityPlanAddRequest(plan);
                if(isAddRequest) {
                	approvalScheme = addApprovalScheme;
                }
                else {
                	approvalScheme = removeApprovalScheme;
                }
                wfArgs.put("approvalScheme", approvalScheme);
                wfArgs.put("plan", plan);
                String identityName = plan.getNativeIdentity();
                scheduleIdentityProvisioningWorkflow(ctx, identityWorkflow, wfArgs, identityName, isAddRequest);
            }
        }
    }
    private void scheduleIdentityProvisioningWorkflow(SailPointContext context, String workflowName,
    		Map<String, Object> workflowArguments, String identityName, boolean isAddRequest) throws GeneralException{
		groupManagementLogger.trace("Enter scheduleIdentityProvisioningWorkflow: " + workflowName + " for " + workflowArguments);
		Workflow eventWorkflow = context.getObject(Workflow.class, workflowName);
		if (null == eventWorkflow) {
			groupManagementLogger.error("Could not find a workflow named: " + workflowName);
			throw new GeneralException("Invalid worklfow: " + workflowName);
		}
		long launchTime = new Date().getTime();
		String caseName = null;
		if(isAddRequest) {
			caseName = "GrpMgmt-AddMemberRequest-" + identityName + "-" + launchTime;
		}
		else {
			caseName = "GrpMgmt-RemoveMemberRequest-" + identityName + "-" + launchTime;
		}
		Attributes<String, Object> reqArgs = new Attributes<String, Object>();
		reqArgs.put(sailpoint.workflow.StandardWorkflowHandler.ARG_REQUEST_DEFINITION,  sailpoint.request.WorkflowRequestExecutor.DEFINITION_NAME);
		reqArgs.put(sailpoint.workflow.StandardWorkflowHandler.ARG_WORKFLOW,   workflowName);
		reqArgs.put(sailpoint.workflow.StandardWorkflowHandler.ARG_REQUEST_NAME,  caseName);
		reqArgs.put( "requestName", caseName );
		Attributes<String, Object> wfArgs = new Attributes<String, Object>();
		wfArgs.put("identityName", identityName);
		wfArgs.put("workflow", workflowName);
		if(workflowArguments != null){
			wfArgs.putAll(workflowArguments);
		}
		reqArgs.putAll(wfArgs);
		Request req = new Request();
		RequestDefinition reqdef = context.getObject(RequestDefinition.class, "Workflow Request");
		req.setDefinition(reqdef);
		req.setEventDate( new Date( launchTime ) );
		req.setName(caseName);
		req.setAttributes( reqdef, reqArgs );
		if(groupManagementLogger.isDebugEnabled()) {
			groupManagementLogger.debug("Add Request to queue: " + req.toXml());
		}
		// Schedule the work flow via the request manager.
		RequestManager.addRequest(context, req);
		groupManagementLogger.trace("Exit scheduleWorkflow");
	}
    private boolean isIdentityPlanAddRequest(ProvisioningPlan plan) {
		boolean isAddRequest = false;
		if(plan != null) {
			List<AccountRequest> acctReqs = plan.getAccountRequests();
			if(acctReqs != null) {
				for(AccountRequest acctReq: acctReqs) {
					List<AttributeRequest> attrReqs = acctReq.getAttributeRequests();
					if(attrReqs != null) {
						for(AttributeRequest attrReq: attrReqs) {
							if(ProvisioningPlan.Operation.Add == attrReq.getOperation()) {
								isAddRequest = true;
							}
						}
					}
				}
			}
		}
		return isAddRequest;
	}
	@SuppressWarnings("unchecked")
    /* Add in Group Management Feature specific changes
     * - Updated Owners
     * - Updated Members
     */
    public ChangeSummary updateGrpMgmtChanges(WorkflowContext wfc) throws GeneralException
         {
        Attributes<String,Object> args = wfc.getArguments();
        SailPointContext context = wfc.getSailPointContext();
        ChangeSummary changes = (ChangeSummary)Util.get(args, "changes");
        String maId = (String)Util.get(args, "maId");
        Map<String, Object> groupModel = (Map<String, Object>)Util.get(args,"maModel");
        if(groupModel == null) {
        	groupManagementLogger.warn("No Group Model to add changes from");
        	return changes;
        }
        if(changes == null) {
        	changes = new ChangeSummary();
        }
        List<Difference> diffs = changes.getDifferences();
        if(diffs == null) {
        	diffs = new ArrayList<Difference>();
        	changes.setDifferences(diffs);
        }
        //Previously calculated added/removed members, owners, and parents for convenience
        List<String> addedOwners = (List<String>) MapUtil.get(groupModel, "sys.addedOwners");
        List<String> removedOwners = (List<String>) MapUtil.get(groupModel, "sys.removedOwners");
        if(addedOwners != null || removedOwners != null) {
        	Difference diff = new Difference();
        	diff.setAttribute("owner");
        	diff.setDisplayName("Owners");
        	diff.setMulti(true);
        	diff.setAddedValues(addedOwners);
        	diff.setRemovedValues(removedOwners);
        	diffs.add(diff);
        }
        List<String> addedMembers = (List<String>) MapUtil.get(groupModel, "sys.addedMembers");
        List<String> removedMembers = (List<String>) MapUtil.get(groupModel, "sys.removedMembers");
        if(addedMembers != null || removedMembers != null) {
        	Difference diff = new Difference();
        	diff.setAttribute("identityMembership");
        	diff.setDisplayName("Members");
        	diff.setMulti(true);
        	diff.setAddedValues(addedMembers);
        	diff.setRemovedValues(removedMembers);
        	diffs.add(diff);
        }
        List<String> addedParents = (List<String>) MapUtil.get(groupModel, "sys.addedParents");
        List<String> removedParents = (List<String>) MapUtil.get(groupModel, "sys.removedParents");
        if(addedParents != null || removedParents != null) {
        	Difference diff = new Difference();
        	diff.setAttribute("inheritedGroups");
        	diff.setDisplayName("Inherited Groups");
        	diff.setMulti(true);
        	diff.setAddedValues(addedParents);
        	diff.setRemovedValues(removedParents);
        	diffs.add(diff);
        }
        return changes;
    }
    private List<String> translateIdListToName(SailPointContext context, List<String> ids) throws GeneralException {
		List<String> names = null;
		if(ids != null) {
			names = new ArrayList<String>();
			for(String id: ids) {
				QueryOptions ops = new QueryOptions();
				ops.addFilter(Filter.ignoreCase(Filter.eq("id", id)));
				Iterator<Object[]> it = context.search(Identity.class, ops, "name");
				while(it.hasNext()) {
					names.add((String)it.next()[0]);
				}
			}
		}
		return null;
	}
	/**
     * A prvate method to launch the provisioning workflows
     * @param context
     * @param plan
     * @param stepArgs
     * @return
     */
    private WorkflowCase launchWorkflow(SailPointContext context, ProvisioningPlan plan, Map<String,Object> stepArgs) throws GeneralException{
            WorkflowCase wfcase = null;
            if ( plan != null ) {
                Map<String,Object> args = new HashMap<String,Object>();
                if ( stepArgs != null )
                    args.putAll(stepArgs);
                // Means its an Identity plan and the "LCM Provisioning" Workflow should be called
                if ( plan.getAccountRequests() != null ) {
                    // Identity change
                    String identityWorkflow = Util.getString(stepArgs, "identityWorkflow");
                    if ( identityWorkflow == null ) {
                        identityWorkflow = "LCM Provisioning";
                    }
                    args.put("identityName", plan.getNativeIdentity());
                    //args.put("project", project);
                    //args.put("notificationScheme", "user,requester");
                    groupManagementLogger.debug("About to launch Identity Workflow...");
                    //try{
                    // Call runWorkflow to execute the plan
                    wfcase = runWorkflow(identityWorkflow, context, plan, args);
                    //}catch(GeneralException ge){
                    //  groupManagementLogger.debug("Caught:"+ge.getMessage());
                    //}catch(Exception e){
                    //  groupManagementLogger.debug(e.getMessage());
                    //}
                } /*else {
                    // group change
                    String groupWorkflow = Util.getString(stepArgs, "groupWorkflow");
                    if ( groupWorkflow == null ) {
                        groupWorkflow = "Entitlement Update";
                    }
                        groupManagementLogger.debug("About to launch Group Workflow...");
                        wfcase = runWorkflow(groupWorkflow, context, plan, args);
                } */
            }
            return wfcase;
        }
        /*
         * Instantiate the Workflower class to launch the Workflow
         * Returns the WorkflowCase object.
         */
        private WorkflowCase runWorkflow(String wfName, SailPointContext context, ProvisioningPlan plan, Map<String,Object> args)
            throws GeneralException{
            WorkflowCase wfcase = null;
            Workflower workflower = new Workflower(context);
            Workflow workflow = context.getObject(Workflow.class, wfName);
            Map<String,Object> vars = new HashMap<String,Object>();
            if ( args != null )
                vars.putAll(args);
            vars.put("plan", plan);
            scrub(vars);
                WorkflowLaunch launch = workflower.launch(workflow, null, vars);
                wfcase = launch.getWorkflowCase();
            return wfcase;
        }
        //RETHINK THIS
        private void scrub(Map<String,Object> vars) {
            vars.remove("handler");
            vars.remove("plans");
            vars.remove("workflow");
            vars.remove("wfcase");
            vars.remove("wfcontext");
            vars.remove("step");
            vars.remove("sessionOwner");
        }
    /**
     * Method to send email notification to the grpMgmt IIQ Support team
     * Email will have error messages along with the provisioning plans
     * This method is called on each provisioning failure and so will send email on each retry
     * But error auditing will only be done if the maximum number of retries has been attempted.
     * @param context
     * @param grpMgmtIIQSupportEmail
     * @param errors
     * @param plans
     */
    public void sendGroupErrorEmailNotificationNAudit(SailPointContext context, String grpMgmtIIQSupportMailbox,List<Message> errors, List<ProvisioningPlan> plans,
            String groupDN,boolean auditError,String operation,String launcher,String sAMAccountName,String application){
        String  emailTemplate = "GrpMgmt-EmailTemplate-Group-Management-Error-Support-Notification";
        groupManagementLogger.debug("Sending email on error launching group Workflow...");
        List<String> planXmls = new ArrayList<String>();
        try {
            String grpMgmtNoReplyNotificationEmail = context.getConfiguration().getString("defaultEmailFromAddress");
            if(grpMgmtNoReplyNotificationEmail == null || grpMgmtNoReplyNotificationEmail.equals("")){
                grpMgmtNoReplyNotificationEmail = "noreply-sailpoint@grpMgmtmple.com";
            }
            // Get the set of Provisioning Plans which are part of this request and send their XML string representation to the error email
            // This will help Support in debugging
            for(ProvisioningPlan plan : plans){
                planXmls.add(plan.toXml());
            }
            EmailTemplate template = context.getObject(EmailTemplate.class, emailTemplate);
            Map mailArgs = new HashMap();
            mailArgs.put("groupDN",groupDN);
            mailArgs.put("errors",errors);
            mailArgs.put("planXmls",planXmls);
            mailArgs.put("operation",operation);
            mailArgs.put("application",application);
            EmailOptions options = new EmailOptions(grpMgmtIIQSupportMailbox, mailArgs);
            groupManagementLogger.debug("About to send error Email to:"+grpMgmtIIQSupportMailbox);
            options.setSendImmediate(true);
            options.setNoRetry(true);
           // Send an email to the requestor
            groupManagementLogger.debug("Sending email on error to:"+options.getTo());
            context.sendEmailNotification(template, options);
            groupManagementLogger.debug("Error notification email sent to:"+options.getTo());
            // If the max. number of retries has been attempted then audit the error
            if(auditError){
                AuditEvent event = new AuditEvent();
                String action = "grpMgmt_"+operation+"_group_error";
                event.setAction(action);
                event.setSource(launcher);
                event.setTarget(sAMAccountName);
                event.setApplication(application);
                event.setAttributeName("group");
                event.setAttributeValue(groupDN);
                // Add some searchable attributes here for debugging
                event.setString1("Error");  // This is the action
                if(errors.size()>0){
                    event.setString2(errors.get(0).toString());
                }
                Auditor.log(event);
                context.commitTransaction();
            }
        }
        catch (Exception e) {
            System.out.println("Error Sending Email: " + e);
       }
    }
    /**
     * Aggregate the newly created/edited group so all details in IIQ are latest
     * No need to wait till next aggregation to see the current changes made to reflect in IIQ
     * @param wfc
     * @return
     * @throws GeneralException
     */
    public String performTargetedAggregation(WorkflowContext wfc) throws GeneralException{
        String errorMessage;
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();
        String name = (String)Util.get(args,"name"); // The Group attribute name passed in from the Workflow. This is usually 'memberOf'
        String appId = (String)Util.get(args,"appId"); // Get Application Id of the LDAP system
        String value = (String)Util.get(args,"value");//Get the String native Identity value of the ManagedAttribute, which in this case is the Group's distinguishedName
        ManagedAttribute newObj = null;
        try{
        Application appObject = context.getObjectById(Application.class, appId);
        String appName = appObject.getName();
        String groupMgmtRefreshRule = (String) appObject.getAttributes().get("groupMgmtRefreshRule");
        // Print the connector type
        String appConnName = appObject.getConnector();
        groupManagementLogger.debug("Application uses connector " + appConnName);
        // Instantiate the Application connector for aggregation
        Connector appConnector = sailpoint.connector.ConnectorFactory.getConnector(appObject, null);
        if (null == appConnector) {
                 errorMessage = "Failed to construct an instance of connector [" + appConnName + "]";
             return errorMessage;
        }
        groupManagementLogger.debug("Connector instantiated, calling getObject() to read account details...");
        ResourceObject rObj = null;
        groupManagementLogger.debug("***Finding the account details.... for groupDN : " + value );
        rObj = appConnector.getObject("group", value, null);
        if ( rObj == null)
        {
            groupManagementLogger.debug("Resource Object is null");
        }
        groupManagementLogger.debug("Got object");
        // Prepare the attributes to be paased to the aggregation.
        // These are same as the options in the Account Aggregation Task
        // Note that it also sets up the call to the Group Refresh task
        Attributes argMap = new Attributes();
        argMap.put("accountGroupRefreshRule", groupMgmtRefreshRule);
        argMap.put("aggregationType", "group");
        argMap.put("applications", appName);
        argMap.put("checkDeleted", "true");
        argMap.put("descriptionAttribute","description");
        argMap.put("descriptionLocale", "en_US");
        // Prepare insytance of Aggregator for the specific connector
        Aggregator agg = new Aggregator(context, argMap);
        if (null == agg) {
           errorMessage = "Null Aggregator returned from constructor.  Unable to Aggregate!";
           groupManagementLogger.error(errorMessage);
           return errorMessage;
        }
        // Invoke the aggregation task by calling the aggregate() method.
        // Note: the aggregate() call may take several seconds to complete.
        groupManagementLogger.debug("Calling aggregate() method... ");
        newObj = agg.aggregateGroup(appObject, rObj);
        }catch(Exception ex){
            groupManagementLogger.debug("Error in targeted aggregation"+ex.getMessage());
            return "Aggregation Error";
        }
        groupManagementLogger.debug("aggregation complete.");
        return newObj.getId(); // Return the Object ID of the newly created/modified and aggregated group object
        // The maId returned will be passed by the Workflow to auditNotifyGroupRequest to get the latest details from IIQ
    }
    /**
     * Run processing rule
     * @param wfc
     * @return
     * @throws GeneralException
     */
    public Object runWorkflowRule(WorkflowContext wfc) throws GeneralException{
        SailPointContext context = wfc.getSailPointContext();
        Attributes<String,Object> args = wfc.getArguments();
        String ruleName = (String)Util.get(args,"ruleName");
        Map<String, Object> ruleArgs = new HashMap<String, Object>();
        ruleArgs.put("context", context);
        if(args != null) {
        	for(Map.Entry<String, Object> e: args.entrySet()) {
        		ruleArgs.put(e.getKey(), e.getValue());
        	}
        }
        Rule rule = context.getObject(Rule.class, ruleName);
        if(rule == null) {
        	throw new GeneralException("Rule " + ruleName + " not found");
        }
        return context.runRule(context.getObject(Rule.class, ruleName), ruleArgs);
    }
    /**
     *
     * @param wfc
     * @return
     * @throws GeneralException
     */
    public void setApplicationSpecificVariables(WorkflowContext wfc) throws GeneralException{
    	SailPointContext context = wfc.getSailPointContext();
    	Attributes<String,Object> args = wfc.getArguments();
    	String appId = (String)Util.get(args,"appId");
    	String flow = (String)Util.get(args,"flow");
    	Custom grpMgmtCustomConfig = context.getObject(Custom.class,"GrpMgmt-Custom-Config");
        Application app = context.getObject(Application.class, appId);
        if(app != null){
        	Attributes<String,Object> appAttrs = app.getAttributes();
        	if(appAttrs == null){
        		appAttrs = new Attributes<String,Object>();
        	}
			Map<String,String> checkOverrides = new HashMap<String,String>();
			if("GroupCreate".equals(flow)) {
				checkOverrides.put("approvalScheme", "grpMgmtCreateApprovalScheme");
				checkOverrides.put("groupAdminApprover", "grpMgmtAdminApprover");
				checkOverrides.put("preFormProcessingRule", "grpMgmtCreatePreFormRule");
				checkOverrides.put("postFormProcessingRule", "grpMgmtCreatePostFormRule");
				checkOverrides.put("preProvisioningProcessingRule", "grpMgmtCreatePreProvisionRule");
				checkOverrides.put("postProvisioningProcessingRule", "grpMgmtCreatePostProvisionRule");
			}
			else if("GroupEdit".equals(flow)) {
				checkOverrides.put("approvalScheme", "grpMgmtEditApprovalScheme");
				checkOverrides.put("groupAdminApprover", "grpMgmtAdminApprover");
				checkOverrides.put("preFormProcessingRule", "grpMgmtEditPreFormRule");
				checkOverrides.put("postFormProcessingRule", "grpMgmtEditPostFormRule");
				checkOverrides.put("preProvisioningProcessingRule", "grpMgmtEditPreProvisionRule");
				checkOverrides.put("postProvisioningProcessingRule", "grpMgmtEditPostProvisionRule");
			}
			else if("GroupDelete".equals(flow)) {
				checkOverrides.put("approvalScheme", "grpMgmtDeleteApprovalScheme");
				checkOverrides.put("groupAdminApprover", "grpMgmtAdminApprover");
				checkOverrides.put("preFormProcessingRule", "grpMgmtDeletePreFormRule");
				checkOverrides.put("postFormProcessingRule", "grpMgmtDeletePostFormRule");
				checkOverrides.put("preProvisioningProcessingRule", "grpMgmtDeletePreProvisionRule");
				checkOverrides.put("postProvisioningProcessingRule", "grpMgmtDeletePostProvisionRule");
			}
			else if("GroupDeprecate".equals(flow)) {
				checkOverrides.put("approvalScheme", "grpMgmtDeprecateApprovalScheme");
				checkOverrides.put("groupAdminApprover", "grpMgmtAdminApprover");
				checkOverrides.put("preFormProcessingRule", "grpMgmtDeprecatePreFormRule");
				checkOverrides.put("postFormProcessingRule", "grpMgmtDeprecatePostFormRule");
				checkOverrides.put("preProvisioningProcessingRule", "grpMgmtDeprecatePreProvisionRule");
				checkOverrides.put("postProvisioningProcessingRule", "grpMgmtDeprecatePostProvisionRule");
			}
			for(Map.Entry<String,String> e: checkOverrides.entrySet()){
				String variableName = e.getKey();
				String configurationKey = e.getValue();
				Object overrideVal = appAttrs.get(configurationKey);
				if(overrideVal == null){
					groupManagementLogger.debug("No application override for " + variableName);
					//No Application Override ... check global
					overrideVal = grpMgmtCustomConfig.get(configurationKey);
				}
				if(overrideVal != null){
					groupManagementLogger.debug("Override " + variableName + ": " + overrideVal);
					wfc.setVariable(variableName, overrideVal);
				}
			}
        }
    }
    /**
     * Audit the Group Request.
     * Also send a notification email with the created group's details to all interested parties
     * If not approved, it'll send rejection emails
     * If approved, but provisioning failed, it'll send failure emails
     * If approved and provisioning succeed, it'll send success emails
     *
     * @param wfc
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
	public void auditNotifyGroupRequest(WorkflowContext wfc) throws GeneralException{
        Attributes<String,Object> args = wfc.getArguments();
        SailPointContext context = wfc.getSailPointContext();
        String launcher = (String)Util.get(args,"launcher");
        String requesterDisplayName = (String)Util.get(args,"requesterDisplayName");
        String groupAdmin = (String)Util.get(args, "groupAdmin");
        Map<String, Object> groupModel = (Map<String, Object>)Util.get(args,"maModel");
        ChangeSummary changes = (ChangeSummary)Util.get(args,"changes");
        Map<String, Object> customEmailArgs = (Map<String, Object>)Util.get(args,"customEmailArgs");
        String maId = (String)Util.get(args,"maId"); // The ManagedAttribute ID passed from Workflow
        ManagedAttribute managedAttribute = null;
        if(maId != null) {
        	managedAttribute = context.getObject(ManagedAttribute.class, maId);
        }
        String operation = (String)Util.get(args,"operation"); // Flow of the request
        String approverDecision = (String)Util.get(args, "approverDecision");
        String approverComments = (String)Util.get(args, "approverComments");
        String groupProvisioningErrors = (String)Util.get(args, "groupProvisioningErrors");
        String groupProvisioningStatus = (String)Util.get(args, "groupProvisioningStatus");
        String rejectNotificationScheme = (String)Util.get(args, "rejectNotificationScheme");
        String rejectNotificationTemplate = (String)Util.get(args, "rejectNotificationTemplate");
        String successNotificationScheme = (String)Util.get(args, "successNotificationScheme");
        String successNotificationTemplate = (String)Util.get(args, "successNotificationTemplate");
        String failureNotificationScheme = (String)Util.get(args, "failureNotificationScheme");
        String failureNotificationTemplate = (String)Util.get(args, "failureNotificationTemplate");
        Map<String, Object> mailArgs = new HashMap<String, Object>();
		String groupName = (String)MapUtil.get(groupModel, "sys.displayName");
		String groupValue = (String)MapUtil.get(groupModel, "sys.nativeIdentity");
		String type = (String)MapUtil.get(groupModel, "sys.type");
		if(managedAttribute != null) {
			groupName = managedAttribute.getDisplayableName();
			groupValue = managedAttribute.getValue();
			type = managedAttribute.getType();
		}
		//Populate all the dynamic text attributes for the Email notification
		mailArgs.put("operation", operation);
		mailArgs.put("groupValue", groupValue);
		mailArgs.put("groupName", groupName);
		mailArgs.put("groupType", type);
		mailArgs.put("requesterDisplayName", requesterDisplayName);
		mailArgs.put("launcher", launcher);
		mailArgs.put("groupModel", groupModel);
		mailArgs.put("changes", changes);
        String status = null;
		String failureNotes = null;
        if(!Util.nullSafeCaseInsensitiveEq("APPROVE", approverDecision)) {
        	status = "Rejected";
        	failureNotes = approverComments;
    		mailArgs.put("status", status);
    		mailArgs.put("failureNotes", failureNotes);
        	//Request Rejected
        	sendGroupRequestEmails(context, launcher, groupAdmin, managedAttribute, groupModel,
        			rejectNotificationScheme, rejectNotificationTemplate, mailArgs);
        }
        else if(!Util.nullSafeCaseInsensitiveEq("Success", groupProvisioningStatus)) {
        	status = "Failed";
        	failureNotes = groupProvisioningErrors;
    		mailArgs.put("status", status);
    		mailArgs.put("failureNotes", failureNotes);
        	//Request Approved and Provisioning Failed
        	sendGroupRequestEmails(context, launcher, groupAdmin, managedAttribute, groupModel,
        			failureNotificationScheme, failureNotificationTemplate, mailArgs);
        }
        else {
        	status = "Complete";
    		mailArgs.put("status", status);
        	failureNotes = groupProvisioningErrors;
        	//Request Approved and Provisioning Succeeded
        	sendGroupRequestEmails(context, launcher, groupAdmin, managedAttribute, groupModel,
        			successNotificationScheme, successNotificationTemplate, mailArgs);
        }
        // auditConfig is the setting stored in the Audit Configuration object in IIQ. It may be enabled or disabled
        // action is the granular level operation being attempted and is set as part of the audit record
        String auditAction = "grpMgmt_audit";
        if (Auditor.isEnabled(auditAction)) {
            AuditEvent event = new AuditEvent();
            groupManagementLogger.debug("action:" + auditAction);
            String appName = (String)MapUtil.get(groupModel, "sys.application.name");
    		String comment = (String)MapUtil.get(groupModel, "displayOnly.comment");
    		if(managedAttribute != null) {
    			groupName = managedAttribute.getDisplayableName();
    			groupValue = managedAttribute.getValue();
    			type = managedAttribute.getType();
    		}
            event.setAction(auditAction);
            event.setSource(launcher);
            event.setTarget(groupName);
            event.setApplication(appName);
            event.setAttributeName(type);
            event.setAttributeValue(groupValue);
            // Add some searchable attributes here for debugging
            event.setString1(operation); // This is the action
            event.setString2(comment); // This is the comment from the requester
            event.setString3(status);  // This is the status
            event.setString4(failureNotes); // This is any errors from provisioning or the approval comments if rejected
            event.setAttribute("groupModel", groupModel);
            if(changes != null) {
            	List<Difference> diffs = changes.getDifferences();
            	if(diffs != null) {
            		for(Difference diff: diffs) {
            			String diffAttr = diff.getDisplayName();
            			if(diffAttr == null) {
            				diffAttr = diff.getAttribute();
            			}
            			if(diff.isMulti()) {
            				/*
            				 * Add attributes for add values and removed
            				 */
            				String diffAdded = diff.getAddedValuesCsv();
            				if(Util.isNotNullOrEmpty(diffAdded)) {
            					event.setAttribute(diffAttr + "::added", diffAdded);
            				}
            				String diffRemoved = diff.getRemovedValuesCsv();
            				if(Util.isNotNullOrEmpty(diffRemoved)) {
            					event.setAttribute(diffAttr + "::removed", diffRemoved);
            				}
            			}
            			else {
            				String newVal = diff.getNewValue();
            				event.setAttribute(diffAttr, newVal);
            			}
            		}
            	}
            }
            Auditor.log(event);
            context.commitTransaction();
       }
    }
    private List<String> getEffectiveEmails(SailPointContext context, String identityNameOrId) {
    	List<String> emails = null;
    	Identity identity = null;
    	if(identityNameOrId != null) {
			try {
				identity = context.getObject(Identity.class, identityNameOrId);
			} catch (GeneralException e) {
				groupManagementLogger.warn("Failed to get Identity in getEffectiveEmails: " + e.getMessage());
			}
			emails = getIdentityEffectiveEmails(context, identity);
    	}
    	return emails;
    }
    private List<String> getIdentityEffectiveEmails(SailPointContext context, Identity identity) {
    	if(groupManagementLogger.isTraceEnabled()) {
    		groupManagementLogger.trace("Enter getIdentityEffectiveEmails: " + identity);
    	}
    	List<String> emails = null;
    	if(identity != null) {
    		try {
				emails = ObjectUtil.getEffectiveEmails(context, identity);
			} catch (GeneralException e) {
				groupManagementLogger.warn("Failed to get emails from identity in getIdentityEffectiveEmails: " + e.getMessage());
			}
    	}
    	if(groupManagementLogger.isTraceEnabled()) {
    		groupManagementLogger.trace("Exit getIdentityEffectiveEmails: " + emails);
    	}
    	return emails;
    }
    @SuppressWarnings("unchecked")
	private void sendGroupRequestEmails(SailPointContext context, String launcher, String admin, ManagedAttribute managedAttribute,
			Map<String, Object> groupModel,	String notificationScheme, String templateName, Map<String, Object> mailArgs) throws GeneralException {
		groupManagementLogger.trace("Enter sendGroupRequestEmails");
		if(notificationScheme == null || "none".equals(notificationScheme)) {
			groupManagementLogger.trace("Exit sendGroupRequestEmails: scheme is \"none\"");
			return;
		}
		// Get the latest ManagedAttribute object from IIQ. Post targeted aggregation this will be latest and should reflect all changes
		groupManagementLogger.debug("Template: " + templateName);
		EmailTemplate template = context.getObject(EmailTemplate.class, templateName);
		if(template == null) {
			groupManagementLogger.warn("Cannot send email " + templateName + ": No Object Found");
			return;
		}
		Set<String> toEmailSet = new HashSet<String>();
		List<String> toUserTypes = Util.csvToList(notificationScheme);
		for(String userType: toUserTypes) {
			if("requester".equals(userType)) {
				List<String> requesterEmails = getEffectiveEmails(context, launcher);
				if(requesterEmails != null) {
					toEmailSet.addAll(requesterEmails);
				}
			}
			else if("owner".equals(userType)) {
				List<String> groupOwnerIds = Util.asList(MapUtil.get(groupModel, "sys.owner"));
				if(groupOwnerIds != null) {
					for(String id: groupOwnerIds) {
						List<String> ownerEmails = getEffectiveEmails(context, id);
						if(ownerEmails != null) {
							toEmailSet.addAll(ownerEmails);
						}
					}
				}
			}
			else if("oldOwner".equals(userType) && managedAttribute != null) {
				Identity oldOwner = managedAttribute.getOwner();
				if(oldOwner != null) {
					List<String> ownerEmails = getIdentityEffectiveEmails(context, oldOwner);
					if(ownerEmails != null) {
						toEmailSet.addAll(ownerEmails);
					}
				}
			}
			else if("admin".equals(userType) && managedAttribute != null) {
				List<String> requesterEmails = getEffectiveEmails(context, admin);
				if(requesterEmails != null) {
					toEmailSet.addAll(requesterEmails);
				}
			}
		}
		if(toEmailSet.isEmpty()) {
			groupManagementLogger.warn("NotificationScheme " + notificationScheme + " resulted in no emails");
			return;
		}
		if(mailArgs == null) {
			mailArgs = new HashMap<String, Object>();
			String groupName = (String)MapUtil.get(groupModel, "sys.displayName");
			String groupValue = (String)MapUtil.get(groupModel, "sys.value");
			String type = (String)MapUtil.get(groupModel, "sys.value");
			if(managedAttribute != null) {
				groupName = managedAttribute.getDisplayableName();
				groupValue = managedAttribute.getValue();
				type = managedAttribute.getType();
			}
			mailArgs = new HashMap<String, Object>();
			mailArgs.put("groupValue", groupValue);
			mailArgs.put("groupName", groupName);
			mailArgs.put("groupType", type);
	        mailArgs.put("requesterDisplayName", launcher);
			mailArgs.put("groupModel", groupModel);
		}
		//Populate all the dynamic text attributes for the Email notification
		String emailTo = Util.setToCsv(toEmailSet);
		EmailOptions options = new EmailOptions(emailTo, mailArgs);
		options.setSendImmediate(true);
		options.setNoRetry(true); // Do not attempt a retry of the Email. This is to avoid duplicate emails in case of multiple request servers.
		//Send an email to the requestor
		groupManagementLogger.debug("Sending email to:"+options.getTo());
		groupManagementLogger.debug(context);
		groupManagementLogger.debug(template);
		groupManagementLogger.debug(options);
		context.sendEmailNotification(template, options);
		groupManagementLogger.debug("Group Create request completion notification email sent to:"+options.getTo());
	}
    /*
    * Builds the ObjectRequest provisioning plan from the change summary to revert the changes
    * The plan will be a modify to undo changes for edit
    * If it's a create, the plan will be to delete the entitlement
    * Then provisions the request with a try catch to avoid any additional errors
    * @param wfc
    * @return
    * @throws GeneralException
    */
    @SuppressWarnings("unchecked")
    public void revertEntitlementChanges(WorkflowContext wfc)
		   throws GeneralException {
    	SailPointContext context = wfc.getSailPointContext();
    	// Retrieve the Workflow arguments
	    Attributes<String,Object> args = wfc.getArguments();
	    // Get the ManagedAttribute Model stored in the Workflow
	    Map<String,Object> groupModel = (Map<String,Object>)Util.get(args, "maModel");
	    ChangeSummary changeSummary = (ChangeSummary)Util.get(args, "changes");
	    if ( groupModel == null ) {
	    	throw new GeneralException("ManagedAttributebuildPlansFromManagedAttributeModel map model was null.");
	    }
	    if ( changeSummary == null ) {
	    	groupManagementLogger.warn("Nothing to revert. No change summary");
	    	return;
	    }
	    String appName = (String)MapUtil.get(groupModel, "sys.application.name");
	    String groupValue = (String)MapUtil.get(groupModel, "sys.nativeIdentity");
		String type = (String)MapUtil.get(groupModel, "sys.type");
		String instance = (String)MapUtil.get(groupModel, "sys.instance");
		boolean hasRevert = false;
		ProvisioningPlan plan = new ProvisioningPlan();
		ObjectRequest req = new ObjectRequest();
		req.setType(type);
		req.setApplication(appName);
		req.setNativeIdentity(groupValue);
		req.setInstance(instance);
		plan.add(req);
		List<String> unsupportedReverts = new ArrayList<String>();
		unsupportedReverts.add("owner");
		unsupportedReverts.add("identityMembership");
		unsupportedReverts.add("inheritedGroups");
		unsupportedReverts.add("Application");
		Map<String, String> sysAttributeConversions = new HashMap<String, String>();
		sysAttributeConversions.put("Owner", "sysOwner");
		sysAttributeConversions.put("Attribute", "sysAttribute");
		sysAttributeConversions.put("Display Name", "sysDisplayName");
		sysAttributeConversions.put("Requestable", "sysRequestable");
		sysAttributeConversions.put("Type", "sysManagedAttributeType");
		if(changeSummary.isCreate()) {
			req.setOp(ProvisioningPlan.ObjectOperation.Delete);
			hasRevert = true;
		}
		else {
			ManagedAttribute ma = null;
			String maId = (String)MapUtil.get(groupModel, "sys.id");
			if(maId != null) {
				ma = context.getObjectById(ManagedAttribute.class, maId);
			}
			req.setOp(ProvisioningPlan.ObjectOperation.Modify);
			List<Difference> diffs = changeSummary.getDifferences();
			if(diffs != null) {
				for(Difference diff: diffs) {
					String attrName = diff.getAttribute();
					if(unsupportedReverts.contains(attrName)) {
						groupManagementLogger.warn("Revert currently unsupported for " + attrName + ". Need to handle in workflow custom hooks");
						continue;
					}
					if(sysAttributeConversions.containsKey(attrName)) {
						attrName = sysAttributeConversions.get(attrName);
					}
					if(attrName.startsWith("Description (")) {
						String localeString = attrName.substring(attrName.indexOf("(") + 1, attrName.indexOf(")"));
						Map<String, String> newDescs = new HashMap<String, String>();
						if(ma != null) {
							Map<String, String> oldDescs = ma.getDescriptions();
				        	newDescs.putAll(oldDescs);
						}
						String oldValue = diff.getOldValue();
						if(oldValue == null && newDescs.containsKey(localeString)) {
							newDescs.remove(localeString);
						}
						else if(oldValue != null) {
							newDescs.put(localeString, oldValue);
						}
						req.add(new AttributeRequest("sysDescriptions", ProvisioningPlan.Operation.Set, newDescs));
					}
					else if(diff.isMulti()) {
						List<String> addedVals = diff.getAddedValues();
						if(addedVals != null){
							for(String s: addedVals) {
								req.add(new AttributeRequest(attrName, ProvisioningPlan.Operation.Remove, s));
							}
							hasRevert = true;
						}
						List<String> removedVals = diff.getRemovedValues();
						if(removedVals != null){
							for(String s: removedVals) {
								req.add(new AttributeRequest(attrName, ProvisioningPlan.Operation.Add, s));
							}
							hasRevert = true;
						}
					}
					else {
						Object oldValue = diff.getOldValue();
						req.add(new AttributeRequest(attrName, ProvisioningPlan.Operation.Set, oldValue));
						hasRevert = true;
					}
				}
			}
		}
		if(hasRevert) {
			if(groupManagementLogger.isDebugEnabled()) {
				groupManagementLogger.debug("Plan for revert: " + plan.toXml());
			}
			Attributes<String, Object> provArgs = new Attributes<String, Object>();
			provArgs.put("source", "GrpMgmtFailureRevert");
			Provisioner p = new Provisioner(context, provArgs);
	        ProvisioningProject project = p.compile(plan, provArgs);
			if(project != null) {
				try{
					p.execute(project);
				}
				catch(Exception e) {
					groupManagementLogger.warn(e);
					//Expect error since we had error in initial provisioning
				}
			}
		}
	}
}
