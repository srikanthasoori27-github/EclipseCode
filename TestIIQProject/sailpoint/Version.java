/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint;

import sailpoint.object.Configuration;
import sailpoint.object.RapidSetupConfigUtils;

/**
 * Class providing static methods to obtain product
 * version information.
 */
public class Version {

    /**
     * This class does not need to be instantiated.
     */
    private Version() {
    }

    /**
     * Return the version of the product.
     */
    public static String getVersion() {
        return VersionConstants.VERSION;
    }
    
    /**
     * Returns whether this product is configured to run as Lifecycle Manager
     */
    public static boolean isLCMEnabled() {
        boolean lcmEnabled = false;

        Configuration syscon = Configuration.getSystemConfig(); 
        if ( syscon != null )
            lcmEnabled = syscon.getBoolean(Configuration.LCM_ENABLED);

        return lcmEnabled;
    }

    /**
     * Returns whether privileged account management is enabled in the system config
     * @return if PAM module is enabled
     */
    public static boolean isPAMEnabled() {
        boolean pamEnabled = false;

        Configuration syscon = Configuration.getSystemConfig();
        if ( syscon != null )
            pamEnabled = syscon.getBoolean(Configuration.PAM_ENABLED);

        return pamEnabled;
    }

    /**
     * Returns whether Identity AI is enabled in the system config
     * @return if IAI module is enabled
     */
    public static boolean isIdentityAIEnabled() {
        boolean identityAIEnabled = false;

        Configuration syscon = Configuration.getSystemConfig();
        if ( syscon != null )
            identityAIEnabled = syscon.getBoolean(Configuration.IDENTITYAI_ENABLED);

        return identityAIEnabled;
    }
    
    /**
     * Returns whether File Access Manager is enabled in the system config
     * @return if FAM module is enabled
     */
    public static boolean isFAMEnabled() {
        boolean famEnabled = false;
        
        Configuration syscon = Configuration.getSystemConfig();
        if ( syscon != null )
            famEnabled = syscon.getBoolean(Configuration.FAM_ENABLED);
        
        return famEnabled;
    }

    /**
     * Returns whether Rapid Setup is enabled in the configuration
     * @return if Rapid Setup module is enabled
     */
    public static boolean isRapidSetupEnabled() {
        return RapidSetupConfigUtils.isEnabled();
    }

    /**
     * Return the patch level of the product.
     */
    public static String getPatchLevel() {
        String patchLevel = VersionConstants.PATCH_LEVEL;
        if ( patchLevel == null || patchLevel.length() == 0 ||
                                        patchLevel.equals("${patchLevel}") ) {
            patchLevel = "";
        }
        return patchLevel;
    }
    
    /**
     * Return the full version of the product including the patch level if
     * it exists.
     */
    public static String getFullVersion() {
        String fullVersion = Version.getVersion();

        String patchLevel = Version.getPatchLevel();
        if ( patchLevel != null && patchLevel.length() > 0 )
            fullVersion += patchLevel;

        String revision = Version.getRevision();
        if ( revision != null && revision.length() > 0 ) {
            fullVersion += " " + revision;
        }

        return fullVersion;
    }

    /**
     * Return the source code control revision of the product.
     */
    public static String getRevision() {
        return VersionConstants.REVISION;
    }

    /**
     * Return the relative source code control repository location of the
     * product.
     */
    public static String getRepoLocation() {
        return VersionConstants.REPO_LOCATION;
    }

    /**
     * Return the user that built the product.
     */
    public static String getBuilder() {
        return VersionConstants.BUILDER;
    }

    /**
     * Return the build date and time of the product.
     */
    public static String getBuildDate() {
        return VersionConstants.BUILDTIME;
    }

    /**
     * Return host where the product was built.
     */
    public static String getBuildHost() {
        return VersionConstants.BUILDHOST;
    }

}  // class Version
