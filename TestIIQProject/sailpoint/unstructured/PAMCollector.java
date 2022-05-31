/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import openconnector.ConnectorConfig;
import openconnector.ConnectorException;
import openconnector.Filter;
import openconnector.Filter.Conjunct;
import openconnector.Filter.Operator;
import openconnector.Item;
import openconnector.ObjectNotFoundException;
import openconnector.Plan;
import openconnector.Request;
import openconnector.Result;
import openconnector.Schema;
import openconnector.Schema.Type;
import openconnector.connector.scim2.SCIM2Connector;
import openconnector.connector.scim2.SCIM2PropertyGetter;
import openconnector.connector.scim2.SCIM2PropertySetter;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.OpenConnectorAdapter.CommonsLogAdapter;
import sailpoint.object.AccessMapping;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.scim.mapping.AttributeMapping;
import sailpoint.service.scim.AttributePropertyMapping;
import sailpoint.service.scim.SchemaPropertyMapping;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * A target collector that can read and provision targets on a PAM (privileged account management) system.
 * This sub-classes the SCIM2Connector so it can use the connector's iterate and provision methods to interact with
 * the ContainerPermissions and PrivilegedDataPermissions endpoints.  The configuration attributes on the TargetSource
 * that are used to create this collector should match the configuration attributes that are used by the SCIM 2
 * connector.
 */
public class PAMCollector extends SCIM2Connector implements TargetCollector {

    private static final Log LOG = LogFactory.getLog(PAMCollector.class);

    private static final String OBJECT_TYPE_CONTAINER = "Container";
    private static final String OBJECT_TYPE_PRIVILEGED_DATA = "PrivilegedData";
    private static final String OBJECT_TYPE_CONTAINER_PERMISSION = "ContainerPermission";
    private static final String OBJECT_TYPE_PRIVILEGED_DATA_PERMISSION = "PrivilegedDataPermission";

    private static final String ATTR_ID = "id";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_CONTAINER = "container";
    private static final String ATTR_PRIVILEGED_DATA = "privilegedData";
    private static final String ATTR_USER = "user";
    private static final String ATTR_GROUP = "group";
    private static final String ATTR_RIGHTS = "rights";


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     *
     * @param ts  The TargetSource this collector is collecting from.
     */
    public PAMCollector(TargetSource ts) {
        super();

        // Configure the connector by creating a ConnectorConfig from the TargetSource configuration.
        super.configure(createConnectorConfig(ts), new CommonsLogAdapter(LOG));
    }

    /**
     * Create a ConnectorConfig based on the given TargetSource.
     *
     * @param ts  The TargetSource to use to create the ConnectorConfig.
     *
     * @return A ConnectorConfig based on the given TargetSource.
     */
    private static ConnectorConfig createConnectorConfig(TargetSource ts) {
        // Create a config that uses the attributes from the TargetSource.
        ConnectorConfig config = new ConnectorConfig();
        config.setConfig(ts.getConfiguration());

        // Create schemas for ContainerPermissions and PrivilegedDataPermissions.
        config.addSchema(createSchema(OBJECT_TYPE_CONTAINER_PERMISSION, ATTR_CONTAINER));
        config.addSchema(createSchema(OBJECT_TYPE_PRIVILEGED_DATA_PERMISSION, ATTR_PRIVILEGED_DATA));

        // We're doing a little bit of reading of containers and privileged data, so add simple schemas for these.
        addSimpleSchemaAndMappings(config, OBJECT_TYPE_CONTAINER);
        addSimpleSchemaAndMappings(config, OBJECT_TYPE_PRIVILEGED_DATA);

        /* IIQSR-127 Some credentials are encrypted using the IIQ key, these need to be decrypted
         * before sending them to the SCIM server, otherwise the SCIM server can't decrypt them and
         * will return errors.
         */
        try {
            SailPointContext context = SailPointFactory.getCurrentContext();
            if (context != null && Util.isNotNullOrEmpty(config.getString(SCIM2Connector.CONFIG_PASSWORD))) {
                config.setAttribute(SCIM2Connector.CONFIG_PASSWORD, context.decrypt(config.getString(SCIM2Connector.CONFIG_PASSWORD)));
            }
            if (context != null && Util.isNotNullOrEmpty(config.getString(SCIM2Connector.CONFIG_CLIENT_SECRET))) {
                config.setAttribute(SCIM2Connector.CONFIG_CLIENT_SECRET, context.decrypt(config.getString(SCIM2Connector.CONFIG_CLIENT_SECRET)));
            }
            if (context != null && Util.isNotNullOrEmpty(config.getString(SCIM2Connector.CONFIG_REFRESH_TOKEN))) {
                config.setAttribute(SCIM2Connector.CONFIG_REFRESH_TOKEN, context.decrypt(config.getString(SCIM2Connector.CONFIG_REFRESH_TOKEN)));
            }
            if (context != null && Util.isNotNullOrEmpty(config.getString(SCIM2Connector.CONFIG_OAUTH_TOKEN))) {
                config.setAttribute(SCIM2Connector.CONFIG_OAUTH_TOKEN, context.decrypt(config.getString(SCIM2Connector.CONFIG_OAUTH_TOKEN)));
            }
        } catch (Throwable t) {
            LOG.error("Exception while creating the connector config in PAMCollector.", t);
        }
        return config;
    }

