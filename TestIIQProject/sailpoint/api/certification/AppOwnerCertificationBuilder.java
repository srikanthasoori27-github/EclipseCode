/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.CertificationContext;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * A certification builder that generates CertificationContexts for app owners
 * to certify access for users on their application.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class AppOwnerCertificationBuilder extends BaseIdentityCertificationBuilder {

    public static final String RESULT_APPLICATION = "application";

    /**
     * Constructor.  If global is true, the app is ignored.
     * 
     * @param  ctx             The SailPointContext to use.
     */
    public AppOwnerCertificationBuilder(SailPointContext ctx, CertificationDefinition definition, EntitlementCorrelator correlator) {
        super(ctx, definition, correlator);
    }
    
    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContext(Certification)
     */
    public CertificationContext getContext(Certification cert) throws GeneralException {

        Application app = cert.getApplication(this.context);
        if (null == app) {
            throw new GeneralException("Could not get application off certification: " + cert);
        }

        return newAppOwnerContext(app);
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContexts()
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException {

        List<String> appIds = ObjectUtil.convertToIds(context, Application.class, definition.getApplicationIds());
        assert (this.global || ((null != appIds) && !appIds.isEmpty())) :
            "Expect either global or application IDs";

        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        if (this.global) {
            // Require an owner.  Maybe just secondary owners would be alright,
            // but getOwnersInternal() currently asserts that there is a
            // non-null owner.
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.notnull("owner"));
            
            Identity defOwner = definition.getOwner();
            try {
                // When we do global app certs, we need to make sure that the apps
                // applied to the cert are within the cert def owner's scope, so impersonate
                context.impersonate(defOwner);
                context.setScopeResults(true);
                
                qo.addOwnerScope(defOwner);
    
                List<Application> apps = context.getObjects(Application.class, qo);
                for (Application currentApp : apps) {
                    CertificationContext ctx = newAppOwnerContext(currentApp);
                    if (null != ctx) {
                        ctxs.add(ctx);
                        super.addResult(RESULT_APPLICATION, currentApp.getName());
                    }
                }
            } finally {
                // If anything bad should happen or we're done, make sure we reveal our true identity before going on.
                context.impersonate(null);
                context.setScopeResults(false);
            }
        }
        else {
            for (String appId : appIds) {
                Application app = context.getObjectById(Application.class, appId);
                CertificationContext ctx = newAppOwnerContext(app);
                if (null != ctx) {
                    ctxs.add(ctx);
                    super.addResult(RESULT_APPLICATION, app.getName());
                }
            }
        }

        // TODO: Consider creating the contexts on demand rather than just
        // returning an iterator over the list.  Probably not a huge deal unless
        // there are LOTS of apps.
        return ctxs.iterator();
    }

    /**
     * Create an AppOwnerCertificationContext that can generate a certification
     * for the given application.  This is pretty hacky, but we need to expose a
     * way to request a context for a particular application.  The only thing
     * that should call this externally is the CertificationBuilderFactory.
     */
    public CertificationContext newAppOwnerContext(Application app) {

        CertificationContext ctx = null;

        // Only create a context for this app if there are owners of the cert
        // and the application is configured for entitlements.
        if (checkForOwner(app) && checkForEntitlements(app)) {
            ctx = new AppOwnerCertificationContext(context, definition, this.owners, app);
            ctx.setCertificationGroups(getCertificationGroups());
        }

        return ctx;
    }

    /**
     * Check that there is an owner for the certification.  If there is not,
     * this adds a warning and returns false.
     */
    private boolean checkForOwner(Application app) {
        boolean hasOwner =
            (null != app.getOwner()) ||
            ((null != this.owners) && !this.owners.isEmpty());

        if (!hasOwner) {
            super.addWarning(new Message(MessageKeys.WARN_NO_CERTIFIER_FOR_APP, app.getName()));
        }

        return hasOwner;
    }

    /**
     * Check that the given application has entitlement attributes or
     * permissions configured on the account schema.  If not, this adds a
     * warning and returns false.
     */
    private boolean checkForEntitlements(Application app) {
        
        if (definition.isCertifyEmptyAccounts() || definition.isCertifyAccounts()) { return true;}
        
        boolean foundEntitlements = false;

        Schema s = app.getAccountSchema();
        if (null != s) {

            foundEntitlements = s.getIncludePermissions();

            if (!foundEntitlements && (null != s.getAttributes())) {
                for (AttributeDefinition attr : s.getAttributes()) {
                    if (attr.isEntitlement()) {
                        foundEntitlements = true;
                        break;
                    }
                }
            }
        }
        
        if (!foundEntitlements) {
            super.addWarning(new Message(MessageKeys.WARN_NO_ENTITLEMENTS_FOR_APP, app.getName()));
        }
        
        return foundEntitlements;
    }
    
    /**
     * The CertificationContext to return information when building application
     * owner certifications.
     */
    public class AppOwnerCertificationContext 
        extends BaseIdentityCertificationBuilder.BaseIdentityCertificationContext {

        private Application app;

        /**
         * Constructor.
         * 
         * @param  app     The Application for which to build the certification.
         * @param  owners  The owners for the this certification.  Defaults to
         *                 the application owner if null.
         */
        public AppOwnerCertificationContext(SailPointContext context, CertificationDefinition definition,
                                            List<Identity> owners, Application app) {
            super(context, definition, owners);
            this.app = app;

            // Load the app fully so we don't get a lazy initialization
            // exception if we later decache.
            this.app.load();
        }

        @Override
        public boolean isIncludeAssignedRoles() {
            return false;
        }

        @Override
        protected List<Identity> getOwnersInternal() {
            Identity owner = this.app.getOwner();
            assert (null != owner) : "Application owner is required for an app owner cert.";
            List<Identity> owners = new ArrayList<Identity>();
            owners.add(owner);
            return owners;
        }

        public Iterator<Identity> getPopulation() throws GeneralException {

            QueryOptions ops = new QueryOptions();
            ops.setDistinct(true);
            ops.add(Filter.eq("links.application", this.app));
            ops.addOrdering("name", true);

            // old way
            //return context.search(Identity.class, ops);

            return new IncrementalIdentityIterator(context, ops);
        }

        public boolean inPopulation(AbstractCertifiableEntity entity) throws GeneralException{
            Identity identity = (Identity) entity;
            IdentityService identSvc = new IdentityService(ctxt);
            return identSvc.countLinks(identity, this.app) > 0;
        }
        
        @Override
        public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {

            assert (null != this.app) : "Application not specified.";
            assert ((null == getIncludedApplicationIds()) || getIncludedApplicationIds().isEmpty())
                : "Cannot filter by applications in an application owner certification."; 

            List<Application> apps = new ArrayList<Application>();
            apps.add(this.app);
            return super.getCertifiables(entity, apps);
        }

        /**
         *
         * Name is localized for the server default locale and timezone.
         *
         * @return
         * @throws GeneralException
         */
        String generateDefaultName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_NAME_GENERIC,
                    new Message(getType().getMessageKey()), app.getName());
            return name.getLocalizedMessage();
        }

        /**
         *
         * Name is localized for the server default locale and timezone.
         *
         * @return
         * @throws GeneralException
         */
        String generateDefaultShortName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_SHORTNAME_GENERIC, app.getName());
            return name.getLocalizedMessage();
        }

        @Override
        protected void addNameParameters(CertificationNamer namer){
             namer.addParameter(CertificationNamer.NAME_TEMPLATE_APP, this.app.getName());
             namer.addParameter(CertificationNamer.NAME_TEMPLATE_GLOBAL, global);
        }
        
        @Override
        public void storeContext(Certification cert) throws GeneralException {
            super.storeContext(cert);
            cert.setApplicationId(this.app.getId());
        }
    }
}
