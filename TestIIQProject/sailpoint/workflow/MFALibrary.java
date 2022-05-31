/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.workflow;

import java.io.UnsupportedEncodingException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.rpc.ServiceException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rsa.authmgr.common.SecurIDAuthenticationConstants;
import com.rsa.common.AuthenticationConstants;
import com.rsa80.authn.LoginCommand;
import com.rsa80.authn.LogoutCommand;
import com.rsa80.authn.data.AbstractParameterDTO;
import com.rsa80.authn.data.FieldParameterDTO;
import com.rsa80.authn.data.MessageDTO;
import com.rsa80.webservice.CommandServerServiceLocator;
import com.rsa80.webservice.CommandServerSoapBindingStub;

import openconnector.connector.Duo.DuoConnector;
import openconnector.connector.Duo.Http;
import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.RequiredArgumentException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Workflow library used in MultiFactor Authentication workflow providers.
 */
public class MFALibrary {
    
    public static String VAR_APPLICATION_NAME = "applicationName";
    public static String VAR_NATIVE_USER_ID_ATTRIBUTE = "nativeUserIdAttribute";
    public static String VAR_MFA_RESPONSE_MESSAGE = "mfaResponseMessage";
    
    public static String RSA_AUTH_PARAMS = "rsaAuthParams";
    public static String RSA_AUTH_CMD = "rsaAuthCmd";

    /* These two static password constants show up in the 8.1 rsa docs, however it is not in our version
     * of the library.  This may be due to a change in the API.  Since we don't have that version, I
     * will define them here, but if later we have a library version that has them, we should use the
     * ones from the library instead.
     */
    public static final String USER_NEW_STATIC_PASSCODE = "USER_NEW_STATIC_PASSCODE";
    public static final String USER_NEW_STATIC_PASSCODE_CONFIRM = "USER_NEW_STATIC_PASSCODE_CONFIRM";

    protected static class DuoCred {
        String host;
        String integrationKey;
        String secretKey;
    }
    
    private static final Log log = LogFactory.getLog(MFALibrary.class);
    
    public String duoAuthenticate(WorkflowContext wfc) throws GeneralException, UnsupportedEncodingException, Exception {
        Attributes<String,Object> args = wfc.getArguments();
        String nativeUserId = getNativeUserId(wfc);
        
        String appName = Util.getString(args, VAR_APPLICATION_NAME);
        DuoCred cred = getDuoCredentials(wfc.getSailPointContext(), appName);

        String _userUri = "/auth/v2/auth";
        String device = Util.getString(args, "device");
        String method = Util.getString(args, "method");
      
        Http duoClient = new Http("POST", cred.host, _userUri);
        duoClient.addParam("username", nativeUserId);
        duoClient.addParam("factor", method);
        duoClient.addParam("device", device);
        duoClient.addParam("async", "1");
        
        duoClient.signRequest(cred.integrationKey, cred.secretKey);
        JSONObject obj = (JSONObject) duoClient.executeRequest();
        JSONObject response = (JSONObject) obj.get("response");
      
        return response.getString("txid");
    }
    
    public MFAAuthResponse duoAuthenticationStatus(WorkflowContext wfc) throws Exception {
        Attributes<String, Object> args = wfc.getArguments();

        String txid = args.getString("txid");

        String appName = Util.getString(args, VAR_APPLICATION_NAME);
        DuoCred cred = getDuoCredentials(wfc.getSailPointContext(), appName);

        String _statusUri = "/auth/v2/auth_status";

        Http duoClient = new Http("GET", cred.host, _statusUri);
        duoClient.addParam("txid", txid);

        duoClient.signRequest(cred.integrationKey, cred.secretKey);
        JSONObject obj = (JSONObject) duoClient.executeRequest();

        return responseToAuthResponse(obj);
    }
    
