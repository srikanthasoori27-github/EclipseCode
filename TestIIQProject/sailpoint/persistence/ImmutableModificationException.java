package sailpoint.persistence;

import java.util.Locale;
import java.util.TimeZone;

import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * This runtime exception occurs when the SailPointObject Immutable property is set
 * and further modifications to the object occur. If it becomes absolutely necessary 
 * to make modifications you may pass a persistence option object to your context and set
 * the allow read only modifications (allow immutable) flag to true.
 * 
 * @author chris.annino
 *
 */
public class ImmutableModificationException extends RuntimeException implements Localizable {

	/**
	 * generated serial version ID
	 */
	private static final long serialVersionUID = 7547354561359039057L;
	
	private Message message = new Message(MessageKeys.EXCEPTION_IMMUTABLE); 

	public ImmutableModificationException() {
		super();
	}
	
	@Override
	public String getMessage() {
		return message.getLocalizedMessage();
	}
	
	@Override
	public String getLocalizedMessage() {
		return message.getLocalizedMessage();
	}

	public String getLocalizedMessage(Locale locale, TimeZone timezone) {
		return message.getLocalizedMessage(locale, timezone);
	}
}
