/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.suggest;

import sailpoint.object.Configuration;
import sailpoint.tools.Util;

import java.util.List;

/**
 * Suggest authorizer context that is for a "global" suggest. This is used when the suggest
 * endpoint is hit directly instead of through another authorized resource, and uses the configuration
 * values for lists of allowed classes and columns.
 */
public class GlobalSuggestAuthorizerContext implements SuggestAuthorizerContext {

    @Override
    public boolean isClassAllowed(String className) {
        Configuration configuration = Configuration.getSystemConfig();
        List<String> classList = configuration.getStringList(Configuration.SUGGEST_OBJECT_WHITELIST);
        return caseInsensitiveContains(classList, getClassName(className));
    }

    @Override
    public boolean isColumnAllowed(String className, String columnName) {
        String columnEntry = getClassName(className) + "." + columnName;
        String allEntry = getClassName(className) + ".*";

        Configuration configuration = Configuration.getSystemConfig();
        List<String> columnList = configuration.getStringList(Configuration.SUGGEST_COLUMN_WHITELIST);
        return caseInsensitiveContains(columnList, columnEntry) || Util.nullSafeContains(columnList, allEntry);
    }

    private String getClassName(String className) {
        return className.startsWith(SAILPOINT_OBJECT_PACKAGE) ? className.substring(SAILPOINT_OBJECT_PACKAGE.length()) : className;
    }

    private boolean caseInsensitiveContains(List<String> stringList, String string) {
        for (String stringEntry : Util.iterate(stringList)) {
            if (Util.nullSafeCaseInsensitiveEq(stringEntry, string)) {
                return true;
            }
        }

        return false;
    }
}
