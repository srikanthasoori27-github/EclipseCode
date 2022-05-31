/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.dialect.MySQL5InnoDBDialect;
import org.hibernate.dialect.Oracle10gDialect;

import sailpoint.api.SailPointContext;
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
import sailpoint.persistence.DB2Dialect;
import sailpoint.persistence.SQLServerUnicodeDialect;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Exports denormalized view of certain IIQ objects to an external datasource.
 *
 */
public class arcsightDataExporter {

    private static Log log = LogFactory.getLog(arcsightDataExporter.class);

    // ////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    // ////////////////////////////////////////////////////////////////////
    // Max number of errors we will accumulate on this object. It's possible
    // this task could generate 100000s of errors. Which might be bad...
    private static int MAX_ERR_CNT = 20;
    public static Map<String, Object> arcsightHeader = new HashMap<String, Object>();

    private String DEFAULT_ATTRIBUTES = "linkid, identityid, modified_dt, identity_display_name, identity_firstname, identity_lastname, "
                            + " application_type, application_host, application_name, link_display_name, entitlements, risk_score";
    private String DEFAULT_PLACEHOLDER = " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ";
    private arcsightDataExportSchemaUtil schemaUtil;
    private SailPointContext context;    
    private boolean fullExport;
    private String BLANK = "";
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

    public arcsightDataExporter(SailPointContext context, DatabaseType type) throws GeneralException {
        schemaUtil = new arcsightDataExportSchemaUtil(type);
        this.context = context;
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
        String sql = "select end_dt from "+ BrandingServiceFactory.getService().brandExportTableName(arcsightDataExportSchemaUtil.ARCSIGHT_EXP_TBL) +
                " where task_def_id='"+taskDefId+"' and class_name=?";
        String dt = JdbcUtil.queryString(conn, sql, clazz.getName());
        if (dt != null)
            return new Date(Util.atol(dt));
        return null;
    }

    public int exportarcsight(Connection conn, Class<? extends SailPointObject> clazz, String id, Identity identity,
            boolean fullExport, Attributes<String,Object> args) {

        this.fullExport = fullExport;
        int noOfRecExported = 0;
        Configuration config = Configuration.getSystemConfig();
        try {
            if (clazz.equals(Identity.class) || clazz.equals(Link.class)) {
                noOfRecExported = exportarcsightIdentity(conn, clazz, id, identity, args);
            }
            else{
                if(clazz.equals(AuditEvent.class)){
                    noOfRecExported = exportarcsightAuditEvent(conn, id, args, config);
                }
            }
        } catch (Throwable e) {
            log.error("exportarcsight: Error exporting object id="+ id +" " + clazz.getName() , e);
            Object exceptionMsg = null;
            if (e instanceof GeneralException) {
                exceptionMsg = ((GeneralException)e).getMessageInstance();
            } else {
                exceptionMsg = e.getMessage() != null ? e.getMessage() : "";
            }
            addMessage(Message.warn(MessageKeys.TASK_DATA_EXPORT_ERROR, clazz.getSimpleName(), id, exceptionMsg),clazz.getSimpleName());
        }
        return noOfRecExported;
    }

    public void completearcsightExport(Connection conn, String taskDefId, Class<? extends SailPointObject> exportClass, Date start)
    throws GeneralException {
        BrandingService brandingService = BrandingServiceFactory.getService();
        String sql = "delete from "+ brandingService.brandExportTableName(arcsightDataExportSchemaUtil.ARCSIGHT_EXP_TBL)+" where task_def_id='"+taskDefId+"' and " + " class_name=?";
        if (log.isDebugEnabled())
            log.debug("completearcsightExport: Delete from " + arcsightDataExportSchemaUtil.ARCSIGHT_EXP_TBL + " : " +sql.toString());
        JdbcUtil.update(conn, sql, exportClass.getName());

        String insertSql = "insert into " + brandingService.brandExportTableName(arcsightDataExportSchemaUtil.ARCSIGHT_EXP_TBL) + " (task_def_id, class_name, start_dt, end_dt) values(?,?,?,?)";

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, taskDefId);
            stmt.setString(2, exportClass.getName());
            stmt.setLong(3, start.getTime());
            stmt.setLong(4, new Date().getTime());

