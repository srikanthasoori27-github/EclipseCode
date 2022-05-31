/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// Author(s): Jeff Larson
//
// Description:
//
// Common base class for application exceptions.
//
// Serves as a wrapper so we don't have to declare all possible
// throws and provides a convenient debugger breakpoint.
//
// Eventually can support I18N if necessary.
//

package sailpoint.tools;

import java.util.Locale;
import java.util.TimeZone;

import sailpoint.web.messages.MessageKeys;

/**
 * The base exception thrown by components.
 *
 */
public abstract class AbstractLocalizableException extends Exception 
       implements Localizable {

    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The final message. It may consist of combination possible suggestion and
     * detailed error.
     */
    private Message message;

    /**
     * The detailed error message
     */
    private Message detailedError;

    /**
     * Contains hints to solve the error (detailedError)
     */
    private Message possibleSuggestion;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    protected AbstractLocalizableException() {
        super();	
    }
    
    protected AbstractLocalizableException(String str) {
        super(str);
        this.message = new Message(Message.Type.Error, str);
    }
    
    protected AbstractLocalizableException(Message message) {
        super();
        this.message = message;        
    }
    
    protected AbstractLocalizableException(Message message, Throwable t) {
        super(t);
        this.message = message;        
    }
    
    protected AbstractLocalizableException(Throwable t) {
        super(t);
    }

    /**
     * Create a new AbstractLocalizableException from an error text, suggestion
     * text and existing exception.
     * <p>
     * The error and suggestion being set here is used later to prepare well
     * formatted exception message based on resource bundle key
     * 'msg_with_suggestion' and 'msg_no_suggestion'.
     *
     * @param error
     *            ResourceBundle message key, or a plain text message containing
     *            error details.
     * @param suggestion
     *            ResourceBundle message key, or a plain text message containing
     *            hints to solve the error.
     * @param t
     *            Throwable object t can be provided at the time of calling
     *            following constructor.
     */
    protected AbstractLocalizableException(String error, String suggestion,
                                           Throwable t) {
        super(t);

        if (error != null) {
            setDetailedError(error);
        }

        if (suggestion != null) {
            setPossibleSuggestion(suggestion);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public void setLocalizedMessage(Message message) {
        this.message = message;
    }

    public String getLocalizedMessage(Locale locale, TimeZone timezone) {
        // @ignore
        // The final message must comply
        // with the standardized message format.
        complyMessageStandard();

        if (message != null)
            return message.getLocalizedMessage(locale, timezone);

        return getCauseMessage();
    }

    private String getCauseMessage() {
        
        String msg = "";
        Throwable cause = super.getCause();
        if (cause != null) {
            msg = cause.getLocalizedMessage();
            if (msg == null) {
                // common for things like NullPointerException, use the class name?
                // hmm, maybe it's best just to say "System Exception" so we don't
                // confuse the users?
                msg = cause.getClass().getSimpleName();
                if (msg == null) {
                    // it looks stupid not to say anything
                    msg = "Unknown Exception";
                }
            }
        }
        return msg;
    }

    public String getLocalizedMessage() {
        // @ignore
        // The final message must comply
        // with the standardized message format.
        complyMessageStandard();

        if (message != null)
            return message.getLocalizedMessage();

        return getCauseMessage();
    }

    public String getMessage() {
        // @ignore
        // The final message must comply
        // with the standardized message format.
        complyMessageStandard();

        if (message != null)
            return message.getMessage();

        return getCauseMessage();
    }

    /**
     * Returns the actual Message object so it can be added to
     * the message list on another object.
     * @return Message instance for this exception or null.
     */
    public Message getMessageInstance(){
        if (message == null)
            return new Message(Message.Type.Error, MessageKeys.MSG_PLAIN_TEXT,
                    this.getLocalizedMessage());
        else
            return message;
    }

    /**
     * Prepares well formatted exception message based on resource bundle key
     * 'msg_with_suggestion' and 'msg_no_suggestion'.
     */
    private void complyMessageStandard() {
        if (detailedError != null) {
            if (possibleSuggestion != null) {
                message = new Message(Message.Type.Error,
                                      MessageKeys.MSG_WITH_SUGGESTION,
                                      this.getClass().getSimpleName(),
                                      possibleSuggestion,
                                      detailedError);
            } else {
                message = new Message(Message.Type.Error,
                                      MessageKeys.MSG_NO_SUGGESTION,
                                      this.getClass().getSimpleName(),
                                      detailedError);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Exception message standardization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set detailed error inside the exception object.
     * <p>
     * The detailed error being set here is used later to prepare well formatted
     * exception message based on resource bundle key 'msg_with_suggestion' and
     * 'msg_no_suggestion'.
     *
     * @param key
     *            ResourceBundle message key, or a plain text message.
     * @param args
     *            Any parameters to be inserted into the message.
     *
     * @ignore When localizing, if we can't find the key in our bundles, we'll
     *         return this key as the message text.
     *
     */
    public void setDetailedError(String key, Object... args) {
        detailedError = new Message(Message.Type.Error, key, args);
    }
    
    public Message getDetailedError() {
        return detailedError;
    }

    /**
     * Set hints (that may solve the error) inside the exception object
     * <p>
     * The possible suggestion being set here is used later to prepare well
     * formatted exception message based on resource bundle key
     * 'msg_with_suggestion' and 'msg_no_suggestion'.
     *
     * @param key
     *            ResourceBundle message key, or a plain text message.
     * @param args
     *            Any parameters to be inserted into the message.
     *
     * @ignore When localizing, if we can't find the key in our bundles, we'll
     *         return this key as the message text.
     *
     */
    public void setPossibleSuggestion(String key, Object... args) {
        possibleSuggestion = new Message(Message.Type.Info, key, args);
    }

    public Message getPossibleSuggestion() {
        return possibleSuggestion;
    }
    
    
}
