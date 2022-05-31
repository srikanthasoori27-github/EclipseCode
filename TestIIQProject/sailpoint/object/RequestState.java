package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;

/**
 * Holds any large data needed to restore a Request to
 * its state upon a restart of the Request.
 *
 * This object is not directly referenced by the Request
 * in order to isolate the Request from the occassional
 * large data being persisted to its RequestState.
 *
 */
@XMLClass
public class RequestState extends SailPointObject
        implements Cloneable, Serializable
{

    /**
     * Attribute (map) that contains information useful during a restart of the
     * request.  Each request type can populate the fields in this map as necessary.
     *
     * For example, for partitioned aggregation, attibutes in this map
     * contains a list of native identities that were
     * already completed, so that they can be skipped for processing
     * upon a later restart of the Request.
     */
    public static final String ATT_RESTART_INFO = "restartInfo";


    /**
     * The Request for which this restart data is relevant
     */
    Request _request;

    /**
     * A map holding the data needed to restore a Request upon a restart
     */
    Attributes<String,Object> _attributes;


    ///////////////////////////////////////////
    // getters/setters
    ///////////////////////////////////////////

    public RequestState() {
    }

    public RequestState(Request req) {
        _request = req;
        _name = req.getName();
    }

    @XMLProperty(mode= SerializationMode.REFERENCE, xmlname = "RequestRef")
    public Request getRequest() {
        return _request;
    }

    public void setRequest(Request request) { _request = request; }


    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> a) {
        _attributes = a;
    }


    ///////////////////////////////////
    // attribute helpers
    ///////////////////////////////////

    // most things support map-style accessors too
    public Object get(String name) {
        return getAttribute(name);
    }

    public void put(String name, Object value) {
        setAttribute(name, value);
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void setAttribute(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, value);
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    /**
     * Convenience setter for tasks that keep lots of numeric
     * statistics. Convert to the int to a String but only store
     * it if it is non-zero to avoid cluttering up the result.
     */
    public void setInt(String name, int value) {
        if (value != 0)
            setAttribute(name, Util.itoa(value));
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @Override
    public boolean isNameUnique() { return false; }
}
