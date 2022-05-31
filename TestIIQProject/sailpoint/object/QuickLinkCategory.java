/**
 *
 */
package sailpoint.object;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author peter.holcomb
 *
 */
public class QuickLinkCategory extends AbstractXmlObject implements Comparable<QuickLinkCategory> {

    private static final long serialVersionUID = -6709513188319107384L;
    private String name;
    private String messageKey;
    private String cssClass;
    private boolean enabled;
    private int ordering;

    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XMLProperty
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * @deprecated use {@link #setCssClass(String)} instead
     */
    @Deprecated
    @XMLProperty(legacy = true)
    public void setIcon(String icon) {
        this.cssClass = icon;
    }

    @XMLProperty
    public String getCssClass() {
        return this.cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    @XMLProperty
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @XMLProperty
    public int getOrdering() {
        return ordering;
    }

    public void setOrdering(int ordering) {
        this.ordering = ordering;
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof QuickLinkCategory)) {
            return false;
        }

        QuickLinkCategory link = (QuickLinkCategory) o;
        return Util.nullSafeEq(this.name, link.getName(), true);
    }

    /**
     * Allows SearchInputDefinitions to be sorted according to the value of their
     * sortIndex attributes. If either definition has a null sortIndex, return
     * 0 (equal) so that no further sorting is attempted. Likewise, return 0 if
     * a NumberFormatException is thrown while trying to convert the sortIndex
     * into an Integer.
     */
    public int compareTo(QuickLinkCategory cat) {
        // don't try to sort if one or the other definitions being compared
        // has a null sortIndex
        if ((this.ordering == 0) || (cat.getOrdering() == 0)) {
            return 0;
        }
        else {
            try {
                Integer int1 = new Integer(this.ordering);
                Integer int2 = new Integer(cat.ordering);
                return int1.compareTo(int2);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
