/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Object created by AnnotationSerializer during compilation.
 * Holds the mapping of one Property with its XMLSerializer.
 * 
 * !! See if we can collapse this into settings on the Property.
 *
 * Author: Rob, comments by Jeff
 */

package sailpoint.tools.xml;

class PropertyHandler
{
    private XMLSerializer _serializer;
    private Property _property;

    public PropertyHandler(XMLSerializer serializer, Property property) {
        _serializer = serializer;
        _property   = property;
    }

    public XMLSerializer getSerializer() {
        return _serializer;
    }
        
    public Property getProperty() {
        return _property;
    }
}
