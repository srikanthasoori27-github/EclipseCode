/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import sailpoint.object.Application;
import sailpoint.object.AttributeSource;
import sailpoint.object.AttributeTarget;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.Rule;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.identity.IdentityAttributeBean.SourceBean;
import sailpoint.web.identity.IdentityAttributeBean.SourceType;
import sailpoint.web.identity.IdentityAttributeBean.TargetBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.SelectItemByLabelComparator;

public abstract class BaseAttributeEditBean extends BaseDTO {

    protected static final String ATT_IDENTITY_ATTRIBUTE_BEAN = "IdentityAttributeBean";
    static final String ATT_ORIGINAL_NAME = "IdentityAttributeName";
    
    protected IdentityAttributeBean editedAttribute;
    private int selectedTargetIdx;

    protected String originalAttributeName;
    
    protected int maxExtendedAttributes;
    
    protected String sourcePriorities;
    
    protected String _lastApplicationName;
    protected List<SelectItem> _lastAttributeItems;

    public void restore() {
        final Map session = getSessionScope();
        
        if (editedAttribute == null) {
            editedAttribute = (IdentityAttributeBean) session.get(ATT_IDENTITY_ATTRIBUTE_BEAN);
        }
        
        if (originalAttributeName == null) {
            originalAttributeName = (String) session.get(ATT_ORIGINAL_NAME);
        }
    }
    
    public String cancel() {
        clearSession();

        return "cancel";
    }
    
    protected AttributeSource createSourceFromBean(IdentityAttributeBean.SourceBean srcBean) throws GeneralException {
        AttributeSource newSource = new AttributeSource();

        newSource.setName(srcBean.getName());
        
        SourceType stype = srcBean.getSourceTypeEnum();
        if  ( (stype == SourceType.APPLICATION) || ( stype == SourceType.APPRULE ) ) {       
            newSource.setApplication(getContext().getObjectByName(sailpoint.object.Application.class, srcBean.getApplication()));
        } 
        if  ( (stype == SourceType.RULE) || ( stype == SourceType.APPRULE ) ) {     
            Rule rule = getContext().getObjectByName(Rule.class, srcBean.getRule());
            newSource.setRule(rule);
        }
        return newSource;
    }
    
    /*
     * Return the index of an available slot or -1 if none is available
     */
    protected int getAvailableExtendedSlot() {
        int index = -1;
        ObjectAttribute existingAttribute = getAttribute(editedAttribute.getName());
        
        if (existingAttribute != null && existingAttribute.getExtendedNumber() > 0) {
            index = existingAttribute.getExtendedNumber();
        } else if (getExtendedAttributes().size() > maxExtendedAttributes) {
            index = -1;
        } else {
            Set<Integer> usedIndecies = new HashSet<Integer>();
            
            for (ObjectAttribute extendedAttr : getExtendedAttributes()) {
                usedIndecies.add(extendedAttr.getExtendedNumber());
            }
            
            int i = 1;
            
            while (index == -1 && i <= maxExtendedAttributes) {
                if (usedIndecies.contains(i))
                    i++;
                else 
                    index = i;
            }
        }
        return index;
    }

    /**
     * Will return the available slot of extended attributes of type identity which
     * are mapped by extendedIdentity1 etc relationships.
     */
    protected int getAvailableExtendedSlotByType(String type, int max) {

        int slotNumber = -1;
        ObjectAttribute existingAttribute = getAttribute(editedAttribute.getName());
        if (existingAttribute != null && existingAttribute.getExtendedNumber() > 0) {
            slotNumber = existingAttribute.getExtendedNumber();
        } else {
        
            Set<Integer> usedIndices = new HashSet<Integer>();
            for (ObjectAttribute extendedAttr : getExtendedAttributes()) {
                if (type.equals(extendedAttr.getType()) && extendedAttr.getExtendedNumber() > 0) {
                    usedIndices.add(extendedAttr.getExtendedNumber());
                }
            }
            
            for (int i = 1; i <= max; ++i) {
                if (!usedIndices.contains(i)) {
                    slotNumber = i;
                    break;
                }
            }
        }
        
        return slotNumber;
    }


