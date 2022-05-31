package Coding;

package Coding;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//import com.sailpoint.sail4j.annotation.IgnoredBySailPointRule;
//import com.sailpoint.sail4j.annotation.SailPointRule;
//import com.sailpoint.sail4j.annotation.SailPointRuleMainBody;

import sailpoint.api.Describer;
import sailpoint.api.RoleChangeAnalyzer;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelectorRules;
import sailpoint.object.Application;
import sailpoint.object.ApplicationAccountSelectorRule;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CompoundFilter;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.Rule;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

/**
 *  A custom rule library for importing roles from a csv file.
 *
 *
 *  Role Importer consumes a .csv file with specfic columns of role data.  Below
 *  is an example format to be used
 *
 *  Available Operaions on Import (note that these are case insensitive)
 *  --------------------------------------------------------------------
 *    Add Role            - Creates a new role
 *    Delete Role         - Deletes an existing role
 *    Add Inheritance     - Adds a parent to an existing role
 *    Add Permitted       - Adds a permitted role to an existing role
 *    Add Required        - Adds a required role to an existing role
 *    Add Matchlist       - Adds a matching list to an existing business type role
 *    Add Profile         - Adds a profile with entitlements to an existing IT type role
 *    Update Profile      - Updates existing entitlements with new entitlements for an existing IT type role
 *    Update Name         - Updates the name and/or display name of a role
 *    Add Metadata        - Updates any attribute of a role
 *    Add Description     - Adds a description (based on a locale) to a role (or updates an existing description)
 *    Add AssignmentRule  - Adds an assignment rule to a role
 *    Add AccountSelector - Adds a an account selector (or application account selector) to a role
 *
 
@SailPointRule(name = "GovConnect-Rule-RoleImporter", fileName = "GovConnect-Rule-RoleImporter.xml", notUpdateIfExist = true,
	description = "GovConnect role importer consumes a CSV that describes roles to be created or updated in the IdentityIQ system.")
	*/
public class GovConnectRoleImporterRule {
	
	//@IgnoredBySailPointRule
	private SailPointContext context;
	
	//
	// Input Arguments
	//
	public static String ARG_ROLE_FILENAME = "roleFile";
	public static String ARG_USE_HEADER = "useHeader";
	public static String ARG_UPDATE_EXISTING = "updateExisting";
	public static String ARG_OR_PROFILES = "orProfiles";
	public static String ARG_FILE_ENCODING = "fileEncoding";
	public static String ARG_NO_ROLE_PROPAGATION = "noRolePropagation";

	private static String QUOTE = "\"";

	private String _roleFile = "";
	private String _fileEncoding = null;
	private boolean _useHeader = true;
	private boolean _updateExisting = true;
	private boolean _orProfiles = false;

	//
	// Return Arguments
	//
	public static String RET_LINES = "lines";
	public static String RET_ROLES_CREATED = "rolesCreated";
	public static String RET_ROLES_UPDATED = "rolesUpdated";
	public static String RET_ROLES_DELETED = "rolesDeleted";
	public static String RET_PROFILES_CREATED = "profilesCreated";

    
	// Counters used to hold return arguments
	private int _lines = 0;
	private int _rolesCreated = 0;
	private int _rolesUpdated = 0;
	private int _rolesDeleted = 0;
	private int _profilesCreated = 0;

    private static int ROLE_NAME_SIZE = 128;
    private static int ROLE_DESC_SIZE = 1024;

    private static Logger _log = Logger.getLogger("GovConnect.role.importer");

	private static RFC4180LineParser _parser = new RFC4180LineParser(',');
	
	private TaskResult result;
	private Attributes args;

	private String getNotNullVal(String oldVal)	{
		if(oldVal == null ) {
			return "";
		} else {
			return oldVal;
		}
	}
	
 	private void addErrorMessage(String message) {
        Message msg = new Message(Message.Type.Error, new Exception(message));
        result.addMessage(msg);
    }

	//
	// processLines will parse each line in the input file and decide how to handle
	// the operation and corresponding operation 'details'
	//
	// Example: Let's say header looks like:
	//
	//      op, type, name, description, owner, parent
	//      Add Role,Organization,Role Name,Role Description,spadmin,Role Parent
	//
	// This should create a new role called 'Role Name' with the parent role, 'Role Parent'
	//
	private int processLines(SailPointContext ctx, RFC4180LineIterator lines, List errorRecordList) throws Exception {
		boolean done = false;
		int linesRead = 0;
		String line = null;

		while (!done) {
			line = lines.readLine();

			// If the current line is null or if the line starts with a # (comment) or size
			// of 0, then continue to the next line.
			if (line == null) {
				done = true;
				continue;
			} else if (line.startsWith("#") || line.length() < 1) {
				continue;
			} else {
				linesRead++;
			}

			List tokens = _parser.parseLine(line);

			processLine(ctx, tokens, errorRecordList);

		}
		ctx.commitTransaction();

		return linesRead;
	}

