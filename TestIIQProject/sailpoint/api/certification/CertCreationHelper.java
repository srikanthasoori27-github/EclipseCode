package sailpoint.api.certification;

import java.util.Date;
import java.util.List;

import sailpoint.api.CertificationBuilder;
import sailpoint.api.CertificationBuilderFactory;
import sailpoint.api.CertificationScheduler;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Identity;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

/**
 * Certification creation utility useful for Tests and
 * demo certification creation.
 */
public class CertCreationHelper {

    public static class SimpleCertDef{
        Identity launcher;
        Identity owner;
        String certName;
        Certification.Type type;
        boolean flatten;
        boolean global;
        Certification.EntitlementGranularity granularity;
        String managerName;
        List<String> applicationIds;
        List<String> roleIds;
        Attributes<String, Object> attributes;
        CertificationDefinition.CertifierSelectionType certifierType;

        public SimpleCertDef(String manager, Certification.EntitlementGranularity granularity) {
            this.type = Certification.Type.Manager;
            this.global = false;
            managerName = manager;
            this.granularity = granularity;
        }

        public SimpleCertDef(String manager, Certification.EntitlementGranularity granularity,
                             boolean enableSubordinateCerts, boolean flatten, boolean completeHierarchy ) {
            this(manager, granularity);
            attributes = new Attributes<String, Object>();
            attributes.put(CertificationDefinition.ARG_SUBORDINATE_CERTIFICATION_ENABLED, enableSubordinateCerts);
            attributes.put(CertificationDefinition.ARG_COMPLETE_CERTIFICATION_HIERARCHY_ENABLED, completeHierarchy);
            attributes.put(CertificationDefinition.ARG_FLATTEN_MANAGER_CERTIFICATION_HIERARCHY_ENABLED, flatten);
        }

        public SimpleCertDef(Certification.Type type, List<String> items,
                             Certification.EntitlementGranularity granularity) {
            this(null, type, false, granularity);

            if (Certification.Type.ApplicationOwner.equals(type)
                    || Certification.Type.AccountGroupPermissions.equals(type)
                    || Certification.Type.AccountGroupMembership.equals(type))
                this.applicationIds = items;
            else if (Certification.Type.BusinessRoleComposition.equals(type) ||
                    Certification.Type.BusinessRoleMembership.equals(type) )
                this.roleIds = items;
            else if (Certification.Type.DataOwner.equals(type))
                this.applicationIds = items;
            else
                throw new RuntimeException("Unhandled certification type.");

        }

        public SimpleCertDef(Certification.Type type, List<String> items,
                             Certification.EntitlementGranularity granularity,
                             CertificationDefinition.CertifierSelectionType certifierType) {
            this(type, items, granularity);
            this.certifierType = certifierType;
        }

        public SimpleCertDef(Certification.Type type, Certification.EntitlementGranularity granularity,
                             CertificationDefinition.CertifierSelectionType certifierType) {
            this.type = type;
            this.global = true;
            this.granularity = granularity;
            this.certifierType = certifierType;
        }

        public SimpleCertDef(Identity launcher, Certification.Type type, boolean global) {
            this.type = type;
            this.global = global;
            this.launcher = launcher;
        }

        public SimpleCertDef(Identity owner, Certification.Type type, String certName, boolean global, boolean flatten) {
            this.type = type;
            this.global = global;
            // make both launcher and owner the owner
            this.launcher = owner;
            this.owner = owner;
            this.certName = certName;
            this.flatten = flatten;
        }

        public SimpleCertDef(Identity launcher, String manager) {
            this.type = Certification.Type.Manager;
            this.global = false;
            managerName = manager;
            this.launcher = launcher;
        }

        public SimpleCertDef(Identity launcher, Certification.Type type, boolean global,
                             Certification.EntitlementGranularity granularity) {
            this(launcher, type, global);
            this.granularity = granularity;
        }

         public SimpleCertDef(Certification.Type type, boolean global,
                             Certification.EntitlementGranularity granularity) {
            this(null, type, global);
            this.granularity = granularity;
        }

        public SimpleCertDef(Identity launcher, Certification.Type type, boolean global,
                             Certification.EntitlementGranularity granularity, Attributes<String, Object> attrs) {
            this(launcher, type, global, granularity);
            this.attributes = attrs;
        }

        public CertificationDefinition toCertDefinition(SailPointContext context) throws GeneralException {
            return createCertDefinition(context, this);
        }
    }

    public static TaskSchedule createSchedule(SailPointContext context, SimpleCertDef def) throws GeneralException{
        CertificationDefinition definition = createCertDefinition(context, def);
        CertificationScheduler scheduler = new CertificationScheduler(context);
        CertificationSchedule schedule = new CertificationSchedule(context, def.launcher, definition);
        schedule.setRunNow(true);

        return scheduler.saveSchedule(schedule, false);
    }


    public static CertificationBuilder getBuilder(SailPointContext context, SimpleCertDef def) throws GeneralException{
        CertificationDefinition definition = def.toCertDefinition(context);
        CertificationBuilderFactory factory = new CertificationBuilderFactory(context);
        return factory.getBuilder(definition);
    }

    public static CertificationDefinition createCertDefinition(SailPointContext ctx, SimpleCertDef def) throws GeneralException{

        // Scheduler performs some initialization by cert type we need here.
        CertificationScheduler scheduler = new CertificationScheduler(ctx);
        CertificationSchedule schedule = scheduler.initializeScheduleBean(null, def.type);

        CertificationDefinition definition = schedule.getDefinition();
        definition.mergeAttributes(def.attributes);
        definition.setGlobal(def.global);
        if (def.granularity != null)
            definition.setEntitlementGranularity(def.granularity);
        definition.setCertifierName(def.managerName);
        definition.setApplicationIds(ObjectUtil.convertToNames(ctx, Application.class, def.applicationIds));
        definition.setBusinessRoleIds(ObjectUtil.convertToNames(ctx, Bundle.class, def.roleIds));
        definition.setCertifierSelectionType(def.certifierType);
        
        if (def.owner != null) {
            definition.setOwner(def.owner);
            definition.setCertificationOwner(def.owner);
        }

        if (def.certName != null) {
            definition.setCertificationNameTemplate(def.certName);
        }

        if (def.flatten) {
            definition.setIsSubordinateCertificationEnabled(false);
            definition.setFlattenManagerCertificationHierarchy(true);
        }

        // set name so that it can be saved
        String name = ObjectUtil.generateUniqueName(ctx, null, definition.getName() + " [" + new Date().toString() + "]",
        CertificationDefinition.class, 0);
        definition.setName(name);
        
        return definition;
    }

}
