package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;

/**
 * Created by ryan.pickens on 9/11/15.
 */
@XMLClass
public class TargetHostConfig extends AbstractXmlObject implements Cloneable {

    //Name of the TargetHost
    String _hostName;

    //Native id of the TargetHost
    String _hostId;

    boolean _pathCaseSensitive;

    //List of paths for the given targetHost
    List<String> _paths;

    @XMLProperty
    public String getHostName() {
        return _hostName;
    }

    public void setHostName(String host) {
        _hostName = host;
    }

    @XMLProperty(mode= SerializationMode.LIST)
    public List<String> getPaths() {
        return _paths;
    }

    public void setPaths(List<String> paths) {
        _paths = paths;
    }

    @XMLProperty
    public String getHostId() {
        return _hostId;
    }

    public void setHostId(String id) {
        _hostId = id;
    }

    @XMLProperty
    public boolean isPathCaseSensitive() {
        return _pathCaseSensitive;
    }

    public void setPathCaseSensitive(boolean b) {
        _pathCaseSensitive = b;
    }
}