    /**
     * Create a target permissions Schema for the given object type and the targetAttr (ie - container or privileged
     * data).
     *
     * @param objectType  The object type for the schema.
     * @param targetAttr  The name of the attribute that refers to the container or privileged data that the target
     *                    permissions are granting access to.
     *
     * @return A Schema for the requested data.
     */
    private static Schema createSchema(String objectType, String targetAttr) {
        Schema schema = new Schema();
        schema.setObjectType(objectType);
        schema.setIdentityAttribute(ATTR_ID);

        schema.addAttribute(ATTR_ID, Type.STRING);
        addComplex(schema, targetAttr, true);
        addComplex(schema, ATTR_USER);
        addComplex(schema, ATTR_GROUP);
        schema.addAttribute(ATTR_RIGHTS, Type.STRING, true);

        return schema;
    }

    /**
     * Add a complex attribute (value, $ref, and display) with the given name to the given schema.
     *
     * @param schema  The Schema to which to add the attribute.
     * @param attrName  The name of the complex attribute.
     */
    private static void addComplex(Schema schema, String attrName) {
        addComplex(schema, attrName, false);
    }

    /**
     * Add a complex attribute (value, $ref, and display) with the given name to the given schema.
     *
     * @param schema  The Schema to which to add the attribute.
     * @param attrName  The name of the complex attribute.
     * @param includeName  Whether to include the name sub-attribute.
     */
    private static void addComplex(Schema schema, String attrName, boolean includeName) {
        schema.addAttribute(attrName + ".value", Type.STRING);
        schema.addAttribute(attrName + ".$ref", Type.STRING);
        schema.addAttribute(attrName + ".display", Type.STRING);
        if (includeName) {
            schema.addAttribute(attrName + ".name", Type.STRING);
        }
    }

    /**
     * Add a simple schema with only ID and name, and SchemaMappings (if they don't exist) to the given config for the
     * given object type.
     *
     * @param config  The config to which to add the schema and mappings.
     * @param objectType  The object type for the schema and mappings.
     */
    private static void addSimpleSchemaAndMappings(ConnectorConfig config, String objectType) {
        // Add a schema to the config.
        Schema schema = createSimpleSchema(objectType);
        config.addSchema(schema);

        // If there are not mappings yet for the the schema, add them.
        addSimpleSchemaMappingIfNotPresent(config, objectType);
    }

    /**
     * Create a simple with only ID and name for the given object type.  These are used to allow very basic reading of
     * some object types.
     *
     * @param objectType  The object type for the schema.
     *
     * @return A Schema for the requested data.
     */
    private static Schema createSimpleSchema(String objectType) {
        Schema schema = new Schema();
        schema.setObjectType(objectType);
        schema.setIdentityAttribute(ATTR_ID);

        schema.addAttribute(ATTR_ID, Type.STRING);
        schema.addAttribute(ATTR_NAME, Type.STRING);
        if (OBJECT_TYPE_CONTAINER.equals(objectType)) {
            schema.addAttribute(ATTR_DISPLAY_NAME, Type.STRING);
        }

        return schema;
    }

    /**
     * Add SchemaMappings for the given object type to the ConnectorConfig for the given object type, if it does not
     * already exist.
     *
     * Note: The SCIM2Connector is currently pretty weird, in that it will initialize and save the schema mappings
     * when iterate() is called but not when read() is called.  This causes problems for Container and PrivilegedData
     * since the collector does not iterate over these object types, but may need to fetch them.  To work around this
     * funky-ness, we'll go ahead and create simple mappings so we can read these objects.
     *
     * @param config  The config to which to add the schema mappings.
     * @param objectType  The object type.
     */
    private static void addSimpleSchemaMappingIfNotPresent(ConnectorConfig config, String objectType) {
        @SuppressWarnings("unchecked")
        List<SchemaPropertyMapping> mappings =
            (List<SchemaPropertyMapping>) config.getAttribute(CONFIG_SCHEMA_PROPERTY_MAPPING);
        if (null == mappings) {
            mappings = new ArrayList<>();
            config.setAttribute(CONFIG_SCHEMA_PROPERTY_MAPPING, mappings);
        }

        // Look for the mapping.
        String objectTypeUrn = getUrnForSimpleObjectType(objectType);
        SchemaPropertyMapping found = null;
        for (SchemaPropertyMapping mapping : mappings) {
            if (objectTypeUrn.equals(mapping.getUrn())) {
                found = mapping;
                break;
            }
        }

        // If we didn't find the mapping, create a simple one with ID and name.
        if (null == found) {
            found = new SchemaPropertyMapping();
            found.setUrn(objectTypeUrn);

            List<AttributeMapping> attrMappings = new ArrayList<>();
            attrMappings.add(createAttributePropertyMapping(ATTR_ID));
            attrMappings.add(createAttributePropertyMapping(ATTR_NAME));
            if (OBJECT_TYPE_CONTAINER.equals(objectType)) {
                attrMappings.add(createAttributePropertyMapping(ATTR_DISPLAY_NAME));
            }
            found.setAttributeMappingList(attrMappings);

            mappings.add(found);
        }
    }

