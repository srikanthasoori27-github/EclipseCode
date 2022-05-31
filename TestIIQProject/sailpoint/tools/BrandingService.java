package sailpoint.tools;

public interface BrandingService {
    public String getPropertyFile();
    public String getSchema();
    public String brandTableName( String tableName );
    public String brandExportTableName( String tableName );
    public String getAdminUserName();
    public String getSpringConfig();
    public String getXmlHeaderElement();
    public String getDtdFilename();
    public String brandFilename( String schemaFilename );
    public String getCustomMessageBundle();
    public String getMessageBundle();
    public String getHelpMessageBundle();
    public String getConnectorMessageBundle();
    public String getSelectorScopeMessageKey();
    public Brand getBrand();
    public String getApplicationName();
    public String getApplicationShortName();
    public String getConsoleApp();
    /* Default value used by some database script creation classes */
    public String getDbName();
    public String getTablePrefix();
}
