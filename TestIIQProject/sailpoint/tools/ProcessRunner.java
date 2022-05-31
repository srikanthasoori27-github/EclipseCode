/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility to asynchronously launch an operating system process 
 * and provide a way to monitor it.
 * 
 * Author: Jeff
 *
 */

package sailpoint.tools;

import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utility to asynchronously launch an operating system process 
 * and provide a way to monitor it.
 */
public class ProcessRunner extends Thread {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ProcessRunner.class);

    /**
     * The size of a char[] buffer we use when reading from
     * the InputStreamReader.
     */
    public static final int READ_CHUNK = 1024;

    //
    // Launch optinos
    //

    /**
     * The command line to execute.
     */
    List<String> _commandLine;

    /**
     * The maximum number of milliseconds to wait for the task to complete.
     * If this is zero we wait forever.
     */
    int _maxWait;

    //
    // Runtime state
    //

    /**
     * The process we launched.
     */
    Process _process;

    /**
     * A reader that consumes the byte stream containing what was sent
     * to the output and error streams of the process and converts
     * them into chars.  
     * 
     * What concerns me is whether this is smart enough to not block
     * on an InputStream read if it contains only 1 byte of a 2 byte char.
     * The InputStream won't be empty so ready() will return true, 
     * but since it can't form a complete character it doesn another
     * InputStream read which blocks.  Beware.
     */
    InputStreamReader _stream;

    /**
     * The result accumulated from the stream.
     */
    StringBuilder _result;

    /**
     * May be set by the controlling thread to force the process to 
     * terminate.
     */
    boolean _terminate;

    /**
     * Set when the monitor thread terminates.
     */
    boolean _terminated;

    /**
     * The number of milliseconds to wait between polls of the stream.
     */
    int _cycleWait = 100;

    /**
     * The total number of milliseconds we've spend waiting for the 
     * process to complete.
     */
    int _totalWait;

    /**
     * The exit value of the process.
     */
    int _exitValue;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public ProcessRunner(String command) {
        add(command);
    }

    public ProcessRunner(List<String> command) {
        _commandLine = new ArrayList<String>(command);
    }

    public void add(String cmd) {
        if (cmd != null) {
            if (_commandLine == null)
                _commandLine = new ArrayList<String>();
            _commandLine.add(cmd);
        }
    }

    public void setMaxWaitSeconds(int i) {
        _maxWait = i * 1000;
    }

    public void terminate() {
        _terminate = true;
    }

    /**
     * Returns true when the monitor thread has completed.
     */
    public boolean isTerminated() {
        return _terminated;
    }

    public int getExitValue() {
        return _exitValue;
    }

    public int getTotalWait() {

        return _totalWait;
    }

    public int getTotalWaitSeconds() {

        return _totalWait / 1000;
    }

    /**
     * Return the currently available process results.
     * The result buffer is cleared as a side effect.
     * Note that this must be synchronized because the monitor
     * thread may be putting things into it at the same time. 
     * And no, we can't use StringBuffer here because we need
     * an atomic toString() and setLength() pair.
     */
    public synchronized String getResult() {

        String result = null;
        if (_result != null && _result.length() > 0) {
            result = _result.toString();
            _result.setLength(0);
        }
        return result;
    }

    /**
     * Add a block to the result buffer.
     * This is called internally by the monitor thread, it must
     * be synchronized because an application thread may
     * be calling getResult at the same time.
     */
    private synchronized void addResult(char[] block, int length) {
        
        _result.append(block, 0, length);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Launch
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch the process and synchronously wait for it to end.
     * You normally don't want this, but it's here for completness.
     * This makes it behave like ProcessUtil.exec though we're handling
     * character conversion properly and we can deal with multi-block
     * input streams.
     */
    public String runSync() {

        if (_process == null && !_terminated) {

            // start the thread
            start();

            // wait, patiently
            while (!_terminated) {
                try {
                    Thread.sleep(_cycleWait);
                }
                catch (java.lang.InterruptedException e) {
                }
            }
        }
        
        return getResult();
    }

    /**
     * Call the Thread.start method to get the ball rolling.
     * We'll get here eventually.
     */
    public void run() {
        try {

            char[] buffer = new char[READ_CHUNK];
            _totalWait = 0;

            // launch the process and get the input stream
            launchProcess();
            
            _result = new StringBuilder();

            // Accumulate stuff from the input stream until the
            // process exits
            while (!_terminate) {

                if (!_stream.ready()) {

                    // before we wait see if the process has terminated
                    // not sure how expensive this is, may only want to 
                    // do it on some cycles?
                    if (isProcessTerminated())
                        _terminate = true;
                    else {
                        try {
                            Thread.sleep(_cycleWait);
                        }
                        catch (java.lang.InterruptedException e) {
                        }
                        _totalWait += _cycleWait;
                    }
                }
                else {
                    // read what we can into the result buffer
                    try {
                        int actual = _stream.read(buffer, 0, READ_CHUNK);
                        if (actual > 0) {
                            // we must synchronize this!
                            addResult(buffer, actual);
                        }
                        else if (actual == -1) {
                            // shouldn't get here if ready() was true?
                            log.error("Premature end of stream");
                            _terminate = true;
                        }
                    }
                    catch (java.io.IOException e) {
                        // shouldn't get here
                        log.error("IOException reading stream");
                        _terminate = true;
                    }
                }

                if (_maxWait > 0 && _totalWait > _maxWait)
                    _terminate = true;
            }

            // this also sets _exitValue as as side effect
            if (!isProcessTerminated()) {
                // is this important?
                _process.destroy();
            }
        }
        catch (Throwable t) {
            log.error(t);
        }

        _terminated = true;
    }

    /**
     * Start the process and create input stream.
     *
     * According to the docs:
     *   "There is no requirement that a process
     *    represented by a Process object execute asynchronously 
     *    or concurrently with respect to the Java process that owns
     *    the Process object."  
     *
     * I'm taking this to mean that once you call Runtime.exec or
     * ProcessBuilder.start the process could be launched synchronously
     * and you will get back a Process object for a terminated process.
     * Presumably the entire JVM is suspended while this happens?
     * I have never observed this to happen so I'm not sure what it means.
     *
     * Prior to 1.5 you did this with Runtime.exec:
     *
     *   Runtime r = Runtime.getRuntime();
     *   Process p = r.exec(command);
     *
     * Now we have ProcessBuilder which looks like it does the same thing
     * and has more options.
     */
    private void launchProcess() throws GeneralException {

        if (_process == null) {

            if (_commandLine == null || _commandLine.size() == 0)
                throw new GeneralException("Command not specified");

            ProcessBuilder pb = new ProcessBuilder(_commandLine);

            // merge stderr and stdout, conditionalize this?
            pb.redirectErrorStream(true);
            
            // temporary debugging
            /*
            File f = pb.directory();
            if (f != null)
                System.out.println("Working directory: " + f.toString());
            */

            try {
                _process = pb.start();  

                // it's an input stream for the output stream, get it!?
                InputStream is = _process.getInputStream();
                _stream = new InputStreamReader(is);
            }
            catch (java.io.IOException e) {
                // command name must have been wrong, soften the exception?
                throw new GeneralException(e);
            }
        }
    }

    /**
     * Return true if the process has exited.
     * Note that this differs from isTerminated which checks
     * to see if this Thread's run method has terminated.  There may be
     * a short window between the process terminating and the thread
     * terminating.
     */
    public boolean isProcessTerminated() {

        boolean exited = false; 
        if (_process != null) {
            try {
                // might as well save this
                _exitValue = _process.exitValue();
                exited = true;
            }
            catch (IllegalThreadStateException e) {
                // process must still be alive
            }
        }

        return exited;
    }

}
