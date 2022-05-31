/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.CertificationContext;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A CertificationBuilder that can build identity certifications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityCertificationBuilder extends BaseIdentityCertificationBuilder {

    /**
     * The type of certifier to use.
     */
    private CertificationDefinition.CertifierSelectionType certifierType;

    /**
     * The certifier to use (if the certifier type is manual) or the default
     * certifier to use (if the certifier type is manual and an identity doesn't
     * have a manager).  Usually, the UI will specify a list of owners rather
     * than a certifier if the certifier type is manual.
     */
    private String defaultCertifier;

    /**
     * The IDs of the identities to certify.
     */
    private List<String> identityIds;


    /**
     * Constructor
     * @param ctx              The SailPointContext to use.
     */
    public IdentityCertificationBuilder(SailPointContext ctx, CertificationDefinition definition, EntitlementCorrelator correlator) {
        super(ctx, definition, correlator);
        this.defaultCertifier = definition.getCertifierName();
        this.certifierType = definition.getCertifierSelectionType();
        this.identityIds = ObjectUtil.convertToIds(ctx, Identity.class, definition.getIdentitiesToCertify());
        try{
            this.owners = definition.getOwners(ctx);
        } catch(GeneralException e){
            throw new RuntimeException("Could not retrieve owners by ID", e);
        }
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContext(Certification)
     */
    public CertificationContext getContext(Certification cert) throws GeneralException {

        // Use the certifier as the owner if there is a single certifier.
        // Otherwise, the context will just use "owners".
        List<Identity> ownerList = this.getOwners();
        List<String> certifiers = cert.getCertifiers();
        if ((null != certifiers) && (1 == certifiers.size())) {
            ownerList = this.context.getObjects(Identity.class, new QueryOptions(Filter.eq("name", certifiers.get(0))));
        }
        
        // Dig the population out of the entities.  This is not great, but we
        // need this to check if an entity should be in the population anymore.
        // It would be nice to make this list lazily initialized so that we
        // don't have to iterate over the entire cert unless it's going to be
        // used, but we can fix this later if it becomes a problem.
        List<String> identityIds = new ArrayList<String>();
        for (CertificationEntity entity : cert.getEntities()) {
            Identity identity = entity.getIdentity(context);
            if (null != identity) {
                identityIds.add(identity.getId());
            }
        }

        return newContext(this.context, ownerList, identityIds);
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContexts()
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException {

        Iterator<CertificationContext> it = null;

        switch (this.certifierType) {

        // Create a single context for the manager that was specified.
        case Manual:
            List<CertificationContext> ctxs = new ArrayList<CertificationContext>();
            List<Identity> ownerList = this.getOwners();
            if (ownerList == null || ownerList.isEmpty()){
                Identity certifier = getDefaultCertifier();
                if (certifier != null)
                    ownerList = Arrays.asList(certifier);
            }
            ctxs.add(newContext(context, ownerList, this.identityIds));
            it = ctxs.iterator();
            break;

        case Manager:
            Map<Identity,List<String>> idsByManager = bucketIdentitiesByManager();
            it = getContexts(idsByManager);
            break;

        default:
            throw new GeneralException("Unknown certifier type: " + this.certifierType);
        }
        
        return it;
    }

    private Identity getDefaultCertifier() throws GeneralException{
        return context.getObjectByName(Identity.class, this.defaultCertifier);
    }

    /**
     * Take the identityIds list and return a map that has each identity bucketed
     * by their manager.  Any identity that does not have a manager should be
     * certified by the default certifier.
     */
    private Map<Identity,List<String>> bucketIdentitiesByManager()
        throws GeneralException {
        
        Map<Identity,List<String>> idsByManager = new HashMap<Identity,List<String>>();
        if (identityIds != null){
            for (String id : this.identityIds) {
                Identity current = context.getObjectById(Identity.class, id);
                if (null != current) {
                    Identity manager = current.getManager();

                    // If the user doesn't have a manager, use the default certifier.
                    manager = (null != manager) ? manager : getDefaultCertifier();

                    List<String> ids = idsByManager.get(manager);
                    if (null == ids) {
                        ids = new ArrayList<String>();
                        idsByManager.put(manager, ids);
                    }
                    ids.add(id);
                    context.decache(current);
                }             
            }
        }
        return idsByManager;
    }
    
    /**
     * Return an iterator of contexts using the given certifier -> identities
     * map.
     */
    private Iterator<CertificationContext> getContexts(Map<Identity,List<String>> idsByCertifier)
        throws GeneralException {
    
        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        for (Map.Entry<Identity,List<String>> entry : idsByCertifier.entrySet()) {
            List<Identity> ownerList = Arrays.asList(entry.getKey());
            ctxs.add(newContext(context, ownerList, entry.getValue()));
            super.addOwnerResult(ManagerCertificationBuilder.RESULT_MANAGER, entry.getKey());
        }

        return ctxs.iterator();
    }

    /**
     * Create a new context using the given parameters.
     */
    private CertificationContext newContext(SailPointContext context,
            List<Identity> owners, List<String> population){

        CertificationContext ctx = new IdentityCertificationContext(context, definition,
            owners, population);

        ctx.setCertificationGroups(this.getCertificationGroups());
        return ctx;
    }


    /**
     * CertificationContext used to generate an identity certification.
     */
    public class IdentityCertificationContext
        extends BaseIdentityCertificationBuilder.BaseIdentityCertificationContext {

        public static final String NAME_TEMPLATE_IDENTITY_IDS = "identityIds";
        private List<String> identityIds;


        /**
         * Default constructor.
         * 
         * @param  owners      The owners for this certification.  This can
         *                     override a single certifier.
         * @param  population  The IDs of the population to include in the certification.
         */
        public IdentityCertificationContext(SailPointContext context, CertificationDefinition definition,
                                            List<Identity> owners, List<String> population) {
            super(context, definition, owners);
            this.identityIds = population;
        }

        /**
         * Always used the passed in owners
         */
        @Override
        protected List<Identity> getOwnersInternal() {
            return null;
        }

        public Iterator<Identity> getPopulation() throws GeneralException {

            return new IncrementalIdentityIterator(context, this.identityIds);
        }

        public boolean inPopulation(AbstractCertifiableEntity entity) {
            return this.identityIds.contains(entity.getId());
        }

        String generateDefaultName() throws GeneralException {

            String identityName = null;
            if(this.identityIds.size()>1){
                identityName = getOwnerNames();
            } else {
                Identity primaryOwner = context.getObjectById(Identity.class, this.identityIds.get(0));
                identityName = getName(primaryOwner);
            }

            Message name = new Message(MessageKeys.CERT_NAME_GENERIC,
                    new Message(getType().getMessageKey()), identityName);
            return name.getLocalizedMessage();
        }

        String generateDefaultShortName() throws GeneralException {
            String identityName = null;
            if(this.identityIds.size()>1){
                identityName = getOwnerNames();
            }else {
                Identity primaryOwner = context.getObjectById(Identity.class, this.identityIds.get(0));
                identityName = getName(primaryOwner);
            }

            Message name = new Message(MessageKeys.CERT_SHORTNAME_IDENTITY, identityName);
            return name.getLocalizedMessage();
        }

        private String getOwnerNames() throws GeneralException {

            StringBuilder names = new StringBuilder();

            List<Identity> owners = super.getOwners();
            if (null != owners) {
                String sep = "";
                for (Identity id : owners) {
                    names.append(sep).append(id.getDisplayableName());
                    sep = ", ";
                }
            }

            // Chop these off at 50 characters.
            final int MAX_LENGTH = 50;
            return Util.truncate(names.toString(), MAX_LENGTH);
        }
        
        /**
         * Add parameters to the given map used to render the name template.
         */
        void addNameTemplateParameters(Map<String,Object> params)
            throws GeneralException {

            // Arguably, this isn't too useful, but it could be pretty expensive
            // to load all of these guys to get their names.  Punt until we get
            // pushed here.
            params.put(NAME_TEMPLATE_IDENTITY_IDS, this.identityIds);
        }

        @Override
        protected void addNameParameters(CertificationNamer namer){
            namer.addParameter(NAME_TEMPLATE_IDENTITY_IDS, this.identityIds);
        }
    }
}
