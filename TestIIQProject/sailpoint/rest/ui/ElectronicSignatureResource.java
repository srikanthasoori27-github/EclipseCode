/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui;

import sailpoint.authorization.IdentityMatchAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.ElectronicSignature;
import sailpoint.object.Identity;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.util.Map;

@Path("electronicSignature")
public class ElectronicSignatureResource extends BaseResource {

    /**
     * Authentication the signature account/password.
     * @param inputs Map from JSON request body, including signature account and password
     * @return SuccessResult object
     * @throws sailpoint.tools.GeneralException
     */
    @POST
    @Path("check")
    public SuccessResult checkSignature(Map<String, Object> inputs) throws GeneralException {

        SuccessResult result = new SuccessResult(true);
        ElectronicSignature signature = getSignature(inputs);
        if (signature == null || Util.isNullOrEmpty(signature.getPassword())) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_INVALID_SIGNATURE_DATA));
        }
        try {
            Identity identity = getNotary().authenticate(signature.getAccountId(), signature.getPassword());
            // Authorize that the authenticated identity matches logged in user
            authorize(new IdentityMatchAuthorizer(identity));
        } catch (GeneralException | ExpiredPasswordException e) {
            result.setSuccess(false);
            if (reportDetailedLoginErrors()) {
                result.setMessage(e.getLocalizedMessage(getLocale(), getUserTimeZone()));
            }
            if (Util.isNullOrEmpty(result.getMessage())) {
                result.setMessage(new Message(MessageKeys.ESIG_POPUP_AUTH_FAILURE).getLocalizedMessage(getLocale(), getUserTimeZone()));
            }
        }
        return result;
    }
}
