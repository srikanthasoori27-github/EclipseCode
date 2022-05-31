/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.export;

import sailpoint.object.Attributes;

import java.util.Map;
import java.util.Set;
import java.util.Collection;

import net.sf.jasperreports.engine.export.PdfFont;
import net.sf.jasperreports.engine.export.FontKey;


/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class FontMapping implements Map {

    public static final String KEY_DEFAULT_FONT = "default";

    public static final String KEY_PDF_FONT = "font";
    public static final String KEY_PDF_ENCODING = "encoding";
    public static final String KEY_PDF_EMBED = "embed";

    private Attributes items;


    public FontMapping(Attributes items) {
        this.items = items;
    }

    public Object get(Object key) {

       if (key != null && key instanceof FontKey)
        return getPdfFont((FontKey)key);

       if (!items.containsKey(key))
            return items.get(KEY_DEFAULT_FONT);
        else
            return items.get(key);
    }

    private PdfFont getPdfFont(FontKey key){

        String faceStr = key.isBold() ? "-bold" : "";
        if (key.isItalic()) faceStr += "-italic";

        String keyVal = key.getFontName() + faceStr;
        Map value = null;

        if (!items.containsKey(keyVal))
          keyVal = KEY_DEFAULT_FONT + faceStr;

        value = (Map)items.get(keyVal);

        if (value == null)
            throw new RuntimeException("Font configuration was not found for font definition:" + key.toString() + faceStr);

        String fontName = (String)value.get(KEY_PDF_FONT);
        String fontEncoding = (String)value.get(KEY_PDF_ENCODING);

        if (fontName == null || fontEncoding == null)
            throw new RuntimeException("Font name or encoding were not specified for font:" + key.toString());

        boolean embedFont = value.containsKey(KEY_PDF_EMBED) && value.get(KEY_PDF_EMBED) != null ?
                Boolean.parseBoolean((String)value.get(KEY_PDF_EMBED)) : false;

        return new PdfFont(fontName, fontEncoding, embedFont);
    }

    public boolean containsKey(Object key) {
        return true;
    }


    public void clear() {
        throw new UnsupportedOperationException();
    }

    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    public Set entrySet() {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        return ( items != null ) ? items.isEmpty() : true;
    }

    public Set keySet() {
        throw new UnsupportedOperationException();
    }

    public Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    public void putAll(Map t) {
        throw new UnsupportedOperationException();
    }

    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return ( items != null ) ? items.size() : 0;
    }

    public Collection values() {
        throw new UnsupportedOperationException();
    }
}
