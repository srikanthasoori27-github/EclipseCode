/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import sailpoint.persistence.DB2Dialect;
import sailpoint.persistence.SQLServerUnicodeDialect;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.Version;
import sailpoint.reporting.custom.CEFDataExportSchemaUtil;
import sailpoint.api.SailPointContext;
import sailpoint.web.messages.MessageKeys;

import org.hibernate.dialect.MySQL5InnoDBDialect;
import org.hibernate.dialect.Oracle10gDialect;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.sql.Types;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Timestamp;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.PropertyUtils;

import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scorecard;
import sailpoint.object.SyslogEvent;

/**
 * Exports denormalized view of certain IIQ objects to an external datasource.
 *
 */
public class CEFDataExporter {

    private static Log log = LogFactory.getLog(CEFDataExporter.class);

    // ////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    // ////////////////////////////////////////////////////////////////////
    public static final String CALCULATED_COLUMN_PREFIX = "SPT_";
    public static final String ATT_IDENT_SEARCH_COL_WORKGROUP_ID = "workgroups.id";
    public static final String ATT_IDENT_SEARCH_COL_APPLICATION_ID = "links.application.id";
    public static final String ATT_IDENT_SEARCH_SCORECARD = "scorecard";

    // Max number of errors we will accumulate on this object. It's possible
    // this task could generate 100000s of errors. Which might be bad...
    private static int MAX_ERR_CNT = 20;
    public static Map<String, Object> cefHeader = new HashMap<String, Object>();
    public static Map<String, String> sysLogSeverity = new HashMap<String, String>();
    public static List<String> identity_extension = Arrays.asList("id","name", "firstname", "lastname", "email", "SPT_workgroups.id", "roles","roleAssignments", "SPT_links.application.id" );
    public static List<String> link_extension = Arrays.asList("id","application.name", "entitlements"," nativeIdentity" );
    public static List<String> syslog_extension = Arrays.asList("id","eventLevel","created","message" );
    public static List<String> audit_extension = Arrays.asList("id","action","created","source", "target" );

    private CEFDataExportSchemaUtil schemaUtil;
    private SailPointContext context;    
    private boolean fullExport;
    
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

