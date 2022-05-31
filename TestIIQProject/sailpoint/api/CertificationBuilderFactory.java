/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;



import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.AccountGroupMembershipCertificationBuilder;
import sailpoint.api.certification.AccountGroupPermissionsCertificationBuilder;
import sailpoint.api.certification.AppOwnerCertificationBuilder;
import sailpoint.api.certification.BusinessRoleCompositionCertificationBuilder;
import sailpoint.api.certification.BusinessRoleMembershipCertificationBuilder;
import sailpoint.api.certification.CertificationHelper;
import sailpoint.api.certification.DataOwnerCertificationBuilder;
import sailpoint.api.certification.GroupCertificationBuilder;
import sailpoint.api.certification.IdentityCertificationBuilder;
import sailpoint.api.certification.ManagerCertificationBuilder;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A factory that can return a CertificationBuilder created from the attributes
 * in a task schedule, or CertificationContext for an existing certification.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationBuilderFactory {

    private static final Log log = LogFactory.getLog(CertificationBuilderFactory.class);


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public CertificationBuilderFactory(SailPointContext context) {
        this.context = context;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    public CertificationBuilder getBuilder(CertificationDefinition definition, TaskSchedule schedule)
        throws GeneralException {
        return getBuilder(definition, null, null, null, schedule, null, null);
    }
    
    public CertificationBuilder getBuilder(CertificationDefinition definition, TaskSchedule schedule, EntitlementCorrelator correlator)
            throws GeneralException {
        return getBuilder(definition, null, null, null, schedule, null, correlator);
    }

    public CertificationBuilder getBuilder(CertificationDefinition definition)
        throws GeneralException {
        return getBuilder(definition, null, null, null, null, null, null);
    }

    public CertificationBuilder getBuilder(CertificationDefinition def, TaskSchedule schedule, EntitlementCorrelator ec, Identity toCertify)
        throws GeneralException {
        return getBuilder(def, null, toCertify, null, schedule, null, ec);
    }


    /**
     * Create a CertificationBuilder using the given IdentityTrigger and the
     * identity that caused the trigger to fire.  Note that the Identity may
     * be a transient copy of an identity.
     */
    public CertificationBuilder getBuilder(IdentityTrigger trigger,
                                           Identity toCertify,
                                           List<Identity> certifiers)
        throws GeneralException {

        CertificationDefinition def = trigger.getCertificationDefinition(context);

        // Clone the definition so the certification can have it's own copy and
        // not affect the original copied from the trigger
        CertificationDefinition clone = (CertificationDefinition)def.derive(context);

        // Make sure the clone knows the source trigger ID
        clone.setTriggerId(trigger.getId());

        String name = ObjectUtil.generateUniqueName(context, null, def.getName() + " for " + toCertify.getName() + " [" + new Date().toString() + "]",
                CertificationDefinition.class, 0);
        clone.setName(name);
        context.saveObject(clone);
        context.commitTransaction();

        QueryOptions ops = new QueryOptions(Filter.eq("definition", def));

        List<CertificationGroup> groups = context.getObjects(CertificationGroup.class, ops);
        return getBuilder(clone, trigger, toCertify, certifiers, null, groups, null);
    }

    /**
     * Convenience method to quickly build a manager certification.
     */
    public CertificationBuilder getManagerCertBuilder(Identity manager) throws GeneralException{
        CertificationDefinition def = new CertificationDefinition();
        def.initialize(context);

        def.setType(Certification.Type.Manager);
        def.setGlobal(false);
        def.setCertifierName(manager.getName());

        return getBuilder(def, null);
    }

    /**
     * Convenience method to quickly build an app owner certification.
     */
    public CertificationBuilder getAppOwnerCertBuilder(List<String> apps) throws GeneralException{
        CertificationDefinition def = new CertificationDefinition();
        def.initialize(context);

        def.setType(Certification.Type.ApplicationOwner);
        def.setGlobal(false);
        def.setApplicationIds(ObjectUtil.convertToNames(context, Application.class, apps));

        return getBuilder(def, null);
    }



    /**
     * Retrieve the CertificationContext used to create the given certification.
     */
    public CertificationContext getContext(Certification cert)
        throws GeneralException {

        return getContext(cert, null);
    }

    public CertificationContext getContext(Certification cert, EntitlementCorrelator correlator)
            throws GeneralException {

        CertificationDefinition def = cert.getCertificationDefinition(context);
        assert (null != def) : "Need a certification definition to be able to retrieve a context for a certification.";

        TaskSchedule sched = cert.getTaskSchedule(this.context);

        CertificationBuilder builder = getBuilder(def, sched, correlator);
        return builder.getContext(cert);
    }
    
    /**
     * Retrieve a CertificationContext that can build a manager certification
     * for the given manager.
     */
    public CertificationContext getManagerContext(TaskSchedule schedule, Identity manager)
        throws GeneralException {

        ManagerCertificationBuilder builder =
            (ManagerCertificationBuilder) getBuilder(schedule);
        return builder.newManagerContext(manager);
    }

    /**
     * Retrieve a CertificationContext that can build an application owner
     * certification for the given application.
     */
    public CertificationContext getAppOwnerContext(TaskSchedule schedule, Application app)
        throws GeneralException {

        AppOwnerCertificationBuilder builder =
            (AppOwnerCertificationBuilder) getBuilder(schedule);
        return builder.newAppOwnerContext(app);
    }

    public List<CertificationContext> getDataOwnerContexts(TaskSchedule schedule, CertificationDefinition def, Application app)
            throws GeneralException {

        DataOwnerCertificationBuilder builder = (DataOwnerCertificationBuilder) getBuilder(def, schedule);

        return builder.getContexts(app);
    }

    public List<CertificationContext> getDataOwnerContexts(TaskSchedule schedule, Application app)
            throws GeneralException {

        DataOwnerCertificationBuilder builder = (DataOwnerCertificationBuilder) getBuilder(schedule);

        return builder.getContexts(app);
    }    
    
    private CertificationBuilder getBuilder(TaskSchedule schedule) throws GeneralException{
        CertificationScheduler scheduler = new CertificationScheduler(context);
        CertificationDefinition definition = scheduler.getCertificationDefinition(schedule);
        if (definition == null)
            return null;

        return getBuilder(definition, schedule);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // STATIC METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Convenience method for quickly creating a builder.
     */
    public static CertificationBuilder getBuilder(SailPointContext context, CertificationDefinition definition,
                                                  List<CertificationGroup> groups)
        throws GeneralException {

        CertificationBuilderFactory factory = new CertificationBuilderFactory(context);
        return factory.getBuilder(definition, null, null, null, null, groups, null);
    }

    public static CertificationBuilder getBuilder(SailPointContext context, CertificationDefinition definition)
        throws GeneralException {
        return getBuilder(context, definition, null);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PRIVATE METHODS
    //
    ////////////////////////////////////////////////////////////////////////////


    /**
     * Create a CertificationBuilder using the given arguments from a
     * certification creation task.
     */
    private CertificationBuilder getBuilder(CertificationDefinition definition, IdentityTrigger trigger,
                                            Identity toCertify, List<Identity> certifiers, TaskSchedule taskSchedule,
                                            List<CertificationGroup>  groups, EntitlementCorrelator correlator)
        throws GeneralException {
        return createBuilder(definition, trigger, toCertify, certifiers, taskSchedule, groups, correlator);
    }



    /**
     * Create a CertificationBuilder that can build certifications for the Type
     * that was pulled from the arguments.
     */
    private CertificationBuilder createBuilder(CertificationDefinition definition, IdentityTrigger trigger, Identity toCertify,
                                           List<Identity> certifiers, TaskSchedule taskSchedule,
                                           List<CertificationGroup>  groups, EntitlementCorrelator correlator) throws GeneralException {

        CertificationBuilder builder = null;

        boolean global = definition.isGlobal();

        switch (definition.getType()) {
        case Manager:

            if (!global && (definition.getCertifierName() == null))
                throw new GeneralException("Missing certifier name");

            builder = new ManagerCertificationBuilder(context, definition, correlator);
            break;
        case ApplicationOwner:
            builder =
                new AppOwnerCertificationBuilder(context, definition, correlator);
            break;
        case DataOwner:
            builder = createDataOwnerCertificationBuilder(definition);
            break;
        case AccountGroupPermissions:
            builder =
                new AccountGroupPermissionsCertificationBuilder(context, definition);
            break;
        case AccountGroupMembership:
            builder =
                new AccountGroupMembershipCertificationBuilder(context, definition);

            break;
        case Identity:

            // If we couldn't find an identity manager map, we can assume that this
            // is a new individual cert and treat it accordingly
            List<String> ids = ObjectUtil.convertToIds(context, Identity.class, definition.getIdentitiesToCertify());
            Identity certifier = definition.getCertifier(context);
            CertificationDefinition.CertifierSelectionType certifierType =
                definition.getCertifierSelectionType();

            // Lazy upgrade - default selection type to Manual.
            certifierType =
                (null == certifierType) ? CertificationDefinition.CertifierSelectionType.Manual : certifierType;

            // If the identity to certify are explicitly set, use this and
            // the calculated certifier instead of the values from the
            // schedule.
            if (null != toCertify) {
                ids = new ArrayList<String>();
                ids.add(toCertify.getId());
                definition.setIdentitiesToCertify(ObjectUtil.convertToNames(context, Identity.class, ids));

                // Determine the certifier.  Do this now since we may have a
                // transient Identity (ie - the previous identity).
                certifier = calculateCertifier(toCertify, definition);
                if (certifier != null)
                    definition.setCertifierName(certifier.getName());

                // Change the certifier type to manual since we pre-calculated
                // the certifier.
                definition.setCertifierSelectionType(CertificationDefinition.CertifierSelectionType.Manual);
            }

            builder = new IdentityCertificationBuilder(context, definition, correlator);

            break;

        // Creating an advanced certification based on one or more iPOPs.
        case Group:
            builder = new GroupCertificationBuilder(context, definition, correlator);
            break;
        case BusinessRoleMembership:
            builder = new BusinessRoleMembershipCertificationBuilder(context, definition, correlator);
            break;
        case BusinessRoleComposition:
            builder = new BusinessRoleCompositionCertificationBuilder(context, definition);
            break;
        default:
            throw new GeneralException("Unhandled certification type: " + definition.getType());
        }

        setCommonOptions(builder, definition, trigger, certifiers, taskSchedule);

        if (groups == null || groups.isEmpty()){
            // If we're dealing with an IdentityTrigger, we want to store the
            // original definition on the CertificationGroup rather than the clone
            // that has been created for the new Certifications
            CertificationDefinition triggerDefinition = null;
            if (trigger != null){
                triggerDefinition = trigger.getCertificationDefinition(context);
            }
            this.initializeCertificationGroups(builder, triggerDefinition != null ? triggerDefinition : definition, taskSchedule);
        } else {
            builder.setCertificationGroups(groups);
        }

        return builder;
    }

    private CertificationBuilder createDataOwnerCertificationBuilder(CertificationDefinition definition)
        throws GeneralException {
        return new DataOwnerCertificationBuilder(context, definition);
    }

    /**
     * Calculate the certifier to use for an individual certification based on
     * the certifier selection type in the schedule.
     *
     * @param  toCertify  The Identity being certified in the individual cert.
     */
    private Identity calculateCertifier(Identity toCertify, CertificationDefinition definition)
        throws GeneralException {

        Identity certifier = null;

        CertificationDefinition.CertifierSelectionType type =
            definition.getCertifierSelectionType();
        if (null == type) {
            throw new GeneralException("No certifier type specified.");
        }

        switch (type) {
        case Manager: certifier = toCertify.getManager(); break;
        case Manual: certifier = definition.getCertifier(context); break;
        default:
            throw new GeneralException("Unhandled certifier type: " + type);
        }

        // If the identity doesn't have a certifier yet, try to default to the
        // certifier on the schedule.
        if (null == certifier) {
            certifier = definition.getCertifier(context);
        }

        return certifier;
    }



    /**
     * Creates a CertificationGroup which will contain the certifications
     * generated by this builder..
     */
    private void initializeCertificationGroups(CertificationBuilder builder, CertificationDefinition definition, TaskSchedule taskSchedule) throws GeneralException{

        String name = builder.getCertificationNamer().render(definition.getCertificationNameTemplate());
        
        CertificationHelper.Input input = new CertificationHelper.Input();
        input.setCertificationDefinition(definition);
        input.setContext(context);
        CertificationHelper helper = new CertificationHelper(input);
        CertificationGroup certificationGroup = helper.createCertificationGroup(name, taskSchedule);
        if (certificationGroup != null) {
            List<CertificationGroup> groupList = new ArrayList<CertificationGroup>();
            groupList.add(certificationGroup);
            builder.setCertificationGroups(groupList);
        }
    }

    /**
     * Set some common options on the given CertificationBuilder.
     */
    private void setCommonOptions(CertificationBuilder builder, CertificationDefinition definition,
                                  IdentityTrigger trigger, List<Identity> certifiers, TaskSchedule taskSchedule)
        throws GeneralException {

        List<Identity> owners = getBuilderOwners(definition, certifiers);
        builder.setOwners(owners);
        if (taskSchedule != null)
            builder.setTaskScheduleId(taskSchedule.getId());
    }



    private List<Identity> getBuilderOwners(CertificationDefinition definition, List<Identity> owners) throws GeneralException {

        // Only use the owners from the schedule if they weren't calculated at
        // runtime and specified with the certifiers field.
        if ((null == owners) && !Util.isEmpty(definition.getOwnerIds())) {
            owners = definition.getOwners(context);
        }

        return owners;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List> groupIdentsByManager(Map<String, String> identMgrMap) {
        Map<String, List> managerIdents = null;
        if(identMgrMap!=null) {
            managerIdents = new HashMap<String, List>();
            Iterator keys = identMgrMap.keySet().iterator();
            while(keys.hasNext()){
                String ident = keys.next().toString();
                String mgr = identMgrMap.get(ident);

                List<String> idents = managerIdents.get(mgr);
                if(idents==null) {
                    idents = new ArrayList<String>();
                }
                idents.add(ident);
                managerIdents.put(mgr, idents);
            }
        }

        return managerIdents;
    }

}
