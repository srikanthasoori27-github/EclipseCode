package sailpoint.web.identity;

import java.util.List;

import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolice.Expiry;
import sailpoint.api.PasswordPolicyException;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.SPRight;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * Backing bean for Password information on View Identity Page, Attributes Tab
 * This is initialized by IdentityDTO
 */
public class PasswordHelper {

    /** indicates that there was an error changing the password and keeps the password field open **/
    private boolean passwordError;
    private IdentityDTO parent;
    
    public PasswordHelper(IdentityDTO parent) {
        this.parent = parent;
    }
    
    public boolean isPasswordError() {
        return this.passwordError;
    }

    public void setPasswordError(boolean error) {
        this.passwordError = error;
    }

    public boolean isExpirePassword() {
        return this.parent.getState().isExpirePassword();
    }

    public void setExpirePassword(boolean expirePassword) {
        this.parent.getState().setExpirePassword(expirePassword);
    }

    /**
     * Password is a pseudo property we maintain in the EditSession
     * until we're ready to commit.  It is always sent out
     * as a fake value so the real password won't appear on the wire.
     */
    public String getPassword() {
        return PasswordPolice.FAKE_PASSWORD;
    }

    public void setPassword(String s) {
        // only start saving this if they type something
        if (s != null && !s.equals(PasswordPolice.FAKE_PASSWORD))
            this.parent.getState().setPassword(s);
    }

    /**
     * Password confirmation is handled like password, the actual
     * value is never sent to the browser.
     */
    public String getConfirmPassword() {
        return PasswordPolice.FAKE_PASSWORD;
    }

    public void setConfirmPassword(String s) {
        // only start saving this if they type something
        if (s != null && !s.equals(PasswordPolice.FAKE_PASSWORD))
            this.parent.getState().setConfirmPassword(s);
    }

    /**
     * Will check password policy
     * If there are any violations, it will set error
     * messages.
     * 
     * @return true if no violations are found
     */
    boolean checkPasswordPolicy() throws GeneralException {

        Identity ident = this.parent.getObject();
        List<Message> errors = null;
        String password = this.parent.getState().getPassword();
        String confirmation = this.parent.getState().getConfirmPassword();

        try {
            PasswordPolice pp = new PasswordPolice(this.parent.getContext());
            pp.validatePasswordFields(password, confirmation, false, null);

            if (null != password) {
                pp.checkPassword(ident, password, this.parent.isSystemAdmin());
                PasswordPolice.Expiry expires;
                if (this.parent.getState().isExpirePassword()) {
                    expires = PasswordPolice.Expiry.EXPIRE_NOW;
                }
                else {
                    expires = PasswordPolice.Expiry.USE_RESET_EXPIRY;
                }
                    
                pp.setPasswordExpiration(ident, expires);
            }
        }
        catch (PasswordPolicyException pve) {
            errors = pve.getAllMessages();
        }

        if (errors != null && errors.size() > 0) {
            // there isn't enough room in the password table for the
            // message, it wraps in a most ugly way, just put
            // it at the top of the page
            for (Message error : errors) {
                this.parent.addMessage(error, null);
            }
            
            this.passwordError = true;
        }

        // return true if no errors
        return (errors == null || errors.size() == 0);
    }
    
    /**
     *  @see #addPasswordChangesToAccountRequest(Identity, String, AccountRequest, Expiry)
     */
    public static void addPasswordChangesToAccountRequest(Identity requester, String password, AccountRequest account) throws GeneralException {
        addPasswordChangesToAccountRequest(requester, password, account, null);
    }
    
    /**
     * Will add password change information to IIQ request
     * 
     * IMP: It assumes that password has already been validated
     * 
     * @param requester Identity requesting the password change
     * @param password if null or empty then this method is a no-op
     * @param account IIQ account request
     * @param expiry Expiry to use when processing the request
     * @throws GeneralException unlikely ever be thrown
     */
    public static void addPasswordChangesToAccountRequest(Identity requester, String password, AccountRequest account, Expiry expiry) throws GeneralException {

        if (Util.isNullOrEmpty(password)) {
        	return;
        }

        AttributeRequest attrReq = new AttributeRequest(ProvisioningPlan.ATT_PASSWORD, ProvisioningPlan.Operation.Set, password);
        account.add(attrReq);
        
        if (requester != null) {
            CapabilityManager capManager = requester.getCapabilityManager();
            boolean isSystemAdmin = Capability.hasSystemAdministrator(capManager.getEffectiveCapabilities());
            attrReq.put(PlanEvaluator.ARG_REQUESTER_IS_SYSTEM_ADMIN, isSystemAdmin);
            boolean isPasswordAdmin =  isSystemAdmin || capManager.getEffectiveFlattenedRights().contains(SPRight.SetIdentityPassword);
            attrReq.put(PlanEvaluator.ARG_REQUESTER_IS_PASSWORD_ADMIN, isPasswordAdmin);
        }
        if (expiry != null) {
            attrReq.put(ProvisioningPlan.ARG_PASSWORD_EXPIRY, expiry.toString());
        }

    }
}
