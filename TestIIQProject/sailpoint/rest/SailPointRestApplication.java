/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.MessageProperties;
import org.glassfish.jersey.server.ResourceConfig;

import sailpoint.rest.jaxrs.GeneralExceptionMapper;
import sailpoint.rest.jaxrs.JsonMessageBodyReader;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;
import sailpoint.rest.oauth.OAuthClientListResource;
import sailpoint.rest.oauth.OAuthConfigurationResource;
import sailpoint.rest.ui.UISessionStorageResource;
import sailpoint.rest.ui.jaxrs.InvalidParameterExceptionMapper;
import sailpoint.rest.ui.jaxrs.ObjectNotFoundExceptionMapper;
import sailpoint.server.Environment;

/**
 * A JAX-RS application that enumerates all of our resources and providers.  If
 * a new resource is added, it needs to be added to the list in getClasses().
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class SailPointRestApplication extends ResourceConfig {
    
    public SailPointRestApplication() {
        register(AccountGroupHierarchyResource.class);
        register(AnalyzeResource.class);
        register(ApplicationsResource.class);
        register(AttachmentResource.class);
        register(CertificationResource.class);
        register(CertificationEntityResource.class);
        register(CertificationGroupListResource.class);
        register(CertificationGroupResource.class);
        register(CertificationItemResource.class);
        register(CertificationListResource.class);
        register(CertificationRevocationResource.class);
        register(ConfigurationResource.class);
        register(DebugResource.class);
        register(DynamicScopeListResource.class);
        register(DynamicScopeResource.class);
        register(ElectronicSignatureResource.class);
        register(ExportResource.class);
        register(ExclusionsListResource.class);
        register(FormResource.class);
        register(GroupFactoryResource.class);
        register(HostsResource.class);
        register(IdentityListResource.class);
        register(IdentityHistoryResource.class);
        register(IdentityResource.class);
        register(IdentityRequestListResource.class);
        register(ImageResource.class);
        register(ItRoleMiningTemplateService.class);
        register(IIQResource.class);
        register(LocalizedAttributeResource.class);
        register(ManagedAttributesResource.class);
        register(ModulesResource.class);
        register(NotificationsResource.class);
        register(ObjectConfigResource.class);
        register(OAuthClientListResource.class);
        register(OAuthConfigurationResource.class);
        register(PasswordInterceptResource.class);
        register(PolicyResource.class);
        register(QuickLinkListResource.class);
        register(RequestAccessResource.class);
        register(RequestResource.class);
        register(RoleEditResource.class);
        register(RoleListResource.class);
        register(RoleMiningResource.class);
        register(RoleViewerResource.class);
        register(UISessionStorageResource.class);
        register(SuggestResource.class);
        register(TabStateResource.class);
        register(TaskResultsResource.class);
        register(TaskScheduleListResource.class);
        register(WorkflowListResource.class);
        register(WorkflowResource.class);
        register(WorkItemResource.class);
        register(WorkItemArchiveResource.class);
        register(VelocityValidatorResource.class);
        register(ReportResource.class);
        register(PasswordPolicyResource.class);
        register(MessageCatalogResource.class);
        register(FormListResource.class);
        register(WorkItemNotificationsResource.class);
        register(ProvisioningTransactionListResource.class);
        // only register the Plugin related endpoints if it is enabled
        if (Environment.getEnvironment().getPluginsConfiguration().isEnabled()) {
            register(PluginsListResource.class);
        }
        register(AlertListResource.class);
        register(AlertDefinitionListResource.class);
        register(ServerStatisticListResource.class);
        register(MonitoringStatisticListResource.class);
        register(ServiceDefinitionResource.class);

        // Providers
        register(GeneralExceptionMapper.class);
        register(ObjectNotFoundExceptionMapper.class);
        register(InvalidParameterExceptionMapper.class);
        register(JsonMessageBodyReader.class);
        register(JsonMessageBodyWriter.class);

        property(MessageProperties.LEGACY_WORKERS_ORDERING, true);

        register(new ProductionBinder());

        register(MultiPartFeature.class);
    }

}
