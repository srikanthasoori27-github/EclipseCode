/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Send an email notification using SMTP.
 * Author: Rob, Jeff
 *
 * From Appendix A of the JavaMail spec, the session 
 * properties are:
 *
 * mail.host - required
 * mail.user - name to provide when connecting to the server
 * mail.from - return address
 *
 * mail.store.protocol
 * mail.transport.protocol
 *  Not sure what these are
 * 
 * mail.<protocol>.host
 * mail.<protocol>.user
 *  Protocol specific options
 * 
 * mail.debug
 *   turns on debug mode, whatever that is
 * 
 */
package sailpoint.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EmailNotifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.EmailFileAttachment;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.tools.EmailException;
import sailpoint.tools.EmailUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MboxTransport;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.web.messages.MessageKeys;

import com.sun.mail.smtp.SMTPTransport;


public class SMTPEmailNotifier implements EmailNotifier {

    private static Log log = LogFactory.getLog(SMTPEmailNotifier.class);

    /**
     * Flag to indicate we should give the email to the
     * Notifier immediately when processing email. If this
     * flag is non-null and true the email will not 
     * be queued. Queuing is the default behavior for
     * performance reasons, but for unit-testing its important
     * that the emails are sent out immediately.
     */
    private Boolean _immediate;
    
    private static final String emailEncoding = "UTF-8";

    public SMTPEmailNotifier() {
        _immediate = null;
    }
    
    /**
     * This is a general purpose sendMessage function just for unit test and it
     * delegates to the other functions to send message.
     */
    public void sendMessage(SmtpOptions smtpOptions, Message msg) throws Exception {
        
        Session session = new SmtpSessionHelper(smtpOptions).createSession();
        
        sendViaSmtp(smtpOptions, session, msg);
    }
    
