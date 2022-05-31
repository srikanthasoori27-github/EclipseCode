/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user is able to make an LCM request for the specified user.
 *
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class LcmActionAuthorizer implements Authorizer {

    private String _quickLinkName;
    private String _lcmAction;
    private String _lcmRequestControl;
    private boolean _allowSelfService;

    /**
     * Constructs a LcmActionAuthorizer that determines whether or not the requesting user
     * is authorized to perform the given LCM action
     * @param lcmAction Action that the user is performing.  Actions are available as static
     * Strings on the QuickLink class.  Appropriate static variable names have the prefix LCM_ACTION
     */
    public LcmActionAuthorizer(String lcmAction) {
        this(lcmAction, true);
    }

    /**
     * Constructs a LcmActionAuthorizer that determines whether or not the requesting user
     * is authorized to perform the given LCM action
     * @param lcmAction Action that the user is performing.  Actions are available as static
     * Strings on the QuickLink class.  Appropriate static variable names have the prefix LCM_ACTION
     * @param allowSelfService True to allow all enabled self service quicklinks. False to exclude.                  
     */
    public LcmActionAuthorizer(String lcmAction, boolean allowSelfService) {
        this(lcmAction, null, allowSelfService);
    }
    
    /**
     * Constructs a LcmActionAuthorizer that determines whether or not the requesting user
     * is authorized to perform the given LCM action. If the LCM action grants access and the request
     * control parameter is not null, the value of the lcm request control is checked before granting access.
     * @param lcmAction Action that the user is performing.  Actions are available as static
     * Strings on the QuickLink class.  Appropriate static variable names have the prefix LCM_ACTION
     * @param lcmRequestControl If not null, will check the request control value before granting authorization
     * @param allowSelfService True to allow all enabled self service quicklinks. False to exclude. 
     */
    public LcmActionAuthorizer(String lcmAction, String lcmRequestControl, boolean allowSelfService) {
        _lcmAction = lcmAction;
        _lcmRequestControl = lcmRequestControl;
        _allowSelfService = allowSelfService;
    }

    /**
     * Constructor.  Will use quicklink name, will allow self service, and no lcm request control
     * @param quickLink The quicklink to authorize
     */
    public LcmActionAuthorizer(QuickLink quickLink) {
        this(quickLink, null, true);
    }

    /**
     * Constructor.  Will use quicklink name and conditionally allow self service, but no lcm request control
     * @param quickLink The quicklink to authorize
     * @param allowSelfService True to allow self service false to exclude
     */
    public LcmActionAuthorizer(QuickLink quickLink, boolean allowSelfService) {
        this(quickLink, null, allowSelfService);
    }

    /**
     * Constructor.  Will use the quicklink name, conditionally allow self service and apply lcm request control
     * @param quickLink The quicklink to authorize
     * @param lcmRequestControl LCM request control
     * @param allowSelfService True to allow self service false to exclude
     */
    public  LcmActionAuthorizer(QuickLink quickLink, String lcmRequestControl, boolean allowSelfService) {
        this._quickLinkName = quickLink != null ? quickLink.getName() : null;
        _allowSelfService = allowSelfService;
        _lcmRequestControl = lcmRequestControl;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        ensureLcmEnabled(userContext);

        SailPointContext context = userContext.getContext();

        Identity requestingIdentity = userContext.getLoggedInUser();

        List<Capability> capabilities = userContext.getLoggedInUserCapabilities();
        if (Capability.hasSystemAdministrator(capabilities)) {
            return;
        }

        boolean isAuthorized = false;

        if (!Util.isNullOrEmpty(_lcmAction) || !Util.isNullOrEmpty(_quickLinkName)) {
            QuickLinkOptionsConfigService qlConfigService = new QuickLinkOptionsConfigService(context, userContext.getLocale(), userContext.getUserTimeZone());
            isAuthorized = qlConfigService.isQuickLinkActionEnabled(requestingIdentity, _lcmAction, _quickLinkName, _lcmRequestControl, _allowSelfService, true);
        }

        if (!isAuthorized) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_LCM_ACTION_UNAUTHORIZED_ACCESS, _lcmAction));
        }
    }

    private void ensureLcmEnabled(UserContext userContext) throws GeneralException {
        new LcmEnabledAuthorizer().authorize(userContext);
    }

}
