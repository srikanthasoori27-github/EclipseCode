/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * An interface that must be implemented by any class that is
 * to be dynamically loaded from a Rule containing Java.
 */

package sailpoint.object;

public interface JavaRuleExecutor {

    public Object execute(JavaRuleContext context) throws Exception;

}
