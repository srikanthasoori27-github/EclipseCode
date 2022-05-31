/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 * Derived from AlphaNumericValidator, but loosened up a bit for
 * object names.  In particular we want to allow hyhpens in 
 * TaskDefinition names, could allow others.
 */
package sailpoint.web.validators;

import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.Validator;
import javax.faces.validator.ValidatorException;

/**
 * @author peter.holcomb
 *
 */
public class ObjectNameValidator implements Validator {

    /* (non-Javadoc)
     * @see javax.faces.validator.Validator#validate(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public void validate(FacesContext context, UIComponent component, Object value)
            throws ValidatorException 
    {
        boolean isOnlySpaces = true;
        char invalid = 0;

        String str = (String)value;
        char chr[] = null;
        if(str != null)
            chr = str.toCharArray();
        
        for(int i=0; i<chr.length; i++)
        {
            char ch = chr[i];

            if (!Character.isLetterOrDigit(ch) &&
                ch != ' ' && ch != '-' && ch != ':')
            {
                invalid = ch;
                break;
            }
            
            if(chr[i]!=' ')
                isOnlySpaces = false;
                
        }

        Message summary = new Message(MessageKeys.INVALID_FIELD_INPUT);
        if (invalid != 0)
        {
            Message msg = new Message(MessageKeys.OBJECTNAME_INVALID_CHAR_ERROR, invalid);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary.getLocalizedMessage(), msg.getLocalizedMessage());
            throw new ValidatorException(message);
        }

        if (isOnlySpaces)
        {
            Message msg = new Message(MessageKeys.ALPHANUMERIC_SPACES_FIELD_ERROR);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary.getLocalizedMessage(), msg.getLocalizedMessage());
            throw new ValidatorException(message);
        }

    }

}