        public int getDateSqlType() {
            return sqlDateType;
        }

    }

    public CEFDataExporter(SailPointContext context, DatabaseType type) throws GeneralException {
        schemaUtil = new CEFDataExportSchemaUtil(type);
        this.context = context;
        cefHeader.put("cef_version", "CEF:0");
        cefHeader.put("device_product", "IdentityIQ");
        cefHeader.put("device_vendor", "SailPoint");
        cefHeader.put("severity", "10");
        sysLogSeverity.put("ERROR","10");
        sysLogSeverity.put("FATAL","9");
        sysLogSeverity.put("WARN","5");
        sysLogSeverity.put("TRACE","4");
        sysLogSeverity.put("DEBUG","3");
        sysLogSeverity.put("INFO","2");
        sysLogSeverity.put("ALL","1");
        
        
    }

    /**
     * Gets the max export_date for a given object type.
     * @param conn
     * @param clazz
     * @param taskDefId
     * @return
     * @throws SQLException
     */
    public Date calculateLastExport(Connection conn, Class<?> clazz, String taskDefId) throws SQLException {
        String sql = "select end_dt from "+ BrandingServiceFactory.getService().brandExportTableName(CEFDataExportSchemaUtil.CEF_EXP_TBL) +
                " where task_def_id='"+taskDefId+"' and class_name=?";
        String dt = JdbcUtil.queryString(conn, sql, clazz.getName());
        if (dt != null)
            return new Date(Util.atol(dt));
        return null;
    }

    public boolean exportCEF(Connection conn, Class<? extends SailPointObject> clazz, String id, 
            boolean fullExport, Attributes<String,Object> args, String objName) {

        this.fullExport = fullExport;
        boolean exported = false;
        Configuration config = Configuration.getSystemConfig();
        try {
            if (clazz.equals(Identity.class)) {
                boolean exportIdentitiesRiskScores = args.getBoolean("exportIdentitiesRiskScores");
                exported = exportCEFIdentity(conn, id, exportIdentitiesRiskScores, args, objName, config);
            } else if (clazz.equals(Link.class)) {
                exported = exportCEFLink(conn, id, false, args, objName, config);
            } else if (clazz.equals(AuditEvent.class)) {
                    exported = exportCEFAuditEvent(conn, id, false, args, objName, config);
            } else if(clazz.equals(SyslogEvent.class)) {
                exported = exportCEFSyslogEvent(conn, id, false, args, objName, config);
            }
        } catch (Throwable e) {
            log.error("CEFDataExport: Error exporting object id="+id+" " + clazz.getName() , e);
            Object exceptionMsg = null;
            if (e instanceof GeneralException) {
                exceptionMsg = ((GeneralException)e).getMessageInstance();
            } else {
                exceptionMsg = e.getMessage() != null ? e.getMessage() : "";
            }
            addMessage(Message.warn(MessageKeys.TASK_DATA_EXPORT_ERROR, clazz.getSimpleName(), id, exceptionMsg),clazz.getSimpleName());
        }
        return exported;
    }

    public void completeCEFExport(Connection conn, String taskDefId, Class<? extends SailPointObject> exportClass, Date start)
    throws GeneralException {
        BrandingService brandingService = BrandingServiceFactory.getService();
        String sql = "delete from "+ brandingService.brandExportTableName(CEFDataExportSchemaUtil.CEF_EXP_TBL)+" where task_def_id='"+taskDefId+"' and " + " class_name=?";
        if (log.isDebugEnabled())
            log.debug("CEFDataExport: Delete from " + CEFDataExportSchemaUtil.CEF_EXP_TBL + " : " +sql.toString());
        JdbcUtil.update(conn, sql, exportClass.getName());

        String insertSql = "insert into " + brandingService.brandExportTableName(CEFDataExportSchemaUtil.CEF_EXP_TBL) + " (task_def_id, class_name, start_dt, end_dt) values(?,?,?,?)";

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, taskDefId);
            stmt.setString(2, exportClass.getName());
            stmt.setLong(3, start.getTime());
            stmt.setLong(4, new Date().getTime());

            if (log.isDebugEnabled())
                log.debug("CEFDataExport: Insert to " + CEFDataExportSchemaUtil.CEF_EXP_TBL + " : " + insertSql.toString());

            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
    }

    /**
     * Export Syslog Event data to CEF syslog event table.
     */
    private boolean exportCEFSyslogEvent(Connection conn, String id, boolean includeRiskScores, Attributes<String, Object> args, String objName, Configuration config) throws GeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        SyslogEvent syslogEvent = context.getObjectById(SyslogEvent.class, id);
        if (syslogEvent == null)
            return false;

        Map<String, String> cefSyslogExtensionMap = null;
        if(config != null) {
            cefSyslogExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_SYSLOG_EXTENSION);
        }

        Class<SyslogEvent> clazz = SyslogEvent.class;
        if (log.isDebugEnabled())
            log.debug("CEFDataExport: Exporting syslog Event '"+syslogEvent.getName()+"'.");

        if (!fullExport) {
            delete(conn, clazz, id);
        }

        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "insert into " + brandingService.brandExportTableName(CEFDataExportSchemaUtil.CEF_SYSLOG_TBL) +
                " (created_dt, hostname, cef_version, device_vendor, device_product, device_version, signature_id, name, " +
                " severity, extension ) " +
                " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // Get CEF Header format
        Map<String, Object> CEFHeader = (Map<String, Object>) args.get("CEFHeader");
        if(CEFHeader == null || CEFHeader.isEmpty()) {
            CEFHeader = cefHeader;       
        }
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            Date date = new Date();
            stmt.setTimestamp(1, new Timestamp(date.getTime()));
            stmt.setString(2, Util.getHostName());
            stmt.setString(3, CEFHeader.get("cef_version").toString());
            stmt.setString(4, CEFHeader.get("device_vendor").toString());
            stmt.setString(5, CEFHeader.get("device_product").toString());
            stmt.setString(6, Version.getVersion());
            stmt.setString(7, syslogEvent.getId());
            stmt.setString(8, objName);

            String strSyslogSeverity = syslogEvent.getEventLevel();
            if(strSyslogSeverity == null || strSyslogSeverity.isEmpty())
                strSyslogSeverity = CEFHeader.get("severity").toString();
            else
                strSyslogSeverity = sysLogSeverity.get(strSyslogSeverity);

            stmt.setString(9, strSyslogSeverity);
            List <String> extensions = (List<String>) args.get("syslog_extension");
            if(extensions == null || extensions.isEmpty()) {
                extensions = syslog_extension;
            }
            Iterator<String> extnIterator = extensions.iterator();
            String extension = new String();
            while (extnIterator.hasNext()) {
                String value = "";
                String attributeValue = null;
                String attribute = extnIterator.next().toString();
                Object attributeObject = PropertyUtils.getProperty(syslogEvent, attribute);
                if (attributeObject != null) {
                    if (attributeObject instanceof String) {
                        attributeValue = (String) attributeObject;
                    }
                    else if(attributeObject instanceof Date) {
                        attributeValue = String.valueOf(attributeObject);
                    }
                }
                if (Util.isNotNullOrEmpty(attributeValue)) {
                    // generate CEF extension
                    value = getCEFExtension(attribute, attributeValue, cefSyslogExtensionMap, includeRiskScores);
                }
                if (value != null && !value.isEmpty()) {
                    extension += value;
                    if (extnIterator.hasNext()) {
                        extension += " ";
                    }
                }
            }

            if(extension != null && !extension.isEmpty()) {
                stmt.setString(10, extension);
            }

            if (log.isDebugEnabled()) {
                log.debug("CEFDataExport: extension:" + extension.toString());
                log.debug("CEFDataExport: query:" + stmt.toString());
            }

            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        return true;
    }

    /**
     * Export Audit Event data to CEF audit event table.
     */
    private boolean exportCEFAuditEvent(Connection conn, String id, boolean includeRiskScores, Attributes<String, Object> args, String objName, Configuration config) throws GeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        AuditEvent auditEvent = context.getObjectById(AuditEvent.class, id);
        if (auditEvent == null)
            return false;

        Map<String, String> cefAuditExtensionMap = null;
        if(config != null) {
            cefAuditExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_AUDIT_EXTENSION);
        }

        Class<AuditEvent> clazz = AuditEvent.class;
        if (log.isDebugEnabled())
            log.debug("CEFDataExport: Exporting audit event '"+auditEvent.getName()+"'.");

        if (!fullExport) {
            delete(conn, clazz, id);
        }

        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "insert into " + brandingService.brandExportTableName(CEFDataExportSchemaUtil.CEF_AUDIT_TBL) +
                " (created_dt, hostname, cef_version, device_vendor, device_product, device_version, signature_id, name, " +
                " severity, extension ) " +
                " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Map<String, Object> CEFHeader = (Map<String, Object>) args.get("CEFHeader");
        if(CEFHeader == null || CEFHeader.isEmpty()) {
            CEFHeader = cefHeader;
        }
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            Date date = new Date();
            stmt.setTimestamp(1, new Timestamp(date.getTime()));
            stmt.setString(2, Util.getHostName());
            stmt.setString(3, CEFHeader.get("cef_version").toString());
            stmt.setString(4, CEFHeader.get("device_vendor").toString());
            stmt.setString(5, CEFHeader.get("device_product").toString());
            stmt.setString(6, Version.getVersion());
            stmt.setString(7, auditEvent.getId());
            stmt.setString(8, objName);
            stmt.setString(9, CEFHeader.get("severity").toString());
            List <String> extensions = (List<String>) args.get("audit_extension");
            if(extensions == null || extensions.isEmpty()) {
                extensions = audit_extension;
            }

            Iterator<String> extnIterator = extensions.iterator();
            String extension = new String();
            while (extnIterator.hasNext()) {
                String value = "";
                String attributeValue = null;
                String attribute = extnIterator.next().toString();
                Object attributeObject = PropertyUtils.getProperty(auditEvent, attribute);
                if (attributeObject != null) {
                    if (attributeObject instanceof String) {
                        attributeValue = (String) attributeObject;
                    }
                    else if(attributeObject instanceof Date) {
                        attributeValue = String.valueOf(attributeObject);;
                    }
                }
                if (Util.isNotNullOrEmpty(attributeValue)) {
                 // generate CEF extension
                    value = getCEFExtension(attribute, attributeValue, cefAuditExtensionMap, includeRiskScores);
                }
                if (value != null && !value.isEmpty()) {
                    extension += value;
                    if (extnIterator.hasNext()) {
                        extension += " ";
                    }
                }
            }

            if(extension != null && !extension.isEmpty()) {
                stmt.setString(10, extension);
            }

            if (log.isDebugEnabled()) {
                log.debug("CEFDataExport: extension:" + extension.toString());
                log.debug("CEFDataExport: query:" + stmt.toString());
            }

            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        return true;
    }

    /**
     * Export Account data to CEF Account table.
     */
    private boolean exportCEFLink(Connection conn, String id, boolean includeRiskScores, Attributes<String, Object> args, String objName, Configuration config) throws GeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Link link = context.getObjectById(Link.class, id);
        if (link == null)
            return false;

        Map<String, String> cefLinkExtensionMap = null;
        if(config != null) {
           cefLinkExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_LINK_EXTENSION);
        }

        Class<Link> clazz = Link.class;
        if (log.isDebugEnabled())
            log.debug("CEFDataExport: Exporting link '"+link.getName()+"'.");

        if (!fullExport) {
            delete(conn, clazz, id);
        }

        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "insert into " + brandingService.brandExportTableName(CEFDataExportSchemaUtil.CEF_LINK_TBL) +
                " (created_dt, hostname, cef_version, device_vendor, device_product, device_version, signature_id, name, " +
                " severity, extension ) " +
                " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Map<String, Object> CEFHeader = (Map<String, Object>) args.get("CEFHeader");
        if(CEFHeader == null || CEFHeader.isEmpty()) {
            CEFHeader = cefHeader;       
        }
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            Date date = new Date();
            stmt.setTimestamp(1, new Timestamp(date.getTime()));
            stmt.setString(2, Util.getHostName());
            stmt.setString(3, CEFHeader.get("cef_version").toString());
            stmt.setString(4, CEFHeader.get("device_vendor").toString());
            stmt.setString(5, CEFHeader.get("device_product").toString());
            stmt.setString(6, Version.getVersion());
            stmt.setString(7, link.getId() + System.currentTimeMillis());
            stmt.setString(8, objName);
            stmt.setString(9, CEFHeader.get("severity").toString());
            List <String> extensions = (List<String>) args.get("link_extension");
            if(extensions == null || extensions.isEmpty()) {
                extensions = link_extension;
            }

            Iterator<String> extnIterator = extensions.iterator();
            String extension = new String();
            while (extnIterator.hasNext()) {
                String value = "";
                String attributeValue = null;
                String attribute = extnIterator.next().toString();

                // Get application name
                if(attribute.equals("application.name")) {
                    attributeValue = link.getApplicationName();
                }
                //Get identity name
                else if(attribute.equals("identity.name")) {
                    attributeValue = link.getIdentity().getName();
                }
                //Get owner name
                else if(attribute.equals("owner.name")) {
                    attributeValue = link.getOwner().getName();
                }
                //Get permission
                else if(attribute.equals("directPermissions")) {
                    attributeValue = String.valueOf(link.getPermissions());
                }
                // get all entitlements
                else if(attribute.equals("entitlements")) {
                    Attributes<String,Object> entitlementsAttribute = link.getEntitlementAttributes();
                    if (entitlementsAttribute != null && !entitlementsAttribute.isEmpty()) {
                        Iterator<String> keys = entitlementsAttribute.keySet().iterator();
                        while ( keys.hasNext() ) {
                            Object entitlementObject = entitlementsAttribute.get(keys.next());
                            if(entitlementObject != null) {
                                if (entitlementObject instanceof List) {
                                    List entitlementObjectList = (List) entitlementObject;
                                    attributeValue = Util.listToCsv(entitlementObjectList);
                                }else if (entitlementObject instanceof String) {
                                    attributeValue = entitlementObject.toString().trim();
                                }
                            }
                        }
                    }
                } else {
                    Object attributeObject = PropertyUtils.getProperty(link, attribute);
                    if (attributeObject != null) {
                        if (attributeObject instanceof String) {
                            attributeValue = (String) attributeObject;
                        }
                        else if(attributeObject instanceof Date) {
                            attributeValue = String.valueOf(attributeObject);;
                        }
                    }
                }
                if (Util.isNotNullOrEmpty(attributeValue)) {
                    // generate CEF extension
                    value = getCEFExtension(attribute, attributeValue, cefLinkExtensionMap, includeRiskScores);
                }

                if (value != null && !value.isEmpty()) {
                    extension += value;
                    if (extnIterator.hasNext()) {
                        extension += " ";
                    }
                }
            }
            if(extension != null && !extension.isEmpty()) {
                stmt.setString(10, extension);
            }

            if (log.isDebugEnabled()) {
                log.debug("CEFDataExport: extension:" + extension.toString());
                log.debug("CEFDataExport: query:" + stmt.toString());
            }            
            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        return true;    
    }

    /**
     * Export Identity to CEF Identity table.
     */
    private boolean exportCEFIdentity(Connection conn, String id,boolean includeRiskScores, Attributes<String, Object> args,String objName, Configuration config) throws GeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Identity identity = context.getObjectById(Identity.class, id);
        if (identity == null)
            return false;

        Map<String, String> cefIdentityExtensionMap = null;
        if(config != null) {
            cefIdentityExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_IDENTITY_EXTENSION);
        }

        Class<Identity> clazz = Identity.class;
        if (log.isDebugEnabled()) {
            log.debug("CEFDataExport: Exporting identity '"+ identity.getName() + "'.");
        }

        if (!fullExport) {
            delete(conn, clazz, id);
        }

        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "insert into "
                + brandingService
                        .brandExportTableName(CEFDataExportSchemaUtil.CEF_IDENTITY_TBL)
                + " (created_dt, hostname, cef_version, device_vendor, device_product, device_version, signature_id, name, "
                + " severity, extension ) "
                + " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Map<String, Object> CEFHeader = (Map<String, Object>) args.get("CEFHeader");
        if(CEFHeader == null || CEFHeader.isEmpty()) {
            CEFHeader = cefHeader;       
        }
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            Date date = new Date();
            stmt.setTimestamp(1, new Timestamp(date.getTime()));
            stmt.setString(2, Util.getHostName());
            stmt.setString(3, CEFHeader.get("cef_version").toString());
            stmt.setString(4, CEFHeader.get("device_vendor").toString());
            stmt.setString(5, CEFHeader.get("device_product").toString());
            stmt.setString(6, Version.getVersion());
            stmt.setString(7, identity.getId());
            stmt.setString(8, objName);
            stmt.setString(9, CEFHeader.get("severity").toString());
            List <String> extensions = (List<String>) args.get("identity_extension");
            if(extensions == null || extensions.isEmpty()) {
                extensions = identity_extension;
            }

            Iterator<String> extnIterator = extensions.iterator();
            String extension = new String();
            while (extnIterator.hasNext()) {
                String value = null;
                String attributeValue = null;
                String attribute = extnIterator.next().toString();

                // Get all application names for an identity
                if (attribute.equals(CALCULATED_COLUMN_PREFIX+ ATT_IDENT_SEARCH_COL_APPLICATION_ID)) {
                    List<Link> linkAttribute= identity.getLinks();
                    if (linkAttribute != null && !linkAttribute.isEmpty()) {
                        Iterator<Link> itr = linkAttribute.iterator();
                        String appString = "";
                        while(itr.hasNext()) {
                            String applicationName = itr.next().getApplicationName();
                            if(applicationName != null && !applicationName.isEmpty()) {
                                appString += applicationName;
                                if(itr.hasNext())
                                    appString += ", ";
                            }
                        }
                        if (appString != null && !appString.isEmpty() && !appString.equalsIgnoreCase("null")) {
                            attributeValue = appString;
                        }
                    }
                } 
                // Get manager display name
                else if(attribute.equals("manager")) {
                    attributeValue = identity.getManager().getDisplayName();
                }
                // Get workgroups
                else if (attribute.equals(CALCULATED_COLUMN_PREFIX+ ATT_IDENT_SEARCH_COL_WORKGROUP_ID)) { 
                    List<Identity> workgroupAttribute= identity.getWorkgroups();
                    if (workgroupAttribute != null && !workgroupAttribute.isEmpty()) {
                        Iterator<Identity> itr = workgroupAttribute.iterator();
                        String workgroupString = "";
                        while(itr.hasNext()) {
                            String displayName = itr.next().getDisplayableName();
                            if(displayName != null && !displayName.isEmpty()) {
                                workgroupString += displayName;
                                if(itr.hasNext())
                                    workgroupString += ", ";
                            }
                        }
                        if (workgroupString != null && !workgroupString.isEmpty() && !workgroupString.equalsIgnoreCase("null")) {
                            attributeValue = workgroupString;
                        }
                    }
                }
                else if(attribute.equals("roles")) {
                    List<Bundle> bundleListAttribute= identity.getDetectedRoles();
                    if (bundleListAttribute != null && !bundleListAttribute.isEmpty()) {
                        Iterator<Bundle> itr = bundleListAttribute.iterator();
                        String bundleString = "";
                        while(itr.hasNext()) {
                            String bundleName = itr.next().getName();
                            if(bundleName != null && !bundleName.isEmpty()) {
                                bundleString += bundleName;
                                if(itr.hasNext())
                                    bundleString += ", ";
                            }
                        }
                        if (bundleString != null && !bundleString.isEmpty() && !bundleString.equalsIgnoreCase("null")) {
                            attributeValue = bundleString;
                        }
                    }
                }
                else if(attribute.equals("roleAssignments")) { 
                    List<RoleAssignment> roleListAttribute= identity.getRoleAssignments();
                    if (roleListAttribute != null && !roleListAttribute.isEmpty()) {
                        Iterator<RoleAssignment> itr = roleListAttribute.iterator();
                        String roleString = "";
                        while(itr.hasNext()) {
                            String roleName = itr.next().getRoleName();
                            if(roleName != null && !roleName.isEmpty()) {
                                roleString += roleName;
                                if(itr.hasNext())
                                    roleString += ", ";
                            }
                        }
                        if (roleString != null && !roleString.isEmpty() && !roleString.equalsIgnoreCase("null")) {
                            attributeValue = roleString;
                        }
                    }
                }
                // Get Identity score
                else if(attribute.contains(ATT_IDENT_SEARCH_SCORECARD)) {
                    Scorecard scorecard = identity.getScorecard();
                    if(scorecard != null) {
                        String scoreAttribute = attribute.split("\\.")[1];
                        if(scoreAttribute != null) {
                            Object attributeObject = PropertyUtils.getProperty(scorecard, scoreAttribute);
                            if (attributeObject != null) {
                                if (attributeObject instanceof Integer) {
                                    attributeValue = String.valueOf(scorecard.getCompositeScore());
                                }
                            }
                        }
                    }
                } else {
                    Object attributeObject = PropertyUtils.getProperty(identity, attribute);
                    if (attributeObject != null) {
                        if (attributeObject instanceof String) {
                            attributeValue = (String) attributeObject;
                        }
                        else if(attributeObject instanceof Date) {
                            attributeValue = String.valueOf(attributeObject);;
                        }
                    }
                }
                if (Util.isNotNullOrEmpty(attributeValue)) {
                    // generate CEF extension
                    value = getCEFExtension(attribute, attributeValue, cefIdentityExtensionMap, includeRiskScores);
                }
                if (value != null && !value.isEmpty()) {
                    extension += value;
                    if (extnIterator.hasNext()) {
                        extension += " ";
                    }
                }
            }
            if(extension != null && !extension.isEmpty()) {
                stmt.setString(10, extension);
            }

            if (log.isDebugEnabled()) {
                log.debug("CEFDataExport: extension:" + extension.toString());
                log.debug("CEFDataExport: query:" + stmt.toString());
            }

            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        return true;
    }

    /** 
     * This function form the cef extenison by checking if any CEF mapping has been provided for the field
     * and also formatting field value before putting it into the extension.
     * Output example:
     * duser=admin OR cs1=admin cs1Label=name
     * 
     */
    public String getCEFExtension(String attribute, String attributeValue, Map<String, String> cefExtensionMap, boolean includeRiskScoresIdentity) {
        String value = null;
        boolean cefMapping = false;
        if(cefExtensionMap != null && !cefExtensionMap.isEmpty()) {
            String cefKey = attribute;
            String cefVal = cefExtensionMap.get(cefKey);
            if(cefVal != null && !cefVal.isEmpty()) {
                cefMapping = true;
                // formatting field value as per ArcSight CEF conventions
                attributeValue = CEFDataExportSchemaUtil.formatCEFExtension(attributeValue);
                // Check if CEF map contains any field having integer in field name
                // So that we can add label
                if (!cefVal.matches(".*\\d.*")) {
                    value = cefVal + "=" + attributeValue;
                } else {
                    // Check for any risk score field in case of identity export
                    if (!cefVal.contains(ATT_IDENT_SEARCH_SCORECARD)) {
                        value = cefVal + "=" + attributeValue+ " " + cefVal + "Label="+ attribute;
                    } else {
                        // Check risk score in case of identity export
                        if (includeRiskScoresIdentity) {
                            value = cefVal + "=" + attributeValue+ " " + cefVal + "Label="+ attribute;
                        }
                    }
                }
            }
        }

        // Putting field value in CEF extension in case no CEF mapping is provided
        if (!cefMapping) {
            value = attribute+ "=" + CEFDataExportSchemaUtil.formatCEFExtension(attributeValue);
        }

        return value;
    }

    public int deleteAllCEF(Connection conn, Class<? extends SailPointObject> clazz) throws GeneralException {
        int cnt = JdbcUtil.update(conn, "delete from " + schemaUtil.getCEFExportTable(clazz), null);
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
        int cnt = JdbcUtil.update(conn, "delete from " + schemaUtil.getCEFExportTable(clazz) + " where signature_id=?", id);
        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }
        return cnt;
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

        Set<String> signature_ids = new HashSet<String>();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("select signature_id from "+schemaUtil.getCEFExportTable(objectClass));
            ResultSet rs = stmt.executeQuery();
            while(rs.next()){
                signature_ids.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }

        for(String signature_id : signature_ids){
            int cnt = context.countObjects(objectClass, new QueryOptions(Filter.eq("id", signature_id)));
            if (cnt == 0){
                if (log.isDebugEnabled())
                    log.debug("CEFDataExport: Pruning object id="+ signature_id +" of " + objectClass.toString());
                delete(conn, objectClass, signature_id);
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