/*
 * (c) Copyright 2021 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.tools;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.app.event.implement.EscapeReference;

/**
 * Velocity escape reference handler for html.
 * Escape html entities in velocity references to prevent html injection in emails.
 * Only used in the EmailTemplate to prevent html injection.
 */
public class SailPointEscapeHtmlReference extends EscapeReference {
    /**
     * html escape
     * @param text
     * @return escaped string.
     */
    protected String escape(Object text) {
        if (text == null || Util.isNullOrEmpty(text.toString())) {
            return null;
        }

        return StringEscapeUtils.escapeHtml(text.toString());
    }

    protected String getMatchAttribute() {
        return "eventhandler.escape.html.match";
    }
}
