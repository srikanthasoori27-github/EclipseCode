/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeSource;
import sailpoint.object.AttributeTarget;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.PropertyInfo;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class IdentityAttributeBean extends BaseDTO {
    private static final long serialVersionUID = -6647412911656856050L;
    private String name;
    private List<SourceBean> mappedSources;
    // This map allows us to conveniently retrieve this attribute's sources via
    // their index as defined in the current view.  We have to do this because
    // the view's indecies may not match the model's until they are forcibly applied. 
    private Map<Integer, SourceBean> indexToSourceMap;
    private SourceBean selectedSource;
    private boolean isNew;
    private int nextAvailableSourceIndex;
    
    private List<TargetBean> targets;
    private TargetBean selectedTarget;
    
    // jsl - this is a misnomer now, when true it indiciates that the
    // attribute is searchable, this may be because it has been assigend
    // and numberd column or that it has a named column.  We don't
    // present both of those options in the UI we just have a "Searchable"
    // checkbox.  Ideally we would rename the property to "searchable"
    // but I don't want to get into Javascript changes near the end
    // of the 6.1 release
    private boolean extended;

    // this is true if the attribute has a named column, this will
    // be derived from the .hbm.xml mapping files and cannot be changed 
    private boolean namedColumn;

    private boolean groupFactory;
    private boolean external;
    private boolean standard;
    private boolean silent;
    private boolean multi;
    
    private String editMode;
    private String type;
    
    private String displayName;
    
    private List<String> groupFactories;

    private String listenerRule;
    private String listenerWorkflow;
    
    public IdentityAttributeBean() {
        name = "";
        displayName = "";
        mappedSources = new ArrayList<SourceBean>();
        targets = new ArrayList<TargetBean>();
        selectedSource = new SourceBean();
        nextAvailableSourceIndex = 0;
        indexToSourceMap = new HashMap<Integer, SourceBean>();
        extended = false;
        namedColumn = false;
        groupFactory = false;
        external = false;
        standard = false;
        silent = false;
        isNew = false;
        multi = false;
        type = PropertyInfo.TYPE_STRING;
        editMode = ObjectAttribute.EditMode.ReadOnly.name();
    }
    
    public IdentityAttributeBean(String className, ObjectAttribute attr, List<String> groupFactories) {
        name = attr.getName();

        // keep the key here.. let the ui decide if it should 
        // interpret attr.getDisplayableName() is broken and
        // returns localized text.
        displayName = WebUtil.safeHTML(attr.getDisplayName());
        if ( displayName == null ) {
            displayName = attr.getName();
        }
        
        // Extended really means "searchable" and is now overloaded to
        // include having a named column.  Bootstrap the namedColumn
        // flag by looking in the mapping files since this can't be edited.
        ExtendedAttributeUtil.PropertyMapping pm = ExtendedAttributeUtil.getPropertyMapping(className, attr);
        namedColumn = (pm != null && pm.namedColumn);
        extended = attr.isExtended() || namedColumn;

        groupFactory = attr.isGroupFactory();
        external = attr.isExternal();
        standard = attr.isStandard();
        silent = attr.isSilent();
        if (attr.getEditMode() == null) {
            editMode = ObjectAttribute.EditMode.ReadOnly.name();
        } else {
            editMode = attr.getEditMode().name();
        }
        multi = attr.isMulti();
        nextAvailableSourceIndex = 0;
        type = attr.getType();
        indexToSourceMap = new HashMap<Integer, SourceBean>();

        Rule rule = attr.getListenerRule();
        if (rule != null)
            listenerRule = rule.getName();

        Workflow wf = attr.getListenerWorkflow();
        if (wf != null)
            listenerWorkflow = wf.getId();

        List<AttributeSource> sourceList = attr.getSources();
        selectedSource = new SourceBean();
        
        mappedSources = new ArrayList<SourceBean>();
        
        if (sourceList != null && !sourceList.isEmpty()) {
            for (AttributeSource src : sourceList) {
                addSource(new SourceBean(src));
            }
        } else {
            // check to see if there is a global rule
            // defined that might not be wrapped
            // in an attribute source
            Rule globalRule = attr.getRule();
            if ( globalRule != null ) {
                addSource(new SourceBean(globalRule));
            }
        }
        this.groupFactories = groupFactories;

        this.targets = new ArrayList<TargetBean>();
        if (!Util.isEmpty(attr.getTargets())) {
            for (AttributeTarget target : attr.getTargets()) {
                this.targets.add(new TargetBean(target));
            }
        }
    }
    
    public List<SourceBean> getMappedSources() {
        // Verify that the order gets displayed correctly
        int currentOrder = 1;
        
        for (SourceBean bean: mappedSources) {
            bean.setCurrentOrder(currentOrder++);
        }
        
        return mappedSources;
    }
    
    public String getPrimarySource() {
        String primarySource = null;
        
        if (mappedSources != null && !mappedSources.isEmpty()) {
            primarySource = mappedSources.get(0).getText();
        }
        
        if (primarySource == null) {
            primarySource = "";
        }
        
        return primarySource;
    }
    
    public void addSource(SourceBean newSource) {
        newSource.setIndex(getNextAvailableSourceIndex());
        indexToSourceMap.put(newSource.getIndex(), newSource);
        mappedSources.add(newSource);
    }
    
    public void removeSources(Collection<SourceBean> deletedSources) {
        mappedSources.removeAll(deletedSources);
        
        for (SourceBean deletedSource : deletedSources) {
            indexToSourceMap.remove(deletedSource.getIndex());
        }
    }

    public List<TargetBean> getTargets() {
        return this.targets;
    }
    
    public void setTargets(List<TargetBean> targets) {
        this.targets = targets;
    }

    public void addTarget(TargetBean target) {
        if (null == this.targets) {
            this.targets = new ArrayList<TargetBean>();
        }
        this.targets.add(target);
    }

    public boolean containsTarget(TargetBean target) {
        boolean contains = false;
        if (null != this.targets) {
            contains = (this.targets.indexOf(target) > -1);
        }
        return contains;
    }
    
    public TargetBean getSelectedTarget() {
        if (null == this.selectedTarget) {
            this.selectedTarget = new TargetBean();
        }
        return this.selectedTarget;
    }
    
    public void setSelectedTarget(TargetBean selectedTarget) {
        this.selectedTarget = selectedTarget;
    }
    
    public void setName(String name) {        
        this.name = Util.compressWhiteSpace(name);
    }
    
    public String getName() {
        return name;
    }
    
    public void setDisplayName(String name) {
        this.displayName = Util.compressWhiteSpace(name);
    }
    
    public String getDisplayName() {
        if (displayName == null || displayName.trim().length() == 0)
            return Util.splitCamelCase(name);
        else
            return displayName;
    }
    
    public String getCharacteristics() {
        List<String> characteristics = new ArrayList<String>();

        if (extended) {
            characteristics.add(getMessage(MessageKeys.IDENT_ATTRIBUTE_SEARCHABLE));
        }
        
        if (groupFactory) {
            characteristics.add(getMessage(MessageKeys.IDENT_ATTRIBUTE_GROUP_FACTORY));
        }
        
        if (external) {
            characteristics.add(getMessage(MessageKeys.IDENT_ATTRIBUTE_EXTERNAL));
        }
        
        ObjectAttribute.EditMode currentEditMode = ObjectAttribute.EditMode.valueOf(editMode);
        
        if (currentEditMode == ObjectAttribute.EditMode.Permanent || currentEditMode == ObjectAttribute.EditMode.UntilFeedValueChanges) {
            characteristics.add(getMessage(MessageKeys.IDENT_ATTRIBUTE_EDITABLE));
        }
        
        if (!Util.isEmpty(this.targets)) {
            characteristics.add(getMessage(MessageKeys.IDENT_ATTRIBUTE_ATTR_SYNC));
        }
        
        return Util.listToCsv(characteristics);
    }

    public String getGroupFactoryNames() {
        return Util.listToCsv(groupFactories);
    }
    
    public SourceBean getSelectedSource() {
        return selectedSource;
    }
    
    public void setSelectedSource(SourceBean selectedSource) {
        this.selectedSource = selectedSource;
    }
    
    public boolean isNew() {
        return isNew;
    }
    
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
    
    public SourceBean getSourceByIndex(int index) {
        return indexToSourceMap.get(index);
    }
    
    public int getNumSources() {
        return mappedSources.size();
    }
    
    private int getNextAvailableSourceIndex() {
        return nextAvailableSourceIndex++;
    }

    public boolean isExtended() {
        return this.extended;
    }

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public boolean isNamedColumn() {
        return this.namedColumn;
    }

    public void setNamedColumn(boolean b) {
        this.namedColumn = b;
    }

    public boolean isGroupFactory() {
        return groupFactory;
    }

    public void setGroupFactory(boolean groupFactory) {
        this.groupFactory = groupFactory;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }    
    
    public boolean isStandard() {
        return standard;
    }

    public void setStandard(boolean standard) {
        this.standard = standard;
    }    

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean b) {
        this.silent = b;
    }
    
    /**
     * The current editableMode
     * @see sailpoint.object.ObjectAttribute$EditMode
     */
    public String getEditMode() {
        return editMode;
    }

    public void setEditMode(String editableMode) {
        this.editMode = editableMode;
    }
    
    public String getType() {
       return this.type;
    }

    public void setType(String type) {
       this.type = type;
    }

    public List<SelectItem> getTypes() {

        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem(PropertyInfo.TYPE_STRING, getMessage(MessageKeys.ATTR_TYPE_STRING)));
        items.add(new SelectItem(PropertyInfo.TYPE_DATE, getMessage(MessageKeys.ATTR_TYPE_DATE)));
        items.add(new SelectItem(PropertyInfo.TYPE_BOOLEAN, getMessage(MessageKeys.ATTR_TYPE_BOOL)));

        return items;
    }

    public String getListenerRule() {
        return this.listenerRule;
    }
    
    public void setListenerRule(String s) {
        if (s != null && s.length() > 0)
            this.listenerRule = s;
        else
            this.listenerRule = null;
    }

    public String getListenerWorkflow() {
        return this.listenerWorkflow;
    }

    public void setListenerWorkflow(String s) {
        if (s != null && s.length() > 0)
            this.listenerWorkflow = s;
        else
            this.listenerWorkflow = null;
    }

    public boolean getMulti() {
        return multi;
    }

    public boolean isMulti() {
        return getMulti();
    }

    public void setMulti(boolean multi) {
        this.multi = multi;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    public enum SourceType {
        RULE("selection_global_rule"),
        APPLICATION("selection_application_attribute"),
        APPRULE("selection_application_rule");

        String _msgKey;
        SourceType(String msgKey) {
           _msgKey = msgKey;
        }
        public String getMessageKey() {
            return _msgKey;
        }
    };
    
    public class SourceBean {
        private String name;
        private String application;
        private String rule;
        /* JSF doesn't seem to play nicely with enums yet, so 
         * we have two fields representing the same value.  The
         * String value is for JSF's benefit, and the enum one is
         * for everyone else
         */
        private SourceType sourceTypeEnum;
        private String sourceType;
        private int index;
        private int currentOrder;
        private boolean isChecked;
        
        private boolean addAsTarget;
        
        public SourceBean() {
            application = "";
            name = "";
            setSourceType(SourceType.APPLICATION);
            // The bean won't have a currentOrder or an index until it's been added to a list
            index = -1;
            currentOrder = -1;
        }

        private SourceBean(AttributeSource src) {
            
            Application app = src.getApplication();
            Rule r = src.getRule();
            
            if (r != null) {
                if ( app != null )  {
                    setSourceType(SourceType.APPRULE);
                    name = src.getName();
                    rule = r.getName();
                    application = app.getName();
                } else {
                    setSourceType(SourceType.RULE);
                    name = src.getName();
                    rule = r.getName();
                    application = "";                    
                }
            } else if (app != null) {
                setSourceType(SourceType.APPLICATION);
                application = app.getName();
                name = src.getName();
            } 
            
            // The bean won't have a currentOrder or an index until it's been added to a list
            index = -1;
            currentOrder = -1;
        }

        /**
         * For global rules store the data in a SourceBean
         * so it can fit nicely with the other sources
         * even though on the object itself this is 
         * store on the top level and not in an
         * attribute source.
         */
        public SourceBean(Rule globalRule) {
            this();
            if ( globalRule != null ) {
                rule = globalRule.getName();
                name = "Global Rule:" + rule;
                application = "";
                setSourceType(SourceType.RULE);
            }
        }
        
        public void setName(String newName) {
            name = newName;
        }
        
        public String getName() {
            return name;
        }
        
        public void setApplication(String newApplication) {
            application = newApplication;
        }
        
        public String getApplication() {
            return application;
        }

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }
        
        /**
         * Gets the field that specifies whether this source is derived from a rule or an application
         * @return RULE or APPLICATION
         */
        public String getSourceType() {
            return sourceType;
        }
        
        /**
         * JSF doesn't seem to play nicely with enums yet, so 
         * we have two fields representing the same value.  The
         * sourceType is for JSF's benefit, and sourceTypeEnum is
         * for everyone else
         */
        public SourceType getSourceTypeEnum() {
            return sourceTypeEnum;
        }

        /**
         * Sets the field that specifies whether this source is derived from a rule or an application
         * @param sourceType either RULE or APPLICATION
         */
        public void setSourceType(SourceType sourceType) {
            this.sourceTypeEnum = sourceType;
            this.sourceType = sourceType.toString();
        }

        public void setSourceType(String sourceType) {
            this.sourceTypeEnum = Enum.valueOf(SourceType.class, sourceType);
            this.sourceType = sourceType;
        }

        public boolean getIsComplete() {            
            return (application != "" && name != "");
        }
        
        public boolean isChecked() {
            return isChecked;
        }

        public void setChecked(boolean isChecked) {
            this.isChecked = isChecked;
        }
        
        public boolean isAddAsTarget() {
            return this.addAsTarget;
        }
        
        public void setAddAsTarget(boolean addAsTarget) {
            this.addAsTarget = addAsTarget;
        }
        
        public boolean isValid() {
            final boolean valid;
            
            if (name == null || name.length() <= 0) {
                valid = false;
            } else if (application == null || application.length() <= 0) {
                if (rule == null || rule.length() <= 0) {
                    valid = false;
                } else {
                    valid = true;
                }
            } else {
                valid = true;
            }
            
            return valid;
        }
        
        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int getCurrentOrder() {
            return currentOrder;
        }

        public void setCurrentOrder(int currentOrder) {
            this.currentOrder = currentOrder;
        }
        
        /**
         * @return A representation of this bean that is suitable for display on the UI
         */
        public String getText() {
            Message msg = null;
            String text= "";
            
            if (sourceTypeEnum == SourceType.APPLICATION) {
                msg = new Message(MessageKeys.OCONFIG_SOURCE_MAPPING_ATTR_DESC, name, application);
            } else if (sourceTypeEnum == SourceType.APPRULE) {
                msg = new Message(MessageKeys.OCONFIG_SOURCE_MAPPING_APP_RULE_DESC, rule, application);
            } else if (sourceTypeEnum == SourceType.RULE) {
                msg = new Message(MessageKeys.OCONFIG_SOURCE_MAPPING_GLOBAL_RULE_DESC, rule);
            }

            if (msg != null) {
                text = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
            }

            return text;
        }
        
        public String toString() {
            StringBuffer buffer = new StringBuffer(SourceBean.class.getName());
            buffer.append(": [[name=");
            buffer.append(name);
            buffer.append("], [application=");
            buffer.append(application);
            buffer.append("], [isChecked=");
            buffer.append(isChecked);
            buffer.append("], [index=");
            buffer.append(index);
            buffer.append("]]");
            
            return buffer.toString();
        }

        public boolean isGlobalRule() {
           return ( (rule != null ) && ( Util.getString(application) == null ) );
        }
    }

    /**
     * A DTO that holds information about an AttributeTarget.
     */
    public static class TargetBean {
        
        /**
         * Tracks whether this target has been selected in the UI.
         */
        private boolean checked;
        
        /**
         * The name of the application.
         */
        private String application;

        /**
         * The name of the attribute on the target application.
         */
        private String name;

        /**
         * The name of the rule.
         */
        private String rule;

        /**
         * Whether to provision all accounts or require selection.
         */
        private boolean provisionAllAccounts;
        

        /**
         * Default constructor.
         */
        public TargetBean() {
            // Default this to true.
            this.provisionAllAccounts = true;
        }

        /**
         * Copy constructor.
         */
        public TargetBean(TargetBean target) {
            this.application = target.application;
            this.name = target.name;
            this.rule = target.rule;
            this.provisionAllAccounts = target.provisionAllAccounts;
        }

        /**
         * Construct from a target from an existing AttributeTarget.
         */
        public TargetBean(AttributeTarget target) {
            this.application =
                (null != target.getApplication()) ? target.getApplication().getName() : null;
            this.name = target.getName();
            this.rule =
                (null != target.getRule()) ? target.getRule().getName() : null;
            this.provisionAllAccounts = target.isProvisionAllAccounts();
        }

        /**
         * Convert this TargetBean into an AttributeTarget.
         */
        public AttributeTarget toTarget(SailPointContext context)
            throws GeneralException {

            AttributeTarget target = new AttributeTarget();
            target.setName(this.name);
            target.setProvisionAllAccounts(this.provisionAllAccounts);

            if (!Util.isNullOrEmpty(this.application)) {
                Application app =
                    context.getObjectByName(Application.class, this.application);
                target.setApplication(app);
            }
            
            if (!Util.isNullOrEmpty(this.rule)) {
                Rule ruleObj =
                    context.getObjectByName(Rule.class, this.rule);
                target.setRule(ruleObj);
            }

            return target;
        }

        public boolean isChecked() {
            return this.checked;
        }
        
        public void setChecked(boolean checked) {
            this.checked = checked;
        }
        
        public String getApplication() {
            return this.application;
        }
        
        public void setApplication(String application) {
            this.application = application;
        }
        
        public String getName() {
            return this.name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getRule() {
            return this.rule;
        }
        
        public void setRule(String rule) {
            this.rule = rule;
        }
        
        public boolean isProvisionAllAccounts() {
            return this.provisionAllAccounts;
        }
        
        public void setProvisionAllAccounts(boolean provisionAllAccounts) {
            this.provisionAllAccounts = provisionAllAccounts;
        }

        /**
         * Equality (as far as the UI is concerned) is based only on application
         * and attribute.  We don't want to add duplicates of these.
         */
        @Override
        public boolean equals(Object obj) {
            if ((null == obj) || !(obj instanceof TargetBean))
                return false;

            TargetBean t = (TargetBean) obj;
            return Util.nullSafeEq(this.application, t.application, true) &&
                   Util.nullSafeEq(this.name, t.name, true);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(this.application)
                .append(this.name)
                .toHashCode();
        }
    }
}
