/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.link;

import java.util.List;
import java.util.Map;

import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Link;
import sailpoint.service.PasswordChangeError;
import sailpoint.web.link.LinkDTO;


/**
 * @author: cindy.he
 * A DTO to represent the PasswordLinkDTO model.
 * 
 * It gives some methods to the ui tier about the types of items
 * in the PasswordLinkDTO.  
 */
public class PasswordLinkDTO extends LinkDTO {
    
    private boolean requiresCurrentPassword;
    private List<String> passwordPolicy;
    private List<PasswordChangeError> passwordChangeErrors;

    /**
     * Constructor
     * @param link The link to instatiate this DTO from
     */
    public PasswordLinkDTO(Link link) {
        super(link);
        this.requiresCurrentPassword = link.getApplication().supportsFeature(Application.Feature.CURRENT_PASSWORD);
    }
    
    /**
     * Constructor, call parent's constructor
     * @param link a link object stored in map, object properties are used for key
     * @param cols list of UI ColumnConfigs of the projection columns
     */
    public PasswordLinkDTO(Link link, Map<String, Object>linkMap, List<ColumnConfig> cols) {
        super(link, linkMap, cols);
        this.requiresCurrentPassword = link.getApplication().supportsFeature(Application.Feature.CURRENT_PASSWORD);
    }
    
    /**
     * true is current password is required for password changes
     * @return boolean true is current password is required for password changes
     */
    public boolean isRequiresCurrentPassword() {
        return requiresCurrentPassword;
    }

    /**
     * set to true is current password is required for password changes
     * @param requiresCurrentPassword true if current password is required for password changes
     */
    public void setRequiresCurrentPassword(boolean requiresCurrentPassword) {
        this.requiresCurrentPassword = requiresCurrentPassword;
    }

    /**
     * get application's password policy
     * @return password policy
     */
    public List<String> getPasswordPolicy() {
        return passwordPolicy;
    }

    /**
     * set password policy
     * @param passwordPolicy password policy of the application for given account
     */
    public void setPasswordPolicy(List<String> passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public List<PasswordChangeError> getPasswordChangeErrors() {
        return passwordChangeErrors;
    }

    /**
     * A list of errors associated with a password change that has failed
     * @param passwordChangeErrors a list of errors associated with the password change
     */
    public void setPasswordChangeErrors(List<PasswordChangeError> passwordChangeErrors) {
        this.passwordChangeErrors = passwordChangeErrors;
    }
}
