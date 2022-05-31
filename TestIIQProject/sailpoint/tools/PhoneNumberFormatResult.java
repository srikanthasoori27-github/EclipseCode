/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

/**
 * Object to hold results of formatting and validating a phone number.
 * This should always have the phone number in it, even if it's invalid.
 *
 * Project: identityiq
 * Author: michael.hide
 * Created: 1/16/14 2:46 PM
 */
public class PhoneNumberFormatResult {
    private boolean isValid = false;
    private String num = null;
    private String message;

    public PhoneNumberFormatResult(String number, boolean isValid, String message) {
        this.isValid = isValid;
        this.num = number;
        this.message = message;
    }

    public boolean isValid() {
        return this.isValid;
    }

    public String getPhoneNumber() {
        return this.num == null ? "" : this.num;
    }

    public String getMessage() {
        return this.message == null ? "" : this.message;
    }
}
