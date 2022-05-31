/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ApplicationConfigChangeListener;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.MaskUtil;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * The definition of an application.
 *
 */
@Indexes({
    @Index(name="spt_application_created", property="created"),
    @Index(name="spt_application_modified", property="modified")
        })
@XMLClass
public class Application extends SailPointObject implements Cloneable, Describable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -7580397226653210846L;
    private static Log log = LogFactory.getLog(Application.class);

    // jsl - sure why not have three of these, forward to Schema

    /**
     * Name of the group schema
     */
    public static final String SCHEMA_GROUP = Schema.TYPE_GROUP;

    /**
     * Name of the account schema
     */
    public static final String SCHEMA_ACCOUNT = Schema.TYPE_ACCOUNT;

    /**
     * Attribute holding the CompositeDefinition.
     */
    public static final String ATTR_COMPOSITE_DEFINITION = "compositeDefinition";

    /**
     * Attribute holding the manager filter.
     */
    public static final String ATTR_MANAGER_FILTER = "managerCorrelationFilter";

    /**
     * Attribute holding the disable account filter.
     */
    public static final String ATTR_ACCOUNT_DISABLE_FILTER = "disableAccountFilter";

    /**
     * Attribute holding the lock account filter.
     */
    public static final String ATTR_ACCOUNT_LOCK_FILTER = "lockAccountFilter";

    /**
     * Attribute holding the service account filter
     */
    public static final String ATTR_SERVICE_ACCOUNT_FILTER = "serviceAccountFilter";

    /**
     * Attribute holding the RPA/bots account filter
     */
    public static final String ATTR_RPA_ACCOUNT_FILTER = "rpaAccountFilter";

    /**
     * Attribute holding the name of the application "template" that was
     * used when this application was created. Template application are
     * stored in the ConnectorRegistry and cross referenced by the UI 
     * for "type" specific information.
     */
    public static final String ATTR_TEMPLATE_APPLICATION = "templateApplication";

    /**
     * Attribute holding the path to the xhtml page used to render the
     * configuration attributes for an application. This is typically 
     * only stored on the template application.
     */
    public static final String ATTR_FORM_PATH = "formPath";
    
    /**
     * Attribute holding the path to the xhtml page used to render any
     * additional rules for an application. This is typically only
     * stored on the template application.
     */
    public static final String ATTR_FORM_PATH_RULES = "formPathRules";
    
    /**
     * Attribute to indicate that the aggregation process should track
     * native changes at the application level.
     * 
     * This flag alone does not cause change detection, there must
     * also be a life cycle event defined that matches
     * the identity.
     * 
     */
    public static final String ATTR_NATIVE_CHANGE_DETECTION_ENABLED = "nativeChangeDetectionEnabled";
    
    /**
     * List of operations that should be included when performing native change detection.
     */
    public static final String ATTR_NATIVE_CHANGE_OPERATIONS = "nativeChangeDetectionOperations";
    
    /**
     * List of attribute names that should be included when performing native change detection.
     * When null the entitlement attributes will be used.
     */
    public static final String ATTR_NATIVE_CHANGE_ATTRIBUTES = "nativeChangeDetectionAttributes";
    
    /**
     * Option that tells the back end which attributes to detect change against.
     * Defaults to "entitlements", but also supports "userDefined" and "all".
     */    
    public static final String ATTR_NATIVE_CHANGE_ATTRIBUTE_SCOPE = "nativeChangeDetectionAttributeScope";
    
    /**
     * Key from the config that holds the value in the config 
     * that holds the name of the sever side rules that should 
     * be executed before and after provisioning.
     */
    public static final String ATTR_BEFORE_PROVISIONING_RULE = "beforeProvisioningRule";
   
    /**
     * Key from the config that holds the value in the config that holds the name of the
     * sever side rules that should be executed before and 
     * after provisioning.
     */
    public static final String ATTR_AFTER_PROVISIONING_RULE = "afterProvisioningRule";
    
    /**
     * Key from the config that holds a list of rule names that need to be executed 
     * by the connector as part of the connector interaction.  This includes
     * thinks such as the ConnectorBeforeCreate, ConnectorAfterCreate, etc...
     */
    public static final String ATTR_NATIVE_RULES = "nativeRules";

    /**
     * Key from the config that holds a list of all referenced applications.
     * This is used for Unstructured Target correlation.
     */
    public static final String ATTR_REFERENCE_APPS = "referencedApplications";

    /**
     * Key from the config that can be set by the Connector to save state
     * for delta aggregation.  Only the connector knows what this is, 
     * it can be a simple String, a Date, or a complex Map.
     */
    public static final String ATTR_DELTA_AGGREGATION = "deltaAggregation";

    /**
     * Key for the connector state map that contains new app config values
     */
    public static final String CONNECTOR_STATE_MAP = "connectorStateMap";

    /**
     * Key from the config that says where before provision rules are run
     * when the Connector is being called through a proxy Connector.
     * This was added for the CIB, where there are three choices
     * where the before provision rule could run: within the CIB, 
     * within IdentityIQ, or both.
     */
    public static final String ATTR_BEFORE_PROV_RULE_LOCATION = "beforeProvisionRuleLocation";

    /**
     * Key from the config that says where after provision rules are run
     * when the Connector is being called through a proxy Connector.
     * This was added for the CIB, where there are three choices
     * where the after provision rule could run: within the CIB, 
     * within IdentityIQ, or both.
     */
    public static final String ATTR_AFTER_PROV_RULE_LOCATION = "afterProvisionRuleLocation";

    /**
     * Key from the config that says where customization rules are run
     * when the Connector is being called through a proxy Connector.
     * This was added for the CIB, where there are three choices
     * where the customization rule could run: within the CIB, 
     * within IdentityIQ, or both.
     */
    public static final String ATTR_CUSTOMIZATION_RULE_LOCATION = "customizationRuleLocation";

    /**
     * Key that indicates if application should create entitlements as requestable.
     * The values that will contain are "applicationLevel" and "schemaLevel"
     */
    public static final String ATTR_NOT_REQUESTABLE_ENTITLEMENTS = "notRequestableEntitlements";

    /**
     * Value for ATTR_CUSTOMIZATION_RULE_LOCATION that means the
     * rule will be called when the connector calls are NOT being proxied.
     * This is the default if not set.  For CIB, it means that the
     * rule will run within the CIB only.  
     * ConnectorProxy._redirectetdConnector must be null.
     */
    public static final String RULE_LOCATION_LOCAL = "local";

    /**
     * Value for ATTR_CUSTOMIZATION_RULE_LOCATION that means the
     * rule will be called when the connector calls are being proxied.
     * For CIB this means from the IdentityIQ side.
     * ConnectorProxy._redirectedConnector must be non null.
     */
    public static final String RULE_LOCATION_PROXY = "proxy";

    /**
     * Value for ATTR_CUSTOMIZATION_RULE_LOCATION that means the
     * rule will be on both the IdentityIQ and CIB sides.  Unlikely to
     * be used in practice but who knows.
     */
    public static final String RULE_LOCATION_BOTH = "both";

    /**
     * CSV list of attribute names that should be encrypted when persisting
     * applications.
     */
    public static final String CONFIG_ENCRYPTED_CONFIG_ATTRIBUTES = "encrypted";

    /**
     * The name of the attribute in the group schema containing the list 
     * of members.  This was originally LDAPConnector.CONFIG_GROUP_ATTR but
     * moved here because it is needed in several connectors.  
     *
     * @ignore
     * Ideally this would be Schema property like identityAttribute 
     * and the others but the old LDAPConnector convention must be supported.
     */
    public static final String ATTR_GROUP_MEMBER_ATTRIBUTE = "groupMemberAttribute";

    /**
     * Enum that helps to identify the level of not requestable entitlements configuration.
     * The level could be: Application and Schema.
     */
    public enum NotRequestableEntsLvl {

        /**
         * Indicates the configuration to make entitlements not requestable applies for all
         * entitlements of the application.
         */
        APPLICATION("applicationLevel"),

        /**
         * Indicates the configuration to make entitlements not requestable applies only to the
         * entitlements of the schema.
         */
        SCHEMA("schemaLevel");

        NotRequestableEntsLvl(String text) {
            this.text = text;
        }

        private String text;

        public String getText() {
            return text;
        }

        public static NotRequestableEntsLvl getEnum(String value) {
            List<NotRequestableEntsLvl> list = Arrays.asList(NotRequestableEntsLvl.values());
            return list.stream().filter(m -> m.text.equals(value)).findAny().orElse(null);
        }

    }


    /**
     * Optional features that can be supported by an application.
     */
    public enum Feature {

        /**
         * The application supports pass through authentication.
         */
        AUTHENTICATE,

        /**
         * The application supports composite application
         * creation.
         */
        COMPOSITE,

        /**
         * The application supports returning DirectPermissions.
         */
        DIRECT_PERMISSIONS,

        /**
         * The application supports discovering schemas for
         * users and groups.
         */
        DISCOVER_SCHEMA,

        /**
         * The application supports reading if an account 
         * is enabled or disabled.
         */
        ENABLE,

        /**
         * The application supports looking up managers 
         * as they are encountered in a feed. This is
         * the opposite of NO_RANDOM_ACCESS.
         */
        MANAGER_LOOKUP,

        /**
         * The application does not support random access  
         * and the getObject() methods should not be called
         * and expected to perform.
         */
        NO_RANDOM_ACCESS,

        /**
         * The application can serve as a proxy for 
         * another application.  When an application has
         * a proxy, all connector calls made with that
         * application are redirected through the connector
         * for the proxy application.
         */
        PROXY,

        SEARCH,
        TEMPLATE,

        /**
         * The application supports reading if an account 
         * is locked or unlocked.
         */
        UNLOCK,

        /**
         * The application supports returning unstructured
         * Targets.
         */
        UNSTRUCTURED_TARGETS,

        /**
         * @deprecated
         * Now AD support Sharepoint Target Collector by default.
         * No need to add feature string.
         * 
         * The application supports returning unstructured
         * Target data for SharePoint. It will be typically 
         * used by AD, LDAP applications.
         */
        @Deprecated
        SHAREPOINT_TARGET,

        /**
         * The application can both read and write accounts.
         * Having this feature implies that the provision()
         * method is implemented.  It also means that 
         * direct and target permissions can also be provisioned
         * if they can be returned by aggregation.  
         */
        PROVISIONING,

        /**
         * @deprecated
         * We should use {@link #PROVISIONING} on group schema type.
         *
         * The application can both read and write groups.
         * Having this feature implies that the provision()
         * method is implemented.
         */
        @Deprecated
        GROUP_PROVISIONING,

        /**
         * The application can provision accounts synchronously.
         */
        SYNC_PROVISIONING,

        /**
         * The application can provision password changes.
         * Since applications can never read passwords, this
         * is should only be used in conjunction with the
         * PROVISIONING feature.
         */
        PASSWORD,
        
        /**
         * Some application types support verification of the current password
         */
        CURRENT_PASSWORD,
        
        
        /**
         * The application supports requesting accounts without
         * entitlements.
         */
        ACCOUNT_ONLY_REQUEST,
        
        /**
         * The application supports requesting additional accounts.
         */
        ADDITIONAL_ACCOUNT_REQUEST,
        
        /**
         * An application that does not support aggregation.
         */
        NO_AGGREGATION,

        /**
         * The application models group memberships with a member
         * attribute on the group object rather than a groups attribute
         * on the account object.  This effects the implementation
         * of delta account aggregation.
         */
        GROUPS_HAVE_MEMBERS,

        /**
         * Indicates that the connector cannot provision direct or target permissions 
         * for accounts. When DIRECT_PERMISSIONS and PROVISIONING features are present,
         * it is assumed that the connector can also provision direct permissions.
         * This feature disables that assumption and causes permission request
         * to be converted to work items for accounts.
         */
        NO_PERMISSIONS_PROVISIONING,
        
        /**
         * @deprecated {@link #NO_PERMISSIONS_PROVISIONING}
         * Deprecated as of 6.4 in favor of NO_PERMISSIONS_PROVISIONING. This now lives
         * on the Schema Feature Strings
         *
         * Indicates that the connector cannot provision direct or target permissions 
         * for groups. When DIRECT_PERMISSIONS and PROVISIONING features are present,
         * it is assumed that the connector can also provision direct permissions.
         * This feature disables that assumption and causes permission request
         * to be converted to work items for groups.
         */
        @Deprecated
        NO_GROUP_PERMISSIONS_PROVISIONING,
        
        /** This string will be replaced by NO_GROUP_PERMISSIONS_PROVISIONING and
         * NO_PERMISSIONS_PROVISIONING in 6.3.  It should be removed in the release 
         * planned after 6.3       
         */
        @Deprecated
        NO_UNSTRUCTURED_TARGETS_PROVISIONING,
        
        /** This string will be replaced by NO_GROUP_PERMISSIONS_PROVISIONING and
         * NO_PERMISSIONS_PROVISIONING in 6.3.  It should be removed in the release 
         * planned after 6.3
         *
         * @deprecated {@link #NO_GROUP_PERMISSIONS_PROVISIONING}
         */
        @Deprecated
        NO_DIRECT_PERMISSIONS_PROVISIONING
       

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A static configuration cache used by the persistence manager
     * to make decisions about searchable extended attributes.
     */
    static private CacheReference _objectConfig;

    /**
     * For multiplexed/proxied applications only, the name of the application
     * returned by the multiplexing connector. This name is defined
     * by the connector and cannot be changed. The SailPointObject.name
     * can however be changed if desired after the initial aggregation.
     */
    String _proxiedName;

    /**
     * Set when accounts for this application are managed through a 
     * proxy application. When this is set, this application does
     * not normally have a connector. If this application does not
     * have a schema, the schema of the proxy application is "inherited".
     */
    private Application _proxy;

    /**
     * Fully qualified name of the connector class.
     */
    private String _connector;

    /**
     * String that represents the type of application, for example, Active Directory,
     * LDAP, XML, etc...
     */
    private String _type;
    
    /**
     * The CSS class name that represents the icon for the application.
     */
    private String _icon;

    /**
     * Map of attributes used to configure the connection to the application
     */
    private Attributes<String, Object> _config;

    /**
     * List of features supported by this application type.
     */
    private List<Feature> _features;

    /**
     * List of the schemas supported by this application.
     */
    private List<Schema> _schemas;

    /**
     * Flag to indicate if this is the application being used for
     * pass-through authentication.
     */
    private boolean _authenticationResource;

    /**
     * List of Activity datasources.
     */
    private List<ActivityDataSource> _dataSources;

    /**
     * Target Source for target collection
     */
    private TargetSource _targetSource;
    
    /**
     * Starting in 6.0p1 a list
     * of these to support multiple sources per
     * application is stored.
     */
    private List<TargetSource> _targetSources;

    /**
     * List of remediators
     */
    private List<Identity> _remediators;

    /**
     * List of secondary application owners
     */
    private List <Identity> _secondaryOwners;

    /**
     * List of applications that this application is dependent upon
     * for provisioning.
     */
    private List<Application> _dependencies;

    /**
     * Correlation rule to be used during account aggregation
     * from this application.
     */
    Rule _correlationRule;

    /**
     * An optional creation rule used during account aggregation
     * from this application. This rule is called when the correlation
     * rules do not locate an existing Identity and a new one is created
     * and initialized with attributes from this application account.
     *
     * The creation rule will be passed the new Identity object before
     * it is saved and is allowed to modify attributes. Typically this
     * is used to set the name property if the native account id
     * is not readable (for example, use cn rather than dn).
     *
     */
    Rule _creationRule;

    /**
     * An optional correlation rule for correlating a manager attribute
     * value back to one of the Identities.
     */
    Rule _managerCorrelationRule;

    /**
     * An optional rule that can be used to customize the ResourceObjects
     * that are returned from Connectors before they get to the aggregator.
     */
    Rule _customizationRule;

    /**
     * An optional rule that can be used to customize ManagedAttributes that
     * are created automatically through promotion during aggregation, refresh,
     * or the missing entitlement descriptions task.
     */
    Rule _managedAttributeCustomizationRule;
    
    /**
     * Optional class name used when matching applications and profiles.
     */
    String _profileClass;

    /**
     * Scores calculated for this application.
     */
    private ApplicationScorecard _scorecard;

    /**
     * Flag to indicate that application is an authoritative feed.
     * This indicates the application is a source of employee
     * employees and/or contractor data. This flag will be used
     * during aggregation and refresh to compute if an Identity
     * is correlated. If an Identity has an account on an
     * authoritative application its marked correlated.
     */
    boolean _authoritative;

    /**
     * XML based Object that contains the  configuration
     * necessary to correlate accounts to identity cubes.
     */
    CorrelationConfig _accountCorrelationConfig;

    /**
     * Optional plan to specify the explicit entitlements necessary
     * when provisioning this role to a user that does not have it.
     * This is necessary if the Profiles are ambiguous.
     */
    ProvisioningPlan _provisioningPlan;

    /**
     * List of templates for creating new objects.  
     * In practice this will have only one for new accounts, but
     * it is a list for future expansion.
     */
    List<Form> _provisioningForms;

    List<PasswordPolicyHolder> _passwordPolicies;

    /**
     * When true indicates that this application considers
     * account attribute values to be case insensitive. This
     * is used by the provisioner to add case insensitivity to plan
     * compilation. Typically this is not on even values are
     * case insensitive because it is relatively easy to use them
     * consistently in profiles. But directories with long DN's
     * can be a problem because of differences in capitalization
     * style, some people always use "OU" others always use "ou" etc.
     */
    boolean _caseInsensitive;

    /**
     * For applications that support the PROVISIONING feature, this
     * contains various declarations about how provisioning will be
     * performed.
     */
    ProvisioningConfig _provisioningConfig;

    /**
     * An internal property that can be used to group related
     * applications together. This is a generic field, but initially
     * it will only be used to identify applications that all
     * provision through the same SM instance.
     */
    String _cluster;

    /*
     * A list of listeners interested in app config changes
     */
    private List<ApplicationConfigChangeListener> _listeners;
    
    /**
     * When set, indiciates that the application is in a maintenance period.
     * If positive, it is a Date that has the expiration of the period.
     * If -1, it is a non expiring period that must be manually disabled.
     */
    long _maintenanceExpiration;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Application() {
        _features = new ArrayList<>();
        _config = new Attributes<>();
        _type = null;
        _connector = null;
        _authenticationResource = false;
        _authoritative = false;
        _passwordPolicies = new ArrayList<>();
        _scorecard = new ApplicationScorecard();
        _scorecard.setApplication(this);
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitApplication(this);
    }

    /**
     * Traverse the composition hierarchy to cause everything to be loaded
     * from the persistent store.  
     *
     * @ignore
     * This is part of a performance enhancement
     * for the Aggregator, letting it clear the Hibernate cache regularly
     * but still hang onto fully fetched Application objects from earlier
     * sessions.
     *
     * Note that there is no attempt to load any associated Identity
     * objects such as the owner or the remediators. These just pull
     * in way too much information and it is not needed for aggregation.
     */
    @Override
    public void load() {

        if (_proxy != null)
            _proxy.load();

        if (_correlationRule != null)
            _correlationRule.load();

        if (_creationRule != null)
            _creationRule.load();

        if (_managerCorrelationRule != null)
            _managerCorrelationRule.load();

        if (_customizationRule != null)
            _customizationRule.load();

        if (_managedAttributeCustomizationRule != null)
            _managedAttributeCustomizationRule.load();

        if (_schemas != null) {
            for (Schema s : _schemas)
                s.load();
        }

        if (getOwner() != null) {
            getOwner().load();
        }

        if (_secondaryOwners != null) {
            for (Identity secondaryOwner : _secondaryOwners) {
                secondaryOwner.load();
            }
        }

        if (_remediators != null) {
            for (Identity remediator : _remediators) {
                remediator.load();
            }
        }

        if (_accountCorrelationConfig != null) {
            _accountCorrelationConfig.load();
        }

        // these can ref rules needed during refresh provisioning
        if (_provisioningForms != null) {
            for (Form form : _provisioningForms)
                form.load();
        }

        if (_provisioningConfig != null)
            _provisioningConfig.load();

        // Don't need these for account aggregation, but may
        // want them for activity aggregation?
        // Sigh, may need several load methods
        //
        // Actually, we DO need these in order to edit them in the UI.
        // DHC & KG, 4/14/11
        if (_dataSources != null) {
            for (ActivityDataSource ds : _dataSources)
                ds.load();
        }

        if (_targetSource != null)
            _targetSource.load();

        if ( _targetSources != null ) {
            for ( TargetSource ts : _targetSources )
                ts.load();
        }

        // jsl - this is apparently necessary sometimes in refresh
        // tasks, maybe when we create new accounts and generate passwords?
        if (_passwordPolicies != null) {
            for (PasswordPolicyHolder pp : _passwordPolicies) {
                pp.load();
            }
        }

        if (_scorecard != null)
            _scorecard.getApplication();

        if (_dependencies != null) {
            for (@SuppressWarnings("unused") Application dependency : _dependencies) {
                // initialize the collection to prevent lazy load exception but
                // don't fully load the Applications that are depended upon
            }
        }
    }

    /**
     * Prune things that were not fully loaded by load() so 
     * that an XML clone of the object can be done without getting lazy loading errors.
     * This is intended for the Aggregator when it clones application
     * definitions to bootstrap definitions for multiplexed applications.
     * It should be kept in sync with load(), if something is not in load()
     * and it is a lazy Hibernate reference, then it needs to be in here.
     *
     * @ignore
     * !! This does not appear to be called any more, and everything
     * used to null out is now in load().  Think about generalizing
     * this so that the cloners do not have to remember.
     */
    public void pruneUnloadedReferences() {

        _dataSources = null;
        _targetSource = null;
        _scorecard = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration cache
    //
    //////////////////////////////////////////////////////////////////////

    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (_objectConfig == null) {
            // the master cache is maintained over here
            _objectConfig = ObjectConfig.getCache(Application.class);
        }

        if (_objectConfig != null)
            config = (ObjectConfig)_objectConfig.getObject();

        return config;
    }

    /**
     * The extended attributes are combined with the configuration
     * attributes.  There is the potential for name collision but
     * it is not an unreasonable restriction.
     */
    @Override
    public Map<String, Object> getExtendedAttributes() {
        return _config;
    }

    /**
     * This is what tells HibernatePersistenceManager to do the
     * extended attribute promotion whenever saveObject is called.
     */
    public boolean isAutoPromotion() {
        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.LIST)
    public List<PasswordPolicyHolder> getPasswordPolicies() {
        return _passwordPolicies;
    }

    public void setPasswordPolicies(List<PasswordPolicyHolder> passwordPolicies) {
        _passwordPolicies = passwordPolicies;
    }
    
    /**
     * Add another PasswordPolicy
     * @param pp PasswordPolicyHolder to add
     */
    public void addPasswordPolicy(PasswordPolicyHolder pp) {
        if (_passwordPolicies == null) {
            _passwordPolicies = new ArrayList<>();
        }
            
        _passwordPolicies.add(pp);
    }
    
    /**
     * Remove PasswordPolicy by name
     * @param ppName Name of PasswordPolicy to remove
     */
    public PasswordPolicyHolder removePasswordPolicy(String ppName) {
        for (PasswordPolicyHolder pp : _passwordPolicies) {
            if (pp.getPolicyName().equals(ppName)) {
                _passwordPolicies.remove(pp);
                return pp;
            }
        }
        return null;
    }
    
    
    /**
     * Set the features for this Application using a CSV string. This
     * sets the feature list using a list of Feature names.
     * 
     * @see #setFeatures(List)
     * 
     * @ignore
     * NOTE: This method is called by hibernate and the xml
     * factory.
     */
    public void setFeaturesString(String features) {
        List<String> list = Util.csvToList(features);
        if ( ( list != null ) && ( list.size() > 0 ) ){
            _features = new ArrayList<>();
            for ( String st : list ) {
                Feature f = Feature.valueOf(st);
                _features.add(f);
            }
        }
    }

    /**
     * Sets the Feature list for this application. Using a CSV list.
     */
    @XMLProperty
    public String getFeaturesString() {
        String features = null;
        if ( _features != null ) {
           features = Util.listToCsv(_features);
           if ( features != null ) {
              if ( features.length() == 0 ) {
                  features = null;
              }
           }
        }
        return features;
    }

    public List<String> getEntitlementAttributeNames() {
        List<String> nameList = new ArrayList<>();
        if(getSchemas()!=null) {
            for(Schema schema : getSchemas()) {
                for (String name : Util.safeIterable(schema.getEntitlementAttributeNames())) {
                    //IIQSAW-1374 -- return unique attribute names.
                    if (!nameList.contains(name)) {
                        nameList.add(name);
                    }
                }
            }
        }
        return nameList;
    }
    
    /**
     * 
     * Returns a map of displayable entitlement names keyed by raw entitlement names for this application's schemas
     * @param locale Locale used to localize display names
     * 
     * @return Map of raw entitlement names to displayNames
     */
    public Map<String, String> getEntitlements(Locale locale) {
        Map<String, String> names = new HashMap<>();
        if (getSchemas() != null) {
            for(Schema schema : getSchemas()) {
                List<AttributeDefinition> attributes = schema.getAttributes();
                if (attributes != null && !attributes.isEmpty()) {
                    for (AttributeDefinition attribute : attributes) {
                        if (attribute.isEntitlement()) {
                            String displayableName = attribute.getDisplayableName(locale);
                            if (displayableName == null) {
                                displayableName = attribute.getName();
                            }
                            names.put(attribute.getName(), displayableName);
                        }
                    }
                }
            }
        }
        return names;
    }

    /**
     * Returns a list of Features supported by this application.
     */
    public List<Feature> getFeatures() {
        List<Feature> features = null;
        if ( ( _features != null ) && ( _features.size() > 0 ) ) {
            features = _features;
        }
        return features;
    }

    /**
     * Sets the Features that are supported by this application.
     */
    public void setFeatures(List<Feature> features) {
        _features = features;
    }

    /**
     * Adds a feature to this Application.
     */
    public void addFeature(Feature feature) {
        if ( feature != null ) {
            if ( _features == null ) _features = new ArrayList<>();

            if ( !_features.contains(feature) )
                _features.add(feature);
        }
    }

    /**
     * Returns true if the given Feature is supported by
     * this application.
     */
    public boolean supportsFeature(Feature feature) {
        return ( _features != null ) ?  _features.contains(feature) : false;
    }

    /**
     * Whether any 'group' type schema object type supports this feature.
     */
    private boolean anyCollectionSchemaSupportsFeature(Feature feature) {
        boolean supports = false;
        if (getSchemas() != null) {
            for (Schema schema : getSchemas()) {
                if (schema.isGroupAggregation()) {
                    if (schema.supportsFeature(feature)) {
                        supports = true;
                        break;
                    }
                }
            }
        }
        return supports;
    }

    /**
     * Removes a feature from this Application.
     *
     * @param feature Feature to be removed from the Application, if present
     * @return true if the Application supported the Feature
     */
    public boolean removeFeature(Feature feature) {
        if ( feature != null ) {
            if ( _features != null ) {
                return _features.remove(feature);
            }
        }

        return false;
    }

    /**
     * A derived Hibernate property holding a CSV of supported aggregation types.
     * This is used to filter applications that support certain kinds of aggregation.
     * It has a getter for Hibernate but no setter.
     */
    public String getAggregationTypes() {

        String types = null;
        if (_schemas != null) {
            List<String> typeNames = new ArrayList<>();
            for (Schema schema : _schemas) {
                String aggType = schema.getAggregationType();
                if (aggType == null)
                    aggType = schema.getObjectType();
                
                if (Schema.TYPE_ACCOUNT.equals(aggType)) {
                    // always force this at the front since it is usually
                    // what we want and the filter can use startsWith
                    typeNames.add(0, Schema.TYPE_ACCOUNT);
                }
                else if (Schema.TYPE_ALERT.equals(aggType)) {
                    if (!typeNames.contains(Schema.TYPE_ALERT))
                        typeNames.add(Schema.TYPE_ALERT);
                } else if (Schema.TYPE_UNSTRUCTURED.equals(aggType)) {
                    if (!typeNames.contains(Schema.TYPE_UNSTRUCTURED)) {
                        typeNames.add(Schema.TYPE_UNSTRUCTURED);
                    }
                } else {
                    // kludge similar to Schema.isGroupAggregation
                    // until we can decide whether to upgrade the 5
                    // connectors that use non-standard objectTypes, we
                    // have to assume everything else is a group
                    if (!typeNames.contains(Schema.TYPE_GROUP))
                        typeNames.add(Schema.TYPE_GROUP);
                }
            }
            types = Util.listToCsv(typeNames);
        }

        return types;
    }
    
    /**
     * Stub property setter for the derived aggregation types.  Need a setter
     * for Hibernate but it doesn't do anything.
     */
    public void setAggregationTypes(String types) {
    }

    @XMLProperty
    public String getProxiedName() {
        return _proxiedName;
    }

    public void setProxiedName(String name) {
        _proxiedName = name;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="ProxyApplication")
    public Application getProxy() {
        return _proxy;
    }

    public void setProxy(Application app) {
        _proxy = app;
    }

    /**
     * Returns the class name of the connector for this application.
     */
    @XMLProperty
    public String getConnector() {
        return _connector;
    }

    /**
     * Set the string that represents the class that implements this application.
     */
    public void setConnector(String className) {
        _connector = className;
    }

    @XMLProperty
    public String getCluster() {
        return _cluster;
    }

    public void setCluster(String s) {
        _cluster = s;
    }

    /**
     * Returns a string to describe the type of Application.
     * 
     * For example: LDAP, JDBC, etc..
     */
    @XMLProperty
    public String getType() {
        return _type;
    }

    /**
     * Sets a string to describe the type of Application.
     */
    public void setType(String type) {
        _type = type;
    }
    
    /**
     * Gets the icon CSS class name for the application.
     * @return The icon CSS class name.
     */
    @XMLProperty
    public String getIcon() {
        return _icon;
    }
    
    /**
     * Sets the icon CSS class name for the application. 
     * @param icon The icon CSS class name.
     */
    public void setIcon(String icon) {
        _icon = icon;
    }

    /**
     * A Map of attributes that describe the application configuration.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _config;
    }

    /**
     * Sets the application configuration attributes
     */
    public void setAttributes(Attributes<String, Object> config) {
        _config = config;
        if(!Util.isEmpty(_listeners) && _config != null && _config.containsKey(CONNECTOR_STATE_MAP)) {
            notifyListeners();
        }
    }

    /**
     * Sets a named application configuration attribute.
     * Existing values will be overwritten
     */
    public void setAttribute(String name, Object value) {
        if ( _config == null ) {
            _config = new Attributes<>();
        }
        _config.put(name,value);
        if(!Util.isEmpty(_listeners) && _config.containsKey(CONNECTOR_STATE_MAP)) {
            notifyListeners();
        }
    }

    /**
     * Gets a named application configuration attribute.
     */
    public Object getAttributeValue(String name) {
        return _config != null ? _config.get(name) : null;
    }

    /**
     * Returns true if this application is the authentication
     * application for the system.
     */
    @XMLProperty
    public boolean isAuthenticationResource() {
        return _authenticationResource;
    }

    /**
     * Set this application as the pass-through authentication
     * application for the system. There should only be one of
     * these in the system.
     */
    public void setAuthenticationResource(boolean isAuthentication) {
        _authenticationResource = isAuthentication;
    }

    /**
     * Correlation rule to be used during account aggregation
     * from this application.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCorrelationRule() {
        return _correlationRule;
    }

    public void setCorrelationRule(Rule r) {
        _correlationRule = r;
    }

    /**
     * An optional creation rule used during account aggregation
     * from this application. This rule is called when the correlation
     * rules do not locate an existing Identity and a new one is created
     * and initialized with attributes from this application account.
     *
     * The creation rule will be passed the new Identity object before
     * it is saved and is allowed to modify attributes. Typically this
     * is used to set the name property if the native account id
     * is not readable (for example, use cn rather than dn).
     *
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCreationRule() {
        //TODO: Handle this with the addition of rules to the schemas
        return _creationRule;
    }

    public void setCreationRule(Rule r) {
        _creationRule = r;
    }

    /**
     * Return the Creation rule for a given objectType. If not found on the schema,
     * look at the application level
     * @param objectType schema objectType to get creation rule
     * @return the Creation rule for a given objectType
     */
    public Rule getCreationRule(String objectType) {
        Rule r = null;
        Schema s = getSchema(objectType);
        if (s != null) {
            r = s.getCreationRule();
        }

        if (r == null) {
            //Fall back to the app level creation rule
            r = getCreationRule();
        }

        return r;
    }

    /**
     * return the Customization rule for a given object Type. If not found on schema,
     * look at the application level
     * @param objectType schema objectType to look for Customization Rule
     * @return the Customization rule for a given object Type
     */
    public Rule getCustomizationRule(String objectType) {
        Rule r = null;
        Schema s = getSchema(objectType);
        if (s != null) {
            r = s.getCustomizationRule();
        }

        if (r == null) {
            //Fall back to the app level customization rule
            r = getCustomizationRule();
        }

        return r;
    }

    /**
     * return the correlation rule for a given object type. If not found on schema,
     * look at the application level
     * @param objectType schema objectType to get correlation Rule
     * @return the correlation rule for a given object type
     */
    public Rule getCorrelationRule(String objectType) {
        Rule r = null;
        Schema s = getSchema(objectType);
        if (s != null) {
            r = s.getCorrelationRule();
        }

        if (r == null) {
            //Fall back to the app level Correlation Rule
            r = getCorrelationRule();
        }

        return r;
    }

    /**
     * An optional correlation rule for correlating a manager attribute
     * value back to one of our Identities.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getManagerCorrelationRule() {
        return _managerCorrelationRule;
    }

    public void setManagerCorrelationRule(Rule r) {
        _managerCorrelationRule = r;
    }

    /**
     * An optional rule that can be used to customize the ResourceObjects
     * that are returned from Connectors before they get to the aggregator.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCustomizationRule() {
        return _customizationRule;
    }

    public void setCustomizationRule(Rule r) {
        _customizationRule = r;
    }

    /**
     * An optional rule that can be used to customize ManagedAttributes that
     * are promoted from this application.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getManagedAttributeCustomizationRule() {
        return _managedAttributeCustomizationRule;
    }
    
    public void setManagedAttributeCustomizationRule(Rule rule) {
        _managedAttributeCustomizationRule = rule;
    }

    public Rule getRefreshRule(String schemaName) {
        Rule r = null;
        Schema s = getSchema(schemaName);
        if (s != null) {
            r = s.getRefreshRule();
        }
        return r;
    }
    
    /**
     * Optional class name used when matching applications and profiles.
     * Normally during entitlement correlation, the Application is taken
     * from each Link and search for a Profile that references the
     * same Application. In some cases there might be many applications
     * with identical schemas that are all logically supposed to
     * contribute to the same profile or role.  But a Profile can
     * only reference one Application, so this results in model explosion
     * as there must be a different role for each application in the class.
     *
     * Instead, applications can be assigned a profile class. When the
     * entitlement correlator looks for profiles that match a given
     * application, it will first compare the application classes
     * if they are set. If the classes are the same, the application
     * is considered to satisfy the profile even if it is a different object
     * than the one referenced by the profile.
     */
    @XMLProperty
    public String getProfileClass() {
        return _profileClass;
    }

    public void setProfileClass(String s) {
        _profileClass = s;
    }
 
    /**
     * Return true if the object attribute values are case insensitive.
     */
    @XMLProperty
    public boolean isCaseInsensitive() {
        return _caseInsensitive;
    }

    public void setCaseInsensitive(boolean b) {
        _caseInsensitive = b;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ProvisioningConfig getProvisioningConfig() {
        return _provisioningConfig;
    }

    public void setProvisioningConfig(ProvisioningConfig c) {
        _provisioningConfig = c;
    }

    /**
     * This is a derived property that indicates if
     * this Application manages any ManagedResources on other applications.
     *
     * This is here to improve the performance of the PlanCompiler,
     * which otherwise must load all applications to determine
     * if an application is a managed resource.
     *
     * @return True if this Integration includes one or
     * more ManagedResources on other applications.
     */
    public boolean isManagesOtherApps(){

        if (_provisioningConfig != null && !Util.isEmpty(_provisioningConfig.getManagedResources())){
            for(ManagedResource res : _provisioningConfig.getManagedResources()){
                if (res.getApplication() != null && !this.getName().equals(res.getApplication().getName())){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @exclude
     * @deprecated This is a derived property and can not be set.
     */
    @Deprecated
    public void setManagesOtherApps(boolean b) {

    }

    /**
     * When set, indiciates that the application is in a maintenance period.
     * If positive, it is a Date that has the expiration of the period.
     * If -1, it is a non expiring period that must be manually disabled.
     */
    @XMLProperty
    public long getMaintenanceExpiration() {
        return _maintenanceExpiration;
    }

    public void setMaintenanceExpiration(long i) {
        _maintenanceExpiration = i;
    }

    /**
     * Convenience method to set the expiration as a date.
     */
    public void setMaintenanceExpirationDate(Date d) {
        if (d == null)
            _maintenanceExpiration = 0;
        else
            _maintenanceExpiration = d.getTime();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning Forms
    //
    // In 7.0 Templates are converted to Forms as they are read from Hibernate
    // or parsed from old XML.
    //
    // Starting 7.1, the provisioning policy form editor will use Form instead
    // of Templates
    //////////////////////////////////////////////////////////////////////

    public List<Form> getHibernateProvisioningForms() {
        return _provisioningForms;
    }

    public void setHibernateProvisioningForms(List<Form> l) {
        //Bug#25471 -- prevent overwrite _provisionForms after converted from templates,
        //since setXmlTemplates() might be called before this when loading object from database. 
        if (l != null) {
            _provisioningForms = l;
        }
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Form> getProvisioningForms() {
        return _provisioningForms;
    }

    public void setProvisioningForms(List<Form> l) {
        _provisioningForms = l;
    }

    /**
     * Return a provisioning form of the given type (formerly usage) for
     * the given object type.
     */ 
    public Form getProvisioningForm(Form.Type type, String objectType) {
        Form found = null;
        for (Form f : Util.iterate(_provisioningForms)) {
            if (f.isMatch(type, objectType)) {
                found = f;
                break;
            }
        }
        return found;
    }

    /**
     * Return all forms associated to a give object type.
     * For consistency with getProvisioningForm(type,objectType) we allow a missing
     * objectType to be assumed to be TYPE_ACCOUNT.
     */
    public List<Form> getProvisioningForms(String objectType) {
        List<Form> forms = new ArrayList<>();
        for (Form f : Util.iterate(_provisioningForms)) {
            if ((objectType.equalsIgnoreCase(f.getObjectType())) ||
                (f.getObjectType() == null && objectType.equalsIgnoreCase(Schema.TYPE_ACCOUNT))) {
                forms.add(f);
            }
        }
        return forms;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Templates
    //
    // In 7.0 Templates are converted to Forms as they are read from Hibernate
    // or parsed from old XML.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Usual XML trick to convert a <Templates> list into forms.
     * Also works for converting the Hibernate column.
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Templates")
    public List<Template> getXmlTemplates() {
        return null;
    }

    public void setXmlTemplates(List<Template> l) {
        _provisioningForms = Form.convertTemplates(l);
    }

    public List<Template> getOldTemplates() {
        return Template.convertForms(_provisioningForms);
    }

    public void setOldTemplates(List<Template> l) {
        _provisioningForms = Form.convertTemplates(l);
    }

    public Template getOldTemplate(Template.Usage usage, String objectType) {
        Template found = null;
        Form.Type type = Form.convertUsage(usage);
        Form form = getProvisioningForm(type, objectType);
        if (form != null) {
            found = new Template(form);
        }
        return found;
    }

    /**
     * Return all templates associated to a give objectType
     */
    public List<Template> getOldTemplates(String objectType) {

        List<Form> forms = getProvisioningForms(objectType);
        return Template.convertForms(forms);
    }

    /**
     * Unfortunately the property name has to continue to be just "templates"
     * instead "oldTemplates" or something clearer because some connectors
     * search the template list and there may be custom connectors we
     * have to support.
     */
    public List<Template> getTemplates() {
        return getOldTemplates();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Scores
    //
    // In the XML these are similar to Links, they must be inside
    // an Identity.
    //
    //////////////////////////////////////////////////////////////////////

    public ApplicationScorecard getScorecard() {
        return _scorecard;
    }

    public void setScorecard(ApplicationScorecard c) {
        _scorecard = c;
    }

    /**
     * Assign the scorecard and set the back pointer.
     * Do not do this in the Hibernate setter as it marks
     * the object dirty.
     */
    public void addScorecard(ApplicationScorecard c) {
        _scorecard = c;
        if (_scorecard != null)
            _scorecard.setApplication(this);
    }

    /**
     * Alternative property accessor for XML serialization that
     * ensures the bi-directional relationship is maintained.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ApplicationScorecard getXmlScorecard() {
        return _scorecard;
    }

    public void setXmlScorecard(ApplicationScorecard c) {
        addScorecard(c);
    }

    /**
     * Convenience method to dig out the composite score.
     */
    public int getScore() {
        return ((_scorecard != null) ? _scorecard.getCompositeScore() : 0);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Searchable Features - These are mapped as hibernate properties so that
    // they can be searched on.  Their values are always derived from the
    // features list.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns true if this application is a logical application (previously 
     * known as a Composite) using the COMPOSITE feature.
     */
    public boolean isLogical() {
        return supportsFeature(Feature.COMPOSITE);
    }


    /**
     * Returns true if this application supports provisioning using the
     * PROVISIONING or GROUP_PROVISIONING feature.
     *
     * Note that once callers get the list of matching Applications they
     * must check the feature list if they care about only one of these.
     *TODO: If we move group provisioning feature string to the schemas, we will need to change the supportsFeature to 
     *look at all group schemas to determine if any support provisioning
     */
    public boolean isSupportsProvisioning() {
        return (supportsFeature(Feature.PROVISIONING) ||
                anyCollectionSchemaSupportsFeature(Feature.PROVISIONING));
    }
    
    public boolean isSupportsGroupProvisioning() {
        return anyCollectionSchemaSupportsFeature(Feature.PROVISIONING);
    }

    public boolean supportsGroupProvisioning(String schemaObjectType) {
        boolean supports = false;

        Schema schema = getSchema(schemaObjectType);
        if (schema != null) {
            if (schema.supportsFeature(Feature.PROVISIONING)) {
                supports = true;
            }
        }

        return supports;
    }

    /**
     * Returns true if this application supports authentication through the
     * AUTHENTICATE feature.
     */
    public boolean isSupportsAuthenticate() {
        return supportsFeature(Feature.AUTHENTICATE);
    }

    /**
     * Returns true if this application supports account only requests through the
     * ACCOUNT_ONLY_REQUEST feature
     */
    public boolean isSupportsAccountOnly() {
        return supportsFeature(Feature.ACCOUNT_ONLY_REQUEST);
    }

    /**
     * Returns true if this application supports additional account requests
     * through the ADDITIONAL_ACCOUNT_REQUEST feature
     */
    public boolean isSupportsAdditionalAccounts() {
        return supportsFeature(Feature.ADDITIONAL_ACCOUNT_REQUEST);
    }

    /**
     * @return true if this application supports direct permissions
     */
    public boolean isSupportsDirectPermissions() {
        return supportsFeature(Feature.DIRECT_PERMISSIONS);
    }
    
    /**
     * Determines if the application supports aggregation
     * @return True if the application does not support aggregation, false otherwise
     */
    public boolean isNoAggregation() {
        return supportsFeature(Feature.NO_AGGREGATION);
    }

    /**
     * Determines if the schema for the given objectType supports provisioning.
     * If the application has the featureString NO_AGGREGATION, no schema in
     * the app will support provisioning.
     * @param objectType objectType of the schema to test for aggregation
     * @return True if the schema for the given objectType OR the application does not
     *          support provisioning. False otherwise
     */
    public boolean isNoAggregation(String objectType) {
        boolean noAgg = true;
        if (getSchema(objectType) != null) {
            noAgg = getSchema(objectType).supportsFeature(Feature.NO_AGGREGATION);
        }
        return (noAgg || isNoAggregation());
    }

    /**
     * Determines if this application is within a maintenance window.
     */
    public boolean isInMaintenance() {
        boolean maintenance = false;

        if (_maintenanceExpiration < 0) {
            // unlimited
            maintenance = true;
        }
        else if (_maintenanceExpiration > 0) {
            Date exp = new Date(_maintenanceExpiration);
            Date now = new Date();
            maintenance = exp.after(now);
        }
        return maintenance;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Convenience methods for getting configuration attributes.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Removes a named application configuration attribute.
     */
    public void removeAttribute(String name) {
        if ( _config != null ) _config.remove(name);
    }

    /**
     * Retrieve a String valued configuration setting.
     */
    public String getStringAttributeValue(String name) {
        return _config != null ? _config.getString(name) : null;
    }

    /**
     * Retrieve a int valued configuration setting.
     */
    public int getIntAttributeValue(String name) {
        return _config != null ? _config.getInt(name) : Util.otoi(null);
    }

    /**
     * Retrieve a Long valued configuration setting.
     */
    public Long getLongAttributeValue(String name) {
        return _config != null ? _config.getLong(name) : null;
    }

    /**
     * Retrieve a List valued configuration setting.
     */
    @SuppressWarnings("rawtypes")
    public List getListAttributeValue(String name) {
        return _config != null ? _config.getList(name) : null;
    }

    /**
     * Retrieve a boolean valued configuration setting.
     */
    public boolean getBooleanAttributeValue(String name) {
        return _config != null ? _config.getBoolean(name) : Util.otob(null);
    }
    
    /**
     * Retrieve a Date valued configuration setting.
     */
    public Date getDateAttributeValue(String name) {
        return _config != null ? _config.getDate(name) : null;
    }

    /**
     * Returns the schemas for this application.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Schema> getSchemas() {
        return _schemas;
    }

    /**
     * Sets the schemas for this application.
     */
    public void setSchemas(List<Schema> schemas) {
        _schemas = schemas;
    }

    public void addSchema(Schema sch) {
        if (sch != null) {
            if (_schemas == null)
                _schemas = new ArrayList<>();
            _schemas.add(sch);
        }
    }

    /**
     * Returns the schema with a given name.
     */
    public Schema getSchema(String name) {

        Schema found = null;
        if (name != null && _schemas != null) {
            for (Schema schema : _schemas) {
                if ( schema != null ) {
                    if (name.equals(schema.getObjectType())) {
                        found = schema;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Provides information about if the entitlements of the schema are requestables with a given schema name.
     * Table conditions:
     * <p>
     * <table border="1">
     *   <thead>
     *     <tr><th>Not Requestable Entilements Level</th><th>Requestable Schema Entitlement</th><th>Is Schema Requestable?</th></tr>
     *   <thead>
     *   <tbody>
     *     <tr><td><code>null</code></td><td>true</td><td><code>true</code></td></tr>
     *     <tr><td><code>null</code></td><td>false</td><td>UI Scenario NA: <code>true</code></td></tr>
     *     <tr><td><code>applicationLevel</code></td><td>true</td><td><code>false</code></td></tr>
     *     <tr><td><code>applicationLevel</code></td><td>false</td><td>UI Scenario NA: <code>false</code></td></tr>
     *     <tr><td><code>schemaLevel</code></td><td>true</td><td><code>true</code></td></tr>
     *     <tr><td><code>schemaLevel</code></td><td>false</td><td>false</td></tr>
     *   </tbody>
     * </table>
     * </p>
     *
     * @param name
     *            Schema object type
     * @returns <code>true</code> if the entitlements of the schema are
     *          requestables
     */
    public boolean isSchemaRequestable(String name) {
        boolean isSchemaRequestable = true;
        NotRequestableEntsLvl lvl = getNotCreateRequestableEntitlements();
        if (lvl == NotRequestableEntsLvl.APPLICATION) {
            isSchemaRequestable = false;
        } else if (lvl == NotRequestableEntsLvl.SCHEMA) {
            Schema schema = getSchema(name);
            if (schema != null) {
                isSchemaRequestable = !schema.getBooleanAttributeValue(Schema.ATTR_NOT_REQUESTABLE);
            }
        }
        return isSchemaRequestable;
    }

    /**
     * Convenient accessor for the desired schema.
     */
    public Schema getAccountSchema() {
        return getSchema(Schema.TYPE_ACCOUNT);
    }

    /**
     * @deprecated as of 6.4. see {@link #getSchema(String)}
     * @return schema with objectType of "group" if it exists on the app or null if it doesn't exist
     */
    @Deprecated
    public Schema getGroupSchema() {
        return getSchema(Schema.TYPE_GROUP);
    }

    /**
     * Return the association schema associated to a given schema
     * @param s
     * @return the association schema associated to a given schema
     */
    public Schema getAssociationSchema(Schema s) {
        if (s != null) {
            if (Util.isNotNullOrEmpty(s.getAssociationSchemaName())) {
                return getSchema(s.getAssociationSchemaName());
            }
        }
        return null;
    }

    /**
     * Get the primary group attribute from the account schema.
     *
     * @deprecated see {@link #getGroupAttribute(String)}
     */
    @Deprecated
    public AttributeDefinition getGroupAttribute() {
        AttributeDefinition found = null;
        Schema schema = getSchema(Schema.TYPE_ACCOUNT);
        if (schema != null) {
            String name = schema.getGroupAttribute();
            if (name != null)
                found = schema.getAttributeDefinition(name);
            else {
                // Fall back to the pre 6.0 behavior of looking
                // for AttributeDefinitions with group=true.
                // This will normally no longer be seed if you
                // have run the upgrade script on the db, but
                // when importing older test try to tolerate
                // missing gropuAttribute definitions.
                List<AttributeDefinition> atts = schema.getAttributes();
                if (atts != null) {
                    for (AttributeDefinition att : atts) {
                        // must be only one 
                        if (att.isGroup()) {
                            found = att;
                            break;
                        }
                    }
                }
            }
            //Upgraded application objects will have the group attribute derived from the attributeDefinition.schemaObjectType
            if(found == null) {
                found = getGroupAttribute(Schema.TYPE_GROUP);
            }
        }
        return found;
    }

    /**
     * Return all group schemas for the application. This assumes all schemas other than account are group schemas.
     */
    public List<Schema> getGroupSchemas() {
        List<Schema> groupSchemas = new ArrayList<>();
        List<Schema> schemas = getSchemas();
        if(!Util.isEmpty(schemas)) {
            for(Schema sc : schemas) {
                // pre 7.1 anything that wasn't "account" was considered a group,
                // it's more complicated now
                if (sc.isGroupAggregation()) {
                    groupSchemas.add(sc);
                }
            }
        }
        return groupSchemas;
    }

    /**
     * Return a list of Strings containing all objectTypes for the groupSchemas of a given app
     * @return all objectTypes of defined group schemas
     */
    public List<String> getGroupSchemaObjectTypes() {
        List<String> groupSchemaNames = new ArrayList<>();
        for(Schema s : Util.safeIterable(getGroupSchemas())) {
            groupSchemaNames.add(s.getObjectType());
        }
        return groupSchemaNames;
    }

    /**
     * Determine if an application has a group schema for the given objectType
     * @param objectType - objectType of the groupSchema to look for
     * @return true if the application has a group schema for the given objectType
     */
    public boolean hasGroupSchema(String objectType) {
        boolean hasSchema = false;
        List<Schema> groupSchemas = getGroupSchemas();
        if(!Util.isEmpty(groupSchemas)) {
            for(Schema s : groupSchemas) {
                if (Util.nullSafeEq(s.getObjectType(), objectType)) {
                    hasSchema = true;
                    break;
                }
            }
        }
        return hasSchema;
    }

    /**
     * Return the schemaObjectType associated to the Account Schema Attribute
     * @param attributeName Name of the AccountSchema attribute to search for
     * @return schemaObjectType associated to the given Account Schema Attribute
     */
    public String getObjectTypeForAttribute(String attributeName) {
        String objType = null;
        if(attributeName != null) {
            Schema accntSchema = getAccountSchema();
            if(accntSchema != null) {
                AttributeDefinition ad = accntSchema.getAttributeDefinition(attributeName);
                if(ad != null) {
                    objType = ad.getSchemaObjectType();
                }
            }

        }
        return objType;
    }

    /**
     * Convenience method to return all schemas that support the GROUPS_HAVE_MEMBERS feature.
     * @return list of Schemas that support the GROUPS_HAVE_MEMBERS features
     */
    public List<Schema> getGroupsHaveMembersSchemas() {
        List<Schema> gschemas = new ArrayList<>();
        List<Schema> schemas = getSchemas();
        if(!Util.isEmpty(schemas)) {
            for(Schema sc : schemas) {
                // jsl - I guess you can't have this unless Schema.isGroupAggregation is also true
                if(sc.supportsFeature(Feature.GROUPS_HAVE_MEMBERS)) {
                    gschemas.add(sc);
                }
            }
        }
        return gschemas;
    }

    /**
     * This will look through the Account Schema attribute definitions for AttributeDefinitions
     * with the specified objectType
     *
     * @param objectType The object Type of the group for which the attribute is wanted
     * @return The Account Schema AttributeDefintion that maps to the given objectType
     */
    public AttributeDefinition getGroupAttribute(String objectType) {
        Schema schema = getSchema(Schema.TYPE_ACCOUNT);
        if(schema != null && objectType != null) {
            List<AttributeDefinition> atts = schema.getAttributes();
            if(atts != null) {
                for (AttributeDefinition ad : atts) {
                    if (objectType.equals(ad.getSchemaObjectType())) {
                        return ad;
                    }
                }
            }
        }
        //Couldn't find an attribute mapped to the given objectType
        return null;
    }

    /**
     * Gets all group attributes configured on the account schema.
     * @return The group attributes.
     */
    public List<AttributeDefinition> getGroupAttributes() {
        List<AttributeDefinition> groupAttrs = new ArrayList<>();
        for (Schema schema : Util.iterate(getGroupSchemas())) {
            AttributeDefinition groupAttr = getGroupAttribute(schema.getObjectType());
            if (groupAttr != null) {
                groupAttrs.add(groupAttr);
            }
        }

        return groupAttrs;
    }

    /**
     * Return whether or not an attribute of a given objectType is directly assignable to an account. THis
     * looks at the account Schema to determine if an AttributeDefinition has the corresponding objectType.
     *
     * @param objectType objectType of the Attribute in question
     * @return true if the attribute with a given objectType is directly assignable to an account
     */
    public boolean isDirectlyAssignable(String objectType) {
        boolean assignable = false;
        Schema schema = getSchema(Schema.TYPE_ACCOUNT);
        if(null != schema && Util.isNotNullOrEmpty(objectType)) {
            List<AttributeDefinition> ad = schema.getAttributes();
            if(!Util.isEmpty(ad)) {
                for(AttributeDefinition def : ad) {
                    if(Util.nullSafeEq(objectType, def.getSchemaObjectType())) {
                        assignable = true;
                        break;
                    }
                }
            }
        }
        return assignable;
    }

    /**
     * Sets a schema on this application. If the schema exists it is replaced,
     * otherwise it will be added to the list of schemas.
     */
    public void setSchema(Schema schema) {

        if ( _schemas != null)  {
            _schemas.remove(schema);
            _schemas.add(schema);
        } else {
            _schemas = new ArrayList<>();
            _schemas.add(schema);
        }
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Identity> getRemediators() {
        return _remediators;
    }

    public void setRemediators(List<Identity> remediators) {
        _remediators = remediators;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<ActivityDataSource> getActivityDataSources() {
        return _dataSources;
    }

    public int getActivityDataSourcesCount() {
        return (null == _dataSources) ? 0 : _dataSources.size();
    }

    public void setActivityDataSources(List<ActivityDataSource> dataSources) {
        _dataSources = dataSources;
    }

    public ActivityDataSource getActivityDataSource(String name) {
        ActivityDataSource dataSource = null;
        if ( ( _dataSources != null )  && ( name != null )  ) {
           for ( ActivityDataSource src : _dataSources ) {
              String srcName = src.getName();
              if ( srcName != null && name.compareTo(srcName) == 0 )  {
                  dataSource = src;
              }
           }
        }
        return dataSource;
    }

    public ActivityDataSource getActivityDataSourceById(String id) {
        ActivityDataSource dataSource = null;
        if ( ( _dataSources != null )  && ( id != null )  ) {
           for ( ActivityDataSource src : _dataSources ) {
              String srcId = src.getId();              
              if ( srcId != null && id.compareTo(srcId) == 0 )  {                  
                  dataSource = src;
              }
           }
        }
        return dataSource;
    }

    public void addActivityDataSource(ActivityDataSource ads) {
        if (_dataSources == null) {
            _dataSources = new ArrayList<>();
        }

        if (!_dataSources.contains(ads)) {
            _dataSources.add(ads);
        }
    }

    public boolean isActivityEnabled() {
        boolean enabled = false;
        List<ActivityDataSource> sources = getActivityDataSources();
        if ( ( sources != null ) && ( sources.size() > 0 ) ) {
            enabled = true;
        }
        return enabled;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Target Sources used for unstructured scans
    //
    //////////////////////////////////////////////////////////////////////////
    
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<TargetSource> getTargetSources() {
        return _targetSources;
    }
    
    public void setTargetSources(List<TargetSource> sources) {
        _targetSources = sources;        
    }
    
    /**
     * Add a single source to the list of sources on this 
     * application.
     * 
     * @param source TargetSource to add
     */
    public void add(TargetSource source) {
        if ( _targetSources == null ) {
            _targetSources = new ArrayList<>();
        }
        if ( !_targetSources.contains(source) ) {
            _targetSources.add(source);        
        }
    }
    
    public TargetSource getTargetSource(String name) {
        TargetSource dataSource = null;
        if ( ( _targetSources != null )  && ( name != null )  ) {
           for ( TargetSource src : _targetSources ) {
              String srcName = src.getName();
              if ( srcName != null && name.compareTo(srcName) == 0 )  {
                  dataSource = src;
              }
           }
        }
        return dataSource;
    }
    
    public TargetSource getTargetSourceById(String id) {
        TargetSource dataSource = null;
        if ( ( _targetSources != null )  && ( id != null )  ) {
           for ( TargetSource src : _targetSources ) {
              String srcId = src.getId();              
              if ( srcId != null && id.compareTo(srcId) == 0 )  {                  
                  dataSource = src;
              }
           }
        }
        return dataSource;
    }
    
    public int getTargetSourcesCount() {
        return (null == _targetSources) ? 0 : _targetSources.size();
    }
    /**
     * Remove the targetSource from the list of configured 
     * targetsources.
     * 
     * @param targetSource TargetSource to remove
     */
    public void remove(TargetSource targetSource) {
        if ( targetSource != null && _targetSources != null )  {
            _targetSources.remove(targetSource);
        }
    }
    
    /**
     * Flag to indicate that application is an authoritative feed.
     * This indicates the application is a source of employee
     * employees and/or contractor data. This flag will be used
     * during aggregation and refresh to compute if an Identity
     * is correlated. If an Identity has an account on an
     * authoritative application its marked correlated.
     */
    @XMLProperty
    public boolean isAuthoritative() {
       return _authoritative;
    }

    public void setAuthoritative(boolean authoritative) {
       _authoritative = authoritative;
    }

    public CompositeDefinition getCompositeDefinition(){
        return isLogical() ? (CompositeDefinition)getAttributeValue(ATTR_COMPOSITE_DEFINITION) : null;
    }

    public void setCompositeDefinition(CompositeDefinition definition){
        this.setAttribute(ATTR_COMPOSITE_DEFINITION, definition);
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public CorrelationConfig getAccountCorrelationConfig() {
        return _accountCorrelationConfig;
    }

    public void setAccountCorrelationConfig(CorrelationConfig config) {
        _accountCorrelationConfig = config;
    }

    public LeafFilter getManagerCorrelationFilter() {
        LeafFilter filter = null;
        Attributes<String,Object> attributes = getAttributes();
        if ( attributes != null ) {
            filter = (LeafFilter)attributes.get(ATTR_MANAGER_FILTER);
        }
        return filter;
    }

    public void setManagerCorrelationFilter(LeafFilter filter) {
        if (filter == null) {
            getAttributes().remove(ATTR_MANAGER_FILTER);
        }
        else {
            Attributes<String, Object> attributes = getAttributes();
            if (attributes == null)
                attributes = new Attributes<>();
            attributes.put(ATTR_MANAGER_FILTER, filter);
        }
    }

    public List<ListFilterValue> getDisableAccountFilter() {
        List<ListFilterValue> filterList = null;
        Attributes<String,Object> attributes = getAttributes();
        if (attributes != null) {
            filterList = (List<ListFilterValue>)attributes.get(ATTR_ACCOUNT_DISABLE_FILTER);
        }
        return filterList;
    }

    public void setDisableAccountFilter(List<ListFilterValue> filter) {
        Attributes<String,Object> attributes = getAttributes();
        if (attributes == null)
            attributes = new Attributes<>();
        attributes.put(ATTR_ACCOUNT_DISABLE_FILTER, filter);
    }

    public List<ListFilterValue> getLockAccountFilter() {
        List<ListFilterValue> filterList = null;
        Attributes<String,Object> attributes = getAttributes();
        if (attributes != null) {
            filterList = (List<ListFilterValue>)attributes.get(ATTR_ACCOUNT_LOCK_FILTER);
        }
        return filterList;
    }

    public void setServiceAccountFilter(List<ListFilterValue> filter) {
        Attributes<String,Object> attributes = getAttributes();
        if ( attributes == null )
            attributes = new Attributes<>();
        attributes.put(ATTR_SERVICE_ACCOUNT_FILTER, filter);
    }

    public List<ListFilterValue> getServiceAccountFilter() {
        Attributes<String,Object> attributes = getAttributes();
        List<ListFilterValue> filterList = (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_SERVICE_ACCOUNT_FILTER) : null;
        return filterList;
    }

    public void setRpaAccountFilter(List<ListFilterValue> filter) {
        Attributes<String,Object> attributes = getAttributes();
        if ( attributes == null )
            attributes = new Attributes<>();
        attributes.put(ATTR_RPA_ACCOUNT_FILTER, filter);
    }

    public List<ListFilterValue> getRpaAccountFilter() {
        Attributes<String,Object> attributes = getAttributes();
        List<ListFilterValue> filterList = (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_RPA_ACCOUNT_FILTER) : null;
        return filterList;
    }

    public void setLockAccountFilter(List<ListFilterValue> filter) {
        Attributes<String,Object> attributes = getAttributes();
        if ( attributes == null )
            attributes = new Attributes<>();
        attributes.put(ATTR_ACCOUNT_LOCK_FILTER, filter);
    }

    // helper function to return the list of listFilterValue based on type
    // not used currently and can be removed if we don't want it.
    public List<ListFilterValue> getFilterValueForType(String type) {
        Attributes<String,Object> attributes = getAttributes();
        switch (type) {
            case MessageKeys.DISABLED :
               return (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_ACCOUNT_DISABLE_FILTER) : null;
            case MessageKeys.LOCKED :
                return (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_ACCOUNT_LOCK_FILTER) : null;
            case MessageKeys.IDENTITY_TYPE_SERVICE :
                return (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_SERVICE_ACCOUNT_FILTER) : null;
            case MessageKeys.IDENTITY_TYPE_RPA :
                return (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_RPA_ACCOUNT_FILTER) : null;
            case MessageKeys.RULE_TYPE_MANAGERCORRELATION :
                return (attributes != null) ? (List<ListFilterValue>)attributes.get(ATTR_MANAGER_FILTER) : null;
            default:
                log.warn("Type " + type + " is invalid.");
                return null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////       
    //
    // Native Change Detection
    //
    /////////////////////////////////////////////////////////////////////////    
    
    public boolean isNativeChangeDetectionEnabled() {
        return Util.otob(getAttributeValue(ATTR_NATIVE_CHANGE_DETECTION_ENABLED));
    }
    
    public void setNativeChangeDetectionEnabled(boolean enabled) {
        setAttribute(ATTR_NATIVE_CHANGE_DETECTION_ENABLED, enabled);
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getNativeChangeOperations() {
        return (List<String>)getAttributeValue(ATTR_NATIVE_CHANGE_OPERATIONS);
    }
    
    public void setNativeChangeOperations(List<String> ops) {
        setAttribute(ATTR_NATIVE_CHANGE_OPERATIONS, ops);
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getNativeChangeAttributes() {
        return (List<String>)getAttributeValue(ATTR_NATIVE_CHANGE_ATTRIBUTES);
    }
    
    public void setNativeChangeAttributes(List<String> ops) {
        setAttribute(ATTR_NATIVE_CHANGE_ATTRIBUTES, ops);
    }
    
    public void setNativeChangeAttributeScope(String scope) {
        setAttribute(ATTR_NATIVE_CHANGE_ATTRIBUTE_SCOPE, scope);
    }
    
    public String getNativeChangeAttributeScope() {
        return (String)getAttributeValue(ATTR_NATIVE_CHANGE_ATTRIBUTE_SCOPE);
    }

    /**
     * Method that will assimilate all of the configuration
     * settings from an existing application. This is used in the UI
     * when changing the type of application, and it preserves
     * the name, owner, etc..
     */
    public void assimilate(Application app ) {
        setAttributes(app.getAttributes());
        setSchemas(app.getSchemas());
        setFeatures(app.getFeatures());
        setType(app.getType());
        setIcon(app.getIcon());
        setFormPath(app.getFormPath());
        setFormPathRules(app.getFormPathRules());
        setTemplateApplication(app.getTemplateApplication());
        setConnector(app.getConnector());
        setProvisioningForms(app.getProvisioningForms());
        setProvisioningConfig(app.getProvisioningConfig());        
        setTemplateApplication(app.getTemplateApplication());
        setPasswordPolicies(app.getPasswordPolicies());
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Application Templates
    //
    ///////////////////////////////////////////////////////////////////////////

    public String getFormPath() {
        return getStringAttributeValue(ATTR_FORM_PATH);
    }

    public void setFormPath(String key) {
        setAttribute(ATTR_FORM_PATH, key);
    }
    
    public String getFormPathRules() {
        return getStringAttributeValue(ATTR_FORM_PATH_RULES);
    }
    
    public void setFormPathRules(String key) {
        setAttribute(ATTR_FORM_PATH_RULES, key);
    }

    public String getTemplateApplication() {
        return getStringAttributeValue(ATTR_TEMPLATE_APPLICATION);
    }

    public void setTemplateApplication(String key) {
        setAttribute(ATTR_TEMPLATE_APPLICATION, key);
    }

    public void removeActivityDataSource(ActivityDataSource activityDS) {
        if (_dataSources != null) {
            try {
                _dataSources.remove(activityDS);
            } catch (UnsupportedOperationException e) {
                List<ActivityDataSource> updatedDataSources = new ArrayList<>();
                for (ActivityDataSource dataSource : _dataSources) {
                    if (!dataSource.equals(activityDS)) {
                        updatedDataSources.add(dataSource);
                    }
                }
                setActivityDataSources(updatedDataSources);
            }
        }
    }

    public void removeRemediator(Identity remediator) {
        if (_remediators != null) {
            try {
                _remediators.remove(remediator);
            } catch (UnsupportedOperationException e) {
                List<Identity> updatedRemediators = new ArrayList<>();
                for (Identity currentRemediator : _remediators) {
                    if (!currentRemediator.equals(remediator)) {
                        updatedRemediators.add(currentRemediator);
                    }
                }
                setRemediators(updatedRemediators);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Overridden to derive the application and the contained objects.
     */
    @Override
    public Object derive(Resolver res) throws GeneralException {

        Application copy = (Application) super.derive(res);

        copy._dataSources = deriveList(res, _dataSources);
        copy._schemas = deriveList(res, _schemas);

        // Derive the target source.  Use deepCopy so we don't lose the name.
        if (null != _targetSource) {
            copy._targetSource = (TargetSource) _targetSource.deepCopy(res);
            copy._targetSource.clearPersistentIdentity();
        }

        // Null out the scorecard.
        copy._scorecard = null;

        return copy;
    }

    private <T extends SailPointObject> List<T> deriveList(Resolver res, List<T> objs)
        throws GeneralException {

        List<T> derived = null;

        if (null != objs) {
            derived = new ArrayList<>();
            for (T obj : objs) {
                // Use deepCopy so we keep the name.
                @SuppressWarnings("unchecked")
                T newObj = (T) obj.deepCopy(res);
                newObj.clearPersistentIdentity();
                derived.add(newObj);
            }
        }

        return derived;
    }

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    @Override
    public Object clone() {
        Object obj = null;
        try
        {
            // TODO: implement a deep copy!!
            obj = super.clone();
        }
        catch (CloneNotSupportedException e)
        {
            // Won't happen.
        }
        return obj;
    }

    public PasswordPolicyHolder getPasswordPolicyHolderById(String id) {
        PasswordPolicyHolder passPolicy = null;
        if ( ( _passwordPolicies != null )  && ( id != null )  ) {
           for ( PasswordPolicyHolder src : _passwordPolicies ) {
              String srcId = src.getId();
              if ( id.equals(srcId))  {
                  passPolicy = src;
                  break;
              }
           }
        }
        return passPolicy;
    }

    /**
     * @return The value of the group attribute that determines the account hierarchy.  
     * Returns null if this application does not have a group hierarchy
     *
     * @deprecated see {@link #getGroupHierarchyAttribute(String)}
     */
    @Deprecated
    public String getGroupHierarchyAttribute() {

        return getGroupHierarchyAttribute(Schema.TYPE_GROUP);
    }

    /**
     * Returns null if the group schema does not exist, or does not have a group hierarchy
     *
     * @param objectType objectType of the schema we want the hierarchy attribute for
     * @return The hierarchy attribute for the group with the specified objectType
     */
    public String getGroupHierarchyAttribute(String objectType) {
        String attname = null;
        Schema gschema = getSchema(objectType);
        if(gschema != null) {
            attname = gschema.getHierarchyAttribute();
        }

        return attname;
    }
    
    /**
     * Convenience method that returns a List of configuration
     * attribute names. This is not a first class or searchable value
     * the value is read from the configuration of the application.
     * 
     * The value is derived from the CONFIG_ENCRYPTED_CONFIG_ATTRIBUTES
     * configuration map.
     * 
     * @return returns a list of configuration attribute names that should be encrypted
     */
    @SuppressWarnings("unchecked")
    public List<String> getEncrpytedConfigAttributes() {
        
        List<String> encrypted = null;
        Object encryptedConfig = getAttributeValue(CONFIG_ENCRYPTED_CONFIG_ATTRIBUTES);
        if ( encryptedConfig != null ) {
            if ( encryptedConfig instanceof String)
                encrypted = Util.csvToList((String)encryptedConfig);
            else
            if ( encryptedConfig instanceof List ) 
                encrypted = (List<String>)encryptedConfig;                   
        }
        return encrypted;
    }
    
    /**
     * Convenience method that returns a List of configuration attribute names
     * and secret attributes.
     * 
     * @return returns a list of configuration attribute names and secret
     *         attributes that should be encrypted
     */
    public List<String> getEncryptedAndSecretAttrList() {
        List<String> encrypted = new ArrayList<>(
                Arrays.asList(SECRET_ATTRIBUTES));
        Object encryptedConfig = getAttributeValue(CONFIG_ENCRYPTED_CONFIG_ATTRIBUTES);
        if (encryptedConfig != null) {
            if (encryptedConfig instanceof String) {
                encrypted.addAll(Util.csvToList((String) encryptedConfig));
            } else if (encryptedConfig instanceof List) {
                encrypted.addAll((List<String>) encryptedConfig);
            }
        }
        return encrypted;
    }
  
    ////////////////////////////////////////////////////////////////////////////
    //
    // Provisioning Before/After rules
    //
    ///////////////////////////////////////////////////////////////////////////
    
    public String getBeforeProvisioningRule() {
        return Util.getString(_config, ATTR_BEFORE_PROVISIONING_RULE);
    }

    public void setBeforeProvisioningRule(String beforeRule) {
        this.setAttribute(ATTR_BEFORE_PROVISIONING_RULE, beforeRule);
    }
    
    public String getAfterProvisioningRule() {
        return Util.getString(_config, ATTR_AFTER_PROVISIONING_RULE);
    }

    public void setAfterProvisioningRule(String afterRule) {
        setAttribute(ATTR_AFTER_PROVISIONING_RULE, afterRule);
    }   
        
    @SuppressWarnings("unchecked")
    public List<String> getNativeRules() {
        return Util.asList(Util.get(_config, ATTR_NATIVE_RULES));
    }
    
    public void setNativeRules(List<String> nativeRules) {
        setAttribute(ATTR_NATIVE_RULES, nativeRules);    
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Deprecated
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Deprecated in 6.0p in favor of a list of target sources to
     * facilitate support for applications like AD where they
     * can have both a SharePoint collector and an AD file scan 
     * connector.      
     * 
     * @deprecated use {@link #getTargetSources()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="AppTargetSource")
    public TargetSource getTargetSource() {
        return _targetSource;
    }

    /**
     * Deprecated in 6.0p in favor of a list of target sources to
     * facilitate support for applications like AD where they
     * can have both SharePoint collector and AD file scan 
     * collector.
     * 
     * @deprecated {@link #setTargetSources(List)}
     */
    @Deprecated
    public void setTargetSource(TargetSource source) { 
        _targetSource = source;
    }
    
    /**
     * @deprecated use {@link #isLogical()}
     */
    @Deprecated
    public boolean isComposite(){
        return isLogical();
    }
    
    /**
     * @exclude
     * @deprecated Used only by hibernate - add the COMPOSITE feature instead.
     */
    @Deprecated
    public void setLogical(boolean b) {}
    
    /**
     * @exclude
     * @deprecated Used only by hibernate - add the ADDITIONAL_ACCOUNT_REQUEST
     * feature instead.
     */
    @Deprecated
    public void setSupportsAdditionalAccounts(boolean b) {}
    
    /**
     * @exclude
     * @deprecated Used only by hibernate - add the ACCOUNT_ONLY_REQUEST feature
     * instead.
     */
    @Deprecated
    public void setSupportsAccountOnly(boolean b) {}

    
    /**
     * @deprecated Secondary owner concept deprecated and 
     * replaced with workgroups.
     */
    @Deprecated  
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Identity> getSecondaryOwners() {
        return _secondaryOwners;
    }

    /**
     * @deprecated Secondary owner concept deprecated and 
     * replaced with workgroups.
     */
    @Deprecated 
    public void setSecondaryOwners(List<Identity> owners) {
        _secondaryOwners = owners;
    }
    
    /**
     * @exclude
     * @deprecated Used only by hibernate - add the AUTHENTICATE feature instead.
     */
    @Deprecated
    public void setSupportsAuthenticate(boolean b) {}
    
    /**
     * @exclude
     * @deprecated Used only by hibernate - add the PROVISIONING feature instead.
     */
    @Deprecated
    public void setSupportsProvisioning(boolean b) {}
    
    /**
     * @exclude
     * @deprecated Used only by hibernate - add the NO_AGGREGATION feature instead.
     */
    @Deprecated
    public void setNoAggregation(boolean b) {}

    /**
     * Gets a list of applications that this application is dependent upon for
     * provisioning.
     *
     * @return The applications.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Application> getDependencies() {
        return _dependencies;
    }

    /**
     * Sets the applications that this application is dependent upon for
     * provisioning.
     *
     * @param dependencies The applications.
     */
    public void setDependencies(List<Application> dependencies) {
        _dependencies = dependencies;
    }

    /**
     * Adds a provisioning dependency for the application.
     *
     * @param dependency The application 
     */
    public void addDependency(Application dependency) {
        if (null == dependency) {
            return;
        }

        if (null == _dependencies) {
            _dependencies = new ArrayList<>();
        }

        if (!_dependencies.contains(dependency)) {
            _dependencies.add(dependency);
        }
    }

    /**
     * Determines if the application supports synchronous provisioning.
     *
     * @return True if synchronous provisioning is supported, false otherwise.
     */
    public boolean isSyncProvisioning() {
        return supportsFeature(Feature.SYNC_PROVISIONING);
    }

    /**
     * @exclude
     * @deprecated Used only by hibernate - add the SYNC_PROVISIONING feature instead.
     */
    @Deprecated
    public void setSyncProvisioning(boolean b) {}
    
    /**
     * 
     * This method will return a list of blocked field names from 
     * the 'Create' and 'Update' Account provisioning forms.
     * A field is considered blocked if its 'displayOnly' property is 
     * set to true. 
     */

    
    /**
     * @return Map descriptions keyed by locale
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getDescriptions() {
        Map<String,String> map = null;
        Object o = (_config != null) ? _config.get(ATT_DESCRIPTIONS) : null;
        if (o instanceof Map)
            map = (Map<String,String>)o;

        return map;
    }

    /**
     * Set the descriptions
     */
    @Override
    public void setDescriptions(Map<String, String> map) {
        if (map == null) {
            if (_config != null)
                _config.remove(ATT_DESCRIPTIONS);
        } else {
            if (_config == null)
                _config = new Attributes<>();
            _config.put(ATT_DESCRIPTIONS, map);
        }
    }

    /**
     * Incrementally add one description.
     */
    @Override
    public void addDescription(String locale, String desc) {
        new DescribableObject<>(this).addDescription(locale, desc);
    }

    /**
     * Return the description for one locale.
     */
    @Override
    public String getDescription(String locale) {
        return new DescribableObject<>(this).getDescription(locale);
    }

    /**
     * Return the description for one locale.
     */
    @Override
    public String getDescription(Locale locale) {
        return new DescribableObject<>(this).getDescription(locale);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Secure Logging
    //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * List of secret attributes used in different connector's application attribute.
     */
    public final static String[] SECRET_ATTRIBUTES = {
        "password",                        // "password"
        "cmdClientPassword",               // used by RSA Connector
        "adminPassword",                   // used by Airwatch Connector
        "apikey",                          // used by Airwatch Connector
        "secret",                          // used by AWS Connector
        "keystorePwd",                     // used by BMCESS Connector
        "token",                           // used by BOX Net Connector
        "transportUserPassword",           // used by CyberArk Connector
        "group.password",                  // used by JDBC Connector
        "accountId",                       // used by NetSuite Connector
        "keystorepassword",                // used by ServiceNow Connector
        "pkeypassword",                    // used by ServiceNow Connector
        "passwd",                          // used by Siebel Connector
        "admin_password",                  // used by TAM Connector
        "SudoUserPassword",                // used by Unix Connector
        "PassphraseForPrivateKey",          // used by Unix Connector
        "clientSecret",                     // used by GoogleApps/AzureAD Connector
        "private_key",                      // used by Success Factors Connector 
        "privateKey",                        // used by Success Factors Connector
        "IQServicePassword"                 // used by IQ-service for client authentication
    };

    /**
     * Clone a Application removing secret attributes.
     * This is intended for use when logging application.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static public Application getLoggingApp(Application src) {
        Application filtered = null;
        if (src != null) {
            try {
                XMLObjectFactory xf = XMLObjectFactory.getInstance();
                filtered = (Application) xf.clone(src, null);
                Attributes<String,Object> appAttributes = filtered
                        .getAttributes();
                if (appAttributes != null) {
                    String encryptedAttrValue = (String) appAttributes
                            .get("encrypted");
                    Map options = new HashMap<>();
                    options.put(
                            MaskUtil.ENCRYPTED_APPLICATION_ATTRIBUTES,
                            encryptedAttrValue);
                    options.put(
                            MaskUtil.SECRET_APPLICATION_ATTRIBUTES,
                            Arrays.asList(SECRET_ATTRIBUTES));
                    for (Entry<String,Object> entry : appAttributes
                            .entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if (null != key && null != value) {
                            appAttributes.put(key,
                                    MaskUtil.mask(key, value, options));
                        }
                    }
                }
            } catch (Throwable t) {
                log.error("Unable to log application: ", t);
            }
        }
        return filtered;
    }

    /**
     * Return the object description.  Descriptions are generally
     * longer than the name and are intended for display in
     * a multi-line text area.
     * @deprecated Use {@link #getDescription(String locale)} instead
     */
    @Override
    @Deprecated
    @XMLProperty(mode=SerializationMode.ELEMENT, legacy=true)
    public String getDescription() {
        return _description;
    }

    /**
     *  @deprecated Use {@link #addDescription(String locale, String description)}
     */
    @Override
    @Deprecated
    public void setDescription(String s) {
        // Since there is no longer a corresponding column for this property in the Application's table
        // this value will not be persisted in this form.  The method only remains so that
        // we can import legacy Applications from XML.  The PostImportVisitor will 
        // properly add the description to the descriptions map when necessary.
        _description = s;
        new DescribableObject<>(this).logDeprecationWarning(s);
    }   

    /**
     * Registers an application config change listener so as to inform it
     * whenever a change in config occurs
     * @param listener  object interested in listening to app config changes
     */
    public void registerListener(ApplicationConfigChangeListener listener) {
        if(listener != null) {
            if(_listeners == null) {
                _listeners = new ArrayList<>();
            }
            _listeners.add(listener);
        }
    }

    /**
     * Unregisters a change listener
     */
    public void unRegisterListener() {
        if(_listeners != null) {
            _listeners.clear();
            _listeners = null;
        }
    }

    /**
     * Notifies change config listeners that a new application config attribute
     * has been specified
     * @throws RuntimeException
     */
    public void notifyListeners() throws RuntimeException {
        if(!Util.isEmpty(_listeners)) {
            for(ApplicationConfigChangeListener chgListener : _listeners) {
                try {
                    chgListener.updateAppConfig();
                }
                catch(Exception e) {
                    if(log.isErrorEnabled())
                        log.error("Failed to update app config.Error:" + e.getMessage(),e);
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
    }

    /**
     * Obtains the "not requestable" configuration level.
     * It could be at application level or schema level.
     * @return An enum that can be {@link NotRequestableEntsLvl#APPLICATION} or {@link NotRequestableEntsLvl#SCHEMA}
     */
    public NotRequestableEntsLvl getNotCreateRequestableEntitlements() {
        NotRequestableEntsLvl notRequestableEntsLvl = null;
        Object valueObject = getAttributeValue(ATTR_NOT_REQUESTABLE_ENTITLEMENTS);
        if(valueObject instanceof String) {
            notRequestableEntsLvl = NotRequestableEntsLvl.getEnum(String.valueOf(valueObject));
        }
        return notRequestableEntsLvl;
    }
}
