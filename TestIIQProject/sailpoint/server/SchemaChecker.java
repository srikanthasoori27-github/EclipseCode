/**
 * This tool is going to extract the differences between create_identityiq_tables-X.N.db and
 * create_identityiq_tables-X.N-1.db. Thus, it will make sure that these differences has been added
 * to the file upgrade_identityiq_tables.db
 */
package sailpoint.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.persistence.SailPointSchemaGenerator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

enum Type {
    MySQL(SailPointSchemaGenerator.TYPE_MYSQL),
    Oracle(SailPointSchemaGenerator.TYPE_ORACLE),
    Db2(SailPointSchemaGenerator.TYPE_DB2),
    SqlServer(SailPointSchemaGenerator.TYPE_SQLSERVER);

    private final String name;

    Type(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}

public class SchemaChecker {

    static private Log log = LogFactory.getLog(SchemaChecker.class);

    /*
     * It points to the full path of upgrade_identityiq_tables (except the type)
     * e.g. /identityiq_8.0_develop/build/WEB-INF/database/upgrade_identityiq_tables.
     */
    private String upgradeFileName;

    /*
     * It points to the full path of the new upgrade_identityiq_tables (except the type)
     * e.g. /identityiq_8.0_develop/build/WEB-INF/database/upgrade_identityiq_tables.SchemaChecker.
     */
    private String newUpgradeFileName;

    /*
     * It points to the full path of the latest creation script (except the type)
     * e.g. /identityiq_8.0_develop/build/WEB-INF/database/create_identityiq_tables-8.0.
     */
    private String createFileName;

    /*
     * It holds the latest version of IIQ
     * e.g. -8.0.
     */
    private String iiqVersion;

    /*
     * It points to the full path of source code folder
     * e.g. /identityiq_8.0_develop/src
     */
    private String srcPath;

    /*
    * This represents the name of a file used to skip sql statements that
    * we don't want to include in the upgrade_identityiq_tables
    * e.g. /identityiq_8.0_develop/src/database/
    */
   private String skipStatementFile;

    /*
     * It points to the full path of build folder
     * e.g. /identityiq_8.0_develop/build
     */
    private String buildPath = "";

    private static boolean PR_FAIL = false;

    SchemaChecker(String iiqVersion, String srcPath, String buildPath) {
        if (Util.isNotNullOrEmpty(iiqVersion)) {
            this.iiqVersion = "-" + iiqVersion;
        }

        if (Util.isNotNullOrEmpty(srcPath)) {
            this.skipStatementFile = srcPath + "/database/skip_statements_schema_checker.";
            this.srcPath = srcPath + "/database/baselines/";
        }

        if (Util.isNotNullOrEmpty(buildPath)) {
            this.buildPath = buildPath + "/WEB-INF/database/";
            this.upgradeFileName = this.buildPath + "upgrade_identityiq_tables" + ".";
            this.newUpgradeFileName = this.upgradeFileName + "SchemaChecker.";
            this.createFileName = this.buildPath + "create_identityiq_tables" + this.iiqVersion + ".";
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: SchemaChecker <IIQ Version> <src path> <build path>");
            System.exit(1);
        }

        SchemaChecker schemaChecker = new SchemaChecker(args[0], args[1], args[2]);
        schemaChecker.checkSchemas();

        if (PR_FAIL) {
            StringBuffer sb = new StringBuffer();
            sb.append(" # # # # # # # # # # # # # # #    E R R O R     # # # # # # # # # # # # # #").append("\n");
            sb.append("If you are seeing this message it means your PR is failing because ").append("\n");
            sb.append("the SchemaChecker has detected some missing statements in upgrade scripts.").append("\n");
            sb.append("Please include these statements in one of the 'upgrade_identityiq_tables' files").append("\n");
            sb.append("If you don't want to include these statements in the upgrade files, you can").append("\n");
            sb.append("add these statements to the file skip_statements_schema_checker and they will").append("\n");
            sb.append("be excluded and your PR will run successfully.").append("\n");
            try {
                throw new Exception(sb.toString());
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                System.exit(1);
            }
        }
    }

