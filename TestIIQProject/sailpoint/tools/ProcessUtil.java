/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility to help coordinate the execution of other operating
 * system processes.
 *
 * Author: Jeff
 *
 * This was originally created for the MiningToolkit integration, but
 * may have other uses.   I've done something similar with Runtime.exec
 * but 1.5 has the spiffy new ProcessBuilder so we're going to try that.
 *
 */
package sailpoint.tools;

import java.io.InputStream;
import java.io.File;
import java.util.List;

public class ProcessUtil {

    /**
     * Synchronously Execute a shell command and return what was
     * sent to the output and error streams.  This is a very simple
     * runner than assumes there will only be one flush of the 
     * stdout or stderr streams.  As soon as we get anything from
     * InputStream we return it and stop.
     */
    public static String exec(String command) throws GeneralException {

        String value = null;
        Process p = null;
        boolean useRuntime = false;

        if (useRuntime) {
            // the old way, any difference?
            Runtime r = Runtime.getRuntime();
            try {
                p = r.exec(command);
            }
            catch (java.io.IOException e) {
                // command name must have been wrong, soften the exception?
                throw new GeneralException(e);
            }
        }
        else {
            ProcessBuilder pb = new ProcessBuilder(command);

            // merge stderr and stdout
            pb.redirectErrorStream(true);

            File f = pb.directory();
            if (f != null)
                System.out.println("Working directory: " + f.toString());

            try {
                p = pb.start();
            }
            catch (java.io.IOException e) {
                // command name must have been wrong, soften the exception?
                throw new GeneralException(e);
            }
        }

        // With older versions of the Windows JRE, waitFor would hang.
        // If you don't wait at all and immediately look in the InputStream,
        // there won't be anything there, so we have to pause for some period
        // of time to let the process do its thing.
        // On Unix, it seems to slow things down, so lets just
        // avoid it in both cases?

        String os = System.getProperty("os.name");
        boolean isWindows = os.startsWith("Windows");
        // if (!isWindows) {
        if (false) {
            try {
                int status = p.waitFor();
            }
            catch (java.lang.InterruptedException e) {}
        }

        InputStream is = p.getInputStream();
        int avail = 0;
        try {
            avail = is.available();
        }
        catch (java.io.IOException e) {
            throw new GeneralException(e);
        }

        int cycleWait = 100;
        int maxWait = 3000;
        int totalWait = 0;

        while (avail == 0 && totalWait < maxWait) {
            try {
                Thread.sleep(cycleWait);
            }
            catch (java.lang.InterruptedException e){}
            totalWait += cycleWait;
            try {
                avail = is.available();
            }
            catch (java.io.IOException e) {
                throw new GeneralException(e);
            }
        }

        if (avail > 0) {
            StringBuffer b = new StringBuffer();
            while (avail > 0) {
                byte[] bytes = new byte[avail];
                try {
                    is.read(bytes, 0, avail);
                    // StringBuffer doesn't coerce bytes to characters,
                    // have to use String?
                    // UNIX WARNING: I've noticed some tests where
                    // large blocks of zero bytes make it in here,
                    // needs further investigation
                    String s = new String(bytes);
                    b.append(s);
                    avail = is.available();
                }
                catch (java.io.IOException e) {
                    // Given our restricted use of this stream, we shouldn't
                    // get here.
                    avail = 0;
                    throw new GeneralException(e);
                }
            }
            value = b.toString();
        }

        // If waitFor is hanging, I'm not sure what state the process
        // is in. Go ahead and kill it now just to be safe.
        p.destroy();

        return value;
    }

    /**
     * Synchronously execute a command from a list of command line args.
     */
    public static String exec(List<String> cmdline) throws GeneralException {

        String result = null;

        if (cmdline != null) {
            StringBuilder b = new StringBuilder();
            for (int i = 0 ; i < cmdline.size() ; i++) {
                if (i > 0) b.append(" ");
                b.append(cmdline.get(i));
            }
            String cmd = b.toString();
        
            result = exec(cmd);
        }

        return result;
    }


}
