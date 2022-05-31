/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.classification;

import sailpoint.object.Attributes;
import sailpoint.object.Classification;

import java.util.List;

public class ClassificationResult {


    Attributes _attributes;

    // ID of the SailPointObject in which this correlates
    String _objectId;

    // Type of SailPointObject in which this correlates. Class.simpleName
    Class _objectType;

    //List of Classification Objects
    List<Classification> _classifications;

    //Object returned from native source used to create Classification Result
    public static String ATT_RESPONSE = "object";


    public String getObjectId() {
        return _objectId;
    }

    public void setObjectId(String objectId) {
        _objectId = objectId;
    }

    public Class getObjectType() {
        return _objectType;
    }

    public void setObjectType(Class objectType) {
        _objectType = objectType;
    }

    public List<Classification> getClassifications() {
        return _classifications;
    }

    public void setClassifications(List<Classification> classifications) {
        _classifications = classifications;
    }

    public void setAttribute(String key, Object value) {
        if (this._attributes == null) {
            this._attributes = new Attributes();
        }

        _attributes.put(key, value);
    }

    public Object getAttribute(String key) {

        if (_attributes != null) {
            return _attributes.get(key);
        } else {
            return null;
        }
    }
}
