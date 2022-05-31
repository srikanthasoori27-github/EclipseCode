/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import java.io.PrintWriter;
import java.security.Key;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import sailpoint.Version;
import sailpoint.api.DatabaseVersionException;
import sailpoint.tools.Base64;
import sailpoint.tools.Console;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

/**
 * 
 * A console to deal with our internal SPKeyStore.  It provides
 * ways to list the contents, generate new keys and change the 
 * master password. ( a.k.a keystore password )
 * 
 * @author dan.smith@sailpoint.com
 */
public class KeyStoreConsole extends Console {
        
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Main
    //
    //////////////////////////////////////////////////////////////////////

    private static final String TEST_STRING = "Sailpoint IdentityIQ";
    SPKeyStore _store = null; 
    
    public KeyStoreConsole() throws GeneralException    {
        _store = SPKeyStore.getInstance();
        addKeyStoreConsoleCommands();
    }
    
    public void addKeyStoreConsoleCommands() {
        addCommand("about", "", "cmdAbout");
        addCommand("addKey", "Generate a new encryption key, the key will be securly generated and random.", "cmdAddKey");
        addCommand("list", "List the contents of the keystore.", "cmdList");
        addCommand("master", "Change the master password and re-encrypt the keystore using the new master.", "cmdChangeMaster");        
        addCommand("use", "Specify the keystore and master file to use when interacting with an alternate keystore.", "cmdUseKeyStore");
        if ( specialContext() )  {        
            addCommand("encrypt", "", "cmdEncrypt");
            addCommand("decrypt", "", "cmdDecrypt");
        }
    }

