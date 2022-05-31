/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.system;

import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.SMSResetConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.PhoneNumberFormatResult;
import sailpoint.tools.PhoneNumberUtil;
import sailpoint.tools.Util;
import sailpoint.web.LoginConfigBean;
import sailpoint.web.messages.MessageKeys;

import java.util.Map;

/**
 * This backing bean is responsible for getting/setting SMS Reset functionality on the Login Config - Password Reset 
 * screen.
 * @author chris.annino
 *
 */
public class SMSResetConfigBean extends UserResetConfigBean {
    
    private SMSResetConfig smsResetConfig;
    
    private static final int MIN_DURATION_VALID = 1;
    private static final int MAX_DURATION_VALID = 60 * 24 * 7;

    public SMSResetConfigBean() throws GeneralException {
        super();
        
        this.init();
    }
    
    private void init() throws GeneralException {
        
        Configuration config = getContext().getConfiguration();
        
        this.smsResetConfig = (SMSResetConfig) config.get(Configuration.SMS_RESET_CONFIG);
        
        if (this.smsResetConfig == null) {
            this.smsResetConfig = new SMSResetConfig();
        }

    }

    @Override
    public boolean save(Configuration config, LoginConfigBean loginConfig) throws GeneralException {
        
        // Validate first.
        if (!validate(config, loginConfig)) {
            return false;
        }
        
        config.put(Configuration.SMS_RESET_CONFIG, smsResetConfig);

        auditSMSResetConfigChanges(loginConfig);
        
        return true;
    }

    private void auditSMSResetConfigChanges(LoginConfigBean loginConfig) {

        if (loginConfig != null) {
            AuditEvent evt = loginConfig.getAuditEvent();
            if (evt != null) {
                //Should be non-null if auditing is enabled
                Map<String, Object> origConfig = loginConfig.getOriginalConfig();
                if (origConfig != null) {
                    Attributes origAtts = (Attributes)origConfig.get(Configuration.OBJ_NAME);
                    if (origAtts != null) {
                        SMSResetConfig origReset = (SMSResetConfig) origAtts.get(Configuration.SMS_RESET_CONFIG);
                        if (origReset == null) {
                            //Instantiate new -- This could set some default values that may not show up as changed
                            origReset = new SMSResetConfig();
                        }
                        if (!Util.nullSafeEq(origReset, smsResetConfig, true)) {

                            if (origReset.isSmsResetEnabled() != smsResetConfig.isSmsResetEnabled()) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[smsResetEnabled]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.isSmsResetEnabled(), smsResetConfig.isSmsResetEnabled()));
                            }

                            if (origReset.getDurationValid() != smsResetConfig.getDurationValid()) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[durationValid]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getDurationValid(), smsResetConfig.getDurationValid()));
                            }

