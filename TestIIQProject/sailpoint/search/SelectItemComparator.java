package sailpoint.search;

import javax.faces.model.SelectItem;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Comparator class to sort SelectItems using the localized label.
 */
public class SelectItemComparator implements Comparator<SelectItem> {

    private Locale locale = Locale.getDefault();

    public SelectItemComparator(Locale l) {
        if(l != null) {
            this.locale = l;
        }
    }

    /**
     * Sort based on the localized label of the SelectItem.
     *
     * Apparently this is relatively slow compared to using
     * the CollationKey way of sorting, but should be okay
     * for the size of lists we're dealing with.
     *
     * @param itemA
     * @param itemB
     * @return
     */
    @Override
    public int compare(SelectItem itemA, SelectItem itemB) {
        Collator collator = Collator.getInstance(this.locale);
        collator.setStrength(Collator.SECONDARY); // this is medium strict
        return collator.compare((String)itemA.getLabel(), (String)itemB.getLabel());
    }
}