    public static void addMFAMessage(WorkflowContext wfc) {
        String message = null;
        Object object = wfc.get("response");
        if (object instanceof MFAAuthResponse) {
            MFAAuthResponse response = (MFAAuthResponse)object;
            message = response.getStatusMessage();
        }
        if (log.isInfoEnabled()) {
            log.info(getIdentityName(wfc) + " received a possible error during multi-factor authentication: " + message);
        }
        // obscure real messages from potential hackers. we're dealing with an unauthenticated session, so be very careful about
        // what messaging we expose to potential hackers 
        message = new Message(MessageKeys.UI_MFA_FAILED, (Object)null).getLocalizedMessage();
        
        wfc.setVariable(VAR_MFA_RESPONSE_MESSAGE, message);
    }
    
    public static String getNativeUserId(WorkflowContext wfc) throws GeneralException {
        String nativeUserId = null;
        String identityName = wfc.getString(IdentityLibrary.VAR_IDENTITY_NAME);
        String appName = wfc.getString(VAR_APPLICATION_NAME);
        String nativeUserAttribute = wfc.getString(VAR_NATIVE_USER_ID_ATTRIBUTE);
        if (Util.isAnyNullOrEmpty(identityName, nativeUserAttribute, appName)) {
            throw new IllegalArgumentException("identityName, nativeUserIdAttribute or applicationName can not be empty");
        }
        
        try {
            SailPointContext context = wfc.getSailPointContext();
            Identity ident = context.getObjectByName(Identity.class, identityName);
            Application app = context.getObjectByName(Application.class, appName);
            
            IdentityService idService = new IdentityService(context);
            List<Link> links = idService.getLinks(ident, app);
            if (Util.size(links) != 1) {
                throw new IllegalStateException("Identity contains more than one account or no accounts for application: " + appName);
            }
            
            nativeUserId = Util.getString(links.get(0).getAttributes(), nativeUserAttribute);
            
        } catch (GeneralException e) {
            log.error(String.format("An exception occurred getting the native identity for %s and application %s", identityName, appName), e);
            throw e;
        }
        
        return nativeUserId; 
    }
    
    public static String duoGetDevices(SailPointContext context, String applicationName, String name) throws GeneralException, UnsupportedEncodingException, Exception {
        
        DuoCred cred = getDuoCredentials(context, applicationName);
        String _userUri = "/auth/v2/preauth";
      
        Http duoClient = new Http("POST", cred.host, _userUri);
        duoClient.addParam("username", name);
        
        duoClient.signRequest(cred.integrationKey, cred.secretKey);
        JSONObject obj = (JSONObject) duoClient.executeRequest();
        if (log.isDebugEnabled()) {
            // TODO any sensitive data here
            log.debug("preauth=" + obj);
        }

        return obj.toString();
    }
    
    public static List<List<String>> responseToDevice(Field field, String preAuthResponse) throws GeneralException {
        List<List<String>> avList = new ArrayList<List<String>>();
        try {
            validateField(field);
            JSONObject obj = new JSONObject(preAuthResponse);
            
            JSONObject response = (JSONObject) obj.get("response");                
            JSONArray devices = response.optJSONArray("devices");
    
            for(int i = 0; devices != null && i < devices.length(); i++) {
                JSONObject dev = (JSONObject) devices.get(i);
                List<String> devValue = new ArrayList<String>();
                devValue.add(dev.getString("device"));
                devValue.add(dev.getString("display_name"));
                avList.add(devValue);
            }
        } catch (JSONException e) {
            throw new GeneralException(e);
        }
        
        // set a default on the field
        if (field.getValue() == null && !Util.isEmpty(avList)) {
            field.setValue(avList.get(0).get(0));
        }

        return avList;
    }
    