    protected abstract ObjectAttribute getAttribute(String name);
    protected abstract List<ObjectAttribute> getExtendedAttributes();
    public abstract void initialize() throws GeneralException;
    public String updateApplication() {
        // This is just to force a form submission so that we can get an proper attributes
        // list when an app selection changes
        return "";
    }
    
    /*
     * This method maintains the order for the source priorities.  Any time the configForm is submitted,
     * this method should be called in order to keep the bean aware of the latest changes to source
     * priorities
     */
    protected void maintainOrder(String latestPriorityChanges) {
        if (latestPriorityChanges != null && latestPriorityChanges.length() > 0) {
            int currentPosition = 0;
            
            currentPosition = latestPriorityChanges.indexOf('[') + 1;
                        
            // The ordering has changed
            while ((latestPriorityChanges.indexOf('[', currentPosition) + 1) > 0) {                
                int startOfCSV = "[[sources:".length() + 1;
                int endOfCSV = latestPriorityChanges.indexOf(']', startOfCSV);
                
                String csv = latestPriorityChanges.substring(startOfCSV, endOfCSV);
                
                String [] indecies = Util.csvToArray(csv);
                List<SourceBean> sources = editedAttribute.getMappedSources();
                
                if (!sourceMappingsMatch(sources, indecies)) {
                    // Specifically, the ordering has changed on this attribute.
                    // Reorder it
                    for (int i = 0; i < indecies.length; ++i) {
                        if (i < sources.size())
                            sources.set(i, editedAttribute.getSourceByIndex(Integer.parseInt(indecies[i])));
                        else
                            sources.add(editedAttribute.getSourceByIndex(Integer.parseInt(indecies[i])));
                    }                    
                } 
                
                currentPosition = endOfCSV + 1;
            }
        }
    }
    
    private boolean sourceMappingsMatch(List<SourceBean> sources, String [] indecies) {
        boolean mappingsMatch = true;
        
        int currentIndex = 0;
        
        for (SourceBean source : sources) {
            int nextSourceIndex = Integer.parseInt(indecies[currentIndex]);
            if (source.getIndex() != nextSourceIndex) {
                mappingsMatch = false;
            }
        }
        
        return mappingsMatch;
    }

    public IdentityAttributeBean getEditedAttribute() throws GeneralException {
        if (editedAttribute == null) {
            restore();
            initialize();
        }
        return editedAttribute;
    }

    public void setEditedAttribute(IdentityAttributeBean editedAttribute) {
        this.editedAttribute = editedAttribute;
    }

    public String getOriginalAttributeName() {
        return originalAttributeName;
    }

    public void setOriginalAttributeName(String originalAttributeName) {
        this.originalAttributeName = originalAttributeName;
    }
    
    public int getSelectedTargetIdx() {
        return this.selectedTargetIdx;
    }

    public void setSelectedTargetIdx(int selectedTargetIdx) {
        this.selectedTargetIdx = selectedTargetIdx;
    }

    protected void clearSession() {
        final Map session = getSessionScope();
        session.remove(ATT_IDENTITY_ATTRIBUTE_BEAN);
        session.remove(ATT_ORIGINAL_NAME);
    }
    
    public String deleteSources() throws GeneralException {
        sourcePriorities = getRequestParameter("configForm:sourcePriorities");

        maintainOrder(sourcePriorities);
        // Commenting this out because it doesn't seem to work or even be necessary anymore
        // manuallyUpdateTheDamnSources();
        
        List<SourceBean> deletedSources = new ArrayList<SourceBean>();
        IdentityAttributeBean editedAttribute = getEditedAttribute();
        
        for (SourceBean src : getEditedAttribute().getMappedSources()) {
            if (src.isChecked()) {
                deletedSources.add(src);
            } 
        }
        
        editedAttribute.removeSources(deletedSources);
        
                
        return "deleteSources";
    }

