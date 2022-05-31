/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationContext;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder for business role composition certifications. Creates a single certification if
 * the certifiers were specified by the certification creator. If no certifier is specified,
 * multiple certifications will be created, each containing a group of roles owned by one
 * identity. Each certification will be assigned to the owner of the given business roles.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BusinessRoleCompositionCertificationBuilder extends BaseCertificationBuilder {

    private static final Log log = LogFactory.getLog(BusinessRoleCompositionCertificationBuilder.class);

    /**
     * Base constructor for global certification.
     *
     * @param ctx SailPointContext instance
     */
    public BusinessRoleCompositionCertificationBuilder(SailPointContext ctx, CertificationDefinition definition) {
        super(ctx, definition);
    }

    /**
     * For the given set of role IDs, retrieves a flattened
     * list of the IDs of the complete hierarchy of the given roles.
     *
     * @param roleIds Set of role Ids
     * @return flattened list of all super role IDs.
     * @throws GeneralException
     */
    private Set<String> getInheritanceHierarchy(Set<String> roleIds) throws GeneralException{

        Set<String> superIds = new HashSet<String>();

        if (roleIds == null || roleIds.isEmpty())
            return superIds;

        for (String roleId : roleIds){
            Bundle bundle =  context.getObjectById(Bundle.class, roleId);
            List<Bundle> supers = bundle.getInheritance();
            for (int j = 0; supers != null && j < supers.size(); j++) {
                Bundle sup =  supers.get(j);
                superIds.add(sup.getId());
            }
        }

        // recursively get all descendants.
        if (!superIds.isEmpty())
            superIds.addAll(getInheritanceHierarchy(superIds));

        return superIds;
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContext(Certification)
     */
    public CertificationContext getContext(Certification cert) throws GeneralException {

        // We don't store the necessary info now, but role certs aren't reactive
        // either so we will just throw.
        throw new GeneralException("getContext(Certification) not yet supported - " +
                                   "context needs to implement storeContext().  This " +
                                   "is only needed for reactive certifications.");
    }

    /**
     * @return Certification contexts, one for each certification to create
     * @throws GeneralException
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException {

        List<String> businessRoleIds = ObjectUtil.convertToIds(context, Bundle.class, definition.getBusinessRoleIds());
        if (businessRoleIds == null)
            businessRoleIds = new ArrayList<String>();
        assert (this.global || (businessRoleIds != null && !businessRoleIds.isEmpty())) :
                "Expect either global or a list of business role IDs";

        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        // If this is a global cert, or if the user has chosen to certify
        // by role type, look up the roles we need to certify
        List<String> roleTypes = definition.getRoleTypes();
        if (global || (roleTypes != null && !roleTypes.isEmpty())){

            Identity defOwner = definition.getOwner();
            try {
                // When we do global certs, we need to make sure that the roles
                // applied to the cert are within the cert def owner's scope, 
                // so impersonate
                context.impersonate(defOwner);
                context.setScopeResults(true);
                
                QueryOptions ops = new QueryOptions();
                ops.addOwnerScope(defOwner);
    
                if (roleTypes != null && !roleTypes.isEmpty())
                    ops.add(Filter.in("type", roleTypes));
    
                Iterator<Object[]> bundleIds = context.search(Bundle.class, ops, Arrays.asList("id", "name"));
                while (bundleIds.hasNext()) {
                    Object[] record = bundleIds.next();
                    businessRoleIds.add((String)record[0]);
                }
            } finally {
                // If anything bad should happen or we're done, make sure we 
                // reveal our true identity before going on.
                context.impersonate(null);
                context.setScopeResults(false);
            }            
        }
        
        if (definition.isIncludeRoleHierarchy()){
            Set<String> roles = new HashSet<String>();
            if (businessRoleIds != null) {
                roles.addAll(businessRoleIds);        
                //IIQCB-1481 should not add duplicate roles to cert
                Set<String> inheritanceRoles = getInheritanceHierarchy(roles);
                for (String role : Util.safeIterable(inheritanceRoles)) {
                    if (!businessRoleIds.contains(role)) {
                        businessRoleIds.add(role);
                    }
                }
            }
        }               

        if (getOwners() == null || getOwners().isEmpty()){
            Map<String, Set<String>> assignments = getBusinessRoleAssigments(businessRoleIds);
            for(String businessRoleOwnerName : assignments.keySet()){
                Identity businessRoleOwner = context.getObjectByName(Identity.class, businessRoleOwnerName);
                CertificationContext ctx = new BusinessRoleCompositionCertificationContext(context, definition,
                        Arrays.asList(businessRoleOwner), assignments.get(businessRoleOwnerName));
                ctx.setCertificationGroups(getCertificationGroups());
                ctxs.add(ctx);
            }
        }else{
            CertificationContext ctx = new BusinessRoleCompositionCertificationContext(context,
                    definition, getOwners(), businessRoleIds);
            ctx.setCertificationGroups(getCertificationGroups());
            ctxs.add(ctx);
        }

        for(CertificationContext certCtx : ctxs){
            certCtx.setCertificationGroups(getCertificationGroups());
        }

        if(ctxs.isEmpty())
            return null;

        return ctxs.iterator();
    }

    /**
     * IIQCB-1409 if role comp cert certifier selection type is role owner we want to ignore the manual selections
     */
    @Override
    public List<Identity> getOwners() {
        if (definition.getCertifierSelectionType() == CertificationDefinition.CertifierSelectionType.Owner) {
            return null;
        }
        else
            return super.getOwners();
    }

    /**
     * Given the selected business roles, creates a map which associates role owners and their
     * business roles. This is used to create individual certifications for each owner composed of
     * all the roles they own.
     *
     * @return Map of role certification assignments where key=identityId and value=list of
     * assigned role ids.
     * @throws GeneralException
     */
    private Map<String, Set<String>> getBusinessRoleAssigments(List<String> businessRoleIds) throws GeneralException{

        Map<String, Set<String>> bundleAssignments = new HashMap<String, Set<String>>();

        if (businessRoleIds == null)
            businessRoleIds = new ArrayList<String>();
        for(String roleId : businessRoleIds){
            Iterator<Object[]> results = context.search(Bundle.class,
                           new QueryOptions(Filter.eq("id", roleId)), Arrays.asList("id", "owner.name", "name"));

            if (results.hasNext()) {
                Object[] row = results.next();

                if (row[1] != null){

                    String owner = (String)row[1];
                    String bundleId = (String)row[0];

                    if (bundleAssignments.containsKey(owner)){
                        bundleAssignments.get(owner).add(bundleId);
                    }else{
                        HashSet<String> newSet = new HashSet<String>();
                        newSet.add(bundleId);
                        bundleAssignments.put(owner, newSet);
                    }
                } else {
                    // bundle had no owner. this shouldnt happen! post a warning
                    addWarning(new Message(MessageKeys.OWNERLESS_ROLE_NOT_ADDED_TO_CERT,
                                (String)row[2]));
                }
            }
        }

        return bundleAssignments;
    }

    /**
     * Used to create UI messages which describe the type of entity
     * being certified.
     * <p/>
     *
     * @return Description of the entity type being certified.
     */
    public Message getEntityName(boolean plural) {
        String key = plural ? MessageKeys.BUSINESS_ROLES_LCASE : MessageKeys.BUSINESS_ROLE_LCASE;
        return new Message(key);
    }

    /**
     * Certification context for creating business role composition certifications.
     * These certifications certify that the profiles and child roles that make up
     * a business role are valid.
     */
    public class BusinessRoleCompositionCertificationContext
            extends BaseCertificationContext {

        private Collection<String> businessRoleIds;

         /**
         * Creates new certification context.
         *
         * @param context an open SailpoinContext
         * @param businessRoleIds List of business role Ids to certify. May be empty is the cert is a global cert.
         * @param owners List of identity IDs assigned to the certification, may be empty.
         */
        public BusinessRoleCompositionCertificationContext(SailPointContext context, CertificationDefinition definition,
                                                           List<Identity> owners, Collection<String> businessRoleIds) {
            super(context, definition, owners);
            this.businessRoleIds = businessRoleIds;
        }

        @Override
        public boolean isIncludeAdditionalEntitlements() {
            return false;
        }

        @Override
        public boolean isIncludePolicyViolations() {
            return false;
        }

        @Override
        public boolean isIncludeBusinessRoles() {
            return true;
        }

        /**
         * Retrieves a list of certifiable roles and child bundles for the given bundle.
         *
         * @param entity Bundle from which to retrieve the Certifiable items.
         * @return List of Certifiables
         * @throws GeneralException
         */
        @SuppressWarnings("unchecked")
        public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {

            List<Certifiable> certifiables = new ArrayList<Certifiable>();

            Bundle businessRole = (Bundle)entity;

            List<Profile> profiles = businessRole.getProfiles();
            if (profiles != null) {
                for(Profile profile : profiles) {
                    certifiables.add(profile);
                }
            }

            List<Bundle> supers = businessRole.getInheritance();
            if (supers != null) {
                for(Bundle bundle : supers) {
                    certifiables.add(bundle);
                }
            }

            if (businessRole.getPermits() != null){
                for(Bundle bundle : businessRole.getPermits()) {
                    certifiables.add(new RoleCertifiable(CertificationItem.Type.BusinessRolePermit, bundle));
                }
            }

            if (businessRole.getRequirements() != null){
                for(Bundle bundle : businessRole.getRequirements()) {
                    certifiables.add(new RoleCertifiable(CertificationItem.Type.BusinessRoleRequirement, bundle));
                }
            }
            
            if (businessRole.getProvisioningPlan() != null){
                ProvisioningPlan.AccountRequest iiqRequest =
                        businessRole.getProvisioningPlan().getIIQAccountRequest();
                if (iiqRequest != null){
                    for(ProvisioningPlan.AttributeRequest attrReq : iiqRequest.getAttributeRequests()){
                        if (Certification.IIQ_ATTR_SCOPES.equals(attrReq.getName()) && attrReq.getValue() != null){
                            List<String> scopes = (List<String>)attrReq.getValue();
                            for(String scope : scopes){
                                //Scope does not have unique name, but seems odd to set ids? -rap
                                SailPointObject obj = context.getObjectById(Scope.class, scope);
                                if (obj != null)
                                    certifiables.add(
                                            new RoleCertifiable(CertificationItem.Type.BusinessRoleGrantedScope, obj));
                            }
                        } else if (Certification.IIQ_ATTR_CAPABILITIES.equals(attrReq.getName()) &&
                                attrReq.getValue() != null){
                            List<String> caps = (List<String>)attrReq.getValue();
                            for(String cap : caps){
                                //TODO: There is some strange behavior with capabilities in attributeRequests. We set the value to the en_US localized value of the display name?! -rap
                                SailPointObject obj = context.getObjectByName(Capability.class, cap);
                                if (obj != null)
                                    certifiables.add(
                                            new RoleCertifiable(CertificationItem.Type.BusinessRoleGrantedCapability, obj));
                            }
                        }
                    }
                }
            }

            return certifiables;
        }

        /**
         * Creates a CertificationEntity for the given Bundle.
         * @param cert The certification
         * @param entity Bundle to be certified
         * @param snapshot Unused in this context
         * @return CertificationEntity for the given Bundle.
         * @throws GeneralException
         */
        protected CertificationEntity createCertificationEntityInternal(Certification cert, AbstractCertifiableEntity entity, boolean snapshot) throws GeneralException {
            Bundle businessRole = (Bundle)entity;           
            CertificationEntity certEntity = new CertificationEntity(businessRole);
            certEntity.setRoleSnapshot(new RoleSnapshot(businessRole, ctxt));
            return certEntity;
        }

        /**
         *
         * @param cert The certification
         * @param certifiable The certifiable to handle, should be a Profile or a child Bundle
         * @param entity The entity that owns the certifiable.
         * @return CertificationItem for the given certifiable
         * @throws GeneralException
         */
        protected CertificationItem createCertificationItemInternal(Certification cert, Certifiable certifiable,
                AbstractCertifiableEntity entity) throws GeneralException {

            if (certifiable instanceof Bundle) {
                return new CertificationItem((Bundle)certifiable);
            } else if (certifiable instanceof Profile) {
                return new CertificationItem((Profile)certifiable);
            } else if (certifiable instanceof RoleCertifiable) {
                RoleCertifiable roleCertifiable = (RoleCertifiable)certifiable;
                return new CertificationItem(roleCertifiable.getType(), roleCertifiable.getObject());    
            } else {
                throw new IllegalArgumentException("Unknown certifiable type on " +
                                                  entity.getName() + ": " + certifiable);
            }
        }

        /**
         * Creates either the short or long name for the certification
         *
         * @param shortName True if the certification short name is required
         * @return  Localized certification name
         * @throws GeneralException
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        private String createName(boolean shortName) throws GeneralException{
            String key = null;

            List params = new ArrayList();
            params.add(new Message(getType().getMessageKey()));

            if (shortName && getOwners() != null && getOwners().size() == 1){
                key = isGlobal() ? MessageKeys.CERT_SHORTNAME_GLOBAL_OWNER :
                        MessageKeys.CERT_SHORTNAME_OWNER;
                params.add(getOwners().get(0).getDisplayableName());
            } else if (!shortName && getOwners() != null && getOwners().size() == 1) {
                key = isGlobal() ? MessageKeys.CERT_NAME_GLOBAL_ROLES_OWNER : MessageKeys.CERT_NAME_ROLES_OWNER;
                params.add(getOwners().get(0).getDisplayableName());
            } else if (shortName) {
                key = isGlobal() ? MessageKeys.CERT_SHORTNAME_GLOBAL : MessageKeys.CERT_SHORTNAME_GENERIC;
            } else {
                key = isGlobal() ? MessageKeys.CERT_NAME_GLOBAL_ROLES : MessageKeys.CERT_NAME_ROLES;
            }

            Message name = new Message(key, params.toArray());

            return name.getLocalizedMessage();
        }

        String generateDefaultName() throws GeneralException {
            return createName(false);
        }

        String generateDefaultShortName() throws GeneralException {
            return createName(false);
        }

        /**
         * This method should never be called since we always populate the owners in the constructor.
         * @return null
         */
        protected List<Identity> getOwnersInternal() {
            log.error("getOwnersInternal() should never be called on BusinessRoleCompositionCertificationContext.");
            return null;
        }

        /**
         * @return Population of roles included in this certification
         * @throws GeneralException
         */
        public Iterator<Bundle> getPopulation() throws GeneralException {
            return new IncrementalObjectIterator<Bundle>(context, Bundle.class, businessRoleIds);
        }

        public boolean inPopulation(AbstractCertifiableEntity entity) {
            return this.businessRoleIds.contains(entity.getId());
        }

         /**
         * Add parameters to the given map used to render the name template.
         * This is currently not needed for this implementation.
         */
        void addNameTemplateParameters(Map<String,Object> params)
            throws GeneralException {
        }

        @Override
        protected void addNameParameters(CertificationNamer namer){
        }
    }

    public static class RoleCertifiable implements Certifiable{

        private CertificationItem.Type type;
        private SailPointObject object;

        public RoleCertifiable(CertificationItem.Type type, SailPointObject obj) {
            this.type = type;
            this.object = obj;
        }

        public CertificationItem.Type getType() {
            return type;
        }

        public SailPointObject getObject() {
            return object;
        }
    }
}
