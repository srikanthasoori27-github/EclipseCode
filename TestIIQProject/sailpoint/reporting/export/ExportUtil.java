/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.export;

import sailpoint.tools.Message;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.LocalizedDate;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.web.messages.MessageKeys;

import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utility class for localizing text during the jasper render process.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ExportUtil {

    private static final Log log = LogFactory.getLog(ExportUtil.class);

    public static String localize(String txt, Locale locale, TimeZone timezone ){

        if (txt == null)
            return null;

        
        String val = null;
        if (isSerializedXmlObject(txt)){
            Localizable msg = parseMessage(txt);
            val = msg!= null ? msg.getLocalizedMessage(locale, timezone) : null;
        } else {
            val = Internationalizer.getMessage(txt, locale);
        }   

        if (val != null)
            txt = val;

        return txt;
    }

    /**
     * Converts object into xml wrapped in a cdata tag.
     *
     * @param obj Object to be serialized
     * @return Xml or '' if the msg was null.
     */
    public static String serializeForJasper(AbstractXmlObject obj) throws GeneralException {
        String xml = obj.toXml(false);
        xml = xml.replace('\n',' ');
        return obj != null ? "<![CDATA[" + xml + "]]>" : "";
    }

     /**
     * Converts the given string into a Message instance.
      *
     * @param val String to parse
     * @return Message instance. Returns null if the val was null.
     */
    public static Localizable parseMessage(String val){

         if (!isSerializedXmlObject(val))
            return null;

         String xml = val.trim().substring(9, val.length() - 3).trim();

         if(xml.length() > 0){
            Object parseResults = XMLObjectFactory.getInstance().parseXml(null, xml, false);
            if (parseResults != null && Localizable.class.isAssignableFrom(parseResults.getClass())){
                return (Localizable)parseResults;    
            }
         }

         return null;
    }

    /**
     * Detects if a string is a cdata tag. If so we assume it's a valid xml object we
     * can deserialize.
     *
     * @param val The string value
     * @return True if the value is non-null and matches our jasperized message format
     */
    private static boolean isSerializedXmlObject(String val){
        return val != null && val.trim().startsWith("<![CDATA[");
    }

    public static void main(String[] args) {

        List<String> str = new ArrayList<String>();
        str.add("fd");
        str.add("fdffd");
        str.add("fxxxxd");

        Message msg = new Message(MessageKeys.ACCESS_CERT_REPT);
        Message msg2 = new Message(MessageKeys.ACCOUNT_GROUP_LCASE, new LocalizedDate(new Date(), 1, 1));
        Message msg3 = new Message(MessageKeys.ACTIVITY_ACTION_LOGOUT, str);


        String val = null;
       int a = 1;

        try {
            val = msg.toXml(false);
            a = 1;
            val = msg2.toXml(false);
            a = 1;
            val = msg3.toXml(false);
            a = 1;

        } catch (GeneralException e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }

        String xml = val.trim().substring(9, val.length() - 3);

        int de = 1;
    }    


}
