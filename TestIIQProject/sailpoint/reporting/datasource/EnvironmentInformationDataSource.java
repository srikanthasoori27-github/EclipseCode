/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.Certification.Type;
import sailpoint.object.CertificationItem;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Server;
import sailpoint.object.Sort;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.task.Monitor;
import sailpoint.tools.ConvertTools;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * DataSource class for generating Environment Information Report.
 * It provides statistics about IIQ environment.
 * Statistics are based on different IIQ objects, detailed story can be found here -
 *     https://rally1.rallydev.com/#/9956471370ud/detail/userstory/31141099756
 *
 * It holds list of datasources for each object type.
 * Each datasource returns statistics related to that object.
 * It iterates over each datasource and its statistics.
 *
 * @author ketan
 */
public class EnvironmentInformationDataSource implements JavaDataSource {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log LOG = LogFactory.getLog(EnvironmentInformationDataSource.class);

    /** Report column configs */
    private static final String FIELD_OBJECT_TYPE = "objectType";
    private static final String FIELD_PROPERTY = "property";
    private static final String FIELD_VALUE = "value";
    private static final String ATTR_KNOWN_BAD_VERSIONS = "knownBadVersions";
    private static final String[] topIdentityStr = { "most", "second most", "third most", "fourth most", "fifth most" };
    private static final String SQLSERVER_DRIVER_NAME = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String MYSQL_DRIVER_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String ORACLE_DRIVER_NAME = "oracle.jdbc.driver.OracleDriver";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext context;

    /** List of datasources for each object type. */
    private List<EnvInfoDataSource> datasources;
    private Iterator<EnvInfoDataSource> itds;

    /**
     * Result with statistics about environment.
     * Each datasource should populate this result with corresponding statistics.
     * It is a list of rows represented by map contaning reportColumnConfigs.
     */
    private List<Map<String,Object>> result;
    private Iterator<Map<String, Object>> itResult;

    /** Current row of the report. */
    private Map<String, Object> row;

    /** Row estimate for the report. */
    private int estimate;

    /**
     * List of bad known version and OOTB jdbc version
     */
    private HashMap<String, ArrayList<String>> knownBadVersions;
    private String reportlauncherName;
    //////////////////////////////////////////////////////////////////////
    //
    // Overrides
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Initialize helper datasource and result object.
     * Set iterator for this result which will be queried by next and getFieldValue.
     * Also set the size estimate for this report.
     */
    @Override
    public void initialize(SailPointContext context, LiveReport report,
            Attributes<String, Object> arguments, String groupBy, List<Sort> sort)
                throws GeneralException {

        this.context = context;

        setDriverVersionsMap(arguments);
        
        //get launcher/username from arguments
        reportlauncherName=(String) arguments.get(TaskSchedule.ARG_LAUNCHER);
        
        //Create list of datasource objects.
        datasources = new ArrayList<EnvInfoDataSource>();
        datasources.add(new IdentityEnvInfoDataSource());
        datasources.add(new IdentityEntitlementsEnvInfoDataSource());
        datasources.add(new ApplicationEnvInfoDataSource());
        datasources.add(new IntegrationConfigurationDataSource());
        datasources.add(new AccountEnvInfoDataSource());
        datasources.add(new WorkItemEnvInfoDataSource());
        datasources.add(new IdentityRequestEnvInfoDataSource());
        datasources.add(new WorkgroupEnvInfoDataSource());
        datasources.add(new CertificationEnvInfoDataSource());
        datasources.add(new TaskScheduleEnvInfoDataSource());
        datasources.add(new RoleEnvInfoDataSource());
        datasources.add(new PolicyEnvInfoDataSource());
        datasources.add(new EntitlementCatalogEnvInfoDataSource());
        datasources.add(new SystemConfigurationDataSource());
        datasources.add(new ClassificationEnvDataSource());
        itds = datasources.iterator();

        // Create result list about all statistics.
        result = new ArrayList<Map<String, Object>>();
        itResult = result.iterator();

        // Create map to hold current row of the report.
        row = new HashMap<String, Object>();

        // Set the size estimate for this report.
        for (EnvInfoDataSource ds : Util.iterate(datasources)) {
            estimate += ds.getSizeEstimate();
        }
    }

    private void setDriverVersionsMap(Attributes<String, Object> arguments) throws GeneralException{
    	
    	if (arguments.get(ATTR_KNOWN_BAD_VERSIONS) != null && arguments.get(ATTR_KNOWN_BAD_VERSIONS) instanceof Map)
     	    knownBadVersions = (HashMap<String, ArrayList<String>>) arguments.get(ATTR_KNOWN_BAD_VERSIONS);
    	
    }

    /**
     * Return the number of rows in the report.
     * This varies due to stats like application and role etc.
     */
    @Override
    public int getSizeEstimate() throws GeneralException {
        return estimate;
    }

    /**
     * Return true if results has next row.
     * If a datasource result does not have next row, it overwrites
     * result with next datasource statistics.
     */
    @Override
    public boolean next() throws JRException {

        boolean hasNext = false;

        //IIQSR-116 Needed to be able to skip empty datasources and continue with other datasources in the iterator.
        while (!itResult.hasNext() && itds.hasNext()) {
            EnvInfoDataSource dataSource = itds.next();
            try {
                result = dataSource.getStatistics();
            } catch (GeneralException ge) {
                LOG.error("Error getting statistics for datasource - " +
                    dataSource.getClass(), ge);
                throw new RuntimeException(ge);
            }
            itResult = result.iterator();
        }

        // Check if result has rows.
        if (itResult.hasNext()) {
            row = itResult.next();
            hasNext = true;
        }

        return hasNext;
    }

