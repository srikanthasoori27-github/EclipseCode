package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.integration.IIQClient.AuthorizationService.CheckAuthorizationResult;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class AuthorizationServiceDelegate
{
    private SailPointContext context;

    public AuthorizationServiceDelegate(SailPointContext context, String authUsername)
    {
        this.context = context;
    }
    
    public CheckAuthorizationResult checkAuthorization(String identityName, String rightName)
        throws GeneralException
    {
        Identity identity = this.context.getObjectByName(
                Identity.class,
                identityName);
        if (identity == null)
        {
            throw new GeneralException(new Message(
                    MessageKeys.WS_IDENTITY_DOESNT_EXIST,
                    identityName).getLocalizedMessage());
        }

        CheckAuthorizationResult result = new CheckAuthorizationResult();
        result.setStatus(CheckAuthorizationResult.STATUS_SUCCESS);
        
        result.setAuthorized(identity.getCapabilityManager()
                .hasCapability(Capability.SYSTEM_ADMINISTRATOR)
                || identity.getCapabilityManager().getEffectiveFlattenedRights().contains(rightName));

        return result;
    }
    
}
