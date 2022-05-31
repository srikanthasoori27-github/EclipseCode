/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;

import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.persistence.HibernateConfigUtil;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class arcsightDataExportSchemaUtil {

    private static Log log = LogFactory.getLog(arcsightDataExportSchemaUtil.class);

    private static String ARCSIGHT_TEMPLATE_FILE = "WEB-INF/database/arcsightDataExport/templates/create_arcsight_data_export_tables" ;

    private static String CREATE_ARCSIGHT_DATA_EXPORT_FILENAME = "create_arcsight_data_export_tables";
    private static String TEMPLATE_FILE_LOC = "WEB-INF/database/arcsightDataExport/templates/";
    private static String TEMPLATE_RESOURCE_LOC = "sailpoint/reporting/custom/arcsightDataExportTemplates/";
    
    private static String DDL_TEMPLATE_FILE = TEMPLATE_FILE_LOC + CREATE_ARCSIGHT_DATA_EXPORT_FILENAME;
    private static String DDL_TEMPLATE_RESOURCE = TEMPLATE_RESOURCE_LOC + CREATE_ARCSIGHT_DATA_EXPORT_FILENAME;

    public static final String ARCSIGHT_IDENTITY_TBL = "sptr_arcsight_identity";
    public static final String ARCSIGHT_AUDIT_TBL = "sptr_arcsight_audit_event";
    public static final String ARCSIGHT_EXP_TBL = "sptr_arcsight_export";
    public static final String ARCSIGHT_EXP_TEMP_TBL = "sptr_arcsight_temp";
    public static final String ARCSIGHT_EXP_TEMP_TBL_IDENTITYID = "identityid";

    public static final String ARCSIGHT_IDENTITY_ID = "identityid";
    public static final String ARCSIGHT_LINK_ID = "linkid";
    public static final String ARCSIGHT_AUDIT_ID = "id";
    
    private static final String IDENTITY_ATTR_COL_LEN_TOKEN = "@IdentityAttrColLength";
    private static final String LINK_ATTR_COL_LEN_TOKEN = "@LinkAttrColLength";

    private static final int DEFAULT_ATTR_COL_LEN = 450;

    private arcsightDataExporter.DatabaseType type;
    private Dialect dialect;
    private HibernateConfigUtil confUtil;
    private int maxIdentityAttrLength;
    private int maxLinkAttrLength;
    private Map<Class, Integer> maxAttrLength;
        
    private final BrandingService brandingService = BrandingServiceFactory.getService();


    public arcsightDataExportSchemaUtil(arcsightDataExporter.DatabaseType type) throws GeneralException {
        this.type = type;
        confUtil = new HibernateConfigUtil();
        maxAttrLength = new HashMap<Class, Integer>();        
        try {
            dialect = (Dialect) type.getHibernateDialect().newInstance();
            maxIdentityAttrLength = getMaxExtendedColumnLength(Identity.class);
            maxLinkAttrLength = getMaxExtendedColumnLength(Link.class);
        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }
    

    /**
     * get the name of export table.
     * @param clazz
     * @return
     */
    public String getarcsightExportTable(Class clazz) {
        String tableName = null;
        if (Identity.class == clazz)
            tableName = ARCSIGHT_IDENTITY_TBL;
        else if (Link.class == clazz)
            tableName = ARCSIGHT_IDENTITY_TBL;
        else if(AuditEvent.class == clazz)
            tableName = ARCSIGHT_AUDIT_TBL;
        if(tableName != null) {
            return brandingService.brandExportTableName(tableName);
        }
        throw new IllegalArgumentException("Unhandled class " + clazz.getName());
    }
    
    public String getarcsightExportField(Class clazz){
        String fieldName = null;
        if (Identity.class == clazz)
            fieldName = ARCSIGHT_IDENTITY_ID;
        else if (Link.class == clazz)
            fieldName = ARCSIGHT_LINK_ID;
        else if(AuditEvent.class == clazz)
            fieldName = ARCSIGHT_AUDIT_ID;
        if(fieldName != null) {
            return fieldName;
        }
        throw new IllegalArgumentException("Unhandled class " + clazz.getName());
    }


    public String getarcsightSchemaSql(String appHome) throws GeneralException {

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

        ddl = ddl.replace(IDENTITY_ATTR_COL_LEN_TOKEN, String.valueOf(maxIdentityAttrLength));
        ddl = ddl.replace(LINK_ATTR_COL_LEN_TOKEN, String.valueOf(maxLinkAttrLength));

        return ddl;
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

}
