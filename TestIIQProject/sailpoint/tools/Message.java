/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.tools;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.web.messages.MessageKeys;

import java.text.MessageFormat;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.Locale;
import java.util.TimeZone;

import openconnector.OpenMessagePart;

/**
 * A class containing all the elements needed to generate localized message text,
 * including the key of the message and any parameters which should be injected
 * into the message string.
 */
@XMLClass(xmlname = "Message", alias = "LocalizedMessage")
public class Message extends AbstractXmlObject implements Localizable, Serializable, OpenMessagePart {

    /**
     * Type or severity of the message.
     */
    @XMLClass(xmlname="Type")
    public enum Type{
        Info
        , Warn
        , Error
    }

    private Type type;

    // Message key. When localizing, if we can't find the key in our
    // bundles, we'll return this key as the message text.
    private String key;

    /**
     * This array can be made up of any object. If the parameter is another
     * Message, it will be processed, if not we will use toString()
     * on the object. Null parameter objects will return null.
     */
    private List parameters;

    /**
     * Maximum size of the string. Resulting message text will be truncated
     * at this length and formatted into MessageKeys.MSG_TRUNCATED.
     *
     * !!Note that that the formatted message can be longer than the maxlength
     * due to the addition of the content of MSG_TRUNCATED.
     */
    private Integer maxLength;


    /**
     * Default constructor
     */
    public Message() {
    }

    /**
     * Creates Message instance with given
     * key and arguments. Message key can be a valid key from a resource
     * bundle, or plain text. Message type is defaulted to
     * <code>Type.Info</code>.
     *
     * @param key ResourceBundle essage key, or a plain text message
     * @param args message format args
     */
    public Message(String key, Object... args) {
        this(Type.Info, key, args);
    }

    /**
     * Creates a new instance for the given type, key and set of parameters.
     *
     * @param type Message type: Warn, Error, etc.
     * @param key ResourceBundle message key, or a plain text message
     * @param args Any parameters to be inserted into the message.
     *  The list can be made up of any object. If the parameter is another
     *  Message, it will be localized, if not we will call toString()
     * to get the value.
     */
    public Message(Type type, String key, Object... args) {
        this.key = key;
        this.type = type;

        if (args != null && args.length > 0){

            this.parameters = new ArrayList();

            for (int i = 0; i < args.length; i++){
                Object arg = args[i];
                parameters.add(convertArg(arg));
            }
        }
    }

    /**
     * Convenience constructors.
     */
    static public Message warn(String key, Object... args) {
        return new Message(Type.Warn, key, args);
    }

    static public Message error(String key, Object... args) {
        return new Message(Type.Error, key, args);
    }

    static public Message info(String key, Object... args) {
        return new Message(Type.Info, key, args);
    }

    /**
     * Localizes the given key.
     *
     * This method is ideally suited
     * for use in Beanshell since Beanshell does not support
     * varargs.
     */
    static public Message localize(String key) {
        return new Message(Type.Info, key);
    }

    /**
     * Localizes the given key with the given paramters.
     *
     * This method is ideally suited
     * for use in Beanshell since Beanshell does not support
     * varargs.
     */
    static public Message localize(String key, Object[] arguments) {
        return new Message(Type.Info, key, arguments);
    }

    /**
     * Converts a message argument into an object which can be stored and serialized
     * as a Message parameter. There are a number of objects that we can handle,
     * otherwise we convert the argument into a string.
     *
     * @param arg The argument to convert into a parameter-friendly object.
     * @return Arg converted to an object which can be stored as a parameter
     */
    private Object convertArg(Object arg){

        // todo use the Localizable interface here.
        if (arg == null){
            return "";
        }else if (arg instanceof LocalizedDate){
            return arg;
        }else if (Message.class.isAssignableFrom(arg.getClass())){
            return arg;
        }else if (GeneralException.class.isAssignableFrom(arg.getClass())){
            return ((GeneralException)arg).getMessageInstance();
        }else if (arg instanceof Boolean){
            return new Message((Boolean)arg ? MessageKeys.TXT_TRUE : MessageKeys.TXT_FALSE);
        }else if (arg instanceof Date){
           return new LocalizedDate((Date)arg, Internationalizer.IIQ_DEFAULT_DATE_STYLE,
                   Internationalizer.IIQ_DEFAULT_TIME_STYLE);
        }else if (Number.class.isAssignableFrom(arg.getClass())){
            return arg;
        }else if (Collection.class.isAssignableFrom(arg.getClass()) || arg.getClass().isArray()){
            List items = new ArrayList();
            Object[] argItems = arg.getClass().isArray() ? (Object[])arg : ((Collection)arg).toArray();
            for (int i=0;i<argItems.length;i++) {
                items.add(convertArg(argItems[i]));
            }
            return items;
        }else{
            return arg.toString();
        }
    }