            if (log.isDebugEnabled())
                log.debug("completearcsightExport: Insert to " + arcsightDataExportSchemaUtil.ARCSIGHT_EXP_TBL + " : " + insertSql.toString());

            stmt.execute();
            conn.commit();
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
    }
    /**
     * Export Identity to arcsight Identity table.
     */
    private int exportarcsightIdentity(Connection conn, Class<? extends SailPointObject> clazz , String id, Identity identity, Attributes<String, Object> args) 
            throws GeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Link link = null;
        int noOfRecExported=0;
        if(clazz.equals(Identity.class)){
            identity = context.getObjectById(Identity.class, id);
        }
        else if (clazz.equals(Link.class)){
            link = context.getObjectById(Link.class, id);
        }
        if (identity == null && link == null)
            return noOfRecExported;

        if (log.isDebugEnabled()) {
            log.debug("exportarcsightIdentity: Exporting " + clazz.toString() + identity.getName() + ".");
        }

        Map<String, Object> arcsightAppNameHostMap = (Map<String, Object>) args.get("arcsightAppNameHostMap");
        boolean isAppNameHostMapDefined = false;
        if(arcsightAppNameHostMap != null && !arcsightAppNameHostMap.isEmpty()) {
            isAppNameHostMapDefined = true;
        }

        Map<String,String> arcsightFieldsMap = (Map<String,String>) args.get("arcsightFieldsMap");

        PreparedStatement stmt = null;
        List<Link> linkAttribute = null;

        if(clazz.equals(Identity.class))
            linkAttribute = identity.getLinks();
        else if(clazz.equals(Link.class)){
            linkAttribute = new ArrayList<Link>();
            linkAttribute.add(link);      
        }
        
        if (linkAttribute != null && !linkAttribute.isEmpty()) {
            Iterator<Link> itr = linkAttribute.iterator();
            while(itr.hasNext()) {

                link = itr.next();
                /*
                 * arcsightFieldsMap defines extra fields to be added in export query.
                 * The format should be TableName:field in key and field name from export table.
                 * Ex. <key=Identity:email value=emailId>
                 */
                String userAttributes="", userAttributeValue="";
                StringBuilder userAttributeValueForComparison= new StringBuilder(1024);
                if(arcsightFieldsMap != null && !arcsightFieldsMap.isEmpty()){
                    Set<Entry<String, String>> s = arcsightFieldsMap.entrySet();
                    Iterator<Entry<String,String>> iter = s.iterator();
                    while(iter.hasNext()){
                        Entry<String,String> mapEntry = iter.next();
                        String classFieldName, exportFieldName, attributeValue="" ;
                        String [] classAttribute;
                        classFieldName = mapEntry.getKey();
                        exportFieldName = mapEntry.getValue();

                        classAttribute = classFieldName.split(":");
                        userAttributes = userAttributes.concat("," + exportFieldName );
                        Object attributeObject = null;
                        if(classAttribute[0].equalsIgnoreCase("Identity")){
                            if(classAttribute[1].equalsIgnoreCase("manager")){
                                if(identity.getManager()!=null){
                                    attributeObject=identity.getManager().getDisplayableName();
                                }
                            }
                            else if(classAttribute[0].equalsIgnoreCase("owner")){
                                if(identity.getOwner()!= null){
                                    attributeObject=identity.getOwner().getDisplayableName();
                                }
                            }
                            else if (classAttribute[1].equalsIgnoreCase("workgroups")) {
                                List<Identity> workgroupAttribute= identity.getWorkgroups();
                                if (workgroupAttribute != null && !workgroupAttribute.isEmpty()) {
                                    Iterator<Identity> itrtr = workgroupAttribute.iterator();
                                    String workgroupString = "";
                                    while(itrtr.hasNext()) {
                                        String displayName = itrtr.next().getDisplayableName();
                                        if(displayName != null && !displayName.isEmpty()) {
                                            workgroupString += displayName;
                                            if(itrtr.hasNext())
                                                workgroupString += ",";
                                        }
                                    }
                                    if (workgroupString != null && !workgroupString.isEmpty() && !workgroupString.equalsIgnoreCase("null")) {
                                        attributeObject = workgroupString;
                                    }
                                }
                            }
                            else if(classAttribute[0].equalsIgnoreCase("roles")) {
                                List<Bundle> bundleListAttribute= identity.getDetectedRoles();
                                if (bundleListAttribute != null && !bundleListAttribute.isEmpty()) {
                                    Iterator<Bundle> itrtr = bundleListAttribute.iterator();
                                    String bundleString = "";
                                    while(itrtr.hasNext()) {
                                        String bundleName = itrtr.next().getName();
                                        if(bundleName != null && !bundleName.isEmpty()) {
                                            bundleString += bundleName;
                                            if(itrtr.hasNext())
                                                bundleString += ",";
                                        }
                                    }
                                    if (bundleString != null && !bundleString.isEmpty() && !bundleString.equalsIgnoreCase("null")) {
                                        attributeObject = bundleString;
                                    }
                                }
                            }
                            else if(classAttribute[0].equalsIgnoreCase("roleAssignments")) { 
                                List<RoleAssignment> roleListAttribute= identity.getRoleAssignments();
                                if (roleListAttribute != null && !roleListAttribute.isEmpty()) {
                                    Iterator<RoleAssignment> itrtr = roleListAttribute.iterator();
                                    String roleString = "";
                                    while(itrtr.hasNext()) {
                                        String roleName = itrtr.next().getRoleName();
                                        if(roleName != null && !roleName.isEmpty()) {
                                            roleString += roleName;
                                            if(itrtr.hasNext())
                                                roleString += ",";
                                        }
                                    }
                                    if (roleString != null && !roleString.isEmpty() && !roleString.equalsIgnoreCase("null")) {
                                        attributeObject = roleString;
                                    }
                                }
                            }
                            else
                                attributeObject = PropertyUtils.getProperty(identity, classAttribute[1]);
                        }
                        else{
                            if(classAttribute[0].equalsIgnoreCase("Link")){
                                attributeObject = PropertyUtils.getProperty(link, classAttribute[1]);
                            }
                            else{
                                log.info("exportarcsightIdentity: The property " + classAttribute[1] + " for " + classAttribute[1] + " not found.");
                            }
                        }
                        String attributeVal=null;
                        if (attributeObject != null) {
                            if (attributeObject instanceof String) {
                                attributeValue = (String) attributeObject;
                            }
                            else {
                                attributeValue = String.valueOf(attributeObject);;
                            }
                            attributeVal=attributeValue;
                            attributeValue="'"+attributeValue+"'";
                        }
                        else{
                            attributeValue="null";
                        }
                        userAttributeValue = userAttributeValue.concat(", " + attributeValue);
                        userAttributeValueForComparison.append((attributeVal==null) ? "null" : attributeVal).append(";");
                    }
                }

                BrandingService brandingService = BrandingServiceFactory.getService();
                ResultSet rs=null;
                boolean shouldExport=false, recExistsExpTbl=true;
                String linkId, identityId, identityDispName, firstName, lastName, appName, appType, linkDispName, compositeScore;
                String appHost=null, entitlements=null;
                
                linkId= link.getId();
                identityId = identity.getId();
                identityDispName=identity.getDisplayName();
                firstName=identity.getFirstname();
                lastName=identity.getLastname();
                appType=link.getApplication().getType();
                if(isAppNameHostMapDefined){
                    appHost = (String) arcsightAppNameHostMap.get(link.getApplicationName());
                }
                appName=link.getApplicationName();
                linkDispName = link.getDisplayName();
                Attributes<String,Object> entitlementsAttribute = link.getEntitlementAttributes();
                if (entitlementsAttribute != null && !entitlementsAttribute.isEmpty()) {
                    Iterator<String> keys = entitlementsAttribute.keySet().iterator();
                    while ( keys.hasNext() ) {
                        Object entitlementObject = entitlementsAttribute.get(keys.next());
                        if(entitlementObject != null) {
                            if (entitlementObject instanceof List) {
                                List entitlementObjectList = (List) entitlementObject;
                                Collections.sort(entitlementObjectList, String.CASE_INSENSITIVE_ORDER);
                                entitlements = Util.listToCsv(entitlementObjectList);
                            }else if (entitlementObject instanceof String) {
                                entitlements = entitlementObject.toString().trim();
                            }
                        }
                    }
                }
                compositeScore= String.valueOf(identity.getScore());

                if(!fullExport){
                    try{
                        String selectsql = "SELECT " + DEFAULT_ATTRIBUTES + (Util.isNotNullOrEmpty(userAttributes) ? userAttributes : BLANK) 
                                + " FROM " + brandingService.brandExportTableName(arcsightDataExportSchemaUtil.ARCSIGHT_IDENTITY_TBL)
                                + " WHERE linkid = ?";
                        if(log.isDebugEnabled())
                            log.debug("exportarcsightIdentity: Select query to read export table - " + selectsql);
                        stmt = conn.prepareStatement(selectsql);
                        stmt.setString(1, link.getId());
                        rs = stmt.executeQuery();
                        int numOfCols = rs.getMetaData().getColumnCount();
                        StringBuilder exportedData= new StringBuilder(1024);
                        /*
                         * If the record does not exists in export table this needs to be exported.
                         * Set should export to true.
                         */
                        if(rs.next()){
                            for (int i=1; i<numOfCols+1; i++){
                                exportedData.append(rs.getString(i)).append(";");
                            }
                            if(log.isDebugEnabled())
                                log.debug("exportarcsightIdentity: Data read from export table for link - " + link.getId() + " : " + exportedData);
                        }
                        else{
                            shouldExport=true;
                            recExistsExpTbl=false;
                        }
                        StringBuilder newData= new StringBuilder(1024);
                        /*
                         * If it is decided that the data should be exported there is no need to 
                         * compare it with existing record
                         */
                        if(!shouldExport){
                            newData.append((linkId==null)? "null": linkId).append(";");
                            newData.append((identityId==null) ? "null" : identityId).append(";");
                            newData.append((identityDispName==null) ? "null" :identityDispName).append(";");
                            newData.append((firstName==null) ? "null" : firstName).append(";");
                            newData.append((lastName==null) ? "null" : lastName).append(";");
                            newData.append((appType==null) ? "null" : appType).append(";");
                            newData.append((appHost==null) ? "null" : appHost).append(";");
                            newData.append((appName==null) ? "null" : appName).append(";");
                            newData.append((linkDispName==null) ? "null" : linkDispName).append(";");
                            newData.append((entitlements==null) ? "null" :entitlements).append(";");
                            newData.append((compositeScore==null) ? "null" : compositeScore).append(";");
                            if(userAttributeValueForComparison.length()>0)
                                newData.append(userAttributeValueForComparison);
                            if(log.isDebugEnabled())
                                log.debug("exportarcsightIdentity: newData - " + newData);
                        }
                        //Compare only if it is not yet decided whether the data should be exported or not
                        if(!shouldExport && !(newData.toString().equals(exportedData.toString()))){
                            shouldExport = true;
                        }
                    } catch (SQLException e) {
                        log.error("exportarcsightIdentity: Error while exporting data." + e.getMessage());
                        throw new GeneralException(e);
                    }
                    finally{
                        JdbcUtil.closeStatement(stmt);
                    }
                }
                else
                    shouldExport=true;
                
                if(shouldExport){
                    try {
                        if (!fullExport && recExistsExpTbl) {
                            delete(conn, clazz, link.getId());
                        }
                        String insertSql = "INSERT INTO "
                                + brandingService.brandExportTableName(arcsightDataExportSchemaUtil.ARCSIGHT_IDENTITY_TBL)
                                + "(" + DEFAULT_ATTRIBUTES 
                                + (Util.isNotNullOrEmpty(userAttributes) ? userAttributes : BLANK) 
                                + " ) values(" + DEFAULT_PLACEHOLDER 
                                + (Util.isNotNullOrEmpty(userAttributeValue) ? userAttributeValue : BLANK)
                                + ")";

                        stmt = conn.prepareStatement(insertSql);

                        stmt.setString(1, linkId);
                        stmt.setString(2, identityId);
                        stmt.setLong(3, new Date().getTime());
                        stmt.setString(4, identityDispName);
                        stmt.setString(5, firstName);
                        stmt.setString(6, lastName);
                        stmt.setString(7, appType);

                        stmt.setString(8, appHost);
                        stmt.setString (9, appName);
                        stmt.setString(10, linkDispName);
                        stmt.setString(11, entitlements);
                        stmt.setString(12, compositeScore);
                        if (log.isDebugEnabled()) {
                            log.debug("exportarcsightIdentity: Update query - " + stmt.toString());
                        }
                        stmt.execute();
                        conn.commit();
                        noOfRecExported++;
                    } catch (SQLException e) {
                        log.error("exportarcsightIdentity: Error while exporting data." + e.getMessage());
                        throw new GeneralException(e);
                    }
                    finally{
                        JdbcUtil.closeStatement(stmt);
                    }
                }
                else
                {
                    if(log.isDebugEnabled()){
                        log.debug("The exported data for link " + linkId + " is same");
                    }
                }
            }
        }
        return noOfRecExported;
    }

    public int deleteAllarcsight(Connection conn, Class<? extends SailPointObject> clazz) throws GeneralException {
        int cnt = JdbcUtil.update(conn, "DELETE FROM " + schemaUtil.getarcsightExportTable(clazz), null);
        try{
            conn.commit();
        } catch(SQLException e){
            throw new GeneralException(e);
        }
        return cnt;
    }

    
    /**
     * Export Audit Event data to CEF audit event table.
     */
    private int exportarcsightAuditEvent(Connection conn, String auditId, Attributes<String, Object> args, Configuration config) throws GeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        AuditEvent auditEvent = context.getObjectById(AuditEvent.class, auditId);
        int noOfRecExported=0;
        if (auditEvent == null)
            return noOfRecExported;

        BrandingService brandingService = BrandingServiceFactory.getService();
        String insertSql = "INSERT INTO " + brandingService.brandExportTableName(arcsightDataExportSchemaUtil.ARCSIGHT_AUDIT_TBL) +
                " (auditid, created_dt, owner, source, action, target, application, account_name, attribute_name, attribute_value) " +
                " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, auditId);
            String auditOwner=null;
            if(auditEvent.getOwner()!=null)
                auditOwner=auditEvent.getOwner().getDisplayName();

            stmt.setLong(2, new Date().getTime());
            stmt.setString(3, auditOwner);
            stmt.setString(4, auditEvent.getSource());
            stmt.setString(5, auditEvent.getAction());
            stmt.setString(6, auditEvent.getTarget());
            stmt.setString(7, auditEvent.getApplication());
            stmt.setString(8, auditEvent.getAccountName());
            stmt.setString(9, auditEvent.getAttributeName());
            stmt.setString(10, auditEvent.getAttributeValue());
            
            if(log.isDebugEnabled()){
                log.debug("exportarcsightAuditEvent: query - " + stmt.toString());
            }
            
            stmt.execute();
            conn.commit();
            noOfRecExported++;
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            JdbcUtil.closeStatement(stmt);
        }
        return noOfRecExported;
    }

    
    
    /**
     * Delete a given object and all it's dependants
     * @param conn
     * @param clazz
     * @param id
     * @throws GeneralException
     */
    public int delete(Connection conn, Class<? extends SailPointObject> clazz, String id) throws GeneralException {
        int cnt = JdbcUtil.update(conn, "delete from " + schemaUtil.getarcsightExportTable(clazz) + " where linkid=?", id);
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
    public int pruneDeletedObjects(Connection conn, Class<? extends SailPointObject> objectClass, Attributes<String,Object> args) throws GeneralException{
        int recordsPruned = 0;
        Set<String> ids = new HashSet<String>();
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("select " +  schemaUtil.getarcsightExportField(objectClass) + " from " + schemaUtil.getarcsightExportTable(objectClass));
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
                    log.debug("pruneDeletedObjects: Pruning object id="+ id +" of " + objectClass.toString());
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