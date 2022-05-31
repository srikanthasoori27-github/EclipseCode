/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
*/
public class ErrorResponse {

    public static final String SYS_ERROR = "system";

    private String error;
    private String errorMsg;

    public ErrorResponse(String error, String errorMsg) {
        this.error = error;
        this.errorMsg = errorMsg;
    }

    public String getError() {
        return error;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
