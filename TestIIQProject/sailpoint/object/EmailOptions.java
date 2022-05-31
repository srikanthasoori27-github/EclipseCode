/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Set of inputs to an email template.
 *
 * @ignore
 * Originally this was used by the EmailNotifiers to do the rendering
 * but now rendering is done at a higher level (in InternalContext).
 * EmailNotifiers will still get one of these but the only thing
 * they should use is the list of attachments.
 */
@XMLClass
public class EmailOptions implements Cloneable, Serializable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Who the email is being sent to. Overrides what is in the EmailTemplate
     * if both are set.
     */
    private String _to;
    
    private String _cc;

    /**
     * Inputs to the email template rendering process.
     * For simple rendering this will just be a map of strings.
     * For Velocity rendering this can contain complex objects.
     */
    private Map<String,Object> _variables;

    /**
     * List of binary file attachments 
     */
    private List<EmailFileAttachment> _attachments;
    
    /**
     * If set, the email should be appended to this file instead of sent
     * via SMTP.
     */
    private String _fileName;

    /**
     * Transient flag to prevent the generation of a retry request 
     * if the email could not be sent.
     * This is intended for use only by the internal notification
     * retry system.
     */
    boolean _noRetry;

    /**
     * Transient flag to tell the notifier that it should try
     * to first send the message and queue it only for retries,
     * if enabled. By default this setting is false and all
     * email will be sent through the request processor.
     */
    boolean _sendImmediate;
    
    /**
     * This is similar to sendImmediate, but stronger.  Unlike sendImmediate,
     * this setting cannot be overridden by the existing system configuration.
     * This prevents services like the EmailRequestExecutor from getting stuck in
     * an infinite cycle of processing and requeuing.  
     */
    boolean _forceImmediate;
    

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public EmailOptions() {
        _attachments = new ArrayList<EmailFileAttachment>();
        _sendImmediate = false;
    }

    public EmailOptions(String to, Map<String,Object> variables) {
        this();
        _to = to;
        _variables = variables;
    }

    public EmailOptions(List<String> toAddresses, Map<String,Object> variables) {
        this();
        _variables = variables;
        if ( toAddresses != null) {
            setTo(toAddresses);
        }
    }

    public void setTo(List<String> toAddresses) {
        if ( Util.size(toAddresses) > 0 ) {
            //IIQTC-269 :- Do not surround by double-quotes the list of recipients.
            //we are assuming that each element is properly formatted CSV and we'll
            //treat each element as a List of CSV
            _to = Util.join(toAddresses, ",");
        }
    }

    /**
     * Who the email is being sent to. Overrides what is in the EmailTemplate
     * if both are set.
     */
    @XMLProperty
    public void setTo(String to) {
        _to = to;
    }
    

    public String getTo()
    {
        return _to;
    }
    
    public void setCc(String cc){
        _cc = cc;
    }
    
    public String getCc(){
        return _cc;
    }

    @XMLProperty
    public void setVariables(Map<String,Object> vars) {
        _variables = vars;
    }

    /**
     * Inputs to the email template rendering process.
     * For simple rendering this will just be a map of strings.
     * For Velocity rendering this may contain complex objects.
     */
    public Map<String,Object> getVariables()
    {
        return _variables;
    }

    @XMLProperty
    public void setAttachments(List<EmailFileAttachment> attachments) 
    {
        _attachments = attachments;
    }

    /**
     * List of binary file attachments 
     */
    public List<EmailFileAttachment> getAttachments() 
    {
        return _attachments;
    }
    
    /**
     * @exclude
     */
    public void setNoRetry(boolean b) {
        _noRetry = b;
    }

    /**
     * @exclude
     */
    public boolean isNoRetry() {
        return _noRetry;
    }

    /**
     * @exclude
     */
    public void setSendImmediate(boolean b) {
        _sendImmediate = b;
    }

    /**
     * @exclude
     */
    public boolean isSendImmediate() {
        return _sendImmediate;
    }

    /**
     * @exclude
     */
    public void setForceImmediate(boolean b) {
        _forceImmediate = b;
    }

    /**
     * @exclude
     */
    public boolean isForceImmediate() {
        return _forceImmediate;
    }

    /**
     * @exclude
     * older name that doesn't follow bean conventions
     */
    public boolean sendImmediate() {
        return _sendImmediate; 
    }

    //////////////////////////////////////////////////////////////////////
    //  
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void addAttachment(EmailFileAttachment attachment) 
    {
        if (_attachments == null)
            _attachments = new ArrayList<EmailFileAttachment>();
        _attachments.add(attachment);
    }

    public void addVariables(Map<String, Object> newVariables) {
        if (_variables == null)
            _variables = newVariables;
        else
            _variables.putAll(newVariables);
    }
    
    /**
     * If set, the email should be appended to this file instead of sent
     * via SMTP.
     */
    public String getFileName() {
        return _fileName;
    }
    
    public void setFileName(String fileName) {
        _fileName = fileName;
    }

    public void setVariable(String name, Object value) {
        if (_variables == null)
            _variables = new HashMap<String,Object>();
        _variables.put(name, value);
    }

    public Object getVariable(String name) {
        return (_variables != null) ? _variables.get(name) : null;
    }

    public String getString(String name) {
        String str = null;
        if (_variables != null) {
            Object o = _variables.get(name);
            if (o != null)
                str = o.toString();
        }
        return str;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
