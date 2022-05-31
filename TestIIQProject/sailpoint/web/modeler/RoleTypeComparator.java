/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;

/**
 * Comparator used to sort roles by type as well as name.  Types
 * are sorted in the order that they are listed in the Bundle ObjectConfig.
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class RoleTypeComparator implements Comparator<Bundle> {
    private List<String> typeNames;
    
    public RoleTypeComparator(ObjectConfig objectConfig) {
        typeNames = new ArrayList<String>();
        if (objectConfig != null) {
             // Establish a type order based on the ObjectConfig
            List<RoleTypeDefinition> typesList = objectConfig.getRoleTypesList();
            if (typesList != null && !typesList.isEmpty()) {
                for (RoleTypeDefinition roleType : typesList) {
                    typeNames.add(roleType.getName());
                }
            }            
        }
    }
    
    public int compare(Bundle role1, Bundle role2) {
        int result;

        if ((role1 == null && role2 == null) || (role1 == role2)) {
            result = 0;
        } else if (role1 == null) {
            result = 1;
        } else if (role2 == null) {
            result = -1;
        } else {
            final String type1 = role1.getType();
            final String type2 = role2.getType();

            if (!Util.isEmpty(typeNames) && typeNames.contains(type1) && typeNames.contains(type2)) {
                // Order as specified by the ObjectConfig if we can
                int type1Index = typeNames.indexOf(type1);
                int type2Index = typeNames.indexOf(type2);
                
                result = type1Index - type2Index;
            } else {
                // When encountering unknown types fall back on alphabetical ordering
                result = Internationalizer.INTERNATIONALIZED_STRING_COMPARATOR.compare(type1, type2);
            }

            if (result == 0) {
                // If two roles are the same type order them by display name
                result = Internationalizer.INTERNATIONALIZED_STRING_COMPARATOR.compare(role1.getDisplayableName(), role2.getDisplayableName());
            } 
            
        }

        return result;
    }
}