    public void checkSchemas() throws Exception {
        for (Type type : Type.values()) {
            System.out.println("Checking " + type + " ...");
            printStatements(type, getUpgradeStatements(type));
        }
    }

    private void printStatements(Type type, StringBuffer statements) throws Exception {
        if (statements.length() == 0) {
            System.out.println("... [OK]");
            return;
        } else if (statements.length() < 5000) {
            PR_FAIL = true;
            System.out.println("[There are some missed statements in upgrade_identityiq_tables." 
            + type.getName() +"]");
            System.out.println(statements);
        } else {
            PR_FAIL = true;
            System.out.println("There are a lot of missed statements, please see more details "
            + "in file upgrade_identityiq_tables-SchemaChecker." + type.getName());
        }
        printlToFile(type, statements);
    }

    private void printlToFile(Type type, StringBuffer statements) throws Exception {
        //Removing previous file built by SchemaChecker
        Helper.deleteFile(this.newUpgradeFileName + type.getName());

        //Writing the content to a file
        Util.writeFile(this.newUpgradeFileName + type.getName(), statements.toString());
    }

    /**
     * @param type - it represents the data base
     * @return It will return a StringBuffer with all missed statements in the upgrade file.
     * @throws Exception
     */
    private StringBuffer getUpgradeStatements(Type type) throws Exception {
        //The previousStatements represents the file from previous version (7.3pX)
        File previousStatementsFile = Helper.findFileByType(type, this.srcPath);

        //The latestStatements represents the latest file generated via "ant schema"
        //it should be something like (7.3pX+1)
        File latestStatementsFile = new File(this.createFileName + type.getName());

        //We need to parse each file to Statements
        Statements latestStatements = parseFileToStatements(latestStatementsFile, type);
        Statements previousStatements = parseFileToStatements(previousStatementsFile, type);

        //We need to extract the differences between previous file and the current file
        Statements differences = compareStatements(previousStatements, latestStatements);

        //Reading the current upgrade file
        String upgradeStatements = Helper.getFileContent(this.upgradeFileName, type);

        //Compare the differences extracted from latest statements and previous statements
        //vs the current upgrade statements
        List<String> newUpgradeStatements = new ArrayList<String>();
        for (Statement statement : differences.getStatements()) {
            newUpgradeStatements.addAll(statement.getNewUpgradeStatements(upgradeStatements));
        }

        //TODO remove this call after IIQ 8.X
        //This is a workaround to remove unnecessary statements
        newUpgradeStatements = previousStatements.removeRedundantStatements(newUpgradeStatements, type);
        //end of workaround

        StringBuffer missedStatements = new StringBuffer();
        String statementsToSkip = Helper.getFileContent(this.skipStatementFile, type);
        for (String st : newUpgradeStatements) {
            if (st.contains(";")) {
                st = st.trim() + "\n\n";
            } else {
                st = st.trim() + ";\n\n";
            }
            if (!Pattern.matches(Helper.buildRegex(st), statementsToSkip)) {
                missedStatements.append(st);
            }
        }

        return missedStatements;
    }

    /**
     * @param previousFile - the file from previous version
     * @param latestFile - the latest file generated via "ant schema"
     * @return A list of Statements that represents the new changes in latestFile
     * @throws GeneralException
     */
    private Statements compareStatements(Statements previousStatements, Statements latestStatements) throws GeneralException {
        String statementToCompare = previousStatements.toString();
        Statements differences = new Statements();
        for (Statement statement : latestStatements.getStatements()) {
            if (!statement.matches(statementToCompare)) {
                differences.add(statement);
                log.debug("-- Statement with changes detected --");
                log.debug(statement.getStatement());
            }
        }
        return differences;
    }