    public static List<List<String>> responseToMethod(Field field, String preAuthResponse, String selectedDevice, boolean allowSMSAuth) throws GeneralException {
        List<List<String>> methodList = new ArrayList<List<String>>();
        boolean initialPageLoad = false;
        validateField(field);
        try {
            // on initial page load and no device is selected, set a default later
            if (Util.isNothing(selectedDevice)) {
                initialPageLoad = true;
            }
            
            JSONObject obj = new JSONObject(preAuthResponse);
            
            JSONObject response = (JSONObject) obj.get("response");                
            JSONArray devices = response.optJSONArray("devices");
            if (log.isDebugEnabled()) {
              log.debug("devices=" + devices);
              log.debug("device0=" + (devices != null ? devices.get(0) : null));
              log.debug("length=" + (devices != null ? devices.length() : null));
            }
            
            for(int i = 0; devices != null && i < devices.length(); i++) {
                JSONObject dev = (JSONObject) devices.get(i);
                String deviceId = dev.getString("device");
                if (null != selectedDevice && selectedDevice.equals(deviceId) || (initialPageLoad && i == 0)) {
                    if (log.isDebugEnabled()) {
                      log.debug("device matches!!!");
                    }
                    
                    // add the selected device capabilities here
                    JSONArray methodArray = dev.getJSONArray("capabilities");
                    if (log.isDebugEnabled()) {
                      log.debug("methodArray=" + methodArray);
                    }
                    
                    for (int j = 0; j < methodArray.length(); j++) {
                        List<String> methodMember = new ArrayList<String>();
                        
                        String methodId = methodArray.getString(j);
                        // don't allow sms auth
                        if (!allowSMSAuth && "sms".equals(methodId)) {
                            if (log.isInfoEnabled()) {
                                log.info("removing SMS authentication from list of DUO device capabilities");
                            }
                            continue;
                        }
                        methodMember.add(methodId);
                        String methodDisplay = methodId;
                        
                        if ("sms".equals(methodId)) 
                          methodDisplay = "ui_mfa_duo_form_method_sms";
                        else if ("phone".equals(methodId)) methodDisplay = "ui_mfa_duo_form_method_call";
                        else if ("auto".equals(methodId)) methodDisplay = "ui_mfa_duo_form_method_auto";
                        else if ("push".equals(methodId)) methodDisplay = "ui_mfa_duo_form_method_push";
                        else if ("mobile_otp".equals(methodId)) methodDisplay = "ui_mfa_duo_form_method_mobileotp";
                        
                        methodMember.add(methodDisplay);
                        methodList.add(methodMember);
                    }
                }
            }
            
            // set a default method if initial page load
            if (initialPageLoad && !Util.isEmpty(methodList) && field.getValue() == null) {
                field.setValue(methodList.get(0).get(0));
            }
        } catch (JSONException e) {
            throw new GeneralException(e);
        }

        return methodList;
    }
    
    public static MFAAuthResponse responseToAuthResponse(String preAuthResponse) throws GeneralException {
        JSONObject obj = null;
        try {
            obj = new JSONObject(preAuthResponse);
        } catch (JSONException e) {
            log.warn(e);
            obj = new JSONObject();
        }
        return responseToAuthResponse(obj);
    }
    
    public static MFAAuthResponse responseToAuthResponse(JSONObject obj) throws GeneralException {
        MFAAuthResponse authResponse = new MFAAuthResponse();
        try {
            JSONObject response = (JSONObject) obj.get("response");
            if(response.has("result")) {
                authResponse.setResult(response.getString("result"));
            }
            if(response.has("status_msg")) {
                authResponse.setStatusMessage(response.getString("status_msg"));
            }
            if(response.has("trusted_device_token")) {
                authResponse.setTrustedDeviceToken(response.getString("trusted_device_token"));
            }
        } catch (JSONException e) {
            throw new GeneralException(e);
        }
        return authResponse;
    }
    
    protected static String getIdentityName(WorkflowContext wfc) {
        return wfc.getString(IdentityLibrary.VAR_IDENTITY_NAME);
    }
    
