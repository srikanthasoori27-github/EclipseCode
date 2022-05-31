/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
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
public class AlphaNumericValidator implements Validator {

    /* (non-Javadoc)
     * @see javax.faces.validator.Validator#validate(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public void validate(FacesContext context, UIComponent component, Object value)
            throws ValidatorException 
    {
        boolean isAlphaNumeric = true;
        boolean isOnlySpaces = true;
        
        String str = (String)value;
        char chr[] = null;
        if(str != null)
            chr = str.toCharArray();
        
        //Check for alphanumeric values
        for(int i=0; i<chr.length; i++)
        {
            if(!((chr[i] >= 'A' && chr[i] <= 'Z') || (chr[i] >= 'a' && chr[i] <= 'z') 
                    || (chr[i] >= '0' && chr[i] <= '9') || (chr[i] == ' ')))
            {
                isAlphaNumeric = false;
                break;
            }
            
            if(chr[i]!=' ')
                isOnlySpaces = false;
                
        }
        Message summary = new Message(MessageKeys.INVALID_FIELD_INPUT);
        if (!isAlphaNumeric)
        {
            Message msg = new Message(MessageKeys.ALPHANUMERIC_FIELD_ERROR);
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
