package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Encapsulates authorization data for account unlock/reset
 * either via SMS code or auth question answers.
 * 
 * @author tapash.majumder
 *
 */
public class AuthData {
    private static final Log log = LogFactory.getLog(AuthData.class);
    
    /**
     * Enum representing the type of authentication
     * Right now either SMS or Auth Questions
     *
     */
    public enum AuthType {
        SMS("SMS"),
        AUTH_QUESTIONS("Questions");
        
        private final String textValue;
        
        private AuthType(final String textValue) {
            this.textValue = textValue;
        }
        
        public String textValue() {
            return textValue;
        }
        
        public static AuthType parse(String textValue) {
            AuthType result = null;
            
            for (AuthType type : AuthType.values()) {
                if (type.textValue().equals(textValue)) {
                    result = type;
                    break;
                }
            }
            
            return result;
        }
    }

    /**
     * String const Keys used for json serialization/deserialization
     * @author tapash.majumder
     *
     */
    public static class Keys {
        public static final String AUTH_TYPE = "type";
        public static final String AUTH_QUESTIONS_DATA = "authQuestions";
        public static final String QUESTION_ID = "id";
        public static final String QUESTION_ANSWER = "answer";
        public static final String TOKEN = "token";
    }
    
    public static class SMSAuthData {
        private String authToken;
    
        public SMSAuthData() {
        }
        
        public SMSAuthData(String authToken) {
            this.authToken = authToken;
        }
        
        public String getAuthToken() {
            return authToken;
        }
    
        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }
    }

    public static class AuthQuestionsAuthData {
        public static class AuthResponse {
            private String id;
            private String answer;
            
            public String getId() {
                return id;
            }
            
            public void setId(String id) {
                this.id = id;
            }
            
            public String getAnswer() {
                return answer;
            }
            
            public void setAnswer(String answer) {
                this.answer = answer;
            }
            
            public AuthResponse() {
            }
            
            public AuthResponse(Map<String, Object> map) {
                setId((String) map.get(Keys.QUESTION_ID));
                setAnswer((String) map.get(Keys.QUESTION_ANSWER));
            }
            
            public Map<String, Object> toMap() {
                Map<String, Object> map = new HashMap<String, Object>();
                
                map.put(Keys.QUESTION_ID, id);
                map.put(Keys.QUESTION_ANSWER, answer);
                
                return map;
            }
        }
    
        private List<AuthResponse> responses;
        
        public AuthQuestionsAuthData() {
            
        }
        
        public AuthQuestionsAuthData(List<Map<String, Object>> responseMapsList) {
            responses = new ArrayList<AuthResponse>();
            if (!Util.isEmpty(responseMapsList)) {
                for (Map<String, Object> responseMap : responseMapsList) {
                    responses.add(new AuthResponse(responseMap));
                }
            }
        }
        
        public List<AuthResponse> getResponses() {
            return responses;
        }
        
        public void setResponses(List<AuthResponse> val) {
            responses = val;
        }
        
        public List<Map<String, Object>> toResponseMapsList() {
            List<Map<String, Object>> responseMapsList = new ArrayList<Map<String,Object>>();
            if (!Util.isEmpty(responses)) {
                for (AuthResponse response : responses) {
                    responseMapsList.add(response.toMap());
                }
            }
            return responseMapsList;
        }
    }

    private AuthType type;
    private AuthData.SMSAuthData smsAuthData;
    private AuthData.AuthQuestionsAuthData authQuestionsAuthData;
    
    public AuthData() {
        
    }
    
    @SuppressWarnings("unchecked")
    public AuthData(Map<String, Object> map) {
        setType(AuthType.parse((String) map.get(Keys.AUTH_TYPE)));

        if (getType() == AuthType.SMS) {
            setSmsAuthData(new AuthData.SMSAuthData((String) map.get(Keys.TOKEN)));
        } else if (getType() == AuthType.AUTH_QUESTIONS) {
            setAuthQuestionsAuthData(new AuthData.AuthQuestionsAuthData((List<Map<String,Object>>) map.get(Keys.AUTH_QUESTIONS_DATA)));
        } else {
            throw new IllegalStateException("Unknown authType: " + getType());
        }
    }
    
    public AuthType getType() {
        return type;
    }
    
    public void setType(AuthType type) {
        this.type = type;
    }
    
    public AuthData.SMSAuthData getSmsAuthData() {
        return smsAuthData;
    }
    
    public void setSmsAuthData(AuthData.SMSAuthData smsAuthData) {
        this.smsAuthData = smsAuthData;
    }
    
    public AuthData.AuthQuestionsAuthData getAuthQuestionsAuthData() {
        return authQuestionsAuthData;
    }
    
    public void setAuthQuestionsAuthData(AuthData.AuthQuestionsAuthData authQuestionsAuthData) {
        this.authQuestionsAuthData = authQuestionsAuthData;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        
        map.put(Keys.AUTH_TYPE, type.textValue());

        if (type == AuthType.SMS) {
            map.put(Keys.TOKEN, smsAuthData.getAuthToken());
        } else if (type == AuthType.AUTH_QUESTIONS) {
            map.put(Keys.AUTH_QUESTIONS_DATA, authQuestionsAuthData.toResponseMapsList());
        } else {
            throw new IllegalStateException("Unknown authType: " + type);
        }
        
        return map;
    }
    
    /**
     * Validates that the data sent by the form is set properly 
     * We do all sorts of check in the beginning before starting with the actual work.
     */
    public void validate() throws ValidationException {
        if (getType() == null) {
            if (log.isWarnEnabled()) {
                log.warn("Reset type not set");
            }
            throwValidationException();
        }

        if (getType() == AuthType.SMS) {
            validateSMSResetInput();
        } else if (getType() == AuthType.AUTH_QUESTIONS) {
            validateAuthQuestionsInput();
        } else {
            // we should never be here but put it for good measure 
            throw new IllegalStateException("Unknown reset type: " + getType());
        }
    }
    
    /**
     * Checks that all input has been set for SMS reset type
     */
    private void validateSMSResetInput() throws ValidationException {
        if (getSmsAuthData() == null || getSmsAuthData().getAuthToken() == null) {
            if (log.isWarnEnabled()) {
                log.warn("Reset token not set");
            }
            throwValidationException();
        }
    }

    /**
     * Checks that all input is set for AuthQuestions type
     */
    private void validateAuthQuestionsInput() throws ValidationException {
        if (getAuthQuestionsAuthData() == null || getAuthQuestionsAuthData().getResponses() == null) {
            if (log.isWarnEnabled()) {
                log.warn("Auth response not set");
            }
            throwValidationException();
        }
    }
    
    private void throwValidationException() throws ValidationException {
        throw new ValidationException(new Message(MessageKeys.RESET_ERR_GENERIC));
    }
}