    /**
     * @param file - The file to be parse to a List of statements.
     * @return A list of statements
     */
    private Statements parseFileToStatements(File file, Type type) {
        InputStream inputStream = null;
        Reader reader = null;
        BufferedReader bufferedReader = null;
        Statements statements = new Statements();
        try {
            String line;
            inputStream = new FileInputStream(file);
            reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            bufferedReader = new BufferedReader(reader);
            List<String> expressionLines = new ArrayList<String>();
            while ((line = bufferedReader.readLine()) != null) {
                if (Helper.skipLine(line)) {
                    continue;
                }
                line = line.toLowerCase().trim();
                expressionLines.add(line);
                if (Helper.isEndOfExpression(line)) {
                    statements.add(new Statement(expressionLines, type));
                    expressionLines = new ArrayList<String>();
                }
            }
        } catch (IOException ie) {
            log.error(ie);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Throwable t) { /* ensure close happens */ }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable t) { /* ensure close happens */ }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable t) { /* ensure close happens */ }
            }
        }
        return statements;
    }
}

class Statements {
    List<Statement> statements;
    public Statements() {
        statements = new ArrayList<Statement>();
    }

    public Statements(Statement e) {
        statements = new ArrayList<Statement>();
        add(e);
    }

    public List<Statement> getStatements() {
        return statements;
    }

    public void setStatements(List<Statement> statements) {
        this.statements = statements;
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        for (Statement statement : statements) {
            result.append(statement.getStatement()).append("\n");
        }
        return result.toString();
    }

    public void add(Statement e) {
        statements.add(e);
    }

    public void sort() {
        Collections.sort(statements);
    }

    /**
     * Method that will remove redundant statements created because of the hibernate migration.
     * TODO Remove this method after IIQ 8.X
     * @param newUpgradeStatements
     * @param type
     * @return
     */
    public List<String> removeRedundantStatements(List<String> newUpgradeStatements, Type type) {
        List<String> toRemove = new ArrayList<String>();
        String statementToCompare = toString();
        List<String> newUpgradeStatementsCopy = new ArrayList<>(newUpgradeStatements);
        for (String st : newUpgradeStatements) {
            newUpgradeStatementsCopy.remove(st);
            if (st.contains("alter table") && !st.contains("add constraint")) {
                String arr[] = st.split(" ");
                StringBuffer firstPart = new StringBuffer();
                for (int i=0; i<3; i++) {
                    firstPart.append(arr[i]);
                    if (i<2) {
                        firstPart.append(" ");
                    } else {
                        firstPart.append("\n").append("add constraint");
                    }
                }

                StringBuffer secondPart = new StringBuffer();
                secondPart.append("unique").append(" (").append(arr[4]).append(")");

                String regex1 = Helper.buildRegex(firstPart.toString(), secondPart.toString());

                firstPart = new StringBuffer();
                firstPart.append("create table").append(" ");
                firstPart.append(arr[2]).append(" (\n");

                secondPart = new StringBuffer();
                for (int i=4; i<arr.length; i++) {
                    secondPart.append(arr[i].trim().replaceAll(";", "")).append(" ");
                }
                secondPart.append("unique");

                String regex2 = Helper.buildRegex(firstPart.toString(), secondPart.toString());
                for (String newStr : newUpgradeStatementsCopy) {
                    if (Pattern.matches(regex1.toString(), newStr) &&
                            Pattern.matches(regex2.toString(), statementToCompare)) {
                        toRemove.add(st);
                        toRemove.add(newStr);
                    }
                }
            } else if (type == Type.SqlServer && st.contains("alter table") && st.contains("add constraint")) {
                String arr[] = Helper.convertToOneLine(st).split(" ");
                StringBuffer firstPart = new StringBuffer();
                firstPart.append("create table").append(" ");
                firstPart.append(arr[2]).append(" (\n");

                StringBuffer secondPart = new StringBuffer();
                String fieldName = arr[7].replace("(", "").replace(")", "").replace(";", "");
                secondPart.append(fieldName).append(" ");
                secondPart.append("nvarchar(128) not null unique");

                String regex = Helper.buildRegex(firstPart.toString(), secondPart.toString());

                if (Pattern.matches(regex, statementToCompare)) {
                    toRemove.add(st);
                } else {
                    regex = regex.replace("128", "255");
                    if (Pattern.matches(regex, statementToCompare)) {
                        toRemove.add(st);
                    }
                }
            }
        }
        newUpgradeStatements.removeAll(toRemove);
        return newUpgradeStatements;
    }
}

