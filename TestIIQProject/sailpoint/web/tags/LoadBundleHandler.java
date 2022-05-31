/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.el.ELException;
import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.FaceletException;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagAttributeException;
import javax.faces.view.facelets.TagConfig;
import javax.faces.view.facelets.TagHandler;

import sailpoint.tools.Internationalizer;

import com.sun.faces.facelets.tag.jsf.ComponentSupport;

/**
 * Creates a map implementation which stores the user's locale and delegates
 * key retrieval to the Internationalizer. This map is then stuffed in the
 * request for use with calls to the sp:bundle component. The Internationalizer
 * allows us to check all our different catalog files when we retrieve a key,
 * rather than just one catalog. This allows us to override our base properties
 * files with any customer-specific text located in iiqCustom.properties. We're
 * also replacing Facelets LoadBundleHandler because it does not handle
 * missing keys gracefully.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class LoadBundleHandler extends TagHandler {

   private final TagAttribute basename;

   private final TagAttribute var;

    /**
     * @param config
     */
    public LoadBundleHandler(TagConfig config) {
        super(config);
        this.basename = this.getRequiredAttribute("basename");
        this.var = this.getRequiredAttribute("var");
    }

    /**
     * Creates a map implementation which stores the user's locale and delegates
     * key retrieval to the Internationalizer. This map is then stuffed in the
     * request for use with calls to the sp:bundle component.
     *
     * @see com.sun.facelets.FaceletHandler#apply(com.sun.facelets.FaceletContext,
     *      javax.faces.component.UIComponent)
     */
    public void apply(FaceletContext ctx, UIComponent parent)
            throws IOException, FacesException, FaceletException, ELException {
        UIViewRoot root = ComponentSupport.getViewRoot(ctx, parent);
        ResourceBundleMap map = null;
        try {
            if (root != null && root.getLocale() != null)
                map = new ResourceBundleMap(root.getLocale());
            else
                map = new ResourceBundleMap(Locale.getDefault());
        } catch (Exception e) {
            throw new TagAttributeException(this.tag, this.basename, e);
        }

        FacesContext faces = ctx.getFacesContext();
        faces.getExternalContext().getRequestMap().put(this.var.getValue(ctx),
                map);
    }

    /**
     * Simple map implementation that delegates calls key retrieval to the
     * Internationalizer.
     */
    private final static class ResourceBundleMap implements Map {

        private final Locale locale;

        public ResourceBundleMap(Locale locale) {
             this.locale = locale;
        }

        public boolean containsKey(Object key) {
            return null != get(key);
        }

        public Object get(Object key) {
            if (key == null)
                return null;

            String val = Internationalizer.getMessage((String)key, locale);

            return val != null ? val : key;
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
            return false;
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
            throw new UnsupportedOperationException();
        }

        public Collection values() {
            throw new UnsupportedOperationException();
        }
    }

}
