package sailpoint.tools;

import sailpoint.web.messages.MessageKeys;

/**
 * Exception for invalid parameter
 * Use in REST services when params are missing or invalid
 */
public class InvalidParameterException extends GeneralException {

    private String parameter;
    
    public InvalidParameterException() {
        super();
    }

    /**
     * Construct an exception based on the name of the invalid parameter
     * @param parameter Parameter name
     */
    public InvalidParameterException(String parameter) {
        super(new Message(MessageKeys.ERR_INVALID_PARAMETER_EXCEPTION, parameter));
        this.parameter = parameter;
    }

    /**
     * Construct an exception based on the name of the invalid parameter, 
     * with an inner exception
     * @param parameter Parameter name
     * @param t Inner exception
     */
    public InvalidParameterException(String parameter, Throwable t) {
        super(new Message(MessageKeys.ERR_INVALID_PARAMETER_EXCEPTION, parameter), t);
        this.parameter = parameter;
    }

    /**
     * Construct an exception with a custom message
     * @param msg Message
     */
    public InvalidParameterException(Message msg) {
        super(msg);
    }

    /**
     * Construct an exception with a custom message and inner exception
     * @param msg Message
     * @param t Inner exception
     */
    public InvalidParameterException(Message msg, Throwable t) {
        super(msg, t);
    }

    /**
     * Get the parameter name, if defined in constructor
     * @return Parameter name
     */
    public String getParameter() {
        return this.parameter;
    }

    /**
     * Set the parameter name. This will NOT reset the message.
     * @param parameter Parameter name.
     */
    public void setParameter(String parameter) {
        this.parameter = parameter;
    }
}