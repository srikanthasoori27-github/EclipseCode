/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. 
 *
 *  DirectRoleMiner
 *
 *  This custom task executor will take some arguments and mine for roles
 *  based on a group, threshold and application list.  The task will search
 *  for all the entitlements based on the filter and application list.  Then
 *  for any entitlements over the specificied threshold, a business role will
 *  be created.
 *
 *  Arguments for this task include:
 *
 * 	  Role Name of the role to which mined entitlements will be attached (if that option is selected)
 *    New Role Name - Name to use for a newly created Business Role (if that option is selected)
 *    Application List - List of applications to mine entitlements on
 *    Group Definition - This is what the mining activity will be run on
 *    Threshold % - This represents the percent of users for each entitlement
 *                  required to include that entitlement in the business profile
 *
 *  @author Terry Sigle
 *    
 */
package sailpoint.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.FilterRenderer;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Profile;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.tools.Message.Type;
import sailpoint.web.messages.MessageKeys;

public class DirectMiningTask extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(DirectMiningTask.class);
	
	   //
    // Input Arguments
    //
    public static final String ARG_ROLE_ID = "roleId";
    public static final String ARG_NEW_ROLE_NAME = "newRoleName";
    public static final String ARG_NEW_ROLE_OWNER = "newRoleOwner";
    public static final String ARG_NEW_ROLE_TYPE = "newRoleType";
    public static final String ARG_CONTAINER_ROLE = "containerRole";
    public static final String ARG_GROUP_NAMES = "groupNames";
    public static final String ARG_FILTER = "filter";
    public static final String ARG_CONSTRAINT_FILTERS = "identityConstraintFilters";
    public static final String ARG_APPLICATIONS = "applications";
    public static final String ARG_THRESHOLD = "threshold";
    public static final String ARG_SIMULATE = "simulate";

	// List of returned attributes to the TaskResult
	final static String FILTER_USED = "filterUsed";
	final static String NUM_IDENTITIES_MINED = "numIdentitiesMined";
	final static String NUM_CANDIDATE_ENTITLEMENTS = "numCandidateEntitlements";
	final static String NUM_USED_ENTITLEMENTS = "numUsedEntitlements";
	final static String THRESHOLD = "threshold";
	final static String SIMULATE = "simulate";
	final static String TASK_RESULTS = "taskResults";
	final static String ERROR_MSG = "errorMsg";

	private SailPointContext context = null;
	private TaskResult _result;
	
	private DirectRoleMiner roleMiner;

	public DirectMiningTask() {
	}

	/**
	 * Terminate at the next convenient point.
	 */
	public boolean terminate() {
	    roleMiner.terminate();
	    _result.addMessage(new Message(Type.Error, MessageKeys.IT_ROLE_MINING_TERMINATED));
	    _result.setTerminated(true);
		return true;
	}

	/**
	 * Parse a string application IDs separated by ',' and return a List of
	 * Applications
	 */
	private List<Application> getApplications(String apps) {
		ArrayList<Application> appsList = new ArrayList<Application>();

		try {
			RFC4180LineParser parser = new RFC4180LineParser(',');

			if (apps != null) {
				ArrayList<String> tmpList = parser.parseLine(apps);

				for (String appId : tmpList) {
					Application app = getContext().getObjectById(Application.class, appId.trim());
					appsList.add(app);
				}
			}
		} catch (Exception e) {
		    if (log.isErrorEnabled())
		        log.error(e.getMessage(), e);
		}

		return appsList;
	}

	// ************************************************************************
	// Task to perform Direct Mining on existing or new Roles and Applications.
	// The arguments passed to this task include:
	//
	// roleName (opt) - Name of EXISTING role to mine
	// newRoleName (opt) - Name of NEW role to mine
	// newRoleOwner (opt) - Owner to set if new role is created
    // newRoleType (opt) - Type to set if new role is created
	// groupName (opt) - Group to use for selecting identities
	// filter (opt) - Filter to use for selecting identities
	// applications (req) - Required applications to mine entitlements on
	// threshold (req) - Threshold to meet to include entitlement
	// simulate - Boolean to similate activity on
	// ************************************************************************
	@SuppressWarnings("unchecked")
    public void execute(SailPointContext context, TaskSchedule sched,
			TaskResult result, Attributes<String, Object> args)
			throws Exception {

		setContext(context);
		_result = result;

		String roleId = (String) args.get(ARG_ROLE_ID);
		String newRoleName = (String) args.get(ARG_NEW_ROLE_NAME);
		String newRoleOwner = (String) args.get(ARG_NEW_ROLE_OWNER);
        String newRoleType = (String) args.get(ARG_NEW_ROLE_TYPE);
		String containerRole = (String) args.get(ARG_CONTAINER_ROLE);
		String groupNames = (String) args.get(ARG_GROUP_NAMES);
		String filter = (String) args.get(ARG_FILTER);
		String applications = (String) args.get(ARG_APPLICATIONS);
		int threshold = args.getInt(ARG_THRESHOLD);
		boolean simulate = args.getBoolean(ARG_SIMULATE);
		boolean refreshIdentities = true; // args.getBoolean("refreshIdentities");

		List<Application> applicationList = this.getApplications(applications);

		log.debug("roleId          = " + roleId);
		log.debug("newRoleName       = " + newRoleName);
		log.debug("newRoleOwner      = " + newRoleOwner);
        log.debug("newRoleType       = " + newRoleType);
		log.debug("containerRole     = " + containerRole);
		log.debug("groupNames         = " + groupNames);
		log.debug("filter            = " + filter);
		log.debug("applications      = " + applications);
		log.debug("threshold         = " + threshold);
		log.debug("simulate          = " + simulate);
		log.debug("refreshIdentities = " + refreshIdentities);

		// Mapping of results that come back from the Role Miner to be sent
		// back to task.
		Map<String, Object> results = null;

		// Check for all the required inputs and combinations
		List<Message> returnMsgs = new ArrayList<Message>();

		// 1. Check for either designation of an existing or new role and only
		// one
		if ((roleId == null && newRoleName == null)
				|| (roleId != null && newRoleName != null)) {
			returnMsgs
					.add(errorMsg("Must specifiy one of existing or new Business Role"));
		}

		// 2. If default Owner isn't null, ensure new role being created
		if (newRoleOwner != null && roleId != null) {
			returnMsgs
					.add(errorMsg("  Default Owner only allowed when creating new Business Role"));
		}

		// 3. If new role being created, require owner
		if (newRoleOwner == null && newRoleName != null) {
			returnMsgs
					.add(errorMsg("  Owner required when creating new Business Role"));
		}

        // 4. If new role being created, require type
        if (newRoleType == null && newRoleName != null) {
            returnMsgs
                    .add(errorMsg("  Role type required when creating new Business Role"));
        }

		// 5. If groupName and filter passed, filter will be ignored.
		if (groupNames != null && filter != null) {
			returnMsgs
					.add(warnMsg("  Filter will be ignored if Group selected"));
		}

		// 6. If groupName and filter are null and we don't have an existing
		// role defined, error
		if (groupNames == null && filter == null && roleId == null) {
			returnMsgs
					.add(errorMsg("If Group and Filter aren't specified, existing Business Role required for filter"));
		}

		boolean terminalFailure = false;
		if (returnMsgs.size() > 0) {
			result.addMessages(returnMsgs);
			terminalFailure = true;
		}

		try {
			// *********************************************
			// Get the role to be mined
			// *********************************************
			Bundle role = null;
			if (roleId != null) {
				role = this.getRole(roleId);
			} else {
			    Bundle existingRole = getContext().getObjectByName(Bundle.class, newRoleName);
			    
			    if (existingRole != null) {
			        // We can't create a role with this name because it already exists
                    result.addMessage(new Message(Type.Error, MessageKeys.NAME_ALREADY_EXISTS, new Message(MessageKeys.ROLE).getLocalizedMessage(), "\'" + newRoleName + "\'"));
			        terminalFailure = true;			        
			    } else {
			        role = this.createRole(newRoleName, newRoleOwner, newRoleType, containerRole);
			    }
			}

			if (!terminalFailure) {
    			// *********************************************
    			// Get a list of filters from the group passed,
    			// Then, if groupName isn't passed, pull them
    			// from the filter passed
    			// *********************************************
    			List<Filter> filters = new ArrayList<Filter>();
    
    			if (groupNames != null) {
    			    List<String> groupNameList = Util.csvToList(groupNames);
    			    Filter groupFilter;
    			    
    			    if (groupNameList.size() > 1) {
        			    List<Filter> groupFilterList = new ArrayList<Filter>();
        			    
        			    for (String groupName : groupNameList) {
            			    GroupDefinition gd = getContext().getObjectByName(
            	                        GroupDefinition.class, groupName);
            			    groupFilterList.add(gd.getFilter());
        			    }
        			    
        			    groupFilter = Filter.or(groupFilterList);
    			    } else {
                        GroupDefinition gd = getContext().getObjectByName(
                                GroupDefinition.class, groupNameList.get(0));
    			        
    			        groupFilter = gd.getFilter();
    			    }
    
    				filters.add(groupFilter);
    			} else if (filter != null) {
    				Filter flt = Filter.compile(filter);
    
    				filters.add(flt);
    			} else {
    				List<Profile> roleProfiles = role.getProfiles();
    				List<Filter> roleFilters = null;
    
    				for (Profile profile : roleProfiles) {
    					roleFilters = profile.getConstraints();
    
    					for (Filter flt : roleFilters) {
    						filters.add(flt);
    					}
    				}
    			}
    			log.debug("Using Filter: " + filters);
    
    			StringBuilder filterString = new StringBuilder();
    			FilterRenderer filterRenderer = new FilterRenderer();
    			int i = 0;
    			for (Filter f : filters) {
    				if (i++ > 0) {
    					filterString.append(" AND ");
    				}
    				filterString.append(filterRenderer.render(f)).append("\n");
    			}
    
    			result.setAttribute(FILTER_USED, filterString.toString());
    
    			// *********************************************
    			// Create a DirectRoleMiner object with details passed to task
    			// *********************************************
    			roleMiner = new DirectRoleMiner(getContext(), role, filters, applicationList, threshold, sched);
    
    			// *********************************************
    			// And finally, mine the filter/applications for entitlements and
    			// return a Mapping of result information.
    			// *********************************************
    			results = roleMiner.mineRole();
    
    			// *********************************************
    			// If ERROR_MSG is set, then a serious error occured
    			// and we need to add an error to the TaskResult
    			// *********************************************
    			if (results.containsKey(ERROR_MSG)) {
    				result.addMessage(errorMsg((String) results.get(ERROR_MSG)));
    			} else {
    				// *********************************************
    				// Save the new/existing role if we aren't simulating
    				// *********************************************
    				if (!simulate) {
    					saveRole(role);
    					// *********************************************
    					// refresh the identities if we aren't simulating
    					// *********************************************
    					if (refreshIdentities) {
    						roleMiner.refreshIdentities();
    					}
    				}
    			}
    
    			// *********************************************
    			// Set all the task results for the Task Object
    			// *********************************************
    			result.setAttribute(NUM_IDENTITIES_MINED, results
    					.get(NUM_IDENTITIES_MINED));
    			result.setAttribute(NUM_CANDIDATE_ENTITLEMENTS, results
    					.get(NUM_CANDIDATE_ENTITLEMENTS));
    			result.setAttribute(NUM_USED_ENTITLEMENTS, results
    					.get(NUM_USED_ENTITLEMENTS));
    			result.setAttribute(THRESHOLD, threshold);
    			result.setAttribute(SIMULATE, simulate);
    
    			List<String> taskResults = (List<String>) results.get(TASK_RESULTS);
    
    			String taskResultString = "\n";
    			if (taskResults != null) {
    				for (String taskResult : taskResults) {
    					taskResultString += taskResult + "\n";
    				}
    			}
    			result.setAttribute(TASK_RESULTS, taskResultString);
    
    			if (log.isDebugEnabled()) {
    				debugRole(role);
    			}
			}
		} catch (Exception ex) {
			result.addMessage(errorMsg(ex.toString()));
		}
		log.debug("Finished...");
	}

	private Bundle getRole(String roleId) throws GeneralException {
		Bundle role = getContext().getObjectById(Bundle.class, roleId);

		if (role == null) {
			throw new GeneralException("Unable to find existing Business Role");
		}
		return role;
	}

	private Bundle createRole(String roleName, String defaultOwner, String roleType, String containerRoleName) 
		throws GeneralException 
	{
		Bundle role = new Bundle();
    	Bundle topLevelContainer = null;

		role.setName(roleName);
		role.setDescription("Results from IT Role Mining Task");

		// set to the defaultOwner passed
		Identity owner = getContext().getObjectById(Identity.class, defaultOwner);

		role.setOwner(owner);
		
		role.setType(roleType);

		// if necessary, set the container for the role
    	if (containerRoleName != null) {
    		topLevelContainer = getContext().getObjectByName(Bundle.class, containerRoleName);
            connect(role, topLevelContainer);
    	} else {
    	    getContext().saveObject(role);
    	}

		return role;
	}

	// Totally stolen from RoleLifecycler....
	//
	private void connect(Bundle role, Bundle container) 
		throws GeneralException 
	{
		role.addInheritance(container);
		role.setAssignedScope(container.getAssignedScope());
		getContext().saveObject(role);
	}

	private void debugRole(Bundle role) {
		log.debug("Resulting Role");
		log.debug("--------------");

		log.debug("       Name: " + role.getName());
		log.debug("Description: " + role.getDescription());

		if (role.getOwner() != null) {
			log.debug("      Owner: " + role.getOwner().getName());
		}

		List<Profile> profiles = role.getProfiles();

		int profCnt = 0;

		for (Profile profile : profiles) {
			profCnt++;
			log.debug("  Profile [" + profCnt + "]:");
			log.debug("     Name: " + profile.getName());

			List<Filter> filters = profile.getConstraints();

			for (Filter filter : filters) {
				log.debug("   Filter: " + filter);
			}
		}
	}

	private void saveRole(Bundle role) throws GeneralException {
		// ******************************
		// Save the Role
		// ******************************
		getContext().saveObject(role);

		// ******************************
		// Commit the objects to database
		// ******************************
		getContext().commitTransaction();
	}

	public SailPointContext getContext() {
		return context;
	}

	public void setContext(SailPointContext context) {
		this.context = context;
	}

	private Message errorMsg(String msg) {
		return new Message(Message.Type.Error, msg);
	}

	private Message warnMsg(String msg) {
		return new Message(Message.Type.Warn, msg);
	}

}
