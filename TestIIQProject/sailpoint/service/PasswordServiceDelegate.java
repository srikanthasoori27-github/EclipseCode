package sailpoint.service;

import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.integration.IIQClient.PasswordService.CheckPasswordResult;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PasswordServiceDelegate
{
    private SailPointContext context;
    
    public PasswordServiceDelegate(SailPointContext context, String authUsername)
    {
        this.context = context;
    }
    
    public CheckPasswordResult checkPassword(String identityName, String password)
        throws GeneralException
    {

        PasswordPolice police = new PasswordPolice(this.context);
        
        Identity identity = null;
        if (Util.isNotNullOrEmpty(identityName))
        {
            identity = context.getObjectByName(Identity.class, identityName);
        }
        
        try
        {
            police.checkPassword(identity, password, true);
        }
        catch (PasswordPolicyException ex)
        {
            CheckPasswordResult result = new CheckPasswordResult();
            result.setStatus(CheckPasswordResult.STATUS_WARNING);
            result.setValid(false);
            result.addWarning(ex.getLocalizedMessage());
            return result;
        }
        
        CheckPasswordResult result = new CheckPasswordResult();
        result.setStatus(CheckPasswordResult.STATUS_SUCCESS);
        result.setValid(true);
        return result;
    }
    
}