    /**
     * Creates a message for the given exception.
     * <p>
     * If the throwable instance is an instance or subclass of <code>sailpoint.tools.GeneralException</code>
     * the message is set using {@link sailpoint.tools.GeneralException#getMessageInstance}.
     * If the exception is any other type, a generic exception message is used, in the form of
     * <i>'An unexpected error occurred: <exception text parameter>'</i> with the text from
     * <code>exception.getLocalizedMessage()</code> used to populate the exception text parameter.
     * </p>
     *
     * @param type Message type: Warn, Error, etc. If null, Error is used.
     * @param t Exception instance to to create a message from. Can be null, but
     *  should not be.
     */
    public Message(Type type, Throwable t){
        this.type = type != null ? type : Type.Error;
        if (t == null){
            this.key = MessageKeys.ERR_FATAL_SYSTEM;
        } else if (GeneralException.class.isAssignableFrom(t.getClass())){
            GeneralException e = (GeneralException)t;
            this.key = e.getMessageInstance().getKey();
            this.parameters = e.getMessageInstance().getParameters();
        }else{
            this.key = MessageKeys.ERR_EXCEPTION;
            this.parameters = new ArrayList<Object>();
            String msg = t.getLocalizedMessage();
            if (msg != null)
                this.parameters.add(msg);
            else {
                // something simple like NPE?
                this.parameters.add(t.getClass().getName());
            }
        }
    }

    /**
     * Returns message localized in US english. Used for logging
     * messages.
     *
     * @return Message localized for USofA.
     */
    public String getMessage() {
        return getLocalizedMessage(Locale.US, TimeZone.getDefault());
    }

    /**
     * Gets message text localized for the default locale and server timezone.
     *
     * @return message localized for the default locale and timezone.
     */
    public String getLocalizedMessage() {
        return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
    }

    /**
     * Convenient so we can just print them.
     */
    public String toString() {
        return getLocalizedMessage();
    }

    /**
     * Creates localized text using the given locale and timezone.
     *
     * @param locale Locale to use. If null the default locale is used.
     * @param timezone TimeZone to use. If null the server timezone is used.
     * @return localized message text.
     */
    public String getLocalizedMessage(Locale locale, TimeZone timezone) {

        String msg = getBundleMessage(this.getKey(), locale);

        // if we can't find the key, assume it's a plain text message.
        if (msg == null)
            return getKey();

        List localizedParams = null;
        if (parameters != null && !parameters.isEmpty()){
            localizedParams = new ArrayList();
            for(Object parameter : parameters){
                localizedParams.add(convertParamToString(parameter, locale, timezone));
            }
        }

        if (localizedParams != null && !localizedParams.isEmpty())
            msg = MessageFormat.format(msg, localizedParams.toArray());

        // the maxlength is set, trunc the string and append '...'
        if (msg != null && maxLength != null && msg.length() > maxLength) {
            String truncMsg = getBundleMessage(MessageKeys.MSG_TRUNCATED, locale);
            msg = MessageFormat.format(truncMsg, msg.substring(0, maxLength-1));
        }

        return msg;
    }

    /**
     * Gets message from the Internationalizer. This method is
     * overridden in unit tests so that we do no rely on
     * any external resource bundles.
     *
     * @param key The key to look up
     * @param locale Locale to
     * @return String message for the given key and locale.
     */
    protected String getBundleMessage(String key, Locale locale){
        return Internationalizer.getMessage(key, locale);
    }

