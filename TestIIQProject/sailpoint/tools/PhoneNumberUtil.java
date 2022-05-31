/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Util class used to format and validate phone numbers.
 *
 * @author chris.annino
 * @author michael.hide
 */
public final class PhoneNumberUtil {
    private static final Log log = LogFactory.getLog(PhoneNumberUtil.class);
    /**
     * Max length of phone number, per E.164 spec.
     */
    private static final int MAX_LENGTH = 15;
    /**
     * Min length of phone number.
     */
    private static final int MIN_LENGTH = 10;
    /**
     * The PLUS_SIGN signifies the international prefix.
     */
    private static final char PLUS_SIGN = '+';

    private PhoneNumberUtil() {
        // use the statics
    }

    /**
     * Returns a PhoneNumberFormatResult object containing the formatted number along with
     * a boolean flag indicating 'validity' and any applicable error messages.
     *
     * @param phoneNumber to format
     * @return Object that holds formatted phone number, isValid state, and any error message
     */
    public static PhoneNumberFormatResult getValidPhoneNumber(String phoneNumber) {
        boolean isValid = true;
        String message = "";

        if (Util.isNullOrEmpty(phoneNumber)) {
            isValid = false;
            message = "Number is empty.";
        }
        else {
            // This could return a 15 digit number with a leading +, so test for > 16.
            phoneNumber = formatPhoneNumber(phoneNumber);
            if (Util.isNullOrEmpty(phoneNumber)) {
                isValid = false;
                message = "Number could not be formatted.";
            }
            else if ((phoneNumber.length() > (MAX_LENGTH + 1) && phoneNumber.charAt(0) == PLUS_SIGN) ||
                    (phoneNumber.length() > MAX_LENGTH) && phoneNumber.charAt(0) != PLUS_SIGN) {
                isValid = false;
                message = "Number is greater than " + MAX_LENGTH + " digits.";
            }
            else if (phoneNumber.length() < MIN_LENGTH) {
                isValid = false;
                message = "Number is less than " + MIN_LENGTH + " digits.";
            }
        }

        return new PhoneNumberFormatResult(phoneNumber, isValid, message);
    }

    private static String formatPhoneNumber(String phoneNumber) {
        boolean hasPlus = false;
        phoneNumber = phoneNumber.trim();
        if (phoneNumber.charAt(0) == PLUS_SIGN) {
            hasPlus = true;
        }
        phoneNumber = phoneNumber.replaceAll("[^0-9]+", "");
        if (hasPlus) {
            phoneNumber = PLUS_SIGN + phoneNumber;
        }
        return phoneNumber;
    }
}

