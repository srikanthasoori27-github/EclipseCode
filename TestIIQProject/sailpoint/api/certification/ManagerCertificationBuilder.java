/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationContext;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.ManagerCertificationHelper;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * A certification builder that can generate manager certifications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ManagerCertificationBuilder extends BaseIdentityCertificationBuilder {

    private static final Log log = LogFactory.getLog(ManagerCertificationBuilder.class);

    public static final String RESULT_MANAGER = "manager";

    private Identity manager;

    private boolean generateSubordinateCerts;
    private boolean flatten;
    private boolean completeHierarchy;


    /**
     * Constructor.
     * @param  ctx      The SailPointContext to use.
     * @param  definition  The certification definition
     */
    public ManagerCertificationBuilder(SailPointContext ctx, CertificationDefinition definition, EntitlementCorrelator correlator)
        throws GeneralException {

        super(ctx, definition, correlator);

        if (!global){
            this.manager =  definition.getCertifier(context);
            if (this.manager == null)
                throw new GeneralException("Non-global manager certification did not specify the certifier");
        }

        this.generateSubordinateCerts = definition.isSubordinateCertificationEnabled();
        this.flatten = definition.isFlattenManagerCertificationHierarchy();
        this.completeHierarchy = definition.isCompleteCertificationHierarchy();
    }
    
    public PartitionedManagerCertificationContext getPartitionedContext(Identity manager)  throws GeneralException {
        
        return new PartitionedManagerCertificationContext(context, definition, owners, manager);
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContext(Certification)
     */
    public CertificationContext getContext(Certification cert) throws GeneralException {

        Identity manager = cert.getManager(this.context);
        if (null == manager) {
            throw new GeneralException("Could not get manager off certification: " + cert);
        }

        return newManagerContext(manager);
    }
    
    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContexts()
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException {

        assert (this.global || (null != this.manager)) : "Expect either global or a manager";

        Iterator<CertificationContext> it = null;

        if (this.global) {
            Identity defOwner = definition.getOwner();
            try {
                // When we do global mgr certs, we need to make sure that the mgr
                // applied to the cert are within the cert def owner's scope, 
                // so impersonate
                context.impersonate(defOwner);
                context.setScopeResults(true);
            
                QueryOptions qo = new QueryOptions();
                qo.addOwnerScope(defOwner);
                
                /* 
                 * If subordinate certifications are disabled:
                 * 1. If flatten hierarchy is not enabled get all the managers
                 * 2. If flatten hierarchy is enabled only get the top level managers
                 *    because we are flattening everything to them and generating certs
                 *    only for them.
                 * Otherwise:  
                 * Get only the top-level ones because the other managers 
                 * will have been included as subordinates.
                 */
                String selectProperty = "id";
                if (this.generateSubordinateCerts || this.flatten) {
                    ManagerCertificationHelper.addTopLevelManagerFilters(qo);
                }
                else {
                    // Optimization - if we're filtering by application, select only
                    // managers that have subordinates on at least one of the requested
                    // applications.  This prevents us from trying to create lots of
                    // empty certs when there are a large number of managers.
                    if ((null != this.includedAppIds) &&
                        !this.includedAppIds.isEmpty()) {
    
                        List<Filter> appConds = new ArrayList<Filter>();
                        for (String app : this.includedAppIds) {
                            appConds.add(Filter.eq("links.application.id", app));
                        }
                        qo.add(Filter.or(appConds));
                        qo.add(Filter.eq("manager.managerStatus", true));
                        qo.setDistinct(true);
                        
                        selectProperty = "manager.id";
                    }
                    else {
                        qo.add(Filter.eq("managerStatus", true));
                    }
                }
    
                // This will lazily load the manager Identities and create the
                // CertificationContexts on demand.
                it = new ManagerCertificationContextIterator(qo, selectProperty);
            } finally {
                // If anything bad should happen or we're done, make sure we reveal our true identity before going on.
                context.impersonate(null);
                context.setScopeResults(false);
            }
        }
        else {
            List<CertificationContext> ctxs = new ArrayList<CertificationContext>();
            ctxs.add(newManagerContext(this.manager));
            super.addOwnerResult(RESULT_MANAGER, this.manager);
            it = ctxs.iterator();
        }


        return it;
    }

    /**
     * Create a ManagerCertificationContext that can generate a certification
     * for the given manager.  This is pretty hacky, but we need to expose a way
     * to request a ManagerCertificationContext for a particular manager.  The
     * only thing that should call this externally is the
     * CertificationBuilderFactory.
     */
    public CertificationContext newManagerContext(Identity manager) {
        ManagerCertificationContext ctx = new ManagerCertificationContext(context, definition, this.owners, manager);
        ctx.setCertificationGroups(getCertificationGroups());

        return ctx;
    }
    
    /**
     * An iterator that will build and return ManagerCertificationContexts.
     */
    private class ManagerCertificationContextIterator
        implements Iterator<CertificationContext> {
        
        private Iterator<Identity> managerIt;

        public ManagerCertificationContextIterator(QueryOptions qo, String selectProperty) {
            this.managerIt = new IncrementalIdentityIterator(context, qo, selectProperty);
        }

        public boolean hasNext() {
            return this.managerIt.hasNext();
        }

        public CertificationContext next() {
            Identity manager = this.managerIt.next();
            addOwnerResult(RESULT_MANAGER, manager);
            return newManagerContext(manager);
        }

        public void remove() {
            throw new UnsupportedOperationException("remove() not implemented");
        }
    }

    /**
     * CertificationContext for building a manager certification.
     */
    public class ManagerCertificationContext
        extends BaseIdentityCertificationBuilder.BaseIdentityCertificationContext{

        private Identity manager;

        /**
         * Constructor.
         *
         * @param  owners   The owners of the certification.  Defaults to the
         *                  specified manager if this is null.
         */
        public ManagerCertificationContext(SailPointContext context, CertificationDefinition definition,
                                           List<Identity> owners, Identity manager) {
            super(context, definition, owners);
            this.manager = manager;
        }

        /**
         * Gets the population of entities that belong to this certification.
         *  
         * @return Iterator on a collection of certifiable entities.
         * @throws GeneralException
         */
        public Iterator<? extends AbstractCertifiableEntity> getPopulation() throws GeneralException {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("manager", manager));
            ops.setOrderBy("name");

            return new IncrementalIdentityIterator(context, ops, flatten);
        }

        public boolean inPopulation(AbstractCertifiableEntity entity) {
            Identity identity = (Identity) entity;
            return this.manager.equals(identity.getManager());
        }
        
        @Override
        protected List<Identity> getOwnersInternal() {
            List<Identity> owners = new ArrayList<Identity>();
            owners.add(this.manager);
            return owners;
        }

        public List<CertificationContext> getSubordinateContexts(AbstractCertifiableEntity entity)
            throws GeneralException {

            Identity identity = (Identity)entity;

            List<CertificationContext> subCtxs = new ArrayList<CertificationContext>();
            
            // TODO: Perform deeper circular reference checking.  We're currently
            // just making sure that a person doesn't manage themselves.
            if (generateSubordinateCerts && identity.getManagerStatus()) {
                // Obviously, don't generate a sub-cert for an identity that manages themselves.
                if (!this.manager.equals(identity)) {
                    CertificationContext ctx = new ManagerCertificationContext(context, definition, null, identity);
                    ctx.setCertificationGroups(this.getCertificationGroups());
                    subCtxs.add(ctx);
                }
            }

            return subCtxs;
        }

        private List<Certification> removeInactiveCerts(List<Certification> certs)
                throws GeneralException {

            if (null != certs) {
                for (Iterator<Certification> it = certs.iterator(); it
                        .hasNext();) {
                    Certification cert = it.next();

                    // For now, inactive will mean that the task schedule no 
                    // longer exists. When continuous certs are deactivated, 
                    // the schedule is deleted.
                    TaskSchedule sched = cert.getTaskSchedule(this.ctxt);
                    if (null == sched) {
                        if (log.isDebugEnabled())
                            log.debug("Not processing inactive certification: " + 
                                      cert.getName());
                        
                        it.remove();
                    }
                }
            }

            return certs;
        }

        String generateDefaultName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_NAME_GENERIC,
                    new Message(definition.getType().getMessageKey()), getName(this.manager));
            return name.getLocalizedMessage();
        }

        String generateDefaultShortName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_SHORTNAME_IDENTITY, getName(this.manager));
            return name.getLocalizedMessage();
        }

        @Override
        protected void addNameParameters(CertificationNamer namer){
            namer.addIdentity(manager, CertificationNamer.NAME_TEMPLATE_MANAGER_PREFIX);
            namer.addParameter(CertificationNamer.NAME_TEMPLATE_GLOBAL, global);
        }

        @Override
        public void storeContext(Certification cert) throws GeneralException {
            super.storeContext(cert);
            cert.setManager(this.manager);
            cert.setCompleteHierarchy(completeHierarchy);
        }
    }
    
    public class PartitionedManagerCertificationContext extends ManagerCertificationContext {

        public PartitionedManagerCertificationContext(SailPointContext context, CertificationDefinition definition, List<Identity> owners, Identity manager) {

            super(context, definition, owners, manager);
        }

        /**
         * No subordinate contexts for partioned certs.
         */
        @Override
        public List<CertificationContext> getSubordinateContexts(AbstractCertifiableEntity entity) throws GeneralException {
        
            return Collections.emptyList();
        }
    }
}