    public String prepareToAddSource() throws GeneralException {
        IdentityAttributeBean idb = getEditedAttribute();
        idb.setSelectedSource(idb.new SourceBean());
        return "";
    }
    
    /**
     * Build a SelectItem list for the account attributes in the chosen application.
     * For some stupid reason, this gets called about 5 times during page rendering even
     * though it is only needed once when bulding the attribute selector.  To prevent
     * at least some of those db accesses, cache the list and reuse it unless the application
     * changes.
     */
    protected List<SelectItem> getAccountAttributeItems(String appname) throws GeneralException {


        List<SelectItem> items = new ArrayList<SelectItem>();

        if (appname != null) {
            Application app = getContext().getObjectByName(Application.class, appname);
            if (app != null) {
                if (appname.equals(_lastApplicationName)) {
                    items = _lastAttributeItems;
                }
                else {
                    Schema schema = app.getAccountSchema();
                    if (schema != null) {
                        List<String> names = schema.getAttributeNames();
                        if (names != null) {
                            for (String name : names) {
                                items.add(new SelectItem(name, name));
                            }
                            // looks better sorted
                            Collections.sort(items, new SelectItemByLabelComparator(getLocale()));
                        }
                    }
                    _lastApplicationName = appname;
                    _lastAttributeItems = items;
                }
            }
        }

        // Always put this at the front
        items.add(0, new SelectItem("", getMessage(MessageKeys.SELECTION_APPLICATION_ATTRIBUTE_VAL)));

        return items;
    }

    public List<SelectItem> getAttributesForSelectedApp() throws GeneralException {
        String selectedApp = editedAttribute.getSelectedSource().getApplication();
        return getAccountAttributeItems(selectedApp);
    }
    
    public String addSourceToAttributeAction() {
        sourcePriorities = getRequestParameter("configForm:sourcePriorities");
        maintainOrder(sourcePriorities);
        
        SourceBean newSource = editedAttribute.getSelectedSource();
        
        // Freaking a4j
        String potentialSourceApp = Util.getString(getRequestParameter("sourceApps"));
        String sourceAttribute = Util.getString(getRequestParameter("configForm:sourceAttributes"));
        String potentialRule = Util.getString(getRequestParameter("configForm:sourceRules"));
        
        SourceType type = newSource.getSourceTypeEnum();
        if (type == SourceType.APPLICATION) {
            newSource.setName(sourceAttribute);
            newSource.setApplication(potentialSourceApp);
            newSource.setRule(null);
        } else if (type == SourceType.APPRULE) {
            newSource.setName("AppRule: " + potentialRule + " " + potentialSourceApp);
            newSource.setRule(potentialRule);
            newSource.setApplication(potentialSourceApp);
        } else if (type == SourceType.RULE) {
            newSource.setName("GlobalRule:" + potentialRule);
            newSource.setRule(potentialRule);
            newSource.setApplication(null);
        }
                
        editedAttribute.addSource(newSource);
        editedAttribute.setSelectedSource(editedAttribute.new SourceBean());

        // If they have chosen to add the source as a target also (only
        // applicable for "application attribute" type source), add it as
        // a target also.
        if (newSource.isAddAsTarget() && SourceType.APPLICATION.equals(type)) {
            TargetBean target = new TargetBean();
            target.setName(sourceAttribute);
            target.setApplication(potentialSourceApp);

            // We could add a message if we don't add.  No big deal, though.
            if (!editedAttribute.containsTarget(target)) {
                editedAttribute.addTarget(target);
            }
        }
        
        return "addedSource";        
    }

    public String getSourcePriorities() {
        return sourcePriorities;
    }

    public void setSourcePriorities(String sourcePriorities) {
        this.sourcePriorities = sourcePriorities;
    }
    
