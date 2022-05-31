/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating encryption services.
 *
 * Prior to 6.2 this was named Cryptographer which a few enterprising
 * customers found in the .jar and tried to use.  It now has a less
 * obvious name.
 *
 */

package sailpoint.server;

import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import sailpoint.api.ObjectUtil;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.tools.SensitiveTraceReturn;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

// TODO: should be package private

class Transformer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    public static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    public static final String CIPHER_ALGORITHM_IDENTIFIER = "ACP";
    public static final String ENCODING_DELIMITER = ":";

    /**
     * Special key identifier used to indiciates the value
     * is not encrypted.
     */
    public static final String KEYID_ASCII = "ascii";

    // these seem expensive to create to cache them
    private static Cipher _cipher;

    // Cipher that can be used to decrypt pre-CBC encrypted text
    private static Cipher _compatibilityCipher;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Transformer() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Keys
    //
    //////////////////////////////////////////////////////////////////////


    //////////////////////////////////////////////////////////////////////
    //
    // Encryption
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Cache the Cipher we'll be using as they can be expensive to lookup.
     * Assuming calling init() isn't expensive.
     */
    private Cipher getCipher() throws GeneralException {
        try {
            if (_cipher == null) {
                _cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            }
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }
        return _cipher;
    }

    /**
     * Gets a Cipher that can be used to decrypt a pre-CBC encrypted string.
     *
     * @return AES cipher (using default mode and padding)
     * @throws GeneralException
     */
    private Cipher getCompatibilityCipher() throws GeneralException {
        try {
            if (_compatibilityCipher == null) {
                _compatibilityCipher = Cipher.getInstance("AES");
            }
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }
        return _compatibilityCipher;
    }

    /**
     * Encrypt a string using the key stored in the keystore
     * with the given alias.
     * 
     * This is necessary to provide a way to allow encrpying
     * values (like the master password) using the default
     * key even when there are other keys defined in the
     * system. 
     *  
     * @param src
     * @param alias
     * @return
     * @throws GeneralException
     */
    @Untraced
    protected String encode(String src, String alias)
        throws GeneralException {
        
        int numid;

        // jsl - is testing for "default" really necessary?
        // It looks like the only thing that calls this is
        // sailpoint.server.Encryptor which is what "iiq encrypt" uses.
        // It will normally just pass null

        if (alias == null || "default".compareToIgnoreCase(alias) == 0) {
            numid = SPKeyStore.getInstance().getCurrentAliasId();
        }
        else {
            numid = Util.atoi(alias);
        } 

        return encode(src, numid);
    }
    
    /**
     * 
     * Encrypt the a string using the current key.
     * 
     * @param src
     * @return
     * @throws GeneralException
     */
    @Untraced
    public String encode(String src)
        throws GeneralException {
    
        return encode(src, SPKeyStore.getInstance().getCurrentAliasId());        
    }

    @Untraced
    protected String encode(String src, int alias) throws GeneralException {
        
        return encode(src, alias, true);
    }

    @Untraced
    protected String encode(String src, boolean checkForEncoded)
        throws GeneralException {

        return encode(src, SPKeyStore.getInstance().getCurrentAliasId(), checkForEncoded);
    }

    /**
     * Encrypt the string using the current key.
     * @param src String to encrypt
     * @param alias id of the key to use
     * @param checkForEncoded true to check if src has already been encoded. If it has, return the string as is
     * @return encoded value of a given string
     * @throws GeneralException
     */
    @Untraced
    protected String encode(String src, int alias, boolean checkForEncoded)
        throws GeneralException {

        if (src == null || (checkForEncoded && isEncoded(src))) {
            return src;
        }

        String encrypted = null;
        if (src != null) {
            try {
                Cipher c = getCipher();
                // Cipher is static so synchronize
                // it's use to prevent threading issues
                Key key = SPKeyStore.getInstance().getKeyById(alias);
                byte[] iv = new byte[16];
                SecureRandom sRand = new SecureRandom();
                sRand.nextBytes(iv);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
                byte[] raw = null;
                synchronized(c) {
                    c.init(Cipher.ENCRYPT_MODE, key, ivParameterSpec, sRand);
                    raw = c.doFinal(src.getBytes("UTF-8"));
                }
                byte[] out = new byte[iv.length + raw.length];
                System.arraycopy(iv, 0, out, 0, iv.length);
                System.arraycopy(raw, 0, out, iv.length, raw.length);

                encrypted = Base64.encodeBytes(out);

            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }

        // add the key index and algorithme to the front of the encrypted string
        encrypted = alias + ENCODING_DELIMITER +
                CIPHER_ALGORITHM_IDENTIFIER + ENCODING_DELIMITER + encrypted;

        return encrypted;
    }



    @Untraced
    @SensitiveTraceReturn
    protected String decode(String src, Key key) throws GeneralException {
        String decrypted = null;
        if (src != null) {
            try {
                Cipher c = getCipher();
                byte[] iv = new byte[16];
                byte[] in = Base64.decode(src);
                System.arraycopy(in, 0, iv, 0, iv.length);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

                byte cipherText [] = new byte [in.length - iv.length];
                byte[] raw = null;
                // Cipher is static so synchronize
                // it's use to prevent threading issues
                synchronized(c) {
                    c.init(Cipher.DECRYPT_MODE, key, ivParameterSpec);
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

    protected String compatibilityDecode(String src, Key key) throws GeneralException {
        String decrypted = null;
        if (src != null) {
            try {
                Cipher c = getCompatibilityCipher();
                byte[] in = Base64.decode(src);

                // Cipher is static so synchronize
                // it's use to prevent threading issues
                synchronized(c) {
                    c.init(Cipher.DECRYPT_MODE, key);
                    byte[] raw = c.doFinal(in);
                    decrypted = new String(raw, "UTF-8");
                }
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }
        return decrypted;
    }


    @Untraced
    @SensitiveTraceReturn
    public String decode(String src) 
        throws GeneralException {
        
        if (!isEncoded(src)) {
            return src;
        }

        String decrypted = null;
        if (src != null) {
            String[] parts = src.split(":");

            String id = parts[0];
            String algorithm = null;
            String remainder = null;
            if(parts.length == 3) {
                algorithm = parts[1];
                remainder = parts[2];
            } else if(parts.length == 2){
                remainder = parts[1];
            } else {
                decrypted = src;
            }

            if(decrypted == null) {
                if (id.equals(KEYID_ASCII))
                    decrypted = remainder;
                else {
                    // Util returns zero if the string isn't numeric
                    // since we have no key id zero this can't be encrypted
                    int numid = Util.atoi(id);
                    if (numid <= 0)
                        decrypted = src;
                    else {
                        if(algorithm == null) {
                            decrypted = compatibilityDecode(remainder, SPKeyStore.getInstance().getKeyById(numid));
                        } else {
                            decrypted = decode(remainder, SPKeyStore.getInstance().getKeyById(numid));
                        }
                    }
                }
            }
        }
        return decrypted;
    }

    /**
     * Try to guess if a string looks encrypted.
     * This is intended only to help transition from storing
     * unencrypted strings, it is not 100% reliable but should
     * be close enough for our purposes.
     */
    @Untraced
    public boolean isEncoded(String src) throws GeneralException {

        return ObjectUtil.isEncoded(src);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // main
    //
    //////////////////////////////////////////////////////////////////////

    public static void println(Object o) {
        System.out.println(o);
    }

    /**
     * The behavior of the main() method is important because
     * this is one of the classes that can be launched
     * from the "iiq" script via the Launcher class.
     *
     * One argument is expected which is a string of clear text
     * to be encrypted.  The encryption of this string is printed.
     * 
     * If you need to add other test methods, you could do this
     * with - flags.
     */
    @Untraced
    public static final void main(String[] args) {

        try {
            if (args.length < 1 || args.length > 2 )  
                println("usage: encrypt <string>");
            else {
                Transformer c = new Transformer();
                String src = args[0];
                if ( src == null ) {
                    throw new Exception("Source string must not be null.");
                }
                String alias = null;
                if ( args.length == 2 )
                    alias = args[1];
                           
                String enc = c.encode(src, alias);
                //println(src + " encrypts to " + enc);
                println(enc);
            }
        }
        catch (Throwable t) {
            System.out.println(t);
        }
    }

}
