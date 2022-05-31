/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class used to maintain an "archived version" of an object.
 * Designed originally for role versioning, might have other uses.
 *
 * Author: Jeff
 *
 * This is conceptually similar to the old CertificationArchive class
 * but general.  Could consider refactoring CertificationArchive someday...
 *
 */

package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/** 
 * A base class used to maintain an "archived version" of an object.
 *
 * An archive is simply a copy of an object saved as XML with some
 * extra metadata.  The object must be saved as XML so references
 * to other objects are "soft" and do not cause the creation of
 * foreign key constraints that prevent the referenced objects from 
 * being deleted.  
 *
 * When an archive is "rehydrated" references to objects that no
 * longer exist are pruned.
 *
 * This is intended to be subclassed to provide a different table for
 * each versioned class.
 *
 * <code>SailPointObject.id</code> will be the unique id for this archive.
 *
 * <code>SailPointObject.name</code> will be the name of the archived 
 * object at the time the archive was created.
 *
 * <code>SailPointObject.created</code> will be the date the archive 
 * was created and  must not be changed.
 */
@XMLClass
public class Archive extends SailPointObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Archive.class);

    // 
    // _id will be the unique id for this archive.
    //
    // _name will be the name of the archived object at the time
    // the archive was created.
    //
    // _created will be the date the archive was created and 
    // must not be changed.
    //

    /**
     * The original object id.
     * This will be the same for all versions of the object.
     */
    String _sourceId;

    /**
     * The name of the Identity that created the version.
     */
    String _creator;

    /**
     * Numeric version number.  Used in cases where the archives
     * are intended to be ordered and presented as numbered versions.
     * Should be able to use the _created date for this, but
     * the number is added so that Hibernate does not need to be upgraded later.
     */
    int _version;

    /**
     * XML blob containing a serialized SailPointObject.
     */
    String _archive;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Archive() {
    }

    /**
     * Let the PersistenceManager know the name is not unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * The original object id.
     * This will be the same for all versions of the object.
     */
    @XMLProperty
    public String getSourceId() {
        return _sourceId;
    }

    public void setSourceId(String s) {
        _sourceId = s;
    }

    /**
     * The name of the Identity that created the version.
     */
    @XMLProperty
    public String getCreator() {
        return _creator;
    }

    public void setCreator(String s) {
        _creator = s;
    }

    /**
     * Numeric version number.  Used in cases where the archives
     * are intended to be ordered and presented as numbered versions.
     */
    @XMLProperty
    public int getVersion() {
        return _version;
    }

    public void setVersion(int i) {
        _version = i;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setArchive(String s) {
        _archive = s;
    }

    /**
     * XML blob containing a serialized SailPointObject.
     */
    public String getArchive() {
        return _archive;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("name", "Name");
        cols.put("version", "Version");
        cols.put("creator", "Creator");
        cols.put("created", "Created");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %-10s %-20s %s\n";
    }

}
