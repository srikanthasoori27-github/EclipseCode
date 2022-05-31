/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Map;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @exclude
 * 
 * An object that encapsulates the payload sent from the 
 * IIQ server and the remote IQService.
 * 
 * The main payload is a map of xml serializable keys
 * and values that will be sent over to the wire.
 * 
 * Each request specifies the service and methid
 * that should be executed.
 * 
 * 
 * @author Dan.Smith@sailpoint.com
 * 
 *
 */
@XMLClass
public class RpcRequest extends AbstractXmlObject {

    private static final long serialVersionUID = 1L;
    
    private String _version;    
    private String _method;
    private String _service;
    private Map<String,Object> _arguments;
    private String _requestId;

    public RpcRequest () {
        _version = "1.0";
        _method = null;
        _service = null;
        _requestId = null;
    }

    public RpcRequest(String service, String method, Map<String,Object> args) {
        this();
        setService(service);
        setMethod(method);
        setArguments(args);
    }

    @XMLProperty
    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        _version = version;
    }

    @XMLProperty
    public String getMethod() {
        return _method;
    }

    public void setMethod(String method) {
        _method = method;
    }

    @XMLProperty
    public String getService() {
        return _service;
    }

    public void setService(String service) {
        _service = service;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public Map<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Map<String,Object> arguments) {
        _arguments = arguments;
    }

    @XMLProperty
    public String getRequestId() {
        return _requestId;
    }

    public void setRequestId(String requestId) {
        _requestId = requestId;
    }
}