    /**
     * Return the URN for the SCIM schema of the given object type.
     *
     * @param objectType  The object type.
     *
     * @return The URN for the SCIM schema of the given object type.
     */
    private static String getUrnForSimpleObjectType(String objectType) {
        String objectTypeUrn = null;

        // Just hard-code this rather than having to look at the schemas/resource type.
        if (OBJECT_TYPE_CONTAINER.equals(objectType)) {
            objectTypeUrn = "urn:ietf:params:scim:schemas:pam:1.0:Container";
        }
        else if (OBJECT_TYPE_PRIVILEGED_DATA.equals(objectType)) {
            objectTypeUrn = "urn:ietf:params:scim:schemas:pam:1.0:PrivilegedData";
        }
        else {
            throw new RuntimeException("Unhandled object type: " + objectType);
        }

        return objectTypeUrn;
    }

    /**
     * Create an AttributePropertyMapping for the given attribute.
     *
     * @param attrName  The name of the attribute.
     */
    private static AttributePropertyMapping createAttributePropertyMapping(String attrName) {
        AttributePropertyMapping mapping = new AttributePropertyMapping();
        mapping.setName(attrName);
        mapping.setProperty(attrName);
        mapping.setGetter(SCIM2PropertyGetter.class.getName());
        mapping.setSetter(SCIM2PropertySetter.class.getName());
        return mapping;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // BASIC COLLECTOR OPERATIONS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.unstructured.TargetCollector#testConfiguration()
     */
    @Override
    public void testConfiguration() throws GeneralException {
        // Just use the regular connector's testConnection().
        super.testConnection();
    }

    /* (non-Javadoc)
     * @see sailpoint.unstructured.TargetCollector#getErrors()
     */
    @Override
    public List<String> getErrors() {
        // Errors will be handled as explosions ... none of this weak message stuff.
        return null;
    }

    /* (non-Javadoc)
     * @see sailpoint.unstructured.TargetCollector#getMessages()
     */
    @Override
    public List<String> getMessages() {
        // Nothing interesting to report.
        return null;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // ITERATE
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.unstructured.TargetCollector#iterate(java.util.Map)
     */
    @Override
    public CloseableIterator<Target> iterate(Map<String, Object> ops) throws GeneralException {
        CompositeIterator iterator = null;

        String containerFilter = Util.getString(ops, "targetName");
        if(Util.isNotNullOrEmpty(containerFilter)) {
            // First, try to read it as a container by searching by name.
            super.setObjectType(OBJECT_TYPE_CONTAINER);
            Filter filter = new Filter();
            filter.add(ATTR_NAME, Operator.EQ, containerFilter);
            TargetIterator targetIterator = new TargetIterator(OBJECT_TYPE_CONTAINER, "", filter);
            iterator = new CompositeIterator(super.getObjectType(), targetIterator);
        } else {
            // Save the objectType that is set prior to iterating.  This will be restored when the iterator is closed.
            String originalObjectType = super.getObjectType();
            Iterator<Target> containerIterator = new TargetIterator(OBJECT_TYPE_CONTAINER, "");
            Iterator<Target> privilegedDataIterator =
                    new TargetIterator(OBJECT_TYPE_PRIVILEGED_DATA_PERMISSION, ATTR_PRIVILEGED_DATA);
            iterator = new CompositeIterator(originalObjectType, containerIterator, privilegedDataIterator);
        }

        return iterator;
    }

    /**
     * An iterator that returns Targets created by retrieving either ContainerPermissions or PrivilegedDataPermissions.
     * This uses the connector's iterate() method to return the raw XXXPermission objects, which will be merged together
     * if multiple permission objects use the same target.
     */
    private class TargetIterator implements Iterator<Target> {

        // The objectType to iterate over - either ContainerPermission or PrivilegedDataPermission.
        private String objectType;

        // The name of the attribute on the resource objects that has the native identity of the target.
        private String targetAttribute;

        // The connector's iterator of the ContainerPermission or PrivilegedDataPermission objects.
        private Iterator<Map<String,Object>> iterator;

        // The Target that will be returned the next time next() is called.  This is pre-built by calling
        // advance() and pre-loading the next target.
        private Target currentTarget;

        // The last object that was returned by the connector's iterator.
        private Map<String,Object> lastObject;

        // A cache of Container ID -> Container name.  This gets loaded as we find ContainerPermissions that don't
        // have container names, and prevents having to do duplicate lookups.
        private Map<String,String> containerNamesById;


        /**
         * Constructor.
         *
         * @param objectType  The objectType to iterate over - either ContainerPermission or PrivilegedDataPermission.
         * @param targetAttr  The name of the attribute on the resource objects returned from the connector that has
         *                    the native identity of the target.
         */
        public TargetIterator(String objectType, String targetAttr) {
            this(objectType, targetAttr, (Filter)null);
        }

        public TargetIterator(String objectType, String targetAttr, Filter filter) {
            this.objectType = objectType;
            this.targetAttribute = targetAttr;
            this.containerNamesById = new HashMap<>();

            // Set the object type and get the iterator over the objects.
            setObjectType(objectType);
            this.iterator = iterate(filter);

            // Pre-build the first "currentTarget".
            this.advance();
        }

        /**
         * Iterate through the underlying connector's iterator to build and set the "currentTarget" for this iterator.
         * We need to pre-build the Targets because we need to peek ahead on the underlying iterator to figure out when
         * the target changes on the Container/PrivilegedData Permissions that are being returned.
         *
         * Note: This relies on the ContainerPermissions and PrivilegedDataPermissions to be sorted by target.
         */
        private void advance() {
            // Call setObjectType() every time we advance to make sure the connector is still configured as we
            // expect it.  For example, constructing the second TargetIterator will change this value, so let's
            // be careful.
            setObjectType(this.objectType);

            // Set the current target to null.  This will remain null if there is nothing left to iterate.
            this.currentTarget = null;

            // If this is the first time through, we won't yet have pulled anything off the iterator. Initialize it.
            if ((null == this.lastObject) && this.iterator.hasNext()) {
                this.lastObject = this.iterator.next();
            }

            // Only build a target if the iterator has more stuff.
            if (null != this.lastObject) {

                if (OBJECT_TYPE_CONTAINER.equals(this.objectType)) {
                    String display = (String) this.lastObject.get("displayName");
                    String name = (String) this.lastObject.get("name");
                    String value = (String) this.lastObject.get("id");
                    // Create the target.
                    this.currentTarget = new Target();
                    this.currentTarget.setName(name);
                    this.currentTarget.setDisplayName(display);
                    this.currentTarget.setNativeObjectId(value);
                    String prevObjectType = getObjectType();
                    setObjectType(OBJECT_TYPE_CONTAINER_PERMISSION);
                    Filter filter = new Filter();
                    //IIQETN-8736 -- use full name in the filter: "container.value"
                    //Filter by complex attribute "container" is only supported 
                    //by SailPoint SCIM implementation.
                    filter.add(ATTR_CONTAINER + ".value", Operator.EQ, value, false);
                    Iterator perms = iterate(filter);
                    while (perms.hasNext()){
                        addAccessMappings(this.currentTarget, (Map<String, Object>) perms.next());
                    }
                    setObjectType(prevObjectType);

                    this.lastObject = null;
                } else {
                    // If we don't have a display name, just use the native identity as the display name.
                    String display = getComplexValue(this.lastObject, this.targetAttribute, "display");
                    String name = getComplexValue(this.lastObject, this.targetAttribute, "name");
                    String value = getComplexValue(this.lastObject, this.targetAttribute);

                    String nativeObjectId = value;

                    // Make sure we have a name set on the target.
                    if (null == name) {
                        // For privileged data, just use the ID as the name if we don't have one.
                        name = value;
                    }

                    // Create the target.
                    this.currentTarget = new Target();
                    this.currentTarget.setName(name);
                    this.currentTarget.setDisplayName(display);
                    this.currentTarget.setNativeObjectId(nativeObjectId);

                    // Iterate over the resource objects until we find one where the target changes.
                    boolean changedTargets = false;
                    do {
                        // Add the AccessMapping from the lastObject to the currentTarget.
                        addAccessMappings(this.currentTarget, this.lastObject);

                        // Increment to the next object if there is one.
                        if (this.iterator.hasNext()) {
                            Map<String, Object> previous = this.lastObject;
                            this.lastObject = this.iterator.next();

                            // Check to see if the target changed between the current object and the next that will
                            // be processed.  If so, this stops iteration for this target.
                            changedTargets =
                                    !Util.nullSafeEq(getComplexValue(previous, this.targetAttribute),
                                            getComplexValue(this.lastObject, this.targetAttribute));
                        } else {
                            // Once the iterator is exhausted, set the lastObject to null so we stop iterating.
                            this.lastObject = null;
                        }
                    } while ((null != this.lastObject) && !changedTargets);
                }
            }
        }

        /**
         * Return the "value" sub-attribute of the complex value in the given object.
         *
         * @param object  The object from which to get the value.
         * @param attr  The name of the complex attribute.
         *
         * @return The "value" sub-attribute of the complex value in the given object.
         */
        private String getComplexValue(Map<String,Object> object, String attr) {
            return getComplexValue(object, attr, null);
        }

        /**
         * Return the requested sub-attribute of the complex value in the given object.
         *
         * @param object  The object from which to get the value.
         * @param attr  The name of the complex attribute.
         * @param subAttr  The sub-attribute to retrieve.
         *
         * @return The requested sub-attribute of the complex value in the given object.
         */
        private String getComplexValue(Map<String,Object> object, String attr, String subAttr) {
            String value = null;

            if (null != object) {
                subAttr = (null != subAttr) ? subAttr : "value";
                value = (String) object.get(attr + "." + subAttr);
            }

            return value;
        }

        /**
         * Fetch the Container from the server with the given ID and return the name, or return it from the cache
         * if it has already been loaded.
         *
         * @param containerId  The ID of the Container.
         *
         * @return The name of the container.
         *
         * @throws ConnectorException If the Container cannot be loaded or does not have a name.
         */
        private String fetchContainerName(String containerId) throws ConnectorException {
            // First, look in the cache.
            String containerName = this.containerNamesById.get(containerId);

            // If it's not in the cache yet, load it and put it in the cache.
            if (null == containerName) {
                String prevObjectType = getObjectType();

                try {
                    setObjectType(OBJECT_TYPE_CONTAINER);
                    Map<String,Object> container = read(containerId);
                    if (null == container) {
                        throw new ObjectNotFoundException("Could not find container with ID " + containerId);
                    }

                    containerName = (String) container.get(ATTR_NAME);
                    if (null == containerName) {
                        throw new ConnectorException("Container object does not have a name: " + container);
                    }

                    // Add it to the cache to prevent further lookups.
                    this.containerNamesById.put(containerId, containerName);
                }
                finally {
                    setObjectType(prevObjectType);
                }
            }

            return containerName;
        }

        /**
         * Add an AccessMapping to the given Target, built from the given ContainerPermission or PrivilegedDataPermission
         * resourceObject.
         *
         * @param target  The Target to which to add the AccessMapping.
         * @param resourceObject  The ContainerPermission or PrivilegedDataPermission resource object from which to
         *                        get the access mapping details.
         */
        @SuppressWarnings("unchecked")
        private void addAccessMappings(Target target, Map<String,Object> resourceObject) {
            String userId = (String) resourceObject.get(ATTR_USER + ".value");
            String groupId = (String) resourceObject.get(ATTR_GROUP + ".value");
            List<String> rights = (List<String>) resourceObject.get(ATTR_RIGHTS);
            String nativeObjectId = (null != userId) ? userId : groupId;

            AccessMapping mapping = new AccessMapping();
            mapping.addNativeId(nativeObjectId);
            mapping.setRightsList(rights);
            mapping.setAllow(true);

            if (null != userId) {
                target.addAccountAccess(mapping);
            }
            else if (null != groupId) {
                target.addGroupAccess(mapping);
            }
            else {
                throw new RuntimeException("Expected a user or group ID for " + resourceObject.get(ATTR_ID));
            }
        }

        /*
         * (non-Javadoc)
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return (null != this.currentTarget);
        }

        /*
         * (non-Javadoc)
         * @see java.util.Iterator#next()
         */
        @Override
        public Target next() {
            Target returnValue = this.currentTarget;

            // Pre-load the next Target that will be returned.
            this.advance();

            // Return the last target that was preloaded.
            return returnValue;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Removal not allowed");
        }
    }

    /**
     * An Iterator that will iterate over all objects in a series of Iterators.  This Iterator will also set the object
     * type of this connector back to its original value when it is closed.
     */
    private class CompositeIterator implements CloseableIterator<Target> {

        private String originalObjectType;
        private Iterator<Iterator<Target>> iterators;
        private Iterator<Target> currentIterator;

        /**
         * Constructor.
         *
         * @param originalObjectType  The connector's original objectType, which will be set when the iterator is closed.
         * @param iterators  The Iterates to iterate over.
         *
         * @throws ConnectorException  If no iterators are passed in.
         */
        @SafeVarargs
        public CompositeIterator(String originalObjectType, Iterator<Target>... iterators) throws ConnectorException {
            // Save the original objectType.  This will be restored in close().
            this.originalObjectType = originalObjectType;

            // Initialize an iterator over the Iterators that were passed in.
            this.iterators = Arrays.asList(iterators).iterator();

            // If no iterators were passed in, throw.
            if (!this.iterators.hasNext()) {
                throw new ConnectorException("At least one iterator expected");
            }

            // Advance to the first target iterator.
            this.currentIterator = this.iterators.next();
        }

        /**
         * Return the current iterator to iterate over.  If there are no more iterators, an empty iterator is returned.
         */
        private Iterator<Target> getIterator() {
            // If the current iterator is exhausted, try to get the next one.
            if (!this.currentIterator.hasNext()) {
                // If there are more iterators, move to the next;
                if (this.iterators.hasNext()) {
                    this.currentIterator = this.iterators.next();
                }
                else {
                    // If there are no more iterators, return an empty iterator.
                    this.currentIterator = Collections.emptyIterator();
                }
            }

            return this.currentIterator;
        }

        /*
         * (non-Javadoc)
         * @see sailpoint.tools.CloseableIterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return this.getIterator().hasNext();
        }

        /*
         * (non-Javadoc)
         * @see sailpoint.tools.CloseableIterator#next()
         */
        @Override
        public Target next() {
            return this.getIterator().next();
        }

        /**
         * Set the connector's objectType back to the originalObjectType.
         */
        @Override
        public void close() {
            // Now that we're all done iterating, restore the original object type for the connector.
            setObjectType(this.originalObjectType);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROVISION
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Provision a ContainerPermission or PrivilegedDataPermission.
     *
     * The provisioning plan is expected to have one or more AccountRequests (if access is being modified for a user)
     * or ObjectRequests (if access is being modified for a group).  The AccountRequest/ObjectRequest must have the
     * following information:
     *
     *   - nativeIdentity: The native identity of the account or group to give/remove access
     *   - permission requests: One or more permission requests with the following information
     *      - operation: Add, remove, or set
     *      - rights: The list of rights to add/remove/set.
     *      - target: The ID of the container or privileged data on which access is being modified.
     *
     * @param plan  The ProvisioningPlan to execute.
     *
     * @return The result from provisioning.
     */
    @Override
    public ProvisioningResult provision(ProvisioningPlan plan) throws sailpoint.connector.ConnectorException {
        ProvisioningResult result = null;

        // Execute each one and assimilate the results.
        for (AbstractRequest req : Util.iterate(plan.getAllRequests())) {
            ProvisioningResult currentResult = handleRequest(req);
            result = assimilateResult(result, currentResult);
        }

        return result;
    }

    /**
     * Execute the permission requests in the given AbstractRequest.
     *
     * @param req  The AccountRequest or ObjectRequest with PermissionRequest to modify the account/group.
     *
     * @return A combined ProvisioningResult from executing all permission requests.
     */
    private ProvisioningResult handleRequest(AbstractRequest req) throws sailpoint.connector.ConnectorException {
        ProvisioningResult result = null;

        try {
            boolean isUser = (req instanceof AccountRequest);
            String userOrGroupId = req.getNativeIdentity();

            for (PermissionRequest permReq : Util.iterate(req.getPermissionRequests())) {
                String target = permReq.getTarget();
                if (null == target) {
                    throw new ConnectorException("target is required for permission requests");
                }

                // Determine whether the target is a container or privileged data, since the provisioning plan does not
                // specify the target type.  For containers, the target is expected to be the container name.  For
                // privileged data, it is expected to be the ID of the PrivilegedData.
                String containerId = this.getContainerIdByName(target);
                target = (null != containerId) ? containerId : target;
                boolean isContainer = (null != containerId);

                // Look to see whether there is already a permission object for this target.
                Map<String,Object> permissionObject = findPermissionObject(target, isContainer, userOrGroupId, isUser);

                // Create a provisioning plan to either create, update, or delete the permission object.
                Plan plan = buildPermissionPlan(permissionObject, target, isContainer, userOrGroupId, isUser, permReq);

                // Set the object type and let the SCIM connector fire our plan.
                super.setObjectType(getPermissionObjectType(isContainer));
                super.provision(plan);

                // Convert the plan's result into a ProvisioningResult, and add it to what we already have.
                for (Result planResult : getResults(plan)) {
                    ProvisioningResult currentResult = createProvisioningResult(planResult);
                    result = assimilateResult(result, currentResult);
                }
            }
        }
        catch (Throwable t) {
            LOG.error("Exception while provisioning in PAMCollector.", t);

            // Any unhandled exceptions should cause a failure.
            result = new ProvisioningResult();
            result.fail(t);
        }

        return result;
    }

    /**
     * Return the ID of the Container with the given name if the object is a Container.  If it is not a Container,
     * this will throw if a PrivilegedData does not exist with the given ID.
     *
     * @param target  The name or ID of the resource to check - either a Container name or PrivilegedData ID.
     *
     * @return The ID of the Container if the given target is a container name, null otherwise.
     *
     * @throws ConnectorException  If there is a problem communicating with the server, or there is not a container
     *     or privileged data with the given target.
     */
    private String getContainerIdByName(String target) throws sailpoint.connector.ConnectorException {
        String containerId = null;

        // First, try to read it as a container by searching by name.
        super.setObjectType(OBJECT_TYPE_CONTAINER);
        Filter filter = new Filter();
        filter.add(ATTR_NAME, Operator.EQ, target);
        Iterator<Map<String,Object>> it = super.iterate(filter);
        if (it.hasNext()) {
            Map<String,Object> container = it.next();
            containerId = (String) container.get(ATTR_ID);
        }

        // If it's not a container, try to read it as privileged data.
        if (null == containerId) {
            try {
                super.setObjectType(OBJECT_TYPE_PRIVILEGED_DATA);
                Map<String,Object> object = super.read(target);

                // This should really throw an ObjectNotFoundException, but just in case, check for null.
                if (null == object) {
                    throwNoContainerOrPrivilegedDataException(target);
                }
            }
            catch (ObjectNotFoundException e) {
                throwNoContainerOrPrivilegedDataException(target);
            }
        }

        return containerId;
    }

    /**
     * Throw an exception indicating that neither a container or privileged data exists with the given ID.
     */
    private void throwNoContainerOrPrivilegedDataException(String id) throws sailpoint.connector.ConnectorException {
        throw new sailpoint.connector.ConnectorException("Expected a container or privileged data with ID " + id);
    }

    /**
     * Return the ContainerPermission or PrivilegedDataPermission that matches the given criteria.
     *
     * @param targetId  The ID of the Container (if isContainer is true), or PrivilegedData otherwise.
     * @param isContainer  Whether the targetId is referring to a Container.
     * @param userOrGroupId  The ID of the User or Group that is referenced by the permission object.
     * @param isUser  Whether the userOrGroupId is referring to a User.
     *
     * @return The ContainerPermission or PrivilegedDataPermission that matches the given criteria.
     *
     * @throws ConnectorException If there are errors communicating with the server, or multiple permission objects
     *      are found that match the given criteria.
     */
    private Map<String,Object> findPermissionObject(String targetId, boolean isContainer,
                                                    String userOrGroupId, boolean isUser)
        throws sailpoint.connector.ConnectorException {

        Map<String,Object> permissionObject = null;

        // Set the object type, so we're iterating over the correct object type.
        super.setObjectType(getPermissionObjectType(isContainer));

        // Look for a permission object that has the target and user/group we're looking for.  Note that we don't have
        // the ID of the permission object in the ProvisioningPlan, so we have to do this query to find the object
        // that matches the criteria.
        Filter filter = new Filter(Conjunct.AND);
        filter.add(getTargetAttribute(isContainer), Operator.EQ, targetId);
        filter.add(getUserOrGroupAttribute(isUser), Operator.EQ, userOrGroupId);

        Iterator<Map<String,Object>> it = super.iterate(filter);
        if (it.hasNext()) {
            permissionObject = it.next();

            // This should be unique.
            if (it.hasNext()) {
                throw new sailpoint.connector.ConnectorException("Found multiple permission objects for " + targetId + ":" + userOrGroupId);
            }
        }

        return permissionObject;
    }

    /**
     * Return the object type to use for a Container or PrivilegedData.
     */
    private static String getPermissionObjectType(boolean isContainer) {
        return (isContainer) ? OBJECT_TYPE_CONTAINER_PERMISSION : OBJECT_TYPE_PRIVILEGED_DATA_PERMISSION;
    }

    /**
     * Return the name of the target attribute on a permission object that refers to either a container or privileged
     * data.
     */
    private static String getTargetAttribute(boolean isContainer) {
        return (isContainer) ? "container.value" : "privilegedData.value";
    }

    /**
     * Return the name of the attribute on a permission object that refers to either a user or group.
     */
    private static String getUserOrGroupAttribute(boolean isUser) {
        return (isUser) ? "user.value" : "group.value";
    }

    /**
     * Create a Plan to execute against the ContainerPermissions or PrivilegedDataPermissions endpoints to modify a
     * user or group's access to a target.
     *
     * @param existingPermission  The existing ContainerPermission or PrivilegedDataPermission object if this is a modify.
     * @param targetId  The ID of the Container or PrivilegedData on which access is being modified.
     * @param isContainer  Whether the targetId refers to a container or privileged data.
     * @param userOrGroupId  The ID of the User or Group for which access is being modified.
     * @param isUser  Whether the userOrGroupId refers to a user or a group.
     * @param permReq  The permission request with the rights and the operation to perform.
     *
     * @return  The plan to execute.
     */
    private Plan buildPermissionPlan(Map<String,Object> existingPermission, String targetId, boolean isContainer,
                                     String userOrGroupId, boolean isUser, PermissionRequest permReq)
        throws sailpoint.connector.ConnectorException {

        Plan plan = new Plan();
        Request objReq = new Request();
        plan.add(objReq);

        // For a create, we need to set up a full plan.
        if (null == existingPermission) {
            objReq.setOperation(Request.Operation.Create);

            // Set the container/privileged data.
            objReq.add(new Item(getTargetAttribute(isContainer), Item.Operation.Set, targetId));

            // Set the user/group.
            objReq.add(new Item(getUserOrGroupAttribute(isUser), Item.Operation.Set, userOrGroupId));
        }
        else if (isDelete(existingPermission, permReq)) {
            // If all permissions are being removed, remove the object completely.
            objReq.setOperation(Request.Operation.Delete);
            objReq.setId((String) existingPermission.get(ATTR_ID));
        }
        else {
            // For a modify, just set the native identifier of the existing permission.
            objReq.setOperation(Request.Operation.Update);
            objReq.setId((String) existingPermission.get(ATTR_ID));
        }

        // If this is a delete, don't add the permission requests.
        if (!Request.Operation.Delete.equals(objReq.getOperation())) {
            // Add an attribute request to modify the rights of the permission object.
            objReq.add(new Item("rights", mapOperation(permReq.getOperation()), permReq.getRightsList()));
        }

        return plan;
    }

    /**
     * Return true if the given PermissionRequest is requesting for all rights on the given ContainerPermission or
     * PrivilegedDataPermission object to be removed, which would lead to the permission object to be deleted.
     *
     * @param permObj  The existing ContainerPermission or PrivilegedDataPermission.
     * @param permReq  The PermissionRequest which is modifying permissions on the given permission object.
     *
     * @return True if the all rights are being removed from the given permission object, false otherwise.
     */
    private static boolean isDelete(Map<String,Object> permObj, PermissionRequest permReq) {
        boolean isDelete = false;

        if (ProvisioningPlan.Operation.Remove.equals(permReq.getOperation())) {
            @SuppressWarnings("unchecked")
            List<String> existingRights = (List<String>) permObj.get(ATTR_RIGHTS);
            List<String> rightsToRemove = permReq.getRightsList();

            // This is a poor-man's order-insensitive list equality check.
            isDelete = (existingRights.containsAll(rightsToRemove) && rightsToRemove.containsAll(existingRights));
        }

        return isDelete;
    }

    /**
     * Convert the given ProvisioningPlan operation to an operation used by openconnector Items.
     */
    private static Item.Operation mapOperation(ProvisioningPlan.Operation op) throws sailpoint.connector.ConnectorException {
        // If there is no operation, default to Set.
        if (null == op) {
            return Item.Operation.Set;
        }

        switch (op) {
        case Add:
            return Item.Operation.Add;
        case Remove:
        case Revoke:
            return Item.Operation.Remove;
        case Set:
            return Item.Operation.Set;
        case Retain:
        default:
            throw new sailpoint.connector.ConnectorException("Unknown operation type: " + op);
        }
    }

    /**
     * Return a list with all of the results off of the given plan.
     */
    private List<Result> getResults(Plan plan) {
        List<Result> results = new ArrayList<>();

        if (null != plan) {
            if (null != plan.getResult()) {
                results.add(plan.getResult());
            }

            for (Request request : Util.iterate(plan.getRequests())) {
                if (null != request.getResult()) {
                    results.add(request.getResult());
                }
            }
        }

        return results;
    }

    /**
     * Create a ProvisioningResult with information from both of the given (possibly null) results.
     *
     * @param result1  The first result to assimilate.
     * @param result2  The second result to assimilate.
     *
     * @return An assimilated result, or null if both results are null.
     */
    private ProvisioningResult assimilateResult(ProvisioningResult result1, ProvisioningResult result2) {
        ProvisioningResult assimilated = null;

        // Only so something if there is at least one, non-null result;
        if ((null != result1) || (null != result2)) {
            // If one of the results is null, return the non-null result.
            if ((null != result1) && (null == result2)) {
                assimilated = result1;
            }
            else if ((null == result1) && (null != result2)) {
                assimilated = result2;
            }
            else {
                // Both are non-null.  Grab the interesting bits from each.
                String status = getHighestPriorityStatus(result1.getStatus(), result2.getStatus());
                List<Message> errors = combineLists(result1.getErrors(), result2.getErrors());
                List<Message> warnings = combineLists(result1.getWarnings(), result2.getWarnings());

                assimilated = new ProvisioningResult();
                assimilated.setStatus(status);
                assimilated.setErrors(errors);
                assimilated.setWarnings(warnings);
            }
        }

        return assimilated;
    }

    /**
     * Return a List that has all elements from both of the given (possibly null) lists.
     *
     * @param list1  The first list to add.
     * @param list2  The second list to add.
     *
     * @return A List that has all elements from both of the given (possibly null) lists, or null if both lists are null.
     */
    private static <T> List<T> combineLists(List<? extends T> list1, List<? extends T> list2) {
        List<T> all = null;

        if ((null != list1) || (null != list2)) {
            all = new ArrayList<T>();

            if (null != list1) {
                all.addAll(list1);
            }

            if (null != list2) {
                all.addAll(list2);
            }
        }

        return all;
    }

    /**
     * Return the most relevant provisioning result status that should be returned from the two given statuses.
     *
     * @param status1  The first status (possibly null).
     * @param status2  The second status (possibly null).
     *
     * @return The most relevant provisioning result status, or null if both statuses are null.
     */
    private static String getHighestPriorityStatus(String status1, String status2) {
        // An array of statuses, ordered from least priority to highest.
        List<String> rankedStatuses =
            Arrays.asList(ProvisioningResult.STATUS_COMMITTED, ProvisioningResult.STATUS_QUEUED,
                          ProvisioningResult.STATUS_RETRY, ProvisioningResult.STATUS_FAILED);

        // Start by choosing a non-null status.
        String highest = (null != status1) ? status1 : status2;

        // If there are two statuses, figure out which one to use.
        if ((null != status1) && (null != status2)) {
            int status1Idx = rankedStatuses.indexOf(status1);
            int status2Idx = rankedStatuses.indexOf(status2);

            highest = (status1Idx > status2Idx) ? status1 : status2;
        }

        return highest;
    }

    /**
     * Create a ProvisioningResult from the given openconnector Result.
     *
     * @param result  The Result from which to create the ProvisioningResult.
     *
     * @return A ProvisioningResult or null if the given result is null.
     */
    private ProvisioningResult createProvisioningResult(Result result) throws sailpoint.connector.ConnectorException {
        ProvisioningResult provResult = null;

        if (null != result) {
            provResult = new ProvisioningResult();
            provResult.setStatus(mapStatus(result.getStatus()));

            if (!Util.isEmpty(result.getMessages())) {
                List<Message> msgs = new ArrayList<Message>();
                for (String msg : result.getMessages()) {
                    msgs.add(new Message(msg));
                }

                if (result.isFailed()) {
                    provResult.setErrors(msgs);
                }
                else {
                    provResult.setWarnings(msgs);
                }
            }
        }

        return provResult;
    }

    /**
     * Map the given openconnector Result status to a ProvisioningResult status.
     *
     * @param status  The openconnector Result status to map.
     *
     * @return The ProvisioningResult status, or STATUS_COMMITTED if the given status is null.
     *
     * @throws sailpoint.connector.ConnectorException
     */
    private static String mapStatus(Result.Status status) throws sailpoint.connector.ConnectorException {
        // No status maps to committed.
        if (null == status) {
            return ProvisioningResult.STATUS_COMMITTED;
        }

        switch (status) {
        case Committed: return ProvisioningResult.STATUS_COMMITTED;
        case Failed: return ProvisioningResult.STATUS_FAILED;
        case Queued: return ProvisioningResult.STATUS_QUEUED;
        case Retry: return ProvisioningResult.STATUS_RETRY;
        }

        throw new sailpoint.connector.ConnectorException("Unknown result status: " + status);
    }
}