class Statement implements Comparable<Statement> {
    private StringBuffer statement;
    private List<Field> fields;
    private boolean singleExpression;
    private List<String> expressionLines;
    private List<String> regex;
    private Type type;

    static private Log log = LogFactory.getLog(Statement.class);

    public Statement(List<String> expressionLines, Type type) {
        this.expressionLines = new ArrayList<String>();
        this.statement = new StringBuffer();
        for (String line : expressionLines) {
            line = Helper.removeUnnecessarySpaces(line);
            this.expressionLines.add(line);
            this.statement.append(line).append("\n");
        }
        this.singleExpression = expressionLines.size() == 1;
        this.fields = new ArrayList<Field>();
        this.regex = new ArrayList<String>();
        buildRegex();
        this.type = type;
    }

    public List<String> getRegex() {
        return this.regex;
    }

    public List<String> getExpressionLines() {
        return expressionLines;
    }

    public boolean isSingleExpression() {
        return singleExpression;
    }

    public void setSingleExpression(boolean singleExpression) {
        this.singleExpression = singleExpression;
    }

    public String getHeader() {
        return expressionLines.get(0);
    }

    public String getFooter() {
        return expressionLines.get(expressionLines.size() -1);
    }

    public String getStatement() {
        return statement.toString();
    }

    public void setStatement(String statement) {
        this.statement = new StringBuffer(statement);
    }

    public List<Field> getFields() {
        return fields;
    }

    /**
     * @param statements These are the statements in upgrade script
     * @return a list of statements that are not in upgrade script
     */
    public List<String> getNewUpgradeStatements(String upgrade) {
        List<String> upgradeStatements = new ArrayList<String>();
        for (Statements statements : buildUpgradeStatements()) {
            //after having the new build statements, we need to verify
            //if they are defined in upgrade_identityiq_tables.type
            boolean isOk = false;
            for (Statement statement : statements.getStatements()) {
                if (statement.matches(upgrade)) {
                    isOk = true;
                }
            }
            if (!isOk) {
                upgradeStatements.add(statements.getStatements().get(0).getStatement());
            }
        }
        return upgradeStatements;
    }

    /**
     * This method will build the new possible upgrade statements
     * @return a list of Statements
     */
    private List<Statements> buildUpgradeStatements() {
        List<Statements> upgradeStatements = new ArrayList<Statements>();
        int count = 0;
        String arr[] = getHeader().trim().split(" ");
        for (Field field : getFields()) {
            if (field.isChanged()) {
                if ((count == 0 && field.getName().indexOf("create table") >= 0) ||
                     isSingleExpression() || getHeader().indexOf("alter table") >= 0) {
                    upgradeStatements.add(new Statements(new Statement(getExpressionLines(), type)));
                    break;
                } else {
                    Statements statements = new Statements();
                    StringBuffer updateStatement = new StringBuffer();
                    updateStatement.append("alter table ").append(arr[2]).append(" ");
                    String name = field.getName().trim();
                    name = name.substring(0, name.lastIndexOf(","));
                    if (field.getOperation() == 'N') {
                        updateStatement.append("add").append(" ").append(name).append("");
                        statements.add(new Statement(Arrays.asList(updateStatement.toString()), type));

                        String synonym = updateStatement.toString().replaceAll(" add ", " add column ");
                        statements.add(new Statement(Arrays.asList(synonym), type));
                    } else {
                        updateStatement.append("modify").append(" ").append(name).append("");
                        statements.add(new Statement(Arrays.asList(updateStatement.toString()), type));
                    }
                    upgradeStatements.add(statements);
                }
            }
            count++;
        }
        return upgradeStatements;
    }

