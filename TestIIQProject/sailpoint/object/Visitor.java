/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base visitor for objects in the SailPoint model.
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;

/**
 * Base visitor for objects in the SailPoint model.
 */
public abstract class Visitor {

    //////////////////////////////////////////////////////////////////////
    //
    // Default Visitor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Root visitor, dispatch to class specific method.
     */
    public void visit(SailPointObject obj) throws GeneralException {
        obj.visit(this);
    }

    /**
     * Default visitor, does nothing.
     */
    public void visitSailPointObject(SailPointObject obj)
        throws GeneralException {
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Class-specific visitors, overload as necessary.
    // These should all call visitSailPointObject in case we want to 
    // add trace or some default behavior.
    //
    //////////////////////////////////////////////////////////////////////

    public void visitApplication(Application obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitApplicationScorecard(ApplicationScorecard obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitAuthenticationAnswer(AuthenticationAnswer obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitAuthenticationQuestion(AuthenticationQuestion obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitBundle(Bundle obj) throws GeneralException {
        visitSailPointObject(obj);
    }
    
    public void visitCertification(Certification obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitCertificationDefinition(CertificationDefinition obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitManagedAttribute(ManagedAttribute obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitGroupDefinition(GroupDefinition obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitGroupFactory(GroupFactory obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitGroupIndex(GroupIndex obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitIdentity(Identity obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitIdentityTrigger(IdentityTrigger obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitIntegrationConfig(IntegrationConfig obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitLink(Link obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitLocalizedAttribute(LocalizedAttribute obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitMitigationExpiration(MitigationExpiration obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitMonitoringStatistic(MonitoringStatistic obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitPolicy(Policy obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitPolicyViolation(PolicyViolation obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitProfile(Profile obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitRequest(Request obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitRule(Rule obj) throws GeneralException {
        visitSailPointObject(obj);
    }
    
    public void visitScope(Scope obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitScorecard(Scorecard obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitTag(Tag obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitTaskDefinition(TaskDefinition obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitTaskResult(TaskResult obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitTaskSchedule(TaskSchedule obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitUIPreferences(UIPreferences obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitWorkflow(Workflow obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitWorkflowCase(WorkflowCase obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitServer(Server obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitWorkItem(WorkItem obj) throws GeneralException {
        visitSailPointObject(obj);
    }
	
    public void visitWorkItemConfig(WorkItemConfig obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitJasperResult(JasperResult obj) throws GeneralException {
        visitSailPointObject(obj);
    }

     public void visitPersistedFile(PersistedFile obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitAccountGroup(AccountGroup obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitTarget(Target obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitCorrelationConfig(CorrelationConfig obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitPasswordPolicyHolder(PasswordPolicyHolder obj) throws GeneralException  {
        visitSailPointObject(obj);
    }

    public void visitPasswordPolicy(PasswordPolicy obj) throws GeneralException {
        visitSailPointObject(obj);
    }

    public void visitCertificationGroup(CertificationGroup certGroup) throws GeneralException{
        visitSailPointObject(certGroup);
    }

    public void visitRoleIndex(RoleIndex roleIndex) throws GeneralException {
        visitSailPointObject(roleIndex);
    }

    public void visitRoleScorecard(RoleScorecard roleScorecard) throws GeneralException {
        visitSailPointObject(roleScorecard);
    }

    public void visitRoleMetadata(RoleMetadata roleMetadata) throws GeneralException {
        visitSailPointObject(roleMetadata);
    }

    public void visitIdentityRequest(IdentityRequest ir)
        throws GeneralException {
        
        visitSailPointObject(ir);
    }
    
    public void visitIdentityRequestItem(IdentityRequestItem item) 
        throws GeneralException {
    
        visitSailPointObject(item);
    }

	public void visitBatchRequest(BatchRequest batchRequest) throws GeneralException {
		visitSailPointObject(batchRequest);
	}
	
	public void visitBatchRequestItem(BatchRequestItem batchRequestItem) throws GeneralException {
		visitSailPointObject(batchRequestItem);
	}
	
    public void visitCertificationItem(CertificationItem certificationItem)
       throws GeneralException {       
        visitSailPointObject(certificationItem);        
    }
    
    public void visitCertificationEntity(CertificationEntity certificationEntity)
        throws GeneralException {
    	visitSailPointObject(certificationEntity);
    }
        
    public void visitTargetSource(TargetSource source) throws GeneralException {
        visitSailPointObject(source);
    }
    
    public void visitDynamicScope(DynamicScope dynamicScope) throws GeneralException {
        visitSailPointObject(dynamicScope);
    }
    
    public void visitQuickLink(QuickLink quickLink) throws GeneralException {
        visitSailPointObject(quickLink);
    }

    public void visitQuickLinkOptions(QuickLinkOptions qlo) throws GeneralException {
        visitSailPointObject(qlo);
    }

    public void visitPlugin(Plugin plugin) throws GeneralException {
        visitSailPointObject(plugin);
    }

    public void visitClassification(Classification classification) throws GeneralException {
        visitSailPointObject(classification);
    }
}