	public void processLine(SailPointContext ctx, List tokens, List errorRecordList) throws GeneralException, JSONException {
		// The first token in each line represents the operation for that line
		String op = ((String)tokens.get(0)).toUpperCase();

		switch(op) {
		case "ADD ROLE":
			addRole(tokens, errorRecordList);
			break;
		case "DELETE ROLE":
			deleteRole(tokens, errorRecordList);
			break;
		case "ADD INHERITANCE":
			addInheritance(tokens, errorRecordList);
			break;
		case "ADD PERMITTED":
			addPermitted(tokens, errorRecordList);
			break;
		case "ADD REQUIRED":
			addRequired(tokens, errorRecordList);
			break;
		case "ADD MATCHLIST":
			addMatchlist(tokens, errorRecordList);
			break;
		case "ADD PROFILE":
			addProfile(false, tokens, errorRecordList);
			break;
		case "UPDATE PROFILE":
			addProfile(true, tokens, errorRecordList);
			break;
		case "UPDATE NAME":
			updateName(tokens, errorRecordList);
			break;
		case "ADD METADATA":
			addMetadata(tokens, errorRecordList);
			break;
		case "ADD DESCRIPTION":
			addDescription(tokens, errorRecordList);
			break;
		case "ADD ASSIGNMENTRULE":
			addAssignmentRule(tokens, errorRecordList);
			break;
		case "ADD ACCOUNTSELECTOR":
			addAccountSelector(tokens, errorRecordList);
			break;
		}

	}
	
	private IdentitySelector generateMatchList(SailPointContext ctx, boolean andOp, IdentitySelector existingSelector, List identityAttrs, List identityValues, List applications)
	{
		IdentitySelector assignmentRule = new IdentitySelector();

		MatchExpression matcher = new MatchExpression();

		_log.debug("RoleImporter.generateMatchList:  AND OP is " + andOp);
		matcher.setAnd(andOp);

		// do we just have one app to set for all the terms
		Application singleApplication=null;
		if(applications!=null && applications.size()==1) {
			try {
				singleApplication=(Application)ctx.getObjectByName(Application.class, (String)applications.get(0));
			} catch (GeneralException ge) {
				_log.debug("Application "+applications.get(0)+" not found. Ignoring..");
			}
		}

		// Build an assignment rule using the identity attributes
		for (int i = 0; i < identityAttrs.size(); i++) {
			String identityAttr = (String)identityAttrs.get(i);
			String identityValue = (String)identityValues.get(i);
			MatchTerm term = new MatchTerm();
			term.setName(identityAttr);
			term.setValue(identityValue);
			if(applications != null) {

				if(singleApplication!=null) {
					term.setApplication(singleApplication);
				} else {
					String appName=(String)applications.get(i);
					try{
						Application app=ctx.getObjectByName(Application.class, appName);
						term.setApplication(app);
					} catch (GeneralException ge) {
						_log.debug("Application "+appName+" not found. Ignoring..");
					}
				}

			}
			matcher.addTerm(term);
		}

		// Now, if the existingSelector is not null, we need to do an 'or' of this matchexpression
		// and the existing matchexpressions
		//
		// we are also assuming that any existing selector is a matchlist

		if(existingSelector!=null) {
			MatchExpression previous=existingSelector.getMatchExpression();

			// Recreate the current MatchExpression as a container MatchTerm
			MatchTerm currentAsTerm=new MatchTerm();
			currentAsTerm.setAnd(andOp);
			currentAsTerm.setContainer(true);
			currentAsTerm.setChildren(matcher.getTerms());
			
			if (!previous.isAnd()) {
				// If existing selector MatchExpression is OR, we just need to append the new MatchExpression
				previous.addTerm(currentAsTerm);
				assignmentRule.setMatchExpression(previous);
			} else {
				// Recreate the previous MatchExpression as a container MatchTerm
				MatchTerm previousAsTerm=new MatchTerm();
				previousAsTerm.setAnd(previous.isAnd());
				previousAsTerm.setContainer(true);
				previousAsTerm.setChildren(previous.getTerms());
				
				matcher=new MatchExpression();
				matcher.setAnd(false);
			
				matcher.addTerm(previousAsTerm);
				matcher.addTerm(currentAsTerm);
				assignmentRule.setMatchExpression(matcher);
			}
		} else {
			assignmentRule.setMatchExpression(matcher);
		}

		return assignmentRule;
	}
	
	private void addMatchlist(List tokens, List errorRecordList) throws GeneralException {
		//  Add MATCHLIST - Adds a matchlist to an existing business role
		//  --------------------------------------------
		//    Role Name            - Name of an existing role
		//    Match List Format    - Type of Matchlist.  Currently only
		//                            IdentityMatchList and Filter are supported.
		//    Match List Options
		//         IdnetityMatchList  - Accepts 3 inputs
		//             AND_OP         - true - ANDs attribute/values together
		//                            - false - ORs attribute/values together
		//             Attributes     - ordered csv list of attributes to match on
		//             Values         - ordered csv list of values to match on
		//  --------------------------------------------

		String roleName = (String)tokens.get(1);
		String matchlistType = (String)tokens.get(2);

		_log.debug("ADD MATCHLIST,"+roleName+","+matchlistType);

		Bundle role = context.getObjectByName(Bundle.class, roleName);

		if (role == null) {
			_log.error("Role not found: " + roleName);
			errorRecordList.add(tokens);
			return;
		}

		if ("IDENTITYMATCHLIST".equalsIgnoreCase(matchlistType)) {
			String mlAnd = (String)tokens.get(3);
			String mlAttrs = (String)tokens.get(4);
			String mlValues = (String)tokens.get(5);
			String mlApplication = (String)tokens.get(6);

			if (mlAnd == null || mlAttrs == null || mlValues == null)
			{
				_log.error("Problem with matchlist while parsing role " + roleName);
				errorRecordList.add(tokens);
				return;
			}

			boolean andOp = ("AND".equalsIgnoreCase(mlAnd));

			List mlAttrsList = _parser.parseLine(mlAttrs);
			List mlValuesList = _parser.parseLine(mlValues);
			List mlApplicationList = _parser.parseLine(mlApplication);

			if (mlAttrsList.size() != mlValuesList.size()
					// Check we have 0, 1 or same number of applications
					|| (mlApplicationList!=null && mlApplicationList.size()>1 && mlAttrsList.size() != mlApplicationList.size() ))
			{
				_log.error("Inconsistent sizes of matchlist attrs/values/applications while parsing role " + roleName);
				errorRecordList.add(tokens);
				return;
			}
			Application matchApplication = null;
			if(mlApplication != null && mlApplication.length() > 0) {
				matchApplication = context.getObjectByName(Application.class, mlApplication);
			}

			IdentitySelector existingSelector = role.getSelector();

			IdentitySelector selector = generateMatchList(context, andOp, existingSelector, mlAttrsList, mlValuesList, mlApplicationList);

			role.setSelector(selector);
		} else if ("FILTER".equalsIgnoreCase(matchlistType)) {

			String filterStr = (String)tokens.get(3);

			if (filterStr == null)
			{
				_log.error("Problem with filter while parsing role " + roleName);
				errorRecordList.add(tokens);
				return;
			}

			CompoundFilter f = new CompoundFilter();
			try {
				f.update(context, filterStr);
			}
			catch (RuntimeException e) {
				_log.debug("exception: " + e);
				_log.error("Problem parsing filter on role: " + roleName);
				errorRecordList.add(tokens);
				return;
			}

			IdentitySelector selector = new IdentitySelector();

			selector.setFilter(f);

			role.setSelector(selector);
		}

		context.saveObject(role);
		context.commitTransaction();
		context.decache(role);
	}

