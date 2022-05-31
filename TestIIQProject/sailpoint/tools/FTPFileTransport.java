/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;


public class FTPFileTransport implements FileTransport {

    private static Log _log = LogFactory.getLog(FTPFileTransport.class);

    public static final String FTP_PORT = "21";
   
    public static final String OP_ACTIVE_MODE = "LocalActiveMode";
    public static final String OP_MODE = "connectionMode";
    public static final int OP_BINARY_FILE_TYPE = FTP.BINARY_FILE_TYPE;
    public static final int OP_ASCII_FILE_TYPE = FTP.ASCII_FILE_TYPE;
    public static final String OP_FILE_TYPE = "fileType";
    public static final String OP_DISABLE_FTP_COMPLETE = "ftpDisableComplete";
    
    private FTPClient _ftp;
    private boolean _loggedIn;
    private boolean _downloadCompleted;
    private boolean _completePending;

    public FTPFileTransport() {
        _ftp = new FTPClient();
        _downloadCompleted = true;
        _loggedIn = false;
        _completePending = true;
    }

    @Override
    public boolean connect(Map options) throws GeneralException {

        boolean success = false;
        String port = FTP_PORT;
        try {

            if ( options == null ) options = new HashMap<String,Object>();

            String server = (String)options.get(HOST);
            
            port = (String) options.get(PORT);
            if ( _log.isDebugEnabled() ) {
                _log.debug("Connecting to " + port + "." );
            }
            if (port == null)
                port = FTP_PORT;
            
            int reply;

            _ftp.connect(server, Integer.parseInt(port));
            if ( _log.isDebugEnabled() ) {
                _log.debug("Connecting to " + server +  " port " + port + "." + "Reply: " 
                           + _ftp.getReplyString());
            }
            String mode = (String)options.get(OP_MODE);
            if ( ( mode != null ) && ( OP_ACTIVE_MODE.compareTo(mode) == 0 ) ) {
                _ftp.enterLocalActiveMode();
            } else {
                _ftp.enterLocalPassiveMode();
            }

            // After connection attempt, you should check the reply code to 
            // verify  success.
            reply = _ftp.getReplyCode();
            if(!FTPReply.isPositiveCompletion(reply)) {
                disconnect();
                throw new GeneralException("Unable to connect to ftp server:" +server);
            }

            String user = (String)options.get(USER);
            String password = (String)options.get(PASSWORD);

            success = _ftp.login(user, password);
            /*DEBUG*/if ( _log.isDebugEnabled() ) {
            /*DEBUG*/    _log.debug("Login.. Reply: " + _ftp.getReplyString());
            /*DEBUG*/}
            if ( success )  {
                _loggedIn = true;
            }
            Integer fileType = (Integer)options.get(OP_FILE_TYPE);
            if ( fileType != null ) {
                _ftp.setFileType(fileType.intValue());
            }
            /*DEBUG*/if ( _log.isDebugEnabled() ) {
            /*DEBUG*/    _log.debug("FileType.. Reply: " + _ftp.getReplyString());
            /*DEBUG*/}
            Boolean disableComplete = (Boolean)options.get(OP_DISABLE_FTP_COMPLETE);
            if ( disableComplete != null ) {
                if ( disableComplete.booleanValue() ) {
                    _completePending = false;
                }
            }
        } catch (UnknownHostException e){
        	throw new GeneralException("Unknown Host:" +(String)options.get(HOST));
        }
        catch(IOException e) {
            disconnect();
            throw new GeneralException(e);            
        } 
        return success;
    }


    private void validateSession() throws GeneralException {
        if (!_downloadCompleted) {
            throw new GeneralException("Previous download was not marked" 
                           + " completed.  Call completeDowload() first.");
        }
        if ( _ftp == null )
            throw new GeneralException("FTP Protocol unavaliable");

        if ( !_ftp.isConnected() )
            throw new GeneralException("FTP: Not connected, must connect before download.");
             
        if ( !_loggedIn )
            throw new GeneralException("FTP: Not logged in, must login before download.");
    }

    /*
     * NOTE djs: I tried to use the _ftp.retrieveFile method,
     * but it was hanging at a customer where the ftp server
     * was running in Z/OS on a very slow network.
     */
    @Override
    public File downloadFile(String pathName, String destination) 
        throws GeneralException { 

        validateSession();
        File fqFile = null;
        FileOutputStream fos = null;

        try {

            fqFile = new File(destination);
            fos = new FileOutputStream(fqFile);
            InputStream is = download(pathName);
            if ( is == null ) {
                throw new GeneralException("Stream returned from ftp call was null.");
            }
            byte[] buffer = new byte[2048];
            int bytesRead = 0;
            while ( ( bytesRead = is.read(buffer, 0, 2048) ) != -1 ) {
                fos.write(buffer,0,bytesRead);
            } 
            is.close();
            is = null;
        } catch(IOException e) {
            throw new GeneralException(e);
        } finally {
            if ( fos != null ) {
                try {
                    fos.close();
                } catch(IOException e ) {
                    _log.error("Error while closing file output stream." 
                               + e.toString());
                }
            }
        }
        return fqFile;
    }

    @Override
    public InputStream download(String pathName) throws GeneralException {
        validateSession();
        InputStream stream = null;
        try {
             InputStream ftpStream = _ftp.retrieveFileStream(pathName);
             int bufferSize = _ftp.getBufferSize();
             /*DEBUG*/if ( _log.isDebugEnabled() ) {
             /*DEBUG*/   _log.debug("Download.. Reply: "+_ftp.getReplyString());
             /*DEBUG*/}
             if ( ftpStream == null ) {
                 throw new GeneralException("unable to get file stream for : " + pathName);
             }
             if (bufferSize > 0) {
                 stream = new BufferedInputStream(ftpStream, bufferSize);
             } else {
                 stream = new BufferedInputStream(ftpStream);
             }
            _downloadCompleted = false;
        } catch(IOException e) {
            throw new GeneralException(e);
        } 
        return stream;
    }

    @Override
    public boolean completeDownload() {
        boolean success = false;
        try {
            if ( _ftp != null ) {
                if ( ( _ftp.isConnected() ) && ( !_downloadCompleted ) ) {
                    if ( _completePending ) {
                        success = _ftp.completePendingCommand(); 
                    } else {
                        _log.debug("completePendingCommand skipped.");
System.out.println("completePendingCommand skipped.");
                    }
                    _downloadCompleted = true;
                }
            }
        } catch(IOException e) {
            success = false;
            _log.error("Error completing download:" + e.toString());
        }
        return success;
    }
    
    @Override
    public void disconnect() {
        try {
            if ( _ftp != null ) {
                if ( _ftp.isConnected() ) {
                    if ( _loggedIn ) {
                        _ftp.logout();   
                        _loggedIn = false;
                    }
                    _ftp.disconnect();                    
                }
                _ftp = null;
            }
        } catch(IOException e) {            
            // throw these away
            _log.error("Error during FTP disconnect: " + e.toString());
        }
    }

    public void setFileType(int fileType) {
        try {
            if (_ftp != null) {
                if (_ftp.isConnected()) {
                    _ftp.setFileType(fileType);
                }
            }
        } catch (IOException e) {
            _log.error("Error setting file type to: " + fileType, e);
        }
    }
}
