/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of IntegrationExecutor that writes requests to 
 * database tables.
 * 
 * Author: Jeff
 *
 * This is similar in spirit to the flat-file executor but may be
 * preferable for some integrations that are accustomed to reading
 * feeds from databases.
 *
 * There are two models we need to provide, one for provisioning plans
 * and another for role definitions.
 *
 * PROVISIONING PLAN
 *
 * The ProvisioningPlan is a hierarchical model that looks roughly like this:
 *
 *    ProvisioningPlan
 *      Attributes - extra arguments to the executor
 *      List<AccountRequest>
 *        - op (create, update, delete)
 *        - application
 *        - instance
 *        - nativeIdentity
 *        List<AttributeRequest>
 *          - op (set, add, remove)
 *          - name
 *          - value (String, Integer, List<String>)
 *        List<PermissionRequest>
 *          - op (set, add, remove)
 *          - target
 *          - rights (String, List<String>)
 * 
 * We could try to model this with three tables: plan, account, attribute
 * (merging AttributeRequest and PermissionRequest).  But it would
 * be easier for the consumer to just deal with single table with columns:
 *
 *   plan - unique id for each plan
 *   identity - "meta" identity for provisioning systems, may be null
 *   op - create, delete, set, add, remove (merge account & attribute ops)
 *   resource - application name
 *   instance - instance name
 *   account - account id on the application
 *   type - attribute,permission (null means attribute)
 *   name - attribute name or permission target
 *   value - string for attribute, csv for target
 *
 * Changes to multi-valued attributes would be broken out into
 * a row for each value.  Changes to permission rights are a single
 * row with rights as a CSV.
 *
 * Typically the application would use "group by plan" to load all the
 * plan rows into memory and order them for efficiency.  This is important
 * for op=create which must be done before any op=set.  Or maybe we
 * could assume that op=create implies set.
 *
 * ROLE DEFINITION
 *
 * Roles lend themselves to a hierarchical table model.
 *
 * spt_role_definition
 *    name varchar
 *    
 * spt_role_attribute
 *    role - FK reference to spt_role_definition(name)
 *    name - varchar
 *    value - varchar
 *
 * spt_role_resource
 *    role - FK reference to spt_role_definition(name)
 *    name - resource name (role+name) composite key
 *   
 * spt_role_entitlement
 *    role - FK reference to spt_role_definition(name)
 *    resource - refernce to spt_role_resource(name)
 *    name - varchar
 *    value - varchar
 *
 * The spt_role_resource table doesn't serve much purpose so we
 * could collapse that and just add a "resource" column to 
 * spt_role_entitlement.  For that matter, the entire definition
 * with the exception of attributes could be collapsed to:
 *
 * spt_role_definition
 *    role varchar
 *    resource varchar
 *    name varchar
 *    value varchar
 *
 */

package sailpoint.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ProvisioningPlan.AccountRequest;
import sailpoint.integration.ProvisioningPlan.AttributeRequest;
import sailpoint.integration.ProvisioningPlan.PermissionRequest;
import sailpoint.object.Attributes;
import sailpoint.object.IntegrationConfig;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;
import sailpoint.tools.VelocityUtil;

public class JdbcExecutor extends AbstractIntegrationExecutor {

