package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationContext;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Created by tapash.majumder on 10/9/14.
 * This class will contain common code between AccountGroupMembershipCertificationBuilder
 * and AccountGroupPermissionsCertificaitonBuilder
 */
public abstract class AccountGroupCertificationBuilder extends BaseCertificationBuilder {
    private static final Log log = LogFactory.getLog(AccountGroupCertificationBuilder.class);

    /**
     * Constructor.
     *
     * @param ctx        The SailPointContext to use for this builder.
     * @param definition The certification definition
     */
    protected AccountGroupCertificationBuilder(SailPointContext ctx, CertificationDefinition definition) {
        super(ctx, definition);
    }

    /**
    * Used to create UI messages which describe the type of entity
    * being certified.
    *
    *
    * @return The name of the entity type being certified.
    */
    public Message getEntityName(boolean plural){
        String key = plural ? MessageKeys.ACCOUNT_GROUPS_LCASE : MessageKeys.ACCOUNT_GROUP_LCASE;
        return new Message(key);
    }

    /* (non-Javadoc)
         * @see sailpoint.api.CertificationBuilder#getContext(Certification)
         */
    public CertificationContext getContext(Certification cert) throws GeneralException {

        // We don't store the necessary info now, but account group certs aren't
        // reactive either so we will just throw.
        throw new GeneralException("getContext(Certification) not yet supported - " +
                                   "context needs to implement storeContext().  This " +
                                   "is only needed for reactive certifications.");
    }

    /**
     * Creates the list of certification contexts for the specified application,
     * or if global for all applications.
     *
     * If the owners of the cert were specified when scheduling the cert, we'll get
     * all the groups for the app and create one big certification assigned to the
     * specified owners.
     *
     * If not, we'll create a certification for each group owner in the app, adding all
     * the groups he/she owns to his/her certification. Any groups which have no owner will
     * be added to a certification assigned to the app owner.
     *
     * @return
     * @throws GeneralException
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException {

        List<String> applicationIds = ObjectUtil.convertToIds(context, Application.class, definition.getApplicationIds());
        assert (this.global || (null != applicationIds && !applicationIds.isEmpty())) :
                "Expect either global or an application Id";

        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        List<Application> apps = null;
        if (global) {
            Identity defOwner = definition.getOwner();
            try {
                // When we do global certs, we need to make sure that the apps
                // applied to the cert are within the cert def owner's scope,
                // so impersonate
                context.impersonate(defOwner);
                context.setScopeResults(true);

                QueryOptions ops = new QueryOptions();
                ops.addOwnerScope(defOwner);

                apps = context.getObjects(Application.class, ops);
            } finally {
                // If anything bad should happen or we're done, make sure we
                // reveal our true identity before going on.
                context.impersonate(null);
                context.setScopeResults(false);
            }
        } else {
            apps = context.getObjects(Application.class, new QueryOptions(Filter.in("id", applicationIds)));
        }

        if ( apps != null ) {
            for ( Application app : apps ) {
                ctxs.addAll(createContextsByOwnerType(app));
            }
        }

        // set common properties
        if (ctxs != null){
            for(CertificationContext ctx : ctxs){
                ctx.setCertificationGroups(getCertificationGroups());
            }
        }

        // TODO: Consider creating the contexts on demand rather than just
        // returning an iterator over the list.
        return ctxs.iterator();
    }

    /**
     * Creates the list of certification contexts based on who the certifier
     * should be.
     *
     * If the owners of the cert were specified when scheduling the cert, we'll get
     * all the groups for the app and create one big certification assigned to the
     * specified owners.
     *
     * If not, we'll create a certification for each group owner in the app, adding all
     * the groups he/she owns to his/her certification. Any groups which have no owner will
     * be added to a certification assigned to the app owner.
     *
     * @param application The application
     * @return List of CertificationContext required to certify the specified app
     * @throws GeneralException
     */
    private List<CertificationContext> createContextsByOwnerType(Application application) throws GeneralException{
        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        List<Identity> certifiers = getOwners();
        if (certifiers != null && certifiers.size() > 0) {
            CertificationContext ctx = createContext(context, definition, certifiers, application.getName());
            ctx.setCertificationGroups(getCertificationGroups());
            ctxs.add(ctx);
        } else {
            ctxs = createGroupOwnersContexts(application);
        }

        return ctxs;
    }