    /**
     * Allows to find the current statement in a String
     * @param statements - A list of statements such as upgrade/creation file scripts
     * @return
     */
    public boolean matches(String statements) {
        boolean allOk = true;
        int count = 0;
        for (String regex : getRegex()) {
            StringBuffer debug = new StringBuffer();
            Field field = new Field();
            if (count < getExpressionLines().size()) {
                field.setName(getExpressionLines().get(count));
            } else {
                field.setName(getHeader());
            }

            debug.append(regex);
            if (!Pattern.matches(regex, statements)) {
                debug.append("[").append("Upgrade detected").append("]");
                allOk = false;
                field.setChanged(true);
                if (count > 0) {
                    String arr[] = field.getName().split(" ");
                    if (Pattern.matches(Helper.buildRegex(getHeader(), arr[0]), statements)) {
                        if (type == Type.SqlServer) {
                            if (Pattern.matches(Helper.buildRegex(getHeader(), arr[0] + " null"), statements)) {
                                // There was an upgrade to the field
                                field.setOperation('U');
                            } else {
                                field.setChanged(false);
                            }
                        } else {
                            // There was an upgrade to the field
                            field.setOperation('U');
                        }
                    } else {
                        // The field is new
                        field.setOperation('N');
                    }
                }
            }
            log.debug(debug);
            if (");".equals(field.getName())) {
                field.setChanged(false);
            }
            getFields().add(field);
            count++;
        }
        return allOk;
    }

    /**
     * For each new Statement we are building all possible regex
     */
    private void buildRegex() {
        if (isSingleExpression()) {
            if (getStatement().trim().indexOf("create index") == 0) {
                buildSpecialRegex(getStatement(), null, 3);
            } else if (getStatement().trim().indexOf("create sequence") == 0) {
                buildSpecialRegex(getStatement(), null, 6);
            } else {
                getRegex().add(Helper.buildRegex(getStatement()));
            }
        } else {
            int count = 0;
            for (String line : getExpressionLines()) {
                if (count == 0) {
                    getRegex().add(Helper.buildRegex(line));
                } else {
                    if (line.trim().indexOf("add constraint") == 0) {
                        buildSpecialRegex("add constraint",getHeader(), 3);
                        buildSpecialRegex(line,getHeader(), 3);
                    } else {
                        getRegex().add(Helper.buildRegex(getHeader(), line));
                    }
                }
                count++;
            }
        }
    }

    private void buildSpecialRegex(String line, String header, int elementToSkip) {
        StringBuffer firstLine = new StringBuffer();
        StringBuffer secondLine = new StringBuffer();
        String arr[] = line.trim().split(" ");
        if (header == null) {
            for (int i = 0; i < elementToSkip - 1; i++) {
                firstLine.append(arr[i]).append(" ");
            }
            for (int i = elementToSkip; i < arr.length - 1; i++) {
                secondLine.append(arr[i]).append(" ");
            }
        } else if (arr.length > elementToSkip) {
            for (int i = elementToSkip; i < arr.length; i++) {
                secondLine.append(arr[i]).append(" ");
            }
            firstLine = new StringBuffer(header);
        }
        getRegex().add(Helper.buildRegex(firstLine.toString(), secondLine.toString()));
    }

    @Override
    public int compareTo(Statement st) {
        return this.statement.toString().compareTo(st.getStatement().toString());
    }
}

class Field {
    char operation;
    String name;
    boolean changed;

    public char getOperation() {
        return operation;
    }

    public void setOperation(char operation) {
        this.operation = operation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public String toString() {
        return "Name: "+ name + " \n " + "Changed: "+changed;
    }
}

class Helper {
    static private Log log = LogFactory.getLog(Helper.class);

