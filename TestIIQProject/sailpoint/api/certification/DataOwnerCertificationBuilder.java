/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationContext;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.DataOwnerCertifiableEntity.DataItem;
import sailpoint.connector.Connector;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.Certification.EntitlementGranularity;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationItem.SubType;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * A certification builder that generates CertificationContexts for owners
 * of entitlements and permissions for an application
 *
 * @author <a href="mailto:tpox.mozambo@sailpoint.com">Tpox Mozambo</a>
 * 
 * TODO: Need to add new unit tests using new IdentityEntitlements after 6.0.
 */
public class DataOwnerCertificationBuilder extends BaseCertificationBuilder {

    public static final String RESULT_APPLICATION = "application";
    private static Log log = LogFactory.getLog(DataOwnerCertificationBuilder.class);

    private List<String> rightNames;

    // todo jfb - ent granularity should default to value?

    public DataOwnerCertificationBuilder(SailPointContext context, CertificationDefinition definition) {
        super(context, definition);
    }
    
    public CertificationContext getContext(Certification cert) throws GeneralException {

        return newDataOwnerContext(
                context.getObjectById(Application.class, cert.getApplicationId()),
                WebUtil.nameListToObjectList(context, Identity.class, cert.getCertifiers()));
    }

    public Iterator<CertificationContext> getContexts() throws GeneralException {

        List<CertificationContext> contexts = new ArrayList<CertificationContext>();

        if (definition.isGlobal()) {
            try {
                // When we do global entitlement owner certs, we need to make sure 
                // that the apps applied to the cert are within the cert def owner's 
                // scope, so impersonate
                Identity defOwner = definition.getOwner();
                context.impersonate(defOwner);
                context.setScopeResults(true);
                
                QueryOptions ops = new QueryOptions();
                ops.addOwnerScope(defOwner);
    
                List<Application> applications = context.getObjects(Application.class, ops);
                for (Application application : applications) {
                    List<CertificationContext> appContexts = getContexts(application);
                    if (appContexts.size() > 0) {
                        super.addResult(RESULT_APPLICATION, application.getName());
                        contexts.addAll(appContexts);
                    }
                }
            } finally {
                // If anything bad should happen or we're done, make sure we 
                // reveal our true identity before going on.
                context.impersonate(null);
                context.setScopeResults(false);
            }            
        }
        else {
            for (String appId : definition.getApplicationIds()) {
                //this was refactored to use names instead of ids, but var wasn't fixed -rap
                Application application = context.getObjectByName(Application.class, appId);
                List<CertificationContext> appContexts = getContexts(application);
                if (appContexts.size() > 0) {
                    super.addResult(RESULT_APPLICATION, application.getName());
                    contexts.addAll(appContexts);
                }
            }
        }
        
        return contexts.iterator();
    }

    public List<CertificationContext> getContexts(Application application) 
        throws GeneralException {
        
        List<CertificationContext> contexts = new ArrayList<CertificationContext>();
        
        if (isOwnerOverride()) {
            contexts.add(newDataOwnerContext(application, getOwners()));
        } else {
            List<Identity> owners = getEntitlementOwners(application);
            for (Identity owner : owners) {
                List<Identity> singleOwnersList = new ArrayList<Identity>();
                singleOwnersList.add(owner);
                contexts.add(newDataOwnerContext(application, singleOwnersList));
            }
        }
        
        return contexts;
    }
    
    private boolean isOwnerOverride() {
        return !Util.isEmpty(getOwners());
    }
    
    private List<Identity> getEntitlementOwners(Application application) 
        throws GeneralException {
        
        List<Identity> owners = new ArrayList<Identity>();

        if (countEntitlements(application) == 0) {
            return owners;
        }
        
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("application", application));
        options.add(Filter.notnull("owner"));
        options.setDistinct(true);
        
        Iterator<Object[]> ownersIterator = context.search(ManagedAttribute.class, options, "owner.id");
        while (ownersIterator.hasNext()) {
            Object[] row = ownersIterator.next();
            owners.add(context.getObjectById(Identity.class, (String)row[0]));
        }