    protected static DuoCred getDuoCredentials(SailPointContext context, String appName) throws GeneralException {
        if (appName == null) {
            throw new NullPointerException("Argument applicationName is required.");
        }
        Application app = context.getObjectByName(Application.class, appName);
        if (app == null) {
            throw new ObjectNotFoundException(Application.class, appName);
        }
        DuoCred result = new DuoCred();
        
        result.host = app.getStringAttributeValue(DuoConnector.CONFIG_DUO_AUTH_API_HOST);
        result.integrationKey = app.getStringAttributeValue(DuoConnector.CONFIG_DUO_AUTH_INTEGRATION_KEY);
        result.secretKey = context.decrypt(app.getStringAttributeValue(DuoConnector.CONFIG_DUO_AUTH_SECRET_KEY));
        
        if (log.isDebugEnabled()) {
            // Do not log actual values here, just if they are null or not, these are considered highly sensitive data
            log.debug(String.format("api host is null? %s, integration key is null? %s, secretKey is null? %s", (result.host == null),
                    (result.integrationKey == null), (result.secretKey == null)));
        }
        
        if (Util.isAnyNullOrEmpty(result.host, result.integrationKey, result.secretKey)) {
            throw new RequiredArgumentException(app.getName() + " has not configured Duo auth credential information in the application definition.");
        }
        
        return result;
    }
    
    protected static void validateField(Field field) {
        if (field == null) {
            throw new NullPointerException("field must not be null");
        }
    }
    
    public MFAAuthResponse rsaLogin(WorkflowContext wfc) throws GeneralException, UnsupportedEncodingException, Exception {
        MFAAuthResponse response = new MFAAuthResponse();
        response.setResult("ACCESS_DENIED");
        wfc.setVariable(VAR_MFA_RESPONSE_MESSAGE, null);
        
        CommandServerSoapBindingStub binding = getBinding(wfc);
        LoginCommand cmd = getLoginCommand(wfc);
        if(cmd == null) {
            response.setResult("ACCESS_DENIED");
            return response;
        }

        try {
            cmd =  (LoginCommand)binding.executeCommand(null, cmd);
        } catch (Exception e) {
            log.warn(e);
            return response;
        }
        
        if (log.isInfoEnabled() && cmd != null && cmd.getMessage() != null) {
            MessageDTO messDTO = cmd.getMessage();
            log.info(String.format("RSA ReasonCode: %s, Key: %s, Args: %s", messDTO.getReasonCode(), messDTO.getKey(), Arrays.toString(messDTO.getArguments())));
        }

        if (AuthenticationConstants.ACCESS_DENIED.equals(cmd.getAuthenticationState())) {
            response.setResult("ACCESS_DENIED");
        } else if(AuthenticationConstants.FAILED.equals(cmd.getAuthenticationState())) {
            response.setResult("FAILED");
            Message mfaResponseMessage = toMessage(cmd.getMessage());
            wfc.setVariable(VAR_MFA_RESPONSE_MESSAGE, mfaResponseMessage.toString());
        } else if(AuthenticationConstants.AUTHENTICATED.equals(cmd.getAuthenticationState())) {
            response.setResult("ACCESS_OK");
            rsaLogout(wfc, cmd.getSessionId());
        } else if(AuthenticationConstants.IN_PROGRESS.equals(cmd.getAuthenticationState())) {
            AbstractParameterDTO[] params = cmd.getParameters();
            wfc.setVariable(RSA_AUTH_CMD, cmd);

            // map of prompt keys to values needed from the UI
            Map<String, String> additionalAuthParams = new LinkedHashMap<String, String>();
            for(AbstractParameterDTO param : params) {
                if(AuthenticationConstants.PRINCIPAL_ID.equals(param.getPromptKey())) {
                    // abort and return a failure response if rsa wants to prompt for a principal ID.
                    // the principal id was provided in the initial login, and is not allowed to be
                    // overridden.
                    response.setResult("ACCESS_DENIED");
                    return response;
                }

                additionalAuthParams.put(param.getPromptKey(), null);
            }

            if(!Util.isEmpty(params)) {
                wfc.setVariable(RSA_AUTH_PARAMS, additionalAuthParams);
            }
            response.setResult("ACCESS_IN_PROGRESS");
        }

        return response;
    }

