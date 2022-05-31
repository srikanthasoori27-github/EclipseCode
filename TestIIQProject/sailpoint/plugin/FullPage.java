package sailpoint.plugin;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Full page object that holds on to the HTML title for now
 * @author brian.li
 *
 */
@XMLClass
public class FullPage extends AbstractXmlObject{

    private static final long serialVersionUID = -30297235533722825L;

    /**
     * HTML title on the full page for the plugin
     */
    private String title;

    @XMLProperty
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
}
