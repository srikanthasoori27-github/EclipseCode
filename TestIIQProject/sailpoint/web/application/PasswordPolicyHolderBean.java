/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.passwordConstraints.PasswordConstraintAttributeAccount;
import sailpoint.api.passwordConstraints.PasswordConstraintBasic;
import sailpoint.api.passwordConstraints.PasswordConstraintHistory;
import sailpoint.api.passwordConstraints.PasswordConstraintMulti;
import sailpoint.api.passwordConstraints.PasswordConstraintRepeatedCharacters;
import sailpoint.object.Configuration;
import sailpoint.object.PasswordPolicyHolder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.ApplicationObjectBean;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class PasswordPolicyHolderBean extends BaseBean {

    private static final Log log = LogFactory.getLog(PasswordPolicyHolderBean.class);
    
    private PasswordPolicyHolderDTO dtoObject;

    private String policyName;

    private String policyDescription;

    private boolean selected;
    
    // For the select case
    private String selectedPolicy;

    private boolean shared = false;

    private static String _numericFields[] =  { PasswordConstraintBasic.MIN_LENGTH,
    	PasswordConstraintBasic.MAX_LENGTH,
    	PasswordConstraintBasic.MIN_ALPHA,
    	PasswordConstraintBasic.MIN_NUMERIC,
    	PasswordConstraintBasic.MIN_UPPER,
    	PasswordConstraintBasic.MIN_LOWER,
    	PasswordConstraintBasic.MIN_SPECIAL,
    	PasswordConstraintBasic.MIN_CHARTYPE,
    	PasswordConstraintAttributeAccount.MIN_DISPLAY_NAME_UNIQUECHARS,
    	PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS,
    	PasswordConstraintAttributeAccount.MIN_ACCOUNT_ID_UNIQUECHARS,
    	PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS,
    	PasswordConstraintHistory.HISTORY };
    
    private Map<String, String> multiConstraintList = new LinkedHashMap<String, String>();
    
    private void setupMultiList() {
        multiConstraintList.put(getMessage(MessageKeys.PSWD_UPPER_CASE), PasswordConstraintMulti.MULTI_UPPER);
        multiConstraintList.put(getMessage(MessageKeys.PSWD_LOWER_CASE), PasswordConstraintMulti.MULTI_LOWER);
        multiConstraintList.put(getMessage(MessageKeys.PSWD_NUMERIC), PasswordConstraintMulti.MULTI_BASE10);
        multiConstraintList.put(getMessage(MessageKeys.PSWD_SPECIAL_CHARS), PasswordConstraintMulti.MULTI_NONALPHA);
//        multiConstraintList.put(getMessage(MessageKeys.PSWD_UNICODE), PasswordPolice.MULTI_UNICODE);
    }
    
    private String[] multiConstraint = null;
    private boolean readonly;

    /**
     * 
     */
    public PasswordPolicyHolderBean(String appName) {
        super();
        this.dtoObject = new PasswordPolicyHolderDTO(appName);
        this.selected = false;
        this.policyDescription = "";
        this.policyName = "";
        setupMultiList();
    }
    
    public PasswordPolicyHolderBean(PasswordPolicyHolder pp, String appName, boolean shared) {
        super();
        this.dtoObject = new PasswordPolicyHolderDTO(pp, appName);
        this.selected = false;
        this.policyName = pp.getPolicyName();
        this.policyDescription = pp.getPolicyDescription();
        String mc = (String) pp.getPasswordConstraints().get("multiConstraint");
        if (mc != null && mc.length() != 0) {
            multiConstraint = (String[]) mc.split(",");
        }
        setupMultiList();
        this.shared = shared;
    }
    
    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }
    
    public String getSelectedPolicy() {
        return this.selectedPolicy;
    }

    public void setSelectedPolicy(String selectedPolicy) {
        this.selectedPolicy = selectedPolicy;
    }
    
    public String[] getMultiConstraint() {
        return multiConstraint;
    }

    public void setMultiConstraint(String[] multiConstraint) {
        this.multiConstraint = multiConstraint;
    }
    
    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getPolicyDescription() {
        return policyDescription;
    }

    public void setPolicyDescription(String policyDescription) {
        this.policyDescription = policyDescription;
    }
    
    public PasswordPolicyHolderDTO getDtoObject() {
        return dtoObject;
    }

    public void setDtoObject(PasswordPolicyHolderDTO dtoObject) {
        this.dtoObject = dtoObject;
    }
    
