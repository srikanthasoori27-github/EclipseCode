/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLProperty;

import java.util.Objects;

/**
 * This class is the object representing SMS text Reset Password configuration. Typically, 
 * you need all these properties, so they are consolidated into this one configuration object.
 * @author chris.annino
 *
 */
public class SMSResetConfig extends AbstractXmlObject {
    private boolean smsResetEnabled;
    private String accountId;
    private String authToken;
    private String fromPhone;
    private String phoneAttribute;
    // let's default to 10 minutes
    private static final int DEFAULT_DURATION_VALID = 10;
    private int durationValid = DEFAULT_DURATION_VALID;

    // minimum span of time (in minutes) we allow message to be sent.
    // i.e. don't send messages more than once every MIN_THROTTLE_TIME minutes.
    private static final int MIN_THROTTLE_TIME = 1;
    private int throttleMinutes = MIN_THROTTLE_TIME;

    // default max failed attempts
    public static final int DEFAULT_MAX_FAILED_ATTEMPTS = 3;
    private int maxFailedAttempts = DEFAULT_MAX_FAILED_ATTEMPTS;

    /**
     * Enables SMS Password Reset functionality
     * @return is SMS Password Reset enabled
     */
    @XMLProperty
    public boolean isSmsResetEnabled() {
        return smsResetEnabled;
    }
    
    /**
     * @param smsResetEnabled
     * @see #isSmsResetEnabled()
     */
    public void setSmsResetEnabled(boolean smsResetEnabled) {
        this.smsResetEnabled = smsResetEnabled;
    }
    
    /**
     * @return Account Id used to communicate with the SMS text service.
     */
    @XMLProperty
    public String getAccountId() {
        return accountId;
    }
    /**
     * @param accountId
     * @see #getAccountId()
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    /**
     * @return Authentication token used by the SMS text service.
     */
    @XMLProperty
    public String getAuthToken() {
        return authToken;
    }
    /**
     * @param authToken
     * @see #getAuthToken()
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
    
    /**
     * @return The "from" phone number that will appear on the sms text message. Must be in 
     * E.164 phone format if this number is not from the US or Canada.
     */
    @XMLProperty
    public String getFromPhone() {
        return fromPhone;
    }
    /**
     * @param fromPhone
     * @see #getFromPhone()
     */
    public void setFromPhone(String fromPhone) {
        this.fromPhone = fromPhone;
    }
    
    /**
     * @return The identity attribute that represents the mobile phone number.
     */
    @XMLProperty
    public String getPhoneAttribute() {
        return phoneAttribute;
    }
    /**
     * @param phoneAttribute
     * @see #getPhoneAttribute()
     */
    public void setPhoneAttribute(String phoneAttribute) {
        this.phoneAttribute = phoneAttribute;
    }
    
    /**
     * @return The duration (minutes) that the password reset token is valid.
     */
    @XMLProperty
    public int getDurationValid() {
        return durationValid;
    }
    /**
     * @param durationValid duration (minutes) a password reset token is valid
     * @see #getDurationValid()
     */
    public void setDurationValid(int durationValid) {
        this.durationValid = durationValid;
    }

    /**
     * @return The duration (minutes) of time below which additional messages will be throttled.
     */
    @XMLProperty
    public int getThrottleMinutes() {
        return throttleMinutes;
    }

    /**
     * @param span minimum allowable time (minutes) between sms message requests
     */
    public void setThrottleMinutes(int span) {
        if(span < MIN_THROTTLE_TIME) {
            span = MIN_THROTTLE_TIME;
        }
        this.throttleMinutes = span;
    }

    /**
     * @return The maximum failed attempts allowed.
     */
    @XMLProperty
    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }
    /**
     * @param maxFailedAttempts maximum failed attempts allowed
     * @see #getMaxFailedAttempts()
     */
    public void setMaxFailedAttempts(int maxFailedAttempts) {
        if (maxFailedAttempts < 0) {
            maxFailedAttempts = DEFAULT_MAX_FAILED_ATTEMPTS;
        }
        this.maxFailedAttempts = maxFailedAttempts;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SMSResetConfig that = (SMSResetConfig) o;
        return smsResetEnabled == that.smsResetEnabled &&
                durationValid == that.durationValid &&
                throttleMinutes == that.throttleMinutes &&
                maxFailedAttempts == that.maxFailedAttempts &&
                Objects.equals(accountId, that.accountId) &&
                Objects.equals(authToken, that.authToken) &&
                Objects.equals(fromPhone, that.fromPhone) &&
                Objects.equals(phoneAttribute, that.phoneAttribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(smsResetEnabled, accountId, authToken, fromPhone, phoneAttribute, durationValid, throttleMinutes, maxFailedAttempts);
    }
}
