package sailpoint.rapidonboarding.web;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.DatabaseVersion;
import sailpoint.rapidonboarding.Version;
import sailpoint.tools.Brand;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;

public class DebugBean extends BaseBean {

    
    private static Log _log = LogFactory.getLog(DebugBean.class);

    /**
     * Return the current time and date in full format.
     *
     * @return a full format current time and date
     */
    public String getCurrentDate() {
        return DateFormat.
                        getDateTimeInstance(DateFormat.FULL, DateFormat.FULL).
                        format(new Date());
    }

    /**
     * Return the complete version of the product including patch level and
     * revision.
     *
     * @return the complete version of the product
     */
    public String getFullVersion() {
        return Version.getFullVersion();
    }

    /**
     * Return a list of efixes installed - basically all files ending in .txt in the WEB-INF/efixes dir
     *
     * @return list of efixes installed
     */
    public String geteFixes() {
    StringBuilder sb = new StringBuilder();
        final Pattern STRIP_BAD_CHARS = Pattern.compile("[^a-zA-Z0-9.-]+");

        try {
            String appName;
            BrandingService bs = BrandingServiceFactory.getService();
            if (bs.getBrand() == Brand.AGS) {
                appName = bs.getApplicationShortName().toLowerCase();
            } else {
                appName = bs.getApplicationName().toLowerCase();
            }
            String efixFile = getFacesContext().getExternalContext().getRealPath("WEB-INF/efixes");
            if (efixFile != null) {
                File efixDir = new File(efixFile);
                if (efixDir != null && efixDir.isDirectory()) {
                    File[] efixFiles = efixDir.listFiles();
                    for (File efix:efixFiles) {
                        String efixName = STRIP_BAD_CHARS.matcher(efix.getName()).replaceAll("");
                        if (efix.isFile() && efixName.toLowerCase().startsWith(appName + "-") && efixName.toLowerCase().endsWith(".txt")) {
                            efixName = efixName.substring(0, efixName.lastIndexOf("."));
                            if (! efixName.isEmpty()) {
                                sb.append(efixName);
                                sb.append("<br />");
                            }
                        }
                    }
                }
            }
            if (sb.length() > 0) {
                return(sb.toString());
            }
        } catch (Exception e) {
            _log.warn("Error getting efix list", e);
        }
        return "None";
    }

    public String getSchemaVersion() {
        String version = null;
        try {
            DatabaseVersion dbv = getContext().getObjectByName(DatabaseVersion.class, "main");
            if (dbv != null)
                version = dbv.getSchemaVersion();
        }
        catch (GeneralException e) {}
        return version;
    }

    /**
     * Return the compiled-in repository location of the product.
     *
     * @return the repository location of the product
     */
    public String getRepoLocation() {
        return Version.getRepoLocation();
    }

    /**
     * Return the compiled-in patch level of the product.
     *
     * @return the patch level of the product
     */
    public String getPatchLevel() {
        return Version.getPatchLevel();
    }

    /**
     * Return the compiled-in source revision of the product.
     *
     * @return the source revision of the product
     */
    public String getRevision() {
        return Version.getRevision();
    }

    /**
     * Return the compiled-in user that built the product.
     *
     * @return the builder of the product
     */
    public String getBuilder() {
        return Version.getBuilder();
    }

    /**
     * Return the compiled-in build date of the product.
     *
     * @return the build date of the product
     */
    public String getBuildDate() {
        return Version.getBuildDate();
    }

    /**
     * Returns the application installation directory.
     */
    public String getApplicationHome() {
        String appHome = "";
        try {
            appHome = Util.getApplicationHome();
        } catch (GeneralException ex) { }

        return appHome;
    }

    public boolean isAcceleratorPackDeployed() {
        return Configuration.getSystemConfig().containsKey(Configuration.ACCELERATOR_PACK_VERSION);
    }

    /**
     * Return the Accelerator Pack Version
     *
     * @return version as string
     */
    public String getAcceleratorPackVersion() {
        String version = Configuration.getSystemConfig().getString(Configuration.ACCELERATOR_PACK_VERSION);
        return (version == null) ? "" : version;
    }


}
