/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Bag of static methods that can be called from Forms. These 
 * are things that are common across many forms to prevent
 * duplicate logic.
 * 
 * @author dan.smith
 * 
 */

package sailpoint.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import sailpoint.api.Notary;
import sailpoint.api.SailPointContext;
import sailpoint.object.ESignatureType;
import sailpoint.object.Field;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.workflow.ArgDTO;
import sailpoint.web.workflow.ScriptDTO;
import sailpoint.web.workflow.StepDTO;

public class FormUtil {

    /**
     * Get the displayableNames for the electronic signatures.
     * 
     * @param context
     * @return a List of displayNames
     * 
     * @throws GeneralException
     */    
    public static List<List<String>> getElectronicSignatureNames(SailPointContext context) 
        throws GeneralException {
        
        List<List<String>> typeNames = null;
        Notary notary = new Notary(context, null);
        List<ESignatureType> esigTypes = notary.getESignatureTypes();
        if ( esigTypes != null ) {
            for ( ESignatureType type : esigTypes ) {
                if ( type != null ) {
                    if ( typeNames == null ) 
                        typeNames = new ArrayList<List<String>>();
                    
                    List<String> val = new ArrayList<String>();
                    val.add(type.getName());   
                    val.add(type.getDisplayableName());
                    
                    typeNames.add(val);                    
                }
            }
        }
        return typeNames;
    }
    
    /**
     * Given a string representing the number of hours
     * divide it by 24 to get the number of days.
     */
    public static String convertHoursToDays(String value) {
        
        String strValue = null;
        Integer i = Util.otoi(value);
        if ( i > 0 ) {
            int divResult = i/24;
            if ( divResult > 0 ) {
                strValue = new Integer(divResult).toString(); 
            }
        } 
        return strValue;
    }
    
    /**
     * Given a string representing the number of days
     * multiply it by 24 to get the number of hours.
     */
    public static String convertDaysToHours(String value) {
        String strValue = null;
        Integer i = Util.otoi(value);
        if ( i > 0 ) {               
            strValue = new Integer(i * 24).toString();
        } 
        return strValue;
    }
        
    ///////////////////////////////////////////////////////////////////////////
    //
    // Workflow Step Library Form Helpers
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * Build an allowed value list for Electronic Signatures.
     * 
     * @param context
     * @param field
     * @param stepDTO
     * @param locale
     * 
     * @return List of List ( name, displayName pairs) of the esigs and options
     * 
     * @throws GeneralException
     */
    public static List<List<String>> getStepElectronicSignatureNames(SailPointContext context, Field field, StepDTO stepDTO, Locale locale) 
        throws GeneralException {
        
        List<List<String>> systemEsigs = getElectronicSignatureNames(context);
        if ( systemEsigs == null ) 
            systemEsigs = new ArrayList<List<String>>();
                
        List<String> extra =  buildExtraComboFields(field, stepDTO, locale);
        if ( extra != null ) 
            systemEsigs.add(extra);
        
        return systemEsigs;
    }
    
    /**
     * 
     * @param field
     * @param stepDTO
     * 
     * @return List of HashMaps for the extra fields
     */
    public static List<HashMap<String, String>> buildExtraFields(Field field, StepDTO stepDTO, Locale locale) {
        
        List<HashMap<String, String>> maps = new ArrayList<HashMap<String, String>>();
        if ( field == null ) {
            return null;
        }
        
        String fieldName = field.getName();
        if ( fieldName != null && !stepDTO.isLiteral(fieldName) ) {
            maps = new ArrayList<HashMap<String,String>>();
            HashMap<String, String> map = new HashMap<String,String>();
            
            String sourceString = stepDTO.getArgSource(fieldName);
            String script = stepDTO.getArgScript(fieldName);

            String localized  = getExtraValueMessage(stepDTO, fieldName, sourceString, locale);
            map.put("displayName", localized);
            map.put("displayableName", localized);
            map.put("name", script);
            map.put("id", script);            

            maps.add(map);
        }        
        return ( maps.size() > 0 ) ? maps : null;
    }

