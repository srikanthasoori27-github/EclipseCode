/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object intended for use only in XML import files.
 * These are recognized by the Importer and trigger special
 * operations during the import.
 * Author: Jeff
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object intended for use only in XML import files.
 * These are recognized by the Importer and trigger special
 * operations during the import.
 */
@XMLClass
public class ImportAction extends AbstractXmlObject {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Defined actions
    //
    //////////////////////////////////////////////////////////////////////

    public static final String MERGE = "merge";

    public static final String MERGE_IF_NULL = "mergeIfNull";
    public static final String INCLUDE = "include";
    public static final String EXECUTE = "execute";
    public static final String LOG_CONFIG = "logConfig";
    public static final String INSTALL_PLUGIN = "installPlugin";
    
    /**
     * Command that will create/or merge application templates into
     * the connectorRegistry.
     */
    public static final String MERGE_CONNECTOR_REGISTRY = "mergeConnectorRegistry";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the action.
     */
    String _name;

    /**
     * An optional argument for the action, can be any XML
     * object. Importer is expected to know how to deal.
     */
    AbstractXmlObject _argument;

    /**
     * An optional <Attributes> map for the action, useful to
     * pass in additional execution options.  The
     * ImportActionCommand is expected to know how to interpret.
     */
    Attributes _attributes;

    /**
     * String value holder for import commands that only have
     * one simple string value.
     */
    String _value;
    
    /**
     * The database system version at which the import should occur.
     * This is used for upgrades where certain activities only need
     * to happen once.
     */
    String _systemVersion;
    
    String _group;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public ImportAction() {
    }

    public ImportAction(String name) {
        _name = name;
    }

    @XMLProperty
    public void setName(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setArgument(AbstractXmlObject arg) {
        _argument = arg;
    }

    public AbstractXmlObject getArgument() {
        return _argument;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setAttributes(Attributes attrs) {
        _attributes = attrs;
    }

    public Attributes getAttributes() {
        return _attributes;
    }

    public String getValue() {
        return _value;
    }

    @XMLProperty
    public void setValue(String value) {
        _value = value;
    }

    public String getRevision() {
        return _systemVersion;
    }

    @XMLProperty(legacy=true)
    public void setRevision(String revision) {
        _systemVersion = revision;
    }
    
    public String getSystemVersion() {
    	return _systemVersion;
    }
    
    @XMLProperty
    public void setSystemVersion(String systemVersion) {
    	_systemVersion = systemVersion;
    }
    
    public String getGroup() {
    	return _group;
    }
    
    @XMLProperty
    public void setGroup(String group) {
    	_group = group;
    }
}
