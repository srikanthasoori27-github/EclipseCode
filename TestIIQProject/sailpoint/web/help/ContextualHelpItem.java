/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.help;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Contextual Help model for storing information used by the ui to render help on various points of the ui.
 * Can be used by links or buttons to popup videos, urls, html content, pdfs, etc... to aid the user in using
 * the ui.
 *
 * @author peter.holcomb
 */
public class ContextualHelpItem extends AbstractXmlObject {
    private static final long serialVersionUID = -2451447994832698411L;

    @XMLClass(xmlname="ContextualHelpItemType")
    public static enum Type {
        Popup,
        Video,
        URL,
        HTML,
        PDF
    }

    /**
     * A unique key used to identify the help item on the ui and load the appropriate content for this
     * help item from the UIConfig
     */
    private String key;

    /**
     * The type of help this is, either Popup, Video, URL, HTML, or PDF
     */
    private Type type;

    /**
     * Whether the help item is enabled and should appear on the ui.
     */
    private boolean enabled;

    /**
     * Optional - Used by help items that load external content from an external url
     */
    private String url;

    /**
     * Optional - Used by help items such as HTML that load html directly from this item into the UI
     */
    private String source;

    /**
     * Optional - On a popup, the height of the popup window.
     */
    private int height;

    /**
     * Optional - On a popup, the width of the popup window.
     */
    private int width;

    /**
     * Optional - Use the included css that will maintain a consistent look and feel across contextual
     * help pages.
     */
    private boolean useIncludedCSS = false;

    /**
     * Optional - Use the included html template (contextual-help-template.html) to be used for
     * pages that want to adopt the standard look and feel across help pages
     */
    private boolean useTemplate = false;

    /**
     * Optional - When using the template, this will override the default logo path.
     * {@value ContextualHelpServlet#LOGO_PATH}
     * Useful for a simple modification when only needing to change the logo
     */
    private String logoPath;

    /**
     * Optional - When using the template, this will override the default alt description
     * on the image tag.
     * Used when overriding the logo path and when the default of "SailPoint logo" would
     * not make sense.
     */
    private String logoDescription;

    /**
     * Optional - When using the template, this will override the default title on the page
     */
    private String title;

    /**
     * Required - URL of where this contextual help item lives.
     * This is used to authorize the user in the servlet. If the user cannot view this url,
     * then they should not be authorized to view this help item.
     */
    private String homeUrl;

    @XMLProperty
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @XMLProperty
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @XMLProperty
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @XMLProperty
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    @XMLProperty
    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    @XMLProperty
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    @XMLProperty
    public boolean isUseIncludedCSS() {
        return useIncludedCSS;
    }

    public void setUseIncludedCSS(boolean useIncludedCSS) {
        this.useIncludedCSS = useIncludedCSS;
    }

    @XMLProperty
    public boolean isUseTemplate() {
        return useTemplate;
    }

    public void setUseTemplate(boolean useTemplate) {
        this.useTemplate = useTemplate;
    }

    @XMLProperty
    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    @XMLProperty
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @XMLProperty
    public String getHomeUrl() {
        return homeUrl;
    }

    public void setHomeUrl(String homeUrl) {
        this.homeUrl = homeUrl;
    }

    @XMLProperty
    public String getLogoDescription() {
        return logoDescription;
    }

    public void setLogoDescription(String logoDescription) {
        this.logoDescription = logoDescription;
    }
}
