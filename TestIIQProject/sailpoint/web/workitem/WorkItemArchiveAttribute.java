/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.workitem;

import sailpoint.object.ApprovalSet;
import sailpoint.object.Form;
import sailpoint.service.pam.PamRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A simple object to hold work item archive attributes so we can iterate through them in JSF with <h:dataTable>
 *
 * @author: michael.hide
 * Created: 3/4/15 11:54 AM
 */
public class WorkItemArchiveAttribute {

    private String _key;
    private Object _value;

    // System default localized 'password'.  The idea being that custom attributes might be named 'passwort' or
    // 'contraseña' or whatever the local language word is for password.
    private final String LOCALIZED_PASSWORD_KEY = new Message(MessageKeys.WORKITEM_ATTRIBUTE_PASSWORD).getLocalizedMessage();

    private static final String PASSWORD_KEY = "password";
    private static final String PASSWORD_MASK = "****";
    private final boolean SAME_PASSWORD_KEY = PASSWORD_KEY.equals(LOCALIZED_PASSWORD_KEY);

    /**
     * Initialize values in the constructor
     *
     * @param key   The key for the attribute
     * @param value The value for the attribute
     */
    public WorkItemArchiveAttribute(String key, Object value) {
        this._key = key;
        this._value = value;
    }

    /**
     * Return the key
     *
     * @return the key
     */
    public String getKey() {
        return this._key;
    }

    /**
     * Return the value, masked if the key indicates it is a password.
     *
     * @return the value
     */
    public Object getValue() {
        if (this._key != null &&
                (this._key.toLowerCase().contains(PASSWORD_KEY) ||
                        // No need to compare the localized password key if it's the same as PASSWORD_KEY
                        (!SAME_PASSWORD_KEY && this._key.toLowerCase().contains(LOCALIZED_PASSWORD_KEY)))) {
            return PASSWORD_MASK;
        }
        return this._value;
    }

    /**
     * Transforms the value into a better representation of the object.  (e.g. instead of the Form object itself,
     * return the name of the Form since that is likely more valuable to the user.)
     *
     * @return A 'prettier' version of the value.
     * @throws GeneralException
     */
    public Object getPrettyValue() throws GeneralException {
        return getPrettyAttribute(this.getValue());
    }

    /**
     * Transform an object value into a more relevant and human readable string.
     *
     * @param attr The attribute value to transform
     * @return An easier to read representation of the object
     * @throws GeneralException
     */
    private Object getPrettyAttribute(Object attr) throws GeneralException {
        if (attr instanceof Form) {
            return ((Form) attr).getName();
        }
        else if (attr instanceof ApprovalSet) {
            return Util.size(((ApprovalSet) attr).getItems());
        }
        else if (attr instanceof PamRequest) {
            PamRequest req = (PamRequest) attr;
            return (null != req.getContainerDisplayName()) ? req.getContainerDisplayName() : req.getContainerName();
        }
        return attr;
    }

    public static String getSecretMask() {
        return PASSWORD_MASK;
    }
}
