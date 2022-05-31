/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.suggest;

public interface SuggestAuthorizerContext {

    String SAILPOINT_OBJECT_PACKAGE = "sailpoint.object.";

    boolean isClassAllowed(String className);

    boolean isColumnAllowed(String className, String columnName);
}
