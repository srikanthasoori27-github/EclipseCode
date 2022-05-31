package sailpoint.tools;

final class NetIqBrandingService implements BrandingService {
    
    public String getPropertyFile() {
        return "ags.properties";
    }

    public String getSchema() {
        return "ags";
    }

    public String brandTableName( String tableName ) {
        String netiqTablePrefix = getTablePrefix();
        String sailpointTablePrefix = "spt_";
        String brandedTableName = tableName.toLowerCase();
        if( brandedTableName.startsWith( sailpointTablePrefix ) ) {
            brandedTableName = brandedTableName.substring( 4 );
        }
        if (!brandedTableName.startsWith(netiqTablePrefix)) {
            brandedTableName = netiqTablePrefix + brandedTableName;
        }
        if( Character.isUpperCase( tableName.charAt( 0 ) ) ) {
            brandedTableName = brandedTableName.toUpperCase();
        }
        return brandedTableName;
    }

    public String brandExportTableName( String tableName ) {
        String netiqTablePrefix = "agsr_";
        String sailpointTablePrefix = "sptr_";
        String brandedTableName = tableName.toLowerCase();
        if( brandedTableName.startsWith( sailpointTablePrefix ) ) {
            brandedTableName = brandedTableName.substring( 5 );
        }
        if (!brandedTableName.startsWith(netiqTablePrefix)) {
            brandedTableName = netiqTablePrefix + brandedTableName;
        }
        if( Character.isUpperCase( tableName.charAt( 0 ) ) ) {
            brandedTableName = brandedTableName.toUpperCase();
        }
        return brandedTableName;
    }
    
    public String getAdminUserName() {
        return "admin";
    }

    public String getConsoleApp() {
        return "ags";
    }

    @Override
    public String getDbName() {
        return "ags";
    }

    @Override
    public String getTablePrefix() {
        return "ags_";
    }

    public String getSpringConfig() {
        return "agsBeans";
    }

    public String getXmlHeaderElement() {
        return "netiq";
    }

    public String getDtdFilename() {
        return "netiq.dtd";
    }

    public String brandFilename( String schemaFilename ) {
        return schemaFilename.replace( "identityiq", "ags" );
    }

    public String getCustomMessageBundle() {
        return "sailpoint.web.messages.customMessages";
    }

    public String getHelpMessageBundle() {
        return "sailpoint.web.messages.helpMessages";
    }

    public String getMessageBundle() {
        return "sailpoint.web.messages.messages";
    }

    public String getConnectorMessageBundle() {
        return "connector.common.messages.conMessages";
    }

    public String getSelectorScopeMessageKey() {
        return "selector_scope";
    }

    public Brand getBrand() {
        return Brand.AGS;
    }

    public String getApplicationShortName() {
        return "ags";
    }

    public String getApplicationName() {
        return "Access Governance Suite";
    }

}
