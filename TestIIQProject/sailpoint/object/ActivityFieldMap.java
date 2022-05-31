/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ActivityFieldMap extends AbstractXmlObject {


    // Prefix these fields with SP to avoid accidental collisions.
    @XMLClass(xmlname="ActivityField")
    public static enum ActivityField {
        SP_Action,
        SP_Info,
        SP_NativeUserId,
        SP_Result,
        SP_TimeStamp,
        SP_Target;
    }

    /**
     * Which of the fields are mapping this value ?
     */
    private String _mapsTo;

    /**
     * The name of this field, it is important because this is how
     * the parsed value of this group is referenced inside
     * scripts and rules.
     */
    private ActivityField _field;

    /**
     * Enumeration which specifies the types of transformations that can
     * be used for a field.
     *  None : No transformation, use the raw value
     *  Rule : Rule a rule to transform the value
     *  DateConverter : Use a specialized date converter
     *  Script : Some script value
     */
    @XMLClass(xmlname="TransformationType")
    public static enum TransformationType {
        DateConverter,
        None,
        Rule,
        Script;
    }

    /**
     * When transforming the value, which type of transformation
     * should be executed.
     */
    private TransformationType _transformationType;

    /**
     * If TransformationType.Script, this field will contain the script
     * that should be executed during.
     */
    private Script _script;

    /**
     * If TransformationType.DateConverter, this field contains the name
     * format of the field. Using the syntax from java.text.SimpleDateFormat
     * that will be used to create a date object. This should include the
     * Timezone, so the time can be converted and stored as GMT.
     */
    private String _dateFormat;

    /**
     * If TransformationType.DateConverter, this field will contain the name
     * string version of the timezone. This is used in cases where the
     * native application does not store timezone, because its assumed relative
     * to the server date.  
     */
    private String _timeZone;

    private Rule _rule;
    
    /**
     * Create a default ActivityFieldMap which creates an empty object
     * and Defaults both TransformationType and ActivityField to None.
     */
    public ActivityFieldMap() {
        super();
        _transformationType = TransformationType.None;
    }

    /**
     * When transforming the value, which type of transformation
     * should be executed.
     */
    @XMLProperty
    public TransformationType getTransformationType() {
        return _transformationType;
    }

    public void setTransformationType(TransformationType type) {
        _transformationType = type;
    }

    /**
     * The name of this field, it is important because this is how
     * the parsed value of this group will be referenced inside
     * scripts and rules.
     */
    @XMLProperty
    public ActivityField getField() {
        return _field;
    }

    public void setField(ActivityField field) {
        _field = field;
    }

    /**
     * The name of the source field.
     */
    @XMLProperty
    public String getSource() {
        return _mapsTo;
    }

    public void setSource(String field) {
        _mapsTo = field;
    }

    /**
     * If TransformationType.DateConverter, this field will contain the name
     * format of the field. Using the syntax from java.text.SimpleDateFormat
     * that will be used to create a date object. This should include the
     * Timezone, so the time can be converted and stored as GMT.
     */
    @XMLProperty
    public String getDateFormat() {
        return _dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        _dateFormat = dateFormat;
    }
    
    /**
     * If TransformationType.DateConverter, this field will contain the name
     * string version of the timezone. This is used in cases where the
     * native application does not store timezone, because its assumed relative
     * to the server date.  
     */
    @XMLProperty
    public String getTimeZone() {
        return _timeZone;
    }

    public void setTimeZone(String timeZone) {
        _timeZone = timeZone;
    }
    
    /**
     * If TransformationType.Script, this field will contain the script
     * that should be executed during mapping.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Script getScript() {
        return _script;
    }

    public void setScript(Script script) {
        _script = script;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public Rule getRule() {
        return _rule;
    }

    public void setRule(Rule rule) {
        _rule = rule;
    }
}