    protected boolean validateAttribute(ObjectAttribute attributeObj) {

        boolean isGood = !Util.isNullOrEmpty(attributeObj.getName());

        if (isGood && !Util.isEmpty(attributeObj.getSources())) {
            for (AttributeSource src : attributeObj.getSources()) {
                isGood |= validateSource(src);
            }
        }

        if (isGood && !Util.isEmpty(attributeObj.getTargets())) {
            for (AttributeTarget target : attributeObj.getTargets()) {
                isGood |= validateTarget(target);
            }
        }
        
        return isGood;
    }
    
    protected boolean validateSource(AttributeSource src) {
        final boolean isValid;
        
        if (src == null) {
            isValid = false;
        } else if (src.getRule() == null) {
            isValid = src.getName() != null && src.getApplication() != null;
        } else {
            isValid = src.getName() != null;
        }
        
        return  isValid;
    }

    protected boolean validateTarget(AttributeTarget target) {
        // Application and name is required.  Everything else is optional.
        return (null != target.getApplication()) &&
               !Util.isNullOrEmpty(target.getName());
    }

    protected ObjectAttribute createAttributeObjFromBean(IdentityAttributeBean attributeBean) throws GeneralException {
        AttributeSource newSourceObject;
        ObjectAttribute newAttributeObject;
        
        newAttributeObject = new ObjectAttribute();
        newAttributeObject.setSystem(false);
        newAttributeObject.setStandard(false);
        newAttributeObject.setName(attributeBean.getName());

        IdentityAttributeBean.SourceBean newSourceBean = attributeBean.getSelectedSource();
        if (newSourceBean != null && newSourceBean.isValid()) {
            if ( newSourceBean.isGlobalRule() ) {
                // this means a global rule not a "real" source
                String ruleName = newSourceBean.getRule();
                if ( ruleName != null ) {
                    Rule r = getContext().getObjectByName(Rule.class, ruleName);
                    newAttributeObject.setRule(r);
                }
            } else {
                newSourceObject = createSourceFromBean(newSourceBean);
                newAttributeObject.add(newSourceObject);
            }
        }

        return newAttributeObject;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS FOR TARGETS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String deleteTargets() throws GeneralException {
        List<TargetBean> targets = getEditedAttribute().getTargets();
        if (null != targets) {
            for (Iterator<TargetBean> it=targets.iterator(); it.hasNext(); ) {
                TargetBean target = it.next();
                if (target.isChecked()) {
                    it.remove();
                }
            }
        }
        return "deleteTargets";
    }

    public String prepareToEditTarget() throws GeneralException {
        List<TargetBean> targets = getEditedAttribute().getTargets();

        // Make a copy so we don't muck with the original.
        TargetBean target = targets.get(this.selectedTargetIdx);
        target = new TargetBean(target);
        getEditedAttribute().setSelectedTarget(target);
        return "";
    }

    public String prepareToAddTarget() throws GeneralException {
        // Start with a fresh target when we're about to add.
        getEditedAttribute().setSelectedTarget(new TargetBean());
        return "";
    }

    public String saveEditedTargetAction() throws GeneralException {
        TargetBean newTarget = editedAttribute.getSelectedTarget();
        List<TargetBean> targets = getEditedAttribute().getTargets();
        targets.set(this.selectedTargetIdx, newTarget);
        editedAttribute.setSelectedTarget(null);
        return "savedTarget";
    }
    
    public String addTargetToAttributeAction() throws GeneralException {
        TargetBean newTarget = editedAttribute.getSelectedTarget();

        // We could add a message if we don't add.  No big deal, though.
        if (!editedAttribute.containsTarget(newTarget)) {
            editedAttribute.addTarget(newTarget);
        }

        // Reset the selected target so the values are empty the next time a
        // target is added.
        prepareToAddTarget();
                
        return "addedTarget";        
    }
    
    public List<SelectItem> getAttributesForSelectedTargetApp() throws GeneralException {
        String selectedApp = editedAttribute.getSelectedTarget().getApplication();
        return getAccountAttributeItems(selectedApp);
    }
}
