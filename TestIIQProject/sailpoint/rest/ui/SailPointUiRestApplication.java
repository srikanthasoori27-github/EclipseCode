/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui;

import org.glassfish.jersey.message.MessageProperties;
import org.glassfish.jersey.server.ResourceConfig;

import sailpoint.rest.ProductionBinder;
import sailpoint.rest.jaxrs.JsonMessageBodyReader;
import sailpoint.rest.jaxrs.JsonMessageBodyWriter;
import sailpoint.rest.ui.apponboard.ApplicationOnboardResource;
import sailpoint.rest.ui.apponboard.IdentityTriggerResource;
import sailpoint.rest.ui.batchrequest.BatchRequestListResource;
import sailpoint.rest.ui.certifications.CertificationListResource;
import sailpoint.rest.ui.certifications.schedule.CertificationScheduleResource;
import sailpoint.rest.ui.fam.FAMWidgetResource;
import sailpoint.rest.ui.form.FormResource;
import sailpoint.rest.ui.groupdefinition.GroupDefinitionResource;
import sailpoint.rest.ui.identities.IdentitiesResource;
import sailpoint.rest.ui.identitypreferences.IdentityPreferencesResource;
import sailpoint.rest.ui.identityrequest.IdentityRequestListResource;
import sailpoint.rest.ui.jaxrs.AllExceptionMapper;
import sailpoint.rest.ui.jaxrs.GeneralExceptionMapper;
import sailpoint.rest.ui.jaxrs.InvalidParameterExceptionMapper;
import sailpoint.rest.ui.jaxrs.ObjectAlreadyLockedExceptionMapper;
import sailpoint.rest.ui.jaxrs.ObjectNotFoundExceptionMapper;
import sailpoint.rest.ui.jaxrs.UnauthorizedAccessExceptionMapper;
import sailpoint.rest.ui.jaxrs.ValidationExceptionMapper;
import sailpoint.rest.ui.me.MeResource;
import sailpoint.rest.ui.me.TablePreferencesResource;
import sailpoint.rest.ui.pam.ContainerListResource;
import sailpoint.rest.ui.pam.PamApprovalResource;
import sailpoint.rest.ui.pam.PamIdentitySuggestResource;
import sailpoint.rest.ui.pam.PamPermissionListResource;
import sailpoint.rest.ui.pam.PamPrivilegedDataSuggestResource;
import sailpoint.rest.ui.policyviolation.PolicyViolationListResource;
import sailpoint.rest.ui.quicklink.QuickLinksResource;
import sailpoint.rest.ui.rapidSetup.IdentityOperationsResource;
import sailpoint.rest.ui.requestaccess.RequestAccessResource;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.rest.ui.workitems.WorkItemListResource;

/**
 * A JAX-RS application that enumerates all of our resources and providers for mobile UI (/ui/rest)  
 * If a new resource is added, it needs to be added to the list in getClasses().
 */
public class SailPointUiRestApplication extends ResourceConfig {

    public SailPointUiRestApplication() {
        register(AccessRequestConfigResource.class);
        register(ApplicationOnboardResource.class);
        register(ApprovalWorkItemListResource.class);
        register(BatchRequestListResource.class);
        register(CertificationListResource.class);
        register(CertificationScheduleResource.class);
        register(ContainerListResource.class);
        register(GroupDefinitionResource.class);
        register(PamPermissionListResource.class);
        register(PamIdentitySuggestResource.class);
        register(PamPrivilegedDataSuggestResource.class);
        register(ConfigResource.class);
        register(ElectronicSignatureResource.class);
        register(FormResource.class);
        register(IdentitiesResource.class);
        register(IdentityRequestListResource.class);
        register(IdentityTriggerResource.class);
        register(MeResource.class);
        register(MessageCatalogResource.class);
        register(PamApprovalResource.class);
        register(PolicyViolationListResource.class);
        register(QuickLinksResource.class);
        register(TablePreferencesResource.class);
        register(RedirectResource.class);
        register(RequestAccessResource.class);
        register(RiskResource.class);
        register(UISessionStorageResource.class);
        register(SuggestResource.class);
        register(UserResetResource.class);
        register(WorkItemListResource.class);
        register(IdentityPreferencesResource.class);
        register(SecurityQuestionsResource.class);

        // Providers
        register(GeneralExceptionMapper.class);
        register(AllExceptionMapper.class);
        register(InvalidParameterExceptionMapper.class);
        register(ValidationExceptionMapper.class);
        register(ObjectAlreadyLockedExceptionMapper.class);
        register(ObjectNotFoundExceptionMapper.class);
        register(UnauthorizedAccessExceptionMapper.class);
        register(JsonMessageBodyReader.class);
        register(JsonMessageBodyWriter.class);

        // FAM
        register(FAMWidgetResource.class);

        //RapidSetup
        register(IdentityOperationsResource.class);

        property(MessageProperties.LEGACY_WORKERS_ORDERING, true);

        try {
            Object o = getBindingClass().newInstance();
            register(o);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    protected Class getBindingClass() {
        return ProductionBinder.class;
    }

}
