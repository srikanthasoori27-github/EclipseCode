/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utilities for the processing of Form objects.
 * 
 * Author: Jeff
 *
 * This is used in severa contexts: provisioning policy compilation
 * and presentation, plan approval forms, and generic workflow forms, 
 *
 * Provisioning Policy Forms (Templates)
 *
 * When compiling provisioning projects we may encounter 
 * "provisioning policies" defined on applications and roles that gather
 * extra information necessary to satisfy the provisioning request.  Internally
 * a provisioning policy is a Template containing Fields.  Some template
 * fields will calculate static value with a script, other fields may
 * need to be presented to an IIQ user for manual value entry.
 *
 * The PlanCompiler will deal with fields that can calculate their own values.
 * Fields that must be presented to a user are gathered into a list
 * of Question objects and left on the ProvisioningProject.  When the
 * request workflow compiles a project and sees that there are Questions,
 * it enters a workflow loop to present one or more IIQ users with work 
 * item forms to gather the information.
 *
 * Formicator is used indirectly by some IdentityLibrary methods
 * to take the Questions out of the ProvisioningProject, organize them
 * by target user, and dynamically build a Form object containing the
 * Fields that need to be presented.  This Form is then placed inside
 * a WorkItem and rendered by WorkflowFormBean.  Formicator is also
 * used to take the results of a completed Form and assimilate the
 * results back into the ProvisioningProject.  
 *
 * When assembling forms from project questions the fields are placed inside
 * a "skeleton" form which defines the structure of the form and may
 * contain static fields that give the user information about what
 * is to be done in the form.  We will copy the skeleton form and 
 * automatically add labeled sections for the fields from the provisioning
 * policies.  Fields from applications will be organized in a section
 * for each application labled with the application name.  Fields from roles
 * will be organized into a section for each role labeled with the
 * role name.  (Really?)  If you want more control over field organization
 * you can define sections in the skeleton form and then refer to those
 * sections in the Fields in the provisioning policy Templates.
 *
 * Plan Approval Forms
 *
 * Currently LCM has an option to allow approvers of a set of
 * attribute requests to not only approve the attributes but modify
 * them.  To do this we convert the ProvisioningPlan that was built by LCM
 * into a Form, then present the form to the user.  When the use is finished
 * we assimilate the modified form fields back into the ProvisioningPlan.
 * 
 * Generic Workflow Forms
 *
 * Workflows may use forms to specify what is presented to the user
 * in a WorkItem.  This can be used as an alternative to writing
 * custom JSF renderers.  The Form is placed inside the WorkItem
 * and rendered in the ame way as forms assembled from provisioning policies.
 * The main difference is in the way the form is assembled.
 *
 * There are several ways to define forms in workflows:
 *
 * - A static inline Form object inside the Approval object
 *
 * - A dynamically created Form object left inside a dynamically
 *   created Approval object during the evaluation of the owner script.
 *
 * - A dynamically created Form object left in a workflow variable and
 *   passed into the work item as an Arg or "send" using the
 *   name "workItemForm".
 *
 * Here we don't really care how the form was assembled, we are responsible
 * for "expanding" the form by evaluating scripts, rules, and inline variable
 * references before the form is rendered.  A form may be rendered several
 * times during the lifespan of a work item and we do expansion each time.
 * This allows field scripts to be sensitive to changes made in other
 * fields.
 *
 * The result of form expansion is a copy of the source form with
 * script and rule references replaced by the results of evaluating
 * those scripts.  Because we need to reevaluate the field scripts
 * on each rendering it is important that expansion not modify the source
 * form.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicValue;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Form.Button;
import sailpoint.object.Form.Section;
import sailpoint.object.FormItem;
import sailpoint.object.FormRef;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.PropertyInfo;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Question;
import sailpoint.object.Resolver;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.object.Source;
import sailpoint.service.form.renderer.item.FormItemDTO.FormItemType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.tools.VariableExpander;
import sailpoint.tools.VariableExpander.Token;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
import sailpoint.workflow.IdentityRequestLibrary;

