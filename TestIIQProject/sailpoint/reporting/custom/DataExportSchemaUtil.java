/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;

import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.object.Tag;
import sailpoint.persistence.HibernateConfigUtil;
import sailpoint.reporting.custom.DataExporter.DatabaseType;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;

/**
 * Methods for inspecting or building SQL for the export database.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class DataExportSchemaUtil {

    private static Log log = LogFactory.getLog(DataExportSchemaUtil.class);

    private static String CREATE_DATA_EXPORT_FILENAME = "create_data_export_tables";
    private static String CREATE_VIEWS_FILENAME = "create_views.sql";
    private static String TEMPLATE_FILE_LOC = "WEB-INF/database/dataExport/templates/";
    private static String TEMPLATE_RESOURCE_LOC = "sailpoint/reporting/custom/dataExportTemplates/";

    private static String DDL_TEMPLATE_FILE = TEMPLATE_FILE_LOC + CREATE_DATA_EXPORT_FILENAME ;
    private static String DDL_TEMPLATE_RESOURCE = TEMPLATE_RESOURCE_LOC + CREATE_DATA_EXPORT_FILENAME;

    public static String VIEW_TEMPLATE_FILE = TEMPLATE_FILE_LOC + CREATE_VIEWS_FILENAME ;
    private static String VIEW_TEMPLATE_RESOURCE = TEMPLATE_RESOURCE_LOC + CREATE_VIEWS_FILENAME ;

    public static final String IDENTITY_TBL = "sptr_identity";
    public static final String IDENTITY_SCORECARD_TBL = "sptr_identity_scorecard";
    public static final String IDENTITY_ATTR_TBL = "sptr_identity_attr";
    public static final String IDENTITY_ENTS = "sptr_identity_entitlements";
    public static final String LINKS_TBL = "sptr_account";
    public static final String LINKS_ATTR_TBL = "sptr_account_attr";
    public static final String CERT_TBL = "sptr_certification";
    public static final String CERT_ATTR_TBL = "sptr_certification_attr";
    public static final String CERT_ITEM_TBL = "sptr_cert_item";
    public static final String CERT_ITEM_ATTR_TBL = "sptr_cert_item_attr";
    public static final String CERT_ITEM_ENTS_TBL = "sptr_cert_item_entitlements";
    public static final String TAG_TBL = "sptr_tag";
    public static final String CERT_TAG_TBL = "sptr_certification_tag";

    public static final String EXP_TBL = "sptr_export";

    private static final int COL_NAME_MAX_LENGTH = 25;
    private static final String IDENTITY_ATTR_COL_LEN_TOKEN = "@IdentityAttrColLength";
    private static final String LINK_ATTR_COL_LEN_TOKEN = "@LinkAttrColLength";

    // View tokens
    private static final String TOKEN_IDENTITY_EXT_COLS = "@IdentityExtendedColumns";
    private static final String TOKEN_IDENTITY_EXT_VIEW_COLS = "@IdentityExtendedViewColumns";
    private static final String TOKEN_LINK_EXT_COLS = "@LinkExtendedColumns";
    private static final String TOKEN_LINK_EXT_VIEW_COLS = "@LinkExtendedViewColumns";
    private static final String TOKEN_ROLE_EXT_COLS = "@RoleExtendedColumns";
    private static final String TOKEN_ROLE_EXT_VIEW_COLS = "@RoleExtendedViewColumns";
    private static final String TOKEN_MG_ATTR_EXT_COLS = "@ManagedAttributeExtendedColumns";
    private static final String TOKEN_MG_ATTR_EXT_VIEW_COLS = "@ManagedAttributeExtendedViewColumns";
    private static final String TOKEN_APP_EXT_COLS = "@ApplicationExtendedColumns";
    private static final String TOKEN_APP_EXT_VIEW_COLS = "@ApplicationExtendedViewColumns";
    private static final String TOKEN_CERT_ITEM_EXT_COLS = "@CertItemExtendedColumns";
    private static final String TOKEN_CERT_ITEM_EXT_VIEW_COLS = "@CertItemExtendedViewColumns";
    private static final String TOKEN_STATEMENT_BREAK = "@BetweenStatements";
    
    private static final int DEFAULT_MAX_VALUE_LENGTH = 2000;

    private static final int DEFAULT_ATTR_COL_LEN = 450;

    private DataExporter.DatabaseType type;
    private Dialect dialect;
    private HibernateConfigUtil confUtil;
    private int maxIdentityAttrLength;
    private int maxLinkAttrLength;
    private Map<Class, Integer> maxAttrLength;
    private Map<String, Integer> maxValueLengthCache;
    
    /**
     * List of exportable attributes by class. This includes all
     * object attributes, minus any which do not have columns
     * in the export db.
     */
    private Map<Class, List<String>> extendedColumns;

    /**
     * The list of attributes names that are not exportable
     * due to the fact that columns are missing from the export database
     * tables. Most likely an attribute was added after the export schema
     * was generated.
     */
    private Map<Class, List<String>> missingExtendedColumns;
    
    private final BrandingService brandingService = BrandingServiceFactory.getService();

    public DataExportSchemaUtil(DataExporter.DatabaseType type) throws GeneralException {
        this.type = type;
        confUtil = new HibernateConfigUtil();
        maxAttrLength = new HashMap<Class, Integer>();
        maxValueLengthCache = new HashMap<String, Integer>();
        extendedColumns = new HashMap<Class, List<String>>();
        missingExtendedColumns = new HashMap<Class, List<String>>();
        try {
            dialect = (Dialect) type.getHibernateDialect().newInstance();
            maxIdentityAttrLength = getMaxExtendedColumnLength(Identity.class);
            maxLinkAttrLength = getMaxExtendedColumnLength(Link.class);
        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    public String getExportTable(Class clazz) {
        String tableName = null;
        if (Identity.class == clazz)
            tableName = IDENTITY_TBL;
        else if (Link.class == clazz)
            tableName = LINKS_TBL;
        else if (Certification.class == clazz)
            tableName = CERT_TBL;
        else if (CertificationItem.class == clazz)
            tableName = CERT_ITEM_TBL;
        else if (EntitlementSnapshot.class == clazz)
            tableName = CERT_ITEM_ENTS_TBL;
        else if (Tag.class == clazz)
            tableName = TAG_TBL;
        if(tableName != null) {
            return brandingService.brandExportTableName(tableName);
        }
        throw new IllegalArgumentException("Unhandled class " + clazz.getName());
    }

    public String getExportAttributesTable(Class clazz) {
        String tableName = null;
        if (Identity.class == clazz)
            tableName = IDENTITY_ATTR_TBL;
        else if (Link.class == clazz)
            tableName = LINKS_ATTR_TBL;
        else if (Certification.class == clazz)
            tableName = CERT_ATTR_TBL;
        else if (CertificationItem.class == clazz)
            tableName = CERT_ITEM_ATTR_TBL;
        if(tableName != null) {
            tableName = brandingService.brandExportTableName(tableName);
        }
        return tableName;
    }
    
    public String getExportLinkedTable(Class clazz) {
        if (Tag.class == clazz)
            return brandingService.brandExportTableName(CERT_TAG_TBL);
        else
            throw new IllegalArgumentException("Unhandled class " + clazz.getName());
    }

    /**
     * Return list of attribute names which are not able to be exported because
     * the column for the given attribute is not in the export database schema.
     * 
     * @param conn
     * @param clazz
     * @return
     * @throws GeneralException
     */
    public List<String> getMissingExtendedColumns(Connection conn, Class clazz) throws GeneralException{
        // this will init the missing columns list
        getExtendedColumns(conn, clazz);

        return this.missingExtendedColumns.get(clazz);
    }
    
    static boolean isExtendendedIdentityType(ObjectAttribute attribute, Class<? extends SailPointObject> clz) throws GeneralException {

        SailPointObject obj = null;
        try {
            obj = clz.newInstance();
        } catch (Exception ex) {
            throw new GeneralException(ex);
        }

        return obj.isExtendedIdentityType(attribute);
    }
    
    public List<String> getExtendedColumns(Connection conn, Class clazz) throws GeneralException {

        if (extendedColumns.containsKey(clazz))
            return extendedColumns.get(clazz);

        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
        if (conf == null || conf.getExtendedAttributeList() == null){
           extendedColumns.put(clazz, new ArrayList<String>());
           missingExtendedColumns.put(clazz, new ArrayList<String>());
           return new ArrayList<String>();
        }

        List<String> columns = new ArrayList<String>();
        List<String> missingColumns = new ArrayList<String>();

        String table = getExportTable(clazz);

        String testItemId = "testItem";
        JdbcUtil.update(conn, "insert into " + table + " (id) values(?)", testItemId);

        // create attribute map using attr names so we can perform
        // comparisons with database col names
        Map<String, ObjectAttribute> attrMap = new HashMap<String, ObjectAttribute>();
        for(String attr: conf.getExtendedAttributeMap().keySet()){
            ObjectAttribute attrObj = conf.getObjectAttribute(attr);
            String colName = calculateExportColumnName(attrObj).toLowerCase();
            attrMap.put(colName, attrObj);
            missingColumns.add(colName);
        }

        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement("select * from " + table + " where id=?");
            stmt.setString(1, testItemId);
            ResultSet rs = stmt.executeQuery();
            int colCnt = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= colCnt; i++) {
                String column = rs.getMetaData().getColumnName(i).toLowerCase();
                if (attrMap.containsKey(column)) {
                    columns.add(attrMap.get(column).getName());
                    missingColumns.remove(column);
                }
            }
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            try {
                JdbcUtil.update(conn, "delete from " + table + " where id = ?", testItemId);
            } catch (GeneralException e) {
                log.error("Could not clean up test insert into reporting database");
            }
            JdbcUtil.closeStatement(stmt);
        }

        extendedColumns.put(clazz, columns);
        missingExtendedColumns.put(clazz, missingColumns);

        return columns;
    }

    public String getSchemaSql(String appHome) throws GeneralException {

        String file = DDL_TEMPLATE_FILE + "." + type.getTemplateFileExtension();
        String ddl = null;
        if (appHome != null) {
            file = appHome + "/" + file;
        }
        try {
            ddl = Util.readFile(file);
        } catch (GeneralException ge) {
            if (ge.getCause() instanceof FileNotFoundException) {
                // FileNotFound - Before throwing up the Exception, we have an alternate approach...
                // We might be looking in a .WAR file or just can't access the file via new File(...). 
                // Try for a resource stream instead. Doing so means WEB-INF/database will need to 
                // be added to the classpath which means the resource path is now slightly different
                // (we'll have to drop 'WEB-INF/database' from the path)
                String resource = DDL_TEMPLATE_RESOURCE + "." + type.getTemplateFileExtension();
                ddl = Util.readResource(resource);
                if (ddl == null) {
                    // We got here because of a File Not Found. A null DDL means it's still
                    // not found. Let the original FileNotFoundException fly but wrap it in a new GE
                    // indicating the Resource could also not be found
                    throw new GeneralException(resource + " (File or Resource not found)", ge.getCause());
                }
            } else {
                // wasn't a FileNotFound. So just let it fly...
                throw ge;
            }
        }
        ddl += "\n";
        ddl += getExtendedColsDDL(Identity.class, brandingService.brandExportTableName(IDENTITY_TBL));
        ddl += getExtendedColsDDL(Link.class, brandingService.brandExportTableName(LINKS_TBL));

        ddl = ddl.replace(IDENTITY_ATTR_COL_LEN_TOKEN, String.valueOf(maxIdentityAttrLength));
        ddl = ddl.replace(LINK_ATTR_COL_LEN_TOKEN, String.valueOf(maxLinkAttrLength));

        return ddl;
    }

    public String getViewSql(String appHome, DatabaseType type) throws GeneralException {

        String file = VIEW_TEMPLATE_FILE;
        String ddl = null;
        if (appHome != null) {
            file = appHome + "/" + file;
        }

        try {
            ddl = Util.readFile(file);
        }catch (GeneralException ge) {
            if (ge.getCause() instanceof FileNotFoundException) {
                // Short version: try a resource. See also #getSchemaSql()
                String resource = VIEW_TEMPLATE_RESOURCE;
                ddl = Util.readResource(resource);
                if (ddl == null) {
                    // We got here because of a File Not Found. A null DDL means it's still
                    // not found. Let the original FileNotFoundException fly but wrap it in a new GE
                    // indicating the Resource could also not be found
                    throw new GeneralException(resource + " (File or Resource not found)", ge.getCause());
                }
            } else {
                // wasn't a FileNotFound. So just let it fly...
                throw ge;
            }
        }

        ddl += "\n";

        ddl = replaceExtendedAttrTokens(ddl, Identity.class, TOKEN_IDENTITY_EXT_VIEW_COLS, TOKEN_IDENTITY_EXT_COLS);
        ddl = replaceExtendedAttrTokens(ddl, Link.class, TOKEN_LINK_EXT_VIEW_COLS, TOKEN_LINK_EXT_COLS);
        ddl = replaceExtendedAttrTokens(ddl, ManagedAttribute.class, TOKEN_MG_ATTR_EXT_VIEW_COLS, TOKEN_MG_ATTR_EXT_COLS);
        ddl = replaceExtendedAttrTokens(ddl, Bundle.class, TOKEN_ROLE_EXT_VIEW_COLS, TOKEN_ROLE_EXT_COLS);
        ddl = replaceExtendedAttrTokens(ddl, Application.class, TOKEN_APP_EXT_VIEW_COLS, TOKEN_APP_EXT_COLS);
        ddl = replaceExtendedAttrTokens(ddl, Link.class, TOKEN_CERT_ITEM_EXT_VIEW_COLS, TOKEN_CERT_ITEM_EXT_COLS);
        String breakCommand = DataExporter.DatabaseType.SqlServer.equals(type)?"GO":"";
        ddl = ddl.replaceAll(TOKEN_STATEMENT_BREAK, breakCommand);

        return ddl;
    }

    private String replaceExtendedAttrTokens(String ddl, Class clazz, String viewToken, String selectToken) throws GeneralException{

        Map<String, String> extendedCols = getExtendedColumnsMapping(clazz);

        String selectCols = "";
        String viewCols = "";
        if (extendedCols != null){
            for(Map.Entry<String, String> entry : extendedCols.entrySet()){
                selectCols += ("," + entry.getKey());
                viewCols += ("," + entry.getValue());
            }
        }

        ddl = ddl.replaceAll(viewToken, viewCols);
        return ddl.replaceAll(selectToken, selectCols);
    }

    private Map<String, String> getExtendedColumnsMapping(Class clazz) throws GeneralException {
        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
        Map<String, String> cols = new HashMap<String, String>();
        if (conf != null && conf.getExtendedAttributeList() != null) {
            for (ObjectAttribute attr : conf.getExtendedAttributeList()) {
                cols.put(calculateIIQColumnName(attr), calculateExportColumnName(attr));
            }
        }

        return cols;
    }

    private String getExtendedColsDDL(Class clazz, String tableName) throws GeneralException {
        String extendedColumnDll = "";
        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
        if (conf.getExtendedAttributeList() != null) {
            String endCommand = DataExporter.DatabaseType.SqlServer.equals(type) ?
                    extendedColumnDll += "GO\n" : "";
            StringBuffer buf = new StringBuffer();
            for (ObjectAttribute attr : conf.getExtendedAttributeList()) {
                Column col = confUtil.getColumn(clazz.getName(), calculateIIQColumnName(attr));
                String exportColName = calculateExportColumnName(attr);
                buf.append(buildAddColumnSql(tableName, exportColName, col.getLength()));
                buf.append(endCommand);
                buf.append(buildIndexString(tableName, exportColName));
                buf.append(endCommand);
            }
            extendedColumnDll = buf.toString();
        }
        return extendedColumnDll;
    }


    private String buildAddColumnSql(String table, String attr, int size) {
        String colName = attr;
        if (colName.length() > COL_NAME_MAX_LENGTH)
            colName = colName.substring(0, COL_NAME_MAX_LENGTH);
        return "alter table " + brandingService.getSchema() + "." + table + " " + dialect.getAddColumnString() + " " +
                colName + " " + dialect.getTypeName(Types.VARCHAR, size, 0, 0) + ";\n";
    }

    private String buildIndexString(String table, String attr) {
        String idxSchema = DataExporter.DatabaseType.Oracle.equals(this.type) ||
                DataExporter.DatabaseType.DB2.equals(this.type)? brandingService.getSchema() + "." : "";
        String idxName = idxSchema + brandingService.brandExportTableName("sptr_idx_") + attr;
        return "create index " + idxName + "  on " + brandingService.getSchema() + "." + table + " (" + attr + ");\n";
    }

    public int getMaxExtendedColumnLength(Class clazz) throws GeneralException {

        if (maxAttrLength.containsKey(clazz))
            return maxAttrLength.get(clazz);

        int len = DEFAULT_ATTR_COL_LEN;
        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
        if (conf != null && conf.getExtendedAttributeList() != null) {
            for (ObjectAttribute attr : conf.getExtendedAttributeList()) {
                Column col = confUtil.getColumn(clazz.getName(), calculateIIQColumnName(attr));
                if (col != null && col.getLength() > len)
                    len = col.getLength();
            }
        }

        maxAttrLength.put(clazz, len);

        return len;
    }
    
    /**
     * Gets the maximum length of the "value" column for the specified table.
     * If the length is unable to be read from the table directly,
     * DEFAULT_MAX_VALUE_LENGTH is returned.
     * 
     * @param tableName the name of the table to check.
     * @return The maximum value length.
     */
    public int getMaxValueLength(Connection connection, String tableName)
    {
        if (maxValueLengthCache.containsKey(tableName)) {
            return maxValueLengthCache.get(tableName);
        }
        
        int maxValueLength = DEFAULT_MAX_VALUE_LENGTH;
        
        try {
            maxValueLength = JdbcUtil.getColumnLength(connection, tableName, "value");
        } catch (Exception ex) {
            log.warn("Unable to retrieve column length", ex);
        }        
        
        maxValueLengthCache.put(tableName, maxValueLength);
        
        return maxValueLength;
    }

    /**
     * Calcuate the IIQ schema column name for an extended attribute.
     */
    private String calculateIIQColumnName(ObjectAttribute attr){

        if (attr == null)
            return null;

        String name = null;
        if (attr.getExtendedNumber() > 0)
            name = "extended" + attr.getExtendedNumber();
        else
            name = org.hibernate.cfg.ImprovedNamingStrategy.INSTANCE.columnName(attr.getName());

        return name;
    }

    /**
     * Calculate the data export schema column name for an extended attribute.
     *
     * jfb: currently, this just returns the attr name. It would
     * be better to calculate the column name using the hibernate
     * naming strategy. However, that would affect backward compatibility,
     * so no dice for now.
     */
    private String calculateExportColumnName(ObjectAttribute attr){
        return attr != null ? attr.getName() : null;
    }

}
