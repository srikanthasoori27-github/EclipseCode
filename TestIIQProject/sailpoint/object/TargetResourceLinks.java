/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;
import java.util.Map;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @exclude
 */
@XMLClass
public class TargetResourceLinks extends AbstractXmlObject {

    private static final long serialVersionUID = 1L;
    
    private String _version;    
    private Map<String,Object> _attributes;
    private List<String> _errors;
    private List<String> _messages;
    private Boolean _complete;
    private String _requestId;

    public TargetResourceLinks () {
        _version = "1.0";
        //_requestId = null;
    }

    public TargetResourceLinks(String service, Map<String,Object> results) {
        this();
        setResultAttributes(results);
    }

    @XMLProperty
    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        _version = version;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public Map<String,Object> getResultAttributes() {
        return _attributes;
    }

    public void setResultAttributes(Map<String,Object> resultMap) {
        _attributes = resultMap;
    }

    @XMLProperty(mode=SerializationMode.CANONICAL,xmlname="RpcErrors")
    public List<String> getErrors() {
        return _errors;
    }

    public void setErrors(List<String> errors) {
        _errors = errors;
    }

    public boolean hasErrors() {
        return ( ( _errors != null ) && ( _errors.size() > 0 ) ) ? true : false;
    }

    @XMLProperty(mode=SerializationMode.CANONICAL,xmlname="RpcMessages")
    public List<String> getMessages() {
        return _messages;
    }

    public void setMessages(List<String> msgs) {
        _messages = msgs;
    }

    @XMLProperty
    public String getComplete() {
        return ( _complete != null ) ? _complete.toString() : "false";
    }

    public Boolean isComplete() {
        return _complete;
    }

    public void setComplete(String complete) {
         _complete = Boolean.parseBoolean(complete);
    }

    @XMLProperty
    public String getRequestId() {
        return _requestId;
    }

    public void setRequestId(String requestId) {
        _requestId = requestId;
    }
}
