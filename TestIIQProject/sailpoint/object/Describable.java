/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.Locale;
import java.util.Map;

/**
 * Interface for objects that have descriptions. As of the time this interface
 * originated these are: Application, Policy, Bundle, ManagedAttribute
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public interface Describable {
    /**
     * Return the Map of localized descriptions.
     */
    Map<String,String> getDescriptions();
    
    /**
     * Set the Map of localized descriptions.
     */
    void setDescriptions(Map<String,String> map);

    /**
     * Incrementally add one description.
     */
    void addDescription(String locale, String desc);

    /**
     * Return the description for one locale.
     */
    String getDescription(String locale);

    /**
     * Return the description for one locale.
     */
    String getDescription(Locale locale);
}
