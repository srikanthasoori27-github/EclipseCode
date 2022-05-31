package sailpoint.service.form;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.Authorizer;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QuickLink;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.UserContext;
import sailpoint.web.lcm.IdentityProvisioningPolicyHelper;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ProvisioningPolicyFormStore extends BaseFormStore {

    public static final String LCM_PROVISIONING_MASTER_FORM = "lcm_update_master_form";
    public static final String LCM_PROVISIONING_EXPANDED_FORM = "lcm_update_expanded_form";
    public static final String LCM_PROVISIONING_IDENTITY = "lcm_update_identity";
    public static final String LCM_PROVISIONING_ACTION = "lcm_update_action";

    /**
     * Constructor for BaseFormStore.
     *
     * @param userContext The user context.
     */
    public ProvisioningPolicyFormStore(UserContext userContext) {
        super(userContext);
    }

    /**
     * Constructor signature necessary for reconstructing this object through reflection.
     *
     * @param userContext The user context.
     * @param state The form store state.
     */
    public ProvisioningPolicyFormStore(UserContext userContext, Map<String, Object> state) {
        this(userContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Authorizer getAuthorizer(boolean isRead) throws GeneralException {
        return new AllowAllAuthorizer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Form retrieveMasterForm() throws GeneralException {
        Object formAction = sessionStorage.get(LCM_PROVISIONING_ACTION);
        if (QuickLink.LCM_ACTION_EDIT_IDENTITY.equals(formAction) &&
                !sessionStorage.containsKey(LCM_PROVISIONING_IDENTITY)) {
            throw new ObjectNotFoundException(Message.error(MessageKeys.ERR_OBJ_NOT_FOUND));
        }

        /* If the master form is stored on the session use that.  Otherwise build the form. */
        if (sessionStorage.get(LCM_PROVISIONING_MASTER_FORM) != null) {
            this.masterForm = (Form) sessionStorage.get(LCM_PROVISIONING_MASTER_FORM);
            return masterForm;
        }

        IdentityProvisioningPolicyHelper helper = new IdentityProvisioningPolicyHelper(getContext(), userContext);
        Identity identity = (Identity) sessionStorage.get(LCM_PROVISIONING_IDENTITY);

        Form masterForm;
        if(QuickLink.LCM_ACTION_EDIT_IDENTITY.equals(formAction)) {
            masterForm = helper.createMasterUpdateIdentityForm();
        } else if(QuickLink.LCM_ACTION_CREATE_IDENTITY.equals(formAction)) {
            masterForm = helper.createMasterCreateIdentityForm();
        } else {
            throw new GeneralException("Unsupported action:" + formAction);
        }
        helper.populateForm(masterForm, identity, new ArrayList<ProvisioningPlan.AccountRequest>());
        this.masterForm = masterForm;
        return masterForm;
    }

    /**
     * Stores the master form on the session storage
     *
     * @param form The expanded form.
     */
    @Override
    public void storeMasterForm(Form form) {
        super.storeMasterForm(form);
        sessionStorage.put(LCM_PROVISIONING_MASTER_FORM, form);
    }

    /**
     * Clears the master form off of the session storage
     */
    @Override
    public void clearMasterForm() {
        super.clearMasterForm();
        sessionStorage.remove(LCM_PROVISIONING_MASTER_FORM);
    }

    @Override
    public Form retrieveExpandedForm() throws GeneralException {
        if (sessionStorage.get(LCM_PROVISIONING_EXPANDED_FORM) != null) {
            return (Form) sessionStorage.get(LCM_PROVISIONING_EXPANDED_FORM);
        }
        return super.retrieveExpandedForm();
    }

    /**
     * Stores the expanded form as the active form on the workflow session.
     *
     * @param form The expanded form.
     */
    @Override
    public void storeExpandedForm(Form form) {
        super.storeExpandedForm(form);
        sessionStorage.put(LCM_PROVISIONING_EXPANDED_FORM, form);
    }

    /**
     * Clears the active form off of the workflow session.
     */
    @Override
    public void clearExpandedForm() {
        super.clearExpandedForm();
        sessionStorage.remove(LCM_PROVISIONING_EXPANDED_FORM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormDTO retrieveFormDTO() throws GeneralException {
        FormDTO dto = getFormRenderer(null).createDTO();
        return dto;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getFormBeanState() {
        return new HashMap<String, Object>();
    }


    /**
     * Returns the form arguments
     * @return The form arguments
     */
    @Override
    public Map<String,Object> getFormArguments() throws GeneralException {
        Identity identity = (Identity) sessionStorage.get(LCM_PROVISIONING_IDENTITY);
        Map<String, Object> args;

        if (this.formRenderer != null) {
            args = this.formRenderer.getData();
            if (args == null) {
                args = new HashMap<String, Object>();
            }
            // IIQETN-5270 we should send identityId by default
            // identity will be null for a create operation, and will contain
            // the identity being managed for an update operation
            if (identity != null) {
                args.put("identityId", identity.getId());
            }
            return args;
        }

        if (identity != null) {
            args = new HashMap<String, Object>();
            args.put("identityId", identity.getId());
            return args;
        }

        return new HashMap<String, Object>();
    }

    /**
     * Set the identity on the store
     * @param identity The identity the form is for
     */
    public void setIdentity(Identity identity) {
        if(sessionStorage.get(ProvisioningPolicyFormStore.LCM_PROVISIONING_IDENTITY) != identity) {
            this.clearMasterForm();
            this.clearExpandedForm();
        }
        sessionStorage.put(ProvisioningPolicyFormStore.LCM_PROVISIONING_IDENTITY, identity);
    }

    /**
     * Set the store's action
     * @param action The lcm action
     */
    public void setAction(String action) {
        sessionStorage.put(ProvisioningPolicyFormStore.LCM_PROVISIONING_ACTION, action);
    }
}
