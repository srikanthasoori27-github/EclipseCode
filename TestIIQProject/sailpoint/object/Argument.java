/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A subcomponent of the Signtaure model, an object describing the arguments
 * and return values of an abstract function.
 *
 * Author: Jeff
 * 
 * This is growing more UI rendering semantics, primarily for task signatures.
 *  
 * This is also now used as the base class for Workflow$Variable so be
 * careful what you put in here.
 * 
 */

package sailpoint.object;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A subcomponent of the Signature model, an object describing the arguments
 * and return values of an abstract function.
 */
@XMLClass
public class Argument extends BaseAttributeDefinition
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For signatures used to generate UIs, this is the name displayed
     * next to the value input field.  If it is null _name is used.
     */
    String _prompt;

    /**
     * Key into our spHelp message catalog for tooltip help text.
     * This is optional and typically only used when a form
     * is being automatically generated from the signature.
     */
    String _helpKey;

    /**
     * For arguments where _type is the name of a SailPointObject
     * subclass, this can be set to restrict the set of possible objects.
     * This is used mostly for Rule so you can display pick lists
     * of rules of a certain type.
     */
    String _filter;

    /**
     * Path to an xhtml template used to generate the UI input when a form
     * is being created from a signature.
     */
    String _inputTemplate;


    /**
     * Name of the section this argument belongs to. Used if the
     * arguments will be converted into a Form object.
     */
    String _section;

    /**
     * True if this argument should be hidden from the arguments form on the Task editor;
     * false otherwise.  This is false in most cases.
     */
    boolean _hidden;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Argument() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setPrompt(String s) {
        _prompt = s;
    }

    /**
     * For signatures used to generate UIs, this is the name displayed
     * next to the value input field.  If it is null <code>name</code> is used.
     */
    public String getPrompt() {
        return _prompt;
    }

    @XMLProperty
    public void setHelpKey(String s) {
        _helpKey = s;
    }

    /**
     * Key into the help message catalog for tooltip text.
     */
    public String getHelpKey() {
        return _helpKey;
    }

    @XMLProperty
    public void setFilterString(String s) {
        _filter = s;
    }

    /**
     * For arguments where <code>type</code> is the name of a <code>SailPointObject</code>
     * subclass, this can be set to restrict the set of possible objects.
     */
    public String getFilterString() {
        return _filter;
    }

    @XMLProperty
    public void setInputTemplate(String inputTemplate) {
        _inputTemplate = inputTemplate;
    }

    /**
     * Path to an xhtml template used to generate the UI input when a form
     * is being created from a signature.
     */
    public String getInputTemplate() {
        return _inputTemplate;
    }

    @XMLProperty
    public String getSection() {
        return _section;
    }

    public void setSection(String section) {
        this._section = section;
    }

    @XMLProperty
    public boolean isHidden() {
        return _hidden;
    }

    public void setHidden(boolean hidden) {
        _hidden = hidden;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the label to be used when prompting for this argument
     * or displaying a result.
     *
     * Assume you do not want to display the description string since they are
     * intended to be long, and more appropriate for tooltips.
     */
    public String getDisplayLabel() {

        return (_prompt != null) ? _prompt : _name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        
        if (getClass() != obj.getClass())
            return false;
        
        final Argument other = (Argument) obj;        
        if (_name == null) {
            if (other._name != null)
                return false;
        } else if (!_name.equals(other._name))
            return false;
        
        if (_type == null) {
            if (other._type != null)
                return false;
        } else if (!_type.equals(other._type))
            return false;
        
        if (_filter == null) {
            if (other._filter != null)
                return false;
        } else if (!_filter.equals(other._filter))
            return false;
        
        if (_inputTemplate == null) {
            if (other._inputTemplate != null)
                return false;
        } else if (!_inputTemplate.equals(other._inputTemplate))
            return false;
        
        if (_description == null) {
            if (other._description != null)
                return false;
        } else if (!_description.equals(other._description))
            return false;
        
        if (_prompt == null) {
            if (other._prompt != null)
                return false;
        } else if (!_prompt.equals(other._prompt))
            return false;
        
        return true;
    }
}
