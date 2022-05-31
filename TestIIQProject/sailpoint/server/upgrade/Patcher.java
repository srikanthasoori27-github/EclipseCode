package sailpoint.server.upgrade;

import java.text.MessageFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Util;


/**
 * @author peter.holcomb
 * @author jeff.upton
 * 
 * The Patcher invokes the functionality of the upgrader for post-release patches.  It
 * takes as an argument, the name of the patch release and utilizes it to bring in the required
 * database and import files for the patch.
 *
 */
public class Patcher
{   
    private static final Log log = LogFactory.getLog(Patcher.class);
    
    /**
     * The entry point into the patching process.
     * @param args The command line arguments.
     */
    public static void main(String[] args) 
    {
        if (args.length == 0) {
            System.err.println("Usage: " + BrandingServiceFactory.getService().getConsoleApp() + " patch <patchName>\n\n    e.g. " + BrandingServiceFactory.getService().getConsoleApp() + " patch 5.2p1");
            return;
        }
        
        try {
            String patch = args[0].toLowerCase();
            args = Util.shiftArray(args);
            
            Upgrader upgrader = new Upgrader();
            upgrader.setPatch(patch);
            String ddlScriptHint = MessageFormat.format("upgrade_identityiq_tables-{0}.*", patch);
            ddlScriptHint = BrandingServiceFactory.getService().brandFilename( ddlScriptHint );
            upgrader.setDdlScriptHint( ddlScriptHint );
            upgrader.execute(args);
        } catch (Throwable t) {
            if (log.isErrorEnabled())
                log.error(t.getMessage(), t);
        }
    }
}
