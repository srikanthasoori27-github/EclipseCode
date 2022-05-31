/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object describing a data value managed by a Template or Form.
 *
 * Jeff
 *
 * These are almost identical to Argument but I wanted to keep them
 * distinct so they can grown in different directions without
 * confusing each other.  Consider factoring out a common superclass
 * if we ever have UIs for editing both.  It might even evolve such
 * that Argument just subclasses Field.
 *
 * Most of the definition comes from BaseAttributeDefinition.
 * 
 * SCRIPT/RULE EVALUATION CONTEXT
 *
 * The Scripts, Rules, and DynamicValues found in the Field and Template models
 * can be evaluated in several contexts so it is important that the script 
 * understands what input variables it can expect and which are optional.
 *
 * First a summary of the scripts in the Field model:
 *
 *   Template.owner (DynamicValue)
 *     This is the default way to determine the owner of fields in 
 *     the template.
 *
 *   Field.owner (DynamicValue)
 *     Field-specific owner logic, overrides the owner from the template.
 *
 *   Field.value (Rule or Script)
 *     Calculate the static value of this field.
 *
 *   Field.allowsedValues (DynamicValue)
 *     Calculates the list of allowed values for this field.
 *
 *   Field.validation (Rule or Script)
 *     Called after form submission to see if the entered field value 
 *     is acceptable
 *
 * There are three classes that will evaluate field scripts:
 *  
 *    PlanCompiler
 *    Formicator
 *    FormBean
 *
 * PlanCopmiler is responsible most of the time.  It is called during
 * plan compilation to expand templates (aka provisioning policies) defined
 * on the role or application when new accounts are created.  It will
 * run the owner and value scripts.
 *
 * Formicator is responsible some of the time.  It is called
 * when forms are assembled by combining a skeleton Form with the
 * unanswered question fields that were calculated during plan compilation.
 * It will run the value and allowedValues scripts.  The value
 * scripts are only run for fields from the skeleton form, they will not
 * be run for fields in the app/role provisioniong templates.
 *
 * FormBean will evaluate the allowedValues sript, but only if the special
 * "dynamicAllowedvalues" flag is on, which means that we have to keep
 * reevaluating the script in response to fields entered on the form.
 *
 * FormBean also evaluates the validation script or rule.
 *
 * The standard arguments for the scripts are:
 *
 * Template.owner, Field.owner (only called by PlanCompiler)
 *
 *   identity
 *     - identity for which the plan is being compiled
 *   accountRequest
 *     - the AccountRequest from the plan being compiled
 *   role
 *     - role that owns template (null if this is an app template)
 *   application
 *     - applicaton that owns the template (null if this is a role template)
 *   template
 *     - this object
 *   field
 *     - the field being compiled
 *
 *   fieldValues (assimilated Map)
 *     - not a Beanshell symbol, rather the names and values of all 
 *       of the previous fields in this form will be included as inputs
 *
 *   scriptArgs (assimilated Map)
 *     - When called from a workflow this will have the Step arguments.
 *       Unfortunately this is unreliable because Provisioner is used in may
 *       places outside workflows.  
 * 
 * Field.value (when called by PlanCompiler)
 *
 *   The same as Field.owner plus:
 *
 *   current
 *     - the current value of the corresponding attribute from the 
 *       Identity or Link
 *   operation
 *     - the operation of the AccountRequest (set, add, remove)
 *
 * Field.value (when called by Formicator)
 *
 *   This context is only used for fields in skeleton provisioning forms,
 *   or for inline workflow forms.
 * 
 *   identity
 *     - identity for whom the form is being compiled
 *       Set by IdentityLibrary only  when Formicator is called from a workflow
 *   form
 *     - the Form skeleton object being assembled (set by Formicator)
 *   field
 *     - the field from the Form being assembled (set by Formicator)
 *
 *   fieldValues (assimilated Map)
 *     - not a Beanshell symbol, rather the names and values of all 
 *       of the previous fields in this form will be included as inputs
 *
 *   args (assimilated Map)
 *     - When called from a workflow, this will have all of the workflow
 *       variables.  Note that this is different than value scripts 
 *       evaluated by the PlanCompiler which only get the Step arguments!
 *
 * Field.allowedValues (when called by Formicator)
 *
 *   Called only if the allowedValuesDynamic flag is OFF.
 *   Same context as Field.value (Formicdator).
 *
 *
 * Field.allowedValues (when called by FormBean)
 *
 *   Called only if the allowedValuesDynamic flag is ON.
 *
 *   identity
 *     - the target identity derived from the WorkItem if possible, 
 *       might not be set if this is an inline worklfow form
 *   form
 *     - the Form from the work item
 *   field
 *     - the Field from the Form being refreshed
 *   workItem
 *     - the WorkItem that is managing the Form
 *   args (assimilated Map)
 *     - all WorkItem variables
 *
 * 
 * Field.validation (only called by FormBean)
 *
 *   Same as Field.allowedValues(when called by FormBean) plus:

 *   value
 *     - the new value for the field
 * 
 * NOTE WELL
 *
 * Provisioner and PlanCompiler are called from many different places,
 * often to simulate provisioning.  In these places workflow
 * variables will NOT be present so the scripts cannot depend on them.
 * 
 * Among the places that use PlanCompiler:
 *    
 *    RoleDifferencer.getProvisioningItems
 *      This uses the PlanCompiler directly to simulate provisioning.
 *      _scriptArgs will be null
 *
 *    Identitizer
 *      During refresh, this builds a Provisioner and calls 
 *      compileAssignmentProject.  _scriptArgs will be all of the arguments
 *      passed to the refresh task.  If the Identitizer was created outside 
 *      the refresh task the arguments _scriptArgs will normally have
 *      the refresh options but nothing of interesting to field scripts.
 * 
 *    RoleMetadator.execute
 *      Simulates provisioning by compiling a role assignment plan.
 *      _scriptArgs only has few compilation options, nothign for fields.
 *
 *    Wonderer
 *      Calls Provisioner.impactAnalysis to simulate provisioning.
 *      _srciptArgs will be null.
 *
 *    RemediationManager.getPlanSummary
 *      Calls Provisioner.compile then itemizes, then pulls out plan 
 *      slices for a given tracking id. 
 *      _scriptArgs will be null.
 *
 *    ArmWorkflowHandler
 *      Calls Provisioner.process and reconcile.
 *      _scriptArgs are null.
 *
 *    RoleChangeProvisioner (task)
 *      Calls Provisioner.compile with null _scriptArgs.  This arguably
 *      should support forms but it isn't a workflow and doesn't launch them.
 *
 *    ManualCorrelationBean
 *      Compiles a plan to move links around.  Might hit fields if the target
 *      Identity has no Link but that shouldn't happen.
 *
 *    IdentityLibrary.simulateProvisioning
 *      Calls Provisioner.impactAnalysis, _scriptArgs are null
 *
 *    IdentityLibrary.handleRoleEvent
 *      Called to handle a role sunrise/sunset event.  Compiles and executes
 *      the plan but *DOES NOT PRESENT FORMS*.  This is a flaw in whatever
 *      workflow is calling this.  
 *      _scriptArgs will have just the Step arguments, not all the workflow
 *      variables.  Currently these are: identity, role, detected(boolean)
 *      but it doesn't really matter because the Questions won't be presented.
 *      
 * 
 */