    /**
     * Creates a certification context for each account group owner in the specified application.
     * If the app has groups with no owner, the cert will be assigned to the app owner. If the app owner
     * is null, a warning message is stored on the builder so the CertificationExecutor can post a message to
     * the user.
     *
     * @param application The sailpoint application object
     * @return  List of CertificationContexts for the group owners in the app
     * @throws GeneralException
     */
    private List<CertificationContext> createGroupOwnersContexts(Application application) throws GeneralException {
        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        List<Identity> groupOwners = getAccountGroupOwners(application);
        if (Util.isEmpty(groupOwners)) {
            addWarning(new Message(Message.Type.Warn,
                    MessageKeys.NOTHING_TO_CERTIFY, application.getName(), getEntityName(true)));
        }
        for ( Identity owner : groupOwners ) {
            if (owner != null) {
                owner.load();
                CertificationContext ctx = createContext(context, definition, Arrays.asList(owner), application.getName(), owner);
                ctx.setCertificationGroups(getCertificationGroups());
                ctxs.add(ctx);
            } else {

                // If the group owner is null, assign the cert to the app owner. If there's no app
                // owner, we'll post a warning and move on
                if (application.getOwner() != null) {
                    Identity appOwner = application.getOwner();
                    appOwner.load();
                    CertificationContext ctx = createContext(context, definition, Arrays.asList(application.getOwner()), application.getName(), null);
                    ctx.setCertificationGroups(getCertificationGroups());
                    ctxs.add(ctx);
                } else {
                    // only need to add this warning once since we've selected a distinct list of group owners
                    addWarning(new Message(MessageKeys.ACCT_GRP_CERT_NOT_CREATED, application.getName()));
                }
            }
        }

        return ctxs;
    }

    /**
     * Subclasses need to override the following two methods and return
     * a concrete implementation of CertificationContext.
     *
     */
    protected abstract AccountGroupCertificationContext createContext(
            SailPointContext context,
            CertificationDefinition definition,
            List<Identity> certifiers,
            String applicationName,
            Identity groupOwner);

    protected abstract AccountGroupCertificationContext createContext(
            SailPointContext context,
            CertificationDefinition definition,
            List<Identity> certifiers,
            String applicationName);

    /**
     * If the attribute is null in ManagedAttribute we consider those indirect group
     * membership. This method need to be overridden to mark as allowed or not.
     *
     * @return true to allow
     */
    protected abstract boolean isIndirectGroupAllowed();


    /**
     * Returns a list a list of all identies who own account groups within the given
     * application. If there are groups with no owner, one item in the list will be null.
     * The null item lets us know we need to create a certification assigned to the
     * app owner.
     *
     * @param application The sailpoint application object
     * @return Non-null list of identities
     * @throws GeneralException
     */
    private List<Identity> getAccountGroupOwners(Application application) throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();

        List<String> filteredTypes = getFilteredTypes(application);
        if (filteredTypes.size() > 0) {
            queryOptions.add(Filter.in("type", filteredTypes));
        }

        queryOptions.setDistinct(true);
        queryOptions.add(Filter.eq("application.name", application.getName()));
        queryOptions.setOrderBy("owner");

        addGroupFilter(application, queryOptions);

        List<Identity> groupOwners = new ArrayList<Identity>();

        // Do a search for the group owners. The first item in the array will be the owner identity
        Iterator<Object[]> results = context.search(ManagedAttribute.class, queryOptions, Arrays.asList("owner"));
        while (results.hasNext()) {
            Object[] row = results.next();
            groupOwners.add(row[0]!=null ? (Identity)row[0] : null);
        }

