package sailpoint.recommender;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RecommenderResourceBundleControl extends Control {
    private static final Log log = LogFactory.getLog(RecommenderResourceBundleControl.class);

    /**
     * Recommender Bundle Base name, you should reference this when calling ResourceBundle.getBundle
     */
    public static final String BASENAME_RECOMMENDER_BUNDLE = "sailpoint.recommender";

    /**
     * Default resource bundle cache expiration time (86400000 = 24 hours)
     */
    public static final long DEFAULT_CACHE_EXPIRATION = 86400000L;

    /* implementation returning a ReasonCatalogResult */
    private RecommenderSPI recommender;
    /* cached catalog result */
    private ReasonCatalogResult catalog;
    private long ttl;

    /**
     * Create an instance of <code>RecommenderResourceBundleControl</code> with the specified
     * recommender and the cache expiration.
     *
     * @param recommender       The recommender implementation
     * @param cacheExpiration   The cache expiration, see the method description for details.
     * @return  An instance of RecommenderResourceBundleControl.
     * @throws IllegalArgumentException when <code>serviceAccount</code> is null or <code>cacheExpiration</code>
     * value is illegal.
     * @see #getInstance(ServiceAccount, long, String, String, NameMapper)
     */
    public static RecommenderResourceBundleControl getInstance(RecommenderSPI recommender, long cacheExpiration) {
        return new RecommenderResourceBundleControl(recommender, cacheExpiration);
    }

    /**
     * Package local constructor.
     * 
     * @param recommender       The recommender implementation
     * @param ttl               The cache expiration time in milliseconds.
     *                          -1 (Control.TTL_DONT_CACHE) to disable cache,
     *                          -2 (Control.TTL_NO_EXPIRATION_CONTROL) to disable cache expiration.
     */
    RecommenderResourceBundleControl(RecommenderSPI recommender, long ttl) {
        this.recommender = recommender;
        this.ttl = ttl;
    }

    @Override
    public long getTimeToLive(String baseName, Locale locale) {
        return ttl;
    }

    @Override
    public boolean needsReload(String baseName, Locale locale, String format, ClassLoader loader,
        ResourceBundle bundle, long loadTime) {
        // if the cache expires, just go ahead and reload
        return true;
    }

    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
        throws IllegalAccessException, InstantiationException, IOException {

        if (!baseName.equals(BASENAME_RECOMMENDER_BUNDLE)) {
            // When requested resource format is not a recommender bundle,
            // just delegate the request to the Java's default implementation.
            return super.newBundle(baseName, locale, format, loader, reload);
        }

        if (locale.getLanguage().isEmpty()) {
            // Recommender does not support locale with no language code, including root locale
            return null;
        }
        
        ReasonCatalogResult cat = reload ? reload() : getReasonCatalog();
        Map<String,String> messages = cat.getReasonCatalog(locale);
        // return null if locale is not available
        return (messages != null) ? RecommenderResourceBundle.loadBundle(messages) : null;
    }
    
    ReasonCatalogResult getReasonCatalog() {
        if (catalog == null) {
            reload();
        }
        return catalog;
    }
    
    ReasonCatalogResult reload() {
        if (log.isInfoEnabled()) {
            log.info("reloading Recommender Reason Message Catalog");
        }
        
        ReasonCatalogResult aCatalog = recommender.getReasonCatalog();
        if (!aCatalog.isEmpty()) {
            catalog = aCatalog;
        } else {
            log.warn("failed to reload Recommender Reason Message Catalog, empty ReasonCatalogResult");
        }
        return catalog;
    }
}