    /**
     * Converts a given message parameter into a string.
     *
     * @param parameter The parameter to convert
     * @param locale Locale to use when localizing the parameter
     * @param timezone Timezone to use when localizing the parameter
     * @return Parameter converted to a localized string
     */
    private String convertParamToString(Object parameter, Locale locale, TimeZone timezone){
        if (parameter == null){
            return "";
        }else if (LocalizedDate.class.isAssignableFrom(parameter.getClass())){
            LocalizedDate param = (LocalizedDate)parameter;
            return param.getLocalizedMessage(locale, timezone);
        }else if (Message.class.isAssignableFrom(parameter.getClass())){
            Message param = (Message)parameter;
            return param.getLocalizedMessage(locale, timezone);
        }else if (Number.class.isAssignableFrom(parameter.getClass())){
            return Internationalizer.getLocalizedNumber((Number)parameter, locale);
        }else if (parameter instanceof List){
            List<String> convertedListItems = new ArrayList<String>();
            for (Object o : ((List)parameter)){
                convertedListItems.add(convertParamToString(o, locale, timezone));
            }
            return createDelimitedList(convertedListItems, locale);
        }else if (parameter instanceof String){
            String localized = Internationalizer.getMessage((String)parameter, locale);
            return localized != null ? localized : (String)parameter;
        }else{
            return parameter.toString();
        }
    }

    /**
     * Concatenates given list of items into a string separated by a
     * locale specific delimiter.
     *
     * @param items List of items to concatenate
     * @param locale The Locale whose delimiter should be used
     * @return String containing the items from the given list concatenated with
     * the locale's delimiter.
     */
    private String createDelimitedList(List<String> items, Locale locale){
        if (items == null || items.isEmpty())
            return "";

        String delimiter = getBundleMessage(MessageKeys.LIST_DELIMITER, locale);

        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0)
                buf.append(delimiter);
            buf.append(items.get(i));
        }

        return buf.toString();
    }

    /**
     * Sets the message key. If the key is not found in a resource
     * bundle, it will be returned when the message is rendered, so
     * the key argument can also be a plain text message.
     *
     * @param key ResourceBundle message key, or a plain text message
     */
    @XMLProperty
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the message key. The key can be a valid key from
     * a ResourceBundle, or plain text.
     *
     * @return Message key, or a plain text message
     */
    public String getKey() {
        return key;
    }


    /**
     * Gets the type of message.
     *
     * @return Message type: Warn, Error, etc.
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the type of message.
     *
     * @param type Message type: Warn, Error, etc.
     */
    @XMLProperty
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Compares the given type to the instance's message type.
     *
     * @param theType The type to compare. If null, false is returned.
     * @return True if theType matches the instances type.
     */
    public boolean isType(Type theType){
        return theType != null && theType.equals(this.type);
    }

    /**
     * Returns true if the message is plain text and does not match an
     * existing key in one of the message catalogs.
     *
     * @return true if the message does not match a key in the message catalogs.
     */
    public boolean isPlainText(){
        return getKey() == null ||
                Internationalizer.getMessage(getKey(), Locale.getDefault()) == null;
    }

    /**
     * Sets parameters to insert into the message when it is rendered. These
     * parameters can be other Message instances, LocalizedDates
     * Dates, Numbers, String or Throwables. Any other object will be converted
     * to a string using the toString() method. Nulls are treated as an empty string.
     *
     * @param parameters Parameters to insert into the message
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * Gets parameters to insert into the message when it is rendered. These
     * parameters can be other Message instances, LocalizedDates
     * Dates, Numbers, String or Throwables. Any other object will be converted
     * to a string using the toString() method. Nulls are treated as an empty string.
     *
     * @return Parameters to insert into the message
     */
    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * Maximum size of the resulting message text string. If Null
     * message will not be truncated.
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * Maximum size of the string. Resulting message text will be truncated
     * at this length and formatted into MessageKeys.MSG_TRUNCATED.
     *
     * !!Note that that the formatted message can be longer than the maxlength
     * due to the addition of the content of MSG_TRUNCATED.
     */
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Convenience method to determine if this message
     * is an error.
     * @return True if message Type==Error
     */
    public boolean isError(){
        return Type.Error.equals(type);
    }

    /**
     * Convenience method to determine if this message
     * is a warning.
     * @return True if message Type==Warn
     */
    public boolean isWarning(){
        return Type.Warn.equals(type);     
    }
    
    /**
     * For comparing messages for equality test to see if the key, 
     * type and parameters are equal. 
     */
    @Override
    public boolean equals(Object other) {
        
        if ( (other == null ) || !(other instanceof Message)) 
            return false;
        
        Message otherMessage = (Message)other;
        String key = this.key;
        List params = this.parameters;
        Type type = this.getType();
        
        if ( Util.nullSafeEq(key, otherMessage.getKey()) && 
             Util.nullSafeEq(params, otherMessage.getParameters(), true) &&
             Util.nullSafeEq(type, otherMessage.getType(), true ) ) {
            return true;
        }
        return false;
    }
}