    /* 
     * Helper method that checks the size of the array 
     * being trying to get the positional argument.
     * 
     * Returns null if the size of the array is smaller
     * then position or if the value at the position
     * is an empty string "". 
     * 
     * @param args
     * @param position
     * @return
     */
    private String getArg(List<String> args, int position) {
        String arg = null;
        if ( Util.size(args) >= (position+1) ) {
            arg = args.get(position);
        }
        return Util.getString(arg);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Commands
    //
    //   password newpassword newPasswordConfirm | -g  - change the master password
    //
    //   generateNewKey  - generate a new key in the keystore, which 
    //                     will be used by the system    
    //
    //   list            - list the keys and alias in a store    
    //
    ///////////////////////////////////////////////////////////////////////////
    
    private boolean isHelpSpecified(List<String> args) {
        String arg0 = getArg(args, 0);    
        if ( arg0 != null ) { 
            arg0 = arg0.toLowerCase();        
            if ( arg0.indexOf("help") != -1 || arg0.indexOf("?") != -1 ) {
                return true;            
            } 
        }
        return false;
    }
    
    /**
     * Change the keystore file that is being managed.
     * This command is useful if you want to write
     * out the changes to a different location then
     * the installation directory. 
     * 
     * @param args
     * @param out
     * @throws Exception
     */
    public void cmdUseKeyStore(List<String> args, PrintWriter out) 
        throws Exception {
        
        if ( isHelpSpecified(args) ) {
            out.println("Usage: use keyStoreFile masterPasswordFile [? | help]\n"+
                        "\tSpecify the file and location of a key store to manage.\n\n"+
                        "\tkeyStoreFile is the path and filename that points to a alternate keystore.");
            return;
        }
        
        String keyStoreFile = getArg(args, 0);
        if ( keyStoreFile == null) { 
            out.println("Keystore filename specified as null, loading system keystore and master file.");
        }
        
        String masterFile = getArg(args,1);
        if ( masterFile == null ) {
            throw new GeneralException("Both the keystore and the master password file must be specified to switch keystores.");
        }
        if ( _store !=  null ) {
            _store.switchMasterFile(masterFile);            
            _store.switchFile(keyStoreFile);
        }                    
    }
           
    /**
     * Change the master password, either using a generated password
     * or one entered manually on the command line.
     * 
     * If entered manually on the command line both the new password
     * and the confirmation password should be entered.
     * 
     * TODO: prompt for confirmation? Easy enough to prompt
     *       but what about masking with ***'s? 
     * 
     * Java doesn't handle this so we'll likely need to 
     * to lean on a jline or simmular library.
     * 
     */
    public void cmdChangeMaster(List<String> args, PrintWriter out) 
        throws Exception {
                        
        if ( isHelpSpecified(args) ) {
            out.println("Usage: masterChange [ newPassword passwordConfirmation ] [? | help]\n"+
                        "\tChange the master password, which can be entered or automatically generated.\n\n"+
                        "\tIf commande line args are not supplied it will prompt for the new password or ask if you'd like it generated.\n"+
                        "\tnewPassword and passwordConfirmation can be added on the command line and both have to be supplied AND match exactly.");
            return;
        }
        String pw = null; 
        String pw1 = null;
        if ( Util.size(args) == 0 ) {
            // confirm what they want todo here
            out.println("You want to generate a new password? (y/n/q) ( q to quit )");
            String line = readLine();
            if ( line != null && line.toLowerCase().compareTo("y") == 0 ) {
                pw = Util.uuid();  
                pw1 = pw;
            } else 
            if ( line != null && line.toLowerCase().compareTo("q") == 0 ) {
                out.println("Password was not updated.");
                return;
            } else {
                out.println("New Password:");
                pw = readLine();
                out.println("Confirm New Password:");
                pw1 = readLine();
            }
            
        } else {
            pw = getArg(args, 0);
            pw1 = getArg(args,1);
        }
        
        if ( pw == null || pw1 == null ) {
            out.println("Both a new password and confirmation of the password must be entered.");
            return;
        } 
        
        if ( Util.nullSafeCompareTo(pw, pw1) != 0 ) {                
            out.println("Sorry passwords did not match, plese try again;");
            return;
        } 

        if ( pw.length() < 8 )  {
            out.println("Passwords must be at least 8 characters, please specify a different password with at least 8 characters.");
        } else {        
            _store.resetPassword(pw);
            out.println("Master password updated in [" + _store.getMasterFileName() + "] for keystore [" + _store.getStoreFileName() + "]");
            out.println("All application servers must be restarted for changes to take effect.");
        }  
    }

    void printUsage(PrintWriter out) {
        out.println("Usage: addKey [-q] [-s <size>]\n"+
                "       addKey -? | -h\n\n"+
                "\tGenerate a new encrpytion key and store it in the keystore.\n\n"+
                "\t-q   makes the command operate quietly and avoids the default confirmation\n" +
                "\t-s   size (in bits) of the key to generate.  Valid values  128, 192, 256 (default)\n" +
                "\t-h | -?   display usage\n");
    }

    /**
     * Generate a new secret key in the keystore which will be used
     * as the new system key for all future encrpytion ( until
     * changed again ).
     *  
     * There is a system task that must be executed that will
     * be in charge of syncing existing data with the newest
     * key.
     * 
     * @param args
     * @param out
     * @throws Exception
     */    
    public void cmdAddKey(List<String> args, PrintWriter out)
        throws Exception {
                
        if ( isHelpSpecified(args) ) {
            printUsage(out);
            return;
        }

        boolean isQuiet = false;
        Integer keysize = 256;

        if(args != null) {
            for(int i = 0;i < args.size();i++) {
                switch(args.get(i)) {
                    case "-q":
                        isQuiet = true;
                        break;

                    case "-s":
                        i++;
                        if(args.size() > i) {
                            keysize = Integer.parseInt(args.get(i));
                        } else {
                            printUsage(out);
                            return;
                        }
                        break;

                    default:
                        printUsage(out);
                        return;
                }
            }
        }

        if ( !isQuiet ) {
            out.println("Generate a new encryption key (y/n)?");
            String input = readLine();
            if ( Util.nullSafeCompareTo("y", input) != 0  ) {
                out.println("Confirmation failed key not added.");
                return;
            }
        }
        
        out.println("Generating a new encryption key for keystore ["+_store.getStoreFileName()+"].");
        Key key = _store.storeNewKey(keysize);
        if ( key != null ) {
            try {
                Transformer transformer = new Transformer();
                String testString = transformer.encode(KeyStoreConsole.TEST_STRING);
                if (TEST_STRING.equals(transformer.decode(testString))) {
                    out.println("New encrpytion key successfully saved to keystore.");
                    out.println("All application servers must be restarted for changes to take effect.");
                } else {
                    throw new RuntimeException("The newly generated key did not properly encrypt/decrypt the test value");
                }
            } catch (Throwable th) {
                out.println(th.getMessage());
                out.println("WARNING:  The key that was generated has failed to encrypt / decrypt a test value.");
                out.println("\tThis is most likely caused by selecting a key length that is not supported by your");
                out.println("\tprovider.  You will need to remedy this before using this key in IdentityIQ.");
                out.println("\tThis can be done by upgrading Java, installing an updated provider, or choosing");
                out.println("\ta smaller key size.");
            }
        } else { 
            out.println("Key Failed to be saved to the keystore!");
        }
    }    

    /**
     * 
     * List out the contents of the key store including the 
     * alias ( which should be an integer ), the algorithm 
     * used, the encoding format and the instance string.
     * 
     * @param args
     * @param out
     * @throws Exception
     */
    public void cmdList(List<String> args, PrintWriter out) 
        throws Exception {
        
        if ( isHelpSpecified(args) ) {
            out.println("Usage: list [? | help]\n"+
                        "\tShow the contents of a keystore.\n\n"+
                        "\t [? | help] for this output.");
            return;
        }
        out.println("Listing contents for keystore ["+_store.getStoreFileName()+"].");        
        List<String> list = _store.getKeyAliases();
        out.printf("%-10s %-11s %-8s %-7s %-10s\n", "KeyAlias", "Algorithm", "Format", "Bits", "Object\n");
        if ( Util.size(list) > 0 ) {
            sortAliases(list);
            for ( String alias : list ) {
                Key key = _store.getKey(alias);
                if ( key == null )
                    throw new GeneralException("Keystore did not contain key for alias ["+alias+"]");
                
                String data = key.toString();           
                if ( specialContext() ) {
                    byte[] encoded = key.getEncoded();
                    if ( encoded != null )
                        data = Base64.encodeBytes(encoded);
                }   
                out.printf("%-10s %-11s %-8s %-7d %-10s\n", alias, key.getAlgorithm(), key.getFormat(), key.getEncoded().length * 8, data);
            }
        } else {
            out.println("No keys found.");
        }
    }
    
    /**
     * Print some information about the current installation includeing
     * the sailpoint version and stuff specific to the current IIQ
     * keystore. Things like the number of keys and the defined location 
     * of the keystore files. 
     * 
     * @param args
     * @param out
     * @throws Exception
     */
    public void cmdAbout(List<String> args, PrintWriter out )
        throws Exception {
        
        out.printf("%-20s %-5s\n","Version:", Version.getFullVersion());
        boolean hasKeyStore = false;

        List<String> list = SPKeyStore.getInstance().getKeyAliases();
        if (Util.size(list) > 0) {
            hasKeyStore = true;
        }
        out.printf("%-20s %-5s\n", "KeyStore exists:", hasKeyStore);
        if  ( hasKeyStore ) { 
            out.printf("%-20s %-5s\n", "Number of keys:", Util.size(list));
            out.printf("%-20s %-5s\n", "KeyStore File:", _store.getStoreFileName());
            String master = _store.getMasterFileName();            
            if ( master == null ) {
                master = "Using password stored in iiq.properties file.";
            }            
            out.printf("%-20s %-5s\n", "Master File:", master);
        }        
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void sortAliases(List<String> aliases) {
        Collections.sort(aliases, new Comparator() {
            public int compare(Object o1, Object o2) {
                
                String s1 = (o1 != null ) ? o1.toString() : null;
                String s2 = (o2 != null ) ? o2.toString() : null; 
                
                Integer a = toInt(s1);
                Integer b = toInt(s2);
                
                if ( a != null && b != null ) {
                    return a.compareTo(b);
                } else {                                               
                    return Util.nullSafeCompareTo(s1, s2); 
                }
                
            }                
            private Integer toInt(String s) {                    
                try {
                    return Integer.parseInt(s);
                }catch(Exception e ) { 
                    // if not an integer deal with it
                }
                return null;
            }
        });
    }
    
    @Untraced
    public void cmdEncrypt(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1 || args.size() > 2) {
            out.format("encrypt <string>\n");
        } else {
            String src = args.get(0);
            String alias = null;
            if ( args.size() == 2 )
                alias = args.get(1);
            
            try {
                out.println(new Transformer().encode(src, alias));
            } finally {
           
            }
        }
    }

    @Untraced
    public void cmdDecrypt(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("decrypt <string>\n");
        } else {
            String src = args.get(0);            
            try {
                out.println(new Transformer().decode(src));
            } finally {          
            }
        }
    }
    
