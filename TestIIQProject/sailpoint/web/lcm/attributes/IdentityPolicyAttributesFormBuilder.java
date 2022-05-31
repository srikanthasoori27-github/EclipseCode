/**
 * 
 */
package sailpoint.web.lcm.attributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Helper class that can convert a Form into an AccountRequest for identity
 * creation or modification.
 * 
 * @author peter.holcomb
 */
public class IdentityPolicyAttributesFormBuilder {

    private static final Log log = LogFactory.getLog(IdentityPolicyAttributesFormBuilder.class);
    
    protected SailPointContext context;
    private List<Message> errors;
    protected boolean hasError;

    Form form;
    String username;
    
    
    /**
     * Constructor.
     */
    public IdentityPolicyAttributesFormBuilder(SailPointContext context, Form form, String identityId)
        throws GeneralException {

        this.context = context;
        this.form = form;

        if (null != identityId) {
            Identity idnty = context.getObjectById(Identity.class, identityId);
            if (null != idnty) {
                this.username = idnty.getName();
            }
        }
    }

    /**
     * Create an AccountRequest based on the Form for this builder.  As a
     * side-effect, this also performs additional validation of the username
     * and password.  Validation errors should be retrieved by the caller using
     * getErrors() and hasError().
     */
    public AccountRequest createRequest() throws GeneralException {

        boolean isCreate = (null == this.username);
        AccountRequest.Operation op =
            (isCreate) ? AccountRequest.Operation.Create : AccountRequest.Operation.Modify;

        AccountRequest request = new AccountRequest();
        request.setApplication(ProvisioningPlan.APP_IIQ);
        request.setOperation(op);

        /** Add a unique identifier so we can get at it from the summary page grid **/
        request.addArgument("id", Util.uuid());
        
        String password = null;
        String confirm = null;
        String username = this.username;
        
        Field usernameField = form.getField(IntegrationConfig.ATT_NAME);
        if(usernameField!=null) {
            username = (String)usernameField.getValue();
            if (username != null) {
                username = username.trim();
            }
        }
        Field passwordField = form.getField(IntegrationConfig.ATT_PASSWORD);
        if(passwordField!=null) {
            password = (String)passwordField.getValue();
        }
        Field confirmField = form.getField(IntegrationConfig.ATT_PASSWORD_CONFIRM);
        if(confirmField!=null) {
            confirm = (String)confirmField.getValue();
        }
        
        if(password!=null && confirm!=null) {
            /** Check passwords/user name only if this is a non-form request **/
            if(!checkPasswordPolicy(password, confirm)) {
                hasError = true;
            }
        }  
        
        if((this.username == null || !this.username.equals(username)) && !checkIdentityName(username)) {
            hasError = true;
        }      

        request.setNativeIdentity(this.username != null ? this.username : username);
        request.addAll(createAttributeRequests(isCreate));
        
        return request;
    }
    
    protected boolean checkPasswordPolicy(String password, String confirmation) throws GeneralException {

        Configuration config = this.context.getConfiguration();
        boolean requirePassword = Util.atob(config.getString(Configuration.LCM_REQUIRE_PASSWORD_IDENTITY_CREATE));

        // Don't do any checking for a blank password if it isn't required.
        if (!requirePassword && Util.isNullOrEmpty(password)) {
            return true;
        }

        if(requirePassword  && 
                ((password==null || password.equals(""))
                        || (confirmation==null || confirmation.equals("")))) {
            getErrors().add(new Message(Message.Type.Error, MessageKeys.LCM_CREATE_IDENTITY_PASSWORD_ERROR));
            return false;
        }

        try {
            PasswordPolice pp = new PasswordPolice(this.context);
            pp.validatePasswordFields(password, confirmation, false, null);

            pp.checkPassword(null, password, false);
        }
        catch (PasswordPolicyException pve) {
            getErrors().addAll(pve.getAllMessages());
        }

        return getErrors().isEmpty();
    }
    
    /** Checks to make sure that the identity name is not currently in use by
     * another identity
     *
     * This will ensure the name is not in use for another Identity's name, AND
     * ensure the name isn't that of another Identity's ID. Setting name to a value
     * of an ID causes all kinds of issues with calling context.getObject()
     *
     * @param name
     * @return
     * @throws GeneralException
     */
    protected boolean checkIdentityName(String name) throws GeneralException{
        if(name==null || name.equals("")) {
            getErrors().add(new Message(Message.Type.Error, MessageKeys.LCM_CREATE_IDENTITY_NO_NAME_ERROR));
            return false;
        } else {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.or(Filter.eq("id", name), Filter.ignoreCase(Filter.eq("name", name))));
            qo.add(ObjectUtil.buildWorkgroupInclusiveIdentityFilter());
            int count = this.context.countObjects(Identity.class,qo);
            if(count>0) {
                Message message = new Message(Message.Type.Error, MessageKeys.LCM_CREATE_IDENTITY_NAME_ERROR);
                getErrors().add(message);
                return false;
            }
        }
        
        return true;
    }

    private List<AttributeRequest> createAttributeRequests(boolean isCreate)
        throws GeneralException {

        List<AttributeRequest> requests = new ArrayList<AttributeRequest>();

        Iterator<Field> fieldIt = form.iterateFields();

        while (fieldIt.hasNext()) {
            Field field = fieldIt.next();
            String name = field.getName();

            // Ignore the confirm password field.
            if(IntegrationConfig.ATT_PASSWORD_CONFIRM.equals(name)) {
                continue;                
            }
            
            Object value = field.getValue();

            // For creation requests we only want to set the value if there is
            // one.  For modify requests, we will set the value even if it is
            // null so that attribute values can be cleared.
            boolean nullValue = ((null == value) || "".equals(value));
            if (!nullValue || !isCreate) {

                if(value instanceof List && ((List<?>)value).isEmpty())
                    continue;

                /** With identities, we need to convert the id value to the name value and fix the key **/
                if (!nullValue && "sailpoint.object.Identity".equals(field.getType())) {
                    try {
                        Identity ident = this.context.getObjectById(Identity.class, (String)value);
                        if(ident!=null)
                            value = ident.getName();
                    } catch(GeneralException ge) {
                        log.warn("Unable to load identity for value: " + value + ". Exception: " + ge.getMessage());
                    }

                    name = form.getIdentityFieldName(name);
                }

                if (IntegrationConfig.ATT_NAME.equals(name) && value instanceof String) {
                    value = ((String)value).trim();
                }

                AttributeRequest attr = new AttributeRequest();
                attr.setOperation(Operation.Set);
                attr.setName(name);
                attr.setValue(value);
                attr.put(ProvisioningPlan.ARG_TYPE, field.getType());
                attr.put(ProvisioningPlan.ARG_REQUIRED, field.isRequired());

                // Mark any secret fields as secret in the plan.
                if (Field.TYPE_SECRET.equals(field.getType())) {
                    attr.put(ProvisioningPlan.ARG_SECRET, "true");
                }
                
                requests.add(attr);
            }
        }

        return requests;
    }

    public List<Message> getErrors() {
        if(errors==null) {
            errors = new ArrayList<Message>();
        }
        return errors;
    }

    public boolean hasError() {
        return this.hasError;
    }
}
