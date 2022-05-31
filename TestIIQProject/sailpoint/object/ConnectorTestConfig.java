/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import sailpoint.object.Application.Feature;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Configuration that indicates the settings and configuration
 * for each connector while being executed by the 
 * ConnectorTestFramework.
 * 
 * The idea is to extract out the things necessary to execute
 * the tests without requiring code.
 * 
 * The idea is to run each connector to plug into the framework
 * so it can be verified and in working order.
 * 
 * 1) Create 
 * 
 *   In this case the test accountId should be 
 *   generated.
 *   
 *   The rest of the attribute list should be as complete
 *   as possible and include at least all of "typical"
 *   attributes.
 *      
 *   Type objectTypes supported account AND group, if left 
 *   null indicates account.
 *      
 */
public class ConnectorTestConfig extends AbstractXmlObject {    

    /**
     * 
     */
    private static final long serialVersionUID = 9100743153213549695L;

    /**
     * Specifies which type of test is being defined.
     *
     */
    @XMLClass(xmlname="TestType")
    public static enum TestType {        
        create(Feature.PROVISIONING, "lifecycle"),
        modify(Feature.PROVISIONING, "lifecycle"),
        disable(Feature.ENABLE, "lifecycle"),
        enable(Feature.ENABLE, "lifecycle"),      
        delete(Feature.PROVISIONING, "lifecycle"),
        authenticate(Feature.AUTHENTICATE, "lifecycle"), 
        password(Feature.PASSWORD, "lifecycle"),
        userPassword(Feature.CURRENT_PASSWORD, "lifecycle"),
        unlock(Feature.UNLOCK, "lifecycle"),
        lock(Feature.AUTHENTICATE, "lifecycle"),
        get(Feature.NO_RANDOM_ACCESS, "lifecycle"),
        schemaFiltering("lifecycle"),
        discoverSchema(Feature.DISCOVER_SCHEMA,"lifecycle"),
        iterate("lifecycle"),
        exportRO("lifecycle"),
        flushCache("lifecycle"),
        defaultApplication(),
        delta("lifecycle"),
        releaseLicense("lifecycle"),
        testConfiguration(),
        provision(),
        checkStatus();  

        Feature feature;
        String testGroup;
        
        private TestType() {
            feature = null;
        }
        private TestType(Feature feature, String defaultTestGroup ) {
            this(feature);
            testGroup =  defaultTestGroup;
        }
        
        private TestType(Feature feature ) {
            this.feature = feature;
        }
        
        private TestType(String defaultTestGroup) {
            this.testGroup = defaultTestGroup;
        }
        
        public boolean isSupported(Application application) {
            if ( feature == null ) 
                return true;
            
            boolean supported = application.supportsFeature(feature);
            if ( TestType.get.equals(this) && Feature.NO_RANDOM_ACCESS.equals(feature) ) {
                // inverse flag so flip the bit so we can indicate this
                supported = !supported;
            }
            return supported;
        }
        public Feature getFeature() {
            return feature;
        }
        public String getDefaultGroup() {
            return this.testGroup;
        }
    }  
    
    /**
     * Name of the test to be executed.
     */
    String name;
   
    /**
     * Top Level flag that indicates if the test is disabled.
     */
    boolean disable;

    /**
     * Flag that indicates if the tests should use the create
     * test account or use the tests used.
     */
    boolean useCreateAccountForTests;
    
    /**
     * Flag to indicate when the native identity is generated
     * and should be put into testcontext using the native
     * identity stored on the plan as part of the provisioning
     * operation.
     */
    boolean nativeIdentityGenerated;
    
    /**
     * Flag to indicate when the native identity is generated
     * and should be put into testcontext using the native
     * identity stored on the provisioning result as part
     * of the provisioning operation.
     */
    boolean nativeIdentityGeneratedInResult;
    
    /**
     * List of tests and their associated configuration.
     */
    List<ConnectorTest> tests;

    /**
     * List of before tests and their associated configuration.
     */
    List<ConnectorTest> beforeTests;
    
    /**
     * Application that will be used to run the test(s).
     */
    Application application;
    
    /**
     * In some cases the feature string from the registry does
     * not include things like Provisioning by default and 
     * the customer is required to add them manually. When this flag
     * is set to true, the tests will use the defined test 
     * application's feature string.
     */
    boolean useTestAppFeatures;
    
    /**
     * If enabled extra information is sent out to the
     * stdout.
     */
    boolean debug;
    
    /**
     * File that has IdentityIQ objects necessary for the test.
     */
    String importFile;
    
    /**
     * If enabled then use existing application 
      */
    boolean useExistingApplication;
    
    /**
     * if enabled then use testapplication schema instead of application schema from connector registry
     */
    boolean useTestAppSchema;
    /**
     * Name of application which will be use for automation testing
     * */
    String appNameStartWith;
    