	private void processRoleChanges(Bundle newRole) throws GeneralException {

		newRole.setDisabled(false);
		//calculate the role differences before committing the Role in database
		boolean isRolePropEnabled = Util.otob(context.getConfiguration().getBoolean(Configuration.ALLOW_ROLE_PROPAGATION));
		
		// When true it means to disable role change propagation.
		boolean noRolePropagation = args.getBoolean(ARG_NO_ROLE_PROPAGATION);
		isRolePropEnabled &= !noRolePropagation;
		
		List roleChangeEvents = null;
		if (isRolePropEnabled) {
			roleChangeEvents = new RoleChangeAnalyzer(context).calculateRoleChanges(newRole);
		}
		//do not trust the context when we need to commit
		context.decache();
		
		context.saveObject(newRole);
		context.commitTransaction();

		if (isRolePropEnabled)
			saveRoleChangeEvents(roleChangeEvents);
	}
	
	//saves role change events in the database
	private void saveRoleChangeEvents(List<RoleChangeEvent> eventList) throws GeneralException {
		for(RoleChangeEvent roleChangeEvt: Util.iterate(eventList)) {
			_log.debug("Role Change Event: " + roleChangeEvt.toXml());
			context.saveObject(roleChangeEvt);
		}
		context.commitTransaction();
	}



	/**
	 * Create the profile filter from a string
	 *
	 * EntitlementValues will look like:
	 *   value1,value2,value3,...,valueN
	 *
	 * Creates:
	 *    groupmbr.containsAll({"ClaimsAdmin"})
	 */
	private String createProfileFilter(String attrName, List<String> entitlementValues) {
		String filter = attrName + ".containsAllIgnoreCase({";
		String comma = "";

		for (String entValue : entitlementValues) {
			filter += comma + QUOTE + entValue + QUOTE;

			comma = ",";
		}


		filter += "})";

		return filter;
	}

	/**
	 * Get the input File Stream.
	 */
	private InputStream getFileStream() throws Exception {
		InputStream stream = null;

		if (_roleFile == null) {
			throw new GeneralException("Filename cannot be null.");
		}
		try {
			File file = new File(_roleFile);
			if (!file.exists()) {
				// sniff the file see if its relative if it is
				// see if we can append sphome to find it
				if (!file.isAbsolute()) {
					String appHome = getAppHome();
					if (appHome != null) {
						file = new File(appHome + File.separator + _roleFile);
						if (!file.exists()) {
							file = new File(_roleFile);
						}
					}
				}
			}
			// This will throw an exception if the file cannot be found
			stream = new BufferedInputStream(new FileInputStream(file));
		} catch (Exception e) {
			throw new GeneralException(e);
		}

		return stream;
	}


	/**
	 * Try to get the app home to be smart about locating relative paths.
	 */
	private String getAppHome() {
		String home = null;
		try {
			home = Util.getApplicationHome();
		} catch (Exception e) {
			_log.error("Unable to find application home.");
		}
		return home;
	}

	private void setDescription(Bundle obj, String desc, String locale) 
		throws GeneralException {
		Describer describer = new Describer(obj);

		//Localizer localizer = new Localizer(_ctx, obj.getId());

		// Use default language of IIQ instance when no locale specified
		if (null == locale) {
			describer.setDefaultDescription(context, desc);
		} else {
			Map descriptions = new HashMap();
			descriptions.put(locale, desc);
			describer.addDescriptions(descriptions); 
		}

		describer.saveLocalizedAttributes(context);
		context.commitTransaction();

		return;
	}


