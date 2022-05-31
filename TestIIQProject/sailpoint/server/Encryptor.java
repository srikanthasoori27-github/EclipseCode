/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A public wrapper around Cryptographer to expose the ability to 
 * encrypt but not decrypt.  This is called by the "encrypt" Launcher command.
 */

package sailpoint.server;

import sailpoint.tools.Untraced;

// TODO: should be package private

/**
 * One argument is expected which is a string of clear text
 * to be encrypted.  The encryption of this string is printed.
 */
public class Encryptor {

    @Untraced
    public static final void main(String[] args) {

        try {
            if (args.length < 1 || args.length > 2 )  
                System.out.println("usage: encrypt <string> [keynum]");
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
                System.out.println(enc);
            }
        }
        catch (Throwable t) {
            System.out.println(t);
        }
    }
}
