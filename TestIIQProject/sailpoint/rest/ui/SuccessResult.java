package sailpoint.rest.ui;

/**
 * Simple result object for REST POST methods indicating success/failure,
 * with optional message
 */
public class SuccessResult {
    private boolean success;
    private String message;

    public SuccessResult() {}

    public SuccessResult(boolean success) {
        this.success = success;
    }

    /**
     * Get the success value
     */
    public boolean isSuccess() {
        return this.success;
    }

    /**
     * Set the success value
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Get the message value
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Set the message value
     */
    public void setMessage(String message) {
        this.message = message;
    }
}