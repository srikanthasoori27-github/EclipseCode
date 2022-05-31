/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileMonitor {
    private static Log log = LogFactory.getLog(FileMonitor.class);
    
    /**
     * 
     */
    private static final FileMonitor _singleton = new FileMonitor();
    
    /**
     * 
     */
    private Timer _timer;
    
    /**
     * 
     */
    private Hashtable<String, FileMonitorTask> _timerEntries;
    
    /**
     * 
     * @return
     */
    public static FileMonitor getInstance() {
        return _singleton;
    }
    
    /**
     * 
     *
     */
    protected FileMonitor() {
        log.debug("Initializing FileMonitor singleton.");
        _timer = new Timer(true);
        _timerEntries = new Hashtable<String, FileMonitorTask>();
    }
    
    /**
     * 
     * @param listener
     * @param fileName
     * @param period
     * @throws FileNotFoundException
     */
    public void addFileListener(FileListener listener,
                                String fileName,
                                long period)
        throws FileNotFoundException {
        log.debug("Adding listener for " + fileName + " checking every " +
                                                              period + "ms.");
        removeFileListener(listener, fileName);
        FileMonitorTask task = new FileMonitorTask(listener, fileName);
        _timerEntries.put(fileName + listener.hashCode(), task);
        _timer.schedule(task, period, period);
    }  // addFileListener(FileListener, String, long)
    
    /**
     * 
     * @param listener
     * @param fileName
     */
    public void removeFileListener(FileListener listener, String fileName) {
        FileMonitorTask task = _timerEntries.remove(fileName + listener.hashCode());
        if ( task != null ) task.cancel();
    }  // removeFileListener(FileListener, String)
    
    /**
     * 
     * @param listener
     * @param fileName
     */
    protected void fileChangedEvent(FileListener listener, String fileName) {
        log.debug(fileName + " changed, firing event.");
        listener.fileChanged(fileName);
    }  // fireChangedEvent(FileListener, String)

    /**
     * 
     *
     */
    class FileMonitorTask extends TimerTask {
        FileListener _listener;
        String _fileName;
        File _file;
        long _lastModified;
        
        /**
         * 
         * @param listener
         * @param fileName
         * @throws FileNotFoundException
         */
        public FileMonitorTask(FileListener listener, String fileName)
            throws FileNotFoundException {
            log.debug("Creating FileMonitorTask for " + fileName);
            
            _listener = listener;
            _fileName = fileName;
            _file = new File(_fileName);
            
            if ( ! _file.exists() ) {
                URL fileURL = listener.getClass().getClassLoader().
                                                        getResource(fileName);
                if ( fileURL != null ) {
                    log.debug(fileName +
                         " is not a file, so it will be monitored as a URL.");
                    _file = new File(fileURL.getFile());
                } else {
                    throw new FileNotFoundException("File not found: " +
                                                                    fileName);
                }
            }
            
            _lastModified = _file.lastModified();
            log.debug("Initial lastModified for " + fileName + " is " +
                                                               _lastModified);
        }  // FileMonitorTask(FileListener, String)
        
        /**
         * 
         */
        public void run() {
            log.debug("Checking lastModified for " + _fileName);
            long lastModified = _file.lastModified();
            if ( lastModified != _lastModified ) {
                _lastModified = lastModified;
                log.debug("New lastModified for " + _fileName + " is " +
                                                               _lastModified);
                fileChangedEvent(_listener, _fileName);
            }
        }  // run()
        
    }  // class FileMonitorTask
}  // class FileMonitor
