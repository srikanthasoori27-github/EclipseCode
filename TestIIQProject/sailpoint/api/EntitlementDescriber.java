/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.object.Application;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.score.EntitlementScoreConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class EntitlementDescriber {

    private static final Log log = LogFactory.getLog(EntitlementDescriber.class);
    private EntitlementScoreConfig entitlementScoreConfig;
    
    /**
     * Create a Message summary of these entitlements.  This does not
     * include the application name or the native identity.
     *
     * @return A Message summary of these entitlements.
     */
    public static Message summarize(EntitlementSnapshot snapshot){
        try {
            return summarize(snapshot, null);
        } catch (GeneralException e) {
                log.error(e.getMessage(), e);
        }
        return null;
    }

    public static Message summarizeWithDescriptions(EntitlementSnapshot snapshot, Locale locale)
            throws GeneralException{
        return summarize(snapshot, locale);
    }

    /**
     * Create a Message summary of the entitlements on this snapshot.
     * Attempts replace attribute values and permissions targets with
     * entitlement descriptions
     * @param locale
     * @return
     * @throws GeneralException
     */
    private static Message summarize(EntitlementSnapshot snapshot, Locale locale) throws GeneralException{

        if (snapshot.isAccountOnly()){
            return  new Message(MessageKeys.ENT_SNAP_ACCOUNT_SUMMARY, snapshot.getDisplayableName(),
                    snapshot.getApplicationName());
        }


        List<Message> attrMessages = new ArrayList<Message>();
        if ((null != snapshot.getAttributes()) && (snapshot.getAttributes().size() > 0)) {
            int cnt = 0;
            for (Map.Entry<String,Object> entry : snapshot.getAttributes().entrySet()) {
                List<String> values = new ArrayList<String>();
                if (entry.getValue() != null) {
                    for(Object val :  Util.asList(entry.getValue())){
                        String realValue = getEntitlementDescription(locale, snapshot, false, entry.getKey(), val);
                        if (realValue.equals(val)) {
                            // Lets try and find the displayableValue
                        	String displayValue = Explanator.getDisplayValue(snapshot.getApplication(),entry.getKey(), (String)val);
                            if (null != displayValue){
                            	values.add(displayValue);
                            } else {
                            	values.add(realValue);
                            }
                        } else {
                            values.add(realValue);
                        }
                    }
                }

                String key = values == null || values.size() < 2 ?  MessageKeys.ENT_SNAP_ATTR_VAL_SUMMARY :
                        MessageKeys.ENT_SNAP_ATTR_VALS_SUMMARY;
                String delimiter = cnt > 0 ? MessageKeys.ENT_SNAP_VAL_DELIMITER : "";
                attrMessages.add(new Message(key, delimiter, values, entry.getKey()));
                cnt++;
            }
        }

        List<Message> permMessages = new ArrayList<Message>();
        if ((null != snapshot.getPermissions()) && (snapshot.getPermissions().size() > 0)) {
            int cnt = 0;
            for (Permission p : snapshot.getPermissions()) {
                List<String> rights = p.getRightsList();
                String key = rights == null || rights.size() < 2 ? MessageKeys.ENT_SNAP_PERM_VAL_SUMMARY :
                        MessageKeys.ENT_SNAP_PERM_VALS_SUMMARY;
                String delimiter = cnt > 0 ? MessageKeys.ENT_SNAP_VAL_DELIMITER : "";
                String desc = getEntitlementDescription(locale, snapshot, true, p.getTarget(), null);
                permMessages.add(new Message(key, delimiter, rights, desc));
                cnt++;
            }
        }

        // construct complete message, only including the lists which are not empty
        // delimiter is added if attrs and perms are not empty
        Object[] params = {null, null, null};
        if (!attrMessages.isEmpty())
            params[0] = attrMessages;
        if (!attrMessages.isEmpty() && !permMessages.isEmpty())
            params[1] = MessageKeys.ENT_SNAP_SUMMARY_DELIMITER;
        if (!permMessages.isEmpty())
            params[2] = permMessages;

        return new Message(MessageKeys.ENT_SNAP_SUMMARY, params) ;
    }

    private static String getEntitlementDescription(Locale locale, EntitlementSnapshot snapshot, 
                                                    boolean permission, String entitlement, Object value)
        throws GeneralException {

        String desc = null;

        if (locale != null) {
            // !! try to arrange to have a context passed in
            SailPointContext con = SailPointFactory.getCurrentContext();
            Application app = con.getObjectByName(Application.class, snapshot.getApplication());

            if (permission) {
                desc = Explanator.getPermissionDescription(app, entitlement, locale);
                if (desc == null)
                    desc = entitlement;
            }
            else {
                String svalue = (value != null) ? value.toString() : null;
                desc = Explanator.getDescription(app, entitlement, svalue, locale);
                if (desc == null)
                    desc = svalue;
            }
        }   
        else {
            // no lang, just use the value
            // ?? why not get the server lang, it's better than nothing?
            if (permission)
                desc = entitlement;
            else if (value != null)
                desc = value.toString();
        }

        // shouldn't happen but caller expects non-null
        if (desc == null) desc = "";

        return desc;
    }
    
    private EntitlementScoreConfig getEntitlementScoreConfig(SailPointContext context)
        throws GeneralException {
        if (null == this.entitlementScoreConfig) {
            this.entitlementScoreConfig = new EntitlementScoreConfig(context);
        }
        return this.entitlementScoreConfig;
    }
    
    public int getEntitlementScore(SailPointContext context, String appName, String attr, String value, String type)
            throws GeneralException {
            
            int score = 0;
            
            EntitlementScoreConfig config = this.getEntitlementScoreConfig(context);
            if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(type)) {
                score = config.getRightWeight(appName, attr, value);
            }
            else {
                //Assume every type other than Permission is an entitlement. We can now have
                //Account Group types as the MA type, but they are still considered Entitlements -rap
                score = config.getValueWeight(appName, attr, value);
            }
            
            return score;
        }

}