        if (definition.isIncludeUnownedData() && nullOwnerExists(application)) {
            Identity unownedOwner = getCalculatedUnownedOwner(context, definition, application);
            if (!owners.contains(unownedOwner)) {
                owners.add(unownedOwner);
            }
        }
        return owners;
    }
    
    private boolean nullOwnerExists(Application application) 
        throws GeneralException {
        
        QueryOptions countOptions = new QueryOptions();
        countOptions.add(Filter.eq("application", application));
        countOptions.add(Filter.isnull("owner"));
        
        return context.countObjects(ManagedAttribute.class, countOptions) > 0;
    }
    
    private int countEntitlements(Application application)
        throws GeneralException {
        
        QueryOptions countOptions = new QueryOptions();
        countOptions.add(Filter.eq("application", application));
        return context.countObjects(ManagedAttribute.class, countOptions);
    }
    
    private CertificationContext newDataOwnerContext(Application app, List<Identity> owners) {
        CertificationContext ctx = new DataOwnerCertificationContext(context, definition, owners, app);
        ctx.setCertificationGroups(getCertificationGroups());
        return ctx;
    }
    
    public Message getEntityName(boolean plural) {
        String key = plural ? MessageKeys.ENTITLEMENTS_LCASE : MessageKeys.ENTITLEMENT_LCASE;
        return new Message(key);
    }

    protected static Identity getCalculatedUnownedOwner(SailPointContext context,
                                                        CertificationDefinition definition, Application app)
            throws GeneralException {

        if (definition.isAppOwnerIsUnownedOwner()) {
            if (definition.getUnownedDataOwner() != null) {
                throw new IllegalStateException("If AppOwner is Unowned Owner. No need to set UnownedOwner.");
            } else {
                return context.getObjectById(Identity.class, app.getOwner().getId());
            }
        } else {
            return definition.getUnownedDataOwner(context);
        }
    }

    private List<String> getRightNames()
        throws GeneralException {

        if (this.rightNames != null) {
            return this.rightNames;
        }
        this.rightNames = new ArrayList<String>();

        List<Right> rights =context.getObjectByName(RightConfig.class, RightConfig.OBJ_NAME).getRights();
        for (Right right : rights) {
            this.rightNames.add(right.getName());
        }

        return this.rightNames;
    }


    public class DataOwnerCertificationContext extends BaseCertificationContext {

        private Application application;
        private List<Identity> owners;
        private Identity dataOwner;
        private boolean includeGrantedByRoles;
        private List<DataOwnerCertifiableEntity> dataOwnerEntities;
        
        /**
         * Read from the definition to see if there is an explict
         * flag to turn this off, otherwise use the IdentityEntitlements
         * its much quicker then trolling through links.
         */
        boolean useIdentityEntitlements;
        
        public DataOwnerCertificationContext(SailPointContext context, CertificationDefinition definition,
                                             List<Identity> owners, Application application) {
            

            super(context, definition, owners);

            this.application = application;
            this.owners = owners;
            if (this.owners == null) {
                throw new IllegalStateException("null owners not allowed here");
            }
            if (!isOwnerOverride()) {
                if (this.owners.size() != 1) {
                    throw new IllegalStateException("Must have exactly one owner.");
                } else {
                    this.dataOwner = this.owners.get(0);
                }
            }            
            useIdentityEntitlements = true;
            if ( Util.getBoolean(definition.getAttributes(), "disableIdentityEntitlementUsage") ) {
                useIdentityEntitlements = false;
            }
            includeGrantedByRoles = definition.isIncludeEntitlementsGrantedByRoles();
        }
        
        public Application getApplication() {
            return this.application;
        }
        
        public Identity getDataOwner() {
            if (isOwnerOverride()) {
                throw new IllegalStateException("Applicable only if default owner hasn't been overridden.");
            }
            return this.dataOwner;
        }
        
        @Override
        public void storeContext(Certification cert) throws GeneralException {

            super.storeContext(cert);
            cert.setApplicationId(this.application.getId());
        }

        // abstract methods from BaseCertificationContext
        @Override
        protected void addNameParameters(CertificationNamer namer){
             namer.addParameter(CertificationNamer.NAME_TEMPLATE_APP, this.application.getName());
             namer.addParameter(CertificationNamer.NAME_TEMPLATE_GLOBAL, definition.isGlobal());
        }
        
        @Override
        String generateDefaultName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_NAME_GENERIC,
                    new Message(getType().getMessageKey()), this.application.getName());
            return name.getLocalizedMessage();
        }
        
        @Override
        String generateDefaultShortName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_SHORTNAME_DATA_OWNER, this.application.getName());
            return name.getLocalizedMessage();
        }
        
        @Override
        protected List<Identity> getOwnersInternal() {

            return this.owners;
        }
        
        public Iterator<DataOwnerCertifiableEntity> getPopulation() throws GeneralException {
            
            if (this.dataOwnerEntities == null) {
                this.dataOwnerEntities = fetchPopulation();
            }
            
            return this.dataOwnerEntities.iterator();
        }

        private List<DataOwnerCertifiableEntity> fetchPopulation()
                throws GeneralException {
            List<DataOwnerCertifiableEntity> entities = new ArrayList<DataOwnerCertifiableEntity>();
            
            List<ManagedAttribute> managedAttributes = fetchManagedAttributes();
            for (ManagedAttribute managedAttribute : managedAttributes) {
                if (ManagedAttribute.Type.Permission.name().equals(managedAttribute.getType())) {
                    entities.addAll(createCertifiableEntitiesForPermission(managedAttribute));
                } else {
                    entities.add(createCertifiableEntityForEntitlement(managedAttribute));
                }
            }
            return entities;
        }
        
        private DataOwnerCertifiableEntity createCertifiableEntityForEntitlement(ManagedAttribute entitlement) {
            
            DataItem item = new DataItem();

            item.setId(entitlement.getId());
            item.setType(entitlement.getType());
            item.setSchemaObjectType(entitlement.getType());
            item.setApplicationName(this.application.getName());
            item.setName(entitlement.getAttribute());
            item.setValue(entitlement.getValue());
            item.setDisplayableValue(entitlement.getDisplayableName());
            
            return new DataOwnerCertifiableEntity(item);
        }

        private List<DataOwnerCertifiableEntity> createCertifiableEntitiesForPermission(ManagedAttribute entitlement)
            throws GeneralException {

            List<DataOwnerCertifiableEntity> entities = new ArrayList<DataOwnerCertifiableEntity>();
            
            for (String right : DataOwnerCertificationBuilder.this.getRightNames()) {
                DataItem item = new DataItem();
                item.setId(entitlement.getId());
                item.setSchemaObjectType(entitlement.getType());
                item.setType(ManagedAttribute.Type.Permission.name());
                item.setApplicationName(this.application.getName());
                item.setName(entitlement.getAttribute());
                item.setValue(right);
                entities.add(new DataOwnerCertifiableEntity(item));
            }
            
            return entities;
        }
        
        private List<ManagedAttribute> fetchManagedAttributes() 
            throws GeneralException {
        
            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("application", this.application));
            Filter ownerFilter = getOwnerFilter();
            if (ownerFilter != null) {
                options.add(ownerFilter);
            }
            
            return this.ctxt.getObjects(ManagedAttribute.class, options);
        }
        
        private Filter getOwnerFilter()
            throws GeneralException {
            
            if (isOwnerOverride()) {
                // need to return all managed attributes for this app
                return null;
            }

            Filter ownerFilter = Filter.eq("owner", this.dataOwner);
            if (amIUnownedOwner()) {
                ownerFilter = Filter.or(ownerFilter, Filter.isnull("owner"));
            }
            
            return ownerFilter;
        }
        
        private boolean amIUnownedOwner() 
            throws GeneralException {
            
            if (!definition.isIncludeUnownedData()) {
                return false;
            }
            
            Identity unownedOwner =
                    DataOwnerCertificationBuilder.getCalculatedUnownedOwner(context, definition, application);
            if (unownedOwner == null) {
                log.error("No owner for unowned data.");
                
                return false;
            }
            
            return unownedOwner.getId().equals(this.dataOwner.getId());
        }

        //!!!tqm: this is called by certSwizzler with Identity
        // and not DataOwnerCertificationIdentity. Need to revisit
        public boolean inPopulation(AbstractCertifiableEntity entity)
                throws GeneralException {
            return true;
        }
        
        public boolean shouldIdentityBeInPopulation(Identity identity)
            throws GeneralException {

            Iterator<DataOwnerCertifiableEntity> populationIterator = getPopulation();
            while (populationIterator.hasNext()) {
                if (shouldIdentityBeIncludedInCertifiableEntity(populationIterator.next(), identity)) {
                    return true;
                }
            }
            return false;
        }

        public boolean shouldIdentityBeIncludedInCertifiableEntity(DataOwnerCertifiableEntity doCertEntity, Identity identity) 
            throws GeneralException {

            List<EntitlementGroup> groups = findEntitlementGroups(identity, doCertEntity.getDataItem());
            return !Util.isEmpty(groups);
        }
        
        /**
         * DataOwner Certs don't create certificationentities during continuous certs
         * but they only deals with cert items need to expose this mehod.
         * @throws GeneralException
         */
        public CertificationItem createCertificationItem(Certification cert, DataOwnerCertifiable certifiable, DataOwnerCertifiableEntity entity) 
            throws GeneralException {
            
            return createCertificationItem(cert, certifiable, entity, true);
        }
        
        @Override
        protected CertificationEntity createCertificationEntityInternal(
                Certification cert, AbstractCertifiableEntity fiableEntity,
                boolean snapshot) throws GeneralException {
            
            
            return new CertificationEntity(fiableEntity);
        }

        public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
            throws GeneralException {
            
            List<Certifiable> certifiables;

            DataOwnerCertifiableEntity doEntity = (DataOwnerCertifiableEntity) entity;
            DataItem item = doEntity.getDataItem();            
            if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(item.getType())) {
                if ( useIdentityEntitlements) {
                    certifiables = fetchCertifiablesUsingIdentityEntitlements(doEntity, false);
                } else {
                    certifiables = fetchCertifiablesForPermission(doEntity);
                }
            } else {
                if ( useIdentityEntitlements) {
                    certifiables = fetchCertifiablesUsingIdentityEntitlements(doEntity, true);
                } else {
                    certifiables = fetchCertifiablesUsingBruteForce(doEntity);

                }
            }            
            return certifiables;
        }           
        
 
        // TQM: this is a really brute force way of looking if entitlement exists
        // we are looking at all the links for each DataOwnerCertifiable 
        
        /**
         * Given a doEntity which has the name, value of the entitlement
         * search throught ALL of the links on an application and look for 
         * links that contain the entitlement.
         * 
         * Do this for each entitlement on an application.
         * 
         */
        private List<Certifiable> fetchCertifiablesUsingBruteForce(DataOwnerCertifiableEntity doEntity)
            throws GeneralException {

            List<Certifiable> certifiables = new ArrayList<Certifiable>();
            
            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("application", this.application));
            
            Iterator<Link> linksIterator = this.ctxt.search(Link.class, options);
            while (linksIterator.hasNext()) {
                Link link = linksIterator.next();
                if (doesLinkContainEntitlement(link, doEntity.getDataItem())) {
                    EntitlementSnapshot entitlementSnapshot = doEntity.createEntitlement();
                    entitlementSnapshot.setInstance(link.getInstance());
                    entitlementSnapshot.setDisplayName(link.getDisplayableName());
                    entitlementSnapshot.setNativeIdentity(link.getNativeIdentity());
                    certifiables.add(new DataOwnerCertifiable(link.getIdentity(), entitlementSnapshot));
                }
            }

            return certifiables;
        }
        
        private boolean doesLinkContainEntitlement(Link link, DataItem item) {
            
            return (getListStringValuesFromLinkAttribute(link, item.getName()).contains(item.getValue())); 
        }

        /**
         * This method is needed because comma separated dn values are being treated as separate values
         * when using sailpoint.tools.util methods.
         * 
         * This will return an empty list or a list. No null check required in calling function.
         */
        @SuppressWarnings("rawtypes")
        private List<String> getListStringValuesFromLinkAttribute(Link link, String attrName) {
            
            List<String> vals = new ArrayList<String>();
            
            Object val = link.getAttribute(attrName);
            if (val == null) {
                return vals;
            }
            
            if (val instanceof String) {
                vals.add((String) val);
            } else if (val instanceof Collection) {
                for (Object oneValue : ((Collection) val)) {
                    vals.add(oneValue.toString());
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Expected String value or List<String> value but got: " + val.getClass().getName() + " value: " + val);
                }
                vals.add(val.toString());
            }
            
            
            return vals;
        }

        private List<Certifiable> fetchCertifiablesForPermission(DataOwnerCertifiableEntity doEntity)
            throws GeneralException {
        
            List<Certifiable> certifiables = new ArrayList<Certifiable>();
            
            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("application", this.application));
            
            Iterator<Link> linksIterator = this.ctxt.search(Link.class, options);
            while (linksIterator.hasNext()) {
                Link link = linksIterator.next();
                if (doesLinkContainPermission(link, doEntity.getDataItem())) {
                    EntitlementSnapshot entitlementSnapshot = doEntity.createEntitlement();
                    entitlementSnapshot.setInstance(link.getInstance());
                    entitlementSnapshot.setDisplayName(link.getDisplayableName());
                    entitlementSnapshot.setNativeIdentity(link.getNativeIdentity());
                    certifiables.add(new DataOwnerCertifiable(link.getIdentity(), entitlementSnapshot));
                }
            }

            return certifiables;
            
        }

        private boolean doesLinkContainPermission(Link link, DataItem item) {
            
            if (link.getAttributes() == null) {return false;}
            
            @SuppressWarnings("unchecked")
            List<Permission> permissions = (List<Permission>)link.getAttribute(Connector.ATTR_DIRECT_PERMISSIONS);
            if (permissions == null) {return false;}
            
            for (Permission permission : permissions) {
                if (permission.hasTarget(item.getName()) && permission.hasRight(item.getValue())) {
                    return true;
                }
            }

            return false;
        }
        
        // this is called during refresh
        public List<EntitlementGroup> findEntitlementGroups(Identity identity, DataItem dataItem) 
            throws GeneralException {
        
            List<EntitlementGroup> groups = null;
            if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(dataItem.getType())) {
                if ( useIdentityEntitlements ) {
                    groups = searchForItemEntitlements(identity, dataItem, false);
                } else {
                    groups = findEntitlementGroupsForManagedPermission(identity, dataItem);
                }
            } else {
                if ( useIdentityEntitlements ) {
                    groups = searchForItemEntitlements(identity, dataItem, true);
                } else {
                    groups = findEntitlementGroupsForManagedEntitlement(identity, dataItem);
                }
            }
            return groups;
        }


        private List<EntitlementGroup> findEntitlementGroupsForManagedEntitlement(Identity identity, DataItem entitlementDataItem) 
            throws GeneralException {

            List<EntitlementGroup> result = new ArrayList<EntitlementGroup>();
            
            Iterator<Link> linksIterator = fetchInterestingLinksIterator(identity, entitlementDataItem);
            while (linksIterator.hasNext()) {
                Link link = linksIterator.next();
                if (doesLinkContainEntitlement(link, entitlementDataItem)) {
                    EntitlementGroup entitlementGroup = new EntitlementGroup();
                    entitlementGroup.setNativeIdentity(link.getNativeIdentity());
                    entitlementGroup.setDisplayName(link.getDisplayableName());
                    result.add(entitlementGroup);
                }
            }
            
            return result;
        }

        private Iterator<Link> fetchInterestingLinksIterator(Identity identity, DataItem entitlementDataItem)
                throws GeneralException {
            
            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("identity", identity));
            options.add(Filter.eq("application.name", entitlementDataItem.getApplicationName()));
            
            return this.ctxt.search(Link.class, options);
        }
    
        private List<EntitlementGroup> findEntitlementGroupsForManagedPermission(Identity identity, DataItem permissionDataItem)
            throws GeneralException {
    
            List<EntitlementGroup> result = new ArrayList<EntitlementGroup>();
            
            Iterator<Link> linksIterator = fetchInterestingLinksIterator(identity, permissionDataItem);
            while (linksIterator.hasNext()) {
                Link link = linksIterator.next();
                if (doesLinkContainPermission(link, permissionDataItem)) {
                    EntitlementGroup entitlementGroup = new EntitlementGroup();
                    entitlementGroup.setNativeIdentity(link.getNativeIdentity());
                    entitlementGroup.setDisplayName(link.getDisplayableName());
                    result.add(entitlementGroup);
                }
            }
            
            return result;
        }
    
        @Override
        protected CertificationItem createCertificationItemInternal(
                Certification cert, Certifiable certifiable,
                AbstractCertifiableEntity entity) throws GeneralException {

            
            DataOwnerCertifiableEntity doEntity = (DataOwnerCertifiableEntity) entity;
            DataOwnerCertifiable doCertifiable = (DataOwnerCertifiable) certifiable;

            CertificationItem item = new CertificationItem(doCertifiable.getEntitlements(), EntitlementGranularity.Value);
            
            item.setType(CertificationItem.Type.DataOwner);
            //TODO: May need to revisit this
            if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(doEntity.getDataItem().getType())) {
                item.setSubType(SubType.Permission);
            } else {
                item.setSubType(SubType.Entitlement);
            }
            
            item.setTargetId(doCertifiable.getIdentity().getId());
            item.setTargetName(doCertifiable.getIdentity().getName());
            
            assimilateLinkAttributes(doCertifiable.getIdentity(), doCertifiable.getEntitlements(), item);
            
            return item;
        }
        
        ///////////////////////////////////////////////////////////////////////////
        //
        // IdentityEntitlement methods
        //
        ///////////////////////////////////////////////////////////////////////////
        
        /**
         * Query for the IdentityEntitlements that represent the data item and 
         * build a list of EntitlementGroups to represent the data.
         * 
         * This is consumed during continous certification processing to
         * update existing certifications and called once for entitlements
         * and once for permissions.  Now that we have moved to a query
         * approach we could probably do them all at one time.
         *  
         */
        private List<EntitlementGroup> searchForItemEntitlements(Identity identity, 
                                                                 DataItem item, 
                                                                 boolean entitlement)
            throws GeneralException {

            List<EntitlementGroup> result = new ArrayList<EntitlementGroup>();

            String attrName = null;
            String itemValue = null;
            if ( item != null ) {
                attrName = item.getName();
                itemValue = item.getValue();
            }

            if ( attrName == null || itemValue == null ) 
                return null;

            QueryOptions options = createQueryOptions(item, new Boolean(entitlement));   
            options.add(Filter.eq("identity", identity ));
            
            Iterator<Object[]> entIterator = ctxt.search(IdentityEntitlement.class, options, "nativeIdentity, instance, displayName");
            while ( entIterator != null && entIterator.hasNext()) {                
                IdentityEntitlementStub ent = new IdentityEntitlementStub(entIterator.next());
                EntitlementGroup entitlementGroup = new EntitlementGroup();
                entitlementGroup.setInstance(ent.getInstance());
                entitlementGroup.setDisplayName(ent.getDisplayName());
                entitlementGroup.setNativeIdentity(ent.getNativeIdentity());
                result.add(entitlementGroup);
            }            
            return result;
        }        
        
        /**
         * 
         * Use the IdentityEntitlements talbe to derive the certifiables for a given attribute name
         * and value ( stored on the entity).  This has traditionally only included entitlements
         * that aren't covered by the role model.
         * 
         * This was originally developed requiring extended link attributes to define the 
         * "members" of an entitlement.
         * 
         * @since 6.0
         * 
         * @param doEntity
         * @param attrName
         * 
         * @return
         * 
         * @throws GeneralException
         */
        private List<Certifiable> fetchCertifiablesUsingIdentityEntitlements(DataOwnerCertifiableEntity doEntity, boolean isEntitlement) 
            throws GeneralException {
            
            List<Certifiable> certifiables = new ArrayList<Certifiable>();            
            if ( doEntity == null )
                return certifiables;
            
            DataItem item = doEntity.getDataItem();
            String itemValue = (item != null ) ? item.getValue() : null;
            if ( itemValue == null ) 
                return certifiables;
           
            QueryOptions options = createQueryOptions(item, isEntitlement);
            Iterator<Object[]> entIterator = this.ctxt.search(IdentityEntitlement.class, options, "nativeIdentity, instance, displayName, identity");
            while ( entIterator != null && entIterator.hasNext()) {                
                IdentityEntitlementStub ent = new IdentityEntitlementStub(entIterator.next());
                EntitlementSnapshot entitlement = doEntity.createEntitlement();
                entitlement.setInstance(ent.getInstance());
                entitlement.setDisplayName(ent.getDisplayName());
                entitlement.setNativeIdentity(ent.getNativeIdentity());
                certifiables.add(new DataOwnerCertifiable(ent.getIdentity(), entitlement));
            }            
            return certifiables;
        }
        
        private QueryOptions createQueryOptions(DataItem item, Boolean entitlement) {
         
            Filter filter = Filter.and(Filter.eq("application", this.application),
                                       Filter.ignoreCase(Filter.eq("name", item.getName())),
                                       Filter.ignoreCase(Filter.eq("value", item.getValue())),
                                       Filter.eq("aggregationState", AggregationState.Connected));

            if (!includeGrantedByRoles) {
                filter = Filter.and(filter, Filter.eq("grantedByRole", false));
            }
            
            if ( entitlement != null ){
                if ( entitlement ) {
                    filter = Filter.and(filter, Filter.eq("type", ManagedAttribute.Type.Entitlement));
                } else {
                    filter = Filter.and(filter, Filter.eq("type", ManagedAttribute.Type.Permission));
                }
            }
            
            QueryOptions options = new QueryOptions();
            options.add(filter);

            return options;
        }
    }

    /**
     * 
     * Defines one
     *
     */
    public static class DataOwnerCertifiable implements Certifiable {

        private Identity identity;
        private Entitlements entitlements;

        public DataOwnerCertifiable(Identity identity, Entitlements entitlements) {
            this.identity = identity;
            this.entitlements = entitlements;
        }

        public Identity getIdentity() {
            return this.identity;
        }

        public void setIdentity(Identity identity) {
            this.identity = identity;
        }
        
        
        public Entitlements getEntitlements() {
            return this.entitlements;
        }
        
        public void setEntitlements(Entitlements val) {
            this.entitlements = val;
        }
    }
    
    /**
     * Helper DTO class to represent enttlements while using a projection
     * query. These are used to represent the fields necessary for the entitlement
     * certification details both when generating new certifications and when
     * refreshing the certification during continuous certifications.
     * 
     */
    public static class IdentityEntitlementStub {
        
        String nativeIdentity;
        String instance;
        String displayName;
        Identity identity;
        
        public IdentityEntitlementStub(Object[] row) {
            if  ( row != null ) {
                if ( row.length > 2 ) {
                    nativeIdentity = (String)row[0];
                    instance = (String)row[1];
                    displayName = (String)row[2];    
                    if ( row.length == 4 )
                        identity = (Identity)row[3];
                }
            }
            
        }

        public String getNativeIdentity() {
            return nativeIdentity;
        }

        public void setNativeIdentity(String nativeIdentity) {
            this.nativeIdentity = nativeIdentity;
        }

        public String getInstance() {
            return instance;
        }

        public void setInstance(String instance) {
            this.instance = instance;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
        
        public Identity getIdentity() {
            return identity;
        }    
    }
}
