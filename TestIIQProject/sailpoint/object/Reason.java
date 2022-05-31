/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Simple XML object used to store a message key and parameters used
 * when rendering a Recommendation. The Reason object is a localizable
 * representation of the Reasons a decision was made in a Recommendation.
 */
@XMLClass(xmlname="ReasonMessage")
public class Reason extends AbstractXmlObject implements MessageKeyHolder {
    
    private String messageKey;
    private List<Object> parameters;
    
    public Reason() { /* no arg constructor for flexjson */ }
    
    public Reason(String key) {
        this(key, null);
    }
    
    public Reason(String key, List<Object> params) {
        this.messageKey = key;
        this.parameters = params;
    }
    
    /* (non-Javadoc)
     * @see sailpoint.tools.MessageKeyHolder#getMessageKey()
     */
    @Override
    @XMLProperty
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<Object> getParameters() {
        return parameters;
    }

    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }
    
}
