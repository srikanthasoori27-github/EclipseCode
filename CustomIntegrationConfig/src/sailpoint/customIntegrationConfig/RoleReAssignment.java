package sailpoint.customIntegrationConfig;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.RoleAssignment;
import sailpoint.tools.GeneralException;

public class RoleReAssignment {
	
	SailPointContext context;
	public void reassignRoleTargets(Identity identity)
	{
		List<RoleAssignment> roleAssignments = identity.getRoleAssignments();
		
		List targets = new ArrayList();
		if(roleAssignments!=null && roleAssignments.size()>0)
		{
			for(RoleAssignment roleAssignment : roleAssignments)
			{
				roleAssignment.getTargets();
			}
		}
		
		
	}
	
	
	public void ProvisionRoleAssignment(Identity ident,String roleId, String assignmentId,String assigner)
	{
		
		Bundle role =  context.getObjectById(Bundle.class, roleId);
		if (role == null) 
            throw new GeneralException("Invalid role id: " + roleId);
		ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(ident);
        AccountRequest account = new AccountRequest();
        account.setApplication(ProvisioningPlan.APP_IIQ);
        plan.add(account);
        AttributeRequest att = new AttributeRequest();
        account.add(att);
        att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
        att.setValue(role);
        
        RoleAssignment assignment = ident.getRoleAssignmentById(assignmentId);
       
        att.setOperation(Operation.Add);
        Provisioner p = new Provisioner(context);
        p.setRequester(assigner);

        // The argument map in this method is used for "script args"
        // that are passed to any scripts or rules in the Templates
        // or Fields.  Here we use the step args for both the
        // options to the Provisioner and the script args during compilation.
        ProvisioningProject project = p.compile(plan, null);

        if (project.hasQuestions()) {
            // nothing we can do about it here
            log.warn("Project has unanswered questions");
        }

        p.execute(project);
	}

}
