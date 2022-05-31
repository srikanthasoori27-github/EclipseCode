
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;

/**
 * Executes a SQL script line-by-line.
 *
 * This class is far from a full SQL compiler. It will
 * execute statements terminated by a semicolon or a GO statement.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class SqlScriptExecutor {

    /**
     * The log.
     */
    private static final Log LOG = LogFactory.getLog(SqlScriptExecutor.class);

    /**
     * Executes a SQL script on the specified connection.
     *
     * @param connection The connection.
     * @param script The script.
     * @throws GeneralException
     */
    public void execute(Connection connection, String script) throws GeneralException {
        BufferedReader scriptReader = null;

        try {
            scriptReader = new BufferedReader(new StringReader(script));

            StringBuilder statementBuilder = new StringBuilder();

            String line;
            boolean inBlock = false;

            while ((line = scriptReader.readLine()) != null) {
                boolean execute = false;
                boolean append = true;

                line = line.trim();
                if (!isIgnoredLine(line)) {
                    line = stripCommentsAndTrim(line);
                    if(!inBlock) {
                        if (isSemiColonTerminated(line)) {
                            line = line.substring(0, line.length() - 1);
                            execute = true;
                        } else if (isGOLine(line)) {
                            append = false;
                            execute = true;
                        }
                    }

                    if (append) {
                        statementBuilder.append(" ").append(line);
                    }

                    if (execute) {
                        executeStatement(connection, statementBuilder.toString());
                        statementBuilder.setLength(0);
                    }
                } else if(isStartBlock(line)) {
                    inBlock = true;
                } else if(isEndBlock(line)) {
                    executeStatement(connection, statementBuilder.toString());
                    statementBuilder.setLength(0);
                    inBlock = false;
                }
            }
        } catch (IOException e) {
            LOG.debug("An error occurred while reading the SQL script", e);
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(scriptReader);
        }
    }

    /**
     * Executes the statement.
     *
     * @param connection The connection.
     * @param statement The statement.
     * @throws GeneralException
     */
    private void executeStatement(Connection connection, String statement) throws GeneralException {
        if (Util.isNullOrEmpty(statement)) {
            return;
        }

        JdbcUtil.sql(connection, statement);
    }

    /**
     * Determines if the line is terminated by a semicolon.
     *
     * @param line The line.
     * @return True if terminated by semicolon.
     */
    private boolean isSemiColonTerminated(String line) {
        return line.endsWith(";");
    }

    /**
     * Determines if this line is a GO line. This statement can be used
     * as a terminator for statements in MS SQL Server scripts.
     *
     * @param line The line.
     * @return True if GO line.
     */
    private boolean isGOLine(String line) {
        return line.toUpperCase().startsWith("GO");
    }

    /**
     * Determines if the line should be ignored when running a sql script. This
     * method pre-supposes that the line has been trimmed and upper-cased.
     *
     * @param line The line
     * @return True if ignored, false otherwise.
     */
    private boolean isIgnoredLine(String line) {
        return Util.isNullOrEmpty(line) || line.startsWith("--");
    }

    private boolean isStartBlock(String line) {
        return line.startsWith("--") && line.contains("sp-start-block");
    }

    protected String stripCommentsAndTrim(String line) {
        // quick test for mid line comments ( -- in the middle of a line that comments out the
        // rest of the line).  We are just looking for a single or double quote in the comment.
        // this should catch all commented double dashes, but could lead to a false positive.
        // Given the differences between all of the SQL implementations, it seems like a safe way
        // to go, and if there is a false positive, you could just rearrange your comments to either
        // be at the beginning of the line, or not include quotes.  This is better than goofing the
        // actual SQL because we were trying to be smart about parsing it.
        int lastOccurance = line.indexOf("--");
        while(lastOccurance >= 0) {
            String potentialComment = line.substring(lastOccurance);
            if((potentialComment.contains("'")) || (potentialComment.contains("\""))) {
                lastOccurance = line.indexOf("--", lastOccurance + 1);
            } else {
                return line.substring(0, lastOccurance).trim();
            }
        }

        return line.trim();
    }

    private boolean isEndBlock(String line) {
        return line.startsWith("--") && line.contains("sp-end-block");
    }

}
