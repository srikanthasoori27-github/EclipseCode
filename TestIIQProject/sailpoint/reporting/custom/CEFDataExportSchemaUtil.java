/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

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
import sailpoint.object.SyslogEvent;
import sailpoint.persistence.HibernateConfigUtil;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class CEFDataExportSchemaUtil {

    private static Log log = LogFactory.getLog(CEFDataExportSchemaUtil.class);

    private static String CEF_TEMPLATE_FILE = "WEB-INF/database/cefDataExport/templates/create_cef_data_export_tables" ;

    public static final String CEF_IDENTITY_TBL = "sptr_cef_identity";
    public static final String CEF_LINK_TBL = "sptr_cef_link";
    public static final String CEF_AUDIT_TBL = "sptr_cef_audit_event";
    public static final String CEF_SYSLOG_TBL = "sptr_cef_syslog_event";
    public static final String CEF_EXP_TBL = "sptr_cef_export";

    private static final String IDENTITY_ATTR_COL_LEN_TOKEN = "@IdentityAttrColLength";
    private static final String LINK_ATTR_COL_LEN_TOKEN = "@LinkAttrColLength";

    private static final int DEFAULT_ATTR_COL_LEN = 450;

    private CEFDataExporter.DatabaseType type;
    private Dialect dialect;
    private HibernateConfigUtil confUtil;
    private int maxIdentityAttrLength;
    private int maxLinkAttrLength;
    private Map<Class, Integer> maxAttrLength;
        
    private final BrandingService brandingService = BrandingServiceFactory.getService();


    public CEFDataExportSchemaUtil(CEFDataExporter.DatabaseType type) throws GeneralException {
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
     * Add escape character if the extension contains \, \n or =.
     * @param value
     * @return
     */
    public static String formatCEFExtension(String value){
        String val = null;
        
        if(value != null){
            //escaping backslash(\) with a backslash(\)
            //backslash should be escaped first before handling any other character.
            val = value.replace("\\","\\\\");
            
            // encoding the newline character as \n. 
            val = val.replaceAll("[\\n\\r]"," \\\\n ");
            
            //escaping equal(=) with a backslash(\)
            val = val.replace("=","\\=");
        }

        return val;
    }

    /**
     * get the name of export table.
     * @param clazz
     * @return
     */
    public String getCEFExportTable(Class clazz) {
        String tableName = null;
        if (Identity.class == clazz)
            tableName = CEF_IDENTITY_TBL;
        else if (Link.class == clazz)
            tableName = CEF_LINK_TBL;
        else if(AuditEvent.class == clazz)
            tableName = CEF_AUDIT_TBL;
        else if (SyslogEvent.class == clazz)
            tableName = CEF_SYSLOG_TBL;
        if(tableName != null) {
            return brandingService.brandExportTableName(tableName);
        }
        throw new IllegalArgumentException("Unhandled class " + clazz.getName());
    }


    public String getCEFSchemaSql(String appHome) throws GeneralException {

        String file = CEF_TEMPLATE_FILE + "." + type.getTemplateFileExtension();
        if (appHome != null)
            file = appHome + "/" + file;
        String ddl = Util.readFile(file);

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
