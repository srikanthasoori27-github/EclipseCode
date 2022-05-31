package sailpoint.service;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.object.Identity;
import sailpoint.service.IdentityAttributesDTO.IdentityAttributeDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.web.QuickLinkDTO;
import sailpoint.web.UserContext;

/**
 * Service to authorized and return details given an identity.
 */
public class LCMIdentityDetailsService extends IdentityDetailsService{
    
    private QuickLinkDTO quickLink;
    private UserContext userContext;
    private SailPointContext context;
    
    /**
     * Constructor to call super and collect information for LCM authorization
     * @param identity Identity for this service
     * @param quickLink which quickLink did user call this service from
     * @param userContext User Context
     * @throws InvalidParameterException
     */
    public LCMIdentityDetailsService(Identity identity, QuickLinkDTO quickLink, UserContext userContext) throws GeneralException{
        super(identity);
        this.quickLink = quickLink;
        this.userContext = userContext;
        this.context = userContext.getContext();
    }
    
    /**
     * Gets an IdentityAttributesDTO for this identity but only ones matching the keys passed in. Check if 
     * Logged in user is authorized to view attribute if it's an identity. Returns in key order.
     * This is similar to getIdentityDetails(), but returns a DTO with more information.
     *
     * @param locale the locale to apply to any message keys
     * @param timeZone the timeZone to use on any messages
     * @param filterKeys the identity attribute keys to include and the order to include them in
     *
     * @return The IdentityAttributesDTO for this identity.
     */
    @Override
    public IdentityAttributesDTO getIdentityAttributesDTO(Locale locale,
            TimeZone timeZone, List<String> filterKeys) throws GeneralException{
        IdentityAttributesDTO attr = super.getIdentityAttributesDTO(locale, timeZone, filterKeys);
        validateAttributeLinks(attr);
        return attr;
    }

    /**
     * Check if logged in user is authorized to view identity link, update authorizedToView property for the attributeDTO
     * @param attrsDTO identity detail attributes DTO
     * @throws GeneralException
     */
    private void validateAttributeLinks(IdentityAttributesDTO attrsDTO) throws GeneralException{
        List<IdentityAttributeDTO> attrList = attrsDTO.getAttributes();
        for (IdentityAttributeDTO attr : attrList) {
            // Identity links are set authorized to view by default
            if (attr.isAuthorizedToView()) {
                if(!authorizeView(attr)) {
                    attr.setAuthorizedToView(false);
                }
            }
        }
    }
    
    /**
     * Check if logged in user is authorized to view identity link
     * @param attr
     * @return true is logged in user can view the identity in the IdentityAttributeDTO value
     * @throws GeneralException
     */
    private boolean authorizeView(IdentityAttributeDTO attr) throws GeneralException{
        boolean auth = false;
        if (attr.getValue() instanceof IdentitySummaryDTO) {
            IdentitySummaryDTO identity = (IdentitySummaryDTO)attr.getValue();
            auth = authorizeByQuickLink(identity);  
        }
        return auth;
    }
    
    /**
     * Authorize by quick link name, default to quick link action
     * @param identitySummary identitySummary of the identity to be viewed
     * @return true if logged user is authorized for the quicklink action on the requestee
     * @throws GeneralException
     */
    private boolean authorizeByQuickLink(IdentitySummaryDTO identitySummary) throws GeneralException{
        boolean auth = false;
        try {
            Identity requestee = this.context.getObjectById(Identity.class, identitySummary.getId());
            LcmRequestAuthorizer authorizer = new LcmRequestAuthorizer(requestee);
            authorizer.setQuickLinkName(this.quickLink.getName());
            authorizer.setAction(this.quickLink.getAction());
            AuthorizationUtility.authorize(userContext, authorizer);
            auth = true;
        }
        catch (GeneralException e) {
            auth = false;
        }
        return auth;
    }
}