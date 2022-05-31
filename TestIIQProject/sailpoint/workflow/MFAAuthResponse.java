/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.workflow;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * MFAAuthResponse holds the response from authentication calls used for multi factor authentication.
 */
@XMLClass(xmlname = "MFAAuthResponse")
public class MFAAuthResponse extends AbstractXmlObject {
    private String result;
    private String statusMessage;
    private String trustedDeviceToken;

    /**
     * A string result containing "allow", "deny", or "waiting".
     */
    @XMLProperty
    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    /**
     * A string containing a descriptive message about the status of a multi-factor
     * authentication request.  Such as "Pushed a login request to your device..."
     */
    @XMLProperty
    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    /**
     * A token that can be populated in an authentication request, and used later to check
     * the status of that request.
     */
    @XMLProperty
    public String getTrustedDeviceToken() {
        return trustedDeviceToken;
    }

    public void setTrustedDeviceToken(String trustedDeviceToken) {
        this.trustedDeviceToken = trustedDeviceToken;
    }
}
