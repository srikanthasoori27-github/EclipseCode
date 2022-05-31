package sailpoint.plugin;

import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Individual representations of the code snippets that will be executed by the plugins.
 * @author brian.li
 *
 */
@XMLClass
public class Snippet extends AbstractXmlObject {

    private static final long serialVersionUID = -6544295058228931071L;

    /**
     * List of script paths that are relative from the plugin root folder 
     */
    private List<String> scripts;

    /**
     * List of style sheet paths that are relative from the plugin root folder
     */
    private List<String> styleSheets;

    /**
     * SPRight required for this Snippet to run
     */
    private String rightRequired;

    /**
     * A regex pattern for the pages that this Snippet that will be run on
     */
    private String regexPattern;

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getScripts() {
        return scripts;
    }
    public void setScripts(List<String> scripts) {
        this.scripts = scripts;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getStyleSheets() {
        return styleSheets;
    }
    public void setStyleSheets(List<String> styleSheets) {
        this.styleSheets = styleSheets;
    }

    @XMLProperty
    public String getRightRequired() {
        return rightRequired;
    }
    public void setRightRequired(String rightRequired) {
        this.rightRequired = rightRequired;
    }

    @XMLProperty
    public String getRegexPattern() {
        return regexPattern;
    }
    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }
}
