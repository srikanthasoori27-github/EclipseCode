/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.validators;

import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.IdentitySearchBean;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.Validator;
import javax.faces.validator.ValidatorException;

/**
 * @author michael.hide
 */
public class AttributeNameValidator implements Validator {

    /* (non-Javadoc)
     * @see javax.faces.validator.Validator#validate(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public void validate(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        String str = (String) value;

        boolean isAlphaNumeric = true;
        boolean isOnlySpaces = true;

        char chr[] = null;
        if(str != null) {
            chr = str.toCharArray();
        }

        //Check for alphanumeric values
        for(int i=0; i < chr.length; i++) {
            if(!(Character.isLetterOrDigit(chr[i]) || chr[i] == ' ' || chr[i] == '_')) {
                isAlphaNumeric = false;
                break;
            }

            if(!Character.isWhitespace(chr[i])) {
                isOnlySpaces = false;
            }
        }

        Message summary = new Message(MessageKeys.INVALID_FIELD_INPUT);

        if (!isAlphaNumeric) {
            Message msg = new Message(MessageKeys.ALPHANUMERIC_FIELD_ERROR);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary.getLocalizedMessage() + ":", msg.getLocalizedMessage());
            throw new ValidatorException(message);
        }

        if (isOnlySpaces) {
            Message msg = new Message(MessageKeys.ALPHANUMERIC_SPACES_FIELD_ERROR);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary.getLocalizedMessage() + ":", msg.getLocalizedMessage());
            throw new ValidatorException(message);
        }

        if (str.startsWith(IdentitySearchBean.ATT_IDT_SEARCH_LINK_HTML_PREFIX)) {
            Message msg = new Message(MessageKeys.IDENTITY_ATTRIBUTE_NAME_ERROR, MessageKeys.OCONFIG_LABEL_ATTRIBUTE_NAME, IdentitySearchBean.ATT_IDT_SEARCH_LINK_HTML_PREFIX);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary.getLocalizedMessage() + ":", msg.getLocalizedMessage());
            throw new ValidatorException(message);
        }
    }

}