public class Formicator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //  
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(Formicator.class);

    /**
     * The name of the default skeleton form for provisioning questions.
     */
    public static final String FORM_IDENTITY_REFRESH = "Identity Refresh";

    /**
     * The default rule used to validation password fields.
     * This is a bit of a kludge to inject password policy that
     * can be configured outside the form but needs to be applied
     * to any form that asks for passwords.  Doing it here means
     * that the Form designer doesn't have to worry about it and it
     * will be done consistently.
     */
    public static final String CHECK_PASSWORD_POLICY_RULE = "Check Password Policy";

    /**
     * The internal name of the Section we generate to hold assembled
     * Fields that did not specify a section name.
     */
    public static final String DEFAULT_SECTION = "default";


    //
    // Arguments passed to field scripts or rules
    // 

    public static final String ARG_FORM = "form";
    public static final String ARG_SECTION = "section";
    public static final String ARG_FIELD = "field";
    public static final String ARG_VALUE = "value";
    public static final String ARG_CURRENT = "current";
    public static final String ARG_LINK = "link";
    public static final String ARG_APPLICATION = "app";

    /**
     * Special value used with the _fieldNamespaces map below
     * to identify the fields that have no prefix.  It has to be
     * something you would never name an Application or Bundle.
     */
    public static final String NO_PREFIX_KEY = "*NO PREFIX*";

    /**
     * You have to love context.
     */
    private SailPointContext _context;

    /**
     * Cache of application schemas used during form building 
     * from a provisioning plan.
     */
    Map<String,Schema> _schemas;
     
    //
    // Expansion State
    //

    /**
     * Fields that have been visited at least once during expansion.
     */
    Map<String,Field> _visited;

    /**
     * Fields currently on the dependency stack.  This is used for
     * dependency cycle detection.
     */
    Map<String,Field> _stack;

    /**
     * The resulting dependency ordered field list.
     */
    List<Field> _ordered;

    /**
     * Fields we decided to defer until dependencies were met.
     */
    List<Field> _deferred;

    /**
     * Field value maps organized by prefix.  
     * These are used to accumulate field values that can be passed
     * into scripts for other fields without a prefix.  Prefixes are relevant
     * only for fields in provisioning policies where the field name
     * will be prefixed with either the application or role name 
     * in the assembled form.
     */
    Map<String,Map<String,Object>> _fieldNamespaces;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public Formicator(SailPointContext context) {

        _context = context;
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Initialization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Initialize the given form by populating it with values from the data map
     * (if requested) and preparing the form to be expanded.  This will set
     * values on the corresponding fields if they do not yet have a value.
     * 
     * @param  form      The Form to initialize with data.
     * @param  data      The data to use for initialization.
     * @param  populate  Whether to populate the form with values from the data.
     */
    public void initialize(Form form, Map<String,Object> data, boolean populate)
        throws GeneralException {

        resolveFormReferences(form);

        // The "value" property on Field has two uses - 1) to hold the current
        // value of the field, and 2) to hold a short-hand initializer (eg - a
        // script, rule, etc...).  Since the value will get overwritten during
        // expansion and the rendering/posting cycle, we want to promote the
        // short-hand initializers into scripts/rules on the field.
        promoteScriptlets(form);
        
        // Populate the form using the data map if this was requested.
        if ((null != data) && populate) {
            Iterator<Field> it = form.iterateFields();
            while (it.hasNext()) {
                Field field = it.next();
    
                // When we're using a model-backed form, the model is
                // authoritative for the values.  In this case we want to
                // always set the value.  Otherwise, only bind a value if
                // the field doesn't have one yet.
                if (form.hasBasePath() || (field.getValue() == null)) {
                    String name = field.getName();
                    if (name != null) {
                        Object value = null;
                        if (form.hasBasePath()){
                            value = MapUtil.get(data, getFullPath(form, name));
                        } else {
                            value = data.get(name);
                        }
    
                        field.setValue(value);
                        // initialize the previous value for dynamic fields so the field
                        // will be re-evaluated upon expansion.  see Formicator.shouldExpand()
                        if (field.isDynamic()) {
                            field.setPreviousValue(value);
                        }
                    }
                }
            }
        }
    }

    /**
     * Promote any scriplet or variable Field values into actual Script/Rule
     * objects on the fields.  This prevents the scriplets from being
     * overwritten during form population.
     */
    private void promoteScriptlets(Form form) throws GeneralException {
        
        Iterator<Field> fields = form.iterateFields();
        while (fields.hasNext()) {
            Field field = fields.next();
            Object value = field.getValue();
            if (value instanceof String) {
                boolean promoted = false;
                String stringVal = (String) value;
                
                // Scriptlets should be promoted out of the field value into
                // a script or rule.
                if (DynamicValuator.isScriptlet(stringVal)) {
                    Scriptlet scriptlet =
                        new Scriptlet(stringVal, Scriptlet.METHOD_STRING);
                    
                    if (null != scriptlet.getScript()) {
                        promoted = promoteScript(field, scriptlet.getScript());
                    }
                    else if (null != scriptlet.getRule()) {
                        promoted = promoteRule(field, scriptlet.getRule());
                    }
                    else if (null != scriptlet.getReference()) {
                        promoted = promoteReference(field, scriptlet);
                    }
                }

                // If the value contains a $() variable reference, convert it
                // into a script.
                if (smellsVariableish(stringVal)) {
                    promoted = promoteVariable(field, stringVal);
                }

                // If it was promoted, we can clear out the value.
                if (promoted) {
                    field.setValue(null);
                }
            }
        }
    }
    
    /**
     * Promote the given Script to a script on the field.
     */
    private static boolean promoteScript(Field field, Script script) {
        boolean promoted = false;
        if (null != field.getScript()) {
            log.warn("Field " + field.getName() + " has a value " +
                     "script and <Script> - ignoring value script.");
        }
        else {
            field.setScript(script);
            promoted = true;
        }
        return promoted;
    }
    
    /**
     * Promote the given rule to a Rule on the field.
     */
    private boolean promoteRule(Field field, String ruleName)
        throws GeneralException {

        boolean promoted = false;
        if (null != field.getRule()) {
            log.warn("Field " + field.getName() + " has a value " +
                     "rule and <Rule> - ignoring value rule.");
        }
        else {
            Rule rule = _context.getObjectByName(Rule.class, ruleName);
            field.setRule(rule);
            promoted = true;
        }
        return promoted;
    }

    /**
     * Promote the given reference to a Script on the field.
     */
    private static boolean promoteReference(Field field, Scriptlet scriptlet) {
        boolean promoted = false;
        if (null != field.getScript()) {
            log.warn("Field " + field.getName() + " has a reference " +
                     "and <Script> - ignoring reference.");
        }
        else {
            // Create a script that just returns the references variable.  If
            // the expression is negated, add a bang.
            String source =
                "return " + ((scriptlet.isNot()) ? "!" : "") + scriptlet.getReference() + ";";
            Script script = new Script();
            script.setSource(source);
            field.setScript(script);
            promoted = true;
        }
        return promoted;
    }

    /**
     * Promote the given string which contains one or more variables into a
     * Script on the field.
     */
    private static boolean promoteVariable(Field field, String value) {
        boolean promoted = false;
        if (null != field.getScript()) {
            log.warn("Field " + field.getName() + " has a variable expansion " +
                     "and <Script> - ignoring variable expansion.");
        }
        else {
            List<Token> tokens = VariableExpander.parse(value);
            if (!tokens.isEmpty()) {
                StringBuilder source = new StringBuilder();
                source.append("return ");
    
                String sep = "";
                for (Token token : tokens) {
                    source.append(sep);

                    String val = token.getValue();
                    // Add quotes around the value if it is a literal.
                    if (Token.Type.Literal.equals(token.getType())) {
                        source.append("\"").append(val).append("\"");
                    }
                    else if (Token.Type.Reference.equals(token.getType())) {
                        //defect 23172 Ran into an issue where we surround the value with quotes to protect a period in the value.
                        //basically we strip out the quotes and let it get expanded later
                        if(null != val && (val.contains("\"") || val.contains("\'"))){
                            val = "$(" + val.replace("\"", "").replace("\'", "") + ")";
                        }
                        // Variable expander treats null values as empty string.
                        // Like such: ((void == foo) ? "" : foo) 
                        //void is used in beanshell not null
                        source.append("((void == ").append(val).append(")")
                            .append(" ? \"\" : ").append(val).append(")");
                    }
    
                    // Separate the tokens using string concatenation.
                    sep = " + ";
                }
                source.append(";");
    
                Script script = new Script();
                script.setSource(source.toString());
                field.setScript(script);
                promoted = true;
            }
        }
        return promoted;
    }

    /**
     * Return whether the given object smells like it might have a variable
     * hiding inside of it.
     */
    private static boolean smellsVariableish(Object smelly) {
        boolean variableish = false;
        if (smelly instanceof String) {
            String stringy = (String) smelly;
            variableish = (stringy.contains("$(") && stringy.contains(")"));
        }
        return variableish;
    }
    
    /**
     * Copy the field values from the given form into the data map.  This will
     * use paths if the form has a base path.
     * 
     * @param  form  The Form from which to copy the values.
     * @param  data  The map into which to copy the values.
     */
    public void copyFormToData(Form form, Map<String,Object> data)
        throws GeneralException {

        if ((form != null) && (data != null)) {
            Iterator<Field> it = form.iterateFields();
            while (it.hasNext()) {
                Field src = it.next();
                String name = src.getName();
                Object value = src.getValue();

                // Copy information from the form back into the data map.
                copyValueToModel(form, name, value, data);
            }

            // TODO: Expecting buttons to all be at the top
            // will need an iterator once we allow inline buttons
            for (FormItem item : Util.iterate(form.getItems())) {
                if (item instanceof Button) {
                    Button b = (Button)item;
                    if (b.isClicked() && b.getParameter() != null) {
                        data.put(b.getParameter(), b.getValue());
                    }
                }
            }
        }
    }
    
    /**
     * Copy the given value into the given data map.  This will use paths if
     * the form has a base path.
     */
    private static void copyValueToModel(Form form, String name, Object value,
                                         Map<String,Object> data)
        throws GeneralException {

        if ((null != name) && (null != data)) {
            if (form.hasBasePath()){
                MapUtil.put(data, getFullPath(form, name), value);
            } else {
                data.put(name, value);
            }
        }
    }
    
    /**
     * Return the full path for the given name, prepending the base path if
     * the form has one.
     */
    private static String getFullPath(Form form, String name) {
        return (form.hasBasePath()) ? form.getBasePath() + "." + name : name;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Form Reference Resolution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Walk over a Form replacing FormRef items with the contents
     * of the referenced form.  If the referenced form contains buttons,
     * they are moved to the root form.  Button placement will need
     * more work if we ever support the notion of inline or top buttons.
     *
     * This is more general than it needs to be since nested sections and
     * arbitrarily placed FormRefs aren't fully supported in the editor but
     * it isn't hard just to do it the right way now.
     *
     * @ignore
     * KLUDGE: The way this used to work, a Form always had to have
     * a Section to wrap the Fields.  When the FormRef was resolved 
     * the fields would be extracted from the wrapper Section to prevent
     * a gratuitous level of nested section from being added, which we
     * couldn't render.
     *
     * The FormRefs are always in the form item list, hence do never check
     * the FormRefs on the Section wrapper.
     *
     * I want to move toward allowing FormRefs anywhere in the form and allow
     * nested sections. But this conflicts with old Forms that referencing a
     * form that also have Sections.
     *
     * To work around this, we're going to preserve the Sections in the 
     * referenced Form as they are, not unwrap them.  To prevent nested
     * Sections we'll avoid adding the wrapper Section to the root form IF
     * it has no label or other rendering options.  Since the Section would
     * have no behavior it can be removed.  
     */
    public void resolveFormReferences(Form form) throws GeneralException {

        if (form != null) {

            List<FormItem> items = form.getItems();
            List<FormItem> flatButtons = new ArrayList<FormItem>();

            List<FormItem> newItems = resolveFormReferences(items, flatButtons);

            // klduge 2: until the renderer can deal with wrapperless forms,
            // add the Section back if it contains Fields, this would only happen
            // if the referenced forms were wrapperless which can't exist right now
            boolean needsWrapper = false;
            for (FormItem item : Util.iterate(newItems)) {
                if (item instanceof Field) {
                    needsWrapper = true;
                    break;
                }
            }

            if (needsWrapper) {
                Section wrapper = new Section();
                wrapper.setItems(newItems);
                newItems = new ArrayList<FormItem>();
                newItems.add(wrapper);
            }
            
            form.setItems(newItems);

            // position doesn't matter, but keep buttons at the bottom as expected
            form.add(flatButtons);

            if (log.isDebugEnabled()) {
                log.debug("Form after resolving FormRefs - ");
                log.debug(form.toXml());
            }
        }
    }

    /**
     * Recursive method to replace FormRefs with the items in the
     * referenced form, and flatten the list of Buttons.
     *
     */
    private List<FormItem> resolveFormReferences(List<FormItem> items,
                                                 List<FormItem> buttons)
        throws GeneralException {

        List<FormItem> newItems = null;
        if (Util.size(items) > 0) {
            newItems = new ArrayList<FormItem>();
            for (FormItem item : items) {

                if (item instanceof Field) {
                    newItems.add(item);
                }
                else if (item instanceof Section) {
                    Section s = (Section)item;
                    s.setItems(resolveFormReferences(s.getItems(), buttons));
                    newItems.add(s);
                }
                else if (item instanceof Button) {
                    // someday will need to support inline buttons
                    buttons.add(item);
                }
                else if (item instanceof FormRef) {
                    FormRef ref = (FormRef) item;
                    Form form = null;
                    if (null != ref.getId()) {
                        form = _context.getObjectById(Form.class, ref.getId());
                    }
                    else {
                        form = _context.getObjectByName(Form.class, ref.getName());
                    }

                    if (form == null) {
                        // TODO: always throw, or allow it to be silently ignored?
                        throw new ObjectNotFoundException(Form.class, ref.getName());
                    }
                    else {
                        Form formCopy = (Form) form.deepCopy((Resolver)_context);
                        List<FormItem> refItems = resolveFormReferences(formCopy.getItems(), buttons);

                        // TODO: if there are Sections in the list and isWrapper is true
                        // then we can collapse the Section
                        if (refItems != null) {
                            newItems.addAll(refItems);
                        }
                    }
                }
            }
        }
        return newItems;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Expansion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Walk over a form expanding variable references and evaluating scripts.
     * The source form is copied to create the expanded form, however, it may
     * be modified to receive values.
     *
     * This must be called if you are expanding a workflow form
     * since you don't want to lose the scripts each time the 
     * work item form is rendered.  
     */
    public Form expand(Form source, Map<String,Object> args)
        throws GeneralException {

        Form expanded = (Form)source.deepCopy((Resolver)_context);

        resolveFormReferences(expanded);

        expandInner(source, expanded, args, false);

        // We typically want to store this on the HttpSession and use
        // it later to make sure it is fully loaded.  Without this
        // you my bet lazy load exceptions on the referencedRules list
        // of any Rules referenced by the form. 
        expanded.load();

        return expanded;
    }

    /**
     * Inner form expander.  Expansion happens in the passed form,
     * the flag controls whether we expand everything, or just the
     * dynamic field values on the final post.  The master form may
     * be updated with hidden field values, etc...
     */
    private void expandInner(Form master, Form form,
                             Map<String,Object> args, 
                             boolean onlyDynamicValues)
        throws GeneralException {

        // Do field dependencies first since these can contribute
        // to the expansion args for other things.
        // TODO: We'll add field values to expandArgs as we expand them,
        // if they already have values should we add them up front?
        // Technically shouldn't be necessary if we've got dependencies
        // delcared propery, but that would be an easy and convenient 
        // thing to do...
        List<Field> fields = orderFields(form);
        if (fields != null) {
            for (Field field : fields) {
                // in case this was previously expanded clear this
                field.setIncomplete(false);
                
                if (isFieldReady(form, field))
                    expandField(master, form, field, onlyDynamicValues, args);
                else {
                    // mark this so we know the scripts were not run
                    field.setIncomplete(true);
                    
                    // Sections and buttons don't have dependency 
                    // declarations yet, so if they have scripts that reference
                    // fields they must always check for null.
                    // So they dont' have to mess with void checking
                    // put an entry in the arg map for this field.
                    String fieldName = field.getUnqualifiedName();
                    if (fieldName != null) {
                        Map<String,Object> namespace = getFieldNamespace(form, field, args);
                        namespace.put(fieldName, null);
                    }
                }
            }
        }

        // Now do variable expanaion or script evaluation in other form
        // elements. These can't have dependencies, if you $() or
        // script reference something that isn't defined yet it will end
        // up null.  

        if (!onlyDynamicValues) {

            // get the unqualified namespace
            // !! may want to allow <Sections> designed to hold only 
            // one prefix to see the fields with that prefix.  Don't have
            // a reliable way to declare that though.  For now, attribute
            // maps outside Field can only use unprefixed field values
            Map<String,Object> namespace = getFieldNamespace(form, null, args);
            
            // Expand top-level form attributes.
            // Originally we just did this for ATT_PAGE_TITLE, ATT_TITLE, 
            // and ATT_SUBTITLE.  Now we just do any entry with a String value
            // which makes it easier to add things.
            // Note that these can't reference Field values.  That seems 
            // right, but we could defer this until after fields?
            expandAttributes(form.getAttributes(), namespace);

            // section labels and attributes, buttons
            // !! think about this, if the section was created for
            // an Application, then the section should be able to 
            // use unprefixed field names in its scripts!  I don't know
            // how we would recognize that though.  Can we assume that
            // the section name will match the prefix?
            // jsl - eventually support sectionless forms
            expandOtherItems(form.getItems(), namespace);
        
            // remove items that are hidden or intcomplete if options set
            // in the Form
            filterHidden(master, form);
        }
    }

    /**
     * Expand the values of any Strings found in an attributes map.
     * This is used for the att maps at all levels of the form.
     */
    private void expandAttributes(Attributes<String,Object> source,
                                  Map<String,Object> args)
        throws GeneralException {

        if (source != null) {
            Iterator<String> it = source.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                Object value = source.get(key);
                
                // First, evaluate the DynamicValue if there is one.
                DynamicValue dv = DynamicValue.get(source, key, _context);
                if (null != dv) {
                    DynamicValuator valuator = new DynamicValuator(dv);
                    value = valuator.evaluate(_context, args);
                }

                // Next, expand.  Assume only Strings need to get expanded, 
                // may want to allow List<String>?
                if (value instanceof String) {
                    String str = (String)value;
                    value = VariableExpander.expand(str, args);
                }

                source.put(key, value);
            }
        }
    }

    /**
     * Expand Sections and Buttons in an item list.  Fields have
     * already been ordered and expanded.
     */
    private void expandOtherItems(List<FormItem> items, Map<String,Object> args)
        throws GeneralException {

        for (FormItem item : Util.iterate(items)) {
            if (item instanceof Section) {
                expandSection((Section)item, args);
            }
            else if (item instanceof Button) {
                expandButton((Button)item, args);
            }
        }
    }
    
    /**
     * Expand things within a Section.
     */
    private void expandSection(Section section, Map<String,Object> args)
        throws GeneralException {
                                                  
        // let the label have $() references
        // TODO: should let this be a scriptlet too!
        String label = section.getLabel();
        if (label != null) {
            section.setLabel(VariableExpander.expand(label, args));
        }

        // let attributes have $() and DynamicValues
        // these are less useful than the top-level Form attributes
        // but we may want something here for the renderer
        expandAttributes(section.getAttributes(), args);

        // recurse on subsections (rare)
        expandOtherItems(section.getItems(), args);
    }

    /**
     * Expand things in a Button.
     * Don't have anything right now but it would be useful
     * to have control over the button label and especially visibility
     * of the button.   
     */
    private void expandButton(Button button, Map<String,Object> args)
        throws GeneralException {

        // label, action, parameter, value, readOnly
        
        // not sure what goes in here
        expandAttributes(button.getAttributes(), args);
    }

    /**
     * Do variable expansion or script evaluation for a Field in a Form.
     *
     * This is similar to what PlanCompiler does when it expands role
     * or application template fields (see bottom half of getFieldValue).
     * We will normally not have anything to do here for policy fields
     * since they will have been expanded by PlanCompiler and had their
     * scripts removed.
     *
     * !! Think more about how dynamic fields work.  We have so far
     * not evaluated allowed values scripts for dynamic fields here and
     * instead done them in FormBean in an Ajax call.  Need to consolodate
     * this here...
     *
     * Field Values
     *
     * Once a field has a value that sticks. 
     * May want something that forces reevaluation but it would have to
     * be field specific?
     * !! what about dynamic field values?  May need to record the original
     * value so we can tell the difference between an calculated value and
     * a manually entered one?
     *
     * KLUDGE: We need to support scriptlets in literal field values but now
     * that field values are supposed to stick after the first evaluation we
     * have to inspect the value to see if it looks like a scriptlet and
     * evaluate it.  It would be more reliable if fields had an 
     * "evaluated once" flag, but the only danger would be if someone had
     * a value script that actually wanted to return "ref:something" or some 
     * other scriptlet syntax.  That is unlikely.  
     *
     * There are similar issues with $() expansion.
     * 
     * The onlyDynamicValues flag is set during the the final post of the form,
     * we only evalute the dynamid values, nothing else.
     */
    private void expandField(Form master, Form form, Field field,
                             boolean onlyDynamicValues,
                             Map<String,Object> baseArgs)
        throws GeneralException {

        // get the accumulated field namespace
        Map<String,Object> namespace = getFieldNamespace(form, field, baseArgs);

        // add the standard args, and any field specific args
        Map<String,Object> fieldArgs = addFieldArgs(form, field, namespace, false);

        // Value
        // Normally we only evaluate if the value is null or a scriptlet.
        // As of 6.1 if the field is declared dynamic, and the current value
        // is the same as the previous value, then we'll run it again.
        // NOTE: Becaues a null value always runs the script it means that
        // the user will be unable to completely take a way a derived value.
        // May want to allow this?

        Object fieldValue = field.getValue();
        if (!onlyDynamicValues || field.isDynamic()) {
            if ( shouldExpand(field) ) {
                
                // note that Dynamicvalue will prefer scripts over a literal values
                // so don't call this if you want the field value to stick
                DynamicValue dv = field.getValueDefinition();
                if (dv != null) {
                    DynamicValuator valuator = new DynamicValuator(dv);
                    fieldValue = valuator.evaluate(_context, fieldArgs);

                    // nb: value scripts that combine other field values
                    // such as fullname can end up with empty values
                    // that contain only padding spaces.  When this winds
                    // it's way through HTML/Ext and back a string containing
                    // only spaces usually comes back null which we don't
                    // want to treat as a diff.  Trim now.  Could also
                    // make Differencer ignore space strings but this
                    // feels  better.
                    if (fieldValue instanceof String) {
                        String trim = ((String)fieldValue).trim();
                        if (trim.length() == 0)
                            trim = null;
                        fieldValue = trim;
                    }

                    // save the end result and clear out the scripts
                    // !! not any more..need to leave the scripts for form refresh
                    //dv.setFinalValue(fieldValue);
                    //field.setValueDefinition(dv);
                    field.setValue(fieldValue);
                    if (field.isDynamic()) {
                        // save it here for refresh checking, assuming
                        // we dont' need a deep clone, rendering better not
                        // mess with this
                        field.setPreviousValue(fieldValue);
                        
                        // Capture the previous value back in the master form.
                        // Need to do this before potentially removing the
                        // field if it is hidden.
                        captureExpansion(master, field);
                    }
                }
            }
        }

        // Field values are allowed to have variable references too
        // hnm...we can't tell if this is the first time through
        // so we have to do this every time, for consistency
        // we also have to do it after evaluating the script just
        // in case that returns something with $(), otherwise
        // the value will change if you refresh the form once.  May want
        // a field option to disable this if someone has a valid reason
        // to use $() in literal text
        if (fieldValue instanceof String)
            field.setValue(VariableExpander.expand((String)fieldValue, fieldArgs));

        // Accumulate field values in the namespace map so they
        // can be referenced by other fields.  If the field has
        // a prefix remove it.
        String fieldName = field.getUnqualifiedName();
        if (fieldName != null) {
            addFieldValue(fieldName, fieldValue, fieldArgs, namespace);

            // Store this in the data that was passed in, also.
            copyValueToModel(form, fieldName, fieldValue, baseArgs);
        }

        if (!onlyDynamicValues) {

            // AllowedValues
            //
            // The result is left in the _allowedvalues property inherited
            // from BaseAttributeDefinition which is what FormBean looks at.
            // The scripts are left in the field for refresh.  We always overwrite
            // the previously calculated allowed values.

                DynamicValue fdv = field.getAllowedValuesDefinition();
                if (fdv == null) {
                    // if we don't have one of these, support older files
                    // that use <AllowedValues>, just leave _allowedValues alone
                }
                else {
                    DynamicValuator valuator = new DynamicValuator(fdv);
                    Object allowed = valuator.evaluate(_context, fieldArgs);
    
                    if (allowed == null) 
                        field.setAllowedValues(null);
                    else {
                        // TODO: Sanity check the reuslt, should be either
                        // List<String> or List<List<String,String>>
                        List<Object> list = new ArrayList<Object>();
                        if (Collection.class.isAssignableFrom(allowed.getClass()))
                            list.addAll((Collection)allowed);
                        else
                            list.add(allowed);
                        field.setAllowedValues(list);
                    }

                    // fomrerly wiped the scripts, we must to leave them
                    // now since refresh can recalculate the allowed values
                    //field.setAllowedValuesDefinition(null);
                }

            // Owner
            //
            // This is not relevant here, it is only used by PlanCompiler
            // when partitioning the template fields among differnent owners.
            // For forms that are being assembled the owner definition will
            // already have been evalated and should be null.  If we do find
            // a use for this we need to be very careful about presenting
            // a consistent evaluation environment.  It may be better to add
            // a different property.
            DynamicValue dv = field.getOwnerDefinition();
            if (dv != null)
                log.warn("Owner definition in Field is not relevant in this context");

            // If a field has a readOnly scriptlet, this scriptlet wins.
            // Otherwise we will default to what was already in the readOnly
            // field.
            dv = field.getReadOnlyDefinition(_context);
            if (null != dv) {
                DynamicValuator valuator = new DynamicValuator(dv);
                Object readOnly = valuator.evaluate(_context, fieldArgs);
                if (null != readOnly) {
                    field.setReadOnly(Util.otob(readOnly));
                }
                
                // Set this to null after it is evaluated so it gets removed
                // from the attributes map.
                field.setReadOnlyDefinition(null);
            }
            
            // If a field has a hidden scriptlet, this scriptlet wins.
            // Otherwise we will default to what was already in the hidden
            // field.
            dv = field.getHiddenDefinition(_context);
            if (null != dv) {
                DynamicValuator valuator = new DynamicValuator(dv);
                Object hidden = valuator.evaluate(_context, fieldArgs);
                if (null != hidden) {
                    field.setHidden(Util.otob(hidden));
                }

                // Set this to null after it is evaluated so it gets removed
                // from the attributes map.
                field.setHiddenDefinition(null);
            }

            // Expand attributes on field - both doing variable substitution
            // and dynamic value evaluation.
            expandAttributes(field.getAttributes(), fieldArgs);
        }
    }

    /**
     * Called during evaluation to check to see if the 
     * Field requires us to call the evaluator to 
     * calculate the value.
     * <br/>
     * This happens when any one of the following is true:
     * <br/>
     * 1. Field value is null  
     * <br/>
     * 2. Field value is a scriptlet ( if its still a scriptlet it hasn't been evaluated )
     * <br/>
     * 3. Field is dynamic and previous value is null
     * <br/>
     * 4. Field is dynamic and the current and previous values are the same.
     * <br/>
     * 
     * @param field The field to be evaluated
     * 
     * @return true if the field should be expanded
     * 
     */
    public boolean shouldExpand(Field field) {
       
        Object fieldValue = field.getValue();
        
        //Our premise is that unless we meet any condition below, the default is false.
        boolean shouldExpandField = false;
        
        //No reason to calculate each of these conditions - hence the elses.
        if (fieldValue == null) {
            //always evaluate
            shouldExpandField = true;
        } else if (fieldValue instanceof String && DynamicValuator.isScriptlet((String)fieldValue)) {
            //Scriptlets are always evaluated because they come from the fieldValue.
            shouldExpandField = true;
        } else if (field.isDynamic()) {
            // The thought behind this bit of logic is that if a user explicitly enters a 
            // value into the form, we don't want to overwrite it with a value generated 
            // by the rule/script.  This allows for the dynamic behavior, but make the 
            // value that they enter sticky once they enter it, it sticks around forever 
            // unless they null it.
            if ( Differencer.objectsEqual(fieldValue, field.getPreviousValue(), true) )   {
                shouldExpandField = true;
            }
        }
        
        return shouldExpandField;
    }

    /**
     * Get the namespace (args to pass to the script interpreter)
     * for the given field.
     *
     * The base argument map usually comes from either WorkItemFormBean
     * or FormBean which in turn got it from WorkflowSession.getBaseScriptArgs.
     * It will normally contain this:
     *
     *   identity - Identity
     *   workItem - WorkItem object
     *   <workItemArgs> - attributes from the work item
     *
     */
    private Map<String,Object> getFieldNamespace(Form form, Field field, Map<String,Object> baseArgs) {

        // if there is no prefix, use a special key so we can handle it consistently
        String prefix = (field != null) ? field.getPrefix() : null;
        if (prefix == null)
            prefix = NO_PREFIX_KEY;

        if (_fieldNamespaces == null) {
            _fieldNamespaces = new HashMap<String,Map<String,Object>>();
        }

        Map<String,Object> namespace = _fieldNamespaces.get(prefix);
        if (namespace == null) {
            // starts with a copy of the base args
            namespace = new HashMap<String,Object>();
            if (baseArgs != null) {
                namespace.putAll(baseArgs);
            }

            // Add the form modelBasePath to the arguments so ScriptPreParser can do its thing.
            if(form != null) {
                namespace.put(Rule.MODEL_BASE_PATH, form.getBasePath());
            }

            _fieldNamespaces.put(prefix, namespace);
        }

        return namespace;
    }

    /**
     * Add field-specific entries to a namespace.  The namespace will
     * start with the base arguments passed to the expand() method
     * and we then accumulate values of fields as they are evaluated.
     *
     * To this we add the following standard fields:
     *
     *   form - the Form being processed
     *   field - the Field in the form
     *   value - the value of the field
     *   current - the value of the field, backward compatibility
     *
     * If the field had an "extra" map containing dependency values
     * those are added too.  This happens only for policy fields
     * that had a dependency that was satisfied in a previous
     * form presenatation.
     *
     * The pullDependencies flag is false for full expansion
     * and true for Ajax calls like getAllowedValues where we don't
     * expand the entire form.  With full expansion we should
     * already have the dependencies in the Map.  For Ajax calls 
     * we have to go find the Field and pull in the value for just
     * those things we depend on.
     */
    private Map<String,Object> addFieldArgs(Form form, Field field, 
                                            Map<String,Object> namespace,
                                            boolean pullDependencies) {
        
        Map<String,Object> args = namespace;

        // If there field is carrying a map of dependency values, 
        // add those but only if we do not already have an entry 
        // in the namespace.  This ensures that we favor the
        // value of posted fields if they were included in the
        // last form rather than the values cached in the
        // extras list.
        Map<String,Object> extra = field.getScriptArguments();
        if (extra != null) {
            // this is field-specific so have to copy again
            args = new HashMap<String,Object>(namespace);
            Iterator<String> it = extra.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                if (pullDependencies || !args.containsKey(key))
                    args.put(key, extra.get(key));
            }
        }

        // Pre 6.1 we pulled in unprefixed dependencies here, but
        // with the new unprefixed namespaces, we shouldn't need that.  
        // Well yes we do for getAllowedValues, for full expansion should
        // already be there.
        List<String> dependencies = field.getDependencyList();
        if (dependencies != null) {
            for (String depname : dependencies) {
                
                if (!pullDependencies && args.containsKey(depname)) {
                    // full expansion and already have it, this
                    // is the expected case, don't have to do anything
                }
                else {
                    // warn if we're not supposed to pull
                    if (!pullDependencies)
                        log.warn("Missing dependency field value: " + depname);

                    // If this is a qualified name we have to qualify
                    // the dependency.
                    String qname = depname;
                    String prefix = field.getPrefix();
                    if (prefix != null)
                        qname = field.qualifyName(prefix, depname);

                    // in theory we could go look for post data with
                    // this qname but we should really have the field 
                    Field other = form.getField(qname);
                    if (other != null)
                        args.put(depname, other.getValue());
                    else {
                        // this might be okay but warn for awhile
                        if (log.isWarnEnabled())
                            log.warn("Unresolved field: " + qname);
                    }
                }
            }
        }

        // Add standard args
        // Technically we shouldn't be modifying the base args but
        // but for the the following standard args it's okay since we
        // change them each time

        // Pass Form so we can make evaluation sensitive to form object
        // attributes.  Not sure how generally useful this is, it was
        // added originally so we could get to form.isReadOnly which is
        // set dynamically during rendering. 
        // TODO: The Section might be interesting too but we dont' have
        // backrefs here.

        args.put(ARG_FORM, form);
        args.put(ARG_FIELD, field);
        
        // And the current field value, only really need this for
        // the validation script but it could be interresting for
        // the allowed values script if for example we wanted to inject
        // another value.  Since this is easy to get to from field
        // we don't really need it but have to be backward compatible.
        args.put(ARG_VALUE, field.getValue());

        // If this is a value script, the PlanCompiler passes the current
        // Link attribute value as "current".  We dont have access to that
        // here, but so we can supply a consistent set of args to all scripts
        // set this to the current field value.  If we need to know 
        // the old value it will have to be passed in the field like scriptArgs
        args.put(ARG_CURRENT, field.getValue());

        // TODO: If the field came from an Application or Role template,
        // we could fetch those objects and pass then as "application"
        // and "role" like PlanCompiler.  Either that or pass 
        // roleName and applicationName so we can use it consistently
        // whether the field is dynamic or not.
        // "template" is a bigger problem, we dont' have access to that
        // without fetching the role or app.

        return args;
    }
    
    /**
     * Add the given value for the field into the arguments maps.  This puts the
     * value in maps under both the fieldName and the ARG_VALUE/ARG_CURRENT
     * keys.
     */
    private void addFieldValue(String fieldName, Object fieldValue,
                               Map<String,Object> fieldArgs,
                               Map<String,Object> namespace) {
        
        addFieldValue(fieldName, fieldValue, namespace);

        // may have copied this in some cases
        if (fieldArgs != namespace)
            addFieldValue(fieldName, fieldValue, fieldArgs);
    }

    /**
     * Add the given field value to the map.
     */
    private void addFieldValue(String fieldName, Object fieldValue,
                               Map<String,Object> map) {
        map.put(fieldName, fieldValue);
        map.put(ARG_VALUE, fieldValue);
        map.put(ARG_CURRENT, fieldValue);
    }

    /**
     * Calculate an ordered list of all Fields in a Form based
     * on declared dependencies.  This will be used as the evaluation
     * order when expanding forms so we don't have to rely on lexical order.
     *
     * It can also be used to selectively hide fields until their
     * dependencies are met.
     */
    private List<Field> orderFields(Form form) 
        throws GeneralException {
        
        // initialize traversal state
        _ordered = new ArrayList<Field>();
        _visited = new HashMap<String,Field>();
        _stack = new HashMap<String,Field>();
        
        // this flattens the sectioned fields into one list
        Iterator<Field> it = form.iterateFields();
        while (it.hasNext()) {
            Field field = it.next();
            String name = field.getName();
            // have we seen it before chasing dependnecies?
            // remember skeleton fields don't have names
            if (name == null || _visited.get(name) == null)
                chaseDependencies(form, field);
        }

        return _ordered;
    }

    /**
     * Recursively walk the dependnecy hierarhcy for a field.
     * As we find dependency fields they will be marked as required.
     *
     * Note that if we're here with provisioning policy forms there
     * will often be dependencies that don't have corresponding Fields.
     * In these cases the PlanCompiler was supposed to have left the
     * dependency values in the scriptArgs field attribute.  Look there
     * before logging a warning.
     *
     * Note that when expanding provisioning policy forms we will walk
     * over both "real" fields from the policy as well as fields in the
     * skeleton form.  Skeleton fields normally value a value script
     * and they should should not have dependencies but suppose that's okay. 
     * Skeleton fields often do not have names since they just calculate
     * static values for display.
     */
    private void chaseDependencies(Form form, Field field) {
        
        String fieldName = field.getName();
        Map<String,Object> args = field.getScriptArguments();

        if (fieldName != null)
            _stack.put(field.getName(), field);
        
        String depspec = field.getDependencies();
        if (depspec != null) {
            List<String> deplist = Util.csvToList(depspec);
            if (deplist != null) {
                for (String depname : deplist) {

                    // Check self-contained dependencies
                    boolean local = (args != null && args.containsKey(depname));

                    String qname = depname;
                    String prefix = field.getPrefix();
                    if (prefix != null) {
                        // this must be a policy field, add the prefix
                        // to the dependency before searching
                        qname = field.qualifyName(prefix, depname);
                    }
                    Field depfield = form.getField(qname);

                    if (depfield == null) {
                        // this is okay as long as we have a local value
                        if (!local) {
                            if (log.isErrorEnabled())
                                log.error("Unresolved field dependency: " + depname);
                            // nothing we can do about it now so go ahead
                            // and process it
                        }
                    }
                    else {
                        // Should a local value override the Field if both
                        // are present?
                        // If not, should the Field be marked as required?
                        String traceName = fieldName;
                        if (traceName == null)
                            traceName = "*Unnamed Skeleton Field*"; 

                        if (local) {
                            if (log.isInfoEnabled())
                                log.info("Form field " + traceName + " has both a local " + 
                                         "dependency value and a matching Field for " + depname);
                        }
                        
                        // Having a dependency automatically makes
                        // this field required, unless we have a local value.  
                        if (!depfield.isRequired() && !local) {
                            if (log.isInfoEnabled())
                                log.info("Promoting field " + depname + " to required " +
                                         "due to dependency from " + traceName);
                            
                            depfield.setRequired(true);
                        }

                        if (_visited.get(depname) != null) {
                            // already processed this 
                        }
                        else if (_stack.get(depname) != null) {
                            // cycles in the dependency tree, have to brake
                            if (log.isErrorEnabled())
                                log.error("Cycle in field dependencies: " + traceName + 
                                          " depends on " + depname);
                        }
                        else {
                            chaseDependencies(form, depfield);
                        }
                    }
                }
            }
        }

        if (fieldName != null) {
            _visited.put(fieldName, field);
            _stack.remove(fieldName);
        }

        _ordered.add(field);
    }

    /**
     * Return true if all of a fields dependencies are met.
     * Note that this is a bit different than what chaseDependencies
     * does, here we're looking for the ability to run our field
     * scripts with values for each dependency.
     */
    private boolean isFieldReady(Form form, Field field) {
                                       
        boolean ready = true;
        List<String> dependencies = field.getDependencyList();
        if (dependencies != null) {
            for (String depname : dependencies) {

                // Check self-contained dependencies
                Map<String,Object> args = field.getScriptArguments();
                boolean local = (args != null && args.containsKey(depname));
                if (local) {
                    // we don't have to look for fields, even if we have
                    // one we'll still fall back to the local value
                }
                else {
                    String qname = depname;
                    String prefix = field.getPrefix();
                    if (prefix != null) {
                        // this must be a policy field, add the prefix
                        // to the dependency before searching
                        qname = field.qualifyName(prefix, depname);
                    }
                    Field dep = form.getField(qname);

                    // will have already traced invalid dependencies
                    if (dep != null) {
                        if (dep.getValue() == null) {
                            // static fields wait
                            if (!field.isDynamic()) {
                                ready = false;
                                break;
                            } 
                        }
                    }
                }
            }
        }

        return ready;
    }

    /**
     * Copy the expansion previousValue from the given field into the master
     * form.
     * 
     * Awful kludge to capture some expansion side effects that we need
     * to keep in the master form.  This only applies to dynamic field previous
     * values.
     */
    private void captureExpansion(Form master, Field fromField) {
        // assuming these will have unique names for now... not good
        Field dest = master.getField(fromField.getName());
        if (dest != null)
            dest.setPreviousValue(fromField.getPreviousValue());
        else {
            log.warn("Unmatched form field: " + fromField.getName());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Hidden and Incomplete Filtering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Remove any hidden sections/fields from the given form.  
     * Optionally if requested in the form, remove fields that are marked incomplete.
     *
     * The values of hidden fields are copied to the master form so that they
     * can still contribute to the script arguments.
     */
    private void filterHidden(Form master, Form form) {

        // historically the flags came from different forms, but
        // I think they should be the same in both?
        boolean removeHidden = !master.isIncludeHiddenFields();
        boolean removeIncomplete = form.isHideIncompleteFields();

        filterHidden(master, form.getItems(), removeHidden, removeIncomplete, false);
    }

    private void filterHidden(Form master, List<FormItem> items,
                              boolean removeHidden, boolean removeIncomplete,
                              boolean removingSection) {

        if (items != null) {
            ListIterator<FormItem> it = items.listIterator();
            while (it.hasNext()) {
                FormItem item = it.next();
                if (item instanceof Field) {
                    Field field = (Field)item;
                    if (removeHidden && (removingSection || field.isHidden())) {
                        it.remove();
                        // We're removing this field from the expanded form, so
                        // copy the value into the master.
                        master.setFieldValue(field.getName(), field.getValue());
                    }
                    else if (removeIncomplete && field.isIncomplete()) {
                        // we do not copy the value here, it shouldn't
                        // have one since dependencies were not met
                        it.remove();
                    }
                }
                else if (item instanceof Section) {
                    Section s = (Section)item;
                    filterHidden(master, s.getItems(), removeHidden, removeIncomplete,
                                 s.isHidden());
                    // Historically if all fields in a Section are hidden
                    // we remove the Section.  This will also have the effect
                    // of removing empty sections.  I'm not sure I like this but
                    // can't change without impact.
                    if (Util.size(s.getItems()) == 0) {
                        it.remove();
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Dynamic Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Expand dynamic field values after the final post.  
     * Think...this could just be expandInto that we do all the time,
     * not just on refresh?
     */
    public void expandDynamicValues(Form master, Form expanded,
                                    Map<String,Object> args) 
        throws GeneralException {

        expandInner(master, expanded, args, true);
    }

    /**
     * Build the arguments map for evaluating field scripts outside
     * of a full form expansion.  First we get the namespace then
     * add the field specific values.  This is broken up into
     * two phases because expandField() needs access to the namespace
     * Map, but the others don't care.
     */
    private Map<String,Object> buildFieldArgs(Form form, Field field, 
                                              Map<String,Object> baseArgs) {

        Map<String,Object> namespace = getFieldNamespace(form, field, baseArgs);

        // last true arg means to resolve and pull in dependencies since
        // we're not doing a full expansion
        return addFieldArgs(form, field, namespace, true);
    }

    /**
     * Calculate the allowed values for a field.
     * This is intended to be called from a renderer like FormBean 
     * where we're making An Ajax request to recalculate the allowed
     * value list for a field based on the latest values for other fields.
     *
     * The Form passed here must be populated with the latest field values.
     *
     * The baseArgs map has context-specific arguments to pass to the
     * field scripts.  This comes from FormBean.getFormArguments().
     */
    public List getAllowedValues(Form form, Field field, 
                                 Map<String,Object> baseArgs)
        throws GeneralException {

        List allowed = null;

        if (field.getAllowedValuesDefinition() != null) {
            
            // add field-specific args
            Map<String,Object> fieldArgs = buildFieldArgs(form, field, baseArgs);

            allowed = getAllowedValues(field, fieldArgs);
        }

        return allowed;
    }

    /**
     * Inner worker that assumes fully fleshed out field arguments.
     */
    public List getAllowedValues(Field field, Map<String,Object> args)
        throws GeneralException {

        List allowed = null;

        DynamicValue dv = field.getAllowedValuesDefinition();
        if (dv != null) {
            DynamicValuator valuator = new DynamicValuator(dv);

            Object o = valuator.evaluate(_context, args);

            if (o instanceof List)
                allowed = (List)o;
            else if (o != null) {
                // should we just coerce it to a list?
                if (log.isErrorEnabled())
                    log.error("Allowed values script returned invalid result: " + 
                              o.toString());
            }
        }

        return allowed;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Validation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Perform validation on all form fields.
     * Returns errors as a Map with the key being the Field name
     * and the value being a List of Message objects containing the
     * error messages related to that field.
     * 
     * Caller is responsible for localizing.
     */
    public Map<String,List<Message>> 
        validate(Form form, Map<String,Object> baseArgs) 
        throws GeneralException {

        Map<String,List<Message>> errors = new HashMap<String,List<Message>>();

        // flatten items, sections don't matter
        Iterator<Field> it = form.iterateFields();
        while (it.hasNext()) {
            Field field = it.next();

            // first check basic data types
            if (validateType(field, errors)) {

                // then required
                if (validateRequired(field, errors)) {
                    
                    //then check XSS for string field
                    if (validateXSS(field, errors)) {

                        // following phases may need field script args
                        Map<String,Object> fieldArgs = 
                            buildFieldArgs(form, field, baseArgs);
                    
                        // then being within the allowed value constraints
                        if (validateAllowedValue(field, fieldArgs, errors)) {

                            // and finally run the validation script
                            doValidationScript(field, fieldArgs, errors);
                        }
                    }
                }
            }
        }

        if (errors.size() == 0)
            errors = null;

        return errors;
    }

    /**
     * Check to see if a field value is of the right type.
     * TODO: Should we be smarter about coercion?
     */
    private boolean validateType(Field field, Map<String,List<Message>> errors) {
        
        boolean valid = true;

        // dont bother validating readonly fields
        if (!field.isReadOnly()) {

            Object value = field.getValue();

            if (!(value instanceof List)) {
                // scalar values work with either scalar or multi fields
                // if the field is multi, do not coerce this to a List
                // since if the field may use FORMAT_CSV and require a String
                valid = validateType(field, value);
            }
            else if (!field.isMulti()) {
                // got a list but the field is scalar
                valid = false;
            }
            else {
                for (Object v : (List)value) {
                    valid = validateType(field, value);
                    if (!valid)
                        break;
                }   
            }

            if (!valid) {
                String msgKey = MessageKeys.FORM_PANEL_VALIDATION_GENERIC;
                String type = field.getType();

                if (Field.TYPE_INT.equals(type))
                    msgKey = MessageKeys.FORM_PANEL_VALIDATION_INT;

                else if (Field.TYPE_LONG.equals(type))
                    msgKey = MessageKeys.FORM_PANEL_VALIDATION_LONG;

                else if (Field.TYPE_DATE.equals(type))
                    msgKey = MessageKeys.FORM_PANEL_VALIDATION_DATE;

                Message msg = Message.error(msgKey, field.getDisplayLabel());
                addError(field, msg, errors);
            } 
        }

        return valid;
    }


    /**
     * Verify that a value of a field matches the field type.
     * If the field is multi-valued the value will be one element
     * from the value list.
     *
     * Ordinarilly we would just coerce the type if possible.  
     * Is it necessary to be so strict here?
     */
    private boolean validateType(Field field, Object value) {

        boolean valid = true;
        
        if (value == null || "".equals(value)) {
            // null is always allowed
            // should empty string always be the same as null?
            // if so should wejust collapse it?
        }
        else {
            String type = field.getType();
            
            if (Field.TYPE_BOOLEAN.equals(type)) {
                try {
                    Boolean.parseBoolean(value.toString());
                } 
                catch (Throwable e) {
                    valid = false;
                }
            } 
            else if (Field.TYPE_INT.equals(type)) {
                try {
                    Integer.parseInt(value.toString());
                } 
                catch (NumberFormatException e) {
                    valid = false;
                }
            } 
            else if (Field.TYPE_LONG.equals(type)) {
                // do we really need to distinguish between int and long?
                try {
                    Long.parseLong(value.toString());
                } 
                catch (NumberFormatException e) {
                    valid = false;
                }
            }
            else if (Field.TYPE_DATE.equals(type)) {
                if (!(value instanceof Date)) {
                    // long is allowed if it parses
                    try {
                        Long.parseLong(value.toString());
                    } 
                    catch (NumberFormatException e) {
                        valid = false;
                    }
                }
            }
        }
            
        return valid;
    }

    /**
     * Validate the required status of a field.
     */
    private boolean validateRequired(Field field, 
                                     Map<String,List<Message>> errors) {

        boolean valid = true;

        if (field.isRequired()) {
            Object value = field.getValue();
            valid = (value != null && !"".equals(value));

            if (!valid) {
                Message msg;
                String displayLabel = field.getDisplayLabel();
                if (displayLabel.startsWith("{") && displayLabel.contains("messageKey")) {
                    msg = Message.error(MessageKeys.FORM_PANEL_SIMPLE_VALIDATION_REQUIRED);
                }
                else {
                    msg = Message.error(MessageKeys.FORM_PANEL_VALIDATION_REQUIRED, field.getDisplayLabel());
                }
                
                addError(field, msg, errors);
            }
        }
        return valid;
    }

    /**
     * Validate that the field value is within the allowed values list.pplier
     */
    private boolean validateAllowedValue(Field field,
                                         Map<String,Object> fieldArgs,
                                         Map<String,List<Message>> errors)
        throws GeneralException {
     

        boolean valid = true;

        Object value = field.getValue();
        if (value != null) {

            if (field.isDynamic()) {
                // refresh the allowed value list
                // this really shouldn't be necessary if the UI
                // was dynamically refreshing the allowed values list
                // before posting the form
                List allowed = getAllowedValues(field, fieldArgs);
                field.setAllowedValues(allowed);
            }

            List allowed = field.getAllowedValues();

            if (allowed != null && allowed.size() > 0) {

                // If the field is declared multi and we don't have a list,
                // and the format is declared as a CSV, then unpack it
                // before validating.  Only do this if explicitly declared
                // because it is okay to have single values with commas
                // such as DNs.
                if (field.isMulti() && 
                    Field.FORMAT_CSV.equals(field.getFormat()) && 
                    !(value instanceof List)) {
                    value = Util.csvToList(value.toString());
                }

                if (value instanceof List) {
                    // validateType already checkeded field.isMulti()
                    // now we check the elements
                    for (Object el : (List)value) {
                        if (!field.isAllowed(el)) {
                            valid = false;
                            break;
                        }
                    }
                }
                else if (!field.isAllowed(value))
                    valid = false;

                if (!valid) {
                    Message msg = Message.error(MessageKeys.FORM_PANEL_VALIDATION_GENERIC, field.getDisplayLabel());
                    addError(field, msg, errors);
                }
            }
        }

        return valid;
    }

    /**
     * After passing all of theother validation phases, we finally
     * run the validation script.
     */
    private void doValidationScript(Field field,
                                    Map<String,Object> fieldArgs,
                                    Map<String,List<Message>> errors) 
        throws GeneralException {
        
        Script script = field.getValidationScript();
        Rule rule = field.getValidationRule();

        if (script != null || rule != null) {
            
            Object result = null;
            
            if (script != null) {
                result = _context.runScript(script, fieldArgs);
            } 
            else if (rule != null) {
                /*
                 * IIQSR-62 Adding link and application attributes
                 * to field arguments so that on password change
                 * we could make sure we are changing the right account.
                 */
                //Need to check if there's a link as sometimes it could be null, for example on new user registration.
                if (null != field.getAttribute("linkId")) {
                    Link link = _context.getObjectById(Link.class, field.getAttribute("linkId").toString());
                    if (null != link) {
                        fieldArgs.put(ARG_LINK, link);
                        if (null != link.getApplication()) {
                            fieldArgs.put(ARG_APPLICATION, link.getApplication());
                        } else {
                            log.warn("Couldn't find an app to add to field arguments");
                        }
                    } else {
                        log.warn("Couldn't find a link to add to field arguments");
                    }
                } else {
                    String appName = field.getApplication();
                    if(Util.isNotNullOrEmpty(appName)) {
                        Application app = _context.getObjectByName(Application.class, appName);
                        fieldArgs.put(ARG_APPLICATION, app);
                    }
                }
                // Original code for password validation reattached the rule
                // is that really necessary?  Hmm probably since the    
                // Form has by now been in the HttpSession for an unknown
                // amount of time and we may need to traverse the
                // included rules list.
                rule = ObjectUtil.reattach(_context, rule);
                result = _context.runRule(rule, fieldArgs);
            }

            if (result instanceof Collection) {
                for (Object item : (Collection)result)
                    addError(field, item, errors);
            }
            else if (result != null)
                addError(field, result, errors);
        }
    }

    /**
     * Check to see if a string field value contains XSS charactor.
     * 
     */
    private boolean validateXSS(Field field, Map<String,List<Message>> errors) {
        
        boolean valid = true;

        // dont bother validating readonly fields
        if (!field.isReadOnly()) {

            String type = field.getType();

            if (Field.TYPE_STRING.equals(type)) {
                Object value = field.getValue();
                if (value != null && value instanceof String) {
                    try {
                        WebUtil.detectFormXSS((String) field.getValue());
                    } catch (RuntimeException rte) {
                        valid = false;
                    }
                }
            }

            if (!valid) {
                Message msg = Message.error(MessageKeys.FORM_PANEL_VALIDATION_XSS, field.getDisplayLabel());
                addError(field, msg, errors);
            } 
        }

        return valid;
    }



    
    /**
     * Add an error message to the error set.
     * The argument is usually a Message but to handle the
     * results of validation rules we'll also take Strings.
     */
    private void addError(Field field, Object result,
                          Map<String,List<Message>> errors) {
        
        if (result != null) {
            Message msg;
            if (result instanceof Message)
                msg = (Message)result;
            else 
                msg = Message.error(result.toString());
            

            String fieldName = field.getName();
            List<Message> fieldErrors = errors.get(fieldName);
            if (fieldErrors == null) {
                fieldErrors = new ArrayList<Message>();
                errors.put(fieldName, fieldErrors);
            }
            fieldErrors.add(msg);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Assembly
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Assemble a Form from a skeleton form and a list of gathered fields.
     * The result form is *not* expanded.
     *
     */
    public Form assemble(String formName, List<Field> fields)
        throws GeneralException {

        Form form = null;

        // should we load the skeleton form even if there are no fields?
        if (fields != null) {
            form = getFormSkeleton(formName);
            assemble(form, fields);
        }

        return form;
    }
    
    /**
     * Assemble a list of fields using an existing form as the skeleton.
     * Primarily useful when assembling a form that hasn't been persisted yet.
     * To assemble from a persistent form use 
     * @see#assemble(String, List<Field>)
     * @param fields Fields to assemble 
     */
    public void assemble(Form form, List<Field> fields) {
        if (fields != null && !fields.isEmpty()) {
            for (Field field : fields) {
                assemble(form, field);
            }
        }
    }

    /**
     * Lookup a skeleton form by name.
     * For now these are just Form object names but we might want
     * to map these through system config like we do for workflows.
     */
    private Form getFormSkeleton(String name)
        throws GeneralException {

        // don't default this for now, the refresh and udpate forms
        // will probably be close the the same but I don't want to 
        // get into the habit of not specifying them
        if (name == null) {
            throw new GeneralException("Missing skeleton form");
            //name = FORM_IDENTITY_REFRESH;
        }

        Form form = _context.getObjectByName(Form.class, name);
        if (form == null)
            throw new GeneralException("Undefined skeleton form: " + name);
        
        // deep copy so we can modify it
        form = (Form)form.deepCopy((Resolver)_context);

        // remove junk we don't need
        form.clearPersistentIdentity();

        return form;
    }

    /**
     * Assimilate one field into the skeleton form.
     *
     * If a field does not speicfy a section name it will be placed
     * in the default section.  This is defined as the section in 
     * the skeleton form whose name is "default".    If there is no
     * section with a "default" name, one is created and placed at the front
     * of the end.
     */
    private void assemble(Form form, Field field) {
        
        // it might be interesting to let section name reference varialbes?
        String secname = Util.trimnull(field.getSection());
        if (secname == null)
            secname = DEFAULT_SECTION;

        Section section = form.getSection(secname);

        if (section == null) {
            // auto-generate a section
            section = new Section(secname);
            
            // FormRenderer only looks at label, don't set it
            // if this is the default section
            if (!DEFAULT_SECTION.equals(secname))
                section.setLabel(secname);
            
            form.add(section);
        }

        Field existing = section.getField(field.getName());
        if (existing != null) {
            // should this happen?  the plan compiler should be
            // able to filter out duplicate questions
            if (log.isWarnEnabled())
                log.warn("Supressing duplicate field: " + field.getName());
        }
        else {
            int priority = field.getPriority();
            if (priority == 0) 
                section.add(field);
            else {
                // find an insertion point
                List<FormItem> items = section.getItems();
                if (items == null)
                    section.add(field);
                else {
                    /* Add the item according to its priority
                     * If our priority is a 0 we automatically go to the end of the list.
                     * If our priority is greater than zero the item with the higher number comes first.
                     * If our priority matches other items' priorities we insert after items with matching priorities
                     */
                    boolean added = false;
                    if (priority == 0) {
                        items.add(field);
                        added = true;
                    } else {
                        for (int i = 0 ; i < items.size() ; i++) {
                            FormItem item = items.get(i);
                            int other = item.getPriority();
                            if (other < priority) {
                                items.add(i, field);
                                added = true;
                                break;
                            }
                        }                        
                    }
                    if (!added)
                        items.add(field);
                }
            }
        }
        
        // reduce XML clutter by removing the section id on the field
        // now that we've put it in the right section
        field.setSection(null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ProvisioningProject Forms
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given a project with Questions, assemble a Form to seek the answers.
     * The name of the skeleton form may be passed in, if not use
     * a default. 
     *
     * This is called by IdenityLibrary.buildProvisoningForm 
     * and is used in identity request workflows to present a 
     * seqeuence of forms to gather provisioning information.
     * 
     * Questions can be targeted at more than one identity. The "owner"
     * may be set to restrict the returned form to fields owned by that
     * owner.  If the "requiredOwner" flag is set then null is returned
     * if there are nofields for the given owner.  If false, then we may
     * return a form for a different owner, but only if there are no fields
     * for the preferred owner.
     *
     * TODO: I don't like the way owner/requiredOwner is passed in
     * but they aren't workflow variables and having them be top-level
     * fields on Formicator feels kludgey.  
     *
     * Could pre-assemble the Forms and just pick them fron a Map here,
     * but that doesn't get you the merger of the unowned fields into
     * the first owner's form.
     */
    public Form buildProvisioningForm(ProvisioningProject project,
                                      String skeleton,
                                      String owner,
                                      boolean ownerRequired,
                                      Map<String,Object> args)
        throws GeneralException {

        Form form = null;

        if (project != null) {
            List<Question> questions = project.getQuestions();
            if (questions != null && questions.size() > 0) {

                // Convert the Question list into a Field list for Formicator
                List<Field> preferredFields = new ArrayList<Field>();

                // If the owner isn't required maintain an alternate list
                String altOwner = null;
                List<Field> altFields = new ArrayList<Field>();

                for (Question q : questions) {
                    // Formerly filtered out questions that had already been answered
                    // but now that the Question list is built from scratch on
                    // each compilation don't do that.  Fields with the
                    // "reviewRequired" option may be in here even if they 
                    // have a value.
                    Field f = q.getField();
                    if (f != null) {
                        String qowner = q.getOwner();

                        // filter based on ownership
                        if (owner == null) {
                            // if no owner was passed in, start assembly for the
                            // first owner we find
                            owner = qowner;
                            preferredFields.add(f);
                        }
                        else if (qowner == null || owner.equals(qowner)) {
                            // owners match
                            preferredFields.add(f);
                        }
                        else {
                            // owners differ, add to the alternate list 
                            if (altOwner == null || altOwner.equals(qowner)) {
                                altFields.add(f); 
                                altOwner = qowner;
                            }
                        }
                    }
                }

                // If we didn't find any fields for the requested owner, and
                // the requested owner wasn't required, use the alternate
                // owner fields.
                List<Field> fields = preferredFields;
                if (fields.size() == 0 && !ownerRequired)
                    fields = altFields;

                if (fields.size() > 0) {

                    // Inject a validation rule into password fields
                    // See method comments for more about what this is
                    for (Field field : fields)
                        hackPasswordField(project, field);

                    form = assemble(skeleton, fields);

                    // We used to expand here.  Expansion is a destructive
                    // process (it can remove scripts, etc...).  Now we return
                    // an unexpanded "master" form that will later be expanded
                    // before it is presented.
                    //expandInto(form, args);

                    // convey the owner we ended up with in the form
                    if (fields == preferredFields)
                        form.setTargetUser(owner);
                    else
                        form.setTargetUser(altOwner);

                    // fully load so we can hang this off the HttpSession
                    form.load();
                }
            }
        }

        return form;
    }

    /**
     * Doctor a password field to have a validation rule and extra help text.
     *
     * We have a kludge/feature for password fields where we will inject
     * a rule to do validation that knows how to apply the PasswordPolicy
     * defined on the Application.  The Application designer could put
     * a validation rule on the field, but since we already have the
     * PasswordPolicy model it is more convenient to leverage that.
     * 
     * How we detect this is a bit of a kludge, any field whose
     * name ends with ":password" and whose type is SECRET 
     * is assumed to be the password field for the application whose
     * name precedes the colon in the field name.  This assumes
     * the delimiting convention used by PlanCompiler when the
     * fields were built.
     * 
     * Added check for required because there are cases
     * where the password is optional in those cases
     * we cannot require validation.
     * 
     */
    private void hackPasswordField(ProvisioningProject project,
                                   Field field)
        throws GeneralException {

        if (field.getName().endsWith(":password") && 
            field.getType().equals(PropertyInfo.TYPE_SECRET) &&
            field.getValidationRule() == null && 
            field.isRequired() ) {
            
            // let the rule be mapped through sysconfig
            Configuration syscon = _context.getConfiguration();
            String ruleName = syscon.getString(Configuration.PASSWORD_VALIDATION_RULE);
            if (ruleName == null || ruleName.length() == 0)
                ruleName = CHECK_PASSWORD_POLICY_RULE;

            Rule rule = _context.getObjectByName(Rule.class, ruleName);
            if (rule != null) {
                field.setValidationRule(rule);
            } else {
                if (log.isErrorEnabled())
                    log.error("Missing password field validation rule: " + ruleName);
            }

            // Optionally append a summary of the policy to the
            // field help text.
            // !! This assumes that the help "key" is literal text
            // and not a mesage catalog key.  Normally true for
            // custom fields defined in the UI, but still...
            boolean addHelp = syscon.getBoolean(Configuration.APPEND_POLICY_REQS, true);
            if (addHelp) {
                // get the application that provided this field
                String appname = field.getApplication();
                if (appname != null) {
                    Application app = _context.getObjectByName(Application.class, appname);
                    Identity targetUser = _context.getObjectByName(Identity.class, project.getIdentity());
                    if (app != null && targetUser != null) {

                        PasswordPolice pp = new PasswordPolice(_context);
                        PasswordPolicy policy = pp.getEffectivePolicy(app, targetUser);
                        if (policy != null) {
                            String help = field.getHelpKey();
                            List<String> extra = policy.convertConstraints();
                            if (extra != null) {
                                if (help == null)
                                    help = extra.toString();
                                else
                                    help = help + "\n" + extra;
                            }
                            field.setHelpKey(help);
                        }
                    }
                }
            }
        }
    }

    /**
     * Assimilate a Form laden with values back into the project.
     *
     * This is called by IdenityLibrary.assimilateProvisoningForm 
     * and is used in identity request workflows to present a 
     * seqeuence of forms to gather provisioning information.
     * 
     * Only assimilate fields that match Questions in the project.
     * In theory the form could have other hidden fields that were only
     * used to derive the values for the question fields.
     */
    public void assimilateProvisioningForm(Form form, ProvisioningProject project)
        throws GeneralException {

        if (project != null && form != null) {

            List<Question> questions = project.getQuestions();
            if (questions != null) {
                for (Question q : questions) {

                    Field field = form.getField(q.getFieldName());
                    if (field != null) {
                        Object value = field.getValue();
                        if (value != null && Util.nullSafeEq(FormItemType.SECRET.getFieldName(), field.getType())) {
                            q.setAnswer(_context.encrypt(String.valueOf(value)));
                        } else {
                            q.setAnswer(value);
                        }
                        // kludge, flag this so we can stop presenting
                        // reviewRequired fields, need to revisit this
                        q.setShown(true);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ProvisioningPlan Approval Forms
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Derive a form to present and allow modifications to the
     * attributes in a provisioning plan.
     *
     * This is called by IdentityLibrary.buildPlanApprovalForm
     * and is used for workflows that want to present a work item
     * that contains the requested attributes for approval but also allows
     * the approver to modify those attributes.
     * 
     * @ignore
     * I decided to keep this out of Provisioner for now since it isn't
     * as dependent on the compilation process as the forms derived from
     * Questions. Still, think about consolodating Question form handling
     * up here too
     *
     * This is still somewhat experimental, there are a lot of options
     * we could pass in here, and we might want to work from the project instead
     * of a plan.  Currently we assume that we're being given the "master plan"
     * that would have been created by LCM, we're going to present it to allow
     * changes to be made during approvls, then compile the result into 
     * the project.
     */
    public Form buildPlanApprovalForm(ProvisioningPlan plan,
                                      String skeleton,
                                      Map<String,Object> args)
        throws GeneralException {

        // start from a skeleton or empty form
        Form form = null;

        if (skeleton != null)
            form = getFormSkeleton(skeleton);
        else
            form = new Form();

        // Expand references in the skeleton form.  
        // We can do this now since we're building the Fields and the
        // won't have scripts or variable references.
        expandInner(form, form, args, false);

        if (plan != null) {
            
            // Here we have the first dilemma, do we merge the attributes
            // for all accounts into one section or do we have a sectino
            // for each account?  Second per account seems best, but in the
            // usual case where we're only asking for IIQ attributes we 
            // could collapse the section?

            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {

                for (AccountRequest account: accounts) {
                    String appname = account.getApplication();
                    String nativeIdentity = account.getNativeIdentity();
                    String sectionName = appname;
                    if(Util.isNotNullOrEmpty(nativeIdentity)) {
                        sectionName = appname + ":" + nativeIdentity;
                    }

                    Section section = form.getSection(sectionName);
                    if (section == null) {
                        section = new Section();
                        section.setName(sectionName);
                        form.add(section);
                    }

                    List<AttributeRequest> atts = account.getAttributeRequests();
                    if (atts != null) {
                        for (AttributeRequest att : atts) {
                            if (includeInApprovalForm(account, att))
                                addPlanField(account, att, section);
                        }
                    }
                    
                    // TODO: what about permissions?
                    
                    if (!section.hasFields())
                        form.remove(section);
                }
            }
        }

        if (!form.hasFields())
            form = null;
        else {
            // fully load so we can hang it off the HttpSession
            form.load();
        }

        return form;
    }
    
    /**
     * Return true if an AttributeRequest should be included in a 
     * plan approval form.
     *
     * This is a kludge to keep the "useBy" date set in LCM create
     * identity workflows from showing.  Since it is likely we'll
     * have other system attributes in time, this should be generalized
     * into some kind of declared exclusion list rather than just 
     * hard coding it here.  
     */
    private boolean includeInApprovalForm(AccountRequest account,
                                          AttributeRequest att) {
        boolean include = true;

        if (ProvisioningPlan.APP_IIQ.equals(account.getApplication()) &&
            Identity.PRF_USE_BY_DATE.equals(att.getName()))
            include = false;

        return include;
    }

    /**
     * This is sort of backwards because we guess at what the Field should
     * look like based on the Application schemas and ObjectConfig:Identity
     * rather than being given a Template like we do in the Provisioner.
     * I guess we should be able to operate in both directions, but we might
     * want to look in the Application templates for any matching fields 
     * and use those instead rather than building them from scratch.
     * 
     * For now ignore Add and Remove, as those don't really fit with
     * the Form model.  Eventually there are two things we could do with those,
     * either get the current value, apply the add/remove then present
     * this as if it were a Set of the entire value, or use a special
     * field type like "addRemove" that presents some sort of complex
     * widget showing the adds/removes, letting you take them out individually
     * and maybe adding new ones.
     *
     */
    private void addPlanField(AccountRequest account, GenericRequest req,
                              Section section) 
        throws GeneralException {

        Operation op = req.getOperation();
        if (op != null && op != Operation.Set) {
            if (log.isErrorEnabled())
                log.error("Ignoring " + op.toString() + " operation in plan");
        }
        else {
            // using a colon delimiter like we did for Question forms
            // but may need to revisit this
            String appname = account.getApplication();
            String attname = req.getName();
            String nativeIdentity = account.getNativeIdentity();

            Field field = new Field();
            String fieldname = buildFieldName(appname, attname);
            field.setName(fieldname);

            Object value = req.getValue();
            field.setValue(value);


            // set the defaults in case we can't find a schema
            field.setType(Field.TYPE_STRING);
            if (value instanceof Collection)
                field.setMulti(true);

            Map<String,Object> args = req.getArguments();
            if ( args != null ) {
                Object prev = args.get(ProvisioningPlan.ARG_PREVIOUS_VALUE);
                field.setPreviousValue(prev);
                
                String type = (String)args.get(ProvisioningPlan.ARG_TYPE);
                if(type!=null && type.equals(ProvisioningPlan.ARG_TYPE_DATE)) {
                    field.setType(Field.TYPE_DATE);
                }
                if (type != null && type.equals(AttributeDefinition.TYPE_SCOPE)) {
                    field.setType(Field.TYPE_SCOPE);
                }
                
                Boolean required = Util.otob(args.get(ProvisioningPlan.ARG_REQUIRED));
                field.setRequired(required);
            }            

            if (ProvisioningPlan.APP_IIQ.equals(appname)) {
                // type information comes from the ObjectConfig
                ObjectConfig config = getIdentityConfig();
                ObjectAttribute att = config.getObjectAttribute(attname);
                if (att != null) {
                    // TODO: Probably want to be smart about EditMode in some cases?
                    if(att.getType()!=null)
                        field.setType(att.getType());
                    
                    field.setMulti(att.isMulti());
                    field.setDisplayName(att.getDisplayName());                   
                    // what about required?
                    if ( "name".equals(attname) ) {
                        field.setRequired(true);
                    }
                } else { 
                    // there isn't an object attribute for this one so special
                    // case it
                    if ( ProvisioningPlan.ATT_PASSWORD.equals(attname) ) {
                        field.setType(Field.TYPE_SECRET);                        
                        field.setDisplayName("Password");
                        field.setRequired(true);
                    }   
                }

                // Mark the field as secret if the attribute request is
                // marked as secret.
                if (req.getBoolean(ProvisioningPlan.ARG_SECRET)) {
                    field.setType(Field.TYPE_SECRET);
                }
            }
            else {
                // type information comes from the Schema
                Schema schema = getSchema(appname);
                if (schema != null) {
                    AttributeDefinition att = schema.getAttributeDefinition(attname);
                    if (att != null) {
                        field.setType(att.getType());
                        field.setMulti(att.isMulti());
                        field.setDisplayName(att.getDisplayName());
                    }
                }
            }

            section.add(field);
        }
    }

    /**
     * Fetch and cache the ObjectConfig for Identity.
     * Actually Identity already maintains the cache.
     */
    private ObjectConfig getIdentityConfig() throws GeneralException {
        
        return Identity.getObjectConfig();
    }

    /**
     * Fetch and cache the account Schema for the named application.
     */
    private Schema getSchema(String appname) throws GeneralException {

        Schema schema = null;

        if (_schemas == null)
            _schemas = new HashMap<String,Schema>();
        
        if (_schemas.containsKey(appname)) {
            schema = _schemas.get(appname);
        }
        else {
            Application app = _context.getObjectByName(Application.class, appname);
            if (app != null)
                schema = app.getAccountSchema();

            _schemas.put(appname, schema);
        }

        return schema;
    }

    /**
     * Generate a field name that will be unique.   
     * TODO: Support instances and all that jazz...
     */
    private String buildFieldName(String appname, String attname) {

        // look ma, no hands!
        String fieldname = appname + ":" + attname;
        
        return fieldname;
    }

    /**
     * Parse the field name generated by buildFieldName.
     * Return null if the field name is malformed.
     *
     * Return a list of name tokens, currently the first
     * token will be the application name and the second
     * will be the attribute name. 
     *
     * TODO: Support for instances and native identities...
     */
    private List<String> parseFieldName(String fieldname) {
        
        List<String> tokens = null;
        if (fieldname != null ) {
            tokens = new ArrayList<String>();
            String[] parts = fieldname.split(":");
            String fieldName = parts[parts.length-1];
            String appName = parts[0];
            tokens.add(appName);
            tokens.add(fieldName);

        }
        return tokens;
    }

    /**
     * Given a form assumed to have been build by getPlanForm, assimilate
     * the results back into a plan.
     * The form fields must use the same naming convention used by
     * getPlanForm.
     *
     * This is called by IdentityLibrary.assimilatePlanApprovalForm
     * and is used for workflows that want to present a work item
     * that contains the requested attributes and also allows
     * the approver to modify those attributes.
     * 
     */
    public void assimilatePlanApprovalForm(Form form, ProvisioningPlan plan)
        throws GeneralException {

        // Don't assume there is only one level of Secton we could
        // have applied a skeleton form transformation and reorganized them
        Iterator<Field> fields = form.iterateFields();
        while (fields.hasNext()) {
            Field field = fields.next();
            String fieldname = field.getName();
            List<String> stuff = parseFieldName(fieldname);
            if (stuff == null) {
                if (log.isErrorEnabled())
                    log.error("Malformed field name: " + fieldname);
            }
            else {
                String appname = stuff.get(0);
                String attname = stuff.get(stuff.size() - 1);
                
                List<AccountRequest> accounts = plan.getAccountRequests(appname);
                if (accounts.isEmpty()) {
                    // hmm, should we bootstrap these as we go or assume
                    // that the form must have been generated from 
                    // this plan?  Seems useful to allow dynamic extension
                    // but we'll do that another day
                    if (log.isErrorEnabled())
                        log.error("Missing account request: " + appname);
                }
                else if (accounts.size() > 1) {
                    // Currently this is only used for IIQ create/update
                    // approvals so we can assume a single account request.  If
                    // there are multiple account requests we'll throw because
                    // this should not happen.
                    throw new GeneralException("Expected one account request: "
                                               + appname + " - " + plan.toXml());
                }
                else {
                    // TODO: handle permissions
                    AccountRequest account = accounts.get(0);
                    AttributeRequest req = account.getAttributeRequest(attname);
                    if (req == null) {
                        // same issues with bootstrapping here
                        if (log.isErrorEnabled())
                            log.error("Missing attribute request: " + attname);
                    }
                    else {
                        if (log.isInfoEnabled()) {
                            log.info("Replacing value for " + fieldname);
                            log.info("Original: " + req.getValue());
                            log.info("New: " + field.getValue());
                        }
                        
                        // So we see a field has changed values. We need to know
                        // if the user did it or the field derived it. If a user did
                        // it, it needs to be shown in subsequent places, like in the
                        // Monitor --> Identity Requests display. If the field
                        // doesn't have a script, then it can only have been changed
                        // by the user
                        if (field.getScript() == null && field.getRule() == null) {

                            // When we do this, we need to instruct 
                            // IdentityRequestLibrary$RequestItemizer#getOrCreateItem to
                            // not let this get altered into an expansion
                            Attributes<String, Object> args = req.getArguments();
                            if (args == null) {
                                args = new Attributes<String, Object>();
                                req.setArguments(args);
                            }
                            // Not sure if this is the intended use of Source.UI, but
                            // it feels right
                            args.put(IdentityRequestLibrary.ARG_SOURCE, Source.UI);
                            req.setValue(field.getValue());
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Create Forms
    //
    //////////////////////////////////////////////////////////////////////

    // TODO: See if we can pull over some of the stuff being
    // done in web/lcm/AttributesRequestBean here if that makes sense.


}
