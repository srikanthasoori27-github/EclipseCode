/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Encapsulates information required to send an email.
 *
 * Author: Rob, Jeff
 *
 */

package sailpoint.object;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.tools.EmailUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MessageRenderer;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class containing information required to send an email.
 * Templates are shared objects, they are typically combined
 * with a <code>EmailOptions</code> object at runtime.
 */
@XMLClass
public class EmailTemplate extends SailPointObject implements Cloneable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    public static final String HOST_PROPERTY = "mail.smtp.host";
    public static final String PORT_PROPERTY = "mail.smtp.port";

    /**
     * The from address template.
     */
    private String _from;

    /**
     * The subject template.
     */
    private String _subject;

    /**
     * The body template.
     */
    private String _body;

    private String _to;
    private String _cc;
    private String _bcc;

    /**
     * @exclude
     * Target SMTP host. In practice this is never used and isn't even
     * mapped in the Hibernate file.  Can we get rid of it? - jsl
     */
    String _host;

    /**
     * Properties for the javax.mail.Session. 
     * mail.smtp.host among other things
     */
    private Map<String,String> _sessionProperties;

    /**
     * Signature formally defining the input variables that can
     * be referenced by this template.
     */
    Signature _signature;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public EmailTemplate()
    {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The from address template.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getFrom()
    {
        return _from;
    }

    public void setFrom(String from)
    {
        _from = from;
    }

    /**
     * The subject template.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getSubject()
    {
        return _subject;
    }

    public void setSubject(String subject)
    {
        _subject = subject;
    }

    /**
     * The body template.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    @XMLProperty(xmlname="SessionProperties")
    public Map<String,String> getSessionProperties()
    {
        return _sessionProperties;
    }

    /**
     * Set the javax.mail.Session properties
     * @param props Map used to populate the javax.mail.Session properties 
     * containing the information specified in Appendix A of the JavaMail 
     * spec 
     *
     * @ignore
     * This @see is going to generate a warning unless we include javax.mail
     * in our own docs.  I think there is a way to link to other javadoc
     * libraries but there are issues...
     * @see javax.mail.Session#getInstance(java.util.Properties)
     */
    public void setSessionProperties(Map<String,String> props)
    {
        _sessionProperties = props;
    }

    @XMLProperty
    public String getTo() {
        return _to;
    }

    public void setTo(String _to) {
        this._to = _to;
    }
    
    @XMLProperty
    public String getCc() {
        return _cc;
    }

    public void setCc(String cc) {
        _cc = cc;
    }
    
    @XMLProperty
    public String getBcc() {
        return _bcc;
    }

    public void setBcc(String bcc) {
        _bcc = bcc;
    }

    /**
     * @exclude
     * Target SMTP host.  In practice this is never used and isn't even
     * mapped in the Hibernate file.  Can we get rid of it? - jsl
     */
    @XMLProperty
    public String getHost() {
        return _host;
    }

    /**
     * @exclude
     */
    public void setHost(String _host) {
        this._host = _host;
    }

    /**
     * Signature formally defining the input variables that can
     * be referenced by this template.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signature getSignature() {
        return _signature;
    }

    public void setSignature(Signature s) {
        _signature = s;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Compile this template by combining it with the EmailOptions.
     * Returns a new EmailTemplate containing the concrete information
     * to generate the notification.
     */
    public EmailTemplate compile(Resolver resolver, Configuration sysconfig,
                                 EmailOptions options)
        throws GeneralException {
        
        // note that we use deepCopy instead of clone because we need to
        // modify the sessionProperties map
        EmailTemplate template = (EmailTemplate)this.deepCopy(resolver);
        Map<String, String> props = template.getSessionProperties();

        // host usually comes from System Configuration, but may be overridden
        // UPDATE: This is no longer recognized, SMTPEmailNotifier always
        // gets a fresh host from the syscofig so it can retry
        // invalid host names that were fixed in sysconfig.
        if (options != null)
            template._host = options.getString(HOST_PROPERTY);

        if (template._host == null && props != null)
            template._host = (String)props.get(HOST_PROPERTY);

        if (template._host == null && sysconfig != null)
            template._host = sysconfig.getString(Configuration.DEFAULT_EMAIL_HOST);

        if ((template._from == null || template._from.length() == 0) && sysconfig != null)
            template._from = sysconfig.getString(Configuration.DEFAULT_EMAIL_FROM);
        
        //Append cc and to values from EmailOptions to original template values
        if (options!=null) { 
            String to = options.getTo();
            if (Util.isNotNullOrEmpty(to)){
                template._to = to;
            }
            String cc = options.getCc();
            if (Util.isNotNullOrEmpty(cc)){
                template._cc = cc;
            }

            Map<String,Object> vars = options.getVariables();
            if (vars != null) {
                template._from    = Util.getString(MessageRenderer.render(template._from, vars));
                template._subject = Util.getString(MessageRenderer.render(template._subject, vars));
                // Only escape html if body is html
                template._body    = Util.getString(MessageRenderer.render(template._body, vars, Locale.getDefault(),
                        TimeZone.getDefault(), EmailUtil.isHtml(template.getBody())));
                template._cc      = Util.getString(MessageRenderer.render(template._cc, vars));
                template._bcc     = Util.getString(MessageRenderer.render(template._bcc, vars));
            }
        }

        return template;
    }

    /**
     * Fully load the template so we can clear the cache.
     */
    public void load() {

        if (_sessionProperties != null) {
            _sessionProperties.get("foo");
        }
    }
    
    public Object clone() {
        Object buddy =null;
        try { 
            buddy = super.clone();
        } catch (CloneNotSupportedException cnfe) {
            //We are swallowing this...don't need to worry since we implement cloneable.
        }
        return buddy;
    }
}
