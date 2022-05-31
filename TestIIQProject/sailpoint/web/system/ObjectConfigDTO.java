/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for an ObjectConfig.
 *
 * Author: Jeff
 *
 * This may be subclassed if you have something more 
 * to manage (e.g. role type definitions).
 */

package sailpoint.web.system;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.SailPointObjectDTO;

public class ObjectConfigDTO extends SailPointObjectDTO
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ObjectConfigDTO.class);

    String _className;
    List<ObjectAttributeDTO> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build the base DTO representation of an ObjectConfig.
     * We don't do anything here with the configAttributes.
     * The ObjectConfigBean subclass is expected to overload
     * the ObjectConfigBean.createSession method to build out
     * the more complex ObjectConfigSession and ObjectConfigDTO 
     * classes.
     */
    public ObjectConfigDTO(ObjectConfig src) {
        super(src); // it's a SailPointObject
        
        _className = src.getName();
        _attributes = new ArrayList<ObjectAttributeDTO>();
        List<ObjectAttribute> srcatts = src.getObjectAttributes();
        if (srcatts != null) {
            for (ObjectAttribute att : srcatts) {
                ObjectAttributeDTO adto = new ObjectAttributeDTO(src.getName(), att);
                _attributes.add(adto);
            }
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

    /**
     * Used by the grid.
     */
    public List<ObjectAttributeDTO> getAttributes() {
        return _attributes;
    }

    /**
     * Lookup an attribute by uid, used when we begin
     * editing an attribute for the first time.
     */
    public ObjectAttributeDTO getAttribute(String uid) {
        
        return (ObjectAttributeDTO)find(_attributes, uid);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Replace one attribute definition with another.
     * Normally the new definition will be the clone of another and
     * must have been given the same uid.
     */
    public void replace(ObjectAttributeDTO neu) {

        if (_attributes == null)
            _attributes = new ArrayList<ObjectAttributeDTO>();

        // BaseDTO handles this comparing uids
        replace(_attributes, neu);
    }

    /**
     * Remove an attribute definition.
     * The attribute is not expected to be cloned.
     */
    public void remove(ObjectAttributeDTO att) {

        if (_attributes != null)
            _attributes.remove(att);
    }

    /**
     * This may be overloaded in a subclass if it has something more to do.
     * 
     * We don't allow editing of any of the SailPointObject fileds
     * so don't need to call super(config).
     *
     * Don't mess with merging just replace the entire object list.
     * Hmm, there are a bunch of read-only flags (system, standard, etc)
     * we wouldn't have to mess with if we did a merge.
     * 
     */
    public void commit(ObjectConfig config)
        throws GeneralException {

        // reallocaete extended numbers
        
        // clear numbers for attributes no longer searchable
        for (ObjectAttributeDTO dto : _attributes) {
            if (!dto.isSearchable())
                dto.setExtendedNumber(0);
        }

        // assign numbers to attributes that are now searchable
        for (ObjectAttributeDTO dto : _attributes) {
            if (dto.isSearchable() && dto.getExtendedNumber() == 0)
                dto.setExtendedNumber(getNextExtendedNumber(dto, config));
        }

        // convert the attributes
        List<ObjectAttribute> defs = new ArrayList<ObjectAttribute>();
        for (ObjectAttributeDTO dto : _attributes)
            defs.add(dto.convert());
        config.setObjectAttributes(defs);

    }

    /**
     * Helper for commit.  
     * Scan the attributes looking for the lowest available number.
     * This isn't very efficient but we won't have many.
     * @param theDto the dto for which extended number is being determined
     * @param config the parent ObjectConfig
     */
    public int getNextExtendedNumber(ObjectAttributeDTO theDto, ObjectConfig config) throws GeneralException {

        int number = 0;

        int maxExtendedAttributes = fetchMaxExtendedAttributes(theDto, config);
        for (int i = 1 ; i <= maxExtendedAttributes ; i++) {
            ObjectAttributeDTO att = null;
            for (ObjectAttributeDTO dto : _attributes) {
                if (belongsToSameSlot(dto, theDto, config) && dto.getExtendedNumber() == i) {
                    // if they are of the same type, and it is taken
                    att = dto;
                    break;
                }
            }

            if (att == null) {
                // not taken, we can claim this one
                number = i;
                break;
            }
        }

        if (number == 0)
            throw new GeneralException("Extended attribute allocation overflow");

        // TODO: Need to check the maximum for this class!

        return number;
    }
    
    private static int fetchMaxExtendedAttributes(ObjectAttributeDTO dto, ObjectConfig config) {
        //IIQSAW-1250
        //only Identity class can have Identity Extended Attribute columns
        //@see ObjectConfig.isExtendedIdentity()
        if (ObjectConfig.IDENTITY.equals(config.getName()) && ObjectAttribute.TYPE_IDENTITY.equals(dto.getType())) {
            return SailPointObject.MAX_EXTENDED_IDENTITY_ATTRIBUTES;
        } else {
            // Starting with 6.1 we don't have a limit but we still
            // use the assignment of a number as an indication that
            // something is searchable.  Searchable attributes that
            // fall outside the hard limit of 20 will need to have
            // symbolic name mappings.  In theory there is no limit
            // but in practice there will be a database limit.  Just
            // return something impossibly large.
            //return SailPointObject.MAX_EXTENDED_ATTRIBUTES;
            return 1000000;
        }
    }
    
    /*
     * Only Identity class has two slots.
     * All other object classes has only one slot.
     * 
     */
    private static boolean belongsToSameSlot(ObjectAttributeDTO dto1, ObjectAttributeDTO dto2, ObjectConfig config) {
        //IIQSAW-1250
        //only Identity class can have Identity Extended Attribute columns 
        //All other classes only have String type of Extended Attribute column
        //@see ObjectConfig.isExtendedIdentity()
        if (!ObjectConfig.IDENTITY.equals(config.getName())) {
            return true;
        }
        
        if (Util.isNullOrEmpty(dto1.getType())) {
            return Util.isNullOrEmpty(dto2.getType());
        }

        boolean result = true;
        
        if (ObjectAttribute.TYPE_IDENTITY.equals(dto1.getType())) {
            if (!ObjectAttribute.TYPE_IDENTITY.equals(dto2.getType())) {
                result = false;
            }
        } else {
            if (ObjectAttribute.TYPE_IDENTITY.equals(dto2.getType())) {
                result = false;
            }
        }
        
        return result;
    }

}
