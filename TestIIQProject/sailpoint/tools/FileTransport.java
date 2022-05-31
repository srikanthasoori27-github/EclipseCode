/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.File;
import java.io.InputStream;
import java.util.Map;


public interface FileTransport {

    public final static String USER = "user";
    public final static String PASSWORD = "password";
    public final static String HOST = "host";
    public final static String PORT = "port";
    public final static String DESTINATION_DIR = "destinationDir";
    
    public final static String PUBLIC_KEY = "publicKey";
    
    public boolean connect(Map options) throws GeneralException;

    public InputStream download(String pathName) throws GeneralException;
    public File downloadFile(String pathName,String destination) throws GeneralException;

    public boolean completeDownload();
    public void disconnect();
}
