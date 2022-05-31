/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApplicationEntitlementWeights;
import sailpoint.object.EntitlementWeight;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.EntitlementWeight.EntitlementType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * This bean is used to indicate which app is currently being edited by the entitlement 
 * risk score config pages.  If JSF had a proper parameter forwarding mechanism, this
 * wouldn't be necessary.
 * @author Bernie Margolis
 */
public class AttributesRiskConfigBean extends BaseObjectBean<ScoreConfig> {
    private static Log log = LogFactory.getLog(AttributesRiskConfigBean.class);
    
    private EntitlementsBarConfigBean.AppAttribute newAttribute;
    private EditedEntitlementAppBean editedEntitlement;
    
    public AttributesRiskConfigBean() {
        super();
        
        setScope(ScoreConfig.class);
        
        try {
            if (getObject() == null) {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                setObject(config);
                setObjectId(config.getId());
            }
            
            editedEntitlement = (EditedEntitlementAppBean) getFacesContext().getApplication().createValueBinding("#{editedEntitlementApp}").getValue(getFacesContext());
            newAttribute = new EntitlementsBarConfigBean.AppAttribute();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE),
                    e.getMessageInstance());
            log.error("The database is not accessible right now.", e);
        }
    }
    
    public EntitlementsBarConfigBean.AppAttribute getNewAttribute() {
        return newAttribute;
    }
    
    @SuppressWarnings("unchecked")
    public String saveChanges() {
        try {
            ScoreDefinition entitlementsDef = getObject().getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);

            List<EntitlementsBarConfigBean.AppAttribute> updatedAttributes = editedEntitlement.getApplication().getAttributes();
                        
            List<ApplicationEntitlementWeights> entitlementWeights = 
                (List<ApplicationEntitlementWeights>) entitlementsDef.getArgument("applicationEntitlementWeights");
            
            String editedApp = editedEntitlement.getApplication().getName();
            ApplicationEntitlementWeights appWeights = getWeightsForApplication(editedApp, entitlementWeights);
            // Start out clean and recreate the weights... this has to be done because attribute names may have changed and
            // they don't have unique identifiers since they are inline XML attributes.
            // First, save the permissions so that we don't whack them by accident
            List<EntitlementWeight> updatedWeights = new ArrayList<EntitlementWeight>();            
            List<EntitlementWeight> existingWeights = appWeights.getWeights();
            if (existingWeights != null && !existingWeights.isEmpty()) {
                for (EntitlementWeight existingWeight : appWeights.getWeights()) {
                    if (existingWeight.getType() == EntitlementType.permission) {
                        updatedWeights.add(existingWeight);
                    }
                }
            }
            // Now it's safe to clear them out
            appWeights.clearWeights();

                        
            // On recreation, we want to make sure than only one attribute/value pair counts... we discard the other one.
            // This set helps us do this
            Set<AVPair> uniqueAttributeValuePairs = new HashSet<AVPair>();
            
            for (EntitlementsBarConfigBean.AppAttribute attributeDTO : updatedAttributes) {
                AVPair testAVPair = new AVPair(attributeDTO.getAttribute(), attributeDTO.getValue());
                                
                if (!uniqueAttributeValuePairs.contains(testAVPair)) {
                    EntitlementWeight attributeWeight = new EntitlementWeight();
                    attributeWeight.setType(EntitlementType.attribute);
                    attributeWeight.setTarget(attributeDTO.getAttribute());
                    attributeWeight.setValue(attributeDTO.getValue());
                    attributeWeight.setWeight(attributeDTO.getWeight());
                    updatedWeights.add(attributeWeight);
                    uniqueAttributeValuePairs.add(testAVPair);
                }
            }
            
            if (!updatedWeights.isEmpty()) {
                appWeights.addWeights(updatedWeights);
            }
            
            saveAction();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                    e.getMessageInstance());
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "save";
    }
    
    public String cancelChanges() {
        return "cancel";
    }
    
    @SuppressWarnings("unchecked")
    public String saveNewAttribute() {
        String editedApp = editedEntitlement.getApplication().getName();
        String result;
        
        // Work around a couple of JSF/a4j quirks
        newAttribute.setAttribute(getRequestParameter("editForm:attributes:attrAttribute"));
        newAttribute.setValue(getRequestParameter("editForm:attributes:attrValue"));

        if (newAttribute.getAttribute() == null || "".equals(newAttribute.getAttribute())) {
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_NO_ATTR_SELECTED);
            addMessage(msg, msg);
            result = null;
        } else if ("".equals(newAttribute.getValue()) || newAttribute.getValue() == null) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ATTR_VALUE_MISSING, newAttribute.getAttribute());
            addMessage(msg, msg);           
            result = null;
        }else {
            try {
                ScoreDefinition entitlementsDef = getObject().getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);
                String defaultAttributeRiskScore = (String) entitlementsDef.getArgument("defaultAttributeScore");
                newAttribute.setWeight(defaultAttributeRiskScore);
                
                List<ApplicationEntitlementWeights> entitlementWeights = 
                    (List<ApplicationEntitlementWeights>) entitlementsDef.getArgument("applicationEntitlementWeights");
                
                ApplicationEntitlementWeights appWeights = getWeightsForApplication(editedApp, entitlementWeights);
                appWeights.getWeights().add(new EntitlementWeight(EntitlementType.attribute, newAttribute.getAttribute(), newAttribute.getValue(), newAttribute.getWeight()));            
                saveAction();
                result = "add";
                editedEntitlement.getApplication().getAttributes().add(newAttribute);
                newAttribute = new EntitlementsBarConfigBean.AppAttribute();
            } catch (GeneralException e) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                        e.getMessageInstance());
                log.error("No changes can be saved right now because the system is offline.", e);
                result = null;
            }
        }            

        return result;
    }
    
    @SuppressWarnings("unchecked")
    public String deleteAttributes() {
        try {
            ScoreDefinition entitlementsDef = getObject().getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);

            List<ApplicationEntitlementWeights> entitlementWeights = 
                (List<ApplicationEntitlementWeights>) entitlementsDef.getArgument("applicationEntitlementWeights");

            String editedApp = editedEntitlement.getApplication().getName();
            
            ApplicationEntitlementWeights weights = getWeightsForApplication(editedApp, entitlementWeights);
            
            List<EntitlementWeight> currentWeightList = new ArrayList<EntitlementWeight>(weights.getWeights());
            List<EntitlementsBarConfigBean.AppAttribute> keptAttributes = new ArrayList<EntitlementsBarConfigBean.AppAttribute>();
            
            for (EntitlementsBarConfigBean.AppAttribute attribute : editedEntitlement.getApplication().getAttributes()) {
                if (attribute.isChecked()) {
                    removeAttributeFromWeightList(attribute, currentWeightList);
                } else {
                    keptAttributes.add(attribute);
                }
            }
            
            weights.getWeights().clear();
            weights.getWeights().addAll(currentWeightList);
            editedEntitlement.getApplication().setAttributes(keptAttributes);
                        
            saveAction();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                    e.getMessageInstance());
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "delete";
    }
    
    private ApplicationEntitlementWeights getWeightsForApplication(final String appName, List<ApplicationEntitlementWeights> entitlementWeights) {
        ApplicationEntitlementWeights retval = null;
        
        for (ApplicationEntitlementWeights appWeights : entitlementWeights) {
            if (appWeights.getApplication().getName().equals(appName)) {
                retval = appWeights;
            }
        }
                
        return retval;
    }
    
    private void removeAttributeFromWeightList(EntitlementsBarConfigBean.AppAttribute attribute, List<EntitlementWeight> weightList) {
        EntitlementWeight weightToRemove = getWeightFromList(attribute, weightList);
        
        if (weightToRemove != null) {
            weightList.remove(weightToRemove);
        }
    }
    
    // Given the specified attribute, return its weight counterpart
    private EntitlementWeight getWeightFromList(EntitlementsBarConfigBean.AppAttribute attribute, List<EntitlementWeight> weightList) {
        boolean isFound = false;
        Iterator<EntitlementWeight> i = weightList.iterator();
        EntitlementWeight retval = null;
        
        while (!isFound && i.hasNext()) {
            EntitlementWeight currentWeight = i.next();
            
            if (currentWeight.getType() == EntitlementWeight.EntitlementType.attribute &&
                currentWeight.getTarget().equals(attribute.getAttribute()) &&
                currentWeight.getValue().equals(attribute.getValue())) {
                isFound = true;
                retval = currentWeight;
            }
        }
        
        return retval;
    }
    
    private class AVPair {
        private String attribute;
        private String value;
        
        AVPair(String attribute, String value) {
            this.attribute = attribute;
            this.value = value;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            
            sb.append("[").append(AVPair.class.getName()).append(": [attribute=").append(attribute).append("], [value=").append(value).append("]]");
            
            return sb.toString();
        }
        
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((attribute == null) ? 0 : attribute.hashCode());
            result = PRIME * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            
            if (getClass() != obj.getClass()) {
                return false;
            }
            
            final AVPair other = (AVPair) obj;
            if (attribute == null) {
                if (other.attribute != null) {
                    return false;
                }
            } else if (!attribute.equals(other.attribute)) {
                return false;
            }
            
            if (value == null) {
                if (other.value != null) {
                    return false;
                }
            } else if (!value.equals(other.value)) {
                return false;
            }
            return true;
        }
    }
}