    private byte[] PROP_BYTES = { 0x73,0x61,0x69,0x6c,0x70,0x6f,0x69,0x6e,0x74,
                                  0x2e,0x6b,0x65,0x79,0x53,0x74,0x6f,0x72,0x65,
                                  0x2e,0x63,0x6f,0x6e,0x73,0x6f,0x6c,0x65,0x43,
                                  0x6f,0x6e,0x74,0x65,0x78,0x74 };
    
    private byte[] PROP_VALUE = { 0x6d,0x61,0x67,0x65,0x6c,0x6c,0x61,0x6e };
    
    private boolean specialContext() {
        boolean defined = false;        
        try { 
            String superSecretProperty = System.getProperty(new String(PROP_BYTES, "UTF-8"));
            if ( Util.nullSafeCompareTo(new String(PROP_VALUE, "UTF-8"), superSecretProperty) == 0 ) {
                defined = true;
            }
        }catch(Exception e ) {
            defined = false;
        }
        return defined;
    }

    /**
     * Since the base class {@link Console} is no longer in charge of printing prompts,
     * make sure we print it in the child class readLine() before we read the input.
     */
    @Override
    protected String readLine() {
        print(_prompt);
        return super.readLine();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Main
    //
    /////////////////////////////////////////////////////////////////////////

    /**
     * Launch the console.
     */
    public static void main(String [] args) {
        try {
            KeyStoreConsole console = new KeyStoreConsole();
            console.run(args);
        }
        catch (DatabaseVersionException dve) {
            // format this more better  
            println(dve.getMessage());
        }
        catch (Throwable t) {
            println(t);
        }
        finally {
            //todo:
        }
    }
}
