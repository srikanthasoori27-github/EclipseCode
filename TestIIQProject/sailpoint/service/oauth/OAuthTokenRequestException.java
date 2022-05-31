/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import sailpoint.tools.Util;

public class OAuthTokenRequestException extends Exception {

    /**
     * Enumeration of Oauth token request error codes
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-5.2">Error Response</a>
     */
    public enum ErrorType {
        INVALID_CLIENT("invalid_client"),
        INVALID_SCOPE("invalid_scope"),
        INVALID_REQUEST("invalid_request"),
        INVALID_GRANT("invalid_grant");

        private String type;
        
        private ErrorType(String type) {
            this.type = type;
        }
        public String getType() {
            return type;
        }
        @Override
        public String toString() {
            return this.type;
        }
    }
    
    private int errorCode;
    private String errorDescription;
    
    public OAuthTokenRequestException(String msg) {
        super(msg);
        this.errorCode = HttpServletResponse.SC_BAD_REQUEST;
    }
    
    public OAuthTokenRequestException(int errorCode, String msg, String errorDescription) {
        super(msg);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }
    
    public OAuthTokenRequestException(ErrorType invalidRequest) {
        this(invalidRequest.getType());
    }

    public int getErrorCode() {
        return errorCode;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("error", this.getMessage());
        if (Util.isNotNullOrEmpty(errorDescription)) {
            map.put("error_description", errorDescription);
        }
        return map;
    }

}
