/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Contains Utility functions for masking Application attribute values
 * 
 * @author neeraj.dorle
 *
 */
public class MaskUtil {

    public static final String MASKED_STRING = "**********";
    public static final String ENCRYPTED_APPLICATION_ATTRIBUTES = "encryptedApplicationAttributes";
    public static final String SECRET_APPLICATION_ATTRIBUTES = "secretApplicationAttributes";
    private static Log log = LogFactory.getLog(MaskUtil.class);

    /**
     * Masks the key-value pair
     * <p>
     * Expects ENCRYPTED_APPLICATION_ATTRIBUTES as a comma-separated strings and
     * SECRET_APPLICATION_ATTRIBUTES as a <code>List</code> of strings as input
     * to be provided using the options map for internal processing.
     * 
     * </p>
     * 
     * @param key
     *            Key of the Application attribute that needs to be
     *            verified
     * @param secret
     *            Attribute value
     * @param options
     *            Extra options that needs to be passed for further processing
     * @return {@link Object} with the operation performed
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public static Object mask(String key, Object secret,
                                      Map<Object,Object> options)
        throws GeneralException {

        if (null == secret) {
            return null;
        }

        if (secret instanceof String) {
            return processString(key, (String) secret, options);
        } else if (secret instanceof Map) {
            return processMap((Map<String,Object>) secret, options);
        } else if (secret instanceof List) {
            return processList((List<Object>) secret, options);
        }
        return secret;
    }

    /**
     * <p>
     * Iterates over the list and recursively calls {@link #mask(String, Object, Map)
     * to process individual entries.
     * </p>
     * 
     * @param secret
     *            Key of the {@link Application} attribute that needs to be
     *            verified
     * @param options
     *            Extra options that needs to be passed for further processing
     * @return List with mask performed on each element.
     * @throws GeneralException
     */
    @SensitiveTraceReturn
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List processList(List<Object> secret,
                                    Map<Object,Object> options)
        throws GeneralException {
        List returnList = null;
        if (null != secret && !secret.isEmpty()) {
            returnList = new ArrayList<>();
            for (Object o : secret) {
                returnList.add(mask(null, o, options));
            }
        }
        return returnList;
    }

    /*
     * This method is kind of a decision making method.
     */

    /**
     * <p>
     * Performs the operation on the secret String.
     * </p>
     * 
     * @param key
     *            Key of the Application attribute that needs to be verified for
     *            decrypting
     * @param secret
     *            Value that is to be decrypted
     * @param options
     *            Extra options that needs to be passed for further processing
     * @return String value with the mask performed
     * @throws GeneralException
     */
    @SuppressWarnings("rawtypes")
    @SensitiveTraceReturn
    private static String processString(String key, String secret,
                                        Map<Object,Object> options)
        throws GeneralException {
        String encryptedAttrValue = "";
        List secrets = Collections.emptyList();
        if (null != options && !options.isEmpty()) {
            if (options.containsKey(
                    MaskUtil.ENCRYPTED_APPLICATION_ATTRIBUTES)) {
                encryptedAttrValue = (String) options.get(
                        MaskUtil.ENCRYPTED_APPLICATION_ATTRIBUTES);
            }
            if (options.containsKey(
                    MaskUtil.SECRET_APPLICATION_ATTRIBUTES)) {
                secrets = (List) options.get(
                        MaskUtil.SECRET_APPLICATION_ATTRIBUTES);
            }
        }
        return maskString(key, secret, encryptedAttrValue, secrets);
    }

    /**
     * <p>
     * Iterates over the Map and recursively calls
     * {@link #mask(String, Object, Map)}
     * to process individual entries
     * </p>
     * 
     * @param secret
     *            Map value of the Application attribute
     * @param options
     *            Extra options that needs to be passed for further processing
     * @return Map with masked entries as needed.
     * @throws GeneralException
     */
    @SensitiveTraceReturn
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Map processMap(Map<String,Object> secret,
                                  Map<Object,Object> options)
        throws GeneralException {
        Map returnMap = null;
        if (null != secret && !secret.isEmpty()) {
            returnMap = new HashMap<>();
            for (Entry<String,Object> entry : secret.entrySet()) {
                if (null != entry) {
                    returnMap.put(entry.getKey(), mask(entry.getKey(),
                            entry.getValue(), options));
                }
            }
        }
        return returnMap;
    }

    /**
     * <p>
     * Masks the value of secret with
     * {@link MaskUtil#MASKED_STRING} if necessary.
     * </p>
     * 
     * @param key
     *            Key of the Application attribute that needs to be verified for
     *            masking
     * @param secret
     *            {@link String} value that is to be masked
     * @param encryptedAttrValue
     *            Comma-separated list of attributes specified in application
     *            definition for encryption
     * @return masked value with {@link MaskUtil#MASKED_STRING}
     */
    @SuppressWarnings("rawtypes")
    private static String maskString(String key, String secret,
                                     String encryptedAttrValue, List secrets) {
        if ((null != key) && (isSecret(key, secrets) ||
                isEncrypted(key, encryptedAttrValue))) {
            return MASKED_STRING;
        }
        
        return secret;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean isSecret(String name, List secrets) {
        boolean isSecretKey = false;
        if (!Util.isEmpty(secrets) && Util.isNotNullOrEmpty(name)) {
            List<String> list = secrets;
            for (String s : list) {
                if (name.equalsIgnoreCase(s)) {
                    isSecretKey = true;
                    break;
                }
            }
        }
        return isSecretKey;
    }

    private static boolean isEncrypted(String key, String encryptedAttrValue) {
        if (Util.isNotNullOrEmpty(encryptedAttrValue) &&
                Util.isNotNullOrEmpty(key)) {
            List<String> encryptedList = Util.csvToList(encryptedAttrValue);
            if (null != encryptedList && !encryptedList.isEmpty()) {
                return encryptedList.contains(key);
            }
        }
        return false;
    }
}
