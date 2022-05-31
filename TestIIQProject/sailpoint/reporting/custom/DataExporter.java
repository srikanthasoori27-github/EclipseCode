/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import sailpoint.persistence.DB2Dialect;
import sailpoint.persistence.SQLServerUnicodeDialect;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.object.*;
import sailpoint.object.Certification.CertificationStatistics;
import sailpoint.api.SailPointContext;
import sailpoint.web.messages.MessageKeys;
import org.hibernate.dialect.MySQL5InnoDBDialect;
import org.hibernate.dialect.Oracle10gDialect;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.sql.Types;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * Exports denormalized view of certain IIQ objects to an external datasource.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class DataExporter {

    private static Log log = LogFactory.getLog(DataExporter.class);


    // Max number of errors we will accumulate on this object. It's possible
    // this task could generate 100000s of errors. Which might be bad...
    private static int MAX_ERR_CNT = 20;


    private DataExportSchemaUtil schemaUtil;
    private SailPointContext context;
    private DatabaseType dbType;
    private boolean fullExport;
    private Map<String, String> applicationIds;
    
    /**
     * Messages accumulated during execution. These will be passed
     * back to the task result
     */
    private List<Message> messages;

    /**
     * Stores details regarding different database types
     */
    public enum DatabaseType {

        MySQL(MySQL5InnoDBDialect.class, "mysql", Types.BIGINT),
        Oracle(Oracle10gDialect.class, "oracle", Types.NUMERIC),
        SqlServer(SQLServerUnicodeDialect.class, "sqlserver", Types.NUMERIC),
        DB2(DB2Dialect.class, "db2", Types.BIGINT);

        private String templateFileExtension;
        private Class<?> hibernateDialect;
        private int sqlDateType;

        DatabaseType(Class<?> hibernateDialect, String templateFileExtension, int sqlDateType) {
            this.hibernateDialect = hibernateDialect;
            this.templateFileExtension = templateFileExtension;
            this.sqlDateType = sqlDateType;
        }

        public Class<?> getHibernateDialect() {
            return hibernateDialect;
        }

        public String getTemplateFileExtension() {
            return templateFileExtension;
        }

        public int getDateSqlType(){
            return sqlDateType;
        }

    }

    public DataExporter(SailPointContext context, DatabaseType type) throws GeneralException {
        dbType = type;
        schemaUtil = new DataExportSchemaUtil(type);
        this.context = context;
    }

    /**
     * Gets the max export_date for a given object type.
     * @param conn
     * @param clazz
     * @return
     * @throws SQLException
     */
    public Date calculateLastExport(Connection conn, Class<?> clazz, String taskDefId) throws SQLException {
        String sql = "select end_dt from "+ BrandingServiceFactory.getService().brandExportTableName(DataExportSchemaUtil.EXP_TBL) +
                " where task_def_id='"+taskDefId+"' and class_name=?";
        String dt = JdbcUtil.queryString(conn, sql, clazz.getName());
        if (dt != null)
            return new Date(Util.atol(dt));
        return null;
    }

    /**
     * Gets list of attributes which do not have columns in the export database tables.
     *
     * @param conn
     * @param clazz
     * @return
     * @throws GeneralException
     */
    public List<String> getAttributesMissingFromExport(Connection conn, Class<?> clazz) throws GeneralException{
        return schemaUtil.getMissingExtendedColumns(conn, clazz);
    }

    /**
     * Exports the given object to the external datasource.
     * @param conn
     * @param clazz Class of the object to export
     * @param id ID of the object to export
     * @throws GeneralException
     */
    public boolean export(Connection conn, Class<? extends SailPointObject> clazz, String id, 
            boolean fullExport, Attributes<String,Object> args) {

        this.fullExport = fullExport;
        boolean exported = false;

        try {
            if (clazz.equals(Identity.class)) {

                boolean exportIdentitiesRiskScores = args.getBoolean("exportIdentitiesRiskScores");
                exported = exportIdentity(conn, id, exportIdentitiesRiskScores);
            } else if (clazz.equals(Link.class)) {
                exported = exportLink(conn, id);
            } else if (clazz.equals(Certification.class)) {
                exported = exportCertification(conn, id);
            } else if(clazz.equals(Tag.class)) {
                exported = exportTag(conn,id);
            }

            if (!clazz.equals(Certification.class))
                exportAttributes(conn, clazz, id);
        } catch (Throwable e) {
            log.error("DataExport: Error exporting object id="+id+" " + clazz.getName() , e);

            Object exceptionMsg = null;
            if (e instanceof GeneralException){
                exceptionMsg = ((GeneralException)e).getMessageInstance();
            } else {
                exceptionMsg = e.getMessage() != null ? e.getMessage() : "";
            }
            addMessage(Message.warn(MessageKeys.TASK_DATA_EXPORT_ERROR, clazz.getSimpleName(), id, exceptionMsg),
                    clazz.getSimpleName());

            // clean up the object so we don't leave a partial
            // record in the export tables
            try {
                delete(conn, clazz, id);
            } catch (GeneralException e1) {
                log.error("DataExport: Could not clean up object id="+id+" " + clazz.getName() +" following error." );
            }
        }

        return exported;
    }

    public void completeExport(Connection conn, String taskDefId, Class<? extends SailPointObject> exportClass, Date start)
            throws GeneralException{
        BrandingService brandingService = BrandingServiceFactory.getService();
        String sql = "delete from "+ brandingService.brandExportTableName(DataExportSchemaUtil.EXP_TBL)+" where task_def_id='"+taskDefId+"' and " +
                " class_name=?";

        JdbcUtil.update(conn, sql, exportClass.getName());

        String insertSql = "insert into " + brandingService.brandExportTableName(DataExportSchemaUtil.EXP_TBL) +
                " (task_def_id, class_name, start_dt, end_dt) values(?,?,?,?)";

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, taskDefId);
            stmt.setString(2, exportClass.getName());
            stmt.setLong(3, start.getTime());
            stmt.setLong(4, new Date().getTime());
            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
    }
    
    
    /**
     * Exports a tag to the sptr_tag
     * @param conn
     * @param id
     * @return
     * @throws GeneralException
     */
    private boolean exportTag(Connection conn, String id) throws GeneralException {
        Tag tag = context.getObjectById(Tag.class, id);
        if(tag==null) {
            return false;
        }
        
        String insertSql = "insert into " + BrandingServiceFactory.getService().brandExportTableName(DataExportSchemaUtil.TAG_TBL) +
            " (id, name) " +
            " values(?, ?)";
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, tag.getId());
            stmt.setString(2, tag.getName());
            stmt.execute();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        
        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        return true;
    }
    
    private boolean exportCertificationTag(Connection conn, String tagId, String certificationId, int idx) throws GeneralException {
        BrandingService brandingService = BrandingServiceFactory.getService();
        PreparedStatement delete = null;
        try {
            delete = conn.prepareStatement("delete from " + brandingService.brandExportTableName(DataExportSchemaUtil.CERT_TAG_TBL) + 
                    " where certification_id = ? and tag_id = ? and idx = ?");
            delete.setString(1, certificationId);
            delete.setString(2, tagId);
            delete.setInt(3, idx);
            delete.execute();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(delete);
        }
        
        
        String insertSql = "insert into " + brandingService.brandExportTableName(DataExportSchemaUtil.CERT_TAG_TBL) +
            " (certification_id, tag_id, idx) " +
            " values(?, ?, ?)";
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, certificationId);
            stmt.setString(2, tagId);
            stmt.setInt(3, idx);
            stmt.execute();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        
        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        return true;
    }

    /**
     * Export the identity for the given ID.
     * @param conn
     * @param id
     * @throws GeneralException
     */
    private boolean exportIdentity(Connection conn, String id, boolean includeRiskScores) throws GeneralException {

        Identity identity = context.getObjectById(Identity.class, id);

        if (identity == null)
            return false;

        Class<Identity> clazz = Identity.class;

        if (log.isDebugEnabled())
            log.debug("DateExport: Exporting identity '"+identity.getName()+"'.");

        if (!fullExport){
            delete(conn, clazz, id);
        }

        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);

        List<String> extendedCols = schemaUtil.getExtendedColumns(conn, clazz);

        String extendedColSql = !extendedCols.isEmpty() ?
                "," + Util.listToCsv(extendedCols) : "";

        String extendedColParams = "";
        for (int i = 0; i < extendedCols.size(); i++) {
            extendedColParams += ",?";
        }
        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "insert into " + brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_TBL) +
                " (id, name, display_name, firstname, lastname, email, manager, manager_display_name, " +
                " correlated, manager_status, inactive, created, created_dt, export_date " + extendedColSql + ") " +
                " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? " + extendedColParams + ")";

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, identity.getId());
            stmt.setString(2, identity.getName());
            stmt.setString(3, identity.getDisplayableName());
            stmt.setString(4, identity.getFirstname());
            stmt.setString(5, identity.getLastname());
            stmt.setString(6, identity.getEmail());
            if (identity.getManager() != null) {
                stmt.setString(7, identity.getManager().getName());
                stmt.setString(8, identity.getManager().getDisplayableName());
            } else {
                stmt.setNull(7, Types.VARCHAR);
                stmt.setNull(8, Types.VARCHAR);
            }
            stmt.setBoolean(9, identity.isCorrelated());
            stmt.setBoolean(10, identity.getManagerStatus());
            stmt.setBoolean(11, identity.isInactive());
            stmt.setLong(12, identity.getCreated().getTime());
            stmt.setTimestamp(13, new Timestamp(identity.getCreated().getTime()));
            stmt.setLong(14, new Date().getTime());


            int colIdx = 15;            
            for (String attr : extendedCols) {
                ObjectAttribute attrDef = conf.getExtendedAttributeMap().get(attr);
                String val = null;
                if (attrDef.isNamedColumn()){
                    Object attrValue = identity.getAttribute(attrDef.getName());
                    val = attrValue != null ? attrValue.toString() : null;
                } else if (!DataExportSchemaUtil.isExtendendedIdentityType(attrDef, Identity.class)) {
                    val = identity.getExtended(attrDef.getExtendedNumber());
                } else {
                    Identity relatedIdentity = identity.getExtendedIdentity(attrDef.getExtendedNumber());
                    if (relatedIdentity != null) {
                        val = relatedIdentity.getName();
                    }
                }
                stmt.setString(colIdx, val);
                colIdx++;
            }

            stmt.execute();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        
        if(includeRiskScores && identity.getScorecard()!=null) {
            Scorecard scorecard = identity.getScorecard();
            
            insertSql = "insert into " + brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_SCORECARD_TBL) +
                " (id, identity_id, composite_score, business_role_score, raw_business_role_score, entitlement_score, " +
                " raw_entitlement_score, policy_score, raw_policy_score, certification_score, total_violations, " +
                " total_remediations, total_delegations, total_mitigations, total_approvals) " +
                " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement stmt2 = null;
            try {
                stmt2 = conn.prepareStatement(insertSql);
                stmt2.setString(1, scorecard.getId());
                stmt2.setString(2, identity.getId());
                stmt2.setInt(3, scorecard.getCompositeScore());
                stmt2.setInt(4, scorecard.getBusinessRoleScore());
                stmt2.setInt(5, scorecard.getRawBusinessRoleScore());
                stmt2.setInt(6, scorecard.getEntitlementScore());
                stmt2.setInt(7, scorecard.getRawEntitlementScore());
                stmt2.setInt(8, scorecard.getPolicyScore());
                stmt2.setInt(9, scorecard.getRawPolicyScore());
                stmt2.setInt(10, scorecard.getCertificationScore());
                stmt2.setInt(11, scorecard.getTotalViolations());
                stmt2.setInt(12, scorecard.getTotalRemediations());
                stmt2.setInt(13, scorecard.getTotalDelegations());
                stmt2.setInt(14, scorecard.getTotalMitigations());
                stmt2.setInt(15, scorecard.getTotalApprovals());

                stmt2.execute();
            } catch (SQLException e) {
                throw new GeneralException(e);
            } finally {
                JdbcUtil.closeStatement(stmt2);
            }
        }

        String attrSql = "insert into "+schemaUtil.getExportAttributesTable(Identity.class)+" (object_id, attr_name, attr_value) " +
                " values('" + identity.getId() + "', ?, ?)";

        PreparedStatement attrStmt = null;
        try {
            attrStmt = conn.prepareStatement(attrSql);

            if (identity.getWorkgroups() != null) {
                for (Identity workgroup : identity.getWorkgroups()) {
                    attrStmt.setString(1, "iiq_workgroup");
                    attrStmt.setString(2, workgroup.getDisplayableName());
                    attrStmt.addBatch();
                }
            }

            Set<String> roles = new HashSet<String>();
            if (identity.getDetectedRoles() != null) {
                for (Bundle role : identity.getDetectedRoles()) {
                    roles.add(role.getName());
                }
            }

            if (identity.getRoleAssignments() != null) {
                for (RoleAssignment role : identity.getRoleAssignments()) {
                    if (!role.isNegative()) {
                        roles.add(role.getRoleName());
                    }
                }
            }

            if (!roles.isEmpty()) {
                for (String role : roles) {
                    attrStmt.setString(1, "iiq_role");
                    attrStmt.setString(2, role);
                    attrStmt.addBatch();
                }
            }

            if (identity.getLinks() != null) {
                for (Link link : identity.getLinks()) {
                    attrStmt.setString(1, "iiq_application");
                    attrStmt.setString(2, link.getApplicationName());
                    attrStmt.addBatch();
                }
            }

            attrStmt.executeBatch();

        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(attrStmt);
        }

        if (identity.getExceptions() != null){
            for(EntitlementGroup grp : identity.getExceptions()){
                exportIdentityException(conn, grp, identity.getId());
            }
        }

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        return true;
    }
    
    private void exportIdentityException(Connection conn, EntitlementGroup group, String identityId)
        throws GeneralException{

        // if the app no longer exists, don't bother exporting the entitlement
        if (group.getApplication() == null)
            return;
        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "insert into "+ brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_ENTS) +
                " (type, description, value, id, identity_id, native_identity, display_name ," +
                " application_id, application_name, application_instance, export_date) " +
                "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int maxValueLength = schemaUtil.getMaxValueLength(conn, brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_ENTS));

        PreparedStatement stmt = null;
        try {

            stmt = conn.prepareStatement(insertSql);
            stmt.setString(4, group.getId());
            stmt.setString(5, identityId);
            stmt.setString(6, group.getNativeIdentity());
            stmt.setString(7, group.getDisplayName());
            stmt.setString(8, group.getApplication().getId());
            stmt.setString(9, group.getApplicationName());
            stmt.setString(10, group.getInstance());
            stmt.setLong(11, new Date().getTime());

            int cnt = 0;

            cnt += insertPermissions(stmt, group.getPermissions());
            cnt += insertAttributes(group.getClass(), group.getDisplayName(), stmt, group.getAttributes(), maxValueLength);

            if (cnt > 0)
                stmt.executeBatch();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
    }

    private int insertAttributes(Class<?> clazz, String id, PreparedStatement stmt, Attributes<String, Object> attributes, int maxValueLength) throws SQLException{
        int cnt = 0;
        if (attributes != null){
            for(String attr : attributes.getKeys()){
                List<String> val = convertAttrVal(clazz, id, attr, attributes.get(attr), maxValueLength);
                if (val != null){
                    for(String v : val){
                        stmt.setString(1, "Attribute");
                        stmt.setString(2, attr);
                        stmt.setString(3, v);
                        stmt.addBatch();
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }

    private int insertPermissions(PreparedStatement stmt, List<Permission> permissions) throws SQLException{
        int cnt = 0;
        if (permissions != null){
             for(Permission perm : permissions){
                if (perm.getRights() != null){
                    for(String right : perm.getRightsList()){
                        stmt.setString(1, "Permission");
                        stmt.setString(2, perm.getTarget());
                        stmt.setString(3, right);
                        stmt.addBatch();
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }


    private boolean exportLink(Connection conn, String id) throws GeneralException {

        Class<Link> clazz = Link.class;

        if (!fullExport){
            delete(conn, clazz, id);
        }

         if (log.isDebugEnabled()){
                log.debug("Exporting link id=" + id);
            }

        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);

        List<String> extendedCols = schemaUtil.getExtendedColumns(conn, clazz);

        String extendedColSql = !extendedCols.isEmpty() ?
                "," + Util.listToCsv(extendedCols) : "";

        String extendedColParams = "";
        for (int i = 0; i < extendedCols.size(); i++) {
            extendedColParams += ",?";
        }

        String insertSql = "insert into "+schemaUtil.getExportTable(clazz)+
                " (id, native_identity, display_name, application_id, application_name, application_instance, " +
                " identity_id, identity_name, identity_display_name, composite, manually_correlated, created, " +
                " created_dt, export_date, entitlements " +extendedColSql+ ") values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? "+
                extendedColParams + ")";
        
        List<String> properties = new ArrayList<String>();
        properties.addAll(Arrays.asList("nativeIdentity", "displayName", "application.id", "application.name",
                        "instance", "identity.id","identity.name", "identity.displayName", "componentIds",
                        "manuallyCorrelated", "created", "entitlements"));
        if ( Util.size(extendedCols) > 0 ) {
            properties.addAll(extendedCols);
        }
        
        QueryOptions ops = new QueryOptions(Filter.eq("id", id));
        ops.setScopeResults(false);
        Iterator<Object[]> iter = context.search(Link.class, ops, properties);

        Object[] link = null;
        if (iter != null && iter.hasNext()) {
            link = iter.next();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(insertSql);
                stmt.setString(1, id);
                stmt.setString(2, (String)link[0]);
                stmt.setString(3, (String)link[1]);
                stmt.setString(4, (String)link[2]);
                stmt.setString(5, (String)link[3]);
                stmt.setString(6, (String)link[4]);
                stmt.setString(7, (String)link[5]);
                stmt.setString(8, (String)link[6]);
                stmt.setString(9, (String)link[7]);
                stmt.setBoolean(10, link[8] != null && link[8].toString().length() > 0);
                stmt.setBoolean(11, (Boolean)link[9]);
                Date created = (Date)link[10];
                stmt.setLong(12, ((Date)link[10]).getTime());
                stmt.setTimestamp(13, new Timestamp(created.getTime()));
                stmt.setLong(14, new Date().getTime());
                stmt.setBoolean( 15, (Boolean)link[11] );
                int colIdx = 16;                

                int projectionIdx = 11;
                for (int i=1; i<=extendedCols.size(); i++) {               
                    String val = (String)link[projectionIdx + i];
                    stmt.setString(colIdx++, val);
                }
                stmt.execute();
            } catch (SQLException e) {
                log.error("Failed exporting link id=" + id, e);
                throw new GeneralException(e);
            } finally {
                JdbcUtil.closeStatement(stmt);
            }
        } else {
            // no link was found
            return false;
        }

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        return true;
    }

    public boolean exportCertification(Connection conn, String id) throws GeneralException {

        Certification cert = context.getObjectById(Certification.class, id);

        Class<Certification> clazz = Certification.class;

        if (cert == null)
            return false;

        if (!fullExport){
            delete(conn, clazz, id);
        }

        if (log.isDebugEnabled()){
            log.debug("Exporting certification id='" + cert.getId() + "' ");
        }

        Date createDate = cert.getCreated();

        String targetName = null;
        String targetDisplayName = null;

        if (cert.getManager() != null){
            targetName = cert.getManager();
            targetDisplayName = getIdentityDisplayName(targetName);
        } else if (cert.getApplicationId() != null){
            targetName = getApplicationName(cert.getApplicationId());
        } else if (cert.getGroupDefinitionName() != null){
            targetName = cert.getGroupDefinitionName();
        }

        if (targetDisplayName == null)
            targetDisplayName=targetName;

        String insertSql = "insert into "+schemaUtil.getExportTable(clazz)+
                " (id, name, short_name, description, type ," +
                " continuous, phase, parent_certification_id, " +
                " created,created_dt,finished, finished_dt, " +
                " signed, signed_dt, expiration,expiration_dt, " +
                " target_name, target_display_name, " +
                " creator_name, creator_display_name, item_percent_complete, cert_grp_id, cert_grp_name, " +
                " cert_grp_owner, cert_grp_owner_display_name, export_date, total_accounts, " + 
                "accounts_approved, accounts_allowed, accounts_remediated, " +
                "roles_allowed, exceptions_allowed, excluded_entities, excluded_items, " +
                "automatic_closing_date ) " +
                " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = null;
        Locale locale = Locale.getDefault();
        try {

            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, id);
            stmt.setString(2, cert.getName());
            stmt.setString(3, cert.getShortName());
            stmt.setString(4, cert.getDescription());
            stmt.setString(5, Internationalizer.getMessage(cert.getType().getMessageKey(), locale));
            stmt.setBoolean(6, cert.isContinuous());
            stmt.setString(7, cert.getPhase() != null ?
                    Internationalizer.getMessage(cert.getPhase().getMessageKey(), locale) : null);
            stmt.setString(8, cert.getParent() != null ? cert.getParent().getId() : null);
            stmt.setLong(9, cert.getCreated().getTime());
            stmt.setTimestamp(10, new Timestamp(cert.getCreated().getTime()));
            if (cert.getFinished() != null){
                stmt.setLong(11, cert.getFinished().getTime());
                stmt.setTimestamp(12, new Timestamp(cert.getFinished().getTime()));
            }else{
                stmt.setNull(11, dbType.getDateSqlType());
                stmt.setNull(12, Types.TIMESTAMP);
            }
            if (cert.getSigned() != null){
                stmt.setLong(13, cert.getSigned().getTime());
                stmt.setTimestamp(14, new Timestamp(cert.getSigned().getTime()));
            }else{
                stmt.setNull(13, dbType.getDateSqlType());
                stmt.setNull(14, Types.TIMESTAMP);
            }
            if (cert.getExpiration() != null){
                stmt.setLong(15, cert.getExpiration().getTime());
                stmt.setTimestamp(16, new Timestamp(cert.getExpiration().getTime()));
            }else{
                stmt.setNull(15, dbType.getDateSqlType());
                stmt.setNull(16, Types.TIMESTAMP);
            }
            stmt.setString(17, targetName);
            stmt.setString(18, targetDisplayName);
            stmt.setString(19, cert.getCreator());

            // attempt to get display name of creator, defaulting to name
            String creatorDisplayName = cert.getCreator();
            Identity creator = cert.getCreator(context);
            if (creator != null)
                creatorDisplayName = creator.getDisplayName();

            stmt.setString(20, creatorDisplayName);
            stmt.setInt(21, cert.getItemPercentComplete());

            List<CertificationGroup> groups =
                    cert.getCertificationGroupsByType(CertificationGroup.Type.Certification);
            CertificationGroup group = null;
            String grpOwner= null;
            String grpOwnerDisplayName = null;
            if (groups != null && !groups.isEmpty()){
                group = groups.get(0);
                if (group != null && group.getOwner() != null){
                    grpOwner = group.getOwner().getName();
                    grpOwnerDisplayName = group.getOwner().getDisplayableName();
                }
            }

            stmt.setString(22, group != null ? group.getId() : null);
            stmt.setString(23, group != null ? group.getName() : null);
            stmt.setString(24, grpOwner);
            stmt.setString(25, grpOwnerDisplayName);

            stmt.setLong(26, new Date().getTime());// export date
            CertificationStatistics statistics = cert.getStatistics();
            stmt.setLong( 27, statistics.getTotalAccounts() );
            stmt.setLong( 28, statistics.getAccountsApproved() );
            stmt.setLong( 29, statistics.getAccountsAllowed() );
            stmt.setLong( 30, statistics.getAccountsRemediated() );
            stmt.setLong( 31, statistics.getRolesAllowed() );
            stmt.setLong( 32, statistics.getExceptionsAllowed() );
            stmt.setLong( 33, statistics.getExcludedEntities() );
            stmt.setLong( 34, statistics.getExcludedItems() );
            Date automaticClosingDate = cert.getAutomaticClosingDate();
            if( automaticClosingDate != null ) {
                stmt.setLong( 35, cert.getAutomaticClosingDate().getTime() );
            } else {
                stmt.setNull( 35, dbType.getDateSqlType() );
            }
            
            stmt.execute();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }

        PreparedStatement attrStmt = null;
        try {
            String attrSql = "insert into "+schemaUtil.getExportAttributesTable(Certification.class)+" " +
                    "(object_id, attr_name, attr_value) values(?, ?, ?)";
            attrStmt = conn.prepareStatement(attrSql);

            if (cert.getCertifiers() != null) {
                for (String certifier : cert.getCertifiers()) {
                    attrStmt.setString(1, cert.getId());
                    attrStmt.setString(2, "iiq_certifier");
                    attrStmt.setString(3, certifier);
                    attrStmt.addBatch();
                }
            }

            if (cert.getTags() != null){
                for(Tag tag : cert.getTags()){
                    attrStmt.setString(1, cert.getId());
                    attrStmt.setString(2, "iiq_tag");
                    attrStmt.setString(3, tag.getName());
                    attrStmt.addBatch();
                }
            }

            if (cert.getSignOffHistory() != null){
                for(SignOffHistory history : cert.getSignOffHistory()){
                    attrStmt.setString(1, cert.getId());
                    attrStmt.setString(2, "iiq_signer");
                    attrStmt.setString(3, history.getSignerName());
                    attrStmt.addBatch();
                }
            }

            attrStmt.executeBatch();

        } catch (SQLException e) {
            log.error("Failed exporting certification id=" + id, e);
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(attrStmt);
        }

        List<ArchivedCertificationEntity> archivedEntities = cert.fetchArchivedEntities(context);
        if (archivedEntities != null){
            for (ArchivedCertificationEntity entity : archivedEntities){
                if (entity.getEntity().getItems() != null){
                    for (CertificationItem item : entity.getEntity().getItems()){
                        String reason = entity.getReason().getMessageKey();
                        this.exportCertificationItem(conn, item, id,
                                        Internationalizer.getMessage(reason, locale),
                                entity.getExplanation(), createDate);
                    }
                }
            }
        }

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        QueryOptions ops = new QueryOptions(Filter.eq("parent.certification.id", id));
        Iterator<Object []> iter = context.search(CertificationItem.class, ops, Arrays.asList("id"));
        int cnt = 0;
        while (iter.hasNext()) {
            String itemId = (String)iter.next()[0];
            exportCertificationItem(conn, itemId, id, createDate);
            cnt++;
            if(cnt % 100 == 0){
                context.decache();
            }
        }
        
        List<Tag> tags = cert.getTags();
        for(int i=0; i<tags.size(); i++) {
            Tag tag = tags.get(i);
            exportCertificationTag(conn, tag.getId(), cert.getId(), i);
        }

        return true;
    }

    /**
     * Export certification item
     * @param conn
     * @param id
     * @param certificationId
     * @throws GeneralException
     */
    public void exportCertificationItem(Connection conn, String id, String certificationId,
                                        Date certCreateDate) throws GeneralException {
        CertificationItem item = context.getObjectById(CertificationItem.class, id);
        if (item == null)
            return;

        exportCertificationItem(conn, item, certificationId, null, null, certCreateDate);

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }
    }

    public void exportCertificationItem(Connection conn, CertificationItem item, String certificationId,
                                        String exclusionReason, String exclusionExplanation, Date certCreateDate) throws GeneralException {

        Locale locale = Locale.getDefault();

        String insertSql = "insert into "+schemaUtil.getExportTable(CertificationItem.class)+
                " (id, certification_id, type, role, violationSummary ," +
                " decision, actor_name, actor_display_name, decision_comments, bulk_decision, " +
                " mitigation_expiration, remediation_kicked_off, remediation_completed, delegation, " +
                " challenge_decision, challenge_decision_maker, challenge_comments, exclusion_reason, " +
                "exclusion_explanation, created, export_date, target, target_display_name, target_type, " +
                " manager, manager_display_name, completed, created_dt, mitigation_expiration_dt, summary_status) " +
                "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?,?,?,?,?,?)";

        // exclusions will not have ids, create one
        String itemId = item.getId();
        boolean isExclusion = item.getId() == null;
        if(item.getId() == null){
            itemId = new java.rmi.dgc.VMID().toString();
        }


        if (log.isDebugEnabled()){
            if (!isExclusion)
                log.debug("Exporting certification item id='" + itemId + "' ");
            else
                log.debug("Exporting excluded certification item, generated id='" + itemId + "' ");
        }

        String target = item.getIdentity();
        String targetDisplayName = null;
        if (target == null){
            target = item.getTargetName();
        } else {
            targetDisplayName = getIdentityDisplayName(target);
        }

        if (target == null){
            target = item.getAccountGroup();
        }

        if (targetDisplayName == null)
            targetDisplayName = target;

        String managerName = null;
        String managerDisplayName = null;
        if (item.getIdentity() != null){
            Identity identity = item.getIdentity(context);
            if (identity != null &&  identity.getManager() != null){
                managerName = identity.getManager().getName();
                managerDisplayName = identity.getManager().getDisplayName();
            }
        }

        String decision = null;
        String actor = null;
        String actorDisplayName = null;
        String decisionComments = null;
        Date mitigationExpiration = null;
        boolean bulkDecision = false;
        boolean remediationKickedOff = false;
        boolean remediationCompleted = false;
        if (item.getAction() != null && item.getAction().getStatus() != null){
            decision = Internationalizer.getMessage(item.getAction().getStatus().getMessageKey(), locale);
            actor = item.getAction().getActorName();
            actorDisplayName = getIdentityDisplayName(actor);
            decisionComments = item.getAction().getComments();
            bulkDecision = item.getAction().isBulkCertified();
            mitigationExpiration = item.getAction().getMitigationExpiration();
            remediationKickedOff = item.getAction().isRemediationKickedOff();
            remediationCompleted = item.getAction().isRemediationCompleted();
        }

        String challengeDecision = null;
        String challengeComments = null;
        String challengeDecisionMaker = null;
        if (item.getChallenge() != null){
            challengeComments = item.getChallenge().getComments();
            challengeDecisionMaker = item.getChallenge().getDeciderName();
            challengeDecision = item.getChallenge().getDecision() != null ?
                    Internationalizer.getMessage(item.getChallenge().getDecision().getMessageKey(), locale) : null;
        }

        PreparedStatement stmt = null;
        try {


            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, itemId);
            stmt.setString(2, certificationId);
            stmt.setString(3, Internationalizer.getMessage(item.getType().getMessageKey(), locale));
            stmt.setString(4, item.getBundle());
            stmt.setString(5, item.getViolationSummary());
            stmt.setString(6, decision);
            stmt.setString(7, actor);
            stmt.setString(8, actorDisplayName);
            stmt.setString(9, decisionComments);
            stmt.setBoolean(10, bulkDecision);
            if (mitigationExpiration != null)
                stmt.setLong(11, mitigationExpiration.getTime());
            else
                stmt.setNull(11, dbType.getDateSqlType());
            stmt.setBoolean(12, remediationKickedOff);
            stmt.setBoolean(13, remediationCompleted);
            stmt.setBoolean(14,  item.isDelegated());
            stmt.setString(15, challengeDecision);
            stmt.setString(16, challengeDecisionMaker);
            stmt.setString(17, challengeComments);
            stmt.setString(18, exclusionReason);
            stmt.setString(19, exclusionExplanation);
            // archived cert exclusion items may not have create date
            Date createDate = item.getCreated();
            if (createDate == null){
                createDate = certCreateDate;
            }
            stmt.setLong(20, createDate.getTime());
            stmt.setLong(21, new Date().getTime());
            stmt.setString(22, target);
            stmt.setString(23, targetDisplayName);
            stmt.setString(24, Internationalizer.getMessage(item.getParent().getType().getMessageKey(),locale));
            stmt.setString(25, managerName);
            stmt.setString(26, managerDisplayName);
            stmt.setBoolean(27, decision != null);
            stmt.setTimestamp(28, new Timestamp(createDate.getTime()));
            if (mitigationExpiration != null){
                stmt.setTimestamp(29, new Timestamp(mitigationExpiration.getTime()));
            } else {
                stmt.setNull(29, Types.TIMESTAMP);        
            }
            stmt.setString(30, item.getSummaryStatus().getLocalizedMessage());
            stmt.execute();
        } catch (SQLException e) {
            log.error("Failed exporting certification item id=" + itemId, e);
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }

        if (item.getEntitlements() != null){
            for (EntitlementSnapshot snap : item.getEntitlements()){
                exportEntitlements(conn, snap, itemId, certificationId);
            }
        }
    }

    /**
     * Export entitlement snapshot
     * @param conn
     * @param snap
     * @param certificationItemId
     * @param certificationId
     * @throws GeneralException
     */
    private void exportEntitlements(Connection conn, EntitlementSnapshot snap, String certificationItemId,
                                    String certificationId)
        throws GeneralException{

        String tableName = schemaUtil.getExportTable(EntitlementSnapshot.class);
        
        String insertSql = "insert into "+ tableName +
                " (type, description, value, certification_item_id, native_identity, display_name ," +
                " application_id, application_name, application_instance,  " +
                " export_date, certification_id, account_only) " +
                "values( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int maxValueLength = schemaUtil.getMaxValueLength(conn, tableName);       

        PreparedStatement stmt = null;
        try {

            if (log.isDebugEnabled())
                log.debug("Exporting certification item entitlement snapshot, item id='"+certificationItemId +
                        "' snapshot id='" + snap.getId() + "' ");

            stmt = conn.prepareStatement(insertSql);
            stmt.setString(4, certificationItemId);
            stmt.setString(5, snap.getNativeIdentity());
            stmt.setString(6, snap.getDisplayableName());
            stmt.setString(7, getApplicationId(snap.getApplicationName()));
            stmt.setString(8, snap.getApplicationName());
            stmt.setString(9, snap.getInstance());
            stmt.setLong(10, new Date().getTime());
            stmt.setString(11, certificationId);
            stmt.setBoolean( 12, snap.isAccountOnly() );

            int cnt = 0;

            cnt += insertPermissions(stmt, snap.getPermissions());
            cnt += insertAttributes(snap.getClass(), snap.getDisplayableName(), stmt, snap.getAttributes(), maxValueLength);

            if (cnt > 0)
                stmt.executeBatch();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
    }



    /**
     * Utility used to look up display name given an identity name.
     * This is used in cases where you dont want hibernate to have to
     * load an identity object to fetch the display name,
     * @param name
     * @return
     * @throws GeneralException
     */
    private String getIdentityDisplayName(String name) throws GeneralException{
        QueryOptions ops = new QueryOptions(Filter.eq("name", name));
        ops.setScopeResults(false);
        Iterator<Object[]> results =
                context.search(Identity.class, ops, Arrays.asList("displayName"));
        if (results == null || !results.hasNext())
            return null;
        else
            return (String)results.next()[0];
    }

    /**
     * Utility to used to look up an application name given an id.
     * @param id
     * @return
     * @throws GeneralException
     */
    private String getApplicationName(String id) throws GeneralException{
        Iterator<Object[]> results =
                context.search(Application.class, new QueryOptions(Filter.eq("id", id)), Arrays.asList("name"));
        if (results == null || !results.hasNext())
            return null;
        else
            return (String)results.next()[0];
    }

    /**
     * Clears the export database of all records for the given class
     * @param conn
     * @param clazz
     * @throws GeneralException
     */
    public int deleteAll(Connection conn, Class<? extends SailPointObject> clazz) throws GeneralException {
        BrandingService brandingService = BrandingServiceFactory.getService();
        int cnt = JdbcUtil.update(conn, "delete from " + schemaUtil.getExportTable(clazz), null);
        String attributesTable = schemaUtil.getExportAttributesTable(clazz);
        if(attributesTable!=null) {
            JdbcUtil.update(conn, "delete from " + attributesTable, null);
        }

        if (Certification.class.equals(clazz)) {
            JdbcUtil.update(conn, "delete from " + brandingService.brandExportTableName(DataExportSchemaUtil.CERT_ITEM_ENTS_TBL), null);
            JdbcUtil.update(conn, "delete from "+schemaUtil.getExportAttributesTable(CertificationItem.class), null);
            JdbcUtil.update(conn, "delete from " +schemaUtil.getExportTable(CertificationItem.class), null);
        } else if (Identity.class.equals(clazz)) {
            JdbcUtil.update(conn, "delete from "+ brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_ENTS), null);
            JdbcUtil.update(conn, "delete from "+ brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_SCORECARD_TBL), null);
        }

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        return cnt;
    }

    /**
     * Delete a given object and all it's dependants
     * @param conn
     * @param clazz
     * @param id
     * @throws GeneralException
     */
    public int delete(Connection conn, Class<? extends SailPointObject> clazz, String id) throws GeneralException {
        BrandingService brandingService = BrandingServiceFactory.getService();
        int cnt = JdbcUtil.update(conn, "delete from " + schemaUtil.getExportTable(clazz) + " where id=?", id);
        JdbcUtil.update(conn, "delete from " + schemaUtil.getExportAttributesTable(clazz) +
                " where object_id=?", id);

        if (Certification.class.equals(clazz)) {
            JdbcUtil.update(conn, "delete from "+ brandingService.brandExportTableName(DataExportSchemaUtil.CERT_ITEM_ENTS_TBL)+
                    " where certification_id=?", id);
            JdbcUtil.update(conn, "delete from "+schemaUtil.getExportTable(CertificationItem.class)+
                    " where certification_id=?", id);
            JdbcUtil.update(conn, "delete from "+schemaUtil.getExportAttributesTable(CertificationItem.class)+
                    " where exists( " +
                    " select id from " + brandingService.brandExportTableName("sptr_cert_item") + " where id = object_id and certification_id=?)", id);
        } else if (Identity.class.equals(clazz)) {
            JdbcUtil.update(conn, "delete from "+ brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_ENTS)+
                    " where identity_id=?", id);
            JdbcUtil.update(conn, "delete from "+ brandingService.brandExportTableName(DataExportSchemaUtil.IDENTITY_SCORECARD_TBL)+
                    " where identity_id=?", id);
        }

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }


        return cnt;
    }
    
    /**
     * Gets the property of the class that returns a displayable name.
     * @param c The class to use.
     * @return The name of the property.
     */
    private String getNameProperty(Class<? extends SailPointObject> c) {
        if (Link.class.equals(c)) {
            return "displayName";
        }
        
        return "name";
    }
    
    /**
     * Export attributes for the given object to the class' attribute export table.
     * @param conn
     * @param clazz
     * @param id
     * @throws GeneralException
     */
    private void exportAttributes(Connection conn, Class<? extends SailPointObject> clazz, String id)
            throws GeneralException {

        if (log.isDebugEnabled()){
            log.debug("DataExport: Exporting attributes for class='"+clazz.getName()+"' id='" + id + "' ");
        }

        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
        if(conf!=null) {
            Map<String,ObjectAttribute> attrDefinitions = conf.getObjectAttributeMap();
            Attributes<String, Object> attributes = null;
            String displayedIdentifier = id;
    
            QueryOptions ops = new QueryOptions(Filter.eq("id", id));
            ops.setScopeResults(false);         
            
            Iterator<Object[]> iter = context.search(clazz, ops, Arrays.asList("attributes", getNameProperty(clazz)));
            if (iter != null && iter.hasNext()){
                Object[] values = iter.next();
                
                attributes = (Attributes<String, Object>)values[0];
                
                String name = (String)values[1];
                if (!Util.isNullOrEmpty(name)) {
                    displayedIdentifier = name;
                }
            }
            
            if (attributes != null && !attributes.isEmpty()) {
                int maxAttrLength = schemaUtil.getMaxExtendedColumnLength(clazz);
                PreparedStatement attrStmt = null;
    
                String attrSql = "insert into "+schemaUtil.getExportAttributesTable(clazz)+
                        " (object_id, attr_name, attr_value) values('" + id + "', ?, ?)";
                try {
    
                    attrStmt = conn.prepareStatement(attrSql);
    
                    int batchCnt = 0;
                    for(String attr : attributes.keySet()){
                        if ( attr == null ) {
                            // shouldn't happen but guard against 
                            continue;
                        }
                        ObjectAttribute attrDef = attrDefinitions.get(attr);
                        if (attrDef == null || (!attrDef.isSystem() && !attrDef.isStandard() && !attrDef.isExtended())) {
                            Object attrValue = attributes.get(attr);
                            for(String v : convertAttrVal(clazz, displayedIdentifier, attr, attrValue, maxAttrLength)){
                                attrStmt.setString(1, attr);
                                attrStmt.setString(2, v);
                                attrStmt.addBatch();
                                batchCnt++;
                            }
                        }
                    }
    
                    if (batchCnt > 0){
                        attrStmt.executeBatch();
                        conn.commit();
                    }
    
                } catch (SQLException e) {
                    throw new GeneralException(e);
                } finally {
                    JdbcUtil.closeStatement(attrStmt);
                }
            }
        }
    }

    /**
     * Converts an attribute into a string which will fit nicely into
     * the EntitlementSnapshot export table
     * @param rawValue
     * @param maxLength
     * @return
     */
    private List<String> convertAttrVal(Class<?> clazz, String id, String attributeName, Object rawValue, int maxLength) {
        List<String> value = new ArrayList<String>();
        if (rawValue != null && rawValue instanceof Date) {
            Date date = (Date) rawValue;
            value.add(Long.toString(date.getTime()));
        } else if (rawValue != null && rawValue instanceof Collection<?>){
            Collection<String> col = (Collection<String>)rawValue;
            for (Iterator<String> iterator = col.iterator(); iterator.hasNext();) {
                Object o =  iterator.next();
                List<String> v = convertAttrVal(clazz, id, attributeName, o, maxLength);
                value.addAll(v);
            }
        } else if (rawValue != null){
            String v = rawValue.toString();
            
            if (maxLength < v.length()) {
                v = v.substring(0, maxLength);
                
                Message message = new Message(Message.Type.Warn, MessageKeys.TASK_DATA_EXPORT_TRUNCATED_VALUE,
                        attributeName,
                        clazz.getSimpleName(),
                        id);
                
                addMessage(message, clazz.getSimpleName());
            }
            
            value.add(v);
        }

        return value;
    }

    /**
     * Ensure that all exported objects of the given type still exist in IIQ. If not, delete
     * them from the export database.
     * @param conn
     * @param objectClass
     * @return Count of objects pruned
     * @throws GeneralException
     */
    public int pruneDeletedObjects(Connection conn, Class<? extends SailPointObject> objectClass) throws GeneralException{

        int recordsPruned = 0;

        Set<String> ids = new HashSet<String>();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("select id from "+schemaUtil.getExportTable(objectClass));
            ResultSet rs = stmt.executeQuery();
            while(rs.next()){
                ids.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }

        for(String id : ids){
            int cnt = context.countObjects(objectClass, new QueryOptions(Filter.eq("id", id)));
            if (cnt == 0){
                if (log.isDebugEnabled())
                    log.debug("DataExport: Pruning object id="+id+" of " + objectClass.toString());
                delete(conn, objectClass, id);
                recordsPruned++;
            }
        }

        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }

        return recordsPruned;
    }

    /**
     * For the given application name, look up the id.
     * @param appName
     * @return
     * @throws GeneralException
     */
    private String getApplicationId(String appName) throws GeneralException{

        if (appName==null)
            return null;

        if (applicationIds == null)
            applicationIds = new HashMap<String, String>();

        String appId = null;

        if (!applicationIds.containsKey(appName)){
            Iterator<Object[]> iter =
                    context.search(Application.class, new QueryOptions(Filter.eq("name", appName)), Arrays.asList("id"));
            if (iter != null && iter.hasNext()){
                appId = (String)iter.next()[0];
                if (appId != null)
                    applicationIds.put(appName, appId);
            }

        } else {
            appId = applicationIds.get(appName);
        }

        return appId;
    }

    private void addMessage(Message msg, String objectName){
        if (messages == null)
            messages = new ArrayList<Message>();

        // Attach an error msg for the TaskResult. Limit the number of error msgs
        // so that we don't blow up the TaskResult
        int msgCnt = messages != null ? messages.size() : 0;
        if (msgCnt > MAX_ERR_CNT)
            return;
        else if (msgCnt == MAX_ERR_CNT)
            messages.add(Message.error(MessageKeys.TASK_DATA_EXPORT_TOO_MANY_ERRS, objectName));
        else
            messages.add(msg);
    }

    public List<Message> getMessages(){
        return messages;
    }

    public void clearMessages(){
        messages = null;
    }
}