    /**
     * Flag to indicate if the test is executed from INOW.        
     */
    private boolean _executedFromInow;
    
    /**
     * Type of application which we are using for execution 
     * */
    String type;
    
    /**
     * For IDN execution 'TestConfig.xml' dynamically get extracted to some
     * temporary files, hence this path is required for further references 
     */
    String TestConfigName;

    /**
     * Agg Framework requires a custom csv file delimiter to separate out the
     * each list entry(specifically when entries also contains comma(,))
     * The default value is set to double pipe '||' which will be overwritten by user input value.
     */
    String csvListDelimiter = "||";

    /**
     * The comma separated values of applicable applications for 
     * given suite or test case.
     */
    String runAgainstTestApps;

    /**
     * This map is generic data holder to carry any data that
     * we want to carry for driving test suites
     */
	private HashMap<String, Object> extraParams;

    /**
     * @return the csvListDelimiter
     */
    public String getCsvListDelimiter() {
        return csvListDelimiter;
    }

    /**
     * @param csvListDelimiter the csvListDelimiter to set
     */
    public void setCsvListDelimiter(String csvListDelimiter) {
        this.csvListDelimiter = Util.isNotNullOrEmpty(csvListDelimiter) ? csvListDelimiter : this.csvListDelimiter;
    }

    /**
     * @return the testConfigName
     */
    public String getTestConfigName() {
        return TestConfigName;
    }

    /**
     * @param testConfigName the testConfigName to set
     */
    public void setTestConfigName(String testConfigName) {
        TestConfigName = testConfigName;
    }

    public ConnectorTestConfig() {         
    }   

    public boolean is_executedFromInow() {
        return _executedFromInow;
    }

    public void set_executedFromInow(boolean _executedFromInow) {
        this._executedFromInow = _executedFromInow;
    }

    @XMLProperty
    public boolean isDisable() {
        return disable;
    }
    public void setDisable(boolean disable) {
        this.disable = disable;
    }
    
    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ConnectorTest> getTests() {
        return tests;
    }
    public void setTests(List<ConnectorTest> tests) {
        this.tests = tests;
    }

    /**
     * gets beforeTests
     * @return List of ConnectorTest
     */
    @XMLProperty(xmlname="BeforeConnectorTests")
    public List<ConnectorTest> getBeforeTests() {
        return beforeTests;
    }

    /**
     * sets beforeTests
     * @param tests List of ConnectorTest
     */
    public void setBeforeTests(List<ConnectorTest> tests) {
        this.beforeTests = tests;
    }

    @XMLProperty
    public boolean isUseCreateAccountForTests() {
        return useCreateAccountForTests;
    }

    public void setUseCreateAccountForTests(boolean useCreateAccountForTests) {
        this.useCreateAccountForTests = useCreateAccountForTests;
    }
    
    @XMLProperty(xmlname="TestApplication")            
    public Application getApplication() {
        return application;
    }
    
    public void setApplication(Application application) {
        this.application = application;
    }    

    @XMLProperty
    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @XMLProperty
    public boolean isNativeIdentityGenerated() {
        return nativeIdentityGenerated;
    }

    public void setNativeIdentityGenerated(boolean nativeIdentityGenerated) {
        this.nativeIdentityGenerated = nativeIdentityGenerated;
    }    

    @XMLProperty
    public boolean isNativeIdentityGeneratedInResult() {
        return nativeIdentityGeneratedInResult;
    }

    public void setNativeIdentityGeneratedInResult(boolean nativeIdentityGeneratedInResult) {
        this.nativeIdentityGeneratedInResult = nativeIdentityGeneratedInResult;
    }

    @XMLProperty
    public boolean isUseTestAppFeatures() {
        return useTestAppFeatures;
    }

    public void setUseTestAppFeatures(boolean useTestAppFeatures) {
        this.useTestAppFeatures = useTestAppFeatures;
    }
    
    @XMLProperty
    public String getImportFile() {
        return importFile;
    }

    public void setImportFile(String importFile) {
        this.importFile = importFile;
    }
    
    @XMLProperty
    public boolean isUseExistingApplication() {
        return useExistingApplication;
    }

    public void setUseExistingApplication(boolean useExistingApplication) {
        this.useExistingApplication = useExistingApplication;
    }
    
    @XMLProperty
    public boolean isUseTestAppSchema() {
        return useTestAppSchema;
    }

    public void setUseTestAppSchema(boolean useTestAppSchema) {
        this.useTestAppSchema = useTestAppSchema;
    }

    
    @XMLProperty
    public String getAppNameStartWith() {
        return appNameStartWith;
    }

    public void setAppNameStartWith(String appNameStartWith) {
        this.appNameStartWith = appNameStartWith;
    }
    
