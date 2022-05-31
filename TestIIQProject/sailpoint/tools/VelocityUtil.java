/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A simple utility class that wraps the Velocity API and
 * provides some convenience methods.
 *
 * Author: Jeff
 */

package sailpoint.tools;

import java.io.StringWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

import sailpoint.web.util.WebUtil;


public class VelocityUtil {
    private static Log log = LogFactory.getLog(VelocityUtil.class); 
        
    /**
     * Set to true when the velocity engine is initialized.
     * Not sure if there is a penalty to calling Velocity.init
     * over and over so prevent it.
     */
    private static boolean _initialized;

    public static void init()
        throws GeneralException {

        if (!_initialized) {
            try {
                URL resourceUrl = VelocityUtil.class.getResource( "/velocity.properties" );
                if( resourceUrl != null ) {
                    Velocity.init( resourceUrl.getPath() );
                } else {
                    Velocity.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.AvalonLogChute,org.apache.velocity.runtime.log.Log4JLogChute,org.apache.velocity.runtime.log.JdkLogChute");
                    Velocity.setProperty("ISO-8859-1", "UTF-8");
                    Velocity.setProperty("output.encoding", "UTF-8");
                    Velocity.setProperty("resource.loaders", "classpath");
                    Velocity.setProperty("resource.loader.classpath.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
                    Velocity.init();
                }
                _initialized = true;
            }
            catch (Exception e) {
                throw new GeneralException(e);
            }
        }
    }

    /**
     * Render string with args and allow html escaping for values in args map.
     * @param src String template to render
     * @param args Map args to use for rendering
     * @param locale Locale
     * @param tz TimeZone
     * @param escape boolean true if html should be escaped
     * @return String rendered with args
     */
    public static String render(String src, Map args, Locale locale, TimeZone tz, boolean escape) throws GeneralException {
        init();

        // convert arg map to a velocity context
        VelocityContext context;
        if (args != null)
            context = new VelocityContext(args);
        else
            context = new VelocityContext();

        // Add SailPoint utility class for use within the template
        context.put("spTools", new SailPointVelocityTools(locale, tz));

        if (escape) {
            // Setup event handler for escaping html in data
            // This will just escape html from context data while allowing html to be used in the email templates.
            EventCartridge eventCartridge = new EventCartridge();
            eventCartridge.addReferenceInsertionEventHandler(new SailPointEscapeHtmlReference());
            context.attachEventCartridge(eventCartridge);
        }

        return render(src, context);
    }

    /**
     * Default render without html escape.
     * @param src String template to render
     * @param args Map args to use for rendering
     * @param locale Locale
     * @param tz TimeZone
     * @return String rendered with args
     * @throws GeneralException
     */
    public static String render(String src, Map args, Locale locale, TimeZone tz) throws GeneralException {
        return render(src, args, locale, tz, false);

    }

    /**
     * Render string using velocity context
     * @param src String template to render
     * @param context VelocityContext to use for rendering
     * @return String rendered velocity template using context
     * @throws GeneralException
     */
    public static String render(String src, VelocityContext context)
        throws GeneralException {

        if (src == null || src.isEmpty() || context == null) {
            return src;
        }

        String result;

        try {
            // render to a string buffer
            StringWriter writer = new StringWriter();

            // tag to be used in log messages, supposed
            // to identity the template
            String tag = "anonymous";

            boolean success = Velocity.evaluate(context, writer, tag, src);

            if (success)
                result = writer.toString();
            else {
                // something bad happened should have been logged
                throw new GeneralException("Unable to evaluate template");
            }
        }   
        catch (ParseErrorException e) {
            // syntax error in the template
            throw new GeneralException(e);
        }
        catch (MethodInvocationException e) {
            // method or property referenced in the template was invalid
            throw new GeneralException(e);
        }
        catch (ResourceNotFoundException e) {
            // when loading a reference
            throw new GeneralException(e);
        }

        return result;
    }
    
    /**
     * Collection of localization utility methods for use within velocity templates.
     */
    public static final class SailPointVelocityTools{

        private Locale locale;
        private TimeZone timezone;
        
        public SailPointVelocityTools(Locale locale, TimeZone timezone) {
            this.locale = locale;
            this.timezone = timezone;
        }

        /**
         * Formats date using IIQ default time and date styles. Check
         * sailpoint.tools.Internationalizer for the defaults. In the event
         * that the date parameter is not actually a date, an empty string
         * is returned.
         * @param date  Date to format
         * @return Formatted date string or empty string if date is null
         */
        public String formatDate(Object date){
            if (date == null || !(date instanceof Date))
                return "";
            return Internationalizer.getLocalizedDate((Date)date, locale, timezone);
        }

        /**
         * Formats date using the given style formats In the event
         * that the date parameter is not actually a date, the value is
         * returned unchanged.
         *
         * The dateStyle and timeStyle parameter values correspond to java.text.DateFormat
         * constants.
         *
         * DateFormat.SHORT is completely numeric, such as 12.13.52 or 3:30pm. The value of this constant is 3
         * DateFormat.MEDIUM is longer, such as Jan 12, 1952. The value of this constant is 2
         * DateFormat.LONG is longer, such as January 12, 1952 or 3:30:32pm.  The value of this constant is 1
         * DateFormat.FULL is completely specified, such as Tuesday, April 12, 1952 AD or 3:30:42pm PST.
         *  The value of this constant is 0.
         *
         * @param date          Date to format
         * @param dateStyle     java.text.DateFormat format style constant
         * @param timeStyle     java.text.DateFormat format style constant 
         * @return Formatted date string or empty string if date is null
         */
        public String formatDate(Object date, Integer dateStyle, Integer timeStyle){
            if (date == null || !(date instanceof Date))
                return "";
            return Internationalizer.getLocalizedDate((Date)date, dateStyle, timeStyle, locale, timezone);
        }

        /**
         * Formats date using the given format String In the event
         * that the date parameter is not actually a date, a blank 
         * String is returned.
         * @param date          Date to format
         * @param formatString  Format String (i.e. MM/dd/yyyy HH:mm)
         * @return Formatted date string or empty string if date is null.
         * If the formatString is invalid, the formatted date will default
         * the short style date and time expected by the current locale
         */
        public String formatDate(Object date, String formatString) {
            final String formattedDate;
            if (date == null || !(date instanceof Date)) {
                formattedDate = "";
            } else {
                DateFormat formatter;
                try {
                    formatter = new SimpleDateFormat(formatString, locale);
                } catch (IllegalArgumentException e) {
                    formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
                    log.error("An invalid pattern string was passed into the formatDate utility.  The short style formatter has been used instead.", e);
                }
                formattedDate = formatter.format(date);
            }
            return formattedDate;
        }
        
        /**
         * Gets message for the given key. If a matching message cannot be
         * found null is returned.
         * @param key Message key
         * @return Message text from catalog, or null if key was not found in catalog.
         */
        public String getMessage(String key){
            return Internationalizer.getMessage(key, locale);
        }
        
        /**
         * Re-formats a URL to use the redirect service.  This is necessary since browsers do not send the
         * named anchor portion of the url to the server and it will not survive the authentication
         * redirect if they are not logged in.
         *
         * **ALL URLs within emails should go through this!**
         *
         * For example, re-formats /ui/index.jsf#/myApprovals to
         * <serverRootPath>/ui/rest/redirect?rp1=index.jsf&rp2=myApprovals
         *
         * Example usage in a Velocity template:
         * $spTools.formatURL('workitem/commonWorkItem.jsf#/commonWorkItem/297ed0d04efa282d014efa2fb8420007')
         *
         * Will not format a URL without a pound sign (#).
         *
         * @param url
         * @return
         */
        public String formatURL(String url) throws GeneralException {
            return WebUtil.getRedirectURL(url);
        }
    }

}

