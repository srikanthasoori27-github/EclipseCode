/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@XMLClass
public class WindowsShare implements Cloneable {

    /**
     * Path to the share. \\server\sharename\
     */
    private String _path;

    /**
     * Wildcard of the paths to include. *.xls, *.pdf, etc..
     */
    private String _filter;

    /**
     * How many directories deep to scan, -1 == subtree 0 = onelevel 
     */
    private int _depth;

    /**
     * Account to authenticate as when accessing the file share.
     */
    private String _user;

    /**
     * Password for user user when accessing the file share.
     */
    private String _password;

    /**
     * Flag to indicate that only directory targets should be returned.
     */
    private boolean _directoriesOnly;

    /**
     * Flag to indicate that explicitPermissions should be returned.
     */
    private boolean _explicitPermissions;

    /**
     * Flag to indicate that inherited Permissions should be returned.
     */
    private boolean _inheritedPermissions;

    public WindowsShare() {
        _depth = Integer.MAX_VALUE;
        _path = null;
        _filter = null;
        _user = null;
        _password = null;
    }

    @XMLProperty
    public String getPath() {
        return _path;
    }

    public void setPath(String path) {
        _path = path;
    }

    @XMLProperty
    public String getFilter() {
        return _filter;
    }

    public void setFilter(String filter) {
        _filter = filter;
    }

    @XMLProperty
    public int getDepth() {
        return _depth;
    }

    public void setDepth(int depth) {
        _depth = depth;
    }

    @XMLProperty
    public String getUser() {
        return _user;
    }

    public void setUser(String user ) {
        _user = user;
    }

    @XMLProperty
    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
    }

    @XMLProperty
    public boolean isDirectoriesOnly() {
        return _directoriesOnly;
    }

    public void setDirectoriesOnly(boolean dirsOnly) {
        _directoriesOnly = dirsOnly;
    }

    @XMLProperty
    public boolean getIncludeInheritedPermissions() {
        return _inheritedPermissions;
    }

    public void setIncludeInheritedPermissions(boolean inherited) {
        _inheritedPermissions = inherited;
    }

    @XMLProperty
    public boolean getIncludeExplicitPermissions() {
        return _explicitPermissions;
    }

    public void setIncludeExplicitPermissions(boolean explicit) {
        _explicitPermissions = explicit;
    }

    public Object clone() {
        Object buddy =null;
        try {
            buddy = super.clone();
        } catch (CloneNotSupportedException cnfe) { }
        return buddy;
    }
}