    public void addFieldsToForm(WorkflowContext wfc) {
        Attributes<String,Object> args = wfc.getArguments();
        String stepName = args.getString("stepName");
        String sectionName = args.getString("sectionName");
        Form form = findForm(wfc, stepName);
        Form.Section section = (form == null) ? null : findSection(form, sectionName);
        if(section != null) {
            //clear all fields
            section.setItems(null);
            Map<String, String> additionalAuthParams = (Map)wfc.get(RSA_AUTH_PARAMS);
            for(String key : additionalAuthParams.keySet()) {
                Field field = new Field();
                field.setName(key);
                field.setRequired(true);
                section.add(field);
                switch(key) {
                    case SecurIDAuthenticationConstants.PromptKeys.SYSGEN_PIN:
                        field.setDisplayName("ui_mfa_rsa_form_sysgen_pin");
                        break;

                    case SecurIDAuthenticationConstants.PromptKeys.USER_NEW_PIN:
                        field.setDisplayName("ui_mfa_rsa_form_user_new_pin");
                        break;

                    case SecurIDAuthenticationConstants.PromptKeys.USER_NEW_PIN_CONFIRM:
                        field.setDisplayName("ui_mfa_rsa_form_user_new_pin_confirm");
                        break;

                    case SecurIDAuthenticationConstants.PromptKeys.NEXT_TOKENCODE:
                        field.setDisplayName("ui_mfa_rsa_form_next_tokencode");
                        break;
                        
                    case SecurIDAuthenticationConstants.PromptKeys.PASSCODE:
                        field.setDisplayName("ui_mfa_rsa_form_token");
                        break;

                    case USER_NEW_STATIC_PASSCODE:
                        field.setDisplayName("ui_mfa_rsa_form_new_static_passcode");
                        break;

                    case USER_NEW_STATIC_PASSCODE_CONFIRM:
                        field.setDisplayName("ui_mfa_rsa_form_new_static_passcode_confirm");
                        break;

                    default:
                        field.setDisplayName(key);
                        break;
                }
            }
        }
    }
    
    public void postProcessFormFields(WorkflowContext wfc) {
        Attributes<String,Object> args = wfc.getArguments();
        String stepName = args.getString("stepName");
        String sectionName = args.getString("sectionName");
        Form form = findForm(wfc, stepName);
        Form.Section section = (form == null) ? null : findSection(form, sectionName);
        if(section != null) {
            Map<String, String> additionalAuthParams = (Map)wfc.get(RSA_AUTH_PARAMS);
            for(String key : additionalAuthParams.keySet()) {
                Field field = section.getField(key);
                if(field != null) {
                    additionalAuthParams.put(key, (String)field.getValue());
                }
            }
        }
    }

    private Form findForm(WorkflowContext wfc, String stepName) {
        Workflow workflow = wfc.getWorkflow();
        List<Workflow.Step> steps = workflow.getSteps();
        for(Workflow.Step step : steps) {
            if(step.getName().equals(stepName)) {
                Workflow.Approval approval = step.getApproval();
                return (approval == null) ? null : approval.getForm();
            }
        }

        return null;
    }
    
    private Form.Section findSection(Form form, String sectionName) {
        for(Form.Section currentSection : form.getSections()) {
            if(sectionName.equals(currentSection.getName())) {
                return currentSection;
            }
    }
    
        return null;
    }
    
    /**
     * Will translate an RSA MessageDTO to a SailPoint Message object. The only thing this does now is render a specific Pin policy violation
     * message if the RSA key matches a specific string. Treating this message differently because the user has already entered a correct Pin
     * and Token. Anything else we will obscure the message and return the generic Multi-Factor Auth failed message.
     * @param dto RSA Message DTO object
     * @return SailPoint Message object
     */
    private Message toMessage(MessageDTO dto) {
        final String POLICY_PREFIX = "POLICY_VIOLATION_";
        String rsaKey = dto.getKey();
        
        String spKey = (rsaKey != null && rsaKey.startsWith(POLICY_PREFIX)) ? MessageKeys.UI_MFA_RSA_FORM_POLICY_VIOLATION : MessageKeys.UI_MFA_FAILED;
        return new Message(spKey);
    }

