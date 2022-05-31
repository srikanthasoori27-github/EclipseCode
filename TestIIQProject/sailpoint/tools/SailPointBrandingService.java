/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

final class SailPointBrandingService implements BrandingService {
    
    public String getPropertyFile() {
        return "iiq.properties";
    }

    public String getSchema() {
        return "identityiq";
    }

    public String brandTableName( String tableName ) {
        String tablePrefix = getTablePrefix();
        String brandedTableName = tableName.toLowerCase();
        if ( !brandedTableName.startsWith( tablePrefix ) ) {
            brandedTableName = tablePrefix + brandedTableName;
        }
        if ( Character.isUpperCase( tableName.charAt( 0 ) ) ) {
            brandedTableName = brandedTableName.toUpperCase();
        }
        return brandedTableName;
    }

    public String brandExportTableName( String tableName ) {
        String tablePrefix = "sptr_";
        String brandedTableName = tableName.toLowerCase();
        if ( !brandedTableName.startsWith( tablePrefix ) ) {
            brandedTableName = tablePrefix + brandedTableName;
        }
        if ( Character.isUpperCase( tableName.charAt( 0 ) ) ) {
            brandedTableName = brandedTableName.toUpperCase();
        }
        return brandedTableName;
    }

    public String getAdminUserName() {
        return "spadmin";
    }

    public String getConsoleApp() {
        return "iiq";
    }

    @Override
    public String getDbName() {
        return "iiq";
    }

    @Override
    public String getTablePrefix() {
        return "spt_";
    }

    public String getSpringConfig() {
        return "iiqBeans";
    }

    public String getXmlHeaderElement() {
        return "sailpoint";
    }

    public String getDtdFilename() {
        return "sailpoint.dtd";
    }

    public String brandFilename( String schemaFilename ) {
        return schemaFilename;
    }

    public String getCustomMessageBundle() {
        return "sailpoint.web.messages.iiqCustom";
    }

    public String getHelpMessageBundle() {
        return "sailpoint.web.messages.iiqHelp";
    }

    public String getMessageBundle() {
        return "sailpoint.web.messages.iiqMessages";
    }

    public String getConnectorMessageBundle() {
        return "connector.common.messages.conMessages";
    }

    public String getSelectorScopeMessageKey() {
        return "selector_scope_iiq";
    }

    public Brand getBrand() {
        return Brand.IIQ;
    }

    public String getApplicationShortName() {
        return "iiq";
    }

    public String getApplicationName() {
        return "IdentityIQ";
    }
}
