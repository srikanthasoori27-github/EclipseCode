/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.suggest;

import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Authorizes that the class (and optional column) are allowed to be queried as part of a suggest.
 * Uses a required SuggestAuthorizerContext for the logic.
 */
public class SuggestAuthorizer implements Authorizer {

    private SuggestAuthorizerContext authorizerContext;
    private String className;
    private String columnName;

    /**
     * Constructor for object list authorization
     * @param suggestAuthorizerContext The SuggestAuthorizerContext
     * @param className The name of the class to authorize for object list
     */
    public SuggestAuthorizer(SuggestAuthorizerContext suggestAuthorizerContext, String className) {
        this.authorizerContext = suggestAuthorizerContext;
        this.className = className;
    }

    /**
     * Constructor for column authorization
     * @param suggestAuthorizerContext The SuggestAuthorizerContext
     * @param className The name of the class to authorize
     * @param columnName The name of the column to authorize
     */
    public SuggestAuthorizer(SuggestAuthorizerContext suggestAuthorizerContext, String className, String columnName) {
        this(suggestAuthorizerContext, className);
        this.columnName = columnName;
    }

    public boolean isAuthorized() {
        if (Util.isNothing(this.columnName)) {
            return this.authorizerContext.isClassAllowed(this.className);
        } else {
            return this.authorizerContext.isColumnAllowed(this.className, this.columnName);
        }
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized()) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_SUGGEST_RESULTS_UNAUTHORIZED_ACCESS));
        }
    }
}
