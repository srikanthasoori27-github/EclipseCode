/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

/**
 */
public class Response {

    public static final String SYS_ERROR = "system";

    private boolean success;
    private String error;
    private String errorMsg;

    public Response(boolean success, String error, String errorMsg) {
        this.success = success;
        this.error = error;
        this.errorMsg = errorMsg;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
