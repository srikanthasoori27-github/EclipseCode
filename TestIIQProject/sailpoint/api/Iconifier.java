/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.AccountIconConfig;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.object.UIConfig;


/**
 * A service class that can help determine which icons to display.  Currently
 * this only supports account icons, but we could eventually extend this concept
 * to other objects (identities, etc...) without much work.
 */
public class Iconifier {
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * An icon representation.
     */
    public static class Icon {
        
        private int extended;
        private String attribute;
        private String value;
        private String icon;
        private String title;
        private boolean namedColumn;
        
        public Icon(int extended, String attribute, String value,
                    String icon, String title, boolean namedColumn) {
            this.extended = extended;
            this.attribute = attribute;
            this.value = value;
            this.icon = icon;
            this.title = title;
            this.namedColumn = namedColumn;
        }

        public int getExtended() {
            return extended;
        }

        public String getAttribute() {
            return this.attribute;
        }
        
        public String getValue() {
            return this.value;
        }

        public String getIcon() {
            return this.icon;
        }
        
        /**
         * Alternative getter for "icon", kept for backwards compatibility.
         */
        public String getSource() {
            return this.icon;
        }

        public String getTitle() {
            return this.title;
        }
        
        public boolean isNamedColumn() {
            return namedColumn;
        }

        public void setNamedColumn(boolean namedColumn) {
            this.namedColumn = namedColumn;
        }
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    // Cached list of AccountIconConfig information.
    private List<Icon> accountIconConfig;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public Iconifier() {
    }

  
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Gets a List of icons relevant to the given account
     * @param extendedValues Map of extended link values. Keys should be extended1, extended2, etc
     */
    public List<Icon> getAccountIcons(Map<String, Object> extendedValues) {

        List<Icon> iconsToDisplay = new ArrayList<Icon>();
        for(Icon icon : getAccountIconConfig()){
            int num = icon.getExtended();
            String extendedValue = (String)extendedValues.get("extended" + num);
            String value = icon.getValue();
            if ((value != null) && (extendedValue != null)) {
                if (extendedValue.compareTo(value) == 0) {
                    iconsToDisplay.add(icon);
                }
            }
        }

        return iconsToDisplay;
    }
    
    /**
     * Return a map of Link id to a list of Icons to display for the link for 
     * all links on the given identity,
     */
    public Map<String,List<Icon>> getAccountIconsByLink(Identity identity) {

        List<Link> links = null;
        if ( identity != null ) {
            links = identity.getLinks();
        }
        return getAccountIconsForLinks(links);
    }

    /**
     * Returns a map of Link id and list of Icons to display for each link in links
     * @param links The links to get the accounts for
     * @return a map of Link id and list of Icons to display for each link in links
     */
    public Map<String, List<Icon>> getAccountIconsForLinks(List<Link> links) {
        Map<String,List<Icon>> accountIconMap = new HashMap<String,List<Icon>>();
        /* If there are no links return an empty map */
        if ( ( links == null ) || ( links.size() == 0 ) ) {
            return accountIconMap;
        }

        List<Icon> icons = getAccountIconConfig();
        if ( ( icons != null ) && ( icons.size() > 0 ) ) {
            for ( Link link : links ) {
                String linkId = link.getId();
                List<Icon> linkIcons = new ArrayList<Icon>();
                for ( Icon icon : icons ) {
                    String attrName = icon.getAttribute();
                    // Historically, this code has retrieve the attribute by
                    // name rather than extended number, so I'm going to leave
                    // it this way to avoid breaking things.
                    Object val = link.getAttribute(attrName);
                    if ( val != null  ) {
                        String attrValue = val.toString();
                        String value = icon.getValue();
                        if (( value != null) && (attrValue != null)) {
                            if ( attrValue.compareTo(value) == 0 ) {
                                linkIcons.add(icon);
                            }
                        }
                    }
                }
                accountIconMap.put(linkId, linkIcons);
            }
        }

        return accountIconMap;
    }

    /**
     * Return all account icons that match the given object (either a Link or
     * CertificationItem).
     */
    public List<Icon> getAccountIcons(SailPointObject obj) {
        List<Icon> icons = new ArrayList<Icon>();
        
        for (Icon icon : getAccountIconConfig()) {
            int num = icon.getExtended();
            // Read the value using the extended attribute.
            String extendedValue = obj.getExtended(num);
            String value = icon.getValue();
            if ( ( value != null ) && ( extendedValue != null ) ) {
                if ( extendedValue.compareTo(value) == 0 ) {
                    icons.add(icon);
                }
            }
        }

        return icons;
    }
    
    /**
     * Return the names of the extended attributes (eg - "extended1") that are
     * used by the account icon config.
     */
    public List<String> getExtendedAccountAttributeProperties() {
        List<String> properties = new ArrayList<String>();
        List<Icon> configs = this.getAccountIconConfig();
        for (Icon config : configs) {
            if (config.isNamedColumn()) {
                properties.add(config.getAttribute());
            }
            else {
                properties.add("extended" + config.getExtended());
            }
        }
        return properties;
    }

    /**
     * Get list of account icons that are applicable given the
     * UIConfig and LinkConfig
     */
    private List<Icon> getAccountIconConfig() {
        if (this.accountIconConfig == null) {
            this.accountIconConfig = new ArrayList<Icon>();
            UIConfig uiconfig = UIConfig.getUIConfig();
            ObjectConfig linkConfig = ObjectConfig.getObjectConfig(Link.class);
            if (uiconfig != null && linkConfig != null) {
                List<AccountIconConfig> configuredIcons = uiconfig.getAccountIcons();
                if ((configuredIcons != null) && (configuredIcons.size() > 0)) {
                    Link link = new Link();
                    for (AccountIconConfig icon : configuredIcons) {
                        String attrName = icon.getAttribute();
                        ObjectAttribute attr = linkConfig.getObjectAttribute(attrName);
                        if ((attr != null) && !link.isExtendedIdentityType(attr)) {
                            accountIconConfig.add(new Icon(attr.getExtendedNumber(),
                                                           attrName, icon.getValue(),
                                                           icon.getSource(), icon.getTitle(), attr.isNamedColumn()));
                        }
                    }
                }
            }
        }
        return accountIconConfig;
    }
}
