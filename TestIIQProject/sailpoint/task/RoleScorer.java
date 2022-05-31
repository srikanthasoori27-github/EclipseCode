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

import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleScorecard;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.roles.RoleMetricsQueryRegistry;

public class RoleScorer extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(RoleScorer.class);
	
    // Input Arguments
    public static final String ARG_ROLE = "role";

	// List of returned attributes to the TaskResult
	final static String ROLES_SCORED = "rolesScored";
	final static String TOTAL_ROLES = "totalRoles";
	final static String TASK_RESULTS = "taskResults";
	final static String ERROR_MSG = "errorMsg";
	
	final static String PROGRESS_MSG = MessageKeys.ROLE_SCORER_PROGRESS;

	private SailPointContext context = null;
	private RoleMetricsQueryRegistry queryRegistry = null;
	private TaskResult _result;
	private boolean _terminate = false;
	
	public RoleScorer() {
	}

	/**
	 * Terminate at the next convenient point.
	 */
	public boolean terminate() {
	    _terminate = true;
	    _result.addMessage(new Message(Type.Error, MessageKeys.ROLE_SCORER_TERMINATED));
	    _result.setTerminated(true);
		return true;
	}

    public void execute(SailPointContext context, TaskSchedule sched,
			TaskResult result, Attributes<String, Object> args)
			throws Exception {

	    setContext(context);
	    setQueryRegistry(new RoleMetricsQueryRegistry(context));
		_result = result;

		String roleId = (String) args.get(ARG_ROLE);
		log.debug("role = " + roleId);
		
		int rolesScored = 0;
		int percentComplete = 0;
		int totalRoles;
		
		try {
		    if (roleId != null && roleId.trim().length() > 0) {
		        Bundle role = getRole(roleId);
		        scoreRole(role);
		        totalRoles = 1;
		        rolesScored = 1;
		    } else {
                QueryOptions roleQuery = new QueryOptions();
		        totalRoles = context.countObjects(Bundle.class, roleQuery);
    		    roleQuery.setResultLimit(1);

    		    for (int i = 0; i < totalRoles && !_terminate; ++i) {
    		        roleQuery.setFirstRow(i);
    		        List<Bundle> roleList = context.getObjects(Bundle.class, roleQuery);
    		        if (roleList != null && !roleList.isEmpty()) {
    		            scoreRole(roleList.get(0));
    		            getContext().decache();
    		            rolesScored++;
    		        }
    		        percentComplete = Math.round((float)rolesScored / (float)totalRoles);
    		        updateProgress(context, result, new Message(PROGRESS_MSG, rolesScored, totalRoles).getLocalizedMessage(), percentComplete);
    		    }
		    }
		    
            percentComplete = 100;		    
            updateProgress(context, result, new Message(PROGRESS_MSG, rolesScored, totalRoles).getLocalizedMessage(), percentComplete);
            
            result.setAttribute(ROLES_SCORED, rolesScored);
            result.setAttribute(TOTAL_ROLES, totalRoles);
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

	private void scoreRole(Bundle roleToScore) throws GeneralException {
	    RoleScorecard roleScorecard = roleToScore.getScorecard();
	    if (roleScorecard == null) {
	        roleScorecard = new RoleScorecard();
	        roleToScore.add(roleScorecard);
	        getContext().saveObject(roleToScore);
	    }
	    
	    roleScorecard.setMembers(getIdentityCount(RoleMetricsQueryRegistry.MEMBERS, roleToScore));
	    roleScorecard.setMembersWithAdditionalEntitlements(getIdentityCount(RoleMetricsQueryRegistry.MEMBERS_WITH_ADDITIONAL_ENTITLEMENTS, roleToScore));
	    roleScorecard.setMembersWithMissingRequiredRoles(getIdentityCount(RoleMetricsQueryRegistry.MEMBERS_WITH_MISSING_REQUIRED, roleToScore));
	    roleScorecard.setDetected(getIdentityCount(RoleMetricsQueryRegistry.DETECTED, roleToScore));
	    roleScorecard.setDetectedAsExceptions(getIdentityCount(RoleMetricsQueryRegistry.DETECTED_EXCEPTIONS, roleToScore));
	    try {
            roleScorecard.setProvisionedEntitlements(getProvisionedEntitlements(roleToScore));
        } catch (Exception e) {
            log.error("Failed to get the provisioned entitlements count for role " + roleToScore.getName());
            _result.addMessage(errorMsg(e.toString()));
        }
        
        try {
            roleScorecard.setPermittedEntitlements(getPermittedEntitlements(roleToScore));
        } catch (Exception e) {
            log.error("Failed to get the permitted entitlements count for role " + roleToScore.getName());
            _result.addMessage(errorMsg(e.toString()));
        }
        
	    getContext().saveObject(roleScorecard);
	    getContext().commitTransaction();
	}
	
	public SailPointContext getContext() {
		return context;
	}

	public void setContext(SailPointContext context) {
		this.context = context;
	}
	
	public RoleMetricsQueryRegistry getQueryRegistry() {
	    return queryRegistry;
	}
	
	public void setQueryRegistry(RoleMetricsQueryRegistry queryRegistry) {
	    this.queryRegistry = queryRegistry;
	}

	private Message errorMsg(String msg) {
		return new Message(Message.Type.Error, msg);
	}

	private Message warnMsg(String msg) {
		return new Message(Message.Type.Warn, msg);
	}
	
	private int getIdentityCount(String metric, Bundle role) {
        int identityCount = queryRegistry.getIdentityCount(metric, role.getId());
        return identityCount;
	}

    

    private int getProvisionedEntitlements(Bundle role) {
        int provisionedEntitlementsCount = 0;
        Set<SimplifiedEntitlement> entitlements = (Set<SimplifiedEntitlement>) queryRegistry.get(RoleMetricsQueryRegistry.PROVISIONED_ENTITLEMENTS, role.getId());
        if (entitlements != null) {
            provisionedEntitlementsCount = entitlements.size();
        }
        return provisionedEntitlementsCount;
    }

    private int getPermittedEntitlements(Bundle role) {
        int permittedEntitlementsCount = 0;
        Set<SimplifiedEntitlement> entitlements = (Set<SimplifiedEntitlement>) queryRegistry.get(RoleMetricsQueryRegistry.PERMITTED_ENTITLEMENTS, role.getId());
        if (entitlements != null) {
            permittedEntitlementsCount = entitlements.size();
        }
        return permittedEntitlementsCount;
    }
}
