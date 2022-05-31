/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.oauth;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sailpoint.api.SailPointContext;
import sailpoint.server.SPKeyStore;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;

/**
 * Similar to {@link sailpoint.server.Transformer} this class is responsible for
 * generating a java.sercuity.Key and performing cryptography operations for OAuth clients.
 * Each OAuth client will be assigned its own key.
 */
public class OAuthTransformer {

    public static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    private SailPointContext context;
    
    public OAuthTransformer(SailPointContext context) {
        this.context = context;
    }
    
    /**
     * Generates a key by:
     * <ul>
     * <li>Obtaining an instance of a KeyGenerator using the SailPoint key algorithm
     * <li>Generate a key
     * <li>Base64 encode the key
     * <li>Use the SailPointContext to encrypt the Base64 version of the key
     * </ul>
     * @return String representing the OAuth key
     * @throws GeneralException
     */
    public String generateKey() throws GeneralException {
        String result = null;
        try {
            KeyGenerator gen = KeyGenerator.getInstance(SPKeyStore.KEY_ALGORITHM);
            byte[] encoded = gen.generateKey().getEncoded();
            String clientKey = Base64.encodeBytes(encoded, Base64.DONT_BREAK_LINES);
            result = this.context.encrypt(clientKey);
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralException(e);
        }
        return result;
    }

    /**
     * Encrypts the a string using the key.
     * 
     * @param key String of the key
     * @param value target String to encrypt
     * @return encrypted string
     * @throws GeneralException
     */
    public String encrypt(String key, String value) throws GeneralException {        
        try {
            Cipher c = Cipher.getInstance(CIPHER_ALGORITHM);
            // Cipher is static so synchronize
            // it's use to prevent threading issues
            Key secretKey = getKey(key);
            byte[] iv = new byte[16];
            SecureRandom sRand = new SecureRandom();
            sRand.nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            byte[] raw = null;

            synchronized(c) {
                c.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
                raw = c.doFinal(value.getBytes("UTF-8"));
            }

            byte[] out = new byte[iv.length + raw.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(raw, 0, out, iv.length, raw.length);

            return Base64.encodeBytes(out);
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }
    }
    
    /**
     * Decrypt the string using the key.
     * 
     * @param key The key for decryption
     * @param value Target string to decrypt
     * @return decrypted value
     * @throws GeneralException
     */
    protected String decrypt(String key, String value) throws GeneralException {
        String decrypted = null;
        byte[] iv = new byte[16];
        byte[] in = Base64.decode(value);
        System.arraycopy(in, 0, iv, 0, iv.length);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        byte cipherText [] = new byte [in.length - iv.length];
        byte[] raw = null;

        if (value != null) {
            Key secretKey = getKey(key);
            try {
                Cipher c = Cipher.getInstance(CIPHER_ALGORITHM);
                // Chipher is static so synchronize
                // it's use to prevent threading issues
                synchronized(c) {
                    c.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
                    System.arraycopy(in, iv.length, cipherText, 0, cipherText.length);
                    raw = c.doFinal(cipherText);
                }
                decrypted = new String(raw, "UTF-8");
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }
        return decrypted;
    }
    
    protected Key getKey(String key) throws GeneralException {
        String decrypted = this.context.decrypt(key);
        if (decrypted == null) {
            throw new GeneralException("Transformed key was null");
        }
        byte[] rawKey = Base64.decode(decrypted);
        return new SecretKeySpec(rawKey, SPKeyStore.KEY_ALGORITHM);
    }
}
