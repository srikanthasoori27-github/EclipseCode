/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class MessageRenderer {



    /**
     * Static method to render an arbitrary string of text. Any paramters which
     * are localizable are localized using the given locale and timezone. Handles
     * Velocity templates or the old SailPoint templating system - see
     * SailPoint.tools.VariableExpander for more on that.
     *
     * This version will not html escape the reference params.
     *
     * @param src Message template text
     * @param params Parameters to pass to the message.
     * @param locale Locale used to localize params
     * @param tz TimeZone used to localize date params
     * @return Formatted, localized message
     * @throws GeneralException
     */
    public static String render(String src, Map params, Locale locale, TimeZone tz)
            throws GeneralException {
       return render(src, params, locale, tz, false);
    }

    /**
     * Render with escape param.
     *
     * @param src Message template text
     * @param params Parameters to pass to the message.
     * @param locale Locale used to localize params
     * @param tz TimeZone used to localize date params
     * @param escape boolean used to enable html escape for template references.
     * @return Formatted, localized message
     */
    public static String render(String src, Map params, Locale locale, TimeZone tz, boolean escape) throws GeneralException {
        if (src == null || src.isEmpty())
            return src;

        Map localizedParms = localizeParams(params, locale, tz);

        //For backward compatibility, sniff the string to see if
        // it contains the old style $(...) references and if so
        //use our own VariableExpander rather than Velocity.
        if (src.indexOf("$(") >= 0) {
            // old school
            return VariableExpander.expand(src, localizedParms);
        } else {
            // note that this may throw for many reasons
            return VelocityUtil.render(src, localizedParms, locale, tz, escape);
        }
    }

    /**
     * Renders the given src text, inserting the given parameters. Parameters
     * are localized using server default locale and timezone. Handles
     * Velocity templates or the old SailPoint templating system - see
     * SailPoint.tools.VariableExpander for more on that.
     *
     * @param src Message template text
     * @param params Parameters to pass to the message.
     * @return Formatted message localized using server default locale and timezone
     * @throws GeneralException
     */
    public static String render(String src, Map params)
            throws GeneralException {
        return render(src, params, Locale.getDefault(), TimeZone.getDefault(), false);
    }

    /**
     * Localizes any args which implement SailPoint.tools.Localizable
     *
     * @param args
     * @return
     */
    private static Map localizeParams(Map args, Locale locale, TimeZone tz) {

        if (args==null || args.isEmpty())
            return args;

        Map localizedArgs = new HashMap();
        for (Object key : args.keySet()) {
            Object val = args.get(key);
            if (val != null && Localizable.class.isAssignableFrom(val.getClass())) {
                localizedArgs.put(key, ((Localizable) val).getLocalizedMessage(locale, tz));
            } else {
                localizedArgs.put(key, val);
            }
        }

        return localizedArgs;
    }

}
