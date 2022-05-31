package sailpoint.api;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;


/**
 * Utility class for hash or encrypt Identity secret, based on
 * setting in system configuration.
 * 
 * It should be only applied to IdentityIQ Identity password, 
 * password history, Link password history, and authentication 
 * question answers.
 * 
 * @author danny.feng
 *
 */
public class EncodingUtil {

    private static final Log LOG = LogFactory.getLog(EncodingUtil.class);

    /** Default hashing algorithm **/
    private static String DEFALT_ALGORITHM = "SHA-256";

    /** Default number of iterations **/
    private static int DEFAULT_ITERATIONS = 1000;
    
    /**
     * Checks whether hashing is enabled in System Configuration.
     * 
     * @param context SailPointContext
     * @return true if hashing is enabled in System Configuration, false if not
     */
    public static boolean isHashingEnabled(SailPointContext context) {
        try {
            Configuration sysConfig = context.getConfiguration();
            return sysConfig.getBoolean(Configuration.HASH_IDENTITY_SECRETS, false);            
        } catch (GeneralException e) {
            LOG.error("Error checking isHashEnabled:", e);
        }
        return false;            
    }

    /**
     * Encodes the string based on the configuration, either hashed or encrypted.
     * This should be only invoked for SailPoint Identity password, password history 
     * and authentication question answers. 
     * 
     * If the passed secret is already hashed, then the hashed value will be returned.
     * 
     * If the passed secret is encrypted and hashing is enabled, 
     * hashed value will be returned.
     * If the passed secret is encrypted and hashing is not enabled, 
     * original encrypted value will be returned.
     * 
     * If the passed secret is null, then null will be returned.
     * 
     * @param secret  string to be encoded
     * @param context SailPointContext
     * @return hashed or encrypted value
     * @throws GeneralException
     */
    @Untraced
    public static String encode(String secret, SailPointContext context) throws GeneralException {
        boolean doHashing = isHashingEnabled(context);
        if (doHashing) {
            if (isEncrypted(secret)) {
                //convert encrypted secret to hashed value if hashing is enabled
                return generateHash(context.decrypt(secret), context);
            } else {
                return generateHash(secret, context);
            }
        } else {
            if (isHashed(secret)) {
                return secret;
            } else {
                return context.encrypt(secret);
            }
        }
    }
        
    /**
     * Generates the hash in the format of: "HASH:<Hash>:<Salt>:<number of iterations>",
     * using a random 64-bit salt.
     * 
     * @param secret the string to be hashed
     * @return the hashed value
     */
    private static String generateHash(String secret, SailPointContext context) {
        if (secret == null || isHashed(secret)) {
            return secret;
        }
        
        // Salt generation 64 bits long
        byte[] salt = generateSalt();
        int iterations = getHashingIterationsFromConfiguration(context);
        String hash = generateHash(secret, salt, iterations);
        return "HASH:" + hash + ":" + Hex.encodeHexString(salt) + ":" + iterations; 
    }

    /**
     * Returns the number of hashing iterations from system configuration.
     * @param context
     * @return the number of hashing iterations from system configuration
     */
    private static int getHashingIterationsFromConfiguration(SailPointContext context) {
        try {
            Configuration sysConfig = context.getConfiguration();
            return sysConfig.getInt(Configuration.HASHING_ITERATIONS);            
        } catch (GeneralException e) {
            LOG.error("Error checking isHashEnabled:", e);
        }
        return DEFAULT_ITERATIONS;            
    }
    private static String generateHash(String secret, byte[] salt, int iterations) {
        if (salt == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(DEFALT_ALGORITHM);
            digest.reset();
            digest.update(salt);
            
            byte[] input = digest.digest(secret.getBytes("UTF-8"));
            
            for (int i = 0; i < iterations; i++) {
               digest.reset();
               input = digest.digest(input);
            }

            return Hex.encodeHexString(input);
        } catch (NoSuchAlgorithmException nsae) {
            LOG.error("Error when generating hash:", nsae);
        } catch (UnsupportedEncodingException uee) {
            LOG.error("Error when generating hash:", uee);
        }
        return null;
    }

    /**
     * Returns whether the passed in secret matches the hashed or encrypted value 
     * based on the encryption or hashing algorithm.
     * 
     * @param secret the raw value
     * @param encryptedOrHashedValue  hashed or encrypted value
     * @param context SailPoint context
     * @return true if it matches
     */
    @Untraced
    public static boolean isMatch(String secret, String encryptedOrHashedValue, SailPointContext context) {
        try {
            if (isHashed(encryptedOrHashedValue)) {
            
                String[] splitted = encryptedOrHashedValue.split(":");
                if (splitted == null || splitted.length != 4) {
                    LOG.error("Invalid format of hashed value:" + encryptedOrHashedValue);
                    return false;
                }
                String hashed = splitted[1];
                String salt = splitted[2];
                int iterations = Integer.parseInt(splitted[3]);
                char[] saltChars = new char[salt.length()];
                salt.getChars(0, salt.length(), saltChars, 0);
                String hashedNew = generateHash(secret, Hex.decodeHex(saltChars), iterations);

                return Util.nullSafeEq(hashed, hashedNew);
            } else if (isEncrypted(encryptedOrHashedValue)) {
                return Util.nullSafeEq(secret, context.decrypt(encryptedOrHashedValue));
            } else {
                return Util.nullSafeEq(secret, encryptedOrHashedValue);
            }
        } catch (GeneralException ge) {
            LOG.error("Error in isMatch:", ge);
        } catch (DecoderException e) {
            LOG.error("Error in isMatch:", e);
        }
        return false;
    }

    /**
     * Checks if the passed in string value is encrypted.
     * 
     * @param secret the string value
     * @return true if it is encrypted
     */
    @Untraced
    public static boolean isEncrypted(String secret) {
        return ObjectUtil.isEncoded(secret);
    }

    /**
     * Checks if the passed string value is hashed.
     * 
     * @param secret the string to be checked
     * @return true if it is hashed.
     */
    public static boolean isHashed(String secret) {
        if (secret != null && secret.startsWith("HASH:")) {
            String[] splitted = secret.split(":");
            if (splitted != null && splitted.length == 4) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns a random 64-bit salt in the byte array.
     * 
     * @return byte array holds the 64-bit salt
     */
    private static byte[] generateSalt() {
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
            // Salt generation 64 bits long
            byte[] salt = new byte[8];
            random.nextBytes(salt);
            return salt;
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Error generating random salt:", e);
        }
        return null;
    }
}