	private void addRole(List tokens, List errorRecordList) throws GeneralException {

		//  Add Role - Format for New Roles
		//  ----------------------------------------------------
		//    Role Type           - Type of role.  Needs to match a valid Role Type
		//    Role Name           - Name of role.
		//    Role Display Name   - Display name of role (optional)
		//    Role Description    - Description of role.
		//    Role Owner          - Owner of role.  Needs to match a valid Identity
		//    Role Parent         - Parent of role.  Needs to match an existing role name
		//
		//
		//    Profile Application - only for 'Entitlement' roles
		//    Profile Attributes  - only for 'Entitlement' roles
		//    Profile Entitlement - only for 'Entitlement' roles
		//
		//  ----------------------------------------------------
		String roleType        = (String)tokens.get(1);
		String roleName        = (String)tokens.get(2);
		String roleDisplayName = (String)tokens.get(3);
		String roleDesc        = (String)tokens.get(4);
		String roleOwner       = (String)tokens.get(5);
		String roleParent      = (String)tokens.get(6);
		
		_log.debug("ADD ROLE,"+roleType+","+roleName+","+roleDesc+","+roleOwner+","+roleParent);
		
		// validate all the mandatory information
		if (!isValidRoleType(roleType, errorRecordList, tokens) 
			|| !isValidStringValue("Role Name", roleName, ROLE_NAME_SIZE, true, errorRecordList, tokens)
			|| !isValidStringValue("Role Display Name", roleDisplayName, ROLE_NAME_SIZE, false, errorRecordList, tokens)
			|| !isValidStringValue("Role Description", roleDesc, ROLE_DESC_SIZE, false, errorRecordList, tokens)
			|| !isValidIdentity("Role Owner", roleOwner, true, errorRecordList, tokens)
			|| !isValidStringValue("Role Parent", roleParent, ROLE_NAME_SIZE, false, errorRecordList, tokens)) {
			_log.error("Issue found validating 'Add Role' parameters. Skipping record.");
			return;
		}

		// First check to see if the role already exists.  If so, then check flag for
		// whether we update or skip
		//
		Bundle role = context.getObjectByName(Bundle.class, roleName);

		if (role != null) {
			_log.info("Role name in import file matches existing role name: " + roleName);
			if (_updateExisting) {
				_log.debug("...updateExisting flag is on; role will be updated.");
			} else {
				_log.debug("...updateExisting flag is off; role will be skipped.");
				return;
			}
		} else {
			// Create a new role
			role = new Bundle();
		}

		// Set the role name, description and type
		role.setName(roleName);
		role.setType(roleType);

		if (null != roleDisplayName) {
			role.setDisplayName(roleDisplayName);
		}

		// Get the Identity of the owner.  If it's not found, default to spadmin
		Identity owner = context.getObjectByName(Identity.class, roleOwner);
		if (owner != null) {
			role.setOwner(owner);
		} else {
			// this was already validated, so let's just return if we hit this condition
			_log.error("Cannot find owner [" + roleOwner + "].");
			return;
		}

		// Get the parent role.  If it's not found, create a stub so that we can continue.
		// The parent role is optional.  If it's not set, then no parent will be set on the role.
		//
		Bundle parent = context.getObjectByName(Bundle.class, roleParent);
		if (parent != null) {
			role.addInheritance(parent);
		} else {
			// Create stub...
			if (null != roleParent) {
				parent = new Bundle();
				parent.setName(roleParent);
				parent.setType("organizational");
				context.saveObject(parent);
				context.commitTransaction();
				context.decache(parent);
				_rolesCreated++;
			}
			role.addInheritance(parent);
		}

		// Special Handling for Business role types.
		if ("BUSINESS".equalsIgnoreCase(roleType)) {
			if (tokens.size() > 12) {

				// TODO HANDLE ALL THE EXTENDED ATTRIBUTES AND VALIDATE THEM
				
				// custom extension attribute handling can be here:
				// isJoiner - used to exclude Birthright Entitlements, true or false
				// formappName - nput Form For Requestor / Application
				// roleBusApprovers - Enter a comma separated  Cube Ids or WorkGroups
				// roleBusApprovalRule - This rule can be used to override 1st Level Business Approvers
				// additionalRoleBusApprovers - Enter a comma separated  Cube Ids or WorkGroups
				// additionalRoleBusApprovalRule - This rule can be used to override 2nd Level Business Approvers
				// acceleratorPackEnabled - Used to make sure access request is redirected to Accelerator Pack Wrapper Workflow
				// appName - Either Single or Comma separated Logical Business Aplications(Shared Databases, Shared Groups, etc)
				// rolePrivileged - Privileged Role
				// apaccountType - Comma Separated Privileged Role Types
				
				String isJoiner = getNotNullVal((String) tokens.get(7));
				String formappName = getNotNullVal((String) tokens.get(8));
				String roleBusApprovers = getNotNullVal((String) tokens.get(9));
				String roleBusApprovalRule = getNotNullVal((String) tokens.get(10));
				String additionalRoleBusApprovers = getNotNullVal((String) tokens.get(11));
				String additionalRoleBusApprovalRule = getNotNullVal((String) tokens.get(12));
				String acceleratorPackEnabled = getNotNullVal((String) tokens.get(13));
				String appName = getNotNullVal((String) tokens.get(14));
				String rolePrivileged = getNotNullVal((String) tokens.get(15));
				String apaccountType = getNotNullVal((String) tokens.get(16));

				// TODO: validate all the business role extended attributes
				//if (performValidation("Governance", extGovType, errorRecordList, tokens)) {
				//	_log.error("Issue found validating Business Role extended attribute parameters. Skipping record.");
				//	return;
				//}
				

				_log.info("Setting the Business Role Extended Attributes: " + roleName);
				_log.info("   isJoiner = " + isJoiner);
				_log.info("   formappName = " + formappName);
				_log.info("   roleBusApprovers = " + roleBusApprovers);
				_log.info("   roleBusApprovalRule = " + roleBusApprovalRule);
				_log.info("   additionalRoleBusApprovers = " + additionalRoleBusApprovers);
				_log.info("   additionalRoleBusApprovalRule = " + additionalRoleBusApprovalRule);
				_log.info("   acceleratorPackEnabled = " + acceleratorPackEnabled);
				_log.info("   appName = " + appName);
				_log.info("   rolePrivileged = " + rolePrivileged);
				_log.info("   apaccountType = " + apaccountType);

	
			    role.setAttribute("isJoiner", isJoiner);
				role.setAttribute("formappName", formappName);
				role.setAttribute("roleBusApprovers", roleBusApprovers);
				role.setAttribute("roleBusApprovalRule", roleBusApprovalRule);
				role.setAttribute("additionalRoleBusApprovers", additionalRoleBusApprovers);
				role.setAttribute("additionalRoleBusApprovalRule", additionalRoleBusApprovalRule);
				role.setAttribute("acceleratorPackEnabled", acceleratorPackEnabled);
				role.setAttribute("appName", appName);
				role.setAttribute("rolePrivileged", rolePrivileged);
				role.setAttribute("apaccountType", apaccountType);
				
			}
			else {
				// TODO should we force setting of extended attributes?
				// not enough tokens
				String errorMessage = "Not setting exended attributes on business role.";
				//addToErrorList(errorMessage, errorRecordList, tokens);
				//return;
			}		
		}

		// Special Handling for Entitlement Roles
		// These are optional, and if set, creates a profile w/ entitlements for the
		// applicaion/attribute combination
		if ("ENTITLEMENT".equalsIgnoreCase(roleType) ||
				"IT".equalsIgnoreCase(roleType)  ) {
			String profileDesc = (String)tokens.get(3);   // Same as Role Description, for now...

			if (tokens.size() > 8) {
				String profileApp  = (String)tokens.get(7);
				String profileAttr = (String)tokens.get(8);
				String entValueStr = (String)tokens.get(9);

				if (entValueStr != null && entValueStr.length() > 0) {
					//To parse multiple entitlements seperated by "|"
					//List entValues = _parser.parseLine(tokens.get(9));
					RFC4180LineParser entParser = new RFC4180LineParser("|");
					List entValues = (List)entParser.parseLine((String)tokens.get(9));
					
					String profileFilter = createProfileFilter(profileAttr, entValues);

					_log.info("Building the Profile for the Role : " + roleName);
					_log.info("   Profile App    = " + profileApp);
					_log.info("   Profile Attr   = " + profileAttr);
					_log.info("   Profile Desc   = " + profileDesc);
					_log.info("   Profile Filter = " + profileFilter);

					Application app = context.getObjectByName(Application.class, profileApp);

					if (null == app) {
						String errorMessage = "Application " + profileApp + " was not found in IIQ.";
						addToErrorList(errorMessage, errorRecordList, tokens);
						return;
					}

					QueryOptions qps = new QueryOptions();
					qps.addFilter(Filter.eq("application.name", profileApp));										
					qps.addFilter(Filter.eq("attribute", profileAttr));
					qps.addFilter(Filter.in("value", entValues));									
					List<ManagedAttribute> existingEntList = context.getObjects(ManagedAttribute.class, qps);
					if(existingEntList != null && !existingEntList.isEmpty()) {
						for (ManagedAttribute ma: existingEntList) {
							String entValue = ma.getValue();
							String entAttributeName = ma.getAttribute();
							_log.debug("real value: " + entValue);
							_log.debug("real attribute: " + entAttributeName);
						}
					  	_log.debug("Entitlement is available in IIQ: " + existingEntList); 
					} else {
						String errorMessage = "Application " + profileApp + " with Entitlement " + profileAttr + " with Value " + entValueStr + " was not found in IIQ.";
						addToErrorList(errorMessage, errorRecordList, tokens);
						// TODO what do we do with non-existent entitlements?
						// return;
					}

					Filter filter = Filter.containsAll(profileAttr, entValues);
					List reqEntitlementList = new ArrayList();
					reqEntitlementList.add(filter);

					List cmpFilterList = new ArrayList();
					CompositeFilter cf = new CompositeFilter();
					if(reqEntitlementList!=null) {
						cf.setOperation(Filter.BooleanOperation.AND);
						cf.setChildren(reqEntitlementList);
						cmpFilterList.add(cf);
						_log.debug("composite filter "+cf);
					}
					Profile profile = new Profile();
					profile.setApplication(app);
					profile.setConstraints(cmpFilterList);
					role.add(profile);

					_profilesCreated++;
				} else {
					_log.debug("Expected entitlement profile not found for: " + roleName);
				}
			}
		}

		// Check whether orProfiles flag is on
		if (_orProfiles) {
			role.setOrProfiles(_orProfiles);
		}

		// All done.  Go ahead and save this role.
		context.saveObject(role);

		setDescription(role, roleDesc, null);

		context.commitTransaction();
		context.decache(role);


		_rolesCreated++;
	}

