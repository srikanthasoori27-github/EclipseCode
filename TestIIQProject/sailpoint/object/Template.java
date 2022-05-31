/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object describing a collection of data values that may be
 * calculated or gathered interactively.
 *
 * Jeff
 *
 * Templates are used primarily to calculate account attributes that
 * are to be included in provisioning requests to an application when
 * roles are assigned.   They are usually necessary when creating new accounts
 * to supply initial values for account attributes that are not managed
 * by the role.
 * 
 * The design should however be general enough for use as a data
 * gathering mechanism for other things. There is some similarity with
 * Signature but usage was different enough that I didn't want to confuse
 * the two.
 * 
 * Templates are currnetly used in two classes:
 *
 *   Application
 *     - defines attributes required for account creation, update,
 *       and deletion
 *
 *   Bundle
 *     - defines attributes necessary for provisining the role
 *       in cases where the role definition is ambiguous
 *
 * 
 * PERMISSIONS
 *
 * It's relatively obvious how to handle account attributes in a template,
 * but Permission lists are harder.
 *
 * How would we build a UI to prompt for permissions?  Would the
 * targets be fixed and we prompt for the rights, or do we need to 
 * prompt for both.
 *
 * Modeling Permission as a Field whose name is the target and whose
 * value are the rights is one approach, but what if we can't know
 * all of the potential targets in the template?  
 *
 * What if Permission annotations are important?  
 *
 * It seems unlikely that anyone will be typing in target/right/annotation
 * values, more likely they would use an abstract selector that 
 * mapped to a List<Permission>.
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sailpoint.provisioning.TemplateFieldIterator;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class Template extends AbstractXmlObject
{
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Value that can be used in the owner definition to reference
     * the owner of the object containing this template (role or application).
     */
    public static final String OWNER_PARENT = "IIQParentOwner";

    /**
     * Value that can be used in the owner definition to reference
     * the owner of the role containing this template.
     * This is what the template editor currently sets.
     */
    public static final String OWNER_ROLE = "IIQRoleOwner";

    /**
     * Value that can be used in the owner definition to reference
     * the owner of the application associated with this template.
     */
    public static final String OWNER_APPLICATION = "IIQApplicationOwner";

    /**
     * An enumeration of usage constants for for application templates.
     * 
     * @ignore
     * These are not used for role templates though we will eventually
     * need to make a distinction between assignment and deassignment
     * templates.  It is convenient to use the same enumeration for both
     * and it's easier to extend if we use templates somewhere else.  But
     * it's a bit ugly because templates used in one context will 
     * only allow a subset of the enumeration.
     */
    @XMLClass(xmlname = "usage")
    public static enum Usage {

        Create,
        Update,
        Delete,
        Assign,
        Deassign,
        
        // A quick note about the following usages:
        // Groups, Registration, and Identity policies are not persisted as Templates,
        // but since we don't have a proper Form editor we use a repurposed
        // Template editor that converts them to Templates as an interim step.  
        // That is why they have usages.
        @Deprecated
        CreateGroup,
        @Deprecated
        UpdateGroup,
        @Deprecated
        DeleteGroup,
        Register,
        CreateIdentity,
        UpdateIdentity,
        
        // jsl - temporary backward compatibility, once all
        // Applications created for 6.0 development have been upgraded
        // this must be removed
        @Deprecated
        EditGroup,

        Enable,
        Disable,
        Unlock,
        ChangePassword;

        public String getMessageKey() { 
           return "template_usage_" + Util.splitCamelCase(this.name()).replace(" ","_").toLowerCase();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Templates have a name but what that means is not defined.
     * 
     * For templates in an Application this could be used to associate
     * them with a Schema but this should not be the same
     * as the Schema.objectType. Several template variants
     * for the same object type might be desirable.
     * 
     * For templates in a Bundle this could be used to reference
     * an Application but again maybe the name should be used
     * for provisioning variants rather using it for application
     * association.
     *
     * In current practice the name will be null.
     */
    String _name;

    /**
     * Templates used in applications have a usage qualifier.
     * This indicates whether the template applies to the creation of
     * new accounts, update of existing accounts, or deletion of accounts.
     * This is recognized by the plan compiler when selecting which
     * template to expand.
     *
     * For backward compatibility, if this is null Create is assumed
     * for application templates and Assignment is assumed for role templates.
     */
    Usage _usage;

    /**
     * For role templates, when true it indicates that the template fields
     * are to be merged with the profile rather than replacing it.
     */
    boolean _merge;

    /**
     * Arbitrary text to describe what the template does.
     */
    String _description;

    /**
     * Reference to the associated application for scoping names.
     * This is null for role templates.
     */
    Application _application;

    /**
     * Optional identifier to associate this template with 
     * another object.  
     * 
     * @ignore
     * As always it is difficult to pick a term that
     * isn't being overloaded like the obvious "scope" or "domain".
     * Explanations started using "purview" which we'll continue here,
     * even if it does make me giggle.
     *
     * UPDATE: Since either IIQ or an Application is always targeted
     * it is more consistent to just have an Application reference like
     * is used in most other places. Keep this around until the 
     * template editor can be changed.
     */
    String _purview;

    /**
     * Various ways to determine the owner.
     * 
     * The return value of the DynamicValue is expected to be an Identity
     * or a String. String is allowed to be an abstract name that does not
     * correspond to an Identity object. Only the name is stored in the
     * Question object when the templates are compiled.
     *
     * This definition applies to all Fields in the template unless the
     * Field has an overriding definition. It will only be called by
     * PlanCompiler when building the list of Question objects to be
     * presented. It is not used in inline workflow forms.
     */
    DynamicValue _owner;

    /**
     * Definition of template data fields.
     */
    List<Field> _fields;

    /**
     * Form reference contained by template
     */
    FormRef _formRef;

    /**
     * Beginning for 6.4, we need to capture the schema object type of a template as well.
     */
    private String schemaObjectType;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Template() {
    }

    public void load() {

        if (_fields != null)  {
            for (Field field : _fields)
                field.load();
        }

        if (_application != null) {
            // just enough to get the name
            _application.getName();
        }
            
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setName(String s) {
        _name = s;
    }

    public String getName() {
        return _name;
    }

    @XMLProperty
    public void setUsage(Usage usage) {
        // auto upgrade
        if (usage == Usage.EditGroup) usage = Usage.UpdateGroup;
        _usage = usage;
    }

    public Usage getUsage() {
        return _usage;
    }

    @XMLProperty
    public void setMerge(boolean merge) {
        _merge = merge;
    }

    public boolean isMerge() {
        return _merge;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setDescription(String s) {
        _description = s;
    }

    public String getDescription() {
        return _description;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application a) {
        _application = a;
    }

    @XMLProperty
    public void setPurview(String s) {
        _purview = s;
    }

    public String getPurview() {
        return _purview;
    }

    @Deprecated
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<Field> getFields() {
        return _fields;
    }

    /**
     * Resolve fields using the resolver
     * @param resolver pass the SailpointContext to resolve
     * @return List of Field objects
     */
    public List<Field> getFields(Resolver resolver) {
        if(this.getFormRef() != null) {
            List<Field> frmFields = new ArrayList<Field>();
            TemplateFieldIterator fieldIterator = new TemplateFieldIterator(this, resolver);
            while(fieldIterator.hasNext()) {
                frmFields.add(fieldIterator.next());
            }
            return frmFields;
        }
        else {
            return _fields;
        }
    }

    /**
     * Returns a list of FormItem objects including fields and/or FormRef
     * @return a list of FormItem objects including fields and/or FormRef
     */
    public List<FormItem> getFormItems() {
        List<FormItem> templateItems = null;
        if(_fields != null)
            templateItems = new ArrayList<FormItem>(_fields);
        else
            templateItems = new ArrayList<FormItem>();
        if(_formRef != null) {
            templateItems.add(_formRef);
        }
        return templateItems;
    }

    public void setFields(List<Field> fields) {
        _fields = fields;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public FormRef getFormRef() {
        return _formRef;
    }

    public void setFormRef(FormRef frmRef) {
        _formRef = frmRef;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setOwnerDefinition(DynamicValue def) {
        _owner = def;
    }

    public DynamicValue getOwnerDefinition() {
        return _owner;
    }

    @XMLProperty
    public String getSchemaObjectType() {
        return schemaObjectType;
    }

    public void setSchemaObjectType(String val) {
        schemaObjectType = val;
    }

    // 
    // Temporary backward compatibility with the original OwnerScript property.
    // Should only be used now for 5.0 demos, can drop soon.
    //
    //
    /**
     * @deprecated use {@link #setOwnerDefinition(DynamicValue)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    @XMLProperty(mode=SerializationMode.INLINE,xmlname="OwnerScript")
    public void setOwnerScriptXml(Script s) {
        setOwnerScript(s);
    }

    /**
     * @deprecated use {@link #getOwnerDefinition()} ()} 
     */
    @Deprecated
    public Script getOwnerScriptXml() {
        return null;
    }

    /**
     * @deprecated use {@link #setOwnerDefinition(DynamicValue)}
     */
    @Deprecated
    public void setOwnerScript(Script s) {
        if (s != null)
            _owner = new DynamicValue(null, s, null);
    }
    
    public Script getOwnerScript() {

        return (_owner != null) ? _owner.getScript() : null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Form to Template Conversion
    // Temporary until the form editor is upgraded to only deal with Forms.
    //
    //////////////////////////////////////////////////////////////////////

    static public List<Template> convertForms(List<Form> forms) {
        List<Template> templates = null;
        for (Form f : Util.iterate(forms)) {
            if (templates == null)
                templates = new ArrayList<Template>();
            templates.add(new Template(f));
        }
        return templates;
    }

    public Template(Form src) {

        _name = src.getName();
        _description = src.getDescription();

        _usage = convertFormType(src.getType());
        schemaObjectType = src.getObjectType();
        
        setApplication(src.getApplication());
        setMerge(src.isMergeProfiles());
        setOwnerDefinition(src.getOwnerDefinition());

        // flatten back to a single field list if there is hierarchy
        List<Field> fields = new ArrayList<Field>();
        Iterator<Field> it = src.iterateFields();
        while (it.hasNext()) {
            fields.add(it.next());
        }
        setFields(fields);

        // this is converted from something in the item list back to a property
        setFormRef(src.getFormRef());
    }
    
    private Usage convertFormType(Form.Type type) {

        Usage usage = null;
        if (type != null) {
            switch (type) {
            case Create:
                usage = Usage.Create;
                break;
            case Update:
                usage = Usage.Update;
                break;
            case Delete:
                usage = Usage.Delete;
                break;
            case Enable:
                usage = Usage.Enable;
                break;
            case Disable:
                usage = Usage.Disable;
                break;
            case Unlock:
                usage = Usage.Unlock;
                break;
            case ChangePassword:
                usage = Usage.ChangePassword;
                break;
            case AssignRole:
                usage = Usage.Assign;
                break;
            case DeassignRole:
                usage = Usage.Deassign;
                break;
                
            // not sure if all of these are necessary but be symetrical with Form
            case RegisterIdentity:
                usage = Usage.Register;
                break;
            case CreateIdentity:
                usage = Usage.CreateIdentity;
                break;
            case UpdateIdentity:
                usage = Usage.UpdateIdentity;
                break;
            }
        }

        return usage;
    }
    
    
}
