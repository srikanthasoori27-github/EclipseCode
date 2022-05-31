import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
 
import com.didisoft.pgp.KeyStore;
import com.didisoft.pgp.PGPException;
import com.didisoft.pgp.PGPLib;
import com.didisoft.pgp.exceptions.DetachedSignatureException;
import com.didisoft.pgp.exceptions.FileIsPBEEncryptedException;
import com.didisoft.pgp.exceptions.IntegrityCheckException;
import com.didisoft.pgp.exceptions.NoPrivateKeyFoundException;
import com.didisoft.pgp.exceptions.NonPGPDataException;
import com.didisoft.pgp.exceptions.WrongPasswordException;
import com.didisoft.pgp.exceptions.WrongPrivateKeyException;
 
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
 
public class PGPEncryptionUtitlity {
 
 
                public static InputStream optSelection(SailPointContext context, String operation) throws Exception {
                                InputStream iso = null;
 
                                Properties p = new Properties();
// Path will be changed as per the environment and can also be pulled from a custom file in Sailpoint.
                                String path = "D:\\BM\\local\\apache-tomcat-8.5.31\\webapps\\identityiq\\WEB-INF\\classes\\com\\bm\\decryption\\arguments.properties";
                                try {
                                                FileReader fr = new FileReader(path);
 
                                                p.load(fr);
                                } catch (FileNotFoundException e) {
                                                System.out.println("FileNotFoundException = " + e);
 
                                } catch (IOException e) {
                                                System.out.println("IOException = " + e);
 
                                } catch (Exception e) {
                                                System.out.println("Exception = " + e);
 
                                }
 
                                if (operation.equalsIgnoreCase("Decrypt")) {
 
                                                iso = decrypt(p, context);
                                } else if (operation.equalsIgnoreCase("Delete")) {
 
                                                delete(p, context);
                                }
                                return iso;
   }
 
// This method will Decrypt the CSV file
                private static InputStream decrypt(Properties p, SailPointContext context) throws Exception, PGPException {
 
                                InputStream is = null;
                                try {
                                                String keyStorePassword = context.decrypt(p.getProperty("keyStorePassword"));
                                                String privateKeyPassword = context.decrypt(p.getProperty("privateKeyPassword"));
                                                String keyStorePath = context.decrypt(p.getProperty("keyStorePath"));
                                                String PGPfilePath = context.decrypt(p.getProperty("PGPfilePath"));
                                                String decryptedFilePath = context.decrypt(p.getProperty("decryptedFilePath"));
                                                KeyStore keyStore = new KeyStore(keyStorePath, keyStorePassword);
                                                PGPLib pgp = new PGPLib();
                                                pgp.decryptFile(PGPfilePath, keyStore, privateKeyPassword, decryptedFilePath);
                                                System.out.println("-----File Decrypted-----");
                                                is = iteration(decryptedFilePath);
                                } catch (FileNotFoundException e) {
                                                System.out.println("FileNotFoundException = " + e);
 
                                } catch (GeneralException e) {
                                                System.out.println("GeneralException = " + e);
 
                                } catch (Exception e) {
                                                System.out.println("Exception = " + e);
                                }
                                return is;
                }
 
 
// This will iterate the data when we will click the Preview button
                private static InputStream iteration(String decryptedFilePath) throws FileNotFoundException {
 
                                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(decryptedFilePath)));
                                return bis;
                }
 
// This method will delete the decrypted CSV File
private static void delete(Properties p, SailPointContext context) {
                                Path p1 = null;
                                try {
                                                p1 =      Paths.get(context.decrypt(p.getProperty("decryptedFilePath")));
                                                Files.delete(p1);
                                                System.out.println("-----File Deleted-----");
                                } catch (GeneralException e) {
                                                System.out.println("GeneralException = " + e);
                                } catch (IOException e) {
                                                System.out.println("IOException = " + e);
                                } catch (Exception e) {
                                                System.out.println("Exception -----------" + e);
                                }
                }
}