    @SuppressWarnings("finally")
    public static String getFileContent(String fileName, Type type) {
        //Reading the content of a file (if it exists)
        String content = "";
        try {
            content = Util.readFile(fileName + type.getName()).toLowerCase();
        } catch (GeneralException ge) {
            //This should not happen, catching just in case the file doesn't exist
            try {
                //if we cannot find the file from the provided fileName,
                //we are going to try to find the best that matches the name.
                content = findAndGetFileContent(fileName, type);
            } catch (GeneralException e) {
                log.error(e);
            }
        } finally {
            return content;
        }
    }

    /*
     * This is a convenience method to try to get the content
     * from the correct upgrade file for X version patch.
     * Basically we are going to find the start of the name
     * and the end of the name.
     * upgrade_identityiq_tables-XXXXXdb
     */
    public static String findAndGetFileContent(String fileName, Type type) throws GeneralException {
        // Reading the content of a file (if it exists)
        String content = "";

        //splitting the full path to get the file name
        String[] arr = fileName.split("/");

        // this is the file name
        String fileNameDB = arr[arr.length - 1];
        arr[arr.length - 1] = fileNameDB.substring(0, fileNameDB.length() - 1) + "-";

        //pointing to the correct folder path to look for the file
        String folderPath = fileName.substring(0, fileName.length() - arr[arr.length - 1].length());
        File path = new File(folderPath);
        File[] listFiles = path.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String name = file.getName().toLowerCase();

                //here we are checking for the correct file
                return name.startsWith(arr[arr.length - 1]) && name.endsWith(type.getName().toLowerCase());
            }
        });
        if (listFiles.length > 0) {
            //if we have found the file, let's read the content
            content = Util.readFile(listFiles[0]).toLowerCase();
        } else {
            throw new GeneralException("Error reading the file content " + fileName + "X.XpX." + type.getName());
        }

        return content;
    }

    public static String buildRegex(String arg) {
        return "(?s)(.*?" + Helper.prepareRegex(arg) + ")(.*?)";
    }

    public static String convertToOneLine(String lines) {
        StringBuffer sb = new StringBuffer();
        String arr[] = lines.split("\n");
        for (String st : arr) {
            sb.append(st).append(" ");
        }
        return sb.toString().trim();
    }

    public static String buildRegex(String arg1, String arg2) {
        StringBuffer regex = new StringBuffer();
        regex.append("(?s)(.*?").append(prepareRegex(arg1)).append("[^;]*?");
        regex.append(prepareRegex(arg2)).append(".*?;).*?");
        return regex.toString();
    }

    private static String prepareRegex(String str) {
        str = safeParentheses(str.replaceAll("\\*", "\\\\*").trim());
        return str;
    }

    public static String safeParentheses(String str) {
        return str.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)").replaceAll("\\[", "\\\\[").replaceAll("\\]","\\\\]");
    }

    public static boolean skipLine(String line) {
        if (line == null || line.length() == 0 || line.trim().startsWith("--")
                || line.trim().startsWith("#") || line.trim().startsWith("warnings;")
                || line.trim().startsWith("nowarning;")) {
            return true;
        }
        return false;
    }

    public static String removeUnnecessarySpaces(String line) {
        String arr[] = line.split(" ");
        StringBuffer sb = new StringBuffer();
        for (int i=0; i < arr.length; i++) {
            if (!Util.isNullOrEmpty(arr[i])) {
                sb.append(arr[i]).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static boolean isEndOfExpression(String line) {
        if (line.endsWith(";") || line.equals("go")) {
            return true;
        }
        return false;
    }

    public static File findFileByType(final Type type, String location) throws Exception {
        File path = new File(location);
        File[] listFiles = path.listFiles(new FileFilter() {

            @Override
            public boolean accept(File file) {
                String name = file.getName().toLowerCase();
                return name.endsWith(type.getName());
            }
        });

        if (listFiles.length == 1) {
            return listFiles[0];
        } else {
            throw new FileNotFoundException("Couldn't find the file type: " + type + " in location: " + location);
        }
    }

    public static void deleteFile(String name) {
        File file = new File(name);
        file.delete();
    }
}