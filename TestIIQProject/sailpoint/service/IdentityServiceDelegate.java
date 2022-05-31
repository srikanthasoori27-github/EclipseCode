package sailpoint.service;

import java.util.Map;
import java.util.Map.Entry;

import sailpoint.api.Identitizer;
import sailpoint.api.SailPointContext;
import sailpoint.integration.IIQClient.IdentityService.Consts;
import sailpoint.integration.IIQClient.IdentityService.CreateOrUpdateResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Identity;
import sailpoint.object.Source;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class IdentityServiceDelegate
{
    private SailPointContext context;

    public IdentityServiceDelegate(SailPointContext context, String authUsername)
    {
        this.context = context;
    }
    
    public CreateOrUpdateResult createIdentity(
            String identityName,
            Map<String, Object> attributes)
            throws GeneralException
    {
        
        Identity identity = this.context.getObjectByName(
                Identity.class,
                identityName);
        if (identity != null)
        {
            return getFailedWithReasonResult(new Message(
                    MessageKeys.WS_IDENTITY_EXISTS,
                    identityName).getLocalizedMessage());
        }

        identity = new Identity();
        identity.setName(identityName);

        CreateOrUpdateResult updateResult = updateAttributes(
                identity,
                attributes);
        if (updateResult.isSuccess() == false)
        {
            return updateResult;
        }

        saveAndTrigger(identity);

        return getSuccessResult();
    }

    private void saveAndTrigger(Identity ident)
        throws GeneralException {

        Identitizer idz = new Identitizer(context);
        idz.setRefreshSource(Source.WebService, context.getUserName());
        // jsl - is this necessary? shouldn't be...
        idz.setProcessTriggers(true);
        
        idz.saveAndTrigger(ident, true);
    }

    public CreateOrUpdateResult createOrUpdateIdentity(
            String identityName,
            Map<String, Object> attributes) throws GeneralException
    {
        Identity identity = this.context.getObjectByName(
                                                         Identity.class,
                                                         identityName);
        if (identity == null)
        {
            return createIdentity(identityName, attributes);
        }

        CreateOrUpdateResult updateResult = updateAttributes(identity, attributes);
        if (updateResult.isSuccess() == false)
        {
            return updateResult;
        }

        saveAndTrigger(identity);

        return getSuccessResult();
    }
    
    private CreateOrUpdateResult updateAttributes(Identity identity, Map<String, Object> attributes)
        throws GeneralException
    {
        for (Entry<String, Object> entry : attributes.entrySet())
        {
            IAttributeSetter setter = getAttributeSetter(entry.getKey());
            if (setter == null)
            {
                return getFailedWithReasonResult(new Message(MessageKeys.WS_ATTR_NOT_DEFINED, entry.getKey()).getLocalizedMessage());
            }

            setter.setAttribute(identity, entry.getValue());
            
        }

        return getSuccessResult();
    }
    
    interface IAttributeSetter
    {
        void setAttribute(Identity identity, Object value) throws GeneralException;
    }
    
    public IAttributeSetter getAttributeSetter(final String attributeName)
        throws GeneralException
    {
        if (Consts.AttributeNames.FIRST_NAME.equalsIgnoreCase(attributeName))
        {
            return new IAttributeSetter()
            {
                public void setAttribute(Identity identity, Object value)
                {
                    identity.setFirstname((String) value);
                }
            };
        }
        else if (Consts.AttributeNames.LAST_NAME.equalsIgnoreCase(attributeName))
        {
            return new IAttributeSetter()
            {
                public void setAttribute(Identity identity, Object value)
                {
                    identity.setLastname((String)value);
                }
            };
        }
        else if (Consts.AttributeNames.EMAIL.equalsIgnoreCase(attributeName))
        {
            return new IAttributeSetter()
            {
                public void setAttribute(Identity identity, Object value)
                {
                    identity.setEmail((String)value);
                }
            };
        }
        else if (Consts.AttributeNames.MANAGER.equalsIgnoreCase(attributeName))
        {
            return new IAttributeSetter()
            {
                public void setAttribute(Identity identity, Object value)
                {
                    try
                    {
                        String managerName = (String) value;
                        Identity manager = IdentityServiceDelegate.this.context.getObjectByName(Identity.class, managerName);
                        if (manager == null)
                        {
                            throw new IllegalStateException("Manager: " + managerName + " does not exist");
                        }
                        identity.setAttribute(Identity.ATT_MANAGER, manager);
                    }
                    catch(GeneralException ex)
                    {
                        throw new IllegalStateException("Error setting Manager: " + value);
                    }
                }
            };
        }
        else if (Consts.AttributeNames.PASSWORD.equalsIgnoreCase(attributeName))
        {
            return new IAttributeSetter()
            {
                public void setAttribute(Identity identity, Object value)
                {
                    identity.setPassword((String)value);
                }
            };
        }

        if (!checkCustomAttribute(attributeName))
        {
            return null;
        }

        return new IAttributeSetter()
        {
            public void setAttribute(Identity identity, Object value)
            {
                identity.setAttribute(attributeName, value);
            }
        };
    }
    
    private boolean checkCustomAttribute(String attributeName)
    {
        return (Identity.getObjectConfig().getObjectAttribute(attributeName) != null);
    }
    
    private CreateOrUpdateResult getSuccessResult()
    {
        CreateOrUpdateResult result = new CreateOrUpdateResult();
        result.setStatus(RequestResult.STATUS_SUCCESS);
        result.setPerformed(true);
        return result;
    }
    
    private CreateOrUpdateResult getFailedWithReasonResult(String reason)
    {
        CreateOrUpdateResult result = new CreateOrUpdateResult();
        result.setStatus(RequestResult.STATUS_WARNING);// it just means no exception
        result.setPerformed(false);
        result.addWarning(reason);

        return result;
    }
}