                            if (origReset.getThrottleMinutes() != smsResetConfig.getThrottleMinutes()) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[throttleMinutes]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getThrottleMinutes(), smsResetConfig.getThrottleMinutes()));
                            }

                            if (origReset.getMaxFailedAttempts() != smsResetConfig.getMaxFailedAttempts()) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[maxFailedAttempts]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getMaxFailedAttempts(), smsResetConfig.getMaxFailedAttempts()));
                            }

                            if (!Util.nullSafeEq(origReset.getAccountId(), smsResetConfig.getAccountId(), true, true)) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[accountId]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getAccountId(), smsResetConfig.getAccountId()));
                            }

                            if (!Util.nullSafeEq(origReset.getAuthToken(), smsResetConfig.getAuthToken(), true, true)) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[authToken]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getAuthToken(), smsResetConfig.getAuthToken()));
                            }

                            if (!Util.nullSafeEq(origReset.getFromPhone(), smsResetConfig.getFromPhone(), true, true)) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[fromPhone]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getFromPhone(), smsResetConfig.getFromPhone()));
                            }

                            if (!Util.nullSafeEq(origReset.getPhoneAttribute(), smsResetConfig.getPhoneAttribute(), true, true)) {
                                evt.setAttribute(Configuration.SMS_RESET_CONFIG + "[phoneAttribute]",
                                        new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                                origReset.getPhoneAttribute(), smsResetConfig.getPhoneAttribute()));
                            }


                        }
                    }

                }
            }
        }
    }


    
    @Override
    public boolean validate(Configuration config, LoginConfigBean loginConfig) throws GeneralException {
        boolean valid = true;
        
        if (this.isSmsResetEnabled()) {
            if (Util.isNullOrEmpty(this.getAccountId())) {
                String fieldName = Message.info(MessageKeys.SMS_RESET_CONF_ACCOUNT_ID).getLocalizedMessage(this.getLocale(), this.getUserTimeZone());
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED, fieldName));
                valid = false;
            }
            if (Util.isNullOrEmpty(this.getAuthToken())) {
                String fieldName = Message.info(MessageKeys.SMS_RESET_CONF_AUTH_TOKEN).getLocalizedMessage(this.getLocale(), this.getUserTimeZone());
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED, fieldName));
                valid = false;
            }
            
            // validate from phone field
            PhoneNumberFormatResult result = PhoneNumberUtil.getValidPhoneNumber(this.getFromPhone());
            if (result.isValid() == false) {
                loginConfig.addMessage(Message.error(MessageKeys.ERR_VALIDATION_PHONE_NUMBER));
                valid = false;
            }
            else if(this.getFromPhone().equals(result.getPhoneNumber()) == false) {
                loginConfig.addMessage(Message.warn(MessageKeys.WARN_PHONE_NUMBER_REFORMATTED, result.getPhoneNumber()));
                this.setFromPhone(result.getPhoneNumber());
            }

            if (Util.isNullOrEmpty(this.getPhoneAttribute())) {
                String fieldName = Message.info(MessageKeys.SMS_RESET_CONF_PHONE_ATTRIBUTE).getLocalizedMessage(this.getLocale(), this.getUserTimeZone());
                loginConfig.addMessage(Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED, fieldName));
                valid = false;
            }
            if (this.getDurationValid() < MIN_DURATION_VALID || this.getDurationValid() > MAX_DURATION_VALID) {
                String fieldName = Message.info(MessageKeys.SMS_RESET_CONF_DURATION_VALID).getLocalizedMessage(this.getLocale(), this.getUserTimeZone());
                loginConfig.addMessage(Message.error(MessageKeys.ERR_VALIDATION_INTEGER_RANGE, fieldName, MIN_DURATION_VALID, MAX_DURATION_VALID));
                valid = false;
            }
            if (this.getMaxFailedAttempts() <= 0) {
                String fieldName = Message.info(MessageKeys.SMS_RESET_CONF_MAX_FAILED_ATTEMPTS).getLocalizedMessage(this.getLocale(), this.getUserTimeZone());
                loginConfig.addMessage(Message.error(MessageKeys.ERR_VALIDATION_POSITIVE_INTEGER, fieldName));
                valid = false;
            }
            
        }
        return valid;
    }
    
    public boolean isSmsResetEnabled() {
        return smsResetConfig.isSmsResetEnabled();
    }
    
    public void setSmsResetEnabled(boolean smsEnabled) {
        this.smsResetConfig.setSmsResetEnabled(smsEnabled);
    }
    
    public String getAccountId() {
        return smsResetConfig.getAccountId();
    }

    public void setAccountId(String accountId) {
        this.smsResetConfig.setAccountId(accountId);
    }

    public String getAuthToken() {
        return smsResetConfig.getAuthToken();
    }

    public void setAuthToken(String authToken) {
        this.smsResetConfig.setAuthToken(authToken);
    }

    public String getFromPhone() {
        return smsResetConfig.getFromPhone();
    }

    public void setFromPhone(String fromPhone) {
        this.smsResetConfig.setFromPhone(fromPhone);
    }

    public String getPhoneAttribute() {
        return smsResetConfig.getPhoneAttribute();
    }

    public void setPhoneAttribute(String phoneAttribute) {
        this.smsResetConfig.setPhoneAttribute(phoneAttribute);
    }

    public int getDurationValid() {
        return smsResetConfig.getDurationValid();
    }

    public void setDurationValid(int durationValid) {
        this.smsResetConfig.setDurationValid(durationValid);
    }

    public int getThrottleMinutes() {
        return smsResetConfig.getThrottleMinutes();
    }

    public void setThrottleMinutes(int span) {
        this.smsResetConfig.setThrottleMinutes(span);
    }

    public int getMaxFailedAttempts() {
        return smsResetConfig.getMaxFailedAttempts();
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.smsResetConfig.setMaxFailedAttempts(maxFailedAttempts);
    }
}
