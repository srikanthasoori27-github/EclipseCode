/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import sailpoint.tools.Internationalizer;

import java.util.ResourceBundle;
import java.util.Enumeration;
import java.util.Locale;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ResourceBundleProxy extends ResourceBundle {

    private Locale locale;


    public ResourceBundleProxy(Locale locale) {
        this.locale = locale;
    }

    public Enumeration<String> getKeys() {
        throw new UnsupportedOperationException();
    }

    protected Object handleGetObject(String key) {

        if (key==null)
            return null;

        String localizedVal = Internationalizer.getMessage(key, locale);

        return localizedVal != null ? localizedVal : key;
    }
}
