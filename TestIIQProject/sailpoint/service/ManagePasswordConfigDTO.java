package sailpoint.service;

import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.web.UserContext;

/**
 * DTO to hold configuration for manage passwords
 */
public class ManagePasswordConfigDTO {

    /**
     * Flag that determines if password generation is allowed.
     */
    private boolean allowGeneratePasswordDelegated;

    /**
     * Constructor
     * @param userContext
     * @throws GeneralException
     */
    public ManagePasswordConfigDTO(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        Configuration configuration = userContext.getContext().getConfiguration();
        allowGeneratePasswordDelegated =
                configuration.getBoolean(Configuration.LCM_ALLOW_GENERATE_PASSWORD_DELEGATION, true);
    }

    /**
     * If true then password generation is allowed.
     * @return boolean
     */
    public boolean isAllowGeneratePasswordDelegated() {
        return allowGeneratePasswordDelegated;
    }
}
