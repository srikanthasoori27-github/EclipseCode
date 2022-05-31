/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO representation of the ObjectConfig for Bundle (role).
 *
 * This extends ObjectConfigDTO which provides services
 * for defining extended role attriutes.  Here we provide
 * a model for defining role types.
 * 
 * NOTE: This is just a stub, Bernie you can extend this as necessary
 * to save state related to role types.
 *
 */

package sailpoint.web.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;

public class RoleObjectConfigDTO extends ObjectConfigDTO
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RoleObjectConfigDTO.class);

    /**
     * DTO's representing the role types.
     */
    List<RoleTypeDefinitionDTO> _types;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public RoleObjectConfigDTO(ObjectConfig src) {
        super(src);

        _types = new ArrayList<RoleTypeDefinitionDTO>();

        // The original persistent model had these in a Map, but they should be in a list now.
        // Stil, we check for both just in case -- Bernie
        String attname = ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS;
        
        Object types = src.get(attname);
        
        if (types != null && types instanceof Map) {
            Map <String, RoleTypeDefinition> typeMap = 
                (Map<String, RoleTypeDefinition>) src.get(attname);
    
            if (typeMap != null) {
                Iterator<RoleTypeDefinition> defs = typeMap.values().iterator();
                while (defs.hasNext()) {
                    _types.add(new RoleTypeDefinitionDTO(defs.next()));
                }
            }
        } else if (types != null && types instanceof List) {
            for (RoleTypeDefinition type : (List<RoleTypeDefinition>) types) {
                _types.add(new RoleTypeDefinitionDTO(type));
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public List<RoleTypeDefinitionDTO> getTypes() {
        return _types;
    }

    public int getTypeCount() {
        return (_types != null) ? _types.size() : 0;
    }

    /**
     * Lookup a type by uid, used when we begin
     * editing a type for the first time.
     */
    public RoleTypeDefinitionDTO getType(String uid) {
        
        return (RoleTypeDefinitionDTO)find(_types, uid);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Replace one type definition with another.
     * Normally the new definition will be the clone of another and
     * must have been given the same uid.
     */
    public void replace(RoleTypeDefinitionDTO neu) {

        if (_types == null)
            _types = new ArrayList<RoleTypeDefinitionDTO>();

        // BaseDTO handles this comparing uids
        replace(_types, neu);
    }

    /**
     * Remove a type definition.
     * The attribute is not expected to be cloned.
     */
    public void remove(RoleTypeDefinitionDTO type) {

        if (_types != null)
            _types.remove(type);
    }

    /**
     * First let ObjectConfigDTO commit the fields related to 
     * ObjectAttribute definitions, then commit our extensions for
     * role types.
     */
    @SuppressWarnings("unchecked")
    public void commit(ObjectConfig config)
        throws GeneralException {

        super.commit(config);

        log.debug("Committing role types");
        // convert the typeDTO list into a persistent list
        List<RoleTypeDefinition> persistentList = new ArrayList<RoleTypeDefinition>();
        
        if (_types != null) {
            /* Ideally we could do this, but it whacks whatever order was originally stored
             * since we want to rely on the for our tree sorting we don't want to do that 
             * for (RoleTypeDefinitionDTO dto : _types) {
             *     RoleTypeDefinition type = dto.convert();
             *     persistentList.add(type);
             * }
             */
            // Generate a by-name map of our DTOs so that we can track what has and hasn't been persisted
            Map<String, RoleTypeDefinitionDTO> dtosByName = new HashMap<String, RoleTypeDefinitionDTO>();
            
            for (RoleTypeDefinitionDTO dto : _types) {
                dtosByName.put(dto.getName(), dto);
            }
            
            // Go through the list and add our elements in that same order if we find them
            List<RoleTypeDefinition> oldTypeDefs = (List<RoleTypeDefinition>) config.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);
            for (RoleTypeDefinition oldTypeDef : oldTypeDefs) {
                RoleTypeDefinitionDTO newTypeDef = dtosByName.remove(oldTypeDef.getName());
                if (newTypeDef != null) {
                    persistentList.add(newTypeDef.convert(getContext()));
                }
            }
            
            // Whatever remains is new, so just tack it on to the end
            for (RoleTypeDefinitionDTO newTypeDef : dtosByName.values()) {
                persistentList.add(newTypeDef.convert(getContext()));
            }
            
            // Voila!  Order has been preserved
        }

        config.put(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS, persistentList);
    }

}