    private static Log log = LogFactory.getLog(JdbcExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    //
    // Configuration arguments
    // We use JdbcUtil.getConnection so all of the JdbcUtil args
    // are supported here.  Normally you would use "url" with
    // the full connection URL.
    //

    public static final String ARG_TABLE_PREFIX = "tablePrefix";
    
    public static final String ARG_EXTRA_COLUMNS = "extraColumns";

    //
    // JDBC Schema
    //

    public static final String TABLE_ROLE = BrandingServiceFactory.getService().brandTableName( "integration_role" );
    public static final String COL_ROLE = "role";
    // sigh, "resource" is a reserved word on Oracle
    public static final String COL_RESOURCE = "application";
    public static final String COL_INSTANCE = "instance";
    public static final String COL_TYPE = "type";
    public static final String COL_NAME = "name";
    public static final String COL_VALUE = "value";

    // resuse COL_ROLE, COL_NAME, COL_VALUE
    public static final String TABLE_ROLE_ATTRIBUTE = BrandingServiceFactory.getService().brandTableName( "integration_roleattr" );

    // resuse COL_RESOURCE, COL_INSTANCE, COL_TYPE, COL_NAME, COL_VALUE
    public static final String TABLE_PLAN = BrandingServiceFactory.getService().brandTableName( "integration_request" );
    // plan and identity are reserved words in SQL Server and/or DB2
    public static final String COL_PLAN_ID = "plan_id";
    public static final String COL_IDENTITY_NAME = "identity_name";
    public static final String COL_OP = "op";
    public static final String COL_ACCOUNT = "account";
    

    public static final String TYPE_ATTRIBUTE = "A";
    public static final String TYPE_PERMISSION = "P";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Optional prefix to put on table names, for example, "identityiq."
     */
    String _tablePrefix;
    
    Map<String, String> _extraColumns;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public JdbcExecutor() {
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationExecutor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Dig our JDBC connection params out of the config map.
     */
    @Override
    public void configure(SailPointContext context,
                          IntegrationConfig config)
        throws Exception {

        // save the args for later calls to provision()
        super.configure(context, config);

        Attributes<String, Object> args = _config.getAttributes();
        configureAttributes(args);
    }
    
    void configureAttributes(Attributes<String, Object> args) {
        if (args != null) {
            _tablePrefix = args.getString(ARG_TABLE_PREFIX);
            Object value = args.get(ARG_EXTRA_COLUMNS);
            if ( value != null ) {
                if ( value instanceof Map ) {
                    _extraColumns = (Map)value;
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInterface
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Verify that we can connect to the DB.
     */
    @Override
    public String ping() throws Exception {

        String result = null;
        Connection con = connect();
        try {
            result = "Connection sucessful";
        }
        finally {
            if (con != null) disconnect(con);
        }
        return result;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Roles
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public List listRoles() throws Exception {

        List roleNames = null;
        Connection con = connect();
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("select distinct ");
            sql.append(COL_ROLE);
            sql.append(" from ");
            addTableName(sql, TABLE_ROLE);
            
            roleNames = JdbcUtil.queryList(con, sql.toString(), null);
        }
        finally {
            if (con != null) disconnect(con);
        }
        return roleNames;
    }

    @Override
    public RequestResult addRole(RoleDefinition def) throws Exception {

        RequestResult result = null;
        if (def != null) {
            Connection con = connect();
            try {
                // delete the old definition
                deleteRole(con, def.getName());

                // add the new definition
                List resources = def.getResources();
                if (resources != null) {
                    for (int i = 0 ; i < resources.size() ; i++) {
                        RoleResource resource = (RoleResource)resources.get(i);
                        List ents = resource.getEntitlements();
                        if (ents != null) {
                            for (int j = 0 ; j < ents.size() ; j++) {
                                RoleEntitlement ent = (RoleEntitlement)ents.get(j);
                                addRole(con, def, resource, ent);
                            }
                        }
                    }
                }
            }
            finally {
                if (con != null) disconnect(con);
            }
        }
        return result;
    }

    /**
     * Add a row of a role definition. It would be safer if we held
     * a transaction open over these, but we are assuming that there is
     * only one thread allowed to be pushing role definitions.
     */
    private void addRole(Connection con, RoleDefinition role, 
                         RoleResource resource, RoleEntitlement ent)
        throws Exception {

        // if we ever get around to supporting Permissions, we
        // can keep all of the values in one CSV
        List values = ent.getValues();
        if (values != null && values.size() > 0) {

            String sqlstr = buildAddRoleSQL();
            PreparedStatement ps = null;
            try {
                ps = buildAddRolePS(con, sqlstr, role, resource, ent);
    
                for (int i = 0 ; i < values.size() ; i++) {
                    String s = (String)values.get(i);
                    if (s != null) {
                        ps.setString(6, s);
                        ps.executeUpdate();
                    }
                }
            } finally {
                /* connection gets closed by caller */
                if (ps != null) ps.close();
            }
        }
    }
    
    /* default package protected for unit tests */
    String buildAddRoleSQL() {
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ");
        addTableName(sql, TABLE_ROLE);
        sql.append("(");
        sql.append(COL_ROLE);
        sql.append(",");
        sql.append(COL_RESOURCE);
        sql.append(",");
        sql.append(COL_INSTANCE);
        sql.append(",");
        sql.append(COL_TYPE);
        sql.append(",");
        sql.append(COL_NAME);
        sql.append(",");
        sql.append(COL_VALUE);
        sql.append(") values(?,?,?,?,?,?)");
        
        return sql.toString();
    }
    
    PreparedStatement buildAddRolePS(Connection con, String sql, RoleDefinition role, RoleResource resource, RoleEntitlement ent)
            throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, role.getName());
        ps.setString(2, resource.getName());
        ps.setString(3, resource.getInstance());
        ps.setString(4, ent.isPermission() ? TYPE_PERMISSION : TYPE_ATTRIBUTE);
        ps.setString(5, ent.getName());
        
        return ps;
    }

    @Override
    public RequestResult deleteRole(String name) throws Exception {

        RequestResult result = null;
        Connection con = connect();
        try {
            deleteRole(con, name);
        }
        finally {
            if (con != null) disconnect(con);
        }
        return result;
    }

    private void deleteRole(Connection con, String name) throws Exception {

        StringBuilder sql = new StringBuilder();
        sql.append("delete from ");
        addTableName(sql, TABLE_ROLE);
        sql.append(" where ");
        sql.append(COL_NAME);
        sql.append(" = ?");

        JdbcUtil.update(con, sql.toString(), name);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provision
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public RequestResult provision(String identity, ProvisioningPlan plan)
        throws Exception {

        RequestResult result = null;
        if (plan != null) {
            Connection con = connect();
            try {
                // generate a uid for the plan
                String planid = Util.uuid();

                List accounts = plan.getAccountRequests();
                if (accounts != null) {
                    for (int i = 0 ; i < accounts.size() ; i++) {
                        AccountRequest account = (AccountRequest)accounts.get(i);
                        if (ProvisioningPlan.OP_ACCOUNT_DELETE.equals(account.getOperation())) {
                            // special case 
                            deleteRequest(con, identity, planid, plan, account);
                        }
                        else if (ProvisioningPlan.OP_ACCOUNT_DISABLE.equals(account.getOperation())) {
                            // special case 
                            throw new Exception("Disable request not supported");
                        }
                        else if (ProvisioningPlan.OP_ACCOUNT_ENABLE.equals(account.getOperation())) {
                            // special case 
                            throw new Exception("Enable request not supported");
                        }
                        else if (ProvisioningPlan.OP_ACCOUNT_LOCK.equals(account.getOperation())) {
                            // special case 
                            throw new Exception("Lock request not supported");
                        }
                        else {
                            List attributes = account.getAttributeRequests();
                            if (attributes != null) {
                                for (int j = 0 ; j < attributes.size() ; j++) {
                                    AttributeRequest att = (AttributeRequest)attributes.get(j);
                                    provision(con, identity, planid, plan, account, 
                                              att.getOperation(),
                                              att.getName(), att.getValue(), false);
                                }
                            }

                            List permissions = account.getPermissionRequests();
                            if (permissions != null) {
                                for (int j = 0 ; j < permissions.size() ; j++) {
                                    PermissionRequest perm = (PermissionRequest)permissions.get(j);
                                    provision(con, identity, planid, plan, account, 
                                              perm.getOperation(),
                                              perm.getTarget(), perm.getRights(), true);
                                }
                            }
                        }
                    }
                }
            }
            finally {
                if (con != null) disconnect(con);
            }
        }
        return result;
    }

    private void provision(Connection con, String identity, String planid,
                           ProvisioningPlan plan, AccountRequest account,
                           String op, String name, Object value, boolean permission)
        throws Exception {

        if (value != null) {
            
            // we're merging the account and attribute ops since we can
            // OP_CREATE implies OP_SET for attributes
            if (ProvisioningPlan.OP_ACCOUNT_CREATE.equals(account.getOperation()))
                op = account.getOperation();

            String type = (permission) ? TYPE_PERMISSION : TYPE_ATTRIBUTE;

            String sqlstr = buildProvisionSQL(); 
            PreparedStatement ps = null;
            
            try {
                ps = buildProvisionPS(con, sqlstr, identity, planid, plan, account, type, op, name, value);
                // permission values are CSVs of right names, 
                // multi-valued attributes might have commas so we have
                // to have a row per value
                if (permission || !(value instanceof Collection)) {
                    // value should be a CSV
                    ps.setString(9, value.toString());
                    ps.executeUpdate();
                }
                else {
                    Collection values = (Collection)value;
                    for (Object o : values) {
                        if (o != null) {
                            ps.setString(9, o.toString());
                            ps.executeUpdate();
                        }
                    }
                }
            } finally {
                if (ps != null) ps.close();
            }
        }
    }
    
    String buildProvisionSQL() {
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ");
        addTableName(sql, TABLE_PLAN);
        sql.append("(");
        sql.append(COL_PLAN_ID);
        sql.append(",");
        sql.append(COL_IDENTITY_NAME);
        sql.append(",");
        sql.append(COL_OP);
        sql.append(",");
        sql.append(COL_RESOURCE);
        sql.append(",");
        sql.append(COL_INSTANCE);
        sql.append(",");
        sql.append(COL_ACCOUNT);
        sql.append(",");
        sql.append(COL_TYPE);
        sql.append(",");
        sql.append(COL_NAME);
        sql.append(",");
        sql.append(COL_VALUE);
        
        StringBuilder extraColsValues = new StringBuilder();
        if ( _extraColumns != null ) {
            Iterator<String> iter = _extraColumns.keySet().iterator();
            while ( iter.hasNext() ) {
                sql.append(",");
                sql.append(iter.next());
                extraColsValues.append(",?");
            }
        }
        sql.append(") values(?,?,?,?,?,?,?,?,?");
        sql.append(extraColsValues);
        sql.append(")");
        
        return sql.toString();
    }
    
    PreparedStatement buildProvisionPS(Connection con, String sql, String identity, String planid,
                                       ProvisioningPlan plan, AccountRequest account, String type,
                                       String op, String name, Object value) throws Exception {
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, planid);
        ps.setString(2, identity);
        ps.setString(3, op);
        ps.setString(4, account.getApplication());
        ps.setString(5, account.getInstance());
        ps.setString(6, account.getNativeIdentity());
        ps.setString(7, type);
        ps.setString(8, name);
        // param 9 will be set by caller
        
        if ( _extraColumns != null ) {
                // build a set of arguments for Velocity rendering
            Map<String, Object> velocityArgs = new HashMap<String, Object>();
            velocityArgs.put("plan", plan);
            velocityArgs.put("identityName", identity);
            velocityArgs.put("request", account);
            velocityArgs.put("op", op);
            velocityArgs.put("type", type);
            velocityArgs.put("name", name);
            velocityArgs.put("value", value);

            Iterator<String> iter = _extraColumns.keySet().iterator();
            int i = 10;
            while ( iter.hasNext() ) {
                String extraVal = _extraColumns.get(iter.next());
                if ( extraVal != null ) {
                    extraVal = VelocityUtil.render(extraVal, velocityArgs,
                                                   Locale.getDefault(),
                                                   TimeZone.getDefault());
                }
                ps.setString(i++, extraVal);
            }
        }
        
        return ps;
    }

    private void deleteRequest(Connection con, String identity, String planid,
            ProvisioningPlan plan, AccountRequest account)
        throws Exception {

        String sql = buildDeleteRequestSQL();
        PreparedStatement ps = null;
        try {
            ps = buildDeleteRequestPS(con, sql, identity, planid, plan, account);
            ps.executeUpdate();
        } finally {
            if (ps != null) ps.close();
        }
    }
    
    String buildDeleteRequestSQL() {
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ");
        addTableName(sql, TABLE_PLAN);
        sql.append("(");
        sql.append(COL_PLAN_ID);
        sql.append(",");
        sql.append(COL_IDENTITY_NAME);
        sql.append(",");
        sql.append(COL_OP);
        sql.append(",");
        sql.append(COL_RESOURCE);
        sql.append(",");
        sql.append(COL_INSTANCE);
        sql.append(",");
        sql.append(COL_ACCOUNT);
        
        StringBuilder extraColsValues = new StringBuilder();
        if ( _extraColumns != null ) {
            Iterator<String> iter = _extraColumns.keySet().iterator();
            while ( iter.hasNext() ) {
                sql.append(",");
                sql.append(iter.next());
                extraColsValues.append(",?");
            }
        }
        
        sql.append(") values(?,?,?,?,?,?");
        sql.append(extraColsValues);
        sql.append(")");
        
        return sql.toString();
    }
    
    PreparedStatement buildDeleteRequestPS(Connection con, String sql, String identity, String planid, ProvisioningPlan plan, AccountRequest account)
            throws Exception {
        
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, planid);
        ps.setString(2, identity);
        ps.setString(3, ProvisioningPlan.OP_ACCOUNT_DELETE);
        ps.setString(4, account.getApplication());
        ps.setString(5, account.getInstance());
        ps.setString(6, account.getNativeIdentity());
        
        if ( _extraColumns != null ) {
                // build a set of arguments for Velocity rendering
            Map<String, Object> velocityArgs = new HashMap<String, Object>();
            velocityArgs.put("plan", plan);
            velocityArgs.put("identityName", identity);
            velocityArgs.put("request", account);
            velocityArgs.put("op", ProvisioningPlan.OP_ACCOUNT_DELETE);
    
    
            Iterator<String> iter = _extraColumns.keySet().iterator();
            int i = 7;
            while ( iter.hasNext() ) {
                String extraVal = _extraColumns.get(iter.next());
                if ( extraVal != null ) {
                    extraVal = VelocityUtil.render(extraVal, velocityArgs,
                                                   Locale.getDefault(),
                                                   TimeZone.getDefault());
                }
                ps.setString(i++, extraVal);
            }
        }
        
        return ps;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Database
    //
    //////////////////////////////////////////////////////////////////////

    private Connection connect() throws Exception {
        
        return JdbcUtil.getConnection(_config.getAttributes());
    }

    private void disconnect(Connection con) {
        if (con != null) {
            try {
                con.close();
            }
            catch (SQLException e) {
                // normally doesn't happen
                log.error(e.getMessage(), e);
            }
        }
    }
    
    private void addTableName(StringBuilder sql, String name) {
        if (_tablePrefix != null)
            sql.append(_tablePrefix);
        sql.append(name);
    }

    private void addString(StringBuilder sql, String value) {
        if (value == null)
            sql.append("null");
        else {
            sql.append("'");
            
            // replace single quote with double
            sql.append(value.replace("'", "''"));

            sql.append("'");
        }
    }
}