    /**
     * The template has already been fully rendered.  We may
     * modify it if desired.  
     * 
     * Do not modify or use anything out of EmailOptions other than
     * the attachment list.  
     */
    public void sendEmailNotification(SailPointContext context,
                                      EmailTemplate src,
                                      EmailOptions options)
        throws GeneralException, EmailException {

        try {
            
            // if we find this address, ignore it
            if ("noone@example.com".equals(src.getTo())) {
                log.warn("Ignoring email sent to " + src.getTo());
                return;
            }

            Configuration config = context.getConfiguration();

            // this will read sailpoint specific stuff and fill in
            // the smtpOptions class
            SailPointSmtpOptionsHelper helper = new SailPointSmtpOptionsHelper(context, src, config);
            helper.readOptions();
            SmtpOptions smtpOptions = helper.getSmtpOptions();
            
            // use the information obtained to create a session
            Session session = new SmtpSessionHelper(smtpOptions).createSession();

            Message msg = createMessage(src, options, config, session);
            
            String fileName = options.getFileName();
            if ( fileName == null || fileName.length() == 0 ) {
                sendViaSmtp(smtpOptions, session, msg);
            } else {
                MboxTransport t = new MboxTransport(fileName);
                t.sendMessage(msg, msg.getAllRecipients());
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (SendFailedException e) {
            String failedToAddresses = src.getTo();
            // Only retryable if there aren't any invalid email addresses.
            if ((null == e.getInvalidAddresses()) ||
                (0 == e.getInvalidAddresses().length)) {
                throw new RetryableEmailException(src.getTo(), e);
            } else {
                List<String> list = new ArrayList<String>();
                Address[] addresses = e.getInvalidAddresses();
                for ( Address address : addresses ) {
                    list.add(address.toString());
                }
                failedToAddresses = Util.listToCsv(list);
            }
            throw new EmailException(failedToAddresses, e);
        }
        catch (MessagingException e) {

            // This can be indicative of temporary network problems.
            // Any others that can be recovered from?
            if (e.getCause() instanceof UnknownHostException ||
                e.getCause() instanceof SocketException) {
                throw new RetryableEmailException(src.getTo(), e);
            }

            throw new EmailException(src.getTo(), e);
        }
        catch (EmailException e) {
            throw e;
        }
        catch (Exception e) {
            log.error("Error sending email: " + e.toString());
            throw new GeneralException(e);
        }
    }

    private void sendViaSmtp(SmtpOptions smtpOptions, Session session, Message msg)
            throws NoSuchProviderException, MessagingException, SendFailedException {
        
        SMTPTransport transport = (SMTPTransport)session.getTransport("smtp");
        if (smtpOptions.getEncryptionOptions().getEncryptionType() == SmtpEncryptionType.NONE) {
            sendSmtpAuthTypeNoneMessage(smtpOptions, msg, transport);
        } else if (smtpOptions.getEncryptionOptions().getEncryptionType() == SmtpEncryptionType.TLS) {
            sendSmtpAuthTypeTLSMessage(smtpOptions, msg, transport);
        } else if (smtpOptions.getEncryptionOptions().getEncryptionType() == SmtpEncryptionType.SSL) {
            sendSmtpAuthTypeSSLMessage(smtpOptions, msg, transport);
        }
    }

    private void sendSmtpAuthTypeSSLMessage(SmtpOptions smtpOptions, Message msg, SMTPTransport transport)
            throws MessagingException, SendFailedException {
        try {
            transport.connect();
            transport.sendMessage(msg, msg.getAllRecipients());
        }
        finally {
            transport.close();
        }
    }

    private void sendSmtpAuthTypeTLSMessage(SmtpOptions smtpOptions, Message msg, SMTPTransport transport)
            throws MessagingException, SendFailedException {
        try {
            transport.connect(
                    smtpOptions.getHost(), 
                    smtpOptions.getPort(), 
                    smtpOptions.getEncryptionOptions().getUsername(), 
                    smtpOptions.getEncryptionOptions().getPassword());
            transport.sendMessage(msg, msg.getAllRecipients());
        }
        finally {
            transport.close();
        }
    }

    private void sendSmtpAuthTypeNoneMessage(SmtpOptions smtpOptions, Message msg, SMTPTransport transport)
            throws MessagingException, SendFailedException {
        try {
            transport.connect();
            transport.sendMessage(msg, msg.getAllRecipients());
        }
        finally {
            transport.close();
        }
    }

    private Message createMessage(EmailTemplate src, EmailOptions options, Configuration config, Session session)
            throws EmailException, MessagingException, AddressException, UnsupportedEncodingException {
        
        String domain = getDomain(config);

        Message msg = createMessageWithHeader(src, session, domain);

        MimeBodyPart msgBodyPart = createMessageBodyPart(src);

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(msgBodyPart);

        addAttachmentsToMultipart(options, multipart);

        // Put parts in message
        msg.setContent(multipart);
        msg.saveChanges();
        
        return msg;
    }

    private void addAttachmentsToMultipart(EmailOptions options, MimeMultipart multipart)
            throws MessagingException {
        
        // attachments are not handled by EmailSource
        List<EmailFileAttachment> attachmentList = options.getAttachments();
        if ( attachmentList != null ) {
            for ( EmailFileAttachment attachment : attachmentList ) {
                MimeBodyPart fileAttachment = new MimeBodyPart();
                fileAttachment.setFileName(attachment.getFileName());
                DataHandler dh = 
                    new DataHandler(new AttachmentDataSource(attachment));
                fileAttachment.setDataHandler(dh);
                multipart.addBodyPart(fileAttachment);
            }

        }
    }

    private MimeBodyPart createMessageBodyPart(EmailTemplate src)
            throws MessagingException {
        
        MimeBodyPart msgBodyPart = new MimeBodyPart();

        // guess at the mime type by examining the body, 
        // ideally we should have a flag to ask for HTML but
        // we're trying to avoid a schema change
        String body = src.getBody();
        //defect 22001 adding in the encoding for html type emails
        if (EmailUtil.isHtml(body)){
            msgBodyPart.setContent(body, "text/html; charset=" + emailEncoding);
        } else {
            msgBodyPart.setText(body, emailEncoding);
        }
        return msgBodyPart;
    }

    private Message createMessageWithHeader(EmailTemplate src, Session session, String domain)
        throws EmailException, MessagingException, AddressException, UnsupportedEncodingException {

        Message msg = new SailPointMessage(session, domain);
        
        String from = src.getFrom();
        if (from == null || from.length() == 0)
            throw new EmailException("Unable to send email notification, a from address was not specified");

        String to = src.getTo();
        if (to == null || to.length() == 0)
            throw new EmailException("Unable to send email notification, a to address was not specified");

        msg.setFrom(new InternetAddress(from));
        
        msg.setRecipients(Message.RecipientType.TO,
                          InternetAddress.parse(to, false));

        if (null != src.getCc()) {
            msg.setRecipients(Message.RecipientType.CC,
                              InternetAddress.parse(src.getCc(), false));
        }
        if (null != src.getBcc()) {
            msg.setRecipients(Message.RecipientType.BCC,
                              InternetAddress.parse(src.getBcc(), false));
        }

        // Encode email subject with UTF-8 (encodeText doesn't do anything if subject is all US-ASCII)
        String subject = src.getSubject();
        if (!Util.isNullOrEmpty(subject)) {
            msg.setSubject(MimeUtility.encodeText(subject, "UTF-8", "Q"));
        }
        msg.setHeader("X-Mailer", "smptsend");
        msg.setSentDate(new Date());
        
        return msg;
    }

    private String getDomain(Configuration config) {
        
        // need to replace the domain in the Message-ID header,
        // can we just cache this and use it forever?
        String domain = "unknown";
        if (config != null) {
            String from = config.getString(Configuration.DEFAULT_EMAIL_FROM);
            if (from != null) {
                int at = from.indexOf("@");
                if (at >= 0 && at + 1 < from.length())
                    domain = from.substring(at + 1);
            }
        }
        return domain;
    }

    public static class AttachmentDataSource implements DataSource
    {
        private EmailFileAttachment _attachment = null;

        public AttachmentDataSource(EmailFileAttachment attachment) {
            _attachment = attachment;
        }

        public String getContentType() {
            return _attachment.getMimeTypeString();
        }

        public String getName() {
            return _attachment.getFileName(); 
        }

        public InputStream getInputStream() throws IOException {
            byte[] data = _attachment.getData();
            if ( data == null )
                throw new IOException("Attachment data was null.");
            return new ByteArrayInputStream(data);
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("getOutputStream is not implemented.");
        }
    }

    /**
     * Subclass we use instead of MimeMessage to gain control over
     * the Message-ID field in the header.
     * 
     * From the JavaMail API FAQ:
     *
     * Q: I set a particular value for the Message-ID header of my new
     * message, but when I send this message that header is rewritten. 
     *
     * A: A new value for the Message-ID field is set when the saveChanges
     * method is called (usually implicitly when a message is sent), 
     * overwriting any value you set yourself. If you need to set your own
     * Message-ID and have it retained, you will have to create your own
     * MimeMessage subclass, override the updateMessageID method and use
     * an instance of this subclass.
     *
     * A typical message id header looks like this:
     *
     * Message-ID: <4746461.01174598080468.JavaMail.jeff.larson@BlackPearl>
     *
     * It is unclear how standardized this is, so trying to reuse
     * the first part while replacing the "jeff.larson@BlackPerl" part
     * may be unreliable.
     */
    public class SailPointMessage extends MimeMessage {

        String _domain;

        public SailPointMessage(Session session) {
            super(session);
        }

        public SailPointMessage(Session session, String domain) {
            super(session);
            _domain = domain;
        }

        public void updateMessageID() throws MessagingException {

            super.updateMessageID();
            
            //String[] id = getHeader("Message-ID");
            //System.out.println("Message-ID=" + id[0]);

            String domain = _domain;
            if (domain == null)
                domain = "unknown";

            // Original bug report requested that we replace only
            // the sensitive part, but I'm not sure we can do that reliably
            String newid = "<" + Util.uuid() + "@" + domain + ">";

            setHeader("Message-ID", newid);
        }

    }

    public Boolean sendImmediate() {
        return _immediate;
    }

    /**
     * If set to true the email will NOT be queued and instead
     * it will be directly sent to the notifier. This is 
     * typically set by our spring configuration.
     */
    public void setSendImmediate(Boolean immediate) {
        _immediate = immediate;
    }
    
    /**
     * This class helps create a
     * general purpose SmtpOptions class
     * from SailPoint specific information
     * in emailtemplate and system configuration
     *
     */
    private static class SailPointSmtpOptionsHelper {
        
        private SailPointContext context;
        private EmailTemplate template;
        private Configuration config;
        private SmtpOptions smtpOptions;
        
        public SailPointSmtpOptionsHelper(SailPointContext context, EmailTemplate template, Configuration config) {
            this.context = context;
            this.template = template;
            this.config = config;
        }
        
        public void readOptions() throws GeneralException {
            
            createSmtpOptions();
        }

        public SmtpOptions getSmtpOptions() {
            return smtpOptions;
        }
        
        private void createSmtpOptions() throws GeneralException {
            
            smtpOptions = new SmtpOptions();
            
            smtpOptions.setEncryptionOptions(createSmtpEncryptionOptions());
            smtpOptions.setHost(readHost());
            String strPort = readPort();
            int intPort;
            if (Util.isNullOrEmpty(strPort)) {
                 intPort = -1;
            } else {
                intPort = Integer.parseInt(strPort);
            }
            smtpOptions.setPort(intPort);
            
            if (template.getSessionProperties() != null) {
                smtpOptions.setSessionProperties(template.getSessionProperties());
            }
        }
        
        private SmtpOptions.SmtpEncryptionOptions createSmtpEncryptionOptions() throws GeneralException {
            
            SmtpOptions.SmtpEncryptionOptions encryptionOptions = new SmtpOptions.SmtpEncryptionOptions();
            
            SmtpEncryptionType encryptionType = (SmtpEncryptionType) config.get(Configuration.SmtpConfiguration.EncryptionType);
            if (encryptionType == null) {
                encryptionType = SmtpEncryptionType.NONE;
            }
            encryptionOptions.setEncryptionType(encryptionType);
            
            encryptionOptions.setSslSocketFactoryClass(config.getString(Configuration.SmtpConfiguration.SslSocketFactoryClass));
            
            encryptionOptions.setUsername(config.getString(Configuration.SmtpConfiguration.Username));
            encryptionOptions.setPassword(context.decrypt(config.getString(Configuration.SmtpConfiguration.Password)));
            
            return encryptionOptions;
        }

        private String readHost() throws GeneralException {
            
            // bug#3169 wants us to get a fresh host from
            // the sysconfig every time.  
            String host = config.getString(Configuration.DEFAULT_EMAIL_HOST);
            if (host == null) {
                // This is the old behavior, host may be in the template or
                // the sessionProperties map.  Putting it in sessionProperties
                // is an older convention, if they're both set assume 
                // sessionProperties wins.
                if (template.getSessionProperties() != null) {
                    host = template.getSessionProperties().get(EmailTemplate.HOST_PROPERTY);
                }
                if (host == null) {
                    host = template.getHost();
                    if (host == null)
                        throw new GeneralException("No SMTP host specified.");
                }
            }
            
            return host;
        }

        private String readPort() {
            String port = config.getString(Configuration.DEFAULT_EMAIL_PORT);
            return port;
        }
    }

    /**
     * This is a general purpose Smtp helper class which 
     * takes a general purpose SmtpOptions and helps
     * create a javax.mail.Session
     *
     */
     public static class SmtpSessionHelper {
        
        private Properties props;
        private SmtpOptions smtpOptions;
        
        public SmtpSessionHelper(SmtpOptions smtpOptions) {
            this.smtpOptions = smtpOptions;
        }
        
        public Session createSession() throws GeneralException {
            
            populateProperties();

            Authenticator authenticator = new javax.mail.Authenticator() {

                protected PasswordAuthentication getPasswordAuthentication() {

                    return new PasswordAuthentication(smtpOptions
                            .getEncryptionOptions().getUsername(), smtpOptions
                            .getEncryptionOptions().getPassword());
                }
            };

            return Session.getInstance(props, authenticator);
        }
        
        private void populateProperties() throws GeneralException {
            
            props = new Properties();

            if (smtpOptions.getSessionProperties() != null) {
                props.putAll(smtpOptions.getSessionProperties());
            }

            props.setProperty(EmailTemplate.HOST_PROPERTY, smtpOptions.getHost());
            
            /* Bug #7827 requested this.  For troubleshooting multiple iiq instances on one host */
            props.setProperty( "mail.smtp.localhost", Util.getHostName() );
            
            if (smtpOptions.getPort() != -1) {
                props.setProperty(EmailTemplate.PORT_PROPERTY, "" + smtpOptions.getPort());
            }
            
            //Always use authentication if they bothered to add a username and password
            if( smtpOptions.getEncryptionOptions().getUsername() != null && smtpOptions.getEncryptionOptions().getPassword() != null) {
                props.put("mail.smtp.auth", "true");
            }
            
            // This property tells the provider to send email to any
            // valid email address. If this is set to false and there
            // are invalid addresses no recipients will recieve the 
            // message, which is the setting by default.
            props.setProperty("mail.smtp.sendpartial", "true");
            
            if (smtpOptions.getEncryptionOptions().getEncryptionType() == SmtpEncryptionType.TLS) {
                props.put("mail.smtp.starttls.enable", "true");
            } else if (smtpOptions.getEncryptionOptions().getEncryptionType() == SmtpEncryptionType.SSL) {
                String sslSocketFactoryClass = "javax.net.ssl.SSLSocketFactory";
                if (!Util.isNullOrEmpty(smtpOptions.getEncryptionOptions().getSslSocketFactoryClass())) {
                    sslSocketFactoryClass = smtpOptions.getEncryptionOptions().getSslSocketFactoryClass();
                } 
                props.put("mail.smtp.socketFactory.class", sslSocketFactoryClass);
                
                if (smtpOptions.getPort() != -1) {
                    props.put("mail.smtp.socketFactory.port", "" + smtpOptions.getPort());
                }
            }
        }
    }
    
    public static class SmtpOptions {

        private String host;
        private int port = -1;
        private SmtpEncryptionOptions encryptionOptions;
        private Map<String, String> sessionProperties;
        
        public String getHost() {
            return this.host;
        }

        public void setHost(String val) {
            this.host = val;
        }

        public int getPort() {
            return this.port;
        }

        public void setPort(int val) {
            this.port = val;
        }

        public SmtpEncryptionOptions getEncryptionOptions() {
            return this.encryptionOptions;
        }

        public void setEncryptionOptions(SmtpEncryptionOptions val) {
            this.encryptionOptions = val;
        }
        
        public Map<String, String> getSessionProperties() {
            return sessionProperties;
        }
        
        public void setSessionProperties(Map<String, String> val) {
            sessionProperties = val;
        }

        public static class SmtpEncryptionOptions {
            
            private SMTPEmailNotifier.SmtpEncryptionType encryptionType;
            private String username;
            private String password;
            private String sslSocketFactoryClass;
            
            public SMTPEmailNotifier.SmtpEncryptionType getEncryptionType() {
                return this.encryptionType;
            }
            
            public void setEncryptionType(SMTPEmailNotifier.SmtpEncryptionType val) {
                this.encryptionType = val;
            }
            
            public String getSslSocketFactoryClass() {
            
                return sslSocketFactoryClass;
            }

            public void setSslSocketFactoryClass(String val) {
            
                sslSocketFactoryClass = val;
            }

            public String getUsername() {
                return this.username;
            }
            
            public void setUsername(String val){
                this.username = val;
            }
            
            public String getPassword() {
                return this.password;
            }
            
            public void setPassword(String val) {
                this.password = val;
            }
        }
        
    }
    
    @XMLClass
    public static enum SmtpEncryptionType {
        
        NONE(MessageKeys.NONE), SSL("ssl"), TLS("tls");
        
        private String display;
        
        private SmtpEncryptionType(String display) {
            this.display = display;
        }
        
        public String getDisplay() {
            return new sailpoint.tools.Message(display).getLocalizedMessage().toUpperCase();
        }
    }

}

