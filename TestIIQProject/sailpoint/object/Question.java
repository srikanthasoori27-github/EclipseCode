/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * An object representing a data value that needs to be requested,
 * typically through interactive prompting.  This is part of the 
 * ProvisioningProject model, used to represent account attributes
 * that are required to complete a provisioning event.
 *
 * Author: Jeff
 *
 * I originally wanted to let the Field just be the definition and to 
 * have the posted field value stored in Question, but it was more convenient
 * to let Field be self contained and have the answer as well as the field
 * definition.  This makes Question less useful but I want to keep it around
 * for awhile in case we need to carry metadata about the answer
 * (who answered, who reviewed, comments, etc.) so we can avoid bloating
 * up Field.
 *
 * UPDATE: This is a convenient place to store the "owner" that was calculated
 * from scripts on the Template or Field.  Compilation of a provisioning
 * project may result in Questions for more than one order, Questions
 * for the same owner are combined into a Form by Provisioner.
 * 
 */

package sailpoint.object;

import java.util.Comparator;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object representing a data value that needs to be requested,
 * typically through interactive prompting. This is part of the 
 * ProvisioningProject model, used to represent account attributes
 * that are required to complete a provisioning event.
 */
@XMLClass
public class Question extends AbstractXmlObject
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Field defining how to prompt for the question.
     */
    Field _field;

    /**
     * The owner of the question. If set this is usually the name of
     * an Identity object but it can be an abstract name like "System".
     * If questions have no owner, they are presented in the first form.
     */
    String _owner;

    /**
     * True once the field has been shown. This is a done to 
     * keep reviewRequired fields from showing only once and then stopping
     * them so forms do not keep generating to review fields.
     * Need to replace this with a more flexible way to control what gets
     * shown and what triggers a form.
     */
    boolean _shown;

    // TODO: who gave the answer, when was the answer given, 
    // does the answer need review, etc
    
    /*
     * Question types can be of application or role depending on which
     * Template from which we originated
     */
    @XMLClass(xmlname="QuestionType")
    public enum Type
    {
        Application("Application"), 
        Role("Role");
        
        private String messageKey;

        private Type(String messageKey)
        {
            this.messageKey = messageKey;
        }

        public String getMessageKey()
        {
            return this.messageKey;
        }
    };
    
    public static class QuestionTypeComparator implements Comparator<Question.Type>
    {
        public int compare(Question.Type t1, Question.Type t2)
        {
            String msg1 = t1.getMessageKey();
            String msg2 = t2.getMessageKey();
            return msg1.compareTo(msg2);
        }
    };
    
    // Our type
    Type _type;

    // Name of the role or application
    String _source;
    
    // Native id where for which the question is being asked
    String _target;

    //Native id this question will provide
    String _futureTarget;
    
    // Name of the attribute that we are asking about
    String _attributeName;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Question() {
    }

    public Question(Field f) {
        _field = f;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Field getField() {
        return _field;
    }

    public void setField(Field f) {
        _field = f;
    }

    @XMLProperty
    public String getOwner() {
        return _owner;
    }

    public void setOwner(String s) {
        _owner = s;
    }

    @XMLProperty
    public boolean isShown() {
        return _shown;
    }

    public void setShown(boolean b) {
        _shown = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public String getFieldName() {

        return (_field != null) ? _field.getName() : null;
    }

    public Object getAnswer() {

        return (_field != null) ? _field.getValue() : null;
    }

    public void setAnswer(Object o) {
        if (_field != null)
            _field.setValue(o);
    }

    public void setPreviousValue(Object o) {
        if (_field != null)
            _field.setPreviousValue(o);
    }

    @XMLProperty
    public Type getType() {
        return _type;
    }

    public void setType(Type _type) {
        this._type = _type;
    }

    @XMLProperty
    public String getSource() {
        return _source;
    }

    public void setSource(String _source) {
        this._source = _source;
    }

    @XMLProperty
    public String getTarget() {
        return _target;
    }

    public void setTarget(String target) {
        this._target = target;
    }

    @XMLProperty
    public String getFutureTarget() {
        return _futureTarget;
    }

    public void setFutureTarget(String target) {
        this._futureTarget = target;
    }

    @XMLProperty
    public String getAttributeName() {
        return _attributeName;
    }

    public void setAttributeName(String attributeName) {
        this._attributeName = attributeName;
    }
    
    public boolean fieldsMatch(Question other) {
        boolean matches = false;
        Field peField = other.getField();
        Field f = this._field;
        if (peField != null && Util.nullSafeCaseInsensitiveEq(peField.getApplication(), f.getApplication())) {
            String peName = peField.getName();
            String fieldName = f.getName();
            if (peName != null && fieldName != null) {
                if (peName.regionMatches(true, peName.lastIndexOf(":"), fieldName, fieldName.lastIndexOf(":"), peName.length() - fieldName.lastIndexOf(":"))) {
                    matches = true;
                }
            }
        }
        
        return matches;
    }

}
