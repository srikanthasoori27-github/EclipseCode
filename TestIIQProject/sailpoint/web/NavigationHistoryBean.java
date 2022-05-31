/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import javax.faces.event.ActionEvent;

import org.apache.commons.lang3.text.WordUtils;

import sailpoint.service.MessageDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.util.NavigationHistory;

/**
 * JSF bean with actions to help with managing navigation history.
 * 
 * @author Kelly Grizzle
 */
public class NavigationHistoryBean extends BaseBean {

    /**
     * A JSON string that represents messages to add to the session.
     */
    private String messagesJson;

    /**
     * The JSF outcome to redirect the user to. Used if back is not true.
     */
    private String outcome;

    /**
     * The fallback JSF outcome to use if there is no navigation history. Only
     * used if back is true.
     */
    private String fallback;

    /**
     * True if the user should be taken back in the navigation history.
     */
    private boolean back;

    /**
     * Navigation string to be used to push into navigation history
     */
    private String navigationHistory;

    /**
     * True if user access to pages without a flow by using a URL, i.e. a bookmark
     */
    private boolean noFlow;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public NavigationHistoryBean() {}


    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getMessagesJson() {
        return messagesJson;
    }

    public void setMessagesJson(String messagesJson) {
        this.messagesJson = messagesJson;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    public boolean isBack() {
        return back;
    }

    public void setBack(boolean back) {
        this.back = back;
    }

    public String getNavigationHistory() {
        return this.navigationHistory;
    }

    public void setNavigationHistory(String navigationHistory) {
        this.navigationHistory = navigationHistory;
    }

    public boolean isNoFlow() {
        return noFlow;
    }

    public void setNoFlow(boolean noFlow) {
        this.noFlow = noFlow;
	}

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTION LISTENERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Go home, i.e., the dashboard
     */
    public String home() {
        return NavigationHistory.getInstance().home();
    }

    /**
     * Go back in the navigation history.
     */
    public void back(ActionEvent event) {
        NavigationHistory.getInstance().back();
    }

    /**
     * Transitions the user to a new JSF page based on the state specified.
     *
     * @return The JSF outcome.
     */
    public String transition() throws GeneralException {
        List<Message> messages = deserializeMessages();
        for (Message msg : Util.iterate(messages)) {
            addMessageToSession(msg);
        }

        String next;

        // if back was requested then try to pop history stack and
        // use fallback if necessary otherwise use the outcome specified
        if (isBack()) {
            if(noFlow) {
                NavigationHistory.getInstance().clearHistory();
            }
            next = NavigationHistory.getInstance().back();
            if (Util.isNullOrEmpty(next)) {
                next = fallback;
            }
        } else {
            next = outcome;
        }

        // if navigationHistory field was set save it on to navigation history
        if (Util.isNotNullOrEmpty(this.navigationHistory)) {
            NavigationHistory.getInstance().saveHistory(createNavigationHistoryPage(this.navigationHistory));
        }

        return next;
    }

    /**
     * Clear all pages from the history stack
     */
    public void clearNavigation() {
        NavigationHistory.getInstance().clearHistory();
    }

    /**
     * Create anonymous instance of NavigationHistory.Page with navigationString field set
     *
     * @return NavigationHistory.Page
     */
    private NavigationHistory.Page createNavigationHistoryPage(final String navString) {
        NavigationHistory.Page historyPage = new NavigationHistory.Page() {
            @Override
            public String getPageName() {
                return null;
            }

            @Override
            public String getNavigationString() {
                return navString;
            }

            @Override
            public Object calculatePageState() {
                return null;
            }

            @Override
            public void restorePageState(Object state) {

            }
        };

        return historyPage;
    }

    /**
     * Deserializes the JSON string containing the MessageDTO objects and creates
     * Message objects from them that can be added to the session.
     *
     * @return The list of messages.
     */
    private List<Message> deserializeMessages() throws GeneralException {
        List<Message> messages = new ArrayList<Message>();

        if (!Util.isNullOrEmpty(this.messagesJson)) {
            List<MessageDTO> messageDtos = JsonHelper.listFromJson(MessageDTO.class, messagesJson);
            for (MessageDTO msgDto : Util.iterate(messageDtos)) {
                if (msgDto.getArgs() != null) {
                    messages.add(new Message(
                            getMessageType(msgDto),
                            msgDto.getMessageOrKey(),
                            msgDto.getArgs().toArray()
                        ));
                } else {
                    messages.add(new Message(
                        getMessageType(msgDto),
                        msgDto.getMessageOrKey(),
                        msgDto.getArgs()
                    ));
                }
            }
        }

        return messages;
    }

    /**
     * Gets the Message.Type enumeration value from the status
     * contained in the DTO.
     *
     * @param msgDto The message DTO.
     * @return The type.
     */
    private Message.Type getMessageType(MessageDTO msgDto) {
        final String SUCCESS_STATUS = "SUCCESS";

        String type = msgDto.getStatus();

        // there is no success type on the server side so translate to info
        // which represents success on the server side
        if (SUCCESS_STATUS.equals(type)) {
            return Message.Type.Info;
        }

        return Message.Type.valueOf(WordUtils.capitalizeFully(type));
    }
}
