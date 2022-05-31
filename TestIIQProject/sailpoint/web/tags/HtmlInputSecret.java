/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import javax.faces.context.FacesContext;
import javax.faces.convert.ConverterException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

/**
 * A wrapper implementation of the Sun JSF RI secret input component that
 * puts back the original value if the submitted value is our masked value.
 *
 * By default this component will encrypt unencrypted values, but can be 
 * disabled by adding encrypt="false" to the tag declaration.
 *
 * @see sailpoint.web.tags.SecretRenderer
 * @see sailpoint.web.tags.InputSecretTag
 */
public class HtmlInputSecret extends javax.faces.component.html.HtmlInputSecret {
    private static Log log = LogFactory.getLog(HtmlInputSecret.class);

    static final String DUMMY_VALUE = "tsewraf";

    /**
     * Flag to indicate to the component if the string value
     * should be converted to an encryted string.
     */
    boolean _encrypt;

    /**
     * pjeong: bug6884 user can login using encrypted password.
     * Let the login page disable the check using this flag so that you can't login
     * using an encrypted password.
     */
    boolean _checkForEncrypted;

    /**
     * Set our renderer type to sailpoint.web.tags.Secret.
     */
    public HtmlInputSecret() {
        super();
        // default is to encrypt the values 
        // after the've been posted
        _encrypt = true;
        setRendererType("sailpoint.web.tags.Secret");
        _checkForEncrypted = true;
    }  // HtmlInputSecret()

    /**
     * Override this method and use the local value unstead of the previous.
     * With this gadge the previous value will always be null since
     * the value is not posted by default.
    @Override
    protected boolean compareValues(Object previous, Object value) {
        return super.compareValues(getLocalValue(), value);
    }
     */

    /**
     * Wrap the RI implementation to replace the submitted value with the
     * original value when the submitted value is our masked value.
     */
    @Untraced
    @Override
    public void setSubmittedValue(Object submittedValue) {
        if ( DUMMY_VALUE.equals(submittedValue) ) {
            log.debug("Intercepting " +  HtmlInputSecret.DUMMY_VALUE +
                      " as the value of secret component and resetting to" +
                      " the original value.");
            super.setSubmittedValue(getValue());
        } else {
            super.setSubmittedValue(submittedValue);
        }
    }

    /**
     * Flag to indicate if the string value should be encrpyted.
     */
    public boolean getEncrypt() {
        return _encrypt;
    }

    public void setEncrypt(boolean b) {
        _encrypt = b;
    }

    
    public boolean isCheckForEncrypted() {
        return _checkForEncrypted;
    }

    public void setCheckForEncrypted(boolean checkForEncrypted) {
        _checkForEncrypted = checkForEncrypted;
    }

    
    /**
     * Convert the raw string value into an encrpyted string. 
     * Check the value to see if its already encrypted before
     * encrypting it, othewise we can get into recursive 
     * encryption during saves where the password attributes
     * don't change.
     */
    @Override
    @Untraced
    protected Object getConvertedValue(FacesContext context,
                                       Object newSubmittedValue)
        throws ConverterException {

        Object convertedValue = super.getConvertedValue(context, newSubmittedValue); 
        if ( newSubmittedValue != null )  {
            if ( _encrypt ) {
                String newValue = (String)newSubmittedValue;
                convertedValue = encrypt(newValue);
            }
        } 
        return convertedValue;
    }

    private SailPointContext getContext() {
        SailPointContext ctx = null;
        try {
            ctx = SailPointFactory.getCurrentContext();
        } catch(GeneralException e){
            log.error("Error getting context! " + e.toString());
        }
        return ctx;
    }

    @Untraced
    private String encrypt(String str) {
        String s = str;
        if ( Util.getString(str) != null ) {
            try {
                s = getContext().encrypt(str, _checkForEncrypted);
            } catch(GeneralException e){
                log.error("Error encrypting! " + e.toString());
            } 
        }
        return s;
    }

}  // class HtmlInputSecret
