package sailpoint.service;

import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

/**
 * Abstract base class for services that target a specific SailPointObject
 * @param <T> Type of SailPoint object
 */
public abstract class BaseObjectService<T extends SailPointObject> {

    /**
     * Gets the SailPointObject
     * @return Object to interact with
     */
    abstract protected T getObject();

    /**
     * Gets a SailPointContext
     * @return SailPointContext
     */
    abstract protected SailPointContext getContext();

    /**
     * Classes should return a list of allowed fields in the patch values map
     * @return List of allowed fields
     */
    abstract protected List<String> getAllowedPatchFields();

    /**
     * Classes should validate that the value of a field is a valid type
     * @param field Field to patch
     * @param value Value provided for patch
     * @return True if valid, False if invalid
     */
    abstract protected boolean validateValue(String field, Object value);

    /**
     * Apply the new value to the object we are patching. Do not save or commit
     * in this method.
     * @param field Field to patch
     * @param value New value
     */
    abstract protected void patchValue(String field, Object value) throws GeneralException;

    /**
     * Patch the object handled by this service with the field/value pairs given
     * @param values Map of field/value pairs to patch
     * @throws GeneralException
     */
    public void patch(Map<String, Object> values) throws GeneralException {
        if (Util.isEmpty(values)) {
            throw new InvalidParameterException("values");
        }
        
        for (String valueKey : values.keySet()) {
            if (!getAllowedPatchFields().contains(valueKey)) {
                throw new GeneralException("Field " + valueKey + " cannot be patched");
            }
            if (!validateValue(valueKey, values.get(valueKey))) {
                throw new GeneralException("Invalid value for field "+ valueKey);
            }
        }

        for (String valueKey : values.keySet()) {
            patchValue(valueKey, values.get(valueKey));    
        }

        getContext().saveObject(getObject());
        getContext().commitTransaction(); 
    }
}