    /**
     * Returns the value for a field.
     */
    @Override
    public Object getFieldValue(String field) throws GeneralException {
        if (!Util.isEmpty(row)) {
            return row.get(field);
        }
        return null;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Unused methods
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public QueryOptions getBaseQueryOptions() {
        // TODO Chart for this report??
        return null;
    }

    @Override
    public String getBaseHql() {
        // TODO Chart for this report??
        return null;
    }

    @Override
    public void setMonitor(Monitor monitor) {}

    @Override
    public void close() {}

    @Override
    public Object getFieldValue(JRField arg0) throws JRException {
        // TODO jrxml for this report??
        return null;
    }

    @Override
    public void setLimit(int startRow, int pageSize) {
        // TODO supported for this report??
    }


    //////////////////////////////////////////////////////////////////////
    //
    // DataSources
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Each object type datasource should implement this interface.
     */
    private interface EnvInfoDataSource {

        /** Select clause for getting the count. */
        static final String SELECT_COUNT = "count(id)";

        /** Get top 5 only. */
        static final int RESULT_LIMIT = 5;

        /**
         * Return rows estimate for datasource.
         */
        int getSizeEstimate() throws GeneralException;

        /**
         * Return result for datasource.
         * Result is a list rows.
         * Each row is a map containing reportColumnConfigs.
         */
        List<Map<String, Object>> getStatistics() throws GeneralException;
    }

    /**
     * Identity statistics datasource.
     * Following statistics are included -
     *     Total Identities
     *     Active Identities
     *     Inactive Identities
     *     Uncorrelated Identities
     *     Identity Snapshots
     *     License Identities (Active + Correlated)
     *     
     *     Identity by Types
     */
    private class IdentityEnvInfoDataSource implements EnvInfoDataSource {

        /** Identity stats contain fixed 6 rows. */
        private static final int ROWS_IDENTITY_STATS = 6;

        /**
         * Return estimate of 6 for identity stats + number of Identity Types (including null)
         */
        @Override
        public int getSizeEstimate() {
            ObjectConfig config = Identity.getObjectConfig();
            List<IdentityTypeDefinition> types = config.getIdentityTypesList();
            if (Util.isEmpty(types)) {
                return ROWS_IDENTITY_STATS;
            } else {
                return ROWS_IDENTITY_STATS + types.size() + 1;
            }
        }

        /**
         * Return identity stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Identity Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Identities.
            int count = context.countObjects(Identity.class, null);
            LOG.debug("    Total Identities - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_TOTAL, count);
            statResult.add(statRow);

            // Get Active Identities.
            QueryOptions qo = new QueryOptions(Filter.eq(Identity.ATT_INACTIVE, false));
            count = context.countObjects(Identity.class, qo);
            LOG.debug("    Active Identities - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_ACTIVE, count);
            statResult.add(statRow);

            // Get Inactive Identities.
            qo = new QueryOptions(Filter.eq(Identity.ATT_INACTIVE, true));
            count = context.countObjects(Identity.class, qo);
            LOG.debug("    Inactive Identities - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_INACTIVE, count);
            statResult.add(statRow);

            // Get Uncorrelated Identities.
            qo = new QueryOptions(Filter.eq(Identity.ATT_CORRELATED, false));
            count = context.countObjects(Identity.class, qo);
            LOG.debug("    Uncorrelated Identities - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_UNCORRELATED, count);
            statResult.add(statRow);

            // Get Identity Snapshots.
            count = context.countObjects(IdentitySnapshot.class, null);
            LOG.debug("    Identity Snapshots - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_SNAPSHOTS, count);
            statResult.add(statRow);

            // Get License identities (Active + Correlated)
            qo = new QueryOptions(Filter.and(Filter.eq(Identity.ATT_INACTIVE, false),
                    Filter.eq(Identity.ATT_CORRELATED, true)));
            count = context.countObjects(Identity.class, qo);
            LOG.debug("    License identities (Active + Correlated) - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_LICENSE, count);
            statResult.add(statRow);

            // Get stats for Identity by Type
            ObjectConfig config = Identity.getObjectConfig();
            List<IdentityTypeDefinition> types = config.getIdentityTypesList();
            if (!Util.isEmpty(types)) {
                for (IdentityTypeDefinition type : types) {
                    statResult.add(getIdentityTypeRow(type));
                }
                //adding row for type unknown
                statResult.add(getIdentityTypeRow(null));
            }

            return statResult;
        }
    }

    private Map<String, Object> getIdentityTypeRow(IdentityTypeDefinition type) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        if (type != null) {
            qo.add(Filter.eq(Identity.ATT_TYPE, type.getName()));
        } else {
            qo.add(Filter.isnull(Identity.ATT_TYPE));
        }
        int count = context.countObjects(Identity.class, qo);
        if (type != null) {
            LOG.debug("    Identity by type :" + type.getName() + " -- " + count);
        } else {
            LOG.debug("    Identity by type : null -- " + count);
        }
        return getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY_TYPE,
                type != null ? type.getDisplayName() : MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_TYPE_UNKNOWN,
                count);
    }

    /**
     * IdentityEntitlements statistics datasource.
     * Following statistics are included -
     *     Total number of IdentityEntitlements
     *     Top 5 Identities with the most entitlements
     */
    private class IdentityEntitlementsEnvInfoDataSource implements EnvInfoDataSource {

        /** IdentityEntitlements stats contain fixed 6 rows. */
        private static final int ROWS_IDENTITY_ENTITLEMENTS_STATS = 6;

        /**
         * Return estimate of 6 for IdentityEntitlements stats.
         */
        @Override
        public int getSizeEstimate() {
            return ROWS_IDENTITY_ENTITLEMENTS_STATS;
        }

        /**
         * Return IdentityEntitlements stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Identity Entitlements Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Number of IdentityEntitlements.
            int count = context.countObjects(IdentityEntitlement.class, null);
            LOG.debug("    Total Number of IdentityEntitlements - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY_ENTITLEMENT,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_ENTITLEMENT_TOTAL, count);
            statResult.add(statRow);

            // Get Top 5 Identities with the Most Entitlements
            QueryOptions qo = new QueryOptions();
            qo.addGroupBy(IdentityEntitlement.IDENTITY_COLUMN);
            qo.setOrderBy(SELECT_COUNT);
            qo.setOrderAscending(false);
            qo.setResultLimit(RESULT_LIMIT);
            Iterator<Object []> it =
                    context.search(IdentityEntitlement.class, qo, SELECT_COUNT);
            for (int i = 0; i < RESULT_LIMIT && it.hasNext(); i++) {
                Object[] results = it.next();
                count = Util.otoi(results[0]);
                LOG.debug("    Identity with the " + topIdentityStr[i] + "entitlements - " + count);
                Message msg =
                    new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_ENTITLEMENT_TOP,
                            topIdentityStr[i]);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_IDENTITY_ENTITLEMENT,
                        msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }

            return statResult;
        }
    }

    /**
     * Application statistics datasource.
     * Following statistics are included -
     *     Total applications
     *     Number of applications by application type
     *     Number of applications by feature string
     *     Number of data sources defined
     */
    private class ApplicationEnvInfoDataSource implements EnvInfoDataSource {

        /** Application column type. */
        private static final String APP_TYPE = "type";
        /** Application column featureString. */
        private static final String APP_FEATURE = "featuresString";
        /** List of features for which stats are viewed. */
        private final Feature[] features = {Feature.COMPOSITE,
                Feature.DIRECT_PERMISSIONS,
                Feature.ENABLE,
                Feature.PROXY,
                Feature.UNLOCK,
                Feature.UNSTRUCTURED_TARGETS,
                Feature.PROVISIONING,
                Feature.PASSWORD,
                Feature.CURRENT_PASSWORD,
                Feature.ACCOUNT_ONLY_REQUEST,
                Feature.ADDITIONAL_ACCOUNT_REQUEST,
                Feature.NO_AGGREGATION,
                Feature.GROUPS_HAVE_MEMBERS};

        /**
         * Return the estimate of app stats.
         * Estimate is variable based on number of distinct application types.
         */
        @Override
        public int getSizeEstimate() throws GeneralException {
            // TODO Update this for other stats.
            // Get the size of distinct app types.
            Iterator<Object []> it = context.search(Application.class, null,
                    "count(distinct type)");
            List<Object []> listTypes = Util.iteratorToList(it);

            // Total apps + Apps by type + Apps by features + Activity datasources.
            return 1 + listTypes.size() + features.length + 1;
        }

        /**
         * Return Application stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Application Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Applications.
            int count = context.countObjects(Application.class, null);
            LOG.debug("    Total Applications - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_APPLICATION,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_APPLICATION_TOTAL, count);
            statResult.add(statRow);

            // Get Number of Applications by Application Type
            List<String> props = new ArrayList<String>();
            props.add(APP_TYPE);
            props.add(SELECT_COUNT);
            QueryOptions qo = new QueryOptions();
            qo.addGroupBy(APP_TYPE);
            Iterator<Object []> it = context.search(Application.class, qo, props);
            while (it.hasNext()) {
                Object[] results = it.next();
                String type = Util.otoa(results[0]);
                count = Util.otoi(results[1]);
                LOG.debug("    Number of applications of " + type + " - " + count);
                Message msg =
                    new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_APPLICATION_TYPE,
                        type);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_APPLICATION,
                        msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }

            // Get Number of Applications by Feature String
            for (Feature feature : features) {
                String featureString = feature.toString();
                qo = new QueryOptions(Filter.like(APP_FEATURE, featureString));
                count = context.countObjects(Application.class, qo);
                LOG.debug("    Number of Applications for " + featureString + " - " + count);
                Message msg =
                    new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_APPLICATION_FEATURE,
                        featureString);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_APPLICATION,
                        msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }

            // Get Number of data sources defined
            count = context.countObjects(ActivityDataSource.class, null);
            LOG.debug("    Number of Data Sources Defined - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_APPLICATION,
                MessageKeys.REPT_ENVIRONMENT_INFORMATION_APPLICATION_DATASOURCES, count);
            statResult.add(statRow);

            return statResult;
        }
    }

    /**
     * IdentityRequest statistics datasource.
     * Following statistics are included -
     *     Total Identity Requests
     *     Total Identity Request items
     *     Top 5 identity Request size by number of request items (do not need to include identity names)
     * Weekly average of new access requests for last 4 weeks
     * Number of password changes
     * Number of request for me
     * Number of request for others
     */
    private class IdentityRequestEnvInfoDataSource implements EnvInfoDataSource {

        /** Identity Request statistics contain fixed 7 rows. */
        private static final int ROWS_IDENTITY_REQ_STATS = 11;
        /** column identity_request_Id  */
        private static final String IDENTITY_REQUEST = "identityRequest";
        private static final String ATTR_PASSWORD_REQUEST = "PasswordsRequest";
        private static final String ATTR_TYPE = "type";
        private static final String ATTR_TARGET_ID = "targetId";
        private static final String ATTR_NAME = "name";
        private static final String ATTR_CREATED = "created";
        private static final String ATTR_ID = "id";

        /**
         * Return estimate of 3 for identity request statistics.
         */
        @Override
        public int getSizeEstimate() {
            return ROWS_IDENTITY_REQ_STATS;
        }

        /**
         * Identity Request Statistics 
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {
            
            LOG.info("Identity Request Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Identity Request.
            int count = context.countObjects(IdentityRequest.class, null);
            LOG.debug("    Total Number of Access Requests - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_TOTAL, count);
            statResult.add(statRow);
            
            //Total Identity Request items.
            count  = context.countObjects(IdentityRequestItem.class, null);
            LOG.debug("    Total Number of AccessRequest Items - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_ITEM, count);
            statResult.add(statRow);
            
            //Top 5 identity request size by number of request items.
            QueryOptions qo = new QueryOptions();
            qo.addGroupBy(IDENTITY_REQUEST);
            qo.setOrderBy(SELECT_COUNT);
            qo.setOrderAscending(false);
            qo.setResultLimit(RESULT_LIMIT);
            Iterator<Object []> it =
                    context.search(IdentityRequestItem.class, qo, SELECT_COUNT);
            for (int i = 0; i < RESULT_LIMIT && it.hasNext(); i++) {
                Object[] results = it.next();
                count = Util.otoi(results[0]);
                LOG.debug("    Identity with the " + topIdentityStr[i] + "number of request items - " + count);
                Message msg =
                    new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_TOP,
                            topIdentityStr[i]);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,
                        msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }
            // Get Total number of request in last 28 days
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.DATE, -28);
            Date lastWeekDate = cal.getTime();
            QueryOptions queryOptions = new QueryOptions(Filter.ge(ATTR_CREATED, lastWeekDate));
            int totalcntoflast4weeks = context.countObjects(IdentityRequest.class, queryOptions);
            int weeklyavgcnt = totalcntoflast4weeks/4;
            LOG.debug("    Weekly average of request count - " + weeklyavgcnt);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_WEEKLY_AVG, weeklyavgcnt);
            statResult.add(statRow);

            // Get Total number of password changes
            queryOptions = new QueryOptions(Filter.eq(ATTR_TYPE,ATTR_PASSWORD_REQUEST));
            int pwdchangeCount = context.countObjects(IdentityRequest.class, queryOptions);
            LOG.debug("    Total number of password changes - " + pwdchangeCount);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_TOTAL_PWD_CHANGE, pwdchangeCount);
            statResult.add(statRow);

            //fetch userid from identity table by using report launcher name
            String requesterid=null;
            queryOptions = new QueryOptions(Filter.eq(ATTR_NAME, reportlauncherName));
            Iterator<Object[]> itr = context.search(Identity.class, queryOptions,ATTR_ID);
            while (!Util.isEmpty(itr)) {
                Object[] results = itr.next();
                requesterid = Util.otos(results[0]);
            }
            
            //Get Total number of request for me
            queryOptions = new QueryOptions(Filter.eq(ATTR_TARGET_ID, requesterid));
            int requestCountforMe = context.countObjects(IdentityRequest.class, queryOptions);
            LOG.debug("    Total number of request for me- " + requestCountforMe);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_TOTAL_REQUEST_FOR_ME, requestCountforMe);
            statResult.add(statRow);

            // Get Total number of request for other
            queryOptions = new QueryOptions(Filter.ne(ATTR_TARGET_ID, requesterid));
            int requestCountforOther = context.countObjects(IdentityRequest.class, queryOptions);
            LOG.debug("    Total number of request for other - " + requestCountforOther);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCESS_REQUEST,MessageKeys.REPT_ENVIRONMENT_INFORMATION_IDENTITY_REQUEST_TOTAL_REQUEST_FOR_OTHER, requestCountforOther);
            statResult.add(statRow);
            return statResult;

        }
    }

    /**
     * Account(Links) statistics datasource.
     * Following statistics are included -
     *     Total number of Account Links
     *     Average number of account link per identity cube
     *     Top 5 Identities with the most Account links
     */
    private class AccountEnvInfoDataSource implements EnvInfoDataSource {

        /** Account(Links) stats contain fixed 7 rows. */
        private static final int ROWS_ACCOUNT_STATS = 7;

        private static final String FIELD_IDENTITY = "identity";

        /**
         * Return estimate of 7 for Account(Links) stats.
         */
        @Override
        public int getSizeEstimate() {
            return ROWS_ACCOUNT_STATS;
        }

        /**
         * Return Account(Links) stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Account Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Number of Account Links.
            int link_count = context.countObjects(Link.class, null);
            LOG.debug("    Total Number of Account - " + link_count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCOUNT,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_ACCOUNT_TOTAL, link_count);
            statResult.add(statRow);

            // Average number of account per identity cube
            int identity_count = context.countObjects(Identity.class, null);
            float avg_count = (float)link_count/identity_count;
            LOG.debug("    Average number of account link per identity cube - " + avg_count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCOUNT,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_ACCOUNT_AVERAGE, avg_count);
            statResult.add(statRow);
            
            // Get Top 5 Identities with the Most Account
            QueryOptions queryOption = new QueryOptions();
            queryOption.addGroupBy(FIELD_IDENTITY);
            queryOption.setOrderBy(SELECT_COUNT);
            queryOption.setOrderAscending(false);
            queryOption.setResultLimit(RESULT_LIMIT);
            Iterator<Object []> it = context.search(Link.class, queryOption, SELECT_COUNT);
            int i=0;
            while (it.hasNext() && i<RESULT_LIMIT) {
                Object[] results = it.next();
                link_count = Util.otoi(results[0]);
                LOG.debug("    Identity with the " + topIdentityStr[i] + "Account links - " + link_count);
                Message msg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_ACCOUNT_TOP,topIdentityStr[i]);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ACCOUNT,msg.getLocalizedMessage(),link_count);
                statResult.add(statRow);
                i++;
            }
            return statResult;
        }
    }

    /**
     * Certification statistics datasource.
     * Following statistics are included -
     *  Number of certifications
     *  Number of certifications item by type
     *  Number of certification items
     *  Number of active certifications item
     *  Number of completed certifications item
     */
    private class CertificationEnvInfoDataSource implements EnvInfoDataSource {

        private static final String FIELD_PHASE = "phase";

        /**
         * Return estimate of Certification stats.
         * @throws GeneralException 
         */
        @Override
        public int getSizeEstimate() throws GeneralException {
            return Certification.Type.values().length+4;
        }

        /**
         * Return Certification stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Certification Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Number of Certification.
            int count = context.countObjects(Certification.class, null);
            LOG.debug("    Total Number of Certification - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CENTIFICATION,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_CERTIFICATION_TOTAL, count);
            statResult.add(statRow);
            
            // Get Certification count by type
            for (Type t:Certification.Type.values()){
                  String featureString = t.toString();
                  QueryOptions qo = new QueryOptions(Filter.like("type", featureString));
                  count = context.countObjects(Certification.class, qo);
                  Message msg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_CERTIFICATION_BY_TYPE, t.getLocalizedMessage());
                  statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CENTIFICATION,msg.getLocalizedMessage(), count);
                  statResult.add(statRow);
            }
            
            // Get Total Number of Certification Item
            int item_count = context.countObjects(CertificationItem.class, null);
            LOG.debug("    Total Number of Certification Item - " + item_count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CENTIFICATION,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_CERTIFICATION_ITEM_TOTAL, item_count);
            statResult.add(statRow);
            
            // Total Number of Active Certification Item
            QueryOptions qo = new QueryOptions(Filter.eq(FIELD_PHASE,Phase.Active));
            int active_count = context.countObjects(Certification.class, qo);
            LOG.debug("    Total Number of Active Certification - " + active_count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CENTIFICATION,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_CERTIFICATION_ACTIVE, active_count);
            statResult.add(statRow);
            
            // Total Number of Complete Certification Item
            qo = new QueryOptions(Filter.eq("complete",true));
            int completed_count = context.countObjects(Certification.class, qo);
            LOG.debug("    Total Number of Complete Certification - " + completed_count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CENTIFICATION,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_CERTIFICATION_COMPLETE, completed_count);
            statResult.add(statRow);
            return statResult;
        }
    }

    /**
     * workgroup statistics datasource.
     * Following statistics are included -
     *     Total number of workgroup
     *     Top 5 workgroup by membership
     */
    private class WorkgroupEnvInfoDataSource implements EnvInfoDataSource {

        /** workgroup stats contain fixed 6 rows. */
        private static final int ROWS_WORKGROUP_STATS = 6;

        /** workgroups query to fetch workgroup by member. */
        private static final String SELECT_TOP_WORKGROUP_QUERY = "sql:select count(identity_id) from spt_identity_workgroups group by workgroup order by count(identity_id) desc";
        /**
         * Return estimate of 6 for workgroup stats.
         */
        @Override
        public int getSizeEstimate() {
            return ROWS_WORKGROUP_STATS;
        }

        /**
         * Return workgroup stats.
         * @throws Exception 
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("workgroups Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get total workgroup count.
            QueryOptions queryOption = new QueryOptions(Filter.eq(Identity.ATT_WORKGROUP_FLAG,true));
            int count = context.countObjects(Identity.class, queryOption);
            LOG.debug("    Total Number of workgroups - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_WORKGROUP,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_WORKGROUP_TOTAL, count);
            statResult.add(statRow);
            
           //Retrive top 5 workgroup with most members
            LOG.debug("    Query to fetch top 5 member count of workgroups -"+SELECT_TOP_WORKGROUP_QUERY);
            Iterator<Object[]> itr = context.search(SELECT_TOP_WORKGROUP_QUERY, null, null);
            int topcount=0;
            while (itr.hasNext() && topcount<5){
                Object results = itr.next();
                String memberCount = results.toString();
                LOG.debug("    Identity with the "+topIdentityStr[topcount]+" number of workgroups by membership -"+memberCount);
                Message msg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_WORKGROUP_TOP, topIdentityStr[topcount]);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_WORKGROUP,msg.getLocalizedMessage(),memberCount);
                statResult.add(statRow);
                topcount++;
            }
            return statResult;
        }
    }

    /**
     * WorkItems statistics datasource.
     * Following statistics are included -
     *     Total number of work items
     *     Top 5 identities with the most work items
     *     Top 5 identities with the most work items (excluding workgroups).This does not need to include identity names
     */
    private class WorkItemEnvInfoDataSource implements EnvInfoDataSource {

        /** WorkItem statistics contain fixed 11 rows. */
        private static final int ROWS_WORK_ITEMS_STATS = 11;
        private static final String OWNER = "owner";
        private static final String OWNER_WORKGROUP = "owner.workgroup";
        /**
         * Return estimate of 11 for WorkItem statistics.
         */
        @Override
        public int getSizeEstimate() {
            return ROWS_WORK_ITEMS_STATS;
        }

        /**
         * WorkItem Statistics 
         */
        @Override
        public List<Map<String, Object>> getStatistics()
                throws GeneralException {
            Message msg = null;
            LOG.info("WorkItem  Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total WorkItem .
            int count = context.countObjects(WorkItem.class, null);
            LOG.debug("    Total Number of WorkItems - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_WORKITEM,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_WORKITEM_TOTAL,
                    count);
            statResult.add(statRow);

            // Top 5 identities with the most work items.
            QueryOptions qo = new QueryOptions();
            qo.addGroupBy(OWNER);
            qo.setOrderBy(SELECT_COUNT);
            qo.setOrderAscending(false);
            qo.setResultLimit(RESULT_LIMIT);
            Iterator<Object[]> it = context.search(WorkItem.class, qo,
                    SELECT_COUNT);
            for (int i = 0; i < RESULT_LIMIT && it.hasNext(); i++) {
                Object[] results = it.next();
                count = Util.otoi(results[0]);
                LOG.debug("    Identity with the  " + topIdentityStr[i]
                        + "work items " + count);
                msg = new Message(
                        MessageKeys.REPT_ENVIRONMENT_INFORMATION_WORKITEM_TOP,
                        topIdentityStr[i]);
                statRow = getStatMap(
                        MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_WORKITEM,
                        msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }

            // Top 5 identities with the most work items (excluding workgroups).
            QueryOptions queryopt = new QueryOptions();
            queryopt.add(Filter.eq(OWNER_WORKGROUP, false));
            queryopt.addGroupBy(OWNER);
            queryopt.setOrderBy(SELECT_COUNT);
            queryopt.setOrderAscending(false);
            queryopt.setResultLimit(RESULT_LIMIT);
            it = context.search(WorkItem.class, queryopt, SELECT_COUNT);
            for (int i = 0; i < RESULT_LIMIT && it.hasNext(); i++) {
                Object[] results = it.next();
                count = Util.otoi(results[0]);
                LOG.debug("    Identity with the  " + topIdentityStr[i]
                              + "work items (excluding workgroups)" + count);
                msg = new Message(
                        MessageKeys.REPT_ENVIRONMENT_INFORMATION_WORKITEM_TOP_EXCLUDE_WORKGROUP,
                        topIdentityStr[i]);
                statRow = getStatMap(
                        MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_WORKITEM,
                        msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }
            return statResult;
        }
    }

    /**
     * TaskSchedule statistics datasource.
     * Following statistics are included -
     * Number of schedule tasks 
     * Number of completed tasks  
     * Number of Fails tasks 
     * Number of pending tasks
     */
    private class TaskScheduleEnvInfoDataSource implements EnvInfoDataSource {

        /** TaskSchedule stats contain fixed 4 rows. */
        private static final int ROWS_TASKSCHEDULE_STATS = 4;

        /** Select clause for getting the number of TaskSchedule and its status. */
        private static final String SELECT_COUNT_AND_COMPLETION_STATUS = "count(*),completed";

        /**
         * Return estimate of 4 for TaskSchedule stats.
         */
        @Override
        public int getSizeEstimate() {
            return ROWS_TASKSCHEDULE_STATS;
        }

        /**
         * Return TaskSchedule stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("TaskSchedule Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get total TaskSchedule count.
            int count = context.countObjects(TaskSchedule.class, null);
            LOG.debug("    Total Number of TaskSchedule - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE_COUNT, count);
            statResult.add(statRow);

            // fetch count by completionStatus from TaskSchedule for failed task
            QueryOptions qo = new QueryOptions(Filter.eq("completionStatus", TaskResult.CompletionStatus.Error));
            int failedTask = context.countObjects(TaskResult.class, qo);

            //fetch count,completed by completed time from TaskSchedule for pending and completed task
            qo = new QueryOptions();
            qo.addGroupBy("completed");
            Iterator<Object []> it = context.search(TaskResult.class, qo, SELECT_COUNT_AND_COMPLETION_STATUS);
            int completedTask = 0,pendingTask = 0;
            while (it.hasNext()) {
                Object[] results = it.next();
                count = Util.otoi(results[0]);
                Date status = (Date) results[1];
                if (status!=null)
                {
                    completedTask = completedTask+count;
                } else {
                    pendingTask = pendingTask+count;
                }
            }
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE_COMPLETE, completedTask);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE_FAILED, failedTask);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_TASK_SCHEDULE_PENDING, pendingTask);
            statResult.add(statRow);
            return statResult;
        }
    }

    /**
     * ROLE statistics datasource.
     * Following statistics are included -
     * Number of ROLE 
     * Number of role types 
     * Role Types By Name (Are the also request able)
     * Number of Role By Type
     * Number of Assignable roles
     */
    private class RoleEnvInfoDataSource implements EnvInfoDataSource {

        /** Select clause for getting the number of role and type. */
        private static final String SELECT_COUNT_AND_TYPE = "count(*),type";

        /**
         * Return estimate for Role stats.
         */
        @Override
        public int getSizeEstimate() throws GeneralException{
            ObjectConfig bundleConfig = ObjectConfig.getObjectConfig(Bundle.class);
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            Iterator<Object []> it = context.search(Bundle.class, qo,"type");
            List<Object []> listTypes = Util.iteratorToList(it);
            return 3+ listTypes.size()+bundleConfig.getRoleTypesMap().size();
        }

        /**
         * Return Role stats.
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Role Statistics - ");

            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get total Role count.
            int count = context.countObjects(Bundle.class, null);
            LOG.debug("    Total Number of Roles - " + count);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE_TOTAL, count);
            statResult.add(statRow);

            // Get total Role type count
            ObjectConfig Config = ObjectConfig.getObjectConfig(Bundle.class);
            int i = Config.getRoleTypesMap().size();
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE_TYPE_TOTAL, i);
            statResult.add(statRow);

            // Check assignable role type
            ArrayList<String> assignableTypes = new ArrayList<String>();
            ArrayList<String> roleTypeList = new ArrayList<String>();

            ObjectConfig bundleConfig = ObjectConfig.getObjectConfig(Bundle.class);
            Map bundleTypeMap = bundleConfig.getRoleTypesMap();
            //Map contain all roletype and  RoleTypeDefinition contain its assignable or not
            for (Object key  : bundleTypeMap.keySet()){
                RoleTypeDefinition def = (RoleTypeDefinition) bundleTypeMap.get(key);  
                String roleName = def.getDisplayName();
                if (Util.isNullOrEmpty(roleName)){
                    roleName = def.getName();
                }
                roleTypeList.add(roleName);
                Message msg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE_ISASSIGNABLE,roleName);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE,msg.getLocalizedMessage(),def.isAssignable());
                statResult.add(statRow);
                if (def.isAssignable())
                {
                  assignableTypes.add(roleName);
                }
            }
            //To find number of assignable roles
            int assignablecount=0;
            if (!Util.isEmpty(assignableTypes) && assignableTypes.size() > 0){
                QueryOptions qo = new QueryOptions(Filter.in("type", assignableTypes));
                assignablecount = context.countObjects(Bundle.class, qo);            
            }

            //TO find role types and its count
            QueryOptions qo = new QueryOptions();
            qo.addGroupBy("type");
            Iterator<Object []> itr = context.search(Bundle.class, qo,SELECT_COUNT_AND_TYPE);
            while (itr.hasNext()){
                Object[] results = itr.next();
                count = Util.otoi(results[0]);
                String Roletype = (String) results[1];
                Iterator<String> roleTypeListIterator = roleTypeList.iterator();
                while (Util.isNotNullOrEmpty(Roletype) && roleTypeListIterator.hasNext()){
                    String roleName = roleTypeListIterator.next();
                    if (roleName.toLowerCase().equals(Roletype.toLowerCase()))
                    {
                        Roletype=roleName;
                    }
                }
                Message msg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE_TYPE_COUNT,Roletype);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE,msg.getLocalizedMessage(), count);
                statResult.add(statRow);
            }
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE,MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ROLE_ASSIGNABLE_COUNT, assignablecount);
            statResult.add(statRow);
            return statResult;
        }
    }

    /**
     * Policies statistics datasource.
     * Following statistics are included -
     *     Total number of Policies
     *     Number of Policies by Type
     *     Total Number of Policy Violations
     *     Number of Policy Violations by Type
     *     Total Number of Active Policy
     *     Total Number of Inactive Policy
     */
    private class PolicyEnvInfoDataSource implements EnvInfoDataSource {

        private static final String POLICY_TEMPLATE = "template";
        private static final String POLICY_TYPE = "type";
        private static final String POLICY_STATE = "state";
        private static final String SQL_QUERY_POLICY_VIOLATION_TYPE = "sql: select count(violation.id) from spt_policy_violation violation inner join "
                + "spt_policy policy on violation.policy_id = policy.id and policy.type='";
        private List<Object[]> policytypeList =null;

        /**
         * Return estimate for Policies statistics.
         * 
         * @throws GeneralException
         */
        @Override
        public int getSizeEstimate() throws GeneralException {
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            Iterator<Object[]> it = context.search(Policy.class, qo,
                    POLICY_TYPE);
            policytypeList = Util.iteratorToList(it);
            int policyCntType = policytypeList.size();
            /* Total number of Policies + Total Number of Policy Violations +
             * Total Number of Active and Inactive Policy + (Policies by Type + Policy Violations by Type)
             */
            return (4 + (2 * policyCntType));
        }

        /**
         * Policies Statistics
         */
        @SuppressWarnings("unchecked")
        @Override
        public List<Map<String, Object>> getStatistics()
                throws GeneralException {
            Message msg = null;
            LOG.info("Policies  Statistics - ");
            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Policies .
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.ignoreCase(Filter.eq(POLICY_TEMPLATE,
                    false)));
            int count = context.countObjects(Policy.class, qo);
            LOG.debug("    Total number of Policies - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_POLICY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_POLICY_TOTAL,
                    count);
            statResult.add(statRow);

            // Number of Policies by Type.
            if (!Util.isEmpty(policytypeList)) {
                for (Object[] policyType : policytypeList) {
                    qo = new QueryOptions(Filter.like(POLICY_TYPE, policyType[0].toString()));
                    qo.add(Filter.ignoreCase(Filter.eq(POLICY_TEMPLATE, false)));
                    count = context.countObjects(Policy.class, qo);
                    LOG.debug("    Total Number of " + policyType + "Policies "
                            + count);
                    msg = new Message(
                            MessageKeys.REPT_ENVIRONMENT_INFORMATION_POLICY_TYPE_TOTAL,
                            policyType);
                    statRow = getStatMap(
                            MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_POLICY,
                            msg.getLocalizedMessage(), count);
                    statResult.add(statRow);
                }
            }

            // Get Total Policies Violation .
            count = context.countObjects(PolicyViolation.class, null);
            LOG.debug("    Total number of Policies Violation - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_POLICY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_POLICY_VIOLATION_TOTAL,
                    count);
            statResult.add(statRow);

            //fetch total policy violation by policy type
            if (!Util.isEmpty(policytypeList)) {
                for (Object[] policyTypeobj : policytypeList) {
                    String policyType = policyTypeobj[0].toString();
                    Iterator<Object[]> it = context.search(SQL_QUERY_POLICY_VIOLATION_TYPE + policyType + "'", null, null);
                    while (it.hasNext()) {
                       Object obj = it.next();
                       String policyVoilationCount = obj.toString();
                       LOG.debug("    Total Number of " + policyType
                           + "Policy Violation " + policyVoilationCount);
                       msg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_POLICY_VIOLATION_TYPE_TOTAL,policyType);
                       statRow = getStatMap(
                                 MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_POLICY,
                                 msg.getLocalizedMessage(), policyVoilationCount);
                       statResult.add(statRow);
                    }
                }
            }
            // Total number of active policies
            qo = new QueryOptions();
            qo.add(Filter.ignoreCase(Filter.eq(POLICY_STATE,
                    Policy.State.Active)));
            count = context.countObjects(Policy.class, qo);
            LOG.debug("    Total number of Active Policies - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_POLICY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_POLICY_ACTIVE,
                    count);
            statResult.add(statRow);

            // Total number of Inactive policies
            qo = new QueryOptions();
            Filter templateFilter = Filter.and(Filter.eq(POLICY_STATE, Policy.State.Inactive),
                    Filter.eq(POLICY_TEMPLATE, false));
            qo.add(templateFilter);
            count = context.countObjects(Policy.class, qo);
            LOG.debug("    Total number of InActive Policies - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_POLICY,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_POLICY_INACTIVE,
                    count);
            statResult.add(statRow);

            return statResult;
        }
    }

    /**
     *System Configuration statistics datasource.
     *   IdentityIQ Version   
     *   Database:  
     *      Type
     *      Version
     *   Driver Name
     *   Driver version
     *   Number of available process
     *   Number of available hosts
     *   JDBC driver - This will check version of 3 driver which are available. Upgraded/OOTB/bad
     *   LCM Enabled
     *   PAM enabled
    */
    private class SystemConfigurationDataSource implements EnvInfoDataSource {

        /**
         * Return estimate for System Configuration stats.
         */
        @Override
        public int getSizeEstimate() throws GeneralException{
            return 16;
        }

        /**
         * Return System Configuration stats.
         * @throws GeneralException 
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("System Configuration Statistics - ");

            // we have to mimic the about command from iiq console since we already have a context
            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;
            
            String databasetype = null;
            String databaseversion = null;
            String drivername = null;
            String driverversion = null;
            String iiqVersion = Version.getFullVersion();
            String accPackVersion= context.getConfiguration().getString(Configuration.ACCELERATOR_PACK_VERSION);
            int availableProcessCount = Runtime.getRuntime().availableProcessors();
            int serversCount = context.countObjects(Server.class, null);
            boolean lcmEnabled = Version.isLCMEnabled();
            boolean pamEnabled = Version.isPAMEnabled();
            boolean identityAIEnabled = Version.isIdentityAIEnabled();
            boolean famEnabled = Version.isFAMEnabled();
            boolean rapidSetupEnabled = Version.isRapidSetupEnabled();

            Connection con;
            DatabaseMetaData dm = null;
            con = context.getJdbcConnection();
            if (con != null) {
                try {
                    dm = con.getMetaData();
                    if (dm != null){
                        databasetype = dm.getDatabaseProductName();
                        databaseversion = dm.getDatabaseProductVersion();
                        drivername = dm.getDriverName();
                        driverversion = dm.getDriverVersion();
                    }
                } catch (SQLException e) {
                    throw new GeneralException(e.getMessage());
                }
            }

            if(accPackVersion != null) {
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_ACCPACK_VERSION, accPackVersion);
                statResult.add(statRow);
            }
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_IIQ_VERSION, iiqVersion);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DB_TYPE, databasetype);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DB_VERSION, databaseversion);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DRIVER_NAME, drivername);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DRIVER_VERSION, driverversion);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_PROCESSOR_COUNT, availableProcessCount);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_HOSTS_COUNT, serversCount);
            statResult.add(statRow);
            statResult.addAll(findDriversDetails());
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_LCM_ENABLED, lcmEnabled);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_PAM_ENABLED, pamEnabled);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_IAI_ENABLED, identityAIEnabled);
            statResult.add(statRow);
            if(identityAIEnabled) {
                String currentRecommender = context.getConfiguration().getString(Configuration.RECOMMENDER_SELECTED);
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_CURRENT_RECOMMENDER, currentRecommender);
                statResult.add(statRow);
            }
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_FAM_ENABLED, famEnabled);
            statResult.add(statRow);
            statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_RAPID_SETUP_ENABLED, rapidSetupEnabled);
            statResult.add(statRow);

            return statResult;
        }
    }

    /**
     * Integration Configuration statistics datasource.
     *   List count of integration by class
    */
    private class IntegrationConfigurationDataSource implements EnvInfoDataSource {

        private static final String INTEGRATION_CONFIG_EXECUTOR = "executor";

        /**
         * Return estimate for Integration Configuration stats by counting executor classes.
         */
        @Override
        public int getSizeEstimate() throws GeneralException{
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            Iterator<Object[]> it = context.search(IntegrationConfig.class, qo,
                    INTEGRATION_CONFIG_EXECUTOR);
            List<Object[]> listIntegrationNames = Util.iteratorToList(it);
            int integrationCntName = listIntegrationNames.size();
            return integrationCntName;
        }

        /**
         * Return Integration Configuration stats.
         * @throws GeneralException 
         */
        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {

            LOG.info("Integration Configuration Statistics - ");

            // we have to mimic the about command from iiq console since we already have a context
            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;
            
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            Iterator<Object[]> it = context.search(IntegrationConfig.class, qo, INTEGRATION_CONFIG_EXECUTOR);
            List<Object[]> listIntegrationNames = Util.iteratorToList(it);

            for (Object[] integration : listIntegrationNames) {
                String className = integration[0].toString();
                QueryOptions classQo = new QueryOptions();
                classQo.addFilter(Filter.eq(INTEGRATION_CONFIG_EXECUTOR, className));
                Iterator<Object[]> result = context.search(IntegrationConfig.class, classQo, INTEGRATION_CONFIG_EXECUTOR);
                List<Object[]> listResult = Util.iteratorToList(result);
                int integrationCnt = listResult.size();
                String classMsg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_INTEGRATION_CONFIG_CLASS,className).getLocalizedMessage();
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_INTEGRATION_CONFIG, classMsg, integrationCnt);
                statResult.add(statRow);
            }
            return statResult;
        }
    }

    /**
     * EntitlementCatalog statistics datasource.
     * Following statistics are included -
     * Number of Entitlements
     * Number of Entitlements by type
     * Number of Entitlements request able
     */
    private class EntitlementCatalogEnvInfoDataSource implements EnvInfoDataSource {

        private static final String ENTITLEMENT_CATALOG_TYPE = "type";
        private static final String REQUESTABLE_ENTITLEMENT = "requestable";


        /**
         * Return estimate of 5 for EntitlementCatalog statistics.
         */
        @Override
        public int getSizeEstimate() throws GeneralException {
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            Iterator<Object[]> it = context.search(ManagedAttribute.class, qo,
                    ENTITLEMENT_CATALOG_TYPE);
            List<Object[]> listEntitlementTypes = Util.iteratorToList(it);
            int entitlementCntType = listEntitlementTypes.size();
            return (2+ entitlementCntType);
        }

        /**
         * EntitlementCatalog Statistics
         */
        @SuppressWarnings("unchecked")
        @Override
        public List<Map<String, Object>> getStatistics()
                throws GeneralException {
            Message msg = null;
            LOG.info("Entitlement Catalog  Statistics - ");
            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow;

            // Get Total Entitlement Catalog (ManagedAttributes)
            int count = context.countObjects(ManagedAttribute.class, null);
            LOG.debug("    Total number of Entitlement Catalog Entries - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ENTITLEMENT_CATALOG,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_ENTITLEMENT_CATALOG_TOTAL,
                    count);
            statResult.add(statRow);
            
            // Get Total Entitlement by type (ManagedAttributes)
            List<String> propList = new ArrayList<String>();
            propList.add(SELECT_COUNT);
            propList.add(ENTITLEMENT_CATALOG_TYPE);
            QueryOptions qo = new QueryOptions();
            qo.addGroupBy(ENTITLEMENT_CATALOG_TYPE);
            Iterator<Object[]> it = context.search(ManagedAttribute.class, qo,
                    propList);
            while (it.hasNext()) {
                Object[] results = it.next();
                count = Util.otoi(results[0]);
                if (results[1] != null) {
                    String entitlementType = results[1].toString();

                    LOG.debug("    Total Number of Entitlements by type" + entitlementType
                            + count);
                    msg = new Message(
                            MessageKeys.REPT_ENVIRONMENT_INFORMATION_ENTITLEMENT_CATALOG_TYPE_TOTAL,
                            entitlementType);
                    statRow = getStatMap(
                            MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ENTITLEMENT_CATALOG,
                            msg.getLocalizedMessage(), count);
                    statResult.add(statRow);
                }
            }

            // Number of Entitlements request able
            qo = new QueryOptions(Filter.eq(REQUESTABLE_ENTITLEMENT, true));
            count = context.countObjects(ManagedAttribute.class, qo);
            LOG.debug("    Total number of Requestable Entitlement - " + count);
            statRow = getStatMap(
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_ENTITLEMENT_CATALOG,
                    MessageKeys.REPT_ENVIRONMENT_INFORMATION_ENTITLEMENT_CATALOG_REQUESTABLE,
                    count);
            statResult.add(statRow);

            return statResult;
        }
    }

    /**
     * DataSource to list some stats about Classifications, mainly total count and count by origin
     */
    private class ClassificationEnvDataSource implements EnvInfoDataSource {

        @Override
        public int getSizeEstimate() throws GeneralException {
            QueryOptions qo = new QueryOptions();
            qo.setDistinct(true);
            Iterator<Object []> it = context.search(Classification.class, qo, "origin");
            List<Object []> listTypes = Util.iteratorToList(it);
            return 1 + listTypes.size();
        }

        @Override
        public List<Map<String, Object>> getStatistics() throws GeneralException {
            QueryOptions qo = new QueryOptions();
            int total = context.countObjects(Classification.class, qo);
            List<Map<String, Object>> statResult = new ArrayList<>();
            statResult.add(getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CLASSIFICATION, MessageKeys.REPT_ENVIRONMENT_INFORMATION_CLASSIFICATION_TOTAL, total));
            qo.addGroupBy("origin");
            Iterator<Object[]> iterator = context.search(Classification.class, qo, Arrays.asList("origin", "count(*)"));
            while (iterator.hasNext()) {
                Object[] result = iterator.next();
                String origin = (String)result[0];
                total = Util.otoi(result[1]);
                Message resultMessage = Util.isNullOrEmpty(origin) ?
                        new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_CLASSIFICATION_ORIGIN_MISSING) :
                        new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_CLASSIFICATION_ORIGIN_COUNT, origin);
                statResult.add(getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_CLASSIFICATION, resultMessage.getLocalizedMessage(), total));
            }

            return statResult;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Helper Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return map for provided statistics.
     */
    private Map<String, Object> getStatMap(String type, String property, Object value) {

        Map<String, Object> statMap = new HashMap<String, Object>();
        statMap.put(FIELD_OBJECT_TYPE, type);
        statMap.put(FIELD_PROPERTY, property);
        statMap.put(FIELD_VALUE, value);
        return statMap;
    }

    private List<Map<String, Object>> findDriversDetails() throws GeneralException{

      List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
      Map<String, Object> statRow;
      HashMap<String,String> ootbVersions = new HashMap<String,String>();

      ootbVersions.put(SQLSERVER_DRIVER_NAME, "584207-a99205071acbe2709103b971662992e0");
      ootbVersions.put(MYSQL_DRIVER_NAME, "875339-b47f18b2f96f3ea138f70f123e3bb8de");
      ootbVersions.put(ORACLE_DRIVER_NAME, "3389454-b99a9e9b93aa31b787d174a1dbec7cc4");

      //driver class list which iiq support
      ArrayList<String> driverClasses = new ArrayList<String>();
      driverClasses.add(MYSQL_DRIVER_NAME);
      driverClasses.add(ORACLE_DRIVER_NAME);
      driverClasses.add(SQLSERVER_DRIVER_NAME);

    //checking each driver class
      for (String driverClass : driverClasses) {
         LOG.info("Examining JDBC driver: " + driverClass);
         boolean foundOOTB = false;
         boolean foundKnownBad = false;
         try {
             String jarPath = getJarFilePath(driverClass);
             String keyString = null;
             if (Util.isNotNullOrEmpty(jarPath)){
                 keyString=getKeyString(jarPath);
              } else {
                 continue;
              }
             String ootbVersion = null;
             if (!Util.isEmpty(ootbVersions))
                  ootbVersion = ootbVersions.get(driverClass);
             if ((Util.isNotNullOrEmpty(ootbVersion)) && Util.isNotNullOrEmpty(keyString) && (keyString.equals(ootbVersion))) {
                LOG.debug("OOTB found for " + driverClass);
                String drivermsg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DRIVER_DETAILS_OOTB_VERSION,driverClass).getLocalizedMessage();
                statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,drivermsg, keyString);
                statResult.add(statRow);
                foundOOTB = true;
             }
             //Check for comparison with known bad versions of the JDBC driver.
             List knownBads = null;
             if (!Util.isEmpty(knownBadVersions))
                 knownBads = knownBadVersions.get(driverClass);
             if (!Util.isEmpty(knownBads) && knownBads.contains(keyString)) {
                   // We have known bad version of the JDBC driver that needs to be replaced.
                   LOG.debug("Bad Version found for " + driverClass);
                   String drivermsg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DRIVER_DETAILS_BAD_VERSION,driverClass).getLocalizedMessage();
                   statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,drivermsg, keyString);
                   statResult.add(statRow);
                   foundKnownBad = true;    
             }
             // In this case we check OOTB and bad version.
             if ((!foundOOTB) && (!foundKnownBad)) {
                 String drivermsg = new Message(MessageKeys.REPT_ENVIRONMENT_INFORMATION_SYSCONFIG_DRIVER_DETAILS_UPGRADED_VERSION,driverClass).getLocalizedMessage();
                 statRow = getStatMap(MessageKeys.REPT_ENVIRONMENT_INFORMATION_OBJECT_SYSCONFIG,drivermsg, keyString);
                 statResult.add(statRow);
             }
         } catch (ClassNotFoundException e) {
             LOG.info("No JDBC driver found for: " + driverClass);
         }
      }
      return statResult;
   }

    /*
     * Function for getting jar file path from jdbc class
     */
    private String getJarFilePath(String driverClass) throws ClassNotFoundException{
    	
        Class clazz = Class.forName(driverClass);       
        String className = null;
        String classPath = null;
        if (clazz != null){
            className = clazz.getSimpleName() + ".class";
            if (clazz.getResource(className) != null)
               classPath = clazz.getResource(className).toString();
        } else {
            LOG.warn("Class not found: " + driverClass);
            return null;
        }
        if (Util.isNullOrEmpty(classPath)){
            LOG.warn("classPath is null or empty");
            return null;
        }
        if (!classPath.startsWith("jar")) {
            LOG.warn("Class not from a jar: " + className);
            return null;
        }
        // classPath looks like: jar:file:/Users/adam.hampton/apache-tomcat-6.0.36/webapps/iiq61lab/WEB-INF/lib/sqljdbc-1.2.jar!/com/microsoft/sqlserver/jdbc/SQLServerDriver.class
        String pathLhs = null;
        if (Util.isNotNullOrEmpty(classPath))
            pathLhs = classPath.split("!")[0];

        String pathRhs =null;
        if (Util.isNotNullOrEmpty(pathLhs))
            pathRhs = pathLhs.split(":file:")[1];

        // We are processing the jarPath as a file path and not a URI, so accommodate paths that have spaces.
        String jarPath = pathRhs.replaceAll("%20"," ");
        LOG.info("jarPath: " + jarPath);
        if (Util.isNullOrEmpty(jarPath)){
          LOG.warn("Jar file path is empty or null");
          return null;
        }
        return jarPath;
    }

    /*
     * Function for getting key string md5 and file size from file
     */
    private String getKeyString(String jarPath) throws GeneralException{
         File jarFile = new File(jarPath);
         long fileSize = jarFile.length();
         byte[] bFile = new byte[(int) jarFile.length()];
         FileInputStream fis;
         String md5 = null;
         try {
            fis = new FileInputStream(jarFile);
            fis.read(bFile);
            byte[] byteList = MessageDigest.getInstance("MD5").digest(bFile);
            md5 = ConvertTools.convertByteListToHexString(byteList).toLowerCase();
         } catch (Exception e) {
             LOG.info("failed to read byte from file :" + jarFile);
             throw new GeneralException(e.getMessage());
         }
         // Check for comparison with OOTB versions of the JDBC driver.
         String keyString = "" + fileSize + "-" + md5;
         return keyString;
    }
}