        return groupOwners;
    }

    /**
     * Return the types of ManagedAttribute this application that we are interested in.
     * @param application the application
     * @return list of managed attribute types
     */
    private List<String> getFilteredTypes(Application application) {
        List<String> types = new ArrayList<String>();
        if (Util.isEmpty(definition.getApplicationGroups())) {
            // short circuit
            return types;
        }

        for (CertificationDefinition.ApplicationGroup applicationGroup : definition.getApplicationGroups()) {
            if (application.getName().equals(applicationGroup.getApplicationName())) {
                types.add(applicationGroup.getSchemaObjectType());
            }
        }

        return types;
    }

    private void addGroupFilter(Application application, QueryOptions options) throws GeneralException {
        List<String> groupTypes = application.getGroupSchemaObjectTypes();
        if (!Util.isEmpty(groupTypes)) {
            options.add(Filter.in("type", application.getGroupSchemaObjectTypes()));
        } else {
            //Default to "group"
            options.add(Filter.eq("type", Application.SCHEMA_GROUP));
        }

        if (!isIndirectGroupAllowed()) {
            options.add(Filter.notnull("attribute"));
        }
    }

    /**
     * The CertificationContext to return information when building account
     * group membership certifications.
     */
    public abstract class AccountGroupCertificationContext
            extends BaseCertificationBuilder.BaseCertificationContext {

        protected Application application;
        protected Identity groupOwner;

        // Indicates that account groups should be certified if they are owner by
        // the identity specified by the groupOwner property. Note that if groupOwner==null
        // we mean to certify groups with no owner.
        protected boolean filterByOwner;

        /**
         * Creates a certification for the account groups belonging to the given owner and app.
         *
         * Not that if you set groupOwner to null, this will only create certifications for account
         * groups with NO owner. If you want all account groups regardless of owner use the following
         * constructor.
         *
         * @param context
         * @param applicationName The name of the application being certified
         * @param groupOwner Owner whose groups you want to certify
         * @param certifiers Identities doing the certifiying
         */
        public AccountGroupCertificationContext(SailPointContext context, CertificationDefinition definition,
                                                           List<Identity> certifiers, String applicationName,
                                                           Identity groupOwner) {
            super(context, definition, certifiers);
            try {
                this.application = context.getObjectByName(Application.class, applicationName);
            } catch(GeneralException ge) {
                if (log.isWarnEnabled())
                    log.warn("Unable to load application [" + applicationName + "]: " +
                            ge.getMessage(), ge);
            }
            this.groupOwner = groupOwner;
            filterByOwner = true;
        }


        /**
         *  Creates a certification for all account groups belonging the given Application.
         *
         * @param context
         * @param applicationName The name of the application being certified
         * @param certifiers Identities doing the certifiying
         */
        public AccountGroupCertificationContext(SailPointContext context,
                                                           CertificationDefinition definition,
                                                           List<Identity> certifiers, String applicationName) {
            super(context, definition, certifiers);
            try {
                this.application = context.getObjectByName(Application.class, applicationName);
            } catch(GeneralException ge) {
                if (log.isWarnEnabled())
                    log.warn("Unable to load application [" + applicationName + "]: " +
                            ge.getMessage(), ge);
            }
            filterByOwner = false;
        }

        @Override
        public boolean isIncludeAdditionalEntitlements() {
            return true;
        }

        @Override
        public boolean isIncludePolicyViolations() {
            return false;
        }

        @Override
        public boolean isIncludeBusinessRoles() {
            return false;
        }

        /**
         * Returns a list of owners for this context. This method is only called if the super.getOwners()
         * returns null. The owner should always be set in the constructor, so this method should not be
         * necessary, but it's here to be safe.
         *
         * @return List of owners for this context
         */
        @Override
        protected List<Identity> getOwnersInternal() {
            if (groupOwner != null){
                return Arrays.asList(groupOwner);
            } else if (this.application != null){
                if (this.application.getOwner() != null)
                    return Arrays.asList(this.application.getOwner());
                else
                    throw new RuntimeException("Could not find an owner for account group certification. Application was '"
                            + StringUtils.defaultString(this.application.getName()));
            }

            throw new RuntimeException("Could not find an owner for an account group certification." +
                    " Both the application and group owner were null");
        }

        /**
         * Create an CertificationEntity for an AccountGroup.  Return null if
         * the entity has no certifiable items.
         *
         * @param cert   The Certification for which we're creating the entity.
         * @param group  The AccountGroup for which to create a CertificationEntity.
         */
        protected CertificationEntity createCertificationEntityInternal(
                Certification cert,
                AbstractCertifiableEntity group,
                boolean snapshot)
                throws GeneralException {

            assert(group != null);

            if (!ManagedAttribute.class.isAssignableFrom(group.getClass()))
                throw new RuntimeException("Could not create an account group permissions certification entity with class of type '"
                        + group.getClass().getName() + "'");

            // Note - snapshotting is not implemented for account groups.

            return new CertificationEntity((ManagedAttribute) group);
        }

        public Iterator<ManagedAttribute> getPopulation() throws GeneralException {
            if (log.isDebugEnabled()) {
                log.debug("Fetching population (ManagedAttributes) for " +
                        this.application.getName() + " with owner " + groupOwner);
            }

            QueryOptions options = new QueryOptions();

            List<String> filteredTypes = getFilteredTypes(application);
            if (filteredTypes.size() > 0) {
                options.add(Filter.in("type", filteredTypes));
            }

            options.add(Filter.eq("application.name", this.application.getName()));
            addGroupFilter(application, options);

            if (filterByOwner){
                if (groupOwner == null) {
                    options.add(Filter.isnull("owner"));
                } else {
                    options.add(Filter.eq("owner", groupOwner));
                }
            }

            return new IncrementalObjectIterator<ManagedAttribute>(context, ManagedAttribute.class, options);
        }

        public boolean inPopulation(AbstractCertifiableEntity entity) {

            boolean inPopulation = false;
            ManagedAttribute accountGroup = (ManagedAttribute) entity;

            if (this.application.getName().equals(accountGroup.getApplication().getName())) {

                inPopulation = true;

                if (this.filterByOwner) {
                    inPopulation = Util.nullSafeEq(this.groupOwner, accountGroup.getOwner(), true);
                }
            }

            return inPopulation;
        }

        @Override
        public void storeContext(Certification cert) throws GeneralException {
            super.storeContext(cert);
            cert.setApplicationId(this.application.getId());
        }

        /**
         * Generate a long descriptive name that specifies what the cert is about and
         * why they were assigned the cert. If there's a group owner, add their name.
         * If the cert is being assigned to the app owner b/c there is no group owner,
         * note that the groups to certify had no owner.
         *
         * Name is localized for the server default locale and timezone.
         *
         * @return long description of the certification.
         * @throws GeneralException
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        String generateDefaultName() throws GeneralException {

            // default message key and message parameters
            String key = MessageKeys.CERT_NAME_GENERIC;
            List params = new ArrayList();
            params.add(new Message(getType().getMessageKey()));
            params.add(this.application.getName());

            // If the cert was built for the owner of a set of account groups include thier name.
            // If there is not a group owner, use the certifier's name (if there is a single certifier).
            if (this.filterByOwner && groupOwner!= null){
                key = MessageKeys.CERT_NAME_ACT_GRP_OWNER;
                params.add(groupOwner.getName());
            } else if (1 == this.getOwners().size()) {
                key = MessageKeys.CERT_NAME_ACT_GRP;
                params.add(this.getOwners().get(0).getDisplayableName());
            }

            Message name = new Message(key, params.toArray());
            return  name.getLocalizedMessage();
        }

        /**
         * Creates a short name for the cert which will be used in places like
         * the dashboard where space is a concern.
         *
         * Name is localized for the server default locale and timezone.
         *
         * @return descriptive short name of the certification
         * @throws GeneralException
         */
        String generateDefaultShortName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_SHORTNAME_ACT_GRP, this.application.getName());
            return name.getLocalizedMessage();
        }

        @Override
        protected void addNameParameters(CertificationNamer namer){
            namer.addIdentity(groupOwner, CertificationNamer.NAME_TEMPLATE_GROUP_OWNER);
            namer.addParameter(CertificationNamer.NAME_TEMPLATE_FILTER_BY_OWNER, this.filterByOwner);
            namer.addParameter(CertificationNamer.NAME_TEMPLATE_APP, application.getName());
            namer.addParameter(CertificationNamer.NAME_TEMPLATE_GLOBAL, global);
        }
    }
}
