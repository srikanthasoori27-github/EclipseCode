/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class that serves as a registry of class lists organized
 * for various purposes.  We started growing several of these and
 * it became error prone to keep them in sync, so they're all
 * in one place now.  
 *
 * When you add a new class, particularly a SailPointObject subclass,
 * consider whether it belongs in one of these lists.
 * it doesn't do anything dangerous should expose it.
 *
 */

package sailpoint.object;


/**
 * Class that serves as a registry of class lists organized
 * for various purposes.
 */
public abstract class ClassLists {

    /** 
     * All "top-level" SailPointObject sub classes.
     * This is used to drive the list of classes in the debug page.
     *
     * @ignore
     * There are others, but those do not have independent lifespans.
     */
    public static final Class[] MajorClasses = {

        AccountGroup.class,
        ActivityDataSource.class,
        Alert.class,
        AlertDefinition.class,
        Application.class,
        ApplicationActivity.class,
        ApplicationScorecard.class,
        AuditConfig.class,
        AuditEvent.class,
        AuthenticationQuestion.class,
        BatchRequest.class,
        Bundle.class,
        BundleArchive.class,
        Category.class,
        Capability.class,
        Certification.class,
        CertificationArchive.class,
        CertificationDefinition.class,
        CertificationGroup.class,
        Classification.class,
        Configuration.class,
        CorrelationConfig.class,
        Custom.class,
        DatabaseVersion.class,
        Dictionary.class,
        DynamicScope.class,
        EmailTemplate.class,
        Form.class,
        FullTextIndex.class,
        GroupFactory.class,
        GroupDefinition.class,
        GroupIndex.class,
        Identity.class,
        IdentityArchive.class,
        IdentityEntitlement.class,
        IdentityHistoryItem.class,
        IdentityRequest.class,
        IdentitySnapshot.class,
        IdentityTrigger.class,
        IntegrationConfig.class,
        JasperResult.class,
        JasperTemplate.class,
        LocalizedAttribute.class,
        ManagedAttribute.class,
        MessageTemplate.class,
        MiningConfig.class,
        MitigationExpiration.class,
        Module.class,
        MonitoringStatistic.class,
        ObjectConfig.class,
        PasswordPolicy.class,
        Plugin.class,
        Policy.class,
        PolicyViolation.class,
        ProcessLog.class,
        Profile.class,
        ProvisioningRequest.class,
        ProvisioningTransaction.class,
        QuickLink.class,
        RecommenderDefinition.class,
        Request.class,
        RequestState.class,
        RequestDefinition.class,
        ResourceEvent.class,
        RightConfig.class,
        RoleChangeEvent.class,
        RoleIndex.class,
        RoleMetadata.class,
        RoleMiningResult.class,
        RoleScorecard.class,
        Rule.class,
        RuleRegistry.class,
        Scope.class,
        Scorecard.class,
        ScoreConfig.class,
        SPRight.class,
        ServerStatistic.class,
        ServiceDefinition.class,
        ServiceStatus.class,
        Server.class,
        SyslogEvent.class,
        Tag.class,
        Target.class,
        TargetAssociation.class,
        TargetSource.class,
        TaskDefinition.class,
        TaskResult.class,
        TaskSchedule.class,
        TimePeriod.class,
        UIConfig.class,
        UIPreferences.class,
        Widget.class,
        Workflow.class,
        WorkflowCase.class,
        WorkflowRegistry.class,
        WorkflowTestSuite.class,
        WorkItem.class,
        WorkItemArchive.class,
    };

    /** 
     * The classes that might be exported by the console.
     * This should include "configuration" classes, but not
     * high-volume classes like Identity or ApplicationActivity.
     */
    public static final Class[] ExportClasses = {

        // probably not that many of these, but they're similar
        // to Identity, data we aggregate but don't manage
        //AccountGroup.class,

        ActivityDataSource.class,
        Application.class,
        // these are normally inside Application, if we
        // allow history then we'll have too many
        //ApplicationScorecard.class,
        AuditConfig.class,

        // too big!
        //ApplicationActivity.class,
        //AuditEvent.class,

        AuthenticationQuestion.class,

        Bundle.class,
        BundleArchive.class,

        Capability.class,

        Category.class,
        
        Certification.class,
        // !! woah, since these are "archival" objects we could have a 
        // ton of them, need to reconsider including them here
        CertificationArchive.class,

        CertificationDefinition.class,

        Classification.class,
        
        Dictionary.class,

        SPRight.class,

        Configuration.class,
        CorrelationConfig.class,
        Custom.class,

        DynamicScope.class,
        EmailTemplate.class,
        Form.class,

        GroupFactory.class,
        GroupDefinition.class,

        // there can potentially be a large number of these, and they're
        // generated from the Identity objects which aren't exported,
        // so assume we can regenerate them later
        //GroupIndex.class,

        //Identity.class,
        //IdentitySnapshot.class,

        IdentityTrigger.class,

        IntegrationConfig.class,
        JasperResult.class,
        MessageTemplate.class,
        MiningConfig.class,
        Module.class,
        MonitoringStatistic.class,

        PasswordPolicy.class,
        // multiplied by Identity
        //MitigationExpiration.class,

        ObjectConfig.class,
        Policy.class,

        // multiplied by Identity
        //PolicyViolation.class,

        QuickLink.class,
        Request.class,
        RequestState.class,
        RequestDefinition.class,
        RightConfig.class,
        Rule.class,
        RuleRegistry.class,
        
        // these are always inside something else now
        //Schema.class,

        // numerous
        //Scorecard.class,

        ScoreConfig.class,
        ServiceDefinition.class,    // not ServiceStatus!
        // I don't think Server makes sense - jsl
        SyslogEvent.class, 
        
        Tag.class,
        
        TargetSource.class,

        TaskDefinition.class,
        // could have a lot of these
        TaskResult.class,
        TaskSchedule.class,
        
        TimePeriod.class,

        UIConfig.class,
        //UIPreferences.class,

        Widget.class,

        Workflow.class,
        WorkflowTestSuite.class,

        // could have a lot of these
        WorkflowCase.class,
        WorkflowRegistry.class,
        WorkItem.class,
    };


    /**
     * Classes that are potentially high volume.
     * This should be the things in MajorClasses that are
     * not in ExportClasses.workItemsArchiveTableColumns
     * 
     * @ignore
     * (and of course it would be less error prone to derive this on the fly).
     */
    public static final Class[] VolumeClasses = {

        AccountGroup.class,
        ApplicationActivity.class,
        AuditEvent.class,
        ManagedAttribute.class,
        GroupIndex.class,
        Identity.class,
        IdentityEntitlement.class,
        IdentityRequest.class,
        IdentitySnapshot.class,
        MitigationExpiration.class,
        PolicyViolation.class,
        ProcessLog.class,
        ResourceEvent.class,
        RoleIndex.class,
        RoleMetadata.class,
        RoleMiningResult.class,
        RoleScorecard.class,
        ServerStatistic.class,
        TargetAssociation.class,
        Target.class,
        UIPreferences.class,
        WorkItemArchive.class
    };

}