    /**
     * 
     * In the "edit" case for the Step forms, we need to display special values for references, script and
     * rules when included in a step argument.
     * 
     * @param field
     * @param stepDTO
     * 
     * @return List of HashMaps for the extra fields
     */
    public static List<String> buildExtraComboFields(Field field, StepDTO stepDTO, Locale locale) {

        List<String> stringList = new ArrayList<String>();
        if ( field == null ) {
            return null;
        }
        
        String fieldName = field.getName();
        if ( fieldName != null && !stepDTO.isLiteral(fieldName) ) {
            String sourceString = stepDTO.getArgSource(fieldName); 
            String localized  = getExtraValueMessage(stepDTO, fieldName, sourceString, locale);
            if ( localized != null ) {
                stringList.add(stepDTO.getArgScript(fieldName));
                stringList.add(localized);
            }
        }
        return stringList;
    }
    
    /**
     * Handle cases where the value is a reference, script and rule.
     * 
     * @param stepDTO
     * @param fieldName
     * @param sourceString
     * @param locale
     * 
     * @return localized message
     */
    private static String getExtraValueMessage(StepDTO stepDTO, String fieldName, String sourceString, Locale locale ) {
        if ( locale == null ) 
            locale = Locale.getDefault();
        
        Message message = null;
        if ( stepDTO.isArgReference(fieldName) )
            message = new Message(MessageKeys.WF_CONFIG_PROVISIONING_REFERENCE, sourceString);                
        if ( stepDTO.isRule(fieldName) ) {
            message = new Message(MessageKeys.WF_CONFIG_PROVISIONING_RULE, sourceString);   
        } else 
        if ( stepDTO.isScript(fieldName) ) {
            message = new Message(MessageKeys.WF_CONFIG_PROVISIONING_SCRIPT);
        }            
        String localized = "Unknown";
        if ( message != null ) {
            localized = message.getLocalizedMessage(locale, null);
        }
        return localized;
    }
    
    /**
     * Used to determine if a field is a reference.
     */
    public static String ATTR_REF_PREFIX = "ref:";
    public static boolean isRefValue(String fieldVal) {
        boolean argRef = false;
        
        if(fieldVal != null) {
            if(fieldVal.toLowerCase().startsWith(ATTR_REF_PREFIX))
                argRef = true;
        }
        return argRef;
    }
    
    /**
     * true if the field value is a script
     */
    public static String ATTR_SCRIPT_PREFIX = "script:";
    public static boolean isScriptValue(String fieldVal) {
        boolean argRef = false;
        
        if(fieldVal != null) {
            if(fieldVal.toLowerCase().startsWith(ATTR_SCRIPT_PREFIX))
                argRef = true;
        }
        return argRef;
    }
    
    /**
     * true if the field value is a rule
     */
    public static String ATTR_RULE_PREFIX = "rule:";
    public static boolean isRuleValue(String fieldVal) {
        boolean argRef = false;
        
        if(fieldVal != null) {
            if(fieldVal.toLowerCase().startsWith(ATTR_RULE_PREFIX))
                argRef = true;
        }
        return argRef;
    }
    
    /**
     * Test to see if the field value is a literal value
     * @param fieldVal
     * @return True if the value is not a script/rule/reference
     */
    public static boolean isLiteralValue(String fieldVal) {
        if(fieldVal != null) {
            if(isRefValue(fieldVal) || isScriptValue(fieldVal) || isRuleValue(fieldVal))
                return false;
        }
        return true;
    }

    /**
     * Tests to see if the form field value is equal to the original value stored on the StepDTO
     * @param formVal
     * @param fieldName
     * @param dto
     * @return
     */
    public static boolean isFieldEqual(String formVal, String fieldName, StepDTO dto) {
        
        if(dto != null) {
            ArgDTO arg = dto.getArgDTO(fieldName);
            if(arg != null) {
                ScriptDTO script = arg.getValue();
                if(script != null) {
                    if(isLiteralValue(formVal)) {
                        return script.getSource().equals(formVal);
                    } else {
                        return script.getScriptlet().equals(formVal);
                    }
                }
            }
        }
        
        return false;
    }
}
