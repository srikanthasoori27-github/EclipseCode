/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Use the Hibernate SchemaExport utility to generate SQL DDL files
 * with the SailPoint schema.  Also run various post-processing steps
 * on the generated schema to fix things.
 *
 * Author: Jeff
 *
 */
package sailpoint.persistence;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Table;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;

import sailpoint.server.ExtendedSchemaGenerator;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;

import static org.hibernate.tool.schema.TargetType.*;

public class SailPointSchemaGenerator {

    public static final String TYPE_MYSQL = "mysql";
    public static final String TYPE_ORACLE = "oracle";
    public static final String TYPE_SQLSERVER = "sqlserver";
    public static final String TYPE_DB2 = "db2";

    public static final String DIALECT_MYSQL =
    "org.hibernate.dialect.MySQL57Dialect";

    public static final String DIALECT_ORACLE = 
    "org.hibernate.dialect.Oracle12cDialect";

    public static final String DIALECT_SQLSERVER = 
    "sailpoint.persistence.SQLServerUnicodeDialect";

    
    public static final String DIALECT_DB2 = 
    "org.hibernate.dialect.DB297Dialect";

    public static final String PLUGIN_TABLE_NAME = "identityiq.spt_plugin";

    public static final String PLUGIN_FILE_ID_COLUMN = "(file_id)";

    private static Log log = LogFactory.getLog(SailPointSchemaGenerator.class);

    /**
     * The default schema may be changed to add a branch qualifier
     * such as "31p".
     */
    private static String _schema = BrandingServiceFactory.getService().getSchema();

    /**
     * Kludge for generating special "concat" indexes for indexing
     * null values on Oracle.
     */
    private static Map<String,List<String>> _nullIndexes;


    public static void generate(String type, String delimiter, String schema,
                                String createFile, String dropFile)
        throws Exception {

        Configuration config = new Configuration();

        // the naming strategy MUST come before the loading of the
        // configuration file or it will not have any effect
        config.setPhysicalNamingStrategy(new SPNamingStrategy());

        config.configure("hibernate.cfg.xml");
        config.setInterceptor(new Interceptor());
        config.setProperty("hibernate.dialect", getDialect(type));
        config.setProperty("hibernate.physical_naming_strategy", "sailpoint.persistence.SPNamingStrategy");

        if (schema != null)
            _schema = schema;
        else
            _schema = BrandingServiceFactory.getService().getSchema();

        config.setProperty(Environment.DEFAULT_SCHEMA, _schema);

        StandardServiceRegistry standardRegistry = new StandardServiceRegistryBuilder().applySettings(config.getProperties())
            .configure("hibernate.cfg.xml")
                //Set set some dumb configs because hibernate throws a fit without a connection, even tho a connection is not needed
                //Set this to prevent HHH000342: Could not obtain connection to query metadata : null WARN
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false)
                //Have to mock this to prevent hibernate HHH000181: No appropriate connection provider encountered, assuming application will be supplying connections WARN
                .applySetting(AvailableSettings.CONNECTION_PROVIDER, new ConnectionProvider() {
                    @Override
                    public boolean isUnwrappableAs(Class unwrapType) {
                        return false;
                    }
                    @Override
                    public <T> T unwrap(Class<T> unwrapType) {
                        return null;
                    }
                    @Override
                    public Connection getConnection() {
                        return null; // Interesting part here
                    }

                    @Override
                    public void closeConnection(Connection conn) throws SQLException {}

                    @Override
                    public boolean supportsAggressiveRelease() {
                        return true;
                    }
                })
                .build();
        Metadata metaData = new MetadataSources(standardRegistry).getMetadataBuilder().build();