package sailpoint.object;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class Field extends BaseAttributeDefinition
                   implements FormItem
{
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(Field.class);
    
    /**
     * An attribute that holds a DynamicValue indicating whether this field
     * is hidden.
     */
    public static final String ATTR_HIDDEN = "hidden";
    
    /**
     * Used in ComboBox
     * Records that are not in the Store via the Proxy
     * This allows IdentityIQ to add certain records to a store that would not normally
     * be there based on the Proxy Filters
     */
    public static final String ATTR_EXTRA_RECS = "extraRecords";

    
    /**
     * An attribute that holds a DynamicValue indicating whether this field
     * is read only.
     */
    public static final String ATTR_READ_ONLY = "readOnly";

    /**
     * Indicates that this field is stored as a csv string, rather than a list.
     */
    public static final String FORMAT_CSV = "csv";


    /**
     * Display type properties
     *
     * - textarea : forces a single-valued text field to be a textarea
     * - radio : forces a multi-valued field to use radio buttons regardless of size.
     * - combobox : forces a multi-valued field to a combobox  regardless of size.
     * - checkbox : forces a multi-valued field to a series of checkboxes.
     */
    public static final String DISPLAY_TYPE_TEXTAREA = "textarea";
    public static final String DISPLAY_TYPE_RADIO = "radio";
    public static final String DISPLAY_TYPE_COMBOBOX = "combobox";
    public static final String DISPLAY_TYPE_CHECKBOX = "checkbox";
    public static final String DISPLAY_TYPE_LABEL = "label";

    // Boolean - indicates that this field requires both the date and time
    public static final String RENDER_SHOW_TIME = "showDateTime";

    // Boolean - indicates that this field is the end date
    // in a date range calculation. The selected date should
    // be the end of the day chosen
    public static final String RENDER_END_DATE = "endDate";

    /**
     * Boolean - indicates that this field should always use a combobox
     * rather than a radio
     * @deprecated Use displayType, setting value to Field.DISPLAY_TYPE_COMBOBOX
     */
    public static final String RENDER_USE_SELECT_BOX = "useSelectBox";

    // Name of the object property that should be used to set the
    // value of the field. By default the object ID will be used
    public static final String ATTR_VALUE_PROPERTY = "valueProperty";

    // Most common value for ATTR_VALUE_PROPERTY - name
    public static final String ATTR_VALUE_PROPERTY_NAME = "name";

    public static final String ATTR_VALUE_OBJ_COLUMN = "objectColumn";

    /**
     * The name of a field that when used in account creation templates
     * will specify the native identity of the new account. This is
     * recognized by the PlanCompiler and set as the nativeIdentity in the
     * AccountRequest rather than a normal AttributeRequest.
     */
    public static final String ACCOUNT_ID = "accountId";

    /**
     * The name of an attribute used to hold another Map containing
     * arguments that will be passed into the evaluation of
     * dynamic scripts in this field.
     */
    public static final String ATT_SCRIPT_ARGS = "scriptArguments";
    
    /**
     * Argument to specify ManagedAttribute. MAs cannot be treated
     *  as normal SailPoint Objects because they do not contain name.
     * There is a special case to deal with MA's as the Field Type.
     * jsl - Unlike the one in PropertyInfo this one does not have
     * a sailpoint.object prefix.  Have to maintain that until we sort
     * out this mess.
     */
    public static final String TYPE_MANAGED_ATTR = "ManagedAttribute";

    /**
     * Argument to specify Application
     */
    public static final String TYPE_APPLICATION_ATTR = "Application";

    /**
     * Argument to specify Role
     */
    public static final String TYPE_ROLE_ATTR = "Bundle";

    /**
     * The name of an attribute that holds the ProvisioningPlan.ObjectOperation
     * name if this field was generated from an update policy where
     * the request was something other than op=Modify, such as op=Enable, 
     * op=Disable, or op=Lock. The scripts might need to change behavior
     * based on this.
     */
    public static final String ATT_OPERATION = "operation";

    /**
     * A field attribute that controls whether this field will be included
     * during provisioning policy template expansion.  
     */
    public static final String ATT_IGNORED = "ignored";

    /**
     * This will be the value of drop downs which need to show null.
     * To explicitly make sure that the value in db is null
     * this is what the value from the UI should be set to.
     */
    public static final String NULL_CONST = "*null*";
    
    public static final String OWNER_REQUESTER = "IIQRequester";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Things that we duplicate from Argument
    //

    /**
     * Key into our spHelp message catalog for tooltip help text.
     * This is optional and typically only used when a form
     * is being automatically generated from the signature.
     */
    String _helpKey;

    /**
     * For fields where _type is the name of a SailPointObject
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

    //
    // Things that aren't in Argument
    //

    /**
     * True if this represents a Permission rather than an attribute.
     * The name is the permission target and the value are the rights.
     *
     * It is uncertain if this will be kept but something needs
     * to let Templates take the place of ProvisioningPlans in 
     * applications and roles.
     */
    boolean _permission;
    
    /**
     * True if the field should be shown to the user for review
     * even if it is non-null.
     */
    boolean _reviewRequired;

    /**
     * True if the value calculated for this field should
     * completely replace the current value rather than be merged
     * with it. This is relevant only for multi-valued attributes.
     */
    boolean _authoritative;

    /**
     * Script to calculate the value.
     * This takes precedence over _defaultValue if both are set.
     * When used in Bundle or Application provisioning templates,
     * the following global symbols are set:
     *
     *    identity - the Identity being processed
     *    role - the Bundle being assigned
     *    application - the Application whose account is being created
     *    template - the Template object from the role or application
     *    field - the Field from the template with this script
     *    current - the current value in the identity cube
     *    deprovisioning - true if deprovisioning is being done 
     *
     */
    Script _script;
    
    /**
     * Rule to calculate the value.
     * This has priority over the Script if both are set.
     * This is called with the same symbols as the script.
     */
    Rule _rule;

    /**
     * Optional form section.  If specified fields in the same section
     * will be grouped together during form compilation.
     */
    String _section;

    /**
     * True if the value of this field sortable. Relevant only for 
     *  multi-valued attributes.
     */
    boolean _sortable;

    /**
     * Additional info about how the value of this field is stored.
     * For example, for multi-valued items is the value stored as a list or csv.
     */
    String _format;

    /**
     * A number that influences the ordering of fields when
     * assembled into a form for presentation. The default is zero
     * which means the form assembler is free to put it anywhere,
     * normally they will be appended to the section.
     *
     * If a number is set, the form assembler will put fields
     * with a lower number above those with a higher number.
     * 
     * Best practices are still being developed here, but one
     * built-in rule is that the plan compiler will assign 
     * a default priority of 10 to any fields that come from an
     * account creation template. In the typical case this will
     * make creation fields appear above fields that came from
     * role templates which is usually what you want. 
     *
     * Start by considering the range 1-9 for special system use, 
     * the range 10-19 for account creation templates and 20+ 
     * for role templates. These are simply guidelines, templates
     * writers are free to violate them.
     */
    int _priority;

    /**
     * Validation script for this Field.
     *
     * Script inputs:
     * form: The field's parent form
     * field: The field to validation with current value unchanged
     * value: The new value to be validated
     *
     * outputs:
     * List<String> error messages. May be message keys.
     *  - or -
     * List<Message> error message objects
     *
     */
    Script _validationScript;

    /**
     * Validation rule for this field.
     * This is an alternative to using a validation script in case you need
     * to share the logic in more than one field.
     */
    Rule _validationRule;

    /**
     * Field attributes. When the Field configuration json is passed to the
     * client, all attributes that do not conflict with a Field
     * member('id', 'name', etc) will be added to the configuration json.
     */

    /**
     * Extended attributes that can influence the renderer.
     */
    Attributes<String, Object> _attributes;

    /**
     * Dynamic value to define the owner of the field.
     * Overrides what is in the template if both are set.
     * This is relevant only during template (provisioning policy)
     * compilation and used by the PlanCompiler to determine the identity
     * to whom this field should be presented. It is not used in 
     * inline workflow forms.
     */
    DynamicValue _owner;

    /**
     * Dynamic value to specify the allowed values.
     * Note that this overrides the inherited _allowedValues from
     * BaseAttributeDefinition.
     */
    DynamicValue _allowedValuesDef;
    
    /**
     * Indicates that the field is dynamic. In 5.5 this option effects
     * the behavior of the allowed values script, causing it to run
     * each time one of the dependency fields changes. When
     * this field is rendered on the form, it is built using a datasource
     * that calls back to the FormBean to build the allowed values list
     * based on the value of other field components.
     * 
     * After 5.5 this will extend to also control the evaluation 
     * of the value script as well.
     */
    boolean _dynamic;

    /**
     * A list of fields that are referenced by the scripts in this field.
     * Specifying a dependency list ensures that the other fields are evaluated
     * before this one and that the values for those fields will be accessible
     * as top-level Beanshell variables in the scripts within this field.
     * The value is a CSV, field names must therefore not contain commas.
     */
    String _dependencies;
    
    /**
     * This serves as a reference to a schema attribute contained on a 
     * separate Application. The value will be a direct copy of the application's
     * schema attribute value unless a script is specified to transform this value. 
     */
    ApplicationDependency _appDependency;
    
    /**
     * Used to modify the way a particular base field type
     * is displayed. See the DISPLAY_TYPE_* statics defined
     * on this class.
     */
    String _displayType;

    /**
     * Determines how many columns a field should occupy when using
     * a multi-columned form
     */
    private int columnSpan;

    /**
     * If true the form will be submitted whenever the value of this
     * fields value changes. This allows the Form to be updated server-side
     * when something changes.
     */
    boolean _postBack;

    //
    // Internal Properties
    // These are not part of the model editable in the UI, they are 
    // maintained by the system during dynamic form assembly
    //

    /**
     * Set to prevent this field from being rendered.
     * This is intended for use by Formicator when it detects that fields
     * do not have dependencies met. Think more about conditional
     * rendering, this could also be done with an attribute but
     * dependency checking feels more fundamental.
     */
    boolean _incomplete;

    /**
     * When fields are assembled from templates (provisioning policies)
     * This will have the name of the template. Currently template names
     * are not well defined, but in the future multiple templates might be allowed
     * to be selected for different purposes. Rules called from the fields
     * might need to know which template they are being used in.
     */
    String _template;

    /**
     * When Fields are assembled from a role template, this has the name of
     * the role. Rules in this field can use this to adjust their behavior.
     */
    String _role;

    /**
     * When Fields are assembled from a role or application template
     * this has the name of the application that is associated with this field.
     * Rules in this field can use the name to adjust their behavior.
     *
     * If this field came from a role template, then _sourceRole will be set.
     * If this field came from an application template, then _sourceRole will be null.
     * This may be necessary to tell the difference between a role template
     * and an application template.
     */
    String _application;
    
    
   
    /**
     * The original unqualified name of this field.
     */
    //String _originalName;

    /**
     * During form interaction, fields will carry their current values
     * rather than  being references to another Map. 
     * This makes things a little easier initially since there is only
     * one object to deal with for both rendering and value
     * assimilation.  
     *
     * With this, the inherited defaultValue property is a little strange, 
     * but they mean different things. defaultValue is the initial
     * value but the current value might be different. 
     * It is unlikely that both the initial and current value will be needed.
     */
    Object _value;

    /**
     * Previous values if applicable. In some cases when editing
     * it is convenient to show the new and old values. This previous values
     * field is used to hold the old values. This is used when building
     * approval forms from a provisioning plan.
     *
     * This is also used when the field is declared "dynamic" to hold
     * the value of the last execution of the value script. This is
     * used to detect manual user edits which prevent further execution
     * of the script.
     *
     * @ignore
     * jsl - I'm not sure we're actually displaying the old value
     * in the plan approval form, if we are then that may start showing
     * when we display dynamic fields which we dont' want.  Could handle
     * this with yet another _lastDynamicValue property or make the form
     * renderer not display _previousValue if the field is dynamic (which
     * it won't ever be on plan approval forms).
     */ 
    Object _previousValue;
    
    /**
     * The transient calculated value for the readOnly property. This can
     * either be set programatically by calling setReadOnly() or will be
     * set during form expansion by evaluating the getReadOnlyDefinition()
     * value. If getReadOnlyDefinition() returns a non-null value, this
     * takes precedence.
     */
    boolean _readOnly;
    
    /**
     * The transient calculated value for the hidden property. This can
     * either be set programatically by calling setHidden() or will be
     * set during form expansion by evaluating the getHiddenDefinition()
     * value. If getHiddenDefinition() returns a non-null value, this
     * takes precedence.
     */
    boolean _hidden;

    /**
     * This is just to keep track of which field is being modified.
     * Some History:
     * This is a transient property here in addition to in the field dto because
     * when identity policy is saved
     * 1. first there is a commit from formDto to form
     * 2. then there is a commit from template to destination form.
     * Now if template loses the originalName it is impossible commit to the destination
     * form.
     */
    transient String _oringalName;
    
    /**
     * Whether this field is blocked from passing on to the connector.
     * It should probably be called 'transient' but cannot call this 
     * field 'transient' because it is reserved.
     *
     * By default all attributes are passed.
     */
    boolean _displayOnly = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    
    public Field() {
    }

    public void load() {

        if (_rule != null)
            _rule.load();
        
        if (_allowedValuesDef != null)
            _allowedValuesDef.load();
        
        if (_owner != null)
            _owner.load();
        
        if (_validationRule != null)
            _validationRule.load();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
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

    public String getDisplayType() {
        return _displayType;
    }

    @XMLProperty
    public void setDisplayType(String displayType) {
        this._displayType = displayType;
    }

    public boolean isPostBack() {
        return _postBack;
    }

    @XMLProperty
    public void setPostBack(boolean postBack) {
        this._postBack = postBack;
    }

    @XMLProperty
    public void setFilterString(String s) {
        _filter = s;
    }

    /**
     * For fields where <code>type</code> is the name of a <code>SailPointObject</code>
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
    public void setPermission(boolean b) {
        _permission = b;
    }

    /**
     * True if this field represents a permission rather than an account 
     * attribute. The name is the permission target and the value
     * are the rights.
     */
    public boolean isPermission() {
        return _permission;
    }
    
    @XMLProperty
    public void setReviewRequired(boolean required) {
        _reviewRequired = required;
    }

    /**
     * True if the field should be presented to the user for
     * approval even if there is already a value.
     */
    public boolean isReviewRequired() {
        return _reviewRequired;
    }


    @XMLProperty
    public void setAuthoritative(boolean b) {
        _authoritative = b;
    }

    /**
     * True if this field is considered authoritative over the 
     * corresponding identity or account attribute value. This means
     * that the current value will be completely replaced with the 
     * field value rather than being merged with the field value.
     */
    public boolean isAuthoritative() {
        return _authoritative;
    }

    @XMLProperty
    public void setSection(String s) {
        _section = s;
    }

    public String getSection() {
        return _section;
    }

    @XMLProperty
    public String getFormat() {
        return _format;
    }

    public void setFormat(String format) {
        _format = format;
    }

    @XMLProperty
    public boolean isSortable() {
        return _sortable;
    }

    public void setSortable(boolean sortable) {
        _sortable = sortable;
    }

    @XMLProperty
    public int getPriority() {
        return _priority;
    }

    public void setPriority(int p) {
        _priority = p;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this._attributes = attributes;
    }

    public void setAttribute(String attribute, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();

        if (null != value) {
            _attributes.put(attribute, value);
        }
        else {
            // Keep it clean!
            _attributes.remove(attribute);
            if (_attributes.isEmpty()) {
                _attributes = null;
            }
        }
    }

    // sigh, this is the old name, nothing else has this but I'm 
    // not sure who is calling it now
    public void addAttribute(String name, Object value) {
        setAttribute(name, value);
    }

    public Object getAttribute(String attribute){
        if (_attributes == null)
            return null;
        return _attributes.get(attribute);
    }
    
    public String getStringAttribute(String name) {
        return Util.otos(getAttribute(name));
    }
    
    public boolean getBooleanAttribute(String name) {
        
        return Util.otob(getAttribute(name));
    }
    
    /**
     * Return a DynamicValue for the requested attribute in the attributes map.
     * Note that this returns a DynamicValue even if the value in the map is
     * just a literal.
     */
    private DynamicValue getAttributeDynamicValue(String attribute,
                                                  Resolver resolver)
        throws GeneralException {
        return DynamicValue.get(_attributes, attribute, resolver, false);
    }

    /**
     * Set the given DynamicValue as a scriptlet in the attributes map.
     */
    public void setAttributeDynamicValue(String attribute, DynamicValue dv) {
        _attributes = DynamicValue.set(_attributes, attribute, dv);
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public Script getValidationScript() {
        return _validationScript;
    }

    public void setValidationScript(Script validationScript) {
        this._validationScript = validationScript;
    }


    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getValidationRule() {
        return _validationRule;
    }

    public void setValidationRule(Rule rule) {
        _validationRule = rule;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
    public void setAllowedValuesDefinition(DynamicValue def) {
        _allowedValuesDef = def;
    }

    public DynamicValue getAllowedValuesDefinition() {
        return _allowedValuesDef;
    }

    public void setReadOnlyDefinition(DynamicValue readOnly) {
        setAttributeDynamicValue(ATTR_READ_ONLY, readOnly);
    }

    public DynamicValue getReadOnlyDefinition(Resolver r) throws GeneralException {
        return getAttributeDynamicValue(ATTR_READ_ONLY, r);
    }

    public void setHiddenDefinition(DynamicValue hidden) {
        setAttributeDynamicValue(ATTR_HIDDEN, hidden);
    }

    public DynamicValue getHiddenDefinition(Resolver r) throws GeneralException {
        return getAttributeDynamicValue(ATTR_HIDDEN, r);
    }

    /**
     * Return whether this field is read only. This should only be called on
     * expanded fields, otherwise use getReadOnlyDefinition().
     */
    @XMLProperty
    public boolean isReadOnly() {
        // Note that this is an XML property for two reasons:
        //  1) We want to support legacy field XML that used this attribute.
        //  2) Expanded forms that are serialized as XML need to retain this.
        return _readOnly;
    }
    
    /**
     * Set whether this field is read only. This can be set programatically
     * but will get overridden during form expansion if there is a non-null
     * readOnlyDefinition. On an expanded form, this holds the evaluation
     * results of the readOnlyDefinition.
     */
    public void setReadOnly(boolean readOnly) {
        _readOnly = readOnly;
    }

    /**
     * Return whether this field is hidden. This should only be called on
     * expanded fields, otherwise use getHiddenDefinition().
     */
    @XMLProperty
    public boolean isHidden() {
        // Note that this is an XML property for two reasons:
        //  1) We want to support legacy field XML that used this attribute.
        //  2) Expanded forms that are serialized as XML need to retain this.
        return _hidden;
    }
    
    /**
     * Set whether this field is hidden. This can be set programatically
     * but will get overridden during form expansion if there is a non-null
     * hiddenDefinition. On an expanded form, this holds the evaluation
     * results of the hiddenDefinition.
     */
    public void setHidden(boolean hidden) {
        _hidden = hidden;
    }

    public int getColumnSpan() {
        return columnSpan;
    }

    @XMLProperty
    public void setColumnSpan(int columnSpan) {
        this.columnSpan = columnSpan;
    }

    //////////////////////////////////////////////////////////////////////
    //  
    // Value
    //
    // This one is funny because we'd like to use DynamicValue but 
    // I don't want th extra level of XML indirection. e.g. this
    //
    //    <Field name='foo'>
    //      <ValueDefinition value='bar'/>
    //
    // rather than this which is what everyone expects:
    //
    //     <Field name='foo' value='bar'/>
    // 
    // We don't have a SerializationMode that will merge the attributes
    // and elements in with the *parent* element.  That might be useful
    // but kind of spooky.  For now explode the three fields but supply
    // accessors to make it look like DynamicValue if we want to.
    //
    // This is a classic example of why multiple inheritance is sometimes
    // good.  Sigh, not happy with this...
    //
    //////////////////////////////////////////////////////////////////////
    
    public void setValue(Object value) {
        _value = value;
    }

    public Object getValue() {
        return _value;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="value")
    public String getValueXmlAttribute() {
        return (_value instanceof String) ? (String)_value : null;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #setValue(Object)} 
     */
    @Deprecated
    public void setValueXmlAttribute(String value) {
        _value = value;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    @XMLProperty(xmlname="Value")
    public Object getValueXmlElement() {
        return (_value instanceof String) ? null : _value;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #setValue(Object)}
     */
    @Deprecated
    public void setValueXmlElement(Object value) {
        _value = value;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setScript(Script s) {
        _script = s;
    }

    public Script getScript() {
        return _script;
    }
        
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public void setRule(Rule rule) {
        _rule = rule;
    }

    public Rule getRule() {
        return _rule;
    }

    // Peter's original name...
    public void setFieldRule(Rule rule) {
        setRule(rule);
    }

    public Rule getFieldRule() {
        return getRule();
    }

    public DynamicValue getValueDefinition() {

        // for literal values support both _value and also _defaultValue
        // we're supposed to be using _value now, but the template editor
        // will still be setting _defaultValue for a while
        // NOTE: For a time the template editor was leaving
        // empty strings in the default value so be sure to filter
        // those.  I can't think of a reason why empty string should
        // be allowed.

        Object literal = Util.nullify(_value);
        if (literal == null)
            literal = Util.nullify(getDefaultValue());

        return new DynamicValue(_rule, _script, literal);
    }

    public void setValueDefinition(DynamicValue dv) {
        if (dv == null) {
            _rule = null;
            _script = null;
            _value = null;
        }
        else {
            _rule = dv.getRule();
            _script = dv.getScript();
            _value = dv.getValue();
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //  
    // previousValue
    // Like defaultValue and value this has hackery to make the XML cleaner
    //
    //////////////////////////////////////////////////////////////////////

    public void setPreviousValue(Object value) {
        _previousValue = value;
    }

    public Object getPreviousValue() {
        return _previousValue;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #getPreviousValue()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="previousValue")
    public String getPreviousValueXmlAttribute() {
        return (_previousValue instanceof String) ? (String)_previousValue: null;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #setPreviousValue(Object)} ()}
     */
    @Deprecated
    public void setPreviousValueXmlAttribute(String value) {
        _previousValue = value;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #getPreviousValue()}
     */
    @Deprecated
    @XMLProperty(xmlname="PreviousValue")
    public Object getPreviousValueXmlElement() {
        return (_previousValue instanceof String) ? null : _previousValue;
    }

    /**
     * @exclude
     * Required for Hibernate 
     * @deprecated use {@link #setPreviousValue(Object)} 
     */
    @Deprecated
    public void setPreviousValueXmlElement(Object value) {
        _previousValue = value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Owner
    //
    // Like Template we're now defining owner with a DynamicValue but
    // we've still got the old OwnerScript property to maintain for awhile.
    // Should only be used now for 5.0 demos, can drop soon.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @deprecated
     */
    @XMLProperty(mode=SerializationMode.INLINE)
    public void setOwnerDefinition(DynamicValue def) {
        _owner = def;
    }
    /**
     * @deprecated
     */
    public DynamicValue getOwnerDefinition() {
        return _owner;
    }

    /**
     * @deprecated 
     */
    @XMLProperty(mode=SerializationMode.INLINE,xmlname="OwnerScript")
    public void setOwnerScriptXml(Script s) {
        setOwnerScript(s);
    }

    /**
     * @deprecated
     */
    public Script getOwnerScriptXml() {
        return null;
    }

    /**
     * @deprecated
     */
    public void setOwnerScript(Script s) {
        if (s != null)
            _owner = new DynamicValue(null, s, null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Dynamic
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public boolean isDynamic() {
        return _dynamic;
    }

    public void setDynamic(boolean b) {
        _dynamic = b;
    }

    /**
     * @deprecated - use {@link #isDynamic()}
     * Old name for "dynamic", eventually this will apply to things
     * other than the allowed values script so it needed a more generic name
     */
    public boolean isAllowedValuesDynamic() {
        return isDynamic();
    }

    /**
     * @deprecated - use {@link #setDynamic(boolean)}
     */
    public void setAllowedValuesDynamic(boolean b) {
        setDynamic(b);
    }

    /**
     * XML "upgrader" for the old allowedValuesDynamic attribute.
     * @deprecated
     */
    @XMLProperty(xmlname="allowedValuesDynamic")
    public boolean isXmlAllowedValuesDynamic() {
        return false;
    }

    /**
     * @deprecated - use {@link #setDynamic(boolean)}
     */
    public void setXmlAllowedValuesDynamic(boolean b) {
        setDynamic(b);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Dependencies
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getDependencies() {
        return _dependencies;
    }

    public void setDependencies(String s) {
        _dependencies = s;
    }
    
    public List<String> getDependencyList() {
        List<String> list = null;
        if (_dependencies != null)
            list = Util.csvToList(_dependencies);
        return list;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // System Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.INLINE)
    public ApplicationDependency getAppDependency() {
        return _appDependency;
    }

    public void setAppDependency(ApplicationDependency _appDependency) {
        this._appDependency = _appDependency;
    }
    
    
    public void setAppDependency(String appName, String schemaAtt) {
        setAppDependency(new ApplicationDependency(appName, schemaAtt));
    }

    @XMLProperty
    public void setIncomplete(boolean b) {
        _incomplete = b;
    }

    public boolean isIncomplete() {
        return _incomplete;
    }

    @XMLProperty
    public void setTemplate(String s) {
        _template = s;
    }

    public String getTemplate() {
        return _template;
    }

    @XMLProperty
    public void setRole(String s) {
        _role = s;
    }

    public String getRole() {
        return _role;
    }

    @XMLProperty
    public void setApplication(String s) {
        _application = s;
    }

    public String getApplication() {
        return _application;
    }

    /**
     * Return the prefix that was added to the field name to disambiguate
     * it in a form assembled from compiled templates. Currently
     * this will always be the application name.
     */
    public String getPrefix() {
        return _application;
    }

    /*
    @XMLProperty
    public void setOriginalName(String s) {
        _originalName = s;
    }

    public String getOriginalName() {
        return _originalName;
    }
    */

    
    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the arguments for dynamic script evaluation.
     */
    public Map<String,Object> getScriptArguments() {
        Map<String,Object> args = null;
        Object o = getAttribute(ATT_SCRIPT_ARGS);
        if (o instanceof Map)
            args = (Map<String,Object>)o;
        return args;
    }

    public void setScriptArguments(Map<String,Object> args) {
        if (args != null)
            setAttribute(ATT_SCRIPT_ARGS, args);
        else {
            // avoid clutter
            if (_attributes != null)
                _attributes.remove(ATT_SCRIPT_ARGS);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////
        
    /**
     * Get the field type normalizing all sailpoint.object class references
     * to have a package prefix.  Intended for use only in the UI layer.
     *
     * @ignore
     * Stupid transformation we have to do because people have not been consistent about
     * using sailpoint.object class names in Fields.
     *
     * For unfortunate reasons, the type of an Identity suggest has been sailpoint.object.Identity,
     * the prefix is unnecessary and no one writing a form manually remembers it.
     *
     * The ManagedAttribute type is just "ManagedAttribute". We can probably change that to be
     * consistent but we'll have to track down all uses of Field.TYPE_MANAGED_ATTR.
     *
     * Until this mess is sorted out we'll consistently use the package prefix in the UI but transform
     * it when we commit the DTO.
     * jsl
     */
    public String getNormalizedType() {
        String type = _type;
        if (TYPE_MANAGED_ATTR.equals(type)) {
            type = PropertyInfo.TYPE_MANAGED_ATTRIBUTE;
        } else if (TYPE_APPLICATION_ATTR.equals(type)) {
            type = PropertyInfo.TYPE_APPLICATION;
        } else if (TYPE_ROLE_ATTR.equals(type)) {
            type = PropertyInfo.TYPE_ROLE;
        }

        return type;
    }

    public void setNormalizedType(String type) {
        if (PropertyInfo.TYPE_MANAGED_ATTRIBUTE.equals(type)) {
            type = TYPE_MANAGED_ATTR;
        } else if (PropertyInfo.TYPE_APPLICATION.equals(type)) {
            type = TYPE_APPLICATION_ATTR;
        } else if (PropertyInfo.TYPE_ROLE.equals(type)) {
            type = TYPE_ROLE_ATTR;
        }

        _type = type;
    }

    /**
     * Gets the normalized field type.  See above for why this is here.
     */

    /**
     * Return the name of the field without template qualification.
     * This might be necessary for fields that were assembled from templates
     * (provisioning policies) on roles or applications. The 
     * qualified name is generated by PlanCompiler. It does not matter what
     * that prefix means here, only that it be separated by a colon.
     */
    public String getUnqualifiedName() {
        String uname = _name;
        if (uname != null) {
            int colon = uname.lastIndexOf(":");
            if (colon > 0 && (colon < (uname.length() - 1)))
                uname = uname.substring(colon + 1);
        }
        return uname;
    }

    /**
     * Format a qualified name.
     * here just so the rules for creating and parsing
     * the qualified name syntax can be in one place.
     */
    public String qualifyName(String prefix, String name) {
        String qname = name;
        if (prefix != null)
            qname = prefix + ":" + name;
        return qname;
    }

    /**
     * Return the class of the type if the type references a SailPoint object.
     * 
     * @ignore
     * ManagedAttribute special because it has no name attribute,   
     * use use the ManagedAttributesResource to fetch these.
     * This is currently only encountered for Model Backed Forms.
     */
    public Class getTypeClass() {

        Class cls = null;

        if (_type != null && !_type.equals("ManagedAttribute")) {

            // ObjectUtil.getSailPointClass will only resolve
            // to classes that are in ClassLists.MajorClasses.
            // Not sure why but allow searching for a few more.
            // jsl - why?
            if (_type.equals("Link"))
                cls = Link.class;

            else if (_type.equals("Tag"))
                cls = Tag.class;

            else if (Field.TYPE_IDENTITY.equals(getType())) {
                // jsl - why is this here, it's on the MajorClasses list?
                // so ObjectUtil should handle it?
                cls = Identity.class;
            }
            else 
                cls = ObjectUtil.getSailPointClass(getType());
        }

        return cls;
    }

    /**
     * @ignore
     * For a short time we were calling this "prompt" before I realized
     * that BaseAttributeDefinition.displayName is effectively the
     * same thing.  I prefer prompt though, would these even need to
     * be different?
     */
    public String getPrompt() {
        return _displayName; 
    }
    
    public void setPrompt(String s) {
        _displayName = s;
    }

    /**
     * Return the label to be used when prompting for this field.
     * Assume the description string will not be displayed since they are
     * intended to be long
     * 
     * If the name is being returned, strip off the 'Application Name:'
     * that is appended to it.
     */
    public String getDisplayLabel() {
        if(_displayName==null && _name!=null) {
            if(_name.indexOf(":")>0) {
                String[] parts = _name.split(":");
                String fieldName = parts[parts.length-1];
                return fieldName;
            } else {
                return _name;
            }
        }
        return _displayName;
    }

    /**
     * Return true if a value is on the allowed values list.
     * The structure of the allowed values list is either a List of values
     * or a List<List> where the first element of the inner list is the value
     * and the second is the display name.
     *
     * This does not run the allowed values rule or script, it is expected
     * to be called after those have been run and the result stored here.
     */
    public boolean isAllowed(Object value) {

        boolean allowed = false;

        if (_allowedValues == null || _allowedValues.size() == 0) {
            // nothing means everything
            allowed = true;
        }
        else {
            for (Object el : _allowedValues) {
                Object possible = el;
                if (el instanceof List) {
                    possible = null;
                    List list = (List)el;
                    if (list.size() > 0) 
                        possible = list.get(0);
                }

                if (getTypeClass() != null) {
                    if (value.equals(possible)) {
                        allowed = true;
                        break;
                    }
                } else {
                    //bug#16655: if this is not sailpoint object type, 
                    //conevrt the allowed value first, then compare
                    try {
                        Object convertedAllowedValue = convertSimpleTypeValue(getType(), possible, null);
                        if (value.equals(convertedAllowedValue)) {
                            allowed = true;
                            break;
                        }
                    } catch (Exception e) {
                        return false;
                    }

                }
            }
        }

        return allowed;
    }

    /**
     * These fields are only used by DTOs.
     * They should not be saved in the object model.
     */
    public String getOriginalName() {
        return _oringalName;
    }
    
    public void setOriginalName(String val) {
        _oringalName = val;
    }
    
    @XMLProperty
    public boolean isDisplayOnly() {
        return _displayOnly;
    }
    
    public void setDisplayOnly(boolean val) {
        _displayOnly = val;
    }

    public boolean equals(Object o)
    {
        boolean eq = false;
        
        if (this == o)
            eq = true;
        
        else if (this.getClass().isInstance(o)) {
            Field fo = (Field) o;

            if (getName() != null && fo.getName() != null)
                eq = getName().equals(fo.getName());
        }

        return eq;
    }
    
    public static Object convertSimpleTypeValue(String type, Object value) throws Exception {
        return convertSimpleTypeValue(type, value, null);
    }

    /**
     * Converts a single value into the appropriate type.
     * It only supports primitive types and date.
     * It does not support sailpoint object types.
     * If the type is not defined, then the original object is returned.
     * This can be used in converting field value or field allowed value.
     *
     * @param type  the class type to be converted to
     * @param value the object value to be converted
     * @return the converted value object
     * @throws Exception  format exception
     */
    public static Object convertSimpleTypeValue(String type, Object value, TimeZone userTimeZone) throws Exception {
        if (type == null || value == null) {
            return value;
        }

        Object newValue = value;
        if (Field.TYPE_DATE.equals(type)) {
            if (!(value instanceof Date)) {
                newValue = new Date(Util.atol(Util.otos(value)));
            }
        }
        else if (Field.TYPE_BOOLEAN.equals(type)) {
            // note that this is stricter than Util.otob
            newValue = Boolean.parseBoolean(value.toString());
        }
        else if (Field.TYPE_LONG.equals(type)) {
            newValue = Long.parseLong(value.toString());
        }
        else if (Field.TYPE_INT.equals(type)) {
            newValue = Integer.parseInt(value.toString());
        }
        return newValue;
    }
    
    @XMLClass
    public static class ApplicationDependency {
        String _applicationName;
        String _schemaAttributeName;
        
        public ApplicationDependency(){
            
        }
        
        public ApplicationDependency(String appName, String schemaAtt) {
            this._applicationName = appName;
            this._schemaAttributeName = schemaAtt;
        }
        
        @XMLProperty
        public String getApplicationName() {
            return _applicationName;
        }
        
        public void setApplicationName(String _applicationName) {
            this._applicationName = _applicationName;
        }
        
        @XMLProperty
        public String getSchemaAttributeName() {
            return _schemaAttributeName;
        }
        
        public void setSchemaAttributeName(String _schemaAttributeName) {
            this._schemaAttributeName = _schemaAttributeName;
        }
        
    }
}
