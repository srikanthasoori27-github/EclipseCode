/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;


/**
 * A resource that returns a message catalog to use for client-side i18n.
 * 
 * @author Kelly Grizzle
 */
@Path("messageCatalog")
public class MessageCatalogResource extends BaseResource {

    /**
     * Return a map of message catalog keys and values for the logged in user's
     * locale.  Only keys that are prefixed with "ui_" are returned to prevent
     * returning huge amounts of data to the client.
     * 
     * @param  lang  The requested language.  NOTE: This is currently ignored
     *               and the logged in user's locale is used.
     */
    @GET
    public Map<String,String> getMessageCatalog(@QueryParam("lang") String lang)
        throws GeneralException {

        // Message keys for ALL ... YAY!!!!
        authorize(new AllowAllAuthorizer());
        
        // TODO: Ignoring the "lang" ... just using the locale from the base
        // resource.  At some point we might want to change this, but I trust
        // the Locale available in JAX-RS more than the language string that is
        // getting passed to us.

        Map<String,String> msgs = Internationalizer.getMobileMessages(getLocale());

        // Change any variables in the message to be what the spTranslate
        // filter expects.
        if (null != msgs) {
            for (Map.Entry<String,String> entry : msgs.entrySet()) {
                String value = changeVariables(entry.getValue());
                entry.setValue(value);
            }
        }

        return msgs;
    }
    
    /**
     * The angular translate service expects message variables to come in a map
     * that maps a variable name to a value.  The spTranslate filter takes care
     * of building this map based on the parameters passed to the filter, however
     * the message has to use normal angular interpolation.  To make this work,
     * we change any java message variables (eg - {0}) to an angular interpolation
     * (eg - {{var0}}).  Note that we cannot just wrap the zero in double curlies
     * because this gets interpolated as the literal number 0.
     * 
     * @param  msg  The message for which to swap out the variables.
     */
    static String changeVariables(String msg) {
        if (null != msg) {
            StringBuilder sb = new StringBuilder();

            int idx = -1;
            int lastIdx = 0;
            boolean anyVars = false;

            while (-1 != (idx = msg.indexOf('{', lastIdx))) {
                // Find the closing curly.  This is required.
                int endIdx = msg.indexOf('}', idx);
                if (-1 == endIdx) {
                    throw new IllegalStateException("Unmatched open brace at " + idx + ": " + msg);
                }

                // Stick anything since the last closing brace into the buffer.
                String prior = msg.substring(lastIdx, idx);
                sb.append(prior);

                // Stick in a special var syntax that is recognized by the
                // spTranslate angular filter.
                String varName = msg.substring(idx+1, endIdx);
                sb.append("{{var").append(varName).append("}}");

                anyVars = true;
                lastIdx = endIdx+1;
            }

            // If there were any variables, use the string that we built.
            // Otherwise, we'll just return msg.
            if (anyVars) {
                // Flush any stuff after the last variable to the buffer.
                if (lastIdx <= (msg.length()-1)) {
                    sb.append(msg.substring(lastIdx));
                }

                msg = sb.toString();
            }
        }

        return msg;
    }
}
