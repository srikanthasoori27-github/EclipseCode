/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.tools.Util;
import sailpoint.tools.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Transfer object for a Message.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class MessageDTO {

    /**
     * Special case status of Success
     */
    public static String STATUS_SUCCESS = "SUCCESS";
    
    /**
     * The message key.
     */
    private String messageOrKey;

    /**
     * The message type.
     */
    private String status;

    /**
     * Original message type
     */
    private String type;

    /**
     * The translation arguments. Use String so it can be deserialized correctly.
     */
    private List<String> args;

    /**
     * Default constructor for MessageDTO.
     */
    public MessageDTO() {}

    /**
     * Constructor that uses the Message to initialize the MessageDTO values.
     *
     * @param message The message.
     */
    public MessageDTO(Message message) {
        messageOrKey = message.getKey();
        if (!Util.isEmpty(message.getParameters())) {
            args = new ArrayList<String>();
            for (Object param: message.getParameters()) {
                args.add((param == null) ? null : param.toString());
            }
        }

        if (message.getType() != null) {
            if (message.getType() == Message.Type.Info) {
                status = STATUS_SUCCESS;
            } else {
                status = message.getType().toString().toUpperCase();
            }
        }

        this.type = message.getType().name();
    }

    /**
     * Constructor that uses message or key and status to initialize
     * @param messageOrKey Message or key
     * @param status Status string
     */
    public MessageDTO(String messageOrKey, String status) {
        this.messageOrKey = messageOrKey;
        this.status = status;
    }

    /**
     * Gets the message key.
     *
     * @return The key.
     */
    public String getMessageOrKey() {
        return messageOrKey;
    }

    /**
     * Sets the message key.
     *
     * @param messageOrKey The key.
     */
    public void setMessageOrKey(String messageOrKey) {
        this.messageOrKey = messageOrKey;
    }

    /**
     * Gets the message status.
     *
     * @return The status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the message status.
     *
     * @param status The status.
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the message translation arguments.
     *
     * @return The arguments.
     */
    public List<String> getArgs() {
        return args;
    }

    /**
     * Sets the message translation arguments.
     *
     * @param args The arguments.
     */
    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Adds a message translation argument.
     *
     * @param arg The argument.
     */
    public void addArg(Object arg) {
        if (args == null) {
            args = new ArrayList<String>();
        }

        args.add((arg == null) ? null : arg.toString());
    }

}