    @XMLProperty
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the comma separated values of applicable applications for 
     * given suite or test case.
     * @return runAgainstTestApps
     */
    @XMLProperty
    public String getRunAgainstTestApps() {
        return runAgainstTestApps;
    }

    /**
     * Set the application names for which the test suite or test case
     * should execute.
     * @param runAgainstTestApps
     */
    public void setRunAgainstTestApps(String runAgainstTestApps) {
        this.runAgainstTestApps = runAgainstTestApps;
    }

    /**
     * Given a type of test, return all of the tests that match
     * the type defined in the TestConfig.
     * 
     * @param type Test type to filter by
     * @return List of the tests for the particular type
     */
    public List<ConnectorTest> getTests(TestType type) {
        List<ConnectorTest> matches = new ArrayList<ConnectorTest>();
        if ( tests != null ){
            for ( ConnectorTest test : tests ) {
                if ( test.type.equals(type) ) {
                    matches.add(test);
                }
            }
        }
        return matches;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // ConnectorTest class, which describes each individual test in the test
    // config
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Inner class ConnectorTests that describes the behavior of each
     * individual test. 
     */
    public static class ConnectorTest extends AbstractXmlObject implements Cloneable {
        /**
         * Here to prevent warnings.
         */
        private static final long serialVersionUID = -1118427504381311071L;
        
        /**
         * Optional name given to the test to help identify failures.
         */
        String name;
        /**
         * Optional isPartition flag given to the test to help identify isPartitioning.
         */
        boolean isPartition;
         /**
         * PartitionStatementType key given to the test to help identify the type of the partitioning statement type.
         * for example :-  AD connector xml code for the reference.
         <entry key="searchDNs">
          <value>
            <List>
              <Map>
              <entry key="groupMemberFilterString"/>
              <entry key="groupMembershipSearchDN"/>
              <entry key="iterateSearchFilter"/>
              <entry key="primaryGroupSearchDN"/>
              <entry key="searchDN" value="CN=Users,DC=autodomain,DC=local"/>
              <entry key="searchScope" value="SUBTREE"/>
             </Map>           
           </List>
         </value>
        </entry>
      User need to add the partitionStatementType and isPartitio keys in the xml given for ConnectorTest if he/she want to aggregate the data with partition using TestNG.
      <ConnectorTest maxIterate="100" name="Iterate Accounts" objectType="account" type="iterate" isPartition='true' partitionStatementType = 'searchDNs'/>
         */
        public String partitionStatementType;
        /**
         * Specifies which type of the test we are executing.
         */
        private TestType type;

        /**
         * Object type for the test, this is either
         * group or account.
         */
        private String objectType;

        /**
         * The native identifier that should be used during the
         * test execution. This can be specified on a per test
         * basis, or if the test supports Create operations the
         * account create can be used for their remainder of the tests.
         * 
         * @see ConnectorTestConfig#useCreateAccountForTests
         */
        private String nativeIdentity;

        /**
         * For Create Script to generate an accountId that should
         * be used for each execution of the test.
         * 
         */
        private Script objectIdGenerationScript;
                
        /**
         * For unlock, it is necessary to provide the ability for connectors
         * to lock the account, which typically means authenticating
         * as the user n number of times with an incorrect password.
         * 
         */
        private Script lockScript;
        
        /**
         * A script that will be called when defined to verify that 
         * an object was deleted. Example is when a backend application
         * might set an flag instead of completely removing the object.
         */
        private Script deleteValidationScript;
        
        /**
         * Indicates that the test is a negative test and will
         * throw and exception OR have some type of errors in
         * the provisioning result. Negative tests
         * will require a NegativeTestScript that can 
         * evaluate to make sure that the connector threw
         * the correct exception or put the correct error
         * in the provisioning result.
         * 
         */
        private boolean negative;
		
		String referenceFile;
       
        /**
         * For negative tests, allow a rule to be defined
         * that can evaluate the errors returned in the 
         * plan, or result to determine if the connector
         * failed in the proper way.
         */
        private Script negativeTestValidationScript;

        /**
         * A boolean attribute which sets to true in case the test-ng
         * script has the attribute compareRO='true' in the test cases.
         * This tag is added in test case to perform the comparison of 
         * resourceObject before execution of test and resourceObject after
         * execution of test.
         * 
         */
        private boolean compareRO;
        
        /**
         * A boolean attribute which sets to true in case the test-ng
         * script has the attribute checkAppAttribute='true' in the test cases.
         * This tag is added in test case to check application attribute after
         * execution of test.
         * 
         */
        private boolean checkAppAttribute;        
        
        
        /**
         * For comparison of resource objects , giving a provision
         * to add one tag in test-ng xml. Each test case ,if want to compare 
         * the resourceObject before the test executes and resourceObject
         * after the execution of test. In that case a tag with name
         * "CompareROScript" can be added to provide script for comparison
         * of different resourceObjects.
         * 
         */
        private Script compareROScript;
        
        /**
         * For checking  of application attribute , giving a provision
         * to add one tag in test-ng xml. after aggregation test case ,if want to check 
         * the application attribute. In that case a tag with name
         * "checkAppAttributeScript" can be added to provide script for checking
         * of different application attribute.
         * 
         */
        private Script checkAppAttributeScript;
        
        /**
         * Script to provide various aggregation options and filter Strings.
         */
        private Script aggregationScope;
        
        
        /**
         * This tag can be added in test cases to validate the resource object after an operation.
         * Some connector might throw RO incorrect exception even the provisioning operation is successful. 
         * This mainly happens in connector which supports the multiplex identity. In such scenario you can use this feature to validate the test case.
         * 
         */
        private boolean validateRO;

        /**
         * For validation of resource objects , giving a provision
         * to add one tag in test-ng xml. In the test case a tag with name
         * "ValidateROScript" can be added to provide script for validation
         * of resourceObjects.
         * 
         */
        private Script validateROScript;

        /**
         * Connector which does not support Pass-Through Authentication
         * or authenticate(), can not validate a password
         * in reset password use case. This boolean flag
         * enables such connectors to validate the password
         * using testConfiguration method.
         */
        private boolean validateUsingTestConfiguration;

        /**
         * When 'validateUsingTestConfiguration' flag is true,
         * this 'validationAttrbutes' attribute reads application
         * configuration attributes for the user name and password.
         * The order is important here. example - "user,password".
         * user and password are the IDs of the attributes.
         */
        private String validationAttrbutes;


        /**
         * Used to authenticate where 
         * the password that should be used for the
         * authenticate call is kept.  
         */
        private String password;
        
        /**
         * String of the current password for applications that support
         * end user password reset which requires the existing password.
         */
        private String currentPassword;

        /**
         * If provisioning, the "base" plan that should
         * be used for the test(s).
         */
        private ProvisioningPlan plan;
        
        /**
         * Name of the attribute that should be removed from the schema
         * to test attribute filtering.
         */
        private String attributeToFilter;

        /**
         * Flag to specify when the application does not have a default
         * schema ( DelimitedFile, JDBC, ... ). In those cases  
         * avoid validating the schemas when validating the default
         * application.
         */
        private boolean noDefaultSchema;

        /**
         * The name of the attribute from the create RO that should
         * be used when authenticating. This can be used in cases
         * like AD where the an attribute other then the nativeIdentity
         * should be used during authentication.
         */
        private String authAttribute;

        /**
         * Maximum number of objects to iterate.
         */
        int maxIterate;
        
        /**
         * Debug flag that will add some extra output when enabled
         * at the test level. This can also be added to the entire
         * suite of tests by adding debug to the ConnectorTestConfig.
         * This flag is if you want to debug just a single test.
         */
        private boolean debug;
        
        /**
         * Execution group, to help defined several types of related,
         * dependent tests.
         * 
         */
        private String group;
        
        /**
         * Flag to indicate if the test is currently disabled.        
         */
        private boolean disable;
        
        /**
         * Flag to indicate if the test is for retry.
         */
        private boolean retry;

        /**
         * Flag to indicate if the test is currently disabled for INOW testng execution.        
         */
        private boolean skipForINOW;
        
        private String unverifiableAttributeCSV;

        /**
         * Number of seconds it will delay next tests
         */
        //adding it for #25299
        private int delay;
        /**
         * For negative tests which allows you to add different values of application 
        * attribute for particular test case
        */
       private Script attributeToAdd;
       /**
        * For negative tests which allows you to remove application 
        * attribute for particular test case
        */
       private String attributeToRemove;
       /**
        * Minimum threshold count of entities in aggregation. If final count is less than this minimum threshold then
        * test case should be failed.
        */
       /*
        * This runType String variable will hold value either "sanity" or "regression" which indicate that this test case should run for sanity or regression
        */
       private String runType;
       
       private int minIterate;
       /**
        * Expected iterate count if user knows in advance exact number of objects returned after aggregation
        */
       private int exactIterate;
       
      /**
         *  For adding schema attributes run-time for particular test case
         *
         */
       private Script schemaAttributeToUpdate;
       
       /**
        * The application may have Roles and custom attributes
        * We need to exclude it before comparing it with discovered
        * schema attributes.
        */
       private String schemaAttributeToExclude;
       
       /**
        * This script will be used to compare ROs returned in delta
        * aggregation where you can iterate over set of ROs and 
        * write condition/business logic to validate whether delta
        * is working properly.
        * For e.g.
        * In Workday connector if we hire one worker through script as
        * create operation is not supported by Workday, in compareDeltaROs
        * script we will iterate over ROs fetched in delta aggregation 
        * and check whether newly hired worker is present in delta 
        */
       private Script compareDeltaRO;
       
       /*
        * hold comma separated testApplications name against which we want to run any particular test case
        */
       private String runAgainstTestApps;
       
       /**
        * Attribute value of account to be used for deleting account 
        * before running testNG.This value will be used for comparing
        * ROs attribute value with provided accountAttrValue.
        * e.g. accountAttrValue='abc@xyz.com'
        */
       private String accountAttrValue;
       
       /**
        * Name of schema attribute which is used while comparing RO for deletion 
        * This will be used when we will iterate over RO after aggregation 
        * to compare its attribute name with provided 'deleteAccountStartsWith' 
        * value for deletion
        */
       private String accountSchemaAttrName;
       
       /**
        * This flag will indicate whether to delete all occurrence of matching accounts
        * with provided accountAttrValue.
        * This will be true if you want to delete all occurrences else it will false
        * for deleting first occurrence of matching account
        * Note : This has to be 'true' incase you are using doNotDeleteAccountAttrValue 
        * attribute this attribute will be used for skipping admin user and deleting all 
        * other users.
        */
       private boolean deleteAll;
       
       /**
        * Account attribute value of admin user account. This will be used when you 
        * want to delete all users except admin user. In this case it will hold attribute
        * value of admin user account 
        */
       private String doNotDeleteAccountAttrValue;

       /**
        * Hold result from a request
        */
       private ProvisioningResult result;
       
       /**
        * This attribute will hold schema attribute name which will be 
        * used as identityAttribute for particular test and reseted to
        * original once test case execution is finished
        */
       private String identityAttribute;
       
       /* *
        * counter to maintain the number of attempts that can be made 
        * For instance now its being used in number of invalid authenticate
        * to be made so as to lock the account.
        */
       private int attempts;
       
       /**
        * This variable is used while performing delta aggregation and 
        * connector state map in updated by connector. In testng while 
        * updating application after delta aggregation, Application is picked up
        * from connector which causing some Application class member saving issue.
        * This can be solved by changing way of picking up application before updating
        * to testContext.getApplication but to provided backward compatibilty with
        * exisiting testcases which are using delta aggregation state.
        * This variable can be used by connectors which are using connector state object.
        */
       private boolean disableConnectorStateMapUpdate;

       /**
        * This xml tag would help to attempt no. of invalid authentication
        * call so that given identity/user should get locked.
        */
       private int invalidAuthCount;

       /**
        * This is max number of account can be returned in delta aggregation, exception will be raised if 
        * number of account returned go beyond this limit in delta.
        */
       private int deltaThreshold = 0;

       /**
        * This method will return no. of invalid authentication attempt
        * required for nativeIdentity/user to get locked.
        * @return
        */
       @XMLProperty
       public int getInvalidAuthCount() {
           return invalidAuthCount;
       }

       public void setInvalidAuthCount(int invalidAuthCount) {
           this.invalidAuthCount = invalidAuthCount;
       }

       /**
        * This method will return comma separated testApplications 
        * name against which we want to run any particular test case
        * @return string
        */
       @XMLProperty
        public String getRunAgainstTestApps() {
            return runAgainstTestApps;
        }

        public void setRunAgainstTestApps(String runAgainstTestApps) {
            this.runAgainstTestApps = runAgainstTestApps;
        }

       @XMLProperty
       public Script getAttributeToAdd() {
           return attributeToAdd;
       }
   
       public void setAttributeToAdd(Script attributeToAdd) {
           this.attributeToAdd = attributeToAdd;
       }
       
       @XMLProperty
        public String getRunType() {
           return runType;
        }

        public void setRunType(String runType) {
           this.runType = runType;
        }
       
        @XMLProperty
        public String getAttributeToRemove() {
            return attributeToRemove;
        }

        public void setAttributeToRemove(String attributeToRemove) {
            this.attributeToRemove = attributeToRemove;
        }

		public ConnectorTest() {
        }

        @XMLProperty
        public int getDelay() {
            return delay;
        }

        public void setDelay(int delay) {
            this.delay = delay;
        }

        @XMLProperty
        public int getMaxIterate() {
            return maxIterate;
        }

        public void setMaxIterate(int maxIterate) {
            this.maxIterate = maxIterate;
        }
        @XMLProperty
        public int getMinIterate() {
            return minIterate;
        }

        public void setMinIterate(int minIterate) {
            this.minIterate = minIterate;
        }
        @XMLProperty
        public int getExactIterate() {
            return exactIterate;
        }

        public void setExactIterate(int exactIterate) {
            this.exactIterate = exactIterate;
        }

        @XMLProperty
        public String getNativeIdentity() {
            return nativeIdentity;
        }

        public void setNativeIdentity(String nativeIdentitifer) {
            this.nativeIdentity = nativeIdentitifer;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public ProvisioningPlan getPlan() {
            return plan;
        }

        public void setPlan(ProvisioningPlan plan) {
            this.plan = plan;
        }

        /**
         * djs: default to account here to make crafting the 
         * test xml easier.
         * 
         * @return The object type to be returned.
         */
        @XMLProperty
        public String getObjectType() {
            return (objectType == null) ? "account" : objectType;
        }

        public void setObjectType(String objectType) {
            this.objectType = objectType;
        }

        @XMLProperty
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @XMLProperty
        public TestType getType() {
            return type;
        }

        public void setType(TestType type) {
            this.type = type;
        }

        @XMLProperty
        public Script getObjectIdGenerationScript() {
            return objectIdGenerationScript;
        }

        public void setObjectIdGenerationScript(Script objectIdGenerationScript) {
            this.objectIdGenerationScript = objectIdGenerationScript;
        }

        @XMLProperty
        public Script getLockScript() {
            return lockScript;
        }

        public void setLockScript(Script lockScript) {
            this.lockScript = lockScript;
        }

        @XMLProperty
        public Script getDeleteValidationScript() {
            return deleteValidationScript;
        }

        public void setDeleteValidationScript(Script deleteValidationScript) {
            this.deleteValidationScript = deleteValidationScript;
        }

        @XMLProperty
        public boolean isNegative() {
            return negative;
        }

        public void setNegative(boolean negative) {
            this.negative = negative;
        }

        @XMLProperty
        public Script getNegativeTestValidationScript() {
            return negativeTestValidationScript;
        }

        public void setNegativeTestValidationScript(Script negativeTestValidationScript) {
            this.negativeTestValidationScript = negativeTestValidationScript;
        }

        @XMLProperty
        public String getAttributeToFilter() {
            return attributeToFilter;
        }

        public void setAttributeToFilter(String attributeName) {
            this.attributeToFilter = attributeName;
        }

        @XMLProperty
        public boolean isNoDefaultSchema() {
            return noDefaultSchema;
        }

        public void setNoDefaultSchema(boolean noDefaultSchema) {
            this.noDefaultSchema = noDefaultSchema;
        }

        @XMLProperty
        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        @XMLProperty
        public boolean isDebug() {
            return debug;
        }

        public void setDebug(boolean debug) {
            this.debug = debug;
        }
        
        @XMLProperty
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XMLProperty
        public boolean getIsPartition() {
            return isPartition;
        }

        public void setIsPartition(boolean isPartition) {
            this.isPartition = isPartition;
        }
        @XMLProperty
        public String getPartitionStatementType() {
            return partitionStatementType;                  
        }

        public void setPartitionStatementType(String partitionStatementType) {
            this.partitionStatementType = partitionStatementType;
        }
        @XMLProperty
        public String getAuthAttribute() {
            return authAttribute;
        }

        public void setAuthAttribute(String authAttribute) {
            this.authAttribute = authAttribute;
        }

        @XMLProperty
        public void setGroup(String group) {
            this.group = group;
        }

        public String getGroup() {
            return group;
        }

        @XMLProperty
        public boolean isDisable() {
            return disable;
        }
        
        public boolean isDisabled() {
            return isDisable();
        }

        public void setDisable(boolean disable) {
            this.disable = disable;
        }
		
		@XMLProperty
        public String getReferenceFile() {
            return referenceFile;
        }

        public void setReferenceFile(String referenceFile) {
            this.referenceFile = referenceFile;
        }

        /**
         * ConnectorTest XMLProperty retry
         * @return retry
         */
        @XMLProperty
        public boolean isRetry() {
            return retry;
        }

        @XMLProperty
        public boolean isSkipForINOW() {
            return skipForINOW;
        }

        public void setSkipForINOW(boolean skipForINOW) {
            this.skipForINOW = skipForINOW;
        }

        /**
         * Sets XMLProperty retry
         * @param retry
         */
        public void setRetry(boolean retry) {
            this.retry = retry;
        }

        @XMLProperty
        public void setUnverifiableAttributes(String unverifiableAttributeCSV) {
            this.unverifiableAttributeCSV = unverifiableAttributeCSV;
        }

        public String getUnverifiableAttributes() {
            return unverifiableAttributeCSV;
        }
        
        /**
         * Convenience method to convert csv to a list of attributes.
         * 
         * @return List<String>
         */
        public List<String> getUnverifiableAttributeList() {
            return Util.csvToList(unverifiableAttributeCSV);
        }

        @XMLProperty
        public void setCompareRO(boolean compareRO) {
            this.compareRO = compareRO;
        }

        public boolean isCompareRO() {
            return compareRO;
        }
        
        @XMLProperty
        public void setCheckAppAttribute(boolean checkAppAttribute) {
            this.checkAppAttribute = checkAppAttribute;
        }

        public boolean isCheckAppAttribute() {
            return checkAppAttribute;
        }

        @XMLProperty
        public void setCompareROScript(Script compareROScript) {
            this.compareROScript = compareROScript;
        }

        public Script getCompareROScript() {
            return compareROScript;
        }
        
        @XMLProperty
        public void setCheckAppAttributeScript(Script CheckAppAttributecript) {
            this.checkAppAttributeScript = CheckAppAttributecript;
        }

        public Script getCheckAppAttributeScript() {
            return checkAppAttributeScript;
        }


        @XMLProperty
        public void setValidateRO(boolean validationRO) {
            this.validateRO = validationRO;
        }

        public boolean isValidateRO() {
            return validateRO;
        }

        public int getDeltaThreshold() {
            return deltaThreshold;
        }

        @XMLProperty
        public void setDeltaThreshold(int deltaThreshold) {
            this.deltaThreshold = deltaThreshold;
        }

        @XMLProperty
        public void setValidateROScript(Script validationROScript) {
            this.validateROScript = validationROScript;
        }

        public Script getValidateROScript() {
            return validateROScript;
        }

        @XMLProperty
        public Script getAggregationScope() {
            return aggregationScope;
        }

        public void setAggregationScope(Script aggregationScope) {
            this.aggregationScope = aggregationScope;
        }

        /**
         * Set 'validateUsingTestConfiguration' in the context of text.
         * @param validateUsingTestConfiguration - Value of attribute 'validateUsingTestConfiguration'.
         */
        @XMLProperty
        public void setValidateUsingTestConfiguration(boolean validateUsingTestConfiguration) {
            this.validateUsingTestConfiguration = validateUsingTestConfiguration;
        }

        /**
         * Function gets the boolean value received from the TestCase for attribute 'validateUsingTestConfiguration'
         * @return the boolean value received from the TestCase for attribute 'validateUsingTestConfiguration'
         */
        public boolean isValidateUsingTestConfiguration() {
            return validateUsingTestConfiguration;
        }

        /**
         * Set 'validationAttrbutes' in the context of text.
         * @param validationAttrbutes - Value for attribute 'validationAttrbutes'.
         */
        @XMLProperty
        public void setValidationAttrbutes(String validationAttrbutes) {
            this.validationAttrbutes = validationAttrbutes;
        }

        /**
         * Function gets the String value received from the TestCase for attribute 'validationAttrbutes'
         * @return the String value received from the TestCase for attribute 'validationAttrbutes'
         */
        public String getValidationAttrbutes() {
            return validationAttrbutes;
        }
        public Script getSchemaAttributeToUpdate() {
            return schemaAttributeToUpdate;
        }
        @XMLProperty
        public void setSchemaAttributeToUpdate(Script schemaAttributeToUpdate) {
            this.schemaAttributeToUpdate = schemaAttributeToUpdate;
        }

        public String getSchemaAttributeToExclude() {
          return schemaAttributeToExclude;
        }

        @XMLProperty
        public void setSchemaAttributeToExclude(String schemaAttributeToExclude) {
          this.schemaAttributeToExclude = schemaAttributeToExclude;
        }
        
        /**
         * This method will return attribute value of account which will be used for 
         * deleting account before running testNG.This value will be used for comparing
         * ROs attribute value with provided accountAttrValue.
         * e.g. accountAttrValue='abc@xyz.com'
         * @return return attribute value of account which will be used for deleting account before running testNG
         */
        public String getAccountAttrValue() {
            return accountAttrValue;
        }
        
        /**
         * This method will set attribute value of account which will be used for 
         * deleting account before running testNG.This value will be used for comparing
         * ROs attribute value with provided accountAttrValue.
         * e.g. accountAttrValue='abc@xyz.com'
         * @param accountAttrValue
         *          This will hold value of account attribute
         */
        @XMLProperty
        public void setAccountAttrValue(String accountAttrValue) {
            this.accountAttrValue = accountAttrValue;
        }
        
        /**
         * This method will return name of schema attribute which is used while 
         * comparing RO for deletion.This will be used when we will iterate over RO 
         * after aggregation to compare its attribute name with provided 
         * 'deleteAccountStartsWith' value for deletion
         * @return String
         *               name of account schema attribute
         */
        public String getAccountSchemaAttrName() {
            return accountSchemaAttrName;
        }
        
        /**
         * This method will set name of schema attribute which is used while 
         * comparing RO for deletion.This will be used when we will iterate over RO 
         * after aggregation to compare its attribute name with provided 
         * 'deleteAccountStartsWith' value for deletion
         * @param accountSchemaAttrName
         *              Name of account schema attribute
         */
        @XMLProperty
        public void setAccountSchemaAttrName(String accountSchemaAttrName) {
            this.accountSchemaAttrName = accountSchemaAttrName;
        }
        
        /**
         * This flag will indicate whether to delete all occurrence of matching accounts
         * with provided accountAttrValue.
         * This will be true if you want to delete all occurrences else it will false
         * for deleting first occurrence of matching account
         * Note : This has to be 'true' incase you are using doNotDeleteAccountAttrValue 
         * attribute this attribute will be used for skipping admin user and deleting all 
         * other users.
         * @return boolean
         *          true if deleteAll is set to true 
         */
        public boolean isDeleteAll() {
            return deleteAll;
        }
        
        /**
         * This flag will indicate whether to delete all occurrence of matching accounts
         * with provided accountAttrValue.
         * This will be true if you want to delete all occurrences else it will false
         * for deleting first occurrence of matching account
         * Note : This has to be 'true' incase you are using doNotDeleteAccountAttrValue 
         * attribute this attribute will be used for skipping admin user and deleting all 
         * other users.
         * @param deleteAll
         */
        @XMLProperty
        public void setDeleteAll(boolean deleteAll) {
            this.deleteAll = deleteAll;
        }
        
        /**
         * This method will return account attribute value of admin user account. 
         * This will be used when you want to delete all users except admin user. 
         * In this case it will hold attribute value of admin user account 
         * @return String
         *          admin account attribute value
         */
        public String getDoNotDeleteAccountAttrValue() {
            return doNotDeleteAccountAttrValue;
        }
        
        /**
         * This method will set account attribute value of admin user account. 
         * This will be used when you want to delete all users except admin user. 
         * In this case it will hold attribute value of admin user account
         * @param doNotDeleteAccountAttrValue
         *                  Admin account attribute value
         */
        @XMLProperty
        public void setDoNotDeleteAccountAttrValue(String doNotDeleteAccountAttrValue) {
            this.doNotDeleteAccountAttrValue = doNotDeleteAccountAttrValue;
        }
        
        /**
         * This method will return compareDeltaRos script 
         * provided in testcase
         * @return script
         */
        public Script getCompareDeltaRO() {
            return compareDeltaRO;
        }
        
        /**
         * This method set used to set script provide in testcase to compare
         * delta ROs
         * @param compareDeltaRO script to compare Delta ROs
         */
        @XMLProperty
        public void setCompareDeltaRO(Script compareDeltaRO) {
            this.compareDeltaRO = compareDeltaRO;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public ProvisioningResult getResult() {
            return result;
        }

        public void setResult(ProvisioningResult result) {
            this.result = result;
        }
        
        
        public boolean isDisableConnectorStateMapUpdate() {
            return disableConnectorStateMapUpdate;
        }
        
        @XMLProperty
        public void setDisableConnectorStateMapUpdate(
                boolean disableConnectorStateMapUpdate) {
            this.disableConnectorStateMapUpdate = disableConnectorStateMapUpdate;
        }

        /**
         * Clones this object. Updates the provisioning plan within.
         */
        public ConnectorTest clone() {
            ConnectorTest ct = null;
            try {
                ct = (ConnectorTest) super.clone();
                if (ct != null && ct.getPlan() != null)
                    ct.setPlan(new ProvisioningPlan(ct.getPlan()));
            } catch (CloneNotSupportedException cnse) {
                // never going to happen....
            }
            return ct;
        }

        /**
         * This method returns identity attribute set for particular test 
         * @return String 
         *          new identity attribute
         */
        public String getIdentityAttribute() {
            return identityAttribute;
        }

        /**
         * This attribute is used for setting particular identityAttribute for 
         * given testcase
         * @param identityAttribute
         */
        @XMLProperty
        public void setIdentityAttribute(String identityAttribute) {
            this.identityAttribute = identityAttribute;
        }
        
        @XMLProperty
        public int getAttempts() {
            return attempts;
        }

        /**
         * This attribute is used as counter to maintain the number of attempts that can be made 
         * in the given test case
         * For instance now its being used in number of invalid authenticate
         * to be made so as to lock the account.
         * @param attempts
         */
        public void setAttempts(int attempts) {
            this.attempts = attempts;
        }
        
    }

    /**
     * Put some data into carrier Map
     * 
     * @param extraParams
     */
    public void setExtraParams(HashMap<String, Object> extraParams) {
       this.extraParams = extraParams;
    }

    /**
     * retrieve data from carrier Map
     * 
     * @param extraParams
     * @return
     */
    public HashMap<String, Object> getExtraParams() {
       return extraParams;
    }
}