/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

import javax.faces.model.SelectItem;
import sailpoint.tools.Util;

/**
 * This Comparator compares to SelectItems according to their respective labels.
 * The Comparator is case-insensitive with respect to the labels and sorts using
 * the collation rules for the given locale.
 *
 * @see java.util.Comparator
 * @author Bernie Margolis
 */
public class SelectItemByLabelComparator implements Comparator<SelectItem> {
  
    private Collator collator;
    private Locale locale;
    private boolean ignoreCase;
    
    /**
     * Creates new comparator instance for the given locale. 
     *
     * @param locale User's Locale, or null for default locale.
     */
    public SelectItemByLabelComparator(Locale locale){
        this(locale, false);
    }
    
    public SelectItemByLabelComparator(Locale locale, boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        this.locale = locale;
        
        if (locale == null)
            collator = Collator.getInstance();
        else
            collator = Collator.getInstance(locale);

        collator.setStrength(Collator.PRIMARY);
    }
    
    public int compare(SelectItem o1, SelectItem o2) {
        int result = 0;
        // I've seen this used in places where the label in one of the
        // items is null.  That's probably wrong but be resiliant - jsl
        String l1 = o1.getLabel();
        if (l1 == null)
            l1 = Util.otoa(o1.getValue());
        else if (ignoreCase)
            l1 = l1.toLowerCase(locale);

        String l2 = o2.getLabel();
        if (l2 == null)
            l2 = Util.otoa(o2.getValue());
        else if (ignoreCase)
            l2 = l2.toLowerCase(locale);

        if (l1 != null && l2 != null) {
            // jsl - by convention we usually use a "--" prefix for
            // an initial value like "--Select Something--"
            // There should only be one, force it to the top.  
            if (l1.startsWith("--"))
                result = -1;
            else
                result = collator.compare(l1, l2);
        }
        else {
            // ambiguous, push them to the bottom?
            result = 1;
        }
        return result;
    }

}
