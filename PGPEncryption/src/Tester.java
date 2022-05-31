

public class Tester {
 
    private static final String PASSPHRASE = "mykey";
 
    private static final String DE_INPUT = "/Users/srikanth.asoori/Desktop/Projects/GOVTECH/SFTP/x.pgp";
    private static final String DE_OUTPUT = "/Users/srikanth.asoori/Desktop/Projects/GOVTECH/SFTP/x.csv";
    private static final String DE_KEY_FILE = "/Users/srikanth.asoori/Desktop/Projects/GOVTECH/SFTP/pgpprivatekey.skr";
 
    private static final String E_INPUT = "/Users/srikanth.asoori/Desktop/Projects/GOVTECH/SFTP/dev/plaintextfile.csv";
    private static final String E_OUTPUT = "/Users/srikanth.asoori/Desktop/Projects/GOVTECH/SFTP/x.pgp";
    private static final String E_KEY_FILE = "/Users/srikanth.asoori/Desktop/Projects/GOVTECH/SFTP/pgppublickey.pkr";
 
 
    public static void main (String args[])
    {
    	try {
    		System.out.println("Starting ..");
			testEncrypt();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    public static void testDecrypt() throws Exception {
        PGPFileProcessor p = new PGPFileProcessor();
        p.setInputFileName(DE_INPUT);
        p.setOutputFileName(DE_OUTPUT);
        p.setPassphrase(PASSPHRASE);
        p.setSecretKeyFileName(DE_KEY_FILE);
        System.out.println(p.decrypt());
    }
 
    public static void testEncrypt() throws Exception {
        PGPFileProcessor p = new PGPFileProcessor();
        p.setInputFileName(E_INPUT);
        p.setOutputFileName(E_OUTPUT);
        p.setPassphrase(PASSPHRASE);
        p.setPublicKeyFileName(E_KEY_FILE);
        System.out.println(p.encrypt());
    }
}