	private void deleteRole(List tokens, List errorRecordList) throws GeneralException {
		//  Delete Role - Deletes existing roles
		//  ----------------------------------------------------
		//    Role Name           - Name of an existing role
		//  ----------------------------------------------------
		String roleName = (String)tokens.get(1);

		Bundle role = context.getObjectByName(Bundle.class, roleName);

		if (role != null) {
			context.removeObject(role);
			context.commitTransaction();
			context.decache(role);
			_rolesDeleted++;
		}
	}

	private void addInheritance(List tokens, List errorRecordList) throws GeneralException {
		//  Add Inheritance - Adds a parent to an existing role
		//  ------------------------------------------------
		//    Role Name         - Role to add a parent to
		//    Parent Role Name  - Parent role name
		//  ------------------------------------------------
		String roleName = (String)tokens.get(1);
		String parentRole = (String)tokens.get(2);

		_log.debug("ADD INHERITANCE," + roleName + "," + parentRole);

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		Bundle parent = context.getObjectByName(Bundle.class, parentRole);

		// Create stub...
		if (parent == null && null != parentRole) {
			parent = new Bundle();
			parent.setName(parentRole);
			context.saveObject(parent);
			context.commitTransaction();
			context.decache(parent);
			_rolesCreated++;
		}

		role.addInheritance(parent);

		context.saveObject(role);
		context.commitTransaction();
		context.decache(role);
	}


