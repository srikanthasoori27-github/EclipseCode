package sailpoint.rapidonboarding;

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

}
