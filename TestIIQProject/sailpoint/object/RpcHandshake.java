/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @exclude
 */
@XMLClass
public class RpcHandshake extends AbstractXmlObject {
    
    private static final long serialVersionUID = 1L;
    
    String _version;
    String _phrase;
    String _guid1;
    String _guid2;
    String _guid3;
    String _guid4;

    public RpcHandshake() {
        _version = "1.1";
        _phrase = "hOwDyTheRE";
        _guid1 = Util.uuid();
        _guid2 = Util.uuid();
        _guid3 = Util.uuid();
        _guid4 = Util.uuid();
    }

    @XMLProperty
    public String getPhrase() {
        return _phrase;
    }

    public void setPhrase(String phrase) {
        _phrase = phrase;
    }

    @XMLProperty
    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        _version = version;
    }

    @XMLProperty
    public String getGuid1() {
        return _guid1;
    }

    public void setGuid1(String guid) {
        _guid1 = guid;
    }

    @XMLProperty
    public String getGuid2() {
        return _guid2;
    }

    public void setGuid2(String guid) {
        _guid2 = guid;
    }

    @XMLProperty
    public String getGuid3() {
        return _guid3;
    }

    public void setGuid3(String guid) {
        _guid3 = guid;
    }

    @XMLProperty
    public String getGuid4() {
        return _guid4;
    }

    public void setGuid4(String guid) {
        _guid4 = guid;
    }

}
