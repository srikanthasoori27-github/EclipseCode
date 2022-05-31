package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityProxy;
import sailpoint.web.messages.MessageKeys;

/**
 * Service to return details given an identity.
 */
public class IdentityDetailsService {

    private static Log log = LogFactory.getLog(IdentityDetailsService.class);
    private Identity identity;
    
    /**
     * Constructor if using instance of this service
     * @param identity Identity for this service
     * @throws InvalidParameterException
     */
    public IdentityDetailsService(Identity identity) throws InvalidParameterException {
        if (identity == null) {
            throw new InvalidParameterException("identity");
        }
        this.identity = identity;
    }

    /**
     * Gets the details for this identity.
     * @param locale the locale to apply to any message keys
     * @param timeZone the timeZone to use on any messages
     * @return Map The details for this identity in a HashMap.
     * @throws GeneralException
     */
    public Map<String, Object> getIdentityDetails(Locale locale, TimeZone timeZone)
        throws GeneralException
    {
        Map<String, Object> result = new HashMap<String, Object>();
        if(identity.getAttributes() != null){
            for (String attrName : identity.getAttributes().keySet()) {
                // stuff the value in a msg object which handles localizing lists, dates, bools, etc.
                Message attrVal = new Message(MessageKeys.MSG_PLAIN_TEXT, identity.getAttributes().get(attrName));
                String displayName = getLocalizedAttribute(locale, attrName);
                result.put(displayName, attrVal.getLocalizedMessage(locale, timeZone));
            }
        }
        return result;
    }

    /**
     * Gets an IdentityAttributesDTO for this identity but only ones matching the keys passed in.  Returns in key order.
     * This is similar to getIdentityDetails(), but returns a DTO with more information.
     *
     * @param locale the locale to apply to any message keys
     * @param timeZone the timeZone to use on any messages
     * @param filterKeys the identity attribute keys to include and the order to include them in
     *
     * @return The IdentityAttributesDTO for this identity.
     */
    public IdentityAttributesDTO getIdentityAttributesDTO(Locale locale,
            TimeZone timeZone, List<String> filterKeys) throws GeneralException{
        IdentityAttributesDTO detailsDTO = new IdentityAttributesDTO();
        ObjectConfig objConfig = Identity.getObjectConfig();
        
        //filter viewable attributes through disallowed attributes list
        IdentityTypeDefinition type = identity.getIdentityTypeDefinition();
        if (type == null) {
            type = objConfig.getDefaultIdentityTypeDefinition();
        }

        List<String> disallowed = type == null ? null : type.getDisallowedAttributes();
        for (String attrName : filterKeys) {
            if (disallowed == null || !disallowed.contains(attrName)) {
                String label = IdentityDetailsService.getLocalizedAttribute(locale, attrName);
                Object value = IdentityProxy.get(identity, attrName, false);
    
                ObjectAttribute attr = objConfig.getObjectAttribute(attrName);
                if ((null != attr) && attr.isIdentity()) {
                    // IdentityProxy just returns a displayName for manager, so we need to handle this specially.
                    if (Identity.ATT_MANAGER.equals(attrName)) {
                        value = identity.getManager();
                    } else if (Identity.ATT_ADMINISTRATOR.equals(attrName)) {
                        value = identity.getAdministrator();
                    }
                    else {
                        // Otherwise, the IdentityProxy gives us a name.  Rather than do a search by name, we'll avoid
                        // the DB hit and get the identity extended attribute from the identity.
                        Object extendedVal = identity.getAttribute(attrName);
                        if (null != extendedVal) {
                            if (extendedVal instanceof Identity) {
                                value = (Identity) extendedVal;
                            }
                            else {
                                log.warn("Expected an extended identity for '" + attrName + "' on " + identity.getName());
                            }
                        }
                    }
                }
    
                detailsDTO.add(attrName, label, value);
            }
        }
        return detailsDTO;
    }

    /**
     * Gets the details for this identity but only ones matching the keys passed in.  Returns in key order.
     * @param locale the locale to apply to any message keys
     * @param timeZone the timeZone to use on any messages
     * @param filterKeys the identity attribute keys to include and the order to include them in
     * @return List The details for this identity in a list of key:value pairs.
     */
    public List<Map<String,String>> getIdentityDetails(Locale locale, TimeZone timeZone, List<String> filterKeys){
        List<Map<String,String>> result = new ArrayList<Map<String,String>>();
        if(filterKeys != null){
            //filter viewable attributes through disallowed attributes list
            IdentityTypeDefinition type = identity.getIdentityTypeDefinition();
            if (type == null) {
                type = Identity.getObjectConfig().getDefaultIdentityTypeDefinition();
            }
            List<String> disallowed = type == null ? null : type.getDisallowedAttributes();
            for (String attrName : filterKeys) {
                if (disallowed == null || !disallowed.contains(attrName)) {
                    // stuff the value in a msg object which handles localizing lists, dates, bools, etc.
                    Message attrVal = new Message(MessageKeys.MSG_PLAIN_TEXT, IdentityProxy.get(identity, attrName, true));
                    if(!Util.isNullOrEmpty(attrVal.getLocalizedMessage(locale, timeZone))){
                        Map<String, String> entry = new HashMap<String,String>();
                        entry.put(attrName, attrVal.getLocalizedMessage(locale, timeZone));
                        result.add(entry);
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * @param locale locale to apply for message keys
     * @param attrName name of attribute to localize
     * @return localized version of attribute if available otherwise the attribute is returned
     */
    public static String getLocalizedAttribute(Locale locale, String attrName){
        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        String displayName = attrName;
        if (identityConfig != null) {
            displayName = identityConfig.getDisplayName(attrName, locale);
        }
        
        String localizedName = Internationalizer.getMessage(displayName, locale);
        return localizedName != null ? localizedName : displayName;
    }

    /**
     *
     * @param identityIds list of identity ids to fetch emails for
     * @param context SailPointContext object
     * @return Map of identityId, email for the given identityIds.
     * @throws GeneralException
     */
    public static Map<String,String> getIdentityEmails(List<String> identityIds, SailPointContext context)
        throws GeneralException
    {
        Map<String,String> emails = new HashMap<String,String>();
        if (Util.isEmpty(identityIds)) {
            return emails;
        }
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.in("id", identityIds));
        String props = "id, email";
        Iterator<Object[]> iterator = context.search(Identity.class, qo, props);
        while(iterator.hasNext()) {
            Object[] row = iterator.next();
            emails.put((String) row[0], (String) row[1]);
        }
        return emails;
    }
}