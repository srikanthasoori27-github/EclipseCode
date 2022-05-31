/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

public class EmailUtil {

    /**
     * Used by Java mail so that attachment filenames with international characters are properly encoded.
     */
    static public void enableEncodedAttachmentNames() {
        System.setProperty("mail.mime.encodefilename", "true");
        System.setProperty("mail.mime.encodeparameters", "true");
    }

    /**
     * Try to guess if a message body is formatted as HTML.
     * Ideally we should have a way to ask that a message be sent
     * with mime type text/html but we're trying to avoid a schema
     * change for a POC.
     *
     * If after trimming the body the body starts with
     * <!DOCTYPE or <HTML ignoring case, then we assume this
     * is in html.
     */
    public static boolean isHtml(String body) {

        boolean html = false;
        if (Util.isNotNullOrEmpty(body)) {
            body = body.trim();
            html = (body.startsWith("<!DOCTYPE") ||
                    body.startsWith("<HTML") ||
                    body.startsWith("<html"));
        }
        return html;
    }
}
