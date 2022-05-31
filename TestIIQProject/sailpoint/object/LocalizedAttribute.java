/**
 * 
 */
package sailpoint.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * This class represents an attribute which has been localized into a specific language. For example, for an attribute
 * description there will be multiple localized attribute objects with the same targetId, but different locales 
 * representing the various languages the system may support.
 * @author peter.holcomb
 *
 */
@XMLClass
public class LocalizedAttribute extends SailPointObject {

    private static final Log log = LogFactory.getLog(LocalizedAttribute.class);


    /** Convenience method for looking the objects up in debug/console **/
    private String name;

    private String targetId;

    private String locale;

    private String attribute;

    private String value;
    
    private String targetClass;
    
    private String targetName;

    /**
     * Default constructor
     */
    public LocalizedAttribute() {

    }

    /* (non-Javadoc)
     * @see sailpoint.object.SailPointObject#visit(sailpoint.object.Visitor)
     */
    public void visit(Visitor v) throws GeneralException {
        v.visitLocalizedAttribute(this);
    }

    /**
     * Return the targetId under which this attribute lives, usually an object id
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * Set the target ID. 
     * @see #getTargetId()
     */
    @XMLProperty
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    /**
     * Return the locale of the value
     */
    public String getLocale() {
        return locale;
    }

    /**
     * Set the Locale. 
     * @see #getLocale()
     */
    @XMLProperty
    public void setLocale(String locale) {
        this.locale = locale;
    }

    /**
     * Return the attribute that we are localizing
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * Set the attribute. 
     * @see #getAttribute()
     */
    @XMLProperty
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * Return the localized value of the attribute
     */
    public String getValue() {
        return value;
    }

    /**
     * Set the value. 
     * @see #getValue()
     */
    @XMLProperty
    public void setValue(String value) {
        this.value = value;
    }

    /* (non-Javadoc)
     * @see sailpoint.object.SailPointObject#hasAssignedScope()
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    /**
     * Return the class of the object that the attribute represents -- important for export/import
     */
    public String getTargetClass() {
        return targetClass;
    }

    /**
     * Set the target class. 
     * @see #getTargetClass()
     */
    @XMLProperty
    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    /**
     * Return the name of the object that the attribute represents -- important for export/import
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Set the target name. 
     * @see #getTargetName()
     */
    @XMLProperty
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }
}
