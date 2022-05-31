/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A helper class to dig into the Beanshell exception mess and try
 * to dig out the grain of interesting information.  Split out of
 * Workflower since we need it in a few places.
 * 
 * Author: Jeff
 * 
 * NOTE: You should only be calling this if you can get a Beanshell
 * exception.  It isn't smart about localization of GeneralExceptions
 * containing Messages.
 *
 */

package sailpoint.api;

public class ExceptionCleaner {

    /**
     * Try to dig just the relevant stuff out of a Beanshell exception mess.
     * 
     * Typical Beanshell error looks like:
     * 
     * java.security.PrivilegedActionException: org.apache.bsf.BSFException: 
     * BeanShell script error: Sourced file: inline evaluation of: 
     * ``approvalObject.getType();'' : Attempt to resolve method: getType() 
     * on undefined variable or class name: approvalObject : at Line: 1 : 
     * in file: inline evaluation of: ``approvalObject.getType();'' : 
     * approvalObject .getType ( ) 
     *
     * Nice huh.  If we find "inline evaluation" we'll assume we have
     * one of these and return something from the middle of it.  Otherwise
     * we just return the toString of the exceptino.
     */
    public static String getString(Throwable t) {

        String msg = t.toString();

        if (msg.indexOf("inline evaluation") > 0) {

            // search up to the terminating quotes on the first
            // "inline evaluation of" section
            String token = "'' : ";
            int psn = msg.indexOf(token);
            if (psn > 0) {
                // then to the colon after "at Line: x :"
                msg = msg.substring(psn + token.length());
                token = "at Line:";
                psn = msg.indexOf(token);
                if (psn > 0) {
                    psn = msg.indexOf(":", psn + token.length());
                    if (psn > 0) 
                        msg = msg.substring(0, psn);
                }
            }
        }
        return msg;
    }


    /**
     * Remove our wrappers out of the exception messages to help
     * cleanup the test connection messages. There are two
     * general wrappers we can remove, exceptions starting with
     * sailpoint.connector or sailpoint.tools.GeneralException.
     *
     * Idea is to go from:
     *  sailpoint.connector.ConnectorException: sailpoint.tools.GeneralException: WEB-INF\config\demo\data\PlatformApp-UsersData.csv (The system cannot find the path specified)
     *
     * to:
     *   WEB-INF\config\demo\data\PlatformApp-UsersData.csv (The system cannot find the path specified)
     *
     * ref: BUG#5224
     */
    public static String cleanConnectorException(Throwable th){
        String msg = null;
        if ( th != null ) {
           String exception = th.getMessage();
           if ( exception != null ) {
               int index = exceptionIndex(exception);
               while ( index != -1 ) {
                   int colon = exception.indexOf(":", index);
                   if ( colon != -1 ) {
                       exception = exception.substring(colon+1);
                       index = exceptionIndex(exception);
                   } else {
                       // shouldn't happen but if we can't find a : just bail
                       index = -1;
                   }
               }
               msg = exception;
           }
        }
        return msg;
    }

    private static int exceptionIndex(String exception ) {
        int index = -1;
        if ( exception != null ) {
            index = exception.indexOf("sailpoint.tools.GeneralException");
            if ( index == -1 ) {
                index = exception.indexOf("sailpoint.connector.");
            }
        }
        return index;
    }

}