    private LoginCommand getLoginCommand(WorkflowContext wfc) throws GeneralException {
        LoginCommand cmd = (LoginCommand)wfc.getVariable(RSA_AUTH_CMD);
        if(cmd == null) {
            cmd = new LoginCommand();
            cmd.setAuthenticationMethodId(SecurIDAuthenticationConstants.METHOD_ID);
            String userName = wfc.getString("nativeUserId");

            // if we don't have a username, there is no need to continue.  In fact, RSA would
            // like to prompt for a username, so we shouldn't allow that to happen.  If we don't
            // know who you are, just fail.
            if(Util.isNullOrEmpty(userName)) {
                return null;
            }

            String token = wfc.getString("token");
            FieldParameterDTO usernameField = getFieldParam(AuthenticationConstants.PRINCIPAL_ID, userName);
            FieldParameterDTO passcodeField = getFieldParam(AuthenticationConstants.PASSCODE, token);
            cmd.setParameters(new AbstractParameterDTO[] {  usernameField, passcodeField});
        } else {
            Map<String, String> rsaAuthParams = (Map)wfc.getVariable(RSA_AUTH_PARAMS);
            for(AbstractParameterDTO param : cmd.getParameters()) {
                if(param instanceof FieldParameterDTO) {
                    ((FieldParameterDTO) param).setValue(rsaAuthParams.get(param.getPromptKey()));
                }
            }
        }

        return cmd;
    }

    private FieldParameterDTO getFieldParam(String promptKey, String value) {
        FieldParameterDTO fieldParameterDTO = new FieldParameterDTO();
        fieldParameterDTO.setPromptKey(promptKey);
        fieldParameterDTO.setValue(value);
        return fieldParameterDTO;
    }
    
    private CommandServerSoapBindingStub getBinding(WorkflowContext wfc) throws GeneralException, ServiceException {
        Attributes<String,Object> args = wfc.getArguments();
        String appName = Util.getString(args, VAR_APPLICATION_NAME);
        Application app = wfc.getSailPointContext().getObjectByName(Application.class, appName);

        String _host = (String) app.getAttributeValue("host");
        String _port = (String) app.getAttributeValue("port");

        CommandServerServiceLocator csl =  new CommandServerServiceLocator();
        String url = "https://" + _host + ":" +_port + "/ims-ws/services/CommandServer";
        csl.setCommandServerEndpointAddress(url);

        String _cmdClientUser = (String) app.getAttributeValue("cmdClientUser");
        String _cmdClientPassword = (String) app.getAttributeValue("cmdClientPassword");
        Integer bindingTimeOut = 60000;

        Object bindingTimeOutObject = app.getAttributeValue("bindingTimeOut");
        if (bindingTimeOutObject != null) {
            bindingTimeOut = Util.otoi(bindingTimeOutObject);
        }

        CommandServerSoapBindingStub binding = (CommandServerSoapBindingStub)csl.getCommandServer();

        binding.setUsername(_cmdClientUser);
        binding.setPassword(wfc.getSailPointContext().decrypt(_cmdClientPassword));
        binding.setMaintainSession(true);
        binding.setTimeout(bindingTimeOut);

        return binding;
    }
    
    public void rsaLogout(WorkflowContext wfc, String sessionId) throws ServiceException, GeneralException, RemoteException {
        CommandServerSoapBindingStub binding = getBinding(wfc);
        LogoutCommand cmd = new LogoutCommand();
        cmd.setSessionId(sessionId);
        try {
            binding.executeCommand(null, cmd);
        } catch (Exception e) {
            log.warn(e);
        }
    }
    
}