	private void addPermitted(List tokens, List errorRecordList) throws GeneralException {
		//  Add Permitted - Adds a permitted role to an existing role
		//  ----------------------------------------------
		//    Role Name            - Role to add permitted roles to
		//    Permitted Role Name  - Permitted role name
		//  ----------------------------------------------
		String roleName = (String)tokens.get(1);
		String permittedRole = (String)tokens.get(2);

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		Bundle permitted = context.getObjectByName(Bundle.class, permittedRole);

		if (role == null) {
			String errorMessage = "Role not found: " + roleName;
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
		if (permitted == null) {
			String errorMessage = "Permitted Role not found: " + permittedRole;
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
		role.addPermit(permitted);

		context.saveObject(role);
		context.commitTransaction();
		context.decache(role);
	}


	private void addRequired(List tokens, List errorRecordList) throws GeneralException {
		//  Add Required - Adds a required role to an existing role
		//  ----------------------------------------------
		//    Role Name            - Role to add required roles to
		//    Permitted Role Name  - Required role name
		//  ----------------------------------------------
		String roleName = (String)tokens.get(1);
		String requiredRole = (String)tokens.get(2);

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		Bundle required = context.getObjectByName(Bundle.class, requiredRole);

		if (role == null) {
			String errorMessage = "Role not found: " + roleName;
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
		if (required == null) {
			String errorMessage = "Required Role not found: " + requiredRole;
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
		role.addRequirement(required);

		context.saveObject(role);
		context.commitTransaction();
		context.decache(role);
	}

	
	private void addProfile(boolean isUpdate, List tokens, List errorRecordList) throws GeneralException {
		//  Add Profile - Adds a profile to an existing role
		//  --------------------------------------------
		//    Role Name
		//    Profile Description
		//    Profile Application
		//    Profile Filter
		//  --------------------------------------------
		String roleName = (String)tokens.get(1);
		String profileDesc = (String)tokens.get(2);
		String profileApp = (String)tokens.get(3);
		String profileFilter = (String)tokens.get(4);

		if (isUpdate)
			_log.debug("UPDATE PROFILE,"+roleName+","+profileDesc+","+profileApp+","+profileFilter);
		else
			_log.debug("ADD PROFILE,"+roleName+","+profileDesc+","+profileApp+","+profileFilter);

		JSONArray permissionArray = null;

		if(tokens.size() > 5) {
			try {
				permissionArray = new JSONArray(tokens.get(5));
			} catch (JSONException je){
				String errorMessage = "JSONException adding profile";
				addToErrorList(errorMessage, errorRecordList, tokens);
				return;
			}
		}

		Bundle role = (Bundle)context.getObjectByName(Bundle.class, roleName);
		if (null != role) {
			Application app = context.getObjectByName(Application.class, profileApp);

			// Handle non-existant apps by boot-strapping stub app
			//
			if (null == app) {
				// we will NOT be creating applications if we cannot find the one referenced by the add profile request
				String errorMessage = "Application " + profileApp + " was not found in IIQ.";
				addToErrorList(errorMessage, errorRecordList, tokens);
				return;
			}

			Profile profile = new Profile();
			if(profileFilter != null && profileFilter.length() > 0) {
				Filter filter = Filter.compile(profileFilter);
				profile.addConstraint(filter);
			}

			if(permissionArray != null && permissionArray.length() > 0) {
				for(int i = 0; i < permissionArray.length(); i++) {
					try {
						JSONObject permObject = permissionArray.getJSONObject(i);
						String target = permObject.getString("target");
						String rights = permObject.getString("rights");
						List rightsList = Util.stringToList(rights);
						Permission newPerm = new Permission();
						newPerm.addRights(rightsList);
						newPerm.setTarget(target);
						profile.addPermission(newPerm);
					} catch (JSONException je){
						String errorMessage = "JSONException adding profile";
						addToErrorList(errorMessage, errorRecordList, tokens);
						return;
					}
				}
			}
			profile.setDescription(profileDesc);
			profile.setApplication(app);

			// this is the only difference between an add and an update
			if (isUpdate) {
				role.getProfiles().clear();
			}
			
			role.add(profile);
			processRoleChanges(role);

			_profilesCreated++;

		}
	}

	private void updateName(List tokens, List errorRecordList) throws GeneralException {
		//  Update Name - Updates a role's name and/or displayName
		//  ------------------------------------------------
		//    Role Name
		//    Role New Name Value (use "", or leave it blank if you don't want to reset the name.)
		//    [Optional] Role New Display Name Value
		//  ------------------------------------------------
		String roleName = (String)tokens.get(1);
		String roleNewName = getNotNullVal((String) tokens.get(2));
		String roleNewDisplayName = "";

		if(tokens.size()==4) {
			roleNewDisplayName = getNotNullVal((String) tokens.get(3));
		}
		
		_log.debug("tokens: " + tokens);

		// validate all the information
		if (!isValidStringValue("Role Name", roleNewName, ROLE_NAME_SIZE, false, errorRecordList, tokens)
			|| !isValidStringValue("Role Display Name", roleNewDisplayName, ROLE_NAME_SIZE, false, errorRecordList, tokens)) {
			_log.error("Issue found validating 'Update Name' parameters. Skipping record.");
			return;
		}

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (null != role) {
		
			if (roleNewName != "") 
				role.setName(roleNewName);
			if (roleNewDisplayName != "") 
				role.setDisplayName(roleNewDisplayName);
			
			context.saveObject(role);
			context.commitTransaction();
			context.decache(role);
		} else {
			String errorMessage = "RoleImporter : UPDATE NAME - Role [" + roleName + "] not found.";
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
	}

	private void addMetadata(List tokens, List errorRecordList) throws GeneralException {
		//  Add Metadata - Adds metadata to an existing role
		//  ------------------------------------------------
		//    Role Name
		//    Meta-attribute Name
		//    Meta-attribute Value
		//    [Optional] datatype. Defaults to string, can be boolean or integer
		//  ------------------------------------------------
		String roleName = (String)tokens.get(1);
		String metaAttr = (String)tokens.get(2);
		String metaVal = (String)tokens.get(3);
		String datatype = "string";
		if(tokens.size()==5) {
			datatype = (String)tokens.get(4);
		}

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (null != role) {
			Object value=metaVal;
			if("boolean".equals(datatype)) value=Boolean.parseBoolean(metaVal);
			if("integer".equals(datatype)) value=Integer.parseInt(metaVal);
			role.setAttribute(metaAttr, value);
			context.saveObject(role);
			context.commitTransaction();
			context.decache(role);
		} else {
			String errorMessage = "RoleImporter : ADD METADATA - Role [" + roleName + "] not found.";
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
	}

	private void addDescription(List tokens, List errorRecordList) throws GeneralException {
		//  Add Description - Adds localized desc to existing role
		//  ------------------------------------------------------
		//    Role Name
		//    Description
		//    Locale
		//  ------------------------------------------------

		String roleName = (String)tokens.get(1);
		String desc = (String)tokens.get(2);
		String locale = (String)tokens.get(3);

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (null != role) {
			_log.debug("...ADD DESCRIPTION: using locale of: " + locale);
			setDescription(role, desc, locale);  
			context.saveObject(role);
			context.commitTransaction();
			context.decache(role);
		} else {
			String errorMessage = "RoleImporter : ADD DESCRIPTION - Role [" + roleName + "] not found.";
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
	}

	private void addAssignmentRule(List tokens, List errorRecordList) throws GeneralException {
		//  Add AssignmentRule - Adds a rule as the assignment logic
		//  ------------------------------------------------------
		//    Role Name
		//    Rule Name
		//  ------------------------------------------------


		String roleName = (String)tokens.get(1);
		String ruleName = (String)tokens.get(2);

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (null != role) {

			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if (null != rule) {

				IdentitySelector selector=new IdentitySelector();
				selector.setRule(rule);
				role.setSelector(selector);

				context.saveObject(role);
				context.commitTransaction();
				context.decache(role);
			} else {
				String errorMessage = "RoleImporter : ADD ASSIGNMENTRULE - Rule [" + ruleName + "] not found.";
				addToErrorList(errorMessage, errorRecordList, tokens);
				return;
			}
		} else {
			String errorMessage = "RoleImporter : ADD ASSIGNMENTRULE - Role [" + roleName + "] not found.";
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
	}

	private void addAccountSelector(List tokens, List errorRecordList) throws GeneralException {
		//  Add Account Selector - adds an account selector rule
		//  ------------------------------------------------------
		//    Role Name
		//    Rule Name
		//    Optional: Application
		//  ------------------------------------------------


		String roleName = (String)tokens.get(1);
		String ruleName = (String)tokens.get(2);
		String applicationName = null;
		if(tokens.size()>3) {
			applicationName = (String)tokens.get(3);
		}

		Bundle role = context.getObjectByName(Bundle.class, roleName);
		if (null != role) {

			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if (null != rule) {

				AccountSelectorRules asr=role.getAccountSelectorRules();
				if(asr==null) asr=new AccountSelectorRules();

				if(applicationName==null) {

					asr.setBundleLevelAccountSelectorRule(rule);
				} else {
					Application appl = context.getObjectByName(Application.class, applicationName);
					if(null == appl) {
						String errorMessage = "RoleImporter : ADD ACCOUNTSELECTOR - Application [" + applicationName + "] not found.";
						_log.error(errorMessage);
						addToErrorList(errorMessage, errorRecordList, tokens);
						return;
					}
					ApplicationAccountSelectorRule aasr=new ApplicationAccountSelectorRule(appl, rule);
					List rules=asr.getApplicationAccountSelectorRules();
					if(rules==null) rules=new ArrayList();
					rules.add(aasr);
					asr.setApplicationAccountSelectorRules(rules);
				}

				role.setAccountSelectorRules(asr);
				context.saveObject(role);
				context.commitTransaction();
				context.decache(role);
			} else {
				String errorMessage = "RoleImporter : ADD ACCOUNTSELECTOR - Rule [" + ruleName + "] not found.";
				addToErrorList(errorMessage, errorRecordList, tokens);
				return;
			}
		} else {
			String errorMessage = "RoleImporter : ADD ACCOUNTSELECTOR - Role [" + roleName + "] not found.";
			addToErrorList(errorMessage, errorRecordList, tokens);
			return;
		}
	}

	// validates the token value
	private boolean isValidStringValue(String tokenName, String tokenValue, int maxLength, boolean isRequired, List errorRecordList, List tokens) {
		boolean isValid = true;
		
		// check exists if required
		if(isRequired && (tokenValue == null || tokenValue.length() == 0)){
			isValid = false;
			String errorMessage = tokenName + " is required, but a valid value is not supplied.";
			addToErrorList(errorMessage, errorRecordList, tokens);
		}
		
		// check if length is ok
		if (isValid && Util.isNotNullOrEmpty(tokenValue) && tokenValue.length() > maxLength) {
			isValid = false;
			String errorMessage = tokenName + " value " + tokenValue + " length of " + tokenValue.length() + " is greater than allowed size: " + maxLength;
			addToErrorList(errorMessage, errorRecordList, tokens);
		}
		
		return isValid;
	}
	
	//validates role type
	private boolean isValidRoleType(String roleType, List errorRecordList, List tokens) {
		boolean isValid = true;
		
		if(!(roleType.equalsIgnoreCase("it") || roleType.equalsIgnoreCase("business") || roleType.equalsIgnoreCase("organizational") || roleType.equalsIgnoreCase("entitlement") )){
			isValid = false;
			String errorMessage = roleType + " is not a valid role type.";
			addToErrorList(errorMessage, errorRecordList, tokens);
		}
		
		return isValid;
	}
	
	//validates identity
	private boolean isValidIdentity(String identityType, String identityName, boolean isRequired, List errorRecordList, List tokens) throws GeneralException {
		boolean isValid = true;
		
		// check exists if required
		if(isRequired && (identityName == null || identityName.length() == 0)){
			isValid = false;
			String errorMessage = identityType + " is required, but a valid value is not supplied.";
			addToErrorList(errorMessage, errorRecordList, tokens);
		}
		
		if (identityName != null && identityName != "") {
			Identity identity = (Identity)context.getObjectByName(Identity.class, identityName);
			if (identity == null){
				isValid = false;
				String errorMessage = "Invalid " + identityType + ": " + identityName + " is not a valid identity in IIQ.";
				addToErrorList(errorMessage, errorRecordList, tokens);
			}
		}
		
		return isValid;
	}
	


	// adds a new error message to the list -- includes the tokens for the record with the error
	private void addToErrorList(String errorMessage, List errorRecordList, List<String> tokens) {
	
		addErrorMessage(errorMessage);
		
		StringBuilder sb = new StringBuilder();
		String loopDelim = "";
		String delim = ", ";	
		sb.append("\nError - " + (errorRecordList.size() + 1) + ": "  + errorMessage + "\nRecord: ");
		
		// loop through tokens and add them to the error message
		for(String token : tokens) {
			sb.append(loopDelim);
			sb.append(token);
			loopDelim = delim;
    	}
		_log.error(sb.toString());
    	errorRecordList.add(sb.toString());	
	}
	
	@SailPointRuleMainBody
	public String executeRule() throws Exception {
		
		//Beginning of rule execution section    
		result = context.getObjectByName(TaskResult.class, "GovConnect Role Importer Task");
		TaskDefinition taskDef = context.getObjectByName(TaskDefinition.class, "GovConnect Role Importer Task");
		
		List errorRecordList = new ArrayList();
		
		args = taskDef.getArguments();
		
		_roleFile = args.getString(ARG_ROLE_FILENAME);
		_useHeader = args.getBoolean(ARG_USE_HEADER);
		_fileEncoding = args.getString(ARG_FILE_ENCODING);
		
		// Allow existing roles to be updated (default) or skipped over
		if (false == args.getBoolean(ARG_UPDATE_EXISTING, true)) {
			_updateExisting = false;
		}
		
		// Flag for specifying multiple profiles in a role should be ORed
		if (true == args.getBoolean(ARG_OR_PROFILES, false)) {
			_orProfiles = true;
		}
		
		// Open the file and get a handle on it.
		InputStream stream = getFileStream();
		
		RFC4180LineIterator lines = null;
		if ( _fileEncoding != null ) {
			_log.debug("...encoding is: " + _fileEncoding);
			try {
				lines = new RFC4180LineIterator(new BufferedReader(new InputStreamReader(stream, _fileEncoding)));
			} catch (java.io.UnsupportedEncodingException e) {
				throw new GeneralException(e);
			}
		} else {
			lines = new RFC4180LineIterator(new BufferedReader(new InputStreamReader(stream)));
		}
		
		// Process each of the lines
		if (lines != null) {
			_lines = processLines(context, lines, errorRecordList);
		}
		
		if (errorRecordList !=null && !errorRecordList.isEmpty()) {
			_log.debug("Sending Email Notification with the following error list: " + errorRecordList);
			String emailTemplateName = "GovConnect-Bulk-Role-Load-Error-Notification";
			EmailTemplate template = context.getObjectByName(EmailTemplate.class, emailTemplateName);
			
			if (template != null) {
			
			HashMap emailVariables = new HashMap();       

				emailVariables.put("errString", "Error due to technical difficulties");
				emailVariables.put("errorRecordList", errorRecordList);
				EmailOptions ops = new EmailOptions();
				ops.setVariables(emailVariables);
				// TODO: determine correct TO for the error notifications
				ops.setTo("%%GOVCONNECT_SUPPORT_EMAIL_ADDRESS%%");
				context.sendEmailNotification(template, ops);
			}
		} else {
			_log.debug("All records loaded in IIQ successfully!");
		}
		
		result.setAttribute(RET_LINES, _lines);
		result.setAttribute(RET_ROLES_CREATED, Util.itoa(_rolesCreated));
		result.setAttribute(RET_ROLES_DELETED, Util.itoa(_rolesDeleted));
		result.setAttribute(RET_PROFILES_CREATED, Util.itoa(_profilesCreated));
		
		return "Success";
	
	}
	

}
