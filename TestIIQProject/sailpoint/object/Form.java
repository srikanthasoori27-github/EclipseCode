/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object describing a custom form.
 *
 * Jeff
 *
 * Forms are named top-level objects but may also be embedded
 * in other things (like Workflow).  
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class Form extends SailPointObject
{
    private static final Log log = LogFactory.getLog(Form.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Standard Form Post Actions
    //

    /**
     * CANCEL means to stop editing the form and return to the
     * "parent" page. Whatever caused the form to be displayed
     * might still be active and the user can return to the form later.
     * 
     * Pages that render forms are not required to recognize 
     * this action, but it is recommended.
     * 
     * For WorkItem forms, this cancels the work item editing session
     * and typically returns to the inbox leaving the work item
     * still active.
     *
     * This is intended for cases where the user does not want
     * to complete the now, but will return to it later.
     */
    public static final String ACTION_CANCEL = "cancel";

    /**
     * REFRESH means to assimilate the posted form data and
     * regenerate the form. This does not represent a state
     * transition, the form is simply redisplayed.
     *  
     * Pages that render forms are not required to recognize
     * this action.
     * 
     * Not used now, but could be useful for side effects
     * if data sensitive hidden regions are used in the future.
     */
    public static final String ACTION_REFRESH = "refresh";

    /**
     * NEXT means to assimilate the posted form data and
     * advance to the next state.
     *
     * Pages that render forms are required to recognize this action.
     * 
     * For WorkItem forms, this marks the work item Finished
     * (also known as "approved") and the workflow is advanced.
     *
     */
    public static final String ACTION_NEXT = "next";

    /**
     * BACK means to assimilate the posted form data and
     * return to the previous state.
     *
     * Pages that render forms are not required to recognize 
     * this action.
     *
     * For WorkItem forms, this marks the work item Rejected
     * and the workflow is advanced.
     */
    public static final String ACTION_BACK = "back";

    //
    // Standard Attributes
    // Most form properties are defined in the attributes map rather than
    // top-level fields so we don't have to mess with Hibernate mappings,
    // column sizes, etc. every time we add one.
    //

    /**
     * A title to render at the top of the page.
     * This is typically larger and a different color than
     * the form title. For pages that have more than one
     * form, only one of them should specify a page title.
     */
    public static final String ATT_PAGE_TITLE = "pageTitle";

    /**
     * Major title for the form.
     */
    public static final String ATT_TITLE = "title";

    /**
     * Sub title for the form.
     */
    public static final String ATT_SUBTITLE = "subtitle";

    /**
     * Flag indicating that this form is read-only.
     * The renderer will render the fields either as uneditable text,
     * or as normal HTML components that are disabled.
     */
    public static final String ATT_READ_ONLY = "readOnly";

    /**
     * If true, this form will be displayed as a wizard.
     */
    public static final String ATT_WIZARD = "isWizard";

    /**
     * Form attribute which indicates how labels should be aligned in
     * the form. Valid values are 'left', 'top' and 'right'. Defaults to 'left'
     */
    private final static String ATT_LABEL_ALIGN = "labelAlign";

    /**
     * A form rendering option that causes any fields that do not
     * have all of their dependencies met to be hidden. This is
     * intended for use only in workflow forms, and it makes them
     * work more like provisioning policy forms that hide fields until
     * the dependencies have been entered. When Formicator expands
     * the form it will leave Fields out of the expanded form that have
     * missing dependencies.
     * 
     * This should never be set in the skeleton form for provisioning
     * policies, PlanCompiler is already managing the dependency checking
     * in a different way. This is necessary because policy field dependencies
     * might have to be sent to different owners in different work items,
     * but the same owner will see all workflow form fields.
     */
    public static final String ATT_HIDE_INCOMPLETE_FIELDS = 
        "hideIncompleteFields";

    /**
     * The base path value used as the context for the form's
     * field values.  Used when the form is used with a "model".
     */
    private final static String ATT_BASE_PATH = "formBasePath";
    
    /**
     * Attribute to inform Formicator whether or not to strip the hidden fields 
     * from the expanded form
     */
    public static final String ATT_INCLUDE_HIDEN_FIELDS = "includeHiddenFields";

    /**
     * Boolean attribute used in role provisioning policy forms.
     * When true, it it means that the form fields are merged with
     * the role profiles rather than replacing them.
     */
    public static final String ATT_MERGE_PROFILES = "mergeProfiles";

    //////////////////////////////////////////////////////////////////////
    //
    // Centralized form editor constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Attribute indicating Application field on the Role Provisioning Policy form.
     * This is deprecated in 7.0 as we now have a first-class Application reference.
     * Must continue to support the old attribute for upgrade.
     */
    public static final String ATT_IIQ_TEMPLATE_APPLICATION = "IIQTemplateApplication";

    /**
     * Attribute indicating Owner field on the Provisioning Policy form,
     * stored onto the attributes map in the form.
     */
    public static final String ATT_IIQ_TEMPLATE_OWNER_DEFINITION = "IIQTemplateOwnerDefinition";

    //
    // Special Owner Constants
    // These can be used instead of an Identity name for form and field owners
    //

    /**
     * Value that can be used in the owner definition to reference
     * the owner of the object containing this form (role or application).
     */
    public static final String OWNER_PARENT = "IIQParentOwner";

    /**
     * Value that can be used in the owner definition to reference
     * the owner of the role containing this form.
     * This is what the form editor currently sets.
     *
     * jsl - this needs to die, we don't need two owner constants,
     * convert everything to use OWNER_PARENT.
     */
    public static final String OWNER_ROLE = "IIQRoleOwner";

    /**
     * Value that can be used in the owner definition to reference
     * the owner of the application associated with this form.
     *
     * jsl - this needs to die, we don't need two owner constants,
     * convert everything to use OWNER_PARENT.
     */
    public static final String OWNER_APPLICATION = "IIQApplicationOwner";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // _name etc. inherited from SailPointObject

    /**
     * Form types
     */
    @XMLClass(xmlname = "FormType")
    public static enum Type {

        // Form used when creating a new identity in LCM
        CreateIdentity,

        // Form used when updating an existing identity in LCM
        UpdateIdentity,

        // Form used when self-registering a new identity
        RegisterIdentity,

        // Form for configuring a live report
        Report,

        // Form used in a standalone
        Application,
        Role,
        Workflow,
        WorkflowConfig,

        // Generic object editing forms for application policies.
        // Brought over from the Template merger in 7.0.
        // Could converrt CreateIdentity and UpdateIdentity to use these
        // with an objectType of "Identity"
        Create,
        Update,
        Delete,

        // Forms for certain types of actions in application policiies
        Enable,
        Disable,
        Unlock,
        ChangePassword,

        // Role provisioning policy forms
        AssignRole,
        DeassignRole;

        public String getMessageKey() { 
           return "form_type_" + Util.splitCamelCase(this.name()).replace(" ","_").toLowerCase();
        }
    }

    /**
     * Not currently used, but could be useful to 
     * configure top-level forms for different pages.
     */
    Type _type;

    /**
     * When Type is Create, Update, or Delete, this has the type of the object.
     * For application provisioning policy forms, this will be the name of
     * one of the Schemas.  
     */
    String _objectType;
    
    /**
     * If true, form will be hidden in Reference form list
     * Needs to be a first-class property so it can be used in queries.
     */
    boolean _hidden;

    /**
     * The associated application, valid only for role provisioning policies, 
     * must match one of the applications referenced by the profiles.
     */
    Application _application;

    /**
     * List of items (Section, Field, FormRef).
     */
    List<FormItem> _items;

    /**
     * Random attributes.
     */
    Attributes<String,Object> _attributes;

    /**
     * Transient field used to look up field objects during assimilation.
     */
    Map<String,Field> _fieldMap;

    /**
     * Transient field used to convey the name of the user that
     * should be shown this form. Currently this is set by Provisioner
     * it might have other uses. The inherited _owner field is not currently used
     * for two reasons: _owner is an Identity and those are relatively
     * difficult to deal with in workflow scripts, and it might be good for form
     * to have administrative owners, like is done for other
     * configuration objects, that are different than the viewer
     * of the form.
     */
    String _targetUser;

    /**
     * An enumeration of usage constants for for application forms.
     *
     * @ignore
     * These are not used for role forms though we will eventually
     * need to make a distinction between assignment and deassignment
     * forms. It is convenient to use the same enumeration for both
     * and it's easier to extend if we use forms somewhere else.
     * But it's a bit ugly because forms used in one context will
     * only allow a subset of the enumeration.
     *
     * Get rid of this whenever form editor of the application, role and
     * Identity provisioning policy starts using form type instead of usage!
     */
    public static enum Usage {

        Create,
        Update,
        Delete,
        Assign,
        Deassign,

        // A quick note about the following usages:
        // Groups, Registration, and Identity policies are not persisted as Forms,
        // but since we don't have a proper Form editor we use a repurposed
        // Form editor that converts them to Forms as an interim step.
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
           return "form_usage_" + Util.splitCamelCase(this.name()).replace(" ","_").toLowerCase();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Form() {
    }

    /**
     * This tells Hibernate that when this object is inside another it is 
     * always XML serialized rather than being a top-level Hibernate object.
     */
    public boolean isXml() {
        return true;
    }

    /** 
     * Fully load the Form object from Hibernate.
     */
    public void load() {
        // always need this?
        if (_application != null)
            _application.load();

        if (_items != null) {
            for(FormItem item : _items) {
                item.load();
            }
        }
    }

    @XMLProperty
    public Type getType() {
        return _type;
    }

    public void setType(Type type) {
        _type = type;
    }

    public boolean isType(Type t) {
        return (_type != null) ? _type.equals(t) : false;
    }

    @XMLProperty
    public String getObjectType() {
        return _objectType;
    }

    public void setObjectType(String type) {
        _objectType = type;
    }

    @XMLProperty
    public boolean isHidden() {
        return _hidden;
    }

    public void setHidden(boolean _hidden) {
        this._hidden = _hidden;
    }

    /**
     * The associated application if this is a role provisioning policy.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    /**
     * The associated application if this is a role provisioning policy.
     *
     * The extended attribute ATT_IIQ_TEMPLATE_APPLICATION is
     * deprecated in 7.0, as we now have a first-class Application
     * <ApplicationRef> in the <Form>.
     * @param res Resolver
     * @throws GeneralException
     */
    public Application getApplication(Resolver res) throws GeneralException {
        if (_application != null) {
            return _application;
        } else {
            // Convert old convention of ATT_IIQ_TEMPLATE_APPLICATION
            // reference to first-class Application object.
            Reference ref = null;
            Object obj = get(ATT_IIQ_TEMPLATE_APPLICATION);
            if (obj instanceof Reference) {
                ref = (Reference) obj;
            }
            if (ref != null && ref.getIdOrName() != null) {
                if (ref.getId() != null) {
                    _application = res.getObjectById(Application.class, ref.getId());
                } else {
                    _application = res.getObjectByName(Application.class, ref.getName());
                }
            }
        }

        return _application;
    }

    public void setApplication(Application r) {
        _application = r;
    }

    /**
     * Used only by form editor.
     * @ignore
     * From 7.0, we now have first-class application object,
     * hence clear the older template application reference.
     */
    public void setRealApplication(Application r) {
        _application = r;

        // make sure this doesn't linger and look confusing
        put(ATT_IIQ_TEMPLATE_APPLICATION, null);
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<FormItem> getItems() {
        return _items;
    }

    public void setItems(List<FormItem> items) {
        _items = withdrawFormRefWrapper(items);
        _fieldMap = null;
    }

    public void add(FormItem item) {
        if (item != null) {
            if (_items == null)
                _items = new ArrayList<FormItem>();
            _items.add(item);
            _fieldMap = null;
        }
    }

    public void add(List<FormItem> items) {
        if (items != null) {
            if (_items == null)
                _items = new ArrayList<FormItem>();
            _items.addAll(items);
            _fieldMap = null;
        }
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> atts) {
        _attributes = atts;
    }

    @XMLProperty
    public String getTargetUser() {
        return _targetUser;
    }

    public void setTargetUser(String s) {
        _targetUser = s;
    }

    public DynamicValue getOwnerDefinition() {
        DynamicValue owner  = null;
        Object obj = get(ATT_IIQ_TEMPLATE_OWNER_DEFINITION);
        if (obj instanceof DynamicValue) {
            owner = (DynamicValue)obj;
        }
        return owner;
    }

    public void setOwnerDefinition(DynamicValue owner) {
        put(ATT_IIQ_TEMPLATE_OWNER_DEFINITION, owner);
    }

    /**
     * True if a form matches a type and objectType.
     * Normally the types and object types are set and must match.  
     *
     * Missing object types are supported for backward compatibility with
     * older converted Templates created before we had object types.
     * 
     * A missing type is less common, and would have been from a very old
     * template where the default was assumed to be a Create template.  
     */
    public boolean isMatch(Form.Type type, String objectType) {
        return (((_objectType == null && objectType.equalsIgnoreCase(Application.SCHEMA_ACCOUNT)) ||
                 (_objectType != null && _objectType.equalsIgnoreCase(objectType))) &&
                ((_type == type) ||
                 (_type == null && type == Type.Create)));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Buttons
    //
    // These used to be in their own list, but are now in the unifieid
    // item list.  For backward compatibility we still need to support
    // reading the old button list from Hibernate and converting it
    // to the item list, and support the old get/set methods in case
    // custom code is using it (unlikely).
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Buttons are now in the items list, but need to support this
     * property to upgrade older Hibernate tables to the new unified list.
     */
    public List<Button> getHibernateButtons() {
        return null;
    }

    public void setHibernateButtons(List<Button> buttons) {
        if (buttons != null) {
            for (Button button : buttons) {
                add(button);
            }
        }
    }

    /**
     * Old interface for getting the discrete button list.
     * This has to be named differently from the Hibernate accessor
     * because we want getHibernateButtons to always return null.
     */
    public void setButtons(List<Button> l) {
        //bug26350 we need to remove the old buttons before setting the new ones
        removeButtons();
        setHibernateButtons(l);
    }
    
    private void removeButtons() {
        List<Button> buttons = getButtons();
        if (null != buttons) {
            for (Button button : buttons){
                remove(button);
            }
        }
    }

    public void remove(Button b) {
        if (_items != null)
            _items.remove(b);
    }

    /**
     * Old interface for getting the discrete button list.
     */
    public List<Button> getButtons() {
        List<Button> buttons = null;
        if (_items != null) {
            for (FormItem item : _items) {
                if (item instanceof Button) {
                    if (buttons == null)
                        buttons = new ArrayList<Button>();
                    buttons.add((Button)item);
                }
            }
        }
        return buttons;
    }
    
    /**
     * Search for a matching button.
     * Action, parameter, and value must match.
     * Technically we should recurse but since we only support top-level 
     * buttons at the moment, just search the outer list.
     */
    public Button getMatchingButton(Button other) {
        Button found = null;
        if (_items != null) {
            for (FormItem item : _items) {
                if (item instanceof Button) {
                    Button b = (Button)item;
                    if (b.hasSameAction(other)) {
                        found = b;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Find the first button that was clicked (should only be one).
     * Technically we should recurse but since we only support top-level 
     * buttons at the moment, just search the outer list.
     */
    public Button getClickedButton() {
        Button found = null;
        if (_items != null) {
            for (FormItem item : _items) {
                if (item instanceof Button) {
                    Button b = (Button)item;
                    if (b.isClicked()) {
                        found = b;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Add all of the Buttons to the item list.
     * @ignore
     * jsl - where is this used?  
     */
    public void addButtons(List<Button> buttons) {
        if (buttons != null) {
            for (FormItem item : buttons) {
                add(item);
            }
        }
    }

    /**
     * Clears out all buttons in the form.
     */
    public void clearButtons() {
        if (_items == null) {
            return;
        }

        for (int i = _items.size() - 1; i >= 0; i--) {
            FormItem item = _items.get(i);
            if (item instanceof Button) {
                _items.remove(i);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Propertites
    //
    //////////////////////////////////////////////////////////////////////

    public String getPageTitle() {
        return getString(ATT_PAGE_TITLE);
    }

    public void setPageTitle(String s) {
        put(ATT_PAGE_TITLE, s);
    }

    public String getTitle() {
        return getString(ATT_TITLE);
    }

    public void setTitle(String s) {
        put(ATT_TITLE, s);
    }
 
    public String getSubtitle() {
        return getString(ATT_SUBTITLE);
    }

    public void setSubtitle(String s) {
        put(ATT_SUBTITLE, s);
    }
    
    public void setReadOnly(boolean b) {
        put(ATT_READ_ONLY, b);
    }

    public boolean isReadOnly() {
        return getBoolean(ATT_READ_ONLY);
    }

    public void setHideIncompleteFields(boolean b) {
        put(ATT_HIDE_INCOMPLETE_FIELDS, b);
    }

    public boolean isHideIncompleteFields() {
        return getBoolean(ATT_HIDE_INCOMPLETE_FIELDS);
    }

    public boolean isIncludeHiddenFields() {
        return getBoolean(ATT_INCLUDE_HIDEN_FIELDS);
    }

    public void setIncludeHiddenFields(boolean b) {
        put(ATT_INCLUDE_HIDEN_FIELDS, b);
    }

    public boolean isWizard(){
        return getBoolean(ATT_WIZARD);
    }

    public void setWizard(boolean wizard){
        put(ATT_WIZARD, wizard);
    }

    public String getLabelAlign() {
        return getString(ATT_LABEL_ALIGN);
    }

    public void setLabelAlign(String align){
        put(ATT_LABEL_ALIGN, align);
    }

    public void setMergeProfiles(boolean b) {
        put(ATT_MERGE_PROFILES, b);
    }

    public boolean isMergeProfiles() {
        return getBoolean(ATT_MERGE_PROFILES);
    }

    /**
     * Get the path expression for the form
     * @return Base Path expression, or null if none set.
     */
    public String getBasePath() {
        return getString(ATT_BASE_PATH);
    }

    /**
     * Sets the given path expression on this form. If null, any existing
     * base path value will be removed.
     * @param path Base Path expression
     */
    public void setBasePath(String path) {
        put(ATT_BASE_PATH, path);
    }

    /**
     * Check if this form has a base path
     * @return Returns true if this form has a base path.
     */
    public boolean hasBasePath() {
        return (getBasePath() != null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Older name for get(), not sure if this was used outside this
     * class, but keep it around.
     */
    public Object getAttribute(String name) {
        return get(name);
    }

    /**
     * Get a form attribute.
     */
    public Object get(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    /**
     * Set a form attribute.
     */
    public void put(String name, Object value) {
        if (name != null) {
            // sigh, I wish Attributes would keep it clean like this
            if (value != null) {
                if (_attributes == null)
                    _attributes = new Attributes<String,Object>();
                _attributes.put(name, value);
            }
            else if (_attributes != null) {
                _attributes.remove(name);
            }
        }
    }

    public void put(String name, boolean b) {
        // Avoid clutter by collapsing false
        if (b)
            put(name, "true");
        else
            put(name, null);
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    /**
     * Return true if there are any fields in this form.
     */
    public boolean hasFields() {
        Map<String,Field> fields = getFieldMap();
        return (fields != null && fields.size() > 0);
    }

    /**
     * Return true if a form has top level fields
     * without section wrapper.
     *
     * @ignore
     * This goes only one level deep in form item list,
     * hence it does not consider section wrapped fields.
     */
    public boolean hasWrapperLessFields() {
        boolean found = false;
        for (FormItem formItem : Util.iterate(_items)) {
            if (formItem instanceof Field) {
                found = true;
                break;
            }
        }

        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Section Management
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Temporary for backward compatibility with code that only
     * understands a top-level Section list.
     */
    public List<Section> getSections() {
        List<Section> sections = null;
        for (FormItem item : Util.iterate(_items)) {
            if (item instanceof Section) {
                if (sections == null)
                    sections = new ArrayList<Section>();
                sections.add((Section)item);
            }
        }
        return sections;
    }

    /**
     * Temporary for backward compatibility with code that only
     * understands a top-level Section list.  We assume if you
     * are calling this that the entire item list is replaced.
     */
    public void setSections(List<Section> l) {
        if (l == null) {
            _items = null;
        }
        else {
            _items = new ArrayList<FormItem>();
            _items.addAll(l);
        }
        _fieldMap = null;
    }

    /**
     * Look for a section by name.
     * @ignore
     * NOTE: This only looks for top-level sections, one might be needed
     * that looks for child sections.
     */
    public Section getSection(String name) {
        // we can only locate sections by name
        // sections without names are considered part of the
        // static skeleton
        Section found = null;
        if (name != null && _items != null) {
            for (FormItem item : _items) {
                if (item instanceof Section) {
                    Section s = (Section)item;
                    if (name.equals(s.getName())) {
                        found = s;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Look for a section by index.
     * Useful in workflows when a script is used to alter a
     * static template form and you know where the sections are.
     * Only works with top-level sections.
     * @ignore
     * jsl - where was this used?  Seems fragile
     * May now have to deal with Section/Field mixtures
     */
    public Section getSection(int index) {
        Section found = null;
        if (_items != null) {
            int sectionsFound = 0;
            for (FormItem item : _items) {
                if (item instanceof Section) {
                    if (index == sectionsFound) {
                        found = (Section)item;
                        break;
                    }
                    sectionsFound++;
                }
            }
        }
        return found;
    }

    /**
     * Return the first section.
     * Useful in workflows when a script is used to alter a
     * static template form and you know where the sections are.
     */
    public Section getSection() {
        return getSection(0);
    }

    /**
     * This was prior to 7.0 but I don't want to support this any more.
    public void add(int index, Section s) {
        if (s != null) {
            if (_sections == null)
                _sections = new ArrayList<Section>();
            if (index < 0 || index >= _sections.size())
                _sections.add(s);
            else
                _sections.add(index, s);

            // make sure this gets rebuilt
            _fieldMap = null;
        }
    }
    */

    public void remove(Section s) {
        // only works with top level sections, should recurse!!
        if (_items != null)
            _items.remove(s);
    }

    /**
     * @ignore
     * jsl - find out where this is used and comment on why
     * it is here.
     */
    public void setFieldValue(String fieldName, Object value){
        if (_items != null) {
            for (FormItem item : _items) {
                if (item instanceof Field) {
                    Field f = (Field)item;
                    if (fieldName.equals(f.getName())) {
                        f.setValue(value);
                        // assume only one
                        break;
                    }
                }
                else if (item instanceof Section) {
                    Section s = (Section)item;
                    Field field = s.getField(fieldName);
                    if (field != null) {
                        field.setValue(value);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Field Search
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Allows a field map to be cleared so that it has to be
     * rebuilt based on updated sections 
     */
    public void clearFieldMap() {
        _fieldMap = null;
    }

    /**
     * Build a lookup map for fields in a form. 
     * This only includes fields that have non-null names.
     *
     * @ignore
     * NOTE: This assumes that a field with a given name can only
     * appear once in a form.  That's okay for now, but may need to 
     * think about "paged" or "tabbed" forms where the same field
     * may need to appear in several sections.  Probably will have
     * to do this with some kind of reference object rather than 
     * duplicating fields.
     */
    private Map<String,Field> getFieldMap() {

        if (_fieldMap == null) {
            _fieldMap = new HashMap<String,Field>();
            populateFieldMap(_fieldMap, _items);
        }
        return _fieldMap;
    }
    
    /**
     * Recursively walk over form items building a Map of Fields keyed by name.
     */
    private void populateFieldMap(Map<String,Field> map, List<FormItem> items) {

        if (items != null) {
            for (FormItem item : items) {
                if (item instanceof Field) {
                    Field f = (Field)item;
                    String name = f.getName();
                    if (name != null) {
                        _fieldMap.put(name, f);
                    }
                }
                else if (item instanceof Section) {
                    Section s = (Section)item;
                    populateFieldMap(map, s.getItems());
                }
            }
        }
    }

    /**
     * Lookup a field by name.
     */
    public Field getField(String name) {

        Map<String,Field> map = getFieldMap();
        Field field = map.get(name);
        
        // Massage the name a bit if this is an identity field
        if (field == null) {
            name = getIdentityFieldName(name);
            field = map.get(name);
            if (field != null && !field.isType(Field.TYPE_IDENTITY)) {
                field = null;
            }
        }
        
        if (field == null) {
            String attrName = null;
            String appName = null;
            // We need to handle generated field names differently
            if (Util.isNotNullOrEmpty(name) && name.indexOf(":") > 0) {
                String[] parts = name.split(":");
                appName = parts[0];
                attrName = parts[parts.length - 1];
                Set<String> fieldNames = map.keySet();
                for (String currentFieldName : Util.iterate(fieldNames)) {
                    if (currentFieldName.indexOf(":") > 0) {
                        String[] fieldNameParts = currentFieldName.split(":");
                        String fieldAttrName = fieldNameParts[fieldNameParts.length - 1];
                        field = map.get(currentFieldName);
                        if (field != null) {
                            String fieldAppname = field.getApplication();
                            if (Util.nullSafeEq(fieldAppname, appName, true)
                                    && Util.nullSafeEq(fieldAttrName, attrName,
                                            true)) {
                                break;
                            } else {
                                field = null;
                            }
                        }
                    }
                }
            }
        }
                
        return field;
    }

    /**
     * Lookup a field by name in a form that uses qualified names.
     * The assumption is that the qualifier for the given field is the
     * same as the field being searched for, so that searching can be done using
     * the qualified name without the caller having to worry about 
     * the qualification syntax.
     */
    public Field getField(Field peer, String name) {

        Field found = null;
        if (name != null) {
            String qualifiedName = name;
            if (peer != null) {
                // currently the only qualification strategy is to prefix
                // the app name, this may change...
                String appname = peer.getApplication();
                if (appname != null)
                    qualifiedName = appname + ":" + name;
            }
            found = getField(qualifiedName);
        }
        return found;
    }

    /**
     * Return fields of the form.
     *
     * @ignore
     * This goes only one level deep in form item list,
     * so it does not consider section wrapped fields.
     */
    public List<Field> getFields() {
        List<Field> fields = new ArrayList<Field>();
        for (FormItem formItem : Util.iterate(_items)) {
            if (formItem instanceof Field) {
                fields.add((Field)formItem);
            }
        }

        return fields;
    }

    /**
     * Return all fields of the form.
     *
     * @ignore
     * This goes two levels deep in the form item list,
     * so it considers section wrapped fields as well.
     */
    public List<Field> getEntireFields() {
        List<Field> fields = new ArrayList<Field>();

        for (FormItem formItem : Util.iterate(_items)) {
            if (formItem instanceof Field) {
                fields.add((Field) formItem);
            } else if (formItem instanceof Section) {
                Section s = (Section) formItem;
                for (FormItem item : Util.iterate(s.getItems())) {
                    if (item instanceof Field) {
                        fields.add((Field) item);
                    }
                }
            }
        }

        return fields;
    }

    // jsl - WTF is this?
    // it's something for web/lcm/AttributesRequestBean
    // where is this naming convention defined?  this doesn't belong here...
    public String getIdentityFieldName(String name) {
        if (name != null && name.endsWith("-field")) {
            int endOfName = name.length() - "-field".length();
            int startOfName = this.getName().length() + "-form-".length();
            return name.substring(startOfName, endOfName);
        } else {
            return name;
        }
    }

    /**
     * Remove a field by name.
     * If the section containing the field becomes empty, remove the section.
     * @ignore
     * jsl - where is this used, form editor?
     * Got more complicated with the FormItem merger, the way this
     * was originally implemented it would only collapse the Section that
     * contained the Field, so we can't just universally collapse empty
     * sections.
     */
    public void removeField(String name) {
        
        if (_items != null) {
            removeField(_items, name);
        }
    }
    
    private boolean removeField(List<FormItem> items, String name) {
        
        boolean removed = false;
        if (items != null) {
            Iterator<FormItem> it = items.listIterator();
            while (it.hasNext()) {
                FormItem item = it.next();
                if (item instanceof Field) {
                    Field field = (Field) item;
                    if (Util.nullSafeEq(name, field.getName())) {
                        it.remove();
                        removed = true;
                        break;
                    }
                }
                else if (item instanceof Section) {
                    Section s = (Section)item;
                    if (removeField(s.getItems(), name)) {
                        // this section collapses if empty
                        if (Util.size(s.getItems()) == 0) {
                            it.remove();
                            // think: since we've never really supported
                            // nested sections the old code would not have
                            // removed more than one level, what to do?
                            // removed = true;
                            break;
                        }
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Return FormRef from the item list.
     * @ignore
     * No need to check FormRefs on the Section.
     */
    public FormRef getFormRef() {
        FormRef ref = null;
        for (FormItem item : Util.iterate(_items)) {
            if (item instanceof FormRef) {
                ref = (FormRef)item;
                break;
            }
        }

        return ref;
    }

    /**
     * Add the FormRef to the item list.
     */
    public void setFormRef(FormRef ref) {
        // remove the ones that are there now
        if (_items != null) {
            ListIterator<FormItem> it = _items.listIterator();
            while (it.hasNext()) {
                FormItem item = it.next();
                if (item instanceof FormRef) {
                    it.remove();
                }
            }
        }

        // then add the new one
        if (ref != null) {
            add(ref);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Form alteration to support new model brought in 7.0 as
    // a part of ETN#24933
    //
    // In 7.1, it is necessary for upgrading older databases.
    //////////////////////////////////////////////////////////////////////

    /**
     * In the new model we don't need the wrapper Section to wrap the
     * FormRefs however we might still encounter it in the database Forms
     * or user can enter FormRefs in the section wrapper.
     */
    private List<FormItem> withdrawFormRefWrapper(List<FormItem> items) {
        if (Util.size(items) == 1) {
            FormItem first = items.get(0);
            if (first instanceof Section) {
                Section s = (Section)first;
                if (s.isWrapper()) {
                    return s.getItems();
                }
            }
        }

        return items;
    }

    /**
     * Converts form usage to type
     * @param usage The form usage
     *
     * @ignore
     * Get rid of this whenever form editor of the application, role and
     * Identity provisioning policy starts using form type instead of usage!
     */
    public Type convertUsage(Form.Usage usage) {

        // if usage is null and this is an Application policy,
        // it should default to Create but we can't know that here,
        // it is done in ImportVisitor

        Type type = null;
        if (usage != null) {
            switch (usage) {

            case Register:
                type = Type.RegisterIdentity;
                break;
            case CreateIdentity:
                type = Type.CreateIdentity;
                break;
            case UpdateIdentity:
                type = Type.UpdateIdentity;
                break;

            case Create:
                type = Type.Create;
                break;
            case Update:
                type = Type.Update;
                break;
            case Delete:
                type = Type.Delete;
                break;
            case Enable:
                type = Type.Enable;
                break;
            case Disable:
                type = Type.Disable;
                break;
            case Unlock:
                type = Type.Unlock;
                break;
            case ChangePassword:
                type = Type.ChangePassword;
                break;

            case Assign:
                type = Type.AssignRole;
                break;
            case Deassign:
                type = Type.DeassignRole;
                break;

            // The Group specific usages have been deprecated for some time,
            // but we still need to support them for backward compatibility

            case CreateGroup:
                type = Type.Create;
                break;
            case UpdateGroup:
                type = Type.Update;
                break;
            case EditGroup:
                type = Type.Update;
                break;
            case DeleteGroup:
                type = Type.Delete;
                break;
            }
        }
        return type;
    }

    /**
     * Converts form type to usage
     * @param type The Type of form
     *
     * @ignore
     * Get rid of this whenever form editor of the application, role and
     * Identity provisioning policy starts using form type instead of usage!
     */
    public Usage convertFormType(Form.Type type) {
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
            case RegisterIdentity:
                usage = Usage.Register;
                break;
            case CreateIdentity:
                usage = Usage.CreateIdentity;
                break;
            case UpdateIdentity:
                usage = Usage.UpdateIdentity;
                break;
            default:
                break;
            }
        }

        return usage;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // FieldIterator.
    // Now it also supports removal of fields while iterating.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convenience method to iterate over fields nested in sections.
     */
    public Iterator<Field> iterateFields() {

        return new FieldIterator(this);
    }

    public static class FieldIterator implements Iterator<Field> {

        private Field field;

        private boolean lookedForNext = false;
        private Iterator<? extends FormItem> currentIterator;
        
        private Stack<Iterator<? extends FormItem>> stack;
        
        public FieldIterator(Form form) {

            stack = new Stack<Iterator<? extends FormItem>>();
            if (form.getItems() != null) {
                stack.add(form.getItems().iterator());
            }
            moveCurrentIterator();
        }
        
        public boolean hasNext() {

            if (!lookedForNext) {
                lookForNextField();
            } 

            lookedForNext = true;

            return (field != null);
        }

        private boolean lookForNextField() {

            while (currentIterator != null && (currentIterator.hasNext() || moveCurrentIterator())) {
                
                if (currentIterator.hasNext()) {
                    FormItem item = currentIterator.next();
                    if (item instanceof Field) {
                        field = (Field) item;
                        return true;
                    } else if (item instanceof Button) {
                        // keep moving
                    } else if (item instanceof Section) {
                        stack.push(currentIterator);
                        Section section = (Section) item;
                        currentIterator = Util.safeIterable(section.getItems()).iterator();
                    } else if (item instanceof FormRef) {
                        // Do nothing here. FormRef will be expanded in Formicator.
                    } else {
                        // throw this exception to be future safe
                        throw new IllegalStateException("Unknown formItem type");
                    }
                } 
            }

            field = null;
            return false;
        }
        
        private boolean moveCurrentIterator() {
            
            if (stack.size() > 0) {
                currentIterator = stack.pop();
            } else {
                currentIterator = null;
            }
            return currentIterator != null;
        }

        public Field next() {

            if (!lookedForNext) {
                lookForNextField();
            }
            
            lookedForNext = false;
            
            return field;
        }

        public void remove() {

            if (field != null && currentIterator != null) {
                currentIterator.remove();
            }
        }
    }

    /**
     * Unit testing utility to build a Map containing all field values.
     */
    public Map<String,Object> getFieldValues() {

        Map<String,Object> values = new HashMap<String,Object>();
        Iterator<Field> it = iterateFields();
        while (it.hasNext()) {
            Field field = it.next();
            String name = field.getName();
            if (name != null)
                values.put(name, field.getValue());
        }
        return values;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Section
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Forms use sections to organize sets of fields.
     * In theory sections can contain subsections to define layout, 
     * not using that yet.  The model and Formicator support nested fields
     * but the editor and renderer do not.
     */
    @XMLClass
    public static class Section extends AbstractXmlObject 
        implements FormItem {

        //
        // Possible values for the _type field.
        // The default rendering of a section is a two column form with
        // editable field values.
        //

        /**
         * Type that causes the section to be rendered as a two column table
         * with non-editable fields. This is typically used for informational
         * tables to give the form filler outer some context so they know what
         * they are doing.
         */
        public static final String TYPE_DATA_TABLE = "datatable";

        /**
         * Type that causes the section to be rendered as non-bordered text.
         * If the section has more than one field, they are rendered with
         * breaks in between. This is used for information text, often at
         * the top of a page but can also appear between form sections to
         * provide instruction.
         */
        public static final String TYPE_TEXT = "text";

        /**
         * An option to the datatable renderer to cause it to suppress the
         * rendering of a field with a null value.
         */
        public static final String ATT_HIDE_NULLS = "hideNulls";

        /**
         * fields within the section rendered as readOnly.
         */
        public static final String ATT_READ_ONLY = "readOnly";

        /**
         * fields within the section kept hidden while rendering.
         */
        public static final String ATT_HIDDEN = "hidden";

        /**
         * The subtitle attribute can be used by form renderer javascript to
         * show description for the fields after the fieldset title.
         */
        public static final String ATT_SUBTITLE = "subtitle";

        /**
         * The internal name for the section, this might be referenced
         * by Field objects in role and application forms.
         */
        String _name;

        /**
         * When non-null causes a label to be placed above the section fields.
         * This might be a message catalog key or literal text.
         * It can contain $() variable references.
         * @ignore
         * TODO: Should allow scriptlets here!
         */
        String _label;

        /**
         * Optional rendering type.
         * The default is a two column form containing editable fields.
         */
        String _type;

        /**
         * Extended attributes that can influence the renderer.
         */
        Attributes<String, Object> _attributes;

        /**
         * This was in the original model but it has never been used.
         * Now that there is a _type this might never be used, but it will be kept around
         * for awhile. The intent was this be used for simple qualificiations
         * to the type like maybe "vertical" or "horizontal" for simple 
         * field collections.  
         */
        String _layout;

        /**
         * An optional priority that is used during form assembly
         * to influence the order of fields.
         */
        int _priority;

        /**
         * Number of columns contained in the form section.
         */
        int _columns;

        /**
         * The contained fields or sub sections.
         */
        List<FormItem> _items;

        public Section() {
        }

        public Section(String name) {
            _name = name;
        }

        public void load() {
            if (_items != null) {
                for (FormItem item : _items) {
                    item.load();
                }
            }
        }

        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }

        @XMLProperty
        public String getLabel() {
            return _label;
        }

        public void setLabel(String s) {
            _label = s;
        }

        @XMLProperty
        public String getType() {
            return _type;
        }

        public void setType(String s) {
            _type = s;
        }

        @XMLProperty(mode = SerializationMode.UNQUALIFIED)
        public Attributes<String, Object> getAttributes() {
            return _attributes;
        }

        public void setAttributes(Attributes<String, Object> attributes) {
            this._attributes = attributes;
        }

        public void addAttribute(String attr, Object value){
            put(attr, value);
        }

        /**
         * Set a section attribute.
         */
        public void put(String name, Object value) {
            if (_attributes == null) {
                _attributes = new Attributes<String,Object>();
            }
            // sigh, I wish Attributes would keep it clean like this
            _attributes.putClean(name, value);
        }

        @XMLProperty
        public String getLayout() {
            return _layout;
        }

        public void setLayout(String s) {
            _layout = s;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<FormItem> getItems() {
            return _items;
        }

        public void setItems(List<FormItem> l) {
            _items = l;
        }

        @XMLProperty
        public int getPriority() {
            return _priority;
        }

        public void setPriority(int p) {
            _priority = p;
        }

        public int getColumns() {
            return _columns;
        }

        @XMLProperty
        public void setColumns(int columns) {
            this._columns = columns;
        }

        /**
         * Return true if this section is interactive - meaning it has
         * user inputs rather than just containing display elements.
         */
        public boolean isInteractive(){
            return !TYPE_DATA_TABLE.equals(_type) && !TYPE_TEXT.equals(_type);
        }
        
        /**
         * Return whether this section is hidden. This is a pseudo-property
         * that looks at Field.ATTR_HIDDEN in the attributes map.
         * 
         * @throws RuntimeException  If the hidden attribute is a DynamicValue
         *    and the form has not yet been expanded.
         */
        public boolean isHidden() {
            return getBooleanDynamicValue(Field.ATTR_HIDDEN);
        }

        /**
         * Return the value of an attribute that might have been dynamically
         * calculated. This throws an exception if the section has not been expanded.
         */
        private boolean getBooleanDynamicValue(String attribute) {
            boolean val = false;
            if (null != _attributes) {
                if (DynamicValue.isDynamicValue(_attributes, attribute)) {
                    throw new RuntimeException("Form is not expanded.");
                }
                val = _attributes.getBoolean(attribute);
            }
            return val;
        }

        /**
         * Return true if this is a wrapper section, meaning it doesn't define
         * any significant properties for rendering and it contains a FormRef
         * in it's item list. Wrapper sections can be removed and the contained
         * FormRef merged with the parent.
         *
         * This is used when assembling FormRef's after the introduction
         * of Sectionless forms in 7.0.
         */
        public boolean isWrapper() {

            // most important is label
            // _layout was never used and I don't think _type was either,
            // special formatting was usually done in _attributes
            // _columns is significant
            // _priority only applies to provisioning policy forms
            // Contains single item and that is of FormRef

            return (_label == null &&
                    _type == null &&
                    _columns == 0 &&
                    (_attributes == null || _attributes.size() == 0) &&
                    (Util.size(_items) == 1 && (_items.get(0) instanceof FormRef)));
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Field Management
        //
        //////////////////////////////////////////////////////////////////////

        public Field getField(String name) {
            Field found = null;
            if (name != null && _items != null) {
                for (FormItem item : _items) {
                    if (item instanceof Field) {
                        Field field = (Field)item;
                        if (name.equals(field.getName()))
                            found = field;
                    }
                    else if (item instanceof Section) {
                        Section sub = (Section)item;
                        found = sub.getField(name);
                    }

                    if (found != null)
                        break;
                }
            }
            return found;
        }

        /**
         * Return list of fields form the section item list.
         */
        public List<Field> getFields() {
            List<Field> fields = new ArrayList<Field>();

            if (_items != null) {
                for (FormItem formItem : _items) {
                    if (formItem instanceof Field) {
                        fields.add((Field)formItem);
                    }
                }
            }

            return fields;
        }

        public void add(FormItem item) {
            if (item != null) {
                if (_items == null)
                    _items = new ArrayList<FormItem>();
                _items.add(item);
            }
        }

        /**
         * Appends the given item to the beginning of the field list.
         */
        public void append(FormItem item) {
            if (item != null) {
                if (_items == null)
                    _items = new ArrayList<FormItem>();
                _items.add(0, item);
            }
        }

        /**
         * Return true if there are any fields in this form.
         */
        public boolean hasFields() {
            boolean has = false;
            if (_items != null) {
                for (FormItem item : _items) {
                    if (item instanceof Field)
                        has = true;
                    else if (item instanceof Section)
                        has = ((Section)item).hasFields();
                    
                    if (has)
                        break;
                }
            }
            return has;
        }

        /**
         * Return true if fields with null values should not be rendered on the form.
         */
        public boolean hideNulls(){
            return _attributes != null ? _attributes.getBoolean(ATT_HIDE_NULLS) : false;
        }
        
        public String getSubtitle() {
            return (_attributes == null ? null : _attributes.getString(ATT_SUBTITLE));
        }
        
        public void setSubtitle(String val) {
            put(ATT_SUBTITLE, val);
        }

        public void setReadOnlyDefinition(DynamicValue readOnly) {
            setAttributeDynamicValue(ATT_READ_ONLY, readOnly);
        }

        public DynamicValue getReadOnlyDefinition(Resolver r) throws GeneralException {
            return getAttributeDynamicValue(ATT_READ_ONLY, r);
        }

        public void setHiddenDefinition(DynamicValue hidden) {
            setAttributeDynamicValue(ATT_HIDDEN, hidden);
        }

        public DynamicValue getHiddenDefinition(Resolver r) throws GeneralException {
            return getAttributeDynamicValue(ATT_HIDDEN, r);
        }

        public void setHideNullsDefinition(DynamicValue hidden) {
            setAttributeDynamicValue(ATT_HIDE_NULLS, hidden);
        }

        public DynamicValue getHideNullsDefinition(Resolver r) throws GeneralException {
            return getAttributeDynamicValue(ATT_HIDE_NULLS, r);
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

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Button
    //
    //////////////////////////////////////////////////////////////////////

    @XMLClass
    public static class Button implements FormItem {

        String _label;
        String _action;
        String _parameter;
        String _value;
        boolean _readOnly;
        boolean _clicked;
        boolean _skipValidation;

        Attributes<String,Object> _attributes;

        public Button() {
        }

        public Button(String _label, String _action) {
            this._label = _label;
            this._action = _action;
        }

        public void load() {
        }
        
        /**
         * Need to have a priority to be a FormItem
         * but button order is not determined by this.
         */
        public int getPriority() {
            return 0;
        }

        @XMLProperty
        public String getLabel() {
            return _label;
        }

        public void setLabel(String s) {
            _label = s;
        }

        @XMLProperty
        public String getAction() {
            return _action;
        }

        public void setAction(String s) {
            _action = s;
        }

        public boolean isClicked() {
            return _clicked;
        }

        public void setClicked(boolean clicked) {
            this._clicked = clicked;
        }

        @XMLProperty
        public String getParameter() {
            return _parameter;
        }

        public void setParameter(String s) {
            _parameter = s;
        }

        @XMLProperty
        public String getValue() {
            return _value;
        }

        public void setValue(String s) {
            _value = s;
        }

        /**
         * True if this button is only to be rendered in read-only forms.
         * The concept of a read-only button is strange, but it was added
         * to address the case where users that do not own a work
         * item can still view it. The form needs to be presented so they
         * can see what is in it but they are not allowed to edit it.
         * Since they cannot edit, the usual Ok/Cancel buttons do not
         * make sense, an alternate button like "Return" should be
         * displayed instead. This might be possible with a more flexible
         * form of conditional rendering but that was not available
         * in 5.0. This is simple, solves the problem, and can be upgraded
         * into something more complex later if necessary.
         */
        @XMLProperty
        public boolean isReadOnly() {
            return _readOnly;
        }

        public void setReadOnly(boolean b) {
            _readOnly = b;
        }

        @XMLProperty
        public boolean getSkipValidation() {
            return _skipValidation;
        }

        public void setSkipValidation(boolean b) {
            _skipValidation = b;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Attributes<String,Object> getAttributes() {
            return _attributes;
        }

        public void setAttributes(Attributes<String,Object> atts) {
            _attributes = atts;
        }

        /**
         * Return true if the button has the same action properties.
         * This includes, action, actionParameter and actionParameterValue.
         * @param otherButton The other button to compare to.
         * @return True if this button has the same action as the given button.
         */
        public boolean hasSameAction(Button otherButton){
            return Util.nullSafeEq(otherButton.getAction(), this.getAction(), true) &&
                            Util.nullSafeEq(otherButton.getParameter(), this.getParameter(), true) &&
                            Util.nullSafeEq(otherButton.getValue(), this.getValue(), true);
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Template to Form Conversion
    // Necessary for upgrading older databases.
    //
    //////////////////////////////////////////////////////////////////////

    static public List<Form> convertTemplates(List<Template> templates) {
        List<Form> forms = null;
        for (Template t : Util.iterate(templates)) {
            if (forms == null)
                forms = new ArrayList<Form>();
            forms.add(new Form(t));
        }
        return forms;
    }

    public Form(Template src) {
        this(src, null);
    }

    public Form(Template src, Resolver res) {

        _name = src.getName();
        _description = src.getDescription();

        _type = convertUsage(src.getUsage());
        _objectType = convertObjectType(src);

        setApplication(src.getApplication());
        setMergeProfiles(src.isMerge());
        setOwnerDefinition(src.getOwnerDefinition());

        List<Field> fields = src.getFields();
        for (Field field : Util.iterate(fields)) {
            add(field);
        }

        // In the Template model, FormRef is a property outside the Field list
        // Here it is just an item.  Normally have one or the other, but some
        // unit tests have both.  The FormRef goes at the end.
        // Note that this model is different than the way Forms are currently edited
        // where the FormRef goes inside a wrapper Section.  It is okay for now since
        // until the Template editor is changed we will convert this back to a template.
        add(src.getFormRef());

        // shouldn't be seeing these any more
        if (_application == null && src.getPurview() != null) {
            if (res == null) {
                log.warn("Unable to convert Template with purview to form: " +
                         src.getPurview());
            }
            else {
                // eat it with a log rather than ripple up everywhere
                try {
                    _application = res.getObjectById(Application.class, src.getPurview());
                    if (_application == null) {
                        log.warn("Unresolved purview: " + src.getPurview());
                    }
                }
                catch (Throwable t) {
                    log.error("Exception resolving purview:");
                    log.error(t);
                }
            }
        }
        
    }

    /**
     * Converts template usage to type
     * @param usage The template usage
     * @return Type representation of the given Usage
     */
    static public Type convertUsage(Template.Usage usage) {

        // if usage is null and this is an Application policy,
        // it should default to Create but we can't know that here,
        // it is done in ImportVisitor

        Type type = null;
        if (usage != null) {
            switch (usage) {

            case Register:
                type = Type.RegisterIdentity;
                break;
            case CreateIdentity:
                type = Type.CreateIdentity;
                break;
            case UpdateIdentity:
                type = Type.UpdateIdentity;
                break;

            case Create:
                type = Type.Create;
                break;
            case Update:
                type = Type.Update;
                break;
            case Delete:
                type = Type.Delete;
                break;
            case Enable:
                type = Type.Enable;
                break;
            case Disable:
                type = Type.Disable;
                break;
            case Unlock:
                type = Type.Unlock;
                break;
            case ChangePassword:
                type = Type.ChangePassword;
                break;

            case Assign:
                type = Type.AssignRole;
                break;
            case Deassign:
                type = Type.DeassignRole;
                break;

            // The Group specific usages have been deprecated for some time,
            // but we still need to support them for backward compatibility

            case CreateGroup:
                type = Type.Create;
                break;
            case UpdateGroup:
                type = Type.Update;
                break;
            case EditGroup:
                type = Type.Update;
                break;
            case DeleteGroup:
                type = Type.Delete;
                break;
            }
        }
        return type;
    }

    /**
     * This can be done in two ways, the older specific enumerations
     * for groups, and the new string schema object type.
     */
    private String convertObjectType(Template t) {

        String otype = t.getSchemaObjectType();
        if (otype == null) {
            Template.Usage usage = t.getUsage();
            if (usage != null) {
                switch (usage) {
                case CreateGroup: {
                    otype = Connector.TYPE_GROUP;
                }
                break;
                case UpdateGroup: {
                    otype = Connector.TYPE_GROUP;
                }
                break;
                case EditGroup: {
                    otype = Connector.TYPE_GROUP;
                }
                break;
                case DeleteGroup: {
                    otype = Connector.TYPE_GROUP;
                }
                break;
                }
            }
        }
        return otype;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Get rid of these!!
    //
    //////////////////////////////////////////////////////////////////////

    public Reference getApplicationRef() {
        Reference ref = null;

        if (_application != null) {
            ref = new Reference(_application);
        }
        else {
            // old convention, can't auto upgrade without a Resolver, wait
            // until it is changed in the editor
            Object obj = get(ATT_IIQ_TEMPLATE_APPLICATION);
            if (obj instanceof Reference) {
                ref = (Reference) obj;
            }
        }
        return ref;
    }

    /**
     * I think the editor was using this? Try to remove.
     */
    public String getApplicationId() {
        Reference ref = getApplicationRef();
        return (ref != null) ? ref.getIdOrName() : null;
    }

}