        generate(standardRegistry, metaData, type, delimiter, createFile, true);
        generate(standardRegistry, metaData, type, delimiter, dropFile, false);
    }

    /**
     * Generate the create or drop script using the given parameters.
     */
    private static void generate(ServiceRegistry reg, Metadata meta, String type,
                                 String delimiter, String fileName,
                                 boolean create)
        throws Exception {
        
        // SQL Server likes GO statements after each command.
        if (TYPE_SQLSERVER.equals(type)) {
            delimiter += "\n    GO";
        }

        if (verify()) {
            // export the create script
            SchemaExport export = new SchemaExport();
            export.setFormat(true);
            //Hibernate decided to append instead of create. Delete first
            Files.deleteIfExists(Paths.get(fileName));
            export.setOutputFile(fileName);
            export.setDelimiter(delimiter);
            export.execute(EnumSet.of(SCRIPT),    //no output
                           create ? SchemaExport.Action.CREATE : SchemaExport.Action.DROP,
                           meta,
                           reg);  //just create

            List<String> lines = null;

            // Creation requires some fixing up.  Just get the lines for drops.
            if (create) {
                lines = fix(meta, type, fileName);
            }
            else {
                lines = splitFile(fileName);
            }

            // Add any lines SQL required for sequences that aren't in the hbm.
            Dialect dialect = meta.getDatabase().getDialect();
            Sequencer sequencer = new Sequencer(dialect, _schema, reg);
            List<String> sequenceDDL =
                (create) ? sequencer.getCreateSql() : sequencer.getDropSql();
            for (String sequence : sequenceDDL) {
                lines.add("\n    " + sequence.trim() + delimiter);
            }

            writeFile(fileName, lines);
        }
    }

    // Verification run prior to exporting create scripts
    protected static boolean verify() throws Exception {
        boolean valid;
        valid = ExtendedAttributeUtil.verifyExtendedHbmFiles();
        if (!valid) {
            throw new GeneralException("Problem Verifying.");
        }
        return valid;
    }
    
    /**
     * Map an abstract type name into a dialect class name.
     */
    private static String getDialect(String type) {

        String dialect = DIALECT_MYSQL;

        if (type.equals(TYPE_ORACLE))
            dialect = DIALECT_ORACLE;

        else if (type.equals(TYPE_SQLSERVER))
            dialect = DIALECT_SQLSERVER;
        
        else if (type.equals(TYPE_DB2))
        	dialect = DIALECT_DB2;

        return dialect; 
    }

    /**
     * Called after generating the create script, read it and
     * make various adjustments.
     *
     * Expects the file to be in a very specific format, this may be
     * a problem if we ever decide to change Hibernate releases.
     */

    static private List<String> fix(Metadata config, String type,
                                    String fileName)
        throws Exception {

        List<String> lines = splitFile(fileName);

        List<CreateIndexStatement> indexes =
            getAnnotationBasedIndexes(config, type);

        if (type.equals(TYPE_ORACLE) || type.equals(TYPE_SQLSERVER) ||
            type.equals(TYPE_DB2))
            addMissingForeignKeyIndexes(lines, type);

        if (type.equals(TYPE_ORACLE) || type.equals(TYPE_DB2))
            addCaseInsensitivityIndexes(lines, type, indexes);

        for (CreateIndexStatement stmt : indexes) {
            lines.add(stmt.toDDL(type));
        }

        if (type.equals(TYPE_MYSQL)) {
            fixLinkKeys(lines);
            fixIndexes(lines, TYPE_MYSQL);
        }
        
        if (type.equals(TYPE_DB2)) {
            addTablespaceIndicator(lines);
        }

        if (type.equals(TYPE_ORACLE) || type.equals(TYPE_DB2))
            addSchemaIndicator(lines);

        return lines;
    }

    /**
     * Read file and split it up into a list of lines.
     */
    static private List<String> splitFile(String fileName)
        throws Exception {

        List<String> lines = new ArrayList<String>();
        String sql = Util.readFile(fileName);
        String[] array = sql.split("\\n");
        if (array != null) {
            for (int i = 0 ; i < array.length ; i++)
                lines.add(array[i]);
        }
        return lines;
    }

    /**
     * Write a line list back to a file.
     */
    static private void writeFile(String fileName, List<String> lines)
        throws Exception {

        StringBuilder b = new StringBuilder();
        if (lines != null) {
            for (int i = 0 ; i < lines.size() ; i++) {
                b.append(lines.get(i));
                b.append("\n");
            }
        }

        Util.writeFile(fileName, b.toString());
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Missing foreign key indexes
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Add missing indexes to the schema.
     *
     * When we see this:
     *
     * alter table identityiq.spt_account_group 
     *     add constraint FK11A66179A5FB1B1 
     *     foreign key (owner) 
     *     references identityiq.spt_identity (id);
     *
     * We need to add an index after the alter like this:
     *
     * create index identityiq.FK11A66179A5FB1B1 on identityiq.spt_account_group(owner);
     *
     * SQL Server requires that the index be created outside the alter.
     * Not sure if Oracle allows an "add index" within the alter but
     * let's do it the same way for both.
     */
    static private void addMissingForeignKeyIndexes(List<String> lines, String type) {

        String token = "add constraint ";
        String token2 = "alter table";

        for (int i = 0 ; i < lines.size() ; i++) {
            String line = lines.get(i);
            //System.out.println("Fixing: " + line);
            int psn = line.indexOf(token);
            if (psn >= 0) {

                String indexName = line.substring(psn + token.length()).trim();

                if (Util.isNotNullOrEmpty(indexName) && indexName.startsWith("FK")) {
                    //Hibernate 5 now creates unique constraints separate from table creation. Fk = ForeignKey UK = Unique Key

                    // next line has the column name
                    String next = lines.get(i + 1);
                    psn = next.indexOf("(");
                    // keep the surrounding parens
                    String column = next.substring(psn).trim();

                    // Previous line has the table name, unfortuantely
                    // Oracle will prefix "identityiq." but SQLServer will not
                    // UPDATE: as of 3.0 SQLServer will have a prefix too
                    String prev = lines.get(i - 1);
                    psn = prev.indexOf(token2);
                    String table = prev.substring(psn + token2.length()).trim();

                    // determine if the index statement should be skipped
                    if (!skipInsertIndex(table, column, type)) {
                        // now insert the index
                        int insertPoint = findNextInsert(lines, i, type);

                        String index = new CreateIndexStatement(indexName, table, column, false).toDDL(type);
                        lines.add(insertPoint, index);
                        // skip over the remainer of the alter and the index
                        i = insertPoint + 1;
                    }
                }
                
            }
        }
    }

    /**
     * Helper method to determine if we should skip adding a index create statement for the way we handle
     * adding missing foreign key indicies after an alter statement in {@link #addMissingForeignKeyIndexes(List, String)}
     *
     * The only conditions that meet the requirements right now are if the table type is oracle,
     * the column name is "(file_id)", and the table name is the plugins table.
     * This was causing issues in the case of a <many-to-one> being used with a "unique=true" set on it,
     * It would already set an index on the column so the added create index would fail
     *
     * @param table The table name.
     * @param column The column name with parenthesis surrounding it.
     * @param type The table type.
     * @return True if the conditions match to skip adding a create index statement
     */
    private static boolean skipInsertIndex(String table, String column, String type) {

        return TYPE_ORACLE.equals(type) &&
               PLUGIN_TABLE_NAME.equals(table) &&
               PLUGIN_FILE_ID_COLUMN.equals(column);
    }

    /**
     * Look for the next insert point in the schema file.
     * This is the line immediately after a line ending with a semicolon.
     */
    static private int findNextInsert(List<String> lines, int start, String type) {

        // insert at the end if we can't find one
        int end = lines.size();
        boolean isSqlServer = TYPE_SQLSERVER.equals(type);

        for (int i = start ; i < end ; i++) {
            String line = lines.get(i);
            // in practice it will always be at the end but there
            // could be blank padding
            if (line.indexOf(";") >= 0) {
                end = i + 1;
                
                if (isSqlServer) {
                    end = skipGoStatements(lines, end);
                }
                
                break;
            }
        }

        return end;
    }

    /**
     * Skip past any empty lines or GO statements that come immediately at or
     * after the given line.
     */
    static private int skipGoStatements(List<String> lines, int start) {
        
        int end = start;
        
        for (int i=start; i<lines.size(); i++) {
            String line = lines.get(i);
            
            // If we find a blank line, keep going...
            if (Util.isNullOrEmpty(line)) {
                continue;
            }
            else if (line.contains("GO")) {
                // If we found a GO statement, return the next line.
                end = i + 1;
                break;
            }
            else {
                // We found a non-empty, non-GO line, so use the original
                // insertion point.
                break;
            }
        }
        
        return end;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Case-insensitive indexes
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Oracle and DB2 cannot use normal indexes for case-insensitive queries.
     * This can be solved in Oracle by using function-based indexes, and in DB2
     * by adding generated uppercase columns to the create table statements and
     * creating indexes over these instead.
     * 
     * This method will add any special case-insensitive indexes to the
     * "indexes" list that is passed in, and add the generated columns to the
     * create table statements for DB2.
     *
     * We are guided by a naming convention on the index name.  If
     * the name ends with _ci the index is converted from this:
     *
     *   create index <name>_ci on <table> (<column>);
     *
     * to:
     *
     *   create index <name>_ci on <table> (upper(<column>)); // Oracle
     *   create index <name>_ci on <table> (<column>_ci);     // DB2
     * 
     * Note that we leave the _ci prefix so we can consistently identify
     * case insensitive indexes.
     *
     * If the name ends with _csi the indexes is converted into two:
     *
     *   create index <name> on <table> (<column>);
     *   create index <name>_ci on <table> (upper(<column>)); // Oracle
     *   create index <name>_ci on <table> (<column>_ci);     // DB2
     *
     * Next if we see any column definition that includes a "unique" constraint,
     * we add a _ci index after the create table statement.  This table:
     *
     *   create table <table> (
     *     ...
     *     <column> varchar2(128 char) unique,
     *     ...
     *   );
     *
     * causes insertion of this index:
     * 
     *   create index <table>_<column>_ci on <table> (upper(<column>));
     *
     * 
     * On DB2, the generated columns will be added like this:
     * 
     *  create table <table> (
     *    ...
     *    <column>_ci generated always as (upper(<column>)),
     *    ...
     *  );
     */
    static private void addCaseInsensitivityIndexes(List<String> lines, String type,
                                                    List<CreateIndexStatement> indexes) {

        // First, find the case insensitive indexes we're going to have to
        // create.  For DB2 we're going to have to add generated columns to the
        // create table statement for these.
        for (ListIterator<String> it=lines.listIterator(); it.hasNext(); ) {
            String line = it.next();
            List<CreateIndexStatement> stmts = parseCreateIndex(line);
            if (null != stmts) {
                boolean hasCi = false;
                for (CreateIndexStatement stmt : stmts) {
                    hasCi |= stmt.hasCaseInsensitiveColumn();
                }

                if (hasCi) {
                    indexes.addAll(stmts);
                    // Remove the line - we'll add this at the end.
                    it.remove();
                }
            } else {
                //Parse Create Unique Constraint
                String unique = parseUniqueColumn(line);
                if (null != unique) {
                    //Get the table from the previous line
                    //Statements look like:
                    //alter table identityiq.spt_alert_definition
                    //  add constraint UK_p9a15ie5pfscgm3hb745wwnsm unique (name);

                    //Back up to current iterator element
                    it.previous();
                    //Back up to previous iterator element
                    String tableName = parseUniqueTable(it.previous());
                    if (null == tableName) {
                        throw new RuntimeException("Invalid state - found unique without leading table: " + line);
                    }
                    String indexName = createIndexName(tableName, unique);
                    indexes.add(new CreateIndexStatement(indexName, tableName, unique, true));
                    //Move to next
                    it.next();
                    //Move forward to current
                    String adv1 = it.next();
                    if (!Util.nullSafeEq(adv1, line)) {
                        throw new RuntimeException("Wrong line expected advancing iterator");
                    }
                }
            }
        }
        
        String lastTable = null;
        for (int i = 0 ; i < lines.size() ; i++) {
            String line = lines.get(i);

            String table = parseCreateTable(line);
            if (null != table) {
                lastTable = table;
            }
            
            if ((null != lastTable) && parseCreateTableEnd(line)) {
                // Add generated columns for DB2.
                if (TYPE_DB2.equals(type)) {
                    i = addDB2GeneratedColumns(lastTable, indexes, lines, i);
                }
                // Clear out the last table ... no longer in a create.
                lastTable = null;
            }
        }
    }

    private static String createIndexName(String table, String column) {
        // note that spt_activity_data_source_name_ci is 31 characters
        // and the maximum is 30, so we won't put on the _ci suffix to
        // avoid unecessary truncation.
        String shortTable = stripPrefix(table);
        return abbreviate(shortTable + "_" + column);
    }
    
    static private String stripPrefix(String src) {
        int dot = src.indexOf(".");
        if (dot > 0) 
            src = src.substring(dot + 1);
        return src;
    }

    static private String abbreviate(String src) {
        // Just truncate this sucker.  This may make something non-unique but it
        // seems unlikely.  If we start getting into this situation, we'll deal
        // with it.  Oracle likes index names at 30 characters or less.
        String abbreviated = src;
        if (src.length() > 30) {
            abbreviated = src.substring(0, 30);
        }
        return abbreviated;
    }

    private static String parseCreateTable(String line) {
        
        String tableName = null;
        final String createTableToken = "create table ";

        int psn = line.indexOf(createTableToken);
        if (psn >= 0) {
            tableName = line.substring(psn + createTableToken.length());
            int space = tableName.indexOf(" ");
            if (space > 0)
                tableName = tableName.substring(0, space);
        }

        return tableName;
    }
    
    private static boolean parseCreateTableEnd(String line) {
        return (line.indexOf(';') >= 0);
    }
    
    private static String parseUniqueColumn(String line) {
        final String uniqueToken = " unique (";
        String column = null;
        String trimLine = line.trim();
        int psn = trimLine.indexOf(uniqueToken);
        if (psn >= 0) {
            // Column will something like "add constraint UK_p9a15ie5pfscgm3hb745wwnsm unique (name);"
            column = trimLine.substring(psn+uniqueToken.length(), trimLine.length()-2);
        }
        return column;
    }

    private static String parseUniqueTable(String line) {
        final String ALTER_TABLE_TOKEN = "alter table ";
        int psn = line.indexOf(ALTER_TABLE_TOKEN);
        String tableName;
        if (psn >= 0) {
            // Will look like:
            // alter table identityiq.spt_bundle
            // Grab the table name.
            int nameStart = psn + ALTER_TABLE_TOKEN.length();
            tableName = line.substring(nameStart).trim();
        } else {
            throw new RuntimeException("Malformed add constraint - missing table: " + line);
        }

        return tableName;

    }
    
    private static List<CreateIndexStatement> parseCreateIndex(String line) {

        final String CREATE_INDEX_TOKEN = "create index ";
        List<CreateIndexStatement> stmts = null;

        int psn = line.indexOf(CREATE_INDEX_TOKEN);
        if (psn >= 0) {
            // Will look like:
            // create index identityiq.spt_actgroup_key3_ci on identityiq.spt_account_group (key3);

            // Grab the index name.
            int nameStart = psn + CREATE_INDEX_TOKEN.length();
            String indexName = line.substring(nameStart);
            int space = indexName.indexOf(" ");
            if (space > 0)
                indexName = indexName.substring(0, space);

            // Grab the table name.
            int on = line.indexOf("on ");
            int paren = line.indexOf("(");
            if ((-1 == on) || (-1 == paren)) {
                throw new RuntimeException("Malformed create index - missing ON or paren: " + line);
            }
            String table = line.substring(on+3, paren).trim();

            // Grab the column name.
            int closeParen = line.indexOf(')');
            String column = line.substring(paren + 1, closeParen);

            boolean ci = indexName.endsWith("_ci");
            boolean csi = indexName.endsWith("_csi");

            stmts = new ArrayList<CreateIndexStatement>();

            // Do we need a case-sensitive index?
            if (!ci || csi) {
                // Trim the _csi off the index name if there is one.
                String csName =
                    (!csi) ? indexName : indexName.substring(0, indexName.length() - 4);
                stmts.add(new CreateIndexStatement(csName, table, column, false));
            }

            // Do we need a case-insensitive index?
            if (ci || csi) {
                stmts.add(new CreateIndexStatement(indexName, table, column, true));
            }
        }
        return stmts;
    }
    
    /**
     * DB2 requires generated columns to allow indexed case-insensitive
     * searches.  This will add any required generated columns based on the
     * given indexes to the given table.
     */
    private static int addDB2GeneratedColumns(String table,
                                              List<CreateIndexStatement> indexes,
                                              List<String> lines, int lastLineIdx) {

        final String GENERATED_COL_SQL =
            "        %s generated always as (upper(%s)),";

        List<CreateIndexStatement> indexesForTable =
            new ArrayList<CreateIndexStatement>();
        for (CreateIndexStatement idx : indexes) {
            if (idx.isOnTable(table)) {
                indexesForTable.add(idx);
            }
        }
        
        Set<String> addedColumns = new HashSet<String>();
        if (!indexesForTable.isEmpty()) {
            // Scroll back above any constraints at the end of the create statement.
            int insertIdx = -1;
            for (insertIdx = lastLineIdx-1; insertIdx > 0; insertIdx--) {
                String line = lines.get(insertIdx);
                if (!line.contains("primary") && !line.contains("constraint")) {
                    break;
                }
            }
            insertIdx++;

            // Now add a generated column for each index.
            for (CreateIndexStatement stmt : indexesForTable) {
                for (CreateIndexStatement.Column col : stmt.getColumns()) {
                    if (col.isCaseInsensitive()) {
                        // extended1_ci GENERATED ALWAYS AS (UPPER(extended1)),
                        String colName = col.getName();
                        String generatedCol = col.getName();
                        if (!colName.endsWith("_ci")) {
                            generatedCol += "_ci";
                        }
                        else {
                            // Trim the "_ci" off of the column.
                            colName = colName.substring(colName.length() - 3);
                        }
                        
                        // Prevent adding the same column twice.  This can happen
                        // if there are multiple indexes over the same column.
                        if (!addedColumns.contains(generatedCol)) {
                            lines.add(insertIdx, String.format(GENERATED_COL_SQL, generatedCol, colName));
                            addedColumns.add(generatedCol);
                        }
                    }
                }
            }
        }
        
        return lastLineIdx + addedColumns.size();
    }
    
    /**
     * Represenation of a "create index" DDL statement.
     */
    private static class CreateIndexStatement {
        
        private static class Column {
            private String name;
            private boolean caseInsensitive;

            public Column(String name, boolean caseInsensitive) {
                this.name = name;
                this.caseInsensitive = caseInsensitive;
            }

            public boolean isCaseInsensitive() {
                return this.caseInsensitive;
            }
            
            public String getName() {
                return this.name;
            }
            
            public String toDDL(String type) {
                String col = this.name;
                if (this.caseInsensitive) {
                    if (TYPE_DB2.equals(type)) {
                        // In DB2 we need to change the column name to use _ci.
                        col = this.name + "_ci";
                    }
                    else if (TYPE_ORACLE.equals(type)) {
                        // In Oracle create a function-based index.
                        col = "upper(" + this.name + ")";
                    }
                }
                
                return col;
            }
        }
        
        private String name;
        private String table;
        private List<Column> columns;

        private CreateIndexStatement(String name, String table, String column,
                                     boolean caseInsensitive) {
            this.name = name;
            this.table = table;
            this.columns = new ArrayList<Column>();
            this.columns.add(new Column(column, caseInsensitive));
        }
        

        private CreateIndexStatement(String name, String table, List<Column> columns) {
            this.name = name;
            this.table = table;
            this.columns = columns;
        }

        public List<Column> getColumns() {
            return this.columns;
        }

        public boolean hasCaseInsensitiveColumn() {
            for (Column col : this.columns) {
                if (col.isCaseInsensitive()) {
                    return true;
                }
            }
            return false;
        }

        public boolean isOnTable(String table) {
            return stripPrefix(table).equals(stripPrefix(this.table));
        }

        public String toDDL(String type) {
            StringBuilder clause = new StringBuilder();
            String sep = "";

            for (Column col : this.columns) {
                clause.append(sep).append(col.toDDL(type));
                sep = ", ";
            }

            return getCreateIndexString(this.name, this.table, clause.toString(), type);
        }

        private static String getCreateIndexString(String indexName, String tableName,
                                                   String clause, String type) {

            final String CREATE_INDEX = "\n    create index %s on %s %s;";
            String prefix = _schema + ".";

            if (!tableName.startsWith(prefix)) {
                tableName = prefix + tableName;
            }

            // MySQL doesn't like a schema name prefix on the index.
            if (!TYPE_MYSQL.equals(type) && !TYPE_SQLSERVER.equals(type) && !indexName.startsWith(prefix)) {
                indexName = prefix + indexName;
            }

            if (!clause.trim().startsWith("(")) {
                clause = "(" + clause + ")";
            }
            
            String ddl = String.format(CREATE_INDEX, indexName, tableName, clause);

            // For SQL Server we need to make it GO!
            if (TYPE_SQLSERVER.equals(type)) {
                ddl += "\n    GO";
            }

            return ddl;
        }
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Link keys
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Fix Link key columns that were emitted as "text" but
     * need to be varchar(2000) so they can be indexed.
     *
     * When we see this:
     *
     *   key1 text,
     *   key2 text,
     *   key3 text,
     *   key4 text,
     * 
     * It needs to be converted to:
     *
     *   key1 varchar(2000),
     *   key2 varchar(2000),
     *   key3 varchar(2000),
     *   key4 varchar(2000),
     *
     * We'll try to adapt to additions in the key list by looking
     * for the regexp "key[0-9]+ text"
     */
    static private void fixLinkKeys(List<String> lines) throws Exception {

        for (int i = 0 ; i < lines.size() ; i++) {
            String line = lines.get(i);
            if (line.matches(".*key[0-9]+ text.*")) {

                int psn = line.indexOf("text");
                String left = line.substring(0, psn);
                String correct = left + "varchar(2000),";

                lines.set(i, correct);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Index Length
    //
    ////////////////////////////////////////////////////////////////////////////

    private static class IndexMap extends HashMap {
        public IndexMap() {
            //Create MySQL entry
            List<Index> mySqlEntries = new ArrayList();
            BrandingService svc = BrandingServiceFactory.getService();
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_certification") + " (name)", svc.brandTableName("spt_certification") + " (name(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_request") + " (name)", svc.brandTableName("spt_request") + " (name(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_link") + " (key1)",          svc.brandTableName("spt_link") + " (key1(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_managed_attribute") + " (displayable_name)",
                    svc.brandTableName("spt_managed_attribute") + " (displayable_name(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_archived_cert_item") + " (policy)",
                    svc.brandTableName("spt_archived_cert_item") + " (policy(255))")));
            mySqlEntries.add(new Index(null, Pair.make("(identity_id, application, native_identity, instance)",
                    "(identity_id(32), application(32), native_identity(175), instance(16))")));
            mySqlEntries.add(new Index("ident_entit_comp_name", Pair.make("(identity_id, name)",
                    "(identity_id(32), name(223))")));
            mySqlEntries.add(new Index(null, Pair.make("(application, native_identity)",
                    "(application(32), native_identity(223))")));
            mySqlEntries.add(new Index(null, Pair.make("(application, type, attribute, value)",
                    "(application(32), type(32), attribute(50), value(141))")));
            mySqlEntries.add(new Index(null, Pair.make("(attribute_name, value)",
                    "(attribute_name(55), value(200))")));

            mySqlEntries.add(new Index(null, Pair.make("(native_identity)",  "(native_identity(255))")));
            mySqlEntries.add(new Index(null, Pair.make("(account_name)",     "(account_name(255))")));
            mySqlEntries.add(new Index(null, Pair.make("(attribute_value)",  "(attribute_value(255))")));
            mySqlEntries.add(new Index(null, Pair.make("(assigned_scope_path)",
                    "(assigned_scope_path(255))")));
            mySqlEntries.add(new Index(null, Pair.make("(exception_native_identity)",
                    "(exception_native_identity(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_identity_entitlement") + " (value)", svc.brandTableName("spt_identity_entitlement") + " (value(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_identity_history_item") + " (value)", svc.brandTableName("spt_identity_history_item") + " (value(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_identity_history_item") + " (attribute)",
                    svc.brandTableName("spt_identity_history_item") + " (attribute(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_identity_request_item") + " (value)", svc.brandTableName("spt_identity_request_item") + " (value(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_managed_attribute") + " (value)", svc.brandTableName("spt_managed_attribute") + " (value(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_managed_attribute") + " (attribute)",
                    svc.brandTableName("spt_managed_attribute") + " (attribute(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_mitigation_expiration") + " (attribute_name)",
                    svc.brandTableName("spt_mitigation_expiration") + " (attribute_name(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_process_log") + " (workflow_case_name)",
                    svc.brandTableName("spt_process_log") + " (workflow_case_name(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_scope") + " (path)",  svc.brandTableName("spt_scope") + " (path(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_syslog_event") + " (message)",  svc.brandTableName("spt_syslog_event") + " (message(255))")));
            mySqlEntries.add(new Index(null, Pair.make(svc.brandTableName("spt_provisioning_transaction") + " (account_display_name)",
                    svc.brandTableName("spt_provisioning_transaction") + " (account_display_name(255))")));
            mySqlEntries.add(new Index(svc.getTablePrefix() + "target_native_obj_id", Pair.make(svc.brandTableName("spt_target") + " (native_object_id)", svc.brandTableName("spt_target") + "(native_object_id(255))")));
            mySqlEntries.add(new Index(svc.getTablePrefix() + "arch_entity_identity_csi", Pair.make(svc.brandTableName("spt_archived_cert_entity") + " (identity_name)", svc.brandTableName("spt_archived_cert_entity") + " (identity_name(255))")));
            mySqlEntries.add(new Index(svc.getTablePrefix() + "arch_entity_acct_grp_csi", Pair.make(svc.brandTableName("spt_archived_cert_entity") + " (account_group)", svc.brandTableName("spt_archived_cert_entity") + " (account_group(255))")));
            mySqlEntries.add(new Index(svc.getTablePrefix() + "cert_entity_identity", Pair.make(svc.brandTableName("spt_certification_entity") + " (identity_id)", svc.brandTableName("spt_certification_entity") + " (identity_id(255))")));
            mySqlEntries.add(new Index(svc.getTablePrefix() + "entitlement_snapshot", Pair.make(svc.brandTableName("spt_entitlement_snapshot") + " (display_name)", svc.brandTableName("spt_entitlement_snapshot") + " (display_name(255))")));


            this.put(TYPE_MYSQL, mySqlEntries);
            this.getExtendedAttributeIndexes(TYPE_MYSQL);

            //Other type entries...
        }

        public void getExtendedAttributeIndexes(String type) {
            Map<String, ExtendedAttributeUtil.MappingFile> files = ExtendedAttributeUtil.getMappingFiles();
            List<Index> indexes = (List)this.get(type);
            for (ExtendedAttributeUtil.MappingFile file : Util.safeIterable(files.values())) {

                for (ExtendedAttributeUtil.PropertyMapping mapping : Util.safeIterable(file.getProperties())) {
                    if (Util.isNotNullOrEmpty(mapping.index) && Util.atoi(mapping.length) > 255) {
                        if (indexes == null) {
                            indexes = new ArrayList<Index>();
                        }
                        indexes.add(new Index(mapping.index, new Pair("("+ ExtendedSchemaGenerator.getColumnName(mapping.getName())+")", "("+ExtendedSchemaGenerator.getColumnName(mapping.getName())+"(255))")));
                    }
                }
            }
        }

        static class Index {
            public String indexName;
            public Pair replacement;
            public Index(String indexName, Pair replacement) {
                this.indexName = indexName;
                this.replacement = replacement;
            }
        }
    }

    static final Map indexFixes = Collections.unmodifiableMap(new IndexMap());

    static final String indexRegex = "^\\s*create\\s*index.*";

    static final String indexToken = "create index";

    /**
     * Update generated indexes. This is currently used to substring MySQL indexes that exceed 767 bytes. However,
     * this can be used to replace any part of an index creation DDL statement.
     * NOTE: named extended columns will need to be altered in the script after the generation is complete.
     * @param lines parsed lines from the generated DDL file
     * @param type type of database
     */
    static private void fixIndexes(List<String> lines, String type) {

        if (Util.isNotNullOrEmpty(type) && !Util.isEmpty(lines)) {
            List<IndexMap.Index> replacements = (List)indexFixes.get(type);

            for (int i=0; i<lines.size(); i++) {
                String line = lines.get(i);
                if (Util.isNotNullOrEmpty(line) && (line.indexOf(indexToken) > -1)) {
                    for (IndexMap.Index index : Util.safeIterable(replacements)) {
                        if (Util.isNotNullOrEmpty(index.indexName)) {
                            //Look for specific index based on name
                            if (line.contains(index.indexName)) {
                                String newLine = line.replace((String)index.replacement.getFirst(), (String)index.replacement.getSecond());
                                if (log.isDebugEnabled()) {
                                    log.debug("Updated index: " + line + "to: " + newLine + " for database type " + type);
                                }
                                lines.set(i, newLine);
                                //Should only match one entry in indexFixes. break out
                                break;
                            }
                        } else {
                            //Search based on Pair.first
                            if (line.contains((String)index.replacement.getFirst())) {
                                String newLine = line.replace((String)index.replacement.getFirst(), (String)index.replacement.getSecond());
                                if (log.isDebugEnabled()) {
                                    log.debug("Updated index: " + line + "to: " + newLine + " for database type " + type);
                                }
                                lines.set(i, newLine);
                                //Should only match one entry in indexFixes. break out
                                break;
                            }
                        }

                    }
                }
            }


        }


    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Tablespace indicator
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Add an indication of which tablespace the tables need to be in ("IN identityiq_ts").
     * The SchemaGenerator does not do this by default and since this is a configuration option
     * that would most likely be of interest to some DBA, I'm implementing it here.  Without this,
     * the tables are created in the default tablespace.
     */
    static private void addTablespaceIndicator(List<String> lines) throws Exception {
    	boolean createTable = false;
    	
    	for (int i = 0; i < lines.size(); i++){
    		String line = lines.get(i);
    		
    		if (line.matches(".*create table.*")) {
    			createTable = true;
    		}
    		else if (line.matches(".*;.*") && createTable) {
    			createTable = false;
    			
    			int semi = line.indexOf(";");
    			String left = line.substring(0, semi);
    			
    			//System.out.println(left + " IN identityiq_ts;");
			lines.set(i, left + " IN " + BrandingServiceFactory.getService().getSchema() + "_ts;");
    		}
    	}
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Schema indicator
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Need to add a schema indicator for the indexes (create index identityiq.*).
     * Otherwise, the indexes get created in the default schema.  This is a convenience for
     * the DBA.
     */
    static private void addSchemaIndicator(List<String> lines) throws Exception {
    	//Each "create index" query is on one line.
    	String createIndexToken = "create index ";
        String prefix = _schema + ".";
    	
    	for (int i = 0; i < lines.size(); i++){
    		String line = lines.get(i);
    		
    		if (line.matches(".*" + createIndexToken + ".*")) {   			
    			int pos = line.indexOf(createIndexToken);
    			String left = line.substring(0, pos + createIndexToken.length());
    			String right = line.substring(pos + createIndexToken.length(), line.length());
    			//Don't set it twice
    			if (!right.startsWith(prefix)) {
                    lines.set(i, left + _schema + "." + right);
                }
    		}
    	}
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Annotation based index processing
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * List of classes that don't actually have a first class hbm file
     * but are parent classes of other files via an entity reference.
     */
    private static String[] explicitClasses = {"sailpoint.object.BaseConstraint",
                                               "sailpoint.object.BaseIdentityIndex", 
                                               "sailpoint.object.ExternalAttribute", 
                                               "sailpoint.object.GenericIndex",
                                               "sailpoint.object.SailPointObject",
                                               "sailpoint.object.TaskItem", 
                                               "sailpoint.object.TaskItemDefinition", 
                                               "sailpoint.object.WorkItemMonitor"};

    private static int MAX_INDEX_NAME_LENGTH = 30;
    private static Set<String> _usedIndexNames;

    /**
     * Read annotations from our object classes and process
     * any of our custom Indexes annotations.
     */
    @SuppressWarnings("unchecked")
    private static List<CreateIndexStatement> getAnnotationBasedIndexes(Metadata config,
                                                                        String type)
        throws Exception {

        List<CreateIndexStatement> stmts = new ArrayList<CreateIndexStatement>();
        
        _usedIndexNames = new HashSet<String>();
        // Process all of the class mappings from hibernate

        Collection<PersistentClass> entityBindings = config.getEntityBindings();
        Iterator<PersistentClass> mappings = entityBindings.iterator();
        while ( mappings.hasNext() ) {
            PersistentClass root = mappings.next();
            Class clazz = root.getMappedClass();
            Boolean isAbstract = root.isAbstract();
            if ( isAbstract == null ) 
                isAbstract = new Boolean(false);
            // skip abstract classes
            if ( isAbstract || clazz == null ) continue;
            Indexes indexes = (Indexes)clazz.getAnnotation(Indexes.class);
            if ( indexes != null ) {
                processClassIndexes(type, config, clazz, indexes, stmts);
            }
        }
        // Also process the classes that don't have mapping, but are still
        // "parent" classes like SailPointObject. If we see a bunch
        // of these we might be able to figure out a better way to 
        // iterate over them...
        for ( String className : explicitClasses ) {
            Class clazz = Class.forName(className);
            if ( clazz == null ) continue;
            Indexes indexes = (Indexes)clazz.getAnnotation(Indexes.class);
            if ( indexes != null ) {
                processClassIndexes(type, config, clazz, indexes, stmts);
            }
        }
        _usedIndexNames.clear();
        _usedIndexNames = null;

        return stmts;
    }

    /**
     * Iterate over the custom indexes defined for a class 
     * and append new indexes to the lines of the create
     * script.
     */
    private static void processClassIndexes(String type, 
                                            Metadata config,
                                            Class<?> clazz,
                                            Indexes indexes,
                                            List<CreateIndexStatement> indexStmts)
        throws Exception {

        Map<String,List<Index>> classIndexes = new HashMap<String,List<Index>>();
        Map<String,List<Index>> subClassIndexes = new HashMap<String,List<Index>>();
        // Combine all indexs that are named simmular and
        // separate out the subclass and non-subclass 
        Index[] indexList = indexes.value();
        for ( Index index : indexList ) {
            List<Index> idxs = new ArrayList<Index>();
            String indexName = index.name();
            if ( Util.getString(indexName) == null ) {
                // this is just a name for the group of filters
                // Composite indexes will require the name to match
                // The real index name will be generated right
                // before we output the index
                indexName = clazz.getName() + "_" + index.property();
            } 
            if ( index.subClasses() ) {
                if ( subClassIndexes.get(indexName) != null ) {
                    idxs = subClassIndexes.get(indexName);
                }
                idxs.add(index);
                subClassIndexes.put(indexName, idxs);
            } else {                
                if ( classIndexes.get(indexName) != null ) {
                    idxs = classIndexes.get(indexName);
                }
                idxs.add(index);
                classIndexes.put(indexName, idxs);
            }
        }
        
        // We are only interested in the combined index list for processing 
        Collection<List<Index>> values = classIndexes.values();
        if ( values != null ) {
            for ( List<Index> idx : values )  {
                PersistentClass root = config.getEntityBinding(clazz.getName());
                if ( root != null ) {
                    CreateIndexStatement stmt = generateIndex(type, idx, root);
                    if ( stmt != null ) {
                        indexStmts.add(stmt);
                    }
                } else {          
                    if (log.isDebugEnabled())
                        log.debug("Unable to find PerisstenClass for [" + clazz.getName() + "]");
                }
            }
        }
          
        Collection<List<Index>> subClassIndexesList = subClassIndexes.values();
        if ( subClassIndexesList != null ) {
            processSubClassIndexes(type, config, clazz, subClassIndexesList, indexStmts);
        }
    }

    @SuppressWarnings("unchecked")
    private static void processSubClassIndexes(String type, 
                                               Metadata config,
                                               Class parent,
                                               Collection<List<Index>> indexes,
                                               List<CreateIndexStatement> indexStmts) 
        throws Exception {

        for ( List<Index> indexList : indexes ) {

            Collection<PersistentClass> entityBindings = config.getEntityBindings();
            Iterator<PersistentClass> mappings = entityBindings.iterator();
            while ( mappings.hasNext() ) {
                PersistentClass root = mappings.next();
                Class child = root.getMappedClass();

                Boolean isAbstract = root.isAbstract();
                if ( isAbstract == null ) 
                    isAbstract = new Boolean(false);
                if ( ( !isAbstract ) &&
                     ( parent.isAssignableFrom(child) ) && 
                     ( subClassHasProperties(type,config,child.getName(),indexList) ) ) {
                    CreateIndexStatement stmt = generateIndex(type, indexList, root);
                    if ( stmt != null ) {
                        indexStmts.add(stmt);
                    }
                }
            }            
        }
    }

    /**
     * Check each class and make sure it has the properties
     * defined in the index.  This is only called when
     * processing the subClasses and is intended to skip
     * subclasses that don't have the property defined.
     */
    private static boolean subClassHasProperties(String type, 
                                                 Metadata config,
                                                 String className,
                                                 List<Index> indexes) {

        PersistentClass root = config.getEntityBinding(className);
        for ( Index index : indexes ) { 
            String colName = getColumnName(root, index.property());
            if ( colName == null ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Index property ["+index.property()
                              +"] was not found on class ["+className
                              +"] subclass index was ignored.");
                }
                
                return false;
            }
        } 
        return true;
    }

    /**
     * Gets the column name for the specified index.
     * @param index
     * @return
     */
    private static String getColumnName(PersistentClass root, Index index)
    	throws Exception
    {
    	if (!Util.isNullOrEmpty(index.column())) {
    		return index.column();
    	}
    	
    	String property = index.property();
        String columnName = getColumnName(root, property);
        if ( columnName == null ) 
            throw new Exception("Error processing annotation can't resolve column for property ["+property+"] from class ["+ root.getMappedClass().getName() );
        
        return columnName;    	
    }
    
    /**
     * Generate a "create index ..." statement that will be placed 
     * into the create scripts.  Composite indexes will have 
     * more then on Index object in the indexes List all with the 
     * same index name. For normal indexes there will be only 
     * one index in the indexes list.
     */
    private static CreateIndexStatement generateIndex(String type, 
                                                      List<Index> indexes, 
                                                      PersistentClass root) 
        throws Exception {

        CreateIndexStatement stmt = null;
        
        String tableName = null;
        
        List<CreateIndexStatement.Column> columns = new ArrayList<CreateIndexStatement.Column>();
        for (Index index : indexes) {
        	if (tableName == null && !Util.isNullOrEmpty(index.table())) {
        		tableName = index.table();
        	}
        	
            columns.add(new CreateIndexStatement.Column(getColumnName(root, index), isNotCaseSensitive(index)));
        }

        // bug#14076 for certain indexes, add column for a "concat index"
        // to handle searching for null values
        if (TYPE_ORACLE.equals(type) && indexes.size() > 0) { 
            // this the class name without package and .class
            String cname = root.getMappedClass().getSimpleName();
            // only expecting one index, in theory we could add this
            // at the end of a multi column index but don't have those yet
            Index first = indexes.get(0);
            Map<String,List<String>> nulls = getNullIndexes();
            List<String> props = nulls.get(cname);
            if (props != null && props.contains(first.property())) {
                columns.add(new CreateIndexStatement.Column("' '", false));
            }
        }

        String indexName = getIndexName(indexes, root);
        
        if (tableName == null) {
        	tableName = root.getTable().getName();
        }
        
        if ( ( indexName != null ) && !columns.isEmpty() ) {
            stmt = new CreateIndexStatement(BrandingServiceFactory.getService().brandTableName( indexName ), BrandingServiceFactory.getService().brandTableName(tableName), columns);
        } 
        return stmt;
    }

    private static String getIndexName(List<Index> indexes, PersistentClass root) 
        throws Exception {

        if ( indexes.size() == 0 ) {
            throw new Exception("Index list was null, exspected at least one!");
        }
        Table table = root.getTable();
        String tableName = table.getName();

        // First see if there is a name defined with the annotation
        String indexName = indexes.get(0).name();
        if ( Util.getString(indexName) == null ) {
            indexName = generateNiceIndexName(indexes, tableName);
        }
        if ( ( _usedIndexNames.contains(indexName) ) ||
             ( indexName.length() > MAX_INDEX_NAME_LENGTH ) ) {
            // We've already used ths index or its too long for 
            // oracle so generate a name
            indexName = mungeIndexName(indexName, tableName);
        }
        _usedIndexNames.add(indexName);
        return indexName; 
    }

    private static String generateNiceIndexName(List<Index> indexes, String tableName) {
        List<String> properties = new ArrayList<String>();
        for ( Index index : indexes ) {
            properties.add(index.property());
        }
        return tableName + "_" + Util.listToCsv(properties);
    }

    /**
     * Build an reproducable index name which is shorter then 30 characters
     * and will be fairly unique.
     */
    public static String mungeIndexName(String name, String tableName) {
        int result = 0;
	if ( tableName != null ) 
            result += tableName.hashCode();
        result += name.hashCode();
        return "IDX"+ ( Integer.toHexString( name.hashCode() ) + 
                        Integer.toHexString( result ) ).toUpperCase();
    }
    
    @SuppressWarnings("unchecked")
    private static String getColumnName(PersistentClass root, String property) {
        
        String name = null;
        try {
            Property prop = root.getProperty(property);
            if ( prop.getColumnSpan()  == 1 ) {
                Iterator<Column> columns = prop.getColumnIterator();
                while ( columns.hasNext() ) {
                    Column col = columns.next();
                    name = col.getName();
                }
            }    
        } catch (Exception e) {            
            if (log.isDebugEnabled())
                log.debug("Failed to get column for propery [" + property + "] on class " + 
                          root.getMappedClass().getName()+ " " + e.getMessage());   
        }
        
        return name;         
    }

    private static boolean isNotCaseSensitive(Index index) {
        if ( index.name().endsWith("ci") || ( !index.caseSensitive() ) ) {
           return true;
        }
        return false;
    }

    /**
     * Name of our null index definition file. 
     */
    public static final String NULL_INDEX_FILE = "iiqNullIndexes.txt";

    /**
     * Bug #14076
     * Kludge to configure the generation of "concat indexes" for
     * assignedScopePath.  We don't want to blindly do this for
     * all columns or even all assignedScopePaths, so start by selectively
     * enabling this from a file of the form:
     *
     *    <classname>,<propertyName>
     *
     * Where <classname> is the sailpoint.object class without package
     * prefix and without ".class", for example "Identity".  
     * <propertyName> must be the camel cased property name for
     * example "assignedScopePath".
     */
    static private Map<String,List<String>> getNullIndexes() {
        if (_nullIndexes == null) {
            _nullIndexes = new HashMap<String,List<String>>();
            try {
                InputStream is = ClassLoader.getSystemResourceAsStream(NULL_INDEX_FILE);
                if (is != null) {
                    String stuff = Util.readInputStream(is);
                    if (stuff != null) {
                        //System.out.println("Read " + NULL_INDEX_FILE);
                        //System.out.println(stuff);
                        if (stuff != null) {
                            String lines[] = stuff.split("\\r?\\n");
                            if (lines != null) {
                                for (int i = 0 ; i < lines.length ; i++) {
                                    String line = lines[i];
                                    if (line != null) {
                                        line = line.trim();
                                        if (line.length() > 0 && line.charAt(0) != '#') {
                                            List<String> tokens = Util.csvToList(line);
                                            if (tokens != null && tokens.size() == 2) {
                                                String cname = tokens.get(0);
                                                String pname = tokens.get(1);
                                                // System.out.println("Null index: " + cname + ", " + pname);
                                                List<String> props = _nullIndexes.get(cname);
                                                if (props == null) {
                                                    props = new ArrayList<String>();
                                                    _nullIndexes.put(cname, props);
                                                }
                                                props.add(pname);
                                            }   
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (Throwable t) {
                log.error("Unable to read null index declaration file");
                log.error(t);
            }
        }
        return _nullIndexes;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Main
    //
    ////////////////////////////////////////////////////////////////////////////

    private static void usage() {
        System.err.println("Usage: SailPointSchemaGenerator " +
                           "<type> <delimiter> <create-file> <drop-file>");
    }

    public static void main(String [] args) throws Exception {
        if ( args.length != 5 ) {
            usage();
            System.exit(1);
        }
        generate(args[0], args[1], null, args[2], args[3]);
    }
}  // class SailPointSchemaGenerator