//    @SuppressWarnings("unchecked")
//    public PasswordPolicyHolderDTO getObject() {
//        return object;
//    }
    
    public Map<String, String> getMultiConstraintList() {
        return multiConstraintList;
    }
    
    private boolean validateNumericFields() {
        Map<String, Object> constraints = dtoObject.getConstraints();
        String fieldVal;
        // first make sure all numeric fields are numbers
        for (String field : _numericFields) {
            try {
                fieldVal = (String)constraints.get(field);
                if (fieldVal == null || fieldVal.trim().length() == 0)
                    continue;
                Integer.parseInt(fieldVal);
            } catch (NumberFormatException e) {
                // if any fields are NaN, no further validation is possible
                addMessage(new Message(Message.Type.Error,  MessageKeys.PASSWD_POLICY_NOT_A_NUMBER));
                return true;           
            }
        }
        
        return false;
    }
    
    private int getConstraintIntValue(Map<String, Object> constraints, String key) {
        int value = 0;
        if (constraints.containsKey(key) && ((String)constraints.get(key)).length() != 0) {
            try {
                value = Integer.parseInt((String)constraints.get(key));
            }
            catch(NumberFormatException nfe) {
                value = 0;
            }
        }
        return value;
    }
    
    /**
     * Make sure constraints dont conflict with each other
     * @return
     */
    private boolean validateConstraints() {
        boolean error = false;

        Map<String, Object> constraints = dtoObject.getConstraints();
        
        int minLength = getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_LENGTH);
        int maxLength =  getConstraintIntValue(constraints, PasswordConstraintBasic.MAX_LENGTH);
        int minAlpha = getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_ALPHA);
        int minNumeric =  getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_NUMERIC);
        int minUpper =  getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_UPPER);
        int minLower =  getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_LOWER);
        int minSpecial =  getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_SPECIAL);
        int minCharType = getConstraintIntValue(constraints, PasswordConstraintBasic.MIN_CHARTYPE);
        
        if ((maxLength > 0) && (minLength > maxLength)) {
            addMessage(new Message(Message.Type.Error, 
                MessageKeys.PASSWD_MIN_MAX_CONFLICT));
            
            error = true;
        }
        
        if (minCharType > 0) {
            if (minCharType > PasswordConstraintBasic.MAXIMUM_CHARACTER_TYPES) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.PASSWD_CHAR_TYPE_OVERFLOW, 
                        PasswordConstraintBasic.MAXIMUM_CHARACTER_TYPES));
                error = true;
            } else {
                int sum = 0;
                
                if (minUpper > 0) {
                    sum++;
                }
                if (minLower > 0) {
                    sum++;
                }
                if (minSpecial > 0) {
                    sum++;
                }
                if (minNumeric > 0) {
                    sum++;
                }
                
                if (sum < minCharType) {
                    addMessage(new Message(Message.Type.Error,
                            MessageKeys.PASSWD_CHAR_TYPE_CONFLICT, minCharType));
                    error = true;
                }
            }
        }
        
        int minUnicode = 0;
        
        if (multiConstraint != null) {
            for (String req : multiConstraint) {
                if (req.equals(PasswordConstraintMulti.MULTI_UPPER) && (minUpper == 0)) {
                    minUpper = 1;
                }
                else if (req.equals(PasswordConstraintMulti.MULTI_LOWER) && (minLower == 0)) {
                    minLower = 1;
                }
                else if (req.equals(PasswordConstraintMulti.MULTI_BASE10) && (minNumeric == 0)) {
                    minNumeric = 1;
                }
                else if (req.equals(PasswordConstraintMulti.MULTI_NONALPHA) && (minSpecial == 0)) {
                    minSpecial = 1;
                }
                else if (req.equals(PasswordConstraintMulti.MULTI_UNICODE)) {
                    minUnicode = 1;
                }
            }
        }

        // upper and lower minimums overlap the alpha minimum so
        // that can reduce to zero
        minAlpha -= minUpper;
        minAlpha -= minLower;
        if (minAlpha < 0) minAlpha = 0;

        int minTotal = minAlpha + minNumeric + minUpper + minLower + minSpecial + minUnicode;
        // Make sure maxLength is max enough
        if ((maxLength > 0) && (minTotal > maxLength)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.PASSWD_CHARS_MIN_GT_MAX, Util.itoa(minTotal), Util.itoa(maxLength)));
            error = true;
        }
        // Make sure minLength matches other min requirements
        if ((minLength > 0) && (minLength < minTotal)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.PASSWD_MIN_REQUIRED, minTotal));
            error = true;
        }

        return error;
    }
    
    private boolean validate() {
        boolean error = false;
        if (policyName == null || policyName.trim().length() == 0) {
            addMessage(new Message(Message.Type.Error, MessageKeys.NAME_REQUIRED), null);
            error = true;
        }

        if (policyName.indexOf(":") > 0) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_INVALID_PASSWORD_POLICY_NAME), null);
            error = true;
        }
        
        // Validate fields
        if (!error) {
            error = validateNumericFields();
        }
        
        if (!error) {
            // Make sure constraints don't conflict with each other
            error = validateConstraints();
        }
        
        // Validate the selector
        if (dtoObject.getSelector() != null) {
            try {
                dtoObject.getSelector().validate();
            }
            catch (GeneralException e) {
                addMessage(e);
                error = true;
            }
        }
        return error;
    }
    
    /**
     * 
     * @return
     */
    public String saveAction() {
        boolean error = false;

        if (dtoObject == null) {
            return "";
        }
        
        // Check if we selected an existing policy or created a new one
        if (dtoObject.getSelectedPolicy() == null) {
            // Do some field validation
            error = validate();
            
            if (!error) {
                dtoObject.setPolicyName(policyName);
                dtoObject.setPolicyDescription(policyDescription);
                dtoObject.setMultiConstraint(multiConstraint);
            }
        }
        else if (dtoObject.getSelectedPolicy().trim().length() == 0) {
            error = true;
            addMessage(new Message(Message.Type.Error, "Select a valid policy"), null);
        }
        
        ValueBinding vb = getFacesContext().getApplication().createValueBinding("#{applicationObject}");
        
        ApplicationObjectBean appBean = (ApplicationObjectBean) vb.getValue(getFacesContext());

        // Check if the policy name is being used already
        try {
            // selected means this is an edit
            // If getSelectedPolicy return something that means this a using an existing policy
            if (!selected && (dtoObject.getSelectedPolicy() == null) && appBean.isPolicyNameUsed(policyName)) {
                error = true;
                addMessage(new Message(Message.Type.Error, "Policy name is already used"), null);
            }
        }
        catch (GeneralException e1) {
            error = true;
            addMessage(new Message(Message.Type.Error, "Error determining whether policy name is already used."), null);
        }
        
        // Update the edit info on the app bean
        if (!error) {
            try {
                appBean.savePasswordPolicies(dtoObject);
            }
            catch (GeneralException e) {
                addMessage(new Message(Message.Type.Error, "Error saving password policy"), null);
                error = true;
                
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
            }
            
            // Forward on the request params in the session so that we don't
            // lose our state
            String id = (String) getRequestParam().get("editForm:id");
            getSessionScope().put("editForm:id", id);
            
            // Remove ourselves from the session because our job is done for the
            // time being
            appBean.removeEditState(ApplicationObjectBean.EDITED_PASSWORD_POLICY);
            return "savePasswordPolicy";
        }

        return "";
    }

    public String cancelAction() {
        String id = (String) getRequestParam().get("editForm:id");
        getSessionScope().put("editForm:id", id);
        
        ValueBinding vb =getFacesContext().getApplication().createValueBinding("#{applicationObject}");
        
        ApplicationObjectBean appBean = (ApplicationObjectBean) vb.getValue(getFacesContext());

        appBean.removeEditState(ApplicationObjectBean.EDITED_PASSWORD_POLICY);
        
        return "cancelPasswordPolicy";
    }

    /**
     * Determines if hashing of secrets is enabled.
     * @return True if hashing is enabled, false otherwise.
     */
    public boolean isHashingEnabled() {
        Configuration systemConfig = Configuration.getSystemConfig();

        return systemConfig.getBoolean(Configuration.HASH_IDENTITY_SECRETS);
    }

    /**
     * Overriding this for the same reason that we overrode getContext()
     */
    @Override
    public FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }
    
    /**
     * Overriding this for the same reason that we overrode getContext()
     */
    @Override
    public Map getRequestParam() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
    }
    
    /**
     * Overriding this for the same reason that we overrode getContext()
     */
    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
    }

    // For viewing only
    public boolean isReadOnly() {
        return readonly;
    }
    
    public void setReadOnly(boolean ro) {
        this.readonly = ro;
    }
   
}
