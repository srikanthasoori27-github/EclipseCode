/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for an ObjectAttribute.
 *
 * Author: Jeff
 */

package sailpoint.web.system;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AttributeSource;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.object.ObjectAttribute.EditMode;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.tools.Message.Type;
import sailpoint.web.BaseDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class ObjectAttributeDTO extends BaseDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // BaseAttributeDefinition
    //
    
    String _originalName;
    String _name;
    String _displayName;
    String _description;
    String _type;
    boolean _multi;
    boolean _required;
    Object _defaultValue;
    String _categoryName;
    List<Object> _allowedValues;

    //
    // ObjectAttribute
    //

    boolean _system;
    boolean _standard;
    boolean _silent;
    boolean _external;
    boolean _groupFactory;
    boolean _searchable;
    int _extendedNumber;
    boolean _namedColumn;
    String _editMode;
    
    String _rule;
    String _listenerRule;
    String _listenerWorkflow;
    List<AttributeSourceDTO> _sources;

    private static Log _log = LogFactory.getLog(ObjectAttributeDTO.class);

    /**
     * Need to remember what ObjectConfig we came from to check named columns.
     */
    String _className;

    //
    // Caches
    //

    List<SelectItem> _editModes;
    List<SelectItem> _types;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build the DTO for a new object.
     */
    public ObjectAttributeDTO(String className) {
        _className = className;
        // so we have something to show in the grid
        _name = "new attribute";
    }

    /**
     * Build the DTO from the persistent object.
     */
    public ObjectAttributeDTO(String className, ObjectAttribute src) {
        
        _className = className;
        _originalName = src.getName();
        _name = src.getName();
        _displayName = src.getDisplayName();
        _description = src.getDescription();
        _type = src.getType();
        _multi = src.isMulti();
        _required = src.isRequired();
        _defaultValue = src.getDefaultValue();
        _categoryName = src.getCategoryName();
        _allowedValues = src.getAllowedValues();
        
        _system = src.isSystem();
        _standard = src.isStandard();
        _silent = src.isSilent();
        _external = src.isExternal();
        _groupFactory = src.isGroupFactory();
        _extendedNumber = src.getExtendedNumber();

        // Don't trust what's out there, it may be stale
        ExtendedAttributeUtil.PropertyMapping pm = ExtendedAttributeUtil.getPropertyMapping(className, src.getName());
        _namedColumn = (pm != null && pm.namedColumn);

        _searchable = (_extendedNumber > 0 || _namedColumn);
        
        // this is allowed to be null in the model
        EditMode emode = src.getEditMode();
        if (emode != null) 
            _editMode = emode.toString();

        Rule rule = src.getRule();
        if (rule != null)
            _rule = rule.getId();

        rule = src.getListenerRule();
        if (rule != null)
            _listenerRule = rule.getId();

        Workflow wf = src.getListenerWorkflow();
        if (wf != null)
            _listenerWorkflow = wf.getId();

        _sources = new ArrayList<AttributeSourceDTO>();
        List<AttributeSource> sources = src.getSources();
        if (sources != null) {
            for (AttributeSource attsrc : sources)
                _sources.add(new AttributeSourceDTO(attsrc));
        }
    }

    /**
     * Clone the DTO for editing.
     * Sigh, it would be really nice if these were XML objects.
     */
    public ObjectAttributeDTO(ObjectAttributeDTO src) {
        
        // be sure to take the other uid so we can match them up later
        setUid(src.getUid());

        _className = src.getClassName();
        _originalName = src.getOriginalName();
        _name = src.getName();
        _displayName = src.getDisplayName();
        _description = src.getDescription();
        _type = src.getType();
        _multi = src.isMulti();
        _required = src.isRequired();
        _defaultValue = src.getDefaultValue();
        _categoryName = src.getCategoryName();
        _allowedValues = src.getAllowedValues();
        
        _system = src.isSystem();
        _standard = src.isStandard();
        _silent = src.isSilent();
        _external = src.isExternal();
        _groupFactory = src.isGroupFactory();
        _extendedNumber = src.getExtendedNumber();
        _namedColumn = src.isNamedColumn();
        _searchable = src.isSearchable();

        _editMode = src.getEditMode();
        _rule = src.getRule();
        _listenerRule = src.getListenerRule();
        _listenerWorkflow = src.getListenerWorkflow();

        _sources = new ArrayList<AttributeSourceDTO>();
        List<AttributeSourceDTO> sources = src.getSources();
        if (sources != null) {
            for (AttributeSourceDTO attsrc : sources)
                _sources.add(new AttributeSourceDTO(attsrc));
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getClassName() {
        return _className;
    }

    //
    // BaseAttributeDefinition properties
    // Note that some of these are not intended to be modified
    //

    public String getOriginalName() {
        return _originalName;
    }
    
    // Note no setter for original name because it is not modifiable
    
    public String getName() {
        return _name;
    }
    
    public void setName(String s) {
        if (!_system && !_standard)
            _name = trim(s);
    }

    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String s) {
        _displayName = trim(s);
    }
    
    /**
     * Used by the grid.
     * Note: {@link #getLocale()} is not really very dependable because the call may be from
     * the REST layer. We should explicitly set the locale using the
     * {@link #getDisplayableName(java.util.Locale)} method.
     */
    @Deprecated
    public String getDisplayableName() {
        return (_displayName != null) ? getDisplayableName(getLocale()) : _name;
    }

    /**
     * Returns the localized name for the attribute.
     * @param locale the user locale
     * {@link #getLocale()} doesn't work if the call is from
     * REST resources.
     */
    public String getDisplayableName(Locale locale) {
        if (_displayName == null) {
            return _name;
        }
        String localized = Internationalizer.getMessage(_displayName, locale);
        if (localized == null) {
            return _displayName;
        } else {
            return localized;
        }
    }
    

    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = trim(s);
    }
    
    public String getType() {
        // bootstrap a value so always match an item in the
        // type selector
        if (_type == null)
            _type = ObjectAttribute.TYPE_STRING;
        return _type;
    }

    public void setType(String s) {
        if (!_system && !_standard)
            _type = s;
    }

    public boolean isMulti() {
        return _multi;
    }

    public void setMulti(boolean b) {
        if (!_system && !_standard)
            _multi = b;
    }

    public boolean isRequired() {
        return _required;
    }
    
    public void setRequired(boolean required) {
        _required = required;
    }
    
    public Object getDefaultValue() {
        return _defaultValue;
    }

    public void setDefaultValue(Object o) {
        if (!_system && !_standard)
            _defaultValue = o;
    }

    public String getCategoryName() {
    	return _categoryName;
    }
    
    public void setCategoryName(String categoryName) {
    	_categoryName = categoryName;
    }
    
    public String getCategoryDisplayName(Locale locale, TimeZone timeZone) {
        return new Message(getCategoryName()).getLocalizedMessage(locale, timeZone);
    }
    
    public List<Object> getAllowedValues() {
        return _allowedValues;
    }
    
    public void setAllowedValues(List<Object> vals) {
        _allowedValues = vals;
    }
    
    //
    // ObjectAttribute properties
    //

    public boolean isSystem() {
        return _system;
    }

    public boolean isStandard() {
        return _standard;
    }

    public boolean isSilent() {
        return _silent;
    }

    public boolean isExternal() {
        return _external;
    }

    public void setExternal(boolean b) {
        _external = b;
    }

    public boolean isGroupFactory() {
        return _groupFactory;
    }
    
    public void setGroupFactory(boolean b) {
        _groupFactory = b;
    }

    public boolean isSearchable() {
        return _searchable;
    }
    
    public void setSearchable(boolean b) {
        _searchable = b;
    }

    public boolean isNamedColumn() {
        return _namedColumn;
    }
    
    public void setNamedColumn(boolean b) {
        _namedColumn = b;
    }

    public int getExtendedNumber() {
        return _extendedNumber;
    }

    /**
     * This is intended only for ObjectConfigDTO when it
     * commits and reassigns the numbers.
     */
    public void setExtendedNumber(int i) {
        _extendedNumber = i;
    }

    // jsl - is this necessary?  Now that we added 
    // _namedColumn UI needs to use isSearchable instead
    // of isExtended
    public boolean isExtended() {
        return (_extendedNumber > 0);
    }

    public String getEditMode() {
        // bootstrap ReadOnly so we always have something
        // that matches a selector menu item
        if (_editMode == null) 
            _editMode = EditMode.ReadOnly.toString();
        return _editMode;
    }

    public void setEditMode(String s) {
        _editMode = s;
    }

    /**
     * Pseudo property for classes that don't have feeds
     * and only need to turn editing on and off.
     */
    public boolean isEditable() {
        return (_editMode != null && 
                !_editMode.equals(EditMode.ReadOnly.toString()));
    }

    public void setEditable(boolean b) {
        if (!b)
            _editMode = EditMode.ReadOnly.toString();
        else
            _editMode = EditMode.Permanent.toString();
    }

    public String getRule() {
        return _rule;
    }

    public void setRule(String s) {
        _rule = trim(s);
    }

    public String getListenerRule() {
        return _listenerRule;
    }

    public void setListenerRule(String s) {
        _listenerRule = trim(s);
    }

    public String getListenerWorkflow() {
        return _listenerWorkflow;
    }

    public void setListenerWorkflow(String s) {
        _listenerWorkflow = trim(s);
    }
    
    public List<AttributeSourceDTO> getSources() {
        return _sources;
    }
    
    /**
     * Pseudo property for the allowed values selector.
     */
    public List<SelectItem> getAllowedValuesSelectItems() {
    	List<SelectItem> result = new ArrayList<SelectItem>();

        if (!_required) {
            result.add(new SelectItem("", WebUtil.localizeMessage(MessageKeys.SELECT_VALUE)));
        }
    	for (Object value : Util.safeIterable(_allowedValues)) {
    	    if (value instanceof Pair) {
    	        Pair item = (Pair) value;
                result.add(new SelectItem(item.getFirst(), (String)item.getSecond()));
    	    } else {
                result.add(new SelectItem(value, value.toString()));
    	    }
    	}
    	
    	return result;
    }


    /**
     * Pseudo property for the edit mode selector.
     */
    public List<SelectItem> getEditModes() {
        
        if (_editModes == null) {

            _editModes = new ArrayList<SelectItem>();

            // TODO: It would be nice if we had an array of 
            // mode definitions to loop over 
            EditMode mode;
            String key;

            mode = EditMode.ReadOnly;
            key = getMessage(MessageKeys.OBJCONFIG_EDIT_MODE_READONLY);
            _editModes.add(new SelectItem(mode, key));

            mode = EditMode.Permanent;
            key = getMessage(MessageKeys.OBJCONFIG_EDIT_MODE_PERMANENT);
            _editModes.add(new SelectItem(mode, key));

            mode = EditMode.UntilFeedValueChanges;
            key = getMessage(MessageKeys.OBJCONFIG_EDIT_MODE_TEMPORARY);
            _editModes.add(new SelectItem(mode, key));
        }

        return _editModes; 
    }

    /**
     * Pseudo property for the type selector.
     */
    public List<SelectItem> getTypes() {

        if (_types == null) {

            _types = new ArrayList<SelectItem>();

            // SECRET might be useful
            // LONG just confuses things
            // PERMISSION is not relevant here

            String type;
            String key;

            type = ObjectAttribute.TYPE_STRING;
            key = getMessage(MessageKeys.OCONFIG_TYPE_STRING);
            _types.add(new SelectItem(type, key));

            type = ObjectAttribute.TYPE_INT;
            key = getMessage(MessageKeys.OCONFIG_TYPE_INT);
            _types.add(new SelectItem(type, key));

            type = ObjectAttribute.TYPE_BOOLEAN;
            key = getMessage(MessageKeys.OCONFIG_TYPE_BOOLEAN);
            _types.add(new SelectItem(type, key));

            type = ObjectAttribute.TYPE_DATE;
            key = getMessage(MessageKeys.OCONFIG_TYPE_DATE);
            _types.add(new SelectItem(type, key));
            
            _types.add(
            		new SelectItem(
	            		ObjectAttribute.TYPE_RULE, 
	            		getMessage(MessageKeys.OCONFIG_TYPE_RULE)));

            _types.add(
            		new SelectItem(
	            		ObjectAttribute.TYPE_IDENTITY, 
	            		getMessage(MessageKeys.OCONFIG_TYPE_IDENTITY)));
        }

        return _types;
    }

    /**
     * Pseudo property for the main attribute grid to show a summary
     * of the sources.  This is what the original identity/link
     * config pages showed, for the new generic ObjectConfig
     * editing pages we can't assume we have sources so we'll
     * show the description instead.
     */
    public String getSourceSummary() {
        return "???";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Currently we don't merge into an existing object, we create a new
     * one from scratch.  This requires that we pull in everything
     * from the original object.
     */
    public ObjectAttribute convert() throws GeneralException {

        ObjectAttribute att = new ObjectAttribute();
        
        att.setName(_name);
        att.setDisplayName(collapse(_displayName));
        att.setDescription(_description);
        att.setMulti(_multi);
        att.setRequired(_required);
        att.setDefaultValue(simplify(_defaultValue));
        att.setCategoryName(collapse(_categoryName));
        att.setAllowedValues(_allowedValues);

        // collapse this to reduce clutter
        if (_type != null && !_type.equals(ObjectAttribute.TYPE_STRING))
            att.setType(_type);

        att.setSystem(_system);
        att.setStandard(_standard);
        att.setSilent(_silent);
        att.setGroupFactory(_groupFactory);
        att.setExternal(_external);

        // if this was new or renamed, have to check again
        // Don't trust what's out there, it may be stale
        ExtendedAttributeUtil.PropertyMapping pm = ExtendedAttributeUtil.getPropertyMapping(_className, _name);
        _namedColumn = (pm != null && pm.namedColumn);

        if (_namedColumn) {
            att.setNamedColumn(true);
            att.setExtendedNumber(0);
        }
        else {
            att.setExtendedNumber(_extendedNumber);
        }

        // collapse the most common case to null to clean up the XML
        if (_editMode != null) {
            EditMode emode = Enum.valueOf(EditMode.class, _editMode);
            if (emode == EditMode.ReadOnly)
                emode = null;
            att.setEditMode(emode);
        }

        att.setRule(resolveById(Rule.class, _rule));
        att.setListenerRule(resolveById(Rule.class, _listenerRule));
        att.setListenerWorkflow(resolveById(Workflow.class, _listenerWorkflow));

        if (_sources != null && _sources.size() > 0) {
            List<AttributeSource> sources = new ArrayList<AttributeSource>();
            for (AttributeSourceDTO src : _sources)
                sources.add(src.convert());
            att.setSources(sources);
        }
        
        return att;
    }

    /**
     * Collapase empty strings to null to avoid XML clutter.
     * This actually isn't clutter for things like defaultValue 
     * and displayName which have meaning if the value is non-null.
     */
    public Object simplify(Object src) {
        Object actual = src;
        if (src instanceof String)
            actual = collapse((String)src);
        return actual;
    }

    public String collapse(String src) {
        if (src != null && src.length() == 0)
            src = null;
        return src;
    }

    //TODO: tqm this code is crying out for refactoring
    public String getDefaultValueString() 
    {
        if (_type.equals(ObjectAttribute.TYPE_STRING))
        {
            return (String)_defaultValue;
        }
        else if (_type.equals(ObjectAttribute.TYPE_BOOLEAN))
        {
            if (_defaultValue == null)
            {
                return "false";
            }
            return _defaultValue.toString().toLowerCase();
        }
        else if (_type.equals(ObjectAttribute.TYPE_DATE))
        {
            Date dateVal = (Date) _defaultValue;
            if (dateVal == null)
            {
                return null;
            }
            else
            {
                return String.valueOf(dateVal.getTime());
            }
        }
        else if (_type.equals(ObjectAttribute.TYPE_INT))
        {
            if (_defaultValue == null)
            {
                return "";
            }
            return String.valueOf((Integer)_defaultValue);
        }
        else if (_type.equals(ObjectAttribute.TYPE_IDENTITY))
        {
            if (_defaultValue == null)
            {
                return "";
            }
            return _defaultValue.toString();
        }
        else
        {
            if (_defaultValue == null)
            {
                return null;
            }
            else
            {
                return _defaultValue.toString();
            }
        }
    }

    //TODO: tqm refactor
    public void setDefaultValueString(String strVal) 
    {
        if (_system || _standard)
        {
            return;
        }
        
        if (_type.equals(ObjectAttribute.TYPE_STRING))
        {
            _defaultValue = strVal;
        }
        else if (_type.equals(ObjectAttribute.TYPE_BOOLEAN))
        {
            _defaultValue = Boolean.parseBoolean(strVal);
        }
        else if (_type.equals(ObjectAttribute.TYPE_DATE))
        {
            if (strVal.trim().length() == 0)
            {
                _defaultValue = null;
            }
            else
            {
                try
                {
                    _defaultValue = new Date(Long.parseLong(strVal));
                }
                catch(Exception ex)
                {
                    _log.error("Error setting date", ex);
                    _defaultValue = null;
                }
            }
        }
        else if (_type.equals(ObjectAttribute.TYPE_INT))
        {
            if (strVal.trim().length() == 0)
            {
                _defaultValue = null;
            }
            else
            {
                try
                {
                    _defaultValue = Integer.parseInt(strVal);
                }
                catch(Exception ex)
                {
                    _log.error("Could not set integer value: " + strVal + ", message: " + ex.getMessage());
                     addMessage(
                             new Message(Type.Error, MessageKeys.ERR_NOT_VALID_DEF_INTEGER, strVal), 
                             null);
                }
            }
        }
        else
        {
            _defaultValue = strVal;
        }
    }

    
}

