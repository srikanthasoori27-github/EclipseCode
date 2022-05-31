/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Describable;
import sailpoint.object.Filter;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This utility enables Describable objects to handle descriptions consistently.
 * Prior to 6.0 we took a simple approach to storing descriptions and stored 
 * a single description on its object.  Due to high demand for internationalized
 * descriptions we took an alternate approach and moved the descriptions into a
 * separate table.  This came with its own set of problems because exported objects
 * no longer contained their own descriptions and versioned/transient objects had 
 * no means of hanging on to descriptions across multiple versions.  
 * 
 * In 6.2 we are taking a hybrid approach:  descriptions still exist in the 
 * LocalizedAttributes table but they are also available inside of a Map that 
 * each object maintains among its attributes.  The methods in this utility 
 * class facilitate setting and/or retrieving descriptions from Describable objects.
 * Currently these include Application, Policy, and Bundle.  
 * 
 * sailpoint.api.Explanator contains similar utilities for ManagedAttributes.
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class Describer {
    private static final Log log = LogFactory.getLog(Describer.class);
    Describable _describable;
    
    /**
     * Create an instance of Describer to descriptions on the specified Describable object
     * @param describable Describable object for which descriptions are managed.
     * @throws IllegalArgumentException
     */
    public Describer(Describable describable) {
        validate(describable);
        _describable = describable;
    }
    
    /**
     * Add descriptions for multiple locales at a time.
     * @param descriptions Map of descriptions keyed by locale
     */
    public void addDescriptions(Map<String, String> descriptions) {
        boolean isValid = validate(_describable);

        if (isValid && !Util.isEmpty(descriptions)) {
            Map<String,String> descs = _describable.getDescriptions();
            if (Util.isEmpty(descs)) {
                descs = new HashMap<String,String>();
                _describable.setDescriptions(descs);
            }
            
            descs.putAll(descriptions);
        }
    }
    
    /**
     * Removes the description from the objects corresponding to the given LocalizedAttribute.  Note that 
     * the LocalizedAttriubte itself is not removed.  The caller is responsible for doing that.  Also no
     * changes are committed, nor is the object decached.  The caller is responsible for doing that as well.
     * @param locale String representation of the Locale whose description is being removed 
     * @throws ClassNotFoundException when the targetClass for the LocalizedAttribute cannot be loaded
     * @throws GeneralException when the objects to update cannot be found
     */
    public void removeDescription(SailPointContext context, Locale locale) throws GeneralException {
        String localeString = locale == null ? null : locale.toString();
        removeDescription(context, localeString);
    }

    
    /**
     * Removes the description from the objects corresponding to the given LocalizedAttribute.  Note that 
     * the LocalizedAttriubte itself is not removed.  The caller is responsible for doing that.
     * @param locale String representation of the Locale whose description is being removed 
     * @param commit true to commit changes to the object; false otherwise
     * @param decache true to decache the object after committing; false otherwise.  
     *        Note:  this parameter is ignored if commit is false 
     * @throws ClassNotFoundException when the targetClass for the LocalizedAttribute cannot be loaded
     * @throws GeneralException when the objects to update cannot be found
     */
    public void removeDescription(SailPointContext context, String locale) throws GeneralException {
        if (!Util.isNullOrEmpty(locale)) {
            boolean isValid = validate(_describable);
            if (isValid) {
                Map<String, String> descriptions = _describable.getDescriptions();
                descriptions.remove(locale);
                _describable.setDescriptions(descriptions);
                context.saveObject((SailPointObject)_describable);
            }
        }
    }
    
    /**
     * Generates LocalizedAttributes corresponding to the descriptions in an efficient 
     * manner and clears out LocalizedAttributes corresponding to empty/non-existent descriptions.
     * This operation does not commit or decache any objects.  The caller is responsible for doing that.
     * @param context SailPointContext in which to save the object
     * @return number of new, updated, and/or deleted LocalizedAttributes
     */
    public int saveLocalizedAttributes(SailPointContext context) { 
        int numUpdated = 0;
        final boolean isValid = validate(_describable);
        
        if (_describable.getClass() == ManagedAttribute.class) {
            throw new IllegalArgumentException("ManagedAttributes do not have corresponding LocalizedAttributes.  Use sailpoint.api.Explanator instead.");
        }
        
        if (isValid) {
            try {            
                String targetId = ((SailPointObject) _describable).getId();
                Localizer localizer = new Localizer(context, targetId);
                Map<String, String> descriptionsToUpdate = _describable.getDescriptions();
                if (Util.isEmpty(descriptionsToUpdate)) {
                    // If we have no descriptions clear out all of the LocalizedAttributes for this object
                    QueryOptions qo = new QueryOptions(Filter.and(Filter.eq("targetId", targetId), Filter.eq("attribute", Localizer.ATTR_DESCRIPTION)));
                    numUpdated += context.countObjects(LocalizedAttribute.class, qo);
                    List<LocalizedAttribute> localizedAttributes = context.getObjects(LocalizedAttribute.class, qo);
                    if (!Util.isEmpty(localizedAttributes)) {
                        for (LocalizedAttribute localizedAttribute : localizedAttributes) {
                            context.removeObject(localizedAttribute);                                            
                        }
                    }
                } else {
                    Set<String> unupdatedDescriptions = new HashSet<String>();
                    unupdatedDescriptions.addAll(descriptionsToUpdate.keySet());
                    
                    // Update or remove the existing attributes as needed
                    List<LocalizedAttribute> existingAttributes = localizer.findAttributes(Localizer.ATTR_DESCRIPTION);
                    if (!Util.isEmpty(existingAttributes)) {
                        for (LocalizedAttribute existingAttribute : existingAttributes) {
                            String locale = existingAttribute.getLocale();
                            String valueToUpdate = descriptionsToUpdate.get(locale);
                            // If the new value is null or empty then get rid of the LocalizedAttribute
                            if (Util.isNullOrEmpty(valueToUpdate)) {
                                context.removeObject(existingAttribute);
                            } else {
                                existingAttribute.setValue(valueToUpdate);
                                context.saveObject(existingAttribute);
                            }
                            numUpdated++;
                            unupdatedDescriptions.remove(locale);
                        }                    
                    }
                    
                    // Create new LocalizedAttributes if necessary
                    if (!Util.isEmpty(unupdatedDescriptions)) {
                        for (String locale : unupdatedDescriptions) {
                            String valueToUpdate = descriptionsToUpdate.get(locale);
                            // Only create Localized attributes for populated values
                            if (!Util.isNullOrEmpty(valueToUpdate)) {
                                String targetName = ((SailPointObject)_describable).getName();
                                LocalizedAttribute newAttribute = localizer.buildLocalizedAttribute(Localizer.ATTR_DESCRIPTION, locale, targetName, _describable.getClass().getSimpleName());
                                // Set a name for visibility on the debug page (this is not seen anywhere in the UI)
                                newAttribute.setName(targetName + ":" + Localizer.ATTR_DESCRIPTION + ":" + locale);
                                newAttribute.setValue(valueToUpdate);
                                context.saveObject(newAttribute);
                                numUpdated++;
                            }
                        }
                    }
                }
            } catch (GeneralException e) {
                log.error("The Describer failed to update localized attributes for " + _describable.toString(), e);
            }
        }
        
        return numUpdated;
    }
    
    /**
     * Convenience method for getting the default description from the Describable object managed by this class
     */
    public String getDefaultDescription(SailPointContext context) {
        String warningMsg = new Message(MessageKeys.ERROR_DESCRIPTION_DEPRECATED, _describable.getClass().getSimpleName()).getLocalizedMessage();
        String defaultDescription = null;
        try {
            if (context != null) {
                String defaultLocale = Localizer.getDefaultLocaleName(context.getConfiguration());
                defaultDescription = _describable.getDescription(defaultLocale);
            } else {
                log.error(warningMsg, new GeneralException("getDescription() is deprecated for Describable objects and may not always work."));
            }
        } catch (GeneralException e) {
            log.error(warningMsg, e);
        }
        return defaultDescription;
    }
    
    /**
     * Convenience method for setting the default description  from the Describable object managed by this class -- 
     * Primarily intended for SailPointObjects to provide legacy support for setDescription
     * @param description
     */
    public void setDefaultDescription(SailPointContext context, String description) {
        String warningMsg = new Message(MessageKeys.ERROR_DESCRIPTION_DEPRECATED, _describable.getClass().getSimpleName()).getLocalizedMessage();
        try {
            if (context != null) {
                String defaultLocale = Localizer.getDefaultLocaleName(context.getConfiguration());
                _describable.addDescription(defaultLocale, description);
            } else {
                log.error(warningMsg, new GeneralException("setDescription() is deprecated for Describable objects and may not always work."));
            }
        } catch (GeneralException e) {
            log.error(warningMsg, e);
        }
    }
    
    /********************************************************************
     * 
     * Utilities for updating objects from LocalizedAttributes 
     *  
     ********************************************************************/
    

    /**
     * Updates the description on the objects corresponding to the given LocalizedAttribute
     * @param context SailPointContext under which to make the update
     * @param attribute LocalizedAttribute whose object is being updated
     * @throws ClassNotFoundException when the targetClass for the LocalizedAttribute cannot be loaded
     * @throws GeneralException when the database fails to fetch objects to update
     * @return Describable whose description was updated; null if no such object was found
     */
    public static Describable updateDescription(SailPointContext context, LocalizedAttribute attribute) 
            throws ClassNotFoundException, GeneralException {
        Describable updatedObj = null;
        if (attribute != null) {
            String targetClassName = "sailpoint.object." + attribute.getTargetClass();
            Class targetClass = Class.forName(targetClassName);
            String targetId = attribute.getTargetId();
            if (!Util.isNullOrEmpty(targetId)) {
                Describable describable = (Describable) context.getObjectById(targetClass, attribute.getTargetId());
                final boolean isValid = validate(describable);
                if (isValid) {
                    describable.addDescription(attribute.getLocale(), attribute.getValue());
                    context.saveObject((SailPointObject)describable);
                    updatedObj = describable;
                }                
            }
        }
        
        return updatedObj;
    }
        
    /**
     * Removes the description from the objects corresponding to the given LocalizedAttribute.  Note that 
     * the LocalizedAttriubte itself is not removed.  The caller is responsible for doing that.  Not that
     * this method does not commit or decache the object.  The caller is responsible for doing so if needed.
     * @param context SailPointContext under which to make the update
     * @param attribute LocalizedAttribute whose object is being updated
     *        Note:  this parameter is ignored if commit is false 
     * @throws ClassNotFoundException when the targetClass for the LocalizedAttribute cannot be loaded
     * @throws GeneralException when the objects to update cannot be found
     * @return Describable object from which the corresponding attribute was removed
     */
    public static Describable removeDescription(SailPointContext context, LocalizedAttribute attribute) 
        throws GeneralException, ClassNotFoundException {
        Describable objToUpdate = null;
        
        if (attribute != null && !Util.isNullOrEmpty(attribute.getLocale())) {
            String targetId = attribute.getTargetId();
            String targetClassName = "sailpoint.object." + attribute.getTargetClass();
            Class targetClass = Class.forName(targetClassName);
            objToUpdate = (Describable) context.getObjectById(targetClass, targetId);
            if (validate(objToUpdate)) {
                Map<String, String> descriptions = objToUpdate.getDescriptions();
                if (descriptions != null) {
                    descriptions.remove(attribute.getLocale());
                    objToUpdate.setDescriptions(descriptions);
                    context.saveObject((SailPointObject) objToUpdate);
                }
            }
        }
        
        return objToUpdate;
    }

    /*
     * @return true if the Object is a valid argument for the Describer utilities; false otherwise
     */
    private static boolean validate(Object obj) {
        boolean valid = true;
        
        if (obj == null || !(obj instanceof SailPointObject)) {
            String objType;
            if (obj == null) {
                objType = "null";
            } else {
                objType = obj.getClass().getName();
            }
            log.warn("The Describer can only commit SailPointObjects.  It cannot update a " + objType);
            valid = false;
        }
        
        return valid;
    }
}
