/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class used to maintain a persistent version number for the
 * object model and associated Hibernate schema.  This is used during
 * system startup to verify that a database we're about to connect to
 * has a schema that is compatible with Java classes the system was
 * built with.
 *
 * The underlying table must be extremely simple because we
 * will be examining the table using ordinary JDBC before we attempt
 * to bring up Hibernate.  We don't need all of the crap in SailPointObject
 * but that's the only way to have objects that are accessible through
 * SailPointContext.
 *
 * Author: Jeff
 *
 */
package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class used to maintain a persistent version number for the
 * object model and associated Hibernate schema. This is used during
 * system startup to verify that a database that is about to be connected to
 * has a schema that is compatible with Java classes the system was
 * built with.
 */
@XMLClass
public class DatabaseVersion extends SailPointObject implements Cloneable {
    private static final long serialVersionUID = -8996940727889216993L;
    
    /*
     * Updating SCHEMA_VERSION and SYSTEM_VERSION
     * 
     * For schema changes:
     * 1) Increment SYSTEM_VERSION
     * 2) Increment SCHEMA_VERSION
     * 3) Update upgrade DDL to set the new SCHEMA_VERSION
     *   
     * For non-schema changes that require 'iiq upgrade' to be run
     * 1) Increment SYSTEM_VERSION
     */

    /**
     * The schema version identifier. For intra-release versions, prefix single
     * digits with 0. For example 3.1-04. This is necessary because the
     * version checking code uses string comparison to determine if an upgrade
     * needs to be run and 3.1-10 is less than 3.1-4 (should be 3.1-04).
     */
    public static final String SCHEMA_VERSION = "8.2-07";

    /**
     * The system version identifier.
     * Note: Has the same value requirements as SCHEMA_VERSION above.
     */
    public static final String SYSTEM_VERSION = "8.2-44";

    /**
     * The major system version required by the "iiq upgrade" command.
     * The value would be "5.1" for version "5.2" (and all patches), which means that upgrade
     * cannot be run unless the major version in the database is "5.1".
     */
    public static final String REQUIRED_UPGRADE_MAJOR_VERSION = "8.1";

    /**
     * The name of the DatabaseVersion object where
     * the database version is stored.  There really should only be one of these
     * but its easier to fetch if a unique name can be relied upon.
     * The generated uuid does not work because there is no way to know that
     * at compile time.
     */
    public static final String OBJ_NAME = "main";

    /**
     * The persisted database version identifier.
     * Making this a string rather than a number to allow
     * non-numeric qualifiers like "-beta4" or "-hotfix29" etc.
     * Typically it will look like "1.5" and "2.0".
     */
    String _systemVersion;
    
    String _schemaVersion;

    public DatabaseVersion () {
    }

    @XMLProperty
    public void setSystemVersion(String s) {
        _systemVersion = s;
    }

    public String getSystemVersion() {
        return _systemVersion;
    }


    public String getSchemaVersion() {
        return _schemaVersion;
    }
    
    @XMLProperty
    public void setSchemaVersion(String schemaVersion) {
        _schemaVersion = schemaVersion;
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    /**
     * Return the static system schema version.
     * This is used by VersionChecker instead of the string constant.
     * When VersionChecker is compiled, the constant is copied. Changing
     * the constant value later will have no effect on VersionChecker's
     * value. This can be annoying during development if you bump
     * the version but it does not seem to have any effect until you
     * touch VersionChecker to force it to be recompiled. By calling
     * this method, the correct value is always found.
     */
    public static String getSchemaVersionConstant() {
        return SCHEMA_VERSION;
    }
    
    /**
     * Gets the SYSTEM_VERSION constant.
     * @see #getSchemaVersionConstant()
     */
    public static String getSystemVersionConstant() {
        return SYSTEM_VERSION;
    }
    
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("name", "Name");
        cols.put("version", "Version");
        return cols;
    }

}
